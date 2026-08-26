# 04 · 人工富文本发送：QA 事实类硬门禁降级为可二次确认

> 基线：`main` @ `0949fa9`（2026-08-21）
> 与 `03-verbatim-fact-answer-handling.md` **无代码耦合**，可并行执行。

## 需求描述

### Observable outcome
1. 人工富文本发送时，**所选 QA 事实与来信对不上**（关键词不匹配 / 来信抽不出问句 / 规则被停用 / 规则不存在 / `replyPolicy=NEVER` / 事实正文为空）不再直接失败弹 `alert`，而是走**已有的二次确认流程**：先弹窗逐条列出风险，运营确认后即可继续发送。
2. **所选事实在服务端全部不成立**时同样不再 422，改为一条可确认的风险项「所选事实均未被系统认可为本次来信的依据」，运营确认后可发送。
3. 上述两类风险在确认弹窗里显示**可读中文**，不再落到「发现未分类风险，请人工核对」这句兜底文案。

### What must NOT change
- N-1 `QaFactSelectionService.select(...)` 的行为**逐字不变**——包括它对显式选择的四道校验与抛 `IllegalArgumentException` 的时机（K-explicit-fact-selection-must-match-request 是 P1 经验，其他 10 个调用点依赖它）。
- N-2 `QaFactSelectionService.selectForWorkbench(...)` / `buildRequestFact(...)` 不变（本计划不碰工作台取证）。
- N-3 自动回复链路（`AutoMailReplyService` / `GroundedAutoReplyDecisionService`）逐字不变。
- N-4 组 C/D/E 的硬闸**全部保留**：`mailVariableService.requireValidPlaceholders(...)` 五处、长度/非空校验、`emailSuppressionService.isSuppressed(...)`（需求方 2026-08-21 明确拍板）。
- N-5 `collectSafetyFindings` 既有 findings 的产出条件与 `severity` 分级不变；`strongConfirmationText == "确认发送"` 的强确认门槛不变。
- N-6 `evaluateComposedReply`（`:478-504`）与 `preflightEditedAiReply`（`:914-1034`）的对外行为不变。
- N-7 审计口径不变：`carriesQa` 仍由 `qaRuleIds` 是否非空决定（K-rich-reply-qa-audit-reuse）。

### Out of scope
- 不改 `QaRequestExtractor` 的请求切分。
- 不给 `evaluateComposedReply` 加同款降级（那是工作台评估接口，不是发送路径）。
- 不改 `preflightEditedAiReply` 已有的 try/catch 降级逻辑（`:944-950`）。
- 不改 `ActionViolation.code == null` 输出裸 `ACTION_VIOLATION` 的问题（K-manual-send-safety-gate-first-hit-only 记录的另一件事）。
- 不重做二次确认 UI（已存在且符合口径）。

---

## 关键不变量

### I-1: `select()` 是共享 API，本计划一行都不改
- Rule: 放宽只能发生在 `PendingMailOperationService.canonicalizeFactRuleIds`（`:552-559`，全仓**唯一调用者是 `:165`**，`grep -rn "canonicalizeFactRuleIds(" src/main/kotlin/` 恰 2 行：定义 `:552` + 调用 `:165`）这一个接缝上。`QaFactSelectionService.select` 的方法体、抛出条件、既有单测断言**全部保持不变**。
- Applies to: `select()` 的 11 个生产调用点（`AiReplyDraftService.kt:2112`；`PendingMailOperationService.kt:170/474/490/491/557/759/761/946/949/952`）。
- Violation consequence: 若在 `select()` 内部放宽，全库每条规则都可能成为任意问题的候选证据，自动回复会拿运营口径的证据发信且完全静默。
- 来源: K-explicit-fact-selection-must-match-request、K-workbench-matrix-path-is-operator-scoped

### I-2: 降级只吃 `IllegalArgumentException`，不吃其他异常
- Rule: `canonicalizeFactRuleIds` 的 try/catch **只捕获 `IllegalArgumentException`**（`validateExplicitSelection:356-370` 与 `validateExplicitRulesMatchRequests:372-396` 抛的正是它）。`ResponseStatusException`、`TrustReplyWorkbenchException`、DB/IO 异常必须继续向上抛。
- Applies to: `canonicalizeFactRuleIds`。
- Violation consequence: `catch (Exception)` 会把数据库不可用、事务失败之类的真故障吞成"可确认的风险"，运营点一下确认就把半损坏状态发出去。
- 来源: original（对比 `preflightEditedAiReply:945` 的 `catch (ex: Exception)` —— 预检是只读的，发送不是；**不得照抄**）

