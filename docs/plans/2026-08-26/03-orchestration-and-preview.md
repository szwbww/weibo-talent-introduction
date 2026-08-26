# 03 · 整封信编排（邻近事实去重 + CTA 唯一）与整合预览渲染

基线：`main` @ `f293507`。执行顺序见 `00-execution-order.md`（本计划排第 3，**必须在 01 之后**）。

---

## 需求描述

### Observable outcome

1. 一键预判产出的多段正文里，**同一条事实不再被多段复述**：机器代填的「回答说明」中列出的
   「能确认的邻近事实」按条目去重，已被前面条目采纳的事实不再出现在后面条目的说明里。
2. 整封信的**行动号召（索取材料 / 提议会议）只出现一次**，且位于最后一条无据条目，
   不再出现「第 1 段要简历、第 2 段约会议」。
3. 无据条目的开头不再是「我们库里没有确认答案」这类内部库存状态措辞，
   改为「说明取决于什么 + 确定后会提供什么」。
4. 工作台「整合摘要」的预览默认显示**变量已替换**的正文，
   不再出现 `${expertFamilyName|Colleague}` / `${senderName}` 这类裸占位符；
   服务端原始正文降级为第三个页签，仍可查看。

### What must NOT change

1. **`renderedDraftText` / `rawDraftText` 的服务端产生逻辑不变** ——
   `TrustReplyWorkbenchService.kt:1471-1477` 一行不动。
2. **`assemble()` 的请求/响应契约不变**，`draftHash` 仍取 `raw`（`:1483`）。
3. **运营手动填写的「回答说明」不受影响** —— 本计划只改
   `suggestedInstructionFor` 产出的**机器建议值**；一旦运营编辑过
   （`request.instructionEditedByOperator === true`，`trust-reply-workbench.js:1375`），
   建议值不得覆盖。
4. **`ANSWER_FACTS_VERBATIM` 与运营矩阵路径不变**（同 01/02 计划）。
5. **既有两页签导航不变** —— `trust-reply-page-nav` / `trust-reply-page-tab`
   （`styles.css:7714-7795`）与 `setActivePage` 的白名单（`trust-reply-workbench.js:1772`）
   一行不动；本计划新增的是**预览区内部**的第二套页签，不复用页面导航。

### Out of scope（显式延后）

- **「编排总览」面板**（事实分配表 + CTA 落点选择 + 未识别诉求单列）：
  它需要 `assemble` 响应新增一整个 DTO 树，会把变更面从 6 个文件撑到 12+ 个，
  超出 create-p 的 10 文件上限 → 独立为后续 `04-orchestration-panel.md`。
- 段落角色（正面回答 / 条件化说明 / 有理由的拒绝）作为**可选值**暴露给运营：同上，延后。
- `AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（`:100-103`）的常量本身**不改** ——
  改它会改变"运营说明可以授权哪些动作"的语义。本计划用**不在说明里写下一步**的方式实现 CTA 唯一，
  属于同一机制的在带内使用。
- 数字/链接门禁改白名单（让已审核事实里的 URL 与流程时长可引用）：需要单独的安全评审，延后。

---

## 关键不变量

### Invariant I-1: 机器建议值不得覆盖运营编辑
- Rule: `suggestedInstructionFor` 的产出只在 `request.instructionEditedByOperator === false`
  时被写入 `request.instruction`。
- Applies to: `trust-reply-workbench.js` 的 `autoRun()`（`:1360-1434`，写入点在 `:1373-1377`）。
- Violation consequence: 运营手写的回答说明被机器重算覆盖，且没有任何提示。
- 来源: 现有代码已满足（`:1376` 写 `instructionEditedByOperator = false`），本条是**回归保护**。

### Invariant I-2: 邻近事实按整封信去重
- Rule: 对第 N 条无据条目，`suggestedInstructionFor` 可列出的邻近事实名单 =
  整封信全部条目的 `factRuleIds` 并集 **减去** 索引小于 N 的条目已列出过的事实。
  同一条事实在整封信的全部建议说明中最多出现一次。
- Applies to: `TrustReplyWorkbenchService.toCoverage`（`:2104-2173`）。
- Violation consequence: 保持现状即缺陷本身 —— 见 `## 现状审计` A-2 的证据：
  当前 `adjacentIds = flatMap { it.factRuleIds }` 是整封 union，
  而无据条目自身 `factRuleIds` 恒空，`filterKeys { it !in item.factRuleIds }` 不过滤任何东西，
  **每条无据条目拿到的名单逐字相同** → 四段复读同一批事实。

