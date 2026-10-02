# 检索原始证据

日期：2026-10-02；HEAD：d41495e590ee2fae2eb757ffc172c2ba1e1f9212。范围为 src/main、scripts、tools；不是线上数据库全量审计。exit 1 的空输出表示该次检索未命中。

## inbound-store

```sh
rg --no-ignore -n "inbound_mail_processing|InboundMailProcessingRepository" src/main scripts tools
```

```text
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:14:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:42:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt:7:@Table("inbound_mail_processing")
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:22:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:58:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:8:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:602:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:6:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:26:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:47: * latestInbound.processingId 是真实 inbound_mail_processing.id（供可信工作台），
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:12: * 数据权威（I-1）：来信只取 linked inbound_mail_processing（排除未匹配与独立机器信；
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:434:     * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:492:              FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:526:        // 入站匹配 inbound_mail_processing.from_email（别名）。recipientEmail/keyword 与
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:550:                SELECT 1 FROM inbound_mail_processing impi
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:614:                    SELECT 1 FROM inbound_mail_processing imp_replied
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:12:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:25:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:3:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:10:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:46:        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:69:        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:9:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:44:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:95:    /** 线上来信：inbound_mail_processing 行；必须已绑定联系人。 */
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:101:                "inbound_mail_processing $sourceId does not exist"
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:107:            "inbound_mail_processing $sourceId has no expert contact"
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:84:                    JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:89:                    JOIN inbound_mail_processing p2 ON p2.id = t2.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:118:                    JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:123:                    JOIN inbound_mail_processing p2 ON p2.id = t2.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:550:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:597:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:667:                FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:733:                FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:826:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:56:        /** 来信路径 = 真实 inbound_mail_processing.id；会话回信路径 = null（I-3）。 */
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:19:interface InboundMailProcessingRepository : CrudRepository<InboundMailProcessing, Long> {
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:37:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:50:        UPDATE inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:87:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:104:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:121:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:140:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:152:        FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:159:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:167:        FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:176:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:185:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:194:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:213:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:229:          FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:238:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:245:    @Query("SELECT COUNT(*) FROM inbound_mail_processing")
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:250:        SELECT p.* FROM inbound_mail_processing p
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:276:        SELECT COUNT(*) FROM inbound_mail_processing p
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:11:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:21:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:6:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:43:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:10:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:33: * 其唯一 owner（mail_record 或 inbound_mail_processing）→ contact。
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:55:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:855:              LEFT JOIN inbound_mail_processing p ON p.id = COALESCE(t.inbound_processing_id, a.inbound_processing_id)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:865:                     SELECT p.id FROM inbound_mail_processing p
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:877:                     SELECT p.id FROM inbound_mail_processing p
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:21:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:42:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:120:        // `inbound_mail_processing`/`mail_record`/意图/标签/附件/专家状态，只在允许确认时
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:22:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:38:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:17:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:63:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:5:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:39:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:6:import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:37:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertRepliedDismissalService.kt:27:            "SELECT MAX(id) FROM inbound_mail_processing WHERE expert_contact_id = :contactId",
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:12:        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id);
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:1:CREATE TABLE inbound_mail_processing (
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:16:    UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid),
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:17:    KEY idx_inbound_mail_processing_status (process_status, received_at),
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:18:    CONSTRAINT fk_inbound_mail_processing_contact
src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql:62:        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id),
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:3:-- The MANUAL_REVIEW value used by inbound_mail_processing.process_status is a
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:18:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:23:UPDATE inbound_mail_processing
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:2:-- V120 inbound_mail_processing: UID 唯一性以 UIDVALIDITY 界定（fast-p 04）
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:17:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:20:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:21:    DROP INDEX uk_inbound_mail_processing_uid,
src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:22:    ADD UNIQUE KEY uk_inbound_mail_processing_uid_validity (sender_account_code, uid_validity, imap_uid);
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:98:        'inbound_mail_processing',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:100:        'ALTER TABLE inbound_mail_processing ADD COLUMN in_reply_to VARCHAR(255) DEFAULT NULL AFTER message_id'
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:103:        'inbound_mail_processing',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:105:        'ALTER TABLE inbound_mail_processing ADD COLUMN body TEXT DEFAULT NULL AFTER subject'
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:108:        'inbound_mail_processing',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:110:        'ALTER TABLE inbound_mail_processing ADD COLUMN cleaned_body TEXT DEFAULT NULL AFTER body'
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:113:        'inbound_mail_processing',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:115:        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_at DATETIME DEFAULT NULL AFTER process_reason'
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:118:        'inbound_mail_processing',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:120:        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_by VARCHAR(100) DEFAULT NULL AFTER resolved_at'
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:36:-- 2. inbound_mail_processing 增加 reason_type 列，给前端按原因过滤用
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:37:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:43:UPDATE inbound_mail_processing
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:52:        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id)
src/main/resources/db/migration/V134__shared_inbox_owner.sql:2:-- V134 共享物理收件箱归属：mail_sender_account / inbound_mail_processing（fast-p 01）
src/main/resources/db/migration/V134__shared_inbox_owner.sql:18:--     uk_inbound_mail_processing_uid_validity（逻辑账号 + 代际 + UID）。
src/main/resources/db/migration/V134__shared_inbox_owner.sql:28:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V134__shared_inbox_owner.sql:32:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V134__shared_inbox_owner.sql:33:    ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);

[exit_code=0]
```

