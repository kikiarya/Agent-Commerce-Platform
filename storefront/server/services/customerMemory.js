const { getDb } = require('../db');
const sops = require('../knowledge/support-sops.json');
const DAY = 86400000;
const RETENTION = 90 * DAY;
const KEYS = ['颜色', '容量', '成色', '品牌'];

function db() {
  const store = getDb();
  store.exec(`
    CREATE TABLE IF NOT EXISTS customer_facts (
      user_id INTEGER NOT NULL REFERENCES users(id), fact_key TEXT NOT NULL,
      value TEXT NOT NULL, source_turn_id INTEGER NOT NULL REFERENCES chat_messages(id),
      updated_at INTEGER NOT NULL, expires_at INTEGER NOT NULL,
      PRIMARY KEY(user_id, fact_key)
    );
    CREATE TABLE IF NOT EXISTS customer_episodes (
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(id),
      session_id TEXT NOT NULL REFERENCES chat_sessions(id),
      source_turn_id INTEGER NOT NULL UNIQUE REFERENCES chat_messages(id),
      intent TEXT NOT NULL, outcome TEXT NOT NULL, summary TEXT NOT NULL,
      evidence_json TEXT NOT NULL, sop_id TEXT NOT NULL, sop_version TEXT NOT NULL,
      created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_customer_episodes_user ON customer_episodes(user_id, source_turn_id DESC);
    CREATE TABLE IF NOT EXISTS customer_memory_resets (
      user_id INTEGER PRIMARY KEY REFERENCES users(id), cleared_turn_id INTEGER NOT NULL
    );
  `);
  return store;
}

function prune(store, now) {
  store.prepare('DELETE FROM customer_facts WHERE expires_at <= ?').run(now);
  store.prepare('DELETE FROM customer_episodes WHERE expires_at <= ?').run(now);
}

function assertOwner(store, userId, sessionId, turnId) {
  const row = store.prepare(`SELECT m.content FROM chat_messages m JOIN chat_sessions s ON s.id=m.session_id
    WHERE m.id=? AND s.id=? AND s.user_id=? AND m.role='user'`).get(turnId, sessionId, userId);
  if (!row) throw new Error('Memory source does not belong to current user');
  return row.content;
}

function selectProcedure(intent) {
  const sop = sops.find(s => s.intent === intent) || sops.find(s => s.intent === 'general');
  return JSON.parse(JSON.stringify(sop));
}

function allows(procedure, tool) { return procedure.tools.includes(tool); }

function command(message) {
  if (/^记住偏好\s*[:：]/.test(message)) return 'save';
  if (/^查看我的记忆[。！!\s]*$/.test(message)) return 'view';
  if (/^忘记我的记忆[。！!\s]*$/.test(message)) return 'clear';
  if (/^(上次客服|上次的问题|历史客服)[。？?！!\s]*$/.test(message)) return 'history';
  return null;
}

function extractFacts(message) {
  const text = message.normalize('NFKC').replace(/^记住偏好\s*:/, '').trim();
  const facts = {};
  for (const part of text.split(/[,，;；]/)) {
    const match = part.trim().match(/^(颜色|容量|成色|品牌)\s*[:=]?\s*([^\n]{1,40})$/);
    if (!match || !match[2].trim()) return null;
    // Allow attribute text, not arbitrary instructions or control characters.
    if (!/^[\p{L}\p{N} .+\-]+$/u.test(match[2].trim())) return null;
    facts[match[1]] = match[2].trim();
  }
  return Object.keys(facts).length ? facts : null;
}

function loadFacts(userId, now = Date.now()) {
  if (!userId) return {};
  const store = db(); prune(store, now);
  return Object.fromEntries(store.prepare('SELECT fact_key,value FROM customer_facts WHERE user_id=? AND expires_at>?')
    .all(userId, now).map(row => [row.fact_key, row.value]));
}

function previousPreferences(userId, session) {
  return { ...session, preferences: { ...loadFacts(userId), ...(session.preferences || {}) } };
}

function recentEpisodes(userId, now = Date.now()) {
  if (!userId) return [];
  const store = db(); prune(store, now);
  return store.prepare(`SELECT intent,outcome,summary,sop_id,sop_version,created_at FROM customer_episodes
    WHERE user_id=? AND expires_at>? ORDER BY source_turn_id DESC LIMIT 5`).all(userId, now);
}

