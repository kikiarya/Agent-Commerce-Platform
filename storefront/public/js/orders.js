(() => {
  const $ = id => document.getElementById(id);
  const states={DRAFT:'待填写',QUOTED:'待确认报价',CONFIRMED:'已确认，待提交',COMPLETED:'已生成订单',EXPIRED:'报价已过期',PAYMENT_PENDING:'支付结果确认中',PAID:'已支付，等待发货',DELIVERING:'配送中',SHIPPED:'配送中',FULFILLED:'已送达',CANCELLED:'已取消',FAILED:'支付失败',pending:'待支付',paid:'已支付',shipped:'配送中',completed:'已完成',cancelled:'已取消'};
  let cursor=null, selected=null, generation=0, busy=false;
  const token=()=>localStorage.getItem('oldphonestore_token');
  const sessionToken=token();
  function sameAccount(){if(!sessionToken || token()!==sessionToken) throw new Error('登录已改变，请返回商城重新登录后刷新本页');}
  async function api(path,method='GET',body){
    sameAccount();
    const res=await fetch(path,{method,headers:{'Content-Type':'application/json',Authorization:`Bearer ${sessionToken}`},body:body===undefined?undefined:JSON.stringify(body)});
    const data=await res.json();sameAccount();
    if(!res.ok) throw new Error(res.status===401?'请先登录商城；交易登录过期时，点击本页的登录交易账号':data.error || `请求失败 ${res.status}`);
    return data;
  }
  function text(parent,tag,value){const el=document.createElement(tag);el.textContent=value;parent.append(el);return el;}
  function button(parent,label,action){const el=text(parent,'button',label);el.className='btn';el.onclick=action;return el;}
  function describe(parent,view){
    text(parent,'p',`状态：${states[view.status] || view.status}`);
    if(view.totalAmount!=null) text(parent,'p',`合计：${view.totalAmount} ${view.currency || 'CNY'}（运费 ${view.shippingFee ?? '—'}）`);
    for(const item of view.items || []) text(parent,'p',`商品 ${item.skuId} × ${item.quantity}，单价 ${item.unitPrice}`);
    if(view.shippingAddress) text(parent,'p',`收货地址：${view.shippingAddress}`);
    if(view.status==='PAYMENT_PENDING') text(parent,'p','正在核对支付结果，请勿重复创建订单。稍后刷新查看进度。');
  }
  async function detail(kind,id){
    if(busy)return;
    const g=++generation;selected=null;$('resume').hidden=true;$('detail-body').replaceChildren();$('detail-message').textContent='正在获取最新状态…';
    if(!$('detail').open)$('detail').showModal();
    try {
      const data=await api(kind==='order'?`/api/orders/${id}`:`/api/checkout/${id}`);
      if(g!==generation)return;
      $('detail-title').textContent=kind==='order'?`订单 ${data.order_no || id}`:`结算 ${id}`;
      describe($('detail-body'),data.mid || data);
      if(kind==='order') {
        button($('detail-body'),'刷新订单状态',()=>detail(kind,id));
        const section=text($('detail-body'),'section','');text(section,'h3','配送进度');
        const deliveryStates={REQUESTED:'已安排配送',PICKUP:'已揽收',IN_TRANSIT:'运输中',OUT_FOR_DELIVERY:'派送中',DELIVERED:'已送达',LOST:'配送异常'};
        try {
          const result=await api(`/api/orders/${id}/delivery-events`);
          if(g!==generation)return;
          if(!result.events.length)text(section,'p','暂无配送记录');
          for(const event of result.events)text(section,'p',`${new Date(event.occurredAt).toLocaleString()} · 包裹 ${event.shipmentId} · ${deliveryStates[event.status] || event.status}`);
        }catch(e){if(g===generation)text(section,'p','配送进度暂时无法查询，请稍后刷新');}
      }
      if(kind==='checkout' && ['DRAFT','QUOTED','CONFIRMED','COMPLETED'].includes(data.status)){
        selected=data;$('resume').hidden=false;
        $('resume').textContent=data.status==='COMPLETED'?'找回已生成订单':'核对并继续';
        if(data.status==='DRAFT'){
          const label=text($('detail-body'),'label','收货地址');const input=document.createElement('input');input.id='recovery-address';input.value=data.shippingAddress || '';label.append(input);
        }
      }
      $('detail-message').textContent='';
    }catch(e){if(g===generation)$('detail-message').textContent=e.message;}
  }
  async function loadCheckouts(append=false){
    const data=await api('/api/checkout'+(append && cursor?`?before=${cursor}`:''));
    if(!append)$('checkouts').replaceChildren();
    for(const c of data.checkouts){
      const row=text($('checkouts'),'article','');
      text(row,'p',`结算 ${c.checkoutId} · ${c.unavailable?'暂时无法查询':states[c.status] || c.status}`);
      if(!c.unavailable && c.status!=='EXPIRED')button(row,c.localOrderId?'查看订单':'查看并恢复',()=>detail(c.localOrderId?'order':'checkout',c.localOrderId || c.checkoutId));
    }
    if(!append && !data.checkouts.length)text($('checkouts'),'p','暂无结算记录');
    cursor=data.nextCursor;$('more').hidden=!cursor;
  }
  async function load(){
    $('account').textContent='正在加载…';$('orders').replaceChildren();$('checkouts').replaceChildren();$('more').hidden=true;
    try {
      const data=await api('/api/orders/mine');
      for(const order of data.orders){const row=text($('orders'),'article','');text(row,'p',`${order.order_no || order.id} · ${states[order.status] || order.status}${order.stale_projection?'（上次保存）':''}`);button(row,'查看最新进度',()=>detail('order',order.id));}
      if(!data.orders.length)text($('orders'),'p','暂无订单');
      $('account').textContent='订单列表显示最近 50 条记录';
      await loadCheckouts();
    }catch(e){$('account').textContent=e.message;}
  }
  $('resume').onclick=async()=>{
    if(!selected || busy)return;
    busy=true;$('resume').disabled=true;
    try {
      let c=await api(`/api/checkout/${selected.checkoutId}`);
      const base=`/api/checkout/${c.checkoutId}`;
      if(c.status==='DRAFT'){
        const address=$('recovery-address')?.value.trim();if(!address)throw new Error('请填写收货地址');
        await api(base,'PUT',{shippingAddress:address});c=await api(base+'/quote','POST',{});
      }
      if(['QUOTED','CONFIRMED'].includes(c.status)){
        const summary=(c.items || []).map(i=>`商品 ${i.skuId} × ${i.quantity} · 单价 ${i.unitPrice}`).join('\n');
        if(!window.confirm(`${summary}\n运费 ${c.shippingFee}\n合计 ${c.totalAmount} ${c.currency}\n地址：${c.shippingAddress}\n确认提交？`))return;
        if(c.status==='QUOTED')await api(base+'/confirm','POST',{quoteVersion:c.quoteVersion});
      }else if(c.status!=='COMPLETED')throw new Error('结算已过期或状态改变，请关闭后刷新');
      const order=await api(base+'/complete','POST',{quoteVersion:c.quoteVersion});
      $('detail-body').replaceChildren();describe($('detail-body'),order);$('detail-message').textContent=`订单 ${order.order_no} 已确认，可在订单记录中查看进度`;
      $('resume').hidden=true;selected=null;await load();
    }catch(e){$('detail-message').textContent=e.message+'；稍后使用本条结算重试';}
    finally{busy=false;$('resume').disabled=false;}
  };
  $('close-detail').onclick=()=>{if(!busy){generation++;$('detail').close();}};
  $('detail').addEventListener('cancel',e=>{if(busy)e.preventDefault();else generation++;});
  $('core-login').onclick=()=>{try{sameAccount();$('login-dialog').showModal();}catch(e){$('account').textContent=e.message;}};
  $('close-login').onclick=()=>$('login-dialog').close();
  $('login-form').onsubmit=async event=>{
    event.preventDefault();const form=event.currentTarget;const submit=form.querySelector('[type=submit]');submit.disabled=true;
    try{
      await api('/api/auth/core-link','POST',{username:form.elements.username.value,password:form.elements.password.value});
      $('login-dialog').close();$('login-message').textContent='';await load();
    }catch(e){$('login-message').textContent=e.message;}
    finally{form.elements.password.value='';submit.disabled=false;}
  };
  $('reload').onclick=()=>load();
  $('more').onclick=async()=>{ $('more').disabled=true;try{await loadCheckouts(true);}catch(e){$('account').textContent=e.message;}finally{$('more').disabled=false;} };
  window.addEventListener('storage',event=>{if(event.key==='oldphonestore_token'){generation++;selected=null;$('detail').close();$('orders').replaceChildren();$('checkouts').replaceChildren();$('account').textContent='登录已改变，请刷新页面';}});
  load().then(()=>{const m=location.hash.match(/^#checkout=(\d+)$/);if(m)detail('checkout',Number(m[1]));});
})();