# 05 — 真实页面入口、日志与完整样式

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

在现有“论文原文”页面提供整批按钮、确认弹窗、运行/历史记录、逐篇和逐档案流程。保留专家审核中的原重审按钮；用户操作正式模式后自动衔接，无需再手工点第二个按钮。

保持不变：①现有样式、单篇解析、下载/删除/保护；②专家重审按钮与8步详情；③Apollo配置保存和免费/付费开关语义；④关闭页面不中断后台任务。

不做：换后台主题、复制预览假数据、展示磁盘绝对路径/API密钥、把hash当主信息。两个子系统：论文页组件、现有核验详情打开接缝。

## 关键不变量

### Invariant I-1: 服务端状态唯一来源
- Rule: 前端计数和runId全部读API；页面重入先查询当前source活动任务，存在则恢复，不自动新建。HTTP超时后先按同requestKey查任务，不能换新key重发。scope由sourceExecutionId决定，与selected/page/q无关。
- Applies to: 新JS、挂载接缝
- Violation consequence: 重复任务与只删/处理本页旧问题复现。
- 来源: original

### Invariant I-2: 按钮语义
- Rule: 默认AUDIT；ENFORCE明确补录补齐并自动重审；查询失败时按钮disabled并显示原因。关闭dialog/切tab/dispose只abort前端请求与轮询；仅显式cancel向后端POST取消。
- Applies to: 确认/启动/取消/生命周期
- Violation consequence: 误写或关闭页面取消后台。
- 来源: original

### Invariant I-3: 结果可解释
- Rule: 首次关联、本次关联、新增绑定、档案变化、实际准入不同字段；AUDIT实际业务数0，拟补录/拟补齐单列。缺源/保护/冲突/资格不足/系统错误各有中文原因；不以“未分析”覆盖已经完成的新任务。
- Applies to: 日志/行详情/原文打开
- Violation consequence: 用户仍看不出卡在哪一步。
- 来源: original

### Invariant I-4: 结构与安全
- Rule: 所有新增DOM/CSS严格遵守S-1..S-6；原文/姓名/原因按textContent；两类分页各保持page，快速切源旧响应不能覆盖新面板。权限失败401/403停止轮询并提示，不清空已显示历史伪装0。
- Applies to: 所有新渲染及事件
- Violation consequence: 样式偏差、XSS、错批展示。
- 来源: K-global-p-is-muted-in-dialogs

## 样式契约

