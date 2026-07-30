# 修复计划：AI 回复第 5A 步：敏感材料否定语义（fix-1）

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-05a-action-policy-negation-plan.md`
- 轮次：1/3；无既往 5A fix plan。

## 约束摘录

1. I-1：否定仅豁免自身作用域；同一文本单元的后续正向敏感材料 CTA 仍必须拦截。
2. I-2：`findViolations()` 与 `sanitize()` 同源；无违规字节一致；有违规时仅删除命中的原始 span，仅折叠删除接缝空行。
3. 护照、身份证、工作/在职证明、银行证明的正向直接索取继续返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；不新增公开 enum、DTO、状态、DB 字段或前端行为。

## 修正记录表

| P1 | 触发频率 | 证据 | 问题 |
|---|---|---|---|
| P1-1 | 常见英文措辞 | `AiReplyActionPolicy.kt:33-35,255-267` | `ID card` 不在敏感材料 family；`Please send your ID card.` 不会返回 sensitive code。现有否定测试以 `identity card` 替代验收指定文本，掩盖缺口。 |
| P1-2 | 只要同一句含安全否定后续 CTA 即触发 | `AiReplyActionPolicy.kt:157-160,255-267` | 判定只返回句级 Boolean，sanitize 删除整个 `TextUnit`；`We do not request an ID card, but please send your passport.` 会连安全否定前缀一起删除。 |

## 修复规格

### P1-1：补全验收指定材料别名

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`。
- 将 `ID card` 纳入与 `identity card`、`national ID` 同一英文敏感材料 matcher family；不新增材料类别或公开类型。
- 断言正向 `Please send your ID card.` 在任意 allowed 集合下产生唯一 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`，sanitize 删除命中 CTA。
- 断言验收原文 `We do not request an ID card.` 无 violation 且 sanitize byte-identical；不得只以未命中材料词而“通过”。

### P1-2：以精确 CTA span 清理，保留否定说明

- 文件同 P1-1。
- 将共享敏感判定收口为可同时供 `findViolations()` 与 `sanitize()` 使用的匹配结果，至少包含正向 CTA 的原始 offset；否定 clause 不得贡献删除范围。
- `We do not request an ID card, but please send your passport.` 必须报告唯一 sensitive violation，sanitize 仅删除 `please send your passport` 的原始 CTA span，前置安全说明、分隔符和其余未命中文本保持原样（仅允许既有删除接缝空行规则）。
- 增加英/中文否定后续正向 CTA 回归，以及无违规 CRLF/LF byte-identical 回归；继续复用现有 tokenizer/接缝规则，不新增 DTO/状态。

## 当前状态（修复前）

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests compile`）。默认 Maven 使用 JDK 25，会因 Kotlin 1.9.25 的 `JavaVersion.parse(25.0.1)` 失败，属于本机工具链问题。
- 定向测试：现有 `AiReplyActionPolicyTest` surefire 报告为 16 passed、0 failed；但未覆盖 P1-1 的正向 `ID card`，亦未断言 P1-2 的否定前缀保留。

## 合规审计

- I-1 否定局部作用域：❌ `AiReplyActionPolicy.kt:255-267` 能识别逗号/`but` 后的护照 CTA，但 `ID card` 未匹配，且未提供精确 CTA span。
- I-2 同源与版式：❌ `findViolations()` 和 `sanitize()` 都调用 `detectSensitiveMaterial()`（`128,157`），但 `sanitize()` 在 `159` 删除整个句单元，违反“只删命中 span”。
- 正向敏感材料仍阻断：❌ `AiReplyActionPolicy.kt:33-35` 未覆盖 `ID card`；护照/工作证明覆盖见 `33-35,234-267`。
- 不新增公开状态/DTO/前端/DB：✅ `AiReplyAction` 保持两项（`3-6`）；仅内部 `ActionViolation.code` 默认字段（`8-12`）。
- Deleted code：✅ 无计划要求删除代码。
- No extras：❌ 当前两个文件还含 CV 用途/自愿性和 trust-state 逻辑；它们不属于 5A，作为既有并行计划改动观察，不纳入本修复。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无状态机。
- Cross-plan check：❌ action-policy → DraftService → fallback/readiness 链中，P1-2 会在 `AiReplyDraftService.kt:513-518` 的 sanitize 后错误删除安全说明；修复后需回归：正向 CTA 触发 validation/retry，安全否定保留，删除结果不误损正文，5B 的 `removed` 降级语义仍成立。
