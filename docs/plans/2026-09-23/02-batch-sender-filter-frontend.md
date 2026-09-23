# 批量邮件发件账号筛选：前端计划（2/2）

> create-p 子计划；受 [MAIN 总计划](00-batch-sender-filter-main.md) 的顺序、接口与发布门槛约束。只创建计划，不授权执行。依赖 [后端计划](01-batch-sender-filter-backend.md) 的 `senderAccountCodes` API。本组是 `index.html`/`app.js` 当前基线的修改方（共享收件箱 01 尚未实施，其 V134/owner UI 已由人工批准作废顺延，见 `docs/plans/fast/2026-09-23-batch-sender-filter-main/ledger.md` 的 `## Amendments` A3）；实施前按当前文件重查 DOM、资源键和测试。

**目标**：定时任务编辑和独立手动执行都能从实时逻辑发件账号列表多选；配置保存、回显、手动差异、预估和启动快照传同一 `senderAccountCodes`。显示“已绑定专家会跳过”。

**技术栈**：原生 HTML/JS/CSS；复用现有 `batch-tag-picker`，本计划不改 CSS。

## 需求描述

- 可观察结果：两个面板显示“发件邮箱”多选，选项来自 `/api/mail/sender-accounts`；每项同时显示发件地址与账号代码，共享 IMAP 的别名仍分成两项。空选显示“全部可发送账号”。
- 可观察结果：定时配置保存再打开、从定时任务带入手动面板、独立手动执行、收件人预估，均使用当前选择；若修改了带入任务的发件账号，手动面板显示“已修改”和原值。
- 必须保持：当前模板、收件筛选、定时参数和手动差异判定；UI 现有颜色/布局/响应式样式；账号页现有账号管理行为；停用账号不会被误表示为可发送。
- 范围外：新账号管理页、新 CSS/设计系统、IMAP owner 设置、调整已绑定专家的绑定或回信页面。

## 关键不变量

### I-1：选项以逻辑账号为单位
- 规则：option `value` 是 `accountCode`，label 是 `senderEmail · accountCode`；禁止按 `inboundMailboxCode` 分组、去重或用邮箱域名推断身份。停用账号标注“已停用，本次不会发信”，历史已选值仍保留/可见，不能因列表刷新而静默删除。
- 适用路径：账号预加载、通用多选 registry、编辑/手动回显、保存。
- 违反后果：共享收件箱别名误合并或编辑配置时丢值。
- 来源：`MailSenderAccountController.kt:21-31,96-126`；共享收件箱计划 01 I-1/I-3；后端计划 I-1/I-5。

### I-2：所有前端快照同字段
- 规则：`buildConfigEditorRecipientSnapshot`、配置 POST/PUT、`deepCloneConfig`、`readManualFormValues`、`buildManualExecutionSnapshot` 都携带有序去重的 `senderAccountCodes`；空数组代表全部可发送账号。不得仅把该字段放在预估或仅放在保存请求。
- 适用路径：定时配置编辑/预估/保存、手动带入/差异/确认/预估/执行。
- 违反后果：页面选 A、后台执行仍从全池发件。
- 来源：`app.js:18161-18211,18307-18355,18404-18470,18530-18687,18782-18791`；K-recipient-count-preview-parity。

### I-3：真实 DOM 与现有样式
- 规则：只新增两个 `batch-config-field` 块，沿用既有 `.batch-tag-picker` DOM/JS 注册；不新增 class、不改 `styles.css`、不加 inline style。新增 HTML 中所有 id 与注册/回显/读取路径对应。`index.html` 的 11 个带版本资源键保持同值；执行前按当前键反查固定字面量测试。
- 适用路径：`index.html`、`app.js`、JS DOM 测试。
- 违反后果：测试绿但真实页面无控件，或缓存加载旧脚本。
- 来源：`index.html:1281-1315,1488-1529`、`styles.css:9336-9459`；K-dom-stub-tests-hide-dangling-refs、K-frontend-cache-key-triad。

## 样式契约

