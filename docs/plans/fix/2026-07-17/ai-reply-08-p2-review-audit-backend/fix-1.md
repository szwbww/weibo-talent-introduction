# Phase 8 复验修复计划（fix-1）

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 8 / P2）
- 子计划：`docs/plans/2026-07-15/ai-reply-08-p2-review-audit-backend.md`

## 约束摘录

- AI_DRAFT 非 READY 必须在 `mailDeliveryService.send()` 前提供完整确认；失败不发信、不写 `mail_record`。
- review key 固定为 `{requestIndex}:{intentKey}`；confirmed 与 unresolved 必须完整相等，未知、多余、重复均拒绝。
- BLOCKED note trim 后至少 5 字符；确认不把 missing intent 变为 QA 事实。
- rich reply 的 QA 关联和 `SEND_MANUAL_COMPOSED_REPLY` 审计保持不变；新确认日志为附加审计。
- 训练模拟零写入；复用 `operator_action_log`，不新增表。
- `SEND_BLOCKED` 审计 payload 必须限制数量/长度，不得记录完整正文。
- 修正记录：发送确认必须由服务端持久化的 draft audit identity 解析 authority，不信任客户端 `replySource`、readiness 或 snapshot。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | 发送闸门完全信任客户端 `replySource`、`draftReadiness` 与 snapshot。省略 `AI_DRAFT`、传 `AI_DRAFT` 但不带 confirmation，或伪造 `READY`/任意 snapshot，均可在 delivery 前绕过非 READY AI 草稿审核。 | 任意直接 API / 非官方客户端调用；一旦出现即会绕过 P2 发布门。 |
| P1-2 | `validateConfirmation()` 将 unresolved key 转为 `Set`，未拒绝 snapshot 内重复 key；两个相同 unresolved item 可只确认一次。 | 异常/篡改 payload；低频，但直接破坏“完整相等、重复拒绝”不变量。 |
| P1-3 | `recordSendBlocked()` 原样写入任意数量、任意长度的 `reviewKey` 列表，未执行计划要求的截断或上限。 | 任意客户端可触发；会膨胀 `operator_action_log`，并可能把不应入审计的长文本写入日志。 |

## 修复规格

### P1-1：服务端权威 AI 草稿身份与确认

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- 对应 Phase 8 三个 Kotlin 测试文件

首轮 mailbox AI 生成时创建不可预测的 draft audit identity，连同 inbound id、readiness 和 canonical unresolved snapshot 写入既有 `operator_action_log` 的 after JSON；将 identity 返回给操作端。manual-rich-reply 接收 identity 与 confirmed keys，但服务层必须按 inbound id + identity 读取该审计记录，并只使用该记录的 readiness/unresolved keys 判定。

非 READY 的服务端记录必须要求非空 confirmation，且 validated confirmed keys 与服务端 canonical set 精确相等；BLOCKED 保留 note 规则。客户端声称 `MANUAL`、省略 `replySource`、传 `READY` 或替换 snapshot 都不得让已标记为非 READY 的 AI 草稿越过闸门。纯 MANUAL 的既有路径不受影响。校验必须仍在 `mailDeliveryService.send()` 之前；成功后复用同一 identity 记录 CONFIRMED，且不改变 QA association/action。

新增测试：缺 confirmation、伪造 READY、伪造/遗漏 source、伪造 snapshot、有效 identity + 完整确认；每个拒绝场景 `verify(mailDeliveryService, never())`。这是计划级语义补全，已写入原子计划 `修正记录`。

### P1-2：canonical snapshot 的唯一 key 校验

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`、`AiReplyReviewAuditServiceTest.kt`

在构造/读取 canonical unresolved snapshot 时，拒绝重复 `reviewKey`，并校验每项 key 精确等于 `${requestIndex}:${intentKey}`；不得先 `toSet()` 再比较而掩盖重复。服务端 audit record 已损坏或不符合该格式时 fail closed，不调用 delivery。

新增重复 snapshot、key 与 requestIndex/intentKey 不一致的测试。

### P1-3：限制 SEND_BLOCKED 审计载荷

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`、`AiReplyReviewAuditServiceTest.kt`

