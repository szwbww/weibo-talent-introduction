# 03b sourceVersion 三分：把非事实性上下文降级为提示

> 本计划由 create-p 生成。所有计数与全称判断均附 grep 回执（K-plan-quantified-claims-need-grep-receipts）。
> **前置：`workbench-repair-03a` 必须先落地并通过验收。** 本计划复用 03a 建立的 per-request evidence 框架。

## 需求描述

**Observable outcome**

1. 运营在「AI 训练」页编辑/新增/禁用一条命中当前来信的训练知识后，回到已打开的可信化回复台，**已锁定的回答不再被全部清空**；受影响条目上出现「本条在旧训练知识下生成」提示。
2. 给同一位专家发出一封邮件、或该专家又来一封新信之后，回复台**不再整体重置**，改为出现同类的条目级提示。
3. 提示区提供「重新生成受影响条目」按钮，一次点击重跑全部被标记的条目，无需逐条点。
4. 专家画像不足（`researchProfileSufficient` 为 false）或画像内容变化时，**只有研究匹配类条目**失效，其余条目保留。

**What must NOT change**

1. 来信正文（`inboundText`）、收件账号、messageId、subject、contactId 任一变化时，**仍然全量重置**——那是「换了一封信」，requestKey 必须全变。
2. 研究匹配意图的硬门禁：`resolveIntentEvidence`（AiReplyIntentCatalog.kt:562-575、:705-712）在 `intent.requiresProfile && !profileSufficient` 时仍判 `MISSING`，条目仍转 UNSUPPORTED。**不得因降级而放行。**
3. `AiReplyContext.profileText`（画像 + 训练知识的拼接串）仍按原样进入 prompt——降级只改**失效判定**，不改喂给 LLM 的内容。
4. `mailHistory` 的构造规则不变：仍只取真实入站与 SENT 出站、仍排除当前入站、仍 `takeLast(8)` + 5000 字符预算（K-ai-reply-history-continuity-not-authority）。**本计划不改 `AiReplyContextBuilder.kt`。**
5. 自动回复路（`GroundedAutoReplyDecisionService`）的行为不变——它有独立的上下文口径问题，属另一条线。
6. 03a 建立的 per-request 证据语义不变：换绑仍失效两端，聚合值仍是整份草稿指纹。

**Out of scope**

- 让 `mailHistory` 窗口对当前 inbound 稳定（改 `takeLast(8)` 的取法）——更根本但会波及自动回复路共用的 `AiReplyContextBuilder`，**本轮不做**。
- 自动回复路不建 context 的口径分叉——独立议题。
- 请求条目切分（`QaRequestExtractor`）。
- `FULL_DRAFT` 分支（03a 现状审计 C-6 已记录为观察项）。

## 关键不变量

### Invariant I-1: sourceVersion 三分，只有 identity 部分进 requestKey
- Rule: 现有 `sourceVersion()`（TrustReplyWorkbenchService.kt:1441-1465）的 10 个成分按下表重新归位，**且只有 identity 组进 `requestKey()`（:1875-1888）**：

  | 成分 | 归位 |
  |---|---|
  | `source.sourceType.name`、`source.sourceId`、`contactId`、`messageId`、`subject`、`senderAccountCode`、`sha256Hex(inboundText)` | **identity** → 进 requestKey |
  | `sha256Hex(expertProfileText)`、`researchProfileSufficient` | **evidence** → 只进 `requiresResearchContext == true` 条目的 per-request evidence |
  | `sha256Hex(trainingKnowledgeText)`、`sha256Hex(mailHistory)` | **context** → 只产生提示，不进任何 requestKey / versionId |

- Applies to: `sourceVersion()`（:1441-1465）、`resolveWithContact`（:1395-1438）、`requestKey()`（:1875-1888）、03a 新增的 `requestEvidenceVersion(...)`。
- Violation consequence: 把 context 组留在 requestKey 里 → 本计划的用户价值归零；把 evidence 组也降级 → 违反 must-NOT-change 2，画像不足时研究匹配条目可能带着旧证据被发出去。
- 来源: K-ai-reply-history-continuity-not-authority（history 非事实权威）+ K-research-fit-dual-evidence（画像是研究匹配的证据侧）

