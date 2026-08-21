# QA 事实编辑框门禁可见化改造

> 计划创建：2026-08-21 · 使用 create-p skill
> 关联记录：`docs/plans/2026-08-04/trust-reply-atomic-facts-and-duplicate-guard.md`（V82 受控门禁来源）、
> `docs/plans/2026-07-17/qa-refactor-02-fact-card-foundation.md`（当年移除 coverage UI 的决策）
> 交互原型：`docs/mockups/qa-fact-editor-gate-preview.html`
>
> **执行顺序（重要）**：本计划与同日的 `ui-tweaks-00-execution-order.md` 所列三份计划
> （P1 `ui-tweaks-01` / P2 `ui-tweaks-02` / P3 `ui-tweaks-03`）**共同修改**
> `src/main/resources/static/index.html`、`app.js` 与
> `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 的缓存键三键（K-frontend-cache-key-triad），
> 四份计划**必须串行**。本计划排**第四**，缓存键取 `20260821-v12-qa-coverage-gate`
> （P1=v9 / P2=v10 / P3=v11 已占用）。
> 除缓存键外与三份 ui-tweaks **无代码耦合**：它们改的是「检查回复」按钮位置、自动回复预览入口、
> 浮层对比度与人工回复主题预填，均不触及 `#qaRuleModal` 内部、`QaCoverageKeyCatalog` 或
> `qa_rule` 的任何列。若实际只执行本计划一份，把缓存键改为 `20260821-v9-qa-coverage-gate` 即可。

## 需求描述

### Observable outcome

1. 打开任意 QA 事实编辑框，能直接看到这条规则被授权了哪些「AI 覆盖能力」，其中哪些是受控事实（对外法律承诺）。
2. 勾选/取消覆盖能力时，编辑框实时给出中文门禁判定；保存被拦截时，界面点名是哪个事实族、为什么拦、以及可执行的出路（恢复标准正文 / 解除本规则对该事实的授权），不再只弹一句英文内部术语。
3. 规则 24《Program overview》恢复为可保存、可启用。
4. 今后新建的「总览型」规则（正文顺带提及承诺、但不是承诺的权威出处）不再被门禁误伤。

### What must NOT change

- **后端仍是唯一权威**：受控事实的「唯一权威出处」与「正文与授权绑定」两个不变量不放宽；前端预判不得成为放行依据。
- 四段 canonical 正文本轮**仍是 Kotlin 常量**，运营不能从 UI 改写它们。
- 规则 24 的 `answer_body` / `reply_body` **一字不动**（对外发信措辞不变）。
- `setRuleEnabled(enabled=true)` 仍复验已存受控规则（K-qa-rule-enable-must-revalidate-facts）。
- QA 规则表格（`#qaRulesTable`）列结构不变，仍是事实卡 9 列布局，不恢复「AI 覆盖能力」列。
- `QaFactBodyPolicy`、占位符校验、reply snippet 的变体编辑器行为不变。

### Out of scope（显式延后）

- **C 阶段**：canonical 正文入库（DB 化）、变更受控事实的二次确认流程、新增 QA 规则变更审计事件（`OperatorActionType` 目前无任何 QA 规则编辑类事件）。单独排一轮。
- 「解除授权」的二次口令 / 管理员权限门槛 —— 需求方 2026-08-21 明确不需要。
- 受控规则正文置灰只读 —— 需求方 2026-08-21 明确选择保持可编辑。
- 修复 K-coverage-key-orphan-makes-fact-unreachable 记录的 5 个孤儿覆盖键。
- 规则 24 正文里「不收费、材料保密」那句的去留（需求方选择保留）。
  > **已知代价（需求方 2026-08-21 知情接受）**：摘掉这两个键后，24 号规则在 grounded 链路里不再为
  > 费用/保密类 intent 供证。总览型来信若同时问到费用或保密，将由 V82 拆出的
  > 《Participant fee policy》/《Application material confidentiality》两条原子规则供证；
  > 若这两条未命中，则该子问题判为缺口。缺口检测与 `supersedesChildren` 的既有耦合见
  > K-overview-gap-supersede —— 本轮**不动**该逻辑，但上线后需观察总览型来信的转人工率是否上升。

### 与既有决策的关系（Decision Log Protocol）

`qa-refactor-02-fact-card-foundation.md:30` 当年写的是：
> 本阶段管理 UI 不展示 `…coverageKeys…`；`coverageKeys` 请求字段忽略并保留 existing，**直到 grounded 引擎切换**。

grounded 引擎已切换（plan 04/06 与 V82 均已落地），该条的生效条件已解除。
本计划**不是重开已关闭的决策**，而是履行其明写的解除条件。同计划 :151 把 `/coverage-keys` 标为
deprecated「暂保留给旧前端」，本轮取消该 deprecated 标注。

---

## 关键不变量

### Invariant I-1：门禁触发条件是「恰为某受控组」，不是「含任一受控键」
- Rule：`QaCoverageKeyCatalog.validateControlledBody(coverageKeys, answerBody)` 仅在
  `coverageKeys` 的集合**恰好等于**四个受控组之一时执行正文一致性校验；否则一律放行，
  **即使集合里出现了受控键**。受控组仍是：`[confidentiality.materials]`、`[fees.policy]`、
  `[contract.party,contract.terms]`、`[ip.arrangements]`。
- Applies to：`QaRuleManagementService.createRule`（:75）、`updateRule`（:105）、
  `setRuleEnabled(…, true)`（:138）三条调用点，语义必须一致。
- Violation consequence：维持现状则任何顺带提及承诺的总览型规则永久不可保存（规则 24 即是）；
  反向放宽（完全不校验）则受控事实的正文可被无声改写并以「依据充分」姿态外发。
- 来源：original（依据 `trust-reply-atomic-facts-and-duplicate-guard.md:213` 的验收标准
  「Program overview 等非受控 legacy 规则不被该门禁拒绝」，该标准当前未落地）

### Invariant I-2：前端评估是提示，后端是权威
- Rule：前端依据 `/api/qa/coverage-keys` 下发的元数据在本地复算门禁状态，仅用于渲染提示与禁用保存按钮。
  后端 `validateControlledBody` 的判定逻辑**不得**因为前端已预判而放宽；前端**不得**新增任何
  「跳过后端校验」的旁路参数。二者判定不一致时以后端 400 为准，前端必须能显示该错误。
- Applies to：`app.js` 新增的门禁评估函数、`QaRuleManagementService` 三条调用点。
- Violation consequence：把 UI 状态当作发送授权，等于把安全边界搬到浏览器（同类前车之鉴见
  K-ai-adopt-direct-send-no-residual-gates：前端 preflight 不得成为发送 authority）。
- 来源：K-ai-adopt-direct-send-no-residual-gates（同构约束，跨模块套用）

### Invariant I-3：`saveQaRule` 必须显式发送 `coverageKeys`，空选也要发空数组
- Rule：前端提交 payload 必须始终携带 `coverageKeys` 字段（`string[]`，可为 `[]`）。
  **禁止**在「未勾选任何键」时省略该字段或发 `null`。
- Applies to：`app.js:saveQaRule`；对应后端 `updateRule` 的 `command.coverageKeys == null → parseStored(existing)`
  分支（`QaRuleManagementService.kt:101-103`）。
- Violation consequence：发 `null` 会让后端保留库存旧值 —— 运营在界面上取消的勾选被静默丢弃，
  界面显示与库内实际不符，且门禁仍按旧值判定。这正是当前 24 号规则不可修复的直接机制。
- 来源：K-qa-coverage-keys-management-write-boundary（该条已记录 update 传 null 保留 existing 的语义）

### Invariant I-4：解除授权只能取消本规则的键，受控组定义不可从 UI 变更
- Rule：「解除授权」按钮的唯一效果是从**当前编辑中的规则**的 `coverageKeys` 里移除该受控组的全部键
  （`contract.party`+`contract.terms` 必须同进同出）。UI 不提供任何修改/删除 `controlledCoverageGroups`
  常量或 canonical 正文的入口，后端也不接受此类请求。
- Applies to：`app.js` 解除授权处理函数；`QaRuleManagementController` 不新增任何写受控组的端点。
- Violation consequence：从界面卸掉门禁 = 任何人可改写一句对外法律承诺且无人留痕
  （本轮尚无 QA 规则变更审计，见 Out of scope）。
- 来源：original

### Invariant I-5：最后授权源判定只提示、不拦截
- Rule：解除授权确认卡展示「解除后该事实是否还有其它权威出处」。判定口径 =
  **其它 `enabled=1` 且 `parseStored(coverage_keys)` 包含该键的规则**（排除当前规则自身）。
  该判定**不参与**保存放行与否，仅改变确认卡的文案与配色。
- Applies to：新增只读端点 `GET /api/qa/coverage-keys/authorities`；`app.js` 确认卡渲染。
- Violation consequence：若拿它做拦截，会在「运营正要重建授权链路」的中间态把人锁死；
  若不展示，运营会在毫不知情的情况下让某类高风险问题永久失去依据（后果见
  K-coverage-key-orphan-makes-fact-unreachable：非空但无人引用 = 事实永久不可达，命中率恒 0）。
- 来源：K-coverage-key-orphan-makes-fact-unreachable

### Invariant I-6：V107 只改 24 号的 `coverage_keys`，正文与时间戳不动
- Rule：迁移仅对 `qa_rule.id = 24` 执行 `coverage_keys` 覆写，去掉 `fees.policy` 与
  `confidentiality.materials`，保留其余 9 个键的原有顺序；**不得**触及 `answer_body`、`reply_body`、
  `keywords`、`enabled`、`priority`；必须显式 `updated_at = updated_at`。
  必须带 `WHERE` 基线守卫（校验现值确实是 V76 回填的那 11 键串），基线不符则不写。
