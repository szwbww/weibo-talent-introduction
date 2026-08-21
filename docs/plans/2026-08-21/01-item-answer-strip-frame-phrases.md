# 计划 01：条目答案剥离邮件框架语气词

- 基线：`main` @ `b02c40b`
- 顺序：本计划**先行**，与 `02-bound-facts-become-partial-evidence.md` 无代码耦合，可独立部署与验收
- 子系统数：1（AI 逐条生成）
- 变更文件数：5

---

## 需求描述

### Observable outcome

1. 「按回答说明生成」与「确认待补充」两种处理方式产出的条目答案，**不再包含**称呼行（`Dear …`）、开场致谢句（`Thank you for your email/message…`）、结尾客套与落款（`Please let us know if you have any further questions.` / `Best regards,`）。整合成整封邮件后，这三类语句**只由 frame 片段提供一次**。
2. 模型偶尔不遵守提示词时，服务端确定性剥离兜住，运营看到的是干净答案，而**不是**「AI 未能产出可用的回答」。

### What must NOT change

- `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` 两条 grounded 链路的正文与 claim 逐字不变（它们的 prompt 早已含 `Claims must not contain salutations, fixed thank-you phrases, sign-offs`，见 `AiReplyDraftService.kt:2144`）。
- `AiReplyPointByPointComposer.composeLockedItems` 的拼接逻辑与块顺序（SALUTATION → GREETING → ACK → 答案 → CLOSING）不变。
- `ReplySnippetService` 与 `V47__create_reply_snippet.sql` 的 frame 片段内容不变。
- G2 合规（`findViolations`：敏感材料、CV 目的 / 自愿）判据与触发时机不变。
- 生成失败的判据不因本计划新增（剥离**只减不增**失败路径）。

### Out of scope

- 不改 `AiReplyActionPolicy` 的任何正则。
- 不改 SSE 错误码透出（P0 已做）。
- 不为「剥离发生了」增加前端可见提示——只落 `warningCodes`，UI 展示留待后续。
- 不动 `QaFactBodyPolicy`（QA 事实正文的入库校验），仅**对齐口径**，不复用其实现（那是 `require` 抛异常语义，本计划要的是静默剥离）。

---

## 关键不变量

### Invariant I-1：剥离在 answerText 定稿之前完成
- Rule：剥离后的文本是该条目 `AiReplyItemAnswer.answerText` 的**唯一**取值；`versionId()` 哈希、`operatorInstructionHash` 之外的一切下游校验，看到的都必须是剥离后的文本。禁止在 composer、preview、发送侧做二次剥离。
- Applies to：`AiReplyDraftService.generateOperatorDirectedAnswer`、`AiReplyDraftService.generatePendingAcknowledgement`。
- Violation consequence：若在 composer 侧剥离，`TrustReplyWorkbenchService.validateLockedItem` 与 `materializeVersion` 会拿到与锁定时不同的文本 → `TRUST_REPLY_ITEM_VERSION_INVALID`，整合按钮永久 422。
- 来源：K-locked-answer-paragraphs-at-version-time（「段落结构属于 answerText 本身，在版本创建时一次性规范化完成；composer 保持逐字」）

### Invariant I-2：剥离永不把答案清空
- Rule：`AiReplyFramePhrasePolicy.strip(text)` 的返回值若为空白，必须返回**原始 text 原样**，并追加 warning `AI_REPLY_FRAME_PHRASE_STRIP_SKIPPED`。剥离本身在任何情况下都不得成为「生成失败」的成因。
- Applies to：`AiReplyFramePhrasePolicy.strip` 的全部调用点。
- Violation consequence：模型只回了一句 `Best regards,` 之类的退化输出时，剥离会把它清空 → `candidate.isBlank()` → `FALLBACK_NO_RESPONSE` → 复现用户刚摆脱的「AI 未能产出可用的回答」，且原因比现在更隐蔽。
- 来源：original

### Invariant I-3：剥离是块级/句首级的，不做全局压缩
- Rule：无删除时**逐字返回原文**；有删除时只删除整块（blank-line 分隔块）或首块的**开场致谢整句**，其余字符与换行逐字保留，仅规范删除接缝处的多余空行。禁止 `joinToString(" ")`、禁止全局空白压缩、禁止 `trim()` 之外的重排。
- Applies to：`AiReplyFramePhrasePolicy.strip`。
- Violation consequence：邮件换行、编号、缩进被摧毁；前端 `white-space:pre-wrap` 无法恢复服务端已丢失的布局。
- 来源：K-action-sanitizer-preserve-layout

