package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.CandidateOperatorStatusSyncService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.BulkSyncResult
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.EmailDomainCount
import com.weibo.talentintroduction.expert.service.RegionCount
import com.weibo.talentintroduction.expert.service.TagCount
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonDetail
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonMember
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonQueryService
import com.weibo.talentintroduction.discovery.service.PersonListEntry
import com.weibo.talentintroduction.discovery.service.PersonSearchResult
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskLaunchResponse
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class ExpertIndexControllerTest {
    private val searchService = Mockito.mock(ExpertSearchService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val writerService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val syncService = Mockito.mock(CandidateOperatorStatusSyncService::class.java)
    private val revalidationService = Mockito.mock(ExpertRevalidationService::class.java)
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val filterService = Mockito.mock(EligibilityFilterService::class.java)
    private val introductionMailComposer = Mockito.mock(IntroductionMailComposer::class.java)
    private val personQueryService = Mockito.mock(DiscoveryPersonQueryService::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val jdbc = Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate::class.java)
    private val servletRequest = org.springframework.mock.web.MockHttpServletRequest()
    private val controller = ExpertIndexController(
        searchService, contactRepository, writerService, syncService,
        revalidationService, taskExecutionService, progressStore, filterService, introductionMailComposer, jdbc,
        personQueryService = personQueryService
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -1L)
    private fun notStartedToken(): Pair<Boolean, Long> = Pair(false, -1L)

    @Test
    fun `promoteEligibleRaw succeeds`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(revalidationService.promoteEligibleRawExperts())
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw()
        assertNotNull(result)
        val body = result.body as TaskLaunchResponse<*>
        val scanResult = body.result as PromotionScanResult
        assertEquals(0, scanResult.stats.total)
        assertEquals(1L, body.executionId)
    }

    @Test
    fun `revalidateCandidates returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.revalidateCandidates()
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `promoteEligibleRaw returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.promoteEligibleRaw()
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `revalidateCandidates writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.revalidateCandidates()
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `promoteEligibleRaw writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.promoteEligibleRaw()
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `backfillOperatorStatus returns 400 when mapping check fails`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(syncService.reconcileAll())
            .thenThrow(IllegalStateException("CANDIDATE 索引缺少 keyword 类型的 operatorStatus mapping 声明，请先更新 mapping"))

        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["message"].toString().contains("CANDIDATE 索引缺少 keyword"))
    }

    @Test
    fun `backfillOperatorStatus processes latest contact per orcid and returns ok`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(syncService.reconcileAll())
            .thenReturn(BulkSyncResult(total = 1, success = 1, failure = 0, skipped = 0))

        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.OK, response.statusCode)
        val body = response.body as BackfillResult
        assertEquals(1, body.total)
        assertEquals(1, body.success)
        assertEquals(0, body.failure)
        assertEquals(0, body.skipped)
    }

    @Test
    fun `listExperts prefers mysql operatorStatus over elasticsearch`() {
        val expert = ExpertProfile(
            orcidId = "orcid-1",
            email = "test@example.com",
            givenNames = "Expert",
            familyNames = "One",
            country = "US",
            keyword = null,
            employment = null,
            operatorStatus = null
        )
        Mockito.`when`(
            personQueryService.searchPersons(
                level = ExpertIndexLevel.CANDIDATE,
                size = 50,
                tag = null,
                sortBy = null,
                from = 0,
                operatorStatus = null,
                emailDomain = null,
                region = null
            )
        ).thenReturn(PersonSearchResult(listOf(PersonListEntry(expert, memberCount = 3)), 1L))
        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L,
            orcidId = "orcid-1",
            operatorStatus = "REPLIED",
            campaignId = 1L,
            expertEmail = "test@example.com",
            expertName = "Expert One",
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(contactRepository.findByOrcidIdIn(listOf("orcid-1")))
            .thenReturn(listOf(contact))

        val response = controller.listExperts(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = null,
            region = null
        )

        assertEquals(1, response.experts.size)
        assertEquals("REPLIED", response.experts[0].operatorStatus)
        assertEquals(3, response.experts[0].memberCount)
        assertEquals(1L, response.totalHits)
    }

    @Test
    fun `listExperts passes emailDomain parameter to the person query`() {
        Mockito.`when`(
            personQueryService.searchPersons(
                level = ExpertIndexLevel.CANDIDATE,
                size = 50,
                tag = null,
                sortBy = null,
                from = 0,
                operatorStatus = null,
                emailDomain = "gmail.com",
                region = null
            )
        ).thenReturn(PersonSearchResult(emptyList(), 0L))

        val response = controller.listExperts(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = "gmail.com",
            region = null
        )
        assertEquals(0L, response.totalHits)
        Mockito.verify(personQueryService).searchPersons(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = "gmail.com",
            region = null
        )
    }

    @Test
    fun `getEmailProviders aggregates email domains through the person query`() {
        val expectedAggs = listOf(EmailDomainCount("gmail.com", 100L))
        Mockito.`when`(personQueryService.aggregateEmailDomainsPersons(ExpertIndexLevel.CANDIDATE, null, null, null))
            .thenReturn(expectedAggs)

        val response = controller.getEmailProviders(ExpertIndexLevel.CANDIDATE)
        assertEquals(1, response.size)
        assertEquals("gmail.com", response[0].domain)
        assertEquals(100L, response[0].count)
    }

    @Test
    fun `getRegions delegates to the person query`() {
        val expectedAggs = listOf(RegionCount("Europe", 100L))
        Mockito.`when`(personQueryService.aggregateRegionsPersons(ExpertIndexLevel.CANDIDATE, null, null, null))
            .thenReturn(expectedAggs)

        val response = controller.getRegions(ExpertIndexLevel.CANDIDATE)
        assertEquals(1, response.size)
        assertEquals("Europe", response[0].region)
        assertEquals(100L, response[0].count)
        Mockito.verify(personQueryService).aggregateRegionsPersons(ExpertIndexLevel.CANDIDATE, null, null, null)
    }

    @Test
    fun `listExperts passes region parameter to the person query`() {
        Mockito.`when`(
            personQueryService.searchPersons(
                level = ExpertIndexLevel.CANDIDATE,
                size = 50,
                tag = null,
                sortBy = null,
                from = 0,
                operatorStatus = null,
                emailDomain = null,
                region = "Europe"
            )
        ).thenReturn(PersonSearchResult(emptyList(), 0L))

        val response = controller.listExperts(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = null,
            region = "Europe"
        )
        assertEquals(0L, response.totalHits)
        Mockito.verify(personQueryService).searchPersons(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = null,
            region = "Europe"
        )
    }

    @Test
    fun `addTag resolves esDocId and delegates to writerService`() {
        val profile = ExpertProfile(
            esDocId = "es-doc-1",
            orcidId = "orcid-1",
            email = "test@example.com",
            givenNames = "Expert",
            familyNames = "One",
            country = "US",
            keyword = null,
            employment = null
        )
        Mockito.`when`(searchService.findByOrcidId("orcid-1", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)
        Mockito.`when`(writerService.addTag("es-doc-1", "承诺回复材料", ExpertIndexLevel.CANDIDATE))
            .thenReturn(true)

        val result = controller.addTag(
            ExpertTagMutationRequest(
                orcidId = "orcid-1",
                tag = "承诺回复材料",
                level = ExpertIndexLevel.CANDIDATE
            ), servletRequest
        )

        assertTrue(result.success)
        Mockito.verify(writerService).addTag("es-doc-1", "承诺回复材料", ExpertIndexLevel.CANDIDATE)
    }

    @Test
    fun `removeTag falls back to orcidId when esDocId is null`() {
        val profile = ExpertProfile(
            esDocId = null,
            orcidId = "orcid-1",
            email = "test@example.com",
            givenNames = "Expert",
            familyNames = "One",
            country = "US",
            keyword = null,
            employment = null
        )
        Mockito.`when`(searchService.findByOrcidId("orcid-1", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)
        Mockito.`when`(writerService.removeTag("orcid-1", "承诺回复材料", ExpertIndexLevel.CANDIDATE))
            .thenReturn(true)

        val result = controller.removeTag(
            ExpertTagMutationRequest(
                orcidId = "orcid-1",
                tag = "承诺回复材料",
                level = ExpertIndexLevel.CANDIDATE
            ), servletRequest
        )

        assertTrue(result.success)
        Mockito.verify(writerService).removeTag("orcid-1", "承诺回复材料", ExpertIndexLevel.CANDIDATE)
    }

    @Test
    fun `aggregateTags delegates to the person query without tag dimension`() {
        val expected = listOf(TagCount("承诺回复材料", 5L))
        Mockito.`when`(
            personQueryService.aggregateTagsPersons(
                ExpertIndexLevel.CANDIDATE,
                "REPLIED",
                "gmail.com",
                "Europe"
            )
        ).thenReturn(expected)

        val response = controller.aggregateTags(
            level = ExpertIndexLevel.CANDIDATE,
            operatorStatus = "REPLIED",
            emailDomain = "gmail.com",
            region = "Europe"
        )

        assertEquals(1, response.size)
        assertEquals("承诺回复材料", response[0].tag)
        assertEquals(5L, response[0].count)
        Mockito.verify(personQueryService).aggregateTagsPersons(
            ExpertIndexLevel.CANDIDATE,
            "REPLIED",
            "gmail.com",
            "Europe"
        )
    }

    @Test
    fun `getPerson returns the read-only person detail`() {
        val detail = DiscoveryPersonDetail(
            personId = "P1",
            revision = 3,
            representativeDocId = "d1",
            requestedDocId = "d2",
            members = listOf(
                DiscoveryPersonMember(
                    docId = "d1", levels = listOf("RAW", "CANDIDATE"), email = "a@x.com",
                    contactId = 1L, contactStatus = "WAITING_REPLY", operatorStatus = "NOT_CONTACTED",
                    admissionStatus = "ADMITTED", representative = true, stale = false, evidenceSource = "PAPER_FULLTEXT"
                ),
                DiscoveryPersonMember(
                    docId = "d2", levels = listOf("CANDIDATE"), email = "b@x.com",
                    contactId = 2L, contactStatus = "WAITING_REPLY", operatorStatus = "NOT_CONTACTED",
                    admissionStatus = "MANUAL_REJECTED", representative = false, stale = false, evidenceSource = "PAPER_FULLTEXT"
                )
            ),
            conflicts = emptyList()
        )
        Mockito.`when`(personQueryService.personDetail("d2", ExpertIndexLevel.CANDIDATE)).thenReturn(detail)

        val response = controller.getPerson("d2", ExpertIndexLevel.CANDIDATE)
        assertEquals("P1", response.personId)
        assertEquals(2, response.members.size)
        assertEquals("d1", response.representativeDocId)
        // R-12：逐邮箱状态原样透出（两个宿主共用同一契约），控制器不做任何翻译。
        assertEquals("ADMITTED", response.members.first { it.docId == "d1" }.admissionStatus)
        assertEquals("MANUAL_REJECTED", response.members.first { it.docId == "d2" }.admissionStatus)
    }

    @Test
    fun `getPerson throws 404 when the docId is unknown`() {
        Mockito.`when`(personQueryService.personDetail("missing", ExpertIndexLevel.CANDIDATE)).thenReturn(null)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            controller.getPerson("missing", ExpertIndexLevel.CANDIDATE)
        }
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun `getExpertProfile returns tags from searchService findByOrcidId`() {
        val profile = ExpertProfile(
            esDocId = "es-doc-1",
            orcidId = "orcid-1",
            email = "test@example.com",
            givenNames = "Expert",
            familyNames = "One",
            country = "US",
            keyword = null,
            employment = null,
            tags = listOf("承诺回复材料")
        )
        Mockito.`when`(searchService.findByOrcidId("orcid-1", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val response = controller.getExpertProfile("orcid-1", ExpertIndexLevel.CANDIDATE, servletRequest)

        assertEquals("orcid-1", response.orcidId)
        assertEquals(true, response.found)
        assertEquals(listOf("承诺回复材料"), response.tags)
    }

    @Test
    fun `getExpertProfile returns found=false with empty tags when profile absent`() {
        Mockito.`when`(searchService.findByOrcidId("orcid-absent", ExpertIndexLevel.CANDIDATE))
            .thenReturn(null)

        val response = controller.getExpertProfile("orcid-absent", ExpertIndexLevel.CANDIDATE, servletRequest)

        assertEquals("orcid-absent", response.orcidId)
        assertEquals(false, response.found)
        assertTrue(response.tags.isEmpty())
    }

    @Test
    fun `getExpertProfile propagates search exception`() {
        Mockito.`when`(searchService.findByOrcidId("orcid-err", ExpertIndexLevel.CANDIDATE))
            .thenThrow(RuntimeException("es down"))

        assertThrows(RuntimeException::class.java) {
            controller.getExpertProfile("orcid-err", ExpertIndexLevel.CANDIDATE, servletRequest)
        }
    }
}
