// Run only against a disposable local demo stack. Creates one paid demo order per run.
const assert = require('node:assert/strict');
const { setTimeout: delay } = require('node:timers/promises');

async function run({ base = 'http://127.0.0.1:3001', fetchImpl = fetch, timeoutMs = 180000, log = console.log } = {}) {
  const origin = new URL(base);
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname), 'Only loopback demo endpoints are allowed');
  assert.equal(origin.protocol, 'http:');
  assert.ok(!origin.username && !origin.password && !origin.search && !origin.hash);
  let token;
  async function api(path, { method = 'GET', body, expected = 200, anonymous = false, headers = {} } = {}) {
    const response = await fetchImpl(new URL(path, origin), {
      method, signal: AbortSignal.timeout(15000),
      headers: { 'Content-Type': 'application/json', ...(!anonymous && token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    assert.equal(response.status, expected, `${method} ${path}: HTTP ${response.status}, expected ${expected}`);
    return response.json();
  }
  const health = await api('/api/health');
  assert.equal(health.trade_core?.enabled, true, 'USE_TRADE_CORE must be enabled');
  const login = await api('/api/auth/login', { method: 'POST', body: { email: 'buyer@oldphonestore.demo', password: 'buyer123' } });
  assert.ok(login.token); token = login.token;
  await api('/api/auth/core-link', { method: 'POST', body: { username: 'customer', password: 'COMP5348' } });
  log('PASS storefront login and explicit core account link');
  const catalog = await api('/api/products');
  assert.equal(catalog.source, 'trade_core');
  const products = catalog.items.filter(p => p.available && Number(p.price) > 0).sort((a,b) => a.price-b.price).slice(0,2);
  assert.equal(products.length, 2, 'Need at least two core products');
  const shippingAddress = 'Integration test address - no real shipment';
  const checkout = await api('/api/checkout', { method: 'POST', body: { items: products.map(p => ({skuId:p.skuId,quantity:1})), shippingAddress } });
  assert.ok(checkout.idempotencyKey);
  const prefix = `/api/checkout/${checkout.checkoutId}`;
  // Keep IDs in output so a timeout can be investigated without creating another order.
  log(`CHECKOUT ${checkout.checkoutId}`);
  await api(prefix, { anonymous:true, expected:401 });
  const quote = await api(prefix+'/quote', {method:'POST',body:{}});
  assert.equal(quote.status,'QUOTED'); assert.equal(quote.items.length,2);
  const cents = n => Math.round(Number(n)*100);
  assert.equal(cents(quote.totalAmount), cents(quote.subtotal)+cents(quote.shippingFee));
  await api(prefix+'/confirm', {method:'POST',body:{quoteVersion:quote.quoteVersion+1},expected:409});
  const confirmed = await api(prefix+'/confirm', {method:'POST',body:{quoteVersion:quote.quoteVersion}});
  assert.equal(confirmed.status,'CONFIRMED');
  const submit = () => api(prefix+'/complete', {method:'POST',body:{quoteVersion:quote.quoteVersion},headers:{'Idempotency-Key':checkout.idempotencyKey}});
  const order = await submit();
  log(`ORDER core=${order.orderId} storefront=${order.local_order_id}`);
  assert.ok(order.orderId && order.local_order_id);
  assert.equal(cents(order.totalAmount),cents(quote.totalAmount));
  assert.equal(order.shippingAddress,shippingAddress);
  assert.equal(order.quoteVersion,quote.quoteVersion);
  const quantities = new Map();
  for(const line of order.items) quantities.set(line.skuId,(quantities.get(line.skuId)||0)+line.quantity);
  assert.deepEqual([...quantities].sort(),products.map(p=>[p.skuId,1]).sort());
  const replay = await submit();
  assert.equal(replay.orderId,order.orderId); assert.equal(replay.local_order_id,order.local_order_id);
  await api(prefix+'/complete',{method:'POST',body:{quoteVersion:quote.quoteVersion},headers:{'Idempotency-Key':'wrong-key'},expected:409});
  log('PASS multi-line snapshots, stale quote rejection and idempotent replay');
  const deadline=Date.now()+timeoutMs;
  let previous;
  while(Date.now()<deadline) {
    const current = await api(`/api/orders/${order.local_order_id}`);
    assert.equal(current.mid.orderId,order.orderId);
    if(current.status!==previous) {log(`STATUS ${current.status}`);previous=current.status;}
    assert.ok(!['FAILED','CANCELLED'].includes(current.status),`Unexpected terminal status ${current.status}`);
    if(current.status==='FULFILLED') {
      log('PASS payment and delivery callback reached FULFILLED');
      return {checkoutId:checkout.checkoutId,orderId:order.orderId,localOrderId:order.local_order_id,status:current.status};
    }
    await delay(2000);
  }
  throw new Error(`Delivery timeout: order ${order.orderId}, last status ${previous}. Inspect service logs; do not treat as a pass.`);
}
if(require.main===module) {
  if(!process.argv.includes('--local-demo')) {
    console.error('Usage: node scripts/integration-smoke.cjs --local-demo [http://127.0.0.1:3001]. This spends simulated balance and stock. Use an isolated demo stack.');
    process.exitCode=2;
  } else {
    const base=process.argv.find(arg=>arg.startsWith('http'));
    run({base}).then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error('FAIL',error.message);process.exitCode=1;});
  }
}
module.exports={run};