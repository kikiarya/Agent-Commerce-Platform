const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const { getDb } = require('../db');
const { getRedis, isRedisReady } = require('../redis');
const { resolveLlmConfig } = require('./llmConfig');
const shoppingAdvisor = require('./shoppingAdvisor');
const trade = require('./tradeCoreClient');
const proposals=require('./orderProposal');
const support=require('./groundedSupport');
const customerMemory = require('./customerMemory');

const FAQ = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'knowledge', 'faq.json'), 'utf8')
);

function ensureSession(sessionId, userId) {
  const id = sessionId || randomUUID();
  const db = getDb();
  const existing = db.prepare('SELECT id, user_id FROM chat_sessions WHERE id = ?').get(id);
  if (!existing) {
    db.prepare(
      `INSERT INTO chat_sessions (id, user_id) VALUES (?, ?)`
    ).run(id, userId || null);
  } else if (existing.user_id !== (userId || null)) {
    return ensureSession(null, userId);
  } else if (userId) {
    db.prepare(
      `UPDATE chat_sessions SET user_id = COALESCE(user_id, ?), updated_at = datetime('now') WHERE id = ?`
    ).run(userId, id);
  }
  return id;
}

function saveMessage(sessionId, role, content, meta = null) {
  const saved = getDb()
    .prepare(
      `INSERT INTO chat_messages (session_id, role, content, meta_json)
       VALUES (?, ?, ?, ?)`
    )
    .run(sessionId, role, content, meta ? JSON.stringify(meta) : null);
  getDb()
    .prepare(`UPDATE chat_sessions SET updated_at = datetime('now') WHERE id = ?`)
    .run(sessionId);
  return saved.lastInsertRowid;
}

async function pushMemory(sessionId, role, content) {
  if (!isRedisReady()) return;
  const key = `chat:mem:${sessionId}`;
  const redis = getRedis();
  try {
    await redis.rpush(key, JSON.stringify({ role, content, ts: Date.now() }));
    await redis.ltrim(key, -20, -1);
    await redis.expire(key, 60 * 60 * 24 * 7);
  } catch { /* SQLite remains the source; cache failure must not abort a saved turn. */ }
}

async function getMemory(sessionId) {
  return customerMemory.workingMessages(sessionId);
}

function retrieveFaq(query) {
  const q = String(query).toLowerCase();
  const scored = FAQ.map((item) => {
    let score = 0;
    for (const tag of item.tags) {
      if (q.includes(tag.toLowerCase())) score += 2;
    }
    if (item.question.toLowerCase().includes(q.slice(0, 24))) score += 1;
    return { item, score };
  })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score);

  return scored.slice(0, 3).map((x) => x.item);
}

/** Tool: search phones (mirrors AI agent tool-calling) */
function toolSearchPhones(query) {
  const db = getDb();
  const rows = db
    .prepare(
      `SELECT id, brand, model, price, condition, available, storage
       FROM phones
       WHERE brand LIKE @q OR model LIKE @q OR description LIKE @q
       ORDER BY available DESC, price DESC
       LIMIT 5`
    )
    .all({ q: `%${query}%` });
  return rows;
}

async function toolGetOrder(orderNoOrId, userId) {
  if (!userId) return { error: '请先登录，再查询自己的订单。', outcome: 'login_required' };
  const db = getDb();
  let order = null;
  if (/^\d+$/.test(String(orderNoOrId))) {
    order = db.prepare('SELECT * FROM orders WHERE id = ?').get(Number(orderNoOrId));
  } else {
    order = db.prepare('SELECT * FROM orders WHERE order_no = ?').get(String(orderNoOrId));
  }
  if (!order) return { error: '未找到属于当前账号的订单，请核对订单号。', outcome: 'not_found' };
  if (order.user_id !== userId) {
    return { error: '未找到属于当前账号的订单，请核对订单号。', outcome: 'not_found' };
  }
  if (order.mid_order_id) {
    try {
      const view = await trade.getOrder(userId, order.mid_order_id);
      return { order_no: order.order_no, status: view.status, total: view.totalAmount, items: view.items, source: 'trade_core' };
    } catch { return { error: 'Core order status unavailable; please sign in to core or retry later' }; }
  }
  const items = db
    .prepare(
      `SELECT oi.quantity, oi.unit_price, p.brand, p.model
       FROM order_items oi JOIN phones p ON p.id = oi.phone_id
       WHERE oi.order_id = ?`
    )
    .all(order.id);
  return {
    order_no: order.order_no,
    status: order.status,
    total: order.total,
    source: order.source,
    created_at: order.created_at,
    items
  };
}