### S-1：整批入口
- 复用 `.dp-root/.button.primary`；基线 `styles.css:13370–13388` 与证据 `frontend-before.md` 中逐字 button 规则。新增 `.rp-batch/.rp-muted` 规则如下，保持预览中的蓝色边框/浅背景/14px标题。仅当已选具体任务且preview成功时启用；默认无范围。
### S-2：确认弹窗
- 复用 `.dp-dialog/.dp-dialog-head/.dp-dialog-body/.dp-dialog-foot/.dp-actions/.dp-warning/.dp-error`（styles.css:13408–13438）；新增 `.rp-dialog/.rp-scope/.rp-option/.rp-process/.rp-label`。
- 显式覆盖全局 label 的 column 排版；dialog在open时flex，头脚不收缩，body单独滚动。确认按钮位置始终可见。
### S-3：统计与分页
- 使用 `.rp-log/.rp-stats/.rp-summary/.rp-tabs/.rp-log-wrap/.rp-pager`；卡片固定六列，≤760px三列；表格在容器内横滚，禁止挤爆页面。纸张与档案两种单位分tab。
### S-4：逐项记录
- 六个li固定步骤，不因失败省略后续步；后续跳过标灰，错误粉红，证据不足琥珀，运行蓝，成功绿。每个step写中文状态及原因，不能只靠颜色。展开当前第5步后通过现有8步详情查看资格核验，不复制8步引擎。
- 历史列表的动态DOM仅使用 `.rp-log-wrap` table、`.rp-pager`、`.dp-link`、`.dp-badge`、`.dp-muted`；列为任务号/源任务/模式/状态/时间/打开。空态和错误复用 `.dp-empty/.dp-error`。
### S-5：完整新增CSS（必须逐字复制）
- 以下块是最终契约，不是示意；不得增删属性/修改值。未出现的class不得自行新增；不修改既有全局class，故无跨模块就地改样式。新增交互状态已列全；button沿用已审计的全局primary/secondary/small状态。
```css
/* paper-reprocess-contract:start */
.rp-batch{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:16px;border:1px solid #c7d2fe;border-radius:8px;background:#f6f8ff}
.rp-batch strong{font-size:14px;color:#1e293b}
.rp-batch p{font-size:12px;margin-top:4px!important}
.rp-batch .button{flex-shrink:0}
.rp-muted{font-size:11px!important;color:#64748b!important}
.rp-dialog{width:min(700px,calc(100vw - 32px));max-width:none}
.rp-dialog[open]{display:flex;flex-direction:column;overflow:hidden}
.rp-dialog .dp-dialog-head,.rp-dialog .dp-dialog-foot{flex-shrink:0}
.rp-dialog .dp-dialog-body{overflow-y:auto;min-height:0}
.rp-scope{display:grid;grid-template-columns:90px 1fr;gap:9px;padding:14px 16px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:7px;color:#475569;font-size:12px}
.rp-scope strong{color:#1e293b;font-weight:600;overflow-wrap:anywhere}
.rp-label{margin:20px 0 10px!important;font-size:13px;font-weight:600;color:#1e293b!important}
.rp-option{display:flex;flex-direction:row;align-items:flex-start;gap:10px;border:1px solid #dfe5ee;border-radius:7px;padding:14px;margin:9px 0;cursor:pointer;text-transform:none;letter-spacing:normal}
.rp-option:has(input:checked){background:#f6f8ff;border-color:#818cf8}
.rp-option:hover:not(:has(input:disabled)){border-color:#818cf8}
.rp-option:active:not(:has(input:disabled)){background:#eef2ff}
.rp-option:has(input:disabled){cursor:not-allowed;opacity:.55}
.rp-option input{appearance:auto;flex:0 0 16px;width:16px;height:16px;min-height:16px;margin:3px 0 0;padding:0;accent-color:#1e40af}
.rp-option span{display:block;min-width:0}
.rp-option strong{display:block;font-size:13px;color:#1e293b}
.rp-option small{display:block;font-size:12px;line-height:1.65;color:#64748b;margin-top:4px}
.rp-process{display:flex;flex-wrap:wrap;justify-content:space-between;gap:7px;padding:13px 0;margin-top:8px;color:#475569;font-size:11px}
.rp-process b{color:#94a3b8}
.rp-log{border:1px solid #dce3f0;background:#fff;border-radius:8px;overflow:hidden}
.rp-log-head{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px 16px;background:#f8faff;border-bottom:1px solid #e2e8f0}
.rp-log-head strong{font-size:13px;color:#1e293b}
.rp-log-head small{display:block;font-size:11px;color:#64748b;margin-top:3px;overflow-wrap:anywhere}
.rp-log-head .dp-badge{margin:0}
.rp-stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;padding:16px}
.rp-stats span{font-size:11px;color:#64748b}
.rp-stats strong{display:block;font-size:23px;color:#1e40af;margin-top:3px;font-weight:600;overflow-wrap:anywhere}
.rp-stats .rp-orange{color:#b45309}
.rp-summary{padding:0 16px 14px;color:#64748b;font-size:11px;line-height:1.8;overflow-wrap:anywhere}
.rp-summary b{color:#1e293b;font-weight:600}
.rp-tabs{display:flex;flex-wrap:wrap;gap:16px;padding:0 16px 12px}
.rp-tabs .dp-link[aria-selected="true"]{font-weight:600;text-decoration:underline;text-underline-offset:5px}
.rp-log table{width:100%;border-collapse:collapse;min-width:850px}
.rp-log th,.rp-log td{font-size:12px;text-align:left;padding:10px 16px;border-top:1px solid #e8edf3;line-height:1.6;overflow-wrap:anywhere}
.rp-log th{position:static;background:#f8fafc;color:#94a3b8;font-size:11px;font-weight:600}
.rp-log td{color:#475569}
.rp-log-wrap{overflow:auto}
.rp-log .dp-link{font-size:11px}
.rp-pager{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 16px;border-top:1px solid #e8edf3;color:#64748b;font-size:11px}
.rp-trace{list-style:none;margin:0;padding:0}
.rp-trace li{position:relative;padding:0 0 18px 34px;border-left:1px solid #dbe3ef;margin-left:10px}
.rp-trace li:last-child{border:0;padding-bottom:0}
.rp-trace i{font-style:normal;position:absolute;left:-11px;top:0;width:22px;height:22px;display:grid;place-items:center;background:#e9f6f0;color:#047857;border:1px solid #cceedd;border-radius:50%;font-size:11px}
.rp-trace strong{font-size:13px;color:#1e293b}
.rp-trace p{font-size:12px;margin:5px 0;color:#64748b}
.rp-trace li[data-state="BLOCKED"] i{background:#fffbeb;color:#b45309;border-color:#fde6b0}
.rp-trace li[data-state="ERROR"] i{background:#fff1f2;color:#be123c;border-color:#fecdd3}
.rp-trace li[data-state="PENDING"] i,.rp-trace li[data-state="SKIPPED"] i{background:#f1f5f9;color:#94a3b8;border-color:#e2e8f0}
.rp-trace li[data-state="RUNNING"] i{background:#eff6ff;color:#1e40af;border-color:#bfdbfe}
.rp-source{margin:18px 0 0;padding:12px;border:1px solid #e2e8f0;border-radius:6px;background:#f8fafc;font-size:12px;overflow-wrap:anywhere}
.rp-source blockquote{margin:8px 0 0;padding:9px 12px;border-left:2px solid #cbd5e1;background:#fff;color:#475569;white-space:pre-wrap}
.rp-batch :is(button,a):focus-visible,.rp-log :is(button,a):focus-visible,.rp-dialog :is(button,input,a):focus-visible{outline:2px solid #3b82f6;outline-offset:3px}
.rp-batch .button:disabled,.rp-log button:disabled,.rp-dialog button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.rp-batch[hidden],.rp-log[hidden],.rp-dialog [hidden],.rp-log [hidden]{display:none!important}
@media(max-width:760px){.rp-batch{align-items:flex-start;flex-direction:column}.rp-stats{grid-template-columns:repeat(3,minmax(0,1fr))}.rp-process{justify-content:flex-start}.rp-scope{grid-template-columns:70px 1fr}.rp-dialog .dp-dialog-foot{align-items:flex-start;flex-direction:column}.rp-log-head{align-items:flex-start;flex-wrap:wrap}.rp-pager{flex-wrap:wrap}}
/* paper-reprocess-contract:end */
```
### S-6：完整DOM骨架与动态节点规则
- 插入位置见注释。模板中的文本全部服务端数据用textContent，禁止innerHTML拼接原文/作者/原因；动态表格td只用已有 `.dp-title/.dp-sub/.dp-badge/.dp-ok/.dp-warn/.dp-link/.dp-empty`，不添加inline style。运行字段使用 `—` 表示未取得，不能假装0。
```html
<!-- S-1: dp-filters 之后、dp-selection 之前 -->
<div class="rp-batch" data-rp="batchBar">
  <div><strong>重新处理整个采集任务</strong><p data-rp="scopeText">请选择一个执行任务</p><p class="rp-muted">覆盖本任务全部页；包含当初未建档的作者。其他筛选和勾选不缩小处理范围。</p></div>
  <button class="button primary" type="button" data-rp-action="open" disabled>重新解析并补录本批</button>
</div>
<!-- S-3: batchBar 之后；没有记录则 hidden -->
<section class="rp-log" data-rp="runPanel" aria-label="论文重新处理记录" hidden>
  <div class="rp-log-head"><div><strong data-rp="runTitle"></strong><small data-rp="runScope"></small></div><div class="dp-actions"><span class="dp-badge" data-rp="runState"></span><button class="dp-link" type="button" data-rp-action="cancel" hidden>取消任务</button><button class="dp-link" type="button" data-rp-action="retry" hidden>重试未完成项</button><button class="dp-link" type="button" data-rp-action="history">历史记录</button></div></div>
  <div class="rp-stats"><div><span>本批论文</span><strong data-rp="papersTotal">—</strong></div><div><span>已处理论文</span><strong data-rp="papersProcessed">—</strong></div><div><span>新增档案</span><strong data-rp="newRecords">—</strong></div><div><span>补齐已有档案</span><strong data-rp="repairedRecords">—</strong></div><div><span>实际准入</span><strong data-rp="admitted">—</strong></div><div><span>待核验档案</span><strong class="rp-orange" data-rp="pendingVerification">—</strong></div></div>
  <div class="rp-summary" role="status" aria-live="polite" data-rp="summary"></div>
  <div class="rp-summary"><progress class="dp-meter" data-rp="progress" aria-label="本批论文处理进度" max="1" value="0"></progress><span data-rp="phase"></span></div>
  <div class="rp-tabs" role="tablist" aria-label="处理结果维度"><button class="dp-link" type="button" role="tab" aria-selected="true" aria-controls="rpPaperResults" id="rpPaperTab" data-rp-action="papers">论文记录</button><button class="dp-link" type="button" role="tab" aria-selected="false" aria-controls="rpTargetResults" id="rpTargetTab" data-rp-action="targets" tabindex="-1">档案记录</button></div>
  <div id="rpPaperResults" class="rp-log-wrap" role="tabpanel" aria-labelledby="rpPaperTab"><table><thead><tr><th>论文 / 原执行</th><th>首次解析</th><th>本次解析</th><th>变化 / 原因</th><th>步骤</th><th>操作</th></tr></thead><tbody data-rp="paperRows"></tbody></table></div>
  <div id="rpTargetResults" class="rp-log-wrap" role="tabpanel" aria-labelledby="rpTargetTab" hidden><table><thead><tr><th>姓名 / 邮箱</th><th>档案变化</th><th>核验结论</th><th>卡住步骤 / 原因</th><th>操作</th></tr></thead><tbody data-rp="targetRows"></tbody></table></div>
  <footer class="rp-pager"><button class="button secondary small" type="button" data-rp-action="previous" disabled>上一页</button><span data-rp="pageInfo"></span><button class="button secondary small" type="button" data-rp-action="next" disabled>下一页</button></footer>
  <div class="dp-error" role="alert" data-rp="error" hidden></div>
</section>
<!-- S-2: 与现有 discoveryPaperDelete 同级，在 taskProgressModal 内 -->
<dialog id="paperReprocessConfirm" class="dp-dialog rp-dialog" aria-labelledby="rpConfirmTitle">
  <div class="dp-dialog-head"><div><h3 id="rpConfirmTitle">重新解析并补录本批</h3><p class="dp-muted">使用最新规则，处理此任务全部留存原文。</p></div><button class="button small" type="button" data-rp-action="close-confirm" aria-label="关闭确认">×</button></div>
  <div class="dp-dialog-body"><div class="rp-scope"><span>执行任务</span><strong data-rp="confirmExecution"></strong><span>范围</span><strong data-rp="confirmScope"></strong><span>Apollo设置</span><strong data-rp="confirmApollo"></strong></div><p class="rp-label">执行方式</p>
    <label class="rp-option"><input type="radio" name="paperReprocessMode" value="AUDIT" checked><span><strong>仅解析对照</strong><small>保存解析差异，不补录专家、不更新档案、不请求外部服务。</small></span></label>
    <label class="rp-option"><input type="radio" name="paperReprocessMode" value="ENFORCE"><span><strong>补录 / 补齐并自动重审</strong><small>明确身份后补录或补齐档案，再按现有规则重审；使用已保存的免费 / 付费设置。</small></span></label>
    <div class="rp-process"><span>1 读取原文</span><b>→</b><span>2 重新解析</span><b>→</b><span>3 身份关联</span><b>→</b><span>4 补录 / 补齐</span><b>→</b><span>5 资格重审</span><b>→</b><span>6 保存结果</span></div>
    <p class="dp-warning">按执行任务处理全部页。关闭页面继续后台执行；取消不会撤销已经补录、补齐或准入的结果。</p><div class="dp-error" role="alert" data-rp="confirmError" hidden></div>
  </div><div class="dp-dialog-foot"><span data-rp="confirmHint">正在读取范围…</span><div class="dp-actions"><button class="button secondary" type="button" data-rp-action="close-confirm">取消</button><button class="button primary" type="button" data-rp-action="start" disabled>确认执行</button></div></div>
</dialog>
<!-- S-4: 逐项详情与历史均复用此容器；历史时body放table/分页，详情时放下面模板 -->
<dialog id="paperReprocessDetail" class="dp-dialog rp-dialog" aria-labelledby="rpDetailTitle"><div class="dp-dialog-head"><div><h3 id="rpDetailTitle">逐项处理记录</h3><p class="dp-muted" data-rp="detailSubtitle"></p></div><button class="button secondary small" type="button" data-rp-action="close-detail">关闭</button></div><div class="dp-dialog-body" data-rp="detailBody"></div></dialog>
<template id="rpTraceTemplate"><ol class="rp-trace"><li data-state="PENDING"><i>1</i><strong></strong><p></p></li></ol><div class="rp-source"><strong>原文证据</strong><p class="rp-muted"></p><blockquote></blockquote><button class="dp-link" type="button" data-rp-action="open-source">查看原文</button></div><div class="dp-actions"><button class="dp-link" type="button" data-rp-action="open-verification">查看逐人核验</button></div></template>
```
- trace状态到data-state映射固定：NOT_STARTED→PENDING，SUCCESS→SUCCESS，INSUFFICIENT→BLOCKED，CANCELLED→SKIPPED，其他RUNNING/SKIPPED/ERROR同名；中文仍分别显示未开始/成功/证据不足/已取消，不能混淆。
- 每个li从模板复制并设置页序1..6，strong=步骤名，p=中文状态/数量/原因；动态纸张行6列、档案行5列。历史/详情返回关闭时焦点回原触发按钮；native dialog Escape只关闭，不取消任务。
- 所有新增DOM由S-1..S-4/S-6覆盖；设置/删除/专家重审原DOM不动。禁止复制预览的假数据、mock背景、toast或inline大号统计样式。


## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
### 前端样式盘点
- 完整改动前DOM+CSS逐字摘录：`paper-reprocess-evidence/frontend-before.md`；该附件是此计划审计组成部分，不是“参考风格”。现有 `.dp-root` 12px/line-height1.6；正文#475569；标题#1e293b；链接#1e40af；灰#64748b；border#e2e8f0。
- 复用class：`.dp-heading/.dp-actions/.dp-error/.dp-muted` 在styles.css:13373–13388，`.dp-link/.dp-badge/.dp-dialog/.dp-dialog-head/.dp-dialog-foot` 在13399–13436。hover蓝#1e3a8a、active#172554、focus2px #3b82f6；disabled opacity .45。
- 新设计基准来自已展示的 `docs/mockups/paper-reprocess-preview/preview.css` 和线上静态预览。新增入口gap20/padding16/radius8；dialog width min(700px,100vw-32px)；头16px20px，脚13px20px沿用dp。所有新增按钮自身复用既有button；label的flex-direction必须显式row。
- `index.html:1342–1361` 为论文页DOM；`discovery-papers.js:12–31`已有root/api/makeUrl/epoch/AbortController；`app.js:2474–2490`负责mount和visible状态（X-9）。新增子组件随同mount/suspend/dispose，不另开全局永久timer。
- `index.html:11,2525`现有资源版本为20261031-mailbox-polish；增加脚本与本次涉及资源cache-buster，不替换其他团队版本语义。
- 现有class均不就地修改，只追加`.rp-*`；全量使用点回执frontend.txt，禁止改通用label/button/p来解决新弹窗布局。

