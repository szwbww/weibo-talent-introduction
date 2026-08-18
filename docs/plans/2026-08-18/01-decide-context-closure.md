# 01 · decide 上下文收口

日期：2026-08-18
基线 commit：`4583525`（main）
主计划：[00-auto-reply-convergence-master.md](./00-auto-reply-convergence-master.md)
子系统数：1（mail）
文件数：7

## 需求描述

### Observable outcome

1. 对同一封来信，**自动回复预览 / 自动实发** 与 **可信回复工作台 / AI 训练模拟** 在「某条诉求是否有依据（GROUNDED/PARTIAL/UNSUPPORTED）」上给出相同结论。当前研究匹配类诉求在两侧结论相反。
2. 运营在 AI 训练页维护的知识（`ai_training_qa`）对自动回复路径生效——当前完全不生效。
3. 预览记录若没有绑定专家联系人（`expertContactId` 为 null），判定 fail-closed，不再被当作"上下文充足"。

### What must NOT change

- `decide()` 仍是自动预览与自动实发的唯一共享决策点，调用方仍为 2 处（X-1）。
- `passesSendGate()` / `resolveReason()` 的既有判定条件与返回的 reason 常量集合不变。
- `verifyAutoEvidenceRuleIds()` 的 AUTO/enabled/非空 三条校验不变。
- 预览保持反事实与只读：不加 `@Transactional`、无 `save`/`send`，`wouldBeBlockedBy` 的 5 个标记不变（X-2）。
- `AutoMailReplyService.processSingle()` 在 `decide()` 之前的分支顺序（classify → effectiveIntent → when(autoAction)）不变。
- `AutoReplyPreviewKind` 枚举值不变（前端 `autoReplyPreviewKindLabels` 依赖，`app.js:9345`）。

### Out of scope

- 前端合并（→ 02）。
- CRS 打分与日志表（→ 03）。
- 逐项 `generateItem()` 管线改造（主计划 X-4，本轮明确不做）。
- `AutoReplyPreviewService` 在 `QA_GAP`/`QA_NO_MATCH`/`MANUAL_HANDOFF` 分支出稿（→ 02）。
- `AiReplyDraftService.generate()` 的 `researchProfileSufficient` 默认表达式本身（保留，只保证调用方显式传值）。

## 关键不变量

### Invariant I-1: 自动路上下文的唯一来源是 `AiReplyContextService.build()`
- Rule：`decide()` 内构造 `expertProfile` / `mailHistory` / `contextWarnings` / `researchProfileSufficient` 时，必须整体来自一次 `aiReplyContextService.build(...)` 调用；禁止在 `GroundedAutoReplyDecisionService` 内自行拼接 profile 文本、自行调 `AiTrainingQaService` 之外的知识源、或只取其中部分字段而其余走默认值。
- Applies to：`GroundedAutoReplyDecisionService.decide()`（唯一新增写入点）。
- Violation consequence：训练知识开出第二条注入通道，与 `appendKnowledgeToProfile` 漂移；或四个字段部分为真部分为默认，产生比现在更难排查的半一致状态。
- 来源：K-training-knowledge-injection-points

### Invariant I-2: `researchProfileSufficient` 必须来自实际查询结果
- Rule：传给 `AiReplyDraftService.generate()` 的 `researchProfileSufficient` 必须是 `AiReplyContext.researchProfileSufficient` 的值（由 `isResearchSufficient(profile)` 对实际 ES 查询结果计算），**禁止依赖 `generate()` 的默认表达式 `!contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")` 反推**。
- Applies to：`GroundedAutoReplyDecisionService.decide()`。
- Violation consequence：这正是当前缺陷 —— 空 warnings 使该值恒为 `true`，`AiReplyIntentCatalog.resolveIntentEvidence()` 的 `if (intent.requiresProfile && !profileSufficient) → MISSING` 永不触发，研究匹配类诉求被自动路判为 SUPPORTED 并可通过 `passesSendGate()`。
- 来源：K-ai-research-profile-authority-parity（"画像充分性使用实际查询结果，不以 warning 缺席反推"）、K-research-fit-dual-evidence