- Applies to：`V107__strip_controlled_keys_from_program_overview.sql`。
- Violation consequence：不带基线守卫会覆盖运营在 UI 上的运行时改动（K-qa-rule-runtime-vs-migration-writes）；
  不保时间戳会把该行误标为运营更新（K-qa-migration-preserve-auto-updated-timestamp，反例 V79）。
- 来源：K-qa-rule-runtime-vs-migration-writes + K-qa-migration-preserve-auto-updated-timestamp

### Invariant I-7：覆盖键复选框读取契约 —— 全部键常驻 DOM
- Rule：`#qaCoverageKeyOptions` 内，目录里的**每一个**覆盖键都有一个常驻的
  `input.qa-cov-input[data-coverage-key]` 复选框；收集函数的契约是
  「遍历容器内全部 `.qa-cov-input`，取 `checked` 者的 `data-coverage-key`」。
  分组折叠、搜索过滤等后续改造只允许用**显隐**控制可见性，严禁把未显示的键移出 DOM
  或改由 JS 数组托管选中值。
- Applies to：`renderQaCoverageKeyOptions`、新增的 `collectQaCoverageKeys`、`saveQaRule`。
- Violation consequence：与内容变体编辑器同类事故 —— 保存时静默丢键。
- 来源：K-content-variant-input-read-contract（同构契约，跨组件套用）

### Invariant I-8：三个静态资源缓存键同值同时 bump
- Rule：`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 必须改为同一个新值，
  且 `batchSendTaskConsoleVisualFix.test.js:49-51` 的三条硬编码断言同步更新。
- Applies to：`index.html:11/2074/2075`、`src/test/js/batchSendTaskConsoleVisualFix.test.js:48-52`。
- Violation consequence：只 bump 部分键 → 构建期 node 测试失败，WAR 构建中止（2026-08-13 实测踩过）。
- 来源：K-frontend-cache-key-triad

---

## 样式契约

> 既有样式引用行号，新增样式逐字给出。执行 agent 只许复制，不许改写。
> 全部新增 class 统一 `qa-cov-` / `qa-gate-` 前缀 —— 已 grep 确认 `styles.css` 现存
> `.gate-filter-summary`（:615-643）与本前缀不冲突，无 `.qa-cov-` / `.qa-gate-` 任何定义。

### S-1：覆盖能力面板外壳
- 复用：`.field-label`（styles.css:955 附近的 `label` 基线）、`.badge` / `.badge.warn` / `.badge.error`
  （styles.css:900/920/926）、`.span-2`（styles.css:1039）、`.button` / `.button.secondary`（styles.css:655/705）。
  禁止自造「近似」的面板/徽章样式替代以上 class。
- DOM 结构（插入位置：`index.html` `#qaRuleForm` 内，「回复策略」`<label>` 之后、
  「标准事实正文」`<label class="span-2 qa-fact-body-field">` 之前）：

```html
<div class="qa-cov-panel span-2">
    <div class="qa-cov-head">
        <span class="qa-cov-title">AI 覆盖能力（事实授权）</span>
        <span class="qa-cov-count" id="qaCoverageKeyCount"></span>
        <span class="qa-cov-spacer"></span>
        <button type="button" class="qa-cov-why" id="qaCoverageWhyBtn">为什么有的能力带锁？</button>
    </div>
    <div class="qa-cov-why-body" id="qaCoverageWhyBody" hidden></div>
    <div class="qa-cov-chips" id="qaCoverageKeyChips"></div>
    <div class="qa-cov-body" id="qaCoverageKeyOptions"></div>
    <p class="qa-cov-warning" id="qaCoverageKeyWarning" hidden></p>
</div>
```

- 新增 CSS（原样追加到 `styles.css` 末尾）：

```css
/* ── QA 事实：覆盖能力面板（事实授权） ───────────────────────── */
.qa-cov-panel {
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.55);
    overflow: hidden;
}

.qa-cov-head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 10px 12px;
    background: var(--surface);
    border-bottom: 1px solid var(--line);
}

.qa-cov-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-strong);
    letter-spacing: 0.2px;
}

.qa-cov-count {
    font-size: 11px;
    font-weight: 500;
    color: var(--text-muted);
}

.qa-cov-spacer {
    flex: 1;
}

.qa-cov-why {
    border: none;
    background: none;
    color: var(--primary);
    font-size: 11px;
    cursor: pointer;
    text-decoration: underline;
    text-underline-offset: 2px;
    padding: 0;
}

.qa-cov-why-body {
    padding: 10px 12px;
    font-size: 12px;
    line-height: 1.65;
    color: var(--text-secondary);
    background: var(--info-bg);
    border-bottom: 1px solid var(--info-border);
}

.qa-cov-body {
    padding: 6px 12px 12px;
    max-height: 300px;
    overflow-y: auto;
}

.qa-cov-group-title {
    margin: 10px 0 4px;
    font-size: 11px;
    font-weight: 700;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.4px;
}

.qa-cov-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 2px 10px;
}

.qa-cov-warning {
    margin: 0;
    padding: 8px 12px;
    font-size: 12px;
    color: var(--warning-strong);
    background: var(--warning-bg);
    border-top: 1px solid var(--warning-border);
}

@media (max-width: 820px) {
    .qa-cov-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
```

- 禁止项：inline style；未在本契约声明的新 class；修改 `.badge` / `.button` / `.field-label` 既有规则块。

### S-2：覆盖键条目（含受控标记）
- 复用：无（`.checkbox-row`（styles.css:1012）是**纵向表单行**语义，此处是三列密排网格，不复用）。
- DOM 结构（由 `renderQaCoverageKeyOptions` 生成，每个键一条）：

```html
<label class="qa-cov-item is-controlled checked" title="申请/项目各阶段是否收费">
    <input type="checkbox" class="qa-cov-input" data-coverage-key="fees.policy" checked>
    <span class="qa-cov-text">费用政策</span>
    <span class="qa-cov-lock">受控</span>
</label>
```

`is-controlled` 仅受控键有；`checked` 随勾选态增删；非受控键不渲染 `.qa-cov-lock`。

- 新增 CSS（原样追加）：

```css
.qa-cov-item {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 7px;
    min-height: 28px;
    padding: 2px 6px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 500;
    color: var(--text-main);
    font-family: var(--font-body);
    text-transform: none;
    letter-spacing: 0;
    cursor: pointer;
}

.qa-cov-item:hover {
    background: var(--primary-light);
}

.qa-cov-item input.qa-cov-input {
    width: 14px;
    height: 14px;
    min-height: auto;
    border-radius: 2px;
    accent-color: var(--primary);
    cursor: pointer;
    flex: 0 0 auto;
}

.qa-cov-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.qa-cov-lock {
    flex: 0 0 auto;
    font-size: 10px;
    font-weight: 700;
    padding: 1px 5px;
    border-radius: 5px;
    background: var(--warning-bg);
    color: var(--warning-strong);
    border: 1px solid var(--warning-border);
}

.qa-cov-item.is-controlled.checked {
    background: var(--warning-bg);
    box-shadow: inset 0 0 0 1px var(--warning-border);
}
```

- 注意：`label` 基线规则（`styles.css` 的裸 `label`）是 `flex-direction: column` +
  `text-transform: uppercase`，**必须**由 `.qa-cov-item` 的 `flex-direction: row` /
  `text-transform: none` / `letter-spacing: 0` 显式覆盖 —— 漏掉任一条会得到「复选框在上、
  文字在下」的错位（原型阶段实测踩到）。

### S-3：当前授权 chip 行
- 复用：视觉基线取自既有 `.var-chip`（styles.css:5750-5758，`border-radius: 999px; padding: 2px 10px;
  font-size: 12px`），但因需要受控/普通两种配色与内嵌删除按钮，派生新 class，**不就地修改 `.var-chip`**。
- DOM 结构（由 `renderQaCoverageKeyChips` 生成）：

```html
<span class="qa-cov-chips-label">当前授权</span>
<span class="qa-cov-chip is-controlled" title="fees.policy">🔒 费用政策<span class="qa-cov-chip-x" data-coverage-unpick="fees.policy">×</span></span>
<span class="qa-cov-chip" title="application.steps">申请步骤<span class="qa-cov-chip-x" data-coverage-unpick="application.steps">×</span></span>
```

未勾选任何键时，容器内只渲染：
```html
<span class="qa-cov-chips-label">当前授权</span>
<span class="qa-cov-chips-none">未勾选任何能力 —— AI 不会引用这条事实作答</span>
```

- 新增 CSS（原样追加）：

```css
.qa-cov-chips {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    padding: 9px 12px;
    background: rgba(255, 255, 255, 0.5);
    border-bottom: 1px solid var(--line);
}

.qa-cov-chips-label {
    font-size: 11px;
    font-weight: 700;
    color: var(--text-muted);
    margin-right: 2px;
}

.qa-cov-chips-none {
    font-size: 11.5px;
    color: var(--text-muted);
}

.qa-cov-chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 3px 9px;
    border-radius: 999px;
    font-size: 11.5px;
    font-weight: 600;
    background: var(--primary-light);
    color: var(--primary);
    border: 1px solid rgba(var(--primary-rgb), 0.2);
}

.qa-cov-chip.is-controlled {
    background: var(--warning-bg);
    color: var(--warning-strong);
    border-color: var(--warning-border);
}

.qa-cov-chip-x {
    cursor: pointer;
    opacity: 0.55;
    font-weight: 700;
}

.qa-cov-chip-x:hover {
    opacity: 1;
}
```

