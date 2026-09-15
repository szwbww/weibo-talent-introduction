/* Isolated UI preview. All experts and messages below are fictional; no API calls. */
const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const star = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="m12 3 2.8 5.8 6.4.9-4.6 4.5 1.1 6.3-5.7-3-5.7 3 1.1-6.3L2.8 9.7l6.4-.9Z"/></svg>';
const experts = [
 {id:1,name:'陈嘉宁',en:'Jianing Chen',initial:'JC',org:'苏黎世联邦理工学院',field:'机器学习 · 计算机视觉',email:'jianing.chen@example.org',account:'recruit-01',tag:'材料沟通',pending:1,time:'10:42',snippet:'已附上最新简历，请查收。',color:'#6376af',bg:'#e9eefb'},
 {id:2,name:'王思远',en:'Siyuan Wang',initial:'SW',org:'新加坡国立大学',field:'先进材料 · 纳米技术',email:'siyuan.wang@example.org',account:'recruit-02',tag:'有意向',pending:2,time:'09:18',snippet:'想进一步了解项目的申请条件。',color:'#498c85',bg:'#e9f4f1'},
 {id:3,name:'刘明哲',en:'Mingzhe Liu',initial:'ML',org:'帝国理工学院',field:'机器人 · 智能控制',email:'mingzhe.liu@example.org',account:'recruit-01',tag:'会议沟通',pending:0,time:'昨天',snippet:'我：会议邀请已发送，请查收。',color:'#a77d4a',bg:'#f7f0e3'},
 {id:4,name:'张若涵',en:'Ruohan Zhang',initial:'RZ',org:'多伦多大学',field:'生物医学 · 医学影像',email:'ruohan.zhang@example.org',account:'recruit-02',tag:'有意向',pending:1,time:'昨天',snippet:'感谢邀请，我对这个机会很感兴趣。',color:'#9975a6',bg:'#f3edf8'},
 {id:5,name:'李睿',en:'Rui Li',initial:'RL',org:'慕尼黑工业大学',field:'新能源 · 电池材料',email:'rui.li@example.org',account:'recruit-01',tag:'材料沟通',pending:0,time:'09/05',snippet:'我：已收到您的材料，感谢配合。',color:'#638798',bg:'#eaf2f6'},
 {id:6,name:'周子谦',en:'Ziqian Zhou',initial:'ZZ',org:'悉尼大学',field:'数据科学 · 信息系统',email:'ziqian.zhou@example.org',account:'recruit-02',tag:'有意向',pending:0,time:'09/04',snippet:'谢谢您的详细介绍，我会仔细阅读。',color:'#70859a',bg:'#edf1f6'},
 {id:7,name:'林予安',en:'Yuan Lin',initial:'YL',org:'代尔夫特理工大学',field:'电子工程 · 半导体',email:'yuan.lin@example.org',account:'recruit-01',tag:'会议沟通',pending:0,time:'09/03',snippet:'我：期待下周与您线上交流。',color:'#85925c',bg:'#f0f4e7'}
];
const initialMessages = {
 1:[
  {id:'m11',direction:'in',date:'2026-09-06',time:'16:28',subject:'Re: International Talent Programme — Further information',body:'Hi,\nThank you for the introduction. I am interested in learning more about the programme. Could you let me know what materials I should prepare?\nBest regards,\nJianing',translated:'您好，\n感谢您的介绍。我希望进一步了解这个项目，请问需要准备哪些材料？\n祝好，\n嘉宁',kind:'专家来信'},
  {id:'m12',direction:'out',date:'2026-09-06',time:'17:05',subject:'Re: International Talent Programme — Further information',body:'Dear Dr. Chen,\nThank you for your interest. Please share your latest CV so we can better understand your research background and discuss the next steps.\nBest regards,\nTalent Introduction Team',kind:'手动回复'},
  {id:'m13',direction:'in',date:'2026-09-07',time:'10:42',subject:'Re: International Talent Programme — CV attached',body:'Hi,\nPlease find my updated CV attached. My work focuses on machine learning and computer vision.\nHappy to discuss further. Please let me know if you need anything else.\nBest regards, Jianing',translated:'您好，\n附件是我最新的简历，请查收。我近期的研究主要集中在机器学习与计算机视觉领域。\n很愿意进一步交流。如需其他资料，请随时告诉我。\n祝好，\n嘉宁',kind:'专家来信',attachment:'Jianing_Chen_CV.pdf',pending:true}
 ]
};
experts.slice(1).forEach(e => { initialMessages[e.id]=[
 {id:`m${e.id}1`,direction:'out',date:'2026-09-04',time:'09:30',subject:'International Talent Programme — Introduction',body:`Dear Dr. ${e.en.split(' ').at(-1)},\nWe would like to introduce our talent programme and explore opportunities to connect with your research interests. Please let us know if you would like further information.\nBest regards,\nTalent Introduction Team`,kind:'首发'},
 {id:`m${e.id}2`,direction:'in',date:'2026-09-07',time:e.id===2?'09:18':'09:00',subject:'Re: International Talent Programme — Introduction',body:`您好，\n${e.id===2?'感谢您的介绍。我想进一步了解项目的申请条件，以及后续沟通的安排。':e.id===4?'感谢邀请，我对这个机会很感兴趣。方便提供更多项目介绍吗？':'感谢您的详细介绍。我已仔细阅读，期待进一步交流。'}\n祝好，\n${e.name}`,kind:'专家来信',pending:e.pending>0}
 ];if(!e.pending) initialMessages[e.id].push({id:`m${e.id}3`,direction:'out',date:'2026-09-07',time:'09:30',subject:'Re: International Talent Programme — Introduction',body:`${e.name}博士，您好：\n${e.snippet.replace('我：','')}\n祝好，\n人才引进团队`,kind:'手动回复'});if(e.pending===2)initialMessages[e.id].push({id:`m${e.id}4`,direction:'in',date:'2026-09-07',time:'09:18',subject:'Re: International Talent Programme — Introduction',body:'补充一个问题：是否方便先安排一次线上交流？谢谢。',kind:'专家来信',pending:true}); });
