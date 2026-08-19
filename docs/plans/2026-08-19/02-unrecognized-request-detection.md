# P2a：未识别诉求检测（影子期 · 纯测量，零行为变化）

> 主计划：`00-grounded-coverage-master.md`。**必须在 P1 之后执行**（P1 会改 `matchIntents` 结果，进而改 `requestKey` 哈希，这个降级只应发生一次）。
> 本计划是两步中的第一步。P2b（把测量结果接进门禁）单独立计划，见文末「P2b 的开关条件」。

---

## 设计思路（先说清楚为什么这么做）

### 要换的是分母，不是阈值

今天 `status` 在 `intentCoverages` 上算，而 `intentCoverages` 来自 `matchIntents`（`QaFactSelectionService.kt:316/326/341`）。**没被认出的问题从不进这个列表，因此永远不会变成 MISSING。** 修法不是"调判定规则"，是**给分母找一个独立于匹配器的来源**。

### 三个已经排除的做法

**① 只把 GROUNDED 降级成 PARTIAL —— 无效。**
```
QaFactSelectionService.kt:55-58 / :246-249
groundedRequestCount = requestFacts.count {
    it.status == RequestGroundingStatus.GROUNDED ||
        it.status == RequestGroundingStatus.PARTIAL
}
```
PARTIAL 与 GROUNDED 同样算「已覆盖」。降级只改徽标，对 CRS 覆盖分和自动发门禁**零影响**。

**② 用 `Kind.FALLBACK` 封顶 —— 会产生假阴性。**
`ExtractedRequest.kind` 在 5 个调用点全部被丢弃（`TrustReplyWorkbenchService.canonicalRequests`（:1497）、`QaFactSelectionService.kt:20/89`、`QaMatchService.kt:24/138`），`Kind.FALLBACK` 的语义确实是"抽取器什么都没找到"。但 P1 之后骨科那封信的 5 个诉求全部有据，它仍然是 FALLBACK 单元 —— 用它封顶会把已经答全的信也判成不足。`kind` 只能当**辅助标记**记账，不能当判据。

**③ 纯词法残余枚举 —— 误报吞没信号。**
把 `wordBoundaryContains`（`AiReplyIntentCatalog.kt:406-409`）改为返回 `IntRange` 是可行的（现在丢弃了匹配位置），但"命中 span 之外的片段"在词法层无法区分**并列成分**与**头部从句**。用骨科来信实测按 `,` / ` and ` 切分：`Before going further`、`I would appreciate some additional information about the programme` 这两段不含任何 span，会被判成未识别诉求。要压这类误报就得维护停用短语表，白名单本身又是新的维护面。

### 选定做法：可验证的枚举器 + span 认领

**一句话**：用一次窄用途 LLM 调用**枚举诉求**，但只取「标签 + 逐字引文」，引文必须是来信的子串（服务端校验，不是则丢弃）；再用 alias 命中 span 判断每条诉求是否已被某个 intent 认领；未认领的就是未识别诉求。

三个要点：

1. **模型输出是可证伪的**。它必须交出一段来信原文（`quote`），服务端 `inboundText.contains(quote)` 校验。模型编造一条不存在的诉求 → 引文找不到 → 直接丢弃。这把"LLM 判断"降级成了"LLM 定位"，后者可验证。
2. **它从不产出内容**。`quote` 与 `label` 只进工作台与 prompt，绝不进外发正文（`K-grounding-status-ui-only`，hit 19，P1）。
3. **它从不进任何哈希**。`evidenceSetVersion` / `versionId` / `requestKey` 一律不含枚举结果（`K-ai-reply-evidence-version-deterministic`），否则同一封信两次 bootstrap 得到不同版本，锁定回答全部失效。

### 为什么影子期不改任何行为

`decision-auto-reply-threshold-2026-08-18` 已经定了「A 线必须从数据反解，不能拍脑袋」。同一条逻辑适用于这里：**在拿到真实误报/漏报分布之前，不知道该不该让未识别项拉低 status**。所以 P2a 只测量、只记账，`status` / `groundedRequestCount` / `allowedHandlings` 一律不动。P2b 才打开开关。

### 复用的既有机件（不需要新基础设施）

