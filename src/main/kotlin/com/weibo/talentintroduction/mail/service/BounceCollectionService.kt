package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import javax.mail.internet.InternetAddress

@Service
class BounceCollectionService(
    private val mailReceiveService: ImapMailReceiveService,
    private val bounceDetector: BounceDetector,
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertEmailAliasService: ExpertEmailAliasService,
    // 尾部可空默认参数：既有 BounceCollectionServiceTest / BounceBackfillServiceTest 以位置/具名参数
    // 直接构造，加默认值后无需改动；生产由 Spring 注入（I-3 挂载点）。
    private val expertOperatorStatusService: ExpertOperatorStatusService? = null,
    /**
     * I-1：`ingest` 判定「唯一 OUTBOUND 记录的发件账号是否属于当前物理组」时读取账号表
     * （02 已有的 `listAccounts` + `inbound_mailbox_code`），不新增持久化字段。未注入时
     * 退化为「只有传入账号自身属于本组」，即保持旧归属行为。
     */
    private val mailSenderAccountService: MailSenderAccountService? = null
) {
    private val log = LoggerFactory.getLogger(BounceCollectionService::class.java)

    fun collectBounces(
        account: MailSenderAccount,
        afterUid: Long = 0,
        expectedUidValidity: Long? = null
    ): BounceCollectionResult {
        var collected = 0
        var skippedDuplicate = 0

        val messages = mailReceiveService.fetchUnseenMessages(
            account, afterUid = afterUid, expectedUidValidity = expectedUidValidity
        )
        for (message in messages) {
            val from = message.from
                ?.filterIsInstance<InternetAddress>()
                ?.firstOrNull()
                ?.address
            val subject = message.subject
            val contentType = message.contentType

            val signal = if (contentType?.contains("report-type=delivery-status", ignoreCase = true) == true) {
                bounceDetector.parseBounceDetails(message)
                    ?: bounceDetector.detect(from, subject, message.content?.toString())
            } else {
                bounceDetector.parseBounceDetails(message)
            } ?: continue

            val messageIdHeader = message.getHeader("Message-ID")?.firstOrNull()
            val receivedAt = message.receivedDate
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?: LocalDateTime.now()

            when (
                ingest(
                    signal = signal,
                    senderAccountCode = account.accountCode,
                    bounceMessageId = messageIdHeader,
                    from = from,
                    subject = subject,
                    receivedAt = receivedAt
                )
            ) {
                BounceIngestResult.INGESTED -> collected++
                BounceIngestResult.DUPLICATE -> skippedDuplicate++
            }
        }

        return BounceCollectionResult(collected = collected, skippedDuplicate = skippedDuplicate)
    }

    fun ingest(
        signal: BounceSignal,
        senderAccountCode: String,
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime
    ): BounceIngestResult = ingest(
        signal,
        senderAccountCode,
        bounceMessageId,
        from,
        subject,
        receivedAt,
        preservePassedInAttribution = false
    )

    fun ingestKnownLogicalAccount(
        signal: BounceSignal,
        senderAccountCode: String,
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime
    ): BounceIngestResult = ingest(
        signal,
        senderAccountCode,
        bounceMessageId,
        from,
        subject,
        receivedAt,
        preservePassedInAttribution = true
    )

    private fun ingest(
        signal: BounceSignal,
        senderAccountCode: String,
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime,
        preservePassedInAttribution: Boolean
    ): BounceIngestResult {
        val dedupeKey = resolveBounceMessageId(bounceMessageId, from, subject, receivedAt)
        if (bounceRecordRepository.existsByBounceMessageId(dedupeKey)) {
            return BounceIngestResult.DUPLICATE
        }

        // I-1：归属只凭唯一 OUTBOUND 原始发信证明。删除邮箱抓取传入 owner（物理来源），
        // 回填传入已知逻辑账号；二者都只作「保持传入归属」的兜底值，绝不据此推断别名。
        val outboundCandidates = findOutboundCandidates(signal.originalMessageId)
        val attributedAccountCode = if (preservePassedInAttribution) {
            senderAccountCode
        } else {
            resolveSenderAccountCode(senderAccountCode, outboundCandidates, signal.originalMessageId)
        }
        val originalContact = resolveOriginalContact(signal, outboundCandidates)

        bounceRecordRepository.save(
            BounceRecord(
                senderAccountCode = attributedAccountCode,
                bounceMessageId = dedupeKey,
                originalMessageId = signal.originalMessageId,
                originalExpertContactId = originalContact?.id,
                failedRecipient = signal.failedRecipient,
                bounceType = signal.bounceType,
                dsnStatus = signal.dsnStatus,
                bounceReason = signal.reason,
                receivedAt = receivedAt,
                createdAt = LocalDateTime.now()
            )
        )

        if (signal.bounceType == "HARD" && originalContact != null) {
            // I-3：先落 MySQL + ES（唯一写入口 markEmailInvalid），再增量写 reachability
            try {
                expertOperatorStatusService?.markEmailInvalid(originalContact, "HARD_BOUNCE")
            } catch (e: Exception) {
                log.warn("Failed to mark EMAIL_INVALID for orcid={}", originalContact.orcidId, e)
            }
        }

        log.debug(
            "Ingested bounce {} for account {} type={}",
            dedupeKey,
            senderAccountCode,
            signal.bounceType
        )
        return BounceIngestResult.INGESTED
    }

    fun resolveBounceMessageId(
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime
    ): String {
        if (!bounceMessageId.isNullOrBlank()) {
            return MailMessageIdNormalizer.normalize(bounceMessageId)
        }
        val input = "${from.orEmpty()}|${subject.orEmpty()}|$receivedAt"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "NOID:$hex"
    }

    /**
     * I-1：`originalMessageId` 的标准化候选逐个查唯一允许的只读查询
     * [MailRecordRepository.findOutboundCandidatesByMessageId]（仅 `direction='OUTBOUND'`），
     * 按 id 去重后保持「候选变体顺序 + 查询 ORDER BY id」的确定性顺序；空列表与多条都表示
     * 不能唯一归属。
     */
    private fun findOutboundCandidates(originalMessageId: String?): List<MailRecord> {
        if (originalMessageId.isNullOrBlank()) return emptyList()
        val byId = mutableMapOf<Long, MailRecord>()
        for (candidate in MessageIdNormalizer.candidatesFor(originalMessageId)) {
            for (record in mailRecordRepository.findOutboundCandidatesByMessageId(candidate)) {
                val id = record.id ?: continue
                byId.getOrPut(id) { record }
            }
        }
        return byId.values.toList()
    }

    /**
     * I-1：只有「唯一 OUTBOUND 候选且其 `sender_account_code` 属于当前物理组」才改写
     * `bounce_record.sender_account_code`；无候选、多候选、候选无发件账号或来自其它物理组
     * 一律保持传入归属并记录可检索警告（不推断别名、不拿 `failedRecipient` 专家邮箱当发件账号）。
     */
    private fun resolveSenderAccountCode(
        passedInAccountCode: String,
        outboundCandidates: List<MailRecord>,
        originalMessageId: String?
    ): String {
        if (outboundCandidates.isEmpty()) {
            return keepPassedInAttribution(
                passedInAccountCode, "NO_OUTBOUND_RECORD", originalMessageId, outboundCandidates.size
            )
        }
        if (outboundCandidates.size > 1) {
            return keepPassedInAttribution(
                passedInAccountCode, "AMBIGUOUS_OUTBOUND_RECORD", originalMessageId, outboundCandidates.size
            )
        }
        val candidateCode = outboundCandidates.single().senderAccountCode
        if (candidateCode == null || candidateCode.isBlank()) {
            return keepPassedInAttribution(
                passedInAccountCode, "OUTBOUND_WITHOUT_SENDER_ACCOUNT", originalMessageId, outboundCandidates.size
            )
        }
        if (candidateCode !in groupMemberCodes(passedInAccountCode)) {
            return keepPassedInAttribution(
                passedInAccountCode, "OUTBOUND_OUTSIDE_GROUP", originalMessageId, outboundCandidates.size
            )
        }
        log.debug(
            "Bounce attributed to {} from unique OUTBOUND record (passedIn={}, originalMessageId={})",
            candidateCode,
            passedInAccountCode,
            originalMessageId
        )
        return candidateCode
    }

    private fun keepPassedInAttribution(
        passedInAccountCode: String,
        reason: String,
        originalMessageId: String?,
        outboundCandidateCount: Int
    ): String {
        log.warn(
            "Bounce attribution unresolved: reason={} passedInAccountCode={} originalMessageId={} " +
                "outboundCandidates={}; keeping passed-in account",
            reason,
            passedInAccountCode,
            originalMessageId,
            outboundCandidateCount
        )
        return passedInAccountCode
    }

    /**
     * I-1/I-2：传入账号所属物理组（`inbound_mailbox_code ?: account_code` 相同的全部逻辑账号）
     * 的账号代码集合，恒含传入账号自身。未注入账号服务或账号表查不到该账号时退化为
     * 「只有传入账号」，即只允许与传入账号完全相同的候选改写归属。
     */
    private fun groupMemberCodes(accountCode: String): Set<String> {
        val accounts = mailSenderAccountService?.listAccounts() ?: return setOf(accountCode)
        val groupKey = accounts.firstOrNull { it.accountCode == accountCode }?.inboundMailboxCode
            ?: accountCode
        return accounts
            .filter { (it.inboundMailboxCode ?: it.accountCode) == groupKey }
            .map { it.accountCode }
            .toSet() + accountCode
    }

    /**
     * 原始专家关联同样只读唯一允许的 OUTBOUND 候选（与归属同一次查询结果，保持二者口径一致）；
     * 无 OUTBOUND 候选时保留旧后备 `failedRecipient` 查专家，但绝不拿它猜发件账号。
     */
    private fun resolveOriginalContact(signal: BounceSignal, outboundCandidates: List<MailRecord>) =
        outboundCandidates.firstOrNull()
            ?.let { mailRecord ->
                expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
            }
            ?: signal.failedRecipient?.let { recipient ->
                expertEmailAliasService.findContactByEmailOrAlias(recipient)
            }
}

enum class BounceIngestResult {
    INGESTED,
    DUPLICATE
}

data class BounceCollectionResult(
    val collected: Int,
    val skippedDuplicate: Int
)
