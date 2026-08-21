# 03 · 工作台新增处理方式「按事实原文回答」

> 基线：`main` @ `0949fa9`（2026-08-21）
> 与 `04-manual-send-fact-gate-downgrade.md` **无代码耦合**，可并行执行。

## 需求描述

### Observable outcome
1. 可信化回复台的「处理方式」下拉在**已绑定/已认可事实**的条目上多出一项 **「按事实原文回答」**；选中后点生成，条目答案是所选事实的 `answerBody` **逐字原文**，按运营在事实 chips 上看到的顺序、每条事实一段（`\n\n` 分隔），**不调用 LLM**。
2. 该处理方式下「AI 调整要求」输入框置灰不可编辑，标签改为明确说明"本处理方式不调用 AI"。
3. 生成结果可正常锁定、参与服务端整合，整合后的正文里每条事实各自成段。

### What must NOT change
- N-1 现有 6 种处理方式（`ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` / `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` / `ANSWER_FROM_OPERATOR_INPUT` / `ACKNOWLEDGE_PENDING` / `OMIT`）的生成、锁定、整合行为逐字不变。
- N-2 `QaFactSelectionService` 的任何行为（`select` / `selectForWorkbench` / `buildRequestFact`）不变——本计划不碰取证。
- N-3 自动回复链路（`GroundedAutoReplyDecisionService` → `AiReplyDraftService.generate()`）逐字不变。
- N-4 `AiReplyPointByPointComposer.composeLockedItems` 不变（K-locked-item-assembly-list-not-set：分段属于 `answerText` 本身，composer 不排版）。
- N-5 `CLAIM_PARAGRAPH_SEPARATOR` 的三处生产引用点语义不变。
- N-6 既有 `versionId` 的哈希输入结构不变（新 handling 只是 `handling` 字段的一个新取值）。