- LLM 调用口：`AiQaExtractionService.kt:46/68-72`（注入声明见 :31-39） 已有 `llmDraftClientProvider.getIfAvailable()` + `client.chat(messages = listOf(LlmChatMessage(role = "system", ...), ...))` 的范式。
- JSON 数组解析：`AiQaExtractionService.parseExtractedItems`（:153）与 `extractJsonPayload`（:164）已处理"模型加了 markdown 围栏"的情况。
- 日志：本仓已有 `LoggerFactory.getLogger(...)` 的常规用法（如 `AiQaExtractionService.kt:40`），影子记账直接用结构化日志行，无需任何 schema 改动。

---

## 需求描述

**Observable outcome**

1. 工作台 bootstrap 返回的 `requestCoverage[]` 每项新增 `unrecognizedAsks: [{label, quote}]` 字段（影子字段，前端本期不渲染）。
2. 每次工作台 bootstrap 与每次自动回复决策，都产生一行**结构化应用日志**（固定前缀 `[ASK_ENUM]`），含：`inboundMailRecordId`、`expertContactId`、枚举诉求数、已认领数、未认领数、抽取器 `kind`、枚举器是否可用。**不落库**（理由见现状审计「为什么不写 auto_reply_confidence_log」）。
3. 用骨科那封来信（P1 已落地）跑一次，`[ASK_ENUM]` 日志行中 `enumerated=5 claimed=5 unrecognized=0`；用一封含"你们能提供签证支持吗"这类目录未覆盖问法的来信跑一次，`unrecognized>=1`。

**What must NOT change**

- N1. `RequestFactItem.status` 的取值与计算规则完全不变（`QaFactSelectionService.kt:341-348` 的 `when` 一个字符都不改）。
- N2. `groundedRequestCount` / `requestCount` / `unsupportedRequests` 的口径与取值不变。
- N3. `allowedHandlings` / `recommendedHandling`（`TrustReplyWorkbenchService.allowedHandlings`/`recommendedHandling`，:1804-1825）不变。
- N4. `evidenceSetVersion` / `versionId` / `requestKey` 三个哈希的输入组成不变。
- N5. 外发邮件正文与 prompt 内容不变（影子期枚举结果不进 prompt）。
- N6. 枚举器不可用（LLM 关闭 / 超时 / 解析失败）时，工作台与自动回复的行为与今天完全一致。

**Out of scope**

- O1. 让未识别项参与 `status` / 门禁 → P2b。
- O2. 前端渲染未识别项 → P2b（届时触发一轮 Step 1b-fe 样式审计）。
- O3. 自动回复路的运行期预算隔离与异步化（见「自动路的预算约束」，本期用开关规避）。
- O4. 修改 `QaRequestExtractor` 的切分规则。
- O5. 事实排序 → P3。

---

## 关键不变量

### I-1: 枚举结果必须逐字可验证，不可验证即丢弃
- Rule: 枚举器返回的每个条目必须含 `quote`，且 `inboundText.contains(quote)` 为真（比较前两侧统一做 `Regex("\\s+")→" "` 折叠 + trim，不做 lowercase）。不满足者**整条丢弃**，不得回退成"用 label 当引文"。`quote` 长度 < 8 字符的也丢弃（防止模型交出 `the` 这类平凡子串蒙混过关）。
- Applies to: `InboundAskEnumerator.parse()`。
- Violation consequence: 模型编造的诉求会变成永久的未识别项，把门禁（P2b）钉死在"永远有未识别"，等于关掉门禁。
- 来源: original

### I-2: 枚举结果绝不进任何版本哈希
- Rule: `requestKey`（`TrustReplyWorkbenchService.requestKey`（:1831-1844））、`versionId`（`versionId`，:1846-1866）、`evidenceSetVersion`（`evidenceSetVersionWithMapping`，:1546-1553）三个函数的入参组成不得增加任何来自枚举器的值。
- Applies to: 上述三个函数及其所有调用点。
- Violation consequence: LLM 输出不确定 → 同一封信两次 bootstrap 得到不同 `evidenceSetVersion` → 已锁定的逐项回答全部判 STALE 失效，操作员的工作凭空蒸发。
- 来源: K-ai-reply-evidence-version-deterministic + K-request-fact-assignment-version-must-include-mapping