### Invariant I-3: CTA 全信唯一，且只在最后一条无据条目
- Rule: 一封信的全部机器建议说明中，「交出下一步」的措辞**恰好出现一次**，
  落在 `index` 最大的那条 UNSUPPORTED 条目上；其余无据条目的建议说明不含任何
  索取材料 / 提议会议 / 下一步的措辞。
- Applies to: `TrustReplyWorkbenchService.suggestedInstructionFor`（`:2186-2205`）。
- Violation consequence: `AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（`:100-103`）
  对每条无据条目恒定放开 `REQUEST_MATERIALS + PROPOSE_MEETING`，
  而 `generateOperatorDirectedAnswer` 的 system message
  （`AiReplyDraftService.kt:738-760`）明确「The answer basis may authorise asking the recipient
  for materials or proposing a meeting or call; when it does, express that action in the reply.」
  —— 说明里写了下一步，模型就会写 CTA。每条都写 → 每段一个 CTA。
- 依据: `AiReplyActionPolicy.kt:100-103`（常量）、`AiReplyDraftService.kt:735`（`allowedActions` 取该常量）

### Invariant I-4: 建议说明不得出现数字、链接、时间承诺
- Rule: `containsUnsafeNameContent`（`TrustReplyWorkbenchService.kt:2213-2222`）与
  `overlapsAnswerBodyFragment`（`:2208-2216`）两道过滤**保持不变**；
  重写后的固定措辞本身也不得含数字、`http`/`www`/`://`、点分域名形态或时间承诺词。
- Applies to: `suggestedInstructionFor` 的新固定措辞、以及 500 字预算的重新计算。
- Violation consequence: 未经校验的事实正文片段或时间承诺进入
  `ANSWER_FROM_OPERATOR_INPUT` 的**唯一答案依据**，绕过事实正文单一来源。
- 来源: K-answerbody-source-exclusive；`TrustReplyWorkbenchService.kt:2175-2185` 既有注释（I-0/V-1）

### Invariant I-5: 500 字上限是硬契约
- Rule: `suggestedInstructionFor` 的返回值长度必须 ≤ 500；
  邻近事实名单仍按「先算固定措辞长度、再贪心装入」的方式截断，且截断不得截断单个名称。
- Applies to: 同上。
- Violation consequence: `requireHandlingPrerequisites`（`:2300-2307`）对
  `INSTRUCTION_REQUIRED_HANDLINGS` 校验 `trimmed.length > 500` → 抛
  `TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID` 422，一键预判在写入建议值后立刻失败。

### Invariant I-6: 预览默认显示已替换变量的正文，原始正文仍可达
- Rule: 「整合摘要」预览区在 `previewState() === "CURRENT"` 时默认渲染
  `assembly.renderedDraftText`；`assembly.rawDraftText` 移入第三页签，**不得删除**。
  `renderedDraftText` 为空/未定义时回退到 `rawDraftText`（与 `app.js:9944` 的既有口径一致）。
- Applies to: `trust-reply-workbench.js` 的 `renderSummary()`（`:2283-2311`），
  预览块在 `:2306-2308`。
- Violation consequence:
  - 不做 → 预览里继续出现 `${expertFamilyName|Colleague}`（现状缺陷）；
  - 删掉 raw → `src/test/js/trustReplyWorkbench.test.js:92`
    （`assert.match(workbench, /data-role="raw-preview"/)`）与
    `src/test/js/aiReplyLoadingFeedback.test.js:813` 立即失败，且运营失去排查占位符问题的手段。

### Invariant I-7: 页签切换不得依赖 `querySelector`
- Rule: 预览页签的切换必须走 `state.previewTab` + `render()` 全量重绘，
  不得用 `host.querySelector(...)` 定位并直接改 DOM。
- Applies to: `trust-reply-workbench.js` 新增的页签处理。
- Violation consequence: 两个 JS 测试沙箱的 `FakeElement.querySelector()` **恒返回 `null`**
  （`autoRunOrchestration.test.js:12-60` 的手写 DOM stub，非 jsdom），
  依赖它的行为在测试里不可验证，且会静默失效。
  另：`state.instanceId` 是 UUID v4，62.5% 概率首字符是数字，`#${id}` 形态的选择器会抛
  `SyntaxError`（`trust-reply-workbench.js:1776-1780` 已有同类注释）。
- 来源: K-dom-stub-tests-hide-dangling-refs、K-css-ident-cannot-start-with-digit

---

## 样式契约

### S-1: 预览页签条（新增组件）