function handleCommand({ userId, sessionId, turnId, message, now = Date.now() }) {
  const action = command(message);
  if (!action) return null;
  if (!userId) return { reply: '请先登录，才能保存或查看跨会话记忆。', action, status: 'login_required' };
  const store = db();
  const source = assertOwner(store, userId, sessionId, turnId);
  if (source !== message) throw new Error('Memory input differs from saved user message');
  prune(store, now);
  if (action === 'save') {
    const facts = extractFacts(message);
    if (!facts) return { reply: `请明确写出要记住的属性，例如“记住偏好：颜色黑色，容量256GB”。支持：${KEYS.join('、')}。`, action, status: 'clarification' };
    const saved = store.transaction(() => {
      const reset = store.prepare('SELECT cleared_turn_id FROM customer_memory_resets WHERE user_id=?').get(userId);
      if (reset && turnId <= reset.cleared_turn_id) return false;
      let changes = 0;
      const put = store.prepare(`INSERT INTO customer_facts VALUES(?,?,?,?,?,?)
        ON CONFLICT(user_id,fact_key) DO UPDATE SET value=excluded.value,source_turn_id=excluded.source_turn_id,
        updated_at=excluded.updated_at,expires_at=excluded.expires_at
        WHERE excluded.source_turn_id>customer_facts.source_turn_id`);
      for (const [key,value] of Object.entries(facts)) changes += put.run(userId,key,value,turnId,now,now+RETENTION).changes;
      return changes > 0;
    })();
    if (!saved) return { reply: '这条偏好请求已被更新或清除，未写入长期记忆。', action, status: 'superseded' };
    return { reply: '已保存你明确提供的选购偏好，后续会话可复用；本次明确条件优先。', action, status: 'saved' };
  }
  if (action === 'clear') {
    store.transaction(() => {
      store.prepare('DELETE FROM customer_facts WHERE user_id=?').run(userId);
      store.prepare('DELETE FROM customer_episodes WHERE user_id=?').run(userId);
      store.prepare(`INSERT INTO customer_memory_resets VALUES(?,?) ON CONFLICT(user_id) DO UPDATE
        SET cleared_turn_id=MAX(cleared_turn_id,excluded.cleared_turn_id)`).run(userId,turnId);
      // Existing shopping preferences must not revive forgotten attributes.
      if (store.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='shopping_preferences'").get()) {
        store.prepare('DELETE FROM shopping_preferences WHERE session_id IN (SELECT id FROM chat_sessions WHERE user_id=?)').run(userId);
      }
    })();
    return { reply: '已清除长期偏好、客服事件和会话选购条件。原始对话记录仍保留。', action, status: 'cleared' };
  }
  if (action === 'view') {
    const facts = loadFacts(userId, now);
    return { reply: Object.keys(facts).length ? `你明确保存的偏好：${Object.entries(facts).map(([k,v])=>`${k}：${v}`).join('；')}。` : '你还没有保存长期偏好。', action, status: 'ok' };
  }
  const episodes = recentEpisodes(userId, now);
  return { reply: episodes.length ? `以下是此前客服事件，仅表示当时的结果，订单和价格需重新查询：\n${episodes.map(e=>`• ${new Date(e.created_at).toISOString()} ${e.summary}`).join('\n')}` : '没有找到仍在保留期内的客服事件。', action, status: 'ok' };
}

function recordEpisode({ userId, sessionId, turnId, intent, tools, procedure, now = Date.now() }) {
  if (!userId || !['search','order_lookup','faq'].includes(intent)) return;
  let outcome, summary, evidence;
  if (intent === 'search') {
    const result = tools.shopping;
    if (!result || ['clarification','reset'].includes(result.status)) return;
    outcome = result.status;
    summary = `商品查询：${result.status}；找到 ${result.products.length} 个候选。`;
    evidence = { checkedAt: result.checkedAt || null, productIds: result.products.map(p=>p.id) };
  } else if (intent === 'order_lookup') {
    const order = tools.order;
    outcome = order?.error ? (order.outcome || 'unavailable') : order ? 'found' : 'not_found';
    summary = order && !order.error ? `订单查询：${order.order_no}；当时状态 ${order.status}。` : `订单查询：${outcome}，未确认当前状态。`;
    evidence = order && !order.error ? { orderNo: order.order_no, status: order.status, source: order.source } : {};
  } else {
    if (!tools.faq?.length) return;
    outcome = 'policy_answered';
    summary = `政策咨询：${tools.faq.map(f=>f.question).join('；')}；仅提供政策，未执行交易。`;
    evidence = { sources: tools.faq.map(f=>({id:f.id,version:f.version})) };
  }
  const store = db(); assertOwner(store,userId,sessionId,turnId); prune(store,now);
  const reset = store.prepare('SELECT cleared_turn_id FROM customer_memory_resets WHERE user_id=?').get(userId);
  if (reset && turnId <= reset.cleared_turn_id) return;
  store.prepare(`INSERT OR IGNORE INTO customer_episodes
    (user_id,session_id,source_turn_id,intent,outcome,summary,evidence_json,sop_id,sop_version,created_at,expires_at)
    VALUES(?,?,?,?,?,?,?,?,?,?,?)`).run(userId,sessionId,turnId,intent,outcome,summary,JSON.stringify(evidence),procedure.id,procedure.version,now,now+RETENTION);
}

function isActiveTurn(userId, turnId) {
  if (!userId) return true;
  const reset = db().prepare('SELECT cleared_turn_id FROM customer_memory_resets WHERE user_id=?').get(userId);
  return !reset || turnId > reset.cleared_turn_id;
}

function workingMessages(sessionId) {
  return getDb().prepare(`SELECT role,content FROM chat_messages WHERE session_id=?
    AND created_at>=datetime('now','-7 days') ORDER BY id DESC LIMIT 20`).all(sessionId).reverse();
}

function resolveOrderReference(message, messages) {
  if (!/这个订单|那笔订单|刚才的订单/.test(message)) return message;
  for (const item of [...messages].reverse()) {
    if (item.role !== 'user' || !/订单|查单|order/i.test(item.content)) continue;
    const id = item.content.match(/MID-\d+|OPS[A-Z0-9]+|(?:订单|查单|order)\s*#?\s*(\d{1,8})\b/i);
    if (id) return `查订单 ${id[1] || id[0]}`;
  }
  return message;
}

module.exports = { selectProcedure, allows, command, extractFacts, loadFacts, previousPreferences,
  recentEpisodes, handleCommand, recordEpisode, workingMessages, resolveOrderReference, isActiveTurn };
