# P2-8：AI 缺口确认与操作审计后端

## 需求描述

后端识别人工富文本回复是否采用 AI 草稿；非 READY 草稿必须携带完整的逐项人工确认才能发送。记录初稿质量、直发拦截与成功确认，复用 `operator_action_log`，不新增业务事实表。

Out of scope：前端弹窗、自动验证人工补写内容真实性、训练模拟审计、修改实际 QA 选用审计语义。

## 关键不变量

### I-1：发送前校验
- `replySource=AI_DRAFT` 且 readiness 非 READY 时，必须提供 unresolved snapshot、完整 confirmedReviewKeys 与 operator note。
- 校验在 `mailDeliveryService.send()` 前执行；失败不得发信、不得写 mail_record。

### I-2：review key 唯一稳定
- key 固定为 `{requestIndex}:{intentKey}`，避免同一 intentKey 在多个 request 重复。
- confirmed set 必须与 unresolved set 完整相等；未知、多余、重复 key 拒绝。

### I-3：人工确认语义
- 确认只表示“操作员已人工补充/核验并承担发送决定”，不把 missing intent 改写成已审核 QA 事实。
- BLOCKED 必须填写非空说明（trim 后至少 5 字符）；NEEDS_REVIEW 可配置同样要求以统一审计。

### I-4：现有外发审计不变
- 携带 qaRuleIds 的 rich reply 继续写 `mail_record_qa_rule` + `SEND_MANUAL_COMPOSED_REPLY`。（K-rich-reply-qa-audit-reuse）
- 新 review confirmation log 是附加审计，不替代实际外发关联表。（K-audit-selected-source）

### I-5：训练模拟零写入
- `AiTrainingController.simulate()` 不调用 audit service。
- mailbox AI 首轮才记录质量；续轮改写不进入“初稿遗漏率”分母。

### I-6：无新表
- operator_action_log 已可保存 target、operator、before/after JSON、note；本计划复用它。

## 修正记录

| 日期 | 复验轮次 | 修正内容 | 原因 |
|---|---:|---|---|
| 2026-07-15 | fix-1 | AI 草稿确认新增服务端权威 draft audit identity：首轮生成将该 identity 与 readiness、unresolved snapshot 写入 `operator_action_log`，响应返回 identity；发送时仅以服务端审计记录判定是否为 AI 草稿、是否非 READY 及其完整 review key 集。客户端 `replySource`、`draftReadiness`、`unresolvedSnapshot` 只能作展示/携带，不得降级或替代服务端事实。继续复用 `operator_action_log`，不新增表。 | 原计划没有定义信任边界；仅凭客户端字段可在直接 API 调用中省略 `AI_DRAFT` 或伪造 `READY`/快照，从而绕过后端审核闸门。 |

## 审计 action 类型

- `AI_REPLY_DRAFT_READY`
- `AI_REPLY_DRAFT_NEEDS_REVIEW`
- `AI_REPLY_DRAFT_BLOCKED`
- `AI_REPLY_SEND_BLOCKED`
- `AI_REPLY_REVIEW_CONFIRMED`

generation after JSON 至少记录：model、mode、requestCount、readiness、unresolved intent snapshot、generationState。不得记录完整专家邮件/完整草稿，避免日志复制敏感正文。

## 写/读路径审计

- 生成写：`UnmatchedInboundMailController.aiReplyTurn()` 首轮 response 完成后。
- 拦截写：新 review-event endpoint，仅允许 SEND_BLOCKED。
- 确认写：`PendingMailOperationService.sendManualRichReply()` 成功保存 mailRecord 后，同一事务记录。
- 指标读：计划 10 通过 action type 计数；现有操作日志详情可直接查看 after/note。

## 实现任务

### T1：扩展 action enum
文件：`src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt`

- 添加五种 action 与中文 summary；不改现有 action 名称。

### T2：新增 review audit/validation service
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`

- 定义受限 DTO：`AiReplyReviewItem(reviewKey,requestIndex,intentKey,status,missingEvidenceKeys)`。
- `recordInitialDraft(...)` 只接受 mailbox inbound id/contact id 与 service result 摘要。
- `recordSendBlocked(...)` 限制 payload 数量/长度，去除完整正文。
- `validateConfirmation(...)` 执行 source/readiness/key/note 校验。
- `recordConfirmed(...)` 关联 mailRecordId 与 operator。

### T3：mailbox AI 生成与事件 endpoint
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- `AiReplyTurnRequest` 添加 `operatorName`。
- `turns.isEmpty()` 时记录一次初稿 action；续轮不记录。
- 新增 `POST /api/mail/unmatched-inbound/{id}/ai-reply/review-event`，eventType 只能 `SEND_BLOCKED`。
- 审计失败策略：generation log 失败不得让 AI 草稿接口失败；使用 best-effort 并记录 server log。发送确认校验失败必须 fail closed。

### T4：manual rich request 与发送闸门
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`

- `PendingManualRichReplyRequest` 添加 `replySource`、`aiReviewConfirmation`。
- service 参数接收 review object；AI_DRAFT 非 READY 先 validate。
- 成功 delivery + mailRecord 后记录 CONFIRMED；现有 SEND_MANUAL_* log 与 association 不变。
- MANUAL/default 请求不受影响；AI_DRAFT READY 可无 unresolved confirmation，但仍可记录 source summary。

### T5：controller 透传
文件：`UnmatchedInboundMailController.kt`

- manual-rich-reply 完整透传新字段；不得在 controller 重复校验集合。

### T6：测试
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

覆盖：初稿 action 分类、续轮不计、simulate 无写、missing/extra/duplicate key、note 规则、校验先于 delivery、成功后双日志、qa association 保留、audit best-effort。

## 变更文件清单（7）

1. `OperatorActionType.kt`
2. `AiReplyReviewAuditService.kt`
3. `UnmatchedInboundMailController.kt`
4. `PendingMailOperationService.kt`
5. `AiReplyReviewAuditServiceTest.kt`
6. `PendingMailOperationServiceTest.kt`
7. `UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

## 验收标准

- 非 READY AI_DRAFT 缺确认时服务端拒绝，即使绕过前端直接调 API。
- 拒绝发生在 delivery 前；测试 verify mailDelivery never invoked。
- 成功发送保留原 QA 审计，同时新增 REVIEW_CONFIRMED。
- 训练模拟 operator_action_log 行数不变。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyReviewAuditServiceTest,PendingMailOperationServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AiTrainingSimulateTest test
```

## 人工验收清单

### A-1：直接 API 绕过
- AI_DRAFT/BLOCKED 不带 confirmation 调 manual-rich-reply。
- 预期：400；无邮件、无 mail_record。

### A-2：完整确认
- 提交所有 reviewKeys + note。
- 预期：发送成功；操作日志同时有原 send action 与 REVIEW_CONFIRMED，后者关联 mailRecordId。

### A-3：模拟只读
- 训练模拟生成 BLOCKED 草稿。
- 预期：operator_action_log 无新增 AI quality action。
