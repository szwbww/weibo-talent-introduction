# 02 可信化回复台正文按 claim 分段

> 本计划由 create-p 生成。所有计数与全称判断均附 grep 回执（K-plan-quantified-claims-need-grep-receipts）。

## 需求描述

**Observable outcome**

1. 可信化回复台「服务端整合」产出的正文，**同一条摘要内的每条 claim 各占一段**，段间空一行；整封信不再是尊语之后一整块文字。
2. 「采用到人工回复」后发出的邮件，收件人看到的段落与预览一致（纯文本 `\n\n`，HTML `<p>` 或 `<br><br>`）。

**What must NOT change**

1. `AiReplyPointByPointComposer` 的行为契约：frame 块与条目之间仍以单个空行分隔；**每个非 OMIT 的 `answerText` 仍按原顺序逐字出现且恰好一次**（K-locked-item-assembly-list-not-set）。composer 源码不改。
2. claim 的**内容**、`intentKey`、`sourceRuleIds`、顺序不变；`mail_record_qa_rule` 审计集合不变。
3. 信任门禁与高风险校验行为不变：`validateGroundedTrustBoundary` 的 `finalBody`（TrustReplyWorkbenchService.kt:1153）仍由 claims 以**单空格**拼出，本计划不改这一处。
4. `validateNoDuplicateClaims`（:1178-1186）的重复判定结果不变。
5. `ACKNOWLEDGE_PENDING`、`ANSWER_FROM_OPERATOR_INPUT`、`OMIT` 三种 handling 的 `answerText` 规范化方式不变（它们 `claims` 恒空）。
6. UnsupportedAnswerIndex 的归档内容不变。

**Out of scope**

- 段落数上限与相邻 claim 合并（原方案 2）——先上本计划观察实际 claim 数分布再决定。
- 请求条目切分（`QaRequestExtractor`）——归入 03b 之后的独立议题。
- `FULL_DRAFT` 分支的 evidence 口径分叉（见现状审计 B-5，工作台不可达，本计划不动）。
- 版本模型粒度（03a/03b）、页签选择器（01）。

## 关键不变量

### Invariant I-1: 段落分隔符只有一处权威定义
- Rule: 条目内 claim 的拼接分隔符必须是**单一常量** `AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR = "\n\n"`，由 3 个生产位置和 1 个测试镜像共同引用，**禁止任何一处写字面量**。
- Applies to:
  1. `AiReplyDraftService.kt:1552`（生成侧：`itemAnswers` 的 `answerText`）
  2. `TrustReplyWorkbenchService.kt:1286`（校验侧：`canonicalizeClaims` 的相等性断言）
  3. `TrustReplyWorkbenchService.kt:1331`（物化侧：`materializeVersion` 的 `normalizedAnswer`，该值进 `versionId()` 哈希）
  4. `TrustReplyWorkbenchItemFlowTest.kt:1204`（测试镜像实现）
- Violation consequence: 三处任一漂移 → `answerText != canonical.join(...)` → 服务端抛 `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`（:1287），运营侧表现为「整合」按钮永远 422。
- 来源: original（起因见 K-composed-reply-order-contract 的同类"多处顺序契约必须同源"教训）

### Invariant I-2: 规范化只在版本创建时发生，composer 不参与格式化
- Rule: 段落结构必须写进 `answerText` 本身（`materializeVersion` 阶段），**不得**由 `AiReplyPointByPointComposer.composeLockedItems` 在组装时插入。composer 仍只做「frame 块 + 逐字答案，`joinToString("\n\n")`」。
- Applies to: `AiReplyPointByPointComposer.kt:34-44`（本计划**不改此文件**）。
- Violation consequence: 违反 K-locked-item-assembly-list-not-set 的「锁定后 composer 不得再次 trim 或格式化」「每个 answerText 必须逐字出现」；`AiReplyPointByPointComposerTest.kt:72-107` 的逐字/顺序/重复断言失效。
- 来源: K-locked-item-assembly-list-not-set

