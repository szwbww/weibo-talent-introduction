---
id: K-sender-account-enabled-scope
domain: mail
created: 2026-07-06
last_used: 2026-08-10
hit_count: 8
source: create-p:pending-reply-account-consistency-and-disabled-receive
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
---
经验：`mail_sender_account.enabled=false` 不应再被理解为账号全局不可用；新目标语义是“禁止自动外发”。涉及账号语义变更时必须同时审计四类路径：自动发送选号（仍排除 disabled）、IMAP 接收/退信收集（允许 disabled）、人工发送（允许 disabled）、收发信箱/待处理队列可见性（允许 disabled 非模拟器账号）。`SIMULATOR_NOOP` 始终必须被真实收发路径排除。

补充（create-p:sender-binding, 2026-08-10）：本条的「人工发送允许 disabled」正在被收窄，但**边界很窄**。
`docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md` 引入
`expert_contact.bound_sender_account_code` 后，拦截对象是「**由绑定解析出的账号**」而非「人工发送」这个动作：
- 由绑定解析的人工发送（单封人工发信、会议确认、材料提醒批量）→ disabled 拦截
- 显式指定收信账号的回复（`PendingMailOperationService.kt:642-647`、`AutoMailReplyService`）→ **仍允许 disabled**，本条原始场景完整保留
- 人工路径**依然不判** `autoSendPaused` 与每日额度（见 [[K-operator-send-quota-paths]]）

起因：2026-08-10 `LiLei`（`enabled=0`）经 `selectAccountForManualSending()` 被选中并真实外发。
根因是 `isManualSendable()`（`MailSenderAccountService.kt:227-228`）只排除 `SIMULATOR_NOOP`。
关联：[[K-sender-account-selection-sites]]
