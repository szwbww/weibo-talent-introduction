# 02 · 未识别诉求进入判定 + 孤儿 coverage key 修复

基线：`main` @ `f293507`。执行顺序见 `00-execution-order.md`（本计划排第 2，建议在 01 之后）。

---

## 需求描述

### Observable outcome

1. 一封信里被切分器/意图匹配器**没认出来**的诉求（`RequestFactItem.unrecognizedAsks` 非空），
   该条摘要不再显示 `依据充分`（GROUNDED），最高只能是 `依据部分`（PARTIAL），
   因而不会通过自动发硬门禁。
2. 事实 `Getting started materials` 从「结构性不可达」变为可被 grounded 链路选中。
3. 新增一条双向守卫测试：coverage key 目录与 intent 的引用关系不一致时，测试立刻变红。

### What must NOT change

1. **`intentCoverages` 的条目集合、顺序、`intentKey` 字面量** —— 本计划不新增、不删除、不重命名任何
   `RequestIntentDefinition`，因此 `requestKey` 与全部既有 version 哈希不变。
2. **枚举器不可用时行为逐字不变** —— `AskEnumeration.available == false` 时，
   status 判定必须与本计划落地前逐字相同。
3. **`select()`（自动/人工发送路径）默认行为不变** —— `askEnumeratorProperties.enabledForAutoReply`
   默认仍为 `false`（`AskEnumeratorProperties.kt:22`），本计划不改这个默认值。
4. **`normalizeAndValidate` 对既有规则的序列化顺序不变** —— 新 catalog 条目只能追加在列表末尾。
5. `ANSWER_FACTS_VERBATIM` 与运营矩阵路径不变（同 01 计划）。

### Out of scope（显式延后）

- 把 `enabledForAutoReply` 默认打开：那会给 IMAP 拉取循环加一次同步 LLM 调用，需独立评估，不在本计划。
- 修改 `QaRequestExtractor` 的切分规则：`requestKey` 含 `index` 与 `requestText`，改切分会让
  V83 表里全部历史 requestKey 失配（见 `## 关键不变量` I-2 的同类论证）。
- 新增 intent 定义或 alias：任何新 intent 都可能改变 `matchIntents` 的输出集合 → 改 `requestKey`。
  本计划**只**加 `alternativeCoverageKeys`（不参与匹配，不进 `requestKey`）。
- 前端展示未识别诉求 → `03-orchestration-and-preview.md`。

---

## 关键不变量

### Invariant I-1: 未识别诉求封顶 PARTIAL
- Rule: 当 `askEnumeration.available == true` 且本条 `unrecognizedAsks` 非空时，
  本条 status 不得为 `GROUNDED`；今日结论为 GROUNDED 的降为 `PARTIAL`，
  今日结论为 PARTIAL/UNSUPPORTED 的保持不变（只封顶，不进一步下调）。
- Applies to: `QaFactSelectionService.buildRequestFact`（`:444-541`）的 status 表达式，
  影响其 5 个调用点中 `askEnumeration` 非空的两条（`:52` select、`:337` auto；
  矩阵/legacy 路径也接收同一 enumeration，见 `:236`/`:304`）。
- Violation consequence: 保持现状即缺陷本身 —— `GROUNDED · 依据充分` 的实际语义是
  「我认出来的每个意图都绑上了事实」，匹配器越读不懂一封信，这封信越容易显示"依据充分"。
  叠加 `GroundedAutoReplyDecisionService.passesSendGate:172-178`（只拒 PARTIAL/UNSUPPORTED），
  这类信会成为**最容易通过自动发门禁**的一类。
- 来源: K-grounded-denominator-is-matched-intents（项目记忆 `grounded-denominator-is-matched-intents.md`）

### Invariant I-2: 不碰 `intentCoverages`，requestKey 必须逐字不变
- Rule: 本计划**只**改 `RequestFactItem.status` 的计算，不改 `intents` 字段的任何内容；
  不新增/删除 `RequestIntentDefinition`；不改任何 `requestAliases`（alias 参与 `matchIntents`）。
  只允许改 `alternativeCoverageKeys`（不参与匹配）。