### S-4：门禁状态条
- 复用：`--success-bg` / `--success-border` / `--warning-bg` / `--warning-border` / `--error-bg` /
  `--error-border` / `--border-strong`（styles.css:36-50 的 `:root` token），不得写死 hex。
- DOM 结构（插入位置：S-1 面板之后、「标准事实正文」`<label>` 之前）：

```html
<div class="qa-gate span-2" id="qaCoverageGate"></div>
```

内部由 `renderQaCoverageGate()` 生成，四种形态共用骨架：

```html
<div class="qa-gate-line"><span class="qa-gate-ic">⚠</span><div>…一句话结论…</div></div>
<div class="qa-gate-sub">…为什么…</div>
<div class="qa-gate-actions">
    <button type="button" class="qa-gate-btn" data-gate-act="canon">查看差异</button>
    <button type="button" class="qa-gate-btn" data-gate-act="restore" data-gate-group="G2">恢复标准正文</button>
    <button type="button" class="qa-gate-btn danger" data-gate-act="revoke" data-gate-group="G2">解除本规则对「费用政策」的授权…</button>
</div>
```

外层 `#qaCoverageGate` 的 class 随状态取 `qa-gate ok` / `qa-gate warn` / `qa-gate error` 三者之一。

- 新增 CSS（原样追加）：

```css
.qa-gate {
    display: flex;
    flex-direction: column;
    gap: 9px;
    padding: 11px 13px;
    border: 1px solid;
    border-radius: var(--radius-md);
}

.qa-gate.ok {
    background: var(--success-bg);
    border-color: var(--success-border);
}

.qa-gate.warn {
    background: var(--warning-bg);
    border-color: var(--warning-border);
}

.qa-gate.error {
    background: var(--error-bg);
    border-color: var(--error-border);
}

.qa-gate-line {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    font-size: 12.5px;
    line-height: 1.6;
}

.qa-gate.ok .qa-gate-line {
    color: var(--success);
}

.qa-gate.warn .qa-gate-line {
    color: var(--warning-strong);
}

.qa-gate.error .qa-gate-line {
    color: var(--error-strong);
}

.qa-gate-ic {
    flex: 0 0 auto;
    font-size: 14px;
    line-height: 1.35;
}

.qa-gate-sub {
    margin-left: 23px;
    font-size: 12px;
    line-height: 1.65;
    color: var(--text-secondary);
}

.qa-gate-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 7px;
    margin-left: 23px;
}

.qa-gate-btn {
    padding: 5px 11px;
    border: 1px solid var(--border-strong);
    border-radius: 7px;
    background: #fff;
    color: var(--text-strong);
    font-size: 12px;
    font-weight: 600;
    font-family: var(--font-body);
    cursor: pointer;
    transition: var(--transition);
}

.qa-gate-btn:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.qa-gate-btn.danger {
    border-color: var(--error-border);
    color: var(--error-strong);
}

.qa-gate-btn.danger:hover {
    background: var(--error-bg);
    border-color: var(--error);
}

.qa-gate-btn[disabled] {
    opacity: 0.5;
    cursor: not-allowed;
}
```

### S-5：标准承诺对照与解除授权确认卡
- 复用：无。
- DOM 结构：

```html
<div class="qa-gate-canon">
    <div class="qa-gate-canon-row"><div class="qa-gate-canon-label">标准承诺</div>…逐词 diff…</div>
    <div class="qa-gate-canon-row"><div class="qa-gate-canon-label">当前正文</div>…逐词 diff…</div>
</div>
<div class="qa-gate-revoke">
    <h4>解除本规则对「费用政策」的授权？</h4>
    <div class="qa-gate-revoke-impact">…影响列表…</div>
    <div class="qa-gate-revoke-foot">
        <button type="button" class="qa-gate-btn danger" data-gate-act="revoke-do" data-gate-group="G2">确认解除授权</button>
        <button type="button" class="qa-gate-btn" data-gate-act="revoke-cancel">再想想</button>
    </div>
</div>
```

- 新增 CSS（原样追加）：

```css
.qa-gate-canon {
    margin-left: 23px;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: #fff;
    overflow: hidden;
}

.qa-gate-canon-row {
    padding: 9px 11px;
    font-size: 12px;
    line-height: 1.7;
}

.qa-gate-canon-row + .qa-gate-canon-row {
    border-top: 1px dashed var(--border);
}

.qa-gate-canon-label {
    margin-bottom: 4px;
    font-size: 10.5px;
    font-weight: 700;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.4px;
}

.qa-gate-canon-row .del {
    padding: 0 2px;
    border-radius: 3px;
    background: rgba(var(--error-rgb), 0.14);
    color: var(--error-strong);
    text-decoration: line-through;
}

.qa-gate-canon-row .ins {
    padding: 0 2px;
    border-radius: 3px;
    background: rgba(var(--success-rgb), 0.16);
    color: var(--success);
}

.qa-gate-revoke {
    margin-left: 23px;
    padding: 11px 13px;
    border: 1px solid var(--border-strong);
    border-radius: 9px;
    background: #fff;
}

.qa-gate-revoke h4 {
    margin: 0 0 7px;
    font-size: 12.5px;
    font-weight: 700;
    color: var(--text-strong);
}

.qa-gate-revoke-impact {
    font-size: 12px;
    line-height: 1.7;
    color: var(--text-secondary);
}

.qa-gate-revoke-impact ul {
    margin: 5px 0 0;
    padding-left: 18px;
}

.qa-gate-revoke-impact li {
    margin: 3px 0;
}

.qa-gate-revoke-impact .impact-bad {
    color: var(--error-strong);
    font-weight: 600;
}

.qa-gate-revoke-impact .impact-ok {
    color: var(--success);
    font-weight: 600;
}

.qa-gate-revoke-foot {
    display: flex;
    gap: 7px;
    margin-top: 10px;
}
```

### S-6：保存拦截提示
- 复用：`.form-actions`（styles.css:1044）、`.button.primary`（styles.css:691）。
- DOM 结构（插入位置：`#qaRuleForm` 内 `.form-actions` **之前**）：

```html
<div class="qa-gate-save-block span-2" id="qaCoverageSaveBlock" hidden></div>
```
- 新增 CSS（原样追加）：

```css
.qa-gate-save-block {
    margin-top: -2px;
    font-size: 12px;
    color: var(--error-strong);
    text-align: right;
}
```
- 保存按钮拦截态复用原生 `disabled` 属性 + 上面 `.qa-gate-btn[disabled]` 同款视觉；
  `.button` 的既有 `[disabled]` 表现不改写。

---

## 现状审计

### `qa_rule` 表（MySQL，Spring Data JDBC）
- Schema：V1 建表；相关列 `coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`（V76 新增）、
  `answer_body`（V79 新增）、`reply_body`、`enabled`、`display_name`、`reply_policy`（V80）。
  表带 `ON UPDATE CURRENT_TIMESTAMP` 的 `updated_at`。
- `coverage_keys` 写路径（grep 回执见下）：
  1. `QaRuleManagementService.createRule`（`:76` serialize → `:83` 落库）—— 运营新建。
  2. `QaRuleManagementService.updateRule`（`:106-108`）—— 运营编辑；**`command.coverageKeys == null`
     时保留 `existing.coverageKeys`**。
  3. Flyway 迁移：`V76`（回填 15 条既有规则）、`V82`（4 条新原子规则的 INSERT 列）、
     `V105`（2 条 programme identity 规则的 INSERT 列）。
  > grep 回执：
  > ```
  > $ grep -rn "coverage_keys" src/main/resources/db/migration/ | cut -d: -f1 | sort -u
  > V105__add_programme_identity_facts.sql
  > V76__add_qa_rule_coverage_keys.sql
  > V82__split_trust_reply_atomic_facts.sql
  > $ grep -n "coverageKeys" src/main/kotlin/.../qa/service/QaRuleManagementService.kt
  > 74,76,83,101,103,105,106,107,120（create/update 两条写路径 + 三条 validate 调用）
  > ```
- `coverage_keys` 读路径：
  1. `QaRuleManagementService.updateRule` / `setRuleEnabled` 的 `parseStored`（门禁复验）。
  2. `QaRuleManagementController` response DTO（`:380` `parseStored`）。
  3. grounded 引擎 `AiReplyIntentCatalog.isCoverageEligible` / `selectIntentKeyForRule`
     （决定规则能否给某 intent 供证 —— 这是 coverage key 的**真实用途**）。
  4. `QaRuleAuditService`（统计口径，不读该列本身）。
- Interaction points：
  - **IP-1**：写路径 1/2（运营 UI）× 读路径 3（grounded 供证）。运营在 UI 上取消一个覆盖键，
    直接改变 AI 能否引用该规则作答 —— 这是「解除授权」确认卡必须展示后果的原因。
  - **IP-2**：写路径 3（迁移）× 写路径 1/2（运营 UI）互相覆盖 —— V107 必须带基线守卫
    （K-qa-rule-runtime-vs-migration-writes）。
  - **IP-3**：写路径 2（`coverageKeys == null` 保留 existing）× 门禁读路径 1。当前前端不发该字段，
    使门禁永远拿库存旧值判定 —— 24 号规则不可修复的直接机制（I-3）。

### `QaCoverageKeyCatalog`（`src/main/kotlin/.../qa/service/QaCoverageKeyCatalog.kt`）
- `Entry(key,label,description,group)`：31 条，`catalog` 按 **声明顺序**决定
  `normalizeAndValidate` 的返回序（`:all().map{it.key}.filter{it in trimmed}`）与 `parseStored` 的排序。
  **新增 Entry 必须继续追加在列表末尾**（该文件 :96-99 注释已写明：中间插入会重排既有规则的序列化串）。
