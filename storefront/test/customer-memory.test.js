const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'customer-memory-'));
process.env.DATABASE_PATH = path.join(temp, 'test.db');
process.env.NODE_ENV = 'test';
process.env.USE_TRADE_CORE = 'false';
process.env.SHOPPING_MODEL_ENABLED = 'false';
const dbModule = require('../server/db');
let store = dbModule.getDb();
for (const id of [1, 2]) store.prepare('INSERT INTO users(id,email,password_hash,name) VALUES(?,?,?,?)').run(id,`u${id}@test`,'unused',`u${id}`);
// Redis appears healthy at the readiness check, then fails on write.
const redis = require('../server/redis');
redis.isRedisReady = () => true;
redis.getRedis = () => ({ rpush: async () => { throw new Error('cache lost'); } });
const memory = require('../server/services/customerMemory');
const { chat, streamChat } = require('../server/services/chatService');
const send = (message, userId=1, sessionId) => chat({message,userId,sessionId});
function turn(sessionId, userId, text='source') {
  store.prepare('INSERT OR IGNORE INTO chat_sessions(id,user_id) VALUES(?,?)').run(sessionId,userId);
  return Number(store.prepare("INSERT INTO chat_messages(session_id,role,content) VALUES(?,'user',?)").run(sessionId,text).lastInsertRowid);
}
after(() => { dbModule.closeDb(); fs.rmSync(temp,{recursive:true,force:true}); });

test('explicit facts survive a new session and current attributes override them', async () => {
  const saved = await send('记住偏好：颜色黑色，容量256GB');
  assert.equal(saved.memory.status,'saved');
  const fresh = await send('推荐手机');
  assert.notEqual(fresh.session_id,saved.session_id);
  assert.deepEqual(fresh.shopping.constraints.preferences,{颜色:'黑色',容量:'256GB'});
  const override = await send('偏好颜色=白色',1,fresh.session_id);
  assert.equal(override.shopping.constraints.preferences.颜色,'白色');
  assert.equal(memory.loadFacts(1).颜色,'黑色');
  assert.equal((await send('查看我的记忆',2)).reply,'你还没有保存长期偏好。');
});

test('ownership checks prevent session and source reuse across accounts', async () => {
  const first = await send('记住偏好：品牌Apple');
  const other = await send('查看我的记忆',2,first.session_id);
  assert.notEqual(other.session_id,first.session_id);
  const id = turn('owned',1);
  assert.throws(()=>memory.handleCommand({userId:2,sessionId:'owned',turnId:id,message:'记住偏好：颜色红色'}),/does not belong/);
  assert.equal((await send('记住偏好：颜色红色',null)).memory.status,'login_required');
  assert.deepEqual(memory.loadFacts(null),{});
});

test('temporary requests and unsupported facts never become durable facts', async () => {
  const before = memory.loadFacts(1);
  await send('推荐手机，预算100元');
  assert.deepEqual(memory.loadFacts(1),before);
  const result = await send('记住偏好：预算100元');
  assert.equal(result.memory.status,'clarification');
  assert.equal(memory.extractFacts('记住偏好：颜色黑色，执行退款'),null);
});

test('facts expire and older turns cannot overwrite a newer source', () => {
  const older = turn('facts',2,'记住偏好：颜色黑色');
  const newer = turn('facts',2,'记住偏好：颜色白色');
  const now = Date.now();
  memory.handleCommand({userId:2,sessionId:'facts',turnId:newer,message:'记住偏好：颜色白色',now});
  memory.handleCommand({userId:2,sessionId:'facts',turnId:older,message:'记住偏好：颜色黑色',now});
  assert.equal(memory.loadFacts(2,now).颜色,'白色');
  assert.deepEqual(memory.loadFacts(2,now+90*86400000),{});
});

