# 05 — 真实深度发现页面的论文原文管理

状态：待执行。依赖01～04。设计基线为已展示的线上隔离预览与实际业务弹窗；不得复制预览的虚构数据或静态硬编码磁盘数。

## 需求描述

在现有深度发现弹窗加“论文原文”页签，提供容量、查询、原文预览/下载、解析对照、职称/学位原句、批量保护/取消保护/删除；采集页可跳转本次任务原文。刷新可读已保存配置与保护状态。

必须保持：1180px/80vh 的既有深度发现弹窗；采集运行/专家审核/审核记录原功能；其他任务弹窗和批量发信页样式不变。范围外：全站重设计、重新实现 PDF 阅读器、生产页面演示数据、自动重解析全库、整批打包下载。

## 关键不变量

### Invariant I-1: 展示真实持久结果
- Rule: 统计只来自01 API；null显示“未知/未分析”，不能0填充。磁盘容量标GiB/MiB、不是内存；显示measuredAt。原文数、下载尝试、邮箱数、关联数、准入数不混用。只显示新归档库，明确“本功能启用后开始留存；历史抽样未自动迁入”。
- Applies to: 页面、采集摘要、筛选和预览。
- Violation consequence: 用户误以为已经全量保存或多准入了专家。
- 来源: K-admission-event-and-projection-counts。

### Invariant I-2: 保护、选择与删除独立
- Rule: 已保护记录可被勾选和全选，用于取消保护；是否可删除由后端预检查决定。选择只覆盖当前页；换筛选/分页清空选择。取消保护成功后清空选择，不触发删除。删除必须显示服务端预检查，再提交snapshotHash，409重开确认，不能自动代用户重试删除。
- Applies to: 表格、工具栏、确认弹窗、错误恢复。
- Violation consequence: 保护样本无法解锁或被隐式删除。
- 来源: original，已确认预览交互。

### Invariant I-3: 页面生命周期与权限
- Rule: 初次进papers才读列表；离开/关闭中止请求和计时器，过期响应不能写入新任务。下载URL由宿主注入contextPath构造；所有姓名、原句、XML、错误内容用textContent/escapeHtml，不执行原文HTML。控件loading/disabled/错误状态可操作恢复。
- Applies to: app.js注册、新模块、预览、下载。
- Violation consequence: 串任务、旧响应覆盖、路径404或脚本执行。
- 来源: K-download-context-path-host-injection；K-dom-stub-tests-hide-dangling-refs。

### Invariant I-4: 证据与准入保持边界
- Rule: 固定文案“论文发表时的本人原句，不等于当前资格或自动准入结论”。分析只在点击“解析对照”时POST；预览不自动产生分析/付费调用；删除后仍显示旧分析，原文入口禁用。
- Applies to: 详情和统计。
- Violation consequence: 点开论文触发后台写专家或付费。
- 来源: K-paper-affiliation-is-not-employment。

## 样式契约

### S-1: 既有容器与页签
- 复用：`styles.css:4494` modal-content（18px圆角/80vh/纵向滚动），`:4525` modal-body；`:12717`深度发现1180px、width calc(100vw - 48px)，≤640px为calc(100vw - 20px)；`:9150–9182` batch-send-tabs/tab/is-active；`:802–870` button/primary/secondary/danger，`:2482` small。
- 既有 class 全局定义不修改；新样式只在新增dp-*或精确宿主下派生。全局引用文件清单已保存在代码证据“复用 CSS 类的使用文件”段；它们包括其他任务/邮件，均不应产生diff。只给现有nav增加dp-tabbar、加第4按钮；三个现有按钮顺序不变。
- DOM逐字骨架（放现有discoveryReviewTabs末尾；section作为现有三个pane的兄弟，dialog置于taskProgressModal内，不能把dialog放table内）：

