from pathlib import Path
import re
p=Path(__file__).parent
src=(p/'production-index.snapshot.html').read_text()
header=re.search(r'<header class="topnav[\s\S]*?</header>',src).group()
header=header[:header.index('</nav>')+6]+'<span class="rp-preview">功能预览 · 不操作线上数据</span></header>'
header=header.replace('nav-tab active','nav-tab').replace('class="nav-tab" data-view="contacts"','class="nav-tab active" data-view="contacts"')
header=re.sub(r' onclick="[^"]*"','',header)
pane=re.search(r'<section id="discoveryPapersPane"[\s\S]*?</section>',src).group().replace(' hidden>','>',1)
pane=pane.replace('本功能启用后开始留存；历史抽样未自动迁入。','留存原文与解析记录；支持按采集任务重新解析并补录。')
pane=pane.replace('<option value="">全部任务</option>','<option value="30711">执行 #30711 · 2026-11-01</option><option value="">全部任务</option>')
pane=pane.replace('正在读取…','存储统计沿用线上组件；预览未连接实时数据。').replace('正在读取留存设置…','原文留存设置沿用线上配置。本页仅演示新入口，不会下载、删除或修改论文。')
pane=pane.replace('未知','—').replace('尚未获取快照','实时磁盘数据未接入预览')
pane=pane.replace('  <div class="dp-selection">', '''
  <div class="rp-batch"><div><strong>重新处理整个采集任务</strong><p>执行 #30711 · 3,185 篇有留存原文的论文 <span class="rp-muted">（11 月 1 日核对快照）</span></p><p class="rp-muted">覆盖本任务全部页；包含当初未建档的作者。上方其他筛选和勾选不缩小处理范围。</p></div><button class="button primary" id="rp-open">重新解析并补录本批</button></div>
  <div id="rp-result" hidden></div>
  <div class="dp-selection">''')
pane=pane.replace('<tbody data-dp="rows"></tbody>', '<tbody data-dp="rows">'+''.join('''<tr><td><input type="checkbox" disabled></td><td><span class="dp-title">'''+title+'''</span><span class="dp-sub">原文记录 #'''+str(rid)+'''</span><span class="dp-badge dp-ok">已留存</span></td><td>PDF<span class="dp-sub">大小未接入</span></td><td>执行 #30711<span class="dp-sub">2026-11-01 · 留档样本</span></td><td>存在未关联邮箱<span class="dp-sub">已确认至少 1 组漏关联</span></td><td>待重新提取</td><td><button class="dp-link rp-evidence">查看核对说明</button></td></tr>''' for rid,title in [(32137,'300 GHz wave with attosecond-level timing noise'),(32822,'Quantum Computing with Rotation-Symmetric Bosonic Codes'),(35208,'Is there a supernova bound on axions?')])+'</tbody>')
pane=pane.replace('正在加载…','仅展示 3 篇已核对样本；整批范围不受当前页限制。')
css=(p/'preview.css').read_text();js=(p/'preview.js').read_text()
page='''<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'; base-uri 'none'"><title>深度发现 · 本批重新解析与补录 · 预览</title><style>'''+(p/'production-styles.snapshot.css').read_text()+css+'''</style></head><body><div class="app-shell">'''+header+'''<main class="rp-background"><h1>专家列表</h1><p>查看联系状态、邮件时间线和人工处理。</p><button class="button primary" id="rp-reopen">深度发现</button></main></div><div id="taskProgressModal" class="modal-overlay" data-discovery-review="true"><div class="modal-content task-modal"><div class="modal-header"><h3>深度发现（外部数据源）</h3><button class="button small" id="rp-close-main" aria-label="关闭深度发现">×</button></div><nav id="discoveryReviewTabs" class="batch-send-tabs dp-tabbar"><button class="batch-send-tab" disabled>采集运行</button><button class="batch-send-tab" disabled>专家审核</button><button class="batch-send-tab" disabled>审核记录</button><button class="batch-send-tab is-active" aria-current="page">论文原文</button></nav><div class="rp-notice"><span>前端预览 · 复用当前线上 HTML / CSS；新增功能未上线，执行结果为演示。</span><button class="dp-link" id="rp-demo">查看执行日志演示</button></div>'''+pane+'''</div></div>
<dialog id="rp-confirm" class="dp-dialog rp-dialog" aria-labelledby="rp-title"><div class="dp-dialog-head"><div><h3 id="rp-title">重新解析并补录本批</h3><p class="dp-muted">使用最新规则，重新处理此任务的全部留存原文。</p></div><button class="button small" data-close="rp-confirm" aria-label="关闭确认">×</button></div><div class="dp-dialog-body">
<div class="rp-scope"><span>采集任务</span><strong>执行 #30711</strong><span>处理范围</span><strong>全部 3,185 篇有原文的论文 · 跨所有页</strong><span>名单确定</span><span>执行前重新统计并固定范围；原文缺失单列</span></div>
<h4 class="rp-label">执行模式</h4><label class="rp-option"><input type="radio" name="rp-mode" value="compare"><span><strong>仅对照解析</strong><small>保存新旧关联差异，不修改专家档案和准入结果。</small></span></label><label class="rp-option"><input type="radio" name="rp-mode" value="apply" checked><span><strong>补录并重新审核</strong><small>已有档案补齐明确证据；未建档作者去重补录，再进入统一审核。</small></span></label>
<div class="rp-process"><span>① 读取原文</span><b>→</b><span>② 重新解析</span><b>→</b><span>③ 确认关联</span><b>→</b><span>④ 去重补录</span><b>→</b><span>⑤ 重新审核</span><b>→</b><span>⑥ 保存结果</span></div>
<p class="dp-warning">仅采用明确且唯一的关联。人工修改与联系保护继续有效；冲突保留待处理。补齐身份不等于自动准入。</p><p class="dp-muted">审核沿用已保存的 Apollo 免费 / 付费配置。关闭页面不取消后台任务，可从执行记录恢复查看。</p><p class="rp-preview-note">当前为交互预览：确认只播放示例结果，不调用真实接口。</p></div><div class="dp-dialog-foot"><span>本批全部页 · 不依赖勾选</span><div class="dp-actions"><button class="button secondary" data-close="rp-confirm">取消</button><button class="button primary" id="rp-start">确认执行（演示）</button></div></div></dialog>
<dialog id="rp-detail" class="dp-dialog rp-dialog" aria-labelledby="rp-detail-title"><div class="dp-dialog-head"><div><h3 id="rp-detail-title">逐项处理记录</h3><p class="dp-muted">虚构示例 · 查看卡住步骤、原文证据和档案变化</p></div><button class="button small" data-close="rp-detail">关闭</button></div><div id="rp-detail-body" class="dp-dialog-body"></div></dialog><div id="rp-toast" role="status" hidden></div><script>'''+js+'''</script></body></html>'''
(p/'paper-reprocess-20261101.html').write_text(page)
print(len(page.encode()))
