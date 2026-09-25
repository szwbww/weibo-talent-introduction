# mail_record 调用方逐点清单

### src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt
- 74: `introductions = mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", start, end),`
- 75: `inboundReplies = mailRecordRepository.countInboundBetween(start, end),`
- 76: `repliedExperts = mailRecordRepository.countDistinctRepliedExpertsBetween(start, end),`
- 77: `autoReplies = mailRecordRepository.countAutoRepliesBetween(start, end),`
- 78: `operatorOutbound = mailRecordRepository.countOperatorOutboundBetween(start, end),`
- 79: `meetingInvitations = mailRecordRepository.countOutboundByMailTypeBetween("MEETING_INVITATION", start, end),`
- 82: `failedOutbound = mailRecordRepository.countFailedOutboundBetween(start, end),`
- 95: `val records = mailRecordRepository.listIntroductions(start, end, senderAccountCode, pageSize.safeLimit(), pageOffset.safeOffset())`
- 116: `totalCount = mailRecordRepository.countIntroductions(start, end, senderAccountCode)`
- 131: `val records = mailRecordRepository.listOutboundReplies(`
- 137: `?.let { mailRecordRepository.findAllById(it).associateBy { record -> record.id } }`
- 172: `totalCount = mailRecordRepository.countOutboundReplies(start, end, triggeredBy, mailType, senderAccountCode, sendStatus)`
- 245: `val stats = mailRecordRepository.aggregateSenderAccountStats(from, to).associateBy { it.senderAccountCode }`
- 272: `val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)`
- 289: `mailRecordRepository.aggregateIntroCohortByDomain(start, end, matureBefore).forEach { row ->`
- 298: `mailRecordRepository.aggregateUndeliveredByDomain(start, end).forEach { row ->`
- 326: `val countryRows = mailRecordRepository.aggregateIntroCohortByCountry(start, end, matureBefore)`

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt
- 71: `val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
- 1187: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`
- 1421: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt
- 55: `val mailRecords = mailRecordRepository.findAll()`

### src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt
- 78: `val mail = mailRecordRepository.findById(sourceId).orElseThrow {`

### src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt
- 57: `val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)`

### src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt
- 81: `return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)`
- 710: `val record = mailRecordRepository.findByIdOrNull(id)`

### src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt
- 98: `mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)`

### src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
- 380: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt
- 50: `val record = mailRecordRepository.findById(mailRecordId).orElse(null)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
- 435: `mailRecordRepository.findById(anchorMailRecordId)`
- 439: `mailRecordRepository.findLatestSentOutboundAnchor(`
- 908: `val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)`
- 1172: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt
- 96: `.firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt
- 34: `val replyCount = mailRecordRepository.countInboundReplies(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt
- 53: `val rows = mailRecordRepository.listMailbox(`
- 65: `val total = mailRecordRepository.countMailbox(`
- 109: `val total = mailRecordRepository.countMailboxExperts(`
- 125: `val summaries = mailRecordRepository.listMailboxExpertSummaries(`
- 144: `val mailRows = mailRecordRepository.listMailboxByExpertContactIds(`
- 192: `val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)`
- 265: `val record = mailRecordRepository.findByIdOrNull(id)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt
- 214: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt
- 201: `for (record in mailRecordRepository.findOutboundCandidatesByMessageId(candidate)) {`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt
- 394: `return mailRecordRepository.findByIdOrNull(mailRecordId)?.expertContactId`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt
- 182: `mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(`
- 189: `mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt
- 386: `val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(`
- 1077: `mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(`
- 1084: `mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(`
- 1423: `.flatMap { mailRecordRepository.findOutboundCandidatesByMessageId(it) }`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
- 224: `val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null`
- 359: `val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)`
- 457: `val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt
- 245: `contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt
- 22: `val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)`

### src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
- 245: `mailRecordRepository.findAllById(outboundSentRows.map { it.id })`

### src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt
- 121: `val records = mailRecordRepository.findInboundMailsForSimulation(`
- 129: `val total = mailRecordRepository.countInboundMailsForSimulation(`
- 177: `val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)`
- 184: `val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)`
- 197: `val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)`
- 211: `val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)`
- 218: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
- 2356: `val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {`
- 2401: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

### src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt
- 50: `val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)`
- 56: `val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`

原生 SQL 读取补充：`mail-record-reads.txt` 中 MailboxConversationRepository；其显式列投影不读新增字段。