### Invariant I-2: 画像只影响研究匹配条目
- Rule: `sha256Hex(expertProfileText)` 与 `researchProfileSufficient` **只**混入 `RequestFactItem.requiresResearchContext == true`（AiReplyDraftService.kt:354）的条目的 per-request evidence；其余条目的 evidence 输入**逐字不变**（仍只有 03a 的 requestKey + factRuleIds + 规则快照）。
- Applies to: 03a 的 `requestEvidenceVersion(...)`。
- Violation consequence: 混进全部条目 → enrichment 更新画像时仍然全量失效，等于没改。
- 来源: original（依据：`requiresProfile = true` 的意图**全仓只有 1 个**，见现状审计 D-4）

### Invariant I-3: 画像身份必须用内容哈希，不能只用布尔
- Rule: 研究匹配条目的 evidence 必须同时混入 `sha256Hex(expertProfileText)` **与** `researchProfileSufficient`。只混布尔不够——`isResearchSufficient`（AiReplyContextService.kt:83-89）只判「researchFields / keyword / disciplineCategory / recentWorkTitles 任一非空」，画像新增论文后布尔不变但内容已变。
- Applies to: 03a 的 `requestEvidenceVersion(...)` 的研究匹配分支。
- Violation consequence: enrichment 补了新论文，研究匹配答案已过时却仍被判有效。
- 来源: original

### Invariant I-4: 降级必须同时提供条目级提示与一键重跑
- Rule: context 组变化时，服务端返回 `contextVersion` 与受影响条目列表；前端必须 (a) 在**每个**受影响条目卡片上渲染提示，(b) 在提示区提供「重新生成受影响条目」按钮一次重跑全部被标记条目。**两者缺一则本计划不得上线**——只提示不给动作等于把风险转嫁给运营。
- Applies to: `trust-reply-workbench.js` 的渲染与状态区。
- Violation consequence: 运营为纠错而改训练知识，却继续把旧知识生成的正文发出去。
- 来源: original（2026-08-19 决策：降级的两个前置条件）

### Invariant I-5: profileText 进 prompt 的内容不变
- Rule: `AiReplyContext` 只**新增**字段（`expertProfileText`、`trainingKnowledgeText`），既有 `profileText`（两者拼接）保持不变且继续被所有 prompt 路径使用。**禁止**改 `profileText` 的构造或删除它。
- Applies to: `AiReplyContextService.build`（:28-56）；5 个调用点（现状审计 D-2）。
- Violation consequence: 改了喂给 LLM 的内容 → 生成质量变化被误当作本计划的回归。
- 来源: original（must-NOT-change 3）

### Invariant I-6: contextVersion 不进任何身份哈希
- Rule: `contextVersion` **不得**出现在 `requestKey()`、`versionId()`、`requestEvidenceVersion()`、`aggregateEvidenceVersion()` 的任一输入中。它只作为响应字段和提示依据。
- Applies to: 上述 4 个函数。
- Violation consequence: 等于没降级。
- 来源: original

## 样式契约

### S-1: 条目级「上下文已变化」提示
- 复用：复用 03a 已引入的 `class="muted"` 提示片段形态（同一位置、同一 class）。执行前跑
  `grep -n 'data-role="item-evidence-stale"' src/main/resources/static/trust-reply-workbench.js`
  确认 03a 的片段存在，本计划在其**旁边**并列输出，不改它。
- 新增：**无新 class、无新 CSS 规则**。
- DOM 结构：仅当该条目被标记为 context-stale 时，在 03a 的提示片段之后追加**这一段，逐字**：
  ```
  <span class="muted" data-role="item-context-stale">本条在旧训练知识/对话历史下生成</span>
  ```
- 禁止项：inline style；任何新 class；任何 `styles.css` 改动。

### S-2: 「重新生成受影响条目」按钮
- 复用：复用工作台既有按钮类 `class="button small secondary"`。执行前跑
  `grep -n 'class="button small secondary"' src/main/resources/static/trust-reply-workbench.js`
  取既有使用点（例如「+ 添加事实」按钮，:1612 输出）作为形态基准，**只引用不修改**其 CSS。
