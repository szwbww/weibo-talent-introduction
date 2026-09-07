# 代码检索证据快照

日期：2026-09-07。仅记录本地代码，不代表线上部署版本。以下为原始命中全集；读写语义见 audit.md。执行前须重新检索，不能照搬行号。

## 附件与专家材料

检索表达式：`mailAttachmentRepository|expertDocumentRepository|mail_attachment|expert_document`

```text
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:1:CREATE TABLE mail_attachment (
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:9:    KEY idx_mail_attachment_record (mail_record_id, created_at),
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:10:    CONSTRAINT fk_mail_attachment_record
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:14:CREATE TABLE expert_document (
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:17:    mail_attachment_id BIGINT NOT NULL,
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:23:    KEY idx_expert_document_contact (expert_contact_id, document_type, document_status),
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:24:    CONSTRAINT fk_expert_document_contact
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:26:    CONSTRAINT fk_expert_document_attachment
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:27:        FOREIGN KEY (mail_attachment_id) REFERENCES mail_attachment(id)
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:1:ALTER TABLE mail_attachment
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:4:ALTER TABLE mail_attachment
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:7:ALTER TABLE mail_attachment
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:8:    ADD KEY idx_mail_attachment_inbound (inbound_processing_id, created_at);
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:10:ALTER TABLE mail_attachment
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:11:    ADD CONSTRAINT fk_mail_attachment_inbound
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:14:ALTER TABLE mail_attachment
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:15:    ADD CONSTRAINT chk_mail_attachment_owner
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:35:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:43:        mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:124:        Mockito.`when`(mailAttachmentRepository.findAll()).thenReturn(attachments)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:341:        // 每个 repository 的全部写方法必须从未被调用（I-1：不得写入 expert_contact / mail_record / mail_attachment / bounce_record / operator_action_log）
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:344:        mailAttachmentRepository.verifyNoWrites()
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:356:        Mockito.verify(mailAttachmentRepository).findAll()
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:362:            expertContactRepository, mailRecordRepository, mailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:28: * 从事件（mail_record / bounce_record / mail_attachment）反推每位联系人的**期望** operator_status
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:44:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:56:        val attachments = mailAttachmentRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:170:     *   附件经 MailAttachmentService.saveInboundAttachments 以 mailRecordId 落 mail_attachment）
src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertDocument.kt:7:@Table("expert_document")
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:35:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:180:            mailAttachmentRepository.findById(attachmentId).orElse(null)?.fileName
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:39:    private val expertDocumentRepository: ExpertDocumentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:40:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:44:        val documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:47:            val attachment = mailAttachmentRepository.findById(doc.mailAttachmentId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:102:        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:108:        val attachment = mailAttachmentRepository.findById(attachmentId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:32:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:33:    private val expertDocumentRepository: ExpertDocumentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:74:            .flatMap(mailAttachmentRepository::findAllByMailRecordIdOrderByCreatedAtAsc)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:81:            documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:26:    private val expertDocumentRepository: ExpertDocumentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:27:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:45:        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:51:        val attachment = mailAttachmentRepository.findById(attachmentId)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:31:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:32:    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:44:        mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:45:        expertDocumentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:30:    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:31:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:44:            expertDocumentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:45:            mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:52:        Mockito.reset(expertDocumentRepository, mailAttachmentRepository, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:105:        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:107:        Mockito.`when`(mailAttachmentRepository.findById(1L))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:135:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:136:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:151:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:152:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:167:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:168:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:183:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:184:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:199:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:200:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:215:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:216:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:234:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:235:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:251:        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:253:        Mockito.`when`(mailAttachmentRepository.findById(1L))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:276:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:277:        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:292:        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:294:        Mockito.`when`(mailAttachmentRepository.findById(1L))
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:43:    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:44:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:53:            expertDocumentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:54:            mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:61:        Mockito.reset(expertDocumentRepository, mailAttachmentRepository, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:124:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:148:        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:158:        Mockito.`when`(mailAttachmentRepository.findById(attachmentId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:27:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:38:            mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:50:            mailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt:7:@Table("mail_attachment")
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:20:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:21:    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:27:            mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:28:            expertDocumentRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:30:        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:35:        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:52:        Mockito.verify(mailAttachmentRepository).save(attachmentCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:58:        Mockito.verify(expertDocumentRepository).save(documentCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:67:            mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:68:            expertDocumentRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:70:        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:89:        Mockito.verify(mailAttachmentRepository).save(attachmentCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt:94:        Mockito.verify(expertDocumentRepository, Mockito.never()).save(Mockito.any(ExpertDocument::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:31:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:38:        mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:235:        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:253:        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:268:        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt:28:    private val mailAttachmentRepository: MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt:34:        val attachment = mailAttachmentRepository.findById(attachmentId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:45:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:56:        val attachmentIntentIgnored = mailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:19:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:20:    private val expertDocumentRepository: ExpertDocumentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:40:            val mailAttachment = mailAttachmentRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:52:            expertDocumentRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:81:            mailAttachmentRepository.save(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:28:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:35:        mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:472:        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(5L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:557:        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(12L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:600:        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(12L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:17:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:24:            mailAttachmentRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:57:            mailAttachmentRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:59:        Mockito.`when`(mailAttachmentRepository.findById(7L)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:86:            mailAttachmentRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt:88:        Mockito.`when`(mailAttachmentRepository.findById(8L)).thenReturn(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:26:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:203:     * LEFT JOIN expert_contact / EXISTS(mail_attachment)）。
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:209:            mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(recordId).isNotEmpty()
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:280:            "MAIL_RECORD" -> mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:282:                val byInbound = mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:292:                mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(mailRecordId)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:35:    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:48:        mailAttachmentRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:77:        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(processingId))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:448:        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(processingId))
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:498:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:529:                   SELECT 1 FROM mail_attachment ma
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:766:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:805:                   SELECT 1 FROM mail_attachment ma

```

## 来信/邮件与游标

检索表达式：`inboundMailProcessingRepository|mailRecordRepository|mailInboxCursorRepository|inbound_mail_processing|mail_inbox_cursor|source_inbound_id`

