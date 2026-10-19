# 07 · 真实线上弹窗、日志与完整 CSS

日期2026-10-18；待实施。依赖01～06。此文件是**最终样式契约**；CSS必须逐字复制，不是示意。预览HTML是视觉证据，不能把其中演示数字、预览提示、禁用审核tab复制到产品。

## 需求描述

O-1：原深度发现配置区可启用Apollo，仍保留关键词、七个平台、高级选项、原开始/取消按钮和3个主tab。O-2：执行记录能看清直接/Apollo准入、已明确/未明确、待核验、错误和排队；点击可查每条依据、完整原因、历史采集量。O-3：加载、失败、旧历史及异步刷新均可解释。

必须保持N-1原弹窗布局与现有来源控件；N-2专家审核/审核记录可用；N-3其他任务原列与交互；N-4无演示数据、无前端自行判资格、无业务写入型“重试”。不改任务活动中心/专家列表/来源选择样式，不新建Dashboard。

## 关键不变量

### Invariant I-1: 真实数据口径
- Rule：全部新指标读06API，使用C-5字段；null显示“未记录”，失败显示错误，0仍为0。采集总处理/收录不能填准入栏。AUDIT明确“影子核验建议”，不能显示真实自动准入。
- Applies to：列表/详情/分页/下载量。
- Violation consequence：页面制造虚假的通过数。
- 来源：总I-8/I-9/I-10。

### Invariant I-2: 按执行隔离异步响应
- Rule：请求携带taskType、generation、executionId、filterSequence，四者匹配才写DOM；新页/新筛选取消旧请求。采集终态后只要核验active仍5s轮询；关闭/切主tab停止不必要请求，重开拉新快照；统计与当前页版本不一致整块刷新。
- Applies to：所有新fetch/计时器/展开缓存。
- Violation consequence：A任务结果出现在B任务，或已完成采集永不刷新核验。
- 来源：K-task-activity-execution-identity、K-execution-detail-running-needs-progress-log。

### Invariant I-3: 单滚动与可访问交互
- Rule：纵向只由原modal-content承担，表格仅横向滚动；表头实色非sticky；按钮键盘可用、tab aria关系完整；新增文本全部escapeHtml、URL仅http(s)；禁止截断HTML字符串/拼未经转义reason。
- Applies to：S-1～S-8。
- Violation consequence：重叠、不可读、错位/XSS。
- 来源：K-html-string-truncation-breaks-cells、K-panel-bg-token-is-translucent。

### Invariant I-4: 全局结构保持
- Rule：仅taskType=EXPERT_DISCOVERY给host加apv-logs，并生成11列；关闭/切任务移除此class并恢复原thead（含taskModalTrafficHead）、8/9列逻辑及缓存。主tabs不禁用；16个既有?v资源只改值，不变数量/顺序，不新增JS文件。
- Applies to：open/close/renderRunList/taskLaunchConfigs。
- Violation consequence：别的任务UI被一起改坏或缓存不更新。
- 来源：K-task-launch-config-registration、K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs。

## 样式契约

### S-1: 容器与边界
- 复用：`.modal-overlay/.modal-content/.task-modal/.modal-header/.modal-body`（styles.css:4483–4533）、深度发现宽度规则:12675–12708、原 `.task-modal-logs-title/-indicator` :4749–4768。1180px最大宽度、18px圆角、80vh最大高度不改。
- 新增：原日志wrapper仅把原inline display:flex/flex-direction:column/gap:10px移到`.apv-log-host`完整等值规则；`.apv-logs`由JS仅深度发现挂载。共用class不原地改；新规则作用域固定。
- DOM：采用下文骨架S-1；index里原静态thead保留作其他任务模板，仅新增id `taskModalRunHead`；深度发现打开时替换为下文11列，关闭/切任务恢复。原id `taskModalRunBody`不更名。apv-runs只随深度发现挂载。
- 禁止：顶层统计卡片、新侧栏、重做来源选项、修改任何全局.data-table/modal/button规则。

### S-2: Apollo开关
- 复用：原配置区gap16及原按钮（styles.css:4662、802–879），新增完整CSS见规范块。
- DOM：下文S-2，插原高级选项后；checkbox14×14且min-height14，label强制row，不受全局label column/input34影响。
- 状态：preload加载时checkbox禁用；ready才按capability默认值设置，后续轮询不得重置用户选择。enabled=false显示禁用与“资格核验未启用”；ready=false显示具体reason，不影响原其他task。
- 禁止：来源checkbox统一缩小、把所有input全局改14px。

### S-3: 执行主行
- 复用`.data-table.compact`、`.badge`（styles.css:3421、1054），status badge只允许既有ok/warn/error/空class。
- DOM：下文S-3，11列固定顺序；箭头按钮具aria-expanded；行点击等同按钮，点击行内其他控件阻止冒泡。时间/耗时属于采集，核验状态在状态格第二行显示。
- 值：旧无账本用“未记录”；不可用读取显示“读取失败”，不渲染0。行内不显示新的饼图/进度条。

### S-4: 展开摘要与子tab
- DOM：下文S-4，summary必须在colspan11的展开行内。自动/直接/Apollo、明确性、pending/error/queue、同步待处理、Apollo漏斗分行，字号12/14固定。
- 交互：点击某数字跳逐条核验并设置route/result；subtab按左右方向键/Home/End切换，只有选中tab tabindex=0。主“专家审核/审核记录”保持原处理函数。
- 禁止：把待核验、错误、排队都叫“拒绝”；把Apollo搜索候选数作为准入数。