## 实现方案

1. **挂载（I-1/I-4；S-1/S-6）**：新增 `window.DiscoveryPaperReprocess.mount({root,api,makeUrl,openPaper,openVerification,onStatus})`；discovery-papers组件提供当前execution变化、show/suspend/dispose事件。app.js仅传既有openVerification接缝，不重写整份任务页。使用相同认证api与request中止/epoch模式。
2. **入口（I-1/I-2；S-1/S-2）**：具体执行任务选中后GET preview；未选提示“请选择一个执行任务”，执行中源任务提示“采集任务尚未结束”；上方筛选与勾选变更不能改整批范围。确认展示真实论文数/缺原文数与免费搜索/付费详情开关，主按钮默认disabled直到范围成功取得。一次点击立刻进入submitting状态，生成requestKey只一次，回runId立即显示后台进度。
3. **恢复（I-1/I-2；S-3）**：可见期间每5秒轮询活动任务；终态停止轮询；关闭清除timer和请求，重新显示重新GET。若API错误保留最后成功快照并显示“更新失败，显示上次结果”，不能显示“已完成”；重连根据服务端状态恢复。历史列表默认20条，可打开非活动任务且不误恢复到其他批次。
4. **日志（I-3；S-3/S-4）**：首次关联与本次关联同时显示，原first统计不覆盖；newRecords、repairedRecords、admitted、pendingVerification独立。辅助摘要注明“准入按档案计数，不等于去重人数”，并显示无变化/冲突/保护/失败/取消、候选同步失败、本批实际处理进度。AUDIT展示“仅对照，未应用”及wouldCreate/wouldRepair，业务卡片保持0。论文分页与档案分页分别维护，页大小20。
5. **逐项详情（I-3/I-4；S-4/S-6）**：固定6步：读取、解析、身份、补档、资格、保存；step1/2包含record/attempt/页数/版本，step3原文证据，step4原/新档案与receipt，step5按真实runId/itemId打开既有8步核验。无核验item时显示“未进入资格重审：原因”，不造链接。查看原文调用existing openPaper(ref)，只读不自动分析。
6. **交互和样式（I-2/I-4；S-1..S-6）**：完整复制CSS和DOM；禁inline style；输入未准备时点击、双击、Escape、焦点回归、手机宽屏横向滚动必须覆盖。index脚本顺序reprocess在discovery-papers之前；更新styles/app/相关JS版本，避免缓存旧DOM。
   - 原8步`openDetail(runId,itemId,trigger)`通过既有DiscoveryRecheck模块只读公开，app只转发；保留其ownership/lifecycle，不复制引擎。资源版本变更同步直接受影响测试，删除过时的固定总数/全集拷贝断言，不重钉新总数。