- **复用**：
  - 页签按钮的**交互语义**参照既有 `trust-reply-page-tab`（`styles.css:7725-7743`），
    但**不得复用该 class** —— 它是页面导航的一部分，其容器
    `.trust-reply-page-nav`（`styles.css:7715-7723`）写死 `grid-template-columns: repeat(2, minmax(0, 1fr))`，
    复用会把两处页签耦合。
  - 容器沿用既有 `.trust-reply-assembly`（`styles.css:7686-7701`）作为外框，**不修改该规则块**。
    注意它带 `max-height: 280px; overflow: auto`，页签条会随内容一起滚动 —— 这是可接受的既有行为。
- **新增**：以下 CSS 规则块**逐字**追加到 `styles.css` 的 `.trust-reply-assembly` 规则块之后
  （即当前 `:7702` 那一行之前插入，或紧接 `:7704` 之后追加，二者取其一并保持文件内其余行不动）：

```css
.trust-reply-preview-tabs {
    display: flex;
    gap: 4px;
    margin: 0 0 8px;
    padding: 3px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background: var(--surface);
}

.trust-reply-preview-tab {
    flex: 1 1 0;
    min-width: 0;
    min-height: 26px;
    padding: 3px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text-muted);
    font-family: var(--font-body);
    font-size: 11.5px;
    font-weight: 600;
    line-height: 1.4;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: pointer;
    transition: background .15s ease, border-color .15s ease, color .15s ease;
}

.trust-reply-preview-tab:hover:not([aria-selected="true"]) {
    background: var(--bg-sidebar-hover);
    color: var(--text-secondary);
}

.trust-reply-preview-tab[aria-selected="true"] {
    border-color: var(--panel-border);
    background: #ffffff;
    color: var(--primary);
}

.trust-reply-preview-tab:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 1px;
}

.trust-reply-preview-tab:disabled {
    color: var(--text-muted);
    cursor: default;
    opacity: .55;
}
```

- **DOM 结构**（逐字骨架，执行时只替换 `${...}` 表达式，不得增删元素或 class）：

```html
<div class="trust-reply-preview-tabs" role="tablist">
  <button type="button" class="trust-reply-preview-tab" role="tab" data-action="set-preview-tab" data-preview-tab="rendered" aria-selected="true">发送正文</button>
  <button type="button" class="trust-reply-preview-tab" role="tab" data-action="set-preview-tab" data-preview-tab="local" aria-selected="false">配置预览</button>
  <button type="button" class="trust-reply-preview-tab" role="tab" data-action="set-preview-tab" data-preview-tab="raw" aria-selected="false">服务端原始正文</button>
</div>
```

- **禁止项**：inline style；未在本契约中声明的新 class；对
  `.trust-reply-assembly`（`7686-7701`）、`.trust-reply-page-nav`（`7715-7723`）、
  `.trust-reply-page-tab`（`7725-7743`）任一既有规则块的修改。

### S-2: 预览正文块（就地修改既有结构）

- **复用**：`<pre class="pre" data-role="…">` 的既有形态与 `.trust-reply-assembly` 外框
  （`trust-reply-workbench.js:2307-2308` 的现状即基线，见下"改动前基线"）。
- **新增**：无新 CSS。三个页签共用同一个 `<pre class="pre">`，仅 `data-role` 与内容随页签变化。
- **DOM 结构**（逐字骨架）：

```html
<div class="trust-reply-assembly">
  <!-- S-1 的页签条 -->
  <pre class="pre" data-role="${previewRole}">${escapeText(previewBody)}</pre>
</div>
```

其中 `previewRole` 取值恰为 `rendered-preview` / `local-preview` / `raw-preview` 三者之一。

- **改动前基线**（`trust-reply-workbench.js:2306-2308`，逐字）：

```js
            const previewBlock = stateKey === "CURRENT" && assembly
                ? `<div class="trust-reply-assembly"><div class="muted">服务端原始正文</div><pre class="pre" data-role="raw-preview">${escapeText(assembly.rawDraftText || "")}</pre></div>`
                : `<div class="trust-reply-assembly"><div class="muted">配置预览 · 未整合</div><pre class="pre" data-role="local-preview">${escapeText(renderFrameLocalPreview())}</pre></div>`;
```

- **class 使用点核查**：`data-role="raw-preview"` 在全仓共 3 处
  （`trust-reply-workbench.js:2307`、`src/test/js/trustReplyWorkbench.test.js:92`、
  `src/test/js/aiReplyLoadingFeedback.test.js:813`）；
  `data-role="local-preview"` 共 4 处（前述 js:2308、`trustReplyWorkbench.test.js:91`、
  `trustReplyWorkbenchSharedMount.test.js:2842` 与 `:2848` 的正则抓取）。
  两个 `data-role` **都保留**，故上述 4 个测试文件的既有断言全部继续成立；
  `trustReplyWorkbenchSharedMount.test.js:2842/2848` 抓的是
  `<pre class="pre" data-role="local-preview">([\s\S]*?)</pre>`，本契约的骨架逐字保持该形态。