### S-5: 批次与采集历史
- DOM：下文S-5；准入表13列；每100激活档案一批，不复用论文batch号；页脚本次合计取runCounts，不能只加当前页。原因按钮展开3栏详情，完整label+code+人数，无ellipsis/max-height。
- 原采集日志7列读原task-progress `batchOnly=true`，只在深度发现的呈现中将通过/拒绝写“收录/未收录”，保留单位说明；完整原因details展开，禁止slice(0,3)。其他task renderBatchTable行为不动。

### S-6: 逐条筛选与分页
- DOM：下文S-6，路径/结果/搜索/清除/总数；8列固定。每页20，后端size范围已定义；上一页/下一页边界disabled；搜索300ms debounce并页号归0；筛选后total来自服务端。
- “Apollo辅助”路由包含参与辅助的pending/error/queued（看apolloEntered），不只准入40；“未明确”必须只19；DIRECT为直接准入。选项词汇不改。

### S-7: 证据和下载量
- DOM：下文S-7；证据同表下方colspan8展开，四块内容使用白底、1px边框、7px圆角。不是另开一个浮层。
- HTTP下载量默认details收起，保留所有来源/站点/超限计数，注明仅采集流量。没有核验流量读数不谎称包含Apollo；证据必须同时显示来源/采集日期/当前或历史，缺值明确“未取得可靠证据”。

### S-8: 空态、错误、键盘和响应式
- DOM：下文S-8，加载/空/未记录占位按11/13/8/7准确colspan；失败有role=alert与“重试读取”。重试只GET，不触发重新核验。
- 样式：focus-visible 2px #1e40af offset3；disabled opacity.45；≤700证据/批次详情单列；≤640 overlay padding10。所有新增button/input/select状态在CSS块给出；原button复用现有hover/active，局部补focus/disabled。
- 禁止：新inline style、未列class、任何同名全局规则改写。原未触及配置/来源inline样式维持基线，不借此全量清理。

### 全量新增 CSS（唯一权威版本）

复制下面完整块到styles.css末尾；禁止增删属性/改变值/压缩改写。已有class其它使用点见shared-style-uses回执，本契约均为派生局部覆盖。