- Applies to: `AiReplyIntentCatalog.definitions`、`QaFactSelectionService.buildRequestFact`。
- Violation consequence: `TrustReplyWorkbenchService.requestKey(...)` 的 `intentKeys` 输入变化
  → canonical 与 item 两个投影不再相等 → bootstrap 抛 422、工作台打不开、
  V83 `trust_reply_workbench_state` 里全部历史锁定项作废。
- 来源: K-request-key-includes-intent-keys

### Invariant I-3: 枚举器不可用不得误伤
- Rule: `askEnumeration.available == false` 时（LLM 关闭、bean 缺席、超时、
  解析失败、全部条目被 verbatim 校验丢弃，见 `InboundAskEnumerator.kt:66-97`），
  I-1 的封顶**一律不生效**，status 与本计划落地前逐字相同。
- Applies to: 同 I-1。
- Violation consequence: 一次 LLM 抖动会把整封信的所有条目从 GROUNDED 打到 PARTIAL，
  从而把本可自动发的信全部转人工 —— fail-open 变成 fail-closed。
- 来源: K-llm-timeout-fallback

### Invariant I-4: catalog 新键只能追加在末尾
- Rule: 向 `QaCoverageKeyCatalog.catalog`（`QaCoverageKeyCatalog.kt:64-110`）新增 `Entry` 时，
  必须追加在 `listOf(...)` 的**最后**，不得插入中间。
- Applies to: `QaCoverageKeyCatalog.kt`。
- Violation consequence: `normalizeAndValidate`（`:118-134`）的返回值是
  `all().map { it.key }.filter { it in trimmed }` —— **按目录声明顺序**。
  中插会让既有规则再次保存时 `coverage_keys` 字符串顺序改变，
  进而触发 `V82` 受控组的 `keys == parsed` 集合判定之外的一切按字符串比对的迁移守卫
  （如 `V107:10` 的 `AND coverage_keys = '...'` 逐字条件）失配。
- 来源: `QaCoverageKeyCatalog.kt:105-107` 既有注释（V105 P1 已记录同一约束）

### Invariant I-5: 目录与 intent 的引用关系必须双向闭合
- Rule: 新增一条机械断言测试：
  (a) 每个 intent 的 `requiredCoverageKeys + alternativeCoverageKeys` 中的键都必须存在于
  `QaCoverageKeyCatalog.all()`；
  (b) `QaCoverageKeyCatalog.all()` 中的每个键都必须被至少一个 intent 引用，
  **例外集合**写成显式常量 `KNOWN_UNREFERENCED_KEYS` 并逐条注释理由。
- Applies to: 新增测试类。
- Violation consequence: 违反 (a) → `normalizeAndValidate` 的 `require(unknown.isEmpty())`（`:126`）
  会让任何试图存该键的规则保存失败；违反 (b) → 该规则在 grounded 链路**结构性不可达**
  （`isCoverageEligible` 对非空 coverage 要求与 intent 的 required+alternative 有交集，
  `AiReplyIntentCatalog.kt:654-661`；无交集 → `selectIntentKeyForRule`（`:663-684`）
  的 `scored` 为空且 `blankCoverage` 为 false → 返回 `null` → 规则被丢弃）。
- 来源: K-coverage-key-orphan-makes-fact-unreachable（**该条已过时，本计划实测后修订，见 `## 现状审计` C-2**）

---

## 现状审计

### A. status 的产生与消费

**A-1 · 产生**（`QaFactSelectionService.kt:504-514`，逐字）

```kotlin
        val researchWarned = isResearch && !researchProfileSufficient
        val allMissing = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "MISSING" }
        val anySupported = intentCoverages.any { it.status == "SUPPORTED" }
        val allSupported = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "SUPPORTED" }
        val anyPartial = intentCoverages.any { it.status == "PARTIAL" }

        val status = when {
            researchWarned && !anySupported -> RequestGroundingStatus.UNSUPPORTED
            intentCoverages.isEmpty() -> RequestGroundingStatus.UNSUPPORTED
            allSupported -> RequestGroundingStatus.GROUNDED
            allMissing -> RequestGroundingStatus.UNSUPPORTED
            anySupported || anyPartial -> RequestGroundingStatus.PARTIAL
            else -> RequestGroundingStatus.UNSUPPORTED
        }
```