### Invariant I-3: 无联系人时 fail-closed
- Rule：`AutoReplyPreviewService` 的 `record.expertContactId` 为 `null` 时，`decide()` 必须以 `researchProfileSufficient = false` 且 `contextWarnings` 至少含 `"EXPERT_PROFILE_NOT_FOUND"` 进入判定；**禁止**在无联系人时跳过上下文构造而沿用 `generate()` 默认值。
- Applies to：`GroundedAutoReplyDecisionService.decide()` 的 `contact == null` 分支。
- Violation consequence：无联系人恰恰是画像最不可能存在的场景，若此时判"充足"，等于把最危险的一类记录放到最宽松的判定上。
- 来源：original（`InboundMailProcessing.kt:25` `val expertContactId: Long?`；`AutoMailReplyService.kt:79-97` 联系人为 null 时早退，故该分支只会由预览触发）

### Invariant I-4: 预览保持只读与反事实
- Rule：本计划新增的上下文构造只允许只读操作（`mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc`、`expertSearchService.findByOrcidId`、`aiTrainingQaService.buildKnowledgeContext`）。`AutoReplyPreviewService` 不得新增 `@Transactional`、`save`、`send`；不得触发 enrichment。
- Applies to：`AutoReplyPreviewService.preview()`、`GroundedAutoReplyDecisionService.decide()`。
- Violation consequence：预览产生副作用，运营点一次预览就改动线上数据。
- 来源：K-preview-mirrors-pipeline、K-ai-reply-profile-absence-warning（"只读画像不得触发 enrichment"）

### Invariant I-5: `decide()` 签名变更后调用方仍恒为 2
- Rule：`decide()` 新增 `contact` 参数后，生产调用方仍只有 `AutoReplyPreviewService:111` 与 `AutoMailReplyService:505`。不得为"方便测试"或"给前端用"新增第三个调用方或旁路重载。
- Applies to：全仓 `grep -rn "\.decide(" src/main --include=*.kt`。
- Violation consequence：自动预览与自动实发再次分叉，这是本计划要消除的根因。
- 来源：K-ai-generate-single-freeform-seam

## 现状审计

### `GroundedAutoReplyDecisionService`（本计划核心改动点）

**当前构造器**（`GroundedAutoReplyDecisionService.kt:38-42`）：

```kotlin
class GroundedAutoReplyDecisionService(
    private val llmProperties: LlmProperties,
    private val aiReplyDraftService: AiReplyDraftService,
    private val qaRuleRepository: QaRuleRepository
)
```

**当前 `decide()` 的生成调用**（`:50-53`）：

```kotlin
val draft = aiReplyDraftService.generate(
    inboundText = inboundText,
    operatorTurns = emptyList()
)
```

其余 8 个具名参数全部走默认值。`AiReplyDraftService.generate()` 的默认值（`AiReplyDraftService.kt`，两个重载签名一致）：

```kotlin
qaRuleIds: List<Long>? = null,
operatorInstruction: String? = null,
expertProfile: String? = null,
mailHistory: String? = null,
simulateOnly: Boolean = false,          // deprecated: has no effect; do not read
contextWarnings: List<String> = emptyList(),
replyModel: String? = null,
researchProfileSufficient: Boolean =
    !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
```

→ `contextWarnings` 为空列表 ⇒ `researchProfileSufficient` 恒 `true`，同时 `expertProfile` 为 `null`。

- **Write paths**：无（纯计算服务，不写任何存储）。
- **Read paths**：`qaRuleRepository.findById()`（`verifyAutoEvidenceRuleIds` / `hasReviewPolicyEvidence`）。
- **调用方（生产，2 处，已 grep 核对）**：

```
$ grep -rn "groundedAutoReplyDecisionService\.decide" src/main --include=*.kt
src/main/kotlin/.../mail/service/AutoReplyPreviewService.kt:111
src/main/kotlin/.../mail/service/AutoMailReplyService.kt:505
```

### `AiReplyContextService`（要接入的现成能力）

签名（`AiReplyContextService.kt:29-35`）：

```kotlin
fun build(
    contact: ExpertContact,
    records: List<MailRecord>,
    inboundText: String,
    trainingKnowledge: String,
    currentInboundMessageId: String? = null
): AiReplyContext
```

返回 `AiReplyContext(profileText, mailHistory, contextWarnings, researchProfileSufficient)`。
内部：`loadProfile()` 无 ORCID 或查不到时追加 `"EXPERT_PROFILE_NOT_FOUND"`；
`isResearchSufficient(profile)` 对 `researchFields / keyword / disciplineCategory / recentWorkTitles` 取或；
`requiresResearchContext(inboundText) && !researchProfileSufficient` 时追加 `"EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"`。