```css
/* apollo-verification-contract:start */
.task-modal-logs.apv-log-host {
    display: flex;
    flex-direction: column;
    gap: 10px;
}
#taskProgressModal[data-discovery-review="true"] .apv-option {
    padding: 10px 12px;
    border: 1px solid var(--panel-border);
    border-radius: 7px;
    background: #f8fafc;
}
#taskProgressModal[data-discovery-review="true"] .apv-option label {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    gap: 7px;
    color: #1e293b;
    font-size: 12px;
    font-weight: 600;
    text-transform: none;
    letter-spacing: normal;
}
#taskProgressModal[data-discovery-review="true"] .apv-option input[type="checkbox"] {
    width: 14px;
    height: 14px;
    min-width: 14px;
    min-height: 14px;
    flex: 0 0 14px;
    margin: 0;
    padding: 0;
    accent-color: #1e40af;
    cursor: pointer;
}
#taskProgressModal[data-discovery-review="true"] .apv-option p {
    margin: 5px 0 0 21px;
    color: #475569;
    font-size: 11px;
    line-height: 1.7;
}
#taskProgressModal[data-discovery-review="true"] .apv-rules {
    margin-top: 6px;
    color: #475569;
    font-size: 11px;
    line-height: 1.8;
}
#taskProgressModal[data-discovery-review="true"] .apv-rules summary {
    color: #1e40af;
    cursor: pointer;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .task-modal-log-table-wrapper {
    max-height: none;
    overflow: visible;
}
#taskProgressModal[data-discovery-review="true"] .apv-outer-scroll,
#taskProgressModal[data-discovery-review="true"] .apv-scrollarea {
    max-width: 100%;
    overflow-x: auto;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .data-table th {
    position: static;
    background: #f8fafc;
    font-size: 11px;
    white-space: nowrap;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .data-table td {
    font-size: 12px;
}
#taskProgressModal[data-discovery-review="true"] .apv-runs {
    min-width: 1060px;
}
#taskProgressModal[data-discovery-review="true"] .apv-arrow-cell {
    width: 28px;
    text-align: center;
}
#taskProgressModal[data-discovery-review="true"] .apv-run {
    background: #fff;
    cursor: pointer;
}
#taskProgressModal[data-discovery-review="true"] .apv-run:hover {
    background: #f8fafc;
}
#taskProgressModal[data-discovery-review="true"] .apv-run:active {
    background: #eef2ff;
}
#taskProgressModal[data-discovery-review="true"] .apv-run-detail > td {
    padding: 0;
    background: #fff;
}
#taskProgressModal[data-discovery-review="true"] .apv-number {
    color: #1e40af;
    font-weight: 600;
}
#taskProgressModal[data-discovery-review="true"] .apv-green { color: #059669; }
#taskProgressModal[data-discovery-review="true"] .apv-amber { color: #b45309; }
#taskProgressModal[data-discovery-review="true"] .apv-red { color: #be123c; }
#taskProgressModal[data-discovery-review="true"] .apv-small {
    color: #475569;
    font-size: 11px;
    line-height: 1.7;
}
#taskProgressModal[data-discovery-review="true"] .apv-block { display: block; }
#taskProgressModal[data-discovery-review="true"] .apv-link {
    margin: 0;
    padding: 0;
    border: 0;
    background: transparent;
    color: #1e40af;
    font: inherit;
    cursor: pointer;
    text-align: inherit;
    text-decoration: none;
}
#taskProgressModal[data-discovery-review="true"] .apv-link:hover:not(:disabled) {
    color: #1e3a8a;
    text-decoration: underline;
}
#taskProgressModal[data-discovery-review="true"] .apv-link:active:not(:disabled) {
    color: #172554;
}
#taskProgressModal[data-discovery-review="true"] .apv-summary {
    padding: 12px 12px 8px;
    background: #fff;
    color: #1e293b;
    font-size: 12px;
    line-height: 1.9;
}
#taskProgressModal[data-discovery-review="true"] .apv-summary strong { font-size: 14px; }
#taskProgressModal[data-discovery-review="true"] .apv-line {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 22px;
}
#taskProgressModal[data-discovery-review="true"] .apv-state-row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 18px;
    padding: 7px 0;
}
#taskProgressModal[data-discovery-review="true"] .apv-flow {
    padding-top: 5px;
    border-top: 1px solid var(--line);
    color: #475569;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-subtabs {
    display: flex;
    gap: 18px;
    padding: 0 12px;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--panel-border);
    background: #fff;
}
#taskProgressModal[data-discovery-review="true"] .apv-subtab {
    margin: 0;
    padding: 9px 0;
    border: 0;
    border-bottom: 2px solid transparent;
    background: transparent;
    color: #475569;
    font-family: inherit;
    font-size: 12px;
    cursor: pointer;
}
#taskProgressModal[data-discovery-review="true"] .apv-subtab:hover:not(:disabled) { color: #1e40af; }
#taskProgressModal[data-discovery-review="true"] .apv-subtab:active:not(:disabled) { background: #f8fafc; }
#taskProgressModal[data-discovery-review="true"] .apv-subtab[aria-selected="true"] {
    border-bottom-color: #1e40af;
    color: #1e40af;
    font-weight: 600;
}
#taskProgressModal[data-discovery-review="true"] .apv-inner { background: #fff; }
#taskProgressModal[data-discovery-review="true"] .apv-inner-note {
    padding: 8px 12px;
    background: #f8fafc;
    color: #475569;
    font-size: 11px;
    line-height: 1.7;
}
#taskProgressModal[data-discovery-review="true"] .apv-batches { min-width: 1020px; }
#taskProgressModal[data-discovery-review="true"] .apv-batches th,
#taskProgressModal[data-discovery-review="true"] .apv-batches td { padding: 8px; }
#taskProgressModal[data-discovery-review="true"] .apv-batches tfoot {
    background: #f8fafc;
    font-weight: 600;
}
#taskProgressModal[data-discovery-review="true"] .apv-batches small {
    display: block;
    color: #94a3b8;
    font-size: 10px;
    font-weight: 400;
}
#taskProgressModal[data-discovery-review="true"] .apv-batch-detail > td {
    padding: 10px 12px;
    background: #f8fafc;
}
#taskProgressModal[data-discovery-review="true"] .apv-detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr 1.1fr;
    gap: 15px;
}
#taskProgressModal[data-discovery-review="true"] .apv-detail-grid p {
    color: #475569;
    font-size: 11px;
    line-height: 1.8;
}
#taskProgressModal[data-discovery-review="true"] .apv-detail-grid strong { font-size: 11px; }
#taskProgressModal[data-discovery-review="true"] .apv-code {
    color: #475569;
    font: 10px/1.6 var(--font-mono);
    overflow-wrap: anywhere;
}
#taskProgressModal[data-discovery-review="true"] .apv-footnote {
    padding: 9px 12px;
    border-top: 1px solid var(--line);
    color: #475569;
    font-size: 11px;
    line-height: 1.7;
}
#taskProgressModal[data-discovery-review="true"] .apv-traffic {
    padding: 9px 12px;
    border-top: 1px solid var(--line);
    background: #f8fafc;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-traffic summary {
    color: #475569;
    font-weight: 500;
    cursor: pointer;
}
#taskProgressModal[data-discovery-review="true"] .apv-traffic p { margin: 6px 0; }
#taskProgressModal[data-discovery-review="true"] .apv-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
}
#taskProgressModal[data-discovery-review="true"] .apv-toolbar label {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 5px;
    margin: 0;
    color: #475569;
    font-size: 11px;
    text-transform: none;
    letter-spacing: normal;
}
#taskProgressModal[data-discovery-review="true"] .apv-toolbar select,
#taskProgressModal[data-discovery-review="true"] .apv-toolbar input {
    width: auto;
    height: 30px;
    min-height: 30px;
    padding: 5px 7px;
    border: 1px solid var(--panel-border);
    border-radius: 7px;
    background: #fff;
    color: #1e293b;
    font-family: inherit;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-toolbar input { width: 165px; }
#taskProgressModal[data-discovery-review="true"] .apv-toolbar :is(input, select):hover:not(:disabled) {
    border-color: #94a3b8;
}
#taskProgressModal[data-discovery-review="true"] .apv-toolbar :is(input, select):focus {
    border-color: #1e40af;
    box-shadow: 0 0 0 3px rgba(30, 64, 175, 0.1);
}
#taskProgressModal[data-discovery-review="true"] .apv-count {
    margin-left: auto;
    color: #475569;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-records { min-width: 950px; }
#taskProgressModal[data-discovery-review="true"] .apv-records .badge { font-size: 10px; }
#taskProgressModal[data-discovery-review="true"] .apv-records small {
    color: #94a3b8;
    font-size: 10px;
}
#taskProgressModal[data-discovery-review="true"] .apv-pagination {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    color: #475569;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-evidence {
    padding: 12px;
    background: #f8fafc;
}
#taskProgressModal[data-discovery-review="true"] .apv-evidence-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
}
#taskProgressModal[data-discovery-review="true"] .apv-evidence-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}
#taskProgressModal[data-discovery-review="true"] .apv-evidence-item {
    padding: 9px 11px;
    border: 1px solid var(--panel-border);
    border-radius: 7px;
    background: #fff;
    font-size: 11px;
    line-height: 1.7;
    overflow-wrap: anywhere;
}
#taskProgressModal[data-discovery-review="true"] .apv-evidence-item p { color: #475569; }
#taskProgressModal[data-discovery-review="true"] .apv-evidence-item b {
    display: block;
    margin-bottom: 3px;
    font-size: 11px;
}
#taskProgressModal[data-discovery-review="true"] .apv-empty {
    padding: 20px;
    text-align: center;
    color: #475569;
}
#taskProgressModal[data-discovery-review="true"] .apv-alert {
    margin: 8px 12px;
    padding: 8px 10px;
    border: 1px solid var(--error-border);
    border-radius: 7px;
    background: #fff1f2;
    color: #be123c;
    font-size: 11px;
    line-height: 1.7;
}
#taskProgressModal[data-discovery-review="true"] :is(.apv-option, .apv-logs) :focus-visible {
    outline: 2px solid #1e40af;
    outline-offset: 3px;
}
#taskProgressModal[data-discovery-review="true"] :is(.apv-option, .apv-logs) :is(button, input, select):disabled {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
#taskProgressModal[data-discovery-review="true"] .apv-option input[type="checkbox"]:disabled {
    cursor: not-allowed;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .apv-link:disabled:hover {
    color: #1e40af;
    text-decoration: none;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .apv-subtab:disabled:hover {
    color: #475569;
    background: transparent;
}
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled,
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled:hover,
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled:active {
    background-color: rgba(30, 64, 175, 0.07);
    border-color: rgba(30, 64, 175, 0.12);
    color: #1e40af;
    opacity: 0.45;
    transform: none;
    box-shadow: none;
}
@media (max-width: 700px) {
    #taskProgressModal[data-discovery-review="true"] .apv-detail-grid,
    #taskProgressModal[data-discovery-review="true"] .apv-evidence-grid { grid-template-columns: 1fr; }
    #taskProgressModal[data-discovery-review="true"] .apv-count { margin-left: 0; }
    #taskProgressModal[data-discovery-review="true"] .apv-toolbar input { width: 140px; }
}
@media (max-width: 640px) {
    #taskProgressModal[data-discovery-review="true"] { padding: 10px; }
    #taskProgressModal[data-discovery-review="true"] .apv-subtabs { gap: 12px; }
}
/* apollo-verification-contract:end */
```

