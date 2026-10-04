const {test}=require('node:test');
const assert=require('node:assert/strict');
const {run}=require('../../scripts/integration-smoke.cjs');
function fixture({wrongReplay=false, terminal='FULFILLED'}={}) {
  let completed=0;
  return async(url,options)=>{
    const path=url.pathname;
    const body=options.body && JSON.parse(options.body);
    let status=200,data={};
    if(path==='/api/health') data={trade_core:{enabled:true}};
    else if(path==='/api/auth/login') data={token:'test-token'};
    else if(path==='/api/auth/core-link') data={linked:true};
    else if(path==='/api/products') data={source:'trade_core',items:[{skuId:11,price:10,available:true},{skuId:22,price:20,available:true}]};
    else if(path==='/api/checkout') {assert.deepEqual(body.items,[{skuId:11,quantity:1},{skuId:22,quantity:1}]);data={checkoutId:1,idempotencyKey:'stable-key'};}
    else if(path==='/api/checkout/1') {assert.equal(options.headers.Authorization,undefined);status=401;}
    else if(path.endsWith('/quote')) data={status:'QUOTED',quoteVersion:1,items:[{},{}],subtotal:30,shippingFee:10,totalAmount:40};
    else if(path.endsWith('/confirm')) {status=body.quoteVersion===1?200:409;data={status:'CONFIRMED'};}
    else if(path.endsWith('/complete')) {
      if(options.headers['Idempotency-Key']==='wrong-key') status=409;
      else {assert.equal(options.headers['Idempotency-Key'],'stable-key');completed++;data={orderId:wrongReplay && completed>1?99:2,local_order_id:3,totalAmount:40,shippingAddress:'Integration test address - no real shipment',quoteVersion:1,items:[{skuId:11,quantity:1},{skuId:22,quantity:1}]};}
    } else if(path==='/api/orders/3') data={status:terminal,mid:{orderId:2}};
    else throw new Error('Unexpected path '+path);
    return {status,json:async()=>data};
  };
}
const log=()=>{};
test('smoke runner follows confirmation, replay and fulfilment contract',async()=>{
  assert.equal((await run({fetchImpl:fixture(),log})).status,'FULFILLED');
});
test('smoke runner fails if retry creates another order',async()=>{
  await assert.rejects(run({fetchImpl:fixture({wrongReplay:true}),log}));
});
test('smoke runner rejects failed payment instead of reporting success',async()=>{
  await assert.rejects(run({fetchImpl:fixture({terminal:'FAILED'}),log}),/Unexpected terminal/);
});
test('smoke runner reports timeout instead of reporting success',async()=>{
  await assert.rejects(run({fetchImpl:fixture(),timeoutMs:0,log}),/Delivery timeout/);
});
test('smoke runner refuses nonlocal endpoints before sending credentials',async()=>{
  await assert.rejects(run({base:'https://example.com',fetchImpl:()=>{throw new Error('Must not fetch');},log}),/loopback/);
});