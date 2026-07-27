# fix-3：P2-8 AI 草稿审核 authority 复验

## 原计划 / 前序复验

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 8 / P2）
- 子计划：`docs/plans/2026-07-15/ai-reply-08-p2-review-audit-backend.md`
- 前序：`fix-1.md`、`fix-2.md`

## 关键约束

- 非 READY AI 草稿必须在 `mailDeliveryService.send()` 前按服务端持久化的 identity、readiness、canonical unresolved snapshot 完整校验。
- 客户端 `replySource`、identity、readiness、snapshot 不能降级或替代服务端事实。
- 审计记录缺失、损坏、identity 过期或不匹配必须 fail closed；无 AI 草稿记录的纯 MANUAL 路径保持可用。
- 初稿审计失败不得使生成接口报错，但该次非 READY 草稿不得获得可发送 authority。
- `AI_REPLY_REVIEW_CONFIRMED` 只能表示一次经服务端验证的人工确认；不得由客户端字段直接制造。
- 不新增表、业务状态或恢复机制；保留既有 QA 关联与 `SEND_MANUAL_COMPOSED_REPLY` 审计。

## 当前验证结果

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`：PASS，BUILD SUCCESS。
- `node --check src/main/resources/static/app.js`：PASS。
- `node --test src/test/js/*.test.js`：PASS，350/350。
- 自动测试通过，但以下写路径仍违反发送与审计 authority 不变量。

## P1 修正记录

| ID | 问题 | 生产触发与证据 |
|---|---|---|
| P1-1 | 非 READY 初稿审计写失败后，controller 仍返回完整草稿且 `draftIdentity=null`；发送校验在查不到初稿记录时直接放行。该草稿以 `replySource=AI_DRAFT` 或省略 source 调用发送接口，均可进入 delivery。 | `AiReplyReviewAuditService.kt:43-106` 捕获写失败并返回 null；`UnmatchedInboundMailController.kt:316-368` 仍返回草稿；`AiReplyReviewAuditService.kt:151-155` 对无记录直接 return；`PendingMailOperationService.kt:206-263` 随后发送。违反 fix-2 第 35-43 行。 |
| P1-2 | “最新初稿”查询只按秒级 `DATETIME created_at` 排序。同一 inbound 在同一秒生成两次时，数据库可任意返回旧行，使新 identity 被拒绝或旧 identity 被接受。 | V19 的 `operator_action_log.created_at` 为 `DATETIME`；`OperatorActionLogRepository.kt:53-62` 仅 `ORDER BY created_at DESC LIMIT 1`，无 `id DESC` 确定性 tie-break。 |
| P1-3 | authority 记录损坏时存在 fail-open，确认审计也仍由客户端触发：缺少 `readiness` 时校验直接 return；action type 与 JSON readiness 不交叉校验；发送成功后只要客户端带 identity 或声称 `AI_DRAFT` 就写 `AI_REPLY_REVIEW_CONFIRMED`。 | `AiReplyReviewAuditService.kt:167-171`；`PendingMailOperationService.kt:290-297`。可形成未验证的确认日志并污染 Phase 10“人工确认”指标。 |

## 修复规格

### T1：初稿审计失败时不暴露可采用的非 READY 草稿

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

要求：

1. 保持首轮生成 endpoint 为成功响应，但当 `result.draftReadiness != READY` 且初稿审计未持久化时，不得把完整 `draftText/renderedDraftText` 返回为可采用草稿。
2. 响应须明确表现为 authority 不可用；优先复用现有 warning/feedback 通道，禁止新增持久状态或让客户端自行补造 identity。
3. READY 审计失败行为保持原兼容边界；若存在更早的非 READY 服务端记录，不得让本次无记录响应覆盖其 authority。
4. 测试模拟 `recordInitialDraft()` 失败，断言非 READY 正文不可采用、identity 为空、接口不抛 5xx；续轮不得凭空恢复 identity。

研究检查点：实现前先确认“成功响应但隐藏非 READY 草稿”与现有 API 客户端兼容；若无法做到，则必须回到原计划决定是否允许生成 endpoint 对该分支 fail closed，不能仅靠前端按钮禁用。

### T2：确定性读取最新 authority，并校验 action/JSON 一致性

文件：

- `src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`

要求：

1. 最新行固定按 `created_at DESC, id DESC` 排序；同秒内 id 较大的初稿必须成为唯一 current authority。
2. 从 action type 推导 readiness，并要求 after JSON 的 readiness 与 action type 完全一致；字段缺失、未知值、矛盾值、缺 identity 或损坏 snapshot 一律拒绝。
3. 非 READY 必须校验 current identity、canonical key 唯一性/格式、confirmed key 精确相等及 BLOCKED note；READY 才允许无确认直发。
4. 增加同秒两行、旧 identity、缺 readiness、action/JSON 矛盾、未知 readiness 测试。

### T3：由校验结果驱动确认审计

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

要求：

1. `validateConfirmationForSend()` 返回服务端判定结果：纯 MANUAL、READY 直发或已验证的非 READY confirmation；不得继续只返回 `Unit` 后由客户端字段推断。
2. 只有“已验证的非 READY confirmation”结果可写 `AI_REPLY_REVIEW_CONFIRMED`，且 identity/confirmed keys 使用服务端校验后的值。
3. 客户端在无初稿、READY 或 identity 不匹配场景伪造 `replySource=AI_DRAFT`/confirmation，不得产生确认日志；现有外发 action 与 QA association 不变。
4. 所有拒绝都发生在 delivery 和 `mail_record` 写入之前。

## 验收矩阵

- 非 READY + 正确 current identity + 全量 key：发送；写原外发审计 + REVIEW_CONFIRMED。
- 非 READY + identity 缺失/旧/伪造：拒绝；delivery、mail_record、REVIEW_CONFIRMED 均无写。
- 初稿审计失败：生成接口不返回可采用的非 READY 正文；后续不能借纯 MANUAL/source 省略发送该草稿。
- 同秒连续生成：只接受 id 最大的 current identity。
- audit action/readiness/snapshot 损坏：拒绝。
- READY：无需 review confirmation，可发送；不计入人工确认指标。
- 无服务端 AI 初稿的真实 MANUAL：保持可发送。

## 定向验证

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyReviewAuditServiceTest,PendingMailOperationServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaRuleAuditServiceTest test
node --test src/test/js/aiReplyReviewConfirmation.test.js src/test/js/qaAiReplyQualityMetrics.test.js
```

## 收敛门

- 这是 Phase 8 第 3/3 轮。
- 修复后若上述任一 P1 仍存在，停止继续补丁，输出 `Verification Blocked` 与重新拆分建议；禁止创建 fix-4。