### Out of scope
- 不改 `AiReplyGroundedContentPlanner.buildPlan()` 的 claim 粒度（`general.answer` 仍是单 claim）——见 [[workbench-reply-no-paragraphs]] 的"方案 A：按事实拆 claim"，本计划用另一条路（verbatim）达成分段，不动 claim 模型。
- 不改 `QaRequestExtractor` 的请求切分（方案 3）。
- 不给 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` 增加分段能力。
- 不做「事实原文 + AI 润色」的混合模式。
- 不改 `qa_rule.replyBody` 相关的任何链路。

---

## 关键不变量

### I-1: verbatim 正文只取 `answerBody`，绝不回退 `replyBody`
- Rule: 新 handling 组装正文时，每一段**只能**是 `QaRule.answerBody`。`replyBody` 在本链路中**禁止出现**；`displayName` 只能做 UI 标签，不得进入正文。
- Applies to: `AiReplyDraftService.generateItem`（`:413-491`）的新分支（唯一正文产出点）；`TrustReplyWorkbenchService.validateLockedItem` 的服务端重算校验。
- Violation consequence: 把废弃的邮件体裁正文（含旧签名/旧口径）重新变成可外发内容；`displayName` 里的数字/URL 会替 `answerBody` 背书。
- 来源: K-grounded-answerbody-no-legacy-fallback、K-answerbody-source-exclusive、K-qa-fact-body-required-no-legacy-fallback

### I-2: 段落单位 = 一条事实，分隔符必须是 `CLAIM_PARAGRAPH_SEPARATOR`
- Rule: `answerText = item.factRuleIds.map { rule(it).answerBody.trim() }.joinToString(AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR)`。顺序**逐字等于** `item.factRuleIds` 的顺序（即运营在 chips 上拖出来的顺序），禁止排序、去重、截断。
- Applies to: `AiReplyDraftService.generateItem`（`:413-491`）新分支；`TrustReplyWorkbenchService.validateLockedItem` 新分支的重算。
- Violation consequence: 运营调整 chips 顺序不生效（K-composed-reply-order-contract 同类）；写字面量 `"\n\n"` 会与三处生产引用点漂移。
- 来源: original（分隔符常量来源 K-locked-answer-paragraphs-at-version-time）

### I-3: `claims` 恒空，走既有的 canonicalize 旁路
- Rule: 新 handling 的 `AiReplyItemClaim` 列表**恒为空**；`materializeVersion` 必须把它加入 `TrustReplyWorkbenchService.kt:1517-1524` 的旁路集合，且 `normalizedAnswer` 走 `answerText.trim()`（`:1529-1534` 的 `when` 新增一行），**不得**落进 `else -> canonicalizeClaims(...)` 分支。
- Applies to: `TrustReplyWorkbenchService.materializeVersion`（`:1500-1560`）。
- Violation consequence: `canonicalizeClaims`（`:1459-1487`）会按 `expected`（SUPPORTED 意图或单条 `general.answer`）比对，verbatim 的空 claims 必然 `byKey.keys != expected` → 422 `TRUST_REPLY_CLAIMS_INVALID`；即使绕过，`answerText != canonical.joinToString(SEP)` 也会 422 `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`。
- 来源: original（旁路集合事实来自 `TrustReplyWorkbenchService.kt:1517-1533` 实读）

### I-4: 锁定校验必须服务端重算，不信任客户端提交的 `answerText`
- Rule: `validateLockedItem` 的新分支必须用 `item.factRuleIds` **重新从库里读 `answerBody` 拼一遍**，与 `locked.answerText` 逐字比对；不等即 422 `TRUST_REPLY_LOCKED_ITEM_INVALID`。同时要求 `locked.claims.isEmpty()` 且 `locked.generationKind == SAFE_TEMPLATE`。
- Applies to: `TrustReplyWorkbenchService.validateLockedItem`（`:1373-1433` 的 `when (locked.handling)`）。
- Violation consequence: 前端可提交任意正文并声称"这是事实原文"，绕过全部内容校验直达外发——这是本计划唯一新增的信任面，必须闭合。
- 来源: original

### I-5: 允许集只在「有事实可引用」的条目上放开
- Rule: `allowedHandlings(item)`（`TrustReplyWorkbenchService.kt:2086-2111`，全仓唯一一张表）中，新 handling **只加入 `item.factRuleIds.isNotEmpty()` 的条目**：`GROUNDED` 分支无条件加入；`PARTIAL` 两个分支均加入；`UNSUPPORTED` 分支**不得**加入（该状态下 `factRuleIds` 恒空，见 K-fact-matrix-two-semantics-in-one-field 的推导闭合）。
- Applies to: `allowedHandlings`（生成前置 `requireAllowedHandlingForApi:1127`、锁定校验 `:1385`、下发 `:1972` 三处共用）。
- Violation consequence: `UNSUPPORTED` 条目上选中会产出空正文 → `require(orderedAnswers.all { it.isNotBlank() })`（`AiReplyPointByPointComposer.kt:35`）抛 500。
- 来源: K-operator-directed-authorization-seam（三份副本已收口为一张表，不得新造第二份）

### I-6: 不复用 grounded 分支的 `detectActions` 无条件禁令
- Rule: 新 handling 的锁定校验**不得**调用 `AiReplyActionPolicy.detectActions(...).isNotEmpty() → 判废`（即 `:1416-1431` 那段）。理由：正文是运营在 QA 事实库里维护的受审内容，不是模型输出；对外发动作的把关由发送期 `PendingMailOperationService.collectSafetyFindings`（`:709-813`）统一承担，且那条路是可二次确认的。
- Applies to: `validateLockedItem` 新分支。
- Violation consequence: 任何含"welcome to share your CV"之类合规句式的事实会让 verbatim 条目永远锁不上，且报错码与原因无关（`TRUST_REPLY_CLAIM_INVALID`）。
- 来源: K-operator-directed-authorization-seam（G1/G2 分离），原创部分为"受审事实不等同模型输出"

### I-7: 同一封信里两个条目引用完全相同的事实集会被判重复
- Rule: 这是**既有行为**，本计划不改：`validateNoDuplicateClaims`（`TrustReplyWorkbenchService.kt:1340-1370`）对非 OMIT 版本按 `answerText` 归一化查重，命中即 422 `TRUST_REPLY_DUPLICATE_CLAIM`。verbatim 使得两条摘要绑同一组事实时 `answerText` 必然逐字相同，因此**必然**触发。
- Applies to: `assemble`（`:1276` 调用点）。
- Violation consequence: 若误以为是缺陷去放宽查重，会让整封信出现两段一模一样的正文。正确处理是让运营把其中一条改成 `OMIT` 或调整事实绑定；错误码文案必须能说清这件事。
- 来源: original（`validateNoDuplicateClaims` 实读）

### I-8: `operatorInstruction` 在新 handling 下不参与生成，但仍进版本身份
- Rule: 新 handling **不得**被加入 `TrustReplyWorkbenchController` / `TrustReplyWorkbenchService.generateItemAdjustment`（`:1131-1140`）的"必须非空回答说明"集合；`materializeVersion` 对 `operatorInstruction` 的哈希处理（`:1535-1545`）保持不变（前端会传空串，哈希稳定）。
- Applies to: `TrustReplyWorkbenchService.generateItemAdjustment`；前端 `OPERATOR_INSTRUCTION_HANDLINGS`（`trust-reply-workbench.js:34-37`）**不得**加入新值——该常量有 **3 个消费点**（`:1108` 生成前置校验「说明为空则拦下」、`:2140`、`:2222` 渲染判据），三处都靠集合成员判定，加入即等于强制要求填说明。
- Violation consequence: 加进去会让 verbatim 强制要求填说明（422 `TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID`），与"不调 AI"自相矛盾。
- 来源: original

---

## 样式契约

> 本计划触及 `src/main/resources/static/trust-reply-workbench.js`。**零新增 CSS**：全部复用既有规则。

### S-1: 处理方式下拉新增选项
- 复用：无需改样式。选项由 `renderRequest`（`trust-reply-workbench.js:2223`）从 `request.availableHandlings` × `HANDLING_LABELS` 数据驱动生成，`<select data-role="handling">` 的样式在 `styles.css:7436-7457`（`.trust-reply-field select`）。
- 新增：`HANDLING_LABELS`（`trust-reply-workbench.js:19-26`）**新增且仅新增**一行：

```js
        ANSWER_FACTS_VERBATIM: "按事实原文回答",
