package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationFilter
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationMessageSqlRow
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.TimelinePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyCollection
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64
import java.util.Optional

/**
 * 02 (I-1–I-4)：只读账号的会话详情准入与附件投影单元测试。
 *
 * 只 mock SQL 仓库/附件解析边界，断言的是 **service 的编排顺序与响应投影**：
 * - I-2：reader 的 `hasReadAccess` 先于联系人存在检查/正文/timeline/标签/附件查询；
 *   grant 缺失与专家不存在统一 403 `MAIL_READ_FORBIDDEN`（不泄露存在性）；每次调用
 *   都重新读 grant（撤销后新请求必拒），游标仍绑定 contact/account。
 * - I-3：reader 响应固定 `attachmentCount=0` / `firstAttachmentNames=[]` /
 *   `calendarAttachment=null` / `outboundAttachments=[]`，且附件解析与 mail_record
 *   快照读取零交互；正文/标签照常返回。
 * - I-4：非 reader（admin 及默认 null 身份）保留原附件解析与快照读取。
 *
 * SQL 层的成员过滤（count/page/explain 的 grant EXISTS）由
 * [com.weibo.talentintroduction.mail.repository.MailboxConversationRepositoryIT] 在真实
 * MySQL 上证明，这里不重复断言 SQL 文本。
 */
class MailboxReadAccessProjectionTest {

    private lateinit var repository: MailboxConversationRepository
    private lateinit var senderAccountRepository: MailSenderAccountRepository
    private lateinit var expertContactRepository: ExpertContactRepository
    private lateinit var expertMaterialService: ExpertMaterialService
    private lateinit var inboundMailTagService: InboundMailTagService
    private lateinit var expertSearchService: ExpertSearchService
    private lateinit var mailRecordRepository: MailRecordRepository
    private lateinit var service: MailboxConversationService
    private lateinit var readAccessService: ExpertMailReadAccessService

