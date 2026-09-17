package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertMaterialStatusRecord
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertMaterialStatusRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.rag.service.RagProcessContextResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.NoSuchElementException
import java.util.Optional

/**
 * 02 材料索取五项目录与三态状态（I-1/I-2/I-3/I-4）的服务层验证。
 *
 * 只覆盖新目录行为与它和旧 7 项目录 / `${pendingExpertMaterials}` / RAG `CV` 读取的隔离；
 * 旧目录自身的语义由既有 [com.weibo.talentintroduction.mail.service.MailVariableServiceTest] 覆盖。
 */
class ExpertMaterialRequestServiceTest {
    private val statusRepository = Mockito.mock(ExpertMaterialStatusRepository::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val service = ExpertMaterialService(statusRepository, contactRepository)

    private val contact = ExpertContact(
        id = 1L,
        campaignId = 10L,
        orcidId = "0000-0001",
        expertEmail = "expert@example.com",
        expertName = "Expert",
        currentStatus = "WAITING_REPLY"
    )

    private fun contactExists(contactId: Long = 1L) {
        Mockito.`when`(contactRepository.findById(contactId)).thenReturn(Optional.of(contact))
    }

    private fun statusRow(id: Long, code: String, status: String) = ExpertMaterialStatusRecord(
        id = id,
        expertContactId = 1L,
        materialCode = code,
        materialStatus = status,
        createdAt = LocalDateTime.of(2026, 9, 17, 10, 0),
        updatedAt = LocalDateTime.of(2026, 9, 17, 10, 0)
    )

    /** 服务端目录的五条英文正文（I-3 唯一真源，测试逐字钉住）。 */
    private val expectedRequestText = listOf(
        "Copies of your representative publications",
        "Supporting documents for research projects",
        "Patent certificates",
        "Certificates of honors and awards",
        "Bachelor’s, master’s, and doctoral degree certificates"
    )

    private val expectedCodes = listOf(
        "REQ_PUBLICATIONS",
        "REQ_PROJECTS",
        "REQ_PATENTS",
        "REQ_AWARDS",
        "REQ_DEGREES"
    )

    @Test
    fun `listMaterialRequests returns five codes in fixed order with server-side English text`() {
        contactExists()
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        val items = service.listMaterialRequests(1L)

        assertEquals(expectedCodes, items.map { it.code })
        assertEquals(listOf("代表性论文", "科研项目", "专利", "荣誉奖项", "学位"), items.map { it.label })
        assertEquals(listOf("PENDING", "PENDING", "PENDING", "PENDING", "PENDING"), items.map { it.status })
        assertEquals(expectedRequestText, items.map { it.requestText })
    }

    @Test
    fun `listMaterialRequests is read-only and never writes the status table`() {
        contactExists()
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        service.listMaterialRequests(1L)

        Mockito.verify(statusRepository, Mockito.never()).save(Mockito.any())
        Mockito.verify(statusRepository, Mockito.never()).deleteById(Mockito.anyLong())
        Mockito.verify(statusRepository, Mockito.never())
            .delete(Mockito.any(ExpertMaterialStatusRecord::class.java))
    }

    @Test
    fun `listMaterialRequests resolves stored REQ rows and treats missing rows as PENDING`() {
        contactExists()
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(statusRow(3, "REQ_PATENTS", "PROVIDED"), statusRow(4, "REQ_DEGREES", "DECLINED"))
        )

        val byCode = service.listMaterialRequests(1L).associateBy { it.code }

        assertEquals("PROVIDED", byCode["REQ_PATENTS"]!!.status)
        assertEquals("DECLINED", byCode["REQ_DEGREES"]!!.status)
        assertEquals("PENDING", byCode["REQ_PUBLICATIONS"]!!.status)
        assertEquals("Certificates of honors and awards", byCode["REQ_AWARDS"]!!.requestText)
    }

    @Test
    fun `updateMaterialRequestStatus PROVIDED inserts a new REQ row`() {
        contactExists()
        Mockito.`when`(statusRepository.findByExpertContactIdAndMaterialCode(1L, "REQ_AWARDS"))
            .thenReturn(null)
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(statusRow(9, "REQ_AWARDS", "PROVIDED"))
        )

        val items = service.updateMaterialRequestStatus(1L, "REQ_AWARDS", "PROVIDED")

