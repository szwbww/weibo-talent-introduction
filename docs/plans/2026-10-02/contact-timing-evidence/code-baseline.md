# 代码证据与前端改动前基线

以下均为当前工作树逐字摘录；行号以检索当时为准。实现时先核对散列/方法名，禁止照搬过期行号。

## src/main/kotlin/com/weibo/talentintroduction/config/TimeZoneConfig.kt:1–14

SHA256：`90e5dae8b66bbb4d50e56390088ba49ae51eeb5955df3f2033899e5ce6d77ce1`

```text
package com.weibo.talentintroduction.config

import org.springframework.context.annotation.Configuration
import java.util.TimeZone
import javax.annotation.PostConstruct

@Configuration
class TimeZoneConfig {
    @PostConstruct
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }
}
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt:337–345

SHA256：`5e15a545fe4df57539833bfb32b8b0d650e2ef5e4f928203c8f3c38c83a5b16c`

```text
            inReplyTo = headers.inReplyTo,
            recipientAddresses = parseTopLevelRecipients(headers.to, headers.cc),
            receivedAt = message.receivedDate
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?: LocalDateTime.now(),
            attachments = extracted.attachments,
            uidValidity = uidValidity,
```

## src/main/resources/db/migration/V1__create_business_tables.sql:80–102

SHA256：`944a8c45d9005871df30db4fb527320b191d943aff7c6a3a290e7f84d33d2235`

```text
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    orcid_id VARCHAR(64) NOT NULL,
    expert_email VARCHAR(255) NOT NULL,
    expert_name VARCHAR(255),
    current_status VARCHAR(64) NOT NULL DEFAULT 'NEW',
    last_mail_at DATETIME,
    last_reply_at DATETIME,
    manual_handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    closed_reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id),
    CONSTRAINT fk_expert_contact_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign(id)
);

CREATE TABLE mail_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    mail_type VARCHAR(64) NOT NULL,
    message_id VARCHAR(255),
```

## src/main/resources/db/migration/V48__add_country_to_expert_contact.sql:1–2

SHA256：`401d6ee8205e9ced7f4c9384e3a3d41c68fceeea00e6b4cafd314d5ca18b5be5`

```text
ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;
CREATE INDEX idx_expert_contact_country ON expert_contact (country);
```

## src/main/resources/db/migration/V5__create_inbound_mail_processing.sql:1–22

SHA256：`f7d08b96902eeed587f7b3a8f0e08ab68d514f0515458c56f0f7a3206d4299d0`

```text
CREATE TABLE inbound_mail_processing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_account_code VARCHAR(64) NOT NULL,
    imap_uid BIGINT NOT NULL,
    message_id VARCHAR(255),
    from_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    received_at DATETIME NOT NULL,
    process_status VARCHAR(32) NOT NULL,
    process_reason VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid),
    KEY idx_inbound_mail_processing_status (process_status, received_at),
    CONSTRAINT fk_inbound_mail_processing_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
```

## src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql:17–24

SHA256：`5e8c390b1d30b7285e9a164beefc50df6b4937ccdf81ecc54e8559b246a90a4f`

```text
ALTER TABLE inbound_mail_processing
    ADD COLUMN uid_validity BIGINT NOT NULL DEFAULT 0 COMMENT '远端邮箱代际；0 仅历史未知，新接收必须为实际正值';

ALTER TABLE inbound_mail_processing
    DROP INDEX uk_inbound_mail_processing_uid,
    ADD UNIQUE KEY uk_inbound_mail_processing_uid_validity (sender_account_code, uid_validity, imap_uid);
```

## src/main/resources/db/migration/V134__shared_inbox_owner.sql:28–34

SHA256：`a54c78f0fd731b8aa55462c12511a00cc27a82267718ea6e88e7cd3b7533835d`

```text
ALTER TABLE inbound_mail_processing
    ADD COLUMN mailbox_owner_code VARCHAR(64) NULL
        COMMENT '物理收件箱主账号代码；NULL=历史未知行（不回填）';

ALTER TABLE inbound_mail_processing
    ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1307–1335

