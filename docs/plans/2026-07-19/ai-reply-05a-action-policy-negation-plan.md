# AI 回复第 5A 步：敏感材料否定语义

## 需求描述

- 可观察结果：包含敏感材料词但明确否定索取的邮件正文，保持原文，不触发 action violation 或删除；同一文本单元中后续出现正向索取时仍被拦截。

必须保持不变：

1. 护照、身份证、工作/在职证明、银行证明的正向直接索取继续返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`。
2. `findViolations()` 与 `sanitize()` 使用同一判定；无删除输入逐字不变，删除只清理接缝空行。
3. 不新增 `AiReplyAction`、DTO、状态、数据库字段或前端行为。

不在本计划范围：

- Grounded retry、fallback readiness 和自动回复 reason。
- CV 用途/自愿性、会议授权或 matcher 之外的动作语义扩展。

## 关键不变量

### Invariant I-1: 否定只豁免其作用域内的索取
- Rule：`You do not need to provide a passport.`、`We do not request an ID card.` 等否定索取不得被识别为直接 CTA；同一文本单元中在否定作用域外的后续正向 CTA（如 `but please send your passport`）必须仍被识别。
- Applies to：`AiReplyActionPolicy.detectSensitiveMaterial()`，以及调用它的 `findViolations()` 与 `sanitize()`。
- Violation consequence：安全说明被删掉并触发错误 fallback，或否定前缀掩盖真实敏感材料索取。
- 来源：K-sensitive-material-cta-not-mention。

### Invariant I-2: 检测与清理同源且保留版式
- Rule：同一输入在 `findViolations()` 与 `sanitize()` 对敏感 CTA 的判定一致；无违规时 sanitize 返回 byte-identical 文本，有违规时只删命中的原始 span。
- Applies to：`AiReplyActionPolicy.findViolations()`、`sanitize()`、共享 token/offset 逻辑。
- Violation consequence：运行时修复与最终正文不一致，或破坏邮件段落和签名。
- 来源：K-action-sanitizer-preserve-layout、K-ai-reply-action-cta-variant-coverage。

## 现状审计

### 内存动作策略
- Schema/mapping：`ActionViolation(action, sentence, code)` 是内部内存对象；`AiReplyAction` 仅有 `REQUEST_MATERIALS`、`PROPOSE_MEETING`。
- Write paths：
  1. `AiReplyActionPolicy.findViolations()`：按 `detectSensitiveMaterial()` 创建敏感材料 violation。
  2. `AiReplyActionPolicy.sanitize()`：按同一方法收集要删除的 offset range。
- Read paths：
  1. `AiReplyDraftService.materializeAndValidateGroundedCandidate()`：把 violation 转成候选校验 warning。
  2. `AiReplyDraftService.groundedFallbackResult()` 与 FREE_FORM action seam：消费 sanitize 的 `(text, removed)`。
- Interaction points：一条敏感 CTA 的 matcher 结果同时决定 Grounded 重试和最终正文删除；两者必须同源。(来源: K-grounded-action-violation-must-retry)

## 实现方案

### T1：按材料 CTA 的局部否定作用域判断
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`
- 用局部否定/正向 CTA 关系替换当前“删除首个否定词后再全句查 CTA”的判断：否定的 `need/request/provide` 不能本身重新成为正向 CTA；后续独立正向 CTA 不得因前置否定而豁免。
- 继续让 `findViolations()` 和 `sanitize()` 只调用共享 sensitive 判定；不改公开 enum、`ActionViolation` 形状和 token/删除接缝规则。
- 新增/调整单测：英文 `do not need to provide`、`do not request`、中文否定说明均无 violation 且 byte-identical；`do not request ... but please send ...` 与中英文正向祈使/疑问仍产生 sensitive code 并被删除。
- 遵守：I-1、I-2。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 收紧敏感材料否定作用域判定。 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 否定/后续正向 CTA 及版式回归。 |

## 验收标准

- I-1：`do not need to provide a passport`、`do not request an ID card` 和对应中文安全说明不产生 violation；同句后续 `please send your passport` 产生唯一 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`。
- I-2：对上述安全说明，`findViolations()` 为空且 sanitize 返回原字符串；对正向 CTA，二者都判定违规，sanitize 只删命中单元并保持其余 CRLF/LF 文本。
- 定向测试：`mvn -Dtest=AiReplyActionPolicyTest test`。

## 人工验收清单

### A-1：否定安全说明保留
- 前置条件：使用测试环境的 AI 草稿生成入口，生成正文包含 `You do not need to provide a passport at this stage.`。
- 操作步骤：生成草稿并查看正文与 warning。
- 预期结果：正文保留整句；不出现 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`，不进入修复/fallback。
- 覆盖：I-1、I-2。

### A-2：后续正向索取仍阻断
- 前置条件：测试 LLM 首稿为 `We do not request an ID card, but please send your passport.`。
- 操作步骤：触发一次 Grounded 草稿生成。
- 预期结果：首稿不作为可采用草稿；warning 含 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`，最终正文不保留索要护照的句子。
- 覆盖：I-1、现状审计 interaction point。