### I-3: 影子期零行为变化
- Rule: 合成的未识别项以**独立字段**承载（`RequestFactItem.unrecognizedAsks`、`ResolvedQaRules.unrecognizedAskCount`），**不得**追加进 `intentCoverages`，**不得**参与 `status` / `groundedRequestCount` / `unsupportedRequests` / `allowedHandlings` 的任何计算。
- Applies to: `QaFactSelectionService.buildRequestFact` / `workbenchResult` / `select`。
- Violation consequence: 影子期一旦改变判定，就失去了"用真实分布反解阈值"的基线；且会在没有误报率数据的情况下直接改变操作员工作流。
- 来源: decision-auto-reply-threshold-2026-08-18（A 线必须从数据反解）

### I-4: 枚举器必须 fail-open
- Rule: LLM 未启用、客户端不可用、超时、HTTP 失败、JSON 解析失败、全部条目被 I-1 丢弃 —— 任一情况下 `enumerate()` 返回 `AskEnumeration(available = false, asks = emptyList())`，调用方按"零未识别项"继续，且**不得**抛异常穿透到 bootstrap。日志行记 `available=false`。
- Applies to: `InboundAskEnumerator.enumerate()` 的全部异常路径、`QaFactSelectionService` 的调用点。
- Violation consequence: 工作台是人工审核的唯一入口；枚举器把它打挂等于停摆。且 `available=false` 的样本必须能从统计里剔除，否则会被当成"零未识别"污染基线。
- 来源: K-llm-timeout-fallback

### I-5: 未识别项是操作端数据，绝不外发
- Rule: `label` / `quote` 只能出现在 API 响应、工作台界面与日志表；**不得**进入 `AiReplyPointByPointComposer` 的 prompt，**不得**进入任何 `answerText` / 外发正文。影子期连 prompt 都不进（N5）。
- Applies to: 新增字段的所有消费点。
- Violation consequence: `GROUNDED/PARTIAL/UNSUPPORTED` 这类内部状态被原样外发是本仓已发生过的事故形态；未识别诉求的引文同属内部审核数据。
- 来源: K-grounding-status-ui-only（hit 19，P1）

### I-6: 自动路默认关闭枚举器
- Rule: 新增配置项 `llm.ask-enumerator.enabled-for-auto-reply`，**默认 `false`**。自动回复路（`GroundedAutoReplyDecisionService.decide()` → `AutoReplyPreviewService.kt:112` / `AutoMailReplyService.kt:508`）在该项为 false 时跳过枚举，只记 `available=false`。工作台路不受该开关约束，始终启用。
- Applies to: 配置类 + 自动路调用点。
- Violation consequence: 自动回复跑在 IMAP 拉取循环里，`MAIL_SCHEDULING_AUTO_REPLY_MAX_MESSAGES_PER_ACCOUNT` 默认 20，`BatchAutoMailReplyService` 还跨账号循环，叠上 attemptTimeout 30s / totalTimeout 300s；每封多一次同步 LLM 调用会拖垮收信链路。这条阻塞在 `auto-reply-context-divergence` 里已记录。
- 来源: auto-reply-context-divergence（路 B 的前置阻塞）

### I-7: span 认领必须用 alias 命中位置，不能用 intent 是否存在
- Rule: 判断一条枚举诉求是否"已认领"，必须比较该诉求 `quote` 在来信中的字符区间与**该 intent alias 实际命中的字符区间**是否重叠。禁止用"本单元匹配到了 N 个 intent，枚举出 M 条，未识别 = M − N"这种计数相减。
- Applies to: `AiReplyIntentCatalog.matchIntentsWithSpans()`（新）、认领判定函数。
- Violation consequence: 计数相减在"一条诉求命中两个 intent"（如 `remuneration and intellectual property` 同时命中 `finance.arrangements` 与 `ip.arrangements`）时会算出负数或错配；也无法指出**哪一条**没被认领，日志失去诊断价值。
- 来源: original

---

## 现状审计

### `matchIntents` 与 span

