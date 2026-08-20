# 回复台「按回答说明生成」：运营回答说明作为动作授权来源（方案 C，全链路）

> 计划日期：2026-08-20
> 基线：`main` @ `08a25fe`（`manual-send-safety-confirm` 已合并于 `a21784e`），工作树干净（`git status --short` 仅 `?? .claude/`）。
> 本计划所有 `file:line` 均取自该基线实测；执行前仍须重新 grep 复核。

---

## 需求描述

### Observable outcome

1. 运营在回复台某个 `UNSUPPORTED` 条目选「按回答说明生成」，在「回答说明（AI 将仅据此生成）」里写明要向专家索取简历、或提议安排会议/通话时，AI 生成的英文正文**包含这些动作**。
2. 该条目能正常锁定并参与汇总（`assemble` 不再抛 `TRUST_REPLY_CLAIM_INVALID`）。
3. 该正文进入人工回复编辑器后，**预检面板不再对运营已授权的动作报风险**；点发送也不再因这些动作被拦。
4. 预检与发送对同一段文字给出**一致**结论。

当前行为（缺陷）：无论回答说明写什么，索取材料与提议会议两类动作都被静默删除，正文退化为 "We have received your details and will follow up with you regarding next steps." 之类的空泛套话；即使模型照写，也会在生成后校验与锁定校验两道闸被判废。

### What must NOT change

1. **G2 合规判定一字不改**：`AiReplyActionPolicy` 的 `SENSITIVE_MATERIAL`、`CV_PURPOSE`、`CV_OPTIONALITY`、`CV_ONLY_PATTERN`、`MATERIAL_REQUEST`、`MEETING_REQUEST`、`MATERIAL_PROCESS_DESCRIPTION`、`MEETING_PROCESS_DESCRIPTION`、`SENSITIVE_MATERIAL_CTA_PREFIX` 等**全部正则与全部判定分支**保持逐字不变。本计划对该文件只允许**新增**一个 `val`。
2. **其余四种 handling 零变化**：`ANSWER_WITH_EVIDENCE`、`ANSWER_SUPPORTED_PART`、`ACKNOWLEDGE_PENDING`、`OMIT` 的生成路径与锁定校验路径逐字不变。
3. **整封 `generate()` 路径零变化**：`AiReplyDraftService.generate()`（两个重载，`:800` 与 `:818`）及其 `deriveAllowed(inboundText, operatorInstruction, operatorTurns)` 调用（`:863-867`）不动。
4. **两级确认语义零变化**：`manual-send-safety-confirm` 建立的 `SafetyFinding` / `SafetySeverity` / `ManualSendSafetyBlockedException` / `safetyWarningConfirmed` / `strongConfirmationText` 全套判定（`PendingMailOperationService.kt:219-233`）不动。本计划只改变**喂给 `collectSafetyFindings` 的授权集合**，不改变它产出 findings 之后的任何处置。
5. **`SafetySeverity.STRONG` 成员不变**：仍然有且仅有 `CODE_ACTION_SENSITIVE_MATERIAL`（`PendingMailOperationService.kt:710-714`）。
6. **自动回复链路零变化**：`GroundedAutoReplyDecisionService` / `AutoMailReplyService` 的 fail-closed 行为不得获得任何旁路。
7. **无存储变更**：不新增表、字段、索引、迁移；`trust_reply_workbench_state` 的 payload 结构与 `TrustReplySavedStatePayload`（`TrustReplyWorkbenchService.kt:197-206`）不变。
8. **无前端变更**：不改 `src/main/resources/static/` 下任何文件。
9. **无 HTTP 契约变更**：`AiReplyPreflightRequest`、`PendingManualRichReplyRequest`、`AiReplyPreflightResponse`、`SafetyFindingResponse` 的字段不增不减。

### Out of scope（显式推迟）

1. **让 `deriveAllowed()` 的 `operatorInstruction` / `operatorTurns` 形参在逐条链路上真正生效**（即所谓「方案 B」）。已被实证否决，见 `## 现状审计` 证据 E-5。
2. **给运营加动作勾选框的显式授权 UI（方案 A）**。需求方已选 C。
3. **句子级授权**（把授权精确绑定到运营批准的那一句，而不是动作类型）。本计划做到「动作类型 × 运营已批准的答案」这一粒度（I-6），更细的粒度需要在正文里携带来源标记，属独立一刀。
4. **`AiReplyActionPolicy.findViolations()` 内 `if/else` 分支的等价冗余简化**（`manual-send-safety-confirm` 的 Out of scope 第 3 条已声明推迟）。
5. **纯人工撰写（未采用 AI 草稿）的邮件跑预检**（`manual-send-safety-confirm` Out of scope 第 1 条已声明推迟）。本计划不改变预检的触发条件。

---

## 关键不变量

### Invariant I-1: `ANSWER_FROM_OPERATOR_INPUT` 的授权集合是常量，不由来信推导
- Rule: 当 `handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT` 时，生成侧与锁定侧的 `allowedActions` 恒等于新增常量 `AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS`（= `setOf(REQUEST_MATERIALS, PROPOSE_MEETING)`），**这两处不得再调用 `deriveAllowed(...)`**。两侧必须引用**同一个常量**，不得各写一份字面量。其他 handling 的推导方式一字不变。
- Applies to:
  - `AiReplyDraftService.generateOperatorDirectedAnswer()` — `:660`
  - `TrustReplyWorkbenchService.validateLockedItem()` 的 `ANSWER_FROM_OPERATOR_INPUT` 分支 — `:1351`
- Violation consequence: 两侧集合不一致时，草稿能生成但锁不上（或反之），运营遇到"能预览不能汇总"，且错误码是无信息量的 `TRUST_REPLY_CLAIM_INVALID`。
- 来源: original

### Invariant I-2: 授权只放开 G1，G2 一寸不动
- Rule: 授权集合只影响「该动作是否被允许出现」这一层（G1）。以下判定必须继续按现状生效，且**不得**为让文案通过而放宽任何一条正则：
  - 敏感材料 CTA（护照/身份证/在职证明/银行流水等）——`findViolations()` 首段的 `detectSensitiveMaterial()` 本就不读 `allowed`（`AiReplyActionPolicy.kt:127-134`），放开授权不影响它，且其码 `CODE_ACTION_SENSITIVE_MATERIAL` 仍是唯一的 `STRONG`；
  - `CODE_ACTION_CV_PURPOSE_MISSING`（索要简历未说明目的）；
  - `CODE_ACTION_CV_OPTIONALITY_MISSING`（索要简历未表明自愿）。
  本计划对 `AiReplyActionPolicy.kt` **只允许新增一个 `val`**（I-1 的常量），不允许修改任何既有正则、既有函数或既有分支。
- Applies to: `AiReplyActionPolicy.kt` 全文件
- Violation consequence: 拆掉 G2 等于允许对外索要护照/身份证/银行流水，以及无目的无自愿的裸索要简历——后者正是本项目反复踩过的坑（见来源）。
- 来源: K-sensitive-material-cta-not-mention / K-sensitive-cta-compound-material-coverage / K-ai-reply-action-cta-variant-coverage

### Invariant I-3: 锁定侧删掉无条件 `detectActions`，且只删这一处
- Rule: `validateLockedItem()` 的 `ANSWER_FROM_OPERATOR_INPUT` 分支中，删除 `AiReplyActionPolicy.detectActions(locked.answerText).isNotEmpty() ||` 这半个条件（`:1352`），只保留 `findViolations(locked.answerText, OPERATOR_DIRECTED_ALLOWED_ACTIONS).isNotEmpty()`。
  **`ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` 分支（`:1358-1372`）中对 `claim.text` 的 `detectActions`（`:1366`）逐字保留**——grounded claim 正文永远不许含任何动作，动作只能走独立 actionText 通道。
- Applies to: `TrustReplyWorkbenchService.validateLockedItem()` `:1342-1357`
- Violation consequence: 保留则运营授权的文案永远锁不上（本缺陷最硬的一道闸，该处不看 `allowed`）；误删到 grounded 分支则 claim 里可藏 CTA，破坏 actionText 单通道契约。
- 来源: K-grounded-proposed-action-body-parity