### S-1：定时配置多选
- 复用：`.batch-config-editor-section-heading span`（`styles.css:8999-9002`，11px/灰字）、`.batch-config-editor-grid`（`:9004-9008`，双列、`gap:14px 16px`）、`.batch-config-editor .batch-config-field`（`:9077-9082`）、`.batch-config-field-label`（`:9088-9094`）、`.batch-tag-picker`/`-control`/`-chips`/`-search`/`-chevron`/`-dropdown`（`:9336-9459`）。全部就地复用，不修改规则，不新增 CSS。
- 既有“收件范围”标题下的 `<span>所有条件同时生效，留空表示不限制</span>` 仅替换文字为 `<span>所有条件同时生效；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号</span>`；保留其父元素和 class。
- DOM：在定时“收件范围”的多选字段组内新增且仅新增下列块：
  ```html
  <div class="batch-config-field">
      <span class="batch-config-field-label">发件邮箱</span>
      <div class="batch-tag-picker" data-tag-picker="batchConfigEditorSenderAccounts">
          <div class="batch-tag-picker-control">
              <div id="batchConfigEditorSenderAccountsChips" class="batch-tag-picker-chips"></div>
              <input type="search" id="batchConfigEditorSenderAccountsSearch" class="batch-tag-picker-search" placeholder="搜索发件邮箱；不选则全部可发送账号" autocomplete="off" aria-controls="batchConfigEditorSenderAccountsDropdown" aria-expanded="false">
              <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
          </div>
          <input type="hidden" id="batchConfigEditorSenderAccounts" value="">
          <div id="batchConfigEditorSenderAccountsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
      </div>
  </div>
  ```
- 禁止项：新 class、inline style、改动现有 `.batch-tag-picker` CSS 或其他 DOM 层级。

### S-2：手动执行多选及差异
- 复用：`.batch-manual-section-heading span`（`styles.css:9289-9292`，11px/灰字）、`.batch-manual-section .batch-config-field`（`:9308-9313`）、`.batch-config-diff-badge`（`:9325-9331`）、`.batch-config-diff-original`（`:9333`），其余多选样式同 S-1；均不修改规则。
- 既有“模板与收件范围”标题下的 `<span>邮件类型根据模板自动确定；留空筛选项表示不限制</span>` 仅替换文字为 `<span>邮件类型根据模板自动确定；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号</span>`；保留其父元素和 class。
- DOM：在手动“收件范围”内新增且仅新增下列块：
  ```html
  <div class="batch-config-field" id="manualFieldSenderAccounts">
      <span class="batch-config-field-label">发件邮箱</span>
      <div class="batch-tag-picker" data-tag-picker="batchManualSenderAccounts">
          <div class="batch-tag-picker-control">
              <div id="batchManualSenderAccountsChips" class="batch-tag-picker-chips"></div>
              <input type="search" id="batchManualSenderAccountsSearch" class="batch-tag-picker-search" placeholder="搜索发件邮箱；不选则全部可发送账号" autocomplete="off" aria-controls="batchManualSenderAccountsDropdown" aria-expanded="false">
              <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
          </div>
          <input type="hidden" id="batchManualSenderAccounts" value="">
          <div id="batchManualSenderAccountsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
      </div>
      <span class="batch-config-diff-badge" hidden>已修改</span>
      <div class="batch-config-diff-original" hidden></div>
  </div>
  ```
- 禁止项：新 class、inline style、改动原有手动差异样式。

## 现状审计

### 前端配置与账号读取
- 后端既有只读账号 API：`MailSenderAccountController.listAccounts:22-26` 返回 `accountCode/senderEmail/enabled/autoSendPaused`；`MailSenderAccountService.listAccounts:20-21` 从 DB 取当前账号。页面账号管理的 `loadAccounts`（`app.js:3045-3046`）已调用它，但批量弹窗不保证账号页先打开，所以在 `preloadBatchSendLookups:17173-17185` 独立请求同 API。
- 写路径：配置表单 `saveBatchConfigEditor`（`app.js:18307-18355`）发 POST/PUT；`buildConfigEditorRecipientSnapshot`（`:18161-18189`）发预估快照；`confirmManualExecution`（`:18782-18791`）发 `buildManualExecutionSnapshot`。`deepCloneConfig`（`:18404-18425`）和 `fillManualFormDefaults/FromDraft`（`:18429-18470`）决定手动初值。读路径：`showBatchConfigEditor:17400-17454` 回显；`readManualFormValues:18530-18559`、`computeManualDiffs:18612-18648` 读取差异。
- 多选注册：`BATCH_MULTI_PICKER_REGISTRY:17785-17815` 提供动态 options；`bindBatchMultiPicker:17946-17971` 绑定；`bindBatchSendTaskEvents:19345-19350` 当前注册 6 个多选。新增两个必须同时进 registry 与事件注册。
- 列表摘要：`app.js:17259` 把现有收件筛选拼成 `scopeParts`，可新增“发件邮箱: A/B”展示；未知 code 应显示 code 原值，不静默隐藏。
- 交互点：账号 API → 选项 label/value；配置 API → 表单回显 → 再保存；手动 source → draft → diff → 启动快照；表单改值 → 预估快照。

