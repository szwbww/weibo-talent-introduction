# 子计划 03：会话内自动回复的退订拦截

> 用 create-p skill 编写。退订系列第 3 篇。
> **依赖子计划 01**（`EmailSuppressionService`）。与 01 同样修改 `AutoMailReplyService`，须在 01 之后执行。

## 需求描述

- 可观察结果：当一个已退订（在抑制名单中）的收件人触发自动回复流程（QA 自动回复 / 自动会议邀请）时，系统**不发送**该自动邮件，转入人工（`MANUAL_HANDOFF`）并记录原因，避免向退订者继续自动外发。
- 必须不变：未退订收件人的自动回复流程与状态流转完全不变；退信、入站解析、QA 匹配逻辑不变；人工发送（`ManualExpertMailService`）作为操作员显式动作不在此拦截（操作员可知情覆盖）。
- 不做：队列执行器 `PendingMailOperationService`、会议服务被动调用链的二次拦截（自动路径在 `AutoMailReplyService` 已被拦下）；删除/恢复抑制（见子计划 04）。

## 关键不变量（引用 + 专属）

- 引用 G-1（归一化）、G-3 精神（外发先查表，本篇扩展到自动回复）。
- Invariant L3-1：自动发送前置查表。`AutoMailReplyService` 在每个**自动**发送点（QA 自动回复、自动会议邀请）调用 `mailDeliveryService.send` 之前，必须 `emailSuppressionService.isSuppressed(recipient)`；命中则跳过发送。
- Invariant L3-2：拦截后转人工且不回退状态机。命中抑制时，通过既有 `ConversationStateService.transition(..., MANUAL_HANDOFF, ...)` 转入人工并附原因（如 `RECIPIENT_UNSUBSCRIBED`），复用既有人工工单创建路径；不得直接改 `currentStatus`，不得静默丢弃（必须留痕）。
- Invariant L3-3：仅自动路径受影响。`ManualExpertMailService` 等操作员主动发送不加此拦截。

## 现状审计

### `AutoMailReplyService`（写路径）
- 自动发送点：`:384`（QA 自动回复）、`:664`（自动会议邀请 / 二次自动外发）。两处均 `mailDeliveryService.send(account, ...)`。
- 状态流转统一经 `ConversationStateService.transition(...)`（见文件内多处 `MANUAL_HANDOFF` 用例，如 `:140/:250/:352`），并有 `createManualHandoffIfAbsent(...)` 之类的人工工单创建辅助（`:134`）。本篇复用这些既有出口，不新增状态。
- 收件人邮箱：来自当前会话联系记录 / 入站发件人。
- 注：子计划 01 已在本文件注入 `EmailSuppressionService` 并在入站处理追加退订捕获；本篇复用同一注入。

### `ConversationStatus`
- `MANUAL_HANDOFF` 为既有「需人工」终态，自动回复在该状态下禁用。无需新增枚举值。

## 实现方案

### 任务 1：自动发送点前置查表（L3-1, L3-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- 抽取私有辅助：
```kotlin
private fun blockedByUnsubscribe(contactId: Long, orcidId: String, recipient: String, scene: String): Boolean {
    if (!emailSuppressionService.isSuppressed(recipient)) return false   // L3-1
    log.info("Recipient {} unsubscribed, skip auto send ({})", recipient, scene)
    // L3-2：转人工，复用既有 transition + 工单创建
    conversationStateService.transition(/* contact */, ConversationStatus.MANUAL_HANDOFF,
        reasonType = "RECIPIENT_UNSUBSCRIBED", ...)
    createManualHandoffIfAbsent(contactId, "RECIPIENT_UNSUBSCRIBED", "Auto send skipped: recipient unsubscribed")
    return true
}
```
（具体 `transition` 参数对齐文件内既有调用签名。）
- 在 `:384` QA 自动回复发送前：`if (blockedByUnsubscribe(contactId, orcidId, recipient, "QA")) return ...`（按所在函数的返回结构给出对应的「已转人工」结果）。
- 在 `:664` 自动会议邀请发送前：同样前置判断并短路。

### 任务 2：测试
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`（补充）
- 收件人在抑制名单：QA 自动回复路径**不调用** `mailDeliveryService.send`，会话转 `MANUAL_HANDOFF`，工单含 `RECIPIENT_UNSUBSCRIBED`（L3-1, L3-2）。
- 收件人不在抑制名单：QA 自动回复正常发送，状态流转与现状一致（回归保护）。
- 自动会议邀请路径同样被拦截（L3-1）。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `mail/service/AutoMailReplyService.kt` | 修改 |
| 2 | `test/.../AutoMailReplyServiceTest.kt` | 新增/修改 |

文件数 = 2 ≤ 10。子系统：自动回复发送拦截（单一）。

## 验收标准
- L3-1：两个自动发送点在收件人被抑制时均不发送。
- L3-2：拦截后经 `transition` 进入 `MANUAL_HANDOFF` 且留人工工单痕迹，未直接改 `currentStatus`。
- L3-3：`ManualExpertMailService` 路径不受影响（操作员可发）。
- 回归：未退订收件人的自动回复与会议邀请流程逐项不变。
