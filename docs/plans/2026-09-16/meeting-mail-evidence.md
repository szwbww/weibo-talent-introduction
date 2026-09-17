# 会议排期与人工附件：代码证据快照

审计日期：2026-09-16。范围：当前工作区代码；不把预览页的浏览器状态视为生产能力。下列命令均已执行；空结果明确标注。代码行号只对本次快照负责，执行前若文件已变化须重新定位同一方法。

## 关键文件 SHA-256

|文件|SHA-256|
|---|---|
|`src/main/resources/static/mailbox-chat.js`|`8ca202cac917b773aa36491e9ab16cc5fe2a367228ca54965a99a6b5aa1e7978`|
|`src/main/resources/static/app.js`|`5774790c8079c5c54010768a4bd7ae56d90ca839d0f8affccc6c0c5c3eddbcac`|
|`src/main/resources/static/styles.css`|`79197685ac5b86523bd8725269f3ec2fd4c3ef3fcb9c084132795f5a0d6a99b2`|
|`src/main/resources/static/index.html`|`bf1a39ebbfc4e5d8b6759ed30ac0e228191f7ece69423d4e531f876e34d21ea2`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`|`044d1a7b0b15d01d09e3bfbbb8ed7d50b74210fbb9610fb295035397c9ca8522`|
|`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`|`d971523ac4b84d9b3ba2958cf6c748e42a45aa0e4af5cca31114be1057fd49d5`|
|`src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt`|`441a843f4d1630d63809531dca77d920718febdab6651d81d13b6f45cf20fef0`|

## 前端改动前基线

src/main/resources/static/mailbox-chat.js:2665：

```html
            return `
                <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
                    <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
                    <div class="mc-editor-tools">
                        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
                        ${meetingTrigger}${followUpButton}
                    </div>
                    <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
                    ${anchorNote}
                    ${meetingAttachment}
                    <div class="mc-compose-footer">
                        <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
                        ${templateFollowButton}
                        <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
                    </div>
                </div>
            `;
        }

        function manualFollowUpHtml() {
            return `
```

src/main/resources/static/mailbox-chat.css:60（不修改此字节锁定文件）：

```css
.mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
.mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
.mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
.mail-chat .button{white-space:nowrap}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
```

## 异常映射补充证据

GlobalExceptionHandler.kt:16/20/24分别映射IllegalArgumentException/IllegalStateException为400、NoSuchElementException为404；:65通用Exception映射500，未单独处理ResponseStatusException或multipart超限。MeetingConfirmationController.kt:23明确说明不使用会落通用500的ResponseStatusException。本计划新增日历冲突使用局部专用异常映射；附件错误使用04的专用映射，不广泛改变旧端点。

## 完整检索回执

### 全文件类型直接SQL写入补查

```sh
rg -n -i '(insert( ignore)? into|update|delete from)[[:space:]]+`?(mail_record|meeting_schedule|mail_send_attempt)`?\b' scripts src/main --glob '!*.map'
```

```text
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:26:UPDATE mail_record mr
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:42:UPDATE mail_send_attempt msa
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:22:        INSERT IGNORE INTO mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:47:        UPDATE mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:59:        UPDATE mail_send_attempt
```

### mail_record_all

```sh
rg -n 'mailRecordRepository\.|mail_record|calendarAttachmentJson' src/main/kotlin src/main/resources/db/migration scripts --glob '*.kt' --glob '*.sql' --glob '*.py'
```

```text
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:5:    MODIFY mail_record_id BIGINT NULL;
src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql:16:        CHECK ((mail_record_id IS NULL) <> (inbound_processing_id IS NULL));
scripts/dump_rag_parity_fixtures.py:20:- >= 20 real inbound emails exported from historical `mail_record` INBOUND
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:15:ALTER TABLE mail_record ADD COLUMN error_summary VARCHAR(1024) DEFAULT NULL;
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:16:ALTER TABLE mail_record ADD COLUMN mail_send_attempt_id BIGINT DEFAULT NULL;
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:17:CREATE INDEX idx_mr_mail_send_attempt_id ON mail_record(mail_send_attempt_id);
src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql:3:ALTER TABLE mail_record
src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql:6:CREATE INDEX idx_mail_record_task_execution
src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql:7:    ON mail_record (task_execution_id, id);
src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql:11:       SELECT 1 FROM mail_record mr
src/main/resources/db/migration/V1__create_business_tables.sql:97:CREATE TABLE mail_record (
src/main/resources/db/migration/V1__create_business_tables.sql:111:    CONSTRAINT fk_mail_record_contact
src/main/resources/db/migration/V1__create_business_tables.sql:113:    CONSTRAINT fk_mail_record_rule
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:2:-- 绑定语义 = 主题发起权归属（回复仍由 mail_record.sender_account_code 决定）。
src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql:21:      FROM mail_record mr
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:3:    mail_record_id BIGINT NOT NULL,
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:9:    KEY idx_mail_attachment_record (mail_record_id, created_at),
src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql:11:        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id)
src/main/resources/db/migration/V114__create_rag_fact_audit.sql:5:-- （I-21），与 03b 的 mail_record_rag_fact.corpus_fingerprint 闭环 —— 单看存证
src/main/resources/db/migration/V114__create_rag_fact_audit.sql:20:-- （与 V113 mail_record_rag_fact 不声明外键的既有基线一致）。
src/main/resources/db/migration/V114__create_rag_fact_audit.sql:36:  COMMENT = 'RAG 事实改动审计：与 mail_record_rag_fact.corpus_fingerprint 闭环还原发信时的原文版本（I-21/D-16）';
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:3:CREATE TEMPORARY TABLE v24_mail_record_ambiguous_attempts AS
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:6:  JOIN mail_record mr
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:13:CREATE TEMPORARY TABLE v24_mail_record_ambiguous_guard (id BIGINT PRIMARY KEY);
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:14:INSERT INTO v24_mail_record_ambiguous_guard (id) VALUES (1);
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:15:INSERT INTO v24_mail_record_ambiguous_guard (id)
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:16:SELECT 1 FROM v24_mail_record_ambiguous_attempts LIMIT 1;
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:26:UPDATE mail_record mr
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:35:DROP INDEX idx_mr_mail_send_attempt_id ON mail_record;
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:37:ALTER TABLE mail_record
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:38:    ADD CONSTRAINT uq_mail_record_send_attempt UNIQUE (mail_send_attempt_id),
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:39:    ADD CONSTRAINT fk_mail_record_send_attempt
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:43:JOIN mail_record mr
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql:2:-- V123 mail_record 会议日历附件存档列（fast-p 02）
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql:13:ALTER TABLE mail_record
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:1:-- 1. mail_record monitoring fields
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:2:ALTER TABLE mail_record
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:8:        COMMENT '触发本条 OUTBOUND 的 INBOUND mail_record.id' AFTER triggered_by;
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:10:ALTER TABLE mail_record
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:11:    ADD INDEX idx_mail_record_dir_type_sent (direction, mail_type, sent_at),
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:12:    ADD INDEX idx_mail_record_dir_received (direction, received_at),
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:13:    ADD INDEX idx_mail_record_sender_sent (sender_account_code, sent_at),
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:14:    ADD INDEX idx_mail_record_triggered_sent (triggered_by, sent_at),
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:15:    ADD INDEX idx_mail_record_source_inbound (source_inbound_id);
src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql:32:    source_inbound_id BIGINT NULL COMMENT '触发本次晋级的 INBOUND mail_record.id',
src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt:54:        // runAndRecord 上下文里跑，会有自己的 execution 行；队列模式下 mail_record.
src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql:1:ALTER TABLE mail_record
src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql:2:    ADD INDEX idx_mail_record_status_created (direction, send_status, created_at);
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt:7:@Table("mail_record")
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt:35:    val calendarAttachmentJson: String? = null
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecordRagFact.kt:8: * `mail_record_rag_fact` 表（与 `mail_record_qa_rule` 并列，一封信只会写其中一张，
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecordRagFact.kt:19:@Table("mail_record_rag_fact")
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:4:    source_mail_record_id BIGINT,
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:16:    CONSTRAINT fk_meeting_schedule_mail FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:64:        /** 03 (I-3/I-4)：日历附件只在 SENT 出站行暴露（mail_record.send_status 值）。 */
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:241:            mailRecordRepository.findAllById(outboundSentRows.map { it.id })
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:387:        val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecordQaRule.kt:6:@Table("mail_record_qa_rule")
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt:22:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:1:ALTER TABLE mail_record
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:6:    mail_record_id BIGINT NOT NULL,
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:15:    CONSTRAINT fk_inbound_intent_mail_record
src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql:16:        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id),
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPrefilterService.kt:29: * `mail_record` 映射本类型。
src/main/kotlin/com/weibo/talentintroduction/rag/service/RagProcessContextResolver.kt:16: * - `expertReplyCount` = 该联系人 `mail_record` 中 `direction='INBOUND'` 的条数
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:70:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:245:            contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:2:-- V113 mail_record_rag_fact（plan 03b: 03b-rag-send-bridge.md）
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:4:-- RAG 回信发出后的事实存证表：与 mail_record_qa_rule 并列、互不干扰 ——
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:5:-- 一封信只会写其中一张（I-39：三条发送路径互斥，RAG 路径绝不写 mail_record_qa_rule）。
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:12:--         UNIQUE(mail_record_id, ordinal) 对齐 mail_record_qa_rule.ordinal 语义
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:16:-- 不声明外键到 rag_fact / mail_record：事实可能被停用或改写，存证不应随之失效
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:17:-- （与 mail_record_qa_rule 不声明 ON DELETE CASCADE 的既有基线一致）；RAG 回信
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:18:-- 的 mail_record 生命周期独立于本表。
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:20:CREATE TABLE mail_record_rag_fact (
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:22:    mail_record_id    BIGINT       NOT NULL COMMENT '已发出的 RAG 回信 mail_record.id',
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:28:    UNIQUE KEY uk_mail_record_rag_fact (mail_record_id, ordinal),
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:29:    KEY idx_mail_record_rag_fact_record (mail_record_id),
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:30:    KEY idx_mail_record_rag_fact_code (fact_code)
src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql:32:  COMMENT = 'RAG 回信的事实存证：与 mail_record_qa_rule 并列，一封信只会写其中一张（I-39）';
src/main/resources/db/migration/V65__qa_rule_dedup_keyword_fix_and_overview.sql:128:--     If mail_record references block the delete, keep them disabled instead.
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:42:        /** 会话回信线程/审计锚点类型前缀（真实 mail_record，绝不伪造 inbound id）。 */
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:72:    /** findCompletedByRequestId 命中的已完成会话回信（attempt SENT + 唯一 mail_record）。 */
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:178:     * 且其唯一 mail_record 存在时返回。其余状态（IN_PROGRESS/UNKNOWN/FAILED…）返回空，
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:186:        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:316:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:329:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:349:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:352:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:398:        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:411:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:432:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:435:        val savedRecord = mailRecordRepository.save(mailRecord)
src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql:6:    inbound_mail_record_id BIGINT      NULL,
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:76:    /** 训练邮件：mail_record 行；只接受 INBOUND。 */
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:78:        val mail = mailRecordRepository.findById(sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:82:                "mail_record $sourceId does not exist"
src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt:89:                "mail_record $sourceId is not an inbound mail"
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:3:CREATE TABLE mail_record_qa_rule (
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:5:    mail_record_id BIGINT NOT NULL,
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:8:    CONSTRAINT fk_mail_record_qa_rule_mail_record
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:9:        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id) ON DELETE RESTRICT,
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:10:    CONSTRAINT fk_mail_record_qa_rule_qa_rule
src/main/resources/db/migration/V42__mail_record_qa_rule.sql:12:    CONSTRAINT uk_mail_record_qa_rule UNIQUE (mail_record_id, qa_rule_id)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:323:        val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:356:        val inboundMailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:696:        val outboundRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:971:    ): MailRecord = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1009:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1016:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1169:        val saved = mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:32: * 其唯一 owner（mail_record 或 inbound_mail_processing）→ contact。
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:38: * mail_record.source_inbound_id 永远不作为 processing id 使用。
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:67:     * - mail_record owner → record.expert_contact_id；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:75:            return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:84:        error("Attachment $attachmentId has no owner (mail_record_id and inbound_processing_id both null)")
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:202:            mailRecordId = rs.getLongOrNull("mail_record_id"),
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:397:     *    processing → 归属 processing；0 候选 → 该 mail_record 自身；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:664:     * 唯一匹配历史 mail_record，恰好 1 条才回退其附件；0/多条一律拒绝（空列表，
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:671:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:737:            SELECT id FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:782:                   a.mail_record_id AS mail_record_id,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:805:              LEFT JOIN mail_record mr ON mr.id = a.mail_record_id
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
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:245:        val stats = mailRecordRepository.aggregateSenderAccountStats(from, to).associateBy { it.senderAccountCode }
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:272:        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:289:        mailRecordRepository.aggregateIntroCohortByDomain(start, end, matureBefore).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:298:        mailRecordRepository.aggregateUndeliveredByDomain(start, end).forEach { row ->
src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt:326:        val countryRows = mailRecordRepository.aggregateIntroCohortByCountry(start, end, matureBefore)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:53:            // 双 owner 兼容：mail_record owner（历史）沿用原有归属校验；
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:57:                val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:22: *   storage_path；已匹配分支以 mail_record 为 owner，未匹配以 processing 为 owner。
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:59:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:110:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:96:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt:394:            return mailRecordRepository.findByIdOrNull(mailRecordId)?.expertContactId
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt:13: * mail_record.calendar_attachment_json；客户端不能直传任意 downloadUrl。
src/main/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizer.kt:7: * （观测事实，无官方文档，见 K-vendor-message-id-prefix.md 两个样本）。`mail_record.message_id`
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:144:                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:756:                        // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:969:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1146:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:53:        val rows = mailRecordRepository.listMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:65:        val total = mailRecordRepository.countMailbox(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:109:        val total = mailRecordRepository.countMailboxExperts(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:125:        val summaries = mailRecordRepository.listMailboxExpertSummaries(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:144:        val mailRows = mailRecordRepository.listMailboxByExpertContactIds(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:192:        val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:265:                val record = mailRecordRepository.findByIdOrNull(id)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:28: * 从事件（mail_record / bounce_record / mail_attachment）反推每位联系人的**期望** operator_status
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:52:    /** 全表扫描 + 内存比对（expert_contact 2062 行 / mail_record 2157 行，规模小，一次读入）。 */
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:55:        val mailRecords = mailRecordRepository.findAll()
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:68:        // 材料附件挂在 INBOUND mail_record 上（MailAttachmentService.saveInboundAttachments 以 mailRecordId 关联）
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:166:     * - CONTACTED：存在 OUTBOUND + INTRODUCTION + SENT 的 mail_record（ManualInitialOutreachService.hasSentIntroduction():895 逐字）
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:168:     * - REPLIED：存在 INBOUND mail_record（AutoMailReplyService:802）
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:199:        // → 400 SEND_EVIDENCE_SOURCE_CONFLICT，绝不任选其一（否则 mail_record_qa_rule 与
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:200:        // mail_record_rag_fact 各写一份、互相矛盾）。判定先于下方 assembly 服务端重算。
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:348:     * 跟进邮件（I-2/I-3）：`anchorMailRecordId` 非空时按 id 重读权威 `mail_record` 并逐条
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:397:            mailRecordRepository.findById(anchorMailRecordId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:401:            mailRecordRepository.findLatestSentOutboundAnchor(
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:618:            // I-3：会话回信锚点只以真实 mail_record.id 表达，绝不伪造 inbound id。
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:716:                            // mail_record_rag_fact 存证；RAG 路径 canonicalFactIds 恒为空，
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:717:                            // 绝不写 mail_record_qa_rule。
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:833:                val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1041:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1645:    /** 落库 mail_record.inReplyTo 与 SendPayload.inReplyTo 的值（线程锚点）。 */
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt:34:        val replyCount = mailRecordRepository.countInboundReplies(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:71:        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:144:        mailRecordRepository.save(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:25: * 只暴露 mail_record 中 OUTBOUND + MANUAL_RICH_REPLY + SENT、expertContactId 等于 path、
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:50:        val record = mailRecordRepository.findById(mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:59:            CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:604:        mailRecordId = requireNotNull(mailRecordId) { "Matched mail attachment must have mail_record_id" },
src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt:214:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:182:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:189:        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:368:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:31: * 不触发 SMTP、send_attempt、mail_record、meeting_schedule、processing 状态、
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:47: * 绝不从 mail_record 推算。
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:79: * `mail_record` 并验证（同联系人/OUTBOUND/SENT/真实账号/scope），非法一律 422；null
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:50:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt:56:                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:121:        val records = mailRecordRepository.findInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:129:        val total = mailRecordRepository.countInboundMailsForSimulation(
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:177:        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:184:            val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:197:            val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:211:            val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt:218:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt:98:            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2356:        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt:2401:        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt:61:    // mail_record.expert_contact_id 有 FK（V1__create_business_tables.sql），发送失败那一支无孤儿问题。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRagFactRepository.kt:7: * 计划 03b (T2): `mail_record_rag_fact` 存证仓储，与
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:13: * 绝不再 UNION INBOUND mail_record 重复计数）；发件只取 OUTBOUND mail_record。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:292:    /** 每位专家最近一封来信（真实 processing.id，绝不从 mail_record 推算）。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:433:     * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:468:              FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:530:                SELECT 1 FROM mail_record mro
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:48:        SELECT * FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:59:        FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:75:        SELECT mr.* FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:78:            FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:85:                    WHERE p.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:90:                    WHERE p2.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:112:            FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:119:                    WHERE p.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:124:                    WHERE p2.expert_contact_id = mail_record.expert_contact_id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:150:        SELECT * FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:174:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:183:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:191:        SELECT COUNT(DISTINCT expert_contact_id) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:199:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:209:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:219:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:229:        SELECT * FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:248:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:263:        SELECT * FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:289:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:309:    @Query("SELECT * FROM mail_record WHERE id = :id")
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:314:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:331:          FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:359:                  FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:365:                       FROM mail_record inb
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:367:                               FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:399:                  FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:405:                       FROM mail_record inb
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:407:                               FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:434:                  FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:452:        SELECT DISTINCT sender_account_code FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:460:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:471:        SELECT COUNT(*) FROM mail_record
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:498:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:500:            FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:530:                     JOIN mail_record mr2 ON ma.mail_record_id = mr2.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:567:            FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:626:                FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:694:                FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:766:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:768:            FROM mail_record mr
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:806:                     JOIN mail_record mr2 ON ma.mail_record_id = mr2.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:858:        SELECT * FROM mail_record
```

### meeting_schedule_all

```sh
rg -n 'meetingScheduleRepository\.|meetingScheduleService\.|meeting_schedule' src/main
```

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MeetingSchedule.kt:7:@Table("meeting_schedule")
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:31: * 不触发 SMTP、send_attempt、mail_record、meeting_schedule、processing 状态、
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:405:            meetingScheduleService.extractAndCreate(contactId, inboundMailRecord)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:90:            meetingSchedules = meetingScheduleRepository.findAllByExpertContactIdOrderByCreatedAtDesc(contactId),
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:40:        return meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:55:        return meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:74:        return meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:101:        val updatedSchedule = meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:196:        val updatedSchedule = meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:222:        val updatedSchedule = meetingScheduleRepository.save(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt:268:        val schedule = meetingScheduleRepository.findById(scheduleId)
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:212:        meetingScheduleService.createManual(contactId, request.toCommand()).toResponse()
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:220:        meetingScheduleService.updateSchedule(contactId, scheduleId, request.toCommand()).toResponse()
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:228:        meetingScheduleService.confirmMeetingAndEmail(contactId, scheduleId, request.toCommand()).toResponse()
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:235:        meetingScheduleService.completeMeeting(contactId, scheduleId).toResponse()
src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:242:        meetingScheduleService.cancelMeeting(contactId, scheduleId).toResponse()
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:1:CREATE TABLE meeting_schedule (
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:14:    KEY idx_meeting_schedule_contact (expert_contact_id),
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:15:    CONSTRAINT fk_meeting_schedule_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql:16:    CONSTRAINT fk_meeting_schedule_mail FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id)
```

### attempt_all

```sh
rg -n 'attemptRepository\.|mailSendAttemptRepository\.|mail_send_attempt' src/main/kotlin src/main/resources/db/migration scripts --glob '*.kt' --glob '*.sql' --glob '*.py'
```

```text
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:1:CREATE TABLE mail_send_attempt (
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:16:ALTER TABLE mail_record ADD COLUMN mail_send_attempt_id BIGINT DEFAULT NULL;
src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql:17:CREATE INDEX idx_mr_mail_send_attempt_id ON mail_record(mail_send_attempt_id);
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:4:SELECT msa.id AS mail_send_attempt_id
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:5:  FROM mail_send_attempt msa
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:18:ALTER TABLE mail_send_attempt
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:27:JOIN mail_send_attempt msa
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:31:SET mr.mail_send_attempt_id = msa.id
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:32:WHERE mr.mail_send_attempt_id IS NULL;
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:35:DROP INDEX idx_mr_mail_send_attempt_id ON mail_record;
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:38:    ADD CONSTRAINT uq_mail_record_send_attempt UNIQUE (mail_send_attempt_id),
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:40:        FOREIGN KEY (mail_send_attempt_id) REFERENCES mail_send_attempt(id);
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:42:UPDATE mail_send_attempt msa
src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql:44:  ON mr.mail_send_attempt_id = msa.id
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:341:    // 每位专家至多一封 INTRODUCTION（mail_send_attempt 的 uq_orcid_mail_type (orcid_id, mail_type)
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:183:        val attempt = attemptRepository.findByOrcidIdAndMailType(orcidId, shortKey) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:200:        attemptRepository.insertIgnore(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:214:        val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:238:                val affected = attemptRepository.claimStatus(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:262:                val affected = attemptRepository.claimStatus(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:303:        val attempt = attemptRepository.findById(attemptId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:367:        attemptRepository.updateStatusAndError(
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:385:        val attempt = attemptRepository.findById(attemptId).orElseThrow {
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:438:        attemptRepository.updateStatusAndError(
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MailSendAttempt.kt:7:@Table("mail_send_attempt")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:85:        val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:87:            mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:134:            val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt:136:                mailSendAttemptRepository.save(attempt.copy(
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:16:    @Query("SELECT * FROM mail_send_attempt WHERE orcid_id = :orcidId AND mail_type = :mailType FOR UPDATE")
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:22:        INSERT IGNORE INTO mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:47:        UPDATE mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt:59:        UPDATE mail_send_attempt
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:475:     *   mail_send_attempt UNIQUE upsert, hasSentIntroduction double-check — all unchanged).
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:734:                    val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:735:                    val attempt = mailSendAttemptRepository.save(
```

### calendar_snapshot

```sh
rg -n 'calendarAttachmentJson|calendarAttachment\s*=' src/main/kotlin
```

```text
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt:35:    val calendarAttachmentJson: String? = null
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:277:                calendarAttachment = calendarAttachmentOf(row, mailRecordRowsById[row.id])
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:387:        val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:329:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:349:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:411:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:432:                calendarAttachmentJson = snapshotJson
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:626:            calendarAttachment = calendarSnapshot
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:665:                    calendarAttachment = calendarSnapshot
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:59:            CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
```

### cache_tests

```sh
rg -l '20260914-followup-email' src/test/js
```

```text
src/test/js/ragWorkbenchRender.test.js
src/test/js/meetingConfirmationAssets.test.js
src/test/js/overlayAndDialogContrast.test.js
src/test/js/mailboxChatStyle.test.js
src/test/js/ragKnowledgeBasePage.test.js
src/test/js/manualReplySubjectPrefill.test.js
src/test/js/trustReplyWorkbenchSharedMount.test.js
src/test/js/checkRepliesRelocation.test.js
src/test/js/batchSendTaskConsoleVisualFix.test.js
```

### draft_writes

```sh
rg -n 'setDraft\(|draftsMap\.(set|delete)|drafts\.(set|delete)|sessionStore\.(set|delete|clear)|deleteDraft\(|getDraft\(' src/main/resources/static/mailbox-chat.js
```

```text
363:        sessionStore.delete(key);
364:        sessionStore.set(key, rec);
371:            sessionStore.delete(key);
372:            sessionStore.set(key, rec);
408:        sessionStore.delete(key);
409:        sessionStore.set(key, rec);
420:            if (oldestKey !== null && oldestKey !== key) sessionStore.delete(oldestKey);
426:        sessionStore.delete(conversationCacheKey(user, accountScope, contactId));
557:        function getDraft(targetKey) {
563:        function setDraft(targetKey, draft) {
566:            drafts.set(targetKey, draft);
569:        function deleteDraft(targetKey) {
571:            if (drafts && targetKey) drafts.delete(targetKey);
2526:            const draft = targetKey != null ? getDraft(targetKey) : null;
3217:            const existing = getDraft(key);
3249:            setDraft(key, {
3292:            const existing = getDraft(key);
3299:                setDraft(key, Object.assign({}, existing || {}, {
3392:            const draft = getDraft(key);
3620:            const draft = getDraft(key);
3852:            const draft = getDraft(key);
3894:            const existing = getDraft(key);
3907:            setDraft(key, next);
3922:            const draft = getDraft(key);
3992:                const existing = getDraft(key);
3993:                setDraft(key, Object.assign({}, existing || {}, {
4107:            const draftSnapshot = getDraft(key);
4214:                        draftsMap.delete(key);
4225:                        draftsMap.set(key, nextDraft);
4230:                deleteDraft(key);
4311:            const draft = currentTargetKey() ? getDraft(currentTargetKey()) : null;
4333:            const draft = oldKey ? getDraft(oldKey) : null;
4349:                setDraft(newKey, migrated);
4350:                if (oldKey && oldKey !== newKey) deleteDraft(oldKey);
```

### attachment_consumers

```sh
rg -n 'MailAttachmentRepository|mail_attachment' src/main/kotlin --glob '*.kt'
```

```text
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt:8: * mail_attachment_transfer：一行 = 一个远端 part（唯一源身份）与其有界下载状态。
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt:18:@Table("mail_attachment_transfer")
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt:22:    /** MATERIAL 必填（一个附件至多一行，uk_mail_attachment_transfer_attachment）；
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt:7:@Table("mail_attachment")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:12:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:28: * 从事件（mail_record / bounce_record / mail_attachment）反推每位联系人的**期望** operator_status
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:44:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt:170:     *   附件经 MailAttachmentService.saveInboundAttachments 以 mailRecordId 落 mail_attachment）
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:10:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:52:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:717:            SELECT attachment_id FROM mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:802:              JOIN mail_attachment a ON a.id = d.mail_attachment_id
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:803:              LEFT JOIN mail_attachment_transfer t ON t.attachment_id = a.id
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:811:              FROM mail_attachment a
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:817:                      WHERE d.mail_attachment_id = a.id)
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:823:              FROM mail_attachment a
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt:829:                      WHERE d.mail_attachment_id = a.id)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:20:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt:32:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:5:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt:42:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:10:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt:35:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt:44: * MATERIAL 是内建终局（落盘 + 回写 mail_attachment + STORED），不经过消费者。
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt:294:    // MATERIAL：下载 -> .part -> 原子转正 -> 事务提交（回写 mail_attachment）
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt:495:                    // mail_attachment 无 updated_at 列（V7 只有 created_at）。
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt:498:                        UPDATE mail_attachment
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:9:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:25: *   uk_mail_attachment_transfer_source）约束内创建 mail_attachment（已匹配时
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:38:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:196:     * metadata 附件登记：在 transfer 源唯一身份内幂等创建 mail_attachment +
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt:199:     * uk_mail_attachment_transfer_source 兜底（冲突即本信失败回滚，重试收敛为
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentRepository.kt:6:interface MailAttachmentRepository : CrudRepository<MailAttachment, Long> {
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:7:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt:45:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt:5:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt:35:    private val attachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:10: * mail_attachment_transfer 的全部状态写路径：
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:13: *   登记并发唯一性由 uk_mail_attachment_transfer_source 兜底。
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:42:        SELECT * FROM mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:50:    @Query("SELECT COUNT(*) FROM mail_attachment_transfer WHERE state = 'QUEUED'")
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:55:        SELECT COUNT(*) FROM mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:63:        SELECT COUNT(*) FROM mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:72:        SELECT DISTINCT purpose FROM mail_attachment_transfer WHERE state = 'QUEUED'
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:81:        UPDATE mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:102:        UPDATE mail_attachment_transfer t
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:115:                    SELECT 1 FROM mail_attachment_transfer a
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:119:                    SELECT 1 FROM mail_attachment_transfer a
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:140:        UPDATE mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:162:        UPDATE mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:187:        UPDATE mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt:215:        UPDATE mail_attachment_transfer
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:498:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:529:                   SELECT 1 FROM mail_attachment ma
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:766:                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:805:                   SELECT 1 FROM mail_attachment ma
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:13:import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:26:    private val mailAttachmentRepository: MailAttachmentRepository,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt:203:     * LEFT JOIN expert_contact / EXISTS(mail_attachment)）。
```

### new_names

```sh
rg -n 'meeting_calendar_event|outbound_mail_attachment|outbound_attachments_json' src/main
```

```text
(0 matches)\n```

### flyway_pins

```sh
rg -n 'targetSchemaVersion|assertEquals\(\"124\"' src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt
```

```text
62:        assertEquals("124", flyway.migrate().targetSchemaVersion)
81:        assertEquals("124", flyway().migrate().targetSchemaVersion)
116:        assertEquals("124", flyway.migrate().targetSchemaVersion)
175:        assertEquals("116", v116Flyway.migrate().targetSchemaVersion)
227:        assertEquals("124", flyway().migrate().targetSchemaVersion)
330:        assertEquals("23", v23Flyway.migrate().targetSchemaVersion)
334:        assertEquals("124", flyway().migrate().targetSchemaVersion)
346:        assertEquals("24", v24Flyway.migrate().targetSchemaVersion)
350:        assertEquals("124", flyway().migrate().targetSchemaVersion)
385:        assertEquals("124", flyway().migrate().targetSchemaVersion)
431:        assertEquals("124", flyway().migrate().targetSchemaVersion)
507:        assertEquals("124", flyway().migrate().targetSchemaVersion)
623:        assertEquals("124", flyway().migrate().targetSchemaVersion)
768:        assertEquals("124", flyway().migrate().targetSchemaVersion)
858:        assertEquals("124", flyway().migrate().targetSchemaVersion)
1035:        assertEquals("124", flyway().migrate().targetSchemaVersion)
```

### assets

```sh
rg -n '\?v=' src/main/resources/static/index.html
```

```text
11:    <link rel="stylesheet" href="styles.css?v=20260914-followup-email">
12:    <link rel="stylesheet" href="expert-materials.css?v=20260914-followup-email">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20260914-followup-email">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20260914-followup-email">
2110:<script src="trust-reply-workbench.js?v=20260914-followup-email"></script>
2111:<script src="expert-materials.js?v=20260914-followup-email"></script>
2112:<script src="meeting-confirmation.js?v=20260914-followup-email"></script>
2113:<script src="mailbox-chat.js?v=20260914-followup-email"></script>
2114:<script src="app.js?v=20260914-followup-email"></script>
```