- 新增：**无新 class、无新 CSS 规则**。
- DOM 结构：在状态区（`data-role="status"`，:1712 / :1737 输出的 `.ai-reply-feedback` 内）末尾追加**这一段，逐字**：
  ```
  <button type="button" class="button small secondary" data-action="regenerate-context-stale">重新生成受影响条目</button>
  ```
  仅当存在 ≥1 个 context-stale 条目时输出；无则整段不输出。
- 禁止项：同 S-1。

## 现状审计

### D-1. `sourceVersion()` 的 10 个成分（逐字现状）

```
$ sed -n '1441,1465p' src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
    private fun sourceVersion(
        source: TrustReplySourceRef,
        contactId: Long,
        messageId: String?,
        subject: String?,
        senderAccountCode: String?,
        inboundText: String,
        mailHistory: String,
        profileText: String,
        researchProfileSufficient: Boolean
    ): String {
        val canonical = listOf(
            source.sourceType.name,
            source.sourceId.toString(),
            contactId.toString(),
            messageId.orEmpty(),
            subject.orEmpty(),
            senderAccountCode.orEmpty(),
            sha256Hex(inboundText),
            sha256Hex(mailHistory),
            sha256Hex(profileText),
            researchProfileSufficient.toString()
        ).joinToString(" ")
        return sha256Hex(canonical)
    }
```

**关键**：其中的 `profileText` 是**画像与训练知识的拼接串**（见 D-3），因此训练知识变化会直接改 sourceVersion。

`sourceVersion` 进 `requestKey`：

```
$ sed -n '1875,1888p' .../TrustReplyWorkbenchService.kt
        fun requestKey(
            sourceVersion: String,
            index: Int,
            requestText: String,
            intentKeys: List<String>
        ): String {
            ...
        }
```

### D-2. `AiReplyContext` 的构造与 5 个调用点

```
$ grep -rn "aiReplyContextService.build(" --include=*.kt src/main
llm/controller/AiTrainingController.kt:220
llm/service/TrustReplyWorkbenchService.kt:1408
mail/controller/UnmatchedInboundMailController.kt:347
mail/service/PendingMailOperationService.kt:535
mail/service/GroundedAutoReplyDecisionService.kt:216
```

**5 个调用点**。本计划对 `AiReplyContext` 只做**加字段**（I-5），因此这 5 个调用点**全部无需修改**——只有 `TrustReplyWorkbenchService.kt:1408` 会消费新字段。

### D-3. 训练知识被拼进 `profileText`（本计划的核心动因）

```
$ sed -n '40,44p' src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt
        val profileText = contextBuilder.appendKnowledgeToProfile(
            contextBuilder.buildExpertProfile(contact, profile),
            trainingKnowledge
        )
        val mailHistory = contextBuilder.buildMailHistory(records, currentInboundMessageId)
```

`trainingKnowledge` 来源（`TrustReplyWorkbenchService.kt:1407`）：`aiTrainingQaService.buildKnowledgeContext(inboundText)`。

```
$ sed -n '85,110p' src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaService.kt
    fun buildKnowledgeContext(inboundText: String): String {
        ...
        return repository.findAllByOrderByCreatedAtDesc()
            .asSequence()
            .filter { it.enabled }
            ... 按关键词命中打分 ...
            .sortedWith(compareByDescending<ScoredQa> { it.score }.thenBy { it.id })
            .take(MAX_KNOWLEDGE_ROWS)
            .map { it.formatted }
            .joinToString("\n\n")
            .take(MAX_KNOWLEDGE_CHARS)
    }
$ grep -n "MAX_KNOWLEDGE_ROWS\|MAX_KNOWLEDGE_CHARS" .../AiTrainingQaService.kt
106:            .take(MAX_KNOWLEDGE_ROWS)
109:            .take(MAX_KNOWLEDGE_CHARS)
156:        private const val MAX_KNOWLEDGE_ROWS = 6
157:        private const val MAX_KNOWLEDGE_CHARS = 6000
```

→ 编辑/新增/禁用**任一条命中本信的训练知识**都会改 `profileText`；且 `take(6)` 的截断会让新增一条高分知识把尾部那条**挤掉**，即使那条没被碰过。