function toolListFlashDeals() {
  return getDb()
    .prepare(
      `SELECT id, title, price, stock, sold, end_at
       FROM flash_deals WHERE active = 1`
    )
    .all()
    .map((d) => ({ ...d, remaining: Math.max(0, d.stock - d.sold) }));
}

function detectIntent(text) {
  const t = text.toLowerCase();
  if (/(order|订单|查单)/.test(t) && /(OPS|#|\d{2,})/.test(t)) return 'order_lookup';
  if (/(flash|seckill|秒杀|限时)/.test(t)) return 'flash';
  if (/(search|find|compare|有没有|推荐|预算|比较|对比|想买|iphone|galaxy|pixel|xiaomi)/.test(t)) return 'search';
  if (/(warranty|shipping|return|退|保修|物流|成色|payment|支付)/.test(t)) return 'faq';
  return 'general';
}

async function callLlm(messages) {
  const cfg = resolveLlmConfig();
  if (!cfg.enabled) return null;

  const res = await fetch(`${cfg.baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${cfg.apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: cfg.model,
      temperature: 0.4,
      messages
    })
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`LLM(${cfg.label}) error ${res.status}: ${body.slice(0, 200)}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content || null;
}

function buildLocalReply(userText, toolsUsed) {
  const parts = [];
  parts.push('您好，我是 OldPhoneStore 客服助手，可以帮您咨询保修、物流、商品和订单。');

  if (toolsUsed.faq?.length) {
    parts.push('');
    for (const f of toolsUsed.faq) {
      parts.push(f.answer);
    }
  }
  if (toolsUsed.phones?.length) {
    parts.push('\n为您找到这些机型：');
    for (const p of toolsUsed.phones) {
      parts.push(
        `• ${p.brand} ${p.model} ${p.storage} — $${p.price}（${p.condition}）${p.available ? '' : ' · 已售出'}`
      );
    }
  }
  if (toolsUsed.order) {
    parts.push('\n订单信息：');
    if (toolsUsed.order.error) {
      parts.push(toolsUsed.order.error);
    } else {
      parts.push(
        `单号 ${toolsUsed.order.order_no || '—'} · ${toolsUsed.order.status} · $${toolsUsed.order.total}`
      );
    }
  }
  if (toolsUsed.flash?.length) {
    parts.push('\n当前限时优惠：');
    for (const d of toolsUsed.flash) {
      parts.push(`• ${d.title} $${d.price} · 剩余 ${d.remaining}`);
    }
  }
  if (parts.length === 1) {
    parts.push('\n您可以问保修、退货、成色，或说「推荐 iPhone」「查订单」「有什么优惠」。');
  }
  return parts.join('\n');
}

async function chat({ sessionId, userId, message }) {
  const sid = ensureSession(sessionId, userId);
  const turnId = saveMessage(sid, 'user', message);
  await pushMemory(sid, 'user', message);

  const working = await getMemory(sid);
  const queryMessage = customerMemory.resolveOrderReference(message, working.slice(0, -1));
  const memoryAction = customerMemory.command(message);
  const intent = memoryAction ? (memoryAction === 'history' ? 'history' : 'memory') : /^选购\s/.test(message) ? 'proposal' : shoppingAdvisor.isFollowUp(message) ? 'search' : detectIntent(queryMessage);
  const procedure = customerMemory.selectProcedure(intent);
  const toolsUsed = {};
  const memoryResult = memoryAction ? customerMemory.handleCommand({ userId, sessionId: sid, turnId, message }) : null;
  if(customerMemory.allows(procedure, 'create_proposal')){try{toolsUsed.proposal=proposals.create(userId,sid,proposals.parseSelection(message));}catch(e){toolsUsed.proposal={reply:e.message,error:true};}}

  // Always try FAQ retrieval when keywords match (multi-intent utterances)
  const faqHits = customerMemory.allows(procedure, 'retrieve_faq') ? support.retrieve(message) : [];
  if (faqHits.length) toolsUsed.faq = faqHits;

  if (customerMemory.allows(procedure, 'search_catalog')) {
    toolsUsed.shopping = await shoppingAdvisor.recommend(message, {previous:customerMemory.previousPreferences(userId, shoppingAdvisor.loadPreferences(sid))});
    if (!toolsUsed.shopping.constraints.clarify && customerMemory.isActiveTurn(userId, turnId)) shoppingAdvisor.savePreferences(sid,turnId,toolsUsed.shopping.constraints);
  }
  if (customerMemory.allows(procedure, 'list_flash_deals')) {
    toolsUsed.flash = toolListFlashDeals();
  }
  if (customerMemory.allows(procedure, 'get_order')) {
    const m = queryMessage.match(/MID-\d+|OPS[A-Z0-9]+|\b\d{1,8}\b/i);
    if (m) toolsUsed.order = await toolGetOrder(m[0], userId);
  }

  const grounded=support.answer(message);
  let reply = memoryResult?.reply || toolsUsed.proposal?.reply || toolsUsed.shopping?.reply || (['order_lookup','flash'].includes(intent)?buildLocalReply(message,toolsUsed):grounded.reply);
  let mode = toolsUsed.shopping ? (toolsUsed.shopping.plannerMode === 'model-tool' ? 'model-catalog-guidance' : toolsUsed.shopping.plannerMode === 'rules-fallback' ? 'catalog-guidance-fallback' : 'catalog-guidance') : 'local-faq';
  const llmCfg = resolveLlmConfig();


  if (!reply) reply = buildLocalReply(message, toolsUsed);

  saveMessage(sid, 'assistant', reply, {
    intent,
    mode,
    provider: (toolsUsed.shopping?.plannerMode === 'model-tool') && llmCfg.enabled ? llmCfg.provider : null,
    tools: Object.keys(toolsUsed),
    shopping: toolsUsed.shopping || null,
    procedure: { id: procedure.id, version: procedure.version }
  });
  await pushMemory(sid, 'assistant', reply);
  customerMemory.recordEpisode({ userId, sessionId: sid, turnId, intent, tools: toolsUsed, procedure });

  return {
    session_id: sid,
    procedure: { id: procedure.id, version: procedure.version },
    memory: memoryResult ? { action: memoryResult.action, status: memoryResult.status } : null,
    reply,
    intent,
    mode,
    llm: (toolsUsed.shopping?.plannerMode === 'model-tool') && llmCfg.enabled
      ? { provider: llmCfg.provider, label: llmCfg.label, model: llmCfg.model }
      : null,
    shopping: toolsUsed.shopping || null,
    tools_used: Object.keys(toolsUsed),
    proposal: toolsUsed.proposal || null,
    sources: ['search','proposal','order_lookup','flash','memory','history'].includes(intent)?[]:grounded.sources,
    citations: ['search','proposal','order_lookup','flash','memory','history'].includes(intent)?[]:grounded.sources.map(f=>f.id)
  };
}

async function streamChat(res, payload) {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders?.();

  const result = await chat(payload);
  const chunks = result.reply.match(/.{1,24}/gs) || [result.reply];
  for (const chunk of chunks) {
    res.write(`data: ${JSON.stringify({ type: 'token', text: chunk })}\n\n`);
    await new Promise((r) => setTimeout(r, 18));
  }
  res.write(
    `data: ${JSON.stringify({
      type: 'done',
      session_id: result.session_id,
      procedure: result.procedure,
      memory: result.memory,
      intent: result.intent,
      mode: result.mode,
      tools_used: result.tools_used,
      citations: result.citations,
      shopping: result.shopping,
      proposal: result.proposal,
      sources: result.sources,
      llm: result.llm
    })}\n\n`
  );
  res.end();
}

module.exports = {
  chat,
  streamChat,
  retrieveFaq,
  FAQ,
  resolveLlmConfig
};
