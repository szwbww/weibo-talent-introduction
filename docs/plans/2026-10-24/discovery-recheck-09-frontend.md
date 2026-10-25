# 09 · 正式审核页面的重审入口和逐步日志

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：在真实“深度发现→专家审核”接入批量重审、固定名单确认、整批进度、每人8步、前后差异及历史。使用服务器真实结果，不复制预览的虚构8人。保留原人工操作、采集运行/论文原文/Apollo保存配置；不改全站视觉，不将“预计准入”标成“自动准入”。

## 关键不变量

### I-1：真实与当前
- 规则：所有数字/状态来自08API；选人用真实docId；切任务/关闭/切人时abort请求并增加epoch，迟到回包不得覆盖新上下文；每次仅一个轮询timer。
- 适用：新JS和app宿主
- 违反后果：张冠李戴或重复轮询。
- 来源：需求及下列源码审计

### I-2：启动前固定
- 规则：默认AUDIT+REUSE；固定名单完成才启用确认，显示人数/hash/模式/Apollo冻结search和detail设置；改变任一筛选/模式/证据选项后必须另prepare，不沿用旧hash。关闭框不等于取消run，明确分开。
- 适用：配置dialog
- 违反后果：启动范围或付费模式偏差。
- 来源：需求及下列源码审计

### I-3：日志与计数有定义
- 规则：总步数8，显示步骤状态/全部原因/主卡点/证据出处/版本/耗时/尝试；SKIPPED不算失败，旧史“历史未记录”；准入建议、实际提交、候选同步三个值分列；失败显示code和可执行动作。
- 适用：列表摘要、历史、详情
- 违反后果：混淆业务失败/证据不足。
- 来源：需求及下列源码审计

### I-4：样式受约束
- 规则：全部新增节点只允许S-1/S-2 class；对照预览布局但生产移除rr-preview背景/虚构数据；样式作用域.dr-recheck；opaque白色dialog、正文不受全局p弱化。
- 适用：HTML/JS/CSS
- 违反后果：样式偏离真实环境或污染其他弹窗。
- 来源：K-global-p-is-muted-in-dialogs

## 样式契约

### S-1：DOM与组件完整合同
复用既有`.button.primary/.button.secondary/.button.small`，不更改规则。下面新增DOM置于原工具栏之后，data-rr为局部绑定键，不由全局选择器操作。新增普通列表行按钮仅用既有`button secondary small`。