### Invariant I-4：区间端点语义显式，不做闭/开区间转换
- Rule：本策略只按「块索引」与「句尾偏移」切分，不引入 span API；若实现中出现 `IntRange`，必须在同一函数内保持同一端点语义，禁止把闭区间再转 `until`。
- Applies to：`AiReplyFramePhrasePolicy` 全部内部函数。
- Violation consequence：剥离后残留孤立句点或末字符（历史反例：`Please send your ID card.` 输出 `.`）。
- 来源：K-action-sanitizer-inclusive-offset

### Invariant I-5：称呼与落款按「整块匹配」，开场致谢按「首句匹配」
- Rule：
  - 称呼块：**整块**匹配 `^(Dear|Hi|Hello)\b[^.\n]{0,60}[,，]?$` 才删，且只删**开头连续**的这类块。
  - 落款块：**整块**匹配落款词表（`Best regards` / `Kind regards` / `Warm regards` / `Regards` / `Sincerely` / `Yours sincerely` / `Yours faithfully` / `Best wishes` / `Thanks` / `Thank you`，可带尾逗号）或整块匹配 `Please let us know if you have any (further |other )?questions?` 才删，且只删**结尾连续**的这类块。
  - 开场致谢：只在**剩余首块的句首**删一句，且该句须同时含致谢动词与来信名词（`email|message|note|reply|enquiry|inquiry|getting in touch|reaching out|writing`）。
- Applies to：`AiReplyFramePhrasePolicy.strip`。
- Violation consequence：正文中间合法出现的 `Dear`（如引用对方原话）、`Thank you for your patience`（无来信名词）、`we thank the reviewers` 被误删，答案语义受损且运营无从察觉。
- 来源：original（口径对齐 `QaFactBodyPolicy.kt:9-18` 的四条禁令，但判据从「整串 containsMatchIn 后抛异常」收紧为「整块/首句匹配后静默删除」）

### Invariant I-6：两条链路共用同一策略对象
- Rule：`generateOperatorDirectedAnswer` 与 `generatePendingAcknowledgement` 必须调用**同一个** `AiReplyFramePhrasePolicy.strip`，不得各写一份正则或各自内联实现。
- Applies to：上述两个函数。
- Violation consequence：两条链路口径漂移，同一封信里一条被剥离一条没有，运营无法形成稳定预期；后续加词表要改两处必漏一处。
- 来源：original（同类教训见 K-locked-answer-paragraphs-at-version-time 的「必须抽成单一常量，禁止各写字面量」）

### Invariant I-7：提示词与剥离是两道独立防线，都要有
- Rule：两条链路的 system prompt 必须显式禁止称呼/开场致谢/落款；确定性剥离**不得**因为提示词已写而省略。
- Applies to：`generateOperatorDirectedAnswer` 的 system message、`generatePendingAcknowledgement` 的 system message、`AiReplyFramePhrasePolicy.strip` 的两个调用点。
- Violation consequence：只有提示词 → 漏网时静默进外发邮件；只有剥离 → 模型持续产出被删的内容，等于浪费 token 且删除接缝更容易出现语义断裂。
- 来源：original

---

## 现状审计

本计划不触及任何数据库表、ES 索引或缓存；被改动的是**内存中的一条文本**在生成链路上的取值。审计对象因此是「该文本的写入点与读出点」。

### 条目答案文本（`AiReplyItemAnswer.answerText`）

- 结构：`AiReplyDraftService.kt:349` `RequestFactItem` / `AiReplyItemAnswer(requestIndex, requestText, status, answerText, claims)`。无持久化 schema；经 `TrustReplyWorkbenchStateStore` 的 `payload_json` 落快照。

