# 修复计划：AI 回复第 7 步最终发送完整性（fix-3）

## 原计划 / 轮次

- 原计划：`docs/plans/2026-07-20/ai-reply-07-final-send-integrity-plan.md`
- 已实施修复：`fix-1.md`、`fix-2.md`
- 复验轮次：3/3

## P1 收敛

| 既往项 | 结论 |
|---|---|
| fix-1 P1-1 ～ P1-5 | RESOLVED：最终 subject/全部 href、空 QA 风险、payload identity、UNKNOWN 收敛和 SMTP 前边界均已落地。 |
| fix-2 P1-1 | RESOLVED：`renderedSubject` 已进入最终复验；href 正则支持无引号值。 |

## 新 P1

### P1-1：SMTP 诊断泄露到 HTTP 响应

- 约束：I-13 要求 controller 只返回稳定中文信息，不回显凭据、异常堆栈或服务端诊断。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:322-330` 把 `classification.errorSummary` 直接插入 503/422 响应；该值来自 `DeliveredMail.errorDetail`（`SmtpErrorClassifier.kt:18,37,68`），可含 SMTP/认证异常原文。
- 影响/频率：每次明确失败或安全重试失败均可能触发，调用方可取得原始服务端诊断。
- 回归边界：INTRODUCED — 该 HTTP 映射由本计划新增发送协调链引入。

### P1-2：非明确 4xx/5xx 被错误收敛为可重试或永久失败

- 约束：I-8 仅允许明确 4xx 为 `FAILED_SAFE_TO_RETRY`、明确 5xx 为 `FAILED`；其他不可确认结果必须 `DELIVERY_UNKNOWN` 且不得重发。
- 证据：`PendingMailOperationService.kt:650-662` 仅要求 `smtpResponseCode != null`，未限制 400–499/500–599。`SmtpErrorClassifier.kt:81-88` 会把非 2xx/4xx/5xx 的三位码归类为 `TRANSIENT`，因此如 `999` 可被该分支标为安全重试。
- 影响/频率：异常文本含非标准三位码时可发生；错误放开重试会产生计划明令禁止的未知投递重复 SMTP。
- 回归边界：INTRODUCED — 本计划新增 `classifyDelivery()` 的状态映射。

## 最小修复规格

- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
  - 如需直接覆盖分类器，可增加现有 SMTP 分类器测试；不得改 schema、Flyway、前端或其他发送路径。
- P1-1：503/422 仅返回稳定中文状态文案；`errorSummary` 仅作有界服务端持久化/日志，不进入 HTTP reason。
- P1-2：安全重试必须同时满足 `TRANSIENT` 和 code 在 `400..499`；永久失败必须同时满足 `PERMANENT` 和 code 在 `500..599`；任一其余组合收敛为 `DELIVERY_UNKNOWN`，返回 409“发送状态未知，请勿重复发送”。
- 禁止：弱化 fail-closed、把未知状态改为自动重试、重新引入草稿 authority、改变 INTRODUCTION/自动回复/会议邮件语义，或扩展 UI/数据库。

## 机器验收

- `errorDetail` 含伪密码、SMTP 原文或异常文本时，503/422 HTTP reason 不包含该值；attempt/mail record 的有界诊断保持可审计。
- `TRANSIENT + 451` 仍为 503 且可安全重试；`PERMANENT + 550` 仍为 422 且不可自动重试。
- `TRANSIENT + 999`、`PERMANENT + 299`、缺 code、未分类错误均为 409 UNKNOWN；同 payload 后续请求零 SMTP。
- 运行原计划目标 Maven 测试、`mvn clean test`、`git diff --check`。

## 修复前验证

- 编译：PASS — 当前 Kotlin 主代码可编译。
- 目标测试：PASS — 106 passed，0 failed，0 errors：`ManualReplySendAttemptServiceTest` 13、`PendingMailOperationServiceTrustWorkbenchTest` 12、`UnmatchedInboundTrustWorkbenchTest` 11、`ManualInitialOutreachServiceTest` 67、`ManualOutreachTxHelperTest` 3。`PendingMailOperationServiceTest` 为显式 `@Disabled` 的旧 API 占位，N/A。
- 全量测试报告：PASS — 当前 surefire 报告 1,767 tests，0 failures，0 errors，4 skipped。
- `git diff --check`：PASS。
- 人工验收：PENDING — A-4、A-5、A-6、A-12 由人工执行。

## 合规审计摘要

- I-1、I-3、I-5～I-12：✅；I-2：✅（`PendingMailOperationService.kt:167-200,542-555`）。
- I-4：❌ P1-2；I-13：❌ P1-1。
- Accumulation：N/A；State machine：❌ P1-2；Cross-plan：✅；Deleted code：✅；No extras：✅；Scope：✅。