**注意**：`intentCoverages` 来自 `matchedIntents`（`:474-481`），而 `matchedIntents` 来自
`AiReplyIntentCatalog.matchIntentsWithSpans(requestText)`（`:457`）。
**没被认出的诉求从不进入这个列表，因此永远不会变成 MISSING** —— 这就是 I-1 要修的缺陷。

**A-2 · 消费**（决定 I-1 的封顶值必须是 PARTIAL 而不是 UNSUPPORTED）

| file:line | 读法 | 封顶到 PARTIAL 的后果 |
|---|---|---|
| `GroundedAutoReplyDecisionService.kt:172-178` `passesSendGate` | 任一 PARTIAL 或 UNSUPPORTED → 拒绝自动发 | **门禁生效**（这正是目的） |
| `GroundedAutoReplyDecisionService.kt:233-236` `hasGroundingGap` | 同上 → 有缺口 | 生效 |
| `AutoReplyConfidenceScorer.kt:41-48` `weightFor` | GROUNDED 1.0 / PARTIAL 0.6 / UNSUPPORTED 0.35，`COVERAGE_MAX = 40.0` | CRS 下降，符合语义 |
| `QaFactSelectionService.kt:76-77` / `:379-380` `groundedRequestCount` | `count { GROUNDED \|\| PARTIAL }` | **零影响**——PARTIAL 与 GROUNDED 同样算"已覆盖" |
| `TrustReplyWorkbenchService.kt:2277-2280` `recommendedHandling` | PARTIAL → `ANSWER_SUPPORTED_PART` | 推荐值变化，符合语义 |
| `AiReplyPointByPointComposer.kt:156` | PARTIAL 且有 sourceIds → 输出「缺失：以下问题暂无已审核事实，需人工补充」 | 复用既有通路，无需新代码 |

> **`groundedRequestCount` 对降级零反应**这一条必须写进计划：任何以
> "把假 GROUNDED 降成 PARTIAL" 为唯一手段的修法，对该计数与任何以它为基础的指标都是**零影响**。
> 本计划的安全属性由 `passesSendGate` 承载，不由 `groundedRequestCount` 承载。

### B. `unrecognizedAsks` 的产生与现有消费

**B-1 · 产生**（`QaFactSelectionService.kt:483-501`，逐字关键段）

```kotlin
        val absoluteMatchedSpans = requestRange?.let { range ->
            matchedSpans.map { span ->
                span.copy(
                    originalRanges = span.originalRanges.map { it.first + range.first..it.last + range.first }
                )
            }
        }
        val unrecognizedAsks = askEnumeration.asks.filter { ask ->
            requestRange != null &&
                ask.originalRange.first in requestRange &&
                absoluteMatchedSpans != null &&
                !claimed(ask, absoluteMatchedSpans)
        }
```

坐标系已对齐：intent span 是 request-local，`ask.originalRange` 是全文绝对偏移，
`:489-495` 做了 `+ range.first` 的 rebase。
→ **K-ask-enum-span-coordinate-system 记录的坐标系缺陷已修复**，本计划无需再处理（该 K 条目应标记为已解决）。

**B-2 · 现有消费点全部是"影子"**（`grep` 实测，`src/main` 内 6 处读点）

```
TrustReplyWorkbenchService.kt:436   诊断 flag UNRECOGNIZED_ASK
TrustReplyWorkbenchService.kt:449   诊断 DTO 的 unrecognizedAskCount
TrustReplyWorkbenchService.kt:463   聚合诊断 flag
TrustReplyWorkbenchService.kt:2163  toCoverage 映射进 TrustReplyUnrecognizedAsk
QaFactSelectionService.kt:65        select() 的日志计数
QaFactSelectionService.kt:369       workbenchResult() 的日志计数
```

`ResolvedQaRules` 的 `unrecognizedAskCount` / `enumeratorAvailable` / `enumeratorEnumerated` /
`enumeratorClaimed` 四个字段在 `src/main` 内**只有日志读点**
（`QaFactSelectionService.kt:92-95`、`TrustReplyWorkbenchService.kt:672-675`），
与 `AiReplyDraftService.kt:384-386` 注释「shadow-period measurement only (I-3)」一致。
→ **本计划就是把它从影子转正**，且只转正到 status 一处。

