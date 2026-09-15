/* Layout verified against the live /talent/ mailbox on 2026-09-07.
 * Workbench runtime is an unmodified copy of the existing frontend component.
 * This adapter is LOCAL PREVIEW ONLY: every request resolves to an in-memory fixture.
 */
// Two explicit outbound-only examples, one recent and one older.
for (const id of [3,6]) {
 const e=experts.find(e=>e.id===id);
 messages[id]=[messages[id][0]];
 messages[id][0].date=id===3?'2026-09-07':'2026-09-04';
 messages[id][0].time=id===3?'08:45':'09:30';
 e.pending=0;e.time=id===3?'08:45':'09/04';e.snippet='我：'+messages[id][0].subject;
}
const hasInbound=e=>messages[e.id].some(m=>m.direction==='in');
const conversationCache = new Map();
const previewLogs = {};
const detailState = {};
let activePanel = 'chat';
const originalTimeline = renderTimeline;
const statusNames = ['未联系','已联系','已回复','已回复材料','已邀约','已完成'];
const levelNames = ['原始','筛选','有效'];
const previewSnippets = [
 {id:1,snippetType:'SALUTATION',content:'[示例] Dear Professor,',enabled:true},
 {id:2,snippetType:'GREETING',content:'[示例] Thank you for your reply.',enabled:true},
 {id:3,snippetType:'ACK',content:'[示例] Thank you for your time.',enabled:true},
 {id:4,snippetType:'CLOSING',content:'[示例] Best regards,\nTalent Introduction Team',enabled:true}
];
window.fetch = async function previewOnlyFetch(input, init={}) {
 const url=String(input);if(init.signal?.aborted)throw new DOMException('Aborted','AbortError');
 const response=data=>({ok:true,status:200,json:async()=>data});
 if(url==='/__preview__/api/reply-snippets')return response(previewSnippets);
 if(url!=='/__preview__/api/rag-reply/compose')throw new Error('预览未连接业务接口');
 const request=JSON.parse(init.body||'{}');
 const excluded=new Set(request.excludedFactCodes||[]);
 const codes=[...new Set(['DEMO-COMM-001',...(request.forcedFactCodes||[])])].filter(c=>!excluded.has(c));
 const selection=request.frameSelection||{};
 return response({frame:{selection,salutation:selection.salutationSnippetId?'Dear Professor,':'',greeting:selection.greetingSnippetId?'Thank you for your reply.':'',acknowledgement:selection.ackSnippetId?'Thank you for your time.':'',closing:selection.closingSnippetId?'Best regards,\nTalent Introduction Team':''},bodyParagraphs:[{text:'[预览示例草稿]\nThank you for sharing your research background. We have received your message and look forward to discussing your interests further.',renderMode:'PARAPHRASE'}],usedFacts:codes.map(factCode=>({factCode,title:'示例事实 · 来信确认',renderMode:'PARAPHRASE',origin:'MODEL'})),unaddressed:[],corpusFingerprint:'preview-fixture'});
};
function localLog(e,action){(previewLogs[e.id]??=[]).unshift({time:new Date().toLocaleTimeString('zh-CN',{hour12:false}),action});renderLogs(e);}
function stateFor(e){return detailState[e.id]??=( {status:hasInbound(e)?'已回复':'已联系',level:'筛选',expertTags:['已验证'],get mailTags(){const m=messages[e.id].find(m=>m.id===this.source);return m ? (m.tags??=[]) : [];},source:messages[e.id].findLast(m=>m.direction==='in')?.id,subject:'',draftHtml:''} );}
function chosenMail(e){return messages[e.id].find(m=>m.id===stateFor(e).source)||messages[e.id].findLast(m=>m.direction==='in');}
function rootFor(e){return conversationCache.get(e.id)?.root;}
function localQuery(e,selector){return rootFor(e)?.querySelector(selector);}
function renderLogs(e){const host=localQuery(e,'.operation-entries');if(host)host.innerHTML=(previewLogs[e.id]||[]).map(l=>`<div class="operation-entry"><time>${esc(l.time)}</time><strong>admin</strong><span>${esc(l.action)}</span><span class="chip">本地预览</span></div>`).join('')||'<div class="empty-state">暂无本地操作记录</div>';}
function updateTags(e){const s=stateFor(e);const summary=localQuery(e,'[data-expert-summary]');if(summary)summary.textContent=(s.expertTags.join('、')||'暂无标签')+' · 展开编辑';['expert','mail'].forEach(type=>{const container=localQuery(e,`[data-tag-list="${type}"]`);if(container)container.innerHTML=s[type+'Tags'].map((t,i)=>`<span class="editable-tag">${esc(t)}<button data-remove-tag="${type}" data-tag-index="${i}" title="删除标签" aria-label="删除${type==='mail'?'邮件':'专家'}标签${esc(t)}">×</button></span>`).join('')||'<span class="subtle">暂无标签</span>';});}
function toggleView(panel){
 activePanel='chat';const e=current();if(!e)return;
 if(panel==='workbench'){localQuery(e,'.workbench-fold').open=true;localQuery(e,'.workbench-fold').scrollIntoView({block:'nearest'});}
 if(panel==='logs'){localQuery(e,'.logs-panel').open=true;localQuery(e,'.logs-panel').scrollIntoView({block:'nearest'});}
 if(panel==='chat')renderTimeline();
 if(location.hash!=='#experts')history.replaceState(null,'','#chat');
}
function refreshSource(e){
 const m=chosenMail(e);if(!m){localQuery(e,'.source-card').innerHTML='<div class="awaiting-source">尚无专家来信，可信工作台暂无生成依据。收到来信后可使用；下方仍可人工写信跟进。</div>';localQuery(e,'.technical-section').hidden=true;return;}
 const host=localQuery(e,'.source-card');
 host.innerHTML=`<div class="source-top"><span class="chip blue">生成依据 · 最新来信</span><span>${esc(m.date)} ${esc(m.time)}</span></div><strong>${esc(m.subject)}</strong><details><summary>清洗后正文 · 点击展开</summary><div class="source-body">${esc(m.body)}</div>${m.translated?`<button class="button small" data-source-translate="true">翻译为中文</button><div class="source-body source-translation" hidden>${esc(m.translated)}</div>`:''}</details><details><summary>原始正文 · 包含原始引用和签名</summary><div class="source-body">${esc(m.body)}\n\n&gt; Previous message …（示例历史引用）</div></details>${m.attachment?`<button class="source-attachment" data-source-attachment="true"><span class="file-icon">PDF</span><span>${esc(m.attachment)}</span><span class="subtle">248 KB · 查看附件 ↗</span></button>`:''}`;
 const technical=localQuery(e,'.technical-section');
 technical.innerHTML=`<summary>邮件技术信息 · Message-ID / In-Reply-To</summary><dl><dt>Message-ID</dt><dd>preview-${esc(m.id)}@example.org</dd><dt>In-Reply-To</dt><dd>preview-outbound-${e.id}@example.org</dd></dl>`;
 syncPending(e);
}
function syncPending(e){
 const button=localQuery(e,'[data-mark-current]');
 if(button){button.disabled=!chosenMail(e)?.pending;button.textContent=chosenMail(e)?.pending?'标记已处理':'已处理';}
 const count=localQuery(e,'.pending-total');if(count)count.textContent=e.pending;
}
const sourceDrafts=new Map();
function saveSourceDraft(e){sourceDrafts.set(stateFor(e).source,{html:localQuery(e,'.manual-editor').innerHTML,subject:localQuery(e,'.manual-subject').value});}
function restoreSourceDraft(e){const saved=sourceDrafts.get(stateFor(e).source);localQuery(e,'.manual-editor').innerHTML=saved?.html||'';localQuery(e,'.manual-subject').value=saved?.subject||chosenMail(e).subject;localQuery(e,'.manual-status').textContent=saved?.html?'已填写':'未填写';}
function mountPreviewWorkbench(e){const item=conversationCache.get(e.id);item.workbench?.unmount();const m=chosenMail(e);if(!m)return;item.workbench=TrustReplyWorkbench.mount(localQuery(e,'[data-trust-reply-live-host]'),{mode:'LIVE',source:{sourceType:'LIVE_INBOUND',sourceId:e.id},contextPath:'/__preview__',autoBootstrap:false,onComplete:assembly=>{
 const text=assembly.text||'';
 const editor=localQuery(e,'.manual-editor');editor.innerHTML=esc(text).replace(/\n/g,'<br>');stateFor(e).draftHtml=editor.innerHTML;localQuery(e,'.manual-status').textContent='已填写';
 const section=localQuery(e,'.manual-section');section.open=true;localQuery(e,'.manual-subject').value=m.subject.startsWith('Re:')?m.subject:'Re: '+m.subject;
 localLog(e,'采用可信工作台草稿到人工富文本回复');notify('已采用到人工回复；可继续编辑');section.scrollIntoView({block:'nearest',behavior:'smooth'});
 }});}
