# fix-2：AI 草稿审核 authority fail-closed 复验

## 原计划 / 修复轮次引用

- 原计划：`docs/plans/2026-07-16/ai-reply-review-authority-fail-closed.md`
- 原实现：`b26c9017 fix: fail-closed AI draft review authority before expose/send`
- fix-1 实现：`d64cf460 fix: harden AI draft authority JSON and replySource gates`
- 上轮 P1 数：2；本轮 P1 数：1，满足收敛要求。

## 约束摘录

- I-1：无持久化 authority，不暴露可采用 AI 草稿。
- I-2：latest authority 使用 `created_at DESC, id DESC`。
- I-3：action/readiness/identity/count/snapshot/key 必须自洽，损坏即 fail closed；READY 不能绕过 JSON 完整性校验。
- I-4：客户端字段不能创造或降级 authority。
- I-5：仅服务端确认的非 READY authority 写确认事件。
- I-6：拒绝必须在 delivery 与持久化之前。
- I-7：前端 authority 失败可恢复且不污染会话状态。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | `operator_action_log.after_value` 损坏、手工修订或历史异常数据，低频但可绕过人工确认 | `readiness=READY` 且 `unresolvedSnapshot` 非空、count 相等时，当前校验完成类型/key/count检查后直接返回 `AI_READY`。这把仍有 unresolved items 的损坏记录当 READY 放行。 | `AiReplyReviewAuditService.kt:214-249`；现有测试没有 READY + non-empty snapshot 反例。 |

## 根因

当前只实现了单向一致性规则“非 READY snapshot 不得为空”，没有实现其对偶规则“READY snapshot 必须为空”。因此 JSON 的局部字段都合法、count 也相等时，readiness 与 unresolved state 仍可能互相矛盾。

## 修复规格

### P1-1：闭合 readiness 与 unresolved snapshot 的双向不变量

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

要求：

1. 严格解析 snapshot 与 `unresolvedCount` 后，执行双向一致性检查：
   - `READY`：snapshot 必须为空且 count 必须为 0；
   - `NEEDS_REVIEW` / `BLOCKED`：snapshot 必须非空且 count 必须大于 0。
2. 任一矛盾统一抛 `IllegalArgumentException`，不得返回 `AI_READY` 或 `AI_REVIEW_CONFIRMED`。
3. 校验必须继续位于 `mailDeliveryService.send()`、mail record、QA association 与 operator action 写入之前。
4. 不改表结构、action type、响应 DTO、前端、READY 正常路径或 confirmation event 计数规则。

## 测试要求

- Service：`AI_REPLY_DRAFT_READY` + `readiness=READY` + 1 条合法 canonical snapshot + `unresolvedCount=1` 必须拒绝。
- Service：READY + empty snapshot + count 0 继续返回 `AI_READY`。
- Pending：上述损坏 READY authority 必须满足 delivery/mail/QA/outbound action 调用次数均为 0。
- 保留 fix-1 的 requestIndex/intentKey/count 类型、未知 source、same-second latest 等回归。

## 当前状态（修前）

- JVM：PASS — `mvn -q test`，1751 tests，0 failures，0 errors，4 skipped。
- JS：PASS — 353 tests，0 failures。
- 语法：PASS — `node --check src/main/resources/static/app.js`。
- 功能验收：FAIL — 损坏 READY authority 可携带 unresolved snapshot 进入投递路径。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 初稿写失败不暴露 | ✅ | controller unavailable 分支返回空正文/null identity；前端早退。 |
| I-2 latest 顺序 | ✅ | repository 使用 `created_at DESC, id DESC`，同秒测试通过。 |
| I-3 authority 自洽 | ❌ P1-1 | READY 非空 snapshot 未拒绝。 |
| I-4 客户端不能降级 | ✅ | 无 authority 与 latest authority 两侧均校验 source/confirmation。 |
| I-5 确认事件来源 | ✅ | Pending 只对 `AI_REVIEW_CONFIRMED` 写确认事件。 |
| I-6 不可逆写前拒绝 | ✅（缺陷修复后需补反例证明） | gate 当前位于 render/delivery 之前；缺少 READY 矛盾测试。 |
| I-7 前端恢复与样式 | ✅ | authority false 早退，不写 draft state；复用原 feedback/loading。 |
| Deleted code | ✅ | 无删除要求。 |
| No extras | ✅ | fix-1 实现仍在原计划文件范围内。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：❌ readiness 与 unresolved snapshot 的状态对应只校验了一个方向。
- Cross-plan check：✅ source allowlist、canonical key 与 Pending 零副作用路径均已闭合；本轮只补 authority 状态一致性。