### I-3: 降级后的 `canonicalFactIds` 只能是运营选择的**子集**，永不回退全集
- Rule: 降级路径产出的 `canonicalFactIds` 必须满足 `canonicalFactIds ⊆ qaRuleIds`（运营原始选择），且顺序为 `qaRuleIds` 的相对顺序。**禁止**在任何分支用 `select(inboundText, null, ...)`（自动全集）的结果充当 `canonicalFactIds`。全部不成立时结果就是**空列表**。
- Applies to: `canonicalizeFactRuleIds` 的降级分支；下游 `payload.canonicalQaRuleIds`（`:265`）→ `mail_record_qa_rule`（`ManualReplySendAttemptService.finalizeSuccess:250-260`）→ `QaRuleAuditService.resolveSelectedRuleIds:74-89`。
- Violation consequence: `mail_record_qa_rule` 会关联运营根本没选的规则，QA 使用率统计全错。
- 来源: K-ai-reply-prompt-vs-send-rule-ids、K-rich-reply-qa-audit-reuse

### I-4: `carriesQa` 的判据仍是 `qaRuleIds`，不是 `canonicalFactIds`
- Rule: `:163` 的 `val carriesQa = !qaRuleIds.isNullOrEmpty()` 保持不变。降级后 `canonicalFactIds` 可能为空而 `carriesQa` 仍为 `true`——此时审计仍记 `SEND_MANUAL_COMPOSED_REPLY`，`mail_record_qa_rule` 零行，`matchedQaRuleId = null`。这是**正确**结果，不是缺陷。
- Applies to: `recordSendAudit(carriesQa = carriesQa, canonicalFactIds = canonicalFactIds)`（`:305-311`）；`finalizeSuccess` 的 `if (payload.canonicalQaRuleIds.isNotEmpty())` 守卫（`ManualReplySendAttemptService.kt:250`）。
- Violation consequence: 改成 `canonicalFactIds.isNotEmpty()` 会让这类发送记成 `SEND_MANUAL_RICH_REPLY`，`QaRuleAuditService.aggregateRuleUsage`（只查 `SEND_MANUAL_COMPOSED_REPLY`）从此看不见它，"运营删掉了哪些建议规则"的统计出现缺口。
- 副作用（已知且接受）：`resolveSelectedRuleIds` 回退到 `after["qaRuleIds"]`（`QaRuleAuditService.kt:88`）= 空列表 → 该次发送会把**全部 `serverSuggestedFactIds` 记为"被运营移除"**（`:42-44`）。这在语义上是准确的。
- 来源: K-rich-reply-qa-audit-reuse、K-audit-selected-source

### I-5: 高风险声明的取证源只能是**系统认可**的事实
- Rule: `collectSafetyFindings(canonicalFactIds = ...)`（`:230-238`）收到的必须是**通过了 `select()` 全套校验**的那个子集，而不是运营的原始选择。降级路径必须对子集再跑一次真正的 `select()`，用它返回的 `sendQaRuleIds` 作为 `canonicalFactIds`。
- Applies to: `aiReplyHighRiskClaimValidator.validatePlainText(verificationText, canonicalFactIds)`（`:734-736`）；`qaFactSelectionService.select(inboundText, canonicalFactIds, ...)`（`:759`）。
- Violation consequence: 把"运营主张但系统不认"的事实正文当作高风险声明的背书来源——正文里的金额/承诺会被一条不相干的事实"证明"，`AI_REPLY_CLAIM_HIGH_RISK_UNBACKED` 静默失效。这正是 K-explicit-fact-selection-must-match-request 要防的事。
- 来源: K-explicit-fact-selection-must-match-request、K-answerbody-source-exclusive

### I-6: 子集为空时不得再调 `select(explicit)`
- Rule: `validateExplicitSelection:357` 首行是 `require(ruleIds.isNotEmpty())`。因此匹配子集为空时**必须**直接用 `emptyList()` 作为 `canonicalFactIds`，禁止调用 `select(inboundText, emptyList(), ...)`（会抛 `IllegalArgumentException` 变成 500）。
- Applies to: `canonicalizeFactRuleIds` 降级分支。
- Violation consequence: 500 而不是可确认的风险，需求完全落空。
- 来源: original（`QaFactSelectionService.kt:357` 实读）

### I-7: `QA_FACTS_ALL_INVALID` 的死分支必须被激活，而不是再造一个新码
- Rule: `collectSafetyFindings:742-745` 已有 `else if (carriesQa && canonicalFactIds.isEmpty()) { add("QA_FACTS_ALL_INVALID") }`，因 `:176-181` 的 422 抛在前而**永不可达**。本计划删掉那个 422 后该分支自然可达，**不得**为同一情形新增第二个码。
- Applies to: `collectSafetyFindings`；`PendingMailOperationService:176-181`。
- Violation consequence: 两个码表达同一件事，运营看到重复风险项；且 K-manual-send-safety-gate-first-hit-only 已把这条记为"已知不可达分支，不要当缺陷报"——留着它不可达会让下一轮验证继续困惑。
- 来源: K-manual-send-safety-gate-first-hit-only