- **禁止项**：删除 `data-role="raw-preview"` 或 `data-role="local-preview"`；
  改动 `<pre class="pre" …>` 的 class 列表。

---

## 现状审计

### A. 后端 · `TrustReplyWorkbenchService.toCoverage` 与 `suggestedInstructionFor`

**A-1 · `suggestedInstructionFor` 现状**（`:2186-2205`，逐字）

```kotlin
    private fun suggestedInstructionFor(item: RequestFactItem, adjacentNames: List<String>): String? {
        if (item.status != RequestGroundingStatus.UNSUPPORTED) return null
        val prefix = "这一条我们库里没有确认口径。请按真人对接人的方式回答：先明说没有确认答案"
        val open = "，再给出能确认的邻近事实（"
        val close = "）"
        val suffix = "，最后交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。"
        val fixedLength = prefix.length + open.length + close.length + suffix.length
        val budget = 500 - fixedLength
        val selected = mutableListOf<String>()
        var used = 0
        for (name in adjacentNames) {
            if (containsUnsafeNameContent(name)) continue
            val extra = name.length + if (selected.isEmpty()) 0 else 1
            if (used + extra > budget) break
            selected.add(name)
            used += extra
        }
        val namesPart = if (selected.isEmpty()) "" else "$open${selected.joinToString("、")}$close"
        return "$prefix$namesPart$suffix"
    }
```

四条产出特征与四段产物逐句对应：
「先明说没有确认答案」→ "We do not have a confirmed answer on file…"；
「邻近事实」→ 复读；「最后交出下一步」→ 每段一个 CTA；
「不要出现数字、链接或时间承诺」→ 官网 URL、"约半年"结构性不可能出现。

**A-2 · 邻近事实的取值**（`:2116-2140`，逐字关键段）

```kotlin
        val adjacentRules = if (any { it.status == RequestGroundingStatus.UNSUPPORTED }) {
            val adjacentIds = flatMap { it.factRuleIds }.distinct()
            ...
        val adjacent = if (item.status == RequestGroundingStatus.UNSUPPORTED) {
                adjacentRules.filterKeys { it !in item.factRuleIds }
            } else {
                emptyMap()
            }
```

`adjacentIds` 是**整封 union**；UNSUPPORTED 条目自身 `factRuleIds` 恒空
（`buildRequestFact:521-523`，`evidenceSet` 为空 → `filter` 结果为空），
所以 `filterKeys { it !in item.factRuleIds }` **不过滤任何东西** →
每条 UNSUPPORTED 条目拿到逐字相同的名单。**这是 I-2 的直接依据。**

> 注：01 计划落地后，检索到事实的条目 `factRuleIds` 不再为空，`status` 也从 UNSUPPORTED 升为 PARTIAL
> （01 的 I-6），因而不再进入 `suggestedInstructionFor`（`:2187` 首行即 return null）。
> 剩下的真·无据条目仍会复读 —— **所以 01 落地后本条缺陷仍在，必须修**。

**A-3 · 两道安全过滤（不得改动）**

- `overlapsAnswerBodyFragment`（`:2208-2216`）：名称若含相邻规则 `answerBody` 的任意连续 12 字符片段则剔除。
- `containsUnsafeNameContent`（`:2222-2231`）：数字、`http`、`www`、`://`、
  `domainFormRegex = Regex("""\.[a-zA-Z]{2,}""")`（`:2263`）、
  `timeCommitmentRegex`（`:2267-2269`）、`timePromiseTokens`（`:2254-2258`，17 个词）。

**A-4 · 500 字上限的强制点**（`:2300-2307`，逐字）

```kotlin
            if (handling in INSTRUCTION_REQUIRED_HANDLINGS) {
                val trimmed = instruction?.trim().orEmpty()
                if (trimmed.isBlank() || trimmed.length > 500) {
                    throw TrustReplyWorkbenchException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID"
                    )
```

`INSTRUCTION_REQUIRED_HANDLINGS = { ANSWER_FROM_OPERATOR_INPUT, ANSWER_EVIDENCE_WITH_OPERATOR_INPUT }`（`:2318-2321`）。

**A-5 · CTA 的机制链**

`AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（`AiReplyActionPolicy.kt:100-103`）：

```kotlin
    val OPERATOR_DIRECTED_ALLOWED_ACTIONS: Set<AiReplyAction> = setOf(
        AiReplyAction.REQUEST_MATERIALS,
        AiReplyAction.PROPOSE_MEETING
    )
```

`AiReplyDraftService.generateOperatorDirectedAnswer:735` 取该常量作 `allowedActions`；
system message（`:738-760`）含逐字句：
「The answer basis may authorise asking the recipient for materials or proposing a meeting or call;
when it does, express that action in the reply. Do not introduce any outbound action that the
answer basis does not state.」
→ **模型只在 answer basis 写了下一步时才写 CTA。**
本计划因此从 answer basis（即建议说明）一侧控制唯一性，不动白名单常量。

### B. 前端 · 整合预览

**B-1 · 唯一渲染点**：`trust-reply-workbench.js:2306-2308`（见"改动前基线"），
在 `renderSummary()` 内，产物由 `:2310` 的返回模板消费一次。

**B-2 · `renderedDraftText` 在前端已可用，无需改取数路径**：
`assemble()` 在 `:1291-1297` 用 `state.assembly = { ...response, ... }` 展开整个响应；
服务端 DTO 同时声明 `rawDraftText`（`TrustReplyWorkbenchService.kt:484`）与
`renderedDraftText`（`:485`），两者都由 `:1483-1484` 赋值。
→ `state.assembly.renderedDraftText` 今天就存在，只是**从未被 `trust-reply-workbench.js` 读过**
（`grep renderedDraftText src/main/resources/static/trust-reply-workbench.js` 零命中）。

**B-3 · 既有口径可照抄**：`app.js:9944` 已经是
`const rendered = assembly.renderedDraftText || assembly.rawDraftText || "";`
→ I-6 的回退写法与它逐字一致。

**B-4 · `previewState()`**（`:1210-1214`，逐字）

```js
        function previewState() {
            if (state.assembly && assemblyIdentityMatches(state.assembly)) return "CURRENT";
            if (state.assemblyStale) return "STALE";
            return "LOCAL";
        }
```

**B-5 · 测试沙箱能力边界**：两个测试文件都用
`fs.readFileSync` + 正则断言 **以及** `vm.runInContext` 跑真实源码
（`trustReplyWorkbench.test.js:1-12` / `:193-210`；`autoRunOrchestration.test.js:1-10` / `:244-261`）。
DOM 是手写 stub（`autoRunOrchestration.test.js:12-60`），
`FakeElement.querySelector()` **恒返回 null**，`querySelectorAll` 只认字面量选择器 `"[data-role]"`。
→ **I-7 的直接依据**：新页签必须走 `state` + 全量 `render()`。

**B-6 · 会被本计划影响的既有断言**（全部必须继续通过或同步更新）

| file:line | 断言 | 本计划影响 |
|---|---|---|
| `trustReplyWorkbench.test.js:23` | `assert.match(workbench, /rawDraftText/)` | 仍通过（raw 页签保留） |
| `trustReplyWorkbench.test.js:56` | `assert.match(workbench, /rawDraftText \|\|/)` | 仍通过（保留 `assembly.rawDraftText \|\| ""`） |
| `trustReplyWorkbench.test.js:91` | `data-role="local-preview"` | 仍通过 |
| `trustReplyWorkbench.test.js:92` | `data-role="raw-preview"` | 仍通过 |
| `trustReplyWorkbench.test.js:88` | `/配置预览 · 尚未服务端整合/` | **未验证**该字符串当前在源码何处；执行前必须先 `grep -n "配置预览" src/main/resources/static/trust-reply-workbench.js` 核实，若与 `:2308` 的 `配置预览 · 未整合` 不同则说明另有渲染点，须一并纳入 |
| `trustReplyWorkbenchSharedMount.test.js:2842/2848` | 正则抓 `<pre class="pre" data-role="local-preview">…</pre>` | 骨架逐字保留 → 仍通过 |
| `aiReplyLoadingFeedback.test.js:812-813` | 源码含 `rawDraftText` 与 `data-role="raw-preview"` | 仍通过 |
| `autoRunOrchestration.test.js:170-171` | fixture 同时给 `rawDraftText` 与 `renderedDraftText`（均为 `"assembled draft"`） | 两者相同，断言不受影响 |

### Interaction points

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| IP-1 | `toCoverage` 写 `suggestedInstruction` | `trust-reply-workbench.js:1375` `request.instruction = request.suggestedInstruction \|\| ""` | I-1 保护运营编辑；I-2/I-3 改的是写侧 |
| IP-2 | `suggestedInstructionFor` 写说明 | `AiReplyDraftService.generateOperatorDirectedAnswer:713` `require(instruction.isNotBlank())` + `:735` allowedActions | I-3 从说明侧控制 CTA；I-5 保住 500 字 |
| IP-3 | `verifyAssembly` 写 `renderedDraftText`（`:1484`） | 本计划新增的前端读点 | I-6 带 `\|\| rawDraftText` 回退 |
| IP-4 | 新增 `state.previewTab` | `render()` | I-7 禁止 querySelector |

---

## 实现方案

### 阶段 1 · 后端：邻近事实去重 + CTA 唯一 + 句式重写（I-2 I-3 I-4 I-5）

**T1.1** `toCoverage`（`:2104`）改为两趟：第一趟先按 `index` 升序确定
「本条可列出的邻近事实」，用一个 `usedNames: MutableSet<String>` 跨条目累积；
第二趟构造 `TrustReplyRequestCoverage`。
`suggestedInstructionFor` 的签名改为
`(item, adjacentNames, isLastUnsupported: Boolean)`。

**T1.2** 计算 `lastUnsupportedIndex = maxOfOrNull { if (it.status == UNSUPPORTED) it.index else null }`，
传入 `isLastUnsupported = (item.index == lastUnsupportedIndex)`（I-3）。

**T1.3** `suggestedInstructionFor` 的措辞按下列**逐字定稿**改写（I-3 I-4）；
执行时不得改写字面量，只允许调整 `budget` 的计算：

```kotlin
        val prefix = "这一条我们暂时给不出确定答案。请按真人对接人的方式回答：" +
            "先说明它取决于什么、还没定下来的原因"
        val open = "，再给出现在就能确认的邻近事实（"
        val close = "）"
        val tailWithAction = "，最后说明确定之后会提供什么，并交出下一步但不承诺具体时间。" +
            "不要出现数字、链接或时间承诺。"
        val tailWithoutAction = "，最后说明确定之后会提供什么。不要在本条里索取材料、" +
            "提议会议或给出下一步。不要出现数字、链接或时间承诺。"
        val suffix = if (isLastUnsupported) tailWithAction else tailWithoutAction
