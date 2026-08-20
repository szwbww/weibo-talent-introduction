# P2b：让运营绑定的事实真正进入 AI 上下文（不进外发审计）

> 顺序权威：本目录 `00-execution-order.md`。基线与"符号名优先、行号仅交叉验证"的约定见该文件。
> **前置：P0、P1、P2a 必须先合并；线 A（`workbench-operator-instruction-authorizes-actions.md`）也必须先合并。**
> 本刀是四刀里唯一**跨线**的一刀：阶段 B 要修订线 A 落地的 operator-directed system prompt。开工前请确认线 A 已合并且其测试全绿。

---

## 需求描述

### Observable outcome

1. 运营给一条 `GROUNDED` / `PARTIAL` 摘要补绑一条事实后，AI 生成时**能引用该事实的正文**，即便该事实没有落进任何 `SUPPORTED` 意图的证据集。
2. 运营给一条 `UNSUPPORTED` 摘要绑定事实并选「按回答说明生成」后，AI **可以引用这些事实的正文**来组织回答，而不再只能复述回答说明。
3. 上述两种情况下，绑定但非证据的事实**都不会**出现在该封外发邮件的 QA 事实使用审计里。

### What must NOT change

1. **`sendQaRuleIds` 恒为证据**：`workbenchResult` 的 `sendIds`（`QaFactSelectionService.kt:301`）与 `generateItem` 的 `sendQaRuleIds`（`AiReplyDraftService.kt:475`）继续只由 `factRuleIds` 合成。外发审计（`mail_record_qa_rule`）永远不记绑定但非证据的事实。
2. **claims 与 `general.answer` 兜底不变**：`canonicalizeClaims`（`TrustReplyWorkbenchService.kt:1424-1425`）与 `AiReplyGroundedContentPlanner`（`:73-80`）继续读 `factRuleIds`。claim 必须有据。
3. **CRS 打分不变**：`AutoReplyConfidenceScorer.kt:54` 继续按 `factRuleIds` 计数。绑定不得拉高自动回复置信分。
4. **status / `allowedHandlings` 不变**（承接 P2a）。
5. **动作授权不变**：线 A 的 `OPERATOR_DIRECTED_ALLOWED_ACTIONS`、`findViolations`、G2 合规判定（敏感材料 / CV 目的 / CV 自愿）一律不动。本刀只碰 prompt 里的**事实通道**，不碰**动作通道**。
6. **自动回复链路零变化**：`GroundedAutoReplyDecisionService` / `AutoMailReplyService` 的 fail-closed 行为不得获得任何旁路。
7. **无存储变更**：不新增表、字段、索引、迁移。
8. **无前端变更**：本刀不改 `src/main/resources/static/` 下任何文件。

### Out of scope（显式推迟）

1. **让绑定影响 status**（需求方已否决，见 `00-execution-order.md`）。
2. **`resolveLegacySelection` 的同款改造**（触发面极小，承接 P2a Out of scope 第 3 条）。
3. **在 UI 上区分"证据事实"与"参考事实"的视觉分组**。P2a 的 chips + 一条提示已足够表达，视觉分组属独立设计。
4. **把 `boundRuleIds` 引入自动回复的 prompt**。自动路不经过运营绑定，无对象。

---

## 关键不变量

### Invariant I-1: prompt 用绑定，审计用证据——两个通道必须分叉
- Rule: 本刀只把 `promptRuleIds` 的来源从 `factRuleIds` 改为「`factRuleIds` 与 `boundRuleIds` 的有序并集」；`sendQaRuleIds` **一律不动**。
  并集规则：`(factRuleIds + boundRuleIds).distinct()`——证据在前、绑定补在后，保证既有条目（两者相等时）的 `promptRuleIds` **逐字不变**。
