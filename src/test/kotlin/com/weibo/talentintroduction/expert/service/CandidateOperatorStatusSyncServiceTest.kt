package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class CandidateOperatorStatusSyncServiceTest {
    private val expertIndexService = Mockito.mock(ExpertIndexService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val service = CandidateOperatorStatusSyncService(
        expertIndexService,
        expertContactRepository,
        expertIndexWriterService
    )

    @Test
    fun `reconcileAll throws when mapping check fails`() {
        Mockito.`when`(expertIndexService.checkCandidateOperatorStatusMapping()).thenReturn(false)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.reconcileAll()
        }
        assertTrue(ex.message!!.contains("CANDIDATE 索引缺少 keyword"))
        Mockito.verify(expertIndexWriterService, Mockito.never())
            .syncCandidateOperatorStatusBatch(Mockito.anyList())
    }

    @Test
    fun `reconcileAll uses latest contact per orcid`() {
        Mockito.`when`(expertIndexService.checkCandidateOperatorStatusMapping()).thenReturn(true)
        val contact1 = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L, orcidId = "orcid-1", operatorStatus = "CONTACTED",
            campaignId = 1L, expertEmail = "test1@example.com", expertName = "Test 1"
        )
        val contact2 = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 2L, orcidId = "orcid-1", operatorStatus = "REPLIED",
            campaignId = 1L, expertEmail = "test2@example.com", expertName = "Test 2"
        )
        Mockito.`when`(expertContactRepository.findAllByOrderByUpdatedAtDesc())
            .thenReturn(listOf(contact2, contact1))
        Mockito.`when`(expertIndexWriterService.syncCandidateOperatorStatusBatch(listOf("orcid-1" to "REPLIED")))
            .thenReturn(BulkSyncResult(total = 1, success = 1))

        val result = service.reconcileAll()

        assertEquals(1, result.total)
        assertEquals(1, result.success)
    }
}
