# fix-1：AI 草稿审核 authority fail-closed 复验

## 原计划 / 子计划引用

- 子计划：`docs/plans/2026-07-16/ai-reply-review-authority-fail-closed.md`
- 实现提交：`b26c9017 fix: fail-closed AI draft review authority before expose/send`

## 约束摘录

- I-1：初稿审计失败时，不得暴露可采用正文；训练模拟零写入。
- I-2：同秒初稿以 `created_at DESC, id DESC` 唯一排序。
- I-3：authority JSON 的 action/readiness/identity/count/snapshot/key 任一损坏均在投递前 fail closed。
- I-4：客户端 source、identity、confirmation 不能创建或降级 authority；未知非空 source 必须拒绝。
- I-5：只有服务端验证的非 READY 确认可写 `AI_REPLY_REVIEW_CONFIRMED`。
- I-6：拒绝发生在 delivery、mail record、QA association 和外发日志之前；合法 QA ordinal 不变。
- I-7/S-1/S-2：authority 失败不污染前端会话状态，复用既有错误反馈与 loading。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 损坏/手工篡改 `operator_action_log.after_value`，低频但一旦发生会绕过 authority 完整性门 | canonical snapshot 的 `requestIndex`、`intentKey` 可缺失/错类型，`unresolvedCount` 可缺失、字符串或小数；服务端仍可把任意 `reviewKey` 当作 canonical key，并在完整确认后投递。 | `AiReplyReviewAuditService.kt:207-224,279-304` |
| P1-2 | P1 | 任意直接 API / 非官方客户端 | latest authority 存在时未校验 `replySource` allowlist；`UNKNOWN_SOURCE` 携带正确 confirmation 仍可送出，违反未知非空 source 拒绝的不变量。 | `AiReplyReviewAuditService.kt:161-277` 未读取或校验 `replySource`；测试仅覆盖无 latest 行的拒绝（`AiReplyReviewAuditServiceTest.kt:257-267`）。 |

## 修复规格

### P1-1：严格解析 authority JSON

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

将 authority after JSON 解析为严格的受限结构：`unresolvedCount` 必须是非负整数且等于 snapshot 长度；snapshot 必须是列表且每项为对象；每项的 `reviewKey`、`requestIndex`、`intentKey` 都必须存在且类型正确，key 必须精确等于 `${requestIndex}:${intentKey}`。任一错误统一抛 `IllegalArgumentException`，使 API 返回 4xx，并在 `mailDeliveryService.send()` 前停止。新增缺字段、错类型、小数/字符串 count、错项类型测试，以及对应 Pending 零 delivery/mail/QA/action 写入断言。

### P1-2：限制客户端 replySource 声明

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

在读取 latest authority 后仍先校验 source 只能为空/空白或 `AI_DRAFT`；任何其他非空值立即 `IllegalArgumentException`。保留无 authority 且 source 空、confirmation null 的人工路径，以及 READY/non-READY 的既有服务端 identity 与 snapshot 校验。新增 READY、NEEDS_REVIEW 两类 latest authority 下 `UNKNOWN_SOURCE` 的拒绝和零副作用测试。

## 当前状态（修前）

- 编译/测试：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`，1741 passed，0 failed，0 errors，4 skipped。
- JS：PASS — `node --check src/main/resources/static/app.js`；`node --test src/test/js/*.test.js`，353 passed，0 failed。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 审计失败不暴露草稿 | ✅ | `UnmatchedInboundMailController.kt:317-372` 初稿 unavailable 返回空 raw/rendered、null identity、authority false；`AiReplyReviewAuditService.kt:119-122` 吞写入异常为 unavailable。 |
| I-2 latest 唯一顺序 | ✅ | `OperatorActionLogRepository.kt:53-62` 使用 `created_at DESC, id DESC`；同秒回归：`OperatorActionLogRepositoryTest.kt:72-119`。 |
| I-3 损坏 authority fail closed | ❌ P1-1 | `AiReplyReviewAuditService.kt:219-224` 只在 count 是 Number 时比较；`:283-293` 将 requestIndex/intentKey 视为可选，缺字段时仍返回 key。 |
| I-4 客户端不能降级 authority | ❌ P1-2 | `AiReplyReviewAuditService.kt:161-277` 在 latest 存在分支不校验 `replySource`；未知 source 可随有效 confirmation 通过。 |
| I-5 确认事件只由服务端结果触发 | ✅ | `PendingMailOperationService.kt:207-211,290-301` 只在 `AI_REVIEW_CONFIRMED` 写 confirmed；READY/MANUAL 不写。 |
| I-6 拒绝在不可逆写前 | ✅ | `PendingMailOperationService.kt:207-211` 在 render/delivery 前执行 gate；投递与记录从 `:263`、`:266` 开始。 |
| I-7 前端可恢复 | ✅ | `app.js:9551-9553` authority failure 早退；state/bubble/输入更新在 `:9555-9578` 之后；finally `:9596-9604` 恢复 loading。 |
| S-1/S-2 样式与 loading 不回归 | ✅ | `app.js:3748,9551-9553` 复用 warning/feedback；实现提交仅改 `app.js`，未改 `index.html`/`styles.css`。 |
| 训练模拟零写入 | ✅ | `AiTrainingController.kt:181-259` 未调用 review audit；初稿写入仅 mailbox controller `UnmatchedInboundMailController.kt:317-325`。 |
| Deleted code | ✅ | 无删除要求。 |
| No extras | ✅ | `b26c9017` 仅修改子计划列出的 10 个文件。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；无新增持久化状态机。
- Cross-plan check：❌ authority JSON 解析与 Phase 9 confirmation 交界仍可接受损坏 canonical snapshot（P1-1）；未知 source 契约未在后端闭合（P1-2）。