- Applies to: `QaFactSelectionService.workbenchResult`（`:295-320`）的 `promptRuleIds`；`AiReplyDraftService.generateItem` 的 `ResolvedQaRules.promptRuleIds`（`:476`）。
- Violation consequence: 动了 `sendQaRuleIds` → 运营随手绑的事实进外发审计，违反 [[K-ai-reply-prompt-vs-send-rule-ids]]；并集顺序反了 → 既有条目的 prompt 内容顺序变化，无谓地扰动生成结果与相关快照比对。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant I-2: operator-directed 的事实通道与 answer basis 是两个通道
- Rule: 线 A 落地的 operator-directed system prompt 规定「answer basis 是权威内容，只许复述，不得添加 basis 之外的事实」。本刀新增一个**并列的**「可引用事实」通道，其约束必须逐字写清楚：
  - 事实正文**只能**来自服务端注入的这批已绑定事实，模型不得自行发明；
  - answer basis 仍然是**回答的骨架与口径**，事实只是**可引用的素材**；
  - **动作**（索取材料 / 提议会议）仍然只能来自 answer basis——线 A 的 I-5「Do not introduce any outbound action that the answer basis does not state.」**必须原样保留**。
- Applies to: `AiReplyDraftService.generateOperatorDirectedAnswer` 的 system message 与 user message。
- Violation consequence: 不写清"两个通道"→ 模型把事实正文当成 basis 直接复述，绕过运营的口径控制；删掉线 A 的动作约束 → 批量预判产物可能夹带未授权的索取动作（线 A 的 I-5 是那条决策的唯一技术护栏）。
- 来源: original（与线 A 的 I-4/I-5 相交，见现状审计「跨线影响」）

### Invariant I-3: 注入的事实必须是当前有效的
- Rule: 注入 operator-directed prompt 的事实，必须按 `boundRuleIds` 逐个从 `qaRuleRepository` 取当前值，并跳过 `answerBody` 为空的规则——照抄既有 `buildFreeFormUserContent` 的知识块写法（`AiReplyDraftService.kt:2281-2296`）。**不得**复用任何缓存或快照里的旧正文。
- Applies to: 新增的注入逻辑
- Violation consequence: 用旧正文 → 事实库已更正的口径仍被发出去，且与 `evidenceSetVersion` 的"内容哈希"承诺相矛盾。
- 来源: K-qa-rule-enable-must-revalidate-facts

### Invariant I-4: 无绑定时行为逐字不变
- Rule: 当 `boundRuleIds` 为空、或与 `factRuleIds` 完全相等时，本刀的所有改动必须是**恒等变换**：`promptRuleIds` 取值不变，operator-directed 的 prompt 文本**一字不增**（不得出现空的「可引用事实」段落标题）。
- Applies to: I-1 的两处；阶段 B 的注入逻辑
- Violation consequence: 空段落会改变每一条既有 operator-directed 生成的 prompt，使线 A 刚落地的两个断言（`prompt.contains("operator-provided answer basis")` 等）与实际产物漂移，且无谓地改变全部历史行为。
- 来源: original

### Invariant I-5: 事实注入不得削弱 G2 合规
- Rule: 注入的事实正文经过 prompt 后产出的答案，仍须通过 `generateOperatorDirectedAnswer` 出参处的 `findViolations(candidate, allowedActions)` 与 `rejectNonEnglishItemAnswer`。本刀**不得**为让注入的事实通过而放宽任一校验。
- Applies to: `generateOperatorDirectedAnswer` 的出参校验段
- Violation consequence: 事实正文里若含敏感材料措辞，放宽校验等于让它直达外发。
- 来源: K-sensitive-material-cta-not-mention

---

## 现状审计

### Phase 0 知识加载（采用与驳回）

**采用**：
- `K-ai-reply-prompt-vs-send-rule-ids` → I-1（prompt 范围可放宽，审计子集不可）。**本刀是这条知识的直接应用场景。**
- `K-qa-rule-enable-must-revalidate-facts` → I-3（注入前必须取当前值）。
- `K-sensitive-material-cta-not-mention` → I-5。
- `K-operator-directed-authorization-seam`（2026-08-20 新增）→ I-2 的动作约束部分。
- `K-js-tests-run-via-exec-plugin` → 验证命令（本刀虽不改前端，仍需跑前端回归）。

**读取后确认不适用**：
- `K-manual-frame-three-consumers` —— 讲回复 frame（问候/致谢/结束语）的消费者，本刀不碰 frame。
- `K-workbench-state-lazy-expiry` —— 本刀不新增状态存储读写路径。

### 数据存储

**本刀不触及任何数据存储。** 不新增表、字段、索引、迁移；不改任何落库结构。