### Invariant I-3: 信任门禁的 finalBody 保持单空格
- Rule: `validateGroundedTrustBoundary` 里的 `finalBody = groundedSections.flatMap { it.answers }.joinToString(" ") { it.answer }`（TrustReplyWorkbenchService.kt:1153）**保持 `" "` 不变**，不引用 I-1 的常量。
- Applies to: TrustReplyWorkbenchService.kt:1153。
- Violation consequence: `AiReplyHighRiskClaimValidator.validateGroundedCandidate` 的短语族匹配（K-high-risk-phrase-family-symmetric-match）以连续文本为前提，插入换行可能让跨 claim 的短语匹配行为改变，属未评估的门禁语义变更。
- 来源: original

### Invariant I-4: 非 claim 型 handling 的规范化不变
- Rule: `OMIT → ""`、`ACKNOWLEDGE_PENDING → answerText.trim()`、`ANSWER_FROM_OPERATOR_INPUT → answerText.trim()` 三个分支（TrustReplyWorkbenchService.kt:1327-1332 的 `when`，实测 :1327 起、:1328/:1329/:1330 三分支、:1331 `else`）逐字不变；只改 `else ->` 分支。
- Applies to: `materializeVersion`（:1327-1332）。
- Violation consequence: 这三类 `claims` 恒为空（校验在 :1211/:1218/:1231），若误用 join 会把空列表拼成空串，锁定项直接失效。
- 来源: original

### Invariant I-5: 段落必须活着到达收件人
- Rule: 采用后外发必须保持 `ComposedMail(html = true)` 且 HTML 侧由 `plainTextToHtml`（未编辑分支）或 `editor.innerHTML`（已编辑分支）产出；**禁止**改成 `html = false` 纯文本外发。
- Applies to: `PendingMailOperationService.kt:271`（`html = true`）、:203-204（`plainTextToHtml(renderedText)` 分支）。本计划不改这两处，但验收必须覆盖。
- Violation consequence: Gmail/Outlook 网页版会重排纯文本，`\n\n` 段落塌成一堵墙——本计划的用户价值归零。
- 来源: K-plaintext-reply-client-reflow

## 现状审计

### 存储 A：无。本计划不新增/修改任何 DB 列、ES 字段或迁移。

```
$ grep -rn "answerText" src/main/resources/db/migration/ ; echo "grep exit=$?"
grep exit=1
```

（exit=1 即无匹配。）

`answerText` 只存在于内存 DTO、`trust_reply_workbench_state.payload_json` 与 ES `UnsupportedAnswerIndex` 文档，无 schema 约束。

### 存储 B：`trust_reply_workbench_state.payload_json`（MySQL，V83 创建）

- Schema：`src/main/resources/db/migration/V83__create_trust_reply_workbench_state.sql:13`，正文以 JSON 存于 `payload_json`，无列级约束。
- **本计划不升 `STATE_SCHEMA_VERSION`**。理由：`answerText` 改变后，存量 locked item 在 bootstrap 恢复时会在 `canonicalizeClaims` 的相等性检查（:1286）失败 → 由 `restoreSavedStateWithFrame` 的 `catch (ex: TrustReplyWorkbenchException)`（:575-587）接住，返回 `status = "STALE"`。这是**既有的优雅降级路径**，无需新增机制。
- 副作用（须在验收中确认）：升级后已存在的未完成草稿会一次性变 STALE，运营需重新生成。这是可接受的一次性代价。

### 写路径：`answerText` 的产生点（全集）