**生产调用方（4 处，已 grep 核对）**：

```
$ grep -rn "aiReplyContextService\.build" src/main --include=*.kt
src/main/kotlin/.../llm/controller/AiTrainingController.kt:220
src/main/kotlin/.../llm/service/TrustReplyWorkbenchService.kt:1367
src/main/kotlin/.../mail/controller/UnmatchedInboundMailController.kt:347
src/main/kotlin/.../mail/service/PendingMailOperationService.kt:535
```

`GroundedAutoReplyDecisionService` **不在其中** —— 这是本计划的根因。

三处传真实 knowledge：

```kotlin
// AiTrainingController.kt:219-220
val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
val context = aiReplyContextService.build(contact, records, inboundText, knowledge, inboundMail.messageId)

// UnmatchedInboundMailController.kt:346-347
val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
val context = aiReplyContextService.build(contact, records, inboundText, knowledge, detail.messageId)

// TrustReplyWorkbenchService.kt:1366-1372
val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
val context = aiReplyContextService.build(
    contact = contact, records = records, inboundText = inboundText,
    trainingKnowledge = knowledge, currentInboundMessageId = messageId
)
```

第 4 处 `PendingMailOperationService.kt:533-536` 传 `""`：

```kotlin
private fun resolveResearchProfileSufficient(contact: ExpertContact, inboundText: String): Boolean {
    val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
    val context = aiReplyContextService.build(contact, records, inboundText, "")
    return context.researchProfileSufficient
}
```

**这不是缺陷**：该方法只读回 `researchProfileSufficient`，`profileText` 直接丢弃。本计划不改它。

### `AiTrainingQaService.buildKnowledgeContext`

签名（`AiTrainingQaService.kt:85`）：`fun buildKnowledgeContext(inboundText: String): String`。
内部对 `inboundText` 归一化后按当前来信定向筛选（空输入返回 `""`），符合 K-training-knowledge-injection-points 的"禁止全量注入"。

### `AutoMailReplyService`（调用方 1）

- **`decide()` 调用点**：`:505` `val decision = groundedAutoReplyDecisionService.decide(cleanedBody, received.subject)`。
- **该点作用域内已有的变量**（已核对行号）：
  - `contact`（`:79` `expertEmailAliasService.findContactByEmailOrAlias(received.from)`）
  - `contactId`（`:99`）
  - `effectiveContact`（`:291` `applyPromotionAndStatus(...)` 的返回）
  - `received: ReceivedMail`，含 `messageId: String?`（`MailReceiveService.kt:25`）
- **contact 为 null 时早退**：`:80-97` 分支 `return SinglePipelineResult(outcome = UNMATCHED_CONTACT, ...)`，故 `:505` 处 `contact` 必非 null。
- **已注入的依赖**（`:30-62` 构造器，共 33 个参数）：含 `expertContactRepository`、`mailRecordRepository`。
  **未注入**：`AiReplyContextService`、`AiTrainingQaService`。
- **`@Transactional`**：`processSingle()` 带 `@org.springframework.transaction.annotation.Transactional`（`:66`）。上下文构造会在事务内做一次 ES 只读查询——该服务在同一事务内已有 SMTP 投递等外部调用，此处不新增约束，仅记录为已知事实。

### `AutoReplyPreviewService`（调用方 2）

- **`decide()` 调用点**：`:111` `val decision = groundedAutoReplyDecisionService.decide(cleanedBody, record.subject)`。
- **该点作用域内已有**：`record: InboundMailProcessing`（含 `expertContactId: Long?`、`messageId: String?`）、`cleanedBody`、`contactId`（`:71` `val contactId = record.expertContactId`）。
- **`contactId` 可为 null**：`InboundMailProcessing.kt:25` `val expertContactId: Long?`。现有代码在 `:114` 已处理 `contactId?.let { expertContactRepository.findById(it).orElse(null) }`。
- **已注入的依赖**（`:36-47`）：含 `expertContactRepository`、`mailRecordRepository`、`emailSuppressionService`、`mailVariableService`。
  **未注入**：`AiReplyContextService`、`AiTrainingQaService`。
- **无 `@Transactional`**：类与方法均无该注解（已核对全文）。

### Interaction points