SHA256：`4b5f2bbac3c20f4278674e04ef21f4c47350fe3aeb8fb6337d9a1042757db78a`

```text
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                // I-3：物理 owner 由逻辑账号唯一决定（单层归属），新行必须带物理键，
                // 唯一键 (owner, uid_validity, imap_uid) 才能约束并发重复。
                mailboxOwnerCode = mailSenderAccountService.resolveInboundOwner(account).accountCode,
                uidValidity = received.uidValidity,
                imapUid = received.imapUid,
                messageId = received.messageId,
                inReplyTo = received.inReplyTo,
                fromEmail = received.from,
                subject = received.subject,
                body = received.body,
                cleanedBody = cleanedBody,
                receivedAt = received.receivedAt,
                processStatus = "MANUAL_REVIEW",
                processReason = reason,
                reasonType = reasonType,
                expertContactId = expertContactId,
                createdAt = now,
                updatedAt = now
            )
        )
        applyAutoTags(saved, cleanedBody, received.body)
        val savedId = saved.id ?: error("Inbound mail processing id is required")
        if (bridgeMetadata) {
            mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
        }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:1363–1392

SHA256：`4b5f2bbac3c20f4278674e04ef21f4c47350fe3aeb8fb6337d9a1042757db78a`

```text
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                // I-3：同 confirmManualReviewWithBody —— 新行必须带物理 owner 键。
                mailboxOwnerCode = mailSenderAccountService.resolveInboundOwner(account).accountCode,
                uidValidity = received.uidValidity,
                imapUid = received.imapUid,
                messageId = received.messageId,
                inReplyTo = received.inReplyTo,
                fromEmail = received.from,
                subject = received.subject,
                body = body ?: received.body,
                cleanedBody = cleanedBody,
                receivedAt = received.receivedAt,
                processStatus = status,
                processReason = reason,
                reasonType = reasonType,
                expertContactId = expertContactId,
                createdAt = now,
                updatedAt = now
            )
        )
        applyAutoTags(saved, cleanedBody, body ?: received.body)
        val savedId = saved.id ?: error("Inbound mail processing id is required")
        if (bridgeMetadata) {
            mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
        }
        return saved
    }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt:873–906

SHA256：`4b5f2bbac3c20f4278674e04ef21f4c47350fe3aeb8fb6337d9a1042757db78a`

```text
                val bounceSignal = bounceDetector.detect(mail.from, mail.subject, mail.body)
                if (bounceSignal != null) {
                    bounceCollectionService.ingest(
                        signal = bounceSignal,
                        senderAccountCode = account.accountCode,
                        bounceMessageId = mail.messageId,
                        from = mail.from,
                        subject = mail.subject,
                        receivedAt = mail.receivedAt
                    )
                    mailReceiveService.markSeen(account, mail.imapUid)
                    log.debug("Ingested bounce during auto-reply poll: uid={}", mail.imapUid)
                    handledUids.add(mail.imapUid)
                    continue
                }
                if (dmarcReportDetector.isDmarcAggregateReport(mail.from, mail.subject, mail.attachments)) {
                    // I-3（metadata 模式）：content=null 的附件不能交给原 ingest（无字节可解压，
                    // parse-null 会被静默跳过 = 丢报表）。改经 02 队列登记 SYSTEM 的 DMARC 获取请求
                    // （purpose=DMARC、无 attachmentId、无专家附件/文档），不在检查线程等待下载/解析；
                    // 每个源索引行持久化且明确入队后才确认该 UID。legacy 模式保持原内联 ingest。
                    val attachments = mail.attachments
                    if (attachments.isNotEmpty() && attachments.all { it.content == null }) {
                        queueDmarcTransfers(account, mail, attachments)
                    } else {
                        try {
                            dmarcReportIngestService.ingest(attachments)
                        } catch (e: Exception) {
                            log.warn("DMARC parse failed uid={}", mail.imapUid, e)
                        }
                    }
                    mailReceiveService.markSeen(account, mail.imapUid)
                    handledUids.add(mail.imapUid)
                    continue
                }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:191–205

SHA256：`183c75f7ac2f961eb1d36f49517d64af9346b41837fce788013f7d0cba99ceaa`

```text

        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            record.copy(
                expertContactId = contactId,
                processStatus = "MANUAL_REVIEW",
                processReason = "MANUAL_BOUND",
                resolvedBy = null,
                resolvedAt = null,
                updatedAt = now
            )
        )

        // 04（I-3/I-4）：开关启用时，按已有 processing-owner 附件幂等补 ExpertDocument——
        // 复用同一 attachmentId、零文件 I/O、不搬迁 owner/路径、不改 review/document
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt:235–247