```
$ grep -rn "answerText = " --include=*.kt src/main
llm/controller/AiTrainingController.kt:339            answerText = answerText,                    （训练模拟 DTO 透传）
llm/controller/TrustReplyWorkbenchController.kt:178   answerText = locked.answerText,             （HTTP DTO 透传）
llm/controller/TrustReplyWorkbenchController.kt:209   answerText = locked.answerText,             （HTTP DTO 透传）
llm/service/UnsupportedAnswerIndexService.kt:305      answerText = values.getValue("answerText"), （ES 读回）
llm/service/UnsupportedAnswerIndexService.kt:398      answerText = version.answerText,            （ES 归档，仅 operator-directed）
llm/service/AiReplyDraftService.kt:603                answerText = candidate,                     （ACKNOWLEDGE_PENDING，claims 空）
llm/service/AiReplyDraftService.kt:729                answerText = candidate,                     （ANSWER_FROM_OPERATOR_INPUT，claims 空）
llm/service/AiReplyDraftService.kt:769                answerText = text,                          （safeAcknowledgement，claims 空）
llm/service/AiReplyDraftService.kt:1552               answerText = section.answers.joinToString(" ") { it.answer },   ← T2 改这一行
llm/service/TrustReplyWorkbenchService.kt:745         answerText = locked.answerText,             （saveState 透传）
llm/service/TrustReplyWorkbenchService.kt:981         answerText = "",                            （OMIT 快路径）
llm/service/TrustReplyWorkbenchService.kt:1024        answerText = generated.itemAnswer.answerText,（adjustItem 透传）
llm/service/TrustReplyWorkbenchService.kt:1087        answerText = locked.answerText,             （assemble 透传）
llm/service/TrustReplyWorkbenchService.kt:1347        answerText = normalizedAnswer,              （materializeVersion 物化，见下）
llm/service/TrustReplyWorkbenchService.kt:1645        answerText = answer.answerText,             （buildInitialItemVersions，FULL_DRAFT 专用）
```

共 **15 行**。

唯一由 claims 拼出的是 **AiReplyDraftService.kt:1552**，其余全是透传或 claims-空 分支。

### 写路径：`normalizedAnswer` 的产生点（唯一）

```
$ grep -n "normalizedAnswer" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
1327:        val normalizedAnswer = when (handling) {
1342:                requestKey, handling, normalizedAnswer, normalizedClaims, model, generationKind,
1347:            answerText = normalizedAnswer,
```

`materializeVersion` 是**唯一**物化点；`normalizedAnswer` 既进 `versionId()`（:1342）又成为最终 `answerText`（:1347）。

### 读路径：`answerText` 的消费者（全集，16 处，逐条判定影响）

```
$ grep -rn "\.answerText" --include=*.kt src/main
```

| 文件:行 | 用途 | 本计划影响 |
|---|---|---|
| `TrustReplyWorkbenchController.kt:178,209` | HTTP DTO 透传 | 无 |
| `TrustReplyWorkbenchService.kt:824,825,826` | `ADJUST_ITEM` 早分支的 `draftText`/`renderedDraftText`/`draftHash` | 单条目预览也带上段落（期望内） |
| `TrustReplyWorkbenchService.kt:1078,1087,1248` | `canonicalizeClaims` 入参 | **随 I-1 改** |
| `TrustReplyWorkbenchService.kt:1107` | `orderedAnswers`，喂给 composer | 内容变（带 `\n\n`），composer 逐字保留 |
| `TrustReplyWorkbenchService.kt:1180-1181` | `validateNoDuplicateClaims` | **无影响**：:1181 已做 `.replace(Regex("\\s+"), " ")`，`\n\n` 被归一 |
| `TrustReplyWorkbenchService.kt:1211,1218,1222,1231,1237,1238,1245` | 三类 claims-空 handling 的校验 | 无（I-4） |
| `TrustReplyWorkbenchService.kt:1635,1645` | `buildInitialItemVersions`（FULL_DRAFT 专用） | 无（工作台不可达，见 B-5） |
| `AiReplyDraftService.kt:742` | `containsNonLatinLetter` 语言检查 | 无（换行不是字母） |
| `AiTrainingEvaluationService.kt:93,141` | 非空判定 + `sha256Hex` | 哈希值变，但每次现算，无存量比对 |
| `UnsupportedAnswerIndexService.kt:327,398,399,415,417` | 归档文档正文与 `answerHash` | **无影响**：归档只收 `ANSWER_FROM_OPERATOR_INPUT`（`PendingMailOperationService.kt:608-611` 的 `isArchiveEligibleOperatorDirectedVersion`），该 handling `claims` 恒空 |
| `PendingMailOperationService.kt:613` | 非空判定 | 无 |

