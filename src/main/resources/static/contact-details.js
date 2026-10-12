/* Contact detail layout approved in the 2026-10-12 preview. Native controls retain their owners. */
function initContactDetailsLayout() {
 'use strict';
 expertContactSharedNoteSectionTemplate=()=>'<section class="ec-section" data-role="expert-shared-note" aria-label="收发件箱备注"><div class="ec-text" data-role="note-content"></div><span class="ec-error" role="alert" hidden></span></section>';
 const presets=[...document.querySelectorAll('.layout-preset-btn')];
 function markPreset(id){presets.forEach(button=>{button.classList.toggle('fp-preset-active',button.id===id);button.setAttribute('aria-pressed',String(button.id===id));});}
 markPreset(!localStorage.getItem('contacts-list-width')||Number(localStorage.getItem('contacts-list-width'))===500?'btnLayoutWideList':'');
 presets.forEach(button=>button.addEventListener('click',()=>markPreset(button.id)));
 document.getElementById('contactsLayoutResizer')?.addEventListener('pointermove',event=>{if(event.buttons)markPreset('');});
 const originalSection=expertManualProfileSectionTemplate;
 expertManualProfileShellTemplate=function(){
  const groups=EXPERT_MANUAL_PROFILE_GROUPS, matching=groups.find(g=>g.key==='matching'),other=groups.filter(g=>g.key!=='matching');
  return expertContactSharedNoteSectionTemplate()+originalSection(matching)+
   `<section class="ec-section fp-profile"><nav class="ec-nav" aria-label="联系详情分组"><h3>专家资料</h3>${other.map(g=>`<button type="button" class="button ${g.key==='identity'?'secondary':''}" aria-pressed="${g.key==='identity'}" data-ec-jump="${g.key}">${g.label}</button>`).join('')}<button class="button" type="button" data-ec-jump="contact-info" aria-pressed="false">联系信息</button><button class="button fp-meeting-jump" type="button" data-meeting-jump>会议记录 ↓</button></nav>${other.map(g=>originalSection(g).replace('<section ',`<section ${g.key==='identity'?'':'hidden '}`)).join('')}<section class="fp-contact-info" data-fp-info hidden><div class="metadata-grid"></div></section></section>`+
   '<div data-meeting-records></div>'+groups.map(expertManualProfileEditorTemplate).join('');
 };
 expertManualProfileJump=function(session,key){
  if(key==='matching')return session.editors.get(key)?.section.scrollIntoView({block:'nearest'});
  const info=session.host.querySelector('[data-fp-info]');if(info)info.hidden=key!=='contact-info';
  const infoButton=session.host.querySelector('[data-ec-jump="contact-info"]');infoButton?.classList.toggle('secondary',key==='contact-info');infoButton?.setAttribute('aria-pressed',String(key==='contact-info'));
  for(const g of EXPERT_MANUAL_PROFILE_GROUPS.filter(g=>g.key!=='matching')){
   const section=session.editors.get(g.key)?.section;if(section)section.hidden=g.key!==key;
   const button=session.host.querySelector(`[data-ec-jump="${g.key}"]`);button?.classList.toggle('secondary',g.key===key);button?.setAttribute('aria-pressed',String(g.key===key));
  }
 };
 // Rearrange existing nodes; native controls, asynchronous targets and handlers survive.
 function reorganizeContactPanel(root,session){
  const detail=session.host.closest('.detail'),contact=session.host.closest('[data-panel="contact"]');
  if(!detail||!contact)return;
  const tabs=detail.querySelector('.detail-sub-tabs');
  const panels={};let after=tabs.querySelector('[data-sub-tab="contact"]');
  for(const [key,label] of [['followup','跟进记录'],['attachments','附件资料']]){
   const button=document.createElement('button');button.type='button';button.className='detail-sub-tab';button.dataset.subTab=key;button.textContent=label;after.after(button);after=button;
   const panel=document.createElement('div');panel.className='detail-tab-panel fp-'+key;panel.dataset.panel=key;panel.hidden=true;detail.append(panel);panels[key]=panel;
  }
  for(const child of [...contact.children])if(child!==session.host)panels.followup.append(child);
  const info=session.host.querySelector('[data-fp-info] .metadata-grid');
  for(const card of [...panels.followup.querySelectorAll('.metadata-card')]){
   const label=card.querySelector('.metadata-card-header')?.textContent.trim()||'';
   if(/^(ORCID|国家 \/ 地区|国家 \/ 国籍|年龄 \/ 学历|当前单位 \/ 机构|专业关键词|邮箱别名)$/.test(label))info.append(card);
  }
  const docs=panels.followup.querySelector('#expertDocumentsSection');if(docs)panels.attachments.append(docs);
  // The no-contact presentation also has its own materials host.
  for(const host of [...panels.followup.querySelectorAll('.expert-materials')])panels.attachments.append(host);
  for(const grid of panels.followup.querySelectorAll('.metadata-grid'))if(!grid.children.length)grid.remove();
  for(const card of [...panels.followup.querySelectorAll('.metadata-card')]){
   const label=card.querySelector('.metadata-card-header')?.textContent.trim()||'';
   if(label==='阶段流转历史'||card.id==='expertOperatorLogsSection'){
    const fold=document.createElement('details');fold.className='fp-history span-all';const summary=document.createElement('summary');summary.textContent=label||'操作日志';card.before(fold);fold.append(summary,card);
   }
  }
  if(!panels.attachments.children.length)panels.attachments.innerHTML='<div class="metadata-card">暂无附件资料</div>';
  const back=root.querySelector('.back-to-list'),header=detail.querySelector('.expert-profile-header');
  if(back&&header){back.textContent='‹ 返回';header.prepend(back);}
  const note=session.host.querySelector('[data-role="expert-shared-note"]');
  if(note&&header){note.classList.add('fp-header-note');header.after(note);}
  const workflow=document.createElement('details');workflow.className='fp-workflow';
  const workflowSummary=document.createElement('summary');workflowSummary.textContent='流程状态与日志';workflow.append(workflowSummary);
  for(const child of [...panels.followup.children])workflow.append(child);
  ContactHistory.mount(panels.followup,session);
  panels.followup.prepend(workflow);
  activateDetailSubTab(tabs.querySelector('[data-sub-tab="contact"]'));
  const material=document.getElementById('expertMaterialRow'),main=document.querySelector('#contactHeadActions .contact-head-main-row');
  if(material&&main&&!material.closest('details')){
   const fold=document.createElement('details');fold.className='fp-material-menu';const summary=document.createElement('summary');summary.className='button';summary.textContent='材料状态 ▾';fold.append(summary,material);main.append(fold);
  }
 }
 const nativeActivate=activateDetailSubTab;
 activateDetailSubTab=function(button){nativeActivate(button);if(button?.dataset.subTab==='followup')ContactHistory.activate(button.closest('.detail').querySelector('[data-panel="followup"]'));};
 // Meeting records own video uploads; the retired standalone video panel is not mounted.
 mountExpertMeetingVideos=function(){return null;};
 const nativeMount=mountExpertManualProfile;
 mountExpertManualProfile=function(root,context){
  const session=nativeMount(root,context);if(!session)return session;
  reorganizeContactPanel(root,session);
  ExpertMeetingRecords.mount(session);
  session.host.querySelector('[data-meeting-jump]')?.addEventListener('click',()=>session.host.querySelector('[data-meeting-records]').scrollIntoView({block:'start',behavior:'smooth'}));
  return session;
 };
 const nativeUnmount=unmountExpertManualProfileSession;
 unmountExpertManualProfileSession=function(session){if(session&&!session.closed)ExpertMeetingRecords.unmount();return nativeUnmount(session)};
 const listProfiles=new Map(),listNotes=new Map();
 function cachedRead(cache,key,url){if(!cache.has(key)){const pending=api(url).catch(e=>{if(cache.get(key)===pending)cache.delete(key);throw e});cache.set(key,pending);}return cache.get(key);}
 function matchingText(profile,name){return expertManualProfileFieldDisplay(EXPERT_MANUAL_PROFILE_FIELD_INDEX.get(name),profile);}
 function updateRowProfile(row,profile){row.querySelector('[data-list-research]').textContent=matchingText(profile,'researchAbility');row.querySelector('[data-list-match]').textContent=matchingText(profile,'companyMatchStatus');}
 function updateRowNote(row,text){const target=row.querySelector('[data-list-note]');if(target){target.textContent=text||'暂无备注';target.title=text||'暂无备注';}}
 const nativeRenderRows=renderContactListItems;
 renderContactListItems=function(){
  nativeRenderRows();
  const jobs=[...document.querySelectorAll('#contactList .expert-list-item')].map(row=>{
   const contact=state.contacts.find(c=>String(c.orcidId)===row.dataset.orcid);if(!contact)return null;
   row.querySelector('.expert-meta').innerHTML='<span>科研能力 <b data-list-research>加载中…</b></span><span>匹配进度 <b data-list-match>加载中…</b></span>';
   const sub=row.querySelector('.expert-row-sub');if(contact.employment&&sub?.firstElementChild?.textContent===contact.employment)sub.firstElementChild.remove();
   const note=document.createElement('div');note.className='fp-list-note';note.innerHTML='<span>备注</span><span data-list-note>加载中…</span>';row.querySelector('.expert-row-main').after(note);
   return async()=>{
    if(!row.isConnected)return;
    const key=String(contact.orcidId),noteKey=String(contact.contactId);
    const profileRead=cachedRead(listProfiles,key,`/api/experts/manual-profile?expertId=${encodeURIComponent(key)}&level=${encodeURIComponent(contact.indexLevel||document.getElementById('expertIndexLevel').value)}`);
    const noteRead=contact.contactId?cachedRead(listNotes,noteKey,`/api/mail/contact-notes/${contact.contactId}`):null;
    await Promise.allSettled([
     profileRead.then(p=>{if(row.isConnected&&listProfiles.get(key)===profileRead)updateRowProfile(row,p)}).catch(()=>{if(row.isConnected&&!listProfiles.has(key)){row.querySelector('[data-list-research]').textContent='加载失败';row.querySelector('[data-list-match]').textContent='加载失败'}}),
     noteRead?noteRead.then(n=>{if(row.isConnected&&listNotes.get(noteKey)===noteRead)updateRowNote(row,n.note)}).catch(()=>{if(row.isConnected&&!listNotes.has(noteKey))updateRowNote(row,'备注加载失败')}):Promise.resolve(updateRowNote(row,'尚无收发件箱备注'))
    ]);
   };
  }).filter(Boolean);
  let cursor=0;async function worker(){while(cursor<jobs.length){const job=jobs[cursor++];await job();}}
  for(let i=0;i<Math.min(4,jobs.length);i++)worker();
 };
 const nativeRenderProfile=expertManualProfileRenderValues;
 expertManualProfileRenderValues=function(session){nativeRenderProfile(session);if(session.profile){listProfiles.set(String(session.expertId),Promise.resolve(session.profile));for(const row of document.querySelectorAll('#contactList .expert-list-item'))if(row.dataset.orcid===String(session.expertId)&&row.querySelector('[data-list-research]'))updateRowProfile(row,session.profile);}};
 const nativeRenderNote=expertContactSharedNoteRender;
 expertContactSharedNoteRender=function(session){nativeRenderNote(session);const note=expertContactSharedNoteState(session);if(note&&!note.loading&&!note.error){listNotes.set(String(session.contactId),Promise.resolve({note:note.content}));for(const row of document.querySelectorAll('#contactList .expert-list-item'))if(row.dataset.contactId===String(session.contactId))updateRowNote(row,note.content);}};
 const quick=document.createElement('button');quick.type='button';quick.id='fpInvitedQuick';quick.className='button secondary';quick.textContent='有效 · 已邀约';quick.title='一键设置层级为有效、状态为已邀约；保留其他筛选条件';
 document.getElementById('filterToggleBtn').after(quick);
 function syncQuick(){const active=document.getElementById('expertIndexLevel').value==='APPLICATION'&&document.getElementById('contactStatusFilter').value==='INVITED';quick.classList.toggle('secondary',active);quick.setAttribute('aria-pressed',String(active));}
 quick.addEventListener('click',()=>{document.getElementById('expertIndexLevel').value='APPLICATION';const status=document.getElementById('contactStatusFilter');status.value='INVITED';status.dispatchEvent(new Event('change',{bubbles:true}));syncQuick();});
 document.getElementById('contactsFilterGroup').addEventListener('change',syncQuick);
 const nativeLoadContacts=loadContacts;
 loadContacts=async function(){listProfiles.clear();listNotes.clear();await nativeLoadContacts();syncQuick();};

}