let followed;
try {const stored=JSON.parse(localStorage.getItem('mailbox-preview-followed'));followed=new Set(Array.isArray(stored)?stored:[1,3,5]);}catch{followed=new Set([1,3,5]);}
let selected=1, filter='all', scope='all', search='', account='',tag='', toastTimer;
const drafts={}, messages=initialMessages;
const current=()=>experts.find(e=>e.id===selected);
const avatar=e=>`<div class="avatar" style="--avatar-bg:${e.bg};--avatar-color:${e.color}">${esc(e.initial)}</div>`;
function notify(text){$('#previewToast').textContent=text;$('#previewToast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#previewToast').hidden=true,2600);}
function showDialog(title,text){$('#dialogTitle').textContent=title;$('#dialogBody').textContent=text;$('#detailDialog').showModal();}
function visibleExperts(){return experts.filter(e=>(filter!=='followed'||followed.has(e.id))&&(filter!=='pending'||e.pending>0)&&(!account||e.account===account)&&(!tag||e.tag===tag)&&(`${e.name} ${e.en} ${e.org} ${e.email}`.toLowerCase().includes(search.toLowerCase())));}
function renderList(){
 const rows=visibleExperts();$('#expertCount').textContent=`${rows.length} 位专家`;
 $('#allCount').textContent=experts.length;$('#followedCount').textContent=followed.size;$('#pendingCount').textContent=experts.filter(e=>e.pending).length;
 document.querySelectorAll('[data-filter]').forEach(b=>{b.classList.toggle('active',b.dataset.filter===filter);b.setAttribute('aria-selected',String(b.dataset.filter===filter));});
 $('#expertList').innerHTML=rows.length?rows.map(e=>`<div class="expert-row ${selected===e.id?'selected':''}" data-expert="${e.id}" tabindex="0" role="button" aria-label="查看${esc(e.name)}的邮件" aria-pressed="${selected===e.id}">${avatar(e)}<div class="expert-copy"><div class="expert-line"><span class="expert-name">${esc(e.name)}</span><button class="star-button ${followed.has(e.id)?'followed':''}" data-follow="${e.id}" aria-label="${followed.has(e.id)?'取消关注':'关注'}${esc(e.name)}" aria-pressed="${followed.has(e.id)}" title="${followed.has(e.id)?'取消关注':'关注专家'}">${star}</button><time class="row-time">${esc(e.time)}</time></div><div class="row-org">${esc(e.org)}</div><div class="row-bottom"><span class="row-snippet">${esc(e.snippet)}</span>${e.pending?`<span class="pending-dot" title="${e.pending} 封待处理来信">${e.pending}</span>`:''}</div></div></div>`).join(''):'<div class="empty-state">暂无符合条件的专家<br><small>试试其他关键词或筛选条件</small></div>';
 $('#listCaption').textContent=account||tag?'已应用筛选条件':'按最近往来排序';
}
function reconcileSelection(){const rows=visibleExperts();if(!rows.some(e=>e.id===selected))selected=rows[0]?.id??null;renderList();renderConversation();}
function toggleFollow(id){followed.has(id)?followed.delete(id):followed.add(id);try{localStorage.setItem('mailbox-preview-followed',JSON.stringify([...followed]));}catch{}notify(followed.has(id)?'已关注该专家':'已取消关注');reconcileSelection();}
function renderConversation(){
 const e=current();if(!e){$('#conversation').innerHTML='<div class="empty-state">选择一位专家，查看往来邮件</div>';return;}
 $('#conversation').innerHTML=`<header class="conversation-header"><div class="identity"><button class="back-experts" id="backExperts" aria-label="返回专家列表">‹</button>${avatar(e)}<div><h2>${esc(e.name)}<span class="chip blue">${esc(e.tag)}</span></h2><p>${esc(e.email)} <span style="padding:0 5px">·</span> ${esc(e.org)}</p></div></div><div class="header-actions"><button class="button small follow-control" id="followCurrent" aria-pressed="${followed.has(e.id)}">${star} ${followed.has(e.id)?'已关注':'关注专家'}</button><button class="button small profile-control" id="expertProfile">专家资料 ↗</button></div></header>
 <div class="conversation-toolbar"><div class="thread-tabs" role="tablist" aria-label="邮件方向"><button class="thread-tab ${scope==='all'?'active':''}" data-scope="all" role="tab" aria-selected="${scope==='all'}">全部往来 <span id="mailCount">${messages[e.id].length}</span></button><button class="thread-tab ${scope==='in'?'active':''}" data-scope="in" role="tab" aria-selected="${scope==='in'}">仅来信</button><button class="thread-tab ${scope==='out'?'active':''}" data-scope="out" role="tab" aria-selected="${scope==='out'}">仅发信</button></div><div class="tags"><span class="chip ${e.pending?'amber':'green'}">${e.pending?`${e.pending} 封待处理`:'来信已处理'}</span><span class="chip">人工回复</span></div></div><div class="timeline" id="timeline"></div>
 <form class="composer" id="replyForm"><div class="composer-head"><span class="composer-target">回复给 <strong style="color:#576980;font-weight:500">${esc(e.name)}</strong><span>· ${esc(e.email)}</span></span><span>${esc(e.account)}</span></div><div class="composer-box"><div class="compose-subject">主题：${esc(messages[e.id].at(-1).subject.startsWith('Re:')?messages[e.id].at(-1).subject:'Re: '+messages[e.id].at(-1).subject)}</div><textarea id="replyBody" aria-label="回复正文" placeholder="输入回复内容…">${esc(drafts[e.id]||'')}</textarea><div class="composer-bottom"><div class="composer-tools"><button type="button" id="insertTemplate">＋ 常用回复</button><button type="button" id="saveDraft">保存草稿</button></div><div class="composer-send"><span class="composer-hint" id="draftHint">预览模式，仅模拟发送</span><button class="button primary" type="submit" id="sendReply" ${drafts[e.id]?.trim()?'':'disabled'}>模拟发送 ↗</button></div></div></div></form>`;
 renderTimeline();
 $('#followCurrent').onclick=()=>toggleFollow(e.id);
 $('#expertProfile').onclick=()=>showDialog('专家资料',`${e.name} / ${e.en}\n\n机构：${e.org}\n研究方向：${e.field}\n邮箱：${e.email}\n关联账号：${e.account}\n沟通标签：${e.tag}\n\n此处为预览示例资料。`);
 $('#backExperts').onclick=()=>$('#workspace').classList.remove('mobile-chat');
 $('#replyBody').oninput=()=>{drafts[e.id]=$('#replyBody').value;$('#sendReply').disabled=!drafts[e.id].trim();};
 $('#insertTemplate').onclick=()=>{drafts[e.id]=`${e.name}博士，您好：\n\n感谢您的来信，我们已收到您的信息。\n\n祝好，\n人才引进团队`;$('#replyBody').value=drafts[e.id];$('#sendReply').disabled=false;$('#replyBody').focus();};
 $('#saveDraft').onclick=()=>{drafts[e.id]=$('#replyBody').value;$('#draftHint').textContent='草稿已暂存于当前预览';notify('草稿已暂存，切换专家后仍可继续编辑');};
 $('#replyForm').onsubmit=event=>{event.preventDefault();const body=$('#replyBody').value.trim();if(!body)return;messages[e.id].push({id:`local-${Date.now()}`,direction:'out',date:'2026-09-07',time:'11:00',subject:messages[e.id].at(-1).subject,body,kind:'模拟回复'});drafts[e.id]='';e.snippet='我：'+body.replace(/\n/g,' ');e.time='刚刚';experts.splice(experts.indexOf(e),1);experts.unshift(e);scope='all';renderList();renderConversation();notify('模拟回复已加入会话，未发送真实邮件');};
}
function renderTimeline(){
 const e=current();let date='';
 $('#timeline').innerHTML=messages[e.id].filter(m=>scope==='all'||scope===m.direction).map(m=>{
 const older=m!==messages[e.id].at(-1);
 const divider=m.date!==date?`<div class="date-divider">${m.date==='2026-09-07'?'今天 · 9 月 7 日':m.date==='2026-09-06'?'昨天 · 9 月 6 日':'9 月 4 日'}</div>`:'';date=m.date;
 return `${divider}<article class="message ${m.direction==='out'?'outbound':'inbound'}">${m.direction==='out'?'<div class="avatar" style="--avatar-bg:#e6edfc;--avatar-color:#5473b8">我</div>':avatar(e)}<div class="message-content"><div class="message-meta"><span>${m.direction==='in'?esc(e.name):'人才引进团队'}</span><span>${esc(m.kind)}</span><time>${esc(m.time)}</time></div><div class="message-bubble"><div class="message-subject">${esc(m.subject)}</div><div class="message-text ${older?'message-collapsed':''}" id="body-${m.id}">${esc(m.body)}</div>${older?`<button class="link-button expand-body" data-expand="${m.id}">展开正文 ↓</button>`:''}${m.attachment?`<button class="attachment" data-attachment="${m.id}"><span class="file-icon">PDF</span><span>${esc(m.attachment)}<small>248 KB · 附件</small></span><span style="margin-left:auto;color:#8a99b0">↗</span></button>`:''}<div class="message-foot"><span>${m.direction==='out'?`<span style="color:#749886">✓ ${m.kind==='模拟回复'?'预览记录':'已发送'}</span>`:m.pending?'<span class="chip amber">待处理</span>':'已处理'}</span><div class="message-actions">${m.translated?`<button class="link-button" data-translate="${m.id}">译文</button>`:''}<button class="link-button" data-original="${m.id}">邮件详情</button>${m.pending?`<button class="link-button" data-resolve="${m.id}" style="color:#496ba4">标记已处理</button>`:''}</div></div></div></div></article>`;
 }).join('')||'<div class="empty-state">暂无此方向的邮件</div>';
 $('#timeline').scrollTop=$('#timeline').scrollHeight;
}
$('#expertList').onclick=event=>{const follow=event.target.closest('[data-follow]');if(follow){toggleFollow(Number(follow.dataset.follow));return;}const row=event.target.closest('[data-expert]');if(row){selected=Number(row.dataset.expert);scope='all';renderList();renderConversation();$('#workspace').classList.add('mobile-chat');}};
$('#expertList').onkeydown=event=>{if(event.target.matches('.expert-row')&&['Enter',' '].includes(event.key)){event.preventDefault();event.target.click();}};
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.filter;reconcileSelection();});
$('#expertSearch').oninput=event=>{search=event.target.value;reconcileSelection();};
$('#conversation').addEventListener('click',event=>{
 const b=event.target.closest('button');if(!b)return;
 if(b.dataset.scope){scope=b.dataset.scope;renderConversation();return;}
 if(b.dataset.expand){const body=$(`#body-${b.dataset.expand}`);body.classList.toggle('message-collapsed');b.textContent=body.classList.contains('message-collapsed')?'展开正文 ↓':'收起正文 ↑';return;}
 const id=b.dataset.translate||b.dataset.original||b.dataset.resolve||b.dataset.attachment;if(!id)return;
 const e=current(),m=messages[e.id].find(m=>m.id===id);
 if(b.dataset.translate){const translated=b.textContent==='译文';$(`#body-${m.id}`).textContent=translated?m.translated:m.body;b.textContent=translated?'原文':'译文';}
 if(b.dataset.original)showDialog('邮件详情',`发件人：${m.direction==='in'?e.email:e.account+'@example.org'}\n收件人：${m.direction==='in'?e.account+'@example.org':e.email}\n时间：${m.date} ${m.time}\n主题：${m.subject}\n\n${m.body}`);
 if(b.dataset.attachment)showDialog(m.attachment,'附件预览\n\nJianing Chen, Ph.D.\nMachine Learning · Computer Vision\n\n此文件为界面示例，未连接真实专家附件。');
 if(b.dataset.resolve){m.pending=false;e.pending=messages[e.id].filter(x=>x.pending).length;reconcileSelection();notify('已在预览中标记处理完成');}
});
$('#closeDialog').onclick=()=>$('#detailDialog').close();$('#closeFilters').onclick=()=>$('#filterDialog').close();
$('#filterOpen').onclick=()=>{$('#accountFilter').value=account;$('#tagFilter').value=tag;$('#filterDialog').showModal();};
$('#applyFilters').onclick=()=>{account=$('#accountFilter').value;tag=$('#tagFilter').value;$('#filterDialog').close();$('#filterOpen').textContent=account||tag?'筛选条件 · 已生效':'筛选条件';reconcileSelection();};
$('#resetFilters').onclick=()=>{$('#accountFilter').value='';$('#tagFilter').value='';};
$('#refreshPreview').onclick=()=>{renderList();notify('当前为示例数据，预览已刷新');};
$('#checkReplies').onclick=()=>notify('预览模式：未连接邮箱，不执行真实收信');
document.querySelectorAll('.topnav button').forEach(b=>b.onclick=()=>notify('当前预览范围：收发件箱'));
renderList();renderConversation();