SHA256：`183c75f7ac2f961eb1d36f49517d64af9346b41837fce788013f7d0cba99ceaa`

```text
            .orElseThrow { error("Inbound mail processing not found: $recordId") }
        require(record.processStatus == "MANUAL_REVIEW") { "Record $recordId is not in MANUAL_REVIEW" }
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(record.copy(
            processStatus = "PROCESSED",
            processReason = "MANUAL_RESOLVED",
            reasonType = "MANUAL_RESOLVED",
            resolvedBy = resolvedBy,
            resolvedAt = now,
            updatedAt = now
        ))

        val contactId = record.expertContactId
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:1522–1536

SHA256：`1735fdbf9c300156fa9c510fce9bf8c94012331dc58d09a98fae30bdc5a86462`

```text
        val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: resolvedBy ?: "UNKNOWN"
        val now = LocalDateTime.now()
        inboundMailProcessingRepository.save(
            record.copy(
                processStatus = "PROCESSED",
                processReason = "MANUAL_RESOLVED",
                reasonType = "MANUAL_RESOLVED",
                resolvedBy = actualOperator,
                resolvedAt = now,
                updatedAt = now
            )
        )

        val contactId = record.expertContactId
        if (contactId != null) {
```

## src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt:48–62

SHA256：`f04f15560af634f9d17f351217ae5af84407625411555e5030d68ae0177c842d`

```text
    @Modifying
    @Query("""
        UPDATE inbound_mail_processing
           SET process_status = 'MANUAL_REVIEW',
               process_reason = 'MANUAL_REOPENED',
               reason_type = NULL,
               resolved_at = NULL,
               resolved_by = NULL,
               updated_at = :now
         WHERE id = :id
           AND process_status = 'PROCESSED'
           AND process_reason = 'MANUAL_RESOLVED'
           AND reason_type = 'MANUAL_RESOLVED'
    """)
    fun reopenManualResolved(id: Long, now: LocalDateTime): Int
```

## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt:434–499

SHA256：`f5a69f1d2ac2d9393ab4f04d8de77aa6b5eb582c982310ac1d4c5a54af6c8a1b`

```text
     * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
     * 两个分支的文本列显式 CONVERT + COLLATE utf8mb4_unicode_ci，避免 UNION 排序/物化
     * 时字符集冲突。includeBody 仅 timeline 需要（summary 永不读正文列）。
     * 注意：本 SQL 片段同时作为 count/summary/latest 系列/accountCodes/timeline 的数据基础，
     * 各列位置与别名必须保持一致。
     */
    private fun rangeUnionSql(includeBody: Boolean): String {
        val outboundBody = if (includeBody) {
            """,
                   CONVERT(mr.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
                   CONVERT(mr.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
        } else ""
        val inboundBody = if (includeBody) {
            """,
                   CONVERT(imp.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
                   CONVERT(imp.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
        } else ""
        return """
            SELECT mr.expert_contact_id AS expert_contact_id,
                   1 AS source_rank,
                   CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                   mr.id AS id,
                   CONVERT('OUTBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                   CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
                   COALESCE(mr.sent_at, mr.created_at) AS event_at,
                   CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                   CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                   CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                   CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
                   CONVERT(mr.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
                   CONVERT(mr.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
                   CASE WHEN mr.send_status = 'SENT' THEN 1 ELSE 0 END AS sent_flag,
                   CASE WHEN mr.send_status = 'FAILED' THEN 1 ELSE 0 END AS failed_flag,
                   0 AS received_flag,
                   0 AS pending_flag$outboundBody
              FROM mail_record mr
             WHERE mr.direction = 'OUTBOUND'
               AND mr.expert_contact_id IS NOT NULL
               AND mr.sender_account_code IN (:accountCodes)
               AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
            UNION ALL
            SELECT imp.expert_contact_id AS expert_contact_id,
                   2 AS source_rank,
                   CONVERT('INBOUND_PROCESSING' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                   imp.id AS id,
                   CONVERT('INBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                   CONVERT(imp.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
                   imp.received_at AS event_at,
                   CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                   CONVERT(imp.process_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                   CONVERT(imp.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                   CONVERT(SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
                   CONVERT(imp.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
                   CONVERT(imp.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
                   0 AS sent_flag,
                   0 AS failed_flag,
                   1 AS received_flag,
                   CASE WHEN imp.process_status = 'MANUAL_REVIEW' THEN 1 ELSE 0 END AS pending_flag$inboundBody
              FROM inbound_mail_processing imp
             WHERE imp.expert_contact_id IS NOT NULL
               AND imp.sender_account_code IN (:accountCodes)
               AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
        """.trimIndent()
    }

    /** 消息级筛选在聚合口径上完全不可满足的组合（label 只存在于 INBOUND 侧）。 */
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:527–532

SHA256：`50de05bc351870ef47d2661ebe9d98a6290ca8c6494b709f0ec5040f89dffb40`

```text

    /** 活跃发件账号（排除模拟器），与 MailboxService 同一口径。 */
    fun activeAccountCodes(): List<String> =
        senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            .map { it.accountCode }
}
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertRepliedDismissalService.kt:1–55

SHA256：`f4dc806675897f49496bfd73db87b64f7a106b9486e96ba94fa9f555c03c24cc`

```text
package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class RepliedDismissalResult(val dismissed: Boolean)

/** 记录当前用户已看过并移出的最新来信；新来信的自增 id 会重新激活已回复分组。 */
@Service
class ExpertRepliedDismissalService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun dismiss(username: String, expertContactId: Long): RepliedDismissalResult {
        require(username.isNotBlank()) { "未登录" }
        val params = MapSqlParameterSource()
            .addValue("username", username)
            .addValue("contactId", expertContactId)
        val contactCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            params,
            Long::class.java
        ) ?: 0L
        if (contactCount == 0L) throw NoSuchElementException("Expert contact not found: $expertContactId")
        val latestInboundId = jdbcTemplate.queryForObject(
            "SELECT MAX(id) FROM inbound_mail_processing WHERE expert_contact_id = :contactId",
            params,
            Long::class.java
        ) ?: throw IllegalArgumentException("该专家尚无来信")
        params.addValue("lastInboundId", latestInboundId)
            .addValue("dismissedAt", LocalDateTime.now())
        jdbcTemplate.update(
            """
            INSERT INTO expert_replied_dismissal
                (username, expert_contact_id, last_inbound_id, dismissed_at)
            VALUES (:username, :contactId, :lastInboundId, :dismissedAt)
            ON DUPLICATE KEY UPDATE
                last_inbound_id = GREATEST(last_inbound_id, VALUES(last_inbound_id)),
                dismissed_at = VALUES(dismissed_at)
            """.trimIndent(),
            params
        )
        return RepliedDismissalResult(true)
    }
}
```

## src/main/kotlin/com/weibo/talentintroduction/campaign/service/ContactCountryBackfillService.kt:48–76

SHA256：`e1927934896f32a24d2e402710669c3f6a176acf906bc9d0d7d080b1cf86baa5`

```text
                val level = toExpertIndexLevel(indexLevel) ?: return@forEach
                contacts.map { ExpertIdNormalizer.normalize(it.orcidId) }
                    .distinct()
                    .chunked(ORCID_BATCH_SIZE)
                    .forEach { orcidBatch ->
                        expertSearchService.searchByOrcidIds(orcidBatch, level)
                            .forEach { profile ->
                                countryByOrcid[ExpertIdNormalizer.normalize(profile.orcidId)] = profile.country
                            }
                    }
            }

        var matched = 0
        var unmatched = 0
        pending.forEach { contact ->
            val contactId = contact.id
            if (contactId == null) {
                log.warn("Skipping contact without id, orcidId={}", contact.orcidId)
                unmatched++
                return@forEach
            }
            val country = countryByOrcid[ExpertIdNormalizer.normalize(contact.orcidId)]
            if (country != null) {
                matched++
            } else {
                unmatched++
            }
            expertContactRepository.updateCountryById(contactId, country)
        }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:52–76

