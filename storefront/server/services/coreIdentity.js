const crypto = require('node:crypto');
const { getDb } = require('../db');

function problem(status, message) { return Object.assign(new Error(message), { status }); }
function encryptionKey() {
  const secret = process.env.CORE_TOKEN_ENCRYPTION_KEY || process.env.JWT_SECRET || '';
  if (Buffer.byteLength(secret) < 32) throw problem(503, 'Configure a secret of at least 32 bytes for core token storage');
  return crypto.createHash('sha256').update(`core-credentials-v1:${secret}`).digest();
}
function seal(token) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(), iv);
  const data = Buffer.concat([cipher.update(token, 'utf8'), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), data]).toString('base64');
}
function unseal(value) {
  const bytes = Buffer.from(value, 'base64');
  const cipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(), bytes.subarray(0, 12));
  cipher.setAuthTag(bytes.subarray(12, 28));
  return Buffer.concat([cipher.update(bytes.subarray(28)), cipher.final()]).toString('utf8');
}
function schema() {
  const db = getDb();
  db.exec(`CREATE TABLE IF NOT EXISTS core_account_links (
    front_user_id INTEGER PRIMARY KEY, core_user_id INTEGER NOT NULL UNIQUE,
    token_cipher TEXT, expires_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS core_checkout_owners (
    checkout_id INTEGER PRIMARY KEY, front_user_id INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE);`);
  db.exec('CREATE INDEX IF NOT EXISTS idx_checkout_owner_page ON core_checkout_owners(front_user_id, checkout_id)');
  return db;
}
function saveLink(frontUserId, login) {
  if (!Number.isSafeInteger(login.userId) || !login.token) throw problem(502, 'Invalid core login response');
  let expires;
  try { expires = JSON.parse(Buffer.from(login.token.split('.')[1], 'base64url').toString()).exp * 1000; }
  catch { throw problem(502, 'Invalid core token response'); }
  if (!Number.isFinite(expires) || expires <= Date.now()) throw problem(502, 'Core token already expired');
  const ciphertext = seal(login.token);
  const db = schema();
  db.transaction(() => {
    const existing = db.prepare('SELECT * FROM core_account_links WHERE front_user_id = ?').get(frontUserId);
    if (existing && existing.core_user_id !== login.userId) throw problem(409, 'This account is already linked to a different core identity');
    const other = db.prepare('SELECT front_user_id FROM core_account_links WHERE core_user_id = ?').get(login.userId);
    if (other && other.front_user_id !== frontUserId) throw problem(409, 'Core identity is already linked to another storefront account');
    db.prepare(`INSERT INTO core_account_links VALUES (?, ?, ?, ?)
      ON CONFLICT(front_user_id) DO UPDATE SET token_cipher=excluded.token_cipher, expires_at=excluded.expires_at`)
      .run(frontUserId, login.userId, ciphertext, expires);
  })();
  return { linked: true, coreUserId: login.userId, expiresAt: expires };
}
function status(frontUserId) {
  const row = schema().prepare('SELECT core_user_id, expires_at, token_cipher FROM core_account_links WHERE front_user_id=?').get(frontUserId);
  return { linked: !!row, authenticated: !!row?.token_cipher && row.expires_at > Date.now(), coreUserId: row?.core_user_id };
}
function tokenFor(frontUserId) {
  const row = schema().prepare('SELECT * FROM core_account_links WHERE front_user_id=?').get(frontUserId);
  if (!row?.token_cipher || row.expires_at <= Date.now()) throw problem(401, 'Core login required; link or refresh your core account');
  try { return unseal(row.token_cipher); } catch { throw problem(401, 'Core credentials unavailable; sign in to core again'); }
}
function revoke(frontUserId) {
  schema().prepare('UPDATE core_account_links SET token_cipher=NULL, expires_at=0 WHERE front_user_id=?').run(frontUserId);
}
function rememberCheckout(frontUserId, id) {
  const key = crypto.randomUUID();
  schema().prepare('INSERT INTO core_checkout_owners VALUES (?, ?, ?)').run(id, frontUserId, key);
  return key;
}
function ownCheckout(frontUserId, id) {
  const row = schema().prepare('SELECT * FROM core_checkout_owners WHERE checkout_id=? AND front_user_id=?').get(id, frontUserId);
  if (!row) throw problem(404, 'Checkout not found for current user');
  return row;
}
module.exports = { saveLink, status, tokenFor, revoke, rememberCheckout, ownCheckout, problem };
