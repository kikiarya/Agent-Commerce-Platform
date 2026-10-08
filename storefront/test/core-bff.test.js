const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const express = require('express');
const jwt = require('jsonwebtoken');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'commerce-bff-'));
process.env.DATABASE_PATH = path.join(temp, 'test.db');
process.env.JWT_SECRET = 'test-storefront-secret-not-for-use-123456789';
process.env.NODE_ENV = 'test';
process.env.USE_TRADE_CORE = 'true';
process.env.TRADE_CORE_SKU_ID_EQUALS_FRONT = 'false';
process.env.TRADE_CORE_DEFAULT_USER_ID = '1'; // legacy configuration must have no effect
for (const key of ['LLM_API_KEY','OPENAI_API_KEY','DEEPSEEK_API_KEY','KIMI_API_KEY']) delete process.env[key];
let coreServer, bffServer, base, dbModule, auth, identity;
let nextId = 1;
const sessions = new Map();
const calls = [];
let failOrders = false;
async function listen(app) { return await new Promise(resolve => { const s = app.listen(0,'127.0.0.1',()=>resolve(s)); }); }
async function request(url, user=1, method='GET', body, headers={}) {
  const token = user == null ? null : auth.signToken({ id:user, email:`u${user}@test`, role:'user', name:`u${user}` });
  const res = await fetch(base+url,{ method, headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`} :{}),...headers},body:body===undefined?undefined:JSON.stringify(body) });
  return { status:res.status, body:await res.json() };
}
const link = user => request('/api/auth/core-link',user,'POST',{username:`user${user}`,password:'secret'});
const create = (user=1,items=[{skuId:11,quantity:1},{skuId:22,quantity:2}]) => request('/api/checkout',user,'POST',{items,shippingAddress:'Test address',userId:999});
async function ready() {
 const c = (await create()).body;
 await request(`/api/checkout/${c.checkoutId}/quote`,1,'POST',{});
 await request(`/api/checkout/${c.checkoutId}/confirm`,1,'POST',{quoteVersion:1});
 return c;
}
before(async()=>{
 const core = express(); core.use(express.json());
 core.get('/api/products/search',(req,res)=>{
   assert.equal(req.query.inStock,'true');
   res.json([{id:11,name:'Beginner skateboard',price:79.99},{id:22,name:'Premium skateboard',price:199.99}]);
 });
 core.post('/api/auth/login',(req,res)=>{
   if(req.body.password!=='secret') return res.status(401).json({message:'Invalid credentials'});
   const userId=Number(req.body.username.replace('user',''));
   res.json({userId,token:jwt.sign({sub:String(userId)},'fake-core-signing-secret',{expiresIn:'1h'})});
 });
 core.use((req,res,next)=>{
   if(req.headers['x-user-id']) return res.status(400).json({error:'Unsigned identity header'});
   try { req.coreUser=Number(jwt.verify(req.headers.authorization?.slice(7),'fake-core-signing-secret').sub); }
   catch { return res.status(401).json({message:'Core token required'}); }
   calls.push({method:req.method,path:req.path,body:req.body,user:req.coreUser}); next();
 });
 core.post('/api/checkouts',(req,res)=>{
   const c={checkoutId:nextId++,userId:req.coreUser,status:'DRAFT',quoteVersion:0,...req.body};
   sessions.set(c.checkoutId,c);res.json(c);
 });
 core.use('/api/checkouts/:id',(req,res,next)=>{
   req.checkout=sessions.get(Number(req.params.id));
   if(!req.checkout || req.checkout.userId!==req.coreUser) return res.status(403).json({message:'Not owner'});
   next();
 });
 core.get('/api/checkouts/:id',(req,res)=>res.json(req.checkout));
 core.post('/api/checkouts/:id/quote',(req,res)=>{
   Object.assign(req.checkout,{status:'QUOTED',quoteVersion:req.checkout.quoteVersion+1,subtotal:120,shippingFee:0,totalAmount:120,currency:'CNY'});res.json(req.checkout);
 });
 core.post('/api/checkouts/:id/confirm',(req,res)=>{
   if(req.body.quoteVersion!==req.checkout.quoteVersion) return res.status(409).json({message:'Stale version'});
   req.checkout.status='CONFIRMED';res.json(req.checkout);
 });
 core.post('/api/checkouts/:id/complete',(req,res)=>{
   const c=req.checkout;
   if(req.body.quoteVersion!==c.quoteVersion || !['CONFIRMED','COMPLETED'].includes(c.status)) return res.status(409).json({message:'Not confirmed'});
   if(c.key && c.key!==req.body.idempotencyKey) return res.status(409).json({message:'Wrong key'});
   c.key=req.body.idempotencyKey;c.status='COMPLETED';c.orderId=100+c.checkoutId;
   res.json({orderId:c.orderId,totalAmount:120,status:'PAYMENT_PENDING',items:c.items});
 });
 core.get('/api/orders/:id/delivery-events',(req,res)=>{
   if(failOrders) return res.status(503).json({message:'Unavailable'});
   res.json([{shipmentId:7,status:'PICKUP',occurredAt:'2026-10-04T00:00:00Z'}]);
 });
 core.get('/api/orders/:id',(req,res)=>{
   if(failOrders) return res.status(503).json({message:'Core offline'});
   res.json({orderId:Number(req.params.id),status:'PAID',totalAmount:120,items:[{skuId:11,quantity:1}]});
 });
 core.post('/api/orders/:id/cancel',(req,res)=>res.json({orderId:Number(req.params.id),status:'CANCELLED'}));
 coreServer=await listen(core);
 process.env.TRADE_CORE_BASE_URL=`http://127.0.0.1:${coreServer.address().port}`;
 dbModule=require('../server/db');
 const db=dbModule.getDb();
 for(const id of [1,2,3]) db.prepare('INSERT INTO users(id,email,password_hash,name) VALUES(?,?,?,?)').run(id,`u${id}@test`,'unused',`u${id}`);
 auth=require('../server/middleware/auth'); identity=require('../server/services/coreIdentity');
 const app=express();app.use(express.json());
 app.use('/api/auth',require('../server/routes/auth'));
 app.use('/api/checkout',require('../server/routes/checkout'));
 app.use('/api/orders',require('../server/routes/orders'));
 app.use('/api/chat',require('../server/routes/chat'));
 bffServer=await listen(app);base=`http://127.0.0.1:${bffServer.address().port}`;
});
after(async()=>{
 await Promise.all([coreServer,bffServer].filter(Boolean).map(s=>new Promise(resolve=>s.close(resolve))));
 dbModule?.closeDb();
 // Remove only this test's OS-created scratch directory, never a configured database.
 assert.ok(temp.startsWith(path.join(os.tmpdir(),'commerce-bff-')));
 fs.rmSync(temp,{recursive:true,force:true});
});
test('anonymous and legacy default user cannot create core checkouts',async()=>{
 assert.equal((await create(null)).status,401);
 assert.equal((await create()).status,401);
 assert.equal(calls.length,0);
});
test('explicit linking is one-to-one and does not expose plaintext core tokens',async()=>{
 assert.equal((await link(1)).status,200);
 assert.equal((await request('/api/auth/core-link',2,'POST',{username:'user1',password:'secret'})).status,409);
 assert.equal((await link(2)).status,200);
 assert.equal((await request('/api/auth/core-link',1,'POST',{username:'user2',password:'secret'})).status,409);
 const row=dbModule.getDb().prepare('SELECT * FROM core_account_links WHERE front_user_id=1').get();
 assert.ok(!row.token_cipher.includes('eyJ'));
 assert.equal((await request('/api/auth/core-link')).body.token,undefined);
});
test('multi-line cart forwards actual identity and never implicitly confirms',async()=>{
 const result=await create();assert.equal(result.status,200);assert.ok(result.body.idempotencyKey);
 assert.equal(result.body.items.length,2);assert.equal(result.body.userId,1);assert.equal(result.body.status,'DRAFT');
 assert.equal((await request(`/api/checkout/${result.body.checkoutId}`,2)).status,404);
 assert.equal((await request(`/api/checkout/${result.body.checkoutId}/complete`,1,'POST',{quoteVersion:1})).status,409);
});
test('invalid quantities, duplicate SKU and unmapped local IDs fail before core call',async()=>{
 const n=calls.length;
 for(const items of [[{skuId:1,quantity:0}],[{skuId:1,quantity:1.2}],[{skuId:1,quantity:'1'}],[{skuId:1,quantity:1},{skuId:1,quantity:1}],[{product_id:999,quantity:1}]]) {
   assert.equal((await create(1,items)).status,400);
 }
 assert.equal(calls.length,n);
});
test('confirm requires the displayed version; stale versions reach core and conflict',async()=>{
 const c=(await create()).body;
 assert.equal((await request(`/api/checkout/${c.checkoutId}/confirm`,1,'POST',{})).status,400);
 await request(`/api/checkout/${c.checkoutId}/quote`,1,'POST',{});
 assert.equal((await request(`/api/checkout/${c.checkoutId}/confirm`,1,'POST',{quoteVersion:2})).status,409);
 assert.equal((await request(`/api/checkout/${c.checkoutId}/confirm`,1,'POST',{quoteVersion:1})).status,200);
});
test('complete replay repairs failed projection with persisted key after SQLite reconnect',async()=>{
 const c=await ready(); const db=dbModule.getDb();
 db.exec("CREATE TRIGGER fail_projection BEFORE INSERT ON orders BEGIN SELECT RAISE(FAIL,'injected failure'); END;");
 const path=`/api/checkout/${c.checkoutId}/complete`;
 assert.equal((await request(path,1,'POST',{quoteVersion:1})).status,502);
 assert.equal(sessions.get(c.checkoutId).status,'COMPLETED');
 db.exec('DROP TRIGGER fail_projection');dbModule.closeDb();
 assert.equal((await request(`/api/checkout/${c.checkoutId}`)).body.idempotencyKey,c.idempotencyKey);
 const replay=await request(path,1,'POST',{quoteVersion:1,bankMock:'FAIL'});
 assert.equal(replay.status,200);
 const again=await request(path,1,'POST',{quoteVersion:1});
 assert.equal(again.body.local_order_id,replay.body.local_order_id);
 assert.equal(dbModule.getDb().prepare('SELECT count(*) n FROM orders WHERE mid_checkout_id=?').get(c.checkoutId).n,1);
 assert.equal(calls.at(-1).body.bankMock,undefined);
 assert.equal((await request(path,1,'POST',{quoteVersion:1},{'Idempotency-Key':'different'})).status,409);
});
test('flag-off recovery still uses core; ownership checks and unavailable state never fall back',async()=>{
 const c=await ready();process.env.USE_TRADE_CORE='false';
 try {
   assert.equal((await create()).status,503);
   const order=(await request(`/api/checkout/${c.checkoutId}/complete`,1,'POST',{quoteVersion:1})).body;
   const url=`/api/orders/${order.local_order_id}`;
   assert.equal((await request(url,null)).status,401);assert.equal((await request(url,2)).status,403);
   assert.equal((await request(url)).body.status,'PAID');
   assert.equal((await request('/api/orders/mine')).body.orders[0].stale_projection,true);
   failOrders=true;const result=await request(url);assert.equal(result.status,503);assert.equal(result.body.stale_projection,true);failOrders=false;
   assert.equal((await request(url+'/pay',1,'POST',{})).status,409);
   assert.equal((await request(url+'/cancel',1,'POST',{})).body.order.status,'CANCELLED');
 } finally {process.env.USE_TRADE_CORE='true';failOrders=false;}
});
test('chat protects order reads and does not reuse another account chat memory',async()=>{
 const order=dbModule.getDb().prepare('SELECT * FROM orders LIMIT 1').get();
 const chat=require('../server/services/chatService').chat;
 const first=await chat({userId:1,message:`查订单 ${order.order_no}`});
 assert.ok(first.reply.includes('PAID'));
 const stranger=await chat({userId:2,sessionId:first.session_id,message:`查订单 ${order.order_no}`});
 assert.notEqual(stranger.session_id,first.session_id);assert.ok(stranger.reply.includes('未找到属于当前账号的订单'));
 const anon=await chat({sessionId:first.session_id,message:`查订单 ${order.order_no}`});
 assert.notEqual(anon.session_id,first.session_id);assert.ok(anon.reply.includes('请先登录'));
 failOrders=true;const unavailable=await chat({userId:1,message:`查订单 ${order.order_no}`});failOrders=false;
 assert.ok(unavailable.reply.includes('unavailable'));assert.ok(!unavailable.reply.includes('PAYMENT_PENDING'));
});
test('logout revokes core credential while preserving account association',async()=>{
 assert.equal((await request('/api/auth/logout',1,'POST',{})).status,200);
 assert.equal((await create()).status,401);
 assert.deepEqual((await request('/api/auth/core-link')).body,{linked:true,authenticated:false,coreUserId:1});
 assert.equal((await link(1)).status,200);
});

test('server-side recovery lists only owned checkouts after database reconnect without browser state',async()=>{
 const own=(await create(1)).body;const other=(await create(2)).body;
 dbModule.closeDb();
 const count=calls.filter(c=>c.path.endsWith('/complete')).length;
 assert.equal((await request('/api/checkout',null)).status,401);
 const result=await request('/api/checkout');assert.equal(result.status,200);
 assert.ok(result.body.checkouts.some(c=>c.checkoutId===own.checkoutId));
 assert.ok(!result.body.checkouts.some(c=>c.checkoutId===other.checkoutId));
 assert.equal(calls.filter(c=>c.path.endsWith('/complete')).length,count);
 assert.equal((await request('/api/checkout?before=invalid')).status,400);
});
test('checkout recovery pagination is stable and includes drafts beyond the first page',async()=>{
 for(let i=0;i<21;i++) assert.equal((await create(2)).status,200);
 const page1=(await request('/api/checkout',2)).body;
 assert.equal(page1.checkouts.length,20);assert.ok(page1.nextCursor);
 const page2=(await request(`/api/checkout?before=${page1.nextCursor}`,2)).body;
 assert.ok(page2.checkouts.length>0);
 const ids=new Set(page1.checkouts.map(c=>c.checkoutId));
 assert.ok(page2.checkouts.every(c=>!ids.has(c.checkoutId) && c.userId===2));
});
test('completed but unprojected checkout can be discovered and repaired without new order',async()=>{
 const c=await ready();const db=dbModule.getDb();
 db.exec("CREATE TRIGGER recovery_failure BEFORE INSERT ON orders BEGIN SELECT RAISE(FAIL,'injected failure'); END;");
 assert.equal((await request(`/api/checkout/${c.checkoutId}/complete`,1,'POST',{quoteVersion:1})).status,502);
 db.exec('DROP TRIGGER recovery_failure');dbModule.closeDb();
 const recovered=(await request('/api/checkout')).body.checkouts.find(x=>x.checkoutId===c.checkoutId);
 assert.equal(recovered.status,'COMPLETED');assert.equal(recovered.localOrderId,null);
 const result=await request(`/api/checkout/${c.checkoutId}/complete`,1,'POST',{quoteVersion:recovered.quoteVersion});
 assert.equal(result.status,200);assert.equal(result.body.orderId,recovered.orderId);
 const listed=(await request('/api/checkout')).body.checkouts.find(x=>x.checkoutId===c.checkoutId);
 assert.equal(listed.localOrderId,result.body.local_order_id);
});
test('recovery list marks unavailable core records instead of fabricating status',async()=>{
 const c=(await create()).body;sessions.delete(c.checkoutId);
 const row=(await request('/api/checkout')).body.checkouts.find(x=>x.checkoutId===c.checkoutId);
 assert.equal(row.unavailable,true);assert.equal(row.status,undefined);assert.equal(row.idempotencyKey,undefined);
});
test('delivery history is owned, live and remains on core when new checkouts disabled',async()=>{
 const order=dbModule.getDb().prepare('SELECT * FROM orders LIMIT 1').get();
 const url=`/api/orders/${order.id}/delivery-events`;
 assert.equal((await request(url,null)).status,401);
 assert.equal((await request(url,2)).status,404);
 process.env.USE_TRADE_CORE='false';
 try {
   const result=await request(url);assert.equal(result.status,200);assert.equal(result.body.events[0].status,'PICKUP');
   failOrders=true;assert.equal((await request(url)).status,503);
 } finally {failOrders=false;process.env.USE_TRADE_CORE='true';}
});
test('chat shopping route uses live core candidates without transaction writes',async()=>{
 const before=calls.filter(c=>c.method==='POST').length;
 const result=await request('/api/chat',1,'POST',{message:'推荐滑板，预算100元'});
 assert.equal(result.status,200);assert.equal(result.body.mode,'catalog-guidance');
 assert.deepEqual(result.body.shopping.products.map(p=>p.id),[11]);
 assert.equal(result.body.shopping.products[0].source,'trade_core');
 assert.equal(calls.filter(c=>c.method==='POST').length,before);
});
test('shopping preferences survive DB reconnect and remain scoped to verified session owner',async()=>{
 const first=(await request('/api/chat',1,'POST',{message:'推荐滑板，预算200元'})).body;
 dbModule.closeDb();
 const next=(await request('/api/chat',1,'POST',{session_id:first.session_id,message:'预算降到100元'})).body;
 assert.equal(next.shopping.constraints.query,'skateboard');assert.equal(next.shopping.constraints.priceMax,100);
 const stranger=(await request('/api/chat',2,'POST',{session_id:first.session_id,message:'预算50元'})).body;
 assert.notEqual(stranger.session_id,first.session_id);assert.equal(stranger.shopping.constraints.query,'');
 const reset=(await request('/api/chat',1,'POST',{session_id:first.session_id,message:'重新选'})).body;
 assert.equal(reset.shopping.status,'reset');
 const fresh=(await request('/api/chat',1,'POST',{session_id:first.session_id,message:'预算80元'})).body;
 assert.equal(fresh.shopping.constraints.query,'');
});
test('older shopping replies cannot overwrite newer preference turns and expired filters are forgotten',()=>{
 const advisor=require('../server/services/shoppingAdvisor');
 const sid=dbModule.getDb().prepare('SELECT id FROM chat_sessions LIMIT 1').get().id;
 advisor.savePreferences(sid,10000,{query:'wheels',priceMax:80});
 advisor.savePreferences(sid,9999,{query:'skateboard',priceMax:200});
 assert.equal(advisor.loadPreferences(sid).query,'wheels');
 dbModule.getDb().prepare('UPDATE shopping_preferences SET updated_at=0 WHERE session_id=?').run(sid);
 assert.deepEqual(advisor.loadPreferences(sid),{});
});