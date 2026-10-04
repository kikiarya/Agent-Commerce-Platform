const express = require('express');
const { authRequired } = require('../middleware/auth');
const trade = require('../services/tradeCoreClient');
const identity = require('../services/coreIdentity');
const { resolveMidSkuId } = require('../services/userMap');
const { getDb } = require('../db');
const router = express.Router();
router.use(authRequired);

function normalize(items) {
  if (!Array.isArray(items) || !items.length || items.length > 100) throw identity.problem(400, 'Cart must contain 1 to 100 items');
  const seen = new Set();
  return items.map(it => {
    if (!it || !Number.isSafeInteger(it.quantity) || it.quantity < 1) throw identity.problem(400, 'Quantity must be a positive integer');
    // skuId is explicitly in the core catalog namespace; local IDs require a map.
    const skuId = it.skuId != null ? Number(it.skuId) : resolveMidSkuId(Number(it.product_id ?? it.phone_id));
    if (!Number.isSafeInteger(skuId) || skuId < 1 || seen.has(skuId)) throw identity.problem(400, 'Missing SKU map or duplicate/invalid SKU');
    seen.add(skuId);
    return { skuId, quantity: it.quantity };
  });
}
function version(body) {
  if (!Number.isSafeInteger(body?.quoteVersion) || body.quoteVersion < 1) throw identity.problem(400, 'Displayed quoteVersion required');
  return body.quoteVersion;
}
function project(userId, order, checkoutId, key) {
  const db = getDb();
  return db.transaction(() => {
    const existing = db.prepare('SELECT id, user_id FROM orders WHERE mid_order_id=?').get(order.orderId);
    if (existing && existing.user_id !== userId) throw identity.problem(409, 'Legacy order ownership needs reconciliation');
    if (existing) {
      db.prepare("UPDATE orders SET status=?, total=?, refund_status=?, updated_at=datetime('now') WHERE id=?")
        .run(order.status, order.totalAmount, order.refundStatus || null, existing.id);
      return existing.id;
    }
    return db.prepare(`INSERT INTO orders (total,status,user_id,order_no,idempotency_key,source,mid_order_id,mid_checkout_id,refund_status)
      VALUES (?,?,?,?,?,'trade_core',?,?,?)`).run(order.totalAmount,order.status,userId,`MID-${order.orderId}`,key,order.orderId,checkoutId,order.refundStatus || null).lastInsertRowid;
  })();
}
function route(action) {
  return async (req,res) => {
    try { await action(req,res); }
    catch (error) { res.status(error.status || 502).json({ error: error.message }); }
  };
}
router.post('/', route(async (req,res) => {
  if (!trade.useTradeCore()) throw identity.problem(503, 'New core checkouts disabled');
  const view = await trade.createCheckout(req.user.id, {
    items: normalize(req.body?.items), shippingAddress: req.body?.shippingAddress ?? req.body?.shipping_address ?? ''
  });
  const idempotencyKey = identity.rememberCheckout(req.user.id, view.checkoutId);
  res.json({ ...view, idempotencyKey });
}));
// Read only: listing never confirms, completes or creates an order.
router.get('/', route(async (req, res) => {
  identity.tokenFor(req.user.id);
  const before = req.query.before === undefined ? Number.MAX_SAFE_INTEGER : Number(req.query.before);
  if (!Number.isSafeInteger(before) || before < 1) throw identity.problem(400, 'Invalid pagination cursor');
  const db = getDb();
  const rows = db.prepare(`SELECT c.checkout_id, o.id AS local_order_id
    FROM core_checkout_owners c LEFT JOIN orders o ON o.mid_checkout_id=c.checkout_id AND o.user_id=c.front_user_id
    WHERE c.front_user_id=? AND c.checkout_id<? ORDER BY c.checkout_id DESC LIMIT 21`).all(req.user.id, before);
  const selected = rows.slice(0, 20);
  const checkouts = [];
  for (let start=0; start<selected.length; start+=4) {
    checkouts.push(...await Promise.all(selected.slice(start,start+4).map(async row => {
      try {
        const view = await trade.getCheckout(req.user.id, row.checkout_id);
        return {...view, localOrderId:row.local_order_id, unavailable:false};
      } catch (error) {
        return {checkoutId:row.checkout_id, unavailable:true, errorStatus:error.status || 502};
      }
    })));
  }
  res.json({checkouts, nextCursor:rows.length>20 ? selected.at(-1).checkout_id : null});
}));
router.use('/:id', (req,res,next) => {
  try {
    if (!/^\d+$/.test(req.params.id) || !Number.isSafeInteger(Number(req.params.id))) throw identity.problem(400, 'Invalid checkout ID');
    req.checkoutOwner = identity.ownCheckout(req.user.id, Number(req.params.id)); next();
  } catch (error) { res.status(error.status || 400).json({ error: error.message }); }
});
router.get('/:id', route(async(req,res) => res.json({
  ...await trade.getCheckout(req.user.id, req.params.id), idempotencyKey: req.checkoutOwner.idempotency_key
})));
router.put('/:id', route(async(req,res) => {
  const body = {};
  if (req.body?.items !== undefined) body.items = normalize(req.body.items);
  if (req.body?.shippingAddress !== undefined || req.body?.shipping_address !== undefined)
    body.shippingAddress = req.body.shippingAddress ?? req.body.shipping_address;
  res.json(await trade.updateCheckout(req.user.id, req.params.id, body));
}));
router.post('/:id/quote', route(async(req,res) => res.json(await trade.quoteCheckout(req.user.id, req.params.id))));
router.post('/:id/confirm', route(async(req,res) => res.json(await trade.confirmCheckout(req.user.id, req.params.id, version(req.body)))));
router.post('/:id/complete', route(async(req,res) => {
  const quoteVersion = version(req.body);
  const key = req.checkoutOwner.idempotency_key;
  const supplied = req.headers['idempotency-key'] ?? req.body.idempotencyKey;
  if (supplied !== undefined && supplied !== key) throw identity.problem(409, 'Use the persisted checkout idempotency key');
  const order = await trade.completeCheckout(req.user.id, req.params.id, { idempotencyKey: key, quoteVersion });
  const localId = project(req.user.id, order, Number(req.params.id), key);
  res.json({ ...order, local_order_id: localId, order_no: `MID-${order.orderId}` });
}));
module.exports = router;