### 完整目标 DOM（结构权威，数据占位必须转义）

重复行/重复reason只复制对应骨架并换数据；不许新增自由class。{{existingStatusClass}}/{{existingResultClass}}仅可映射已有badge的ok/warn/error/空值。真实输出没有`{{...}}`占位文字；非深度发现仍用原DOM。所有新增/修改元素由S-1～S-8注释区域覆盖，不允许inline onclick。hidden用于收起，aria状态由同一个render state驱动。

```html
<!-- S-1：仅给原执行记录wrapper新增id/class，原inline display/flex/gap移到apv-log-host -->
<div id="discoveryVerificationLogHost" class="task-modal-logs apv-log-host">
  <h4 class="task-modal-logs-title"><span class="task-modal-logs-title-indicator"></span>执行记录（点击行展开批次明细）</h4>
  <div class="task-modal-log-table-wrapper"><div class="apv-outer-scroll">
    <table class="data-table compact apv-runs">
      <thead id="taskModalRunHead"><tr>
        <th class="apv-arrow-cell"></th><th>开始时间</th><th>触发</th><th>状态</th><th>档案数</th>
        <th>自动准入</th><th>其中 Apollo</th><th>已明确</th><th>未明确</th><th>下载量</th><th>耗时</th>
      </tr></thead>
      <tbody id="taskModalRunBody">
        <!-- S-3：每次执行一行，原run-row/data-execution-id保留；row本身不抢tab焦点 -->
        <tr class="run-row apv-run" data-execution-id="{{executionId}}">
          <td class="apv-arrow-cell"><button type="button" class="apv-link" data-action="toggle-run" aria-expanded="false" aria-controls="detail-row-{{executionId}}" aria-label="展开执行记录">▶</button></td>
          <td>{{startedAt}}</td><td>{{triggerLabel}}</td>
          <td><span class="badge {{existingStatusClass}}">{{collectionLabel}}</span><small class="apv-small apv-block">{{verificationLabel}}</small></td>
          <td>{{profiles}}</td><td class="apv-number">{{autoAdmitted}}</td><td>{{apolloAdmitted}}</td>
          <td class="apv-green">{{clear}}</td><td class="apv-amber">{{unclear}}</td><td>{{collectionTraffic}}</td><td>{{collectionDuration}}</td>
        </tr>
        <!-- S-4：展开时插入，一次只展开一个execution -->
        <tr class="run-detail-row apv-run-detail" id="detail-row-{{executionId}}"><td colspan="11">
          <div class="apv-summary" aria-live="polite">
            <div class="apv-line"><span>自动准入 <strong class="apv-number">{{autoAdmitted}}</strong></span><span>直接准入 <button class="apv-link" type="button" data-action="filter-direct">{{directAdmitted}}</button></span><span>Apollo 准入 <button class="apv-link" type="button" data-action="filter-apollo">{{apolloAdmitted}}</button></span></div>
            <div class="apv-line"><span>已明确 <button class="apv-link" type="button" data-action="filter-clear">{{clear}}</button></span><span>未明确 <button class="apv-link" type="button" data-action="filter-unclear">{{unclear}}</button></span></div>
            <div class="apv-state-row"><button class="apv-link" type="button" data-action="filter-pending">待核验 {{pending}}</button><button class="apv-link" type="button" data-action="filter-errors">错误 {{errors}}</button><button class="apv-link" type="button" data-action="filter-queued">排队/核验中 {{queued}}</button><button class="apv-link" type="button" data-action="filter-protected">保护跳过 {{protected}}</button></div>
            <p class="apv-small">已准入，候选同步待处理 {{projectionPendingAndErrors}} · 统计更新时间 {{asOf}}</p>
            <div class="apv-flow">Apollo：进入辅助核验 {{apolloEntered}} → 已查询 {{apolloSearched}} → 返回候选 {{apolloCandidates}} → 确认本人 {{apolloMatched}}；请求 {{apolloHttpAttempts}} 次</div>
          </div>
          <nav class="apv-subtabs" role="tablist" aria-label="核验日志">
            <button class="apv-subtab" type="button" role="tab" id="apv-tab-batches-{{executionId}}" aria-controls="apv-batches-{{executionId}}" aria-selected="true" data-action="tab-batches">准入批次</button>
            <button class="apv-subtab" type="button" role="tab" id="apv-tab-records-{{executionId}}" aria-controls="apv-records-{{executionId}}" aria-selected="false" tabindex="-1" data-action="tab-records">逐条核验</button>
            <button class="apv-subtab" type="button" role="tab" id="apv-tab-collection-{{executionId}}" aria-controls="apv-collection-{{executionId}}" aria-selected="false" tabindex="-1" data-action="tab-collection">采集日志</button>
          </nav>
          <!-- S-5：准入批次，重复tr只换数据，不加class或style -->
          <section class="apv-inner" role="tabpanel" id="apv-batches-{{executionId}}" aria-labelledby="apv-tab-batches-{{executionId}}">
            <p class="apv-inner-note">按本次激活档案每 100 条分批；与论文采集批次独立。理由可能一条档案多项命中。</p>
            <div class="apv-scrollarea"><table class="data-table compact apv-batches">
              <thead><tr><th>批次</th><th>本批档案</th><th>自动准入</th><th>直接</th><th>Apollo</th><th>已明确</th><th>未明确</th><th>待核验</th><th>错误</th><th>排队</th><th>累计档案</th><th>原因</th><th>时间</th></tr></thead>
              <tbody>
                <tr><td><button class="apv-link" type="button" data-action="toggle-batch" data-batch="{{batchNumber}}" aria-expanded="false" aria-controls="apv-batch-detail-{{executionId}}-{{batchNumber}}">{{batchNumber}}</button></td><td>{{profiles}}</td><td>{{autoAdmitted}}</td><td>{{directAdmitted}}</td><td>{{apolloAdmitted}}</td><td>{{clear}}</td><td>{{unclear}}</td><td>{{pending}}</td><td>{{errors}}</td><td>{{queued}}</td><td>{{cumulativeProfiles}}</td><td><button class="apv-link" type="button" data-action="toggle-batch" data-batch="{{batchNumber}}">查看全部（{{reasonKinds}} 类）</button></td><td>{{updatedAt}}</td></tr>
                <tr class="apv-batch-detail" id="apv-batch-detail-{{executionId}}-{{batchNumber}}" hidden><td colspan="13"><div class="apv-detail-grid">
                  <div><strong>准入结果</strong><p>{{countsText}}</p><p>保护跳过 {{protected}}</p></div>
                  <div><strong>待核验原因</strong><p>{{reasonLabelAndCount}}</p><p class="apv-code">{{reasonCode}}</p></div>
                  <div><strong>接口与同步异常</strong><p>{{errorLabelAndCount}}</p><p class="apv-code">{{errorCode}}</p><button class="apv-link" type="button" data-action="filter-batch" data-batch="{{batchNumber}}">查看本批档案</button></div>
                </div></td></tr>
              </tbody>
              <tfoot><tr><td>本次合计</td><td>{{runProfiles}}</td><td>{{runAutoAdmitted}}</td><td>{{runDirect}}</td><td>{{runApollo}}</td><td>{{runClear}}</td><td>{{runUnclear}}</td><td>{{runPending}}</td><td>{{runErrors}}</td><td>{{runQueued}}</td><td colspan="3">保护跳过 {{runProtected}}</td></tr></tfoot>
            </table></div>
            <div class="apv-pagination"><button class="button small secondary" type="button" data-action="batch-prev" disabled>上一页</button><span>{{batchPageText}}</span><button class="button small secondary" type="button" data-action="batch-next">下一页</button></div>
          </section>
          <!-- S-6：逐条核验 -->
          <section class="apv-inner" role="tabpanel" id="apv-records-{{executionId}}" aria-labelledby="apv-tab-records-{{executionId}}" hidden>
            <div class="apv-toolbar">
              <label>路径<select data-filter="route" aria-label="核验路径"><option value="ALL">全部</option><option value="DIRECT">直接准入</option><option value="APOLLO">Apollo 辅助</option></select></label>
              <label>结果<select data-filter="result" aria-label="核验结果"><option value="ALL">全部</option><option value="CLEAR">已明确</option><option value="UNCLEAR">未明确</option><option value="PENDING">待核验</option><option value="ERROR">错误</option><option value="QUEUED">排队/核验中</option><option value="PROTECTED">保护跳过</option></select></label>
              <label>搜索<input type="search" data-filter="q" maxlength="100" placeholder="姓名 / 邮箱 / 机构" aria-label="搜索核验档案"></label>
              <button class="button small secondary" type="button" data-action="clear-filters">清除筛选</button><span class="apv-count">共 {{total}} 条档案</span>
            </div>
            <div class="apv-scrollarea"><table class="data-table compact apv-records"><thead><tr><th>姓名 / 邮箱</th><th>机构</th><th>核验路径</th><th>本次结论</th><th>缺失信息 / 原因</th><th>当前审核 / 同步</th><th>更新时间</th><th>证据</th></tr></thead><tbody>
              <tr><td>{{name}}<small class="apv-block">{{email}}</small></td><td>{{institution}}</td><td>{{routeLabel}}</td><td><span class="badge {{existingResultClass}}">{{resultLabel}}</span></td><td>{{missingFieldsAndReasons}}</td><td>{{currentDecisionAndProjection}}</td><td>{{updatedAt}}</td><td><button class="apv-link" type="button" data-action="toggle-evidence" data-item-id="{{itemId}}" aria-expanded="false" aria-controls="apv-evidence-{{itemId}}">查看依据</button></td></tr>
              <!-- S-7：就地展开，无新弹窗 -->
              <tr hidden id="apv-evidence-{{itemId}}"><td colspan="8"><div class="apv-evidence">
                <div class="apv-evidence-head"><strong>{{name}} · 核验依据</strong><button class="button small secondary" type="button" data-action="close-evidence" data-item-id="{{itemId}}">收起</button></div>
                <div class="apv-evidence-grid">
                  <div class="apv-evidence-item"><b>身份对应</b><p>{{identityConclusion}}</p><p>{{originalIdentity}}</p><a class="apv-link" href="{{safeSourceUrl}}" target="_blank" rel="noopener noreferrer">{{sourceTitle}}</a><p>{{evidenceTime}}</p></div>
                  <div class="apv-evidence-item"><b>博士学位</b><p>{{degreeEvidenceOrUnknown}}</p><p>{{sourceAndTime}}</p></div>
                  <div class="apv-evidence-item"><b>年龄与当前职称</b><p>{{ageAndTitleEvidenceOrUnknown}}</p><p>{{sourceAndTime}}</p></div>
                  <div class="apv-evidence-item"><b>Apollo 与处理记录</b><p>{{apolloPersonIdAndStages}}</p><p>{{attemptSummary}}</p><p class="apv-code">{{completeReasonCodes}}</p></div>
                </div>
              </div></td></tr>
            </tbody></table></div>
            <div class="apv-pagination"><button class="button small secondary" type="button" data-action="record-prev" disabled>上一页</button><span>{{recordPageText}}</span><button class="button small secondary" type="button" data-action="record-next">下一页</button></div>
          </section>
          <!-- S-5：原采集日志也用实色表头和横向容器，保留单位；完整原因用details -->
          <section class="apv-inner" role="tabpanel" id="apv-collection-{{executionId}}" aria-labelledby="apv-tab-collection-{{executionId}}" hidden>
            <p class="apv-inner-note">原采集统计：论文/记录处理与 RAW 收录，不等于资格准入。</p>
            <div class="apv-scrollarea"><table class="data-table compact"><thead><tr><th>批次</th><th>本批处理</th><th>收录</th><th>未收录</th><th>累计进度</th><th>原因</th><th>时间</th></tr></thead><tbody id="batch-body-{{executionId}}"><tr><td>{{batchNumber}}</td><td>{{batchProcessed}}</td><td>{{batchPassed}}</td><td>{{batchRejected}}</td><td>{{processedAndTotal}}</td><td><details class="apv-rules"><summary>查看全部原因</summary><p>{{allReasons}}</p></details></td><td>{{createdAt}}</td></tr></tbody></table></div>
          </section>
          <!-- S-7：原下载量按来源/站点完整显示，默认收起 -->
          <details class="apv-traffic"><summary>HTTP 下载量 {{collectionTraffic}} · 展开详情</summary><p>{{metadataAndFulltext}}</p><p>{{oversizedAndDiscarded}}</p><p>{{allSources}}</p><p>{{allHosts}}</p><p class="apv-small">仅统计采集任务实际读取的响应体；不含 TLS、代理开销、其他应用及 Apollo/ORCID 核验请求流量。</p></details>
          <p class="apv-footnote">按本次核验结论统计；当前审核状态见逐条详情。自动准入不代表已发信。未明确表示资格缺证，待核验表示尚未获自动准入。</p>
        </td></tr>
      </tbody>
    </table>
  </div></div>
</div>

<!-- S-2：插在原高级选项之后、原开始/取消按钮之前；仅深度发现显示 -->
<div class="apv-option" id="taskLaunchApolloRow" hidden>
  <label for="taskLaunchApolloAssist"><input type="checkbox" id="taskLaunchApolloAssist" aria-describedby="taskLaunchApolloHint">启用 Apollo 辅助核验</label>
  <p id="taskLaunchApolloHint">已有可靠证据足够的直接准入；其余进入辅助核验。确认本人后，资格结论分别标记为「已明确」或「未明确」。</p>
  <details class="apv-rules"><summary>查看准入口径</summary><p>当前本人教授类职称可明确符合；其他情况核对已取得博士及年龄、职称条件。Apollo 仅返回候选不能直接准入。缺少可靠来源时保留待核验。</p></details>
</div>

<!-- S-8：加载/空态置于对应tbody，colspan外层11、批次13、逐条8、采集7 -->
<tr><td class="apv-empty" colspan="{{columnCount}}">{{加载中… / 暂无记录 / 暂无符合筛选的档案 / 未记录}}</td></tr>
<!-- S-8：错误提示在对应section内，旧成功数据保留且标旧更新时间 -->
<div class="apv-alert" role="alert">统计读取失败，当前显示上次成功结果（{{asOf}}）。<button class="apv-link" type="button" data-action="reload">重试读取</button></div>
```

