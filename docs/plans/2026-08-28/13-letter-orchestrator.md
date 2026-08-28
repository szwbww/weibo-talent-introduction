# 13 编排调用与六道校验（把 12 的确定性收口升级为一次整封 LLM 编排）

顺序权威：`10-reply-orchestration-order.md`。依赖 `12-letter-closer.md`——本计划**替换**其中 `AiReplyLetterCloser` 的第 3 步（主题归并产段），其余步骤（去重、CTA 收口、逃生舱）原样复用。

## 需求描述

**Observable outcome**
1. 段落之间有真正的过渡与因果连接，而不是把同主题的句子用空格接起来。目标形态是人工回信里那种 `The ownership structure **therefore** cannot be determined before the enterprise and project are identified.`
2. 分组与顺序由服务端 `paragraphPlan` / `topicOrder` 给定，LLM 不能改；分错组时改事实的主题即可，不必重跑碰运气。
3. 缺口合并进对应主题段末尾，措辞为条件式（「这取决于 X」），不单独成段、不逐条道歉。

**What must NOT change**
- 12 建立的四条：claims 为收口输入、`sourceRuleIds` 去重身份、canonical order 单调、全锁定逃生舱。
- 受控事实 G1–G4 的正文在最终成文里**逐字出现**（`QaCoverageKeyCatalog.kt:19-42`；需求方 2026-08-28 已确认线上四条与 canonical 逐字一致）。
- 冻结事实 id 1 / 3 / 21 的正文一字不改（G-1）；id 1 的 `${researchFields|your field}` / `${recentWorkTitle|your recent research}` 占位符不得被切分或改写；id 21 自带的 CTA 按 12 的 I-5 处理。
- `canonicalFactIds = selection.sendQaRuleIds`（`TrustReplyWorkbenchService.kt:1488`）不因文本形态变化而改变。
- 逐条生成链路、逐条锁定语义、`versionId` / `requestKey` 算法全部不动（G-7）。
- LLM 不可用 / 超时 / 重试穷尽时，**退回 12 的确定性收口**，assemble 不失败。

**Out of scope**
- 句子级 pinned（需求方已定「先按段落做」）。
- 前端 → 15。
- 索引回流 → 16。
- **G3 canonical body 的措辞变更**（`labor contract` → `written agreement`）：对外法律承诺变更，需需求方书面确认后单独立计划。
- `Application process` / `Agency credentials` / `Partner company information` 的正文改写：仍缺这三条的 `reply_body` 逐字基线（需求方 2026-08-28 提供的是冻结三条 1/3/21 的正文）。

## 关键不变量

### Invariant I-1: 分组与顺序是输入，不是输出
- Rule: 服务端给出 `paragraphPlan: List<ParagraphPlanEntry>`（`topic` + `factIds` + 可选 `gapCondition`）与 `topicOrder`。LLM 返回的 `paragraphs` 必须与之**集合相等**：按 topic 分组的 factIds 集合逐组相等，topic 序列等于 `topicOrder`。
- `paragraphPlan` 由 12 的去重与主题归并结果直接生成——**同一份分组既是确定性兜底的分组，也是 LLM 的输入约束**，两条路径不会给出不同的分段。
- Violation consequence: 分段回到模型临场判断，运营无法通过改主题修正。
- 来源: original

### Invariant I-2: 来源封闭
- Rule: 返回的 `paragraphs[].factIds` 中每个 id 必须在输入 `paragraphPlan` 的 factIds 并集内。出现集合外 id 即整份响应无效，走重试。
- 这道闸也是 `16-unsupported-index.md` 措辞 few-shot 通道能安全放开的**唯一前提**——样例里的命题没有合法 id 可挂。
- 来源: original

### Invariant I-3: required 事实恰好使用一次
- Rule: 12 去重后存活的每条事实，在全信 `paragraphs[].factIds` 并集里**恰好出现一次**。
- 与 12 的分工：12 保证「服务端不要求写两遍」，本计划保证「模型不许自己写两遍」。
- 来源: original

### Invariant I-4: 受控与冻结事实是逐字插槽
- Rule: `controlled` 非 null 的事实（`QaCoverageKeyCatalog.groupIdOf` 求值，取值 `G1`..`G4`）与 `frozen` 为 true 的事实（qa_rule id ∈ {1, 3, 21, 24}），其 `body` 必须作为**子串逐字出现**在引用它的段落 `text` 里。比较前两侧各做一次「压缩连续空白为单个空格 + trim」，不做其他归一化。
- 含 `${...}` 占位符的 body 参与同样的逐字比对——占位符是正文的一部分，被改写即校验失败。
- Violation consequence: 一句已被改写的法律承诺或被冻结的话术以「有依据」的姿态发出。
- 来源: K-controlled-gate-trigger-exact-group；G-1