- `controlledCoverageGroups`：4 组，硬编码 canonical 正文（:17-33）。
- `validateControlledBody`（:36-53）：当前触发条件为 `parsed.none { it in controlled } → return`，
  即**含任一受控键即进入严格校验** —— I-1 要改的就是这一行的语义。

### `QaRuleManagementController`（`src/main/kotlin/.../qa/controller/`）
- `GET /coverage-keys`（:131-140）返回 `CoverageKeyMetadataResponse(key,label,description,group)`。
  当年被 `qa-refactor-02` 标为 deprecated，前端不调用。
- `QaRuleCreateRequest`（:260）/ `QaRuleUpdateRequest`（:292）**已有** `coverageKeys: List<String>? = null`
  字段并透传（:274/:307）—— 后端接收侧无需改造，只有前端不发。
- `QaRuleResponse.coverageKeys`（:336/:380）已用 `parseStored` 输出。

### `QaRuleRepository`
- 仅 3 个方法：`findAllByOrderByPriorityAscIdAsc`、`findAllByCategoryIdOrderByPriorityAscIdAsc`、
  `findAllEnabledOrdered`（`@Query` 返回实体）。
- 结论：**最后授权源判定不需要新增 repository 方法**，用 `findAllEnabledOrdered()` +
  `parseStored` 在 service 层过滤即可。CLAUDE.md 明确警示 Spring Data JDBC 的 `@Query` DTO 投影
  在本仓库零先例，本计划不引入。

### 前端（`src/main/resources/static/`）
- **覆盖键 UI 当前是完整死代码链**，四条互相印证的 grep 回执：
  ```
  $ grep -n "renderQaCoverageKeyOptions\|renderQaCoverageKeyLabels" app.js
  1978:function renderQaCoverageKeyOptions(selectedKeys) {     ← 仅定义，零调用
  2111:function renderQaCoverageKeyLabels(coverageKeys) {      ← 仅定义，零调用
  $ grep -n "qaCoverageKeys" app.js
  11:    qaCoverageKeys: [],        ← 初始化后再无赋值
  1984, 2115                        ← 仅这两个死函数读它
  $ grep -n "coverage" index.html
  2042:      <p class="preview-coverage" id="previewCoverage" hidden></p>   ← 无关（AI 预览）
  $ grep -n "coverageKeys" app.js | grep -i "payload\|saveQaRule"
  （无输出 —— saveQaRule 的 payload 不含该字段）
  ```
  即：`#qaCoverageKeyOptions` / `#qaCoverageKeyWarning` 两个容器在 `index.html` 中不存在，
  `renderQaCoverageKeyOptions` 首行 `if (!container) return;` 静默短路；
  `/api/qa/coverage-keys` 前端从不请求。典型 K-dom-stub-tests-hide-dangling-refs 形态。
- `fillQaRuleForm`（`app.js:2846`）：填 8 个字段 + 占位符校验 + `mountPreviewRail`，无 coverage 相关调用。
- `saveQaRule`（`app.js:2877`）：payload 8 个字段，无 `coverageKeys`。
- 错误显示路径：`api()`（`app.js` 顶部）把非 2xx 的 `data.message` 包成 Error；
  `index.html` 的表单提交绑定在 `app.js:11065`：
  `saveQaRule(event).catch((error) => showStatus(error.message, "error"))`
  —— 后端英文异常原文直接进 toast，这是当前「黑盒报错」的最后一环。

### 前端样式盘点
- 可复用 class：
  - `.badge` / `.badge.ok` / `.badge.warn` / `.badge.error` — `styles.css:900/914/920/926` — 状态徽章
  - `.button` / `.button.primary` / `.button.secondary` — `styles.css:655/691/705` — 表单按钮
  - `.span-2` — `styles.css:1039`（`grid-column: span 2`）— 表单栅格跨列
  - `.form-actions` — `styles.css:1044` — 表单底部操作区
  - `.checkbox-row` — `styles.css:1012` — **纵向表单复选行，本计划的三列密排网格不复用**（见 S-2）
  - `.var-chip` — `styles.css:5750` — chip 视觉基线，**派生不就地改**（见 S-3）
  - `.qa-fact-body-head` — `styles.css:5739`（`display:flex; align-items:center; justify-content:space-between; gap:12px`）
- 设计基准 token 实值（`styles.css:1-60` `:root`）：
  `--primary: #1e40af`、`--primary-rgb: 30,64,175`、`--primary-light: rgba(var(--primary-rgb),.07)`、
  `--success: #059669` / `--success-bg` / `--success-border`、`--warning: #d97706` /
  `--warning-strong: #b45309` / `--warning-bg` / `--warning-border`、`--error: #e11d48` /
  `--error-strong: #be123c` / `--error-bg` / `--error-border`、`--info-bg` / `--info-border`、
  `--border: rgba(15,23,42,.11)`、`--border-strong: #cbd5e1`、`--line: rgba(15,23,42,.055)`、
  `--surface: rgba(15,23,42,.022)`、`--text-main: #1e293b` / `--text-strong: #334155` /
  `--text-secondary: #475569` / `--text-muted: #94a3b8`。
  > 注意 `--panel-bg` 是 `rgba(255,255,255,0.55)` **半透明**（K-panel-bg-token-is-translucent），
  > 本契约里需要不透明白底的地方（对照卡、确认卡、门禁按钮）一律显式写 `#fff`，不要用 `--panel-bg`。
- DOM 结构约定：`#qaRuleForm` 是 `.form-grid`（2 列栅格，`styles.css:951`）；
  跨列元素加 `.span-2`；`label` 基线为 `flex-direction: column` + `text-transform: uppercase`
  （`styles.css:963`）—— 任何横向 label 必须显式覆盖这三条（见 S-2 注意项）。
- 改动前基线（`index.html` `#qaRuleForm`，回复策略 label 与正文 label 之间当前**无任何元素**，
  本计划在此空隙插入 S-1/S-4 两块）：

```html
      <label>回复策略
          <select name="replyPolicy" required>
              <option value="AUTO">AUTO（可自动回复）</option>
              <option value="REVIEW">REVIEW（仅人工草稿）</option>
              <option value="NEVER">NEVER（禁止外发）</option>
          </select>
      </label>
      <label class="span-2 qa-fact-body-field">
```

### 缓存键三元组（K-frontend-cache-key-triad）
- 当前值 `20260820-v8-trust-fact-actions`，三处：`index.html:11`（styles.css）、
  `:2074`（trust-reply-workbench.js）、`:2075`（app.js）。
- 断言点 2 处：
  - `src/test/js/trustReplyWorkbenchSharedMount.test.js:341-343` —— 只断言**三者相等**，不含硬编码值 → 无需改。
  - `src/test/js/batchSendTaskConsoleVisualFix.test.js:48-52` —— **硬编码具体字符串三行** → 必须同步改。

### 既有契约测试的反向约束（重要）
`src/test/js/qaCoverageKeyEditor.test.js` 的第二个 describe 块（"fact-card era: coverage UI removed"，
:83-123）**主动断言 coverage UI 保持缺席**，共 4 条会被本计划打破：
```
:85   assert.doesNotMatch(indexHtml, /id="qaCoverageKeyOptions"/)
:86   assert.doesNotMatch(indexHtml, /id="qaCoverageKeyWarning"/)
:101  assert.doesNotMatch(loadFn, /\/api\/qa\/coverage-keys\/)      # loadQa 不请求该端点
:106  assert.doesNotMatch(saveFn, /coverageKeys/)                    # saveQaRule 不发该字段
:112  assert.doesNotMatch(fillFn, /renderQaCoverageKeyOptions/)      # fillQaRuleForm 不渲染
```
另有 2 条不受影响须保留：`:90-97`（规则表格仍用「事实标题」列、不出现「AI 覆盖能力」列）、
`:115-117`（`colspan="9"`）。`:120-122`（app.js 不硬编码 coverage key 常量）**必须保留且本计划不得违反**
—— 前端一律从接口取目录，禁止在 `app.js` 里写死任何 `"fees.policy"` 之类字面量。
这是 K-ui-removal-retires-obsolete-contract-tests 的反向形态：功能恢复必须同步改写「断言其缺席」的测试。

---

## 实现方案

### 阶段 1：后端元数据与门禁形状（子系统 A）

#### T1. `QaCoverageKeyCatalog` 暴露受控元数据并修正触发条件（I-1、I-4）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt`

1. 给 `ControlledCoverageGroup` 加稳定 `id: String`（取值 `"G1".."G4"`，顺序与现有 list 一致：
   G1=材料保密、G2=费用政策、G3=合同安排、G4=签约前 IP 边界）与 `name: String`（中文名，同上）。
2. 新增只读查询函数（供 controller 组装 DTO，不改变任何既有函数签名）：
   - `fun controlledGroups(): List<ControlledCoverageGroup>`
   - `fun groupIdOf(key: String): String?` —— 键不属于任何受控组时返回 `null`
   - `fun isControlled(key: String): Boolean`
3. **修改 `validateControlledBody` 的触发条件（I-1）**：
   把「`parsed.none { it in controlled }` 才提前返回」改为
   「先求 `controlledCoverageGroups.firstOrNull { it.keys == parsed }`；为 `null` 则**直接返回**（放行），
   非 `null` 才校验正文逐字相等」。即删除现有的 `?: throw IllegalArgumentException(...)` 分支。
   `Answer body must match the V82 canonical body for coverage …` 这条异常**保留不动**。