7. **验证（I-1..I-4；S-1..S-6）**：运行Node测试；用真实后端测试数据截图1440px和390px，核验范围/按钮/数字/状态均来自API。沿用preview作为布局对照但不将示例100/12/8/6替换真实数。
8. **权威展示查询接缝（I-1/I-3）**：只读扩展既有Reprocess DTO/repository/service的summary与preview；pendingVerification/wouldCreate/wouldRepair/unchanged/conflict/protected/syncFailure/qualification/missing分类由持久TARGET/PAPER及其已关联核验项计算，明确论文/目标单位与可重叠子统计、totalKnown语义，不把解析身份数当准入。preview.missingSourcePapers按全source没有SAVED/AVAILABLE descriptor的record计数，沿用freeze相同选择条件，包含DELETED/SAVE_ERROR，不以旧archive NOT_STORED计数替代。HTTP不解析正文；不写业务、不新增策略/准入状态或共享列。前端不得拉全部item页自行聚合这些权威数；两类明细仍独立20条分页。原source筛选不缩scope；Apollo仅读现有已保存设置endpoint。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/resources/static/discovery-paper-reprocess.js` | 新增真实API与任务面板/弹窗/恢复组件 |
| `src/main/resources/static/discovery-papers.js` | 挂载/暂停/销毁/执行任务筛选联动；复用openPaper |
| `src/main/resources/static/index.html` | 新增契约DOM与新脚本；相关静态资源版本同步 |
| `src/main/resources/static/styles.css` | 只追加S-5完整CSS |
| `src/test/js/discoveryPaperReprocess.test.js` | 异步/重复请求/恢复/分页/文案/只读测试 |
| `src/test/js/discoveryPapers.test.js` | 原下载/删除/保护/解析/开关回归 |
| `src/main/resources/static/app.js` | 窄接缝将既有逐人核验打开函数传给archive子组件 |
| `src/main/resources/static/discovery-recheck.js` | 只读公开既有openDetail，保留8步引擎与生命周期 |
| `src/test/js/discoveryRecheck.test.js` | 真实公开入口的上下文/生命周期行为回归 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt` | 权威只读summary/preview展示字段及单位/未知语义 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` | 基于持久结果与关联核验的只读分类汇总、全source缺descriptor记录数 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt` | 只读preview/summary映射，不改写路径、策略或生命周期 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt` | 跨页/分类重叠/未知/缺源的真实SQL汇总行为 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt` | preview缺源与权威展示契约行为 |
| `src/test/js/discoveryReview.test.js` | 删除被新脚本直接触发的固定资源总数/全集拷贝断言，保留真实行为 |
| `src/test/js/mailboxChatBehavior.test.js` | 同步直接受影响资源测试，不重钉新总数/全集 |
| `src/test/js/mailboxGroupPush.test.js` | 同步直接受影响资源测试，不重钉新总数/全集 |
| `src/test/js/meetingConfirmationAssets.test.js` | 同步直接受影响资源测试，不重钉新总数/全集 |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 同步直接受影响资源测试，不重钉新总数/全集 |