**B-3 · 枚举器的门控差异**（决定 I-3 的写法）

| 路径 | 是否枚举 | 位置 |
|---|---|---|
| `select()`（自动回复 + 人工发送，12 个调用点） | 受 `askEnumeratorProperties.enabledForAutoReply` 门控，**默认 false** | `QaFactSelectionService.kt:41-46`；默认值 `AskEnumeratorProperties.kt:22` |
| `selectForWorkbench()`（工作台，1 个调用点） | **恒开**，fail-open | `QaFactSelectionService.kt:167-168` |

→ 本计划落地后，**只有工作台会看到状态封顶**；自动回复路径在 `enabledForAutoReply` 打开前逐字不变。
这与 What-must-NOT-change 第 3 条一致。

`InboundAskEnumerator.enumerate` 的 fail-open 路径（`:66-97`）：LLM 关闭、bean 缺席、
`chat` 抛异常、空/空白响应、JSON 不可解析、全部条目被 verbatim 校验丢弃 —— 六条路径都返回
`AskEnumeration(false, emptyList())`，永不抛进调用方。

### C. coverage key 目录与 intent 引用的实测比对

**C-1 · 比对脚本与结果**（2026-08-26 在 `main @f293507` 实跑）

脚本：从 `QaCoverageKeyCatalog.kt` 正则提取全部 `Entry("<key>"`，
从 `AiReplyIntentCatalog.kt` 提取全部 `(required|alternative)CoverageKeys = listOf(...)` 中的字面量，做双向差集。

```
catalog keys: 31
intent-referenced keys: 30

ORPHANS (in catalog, referenced by NO intent): 3
  - general.answer
  - application.required_materials
  - work.relocation

REVERSE MISMATCH (intent wants, catalog lacks): 2
  - work.advisory_duration
  - work.time_commitment
```

**C-2 · 与知识条目的差异（K 条目已过时，Phase 6 需修订）**

`K-coverage-key-orphan-makes-fact-unreachable`（2026-08-19）记录的孤儿是 5 个：
`company.verification_evidence`、`application.required_materials`、`work.remote_arrangement`、
`work.travel_arrangement`、`work.relocation`。
实测今日只剩 3 个，其中 `company.verification_evidence` 已被
`AiReplyIntentCatalog.kt:329` 的 `alternativeCoverageKeys = listOf("company.verification_evidence")` 引用，
`work.remote_arrangement`（`:` 有 `requiredCoverageKeys = listOf("work.remote_arrangement")`）与
`work.travel_arrangement`（`:339` 的 alternative）也已被引用。
**该 K 条目必须在 Phase 6 修订，不得原样带入后续计划。**

**C-3 · 三个孤儿的逐个定性**

| 键 | catalog 行 | 是否真缺陷 | 依据 |
|---|---|---|---|
| `general.answer` | `QaCoverageKeyCatalog.kt:65` | **否，设计如此** | 它是零具名意图命中时合成的兜底 intent 的 key（`AiReplyIntentCatalog.kt:406-414`，`requiredCoverageKeys = emptyList()`）；`selectIntentKeyForRule:679` 专门为它写了 `blankCoverage && "general.answer" in intentKeys` 分支。列入 I-5 的例外常量。 |
| `application.required_materials` | `:92` | **是** | `V76__add_qa_rule_coverage_keys.sql:57-60` 把它作为 **唯一** 覆盖键赋给 `Getting started materials`；无 intent 引用 → 该规则在 grounded 链路结构性不可达。 |
| `work.relocation` | `:98` | **否（无实害）** | `V76:77-80` 把它与 `work.travel_arrangement` 一起赋给 `Workplace arrangement`；`work.travel_arrangement` 已被 `AiReplyIntentCatalog.kt:339` 引用，故该规则仍可达。`V107:7` 保留的 `Program overview` 也含 `work.travel_arrangement`。列入 I-5 的例外常量并注明理由。 |

**C-4 · 两个反向失配**