## 现状审计

[audit§7](apollo-code-audit.md)及[完整修改前HTML/CSS逐字快照](apollo-evidence/frontend-before.txt)是本片强制输入。原index:1129–1258；app.js:4179 renderRunList、4349 renderBatchTable、4395 renderRunRow、4429 renderBatchDetailRow、9534 taskLaunchConfigs、10081 executeDiscover、10144 postDiscoveryLaunch。原来源项动态inline布局在10043，不改。

全局label/input为column/34px，半透明panel-bg和sticky th造成用户截图表头叠层风险；上文只覆盖apv scope。原runtime按采集终态结束轮询，故本片在app.js独立管理核验轮询，不改task-modal-runtime.js。原JS测试会从app.js源文本提取函数，函数新依赖必须进入真实测试sandbox，不能用永远返回假节点的stub掩盖缺DOM。IP-5/IP-6贯穿本片，IP-2投影失败显示在摘要与当前状态列。

## 实现方案

1. **S-1～S-8 / I-3/I-4**：styles.css仅追加上述CSS；index新增Apollo开关、原logs wrapper id/class、thead id，去除已等值搬移的日志wrapper inline；不改关键词/来源/高级选项/按钮现有尺寸。原modal-body等未改inline不动。必须保留审计末尾列出的并发“手动统计命中人数”改动；原行号定位以冻结HEAD为准，增量styles在9310后偏移42行，用选择器确认。缓存16个key同步为`20261018-apollo-verification`，不新增资源、不改runtime无版本引用。
2. **S-2 / I-2/I-4**：app.js的EXPERT_DISCOVERY preload并行读sources+capabilities，校验generation后应用；executeDiscover切换视图前冻结apolloAssist，传入postDiscoveryLaunch的新增末尾参数（旧调用默认保持）。普通`POST /run`在JSON body写`apolloAssist`；关键词`POST /run/by-keyword`按现有URLSearchParams追加`apolloAssist=true/false`（该入口没有body）；只控制该次run，不写全局开关。新capability失败需要展示错误且禁止显式Apollo启动，不能空值静默开跑。
3. **S-3～S-8 / I-1/I-2**：app.js追加`// apollo-verification-ui:start/end`功能区域，独立render/state/fetch函数。renderRunList和updateTaskModalLogs仅在深度发现分派新函数，原分支保持。复用原execution list获取开始时间/触发/采集状态/流量，批量summary按ID合并；展开时取batches/records，旧采集日志仍由runtime fetchAndCacheBatchLogs写入新采集子tab。不能令一个旧async回调重建已切换的tbody。
4. **S-4/S-6/S-7 / I-2/I-3**：仅在logHost绑定一个delegated click/change/input/keydown；不要全document listener。事件侦听一次、关闭释放；tab/筛选/page/detail/openBatch状态存在当前modal generation下。列表轮询不重建正在输入的select/input，不失焦；使用snapshotVersion判变化，展开状态保留。
5. **I-1/I-2 / S-8**：每5秒汇总+当前可见页更新，采集终态但queued>0继续；全部核验终态停止定时器，用户“重试读取”可手动刷新；关闭/切主tab/换task取消timer和AbortController，重开拉最新。新统计错误时保留旧data并显示旧asOf，不用空数组覆盖。未记录选项不下钻空历史“0”；保留采集tab可读。
6. **I-1～I-4 / S-1～S-8**：新测试内嵌匿名F123 JSON，模拟已完成采集仍在核验、request乱序、分页、错误和null。增加源文本真实DOM id检查、完整CSS块SHA256比对及新markup无inline样式检查；使用真实浏览器检查截图，不能仅用Node stub。调整列出的3个旧测试fixture新参数/页面节点，禁止放宽其非发现/审核断言。所有新函数运行路径都纳入新test sandbox。
7. **C07-BATCH-READ-SHAPE / S-5必要接缝**：既有batches只读响应每项增补`cumulativeProfiles`（该run批次号≤本批的已激活档案数，不限当前页）、`updatedAt`（该批已激活档案的MAX(updated_at)）及完整`reasons:{code,label,count}[]`（每档案每原因去重，未知code保留）；同REPEATABLE_READ与snapshotVersion，不写库或访问provider。服务端聚合，不以前端无限翻records补齐。既有counts/suggested/runCounts及其他API字段语义不变；回归覆盖跨页累计、完整原因和批次更新时间。

