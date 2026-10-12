const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/contact-details.js'),'utf8');
const implementation=source.match(/function cachedRead\(cache,key,url\)\{[\s\S]*?return cache\.get\(key\);\}/)[0];
test('late list read failure cannot evict a freshly saved profile from cache',async()=>{
 let reject;const old=new Promise((_,r)=>reject=r);const read=new Function('api',`return (${implementation})`)(()=>old);const cache=new Map();const pending=read(cache,'expert','/profile');const saved=Promise.resolve({version:2});cache.set('expert',saved);reject(Error('old request failed'));await assert.rejects(pending);assert.equal(cache.get('expert'),saved);
});
test('failed current read is evicted so refresh can retry',async()=>{
 const read=new Function('api',`return (${implementation})`)(()=>Promise.reject(Error('offline')));const cache=new Map();await assert.rejects(read(cache,'expert','/profile'));assert.equal(cache.has('expert'),false);
});
