(function () {
  'use strict';
  const BASE = '/talent';
  window.expertTagLabels = {discovered:'新发现',auto_promoted:'自动晋升',verified:'已验证'};
  window.operatorStatusOptions = [['REPLIED','已回复'],['NOT_CONTACTED','未联系']];
  window.indexLevelOptions = [['CANDIDATE','候选专家'],['APPLICATION','申报专家'],['RAW','原始专家']];
  const LIST = '/api/mail/mailbox/conversations';
  const originals = new Map(), edits = new Map(), resolved = new Map();
  let currentUser = "", closeReason = null;
  const confirming = new Set(), completionAnchors = new Map(), kept = new Set();
  let pendingSnapshot = [], controller, toastTimer, pendingFetch, lastRead = 0, busy = false;
  const clone = x => JSON.parse(JSON.stringify(x));
  const escape = x => String(x ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  // Test fixtures exist only on loopback and must be explicitly selected. Never fall back to fake online data.
  const demo = /^(127\.0\.0\.1|localhost)$/.test(location.hostname) && new URLSearchParams(location.search).get('demo') === '1';
  const demoItems = [
    {contactId:900001,name:'Samuel L. Braunstein（演示）',email:'samuel@example.org',pendingCount:2,receivedCount:2,sentCount:2,expertTags:['演示数据','英国','量子信息'],followed:true},
    {contactId:900002,name:'Lucio Strazzabosco Dorneles（演示）',email:'lucio@example.org',pendingCount:1,receivedCount:4,sentCount:4,expertTags:['演示数据','巴西','超导材料'],followed:true}
  ].map((x,i)=>({...x,accountCodes:['LiLei_QF'],orcid:'',failedCount:0,materialCount:0,waitingReply:false,latestMessage:{source:'INBOUND_PROCESSING',id:x.contactId*10,direction:'INBOUND',subject:'Re: Research collaboration enquiry',time:`2026-10-02T${i?'22:01':'23:15'}:00`,preview:'演示邮件'},latestInbound:{processingId:x.contactId*10,accountCode:'LiLei_QF',receivedAt:`2026-10-02T${i?'22:01':'23:15'}:00`}}));
  function toast(text){const el=document.getElementById('previewToast');el.textContent=text;el.hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.hidden=true,4800);}
  window.showStatus = toast;
  async function read(path) {
    if(demo) {
      const u = new URL(path,location.origin);
      if(u.pathname==='/api/auth/me')return {authenticated:true,username:'演示用户'};
      if(u.pathname===LIST){let items=clone(demoItems);if(u.searchParams.get('repliedOnly')==='true')items=items.filter(e=>!e.followed&&e.sentCount>0);return {items,total:items.length,page:0,size:100};}
      const match=u.pathname.match(/conversations\/(\d+)\/messages$/);
      if(match){const e=demoItems.find(x=>x.contactId===+match[1]);return {items:Array.from({length:e.pendingCount},(_,i)=>({source:'INBOUND_PROCESSING',id:e.contactId*10+i,contactId:e.contactId,direction:'INBOUND',accountCode:'LiLei_QF',subject:e.latestMessage.subject,body:'[本地交互测试，非真实邮件]\n\nThank you for your message. I would like to know more about the research collaboration.\n\nBest regards',eventAt:e.latestInbound.receivedAt,processStatus:'MANUAL_REVIEW',attachmentCount:0,firstAttachmentNames:[],tags:[]})),hasMore:false,nextBefore:null};}
      if(u.pathname.startsWith('/api/expert-contacts/')){const id=+u.pathname.split('/')[3],e=demoItems.find(x=>x.contactId===id);return {contact:{id,expertName:e.name,expertEmail:e.email,currentStatus:'MANUAL_HANDOFF',currentIndexLevel:'APPLICATION'},mails:[]};}
      throw new Error('本地测试不提供此只读接口');
    }
    const u = new URL(path,location.origin);
    if(u.origin!==location.origin || !(/^\/api\/auth\/me$/.test(u.pathname) || /^\/api\/mail\/mailbox\/conversations(?:\/\d+\/messages)?$/.test(u.pathname) || /^\/api\/expert-contacts\/\d+$/.test(u.pathname) || /^\/api\/mail\/contact-locations\/(?:countries|\d+(?:\/timing)?)$/.test(u.pathname) || /^\/api\/inbound-summary\/tags\/options$/.test(u.pathname)))throw new Error('此操作未纳入挂起预览');
    const response=await fetch(BASE+u.pathname+u.search,{method:'GET',credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(20000)});
    if(response.status===401){document.getElementById('loginNotice').hidden=false;throw new Error('请先登录线上系统');}
    const data=await response.json();if(!response.ok)throw new Error(data.message||'线上数据读取失败');return data;
  }
  function remember(items){items.forEach(x=>originals.set(Number(x.contactId),clone(x)));}
  function effective(id) {
    const e=originals.get(Number(id));if(!e)return null;
    const edit=edits.get(Number(id));
    return {...clone(e),pendingCount:Math.max(0,Number(e.pendingCount||0)-(resolved.get(Number(id))?.size||0)),suspended:!!edit?.suspended,suspendReason:edit?.reason||''};
  }
  function reconcile() {
    for(const [id,edit] of edits)if(edit.suspended && effective(id)?.pendingCount>0)kept.delete(id);
    updateCounts();
  }
  async function loadPending(force=false) {
    if(!force && Date.now()-lastRead<15000)return;
    if(pendingFetch)return pendingFetch;
    pendingFetch=(async()=>{
      const rows=[];
      for(let page=0;page<50;page++){
        const data=await read(LIST+'?pendingOnly=true&size=100&page='+page);
        rows.push(...data.items);
        if(rows.length>=data.total)break;
        if(page===49 || !data.items.length)throw new Error('待处理集合未完整读取，请重试');
      }
      const now=new Set(rows.map(x=>Number(x.contactId)));
      for(const old of pendingSnapshot)if(!now.has(Number(old.contactId))){const original=originals.get(Number(old.contactId));if(original)original.pendingCount=0;}
      pendingSnapshot=clone(rows);remember(rows);lastRead=Date.now();reconcile();
      document.getElementById('sourceStatus').textContent=(demo?'本地演示':'线上会话')+' · '+new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',timeZone:'Asia/Shanghai',hourCycle:'h23'}).format(new Date())+' 读取';
    })().finally(()=>pendingFetch=null);
    return pendingFetch;
  }
  function updateCounts(){
    const count={pending:pendingSnapshot.filter(x=>{const e=effective(x.contactId);return e.pendingCount>0&&!e.suspended;}).length,suspended:[...edits.keys()].filter(id=>effective(id)?.suspended).length};
    for(const chip of ['pending','suspended']){const el=document.querySelector(`[data-chip="${chip}"]`);if(el)el.innerHTML=(chip==='pending'?'待处理':'已挂起')+`<span class="sp-count">${count[chip]}</span>`;}
  }
  function button(id,cls){const e=effective(id);return e&&(e.pendingCount>0||e.suspended)?`<button type="button" class="${cls}" data-action="mc-preview-suspend" data-contact-id="${Number(id)}">${e.suspended?'取消挂起':'挂起'}</button>`:'';}
  function badge(id){const e=effective(id);if(!e)return '';return e.suspended?`<span class="sp-state-chip">已挂起 · ${e.pendingCount} 条待处理</span>`:e.pendingCount?`<span class="sp-state-chip pending">${e.pendingCount} 条待处理</span>`:'';}
  function banner(id){const e=effective(id);if(!e?.suspended)return '';return `<div class="sp-status"><div><strong>${e.pendingCount?'此会话已挂起 · '+e.pendingCount+' 条待处理':'消息已全部处理 · 等待结束挂起'}</strong><small>${e.suspendReason?'挂起原因：'+escape(e.suspendReason):'未填写挂起原因'}</small><small>${e.pendingCount?'全部处理完成后，可在消息下方确认是否结束挂起。':'挂起仍然保留，可点击「取消挂起」结束。'}</small></div></div>`;}
  function askReason(id,trigger){
    if(closeReason)closeReason();
    return new Promise(resolve=>{
      const box=document.createElement('section');box.className='sp-inline-reason';box.setAttribute('aria-label','填写挂起原因');
      box.innerHTML=`<div class="sp-inline-reason-head"><strong>挂起此会话</strong><span>原因选填</span></div><textarea aria-label="挂起原因（选填）" maxlength="500" rows="2" placeholder="例如：等待专家补充材料，稍后跟进"></textarea><div class="sp-inline-reason-bottom"><small>仅内部可见 · <span class="sp-reason-count">0 / 500</span></small><div><button type="button" class="sp-inline-secondary">取消</button><button type="button" class="sp-inline-primary">确认挂起</button></div></div>`;
      const input=box.querySelector('textarea');
      input.addEventListener('input',()=>box.querySelector('.sp-reason-count').textContent=input.value.length+' / 500');
      const finish=value=>{box.remove();closeReason=null;resolve(value);};
      closeReason=()=>finish(null);
      box.querySelector('.sp-inline-secondary').addEventListener('click',()=>finish(null));
      box.querySelector('.sp-inline-primary').addEventListener('click',()=>finish({reason:input.value.trim()}));
      box.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();finish(null);}});
      const anchor=trigger.closest('.mc-header')||trigger.closest('.mc-person');
      if(!anchor){finish(null);return;}
      anchor.insertAdjacentElement('afterend',box);input.focus({preventScroll:true});box.scrollIntoView({block:'nearest'});
    });
  }
  const confirmKey=(id,messageId)=>Number(id)+':'+Number(messageId);
  function setConfirming(id,messageId,value){const key=confirmKey(id,messageId);if(value)confirming.add(key);else confirming.delete(key);}
  function processingControls(id,message){const key=escape(message.source+':'+message.id);if(!confirming.has(confirmKey(id,message.id)))return `<button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="${key}">待处理</button>`;return `<span class="sp-process-confirm"><span>确认标记为已处理？</span><button type="button" class="sp-inline-secondary" data-action="mc-preview-process-cancel" data-message-key="${key}">取消</button><button type="button" class="sp-inline-primary" data-action="mc-preview-process-confirm" data-message-key="${key}">确认</button></span>`;}
  function processedLabel(id,message){return `<span class="sp-processed-label">已处理${resolved.get(Number(id))?.has(Number(message.id))?' · '+escape(currentUser):''}</span>`;}
  function completionLine(id,message,messages){
    id=Number(id);const e=effective(id);if(!e?.suspended||e.pendingCount||message.source!=='INBOUND_PROCESSING'||message.processStatus!=='PROCESSED')return '';
    const anchor=completionAnchors.get(id)??(messages||[]).filter(m=>m.source==='INBOUND_PROCESSING'&&m.processStatus==='PROCESSED').at(-1)?.id;
    if(Number(anchor)!==Number(message.id))return '';
    return `<div class="sp-completion-line" role="status"><div><strong>${kept.has(id)?'已继续挂起':'所有消息已处理，是否结束挂起？'}</strong><small>${kept.has(id)?'会话仍保留在「已挂起」，可随时结束。':e.followed?'结束后仍保留关注，可在「关注」查看。':'结束后按现有「已回复」规则归类。'}</small></div><div class="sp-completion-actions">${kept.has(id)?'':`<button type="button" class="sp-inline-secondary" data-action="mc-preview-keep" data-contact-id="${id}">继续挂起</button>`}<button type="button" class="sp-inline-primary" data-action="mc-preview-end" data-contact-id="${id}">结束挂起</button></div></div>`;
  }
  function end(id){const e=effective(id);edits.set(id,{...edits.get(id),suspended:false});updateCounts();toast(e.pendingCount?'已取消挂起，回到「待处理」':e.followed?'已结束挂起，可在「关注」查看':'已结束挂起，按现有「已回复」规则归类');return e.pendingCount?'pending':e.followed?'followed':'replied';}
  async function toggle(id,trigger){id=Number(id);const e=effective(id);if(!e)return null;if(e.suspended)return end(id);if(!e.pendingCount){toast('当前已无待处理消息，无需挂起');return null;}const result=await askReason(id,trigger);if(!result)return null;edits.set(id,{suspended:true,reason:result.reason});kept.delete(id);toast('已挂起，可在「已挂起」继续跟进');updateCounts();return 'suspended';}
  function resolve(id,messageId){id=Number(id);setConfirming(id,messageId,false);if(!resolved.has(id))resolved.set(id,new Set());resolved.get(id).add(Number(messageId));const e=effective(id);if(!e?.pendingCount)completionAnchors.set(id,Number(messageId));updateCounts();}
  function keep(id){kept.add(Number(id));}
  window.SuspendPreview={item:effective,button,badge,banner,toggle,resolve,end,keep,setConfirming,processingControls,processedLabel,completionLine};
  window.api=async(path,options={})=>{
    if(options.method && !['GET','HEAD'].includes(options.method.toUpperCase())){toast('预览不提交业务修改');throw new Error('预览不提交业务修改');}
    const u=new URL(path,location.origin),q=u.searchParams;
    if(u.pathname===LIST){
      await loadPending();
      if(q.get('suspendedOnly')==='true'||q.get('pendingOnly')==='true'){
        let rows=(q.get('suspendedOnly')==='true'?[...edits.keys()].map(effective).filter(e=>e?.suspended):pendingSnapshot.map(x=>effective(x.contactId)).filter(e=>e.pendingCount&&!e.suspended));
        if(q.get('q'))rows=rows.filter(x=>(x.name+' '+x.email).toLowerCase().includes(q.get('q').toLowerCase()));
        const page=Number(q.get('page')||0),size=Number(q.get('size')||20);queueMicrotask(updateCounts);return {items:rows.slice(page*size,(page+1)*size),total:rows.length,page,size};
      }
      const data=await read(path);remember(data.items);
      data.items=data.items.map(x=>effective(x.contactId));
      if(q.get('repliedOnly')==='true'){
        // The live replied queue also contains pending experts. Preview only removes suspended rows.
        data.items=data.items.filter(e=>!e.suspended);
        // Keep the production replied eligibility; followed experts are never force-inserted.
        data.items=data.items.filter(e=>!e.followed);
      }
      queueMicrotask(updateCounts);return data;
    }
    const match=u.pathname.match(/conversations\/(\d+)\/messages$/);
    if(match){const data=await read(path),ids=resolved.get(+match[1]);if(ids)data.items=data.items.map(m=>m.source==='INBOUND_PROCESSING'&&ids.has(Number(m.id))?{...m,processStatus:'PROCESSED'}:m);return data;}
    if(u.pathname.includes('unmatched-inbound'))return {records:[],totalCount:0,items:[],total:0};
    return read(path);
  };
  // Only preview actions are enabled. There is no mail-sending adapter and transport rejects writes.
  document.addEventListener('click',event=>{
    const el=event.target.closest('[data-action]');if(!el)return;
    const permitted=['mc-preview-suspend','mc-select-expert','mc-filter','mc-page-prev','mc-page-next','mc-load-older','mc-latest','mc-mark-resolved','mc-retry-list','mc-retry-conversation','mc-preview-process-cancel','mc-preview-process-confirm','mc-preview-keep','mc-preview-end'];
    if(!permitted.includes(el.dataset.action)){event.preventDefault();event.stopImmediatePropagation();toast('此预览仅开放会话浏览、挂起、取消挂起与标记已处理');}
    if(el.dataset.chip==='unmatched'){event.preventDefault();event.stopImmediatePropagation();toast('当前预览聚焦已关联专家，待匹配请到正式信箱查看');}
  },true);
  async function start(reset=false){
    if(busy)return;busy=true;
    const error=document.getElementById('loadingError');error.hidden=true;
    try{
      const auth=await read('/api/auth/me');
      document.getElementById('loginNotice').hidden=!!auth.authenticated&&!auth.mustChangePassword;
      if(!auth.authenticated||auth.mustChangePassword)return;
      currentUser=auth.username;
      document.getElementById('operator').textContent='当前登录：'+currentUser;
      if(closeReason)closeReason();
      if(reset){edits.clear();resolved.clear();originals.clear();pendingSnapshot=[];confirming.clear();completionAnchors.clear();kept.clear();}
      await loadPending(true);
      if(controller)window.MailboxChat.unmount(document.getElementById('mailboxList'));
      const hasPending=pendingSnapshot.some(x=>{const e=effective(x.contactId);return e.pendingCount>0&&!e.suspended;});
      controller=window.MailboxChat.mount(document.getElementById('mailboxList'),{sessionUser:'suspend-preview-'+auth.username,filters:{pendingOnly:hasPending}});
      updateCounts();

    }catch(e){error.textContent=e.message;error.hidden=false;}finally{busy=false;}
  }
  document.addEventListener('DOMContentLoaded',()=>{
    if(demo)document.querySelector('.sp-preview-note span').textContent='本地交互测试 · 合成会话数据 · 不连接线上';
    document.getElementById('resetPreview').addEventListener('click',()=>start(true));
    document.getElementById('refreshPreview').addEventListener('click',()=>start(false));
    document.getElementById('retryAuth').addEventListener('click',()=>start(false));
    start();
  });
})();