```text
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt:84:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt:85:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:32:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:46:            mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:52:        Mockito.reset(expertDocumentRepository, mailAttachmentRepository, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:109:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:137:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:153:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:169:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:185:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:201:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 999))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:217:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 999))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:236:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:255:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:278:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt:296:        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:45:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:55:            mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:61:        Mockito.reset(expertDocumentRepository, mailAttachmentRepository, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt:172:        Mockito.`when`(mailRecordRepository.findByIdOrNull(500L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:107:    private lateinit var mailRecordRepository: MailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:116:    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:217:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:293:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:354:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:402:        Mockito.`when`(mailRecordRepository.findById(77L)).thenReturn(Optional.of(exactMail))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:404:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:424:        Mockito.verify(mailRecordRepository, Mockito.never()).findLatestInboundByExpertContactId(Mockito.anyLong())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:425:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:430:        Mockito.`when`(mailRecordRepository.findById(999L)).thenReturn(Optional.empty())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:457:        Mockito.`when`(mailRecordRepository.findById(55L)).thenReturn(Optional.of(outbound))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:470:        Mockito.`when`(mailRecordRepository.findById(66L)).thenReturn(Optional.of(mail))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:499:        Mockito.verify(mailRecordRepository, Mockito.times(1)).findLatestInboundByExpertContactId(10L)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:500:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:531:            mailRecordRepository.findInboundMailsForSimulation(true, listOf(-1L), null, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:534:            mailRecordRepository.countInboundMailsForSimulation(true, listOf(-1L), null, null)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:537:        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:547:        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(true, listOf(-1L), null, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:555:            mailRecordRepository.findInboundMailsForSimulation(true, listOf(-1L), 3L, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:558:            mailRecordRepository.countInboundMailsForSimulation(true, listOf(-1L), 3L, null)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:561:        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:568:        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(true, listOf(-1L), 3L, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:584:            mailRecordRepository.findInboundMailsForSimulation(false, listOf(10L), null, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:587:            mailRecordRepository.countInboundMailsForSimulation(false, listOf(10L), null, null)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:596:        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:603:        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(false, listOf(10L), null, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:740:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 50))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:744:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(2L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:746:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(1L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:756:        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail(null, 50)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:762:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail("alice", 50))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:765:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(5L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:774:        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail("alice", 50)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:780:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 50))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:783:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:794:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(10L)).thenReturn(inbound)
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:795:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:853:        Mockito.`when`(mailRecordRepository.findById(77L)).thenReturn(Optional.of(inbound))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:855:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:921:        Mockito.`when`(mailRecordRepository.findById(88L)).thenReturn(Optional.of(current))
src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:923:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplySuggestedInstructionTest.kt:92:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplySuggestedInstructionTest.kt:93:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:770:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:771:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:1755:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:1756:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:1990:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:1991:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:2395:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:2396:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:2614:            mailRecordRepository = mailRecords,
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt:2615:            inboundMailProcessingRepository = inboundProcessing,
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:23:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:44:            mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:73:        Mockito.verifyNoInteractions(mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:80:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:82:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:102:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:104:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:164:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:172:        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail(null, 1)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:173:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:178:        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 3))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:181:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:190:        val inOrder = Mockito.inOrder(mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:191:        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(30L)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:192:        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(20L)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:193:        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(10L)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:240:    private lateinit var mailRecordRepository: MailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:260:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, 10)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt:282:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, 10)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt:34:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt:1468:            mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:26:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:28:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:38:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:40:        inboundMailProcessingRepository = inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:69:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:80:        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:108:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:138:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:173:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:203:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:215:        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:229:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:232:        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:250:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:255:        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:268:        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt:271:        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:40:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:42:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:74:            introductions = mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:75:            inboundReplies = mailRecordRepository.countInboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:76:            repliedExperts = mailRecordRepository.countDistinctRepliedExpertsBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:77:            autoReplies = mailRecordRepository.countAutoRepliesBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:78:            operatorOutbound = mailRecordRepository.countOperatorOutboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:79:            meetingInvitations = mailRecordRepository.countOutboundByMailTypeBetween("MEETING_INVITATION", start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:80:            manualReviewInbound = inboundMailProcessingRepository.countManualReviewBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:81:            unmatchedInbound = inboundMailProcessingRepository.countUnmatchedBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:82:            failedOutbound = mailRecordRepository.countFailedOutboundBetween(start, end),
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:95:        val records = mailRecordRepository.listIntroductions(start, end, senderAccountCode, pageSize.safeLimit(), pageOffset.safeOffset())
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:116:            totalCount = mailRecordRepository.countIntroductions(start, end, senderAccountCode)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:131:        val records = mailRecordRepository.listOutboundReplies(
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:137:            ?.let { mailRecordRepository.findAllById(it).associateBy { record -> record.id } }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:172:            totalCount = mailRecordRepository.countOutboundReplies(start, end, triggeredBy, mailType, senderAccountCode, sendStatus)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:185:        val records = inboundMailProcessingRepository.listInboundActivity(
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:214:            totalCount = inboundMailProcessingRepository.countInboundActivity(start, end, processStatus, reasonType)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:245:        val stats = mailRecordRepository.aggregateSenderAccountStats(from, to).associateBy { it.senderAccountCode }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:246:        val lastReceived = inboundMailProcessingRepository.findLastReceivedAtPerAccount()
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:272:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:289:        mailRecordRepository.aggregateIntroCohortByDomain(start, end, matureBefore).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:298:        mailRecordRepository.aggregateUndeliveredByDomain(start, end).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:326:        val countryRows = mailRecordRepository.aggregateIntroCohortByCountry(start, end, matureBefore)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:72:    private lateinit var mailRecordRepository: MailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:85:        jdbcTemplate.execute("DELETE FROM inbound_mail_processing")
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:110:        assertEquals(2L, mailRecordRepository.countFailedOutboundBetween(todayStart, todayEnd))
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:111:        assertEquals(1L, mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", todayStart, todayEnd))
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:134:        val stats = mailRecordRepository.aggregateSenderAccountStats(todayStart, todayEnd)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:146:        jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:147:        jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:163:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:173:        val rows = mailRecordRepository.listMailbox(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:197:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:222:        assertEquals(1L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:234:        val allSummary = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:242:        val pendingMails = mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:260:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:272:        assertEquals(2L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:284:        val page0 = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:297:        val page1 = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:317:        val mails = mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:341:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:353:        assertEquals(1L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:365:        val summaries = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:382:        val mails = mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:405:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:433:        assertEquals(1L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:439:        val expertSummary = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:451:        val mails = mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:462:        assertEquals(1L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:467:        val otherSummary = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:478:        assertEquals(2L, mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:482:        val allSummaries = mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:521:        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:571:        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:621:        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:667:        val byCountry = mailRecordRepository.aggregateIntroCohortByCountry(from, to, matureBefore)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:669:        val byDomain = mailRecordRepository.aggregateIntroCohortByDomain(from, to, matureBefore)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:708:        val byDomain = mailRecordRepository.aggregateUndeliveredByDomain(todayStart, todayEnd)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:736:        val byDomain = mailRecordRepository.aggregateUndeliveredByDomain(todayStart, todayEnd)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:760:        val byDomain = mailRecordRepository.aggregateUndeliveredByDomain(todayStart, todayEnd)
src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt:785:        val byDomain = mailRecordRepository.aggregateUndeliveredByDomain(todayStart, todayEnd)
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:89:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:95:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:128:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:134:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:267:        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:269:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:54:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:56:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:116:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:118:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:154:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:258:        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:260:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any(MailRecord::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:317:        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:380:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:382:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:422:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:431:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any(MailRecord::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:432:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:454:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:456:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:475:        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:499:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:501:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:551:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:553:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:577:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:588:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:598:        Mockito.verify(inboundMailProcessingRepository).save(captor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:620:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:647:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:653:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:686:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:688:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:748:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:750:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:795:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:797:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:838:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:840:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:874:            inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid("sender", received.imapUid)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:906:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:908:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:992:        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1129:        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1156:        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1179:        Mockito.verify(inboundMailProcessingRepository).save(inboundCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1192:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1255:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1270:        Mockito.verify(inboundMailProcessingRepository).save(inboundCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1301:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1331:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1338:            mailRecordRepository.findRecentDuplicateInbound(
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1423:        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1494:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1540:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1542:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1586:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1637:        Mockito.`when`(inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid("sender", uid))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1768:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt:1770:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:34:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:42:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:123:        Mockito.`when`(mailRecordRepository.findAll()).thenReturn(records)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:343:        mailRecordRepository.verifyNoWrites()
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:355:        Mockito.verify(mailRecordRepository).findAll()
src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt:362:            expertContactRepository, mailRecordRepository, mailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:18:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:59:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:110:        mailRecordRepository.save(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:72:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:104:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:196:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:217:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:257:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:368:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:398:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:434:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:468:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:527:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(listOf(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:551:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:573:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:595:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:620:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:659:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:734:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:764:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:800:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:832:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:858:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:902:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:939:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:966:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1018:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1058:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1100:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1146:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(listOf(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1175:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1286:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1287:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1315:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1338:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1428:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1504:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(cid))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1585:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1610:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1668:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1852:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1900:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1948:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1997:            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2032:                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2085:                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2143:                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2196:                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2250:                    Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2958:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:2984:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3005:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3037:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3065:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:3199:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4146:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4156:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:4251:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt:41:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt:58:            mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt:98:            mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt:350:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!))
src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt:430:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:21:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:28:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:78:        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:98:        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:148:        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:170:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:183:        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:205:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:209:        Mockito.verify(mailRecordRepository).save(Mockito.any(MailRecord::class.java))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:239:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:253:        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:259:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt:272:        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:28:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:32:        attemptRepository, mailRecordRepository, mailRecordQaRuleRepository, operatorActionLogService
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:197:        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:198:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:215:        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:216:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:244:        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:245:        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:43:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:55:        val mailRecords = mailRecordRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt:7:@Table("inbound_mail_processing")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:26:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:144:        mailRecordRepository.save(
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:31:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:43:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:146:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:198:        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(1L)).thenReturn(anchor)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:202:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:320:        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:511:        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:634:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:660:        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:806:        Mockito.verify(mailRecordRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:829:        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:843:        Mockito.verify(mailRecordRepository, Mockito.times(2)).save(secondRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:950:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt:1005:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:68:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:969:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1146:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:29:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:42:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:111:        Mockito.`when`(mailRecordRepository.findByMessageId(prefixed)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:112:        Mockito.`when`(mailRecordRepository.findByMessageId(stored)).thenReturn(outboundRecord)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt:174:        Mockito.`when`(mailRecordRepository.findByMessageId("orig-hard@example.com")).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:25:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:27:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:32:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:34:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:72:        Mockito.verifyNoInteractions(mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:94:        Mockito.verifyNoMoreInteractions(mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:125:            mailRecordRepository.listMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:140:            mailRecordRepository.countMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:222:            mailRecordRepository.listMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:236:            mailRecordRepository.countMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:387:            mailRecordRepository.listMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:401:            mailRecordRepository.countMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:425:        Mockito.verify(mailRecordRepository).listMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:470:        Mockito.`when`(mailRecordRepository.findByIdOrNull(5L)).thenReturn(record)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:517:        Mockito.`when`(inboundMailProcessingRepository.findById(12L)).thenReturn(Optional.of(inbound))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:519:        Mockito.`when`(mailRecordRepository.findFirstByMessageIdOrderByCreatedAtDesc("in-msg")).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:599:        Mockito.`when`(inboundMailProcessingRepository.findById(12L)).thenReturn(Optional.of(inbound))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:622:        Mockito.verifyNoInteractions(mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:630:            mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:644:        Mockito.verify(mailRecordRepository, never()).listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:655:            mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:674:            mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:698:            mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:720:            mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:742:            mailRecordRepository.listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:766:            mailRecordRepository.listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:779:        Mockito.verify(mailRecordRepository).countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:784:        Mockito.verify(mailRecordRepository).listMailboxExpertSummaries(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:789:        Mockito.verify(mailRecordRepository).listMailboxByExpertContactIds(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:810:            mailRecordRepository.countMailboxExperts(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt:822:        Mockito.verify(mailRecordRepository).countMailboxExperts(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:30:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:38:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:71:        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:75:        val latestManualMailProcessing = inboundMailProcessingRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:16:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:17:    private val service = BatchAutoMailReplyService(accountService, autoReplyService, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:153:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:170:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:187:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:201:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:213:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:226:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:244:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt:257:        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:24:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:27:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:33:        inboundMailProcessingRepository = inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:36:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:63:            inboundMailProcessingRepository.findManualReviewQueue(null, null, null, 20, 0)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:66:            inboundMailProcessingRepository.countManualReviewQueue(null, null, null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:71:            inboundMailProcessingRepository.countManualReviewByAccounts(listOf("acc1"))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:74:            inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(listOf("acc1"))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:92:        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:98:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:114:        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:131:        Mockito.`when`(mailRecordRepository.findByMessageId("out-msg-1")).thenReturn(outboundRecord)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:152:        Mockito.`when`(mailRecordRepository.findByMessageId(prefixed)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:153:        Mockito.`when`(mailRecordRepository.findByMessageId(stored)).thenReturn(outboundRecord)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:162:        Mockito.verify(mailRecordRepository).findByMessageId(prefixed)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:163:        Mockito.verify(mailRecordRepository).findByMessageId(stored)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:172:        Mockito.verify(mailRecordRepository, Mockito.never()).findByMessageId(Mockito.anyString())
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:182:        Mockito.`when`(mailRecordRepository.findByMessageId(Mockito.anyString())).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:202:        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:208:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:216:        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:226:        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:227:        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:229:        Mockito.`when`(inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW"))
src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt:248:        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailInboxCursor.kt:7:@Table("mail_inbox_cursor")
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:55:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:61:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:90:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:96:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:127:        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:132:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:28:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:30:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:35:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:37:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:192:            mailRecordRepository.listMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:206:            mailRecordRepository.countMailbox(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:232:        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(13023L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:250:        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(999L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxTaskExecutionFilterTest.kt:265:        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(13023L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:68:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:74:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:103:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:109:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:151:        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:156:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1419:        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2279:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2282:            inboundMailProcessingRepository.reopenManualResolved(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2328:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2331:            inboundMailProcessingRepository.reopenManualResolved(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2361:            Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2369:            inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2377:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2380:            inboundMailProcessingRepository.reopenManualResolved(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2394:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2402:            inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2410:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2413:            inboundMailProcessingRepository.reopenManualResolved(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2436:        Mockito.`when`(inboundMailProcessingRepository.findById(100L))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2441:            inboundMailProcessingRepository.reopenManualResolved(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:29:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:36:    private val inboundMailProcessingRepository = Mockito.mock(com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:42:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt:50:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt:36:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt:48:        mailRecordRepository = mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt:196:        Mockito.verify(mailRecordRepository).save(mailRecordCaptor.capture())
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:460:        val mailRecordRepository: MailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:682:        val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:687:            expertContactRepository, Mockito.mock(CampaignRepository::class.java), mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:701:        return ManualOutreachHarness(service, expertSearchService, expertContactRepository, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt:713:            Mockito.`when`(outreach.mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(c.id!!)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:27:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:33:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:40:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:46:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:304:        Mockito.`when`(inboundMailProcessingRepository.findById(processingId))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:479:        Mockito.`when`(inboundMailProcessingRepository.findById(processingId))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:493:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt:501:            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt:12:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt:22:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt:40:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt:57:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt:163:        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt:305:        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:20:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:25:        mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:44:        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(1)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:55:        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(2)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:65:        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(3)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:94:        Mockito.verifyNoInteractions(expertIndexWriterService, mailRecordRepository)
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt:99:        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(3)
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:28:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt:57:        val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt:15:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt:18:        mailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt:29:            mailRecordRepository.countSentByAccountSince(
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:41:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:53:            val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:114:        val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:33:    private val mailRecordRepository: MailRecordRepository? = null
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt:123:                val count = mailRecordRepository?.countByTaskExecutionId(executionId)?.toInt() ?: 0
src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt:39:                "Docker is required for inbound_mail_processing repository tests"
src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt:68:        jdbc.execute("DELETE FROM inbound_mail_processing")
src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt:79:            INSERT INTO inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt:89:            "SELECT id FROM inbound_mail_processing WHERE sender_account_code = ? AND imap_uid = ?",
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:35:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:50:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:56:                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt:28:    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository
src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt:37:    private lateinit var mailRecordRepository: MailRecordRepository
src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt:45:            inboundMailProcessingRepository.listInboundSummary(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt:72:            inboundMailProcessingRepository.countInboundSummary(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:77:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:109:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:149:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:219:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:313:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:384:        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:809:        mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:833:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1517:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1576:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1621:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1658:            mailRecordRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1691:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1738:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1786:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1833:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1912:        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:601:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:602:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2356:        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2373:        val inbound = inboundMailProcessingRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2401:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:49:    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:65:        mailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:55:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:58:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:121:        val records = mailRecordRepository.findInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:129:        val total = mailRecordRepository.countInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:177:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:184:            val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:197:            val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:211:            val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:218:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:410:                ?: inboundMailProcessingRepository.findAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:21:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:24:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:36:        val records = inboundMailProcessingRepository.findManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:43:        val totalCount = inboundMailProcessingRepository.countManualReviewQueue(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:54:            val total = inboundMailProcessingRepository.countManualReviewByAccounts(activeCodes)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:55:            val grouped = inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(activeCodes)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:68:        val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:79:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:141:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:176:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:212:        val record = inboundMailProcessingRepository.findById(recordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:216:        val saved = inboundMailProcessingRepository.save(record.copy(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:227:            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW")
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:50:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:214:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:61:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:67:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:110:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:130:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:166:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:549:                val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:592:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:608:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:729:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1060:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1066:        inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1079:            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1118:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1137:        val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1198:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:23:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:25:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:53:        val rows = mailRecordRepository.listMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:65:        val total = mailRecordRepository.countMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:109:        val total = mailRecordRepository.countMailboxExperts(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:125:        val summaries = mailRecordRepository.listMailboxExpertSummaries(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:144:        val mailRows = mailRecordRepository.listMailboxByExpertContactIds(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:192:        val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:265:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:270:                val record = inboundMailProcessingRepository.findById(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:286:                val inbound = inboundMailProcessingRepository.findById(id).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:289:                    mailRecordRepository.findFirstByMessageIdOrderByCreatedAtDesc(it)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:21:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:34:        val replyCount = mailRecordRepository.countInboundReplies(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:78:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:361:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:39:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt:242:        if (!inboundMailProcessingRepository.existsById(inboundProcessingId)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt:13:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt:40:        val accountCodes = mailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:26:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:29:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:40:        val records = inboundMailProcessingRepository.listInboundSummary(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:48:        val totalCount = inboundMailProcessingRepository.countInboundSummary(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:83:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:87:            inboundMailProcessingRepository.findAllByExpertContactId(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:98:            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:149:        val inbound = inboundMailProcessingRepository.findById(inboundId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:10:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:23:        val total = inboundMailProcessingRepository.countAll()
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt:25:            val batch = inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(batchSize, offset)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:25:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:69:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:239:            contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:27:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:215:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:249:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:293:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:328:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:23:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:144:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:37:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:43:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:50:        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:182:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:189:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:46:        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt:69:        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:37:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:39:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:77:        if (inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid(accountCode, received.imapUid) != null) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:233:        val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:267:        val inboundMailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:614:        val outboundRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:804:    ): MailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:842:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:849:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1002:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1056:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1105:        val saved = inboundMailProcessingRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:84:                    JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:89:                    JOIN inbound_mail_processing p2 ON p2.id = t2.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:118:                    JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:123:                    JOIN inbound_mail_processing p2 ON p2.id = t2.inbound_processing_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:534:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:581:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:651:                FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:717:                FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:810:            FROM inbound_mail_processing imp
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagProcessContextResolver.kt:26:    private val mailRecordRepository: MailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagProcessContextResolver.kt:45:        val replyCount = mailRecordRepository
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:43:    private val mailRecordRepository: MailRecordRepository,
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:44:    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:78:        val mail = mailRecordRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:95:    /** 线上来信：inbound_mail_processing 行；必须已绑定联系人。 */
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:97:        val inbound = inboundMailProcessingRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:101:                "inbound_mail_processing $sourceId does not exist"
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:107:            "inbound_mail_processing $sourceId has no expert contact"
src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql:3:-- The MANUAL_REVIEW value used by inbound_mail_processing.process_status is a
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:36:-- 2. inbound_mail_processing 增加 reason_type 列，给前端按原因过滤用
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:37:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql:43:UPDATE inbound_mail_processing
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:13:    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:20:        mailRecordRepository = Mockito.mock(com.weibo.talentintroduction.mail.repository.MailRecordRepository::class.java),
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:26:        inboundMailProcessingRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:56:        Mockito.`when`(inboundMailProcessingRepository.countAll()).thenReturn(5L)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:57:        Mockito.`when`(inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(200, 0)).thenReturn(rows)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:81:        Mockito.`when`(inboundMailProcessingRepository.countAll()).thenReturn(1L)
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:82:        Mockito.`when`(inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(200, 0))
src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt:90:        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:24:        UPDATE inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:61:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:78:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:92:        FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:99:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:107:        FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:116:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:125:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:134:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:153:        SELECT COUNT(*) FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:169:          FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:178:        SELECT * FROM inbound_mail_processing
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:185:    @Query("SELECT COUNT(*) FROM inbound_mail_processing")
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:190:        SELECT p.* FROM inbound_mail_processing p
src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:216:        SELECT COUNT(*) FROM inbound_mail_processing p
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:7:    ADD COLUMN source_inbound_id BIGINT NULL
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:15:    ADD INDEX idx_mail_record_source_inbound (source_inbound_id);
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:18:ALTER TABLE inbound_mail_processing
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:23:UPDATE inbound_mail_processing
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:32:    source_inbound_id BIGINT NULL COMMENT '触发本次晋级的 INBOUND mail_record.id',
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:48:    (expert_contact_id, orcid_id, source_inbound_id, triggered_by, promotion_status,
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:12:        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id);
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
src/main/resources/db/migration/V49__create_mail_inbox_cursor.sql:1:CREATE TABLE mail_inbox_cursor (
src/main/resources/db/migration/V49__create_mail_inbox_cursor.sql:7:    UNIQUE KEY uk_mail_inbox_cursor_account (sender_account_code)
src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql:52:        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id)
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:1:CREATE TABLE inbound_mail_processing (
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:16:    UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid),
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:17:    KEY idx_inbound_mail_processing_status (process_status, received_at),
src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:18:    CONSTRAINT fk_inbound_mail_processing_contact

```