### B-5. `containsNonNaturalGroundedStructure` 不会因换行产生新误报

```
$ grep -rn "containsNonNaturalGroundedStructure" --include=*.kt src/main src/test
src/main/.../AiReplyDraftService.kt:2324:        if (AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(materialized.text)) {
src/main/.../AiReplyGroundedDraftMaterializer.kt:177:        fun containsNonNaturalGroundedStructure(text: String): Boolean {
src/test/.../AiReplyGroundedDraftMaterializerTest.kt:139: (断言)
src/test/.../AiReplyGroundedDraftMaterializerTest.kt:140: (断言)
```

**生产调用点 1 个**（:2324），作用对象是 `materialized.text` —— 那是 `composeFromPlan → assembleGroundedEmail` 的产物，**本身已经是带 `\n\n` 的多段文本**，且本计划不改它。`answerText` 从不经过该检查，因此 `NUMBERED_LIST_LINE = Regex("(?m)^\\s*\\d+\\.\\s+\\S")` 不会因 `answerText` 新增换行而多命中。

### B-6. 段落到达收件人的两条外发分支（均已验证保段落）

`app.js:10391-10406`（`send-manual-rich-reply`）：

- **未编辑分支**（`editor.innerText.trim() === adopt.renderedBaseline.trim() && editor.innerHTML === adopt.renderedBaselineHtml`，:10401-10403）→ 发送 `templateTextBody = adopt.rawTemplate`（:10405）。
  服务端 `PendingMailOperationService.kt:203-204`：`!templateTextBody.isNullOrBlank()` → `finalHtmlBody = mailContentService.plainTextToHtml(renderedText)`。
  `MailContentService.kt:10` 按 `Regex("\\n\\s*\\n")` 切段并包 `<p>`。→ **段落生效**
- **已编辑分支** → `htmlBody = editor.innerHTML`（:10394）。adopt 时用 `editor.innerText = rendered`（`app.js:9586`），contenteditable 的 `innerText` 赋值把 `\n\n` 渲染成 `<br><br>`。→ **段落生效**
- 两条分支最终都走 `ComposedMail(..., html = true, text = finalTextBody)`（`PendingMailOperationService.kt:268-273`）。

```
$ grep -rn "templateTextBody\|templateHtmlBody" src/main/resources/static/*.js
src/main/resources/static/app.js:10405:            requestBody.templateTextBody = adopt.rawTemplate;
```

前端**从不发送 `templateHtmlBody`**（唯一命中就是上面这一行），因此 `rawHtmlFromTemplate` 恒为 null，第一个分支（:201-202）在工作台路径上不可达。

### B-7. 现有 Kotlin 测试全部使用**单 claim** 条目，因此改分隔符后仍然全绿

```
$ grep -rn 'claims = listOf(AiReplyItemClaim' --include=*.kt src/test
TrustReplyWorkbenchServiceTest.kt:379   listOf(AiReplyItemClaim("general.answer", answerText, listOf(ruleId)))
TrustReplyWorkbenchItemFlowTest.kt:168  listOf(AiReplyItemClaim("general.answer", "Answer", listOf(1L)))
TrustReplyWorkbenchItemFlowTest.kt:179  同上
TrustReplyWorkbenchItemFlowTest.kt:190  同上
TrustReplyWorkbenchItemFlowTest.kt:673  listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(99L)))
TrustReplyWorkbenchItemFlowTest.kt:689  listOf(AiReplyItemClaim("general.answer", "Please send your CV", listOf(9L)))
TrustReplyWorkbenchItemFlowTest.kt:1033 listOf(AiReplyItemClaim("general.answer", text, listOf(sourceId)))
```

**7 处，全部是单元素 `listOf(...)`**。`joinToString(" ")` 与 `joinToString("\n\n")` 对单元素列表输出相同 → 既有断言不受影响。