`work.time_commitment`（intent 定义 `AiReplyIntentCatalog.kt:285`）与
`work.advisory_duration`（`:296`）各自 `requiredCoverageKeys = listOf(<自身 key>)`，
但两个键**不在** `QaCoverageKeyCatalog` 里。
后果：`normalizeAndValidate`（`QaCoverageKeyCatalog.kt:126`）的
`require(unknown.isEmpty())` 会拒绝任何试图存这两个键的规则 →
**这两个 intent 只能靠空 coverage 的规则兜底**（`isCoverageEligible:656-657`：
coverage 为空且 intent 不在 `coverageRequiredIntentKeys` 时返回 true；
这两个 key 确实不在 `coverageRequiredIntentKeys`（`:645-653`）里，所以今天还能兜住）。

### D. 拟采用的可达性修复（不需要迁移）

`Getting started materials` 的唯一 coverage key 是 `application.required_materials`。
最小改动是给**已存在**的 intent 加一条 `alternativeCoverageKeys`：

`AiReplyIntentCatalog.kt:279-283` 现状逐字：

```kotlin
            key = "application.next_stages",
            title = "Next stages",
            requestAliases = listOf("next stages", "next steps", "what happens next", "application process", "timeline"),
            requiredCoverageKeys = listOf("application.steps")
        ),
```

加 `alternativeCoverageKeys = listOf("application.required_materials")` 即可。
`alternativeCoverageKeys` 不参与 `matchIntentsWithSpans`（`:379-400` 只读 `requestAliases`），
因此**不改变哪些 intent 被匹配** → 不改 `intentKeys` → 不改 `requestKey`（I-2 保住）。
**不需要任何 Flyway 迁移**，因而不触发 K-qa-rule-runtime-vs-migration-writes 的上线基线核对要求。

### Interaction points

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| IP-1 | `buildRequestFact` 写 `status` | `GroundedAutoReplyDecisionService.kt:172-178` 自动发门禁 | I-1 的目的即此；A-2 已列全部消费者 |
| IP-2 | `buildRequestFact` 写 `status` | `AutoReplyConfidenceScorer.kt:41-48` CRS | 分数下降，符合语义；A-2 已列 |
| IP-3 | `InboundAskEnumerator.enumerate` 写 `AskEnumeration.available` | `buildRequestFact` 的封顶条件 | I-3：available=false 一律不封顶 |
| IP-4 | `QaCoverageKeyCatalog.catalog` 追加 Entry | `normalizeAndValidate` 的返回顺序 → `QaRuleManagementService` 保存的 `coverage_keys` 字符串 | I-4：只许追加末尾 |
| IP-5 | `AiReplyIntentCatalog` 加 alternative key | `isCoverageEligible:654-661` | `Getting started materials` 变可达；不影响匹配 |

---

## 实现方案

### 阶段 1 · status 封顶（I-1 / I-2 / I-3）

**T1.1** `QaFactSelectionService.buildRequestFact`：把 `:506-514` 的 `when` 结果先赋给
`naturalStatus`，然后追加封顶：

```kotlin
        // 计划 02 (I-1/I-3): 枚举器可用且本条存在未被任何 alias span 认领的诉求时，
        // 状态封顶为 PARTIAL——"我认出来的都答了"不等于"专家问的都答了"。
        // available=false（LLM 关闭/超时/解析失败/全部条目被 verbatim 校验丢弃）
        // 一律不封顶，行为与本计划落地前逐字相同 (I-3)。
        val status = if (askEnumeration.available &&
            unrecognizedAsks.isNotEmpty() &&
            naturalStatus == RequestGroundingStatus.GROUNDED
        ) {
            RequestGroundingStatus.PARTIAL
        } else {
            naturalStatus
        }
```

注意顺序：`unrecognizedAsks` 的计算（`:496-501`）已在 status 之前，无需移动代码。

**T1.2** `intents = intentCoverages` 的返回表达式（`:534`）**一行都不动**（I-2）。

**T1.3** 若 01 计划已落地，其 I-6（UNSUPPORTED + 非空 factRuleIds → PARTIAL）与本条封顶
**必须写成同一个 `status` 表达式的两个分支**，先算 01 的提升、再算 02 的封顶，
并在注释里标出两条计划编号，避免后续 merge 时其中一条被覆盖。

### 阶段 2 · coverage key 目录闭合（I-4 / I-5）