**当前实现**（`AiReplyIntentCatalog.kt:315-345`）返回 `List<RequestIntentDefinition>`，匹配位置在 `wordBoundaryContains`（:406-409）内部被丢弃：
```kotlin
private fun wordBoundaryContains(text: String, phrase: String): Boolean {
    val escaped = Regex.escape(phrase)
    return Regex("\\b$escaped\\b").containsMatchIn(text)
}
```
`containsMatchIn` 换成 `find()` 即可拿到 `MatchResult.range`，属本地改动。

**调用点（grep 回执，共 3 处生产 + 1 处定义）**
```
$ grep -rn "matchIntents" src/main/kotlin
AiReplyContextService.kt:26        AiReplyIntentCatalog.matchIntents(text).any { it.requiresProfile }
AiReplyIntentCatalog.kt:315        fun matchIntents(requestText: String): List<RequestIntentDefinition> {
TrustReplyWorkbenchService.kt:1498 val intentKeys = AiReplyIntentCatalog.matchIntents(request.text).map { it.key }
QaFactSelectionService.kt:316      val matchedIntents = AiReplyIntentCatalog.matchIntents(requestText)
```
**保留 `matchIntents` 原签名不动**，新增 `matchIntentsWithSpans` 并让 `matchIntents` 成为它的薄封装 —— 这样 `AiReplyContextService:26` 与 `TrustReplyWorkbenchService.canonicalRequests`（:1498）（喂 `requestKey` 哈希）行为不变（守 N4）。

**canonicalize 的坐标漂移（必须处理）**
`canonicalize`（:305-312）做了 URL 屏蔽、连字符归一、空白折叠、`programme`→`program`。**`programme`→`program` 会改变字符串长度**，因此 canonical 串上的 span 不能直接当作原文 span。做法：先在 canonical 串上匹配拿 span，再用一张 canonical→原文 的下标映射表还原（与 `QaRequestExtractor.extractQuestions`（:120-140）已有的 `indexMap` 同一手法，可照抄该范式）。

### 为什么不写 `auto_reply_confidence_log`（V104）—— 实测后否掉的初稿方案

初稿打算给该表加 4 列记账。**实测后否掉**，两条理由：

1. **写入点只有一处，且不在工作台路。**
   ```
   $ grep -rn "autoReplyConfidenceLogRepository\." src/main/kotlin
   src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:516
   $ grep -n "ConfidenceLog" .../llm/service/TrustReplyWorkbenchService.kt      # 无输出
   $ grep -n "ConfidenceLog" .../mail/service/AutoReplyPreviewService.kt        # 无输出
   ```
   全仓**唯一**写入点是 `AutoMailReplyService.kt:516`，且被 `decision.confidence?.let { ... }`（:514）包着——confidence 为空就不写。工作台路要写这张表得**新增**写入点，不是「同一行补值」。

2. **表结构容不下工作台样本。** `V104` 的业务列几乎全是 `NOT NULL`：`crs`、`coverage_score`、`evidence_score`、`consistency_score`、`history_score`、`request_count`、`unsupported_count`、`partial_count`、`verified_rule_count`、`warning_count`、`draft_readiness`、`generation_state`、`decision_reason`、`ready_to_send`、`tier`。工作台 bootstrap 没有 CRS、没有 tier、没有 decision_reason——塞进去要给这些列编造值，**会污染阈值反解的基线**，而这张表存在的唯一目的就是反解阈值。

**改为结构化应用日志**：一行 `logger.info` + 固定前缀 `[ASK_ENUM]` + 定长键值。零 schema 改动、零新表、零 domain/repository 文件，样本靠日志采集导出。代价是统计不如 SQL 方便，但影子期本就是短期测量；正式记账留到 P2b 与门禁一起设计表。

### `ResolvedQaRules` 的下游

```
$ grep -rn "unsupportedRequests" src/main/kotlin
AiTrainingController.kt:250 / :505
AiReplyDraftService.kt:325 / :362 / :1526 / :1596 / :1833
TrustReplyWorkbenchService.kt:226 / :883
QaFactSelectionService.kt:51 / :242
UnmatchedInboundMailController.kt:411 / :1012
```
新增的 `unrecognizedAskCount` **本期只透传到 `TrustReplyWorkbenchService:226` 的工作台 coverage DTO**，不铺到 `AiReplyDraftService` / `AiTrainingController` / `UnmatchedInboundMailController` 的三个 DTO —— 那三条铺开属 P2b（届时门禁要读它）。这是本计划把文件数压在 10 以内的关键取舍。