### 关键路径：`promptRuleIds` 的产生与消费

**证据 E-1 — `promptRuleIds` 的全部产生点与消费点。** 实测 `grep -rn "promptRuleIds" --include=*.kt src/main` 共 14 行：

**产生（3 处）**
| 位置 | 现状 | 本刀 |
|---|---|---|
| `QaFactSelectionService.kt:69` | `promptRuleIds = sendQaRuleIds`（`select()` 老路径） | ❌ 不改（非工作台路径） |
| `QaFactSelectionService.kt:305`（`workbenchResult`） | `promptRuleIds = sendIds` | ✅ **改为并集** |
| `AiReplyDraftService.kt:476`（`generateItem` 逐条 grounded） | `promptRuleIds = requestFact.factRuleIds.distinct()` | ✅ **改为并集** |

**消费（其余）**
`AiReplyDraftService.kt:958` → `generateGrounded`；`:1980-1985` `buildMatchedUserContent`；`:2032-2049` `buildFreeFormUserContent`；`:2133-2138`、`:2281-2296` 把 id 变成 prompt 文本。
**这些消费点一律不改** —— 它们已经会把拿到的 id 全部渲染成知识块。

**证据 E-2 — `promptRuleIds` 渲染成 prompt 的既有写法（I-3 的照抄对象）。** `AiReplyDraftService.kt:2281-2296`：
```kotlin
if (promptRuleIds.isNotEmpty()) {
    val knowledge = promptRuleIds.mapNotNull { ruleId ->
        qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
            val body = rule.answerBody.trim()
            if (body.isBlank()) { return@mapNotNull null }
            val title = rule.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Fact $ruleId"
            "$title\n$body"
        }
    }.joinToString("\n\n").take(12000)
    appendLine("QA rule knowledge (authoritative facts):")
    appendLine(knowledge)
    appendLine("Facts (figures, names, links, commitments) must come from the QA rule knowledge or training knowledge base above; do not invent specifics.")
}
```
逐个从仓库取当前值、跳过空正文、12000 字截断、`isNotEmpty()` 才输出段落——**I-3 与 I-4 的实现照抄这一段**。

### 关键路径：UNSUPPORTED 条目为什么必须单独做（本刀最关键的一条审计）

**证据 E-3 — 改 `promptRuleIds` 对 `UNSUPPORTED` 条目零效果。**
`AiReplyDraftService.generateItem`（`:394`）的分支顺序：

```
:407-417  handling == OMIT                     → 直接 return，不建 ResolvedQaRules
:422-436  handling == ACKNOWLEDGE_PENDING      → return generatePendingAcknowledgement(...)
:438-451  handling == ANSWER_FROM_OPERATOR_INPUT → return generateOperatorDirectedAnswer(...)
:474-480  （以下才是 grounded 路）val resolved = ResolvedQaRules(sendQaRuleIds = …, promptRuleIds = …)
```

而 `validateItemHandling`（`:787` 区段）规定 `UNSUPPORTED` 的允许集是
`{ANSWER_FROM_OPERATOR_INPUT, ACKNOWLEDGE_PENDING, OMIT}` —— **三者全部在 `:474` 之前 return**。

**结论：`UNSUPPORTED` 条目永远走不到 `:476`。** 只改 `promptRuleIds` 的话，本刀对"运营给 UNSUPPORTED 条目绑事实"这个原始场景**一点效果都没有**。

**证据 E-4 — `generateOperatorDirectedAnswer` 当前完全不读事实。** 其 user message 只拼四样（线 A 落地后的当前形态）：`requestKey`、`Target question`、可选的 `Recipient context`（`expertProfile`）、`operator-provided answer basis`（`instruction`）。**没有任何事实通道。**

因此本刀必须分两段：阶段 A 覆盖 `GROUNDED` / `PARTIAL`（走 `promptRuleIds`），阶段 B 覆盖 `UNSUPPORTED`（给 operator-directed 加事实通道）。

### 跨线影响：与线 A 的 prompt 契约相交

线 A 在 `generateOperatorDirectedAnswer` 的 system message 里落地了两条与本刀相交的约束：
- 「The operator-provided answer basis is authoritative content. Only restate or organize it; do not add any institution, programme, funding, contract, time, identity, number, URL, or other fact not present in that basis.」
- 线 A 的 I-5：「Do not introduce any outbound action that the answer basis does not state.」