### I-8: 新增的 finding code 必须同时进前端文案表
- Rule: 任何新增的 `SafetyFinding.code` 必须在 `app.js` 的 `AI_REPLY_WARNING_LABELS`（`:4450-4474`）里有对应中文；`QA_FACTS_ALL_INVALID` 当前**不在表中**（`grep -n "QA_FACTS_ALL_INVALID" src/main/resources/static/app.js` → 0 命中），也必须补上。
- Applies to: `app.js:4450-4474`；消费点 `:4796`（`AI_REPLY_WARNING_LABELS[code] || String(code)`）与 `:10033`（`|| "发现未分类风险，请人工核对"`）与确认弹窗 `:10684`（`|| "正文包含需人工核对的风险声明"`）。
- Violation consequence: 运营在确认弹窗里只看到「正文包含需人工核对的风险声明」这句无信息量的兜底，无法判断该不该确认——等于把硬闸换成了盲确认。
- 来源: original（三处兜底文案实读）

### I-9: 两类新风险都是 `NORMAL` 级，不升 `STRONG`
- Rule: `collectSafetyFindings` 内 `add(code)` 的 severity 由 `:721-726` 决定——只有 `AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL` 是 `STRONG`，其余一律 `NORMAL`。本计划新增的码走默认 `NORMAL`，即**一次**确认弹窗即可，不要求输入「确认发送」。
- Applies to: `collectSafetyFindings` 的 `add()` 辅助函数（`:719-731`）。
- Violation consequence: 升成 `STRONG` 会让"事实标签对不上"这种低危情形也要求逐字打字确认，运营会养成无脑打字的习惯，冲掉敏感材料索取那条真正的强确认。
- 来源: original（`:721-726` 实读）

---

## 样式契约

> 本计划触及前端文件 `src/main/resources/static/app.js`，故本节必填。改动**只有一个常量对象的 4 行新增**，**零 DOM 变更、零 CSS 变更**。

### S-1: 风险文案表新增 4 个键
- 复用：确认弹窗的渲染与样式全部复用既有实现——`app.js:10682-10688` 的 `renderFindings` 已在用 `.ai-reply-error`（STRONG）/ `.ai-reply-warning`（NORMAL）/ `.ai-reply-coverage` / `.ai-reply-feedback`；橙色预检条复用 `app.js:10033` 的既有渲染。**禁止**为新码新造 class、新造样式或改动上述任一 class 的规则块。
- 新增：`AI_REPLY_WARNING_LABELS`（`app.js:4450-4474`）表尾、`AI_REPLY_PREFLIGHT_NO_EVIDENCE` 那一行之后，**逐字**新增以下 4 行（缩进 4 空格，与表内既有行一致）：

```js
    QA_FACTS_ALL_INVALID: "所选事实均未被系统认可为本次来信的依据，本次发送不会关联任何 QA 事实。",
    QA_FACT_NOT_MATCHING_REQUEST: "部分所选事实的关键词与来信中的问句对不上，系统未把它们当作依据。",
    QA_FACT_UNAVAILABLE: "部分所选事实已停用、被设为不可外发或事实正文为空，系统未把它们当作依据。",
    QA_FACT_NO_EXTRACTABLE_REQUEST: "来信中没有可识别的问句，所选事实无法与任何问题对应。",
```

- DOM 结构：不变。新码经既有 `AI_REPLY_WARNING_LABELS[finding.code] || ...` 查表逻辑自动获得文案，无需任何模板改动。
- 禁止项：inline style；新增 class；修改 `styles.css` 任何一行；改动 `submitManualRichReply`（`:10655-10716`）的函数体。

### S-2: 既有 class 使用点核查
- 本契约不修改任何既有 class 的规则块，`git diff src/main/resources/static/styles.css` 必须为空。

---

## 现状审计

> Step 1b-fe 的样式盘点已落进上方 `## 样式契约` 与下方「前端文案盘点」小节。

### 接缝 A：`PendingMailOperationService.sendManualRichReply`（`:130-465`）
发送前的全部阻断点，按执行顺序（实读 `:156-270`）：

| 组 | 行号 | 判据 | 异常 | 本计划 |
|---|---|---|---|---|
| — | `:156-159` | subject 非空 / ≤255 | `IllegalArgumentException` → 400 | 保留（N-4） |
| — | `:157` | htmlBody 非空 | 同上 | 保留（N-4） |
| **A** | `:165` | `canonicalizeFactRuleIds` → `select(explicit)` | `IllegalArgumentException` → 400 | **降级** |
| **B** | `:176-181` | `carriesQa && canonicalFactIds.isEmpty()` | `ResponseStatusException` 422「所选的QA事实已全部失效，请重新选择」 | **删除**（由 I-7 的 finding 接管） |
| — | `:183` | `resolvePendingReplyAccount` | 抛 | 保留 |
| C | `:185` / `:194` / `:196` / `:198` / `:217` / `:218` | `requireValidPlaceholders` ×6 个调用点（`:196`/`:198` 互斥分支，单次调用最多执行 5 次） | `IllegalArgumentException` → 400 | 保留（N-4） |
| D | `:187-215` | 渲染后 subject 非空且 ≤255（`:187-189`）、`finalValidationText` 非空且 ≤20000（`:211-215`） | 同上 | 保留（N-4） |
| — | `:230-244` | `collectSafetyFindings` → `ManualSendSafetyBlockedException` | 可二次确认 | **扩容**（承接 A、B） |
| D | `:257-262` | `inReplyTo` ≤255 | 422 | 保留（N-4） |
| E | `:265-270` | `emailSuppressionService.isSuppressed` | 400「收件人已退订，禁止外发」 | 保留（N-4） |