```html
<button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryPapersPane" id="discoveryPapersTab" tabindex="-1">论文原文</button>
<section id="discoveryPapersPane" class="modal-body dp-root" role="tabpanel" aria-labelledby="discoveryPapersTab" hidden>
  <div class="dp-heading"><div><h4>论文原文</h4><p class="dp-muted">本功能启用后开始留存；历史抽样未自动迁入。</p></div><button class="button secondary small" type="button" data-dp-action="settings">留存设置</button></div>
  <div class="dp-storage">
    <div class="dp-storage-main"><span>原文库占用（磁盘）</span><strong data-dp="archiveBytes">未知</strong><p class="dp-muted" data-dp="archiveCounts">正在读取…</p></div>
    <div class="dp-disk"><div class="dp-between"><span>所在磁盘总容量 <b data-dp="diskTotal">未知</b></span><span>可用 <b data-dp="diskAvailable">未知</b></span></div><progress class="dp-meter" data-dp="diskMeter" aria-label="所在磁盘已用空间" max="1" value="0"></progress><p class="dp-muted" data-dp="diskBreakdown">未知</p><p class="dp-muted" data-dp="measuredAt">尚未获取快照</p></div>
  </div>
  <div class="dp-policy" role="status" data-dp="policy">正在读取留存设置…</div>
  <div class="dp-error" role="alert" data-dp="error" hidden></div>
  <div class="dp-filters">
    <label class="dp-search">论文标题 / DOI<input type="search" data-dp="q" maxlength="200" placeholder="输入标题或 DOI"></label>
    <label>执行任务<select data-dp="execution"><option value="">全部任务</option></select></label>
    <label>原文状态<select data-dp="status"><option value="">全部状态</option><option value="STORED">已留存</option><option value="NOT_STORED">未留存</option><option value="SAVE_ERROR">保存异常</option><option value="DELETED">已删除</option></select></label>
    <label>保护状态<select data-dp="protected"><option value="">全部</option><option value="true">已保护</option><option value="false">未保护</option></select></label>
    <label>解析对照<select data-dp="gain"><option value="">全部</option><option value="true">有新增关联</option></select></label>
  </div>
  <div class="dp-selection"><span data-dp="selection">已选 0 篇</span><div class="dp-actions"><button class="button secondary small" type="button" data-dp-action="protect" disabled>批量保护</button><button class="button secondary small" type="button" data-dp-action="unprotect" disabled>批量取消保护</button><button class="button danger small" type="button" data-dp-action="delete" disabled>批量删除原文</button></div></div>
  <div class="dp-table-wrap"><table class="dp-table"><thead><tr><th><input type="checkbox" data-dp="selectPage" aria-label="选择本页论文"></th><th>论文 / 原文状态</th><th>来源 / 大小</th><th>执行记录</th><th>邮箱 / 姓名关联</th><th>职称 / 学位证据</th><th>操作</th></tr></thead><tbody data-dp="rows"></tbody></table></div>
  <div class="dp-footer"><span data-dp="listInfo">正在加载…</span><div class="dp-actions"><button class="button secondary small" type="button" data-dp-action="previous" disabled>上一页</button><span data-dp="pageInfo">第 1 页</span><button class="button secondary small" type="button" data-dp-action="next" disabled>下一页</button></div></div>
</section>
```

采集页在执行记录摘要的末尾新增固定宿主（不是修改已有“晋升”列）：

```html
<div class="dp-run-summary" data-dp="runSummary" hidden><span data-dp="runCounts"></span><button class="dp-link" type="button" data-dp-action="open-run-papers">查看本次原文</button></div>
```

### S-2: 行、空态和原文详情

动态行必须使用下列骨架；文本为API值，`data-dp-id`值为record ID。没有正文的预览/下载disabled，但“查看记录”仍可打开详情。标签class只允许dp-badge、dp-ok、dp-warn、dp-protected；被选行加is-selected。未知不是绿色。

```html
<tr data-dp-id="1"><td><input type="checkbox" aria-label="选择论文" data-dp-action="select"></td><td><span class="dp-title"></span><span class="dp-sub"></span><span class="dp-badge"></span><span class="dp-badge dp-protected" hidden>已保护</span></td><td><span data-dp="source"></span><span class="dp-sub" data-dp="bytes"></span></td><td><span data-dp="executionLabel"></span><span class="dp-sub" data-dp="createdAt"></span></td><td><span data-dp="bindings"></span><span class="dp-sub" data-dp="delta"></span></td><td><span data-dp="facts"></span></td><td><div class="dp-actions"><button class="dp-link" type="button" data-dp-action="view">查看记录</button><button class="dp-link" type="button" data-dp-action="download">下载</button></div></td></tr>
<tr><td class="dp-empty" colspan="7">暂无论文原文记录</td></tr>
```

详情骨架：