## 任务进度

检索表达式：`progressStore\.|taskProgressLogRepository|task_progress_log`

```text
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt:46:        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId))).thenReturn(false)
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt:159:        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId)))
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt:241:        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId)))
src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt:61:            progressStore.tryStartWithToken(
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt:78:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_REVALIDATION"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt:123:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("RAW_PROMOTION_SCAN"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt:159:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_REVALIDATION"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:154:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:175:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:199:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:215:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:238:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt:347:        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt:66:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt:81:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt:90:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt:99:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt:117:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:77:        val (started, pendingToken) = progressStore.tryStartWithToken(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:96:                            progressStore.bindExecutionId(ExpertClassificationBackfillService.TASK_TYPE, pendingToken, id)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:103:                    progressStore.update(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:118:                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, execId)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:120:                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:126:            progressStore.update(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt:138:            progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:490:        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L))).thenReturn(true)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1540:        Mockito.`when`(progressStore.update(
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:286:        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_REVALIDATION"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:308:        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_REVALIDATION"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt:19:        Path.of("src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql")
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt:29:    fun `V102 creates the created_at index on task_progress_log`() {
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt:31:            v102Sql.contains("CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at)"),
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt:32:            "V102 must create idx_tpl_created_at on task_progress_log(created_at)"
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:41:        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:57:        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:66:        Mockito.`when`(progressStore.requestCancel("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:76:        Mockito.`when`(progressStore.requestCancel("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:116:        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:133:        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:147:        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:33:        val execId = progressStore.getCurrentExecutionId(taskType)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:37:                if (progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:93:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:106:            if (progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:109:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:121:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:128:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:142:        val execId = progressStore.getCurrentExecutionId(taskType)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:148:                if (progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:216:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:228:            if (progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:231:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:240:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt:247:            progressStore.update(taskType, TaskProgress(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:69:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(999L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:71:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE))
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:79:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:87:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:88:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:112:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:113:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:125:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:126:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:138:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:139:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:177:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:188:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:189:        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(false)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:199:        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt:200:        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(true)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:198:        executionId != null && progressStore.isCancelled(ExpertClassificationBackfillService.TASK_TYPE, executionId)
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt:223:        progressStore.update(
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt:230:        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(true)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt:494:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(progress)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt:515:        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(null)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:68:        val (started, pendingToken) = progressStore.tryStartWithToken(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:90:                            progressStore.bindExecutionId(ExpertClassificationBackfillService.TASK_TYPE, pendingToken, id)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:97:                    progressStore.update(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:112:                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, execId)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:114:                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:120:            progressStore.update(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt:132:            progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:56:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:90:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:114:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:130:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:154:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:175:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt:187:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:127:        val (started, token) = progressStore.tryStartWithToken("EXPERT_REVALIDATION", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:141:                    progressStore.bindExecutionId("EXPERT_REVALIDATION", token, id)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:148:            progressStore.update("EXPERT_REVALIDATION", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:157:                progressStore.clearExecutionContext("EXPERT_REVALIDATION", execId)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:159:                progressStore.clearExecutionContext("EXPERT_REVALIDATION", token)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:166:        val (started, token) = progressStore.tryStartWithToken("RAW_PROMOTION_SCAN", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:180:                    progressStore.bindExecutionId("RAW_PROMOTION_SCAN", token, id)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:187:            progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:196:                progressStore.clearExecutionContext("RAW_PROMOTION_SCAN", execId)
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:198:                progressStore.clearExecutionContext("RAW_PROMOTION_SCAN", token)
src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskProgressLog.kt:7:@Table("task_progress_log")
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt:78:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt:115:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt:128:        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:83:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:92:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:101:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:122:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:143:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:161:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:198:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:200:        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:265:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:274:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:293:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:310:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt:327:        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt:18: * ② 该 executionId 最新一条 `task_progress_log.detailsJson` + 该行 `processedCount` →
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt:130:     * 第 ② 级：读该 executionId 最新一条 `task_progress_log` 的 `detailsJson`。
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:48:        val progress = progressStore.get(taskType) ?: return ResponseEntity.noContent().build()
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:54:        if (!progressStore.requestCancel(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:68:            ?: progressStore.getCurrentExecutionId(taskType)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:13: * - I3-1：`task_progress_log` 按 `created_at` 删，无 JOIN / EXISTS / task_execution_id 关联
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:19: * - I3-4：先子表 `task_progress_log` 后主表 `task_execution`。
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:45:            log.warn("purge task_progress_log failed: {}", e.message)
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt:16:    @Query("UPDATE task_progress_log SET task_execution_id = :executionId WHERE task_execution_id = :pendingToken")
src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt:26:    @Query("DELETE FROM task_progress_log WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchSize")
src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql:4:CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at);
src/main/resources/db/migration/V22__create_task_progress_log.sql:1:CREATE TABLE task_progress_log (
src/main/resources/db/migration/V22__create_task_progress_log.sql:18:CREATE INDEX idx_tpl_task_type ON task_progress_log(task_type);
src/main/resources/db/migration/V22__create_task_progress_log.sql:19:CREATE INDEX idx_tpl_execution_id ON task_progress_log(task_execution_id);
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:233:        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:242:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:249:                progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:268:                if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:294:            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:296:                progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:313:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:325:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:344:        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:357:            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:405:                if (consumedInBatch % 10 == 0 && progressStore.isCancelled("EXPERT_DISCOVERY")) { limitReached = true; break }
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:438:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:485:        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:496:            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:584:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:867:        if (ms <= 0) return progressStore.isCancelled(taskType)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:870:            if (progressStore.isCancelled(taskType)) return true
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:875:        return progressStore.isCancelled(taskType)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:885:        val execId = progressStore.getCurrentExecutionId(taskType)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:894:        progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:901:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:920:                if (circuitBreakerTripped || progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:931:                    if (circuitBreakerTripped || progressStore.isCancelled(taskType)) break
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:937:                        if (circuitBreakerTripped || progressStore.isCancelled(taskType)) break
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:988:                        progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1013:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1032:                !progressStore.isCancelled(taskType) && !circuitBreakerTripped
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1035:            if (progressStore.isCancelled(taskType)) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1038:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1060:                progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1079:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1093:            progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1299:            if (progressStore.isCancelled("EXPERT_DISCOVERY")) return@scrollExperts false
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1304:                if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1313:                            if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1316:                                if (validEmail != null && !progressStore.isCancelled("EXPERT_DISCOVERY")) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1318:                                        if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
src/main/resources/db/migration/V35__add_task_progress_batch_reject_reasons.sql:1:ALTER TABLE task_progress_log
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:178:        if (progressStore.getCurrentExecutionId(TASK_TYPE) != executionId) return null
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:179:        val progress = progressStore.get(TASK_TYPE) ?: return null
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:199:        if (progressStore.getCurrentExecutionId(TASK_TYPE) != executionId) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:203:        val accepted = progressStore.requestCancel(TASK_TYPE)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:217:        progressStore.requestCancel(TASK_TYPE)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:280:        val progress = progressStore.get(TASK_TYPE)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:336:        val (started, pendingToken) = progressStore.tryStartWithToken(TASK_TYPE, initialProgress)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:358:                            progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:378:                    progressStore.update(TASK_TYPE, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:386:                        progressStore.clearExecutionContext(TASK_TYPE, execId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:388:                        progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:397:            progressStore.update(TASK_TYPE, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:402:            progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:72:        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:90:                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:103:                val existing = progressStore.get("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:104:                progressStore.update("EXPERT_DISCOVERY", existing?.copy(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:117:            val existing = progressStore.get("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:118:            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:130:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", execId)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:132:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", token)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:145:        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:168:                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:178:                val existing = progressStore.get("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:179:                progressStore.update("EXPERT_DISCOVERY", existing?.copy(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:192:            val existing = progressStore.get("EXPERT_DISCOVERY")
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:193:            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:205:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", execId)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:207:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", token)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:222:        val (started, pendingToken) = progressStore.tryStartWithToken(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:240:                            progressStore.bindExecutionId(taskType, pendingToken, id)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:246:                    progressStore.update(taskType, TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:255:                        progressStore.clearExecutionContext(taskType, execId)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:257:                        progressStore.clearExecutionContext(taskType, pendingToken)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:259:                    val remaining = progressStore.get(taskType)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:261:                        progressStore.clear(taskType)
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt:266:            progressStore.clear(taskType)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:207:            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:549:            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1132:        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1432:        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt:32:        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt:49:                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt:55:            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt:63:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", execId)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt:65:                progressStore.clearExecutionContext("EXPERT_DISCOVERY", token)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:135:        val (started, pendingToken) = progressStore.tryStartWithToken("CHECK_REPLIES", initialProgress)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:151:                            progressStore.bindExecutionId("CHECK_REPLIES", pendingToken, id)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:171:                            progressStore.update("CHECK_REPLIES", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:195:                                progressStore.isCancelled("CHECK_REPLIES", currentExecId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:197:                                progressStore.isCancelled("CHECK_REPLIES", pendingToken)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:221:                        progressStore.update("CHECK_REPLIES", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:243:                    progressStore.update("CHECK_REPLIES", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:253:                        progressStore.clearExecutionContext("CHECK_REPLIES", execId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:255:                        progressStore.clearExecutionContext("CHECK_REPLIES", pendingToken)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:260:            progressStore.update("CHECK_REPLIES", TaskProgress(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:265:            progressStore.clearExecutionContext("CHECK_REPLIES", pendingToken)

```