**已实现且符合口径的二次确认通道**（本计划复用，不重做）：
`ManualSendSafetyBlockedException`（`:1136-1138`）→ `GlobalExceptionHandler:32-42` 输出 `code = MANUAL_SEND_SAFETY_BLOCKED` + `findings` + `requiresStrongConfirmation`（`= findings.any { it.severity == STRONG }`）→ `app.js:10677-10714` 弹一次 `openActionDialog("confirm", ...)`，`requiresStrongConfirmation === true` 时再弹 `confirm-typed` 要求逐字输入「确认发送」→ 带 `safetyWarningConfirmed: true` 重发。

### 接缝 B：`QaFactSelectionService`（**只读，不改**）
- `select(inboundText, selectedRuleIds, researchProfileSufficient)`（`:22-60`）：
  - `:32` `validateExplicitSelection(it)`（`:356-370`）——规则不存在 / `enabled == false` / `replyPolicyEnum() == NEVER` / `answerBody.trim().isBlank()` → `IllegalArgumentException`
  - `:34` `validateExplicitRulesMatchRequests(explicitRules, requestTexts)`（`:372-396`）——`requestTexts.isEmpty()` 或存在关键词不匹配任何 request 的规则 → `IllegalArgumentException`
- 关键词匹配器 `QaFactKeywordMatcher`（`:580-606`）是 **`internal object`**，与 `PendingMailOperationService` 同一编译模块，可直接调用；`matchesRule` 对**无关键词**的规则直接 `return false`（`:597-600`）。
- 请求抽取 `QaRequestExtractor.extract(inboundText)`（`qa/service/QaRequestExtractor.kt`）为 public，`QaFactSelectionService.extractRequests:569` 只是薄封装。
- **既有单测锁死了这些行为**（改 `select` 必然打破，故 I-1）：
  - `QaFactSelectionServiceTest:92` `explicit NEVER rule is rejected`
  - `QaFactSelectionServiceTest:128` `explicit rules must match at least one extracted request`
  - `QaFactSelectionServiceTest:142` `explicit rules never bypass keyword matching per request`
  - `QaFactSelectionServiceTest:160` `explicit mixed matching and non-matching rules is rejected`（断言异常 message 含 `"2"`）

### 存储：`mail_record_qa_rule`（QA 使用率审计的权威来源）
- 写路径全集（`grep -rn "mailRecordQaRuleRepository.save" src/main/kotlin/` → **3 处**）：
  1. `ManualReplySendAttemptService.kt:253`（在 `finalizeSuccess:250-260` 内），守卫 `if (payload.canonicalQaRuleIds.isNotEmpty())` —— **本计划唯一涉及的写点**
  2. `AutoMailReplyService.kt:637` —— 自动回复路径，本计划不触及（N-3）
  3. `ManualExpertMailService.kt:92` —— 手动专家发信路径，本计划不触及
- 读路径：`QaRuleAuditService.resolveSelectedRuleIds:74-89`——先查关联表，`fromAssociation.isEmpty()` 时回退 `after["qaRuleIds"]`（`:88`）。
- Interaction point **IP-1**：本计划让 `carriesQa == true && canonicalFactIds.isEmpty()` 首次成为**可达状态**。链路已验证安全：`finalizeSuccess:250` 的守卫使关联表零行；`matchedQaRuleId = payload.primaryRuleId = canonicalFactIds.firstOrNull() = null`（`:174`），`mail_record.matched_qa_rule_id` 可空；审计回退到空列表。语义后果见 I-4。