### Invariant I-5: 动作只走 `actionText` 通道，且沿用 12 的 CTA 收口结论
- Rule: `paragraphs[].text` 不得含动作句；`actionText` 非空时恰好一个被授权的动作。**例外**：12 的 I-5 判定为「冻结事实自带 CTA 且是全信唯一动作来源」时，该动作句留在段落内，`actionText` 为 null。
- 最终正文的 `AiReplyActionPolicy.detectActions` 结果必须等于「`actionText` 声明集合 ∪ 冻结事实豁免集合」。
- 来源: original（12 的 I-4 / I-5 的下游）

### Invariant I-6: 缺口挂主题，不独立成段
- Rule: `paragraphPlan` 条目可带 `gapCondition`。带 gap 的段落 `text` 必须包含对该条件的表述；全信缺口段落数恒为 0；`paragraphs.size == paragraphPlan.size`。
- 来源: original

### Invariant I-7: 六道校验全是服务端确定性判定
- Rule: 六道（G1 来源封闭 / G2 恰好一次 / G3 受控与冻结逐字 / G4 段落零动作 / G5 动作对账 / G6 编排一致）必须以纯函数实现在编排响应的解析器里，**不得**以提示词约束替代任何一道。失败复用既有 `AiReplyValidationIssue` + 重试机制。
- 来源: K-grounded-json-materialize-before-policy

### Invariant I-8: 编排失败必须退回 12，不得让 assemble 失败
- Rule: LLM 不可用、超时、或六道校验重试穷尽后，`AiReplyLetterCloser` 返回其确定性结果（12 的行为），assemble 正常完成并置一条 warning。**编排是增强，不是前置条件。**
- Violation consequence: 一次模型抖动就让运营整合不了信，比现状更差。
- 来源: K-free-form-fallback-nonempty、K-llm-timeout-fallback

## 现状审计

### 12 建立的接缝
`AiReplyLetterCloser`（12 新增）：输入 `(versions, allowedActions)`，五步——展开 claims → `sourceRuleIds` 去重 → 主题归并产段 → CTA 收口 → 逃生舱短路。
本计划**只替换第 3 步**：把「同主题 claim 文本用空格连接」换成「构造 `paragraphPlan` → 一次编排 LLM 调用 → 六道校验 → 取 `paragraphs[].text`」。第 1、2、4、5 步原样保留。

### 可复用的既有机制
- **重试与校验框架**：`AiReplyValidationIssue` / `AiReplyValidationStage` / `AiReplyValidationCodes`（`AiReplyValidationDiagnostic.kt`，64 行）已就位。
- **JSON 解析范式**：`AiReplyGroundedDraftMaterializer.parseUnifiedJson`（`:78-152`）的写法可直接借鉴——顶层字段集合相等判定（`:91`）、逐项字段集合相等（`:105`）、重复键、未知键、空文本与内部标记（`containsInternalMarker`，`:169-173`）。
- **动作策略**：`AiReplyActionPolicy.detectActions`。
- **超时与取消**：`AiReplyTimeoutPolicy`、`AiReplyGenerationCoordinator`（329 行）——编排调用应复用同一套 TTL 与取消语义。
- **受控组查询**：`QaCoverageKeyCatalog.controlledGroups()`（`:45`）与 `groupIdOf(key)`（`:47-48`）。

### 受控事实 canonical body（需求方 2026-08-28 已确认线上四条逐字一致）
- G1 `{confidentiality.materials}` — `Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.`
- G2 `{fees.policy}` — `We never charge any fees throughout the entire process.`
- G3 `{contract.party, contract.terms}` — `After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment.`
- G4 `{ip.arrangements}` — `Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.`

### 冻结事实正文（需求方 2026-08-28 提供，逐字）
- **id 1 `About the talent program`** — 四段，含 `${researchFields|your field}` 与 `${recentWorkTitle|your recent research}` 两处占位符，末段是动作句 `Would you be open to learning more about the program and the possible cooperation format?`
- **id 3 `Application criteria`** — 两段，第二段含 `--`（双连字符）：`We can discuss fit first -- no documents needed at this stage.`
- **id 21 `Meeting arrangement`** — 两段，第二段含动作句 `Could you please let us know when you would be available?`

**三条都不可改正文（G-1），且其中两条自带动作句、一条含变量占位符、一条含 `--`。** 逐字子串比对必须在归一化后仍能命中——归一化只压缩空白，**不得**触碰 `--`、`–`（id 21 的 `15–20` 用的是 en dash）或 `${...}`。