        val captured = ArgumentCaptor.forClass(ExpertMaterialStatusRecord::class.java)
        Mockito.verify(statusRepository).save(captured.capture())
        assertEquals(null, captured.value.id)
        assertEquals("REQ_AWARDS", captured.value.materialCode)
        assertEquals("PROVIDED", captured.value.materialStatus)
        assertEquals("PROVIDED", items.single { it.code == "REQ_AWARDS" }.status)
        assertEquals(expectedCodes, items.map { it.code })
    }

    @Test
    fun `updateMaterialRequestStatus on an existing row keeps its id`() {
        val existing = statusRow(7, "REQ_PROJECTS", "PROVIDED")
        contactExists()
        Mockito.`when`(statusRepository.findByExpertContactIdAndMaterialCode(1L, "REQ_PROJECTS"))
            .thenReturn(existing)
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(existing.copy(materialStatus = "DECLINED"))
        )

        val items = service.updateMaterialRequestStatus(1L, "REQ_PROJECTS", "DECLINED")

        val captured = ArgumentCaptor.forClass(ExpertMaterialStatusRecord::class.java)
        Mockito.verify(statusRepository).save(captured.capture())
        assertEquals(7L, captured.value.id)
        assertEquals("DECLINED", captured.value.materialStatus)
        assertEquals("DECLINED", items.single { it.code == "REQ_PROJECTS" }.status)
    }

    @Test
    fun `updateMaterialRequestStatus PENDING deletes the stored row`() {
        contactExists()
        Mockito.`when`(statusRepository.findByExpertContactIdAndMaterialCode(1L, "REQ_DEGREES"))
            .thenReturn(statusRow(11, "REQ_DEGREES", "DECLINED"))
        Mockito.`when`(statusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        val items = service.updateMaterialRequestStatus(1L, "REQ_DEGREES", "PENDING")

        Mockito.verify(statusRepository).deleteById(11L)
        Mockito.verify(statusRepository, Mockito.never()).save(Mockito.any())
        assertTrue(items.all { it.status == "PENDING" })
    }

    @Test
    fun `updateMaterialRequestStatus rejects unknown code and status before any write`() {
        contactExists()

        assertThrows(IllegalArgumentException::class.java) {
            service.updateMaterialRequestStatus(1L, "REQ_UNKNOWN", "PROVIDED")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateMaterialRequestStatus(1L, "REQ_AWARDS", "DONE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateMaterialRequestStatus(1L, "req_awards", "PROVIDED")
        }

        Mockito.verifyNoInteractions(statusRepository)
    }

    @Test
    fun `legacy and request catalogues never accept each other's codes`() {
        contactExists()

        assertThrows(IllegalArgumentException::class.java) {
            service.updateMaterialRequestStatus(1L, "CV", "PROVIDED")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateStatus(1L, "REQ_AWARDS", "PROVIDED")
        }

        Mockito.verifyNoInteractions(statusRepository)
    }

    @Test
    fun `listMaterialRequests rejects a missing contact with NoSuchElementException`() {
        Mockito.`when`(contactRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows(NoSuchElementException::class.java) {
            service.listMaterialRequests(99L)
        }
        assertThrows(NoSuchElementException::class.java) {
            service.updateMaterialRequestStatus(99L, "REQ_AWARDS", "DECLINED")
        }
    }

    /**
     * I-1 跨模块隔离：同一联系人先写旧 `CV=PROVIDED`，再经新 5 项 PUT 写 `REQ_DEGREES`，
     * 旧 7 项目录、`${pendingExpertMaterials}` 组装与 RAG 的 `CV → RECEIVED` 判定都不受影响。
     * 用可写回的桩仓储让"写后读"是真实序列，而不是分别打桩的静态值。
     */
    @Test
    fun `new catalogue writes leave the legacy catalogue the pending materials variable and the RAG CV read unchanged`() {
        val rows = mutableListOf(statusRow(7, "CV", "PROVIDED"))
        val repository = Mockito.mock(ExpertMaterialStatusRepository::class.java)
        Mockito.`when`(repository.findAllByExpertContactId(1L)).thenAnswer { rows.toList() }
        Mockito.`when`(repository.findByExpertContactIdAndMaterialCode(Mockito.eq(1L), Mockito.anyString()))
            .thenAnswer { invocation ->
                val code = invocation.getArgument<String>(1)
                rows.firstOrNull { it.materialCode == code }
            }
        Mockito.`when`(repository.save(Mockito.any(ExpertMaterialStatusRecord::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.getArgument<ExpertMaterialStatusRecord>(0)
                rows.removeIf { it.materialCode == saved.materialCode }
                rows.add(saved)
                saved
            }
        val isolatedService = ExpertMaterialService(repository, contactRepository)
        contactExists()

        val requestItems = isolatedService.updateMaterialRequestStatus(1L, "REQ_DEGREES", "PROVIDED")

        assertEquals("PROVIDED", requestItems.single { it.code == "REQ_DEGREES" }.status)
        assertEquals(2, rows.size)

        val legacyItems = isolatedService.listMaterials(1L)
        assertEquals(7, legacyItems.size)
        assertEquals("PROVIDED", legacyItems.single { it.code == "CV" }.status)
        assertEquals("PENDING", legacyItems.single { it.code == "DEGREE" }.status)
        assertTrue(legacyItems.none { it.code.startsWith("REQ_") })

        val pendingMaterials = isolatedService.renderPendingMaterials(1L)
        assertTrue(pendingMaterials.startsWith("1. A copy of the personal information page of your valid passport."))
        assertFalse(pendingMaterials.contains("Copies of your representative publications"))
        assertEquals(6, pendingMaterials.lines().size)

        val ragResolver = RagProcessContextResolver(
            repository,
            Mockito.mock(MailRecordRepository::class.java).also {
                Mockito.`when`(it.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
            }
        )
        assertEquals(RagProcessContextResolver.CV_STATUS_RECEIVED, ragResolver.resolve(1L).cvStatus)
    }
}
