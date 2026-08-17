package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertReachability
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.EmailSuppressionRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

class ExpertReachabilitySyncServiceTest {
    private val expertIndexService = Mockito.mock(ExpertIndexService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val classifier = Mockito.mock(ExpertReachabilityClassifier::class.java)
    private val emailSuppressionRepository = Mockito.mock(EmailSuppressionRepository::class.java)
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic",
        password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val service = ExpertReachabilitySyncService(
        expertIndexService, expertSearchService, expertIndexWriterService, classifier,
        emailSuppressionRepository, bounceRecordRepository, expertContactRepository,
        progressStore, restTemplate, properties
    )
    private val mapper = ObjectMapper()

    private fun profile(orcidId: String, email: String? = null, emailSource: String? = null) =
        ExpertProfile(
            orcidId = orcidId,
            email = email,
            givenNames = null,
            familyNames = null,
            country = null,
            keyword = null,
            employment = null,
            emailSource = emailSource
        )

    private fun contact(id: Long, orcidId: String) =
        ExpertContact(id = id, campaignId = 1L, orcidId = orcidId, expertEmail = "a@x.com", expertName = "A")

    private fun hardBounce(contactId: Long?) =
        BounceRecord(
            senderAccountCode = "acc",
            bounceMessageId = "msg-$contactId",
            originalMessageId = null,
            originalExpertContactId = contactId,
            bounceType = "HARD",
            dsnStatus = null,
            bounceReason = null,
            receivedAt = LocalDateTime.now()
        )

    /** Mockito matcher + 非空兜底值（照代码库先例：Kotlin 非空参数下 any() 返回 null 会触发空检查）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T =
        captor.capture() ?: defaultValue

    /** 模拟 scrollExperts：按真实语义逐批回调，handler 返回 false 即停止。 */
    private fun stubScroll(vararg batches: List<ExpertProfile>) {
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<Any>(2) as (List<ExpertProfile>, Int, Long) -> Boolean
            val totalHits = batches.sumOf { it.size }.toLong()
            var cancelled = false
            batches.forEachIndexed { index, batch ->
                if (cancelled) return@forEachIndexed
                if (!handler(batch, index + 1, totalHits)) cancelled = true
            }
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            eqValue(500),
            anyValue<(List<ExpertProfile>, Int, Long) -> Boolean>({ _, _, _ -> true })
        )
    }

