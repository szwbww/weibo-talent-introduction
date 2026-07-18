---
id: K-sender-account-enabled-scope
domain: mail
created: 2026-07-06
last_used: 2026-07-18
hit_count: 3
source: create-p:pending-reply-account-consistency-and-disabled-receive
---
经验：`mail_sender_account.enabled=false` 不应再被理解为账号全局不可用；新目标语义是“禁止自动外发”。涉及账号语义变更时必须同时审计四类路径：自动发送选号（仍排除 disabled）、IMAP 接收/退信收集（允许 disabled）、人工发送（允许 disabled）、收发信箱/待处理队列可见性（允许 disabled 非模拟器账号）。`SIMULATOR_NOOP` 始终必须被真实收发路径排除。