### Interaction points
- **IP-1**：`paragraphPlan` 构造 × 六道校验 —— 同一份分组既是输入又是判据（I-1）。
- **IP-2**：`QaCoverageKeyCatalog.controlledGroups()` × 逐字比对 —— 两侧必须取同一份常量，校验侧不得另抄字面量。
- **IP-3**：本计划的编排结果 × 12 的 CTA 收口 —— 冻结事实的 CTA 豁免要在两处保持一致（I-5）。
- **IP-4**：编排失败 × assemble 完成 —— 退回路径必须无条件可用（I-8）。
- **IP-5**：编排后的段落 × `draftHash`（`TrustReplyWorkbenchService.kt:1485`）—— 同一批事实两次编排可能产出不同文本，`draftHash` 随之不同；前端 preflight 的 draft identity 消费者须复核（`K-ai-preflight-stale-response-draft-identity`）。

### 前端样式盘点
不适用——本计划不触及任何前端文件。

## 实现方案

### T-1：`paragraphPlan` 与事实清单构造（I-1 / I-4 / I-6）
在 `AiReplyLetterCloser` 中，把去重后的存活 claim 转为：
```
PlanFact(id, topic, body, controlled: String?, frozen: Boolean, required: Boolean)
ParagraphPlanEntry(topic, factIds, gapCondition: String?)
```
- `id` 用 `f<ruleId>`；`sourceRuleIds` 为空的 claim 用 `x<n>`（无依据/运营自撰）。
- `controlled` 由 `QaCoverageKeyCatalog.groupIdOf` 对该规则的 coverage key 求值。
- `frozen` = 该规则 id ∈ {1, 3, 21, 24}。
- 单主题事实数 > 4 时按二级分类拆为 `<topic>` / `<topic>.2`。
- 缺口（对应 request 无存活 claim）归到其主题的 `gapCondition`。

### T-2：编排调用（I-7 / I-8）
新增 `AiReplyLetterOrchestrator`：构造提示词（内联 `paragraphPlan`、事实清单含逐字 body、`topicOrder`、gap 条件、动作授权），调用 LLM，解析 `{"paragraphs":[{"topic","factIds","text"}],"actionText"}`。复用 `AiReplyTimeoutPolicy` 与取消语义。**任何失败路径返回 null**，由 `AiReplyLetterCloser` 退回确定性结果（I-8）。

### T-3：六道校验（I-2～I-6）
在 `AiReplyLetterOrchestrator` 的解析器里实现，新增校验码常量：
`ORCH_FACT_ID_UNKNOWN` / `ORCH_REQUIRED_FACT_COUNT_INVALID` / `ORCH_VERBATIM_BODY_MISSING` / `ORCH_ACTION_IN_PARAGRAPH` / `ORCH_PLAN_MISMATCH`（动作对账复用既有）。逐字比对的期望字符串**一律从 `QaCoverageKeyCatalog.controlledGroups()` 与 `PlanFact.body` 取**，不得在校验侧另写字面量（IP-2）。

### T-4：接入
`AiReplyLetterCloser` 的第 3 步改为：先试 `AiReplyLetterOrchestrator`，成功则取其 `paragraphs[].text`，失败则用原确定性归并。第 4 步的 CTA 收口对两条路径同样执行（编排路径下多数已由 I-5 保证，收口作为最后一道网）。

### T-5：测试
1. 六道校验各一个失败用例 + 一个通过用例（`AiReplyLetterOrchestratorTest`）。
2. 受控逐字用例的期望串取自 `QaCoverageKeyCatalog.controlledGroups()`；grep 断言测试文件中不出现硬编码 canonical 字面量。
3. **冻结事实逐字用例**：分别用 id 1 / id 3 / id 21 的真实逐字正文做 fixture，断言：含 `${...}` 的正文被改写 → 校验失败；`--` 被改成 `—` → 校验失败；`15–20` 的 en dash 被改成连字符 → 校验失败。
4. 编排返回 null（模拟超时）→ `AiReplyLetterCloser` 输出等于纯确定性结果，assemble 成功并带 warning（I-8）。
5. `paragraphPlan` 与返回分组不等 → `ORCH_PLAN_MISMATCH`（I-1）。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestrator.kt` | 新增（T-2、T-3） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` | 修改（T-1、T-4；12 新建） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyValidationDiagnostic.kt` | 修改（新增 5 个校验码常量） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestratorTest.kt` | 新增（T-5.1～T-5.3、T-5.5） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloserTest.kt` | 修改（T-5.4；12 新建） |

合计 5 个文件，1 个子系统（llm 编排层）。
**`AiReplyDraftService.kt` 不在清单内**——编排是独立于逐条生成的第二次调用，不改逐条生成的提示词与协议。若实现中发现必须改它，说明接缝判断有误，停止并回 create-p。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterOrchestratorTest,AiReplyLetterCloserTest

# 单条用例
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterOrchestratorTest#'frozen fact body must appear verbatim'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：项目根 `CLAUDE.md` 的 `## Commands` 章节（逐字照抄）。