```html
<dialog id="discoveryPaperReader" class="dp-dialog dp-reader" aria-labelledby="dpReaderTitle">
  <div class="dp-dialog-head"><div><h3 id="dpReaderTitle">论文原文</h3><p class="dp-muted" data-dp="readerSubtitle"></p></div><button class="button small" type="button" data-dp-action="close-reader" aria-label="关闭原文">×</button></div>
  <div class="dp-reader-tools"><label class="dp-inline-label">原文文件<select data-dp="attempts"></select></label><div class="dp-actions"><button class="button secondary small" type="button" data-dp-action="analyze">解析对照</button><button class="button secondary small" type="button" data-dp-action="reader-download">下载原文</button></div></div>
  <div class="dp-reader-grid"><div class="dp-original"><iframe class="dp-pdf" title="已留存的 PDF 原文" data-dp="pdf" hidden></iframe><pre class="dp-text" data-dp="text" hidden></pre><p class="dp-empty" data-dp="originalMessage">请选择已保存原文；浏览器无法内嵌时可下载查看。</p></div><div class="dp-evidence">
    <div class="dp-evidence-tabs" role="tablist" aria-label="论文解析记录"><button type="button" class="is-active" role="tab" aria-selected="true" aria-controls="dpMappings" id="dpMappingsTab" data-dp-action="mappings">邮箱 / 姓名关联</button><button type="button" role="tab" aria-selected="false" aria-controls="dpQualifications" id="dpQualificationsTab" tabindex="-1" data-dp-action="qualifications">职称 / 学位原句</button></div>
    <div class="dp-error" role="alert" data-dp="analysisError" hidden></div>
    <section id="dpMappings" role="tabpanel" aria-labelledby="dpMappingsTab"><p class="dp-muted" data-dp="comparisonSummary">尚未分析</p><div data-dp="mappingCards"></div></section>
    <section id="dpQualifications" role="tabpanel" aria-labelledby="dpQualificationsTab" hidden><p class="dp-warning">论文发表时的本人原句，不等于当前资格或自动准入结论。</p><p class="dp-muted" data-dp="coverage"></p><div data-dp="qualificationCards"></div></section>
  </div></div>
  <div class="dp-dialog-foot"><span class="dp-muted" data-dp="readerFoot"></span><button class="button secondary small" type="button" data-dp-action="close-reader">关闭</button></div>
</dialog>
```

邮箱关联/资格卡片使用同一模板，kind标签分别显示，原句textContent；资格卡片footer年份未知写“论文年份未记录”：

```html
<article class="dp-evidence-card"><h4></h4><p class="dp-muted" data-dp="personEmail"></p><div class="dp-actions"><span class="dp-badge" data-dp="evidenceKind"></span></div><blockquote></blockquote><footer><span data-dp="evidenceLocator"></span><button class="dp-link" type="button" data-dp-action="locate">定位原句</button></footer></article>
```

### S-3: 设置与删除确认

使用原生dialog.showModal/close，dialog.cancel中只关闭当前详情/确认层并阻止冒泡到任务弹窗，Esc关闭、焦点返回触发按钮；不调用全局alert/confirm。保存失败不关闭、不丢输入。保护/取消保护无需额外弹窗，结果显示当前页status。

```html
<dialog id="discoveryPaperSettings" class="dp-dialog dp-confirm" aria-labelledby="dpSettingsTitle"><div class="dp-dialog-head"><h3 id="dpSettingsTitle">论文原文留存设置</h3><button class="button small" type="button" data-dp-action="close-settings" aria-label="关闭设置">×</button></div><form data-dp="settingsForm"><div class="dp-dialog-body"><label class="dp-check"><input type="checkbox" data-dp="enabled">保存实际下载的完整论文原文</label><label class="dp-setting-field">原文库上限（GiB）<input type="number" min="1" max="1024" step="1" required data-dp="maxGiB"></label><label class="dp-setting-field">磁盘最低可用空间（GiB）<input type="number" min="1" max="1024" step="1" required data-dp="minFreeGiB"></label><p class="dp-warning">达到阈值会阻断新的全文下载，不会自动删除已有原文。原有单文件下载限制仍有效。</p><div class="dp-error" role="alert" data-dp="settingsError" hidden></div></div><div class="dp-dialog-foot"><button class="button secondary" type="button" data-dp-action="close-settings">取消</button><button class="button primary" type="submit">保存设置</button></div></form></dialog>
<dialog id="discoveryPaperDelete" class="dp-dialog dp-confirm" aria-labelledby="dpDeleteTitle"><div class="dp-dialog-head"><h3 id="dpDeleteTitle">确认删除论文原文</h3><button class="button small" type="button" data-dp-action="close-delete" aria-label="关闭删除确认">×</button></div><div class="dp-dialog-body"><div class="dp-delete-metrics"><div><span>可删除文件</span><strong data-dp="deleteFiles">0</strong></div><div><span>文件逻辑字节</span><strong data-dp="deleteBytes">0 B</strong></div></div><p data-dp="deleteSkipped"></p><p class="dp-warning">保护文件和仍被未选记录引用的文件会跳过。论文元数据、解析记录和专家准入结果保留。</p><div class="dp-error" role="alert" data-dp="deleteError" hidden></div></div><div class="dp-dialog-foot"><button class="button secondary" type="button" data-dp-action="close-delete">取消</button><button class="button danger" type="button" data-dp-action="confirm-delete" disabled>确认删除原文</button></div></dialog>
```

### S-4: 全部新增 CSS（逐字复制，不得自由改值）

放styles.css末尾。不存在“其余样式执行时补齐”；所有新class已包含。动态磁盘填充用progress.value/max，禁止style.width。全局已有[hidden]规则复用。

