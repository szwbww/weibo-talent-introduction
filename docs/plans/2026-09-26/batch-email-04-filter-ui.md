# 子计划 04：过滤开关控制台接入

状态：DRAFT，仅创建开发计划，未实施、未运行测试、未部署。
目标工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，审计分支 `main`，HEAD `d6f54c25b228ee2e9e0317d053957ae3f56984b5`。当前已有其他未提交改动，见证据 E-00；执行前重查，禁止覆盖。
证据：[源码快照](batch-email-reliability-evidence.md)。E-n 均包含读取命令/原始输出或带原文件行号的摘录；下文“拟改”是设计决策，不是现状事实。

依赖：01/02/03 已验证。仅将已生效的后端能力接入现有表单，不改控制台布局框架。

## 需求描述

定时任务编辑、手动执行的“过滤条件”中新增“排除已验证不可用邮箱”；开关修改触发候选预估，显示排除数；贯通来源配置、差异、执行确认和历史参数。

必须保持：新建默认开、存量缺省关、手动覆盖不回写来源；发送前实时验证开关独立；原模板门禁人数提示/防旧响应覆盖机制；两类邮件模板均可使用历史过滤。

范围外：新增页面/弹窗、全局设置、CSS 重构、重验按钮、历史数据回填、自动恢复发送。

## 关键不变量

### Invariant I-1：配置/草稿/请求字段完整
- Rule：统一字段名 excludeVerifiedUnavailableEmails，严格布尔值。新建编辑器与独立手动默认 true；来源配置缺字段视 false。读取来源后允许仅本次改动，clear source 回到独立默认。历史请求JSON缺字段保留原样；需要反序列化时按false，不用当前配置回填。
- Applies to：editor fill/save/preview、deepCloneConfig/defaults/fill/read/build snapshot、diff/confirm/requestSnapshot 参数持久化。
- Violation consequence：UI 开着请求却关着，或旧任务在编辑时被默默开启。
- 来源：K-batch-snapshot-two-write-entrances；E-18/E-28。

### Invariant I-2：开关不随验证或模板门禁禁用
- Rule：切 INTRODUCTION/MATERIAL_REMINDER 不清理新开关；实时验证 off 也允许历史过滤 on；模板无必填字段导致 gate unavailable 时，新开关仍可用。
- Applies to：事件绑定、refreshEmailVerificationState 周边；禁止直接复用其“材料提醒清 false”逻辑。
- Violation consequence：截图场景仍无法独立过滤。
- 来源：original；E-18 的现有 realtime state 逻辑。

### Invariant I-3：人数以同一响应为准
- Rule：展示选中的门禁分支 response.totalSendable 和 response.excludedVerifiedUnavailable（缺省0）；新开关并入现有 snapshot。门禁有无只维持原1/2个请求，不另发一次“关闭邮箱过滤”请求做减法。保持500ms debounce、request seq 防过期响应、catch 错误提示。
- Applies to：buildConfigEditorRecipientSnapshot/buildManualExecutionSnapshot/baseHintHtml/refreshRecipientPreview。
- Violation consequence：错误显示门禁分支的人数，或者预估请求倍增。
- 来源：original，E-19。

## 样式契约

### S-1：两个过滤区开关
- 位置：分别紧接 editorFieldGateFilter / manualFieldGateFilter，仍在过滤条件 section 内、人数 hint 前。
- 复用：styles.css:9331 .batch-config-field-label（12px/600、margin-bottom6px、#64748b）；9354–9415 status-toggle/switch（36×20、圆角999px、thumb16×16、checked #1e40af、off #cbd5e1、focus 3px rgba(37,99,235,.18)）；9708–9740 gate-field/row/toggle/hint（横排gap12px、开关gap8px、说明11px/1.5/#94a3b8）。完整原规则 E-17；原 DOM E-16。
- 新增 CSS：无。不得改既有规则、另造 class 或 inline style；输入跟已有切换框一致由 label 提供可点击名称。
- 编辑器新增 DOM 必须为：

```html
<div class="batch-config-field batch-gate-field" id="editorFieldExcludeVerifiedUnavailableEmails">
  <span class="batch-config-field-label">排除已验证不可用邮箱</span>
  <div class="batch-gate-row">
    <label class="batch-task-status-toggle batch-gate-toggle">
      <input type="checkbox" id="batchConfigEditorExcludeVerifiedUnavailableEmails" aria-label="排除已验证不可用邮箱" aria-describedby="batchConfigEditorExcludeVerifiedUnavailableEmailsHint">
      <span class="batch-task-status-switch" aria-hidden="true"></span>
      <span class="batch-task-status-label" id="batchConfigEditorExcludeVerifiedUnavailableEmailsLabel">已开启</span>
    </label>
    <span class="batch-gate-hint" id="batchConfigEditorExcludeVerifiedUnavailableEmailsHint">按最近一年内的最新有效验证结果，排除不可投递邮箱；不发起新的验证请求。</span>
  </div>
</div>
```