### Invariant I-4: 合规句式由 prompt 保证，不由放宽正则保证
- Rule: operator-directed 的 system prompt 必须新增一条约束：当 answer basis 要求索取简历/材料时，生成句必须在**同一个句子内**同时包含「目的」与「自愿」表述（`AiReplyActionPolicy` 的句子切分单位是 `SENTENCE_SPLIT`（`:87`）= `(?<=[.!?。！？])\s+|\n+`，跨句不算）。`## 实现方案` 中给出的候选措辞，必须实测满足 `findViolations(text, OPERATOR_DIRECTED_ALLOWED_ACTIONS).isEmpty()`。
- Applies to: `AiReplyDraftService.kt:663-671` 的 system message content
- Violation consequence: 不加此约束，模型自由发挥的措辞会概率性命中 `CV_PURPOSE_MISSING`，被 `:707-708` 判 `invalid` → 返回 `itemAnswer = null` → 运营看到「生成失败」。**这比现状更糟**：现状至少还有一段（虽然错的）文案。
- 来源: original（实证见 `## 现状审计` 证据 E-6）

### Invariant I-5: 授权是「允许出现」，不是「应当出现」
- Rule: system prompt 必须保留并强化「只许复述 answer basis」的约束，明确禁止引入 answer basis 未提出的对外动作。授权集合的放开不得被模型理解为鼓励添加动作。
- Applies to: 同 I-4 的 system message content
- Violation consequence: 需求方已决定「一键预判自动填入的机器说明也算授权」（理由：目前不自动发送，发送前必有人工过目）。该决策之后，**拦住 AI 在运营未逐条阅读的条目里自行添加索要动作的，只剩本条 prompt 约束与人工过目两道**。若本条失效，批量预判产物可能夹带未经运营意图的索要材料句。
  **附加约束（写给未来）：若将来要开启自动发送（去掉人工过目），必须回到本条重新评估「机器说明也算授权」这一决策。**
- 来源: original（需求方 2026-08-20 决策）

### Invariant I-6: 发送/预检的授权粒度 = 运营已批准答案中**实际出现**的动作，且只由服务端推导
- Rule: `collectSafetyFindings()` 新增入参 `operatorAuthorizedActions: Set<AiReplyAction>`，其取值**只能**由服务端按下式推导，**不得**来自任何 HTTP 请求字段、不得由前端传入：

  ```
  operatorAuthorizedActions =
      lockedItems
        .filter { it.handling == ANSWER_FROM_OPERATOR_INPUT && it.operatorInstruction.isNotBlank() }
        .flatMap { AiReplyActionPolicy.detectActions(it.answerText) }
        .toSet()
  ```

  即：**只授权运营已经批准的那些答案里真实出现过的动作类型**，不是无条件授权 `OPERATOR_DIRECTED_ALLOWED_ACTIONS` 全集。没有 operator-directed 锁定条目时，该集合为空集，行为与改动前逐字一致。
- Applies to: `PendingMailOperationService.collectSafetyFindings()`（`:697-800`）及其两个调用点 `:219`（发送）与 `:991`（预检）；新增的 `TrustReplyWorkbenchService.operatorAuthorizedActions(...)`。
- Violation consequence:
  - 若改用全集无条件授权：运营只授权了「约会议」，正文里手打的索要简历句也会被放行。
  - 若接受客户端传入：直接调 API 即可自造授权，绕过全部 G1 门禁（违反 `manual-send-safety-confirm` I-3「服务端是放行权威」）。
- 来源: original / manual-send-safety-confirm I-3

### Invariant I-7: 授权只做并集加法，信任缺口仍在其后生效
- Rule: 授权集合以**并集**方式加进推导结果，且 `restrictForTrustState()` 必须在并集**之后**执行：

  ```kotlin
  val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
      AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()) + operatorAuthorizedActions,
      hasBlockingTrust
  )
  ```

  运营授权**不得**压过信任缺口限制：当 `hasBlockingTrustGap` 为真时，`restrictForTrustState` 仍会剥掉 `REQUEST_MATERIALS`（`AiReplyActionPolicy.kt:113-121`），此时索要简历句仍产出 `MATERIALS_NOT_ALLOWED` finding，由运营在二次确认框里知情放行。
- Applies to: `PendingMailOperationService.kt:788-792`
- Violation consequence: 若把并集放在 `restrictForTrustState` 之后，等于运营授权可以压过信任缺口——对方正在质疑「你们是不是中介 / 钱谁出」而我方答不上来时仍主动索要个人材料，正是诈骗邮件的形状。
- 来源: original（需求方 2026-08-20 决策 2）

### Invariant I-8: 预检与发送同函数、同语义，来源不同但必须等价
- Rule: 预检与发送继续共用同一个 `collectSafetyFindings()`（`manual-send-safety-confirm` I-5 不得被破坏）。两者的 `operatorAuthorizedActions` 来源不同，但推导公式必须是同一个（I-6）：
  - **发送侧**：取自本次请求已携带的 `trustReplyAssembly.lockedItems`（`sendManualRichReply` 形参 `:144`）。必须先校验 `source.sourceType == LIVE_INBOUND && source.sourceId == inboundProcessingId`，不匹配则授权为空集——照抄 `:573-576` 既有的同款身份守卫。
  - **预检侧**：`preflightEditedAiReply` 没有 assembly 入参，改由 `TrustReplyWorkbenchService` 用 `stateStore.load("LIVE_INBOUND", inboundProcessingId)` + `decodePayload` 读回持久化快照的 `lockedItems`。**必须显式判过期**（`stored.expiresAt.isAfter(now)`）——过期是惰性清理的，`load()` 可能读到过期行，见 `## 现状审计` → 数据存储 → 过期语义。无快照 / 已过期 / 解码失败三种情况一律返回空集。
- Applies to: `PendingMailOperationService.kt:219-226`、`:991-998`；新增的 `TrustReplyWorkbenchService.operatorAuthorizedActions(...)` 两个重载
- Violation consequence: 两侧授权不等价则预检与实发再次漂移，运营据错误预检调正文（[[K-preview-mirrors-pipeline]]）——这正是 `manual-send-safety-confirm` 刚消掉的缺陷，不得换个方向重造。
  读不到快照时返回空集是**有意的 fail-closed**：预检偏严（多报不漏报），运营在发送时仍可确认放行。
- 来源: K-preview-mirrors-pipeline / manual-send-safety-confirm I-5

### Invariant I-9: 变更文件清单之外零改动
- Rule: 本计划只允许修改 `## 变更文件清单` 中列出的 7 个文件。特别地：`app.js`、`UnmatchedInboundMailController.kt`、`GlobalExceptionHandler.kt`、`AiReplyGroundedContentPlanner.kt`、`AiReplyHighRiskClaimValidator.kt`、`TrustReplyWorkbenchStateStore.kt` 一行不改。
- Applies to: 全仓
- Violation consequence: 前三个属 `manual-send-safety-confirm` 刚落地的契约（改动即破坏 must-NOT-change 第 4、9 条）；中间两个属 G2 与信任缺口判定，改动即破坏 I-2 / I-7；`TrustReplyWorkbenchStateStore` 改动即触及存储契约，破坏 must-NOT-change 第 7 条。
- 来源: K-ai-generate-single-freeform-seam / manual-send-safety-confirm I-10

---

## 现状审计

### Phase 0 知识加载（本轮采用与驳回）

**采用**（已在对应不变量中以 `来源:` 标注）：`K-sensitive-material-cta-not-mention`、`K-sensitive-cta-compound-material-coverage`、`K-ai-reply-action-cta-variant-coverage`（→ I-2）；`K-grounded-proposed-action-body-parity`（→ I-3）；`K-preview-mirrors-pipeline`（→ I-8）；`K-ai-generate-single-freeform-seam`（→ I-9）；`K-js-tests-run-via-exec-plugin`（→ `## 验证命令`）。

**读取后确认不适用**（非静默忽略）：
- `K-action-sanitizer-inclusive-offset`（span 闭区间平移）与 `K-sensitive-action-span-granularity`（sanitize 删除粒度）——本计划**不调用也不修改** `AiReplyActionPolicy.sanitize()`（`grep -rn "AiReplyActionPolicy.sanitize" --include=*.kt src/main` 命中全在 `AiReplyDraftService.kt:1506/1797/1812`，属整封 grounded 路，见 must-NOT-change 第 3 条），offset 语义与本计划无交集。
- `K-manual-send-safety-gate-first-hit-only`——其记录的「首命中即 return」与「预检静默丢弃 `code == null`」两个缺陷已由 `a21784e` 修复；本轮复核 `collectSafetyFindings`（`:697-800`）确认已改为收集全集、`findViolations` 的 `code` 已恒非空。**该条目的「现状」部分已过期**，Phase 6 需修订。

### 数据存储

**本计划不触及任何数据存储写路径。** 不新增表、字段、索引、迁移。

唯一被**读取**的存储是 `trust_reply_workbench_state`：