### 前端样式盘点
- 可复用 class：S-1/S-2 上述 class；`.batch-tag-picker-chip`（`styles.css:9363-9375`，11px/圆角 999px）、`.batch-tag-picker-option`（`:9420-9432`，12px）、hover/selected（`:9434-9438`）；`.batch-config-field`（`:9294-9300`）被其他批量字段使用，本计划只复用，不就地修改。
- 设计基准 token：`:root` 的 `--primary:#1e40af`、`--primary-hover:#1e3a8a`、`--panel-bg:rgba(255,255,255,0.55)`、`--text-main:#1e293b`、`--text-muted:#94a3b8`、`--border-strong:#cbd5e1`、`--z-dropdown:20`（`styles.css:1-60`）；picker 控件最小高 42px、内边距 `6px 36px 6px 10px`、边框 `rgba(15,23,42,.12)`、focus 阴影 `0 0 0 3px rgba(37,99,235,.1)`、下拉最大高 230px（`:9341-9418`）。
- 改动前 HTML 基线（逐字摘录，定时与手动现有同族字段）：
  ```html
  <div class="batch-config-field">
      <span class="batch-config-field-label">邮箱服务商</span>
      <div class="batch-tag-picker" data-tag-picker="batchConfigEditorEmailDomains">
          <div class="batch-tag-picker-control">
              <div id="batchConfigEditorEmailDomainsChips" class="batch-tag-picker-chips"></div>
              <input type="search" id="batchConfigEditorEmailDomainsSearch" class="batch-tag-picker-search" placeholder="搜索并选择邮箱服务商" autocomplete="off" aria-controls="batchConfigEditorEmailDomainsDropdown" aria-expanded="false">
              <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
          </div>
          <input type="hidden" id="batchConfigEditorEmailDomains" value="">
          <div id="batchConfigEditorEmailDomainsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
      </div>
  </div>
  ```
  手动对应字段 `index.html:1516-1529` 在同级 `<div class="batch-config-field" id="manualFieldEmailDomain">` 内，并在 picker 后含既有 `.batch-config-diff-badge`/`.batch-config-diff-original`。对应 CSS 的完整规则由 S-1/S-2 行号锁定，不修改。
- 资源键：当前 `index.html:11-15,2168-2173` 共 11 个 `20260922-discovery-continuous`；本次 `rg -l '20260922-discovery-continuous' src/test --hidden --no-ignore` 为 0 命中。执行前重查；如果测试出现固定旧键，先将其列入修订后变更文件清单，不得漏改。

## 实现方案

### 阶段 1：真实账号选项与 DOM（I-1、I-3、S-1、S-2）
- 文件：`src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/test/js/batchSenderFilter.test.js`。
- 先写 JS 测试读取真实 `index.html`，断言 S-1/S-2 中新增的所有 id/data-tag-picker 与 JS 读取、注册处对齐（不能只用 DOM stub）。再按契约加两个块。批量弹窗预加载 `/api/mail/sender-accounts` 到 `batchTaskState.preloadedSenderAccounts`；在 registry 注册两个 picker、在 `bindBatchSendTaskEvents` 绑定。label 使用 `senderEmail · accountCode`，停用加“已停用，本次不会发信”；value 始终原始 `accountCode`。数组不按 IMAP owner 去重。

### 阶段 2：回显/保存/手动/预估（I-1、I-2、S-1、S-2）
- 文件同阶段 1。定时 `showBatchConfigEditor`、`saveBatchConfigEditor`、`buildConfigEditorRecipientSnapshot`；手动 `deepCloneConfig`、`fillManualFormDefaults/FromDraft`、`readManualFormValues`、`normalizeManualSnapshot`、`formatManualDiffValue`、`computeManualDiffs/computeAndRenderDiffs`、`buildManualExecutionSnapshot` 均接入 `senderAccountCodes`。独立手动默认 `[]`；手动从配置带入真实列表。预估请求沿用现有防抖/序号机制，不新建 API。
- 账号列表暂时加载失败时保留 hidden input 已选 code，显示已有 chip/code，不能替换成 `[]`；保存仍以该值为准，服务器校验 code。按 S-1/S-2 更新两个既有标题副文案；材料提醒也显示该提示。
- 测试：配置 A 保存→GET 回显 A→手动带入 A→改 B 的差异→预估/执行快照为 B；独立手动空选为 `[]`；停用历史 code 不被回显和保存过程抹掉；共享 inbox 的两个逻辑 code 出现两个 option。