**T2.1** `QaCoverageKeyCatalog.kt`：在 `catalog` 的 `listOf(...)` **末尾**（当前最后一项是
`:109` 的 `governance.sponsor_level`）追加两条，并保留一段与 V105 同风格的注释说明为何追加在末尾：

```kotlin
        // 计划 02 (I-4): 追加在末尾——normalizeAndValidate 按目录声明顺序返回，
        // 中插会改变既有规则再次保存时 coverage_keys 的字符串顺序。
        // 这两个键此前是"intent 要、目录没有"的反向失配，任何规则都存不进去。
        Entry("work.time_commitment", "投入时间", "每周/每月投入的时间承诺", "工作安排"),
        Entry("work.advisory_duration", "顾问期限", "顾问合作的典型时长", "工作安排")
```

**T2.2** `AiReplyIntentCatalog.kt:279-283` 的 `application.next_stages` 定义追加一行：

```kotlin
            requiredCoverageKeys = listOf("application.steps"),
            alternativeCoverageKeys = listOf("application.required_materials")
```

**禁止**在本计划里改动该定义的 `key`、`title` 或 `requestAliases`（I-2）。

**T2.3** 新建 `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt`：

```kotlin
class QaCoverageKeyIntentParityTest {
    // I-5 例外集合：每条都必须写明理由，删掉理由即视为缺陷。
    private val knownUnreferencedKeys = setOf(
        // 兜底 intent 自身的 key；requiredCoverageKeys 为空，
        // 由 selectIntentKeyForRule 的 blankCoverage 分支专门处理。
        "general.answer",
        // Workplace arrangement 与 Program overview 都同时带 work.travel_arrangement，
        // 后者已被 intent 引用，故这两条规则仍可达；本键无实害，暂不删。
        "work.relocation"
    )

    @Test fun `every intent coverage key exists in the catalog`() { /* (a) */ }
    @Test fun `every catalog key is referenced by at least one intent`() { /* (b) + 例外集合 */ }
}
```

断言 (b) 失败信息里必须打印出未被引用的键名，方便下次直接定位。

**T2.4** 新建 `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionUnrecognizedStatusTest.kt`，
照抄 `QaFactSelectionServiceTest.kt:20-22` 的 mock 形态与 `:1427-1445` 的 `ask(...)` fixture 构造器。

---

## 变更文件清单（5 个，≤10 ✅；子系统 1 个：QA 事实选择与 coverage 目录 ✅）

| # | 文件 | 新增/修改 | 涉及不变量 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改（`buildRequestFact` 的 status 表达式，一处） | I-1 I-2 I-3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | 修改（catalog 末尾追加 2 条 Entry） | I-4 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修改（`application.next_stages` 追加 `alternativeCoverageKeys`，一行） | I-2 I-5 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` | 新增 | I-5 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionUnrecognizedStatusTest.kt` | 新增 | I-1 I-3 |

**无 Flyway 迁移**（依据见 `## 现状审计` D）。

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。来源：`CLAUDE.md:7`。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的两个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaCoverageKeyIntentParityTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionUnrecognizedStatusTest

# 受影响的既有测试类（必须全绿）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyIntentCatalogTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleManagementServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

> 若 `AiReplyIntentCatalogTest` / `QaRuleManagementServiceTest` 在本仓不存在，
> 执行时以 `ls src/test/kotlin/**/**Test.kt` 的实际结果替换，并把替换后的命令写回本节 —— **不得跳过**。

通过判据：每条 `mvn` 退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：`CLAUDE.md:7-27`。

---

## 验收标准

- **I-1**：测试构造一条 request，使 `matchIntents` 命中的意图全部 SUPPORTED（自然 status = GROUNDED），
  同时喂一个落在该 request 范围内、与任何 alias span 不重叠的 `EnumeratedAsk`；
  断言 `item.status == PARTIAL`。
- **I-2**：`git diff` 中 `AiReplyIntentCatalog.kt` 的改动**只有** `alternativeCoverageKeys` 一行；
  测试断言对同一 requestText，改动前后 `matchIntents(text).map { it.key }` 逐元素相等
  （用一组覆盖全部 20+ 条 alias 的 fixture）。
- **I-3**：同 I-1 的构造，但传 `AskEnumeration(available = false, asks = listOf(同一个 ask))`；
  断言 `item.status == GROUNDED`（逐字回到落地前）。
