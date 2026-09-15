/* Proposed attachment workflow. Fictional metadata and simulated transfers only. */
const materialFiles = Array.from({length:39},(_,i)=>({
 id:i+1,mailId:i<19?'m11':'m13',
 name:i===0?'Jianing_Chen_CV.pdf':i===19?'Research_materials_supplement.zip':i===20?'Degree_certificate.pdf':i===21?'Employment_certificate.pdf':`Research_publication_${String(i+1).padStart(2,'0')}.pdf`,
 size:i===19?null:(i===0?0.24:Number((0.35+(i%7)*0.42).toFixed(2))),
 state:'remote',progress:0,selected:false,attempt:0
}));
const materialStateNames={remote:'仅文件信息',queued:'排队中',loading:'下载中',ready:'已存服务器',failed:'下载失败'};
let materialSearch='',materialFilter='all',materialPage=1,materialActive=0;
messages[1][0].body='Hi,\nPlease find the first batch of my research materials attached (19 files).\nBest regards, Jianing';
messages[1][2].body='Hi,\nHere are 20 additional files for your review. Together with my earlier email, there are 39 attachments.\nPlease let me know if you need anything else.\nBest regards, Jianing';
messages[1][0].translated='您好，附件为第一批研究材料，共 19 份。祝好，嘉宁';
messages[1][2].translated='您好，补充发送 20 份材料，两封来信合计 39 个附件。需要其他资料请告诉我。祝好，嘉宁';
messages[1][2].attachment=null;
experts.find(e=>e.id===1).snippet='补充 20 份材料，两封来信共 39 个附件。';
function materialSummary(list){return `${list.length} 个附件 · ${list.filter(f=>f.state==='ready').length} 已存服务器`;}
function renderMaterialBlocks(){
 const e=current();if(e?.id!==1)return;
 const root=rootFor(e);root.querySelectorAll('.attachment,.source-attachment,.mail-materials').forEach(n=>n.remove());
 for(const m of messages[1]){
  const files=materialFiles.filter(f=>f.mailId===m.id);if(!files.length)continue;
  const bubble=root.querySelector(`[data-mail-id="${m.id}"] .message-bubble`);if(!bubble)continue;
  const block=document.createElement('details');block.className='mail-materials';
  block.innerHTML=`<summary><span>▤ ${files.length} 个附件</span><span class="subtle">${files.filter(f=>f.state==='ready').length} 已存 · 展开文件名</span></summary><div class="material-name-preview">${files.slice(0,3).map(f=>`<div title="${esc(f.name)}">${esc(f.name)}</div>`).join('')}<button class="button small" data-open-materials="${m.id}">查看本封 ${files.length} 个附件 →</button></div>`;
  bubble.querySelector('.message-foot').before(block);
 }
 const header=root.querySelector('.header-actions');if(!header.querySelector('[data-material-hub]')){const b=document.createElement('button');b.className='button small';b.dataset.materialHub='';b.textContent='材料 '+materialFiles.length;b.onclick=()=>openMaterials();header.prepend(b);}
 root.querySelectorAll('[data-open-materials]').forEach(b=>b.onclick=()=>openMaterials(b.dataset.openMaterials));
 const source=root.querySelector('.source-card');if(source&&!source.querySelector('.source-material-info')){const info=document.createElement('div');info.className='source-material-info';info.textContent='本封含 20 个附件 · 工作台仅使用邮件正文与文件名，未读取附件内容';source.append(info);}
}
const timelineBeforeMaterials=renderTimeline;
renderTimeline=function(){timelineBeforeMaterials();renderMaterialBlocks();const timeline=$('#timeline');if(timeline)timeline.scrollTop=timeline.scrollHeight;};
const materialDrawer=document.createElement('dialog');materialDrawer.className='materials-drawer';materialDrawer.setAttribute('aria-label','专家材料');
materialDrawer.innerHTML=`<header class="materials-heading"><div><span class="subtle">陈嘉宁 · 两封来信</span><h2>专家材料 <span>39</span></h2></div><button class="button small" data-close-materials>关闭</button></header><div class="materials-policy"><strong>文件名已同步，附件按需获取</strong><p>打开清单不会下载。选择所需文件，再下载到服务器；已保存的文件可直接取用。</p></div><div class="materials-stats"></div><div class="materials-filters"><input type="search" aria-label="搜索附件文件名" placeholder="搜索文件名"><select aria-label="筛选附件"><option value="all">全部附件</option><option value="m13">最新来信 · 20 个</option><option value="m11">上一封来信 · 19 个</option><option value="remote">仅文件信息</option><option value="ready">已存服务器</option><option value="failed">下载失败</option></select></div><div class="materials-table-head"><label><input type="checkbox" aria-label="选择本页附件">本页</label><span>文件名 / 来源</span><span>存储状态</span></div><div class="materials-rows"></div><div class="materials-pagination"><span></span><button class="button small" data-material-prev>上一页</button><button class="button small" data-material-next>下一页</button></div><footer class="materials-footer"><div><strong data-material-selection>已选 0 个</strong><span class="subtle">仅本地演示，不连接邮箱或下载真实文件</span></div><button class="button" data-material-clear>清空选择</button><button class="button primary" data-material-download>下载所选到服务器</button></footer>`;
document.body.append(materialDrawer);
const materialPanel=document.createElement('div');materialPanel.className='shared-material-panel';
Array.from(materialDrawer.children).slice(1).forEach(n=>materialPanel.append(n));materialDrawer.append(materialPanel);
materialDrawer.querySelector('[data-close-materials]').onclick=()=>materialDrawer.close();
function filteredMaterials(){return materialFiles.filter(f=>f.name.toLowerCase().includes(materialSearch.toLowerCase())&&(materialFilter==='all'||f.mailId===materialFilter||f.state===materialFilter)).sort((a,b)=>b.mailId.localeCompare(a.mailId)||a.id-b.id);}
function openMaterials(mail='all'){materialDrawer.append(materialPanel);materialFilter=mail;materialPage=1;materialPanel.querySelector('select').value=mail;renderMaterials();materialDrawer.showModal();}
function renderMaterials(){
 const list=filteredMaterials(),pages=Math.max(1,Math.ceil(list.length/10));materialPage=Math.min(materialPage,pages);const rows=list.slice((materialPage-1)*10,materialPage*10);
 const stats=materialPanel.querySelector('.materials-stats');stats.innerHTML=['remote','loading','ready','failed'].map(s=>`<span>${materialStateNames[s]} <strong>${materialFiles.filter(f=>s==='loading'?['queued','loading'].includes(f.state):f.state===s).length}</strong></span>`).join('');
 materialPanel.querySelector('.materials-rows').innerHTML=rows.length?rows.map(f=>`<div class="material-row"><input type="checkbox" data-material-select="${f.id}" aria-label="选择 ${esc(f.name)}" ${f.selected?'checked':''}><div class="material-file"><strong title="${esc(f.name)}">${esc(f.name)}</strong><span>${esc(f.sourceLabel||(f.mailId==='m13'?'09/07 10:42 · 最新来信':'09/06 16:28 · 上一封来信'))} · ${f.size===null?'大小待获取':(f.exactSize?'':'约 ')+f.size+' MB'} · ${esc(f.documentType||'其他材料')} · 待审核</span></div><div class="material-file-state ${f.state}"><span>${materialStateNames[f.state]}${f.state==='loading'?' '+f.progress+'%':''}</span>${f.state==='loading'?`<progress max="100" value="${f.progress}"></progress>`:''}${f.state==='failed'?'<small>读取超时 · 可重试</small>':''}${f.state==='remote'?`<button data-material-fetch="${f.id}">下载到服务器</button>`:f.state==='failed'?`<button data-material-fetch="${f.id}">重试</button>`:f.state==='ready'?`<button data-material-local="${f.id}">下载到电脑</button>`:''}</div></div>`).join(''):'<div class="empty-state">没有符合条件的附件</div>';
 materialPanel.querySelector('.materials-pagination span').textContent=`共 ${list.length} 个 · ${materialPage} / ${pages} 页`;
 materialPanel.querySelector('[data-material-prev]').disabled=materialPage===1;materialPanel.querySelector('[data-material-next]').disabled=materialPage===pages;
 const check=materialPanel.querySelector('[aria-label="选择本页附件"]');check.checked=rows.length>0&&rows.every(f=>f.selected);check.indeterminate=rows.some(f=>f.selected)&&!check.checked;
 const selectedFiles=materialFiles.filter(f=>f.selected);materialPanel.querySelector('[data-material-selection]').textContent=`已选 ${selectedFiles.length} 个`;
 materialPanel.querySelector('[data-material-download]').disabled=!selectedFiles.some(f=>['remote','failed'].includes(f.state));
}
function queueMaterials(files){files.filter(f=>['remote','failed'].includes(f.state)).forEach(f=>{f.state='queued';f.selected=false;});renderMaterials();pumpMaterialQueue();}
function pumpMaterialQueue(){while(materialActive<2){const file=materialFiles.find(f=>f.state==='queued');if(!file)break;materialActive++;file.state='loading';file.progress=0;file.attempt++;
 const tick=setInterval(()=>{file.progress=Math.min(100,file.progress+20);if(file.progress===100){clearInterval(tick);file.state=file.id===20&&file.attempt===1?'failed':'ready';materialActive--;if(file.state==='ready'&&file.size===null)file.size=12.8;renderMaterialBlocks();pumpMaterialQueue();}renderMaterials();},600);
 }renderMaterials();}
