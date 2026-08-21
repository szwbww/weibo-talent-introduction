# 计划 02：手动绑定的事实成为依据，条目按「有部分依据」处理

- 基线：`main` @ `b02c40b`
- 顺序：在 `01-item-answer-strip-frame-phrases.md` **之后**执行（两者无代码耦合，但 01 更小更快，先落地可减少本计划的人工验收噪声）
- 子系统数：2（① 证据解析 `QaFactSelectionService`；② 处理方式与生成 `TrustReplyWorkbenchService` + `AiReplyDraftService` + 工作台前端）
- 变更文件数：8

---

## ⚠ 本计划推翻两条先前冻结的需求方决策

| 冻结决策 | 原出处 | 原理由 | 本计划的处置与理由 |
|---|---|---|---|
| **D1：手动绑定不改变条目 status** | `P1-fact-binding-drop-not-fatal.md` / `P2a-bound-vs-evidence-split.md` 的「需求方决策 1」 | 改 status 会连带改 `allowedHandlings`，使「按回答说明生成」从条目上消失，与线 A（运营说明即授权）冲突 | **推翻。** 原理由成立，但解法选错了：不该靠冻结 status 来保住 handling，而应让 `allowedHandlings` 不只看 status。本计划把「按回答说明生成」显式加进「含运营绑定的 PARTIAL」允许集（I-5），冲突消失，status 得以反映真实证据情况 |
| **D2：绑定但非证据的事实进 `promptRuleIds`，不进 `sendQaRuleIds`** | `P2a-bound-vs-evidence-split.md` 的「需求方决策 2」（外发审计只记真证据） | 运营绑定 ≠ 系统认可，审计不该记未经系统认可的规则 | **推翻。** 一旦允许「按依据生成 / 混合生成」，这些事实就是本封信正文的实际来源；审计不记就等于事后无法复现正文凭什么写成这样。「真证据」的定义由「系统关键词匹配认可」扩展为「系统匹配认可 ∪ 运营显式担保」。**注意本条不需要单独改代码**：绑定事实一旦进入 `factRuleIds`，`workbenchResult`（`QaFactSelectionService.kt:314`）的 `sendIds = ordered.flatMap { it.factRuleIds }` 自动纳入 |

**必须写入知识库**（Phase 6，见文末）：`K-fact-matrix-two-semantics-in-one-field` 的相关论断需修订，标注 D1/D2 已于 2026-08-21 被推翻及理由。

---

## 需求描述

### Observable outcome

1. 对一条 `UNSUPPORTED` 摘要用「+ 添加事实」绑定事实后，该条目状态变为 `PARTIAL`（界面显示「有部分依据」），**不再**是「无依据」。
2. 该条目的「处理方式」下拉出现三个可选项：**回答有依据部分**（默认选中）、**按回答说明生成**、**依据+说明混合**（新增）。
3. 「依据+说明混合」会把绑定的事实与运营填写的回答说明**合写成一段**答案：事实决定事实性内容，回答说明决定要表达的意图与动作。
4. 绑定的事实进入外发审计（`mail_record_qa_rule` 的来源 `sendQaRuleIds`），事后可复现这封信凭什么写成这样。

### What must NOT change

1. **自动回复链路完全不受影响。** 证据：`resolveMatrixSelection` 只在 `selectForWorkbench(selectionsByRequest != null)` 时可达（`QaFactSelectionService.kt:137`），而自动回复与人工发送走的是 `select()`（`PendingMailOperationService.kt:170/474/490/491/557/759/761/946/949/952`、`AiReplyDraftService.kt:1923`），后者永远走 legacy/auto 分支。
2. **已经是 `GROUNDED` 的条目仍然是 `GROUNDED`**，其允许集仍为 `[ANSWER_WITH_EVIDENCE, OMIT]`，已锁定的 `ANSWER_WITH_EVIDENCE` 条目不得因本计划变成非法（否则 `requireAllowedHandlingForApi`（`TrustReplyWorkbenchService.kt:1374`）会让整合 422）。
3. **`requestKey` 不变。** 它由 `item.intents.map { it.intentKey }` 参与哈希（`TrustReplyWorkbenchService.kt:1854-1859`、`:2077-2090`），一旦 intent 条目集合变化，全站 requestKey 漂移、`validateMatrixKeys` 与全部历史锁定项作废。
4. **`requestEvidenceVersion` 不变。** 它取 `item.boundRuleIds`（`TrustReplyWorkbenchService.kt:1715`），本计划不改 `boundRuleIds` 的取值与顺序，故历史锁定项不会批量失效。
5. **G1/G2 动作策略不变**：`OPERATOR_DIRECTED_ALLOWED_ACTIONS`、`findViolations`、CV 双条件、敏感材料闸逐字保留。
6. **未绑定任何事实的 `UNSUPPORTED` 条目行为逐字不变**（仍是 `[ANSWER_FROM_OPERATOR_INPUT, ACKNOWLEDGE_PENDING, OMIT]`，推荐 `ANSWER_FROM_OPERATOR_INPUT`）。
7. 前端**零新增 DOM 元素、零新增 CSS class**（处理方式下拉是数据驱动的，见 `## 样式契约`）。

### Out of scope

- **不改 `AiReplyIntentCatalog`**。闸 B 的绕过在 `QaFactSelectionService.buildRequestFact` 内完成（理由见 I-3），不动 catalog 的匹配与打分。
- **不为「依据+说明混合」产出 claim**。该 handling 的 `claims` 恒空（I-8），事实追溯依赖 `sendQaRuleIds`。逐 claim 归因留待后续。
- **不把「依据+说明混合」纳入 `UnsupportedAnswerIndex` 归档**。`PendingMailOperationService.isArchiveEligibleOperatorDirectedVersion`（`:636-641`）只认 `ANSWER_FROM_OPERATOR_INPUT`，本计划保持原样；混合答案不进「无依据答案索引」。
- 不改 `bootstrapReadiness` 的判据本身（`TrustReplyWorkbenchService.kt:1861-1862`），但要接受其结果因 status 变化而变（见 IP-4）。
- 不改 `droppedBindingRuleIds` / `droppedFactRuleIds` 的 P1 提示链路（它会因本计划变得少触发，但仍是有效的安全网）。
- 不改前端「+ 添加事实」的交互与 chips 渲染。

### 待需求方拍板的开放项（实施前必须闭合）

- **O-1：绑定后是否保留 `ACKNOWLEDGE_PENDING` 与 `OMIT`。** 需求确认时只勾了「回答有依据部分 / 按回答说明生成 / 混合生成」三项。本计划**默认保留** `OMIT`（否则运营一旦绑错事实就再也省略不掉这一条，只能解绑）与 `ACKNOWLEDGE_PENDING`（它本就是 `PARTIAL` 的既有选项，移除属额外的收窄改动）。若需求方坚持移除，改动点只在 I-5 的允许集与对应测试，不影响其余设计。

---

## 关键不变量