```html
<section class="dr-recheck" id="discoveryRecheckRoot" aria-label="重新审核">
  <div class="rr-between"><button class="button primary" type="button" data-rr="open">批量重新审核</button><button class="rr-link" type="button" data-rr="history">重审记录</button></div>
  <section class="rr-panel" data-tone="blue" data-rr="run" hidden aria-label="本次重审">
    <div class="rr-between"><h4 data-rr="run-title"></h4><span class="rr-tag" data-rr="run-state"></span><div class="rr-actions"><button class="button secondary" type="button" data-rr="cancel-run">取消任务</button><button class="rr-link" type="button" data-rr="run-details">查看逐人流程</button></div></div>
    <p class="rr-muted" data-rr="run-context"></p><progress data-rr="progress" max="1" value="0" aria-label="已处理固定名单"></progress>
    <div class="rr-metrics" data-rr="metrics"></div><p class="rr-status" data-rr="run-message" role="status" aria-live="polite"></p>
  </section>
  <dialog class="rr-dialog rr-config" data-rr="config" aria-labelledby="recheckConfigTitle">
    <header class="rr-head"><h3 class="rr-title" id="recheckConfigTitle">批量重新审核</h3><button class="button secondary" type="button" data-rr="close-config">关闭</button></header>
    <div class="rr-content">
      <fieldset class="rr-field"><legend>名单范围</legend><label class="rr-option"><input type="radio" name="rr-scope" value="IDS">所选专家 <span data-rr="selected-count">0</span> 人</label><label class="rr-option"><input type="radio" name="rr-scope" value="ALL_MATCHING">当前筛选的全部页<small data-rr="filter-summary"></small></label></fieldset>
      <fieldset class="rr-field"><legend>执行模式</legend><label class="rr-option"><input type="radio" name="rr-mode" value="AUDIT" checked>仅对照验证<small>记录新结论；不改变当前准入和候选层。</small></label><label class="rr-option"><input type="radio" name="rr-mode" value="ENFORCE">重审并应用结果<small>按现有规则提交可准入者；人工和联系保护仍有效。</small></label></fieldset>
      <fieldset class="rr-field"><legend>证据来源</legend><label class="rr-option"><input type="radio" name="rr-evidence" value="REUSE" checked>复用留存证据<small>不请求外部服务；缺失证据会明确标注。</small></label><label class="rr-option"><input type="radio" name="rr-evidence" value="REFRESH">刷新外部证据<small>按已保存的免费／付费配置调用；外部数据变化单列。</small></label></fieldset>
      <p class="rr-muted" data-rr="apollo-config"></p><p class="rr-status" data-rr="prepare-status" role="status" aria-live="polite"></p>
      <details data-rr="manifest-panel" hidden><summary>查看固定名单</summary><div class="rr-manifest" data-rr="manifest"></div><div class="rr-pager"><button class="button secondary" type="button" data-rr="manifest-prev">上一页</button><span data-rr="manifest-page"></span><button class="button secondary" type="button" data-rr="manifest-next">下一页</button></div></details>
    </div><footer class="rr-foot"><span class="rr-muted" data-rr="scope-hash"></span><button class="button secondary" type="button" data-rr="prepare">固定名单</button><button class="button primary" type="button" data-rr="start" disabled>确认执行</button></footer>
  </dialog>
  <dialog class="rr-dialog" data-rr="history-dialog" aria-labelledby="recheckHistoryTitle">
    <header class="rr-head"><h3 class="rr-title" id="recheckHistoryTitle">重审记录</h3><button class="button secondary" type="button" data-rr="close-history">关闭</button></header>
    <div class="rr-content"><p class="rr-status" data-rr="history-status" role="status"></p><div class="rr-table-wrap"><table class="rr-table"><thead data-rr="history-head"></thead><tbody data-rr="history-rows"></tbody></table></div><footer class="rr-pager"><span data-rr="history-page"></span><div class="rr-row"><button class="button secondary" type="button" data-rr="history-prev">上一页</button><button class="button secondary" type="button" data-rr="history-next">下一页</button></div></footer></div>
  </dialog>
  <dialog class="rr-dialog" data-rr="detail" aria-labelledby="recheckDetailTitle">
    <header class="rr-head"><div><h3 class="rr-title" id="recheckDetailTitle"></h3><p class="rr-muted" data-rr="detail-person"></p></div><button class="button secondary" type="button" data-rr="close-detail">关闭</button></header>
    <div class="rr-detail-summary" data-rr="detail-summary"></div><div class="rr-detail-content"><div class="rr-detail-grid"><ol class="rr-steps" data-rr="steps"></ol><section class="rr-step-body" data-rr="step-body" aria-live="polite"></section></div></div>
    <footer class="rr-foot"><span class="rr-muted" data-rr="detail-version"></span><button class="button secondary" type="button" data-rr="sync-retry" hidden>仅重试候选同步</button><button class="button secondary" type="button" data-rr="person-history">查看此人历次重审</button></footer>
  </dialog>
</section>
```
动态节点只允许下述结构；历史表头用thead→tr→th，固定名单用同rr-table结构；证据链接用a.rr-link，attempt展开用details→summary→p.rr-muted；无其他新class。