4. 不新增、不重排 `catalog` 中的 `Entry`（该文件 :96-99 的顺序契约注释保持有效）。

> 语义变化的完整表述：改造后，只有「覆盖集恰为某受控组」的规则受正文约束；
> 「顺带勾了受控键的总览型规则」与「受控组勾不全的规则」都放行。
> 后者（如只勾 `contract.party` 不勾 `contract.terms`）由前端提示「合同安排需成对勾选」引导，
> 但**不再是后端硬拦截** —— 这是 I-1 的有意取舍，因为不成对时它本就不构成权威出处。

#### T2. `/coverage-keys` 下发受控元数据 + 新增授权源只读端点（I-4、I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`、
`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`

1. `CoverageKeyMetadataResponse` 增加三个字段：
   `controlled: Boolean`、`groupId: String?`、`groupName: String?`。
   `GET /coverage-keys`（:131）按 `QaCoverageKeyCatalog.isControlled/groupIdOf` 填充。
   移除该端点的 deprecated 语义（`qa-refactor-02:151` 的过渡标注已到期）。
2. 新增 `GET /api/qa/coverage-keys/controlled-groups`，返回
   `List<ControlledGroupResponse(id, name, keys: List<String>, canonicalBody: String)>`，
   数据源为 `QaCoverageKeyCatalog.controlledGroups()`。**只读**，无对应写端点（I-4）。
3. 新增 `GET /api/qa/coverage-keys/authorities`，返回 `Map<String, List<AuthorityRuleResponse(id, displayName)>>`，
   key 为受控键，value 为**所有 `enabled=1` 且 `parseStored(coverage_keys)` 含该键**的规则。
   实现落在 `QaRuleManagementService.listCoverageAuthorities()`：
   `ruleRepository.findAllEnabledOrdered()` + `QaCoverageKeyCatalog.parseStored` 过滤，
   **不新增 repository 方法、不使用 DTO 投影**。前端自行排除当前编辑中的规则 id（I-5）。

#### T3. V107 迁移：摘掉 24 号的两个受控键（I-6）
文件：`src/main/resources/db/migration/V107__strip_controlled_keys_from_program_overview.sql`

```sql
-- V107: Program overview (id=24) is an overview fact, not the authority for the
-- fee / material-confidentiality commitments. V76 backfilled both controlled keys
-- onto it; V82 later made controlled keys exclusive, which left id=24 unsavable
-- and un-enableable. Strip the two controlled keys, keep everything else.
-- answer_body / reply_body are deliberately untouched (I-6).
UPDATE qa_rule
   SET coverage_keys = 'programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation',
       updated_at = updated_at
 WHERE id = 24
   AND coverage_keys = 'programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation,fees.policy,confidentiality.materials';
```

- `WHERE` 里的基线串即 `V76:24` 的原值，逐字复制；线上若已被运营改过则本迁移**不写**（IP-2）。
- 无 `${...}` 占位符，不触发 K-flyway-placeholder-replacement。
- **上线前置动作**（写入发布清单，非代码）：先执行
  `SELECT id, reply_subject, enabled, coverage_keys FROM qa_rule WHERE id = 24;`
  确认现值与基线串一致；不一致则人工合并后再调整迁移的 `WHERE` 基线。

### 阶段 2：前端接线与门禁可见化（子系统 B）

#### T4. `index.html` 插入面板骨架并 bump 缓存键（S-1、S-4、S-6、I-8）
文件：`src/main/resources/static/index.html`

1. 按 S-1 在「回复策略」`<label>` 与「标准事实正文」`<label>` 之间插入 `.qa-cov-panel` 骨架。
2. 按 S-4 紧随其后插入 `<div class="qa-gate span-2" id="qaCoverageGate"></div>`。
3. 按 S-6 在 `.form-actions` 之前插入 `#qaCoverageSaveBlock`。
4. 三处缓存键（:11 / :2074 / :2075）统一改为 `20260821-v12-qa-coverage-gate`。

#### T5. `styles.css` 追加 S-1..S-6 的全部 CSS（S-1..S-6）
文件：`src/main/resources/static/styles.css`
- 把 S-1~S-6 的 CSS 代码块**原样**追加到文件末尾，一个属性都不改。
- 不修改任何既有规则块（`.badge` / `.button` / `.var-chip` / `.checkbox-row` / `label` 均不动）。

#### T6. `app.js`：目录加载、面板渲染、收集契约（I-2、I-3、I-7）
文件：`src/main/resources/static/app.js`

1. `loadQa()`（:2008）改为并行请求三项：`/api/qa/categories`、`/api/qa/rules`、`/api/qa/coverage-keys`，
   把第三项写入 `state.qaCoverageKeys`。**另**在同处（或 `fillQaRuleForm` 首次打开时）加载
   `/api/qa/coverage-keys/controlled-groups` 与 `/api/qa/coverage-keys/authorities`，
   分别存入 `state.qaControlledGroups`、`state.qaCoverageAuthorities`。
   目录与受控组数据一律来自接口，**禁止在 app.js 里硬编码任何 coverage key 字面量**
   （既有测试 `qaCoverageKeyEditor.test.js:120-122` 会红）。
2. 改造既有死函数 `renderQaCoverageKeyOptions(selectedKeys)`（:1978）以匹配 S-2 的
   `.qa-cov-item` / `.qa-cov-input[data-coverage-key]` / `.qa-cov-lock` 结构（I-7：全部键常驻 DOM）。
3. 新增 `renderQaCoverageKeyChips(selectedKeys)`（S-3），写入 `#qaCoverageKeyChips`。
4. 新增 `collectQaCoverageKeys()`：**遍历 `#qaCoverageKeyOptions` 内全部 `.qa-cov-input`**，
   返回 `checked` 者的 `data-coverage-key` 数组（I-7）。
5. `fillQaRuleForm`（:2846）末尾调用 `renderQaCoverageKeyOptions(rule?.coverageKeys || [])`
   + `renderQaCoverageKeyChips(...)` + `renderQaCoverageGate()`。
6. `saveQaRule`（:2877）的 payload **增加 `coverageKeys: collectQaCoverageKeys()`**（I-3，空选发 `[]`）。
7. 事件绑定：`.qa-cov-input` 的 `change`、`.qa-cov-chip-x` 的 `click`（`data-coverage-unpick`）
   均触发「更新选中态 → 重渲染 chips → 重渲染门禁条」。
   **正文 textarea 的 `input` 事件只重渲染门禁条，不得重渲染整个面板 innerHTML**
   （K-state-input-no-per-keystroke-innerhtml）。
8. **选择器陷阱**：覆盖键含点号（`fees.policy`）。凡按键值取元素，属性值**必须加引号**：
   `container.querySelector('.qa-cov-input[data-coverage-key="' + key + '"]')`。
   不加引号会被解析成类选择器并抛 `SyntaxError`；同理禁止用键值拼 `id` 或 `querySelector('#'+key)`
   （同类事故见 K-css-ident-cannot-start-with-digit —— DOM stub 测试永不抛，只有真实浏览器会炸，
   故 A-1/A-9 的人工验收必须在真实浏览器执行）。
9. **不要删除 `renderQaCoverageKeyLabels`（`app.js:2111`）**。它当前仍是死函数，但
   `qaCoverageKeyEditor.test.js:35-81` 的第一个 describe 块（4 条用例）仍在测它；
   顺手清理会让这 4 条直接红。本计划不引入它的调用点，也不删除它
   （K-ai-reply-modal-helper-scope 的反向形态：删 helper 必须同步清引用，此处是"别删"）。

#### T7. `app.js`：门禁评估、提示与解除授权（I-1、I-2、I-4、I-5、S-4、S-5）
文件：`src/main/resources/static/app.js`（与 T6 同文件，分任务描述便于验证）

1. 新增 `evaluateQaCoverageGate()`：按 I-1 的**同一口径**在前端复算 —— 选中集恰等于某受控组则
   进一步比对正文（`trim()` 后逐字），否则一律 `ok`。返回四态之一：
   - `none`：无受控键 → 绿条「本规则未声明任何受控事实，正文可自由编辑，保存不受门禁限制。」
   - `aligned`：恰为某组且正文一致 → 绿条 + 「查看标准承诺」「解除授权…」
   - `drift`：恰为某组但正文不一致 → 琥珀条 + 逐词 diff + 「恢复标准正文」「解除授权…」，保存禁用
   - `partial`：勾了受控键但不成组 → **提示性琥珀条**，说明「不构成权威出处，AI 不会用它回答该类问题；
     如需成为权威出处请补齐该组的全部键」，**保存不禁用**（与 I-1 的后端放行一致）
2. 新增 `renderQaCoverageGate()`：按 S-4/S-5 渲染，并同步
   `#saveBtn.disabled` 与 `#qaCoverageSaveBlock`（仅 `drift` 态禁用保存）。
3. 逐词 diff：新增 `diffWordsForGate(canonical, current)`，返回 `[delHtml, insHtml]`
   （LCS 逐词，`.del` / `.ins` 标记，见 S-5）。所有插入文本必须过 `escapeHtml`。
4. 解除授权：`data-gate-act="revoke"` 打开确认卡；确认卡的「最后授权源」文案取
   `state.qaCoverageAuthorities[key]` 过滤掉当前 `state.selectedRuleId` 后是否为空（I-5）。
   `revoke-do` 从选中集移除该组**全部键**（I-4），重渲染面板与门禁条。
   确认卡关闭时清理其局部状态，避免切换规则后残留（K-shared-action-dialog-cleanup）。