共 19 个文件。新增范围仅UI/其权威只读查询与直接受影响测试，业务写算法、S-1..S-6及验收不变；未列文件禁止扩改，新增问题先记录证据并修订范围。

## 验收标准

- I-1：双击、超时重送、关闭重开均一个requestKey/run；121跨页不受搜索/勾选限制；旧源迟到响应丢弃。
- I-2：关闭/Escape/切页无cancel POST；显式cancel只有一次；API失败不能启动；免费/付费设置仅展示，不在本页偷偷保存。
- I-3：AUDIT实际计数全0；ENFORCE卡片准确读回；缺源/保护/冲突/待核验/错误可分辨；first与current两个数均可见。
- I-4/S-1..S-6：提取styles内契约标记块与本计划S-5逐字比较；DOM class/层级与S-6一致；新增节点无inline style/未声明class；原通用规则diff为0。
- X-5/X-6/X-8/X-9：后端→新组件→原原文/原8步详情回跳不丢任务；运行 `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js`。
- 截图是独立证据：1440/390宽、缩放100%/125%、长中文原因、空数据、进行中、失败、完成均无操作按钮被滚出不可达。
- 补充必跑：`node --test src/test/js/*.test.js`（覆盖直接受影响资源测试）；JDK11 `mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest -DpaperReprocessIt=true -DmysqlIt=true`，真实SQL覆盖跨页权威汇总和preview缺源；不替代浏览器验收。