### Invariant I-1：闸 A 的绕过只发生在矩阵路径
- Rule：`buildRequestFact` 新增形参 `operatorBound: Boolean`（默认 `false`）。**只有** `resolveMatrixSelection`（`QaFactSelectionService.kt:190` 的调用点）传 `true`；`:52`（auto）、`:229`（legacy 空选）、`:255`（legacy）、`:288`（auto）四处必须保持 `false`。`operatorBound == true` 时 `candidateRules` 跳过 `QaFactKeywordMatcher.matchesRule`，只保留 `rule.id != null && rule.id in promptSet`。
- Applies to：`QaFactSelectionService.buildRequestFact` 的全部 5 个调用点。
- Violation consequence：若 auto 路径也绕过关键词匹配，全库每条规则都会成为每个问题的候选证据，自动回复会拿全库当依据 —— 静默的、全站的事实污染。
- 来源：original（矩阵路径 `promptPool = explicitRules` 恰为运营绑定集，见 `QaFactSelectionService.kt:188-194`，这是绕过之所以安全的唯一依据）

### Invariant I-2：闸 B 的绕过绝不新增或删除 intent coverage 条目
- Rule：运营绑定只能**改写已存在**的某条 `RequestIntentCoverage` 的 `status` 与 `evidenceRuleIds`，绝不 `+=` 一条新 coverage、也绝不移除既有 coverage。具体：未被 `assignRulesToIntents` 分配到任何 intent 的运营绑定规则，**仅当**该条目的 `intentCoverages` 中已存在 `general.answer` 条目时，并入该条目的 `evidenceRuleIds` 并置 `status = "SUPPORTED"`；不存在时这些规则保持未分配（落入 `droppedBindingRuleIds`，走既有 P1 提示）。
- Applies to：`QaFactSelectionService.buildRequestFact`。
- Violation consequence：`requestKey = sha256(sourceVersion, index, requestText, intentKeys.join)`（`TrustReplyWorkbenchService.kt:2077-2090`），而 `intentKeys` 取自 `item.intents`。新增一条 coverage → requestKey 变 → `validateMatrixKeys` 判 `TRUST_REPLY_REQUEST_KEY_INVALID` / `TRUST_REPLY_FACT_SELECTION_INVALID`，**工作台整个打不开**，且所有历史锁定项作废。
- 来源：original（本计划研究期间实测发现；`AiReplyIntentCatalog.matchIntentsWithSpans` 在零命中时会合成一条 `general.answer`（`AiReplyIntentCatalog.kt:401-414`），`matchIntents` 复用它（`:436-437`），所以 `canonicalRequests`（`TrustReplyWorkbenchService.kt:1726`）与 `buildRequestFact` 今日恰好一致——这份一致必须逐字保住）

### Invariant I-3：绕过逻辑全部落在 `QaFactSelectionService.buildRequestFact` 内
- Rule：不得修改 `AiReplyIntentCatalog` 的任何函数（`matchIntentsWithSpans` / `assignRulesToIntents` / `selectIntentKeyForRule` / `isCoverageEligible` / `resolveIntentEvidence`）。
- Applies to：全部实现任务。
- Violation consequence：`AiReplyIntentCatalog` 同时服务自动回复、训练模拟、工作台三条链路；在其中开后门等于对三条链路同时放宽，违反 I-1 的隔离。
- 来源：original

### Invariant I-4：只有「靠绕过才成立」的证据才把 status 压到 PARTIAL
- Rule：`buildRequestFact` 内同时计算严格候选集 `strictCandidateRules`（**沿用今日逻辑**，含 `matchesRule`）与绕过候选集。令 `bypassedRuleIds` = 最终 `factRuleIds` 中不属于 `strictCandidateRules`、或经 I-2 的 `general.answer` 并入路径进来的规则 id。
  - `bypassedRuleIds` 为空 → status **逐字等于今日算法的输出**（可以是 GROUNDED）。
  - `bypassedRuleIds` 非空 → 自然算得的 status 若为 `GROUNDED`，**下调为 `PARTIAL`**；若为 `PARTIAL` 或 `UNSUPPORTED` 则按自然值（`UNSUPPORTED` 在有证据时不会出现）。
- Applies to：`QaFactSelectionService.buildRequestFact`。
- Violation consequence：
  - 若无条件把矩阵路径的 GROUNDED 一律压成 PARTIAL → 今日已 `GROUNDED` 且已锁定 `ANSWER_WITH_EVIDENCE` 的条目在下次 bootstrap 时允许集变化，`requireAllowedHandlingForApi`（`:1374`）抛 422，整合永久失败（违反 must-NOT-change 第 2 条）。
  - 若不下调 → 零命中关键词的条目绑一条事实就变 `GROUNDED`（`allSupported` 为真，因为合成的 `general.answer` 是唯一 intent），允许集变成 `[ANSWER_WITH_EVIDENCE, OMIT]`，「按回答说明生成」与「回答有依据部分」双双消失——正是 D1 当初担心的事故。
- 来源：original

### Invariant I-5：允许集由「条目」而非「status」决定
- Rule：`allowedHandlings` 与 `recommendedHandling` 的判据从 `(status)` 改为 `(item: RequestFactItem)`。规则表：

  | 条件 | 允许集 | 推荐 |
  |---|---|---|
  | `GROUNDED` | `[ANSWER_WITH_EVIDENCE, OMIT]` | `ANSWER_WITH_EVIDENCE` |
  | `PARTIAL` 且**无**运营绕过证据 | `[ANSWER_SUPPORTED_PART, ACKNOWLEDGE_PENDING, OMIT]`（今日值，逐字不变） | `ANSWER_SUPPORTED_PART` |
  | `PARTIAL` 且**有**运营绕过证据 | `[ANSWER_SUPPORTED_PART, ANSWER_EVIDENCE_WITH_OPERATOR_INPUT, ANSWER_FROM_OPERATOR_INPUT, ACKNOWLEDGE_PENDING, OMIT]` | `ANSWER_SUPPORTED_PART` |
  | `UNSUPPORTED` | `[ANSWER_FROM_OPERATOR_INPUT, ACKNOWLEDGE_PENDING, OMIT]`（今日值，逐字不变） | `ANSWER_FROM_OPERATOR_INPUT` |

  「有运营绕过证据」的判据必须是 `RequestFactItem` 上的一个**显式字段**，不得在消费侧重新推导。
- Applies to：`TrustReplyWorkbenchService.allowedHandlings`（`:2050`）、`recommendedHandling`（`:2067`）、`requireAllowedHandling`（`:2073`）、`requireAllowedHandlingForApi`（`:1463`，两个调用点 `:1121` / `:1374`）、`AiReplyDraftService.validateItemHandling`（`:818`）。
- Violation consequence：若沿用 `(status)` 并直接把新选项加进 `PARTIAL`，则**所有** `PARTIAL` 条目（包括系统自动匹配出的、运营从未介入的）都获得「自由写」入口，等于全局绕开证据约束。
- 来源：original