**本刀会修改第一条**（把"事实只能来自 basis"放宽为"事实可来自 basis 或服务端注入的已绑定事实"），**并原样保留第二条**（动作仍只能来自 basis）。

这是一次**有意的、显式的契约修订**，不是顺手改：
- 线 A 的 I-4（CV 合规句式）与 I-5（动作不得凭空引入）**不受影响**，本刀的 I-2 明文要求保留。
- 线 A 的测试用例 `operator directed item uses only target question and operator answer basis`（`AiReplyDraftServiceTest`）断言了 prompt 只含目标问题与 basis。**本刀必须更新该用例**：改为"无绑定事实时仍只含目标问题与 basis"（即 I-4 的恒等性），并新增"有绑定事实时才出现事实通道"的用例。

### Interaction points

| # | 写/产生 | 读/消费 | 影响 | 验收 |
|---|---|---|---|---|
| IP-1 | `workbenchResult` 的 `promptRuleIds` 并集 | `generateGrounded`（`:958`）→ `buildFreeFormUserContent`（`:2049`）→ 知识块（`:2281-2296`） | 整封生成时绑定的事实进 prompt | I-1 / A-1 |
| IP-2 | `generateItem` 的 `promptRuleIds` 并集（`:476`） | 同上，逐条 grounded 生成 | 逐条生成时绑定的事实进 prompt | I-1 / A-1 |
| IP-3 | `sendQaRuleIds`（**不动**） | `buildEvidenceSnapshotForSelection`（`:2394`）→ `evidenceSetVersion`；`mail_record_qa_rule` 审计 | 绑定不得改变证据快照与审计 | I-1 / A-3 / A-4 |
| IP-4 | operator-directed 新增的事实通道 | 线 A 的出参校验 `findViolations` / `rejectNonEnglishItemAnswer` | 注入的事实正文若含敏感措辞，必须仍被拦 | I-5 / A-5 |
| IP-5 | operator-directed system prompt 的修订 | 线 A 的既有测试断言 | 必须同步更新线 A 的用例，否则红 | I-2 / IP-5 见 D-3 |

---

## 实现方案

### 阶段 A：`promptRuleIds` 改为有序并集（I-1 / I-4）

**A-1. `QaFactSelectionService.workbenchResult`（`:295-320`）**

```kotlin
// 改前
val sendIds = requestFacts.sortedBy { it.index }.flatMap { it.factRuleIds }.distinct()
return ResolvedQaRules(
    sendQaRuleIds = sendIds,
    promptRuleIds = sendIds,
    ...

// 改后（I-1/I-4）
// P2b (I-1): 外发审计只认证据（sendIds 不变）；prompt 可以多看运营绑定的事实。
// 并集顺序固定为「证据在前、绑定补在后」，保证两者相等时 promptIds 与 sendIds 逐字相同（I-4）。
val ordered = requestFacts.sortedBy { it.index }
val sendIds = ordered.flatMap { it.factRuleIds }.distinct()
val promptIds = (sendIds + ordered.flatMap { it.boundRuleIds }).distinct()
return ResolvedQaRules(
    sendQaRuleIds = sendIds,
    promptRuleIds = promptIds,
    ...
```

**A-2. `AiReplyDraftService.generateItem`（`:474-480`）**

```kotlin
// 改前
val resolved = ResolvedQaRules(
    sendQaRuleIds = requestFact.factRuleIds.distinct(),
    promptRuleIds = requestFact.factRuleIds.distinct(),
    ...

// 改后（I-1/I-4）
val resolved = ResolvedQaRules(
    sendQaRuleIds = requestFact.factRuleIds.distinct(),
    // P2b (I-1): 证据在前、绑定补在后；无绑定或两者相等时逐字等于 sendQaRuleIds（I-4）。
    promptRuleIds = (requestFact.factRuleIds + requestFact.boundRuleIds).distinct(),
    ...
```

**A-3. `QaFactSelectionService.kt:69` 的 `promptRuleIds = sendQaRuleIds` 一字不改**（`select()` 是非工作台的老路径，那里没有运营绑定的概念）。