## 变更文件清单

共13个文件：原7个前端及验证文件，加4个S-5必需的既有只读API/聚合及测试，以及2个因强制追加CSS而须移除旧文件尾部位置断言的现有测试；作为每片≤10的直接依赖/测试专项例外。生产范围不再增加，无新增业务目标，以下之外无实施授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/resources/static/index.html`|修改原弹窗、资源key|
|2|`src/main/resources/static/styles.css`|仅追加契约CSS|
|3|`src/main/resources/static/app.js`|有边界功能区及既有入口接入|
|4|`src/test/js/discoveryVerification.test.js`|新增数据/DOM/CSS/异步测试|
|5|`src/test/js/discoveryContinuousRun.test.js`|修改启动参数fixture|
|6|`src/test/js/discoveryScheduleSetting.test.js`|修改启动参数fixture|
|7|`src/test/js/discoveryReview.test.js`|补审核页签和缓存回归|
|8|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`|修改batches只读DTO累计/更新时间/完整原因|
|9|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`|修改只读批次聚合，保持写路径不变|
|10|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`|修改批次DTO语义回归|
|11|`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt`|修改真实MySQL批次跨页累计/原因/时间回归|
|12|`src/test/js/expertManualProfile.test.js`|移除与强制追加CSS冲突的旧样式块必须在文件末尾断言；保留其他行为回归|
|13|`src/test/js/expertMeetingVideo.test.js`|移除同类旧样式块文件尾部位置断言；保留其他行为回归|