- **写路径（生成侧，本计划改前两条）**：
  1. `AiReplyDraftService.generateOperatorDirectedAnswer`（`:639-771`）—— `candidate = observed.content?.trim().orEmpty()`（`:744`），随后 `:745` 判 `invalid`，`:764` 写入 `answerText = candidate`。**本计划在 `:744` 与 `:745` 之间插入剥离。**
  2. `AiReplyDraftService.generatePendingAcknowledgement`（`:563-636`）—— `candidate = observed.content?.trim()`（`:609`），`:610` 交 `claimValidator.validateNoEvidenceAcknowledgement`，`:617` 写入 `answerText = candidate`。**本计划在 `:609` 与 `:610` 之间插入剥离。**
  3. `AiReplyDraftService.generateItem` 的 grounded 分支（`:466` 之后）—— 走 `contentPlanner.buildPlan` + claim 物化，prompt 已含禁令（`:2144`）。**不改。**
  4. `AiReplyDraftService.safeAcknowledgementResult`（`:797`）—— 安全模板兜底，文本来自内部常量，本就不含 frame 语句。**不改。**
  5. `TrustReplyWorkbenchService.materializeVersion`（`:1472`）—— 只做 `normalizedAnswer` 与哈希，不改写语义。**不改。**

- **读路径**：
  1. `AiReplyPointByPointComposer.composeLockedItems(orderedAnswers, resolvedFrame)`（`AiReplyPointByPointComposer.kt:35-45`）—— 在答案外层按 SALUTATION → GREETING → ACK → 答案 → CLOSING 插块，`joinToString("\n\n")`，**对答案不做任何 trim / 格式化**。这是重复问题的直接现场。
  2. `TrustReplyWorkbenchService.validateLockedItem`（`:1360-1430`）—— 按 handling 分支校验；`ANSWER_FROM_OPERATOR_INPUT` 分支（`:1393-1413`）判 `answerText.isBlank()`、`claims.isNotEmpty()`、`findViolations`；`ACKNOWLEDGE_PENDING` 分支（`:1382-1392`）判 `validateNoEvidenceAcknowledgement`。
  3. `TrustReplyWorkbenchService.materializeVersion` → `versionId()`（`:1890-1908`）—— `normalizedAnswer` 进哈希。
  4. `AiReplyDraftPreviewService.preview`（`TrustReplyWorkbenchService.kt:1276`）—— 拿 composer 产物做变量渲染与预览。
  5. `PendingMailOperationService.sendManualRichReply`（`:144` 起）—— 消费 `trustReplyAssembly.lockedItems`。

- **Interaction points**：
  - **IP-1**（写 1/2 × 读 1）：生成侧插入剥离 → composer 不再遇到重复的 frame 语句。这是本计划的目标交互。
  - **IP-2**（写 1/2 × 读 2）：剥离后的文本必须仍能通过 `validateLockedItem` 的 blank / claims / `findViolations` / `validateNoEvidenceAcknowledgement` 四项。**剥离掉的都是无动作的客套句，不会新增动作违规；但若剥离清空了文本会踩 `answerText.isBlank()`** → 由 I-2 兜住。
  - **IP-3**（写 1/2 × 读 3）：剥离改变 `answerText` → `versionId` 改变。**只影响本次生成产生的新版本**；存量已锁定条目的 `answerText` 不被回溯改写，因此不会批量失效（与 K-locked-answer-paragraphs-at-version-time 里「改 answerText 规范化会让存量 locked item 失效」的场景**不同**：那次改的是 join 分隔符这类**所有**版本共用的规范化，本次改的是**生成时刻**的一次性清洗）。若运营对同一条目重新生成，新版本自然替换旧版本，走既有流程。

### frame 片段（只读，作为重复对象的证据）

- `src/main/resources/db/migration/V47__create_reply_snippet.sql:14-16` 默认值：
  - `SALUTATION` = `Dear Professor,`
  - `GREETING` = `Thank you for your email. Please find our answers below.`
  - `CLOSING` = `Please let us know if you have any further questions.` + 落款
- 解析入口：`ReplySnippetService.resolveManualFrame()` / `resolveFrame(selection)`（`ReplySnippetService.kt:31-33`、`:101-104`）。
- **本计划不写这张表**，仅据其内容确定剥离词表（I-5）。

### 前端

本计划**不触及任何前端文件**，故不设 `## 样式契约`。

---

## 实现方案

### 阶段 1：新增剥离策略对象