```css
/* discovery-paper-archive-contract:start */
#taskProgressModal[data-discovery-review="true"] .dp-tabbar{overflow-x:auto;white-space:nowrap}
.dp-root{color:#1e293b;font-size:12px;line-height:1.6;gap:14px}
.dp-root h4,.dp-dialog h4{margin:0;font-size:14px;font-weight:600;color:#1e293b}
.dp-root p,.dp-dialog p{color:#475569;line-height:1.7;margin:0}
.dp-root .dp-muted,.dp-dialog .dp-muted{font-size:11px;color:#64748b;line-height:1.7}
.dp-heading,.dp-between,.dp-selection,.dp-footer,.dp-reader-tools,.dp-dialog-foot{display:flex;align-items:center;justify-content:space-between;gap:16px}
.dp-heading>div{min-width:0}.dp-heading .button{flex-shrink:0}
.dp-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.dp-storage{display:grid;grid-template-columns:1fr 1.65fr;border:1px solid rgba(15,23,42,.08);border-radius:8px;background:#fff}
.dp-storage-main{padding:12px 18px;border-right:1px solid rgba(15,23,42,.08);font-size:12px;color:#64748b}
.dp-storage-main>strong{display:block;font-size:26px;font-weight:600;line-height:1.45;letter-spacing:-.5px;color:#1e293b}
.dp-disk{padding:14px 20px;color:#64748b;font-size:12px}
.dp-disk b{font-weight:600;color:#334155}
.dp-meter{display:block;width:100%;height:8px;margin:15px 0 11px;border:0;border-radius:4px;overflow:hidden;appearance:none;background:#edf1f7;accent-color:#1e40af}
.dp-meter::-webkit-progress-bar{background:#edf1f7;border-radius:4px}.dp-meter::-webkit-progress-value{background:#1e40af;border-radius:4px}.dp-meter::-moz-progress-bar{background:#1e40af;border-radius:4px}
.dp-policy{padding:10px 12px;border:1px solid #e8edf3;border-radius:6px;background:#f8fafc;color:#475569;font-size:11px;line-height:1.7}
.dp-policy[data-state="BLOCKED"]{border-color:#fde6b0;background:#fffbeb;color:#92400e}
.dp-error{padding:11px 12px;border:1px solid rgba(225,29,72,.16);border-radius:6px;background:rgba(225,29,72,.07);color:#be123c;font-size:12px;line-height:1.7;overflow-wrap:anywhere}
.dp-filters{display:grid;grid-template-columns:1.6fr 1.3fr .9fr .9fr 1fr;gap:10px}
.dp-filters label{display:flex;flex-direction:column;gap:5px;margin:0;font-size:11px;color:#64748b}
.dp-filters :is(input,select),.dp-setting-field input,.dp-inline-label select{width:100%;min-width:0;height:35px;min-height:35px;padding:8px 10px;border:1px solid #dfe4eb;border-radius:6px;background:#f8fafc;color:#334155;font:inherit;font-size:12px;line-height:1.4;box-shadow:none;margin:0}
.dp-filters :is(input,select):hover:not(:disabled),.dp-setting-field input:hover:not(:disabled),.dp-inline-label select:hover:not(:disabled){border-color:#93b4ec}
.dp-filters :is(input,select):focus,.dp-setting-field input:focus,.dp-inline-label select:focus{outline:2px solid #c7d2fe;outline-offset:0;border-color:#818cf8;box-shadow:none}
.dp-selection{font-size:12px;color:#64748b}
.dp-check{display:flex;flex-direction:row;align-items:center;gap:7px;margin:0;font-size:12px;line-height:1.5;color:#334155}
.dp-check input[type="checkbox"],.dp-table input[type="checkbox"]{appearance:auto;width:14px;height:14px;min-width:14px;min-height:14px;max-width:14px;max-height:14px;margin:0;padding:0;flex:none;box-sizing:border-box;accent-color:#1e40af;cursor:pointer;box-shadow:none}
.dp-table-wrap{border:1px solid #e3e8ef;border-radius:7px;overflow-x:auto}
.dp-table{width:100%;min-width:1010px;table-layout:fixed;border-collapse:collapse}
.dp-table th,.dp-table td{padding:12px 10px;font-size:12px;line-height:1.55;text-align:left;border-bottom:1px solid #e8edf3;vertical-align:middle;overflow-wrap:anywhere}
.dp-table th{position:static;top:auto;z-index:auto;height:40px;background:#f8f9fb;color:#94a3b8;font-weight:600;white-space:nowrap}
.dp-table th:first-child{width:34px;padding-left:12px}.dp-table th:nth-child(2){width:auto}.dp-table th:nth-child(3){width:10%}.dp-table th:nth-child(4){width:17%}.dp-table th:nth-child(5){width:13%}.dp-table th:nth-child(6){width:13%}.dp-table th:last-child{width:11%}
.dp-table tbody tr:last-child td{border-bottom:0}.dp-table tbody tr:hover{background:#f8faff}.dp-table tbody tr.is-selected{background:#eef2ff}
.dp-title{display:block;color:#334155;font-weight:500;line-height:1.5}.dp-sub{display:block;margin-top:4px;font-size:10px;color:#94a3b8;line-height:1.5;white-space:normal}
.dp-link{border:0;background:none;padding:0;color:#1e40af;font:inherit;font-size:12px;cursor:pointer;text-decoration:none;box-shadow:none}
.dp-link:hover:not(:disabled){text-decoration:underline;color:#1e3a8a}.dp-link:active:not(:disabled){color:#172554}
.dp-badge{display:inline-flex;align-items:center;padding:2px 6px;margin-top:4px;border:1px solid #e2e8f0;border-radius:4px;background:#f1f5f9;color:#64748b;font-size:11px;line-height:1.5;white-space:nowrap}
.dp-ok{border-color:#cceedd;background:#ecfdf5;color:#047857}.dp-warn{border-color:#fde6b0;background:#fffbeb;color:#b45309}.dp-protected{border-color:#d5e3ff;background:#eff6ff;color:#1e40af}
.dp-footer{font-size:11px;color:#94a3b8;line-height:1.6}
.dp-empty,.dp-table td.dp-empty{padding:40px 20px;text-align:center;font-size:12px;color:#64748b;background:#f8fafc}
.dp-run-summary{display:flex;flex-wrap:wrap;align-items:center;gap:12px;padding:12px;border:1px solid #e2e8f0;border-radius:6px;background:#f8fafc;font-size:12px;color:#475569}
.dp-dialog{margin:auto;padding:0;border:1px solid #dfe5ee;border-radius:14px;background:#fff;color:#1e293b;max-height:90vh;overflow:auto;box-shadow:0 25px 80px rgba(15,23,42,.19);font-family:var(--font-body)}
.dp-dialog::backdrop{background:rgba(15,23,42,.45)}.dp-reader{width:min(1180px,calc(100vw - 48px));max-width:none}.dp-confirm{width:min(520px,calc(100vw - 48px));max-width:none}
.dp-dialog-head{display:flex;align-items:flex-start;justify-content:space-between;gap:15px;padding:16px 20px;border-bottom:1px solid #e2e8f0}
.dp-dialog h3{margin:0;font-size:15px;font-weight:600;color:#1e293b}.dp-dialog-head .dp-muted{margin-top:6px}
.dp-reader-tools{padding:10px 20px;border-bottom:1px solid #e2e8f0;font-size:11px;color:#64748b}
.dp-inline-label{display:flex;flex-direction:row;align-items:center;gap:8px;margin:0;min-width:0;font-size:12px;white-space:nowrap}.dp-inline-label select{max-width:330px}
.dp-reader-grid{display:grid;grid-template-columns:1.08fr 1fr;height:57vh;min-height:420px;max-height:620px}
.dp-original{min-width:0;overflow:auto;padding:16px;background:#edf0f5;border-right:1px solid #e2e8f0}
.dp-pdf{display:block;width:100%;height:100%;min-height:380px;border:1px solid #dbe1ea;background:#fff}
.dp-text{margin:0;padding:16px;background:#fff;color:#293340;border:1px solid #dbe1ea;white-space:pre-wrap;overflow-wrap:anywhere;font:11px/1.8 var(--font-mono)}
.dp-text mark{padding:0;background:#fef3c7;color:#293340;outline:2px solid #fcd34d;outline-offset:2px;border-radius:2px}
.dp-evidence{min-width:0;overflow:auto;padding:0 18px 18px}
.dp-evidence-tabs{display:flex;gap:18px;position:sticky;top:0;z-index:1;background:#fff;border-bottom:1px solid #e2e8f0;margin-bottom:15px}
.dp-evidence-tabs button{border:0;border-bottom:2px solid transparent;background:none;padding:14px 0 12px;color:#64748b;font:inherit;font-size:12px;cursor:pointer;white-space:nowrap}
.dp-evidence-tabs button:hover{color:#1e40af}.dp-evidence-tabs button:active{color:#172554}.dp-evidence-tabs button.is-active{border-bottom-color:#1e40af;color:#1e40af;font-weight:600}
.dp-evidence-card{margin:11px 0;padding:13px;border:1px solid #e2e8f0;border-radius:7px;background:#fff;font-size:12px}
.dp-evidence-card h4{font-size:12px;line-height:1.5}.dp-evidence-card .dp-muted{margin:3px 0 9px;overflow-wrap:anywhere}
.dp-evidence-card blockquote{margin:10px 0;padding:9px 10px;border-left:2px solid #cbd5e1;background:#f8fafc;color:#475569;font-size:11px;line-height:1.75;overflow-wrap:anywhere;white-space:pre-wrap}
.dp-evidence-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;color:#94a3b8;font-size:10px}.dp-evidence-card footer .dp-link{font-size:11px}
.dp-dialog .dp-warning{margin:12px 0;padding:11px 12px;border:1px solid #fde7b3;border-radius:6px;background:#fffbeb;color:#92400e;font-size:11px;line-height:1.8}
.dp-dialog-body{padding:18px 20px;font-size:12px;line-height:1.7}.dp-dialog-body>p{margin:12px 0}
.dp-dialog-foot{padding:13px 20px;border-top:1px solid #e2e8f0;background:#fbfcfe;font-size:11px;color:#94a3b8}
.dp-setting-field{display:flex;flex-direction:row;align-items:center;justify-content:space-between;gap:20px;margin:20px 0;color:#334155;font-size:12px}.dp-setting-field input{width:100px}
.dp-delete-metrics{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:15px;border:1px solid #e2e8f0;border-radius:7px;background:#f8fafc;color:#64748b;font-size:11px}
.dp-delete-metrics strong{display:block;font-size:23px;line-height:1.5;color:#1e293b}
.dp-root :is(button,input,select,a):focus-visible,.dp-dialog :is(button,input,select,a):focus-visible,.dp-run-summary button:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.dp-root :is(button,input,select):disabled,.dp-dialog :is(button,input,select):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.dp-root .button:disabled:hover,.dp-dialog .button:disabled:hover{transform:none;box-shadow:none}
@media(max-width:900px){.dp-filters{grid-template-columns:repeat(3,minmax(0,1fr))}.dp-search{grid-column:span 2}.dp-storage{grid-template-columns:1fr 1.3fr}.dp-reader-grid{display:block;overflow:auto;height:65vh;min-height:0;max-height:none}.dp-original{height:40vh;border-right:0;border-bottom:1px solid #e2e8f0}.dp-evidence{overflow:visible}.dp-pdf{min-height:0}}
@media(max-width:540px){.dp-root{padding:14px;gap:12px}.dp-storage{grid-template-columns:1fr}.dp-storage-main{border-right:0;border-bottom:1px solid rgba(15,23,42,.08)}.dp-storage-main>strong{font-size:24px}.dp-disk{padding:13px 15px}.dp-filters{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-selection,.dp-footer{align-items:flex-start;gap:8px;flex-direction:column}.dp-reader,.dp-confirm{width:calc(100vw - 24px)}.dp-reader-tools{align-items:flex-start;flex-direction:column}.dp-inline-label{width:100%}.dp-dialog-head{padding:14px}.dp-dialog-foot{padding:12px 14px}.dp-original{padding:12px}.dp-evidence-tabs{gap:12px}.dp-evidence-tabs button{font-size:11px}.dp-heading{align-items:flex-start}.dp-root .button,.dp-dialog .button{height:auto;min-height:36px}}
/* discovery-paper-archive-contract:end */
```

