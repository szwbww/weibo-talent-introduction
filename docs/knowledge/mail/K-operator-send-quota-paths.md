---
id: K-operator-send-quota-paths
domain: mail
created: 2026-06-27
last_used: 2026-06-27
hit_count: 1
source: create-p:manual-send-decouple-daily-quota
severity: P2
---
经验：`mail_sender_account.todaySentCount` 有多条独立写路径，改"配额"前必须全部列清，否则漏改导致行为不一致。
写路径全集：① `ManualExpertMailService.sendManualMail`（OPERATOR，曾 +1）② `MeetingScheduleService.confirmSchedule`（SYSTEM，+1）③ `ManualOutreachTxHelper.recordSuccess`→`MailSenderAccountRepository.incrementTodaySentCount`（批量外联）④ 管理/重置：`updateAccount`/`resetTodaySentCount`/`resetDailyCounts`。注意 `PendingMailOperationService` 三个 OPERATOR 发送方法**不写** todaySentCount。
选号入口：`selectAccountForSending()`/`listSendableAccounts()`/`isSendable()` 均含 `todaySentCount < effectiveDailyLimit` 过滤——人工发送若走它就会被每日上限阻塞；`getEnabledAccount(code)` 仅校验 enabled，天然绕过上限。
正确做法：让"人工发送脱离配额"需同时改两处——选号兜底换不含上限判定的方法 + 移除发送后自增；不可只改其一。