**T1.1** 新建 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyFramePhrasePolicy.kt`。（I-2 / I-3 / I-4 / I-5 / I-6）

对象为无状态 `object`，暴露单一入口：

```kotlin
data class FrameStripResult(val text: String, val stripped: Boolean, val skipped: Boolean)

object AiReplyFramePhrasePolicy {
    fun strip(text: String): FrameStripResult
}
```

行为规格（逐条对应不变量）：

1. `text.isBlank()` → 原样返回，`stripped=false, skipped=false`。
2. 按 `Regex("""\n\s*\n""")` 切块，**保留每块内部的原始字符**（I-3）。
3. 从**头部**起，连续删除整块匹配称呼式的块（I-5 第一条）。
4. 从**尾部**起，连续删除整块匹配落款式或收尾客套式的块（I-5 第二条）。
5. 在剩余**首块**的句首，删除至多一句开场致谢（I-5 第三条）；句边界复用与 `AiReplyActionPolicy.SENTENCE_SPLIT` 相同的判据（`(?<=[.!?。！？])\s+`），但本策略**自带一份私有常量**，不 import `AiReplyActionPolicy` 的 private 成员。
6. 剩余块以 `"\n\n"` 重新拼接后 `trim()`。
7. 若结果 `isBlank()` → 返回**原始 text**，`stripped=false, skipped=true`（I-2）。
8. 若结果与原 text 逐字相等 → `stripped=false, skipped=false`。

**T1.2** 新增常量 `const val WARNING_FRAME_PHRASE_STRIPPED = "AI_REPLY_FRAME_PHRASE_STRIPPED"` 与 `const val WARNING_FRAME_PHRASE_STRIP_SKIPPED = "AI_REPLY_FRAME_PHRASE_STRIP_SKIPPED"`，置于 `AiReplyDraftService` 的 `companion object`（与既有 `WARNING_ENGLISH_REPLY_REQUIRED`（`:2387`）同处，保持 warning 常量单一归属）。

### 阶段 2：接入两条链路

**T2.1** `AiReplyDraftService.generateOperatorDirectedAnswer`（`AiReplyDraftService.kt:639-771`）。（I-1 / I-6 / I-7）

- 在 `:744` 的 `val candidate = observed.content?.trim().orEmpty()` 之后立即调用 `AiReplyFramePhrasePolicy.strip(candidate)`，得到 `cleaned`。
- `:745` 起的 `invalid` 判据全部改用 `cleaned.text`：`cleaned.text.isBlank() || INTERNAL_RESPONSE_MARKER.containsMatchIn(cleaned.text) || AiReplyActionPolicy.findViolations(cleaned.text, allowedActions).isNotEmpty()`。
- `:764` 的 `answerText = candidate` 改为 `answerText = cleaned.text`（I-1）。
- `warnings` 追加：`stripped` 为真加 `WARNING_FRAME_PHRASE_STRIPPED`；`skipped` 为真加 `WARNING_FRAME_PHRASE_STRIP_SKIPPED`。两者互斥。

**T2.2** `AiReplyDraftService.generatePendingAcknowledgement`（`AiReplyDraftService.kt:563-636`）。（I-1 / I-6 / I-7）

- 在 `:609` 的 `val candidate = observed.content?.trim()` 之后调用同一策略（`candidate` 为可空，`null` 时跳过）。
- `:610` 的 `claimValidator.validateNoEvidenceAcknowledgement(...)` 改为对剥离后文本求值。
- `:617` 的 `answerText = candidate` 改为剥离后文本。
- warning 追加规则同 T2.1（并入既有 `validation?.warningCodes.orEmpty()` 之后）。

**T2.3** 两条链路的 system prompt 各加一句禁令（I-7），逐字如下，追加在现有 system message 末尾、`withActionBoundary` 追加动作边界之前：

> `Do not include a salutation, an opening thank-you line, a closing courtesy line, or a sign-off. The reply frame supplies those separately. Begin directly with the substance of the answer.`

- `generateOperatorDirectedAnswer` 的 system message：`AiReplyDraftService.kt:687-702`（`Never ask for passports…` 之后）。
- `generatePendingAcknowledgement` 的 system message：`AiReplyDraftService.kt:587-589`（`Do not add facts, numbers…` 之后）。

### 阶段 3：测试

**T3.1** 新建 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyFramePhrasePolicyTest.kt`，覆盖 I-2 / I-3 / I-5 的正反例（清单见 `## 验收标准`）。