- 手动新增 DOM 必须为：

```html
<div class="batch-config-field batch-gate-field" id="manualFieldExcludeVerifiedUnavailableEmails">
  <span class="batch-config-field-label">排除已验证不可用邮箱</span>
  <div class="batch-gate-row">
    <label class="batch-task-status-toggle batch-gate-toggle">
      <input type="checkbox" id="batchManualExcludeVerifiedUnavailableEmails" aria-label="排除已验证不可用邮箱" aria-describedby="batchManualExcludeVerifiedUnavailableEmailsHint">
      <span class="batch-task-status-switch" aria-hidden="true"></span>
      <span class="batch-task-status-label" id="batchManualExcludeVerifiedUnavailableEmailsLabel">已开启</span>
    </label>
    <span class="batch-gate-hint" id="batchManualExcludeVerifiedUnavailableEmailsHint">按最近一年内的最新有效验证结果，排除不可投递邮箱；不发起新的验证请求。仅影响本次执行，不修改原定时任务。</span>
  </div>
  <span class="batch-config-diff-badge" hidden>已修改</span>
  <div class="batch-config-diff-original" hidden></div>
</div>
```

label/checkbox 的初值最终由表单填充逻辑同时设置，禁止保留 DOM 中 label=已开启、checkbox=false 的未初始化可交互状态。

### S-2：人数与差异/参数摘要
- 复用 .batch-config-editor-hint，styles.css:9257，margin-top10px、padding8px12px、radius10px、背景rgba(37,99,235,.06)、字号12px/1.6；strong 主色 #1e40af、600。
- 当前开关开启时在原 baseHintHtml 末尾追加 `；已排除不可用邮箱 <strong>M</strong> 位`；关闭不追加，避免0被误解为历史不存在。M 从对应响应取值。保持原门禁 hint 的后续拼接，不新增容器或 class。
- 手动差异继续 .is-config-diff（10px padding，1px #e11d48 边框、radius10px）、.batch-config-diff-badge 与 .batch-config-diff-original（E-17:9555–9579）；参数确认与来源差异沿用现有字段行，“排除已验证不可用邮箱：开启/关闭”。列表范围行沿用 .batch-task-scope-line（styles.css:9172）。不增加新的 pill 设计。
- 既有 CSS 定义一律不修改；现有使用点核对见 E-27，故无共享样式传播风险。

## 现状审计

### 表单与存储交互
- E-18 是 emailVerificationEnabled 现有全链位置索引，需逐点对照新字段；不能只在 submit body 加一处。保存 controller 直收 command，执行 controller 直收 snapshot，见 E-14。
- 写入口：保存配置→config entity；手动执行→task_execution.request_payload。草稿值保存在 batchTaskState.manualDraft，来源 baseline 用于 diff。新字段只跟随既有写入口，无新存储。
- 读入口：任务列表范围、编辑器填充、来源→草稿、人数预估、字段 diff map、执行确认；历史请求由接口原样返回，通用任务详情可显示原始JSON（E-28:12347）。不新建批量日志参数面板。缺字段兼容落在这些读入口。
- IP-1：配置保存→重新编辑/来源回填；IP-2：草稿修改→preview 与执行快照→历史详情；IP-3：门禁双请求→选中分支的排除计数。

### 前端样式盘点
- 逐字 DOM 基线 E-16，CSS 基线 E-17/E-26；全部复用 class、颜色、字号和间距见 S-1/S-2，无 styles.css 修改。
- 既有两个“邮件模版门禁过滤”节点不可用不代表新历史过滤不可用；S-1 是独立节点。
- E-19 显示 preview 在门禁不可用时有 early return，该分支必须通过同一 baseHintHtml 追加 M，不能只更新门禁开启分支。
- JS 测试已有 extractFn + DOM stub，新增 id 必须额外断言真实 index.html 存在（已有项目知识约定），否则测试可能虚绿。

## 实现方案

### T1：DOM 和表单全链（I-1/I-2/S-1/S-2）
文件：index.html、app.js。

在 E-16 标记位置插入 S-1 DOM。沿 E-18 的既有字段链核对以下函数/入口：

