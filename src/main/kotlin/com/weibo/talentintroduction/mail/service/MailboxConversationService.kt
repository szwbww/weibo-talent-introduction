package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.mail.controller.ConversationItemResponse
import com.weibo.talentintroduction.mail.controller.ConversationLatestInboundItem
import com.weibo.talentintroduction.mail.controller.ConversationLatestMessageItem
import com.weibo.talentintroduction.mail.controller.ConversationListResponse
import com.weibo.talentintroduction.mail.controller.ConversationMessageItemResponse
import com.weibo.talentintroduction.mail.controller.ConversationMessageListResponse
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationFilter
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationKeyset
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * 专家会话查询与关注的服务装配（fast-p 07）。GET 绝不调用 IMAP：
 * summary/timeline 全部来自 MySQL 归一化 UNION（I-1/I-4），仅当前专家 timeline
 * 才加载正文与附件元数据；附件元数据复用 06 的精确来源解析
 * （[ExpertMaterialService.resolveMessageAttachments]，与详情附件列表同一口径）。
 */
@Service
class MailboxConversationService(
    private val repository: MailboxConversationRepository,
    private val senderAccountRepository: MailSenderAccountRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val expertMaterialService: ExpertMaterialService
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val DEFAULT_MESSAGE_LIMIT = 50
        const val MAX_MESSAGE_LIMIT = 100
        const val MAX_TEXT_FILTER_LENGTH = 255
        const val FIRST_ATTACHMENT_NAME_LIMIT = 3

        private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
        private val CURSOR_SEPARATOR = "\u001F"
        private val SOURCE_RANK_OF = mapOf(
            MailboxConversationRepository.SOURCE_MAIL_RECORD to 1,
            MailboxConversationRepository.SOURCE_INBOUND_PROCESSING to 2
        )
        private val RANK_SOURCE_OF = SOURCE_RANK_OF.entries.associate { (k, v) -> v to k }
    }

    // ------------------------------------------------------------------
    // summary 列表
    // ------------------------------------------------------------------

    fun listConversations(
        username: String?,
        q: String?,
        followed: Boolean,
        pendingOnly: Boolean,
        waitingReply: Boolean,
        accountCode: String?,
        direction: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        subject: String?,
        label: String?,
        page: Int,
        size: Int
    ): ConversationListResponse {
        validateDirection(direction)
        validateTextFilter("q", q)
        validateTextFilter("subject", subject)
        validateTextFilter("label", label)
        val activeCodes = activeAccountCodes()
        if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
            return ConversationListResponse(emptyList(), 0, page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        }
        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val filter = ConversationFilter(
            accountCodes = activeCodes,
            accountCode = accountCode,
            q = q,
            followed = followed,
            waitingReply = waitingReply,
            pendingOnly = pendingOnly,
            direction = direction,
            startTime = startDate?.atStartOfDay(),
            endTime = endDate?.plusDays(1)?.atStartOfDay(),
            subject = subject,
            label = label
        )
        val sessionUser = username.orEmpty()
        val total = repository.countConversations(sessionUser, filter)
        if (total == 0L) {
            return ConversationListResponse(emptyList(), 0, pageIndex, pageSize)
        }
        val offset = pageIndex.toLong() * pageSize
        val rows = repository.pageConversations(sessionUser, filter, pageSize, offset)
        if (rows.isEmpty()) {
            return ConversationListResponse(emptyList(), total, pageIndex, pageSize)
        }
        val contactIds = rows.map { it.expertContactId }
        val latestMessages = repository.latestMessageByContacts(contactIds, activeCodes, accountCode)
        val latestInbounds = repository.latestInboundByContacts(contactIds, activeCodes, accountCode)
        val accountCodesByContact = repository.accountCodesByContacts(contactIds, activeCodes, accountCode)
        val materialCounts = repository.materialCountByContacts(contactIds)

        val items = rows.map { row ->
            ConversationItemResponse(
                contactId = row.expertContactId,
                name = row.expertName,
                email = row.expertEmail,
                orcid = row.orcidId,
                // expert_contact 无机构列（MySQL 无 institution 存储），此处恒 null，
                // 由前端从专家资料（ES 画像）展示；不伪造 DB 事实。
                institution = null,
                accountCodes = accountCodesByContact[row.expertContactId].orEmpty(),
                followed = row.followed,
                receivedCount = row.receivedCount,
                sentCount = row.sentCount,
                failedCount = row.failedCount,
                pendingCount = row.pendingCount,
                waitingReply = row.receivedCount == 0L && row.sentCount > 0L,
                latestMessage = latestMessages[row.expertContactId]?.let { m ->
                    ConversationLatestMessageItem(
                        source = m.source,
                        id = m.id,
                        direction = m.direction,
                        subject = m.subject,
                        preview = m.preview,
                        time = m.eventAt.format(ISO),
                        sendStatus = m.sendStatus
                    )
                },
                latestInbound = latestInbounds[row.expertContactId]?.let { i ->
                    ConversationLatestInboundItem(
                        processingId = i.processingId,
                        accountCode = i.accountCode,
                        messageId = i.messageId,
                        receivedAt = i.receivedAt.format(ISO)
                    )
                },
                materialCount = materialCounts[row.expertContactId] ?: 0L
            )
        }
        return ConversationListResponse(items, total, pageIndex, pageSize)
    }

    // ------------------------------------------------------------------
    // 单专家 timeline
    // ------------------------------------------------------------------

    fun listMessages(
        contactId: Long,
        accountCode: String?,
        beforeCursor: String?,
        limit: Int
    ): ConversationMessageListResponse {
        expertContactRepository.findById(contactId)
            .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
        val activeCodes = activeAccountCodes()
        val pageLimit = limit.coerceIn(1, MAX_MESSAGE_LIMIT)
        if (activeCodes.isEmpty() || (accountCode != null && accountCode !in activeCodes)) {
            return ConversationMessageListResponse(emptyList(), null, false)
        }
        val before = decodeKeyset(beforeCursor, contactId, accountCode)
        val filter = ConversationFilter(accountCodes = activeCodes, accountCode = accountCode)
        val page = repository.timelineMessages(contactId, filter, before, pageLimit)

        // 行按 DESC（新→旧）返回；正序呈现最新窗口。
        val items = page.rows.asReversed().map { row ->
            val attachments = expertMaterialService.resolveMessageAttachments(row.source, row.id)
            ConversationMessageItemResponse(
                source = row.source,
                id = row.id,
                contactId = row.expertContactId,
                direction = row.direction,
                accountCode = row.accountCode,
                subject = row.subject,
                body = row.body,
                cleanedBody = row.cleanedBody,
                eventAt = row.eventAt.format(ISO),
                sendStatus = row.sendStatus,
                processStatus = row.processStatus,
                attachmentCount = attachments.size,
                firstAttachmentNames = attachments.take(FIRST_ATTACHMENT_NAME_LIMIT).mapNotNull { it.fileName },
                messageId = row.messageId,
                inReplyTo = row.inReplyTo
            )
        }
        val nextBefore = if (page.hasMore) {
            // items 为正序；本窗口最旧的一条 = 边界（更早页 = 严格早于该键）。
            val boundary = items.firstOrNull()
            if (boundary != null) {
                encodeCursor(
                    contactId = contactId,
                    accountCode = accountCode,
                    item = boundary
                )
            } else null
        } else null
        return ConversationMessageListResponse(items, nextBefore, page.hasMore)
    }

    // ------------------------------------------------------------------
    // 游标（(time, source, id) 编码/校验；绑定 contact/account scope）
    // ------------------------------------------------------------------

    private fun encodeCursor(contactId: Long, accountCode: String?, item: ConversationMessageItemResponse): String {
        val sourceRank = SOURCE_RANK_OF[item.source]
            ?: throw IllegalStateException("Unknown message source for cursor: ${item.source}")
        // scope = 本次请求的 contact + accountCode（不是消息自己的账号）：
        // 同一 scope 翻页才能复用；跨专家/换账号过滤一律拒绝。
        val payload = listOf(
            contactId.toString(),
            accountCode.orEmpty(),
            item.eventAt,
            item.source,
            item.id.toString(),
            sourceRank.toString()
        ).joinToString(CURSOR_SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    private fun decodeKeyset(cursor: String?, contactId: Long, accountCode: String?): ConversationKeyset? {
        if (cursor.isNullOrBlank()) return null
        val decoded = runCatching {
            String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("无效的游标") }
        val parts = decoded.split(CURSOR_SEPARATOR)
        if (parts.size != 6) throw IllegalArgumentException("无效的游标")
        val cursorContactId = parts[0].toLongOrNull()
            ?: throw IllegalArgumentException("无效的游标")
        val cursorAccountCode = parts[1].ifEmpty { null }
        val cursorTime = runCatching { LocalDateTime.parse(parts[2], ISO) }
            .getOrElse { throw IllegalArgumentException("无效的游标") }
        val cursorSource = parts[3]
        val cursorId = parts[4].toLongOrNull()
            ?: throw IllegalArgumentException("无效的游标")
        val cursorRank = parts[5].toIntOrNull()
            ?: throw IllegalArgumentException("无效的游标")
        if (cursorContactId != contactId) throw IllegalArgumentException("无效的游标")
        if (cursorAccountCode != accountCode) throw IllegalArgumentException("无效的游标")
        if (SOURCE_RANK_OF[cursorSource] != cursorRank || cursorRank !in RANK_SOURCE_OF) {
            throw IllegalArgumentException("无效的游标")
        }
        return ConversationKeyset(
            beforeTime = cursorTime,
            beforeSourceRank = cursorRank,
            beforeId = cursorId
        )
    }

    // ------------------------------------------------------------------
    // 公共参数工具
    // ------------------------------------------------------------------

    /** 解析 yyyy-MM-dd 日期（含 end 当天的开区间语义由调用方处理）。 */
    fun parseDate(value: String?, paramName: String): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim(), DATE)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$paramName 必须是 yyyy-MM-dd 日期")
        }
    }

    private fun validateDirection(direction: String?) {
        if (direction != null && direction != MailboxConversationRepository.DIRECTION_INBOUND &&
            direction != MailboxConversationRepository.DIRECTION_OUTBOUND
        ) {
            throw IllegalArgumentException("direction 只能是 INBOUND 或 OUTBOUND")
        }
    }

    private fun validateTextFilter(name: String, value: String?) {
        if (value != null && value.length > MAX_TEXT_FILTER_LENGTH) {
            throw IllegalArgumentException("$name 过长（最多 $MAX_TEXT_FILTER_LENGTH 字符）")
        }
    }

    /** 活跃发件账号（排除模拟器），与 MailboxService 同一口径。 */
    fun activeAccountCodes(): List<String> =
        senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            .map { it.accountCode }
}