### Invariant I-6：status↔handling 的合法性规则有三份副本，必须同改
- Rule：以下三处必须表达同一张表（I-5），且任一处不得再出现 status 的字面量硬编码：
  1. `TrustReplyWorkbenchService.allowedHandlings`（`:2050-2065`）
  2. `AiReplyDraftService.validateItemHandling`（`:818-836`）
  3. `TrustReplyWorkbenchService.validateLockedItem` 的 `ANSWER_FROM_OPERATOR_INPUT` 分支（`:1396` 的 `item.status != RequestGroundingStatus.UNSUPPORTED` 硬编码）
- Applies to：上述三处。
- Violation consequence：漏第 3 处的表现是「能生成、能选，但一采用就 `TRUST_REPLY_LOCKED_ITEM_INVALID`，整合永远失败」——且报错码与 status 无关，排查会走很远。
- 来源：K-operator-directed-authorization-seam（该条已记录五道闸，本计划新增第 6 处：`validateLockedItem` 的 status 前置判定）

### Invariant I-7：新 handling 的授权与合规沿用 operator-directed 口径
- Rule：`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 的生成侧 `allowedActions` 取 `AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（G1 放开），`findViolations` 照常执行（G2 不放开）；`validateLockedItem` 的对应分支复用 `ANSWER_FROM_OPERATOR_INPUT` 分支的同一套校验（非空说明、说明哈希、`findViolations`），**不得**使用 `ANSWER_WITH_EVIDENCE` 分支的 `detectActions(...).isNotEmpty()` 无条件禁令。
- Applies to：`AiReplyDraftService` 的新生成函数、`TrustReplyWorkbenchService.validateLockedItem`。
- Violation consequence：用 grounded 分支的无条件 `detectActions` → 只要答案里含索要材料或约会议就判废，而该 handling 存在的意义正是让运营授权这两类动作，功能等于不可用。
- 来源：K-operator-directed-authorization-seam（G1 授权 / G2 合规必须分开；`TrustReplyWorkbenchService.kt:1414-1421` 的 grounded 分支 `detectActions` 是刻意保留的，别复用）

### Invariant I-8：新 handling 的 claims 恒空
- Rule：`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 产出的 `AiReplyItemAnswer.claims` 恒为空列表；`assemble` 时**不得**把它计入 `groundedSections`（`TrustReplyWorkbenchService.kt:1235-1244` 的条件只认 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART`，保持原样）；`validateLockedItem` 对该 handling 断言 `claims.isEmpty()`。
- Applies to：新生成函数、`validateLockedItem`、`assemble`。
- Violation consequence：若产出 claims，`canonicalizeClaims`（`:1432-1461`）会要求 claim 的 key 集合恰等于 SUPPORTED intent 集合、且 `answerText` 逐字等于 claims 的拼接 —— 而混合答案含运营说明的内容，永远无法满足，落到 `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`。
- 来源：original（口径与 `ANSWER_FROM_OPERATOR_INPUT` 一致，见 K-locked-answer-paragraphs-at-version-time 的「三分支 claims 恒空」）

### Invariant I-9：新 handling 必须有非空回答说明
- Rule：`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 与 `ANSWER_FROM_OPERATOR_INPUT` 同样要求 `operatorInstruction` 非空且 ≤ 500 字符，且 `operatorInstructionHash == sha256Hex(instruction)`。前端在生成前也须拦（今日 `trust-reply-workbench.js:1101` 只判 `ANSWER_FROM_OPERATOR_INPUT`）。
- Applies to：`TrustReplyWorkbenchService.adjustItem`（`:1122-1128` 的校验块）、`validateLockedItem`、`trust-reply-workbench.js:1101`。
- Violation consequence：说明为空时该 handling 退化为「按依据生成但没人告诉它要表达什么」，模型只能编，且 `require(instruction.isNotBlank())` 会在服务层抛 `IllegalArgumentException` 而非 422，变成 500。
- 来源：original

### Invariant I-10：绑定事实进外发审计由 `factRuleIds` 自动带出，不新增旁路
- Rule：不得在 `workbenchResult`（`QaFactSelectionService.kt:305-334`）中为 `sendIds` 额外并入 `boundRuleIds`。绑定事实进审计的**唯一**机制是它已经成为 `factRuleIds` 的成员。
- Applies to：`QaFactSelectionService.workbenchResult`。
- Violation consequence：若额外并入，未被 I-2 采纳的绑定（落在 `droppedBindingRuleIds` 的那些）也会进审计，审计记录会包含从未影响正文的规则，与 D2 推翻后的新定义（「系统匹配认可 ∪ 运营显式担保且已生效」）不符。
- 来源：original（推翻 D2 的实现约束）

---

## 样式契约

本计划触及一个前端文件（`src/main/resources/static/trust-reply-workbench.js`），但**零新增 DOM 元素、零新增 CSS class、零 inline style**。

### S-1：处理方式下拉（`<select data-role="handling">`）
- 复用：完全复用既有渲染，`trust-reply-workbench.js:2217` —— `request.availableHandlings.map(...)` 逐项生成 `<option>`，文案取 `HANDLING_LABELS[handling]`。容器 class `trust-reply-field`（`:2222` 的 `<label class="trust-reply-field">处理方式…`）。
  禁止执行 agent 为新选项另写渲染分支或另加 class。
- 新增：**无新增 CSS**。唯一新增的是 `HANDLING_LABELS` 常量表（`:19-25`）里的一个键值对，逐字如下：

```js
        ANSWER_EVIDENCE_WITH_OPERATOR_INPUT: "依据+说明混合",
```

  （缩进为 8 空格，与该常量表内既有各行逐字一致——表内成员缩进 8 空格，表本身缩进 4 空格。）

  插入位置：`ANSWER_SUPPORTED_PART: "回答有依据部分",`（`:21`）与 `ANSWER_FROM_OPERATOR_INPUT: "按回答说明生成",`（`:22`）之间，保持与后端枚举同序。
- DOM 结构：不变。新选项以 `<option value="ANSWER_EVIDENCE_WITH_OPERATOR_INPUT">依据+说明混合</option>` 的形式出现在既有 `<select>` 内，由 `availableHandlings` 驱动。
- 禁止项：inline style；新 class；对 `trust-reply-field` / `trust-reply-item-controls` 规则块的任何修改。

### S-2：回答说明输入框的标签与前置校验
- 复用：既有 `<label class="trust-reply-field">${instructionLabel}<textarea data-role="instruction" …>`（`:2222`）。标签文案由 `instructionLabel`（`:2221`）决定。
- 新增：**无新增 CSS**。仅把三处 handling 判定从等值比较改为集合成员判定，逐字如下（三处使用同一个模块级常量）：

```js
    const OPERATOR_INSTRUCTION_HANDLINGS = Object.freeze([
        "ANSWER_FROM_OPERATOR_INPUT",
        "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT"
    ]);
