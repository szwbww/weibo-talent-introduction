/* Deterministic local test data, never loaded by the production page. */
(async()=>{
 const contact={id:42,orcidId:'local-test',expertName:'本地验收专家',expertEmail:'test@example.invalid',currentIndexLevel:'APPLICATION',operatorStatus:'INVITED',currentStatus:'INITIAL_SENT',boundSenderAccountCode:'TEST',autoReplyEnabled:false};
 const expert={orcidId:'local-test',displayName:contact.expertName,email:contact.expertEmail,indexLevel:'APPLICATION',contactId:42,operatorStatus:'INVITED',country:'测试国家',employment:'测试机构',keyword:'测试技术',tags:[]};
 let records=[],profile={version:0,matching:{researchAbility:'强',companyMatchStatus:'未匹配'}};
 window.fixtureRequests=[];
 api=async(path,options={})=>{
  window.fixtureRequests.push({path,method:options.method||'GET'});
  if(path.startsWith('/api/experts/meeting-records')) {
   if(options.method==='PUT') {const p=JSON.parse(options.body),id=path.split('/').pop(),r={...p,id,version:p.expectedVersion+1};records=records.filter(r=>r.id!==id).concat(r);return r;}
   return {items:records,total:records.length};
  }
  if(path.startsWith('/api/experts/meeting-videos'))return {items:[],total:0};
  if(path.startsWith('/api/experts/manual-profile'))return profile;
  if(path.startsWith('/api/mail/contact-notes'))return {note:'已收 CV；等待企业对接',updatedBy:'不应显示',updatedAt:'2026-10-12T12:00:00'};
  if(path.startsWith('/api/mail/mailbox/conversations'))return {items:[{id:1,source:'INBOUND',direction:'INBOUND',subject:'技术合作',body:'完整原文\n引用',cleanedBody:'愿意参加会议。',eventAt:'2026-10-12T10:00:00',processStatus:'PROCESSED'}],hasMore:false};
  if(path.startsWith('/api/expert-contacts/mail-send-options'))return [];
  if(path==='/api/expert-contacts/42')return {contact,statusHistory:[],recommendedNextAction:'等待回复'};
  if(path.includes('tags'))return {tags:[],found:true};
  if(path.includes('by-expert'))return {inboundCount:1,outboundSentCount:0,outboundFailedCount:0};
  if(path.includes('operator-action-logs'))return {records:[]};
  return [];
 };
 state.contacts=[expert];state.selectedExpertOrcid=expert.orcidId;state.accounts=[{accountCode:'TEST',enabled:true}];state.view='contacts';
 document.querySelectorAll('.view').forEach(v=>v.hidden=v.id!=='view-contacts');
 document.getElementById('view-contacts').hidden=false;document.getElementById('view-contacts').classList.add('active');
 document.querySelector('.app-shell').hidden=false;document.querySelector('.app-shell').style.display='';document.getElementById('loginOverlay').hidden=true;
 document.getElementById('expertIndexLevel').value='APPLICATION';document.getElementById('contactStatusFilter').value='INVITED';
 initLayoutResizer();
 document.getElementById('contactDetail').addEventListener('click',e=>{const b=e.target.closest('[data-sub-tab]');if(b)activateDetailSubTab(b)});
 renderContactListItems();if(new URLSearchParams(location.search).has('raw'))await showExpertDetail({...expert,contactId:null});else await loadContactDetail(42);
 document.getElementById('viewTitle').textContent='专家列表';document.getElementById('viewSubtitle').textContent='本地验收数据 · 未连接线上写入';document.querySelectorAll('.nav-tab').forEach(n=>n.classList.toggle('active',n.dataset.view==='contacts'));document.title='本地验收 · 专家联系详情';
 window.fixtureReady=true;
})().catch(e=>{document.body.insertAdjacentHTML('afterbegin','<pre id="fixtureError"></pre>');document.getElementById('fixtureError').textContent=e.stack});