```html
<!-- metrics 内固定6项；AUDIT用预计准入，ENFORCE用实际准入 -->
<div><span>已处理</span><strong data-value></strong></div>
<!-- steps 每人必须生成8个li，step-no=1..8；状态仅取服务端 -->
<li><button class="rr-step" type="button" data-step-no="1" data-state="NOT_STARTED"><span class="rr-step-number">1</span><span class="rr-step-text">名单快照与保护<small>未开始</small></span></button></li>
<!-- step-body -->
<h4 data-step-title></h4><span class="rr-tag" data-step-state></span><p data-step-reason></p>
<dl class="rr-facts"><dt>输入</dt><dd data-step-input></dd><dt>证据</dt><dd data-step-evidence></dd><dt>版本</dt><dd class="rr-code" data-step-version></dd><dt>执行时间</dt><dd data-step-time></dd><dt>尝试次数</dt><dd data-step-attempt></dd></dl>
<blockquote class="rr-quote" data-step-quote hidden></blockquote>
<div class="rr-delta"><div>执行前<strong data-before></strong></div><div>本轮建议 / 实际应用<strong data-after></strong></div></div>
<!-- history row: content via textContent, action via button data-rr-item -->
<tr><td data-history-start></td><td data-history-mode></td><td data-history-scope></td><td data-history-result></td><td data-history-state></td><td><button class="rr-link" type="button" data-rr-item>查看</button></td></tr>
```

### S-2：新增CSS全文（逐字复制）
覆盖正文/hover/active/disabled/focus/响应式；不复制预览的body背景、rr-preview或虚构示例内容。

