const {randomUUID}=require('crypto');
const {getDb}=require('../db');
const trade=require('./tradeCoreClient');
const identity=require('./coreIdentity');
function db(){const d=getDb();d.exec(`CREATE TABLE IF NOT EXISTS order_proposals(
 id TEXT PRIMARY KEY,user_id INTEGER NOT NULL,session_id TEXT NOT NULL,items_json TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'DRAFT',checkout_id INTEGER,created_at INTEGER NOT NULL)`);return d;}
function own(id,userId){if(!userId)throw identity.problem(401,'请先登录商城');const row=db().prepare('SELECT * FROM order_proposals WHERE id=? AND user_id=?').get(id,userId);if(!row)throw identity.problem(404,'找不到这份选购清单');return row;}
function parseSelection(message){
 if(!/^选购\s/.test(message))return null;
 const text=message.replace(/^选购\s+/,'').trim();
 const parts=text.split(/[,，、;；]/);
 if(parts.length>20)throw identity.problem(400,'最多选择20种商品');
 return parts.map(part=>{const m=part.trim().match(/^#?(\d+)\s*[xX×*]\s*(\d+)$/);if(!m)throw identity.problem(400,'请按“选购 11×2、22×1”填写商品编号与数量');return {skuId:Number(m[1]),quantity:Number(m[2])};});
}
function create(userId,sessionId,items){
 if(!userId)throw identity.problem(401,'请先登录商城再整理选购清单');
 if(!trade.useTradeCore())throw identity.problem(503,'当前未启用交易核心');
 const d=db();
 const session=d.prepare('SELECT id FROM chat_sessions WHERE id=? AND user_id=?').get(sessionId,userId);
 if(!session)throw identity.problem(404,'会话不属于当前账号');
 const last=d.prepare("SELECT meta_json FROM chat_messages WHERE session_id=? AND role='assistant' ORDER BY id DESC LIMIT 1").get(sessionId);
 const shopping=JSON.parse(last?.meta_json || '{}').shopping;
 if(!shopping || !Number.isFinite(Date.parse(shopping.checkedAt)) || Date.now()-Date.parse(shopping.checkedAt)>15*60*1000)throw identity.problem(409,'请先重新搜索商品，再选择本轮候选');
 if(!Array.isArray(items)||!items.length||items.length>20)throw identity.problem(400,'请选择1至20种商品');
 const seen=new Set();
 const lines=items.map(item=>{
   if(!Number.isSafeInteger(item.skuId)||seen.has(item.skuId)||!Number.isSafeInteger(item.quantity)||item.quantity<1||item.quantity>99)throw identity.problem(400,'商品编号不能重复，数量需为1至99的整数');
   seen.add(item.skuId);
   const product=shopping.products.find(p=>p.id===item.skuId && p.source==='trade_core');
   if(!product)throw identity.problem(400,'只能选择本轮交易核心候选商品');
   return {skuId:item.skuId,quantity:item.quantity,name:product.name};
 });
 const id=randomUUID();d.prepare('INSERT INTO order_proposals(id,user_id,session_id,items_json,created_at) VALUES(?,?,?,?,?)').run(id,userId,sessionId,JSON.stringify(lines),Date.now());
 return {id,items:lines,status:'DRAFT',reply:'已整理选购清单，尚未提交订单。请填写地址获取最新报价，核对后再确认。'};
}
async function quote(id,userId,address){
 let row=own(id,userId);
 if(row.checkout_id){identity.ownCheckout(userId,row.checkout_id);const current=await trade.getCheckout(userId,row.checkout_id);return current.status==='DRAFT'?await trade.quoteCheckout(userId,row.checkout_id):current;}
 if(Date.now()-row.created_at>60*60*1000)throw identity.problem(409,'清单已过期，请重新选购');
 if(typeof address!=='string'||!address.trim()||address.length>500)throw identity.problem(400,'请填写有效收货地址（最多500字）');
 // Validate credentials before acquiring the durable one-shot creation claim.
 identity.tokenFor(userId);
 const claimed=db().prepare("UPDATE order_proposals SET status='CREATING' WHERE id=? AND user_id=? AND status='DRAFT'").run(id,userId);
 if(!claimed.changes)throw identity.problem(409,'结算创建结果尚未确定，请在我的订单中检查或联系人工；不要重复创建');
 try {
   const checkout=await trade.createCheckout(userId,{items:JSON.parse(row.items_json).map(({skuId,quantity})=>({skuId,quantity})),shippingAddress:address.trim()});
   db().transaction(()=>{identity.rememberCheckout(userId,checkout.checkoutId);db().prepare("UPDATE order_proposals SET checkout_id=?,status='READY' WHERE id=?").run(checkout.checkoutId,id);})();
   return await trade.quoteCheckout(userId,checkout.checkoutId);
 }catch(error){db().prepare("UPDATE order_proposals SET status='UNKNOWN' WHERE id=? AND checkout_id IS NULL").run(id);throw error;}
}
module.exports={parseSelection,create,quote,own};
