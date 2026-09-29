# 修复计划代码审计回执

日期：2026-09-29；HEAD：ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3。
只读rg；包含未改动路径，供实施前复核范围。匹配行不等于全部都是写入；按方法名与总计划的存储分类解读。退出码1的最后一组表示当前旧缓存key未在测试中写死，不要求批量改测试。

计划自检回执：三个子计划必需章节及顺序通过；文件清单分别7/8/8，去重19（生产9、测试10）；除明确新增的RecipientAddressFailureClassifier及其测试外，其余路径均存在；计划内相对链接及尾部空白检查通过；git diff --check通过；src/main、src/test无本次修改。以上只证明计划静态检查，不代表尚未实施的产品测试已通过。

## repository-access

命令：`rg -n '(bounceRecordRepository|mailRecordRepository|expertContactRepository|mailSendAttemptRepository|mailSenderAccountRepository)\.' src/main/kotlin`

```text
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewService.kt:23:        val account = mailSenderAccountRepository.findByAccountCode(code)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:121:        val records = mailRecordRepository.findInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:129:        val total = mailRecordRepository.countInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:140:            expertContactRepository.findAllById(contactIds).associateBy { requireNotNull(it.id) }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:177:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:181:        val contacts = expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:184:            val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:197:            val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:203:            val c = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:209:            val c = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:211:            val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:218:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:369:        val contactIds = expertContactRepository.findByOrcidIdIn(orcidIds).mapNotNull { it.id }
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:50:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:56:                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:81:            return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:710:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt:86:        if (!expertContactRepository.existsById(contactId)) {
src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateOperatorStatusSyncService.kt:18:        val latestUpdates = expertContactRepository.findAllByOrderByUpdatedAtDesc()
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:57:                val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2356:        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2398:        val contact = expertContactRepository.findById(contactId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2401:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:481:        val contact = expertContactRepository.findById(promotion.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:116:                expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/postmaster/service/ReputationAutoPauseService.kt:30:        val accountsForDomain = mailSenderAccountRepository.findAllByEnabledTrue()
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:384:            return expertContactRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:78:        val mail = mailRecordRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:134:        expertContactRepository.findById(contactId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:74:            introductions = mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:75:            inboundReplies = mailRecordRepository.countInboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:76:            repliedExperts = mailRecordRepository.countDistinctRepliedExpertsBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:77:            autoReplies = mailRecordRepository.countAutoRepliesBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:78:            operatorOutbound = mailRecordRepository.countOperatorOutboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:79:            meetingInvitations = mailRecordRepository.countOutboundByMailTypeBetween("MEETING_INVITATION", start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:82:            failedOutbound = mailRecordRepository.countFailedOutboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:95:        val records = mailRecordRepository.listIntroductions(start, end, senderAccountCode, pageSize.safeLimit(), pageOffset.safeOffset())
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:116:            totalCount = mailRecordRepository.countIntroductions(start, end, senderAccountCode)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:131:        val records = mailRecordRepository.listOutboundReplies(
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:137:            ?.let { mailRecordRepository.findAllById(it).associateBy { record -> record.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:172:            totalCount = mailRecordRepository.countOutboundReplies(start, end, triggeredBy, mailType, senderAccountCode, sendStatus)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:190:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:229:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:239:        val contact = expertContactRepository.findById(promotion.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:245:        val stats = mailRecordRepository.aggregateSenderAccountStats(from, to).associateBy { it.senderAccountCode }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:248:        return mailSenderAccountRepository.findAllByOrderByAccountCodeAsc().map { account ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:270:        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:271:        val softBounces = bounceRecordRepository.countSoftBouncesSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:272:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:289:        mailRecordRepository.aggregateIntroCohortByDomain(start, end, matureBefore).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:298:        mailRecordRepository.aggregateUndeliveredByDomain(start, end).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:303:        val unattributedBounceCount = bounceRecordRepository.countUnattributedBouncesBetween(start, end)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:326:        val countryRows = mailRecordRepository.aggregateIntroCohortByCountry(start, end, matureBefore)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:393:            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:209:            return expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:50:        val record = mailRecordRepository.findById(mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:60:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:98:            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:33:        val records = bounceRecordRepository.findPaged(accountCode, bounceType, limit, offset)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:34:        val totalCount = bounceRecordRepository.countPaged(accountCode, bounceType)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:37:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:60:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:84:        mailSenderAccountRepository.incrementTodaySentCount(accountCode, now)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:87:        val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:89:            mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:112:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:136:            val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:138:                mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:213:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:247:            mailRecordRepository.findAllById(outboundSentRows.map { it.id })
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:320:        val contacts = expertContactRepository.findAllById(rows.map { it.expertContactId })
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt:80:        val totals = expertContactRepository.countBindingsByAccount()
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt:82:        val segments = expertContactRepository.countBindingsByAccountAndCountry()
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt:394:            return mailRecordRepository.findByIdOrNull(mailRecordId)?.expertContactId
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:33:        val all = expertContactRepository.findAll().toList()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:75:            expertContactRepository.updateCountryById(contactId, country)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:96:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:98:                val contact = expertContactRepository.findById(outboundMail.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:147:        expertContactRepository.findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:162:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:190:        expertContactRepository.save(currentContact)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:251:                expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:253:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:67:            if (expertContactRepository.existsByCampaignIdAndOrcidId(campaignId, expert.orcidId)) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:82:            val contact = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt:21:        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt:22:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:111:            expertContactRepository.findAllById(contactIds).associateBy { it.id }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:135:        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:377:        val contact = expertContactRepository.findById(contactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:380:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:53:        val rows = mailRecordRepository.listMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:65:        val total = mailRecordRepository.countMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:109:        val total = mailRecordRepository.countMailboxExperts(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:125:        val summaries = mailRecordRepository.listMailboxExpertSummaries(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:144:        val mailRows = mailRecordRepository.listMailboxByExpertContactIds(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:192:        val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:207:        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:265:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:296:        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:328:        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:135:        if (bounceRecordRepository.existsByBounceMessageId(dedupeKey)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:149:        bounceRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:207:            for (record in mailRecordRepository.findOutboundCandidatesByMessageId(candidate)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:294:                expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:218:        if (!expertContactRepository.existsById(contactId)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:34:        val replyCount = mailRecordRepository.countInboundReplies(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:83:                val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt:224:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt:26:        expertContactRepository.countBindingsByAccount()
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:87:                val contactForSeed = contactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:111:                val previewContact = contactId?.let { expertContactRepository.findById(it).orElse(null) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:168:        val contact = expertContactRepository.findById(contactId).orElse(null) ?: return blocked
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:182:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:189:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:196:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:386:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:435:            mailRecordRepository.findById(anchorMailRecordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:439:            mailRecordRepository.findLatestSentOutboundAnchor(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:910:                val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1042:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1058:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1174:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1541:                expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1543:                        expertContactRepository.save(contact.copy(needsManualAttention = false))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1605:            expertContactRepository.findById(contactId).ifPresent { contact ->
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1607:                    expertContactRepository.save(contact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1661:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt:57:        if (!expertContactRepository.existsById(contactId)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:356:                    expertContactRepository.save(disabledContact.copy(needsManualAttention = true))
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:386:        val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:419:        val inboundMailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:760:        val outboundRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1047:    ): MailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1085:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1092:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1246:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1432:            .flatMap { mailRecordRepository.findOutboundCandidatesByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:45:        expertContactRepository.updateBindingById(contactId, code, at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:51:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:58:        expertContactRepository.rebindSenderAccountById(contactId, target.accountCode, now)  // I-2
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:70:        return expertContactRepository.findById(contactId).orElseThrow()
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:86:        val updated = expertContactRepository.migrateBindingByAccount(
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:108:        expertContactRepository.clearSenderChangeMarkById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:119:        return expertContactRepository.findById(contactId).orElseThrow()
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:56:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:70:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:105:        mailSenderAccountRepository.save(account.copy(lastSentAt = now))
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:249:            contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:214:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:208:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ConversationStateService.kt:26:        val saved = expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:224:        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:359:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:397:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:457:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:496:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:27:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:30:        val updated = expertContactRepository.save(contact.copy(operatorStatus = target.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:64:        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:88:        val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:94:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:145:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:166:        mailSenderAccountRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:191:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:217:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:54:        val contacts = expertContactRepository.findAll().toList()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:55:        val mailRecords = mailRecordRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:57:        val bounces = bounceRecordRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:26:        val contact = expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:69:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:86:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:103:        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:110:        return expertContactRepository.save(contact.copy(currentIndexLevel = "RAW", applicationIndexed = false))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:21:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:24:        return expertContactRepository.findById(alias.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:29:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(normalized)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:30:        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:33:        return expertContactRepository.findById(alias.expertContactId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:42:        expertContactRepository.findById(expertContactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:356:                        mailSenderAccountRepository.incrementTodaySentCount(account.accountCode, LocalDateTime.now())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:867:                        expertContactRepository.save(ExpertContact(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:950:                    val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:951:                    val attempt = mailSendAttemptRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1008:                                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1270:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1336:        expertContactRepository.findByOrcidIdIn(listOf(normOrcid))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1361:        return expertContactRepository.findByOrcidIdIn(distinct)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1378:        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1432:        val enabledAccounts = mailSenderAccountRepository.findAllByEnabledTrue()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1519:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1615:        val contacts = if (normOrcidList.isNotEmpty()) expertContactRepository.findByOrcidIdIn(normOrcidList) else emptyList()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:50:        expertContactRepository.findFilteredContacts(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:57:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:64:        return expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:71:        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:277:        val updated = expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:287:        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:296:        return expertContactRepository.save(contact.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:316:                expertContactRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:454:        expertContactRepository.findById(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:463:        val allContacts = expertContactRepository.findAll().toList()
```