```css
.dr-recheck{color:#1e293b;font-size:13px;line-height:1.6}
.dr-recheck [hidden]{display:none!important}
.dr-recheck p{margin:6px 0;color:#475569;font-size:12px;line-height:1.7}
.dr-recheck h4{margin:0;color:#1e293b;font-size:14px;font-weight:600}
.dr-recheck .rr-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.dr-recheck .rr-between{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.dr-recheck .rr-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-left:auto}
.dr-recheck .rr-muted{color:#64748b;font-size:12px;line-height:1.7}
.dr-recheck .rr-title{margin:0;color:#1e293b;font-size:16px;font-weight:600}
.dr-recheck .rr-panel{margin:12px 0;padding:14px 16px;border:1px solid #e2e8f0;border-radius:10px;background:#fff}
.dr-recheck .rr-panel[data-tone="blue"]{border-color:#c7d2fe;background:#f7f9ff}
.dr-recheck .rr-tag{display:inline-block;padding:3px 8px;border:1px solid #dbe4ff;border-radius:6px;background:#eff4ff;color:#1e40af;font-size:11px;white-space:nowrap}
.dr-recheck .rr-tag[data-tone="ok"]{border-color:#a7f3d0;background:#ecfdf5;color:#047857}
.dr-recheck .rr-tag[data-tone="warn"]{border-color:#fde68a;background:#fffbeb;color:#92400e}
.dr-recheck .rr-tag[data-tone="error"]{border-color:#fecdd3;background:#fff1f2;color:#be123c}
.dr-recheck .rr-tag[data-tone="skip"]{border-color:#e2e8f0;background:#f1f5f9;color:#64748b}
.dr-recheck .rr-link{appearance:none;margin:0;padding:3px 0;border:0;background:none;color:#1e40af;font:inherit;font-size:12px;text-align:left;cursor:pointer;box-shadow:none;transform:none}
.dr-recheck .rr-link:hover:not(:disabled){color:#1d4ed8;text-decoration:underline}
.dr-recheck .rr-link:active:not(:disabled){color:#172554}
.dr-recheck .rr-link:disabled{color:#94a3b8;text-decoration:none;cursor:not-allowed}
.dr-recheck :is(button,input,select,summary):focus-visible{outline:2px solid #2563eb;outline-offset:3px}
.dr-recheck button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.dr-recheck .rr-metrics{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:6px;margin-top:8px}
.dr-recheck .rr-metrics>div{padding:6px;color:#64748b;font-size:11px}
.dr-recheck .rr-metrics strong{display:inline;margin-left:7px;color:#1e40af;font-size:17px}
.dr-recheck progress{display:block;width:100%;height:7px;margin:12px 0;border:0;accent-color:#2563eb}
.dr-recheck progress::-webkit-progress-bar{border-radius:6px;background:#e2e8f0}
.dr-recheck progress::-webkit-progress-value{border-radius:6px;background:#2563eb}
.dr-recheck .rr-status{min-height:20px;color:#1e40af;font-size:12px;overflow-wrap:anywhere}
.dr-recheck .rr-status[data-tone="error"]{color:#b91c1c}
.dr-recheck .rr-dialog{width:min(1020px,calc(100vw - 40px));max-height:90vh;margin:auto;padding:0;border:1px solid #e2e8f0;border-radius:14px;background:#fff;color:#1e293b;box-shadow:0 24px 80px #0f172a40}
.dr-recheck .rr-dialog::backdrop{background:#0f172a66}
.dr-recheck .rr-config{width:min(700px,calc(100vw - 32px))}
.dr-recheck .rr-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;padding:20px 24px;border-bottom:1px solid #e2e8f0}
.dr-recheck .rr-content{max-height:64vh;overflow:auto;padding:20px 24px}
.dr-recheck .rr-foot{display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap;padding:14px 24px;border-top:1px solid #e2e8f0}
.dr-recheck .rr-foot .rr-muted{margin-right:auto}
.dr-recheck .rr-field{margin:0 0 16px;padding:0;border:0}
.dr-recheck .rr-field legend{margin-bottom:8px;color:#1e293b;font-size:13px;font-weight:600}
.dr-recheck .rr-option{display:block;margin-bottom:8px;padding:12px;border:1px solid #e2e8f0;border-radius:7px;color:#1e293b;font-size:13px;cursor:pointer}
.dr-recheck .rr-option:has(input:checked){border-color:#818cf8;background:#f7f9ff}
.dr-recheck .rr-option small{display:block;margin:4px 0 0 23px;color:#64748b;font-size:12px}
.dr-recheck input[type="radio"],.dr-recheck input[type="checkbox"]{appearance:auto;width:15px;height:15px;min-width:15px;min-height:15px;margin:0 6px 0 0;vertical-align:middle;accent-color:#1e40af}
.dr-recheck .rr-detail-summary{padding:12px 24px;border-bottom:1px solid #e2e8f0;background:#f8fafc}
.dr-recheck .rr-detail-grid{display:grid;grid-template-columns:235px minmax(0,1fr);min-height:420px}
.dr-recheck .rr-steps{margin:0;padding:16px;border-right:1px solid #e2e8f0;background:#fbfcfe;list-style:none}
.dr-recheck .rr-step{display:flex;align-items:center;gap:10px;width:100%;margin:0 0 3px;padding:7px 8px;border:1px solid transparent;border-radius:7px;background:transparent;color:#475569;font:inherit;text-align:left;cursor:pointer;box-shadow:none;transform:none}
.dr-recheck .rr-step:hover:not(:disabled){background:#eff4ff}
.dr-recheck .rr-step:active:not(:disabled){background:#dbeafe}
.dr-recheck .rr-step[aria-current="step"]{border-color:#c7d2fe;background:#eff4ff;color:#1e40af}
.dr-recheck .rr-step-number{display:grid;place-items:center;flex:none;width:24px;height:24px;border-radius:50%;background:#e2e8f0;color:#475569;font-size:12px}
.dr-recheck .rr-step[data-state="SUCCESS"] .rr-step-number{background:#d1fae5;color:#047857}
.dr-recheck .rr-step[data-state="INSUFFICIENT"] .rr-step-number{background:#fef3c7;color:#92400e}
.dr-recheck .rr-step[data-state="ERROR"] .rr-step-number{background:#ffe4e6;color:#be123c}
.dr-recheck .rr-step-text{flex:1;font-size:12px}
.dr-recheck .rr-step-text small{display:block;margin:0;color:#64748b;font-size:11px}
.dr-recheck .rr-step-body{min-width:0;padding:20px}
.dr-recheck .rr-facts{display:grid;grid-template-columns:90px minmax(0,1fr);gap:9px 12px;margin:16px 0;font-size:12px;line-height:1.7}
.dr-recheck .rr-facts dt{color:#64748b}
.dr-recheck .rr-facts dd{margin:0;color:#334155;overflow-wrap:anywhere}
.dr-recheck .rr-quote{margin:12px 0;padding:12px;border-left:3px solid #a5b4fc;background:#f8fafc;color:#475569;font-size:12px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}
.dr-recheck .rr-code{color:#64748b;font:11px var(--font-mono);overflow-wrap:anywhere}
.dr-recheck .rr-delta{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:16px 0}
.dr-recheck .rr-delta>div{padding:12px;border:1px solid #e2e8f0;border-radius:7px;font-size:12px}
.dr-recheck .rr-delta strong{display:block;margin:5px 0}
.dr-recheck .rr-table-wrap{overflow:auto;border:1px solid #e2e8f0;border-radius:8px}
.dr-recheck .rr-table{width:100%;min-width:900px;border-collapse:collapse}
.dr-recheck .rr-table th,.dr-recheck .rr-table td{padding:10px;border-bottom:1px solid #e2e8f0;text-align:left;vertical-align:top;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.dr-recheck .rr-table th{background:#f8fafc;color:#64748b;font-weight:600}
.dr-recheck .rr-table small{display:block;color:#64748b;font-size:11px}
.dr-recheck .rr-table tr:last-child td{border-bottom:0}
.dr-recheck .rr-pager{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;margin-top:12px}
.dr-recheck .rr-manifest{max-height:220px;overflow:auto;margin-top:8px}
.dr-recheck .rr-detail-content{max-height:64vh;overflow:auto}
@media(max-width:760px){.dr-recheck .rr-actions{margin-left:0}.dr-recheck .rr-metrics{grid-template-columns:repeat(3,minmax(0,1fr))}.dr-recheck .rr-metrics strong{display:block;margin-left:0}.dr-recheck .rr-dialog{max-height:94vh}.dr-recheck .rr-head,.dr-recheck .rr-content,.dr-recheck .rr-foot,.dr-recheck .rr-step-body{padding:14px}.dr-recheck .rr-detail-grid{grid-template-columns:1fr;min-height:0}.dr-recheck .rr-steps{display:grid;grid-template-columns:1fr 1fr;padding:10px;border-right:0;border-bottom:1px solid #e2e8f0}.dr-recheck .rr-step{margin:0}.dr-recheck .rr-facts{grid-template-columns:75px minmax(0,1fr)}.dr-recheck .rr-delta{grid-template-columns:1fr}.dr-recheck .button{min-height:36px}}

```