- Schema/契约：一行 payload JSON per `(source_type, source_id)`，带 `state_version` 乐观锁与 `expires_at`（`TrustReplyWorkbenchStateStore.kt:22-46`）。TTL = `EXPIRY_DAYS = 30L` 天（`:190`，用于 `:75` / `:148` 的 `now.plusDays(...)`）。
- payload 结构：`TrustReplySavedStatePayload`（`TrustReplyWorkbenchService.kt:197-206`），其中 `lockedItems: List<TrustReplyLockedItemRequest>`（`:203`）。
- `TrustReplyLockedItemRequest`（`:308-325`）已含本计划所需的三个字段：`handling`（`:311`）、`answerText`（`:312`）、`operatorInstruction`（`:319`）。**授权集合完全可由既有字段推导，无需新增任何持久化字段。**
- **写路径（全集，`grep -rn "stateStore\.save\|stateStore\.delete" --include=*.kt src/main`，3 处语句 / 2 个函数）**：`saveState()`（`:480`）内的 `stateStore.delete(...)`（`:498`，锁定项清空时删行）与 `stateStore.save(...)`（`:529`）；`deleteState()`（`:552`）内的 `stateStore.delete(...)`（`:554`）。本计划**这三处一行不改**。
- **读路径（全集，`grep -rn "stateStore\.load\|stateStore\.decodePayload" --include=*.kt src/main`，3 处语句 / 1 条链路）**：`bootstrap()` 的 `stateStore.load(...)`（`:387`）与 `stateStore.decodePayload(...)`（`:392`），以及它调用的私有助手 `restoreSavedStateWithFrame()`（`:582`）内的 `stateStore.decodePayload(...)`（`:601`）——三处同属 bootstrap 一条链路。本计划**新增第 2 条读路径**（`operatorAuthorizedActions(source)`），只读不写、不触碰 `state_version`。
- **过期语义（关键，新读路径必须对齐）**：过期是**惰性**的——`stateStore.pruneExpired()` 只在 `save()`（`TrustReplyWorkbenchStateStore.kt:56`）与 `restoreSavedStateWithFrame()` 命中过期分支时（`TrustReplyWorkbenchService.kt:594`）被调用。因此**一行已过期的快照仍可能被 `load()` 读到**。`restoreSavedStateWithFrame` 的做法是显式判 `if (!stored.expiresAt.isAfter(now))` 并按 `EXPIRED` 处理（`:593-599`）。新读路径必须照做，否则会用一份 30 天前的锁定集合去授权今天的正文。
- `deleteState` 的唯一生产调用点是 `TrustReplyWorkbenchController.kt:111-112`（运营显式重置），**发送流程不删快照** —— 因此发送后重发、或先预检后发送，快照都仍在。

### 关键路径：`AiReplyActionPolicy.deriveAllowed()`

**契约**：`fun deriveAllowed(inboundText: String, operatorInstruction: String?, operatorTurns: List<AiReplyTurn>): Set<AiReplyAction>`（`AiReplyActionPolicy.kt:89`）。

**全部命中（`grep -rn "deriveAllowed" --include=*.kt src/main`，基线实测 7 行 = 6 调用点 + 1 定义行）**：

| # | 位置 | 传参 | 本计划 |
|---|---|---|---|
| — | `AiReplyActionPolicy.kt:89` | 定义行 | 不改 |
| 1 | `AiReplyDraftService.kt:483` | `(inboundText, null, emptyList())` | ❌ 不改（逐条 grounded 生成） |
| 2 | `AiReplyDraftService.kt:660` | `(inboundText, null, emptyList())` | ✅ **替换**为常量（B-1） |
| 3 | `AiReplyDraftService.kt:863` | `(inboundText, operatorInstruction, operatorTurns)` | ❌ 不改（整封 `generate()`，唯一真的传值的调用点） |
| 4 | `TrustReplyWorkbenchService.kt:1351` | `(inboundText, null, emptyList())` | ✅ **替换**为常量（C-1） |
| 5 | `TrustReplyWorkbenchService.kt:1364` | `(inboundText, null, emptyList())` | ❌ 不改（grounded claim 锁定） |
| 6 | `PendingMailOperationService.kt:789` | `(inboundText, null, emptyList())` | ✅ **并入**授权集合（D-2），保留调用本身 |

**证据 E-1 — 形参在逐条链路上是死参数。** 6 个调用点中 5 个传 `null, emptyList()`，只有 #3（整封 `generate()`）真的传值。因此在逐条生成/锁定/发送取证链路上，「回答说明」对授权集合的影响**恒为零**。

### 关键路径：`AiReplyDraftService.generateOperatorDirectedAnswer()`（`:625-733`）

**证据 E-2 — 生成侧五步链路（逐字）：**

1. `:637-638`：`val instruction = operatorInstruction?.trim().orEmpty()` + `require(instruction.isNotBlank())` —— 说明非空是硬前提。
2. `:660`：`val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())`
3. `:661-688`：`withActionBoundary(listOf(system, user), allowedActions)`；system content（`:663-671`）逐字为：
   ```
   Rewrite one recipient-facing answer from the operator-provided answer basis.
   Return English only, regardless of the language used in the target question or answer basis;
   translate the answer basis into natural English without changing its facts.
   The operator-provided answer basis is authoritative content. Only restate or organize it;
   do not add any institution, programme, funding, contract, time, identity, number, URL, or other fact
   not present in that basis. Return plain email prose only, with no JSON, headings, lists, status labels,
   or internal markers.
   ```
4. `:707-708`：
   ```kotlin
   val invalid = candidate.isBlank() || INTERNAL_RESPONSE_MARKER.containsMatchIn(candidate) ||
       AiReplyActionPolicy.findViolations(candidate, allowedActions).isNotEmpty()
   ```
   命中即 `itemAnswer = null` + `FALLBACK_NO_RESPONSE` + `lockable = false`。
5. `:740` `rejectNonEnglishItemAnswer()` 兜非拉丁字母（与本计划无关，保持不变）。

**证据 E-3 — `withActionBoundary` 追加的 prompt 文本（`:1724-1744` 逐字）：**
```kotlin
appendLine("Allowed outbound actions for this draft: ${AiReplyActionPolicy.formatAllowedLabel(allowedActions)}.")
appendLine("Do not request materials or propose a meeting/call unless that action is listed above.")
appendLine("Never ask for passports, ID cards, work certificates, or bank statements.")
```
`formatAllowedLabel(emptySet())` 返回 `"NONE"`（`AiReplyActionPolicy.kt:217-218`）。因此当前 system prompt 同时包含「answer basis 是权威内容，只许复述」与「Allowed outbound actions: NONE，不许索要材料或提议会议」两条**互相矛盾**的指令，模型服从后者。

**证据 E-4 — 生产调用点唯一。** `grep -rn "generateItem(" --include=*.kt src/main` 只有两行：定义 `AiReplyDraftService.kt:394`，调用 `TrustReplyWorkbenchService.kt:1104`。**生成侧改动的爆炸半径只有回复台逐条生成这一条链路。**

**证据 E-5 —「方案 B」（把 `operatorInstruction` 传进 `deriveAllowed`）会造成半个生效，实证否决。**
以真实缺陷用例的回答说明「希望专家先提供一下简历 做一个简单的了解 然后再安排 zoom 视频会议」逐字移植 `MATERIAL_INTENT`（`:15-23`）与 `MEETING_INTENT`（`:25-32`）正则实测：

```
instr MEETING  hits: [ /\b(zoom|teams|webex)\b/i , /安排会议|预约通话|方便的时间|约个时间|视频会议/ ]   → 命中
instr MATERIAL hits: []                                                                              → 不命中
```

原因：`MATERIAL_INTENT` 的中文分支只有 `附件.{0,10}(简历|履历|材料)|我的简历|索要(简历|材料)|请对方提供(简历|材料)`（`:22`），「希望专家先提供一下简历」一条都不沾。结果是 Zoom 句子出得来、简历句子仍然没有——**半个生效比全不生效更难排查**。这些正则是为解析来信英文设计的，不适合作为运营中文自由文本的判据。故取方案 C（授权由 handling 决定，不读说明内容）。

**证据 E-6 — 合规句式必须由 prompt 保证（可执行实测）。**
逐字移植 `CV_PURPOSE`（`:38-42`）/`CV_OPTIONALITY`（`:44-47`）/`CV_ONLY_PATTERN`（`:62-65`）/`MATERIAL_REQUEST`（`:49-71`）/`MEETING_REQUEST`（`:73-85`）/`MATERIAL_PROCESS_DESCRIPTION`/`MEETING_PROCESS_DESCRIPTION`/`detectCvConditionViolation`（`:324-341`）/`SENTENCE_SPLIT`（`:87`）后实测：

