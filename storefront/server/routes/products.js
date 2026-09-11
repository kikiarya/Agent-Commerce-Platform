const express = require('express');
const { getDb } = require('../db');
const { authOptional } = require('../middleware/auth');
const trade = require('../services/tradeCoreClient');
const { cacheGet, cacheSet } = require('../services/cacheService');

const router = express.Router();

/** Normalize local phone row → multi-category product DTO */
function toProduct(row) {
  if (!row) return null;
  const name = [row.brand, row.model].filter(Boolean).join(' ');
  return {
    id: row.id,
    skuId: row.id,
    sku: row.imei_masked || `LOCAL-${row.id}`,
    name,
    brand: row.brand,
    model: row.model,
    price: row.price,
    currency: 'CNY',
    category: row.category,
    condition: row.condition,
    available: !!row.available,
    attrs: {
      storage: row.storage,
      color: row.color,
      battery_health: row.battery_health,
      year: row.year,
      rating: row.rating,
      description: row.description
    },
    img: row.img,
    source: 'local'
  };
}

function toCoreProduct(p) {
  return {
    id: p.id,
    skuId: p.id,
    sku: p.sku,
    name: p.name,
    brand: null,
    model: p.name,
    price: Number(p.price),
    currency: 'CNY',
    category: 'General',
    condition: 'New',
    available: true,
    attrs: {},
    img: null,
    source: 'trade_core'
  };
}

/**
 * GET /api/products
 * Prefer Trade Core when USE_TRADE_CORE=true; else local multi-category catalog (phones table).
 */
router.get('/', authOptional, async (req, res) => {
  try {
    if (trade.useTradeCore()) {
      const q = req.query.q || '';
      const priceMax = req.query.priceMax != null ? Number(req.query.priceMax) : undefined;
      const list = q || priceMax != null
        ? await trade.searchProducts(q, priceMax, req.query.inStock === 'true' ? true : undefined)
        : await trade.listProducts();
      return res.json({
        source: 'trade_core',
        items: (list || []).map(toCoreProduct)
      });
    }

    const cacheKey = `products:list:${JSON.stringify(req.query)}`;
    const cached = await cacheGet(cacheKey);
    if (cached.hit && cached.value) {
      res.set('X-Cache-Source', 'redis');
      return res.json(cached.value);
    }

    const db = getDb();
    let sql = 'SELECT * FROM phones WHERE 1=1';
    const params = [];
    if (req.query.q) {
      sql += ' AND (brand LIKE ? OR model LIKE ? OR category LIKE ? OR description LIKE ?)';
      const like = `%${req.query.q}%`;
      params.push(like, like, like, like);
    }
    if (req.query.category && req.query.category !== 'All') {
      sql += ' AND category = ?';
      params.push(req.query.category);
    }
    if (req.query.condition && req.query.condition !== 'All') {
      sql += ' AND condition = ?';
      params.push(req.query.condition);
    }
    if (req.query.available === 'true') sql += ' AND available = 1';
    if (req.query.available === 'false') sql += ' AND available = 0';
    if (req.query.priceMax != null) {
      sql += ' AND price <= ?';
      params.push(Number(req.query.priceMax));
    }
    sql += ' ORDER BY id DESC';
    const rows = db.prepare(sql).all(...params);
    const body = { source: 'local', items: rows.map(toProduct) };
    await cacheSet(cacheKey, body, 30);
    res.set('X-Cache-Source', 'db');
    return res.json(body);
  } catch (err) {
    console.error('[products]', err.message);
    return res.status(err.status || 502).json({ error: err.message, detail: err.data });
  }
});

router.get('/meta', (_req, res) => {
  const db = getDb();
  const categories = db.prepare('SELECT DISTINCT category FROM phones ORDER BY category').all().map((r) => r.category);
  const conditions = db.prepare('SELECT DISTINCT condition FROM phones ORDER BY condition').all().map((r) => r.condition);
  const stats = db
    .prepare(
      `SELECT COUNT(*) AS total,
              SUM(CASE WHEN available = 1 THEN 1 ELSE 0 END) AS in_stock,
              MIN(price) AS min_price,
              MAX(price) AS max_price
       FROM phones`
    )
    .get();
  res.json({
    categories,
    conditions,
    currency: 'CNY',
    catalogMode: trade.useTradeCore() ? 'trade_core' : 'local_multi_category',
    stats
  });
});

router.get('/:id', async (req, res) => {
  try {
    if (trade.useTradeCore()) {
      const p = await trade.getProduct(req.params.id);
      if (!p) return res.status(404).json({ error: 'Not found' });
      res.set('X-Cache-Source', 'trade_core');
      return res.json(toCoreProduct(p));
    }
    const row = getDb().prepare('SELECT * FROM phones WHERE id = ?').get(Number(req.params.id));
    if (!row) return res.status(404).json({ error: 'Not found' });
    res.set('X-Cache-Source', 'db');
    return res.json(toProduct(row));
  } catch (err) {
    return res.status(err.status || 502).json({ error: err.message });
  }
});

module.exports = router;