```

自查（I-4）：上述四段字面量均不含阿拉伯数字、`http`/`www`/`://`、
点分域名形态（`\.[a-zA-Z]{2,}`），也不含 `timePromiseTokens`（`:2254-2258`）中的任一词
与 `timeCommitmentRegex`（`:2267-2269`）可匹配的形态。
**执行时必须写一条测试逐字断言这一点**，而不是靠人眼。

**T1.4** `budget = 500 - (prefix.length + open.length + close.length + suffix.length)`
按新措辞重算；`suffix` 两种取值长度不同，必须**按本条实际取值**算，不得用固定值（I-5）。

**T1.5** 名称去重：贪心装入循环内，选中一个名称后 `usedNames.add(name)`；
后续条目的 `adjacentNames` 先 `filterNot { it in usedNames }`（I-2）。

### 阶段 2 · 前端：三页签预览（I-6 I-7 + S-1 S-2）

**T2.1** `state` 初始化处新增 `previewTab: "rendered"`。

**T2.2** `renderSummary()`（`:2283`）的 `previewBlock` 改为按 `state.previewTab` 选择：

- `stateKey !== "CURRENT"` 时：**只渲染 `local` 一个页签**（其余两个 `disabled`），
  内容 `escapeText(renderFrameLocalPreview())`，`data-role="local-preview"`（保住既有断言）。
- `stateKey === "CURRENT"` 时：三页签可用，
  `rendered` → `escapeText(assembly.renderedDraftText || assembly.rawDraftText || "")`，`data-role="rendered-preview"`；
  `local` → 同上，`data-role="local-preview"`；
  `raw` → `escapeText(assembly.rawDraftText || "")`，`data-role="raw-preview"`。

**T2.3** 事件：在既有 `data-action` 分发处（`:2388` 附近的 `if (action === "auto-run") …` 同一分支表）
新增 `if (action === "set-preview-tab") { state.previewTab = target.dataset.previewTab; render(); return; }`。
**不得**使用 `host.querySelector`（I-7）。

**T2.4** `styles.css` 按 S-1 逐字追加新规则块。

### 阶段 3 · 测试