5. 「为什么有的能力带锁？」折叠说明文案（写死中文，非 coverage key 字面量，不触发既有断言）：
   > 勾选的能力决定 AI 在回答哪类问题时可以把这条事实当作依据引用。没有勾选，AI 就算读到这条正文也不会拿它作答。
   > 带「受控」的四类是对外法律承诺（费用、材料保密、合同安排、签约前 IP）。一条规则要成为某类承诺的
   > 权威出处，它的覆盖能力必须**恰好**是该类的全部键；此时正文必须与标准承诺逐字一致，
   > 否则 AI 会以「依据充分」的姿态发出一句被改写过的承诺。
6. **保留**后端错误的最终显示：`app.js:11065` 的 `.catch(... showStatus(error.message, "error"))` 不删
   —— 前端预判与后端判定万一不一致时，运营仍须看到后端原文（I-2）。

### 阶段 3：测试

#### T8. 反转前端契约测试（I-3、I-7、I-8）
文件：`src/test/js/qaCoverageKeyEditor.test.js`
- 删除 describe「fact-card era: coverage UI removed」中的 4 条缺席断言（:85、:86、:101、:106、:112），
  改写为存在性断言：
  - `index.html` **含** `id="qaCoverageKeyOptions"`、`id="qaCoverageKeyChips"`、`id="qaCoverageGate"`
    （K-dom-stub-tests-hide-dangling-refs：必须对真实 `index.html` 源文本做存在性断言，
    而不是只断言 stub 被写入）
  - `loadQa` **含** `/api/qa/coverage-keys`
  - `saveQaRule` **含** `coverageKeys`
  - `fillQaRuleForm` **含** `renderQaCoverageKeyOptions`
- **保留不动**：:90-97（表格列）、:115-117（`colspan="9"`）、:120-122（app.js 无硬编码 coverage 常量）。
- 新增用例：
  - `collectQaCoverageKeys` 在 stub 容器里遍历全部 `.qa-cov-input`（I-7 契约）
  - `evaluateQaCoverageGate` 四态各一条（`none` / `aligned` / `drift` / `partial`）
  - `partial` 态**不禁用**保存按钮（对齐 I-1 后端放行）

#### T9. 后端门禁形状回归（I-1、I-6）
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- **改写** `legacy non controlled coverage is unaffected by the body gate`（:931）：
  fixture 改用**规则 24 的真实 V76 覆盖串**（11 键，含 `fees.policy` 与 `confidentiality.materials`）
  与一段非 canonical 的总览正文，断言 create/update **均成功保存**且 `coverageKeys` 逐字保留。
  > 这是本轮最重要的一条：原用例用的是自造的 2 键 fixture（`programme.purpose`+`programme.structure`），
  > 刻意避开受控键，因此测试名挂着 Program overview 却测不到真实缺陷（K-plan-quantified-claims-need-grep-receipts
  > 的同类形态：验收标准点名的真实数据，fixture 必须取该行真实值）。
- 新增：只勾 `contract.party`（不成对）→ 保存成功（I-1 放行）。
- 新增：`disable → 改错正文 → enable` 对**恰为受控组**的规则仍拒绝（保持 K-qa-rule-enable-must-revalidate-facts）。
- **必须改写** `create rejects mixed controlled coverage without saving`（:810）：
  已实测其 fixture 为 `coverageKeys = listOf("fees.policy", "confidentiality.materials")` +
  `answerBody = canonicalFeeBody` —— 两组受控键混合，集合不等于任一受控组，按 I-1 改造后应变为
  **接受并保存**。改写为 `create accepts mixed controlled coverage as a non-authority rule`，
  断言保存成功且 `coverageKeys` 逐字保留。
  > grep 回执：
  > ```
  > $ sed -n '810,824p' src/test/kotlin/.../QaRuleManagementServiceTest.kt
  > fun `create rejects mixed controlled coverage without saving`() {
  >     … coverageKeys = listOf("fees.policy", "confidentiality.materials") …
  >     Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
  > ```
- **保留不动**：:775 / :793 / :827 / :869 / :901 五条「恰为受控组」的正文一致性用例
  （这些 fixture 的覆盖集本就等于某一组，I-1 改造不影响其判定）。
- 新增 V107 迁移断言（沿用该文件既有的「读迁移 SQL 源文本」风格，参考 :990-1002 对 V76 的断言）：
  V107 文本含 `updated_at = updated_at`、`WHERE id = 24`、基线 `coverage_keys = '...'`，
  且**不含** `answer_body` / `reply_body`。
  （既有风格为 `java.nio.file.Files.readString(Path.of("src/main/resources/db/migration/V76__...sql"))`
  + `assertTrue(section.contains(...))`，见 :992-1002。）

#### T10. 缓存键断言同步（I-8）
文件：`src/test/js/batchSendTaskConsoleVisualFix.test.js`
- `:49-51` 三条硬编码字符串改为 `20260821-v12-qa-coverage-gate`。

#### T11. `qaFactCardEditor.test.js` 陈旧缺席用例退役（A5，I-3）
文件：`src/test/js/qaFactCardEditor.test.js`
- 删除 `:99-104` 的 `loadQa does not request coverage-keys endpoint` 用例
  （断言 `!apiCalls.includes("/api/qa/coverage-keys")`，与 I-3/T6 的「loadQa 必须请求
  `/api/qa/coverage-keys`」直接冲突；该文件 `:102` 的 `loadQa fetches coverage-keys metadata plus
  gate endpoints` 正向用例已覆盖此契约，属陈旧契约测试，K-ui-removal-retires-obsolete-contract-tests）。
- 其余用例逐字不动。

---

## 变更文件清单

| # | 文件 | 类型 | 内容 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | 修改 | 受控组加 id/name；新增 3 个只读查询；`validateControlledBody` 触发条件改为「恰为受控组」(I-1) |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt` | 修改 | `CoverageKeyMetadataResponse` 加 3 字段；新增 controlled-groups / authorities 两个只读端点 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | 修改 | 新增 `listCoverageAuthorities()`（复用 `findAllEnabledOrdered` + `parseStored`） |
| 4 | `src/main/resources/db/migration/V107__strip_controlled_keys_from_program_overview.sql` | 新增 | 摘掉 id=24 的两个受控键，带基线守卫与 `updated_at=updated_at` (I-6) |
| 5 | `src/main/resources/static/index.html` | 修改 | 插入 S-1/S-4/S-6 骨架；三处缓存键 bump (I-8) |
| 6 | `src/main/resources/static/styles.css` | 修改 | 追加 S-1..S-6 全部 CSS，不改既有规则块 |
| 7 | `src/main/resources/static/app.js` | 修改 | T6/T7：目录加载、面板与 chip 渲染、收集契约、门禁评估、diff、解除授权 |
| 8 | `src/test/js/qaCoverageKeyEditor.test.js` | 修改 | 反转 4 条缺席断言；新增收集契约与四态用例 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | 修改 | 真实 24 号 fixture；不成对放行；V107 文本断言；:810 语义复核 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 缓存键硬编码断言同步 (I-8) |
| 11 | `src/test/js/qaFactCardEditor.test.js` | 修改 | T11（A5：删除陈旧缺席用例 `loadQa does not request coverage-keys endpoint`） |

文件数 11（计划原 10 + A5 追加 1；A3 同步的三键断言测试另计）。子系统 2（A=后端 QA 服务+迁移，B=前端静态资源）。新增共享存储字段 0。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败（来源：`CLAUDE.md` Commands 章节）。
> 前端 JS 用例有两条不等价入口，`verify.sh` **只跑一个文件**，不可当回归门禁
> （来源：K-js-test-invocation-surface）。

```bash
# ── 前端：本计划直接改动的三个 JS 用例（权威门禁，可原样复制）
node --test src/test/js/qaCoverageKeyEditor.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js

# ── 前端：全量 JS 用例
node --test src/test/js/*.test.js

# ── 前端：语法检查（pom 的 test phase 也会跑这两条）
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js

# ── 后端：本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleManagementServiceTest

# ── 全量测试（回归门禁；含上面的 node --test 与 node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# ── 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# ── Flyway 迁移集成测试（需本地 Docker；本计划新增 V107 必跑）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# ── 空白/换行卫生
git diff --check
```

