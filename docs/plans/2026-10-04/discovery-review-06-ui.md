# 06：深度发现审核前端

依赖：01–05 API契约稳定；D1确认后才能展示最终“仅显式条件”的发送说明。沿用现有真实预览的表格/批量确认方向，但删掉其“全员待审”“本次预览”“新增审核状态过滤”旧语义。

## 需求描述

在深度发现入口中增加“专家审核”“审核记录”，与现有“采集运行”同级。展示自动通过/需人工审核及具体原因，支持单个、所选、当前页、所有页确认。批量配置保留现有控件，只纠正文案并展示准入与筛选计数。

必须保持：采集启动/暂停/定时/日志；其他任务弹窗700px宽度；现有批量配置保存与手动快照字段；键盘可操作；刷新后以数据库审核为准。

范围外：新路由框架、重做导航、复制整份production CSS、改全站主题、浏览器存授权、为每个缺项新增发信开关。

## 关键不变量

### Invariant I-1：位置及结果真实
- Rule：审核只挂EXPERT_DISCOVERY；自动通过不显示待审；所有数量与状态来自API。点击确认后显示“处理中”，收到持久APPLIED才更新成功数；未初始化/同步失败与待审分开。
- Applies to：初始化、列表、详情、确认、结果与记录。
- Violation consequence：预览假状态进入正式产品。
- 来源：用户要求。

### Invariant I-2：三种批量范围明确
- Rule：所选=所选IDs，当前页=当前展示且可审核IDs，所有页=服务端当前筛选内全部NEEDS_REVIEW。确认框列范围、固定总数、原因命中人数、备注；所有页等待READY。筛选变化使未确认快照作废，已提交任务不扩大范围。全选当前页不是全选所有页。
- Applies to：selection、prepare、confirm、翻页、搜索。
- Violation consequence：越界批准。
- 来源：用户要求；X5。

### Invariant I-3：异步响应不能污染新页面
- Rule：使用现有modal generation + 独立review请求序号；切tab/关弹窗/换任务时取消GET或丢弃旧响应；关闭弹窗不取消后台审核。错误保留名单、显示具体原因，禁止自动假装重试成功。
- Applies to：polling、reload、页切换、任务切换。
- Violation consequence：旧请求覆盖新选择或关窗意外停止后台任务。
- 来源：当前app.js:2249/2380/7986生命周期。

### Invariant I-4：前后端使用相同词义
- Rule：无新增审核筛选控件到批量任务。批量页显示“准入通过N；待审核M（去深度发现）；显式条件排除K；本次目标T”。模板门禁关闭文案明确不按个性化缺项排除；D1未定案不写“所有黑盒已清除”。已批准缺项可继续显示，不能用绿勾伪装资料补齐。
- Applies to：深度发现与批量编辑/手动执行两处文案。
- Violation consequence：页面与实际不一致。
- 来源：用户要求。

## 样式契约

所有新增DOM归属S-1/S-2/S-3；新增class仅下列dr-*。既有class不修改，无全局覆盖；不使用inline style。动态行只使用同表所列class、标准tr/td/details/summary/p/span/button/input。错误、空态、加载态也使用同一契约。

### S-1：容器与页签
- 复用：styles.css `.modal-content:4494`、`.modal-header:4510`、`.modal-body:4525`、`.batch-send-tabs:9150`、`.batch-send-tab:9159`、`.button:802–873`。仅深度发现标记触发宽度；不改原规则，故无需改其他使用点。
- 新增CSS逐字：

```css
#taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
    max-width: 1180px;
    width: calc(100vw - 48px);
}
.dr-review {
    color: var(--text-main);
    font-size: 13px;
    line-height: 1.6;
}
.dr-review [role="alert"] {
    padding: 12px;
    color: var(--error-strong);
    background: var(--error-bg);
    border: 1px solid var(--error-border);
    border-radius: var(--radius-sm);
}
.dr-review button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
.dr-review :focus-visible,
#discoveryReviewTabs :focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 3px;
}
@media (max-width: 640px) {
    #taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
        width: calc(100vw - 20px);
    }
}
```

- DOM：在taskProgressModal现有header之后插入下列nav；原采集内容包进discoveryPipelinePane（内部原ID/事件不变）。其他任务nav隐藏、pipeline pane常见。