## store-sql

命令：`rg -n -i '(insert into|update|delete from|alter table|create table).*(bounce_record|mail_record|expert_contact|mail_send_attempt|mail_sender_account)|operator_status' src/main/resources/db/migration src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt scripts tools --glob '!*.json'`

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:47:          AND (:operatorStatus IS NULL OR operator_status = :operatorStatus)
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:67:    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:72:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:81:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:92:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:105:        UPDATE expert_contact
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:1:CREATE TABLE mail_send_attempt (
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:15:ALTER TABLE mail_record ADD COLUMN error_summary VARCHAR(1024) DEFAULT NULL;
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:16:ALTER TABLE mail_record ADD COLUMN mail_send_attempt_id BIGINT DEFAULT NULL;
src/main/resources/db/migration/V11__expert_contact_auto_reply_flag.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V1__create_business_tables.sql:1:CREATE TABLE mail_sender_account (
src/main/resources/db/migration/V1__create_business_tables.sql:79:CREATE TABLE expert_contact (
src/main/resources/db/migration/V1__create_business_tables.sql:97:CREATE TABLE mail_record (
src/main/resources/db/migration/V51__add_follow_up_marked.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:3:-- 幂等：以 operator_status='NOT_CONTACTED' 为前置，重复执行为 no-op
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:7:UPDATE expert_contact ec
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:8:   SET ec.operator_status = 'CONTACTED'
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:9: WHERE ec.operator_status = 'NOT_CONTACTED'
src/main/resources/db/migration/V28__add_sender_account_auto_pause.sql:1:ALTER TABLE mail_sender_account
src/main/resources/db/migration/V34__add_sender_account_warmup_fields.sql:1:ALTER TABLE mail_sender_account
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:7:UPDATE expert_contact
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:11:UPDATE expert_contact_status_history
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:15:UPDATE expert_contact_status_history
src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql:3:ALTER TABLE expert_contact
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:2:ALTER TABLE mail_record
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:10:ALTER TABLE mail_record
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:20:CREATE TABLE mail_record_rag_fact (
src/main/resources/db/migration/V29__create_bounce_record.sql:1:CREATE TABLE bounce_record (
src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql:1:ALTER TABLE mail_record
src/main/resources/db/migration/V16__simulator_campaign.sql:2:INSERT INTO mail_sender_account (account_code, sender_email, sender_name, sender_title, sender_display_name,
src/main/resources/db/migration/V16__simulator_campaign.sql:15:UPDATE mail_sender_account SET enabled = TRUE WHERE account_code = 'SIMULATOR_NOOP' AND enabled = FALSE;
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:1:-- P-E T-1: operator_status 可空，默认 NULL = 不限（与 expert_contact.operator_status 的
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:6:    ADD COLUMN operator_status VARCHAR(32) NULL AFTER discipline;
src/main/resources/db/migration/V134__shared_inbox_owner.sql:20:ALTER TABLE mail_sender_account
src/main/resources/db/migration/V134__shared_inbox_owner.sql:24:ALTER TABLE mail_sender_account
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:1:CREATE TABLE expert_contact_status_history (
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:3:CREATE TABLE mail_record_qa_rule (
src/main/resources/db/migration/V141__create_mail_open_tracking.sql:11:ALTER TABLE mail_record
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:4:ALTER TABLE expert_contact
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:15:UPDATE expert_contact ec
src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql:3:ALTER TABLE mail_record
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:2:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:6:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:11:UPDATE expert_contact SET current_index_level = 'APPLICATION'
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:15:UPDATE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:23:INSERT INTO manual_handoff (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
src/main/resources/db/migration/V12__expert_contact_first_reply.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql:13:ALTER TABLE mail_record
src/main/resources/db/migration/V43__add_bounce_record_failed_recipient.sql:1:ALTER TABLE bounce_record
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:94:        'ALTER TABLE expert_email_alias ADD CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)'
src/main/resources/db/migration/V20__disable_orphan_simulator_mail_account.sql:2:UPDATE mail_sender_account
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:2:WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND COLUMN_NAME = 'operator_status';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:4:SET @add_col = 'ALTER TABLE expert_contact ADD COLUMN operator_status VARCHAR(32) NOT NULL DEFAULT ''NOT_CONTACTED'' COMMENT ''运营视角专家状态: NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED / INVITED / COMPLETED''';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:5:SET @noop = 'SELECT ''Column operator_status exists, skipping ALTER'' AS msg';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:13:WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND INDEX_NAME = 'idx_expert_contact_operator_status';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:15:SET @add_idx = 'ALTER TABLE expert_contact ADD INDEX idx_expert_contact_operator_status (operator_status, updated_at)';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:16:SET @skip_idx = 'SELECT ''Index idx_expert_contact_operator_status exists, skipping ALTER'' AS msg';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:23:UPDATE expert_contact
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:24:   SET operator_status = CASE
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:38:    action_type VARCHAR(64) NOT NULL COMMENT 'CHANGE_OPERATOR_STATUS / CHANGE_INDEX_LEVEL / SWITCH_REPLY_MODE / BIND_INBOUND_MAIL / SEND_QA_REPLY / SEND_MANUAL_RICH_REPLY / MARK_INBOUND_RESOLVED',
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:14:INSERT INTO v24_mail_record_ambiguous_guard (id) VALUES (1);
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:15:INSERT INTO v24_mail_record_ambiguous_guard (id)
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:18:ALTER TABLE mail_send_attempt
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:26:UPDATE mail_record mr
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:37:ALTER TABLE mail_record
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:42:UPDATE mail_send_attempt msa
src/main/resources/db/migration/V117__convert_bounce_rate_pause_to_warning.sql:1:UPDATE mail_sender_account
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:1:-- I3a-7: operator_statuses_json 成为唯一事实源；operator_status 单值列在本迁移中删除。
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:2:-- 照 V93 的两步范式（TEXT 不能带 DEFAULT）。空数组 [] = 不限（与旧 operator_status IS NULL 等价）。
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:4:    ADD COLUMN operator_statuses_json TEXT NOT NULL AFTER discipline;
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:7:SET operator_statuses_json = CASE
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:8:        WHEN operator_status IS NULL OR operator_status = '' THEN '[]'
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:9:        ELSE CONCAT('["', operator_status, '"]')
src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql:12:ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
src/main/resources/db/migration/V48__add_country_to_expert_contact.sql:1:ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:1:ALTER TABLE mail_record
src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql:13:ALTER TABLE mail_record
src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql:4:    ADD COLUMN expert_types_json TEXT NOT NULL AFTER operator_statuses_json;
```

## status-writes

命令：`rg -n 'syncOperatorStatus|operatorStatus\s*=|operatorStatus" to|operatorStatus.*remove|remove.*operatorStatus' src/main/kotlin`

```text
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:224:                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:244:                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:261:         * 禁止在别处另写 `term operatorStatus=NOT_CONTACTED`。
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:267:            return listOf(mapOf("term" to mapOf("operatorStatus" to status)))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:276:         * `term operatorStatus=EMAIL_INVALID` 蕴含 `exists operatorStatus`，
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:285:                mapOf("term" to mapOf("operatorStatus" to status))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:499:            operatorStatus = source.nullableText("operatorStatus"),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:1098:                    filters.add(mapOf("term" to mapOf("operatorStatus" to operatorStatus)))
src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateOperatorStatusSyncService.kt:22:        return expertIndexWriterService.syncOperatorStatusBatch(latestUpdates)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:75:    fun syncOperatorStatus(orcidId: String, operatorStatus: String): SingleSyncResult {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:86:                val body: Map<String, Any> = if (operatorStatus == "NOT_CONTACTED") {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:89:                            "source" to "if (ctx._source.containsKey('operatorStatus')) { ctx._source.remove('operatorStatus'); ctx._source.updatedAt = params.updatedAt; }",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:96:                            "operatorStatus" to operatorStatus,
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:117:            log.warn("syncOperatorStatus matched 0 docs across raw/candidate/application for orcid={}", normalizedOrcidId)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:122:    fun syncOperatorStatusBatch(updates: List<Pair<String, String>>): BulkSyncResult {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:141:                        val data = if (operatorStatus == "NOT_CONTACTED") {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:144:                                    "source" to "if (ctx._source.containsKey('operatorStatus')) { ctx._source.remove('operatorStatus'); ctx._source.updatedAt = params.updatedAt; }",
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:151:                                    "operatorStatus" to operatorStatus,
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:99:                else profile.operatorStatus == it
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:91:                operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:435:                operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED",
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:214:     * （与 syncOperatorStatusBatch 同款分批方式），值优先级 CANDIDATE > APPLICATION > RAW
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:216:     * （syncOperatorStatus 对 NOT_CONTACTED 走字段移除脚本）。
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:30:        val updated = expertContactRepository.save(contact.copy(operatorStatus = target.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:37:            before = mapOf("operatorStatus" to oldStatus),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:38:            after = mapOf("operatorStatus" to target.name),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:42:        expertIndexWriterService.syncOperatorStatus(updated.orcidId, target.name)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:53:        if (contact.operatorStatus == "EMAIL_INVALID") {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:64:        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:65:        expertIndexWriterService.syncOperatorStatus(updated.orcidId, targetStatus.name)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:85:        if (contact.operatorStatus == EMAIL_INVALID) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:88:        val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:89:        expertIndexWriterService.syncOperatorStatus(updated.orcidId, EMAIL_INVALID)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:870:                            currentStatus = "NEW", operatorStatus = "NOT_CONTACTED",
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1009:                                    contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1011:                                expertIndexWriterService.syncOperatorStatus(normOrcid, "EMAIL_INVALID")
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:578:        operatorStatus = operatorStatus,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:221:            operatorStatus = request.operatorStatus,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:1137:    operatorStatus = operatorStatus,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:168:                operatorStatus = summary.operatorStatus,
```

## ingest-and-constructors

命令：`rg -n 'bounceCollectionService\.|ManualInitialOutreachService\(|OperatorStatusReconcileService\(|BounceCollectionService\(' src/main src/test`

```text
src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt:182:        reconcileService = OperatorStatusReconcileService(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:41:class OperatorStatusReconcileService(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:73:class ManualInitialOutreachService(
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:32:                    bounceCollectionService.ingestKnownLogicalAccount(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:875:                    bounceCollectionService.ingest(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:944:        val bounceResult = bounceCollectionService.collectBounces(account, start, fetch.uidValidity)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:20:class BounceCollectionService(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:40:    private val service = OperatorStatusReconcileService(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:122:    private val service = ManualInitialOutreachService(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:701:        val service = ManualInitialOutreachService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:277:            bounceCollectionService.ingest(
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:22:    private val bounceCollectionService = BounceCollectionService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt:180:        val service = ManualInitialOutreachService(search, assignment, composer, delivery, contacts, campaigns,
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:50:    private val service = BounceCollectionService(
```

## cache-literal-tests

命令：`rg -n -F '20260929-discovery-schedule' src/test`

```text
(无匹配)
```
