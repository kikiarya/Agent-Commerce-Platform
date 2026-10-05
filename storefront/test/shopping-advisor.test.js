const {test}=require('node:test');
const assert=require('node:assert/strict');
const {parseRequest,recommend}=require('../server/services/shoppingAdvisor');
test('budget extraction preserves product name and supports unit multipliers',()=>{
 assert.deepEqual(parseRequest('推荐滑板，预算100元'),{query:'skateboard',priceMax:100,purpose:null});
 assert.equal(parseRequest('推荐 iPhone 15，预算3千元').priceMax,3000);
 assert.equal(parseRequest('推荐 wheels 100元以内').priceMax,100);
});
test('recommendations enforce budget, sort prices and identify data source',async()=>{
 let observed;
 const result=await recommend('推荐滑板，预算100元',{useCore:true,search:async c=>{observed=c;return [{id:1,name:'Expensive',price:101},{id:2,name:'Entry',price:80},{id:3,name:'Basic',price:60}];}});
 assert.equal(observed.query,'skateboard');assert.deepEqual(result.products.map(p=>p.id),[3,2]);
 assert.equal(result.products[0].source,'trade_core');assert.ok(result.reply.includes('不包含运费'));
});
test('unavailable catalog never falls back to fabricated or stale candidates',async()=>{
 const result=await recommend('推荐滑板',{search:async()=>{throw new Error('offline');}});
 assert.equal(result.status,'unavailable');assert.deepEqual(result.products,[]);
});
test('unsupported exclusions and foreign currency request clarification without a tool call',async()=>{
 for(const message of ['推荐手机，不要苹果','推荐手机，预算100美元','推荐滑板，预算0元']){
  const result=await recommend(message,{search:async()=>{throw new Error('Should not call');}});
  assert.equal(result.status,'clarification');
 }
});
test('purpose does not become an unsupported performance claim',async()=>{
 const result=await recommend('推荐滑板，预算100元，用于通勤',{search:async()=>[{id:1,name:'Board',price:50}]});
 assert.equal(result.constraints.purpose,'通勤');assert.ok(result.reply.includes('不足以验证'));
});
test('empty and malformed catalog data are not recommendations',async()=>{
 assert.equal((await recommend('推荐滑板',{search:async()=>[]})).status,'empty');
 assert.equal((await recommend('推荐滑板',{search:async()=>({error:'bad'})})).status,'unavailable');
});
test('follow-up edits inherit explicit filters and reset clears them',()=>{
 const initial=parseRequest('推荐滑板，预算200元，用于通勤');
 assert.deepEqual(parseRequest('预算降到100元',initial),{query:'skateboard',priceMax:100,purpose:'通勤'});
 assert.equal(parseRequest('换成轮子',initial).query,'wheels');
 assert.equal(parseRequest('不限预算',initial).priceMax,null);
 assert.equal(parseRequest('重新选',initial).reset,true);
});