**T3.2** 在 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` 增加两条链路的端到端用例：桩 LLM 返回带 `Dear Josep,` / `Thank you for your message.` / `Best regards,` 的文本，断言 `itemAnswer.answerText` 不含这三者且实体句逐字保留。

**T3.3** 在 `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` 增加一条：剥离后的答案能通过 `validateLockedItem`（IP-2）并被 `composeLockedItems` 整合出**恰好一个** `Dear Professor,` 与**恰好一个** `Best regards`。

---

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyFramePhrasePolicy.kt` | 新增 | 剥离策略对象（T1.1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | 两条链路接入 + 两处 prompt + 两个 warning 常量（T1.2 / T2.1 / T2.2 / T2.3） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyFramePhrasePolicyTest.kt` | 新增 | 策略单测（T3.1） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | 两条链路端到端（T3.2） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | 锁定 + 整合回归（T3.3） |

合计 5 个文件，1 个子系统。

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。JS 单测由 `exec-maven-plugin` 绑定在 `test` 阶段（`pom.xml:184-203`），`mvn test` 已覆盖。

```bash
# 全量测试（回归门禁；含 Kotlin 单测与 node --test 前端单测）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试类（单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyFramePhrasePolicyTest

# 本计划相关的既有测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn test` 汇总行 `BUILD SUCCESS`）。
来源：项目根 `CLAUDE.md` 「Commands」章节（`CLAUDE.md:9-27`）与 `test_command:` / `build_command:` 项目元信息（`CLAUDE.md:140,142`）；JS 单测绑定见 `pom.xml:184-203` 实测。

---

## 验收标准

- **I-1**：`grep -n "answerText = " src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` — `generateOperatorDirectedAnswer` 与 `generatePendingAcknowledgement` 两处的赋值右值均为剥离后变量，不是 `candidate`。另 grep 确认 `AiReplyPointByPointComposer.kt` 与 `TrustReplyWorkbenchService.kt` 中**零** `AiReplyFramePhrasePolicy` 引用（禁止二次剥离）。
- **I-2**：`AiReplyFramePhrasePolicyTest` 断言 `strip("Best regards,")` 返回 `text == "Best regards,"`、`skipped == true`、`stripped == false`；`strip("Dear Josep,\n\nBest regards,")` 同样原样返回且 `skipped == true`。
- **I-3**：`AiReplyFramePhrasePolicyTest` 断言无可删内容时 `strip(input).text === input`（逐字，含多行缩进与编号列表）；有删除时对整段输出做**全等断言**（不得只断言词消失），验证换行与编号未被压缩。
- **I-4**：`grep -n "until" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyFramePhrasePolicy.kt` 零命中（本策略不引入 span 运算）。
- **I-5**：`AiReplyFramePhrasePolicyTest` 反例断言 —— 以下输入必须**逐字不变**：
  - `We are pleased to hear from you. Thank you for your patience while we complete the review.`（致谢句缺来信名词）
  - `You asked whether "Dear Colleague" is an acceptable salutation for our template.`（`Dear` 不在块首且非整块）
  - `Regards from the Shanghai office were passed along.`（落款词出现在句中，非整块）
- **I-6**：`grep -rn "AiReplyFramePhrasePolicy.strip" src/main/kotlin/` 恰好 2 处命中，且均在 `AiReplyDraftService.kt`；`grep -rn "Best regards\|Dear\\\\b" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 中不出现新的正则字面量（词表只存在于策略文件）。
- **I-7**：`grep -n "Begin directly with the substance of the answer" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` 恰好 2 处命中。
- **IP-2 集成**：`TrustReplyWorkbenchItemFlowTest` 新用例 —— 剥离后的 `ANSWER_FROM_OPERATOR_INPUT` 答案通过 `validateLockedItem` 不抛异常，且 `claims` 为空、`findViolations` 为空。
- **IP-1 集成**：同一用例整合后的正文中，`Dear Professor,` 出现次数 == 1，`Best regards` 出现次数 == 1，`Thank you for your email` 出现次数 == 1。
- **回归**：执行 `## 验证命令` 节的全量测试命令通过；`## 验证命令` 节的构建命令通过。

---

## 人工验收清单