    private fun stubEmptyDataSources() {
        Mockito.`when`(emailSuppressionRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(bounceRecordRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(emptyList())
    }

    // --- I-3-6: mapping 断言前置且 fail-fast ---

    @Test
    fun `syncAll throws when mapping check fails and writes nothing`() {
        Mockito.`when`(expertIndexService.checkReachabilityMapping()).thenReturn(false)

        val ex = assertThrows(IllegalStateException::class.java) { service.syncAll() }
        assertTrue(ex.message!!.contains("reachability mapping 声明"))
        Mockito.verify(expertIndexWriterService, Mockito.never()).syncReachabilityBatch(Mockito.anyList())
    }

    // --- I-3-3 + T3: scroll 驱动、分批聚合、progress 上报 ---

    @Test
    fun `syncAll aggregates results across scroll batches and reports progress`() {
        Mockito.`when`(expertIndexService.checkReachabilityMapping()).thenReturn(true)
        stubEmptyDataSources()
        Mockito.`when`(progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")).thenReturn(false)
        stubScroll(
            listOf(profile("0000-0001", email = "a@mit.edu", emailSource = "PAPER_FULLTEXT")),
            listOf(profile("0000-0002", email = "b@gmail.com", emailSource = "PAPER_FULLTEXT"))
        )
        Mockito.`when`(
            classifier.classify(
                anyValue(profile("0000-0001")),
                anyValue(emptySet<String>()),
                anyValue(emptySet<String>())
            )
        ).thenReturn(ExpertReachability.HIGH)
        Mockito.`when`(expertIndexWriterService.syncReachabilityBatch(Mockito.anyList()))
            .thenReturn(BulkSyncResult(total = 1, success = 1))

        val result = service.syncAll()

        assertEquals(2, result.total)
        assertEquals(2, result.success)
        Mockito.verify(expertIndexWriterService, Mockito.times(2)).syncReachabilityBatch(Mockito.anyList())
        Mockito.verify(progressStore, Mockito.times(2)).update(
            eqValue("EXPERT_REACHABILITY_SYNC"),
            anyValue(TaskProgress("", "", 0, 0, 0)),
            Mockito.isNull()
        )
    }

    @Test
    fun `syncAll stops scrolling when cancellation is requested`() {
        Mockito.`when`(expertIndexService.checkReachabilityMapping()).thenReturn(true)
        stubEmptyDataSources()
        Mockito.`when`(progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")).thenReturn(true)
        stubScroll(
            listOf(profile("0000-0001")),
            listOf(profile("0000-0002"))
        )
        Mockito.`when`(expertIndexWriterService.syncReachabilityBatch(Mockito.anyList()))
            .thenReturn(BulkSyncResult())

        service.syncAll()

        Mockito.verify(expertIndexWriterService, Mockito.times(1)).syncReachabilityBatch(Mockito.anyList())
    }

    // --- I-3-1: null value 走 remove 分支（service 层验证 map 而非 mapNotNull） ---

    @Test
    fun `syncAll passes null classification through as remove value`() {
        Mockito.`when`(expertIndexService.checkReachabilityMapping()).thenReturn(true)
        stubEmptyDataSources()
        Mockito.`when`(progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")).thenReturn(false)
        stubScroll(listOf(profile("0000-0001", email = "a@mit.edu", emailSource = null)))
        Mockito.`when`(
            classifier.classify(
                anyValue(profile("0000-0001")),
                anyValue(emptySet<String>()),
                anyValue(emptySet<String>())
            )
        ).thenReturn(null)
        Mockito.`when`(expertIndexWriterService.syncReachabilityBatch(Mockito.anyList()))
            .thenReturn(BulkSyncResult())

        service.syncAll()

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Pair<String, ExpertReachability?>>>
        Mockito.verify(expertIndexWriterService).syncReachabilityBatch(captureValue(captor, emptyList()))
        val updates = captor.value
        assertEquals(1, updates.size)
        assertEquals("0000-0001", updates[0].first)
        assertNull(updates[0].second)
    }

    // --- I-1: 硬退集合装配（HARD 且可溯源 → contactId → orcidId） ---

    @Test
    fun `syncAll passes hard-bounced orcids to classifier`() {
        Mockito.`when`(expertIndexService.checkReachabilityMapping()).thenReturn(true)
        Mockito.`when`(emailSuppressionRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(bounceRecordRepository.findAll()).thenReturn(
            listOf(
                hardBounce(1L),
                BounceRecord(
                    senderAccountCode = "acc",
                    bounceMessageId = "msg-2",
                    originalMessageId = null,
                    originalExpertContactId = 2L,
                    bounceType = "SOFT",
                    dsnStatus = null,
                    bounceReason = null,
                    receivedAt = LocalDateTime.now()
                ),
                hardBounce(null)
            )
        )
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(
            listOf(contact(1L, "0000-0001"), contact(2L, "0000-0002"))
        )
        Mockito.`when`(progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")).thenReturn(false)
        stubScroll(listOf(profile("0000-0001", email = "a@mit.edu", emailSource = "PAPER_FULLTEXT")))
        Mockito.`when`(expertIndexWriterService.syncReachabilityBatch(Mockito.anyList()))
            .thenReturn(BulkSyncResult())

        service.syncAll()

        val captor = ArgumentCaptor.forClass(Set::class.java) as ArgumentCaptor<Set<String>>
        Mockito.verify(classifier).classify(
            anyValue(profile("0000-0001")), anyValue(emptySet<String>()), captureValue(captor, emptySet())
        )
        val hardBounced = captor.value
        assertTrue(hardBounced.contains("0000-0001"))
        assertFalse(hardBounced.contains("0000-0002"))
    }

    // --- T5: 两个增量方法 ---

    @Test
    fun `markBlockedByEmail resolves orcid and writes BLOCKED_UNSUBSCRIBED`() {
        val searchHits = mapper.readTree(
            """
            {"hits":{"hits":[{"_id":"doc-1","_source":{"orcidId":"0000-0001"}}]}}
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(searchHits, HttpStatus.OK))

        service.markBlockedByEmail("a@x.com")

        Mockito.verify(expertIndexWriterService).syncReachabilityBatch(
            listOf("0000-0001" to ExpertReachability.BLOCKED_UNSUBSCRIBED)
        )
    }

    @Test
    fun `markBlockedByEmail swallows email lookup failure and writes nothing`() {
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES unavailable"))

        // fail-open：查询失败不抛异常，不写任何文档（调用方还有外层 try/catch，I-3-5）。
        service.markBlockedByEmail("a@x.com")

        Mockito.verify(expertIndexWriterService, Mockito.never()).syncReachabilityBatch(Mockito.anyList())
    }

    @Test
    fun `markBlockedByContact writes BLOCKED_BOUNCED for contact orcid`() {
        val contact = contact(1L, "0000-0001")

        service.markBlockedByContact(contact)

        Mockito.verify(expertIndexWriterService).syncReachabilityBatch(
            listOf("0000-0001" to ExpertReachability.BLOCKED_BOUNCED)
        )
    }

    // --- T1 writer 级：I-3-1 / I-3-2 / IP-5（bulk body 断言） ---

    @Test
    fun `syncReachabilityBatch builds remove script for null and doc for non-null on two layers`() {
        val realIndexService = ExpertIndexService(properties, restTemplate, mapper)
        val writer = ExpertIndexWriterService(
            restTemplate, properties, realIndexService, mapper,
            Mockito.mock(ExpertApplicationPromotionRepository::class.java),
            Mockito.mock(ExpertContactRepository::class.java)
        )
        val searchHits = mapper.readTree(
            """
            {"hits":{"hits":[
              {"_id":"doc-0001","_source":{"orcidId":"0000-0001"}},
              {"_id":"doc-0002","_source":{"orcidId":"0000-0002"}}
            ]}}
            """.trimIndent()
        )
        val bulkResponse = mapper.readTree(
            """
            {"items":[
              {"update":{"status":200,"_id":"doc-0001"}},
              {"update":{"status":200,"_id":"doc-0002"}}
            ]}
            """.trimIndent()
        )
        val urls = mutableListOf<String>()
        val bulkBodies = mutableListOf<String>()
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            urls.add(url)
            if (url.endsWith("/_search")) {
                ResponseEntity(searchHits, HttpStatus.OK)
            } else {
                val entity = invocation.getArgument<HttpEntity<*>>(2)
                bulkBodies.add(entity.body as String)
                ResponseEntity(bulkResponse, HttpStatus.OK)
            }
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(JsonNode::class.java)
        )

        val result = writer.syncReachabilityBatch(
            listOf("0000-0001" to null, "0000-0002" to ExpertReachability.HIGH)
        )

        // 2 orcid × CANDIDATE + APPLICATION 两层 = 4 条成功
        assertEquals(4, result.total)
        assertEquals(4, result.success)
        assertEquals(0, result.failure)
        assertEquals(2, bulkBodies.size)
        // I-3-2: 层级只含 CANDIDATE + APPLICATION，绝不触碰 RAW（orcid_info）
        assertTrue(urls.any { it.endsWith("/orcid_info_candidate/_search") })
        assertTrue(urls.any { it.endsWith("/orcid_info_application/_search") })
        assertTrue(urls.none { it.endsWith("/orcid_info/_search") })
        // I-3-1: null value → remove 脚本；非 null → esValue；绝不写 "UNKNOWN" 字符串
        assertTrue(bulkBodies.all { it.contains("ctx._source.remove('reachability')") })
        assertTrue(bulkBodies.all { it.contains("\"reachability\":\"HIGH\"") })
        assertTrue(bulkBodies.none { it.contains("UNKNOWN") })
        // IP-5: 两个分支均不写 updatedAt（不刷平「按更新时间排序」）
        assertTrue(bulkBodies.none { it.contains("updatedAt") })
    }

    @Test
    fun `syncReachabilityBatch counts unmapped orcid as skipped`() {
        val realIndexService = ExpertIndexService(properties, restTemplate, mapper)
        val writer = ExpertIndexWriterService(
            restTemplate, properties, realIndexService, mapper,
            Mockito.mock(ExpertApplicationPromotionRepository::class.java),
            Mockito.mock(ExpertContactRepository::class.java)
        )
        val searchHits = mapper.readTree(
            """
            {"hits":{"hits":[
              {"_id":"doc-0001","_source":{"orcidId":"0000-0001"}}
            ]}}
            """.trimIndent()
        )
        val bulkResponse = mapper.readTree(
            """
            {"items":[{"update":{"status":200,"_id":"doc-0001"}}]}
            """.trimIndent()
        )
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            if (url.endsWith("/_search")) {
                ResponseEntity(searchHits, HttpStatus.OK)
            } else {
                ResponseEntity(bulkResponse, HttpStatus.OK)
            }
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(JsonNode::class.java)
        )

        val result = writer.syncReachabilityBatch(
            listOf("0000-0001" to ExpertReachability.HIGH, "0000-0009" to ExpertReachability.LOW)
        )

        // 每层：1 成功 + 1 skipped（0000-0009 无 _id）→ 两层合计
        assertEquals(4, result.total)
        assertEquals(2, result.success)
        assertEquals(2, result.skipped)
    }
}