通过判据：
- `node --test`：退出码 0，输出 `# fail 0`。
- `mvn test`：退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`；且输出中出现 `node --test` 记录
  （确认 JS 用例确实被 `exec-maven-plugin` 执行，未被 `skipNodeTests` 跳过）。
- `mvn clean package`：退出码 0，生成 WAR。
- `git diff --check`：无输出。

来源：`CLAUDE.md` 项目元信息（`test_command` / `build_command` / Commands 章节）+
K-js-test-invocation-surface（node 入口与 `verify.sh` 的不等价性）。

---

## 验收标准

### 不变量
- **I-1**：`QaRuleManagementServiceTest` 中，以规则 24 真实 11 键覆盖串 + 非 canonical 正文
  create/update 均成功且 `coverageKeys` 逐字保留；只勾 `contract.party` 亦保存成功；
  恰为四组之一时正文错配仍抛 `IllegalArgumentException` 且 `ruleRepository.save` **未被调用**
  （沿用该文件既有的 `Mockito.verify(never())` 写法）。
- **I-2**：grep `app.js` 无任何绕过后端的旁路参数（`assert.doesNotMatch(appJsSource, /skipCoverageGate|forceSave/)`）；
  `app.js:11065` 的 `.catch(... showStatus(error.message, "error"))` 仍在
  （`assert.match(indexBoundSource, /saveQaRule\(event\)\.catch/)`）。
- **I-3**：`extractFn("saveQaRule")` 的源文本匹配 `/coverageKeys:\s*collectQaCoverageKeys\(\)/`；
  sandbox 中零勾选时 `collectQaCoverageKeys()` 返回 `[]`（不是 `undefined`/`null`）。
- **I-4**：`QaRuleManagementController` 源文本中 controlled-groups / authorities 两端点均为 `@GetMapping`，
  全仓库 grep 无写受控组的端点（`grep -rn "controlledCoverageGroups" src/main/kotlin` 仅出现在
  `QaCoverageKeyCatalog.kt`）；JS 用例断言 `revoke-do` 对 G3 同时移除 `contract.party` 与 `contract.terms`。
- **I-5**：JS 用例覆盖两分支 —— `authorities` 过滤掉当前规则后非空 → 确认卡含 `impact-ok`；
  为空 → 含 `impact-bad` 且文案含「转人工」；两分支的保存按钮 `disabled` 状态**均不变**。
- **I-6**：V107 源文本断言（T9）：含 `updated_at = updated_at`、`WHERE id = 24`、9 键基线串；
  不含 `answer_body`、`reply_body`；`FlywayMigrationIntegrationTest` 通过。
- **I-7**：sandbox 渲染 31 个键后，`#qaCoverageKeyOptions` 内 `.qa-cov-input` 数量 === 31；
  仅勾 2 个时 `collectQaCoverageKeys()` 返回长度 2 的数组。
- **I-8**：`node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` 与
  `trustReplyWorkbenchSharedMount.test.js` 均通过（前者校具体值、后者校三键相等）。

### 样式契约
- **S-1..S-6**：对 `styles.css` 的新增段落逐块 diff，确认与本计划代码块**逐字一致**（属性顺序、值、空行均不变）；
  `git diff src/main/resources/static/styles.css` 中**无既有规则块被修改**（新增全部在文件末尾）；
  `grep -c 'style="' src/main/resources/static/index.html` 相对改动前**不增加**（无新 inline style）；
  `index.html` 新增元素的 class 全部出现在 S-1..S-6 声明列表内（无未声明 class）。
- **S-2 专项**：`.qa-cov-item` 规则块必须同时含 `flex-direction: row`、`text-transform: none`、
  `letter-spacing: 0` 三条（缺任一条会被 `label` 基线覆盖成纵向错位）。

### 集成场景（跨 interaction point）
- **IP-1**：单测覆盖「UI 取消某受控键 → `coverage_keys` 落库变化 → `parseStored` 读回不含该键」。
- **IP-3**：单测覆盖「前端发 `coverageKeys: []` → `updateRule` 走 `normalizeAndValidate` 分支
  （非 `parseStored(existing)`）→ 库内被清空」，与「发 `null` 时保留 existing」两条对照用例并存。

### 回归
- 执行「验证命令」节的**全量测试命令**与**构建命令**，均按该节通过判据通过。
- 执行「验证命令」节的 **Flyway 迁移集成测试**通过。

---

## 人工验收清单

### A-1：打开编辑框即可见授权绑定
- 前置条件：以管理员登录后台，进入 QA 事实页，任选一条已启用规则（如《Application process》）。
- 操作步骤：
  1. 点击该行「编辑」。
  2. 观察「回复策略」下方新出现的「AI 覆盖能力（事实授权）」面板。
- 预期结果：面板顶部显示「已勾选 N 项」；下方「当前授权」行以 chip 形式列出该规则的全部覆盖能力中文名
  （如「申请步骤」「申请时间线」）；再下方是按「通用 / 公司信息 / 项目概况 / 专家匹配 / 角色与产出 /
  合同与IP / 资金 / 申请流程 / 工作安排 / 费用与保密」分组的复选框列表，已授权项为勾选态。
- 覆盖：需求描述 outcome 1；I-7

### A-2：受控能力有可见标记
- 前置条件：同 A-1，编辑框已打开。
- 操作步骤：滚动「AI 覆盖能力」列表到「合同与IP」与「费用与保密」两组。
- 预期结果：`签约主体`、`合同条款`、`知识产权安排`、`费用政策`、`材料保密` 五项右侧各有一枚
  琥珀色「受控」小标签；其余项无标签。点击「为什么有的能力带锁？」展开一段蓝底中文说明，
  含「对外法律承诺」「恰好」「逐字一致」等表述。
- 覆盖：需求描述 outcome 1

### A-3：普通规则不受打扰
- 前置条件：编辑《Application process》（覆盖能力为「申请步骤」「申请时间线」，均非受控）。
- 操作步骤：任意修改正文一个词，观察门禁条与保存按钮。
- 预期结果：门禁条为**绿色**单行「本规则未声明任何受控事实，正文可自由编辑，保存不受门禁限制。」；
  「保存事实」按钮**可点击**；点击后提示「QA 事实已保存」，重新打开该规则正文为修改后的值。
- 覆盖：需求描述 must-NOT-change（普通规则行为不变）

### A-4：规则 24 恢复可保存（本轮核心）
- 前置条件：V107 已执行。进入 QA 事实页找到《Program overview》（规则 ID 24）。
- 操作步骤：
  1. 点击「编辑」。
  2. 观察「当前授权」chip 行与门禁条。
  3. 不做任何修改，直接点「保存事实」。
- 预期结果：chip 行显示 9 个 chip，**不含**「费用政策」「材料保密」，且**无任何琥珀色受控 chip**；
  门禁条为绿色「本规则未声明任何受控事实…」；保存成功，提示「QA 事实已保存」，
  **不再出现** `Controlled coverage keys must form exactly one V82 atomic fact group`。
- 覆盖：需求描述 outcome 3；I-1、I-6

### A-5：规则 24 正文未被改动（回归）
- 前置条件：同 A-4。
- 操作步骤：在编辑框「标准事实正文」中逐段比对；或执行
  `SELECT answer_body, reply_body FROM qa_rule WHERE id = 24;`
- 预期结果：正文仍以 `Two tracks:` 开头，仍含 `There are no fees at any stage, and all materials are kept strictly confidential.`
  这一句；`answer_body` 与 `reply_body` 相等且与 V107 执行前完全一致。
- 覆盖：需求描述 must-NOT-change（对外措辞不变）；I-6

### A-6：总览型规则新建不再被误伤
- 前置条件：QA 事实页，点「新增事实」。
- 操作步骤：
  1. 填写：事实标题 `临时总览测试`、分类任选、匹配短语 `overview test`、正文 `We cover funding and there are no fees.`。
  2. 在覆盖能力里勾选「项目目的」「项目结构」**以及**「费用政策」。
  3. 点「保存事实」。
- 预期结果：门禁条为**琥珀提示条**（非红色拦截），文案说明该规则不构成「费用政策」的权威出处、
  AI 不会用它回答该类问题；**「保存事实」按钮可点击**；保存成功。
  验收后删除该测试规则。
- 覆盖：需求描述 outcome 4；I-1

### A-7：受控规则正文被改动 → 中文拦截 + 三条出路
- 前置条件：编辑《Participant fee policy》（覆盖能力恰为「费用政策」一项）。
- 操作步骤：
  1. 把正文改成 `We never charge any fees during the application stage.`
  2. 观察门禁条与保存按钮。
  3. 点「查看差异」。
  4. 点「恢复标准正文」。
- 预期结果：
  - 第 2 步：门禁条变**琥珀色**，首行含「正文与『费用政策』的标准承诺不一致，无法保存。」；
    「标准事实正文」标题右侧出现红色徽章「受控 · 正文不一致」；右下角出现红字
    「保存已被门禁拦截 —— 按上方任一条出路处理后即可保存」；「保存事实」按钮**置灰不可点**。
  - 第 3 步：展开上下两栏对照，「标准承诺」栏中 `throughout`、`entire`、`process.` 带红色删除线，
    「当前正文」栏中 `during`、`application`、`stage.` 带绿色高亮。
  - 第 4 步：正文恢复为 `We never charge any fees throughout the entire process.`；
    门禁条转**绿色**「本规则是『费用政策』的权威出处，正文与标准承诺逐字一致，可以保存。」；
    保存按钮恢复可点；点击保存成功。
- 覆盖：需求描述 outcome 2；I-1、I-2；S-4、S-5

### A-8：解除授权 —— 最后授权源警告（红分支）
- 前置条件：编辑《Participant fee policy》（当前是「费用政策」的唯一权威出处）。
- 操作步骤：点门禁条上的「解除本规则对『费用政策』的授权…」。
- 预期结果：展开白底确认卡，标题「解除本规则对『费用政策』的授权？」；影响列表中出现**红色加粗**的
  「⚠ 这是最后一个授权源。」并说明解除后该类问题会判为 UNSUPPORTED 并**转人工处理**；
  另有一条说明「正文里已经写过的相关句子不会被自动删除」。底部两个按钮「确认解除授权」「再想想」。
  点「再想想」确认卡收起，勾选态与保存按钮状态均不变。
- 覆盖：I-5；IP-1

### A-9：解除授权 —— 仍有其它出处（绿分支）+ 成对移除
- 前置条件：编辑《Contract arrangements》（覆盖能力为「签约主体」+「合同条款」，即受控组 G3）。
  另需确认库中不存在第二条声明这两个键的启用规则；若存在则本条改用该规则验证绿分支。
- 操作步骤：
  1. 点「解除本规则对『合同安排』的授权…」。
  2. 点「确认解除授权」。
  3. 观察「当前授权」chip 行。