| 文案 | `detectDirectRequest` | allowed=∅ | allowed={MATERIALS,MEETING} |
|---|---|---|---|
| `If you would like to proceed, you are welcome to share your CV at your convenience so that we can carry out an initial eligibility review.` | REQUEST_MATERIALS | `MATERIALS_NOT_ALLOWED` | **`[]` 通过** |
| `We would also be glad to arrange a Zoom call once that initial review is complete.` | PROPOSE_MEETING | `MEETING_NOT_ALLOWED` | **`[]` 通过** |
| `Could you please share your CV so we can get to know you better?` | REQUEST_MATERIALS | `MATERIALS_NOT_ALLOWED` | `CV_PURPOSE_MISSING` |
| `Please share your CV for an initial eligibility review.` | REQUEST_MATERIALS | `MATERIALS_NOT_ALLOWED` | `CV_OPTIONALITY_MISSING` |
| `You are welcome to share your CV at your convenience.` | REQUEST_MATERIALS | `MATERIALS_NOT_ALLOWED` | `CV_PURPOSE_MISSING` |

结论两条：(a) 只放开授权、不动 prompt，模型有相当概率写出被 G2 判废的句子；(b) 前两条候选措辞在授权集合下确实通过，可直接写进 prompt 作为范式。**同时证明 G2 在授权放开后依然生效**（后三行）。

### 关键路径：`TrustReplyWorkbenchService.validateLockedItem()`（`:1310` 起）

`ANSWER_FROM_OPERATOR_INPUT` 分支 `:1342-1357` 逐字：

```kotlin
TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT -> {
    val instruction = locked.operatorInstruction.trim()
    if (item.status != RequestGroundingStatus.UNSUPPORTED || instruction.isBlank() ||
        instruction.length > 500 || locked.operatorInstructionHash != sha256Hex(instruction) ||
        locked.answerText.isBlank() || locked.claims.isNotEmpty() ||
        locked.generationKind != TrustReplyItemGenerationKind.AI_GENERATED
    ) {
        throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
    }
    val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
    if (AiReplyActionPolicy.detectActions(locked.answerText).isNotEmpty() ||
        AiReplyActionPolicy.findViolations(locked.answerText, allowedActions).isNotEmpty()
    ) {
        throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
    }
}
```

**证据 E-7 — `detectActions` 这半个条件不看 `allowed`。** `detectActions(text)`（`AiReplyActionPolicy.kt:203-215`）返回文本中检出的动作集合，**不接收 `allowed` 参数**。因此 `:1352` 的语义是「只要含任何索要材料或提议会议的句子，一律判废」，与授权无关。这是本缺陷最硬的一道闸：只改生成侧而不改这里，运营授权的文案仍然锁不上。

**证据 E-8 — 该分支的到达路径与爆炸半径。** `validateLockedItem` 有两个调用点：`:818`（在 `validateLockedSubset()` `:789` 内）与 `:1172`（在 `assemble()` `:1149` 内）。
`validateLockedSubset` 的调用点：`:502`（`saveState`）、`:638`。
`assemble()` 的生产调用点（`grep -rn "\.assemble(" --include=*.kt src/main`，3 处）：
1. `TrustReplyWorkbenchController.kt:104` —— 回复台人工汇总
2. `AiTrainingEvaluationService.kt:65` —— 训练模拟评估
3. `PendingMailOperationService.kt:578` —— 发送后的未支持答案归档校验

**因此锁定侧的放宽会同时影响这 5 个入口**（IP-3），A-7 / A-8 分别做黑盒验收。

### 关键路径：`PendingMailOperationService.collectSafetyFindings()`（`:697-800`）

`manual-send-safety-confirm`（`a21784e`）落地后的当前形态：

**签名（`:697-704`）**：
```kotlin
private fun collectSafetyFindings(
    verificationText: String,
    carriesQa: Boolean,
    canonicalFactIds: List<Long>,
    contact: ExpertContact,
    inboundText: String,
    researchProfileSufficient: Boolean
): List<SafetyFinding>
```

**严重度判定（`:708-716`）** —— `CODE_ACTION_SENSITIVE_MATERIAL` 是唯一 `STRONG`，其余一律 `NORMAL`：
```kotlin
findings += SafetyFinding(
    code = code,
    severity = if (code == AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL) {
        SafetySeverity.STRONG
    } else {
        SafetySeverity.NORMAL
    },
    sentence = sentence
)
```

**动作取证段（`:788-796`，本计划唯一改动点）**：
```kotlin
val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
    AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()),
    hasBlockingTrust
)
val violations = AiReplyActionPolicy.findViolations(verificationText, restrictedActions)
violations.forEach { violation ->
    if (violation.code != null) {
        add(violation.code, violation.sentence)
    }
}
```

**两个调用点（全集，`grep -n "collectSafetyFindings(" src/main/.../PendingMailOperationService.kt`）**：

| 调用点 | 位置 | `verificationText` | 可用的授权来源 |
|---|---|---|---|
| 发送 | `:219-226`（`sendManualRichReply` 内） | `finalValidationText` = 渲染后主题+正文+HTML（`:212`） | 形参 `trustReplyAssembly: TrustReplyAssembleRequest?`（`:144`），**已含 `lockedItems`** |
| 预检 | `:991-998`（`preflightEditedAiReply` 内） | `textBody`（编辑器全文） | 只有 `inboundProcessingId`（`:901` 形参）→ 须读持久化快照 |

**证据 E-9 — 发送侧的授权来源就在请求里，无需读库。** `sendManualRichReply` 的 `trustReplyAssembly` 形参（`:144`）类型 `TrustReplyAssembleRequest`，其 `lockedItems: List<TrustReplyLockedItemRequest>`（`TrustReplyWorkbenchService.kt:331`）正是产出本次正文的那一份锁定集合。用它推导授权，比读快照更准（不存在 TTL 与"快照与眼前文字不对应"的问题）。

**证据 E-10 — 身份守卫已有现成范式，必须照抄。** `PendingMailOperationService.kt:573-576` 在消费 `candidateAssembly` 前先校验：
```kotlin
if (candidateAssembly.source.sourceType != TrustReplySourceType.LIVE_INBOUND ||
    candidateAssembly.source.sourceId != inboundProcessingId
) {
    return failedArchive(operatorDirectedCount)
}
```
且 `:569-571` 已有按 `handling == ANSWER_FROM_OPERATOR_INPUT` 过滤 `lockedItems` 的先例。授权推导必须套用同一守卫，否则客户端可传一份**别的来信**的 assembly 来解锁动作。

**证据 E-11 — 预检的入参与请求体确实拿不到逐条目信息。**
- 服务端签名 `preflightEditedAiReply(inboundProcessingId: Long, factRuleIds: List<Long>, expectedEvidenceSetVersion: String, textBody: String)`（`:901-905`）。
- Controller 透传 `UnmatchedInboundMailController.kt:280-291`，请求体只有 `factRuleIds` / `expectedEvidenceSetVersion` / `textBody`。
- 前端请求体 `app.js:9499-9505` 同上。

**证据 E-12 — 快照可由 `inboundProcessingId` 直接定位，无需新增入参。**
`TrustReplySourceType.LIVE_INBOUND` 的 `sourceId` **就是** `inboundProcessingId`：`resolveLiveInbound(source)`（`TrustReplyWorkbenchService.kt:1494-1496`）用 `inboundMailProcessingRepository.findById(source.sourceId)` 解析来信。而 `stateStore.load(sourceType, sourceId)`（`TrustReplyWorkbenchStateStore.kt:31-45`）的主键正是这一对。因此预检侧可在**不加接口参数、不改前端、不动表结构**的前提下读回 `lockedItems`。

### 关键路径：`AiReplyActionPolicy.findViolations()` 的其他消费者

`grep -rn "findViolations" --include=*.kt src/main` 共 10 处：`AiReplyHighRiskClaimValidator.kt:47`、`AiReplyDraftService.kt:708/1414/1452/1770/1793`、`TrustReplyWorkbenchService.kt:1353/1367`、`PendingMailOperationService.kt:792`（`:1022` 已被 `a21784e` 合并进 `collectSafetyFindings`，不再单独存在）。

**证据 E-13 — 本计划只改变其中 3 处传入的 `allowed` 集合，不改 `findViolations` 本身。** 被改的是 `AiReplyDraftService.kt:708`、`TrustReplyWorkbenchService.kt:1353`、`PendingMailOperationService.kt:792` 三处的入参取值；函数实现零改动，其余 6 个消费者行为完全不变。

### 前端

**证据 E-14 — 前端无相关校验，本计划不触及前端。**
`grep -n "ACTION\|CV\|简历\|材料\|会议" src/main/resources/static/trust-reply-workbench.js` **零命中**。回复台前端不做任何动作/CV 判定，全部由服务端裁决。发送侧的确认弹窗与文案已由 `a21784e` 落地（`app.js:4166-4167` 的两条中文说明），本计划复用，不改。故本计划**无 `## 样式契约` 节**（Step 1b-fe 未触发）。

