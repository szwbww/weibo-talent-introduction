# AI 回复第 5A-final 步：敏感材料 CTA 作用域契约

本计划替代并关闭 `ai-reply-05a-action-policy-negation-plan.md` 的验证周期；它是新的独立计划，不是旧计划的 fix-4。

## 需求描述

- 可观察结果：敏感材料否定说明保持原文；正向索取覆盖同一动作下的全部并列材料；否定说明后的独立正向索取仍被识别并完整删除。

必须保持不变：

1. 护照、身份证/ID card、工作/在职证明、银行证明的直接索取继续返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`。
2. `findViolations()` 与 `sanitize()` 使用同一敏感 CTA 匹配结果；无删除输入 byte-identical。
3. 既有 CV 用途/自愿性、会议授权、trust-state、Grounded retry/readiness、公开 enum/DTO/state 均不改变。

不在本计划范围：

- 扩展新的材料类别、CTA 动词或自然语言分类体系。
- 修改 `AiReplyDraftService`、materializer、自动回复 decision、前端、数据库或 API。
- 对本计划验收矩阵之外的新语言变体建立新的阻断要求；后续新变体另立计划。

## 关键不变量

### Invariant I-1: 并列材料继承同一正向 CTA
- Rule：`and/和/以及` 右侧仅有敏感材料名时，不建立新动作边界；右侧材料继承左侧正向 CTA，整个索取 span 必须被识别。若右侧以独立正向 CTA 开始，且左侧处于否定作用域，则建立新动作边界，否定不得遮蔽右侧正向索取。
- Applies to：`findPositiveSensitiveCtaSpans()`、`detectSensitiveMaterial()`、`findViolations()`、`sanitize()`。
- Violation consequence：并列的银行证明、身份证等敏感材料残留在最终正文，绕过最终动作门禁。
- 来源：K-sensitive-cta-compound-material-coverage、K-sensitive-material-cta-not-mention。

### Invariant I-2: 否定作用域只覆盖自身动作段
- Rule：`do not request/do not need to provide/不需要提供/不索要` 只否定自身动作段；`but/但是/但` 后的正向 CTA，或 `and/并且` 后显式重新出现的正向 CTA，必须独立判定。安全否定段不得因后续违规被删除。
- Applies to：敏感 CTA 分段、正向/否定判定以及删除 range 计算。
- Violation consequence：安全说明被误删，或前置否定掩盖真实索取。
- 来源：K-sensitive-material-cta-not-mention、K-sensitive-action-span-granularity。

### Invariant I-3: span 统一为半开区间且检测清理同源
- Rule：内部敏感 CTA span 统一表示为 `[start, endExclusive)`；不得把 Kotlin 闭区间再次作为 half-open 端点转换。`findViolations()` 以共享 span 是否非空判定，`sanitize()` 直接平移并删除同一 span。
- Applies to：`findPositiveSensitiveCtaSpans()` 与 `sanitize()` 的 offset 平移。
- Violation consequence：句末标点、最后一个字符或并列材料残留。
- 来源：K-action-sanitizer-inclusive-offset、K-sensitive-action-span-granularity。

### Invariant I-4: 无匹配逐字不变，删除只影响命中 CTA
- Rule：无敏感 CTA 时返回原字符串且 `removed=false`；有匹配时只删除命中的动作段，并继续仅在删除接缝折叠过量空行，保留 LF/CRLF、其他段落和签名。
- Applies to：`sanitize()`、`appendCollapsedSeam()` 及回归测试。
- Violation consequence：安全邮件版式或否定说明被破坏。
- 来源：K-action-sanitizer-preserve-layout。

### Invariant I-5: 下游契约不变
- Rule：每个敏感 CTA 仍产生唯一 `ActionViolation(REQUEST_MATERIALS, ..., AI_REPLY_ACTION_SENSITIVE_MATERIAL)`；不改变 `ActionViolation`、`AiReplyAction`、`sanitize(): Pair<String, Boolean>` 的形状。
- Applies to：`AiReplyDraftService` 的 validation/retry 与 fallback sanitizer 只读调用点。
- Violation consequence：Grounded 候选绕过统一修复，或 fallback/readiness 无法感知删除。
- 来源：K-ai-reply-action-cta-variant-coverage、K-grounded-action-violation-must-retry、K-grounded-sanitize-removal-readiness。

## 现状审计

### 内存敏感动作匹配
- Schema/mapping：无持久化数据；`ActionViolation` 与 `(sanitizedText, removed)` 是进程内契约。
- Write paths：
  1. `AiReplyActionPolicy.findViolations()` — 读取敏感 matcher，写稳定 violation code。
  2. `AiReplyActionPolicy.sanitize()` — 读取同一 matcher span，写最终安全正文及 removed 标志。
- Read paths：
  1. `AiReplyDraftService.materializeAndValidateGroundedCandidate()` — violation 触发统一一次修复。
  2. `AiReplyDraftService.buildGroundedResult()/groundedFallbackResult()` — removed 进入 warning/readiness 处理。
  3. `AiReplyGroundedDraftMaterializer` — `detectActions()` 读取公开动作类型；本计划不改其语义。
- Interaction points：action matcher → Grounded validation/retry；sanitizer removed → fallback/readiness。两条下游链只做回归，不修改文件。
- 当前缺陷：`AiReplyActionPolicy.kt` 的 `CLAUSE_BOUNDARY` 无条件按 `and` 分段，`Please send your passport and bank statement.` 的后半段失去 CTA 动词。
- 计划建立时基线：production blob `451909505f455ccce31b54c679064f584f0d240d`；test blob `a35ac1fd2289f80e860fa3875482457cb5b3e3ac`。

## 实现方案

### T1：用“独立动作边界”替代无条件连接词切分
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`。
- 保留句级 tokenizer；敏感 CTA 内部只在 `but/但是/但` 或右侧显式重新出现 CTA/否定动作开头时建立边界。
- `and/和/以及` 后只有材料名时视为同一动作的材料列表；左侧否定、右侧显式 `please send/请发送` 时切为两个动作段。
- 将共享 span 表示改为 `Pair<start, endExclusive>` 或等价的明确 half-open 结构，不新增 class、enum、DTO 或状态。
- 遵守：I-1、I-2、I-3、I-5。