SHA256：`9395f20c13064497a467e7b0f47a132f77d12e9649074eb302ef2bc40fe07129`

```text
 */
data class ConversationItemResponse(
    val contactId: Long,
    val name: String?,
    val email: String,
    val orcid: String,
    /** expert_contact 无机构列（DB 无 institution 存储）；恒 null，UI 自专家资料流程补充。 */
    val institution: String?,
    val accountCodes: List<String>,
    val followed: Boolean,
    val receivedCount: Long,
    val sentCount: Long,
    val failedCount: Long,
    val pendingCount: Long,
    val waitingReply: Boolean,
    val latestMessage: ConversationLatestMessageItem?,
    val latestInbound: ConversationLatestInboundItem?,
    val materialCount: Long,
    val expertTags: List<String>? = null
)

/**
 * 会话级人工回信请求（无来信路径，T3/I-4/I-6）：携带前端生成并随草稿保存的 requestId
 * （attempt 短键来源）与可空 accountScope（只约束服务端锚点查询，不直接决定发件账号）；
 * 不接收 senderAccountCode、qaRuleIds、RAG 或 assembly 字段 —— 联系人与锚点校验全部在
```

## src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:609–649

SHA256：`cf37f144f8fe8e9042065d52adbded29809a41732dc970359770d67afb9404bc`

