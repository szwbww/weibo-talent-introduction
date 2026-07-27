# Phase 8 复验修复计划（fix-2）

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 8 / P2）
- 子计划：`docs/plans/2026-07-15/ai-reply-08-p2-review-audit-backend.md`
- 前序复验：`docs/plans/fix/ai-reply-08-p2-review-audit-backend/fix-1.md`

## 约束摘录

- 非 READY AI 草稿必须在 `mailDeliveryService.send()` 前，经服务端权威 draft identity 校验完整确认；失败不得发信、不得写 `mail_record`。
- identity、readiness 与 unresolved snapshot 以 `operator_action_log` 的初稿审计记录为准；客户端 `replySource`、readiness 与 snapshot 不得降级或替代服务端事实。
- review key 为 `{requestIndex}:{intentKey}`，canonical snapshot 与 confirmed set 均不得重复，且必须完整相等。
- `SEND_BLOCKED` 仅记录受限 review key 审计载荷；现有 QA 外发关联与 `SEND_MANUAL_COMPOSED_REPLY` 保持不变；训练模拟零写入、无新表。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1（遗留） | 服务端只在客户端提供 `aiAuditRecordId > 0` 时调用确认校验；省略 identity 或把 `replySource` 改为 `MANUAL`/省略后，已生成的非 READY 草稿仍直接执行 delivery。返回的值还是可枚举的 `operator_action_log.id`，不是修正记录要求的不可预测 draft identity。 | 任意直接 API/非官方客户端调用；一旦绕过即违反 P2 后端发布门。 |

## 修复规格

### P1-1：强制服务端 authority 并使用不透明 identity

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

首轮生成时在现有初稿审计 `after` JSON 写入不可预测的 `draftIdentity`，响应仅返回该 identity，不暴露或接受顺序型 audit row id 作为认证凭据。发送服务按 inbound id 从既有 `operator_action_log` 初稿 action 读取当前服务端草稿事实：若当前记录为 `NEEDS_REVIEW`/`BLOCKED`，identity 缺失、未知、不匹配、旧记录或 canonical snapshot 损坏均 fail closed，且在 `mailDeliveryService.send()` 前拒绝；客户端 `replySource` 不参与该判断。无服务端 AI 草稿记录的纯 MANUAL 路径保持不变。

确认成功后仍以服务端记录的 readiness/snapshot 校验 key 与 note，并将同一不透明 identity 写入 `AI_REPLY_REVIEW_CONFIRMED`。审计记录写入失败仍不使生成接口失败，但该次非 READY 草稿不得获得可发送 authority；后续发送应 fail closed，不可因缺 identity 走纯人工路径。不得新增表、枚举、恢复机制或扩大到 Phase 9 前端。

新增测试：

- 已存在非 READY 初稿时，省略 identity、伪造/替换 identity、声明 `MANUAL`、伪造 READY 均拒绝，并 `verify(mailDeliveryService, never())`。
- 正确 identity + 全量确认通过；READY 与无初稿的 MANUAL 保持可发。
- 初稿审计失败后的非 READY 草稿无可发送 authority；不透明 identity 不等于日志行 id。

## 当前状态

- 编译/测试：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`（2026-07-15，exit 0）。
- JS 回归：PASS — `node --test src/test/js/*.test.js`，301 passed。

## 合规审计

- I-1 发送前校验：❌ `PendingMailOperationService.kt:206-214` 仅对非空 `aiAuditRecordId` 校验；`:266` 随后直接调用 delivery。`:293-301` 还以客户端 `replySource` 决定确认审计。P1-1。
- I-2 key 唯一稳定：✅ `AiReplyReviewAuditService.kt:174-217` 校验 key 格式、canonical/confirmed 重复、unknown 与 missing。
- I-3 人工确认语义：✅ `AiReplyReviewAuditService.kt:219-224` 对 BLOCKED trim 后至少 5 字符；`:227-255` 的确认审计不写 QA 事实。
- I-4 现有外发审计不变：✅ `PendingMailOperationService.kt:303-348` 仍写 `mail_record_qa_rule` 与 `SEND_MANUAL_COMPOSED_REPLY`；`:293-301` 只是附加确认审计。
- I-5 训练模拟零写入：✅ `UnmatchedInboundMailController.kt:316-329` 仅 mailbox 首轮 best-effort 记录；`AiTrainingController.kt` 未注入 review audit service。
- I-6 无新表：✅ 仅复用 `operator_action_log`；无 Phase 8 migration。
- T1 action enum：✅ `OperatorActionType.kt:12-16` 包含五种 action。
- T2 review service：❌ 权威记录读取存在，但 `AiReplyReviewAuditService.kt:140-225` 只能在调用方已给 audit row id 时校验；`:97` 返回日志自增 id，未产生不透明 identity。P1-1。
- T3 初稿与 review-event：✅ `UnmatchedInboundMailController.kt:316-329` 首轮记录、续轮不记；`:372-389` 只接受 `SEND_BLOCKED`。
- T4/T5 发送闸门与透传：❌ controller 透传见 `UnmatchedInboundMailController.kt:218-228`，但 `PendingMailOperationService.kt:206-214` 允许省略 identity 后直发。P1-1。
- T6：❌ `PendingMailOperationServiceTest.kt:1753-1761` 与 `:1807-1832` 将省略 audit id 的 AI 草稿绕过视为成功；缺少 P1-1 拒绝测试。
- `SEND_BLOCKED` 载荷：✅ `AiReplyReviewAuditService.kt:110-126` 限制 100 项、每 key 200 字符并保留总数/截断标记。
- Deleted code：✅ 无本计划要求删除项。
- No extras：✅ 本轮已修复内容仍在 Phase 8 原定 7 文件范围内。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；本阶段没有状态机。
- Cross-plan check：❌ 错误/重启路径中，identity 缺失会被当作人工回复而直发；Phase 9 仅携带 identity 不能修补直接 API 省略字段。P1-1。