`appendKnowledgeToProfile` 的拼接方式（`AiReplyContextBuilder.kt:98-110`）是 `profile + "\n\nTraining knowledge base:\n" + knowledgeContext`——两段本来就是可分离的，因此新增字段无需改 builder。

### D-4. `requiresProfile` 的意图**全仓只有 1 个**

```
$ grep -rn "requiresProfile" --include=*.kt src/main
llm/service/AiReplyContextService.kt:26          AiReplyIntentCatalog.matchIntents(text).any { it.requiresProfile }
llm/service/AiReplyIntentCatalog.kt:17           val requiresProfile: Boolean = false        （默认值）
llm/service/AiReplyIntentCatalog.kt:128          requiresProfile = true                       ← 唯一置 true 处（research-fit）
llm/service/AiReplyIntentCatalog.kt:304          （注释：requiresProfile stays at the default false）
llm/service/AiReplyIntentCatalog.kt:567          if (intent.requiresProfile && !profileSufficient) {
llm/service/AiReplyIntentCatalog.kt:588          requiresResearchContext = intent.requiresProfile
llm/service/AiReplyIntentCatalog.kt:701          requiresResearchContext = intent.requiresProfile
llm/service/AiReplyIntentCatalog.kt:705          if (intent.requiresProfile && !profileSufficient) {
llm/service/AiReplyIntentCatalog.kt:763          requiresResearchContext = intent.requiresProfile
llm/service/QaFactSelectionService.kt:392        val isResearch = matchedIntents.any { it.requiresProfile }
```

**10 行**，其中 `requiresProfile = true` **只有 :128 一处**（research-fit 意图）。
该标记已被逐条透传到 `RequestFactItem.requiresResearchContext`（`AiReplyDraftService.kt:354`）与 `RequestIntentCoverage.requiresResearchContext`（`AiReplyIntentCatalog.kt:27`），因此 I-2 的分支判断**不需要新增任何数据通道**。

硬门禁位置（must-NOT-change 2 要守住的）：`AiReplyIntentCatalog.kt:567` 与 `:705`，两处均为 `if (intent.requiresProfile && !profileSufficient) → status = "MISSING"`。

### D-5. `isResearchSufficient` 是粗布尔（I-3 的依据）

```
$ sed -n '83,89p' src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt
    private fun isResearchSufficient(profile: ExpertProfile?): Boolean {
        if (profile == null) return false
        return !profile.researchFields.isNullOrBlank() ||
            !profile.keyword.isNullOrBlank() ||
            !profile.disciplineCategory.isNullOrBlank() ||
            !profile.recentWorkTitles.isNullOrEmpty()
    }
```

只判「任一非空」——画像新增论文后布尔不变。故 I-3 要求同时混入内容哈希。

### D-6. `mailHistory` 是滑动窗口（发一封信就动）

```
$ sed -n '61,65p' src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt
        val sorted = filtered.sortedWith(
            compareBy(effectiveTime)
                .thenBy { it.id ?: Long.MIN_VALUE }
        )
        val recent = sorted.takeLast(8)
```

`records` 来自 `mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`（`TrustReplyWorkbenchService.kt:1406`）。
→ 给该专家新增任意一条 mail_record（IMAP 收信、或运营在别的 tab 发一封材料提醒）都会移动窗口。

**无 wall clock**（这点已实测，保证不会自己烂掉）：

```
$ grep -n "now()\|Instant\|LocalDateTime.now" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt ; echo exit=$?
exit=1
```

### D-7. `ResolvedTrustReplySource` 现状

```
$ sed -n '47,59p' .../TrustReplyWorkbenchService.kt
data class ResolvedTrustReplySource(
    val source: TrustReplySourceRef,
    val contact: ExpertContact,
    val inboundText: String,
    val subject: String?,
    val messageId: String?,
    val senderAccountCode: String?,
    val profileText: String,
    val mailHistory: String,
    val contextWarnings: List<String>,
    val researchProfileSufficient: Boolean,
    val sourceVersion: String
)
```

### Interaction points