### 阶段 B：operator-directed 的事实通道（I-2 / I-3 / I-4 / I-5）

> **开工前确认线 A 已合并**，否则 system message 的基线文本对不上。

**B-1. `generateItem` 把绑定的事实传下去（`:438-451`）。**
给 `generateOperatorDirectedAnswer(...)` 的调用补一个参数，传 `requestFact.boundRuleIds`。函数签名同步增加该参数。**`requestFact` 本身不新增字段**（`boundRuleIds` 由 P2a 落地）。

**B-2. `generateOperatorDirectedAnswer` 内构造事实块（I-3 / I-4）。**

照抄证据 E-2 的写法，逐个取当前值、跳过空正文、截断：

```kotlin
// P2b (I-3): 逐个从仓库取当前正文，跳过空的；不得复用任何缓存或快照。
// P2b (I-4): 为空时【什么都不输出】——不得出现空的段落标题，否则每一条既有
// operator-directed 生成的 prompt 都会变，与线 A 刚落地的断言漂移。
val boundFactsBlock = boundRuleIds.mapNotNull { ruleId ->
    qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
        val body = rule.answerBody.trim()
        if (body.isBlank()) return@mapNotNull null
        val title = rule.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Fact $ruleId"
        "$title\n$body"
    }
}.joinToString("\n\n").take(12000)
```

user message 在 `operator-provided answer basis` **之后**追加（仅当 `boundFactsBlock.isNotBlank()`）：
```kotlin
if (boundFactsBlock.isNotBlank()) {
    appendLine("Facts the operator attached to this question (reference material, not the answer basis):")
    appendLine(boundFactsBlock)
}
```

**B-3. system message 的契约修订（I-2）。**

线 A 落地的这一句：
```
The operator-provided answer basis is authoritative content. Only restate or organize it;
do not add any institution, programme, funding, contract, time, identity, number, URL, or other fact
not present in that basis.
```
改为（**逐字**）：
```
The operator-provided answer basis is authoritative content and defines what the reply says.
Only restate or organize it. You may additionally quote or paraphrase the attached reference
facts when they support that answer basis, but you must not add any institution, programme,
funding, contract, time, identity, number, URL, or other fact that appears in neither the
answer basis nor the attached reference facts.
```

**线 A 的动作约束原样保留，一字不改**（I-2）：
```
Do not introduce any outbound action that the answer basis does not state.
```
——注意这句仍然只提 answer basis，**不包含** reference facts，即事实通道**不能**成为动作的授权来源。

**B-4. 出参校验一字不改**（I-5）：`findViolations(candidate, allowedActions)`、`INTERNAL_RESPONSE_MARKER`、`rejectNonEnglishItemAnswer` 全部保持原样。

### 阶段 C：测试

**C-1. `QaFactSelectionServiceTest` —— 新增 2 个用例**
- `prompt rule ids include bound facts while send rule ids do not`：绑定 1 条非证据事实 → 断言 `promptRuleIds` 含它、`sendQaRuleIds` **不含**它（I-1 / IP-3）。
- `prompt rule ids are identical to send rule ids without extra bindings`：无绑定分叉 → 断言两者**逐字相等**（I-4）。

**C-2. `AiReplyDraftServiceTest` —— 新增 3 个用例**
- `operator directed item injects attached facts as reference material`：`boundRuleIds` 给 1 条事实 → 断言捕获的 prompt 含该事实的标题与正文，且含 `reference material, not the answer basis` 字样（I-2 / B-2）。
- `operator directed item without bound facts keeps the prompt unchanged`：`boundRuleIds` 为空 → 断言 prompt **不含** `Facts the operator attached` 段落标题（I-4）。
- `operator directed item still blocks an unauthorised action from an attached fact`：注入一条正文里含索取材料措辞的事实、且来信未表达提供意愿 → 断言结果仍被 `findViolations` 判废（`lockable == false`）（I-5 / IP-4）。

**C-3. 更新线 A 的既有用例（IP-5）。**
`AiReplyDraftServiceTest` 的 `operator directed item uses only target question and operator answer basis`：其断言前提由"prompt 只含目标问题与 basis"改为"**在没有绑定事实时** prompt 只含目标问题与 basis"。用例名同步加上 `without attached facts` 后缀，并在注释里写明它现在验证的是 I-4 的恒等性。