`TrustReplyWorkbenchItemFlowTest.kt:779` 的 `assertEquals("raw Claim A|Claim B", response.rawDraftText)` 是 **composer mock 的返回值**（stub 在 :1000 用 `|` 拼），且 `Claim A` / `Claim B` 是两个**不同条目**各一个 claim，不是同条目两个 claim → 不受影响。

### Interaction points

| # | 写入侧 | 读取侧 | 计划如何覆盖 |
|---|---|---|---|
| IP-1 | `AiReplyDraftService:1552` 生成 `answerText` | `TrustReplyWorkbenchService:1286` 校验相等 | I-1：同一常量，T1+T2 同批改 |
| IP-2 | `TrustReplyWorkbenchService:1331` 物化 `normalizedAnswer` | `versionId()`（:1342）→ 前端 `hasVersionIdentity` → `validateLockedItem`（:1245-1248） | I-1；存量草稿一次性 STALE（走既有 :575-587 降级） |
| IP-3 | `materializeVersion` 产出的 `answerText` | `composeLockedItems`（:1112）逐字组装 | I-2：composer 不改，逐字契约由 `AiReplyPointByPointComposerTest:72-107` 守住 |
| IP-4 | `assemble` 的 `rawDraftText` | `app.js:10405 templateTextBody` → `PendingMailOperationService:203` → `plainTextToHtml` | I-5：A-3 人工验收覆盖 |
| IP-5 | `assemble` 的 `renderedDraftText` | `app.js:9586 editor.innerText` → `editor.innerHTML` → `PendingMailOperationService:205` | I-5：A-4 人工验收覆盖 |

## 实现方案

### T1 — 在 `AiReplyDraftService` companion 新增分隔符常量（I-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

在 `AiReplyDraftService` 类的 companion object（:2336）中新增：

```kotlin
        /**
         * I-1 (plan 02): the single authority for how a request item's claims are
         * joined into its canonical answerText. Three production sites and one
         * test mirror MUST reference this constant instead of a literal:
         * AiReplyDraftService:1552, TrustReplyWorkbenchService:1286 and :1331.
         * The paragraph structure lives in answerText itself — the locked
         * composer never formats (K-locked-item-assembly-list-not-set).
         */
        const val CLAIM_PARAGRAPH_SEPARATOR = "\n\n"
```

跨文件引用有先例：`AiReplyDraftService.sha256Hex`（6 个生产调用点）、`AiReplyDraftService.PREFLIGHT_VERSION_CHARSET`（`PendingMailOperationService.kt:880`）。

### T2 — 生成侧改用常量（I-1）

同文件 :1552：

```kotlin
                    answerText = section.answers.joinToString(CLAIM_PARAGRAPH_SEPARATOR) { it.answer },
```

不改同一 `map` 块内的 `claims = ...`（:1553-1559）。

### T3 — 校验侧与物化侧改用常量（I-1 / I-3 / I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`

- :1286（`canonicalizeClaims`）
  ```kotlin
        if (answerText != canonical.joinToString(AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR) { it.text }) {
  ```
- :1331（`materializeVersion` 的 `when` 的 `else` 分支，其余三个分支逐字不动 — I-4）
  ```kotlin
            else -> normalizedClaims.joinToString(AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR) { it.text }
  ```
- **:1153 不改**（I-3）。执行时须在该行上方保留/补一行注释说明它刻意保持 `" "`：
  ```kotlin
                // I-3 (plan 02): the trust-boundary body stays single-space joined on
                // purpose — high-risk phrase-family matching assumes continuous text.
  ```

### T4 — 测试镜像改用常量（I-1）

文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`

:1204 改为：

```kotlin
            else -> answerText.trim().ifBlank { claims.joinToString(AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR) { it.text } }