禁止：新inline style、新未声明class、复制pp-preview/虚构文档排版、修改全局p/th/input规则。已有class被复用，不更改其定义。

## 现状审计

### 前端样式盘点与改动前基线

现状逐字片段：`index.html:1135–1139` 是三页签；`styles.css:12717` 控制1180px，`:4494`控制80vh。完整原HTML/CSS及SHA在[代码快照](discovery-paper-code-receipts.md)。关键原块：

```html
<nav id="discoveryReviewTabs" class="batch-send-tabs" role="tablist" aria-label="深度发现功能" hidden>
  <button class="batch-send-tab is-active" type="button" role="tab" aria-selected="true" aria-controls="discoveryPipelinePane" id="discoveryPipelineTab">采集运行</button>
  <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewPane" id="discoveryReviewTab" tabindex="-1">专家审核</button>
  <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewHistoryPane" id="discoveryReviewHistoryTab" tabindex="-1">审核记录</button>
</nav>
```

```css
#taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
    max-width: 1180px;
    width: calc(100vw - 48px);
}
```

设计tokens：主色#1e40af，hover#1e3a8a，active#172554；正文#1e293b，次级#64748b，muted#94a3b8；button字体12px、32px高、圆角7px；modal圆角18px；新增纸库卡片圆角8px/边框#e3e8ef；声明值以S-4为准。`p`全局muted、`th`全局sticky及input最小高度会干扰新页面，已通过局部规则覆盖。（来源: K-global-p-is-muted-in-dialogs；K-nested-task-table-header-scope；K-batch-checkbox-global-input-height）

