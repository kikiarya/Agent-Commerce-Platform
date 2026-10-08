const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { once } = require('node:events');

async function freePort() {
  const server = net.createServer();
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const port = server.address().port;
  await new Promise(resolve=>server.close(resolve));
  return port;
}

test('real storefront startup remains empty and real HTTP requests create isolated memory', {timeout:20000}, async () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(),'memory-runtime-'));
  const port = await freePort();
  const redisPort = await freePort();
  const child = spawn(process.execPath,['server/index.js'],{
    cwd:path.join(__dirname,'..'),windowsHide:true,
    env:{...process.env,NODE_ENV:'test',PORT:String(port),DATABASE_PATH:path.join(temp,'runtime.db'),
      REDIS_URL:`redis://127.0.0.1:${redisPort}`,USE_TRADE_CORE:'false',SHOPPING_MODEL_ENABLED:'false',
      JWT_SECRET:'runtime-isolated-test-secret',LLM_PROVIDER:'none',LLM_API_KEY:'',OPENAI_API_KEY:'',DEEPSEEK_API_KEY:'',KIMI_API_KEY:''}
  });
  let logs=''; child.stdout.on('data',chunk=>{logs+=chunk;});child.stderr.on('data',chunk=>{logs+=chunk;});
  const base=`http://127.0.0.1:${port}`;
  async function request(url,body,token) {
    const response=await fetch(base+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
    assert.ok(response.ok,`${url}: ${response.status}`);
    return response.json();
  }
  try {
    const started = Date.now();
    let health;
    while (Date.now()-started<10000) {
      if(child.exitCode!==null)throw new Error(`Server exited: ${logs}`);
      try { health=await request('/api/health');break; }catch { await new Promise(resolve=>setTimeout(resolve,100)); }
    }
    assert.ok(health,logs);assert.equal(health.users,0);assert.equal(health.catalog_items,0);
    const account=await request('/api/auth/register',{email:'runtime-user@local.test',password:'isolated-password',name:'Runtime user'});
    const send=message=>request('/api/chat',{message},account.token);
    assert.match((await send('查看我的记忆')).reply,/还没有保存/);
    assert.match((await send('历史客服')).reply,/没有找到/);
    assert.equal((await send('记住偏好：颜色黑色')).memory.status,'saved');
    assert.match((await send('查看我的记忆')).reply,/颜色：黑色/);
    const search=await send('推荐手机');
    assert.equal(search.shopping.status,'empty');assert.deepEqual(search.shopping.products,[]);
    const missing=await send('查订单 OPS123456');
    assert.match(missing.reply,/未找到属于当前账号的订单/);
    const history=await send('历史客服');
    assert.match(history.reply,/not_found/);assert.doesNotMatch(history.reply,/退款成功/);
    const stranger=await request('/api/auth/register',{email:'other-runtime-user@local.test',password:'isolated-password',name:'Other user'});
    assert.match((await request('/api/chat',{message:'历史客服'},stranger.token)).reply,/没有找到/);
    assert.match((await request('/api/chat',{message:'查看我的记忆'},stranger.token)).reply,/还没有保存/);
    await send('忘记我的记忆');
    assert.match((await send('历史客服')).reply,/没有找到/);
    assert.match((await send('查看我的记忆')).reply,/还没有保存/);
  } finally {
    if(child.exitCode===null) { const stopped=once(child,'exit');child.kill();await stopped; }
    fs.rmSync(temp,{recursive:true,force:true});
  }
});