### Interaction point IP-2：预检与发送的判据分叉（本计划**收敛**它）
`preflightEditedAiReply:944-950` 已经把 `select(explicit)` 的异常降级成 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED` 警告并继续：

```kotlin
val selection = if (factRuleIds.isNotEmpty()) {
    try { qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient) }
    catch (ex: Exception) {
        warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
        qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
    }
} else { ... }
```

即**预检说"有风险但可以发"，发送却直接 400** —— 这是 K-preview-mirrors-pipeline 明令禁止的分叉，也解释了截图里"依据已变化或不可用"橙条与 `alert` 同时出现的现象。本计划把发送侧对齐到"可确认"，分叉消失。
注意预检的回退**换掉了** `canonicalFactIds`（用自动全集重算），发送侧**不得**照抄这一点（I-3）。

### `IllegalArgumentException` 的 HTTP 映射
`GlobalExceptionHandler:16-17` `@ExceptionHandler(IllegalArgumentException::class)` → 400 + `ApiErrorResponse`；前端 `app.js:10716` `alert("人工回复发送失败: " + e.message)`。这是截图中弹窗的完整来源链。

### 前端文案盘点（Step 1b-fe，仅常量）
- `AI_REPLY_WARNING_LABELS`（`app.js:4450-4474`）现有 23 个键，**不含** `QA_FACTS_ALL_INVALID`、`ACTION_VIOLATION`、`CLAIM_VALIDATION_FAILED`。
- 三处兜底：`:4796` `|| String(code || "")`；`:10033` `|| "发现未分类风险，请人工核对"`（截图第二条橙条即此）；`:10684` `|| "正文包含需人工核对的风险声明"`（确认弹窗内）。
- 不涉及 `styles.css`：确认弹窗复用既有 `.ai-reply-error` / `.ai-reply-warning` / `.ai-reply-coverage` / `.ai-reply-feedback` class（`app.js:10682-10688` 已在用），本计划不新增/不修改任何 class。

---

## 实现方案

### 阶段 T1 — 新增一个只读的"可选性分区"查询（`QaFactSelectionService.kt`）

- **T1.1** 新增 public 方法，**不改动任何既有方法**：

  ```kotlin
  data class ExplicitSelectionPartition(
      val selectable: List<Long>,      // 通过全部校验、且至少匹配一条 request
      val unavailable: List<Long>,     // 不存在 / 停用 / policy=NEVER / answerBody 空
      val unmatched: List<Long>,       // 规则可用，但关键词不匹配任何 request
      val noRequests: Boolean          // 来信抽不出任何 request
  )

  fun partitionExplicitSelection(inboundText: String, ruleIds: List<Long>): ExplicitSelectionPartition
  ```
  实现直接复用现成构件：`extractRequests(inboundText)` → `QaFactKeywordMatcher.normalize` → `QaFactKeywordMatcher.matchesRule`；可用性判据逐字照抄 `validateExplicitSelection:359-368` 的四条（`findById` 为空 / `!enabled` / `replyPolicyEnum() == NEVER` / `answerBody.trim().isBlank()`）。
  `selectable` 保持 `ruleIds` 的相对顺序。**本方法不抛异常。** *遵守 I-1 / I-3*
- **T1.2** 显式核查 `select` / `selectForWorkbench` / `buildRequestFact` / `validateExplicitSelection` / `validateExplicitRulesMatchRequests` 的方法体 diff 为空。**零改动核查项。** *遵守 I-1 / N-1 / N-2*

### 阶段 T2 — 发送路径降级（`PendingMailOperationService.kt`）

- **T2.1** `canonicalizeFactRuleIds`（`:552-559`）改为返回带诊断的结果，签名变为：

  ```kotlin
  private data class CanonicalFactResolution(
      val canonicalFactIds: List<Long>,
      val degradedCodes: List<String>   // 有序、去重
  )
  private fun canonicalizeFactRuleIds(...): CanonicalFactResolution
  ```
  逻辑：

  1. `try { CanonicalFactResolution(qaFactSelectionService.select(inboundText, requestedRuleIds, researchProfileSufficient).sendQaRuleIds, emptyList()) }`
  2. `catch (ex: IllegalArgumentException)` —— **只捕这一种**（I-2）：
     - `val p = qaFactSelectionService.partitionExplicitSelection(inboundText, requestedRuleIds)`
     - `codes`：`p.noRequests` → `QA_FACT_NO_EXTRACTABLE_REQUEST`；`p.unmatched` 非空 → `QA_FACT_NOT_MATCHING_REQUEST`；`p.unavailable` 非空 → `QA_FACT_UNAVAILABLE`
     - `ids`：`if (p.selectable.isEmpty()) emptyList()`（I-6）`else qaFactSelectionService.select(inboundText, p.selectable, researchProfileSufficient).sendQaRuleIds`
     - 内层 `select` 理论上不应再抛（入参已是 selectable 子集）；若仍抛 `IllegalArgumentException`，**降级为 `emptyList()` + 追加 `QA_FACT_UNAVAILABLE`**，不得让异常逃逸。
  *遵守 I-1 / I-2 / I-3 / I-5 / I-6*
- **T2.2** `:164-168` 调用点改为接收 `CanonicalFactResolution`，`:163` 的 `carriesQa` 判据**逐字不动**（I-4）。
- **T2.3** **删除** `:176-181` 的 `ResponseStatusException` 整块。*遵守 I-7*
- **T2.4** `collectSafetyFindings` 新增一个形参 `degradedFactCodes: List<String> = emptyList()`，在函数开头（`:733` 的 `if (carriesQa && canonicalFactIds.isNotEmpty())` 之前）`degradedFactCodes.forEach { add(it) }`。`add()` 的 severity 逻辑不动 → 新码自动为 `NORMAL`。*遵守 I-9*
- **T2.5** `:230-238` 的调用点传入 `degradedFactCodes = resolution.degradedCodes`。
- **T2.6** `preflightEditedAiReply:1004-1012` 的 `collectSafetyFindings` 调用点**不传**新形参（用默认值）——预检已有自己的降级码 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED`，不重复报。**零改动核查项。** *遵守 N-6*
- **T2.7** 新码常量定义在 `PendingMailOperationService` 的 companion（与既有 `AI_REPLY_PREFLIGHT_*` 同处），三个值逐字为：`QA_FACT_NOT_MATCHING_REQUEST`、`QA_FACT_UNAVAILABLE`、`QA_FACT_NO_EXTRACTABLE_REQUEST`。