```text
        private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
        private val LOCAL_DATETIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$""")
        private val DASH_RUN = Regex("-+")
        private val ZOOM_JOIN_PATH = Regex("""^/j/[^/]+$""")
        private val ZOOM_MY_PATH = Regex("""^/my/[^/]+$""")

        private val ALLOWED_ZOOM_HOSTS = listOf("zoom.us", "zoom.com", "zoom.com.cn")

        private val FULL_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val BASIC_UTC_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        private val WEEKDAY_ZH: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "周一",
            DayOfWeek.TUESDAY to "周二",
            DayOfWeek.WEDNESDAY to "周三",
            DayOfWeek.THURSDAY to "周四",
            DayOfWeek.FRIDAY to "周五",
            DayOfWeek.SATURDAY to "周六",
            DayOfWeek.SUNDAY to "周日"
        )

        private data class TimeZoneCatalogEntry(
            val labelZh: String,
            val aliases: List<String>
        )

        /**
         * 由 Unicode CLDR 生成并随应用发布的离线目录。运行时不联网；目录缺条目时
         * 显式失败，防止 JDK tzdata 新增时区后重新退化为英文 ID。
         */
        private val TIME_ZONE_CATALOG: Map<String, TimeZoneCatalogEntry> by lazy {
            val properties = Properties()
            MeetingConfirmationService::class.java.classLoader
                .getResourceAsStream("meeting-timezones-zh.properties")
                ?.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use(properties::load)
                }
```

## src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt:11–29

SHA256：`f41a744f244f28346dfa4f8c1da37abe2cfd795c496699edf5f9ac8679a78ebb`

```text
@Configuration
@ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)
class AuthWebConfig(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper
) : WebMvcConfigurer {

    @Bean
    fun authInterceptor(): AuthInterceptor {
        return AuthInterceptor(authService, objectMapper)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor())
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/login", "/api/auth/me")
    }
}
```

## src/main/resources/static/mailbox-chat.js:1808–1825

SHA256：`f82db572ce447c84adfb2986e09f919ca84ea8176e23cbb88e6b11512c32a6bd`

