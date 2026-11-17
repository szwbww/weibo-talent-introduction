'use strict';
const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const seed = [
  {id:'o1',mode:'original',name:'Publications 3.rar',originalName:'Publications 3.rar',size:'67.0 MB',note:'包含 3 篇论文全文，研究方向涉及植物抗逆与耐受机制。',tags:['论文全文','植物抗逆'],source:'手动上传',actor:'admin',date:'2026-09-21 00:46',status:'待审核',archive:true},
  {id:'o2',mode:'original',name:'Required Personal Documents.pdf',originalName:'Required Personal Documents.pdf',size:'1.7 MB',note:'个人证明材料合集：护照、本硕博学位证明及工作证明。',tags:['身份证明','学位证明','工作证明'],source:'邮件附件',actor:'Re: Remote advisory collaboration with Chinese industry — Plant Stress Responses and Tolerance',date:'2026-09-19 02:23',status:'待审核'},
  {id:'o3',mode:'original',name:'Prof_Aziza_Ahmed_Aboulila_CV.pdf',originalName:'Prof_Aziza_Ahmed_Aboulila_CV.pdf',size:'418.5 KB',note:'教育背景、工作经历、研究方向及代表性学术成果。',tags:['个人简历','英文'],source:'邮件附件',actor:'Re: Remote advisory collaboration with Chinese industry — Plant Stress Responses and Tolerance',date:'2026-09-12 10:49',status:'待审核'},
  {id:'p1',mode:'parsed',name:'01_Plant_Stress_Responses.pdf',originalName:'01_Plant_Stress_Responses.pdf',size:'22.4 MB',note:'植物胁迫响应研究论文全文，含实验方法、结果及图表。',tags:['论文全文','代表作','植物抗逆'],source:'压缩包解压',parent:'o1',path:'Publications 3 / 01_Plant_Stress_Responses.pdf',date:'2026-09-21 00:48',status:'待审核'},
  {id:'p2',mode:'parsed',name:'02_Salt_Tolerance.pdf',originalName:'02_Salt_Tolerance.pdf',size:'18.6 MB',note:'盐胁迫耐受机制研究论文全文。',tags:['论文全文','盐胁迫'],source:'压缩包解压',parent:'o1',path:'Publications 3 / 02_Salt_Tolerance.pdf',date:'2026-09-21 00:48',status:'待审核'},
  {id:'p3',mode:'parsed',name:'03_Crop_Resilience.pdf',originalName:'03_Crop_Resilience.pdf',size:'25.8 MB',note:'作物适应性研究论文全文及参考文献。',tags:['论文全文','作物研究'],source:'压缩包解压',parent:'o1',path:'Publications 3 / 03_Crop_Resilience.pdf',date:'2026-09-21 00:48',status:'待审核'},
  {id:'p4',mode:'parsed',name:'Required Personal Documents.pdf',originalName:'Required Personal Documents.pdf',size:'1.7 MB',note:'独立 PDF 整理入库，保留证明材料合集。',tags:['身份证明','学位证明','工作证明'],source:'原文件入库',parent:'o2',path:'Required Personal Documents.pdf',date:'2026-09-19 02:25',status:'待审核'},
  {id:'p5',mode:'parsed',name:'Prof_Aziza_Ahmed_Aboulila_CV.pdf',originalName:'Prof_Aziza_Ahmed_Aboulila_CV.pdf',size:'418.5 KB',note:'独立简历文件，可单独归类、标记与管理。',tags:['个人简历','英文'],source:'原文件入库',parent:'o3',path:'Prof_Aziza_Ahmed_Aboulila_CV.pdf',date:'2026-09-12 10:51',status:'待审核'}
];
const storageKey = 'expert-materials-preview-v1';
let items = structuredClone(seed), mode = 'original', sourceParent = '', editingId = '', deletingId = '', pendingFiles = [], toastTimer;
try { const saved = JSON.parse(localStorage.getItem(storageKey)); if (Array.isArray(saved) && saved.every(item => item && typeof item.id === 'string' && Array.isArray(item.tags))) items = saved.map(({type, ...item}) => item); } catch (_) { /* Fresh demo when browser storage is unavailable. */ }
function persist() { try { localStorage.setItem(storageKey, JSON.stringify(items)); } catch (_) { notify('浏览器存储不可用；修改仅在当前页面有效。'); } }
function notify(message) { clearTimeout(toastTimer); $('#toast').textContent = message; $('#toast').hidden = false; toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 4500); }
function tagsFrom(value) { return [...new Set(value.split(/[,，;；\n]/).map(x => x.trim()).filter(Boolean))].slice(0, 12); }
function activeChildren(id) { return items.filter(item => item.parent === id && !item.deleted); }
function sourceOptions() {
  $('#source-filter').innerHTML = '<option value="">全部来源</option>' + (mode === 'original' ? ['手动上传','邮件附件'] : ['压缩包解压','原文件入库']).map(label => `<option>${label}</option>`).join('');
}
function setMode(next, parent = '') {
  mode = next; sourceParent = parent; $('#search').value = ''; $('#tag-filter').value = ''; sourceOptions(); render();
}
function tagOptions() {
  const select = $('#tag-filter'), current = select.value;
  const tags = [...new Set(items.filter(item => item.mode === mode && !item.deleted).flatMap(item => item.tags))];
  const options = tags.map(tag => ({value:'tag:' + tag, label:tag}));
  if (current.startsWith('tag:') && !options.some(option => option.value === current)) options.push({value:current, label:current.slice(4)});
  select.innerHTML = '<option value="">全部标签</option><option value="__untagged__">未添加标签</option>' + options.map(option => `<option value="${escapeHtml(option.value)}">${escapeHtml(option.label)}</option>`).join('');
  select.value = current;
}
function render() {
  tagOptions();
  $('#original-count').textContent = items.filter(x => x.mode === 'original' && !x.deleted).length;
  $('#parsed-count').textContent = items.filter(x => x.mode === 'parsed' && !x.deleted).length;
  $('#trash-count').textContent = items.filter(x => x.deleted).length;
  document.querySelectorAll('[data-mode]').forEach(button => { button.setAttribute('aria-selected', String(button.dataset.mode === mode)); button.tabIndex = button.dataset.mode === mode ? 0 : -1; });
  $('#file-panel').setAttribute('aria-labelledby', 'tab-' + mode);
  $('#mode-description').textContent = mode === 'original' ? '保留上传原件；压缩包按一个文件管理。' : '解压后的每个文件单独管理，保留来源与包内路径。';
  const query = $('#search').value.trim().toLowerCase(), tag = $('#tag-filter').value, source = $('#source-filter').value;
  const candidates = items.filter(x => x.mode === mode && !x.deleted);
  const filtered = candidates.filter(x => (!tag || (tag === '__untagged__' ? x.tags.length === 0 : x.tags.includes(tag.slice(4)))) && (!source || x.source === source) && (!sourceParent || x.parent === sourceParent) && (!query || [x.name,x.note,...x.tags].join(' ').toLowerCase().includes(query)));
  $('#rows').innerHTML = filtered.length ? filtered.map(rowHtml).join('') : '<div class="empty-state"><strong>暂无符合条件的文件</strong><p>调整搜索或筛选条件后重试。</p></div>';
  $('#list-count').textContent = `共 ${candidates.length} 份 · 当前显示 ${filtered.length} 份`;
  $('#footer-detail').innerHTML = sourceParent ? '<button class="link-button" data-action="clear-source">清除来源限定 ×</button>' : mode === 'parsed' ? '独立文件 · 来源可追溯' : '原件保留 · 文件信息可编辑';
}
function rowHtml(item) {
  const parent = item.parent ? items.find(x => x.id === item.parent) : null;
  const source = parent ? `来源：<button class="link-button" data-action="source" data-id="${item.id}">${escapeHtml(parent.originalName)}</button>${parent.deleted ? ' · 原件已移入回收站' : ''}<span class="file-path">${escapeHtml(item.path)}</span>` : `${escapeHtml(item.source)} · ${escapeHtml(item.source === '邮件附件' ? '专家来信' : item.actor)} · ${escapeHtml(item.date)}`;
  const count = activeChildren(item.id).length;
  const ext = item.originalName.split('.').pop().toUpperCase().slice(0, 5);
  return `<article class="file-row" data-file-id="${escapeHtml(item.id)}"><div class="file-icon ${item.archive ? 'archive' : ''}">${escapeHtml(ext)}</div><div class="file-main"><div class="file-name">${escapeHtml(item.name)}</div><div class="file-meta"><span class="status-badge">${escapeHtml(item.status)}</span><span>· ${escapeHtml(item.size)}</span>${mode === 'parsed' ? `<span>· ${escapeHtml(item.source)}</span>` : ''}</div><p class="file-note ${item.note ? '' : 'empty'}">${escapeHtml(item.note || '尚未填写内容说明')}</p><div class="tag-list">${item.tags.length ? item.tags.map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join('') : '<span class="field-hint">未添加标签</span>'}</div><div class="source-line">${source}</div></div><div class="file-actions"><span class="stored">${item.localOnly ? '本机预览' : '已存储 · 示例'}</span><button class="link-button" data-action="edit" data-id="${item.id}">编辑信息</button>${mode === 'original' && count ? `<button class="link-button" data-action="children" data-id="${item.id}">${item.archive ? '已解压' : '已整理'} ${count} 份 ↗</button>` : mode === 'original' ? '<span class="field-hint">待解析</span>' : `<button class="link-button" data-action="source" data-id="${item.id}">查看来源</button>`}<button class="link-button delete-action" data-action="delete" data-id="${item.id}">删除</button></div></article>`;
}
function openEditor(id) {
  const item = items.find(x => x.id === id); if (!item || item.deleted) return;
  editingId = id; $('#editor-eyebrow').textContent = item.mode === 'original' ? '原始文件' : '解析后文件';
  $('#editor-source').textContent = `原始文件名：${item.originalName}\n${item.size} · ${item.source}`;
  $('#edit-name').value = item.name; $('#edit-note').value = item.note; $('#edit-tags').value = item.tags.join('，'); $('#edit-status').value = item.status; $('#editor').showModal();
}
function openDelete(id) {
  const item = items.find(x => x.id === id); if (!item || item.deleted) return;
  deletingId = id; const children = activeChildren(id);
  $('#delete-name').textContent = item.name;
  $('#cascade').checked = false; $('#cascade-label').hidden = !children.length;
  $('#delete-warning').textContent = item.mode === 'parsed' ? '仅删除这份解析后文件，原始文件及其他解析文件保留。' : children.length ? `该原件关联 ${children.length} 份解析后文件。默认保留解析文件及其来源记录。` : '仅删除此原始文件。';
  $('#delete-dialog').showModal();
}
function showSource(id) {
  const item = items.find(x => x.id === id), parent = items.find(x => x.id === item?.parent); if (!parent) return;
  $('#info-title').textContent = '文件来源';
  $('#info-body').innerHTML = `<div class="provenance"><div><small>原始文件${parent.deleted ? ' · 已移入回收站' : ''}</small><strong>${escapeHtml(parent.originalName)}</strong><br>${escapeHtml(parent.source)} · ${escapeHtml(parent.date)}</div><div><small>${parent.source === '邮件附件' ? '来源邮件' : '上传人'}</small>${escapeHtml(parent.actor)}</div><div><small>${item.source} · ${escapeHtml(item.date)}</small>${escapeHtml(item.path)}</div><div><small>当前独立文件</small><strong>${escapeHtml(item.name)}</strong> · ${escapeHtml(item.size)}</div></div><button class="button" data-action="locate" data-id="${parent.id}">定位原始文件</button><p class="field-hint">来源路径和解压内容为交互示例，未读取实际文件。</p>`;
  $('#info-dialog').showModal();
}
function showTrash() {
  $('#info-title').textContent = '回收站'; const removed = items.filter(x => x.deleted);
  $('#info-body').innerHTML = '<p class="field-hint">恢复原件不会自动恢复已删除的解析文件；可分别恢复。</p>' + (removed.length ? removed.map(x => `<div class="trash-item"><div>${escapeHtml(x.name)}<small>${x.mode === 'original' ? '原始文件' : '解析后文件'}</small></div><button class="button" data-action="restore" data-id="${x.id}">恢复</button></div>`).join('') : '<div class="empty-state">回收站为空</div>');
  if (!$('#info-dialog').open) $('#info-dialog').showModal();
}
document.addEventListener('click', event => {
  const close = event.target.closest('[data-close]'); if (close) { close.closest('dialog').close(); return; }
  const button = event.target.closest('[data-action]'); if (!button) return;
  const {action, id} = button.dataset;
  if (action === 'edit') openEditor(id);
  if (action === 'delete') openDelete(id);
  if (action === 'children') setMode('parsed', id);
  if (action === 'source') showSource(id);
  if (action === 'clear-source') { sourceParent = ''; render(); }
  if (action === 'restore') { const item = items.find(x => x.id === id); if (item) { item.deleted = false; persist(); render(); showTrash(); notify('文件已恢复。'); } }
  if (action === 'locate') {
    const parent = items.find(x => x.id === id); $('#info-dialog').close();
    if (parent?.deleted) { showTrash(); return; }
    setMode('original'); const row = document.querySelector(`[data-file-id="${id}"]`); row?.classList.add('highlight'); row?.scrollIntoView({block:'nearest',behavior:'smooth'});
  }
});
document.querySelectorAll('[data-mode]').forEach(button => {
  button.addEventListener('click', () => setMode(button.dataset.mode));
  button.addEventListener('keydown', event => { if (['ArrowLeft','ArrowRight'].includes(event.key)) { event.preventDefault(); setMode(mode === 'original' ? 'parsed' : 'original'); $('#tab-' + mode).focus(); } });
});
$('#editor-form').addEventListener('submit', event => {
  event.preventDefault(); const item = items.find(x => x.id === editingId); const name = $('#edit-name').value.trim(); if (!name) { $('#edit-name').setCustomValidity('请输入显示名称'); $('#edit-name').reportValidity(); return; }
  Object.assign(item, {name,note:$('#edit-note').value.trim(),tags:tagsFrom($('#edit-tags').value),status:$('#edit-status').value}); persist(); render(); $('#editor').close(); notify('文件信息已保存到本机预览。');
});
$('#edit-name').addEventListener('input', () => $('#edit-name').setCustomValidity(''));
$('#delete-form').addEventListener('submit', event => { event.preventDefault(); const item = items.find(x => x.id === deletingId); item.deleted = true; let count = 1; if ($('#cascade').checked) activeChildren(item.id).forEach(child => { child.deleted = true; count++; }); persist(); render(); $('#delete-dialog').close(); notify(`已将 ${count} 份文件移入回收站，可恢复。`); });
$('#search').addEventListener('input', render);
$('#tag-filter').addEventListener('change', render);
$('#source-filter').addEventListener('change', render);
$('#trash').addEventListener('click', showTrash);
$('#theme').addEventListener('click', () => { document.documentElement.dataset.consoleTheme = document.documentElement.dataset.consoleTheme === 'dark' ? 'light' : 'dark'; });
$('#reset').addEventListener('click', () => { items = structuredClone(seed); persist(); setMode('original'); notify('已恢复初始演示数据。'); });
function renderUploadFiles() { $('#upload-files').innerHTML = pendingFiles.map(file => `<div class="upload-item"><span>${escapeHtml(file.name)}</span><small>${formatSize(file.size)}</small></div>`).join(''); $('#upload-error').textContent = ''; }
function formatSize(bytes) { return bytes >= 1048576 ? (bytes / 1048576).toFixed(1) + ' MB' : bytes >= 1024 ? (bytes / 1024).toFixed(1) + ' KB' : bytes + ' B'; }
$('#upload').addEventListener('click', () => { pendingFiles = []; $('#upload-form').reset(); renderUploadFiles(); $('#upload-dialog').showModal(); });
$('#upload-input').addEventListener('change', event => { pendingFiles = Array.from(event.target.files); renderUploadFiles(); });
['dragenter','dragover'].forEach(name => $('#dropzone').addEventListener(name, event => { event.preventDefault(); $('#dropzone').classList.add('dragover'); }));
$('#dropzone').addEventListener('dragleave', () => $('#dropzone').classList.remove('dragover'));
$('#dropzone').addEventListener('drop', event => { event.preventDefault(); $('#dropzone').classList.remove('dragover'); pendingFiles = Array.from(event.dataTransfer.files); renderUploadFiles(); });
$('#upload-form').addEventListener('submit', event => {
  event.preventDefault(); if (!pendingFiles.length) { $('#upload-error').textContent = '请先选择至少一个文件。'; return; }
  pendingFiles.forEach(file => items.unshift({id:'u-' + crypto.randomUUID(),mode:'original',name:file.name,originalName:file.name,size:formatSize(file.size),note:$('#upload-note').value.trim(),tags:tagsFrom($('#upload-tags').value),source:'手动上传',actor:'本机预览',date:new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',dateStyle:'short',timeStyle:'short'}).format(new Date()),status:'待审核',archive:/\.(zip|rar|7z|tar|gz)$/i.test(file.name),localOnly:true}));
  persist(); setMode('original'); $('#upload-dialog').close(); notify('已添加文件信息；预览未上传文件内容或执行解压。'); pendingFiles = [];
});
sourceOptions(); render();