    @BeforeEach
    fun setUp() {
        repository = Mockito.mock(MailboxConversationRepository::class.java)
        senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
        expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
        expertMaterialService = Mockito.mock(ExpertMaterialService::class.java)
        inboundMailTagService = Mockito.mock(InboundMailTagService::class.java)
        expertSearchService = Mockito.mock(ExpertSearchService::class.java)
        mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
        readAccessService = Mockito.mock(ExpertMailReadAccessService::class.java)
        Mockito.`when`(readAccessService.accessLevel(anyLong())).thenReturn(ExpertMailAccessLevel.NONE)
        service = MailboxConversationService(
            repository,
            senderAccountRepository,
            expertContactRepository,
            expertMaterialService,
            inboundMailTagService,
            expertSearchService,
            mailRecordRepository,
            readAccessService
        )
        Mockito.`when`(
            senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(listOf(account(ACCOUNT_CODE), account(OTHER_ACCOUNT_CODE)))
        Mockito.`when`(expertContactRepository.findById(CONTACT_ID))
            .thenReturn(Optional.of(contact(CONTACT_ID)))
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection())).thenReturn(emptyMap())
        Mockito.`when`(mailRecordRepository.findAllById(anyCollection())).thenReturn(emptyList())
    }

    // ------------------------------------------------------------------
    // I-2：准入先于一切读取
    // ------------------------------------------------------------------

    @Test
    fun `reader without grant is denied before contact timeline and attachment reads`() {
        Mockito.`when`(readAccessService.accessLevel(CONTACT_ID))
            .thenReturn(ExpertMailAccessLevel.NONE)

        val ex = assertThrows(MailReadAccessDeniedException::class.java) {
            service.listMessages(CONTACT_ID, null, null, 50, MailReadOnlyPolicy.READER_USERNAME)
        }
        assertEquals(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, ex.code)

        // 准入即边界：联系人、正文、标签、附件、快照一次都不许读。
        verifyNoInteractions(expertContactRepository)
        verify(repository, never())
            .timelineMessages(anyLong(), anyFilter(), Mockito.any(), anyInt())
        verifyNoInteractions(inboundMailTagService)
        verifyNoInteractions(expertMaterialService)
        verify(mailRecordRepository, never()).findAllById(anyCollection())
    }

    @Test
    fun `missing expert and missing grant are both forbidden without leaking existence`() {
        // grant 查询为唯一准入事实：专家不存在同样只回 403（不是 404）。
        Mockito.`when`(readAccessService.accessLevel(999999L))
            .thenReturn(ExpertMailAccessLevel.NONE)
        Mockito.`when`(expertContactRepository.findById(999999L)).thenReturn(Optional.empty())

        val ex = assertThrows(MailReadAccessDeniedException::class.java) {
            service.listMessages(999999L, null, null, 50, MailReadOnlyPolicy.READER_USERNAME)
        }
        assertEquals(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, ex.code)
        verify(expertContactRepository, never()).findById(999999L)
    }

    @Test
    fun `grant is re-read on every request so a revoked expert is denied on the next page`() {
        Mockito.`when`(readAccessService.accessLevel(CONTACT_ID))
            .thenReturn(ExpertMailAccessLevel.READ, ExpertMailAccessLevel.NONE)
        Mockito.`when`(
            repository.timelineMessages(anyLong(), anyFilter(), Mockito.any(), anyInt())
        ).thenReturn(TimelinePage(emptyList(), false))

        val first = service.listMessages(CONTACT_ID, null, null, 50, MailReadOnlyPolicy.READER_USERNAME)
        assertTrue(first.items.isEmpty())

        val cursor = cursorOf(CONTACT_ID, ACCOUNT_CODE, "MAIL_RECORD", 5L, 1)
        val ex = assertThrows(MailReadAccessDeniedException::class.java) {
            // 更早页：撤销提交后的新请求必须被拒。
            service.listMessages(CONTACT_ID, null, cursor, 50, MailReadOnlyPolicy.READER_USERNAME)
        }
        assertEquals(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, ex.code)

        verify(readAccessService, times(2)).accessLevel(CONTACT_ID)
    }

    @Test
    fun `reader cursor stays bound to the expert even when the grant is valid`() {
        Mockito.`when`(readAccessService.accessLevel(CONTACT_ID))
            .thenReturn(ExpertMailAccessLevel.READ)
        Mockito.`when`(readAccessService.accessLevel(OTHER_CONTACT_ID))
            .thenReturn(ExpertMailAccessLevel.READ)
        Mockito.`when`(expertContactRepository.findById(OTHER_CONTACT_ID))
            .thenReturn(Optional.of(contact(OTHER_CONTACT_ID)))

        val otherExpertCursor = cursorOf(OTHER_CONTACT_ID, ACCOUNT_CODE, "MAIL_RECORD", 5L, 1)
        assertThrows(IllegalArgumentException::class.java) {
            service.listMessages(CONTACT_ID, null, otherExpertCursor, 50, MailReadOnlyPolicy.READER_USERNAME)
        }
        // 账号 scope 变化同样拒绝（绑定的是请求 scope，不是消息自身账号）。
        Mockito.`when`(
            repository.timelineMessages(anyLong(), anyFilter(), Mockito.any(), anyInt())
        ).thenReturn(TimelinePage(emptyList(), false))
        val accountScopedCursor = cursorOf(CONTACT_ID, ACCOUNT_CODE, "MAIL_RECORD", 5L, 1)
        service.listMessages(CONTACT_ID, ACCOUNT_CODE, accountScopedCursor, 50, MailReadOnlyPolicy.READER_USERNAME)
        assertThrows(IllegalArgumentException::class.java) {
            service.listMessages(CONTACT_ID, OTHER_ACCOUNT_CODE, accountScopedCursor, 50, MailReadOnlyPolicy.READER_USERNAME)
        }
    }

    // ------------------------------------------------------------------
    // I-3 / I-4：附件投影
    // ------------------------------------------------------------------

    @Test
    fun `reader gets body and tags but fixed empty attachments with zero attachment interaction`() {
        Mockito.`when`(readAccessService.accessLevel(CONTACT_ID))
            .thenReturn(ExpertMailAccessLevel.READ)
        val inbound = inboundRow(id = 31L, messageId = "in-31")
        val outbound = outboundSentRow(id = 77L, subject = "Me: intro")
        Mockito.`when`(
            repository.timelineMessages(anyLong(), anyFilter(), Mockito.any(), anyInt())
        ).thenReturn(TimelinePage(listOf(outbound, inbound), false))
        Mockito.`when`(inboundMailTagService.listTagsBatch(listOf(31L)))
            .thenReturn(mapOf(31L to listOf(TagView(tagId = 5L, tagType = "TOPIC", qaRuleId = null, label = "会议安排", source = "MANUAL", active = true))))

        val response = service.listMessages(CONTACT_ID, null, null, 50, MailReadOnlyPolicy.READER_USERNAME)

        assertEquals(ExpertMailAccessLevel.READ, response.accessLevel)
        assertEquals(2, response.items.size)
        for (item in response.items) {
            assertEquals(0, item.attachmentCount, "reader attachmentCount 固定 0")
            assertTrue(item.firstAttachmentNames.isEmpty(), "reader firstAttachmentNames 固定 []")
            assertNull(item.calendarAttachment, "reader calendarAttachment 固定 null")
            assertTrue(item.outboundAttachments.isEmpty(), "reader outboundAttachments 固定 []")
        }
        // 正文与标签不受影响（只读正文允许；附件能力关闭）。
        val inboundItem = response.items.first { it.source == MailboxConversationRepository.SOURCE_INBOUND_PROCESSING }
        assertEquals("inbound body", inboundItem.body)
        assertEquals(1, inboundItem.tags.size)
        assertEquals("会议安排", inboundItem.tags[0].label)
        val outboundItem = response.items.first { it.source == MailboxConversationRepository.SOURCE_MAIL_RECORD }
        assertEquals("outbound body", outboundItem.body)

        // 附件解析与仅用于附件的发件快照读取零交互。
        verifyNoInteractions(expertMaterialService)
        verify(mailRecordRepository, never()).findAllById(anyCollection())
    }

    @Test
    fun `FULL reader admin and default identity share original attachment projection`() {
        Mockito.`when`(
            repository.timelineMessages(anyLong(), anyFilter(), Mockito.any(), anyInt())
        ).thenReturn(TimelinePage(listOf(outboundSentRow(id = 77L, subject = "Me: intro")), false))
        Mockito.`when`(expertMaterialService.resolveMessageAttachments(
            MailboxConversationRepository.SOURCE_MAIL_RECORD, 77L
        )).thenReturn(
            listOf(
                MailAttachment(
                    id = 11L, mailRecordId = 77L, inboundProcessingId = null,
                    fileName = "cv.pdf", contentType = "application/pdf", fileSize = 10L, storagePath = null
                )
            )
        )
        val record = MailRecord(
            id = 77L,
            expertContactId = CONTACT_ID,
            direction = MailboxConversationRepository.DIRECTION_OUTBOUND,
            mailType = MailboxConversationService.MANUAL_RICH_REPLY_MAIL_TYPE,
            senderAccountCode = ACCOUNT_CODE,
            messageId = "out-77",
            inReplyTo = null,
            subject = "Me: intro",
            body = "outbound body",
            matchedQaRuleId = null,
            sendStatus = MailboxConversationService.SEND_STATUS_SENT,
            receivedAt = null,
            sentAt = LocalDateTime.now(),
            calendarAttachmentJson = calendarSnapshotJson(),
            outboundAttachmentsJson = outboundSnapshotJson()
        )
        Mockito.`when`(mailRecordRepository.findAllById(listOf(77L))).thenReturn(listOf(record))

        for (username in listOf(MailReadOnlyPolicy.ADMIN_USERNAME, null, MailReadOnlyPolicy.READER_USERNAME)) {
            val level = if (username == MailReadOnlyPolicy.READER_USERNAME) ExpertMailAccessLevel.FULL
                else ExpertMailAccessLevel.READ
            Mockito.`when`(readAccessService.accessLevel(CONTACT_ID)).thenReturn(level)
            val response = service.listMessages(CONTACT_ID, null, null, 50, username)
            assertEquals(level, response.accessLevel)
            val item = response.items.single()
            assertEquals(1, item.attachmentCount)
            assertEquals(listOf("cv.pdf"), item.firstAttachmentNames)
            assertNotNull(item.calendarAttachment)
            assertEquals("meeting-2026-10-27-meeting.ics", item.calendarAttachment!!.filename)
            assertEquals(1, item.outboundAttachments.size)
            assertEquals("会议资料.txt", item.outboundAttachments[0].filename)
        }

        verify(expertMaterialService, times(3))
            .resolveMessageAttachments(MailboxConversationRepository.SOURCE_MAIL_RECORD, 77L)
        verify(mailRecordRepository, times(3)).findAllById(listOf(77L))
        // Existence-only lookup is no longer the projection/admission source.
        verify(repository, never()).hasReadAccess(anyString(), anyLong())
    }

    @Test
    fun `summary levels use one bulk lookup and preserve SQL order tags and pagination`() {
        val ids = listOf(OTHER_CONTACT_ID, CONTACT_ID, 9L)
        val rows = ids.map { id ->
            MailboxConversationRepository.ConversationSummarySqlRow(
                id, "Expert $id", "expert$id@example.org", "orcid-$id",
                1, 2, 0, 1, LocalDateTime.of(2026, 10, 1, 9, 0), MailboxProgressStatus.NONE
            )
        }
        Mockito.`when`(repository.countConversations(Mockito.eq("admin") ?: "", anyFilter())).thenReturn(3L)
        Mockito.`when`(repository.filterCounts(listOf(ACCOUNT_CODE, OTHER_ACCOUNT_CODE)))
            .thenReturn(0L to 0L)
        Mockito.`when`(repository.pageConversations(Mockito.eq("admin") ?: "", anyFilter(), Mockito.eq(20), Mockito.eq(0L)))
            .thenReturn(rows)
        Mockito.`when`(expertContactRepository.findAllById(ids)).thenReturn(emptyList())
        Mockito.`when`(readAccessService.accessLevels(ids)).thenReturn(
            mapOf(OTHER_CONTACT_ID to ExpertMailAccessLevel.FULL,
                CONTACT_ID to ExpertMailAccessLevel.READ, 9L to ExpertMailAccessLevel.NONE)
        )
        val result = service.listConversations("admin", null, false, false, false, false,
            null, null, null, null, null, null, null, null, 0, 20)
        assertEquals(ids, result.items.map { it.contactId })
        assertEquals(listOf(ExpertMailAccessLevel.FULL, ExpertMailAccessLevel.READ, ExpertMailAccessLevel.NONE),
            result.items.map { it.accessLevel })
        assertEquals(3L, result.total)
        assertEquals(20, result.size)
        assertTrue(result.items.all { it.expertTags == null })
        verify(readAccessService).accessLevels(ids)
        verify(readAccessService, never()).accessLevel(anyLong())
    }

    @Test
    fun `reader summaries expose shared metadata while personal pending membership stays separate`() {
        val ids = listOf(CONTACT_ID, OTHER_CONTACT_ID)
        val rows = ids.map { id -> MailboxConversationRepository.ConversationSummarySqlRow(
            id, "Expert $id", "expert$id@example.org", "orcid-$id", 2, 3, 0, 0,
            LocalDateTime.of(2026, 10, 1, 9, 0), MailboxProgressStatus.NONE) }
        Mockito.`when`(repository.countConversations(Mockito.eq("user1") ?: "", anyFilter())).thenReturn(2L)
        Mockito.`when`(repository.pageConversations(Mockito.eq("user1") ?: "", anyFilter(), Mockito.eq(20), Mockito.eq(0L))).thenReturn(rows)
        Mockito.`when`(expertContactRepository.findAllById(ids)).thenReturn(ids.map(::contact))
        Mockito.`when`(readAccessService.accessLevels(ids)).thenReturn(mapOf(CONTACT_ID to ExpertMailAccessLevel.READ, OTHER_CONTACT_ID to ExpertMailAccessLevel.FULL))
        Mockito.`when`(repository.suspensionStatesByContacts("admin", ids)).thenReturn(mapOf(CONTACT_ID to
            MailboxConversationRepository.ConversationSuspensionStateRow(CONTACT_ID, true, "等待材料", 4)))
        Mockito.`when`(repository.suspensionStatesByContacts("user1", ids)).thenReturn(mapOf(OTHER_CONTACT_ID to
            MailboxConversationRepository.ConversationSuspensionStateRow(OTHER_CONTACT_ID, false, null, 2)))
        val items = service.listConversations("user1", null, false, false, false, false,
            null, null, null, null, null, null, null, null, 0, 20).items
        assertEquals(0, items[0].pendingCount.toInt())
        assertEquals(4L, items[0].displayPendingCount)
        assertEquals("admin", items[0].displayPendingOwner)
        assertEquals("等待材料", items[0].displaySuspendReason)
        assertTrue(items[0].displaySuspended)
        assertEquals(contact(CONTACT_ID).currentIndexLevel, items[0].currentIndexLevel)
        assertEquals(2L, items[1].displayPendingCount)
        assertEquals("user1", items[1].displayPendingOwner)
    }

    @Test
    fun `empty timeline account scope still returns current level without attachment reads`() {
        Mockito.`when`(readAccessService.accessLevel(CONTACT_ID)).thenReturn(ExpertMailAccessLevel.FULL)
        val result = service.listMessages(CONTACT_ID, "missing-account", null, 50, "user1")
        assertEquals(ExpertMailAccessLevel.FULL, result.accessLevel)
        assertTrue(result.items.isEmpty())
        verifyNoInteractions(expertMaterialService, mailRecordRepository)
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun account(code: String) = MailSenderAccount(
        accountCode = code,
        senderEmail = "$code@fixture.local",
        senderName = code,
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.fixture",
        smtpPort = 465,
        smtpUsername = code,
        smtpPassword = "pw",
        imapHost = "imap.fixture",
        imapPort = 993,
        imapUsername = code,
        imapPassword = "pw"
    )

    private fun contact(id: Long) = ExpertContact(
        id = id,
        campaignId = id,
        orcidId = "0000-0000-0000-%04d".format(id),
        expertEmail = "expert$id@example.org",
        expertName = "Expert $id"
    )

    private fun inboundRow(id: Long, messageId: String) = ConversationMessageSqlRow(
        source = MailboxConversationRepository.SOURCE_INBOUND_PROCESSING,
        id = id,
        expertContactId = CONTACT_ID,
        direction = MailboxConversationRepository.DIRECTION_INBOUND,
        accountCode = ACCOUNT_CODE,
        subject = "Re: intro",
        body = "inbound body",
        cleanedBody = "cleaned inbound body",
        eventAt = LocalDateTime.of(2026, 10, 1, 9, 0),
        sendStatus = null,
        processStatus = "PROCESSED",
        messageId = messageId,
        inReplyTo = "out-77"
    )

    private fun outboundSentRow(id: Long, subject: String) = ConversationMessageSqlRow(
        source = MailboxConversationRepository.SOURCE_MAIL_RECORD,
        id = id,
        expertContactId = CONTACT_ID,
        direction = MailboxConversationRepository.DIRECTION_OUTBOUND,
        accountCode = ACCOUNT_CODE,
        subject = subject,
        body = "outbound body",
        cleanedBody = null,
        eventAt = LocalDateTime.of(2026, 10, 2, 9, 0),
        sendStatus = MailboxConversationService.SEND_STATUS_SENT,
        processStatus = null,
        messageId = "out-$id",
        inReplyTo = null
    )

    /**
     * Kotlin 非空参数 + Mockito matcher 的常规写法：`any()` 返回 null 会触发 Kotlin 空检查，
     * 因此回退到一个真实实例（仓库内 `anyValue` 同一约定）。
     */
    private fun anyFilter(): ConversationFilter =
        Mockito.any<ConversationFilter>() ?: ConversationFilter(accountCodes = emptyList())

    /** 与 service 私有游标编码同构（contact \u001F account \u001F time \u001F source \u001F id \u001F rank）。 */
    private fun cursorOf(
        contactId: Long,
        accountCode: String?,
        source: String,
        id: Long,
        rank: Int
    ): String {
        val payload = listOf(
            contactId.toString(),
            accountCode.orEmpty(),
            "2026-10-02T09:00:00",
            source,
            id.toString(),
            rank.toString()
        ).joinToString("\u001F")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    private fun calendarSnapshotJson(): String {
        val ics = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(ics.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return CalendarAttachmentCodec.serialize(
            CalendarAttachmentSnapshot(
                schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
                filename = "meeting-2026-10-27-meeting.ics",
                contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
                icsText = ics,
                sha256 = sha,
                semanticSha256 = "a".repeat(64)
            )
        )
    }

    private fun outboundSnapshotJson(): String {
        val bytes = "会议资料".toByteArray(Charsets.UTF_8)
        return OutboundAttachmentSnapshotCodec.serialize(
            listOf(
                OutboundAttachmentSnapshot(
                    schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
                    id = "3f7c1a52-90de-4a1b-8b0e-6c2f5d4a1e77",
                    filename = "会议资料.txt",
                    contentType = "text/plain",
                    byteLength = bytes.size.toLong(),
                    sha256 = outboundSha256Hex(bytes)
                )
            )
        )
    }

    private companion object {
        const val CONTACT_ID = 7L
        const val OTHER_CONTACT_ID = 8L
        const val ACCOUNT_CODE = "acc-a"
        const val OTHER_ACCOUNT_CODE = "acc-b"
    }
}