| # | 写入方 | 读取方 | 本计划影响 |
|---|---|---|---|
| IP-1 | `AiTrainingQaService.buildKnowledgeContext()`（读 `ai_training_qa`） | `decide()` → `generate()` 的 `expertProfile` | 新建。训练页新增/停用条目后，自动路的草稿内容随之变化 |
| IP-2 | `ExpertSearchService.findByOrcidId()`（读 ES 三层画像） | `decide()` 的 `researchProfileSufficient` | 新建。专家画像缺失/补齐会翻转自动路的 GROUNDED 判定 |
| IP-3 | `mailRecordRepository`（读既有往来） | `decide()` → `generate()` 的 `mailHistory` | 新建。历史邮件会进入自动路 prompt |
| IP-4 | `decide()` 的 `reason` | `AutoMailReplyService.markManualReview()` 的 `reason` / `reasonType` 落库；前端按 reason 展示 | 既有。**reason 常量集合不得扩充**（must-NOT-change） |

### 测试面（签名变更会打断的既有断言，已逐一核对）

| 文件 | 位置 | 断言内容 | 本计划必须如何改 |
|---|---|---|---|
| `GroundedAutoReplyDecisionServiceTest.kt` | `:38-53` `stubGenerate()` | 用 **10 个位置匹配器** stub `generate()`，其中第 5、6 位（`expertProfile`、`mailHistory`）为 `Mockito.isNull()` | 改为 `Mockito.any()` / `Mockito.anyString()`；新增断言验证第 10 位 `researchProfileSufficient` 收到 `false` |
| `GroundedAutoReplyDecisionServiceTest.kt` | `:55-60` `service()` | 3 参构造器 | 增补新依赖的 mock |
| `GroundedAutoReplyDecisionServiceTest.kt` | `:96,108,126,148,158,176,194,211,227,242,258` 共 11 处 | `service().decide("...", "...")` 二参调用 | 全部补 contact 实参 |
| `AutoReplyPreviewServiceTest.kt` | `:169` | `verify(...).decide(anyString(), eqValue("Remote work"))` | 补第三个匹配器 |
| `AutoReplyPreviewServiceTest.kt` | `:549,555` | `when(...decide(anyString(), any()))` | 补第三个匹配器 |
| `AutoMailReplyServiceTest.kt` | `:1060` | `verify(...).decide(anyString(), eqValue("Re: Talent Program"))` | 补第三个匹配器 |
| `AutoMailReplyServiceTest.kt` | `:1680,1686` | `when(...decide(anyString(), any()))` | 补第三个匹配器 |
| `AiReplyDraftServiceTest.kt` | `:925-929` | `GroundedAutoReplyDecisionService(props, service(props, client), qaRuleRepository).decide("Auto question", "Subject")` | 补构造器参数与 contact 实参 |

grep 依据：

```
$ grep -rn "\.decide(" src/test --include=*.kt | wc -l
18
```

分布：`GroundedAutoReplyDecisionServiceTest` 11 处 + `AutoReplyPreviewServiceTest` 3 处 + `AutoMailReplyServiceTest` 3 处 + `AiReplyDraftServiceTest` 1 处 = 18，与总数吻合。执行时以实际编译错误为准逐一核销。

## 实现方案

### T1 · 扩展 `GroundedAutoReplyDecisionService` 依赖与签名（I-1, I-2, I-3, I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`

1. 构造器新增三个依赖：`aiReplyContextService: AiReplyContextService`、`aiTrainingQaService: AiTrainingQaService`、`mailRecordRepository: MailRecordRepository`。
2. `decide()` 签名改为：

```kotlin
fun decide(
    inboundText: String,
    inboundSubject: String?,
    contact: ExpertContact?,
    currentInboundMessageId: String? = null
): GroundedAutoReplyDecision
```

3. 在 `if (!llmProperties.autoReplyEnabled) return disabledDecision(subject)` **之后**、`generate()` **之前**插入上下文构造（保持 kill switch 优先，避免关闭时仍做 ES 查询）：

```kotlin
val context = buildAutoReplyContext(contact, inboundText, currentInboundMessageId)

val draft = aiReplyDraftService.generate(
    inboundText = inboundText,
    operatorTurns = emptyList(),
    expertProfile = context.profileText,
    mailHistory = context.mailHistory,
    contextWarnings = context.contextWarnings,
    researchProfileSufficient = context.researchProfileSufficient
)
```