### 读写与注册
- DOM注册：`app.js:2452` DISCOVERY_REVIEW_TABS，elements:2545，reset:2720，buttons/panes:2770，select:2781，事件:2836；四处同时更新，非深度发现任务始终隐藏papers。
- `apvSetVisible(name==='pipeline')` 保留，切论文页不继续无谓轮询核验；新模块自管10秒可见刷新，离开即停止。
- 新数据写口仅01 protection/delete/settings与04 analysis API；读口01 stats/list/detail/content。前端无localStorage业务配置、无个人原文缓存。
- `api:4701`自动加词法绑定contextPath；文件URL不能假定window.contextPath。模块mount注入`makeUrl(path)=>contextPath+path`。
- 现有缓存key为20261023-batch-statistics-key-fix；逐项rg已确认当前测试无该字面值。index内所有本地CSS/JS key一起更新，07再顺序升级。（来源: K-frontend-cache-key-triad）

## 实现方案

### T-1 页面注册（I-1/I-3；S-1）

新增 `discovery-papers.js`，导出 `window.DiscoveryPapers.mount({root,api,makeUrl,onStatus})`，返回setVisible/setExecution/dispose；仅宿主app调用，不自读window.contextPath。index在app.js前加载模块。app仅接入页签与生命周期，不搬动原审核逻辑。采集摘要读取同executionId纸库stats，点击跳papers并选该execution。运行摘要宿主插在当前apv详情中，rerender时由模块重新挂接且不重复绑定。