## 验收标准

- I-1：F123=123/55/15/40/36/19/45/5/18；旧null“未记录”、新0、错误503可见；一条ES同步错误不减少auto而增加projectionErrors；AUDIT不得显示55自动准入。
- I-2：A请求晚到B不得改B；切筛选重置页；采集SUCCESS但active>0继续轮询；关闭不再请求；相同snapshot不销毁输入焦点。
- I-3：输出姓名/机构`<img onerror=...>`呈纯文本；javascript:链接不生成；按钮键盘和aria正确；表格colspan闭合，无HTML截断。
- I-4：其他任务列/主审核tabs回归，16个资源key统一，runtime原引用不变。
- S-1：CSS契约块逐字SHA一致，原全局规则diff不变；日志wrapper等值搬移；1180/80vh/18px。
- S-2：checkboxcomputedStyle width/height/min-height均14px，label flex-direction=row；来源checkbox保持原值；原开始/取消按钮样式不改。
- S-3：11列顺序，status双行，历史未记录；无新卡片。
- S-4：摘要在展开行，3subtabs aria和键盘，计数跳转筛选正确。
- S-5：13列/合计全run，原因完整；采集表7列非sticky且#f8fafc；无纵向内层max-height。
- S-6：8列、服务端total/20页、筛选重置、上一页边界禁用。
- S-7：证据白底4块、链接/时间/缺失文字；下载details初始closed，所有host可见。
- S-8：11/13/8/7占位colspan，error alert/只读重试，focus/disabled，390/700/1366/1920宽测试。
- 执行C-7的Node测试与`node --check`，再跑全部src/test/js；保存1366×900和390×844的配置/日志/证据截图作验证产物，不在本计划阶段声称通过。