4. 新增私有方法，`contact == null` 走 fail-closed 分支（I-3）：

```kotlin
private fun buildAutoReplyContext(
    contact: ExpertContact?,
    inboundText: String,
    currentInboundMessageId: String?
): AiReplyContext {
    if (contact?.id == null) {
        return AiReplyContext(
            profileText = "",
            mailHistory = "",
            contextWarnings = listOf("EXPERT_PROFILE_NOT_FOUND"),
            researchProfileSufficient = false
        )
    }
    val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
    val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
    return aiReplyContextService.build(
        contact = contact,
        records = records,
        inboundText = inboundText,
        trainingKnowledge = knowledge,
        currentInboundMessageId = currentInboundMessageId
    )
}
```

**约束**：`resolveReason()`、`passesSendGate()`、`verifyAutoEvidenceRuleIds()`、`disabledDecision()`、`buildReplySubject()` 一行不改（must-NOT-change）。

**注意**：`hasValidationFailure(draft.contextWarnings)` 现在会看到新注入的 `EXPERT_PROFILE_NOT_FOUND` / `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`。核对 `hasValidationFailure` 的匹配集合（`:190-200`）：只匹配 `WARNING_STRUCTURED_RESPONSE_INVALID`、`WARNING_UNNATURAL_GROUNDED_STRUCTURE`、`WARNING_CLAIM_VALIDATION_FAILED`、`TRUST_REPAIR_EXHAUSTED`、`UNAUTHORIZED_ACTION_REMOVED`、前缀 `AI_REPLY_CLAIM_`、前缀 `AI_REPLY_ACTION_`。两个新 warning **均不命中**，故不会被误判为 `AI_REPLY_VALIDATION_FAILED`。此为设计意图：画像不足应通过 `requestFacts` 的 UNSUPPORTED 走 `QA_GROUNDING_GAP`，而非伪装成校验失败。**执行时必须保留此行为，不得把新 warning 加进 `hasValidationFailure`。**

### T2 · `AutoReplyPreviewService` 传入 contact（I-3, I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`

`:111` 改为：

```kotlin
val previewContact = contactId?.let { expertContactRepository.findById(it).orElse(null) }
val decision = groundedAutoReplyDecisionService.decide(
    inboundText = cleanedBody,
    inboundSubject = record.subject,
    contact = previewContact,
    currentInboundMessageId = record.messageId
)
```

`:114` 处已有的 `val contact = contactId?.let { ... }` 与此处复用同一个变量，避免重复查库（合并为一次 `findById`）。
**不新增 `@Transactional`，不新增任何写操作。**

### T3 · `AutoMailReplyService` 传入 effectiveContact（I-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`

`:505` 改为：

```kotlin
val decision = groundedAutoReplyDecisionService.decide(
    inboundText = cleanedBody,
    inboundSubject = received.subject,
    contact = effectiveContact,
    currentInboundMessageId = received.messageId
)
```

用 `effectiveContact`（`:291`）而非 `contact`（`:79`）：前者已经过 `applyPromotionAndStatus`，`currentIndexLevel` 可能已升级，而 `AiReplyContextService.loadProfile()` 按 `contact.currentIndexLevel` 选 ES 层级。用旧对象会查错层。

该服务不注入新依赖（上下文构造在 `decide()` 内部完成，I-1）。

### T4 · 修复被打断的测试断言

文件：`GroundedAutoReplyDecisionServiceTest.kt`、`AutoReplyPreviewServiceTest.kt`、`AutoMailReplyServiceTest.kt`、`AiReplyDraftServiceTest.kt`

按「现状审计 → 测试面」表逐项修复。`stubGenerate()` 的第 5、6 位匹配器从 `Mockito.isNull()` 改为 `Mockito.any()`。

### T5 · 新增回归断言（I-1, I-2, I-3）

文件：`GroundedAutoReplyDecisionServiceTest.kt`（不新建测试类，避免文件数超限）

新增 3 个测试：

1. `decide passes real research sufficiency instead of warning absence`
   构造一个 `AiReplyContextService` mock 返回 `researchProfileSufficient = false` 且 `contextWarnings = ["EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"]`，用 `ArgumentCaptor` 捕获 `generate()` 的第 10 位实参，断言为 `false`。