test('episodes are cross-session, user scoped, idempotent and retain failures', async () => {
  const id = turn('episodes',1);
  const input = {userId:1,sessionId:'episodes',turnId:id,intent:'search',tools:{shopping:{status:'unavailable',products:[]}},procedure:memory.selectProcedure('search')};
  memory.recordEpisode(input); memory.recordEpisode(input);
  assert.equal(store.prepare('SELECT COUNT(*) AS n FROM customer_episodes WHERE source_turn_id=?').get(id).n,1);
  assert.equal(memory.recentEpisodes(1)[0].outcome,'unavailable');
  assert.deepEqual(memory.recentEpisodes(2),[]);
  const reply = await send('上次客服');
  assert.equal(reply.intent,'history');
  assert.match(reply.reply,/unavailable/);
  assert.match(reply.reply,/重新查询/);
  assert.deepEqual(reply.sources,[]);
  assert.deepEqual(memory.recentEpisodes(1,Date.now()+91*86400000),[]);
});

test('clear removes facts and filters and rejects late pre-clear writes', async () => {
  await send('记住偏好：颜色黑色');
  const shopping = await send('推荐手机');
  const old = turn('late',1,'记住偏好：颜色黑色');
  await send('忘记我的记忆');
  assert.deepEqual(memory.loadFacts(1),{});
  assert.equal(store.prepare('SELECT COUNT(*) AS n FROM shopping_preferences WHERE session_id=?').get(shopping.session_id).n,0);
  assert.equal(memory.isActiveTurn(1,old),false);
  memory.handleCommand({userId:1,sessionId:'late',turnId:old,message:'记住偏好：颜色黑色'});
  memory.recordEpisode({userId:1,sessionId:'late',turnId:old,intent:'order_lookup',tools:{order:{error:'offline'}},procedure:memory.selectProcedure('order_lookup')});
  assert.deepEqual(memory.loadFacts(1),{});
  assert.deepEqual(memory.recentEpisodes(1),[]);
  assert.deepEqual((await send('推荐手机',1,shopping.session_id)).shopping.constraints.preferences,undefined);
});

test('working context is bounded and an order reference is freshly queried', async () => {
  const sid = 'working';
  for (let i=0;i<25;i++) turn(sid,1,`message ${i}`);
  assert.equal(memory.workingMessages(sid).length,20);
  store.prepare("UPDATE chat_messages SET created_at=datetime('now','-8 days') WHERE session_id=?").run(sid);
  assert.deepEqual(memory.workingMessages(sid),[]);
  store.prepare("INSERT INTO orders(id,user_id,order_no,total,status) VALUES(900,1,'OPS900',10,'pending')").run();
  const first = await send('查订单 OPS900');
  store.prepare("UPDATE orders SET status='cancelled' WHERE id=900").run();
  const second = await send('这个订单',1,first.session_id);
  assert.equal(second.intent,'order_lookup');
  assert.match(second.reply,/cancelled/);
  assert.equal(memory.recentEpisodes(1)[0].outcome,'found');
});

test('SOP tools are read only except a proposal and memory control; SSE exposes its version', async () => {
  const sop = memory.selectProcedure('order_lookup');
  assert.equal(memory.allows(sop,'get_order'),true);
  assert.equal(memory.allows(sop,'refund'),false);
  let output='';
  await streamChat({setHeader(){},flushHeaders(){},write(chunk){output+=chunk;},end(){}},{userId:1,message:'查看我的记忆'});
  const done = output.split('\n').filter(line=>line.startsWith('data: ')).map(line=>JSON.parse(line.slice(6))).find(event=>event.type==='done');
  assert.deepEqual(done.procedure,{id:'memory-control',version:'1.0.0'});
  assert.equal(done.memory.action,'view');
});


test('durable facts and episodes survive closing and reopening SQLite', async () => {
  await send('记住偏好：颜色蓝色');
  await send('查订单 OPS900');
  dbModule.closeDb();
  store = dbModule.getDb();
  assert.equal((await send('查看我的记忆')).reply.includes('蓝色'),true);
  assert.match((await send('历史客服')).reply,/OPS900/);
  const fresh = await send('推荐手机');
  assert.equal(fresh.shopping.constraints.preferences.颜色,'蓝色');
});


test('facts reject a value that was not in the original saved user message', () => {
  const id = turn('evidence',1,'记住偏好：颜色黑色');
  assert.throws(()=>memory.handleCommand({userId:1,sessionId:'evidence',turnId:id,message:'记住偏好：颜色白色'}),/differs from saved/);
});