**T3.1** `src/test/js/trustReplyWorkbench.test.js` 新增断言：
源码含 `renderedDraftText || assembly.rawDraftText`；含三个 `data-preview-tab` 字面量；
含 `data-role="rendered-preview"`；`styles` 含 `.trust-reply-preview-tabs {` 与
`.trust-reply-preview-tab {`；且 `assert.doesNotMatch(workbench, /host\.querySelector\([^)]*preview/)`。

**T3.2** `src/test/js/autoRunOrchestration.test.js` 新增沙箱用例：
fixture 里让 `renderedDraftText` 与 `rawDraftText` **不同**
（现状 `:170-171` 两者都是 `"assembled draft"`，必须改成不同值才有区分力），
断言默认渲染的是 `renderedDraftText`；再驱动一次 `set-preview-tab=raw`，断言渲染 `rawDraftText`。

**T3.3** 新建 `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplySuggestedInstructionTest.kt`：
覆盖 I-2（两条 UNSUPPORTED，第二条的名单不含第一条已用的名称）、
I-3（只有最后一条含"下一步"措辞，其余含"不要在本条里索取材料"）、
I-4（对两种 suffix 逐字断言无数字/链接/时间承诺）、
I-5（构造超长名称列表，断言返回值长度 ≤ 500 且不出现被截断的半个名称）。

---

## 变更文件清单（6 个，≤10 ✅；子系统 2 个：工作台后端 + 静态前端 ✅）

| # | 文件 | 新增/修改 | 涉及不变量 / 契约 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改（`toCoverage` + `suggestedInstructionFor`） | I-2 I-3 I-4 I-5 |
| 2 | `src/main/resources/static/trust-reply-workbench.js` | 修改（`state.previewTab`、`renderSummary` 预览块、动作分发一处） | I-6 I-7 / S-1 S-2 |
| 3 | `src/main/resources/static/styles.css` | 修改（按 S-1 逐字追加 6 个规则块） | S-1 |
| 4 | `src/test/js/trustReplyWorkbench.test.js` | 修改（新增断言） | I-6 I-7 S-1 |
| 5 | `src/test/js/autoRunOrchestration.test.js` | 修改（fixture 区分 + 新增沙箱用例） | I-6 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplySuggestedInstructionTest.kt` | 新增 | I-2 I-3 I-4 I-5 |

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。来源：`CLAUDE.md:7`。
> JS 测试通过 `exec-maven-plugin` 的 `node-test` execution 绑定在 `test` 阶段（`pom.xml:203-217`），
> 也可脱离 Maven 单独跑。

```bash
# 全量测试（回归门禁，含 JS）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplySuggestedInstructionTest

# 受影响的既有 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchControllerTest

# 本计划修改的 JS 测试文件（单文件，快速迭代用）
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/autoRunOrchestration.test.js

# 受影响的其余 JS 测试文件
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/aiReplyLoadingFeedback.test.js