- **I-4**：`grep -n 'Entry("' QaCoverageKeyCatalog.kt` 输出中，两个新键的行号大于
  `governance.sponsor_level` 的行号；并断言
  `QaCoverageKeyCatalog.normalizeAndValidate(listOf("programme.purpose","fees.policy"))`
  的返回顺序与本计划落地前相同。
- **I-5**：`QaCoverageKeyIntentParityTest` 的两条断言均通过；
  且把 `knownUnreferencedKeys` 清空后 (b) 必须失败（在测试里用一个 `@Test` 显式验证例外集合确实在起作用）。
- 可达性：测试断言携带 `coverage_keys = "application.required_materials"` 的规则，
  在命中 `application.next_stages` 的 request 上会被 `assignRulesToIntents` 分配（不再返回 null）。
- 回归：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A-1: 读不懂的问题不再显示"依据充分"（覆盖 outcome 1；I-1）
- 前置条件：
  1. `LLM_ENABLED=true`（枚举器需要 LLM；工作台路径恒开，无需额外开关）。
  2. 准备一封来信，正文为一段**没有问号、没有 bullet** 的礼貌陈述句，且包含两个诉求，
     其中一个用库里 alias 覆盖不到的说法，例如：
     `I would appreciate some additional information about the usual form of collaboration with the Chinese partner companies, and about the government authority responsible for the programme.`
- 操作步骤：
  1. 打开该来信的可信回复工作台。
  2. 查看第 1 条摘要的状态徽标，并展开该条查看「未识别诉求」诊断。
- 预期结果：
  - 状态徽标为 **`依据部分`**，**不是** `依据充分`。
  - 诊断区列出至少 1 条未识别诉求，其引文是来信中的逐字片段。

### A-2: 自动发门禁因此生效（覆盖 I-1 的 violation consequence；interaction point IP-1）
- 前置条件：同 A-1。
- 操作步骤：在同一封来信的「自动回复预判」区查看判定。
- 预期结果：判定为 **`转人工`**，硬性闸门列表非空。（落地前同一封信可能显示 `可自动发`。）

### A-3: LLM 关闭时逐字退化（覆盖 must-NOT-change 第 2 条；I-3）
- 前置条件：先在 A-1 的环境下截图记录该来信每条摘要的状态徽标。
- 操作步骤：设 `LLM_ENABLED=false`，重启，重新打开同一封来信的工作台。
- 预期结果：状态徽标回到本计划落地**之前**的值（该条恢复为 `依据充分`）；
  诊断区不再显示未识别诉求；日志中 `[ASK_ENUM] ... available=false`。

### A-4: 自动回复路径未被波及（覆盖 must-NOT-change 第 3 条；interaction point IP-3）
- 前置条件：确认未设置 `talent-introduction.llm.ask-enumerator.enabled-for-auto-reply`
  （仓库内无该 yml 键，默认 `false`，见 `AskEnumeratorProperties.kt:22`）。
- 操作步骤：让一封同样"无问号无 bullet"的来信走一次自动回复预判（不经工作台）。
- 预期结果：日志中该封信的 `[ASK_ENUM] source=AUTO ... available=false`；
  自动回复的判定与本计划落地前一致。

### A-5: 「申请所需材料」这条事实变为可选中（覆盖 outcome 2；I-5、interaction point IP-5）
- 前置条件：后台「QA 规则」页确认 `Getting started materials` 处于启用、事实正文非空。
- 操作步骤：
  1. 准备一封来信，正文含 `Could you tell me about the application process and the next steps?`
  2. 打开工作台，展开该条摘要，查看「已选事实」。
- 预期结果：`Getting started materials` 出现在已选事实中（落地前它在任何来信上都不会出现）。

### A-6: coverage key 编辑不受影响（覆盖 must-NOT-change 第 4 条；I-4）
- 前置条件：任选一条既有启用规则（例如 `Application process`）。
- 操作步骤：在后台「QA 规则」页打开该规则，不做任何修改，直接点保存；再重新打开。
- 预期结果：保存成功，重新打开后 coverage key 的**显示顺序与保存前完全一致**；
  下拉可选项中新增了「投入时间」与「顾问期限」两项，且它们排在列表最后。