## 人工验收清单

### A-1: 原配置与主页签
- 前置条件：测试部署启用P1，浏览器1366×900；capabilities ready。
- 操作步骤：打开深度发现；对照冻结配置图；勾选/取消Apollo；切专家审核、审核记录再返回；启动1条合成档案。
- 预期结果：关键词、7平台原样；Apollo14px checkbox与文字横排；原3主tab均能操作；请求中的apolloAssist与所选一致，不因轮询复位。
- 覆盖：O-1，N-1/N-2，I-4，S-1/S-2，IP-6

### A-2: 日志计数和下钻
- 前置条件：在隔离测试环境seedF123；打开该执行。
- 操作步骤：展开执行→点击未明确19→翻页→清除筛选→查看本批。
- 预期结果：总123、auto55、direct15、Apollo40、clear36、unclear19；未明确total19，待核验45单列；首末分页按钮按边界禁用；批次40/40/43。
- 覆盖：O-2，I-1，S-3/S-4/S-5/S-6，IP-5

### A-3: 完整原因/依据/同步
- 前置条件：F123一条含5类原因及超长code，一条投影错误；证据URL与来源时间具值。
- 操作步骤：展开全部原因、逐条证据及下载量。
- 预期结果：5类原因全见，不止前3；未取得博士/年龄直接显示缺证；投影错标候选同步待处理，不归Apollo请求错误；下载量默认收起且可见全部站点。
- 覆盖：O-2，I-3，S-5/S-7，IP-2/IP-5

### A-4: 旧历史、0和失败
- 前置条件：准备无账本旧任务、新空账本任务；让summary接口返回503，再恢复。
- 操作步骤：分别打开，点击重试读取。
- 预期结果：旧“未记录”，新0；503显示统计读取失败及旧asOf；恢复后更新真实值，无准入写请求。
- 覆盖：O-3，N-4，I-1，S-8，IP-5

### A-5: 异步与其他任务回归
- 前置条件：A核验响应延迟10秒；B为不同执行，另一任务为候选重验；A采集已SUCCESS但queue1。
- 操作步骤：展开A后立即B；关闭再打开候选重验；回A等queue结束。
- 预期结果：A晚响应不覆盖B；关闭后无新请求；候选重验仍原列/操作；A在采集完成后仍更新核验状态，最终停止轮询。
- 覆盖：N-3，I-2/I-4，S-1/S-3/S-8，IP-5/IP-6

### A-6: 样式逐项目测
- 前置条件：1366×900/1920×1080/390×844，100%缩放，5类原因展开。
- 操作步骤：比对冻结截图；滚动；Tab遍历开关、tab、筛选、页码和证据链接。
- 预期结果：最大宽1180、圆角18；主色#1e40af；表头#f8fafc不与下载文字叠；只有modal纵向scroll、表格可横向scroll；≤700证据单列；focus2px offset3；disabled透明度.45。
- 覆盖：N-1，I-3，S-1～S-8

## 修正记录
- C07-BATCH-READ-SHAPE：冻结S-5需要累计档案/完整批次原因/时间，现有06批次DTO仅有batchNumber/counts/suggested，不能由当前分页可靠还原。依据用户“批准该计划并执行 后续也都授权 执行完所有子计划”的后续必要依赖授权，增加上述4个既有只读生产/测试文件，上限7→11为直接依赖专项例外；不重开06、不变CSS/DOM或统计真值，精确身份与依据见fast-p ledger Amendments。
- C07-CSS-TRAILING-TESTS：真实全量Node发现expertManualProfile.test.js:911–915及expertMeetingVideo.test.js:977–982仍断言各自旧STYLE_S2位于styles.css末尾，与S-1强制追加Apollo契约矛盾。按同一后续授权增加这两个现有测试，上限11→13，仅删除过时位置/实现断言及随之无用的测试常量，不将其重新钉在新位置；其他功能行为断言保留，既有41个无关失败不在修复范围。