## inbound-writers

```sh
rg --no-ignore -n "InboundMailProcessing\(|inboundMailProcessingRepository\.(save|reopenManualResolved)|UPDATE inbound_mail_processing" src/main
```

```text
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:43:UPDATE inbound_mail_processing
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:23:UPDATE inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt:8:data class InboundMailProcessing(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:193:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:238:        val saved = inboundMailProcessingRepository.save(record.copy(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1524:        inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1595:        val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1308:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1309:            InboundMailProcessing(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1364:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1365:            InboundMailProcessing(
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:50:        UPDATE inbound_mail_processing

[exit_code=0]
```

## inbound-readers

```sh
rg --no-ignore -n "inboundMailProcessingRepository\." src/main/kotlin
```

```text
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:80:            manualReviewInbound = inboundMailProcessingRepository.countManualReviewBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:81:            unmatchedInbound = inboundMailProcessingRepository.countUnmatchedBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:185:        val records = inboundMailProcessingRepository.listInboundActivity(
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:214:            totalCount = inboundMailProcessingRepository.countInboundActivity(start, end, processStatus, reasonType)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:246:        val lastReceived = inboundMailProcessingRepository.findLastReceivedAtPerAccount()
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2373:        val inbound = inboundMailProcessingRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:410:                ?: inboundMailProcessingRepository.findAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:97:        val inbound = inboundMailProcessingRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:259:        inboundMailProcessingRepository.findAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:477:                val processing = inboundMailProcessingRepository.findById(candidate.id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:730:                val processing = inboundMailProcessingRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:40:        val records = inboundMailProcessingRepository.listInboundSummary(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:48:        val totalCount = inboundMailProcessingRepository.countInboundSummary(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:83:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:87:            inboundMailProcessingRepository.findAllByExpertContactId(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:149:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:270:                val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:23:        val total = inboundMailProcessingRepository.countAll()
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:25:            val batch = inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(batchSize, offset)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:126:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:146:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:192:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1037:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1053:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1518:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1524:        inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1537:            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1576:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1595:        val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1656:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:51:                inboundMailProcessingRepository.findUnmatchedManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:53:                ) to inboundMailProcessingRepository.countUnmatchedManualReviewQueue(activeCodes, normalizedQuery)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:56:            inboundMailProcessingRepository.findManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:62:            ) to inboundMailProcessingRepository.countManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:71:            val total = inboundMailProcessingRepository.countManualReviewByAccounts(activeCodes)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:72:            val grouped = inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(activeCodes)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:85:        val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:158:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:193:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:234:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:238:        val saved = inboundMailProcessingRepository.save(record.copy(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:249:            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW")
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:195:        val processing = inboundMailProcessingRepository.findById(processingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:162:        inboundMailProcessingRepository.findByMailboxOwnerCodeAndUidValidityAndImapUid(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:183:        val legacySameUid = inboundMailProcessingRepository.findLegacyOwnerlessByGroupAndImapUid(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1308:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1364:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:242:        if (!inboundMailProcessingRepository.existsById(inboundProcessingId)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:50:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)

[exit_code=0]
```

## contact-store

```sh
rg --no-ignore -n "expert_contact|ExpertContactRepository" src/main scripts tools
```