```

  插入位置：`ANSWER_SUPPORTED_PART` 之后、`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 之前（与 `allowedHandlings` 的排列顺序一致）。
- DOM 结构：不变。
- 禁止项：inline style；新增 class；修改 `.trust-reply-field` 任何规则块。

### S-2: 「AI 调整要求」输入框在 verbatim 下置灰
- 复用：`styles.css:7474-7476` 已有 `.trust-reply-field select:disabled, .trust-reply-field textarea:disabled, .trust-reply-version-select:disabled { cursor: not-allowed; opacity: 0.62; background: var(--surface); }` —— **直接复用，禁止新写"灰化"样式**。
- 新增：`renderRequest`（`trust-reply-workbench.js:2218-2229`）内，在既有 `needsOperatorInstruction` 常量旁新增一个常量并在两处使用：

```js
            const instructionDisabled = request.pending || request.draftHandling === "ANSWER_FACTS_VERBATIM";
```

  `instructionLabel` 三元改为：

```js
            const instructionLabel = request.draftHandling === "ANSWER_FACTS_VERBATIM"
                ? "AI 调整要求（本处理方式不调用 AI，此项不生效）"
                : (needsOperatorInstruction ? "回答说明（AI 将仅据此生成）" : "AI 调整要求（仅调整表达，可留空）");
```

  `<textarea data-role="instruction">` 的 `disabled` 判据由 `request.pending` 改为 `instructionDisabled`（模板串中 `${request.pending ? " disabled" : ""}` → `${instructionDisabled ? " disabled" : ""}`，**只改 `data-role="instruction"` 这一个 textarea**，同一行内 `data-role="handling"` 与 `data-role="version"` 两个 `<select>` 的 `request.pending` 判据保持不变）。
- DOM 结构：`<label class="trust-reply-field">…<textarea data-role="instruction" …></textarea></label>` 骨架不变，textarea 始终留在 DOM 内，**禁止移除或 `hidden`**。
- 禁止项：新增 class；用 `style="opacity:…"` 之类 inline 灰化；把 textarea 移出 DOM。

### S-3: 既有 class 使用点核查
- 本契约不修改任何既有 class 的规则块，因此无需列出使用点全集。
- 被复用的 `:disabled` 规则块（`styles.css:7474-7476`）为**就地复用，零修改**。

---

## 现状审计

### 存储 A：`qa_rule` 表（正文来源）
- Schema（`src/main/kotlin/.../qa/domain/QaRule.kt:38-58`）：`replyBody: String`（非空，旧邮件体裁正文）、`answerBody: String = ""`（事实正文，新权威字段）、`displayName: String?`、`enabled: Boolean`、`replyPolicy: String`。
- 本计划的**读路径**：新增 1 条——`AiReplyDraftService.generateItem` 新分支按 `item.factRuleIds` 逐个 `qaRuleRepository.findById`，只取 `answerBody`。
- 本计划的**写路径**：无。
- 既有相关读路径（不改）：
  1. `QaFactSelectionService.validateExplicitSelection:356-370` — 读 `enabled` / `replyPolicy` / `answerBody`
  2. `AiReplyDraftService.buildGroundedUserContent`（`APPROVED FACTS` 段，`:2400+`）— 读 `answerBody`
  3. `AiReplyHighRiskClaimValidator.resolveSourceText` — 读 `answerBody`
  4. `QaReplyComposer.formatSection`（`qa/service/QaReplyComposer.kt:42`）— 读 `replyBody`，**唯一调用者是 `QaMatchService:83`**（`grep -rn "QaReplyComposer\." src/main/kotlin/` → 恰 2 行，均在 `QaMatchService.kt:82/83`）。本计划**不复用**它，正是因为它读 `replyBody`（I-1）。
  5. `trust-reply-workbench.js:1874` — chips 的 `title` 提示已在读 `rule.answerBody`，说明 `answerBody` 已下发到前端。