### Interaction points 汇总

| # | 写路径 | 读路径 | 影响 | 验收 |
|---|---|---|---|---|
| IP-1 | 生成侧 `allowedActions`（`AiReplyDraftService.kt:660`） | 锁定侧 `allowedActions`（`TrustReplyWorkbenchService.kt:1351`） | 两者必须同集合，否则「能生成不能锁定」 | I-1 / A-1 / A-7 |
| IP-2 | 生成侧 prompt（`:663-671`） | 生成后校验 `:707-708` 的 G2 判定 | prompt 不给合规句式则概率性自废 | I-4 / A-1 / A-2 |
| IP-3 | 锁定侧放宽（`:1352`） | `assemble()` 的 3 个生产调用者 + `validateLockedSubset` 的 2 个调用点 | 回复台汇总 / 训练模拟 / 归档校验三条链路同时受影响 | I-3 / A-7 / A-8 |
| IP-4 | `saveState()` 写入的 `lockedItems`（`TrustReplyWorkbenchService.kt:480`） | 新增读路径 `operatorAuthorizedActions()` → 预检取证 | 运营锁定后未保存 / 快照过期 → 预检授权为空 → 偏严多报 | I-8 / A-4 |
| IP-5 | 请求携带的 `trustReplyAssembly.lockedItems`（`:144`） | 发送取证 `:219-226` | 身份守卫缺失则可用别的来信的 assembly 越权解锁 | I-6 / I-8 / A-5 |
| IP-6 | 授权并集（`:788-790`） | `restrictForTrustState` 的信任缺口剥离 | 并集位置放错则运营授权压过信任缺口 | I-7 / A-6 |
| IP-7 | 一键预判写入的机器 `suggestedInstruction`（`TrustReplyWorkbenchService.kt:1907`） | 生成侧授权判据（handling + 说明非空） | 机器说明同样获得授权，仅靠 prompt + 人工过目约束 | I-5 / A-9 |

---

## 实现方案

### 阶段 A：新增授权常量（I-1 / I-2）

**A-1. `AiReplyActionPolicy.kt` —— 只新增一个 `val`，不动任何既有行。**

在 `object AiReplyActionPolicy` 内、`fun deriveAllowed(`（`:89`）之前插入：

```kotlin
    /**
     * I-1: ANSWER_FROM_OPERATOR_INPUT 的授权集合（方案 C）。
     *
     * 该 handling 只适用于 RequestGroundingStatus.UNSUPPORTED 条目，答案正文完全来自
     * 运营在「回答说明」里填写的内容，不存在可供偏离的证据。因此「防止 AI 自作主张
     * 向专家提要求」这一层防线（G1）在此没有对象，由运营的填写行为本身承担授权。
     *
     * 本常量只放开 G1。合规层（G2）——敏感材料 CTA、CV 目的缺失、CV 自愿缺失——
     * 由 findViolations() 独立执行，不受本集合影响，见 I-2。
     *
     * 适用范围仅限「生成一条 operator-directed 答案」与「校验一条 operator-directed
     * 锁定项」。发送/预检的整封取证不使用本常量，而是按 I-6 从锁定项实际内容推导。
     */
    val OPERATOR_DIRECTED_ALLOWED_ACTIONS: Set<AiReplyAction> = setOf(
        AiReplyAction.REQUEST_MATERIALS,
        AiReplyAction.PROPOSE_MEETING
    )
```

约束：本文件**只允许**这一处新增。`findViolations`（`:123-152`）、`detectCvConditionViolation`（`:324-341`）、全部正则 `val`、`sanitize`、`restrictForTrustState`、`detectActions`、全部 `const val` 码一字不动（I-2 / I-9）。

### 阶段 B：生成侧（I-1 / I-4 / I-5）

**B-1. `AiReplyDraftService.kt:660` —— 换授权来源。**

```kotlin
// 改前
val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
// 改后（I-1）
val allowedActions = AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS
```

**`inboundText` 形参必须保留，不得删除。** 实测（`sed -n '625,733p' AiReplyDraftService.kt | grep -n inboundText`）：该形参在本函数内**只**出现在两处——签名行与 `:660`。改后它成为未使用形参，但 (a) Kotlin 对未使用的**函数形参**不产生警告；(b) `grep -n "Werror\|allWarningsAsErrors\|<args>" pom.xml` **零命中**，构建未开启「告警即错误」。因此保留形参不会导致编译失败；删除它会改变函数签名并波及唯一调用点（`TrustReplyWorkbenchService.kt:1104`），属越界改动。

**B-2. `AiReplyDraftService.kt:663-671` —— system message content 增补。**

在既有 content 末尾（`"or internal markers."` 之后）追加：

```
 "The answer basis may authorise asking the recipient for materials or proposing a meeting or call; " +
 "when it does, express that action in the reply. Do not introduce any outbound action that the answer basis does not state. " +
 "When the answer basis asks for a CV or other materials, the sentence that makes the request must, within that same sentence, " +
 "state the purpose using the words \"eligibility review\" and make it optional using the words \"at your convenience\". " +
 "Example of an acceptable request sentence: " +
 "\"If you would like to proceed, you are welcome to share your CV at your convenience so that we can carry out an initial eligibility review.\" " +
 "Never ask for passports, ID cards, work certificates, bank statements, or any other identity or financial document."
```

依据（I-4 / 证据 E-6）：`"eligibility review"` 命中 `CV_PURPOSE` 第 1 条正则（`:39`）；`"at your convenience"` 与 `"you are welcome to"` 均命中 `CV_OPTIONALITY` 第 1 条正则（`:45`）；示例句实测在授权集合下 `findViolations` 返回空。
「Do not introduce any outbound action that the answer basis does not state.」是 I-5 的唯一技术护栏，不得删减或弱化。
最后一句敏感材料禁令与 `withActionBoundary` 追加的第三行**有意重复**：后者措辞为「passports, ID cards, work certificates, or bank statements」，本处扩展为「or any other identity or financial document」，覆盖 `SENSITIVE_MATERIAL` 正则里的中文项（护照/身份证/在职证明/银行对账单，`:33-36`，中文分支在 `:35`）。

**B-3. 不改 `withActionBoundary()`（`:1724-1744`）。**
改动后它会自动输出 `Allowed outbound actions for this draft: REQUEST_MATERIALS,PROPOSE_MEETING.`，与 B-2 的新增约束一致，矛盾自然消解。**不得**为本计划给它加分支或参数（它同时服务于 `:483` 的 grounded 路，加分支即违反 must-NOT-change 第 2 条）。

### 阶段 C：锁定侧（I-1 / I-3）

**C-1. `TrustReplyWorkbenchService.kt:1351-1356`。**

```kotlin
// 改前
val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
if (AiReplyActionPolicy.detectActions(locked.answerText).isNotEmpty() ||
    AiReplyActionPolicy.findViolations(locked.answerText, allowedActions).isNotEmpty()
) {
    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
}

// 改后（I-1 / I-3）
// I-3: 本分支不再用无条件 detectActions 判废——运营的回答说明已授权索要材料/
// 提议会议两类动作（I-1）。G2（敏感材料、CV 目的/自愿）仍由 findViolations 执行。
// 注意：下面 ANSWER_WITH_EVIDENCE / ANSWER_SUPPORTED_PART 分支的 detectActions
// 必须保留——grounded claim 正文永远不许含动作。
if (AiReplyActionPolicy.findViolations(
        locked.answerText,
        AiReplyActionPolicy.OPERATOR_DIRECTED_ALLOWED_ACTIONS
    ).isNotEmpty()
) {
    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
}
```

**C-2. 不改 `:1358-1372` 的 grounded 分支。** 其 `deriveAllowed`（`:1364`）与 `detectActions(claim.text)`（`:1366`）逐字保留（I-3 / I-9）。

**C-3. `inboundText` 形参保留。** `validateLockedItem` 的 grounded 分支（`:1364`）仍在使用，无需改签名。

**C-4. 新增公开方法 `TrustReplyWorkbenchService.operatorAuthorizedActions()`（I-6 / I-8）。**

两个重载，共用同一个私有推导函数：

