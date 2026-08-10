---
id: K-auto-send-paused-reason-prefix-routing
domain: mail
created: 2026-07-04
last_used: 2026-08-10
hit_count: 3
source: create-p:postmaster-reputation-auto-pause
severity: P1
---
经验：`autoSendPausedReason` 使用前缀约定路由恢复逻辑。已有前缀：`DAILY_LIMIT`（每日重置自动恢复）、`SELF_CHECK_FAILED:`（运营手动恢复）、`BOUNCE_RATE_HIGH:`（运营手动恢复）、`SMTP_TRANSIENT:`（运营手动恢复）。新增 `REPUTATION:`（信誉恢复服务自动恢复或运营手动恢复）。
正确做法：新增暂停来源时必须选择不与已有前缀冲突的 reason 前缀，并确认 `resumeDailyLimitPausedAccounts()` 的 `LIKE 'DAILY_LIMIT%'` 不会误匹配。新来源的批量恢复方法用 `LIKE '<新前缀>%'` 条件。
关联写路径：`MailSenderAccountRepository.pauseAutoSend()`, `MailSenderAccountRepository.resumeAutoSend()`, `MailSenderAccountRepository.resumeDailyLimitPausedAccounts()`。