```html
<nav id="discoveryReviewTabs" class="batch-send-tabs" role="tablist" aria-label="深度发现功能" hidden>
  <button class="batch-send-tab is-active" type="button" role="tab" aria-selected="true" aria-controls="discoveryPipelinePane" id="discoveryPipelineTab">采集运行</button>
  <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewPane" id="discoveryReviewTab" tabindex="-1">专家审核</button>
  <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewHistoryPane" id="discoveryReviewHistoryTab" tabindex="-1">审核记录</button>
</nav>
<div id="discoveryPipelinePane" role="tabpanel" aria-labelledby="discoveryPipelineTab"></div>
<section id="discoveryReviewPane" class="modal-body dr-review" role="tabpanel" aria-labelledby="discoveryReviewTab" hidden></section>
<section id="discoveryReviewHistoryPane" class="modal-body dr-review" role="tabpanel" aria-labelledby="discoveryReviewHistoryTab" hidden></section>
```

空pipeline div是原采集DOM的包装位置，执行时须移动原节点，禁止新建空替代品。

### S-2：统计、工具条、表格、分页
- 复用：`.data-table:3421`、`.bsc-input:5498`及focus规则、`.button.primary/.secondary`。蓝色#1e40af，正文#1e293b，次正文#475569；表格数据文字12px、line-height1.6，button既有32px。
- 新增CSS逐字：

```css
.dr-counts {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
}
.dr-counts > div {
    padding: 14px;
    background: var(--bg-subtle);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
}
.dr-counts strong {
    display: block;
    font-size: 24px;
    color: var(--primary);
}
.dr-toolbar,
.dr-pager {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}
.dr-toolbar .bsc-input {
    width: 180px;
}
.dr-pager {
    justify-content: space-between;
}
.dr-table-wrap {
    overflow: auto;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
}
.dr-table {
    min-width: 1000px;
}
.dr-table th,
.dr-table td {
    padding: 12px 10px;
    font-size: 12px;
    line-height: 1.6;
    vertical-align: top;
    overflow-wrap: anywhere;
}
.dr-table th {
    background: #f8fafc;
    color: var(--text-secondary);
}
.dr-table tbody tr:hover {
    background: #f8faff;
}
.dr-state {
    display: inline-block;
    padding: 2px 8px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    background: var(--bg-subtle);
}
.dr-state[data-state="AUTO_PASSED"],
.dr-state[data-state="MANUAL_APPROVED"],
.dr-state[data-state="LEGACY_APPROVED"] {
    color: var(--success);
    background: var(--success-bg);
    border-color: var(--success-border);
}
.dr-review input[type="checkbox"] {
    width: 15px;
    height: 15px;
    accent-color: var(--primary);
}
@media (max-width: 640px) {
    .dr-counts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
```

- DOM：放入review pane；动态文案用textContent，事实详情details展开，不插入未转义专家HTML。空/加载状态为tbody单行colspan7；不新增样式类。

```html
<div class="dr-counts" id="discoveryReviewCounts" aria-live="polite">
  <div>自动通过<strong>—</strong></div><div>待人工审核<strong>—</strong></div>
  <div>人工 / 历史通过<strong>—</strong></div><div>暂缓 / 不通过<strong>—</strong></div>
</div>
<div class="dr-toolbar">
  <input class="bsc-input" id="discoveryReviewSearch" type="search" aria-label="搜索专家" placeholder="姓名、邮箱、机构">
  <select class="bsc-input" id="discoveryReviewIssue" aria-label="问题筛选"></select>
  <select class="bsc-input" id="discoveryReviewDecision" aria-label="准入状态筛选"></select>
  <select class="bsc-input" id="discoveryReviewPageSize" aria-label="每页人数"><option>20</option><option>50</option><option>100</option></select>
  <button class="button secondary" type="button" id="discoveryReviewApprovePage">审核通过当前页</button>
  <button class="button primary" type="button" id="discoveryReviewApproveAll">审核通过所有页</button>
</div>
<div class="dr-toolbar">
  <span id="discoveryReviewSelection">已选择 0 人</span>
  <button class="button secondary" type="button" data-review-action="APPROVE">通过所选</button>
  <button class="button secondary" type="button" data-review-action="HOLD">暂缓所选</button>
  <button class="button secondary" type="button" data-review-action="REJECT">不通过所选</button>
</div>
<div role="alert" id="discoveryReviewError" hidden></div>
<div role="status" id="discoveryReviewResult" aria-live="polite"></div>
<div class="dr-table-wrap"><table class="data-table dr-table">
  <thead><tr><th><input type="checkbox" id="discoveryReviewSelectPage" aria-label="全选当前页可审核专家"></th><th>专家 / 邮箱</th><th>机构 / 所在国家</th><th>研究方向 / 学术指标</th><th>校验问题</th><th>准入结果</th><th>操作</th></tr></thead>
  <tbody id="discoveryReviewRows"></tbody>
</table></div>
<footer class="dr-pager"><span id="discoveryReviewPageInfo"></span><div class="dr-toolbar">
  <button class="button secondary" type="button" id="discoveryReviewPrev">上一页</button>
  <button class="button secondary" type="button" id="discoveryReviewNext">下一页</button>
</div></footer>
```