**C-4. `TrustReplyWorkbenchItemFlowTest` —— 新增 1 个用例**
- `bound facts never enter the send audit rule ids`：整封汇总后断言 `qaRuleIds` / `sendQaRuleIds` 不含绑定但非证据的 id（must-NOT-change 第 1 条 / IP-3）。

---

## 变更文件清单

| # | 文件 | 改动性质 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | `workbenchResult` 的 `promptRuleIds` 改为并集 | A-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `generateItem` 的 `promptRuleIds` 改为并集；`generateOperatorDirectedAnswer` 加参数、加事实块、改 system message | A-2, B-1〜B-3 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 新增 2 个用例 | C-1 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 新增 3 个用例 + 更新线 A 的 1 个用例 | C-2, C-3 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 新增 1 个用例 | C-4 |

**文件数：5（≤10 ✓）**
**子系统数：1 ✓** —— AI 生成的事实上下文。
**新增存储字段：0 ✓　前端文件：0 ✓　`styles.css` 改动：0 ✓　HTTP 契约变更：0 ✓**
**无 `## 样式契约` 节** —— 变更清单不含任何前端文件，Step 1b-fe 未触发。

---

## 验证命令

> 全量测试、构建、前端全量、语法检查、空白卫生一律见 `00-execution-order.md`。

```bash
# 本刀相关的后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest

# C-1 两条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#prompt rule ids include bound facts while send rule ids do not'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#prompt rule ids are identical to send rule ids without extra bindings'

# C-2 三条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyDraftServiceTest#operator directed item injects attached facts as reference material'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyDraftServiceTest#operator directed item without bound facts keeps the prompt unchanged'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyDraftServiceTest#operator directed item still blocks an unauthorised action from an attached fact'

# C-4 一条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#bound facts never enter the send audit rule ids'
```

**通过判据**：同 `00-execution-order.md`。

---

## 验收标准