**Interaction points**
- IP-1：`matchIntentsWithSpans` 写 × `TrustReplyWorkbenchService.canonicalRequests`（:1498） 读（`requestKey` 哈希）—— 必须证明 `intentKeys` 输出逐字未变（N4）。
- IP-2：`InboundAskEnumerator` 写 × `QaFactSelectionService.buildRequestFact` 读 —— fail-open 路径（I-4）。
- IP-3：枚举器 × 自动回复的 IMAP 拉取循环 —— 开关默认关（I-6）。
- IP-4：`[ASK_ENUM]` 日志行 × `AutoMailReplyService.kt:516` 的 confidence 记账 —— 两者**互不影响**（一个进日志、一个进表），但同一封信可能同时产生两条记录，统计时用 `inboundMailRecordId` 关联，不要相加。

### 前端样式盘点

不适用：本计划变更文件清单中无任何 `.html` / `.css` / 前端 `.js` 文件（影子字段前端不渲染，O2）。

---

## 实现方案

### 阶段 A：span 化（不改行为）

**A-1. `AiReplyIntentCatalog` 新增 `matchIntentsWithSpans`**
```kotlin
data class MatchedIntentSpan(
    val definition: RequestIntentDefinition,
    val originalRanges: List<IntRange>   // 原文坐标，非 canonical 坐标
)

fun matchIntentsWithSpans(requestText: String): List<MatchedIntentSpan>
```
- 内部沿用现有 `canonicalize` → alias 匹配 → `disambiguateSelectionMatchingProjectTypes` → `application.next_stages` 的 timing 改写全套逻辑，**逻辑分支一处不改**。
- `wordBoundaryContains` 改为返回 `Sequence<IntRange>`（`Regex(...).findAll(text).map { it.range }`），原布尔语义用 `.any()` 表达。
- canonical→原文 下标映射照抄 `QaRequestExtractor.kt:120-140` 的 `indexMap` 范式。
- `matchIntents(requestText)` 改为 `matchIntentsWithSpans(requestText).map { it.definition }`，**保证既有三个调用点零行为变化**。
- `general.answer` 兜底项的 `originalRanges` 为空列表。

**A-2. 回归（`AiReplyIntentCatalogTest`）**
- 对现有全部 fixture 断言 `matchIntents(x)` 的结果与改动前逐字相同（N4/IP-1）。
- 新增 span 正确性用例：对骨科来信断言 `remuneration` 与 `intellectual property` 的 `originalRanges` 落在原文中确实是这两个词的位置（用 `mail.substring(range)` 逐字比对）。
- **含 `programme` 的用例**：断言 span 还原后 `mail.substring(range)` 取到的是原文形态，不因 `programme`→`program` 的长度变化而错位。

### 阶段 B：枚举器

**B-1. 新建 `InboundAskEnumerator.kt`**
```kotlin
data class EnumeratedAsk(val label: String, val quote: String, val originalRange: IntRange)
data class AskEnumeration(val available: Boolean, val asks: List<EnumeratedAsk>)

@Service
class InboundAskEnumerator(
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val llmProperties: LlmProperties,
    private val askEnumeratorProperties: AskEnumeratorProperties
) {
    fun enumerate(inboundText: String): AskEnumeration
}
```
system prompt（定稿逐字，范式对齐 `AiQaExtractionService.EXTRACTION_SYSTEM_PROMPT`）：
```
You segment an inbound email from a researcher into the distinct things they are asking for.
Return ONLY a JSON array. Each element must have:
- label (string, a short neutral title for the ask, at most 8 words)
- quote (string, a VERBATIM contiguous substring of the email that expresses this ask)
The quote must be copied character-for-character from the email. Do not paraphrase, translate,
correct spelling, or join non-adjacent text. If you cannot quote it verbatim, omit the element.
Do not include greetings, thanks, sign-offs, or statements about the sender's own background.
Do not include markdown fences or commentary outside the JSON array.
```
校验（I-1）：折叠空白后 `inboundText.contains(quote)`；`quote` 折叠后长度 ≥ 8；同一 `quote` 去重；条目数上限 12（超出截断并在日志记 `truncated`，不静默丢弃）。
JSON 解析复用 `AiQaExtractionService.extractJsonPayload` 的范式（抽成共享工具或原样复制一份小实现，**不得**改动 `AiQaExtractionService` 现有行为）。
fail-open（I-4）：`llmProperties.enabled` 为假 / provider 取不到 / `chat` 抛异常 / 解析为空 → `AskEnumeration(false, emptyList())`。

