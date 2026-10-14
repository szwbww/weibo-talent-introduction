package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.service.BulkAutoReplyResult
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import com.weibo.talentintroduction.campaign.service.ExpertMaterialItem
import com.weibo.talentintroduction.campaign.service.ExpertMaterialRequestItem
import com.weibo.talentintroduction.campaign.service.ExpertMaterialService
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonQueryService
import com.weibo.talentintroduction.discovery.service.VerifiedPersonRelation
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime
import java.util.Optional

class ExpertContactManagementControllerTest {
    private val service = Mockito.mock(ExpertContactManagementService::class.java)
    private val expertMaterialService = Mockito.mock(ExpertMaterialService::class.java)
    private val personQueryService = Mockito.mock(DiscoveryPersonQueryService::class.java)
    private val controller = ExpertContactManagementController(
        service = service,
        manualExpertMailService = Mockito.mock(com.weibo.talentintroduction.mail.service.ManualExpertMailService::class.java),
        meetingScheduleService = Mockito.mock(com.weibo.talentintroduction.campaign.service.MeetingScheduleService::class.java),
        expertOperatorStatusService = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService::class.java),
        expertIndexLevelOperationService = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService::class.java),
        senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java),
        expertMaterialService = expertMaterialService,
        personQueryService = personQueryService
    )
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    // ── 06：可选 groupDiscoveredPersons 分支（默认契约不变）────────────────────────

    @Test
    fun `listContacts default branch keeps the legacy flat contract`() {
        val contacts = listOf(
            sampleContact().copy(id = 1L, orcidId = "d1", expertEmail = "a@x.com"),
            sampleContact().copy(id = 2L, orcidId = "d2", expertEmail = "b@x.com")
        )
        Mockito.`when`(
            service.listContacts(null, null, null, null, null, null)
        ).thenReturn(contacts)

        val response = controller.listContacts(null, null, null, null, null, null)
        assertFalse(response.groupDiscoveredPersons)
        assertTrue(response.groupedPersons.isEmpty())
        assertEquals(2L, response.totalCount)
        assertEquals(2, response.contacts.size)
    }

    @Test
    fun `groupDiscoveredPersons aggregates members into one person with all contact ids`() {
        val contacts = listOf(
            sampleContact().copy(id = 1L, orcidId = "d1", expertEmail = "a@x.com"),
            sampleContact().copy(id = 2L, orcidId = "d2", expertEmail = "b@x.com"),
            sampleContact().copy(id = 3L, orcidId = "d9", expertEmail = "other@x.com")
        )
        Mockito.`when`(
            service.listContacts(null, null, null, null, null, null)
        ).thenReturn(contacts)
        Mockito.`when`(personQueryService.resolveVerifiedPersonRelations(listOf("d1", "d2", "d9")))
            .thenReturn(
                mapOf(
                    "d1" to VerifiedPersonRelation("P1", false),
                    "d2" to VerifiedPersonRelation("P1", false)
                )
            )

        val response = controller.listContacts(
            null, null, null, null, null, null, groupDiscoveredPersons = true
        )

        assertTrue(response.groupDiscoveredPersons)
        // 两人：P1（d1+d2）与范围外 d9（保持原 contactId 单位）。
        assertEquals(2L, response.totalCount)
        val p1 = response.groupedPersons.first { it.personId == "P1" }
        assertEquals(listOf(1L, 2L), p1.contactIds)
        assertEquals(2, p1.memberCount)
        assertFalse(p1.needsReview)
        assertTrue(response.groupedPersons.any { it.personId == null && it.personKey == "contact:3" })
    }

    @Test
    fun `ordinary contacts sharing one orcidId stay two contact units and are never merged`() {
        val contacts = listOf(
            sampleContact().copy(id = 1L, orcidId = "d9", expertEmail = "a@x.com"),
            sampleContact().copy(id = 2L, orcidId = "d9", expertEmail = "b@x.com")
        )
        Mockito.`when`(
            service.listContacts(null, null, null, null, null, null)
        ).thenReturn(contacts)
        // 无有效关系：绝不按 orcidId 合并两个不同 contactId 的普通联系人。
        Mockito.`when`(personQueryService.resolveVerifiedPersonRelations(listOf("d9")))
            .thenReturn(emptyMap())

        val response = controller.listContacts(
            null, null, null, null, null, null, groupDiscoveredPersons = true
        )

        assertEquals(2L, response.totalCount)
        assertEquals(2, response.groupedPersons.size)
        assertEquals(listOf(listOf(1L), listOf(2L)), response.groupedPersons.map { it.contactIds })
        assertTrue(response.groupedPersons.all { it.personId == null })
        assertTrue(response.groupedPersons.all { !it.needsReview })
    }

    @Test
    fun `a member whose relation no longer verifies falls back to its contact unit and is marked for review`() {
        val contacts = listOf(
            sampleContact().copy(id = 1L, orcidId = "d1", expertEmail = "a@x.com"),
            sampleContact().copy(id = 2L, orcidId = "d6", expertEmail = "b@x.com")
        )
        Mockito.`when`(
            service.listContacts(null, null, null, null, null, null)
        ).thenReturn(contacts)
        Mockito.`when`(personQueryService.resolveVerifiedPersonRelations(listOf("d1", "d6")))
            .thenReturn(
                mapOf(
                    "d1" to VerifiedPersonRelation("P1", false),
                    "d6" to VerifiedPersonRelation(null, true)
                )
            )

        val response = controller.listContacts(
            null, null, null, null, null, null, groupDiscoveredPersons = true
        )

        assertEquals(2L, response.totalCount)
        val verified = response.groupedPersons.first { it.personId == "P1" }
        assertEquals(listOf(1L), verified.contactIds)
        val review = response.groupedPersons.first { it.needsReview }
        assertNull(review.personId)
        assertEquals(listOf(2L), review.contactIds)
        assertEquals("contact:2", review.personKey)
    }

    @Test
    fun `groupDiscoveredPersons filters by member email domain before aggregating`() {
        val contacts = listOf(
            sampleContact().copy(id = 1L, orcidId = "d1", expertEmail = "a@gmail.com"),
            sampleContact().copy(id = 2L, orcidId = "d2", expertEmail = "b@outlook.com")
        )
        Mockito.`when`(
            service.listContacts(null, null, null, null, null, null)
        ).thenReturn(contacts)
        // 仅 gmail 成员进入聚合：非代表邮箱也能按域名命中。
        Mockito.`when`(personQueryService.resolveVerifiedPersonRelations(listOf("d1")))
            .thenReturn(mapOf("d1" to VerifiedPersonRelation("P1", false)))

        val response = controller.listContacts(
            null, null, null, null, null, null, groupDiscoveredPersons = true, emailDomain = "gmail.com"
        )

        assertEquals(1L, response.totalCount)
        assertEquals(1, response.contacts.size)
        assertEquals("d1", response.contacts.first().orcidId)
    }

    @Test
    fun `listMaterials delegates contactId to service and returns catalog items`() {
        val items = listOf(
            ExpertMaterialItem(code = "CV", label = "简历", status = "PENDING"),
            ExpertMaterialItem(code = "PASSPORT", label = "护照", status = "PENDING"),
            ExpertMaterialItem(code = "DEGREE", label = "学位", status = "PENDING"),
            ExpertMaterialItem(code = "EMPLOYMENT", label = "工作", status = "PENDING"),
            ExpertMaterialItem(code = "PUBLICATIONS", label = "出版", status = "PENDING"),
            ExpertMaterialItem(code = "PATENTS", label = "专利", status = "PENDING"),
            ExpertMaterialItem(code = "RESEARCH", label = "研究", status = "PENDING")
        )
        Mockito.`when`(expertMaterialService.listMaterials(1L)).thenReturn(items)

        val result = controller.listMaterials(1L)

        assertEquals(items, result)
        assertEquals(7, result.size)
        Mockito.verify(expertMaterialService).listMaterials(1L)
    }

    @Test
    fun `updateMaterialStatus delegates contactId code and status and returns full list`() {
        val items = listOf(
            ExpertMaterialItem(code = "CV", label = "简历", status = "PROVIDED")
        )
        Mockito.`when`(expertMaterialService.updateStatus(1L, "CV", "PROVIDED")).thenReturn(items)

        val result = controller.updateMaterialStatus(
            1L,
            "CV",
            UpdateExpertMaterialStatusRequest(status = "PROVIDED")
        )

        assertEquals(items, result)
        Mockito.verify(expertMaterialService).updateStatus(1L, "CV", "PROVIDED")
    }

    @Test
    fun `bulk auto reply rejects missing operator name`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = null))
        }
        assertEquals("operatorName is required", ex.message)
    }

    @Test
    fun `bulk auto reply rejects blank operator name`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = "   "))
        }
        assertEquals("operatorName is required", ex.message)
    }

    @Test
    fun `bulk auto reply trims operator name`() {
        Mockito.`when`(service.bulkUpdateAutoReply(true, "admin"))
            .thenReturn(BulkAutoReplyResult(globalEnabled = true))

        val result = controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = " admin "))

        assertEquals(true, result.globalEnabled)
        Mockito.verify(service).bulkUpdateAutoReply(true, "admin")
    }

    @Test
    fun `markFollowUp delegates to service`() {
        val contact = sampleContact().copy(followUpMarked = true, followUpMarkedAt = LocalDateTime.of(2026, 6, 30, 10, 0))
        Mockito.`when`(service.markFollowUp(1L)).thenReturn(contact)

        val response = controller.markFollowUp(1L)

        assertTrue(response.followUpMarked)
        assertEquals("2026-06-30T10:00", response.followUpMarkedAt)
        Mockito.verify(service).markFollowUp(1L)
    }

    @Test
    fun `unmarkFollowUp delegates to service`() {
        val contact = sampleContact()
        Mockito.`when`(service.unmarkFollowUp(1L)).thenReturn(contact)

        val response = controller.unmarkFollowUp(1L)

        assertFalse(response.followUpMarked)
        assertEquals(null, response.followUpMarkedAt)
        Mockito.verify(service).unmarkFollowUp(1L)
    }

    // ── 02 材料索取固定 5 项路由（I-4：真实 HTTP 映射与 JSON 契约）──

    @Test
    fun `GET material-requests route maps to the five-item status array`() {
        Mockito.`when`(expertMaterialService.listMaterialRequests(1L)).thenReturn(requestItems())

        mockMvc.perform(get("/api/expert-contacts/1/material-requests"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(allPendingRequestBody(), true))

        Mockito.verify(expertMaterialService).listMaterialRequests(1L)
    }

    @Test
    fun `PUT material-requests route maps the status body and returns the updated array`() {
        Mockito.`when`(expertMaterialService.updateMaterialRequestStatus(1L, "REQ_AWARDS", "DECLINED"))
            .thenReturn(requestItems(mapOf("REQ_AWARDS" to "DECLINED")))

        mockMvc.perform(
            put("/api/expert-contacts/1/material-requests/REQ_AWARDS")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"DECLINED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(content().json(expectedRequestBody(awardsStatus = "DECLINED"), true))

        Mockito.verify(expertMaterialService).updateMaterialRequestStatus(1L, "REQ_AWARDS", "DECLINED")
    }

    @Test
    fun `GET materials stays unrouted here so the document paging endpoint is not shadowed`() {
        mockMvc.perform(get("/api/expert-contacts/1/materials"))
            .andExpect(status().isNotFound)
    }

    private fun requestItems(overrides: Map<String, String> = emptyMap()): List<ExpertMaterialRequestItem> =
        listOf(
            Triple("REQ_PUBLICATIONS", "代表性论文", "Copies of your representative publications"),
            Triple("REQ_PROJECTS", "科研项目", "Supporting documents for research projects"),
            Triple("REQ_PATENTS", "专利", "Patent certificates"),
            Triple("REQ_AWARDS", "荣誉奖项", "Certificates of honors and awards"),
            Triple("REQ_DEGREES", "学位", "Bachelor’s, master’s, and doctoral degree certificates")
        ).map { (code, label, requestText) ->
            ExpertMaterialRequestItem(
                code = code,
                label = label,
                status = overrides[code] ?: "PENDING",
                requestText = requestText
            )
        }

    private fun allPendingRequestBody(): String = expectedRequestBody()

    private fun expectedRequestBody(awardsStatus: String = "PENDING"): String = """
        [
          {"code":"REQ_PUBLICATIONS","label":"代表性论文","status":"PENDING","requestText":"Copies of your representative publications"},
          {"code":"REQ_PROJECTS","label":"科研项目","status":"PENDING","requestText":"Supporting documents for research projects"},
          {"code":"REQ_PATENTS","label":"专利","status":"PENDING","requestText":"Patent certificates"},
          {"code":"REQ_AWARDS","label":"荣誉奖项","status":"$awardsStatus","requestText":"Certificates of honors and awards"},
          {"code":"REQ_DEGREES","label":"学位","status":"PENDING","requestText":"Bachelor’s, master’s, and doctoral degree certificates"}
        ]
    """.trimIndent()

    private fun sampleContact(): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = "WAITING_REPLY"
        )
}