```text
src/main/resources/db/migration/V143__create_expert_replied_dismissal.sql:3:    expert_contact_id  BIGINT NOT NULL,
src/main/resources/db/migration/V143__create_expert_replied_dismissal.sql:6:    PRIMARY KEY (username, expert_contact_id),
src/main/resources/db/migration/V143__create_expert_replied_dismissal.sql:8:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V29__create_bounce_record.sql:6:    original_expert_contact_id BIGINT,
src/main/resources/db/migration/V29__create_bounce_record.sql:15:    INDEX idx_original_contact (original_expert_contact_id)
src/main/resources/db/migration/V59__create_expert_analysis_result.sql:3:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V59__create_expert_analysis_result.sql:13:    KEY idx_analysis_contact (expert_contact_id, display_order),
src/main/resources/db/migration/V59__create_expert_analysis_result.sql:15:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql:12:--   I-2 expert_contact_id 必须是当前已存在专家（FK ON DELETE RESTRICT）；
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql:18:-- 索引 (expert_contact_id, created_at)：按专家列出本人上传附件的稳定顺序。
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql:23:    expert_contact_id BIGINT       NOT NULL,
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql:31:    KEY idx_outbound_mail_attachment_contact (expert_contact_id, created_at),
src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql:33:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:7:UPDATE expert_contact
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:11:UPDATE expert_contact_status_history
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:15:UPDATE expert_contact_status_history
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:25:    (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:34:FROM expert_contact ec
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:39:      WHERE mh.expert_contact_id = ec.id
src/main/kotlin/com/weibo/talentintroduction/document/domain/ManualExpertMaterialUpload.kt:12: *   `expert_document.expert_contact_id` 同时等于请求的 contactId；
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql:6:--      只存归属（expert_contact_id）、操作者（uploaded_by）与上传时间（created_at）；
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql:10:--      （owner 链：manual_upload_id → manual_expert_material_upload.expert_contact_id）。
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql:26:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql:29:    KEY idx_manual_material_upload_contact (expert_contact_id, created_at, id),
src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql:31:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V43__add_bounce_record_failed_recipient.sql:2:    ADD COLUMN failed_recipient VARCHAR(255) NULL AFTER original_expert_contact_id;
src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateOperatorStatusSyncService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateOperatorStatusSyncService.kt:9:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql:3:ALTER TABLE expert_contact
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:45:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:138:                "expert_contact $contactId does not exist"
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:60:    // 手动材料 owner 解析（V130）：manual_upload_id → manual_expert_material_upload.expert_contact_id。
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:72:     * - mail_record owner → record.expert_contact_id；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:73:     * - processing owner → processing.expert_contact_id（未绑定来信返回 null）；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:74:     * - manual owner（V130）→ upload.expert_contact_id；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:230:            pExpertContactId = rs.getLongOrNull("p_expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:236:            mrExpertContactId = rs.getLongOrNull("mr_expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:237:            muExpertContactId = rs.getLongOrNull("mu_expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:642:    /** dryRun：只返回候选（processing.expert_contact_id=id 且 attachment 为
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:778:               AND expert_contact_id = :expertContactId
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:842:                   p.expert_contact_id AS p_expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:848:                   mr.expert_contact_id AS mr_expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:849:                   mu.expert_contact_id AS mu_expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:858:             WHERE d.expert_contact_id = :contactId
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:866:                      WHERE p.expert_contact_id = :contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:878:                      WHERE p.expert_contact_id = :contactId)
src/main/resources/db/migration/V138__create_batch_email_verification.sql:36:-- 不建 contact 外键：验证拒绝的目标不得创建/绑定 expert_contact。
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:2:WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND COLUMN_NAME = 'operator_status';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:4:SET @add_col = 'ALTER TABLE expert_contact ADD COLUMN operator_status VARCHAR(32) NOT NULL DEFAULT ''NOT_CONTACTED'' COMMENT ''运营视角专家状态: NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED / INVITED / COMPLETED''';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:13:WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND INDEX_NAME = 'idx_expert_contact_operator_status';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:15:SET @add_idx = 'ALTER TABLE expert_contact ADD INDEX idx_expert_contact_operator_status (operator_status, updated_at)';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:16:SET @skip_idx = 'SELECT ''Index idx_expert_contact_operator_status exists, skipping ALTER'' AS msg';
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:23:UPDATE expert_contact
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:36:    expert_contact_id BIGINT NULL,
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:45:    KEY idx_operator_action_contact_created (expert_contact_id, created_at),
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:50:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:7:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt:37:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt:64:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V121__create_expert_follow.sql:4:-- 复合主键 (username, expert_contact_id)：同一用户对同一专家至多一行。
src/main/resources/db/migration/V121__create_expert_follow.sql:8:-- followed 过滤。关注不写 ES / expert_contact / 来信处理状态（I-3）。
src/main/resources/db/migration/V121__create_expert_follow.sql:12:    expert_contact_id  BIGINT       NOT NULL,
src/main/resources/db/migration/V121__create_expert_follow.sql:14:    PRIMARY KEY (username, expert_contact_id),
src/main/resources/db/migration/V121__create_expert_follow.sql:16:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:40:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:1:-- 1. expert_contact 增加 ES 层级与人工待办标记
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:2:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:6:ALTER TABLE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:11:UPDATE expert_contact SET current_index_level = 'APPLICATION'
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:15:UPDATE expert_contact
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:23:INSERT INTO manual_handoff (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:25:  FROM expert_contact ec
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:31:        WHERE mh.expert_contact_id = ec.id
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:46:       WHEN expert_contact_id IS NULL THEN 'UNMATCHED_CONTACT'
src/main/kotlin/com/weibo/talentintroduction/document/repository/ManualExpertMaterialUploadRepository.kt:10: * `expert_contact_id`）与材料列表的来源/上传时间投影（列表走 SQL LEFT JOIN，不经本仓库）。
src/main/kotlin/com/weibo/talentintroduction/expert/repository/ExpertApplicationPromotionRepository.kt:56:          JOIN expert_contact ec ON eap.expert_contact_id = ec.id
src/main/resources/db/migration/V12__expert_contact_first_reply.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:30:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:42:    KEY idx_eap_contact (expert_contact_id, created_at),
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:43:    CONSTRAINT fk_eap_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:48:    (expert_contact_id, orcid_id, source_inbound_id, triggered_by, promotion_status,
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:52:  FROM expert_contact ec
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:56:        WHERE eap.expert_contact_id = ec.id
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:11:    expert_contact_id BIGINT,
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:19:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:45:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:3:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:14:    KEY idx_meeting_schedule_contact (expert_contact_id),
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:15:    CONSTRAINT fk_meeting_schedule_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
src/main/resources/db/migration/V48__add_country_to_expert_contact.sql:1:ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;
src/main/resources/db/migration/V48__add_country_to_expert_contact.sql:2:CREATE INDEX idx_expert_contact_country ON expert_contact (country);
src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql:5:    expert_contact_id     BIGINT       NOT NULL,
src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql:28:    KEY idx_arcl_contact (expert_contact_id),
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:35:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:7:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:13:    KEY idx_inbound_intent_contact (expert_contact_id, created_at),
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:18:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V125__create_meeting_calendar_event.sql:3:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V125__create_meeting_calendar_event.sql:14:    KEY idx_meeting_calendar_contact_status_start_id (expert_contact_id, status, starts_at_utc, id),
src/main/resources/db/migration/V125__create_meeting_calendar_event.sql:17:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT,
src/main/kotlin/com/weibo/talentintroduction/monitoring/controller/MailMonitoringResponses.kt:136:    // I-4：归因不到专家的退信条数（original_expert_contact_id IS NULL 或指向不存在的专家），不计入任何 rows 元素
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:44:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:1:CREATE TABLE expert_contact_status_history (
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:3:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:9:    KEY idx_expert_contact_status_history_contact (expert_contact_id, created_at),
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:10:    KEY idx_expert_contact_status_history_status (to_status, created_at),
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:11:    CONSTRAINT fk_expert_contact_status_history_contact
src/main/resources/db/migration/V8__add_expert_contact_status_history.sql:12:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:7:UPDATE expert_contact ec
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:12:        WHERE mr.expert_contact_id = ec.id
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:4:ALTER TABLE expert_contact
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:10:CREATE INDEX idx_expert_contact_bound_sender
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:11:    ON expert_contact (bound_sender_account_code);
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:15:UPDATE expert_contact ec
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:17:    SELECT mr.expert_contact_id,
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:27:     GROUP BY mr.expert_contact_id
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:28:) f ON f.expert_contact_id = ec.id
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:314:     * I-3/I-4: 目标专家已有任一 `expert_contact.bound_sender_account_code`（与绑定值是否在
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt:7:@Table("expert_contact")
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:16:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:23:    KEY idx_expert_document_contact (expert_contact_id, document_type, document_status),
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:25:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
src/main/resources/db/migration/V1__create_business_tables.sql:79:CREATE TABLE expert_contact (
src/main/resources/db/migration/V1__create_business_tables.sql:93:    CONSTRAINT fk_expert_contact_campaign
src/main/resources/db/migration/V1__create_business_tables.sql:99:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V1__create_business_tables.sql:112:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
src/main/resources/db/migration/V1__create_business_tables.sql:119:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V1__create_business_tables.sql:127:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V11__expert_contact_auto_reply_flag.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V111__create_expert_material_status.sql:4:-- I1-4: 每个联系人每种材料至多一行，由唯一键 (expert_contact_id, material_code) 保证。
src/main/resources/db/migration/V111__create_expert_material_status.sql:6:-- 不声明 ON DELETE CASCADE，与现有 expert_contact 子表外键基线一致。
src/main/resources/db/migration/V111__create_expert_material_status.sql:9:    expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V111__create_expert_material_status.sql:14:    UNIQUE KEY uk_expert_material_contact_code (expert_contact_id, material_code),
src/main/resources/db/migration/V111__create_expert_material_status.sql:20:        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt:12:        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt:36:        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt:71:        SELECT DISTINCT expert_contact_id FROM operator_action_log
src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt:73:          AND expert_contact_id IN (:contactIds)
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContactStatusHistory.kt:7:@Table("expert_contact_status_history")
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:3:                                                  expert_contact_id BIGINT NOT NULL,
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:10:                                                  CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:88:        'idx_expert_email_alias_expert_contact_id',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:89:        'CREATE INDEX idx_expert_email_alias_expert_contact_id ON expert_email_alias(expert_contact_id)'
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:93:        'fk_expert_email_alias_expert_contact',
src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql:94:        'ALTER TABLE expert_email_alias ADD CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)'
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:21:    private val expertContactRepository: ExpertContactRepository,
src/main/resources/db/migration/V51__add_follow_up_marked.sql:1:ALTER TABLE expert_contact
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:1:-- P-E T-1: operator_status 可空，默认 NULL = 不限（与 expert_contact.operator_status 的
src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql:4:-- 不加 CHECK 约束以便枚举演进（与 expert_contact 同款立场）。
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:27:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:28:    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:7:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:43:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:53:    /** 全表扫描 + 内存比对（expert_contact 2062 行 / mail_record 2157 行，规模小，一次读入）。 */
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:8:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:29:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertEmailAliasService.kt:14:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ConversationStateService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ConversationStateService.kt:13:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:25:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt:103:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:7:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertOperatorStatusService.kt:14:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:28:                expertContactId = rs.getLong("expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:84:            clauses += "e.expert_contact_id = :contactId"
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:109:            BASE_SELECT + " WHERE e.status = 'ACTIVE' AND e.expert_contact_id IN (:contactIds)" +
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:179:            SELECT e.id, e.expert_contact_id, e.source_mail_record_id,
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:184:              JOIN expert_contact c ON c.id = e.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:189:                (expert_contact_id, source_mail_record_id, starts_at_utc, ends_at_utc,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:17:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:90:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:323:                // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:744:                // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1417:     * I-3: 同一 ORCID 的**任一** `expert_contact` 行（任意 campaign）已有非空白绑定即为已绑定。
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt:13: *   `manual_expert_material_upload.expert_contact_id`，不是邮件事件）。
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:6:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertIndexLevelOperationService.kt:14:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:9:interface ExpertContactRepository : CrudRepository<ExpertContact, Long> {
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:44:        SELECT * FROM expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:67:    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:72:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:81:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:92:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:105:        UPDATE expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:116:          FROM expert_contact
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:128:          FROM expert_contact
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:54:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:603:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:48:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:166:                // expert_contact 无机构列（MySQL 无 institution 存储），此处恒 null，
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:6:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt:16:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:58:    // I-4: bounce_record 无任何外键（V29__create_bounce_record.sql），original_expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:59:    // 可能指向已不存在的 expert_contact（孤儿引用）；主查询的 JOIN expert_contact 会丢弃这类行，
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:61:    // mail_record.expert_contact_id 有 FK（V1__create_business_tables.sql），发送失败那一支无孤儿问题。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:66:           AND (br.original_expert_contact_id IS NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:67:                OR NOT EXISTS (SELECT 1 FROM expert_contact ec WHERE ec.id = br.original_expert_contact_id))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt:12: * 全部语句只有 INSERT 与按 (expert_contact_id, id 集合) 的 SELECT：不存在
src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt:30:                (id, expert_contact_id, created_by, file_name, content_type,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt:62:            SELECT id, expert_contact_id, created_by, file_name, content_type,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt:65:             WHERE expert_contact_id = :expertContactId
src/main/kotlin/com/weibo/talentintroduction/mail/repository/OutboundMailAttachmentRepository.kt:79:                expertContactId = rs.getLong("expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt:14:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:71:    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:49:          AND p.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:72:          AND p.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:27:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:200:     * SELECT 列表（source / id / expert_contact_id / direction / mail_type /
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:203:     * LEFT JOIN expert_contact / EXISTS(mail_attachment)）。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:128:                SELECT u.expert_contact_id AS expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:130:                  JOIN expert_contact ec ON ec.id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:132:                 GROUP BY u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:153:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:165:                          AND ef.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:168:              JOIN expert_contact ec ON ec.id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:170:             GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:194:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:206:                          AND ef.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:209:              JOIN expert_contact ec ON ec.id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:211:             GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:236:            "u.expert_contact_id DESC"
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:260:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:271:             WHERE u.expert_contact_id IN (:contactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:275:                    WHERE newer.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:289:            rs.getLong("expert_contact_id") to rs.toLatestMessageRow()
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:302:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:308:             WHERE u.expert_contact_id IN (:contactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:313:                    WHERE newer.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:328:            rs.getLong("expert_contact_id") to ConversationLatestInboundRow(
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:345:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:348:             WHERE u.expert_contact_id IN (:contactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:349:             GROUP BY u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:360:            rs.getLong("expert_contact_id") to codes
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:368:            SELECT expert_contact_id AS expert_contact_id, COUNT(*) AS material_count
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:370:             WHERE expert_contact_id IN (:contactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:371:             GROUP BY expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:374:            rs.getLong("expert_contact_id") to rs.getLong("material_count")
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:395:            SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:409:             WHERE u.expert_contact_id = :contactId
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:452:            SELECT mr.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:471:               AND mr.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:475:            SELECT imp.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:493:             WHERE imp.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:525:        // expert_contact.expert_email（本专家所有出站共享的联系人邮箱，在出站 EXISTS 内恒等），
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:533:                   AND mro.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:551:                 WHERE impi.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:577:     * WHERE 片段：q（真实 expert_contact 姓名/邮箱）+ followed 过滤 + 消息级 membership。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:594:                   AND eff.expert_contact_id = u.expert_contact_id))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:601:                       AND ef_replied.expert_contact_id = u.expert_contact_id)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:606:                     WHERE mr_replied.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:615:                     WHERE imp_replied.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:621:                              AND erd.expert_contact_id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:661:            expertContactId = getLong("expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:690:            expertContactId = getLong("expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt:141:            expertContactId = rs.getLong("expert_contact_id"),
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt:164:            "LEFT JOIN expert_contact ec ON ec.id = m.expert_contact_id"
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt:165:        private const val SELECT = "SELECT m.id AS mail_record_id, m.expert_contact_id, ec.expert_name, " +
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:123:          AND expert_contact_id IS NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:142:          AND expert_contact_id IS NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:252:          AND p.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:278:          AND p.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt:20:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt:39:    private val expertContactRepository: ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:58:    /** expert_contact 无机构列（DB 无 institution 存储）；恒 null，UI 自专家资料流程补充。 */
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:49:        WHERE expert_contact_id = :contactId AND direction = 'INBOUND'
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:58:        SELECT mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:60:        INNER JOIN expert_contact ec ON ec.id = mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:62:          AND mr.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:66:        GROUP BY mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:77:            SELECT expert_contact_id, MAX(id) AS latest_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:80:              AND expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:81:              AND (:unrestricted = true OR expert_contact_id IN (:contactIds))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:85:                    WHERE p.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:90:                    WHERE p2.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:93:            GROUP BY expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:111:            SELECT expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:114:              AND expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:115:              AND (:unrestricted = true OR expert_contact_id IN (:contactIds))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:119:                    WHERE p.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:124:                    WHERE p2.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:127:            GROUP BY expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:167:        WHERE expert_contact_id = :expertContactId
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:207:        SELECT COUNT(DISTINCT expert_contact_id) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:331:        WHERE expert_contact_id = :expertContactId
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:356:    // 队列口径：分母 = 窗口内首发 INTRODUCTION 按 expert_contact_id 去重的人数。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:361:    // 不读取 expert_contact.first_reply_at）。发送失败的 INTRODUCTION sent_at 为 null，天然不进队列，
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:374:          FROM (SELECT expert_contact_id, MIN(sent_at) AS first_sent_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:378:                 GROUP BY expert_contact_id) s
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:379:          JOIN expert_contact ec ON ec.id = s.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:380:          LEFT JOIN (SELECT inb.expert_contact_id, MIN(inb.received_at) AS first_reply_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:382:                       JOIN (SELECT expert_contact_id, MIN(sent_at) AS first_sent_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:386:                              GROUP BY expert_contact_id) c
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:387:                         ON c.expert_contact_id = inb.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:391:                      GROUP BY inb.expert_contact_id) r
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:392:            ON r.expert_contact_id = s.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:414:          FROM (SELECT expert_contact_id, MIN(sent_at) AS first_sent_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:418:                 GROUP BY expert_contact_id) s
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:419:          JOIN expert_contact ec ON ec.id = s.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:420:          LEFT JOIN (SELECT inb.expert_contact_id, MIN(inb.received_at) AS first_reply_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:422:                       JOIN (SELECT expert_contact_id, MIN(sent_at) AS first_sent_at
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:426:                              GROUP BY expert_contact_id) c
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:427:                         ON c.expert_contact_id = inb.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:431:                      GROUP BY inb.expert_contact_id) r
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:432:            ON r.expert_contact_id = s.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:447:               COUNT(DISTINCT u.expert_contact_id) AS undelivered_count
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:449:                SELECT expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:455:                SELECT original_expert_contact_id AS expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:457:                 WHERE original_expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:460:          JOIN expert_contact ec ON ec.id = u.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:469:        WHERE expert_contact_id IN (:contactIds) AND sender_account_code IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:500:                 mr.id AS id, mr.expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:517:            LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:530:                 imp.id AS id, imp.expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:551:            LEFT JOIN expert_contact ec2 ON imp.expert_contact_id = ec2.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:584:            LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:598:            LEFT JOIN expert_contact ec2 ON imp.expert_contact_id = ec2.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:624:        SELECT u.expert_contact_id AS expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:637:              SELECT mr.expert_contact_id, COALESCE(mr.sent_at, mr.created_at) AS event_at,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:643:                JOIN expert_contact ec1 ON ec1.id = mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:645:                 AND mr.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:646:                 AND (:expertContactId IS NULL OR mr.expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:664:              SELECT imp.expert_contact_id, imp.received_at AS event_at,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:668:                JOIN expert_contact ec2 ON ec2.id = imp.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:669:               WHERE imp.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:670:                 AND (:expertContactId IS NULL OR imp.expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:683:          JOIN expert_contact ec ON u.expert_contact_id = ec.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:684:         GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:686:         ORDER BY latest_event_at DESC, u.expert_contact_id DESC
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:707:        SELECT COUNT(DISTINCT u.expert_contact_id)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:709:              SELECT mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:711:                JOIN expert_contact ec1 ON ec1.id = mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:713:                 AND mr.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:714:                 AND (:expertContactId IS NULL OR mr.expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:732:              SELECT imp.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:734:                JOIN expert_contact ec2 ON ec2.id = imp.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:735:               WHERE imp.expert_contact_id IS NOT NULL
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:736:                 AND (:expertContactId IS NULL OR imp.expert_contact_id = :expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:768:                 mr.id AS id, mr.expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:785:            JOIN expert_contact ec1 ON ec1.id = mr.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:787:             AND mr.expert_contact_id IN (:expertContactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:806:                 imp.id AS id, imp.expert_contact_id,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:827:            JOIN expert_contact ec2 ON ec2.id = imp.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:828:           WHERE imp.expert_contact_id IN (:expertContactIds)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:841:        ORDER BY u.expert_contact_id DESC, COALESCE(u.sent_at, u.received_at) DESC, u.id DESC
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:875:        WHERE expert_contact_id = :contactId
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:18:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertRepliedDismissalService.kt:21:            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertRepliedDismissalService.kt:27:            "SELECT MAX(id) FROM inbound_mail_processing WHERE expert_contact_id = :contactId",
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertRepliedDismissalService.kt:36:                (username, expert_contact_id, last_inbound_id, dismissed_at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:4:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:44:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:8:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:64:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:44:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BounceController.kt:21:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:28:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt:16: * 拒绝。expert_contact 必须真实存在（JDBC 存在性校验；未知 contact → 404）。
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt:21: * 不使用 toggle。关注不写 ES / expert_contact / 来信处理状态。
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt:30:            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt:40:                INSERT IGNORE INTO expert_follow (username, expert_contact_id, created_at)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt:52:                 WHERE username = :username AND expert_contact_id = :contactId
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:39:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:5:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:24:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:3:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:26:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:6:import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:22:    private val expertContactRepository: ExpertContactRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:45:            // I-1：成员资格由 MANUAL_REVIEW + expert_contact_id IS NULL 共同决定。

[exit_code=0]
```