2. `decide with null contact fails closed`
   `decide(text, subject, contact = null)`，捕获 `generate()` 实参，断言 `expertProfile == ""`、`researchProfileSufficient == false`、`contextWarnings` 含 `"EXPERT_PROFILE_NOT_FOUND"`，且 `aiReplyContextService.build()` **未被调用**。
3. `decide injects training knowledge through context service`
   stub `aiTrainingQaService.buildKnowledgeContext(any())` 返回 `"KNOWLEDGE-MARKER"`，断言该值作为 `trainingKnowledge` 实参传给 `aiReplyContextService.build()`（用 `ArgumentCaptor` 捕获第 4 位）。

## 变更文件清单

| # | 文件 | 改动类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | 修改 | 构造器 +3 依赖；`decide()` +2 参数；新增 `buildAutoReplyContext()` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` | 修改 | `:111` 传 contact 与 messageId；合并重复 `findById` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | `:505` 传 `effectiveContact` 与 `received.messageId` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | 修改 | 修复 stub/构造器/11 处调用；新增 3 个回归测试 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt` | 修改 | 修复 3 处 decide 匹配器 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | 修复 3 处 decide 匹配器 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | 修复 `:925-929` 构造器与调用 |

合计 7 个文件，1 个子系统（mail）。无新增数据字段，无迁移。

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下命令可原样复制执行。
> 来源：项目根 `CLAUDE.md`「Commands」章节与「项目元信息」的 `test_command` / `build_command`。

```bash
# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=GroundedAutoReplyDecisionServiceTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=AutoReplyPreviewServiceTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=AutoMailReplyServiceTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=AiReplyDraftServiceTest

# 单个测试方法（定位失败用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=GroundedAutoReplyDecisionServiceTest#'decide with null contact fails closed'

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`（Skipped 允许非 0，仅限 `@EnabledIfSystemProperty` 门控的 `FlywayMigrationIntegrationTest`）。

> 本计划不改前端文件，故不需要 `node --test` 单跑门禁。`mvn test` 的 `test` phase 会顺带执行
> `node --test src/test/js/*.test.js`（`pom.xml:188-203`，`skipNodeTests` 在 `pom.xml:19-25` 未定义故不跳过），
> 这是既有行为，本计划不依赖它。（来源：K-js-test-invocation-surface）

## 验收标准

- **I-1**：`grep -n "aiReplyContextService.build\|buildKnowledgeContext" GroundedAutoReplyDecisionService.kt` 各恰好 1 处，且都在 `buildAutoReplyContext()` 内。全文无手写 profile 拼接字符串。
- **I-2**：T5 测试 1 通过 —— `generate()` 收到的 `researchProfileSufficient` 等于 `AiReplyContext` 的值，而非 `!contextWarnings.contains(...)` 的推断值。额外断言：当 `contextWarnings` 为空但 `AiReplyContext.researchProfileSufficient = false` 时，传入值仍为 `false`（证明未走默认表达式）。
- **I-3**：T5 测试 2 通过 —— null contact 时 `researchProfileSufficient == false`、warnings 含 `EXPERT_PROFILE_NOT_FOUND`、`aiReplyContextService.build()` 零调用。
- **I-4**：`grep -n "Transactional\|\.save(\|\.send(" AutoReplyPreviewService.kt` 结果为空。
- **I-5**：`grep -rn "\.decide(" src/main --include=*.kt` 恰好返回 2 行，且分别位于 `AutoReplyPreviewService.kt` 与 `AutoMailReplyService.kt`。
- **IP-1 集成**：T5 测试 3 通过。
- **IP-4 回归**：`grep -n "const val" GroundedAutoReplyDecisionService.kt` 返回的 reason 常量集合与改动前 `git show 4583525:...` 逐字一致（7 个）。
- **回归**：执行「验证命令」节的全量测试命令通过；执行构建命令通过；`git diff --check` 无输出。

## 人工验收清单

### A-1: 研究匹配类来信在两侧结论一致（覆盖：需求 1，I-2，IP-2）

- 前置条件：
  1. 挑一个 `expert_contact`，其 `orcid_id` 在 ES 中**查不到画像**（或画像的 `researchFields` / `keyword` / `disciplineCategory` / `recentWorkTitles` 四个字段全为空）。
  2. 为该联系人造一条 `inbound_mail_processing` 记录，`body` 含研究匹配类问题，例如：
     `Does my research on solid-state electrolytes actually match what your partner enterprises need?`
  3. 确认该联系人已有 `direction='OUTBOUND' AND mail_type='INTRODUCTION'` 的 `mail_record`（否则会被 `INTRODUCTION_NOT_SENT` 标记，不影响本项但会干扰读数）。