| # | 写入侧 | 读取侧 | 计划覆盖 |
|---|---|---|---|
| IP-1 | `AiReplyContextService.build`（:28-56）产出画像/训练知识两段 | `TrustReplyWorkbenchService.resolveWithContact`（:1395-1438） | I-5；T1/T2 |
| IP-2 | `sourceVersion()`（identity 组） | `requestKey()`（:1875-1888）→ 矩阵、saved state、locked item | I-1；T3 |
| IP-3 | 画像哈希 + `researchProfileSufficient` | 03a 的 `requestEvidenceVersion`（仅 requiresResearchContext 条目） | I-2/I-3；T4 |
| IP-4 | `contextVersion`（训练知识 + mailHistory） | bootstrap/adjust 响应 → 前端条目级提示 | I-4/I-6；T5/T6 |
| IP-5 | 运营在「AI 训练」页写 `ai_training_qa` | `buildKnowledgeContext`（AiTrainingQaService.kt:85-110）→ contextVersion | I-4；A-1 |
| IP-6 | 任意 mail_record writer（K-ai-reply-history-continuity-not-authority 列了 6 个：ManualOutreachTxHelper / MeetingScheduleService / ManualExpertMailService / ManualReplySendAttemptService / AutoMailReplyService / MailboxService） | `buildMailHistory` → contextVersion | I-4；A-2 |

## 实现方案