```kotlin
    /**
     * I-6: 从一组锁定项推导「运营已批准的动作类型」。
     * 只取 handling 为 ANSWER_FROM_OPERATOR_INPUT 且回答说明非空的条目，
     * 并且只授权这些条目的答案正文里**实际出现过**的动作类型——不是无条件
     * 授权 OPERATOR_DIRECTED_ALLOWED_ACTIONS 全集。无此类条目时返回空集，
     * 调用方行为与改动前逐字一致。
     */
    fun operatorAuthorizedActions(lockedItems: List<TrustReplyLockedItemRequest>): Set<AiReplyAction> =
        lockedItems
            .asSequence()
            .filter {
                it.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT &&
                    it.operatorInstruction.isNotBlank()
            }
            .flatMap { AiReplyActionPolicy.detectActions(it.answerText).asSequence() }
            .toSet()

    /**
     * I-8: 预检侧入口——没有 assembly 请求时，从持久化快照读回锁定项。
     * 读不到（无快照 / 已过期 / 解码失败）一律返回空集，属有意的 fail-closed：
     * 预检偏严（多报不漏报），运营在发送时仍可确认放行。
     * 本方法只读不写，不触碰 state_version。
     */
    fun operatorAuthorizedActions(source: TrustReplySourceRef): Set<AiReplyAction> {
        require(source.sourceId > 0) { "sourceId must be positive" }
        val stored = stateStore.load(source.sourceType.name, source.sourceId) ?: return emptySet()
        // I-8: 过期是惰性清理的，load() 可能读到已过期的行（pruneExpired 只在
        // save() 与 restoreSavedStateWithFrame 的过期分支被调用）。这里必须显式判，
        // 照抄 restoreSavedStateWithFrame :593-599 的判据；但本方法只读，
        // 不调 pruneExpired、不写 state_version。
        if (!stored.expiresAt.isAfter(LocalDateTime.now())) return emptySet()
        val payload = stateStore.decodePayload(stored.payloadJson) ?: return emptySet()
        return operatorAuthorizedActions(payload.lockedItems)
    }
```

说明：`stateStore.decodePayload()` 已是 `public`（`TrustReplyWorkbenchStateStore.kt:122`）且自带异常兜底（`:131-133` 返回 `null`），无需给 store 加任何方法（I-9 要求该文件零改动）。`java.time.LocalDateTime` 在本文件已被 `bootstrap()`（`:388` 的 `LocalDateTime.now()`）使用，无需新增 import。
本方法**不调用 `resolveSource()`**：授权推导只需读快照，不需要解析来信、联系人或画像，避免为一次取证付出整条 resolve 链路的代价，也避免在来信被删/未绑定联系人时抛异常打断预检。

### 阶段 D：发送与预检取证（I-6 / I-7 / I-8）

**D-1. `collectSafetyFindings()` 增加一个入参（`:697-704`）。**

```kotlin
private fun collectSafetyFindings(
    verificationText: String,
    carriesQa: Boolean,
    canonicalFactIds: List<Long>,
    contact: ExpertContact,
    inboundText: String,
    researchProfileSufficient: Boolean,
    operatorAuthorizedActions: Set<AiReplyAction>   // 新增，无默认值
): List<SafetyFinding>
```

**不给默认值**，强制两个调用点都显式传参，避免将来新增调用点静默走空集。函数体内除 D-2 一处外不做任何修改。

**D-2. 动作取证段并入授权（`:788-792`）。**

```kotlin
// 改前
val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
    AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()),
    hasBlockingTrust
)
// 改后（I-7：并集在前，信任缺口剥离在后）
val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
    AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()) + operatorAuthorizedActions,
    hasBlockingTrust
)
```

`:792` 的 `findViolations` 与 `:793-797` 的 `add(...)` 循环逐字不动。

**D-3. 发送侧调用点传参（`:219-226`）。**

在 `collectSafetyFindings(` 调用之前推导，套用 `:573-576` 的既有身份守卫（证据 E-10）：

```kotlin
// I-6 / I-8: 授权只能由服务端从本次请求已携带的锁定集合推导；
// assembly 必须指向本次来信，否则视为未授权（照抄 :573-576 的身份守卫）。
val operatorAuthorized = trustReplyAssembly
    ?.takeIf {
        it.source.sourceType == TrustReplySourceType.LIVE_INBOUND &&
            it.source.sourceId == inboundProcessingId
    }
    ?.let { trustReplyWorkbenchService.operatorAuthorizedActions(it.lockedItems) }
    .orEmpty()

val findings = collectSafetyFindings(
    verificationText = finalValidationText,
    carriesQa = carriesQa,
    canonicalFactIds = canonicalFactIds,
    contact = contact,
    inboundText = inboundText,
    researchProfileSufficient = researchProfileSufficient,
    operatorAuthorizedActions = operatorAuthorized
)
```

`:227-233` 的 `requiresStrong` 判定与两处 `throw ManualSendSafetyBlockedException(findings)` 逐字不动（must-NOT-change 第 4 条）。

**D-4. 预检侧调用点传参（`:991-998`）。**

```kotlin
val safetyFindings = collectSafetyFindings(
    verificationText = textBody,
    carriesQa = factRuleIds.isNotEmpty(),
    canonicalFactIds = canonicalFactIds,
    contact = contact,
    inboundText = inboundText,
    researchProfileSufficient = researchProfileSufficient,
    // I-8: 预检没有 assembly 入参，改从持久化快照推导；读不到即空集（fail-closed）。
    operatorAuthorizedActions = trustReplyWorkbenchService.operatorAuthorizedActions(
        TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, inboundProcessingId)
    )
)
```

`:999-1003` 的 `safetyFindings.forEach { ... warningCodes += finding.code }` 逐字不动。

**D-5. 唯一需要新增的 import。**
`PendingMailOperationService.kt` 已 import `AiReplyAction`（`:19`）、`AiReplyActionPolicy`（`:20`）、`TrustReplyAssembleRequest`（`:28`）、`TrustReplySourceType`（`:32`），**但未 import `TrustReplySourceRef`**（`grep -n "TrustReplySourceRef" ...` 零命中）。D-4 需要它构造 source，故新增一行：

```kotlin
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
```

按现有 import 块的字母序插入（在 `TrustReplyItemVersion` 之后、`TrustReplySourceType` 之前）。这是本计划**唯一**的 import 新增；`TrustReplyWorkbenchService.kt` 侧 `java.time.LocalDateTime` 已在 `:20` import，无需新增。

### 阶段 E：测试

**E-1. `AiReplyDraftServiceTest.kt` —— 新增 2 个用例，不改既有用例。**

- `operator directed item keeps a materials request authorised by the operator instruction`
  桩 LLM 返回 `"If you would like to proceed, you are welcome to share your CV at your convenience so that we can carry out an initial eligibility review."`，`handling = ANSWER_FROM_OPERATOR_INPUT`，`operatorInstruction = "希望专家先提供一下简历 做一个简单的了解 然后再安排 zoom 视频会议"`。
  断言：`result.lockable == true`、`generationKind == AI_GENERATED`、`itemAnswer?.answerText` 等于桩返回值；并断言捕获的 prompt 含 `"Allowed outbound actions for this draft: REQUEST_MATERIALS,PROPOSE_MEETING."`（I-1 + 证据 E-3 的可观察证据）。
- `operator directed item still rejects a CV request without purpose or optionality`
  桩 LLM 返回 `"Could you please share your CV so we can get to know you better?"`。
  断言：`result.lockable == false`、`itemAnswer == null`、`generationState == FALLBACK_NO_RESPONSE`（I-2 的正向回归）。

既有用例 `operator directed item uses only target question and operator answer basis`（`:129`）与 `operator directed item rejects a Chinese AI answer for an English reply`（`:176`）**不得修改**；前者的 `assertFalse(prompt.contains("expression only"))` 在本改动下仍应通过（B-2 追加的文本不含 `expression only`）。

**E-2. `TrustReplyWorkbenchItemFlowTest.kt` —— 改写 1 个用例注释、新增 2 个。**

- **改写**（仅注释与用例名语义说明，断言保留）`assembled operator directed answer cannot bypass action policy`（`:71-83`）。该用例用 `answerText = "Please send your CV."` 断言抛 `TRUST_REPLY_CLAIM_INVALID`。该句缺目的与自愿，在新语义下**仍应被拒**，但拒因从 G1（动作未授权）变为 G2（`CV_PURPOSE_MISSING`）。在注释中写明这一转变，断言不动。
- **新增** `assembled operator directed answer keeps an operator authorised compliant request`
  `answerText = "If you would like to proceed, you are welcome to share your CV at your convenience so that we can carry out an initial eligibility review."`，其余 fixture 同上，断言 `assemble()` **不抛异常**且返回值包含该文本（I-1 / I-3 / IP-3）。
- **新增** `operator authorized actions come only from operator directed items with a non-blank instruction`
  直接对 `operatorAuthorizedActions(lockedItems)` 断言四种输入：(a) 空列表 → 空集；(b) 只有 grounded 条目且其 `answerText` 含 CV 请求 → 空集（handling 不符）；(c) operator-directed 但 `operatorInstruction` 为空串 → 空集；(d) operator-directed + 说明非空 + 答案只含会议句 → 恰为 `setOf(PROPOSE_MEETING)`（**不含** `REQUEST_MATERIALS`，验证 I-6 的粒度）。
