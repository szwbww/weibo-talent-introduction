// Isolated UI fixture: no network requests and no production configuration writes.
(() => {
  'use strict';
  const $ = s => document.querySelector(s);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const accounts = {academic:'学术合作邮箱',industry:'企业合作邮箱'};
  const rows = [
    {id:1008,name:'Alex Chen',email:'alex.chen@example.org',sender:'academic',subject:'Research collaboration opportunity',sent:'2026-09-25 10:32',tracked:true,events:['2026-09-25 11:06','2026-09-25 13:42']},
    {id:1007,name:'Maya Lin',email:'maya.lin@example.org',sender:'academic',subject:'Introduction to our research program',sent:'2026-09-25 10:18',tracked:true,events:[]},
    {id:1006,name:'Daniel Wu',email:'daniel.wu@example.org',sender:'industry',subject:'An invitation to connect',sent:'2026-09-24 16:40',tracked:true,events:['2026-09-25 08:16']},
    {id:1005,name:'Sophie Zhang',email:'sophie.zhang@example.org',sender:'academic',subject:'Research collaboration opportunity',sent:'2026-09-24 14:12',tracked:true,events:[]},
    {id:1004,name:'Oliver Liu',email:'oliver.liu@example.org',sender:'industry',subject:'Introduction to our research program',sent:'2026-09-23 11:26',tracked:false,events:[]},
    {id:1003,name:'Emma Wang',email:'emma.wang@example.org',sender:'industry',subject:'An invitation to connect',sent:'2026-09-22 09:08',tracked:true,events:['2026-09-22 12:03']},
    {id:1002,name:'Leo Zhou',email:'leo.zhou@example.org',sender:'academic',subject:'Research collaboration opportunity',sent:'2026-09-20 15:45',tracked:true,events:[]},
    {id:1001,name:'Grace Sun',email:'grace.sun@example.org',sender:'academic',subject:'Introduction to our research program',sent:'2026-09-15 10:10',tracked:false,events:[]}
  ];
  let enabled = sessionStorage.getItem('open-tracking-preview-enabled') === 'true';
  let days = 7, page = 1, currentTab = 'tracking', detailTrigger;
  const pageSize = 5;
  const state = r => !r.tracked ? 'untracked' : r.events.length ? 'opened' : 'waiting';
  const labels = {untracked:'未启用跟踪',opened:'检测到打开信号',waiting:'暂无打开信号'};
  const badge = r => `<span class="badge ${state(r)==='opened'?'ok':state(r)==='waiting'?'info':''}">${labels[state(r)]}</span>`;
  function cohort() {
    const first = new Date(Date.UTC(2026,8,26-days)).toISOString().slice(0,10);
    return rows.filter(r => r.sent.slice(0,10)>=first && (!$('#senderFilter').value || r.sender===$('#senderFilter').value));
  }
  function render() {
    const data = cohort();
    const tracked = data.filter(r=>r.tracked).length;
    const opened = data.filter(r=>r.tracked && r.events.length).length;
    $('#metrics').innerHTML = [['已跟踪发送',tracked,'已成功发送的首封介绍邮件'],['有打开信号',opened,'每封邮件仅计一次'],['打开信号率',tracked?`${(opened/tracked*100).toFixed(1)}%`:'—','仅以已跟踪发送为分母']].map(([label,value,hint])=>`<div class="metric-card"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="ot-metric-hint">${hint}</div></div>`).join('');
    const search = $('#search').value.trim().toLowerCase();
    const filtered = data.filter(r=>(!$('#statusFilter').value || state(r)===$('#statusFilter').value) && (!search || [r.name,r.email,r.subject].some(s=>s.toLowerCase().includes(search))));
    const pages = Math.max(1,Math.ceil(filtered.length/pageSize));
    page = Math.min(page,pages);
    $('#trackingRows').innerHTML = filtered.slice((page-1)*pageSize,page*pageSize).map(r=>`<tr><td><strong>${esc(r.name)}</strong><div class="ot-secondary">${esc(r.email)}</div></td><td>${esc(r.subject)}<div class="ot-secondary">${accounts[r.sender]}</div></td><td class="ot-time">${r.sent}<div class="ot-secondary">发送成功</div></td><td>${badge(r)}</td><td class="ot-time">${r.events.length?`${r.events[0]}<div class="ot-secondary">最近 ${r.events.at(-1)}</div>`:'<span class="muted">—</span>'}</td><td><button class="ot-link" data-detail="${r.id}">查看详情</button></td></tr>`).join('') || '<tr><td colspan="6" class="ot-empty">暂无匹配邮件，可调整时间范围或筛选条件。</td></tr>';
    $('#countLabel').textContent = `${filtered.length} 封首封介绍邮件`;
    $('#pageInfo').textContent = `共 ${filtered.length} 条 · 第 ${page} / ${pages} 页`;
    $('#prevPage').disabled = page===1;
    $('#nextPage').disabled = page===pages;
    $('#trackingSettings').hidden = currentTab!=='tracking';
    $('#tableTitle').textContent = currentTab==='tracking'?'跟踪明细':'首发邮件';
    $('#simulate').disabled = !enabled || !data.some(r=>r.tracked && !r.events.length);
    $('#demoHint').textContent = !enabled?'开启开关后可体验信号更新':'仅更新示例邮件，不发送邮件';
  }
  function renderSwitch() {
    $('#trackingToggle').setAttribute('aria-checked',String(enabled));
    $('#switchStatus').textContent = $('#switchLabel').textContent = enabled?'已开启':'已关闭';
    $('#switchStatus').className = `badge ${enabled?'ok':''}`;
    $('#stateNote').classList.toggle('enabled',enabled);
    $('#stateNote').textContent = enabled?'跟踪已开启：后续首封介绍邮件将加入跟踪图片。所有回复及跟进邮件始终排除。':'跟踪已关闭：新邮件不加入跟踪图片，旧邮件停止新增信号，已有历史记录仍可查看。';
    render();
  }
  function detail(id,button) {
    const r = rows.find(r=>r.id===Number(id));
    detailTrigger=button;
    $('#detailTitle').textContent = `${r.name} · 跟踪详情`;
    $('#detailBody').innerHTML = `<dl class="ot-detail-grid"><dt>收件邮箱</dt><dd>${esc(r.email)}</dd><dt>邮件主题</dt><dd>${esc(r.subject)}</dd><dt>发件账号</dt><dd>${accounts[r.sender]}</dd><dt>发送时间</dt><dd>${r.sent}</dd><dt>跟踪状态</dt><dd>${badge(r)}</dd><dt>跟踪范围</dt><dd>仅此封介绍邮件；后续任何回复均不添加。</dd></dl><ul class="ot-timeline"><li><span class="ot-secondary">${r.sent}</span><br>首封介绍邮件发送成功 · ${r.tracked?'已加入跟踪图片':'未启用跟踪'}</li>${r.events.map((t,i)=>`<li><span class="ot-secondary">${t}</span><br><strong>${i===0?'首次检测到打开信号':'再次检测到打开信号'}</strong></li>`).join('')}${r.tracked&&!r.events.length?'<li>暂无打开信号，不能据此认定对方未读。</li>':''}</ul><div class="ot-dialog-note">${!r.tracked?'此封邮件发送时未启用跟踪，无法补追踪。':!enabled?'当前总开关已关闭，历史信号保留，不再新增记录。':'当前可记录此封邮件的后续打开信号。'}<br>图片加载可能来自预加载或转发，不能证明收件人本人阅读。</div>`;
    $('#detailDialog').showModal();
  }
  document.querySelectorAll('.nav-tab[data-view]').forEach(b=>{
    b.classList.toggle('active',b.dataset.view==='monitoring');
    b.disabled=b.dataset.view!=='monitoring';
    if(b.disabled)b.title='本预览聚焦邮件监控';
  });
  $('#viewTitle').textContent='邮件监控';
  $('#viewSubtitle').textContent='查看邮件发送、回复与首封介绍邮件的打开信号。';
  $('#currentUserDisplay').textContent='预览';
  $('#logoutBtn').hidden=true;
  $('#trackingToggle').onclick=()=>{enabled=!enabled;sessionStorage.setItem('open-tracking-preview-enabled',String(enabled));renderSwitch();$('#toast').textContent=enabled?'预览开关已开启':'预览开关已关闭，历史保留';};
  $('#rangeTabs').onclick=e=>{const b=e.target.closest('[data-days]');if(!b)return;days=Number(b.dataset.days);page=1;document.querySelectorAll('[data-days]').forEach(x=>x.classList.toggle('active',x===b));render();};
  $('#subTabs').onclick=e=>{const b=e.target.closest('[data-tab]');if(!b)return;currentTab=b.dataset.tab;document.querySelectorAll('[data-tab]').forEach(x=>x.classList.toggle('active',x===b));render();};
  for(const id of ['senderFilter','statusFilter'])$('#'+id).onchange=()=>{page=1;render();};
  $('#search').oninput=()=>{page=1;render();};
  $('#resetFilters').onclick=()=>{$('#senderFilter').value='';$('#statusFilter').value='';$('#search').value='';page=1;render();};
  $('#prevPage').onclick=()=>{page--;render();};
  $('#nextPage').onclick=()=>{page++;render();};
  $('#trackingRows').onclick=e=>{const b=e.target.closest('[data-detail]');if(b)detail(b.dataset.detail,b);};
  $('#closeDetail').onclick=()=>$('#detailDialog').close();
  $('#detailDialog').addEventListener('close',()=>detailTrigger?.focus());
  $('#detailDialog').onclick=e=>{if(e.target===$('#detailDialog')){const r=e.target.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)e.target.close();}};
  $('#simulate').onclick=()=>{if(!enabled)return;const r=cohort().find(r=>r.tracked&&!r.events.length);if(!r)return;r.events.push('2026-09-25 14:30');render();$('#toast').textContent=`${r.name}：已新增一条示例信号`;};
  $('#refreshBtn').onclick=()=>{render();$('#toast').textContent='示例数据已刷新';};
  renderSwitch();
})();