1. 编辑器 open/fill、buildConfigEditorRecipientSnapshot、编辑器 save payload；新建无 config→true，有 config→`config.excludeVerifiedUnavailableEmails === true`。
2. deepCloneConfig、fillManualFormDefaults、fillManualFormFromDraft、readManualFormValues、buildManualExecutionSnapshot；来源缺字段 false，独立默认 true。
3. normalizeManualSnapshot/formatManualDiffValue（E-28:19319/19341）、diff 字段列表/fieldId 映射/清理列表、执行前 confirm 摘要；以新字段更新保存 baseline，不遗留上一配置值。
4. renderBatchConfigRow 的任务列表范围行显示当前配置值；历史API和通用 renderTaskDetailRawBlocks 原始JSON展示保持原样，新请求自然带新字段，不追加新的历史参数UI。旧快照反序列化默认false，不按当前配置倒推旧执行。
5. 新开关 change 同步 label，并触发现有预估/手动 diff；复用现有手动 input/change 总监听，避免重复绑定和重复请求。不得将新开关加入实时验证模板禁用逻辑。

### T2：统一人数文案（I-3/S-2）
文件：app.js。

新字段加入预估 snapshot。baseHintHtml 接收当前开关或由调用方传显式 enabled，使用选择的 response 追加排除数；refreshRecipientPreview 无门禁/门禁off/门禁on 三支全部覆盖。服务器旧 response 无新计数按0，不从两个响应差值推导邮箱排除数。只沿用原门禁最多两次请求。

### T3：真实 DOM 与行为测试（I-1/I-2/I-3/S-1/S-2）
文件：batchEmailVerification.test.js。补充新字段函数的 extractFn 输入和必要真实 DOM 断言；已有 realtime 测试继续覆盖材料提醒禁用，但不能把该限制套给历史过滤。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/resources/static/index.html` | 两个过滤区新增开关 |
| `src/main/resources/static/app.js` | 新建/编辑/来源/预估/确认/差异/历史快照贯通 |
| `src/test/js/batchEmailVerification.test.js` | 真实 DOM 与全链行为断言 |

共 3 个文件，一个前端子系统，零新 CSS。

## 验收标准

- I-1：新建true、存量false、来源true→手动false→请求false/来源仍true、重开编辑器不串值、clear source回true、历史无字段保持原始JSON且后端反序列化false；保存/预估/执行三种 JSON 都含同一布尔值。
- I-2：介绍↔材料切换，新开关保持值且可用；实时验证关闭、门禁 unavailable 时可独立开关；无模板也能显示其当前草稿值。
- I-3：mock 返回 on/关闭门禁两套不同 excluded 值，展示选中响应值；无门禁 early return 仍显示；两个请求而非四个；晚返回旧 seq 不覆盖；错误时不继续显示旧人数。
- S-1/S-2：两个新增 id 唯一且确实存在 HTML；DOM/class/文案对照合同；无 inline style、无新增 CSS；键盘 Tab/Space 可切换；1100px 与窄屏下说明自动换行，无横向遮挡。
- 命令：`node --test src/test/js/batchEmailVerification.test.js`；`node --test src/test/js/*.test.js`；`git diff --check`。若其他测试硬编码字段清单冲突，须报告准确文件并先修订范围，不静默改测试削弱断言。

## 人工验收清单

### A-1：位置、默认、独立性
- 前置条件：新版本本地 UI，一条迁移前配置；无须真实发信。
- 操作步骤：1. 新建任务。2. 编辑旧任务。3. 开独立手动页。4. 切材料提醒，关实时验证和模板门禁，切新开关。5. 用键盘操作并缩窄窗口。
- 预期结果：新开关位于过滤条件、紧邻模板门禁；新建/独立手动开启，旧任务关闭；材料/实时验证状态不禁用新开关；36×20开关、说明11px、行gap12px，无新增视觉样式和遮挡。
- 覆盖：I-1/I-2/S-1，IP-1。

### A-2：计数与来源差异
- 前置条件：保存开启过滤的任务，测试数据预估基础5、过滤后3、排除2；门禁可用与不可用各一套 fixture。
- 操作步骤：1. 手动选择来源。2. 关闭新开关，再打开。3. 对照网络 preview body/response。4. 查看执行确认，但先不发送。
- 预期结果：关闭显示5；开启显示3并“已排除不可用邮箱2位”；关闭产生“已修改/原值开启”；确认反映当前值；原任务仍开启。门禁不同分支均显示对应响应；快速切换无旧响应回盖。
- 覆盖：I-1/I-3/S-2，IP-1/IP-2/IP-3；原门禁预估与手动覆盖回归。

### A-3：执行与历史
- 前置条件：02 的 SMTP sink fixture；来源任务开启、新手动草稿关闭；已有旧执行请求无新字段。
- 操作步骤：1. 提交手动执行。2. 查请求快照和配置。3. 查看旧执行详情接口的 requestSnapshot。
- 预期结果：新执行记录false、来源配置true；旧请求仍缺字段，后端按false解释，不被当前配置污染；实时验证开关仍以自身值运行。
- 覆盖：I-1/I-2/S-2，IP-2。