每行操作复用button small secondary，结果用dr-state，证据用details/summary；各项诊断以p/span文本呈现。空值显示“未记录”，无返回显示“未提供”，数据读取失败显示“读取失败”，不混为一个破折号。

### S-3：确认与记录、发送提示
- 复用button、bsc-input与S-2 dr-toolbar/dr-state；确认框内嵌，无另起遮罩/z-index；避免透明panel遮不住底层（K-panel-bg-token-is-translucent）。批量页既有batch-gate-hint只换text，不加class。
- 新增CSS逐字：

```css
.dr-confirm,
.dr-record {
    padding: 16px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
    background: #ffffff;
}
.dr-confirm {
    border-color: rgba(30, 64, 175, 0.25);
    background: #f7f9ff;
}
.dr-confirm p,
.dr-record p {
    margin: 8px 0;
    color: var(--text-secondary);
}
.dr-confirm textarea {
    min-height: 68px;
    margin: 8px 0;
    resize: vertical;
}
```

- DOM：confirm放S-2表格前；记录在history pane动态重复dr-record。所有页阶段显示“正在固定名单 / 待确认 / 处理中 / 部分失败 / 已完成”，同status区域更新；禁止用一个成功toast代替失败清单。

```html
<section class="dr-confirm" id="discoveryReviewConfirm" hidden aria-labelledby="discoveryReviewConfirmTitle">
  <h4 id="discoveryReviewConfirmTitle">确认审核</h4>
  <p id="discoveryReviewConfirmScope"></p><p id="discoveryReviewConfirmReasons"></p>
  <details><summary>查看固定名单</summary><div id="discoveryReviewConfirmPeople"></div></details>
  <label for="discoveryReviewNote">审核备注</label>
  <textarea class="bsc-input" id="discoveryReviewNote" maxlength="1000"></textarea>
  <div class="dr-toolbar"><button class="button secondary" type="button" id="discoveryReviewCancelConfirm">取消</button><button class="button primary" type="button" id="discoveryReviewCommit">确认审核</button></div>
</section>
<article class="dr-record">
  <strong></strong><p></p><details><summary>名单及逐项结果</summary><div></div></details>
  <div class="dr-toolbar"><button class="button secondary" type="button" data-review-retry>重试失败项</button><button class="button secondary" type="button" data-review-revoke>撤销当前审核</button></div>
</article>
```

只在有效动作可用时显示/启用重试与撤销。按人展示实际失败，不将“审核保存但候选同步失败”归到审批未成功。

## 现状审计

逐字旧HTML/CSS基线见[frontend-baseline](discovery-review-evidence/frontend-baseline.md)。现有深度发现为通用任务弹窗，无专用审核页；预览review.js只用会话内数据，不能进入正式代码。入口至少覆盖app.js openTaskModal:2249、配置态createTaskModalContext:7986、closeTaskModal:2380，不能只接运行态而漏掉初次打开配置态。

全站CSS类复用但不改旧规则；新增CSS只在dr-*及带data-discovery-review容器生效。按钮hover、active来自802–873；新增disabled和focus在S-1。数据表表头采用不透明#f8fafc；无新drawer。当前11个资源版本相同、测试无固定旧键命中，证据见cache-key.txt。

读写路径：GET列表/历史/任务→渲染；用户prepare→确认→后台写入→轮询；不在前端再算资格或写localStorage批准。跨路径X2/X5/X6/X7/X9全部由接口返回驱动。

## 实现方案