```text
        function renderHeaderMeta(metaEl) {
            if (!metaEl) return;
            const contact = instance.conversation.contact || null;
            const summary = instance.selectedSummary || {};
            const statusText = contact ? displayNameForCatalog(statusCatalog(), contact.operatorStatus) : "";
            const levelText = contact ? displayNameForCatalog(levelCatalog(), contact.currentIndexLevel) : "";
            const statusBadge = statusText ? `<span class="mc-badge" data-tone="success">${escapeText(statusText)}</span>` : "";
            const levelBadge = levelText ? `<span class="mc-badge">${escapeText(levelText)}</span>` : "";
            const orcid = (contact && contact.orcidId) || summary.orcid || "";
            const expertTagSpans = headerExpertTagSpans();
            metaEl.innerHTML = `
                ${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button>
            `;
        }

        function headerExpertTagSpans() {
            const contact = instance.conversation.contact || null;
            const orcidId = contact && (contact.orcidId || contact.expertOrcidId) ? String(contact.orcidId || contact.expertOrcidId) : "";
```

## src/main/resources/static/mailbox-chat.js:1627–1640

SHA256：`f82db572ce447c84adfb2986e09f919ca84ea8176e23cbb88e6b11512c32a6bd`

```text
        function teardownConversationSubViews() {
            closeManageOverlay({ restoreFocus: false });
            closeFollowUpDialog({ restoreFocus: false });
            closeMaterialRequestDialog({ restoreFocus: false });
            closeTemplateReferenceDialog({ restoreFocus: false });
            if (instance.workbench.instance) {
                try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
                instance.workbench.instance = null;
                instance.workbench.processingId = null;
            }
            teardownMeetingViews();
            const releaseMaterials = hostFn("unmountExpertMaterialsHosts");
            if (releaseMaterials && instance.host) releaseMaterials(instance.host);
        }
```

## src/main/resources/static/mailbox-chat.js:3043–3065

SHA256：`f82db572ce447c84adfb2986e09f919ca84ea8176e23cbb88e6b11512c32a6bd`

```text
        // --------------------------------------------------------------

        function createPortalRoot() {
            const doc = docRoot();
            if (!doc || !doc.body) return null;
            const root = doc.createElement("div");
            root.setAttribute("class", "mail-chat mc-overlay-root");
            root.setAttribute("data-role", "mc-overlay-root");
            doc.body.appendChild(root);
            return root;
        }

        function removePortalRoot() {
            const root = instance.elements.portalRoot;
            if (root && root.parentNode) root.parentNode.removeChild(root);
            instance.elements.portalRoot = null;
        }

        function ensurePortalRoot() {
            if (instance.elements.portalRoot) return instance.elements.portalRoot;
            const root = createPortalRoot();
            if (root) instance.elements.portalRoot = root;
            return root;
```

## src/main/resources/static/mailbox-chat.js:6569–6605

SHA256：`f82db572ce447c84adfb2986e09f919ca84ea8176e23cbb88e6b11512c32a6bd`

```text
        function unmount() {
            if (instance.disposed) return;
            saveConversationState();
            // 右栏/根节点清空前必须先归还详情面板 lease（I-5）。
            clearUnmatchedState();
            instance.disposed = true;
            clearTimeout(instance.searchTimer);
            clearTimeout(instance.saveTimer);
            teardownConversationSubViews();
            handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
            handlers.length = 0;
            portalHandlers.forEach((pair) => {
                const [root, type, fn] = pair;
                if (root && typeof root.removeEventListener === "function") root.removeEventListener(type, fn);
            });
            portalHandlers.length = 0;
            const doc = docRoot();
            if (doc && typeof doc.removeEventListener === "function") {
                doc.removeEventListener("click", onOutsideFilterClick);
                doc.removeEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
            }
            restoreRefreshButton();
            restoreLegacyFilterNodes();
            removePortalRoot();
            setRefined(false);
            if (instance.host) instance.host.innerHTML = "";
            instances.delete(instance.host);
        }

        function refreshFromHost() {
            if (instance.disposed) return;
            saveConversationState();
            return fetchList({ page: instance.list.page }).then((data) => {
                if (instance.disposed) return data;
                if (instance.selectedContactId != null) refreshConversationQuiet();
                return data;
            });
```

## src/main/resources/static/mailbox-chat.css:34–34

SHA256：`0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d`

```text
.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
```

## src/main/resources/static/mailbox-chat.css:95–111

SHA256：`0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d`

```text
.mail-chat .mc-field input,.mail-chat .mc-field select{width:100%;min-width:0;height:34px;min-height:34px;margin:0;padding:0 9px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#5f7390;font:inherit;font-size:12px}
.mail-chat .mc-filter-popover footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
.mail-chat .mc-filter-popover footer .mc-text-button{margin-right:auto}
.mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
.mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
.mail-chat .mc-close:active{background:#dbeafe}
.mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
.mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
.mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
.mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
.mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
.mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
.mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
.mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
.mail-chat .mc-text-button:active{background:#dbeafe}
```

## src/main/resources/static/styles.css:948–959

SHA256：`2dac0f6a5c30d13fb496cefbaee52a75371fcefd1e13241b3a15e72528071128`

```text
.panel {
    background: var(--panel-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-lg);
    box-shadow: var(--glass-shadow);
}

.panel:hover {
    box-shadow: var(--shadow-lg);
    border-color: rgba(15, 23, 42, 0.12);
```

## src/test/js/mailboxChatStyle.test.js:14–47

SHA256：`4c907ad5182a6c528a6729b31936690b10c38ad6b7640cdcffb69caecbe14985`

```text
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssPath = path.join(ROOT, "mailbox-chat.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");

const TARGET_CSS = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-09", "mailbox-refinement-evidence", "mailbox-chat.target.css");

// I-1：版本键唯一来源是 index.html 的 styles.css?v=<key>，本文件不得写死字面量。
const CACHE_KEY = (() => {
    const match = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

describe("S-6: 落地 CSS 与 02 evidence 逐字一致", () => {
    it("mailbox-chat.css 与 evidence/mailbox-chat.target.css 字节一致", () => {
        const expected = fs.readFileSync(TARGET_CSS, "utf-8");
        assert.strictEqual(cssSource, expected, "mailbox-chat.css 必须与 S-6 合同证据逐字一致");
    });
});

describe("S-1/S-2: index.html 宿主 id 与唯一筛选节点（源文本断言）", () => {
    function countOccurrences(text, needle) {
        let count = 0;
        let idx = text.indexOf(needle);
        while (idx !== -1) {
            count += 1;
```

## src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:87–98

SHA256：`3013db75ee0aa6e695ec006cb6d6b934d8561e12c67461dcf2cae1c7e22e70a7`

```text
        NoiseSite("com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt", 47, "operator_status = :operatorStatus"),
        // SELECT 列投影：读取列值供响应 DTO 使用
        // （2026-09-02 provider-undelivered 计划：MailRecordRepository 新增 DomainUndeliveredCount 投影
        //   与 aggregateUndeliveredByDomain 查询，:583 偏移至 :612，本计划授权行号修正）
        // （2026-09-23 共享收件箱计划 02：MailRecordRepository 在 :148 新增
        //   findOutboundCandidatesByMessageId 只读候选查询（+16 行），:612 平移至 :628，path/context 不变）
        NoiseSite("com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt", 628, "ec.operator_status AS operator_status"),
        // SELECT GROUP BY 列引用：只读聚合
        // （同上计划：:640 偏移至 :669，本计划授权行号修正）
        // （2026-09-23 共享收件箱计划 02：同一处 +16 行平移，:669 平移至 :685，path/context 不变）
        NoiseSite("com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt", 685, "ec.operator_status, ec.current_index_level")
    )
```

## pom.xml:186–207

SHA256：`58e5999fad9df89d6479045d5ea70d86c33ccf7591da3f93470669dc30b6f21f`

```text
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>node-test</id>
                        <phase>test</phase>
                        <goals>
                            <goal>exec</goal>
                        </goals>
                        <configuration>
                            <executable>bash</executable>
                            <arguments>
                                <argument>-lc</argument>
                                <argument>node --test src/test/js/*.test.js</argument>
                            </arguments>
                            <skip>${skipNodeTests}</skip>
                        </configuration>
                    </execution>
                    <execution>
                        <id>node-check-app</id>
                        <phase>test</phase>
                        <goals>
```
