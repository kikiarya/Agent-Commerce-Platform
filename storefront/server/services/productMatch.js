function parsePreferences(message, previous={}) {
  const raw=String(message).normalize('NFKC');
  const found=[...raw.matchAll(/偏好\s*([^=,，;；\s]{1,40})\s*=\s*([^,，;；\n]{1,80})/g)];
  const preferences={...(previous.preferences || {})};
  for(const m of found) preferences[m[1].trim()]=m[2].trim();
  return {preferences,clean:raw.replace(/偏好\s*[^=,，;；\s]{1,40}\s*=\s*[^,，;；\n]{1,80}/g,'')};
}
function matchProduct(product, constraints) {
  const attrs=product.attributes && typeof product.attributes==='object' && !Array.isArray(product.attributes)
    ? Object.fromEntries(Object.entries(product.attributes).filter(([k,v])=>k.length<=80 && typeof v==='string' && v.length<=200).slice(0,30)) : {};
  const purposes=Array.isArray(product.purposes)?product.purposes.filter(x=>typeof x==='string').slice(0,20):[];
  const evidence=[];const gaps=[];const conflicts=[];
  if(constraints.purpose) {
    if(purposes.some(p=>p.toLowerCase()===constraints.purpose.toLowerCase())) evidence.push(`商家标注用途：${constraints.purpose}`);
    else gaps.push(`未标注适用于${constraints.purpose}`);
  }
  for(const [key,value] of Object.entries(constraints.preferences || {})) {
    if(attrs[key]===undefined) gaps.push(`${key}未提供`);
    else if(attrs[key].toLowerCase()===value.toLowerCase()) evidence.push(`${key}：${attrs[key]}`);
    else conflicts.push(`${key}为${attrs[key]}，与你偏好的${value}不同`);
  }
  return {attributes:attrs,purposes,evidence,gaps,conflicts,matchScore:evidence.length};
}
function compareProducts(products) {
  const keys=[...new Set(products.flatMap(p=>Object.keys(p.attributes || {})))].sort();
  return keys.map(attribute=>({attribute,values:products.map(p=>({id:p.id,value:p.attributes[attribute] ?? '未提供'}))}));
}
module.exports={parsePreferences,matchProduct,compareProducts};