1. **I-1/I-3、S-1**：修改index.html包装原采集DOM，增nav/panes。app.js两个打开路径统一initDiscoveryReview(taskType,generation)；非发现立即隐藏并清在途响应；关闭清本页轮询，后台任务继续。
2. **I-1/I-2、S-2/S-3**：实现API列表/详情/选中/页/all按钮。整页与all点击后先prepare，确认按钮等READY；显示真实count/hash范围，确认payload不带新query。空名单禁用，失败保留错误。记录支持固定名单分页、失败重试、撤销当前决定。
3. **I-3、S-1/S-2**：tab左右键/Tab焦点切换，aria-selected/hidden同步；关闭返回触发按钮。请求序号防慢响应覆盖；UI内所有专家字段用textContent。
4. **I-4、S-3**：批量编辑与手动两处模板提示同步替换；显示准入计数和去审核入口，不新增审核筛选。账号绑定等文案按D1最终结论更新，未定案不能写断言式承诺。
5. **I-1–I-4、S-1–S-3**：styles.css逐字追加契约规则；11项已有资源统一bump至`20261004-discovery-review`，执行前重查当时键和测试字面量。新增资源0，仍11项。新增测试真实HTML节点存在及生命周期，不能只用万能DOM stub通过。

## 变更文件清单

共6文件、前端1子系统；新数据字段0。

| 文件 | 操作 |
|---|---|
| src/main/resources/static/index.html | DOM、两处文案、统一版本 |
| src/main/resources/static/app.js | 生命周期、真实接口、范围确认 |
| src/main/resources/static/styles.css | 仅追加S-1/S-2/S-3 |
| src/test/js/discoveryReview.test.js | 新增交互/HTML/请求测试 |
| src/test/js/taskModalLifecycleIntegration.test.js | 其他任务与双打开路径回归 |
| src/test/js/gateTemplateFilter.test.js | 两入口开关文案/快照一致 |

## 验收标准

- I-1：自动通过不计待审；202不算完成；DB失败保持明确错误；刷新不丢审批。
- I-2：当前页20且筛选10005时两个按钮范围分别20/10005；confirm只发送batchKey/hash；新入库不加入。
- I-3：慢请求、关窗、换任务、换筛选、重开均无旧响应覆盖；关闭后台任务继续。
- I-4：没有sendReview/审核状态新过滤；两处模板hint一致；缺项批准仍展示缺项；D1文字与政策一致。
- S-1/S-2/S-3：新CSS与代码块逐字一致；DOM层级/ID对齐；无新增inline style、未声明class或旧规则改动。桌面1440px与手机390px截图，表格横向滚动、容器不溢出，其他任务仍700px。
- `node --test src/test/js/*.test.js`；真实浏览器验证，不能只凭node测试宣称UI验收通过。

## 人工验收清单

### A-1：入口与视觉回归
- 前置条件：登录隔离验收环境，1440px桌面与390px移动视口；有发现任务和检查回复任务。
- 操作步骤：1. 分别从配置态、运行态打开深度发现。2. 切三个页签。3. 打开检查回复任务。4. 用键盘操作页签/确认。
- 预期结果：深度发现最多1180px，其他任务仍700px；primary #1e40af，按钮32px，表格12px；手机容器不出屏、表格可横滚；采集控制/日志继续可用。
- 覆盖：I-1/I-3、S-1/S-2/S-3、必须保持项。

### A-2：当前页与所有页
- 前置条件：20人一页、10005待审；另有自动通过样本。
- 操作步骤：1. 当前页通过，确认人数20。2. 再点所有页，等待固定名单。3. 确认后关窗再打开。4. 查看失败明细和历史。
- 预期结果：第一页20人通过后所有页剩余9985；自动通过者不被要求点击；关窗不丢任务；完成按后端计数，失败不会显示全成功。
- 覆盖：I-1/I-2/I-3、S-2/S-3、X2/X5/X9。

### A-3：显式配置衔接
- 前置条件：人工批准但缺机构/研究方向的样本，批量编辑与手动执行入口均可用；D1已定案。
- 操作步骤：1. 查看审批结果与缺项。2. 跳批量预估。3. 分别开/关模板门禁，保存重开。4. 切任务时制造慢列表响应。
- 预期结果：缺项事实不变；无额外审核过滤开关；门禁关不按缺项排除，开有具体变量原因；保存快照不丢现有字段；慢响应不污染其他任务。
- 覆盖：I-4、S-3、X6/X7、必须保持项。