为 `recordSendBlocked()` 设置固定的 review item 数量和单个 key 长度上限；after JSON 保留原始 `unresolvedCount`，只保存截断后的 key 列表及明确的 `truncated` 标记。只记录 review key，不记录正文、requestText、intent title 或 operator note。上限命中时仍成功记录拦截审计，不增加新的 DTO、表或恢复机制。

新增超量、超长 key 测试，断言日志 after JSON 有上限且没有正文相关字段。

## 当前状态

- 编译：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
- 定向测试：PASS — 84 passed, 0 failed, 0 skipped：`AiReplyReviewAuditServiceTest`（11）、`PendingMailOperationServiceTest`（42）、`UnmatchedInboundAiReplyTurnKnowledgeTest`（9）、`AiTrainingSimulateTest`（22）。
- JS 回归：PASS — `node --test src/test/js/*.test.js`，301 passed。

## 合规审计

- I-1 发送前校验：❌ `PendingMailOperationService.kt:206-210` 仅在 confirmation 非空且客户端 readiness 非 READY 时调用校验；`AI_DRAFT` + null confirmation 直接到 delivery（`:262`）。`PendingManualRichReplyRequest` 字段位于 `PendingMailOperationService.kt:699-715`，默认可省略 source/confirmation。
- I-2 key 唯一稳定：❌ `AiReplyReviewAuditService.kt:127-149` 以 `toSet()` 建 expected/confirmed，未检查 unresolved snapshot 自身重复；同一 key 的多项会被折叠。
- I-3 人工确认语义：✅ `AiReplyReviewAuditService.kt:151-155` 对 BLOCKED 使用 trim 后最少 5 字符；确认日志未写 QA 事实（`:166-188`）。
- I-4 外发 QA 审计不变：✅ `PendingMailOperationService.kt:299-329` 继续写 `mail_record_qa_rule` 与 `SEND_MANUAL_COMPOSED_REPLY`；确认日志为附加调用（`:289-297`）。
- I-5 训练模拟零写入：✅ `AiTrainingController.kt:180-259` 未注入 audit service；mailbox 首轮才调用初稿审计（`UnmatchedInboundMailController.kt:316-326`），续轮不调用。
- I-6 无新表：✅ Phase 8 实现只使用已有 audit service/log；无 Phase 8 migration。
- T1 action enum：✅ `OperatorActionType.kt:12-16` 含五种 action。
- T2 review service：❌ `recordSendBlocked()` 在 `AiReplyReviewAuditService.kt:95-115` 无数量/长度边界；其余 initial/confirmation 调用存在。
- T3 初稿与 review-event：✅ `UnmatchedInboundMailController.kt:316-325` 首轮 best-effort；`:368-385` 只允许 `SEND_BLOCKED`。
- T4/T5 发送闸门与透传：❌ controller 已透传（`UnmatchedInboundMailController.kt:206-228`），但服务端事实仍取客户端字段，见 P1-1。
- T6：✅ 定向测试均通过，但未覆盖 P1-1/P1-2/P1-3 的绕过路径。
- Deleted code：✅ 无本计划要求删除项。
- No extras：✅ Phase 8 新增/修改仅落在规定的 7 个文件范围；`AiReplyReviewAuditService.kt` 与对应测试为计划列明的新文件。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；本阶段没有状态机。
- Cross-plan check：❌ Phase 9 的确认 UI 只能回传客户端生成的 source/snapshot，Phase 8 未定义服务端权威草稿身份。错误恢复与重启后均不能证明确认对象仍是原非 READY 草稿；P1-1 的修正记录补齐此接口契约。
