const { getDb } = require('../db');

/**
 * Map front-end user → Trade Core user id.
 * V1 demo: TRADE_CORE_DEFAULT_USER_ID (default 1 = seeded "customer").
 */
function ensureUserMapSchema(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS user_id_map (
      front_user_id INTEGER PRIMARY KEY,
      mid_user_id INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
    CREATE TABLE IF NOT EXISTS product_sku_map (
      front_product_id INTEGER PRIMARY KEY,
      mid_product_id INTEGER NOT NULL,
      note TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);
}

function resolveMidUserId(frontUserId) {
  const db = getDb();
  ensureUserMapSchema(db);
  const row = db.prepare('SELECT mid_user_id FROM user_id_map WHERE front_user_id = ?').get(frontUserId);
  if (row) return row.mid_user_id;
  const mid = Number(process.env.TRADE_CORE_DEFAULT_USER_ID) || 1;
  db.prepare(
    'INSERT OR REPLACE INTO user_id_map (front_user_id, mid_user_id) VALUES (?, ?)'
  ).run(frontUserId, mid);
  return mid;
}

function resolveMidSkuId(frontProductId) {
  const db = getDb();
  ensureUserMapSchema(db);
  const row = db
    .prepare('SELECT mid_product_id FROM product_sku_map WHERE front_product_id = ?')
    .get(frontProductId);
  if (row) return row.mid_product_id;
  // Allow 1:1 id mapping for local dual-stack demos when catalogs were aligned
  if (String(process.env.TRADE_CORE_SKU_ID_EQUALS_FRONT || '').toLowerCase() === 'true') {
    return Number(frontProductId);
  }
  return null;
}

function setSkuMap(frontProductId, midProductId, note) {
  const db = getDb();
  ensureUserMapSchema(db);
  db.prepare(
    `INSERT OR REPLACE INTO product_sku_map (front_product_id, mid_product_id, note)
     VALUES (?, ?, ?)`
  ).run(frontProductId, midProductId, note || null);
}

module.exports = {
  ensureUserMapSchema,
  resolveMidUserId,
  resolveMidSkuId,
  setSkuMap
};