### T-2 列表、详情及操作（I-1～I-4；S-1～S-4）

- 首次mount加载settings/stats/list；任务下拉复用现有执行记录ID+当前传入ID，不以“任务A/B”替代真实ID。分页20条；q防抖400ms；筛选变化清选择，AbortController与generation共同拒绝旧响应。
- 统计显示“成功留存X次＝新存Y次＋复用Z次”；失败尝试、无原文论文单列。文件数按blob去重，篇数按record，指标不相加冒充任务人数。stats失败保留旧值但标“读取失败，以下为旧快照”，无旧值则未知。
- 原文详情默认GET，不POST；多attempt下拉显示类型、时间、状态；选择已存原文发认证fetch获取Blob，确认content-type，PDF blobURL进iframe，其他按textContent放pre；切文件/关闭revokeObjectURL；dispose必须显式close全部本模块dialog，不能只隐藏父容器留下top-layer弹窗。下载同样走带contextPath认证地址，不把文件公开挂到静态目录。
- 点击解析对照POST；loading禁用按钮，完成刷新详情；409提示“原文状态已变化，请刷新后重试”；410显示“原文已删除，已有解析记录仍保留”。既有first与新analysis分别注明版本/时间；PARTIAL、BASELINE_MISSING醒目显示。
- 定位PDF使用blobURL#page=N；XML/文本跳到对应段落，仅原句quote匹配可高亮文字（使用mark元素，不增加class；无坐标不得声称精准高亮PDF）。失败时保留原句供查阅。
- 批量保护/取消保护分别提交目标布尔值与revision，显示changed/conflicts并刷新；保护筛选下取消后0条是正常结果。取消保护后选择清空，删除按钮disabled。
- 删除只用preview→确认→commit；protected/shared/BUSY/notStored逐类说明；没有可删项确认disabled；409展示新预检查并等待用户再次点击；部分失败逐条显示，重查列表与磁盘。原文删后关联/资格卡仍可查看。
- settings无行显示20/15GiB、关闭，保存用整数GiB转换bytes；保存成功才更新页内值；409保持输入并提示重新载入，503不假成功。
- 所有disabled/loading/error空态用S-4；授权失败沿用宿主登录处理。不存在仅改页面内存的“保护成功”。

### T-3 缓存与验收（I-3；S-4）

index全部本地asset key=20261023-discovery-paper-archive；记录新模块同key。新测试用实际HTML解析得到元素，不能使用“任何选择器都有元素”的万能stub。扩展旧discoveryReview.test.js四页签键盘/重置用例；该文件:446硬断言16个cache-busted assets，新增discovery-papers.js后应为17；同步更新这一精确数量，保留所有key同值断言。
`discoveryVerification.test.js` 同样钉死16个资源；同步该必跑测试的描述和数量为17，保留同key与runtime不带版本的断言。

## 变更文件清单

7 文件；子系统1（深度发现前端）；共享store新字段0。

| 文件 | 动作 |
|---|---|
| src/main/resources/static/index.html | 页签、pane/dialog、模块script、统一缓存key |
| src/main/resources/static/styles.css | 仅追加S-4块 |
| src/main/resources/static/app.js | 宿主生命周期/采集摘要接缝 |
| src/main/resources/static/discovery-papers.js | 新页面模块 |
| src/test/js/discoveryPapers.test.js | 实际DOM/请求/批处理回归 |
| src/test/js/discoveryReview.test.js | 原审核与四页签回归 |
| src/test/js/discoveryVerification.test.js | 仅同步资源数量及描述16→17，保留同key/runtime约束 |