### 阶段 T3 — 前端文案（`app.js`）

- **T3.1** `AI_REPLY_WARNING_LABELS`（`:4450-4474`）**新增且仅新增** 4 行：

  ```js
    QA_FACTS_ALL_INVALID: "所选事实均未被系统认可为本次来信的依据，本次发送不会关联任何 QA 事实。",
    QA_FACT_NOT_MATCHING_REQUEST: "部分所选事实的关键词与来信中的问句对不上，系统未把它们当作依据。",
    QA_FACT_UNAVAILABLE: "部分所选事实已停用、被设为不可外发或事实正文为空，系统未把它们当作依据。",
    QA_FACT_NO_EXTRACTABLE_REQUEST: "来信中没有可识别的问句，所选事实无法与任何问题对应。",
  ```
  插入位置：`AI_REPLY_PREFLIGHT_NO_EVIDENCE` 之后（表尾）。*遵守 I-8*
- **T3.2** `submitManualRichReply`（`:10655-10716`）与确认弹窗渲染（`:10682-10688`）**零改动**——新码经 `AI_REPLY_WARNING_LABELS` 查表后即可正常显示。**零改动核查项。** *遵守 N-5*

### 阶段 T4 — 测试

- **T4.1** `QaFactSelectionServiceTest.kt` 新增 4 个用例（**只测新方法**）：全部可选；混合可选/不匹配；全部不匹配；来信抽不出 request（`noRequests == true`）。并显式保留 `:92/:128/:142/:160` 四个既有用例**逐字不变**。
- **T4.2** `PendingMailOperationServiceTrustWorkbenchTest.kt`：
  - **改写** `:206-227` `sendManualRichReply blocks on QA facts all invalid` —— 断言从 `ResponseStatusException` 改为 `ManualSendSafetyBlockedException` 且 `findings.map { it.code }` 含 `QA_FACTS_ALL_INVALID`；补一个后续断言：带 `safetyWarningConfirmed = true` 重发时**成功**且 `mailDeliveryService` 被调用一次。（K-ui-removal-retires-obsolete-contract-tests：行为变更必须同步退役旧契约断言）
  - **新增** 3 个用例：`select` 抛 `IllegalArgumentException` 时不再 400，而是 `ManualSendSafetyBlockedException` 且含 `QA_FACT_NOT_MATCHING_REQUEST`；确认后发送成功且 `payload.canonicalQaRuleIds` 等于可选子集（I-3）；子集为空时 `mail_record_qa_rule` 零行且 `matchedQaRuleId` 为 null（I-4 / IP-1）。
  - **注意**：该测试类把 `qaFactSelectionService` mock 掉了（`:210` 用 `Mockito.when(...select(...))`）。新增用例需同时 stub `partitionExplicitSelection`；既有用例因 `select` 的 stub 正常返回而**不会**进入 catch 分支，**无需**补 stub —— 这是选择"只在 catch 里调新方法"的直接收益，实现时不得改成无条件调用。
- **T4.3** `src/test/js/aiReplyReviewConfirmation.test.js` 新增 1 个用例：`AI_REPLY_WARNING_LABELS` 同时含上述 4 个新键。
- **T4.4** 回归核查：`QaFactSelectionServiceTest` 全类通过（N-1）；`AutoMailReplyServiceTest` / `AutoReplyPreviewServiceTest` / `GroundedAutoReplyDecisionServiceTest` 全通过（N-3）。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | T1.1 新增 `ExplicitSelectionPartition` + `partitionExplicitSelection`（T1.2 零改动核查） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | T2.1/T2.2/T2.3/T2.4/T2.5/T2.7（T2.6 零改动核查） |
| 3 | `src/main/resources/static/app.js` | T3.1（T3.2 零改动核查） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | T4.1 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | T4.2 |
| 6 | `src/test/js/aiReplyReviewConfirmation.test.js` | T4.3 |

文件数 **6** ≤ 10。子系统数 **2**（QA 取证服务 / 人工发送服务，前端仅一个常量对象不计作独立子系统）≤ 2。

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。JS 用例用系统 `node`（实测 v22.23.2），无需 JAVA_HOME 前缀。