- 预期结果：第 2 步后，chip 行中「签约主体」与「合同条款」**同时消失**（不是只掉一个）；
  复选框列表中两项同时变为未勾选；门禁条转绿色「本规则未声明任何受控事实…」；保存按钮可点。
  确认卡中若显示绿色「✓ 仍有其它权威出处」，其后列出的规则名必须真实存在于 QA 事实列表中。
  **验收后请点「取消」放弃保存**，不要真的把该授权解除。
- 覆盖：I-4、I-5；IP-1

### A-10：取消勾选真的落库（IP-3 回归）
- 前置条件：新建一条测试规则 `临时授权测试`，勾选「项目目的」「项目结构」，保存。
- 操作步骤：
  1. 重新编辑该规则，**取消勾选全部**覆盖能力（chip 行应显示「未勾选任何能力 —— AI 不会引用这条事实作答」）。
  2. 点「保存事实」。
  3. 关闭编辑框，重新打开该规则。
  4. 执行 `SELECT coverage_keys FROM qa_rule WHERE display_name = '临时授权测试';`
- 预期结果：第 3 步 chip 行仍显示「未勾选任何能力…」，复选框全部未勾选；
  第 4 步查询结果为**空串**（`''`），不是原来的 `programme.purpose,programme.structure`。
  验收后删除该测试规则。
- 覆盖：I-3；IP-3（这是当前缺陷的直接回归项 —— 改造前此操作会被静默忽略）

### A-11：启用受控脏数据规则仍被拦（回归）
- 前置条件：库中存在一条覆盖能力**恰为**某受控组、但正文与标准承诺不一致的**已禁用**规则。
  若无，可先新建：覆盖能力只勾「知识产权安排」、正文写 `Anything you share becomes ours.`，
  保存时应被拦截 → 说明无法构造该前置；改用 SQL 直接插入一条 `enabled = 0` 的此类规则。
- 操作步骤：在 QA 事实列表中点该行的「启用」。
- 预期结果：启用失败，顶部状态条出现错误提示（后端原文，含 `canonical body`）；
  列表刷新后该规则仍为**禁用**状态。
- 覆盖：需求描述 must-NOT-change（`setRuleEnabled` 仍复验）；I-1、I-2

### A-12：UI 目测（对照样式契约实值）
- 前置条件：编辑《Participant fee policy》，把正文改一个词使门禁条进入琥珀态。
- 操作步骤：逐项目测下列元素。
- 预期结果：
  1. 覆盖能力面板：浅灰标题栏（`--surface`），主体三列等宽网格，超过约 300px 高度出现内部滚动条，
     整体不撑破弹窗。
  2. 受控项复选框行：**复选框在左、文字在右、同一行**（不得出现复选框在上文字在下的纵向错位）；
     文字为常规大小写中文，非全大写。
  3. 已勾选的受控项整行为琥珀底色，右侧「受控」标签为琥珀底 + 深琥珀字（`--warning-strong: #b45309`）。
  4. chip 行：受控 chip 为琥珀系（带 🔒），普通 chip 为蓝系（`--primary: #1e40af` 配 `--primary-light` 底），
     每个 chip 尾部有可点的 `×`。
  5. 门禁条：琥珀底（`--warning-bg`）+ 琥珀边框（`--warning-border`），左侧 ⚠ 图标与文字基线对齐；
     按钮为白底圆角，「解除…」按钮为红字红边。
  6. 对照卡与确认卡底色为**不透明纯白**（`#fff`），不透出后面的表单内容。
  7. 弹窗内**无横向滚动条**；浏览器窗口缩到 820px 以下时，能力网格变为两列。
- 覆盖：S-1、S-2、S-3、S-4、S-5

### A-13：缓存键生效（回归）
- 前置条件：部署新版本后，用**曾访问过旧版本**的浏览器打开后台。
- 操作步骤：硬性不清缓存，直接刷新页面，打开任意 QA 事实编辑框。
- 预期结果：新面板正常出现（说明 `styles.css` / `app.js` 均已按新缓存键重新拉取）；
  查看页面源码，三处 `?v=` 值均为 `20260821-v12-qa-coverage-gate`。
- 覆盖：I-8

---

## 执行前置条件（需求方/发布负责人确认）

1. 执行 `SELECT id, reply_subject, enabled, coverage_keys FROM qa_rule WHERE id = 24;`，
   确认 `coverage_keys` 与 T3 的 `WHERE` 基线串逐字一致。不一致 → 先人工合并，再调整迁移基线（IP-2）。
2. 执行 `SELECT id, display_name, enabled, coverage_keys FROM qa_rule WHERE enabled = 1 AND coverage_keys REGEXP 'fees\\.policy|confidentiality\\.materials|contract\\.party|contract\\.terms|ip\\.arrangements';`
   —— 盘点全部声明受控键的启用规则，用于 A-8/A-9 选取验收样本，并确认没有第二条「恰为同一受控组」的规则。


---

## Phase 0 知识取舍记录

按 K-phase0-load-by-severity-not-filename 的要求，对 `qa` / `frontend` / `audit` 三域中
`severity: P1` 或 `hit_count >= 3` 的全部条目做了一遍过目，取舍如下。

### 已采纳并落到不变量/契约/任务
| 条目 | 落点 |
|---|---|
| K-qa-coverage-keys-management-write-boundary | I-3（update 传 null 保留 existing 的语义） |
| K-coverage-key-orphan-makes-fact-unreachable | I-5（解除授权的后果口径与确认卡文案） |
| K-qa-rule-runtime-vs-migration-writes | I-6（V107 基线守卫）、执行前置条件 1 |
| K-qa-migration-preserve-auto-updated-timestamp | I-6（`updated_at = updated_at`） |
| K-qa-rule-enable-must-revalidate-facts | must-NOT-change、T9 新增用例、A-11 |
| K-content-variant-input-read-contract | I-7（覆盖键复选框读取契约，同构套用） |
| K-dom-stub-tests-hide-dangling-refs | 现状审计的死代码链认定、T8 的 `index.html` 源文本存在性断言 |
| K-ui-removal-retires-obsolete-contract-tests | T8（反向形态：功能恢复须改写「断言其缺席」的测试） |
| K-frontend-cache-key-triad | I-8、T4、T10 |
| K-js-test-invocation-surface | 验证命令节的两条 node 入口与 `verify.sh` 警示 |
| K-panel-bg-token-is-translucent | 样式盘点中「需不透明白底处显式写 `#fff`」 |
| K-state-input-no-per-keystroke-innerhtml | T6.7 |
| K-shared-action-dialog-cleanup | T7.4（确认卡关闭时清理局部状态） |
| K-css-ident-cannot-start-with-digit | T6.8（含点号键值的选择器必须加引号） |
| K-ai-reply-modal-helper-scope | T6.9（反向：不要顺手删仍被测试覆盖的死函数） |
| K-ai-adopt-direct-send-no-residual-gates | I-2（前端状态不得成为安全边界，跨模块同构套用） |
| K-overview-gap-supersede | Out of scope 的已知代价说明 |
| K-plan-quantified-claims-need-grep-receipts | 全文计数与全称判断均附 grep 回执 |
| K-phase0-load-by-severity-not-filename | 本节本身 |

### 有意识拒绝（附理由）
- **AI 回复链路整族**（K-ai-reply-prompt-vs-send-rule-ids、K-request-facts-not-flat-pool、
  K-free-form-fallback-nonempty、K-llm-timeout-fallback、K-composed-reply-order-contract、
  K-manual-frame-three-consumers、K-rich-reply-qa-audit-reuse、K-explicit-fact-selection-must-match-request、
  K-draft-supersede-separate-auto、K-ai-draft-review-state-per-draft、K-ai-draft-edit-not-review-confirmation
  等）：本计划不触及 `AiReplyDraftService` / composer / 回复台任何文件，也不改变 `qa_rule` 的读语义
  （只改「运营能否保存这条规则」与「界面显示什么」）。coverage_keys 的**取值**会因 V107 变化，
  其下游影响已在 Out of scope 的已知代价中记录。
- **审计族**（K-audit-selected-source、K-audit-free-text-topic、K-training-evaluation-bounded-action-log）：
  本轮不新增任何审计事件（见 Out of scope 的 C 阶段），`mail_record_qa_rule` 与
  `OperatorActionType` 均不改。
- **批量发送控制台族**（K-batch-console-*，7 条）、**回复台族**（K-workbench-*、K-trust-reply-*）、
  **专家详情/筛选族**（K-expert-*、K-filter-option-scope-parity、K-bulk-actions-must-cover-full-filter-set、
  K-contact-list-dual-path-field-parity、K-detail-es-backed-fields-need-authoritative-read）：
  变更文件清单中无对应文件，零交集。
- **K-view-registration-triad**：本计划不新增侧栏 Tab 或 view，只在既有 `#qaRuleModal` 内插入元素，
  四处注册套路不适用。
- **K-qa-fact-body-required-no-legacy-fallback / K-qa-fact-body-signature-punctuation**：
  本计划不改 `QaFactBodyPolicy`，也不改 `fillQaRuleForm` 里 `rule?.answerBody || rule?.replyBody`
  这行既有兜底（属 must-NOT-change 范围）。
- **K-intent-keyword-two-sided-normalization / K-company-identity-keyword-intent-parity /
  K-due-diligence-intent-fact-parity / K-training-keyword-bare-verb-collision**：
  本计划不新增 coverage key、不新增 intent、不改 keywords，intent↔key 配对约束无触发面。
- **K-release-gate-evidence-not-example**：无发布门禁脚本产出。
- **K-html-string-truncation-breaks-cells**：新增渲染无任何字符串截断（diff 输出全量渲染）。