**B-2. 认领判定（I-7）**
```kotlin
internal fun claimed(ask: EnumeratedAsk, intentSpans: List<MatchedIntentSpan>): Boolean =
    intentSpans.any { spanSet -> spanSet.originalRanges.any { it.first <= ask.originalRange.last && ask.originalRange.first <= it.last } }
```
未认领集合 = `asks.filterNot { claimed(it, spans) }`。

### 阶段 C：接线（影子，零行为变化）

**C-1. `QaFactSelectionService`**
- `RequestFactItem` 新增 `val unrecognizedAsks: List<EnumeratedAsk> = emptyList()`（**默认空**，保证所有既有构造点不用改）。
- `ResolvedQaRules` 新增 `val unrecognizedAskCount: Int = 0`、`val enumeratorAvailable: Boolean = false`。
- `buildRequestFact` 增加**可选**入参 `askEnumeration: AskEnumeration = AskEnumeration(false, emptyList())`，只填充新字段。
- **`status` 的 `when`（:341-348）一个字符都不改**（I-3/N1）；`workbenchResult`（:225-241）与 `select`（:15-58）的 `groundedRequestCount` / `unsupportedRequests` 表达式不改（N2）。

**C-2. `TrustReplyWorkbenchService`**
- `selectForWorkbench` 之前调一次 `inboundAskEnumerator.enumerate(resolved.inboundText)`，结果透传给 `QaFactSelectionService`。
- `TrustReplyRequestCoverage` 新增 `val unrecognizedAsks: List<TrustReplyUnrecognizedAsk> = emptyList()`（**带默认值**，前端忽略未知字段，无需前端改动）。
- `toCoverage`（:1659 起）填充该字段。
- `allowedHandlings` / `recommendedHandling`（:1804-1825）**不改**（N3）。

**C-3. 记账（结构化日志，零 schema 改动）**
- 在 `TrustReplyWorkbenchService` 与自动路各输出一行：
  ```
  log.info("[ASK_ENUM] source={} contactId={} available={} enumerated={} claimed={} unrecognized={} kind={}",
           sourceType, contactId, available, asks.size, claimedCount, unrecognizedCount, extractorKind)
  ```
- 字段名与顺序固定，便于 grep 统计。
- `kind` 取自 `QaRequestExtractor` 返回的 `ExtractedRequest.kind`（今天被 5 个调用点全部丢弃，此处首次消费）——只记录，不参与任何判定（②的结论）。
- **不写 `auto_reply_confidence_log`**（IP-4），理由见现状审计。

### 阶段 D：回归

**D-1. `InboundAskEnumeratorTest`（新）**
- I-1：模型返回改写过的 quote（非逐字）→ 该条被丢弃；返回 `the` 这类 <8 字符 quote → 丢弃；返回拼接了不相邻文本的 quote → 丢弃。
- I-1：合法逐字 quote → 保留，且 `originalRange` 还原后 `mail.substring(range) == quote`。
- I-4：`llmProperties.enabled = false`、provider 返回 null、`chat` 抛 `RuntimeException`、返回非 JSON、返回空数组 —— 五种情况全部得到 `available = false, asks = []` 且不抛异常。
- 条目数上限 12 的截断行为被显式断言（不静默）。

**D-2. `QaFactSelectionServiceTest`（改）**
- I-3 影子断言：对同一输入，`askEnumeration` 给出 3 条未识别项 与 给出 0 条 两种情况下，`status` / `groundedRequestCount` / `unsupportedRequests` / `factRuleIds` **完全相同**。
- I-7：`remuneration and intellectual property` 这条枚举诉求同时与两个 intent 的 span 重叠时，只算一次"已认领"，不产生负数。
- 用骨科来信（P1 已落地的目录）断言 `unrecognizedAskCount == 0`；用一封含"Do you provide visa support?"的来信断言 `unrecognizedAskCount >= 1`。