- **I-1**：`grep -n "sendQaRuleIds = " src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 的每一处右侧表达式**都不含** `boundRuleIds`；`promptRuleIds` 的两处右侧**都含** `boundRuleIds` 且并集顺序为「factRuleIds/sendIds 在前」。C-1 两条用例绿。
- **I-2**：`git diff` 中 operator-directed system message 逐字包含 `neither the answer basis nor the attached reference facts`，且**仍逐字包含** `Do not introduce any outbound action that the answer basis does not state.`（线 A 的 I-5 未被破坏）。
- **I-3**：新增的事实块代码含 `qaRuleRepository.findById(ruleId)`，且含 `if (body.isBlank()) return@mapNotNull null` 与 `.take(12000)`——与 `:2281-2296` 的既有写法同构。
- **I-4**：C-1 第二条与 C-2 第二条用例绿；`boundFactsBlock.isNotBlank()` 的条件判断存在（不得无条件 append 段落标题）。
- **I-5**：`generateOperatorDirectedAnswer` 出参校验段（`findViolations` / `INTERNAL_RESPONSE_MARKER` / `rejectNonEnglishItemAnswer`）在 diff 中**零改动**。C-2 第三条用例绿。
- **must-NOT-change 第 1〜3 条**：`git diff --name-only` 中**不含** `AiReplyGroundedContentPlanner.kt`、`AutoReplyConfidenceScorer.kt`、`AiReplyReviewAuditService.kt`、`PendingMailOperationService.kt`、`TrustReplyWorkbenchService.kt`。C-4 用例绿。
- **must-NOT-change 第 8 条**：`git diff --name-only` 中不含 `src/main/resources/static/` 下任何文件。
- **IP-5**：线 A 的用例 `operator directed item uses only target question and operator answer basis` 已按 C-3 更新且绿。
- **回归**：执行 `00-execution-order.md` 的全量测试与构建通过；前端全量与 `node --check` 通过；`git diff --check` 无输出。

---

## 人工验收清单

### A-1: 有据条目补绑的事实能被 AI 引用（本刀目标之一）
- 前置条件：一条 `GROUNDED` 或 `PARTIAL` 摘要；事实库里另有一条与该问题相关、但**不会**被自动匹配到的事实（例如关键词不重合）
- 操作步骤：
  1. 给该摘要手动绑上那条事实
  2. 生成一次
  3. 阅读产出的英文正文
- 预期结果：正文中出现该事实的内容（口径与事实正文一致）。改动前它只会被绑定、不会被引用。
- 覆盖：observable outcome 1；I-1、IP-1、IP-2

### A-2: 无据条目绑事实 + 按回答说明生成，事实能被引用（本刀目标之二）
- 前置条件：一条 `UNSUPPORTED · 无依据` 摘要
- 操作步骤：
  1. 手动绑上 2 条相关事实
  2. 「处理方式」选「按回答说明生成」
  3. 「回答说明」写一句口径，例如「说明我们能提供的支持范围，并请对方补充研究方向」
  4. 生成
- 预期结果：正文既体现回答说明的口径，**也**引用了绑定事实的内容。改动前正文只能复述回答说明。
- 覆盖：observable outcome 2；I-2、证据 E-3

### A-3: 绑定但非证据的事实不进外发审计（回归，最关键的一条）
- 前置条件：A-1 或 A-2 完成，并把该封信实际发出
- 操作步骤：查看该封外发邮件的 QA 事实使用审计（`mail_record_qa_rule` 关联）
- 预期结果：审计里**只有**系统认可为证据的事实；手动绑定但未成为证据的那几条**不在**其中。
- 覆盖：observable outcome 3；must-NOT-change 第 1 条；IP-3

### A-4: 证据快照与版本身份不受影响（回归）
- 前置条件：一封已锁定若干条目的来信
- 操作步骤：给其中一条摘要补绑一条非证据事实，观察**其它**条目
- 预期结果：其它条目的锁定版本仍在，不出现陈旧提示；整封的 `evidenceSetVersion` 变化只来自被改动的那一条。
- 覆盖：IP-3

### A-5: 事实正文里的敏感措辞仍被拦（回归/安全）
- 前置条件：临时准备一条正文里含「请提供护照复印件」之类措辞的测试事实
- 操作步骤：把它绑到一条 `UNSUPPORTED` 摘要上，选「按回答说明生成」并生成
- 预期结果：产出被判废（显示生成失败），或产出的正文**完全不含**索取护照的句子。任何情况下都不得把该句子发出去。
- 覆盖：I-5、IP-4；must-NOT-change 第 5 条

### A-6: 动作仍只能来自回答说明（回归，守线 A 的决策）
- 前置条件：一条 `UNSUPPORTED` 摘要，绑一条正文里含「可安排线上会议」字样的事实；回答说明里**不提**任何会议
- 操作步骤：选「按回答说明生成」并生成
- 预期结果：正文**不得**主动提议安排会议。事实通道只能提供事实素材，不能成为动作的授权来源。
- 覆盖：I-2；线 A 的 I-5

### A-7: 无绑定时行为完全不变（回归）
- 前置条件：一条从未手动绑过事实的 `UNSUPPORTED` 摘要
- 操作步骤：选「按回答说明生成」，用与升级前相同的回答说明生成一次
- 预期结果：产出风格与内容与升级前一致；生成日志里的 prompt **不含** `Facts the operator attached` 段落。
- 覆盖：I-4

### A-8: 自动回复链路不受影响（回归）
- 前置条件：自动回复功能开启，且有一封符合条件的来信
- 操作步骤：触发一次自动回复决策，查看决策结果与 CRS 分数
- 预期结果：判定与分数构成与升级前一致；存在 `UNSUPPORTED` 项时仍不自动发送。
- 覆盖：must-NOT-change 第 3、6 条

### A-9: 改动范围核对（防越界）
- 前置条件：本刀实现完成，P0/P1/P2a 与线 A 均已提交
- 操作步骤：`git diff --name-only`
- 预期结果：输出恰好为变更文件清单的 5 个路径。特别确认 `TrustReplyWorkbenchService.kt`、`AiReplyGroundedContentPlanner.kt`、`AutoReplyConfidenceScorer.kt`、`AiReplyReviewAuditService.kt`、`PendingMailOperationService.kt`、`src/main/resources/static/` **均不在**其中。
- 覆盖：must-NOT-change 第 2、3、8 条
