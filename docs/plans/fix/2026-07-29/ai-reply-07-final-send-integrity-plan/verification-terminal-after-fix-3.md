# 终止复验：AI 回复第 7 步最终发送完整性

## 基线与轮次

- 基线：`docs/plans/2026-07-20/ai-reply-07-final-send-integrity-plan.md`
- 已实施修复：`fix-1.md`、`fix-2.md`、`fix-3.md`
- 复验轮次：fix-3 后的终止复验
- 模式：WORKFLOW_ARTIFACTS
- 结论：TERMINAL_BLOCKED

## P1 谱系

| 项目 | 谱系 | 结论 |
|---|---|---|
| fix-1 P1-1 ～ P1-5 | RESOLVED | 最终 subject/href、空 QA 风险、inbound identity、有界 UNKNOWN 与 SMTP 前边界仍符合实现证据。 |
| fix-2 P1-1 | RESOLVED | 最终复验已覆盖 rendered subject 与无引号 href。 |
| fix-3 P1-1 | RESOLVED | 503/422 仅返回稳定中文，不再把 `errorSummary` 回显。 |
| fix-3 P1-2 | RESOLVED | 安全重试限制为明确 4xx，永久失败限制为明确 5xx，其余归入 UNKNOWN。 |
| P1-1 | NEW_IN_SCOPE | 最终指纹遗漏 `contactId`。 |
| P1-2 | REGRESSION | 计划范围外公开了高风险校验器 API。 |

## 剩余 P1

### P1-1：最终指纹遗漏 `contactId`

- 约束：I-5 明确要求长度前缀 SHA-256 固定包含 `contactId`，且身份字段必须由服务端的最终 canonical payload 计算。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:39-50` 定义了 `SendPayload.contactId`；`74-87` 仅写入 schemaVersion、inboundProcessingId、orcidId、account、recipient、subject、text、HTML、inReplyTo 和 QA IDs，未写入 contactId。
- 触发：任意仅 `contactId` 不同、其余字段相同的 payload；当前测试只覆盖 subject 变化（`ManualReplySendAttemptServiceTest.kt:73-78`），未覆盖 contactId。
- 影响：两个不同 contact 的 payload 得到同一指纹，并可能错误复用同一个 reservation、Message-ID 和投递结果，违反精确幂等身份。
- 回归边界：NEW_IN_SCOPE — fix-1 修复 inbound identity 后仍未满足基线要求的 contact identity。
- 最小后续范围：仅在 `computeFingerprint()` 的长度前缀序列中加入 `payload.contactId`，并增加 contactId-only change 的不同 hash 回归测试；不得修改 schema、重试语义或其他发送路径。

### P1-2：范围外公开高风险校验 API

- 约束：基线“变更文件清单/边界”限定 8 个实现与测试文件；未列入 `AiReplyHighRiskClaimValidator.kt`。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt:177,246` 将两个方法从 `internal` 改为 `public`；该文件不在批准范围。
- 触发：每次构建均扩大该类公开 API；无计划授权。
- 影响：违反批准范围并扩大跨模块可调用面；本次未证明额外运行时行为变化。
- 回归边界：REGRESSION — 本计划工作树变更引入。
- 最小后续范围：确认 `internal` 可满足本模块调用后恢复可见性；不得扩展调用方或新增 API。

## 构建与测试

- 目标 Maven：PASS — 78 passed，0 failed，0 errors：`ManualReplySendAttemptServiceTest` 13、`PendingMailOperationServiceTrustWorkbenchTest` 12、`UnmatchedInboundTrustWorkbenchTest` 11、`ManualInitialOutreachServiceTest` 39、`ManualOutreachTxHelperTest` 3。`PendingMailOperationServiceTest` 是显式 `@Disabled` 旧 API 占位，N/A。
- `mvn clean test`：BLOCKED — 本地执行环境在 Kotlin compile 阶段提前结束，`clean` 后未产生 surefire 报告。
- `npm test`：N/A — 仓库无 `package.json` 或前端测试配置。
- `git diff --check`：PASS。

## 合规审计

- I-1：✅ `PendingMailOperationService.kt:131-165` 重新读取 inbound/contact/account 与当前事实；未使用草稿状态作为 gate。
- I-2：✅ `PendingMailOperationService.kt:167-200,542-555` 渲染 subject/text/HTML 后复验，收集所有 href 语法。
- I-3：✅ `PendingMailOperationService.kt:146-163,565-581` 当前 canonical QA 和空 QA 的确定性风险均受检。
- I-4：✅ `PendingMailOperationService.kt:202-215,583-639` 仅当前确定性风险阻断。
- I-5：❌ P1-1。
- I-6：✅ `ManualReplySendAttemptService.kt:95-151` 使用独立事务 reservation/CAS，claim 后才 delivery。
- I-7：✅ `ManualReplySendAttemptService.kt:154-197` SENT/IN_PROGRESS/UNKNOWN/FAILED 均 fail closed。
- I-8：✅ `PendingMailOperationService.kt:642-679` 仅明确 4xx/5xx 映射重试/永久失败，其余 UNKNOWN。
- I-9：✅ `ManualReplySendAttemptService.kt:201-336` 一 attempt 更新同一 mail record，失败无 sentAt。
- I-10：✅ `ManualReplySendAttemptService.kt:250-267,339-410` 成功后按 ordinal 关联并 best-effort 审计。
- I-11：✅ `PendingMailOperationService.kt:241-251,527-532` 保持人工账号与 multipart 语义。
- I-12：✅ `MailSendAttemptStatus.kt` 仅新增状态；目标 INTRODUCTION 回归测试通过。
- I-13：✅ `PendingMailOperationService.kt:138-141,194-200,231-237,316-331` 发送前边界与稳定响应均存在。
- Accumulation check：N/A — 基线无时间窗口累计契约。
- State-machine check：✅ PREPARED → DELIVERY_IN_PROGRESS → SENT / FAILED_SAFE_TO_RETRY / FAILED / DELIVERY_UNKNOWN；未知/进行中均不重发。
- Cross-plan check：❌ P1-1，I-5 的完整 payload 字段未闭合。
- Deleted code：✅ 无批准删除项。
- No extras：❌ P1-2。
- Scope compliance：❌ P1-2。
- Plan quality gate：✅ 未证明需计划拆分的结构性耦合；两个问题均有窄修复位置。
- 人工验收：PENDING — A-1 ～ A-12。

## 后续边界

fix-3 已实施，**未创建 fix-4**。任何后续修复须经人工批准，作为独立目标，限定为上述两个窄改动及对应测试，并在可完成 `mvn clean test` 的环境中重新验证。