## contact-country-writers

```sh
rg --no-ignore -n "updateCountry|country = expert.country" src/main/kotlin
```

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:75:            expertContactRepository.updateCountryById(contactId, country)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:89:                    country = expert.country,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:923:                            country = expert.country,
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:429:                country = expert.country,
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:68:    fun updateCountryById(id: Long, country: String?): Int

[exit_code=0]
```

## no-existing-location-store

```sh
rg --no-ignore -n "expert_contact_location" src/main scripts tools
```

```text

[exit_code=1]
```

## country-zone-map

```sh
rg --no-ignore -n "countryCode|country_code|defaultZoneId" src/main/kotlin/com/weibo/talentintroduction/mail src/main/resources/meeting-timezones-zh.properties
```

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt:74:    val defaultZoneId: String
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:59:            defaultZoneId = MeetingConfirmationDomain.DEFAULT_ZONE_ID

[exit_code=0]
```

## header-all-sites

```sh
rg -n "mc-header-meta|mc-header\{" src/main/resources/static
```

```text
src/main/resources/static/mailbox-chat.css:34:.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
src/main/resources/static/mailbox-chat.css:69:@media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
src/main/resources/static/mailbox-chat.css:70:@media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
src/main/resources/static/mailbox-chat.css:102:.mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
src/main/resources/static/mailbox-chat.css:103:.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
src/main/resources/static/mailbox-chat.css:121:.mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
src/main/resources/static/mailbox-chat.css:150:@media(max-width:1100px){.mail-chat{grid-template-columns:275px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:15px}.mail-chat .mc-timeline-head{padding:10px 15px}.mail-chat .mc-message{width:96%}.mail-chat .mc-header .button{font-size:11px;padding:0 8px}}
src/main/resources/static/mailbox-chat.js:1802:            const meta = head.querySelector(".mc-header-meta");
src/main/resources/static/mailbox-chat.js:1853:                    const meta = body.querySelector(".mc-header-meta");
src/main/resources/static/mailbox-chat.js:2283:                    <div class="mc-header-meta"></div>
src/main/resources/static/mailbox-chat.js:3180:                    const meta = body.querySelector(".mc-header-meta");
src/main/resources/static/mailbox-chat.js:3400:            const meta = conversationBody() ? conversationBody().querySelector(".mc-header-meta") : null;

[exit_code=0]
```

