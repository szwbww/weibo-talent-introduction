# fix-1：AI 回复第 5B 步 Grounded fallback readiness

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-05b-grounded-fallback-readiness-plan.md`
- 复验轮次：1/3
- 既有 fix 文档：无。

## 约束摘录

1. `groundedFallbackResult()` 的优先级固定为 repair exhausted → `BLOCKED`，sanitize 删除 → `NEEDS_REVIEW`，否则统一 factual readiness。
2. `resolveDraftReadiness()` 与 `resolveDraftReadinessForSelection()` 必须逐项重读传入 evidence ID；规则缺失、disabled、`answerBody` 空白或 policy 不可解析/为 `NEVER` 时均返回 `BLOCKED`，不得静默缩小 evidence。
3. evidence 可用后，critical/unknown `UNSUPPORTED` 为 `BLOCKED`，PARTIAL 与已分类 noncritical `UNSUPPORTED`、REVIEW 为 `NEEDS_REVIEW`，其余才 `READY`。
4. 不改 schema、迁移、DTO、动作 matcher 或自动回复 reason；修复只限计划列出的 service/test 两文件。

## 修正记录

| P1 | 触发频率 | 根因 | 修复方向 |
|---|---|---|---|
| P1-1 非法 replyPolicy 未 fail-closed | 低频：历史脏数据、手工 SQL 或旁路写入 `qa_rule.reply_policy` 时 | `replyPolicyEnum()` 对非法字符串抛异常，`resolveDraftReadiness()` 未捕获 | 将逐项 policy 解析改为安全校验；解析失败直接 `BLOCKED`，并补回归测试。 |

## 修复规格

### P1-1：policy 解析失败必须返回 BLOCKED

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 改动：在 `resolveDraftReadiness()` 的 evidence 重读阶段，将 `QaRule.replyPolicyEnum()` 的 `IllegalArgumentException` 收敛为该次 readiness 的 `BLOCKED` 返回；保持缺失、disabled、空 `answerBody`、`NEVER` 与既有三态排序不变。
- 文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 改动：构造 enabled、非空 `answerBody` 但 `replyPolicy="invalid"` 的 evidence，断言 `resolveDraftReadiness()` 返回 `BLOCKED` 且不抛异常。
- 简化要求：仅在现有函数内做安全解析，不新增状态、DTO、迁移、恢复机制或额外 service。

## 当前状态（修复前）

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests compile`）。默认 JDK 25 会被 Kotlin 1.9.25 拒绝，属本机运行时兼容性，不是本计划代码失败。
- 测试：PASS — `mvn -Dtest=AiReplyDraftServiceTest test`，117 passed，0 failed，0 skipped（JDK 11）。

## 合规审计

- I-1 fallback priority：✅ `AiReplyDraftService.kt:577-580` 按 exhausted、removed、factual readiness 排序；`AiReplyDraftServiceTest.kt:3228-3247`、`3251-3278` 覆盖 removal 与 exhausted 优先级。
- I-2 evidence 当前可用性：❌ `AiReplyDraftService.kt:835-845` 已 fail-closed 处理缺失、disabled、空正文，但 `:847` 的 `replyPolicyEnum()` 会由 `QaRule.kt:17-22` 对非法值抛异常，而非返回 `BLOCKED`；`reply_policy` 是无 CHECK 约束的 `VARCHAR(16)`，见 `V80__add_qa_reply_policy.sql:2`。
- I-3 三态排序：✅ `AiReplyDraftService.kt:805-864` 先 BLOCKED critical/unknown，再 PARTIAL/noncritical/REVIEW 为 NEEDS_REVIEW；`AiReplyDraftServiceTest.kt:3396` 覆盖 noncritical UNSUPPORTED。
- 删除代码：✅ 无本计划要求删除项。
- No extras：✅ 仅计划列出的 service/test 被实现改动触及。

## 语义完整性检查

- Accumulation check：✅ N/A，无时间窗口累计量。
- State machine check：✅ N/A，无新增状态机。
- Cross-plan check：✅ `UNAUTHORIZED_ACTION_REMOVED` 仍通过 `AiReplyDraftResult.contextWarnings` 输出（`AiReplyDraftService.kt:570-580`）；自动回复 reason 映射属于已拆分的 05C 范围，未在本子计划扩改。
