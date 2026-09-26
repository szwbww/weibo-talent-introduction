// Demonstration fixtures only; no requests, schedulers or business writes.
const q=s=>document.querySelector(s);
const runs=[
 {id:12846,name:'检查回复',source:'定时触发',time:'14:30:00',elapsed:'02:18',pct:68,stage:'正在检查邮箱 · 第 17 / 25 个账号',done:'已检查 17 / 25',extra:'发现新回复 6 封'},
 {id:12845,name:'补充学术数据',source:'后台队列',time:'14:28:32',elapsed:'03:46',pct:42,stage:'正在补全专家学术信息',done:'已处理 126 / 300',extra:'成功 124 · 待重试 2'},
 {id:12844,name:'批量首发邮件',source:'定时触发',time:'14:25:00',elapsed:'07:18',pct:60,stage:'正在发送 · 第 3 / 5 轮',done:'已发送 36 / 60',extra:'本轮剩余 4 封'}
];
const history=[
 [12843,'检查回复','定时触发','执行成功','25 个账号 · 发现回复 3 封','14:20:00','01:46'],
 [12842,'补充学术数据','后台队列','部分成功','成功 98 · 失败 2','14:18:10','03:12'],
 [12841,'深度发现','手动触发','执行失败','外部数据源请求超时','14:15:00','00:32'],
 [12840,'批量首发邮件','定时触发','执行成功','已发送 60 封','14:00:00','08:24'],
 [12839,'检查回复','定时触发','执行成功','25 个账号 · 发现回复 4 封','13:50:00','01:58']
];
q('[data-view="tasks"]').insertAdjacentHTML('beforeend','<span class="tc-pill tc-nav-count"><i class="tc-dot"></i>3</span>');
q('.topbar-actions').insertAdjacentHTML('afterbegin','<button class="tc-pill tc-global" id="tcGlobal"><i class="tc-dot"></i>3 个任务执行中 <span>↗</span></button>');
q('#view-tasks').innerHTML=`
 <div class="tc-section"><h2>正在执行 <span class="tc-count">3</span></h2><span class="tc-note">后台运行，不影响当前操作 · 每 5 秒更新</span></div>
 <div class="tc-grid">${runs.map(r=>`<article class="tc-card"><div class="tc-card-top"><span class="tc-source">${r.source} · <span class="tc-id">#${r.id}</span></span><span class="tc-pill"><i class="tc-dot"></i>执行中</span></div><h3>${r.name}</h3><span class="tc-source">开始于 ${r.time} · 已运行 ${r.elapsed}</span><p>${r.stage}</p><div class="tc-track"><span class="tc-fill" style="width:${r.pct}%"></span></div><div class="tc-progress"><span>${r.done}</span><strong>${r.pct}%</strong></div><div class="tc-card-footer"><span>${r.extra}</span><button class="tc-link" data-detail="${r.id}">查看详情 →</button></div></article>`).join('')}</div>
 <section class="tc-detail" id="tcDetail" hidden></section>
 <section class="panel tc-history"><div class="panel-head"><h2>执行记录</h2><span class="tc-note">保留每次执行结果与日志</span></div><div class="toolbar"><select id="tcType"><option>全部任务类型</option>${[...new Set(history.map(x=>x[1]))].map(x=>`<option>${x}</option>`).join('')}</select><select id="tcStatus"><option>全部执行状态</option><option>执行成功</option><option>部分成功</option><option>执行失败</option></select><button class="button primary small" id="tcFilter">查询</button><span class="tc-note">2026-09-22</span></div><div class="table-wrap"><table><thead><tr><th>执行 ID</th><th>任务类型</th><th>触发方式</th><th>执行状态</th><th>执行结果</th><th>开始时间</th><th>耗时</th><th>操作</th></tr></thead><tbody id="tcRows"></tbody></table></div><div class="list-pager"><span class="tc-note" id="tcTotal">共 5 条</span><button class="button small" disabled>上一页</button><span class="list-pager-info">第 1 页</span><button class="button small" disabled>下一页</button></div></section>`;
function renderHistory(){const data=history.filter(r=>(q('#tcType').selectedIndex===0||q('#tcType').value===r[1])&&(q('#tcStatus').selectedIndex===0||q('#tcStatus').value===r[3]));q('#tcRows').innerHTML=data.map(r=>`<tr><td class="tc-id">#${r[0]}</td><td>${r[1]}</td><td class="tc-note">${r[2]}</td><td><span class="tc-state ${r[3]==='执行失败'?'fail':r[3]==='部分成功'?'partial':''}">${r[3]}</span></td><td class="tc-result">${r[4]}</td><td class="tc-note">${r[5]}</td><td class="tc-note">${r[6]}</td><td><button class="tc-link" data-history="${r[0]}">查看记录</button></td></tr>`).join('');q('#tcTotal').textContent=`共 ${data.length} 条`;}
function showView(view){document.querySelectorAll('.view').forEach(x=>x.classList.toggle('active',x.id==='view-'+view));document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x.dataset.view===view));const tab=q('[data-view="'+view+'"]');q('#viewTitle').textContent=tab?.querySelector('span')?.textContent||'任务记录';q('#viewSubtitle').textContent=view==='tasks'?'统一查看后台任务进度、执行结果和日志。':'维护专家信息，后台任务持续执行。';}
document.querySelectorAll('[data-view]').forEach(btn=>btn.onclick=()=>showView(btn.dataset.view));q('#tcGlobal').onclick=()=>showView('tasks');q('#tcFilter').onclick=renderHistory;q('#refreshBtn').onclick=()=>renderHistory();
q('#view-tasks').addEventListener('click',e=>{const btn=e.target.closest('[data-detail],[data-history]');if(!btn)return;const r=runs.find(x=>String(x.id)===btn.dataset.detail);const h=history.find(x=>String(x[0])===btn.dataset.history);q('#tcDetail').hidden=false;q('#tcDetail').innerHTML=`<header><h2>${r?r.name:h[1]} · #${r?r.id:h[0]}</h2><button class="tc-link" id="tcClose">收起详情 ↑</button></header><div class="tc-log"><div><span>${r?r.time:h[5]}</span>任务开始 · ${r?r.source:h[2]}</div><div><span>${r?'14:32:18':h[5]}</span>${r?r.stage:h[4]}</div></div>`;q('#tcClose').onclick=()=>q('#tcDetail').hidden=true;});
q('#logoutBtn').hidden=true;
q('#currentUserDisplay').textContent='预览';
document.body.insertAdjacentHTML('beforeend','<div class="tc-preview-label">交互预览 · 演示任务数据 · 未连接生产操作</div>');
renderHistory();showView('tasks');