## 现状审计

真实DOM index.html:1274–1319现有四卡、两排dr-toolbar、7列表、分页；app.js:2427起现有审核模块。线上预览为独立static模拟，不能直接拷贝preview.js业务数据。
改动前逐字片段：
```html
<div class="dr-toolbar">
  <span id="discoveryReviewSelection">已选择 0 人</span>
  <button class="button secondary" type="button" data-review-action="APPROVE">通过所选</button>
  <button class="button secondary" type="button" data-review-action="HOLD">暂缓所选</button>
  <button class="button secondary" type="button" data-review-action="REJECT">不通过所选</button>
</div>
```
复用：styles.css:12717的modal task宽1180/calc；:12752四卡；:12768工具条；:12780表格滚动。现有class所有用途保持不变，不改全局p/button/modal，所有新规则派生到dr-recheck。颜色#1e40af/#475569/#64748b，边框#e2e8f0，白底#fff，间距6/8/10/12/14/16/20/24，圆角6/7/8/10/14均在S-2完整定义。
存储：新JS仅内存selection/context/timer，不localStorage保存名单、token或资格；写者事件处理器，读者render；持久状态只由08API写入run/item/trace。数量用01单任务API，不恢复六路请求。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. index在原人工工具栏之后、原列表之前插入S-1根节点及三个dialog；不移走原人工按钮、不增加全局导航。加载discovery-recheck.js在app.js前；统一全部18个版本化资源key=`20261024-discovery-recheck`；两个资源测试数量17→18，增加明确脚本顺序断言。（I-4，S-1/2）
2. 新模块暴露createDiscoveryRecheck({root,api,getSelectedDocIds,getFilter,onApplied,openPaper})，返回mount/unmount/onRows；app仅桥接审核生命周期/实际选中docId/筛选/刷新数量和列表。不得复刻手工approve逻辑。ID选中沿现有可选列表；任何不能勾选的保护对象可以通过ALL_MATCHING进入且被后端标保护，不前端擅自强放开旧人工checkbox。每行原“操作”格加“验证流程”按钮，使用现有button secondary small类，不新增第8列表列。（I-1/4，S-1）
3. prepare后读取分页manifest，显示真实固定人数、范围、配置，READY才start；数据准备/网络错误在本dialog rr-status显示，不能覆盖原审核错误框。关闭配置保留待执行run，只有“取消任务”调用cancel；Esc只关dialog且归还焦点。请求1次、运行每2秒poll，隐藏页暂停poll，恢复时GET状态；终态停止。（I-1/2）
4. 整批六指标为固定总数、已处理、预计/实际准入、待验证、错误、取消/保护（最后一格分别列取消和保护数，不能相加当一个人数类别）；queued/processing通过已处理差值及文本列出；候选待同步/同步失败另有run-message，计数不可重复加总。模式显示AUDIT“预计准入”、ENFORCE“实际准入”。详情固定八个step按钮及attempt列表，默认选blockingStep，否则最后完成步；summary显示current与proposed/applied差异。（I-3，S-1/2）
5. 历史dialog提供run列表、run内人列表、按docId历史三种同表模式，明确表头，分页每页20；GET /runs支持docId过滤，GET /latest按当前页≤100 docIds批量读，不逐人N+1。这两个只读查询已由08提供；后端仅按第8项增加原Repository只读计数，不修改准入写入。用户点真实证据链接经第7项桥接调用现有原文reader，SHA/页码不存在则明确未留存，不猜定位。（I-1/3）
6. 所有外部名字、原因、quote通过textContent；URL只接受服务器提供http(s)/同域API引用并校验，不innerHTML注入provider原文。CSS逐字复制S-2；新增任意class或inline style必须修订本计划，不交执行agent自行设计。（I-4）
7. 原`DiscoveryPapers.mount()`只公开setVisible/setExecution/dispose，reader为私有且默认首个可读attempt。仅在同一模块公开`openPaper(ref,trigger)`窄桥接，按服务器冻结的archiveRecordId/attemptId/bodySha256核对目标并复用原reader；可选页定位只用真实证据locator，不猜SHA/页码。保留旧默认reader、保护/删除功能和dispose/迟到请求隔离。新增回归覆盖指定attempt、错误SHA/attempt与dispose陈旧回包。（I-1/3）
8. 08的RecheckCounts与SQL在原Repository同文件，QueryService原样转发。保留现有queued字段，追加pending（RESERVED/QUEUED/RETRY_WAIT）、processing（RUNNING）和protected；前两者是queued的互斥分区，protected只从已持久化的APPLICATION_PRESENT/SBIR_PROTECTED/CONTACT_PROTECTED/REVIEW_PROTECTED/LEGACY_AUTO_PROTECTED原因取诊断子计数，可与终态重叠，不得和互斥状态求和。仅只读查询/DTO与真实SQL回归，不新增schema、provider请求、逐人N+1或任何准入/人工写行为。（I-3）

