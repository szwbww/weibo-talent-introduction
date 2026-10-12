/* Read-only contact history. Uses the production mailbox endpoint and card CSS. */
window.ContactHistory = (() => {
 const sessions=new WeakMap();
 const esc=value=>escapeHtml(String(value??''));
 function downloadLink(file){const raw=String(file.downloadUrl||'');if(!raw)return '';try{const url=new URL(raw.startsWith('/api/')?contextPath+raw:raw,location.href);if(url.origin!==location.origin||!url.pathname.startsWith(contextPath+'/api/'))return '';return `<a class="mc-text-button" href="${esc(url.href)}" download>下载</a>`;}catch{return '';}}
 function card(m){
  const outbound=m.direction==='OUTBOUND',body=String(m.cleanedBody||'').trim()||String(m.body||'').trim();
  const status=outbound?(m.sendStatus==='SENT'?'已发送':m.sendStatus==='FAILED'?'发送失败':m.sendStatus||''):(m.processStatus==='MANUAL_REVIEW'?'待处理':m.processStatus==='PROCESSED'?'已处理':'');
  const files=[...(m.outboundAttachments||[])];if(m.calendarAttachment)files.push(m.calendarAttachment);
  return `<article class="mc-message" data-direction="${outbound?'OUTBOUND':'INBOUND'}"><header><span>${outbound?'发出邮件':'专家来信'} · ${esc(String(m.eventAt||'').replace('T',' '))}${m.accountCode?' · '+esc(m.accountCode):''}</span><span class="mc-badge">${esc(status)}</span></header><h3>${esc(m.subject||'(无主题)')}</h3><div class="mc-body">${renderMailBody(body,true)}</div>${m.cleanedBody&&m.body&&m.cleanedBody!==m.body?`<details class="mc-mail-extras"><summary>查看完整原文（含引用）</summary><div class="mc-body">${renderMailBody(m.body,true)}</div></details>`:''}${Number(m.attachmentCount)>0?`<details class="mc-mail-extras"><summary>附件 ${Number(m.attachmentCount)} 份</summary>${(m.firstAttachmentNames||[]).map(n=>`<div>${esc(n)}</div>`).join('')}<button type="button" class="button small" data-history-attachments>查看附件资料</button></details>`:''}${files.length?`<details class="mc-mail-extras"><summary>已发附件 ${files.length} 份</summary>${files.map(f=>`<div>${esc(f.filename||f.originalFilename||f.displayName||'附件')} ${downloadLink(f)}</div>`).join('')}<button type="button" class="button small" data-history-attachments>查看附件资料</button></details>`:''}${(m.tags||[]).length?`<div class="mc-tag-row">${m.tags.map(t=>`<span class="inbound-tag-chip">${esc(t.label)}</span>`).join('')}</div>`:''}</article>`;
 }
 function mount(panel,session){
  const box=document.createElement('section');box.className='mail-chat fp-mail-history';
  box.innerHTML='<div class="mc-timeline-head"><strong>往来信件</strong><span data-history-count></span><button class="mc-text-button" data-history-first>最早已加载</button><button class="mc-text-button" data-history-latest>↓ 最新消息</button><button class="button small" data-history-refresh>刷新</button></div><div class="fp-mail-scroll" tabindex="0" aria-label="往来信件历史"><div class="mc-timeline">切换到跟进记录加载历史信件</div></div>';
  panel.prepend(box);const state={box,session,items:[],before:null,more:false,busy:false,loaded:false};sessions.set(panel,state);
  box.addEventListener('click',e=>{if(e.target.closest('[data-history-refresh]'))load(state,false);if(e.target.closest('[data-history-older]'))load(state,true);if(e.target.closest('[data-history-first]'))box.querySelector('.fp-mail-scroll').scrollTop=0;if(e.target.closest('[data-history-latest]'))latest(state);if(e.target.closest('[data-history-attachments]'))activateDetailSubTab(panel.closest('.detail').querySelector('[data-sub-tab="attachments"]'));});
 }
 function latest(s){const el=s.box.querySelector('.fp-mail-scroll'),last=el.querySelector('.mc-message:last-child');if(last)el.scrollTop=last.offsetTop-el.offsetTop;}
 function render(s){
  let day='';const content=s.items.map(m=>{const next=String(m.eventAt||'').slice(0,10),sep=next!==day?`<div class="mc-day">${esc(next)}</div>`:'';day=next;return sep+card(m)}).join('');
  s.box.querySelector('.mc-timeline').innerHTML=(s.more?'<button class="button mc-load-older" data-history-older>加载更早信件</button>':'<div class="mc-day">已显示全部历史</div>')+(content||'<div class="mc-empty">暂无往来信件</div>');
  s.box.querySelector('[data-history-count]').textContent=`已加载 ${s.items.length} 封 · 北京时间`;
 }
 async function load(s,older){
  if(s.busy||s.session.closed)return;
  if(!/^\d+$/.test(String(s.session.contactId||''))){s.box.querySelector('.mc-timeline').textContent='尚未建立联系，暂无往来信件';s.loaded=true;return;}
  s.busy=true;const refresh=s.box.querySelector('[data-history-refresh]');refresh.disabled=true;const scroll=s.box.querySelector('.fp-mail-scroll'),oldHeight=scroll.scrollHeight,oldTop=scroll.scrollTop;
  const previousError=s.box.querySelector('.mc-error');previousError?.remove();
  if(!s.loaded)s.box.querySelector('.mc-timeline').textContent='正在加载往来信件…';
  try{
   const params=new URLSearchParams({limit:'30'});if(older&&s.before)params.set('before',s.before);
   const data=await api(`/api/mail/mailbox/conversations/${s.session.contactId}/messages?${params}`);
   if(s.session.closed||!s.box.isConnected)return;
   if(!Array.isArray(data.items))throw Error('历史响应不可用');
   const combined=older?[...data.items,...s.items]:data.items;const seen=new Set();s.items=combined.filter(m=>{const k=m.source+':'+m.id;if(seen.has(k))return false;seen.add(k);return true});
   s.before=data.nextBefore;s.more=!!data.hasMore;s.loaded=true;render(s);
   if(older)scroll.scrollTop=oldTop+scroll.scrollHeight-oldHeight;else latest(s);
  }catch(error){if(!s.session.closed&&s.box.isConnected){if(!s.loaded)s.box.querySelector('.mc-timeline').textContent='';const msg=document.createElement('div');msg.className='mc-error';msg.textContent='历史加载失败，请点击刷新重试。';s.box.append(msg);}}
  finally{s.busy=false;refresh.disabled=false;}
 }
 return {mount,activate(panel){const s=sessions.get(panel);if(s&&!s.loaded)load(s,false);}};
})();
