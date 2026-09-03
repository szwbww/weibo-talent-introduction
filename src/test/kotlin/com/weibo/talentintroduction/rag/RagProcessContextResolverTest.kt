package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.campaign.domain.ExpertMaterialStatusRecord
import com.weibo.talentintroduction.campaign.repository.ExpertMaterialStatusRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.rag.service.RagProcessContextResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * 计划 03 (T7): I-19 的固定映射 —— CV 状态 PROVIDED→RECEIVED / DECLINED→UNKNOWN /
 * 缺行→MISSING；expertReplyCount = 该联系人 mail_record 中 direction=INBOUND 的
 * 条数（应用层过滤）；expertTags 恒空。仓储全部 Mockito mock，无 DB。
 */
class RagProcessContextResolverTest {

    private val materialRepository = Mockito.mock(ExpertMaterialStatusRepository::class.java)
    private val mailRepository = Mockito.mock(MailRecordRepository::class.java)
    private val resolver = RagProcessContextResolver(materialRepository, mailRepository)

    private val contactId = 42L

    @Test
    fun `cv PROVIDED maps to RECEIVED`() {
        Mockito.`when`(materialRepository.findByExpertContactIdAndMaterialCode(contactId, "CV"))
            .thenReturn(statusRow("PROVIDED"))
        Mockito.`when`(mailRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(emptyList())

        val context = resolver.resolve(contactId)
        assertEquals("RECEIVED", context.cvStatus)
        assertEquals(0, context.expertReplyCount)
        assertEquals(emptyList<String>(), context.expertTags)
    }

    @Test
    fun `cv DECLINED maps to UNKNOWN`() {
        Mockito.`when`(materialRepository.findByExpertContactIdAndMaterialCode(contactId, "CV"))
            .thenReturn(statusRow("DECLINED"))
        Mockito.`when`(mailRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(emptyList())

        assertEquals("UNKNOWN", resolver.resolve(contactId).cvStatus)
    }

    @Test
    fun `missing cv row maps to MISSING`() {
        Mockito.`when`(materialRepository.findByExpertContactIdAndMaterialCode(contactId, "CV"))
            .thenReturn(null)
        Mockito.`when`(mailRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(emptyList())

        assertEquals("MISSING", resolver.resolve(contactId).cvStatus)
    }

    @Test
    fun `expert reply count counts only inbound mail records`() {
        Mockito.`when`(materialRepository.findByExpertContactIdAndMaterialCode(contactId, "CV"))
            .thenReturn(null)
        val records = listOf(
            mailRecord(direction = "INBOUND", id = 1L),
            mailRecord(direction = "INBOUND", id = 2L),
            mailRecord(direction = "INBOUND", id = 3L),
            mailRecord(direction = "OUTBOUND", id = 4L),
            mailRecord(direction = "OUTBOUND", id = 5L)
        )
        Mockito.`when`(mailRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(records)

        val context = resolver.resolve(contactId)
        assertEquals(3, context.expertReplyCount)
        assertEquals("MISSING", context.cvStatus)
    }

    private fun statusRow(status: String): ExpertMaterialStatusRecord =
        ExpertMaterialStatusRecord(
            expertContactId = contactId,
            materialCode = "CV",
            materialStatus = status
        )

    private fun mailRecord(direction: String, id: Long): MailRecord = MailRecord(
        id = id,
        expertContactId = contactId,
        direction = direction,
        mailType = "MANUAL",
        senderAccountCode = "acc-1",
        messageId = "msg-$id",
        inReplyTo = null,
        subject = "Subject $id",
        body = "Body $id",
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )
}
