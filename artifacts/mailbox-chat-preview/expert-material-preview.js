/* Reuses the SAME panel, file objects and download queue in both screens. Local preview only. */
const requestedExpertScene=location.hash==='#experts';
materialFiles.splice(0,materialFiles.length,...expertSceneFiles);
messages[1][0].body='[布局示例正文，非真实邮件原文]\n第一批材料，19 个附件。';
messages[1][1].body='[布局示例正文，非真实邮件原文]\n材料沟通与后续跟进。';
messages[1][2].body='[布局示例正文，非真实邮件原文]\n补充材料，20 个附件。此前还有历史简历。';
messages[1][0].translated=null;messages[1][2].translated=null;
const sceneExpert=experts.find(e=>e.id===1);
Object.assign(sceneExpert,{name:'Cole D. Bendor',en:'Cole D. Bendor',email:'gilad.twig@gmail.com',org:'Hebrew University of Jerusalem',account:'LuKai',initial:'CB',snippet:'补充 39 个附件 · 历史简历 1 份'});
materialDrawer.querySelector('.materials-heading .subtle').textContent='Cole D. Bendor · 专家资料';
materialDrawer.querySelector('.materials-heading h2 span').textContent='40';
const historyChoice=document.createElement('option');historyChoice.value='history';historyChoice.textContent='历史来信 · 1 个';materialPanel.querySelector('select').append(historyChoice);
materialPanel.querySelector('option[value=m13]').textContent='来信 218 · 20 个';materialPanel.querySelector('option[value=m11]').textContent='来信 217 · 19 个';
const mailboxMain=$('main.preview-main');
const expertMain=document.createElement('main');expertMain.className='expert-scene';expertMain.hidden=true;
expertMain.innerHTML=`<header class="expert-scene-title"><div><h1>专家列表</h1><p>查看联系状态、邮件时间线和人工处理。</p></div><span class="scene-preview-label">线上布局复刻 · 本地交互预览</span><button class="button" data-scene-info>刷新</button></header>
<div class="expert-scene-tools"><button class="button" data-scene-info>⚑ 筛选 <b>2</b></button><div><select aria-label="切换材料演示场景"><option value="new">新来信按需下载（模拟）</option><option value="stored">全部已落地（模拟）</option></select><button class="button" data-scene-info>刷新</button><button class="button primary" data-scene-info>发现专家 ▾</button><button class="button" data-scene-info>回刷 ES</button></div></div>
<div class="expert-scene-grid"><aside class="scene-list"><header><h2>专家列表</h2><span>▏ ▎ ▌</span></header><p>筛选结果：2 位专家，当前显示 2 位</p><button class="scene-person active" data-scene-expert="1"><span class="scene-check">□</span><div><strong>Cole D. Bendor <em>已邀约</em></strong><p>有效　Hebrew University of Jerusalem</p><p>gilad.twig@gmail.com</p><p>账号：LuKai　<span class="chip green">已补充</span>　学术科研</p><span class="chip amber">新发现</span> <span class="chip">姓名错误</span> <span class="chip">专业合适</span> <span class="chip">承诺回复材料</span></div></button><button class="scene-person" data-scene-expert="2"><span class="scene-check">□</span><div><strong>Sayed Mortaza Fayez <em>已邀约</em></strong><p>有效　Afghanistan</p><p>sayedmortazafayez455@gmail.com</p><p>1Faculty of Medicine, Kabul University…</p><p>账号：WuWei_WB　<span class="chip green">已补充</span></p><span class="chip amber">新发现</span> <span class="chip">学术科研</span></div></button><footer><select aria-label="专家每页数量"><option>50 条/页</option></select><span>第 1 / 1 页</span></footer></aside>
<section class="scene-detail"><header><h2>专家引进状态与联系详情</h2><button class="button small" data-go-mailbox>查看收发往来 →</button></header><div class="scene-contact-tools"><button class="button" data-scene-info>● 发件 LuKai ▾</button><select aria-label="邮件模板"><option>项目介绍邮件 - Remote advisory collaboration with Chinese industry</option></select><button class="button primary" data-scene-info>发送邮件</button><button class="button" data-scene-info>⚙ 更多</button></div><div class="scene-material-categories"><span>材料</span>${['简历','护照','学位','工作','出版','专利','研究'].map(t=>`<button data-scene-info>${t} ▾</button>`).join('')}</div>
<div class="scene-detail-scroll"><div class="scene-other-sections"><details><summary>邮箱别名 <span>主邮箱：gilad.twig@gmail.com</span></summary><div><input placeholder="输入邮箱地址" aria-label="邮箱别名"><button class="button primary" data-scene-info>添加别名</button></div></details><details><summary>阶段流转历史 <span>首封已发送 → 已转人工</span></summary><p>2026-09-07 15:04:40 · ATTACHMENTS_RECEIVED_MANUAL</p><p>2026-08-22 13:00:36 · 新建 → 首封已发送</p></details></div>
<div class="scene-doc-card"><header><h3>▤ 专家上传资料 <span>40</span></h3><button class="button primary small" data-scene-ai>AI 智能分析</button></header><div class="scene-material-host"></div></div><details class="scene-operator"><summary>操作记录</summary><p>本地演示：材料下载状态同步到收发件箱。</p></details></div></section></div>`;
mailboxMain.after(expertMain);
let sceneSelected=1;
function showExpertScene(){materialDrawer.close();mailboxMain.hidden=true;expertMain.hidden=false;expertMain.querySelector('.scene-material-host').append(materialPanel);renderMaterials();setSceneNav('contacts');history.replaceState(null,'','#experts');}
function showMailboxScene(){expertMain.hidden=true;mailboxMain.hidden=false;materialDrawer.append(materialPanel);selected=1;renderList();renderConversation();setSceneNav('mailbox');history.replaceState(null,'','#chat');}
function setSceneNav(name){document.querySelectorAll('.nav-tab[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===name));}
document.addEventListener('click',event=>{const nav=event.target.closest('.nav-tab[data-view]');if(!nav)return;if(nav.dataset.view==='contacts'||nav.dataset.view==='mailbox'){event.stopImmediatePropagation();nav.dataset.view==='contacts'?showExpertScene():showMailboxScene();}},true);
expertMain.addEventListener('click',event=>{const b=event.target.closest('button');if(!b)return;if(b.hasAttribute('data-go-mailbox'))showMailboxScene();if(b.hasAttribute('data-scene-info'))notify('沿用线上操作入口；本地预览未连接业务接口');if(b.hasAttribute('data-scene-ai'))openSceneAnalysis();if(b.dataset.sceneExpert){sceneSelected=Number(b.dataset.sceneExpert);expertMain.querySelectorAll('.scene-person').forEach(x=>x.classList.toggle('active',x===b));if(sceneSelected===2){showDialog('专家材料预览','本次仅载入 Cole D. Bendor 的材料场景；未查询第二位专家的资料，不能据此判断是否有材料。');sceneSelected=1;expertMain.querySelectorAll('.scene-person').forEach(x=>x.classList.toggle('active',x.dataset.sceneExpert==='1'));}}});
expertMain.querySelector('[aria-label="切换材料演示场景"]').onchange=event=>{if(materialActive||materialFiles.some(f=>f.state==='queued')||analysisRunning){event.target.value=sceneScenario;notify('请等待当前模拟任务结束后切换场景');return;}sceneScenario=event.target.value;materialFiles.forEach(f=>{f.state=sceneScenario==='stored'||f.id===40?'ready':'remote';f.progress=0;f.attempt=0;f.selected=false;});renderMaterials();renderMaterialBlocks();};
let sceneScenario='new',analysisRunning=false,analysisSelection=[];
const analysisDialog=document.createElement('dialog');analysisDialog.className='scene-analysis-dialog';analysisDialog.setAttribute('aria-label','选择分析文件');document.body.append(analysisDialog);
function canAnalyzeFile(f){return /\.(pdf|txt|csv)$/i.test(f.name);}
function openSceneAnalysis(){
 if(analysisRunning){analysisDialog.showModal();return;}
 const checked=materialFiles.filter(f=>f.selected);analysisSelection=(checked.length?checked:materialFiles.filter(f=>f.documentType==='简历')).map(f=>f.id);
 analysisDialog.innerHTML=`<header><h2>选择分析文件</h2><button class="button small" data-analysis-close>关闭</button></header><p class="analysis-explainer">${checked.length?'已带入材料清单勾选项。':'默认选择简历，可调整所选文件。'} 打开此窗口不会下载，也不会启动分析。</p><div class="analysis-selection-list">${materialFiles.map(f=>`<label><input type="checkbox" data-analysis-id="${f.id}" ${analysisSelection.includes(f.id)&&canAnalyzeFile(f)?'checked':''} ${canAnalyzeFile(f)?'':'disabled'}><span><strong title="${esc(f.name)}">${esc(f.name)}</strong><small>${esc(f.documentType)} · ${materialStateNames[f.state]}${canAnalyzeFile(f)?'':' · 当前解析器不支持该格式'}</small></span></label>`).join('')}</div><div class="analysis-action-summary"></div><footer><span>本地模拟 · 不调用模型</span><button class="button primary" data-analysis-start></button></footer>`;
 analysisSelection=analysisSelection.filter(id=>canAnalyzeFile(materialFiles.find(f=>f.id===id)));updateAnalysisSummary();analysisDialog.showModal();
}
function updateAnalysisSummary(){const files=materialFiles.filter(f=>analysisSelection.includes(f.id));const missing=files.filter(f=>f.state!=='ready');analysisDialog.querySelector('.analysis-action-summary').textContent=`已选 ${files.length} 个 · 已存服务器 ${files.length-missing.length} 个 · 需获取 ${missing.length} 个。仅处理所选文件，不下载其他材料。`;const b=analysisDialog.querySelector('[data-analysis-start]');b.disabled=!files.length;b.textContent=missing.length?'获取所选文件并分析':'开始分析';}
analysisDialog.addEventListener('change',event=>{const id=Number(event.target.dataset.analysisId);if(!id)return;analysisSelection=analysisSelection.filter(x=>x!==id);if(event.target.checked)analysisSelection.push(id);updateAnalysisSummary();});
analysisDialog.addEventListener('click',event=>{const b=event.target.closest('button');if(!b)return;if(b.hasAttribute('data-analysis-close'))analysisDialog.close();if(b.hasAttribute('data-analysis-retry'))startSceneAnalysis();if(b.hasAttribute('data-analysis-start'))startSceneAnalysis();});
function startSceneAnalysis(){
 const files=materialFiles.filter(f=>analysisSelection.includes(f.id));if(!files.length||analysisRunning)return;
 analysisRunning=true;queueMaterials(files);
 analysisDialog.innerHTML='<header><h2>AI 智能分析</h2><button class="button small" data-analysis-close>关闭</button></header><div class="analysis-progress"></div><footer>本地模拟 · 不调用模型</footer>';
 const watch=setInterval(()=>{const ready=files.filter(f=>f.state==='ready').length,failed=files.filter(f=>f.state==='failed').length;analysisDialog.querySelector('.analysis-progress').innerHTML=`<strong>获取所选文件 ${ready} / ${files.length}</strong><p>${failed?failed+' 个文件获取失败，尚未开始分析。':'文件就绪后开始分析；关闭窗口不取消获取任务。'}</p>`;
 if(files.every(f=>['ready','failed'].includes(f.state))){clearInterval(watch);if(failed){analysisRunning=false;analysisDialog.querySelector('.analysis-progress').innerHTML+='<button class="button" data-analysis-retry>重试失败文件并分析</button>';}else{analysisDialog.querySelector('.analysis-progress').innerHTML='<strong>文件已就绪 · 模拟分析中</strong>';setTimeout(()=>{analysisRunning=false;analysisDialog.querySelector('.analysis-progress').innerHTML=`<strong>模拟流程完成 · ${files.length} 个所选文件</strong><p>正式接入后在此展示分析结果。本次未读取附件正文，未产生真实分析结论。</p>`;},800);}}},250);
}
const originalSceneMaterialsRender=renderMaterials;
renderMaterials=function(){originalSceneMaterialsRender();if(expertMain)expertMain.querySelector('.scene-doc-card h3 span').textContent=materialFiles.length;};
conversationCache.delete(1);renderConversation();renderList();
if(requestedExpertScene)showExpertScene();
window.addEventListener('hashchange',()=>{if(location.hash==='#experts')showExpertScene();});
