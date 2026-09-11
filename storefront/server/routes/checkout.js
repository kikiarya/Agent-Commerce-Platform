const express = require('express');
const { authRequired } = require('../middleware/auth');
const trade = require('../services/tradeCoreClient');
const { resolveMidUserId, resolveMidSkuId } = require('../services/userMap');
const { getDb } = require('../db');

const router = express.Router();

function requireTradeCore(res) {
  if (!trade.useTradeCore()) {
    res.status(503).json({
      error: 'Trade core disabled',
      hint: 'Set USE_TRADE_CORE=true and start Store on TRADE_CORE_BASE_URL'
    });
    return false;
  }
  return true;
}

function persistProjection(frontUserId, orderView, checkoutId, idempotencyKey) {
  const db = getDb();
  const midId = orderView.orderId;
  const existing = db.prepare('SELECT id FROM orders WHERE mid_order_id = ?').get(midId);
  if (existing) {
    db.prepare(
      `UPDATE orders SET status = ?, total = ?, refund_status = ?, updated_at = datetime('now') WHERE id = ?`
    ).run(orderView.status, orderView.totalAmount, orderView.refundStatus || null, existing.id);
    return existing.id;
  }
  const orderNo = `MID-${midId}`;
  const info = db
    .prepare(
      `INSERT INTO orders (
         customer_name, customer_email, total, status, user_id, order_no,
         idempotency_key, source, mid_order_id, mid_checkout_id, refund_status
       ) VALUES (?, ?, ?, ?, ?, ?, ?, 'trade_core', ?, ?, ?)`
    )
    .run(
      null,
      null,
      orderView.totalAmount,
      orderView.status,
      frontUserId,
      orderNo,
      idempotencyKey || null,
      midId,
      checkoutId || null,
      orderView.refundStatus || null
    );
  return info.lastInsertRowid;
}

/** POST /api/checkout — create session from cart items */
router.post('/', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    const items = (req.body?.items || []).map((it) => {
      const frontId = Number(it.product_id ?? it.phone_id ?? it.skuId);
      const mid = resolveMidSkuId(frontId) ?? (trade.useTradeCore() ? frontId : null);
      if (!mid) {
        const err = new Error(`No SKU map for product ${frontId}. Set product_sku_map or TRADE_CORE_SKU_ID_EQUALS_FRONT=true`);
        err.status = 400;
        throw err;
      }
      return { skuId: mid, quantity: Math.max(1, Number(it.quantity) || 1) };
    });
    if (!items.length) return res.status(400).json({ error: 'Cart is empty' });
    if (items.length > 1) {
      return res.status(400).json({
        error: 'Stage-1 trade core complete supports single SKU only',
        hint: 'Checkout one item at a time, or wait for multi-line stage 2'
      });
    }
    const view = await trade.createCheckout(midUser, {
      items,
      shippingAddress: req.body?.shippingAddress || req.body?.shipping_address || ''
    });
    res.json(view);
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.get('/:id', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    res.json(await trade.getCheckout(midUser, req.params.id));
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.put('/:id', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    const body = {};
    if (req.body?.shippingAddress || req.body?.shipping_address) {
      body.shippingAddress = req.body.shippingAddress || req.body.shipping_address;
    }
    if (req.body?.items) {
      body.items = req.body.items.map((it) => ({
        skuId: resolveMidSkuId(Number(it.product_id ?? it.phone_id ?? it.skuId)) || Number(it.skuId),
        quantity: Math.max(1, Number(it.quantity) || 1)
      }));
    }
    res.json(await trade.updateCheckout(midUser, req.params.id, body));
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.post('/:id/quote', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    res.json(await trade.quoteCheckout(midUser, req.params.id));
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.post('/:id/confirm', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    res.json(await trade.confirmCheckout(midUser, req.params.id));
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.post('/:id/complete', authRequired, async (req, res) => {
  if (!requireTradeCore(res)) return;
  try {
    const midUser = resolveMidUserId(req.user.id);
    const idempotencyKey = String(
      req.headers['idempotency-key'] || req.body?.idempotencyKey || ''
    ).slice(0, 48);
    if (!idempotencyKey) {
      return res.status(400).json({ error: 'idempotencyKey required (max 48 chars)' });
    }
    const order = await trade.completeCheckout(midUser, req.params.id, {
      idempotencyKey,
      bankMock: req.body?.bankMock || null
    });
    const localId = persistProjection(req.user.id, order, Number(req.params.id), idempotencyKey);
    res.json({
      ...order,
      local_order_id: localId,
      order_no: `MID-${order.orderId}`
    });
  } catch (err) {
    res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

module.exports = router;
