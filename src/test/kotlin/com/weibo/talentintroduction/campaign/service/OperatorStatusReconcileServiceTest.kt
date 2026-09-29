package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

/**
 * 04 P-C 对账作业单测（T-4）：
 * - I-4：期望值映射逐条正确（CONTACTED/INVITED/REPLIED/MATERIALS_RECEIVED/EMAIL_INVALID×2 + 最高里程碑）
 * - I-2：人工覆盖被单列（HUMAN_OVERRIDE），不计入异常
 * - I-3：COMPLETED 不判异常
 * - I-1：全程零写入——全部写方法 verify(never) + verifyNoMoreInteractions 总闭包 + ES 仅 _search
 */
class OperatorStatusReconcileServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val operatorActionLogRepository = Mockito.mock(OperatorActionLogRepository::class.java)
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val objectMapper = ObjectMapper()
    private val service = OperatorStatusReconcileService(
        expertContactRepository,
        mailRecordRepository,
        mailAttachmentRepository,
        bounceRecordRepository,
        operatorActionLogRepository,
        restTemplate,
        ElasticsearchProperties(
            baseUrl = "http://es:9200",
            username = "es-user",
            password = "es-pass",
            rawIndexName = "orcid_info",
            candidateIndexName = "orcid_info_candidate",
            applicationIndexName = "orcid_info_application"
        )
    )

    // ── fixtures ──────────────────────────────────────────────────────────────

    private fun contact(id: Long, orcid: String, operatorStatus: String = "NOT_CONTACTED"): ExpertContact =
        ExpertContact(
            id = id,
            campaignId = 1,
            orcidId = orcid,
            expertEmail = "expert$id@example.com",
            expertName = "Expert $id",
            operatorStatus = operatorStatus
        )

    private fun mail(
        id: Long,
        contactId: Long,
        direction: String,
        mailType: String,
        sendStatus: String? = null,
        errorSummary: String? = null
    ): MailRecord = MailRecord(
        id = id,
        expertContactId = contactId,
        direction = direction,
        mailType = mailType,
        messageId = "msg-$id",
        inReplyTo = null,
        subject = "subject-$id",
        body = null,
        matchedQaRuleId = null,
        sendStatus = sendStatus,
        receivedAt = null,
        sentAt = null,
        errorSummary = errorSummary
    )

    private fun attachment(id: Long, mailRecordId: Long): MailAttachment =
        MailAttachment(
            id = id,
            mailRecordId = mailRecordId,
            fileName = "cv.pdf",
            contentType = "application/pdf",
            fileSize = 100L,
            storagePath = "/tmp/$id.pdf"
        )

    private fun hardBounce(id: Long, contactId: Long, dsnStatus: String = "5.1.1"): BounceRecord =
        BounceRecord(
            id = id,
            senderAccountCode = "ACC",
            bounceMessageId = "bounce-$id",
            originalMessageId = "orig-$id",
            originalExpertContactId = contactId,
            bounceType = "HARD",
            dsnStatus = dsnStatus,
            bounceReason = "user unknown",
            receivedAt = java.time.LocalDateTime.now()
        )

    private fun stubRepositories(
        contacts: List<ExpertContact>,
        records: List<MailRecord>,
        attachments: List<MailAttachment> = emptyList(),
        bounces: List<BounceRecord> = emptyList(),
        humanOverrideContactIds: List<Long> = emptyList()
    ) {
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(contacts)
        Mockito.`when`(mailRecordRepository.findAll()).thenReturn(records)
        Mockito.`when`(mailAttachmentRepository.findAll()).thenReturn(attachments)
        Mockito.`when`(bounceRecordRepository.findAll()).thenReturn(bounces)
        Mockito.`when`(operatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs(Mockito.anyList<Long>() ?: emptyList()))
            .thenReturn(humanOverrideContactIds)
    }

    /** Mockito matcher + 非空兜底值（照 ManualOutreachTxHelperTest.anyValue 先例：Kotlin 非空参数下 any() 返回 null 会触发空检查）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    /** stub ES 三层 _search：同一响应返回给 RAW/CANDIDATE/APPLICATION 三层（CANDIDATE 优先解析）。 */
    private fun stubEs(entries: List<Pair<String, String?>>) {
        val root = objectMapper.createObjectNode()
        val hitsArray = root.putObject("hits").putArray("hits")
        for ((orcid, status) in entries) {
            val source = hitsArray.addObject().putObject("_source")
            source.put("orcidId", orcid)
            if (status != null) source.put("operatorStatus", status)
        }
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(root))
    }

    private fun sampleOf(report: ReconcileReport, category: String): ReconcileSample =
        report.samples.first { it.category == category }

    // ── I-4 期望值映射 ────────────────────────────────────────────────────────

    @Test
    fun `expected CONTACTED when introduction sent`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT"))
        )
        val report = service.reconcile()
        assertEquals(1, report.total)
        assertEquals(1, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(0, report.humanOverride)
        assertEquals(0, report.consistent)
        val sample = sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED)
        assertEquals("CONTACTED", sample.expectedStatus)
        assertEquals("NOT_CONTACTED", sample.dbStatus)
        assertEquals("NOT_CONTACTED", sample.esStatus)
    }

    @Test
    fun `expected INVITED when meeting invitation sent`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "OUTBOUND", "MEETING_INVITATION", sendStatus = "SENT"))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("INVITED", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @Test
    fun `expected REPLIED when inbound exists`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "INBOUND", "REPLY", sendStatus = null))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("REPLIED", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @Test
    fun `expected MATERIALS_RECEIVED when inbound has material attachment, not for outbound attachment`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1"), contact(2, "ORCID-2")),
            records = listOf(
                mail(10, 1, "INBOUND", "REPLY"),
                mail(20, 2, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT")
            ),
            attachments = listOf(
                attachment(100, mailRecordId = 10),
                // 附件挂在 OUTBOUND 记录上：不满足"INBOUND 有材料附件"，只到 CONTACTED
                attachment(200, mailRecordId = 20)
            )
        )
        val report = service.reconcile()
        assertEquals(2, report.dbVsExpected)
        val materials = sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED)
        assertEquals(1L, materials.contactId)
        assertEquals("MATERIALS_RECEIVED", materials.expectedStatus)
        val contacted = report.samples.first { it.category == OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED && it.contactId == 2L }
        assertEquals("CONTACTED", contacted.expectedStatus)
    }

    @Test
    fun `expected EMAIL_INVALID on hard bounce record`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = emptyList(),
            bounces = listOf(hardBounce(100, contactId = 1))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("EMAIL_INVALID", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @Test
    fun `expected EMAIL_INVALID on permanent smtp failure of introduction`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(
                mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "FAILED", errorSummary = "PERMANENT:550:550 5.1.1 User unknown")
            )
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("EMAIL_INVALID", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    /**
     * 03 I-1/I-5：对账与在线判定共用同一份持久化摘要（`PERMANENT:<5xx>:<详情>`），
     * 只有行首可信增强码落在白名单才构成地址证据；政策拒绝/裸码/英文说明都不算。
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "PERMANENT:550:550 5.7.1 Blocked",
            "PERMANENT:550:5.7.1 policy rejection",
            "PERMANENT:550:user unknown",
            "PERMANENT:550:550",
            "PERMANENT:554:550 5.1.1 User unknown",
            "PERMANENT:550:550 5.1.1 User unknown\n550 5.1.2 mailbox unavailable",
            "PERMANENT:550:reason: 5.1.1 user unknown",
            "TRANSIENT:421:421 5.1.1 slow down"
        ]
    )
    fun `permanent smtp failure without address evidence never implies EMAIL_INVALID`(errorSummary: String) {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "FAILED", errorSummary = errorSummary))
        )
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `a truncated permanent summary detail is never address evidence`() {
        // 详情恰好 200 字符 = buildSmtpErrorSummary 的截断上限：尾部 5.1.10 可能被截成 5.1.1 → 保守 false。
        val protocolLine = "550 5.1.1 User unknown "
        val truncatedDetail = protocolLine + "x".repeat(200 - protocolLine.length)
        assertEquals(200, truncatedDetail.length)
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(
                mail(
                    10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "FAILED",
                    errorSummary = "PERMANENT:550:$truncatedDetail"
                )
            )
        )
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `permanent smtp failure never overrides a replied milestone`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "REPLIED")),
            records = listOf(
                mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "FAILED", errorSummary = "PERMANENT:550:550 5.1.1 User unknown"),
                mail(11, 1, "INBOUND", "REPLY")
            )
        )
        stubEs(listOf("ORCID-1" to "REPLIED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `permanent smtp failure of a reply or inbound record is not first-mail evidence`() {
        // 同一 PERMANENT 文本只在 OUTBOUND+INTRODUCTION+FAILED 上构成首封地址证据：
        // 回复失败行与 INBOUND 行达不到该条件，期望值只由既有通信事实（INBOUND → REPLIED）决定。
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "REPLIED")),
            records = listOf(
                mail(10, 1, "OUTBOUND", "REPLY", sendStatus = "FAILED", errorSummary = "PERMANENT:550:550 5.1.1 User unknown"),
                mail(11, 1, "INBOUND", "REPLY", sendStatus = "FAILED", errorSummary = "PERMANENT:550:550 5.1.1 User unknown")
            )
        )
        stubEs(listOf("ORCID-1" to "REPLIED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `transient smtp failure does not imply EMAIL_INVALID`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(
                mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "FAILED", errorSummary = "TRANSIENT:421:try again later")
            )
        )
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `expected status is highest milestone reached`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(
                mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT"),
                mail(11, 1, "INBOUND", "REPLY"),
                mail(12, 1, "OUTBOUND", "MEETING_INVITATION", sendStatus = "SENT")
            )
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("INVITED", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    // ── I-4 里程碑优先：合格地址退信不得遮盖已有通信事实 ────────────────────────

    @Test
    fun `qualifying address evidence does not override the replied milestone`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "REPLIED")),
            records = listOf(mail(10, 1, "INBOUND", "REPLY")),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.1.1"))
        )
        stubEs(listOf("ORCID-1" to "REPLIED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `qualifying address evidence does not override the materials received milestone`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "MATERIALS_RECEIVED")),
            records = listOf(mail(10, 1, "INBOUND", "REPLY")),
            attachments = listOf(attachment(100, mailRecordId = 10)),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.1.10"))
        )
        stubEs(listOf("ORCID-1" to "MATERIALS_RECEIVED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `qualifying address evidence does not override the invited milestone`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "INVITED")),
            records = listOf(mail(10, 1, "OUTBOUND", "MEETING_INVITATION", sendStatus = "SENT")),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.1.2"))
        )
        stubEs(listOf("ORCID-1" to "INVITED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `contacted with qualifying address evidence expects EMAIL_INVALID`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "CONTACTED")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT")),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.1.1"))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("EMAIL_INVALID", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @Test
    fun `non address permanent bounce with a sent introduction still expects CONTACTED`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT")),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.7.1"))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("CONTACTED", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @ParameterizedTest
    @ValueSource(strings = ["5.1.1", "5.1.2", "5.1.3", "5.1.10"])
    fun `hard bounce with address evidence expects EMAIL_INVALID`(dsnStatus: String) {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = emptyList(),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = dsnStatus))
        )
        val report = service.reconcile()
        assertEquals(1, report.dbVsExpected)
        assertEquals("EMAIL_INVALID", sampleOf(report, OperatorStatusReconcileService.CATEGORY_DB_VS_EXPECTED).expectedStatus)
    }

    @ParameterizedTest
    @ValueSource(strings = ["5.7.1", "5.4.1", "5.2.2", "5.0.0", "5.1.100"])
    fun `hard bounce without address evidence never implies EMAIL_INVALID`(dsnStatus: String) {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = emptyList(),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = dsnStatus))
        )
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.consistent)
    }

    @Test
    fun `soft bounce is never address evidence for reconcile`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = emptyList(),
            bounces = listOf(hardBounce(100, contactId = 1, dsnStatus = "5.1.1").copy(bounceType = "SOFT"))
        )
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.consistent)
    }

    // ── I-2 人工覆盖 ──────────────────────────────────────────────────────────

    @Test
    fun `human override is singled out and not counted as anomaly`() {
        // 有 CHANGE_OPERATOR_STATUS 日志 + DB 与事件期望不符（无任何邮件却设为 CONTACTED）
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "CONTACTED")),
            records = emptyList(),
            humanOverrideContactIds = listOf(1L)
        )
        stubEs(listOf("ORCID-1" to "CONTACTED"))
        val report = service.reconcile()
        assertEquals(1, report.humanOverride)
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(0, report.consistent)
        val sample = sampleOf(report, OperatorStatusReconcileService.CATEGORY_HUMAN_OVERRIDE)
        assertEquals(1L, sample.contactId)
        assertEquals("NOT_CONTACTED", sample.expectedStatus)
        assertEquals("CONTACTED", sample.dbStatus)
        assertEquals("CONTACTED", sample.esStatus)
    }

    // ── I-3 COMPLETED ─────────────────────────────────────────────────────────

    @Test
    fun `COMPLETED is not anomalous even when events suggest otherwise`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1", operatorStatus = "COMPLETED")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT"))
        )
        stubEs(listOf("ORCID-1" to "COMPLETED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(0, report.esVsDb)
        assertEquals(0, report.humanOverride)
        assertEquals(1, report.consistent)
    }

    // ── ES 与 DB 不符 ─────────────────────────────────────────────────────────

    @Test
    fun `es vs db mismatch is reported with both sides`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = emptyList()
        )
        stubEs(listOf("ORCID-1" to "CONTACTED"))
        val report = service.reconcile()
        assertEquals(0, report.dbVsExpected)
        assertEquals(1, report.esVsDb)
        val sample = sampleOf(report, OperatorStatusReconcileService.CATEGORY_ES_VS_DB)
        assertEquals("CONTACTED", sample.esStatus)
        assertEquals("NOT_CONTACTED", sample.dbStatus)
        assertEquals("NOT_CONTACTED", sample.expectedStatus)
    }

    // ── I-1 零写入 ────────────────────────────────────────────────────────────

    @Test
    fun `reconcile performs zero writes`() {
        stubRepositories(
            contacts = listOf(contact(1, "ORCID-1")),
            records = listOf(mail(10, 1, "OUTBOUND", "INTRODUCTION", sendStatus = "SENT"))
        )
        stubEs(listOf("ORCID-1" to "CONTACTED"))
        service.reconcile()

        // 每个 repository 的全部写方法必须从未被调用（I-1：不得写入 expert_contact / mail_record / mail_attachment / bounce_record / operator_action_log）
        expertContactRepository.verifyNoWrites()
        mailRecordRepository.verifyNoWrites()
        mailAttachmentRepository.verifyNoWrites()
        bounceRecordRepository.verifyNoWrites()
        operatorActionLogRepository.verifyNoWrites()
        // ExpertContactRepository 的 @Modifying 更新（写 expert_contact 表）
        Mockito.verify(expertContactRepository, Mockito.never()).updateCountryById(Mockito.anyLong(), anyValue(""))
        Mockito.verify(expertContactRepository, Mockito.never()).updateBindingById(Mockito.anyLong(), anyValue(""), anyValue(java.time.LocalDateTime.now()))
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(Mockito.anyLong(), anyValue("account"), anyValue(java.time.LocalDateTime.now()))
        Mockito.verify(expertContactRepository, Mockito.never()).migrateBindingByAccount(anyValue("from"), anyValue("to"), anyValue(java.time.LocalDateTime.now()))
        Mockito.verify(expertContactRepository, Mockito.never()).clearSenderChangeMarkById(Mockito.anyLong())
        // 先验只读调用（verifyNoMoreInteractions 要求先覆盖全部真实交互）
        Mockito.verify(expertContactRepository).findAll()
        Mockito.verify(mailRecordRepository).findAll()
        Mockito.verify(mailAttachmentRepository).findAll()
        Mockito.verify(bounceRecordRepository).findAll()
        Mockito.verify(operatorActionLogRepository)
            .findContactIdsWithChangeOperatorStatusLogs(Mockito.anyList<Long>() ?: emptyList())
        // 总闭包：除已 verify 的只读调用外不得有任何交互（未列出的写方法也会在这里暴露）
        Mockito.verifyNoMoreInteractions(
            expertContactRepository, mailRecordRepository, mailAttachmentRepository,
            bounceRecordRepository, operatorActionLogRepository
        )
        // ES 侧只读：全部请求必须命中 _search，不得出现 _update/_bulk/_doc 等写端点
        val urlCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            urlCaptor.capture(),
            Mockito.eq(HttpMethod.POST),
            Mockito.any(HttpEntity::class.java),
            Mockito.eq(JsonNode::class.java)
        )
        assertTrue(urlCaptor.allValues.isNotEmpty()) { "对账必须查询 ES" }
        assertTrue(
            urlCaptor.allValues.all { it.endsWith("/_search") },
            "对账只能读 ES（_search），实际请求 URL: ${urlCaptor.allValues}"
        )
    }

    /** CrudRepository 全部写方法零交互断言（I-1 辅助；仓库主键均为 Long）。 */
    private inline fun <reified T : Any> org.springframework.data.repository.CrudRepository<T, Long>.verifyNoWrites() {
        Mockito.verify(this, Mockito.never()).save(Mockito.any(T::class.java))
        Mockito.verify(this, Mockito.never()).saveAll(Mockito.anyCollection())
        Mockito.verify(this, Mockito.never()).saveAll(Mockito.anyIterable())
        Mockito.verify(this, Mockito.never()).deleteById(Mockito.any(Long::class.java))
        Mockito.verify(this, Mockito.never()).delete(Mockito.any(T::class.java))
        Mockito.verify(this, Mockito.never()).deleteAll(Mockito.anyIterable())
        Mockito.verify(this, Mockito.never()).deleteAll()
        Mockito.verify(this, Mockito.never()).deleteAllById(Mockito.anyIterable())
    }
}