materialPanel.addEventListener('click',event=>{const b=event.target.closest('button');if(!b)return;if(b.hasAttribute('data-close-materials'))materialDrawer.close();if(b.hasAttribute('data-material-prev')){materialPage--;renderMaterials();}if(b.hasAttribute('data-material-next')){materialPage++;renderMaterials();}if(b.hasAttribute('data-material-clear')){materialFiles.forEach(f=>f.selected=false);renderMaterials();}if(b.hasAttribute('data-material-download'))queueMaterials(materialFiles.filter(f=>f.selected));if(b.dataset.materialFetch)queueMaterials([materialFiles.find(f=>f.id===Number(b.dataset.materialFetch))]);if(b.dataset.materialLocal){notify('本地演示：正式接入后从服务器下载已保存文件，不重复访问邮箱');}});
materialPanel.addEventListener('change',event=>{const t=event.target;if(t.dataset.materialSelect)materialFiles.find(f=>f.id===Number(t.dataset.materialSelect)).selected=t.checked;if(t.matches('select')){materialFilter=t.value;materialPage=1;}if(t.getAttribute('aria-label')==='选择本页附件')filteredMaterials().slice((materialPage-1)*10,materialPage*10).forEach(f=>f.selected=t.checked);renderMaterials();});
materialPanel.querySelector('input[type=search]').oninput=event=>{materialSearch=event.target.value;materialPage=1;renderMaterials();};
$('#checkReplies').onclick=()=>showDialog('检查回复 · 预览结果','邮件同步完成（示例）\n\n4 / 4 个邮箱完成检查\n新增 2 封来信，发现 39 个附件\n附件仅登记文件信息，未下载内容\n\n附件后台下载单独计进度，不影响检查其他邮箱。');
refreshSource(experts.find(e=>e.id===1));renderList();renderTimeline();