## 验收标准

- I-1：null统计、保存失败、首次空库、旧快照明确；没有示例论文/硬编码磁盘字节进入生产。
- I-2：保护项能选，取消保护只提交protected=false标记；取消后选中数0；删除preview与commit分开；409不自动重删。
- I-3：任务A慢响应不能覆盖任务B；关弹窗0计时器/0未释放blob；/talent与根context下载均200；恶意原句作为文字；原三个页面无行为回归。
- I-4：GET详情不调用analysis/Apollo/准入API；历史年份及partial显示；删除后分析仍可读。
- S-1：真实index新增第4tab且aria-controls存在，左右/Home/End涵盖4页；1180px/80vh未变；其他task不显示papers。
- S-2/S-3：所有动态DOM映射到骨架；关闭恢复焦点、Esc关闭；表格空/错/选中/保护/已删状态截图齐全。
- S-4：抽取标记块逐字diff一致；无新增inline style；新增class集合仅契约集合。1440×1080与643×900浏览器验收，不得仅以JS测试代替CSS检查；表头背景不透明、position=static、checkbox=14px。
- 运行node --check、node --test src/test/js/discoveryPapers.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryVerification.test.js；重复测试仅在改动/失败后进行。

## 人工验收清单

### A-1: 真实页面与窄屏
- 前置条件：01的A/B共享+保护、C独占夹具及一条无正文记录，登录隔离实例；窗口1440×1080。
- 操作步骤：1. 专家列表→深度发现。2. 查看四页签并键盘切换。3. 进论文原文。4. 改643×900，横向滚动表格。5. 打开普通批量发信任务对照。
- 预期结果：顺序采集运行/专家审核/审核记录/论文原文；桌面modal宽1180px、最大高80vh；主色#1e40af，checkbox14px、表头#f8f9fb且不悬浮遮正文；窄屏卡片/工具栏可操作，表格仅自身横滚；发信和原三页签布局/功能不变。
- 覆盖：I-1/I-3，S-1～S-4。

### A-2: 批量取消保护和安全删除
- 前置条件：A/B共享1024bytes、B保护，C独占2048bytes；使用01测试夹具构造。
- 操作步骤：1. 筛选已保护，选B，点批量取消保护。2. 清筛选，查看B原文。3. 选择A/B/C，删除预检查后取消。4. 再次预检查并确认。5. 查看解析和磁盘卡。
- 预期结果：步骤1 changed1、选择0、原文保留；步骤3取消没有删除；步骤4删物理文件2个/逻辑3072bytes；分析记录不变；磁盘卡measuredAt更新且不把逻辑字节当立即释放磁盘量。
- 覆盖：I-1/I-2/I-4，S-2/S-3/S-4。

### A-3: 预览、资格证据和故障
- 前置条件：04的甲Assistant Professor/乙已取得PhD/丙PhD student夹具；另一条BASELINE_MISSING；测试服务器可返回409/503与延迟响应。
- 操作步骤：1. 查看记录，确认没有自动分析。2. 点击解析对照，分别打开两类证据页签和定位。3. 切任务期间延迟旧列表。4. 保存设置时返回503。5. 用另一会话先保护文件，再在旧会话确认删除。
- 预期结果：三个分类与2023年份分开，显示固定历史提示；无基线者delta不是0；旧响应不串任务；保存失败弹窗保留输入；删除409刷新预检查且不自动重删；已准入专家数量不变。
- 覆盖：I-1～I-4，S-1～S-4；API→UI全部写读路径。

### A-4: 持久化与下载路径
- 前置条件：应用部署在/talent，记录一份PDF与一份HTML；当前设置20/15GiB。
- 操作步骤：1. 保存25/15GiB，保护一份原文。2. 刷新并重启隔离实例。3. 下载两份。4. 关闭重开弹窗，再打开专家审核与审核记录。
- 预期结果：配置25/15GiB与保护状态保留；下载200、HTML只显示文字；没有重复事件/请求；原审核操作与历史记录仍可用。
- 覆盖：I-2/I-3/I-4，S-1/S-3；持久配置/旧页面回归。

## 修正记录

- 2026-10-24：用户“批准 继续”批准05暂停报告C-1：将discoveryVerification.test.js加入第七个授权文件，仅同步新增模块后的资源数量与描述16→17，保留缓存键一致性与runtime不带版本的断言。依据主计划I-3及执行前检查第5项；不改产品行为、不排除测试。批准与提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