## cache-keys

```sh
rg -n "\?v=" src/main/resources/static/index.html
```

```text
11:    <link rel="stylesheet" href="styles.css?v=20261001-email-verification-allowlist">
12:    <link rel="stylesheet" href="expert-materials.css?v=20261001-email-verification-allowlist">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20261001-email-verification-allowlist">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20261001-email-verification-allowlist">
15:    <link rel="stylesheet" href="world-clock.css?v=20261001-email-verification-allowlist">
2345:<script src="trust-reply-workbench.js?v=20261001-email-verification-allowlist"></script>
2346:<script src="expert-materials.js?v=20261001-email-verification-allowlist"></script>
2347:<script src="meeting-confirmation.js?v=20261001-email-verification-allowlist"></script>
2348:<script src="mailbox-chat.js?v=20261001-email-verification-allowlist"></script>
2349:<script src="app.js?v=20261001-email-verification-allowlist"></script>
2350:<script src="world-clock.js?v=20261001-email-verification-allowlist"></script>

[exit_code=0]
```

## old-cache-test-literal

```sh
rg -n "20260930-manual-template-reference" src/test
```

```text

[exit_code=1]
```

## migration-order

```sh
rg --files src/main/resources/db/migration | sort -V | tail -5
```

```text
src/main/resources/db/migration/V141__create_mail_open_tracking.sql
src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql
src/main/resources/db/migration/V143__create_expert_replied_dismissal.sql
src/main/resources/db/migration/V144__create_discovery_schedule_setting.sql
src/main/resources/db/migration/V145__add_batch_email_verification_allowed_states.sql

[exit_code=0]
```

## auth-and-time

```sh
rg -n "TimeZone.setDefault|systemDefault|/api/\*\*" src/main/kotlin/com/weibo/talentintroduction/config/TimeZoneConfig.kt src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt
```

```text
src/main/kotlin/com/weibo/talentintroduction/config/TimeZoneConfig.kt:11:        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt:25:            .addPathPatterns("/api/**")
src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt:341:                ?.atZone(ZoneId.systemDefault())

[exit_code=0]
```
