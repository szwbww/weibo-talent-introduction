package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.controller.ConversationCalendarAttachment
import com.weibo.talentintroduction.mail.controller.ConversationItemResponse
import com.weibo.talentintroduction.mail.controller.ConversationLatestInboundItem
import com.weibo.talentintroduction.mail.controller.ConversationLatestMessageItem
import com.weibo.talentintroduction.mail.controller.ConversationListResponse
import com.weibo.talentintroduction.mail.controller.ConversationMessageItemResponse
import com.weibo.talentintroduction.mail.controller.ConversationMessageListResponse
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationFilter
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationKeyset
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationMessageSqlRow
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationSummarySqlRow
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.slf4j.LoggerFactory
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
 *
 * fast-p 01 追加：timeline 当前窗口邮件标签一次批量读取（I-3，复用
 * [InboundMailTagService.listTagsBatch]，仅真实 INBOUND_PROCESSING id）；summary 本页
 * 专家标签按 (level, orcidId) 批量投影（I-6，复用 [ExpertSearchService.searchByOrcidIds]，
 * 只读 ES 画像，不触发发现/补全/晋级）；主题 MIME 解码统一走 [MailSubjectDecoder]（I-4）。
 */
@Service
class MailboxConversationService(
    private val repository: MailboxConversationRepository,
    private val senderAccountRepository: MailSenderAccountRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val expertMaterialService: ExpertMaterialService,
    private val inboundMailTagService: InboundMailTagService,
    private val expertSearchService: ExpertSearchService,
    // 03 (T2/I-3/I-4): 会话时间线单独日历附件元数据 —— 按 MAIL_RECORD OUTBOUND SENT
    // id 集合批量读一次存档快照。
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(MailboxConversationService::class.java)
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val DEFAULT_MESSAGE_LIMIT = 50
        const val MAX_MESSAGE_LIMIT = 100
        const val MAX_TEXT_FILTER_LENGTH = 255
        const val FIRST_ATTACHMENT_NAME_LIMIT = 3

        /** 03 (I-3/I-4)：日历附件只在 SENT 出站行暴露（mail_record.send_status 值）。 */
        const val SEND_STATUS_SENT = "SENT"

        /** 03 (I-4)：时间线单独日历附件下载端点（与 CalendarAttachmentController 同源）。 */
        const val CALENDAR_DOWNLOAD_BASE = "/api/mail/conversations"

        /** 专家层级白名单：只在合法层级查询 ES 画像（I-6，禁止跨层猜测回退）。 */
        private val LEVEL_NAMES = ExpertIndexLevel.values().map { it.name }.toSet()

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
        recipientEmail: String?,
        keyword: String?,
        page: Int,
        size: Int
    ): ConversationListResponse {
        validateDirection(direction)
        validateTextFilter("q", q)
        validateTextFilter("subject", subject)
        validateTextFilter("label", label)
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw IllegalArgumentException("startDate 不能晚于 endDate")
        }
        // 新筛选：trim 空白后为空 → null；长度校验在 trim 之后（超长 400）。
        val recipientEmailFilter = recipientEmail?.trim()?.takeIf { it.isNotEmpty() }
        val keywordFilter = keyword?.trim()?.takeIf { it.isNotEmpty() }
        validateTextFilter("recipientEmail", recipientEmailFilter)
        validateTextFilter("keyword", keywordFilter)
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
            label = label,
            recipientEmail = recipientEmailFilter,
            keyword = keywordFilter
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
        val expertTagsByContact = currentPageExpertTags(rows)

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
                        // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）。
                        subject = MailSubjectDecoder.decode(m.subject),
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
                materialCount = materialCounts[row.expertContactId] ?: 0L,
                expertTags = expertTagsByContact[row.expertContactId]
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

        // I-3：只把当前窗口的真实 INBOUND_PROCESSING id 交出去批量读一次标签；窗口内
        // 无来信（纯 OUTBOUND / 更早页）不发请求。绝不用 source_inbound_id / 数值巧合
        // 给 OUTBOUND 行映射标签。
        val processingIds = page.rows
            .filter { it.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING }
            .map { it.id }
        val tagsByProcessingId = if (processingIds.isEmpty()) {
            emptyMap()
        } else {
            inboundMailTagService.listTagsBatch(processingIds)
        }

        // 03 (I-3/I-4)：只对当前窗口的 MAIL_RECORD+OUTBOUND+SENT 行按 id 集合批量读一次
        // 存档快照（findAllById 恰好一次；窗口无此类行不发请求）。INBOUND 行即使与
        // outbound 数值 id 相同也绝不读 outbound 快照（避免 source 碰撞串附件）。
        val outboundSentRows = page.rows.filter {
            it.source == MailboxConversationRepository.SOURCE_MAIL_RECORD &&
                it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
                it.sendStatus == SEND_STATUS_SENT
        }
        val mailRecordRowsById = if (outboundSentRows.isEmpty()) {
            emptyMap()
        } else {
            mailRecordRepository.findAllById(outboundSentRows.map { it.id })
                .asSequence()
                .filter {
                    it.id != null &&
                        it.direction == MailboxConversationRepository.DIRECTION_OUTBOUND &&
                        it.sendStatus == SEND_STATUS_SENT
                }
                .associateBy { requireNotNull(it.id) }
        }

        // 行按 DESC（新→旧）返回；正序呈现最新窗口。
        val items = page.rows.asReversed().map { row ->
            val attachments = expertMaterialService.resolveMessageAttachments(row.source, row.id)
            ConversationMessageItemResponse(
                source = row.source,
                id = row.id,
                contactId = row.expertContactId,
                direction = row.direction,
                accountCode = row.accountCode,
                // 读兼容解码：历史行可能是编码 subject（旧库不 UPDATE，I-4）；新收信已在
                // 头读取时解码落库，普通文本二次解码是幂等 no-op。
                subject = MailSubjectDecoder.decode(row.subject),
                body = row.body,
                cleanedBody = row.cleanedBody,
                eventAt = row.eventAt.format(ISO),
                sendStatus = row.sendStatus,
                processStatus = row.processStatus,
                attachmentCount = attachments.size,
                firstAttachmentNames = attachments.take(FIRST_ATTACHMENT_NAME_LIMIT).mapNotNull { it.fileName },
                messageId = row.messageId,
                inReplyTo = row.inReplyTo,
                tags = if (row.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING) {
                    tagsByProcessingId[row.id].orEmpty()
                } else {
                    emptyList()
                },
                calendarAttachment = calendarAttachmentOf(row, mailRecordRowsById[row.id])
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
    // 本页专家标签投影（I-6/X8）
    // ------------------------------------------------------------------

    /**
     * 当前 SQL 页的专家标签：contactId → 标签列表或 null。
     *
     * - null = 未取得有效画像结果（本页无该 contact / 无 ORCID / 层级非法 / 画像缺失 /
     *   该层 ES 查询异常）——绝不伪称 []，绝不跨层猜测；[] = 画像已读取且无标签。
     * - 只对当前页 id 调一次 findAllById（不查全库、不逐人 findByOrcidId）；按层级分组后
     *   每层至多一次 searchByOrcidIds（全页 ≤3 次）；页为空/无合法 (level, orcid) 零调用。
     * - 结果按 (level, orcidId) 回填原 SQL 行序（绝不用 ES 返回顺序替代 SQL 顺序）；
     *   tags trim/去空/去重并保持 ES 顺序，服务端不截断。只读 ES 画像（复用既有
     *   _source 投影），不触发发现/补全/晋级；某层异常仅该层降级为 null，不影响其它层、
     *   SQL 排序、分页与整页响应。
     */
    private fun currentPageExpertTags(rows: List<ConversationSummarySqlRow>): Map<Long, List<String>?> {
        if (rows.isEmpty()) return emptyMap()
        val contacts = expertContactRepository.findAllById(rows.map { it.expertContactId })
            .associateBy { requireNotNull(it.id) }
        val profilesByLevelAndOrcid = mutableMapOf<Pair<String, String>, ExpertProfile>()
        for ((levelName, orcidIds) in levelOrcidIdsOf(contacts)) {
            try {
                val level = ExpertIndexLevel.valueOf(levelName)
                for (profile in expertSearchService.searchByOrcidIds(orcidIds, level)) {
                    if (profile.orcidId.isNotBlank()) {
                        profilesByLevelAndOrcid[levelName to profile.orcidId] = profile
                    }
                }
            } catch (e: Exception) {
                log.warn(
                    "Expert tag lookup failed for level {} ({} orcidIds); expertTags=null for this level",
                    levelName, orcidIds.size, e
                )
            }
        }
        return rows.associate { row ->
            val contact = contacts[row.expertContactId]
            val tags = when {
                contact == null -> null
                contact.currentIndexLevel !in LEVEL_NAMES -> null
                contact.orcidId.isBlank() -> null
                else -> {
                    val profile = profilesByLevelAndOrcid[contact.currentIndexLevel to contact.orcidId]
                    if (profile == null) {
                        null
                    } else {
                        profile.tags
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.distinct()
                            ?: emptyList()
                    }
                }
            }
            row.expertContactId to tags
        }
    }

    /** 按合法层级分组去重本页 ORCID（空白或非法层级不进入任何 ES 查询）。 */
    private fun levelOrcidIdsOf(contacts: Map<Long, ExpertContact>): Map<String, List<String>> {
        val grouped = mutableMapOf<String, MutableList<String>>()
        for (contact in contacts.values) {
            if (contact.currentIndexLevel in LEVEL_NAMES && contact.orcidId.isNotBlank()) {
                grouped.getOrPut(contact.currentIndexLevel) { mutableListOf() }.add(contact.orcidId)
            }
        }
        return grouped.mapValues { (_, orcids) -> orcids.distinct() }
    }

    // ------------------------------------------------------------------
    // 游标（(time, source, id) 编码/校验；绑定 contact/account scope）
    // ------------------------------------------------------------------

    /**
     * 03 (I-3/I-4)：单条时间线行的日历附件元数据。只在 MAIL_RECORD+OUTBOUND+SENT 行上
     * 按存档快照解析（01 CalendarAttachmentCodec 校验）；归属（contact/账号）一致且
     * 快照合法才附加，其余（旧行 null / 损坏 JSON / 跨专家账号）一律 null —— 与下载
     * 端点同一 parseOrNull，绝不从配置/当前模板重新生成。
     */
    private fun calendarAttachmentOf(
        row: ConversationMessageSqlRow,
        record: MailRecord?
    ): ConversationCalendarAttachment? {
        // I-3/I-4: 只对真实 MAIL_RECORD 行附加 —— INBOUND 行即使与 outbound 数值 id 相同
        // 也绝不读 outbound 快照（source 碰撞防护与下载端同款显式归属检查）。
        if (row.source != MailboxConversationRepository.SOURCE_MAIL_RECORD ||
            record == null ||
            record.expertContactId != row.expertContactId ||
            record.senderAccountCode != row.accountCode
        ) {
            return null
        }
        val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
        val id = requireNotNull(record.id)
        return ConversationCalendarAttachment(
            filename = snapshot.filename,
            byteLength = snapshot.icsText.toByteArray(Charsets.UTF_8).size,
            downloadUrl = "$CALENDAR_DOWNLOAD_BASE/${row.expertContactId}/messages/$id/calendar-attachment"
        )
    }

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