```

（现有用例全是单 claim，此行改动不改变任何现有断言的结果——见现状审计 B-7——但必须改，否则将来加多 claim 用例时会静默漂移。）

### T5 — 新增多 claim 回归测试（I-1 / I-2 / I-3 / I-4）

同文件新增 4 个用例：

1. **多 claim 条目的 answerText 分段**：构造一个 `ANSWER_WITH_EVIDENCE` 条目，`item.intents` 含 2 个 `SUPPORTED` intent，`claims = listOf(claimA, claimB)`，`answerText = "Claim A" + "\n\n" + "Claim B"`。断言 `adjustItem` / `assemble` 返回的 `version.answerText == "Claim A\n\nClaim B"`，且**不抛** `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`。
2. **旧格式被拒**：同样输入但 `answerText = "Claim A Claim B"`（单空格），断言抛 `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`。这条守住 I-1 的"三处同源"。
3. **claims-空 handling 不受影响（I-4）**：对 `ACKNOWLEDGE_PENDING` 与 `ANSWER_FROM_OPERATOR_INPUT` 各断言 `version.answerText == 输入.trim()`，中间无 `\n\n` 注入。
4. **重复判定仍生效（must-NOT-change 4）**：两个不同条目，一个 `answerText = "Same\n\nclaim"`，另一个 `"Same  claim"`，断言仍抛 `TRUST_REPLY_DUPLICATE_CLAIM`（因 :1181 的 `\s+` 归一）。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | T1 新增常量（companion :2336）；T2 改 :1552 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | T3 改 :1286、:1331；:1153 只加注释不改逻辑 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | T4 改 :1204；T5 新增 4 个用例 |

合计 **3** 个文件（上限 10）。子系统 **1** 个（LLM 回复组装，上限 2）。

**明确不在清单内**（执行 agent 不得改动）：`AiReplyPointByPointComposer.kt`、`AiReplyPointByPointComposerTest.kt`、`MailContentService.kt`、`PendingMailOperationService.kt`、`app.js`、`trust-reply-workbench.js`、任何 `db/migration/*.sql`、`TrustReplyWorkbenchStateStore.kt`。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchItemFlowTest

# 受影响的相邻测试类（必须一并绿）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='TrustReplyWorkbenchServiceTest+AiReplyPointByPointComposerTest+AiReplyDraftServiceTest+PendingMailOperationServiceTrustWorkbenchTest'

# 单个测试方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchItemFlowTest#methodName

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- `mvn test`：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且 `exec-maven-plugin` 的 `node-test` execution 无报错。
- `mvn clean package`：退出码 0，`BUILD SUCCESS`。
- `git diff --check`：无输出。

来源：`CLAUDE.md` 项目元信息的 `test_command` / `build_command` 与「Commands」章节（含 `-Dtest=Class#method` 语法）。

## 验收标准

- **I-1**：
  `grep -rn 'joinToString(" ")' src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` **恰剩 1 行且为 :1153**（信任门禁，I-3 刻意保留）。
  `grep -rn 'CLAIM_PARAGRAPH_SEPARATOR' --include=*.kt src/main src/test` **恰 5 行**：定义 1 行（AiReplyDraftService companion）+ 生产 3 行（AiReplyDraftService:1552、TrustReplyWorkbenchService:1286、:1331）+ 测试镜像 1 行（ItemFlowTest:1204）。
  T5 用例 1、2 通过。
- **I-2**：`git diff --stat` 中**不含** `AiReplyPointByPointComposer.kt`；`AiReplyPointByPointComposerTest` 全部通过（尤其 :72「preserves every answer byte order and duplicate」与 :99「keeps ordered answers when frame is empty」）。
- **I-3**：`sed -n '1153p' src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 仍含 `joinToString(" ")`。
- **I-4**：T5 用例 3 通过；`sed -n '1327,1332p' TrustReplyWorkbenchService.kt` 的 `OMIT` / `ACKNOWLEDGE_PENDING` / `ANSWER_FROM_OPERATOR_INPUT` 三个分支与改动前逐字一致（`git diff` 中这三行无变更）。
- **I-5**：`grep -n "html = true" src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` 仍命中 :271；A-3 与 A-4 人工验收通过。
- **must-NOT-change 4**：T5 用例 4 通过。
- **must-NOT-change 6**：`git diff --stat` 不含 `UnsupportedAnswerIndexService.kt`；`UnsupportedAnswerIndexApiTest` 通过。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 多问题来信的整合正文分段
- 前置条件：找一封（或用「AI 训练」页构造一封）来信，其内容在「摘要与事实」页里**至少有一条摘要绑定了 ≥2 个事实**（卡片上的「对应事实」计数 ≥2）。
- 操作步骤：
  1. 在该摘要卡片上逐条生成并锁定回答，直到「已处理 N/N」满格。
  2. 切到「回复框架与整合」页，点「服务端整合」。
  3. 看「服务端原始正文」预览框（如内容超出请滚动到底）。
- 预期结果：尊语单独一行；**该摘要的每条事实对应的句子各自成段，段与段之间有一个空行**；不再是一整块。结束语单独成段出现在最后。
- 覆盖：I-1、需求描述 observable outcome 1

### A-2: 单事实摘要仍是一段
- 前置条件：同 A-1，但选一条只绑定 **1 个事实** 的摘要。
- 操作步骤：生成、锁定、整合。
- 预期结果：该摘要的答案仍是**连续的一段**，内部没有被塞进空行。
- 覆盖：I-1（不过度分段）

### A-3: 未编辑直接发送，收件人看到段落（I-5，IP-4）
- 前置条件：完成 A-1 的整合。准备一个你能收信的测试邮箱，把该专家的邮箱临时改为它（或用已有测试联系人）。
- 操作步骤：
  1. 点「采用到人工回复」。
  2. **不要动编辑器里的任何一个字**。
  3. 填好主题，点发送。
  4. 到测试邮箱用 **Gmail 网页版** 打开这封信。
- 预期结果：正文段落与 A-1 预览一致，每段之间有明显空行，**不是一堵墙**。查看邮件原始内容（Gmail「显示原始邮件」），`Content-Type: multipart/alternative` 存在，HTML 部分含 `<p>` 标签。
- 覆盖：I-5、IP-4、需求描述 observable outcome 2

### A-4: 编辑后再发送，段落仍在（I-5，IP-5）
- 前置条件：同 A-3，重新走一遍到「采用到人工回复」。
- 操作步骤：
  1. 在编辑器正文里**任意改一个词**（例如把某个 "the" 改成 "this"）。
  2. 发送，到测试邮箱用 Gmail 网页版打开。
- 预期结果：段落仍然可见（HTML 侧为 `<br><br>` 或 `<p>`），不塌成一段。
- 覆盖：I-5、IP-5

### A-5: 回归 — 存量未完成草稿的降级行为（IP-2）
- 前置条件：**在部署本计划之前**，在某封来信的回复台里锁定至少 1 条回答，然后关闭页面（不整合、不发送），让 saved state 留在服务端。
- 操作步骤：部署后重新打开同一封来信的回复台。
- 预期结果：页面正常加载，**不报错、不白屏**；顶部状态提示为「来源或事实已变化」类的 STALE 文案，锁定回答被清空需要重新生成。**不得**出现未捕获异常或 500。
- 覆盖：IP-2、现状审计「存储 B」的副作用声明

### A-6: 回归 — 信任门禁未被放松（I-3，must-NOT-change 3）
- 前置条件：找一条会触发高风险短语校验的事实（或在「AI 训练」页临时构造一条 QA 事实，正文含 "guaranteed" 之类的强承诺词）。
- 操作步骤：把该事实绑到某条摘要上，生成回答。
- 预期结果：与部署前**表现一致**——该条目仍被判为需要人工/被拒绝，不因本次改动而放行。
- 覆盖：I-3、must-NOT-change 第 3 条

### A-7: 回归 — 重复答案仍被拒（must-NOT-change 4）
- 前置条件：构造两条摘要，让它们生成出**文字相同**的答案（可通过给两条摘要绑同一类事实实现）。
- 操作步骤：两条都锁定，点「服务端整合」。
- 预期结果：整合被拒绝，提示重复（`TRUST_REPLY_DUPLICATE_CLAIM` 对应的错误文案），与部署前一致。
- 覆盖：must-NOT-change 第 4 条