- **新增** `operator authorized actions ignore a missing or expired snapshot`
  对 `operatorAuthorizedActions(source)` 重载断言三种 store 状态：(a) `stateStore.load` 返回 `null` → 空集；(b) 返回的 `TrustReplyStoredState.expiresAt` 早于当前时刻（即便 payload 里有合法的 operator-directed 条目）→ 空集，且断言 `stateStore.pruneExpired` **未被调用**（本方法只读）；(c) `decodePayload` 返回 `null` → 空集。（I-8 的 fail-closed 三分支）

**E-3. `PendingMailOperationServiceTrustWorkbenchTest.kt` —— 新增 3 个用例。**

- `manual send accepts an operator authorised materials request`：assembly 的 locked items 含一条 operator-directed 且答案含合规 CV 请求句，正文同样含该句 → 断言不抛 `ManualSendSafetyBlockedException`（IP-5）。
- `manual send ignores an assembly that points at another inbound`：同上但 `assembly.source.sourceId` 与 `inboundProcessingId` 不一致 → 断言仍抛 `ManualSendSafetyBlockedException` 且 findings 含 `CODE_ACTION_MATERIALS_NOT_ALLOWED`（I-6 身份守卫，证据 E-10）。
- `operator authorisation does not override a blocking trust gap`：构造 `hasBlockingTrustGap == true`（令某条 request fact 存在 `company.` / `agency.` / `finance.` / `fees.` 前缀且 `status != "SUPPORTED"` 的 intent，见 `AiReplyGroundedContentPlanner.kt:219-224`）+ 运营已授权 `REQUEST_MATERIALS` → 断言 findings **仍含** `CODE_ACTION_MATERIALS_NOT_ALLOWED`（I-7 / IP-6）。

**E-4. 不新增 `AiReplyActionPolicyTest` 用例。** 本计划不改 `AiReplyActionPolicy` 的任何判定逻辑，无新行为需要在该层断言；G2 的回归由 E-1 第二条与 E-2 第一条在上层覆盖。

---

## 变更文件清单

| # | 文件 | 改动性质 | 对应任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 新增 1 个 `val`（禁止其他改动） | A-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 改 `:660` 一行；`:663-671` system content 增补 | B-1, B-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 改 `:1351-1356`；新增 2 个重载公开方法 | C-1, C-4 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | `collectSafetyFindings` 增 1 入参、改 `:788-790` 一处、两个调用点传参 | D-1〜D-4 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 新增 2 个用例 | E-1 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 改写 1 个用例注释、新增 3 个用例 | E-2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 新增 3 个用例 | E-3 |

**文件数：7（≤10 ✓）**
**子系统数：2 ✓** —— ① AI 逐条生成/锁定（文件 1-3、5-6）；② 人工发送/预检取证（文件 4、7）。两者的耦合面只有 `operatorAuthorizedActions()` 一个函数签名。
**新增存储字段：0 ✓　前端文件：0 ✓　HTTP 契约变更：0 ✓**

---

## 验证命令

> **本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。** 命令来源：项目根 `CLAUDE.md` 「Commands」章节（`:5-28`）与项目元信息 `test_command:`（`:140`）/ `build_command:`（`:142`）。
> JS 测试命令来源：`docs/knowledge/build/K-js-tests-run-via-exec-plugin.md`（依据 `pom.xml:186-232`，2026-08-19 实测）。

```bash
# 1) 本计划相关的后端测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest,AiReplyActionPolicyTest,PendingMailOperationServiceTrustWorkbenchTest

# 2) E-1 新增的两个生成侧用例单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyDraftServiceTest#operator directed item keeps a materials request authorised by the operator instruction'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyDraftServiceTest#operator directed item still rejects a CV request without purpose or optionality'

# 3) E-2 新增/改写的锁定侧用例单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#assembled operator directed answer keeps an operator authorised compliant request'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#operator authorized actions come only from operator directed items with a non-blank instruction'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#operator authorized actions ignore a missing or expired snapshot'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#assembled operator directed answer cannot bypass action policy'

# 4) E-3 新增的发送/预检取证用例单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='PendingMailOperationServiceTrustWorkbenchTest#manual send accepts an operator authorised materials request'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='PendingMailOperationServiceTrustWorkbenchTest#manual send ignores an assembly that points at another inbound'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='PendingMailOperationServiceTrustWorkbenchTest#operator authorisation does not override a blocking trust gap'

# 5) 前端回归（本计划不改前端，仅作未污染确认；无需 JAVA_HOME）
node --test src/test/js/*.test.js

# 6) 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 7) 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 8) 空白/换行卫生
git diff --check
```

**通过判据**
- 命令 1/2/3/4/6：退出码 0，Surefire 汇总为 `Tests run: N, Failures: 0, Errors: 0`。
- 命令 5：退出码 0，输出含 `# fail 0`。
- 命令 7：退出码 0，`BUILD SUCCESS`，产出 WAR。
- 命令 8：无输出（有输出即存在行尾空白或缺失换行）。

---

## 验收标准

