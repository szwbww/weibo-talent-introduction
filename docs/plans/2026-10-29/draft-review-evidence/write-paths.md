| 位置 | 所在函数 | 逐字调用 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:69` | `recordSuccess` | `mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:98` | `recordSuccess` | `mailSendAttemptRepository.save(attempt.copy(` |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:127` | `recordFailure` | `mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:153` | `recordFailure` | `mailSendAttemptRepository.save(attempt.copy(` |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:145` | `confirmMeetingAndEmail` | `mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1551` | `runIntroductionFromSnapshot` | `val attempt = mailSendAttemptRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:134` | `sendManualMailInternal` | `val saved = mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:287` | `prepareAndClaim` | `attemptRepository.insertIgnore(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:325` | `prepareAndClaim` | `val affected = attemptRepository.claimStatus(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:349` | `prepareAndClaim` | `val affected = attemptRepository.claimStatus(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:457` | `finalizeSuccess` | `val savedRecord = mailRecordRepository.save(mailRecord)` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:481` | `finalizeSuccess` | `attemptRepository.updateStatusAndError(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:558` | `finalizeFailure` | `val savedRecord = mailRecordRepository.save(mailRecord)` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:561` | `finalizeFailure` | `attemptRepository.updateStatusAndError(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1618` | `markResolved` | `inboundMailProcessingRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1689` | `cancelResolved` | `val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:193` | `bindToContact` | `val saved = inboundMailProcessingRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:238` | `markResolved` | `val saved = inboundMailProcessingRepository.save(record.copy(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:246` | `reclassifyFirstReplyAutoResponse` | `inboundMailProcessingRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:524` | `processSingleCore` | `val inboundMailRecord = mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:865` | `processSingleCore` | `val outboundRecord = mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1212` | `saveMailRecord` | `): MailRecord = mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1411` | `sendMeetingInvitation` | `val saved = mailRecordRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1473` | `confirmManualReviewWithBody` | `val saved = inboundMailProcessingRepository.save(` |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1529` | `confirmProcessed` | `val saved = inboundMailProcessingRepository.save(` |
