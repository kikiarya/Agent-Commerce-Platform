const FAQ=require('../knowledge/faq.json');
function retrieve(query){const text=String(query).toLowerCase();return FAQ.map(item=>({item,score:item.tags.filter(tag=>text.includes(tag.toLowerCase())).length})).filter(x=>x.score>0).sort((a,b)=>b.score-a.score).slice(0,3).map(x=>x.item);}
function answer(query){const hits=retrieve(query);return {reply:hits.length?hits.map(f=>`${f.answer}\n[${f.question} · ${f.version}]（${f.scope}）`).join('\n\n'):'没有找到能支持这个问题的政策资料，请联系商家核实。',sources:hits.map(f=>({id:f.id,title:f.question,version:f.version,scope:f.scope,url:`/api/chat/faq/${f.id}`,excerpt:f.answer}))};}
module.exports={retrieve,answer,FAQ};