```

  插入位置：紧接 `HANDLING_LABELS` / `GENERATION_KIND_LABELS` 常量之后（`trust-reply-workbench.js:26` 之后），与它们同为 4 空格缩进的模块级常量。

  三处替换点：
  - `:1101` `if (request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT" && !request.instruction.trim())` → `if (OPERATOR_INSTRUCTION_HANDLINGS.includes(request.draftHandling) && !request.instruction.trim())`
  - `:2134` `const needsOperatorInstruction = request.draftHandling === "ANSWER_FROM_OPERATOR_INPUT";` → `const needsOperatorInstruction = OPERATOR_INSTRUCTION_HANDLINGS.includes(request.draftHandling);`
  - `:2216` 同 `:2134` 的替换
- DOM 结构：不变。
- 禁止项：把 `instructionLabel` 的两句文案（`"回答说明（AI 将仅据此生成）"` / `"AI 调整要求（仅调整表达，可留空）"`）改写或新增第三句——混合生成沿用前者。

### S-3：既有 class 的使用点盘点
- 本计划**不修改**任何既有 class 的规则块。`grep -n "trust-reply-field\|trust-reply-item-controls" src/main/resources/static/styles.css` 今日命中 `7419 / 7426 / 7436 / 7437 / 7447 / 7454 / 7458 / 7467 / 7468 / 7469` 十行，实施后须逐字不变（验收见 S-1 的 `git diff styles.css` 为空）。
- `trust-reply-field` 与 `trust-reply-item-controls` 的使用点全集：`trust-reply-workbench.js:2222`（单点，同一模板字符串内）。因不修改规则块，无需派生新 class。

---

## 现状审计

### 数据存储：无

本计划不新增/修改任何数据库表、Flyway 迁移、ES 字段或缓存键。证据：
- `grep -rn "grounding_status\|request_fact" src/main/resources/db/migration/` 零命中；
- `RequestGroundingStatus` 与 `TrustReplyItemHandling` 只以枚举名进入 `TrustReplyWorkbenchStateStore` 的 `payload_json` 与审计 JSON，无独立列。
- **需要升 `TrustReplyWorkbenchStateStore.SCHEMA_VERSION` 吗：否。** 本计划不改 `versionId()` 的输入（`answerText` / `handling` / `evidenceSetVersion` 均不被回溯改写），也不改 `requestEvidenceVersion` 的输入（I-2 保住 requestKey、must-NOT-change 第 4 条保住 `boundRuleIds`）。新增的枚举值只出现在**新产生**的 payload 中；旧 payload 反序列化不受影响。

### 内存结构：`RequestFactItem`（`AiReplyDraftService.kt:349-371`）

现有字段与语义（P2a 注释已写明）：
- `factRuleIds` —— 系统认可、可用作回答依据的证据（关键词命中 + 落在 SUPPORTED 意图证据集）
- `boundRuleIds` —— 运营主张"这条事实属于这个问题"，**进** `canonicalMatrix` 与 `requestEvidenceVersion` 身份哈希
- `droppedBindingRuleIds` —— 运营绑了但被过滤掉的，**影子字段**，只进 coverage 提示
- `intents` —— `List<RequestIntentCoverage>`，**参与 requestKey 哈希**（经 `intentKeys`）
- `status` —— `GROUNDED / PARTIAL / UNSUPPORTED`

**本计划新增 1 个字段**：`operatorBypassedRuleIds: List<Long> = emptyList()` —— 「靠绕过才成为证据」的规则 id（I-4 的计算产物，I-5 的判据）。
- 是否进哈希：**否**。不进 `canonicalMatrix`、不进 `requestEvidenceVersion`、不进 `versionId`。它是 `factRuleIds` 的一个来源标注，`factRuleIds` 本身已经通过 `sendQaRuleIds` 影响审计。
- 是否进对外文本：**否**。

### 写路径：`RequestFactItem.status` 与 `factRuleIds`（`QaFactSelectionService.kt`）

| # | 位置 | 何时写 | 本计划是否改 |
|---|---|---|---|
| 1 | `buildRequestFact`（`:392-467`）——`candidateRules`（`:409`）、`intentCoverages`（`:416`）、`status`（`:441-448`）、`evidenceSet`（`:450`）、`factRuleIds`（`:455`） | 每次解析 | **改**（I-1 / I-2 / I-4） |
| 2 | `resolveMatrixSelection`（`:162-214`）——`item.copy(boundRuleIds=…, droppedBindingRuleIds=…)`（`:202-206`） | 工作台矩阵路径 | **改**（传 `operatorBound = true`；`droppedBindingRuleIds` 的计算式不变，其结果自然变少） |
| 3 | `resolveLegacySelection`（`:216-275`）——`item.copy(boundRuleIds = item.factRuleIds)`（`:267`） | legacy 扁平选择 | 不改（`operatorBound = false`） |
| 4 | `resolveAutoSelection`（`:277-303`）——`item.copy(boundRuleIds = item.factRuleIds)`（`:300`） | 自动匹配 | 不改 |
| 5 | `select()`（`:22-70`） | 自动回复 / 人工发送 | 不改 |
| 6 | `workbenchResult`（`:305-334`）——`sendIds`（`:314`）、`promptIds`（`:315`）、`groundedRequestCount`（`:322-325`） | 每次解析收尾 | 不改代码（I-10），但**结果会变**：绑定事实进 `factRuleIds` 后自动进 `sendIds` |

### 读路径：`RequestFactItem.status`

| # | 位置 | 读什么 / 期望 | 本计划影响 |
|---|---|---|---|
| 1 | `TrustReplyWorkbenchService.allowedHandlings`（`:2050`） | status → 允许集 | **改签名**（I-5） |
| 2 | `TrustReplyWorkbenchService.recommendedHandling`（`:2067`） | status → 推荐 | **改签名**（I-5） |
| 3 | `TrustReplyWorkbenchService.requireAllowedHandling`（`:2073`）→ `requireAllowedHandlingForApi`（`:1463`）→ 调用点 `:1121`（adjustItem）、`:1374`（validateLockedItem） | 校验 handling 合法性 | **改签名**（I-5） |
| 4 | `TrustReplyWorkbenchService.validateLockedItem`（`:1396`） | `item.status != UNSUPPORTED` 即判 `TRUST_REPLY_LOCKED_ITEM_INVALID` | **改**（I-6 第 3 处） |
| 5 | `AiReplyDraftService.validateItemHandling`（`:818-836`） | status → 允许集（第二份副本） | **改**（I-6 第 2 处） |
| 6 | `TrustReplyWorkbenchService.toCoverage`（`:1925-1950`） | `status.name` 出前端；`allowedHandlings` / `recommendedHandling` 出前端 | 改调用形式（传 item） |
| 7 | `TrustReplyWorkbenchService.bootstrapReadiness`（`:1861-1862`） | 任一 `UNSUPPORTED` → `BLOCKED` | 不改代码，**结果会变**（IP-4） |
| 8 | `AiReplyGroundedContentPlanner.buildPlan`（`:46`、`:73`） | `UNSUPPORTED` 直接 `continue`；无 SUPPORTED intent 但有 `factRuleIds` → 造 `general.answer` claim | 不改代码，**现在能走到 `:73` 了** |
| 9 | `TrustReplyWorkbenchService.canonicalizeClaims`（`:1436-1442`） | SUPPORTED intent 优先，否则 `factRuleIds` 非空则期望单条 `general.answer` claim | 不改代码（是 `:73` 的镜像分支） |
| 10 | `AiReplyDraftService.generateItem`（`:493`） | `groundedRequestCount = if (status == UNSUPPORTED) 0 else 1` | 不改代码，结果随 status 变 |
| 11 | `QaFactSelectionService.workbenchResult`（`:320-325`） | `unsupportedRequests`、`groundedRequestCount` | 不改代码，结果随 status 变 |
| 12 | `TrustReplyWorkbenchService.suggestedInstructionFor`（`:1966`） | `if (item.status != UNSUPPORTED) return null` | 不改代码，**结果会变**（IP-5） |

### 读路径：`TrustReplyItemHandling` 枚举（新增值的影响面）

`grep -rn "TrustReplyItemHandling\." --include=*.kt src/main/kotlin/` 命中分布：`TrustReplyWorkbenchService.kt` 36 处、`AiReplyDraftService.kt` 16 处、`PendingMailOperationService.kt` 3 处、`AiTrainingEvaluationService.kt` 2 处、`TrustReplyWorkbenchController.kt` 1 处、`AiTrainingController.kt` 1 处。

需要逐个确认的非本子系统命中：
- `PendingMailOperationService.kt:582 / :633 / :637` —— 均为 `== ANSWER_FROM_OPERATOR_INPUT` 的等值比较，用于 `UnsupportedAnswerIndex` 归档计数与资格判定。**保持原样**（out of scope 已声明：混合答案不归档）。
- `AiTrainingEvaluationService.kt:89` —— `== ANSWER_FROM_OPERATOR_INPUT` 等值比较。**保持原样**。
- `AiTrainingEvaluationService.kt:146` —— `TrustReplyItemHandling.values().associate { … }`，遍历全部枚举值做计数。新增值**自动**多出一个计数桶，值为 0，无需改代码，但需确认前端展示不因多一个键出错。
- `TrustReplyWorkbenchController.kt:230` / `AiTrainingController.kt:331` —— `valueOf(trim().uppercase())`，新增值自动可解析。

### Interaction points

- **IP-1**（写 1 × 读 1/2/3/5）：`status` 变化 → 允许集变化。核心交互，由 I-4 / I-5 / I-6 共同约束。
- **IP-2**（写 1 × 读 4）：`status` 从 `UNSUPPORTED` 变 `PARTIAL` → `validateLockedItem:1396` 的硬编码会让**已存在的** `ANSWER_FROM_OPERATOR_INPUT` 锁定项立刻非法。这是升级路径上最尖锐的一处：运营在改动前锁定了运营答案、改动后又绑了一条事实，整合就会 422。由 I-6 消除。
- **IP-3**（写 1 × 读 8/9）：`factRuleIds` 非空且 status 非 `UNSUPPORTED` → `AiReplyGroundedContentPlanner.kt:73` 的 `general.answer` 分支**首次可达**，`canonicalizeClaims:1441` 的镜像分支同时可达。两者必须产出同一份 `sourceIds`（都取 `item.factRuleIds`，今日已一致，不改即可）。
- **IP-4**（写 1 × 读 7）：条目由 `UNSUPPORTED` 变 `PARTIAL` → `bootstrapReadiness` 从 `BLOCKED` 变 `READY`。这是**期望的**语义变化（绑了事实就不再是"完全无依据"），但需在人工验收里确认前端 readiness 展示随之变化且不误导。
- **IP-5**（写 1 × 读 12）：`suggestedInstructionFor` 只对 `UNSUPPORTED` 返回建议说明（`:1966`）。条目变 `PARTIAL` 后，「一键预判自动填说明」对该条目**不再返回建议**。运营选「按回答说明生成」或「依据+说明混合」时会拿到空说明框。需在人工验收确认这不阻断流程（手填即可），并在计划的 out-of-scope 里明确不扩大 `suggestedInstructionFor` 的适用范围。
- **IP-6**（写 6 × 外发审计）：`sendQaRuleIds` 纳入绑定事实 → `mail_record_qa_rule` 记录增多。消费点：`AiReplyReviewAuditService`、`buildEvidenceSnapshotForSelection`（`AiReplyDraftService.kt:2425`）。后者按 id 读规则正文算快照，绑定事实的正文本就存在，无新失败模式。

### 前端样式盘点

- **可复用 class**：
  - `trust-reply-field` —— 规则块 `styles.css:7426`，配套规则 `:7436-7437`、`:7447`、`:7454`、`:7458`、`:7467-7469`；使用点 `trust-reply-workbench.js:2222`。处理方式/版本/说明三个表单域的统一容器。
  - `trust-reply-item-controls` —— 规则块 `styles.css:7419`；使用点 `trust-reply-workbench.js:2222`。处理方式与版本两个下拉的横向容器。
  - `trust-reply-version-select` —— 版本下拉专用；本计划不涉及。
  - `compose-panel trust-reply-item` —— 条目卡片外层；本计划不涉及。
- **设计基准 token**：本计划零新增样式，无需引入 token。
- **DOM 结构约定**：处理方式下拉是**数据驱动**的 —— `<select data-role="handling">` 的选项由 `request.availableHandlings`（来自后端 `allowedHandlings`）与 `HANDLING_LABELS`（`:19-25`）笛卡尔生成（`:2217`）。新增一个 handling **不需要**改 DOM，只需后端把它放进 `allowedHandlings` 并在 `HANDLING_LABELS` 加一行。
- **改动前基线**：`HANDLING_LABELS` 当前逐字内容（`trust-reply-workbench.js:19-25`）：

```js
    const HANDLING_LABELS = Object.freeze({
        ANSWER_WITH_EVIDENCE: "依据完整回答",
        ANSWER_SUPPORTED_PART: "回答有依据部分",
        ANSWER_FROM_OPERATOR_INPUT: "按回答说明生成",
        ACKNOWLEDGE_PENDING: "确认待补充",
        OMIT: "省略此项"
    });
```

---

## 实现方案

### 阶段 1：让运营绑定成为证据（子系统 ①）

**T1.1** `RequestFactItem` 增字段（`AiReplyDraftService.kt:349-371`）。（I-4 / I-5）
新增 `val operatorBypassedRuleIds: List<Long> = emptyList()`，并在字段上方按现有注释风格写明：不进任何哈希、不进对外文本、仅供 I-5 判据与 UI。

**T1.2** `QaFactSelectionService.buildRequestFact` 增形参并实现两道绕过（`:392-467`）。（I-1 / I-2 / I-3 / I-4）

1. 签名末尾追加 `operatorBound: Boolean = false`。
2. `:409` 处保留今日的 `candidateRules` 计算，重命名为 `strictCandidateRules`；再算
   `effectiveCandidateRules = if (operatorBound) promptPool.filter { it.id != null && it.id in promptSet } else strictCandidateRules`。
   后续 `assignRulesToIntents` 与 `factRuleIds` 均改用 `effectiveCandidateRules`。
3. `:416` 的 `intentCoverages` 计算之后、`:441` 的 `status` 计算之前，插入 I-2 的并入逻辑：
   - 求 `assignedIds` = `assignments` 全部桶的 id 并集；
   - `unassignedBound` = `effectiveCandidateRules.mapNotNull { it.id } - assignedIds`（`operatorBound` 为假时恒空）；
   - 若 `unassignedBound` 非空**且** `intentCoverages` 中存在 `intentKey == "general.answer"` 的条目，则以 `copy(evidenceRuleIds = (原 evidenceRuleIds + unassignedBound).distinct(), status = "SUPPORTED")` **替换**该条目（**替换，不是追加**，I-2）；否则不动。
4. `:441-448` 的 `status` 表达式保持逐字不变，结果存入 `naturalStatus`。
5. 计算 `operatorBypassedRuleIds` = `factRuleIds.filter { it !in strictCandidateRules.mapNotNull { r -> r.id }.toSet() || it in unassignedBound }`（I-4）。
6. `status = if (operatorBypassedRuleIds.isNotEmpty() && naturalStatus == GROUNDED) PARTIAL else naturalStatus`（I-4）。
7. `RequestFactItem(...)` 构造处补 `operatorBypassedRuleIds = operatorBypassedRuleIds`。

**T1.3** `resolveMatrixSelection` 传 `operatorBound = true`（`QaFactSelectionService.kt:190` 的调用点）。（I-1）
其余 4 个调用点（`:52`、`:229`、`:255`、`:288`）**一个字都不改**，靠默认值 `false`。

**T1.4** `workbenchResult`（`:305-334`）**不改**。（I-10）在 `sendIds` 那一行补一条注释，写明「D2 已于 2026-08-21 推翻：绑定事实通过成为 `factRuleIds` 成员进入审计，此处不得额外并入 `boundRuleIds`」。

### 阶段 2：允许集与新 handling（子系统 ②）

**T2.1** `TrustReplyItemHandling` 增值（`TrustReplyWorkbenchService.kt:28-34`）。（I-5）
在 `ANSWER_SUPPORTED_PART` 与 `ANSWER_FROM_OPERATOR_INPUT` 之间插入 `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`（与前端 `HANDLING_LABELS` 同序，S-1）。

**T2.2** `allowedHandlings` / `recommendedHandling` / `requireAllowedHandling` 改判据（`TrustReplyWorkbenchService.kt:2050-2075`）。（I-5 / I-6 第 1 处）
签名从 `(status: RequestGroundingStatus)` 改为 `(item: RequestFactItem)`，按 I-5 的表实现。调用点 `:1941`、`:1942`、`:1121`、`:1374` 随之传 item。

**T2.3** `AiReplyDraftService.validateItemHandling` 改判据（`:818-836`）。（I-6 第 2 处）
签名从 `(status, handling)` 改为 `(item: RequestFactItem, handling)`，**直接调用** `TrustReplyWorkbenchService.Companion.allowedHandlings(item)`，消除第二份副本，不再自己写一张表。调用点 `:424`。

**T2.4** `validateLockedItem` 拆分与放宽（`TrustReplyWorkbenchService.kt:1393-1413`）。（I-6 第 3 处 / I-7 / I-8 / I-9）
- `:1396` 的 `item.status != RequestGroundingStatus.UNSUPPORTED` 判定**删除**（合法性已由 `:1374` 的 `requireAllowedHandlingForApi(item, locked.handling)` 完整覆盖）。在该行位置留注释说明这是 I-6 的第 3 份副本，已收口到 I-5 的唯一表。
- 新增 `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 分支：与 `ANSWER_FROM_OPERATOR_INPUT` 分支**共用同一段校验代码**（抽成私有函数 `validateOperatorInstructionBackedItem(locked)`），即：说明非空 / ≤500 / 哈希匹配 / `answerText` 非空 / `claims` 为空 / `generationKind == AI_GENERATED` / `findViolations(answerText, OPERATOR_DIRECTED_ALLOWED_ACTIONS)` 为空。
- `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` 分支（`:1414-1421`）的 `detectActions` 无条件禁令**逐字保留**（I-7）。

**T2.5** `adjustItem` 的说明校验扩到新 handling（`TrustReplyWorkbenchService.kt:1122-1128`）。（I-9）
`if (request.handling == ANSWER_FROM_OPERATOR_INPUT)` 改为集合成员判定，含新值。

**T2.6** `AiReplyDraftService.generateItem` 增分支与生成函数（`:450-464` 附近）。（I-7 / I-8）
- 在 `ANSWER_FROM_OPERATOR_INPUT` 分支之后增加 `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 分支，同样包在 `rejectNonEnglishItemAnswer(...)` 里。
- 新增 `generateBlendedAnswer(...)`，形参与 `generateOperatorDirectedAnswer` 一致，实现上以后者为模板，差异仅三处：
  1. 事实块的取值从 `boundRuleIds` 改为 `requestFact.factRuleIds`（已被 I-2 采纳的那些才是依据）；
  2. system message 改为「事实与运营说明并列为两个权威来源」的措辞（逐字见 T2.7）；
  3. 返回的 `handling` 为 `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`，`claims = emptyList()`（I-8）。
- `allowedActions` 取 `AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（I-7）。
- **若计划 01 已合并**：本函数同样调用 `AiReplyFramePhrasePolicy.strip`（计划 01 的 I-6 要求「同一策略对象」，新增第三个调用点时须同步更新计划 01 验收标准里的「恰好 2 处命中」为 3 处）。

**T2.7** 新 handling 的 system message（逐字）：

> `Rewrite one recipient-facing answer that combines two authoritative sources: the attached reference facts and the operator-provided answer basis. Return English only, regardless of the language used in the target question, the facts, or the answer basis. Factual content — institutions, programmes, funding, contracts, times, identities, numbers, URLs — may come ONLY from the attached reference facts. The operator-provided answer basis defines the intent, the framing and any outbound action; it must not introduce new factual claims. Write them as one continuous passage, not two separate paragraphs. Return plain email prose only, with no JSON, headings, lists, status labels, or internal markers. The answer basis may authorise asking the recipient for materials or proposing a meeting or call; when it does, express that action in the reply. Do not introduce any outbound action that the answer basis does not state. When the answer basis asks for a CV or other materials, the sentence that makes the request must, within that same sentence, state the purpose using the words "eligibility review" and make it optional using the words "at your convenience". Example of an acceptable request sentence: "If you would like to proceed, you are welcome to share your CV at your convenience so that we can carry out an initial eligibility review." Never ask for passports, ID cards, work certificates, bank statements, or any other identity or financial document.`

### 阶段 3：前端（子系统 ②）

**T3.1** `trust-reply-workbench.js` 按 S-1 / S-2 逐字改三处 + 两个常量。**不改任何 CSS 文件。**

### 阶段 4：测试

**T4.1** `QaFactSelectionServiceTest.kt` —— I-1 / I-2 / I-4 / I-10 的正反例。
**T4.2** `TrustReplyWorkbenchItemFlowTest.kt` —— I-5 / I-6 / I-7 / I-8 / I-9，含 IP-2 的升级路径用例。
**T4.3** `AiReplyDraftServiceTest.kt` —— 新生成函数的 G1 放开 / G2 保留 / claims 恒空。
**T4.4** `src/test/js/trustReplyWorkbench.test.js` —— S-1 / S-2 的渲染与前置校验。

---

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改 | 两道绕过、PARTIAL 上限、`operatorBound` 形参（T1.2 / T1.3 / T1.4） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | `RequestFactItem` 增字段、`validateItemHandling` 收口、新 handling 分支与生成函数（T1.1 / T2.3 / T2.6 / T2.7） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | 枚举新值、允许集改判据、`validateLockedItem` 拆分、`adjustItem` 校验（T2.1 / T2.2 / T2.4 / T2.5） |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | 标签常量 + 三处 handling 集合判定（T3.1，见 S-1 / S-2） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 修改 | T4.1 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | T4.2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | T4.3 |
| 8 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | T4.4 |

合计 8 个文件，2 个子系统，新增 1 个内存字段（无共享存储字段新增）。

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。JS 单测由 `exec-maven-plugin` 绑定在 `test` 阶段（`pom.xml:184-203`），`mvn test` 已覆盖；单独跑 JS 用 `node --test`。

```bash
# 全量测试（回归门禁；含 Kotlin 单测与 node --test 前端单测）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关的测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest

# 单独跑本计划涉及的前端单测（不经 Maven）
node --test src/test/js/trustReplyWorkbench.test.js

# 前端语法自检（与 pom.xml 的 node-check-app 同口径）
node --check src/main/resources/static/trust-reply-workbench.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0；Kotlin 侧输出含 `Tests run: N, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`；`node --test` 输出含 `fail 0`。
来源：项目根 `CLAUDE.md` 「Commands」章节（`CLAUDE.md:9-27`）与 `test_command:` / `build_command:` 项目元信息（`CLAUDE.md:140,142`）；JS 单测与 `node --check` 绑定见 `pom.xml:184-215` 实测。

---

## 验收标准

- **I-1**：`grep -n "buildRequestFact(" src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` 共 5 个调用点，其中**恰好 1 处**出现 `operatorBound = true`，且该处位于 `resolveMatrixSelection` 内。单测：auto 路径下绑定一条关键词完全不匹配的规则，断言 `factRuleIds` 为空、status 不变。
- **I-2**：单测 —— 对一条零意图命中的摘要绑定 2 条事实，断言 `item.intents.size == 1`、`item.intents[0].intentKey == "general.answer"`；并断言 `TrustReplyWorkbenchService.requestKey(sourceVersion, item)` 与改动前对同一 `(sourceVersion, index, requestText)` 计算出的值**逐字相等**（用硬编码期望值锁死）。另断言：对一条**有具名意图命中**的摘要绑定一条对不上任何意图的事实时，`intents.size` 不变、该事实落入 `droppedBindingRuleIds`。
- **I-3**：`git diff --name-only` 中**不包含** `AiReplyIntentCatalog.kt`。
- **I-4**：单测三例 ——
  1. 绑定的事实严格命中关键词且落进 SUPPORTED 意图 → `operatorBypassedRuleIds` 为空、status 为 `GROUNDED`（与改动前逐字一致）；
  2. 绑定的事实关键词不命中 → `operatorBypassedRuleIds` 非空、status 为 `PARTIAL`（**不是** `GROUNDED`）；
  3. 未绑定任何事实 → status 为 `UNSUPPORTED`、`operatorBypassedRuleIds` 为空。
- **I-5**：单测按 I-5 的四行表逐行断言 `allowedHandlings(item)` 与 `recommendedHandling(item)` 的返回值（含顺序）。特别断言：`PARTIAL` 且 `operatorBypassedRuleIds` 为空时，允许集**逐字等于** `[ANSWER_SUPPORTED_PART, ACKNOWLEDGE_PENDING, OMIT]`。
- **I-6**：`grep -n "RequestGroundingStatus\." src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 中 `validateItemHandling` 函数体内**零命中**（已收口）；`grep -n "item.status != RequestGroundingStatus.UNSUPPORTED" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` **零命中**。
- **I-7**：单测 —— 混合生成的答案含 `share your CV at your convenience … initial eligibility review` 时通过 `validateLockedItem`；含 `Please send your passport` 时抛 `TRUST_REPLY_CLAIM_INVALID`。
- **I-8**：单测 —— 混合生成结果 `claims.isEmpty()`；`assemble` 时该条目不进 `groundedSections`（断言 `validateGroundedTrustBoundary` 未因该条目被触发，或 `groundedSections` 中无该 `requestIndex`）。
- **I-9**：单测 —— `adjustItem` 传空 `operatorInstruction` + 新 handling 时抛 422 `TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID`（**不是** 500）。
- **I-10**：`grep -n "sendIds" src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` —— `sendIds` 的定义式逐字仍为 `ordered.flatMap { it.factRuleIds }.distinct()`。单测：绑定 2 条事实、其中 1 条被 I-2 采纳 1 条落入 dropped，断言 `sendQaRuleIds` 只含被采纳的那条。
- **S-1**：`git diff src/main/resources/static/styles.css` 为空；`grep -n "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT" src/main/resources/static/trust-reply-workbench.js` 命中处均不含 `style=` 或新 class 名。JS 单测断言：`availableHandlings` 含新值时渲染出的 `<option>` 文案为 `依据+说明混合`。
- **S-2**：JS 单测断言 —— `draftHandling` 为新值且 `instruction` 为空时点生成，`request.error === "请先填写回答说明"`；且此时说明框标签为 `回答说明（AI 将仅据此生成）`。
- **IP-2 集成**：`TrustReplyWorkbenchItemFlowTest` 用例 —— 先以 `UNSUPPORTED` 锁定一条 `ANSWER_FROM_OPERATOR_INPUT`，再给该条目绑定一条事实使其变 `PARTIAL`，断言该锁定项仍能通过 `validateLockedItem` 并成功 `assemble`。
- **IP-3 集成**：用例 —— 绑定事实后选「回答有依据部分」生成并锁定，断言产出**恰好 1 条** `general.answer` claim，其 `sourceRuleIds` 等于 `item.factRuleIds`，且 `canonicalizeClaims` 不抛异常。
- **IP-6 集成**：用例 —— 断言 `selection.sendQaRuleIds` 含被采纳的绑定事实 id。
- **回归**：执行 `## 验证命令` 节的全量测试命令通过；构建命令通过；`node --check` 通过。

---

## 人工验收清单

### A-1：绑定事实后条目状态与可选处理方式
- 前置条件：一封已进工作台的来信，含一条状态为「无依据」（`UNSUPPORTED`）的摘要（例如 `Could you please clarify what you would like me to do now?`）；QA 事实库中存在一条已启用、正文非空的事实（记为 F1，其关键词与该问句**无任何重叠**）。
- 操作步骤：
  1. 展开该摘要卡片，点「+ 添加事实」，选中 F1，确认。
  2. 观察卡片顶部的状态标签。
  3. 展开「处理方式」下拉，读出全部选项文案。
- 预期结果：
  - 状态标签由 `UNSUPPORTED · 无依据` 变为 `PARTIAL`（界面文案「有部分依据」）；
  - 「对应事实」计数由 `0` 变为 `1`，chips 区显示 F1，且**不再**出现「未采纳的绑定」提示；
  - 下拉选项恰为：`回答有依据部分`、`依据+说明混合`、`按回答说明生成`、`确认待补充`、`省略此项`；
  - 默认选中的是 `回答有依据部分`。
- 覆盖：Observable outcome 1、2；I-4；I-5

### A-2：依据+说明混合能同时用上事实与说明
- 前置条件：承 A-1。
- 操作步骤：
  1. 处理方式选 `依据+说明混合`。
  2. 回答说明填：`请用英文回复。现阶段希望专家方便时提供简历（at your convenience），以便我们做资格初核（initial eligibility review）；后续可安排一次 Zoom 视频会议详谈。`
  3. 点「重试 AI 调整」。
- 预期结果：生成成功（**不**出现「AI 未能产出可用的回答」）；答案是**一段连续文字**（不是两段拼接）；其中同时出现 ① F1 正文里的事实性内容（机构名/项目名/数字等，可与 F1 原文逐项核对），② 索要简历与提议 Zoom 会议两个动作；且索要简历那一句内同时含 `at your convenience` 与 `eligibility review`。
- 覆盖：Observable outcome 3；I-7

### A-3：说明为空时被前端拦住，不落到 500
- 前置条件：承 A-1，处理方式选 `依据+说明混合`，回答说明清空。
- 操作步骤：点「重试 AI 调整」。
- 预期结果：条目内出现红字 `请先填写回答说明`，卡片自动展开；浏览器 Network 面板中**没有**发出生成请求；无 500 报错。
- 覆盖：I-9；S-2

### A-4：绑定事实进入外发审计
- 前置条件：承 A-2，该条目已「采用」并锁定；整封回复已整合并实际发送。
- 操作步骤：在专家详情 / 邮件详情里查看该封外发邮件关联的「使用的 QA 事实」列表（或直接查 `mail_record_qa_rule` 表中该 `mail_record_id` 的行）。
- 预期结果：列表中包含 F1。
- 覆盖：Observable outcome 4；I-10；IP-6

### A-5：升级路径 —— 先锁运营答案再绑事实，整合不炸（跨路径回归）
- 前置条件：一条 `UNSUPPORTED` 摘要。
- 操作步骤：
  1. 处理方式选 `按回答说明生成`，填说明，生成并「采用」（条目变为已锁定）。
  2. **在已锁定状态下**给同一条目「+ 添加事实」绑定 F1。
  3. 点「整合为整封回复」。
- 预期结果：整合成功，不出现 `TRUST_REPLY_LOCKED_ITEM_INVALID` 或 `TRUST_REPLY_HANDLING_INVALID`；已锁定的运营答案正文逐字保留在整合结果中。
- 覆盖：IP-2；I-6

### A-6：未绑定事实的条目行为不变（回归 must-NOT-change 6）
- 前置条件：一条 `UNSUPPORTED` 摘要，**不做**任何事实绑定。
- 操作步骤：展开「处理方式」下拉。
- 预期结果：选项恰为 `按回答说明生成`、`确认待补充`、`省略此项` 三项，默认选中 `按回答说明生成`；与本次改动前一致。
- 覆盖：需求描述 What must NOT change 第 6 条

### A-7：已有依据的条目行为不变（回归 must-NOT-change 2）
- 前置条件：一封来信中存在一条状态为 `GROUNDED`（「依据完整」）的摘要，其事实由系统自动匹配得出。
- 操作步骤：展开该条目「处理方式」下拉；按推荐方式生成一次并与改动前的产出逐字比对。
- 预期结果：下拉选项恰为 `依据完整回答`、`省略此项` 两项；生成正文与 claim 的 `sourceRuleIds` 与改动前逐字相同。
- 覆盖：需求描述 What must NOT change 第 2 条；I-4 第 1 例

### A-8：自动回复链路不受影响（回归 must-NOT-change 1）
- 前置条件：一个开启了自动回复的收件账号；构造一封能命中 QA 规则的来信。
- 操作步骤：触发一次自动回复（或用「自动回复预判」面板对同一封信预判），查看使用的事实集与判定结果。
- 预期结果：使用的事实集、判定（可自动发 / 转人工）、各门禁项与本次改动前完全一致。
- 覆盖：需求描述 What must NOT change 第 1 条

### A-9：整体就绪状态随绑定变化（跨路径）
- 前置条件：一封来信，其**唯一**未就绪原因是一条 `UNSUPPORTED` 摘要，工作台顶部就绪状态显示 `BLOCKED`。
- 操作步骤：给该摘要绑定 F1，观察顶部就绪状态。
- 预期结果：就绪状态由 `BLOCKED` 变为 `READY`。
- 覆盖：IP-4

### A-10：一键预判自动填说明在 PARTIAL 条目上不再给建议（已知取舍，确认不阻断）
- 前置条件：承 A-1（条目已变 `PARTIAL`）。
- 操作步骤：点「一键预判」或触发自动填说明的入口，观察该条目的回答说明框。
- 预期结果：说明框保持为空（不报错、不卡住）；运营手动填入说明后一切功能正常。若该行为不可接受，属需求变更，回到计划修订，不在实施中扩大 `suggestedInstructionFor` 的适用范围。
- 覆盖：IP-5

### A-11：界面无样式失真（UI 目测）
- 前置条件：承 A-1。
- 操作步骤：对照 `## 样式契约` 的「改动前基线」，逐项目测处理方式下拉区域。
- 预期结果：下拉框宽度、字号、边框、圆角、`disabled` 灰态与其右侧「版本」下拉**完全一致**；`依据+说明混合` 选项在下拉中位于 `回答有依据部分` 与 `按回答说明生成` 之间；整个条目卡片无任何间距或对齐变化。
- 覆盖：S-1；S-3；需求描述 What must NOT change 第 7 条

---

## Phase 6 · 知识库写回（实施完成后执行）

1. **修订** `docs/knowledge/llm/K-fact-matrix-two-semantics-in-one-field.md`：追加「2026-08-21 更新」小节，写明 D1/D2 已被推翻、绑定事实现在可成为 `factRuleIds` 与 `sendQaRuleIds` 成员、绕过只发生在矩阵路径。
2. **修订** `docs/knowledge/llm/K-operator-directed-authorization-seam.md`：把 `TrustReplyWorkbenchService.validateLockedItem:1396` 的 status 前置判定补为第 6 处闸门（本计划已消除，但需记录它曾存在及为何容易漏）。
3. **新增** `docs/knowledge/llm/K-request-key-includes-intent-keys.md`：`requestKey` 的哈希输入含 `item.intents.map { it.intentKey }`，因此**任何**改变 intent coverage **条目集合**的改动都会让全站 requestKey 漂移、工作台打不开；改 coverage 的 status/evidence 安全，增删条目不安全。这是本计划研究中最容易踩空的一条。
4. **新增** `docs/knowledge/llm/K-workbench-matrix-path-is-operator-scoped.md`：`resolveMatrixSelection` 只在 `selectForWorkbench(selectionsByRequest != null)` 时可达，自动回复与人工发送走 `select()`；对工作台放宽证据口径不会外溢到自动链路——这是所有"只给工作台放宽"类改动的安全依据。
