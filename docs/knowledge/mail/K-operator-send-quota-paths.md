---
id: K-operator-send-quota-paths
domain: mail
created: 2026-06-27
last_used: 2026-07-04
hit_count: 5
source: create-p:manual-send-decouple-daily-quota
severity: P2
---
经验：`mail_sender_account.todaySentCount` 有多条独立写路径，改"配额"前必须全部列清，否则漏改导致行为不一致。
写路径全集：① `ManualExpertMailService.sendManualMail`（OPERATOR，曾 +1）② `MeetingScheduleService.confirmSchedule`（SYSTEM，+1）③ `ManualOutreachTxHelper.recordSuccess`→`MailSenderAccountRepository.incrementTodaySentCount`（批量外联）④ 管理/重置：`updateAccount`/`resetTodaySentCount`/`resetDailyCounts`。注意 `PendingMailOperationService` 三个 OPERATOR 发送方法**不写** todaySentCount。
选号入口：`selectAccountForSending()`/`listSendableAccounts()`/`isSendable()` 均含 `todaySentCount < effectiveDailyLimit` 过滤——人工发送若走它就会被每日上限阻塞；`getEnabledAccount(code)` 仅校验 enabled，天然绕过上限。
正确做法：让"人工发送脱离配额"需同时改两处——选号兜底换不含上限判定的方法 + 移除发送后自增；不可只改其一。

补充（create-p:manual-batch-bypass-warmup, 2026-06-29）：额度"读判定"全集（区别于上面的"写"全集）= `SenderWarmupService.{effectiveDailyLimit,dailyState,remainingCapacity,isWarmupActive}` ←被← `MailSenderAccountService.{isSendable/listSendableAccounts,selectAccountForSending,remainingDailyCapacity,todayTotalCapacity,warmupActiveCount,effectiveDailyLimitFor,selectionScore}` + `SenderAccountAssignmentService.{selectAccount,assignmentScore}` + `ManualInitialOutreachService` 批量引擎（runRoundGate→listSendableAccounts、:204 remainingCapacity、:252 selectAccount、classify*/buildAccountStats）。要"按模式放宽额度上界"（如手动绕过预热但保留 dailySendLimit）推荐模式：给 `effectiveDailyLimit` 加 `ignoreWarmup=false` 形参（true 时首行 `return dailySendLimit`），逐层下传，判别条件用 `mode==MANUAL`（startManual/executeOneRound 均为 MANUAL，startAuto 为 AUTO）；默认 false 保证 AUTO/账号管理页/单封人工发送零回归。注意 `dailyState` 的 WARMUP_LIMIT_REACHED 分支在 ignoreWarmup 下自然失效。