## 人工验收清单

### A-1: 整批与真实数
- 前置条件：测试源任务121篇；仅选当前页1条且搜索命中1篇；1440px窗口。
- 操作步骤：1. 打开整批弹窗；2. 检查范围；3. AUDIT执行；4. 查看两类记录分页。
- 预期结果：范围121；卡片真实数据不含预览固定3185/100；实际新增/补齐/准入0；拟变化另外显示；第1/7页且每页20。
- 覆盖：I-1/I-2/I-3，S-1/S-2/S-3/S-6，X-5/X-9

### A-2: 关闭恢复及原入口
- 前置条件：ENFORCE任务运行中；有1个真实verification item。
- 操作步骤：1. 关闭弹窗和深度发现；2. 重开同任务；3. 查看资格详情；4. 回专家审核。
- 预期结果：同runId继续展示；资格打开原8步；原专家重审按钮仍可用；关闭没有取消后台。
- 覆盖：I-1/I-2/I-3，S-3/S-4，X-6/X-8，保持不变②④

### A-3: 样式与错误
- 前置条件：390px和1440px窗口，后端stub可返回401、500、冲突与超长中文原因。
- 操作步骤：1. 打开确认/详情；2. 滚动；3. 键盘Tab、Escape；4. 触发错误。
- 预期结果：700px上限dialog、8px入口圆角、14px入口标题、23px统计字；390px统计3列；头脚始终可见；焦点2px蓝框；失败提示保留上次数据且不造0。
- 覆盖：I-2/I-4，S-1..S-6，保持不变①

### A-4: 旧功能与配置
- 前置条件：有受保护论文、可下载论文；Apollo保存search=true/detail=false。
- 操作步骤：1. 使用原单篇对照/下载/删除预览/取消保护；2. 打开新确认。
- 预期结果：旧操作规则不变；新弹窗明确免费搜索开启/付费详情关闭，保存配置revision不变；原文打开不自动运行分析。
- 覆盖：I-2/I-4，S-2/S-4，X-5/X-9，保持不变①③


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。

## 修正记录
- 2026-11-02：按用户后续必要授权，05扩为19文件以接通已private的原8步详情、补齐缺失权威只读summary/preview及直接受影响资源测试。05执行日志epoch1保留确切grep/read回执：字面cachekey检索0命中，语义检索找到固定资源19/全集断言；旧archive缺源统计仅NOT_STORED，不含DELETED/SAVE_ERROR。只读服务端统计替代前端全页派生，删除incidental断言而非重钉；所有CSS/DOM/浏览器/保护/无业务写要求不减，A10/A11见ledger。