## 变更文件清单

仅下列 12 个文件。新增文件明确标注；其余文件不得顺带修改。依用户A7持续授权为满足既定原文/真实计数合同增加四个直接依赖，09获得必要文件数例外。

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/discovery-recheck.js` | 新增组件及API适配 |
| `src/main/resources/static/app.js` | 宿主挂载/列表按钮/旧回包隔离 |
| `src/main/resources/static/index.html` | 精确DOM、加载脚本、18资源同key |
| `src/main/resources/static/styles.css` | S-2完整作用域CSS |
| `src/test/js/discoveryRecheck.test.js` | 新增真实DOM/状态/迟到回包测试 |
| `src/test/js/discoveryReview.test.js` | 人工操作和计数回归 |
| `src/test/js/meetingConfirmationAssets.test.js` | 18资源与顺序 |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 18资源与同key |
| `src/main/resources/static/discovery-papers.js` | 公开原reader的冻结record/attempt/SHA窄桥接，不复制reader |
| `src/test/js/discoveryPapers.test.js` | 指定原文/错误SHA或attempt/陈旧dispose回包回归 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 既有只读counts SQL/DTO追加pending/processing/protected，旧字段及写链不变 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` | 真MySQL核对互斥分区、保护重叠计数与原回归 |

## 验收标准

