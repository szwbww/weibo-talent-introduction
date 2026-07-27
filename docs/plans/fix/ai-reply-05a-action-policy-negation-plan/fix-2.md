# 修复计划：AI 回复第 5A 步：敏感材料否定语义（fix-2）

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-05a-action-policy-negation-plan.md`
- 前序复验：`docs/plans/fix/ai-reply-05a-action-policy-negation-plan/fix-1.md`
- 轮次：2/3；P1 数量 `2 → 1`，满足收敛要求。

## 约束摘录

1. I-1：否定只豁免自身作用域；同一文本单元中后续正向敏感材料 CTA 必须拦截。
2. I-2：`findViolations()` 与 `sanitize()` 共用敏感 CTA 判定；无违规逐字不变；有违规时只删除命中的原始 span，只清理删除接缝空行。
3. 护照、身份证、工作/在职证明、银行证明的正向直接索取继续返回 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`；不新增公开 enum、DTO、状态、数据库字段或前端行为。

## 修正记录表

| P1 | 触发频率 | 证据 | 问题 |
|---|---|---|---|
| P1-1 | 每次敏感 CTA span 结束于文本末尾或分句末尾时 | `AiReplyActionPolicy.kt:157-161,272-297` | `findPositiveSensitiveCtaSpans()` 产生闭区间 `start..end-1`，`sanitize()` 再用 `until span.last` 转换，二次排除了末字符。`Please send your ID card.` 会留下 `.`；测试只检查材料词消失，未检查完整 span 删除。 |

## 修复规格

### P1-1：保持 span 的闭区间语义

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`。
- `sanitize()` 将 `findPositiveSensitiveCtaSpans()` 返回的闭区间直接平移到全文 offset，不能再用 `until span.last` 截短最后一个字符。
- 保持 `findViolations()` 与 `sanitize()` 复用同一 `findPositiveSensitiveCtaSpans()`；不修改 tokenizer、公开模型或接缝空行规则。
- 新增精确断言：`Please send your ID card.` sanitize 后为 `""`；`We do not request an ID card, but please send your passport.` 的结果不含 `passport`、`send` 或残留的 CTA 末尾句点，并保留否定说明及其未命中分隔文本。另回归 LF/CRLF 的无违规 byte-identical 和正向材料 code。

## 当前状态（修复前）

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests compile`）。
- 定向测试：PASS — 18 passed，0 failed，0 skipped（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyActionPolicyTest test`）。
- 全量测试：未完成；本机 Surefire 在启动后因 `PpidChecker` 无法读取父进程信息而中断（`target/surefire-reports/2026-07-19T20-52-38_646-jvmRun1.dump`），非当前两文件的编译或断言失败。

## 合规审计

- I-1 否定局部作用域：✅ `AiReplyActionPolicy.kt:272-297` 按 clause 检测；`AiReplyActionPolicyTest.kt:302-313` 覆盖否定后续正向 CTA。
- I-2 同源与版式：❌ `AiReplyActionPolicy.kt:157-161` 对已闭合的 `span` 再使用 `until span.last`，使 sanitize 未完整删除命中的原始 span；违反只删除命中 span 的完整性。无删除逐字返回的分支见 `174-175`，安全说明回归见测试 `272-299,328-335`。
- 正向敏感材料继续阻断：✅ `AiReplyActionPolicy.kt:33-35,257-263`；`AiReplyActionPolicyTest.kt:316-325,338-355`。
- 不新增公开状态/DTO/前端/DB：✅ `AiReplyActionPolicy.kt:3-12`；修复仅限两个计划内文件。
- Deleted code：✅ 无计划要求删除代码。
- No extras：✅ 本轮仅审计 fix-1 修复涉及的两个计划内文件。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无状态机。
- Cross-plan check：✅ `AiReplyActionPolicy` 仍向 `AiReplyDraftService` 提供相同 violation code 和 `(text, removed)` 契约；P1-1 仅修复 sanitizer 输出的 span 完整性，避免下游 fallback 看到残留 CTA 字符。
