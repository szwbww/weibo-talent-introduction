package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.NoSuchElementException
import java.util.Optional

class MeetingConfirmationServiceTest {

    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailVariableService = Mockito.mock(MailVariableService::class.java)
    private val templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java)
    private val blockRepository = Mockito.mock(MailComposeTemplateBlockRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetRepository = Mockito.mock(ReplySnippetRepository::class.java)

    /**
     * I-1 证据：通用模板链路用**真实** MailComposeTemplateService（renderByCode +
     * REPLY_SNIPPET/CUSTOM_TEXT 全序 + ${...} 替换），只 mock 模板/片段仓库与变量服务；
     * 因此“正文确实由通用模板渲染出一条完整文本”是被测行为而非硬编码字串。
     * spy 只为捕获 renderByCode 收到的变量 map（I-2 判据），不改渲染行为。
     *
     * I-1 只读证明：被测服务只注入只读协作者（来信处理/专家/账号/模板/变量/纯函数
     * MailContentService）。SMTP、mail_record、mail_send_attempt、meeting_schedule、
     * 状态/绑定/计数的写入口在此对象图上不存在。
     */
    private val mailComposeTemplateService = Mockito.spy(
        MailComposeTemplateService(
            templateRepository,
            blockRepository,
            qaRuleRepository,
            replySnippetRepository,
            ObjectMapper(),
            mailVariableService,
            expertContactRepository,
            mailSenderAccountService,
            ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), MailPlaceholderService())
        )
    )

    private val service = MeetingConfirmationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        mailSenderAccountService,
        mailComposeTemplateService,
        MailContentService(),
        mailVariableService
    )

    companion object {
        const val TEMPLATE_ID = 100L
        const val SNIPPET_ID = 900L
        const val PROCESSING_ID = 7L
        const val CONTACT_ID = 1L
        const val ACCOUNT_CODE = "acc-1"
        const val ZOOM_URL = "https://zoom.us/j/87102801187?pwd=RH3bf4vbH0SoyTq2UW1Dzzuag4kISa.1"
        const val GENERATED_AT = "2026-09-09T03:00:40Z"

        /** REPLY_SNIPPET 块：沿用既有邀请模板的 ${expertFamilyName|Colleague} 语法。 */
        val DEFAULT_SNIPPET = "Dear \${expertFamilyName|Colleague},\n\n" +
            "Thank you for confirming your interest in our programme."

        /** CUSTOM_TEXT 块：两个会议专用变量 + 既有 sender/team 变量。 */
        val DEFAULT_CUSTOM_TEXT = "We have noted the meeting time as \${meeting_time}.\n\n" +
            "Please join the meeting using the following link:\n\n" +
            "\${zoom_url}\n\n" +
            "We look forward to speaking with you.\n\n" +
            "Best regards,\n" +
            "\${senderName}, \${senderTitle}\n" +
            "\${teamName} \${countryName}"

        val DEFAULT_TEMPLATE_BODY = DEFAULT_SNIPPET + "\n\n" + DEFAULT_CUSTOM_TEXT

        /** `MailVariableService.buildVariables` 返回的通用 map（测试替身，键集即契约）。 */
        val GENERIC_VARIABLES: Map<String, String> = linkedMapOf(
            "senderEmail" to "sender@example.com",
            "senderName" to "LuKai",
            "senderTitle" to "Customer Care Officer",
            "teamName" to "Qingfei Tech Talent Team",
            "countryName" to "China",
            "expertName" to "Professor Basdogan",
            "expertFamilyName" to "Basdogan",
            "unsubscribeUrl" to "https://example.com/unsubscribe"
        )
    }

    private fun processing(
        id: Long = PROCESSING_ID,
        expertContactId: Long? = CONTACT_ID,
        senderAccountCode: String = ACCOUNT_CODE
    ) = InboundMailProcessing(
        id = id,
        senderAccountCode = senderAccountCode,
        imapUid = 200L,
        messageId = "msg-1",
        fromEmail = "expert@test.com",
        subject = "Re: invitation",
        body = "Hello",
        cleanedBody = "Hello",
        receivedAt = LocalDateTime.of(2026, 9, 9, 8, 0),
        processStatus = "MANUAL_REVIEW",
        processReason = "UNMATCHED",
        expertContactId = expertContactId
    )

    private fun contact(
        id: Long = CONTACT_ID,
        name: String? = "Professor Basdogan",
        email: String = "expert@test.com"
    ) = ExpertContact(
        id = id,
        campaignId = 1,
        orcidId = "0000-0001-2345-6789",
        expertEmail = email,
        expertName = name,
        currentStatus = "WAITING_MEETING_CONFIRMATION"
    )

    private fun account(code: String = ACCOUNT_CODE) = MailSenderAccount(
        accountCode = code,
        senderEmail = "sender@example.com",
        senderName = "LuKai",
        senderTitle = "Customer Care Officer",
        senderDisplayName = "LuKai",
        teamName = "Qingfei Tech Talent Team",
        countryName = "China",
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p"
    )

    private fun stubIdentity(
        processing: InboundMailProcessing = processing(),
        expert: ExpertContact = contact(),
        requestedAccount: MailSenderAccount? = null
    ) {
        val processingId = processing.id ?: PROCESSING_ID
        Mockito.`when`(inboundMailProcessingRepository.findById(processingId)).thenReturn(Optional.of(processing))
        Mockito.`when`(expertContactRepository.findById(CONTACT_ID)).thenReturn(Optional.of(expert))
        val code = requestedAccount?.accountCode ?: processing.senderAccountCode
        Mockito.`when`(mailSenderAccountService.getManualSendAccount(code)).thenReturn(requestedAccount ?: account())
    }

    /** 启用中的通用 `MEETING_INVITATION`：回复片段块 + 自定义文本块（有序）。 */
    private fun stubInvitationTemplate(
        customText: String = DEFAULT_CUSTOM_TEXT,
        snippet: String? = DEFAULT_SNIPPET
    ) {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("MEETING_INVITATION"))
            .thenReturn(
                MailComposeTemplate(
                    id = TEMPLATE_ID,
                    templateCode = "MEETING_INVITATION",
                    templateName = "会议邀请 · 英文",
                    subject = "Meeting invitation",
                    mailType = "MEETING_INVITATION",
                    enabled = true
                )
            )
        val blocks = mutableListOf<MailComposeTemplateBlock>()
        if (snippet != null) {
            blocks += MailComposeTemplateBlock(
                id = 1,
                templateId = TEMPLATE_ID,
                blockOrder = 0,
                blockType = "REPLY_SNIPPET",
                refId = SNIPPET_ID
            )
            Mockito.`when`(replySnippetRepository.findById(SNIPPET_ID)).thenReturn(
                Optional.of(
                    ReplySnippet(
                        id = SNIPPET_ID,
                        snippetType = "GREETING",
                        content = snippet,
                        enabled = true
                    )
                )
            )
        }
        blocks += MailComposeTemplateBlock(
            id = 2,
            templateId = TEMPLATE_ID,
            blockOrder = 1,
            blockType = "CUSTOM_TEXT",
            customText = customText
        )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(TEMPLATE_ID)).thenReturn(blocks)
    }

    /** 通用变量（与普通人工单发同序：sender + expert + unsubscribe）。 */
    private fun genericVariables(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        GENERIC_VARIABLES + overrides

    private fun stubVariables(overrides: Map<String, String> = emptyMap()) {
        Mockito.`when`(mailVariableService.resolveExpertProfileFor(anyValue(contact()))).thenReturn(null)
        Mockito.`when`(
            mailVariableService.buildVariables(
                anyValue(account()),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyBoolean(),
                Mockito.any()
            )
        ).thenReturn(genericVariables(overrides))
    }

    /** 捕获通用模板渲染实际收到的变量 map（I-2 判据）；spy 仍执行真实渲染。 */
    private fun captureRenderedVariables(): MutableList<Map<String, String>> {
        val captured = mutableListOf<Map<String, String>>()
        Mockito.doAnswer { invocation ->
            captured += invocation.getArgument<Map<String, String>>(1)
            invocation.callRealMethod()
        }.`when`(mailComposeTemplateService).renderByCode(
            anyValue(MeetingConfirmationService.MEETING_INVITATION_TEMPLATE_CODE),
            anyValue(emptyMap()),
            Mockito.anyInt()
        )
        return captured
    }

    /** Mockito.any() 对 Kotlin 非空参数会返回 null；传一个真实默认值实例占位（既有测试同款手法）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun stubMeetingPreparation(overrides: Map<String, String> = emptyMap()) {
        stubInvitationTemplate()
        stubVariables(overrides)
    }

    private fun meetingInput(
        zoneId: String = "Europe/Istanbul",
        startLocal: String = "2026-09-11T10:00",
        endLocal: String = "2026-09-11T10:30",
        zoomUrl: String = ZOOM_URL,
        generatedAt: String = GENERATED_AT
    ) = MeetingInput(
        zoneId = zoneId,
        startLocal = startLocal,
        endLocal = endLocal,
        zoomUrl = zoomUrl,
        generatedAt = generatedAt
    )

    private fun preview(input: MeetingInput = meetingInput(), processingId: Long = PROCESSING_ID): MeetingPreviewResponse =
        service.preview(
            processingId,
            MeetingPreviewRequest(contactId = CONTACT_ID, senderAccountCode = null, meeting = input)
        )

    private fun assertIstanbulResponse(response: MeetingPreviewResponse) {
        assertEquals("$CONTACT_ID:$ACCOUNT_CODE", response.targetKey)
        assertEquals(ACCOUNT_CODE, response.resolvedAccountCode)
        assertEquals("2026-09-11T07:00:00Z", response.startUtc)
        assertEquals("2026-09-11T07:30:00Z", response.endUtc)
        assertEquals(30, response.durationMinutes)
        assertEquals(
            "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)",
            response.meetingTime
        )
        assertEquals("2026/09/11 周五 15:00 – 2026/09/11 周五 15:30", response.chinaTime)
        assertTrue(response.textBody.startsWith("Dear Basdogan,\n\nThank you for confirming"), response.textBody)
        assertTrue(response.textBody.contains("Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)"))
        assertTrue(response.textBody.contains(ZOOM_URL))
        assertTrue(response.textBody.endsWith("Best regards,\nLuKai, Customer Care Officer\nQingfei Tech Talent Team China"))
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
        assertEquals(MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE, response.attachment.contentType)
        assertTrue(response.attachment.byteLength > 0)
        assertEquals(64, response.attachment.sha256.length)
        assertEquals(64, response.attachment.semanticSha256.length)
        assertEquals(response.attachment.semanticSha256.take(32) + "@qingfei-calendar", uidOf(response))
    }

    // ───────────────────────── I-1/I-2：通用邀请模板链路 ─────────────────────────

    @Test
    fun `preview renders reply snippet and custom text through the generic template chain`() {
        stubIdentity()
        stubMeetingPreparation()

        val response = preview()

        assertIstanbulResponse(response)
        // 回复片段与自定义文本都进入正文，且 ${...} 全部被替换（含 |Colleague 回退语法）
        assertTrue(response.textBody.contains(DEFAULT_SNIPPET.replace("\${expertFamilyName|Colleague}", "Basdogan")))
        assertTrue(response.textBody.contains(DEFAULT_CUSTOM_TEXT
            .replace("\${meeting_time}", response.meetingTime)
            .replace("\${zoom_url}", ZOOM_URL)
            .replace("\${senderName}", "LuKai")
            .replace("\${senderTitle}", "Customer Care Officer")
            .replace("\${teamName}", "Qingfei Tech Talent Team")
            .replace("\${countryName}", "China")))
        assertFalse(response.textBody.contains("\${"), "正文不得残留 \${...}")
        assertFalse(response.textBody.contains("{{"), "正文不得残留下划线花括号专用语法")
        assertFalse(response.textBody.contains("MANUAL_MEETING_CONFIRMATION"))
    }

    @Test
    fun `render map adds only meeting_time and zoom_url to the generic variables`() {
        stubIdentity()
        stubMeetingPreparation()
        val captured = captureRenderedVariables()

        val response = preview()

        assertIstanbulResponse(response)
        val rendered = captured.single()
        // 除通用 map 之外只允许新增/覆盖两个会议值（I-2）
        assertEquals(GENERIC_VARIABLES.keys + setOf("meeting_time", "zoom_url"), rendered.keys)
        assertFalse(rendered.containsKey("senderDisplayName"), "不得注入通用 map 之外的变量")
        assertEquals(response.meetingTime, rendered["meeting_time"])
        assertEquals(ZOOM_URL, rendered["zoom_url"])
        // 其余键逐字等于通用 map
        assertEquals(GENERIC_VARIABLES, rendered - "meeting_time" - "zoom_url")
    }

    @Test
    fun `family name fallback renders Colleague when the expert profile has no family name`() {
        stubIdentity()
        stubMeetingPreparation(mapOf("expertName" to "", "expertFamilyName" to ""))

        val response = preview()

        assertTrue(response.textBody.startsWith("Dear Colleague,"), response.textBody)
        // ICS 称呼回退到联系人姓名，而不是空串
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
    }

    @Test
    fun `preview is served purely from read collaborators and touches nothing else`() {
        stubIdentity()
        stubMeetingPreparation()

        val response = preview()

        assertIstanbulResponse(response)
        // preview 先解析目标、validateAndBuild 再校验 processing 归属（IP-2）→ 两次 findById
        Mockito.verify(inboundMailProcessingRepository, Mockito.times(2)).findById(PROCESSING_ID)
        Mockito.verify(expertContactRepository).findById(CONTACT_ID)
        Mockito.verify(mailSenderAccountService).getManualSendAccount(ACCOUNT_CODE)
        Mockito.verify(mailVariableService).resolveExpertProfileFor(anyValue(contact()))
        Mockito.verify(mailVariableService).buildVariables(
            anyValue(account()),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.any()
        )
        Mockito.verify(templateRepository).findByTemplateCodeAndEnabledTrue("MEETING_INVITATION")
        Mockito.verify(blockRepository).findAllByTemplateIdOrderByBlockOrderAsc(TEMPLATE_ID)
        Mockito.verify(replySnippetRepository).findById(SNIPPET_ID)
        // 无任何额外读取/写入交互：写库/发信入口未注入，额外调用会被严格捕获。
        Mockito.verifyNoMoreInteractions(
            inboundMailProcessingRepository,
            expertContactRepository,
            mailSenderAccountService,
            mailVariableService,
            templateRepository,
            blockRepository,
            qaRuleRepository,
            replySnippetRepository
        )
    }

    @Test
    fun `preview rejects when the enabled meeting invitation template is missing`() {
        stubIdentity()
        stubVariables()
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("MEETING_INVITATION"))
            .thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> { preview() }
        assertEquals("会议模板不可用，请重新选择或检查模板变量", ex.message)
    }

    @Test
    fun `preview rejects a blank rendered body`() {
        stubIdentity()
        stubInvitationTemplate(customText = "   ", snippet = null)
        stubVariables()

        val ex = assertThrows<IllegalArgumentException> { preview() }
        assertEquals("会议模板不可用，请重新选择或检查模板变量", ex.message)
    }

    @Test
    fun `preview rejects processing bound to a different contact`() {
        stubIdentity(processing = processing(expertContactId = 99))
        stubMeetingPreparation()

        val ex = assertThrows<IllegalArgumentException> {
            preview()
        }
        assertTrue(ex.message!!.contains("绑定 expertContact 99"))
        assertTrue(ex.message!!.contains("请求 contactId $CONTACT_ID"))
        Mockito.verifyNoMoreInteractions(expertContactRepository, mailSenderAccountService, mailVariableService)
    }

    @Test
    fun `preview throws NoSuchElementException when processing is missing`() {
        Mockito.`when`(inboundMailProcessingRepository.findById(PROCESSING_ID)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> { preview() }
        assertTrue(ex.message!!.contains("Inbound mail processing not found: $PROCESSING_ID"))
    }

    @Test
    fun `preview throws NoSuchElementException when contact is missing`() {
        stubIdentity(expert = contact(id = CONTACT_ID))
        Mockito.`when`(expertContactRepository.findById(CONTACT_ID)).thenReturn(Optional.empty())

        val ex = assertThrows<NoSuchElementException> { preview() }
        assertTrue(ex.message!!.contains("Expert contact not found: $CONTACT_ID"))
    }

    @Test
    fun `preview uses requested account when provided else inbound account like Pending`() {
        stubIdentity()
        stubMeetingPreparation()
        // requested 为空 → 来信账号
        val inboundDefault = preview()
        assertEquals(ACCOUNT_CODE, inboundDefault.resolvedAccountCode)
        Mockito.verify(mailSenderAccountService).getManualSendAccount(ACCOUNT_CODE)
        Mockito.verifyNoMoreInteractions(mailSenderAccountService)

        // requested 非空 → 请求账号优先
        Mockito.reset(mailSenderAccountService)
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("other-acc"))
            .thenReturn(account(code = "other-acc"))
        val request = MeetingPreviewRequest(
            contactId = CONTACT_ID,
            senderAccountCode = "other-acc",
            meeting = meetingInput()
        )
        val requested = service.preview(PROCESSING_ID, request)
        assertEquals("other-acc", requested.resolvedAccountCode)
        Mockito.verify(mailSenderAccountService).getManualSendAccount("other-acc")
        Mockito.verifyNoMoreInteractions(mailSenderAccountService)
    }

    @Test
    fun `options returns only target account generatedAt and default zone without touching templates`() {
        stubIdentity()

        val response = service.options(PROCESSING_ID, CONTACT_ID, null)

        assertEquals("$CONTACT_ID:$ACCOUNT_CODE", response.targetKey)
        assertEquals(ACCOUNT_CODE, response.resolvedAccountCode)
        assertEquals("Asia/Shanghai", response.defaultZoneId)
        // generatedAt：UTC ISO Instant，精度秒
        val generatedAt = Instant.parse(response.generatedAt)
        assertEquals(0, generatedAt.nano)
        Mockito.verify(inboundMailProcessingRepository).findById(PROCESSING_ID)
        Mockito.verify(expertContactRepository).findById(CONTACT_ID)
        Mockito.verify(mailSenderAccountService).getManualSendAccount(ACCOUNT_CODE)
        // I-1：options 不再读取专用模板目录或任何模板/变量读取
        Mockito.verifyNoMoreInteractions(
            inboundMailProcessingRepository,
            expertContactRepository,
            mailSenderAccountService,
            mailVariableService,
            templateRepository,
            blockRepository,
            qaRuleRepository,
            replySnippetRepository
        )
    }

    @Test
    fun `options validates identity the same way as preview`() {
        stubIdentity(processing = processing(expertContactId = 99))
        val ex = assertThrows<IllegalArgumentException> {
            service.options(PROCESSING_ID, CONTACT_ID, null)
        }
        assertTrue(ex.message!!.contains("不匹配"))
    }


    // ───────────────────────── I-3：时区与时间换算 ─────────────────────────

    @Test
    fun `timeZones catalog carries fixed common zones with offsets at given date`() {
        val zones = service.timeZones(LocalDate.of(2026, 9, 11))

        val istanbul = zones.first { it.id == "Europe/Istanbul" }
        assertEquals("土耳其 · 伊斯坦布尔", istanbul.labelZh)
        assertTrue(istanbul.aliases.contains("Türkiye"))
        assertTrue(istanbul.aliases.contains("Turkey"))
        assertTrue(istanbul.aliases.contains("Istanbul"))
        assertEquals("UTC+3", istanbul.offsetLabel)
        assertEquals(10800, istanbul.offsetSeconds)

        val kolkata = zones.first { it.id == "Asia/Kolkata" }
        assertEquals("UTC+5:30", kolkata.offsetLabel)
        assertEquals(19800, kolkata.offsetSeconds)
        assertTrue(kolkata.aliases.contains("加尔各答"))

        val calcutta = zones.first { it.id == "Asia/Calcutta" }
        assertEquals("UTC+5:30", calcutta.offsetLabel)

        val london = zones.first { it.id == "Europe/London" }
        assertEquals("UTC+1", london.offsetLabel)

        val newYork = zones.first { it.id == "America/New_York" }
        assertEquals("UTC-4", newYork.offsetLabel)
        assertEquals(-14400, newYork.offsetSeconds)

        val shanghai = zones.first { it.id == "Asia/Shanghai" }
        assertEquals("中国 · 北京 / 上海", shanghai.labelZh)
        assertEquals("UTC+8", shanghai.offsetLabel)

        assertTrue(zones.first().id == "Europe/Istanbul", "固定常用表先行")
        assertTrue(zones.indexOfFirst { it.id == "Asia/Shanghai" } < zones.indexOfFirst { it.id == "Asia/Tokyo" })
        // 短名（无 /）与 Etc/* 被排除
        assertTrue(zones.none { it.id == "GMT" })
        assertTrue(zones.none { it.id.startsWith("Etc/") })
        // 未映射 id 也可用（英文标签兜底）
        assertTrue(zones.any { it.id == "America/Argentina/Buenos_Aires" })
    }

    @Test
    fun `istanbul morning preview matches the approved example`() {
        stubIdentity()
        stubMeetingPreparation()
        assertIstanbulResponse(preview())
    }

    @Test
    fun `shanghai meeting converts to same china display clock`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview(meetingInput(zoneId = "Asia/Shanghai"))
        assertEquals("2026-09-11T02:00:00Z", response.startUtc)
        assertEquals("2026-09-11T02:30:00Z", response.endUtc)
        assertEquals("2026/09/11 周五 10:00 – 2026/09/11 周五 10:30", response.chinaTime)
        assertTrue(response.meetingTime.contains("China Standard Time (UTC+8)"))
    }

    @Test
    fun `kolkata half-hour offset is converted independently`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview(meetingInput(zoneId = "Asia/Kolkata"))
        assertEquals("2026-09-11T04:30:00Z", response.startUtc)
        assertEquals("2026-09-11T05:00:00Z", response.endUtc)
        assertEquals(30, response.durationMinutes)
        assertTrue(response.meetingTime.contains("Kolkata Time (UTC+5:30)"))
    }

    @Test
    fun `sydney overnight meeting writes full dates on both ends`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview(
            meetingInput(
                zoneId = "Australia/Sydney",
                startLocal = "2026-09-11T23:00",
                endLocal = "2026-09-12T00:30"
            )
        )
        assertEquals("2026-09-11T13:00:00Z", response.startUtc)
        assertEquals("2026-09-11T14:30:00Z", response.endUtc)
        assertEquals(90, response.durationMinutes)
        assertEquals(
            "Friday, September 11, 2026, from 11:00 PM to Saturday, September 12, 2026, at 12:30 AM Sydney Time (UTC+10)",
            response.meetingTime
        )
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
    }

    @Test
    fun `london dst spring-forward shows start and end offset change`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview(
            meetingInput(
                zoneId = "Europe/London",
                startLocal = "2026-03-29T00:30",
                endLocal = "2026-03-29T02:30"
            )
        )
        assertEquals("2026-03-29T00:30:00Z", response.startUtc)
        assertEquals("2026-03-29T01:30:00Z", response.endUtc)
        assertEquals(60, response.durationMinutes)
        assertEquals(
            "Sunday, March 29, 2026, from 12:30 AM to 2:30 AM London Time (UTC+0 → UTC+1)",
            response.meetingTime
        )
    }

    @Test
    fun `new york dst gap local time is rejected with fixed message`() {
        stubIdentity()
        stubMeetingPreparation()
        val ex = assertThrows<IllegalArgumentException> {
            preview(
                meetingInput(
                    zoneId = "America/New_York",
                    startLocal = "2026-03-08T02:30",
                    endLocal = "2026-03-08T03:30"
                )
            )
        }
        assertEquals("该当地时间不存在，请避开夏令时跳时区间", ex.message)
    }

    @Test
    fun `new york dst overlap local time is rejected with fixed message`() {
        stubIdentity()
        stubMeetingPreparation()
        val ex = assertThrows<IllegalArgumentException> {
            preview(
                meetingInput(
                    zoneId = "America/New_York",
                    startLocal = "2026-11-01T01:30",
                    endLocal = "2026-11-01T02:30"
                )
            )
        }
        assertEquals("该当地时间出现两次，请选择不处于夏令时回拨区间的时间", ex.message)
    }

    @Test
    fun `invalid zone and missing times and duration use fixed messages`() {
        stubIdentity()
        stubMeetingPreparation()
        val badZone = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoneId = "Mars/Olympus"))
        }
        assertEquals("请选择有效会议时区", badZone.message)

        val badShortZone = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoneId = "GMT"))
        }
        assertEquals("请选择有效会议时区", badShortZone.message)

        val missingStart = assertThrows<IllegalArgumentException> {
            preview(meetingInput(startLocal = ""))
        }
        assertEquals("请填写日期和起止时间", missingStart.message)

        val malformedEnd = assertThrows<IllegalArgumentException> {
            preview(meetingInput(endLocal = "2026-09-11T25:00"))
        }
        assertEquals("请填写日期和起止时间", malformedEnd.message)

        val tooLong = assertThrows<IllegalArgumentException> {
            preview(
                meetingInput(
                    startLocal = "2026-09-11T10:00",
                    endLocal = "2026-09-12T11:00"
                )
            )
        }
        assertEquals("会议时长须大于 0 且不超过 24 小时", tooLong.message)

        val reversed = assertThrows<IllegalArgumentException> {
            preview(
                meetingInput(
                    startLocal = "2026-09-11T11:00",
                    endLocal = "2026-09-11T10:00"
                )
            )
        }
        assertEquals("会议时长须大于 0 且不超过 24 小时", reversed.message)

        val outOfYearRange = assertThrows<IllegalArgumentException> {
            preview(meetingInput(startLocal = "1800-01-01T10:00"))
        }
        assertTrue(outOfYearRange.message!!.contains("1900"))
    }

    @Test
    fun `past meetings are allowed for review and replay`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview(
            meetingInput(
                startLocal = "2025-03-05T10:00",
                endLocal = "2025-03-05T10:30"
            )
        )
        assertEquals("2025-03-05T07:00:00Z", response.startUtc)
        assertEquals("2025-03-05T07:30:00Z", response.endUtc)
    }

    // ───────────────────────── I-5：受限内容与安全 ─────────────────────────

    @Test
    fun `html escapes expert markup and anchor only the zoom url`() {
        stubIdentity()
        stubInvitationTemplate(customText = "Dear \${expertName},\n\nJoin: \${zoom_url}")
        stubVariables(mapOf("expertName" to "<img src=x onerror=alert(1)>"))

        val response = preview()

        // 文本中仍是字面量；HTML 中被逐段 escape，绝不成标签
        assertTrue(response.textBody.contains("Dear <img src=x onerror=alert(1)>,"))
        assertFalse(response.htmlBody.contains("<img"))
        assertTrue(response.htmlBody.contains("&lt;img"))
        assertFalse(response.htmlBody.contains("<script"))
        // 只把 zoom url 转为同文字链接
        assertTrue(
            response.htmlBody.contains(
                "<a href=\"$ZOOM_URL\" target=\"_blank\" rel=\"noopener noreferrer\">$ZOOM_URL</a>"
            )
        )
    }

    @Test
    fun `zoom url query parameters survive html anchor and ics url`() {
        stubIdentity()
        stubMeetingPreparation()
        val url = "https://zoom.us/j/87102801187?pwd=RH3bf4vbH0SoyTq2UW1Dzzuag4kISa.1&tk=keepcase&x=Y"
        val response = preview(meetingInput(zoomUrl = url))
        // HTML 中 & 被实体转义，锚文本/链接保持完整查询
        assertTrue(response.htmlBody.contains("pwd=RH3bf4vbH0SoyTq2UW1Dzzuag4kISa.1&amp;tk=keepcase&amp;x=Y"))
        // ICS URL/LOCATION 属性保持原查询顺序与大小写（URL 按 URI 原样输出）
        assertEquals(url, propertyLine(response, "URL"))
        assertEquals(url, propertyLine(response, "LOCATION"))
        assertTrue(response.textBody.contains(url))
    }

    @Test
    fun `zoom url rejects evil hosts userinfo fragments and http`() {
        stubIdentity()
        stubMeetingPreparation()
        val evilSuffix = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "https://zoom.us.evil.example/j/1"))
        }
        assertTrue(evilSuffix.message!!.contains("有效的 Zoom"))

        val evilUserInfo = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "https://zoom.us@evil.example/j/1"))
        }
        assertTrue(evilUserInfo.message!!.contains("有效的 Zoom"))

        val fragment = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "https://zoom.us/j/1#frag"))
        }
        assertTrue(fragment.message!!.contains("有效的 Zoom"))

        val plainHttp = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "http://zoom.us/j/1"))
        }
        assertTrue(plainHttp.message!!.contains("有效的 Zoom"))

        val embeddedBraces = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "https://zoom.us/j/1{{x}}"))
        }
        assertTrue(embeddedBraces.message!!.contains("模板变量字符"))

        val notMeetingPath = assertThrows<IllegalArgumentException> {
            preview(meetingInput(zoomUrl = "https://zoom.us/not-a-meeting"))
        }
        assertTrue(notMeetingPath.message!!.contains("有效的 Zoom"))
    }

    @Test
    fun `generatedAt must be utc iso seconds and is frozen when blank`() {
        stubIdentity()
        stubMeetingPreparation()
        val notUtc = assertThrows<IllegalArgumentException> {
            preview(meetingInput(generatedAt = "2026-09-09T03:00:40+02:00"))
        }
        assertTrue(notUtc.message!!.contains("UTC"))

        val garbage = assertThrows<IllegalArgumentException> {
            preview(meetingInput(generatedAt = "yesterday"))
        }
        assertTrue(garbage.message!!.contains("generatedAt"))

        // 空值 → 服务端冻结为当前秒，且回传、DTSTAMP 一致
        val response = preview(meetingInput(generatedAt = ""))
        assertNotNull(response.meeting.generatedAt)
        val frozen = Instant.parse(response.meeting.generatedAt)
        assertEquals(0, frozen.nano)
        assertEquals(basicUtc(frozen), propertyLine(response, "DTSTAMP"))
    }

    // ───────────────────────── I-6：ICS 与可重算快照 ─────────────────────────

    @Test
    fun `ics structure uid and fixed property order`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview()
        val ics = response.attachment.icsText
        assertTrue(ics.endsWith("\r\n"))

        val propertyKeys = unfoldedPropertyNames(ics)
        assertEquals(
            listOf(
                "BEGIN", "VERSION", "PRODID", "CALSCALE", "BEGIN", "UID",
                "DTSTAMP", "DTSTART", "DTEND", "SUMMARY", "DESCRIPTION", "LOCATION", "URL",
                "STATUS", "TRANSP", "END", "END"
            ),
            propertyKeys
        )
        // 首尾与 VEVENT 边界行逐字固定
        val unfoldedLines = ics.replace("\r\n ", "").split("\r\n").filter { it.isNotEmpty() }
        assertEquals("BEGIN:VCALENDAR", unfoldedLines.first())
        assertEquals("END:VCALENDAR", unfoldedLines.last())
        assertTrue(unfoldedLines.contains("BEGIN:VEVENT"))
        assertTrue(unfoldedLines.contains("END:VEVENT"))
        assertEquals("20260911T070000Z", propertyLine(response, "DTSTART"))
        assertEquals("20260911T073000Z", propertyLine(response, "DTEND"))
        assertEquals("20260909T030040Z", propertyLine(response, "DTSTAMP"))
        assertEquals("Meeting with Professor Basdogan | Qingfei Tech Talent Team", propertyLine(response, "SUMMARY"))
        assertEquals(ZOOM_URL, propertyLine(response, "URL"))
        // 无 METHOD/ORGANIZER/ATTENDEE；恰好 1 个 VEVENT
        assertFalse(ics.contains("METHOD"))
        assertFalse(ics.contains("ATTENDEE"))
        assertFalse(ics.contains("ORGANIZER"))
        assertEquals(1, ics.split("BEGIN:VEVENT").size - 1)
        assertEquals(1, ics.split("END:VEVENT").size - 1)
    }

    @Test
    fun `description escapes and folds back to the original value`() {
        stubIdentity()
        stubMeetingPreparation()
        val response = preview()
        val description = propertyLine(response, "DESCRIPTION")
        assertEquals(
            "Friday\\, September 11\\, 2026\\, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)\\n\\n" +
                "Join Zoom meeting:\\n$ZOOM_URL\\n\\nLuKai\\, Customer Care Officer\\nQingfei Tech Talent Team China",
            description
        )
        assertEquals(
            "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)\n\n" +
                "Join Zoom meeting:\n$ZOOM_URL\n\nLuKai, Customer Care Officer\nQingfei Tech Talent Team China",
            unescapeText(description)
        )
    }

    @Test
    fun `physical ics lines stay within 75 utf-8 bytes and never split codepoints`() {
        stubIdentity()
        val longChineseSignature = "签名🎉 中文长签名，" + "长".repeat(150) + "，结尾 emoji 🚀 ok"
        stubInvitationTemplate()
        stubVariables(
            mapOf(
                "senderName" to longChineseSignature,
                "senderTitle" to "",
                "teamName" to "",
                "countryName" to ""
            )
        )

        val response = preview()
        val ics = response.attachment.icsText

        val physicalLines = ics.split("\r\n")
        assertTrue(physicalLines.last().isEmpty(), "CRLF 结尾")
        physicalLines.filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(
                line.toByteArray(Charsets.UTF_8).size <= 75,
                "physical line exceeds 75 bytes: [${line.take(40)}] (${line.toByteArray(Charsets.UTF_8).size})"
            )
        }
        // 多字节码点不被折行截断：unfold + unescape 必须逐字还原
        val expectedRawDescription =
            response.meetingTime + "\n\nJoin Zoom meeting:\n$ZOOM_URL\n\n" + longChineseSignature
        assertEquals(
            expectedRawDescription,
            unescapeText(propertyLine(response, "DESCRIPTION"))
        )
    }

    @Test
    fun `same configuration recomputes identical bytes and generatedAt only changes sha`() {
        stubIdentity()
        stubMeetingPreparation()
        val first = preview()
        val second = preview()
        assertEquals(first.attachment.icsText, second.attachment.icsText)
        assertEquals(first.attachment.sha256, second.attachment.sha256)
        assertEquals(first.attachment.semanticSha256, second.attachment.semanticSha256)
        assertEquals(first.attachment.byteLength, second.attachment.byteLength)

        // 只改 generatedAt：DTSTAMP/sha256 变，semanticSha256 不变（语义不含 generatedAt）
        val shifted = preview(meetingInput(generatedAt = "2026-09-09T03:01:01Z"))
        assertNotEquals(first.attachment.sha256, shifted.attachment.sha256)
        assertEquals("20260909T030101Z", propertyLine(shifted, "DTSTAMP"))
        assertEquals(first.attachment.semanticSha256, shifted.attachment.semanticSha256)

        // 语义变化（换 processing 实例）会改变 semanticSha256 与 UID
        stubIdentity(processing = processing(id = 8))
        stubMeetingPreparation()
        val otherProcessing = service.preview(
            8,
            MeetingPreviewRequest(contactId = CONTACT_ID, senderAccountCode = null, meeting = meetingInput())
        )
        assertNotEquals(first.attachment.semanticSha256, otherProcessing.attachment.semanticSha256)
        assertNotEquals(uidOf(first), uidOf(otherProcessing))
    }

    @Test
    fun `filename falls back to expert for non-ascii salutation`() {
        stubIdentity()
        stubInvitationTemplate()
        stubVariables(mapOf("expertName" to "王教授", "expertFamilyName" to "王教授", "senderName" to "", "senderTitle" to "",
            "teamName" to "", "countryName" to ""))

        val response = preview()
        assertEquals("meeting-2026-09-11-expert.ics", response.attachment.filename)
        assertTrue(MeetingConfirmationDomain.CALENDAR_FILENAME_REGEX.matches(response.attachment.filename))

        stubVariables(mapOf("expertName" to "Dr. Anne-Marie O'Brien -- x"))
        val punctuationName = preview()
        assertEquals("meeting-2026-09-11-Dr-Anne-Marie-O-Brien-x.ics", punctuationName.attachment.filename)
    }

    @Test
    fun `meeting input is echoed back for reuse`() {
        stubIdentity()
        stubMeetingPreparation()
        val input = meetingInput()
        val response = preview(input = input)
        assertEquals(input, response.meeting)
    }

    @Test
    fun `legacy meeting input JSON with dedicated-template fields still deserializes and is ignored`() {
        stubIdentity()
        stubMeetingPreparation()
        val legacy = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            .readValue(
                """{"templateId":100,"templateBody":"Dear {{expert_salutation}}",""" +
                    """"expertSalutation":"Professor Basdogan","zoneId":"Europe/Istanbul",""" +
                    """"startLocal":"2026-09-11T10:00","endLocal":"2026-09-11T10:30",""" +
                    """"zoomUrl":"$ZOOM_URL","senderSignature":"LuKai","generatedAt":"$GENERATED_AT"}""",
                MeetingInput::class.java
            )

        // 旧字段只作兼容载体：正文/称呼/签名一律取自通用模板链路
        val response = preview(legacy)
        assertIstanbulResponse(response)
        assertFalse(response.textBody.contains("{{expert_salutation}}"))
        assertFalse(response.textBody.contains("\${"))
    }

    // ───────────────────────── CalendarAttachmentCodec（I-6/02 契约） ─────────────────────────

    private fun validSnapshot() = CalendarAttachmentSnapshot(
        schemaVersion = 1,
        filename = "meeting-2026-09-11-Professor-Basdogan.ics",
        contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
        icsText = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n",
        sha256 = sha256Of("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n"),
        semanticSha256 = "a".repeat(64)
    )

    @Test
    fun `codec roundtrip preserves the snapshot`() {
        val snapshot = validSnapshot()
        val json = CalendarAttachmentCodec.serialize(snapshot)
        val parsed = CalendarAttachmentCodec.parseOrNull(json)
        assertEquals(snapshot, parsed)
        // schemaVersion 缺失/未知字段 → 损坏 null
        assertNull(CalendarAttachmentCodec.parseOrNull(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertNull(CalendarAttachmentCodec.parseOrNull(json.replace("\"schemaVersion\":1,", "")))
        assertNull(CalendarAttachmentCodec.parseOrNull("""{"schemaVersion":1,"extra":true}"""))
    }

    @Test
    fun `codec rejects damaged json hashes schema content type and size`() {
        val snapshot = validSnapshot()
        val json = CalendarAttachmentCodec.serialize(snapshot)

        assertNull(CalendarAttachmentCodec.parseOrNull(null))
        assertNull(CalendarAttachmentCodec.parseOrNull(""))
        assertNull(CalendarAttachmentCodec.parseOrNull("   "))
        assertNull(CalendarAttachmentCodec.parseOrNull("not json"))
        // sha 不匹配 icsText
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.sha256, "f".repeat(64))
            )
        )
        // semanticSha256 非 64 位小写 hex
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.semanticSha256, "F".repeat(64))
            )
        )
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.semanticSha256, "abc")
            )
        )
        // 非法 contentType
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE, "text/calendar")
            )
        )
        // 非法 filename
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.filename, "meeting-2026-09-11-x.ics.ics")
            )
        )
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.filename, "../meeting-2026-09-11-x.ics")
            )
        )
        // 超长文件名 token（61 chars）→ 损坏
        assertNull(
            CalendarAttachmentCodec.parseOrNull(
                json.replace(snapshot.filename, "meeting-2026-09-11-" + "A".repeat(61) + ".ics")
            )
        )
        // >64KiB（sha 本身正确也拒绝）
        val oversizedIcs = "x".repeat(64 * 1024 + 1)
        val oversizedJson = """{"schemaVersion":1,"filename":"meeting-2026-09-11-x.ics",""" +
            """"contentType":"text/calendar; charset=UTF-8","icsText":"$oversizedIcs",""" +
            """"sha256":"${sha256Of(oversizedIcs)}","semanticSha256":"${"a".repeat(64)}"}"""
        assertNull(CalendarAttachmentCodec.parseOrNull(oversizedJson))
    }

    @Test
    fun `codec serialize rejects invalid snapshots with IllegalArgumentException`() {
        val base = validSnapshot()
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(schemaVersion = 2))
        }
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(contentType = "text/plain"))
        }
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(filename = "evil.ics"))
        }
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(sha256 = "0".repeat(64)))
        }
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(semanticSha256 = "XYZ"))
        }
        assertThrows<IllegalArgumentException> {
            CalendarAttachmentCodec.serialize(base.copy(icsText = "x".repeat(64 * 1024 + 1)))
        }
    }

    // ───────────────────────── 帮助方法 ─────────────────────────

    private fun uidOf(response: MeetingPreviewResponse): String {
        val ics = response.attachment.icsText
        val uidLine = ics.split("\r\n").first { it.startsWith("UID:") }
        return uidLine.removePrefix("UID:")
    }

    private fun propertyLine(response: MeetingPreviewResponse, property: String): String {
        val ics = response.attachment.icsText
        // unfold：折行插入的 CRLF+空格还原为无折行单属性行
        val unfolded = ics.replace("\r\n ", "")
        return unfolded.split("\r\n")
            .first { it.startsWith("$property:") }
            .removePrefix("$property:")
    }

    private fun unfoldedPropertyNames(ics: String): List<String> {
        val unfolded = ics.replace("\r\n ", "")
        return unfolded.split("\r\n").filter { it.isNotEmpty() }.map { line ->
            line.substringBefore(':')
        }
    }

    private fun basicUtc(instant: Instant): String =
        java.time.ZoneOffset.UTC.let { offset ->
            instant.atOffset(offset).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            )
        }

    private fun unescapeText(escaped: String): String {
        val sb = StringBuilder(escaped.length)
        var index = 0
        while (index < escaped.length) {
            val ch = escaped[index]
            if (ch == '\\' && index + 1 < escaped.length) {
                when (escaped[index + 1]) {
                    'n' -> sb.append('\n')
                    ';' -> sb.append(';')
                    ',' -> sb.append(',')
                    '\\' -> sb.append('\\')
                    else -> {
                        sb.append(ch)
                        sb.append(escaped[index + 1])
                    }
                }
                index += 2
            } else {
                sb.append(ch)
                index += 1
            }
        }
        return sb.toString()
    }

    private fun sha256Of(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