- Interaction point IP-1：`answerBody` 被运营在 QA 管理页随时可改 → verbatim 版本的 `answerText` 与库内容可能漂移。既有机制已覆盖：`requestEvidenceVersion` / `evidenceSetVersion` 含 `answerBodySha256`（`AiReplyEvidenceSnapshot`，`AiReplyDraftService.kt:285-291`），改动后锁定项走 `TRUST_REPLY_EVIDENCE_STALE`。I-4 的服务端重算是第二道。

### 存储 B：`TrustReplyItemVersion`（工作台版本，走 `TrustReplyWorkbenchStateStore` 持久化）
- 写路径全集（`grep -n "materializeVersion(" src/main/kotlin/.../TrustReplyWorkbenchService.kt` → **5 个调用点 + 1 处定义 `:1498`**，全部在本文件内）：
  1. `:882` — 快照回放路径（`restoreSavedStateWithFrame`），先 `validateLockedItem`（`:875-881`）再重算版本。**新 handling 经此回放**，因此 T1.5 的校验分支必须先于本路径成立。
  2. `:1146` — `generateItemAdjustment` 的 `OMIT` 早返回分支
  3. `:1190` — `generateItemAdjustment` 的生成分支（**新 handling 从此进入**）
  4. `:1256` — `assemble` 的逐项重算分支
  5. `:1867` — 整封聚合路径，`handling` 由 `when (item.status)`（`:1856-1860`）从 status 推导，只可能产出 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART`（`UNSUPPORTED` 直接 `return@mapNotNull null`）。**新 handling 永不经此产生 → 零改动核查项。**
- 读路径：`assemble:1276`（`orderedAnswers`）、`validateNoDuplicateClaims:1340`、`validateLockedItem:1373`、`archiveLiveUnsupportedAnswers`（`PendingMailOperationService:570-643`，只认 `ANSWER_FROM_OPERATOR_INPUT`，verbatim 不受影响）。
- Interaction point IP-2：`:1190` 写入 → `:1256` / `:882` 重算 → `versionId` 必须逐字相同，否则 422 `TRUST_REPLY_ITEM_VERSION_INVALID`（`:1268`）。因此新 handling 在 `materializeVersion` 里的 `normalizedAnswer` / `normalizedClaims` 计算必须**只有一份实现**（I-3）。

### `TrustReplyItemHandling` 全部消费点（`grep -rn "TrustReplyItemHandling\." src/main/kotlin/` → 65 行 / 6 文件）
| 文件 | 行数 | 是否需改 |
|---|---|---|
| `TrustReplyWorkbenchService.kt` | 46 | **是**（见实现方案 T1） |
| `AiReplyDraftService.kt` | 12 | **是**（T2） |
| `PendingMailOperationService.kt` | 3（`:582` / `:633` / `:637`） | 否——三处均只判 `ANSWER_FROM_OPERATOR_INPUT`（无据回答归档），verbatim 不属于 |
| `AiTrainingEvaluationService.kt` | 2（`:89` 判 `ANSWER_FROM_OPERATOR_INPUT`；`:146` `values().associate{}`） | 否——`:146` 自动包含新值 |
| `AiTrainingController.kt:331` | 1 | 否——`valueOf` 通用解析 |
| `TrustReplyWorkbenchController.kt:230` | 1 | 否——`valueOf` 通用解析 |

**穷尽性提示点**：`validateLockedItem` 的 `when (locked.handling)`（`TrustReplyWorkbenchService.kt:1386-1432`）**无 `else` 分支**。本项目 Kotlin 版本为 **1.9.25**（`pom.xml:21`），新增枚举值后该 `when` 不再穷尽，编译期必然给出提示（错误或警告取决于编译器配置，本计划**不依赖**具体级别）。**无论哪种，T1.5 必须显式补分支**，实现者不得把"编译通过"当作已覆盖的证据。

### 前端样式盘点
- 可复用 class：
  - `.trust-reply-field` — `styles.css:7426-7434`（label 容器）
  - `.trust-reply-field select` / `textarea` — `styles.css:7436-7466`
  - `.trust-reply-field textarea:disabled` — `styles.css:7474-7476`（`cursor: not-allowed; opacity: 0.62; background: var(--surface);`）
  - `.trust-reply-item-controls` — `styles.css:7419-7424`（`grid-template-columns: minmax(150px, 0.4fr) minmax(0, 1.6fr)`）
  - `.trust-reply-fact-chip` / `.trust-reply-fact-grip` — chips 与拖拽把手，本计划不改
- 设计基准 token（本计划涉及的）：`--surface`（禁用态底色）、`--panel-bg`、`--line`、`--primary`、`--text-muted`、`--radius-md`、`--shadow-sm`；`.trust-reply-field` 字号 `11px` / 字重 `600`；textarea `min-height: 52px`、`padding: 9px 10px`、`line-height: 1.5`。
- DOM 结构约定：处理方式下拉与版本下拉在 `.trust-reply-item-controls` 网格内；回答说明 textarea 在其后独立一个 `.trust-reply-field` label；三者均由 `renderRequest`（`:2218-2230`）单函数生成模板串。
- 改动前基线（`trust-reply-workbench.js:2228`）：

```js
            const instructionLabel = needsOperatorInstruction ? "回答说明（AI 将仅据此生成）" : "AI 调整要求（仅调整表达，可留空）";