### 阶段 3：缓存与静态检查（I-3、S-1、S-2）
- 文件同阶段 1。执行前重扫 `index.html` 当前 `?v=` 值与 `src/test` 固定字面量；把 11 个资源键统一改为同一新值，不新加 script/link。若固定键测试文件不为 0，先修订本计划文件清单（≤10）再动代码。

## 变更文件清单

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 两个 picker、提示、缓存键 |
| 2 | `src/main/resources/static/app.js` | 账号选项、所有表单/快照路径 |
| 3 | `src/test/js/batchSenderFilter.test.js` | 真实 DOM 与 payload/diff 回归 |

## 验收标准

- I-1：两个同 inbox 的逻辑账号为两个 value；label 含各自 senderEmail/accountCode；禁用账号可辨认且已有选中值不丢。
- I-2：定时 create/update/get、编辑器预估、手动 source/diff/预估/启动均读写同一 `senderAccountCodes`；空选确实发 `[]`。
- I-3：测试实际解析 `index.html`，所有新增 id、picker registry 和绑定调用存在；11 个资源 `?v=` 同值，静态资源测试通过。
- S-1/S-2：DOM 与契约逐字对应；`styles.css` 无 diff、新增元素无 inline style/未声明 class；宽屏双列、窄屏单列及 chip/focus/dropdown/“已修改”样式目测符合上述数值。
- 运行：`node --check src/main/resources/static/app.js`、`node --test src/test/js/batchSenderFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js`。若全量 JS 测试出现与当前键有关的失败，仅按反查到的固定键文件修订计划后处理。

## 人工验收清单

### A-1：定时配置保存与回显
- 前置条件：账号 A/B 均 enabled，有不同 senderEmail；打开批量邮件任务控制台。
- 操作步骤：新增介绍邮件任务，在“发件邮箱”选 A，保存后重新打开编辑。
- 预期结果：只显示 A 的 chip，文字含 A 的邮箱地址和账号代码；保存的任务详情 `senderAccountCodes` 为 `["A"]`；页面提示“已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号”。
- 覆盖：I-1、I-2、I-3、S-1、配置写→读。

### A-2：手动带入与差异
- 前置条件：沿用只选 A 的定时任务。
- 操作步骤：手动执行页选择该任务，确认 A 已带入；改选 B，打开差异/确认，再查看预估与启动请求。
- 预期结果：“发件邮箱”显示“已修改”，原值 A、新值 B；预估和执行请求均含 `senderAccountCodes:["B"]`，定时配置仍为 `["A"]`。
- 覆盖：I-2、S-2、source→draft→diff→snapshot。

### A-3：同物理收件箱的两个逻辑账号与停用状态
- 前置条件：A/B 是同一物理 IMAP 收件箱的两个逻辑发件账号（如现网 LuKai/LuKai_QF），senderEmail 不同；B 已停用且原配置选了 B。
- 操作步骤：打开配置编辑，再打开手动执行选择器。
- 预期结果：A/B 为不同选项；B 标“已停用，本次不会发信”，原配置的 B chip 不消失；保存别的字段后仍回显 B。
- 覆盖：I-1、I-3、账号 API→选项→配置保留。

### A-4：空选与样式回归
- 前置条件：新建独立手动执行，无来源任务；宽屏和窄屏各查看一次。
- 操作步骤：不选择发件邮箱，查看两个面板的 picker、focus、下拉和 chip；再选/移除一个账号。
- 预期结果：空选请求为 `[]`，提示“发件邮箱留空使用全部可发送账号”；控件最小高 42px、focus 为 3px 蓝色淡阴影、下拉最大高 230px；宽屏双列、窄屏单列，原有模板/收件筛选值不变。
- 覆盖：I-2、I-3、S-1、S-2、must-not-change。