- I-1：切人/切任务时旧Promise完成不更新DOM；unmount后timer/监听器0；单poll；无预览人名、mock固定数字；网络失败不伪0。
- I-2：默认两个radio值；READY前按钮disabled；选项变化使旧hash失效；Esc不cancel；人工原按钮仍调用原endpoint。
- I-3：8步完整；状态文案映射明确；AUDIT仅预计、ENFORCE实际、同步另列；旧记录“历史未记录”；错误包含code和重试操作。
- I-4/S-1/S-2：对完整CSS逐字diff；DOM类名集合包含于S-1/S-2及列出的既有button类；新增节点无style属性。真实浏览器1280×720和640×900截图检查，dialog不透底、横向滚动限制在表、焦点与Esc可用。
- node --check src/main/resources/static/discovery-recheck.js；运行本表4个JS测试及全量node --test src/test/js/*.test.js。只用弱DOM stub不能取代真实浏览器检查。
- 新增依赖门禁：`node --check src/main/resources/static/discovery-papers.js`；`node --test src/test/js/discoveryPapers.test.js`；指定JDK11执行`mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationQueryServiceTest -DmysqlIt=true`，真实Docker MySQL不得跳过。全部必需命令在最终相关修改后重新运行，保留原四文件及全量JS命令。

## 人工验收清单

### A-1：默认配置与固定人数
- 前置条件：测试环境1001待审核人；当前页20且选中2人。
- 操作步骤：点批量重新审核；检查默认选项；固定所选；未READY前尝试启动；READY后执行。
- 预期结果：默认仅对照/复用；固定2人，未READY按钮禁用；开始后仅2人，原卡片准入不变。
- 覆盖：I-1/2

### A-2：逐人流程与候选同步
- 前置条件：06–08联调fixture含直接准入、缺身份、Apollo429、已准入但候选同步失败。
- 操作步骤：打开每人的验证流程；点失败者仅重试候选同步。
- 预期结果：每人1–8步；缺身份卡3；429显示等待与下次时间；同步失败显示已准入，重试不增加Apollo请求。
- 覆盖：I-3

### A-3：视觉与无串台
- 前置条件：1280×720和640×900浏览器；人为延迟第1人的详情请求。
- 操作步骤：快速切到第2人、关开dialog、Esc退出、Tab遍历焦点；打开论文原文和原人工确认。
- 预期结果：第1人迟到结果不替换第2人；宽屏235px步骤栏，窄屏两列步骤；白色不透明背景、正文#475569/12px；旧弹窗布局不变。
- 覆盖：I-1/4，S-1/2

### A-4：应用与人工保护
- 前置条件：2个可准入待审核+1个人工通过保护样本。
- 操作步骤：选ENFORCE/REUSE固定并执行；看run、列表、卡片和历史。
- 预期结果：显示实际准入仅成功提交数；保护原因可看；四卡经01刷新；人工通过不被覆盖；旧轮仍可查询。
- 覆盖：I-2/3/4

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。

## 修正记录
- 2026-10-25，依用户A7持续授权增加上述四个直接依赖、8→12文件例外及对应命令；原reader缺公开固定来源桥接，08计数缺本计划已要求的拆分/保护值。仅满足原I-1/3合同，不改变S-1/S-2、准入策略或旧人工路径。确切源码/检索回执见`../fast/discovery-recheck-master/children/09/execution.md`，授权/前后身份见同一ledger的Amendments。