### T2：建立封闭表驱动回归矩阵
- 文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`。
- 对下列矩阵同时断言 violation code、removed 与完整 sanitize 输出；不得只断言某个敏感词消失：
  1. `We do not request an ID card.` → 无 violation，原文不变。
  2. `We do not request an ID card, but please send your passport.` → 仅保留 `We do not request an ID card`。
  3. `Please send your passport and bank statement.` → 唯一 sensitive violation，输出空串。
  4. `Please send your passport, ID card, and bank statement.` → 唯一 sensitive violation，输出空串。
  5. `We do not request a passport and bank statement.` → 无 violation，原文不变。
  6. `We do not request a passport and please send your bank statement.` → 仅保留 `We do not request a passport`。
  7. `此阶段不需要提供护照和银行证明。` → 无 violation，原文不变。
  8. `此阶段不需要提供护照，但请发送银行证明。` → 仅保留 `此阶段不需要提供护照`。
  9. 安全 LF/CRLF 多段正文 → 原文 byte-identical；违规独立行删除后其余布局保持既有契约。
- 遵守：I-1、I-2、I-3、I-4、I-5。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 独立动作边界、并列材料继承、half-open span。 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 封闭矩阵与完整输出断言。 |

## 验收标准

- I-1：矩阵 3/4 的所有并列材料完整删除；矩阵 6 的右侧显式 CTA 不被左侧否定遮蔽。
- I-2：矩阵 1/2/5/6/7/8 的否定作用域和保留正文逐字符合预期。
- I-3：矩阵 2/3/4/6/8 不残留句末标点、连接词或材料名；代码中 span 端点只有一种语义。
- I-4：矩阵 1/5/7/9 byte-identical；既有 LF/CRLF seam 测试继续通过。
- I-5：每个正向敏感 case 只有一个 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；既有 `AiReplyActionPolicyTest` 全通过。
- 编译：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests compile`。
- 定向测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyActionPolicyTest test`。
- 静态检查：`git diff --check`；变更仅限清单两个代码文件及本计划/知识计数。

## 人工验收清单

### A-1：并列敏感材料全部阻断
- 前置条件：测试 LLM 返回 `Please send your passport and bank statement.`。
- 操作步骤：1. 触发 Grounded 草稿生成；2. 查看 warning 与最终正文。
- 预期结果：warning 含 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；该请求不出现在最终正文；最多进入一次统一修复。
- 覆盖：I-1、I-3、I-5、interaction matcher → Grounded validation。

### A-2：否定说明保留、后续正向请求删除
- 前置条件：测试 LLM 返回 `We do not request an ID card, but please send your passport.`。
- 操作步骤：1. 触发草稿生成；2. 查看最终安全正文。
- 预期结果：保留 `We do not request an ID card`；不含 `but`、`please send` 或 `passport`；warning 含 sensitive code。
- 覆盖：I-2、I-3、I-4。

### A-3：纯否定说明逐字不变
- 前置条件：测试 LLM 返回 `We do not request a passport and bank statement.`。
- 操作步骤：触发草稿生成并查看正文与 warning。
- 预期结果：该句逐字保留；不出现 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；不因 sanitizer 产生 removed 状态。
- 覆盖：I-1、I-2、I-4、must-not-change 1/2。

### A-4：既有 CV/会议动作语义不变
- 前置条件：分别构造已授权合规 CV 请求、未授权 CV 请求和未授权会议请求。
- 操作步骤：逐一调用现有草稿生成入口。
- 预期结果：已授权且含用途/自愿性的 CV 请求通过；未授权 CV/会议请求仍产生既有 violation；公开 action type 仍只有 `REQUEST_MATERIALS/PROPOSE_MEETING`。
- 覆盖：I-5、must-not-change 3、interaction action policy → DraftService。
