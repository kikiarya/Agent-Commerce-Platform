// Read-only shopping tool. Prices and candidates always come from the catalog.
const trade = require('./tradeCoreClient');
const {getDb} = require('../db');
function parseRequest(message, previous = {}) {
  const raw=String(message).normalize('NFKC');
  if (/^(重新选|重新开始|清空偏好)[。！!\s]*$/.test(raw)) return {query:'',priceMax:null,purpose:null,reset:true};
  const clearBudget=/预算不限|不限预算/.test(raw);
  const numbers=[...raw.matchAll(/(?:预算|不超过|最多|以内|budget\s*|under\s*)(?:降到|提高到|改成|改为|调整到|为|是|[:：])?\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)\s*(千|万|k)?\s*(?:元)?|(\d+(?:\.\d{1,2})?)\s*(千|万|k)?\s*(?:元)?(?:以内|以下)/gi)];
  const budgets=numbers.map(m=>Number(m[1]||m[3])*({千:1000,万:10000,k:1000}[(m[2]||m[4]||'').toLowerCase()]||1));
  const priceMax=clearBudget ? null : budgets.length?Math.min(...budgets):(previous.priceMax ?? null);
  if(priceMax!==null && (!Number.isFinite(priceMax)||priceMax<=0||priceMax>10000000)) return {clarify:'请提供有效的人民币预算上限。'};
  if(/美元|美金|USD|\$|欧元|EUR/i.test(raw))return {clarify:'商品以人民币计价，请提供人民币预算上限。'};
  if(/\d\s*[-~至到]\s*\d|不要|不买|排除|除了/.test(raw))return {clarify:'这类范围或排除条件暂不能可靠解析。请提供一个商品名称和明确的预算上限，我先查询候选。'};
  let query=raw.replace(/预算不限|不限预算/g,'');
  for(const m of numbers)query=query.replace(m[0],' ');
  query=query.replace(/(?:用来|用于|用途|主要用来).*/,'').replace(/换成|换个|改看|推荐|帮我|请|找一[个台款]|找|搜索|比较|对比|买一[个台款]|想买|有没有|一下|一些|商品|预算|[，。,:：!?？]/g,' ').replace(/\b(?:recommend|find|search|compare|please|budget)\b/gi,' ').replace(/\s+/g,' ').trim();
  const aliases={'滑板':'skateboard','轮子':'wheels','板面':'deck'};
  query=aliases[query]||query||previous.query||'';
  if(query.length>80)return {clarify:'请缩短为商品名称或品牌，并给出预算上限。'};
  const purpose=raw.match(/(?:用来|用于|用途|主要用来)\s*[:：]?\s*(.+)/)?.[1] || previous.purpose || null;
  return {query,priceMax,purpose};
}
async function recommend(message, {search, previous, useCore=trade.useTradeCore()}={}) {
  const constraints=parseRequest(message,previous);
  if(constraints.reset)return {status:'reset',reply:'已清空选购条件。你想买什么商品？',products:[],constraints:{query:'',priceMax:null,purpose:null}};
  if(constraints.clarify)return {status:'clarification',reply:constraints.clarify,products:[],constraints};
  if(!constraints.query && constraints.priceMax===null)return {status:'clarification',reply:'你想买什么商品？预算上限是多少元？',products:[],constraints};
  try {
    let rows;
    if(search)rows=await search(constraints);
    else if(useCore)rows=await trade.searchProducts(constraints.query,constraints.priceMax,true);
    else {
      rows=getDb().prepare('SELECT id, brand || \' \' || model AS name, price FROM phones WHERE available=1 AND (brand LIKE ? OR model LIKE ? OR category LIKE ?) AND (? IS NULL OR price<=?)')
        .all(`%${constraints.query}%`,`%${constraints.query}%`,`%${constraints.query}%`,constraints.priceMax,constraints.priceMax);
    }
    if(!Array.isArray(rows))throw new Error('Invalid catalog response');
    const products=rows.filter(p=>Number.isSafeInteger(p.id) && p.id>0 && typeof p.name==='string' && Number.isFinite(Number(p.price)) && Number(p.price)>0 && (constraints.priceMax===null || Number(p.price)<=constraints.priceMax))
      .sort((a,b)=>Number(a.price)-Number(b.price)||a.id-b.id).slice(0,5)
      .map(p=>({id:p.id,name:p.name,price:Number(p.price),currency:'CNY',source:useCore?'trade_core':'local',reason:constraints.priceMax===null?'符合本次商品搜索条件':`单价不超过预算 ${constraints.priceMax} 元`}));
    const note='价格和可售状态以本次查询为准，下单时重新报价；预算筛选不包含运费。';
    let reply=products.length?`按价格从低到高找到这些候选：\n${products.map(p=>`• ${p.name} — ¥${p.price.toFixed(2)}；${p.reason}`).join('\n')}\n${note}`:'没有找到符合条件的在售商品。可以换一个商品名称或调整预算。';
    reply=`当前条件：${constraints.query || '所有商品'}；${constraints.priceMax===null?'未设预算上限':`单价不超过 ${constraints.priceMax} 元`}。\n`+reply;
    if(constraints.purpose)reply+='\n当前商品资料不足以验证是否适合该用途，不能据此保证性能或兼容性。';
    return {status:products.length?'ok':'empty',constraints,products,reply,checkedAt:new Date().toISOString()};
  } catch {return {status:'unavailable',constraints,products:[],reply:'商品查询暂时不可用，请稍后再试。当前无法确认价格和可售状态。'};}
}
function preferencesDb() {
  const db=getDb();
  db.exec(`CREATE TABLE IF NOT EXISTS shopping_preferences (
    session_id TEXT PRIMARY KEY REFERENCES chat_sessions(id), turn_id INTEGER NOT NULL,
    constraints_json TEXT NOT NULL, updated_at INTEGER NOT NULL)`);
  return db;
}
function loadPreferences(sessionId) {
  const row=preferencesDb().prepare('SELECT constraints_json, updated_at FROM shopping_preferences WHERE session_id=?').get(sessionId);
  if(!row || Date.now()-row.updated_at>24*60*60*1000)return {};
  try{return JSON.parse(row.constraints_json);}catch{return {};}
}
function savePreferences(sessionId,turnId,constraints) {
  // Persist filters only. Older concurrent replies cannot overwrite newer turns.
  const safe={query:constraints.query || '',priceMax:constraints.priceMax ?? null,purpose:constraints.purpose || null};
  preferencesDb().prepare(`INSERT INTO shopping_preferences VALUES(?,?,?,?)
    ON CONFLICT(session_id) DO UPDATE SET turn_id=excluded.turn_id,
      constraints_json=excluded.constraints_json,updated_at=excluded.updated_at
    WHERE excluded.turn_id>shopping_preferences.turn_id`).run(sessionId,turnId,JSON.stringify(safe),Date.now());
}
function isFollowUp(message) {return /^(预算|不限预算|换成|换个|改看|用于|用来|用途|主要用来|重新选|重新开始|清空偏好)/.test(String(message).trim());}
module.exports={parseRequest,recommend,loadPreferences,savePreferences,isFollowUp};