## 验收标准

- **I-1**：T-5.5 通过。
- **I-2**：`ORCH_FACT_ID_UNKNOWN` 用例通过。
- **I-3**：required 事实出现 0 次 / 2 次的两个用例均触发 `ORCH_REQUIRED_FACT_COUNT_INVALID`。
- **I-4**：T-5.2、T-5.3 通过；`grep -c "Your materials are kept strictly" src/test/kotlin/.../AiReplyLetterOrchestratorTest.kt` == 0（期望串从常量取，不硬编码）。
- **I-5**：`ORCH_ACTION_IN_PARAGRAPH` 用例通过；冻结事实豁免用例通过。
- **I-6**：`paragraphs.size == paragraphPlan.size` 的断言通过；缺口以 `gapCondition` 挂在主题条目上。
- **I-7**：`grep -c "AiReplyValidationCodes\.\|ORCH_" src/main/kotlin/.../AiReplyLetterOrchestrator.kt` 覆盖全部六道。
- **I-8**：T-5.4 通过。
- **回归**：执行「验证命令」节的全量测试命令通过；12 的全部验收项复跑通过。

## 人工验收清单

### A-1: 段落有过渡与因果连接
- 前置条件：11、12 已上线；一封多主题来信。
- 操作步骤：点「一键预判」，整合，通读正文。
- 预期结果：同主题的多条事实被写成一段**连贯的话**，段内出现 `therefore` / `however` / `once ... we will` 之类的连接；不是把句子用空格接起来。与 12 上线后的产出对比，可读性明显不同。
- 覆盖：observable outcome 1

### A-2: 改事实主题即可改分段
- 前置条件：同 A-1。
- 操作步骤：把某条事实的主题改到另一主题，重新整合。
- 预期结果：该事实的内容出现在新主题段落里，无校验失败，一次即成。
- 覆盖：observable outcome 2；I-1

### A-3: 缺口条件式且不独立成段
- 前置条件：一封含 ≥2 个无事实可答问题的来信。
- 操作步骤：整合后通读正文。
- 预期结果：无单独成段的道歉；缺口表述内嵌在对应主题段落里，措辞是条件式；**不出现** `we don't have a confirmed answer` / `pending confirmation`。
- 覆盖：observable outcome 3；I-6

### A-4（回归）: 受控承诺逐字
- 前置条件：一封同时问收费与签约前 IP 的来信。
- 操作步骤：把正文与 G2、G4 两句 canonical 逐字比对。
- 预期结果：两句一字不差出现（可有过渡句包裹）。
- 覆盖：What must NOT change 第 2 项；I-4

### A-5（回归）: 冻结事实逐字，占位符与标点未被改写
- 前置条件：分别构造使 id 1 / id 3 / id 21 被选中的来信。
- 操作步骤：把正文中对应段落与需求方提供的逐字正文比对，重点看 `${researchFields|your field}` 是否完整（渲染后应为专家的研究领域或兜底词）、id 3 的 `--` 是否仍是双连字符、id 21 的 `15–20` 是否仍是 en dash。
- 预期结果：三处全部逐字一致；`${...}` 正常渲染为具体值而非残缺文本。
- 覆盖：What must NOT change 第 3 项；G-1；I-4

### A-6（回归）: 编排失败仍能整合
- 前置条件：把「单次 TTL」调到最小使编排调用必然超时。
- 操作步骤：点「一键预判」并整合。
- 预期结果：整合**成功**，正文是 12 的确定性收口结果（去重、按主题、单 CTA，但无连接词），界面出现一条 warning 说明编排未生效。**不得整合失败。**
- 覆盖：What must NOT change 第 6 项；I-8；IP-4

### A-7（回归）: 审计规则集不变
- 前置条件：对同一封来信记录 12 上线后的 `canonicalFactIds`。
- 操作步骤：13 上线后重跑 assemble，对比。
- 预期结果：完全相同。
- 覆盖：What must NOT change 第 4 项

### A-8（回归）: 12 的四条结论未被破坏
- 前置条件：同 A-1。
- 操作步骤：复跑 12 的 A-1（去重）、A-3（单 CTA）、A-4（会议事实 CTA 不被删改）、A-7（全手动逃生舱）。
- 预期结果：四项预期全部与 12 上线后一致。
- 覆盖：What must NOT change 第 1 项；IP-3