# JS 全量
node --test src/test/js/*.test.js

# 静态语法检查（pom.xml:219-249 的 node-check-app 同款）
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn` 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且输出末尾 `# fail 0`。
来源：`CLAUDE.md:7-27`、`pom.xml:185-250`、`verify.sh:1-23`。

---

## 验收标准

- **I-1**：`git diff` 中 `trust-reply-workbench.js` 的 `:1373-1377` 写入块未被修改；
  沙箱用例：先手动改 `request.instruction` 并置 `instructionEditedByOperator = true`，
  再触发 `autoRun()`，断言 `instruction` 未被覆盖。
- **I-2**：Kotlin 测试构造 3 条 request（1 条 GROUNDED 带 2 条事实、2 条 UNSUPPORTED），
  断言两条 UNSUPPORTED 的 `suggestedInstruction` 中出现的事实名称集合**交集为空**。
- **I-3**：同一 fixture，断言 `index` 最大的那条 UNSUPPORTED 的说明含
  `交出下一步`，其余条含 `不要在本条里索取材料`；且整封信中 `交出下一步` 出现次数 == 1。
- **I-4**：对两种 suffix 分别断言：不含 `Regex("[0-9]")` 匹配、
  不含 `http`/`www`/`://`、`domainFormRegex` 无匹配、`timeCommitmentRegex` 无匹配、
  `timePromiseTokens` 无一命中。
- **I-5**：构造 20 个各 40 字的合法邻近名称，断言返回值 `length <= 500`，
  且返回值中出现的每个名称都是完整名称（用 `split("、")` 后逐个在输入集合中查得）。
- **I-6**：`assert.match(workbench, /renderedDraftText \|\| assembly\.rawDraftText/)`；
  沙箱用例断言默认 `<pre …data-role="rendered-preview">` 内容 == fixture 的 `renderedDraftText`；
  切到 raw 后内容 == fixture 的 `rawDraftText`；
  并断言 `data-role="raw-preview"` 与 `data-role="local-preview"` 仍存在于源码中。
- **I-7**：`assert.doesNotMatch(workbench, /querySelector\([^)]*preview/)`。
- **S-1**：`assert.match(styles, /\.trust-reply-preview-tabs \{/)` 与
  `/\.trust-reply-preview-tab \{/`；并用 `git diff src/main/resources/static/styles.css`
  逐行核对新增块与本契约代码块**逐字一致**（属性顺序、值、空格均不得改）；
  断言 `.trust-reply-assembly {`、`.trust-reply-page-nav {`、`.trust-reply-page-tab {`
  三个既有规则块在 diff 中**未出现**（即未被修改）。
- **S-2**：`assert.match(workbench, /<pre class="pre" data-role="\$\{/)` 或等价形态断言；
  `assert.doesNotMatch(workbench, /style="/)`（无 inline style）。
- 回归：执行「验证命令」节的全量测试与 JS 全量命令通过。

---

## 人工验收清单

### A-1: 多段不再复读同一批事实（覆盖 outcome 1；I-2）
- 前置条件：准备一封含 3 条 `-` bullet 的来信，其中至少 2 条在库里没有对应事实
  （例如问「你们通常和哪类企业合作」与「顾问的报酬结构是怎样的」的组合）。
- 操作步骤：
  1. 打开该来信的可信回复工作台，点「一键预判」。
  2. 待整合完成后，逐条展开无据条目，阅读「回答说明」文本框内容。
- 预期结果：两条无据条目的说明里，括号内列出的事实名称**没有任何一个重复**。
  整合正文中同一条事实的内容只出现一次。

### A-2: 整封信只有一处行动号召（覆盖 outcome 2；I-3）
- 前置条件：同 A-1。
- 操作步骤：阅读「整合摘要」预览的完整正文。
- 预期结果：
  - 索取简历 / 提议通话 之类的措辞**总共只出现一次**，且位于正文的最后一个答复段。
  - 前面各段结尾**不含**任何索取材料或约会议的句子。

### A-3: 不再暴露内部库存状态（覆盖 outcome 3；I-3 的措辞改写）
- 前置条件：同 A-1。
- 操作步骤：阅读无据条目对应的正文段落。
- 预期结果：段落**不以** "We do not have a confirmed answer on file" /
  "we do not have a confirmed position in our records" 这类句子开头；
  改为说明该问题取决于什么、以及确定之后会提供什么。

### A-4: 预览默认显示已替换变量的正文（覆盖 outcome 4；I-6、S-1、S-2）
- 前置条件：该来信的联系人有姓氏、已绑定发件账号（保证变量可替换）。
- 操作步骤：
  1. 一键预判并等待整合完成。
  2. 查看「整合摘要」的预览区。
- 预期结果：
  - 预览区顶部出现三个页签：**`发送正文` / `配置预览` / `服务端原始正文`**，
    默认选中第一个（选中态为白底、蓝字、有细边框）。
  - 正文中出现真实姓氏与真实发件人署名，**不出现** `${expertFamilyName|Colleague}`
    或 `${senderName}` 这类字样。
  - 点第三个页签后，正文变为含 `${...}` 占位符的原始文本；再点回第一个页签恢复。

### A-5: 未整合时的行为不变（回归；覆盖 must-NOT-change 第 2 条）
- 前置条件：新打开一封来信的工作台，**不点**一键预判。
- 操作步骤：查看「整合摘要」预览区。
- 预期结果：只有 `配置预览` 页签可点，另外两个置灰不可点；
  内容与本计划落地前的「配置预览 · 未整合」正文一致。

### A-6: 运营手写的说明不被覆盖（回归；覆盖 must-NOT-change 第 3 条；I-1）
- 前置条件：同 A-1，且已点过一次一键预判。
- 操作步骤：
  1. 在某条无据条目的「回答说明」框里手动改写一段文字（例如加一句自定义口径）。
  2. 再次点击「一键预判」。
- 预期结果：该条的说明**保持运营改写后的内容**，「机器代填」徽标消失且不再出现。

### A-7: 既有两页签导航不受影响（回归；覆盖 must-NOT-change 第 5 条；S-1）
- 前置条件：任意一封来信的工作台。
- 操作步骤：在页面顶部的「1 摘要与事实 / 2 回复框架与整合」之间来回切换，并用键盘左右方向键切换。
- 预期结果：两个页签的宽度仍是等分两列、外观与本计划落地前一致；
  方向键切换正常；不出现控制台报错。