function renderConversation(){
 const e=current();if(!e){$('#conversation').innerHTML='<div class="empty-state">请选择专家会话</div>';return;}
 if(conversationCache.has(e.id)){$('#conversation').replaceChildren(rootFor(e));renderTimeline();return;}
 const s=stateFor(e),root=document.createElement('div');root.className='production-conversation';conversationCache.set(e.id,{root,workbench:null});
 root.innerHTML=`<header class="conversation-header"><div class="identity"><button class="back-experts" data-back-experts aria-label="返回专家列表">‹</button>${avatar(e)}<div><h2>${esc(e.name)}<span class="chip green">${s.status}</span><span class="chip">${s.level}</span></h2><p>${esc(e.email)} · ${esc(e.org)}</p></div></div><div class="header-actions"><button class="button small follow-control" data-follow-current aria-pressed="${followed.has(e.id)}">${star} ${followed.has(e.id)?'已关注':'关注专家'}</button><button class="button small" data-expert-details>查看专家详情</button></div></header>
 <details class="expert-operation-panel"><summary><span>专家状态、层级与标签</span><span class="subtle" data-expert-summary>已验证 · 展开编辑</span></summary><div class="expert-controls-row"><label>专家状态<select class="operator-select" aria-label="专家状态">${statusNames.map(n=>`<option ${n===s.status?'selected':''}>${n}</option>`).join('')}</select></label><label>专家层级<select class="level-select" aria-label="专家层级">${levelNames.map(n=>`<option ${n===s.level?'selected':''}>${n}</option>`).join('')}</select></label><button class="button primary" data-save-expert>保存变更</button><span class="expert-control-note">ORCID：—</span></div><div class="compact-tag-row"><strong>专家标签</strong><div data-tag-list="expert"></div><button class="button small" data-add-tag="expert">＋ 添加标签</button></div></details>
 <div class="conversation-flow">
 <section class="chat-panel"><div class="conversation-toolbar"><div><strong>往来信件</strong> <span class="mail-total">${messages[e.id].length}</span><span class="thread-counts">收 ${messages[e.id].filter(m=>m.direction==='in').length} · 发 ${messages[e.id].filter(m=>m.direction==='out').length}</span></div><span class="conversation-state ${hasInbound(e)?'':'waiting'}">${hasInbound(e)?`${e.pending} 封待处理来信`:'待专家回复'}</span></div><div class="timeline" id="timeline"></div></section>
 <details class="workbench-fold"><summary><span class="fold-title"><span class="fold-icon">✧</span><strong>可信工作台</strong><span class="subtle">${hasInbound(e)?'基于最新来信生成草稿':'暂无来信，暂不可生成'}</span></span><span class="fold-toggle">展开 ⌄</span></summary><div class="processing-panel"><div class="source-card"></div>
 ${hasInbound(e)?`<div class="compact-tag-row mail-tags"><strong>邮件标签</strong><div data-tag-list="mail"></div><button class="button small" data-auto-qa>自动添加 QA 标签</button><button class="button small primary" data-add-tag="mail">＋ 添加标签</button></div><div data-trust-reply-live-host></div>`:''}
 <details class="technical-section"><summary>邮件技术信息 · Message-ID / In-Reply-To</summary></details></div></details>
 <details class="manual-section" open><summary><span class="fold-title"><span class="fold-icon">✎</span><strong>人工回复</strong><span class="subtle">${hasInbound(e)?'手动编辑或采用工作台草稿':'可继续发信跟进'}</span></span><span class="manual-status chip">未填写</span></summary><div class="manual-content"><div class="manual-recipient">收件人 <strong>${esc(e.name)}</strong><span>${esc(e.email)}</span><span class="manual-account">发件账号：${esc(e.account)}</span></div><label>主题<input class="manual-subject" aria-label="邮件主题" value="${esc(chosenMail(e)?.subject||messages[e.id].at(-1).subject)}"></label><div class="rich-toolbar"><button type="button" data-rich="bold"><strong>B</strong></button><button type="button" data-rich="italic"><em>I</em></button><button type="button" data-rich="insertUnorderedList">列表</button><button type="button" data-rich="createLink">链接</button></div><div class="manual-editor rich-editor" role="textbox" aria-label="人工回复正文" contenteditable="true" data-placeholder="${hasInbound(e)?'输入回复内容，或展开可信工作台生成草稿…':'尚未收到来信，可在这里撰写跟进邮件…'}"></div><div class="manual-footer"><span class="subtle">本地预览 · 不发送真实邮件</span><button class="button primary" data-send-manual>${hasInbound(e)?'发送人工回复':'发送跟进邮件'}</button></div></div></details>
 <details class="logs-panel"><summary>操作日志 <span class="subtle">处理、回复与状态变更记录</span></summary><div class="operation-entries"></div></details></div>`;
 $('#conversation').replaceChildren(root);
 root.addEventListener('click',event=>handleProductionClick(event,e));
 root.addEventListener('input',event=>{if(event.target.closest('.manual-editor')){s.draftHtml=localQuery(e,'.manual-editor').innerHTML;localQuery(e,'.manual-status').textContent=localQuery(e,'.manual-editor').innerText.trim()?'已填写':'未填写';}});
 refreshSource(e);updateTags(e);renderLogs(e);mountPreviewWorkbench(e);renderTimeline();toggleView(activePanel);
}
renderTimeline=function(){
 const e=current();if(!e||!$('#timeline'))return;
 const list=messages[e.id];let lastDate='';
 $('#timeline').innerHTML=list.map((m,i)=>{
 const divider=m.date!==lastDate?`<div class="date-divider">${esc(m.date.slice(5).replace('-',' 月 '))} 日</div>`:'';lastDate=m.date;
 const lastInbound=m===list.findLast(x=>x.direction==='in');
 const full=m.body;
 const body=i===list.length-1?`<div class="message-text">${esc(full)}</div>`:`<details class="history-body"><summary>${esc(full.replace(/\n/g,' ').slice(0,95))}… <span>展开正文</span></summary><div class="message-text">${esc(full)}</div></details>`;
 return `${divider}<article class="message ${m.direction==='out'?'outbound':'inbound'}" data-mail-id="${m.id}">${m.direction==='out'?'<div class="avatar self-avatar">我</div>':avatar(e)}<div class="message-content"><div class="message-meta"><span>${m.direction==='in'?esc(e.name):'人才引进团队'}</span><span>${esc(m.kind)}</span><time>${esc(m.time)}</time>${lastInbound?'<span class="latest-inbound">最新来信</span>':''}</div><div class="message-bubble"><div class="message-subject">${esc(m.subject)}</div>${body}${m.attachment?`<div class="attachment file-preview"><span class="file-icon">PDF</span><span>${esc(m.attachment)}<small>248 KB · 附件</small></span></div>`:''}<div class="message-foot"><span>${m.direction==='out'?'<span class="sent-indicator">✓ '+(m.kind.includes('模拟')?'模拟已发送':'已发送')+'</span>':m.pending?'<span class="chip amber">待处理</span>':'<span class="handled-indicator">✓ 已处理</span>'}</span>${m.direction==='in'&&m.pending?`<button class="button small" data-resolve="${m.id}">标记已处理</button>`:''}</div></div></div></article>`;
 }).join('')+(!hasInbound(e)?'<div class="waiting-notice"><span class="waiting-circle">◷</span>已发送 '+list.length+' 封邮件，尚未收到专家回复</div>':'');
 const count=localQuery(e,'.mail-total');if(count)count.textContent=list.length;
 const direction=localQuery(e,'.thread-counts');if(direction)direction.textContent=`收 ${list.filter(m=>m.direction==='in').length} · 发 ${list.filter(m=>m.direction==='out').length}`;
 const status=localQuery(e,'.conversation-state');if(status)status.textContent=hasInbound(e)?`${e.pending} 封待处理来信`:'待专家回复';
 $('#timeline').scrollTop=$('#timeline').scrollHeight;
};
function updateFollowControl(e){const button=localQuery(e,'[data-follow-current]');button.setAttribute('aria-pressed',String(followed.has(e.id)));button.innerHTML=star+(followed.has(e.id)?' 已关注':' 关注专家');}
const originalToggleFollow=toggleFollow;
toggleFollow=function(id){originalToggleFollow(id);for(const e of experts)if(rootFor(e))updateFollowControl(e);};
function handleProductionClick(event,e){
 const b=event.target.closest('button');if(!b)return;
 if(b.dataset.panelTab){toggleView(b.dataset.panelTab);return;}
 if(b.hasAttribute('data-follow-current')){toggleFollow(e.id);return;}
 if(b.hasAttribute('data-back-experts')){$('#workspace').classList.remove('mobile-chat');return;}
 if(b.hasAttribute('data-expert-details')){showDialog('专家详情',`${e.name} / ${e.en}\n\n机构：${e.org}\n邮箱：${e.email}\n状态：${stateFor(e).status}\n层级：${stateFor(e).level}\n\n数据为预览示例。`);return;}
 if(b.hasAttribute('data-save-expert')){const s=stateFor(e);s.status=localQuery(e,'.operator-select').value;s.level=localQuery(e,'.level-select').value;localQuery(e,'.identity .chip.green').textContent=s.status;localQuery(e,'.identity .chip:not(.green)').textContent=s.level;localLog(e,`保存专家变更：${s.status} / ${s.level}`);notify('已在本地预览保存专家状态与层级');return;}
 if(b.dataset.addTag){openTagDialog(e,b.dataset.addTag);return;}
 if(b.dataset.removeTag){const s=stateFor(e),type=b.dataset.removeTag;const removed=s[type+'Tags'].splice(Number(b.dataset.tagIndex),1);updateTags(e);localLog(e,`删除${type==='mail'?'邮件':'专家'}标签：${removed}`);return;}
 if(b.hasAttribute('data-auto-qa')){const s=stateFor(e);if(!s.mailTags.includes('示例 QA 标签'))s.mailTags.push('示例 QA 标签');updateTags(e);localLog(e,'自动添加 QA 标签（本地示例）');notify('已添加示例 QA 标签，未调用正式服务');return;}
 if(b.hasAttribute('data-return-chat')){toggleView('chat');const target=localQuery(e,`[data-process-mail="${stateFor(e).source}"]`);target?.closest('.message')?.scrollIntoView({block:'center'});return;}
 if(b.hasAttribute('data-open-workbench')||b.dataset.processMail){if(b.dataset.processMail&&b.dataset.processMail!==stateFor(e).source){saveSourceDraft(e);stateFor(e).source=b.dataset.processMail;refreshSource(e);restoreSourceDraft(e);updateTags(e);mountPreviewWorkbench(e);}toggleView('workbench');return;}
 if(b.hasAttribute('data-mark-current')||b.dataset.resolve){event.stopPropagation();const m=b.dataset.resolve?messages[e.id].find(m=>m.id===b.dataset.resolve):chosenMail(e);m.pending=false;e.pending=messages[e.id].filter(m=>m.pending).length;syncPending(e);localLog(e,'标记来信已处理：'+m.subject);renderList();if(activePanel==='chat')renderTimeline();notify('当前来信已在本地标记处理');return;}
 if(b.dataset.sourceTranslate){const m=chosenMail(e),host=localQuery(e,'.source-translation');host.hidden=!host.hidden;b.textContent=host.hidden?'翻译为中文':'收起译文';return;}
 if(b.dataset.sourceAttachment){showDialog(chosenMail(e).attachment,'附件预览示例，未连接真实文件。');return;}
 if(b.dataset.rich){const editor=localQuery(e,'.manual-editor');editor.focus();if(b.dataset.rich==='createLink'){openLinkDialog(editor);return;}document.execCommand(b.dataset.rich,false,null);return;}
 if(b.hasAttribute('data-send-manual')){const editor=localQuery(e,'.manual-editor'),text=editor.innerText.trim();if(!text){notify('请先填写人工回复正文');editor.focus();return;}messages[e.id].push({id:'local-'+Date.now(),direction:'out',date:'2026-09-07',time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}),subject:localQuery(e,'.manual-subject').value,body:text,kind:'模拟人工回复'});localLog(e,'发送人工回复（仅本地模拟）');localQuery(e,'.mail-total').textContent=messages[e.id].length;editor.innerHTML='';stateFor(e).draftHtml='';sourceDrafts.delete(stateFor(e).source);localQuery(e,'.manual-status').textContent='未填写';e.snippet='我：'+text.replace(/\n/g,' ');e.time='刚刚';experts.splice(experts.indexOf(e),1);experts.unshift(e);renderList();scope='all';toggleView('chat');notify('模拟人工回复已加入会话，未发送真实邮件');}
}
function openTagDialog(e,type){const dialog=$('#detailDialog');$('#dialogTitle').textContent=type==='mail'?'添加邮件标签':'添加专家标签';$('#dialogBody').innerHTML='<label>标签名称<input id="newPreviewTag" placeholder="输入标签名称" maxlength="30"></label><button class="button primary" id="confirmPreviewTag" style="margin-top:14px">添加标签</button>';dialog.showModal();$('#confirmPreviewTag').onclick=()=>{const value=$('#newPreviewTag').value.trim();if(!value)return;const tags=stateFor(e)[type+'Tags'];if(!tags.includes(value))tags.push(value);updateTags(e);localLog(e,`添加${type==='mail'?'邮件':'专家'}标签：${value}`);dialog.close();};}
function openLinkDialog(editor){const selection=getSelection(),range=selection.rangeCount?selection.getRangeAt(0).cloneRange():null;$('#dialogTitle').textContent='插入链接';$('#dialogBody').innerHTML='<label>链接地址<input id="previewLinkUrl" placeholder="https://"></label><button class="button primary" id="insertPreviewLink" style="margin-top:14px">插入</button>';$('#detailDialog').showModal();$('#insertPreviewLink').onclick=()=>{const url=$('#previewLinkUrl').value.trim();if(!/^https?:\/\//i.test(url)){notify('请输入 http 或 https 链接');return;}$('#detailDialog').close();editor.focus();if(range){selection.removeAllRanges();selection.addRange(range);}document.execCommand('createLink',false,url);};}
$('#bulkOutreachPreview').onclick=()=>showDialog('批量发送','此入口保留正式页面的批量发送操作。\n本次为收发件箱布局预览，未连接发送任务。');
$('#autoReplyPreview').onclick=()=>{const on=$('#autoReplyPreview').dataset.on==='true';$('#autoReplyPreview').dataset.on=String(!on);$('#autoReplyPreview').textContent=on?'自动回复：全部关闭':'自动回复：全部开启';notify('仅切换预览状态，未修改正式邮箱配置');};
const mailFilters={direction:'',kind:'',recipient:'',keyword:'',start:'',end:''};
const mailboxKinds=['专家','待匹配','自动回复','手动回复','首发','待处理','收件','发件'];
$('#filterDialog .filter-fields').innerHTML=`<label>邮箱账号<select id="accountFilter"><option value="">全部邮箱账号</option><option>recruit-01</option><option>recruit-02</option></select></label><label>收发方向<select id="directionFilter"><option value="">全部收发方向</option><option value="in">收件 (INBOUND)</option><option value="out">发件 (OUTBOUND)</option></select></label><label>邮件标签<select id="tagFilter"><option value="">全部标签</option>${mailboxKinds.map(k=>`<option>${k}</option>`).join('')}</select></label><label>收件人邮箱<input id="recipientFilter" placeholder="过滤收件人邮箱"></label><label class="filter-span">主题 / 内容关键词<input id="keywordFilter" placeholder="过滤主题/内容关键词"></label><label>开始日期<input id="startFilter" type="date"></label><label>结束日期<input id="endFilter" type="date"></label>`;
$('#filterOpen').onclick=()=>{$('#accountFilter').value=account;$('#tagFilter').value=mailFilters.kind;['direction','recipient','keyword','start','end'].forEach(k=>$('#'+k+'Filter').value=mailFilters[k]);$('#filterDialog').showModal();};
$('#resetFilters').onclick=()=>{$('#filterDialog').querySelectorAll('input,select').forEach(e=>e.value='');};
$('#applyFilters').onclick=()=>{const start=$('#startFilter').value,end=$('#endFilter').value;if(start&&end&&start>end){notify('开始日期不能晚于结束日期');return;}account=$('#accountFilter').value;mailFilters.kind=$('#tagFilter').value;['direction','recipient','keyword','start','end'].forEach(k=>mailFilters[k]=$('#'+k+'Filter').value.trim());$('#filterDialog').close();$('#filterOpen').textContent=account||Object.values(mailFilters).some(Boolean)?'筛选条件 · 已生效':'筛选条件';reconcileSelection();};
function matchesMailFilter(e,m){
 if(mailFilters.direction&&m.direction!==mailFilters.direction)return false;
 if(mailFilters.start&&m.date<mailFilters.start)return false;
 if(mailFilters.end&&m.date>mailFilters.end)return false;
 if(mailFilters.keyword&&!`${m.subject} ${m.body}`.toLowerCase().includes(mailFilters.keyword.toLowerCase()))return false;
 if(mailFilters.recipient&&!(m.direction==='out'?e.email:e.account+'@example.org').toLowerCase().includes(mailFilters.recipient.toLowerCase()))return false;
 const kinds=['专家',m.direction==='in'?'收件':'发件',...(m.tags||[])];
 if(m.pending)kinds.push('待处理');if(m.kind==='首发')kinds.push('首发');if(m.kind.includes('自动'))kinds.push('自动回复');if(m.kind.includes('手动')||m.kind.includes('人工'))kinds.push('手动回复');
 return !mailFilters.kind||kinds.includes(mailFilters.kind);
}
visibleExperts=function(){return experts.filter(e=>(filter!=='followed'||followed.has(e.id))&&(filter!=='pending'||e.pending>0)&&(filter!=='waiting'||!hasInbound(e))&&(!account||e.account===account)&&`${e.name} ${e.en} ${e.org} ${e.email}`.toLowerCase().includes(search.toLowerCase())&&messages[e.id].some(m=>matchesMailFilter(e,m)));};
const baseRenderList=renderList;
renderList=function(){
 baseRenderList();
 $('#listCaption').textContent=account||Object.values(mailFilters).some(Boolean)?'已筛选专家 · 右侧保留完整往来':'按最近往来排序';
 const count=$('#waitingCount');if(count)count.textContent=experts.filter(e=>!hasInbound(e)).length;
 $('#expertList').querySelectorAll('[data-expert]').forEach(row=>{const e=experts.find(e=>e.id===Number(row.dataset.expert));const badge=document.createElement('div');badge.className='row-contact-state';badge.innerHTML=!hasInbound(e)?'<span class="waiting-state">待专家回复</span><span>收 0 · 发 '+messages[e.id].length+'</span>':e.pending?'<span class="pending-state">待处理来信</span>':'<span class="answered-state">已回复</span>';row.querySelector('.expert-copy').append(badge);});
};
window.addEventListener('hashchange',()=>{const panel=location.hash.slice(1);if(['chat','workbench','logs'].includes(panel))toggleView(panel);});
renderConversation();

const waitingTab=document.createElement('button');waitingTab.className='filter-tab';waitingTab.dataset.filter='waiting';waitingTab.setAttribute('role','tab');waitingTab.innerHTML='待回复<span class="tab-count" id="waitingCount">2</span>';waitingTab.onclick=()=>{filter='waiting';reconcileSelection();};$('.filter-tabs').append(waitingTab);
renderList();