```bash
# 全量测试（回归门禁，含 exec-maven-plugin 绑定的 node --test）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest

# 自动回复回归（N-3）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoReplyPreviewServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest

# 单个测试方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest#methodName

# 前端 JS 用例（权威门禁，可单跑）
node --test src/test/js/aiReplyReviewConfirmation.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- Maven：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`
- node：退出码 0，输出含 `# fail 0`
- `node --check`：退出码 0，无输出

来源：Maven 命令逐字取自项目根 `CLAUDE.md:9-27` 的「Commands」代码块与 `CLAUDE.md:140/142` 的 `test_command` / `build_command`；node 命令取自 `pom.xml:186-232` 的 `exec-maven-plugin` 三个 execution（K-js-tests-run-via-exec-plugin，实测通过）。
**注意**：`verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，**不可**用作本计划的前端回归门禁（K-js-test-invocation-surface）。

---

## 验收标准

- **I-1**：`git diff src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` 中**只有新增行**（`ExplicitSelectionPartition` + `partitionExplicitSelection`），既有方法体零删改（`git diff -U0 ... | grep '^-' | grep -v '^---'` 输出为空）。`QaFactSelectionServiceTest:92/128/142/160` 四个用例**未被修改**且通过。
- **I-2**：`grep -n "catch" src/main/kotlin/.../PendingMailOperationService.kt` 在 `canonicalizeFactRuleIds` 函数体内只出现 `catch (ex: IllegalArgumentException)`，无 `catch (Exception)` / `catch (_: Throwable)`。
- **I-3**：T4.2 新增用例断言 `payload.canonicalQaRuleIds` 是运营入参的子集且保序；`grep` 确认 `canonicalizeFactRuleIds` 函数体内无 `select(inboundText, null,` 调用。
- **I-4**：`grep -n "val carriesQa" src/main/kotlin/.../PendingMailOperationService.kt` 结果仍为 `val carriesQa = !qaRuleIds.isNullOrEmpty()`。T4.2 的 IP-1 用例通过。
- **I-5**：`collectSafetyFindings` 的 `canonicalFactIds` 实参来源仍是 `select()` 的 `sendQaRuleIds`（而非 `partition.selectable`）——代码走查 + T4.2 用例断言 `canonicalFactIds` 不含 unmatched 规则 id。
- **I-6**：`canonicalizeFactRuleIds` 内存在 `if (p.selectable.isEmpty()) emptyList() else select(...)` 的显式分支。T4.2「子集为空」用例通过且不出现 500。
- **I-7**：`grep -n "所选的QA事实已全部失效" src/main/kotlin/` 输出为空；`grep -c "QA_FACTS_ALL_INVALID" src/main/kotlin/.../PendingMailOperationService.kt` 恰为 1（即 `:744` 那一处）。
- **I-8**：`node --test src/test/js/aiReplyReviewConfirmation.test.js` 中 T4.3 用例通过（4 个新键齐全）。
- **I-9**：T4.2 全部新增用例断言 `requiresStrongConfirmation == false`（即 `findings.none { it.severity == SafetySeverity.STRONG }`），除非同一封信另外命中了敏感材料索取。
- **N-1/N-2 回归**：`QaFactSelectionServiceTest` 全类通过。
- **N-3 回归**：`AutoMailReplyServiceTest` / `AutoReplyPreviewServiceTest` / `GroundedAutoReplyDecisionServiceTest` 全通过。
- **N-4 回归**：`grep -c "requireValidPlaceholders" src/main/kotlin/.../PendingMailOperationService.kt` 仍为 **6**；`grep -c "isSuppressed" ...` 仍为 **1**；`grep -n "length <= 20000\|length <= 255" ...` 的命中行与改动前逐行一致。
- **N-5 回归**：`git diff src/main/resources/static/app.js` 只含 `AI_REPLY_WARNING_LABELS` 内的 4 行新增，`submitManualRichReply` 函数体零改动。
- **S-1**：`git diff src/main/resources/static/app.js` 的新增行与契约代码块**逐字一致**（含缩进与中文标点）；diff 中无 inline style、无新增 class。
- **S-2**：`git diff src/main/resources/static/styles.css` 输出为空。
- **N-6 回归**：`preflightEditedAiReply` 的 `collectSafetyFindings` 调用点 diff 为空。
- **N-7 回归**：`recordSendAudit(carriesQa = ...)` 实参未变。
- **IP-2 收敛**：新增或扩展一个用例，对同一 `(inboundText, factRuleIds)` 组合分别调 `preflightEditedAiReply` 与 `sendManualRichReply`，断言两者**都不**抛 400/422（预检产 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED`，发送产 `MANUAL_SEND_SAFETY_BLOCKED`）。
- 回归总门禁：执行「验证命令」节的**全量测试命令**通过。

---

## 人工验收清单

### A-1: 事实与来信对不上时可确认后发送
- 前置条件：一封来信，正文里有明确问句（例如 `What are the expectations and what will benefit me?`）；在 QA 管理页找到一条**关键词与该问句完全不沾边**的启用事实（例如关键词只有 `visa`）。在回复台把这条事实绑到该摘要上，生成并整合出草稿，进入「人工富文本回复」面板。
- 操作步骤：
  1. 点击「发送人工回复」。
  2. 观察弹窗。
  3. 点击确认。
- 预期结果：
  - **不再**出现浏览器原生 `alert`「人工回复发送失败: Selected QA rules do not match any request in the inbound email: [...]」。
  - 出现应用内确认弹窗，正文含可读中文 **「部分所选事实的关键词与来信中的问句对不上，系统未把它们当作依据。」**，并提示「确认已人工核对，仍要发送吗？」。
  - 弹窗**不要求**输入「确认发送」四个字。
  - 点确认后邮件发送成功，提示「人工回复邮件发送成功」。
- 覆盖：需求描述 outcome 1、outcome 3、I-8、I-9

### A-2: 所选事实全部不成立时可确认后发送
- 前置条件：同 A-1，但所选事实**全部**与来信无关（例如只绑那一条 `visa` 事实）。
- 操作步骤：点「发送人工回复」→ 阅读弹窗 → 确认。
- 预期结果：弹窗含 **「所选事实均未被系统认可为本次来信的依据，本次发送不会关联任何 QA 事实。」**；确认后发送成功。**不得**出现 422「所选的QA事实已全部失效，请重新选择」。
- 覆盖：需求描述 outcome 2、I-7

### A-3: 停用的事实同样走确认而非失败
- 前置条件：在 QA 管理页把一条已绑定的事实**停用**（`enabled = false`），不刷新回复台。
- 操作步骤：点「发送人工回复」→ 阅读弹窗 → 确认。
- 预期结果：弹窗含 **「部分所选事实已停用、被设为不可外发或事实正文为空，系统未把它们当作依据。」**；确认后发送成功。
- 覆盖：需求描述 outcome 1、I-8

### A-4: 审计关联正确（跨路径）
- 前置条件：A-2 已发送成功一封（可选事实子集为空）；另外用一封**事实全部匹配**的来信正常发送一封作对照。
- 操作步骤：进入「AI 训练」页的 QA 使用率统计（或直接查库：`SELECT * FROM mail_record_qa_rule WHERE mail_record_id = <本次记录 id>`），对比两封。
- 预期结果：
  - A-2 那封：`mail_record_qa_rule` **零行**，`mail_record.matched_qa_rule_id` 为 `NULL`，但操作日志的 action 仍是 `SEND_MANUAL_COMPOSED_REPLY`。
  - 对照那封：`mail_record_qa_rule` 行数等于所选事实数，`ordinal` 从 0 递增，顺序等于运营选择顺序。
  - 两封都**不得**出现运营没选过的规则 id。
- 覆盖：I-3、I-4、IP-1

### A-5: 高风险内容仍要求逐字确认（回归）
- 前置条件：一封正文里含向专家索取护照/身份证/银行流水一类敏感材料措辞的草稿。
- 操作步骤：点「发送人工回复」，走完确认流程。
- 预期结果：先弹一次普通确认，**再**弹一次要求逐字输入「确认发送」的强确认框；不输入或输错则无法发送。与本次改动前**行为一致**。
- 覆盖：N-5、I-9

### A-6: 占位符 / 长度 / 退订三类硬闸保留（回归）
- 前置条件：准备三种情形各一封——(a) 正文里写一个不存在的变量占位符如 `${notARealKey}`；(b) 主题超过 255 字；(c) 收件人已在退订名单中。
- 操作步骤：分别点「发送人工回复」。
- 预期结果：三种都**直接失败**并给出明确原因，**不出现**二次确认弹窗、不可强行发送。(c) 的提示为「收件人已退订，禁止外发：<邮箱>」。
- 覆盖：N-4（需求方 2026-08-21 拍板：C/D/E 保留）

### A-7: 预检与发送口径一致
- 前置条件：同 A-1 的那封信。
- 操作步骤：先点面板上的「复验/预检」按钮，读橙色提示；再点「发送人工回复」。
- 预期结果：预检显示「依据已变化或不可用，请重新生成草稿或重新选择事实。」这类**警告**（不阻断）；发送也是**可确认**（不阻断）。两者结论方向一致，**不得**出现"预检说能发、发送直接报错"。
- 覆盖：IP-2

### A-8: 事实全部匹配时无多余弹窗（回归）
- 前置条件：一封来信，所绑事实的关键词与来信问句**确实匹配**，正文无高风险措辞。
- 操作步骤：点「发送人工回复」。
- 预期结果：**不弹任何确认框**，直接发送成功。（确认新增的三个码不会误报。）
- 覆盖：N-5、需求描述 outcome 1 的反面边界