- 操作步骤：
  1. 打开该来信的详情页，点「生成自动回复预览」。
  2. 记录返回的 `previewKind` 与 `reason`。
  3. 在同一详情页的可信回复工作台里，找到对应那条诉求，记录其 grounding 状态。
- 预期结果：
  - 预览的 `previewKind` 为 `QA_GAP`，`reason` 为 `QA_GROUNDING_GAP`。
  - 工作台该诉求显示为 `UNSUPPORTED`（无依据）。
  - **两侧一致**。改动前该场景预览会返回 `QA_AUTO_REPLIED`，工作台仍是 `UNSUPPORTED`。

### A-2: 训练知识对自动路生效（覆盖：需求 2，I-1，IP-1）

- 前置条件：挑一条自动路当前能走通（预览返回 `QA_AUTO_REPLIED`）的来信。
- 操作步骤：
  1. 记录当前预览正文全文。
  2. 进「AI 训练」→ QA 条目 Tab，新增一条与该来信主题强相关的训练条目（关键词要能被 `buildKnowledgeContext` 的归一化匹配命中）。
  3. 回到该来信详情页，重新点「生成自动回复预览」。
- 预期结果：新预览正文与步骤 1 记录的正文**不同**，且能看出新训练条目的影响。改动前两次正文完全相同。

### A-3: 无联系人的来信 fail-closed（覆盖：需求 3，I-3）

- 前置条件：一条 `inbound_mail_processing` 记录，`expert_contact_id` 为 `NULL`（未匹配联系人的来信，`reasonType='UNMATCHED_CONTACT'`）。
- 操作步骤：打开该记录详情，点「生成自动回复预览」。
- 预期结果：
  - 页面正常返回，不抛 500、不显示"预览失败"。
  - `previewKind` 不为 `QA_AUTO_REPLIED`（fail-closed）。
  - `wouldBeBlockedBy` 中仍能看到既有标记（该记录无 contact，按现有逻辑只会有 `RECIPIENT_UNSUBSCRIBED` / `ACCOUNT_AUTO_SEND_DISABLED` 两类）。

### A-4: 回归 —— 预览的反事实性质未被破坏（覆盖：must-NOT-change 第 4 条，I-4，X-2）

- 前置条件：挑一个 `expert_contact.auto_reply_enabled = 0` 或 `current_status = 'MANUAL_HANDOFF'` 的联系人的来信。
- 操作步骤：
  1. 点「生成自动回复预览」。
  2. 观察正文区与阻断标记区。
- 预期结果：
  - 正文区**仍然显示完整回复内容**（不因关闭自动回复而隐藏）。
  - 阻断标记区显示 `AUTO_REPLY_DISABLED` 或 `MANUAL_HANDOFF_STATUS`。
  - 连续点击 3 次预览，数据库中该联系人的 `current_status`、`mail_record` 行数、`inbound_mail_processing.process_status` 全部不变。

### A-5: 回归 —— 已能自动回复的来信仍能自动回复（覆盖：must-NOT-change 第 2/3 条，IP-4）

- 前置条件：挑一条画像完整（ES 中 `researchFields` 非空）、且改动前预览返回 `QA_AUTO_REPLIED` 的来信。
- 操作步骤：点「生成自动回复预览」。
- 预期结果：`previewKind` 仍为 `QA_AUTO_REPLIED`，`matchedRuleIds` 与改动前一致（逐个 ID 核对），正文非空。

### A-6: 回归 —— 会议邀请与人工转交分支未受影响（覆盖：must-NOT-change 第 5 条）

- 前置条件：两条来信，一条命中 `MEETING_REQUESTED` 意图，一条命中 `ASK_FUNDING` 意图。
- 操作步骤：分别点「生成自动回复预览」。
- 预期结果：
  - 前者 `previewKind` 为 `MEETING_INVITATION` 或 `MEETING_ALREADY_SENT`，正文为会议邀请模板渲染结果。
  - 后者 `previewKind` 为 `MANUAL_HANDOFF`，`reason` 为 `HANDLE_RISKY_QUESTION`。
  - 两者均**未**进入 QA 分支（说明 `when(classification.autoAction)` 的分支顺序未变）。