```

  以及 `:2229` 模板串中的 `<label class="trust-reply-field">${instructionLabel}<textarea data-role="instruction" data-request-key="${escapeText(request.requestKey)}" maxlength="500"${request.pending ? " disabled" : ""}>${escapeText(request.instruction)}</textarea></label>`。

---

## 实现方案

### 阶段 T1 — 后端：枚举、允许集、版本物化、锁定校验（`TrustReplyWorkbenchService.kt`）

- **T1.1** `enum class TrustReplyItemHandling`（`:28-37`）新增 `ANSWER_FACTS_VERBATIM`，位置在 `ANSWER_SUPPORTED_PART` 之后。*遵守 I-5*
- **T1.2** `allowedHandlings(item)`（`:2086-2111`）：`GROUNDED` 分支、`PARTIAL` 的两个分支各新增 `ANSWER_FACTS_VERBATIM`；`UNSUPPORTED` 分支不加。三个分支均以 `item.factRuleIds.isNotEmpty()` 为前置（`GROUNDED`/`PARTIAL` 状态下该条件天然成立，仍需显式守卫，防止未来取证口径变化）。*遵守 I-5*
- **T1.3** `generateItemAdjustment`（`:1131-1140`）：确认新 handling **不**进入"必须非空回答说明"的两个 `if` 判据。**本步骤为零改动的显式核查项**，须在实现记录中写明已核查。*遵守 I-8*
- **T1.4** `materializeVersion`（`:1517-1534`）：`normalizedClaims` 的旁路集合（`:1517-1524`）新增 `handling == TrustReplyItemHandling.ANSWER_FACTS_VERBATIM`；`normalizedAnswer` 的 `when`（`:1529-1534`）新增 `TrustReplyItemHandling.ANSWER_FACTS_VERBATIM -> answerText.trim()`。*遵守 I-3*
- **T1.5** `validateLockedItem` 的 `when (locked.handling)`（`:1386-1432`）新增独立分支：

  ```
  TrustReplyItemHandling.ANSWER_FACTS_VERBATIM -> {
      if (locked.answerText.isBlank() ||
          locked.claims.isNotEmpty() ||
          locked.generationKind != TrustReplyItemGenerationKind.SAFE_TEMPLATE ||
          locked.answerText != aiReplyDraftService.composeVerbatimFactAnswer(item)
      ) throw TrustReplyWorkbenchException(UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
  }
  ```
  *遵守 I-1 / I-2 / I-4 / I-6*（**不**加 `detectActions` 判废）
- **T1.6** `assemble`（`:1246-1252`）与 `restoreSavedState` 路径（`:899-908`）的 `groundedSections` 收集条件保持只认 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART`——verbatim 的 claims 恒空，不进 `validateGroundedTrustBoundary`。**零改动的显式核查项**。*遵守 I-3*

### 阶段 T2 — 后端：verbatim 正文产出（`AiReplyDraftService.kt`）

- **T2.1** 新增 `internal fun composeVerbatimFactAnswer(item: RequestFactItem): String`，实现即 I-2 的公式；对 `findById` 取不到、`enabled == false`、`answerBody.isBlank()` 的规则**跳过该条**，若最终结果为空串则由调用方判失败。该函数是 verbatim 正文的**唯一产出点**，供 T1.5 与 T2.2 共用。*遵守 I-1 / I-2*
- **T2.2** `generateItem`（`:413-491`）在 `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 分支（`:476-490`）之后、`val token = cancellationToken ?: AiReplyCancellationToken()`（`:492`）之前新增分支：

  ```
  if (handling == TrustReplyItemHandling.ANSWER_FACTS_VERBATIM) {
      val text = composeVerbatimFactAnswer(requestFact)
      return AiReplyItemGenerationResult(
          itemAnswer = if (text.isBlank()) null else AiReplyItemAnswer(
              requestIndex = requestFact.index,
              requestText = requestFact.requestText,
              status = requestFact.status,
              answerText = text,
              claims = emptyList()
          ),
          handling = handling,
          generationKind = if (text.isBlank()) null else TrustReplyItemGenerationKind.SAFE_TEMPLATE,
          generationState = AiReplyGenerationState.LLM_USED,
          usedLlm = false,
          lockable = text.isNotBlank()
      )
  }
  ```
  形态照抄 `:432-441` 的 `OMIT` 分支（`generationState = LLM_USED` + `usedLlm = false` 是既有先例）与 `:1002-1020` 的 `safeAcknowledgementResult`。`lockable = false` 时由 `TrustReplyWorkbenchService:1185` 统一抛 `TRUST_REPLY_ITEM_GENERATION_FAILED`。*遵守 I-1 / I-2 / I-3*
- **T2.3** `validateItemHandling`（`:1023-1025`）不改——它已委托给 `TrustReplyWorkbenchService.requireAllowedHandling`（唯一一张表）。**零改动的显式核查项**。*遵守 I-5*
- **T2.4** 新分支**必须**位于 `rejectNonEnglishItemAnswer` 包装之外：事实正文由运营维护，可能合法含非拉丁字符，不适用英文校验（`:982` 的 `containsNonLatinLetter` 只对 `AI_GENERATED` 生效，verbatim 是 `SAFE_TEMPLATE`，天然不受影响——仍须显式核查不要误加包装）。

### 阶段 T3 — 前端（`trust-reply-workbench.js`）

- **T3.1** 按 **S-1** 新增 `HANDLING_LABELS` 一行。
- **T3.2** 按 **S-2** 改 `renderRequest` 的 `instructionLabel` 与 instruction textarea 的 `disabled` 判据。
- **T3.3** 显式核查 `OPERATOR_INSTRUCTION_HANDLINGS`（`:34-37`）**未**加入新值，并逐个确认其 3 个消费点（`:1108` / `:2140` / `:2222`）行为不变。*遵守 I-8*
- **T3.4** 显式核查 `WORKBENCH_ERROR_TEXT`（`:44-60`）已含 `TRUST_REPLY_LOCKED_ITEM_INVALID` 与 `TRUST_REPLY_DUPLICATE_CLAIM`。若 `TRUST_REPLY_DUPLICATE_CLAIM` 缺失则补一行文案：`"多条摘要引用了完全相同的事实，正文会重复。请把其中一条改为「省略此项」或调整事实绑定。"` *遵守 I-7*

### 阶段 T4 — 测试

- **T4.1** `TrustReplyWorkbenchItemFlowTest.kt` 新增 4 个用例：verbatim 生成产出 N 段（N = 事实数）且顺序等于 `factRuleIds`；篡改 `answerText` 后 `assemble` 抛 `TRUST_REPLY_LOCKED_ITEM_INVALID`；`claims` 非空时抛同码；两条摘要绑同一事实集时抛 `TRUST_REPLY_DUPLICATE_CLAIM`（I-7 是既有行为，本用例是行为固化）。
- **T4.2** `AiReplyDraftServiceTest.kt` 新增 3 个用例：`composeVerbatimFactAnswer` 用 `answerBody` 而非 `replyBody`（构造 `replyBody`/`answerBody` 不同的规则，断言产物不含 `replyBody` 文本）；禁用/空 `answerBody` 的规则被跳过；全部跳过时 `generateItem` 返回 `lockable = false`。
- **T4.3** `src/test/js/trustReplyWorkbench.test.js` 新增 2 个用例：`HANDLING_LABELS` 含 `ANSWER_FACTS_VERBATIM: "按事实原文回答"`；`OPERATOR_INSTRUCTION_HANDLINGS` **不**含该值。
- **T4.4** 现有用例回归核查：`TrustReplyWorkbenchItemFlowTest:312` `bound fact adopted on partial item supports exactly one general answer claim`（断言 `claims.size == 1`）**必须继续通过**——它测的是 `ANSWER_SUPPORTED_PART`，与新 handling 无关（N-1）。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | T1.1/T1.2/T1.4/T1.5（T1.3/T1.6 为零改动核查） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | T2.1/T2.2（T2.3/T2.4 为零改动核查） |
| 3 | `src/main/resources/static/trust-reply-workbench.js` | T3.1/T3.2（T3.3 零改动核查，T3.4 条件性一行） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | T4.1 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | T4.2 |
| 6 | `src/test/js/trustReplyWorkbench.test.js` | T4.3 |

文件数 **6** ≤ 10。子系统数 **2**（后端工作台 / 前端工作台 JS）≤ 2。

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。JS 用例用系统 `node`（实测 v22.23.2），无需 JAVA_HOME 前缀。

```bash
# 全量测试（回归门禁，含 exec-maven-plugin 绑定的 node --test）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest

# 单个测试方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest#methodName

# 前端 JS 用例（权威门禁，可单跑）
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/trust-reply-workbench.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- Maven：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`
- node：退出码 0，输出含 `# fail 0`
- `node --check`：退出码 0，无输出

来源：Maven 命令取自项目根 `CLAUDE.md` 的「Commands」章节与项目元信息 `test_command` / `build_command`；node 命令取自 `pom.xml:186-232` 的 `exec-maven-plugin` 三个 execution（K-js-tests-run-via-exec-plugin，实测通过）。
**注意**：`verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，**不可**用作本计划的前端回归门禁（K-js-test-invocation-surface）。

---

## 验收标准

- **I-1**：`grep -n "replyBody" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 的结果中，`composeVerbatimFactAnswer` 函数体内**零命中**。T4.2 第 1 个用例（`replyBody` 与 `answerBody` 内容不同，断言产物不含 `replyBody` 文本）通过。
- **I-2**：`grep -n '"\\\\n\\\\n"' src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 除 `CLAIM_PARAGRAPH_SEPARATOR` 定义行（`:2572`）外零新增字面量。T4.1 第 1 个用例断言 `answerText.split("\n\n")` 的元素数等于事实数、且顺序等于 `factRuleIds` 顺序。
- **I-3**：`TrustReplyWorkbenchService.kt:1517-1524` 的旁路集合含 `ANSWER_FACTS_VERBATIM`；`:1529-1534` 的 `when` 含对应分支。T4.1 全部用例不出现 `TRUST_REPLY_CLAIMS_INVALID` / `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`。
- **I-4**：T4.1 第 2、3 个用例通过（篡改正文 / 非空 claims → `TRUST_REPLY_LOCKED_ITEM_INVALID`）。
- **I-5**：`allowedHandlings` 的 `UNSUPPORTED` 分支 `grep` 结果不含 `ANSWER_FACTS_VERBATIM`。新增单测断言 `allowedHandlings(unsupportedItem)` 不含该值、`allowedHandlings(groundedItem)` 含该值。
- **I-6**：`validateLockedItem` 的 `ANSWER_FACTS_VERBATIM` 分支内 `grep -c "detectActions"` 为 0。
- **I-7**：T4.1 第 4 个用例通过（`TRUST_REPLY_DUPLICATE_CLAIM`）。
- **I-8**：`grep -n "ANSWER_FACTS_VERBATIM" src/main/resources/static/trust-reply-workbench.js` 的命中行中，不含 `OPERATOR_INSTRUCTION_HANDLINGS` 数组内的行；`TrustReplyWorkbenchService.kt:1131-1140` 两个 `if` 判据不含新值。T4.3 第 2 个用例通过。
- **S-1**：`git diff src/main/resources/static/trust-reply-workbench.js` 中 `HANDLING_LABELS` 块只新增 1 行，逐字等于契约给出的代码。
- **S-2**：diff 中无新增 CSS（`git diff --stat src/main/resources/static/styles.css` 为空）；`renderRequest` 内新增的 `instructionDisabled` 与 `instructionLabel` 逐字等于契约代码块；`data-role="handling"` 与 `data-role="version"` 两个 select 的 `request.pending` 判据未被改动。
- **S-3**：`git diff src/main/resources/static/styles.css` 输出为空。
- **N-1 回归**：执行「验证命令」节的**全量测试命令**通过，且 `TrustReplyWorkbenchItemFlowTest:312` 用例仍绿（T4.4）。
- **N-2 回归**：`git diff --stat src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` 输出为空。
- **IP-2 跨路径**：T4.1 第 1 个用例必须走完「生成 → 锁定 → assemble」全流程且不出现 `TRUST_REPLY_ITEM_VERSION_INVALID`。

---

## 人工验收清单

### A-1: verbatim 生成按事实分段
- 前置条件：收发件箱里存在一封已解析出至少 1 条摘要的来信；该摘要上已绑定 **2 条**事实（QA 管理页确认这两条的「事实正文/answerBody」非空且内容明显不同，例如「项目总览」与「薪资与资金支持」）。
- 操作步骤：
  1. 打开该来信 → 可信化回复台 → 展开该条摘要。
  2. 「处理方式」下拉选择 **「按事实原文回答」**。
  3. 点击生成。
- 预期结果：答案区出现**恰好 2 段**文本，段间有一个空行；第 1 段逐字等于第 1 个事实 chip 的 `answerBody`（把鼠标悬停在 chip 上看 title 提示对照），第 2 段逐字等于第 2 个 chip 的。版本下拉显示 **「版本 N · 安全模板」**（不是「AI 生成」）。生成过程**无** AI 进度条/流式输出。
- 覆盖：需求描述 outcome 1、I-1、I-2

### A-2: 事实顺序即段落顺序
- 前置条件：同 A-1，已生成过一次。
- 操作步骤：
  1. 用 chip 上的 `⋮⋮` 把手把第 2 条事实拖到第 1 位（或用左右方向键）。
  2. 重新点击生成。
- 预期结果：两段内容互换位置，其余逐字不变。
- 覆盖：I-2

### A-3: AI 调整要求置灰
- 前置条件：同 A-1。
- 操作步骤：处理方式在「按事实原文回答」与「回答有依据部分」之间来回切换，观察下方输入框。
- 预期结果：选「按事实原文回答」时，标签显示 **「AI 调整要求（本处理方式不调用 AI，此项不生效）」**，输入框变灰、鼠标悬停显示禁止光标、无法输入；切回「回答有依据部分」时标签恢复 **「AI 调整要求（仅调整表达，可留空）」** 且可输入。输入框在两种状态下**位置和大小不变**（不跳动、不消失）。
- 覆盖：需求描述 outcome 2、S-2、I-8

### A-4: 锁定与整合后正文分段
- 前置条件：A-1 已生成成功。
- 操作步骤：
  1. 点击采用/锁定该条。
  2. 其余摘要各自处理完（可选「省略此项」）。
  3. 点击「服务端整合」。
- 预期结果：整合出的正文里，该条对应的两段各自独立成段（段间空行），且与称呼、开场白、结束语之间也各有空行。
- 覆盖：需求描述 outcome 3、IP-2

### A-5: 篡改正文被拒（安全回归）
- 前置条件：A-4 已完成锁定但未发送。
- 操作步骤：在 QA 管理页把其中一条事实的「事实正文」改动一个字并保存，然后回到回复台点「服务端整合」。
- 预期结果：整合失败，提示 **「本条的事实已变化，请刷新工作台后重试。」**（`TRUST_REPLY_EVIDENCE_STALE`）或 **「已锁定的回答与当前状态不一致，请重新生成本条。」**（`TRUST_REPLY_LOCKED_ITEM_INVALID`）。**不得**出现整合成功并把旧正文发出去。
- 覆盖：I-4、IP-1

### A-6: 无事实的摘要看不到这个选项（回归）
- 前置条件：存在一条**未绑定任何事实**、状态显示 `UNSUPPORTED · 无依据` 的摘要。
- 操作步骤：展开该摘要，打开「处理方式」下拉。
- 预期结果：下拉里**没有**「按事实原文回答」；只有「按回答说明生成」「确认待补充」「省略此项」三项。
- 覆盖：I-5、N-1

### A-7: 既有 6 种处理方式未受影响（回归）
- 前置条件：一条 `GROUNDED · 依据充分` 的摘要，一条 `PARTIAL · 部分有据` 的摘要。
- 操作步骤：分别用「依据完整回答」和「回答有依据部分」各生成一次并锁定，然后整合。
- 预期结果：生成结果、版本标签「AI 生成」、整合成功——与本次改动前**行为一致**；`PARTIAL` 条目仍然是单段（本计划不改 claim 粒度）。
- 覆盖：N-1、N-3、Out of scope 第 1 条

### A-8: 两条摘要绑同一组事实的重复提示
- 前置条件：一封来信解析出 ≥2 条摘要，把**完全相同**的一组事实分别绑到两条摘要上（若被「同一条事实被多个摘要绑定」拦住，则本项标记为不适用并记录）。
- 操作步骤：两条都选「按事实原文回答」，生成、锁定，点整合。
- 预期结果：整合失败并给出可读中文提示，说明多条摘要正文重复、应把其中一条改为「省略此项」。**不得**出现整合成功且正文里两段一模一样。
- 覆盖：I-7、T3.4
