(() => {
  const CART_KEY = 'oldphonestore_cart';
  const THEME_KEY = 'oldphonestore_theme';
  const TOKEN_KEY = 'oldphonestore_token';
  const USER_KEY = 'oldphonestore_user';
  const CHAT_SESSION_KEY = 'oldphonestore_chat_session';

  const els = {
    grid: document.getElementById('phone-grid'),
    empty: document.getElementById('empty-state'),
    stats: document.getElementById('inventory-stats'),
    form: document.getElementById('filter-form'),
    search: document.getElementById('search-input'),
    category: document.getElementById('category-filter'),
    condition: document.getElementById('condition-filter'),
    available: document.getElementById('available-filter'),
    resetFilters: document.getElementById('reset-filters'),
    themeToggle: document.getElementById('theme-toggle'),
    cartOpen: document.getElementById('cart-open'),
    cartClose: document.getElementById('cart-close'),
    cartDrawer: document.getElementById('cart-drawer'),
    cartItems: document.getElementById('cart-items'),
    cartEmpty: document.getElementById('cart-empty'),
    cartCount: document.getElementById('cart-count'),
    cartTotal: document.getElementById('cart-total'),
    checkoutBtn: document.getElementById('checkout-btn'),
    clearCart: document.getElementById('clear-cart'),
    checkoutName: document.getElementById('checkout-name'),
    checkoutEmail: document.getElementById('checkout-email'),
    checkoutAddress: document.getElementById('checkout-address'),
    toast: document.getElementById('toast'),
    apiStatus: document.getElementById('api-status'),
    detailDialog: document.getElementById('detail-dialog'),
    detailBody: document.getElementById('detail-body'),
    flashGrid: document.getElementById('flash-grid'),
    authBtn: document.getElementById('auth-btn'),
    userChip: document.getElementById('user-chip'),
    authDialog: document.getElementById('auth-dialog'),
    authForm: document.getElementById('auth-form'),
    authTitle: document.getElementById('auth-title'),
    authName: document.getElementById('auth-name'),
    authEmail: document.getElementById('auth-email'),
    authPassword: document.getElementById('auth-password'),
    authSubmit: document.getElementById('auth-submit'),
    authModeToggle: document.getElementById('auth-mode-toggle'),
    authCancel: document.getElementById('auth-cancel'),
    chatToggle: document.getElementById('chat-toggle'),
    chatPanel: document.getElementById('chat-panel'),
    chatLog: document.getElementById('chat-log'),
    chatForm: document.getElementById('chat-form'),
    chatInput: document.getElementById('chat-input')
  };

  let phonesCache = [];
  let cart = loadCart();
  let authMode = 'login';
  let token = localStorage.getItem(TOKEN_KEY) || '';
  let user = null;
  try {
    user = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
  } catch {
    user = null;
  }

  function loadCart() {
    try {
      return JSON.parse(localStorage.getItem(CART_KEY)) || [];
    } catch {
      return [];
    }
  }

  function saveCart() {
    localStorage.setItem(CART_KEY, JSON.stringify(cart));
    renderCart();
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function formatPrice(n) {
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency: 'CNY'
    }).format(Number(n));
  }

  function normalizeProduct(p) {
    if (!p) return null;
    const title = p.title || p.name || [p.brand, p.model].filter(Boolean).join(' ');
    const attrs = p.attrs || {};
    return {
      id: p.id,
      source: p.source || 'local',
      skuId: p.source === 'trade_core' ? p.skuId : undefined,
      brand: p.brand,
      model: p.model,
      title,
      storage: p.storage ?? attrs.storage ?? '—',
      color: p.color ?? attrs.color ?? '—',
      condition: p.condition || 'New',
      battery_health: p.battery_health ?? attrs.battery_health ?? 100,
      year: p.year ?? attrs.year ?? '',
      price: Number(p.price),
      rating: p.rating ?? attrs.rating ?? 5,
      available: p.available !== false,
      category: p.category || 'General',
      imei_masked: p.imei_masked || p.sku || attrs.description || '',
      description: p.description || attrs.description || '',
      img: p.img || 'data:image/svg+xml,' + encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="520"><rect fill="#e8e6e1" width="100%" height="100%"/><text x="50%" y="50%" text-anchor="middle" fill="#666" font-size="22">${title.slice(0, 18)}</text></svg>`
      )
    };
  }

  function stars(rating) {
    const filled = '★'.repeat(Number(rating) || 0);
    const empty = '☆'.repeat(5 - (Number(rating) || 0));
    return filled + empty;
  }

  function toast(message) {
    els.toast.textContent = message;
    els.toast.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => els.toast.classList.remove('show'), 2800);
  }

  async function api(path, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(path, { ...options, headers });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw Object.assign(new Error(data.error || `Request failed (${res.status})`), { status: res.status });
    }
    return data;
  }

  function setAuth(nextToken, nextUser) {
    token = nextToken || '';
    user = nextUser || null;
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
    renderAuth();
    renderCart();
  }

  function renderAuth() {
    if (user) {
      els.authBtn.textContent = 'Logout';
      els.userChip.textContent = `${user.name} · ${user.role}`;
      els.userChip.classList.remove('hidden');
      if (!els.checkoutName.value) els.checkoutName.value = user.name || '';
      if (!els.checkoutEmail.value) els.checkoutEmail.value = user.email || '';
    } else {
      els.authBtn.textContent = 'Login';
      els.userChip.classList.add('hidden');
      els.userChip.textContent = '';
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    els.themeToggle.textContent = theme === 'light' ? 'Dark' : 'Light';
    els.themeToggle.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
    localStorage.setItem(THEME_KEY, theme);
  }

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    applyTheme(saved === 'light' || saved === 'dark' ? saved : 'light');
  }

  function fillSelect(select, values, allLabel) {
    const current = select.value;
    select.innerHTML = '';
    const all = document.createElement('option');
    all.value = 'All';
    all.textContent = allLabel;
    select.appendChild(all);
    for (const value of values) {
      const opt = document.createElement('option');
      opt.value = value;
      opt.textContent = value;
      select.appendChild(opt);
    }
    if ([...select.options].some((o) => o.value === current)) {
      select.value = current;
    }
  }

  function phoneCard(phone, index) {
    const title = escapeHtml(phone.title);
    const sold = !phone.available;
    const article = document.createElement('article');
    article.className = `phone-card${sold ? ' sold' : ''}`;
    article.style.animationDelay = `${Math.min(index * 0.04, 0.4)}s`;
    article.innerHTML = `
      <div class="phone-media">
        <img src="${escapeHtml(phone.img)}" alt="${title}" loading="lazy" width="400" height="520">
      </div>
      <div class="phone-meta">
        <div class="phone-row">
          <span class="badge ${sold ? 'sold' : ''}">${sold ? 'Sold' : escapeHtml(phone.condition)}</span>
          <span class="rating" aria-label="Rating ${phone.rating} of 5">${stars(phone.rating)}</span>
        </div>
        <h3 class="phone-title">${title}</h3>
        <p class="phone-specs">${escapeHtml(phone.storage)} · ${escapeHtml(phone.color)} · ${phone.battery_health}% battery</p>
        <div class="phone-row">
          <span class="price">${formatPrice(phone.price)}</span>
          <span class="muted">${phone.year}</span>
        </div>
      </div>
      <div class="phone-actions">
        <button type="button" class="btn ghost" data-action="detail" data-id="${phone.id}">Details</button>
        <button type="button" class="btn primary" data-action="add" data-id="${phone.id}" ${sold ? 'disabled' : ''}>
          ${sold ? 'Sold out' : 'Add'}
        </button>
      </div>
    `;
    return article;
  }

  function renderPhones(phones) {
    phonesCache = phones;
    els.grid.innerHTML = '';
    if (!phones.length) {
      els.empty.classList.remove('hidden');
      return;
    }
    els.empty.classList.add('hidden');
    const frag = document.createDocumentFragment();
    phones.forEach((phone, i) => frag.appendChild(phoneCard(phone, i)));
    els.grid.appendChild(frag);
  }

  function highlightMatches(term) {
    if (!term) return;
    const needle = term.toLowerCase();
    [...els.grid.children].forEach((card) => {
      const title = card.querySelector('.phone-title')?.textContent.toLowerCase() || '';
      const specs = card.querySelector('.phone-specs')?.textContent.toLowerCase() || '';
      if (title.includes(needle) || specs.includes(needle)) {
        card.style.outline = '1px solid color-mix(in srgb, var(--accent) 55%, transparent)';
      }
    });
  }

  async function loadMeta() {
    const meta = await api('/api/products/meta');
    fillSelect(els.category, meta.categories || [], 'All categories');
    fillSelect(els.condition, meta.conditions || [], 'Any');
    if (meta.stats) {
      const { total, in_stock, min_price, max_price } = meta.stats;
      els.stats.textContent = `${in_stock} in stock · ${total} listed · ${formatPrice(min_price)}–${formatPrice(max_price)}`;
    } else {
      els.stats.textContent = `Catalog mode: ${meta.catalogMode || 'local'} · CNY`;
    }
  }

  async function loadPhones() {
    const params = new URLSearchParams();
    const q = els.search.value.trim();
    const category = els.category.value;
    const condition = els.condition.value;
    const available = els.available.value;

    if (q) params.set('q', q);
    if (category && category !== 'All') params.set('category', category);
    if (condition && condition !== 'All') params.set('condition', condition);
    if (available !== '') params.set('available', available);

    const data = await api(`/api/products?${params.toString()}`);
    const items = (data.items || data.phones || []).map(normalizeProduct);
    renderPhones(items);
    highlightMatches(q);
  }

  async function loadFlash() {
    if (!els.flashGrid) return;
    const data = await api('/api/flash');
    els.flashGrid.innerHTML = '';
    if (!data.deals?.length) {
      els.flashGrid.innerHTML = '<p class="muted">No active flash deals.</p>';
      return;
    }
    for (const deal of data.deals) {
      const card = document.createElement('article');
      card.className = 'flash-card';
      const pct = deal.stock ? Math.min(100, Math.round((deal.sold / deal.stock) * 100)) : 100;
      card.innerHTML = `
        <div class="flash-card-top">
          <h3>${escapeHtml(deal.title)}</h3>
          <p class="muted">${escapeHtml(deal.subtitle || '')}</p>
        </div>
        <p class="flash-price">
          <span class="price">${formatPrice(deal.price)}</span>
          <span class="muted strike">${formatPrice(deal.original_price)}</span>
        </p>
        <p class="muted">${deal.brand ? `${escapeHtml(deal.brand)} ${escapeHtml(deal.model || '')}` : 'Bundle deal'}</p>
        <div class="flash-bar" aria-hidden="true"><span style="width:${pct}%"></span></div>
        <p class="muted">剩余 ${deal.remaining}/${deal.stock} · Redis ${data.redis ? 'ON' : 'fallback'}</p>
        <button type="button" class="btn primary" data-flash-buy="${deal.id}" ${deal.remaining > 0 && deal.active_window ? '' : 'disabled'}>
          ${deal.remaining > 0 ? 'Seckill' : 'Sold out'}
        </button>
      `;
      els.flashGrid.appendChild(card);
    }
  }

  function findPhone(id) {
    return phonesCache.find((p) => p.id === Number(id));
  }

  function addToCart(id) {
    const phone = findPhone(id);
    if (!phone) return;
    if (!phone.available) {
      toast('That item is unavailable.');
      return;
    }
    if (cart.some((item) => item.phone_id === phone.id)) {
      toast('Already in your cart.');
      return;
    }
    cart.push({
      phone_id: phone.id,
      product_id: phone.id,
      source: phone.source,
      skuId: phone.skuId,
      title: phone.title,
      price: phone.price,
      img: phone.img,
      storage: phone.storage,
      condition: phone.condition,
      quantity: 1
    });
    saveCart();
    toast(`Added ${phone.title}`);
  }

  function removeFromCart(phoneId) {
    cart = cart.filter((item) => item.phone_id !== Number(phoneId));
    saveCart();
  }

  function renderCart() {
    const totalQty = cart.reduce((sum, item) => sum + item.quantity, 0);
    const totalPrice = cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
    els.cartCount.textContent = String(totalQty);
    els.cartTotal.textContent = formatPrice(totalPrice);
    els.checkoutBtn.disabled = cart.length === 0 && !pendingCheckout();
    els.checkoutBtn.textContent = pendingCheckout() ? '继续上次结算' : '查看报价并下单';

    els.cartItems.innerHTML = '';
    if (!cart.length) {
      els.cartEmpty.classList.remove('hidden');
      return;
    }
    els.cartEmpty.classList.add('hidden');

    for (const item of cart) {
      const li = document.createElement('li');
      li.className = 'cart-item';
      li.innerHTML = `
        <img src="${escapeHtml(item.img)}" alt="">
        <div>
          <p class="cart-item-title">${escapeHtml(item.title)}</p>
          <p class="cart-item-meta">${escapeHtml(item.storage)} · ${escapeHtml(item.condition)}</p>
          <p class="cart-item-meta">${formatPrice(item.price)}</p>
        </div>
        <button type="button" class="btn ghost" data-remove="${item.phone_id}" aria-label="Remove">✕</button>
      `;
      els.cartItems.appendChild(li);
    }
  }

  function openCart() {
    els.cartDrawer.classList.add('open');
    els.cartDrawer.setAttribute('aria-hidden', 'false');
    els.cartOpen.setAttribute('aria-expanded', 'true');
  }

  function closeCart() {
    els.cartDrawer.classList.remove('open');
    els.cartDrawer.setAttribute('aria-hidden', 'true');
    els.cartOpen.setAttribute('aria-expanded', 'false');
  }

  function showDetail(id) {
    const phone = findPhone(id);
    if (!phone) return;
    els.detailBody.innerHTML = `
      <div class="detail-layout">
        <img src="${escapeHtml(phone.img)}" alt="${escapeHtml(phone.title)}">
        <div>
          <h3>${escapeHtml(phone.title)}</h3>
          <p class="price">${formatPrice(phone.price)}</p>
          <p><span class="badge ${phone.available ? '' : 'sold'}">${phone.available ? escapeHtml(phone.condition) : 'Sold'}</span></p>
          <p class="muted">${escapeHtml(phone.storage)} · ${escapeHtml(phone.color)} · ${phone.year}</p>
          <p>Battery health: <strong>${phone.battery_health}%</strong></p>
          <p>IMEI: ${escapeHtml(phone.imei_masked || '—')}</p>
          <p class="rating">${stars(phone.rating)}</p>
          <p>${escapeHtml(phone.description || '')}</p>
          <button type="button" class="btn primary" data-action="add" data-id="${phone.id}" ${phone.available ? '' : 'disabled'}>
            ${phone.available ? 'Add to cart' : 'Sold out'}
          </button>
        </div>
      </div>
    `;
    els.detailDialog.showModal();
  }

  function pendingKey() { return `core-checkout-v1:${user?.id || 'anonymous'}`; }
  function pendingCheckout() {
    try { return JSON.parse(localStorage.getItem(pendingKey()) || 'null'); } catch { return null; }
  }

  function showCoreLogin() {
    const dialog = document.createElement('dialog');
    dialog.innerHTML = `<form><h2>关联交易核心账号</h2>
      <p>首次结算或核心登录过期时，请登录自己的 Core 账号。关联后不能切换为其他核心身份。</p>
      <label>用户名 <input name="username" autocomplete="username" required></label>
      <label>密码 <input name="password" type="password" autocomplete="current-password" required></label>
      <p role="alert"></p><button type="submit" class="btn primary">关联账号</button>
      <button type="button" class="btn">取消</button></form>`;
    document.body.append(dialog);
    dialog.querySelector('[type=button]').onclick = () => dialog.close();
    dialog.onclose = () => dialog.remove();
    dialog.querySelector('form').onsubmit = async event => {
      event.preventDefault();
      const form = event.currentTarget;
      form.querySelector('[type=submit]').disabled = true;
      try {
        await api('/api/auth/core-link', { method: 'POST', body: JSON.stringify({
          username: form.elements.username.value, password: form.elements.password.value
        }) });
        dialog.close();
        toast('账号已关联，请再次点击结算查看报价');
      } catch (error) { form.querySelector('[role=alert]').textContent = error.message; }
      finally { form.elements.password.value = ''; form.querySelector('[type=submit]').disabled = false; }
    };
    dialog.showModal();
  }

  async function checkout() {
    if (!cart.length && !pendingCheckout()) return;
    const actingUser = user?.id;
    const key = pendingKey();
    const sessionToken = token;
    const requireSameUser = () => {
      if (user?.id !== actingUser || token !== sessionToken) throw new Error('登录已改变，请重新结算');
    };
    try {
      els.checkoutBtn.disabled = true;
      let pending = pendingCheckout();
      const health = await api('/api/health');
      requireSameUser();
      if (health.trade_core?.enabled || pending) {
        if (!token || !user) throw new Error('请先登录商城账号');
        const link = await api('/api/auth/core-link');
        requireSameUser();
        if (!link.authenticated) { showCoreLogin(); return; }
        if (!pending) {
          const address = els.checkoutAddress.value.trim();
          if (!address) throw new Error('请填写收货地址');
          const cartSnapshot = JSON.stringify(cart);
          const created = await api('/api/checkout', { method: 'POST', body: JSON.stringify({
            items: cart.map(item => ({
              ...(item.source === 'trade_core' ? { skuId: item.skuId } : { product_id: item.product_id || item.phone_id }),
              quantity: item.quantity
            })), shippingAddress: address
          }) });
          pending = { checkoutId: created.checkoutId, idempotencyKey: created.idempotencyKey, cartSnapshot };
          localStorage.setItem(key, JSON.stringify(pending));
          requireSameUser();
        }
        let quote = await api(`/api/checkout/${pending.checkoutId}`);
        requireSameUser();
        if (quote.status === 'EXPIRED') {
          if (window.confirm('上次结算已过期且未生成订单。清除后可重新获取报价。')) localStorage.removeItem(key);
          return;
        }
        if (quote.status === 'DRAFT') quote = await api(`/api/checkout/${pending.checkoutId}/quote`, { method: 'POST', body: '{}' });
        requireSameUser();
        if (quote.status === 'QUOTED') {
          const lines = quote.items.map(item => `SKU ${item.skuId} × ${item.quantity} · 单价 ${item.unitPrice}`).join('\n');
          const yes = window.confirm(`${lines}\n运费：${quote.shippingFee}\n合计：${quote.totalAmount} ${quote.currency}\n地址：${quote.shippingAddress}\n报价版本：${quote.quoteVersion}\n确认按此报价提交订单？`);
          if (!yes) {
            if (window.confirm('保留此报价以便稍后继续？选择取消可修改购物车并重新报价。')) return;
            localStorage.removeItem(key); return;
          }
          pending.quoteVersion = quote.quoteVersion;
          localStorage.setItem(key, JSON.stringify(pending));
          quote = await api(`/api/checkout/${pending.checkoutId}/confirm`, {
            method: 'POST', body: JSON.stringify({ quoteVersion: pending.quoteVersion })
          });
        }
        requireSameUser();
        if (!['CONFIRMED', 'COMPLETED'].includes(quote.status)) throw new Error(`无法提交当前结算：${quote.status}`);
        // A saved version proves this browser displayed this exact quote before confirming.
        if (!pending.quoteVersion || pending.quoteVersion !== quote.quoteVersion) throw new Error('报价已改变，请刷新核对；未提交订单');
        const order = await api(`/api/checkout/${pending.checkoutId}/complete`, {
          method: 'POST', headers: { 'Idempotency-Key': pending.idempotencyKey },
          body: JSON.stringify({ quoteVersion: pending.quoteVersion })
        });
        localStorage.removeItem(key);
        requireSameUser();
        if (JSON.stringify(cart) === pending.cartSnapshot) { cart = []; saveCart(); }
        closeCart();
        toast(`Core order ${order.order_no || order.orderId} · ${order.status} · ${order.totalAmount} ${quote.currency}`);
        await loadMeta(); await loadPhones(); return;
      }
      if (cart.some(item => item.source === 'trade_core')) throw new Error('核心商品暂不可创建新订单，请稍后重试');
      const idem = crypto.randomUUID();
      const result = await api('/api/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': idem },
        body: JSON.stringify({
          customer_name: els.checkoutName.value.trim() || undefined,
          customer_email: els.checkoutEmail.value.trim() || undefined,
          items: cart.map((item) => ({
            phone_id: item.phone_id,
            product_id: item.product_id || item.phone_id,
            quantity: item.quantity
          }))
        })
      });
      cart = [];
      saveCart();
      closeCart();
      toast(`Order ${result.order_no || '#' + result.order_id} · ${formatPrice(result.total)} · pending`);
      await loadMeta();
      await loadPhones();
    } catch (error) { toast(error.message); }
    finally { renderCart(); }
  }

  async function checkHealth() {
    try {
      const health = await api('/api/health');
      const core = health.trade_core?.enabled ? ' · TradeCore ON' : ' · local checkout';
      els.apiStatus.textContent = `ok · ${health.catalog_items || health.phones} items · Redis ${health.redis ? 'ON' : 'OFF'}${core}`;
    } catch {
      els.apiStatus.textContent = 'unreachable — start with npm start or docker compose up';
    }
  }

  function setAuthMode(mode) {
    authMode = mode;
    els.authTitle.textContent = mode === 'login' ? 'Login' : 'Register';
    els.authSubmit.textContent = mode === 'login' ? 'Login' : 'Create account';
    els.authModeToggle.textContent = mode === 'login' ? 'Need an account?' : 'Have an account?';
  }

  function appendChat(role, text) {
    const div = document.createElement('div');
    div.className = `chat-bubble ${role}`;
    div.textContent = text;
    els.chatLog.appendChild(div);
    els.chatLog.scrollTop = els.chatLog.scrollHeight;
  }

  async function sendChat(message) {
    appendChat('user', message);
    const thinking = document.createElement('div');
    thinking.className = 'chat-bubble assistant';
    thinking.textContent = '…';
    els.chatLog.appendChild(thinking);

    const session_id = localStorage.getItem(CHAT_SESSION_KEY) || undefined;
    const result = await api('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ message, session_id, stream: false })
    });
    if (result.session_id) localStorage.setItem(CHAT_SESSION_KEY, result.session_id);
    thinking.textContent = result.reply;
    thinking.title = `mode=${result.mode} intent=${result.intent}`;
    const line=(parent,tag,value)=>{const el=document.createElement(tag);el.textContent=value;parent.appendChild(el);return el;};
    const showProposal=proposal=>{
      if(proposal.error)return;
      const card=line(thinking,'section','待确认选购清单');
      for(const item of proposal.items)line(card,'p',`${item.name} × ${item.quantity}`);
      const label=line(card,'label','收货地址');const address=document.createElement('input');address.maxLength=500;label.append(address);
      const button=line(card,'button','获取最新报价');button.type='button';
      const state=line(card,'p','获取报价不会提交订单或扣款');
      button.onclick=async()=>{button.disabled=true;try{
        const q=await api(`/api/chat/proposals/${proposal.id}/quote`,{method:'POST',body:JSON.stringify({shippingAddress:address.value})});
        state.textContent=`报价 ${q.quoteVersion}：合计 ${q.totalAmount} ${q.currency}（运费 ${q.shippingFee}）；地址：${q.shippingAddress}`;
        for(const item of q.items || [])line(card,'p',`商品 ${item.skuId} × ${item.quantity}，单价 ${item.unitPrice}`);
        const link=line(card,'a','核对报价并继续');link.href=`/orders.html#checkout=${q.checkoutId}`;
      }catch(e){state.textContent=e.message;button.disabled=false;}};
    };
    if(result.proposal)showProposal(result.proposal);
    if(result.shopping?.products?.length){
      const panel=line(thinking,'section','商品比较与选购');const table=line(panel,'table','');
      const header=line(table,'tr','');line(header,'th','属性');for(const p of result.shopping.products)line(header,'th',p.name);
      for(const row of result.shopping.comparison || []){const tr=line(table,'tr','');line(tr,'th',row.attribute);for(const v of row.values)line(tr,'td',v.value);}
      const inputs=[];
      for(const p of result.shopping.products.filter(p=>p.source==='trade_core')){
        const label=line(panel,'label',`${p.name}（编号 ${p.id}）数量：`);const input=document.createElement('input');input.type='number';input.min='0';input.max='99';input.value='0';label.append(input);inputs.push({skuId:p.id,input});
      }
      if(inputs.length){const button=line(panel,'button','整理选购清单');button.type='button';button.onclick=async()=>{button.disabled=true;try{
        const proposal=await api('/api/chat/proposals',{method:'POST',body:JSON.stringify({session_id:result.session_id,items:inputs.map(x=>({skuId:x.skuId,quantity:Number(x.input.value)})).filter(x=>x.quantity>0)})});showProposal(proposal);
      }catch(e){toast(e.message);button.disabled=false;}};}
    }
    for(const source of result.sources || []){const a=line(thinking,'a',`依据：${source.title}（${source.version}）`);a.href=source.url;a.target='_blank';a.rel='noopener';}
    els.chatLog.scrollTop = els.chatLog.scrollHeight;
  }

  // Events
  els.themeToggle.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
    applyTheme(next);
  });

  els.authBtn.addEventListener('click', async () => {
    if (user) {
      try {
        await api('/api/auth/logout', { method: 'POST' });
      } catch {
        /* ignore */
      }
      setAuth('', null);
      toast('Logged out');
      return;
    }
    setAuthMode('login');
    els.authDialog.showModal();
  });

  els.authModeToggle.addEventListener('click', () => {
    setAuthMode(authMode === 'login' ? 'register' : 'login');
  });

  els.authCancel.addEventListener('click', () => els.authDialog.close());

  els.authForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const body = {
        email: els.authEmail.value.trim(),
        password: els.authPassword.value,
        name: els.authName.value.trim() || 'Buyer'
      };
      const path = authMode === 'login' ? '/api/auth/login' : '/api/auth/register';
      const data = await api(path, { method: 'POST', body: JSON.stringify(body) });
      setAuth(data.token, data.user);
      els.authDialog.close();
      toast(`Welcome, ${data.user.name}`);
    } catch (err) {
      toast(err.message);
    }
  });

  els.flashGrid?.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-flash-buy]');
    if (!btn) return;
    if (!user) {
      toast('Login required for seckill');
      els.authDialog.showModal();
      return;
    }
    try {
      btn.disabled = true;
      const dealId = btn.getAttribute('data-flash-buy');
      const result = await api(`/api/flash/${dealId}/buy`, { method: 'POST' });
      toast(`${result.message} · ${result.order_no}`);
      await loadFlash();
    } catch (err) {
      toast(err.message);
      btn.disabled = false;
    }
  });

  els.chatToggle.addEventListener('click', () => {
    const open = els.chatPanel.hasAttribute('hidden');
    if (open) els.chatPanel.removeAttribute('hidden');
    else els.chatPanel.setAttribute('hidden', '');
    els.chatToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
  });

  els.chatForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const message = els.chatInput.value.trim();
    if (!message) return;
    els.chatInput.value = '';
    sendChat(message).catch((err) => {
      appendChat('assistant', err.message);
    });
  });

  els.form.addEventListener('submit', (e) => {
    e.preventDefault();
    loadPhones().catch((err) => toast(err.message));
  });

  els.resetFilters.addEventListener('click', () => {
    els.form.reset();
    loadPhones().catch((err) => toast(err.message));
  });

  els.grid.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    const id = btn.getAttribute('data-id');
    if (btn.dataset.action === 'add') addToCart(id);
    if (btn.dataset.action === 'detail') showDetail(id);
  });

  els.detailBody.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action="add"]');
    if (!btn) return;
    addToCart(btn.getAttribute('data-id'));
  });

  els.cartOpen.addEventListener('click', openCart);
  els.cartClose.addEventListener('click', closeCart);
  els.cartDrawer.addEventListener('click', (e) => {
    if (e.target === els.cartDrawer) closeCart();
  });

  els.cartItems.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-remove]');
    if (!btn) return;
    removeFromCart(btn.getAttribute('data-remove'));
  });

  els.clearCart.addEventListener('click', () => {
    if (!cart.length) return;
    if (confirm('Clear the entire cart?')) {
      cart = [];
      saveCart();
    }
  });

  els.checkoutBtn.addEventListener('click', () => {
    checkout().catch((err) => toast(err.message));
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeCart();
  });

  initTheme();
  renderCart();
  renderAuth();
  Promise.all([checkHealth(), loadMeta(), loadPhones(), loadFlash()]).catch((err) => {
    toast(err.message);
    els.stats.textContent = 'Could not load inventory. Is the API running?';
  });
})();
