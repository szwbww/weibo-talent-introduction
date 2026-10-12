const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const code=fs.readFileSync(path.join(__dirname,'../../main/resources/static/contact-history.js'),'utf8');
const flush=async()=>{for(let i=0;i<12;i++)await Promise.resolve()};
function harness(responses,contactId='42'){
 const parts=new Map(),requests=[];const part=k=>{if(!parts.has(k))parts.set(k,{innerHTML:'',textContent:'',disabled:false,scrollHeight:500,scrollTop:0,offsetTop:0,querySelector:()=>null});return parts.get(k)};
 const box={isConnected:true,querySelector:part,addEventListener(t,f){this.click=f},append(){}};
 const panel={prepend(){},closest(){return {querySelector(){return {dataset:{subTab:'attachments'}}}}}};
 const session={contactId,closed:false};
 const sandbox={window:{},document:{createElement:()=>box},escapeHtml:s=>s.replace(/</g,'&lt;'),renderMailBody:s=>s.replace(/</g,'&lt;'),contextPath:'',URL,URLSearchParams,location:{href:'http://localhost/',origin:'http://localhost'},activateDetailSubTab(){},api:async url=>{requests.push(url);return await responses.shift()}};
 // No error element in successful state.
 const original=box.querySelector;box.querySelector=k=>k==='.mc-error'?null:original(k);
 vm.runInNewContext(code,sandbox);const api=sandbox.window.ContactHistory;api.mount(panel,session);
 return {api,panel,session,requests,part,box,activate:()=>api.activate(panel),older:()=>box.click({target:{closest:k=>k==='[data-history-older]'?{}:null}})};
}
const mail=(id,text)=>({id,source:'INBOUND',direction:'INBOUND',eventAt:'2026-10-12T10:00:00',subject:text,body:'正文 <tag>',cleanedBody:'正文',processStatus:'PROCESSED'});
test('history is lazy, reads mailbox cursor pages and deduplicates overlapping messages',async()=>{
 const h=harness([{items:[mail(2,'第二封')],nextBefore:'cursor-2',hasMore:true},{items:[mail(1,'第一封'),mail(2,'第二封')],hasMore:false}]);assert.equal(h.requests.length,0);h.activate();await flush();h.older();await flush();assert.match(h.requests[1],/before=cursor-2/);assert.equal((h.part('.mc-timeline').innerHTML.match(/第二封/g)||[]).length,1);assert.match(h.part('[data-history-count]').textContent,/2 封/);assert.doesNotMatch(h.part('.mc-timeline').innerHTML,/textarea|data-send/);
});
test('history response after expert unmount is ignored',async()=>{
 let resolve;const h=harness([new Promise(r=>resolve=r)]);h.activate();h.session.closed=true;resolve({items:[mail(1,'旧专家')],hasMore:false});await flush();assert.doesNotMatch(h.part('.mc-timeline').innerHTML,/旧专家/);
});
test('expert without contact never requests mailbox history',async()=>{
 const h=harness([],null);h.activate();await flush();assert.equal(h.requests.length,0);assert.match(h.part('.mc-timeline').textContent,/尚未建立联系/);
});
