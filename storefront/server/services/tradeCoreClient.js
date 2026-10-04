/**
 * HTTP client for Java Trade Core (Store :8080).
 * Browser never calls this directly — only Express BFF.
 */
const BASE = (process.env.TRADE_CORE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const identity = require('./coreIdentity');
const TIMEOUT_MS = Number(process.env.TRADE_CORE_TIMEOUT_MS) || 8000;

function useTradeCore() {
  return String(process.env.USE_TRADE_CORE || '').toLowerCase() === 'true';
}

async function coreFetch(method, path, { userId, body, headers = {} } = {}) {
  const controller = new AbortController();

  const h = {
    Accept: 'application/json',
    ...headers
  };
  if (userId != null) h.Authorization = `Bearer ${identity.tokenFor(userId)}`;
  if (body !== undefined) h['Content-Type'] = 'application/json';

  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE}${path}`, {
      method,
      headers: h,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal
    });
    const text = await res.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { raw: text };
    }
    if (!res.ok) {
      const err = new Error(data?.message || data?.error || `Trade core ${res.status}`);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  } finally {
    clearTimeout(timer);
  }
}

module.exports = {
  login: (username, password) => coreFetch('POST', '/api/auth/login', { body: { username, password } }),
  useTradeCore,
  BASE,
  searchProducts: (q, priceMax, inStock) => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (priceMax != null) params.set('priceMax', String(priceMax));
    if (inStock != null) params.set('inStock', String(inStock));
    const qs = params.toString();
    return coreFetch('GET', `/api/products/search${qs ? `?${qs}` : ''}`);
  },
  listProducts: () => coreFetch('GET', '/api/products'),
  getProduct: (id) => coreFetch('GET', `/api/products/${id}`),
  createCheckout: (userId, body) => coreFetch('POST', '/api/checkouts', { userId, body }),
  updateCheckout: (userId, id, body) => coreFetch('PUT', `/api/checkouts/${id}`, { userId, body }),
  quoteCheckout: (userId, id) => coreFetch('POST', `/api/checkouts/${id}/quote`, { userId }),
  confirmCheckout: (userId, id, quoteVersion) => coreFetch('POST', `/api/checkouts/${id}/confirm`, { userId, body: { quoteVersion } }),
  completeCheckout: (userId, id, body) =>
    coreFetch('POST', `/api/checkouts/${id}/complete`, { userId, body }),
  getCheckout: (userId, id) => coreFetch('GET', `/api/checkouts/${id}`, { userId }),
  getOrder: (userId, id) => coreFetch('GET', `/api/orders/${id}`, { userId }),
  getDeliveryEvents: (userId, id) => coreFetch('GET', `/api/orders/${id}/delivery-events`, { userId }),
  listOrders: (userId) => coreFetch('GET', '/api/orders', { userId }),
  cancelOrder: (userId, id) => coreFetch('POST', `/api/orders/${id}/cancel`, { userId })
};