> **研究检查点（动工前必做）**：
> 1. `grep -rn "sourceVersion" --include=*.kt src/test | wc -l` 与 `grep -rn "sourceVersion" src/test/js/*.js | wc -l` —— 记录基线数字（2026-08-19 实测：Kotlin 测试 137 行、JS 测试与前端合计 124 行）。若改动后失败数远超预期，说明有未识别的读路径，停下来报告而不是逐个改测试。
> 2. 确认 03a 已落地：`grep -n "requestEvidenceVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 有命中。若无，**本计划不得开工**。

### T1 — `AiReplyContext` 加两个字段（I-5）

文件：`AiReplyContextService.kt`

- `AiReplyContext`（:11-16）**追加**两个带默认值的字段，既有 5 个调用点因此不需要改（D-2）：
  - `val expertProfileText: String = ""` —— `contextBuilder.buildExpertProfile(contact, profile)` 的**原始返回值**（不含训练知识）
  - `val trainingKnowledgeText: String = ""` —— 传入的 `trainingKnowledge` 原值
- `build`（:28-56）在 :40-43 处把 `buildExpertProfile(...)` 的结果先存入局部变量再传给 `appendKnowledgeToProfile`，并把两段分别填入新字段。
- **`profileText` 的构造与取值逐字不变**（I-5）。
- **不改 `AiReplyContextBuilder.kt`**（must-NOT-change 4）。

### T2 — `ResolvedTrustReplySource` 携带三组版本（I-1）

文件：`TrustReplyWorkbenchService.kt`

- `ResolvedTrustReplySource`（:47-59）追加：
  - `val expertProfileText: String = ""`
  - `val trainingKnowledgeText: String = ""`
  - `val contextVersion: String = ""`
  （`sourceVersion` 字段名**保留**，语义收窄为 identity；不改名以免波及 D-2 之外的读点。）
- `resolveWithContact`（:1395-1438）从 `context` 取新字段并填入。

### T3 — `sourceVersion()` 收窄为 identity，新增 `contextVersion()`（I-1 / I-6）

文件：`TrustReplyWorkbenchService.kt`

- `sourceVersion()`（:1441-1465）**删除** 3 个形参 `mailHistory` / `profileText` / `researchProfileSufficient` 及其对应的 3 个 canonical 成分，只保留 7 个 identity 成分。
- 新增 `private fun contextVersion(trainingKnowledgeText: String, mailHistory: String): String`，返回 `sha256Hex(listOf(sha256Hex(trainingKnowledgeText), sha256Hex(mailHistory)).joinToString(" "))`。
- `contextVersion` **不得**进 `requestKey()` / `versionId()` / `requestEvidenceVersion()` / `aggregateEvidenceVersion()`（I-6）。

### T4 — 画像只混入研究匹配条目的 evidence（I-2 / I-3）

文件：`TrustReplyWorkbenchService.kt`

- 03a 的 `requestEvidenceVersion(requestKey, factRuleIds, baseSnapshotOf)` 增加一个入参 `researchEvidence: String?`：
  - 当该条目 `RequestFactItem.requiresResearchContext == true`（`AiReplyDraftService.kt:354`）时传
    `sha256Hex(resolved.expertProfileText) + " " + resolved.researchProfileSufficient`
  - 否则传 `null`，此时哈希输入**与 03a 逐字相同**（保证非研究条目的版本值不因本计划而改变——这是 I-2 的可验证形式）。
- `resolveCanonicalSelection`（03a 的 T2）按 `selection.requestFacts` 里对应条目的 `requiresResearchContext` 决定传值。

### T5 — 响应携带 contextVersion 与受影响条目（I-4）

文件：`TrustReplyWorkbenchService.kt`

- `TrustReplyBootstrapResponse` 与 `TrustReplyAssembleResponse` 追加 `val contextVersion: String = ""`。
- `TrustReplyItemVersion` 追加 `val contextVersion: String = ""`（记录该 version 生成时的 context 指纹）；`materializeVersion` 填入。
  **该字段不进 `versionId()` 的哈希输入**（I-6）——它是观测量，不是身份。
- `restoreSavedStateWithFrame`：context 不匹配**不再**导致丢弃任何 locked item，只在返回里带上「哪些条目的 contextVersion 与当前不符」。

### T6 — 前端条目级提示与一键重跑（I-4 / S-1 / S-2）

文件：`trust-reply-workbench.js`

- `applyBootstrap`（:521 附近）记录 `state.contextVersion`；逐条比对 version 携带的 `contextVersion` 与当前值，不符则置 `request.contextStale = true`。
- 渲染：`request.contextStale === true` 时按 S-1 输出提示片段（与 03a 的 `item-evidence-stale` 并列，互不覆盖）。
- 状态区：存在 ≥1 个 `contextStale` 条目时按 S-2 输出按钮。
- `onClick`（:1995-2030 区间）新增 `regenerate-context-stale` 分支：对所有 `contextStale` 条目依次走既有的 `adjustItem` 流程（复用 :709-798 的批量生成循环，不新写一套）。
- **不得**因 context 变化调用 `resetVersions()` 或 `handleStaleGeneration`。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt` | T1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | T2–T5 |
| 3 | `src/main/resources/static/trust-reply-workbench.js` | T6 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt` | 新字段断言 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | sourceVersion 三分 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | I-2/I-3 分支用例 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 重整合比对 |
| 8 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 提示与一键重跑 |

合计 **8** 个文件（上限 10）。子系统 **2** 个（后端上下文/版本模型 / 前端工作台，上限 2）。

**明确不在清单内，执行 agent 不得改动**：`AiReplyContextBuilder.kt`（must-NOT-change 4）、`AiTrainingQaService.kt`、`AiReplyIntentCatalog.kt`（must-NOT-change 2 的门禁所在）、`GroundedAutoReplyDecisionService.kt`（must-NOT-change 5）、`AiTrainingController.kt`、`UnmatchedInboundMailController.kt`、`PendingMailOperationService.kt`、`app.js`、任何 `db/migration/*.sql`、`styles.css`、`index.html`。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。JS 测试由 `exec-maven-plugin` 的 `node-test` execution 在 `test` 阶段执行（`pom.xml:186-202`）。

```bash
# 本计划相关 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='AiReplyContextServiceTest+TrustReplyWorkbenchServiceTest+TrustReplyWorkbenchItemFlowTest+PendingMailOperationServiceTrustWorkbenchTest'

# must-NOT-change 2 / 5 的守门测试（不得修改这些文件，但必须绿）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='AiReplyIntentCatalogTest+GroundedAutoReplyDecisionServiceTest+AiTrainingQaServiceTest'

# 单个测试方法（确切过滤语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchItemFlowTest#methodName

# 本计划相关前端测试（实测可用；node v22.23.2）
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js

# 全部前端测试
node --test src/test/js/*.test.js

# 前端语法检查
node --check src/main/resources/static/trust-reply-workbench.js

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

> 注：上面第二条命令里的三个测试类名需在执行前用
> `ls src/test/kotlin/com/weibo/talentintroduction/llm/service/ src/test/kotlin/com/weibo/talentintroduction/mail/service/`
> 核对存在性；不存在的类从 `-Dtest` 里去掉并在计划的「修正记录」中注明。

通过判据：
- `mvn test`：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且 `node-test` execution 无报错。
- `node --test`：退出码 0，输出含 `# fail 0`。
- `mvn clean package`：退出码 0，`BUILD SUCCESS`。
- `git diff --check`：无输出。

来源：`CLAUDE.md` 项目元信息的 `test_command` / `build_command` 与「Commands」章节；`pom.xml:186-202`；`node --test src/test/js/trustReplyWorkbench.test.js` 于 2026-08-19 实测通过（node v22.23.2）。

## 验收标准

- **I-1**：新增单测三条——(a) 只改 `trainingKnowledgeText` 时 `sourceVersion` **不变**、`contextVersion` **变**；(b) 只改 `mailHistory` 时同上；(c) 改 `inboundText` / `messageId` / `subject` / `senderAccountCode` / `contactId` 任一时 `sourceVersion` **变**。
  `grep -n "sha256Hex(mailHistory)\|sha256Hex(profileText)" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` **无输出**（两者已移出 `sourceVersion()`）。
- **I-2**：新增单测——2 条目场景，条目 1 `requiresResearchContext = true`、条目 2 `false`。改 `expertProfileText` 后断言条目 1 的 per-request evidence **变**、条目 2 **逐字不变**（与 03a 基线值相等）。
- **I-3**：新增单测——`researchProfileSufficient` 保持 `true` 不变、只改 `expertProfileText` 内容，断言研究匹配条目的 evidence **仍然变化**。
- **I-4**：前端测试断言——context 变化后 (a) 受影响条目渲染出 `data-role="item-context-stale"`；(b) 状态区渲染出 `data-action="regenerate-context-stale"`；(c) 点击该按钮触发的 `/generations/stream` 请求数**等于** context-stale 条目数；(d) 全程 `/bootstrap` 请求数为 0 且未调用 `resetVersions`。
- **I-5**：`git diff src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt` 中 `profileText` 的赋值表达式**无语义改动**（只是把中间结果提取为局部变量）；`AiReplyContextServiceTest` 中既有的 `profileText` 断言**未修改**且通过。
- **I-6**：`grep -n "contextVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 的命中行中，**没有任何一行**位于 `requestKey()`（:1875-1888）、`versionId()`（:1890-1908）或 03a 的 `requestEvidenceVersion` / `aggregateEvidenceVersion` 函数体内。
- **S-1 / S-2**：`git diff --stat` 不含 `styles.css` / `index.html`；新增的两段 DOM 与契约代码块**逐字一致**；diff 中无新增 `style="`、无新增 class 名。
- **must-NOT-change 1**：新增单测——改 `inboundText` 后所有 `requestKey` 均变化，saved state 整体判 STALE。
- **must-NOT-change 2**：`git diff --stat` **不含** `AiReplyIntentCatalog.kt`；新增单测：`researchProfileSufficient = false` 时研究匹配条目仍为 `UNSUPPORTED`，`missingEvidenceKeys` 仍含 `"profile"`。
- **must-NOT-change 4**：`git diff --stat` **不含** `AiReplyContextBuilder.kt`。
- **must-NOT-change 5**：`git diff --stat` **不含** `GroundedAutoReplyDecisionService.kt`。
- **must-NOT-change 6**：03a 的验收标准 I-1/I-2/I-3 全部重跑通过。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 编辑训练知识后，已锁定回答不再全部清空
- 前置条件：找一封在「摘要与事实」页有 **≥2 条摘要**的来信；先到「AI 训练」页确认存在**至少 1 条**关键词能命中这封来信的训练知识（没有就新建一条，关键词取来信里出现的词）。
- 操作步骤：
  1. 在回复台对全部摘要生成并锁定回答，进度满格。
  2. **不要关闭回复台标签页**，另开一个标签进「AI 训练」页，修改刚才那条训练知识的正文（改几个字即可），保存。
  3. 回到回复台标签，点任意一条摘要的「重新生成」（或触发一次会打服务端的操作）。
- 预期结果：
  - **不弹**「来源或事实已变化，确认刷新工作台并重新生成？」。
  - 所有已锁定回答**仍在**。
  - 受影响条目上出现「本条在旧训练知识/对话历史下生成」。
  - 状态区出现「重新生成受影响条目」按钮。
- 覆盖：I-1、I-4、S-1、S-2、IP-5、需求描述 observable outcome 1 与 3

### A-2: 给同一专家发一封信后，回复台不再整体重置
- 前置条件：某封来信的回复台已锁定 ≥2 条回答，保持页面打开。
- 操作步骤：另开标签，在「专家列表」里给**同一位专家**发送任意一封邮件（或等 IMAP 轮询收到该专家的新信）；回到回复台点任意一条的「重新生成」。
- 预期结果：同 A-1——不整体重置，出现条目级提示与一键重跑按钮。
- 覆盖：I-1、I-4、IP-6、observable outcome 2

### A-3: 一键重跑
- 前置条件：完成 A-1 或 A-2，状态区已出现「重新生成受影响条目」按钮，且被标记的条目 **≥2 条**。
- 操作步骤：点一次「重新生成受影响条目」。
- 预期结果：被标记的条目**全部**依次重新生成（进度条推进），无需逐条点；重跑完成后所有「本条在旧训练知识/对话历史下生成」提示消失；**未被标记的条目内容逐字不变**。
- 覆盖：I-4、observable outcome 3

### A-4: 画像变化只影响研究匹配条目
- 前置条件：一封来信里**同时**包含一个研究匹配类问题（例如 "does my research fall within the programme scope?"）和一个非研究类问题（例如 "what is the funding amount?"），两条都已锁定回答。
- 操作步骤：对该专家触发一次 enrichment（「专家列表」→ 该专家 → 补充学术信息），使 ES 画像内容变化；回到回复台刷新。
- 预期结果：**只有**研究匹配那一条被清空并要求重新生成；资助金额那一条**原文保留**。
- 覆盖：I-2、I-3、observable outcome 4

### A-5: 回归 — 来信正文变化仍然全量重置
- 前置条件：某封来信的回复台已锁定 ≥2 条回答。
- 操作步骤：在「AI 训练」页把这封来信的正文改掉一句话（或换一封来信重走），回到回复台刷新。
- 预期结果：**全量重置**——所有回答清空，出现整体「来源或事实已变化」提示。这是正确行为，不得被降级。
- 覆盖：must-NOT-change 第 1 条

### A-6: 回归 — 画像不足时研究匹配仍判 UNSUPPORTED（安全底线）
- 前置条件：找一位 ES 里**没有**研究方向/关键词/学科/近期论文的专家（或临时构造一个 ORCID 无画像的联系人）；给他发一封含研究匹配类问题的来信。
- 操作步骤：在回复台打开这封来信，查看研究匹配那一条摘要的状态。
- 预期结果：该条目状态为 **UNSUPPORTED**（依据不足，需人工），缺失依据里含「画像」相关项；**不得**因本计划的降级而变成可自动作答。
- 覆盖：must-NOT-change 第 2 条（这是本计划最重要的安全回归项）

### A-7: 回归 — 生成质量未变（prompt 内容不变）
- 前置条件：部署前对某封来信的某一条摘要生成一次回答，**逐字保存**该文本。
- 操作步骤：部署后对同一封来信、同一条摘要、同一模型重新生成一次。
- 预期结果：生成结果在**内容与风格上无系统性变化**（LLM 有随机性，不要求逐字相同；判据是不出现「引用的事实来源变了」「训练知识没被用上」这类可辨识差异）。
- 覆盖：must-NOT-change 第 3 条、I-5

### A-8: 回归 — 自动回复路未受影响
- 前置条件：确认 `LLM_AUTO_REPLY_ENABLED` 的当前取值并记录（本项**不要**为验收而打开它）。
- 操作步骤：在「邮件监控」或自动回复预览入口，对一封来信查看自动回复决策结果。
- 预期结果：判定结果与部署前**一致**。
- 覆盖：must-NOT-change 第 5 条

### A-9: 回归 — 03a 的局部失效仍然生效
- 操作步骤：重跑 `workbench-repair-03a` 的人工验收 A-1、A-4、A-5 三条。
- 预期结果：三条全部仍然通过。
- 覆盖：must-NOT-change 第 6 条
