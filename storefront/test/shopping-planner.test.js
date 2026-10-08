const {test}=require('node:test');
const assert=require('node:assert/strict');
const {validatePlan,planShopping}=require('../server/services/shoppingPlanner');
const {recommend}=require('../server/services/shoppingAdvisor');
const answer=(name,args)=>({choices:[{message:{tool_calls:[{type:'function',function:{name,arguments:JSON.stringify(args)}}]}}]});
const config={enabled:true,baseUrl:'https://model.example.test/v1',model:'test-model',apiKey:'test-key'};
test('tool dispatcher rejects transaction tools, surplus arguments and multiple calls',()=>{
 for(const data of [answer('complete_checkout',{}),answer('search_catalog',{query:'phone',priceMax:9999}),answer('search_catalog',{query:''}),{choices:[{message:{tool_calls:[]}}]}])assert.throws(()=>validatePlan(data));
 const data=answer('search_catalog',{query:'phone'});data.choices[0].message.tool_calls.push(data.choices[0].message.tool_calls[0]);assert.throws(()=>validatePlan(data));
});
test('configured planner sends only read tools and parses a single provider call',async()=>{
 process.env.SHOPPING_MODEL_ENABLED='true';
 try{
  const result=await planShopping('推荐滑板',{}, {config,fetchImpl:async(_url,options)=>{
    const body=JSON.parse(options.body);assert.deepEqual(body.tools.map(t=>t.function.name),['search_catalog','ask_clarification']);
    assert.equal(body.parallel_tool_calls,false);assert.ok(options.signal);
    return {ok:true,text:async()=>JSON.stringify(answer('search_catalog',{query:'skateboard'}))};
  }});
  assert.equal(result.mode,'model-tool');assert.equal(result.plan.query,'skateboard');
 }finally{delete process.env.SHOPPING_MODEL_ENABLED;}
});
test('provider failure or invalid calls fail into explicit rule fallback',async()=>{
 process.env.SHOPPING_MODEL_ENABLED='true';
 try{
  for(const fetchImpl of [async()=>{throw new Error('timeout');},async()=>({ok:false}),async()=>({ok:true,text:async()=>JSON.stringify(answer('pay',{}))})]){
    const result=await planShopping('推荐滑板',{}, {config,fetchImpl});assert.equal(result.mode,'rules-fallback');assert.equal(result.plan,null);
  }
 }finally{delete process.env.SHOPPING_MODEL_ENABLED;}
});
test('model-selected query cannot relax an explicit budget',async()=>{
 const result=await recommend('推荐滑板，预算100元',{planner:async()=>({mode:'model-tool',plan:{tool:'search_catalog',query:'skateboard'}}),search:async c=>{
   assert.equal(c.priceMax,100);return [{id:1,name:'Affordable',price:80},{id:2,name:'Over budget',price:101}];
 }});
 assert.equal(result.plannerMode,'model-tool');assert.deepEqual(result.products.map(p=>p.id),[1]);
});
test('model clarification does not query catalog or overwrite preference filters',async()=>{
 const result=await recommend('推荐通勤装备',{planner:async()=>({mode:'model-tool',plan:{tool:'ask_clarification',question:'需要哪类商品？'}}),search:async()=>{throw new Error('Must not query');}});
 assert.equal(result.status,'clarification');assert.ok(result.constraints.clarify);assert.deepEqual(result.products,[]);
});