### A-1：按回答说明生成不再自带称呼与落款
- 前置条件：一封已进工作台的来信，其中至少一条摘要为 `UNSUPPORTED`；回复框架三个片段均为 `V47` 默认值（`Dear Professor,` / `Thank you for your email. Please find our answers below.` / `Please let us know if you have any further questions.` + 落款）。
- 操作步骤：
  1. 该条目「处理方式」选「按回答说明生成」。
  2. 回答说明填：`请用英文回复。现阶段希望专家方便时提供简历（at your convenience），以便我们做资格初核（initial eligibility review）并匹配合适的企业；后续可安排一次 Zoom 视频会议详谈。`
  3. 点「重试 AI 调整」，等待生成完成。
- 预期结果：生成的条目答案**第一个字符**不是 `Dear`；全文不含 `Best regards`、不含 `Please let us know if you have any further questions`、不含以 `Thank you for your email` 或 `Thank you for your message` 开头的首句。答案首句直接是实质内容（例如以 `At this stage` / `We would be grateful` 之类开头）。
- 覆盖：需求描述 Observable outcome 1

### A-2：整合后的整封邮件里每种框架语句只出现一次
- 前置条件：承 A-1，该条目已「采用」并锁定；该来信的其余摘要条目也各自锁定（任意处理方式）。
- 操作步骤：
  1. 点「整合为整封回复」。
  2. 在预览里用浏览器查找（Cmd+F）分别搜 `Dear Professor`、`Thank you for your email`、`Best regards`。
- 预期结果：三个词各命中 **1 次**；`Dear Professor,` 在正文最顶部、`Best regards,` 在正文最底部，中间**不再**出现任何称呼或落款。
- 覆盖：需求描述 Observable outcome 1；IP-1

### A-3：确认待补充也不带框架语句
- 前置条件：同一封来信中另有一条摘要，处理方式可选「确认待补充」。
- 操作步骤：该条目选「确认待补充」，生成，查看条目答案。
- 预期结果：答案不以 `Dear` 开头，全文不含 `Best regards`、不含 `Please let us know if you have any further questions`。
- 覆盖：需求描述 Observable outcome 1（第二条链路）

### A-4：模型不听话时不再变成生成失败（回归 must-NOT-change）
- 前置条件：无法直接构造模型输出，用测试替代 —— 由开发在 `AiReplyDraftServiceTest` 中提供桩用例，验收人核对测试报告。
- 操作步骤：查看 `## 验证命令` 节 `-Dtest=AiReplyDraftServiceTest` 的运行输出。
- 预期结果：存在名称含 `frame` / `salutation` 的用例，且 `Failures: 0, Errors: 0`；其中包含「模型只返回落款」的用例，断言结果为原文返回而非生成失败。
- 覆盖：需求描述 Observable outcome 2；I-2

### A-5：依据完整回答 / 回答有依据部分未受影响（回归 must-NOT-change）
- 前置条件：一封来信中存在 `GROUNDED` 或 `PARTIAL` 摘要条目。
- 操作步骤：该条目按推荐处理方式生成，与本次改动**之前**的同条件产出逐字比对（可用改动前的一次生成结果截图/文本留底）。
- 预期结果：正文逐字相同；claim 数量与 `sourceRuleIds` 相同。
- 覆盖：需求描述 What must NOT change 第 1 条

### A-6：G2 合规仍然生效（回归 must-NOT-change）
- 前置条件：`UNSUPPORTED` 条目一条。
- 操作步骤：处理方式选「按回答说明生成」，回答说明填 `请专家提供护照复印件`，生成。
- 预期结果：仍然拿不到可发送的答案（显示「AI 未能产出可用的回答，请重试或换一种处理方式。」），即敏感材料闸未被本计划削弱。
- 覆盖：需求描述 What must NOT change 第 4 条

### A-7：回复框架片段可配置性未受影响（回归 must-NOT-change）
- 前置条件：在片段管理里把 `SALUTATION` 改成 `Dear Colleague,` 并启用。
- 操作步骤：重新整合一封回复，查看正文首行。
- 预期结果：首行为 `Dear Colleague,`（说明 frame 仍由片段决定，未被剥离逻辑写死或误删）。
- 覆盖：需求描述 What must NOT change 第 3 条
