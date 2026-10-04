const { getDb } = require('../db');

/**
 * Map front-end user → Trade Core user id.
 * Legacy user_id_map is retained only for reconciliation, never for authentication.
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
  resolveMidSkuId,
  setSkuMap
};