## 前端入口

检索表达式：`renderExpertDocuments|openAiAnalysisModal|startAiAnalysis|mountLiveTrustReply|renderMailbox|loadMailbox|manual-rich-reply|checkReplies|20260903-bounce-warning`

```text
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:243:            "Use trust workbench and manual-rich-reply"
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:246:    @PostMapping("/unmatched-inbound/{id}/manual-rich-reply")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:322:            "Use trust workbench and manual-rich-reply"
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:332:            "Use trust workbench and manual-rich-reply"
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt:101:    fun checkReplies(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:98:        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:116:        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:132:        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
src/main/resources/static/index.html:11:    <link rel="stylesheet" href="styles.css?v=20260903-bounce-warning">
src/main/resources/static/index.html:762:                        <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
src/main/resources/static/index.html:2107:<script src="trust-reply-workbench.js?v=20260903-bounce-warning"></script>
src/main/resources/static/index.html:2108:<script src="app.js?v=20260903-bounce-warning"></script>
src/test/js/mailboxDateDefault.test.js:75:        renderMailboxTable: () => {},
src/test/js/mailboxDateDefault.test.js:76:        renderMailboxPagination: () => {},
src/test/js/mailboxDateDefault.test.js:86:    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
src/test/js/mailboxDateDefault.test.js:87:    vm.runInContext(extractFn("loadMailbox"), sandbox);
src/test/js/mailboxDateDefault.test.js:103:            "loadMailbox should apply defaults whenever pending-only is off"
src/test/js/mailboxDateDefault.test.js:108:            "loadMailbox should mark date defaults applied whenever pending-only is off"
src/test/js/mailboxDateDefault.test.js:112:    it("first loadMailbox fills default date range", async () => {
src/test/js/mailboxDateDefault.test.js:121:        await sb.loadMailbox();
src/test/js/mailboxDateDefault.test.js:134:    it("cleared dates stay empty on subsequent loadMailbox calls", async () => {
src/test/js/mailboxDateDefault.test.js:143:        await sb.loadMailbox();
src/test/js/mailboxDateDefault.test.js:148:        await sb.loadMailbox();
src/test/js/mailboxDateDefault.test.js:168:        await sb.loadMailbox();
src/test/js/mailboxDateDefault.test.js:192:        await sb.loadMailbox();
src/test/js/mailboxDateDefault.test.js:196:        await sb.loadMailbox();
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:117:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:127:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = emptyList()))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:141:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:155:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 1L, 2L)))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:166:            controller.checkReplies(CheckRepliesRequest(contactIds = listOf(0L, 1L)))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:174:            controller.checkReplies(CheckRepliesRequest(contactIds = listOf(-1L)))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:183:            controller.checkReplies(CheckRepliesRequest(contactIds = (1L..501L).toList()))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:192:            controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 0))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:200:            controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 101))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:208:        val response = controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 1))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:217:        val response = controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 100))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:227:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = 15))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:243:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(5L), maxMessagesPerAccount = 10))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:257:        val response = controller.checkReplies(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:269:        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = null))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:464:    fun `checkReplies records COMPLETED state in progress store`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:481:        controller.checkReplies(CheckRepliesRequest(emptyList(), null))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:494:    fun `checkReplies records PARTIAL_SUCCESS state in progress store`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:511:        controller.checkReplies(CheckRepliesRequest(emptyList(), null))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:524:    fun `checkReplies records FAILED state in progress store`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:541:        controller.checkReplies(CheckRepliesRequest(emptyList(), null))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:554:    fun `checkReplies records CANCELLED state in progress store`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:571:        controller.checkReplies(CheckRepliesRequest(emptyList(), null))
src/test/js/taskModalStateMachine.test.js:1338:            const ctx = sandbox.createTaskModalContext("CHECK_REPLIES", "检查回复", "checkRepliesBtn", "PROGRESS");
src/test/js/expertProfileAbsence.test.js:293:        mountLiveTrustReply() {},
src/test/js/expertProfileAbsence.test.js:307:        renderExpertDocuments: () => "",
src/test/js/expertProfileAbsence.test.js:309:        renderMailboxInboundTagEditor: () => "",
src/test/js/expertProfileAbsence.test.js:342:    vm.runInContext(extractFunction("renderMailboxExpertTagEditor"), sandbox);
src/test/js/ragAdoptSendBridge.test.js:145:        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
src/test/js/ragAdoptSendBridge.test.js:146:        assert.ok(sendIdx > 0, "send-manual-rich-reply handler must exist");
src/test/js/unmatchedDetailResolvedAction.test.js:50:    const renderActions = extractFn("renderMailboxActions");
src/test/js/unmatchedDetailResolvedAction.test.js:51:    assert.ok(renderActions, "renderMailboxActions must exist");
src/test/js/unmatchedDetailResolvedAction.test.js:59:        return sandbox.renderMailboxActions(row);
src/test/js/batchEntryRelocation.test.js:35:            `<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies\\(\\)">检查回复</button>\\s*` +
src/test/js/taskDrilldown.test.js:47:/** 收发件箱沙箱：预载 loadMailbox 及其依赖（过滤条由 loadMailbox 内联渲染）。 */
src/test/js/taskDrilldown.test.js:101:    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
src/test/js/taskDrilldown.test.js:102:    vm.runInContext("function renderMailboxTable() {}", sandbox);
src/test/js/taskDrilldown.test.js:103:    vm.runInContext("function renderMailboxExpertGroups() {}", sandbox);
src/test/js/taskDrilldown.test.js:104:    vm.runInContext("function renderMailboxPagination() {}", sandbox);
src/test/js/taskDrilldown.test.js:106:    vm.runInContext(extractFn("loadMailbox"), sandbox);
src/test/js/taskDrilldown.test.js:172:            loadMailbox: async () => {},
src/test/js/taskDrilldown.test.js:218:        await sandbox.loadMailbox();
src/test/js/taskDrilldown.test.js:229:        await sandbox.loadMailbox();
src/test/js/taskDrilldown.test.js:239:        await sandbox.loadMailbox();
src/test/js/taskDrilldown.test.js:250:        await sandbox.loadMailbox();
src/test/js/checkRepliesRelocation.test.js:11:const CACHE_KEY = "20260903-bounce-warning";
src/test/js/checkRepliesRelocation.test.js:12:const CHECK_REPLIES_TAG = '<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>';
src/test/js/checkRepliesRelocation.test.js:24:    it("I-1: id=\"checkRepliesBtn\" appears exactly once in index.html, inside the mailbox view", () => {
src/test/js/checkRepliesRelocation.test.js:25:        const idMatches = html.match(/id="checkRepliesBtn"/g) || [];
src/test/js/checkRepliesRelocation.test.js:26:        assert.strictEqual(idMatches.length, 1, "id=\"checkRepliesBtn\" must appear exactly once in index.html");
src/test/js/checkRepliesRelocation.test.js:28:        const btnIdx = html.indexOf('id="checkRepliesBtn"');
src/test/js/checkRepliesRelocation.test.js:29:        assert.ok(btnIdx > mailboxIdx, "checkRepliesBtn must live in the mailbox view, not the contacts view");
src/test/js/checkRepliesRelocation.test.js:30:        assert.ok(!contactsFragment().includes("checkRepliesBtn"),
src/test/js/checkRepliesRelocation.test.js:31:            "checkRepliesBtn must not remain in view-contacts");
src/test/js/checkRepliesRelocation.test.js:39:        assert.strictEqual((app.match(/checkRepliesBtn/g) || []).length, 5,
src/test/js/checkRepliesRelocation.test.js:40:            "checkRepliesBtn must appear exactly 5 times in app.js");
src/test/js/checkRepliesRelocation.test.js:41:        assert.ok(app.includes('checkRepliesBtn: "检查回复"'),
src/test/js/checkRepliesRelocation.test.js:43:        assert.ok(app.includes('CHECK_REPLIES: { label: "检查回复", btnId: "checkRepliesBtn" }'),
src/test/js/checkRepliesRelocation.test.js:45:        assert.ok(app.includes('openTaskModal(taskType, "检查回复", "checkRepliesBtn", { knownActiveAtOpen: true });'),
src/test/js/checkRepliesRelocation.test.js:47:        assert.ok(app.includes('openTaskModal(taskType, "检查回复", "checkRepliesBtn", { launchRequested: true });'),
src/test/js/checkRepliesRelocation.test.js:49:        assert.ok(app.includes('btnId: "checkRepliesBtn"'),
src/test/js/checkRepliesRelocation.test.js:75:        const checkIdx = html.indexOf('id="checkRepliesBtn"');
src/test/js/checkRepliesRelocation.test.js:95:        const checkIdx = html.indexOf('id="checkRepliesBtn"');
src/test/js/checkRepliesRelocation.test.js:108:            "refreshCurrentView mailbox branch must await loadMailbox and refreshAutoReplySummary together");
src/test/js/ragWorkbenchRender.test.js:20:const CACHE_KEY = "20260903-bounce-warning";
src/test/js/trustReplyWorkbenchSharedMount.test.js:5:// 2) G-5 缓存键三联同值（20260903-bounce-warning）；
src/test/js/trustReplyWorkbenchSharedMount.test.js:22:const CACHE_KEY = "20260903-bounce-warning";
src/test/js/trustReplyWorkbenchSharedMount.test.js:154:        const live = appSource.indexOf("function mountLiveTrustReply(recordId)");
src/test/js/trustReplyWorkbenchSharedMount.test.js:156:        assert.ok(live >= 0, "mountLiveTrustReply must exist in app.js");
src/test/js/trustReplyWorkbenchSharedMount.test.js:175:    it("G-5: the cache-key triad is one value (20260903-bounce-warning)", () => {
src/test/js/autoPreviewWorkbenchHost.test.js:50:        assert.match(appSource, /function unmountMailboxTrustReplyHosts\(\) \{\s*unmountLiveTrustReply\(\);\s*\}/,
src/test/js/autoPreviewWorkbenchHost.test.js:58:        assert.ok(appSource.includes("function mountLiveTrustReply(recordId)"), "LIVE mount must remain");
src/test/js/expertTagBatchFix.test.js:51:    vm.runInContext(extractFn("renderMailboxExpertTagEditor"), sandbox);
src/test/js/expertTagBatchFix.test.js:168:        const html = sb.renderMailboxExpertTagEditor(
src/test/js/batchSendTaskConsoleVisualFix.test.js:49:       assert.ok(html.includes('styles.css?v=20260903-bounce-warning'));
src/test/js/batchSendTaskConsoleVisualFix.test.js:50:        assert.ok(html.includes('trust-reply-workbench.js?v=20260903-bounce-warning'));
src/test/js/batchSendTaskConsoleVisualFix.test.js:51:        assert.ok(html.includes('app.js?v=20260903-bounce-warning'));
src/main/resources/static/app.js:186:function unmountLiveTrustReply() {
src/main/resources/static/app.js:196:    unmountLiveTrustReply();
src/main/resources/static/app.js:244:    /** Matched QA subset for send-path audit (composed-reply vs manual-rich-reply), not prompt rule set. */
src/main/resources/static/app.js:709:    checkRepliesBtn: "检查回复",
src/main/resources/static/app.js:720:    CHECK_REPLIES: { label: "检查回复", btnId: "checkRepliesBtn" }
src/main/resources/static/app.js:1702:            loadMailbox(),
src/main/resources/static/app.js:5355:function renderMailboxExpertTagEditor(expertRef, tags, editorId = "mailboxExpertTagEditor", profileMissing = false) {
src/main/resources/static/app.js:5727:        openTaskModal(taskType, "检查回复", "checkRepliesBtn", { knownActiveAtOpen: true });
src/main/resources/static/app.js:5749:    openTaskModal(taskType, "检查回复", "checkRepliesBtn", { launchRequested: true });
src/main/resources/static/app.js:6050:        btnId: "checkRepliesBtn",
src/main/resources/static/app.js:8254:                    ${renderExpertDocuments(documents, contactId)}
src/main/resources/static/app.js:8363:function renderExpertDocuments(documents, contactId) {
src/main/resources/static/app.js:8413:async function openAiAnalysisModal(contactId) {
src/main/resources/static/app.js:8524:async function startAiAnalysis() {
src/main/resources/static/app.js:9863:        if (contactId) await openAiAnalysisModal(contactId);
src/main/resources/static/app.js:9871:        await startAiAnalysis();
src/main/resources/static/app.js:10066:    loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:10187:function renderMailboxTagBadges(tags) {
src/main/resources/static/app.js:10191:function renderMailboxActions(row) {
src/main/resources/static/app.js:10237:                attachmentSectionHtml = renderMailboxAttachments(attachments);
src/main/resources/static/app.js:10251:            ? renderMailboxInboundTagEditor(detail.inboundTags || [], inboundProcessingId)
src/main/resources/static/app.js:10264:            ? renderMailboxExpertTagEditor(
src/main/resources/static/app.js:10307:function renderMailboxAttachments(attachments) {
src/main/resources/static/app.js:10330:    await loadMailbox();
src/main/resources/static/app.js:10653:function mountLiveTrustReply(recordId) {
src/main/resources/static/app.js:10734:    const processingExpertTagHtml = renderMailboxExpertTagEditor(
src/main/resources/static/app.js:10853:                ${renderMailboxInboundTagEditor(inboundTags, Number(id))}
src/main/resources/static/app.js:10895:            <details class="detail-section reply-workflow-detail manual-rich-reply-section">
src/main/resources/static/app.js:10912:                <button class="button primary" data-action="send-manual-rich-reply" data-record-id="${id}" style="margin-top:8px;">发送人工回复</button>
src/main/resources/static/app.js:10931:        mountLiveTrustReply(Number(id));
src/main/resources/static/app.js:11315:            const result = await api(`/api/mail/unmatched-inbound/${recordId}/manual-rich-reply`, {
src/main/resources/static/app.js:11381:    if (action === "send-manual-rich-reply") {
src/main/resources/static/app.js:12073:            await loadMailbox();
src/main/resources/static/app.js:12904:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13381:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13387:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13389:    // B4（T2b-5）：清除按执行的批次过滤 → 提示条随 loadMailbox 隐藏，列表恢复全部邮件。
src/main/resources/static/app.js:13394:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13402:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13409:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13417:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13423:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13428:        loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13437:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13443:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13446:        renderMailboxTable();
src/main/resources/static/app.js:13457:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13465:            loadMailbox().catch((e) => showStatus(e.message, "error"));
src/main/resources/static/app.js:13813:async function loadMailboxAccounts() {
src/main/resources/static/app.js:13828:async function loadMailbox() {
src/main/resources/static/app.js:13829:    await loadMailboxAccounts();
src/main/resources/static/app.js:13909:            renderMailboxExpertGroups();
src/main/resources/static/app.js:13915:            renderMailboxTable();
src/main/resources/static/app.js:13917:        renderMailboxPagination();
src/main/resources/static/app.js:13924:function renderMailboxCard(row) {
src/main/resources/static/app.js:13953:    const actions = renderMailboxActions(row);
src/main/resources/static/app.js:13959:                ${renderMailboxTagBadges(row.tags)}
src/main/resources/static/app.js:13976:function renderMailboxExpertGroups() {
src/main/resources/static/app.js:13992:        const mailCards = (group.mails || []).map((row) => renderMailboxCard(row)).join("");
src/main/resources/static/app.js:14012:function renderMailboxTable() {
src/main/resources/static/app.js:14025:    list.innerHTML = rows.map((row) => renderMailboxCard(row)).join("");
src/main/resources/static/app.js:14028:function renderMailboxPagination() {
src/main/resources/static/app.js:14312:function renderMailboxInboundTagEditor(tags, inboundProcessingId) {
src/main/resources/static/app.js:14348:        editor.outerHTML = renderMailboxInboundTagEditor(detail.inboundTags || [], ctx.inboundProcessingId);
src/test/js/unmatchedQaReplySource.test.js:13:        assert.ok(appJsSource.includes('class="detail-section reply-workflow-detail manual-rich-reply-section"'));
src/test/js/overlayAndDialogContrast.test.js:22:const CACHE_KEY = "20260903-bounce-warning";
src/test/js/ragKnowledgeBasePage.test.js:333:    it("G-5：三处 ?v= 缓存键同值且等于 20260903-bounce-warning", () => {
src/test/js/ragKnowledgeBasePage.test.js:335:            assert.ok(html.includes(`${asset}?v=20260903-bounce-warning`), `${asset} key`);
src/test/js/ragKnowledgeBasePage.test.js:339:        assert.ok(keys.every((key) => key === "20260903-bounce-warning"), `all keys must share one value: ${keys}`);
src/test/js/composedReplyOrder.test.js:15:        assert.ok(!appJsSource.includes('data-action="copy-to-manual-rich-reply"'));
src/test/js/aiReplyReviewConfirmation.test.js:13:        if (!app.includes("mountLiveTrustReply")) throw new Error("missing live adapter");
src/test/js/aiReplyReviewConfirmation.test.js:18:        if (workbench.includes("manual-rich-reply") || workbench.includes("/send")) {
src/test/js/aiReplyReviewConfirmation.test.js:65:    it("send-manual-rich-reply calls submitManualRichReply directly", function () {
src/test/js/aiReplyReviewConfirmation.test.js:66:        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
src/test/js/aiReplyReviewConfirmation.test.js:100:        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
src/test/js/aiReplyReviewConfirmation.test.js:191:    it("training host never constructs manual-rich-reply payload", function () {
src/test/js/aiReplyReviewConfirmation.test.js:193:        if (trainingBlock.includes("send-manual-rich-reply")) throw new Error("training host must not send");
src/test/js/aiReplyReviewConfirmation.test.js:200:        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
src/test/js/aiReplyReviewConfirmation.test.js:218:    it("send-manual-rich-reply does not gate on generationState or usedLlm", function () {
src/test/js/aiReplyReviewConfirmation.test.js:219:        const sendIdx = app.indexOf('if (action === "send-manual-rich-reply")');
src/test/js/aiReplyReviewConfirmation.test.js:231:        if (!app.includes('"send-manual-rich-reply"')) throw new Error("send handler missing");
src/test/js/manualReplySubjectPrefill.test.js:13:const CACHE_KEY = "20260903-bounce-warning";
src/test/js/mailboxExpertGrouping.test.js:89:        renderMailboxTable: () => {},
src/test/js/mailboxExpertGrouping.test.js:90:        renderMailboxExpertGroups: () => {},
src/test/js/mailboxExpertGrouping.test.js:91:        renderMailboxPagination: () => {},
src/test/js/mailboxExpertGrouping.test.js:95:        renderMailboxTagBadges: () => "",
src/test/js/mailboxExpertGrouping.test.js:96:        renderMailboxActions: (row) => `<button data-action="open-pending" data-id="${row.inboundProcessingId}">处理</button>`
src/test/js/mailboxExpertGrouping.test.js:104:    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
src/test/js/mailboxExpertGrouping.test.js:105:    vm.runInContext(extractFn("renderMailboxCard"), sandbox);
src/test/js/mailboxExpertGrouping.test.js:106:    vm.runInContext(extractFn("renderMailboxExpertGroups"), sandbox);
src/test/js/mailboxExpertGrouping.test.js:107:    vm.runInContext(extractFn("renderMailboxPagination"), sandbox);
src/test/js/mailboxExpertGrouping.test.js:108:    vm.runInContext(extractFn("loadMailbox"), sandbox);
src/test/js/mailboxExpertGrouping.test.js:146:        await sb.loadMailbox();
src/test/js/mailboxExpertGrouping.test.js:171:        await sb.loadMailbox();
src/test/js/mailboxExpertGrouping.test.js:182:    it("renderMailboxExpertGroups outputs nested expert group and mailbox card actions", () => {
src/test/js/mailboxExpertGrouping.test.js:209:        sb.renderMailboxExpertGroups();
src/test/js/mailboxExpertGrouping.test.js:219:    it("renderMailboxPagination uses expert unit label", () => {
src/test/js/mailboxExpertGrouping.test.js:225:        sb.renderMailboxPagination();
src/test/js/mailboxExpertGrouping.test.js:258:    it("loadMailbox sends the exact expertContactId and no dates when focused", async () => {
src/test/js/mailboxExpertGrouping.test.js:270:        await sb.loadMailbox();
src/test/js/mailboxExpertGrouping.test.js:281:    it("loadMailbox omits expertContactId when no focus is set", async () => {
src/test/js/mailboxExpertGrouping.test.js:291:        await sb.loadMailbox();
src/test/js/mailboxExpertGrouping.test.js:297:    it("renderMailboxExpertGroups opens only the focused group and tags it with the contact id", () => {
src/test/js/mailboxExpertGrouping.test.js:325:        sb.renderMailboxExpertGroups();

```

## 分析/机器报告/异常补充检索（2026-09-08）

```text
src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt:15:class GlobalExceptionHandler {
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:26:    private val analysisResultRepository = Mockito.mock(ExpertAnalysisResultRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:37:            analysisResultRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:49:            analysisResultRepository,
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:143:        Mockito.`when`(analysisResultRepository.save(any(ExpertAnalysisResult::class.java)))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:166:        Mockito.`when`(analysisResultRepository.findById(100L)).thenReturn(Optional.of(existing))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:167:        Mockito.`when`(analysisResultRepository.save(any(ExpertAnalysisResult::class.java)))
src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt:180:        Mockito.verify(analysisResultRepository, Mockito.atLeastOnce()).save(saveCaptor.capture())
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:29:    private val progressLogRepository: TaskProgressLogRepository,
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:44:    private val extractor = TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:70:                val latestLog = progressLogRepository.findTopByTaskTypeOrderByIdDesc(taskType)
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt:74:        val logs = progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(targetExecutionId)
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagFactAdminService.kt:10:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagFactAdminService.kt:171:            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "RAG fact not found: $factCode")
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConfigService.kt:13:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConfigService.kt:334:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:317:            ?: throw org.springframework.web.server.ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt:13:    private val progressLogRepository: TaskProgressLogRepository,
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt:178:                progressLogRepository.rebindPendingExecutionId(pendingToken, executionId)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt:203:            progressLogRepository.save(logEntry)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt:211:            val latestLog = progressLogRepository.findTopByTaskTypeOrderByIdDesc(taskType)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt:26:    private val progressLogRepository: TaskProgressLogRepository,
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt:136:        val latestLog = progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(executionId)
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:27:    private val progressLogRepository: TaskProgressLogRepository,
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt:42:            progressDeleted = purgeLoop { limit -> progressLogRepository.deleteOlderThan(cutoff, limit) }
src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertAnalysisResult.kt:7:@Table("expert_analysis_result")
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:34:    private val analysisResultRepository: ExpertAnalysisResultRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:93:        analysisResultRepository.deleteAllByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:95:            analysisResultRepository.save(
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:113:        val results = analysisResultRepository.findAllByExpertContactIdOrderByDisplayOrderAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:119:        val existing = analysisResultRepository.findById(fieldId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:125:        val updated = analysisResultRepository.save(
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:133:        val existing = analysisResultRepository.findAllByExpertContactIdOrderByDisplayOrderAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:135:        val saved = analysisResultRepository.save(
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:149:        analysisResultRepository.deleteAllByExpertContactId(contactId)
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:30:import org.springframework.web.server.ResponseStatusException
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:228:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:240:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:250:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:264:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:294:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:455:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:508:        val missing = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt:517:        val deleted = assertThrows(ResponseStatusException::class.java) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:24:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:130:                throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:221:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:251:            throw ResponseStatusException(HttpStatus.CONFLICT, "Config name already exists: $configName")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:357:            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Template $templateId not found", e)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:360:            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Template $templateId is not enabled")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:364:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt:549:            throw ResponseStatusException(HttpStatus.CONFLICT, "Config name already exists: $configName", e)
src/main/kotlin/com/weibo/talentintroduction/mail/domain/DmarcReport.kt:7:@Table("dmarc_report")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:19:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt:101:            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Batch send task config not found: $configId")
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:32:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:36:    throw ResponseStatusException(HttpStatus.FORBIDDEN, "QA_RULE_READ_ONLY")
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:141:     * `@ExceptionHandler(Exception)` 会把 ResponseStatusException 变成 500
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:146:    @ExceptionHandler(ResponseStatusException::class)
src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt:147:    fun handleReadOnlyGuard(ex: ResponseStatusException): ResponseEntity<ApiErrorResponse> {
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:19:    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:24:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:32:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:40:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:51:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:60:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:90:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:100:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:111:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:121:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:135:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:146:        Mockito.reset(progressLogRepository)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:147:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:152:        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:171:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:174:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:192:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:195:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:211:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:214:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:221:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:236:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:256:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:277:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:297:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:312:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:326:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:354:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:382:        Mockito.reset(progressLogRepository)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:383:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:393:        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:403:        Mockito.reset(progressLogRepository)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:404:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:408:        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:427:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:430:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:448:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreTest.kt:451:        val store = TaskProgressStore(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:18:    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:23:        TaskAuditRetentionService(progressLogRepository, executionRepository, props)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:32:        Mockito.verify(progressLogRepository).deleteOlderThan(
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:49:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:60:        Mockito.verify(progressLogRepository, Mockito.times(4))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:66:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.eq(2000)))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:68:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.eq(1000)))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:77:        Mockito.verify(progressLogRepository, Mockito.times(2))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:92:        val inOrder: InOrder = Mockito.inOrder(progressLogRepository, executionRepository)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:93:        inOrder.verify(progressLogRepository).deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt())
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:101:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:115:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:132:        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt:140:        Mockito.verify(progressLogRepository, Mockito.times(2))
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:24:    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:26:    private val extractor = TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:104:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(7L)).thenReturn(log)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:118:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(8L)).thenReturn(log)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:132:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(9L)).thenReturn(log)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:143:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:152:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(11L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt:165:        Mockito.verify(progressLogRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:20:    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:24:        progressStore, progressLogRepository, taskExecutionRepository, objectMapper
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:79:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(3L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:94:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(4L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:114:        // Verify progressLogRepository was never queried for this executionId
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:115:        Mockito.verify(progressLogRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:153:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(17L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:171:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(18L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:250:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(8L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:265:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(9L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:288:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_REVALIDATION"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:290:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(1L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:310:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_REVALIDATION"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:312:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(1L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:347:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(12L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt:405:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(23L))
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:64:import org.springframework.web.server.ResponseStatusException
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:341:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:371:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:393:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:418:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:438:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:34:    private lateinit var progressLogRepository: TaskProgressLogRepository
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:98:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(7L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:118:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(5L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:135:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:137:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(8L))
src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt:149:        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_DISCOVERY"))
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:630:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:807:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:828:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:987:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1019:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1050:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1096:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1487:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1517:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1547:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1655:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1688:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1721:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1767:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1888:        val missing = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1897:        val reordered = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1906:        val extra = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1915:        val absent = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:1940:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2171:        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2363:            val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2385:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:2397:        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt:47:        progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java),
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt:227:            throw AssertionError("expected ResponseStatusException")
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt:228:        } catch (ex: org.springframework.web.server.ResponseStatusException) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportIngestService.kt:13:    private val dmarcReportRepository: DmarcReportRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportIngestService.kt:19:            if (dmarcReportRepository.existsByReportId(summary.reportId)) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportIngestService.kt:22:            dmarcReportRepository.save(summary.toEntity(now))
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:35:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:241:        throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:320:        throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:330:        throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:502:                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI generation queue is full")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:505:                throw ResponseStatusException(HttpStatus.CONFLICT, "generationId is already active")
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:35:import org.springframework.web.server.ResponseStatusException
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:94:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:104:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:122:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:238:            ResponseStatusException(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:247:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:268:            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "\u53d1\u9001\u6682\u65f6\u5931\u8d25\uff0c\u53ef\u5b89\u5168\u91cd\u8bd5")
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:274:        val ex = assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:295:            ResponseStatusException(HttpStatus.CONFLICT, "\u53d1\u9001\u72b6\u6001\u672a\u77e5\uff0c\u8bf7\u52ff\u91cd\u590d\u53d1\u9001 (Message-ID: <test@weibo.com>)")
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt:301:        val ex = assertThrows(ResponseStatusException::class.java) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:54:import org.springframework.web.server.ResponseStatusException
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:186:            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SEND_EVIDENCE_SOURCE_CONFLICT")
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:197:                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "RAG_FINGERPRINT_REQUIRED")
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:199:                throw ResponseStatusException(HttpStatus.CONFLICT, "RAG_CORPUS_STALE")
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:208:                    throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "RAG_FACT_CODE_UNKNOWN")
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:223:                throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:232:                throw ResponseStatusException(ex.status, ex.code)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:239:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:364:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:372:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:473:                            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:506:                            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:512:                            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:517:                        throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:524:                        is ResponseStatusException -> throw deliveryEx
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:539:                            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:572:                throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:578:                throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:584:                throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:686:    // - I-2: 只捕获 IllegalArgumentException，真故障（DB/IO/ResponseStatusException）向上抛；
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1120:                ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1129:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1139:            throw ResponseStatusException(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:46:    private val progressLogRepository: TaskProgressLogRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:165:        val logs = progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(executionId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt:272:                parseProgressLogOutcome(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(it))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:41:import org.springframework.web.server.ResponseStatusException
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:455:            assertThrows(ResponseStatusException::class.java) {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:29:    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:39:        progressLogRepository = progressLogRepository,
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:89:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(10L)).thenReturn(rows)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:90:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(null)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:196:        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(10L))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:198:        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(latest)
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:235:        Mockito.verify(progressLogRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:246:        Mockito.verify(progressLogRepository, Mockito.never())
src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt:286:        Mockito.verify(progressLogRepository, Mockito.never())
src/main/resources/db/migration/V37__create_dmarc_report.sql:1:CREATE TABLE dmarc_report (
src/main/resources/db/migration/V37__create_dmarc_report.sql:14:    UNIQUE KEY uk_dmarc_report_id (report_id)
src/main/resources/db/migration/V59__create_expert_analysis_result.sql:1:CREATE TABLE expert_analysis_result (

```