**D-3. `AiReplyIntentCatalogTest`（改）** —— 见 A-2。

**D-4. 日志格式断言（并入 D-1）**
- 把 `[ASK_ENUM]` 行的拼装抽成纯函数，直接对返回字符串断言字段名与顺序固定。
- 本计划**无迁移文件**，因此不需要迁移文本断言测试。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumerator.kt` | 新增 | 枚举器 + 逐字校验 + fail-open |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/config/AskEnumeratorProperties.kt` | 新增 | `enabled-for-auto-reply` 默认 false（I-6） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修改 | `matchIntentsWithSpans` + span 还原；`matchIntents` 变薄封装 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改 | 两个新字段 + 可选入参；判定逻辑零改动 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | 接线枚举器 + coverage DTO 新字段 + 记账补值 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumeratorTest.kt` | 新增 | D-1 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 修改 | D-2 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | 修改 | D-3 / A-2 |

文件数 **8** ≤ 10。子系统 **2**（LLM 枚举器与 intent 目录 / QA 判定层）。新增共享存储字段 **0** —— 记账走结构化日志，无迁移、无新表、无 domain/repository。

> ⚠️ 执行时**不得**顺手把 `unrecognizedAskCount` 铺到 `AiReplyDraftService` / `AiTrainingController` / `UnmatchedInboundMailController` 三个 DTO，也**不得**顺手建记账表 —— 两者都是 P2b 的范围。初稿曾把记账写成「给 `auto_reply_confidence_log` 加 4 列」，实测后否掉（见现状审计）。

---

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7（Java 11）Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增/修改的测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InboundAskEnumeratorTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='InboundAskEnumeratorTest,QaFactSelectionServiceTest,AiReplyIntentCatalogTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空库全量迁移（可选；需本机 Docker，默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且测试输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：`CLAUDE.md` 第 5-27 行「Commands」章节 与第 140/142 行 `test_command:` / `build_command:` 项目元信息。

---

## 验收标准

- **I-1**：`InboundAskEnumeratorTest` 的四条丢弃用例 + 一条保留用例全部通过。
- **I-2**：`git diff` 中 `TrustReplyWorkbenchService.kt` 的 `requestKey` / `versionId` / `evidenceSetVersionWithMapping` 三个函数体**无改动行**；且 `grep -n "unrecognized\|askEnumer" ` 在这三个函数的行区间内零命中。
- **I-3**：`QaFactSelectionServiceTest` 的影子对照用例通过（3 条未识别 vs 0 条，四项输出完全相同）；且 `git diff QaFactSelectionService.kt` 中 `:341-348` 的 `when` 块无改动行。
- **I-4**：`InboundAskEnumeratorTest` 的五条 fail-open 用例通过。
- **I-5**：`grep -rn "unrecognizedAsks\|EnumeratedAsk" src/main/kotlin` 的命中集合**不含** `AiReplyPointByPointComposer.kt`、`AiReplyGroundedContentPlanner.kt`、`AiReplyGroundedDraftMaterializer.kt` 任一文件。
- **I-6**：`AskEnumeratorProperties` 的 `enabledForAutoReply` 默认值为 `false`（源码断言）；且自动路调用点在开关关闭时不进入 `client.chat`（用 Mockito 断言零调用）。
- **I-7**：`QaFactSelectionServiceTest` 的双 intent 重叠用例通过。
- **N1/N2/N3/N4/N6**：执行「验证命令」节的全量测试命令通过；`AiReplyIntentCatalogTest` 的既有 fixture 逐字回归通过。
- **N5**：I-5 的 grep 结果即为证据。

---

## 人工验收清单

### A-1: 骨科来信的枚举数与未识别数
- 前置条件：P1 已上线；LLM 已启用；库中存在骨科那封来信。
- 操作步骤：
  1. 打开该来信的可信回复工作台（触发一次 bootstrap）。
  2. 在应用日志中取最后一条 `[ASK_ENUM]` 行（`grep '\[ASK_ENUM\]' <logfile> | tail -1`）。
- 预期结果：该行为 `available=true enumerated=5 claimed=5 unrecognized=0 kind=FALLBACK`。
- 覆盖：observable outcome 2、3

### A-2: 目录未覆盖的问法被记为未识别
- 前置条件：同 A-1。构造一封来信，正文为 `Thank you. Before I decide, could you tell me whether you provide visa support, and what happens if the enterprise withdraws midway?`
- 操作步骤：同 A-1 的两步。
- 预期结果：`[ASK_ENUM]` 行中 `unrecognized>=1`；且工作台 bootstrap 接口响应（浏览器开发者工具 Network 面板）中该项的 `unrecognizedAsks` 含一条 `quote`，其文本是上述句子里的**原文片段**（可逐字在原信中搜到）。
- 覆盖：observable outcome 1、3、I-1

### A-3: 界面与判定完全没变（回归，本计划最重要的一条）
- 前置条件：在部署本计划**之前**，对 3 封不同来信各截一张「摘要与事实」页截图，并记录每项的状态标签与「对应事实」计数。
- 操作步骤：部署后重新打开同样 3 封来信的工作台，逐一对照。
- 预期结果：状态标签（GROUNDED / PARTIAL / UNSUPPORTED）、「对应事实」计数、chip 内容与顺序、处理方式下拉的可选项，**三封信全部逐项一致**；页面上看不到任何新元素。
- 覆盖：must-NOT-change N1/N2/N3、I-3

### A-4: 关掉 LLM 时工作台照常可用
- 前置条件：把 LLM 配置项关闭（`llm.enabled=false`）并重启。
- 操作步骤：打开任一来信的工作台，走到「回复框架与整合」页。
- 预期结果：页面正常加载，无错误提示；最后一条 `[ASK_ENUM]` 行为 `available=false enumerated=0 claimed=0 unrecognized=0`；工作台的状态与事实与开启 LLM 时一致。
- 覆盖：I-4、must-NOT-change N6

### A-5: 自动回复路未新增 LLM 调用（回归）
- 前置条件：`llm.ask-enumerator.enabled-for-auto-reply` 保持默认（未在配置文件中出现）。
- 操作步骤：触发一次自动回复决策（或自动回复预览），观察应用日志中的 LLM 调用记录。
- 预期结果：本次决策的 LLM 调用次数与部署前一致（未新增枚举器调用）；对应 `[ASK_ENUM]` 行为 `available=false`。
- 覆盖：I-6、IP-3

### A-6: 未识别引文没有出现在任何外发内容里
- 前置条件：用 A-2 那封含未识别项的来信。
- 操作步骤：在工作台逐项生成回答，进入「回复框架与整合」页查看整合后的正文预览。
- 预期结果：正文中**不出现** `unrecognizedAsks` 的任何 `label` 或 `quote`；也不出现「未识别」「unrecognized」这类字样。
- 覆盖：I-5

---

## P2b 的开关条件（不在本计划范围）

P2a 上线后**不要立刻**做 P2b。先攒样本，用真实数据回答三个问题：

1. **误报率**：`unrecognized>0` 的样本里，人工复核认为"确实漏答了"的比例。低于某个水平才谈得上做门禁。
2. **漏报率**：人工在工作台用 `ANSWER_FROM_OPERATOR_INPUT` 补答过的项里，有多少条当时 `unrecognized=0`（即枚举器没抓到）。
3. **`kind` 的相关性**：`FALLBACK` 单元的未识别率是否显著高于 `BULLET` / `QUESTION`。若显著，P2b 可以只对 FALLBACK 单元启用门禁，成本更低。

样本量参照 `decision-auto-reply-threshold-2026-08-18` 的 ≥200 影子样本口径。

P2b 的内容（届时单独立计划）：把 `unrecognizedAskCount` 铺到 `AiReplyDraftService:325/362`、`AiTrainingController:505`、`UnmatchedInboundMailController:1012` 三个 DTO；让未识别项进 `intentCoverages` 参与 `status`；工作台前端渲染未识别项（触发 Step 1b-fe 样式审计）；接入「有未识别项永不进 A 档」的硬门禁。