- **I-1**：`grep -rn "OPERATOR_DIRECTED_ALLOWED_ACTIONS" --include=*.kt src/main` 恰好命中 3 行（定义 1 处 + `AiReplyDraftService.kt` 1 处 + `TrustReplyWorkbenchService.kt` 1 处）；`grep -rn "deriveAllowed" --include=*.kt src/main | wc -l` 由 **7**（6 调用点 + 1 定义行）降为 **5**（4 调用点 + 1 定义行），剩余 4 个调用点对应现状审计表中标注「不改」的 #1/#3/#5/#6。E-1 第一条用例断言 prompt 含 `Allowed outbound actions for this draft: REQUEST_MATERIALS,PROPOSE_MEETING.`。
- **I-2**：`git diff --numstat src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` 的 **deletions 必须为 0**（只有新增行）。E-1 第二条与 E-2 第一条用例通过（缺目的/自愿的 CV 请求仍被拒）。
- **I-3**：`grep -n "detectActions" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 从 2 处变为 2 处，但其中一处位于新增的 `operatorAuthorizedActions()` 内、另一处位于 `ANSWER_WITH_EVIDENCE / ANSWER_SUPPORTED_PART` 分支且作用于 `claim.text`；**`locked.answerText` 不再出现在任何 `detectActions(` 的入参位置**（`grep -n "detectActions(locked.answerText)" ...` 零命中）。
- **I-4**：`git diff` 中 `AiReplyDraftService.kt` 的 system content 新增文本逐字包含 `eligibility review` 与 `at your convenience`；E-1 第一条用例通过。
- **I-5**：`git diff` 中 system content 逐字包含 `Do not introduce any outbound action that the answer basis does not state.`。
- **I-6**：`collectSafetyFindings` 的新增形参**无默认值**（`grep -n "operatorAuthorizedActions: Set<AiReplyAction>" ...` 命中行不含 `=`）；`grep -rn "operatorAuthorizedActions" --include=*.kt src/main/kotlin/com/weibo/talentintroduction/mail` 的每一处取值都不来自任何 `*Request` DTO 字段。E-2 第三条用例的 (b)(c)(d) 三个分支通过（粒度断言）。
- **I-7**：`git diff` 中 `PendingMailOperationService.kt` 的 `restrictForTrustState(` 调用里，`+ operatorAuthorizedActions` 出现在**第一个实参内部**（即 `deriveAllowed(...) + operatorAuthorizedActions` 整体作为 `allowedActions` 传入），而非包在 `restrictForTrustState(...)` 外层。E-3 第三条用例通过。
- **I-8**：`collectSafetyFindings` 仍只有 2 个调用点（`grep -c "collectSafetyFindings(" src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` 结果为 **3** = 1 定义 + 2 调用），两处都显式传 `operatorAuthorizedActions`；新增的 `operatorAuthorizedActions(source)` 内含 `expiresAt` 判据（`grep -n "expiresAt" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 较改动前多 1 处命中）且**不含** `pruneExpired`；E-2 第四条与 E-3 前两条用例通过。
- **I-9**：`git diff --name-only` 的输出与 `## 变更文件清单` 的 7 个路径**完全一致**（集合相等，不多不少）。
- **IP-1**：E-1 第一条（生成侧通过）与 E-2 第二条（锁定侧通过）同时绿。
- **IP-2**：E-1 两条用例一正一反同时绿。
- **IP-3**：执行「验证命令」节命令 1，`TrustReplyWorkbenchServiceTest` 与 `TrustReplyWorkbenchItemFlowTest` 全绿（覆盖 `saveState` 与 `assemble` 两条到达路径）。
- **IP-4 / IP-5 / IP-6**：E-3 三条用例分别绿。
- **回归**：执行「验证命令」节命令 6（全量测试）与命令 7（构建）通过；命令 5 前端测试通过；命令 8 无输出。

---

## 人工验收清单

### A-1: 运营授权的索要简历 + 提议会议，两个动作都出现在正文
- 前置条件：存在一封已绑定专家联系人的来信，其中至少一条请求被判为 `UNSUPPORTED`（回复台该条目显示「对应事实 0 / 未绑定事实」）。可直接用本缺陷的真实来信：`Hi, thank you for your email. My area of specialisation is econometric and statistical analysis of traffic and road safety data. A call would also offer an opportunity for further discussion. I look forward to hearing from you. Thank you`
- 操作步骤：
  1. 打开该来信的回复台，展开该 `UNSUPPORTED` 条目
  2. 「处理方式」选「按回答说明生成」
  3. 「回答说明」填入：`希望专家先提供一下简历 做一个简单的了解 然后再安排 zoom 视频会议`
  4. 点击生成
- 预期结果：生成的英文正文**同时包含**一句索取简历的话（含 `eligibility review` 与 `at your convenience` 或等价自愿措辞）**和**一句提议 Zoom 通话的话。不再出现 `We have received your details and will follow up with you regarding next steps.` 这类只有寒暄没有动作的文案。
- 覆盖：observable outcome 1；I-1、I-4、IP-1、IP-2

### A-2: 回答说明明确要求"不要说理由"时，系统仍不产出裸索要
- 前置条件：同 A-1
- 操作步骤：「回答说明」填入 `直接问他要简历，不用说原因，也别说是自愿的`，点击生成
- 预期结果：二选一，均为通过——(a) 正文中的索取句**仍然**带有目的与自愿措辞；(b) 生成失败并提示。**不允许**出现「正文里是一句光秃秃的 `Please send your CV.`」。
- 覆盖：must-NOT-change 第 1 条；I-2、I-4

### A-3: 敏感材料仍被硬拦（G2 回归）
- 前置条件：同 A-1
- 操作步骤：「回答说明」填入 `让他把护照复印件和银行流水一起发过来`，点击生成
- 预期结果：该条目**生成失败**，或生成的正文完全不含护照/银行流水的索取。任何情况下正文都不得出现向专家索要护照、身份证、在职证明、银行流水的句子。
- 覆盖：must-NOT-change 第 1、5 条；I-2

### A-4: 预检面板不再对已授权动作报风险（本轮新增能力）
- 前置条件：A-1 与 A-7 已完成，汇总正文含索要简历句与 Zoom 提议句，且该正文已进入人工回复编辑器
- 操作步骤：
  1. 在编辑器里点一下正文（触发防抖预检）
  2. 等待编辑器下方「风险提示」面板刷新
- 预期结果：面板**不再**出现「对方来信未提出提供材料，正文却主动索要简历/材料」与「对方来信未提出会面意向，正文却主动提议安排会议/Zoom」两条。（改动前这两条会出现——或更糟，因 `code == null` 被静默丢弃而显示"未发现新增风险"却在发送时被拦。）
- 覆盖：observable outcome 3、4；I-8、IP-4

### A-5: 发送成功，且预检与发送结论一致
- 前置条件：同 A-4
- 操作步骤：点击发送
- 预期结果：**不弹出**关于索要材料 / 提议会议的安全提示，直接发送成功。若因正文中其它内容弹出提示，其条目中**不得**包含上述两条动作类提示。
- 覆盖：observable outcome 3、4；I-6、I-8、IP-5

### A-6: 信任缺口场景下仍提示，且可知情放行
- 前置条件：一封来信中，除 A-1 的那条请求外，另有一条被识别为公司/中介/资金/收费类的问题（例如「Is this agency-run? Who pays the salary?」）且事实库中该意图**未被支持**（回复台该条目显示为 `UNSUPPORTED` 或 `PARTIAL` 且相关意图未命中事实）
- 操作步骤：按 A-1 授权索要简历 → 汇总 → 采用 → 点击发送
- 预期结果：弹出安全提示框，其中**包含**「对方来信未提出提供材料，正文却主动索要简历/材料…」一条；点击确认后**发送成功**（该码为 `NORMAL` 等级，无需逐字输入「确认发送」）。
  这是决策 2 的目标行为：运营授权**不压过**信任缺口，但运营可以知情放行。
- 覆盖：I-7、IP-6

### A-7: 授权文案能正常汇总（本缺陷最硬一道闸的直接验收）
- 前置条件：A-1 已完成且该条目已生成出含索要简历的正文
- 操作步骤：锁定该条目 → 其余条目也各自锁定 → 点击汇总
- 预期结果：汇总**成功**，预览正文中该条目的段落完整保留索要简历与提议 Zoom 两句。**不得**出现 `TRUST_REPLY_CLAIM_INVALID` 报错。（改动前此步必然失败——这是本条验收的意义。）
- 覆盖：observable outcome 2；I-3、IP-3

### A-8: 训练模拟入口回归
- 前置条件：训练邮件库中存在一封含 `UNSUPPORTED` 请求的训练来信
- 操作步骤：在训练模拟页对该邮件走一遍逐条生成 + 汇总评估
- 预期结果：流程正常完成，不报 `TRUST_REPLY_CLAIM_INVALID`；评估结果正常展示。
- 覆盖：IP-3（`AiTrainingEvaluationService.kt:65` 这条 assemble 到达路径）

### A-9: 一键预判批量产物不夹带未授权动作
- 前置条件：一封含 ≥2 条 `UNSUPPORTED` 请求的来信，各条目「回答说明」均为空
- 操作步骤：点击「一键预判」→ 逐条展开，阅读自动填入的「回答说明」与生成的正文
- 预期结果：自动填入的回答说明形如「这一条我们库里没有确认口径。请按真人对接人的方式回答：先明说没有确认答案…最后交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。」；对应生成的正文**不含**索要简历/材料，也**不含**提议会议/通话（机器说明本身没提这两个动作，I-5 禁止模型自行引入）。条目上应显示「自动填写」标记。
- 覆盖：I-5、IP-7；需求方决策「一键预判也算授权」的护栏

### A-10: 有据条目（GROUNDED）与待核实条目（ACK）零变化（回归）
- 前置条件：同一封来信中存在一条 `GROUNDED` 条目与一条可选「先致意、待核实」的条目
- 操作步骤：对 `GROUNDED` 条目按默认处理方式生成；对另一条改选「先致意、待核实」后生成
- 预期结果：两者正文均**不含**任何索要简历/材料或提议会议/通话的句子；均可正常锁定。
- 覆盖：must-NOT-change 第 2 条；I-3、I-9

### A-11: 纯人工回复（未采用 AI 草稿）零变化（回归）
- 前置条件：一封未使用回复台、无任何锁定快照的来信
- 操作步骤：直接在人工富文本编辑器里写一句 `Could you please share your CV?` 并发送
- 预期结果：仍弹出安全提示「对方来信未提出提供材料，正文却主动索要简历/材料…」，需确认后才发送。（无 operator-directed 锁定项 → 授权为空集 → 行为与改动前逐字一致。）
- 覆盖：I-6 的空集分支；I-8 的 fail-closed 分支

### A-12: 整封 AI 草稿与自动回复链路零变化（回归）
- 前置条件：(a) 一封可走「整封生成」的来信（非逐条 workbench 路径）；(b) 自动回复功能处于开启状态且存在一封符合自动回复条件的来信
- 操作步骤：
  1. 对 (a) 触发一次整封 AI 草稿生成，阅读正文
  2. 对 (b) 等待/触发一次自动回复决策，查看决策结果与产出（不实发）
- 预期结果：
  1. 整封草稿的动作行为与改动前一致——来信未表达提供材料/会面意向时，正文不含索要或提议句；来信表达了的则照常出现。
  2. 自动回复的 fail-closed 判定不变：存在 `UNSUPPORTED` 条目时仍不自动发送；决策日志中不出现任何「已授权」类旁路。
- 覆盖：must-NOT-change 第 3、6 条；I-9

### A-13: 改动范围核对（防越界）
- 前置条件：本计划实现完成，工作区仅含本计划改动
- 操作步骤：执行 `git diff --name-only`
- 预期结果：输出恰好为 `## 变更文件清单` 中的 7 个路径，不多不少。特别确认 `app.js`、`UnmatchedInboundMailController.kt`、`GlobalExceptionHandler.kt`、`TrustReplyWorkbenchStateStore.kt` **不在**其中。
- 覆盖：I-9；must-NOT-change 第 4、7、8、9 条
