package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExpertClassificationBackfillServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-15T10:30:00Z"), ZoneOffset.UTC)
    private val classifier = ExpertClassificationService(clock = fixedClock)
    private val indexWriter = Mockito.mock(ExpertIndexWriterService::class.java)
    private val searchService = Mockito.mock(ExpertSearchService::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val service = ExpertClassificationBackfillService(classifier, indexWriter, searchService, progressStore)

    private val executionId = 42L

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    @BeforeEach
    fun setUp() {
        Mockito.`when`(indexWriter.checkExpertClassificationMapping(anyValue(ExpertIndexLevel.CANDIDATE))).thenReturn(true)
        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId))).thenReturn(false)
    }

    private fun profile(
        docId: String?,
        employment: String? = null,
        researchFields: String? = null,
        institution: String? = null,
        lastPublicationYear: Int? = null,
        hIndex: Int? = null,
        worksCount: Int? = null,
        recentWorkTitles: List<String>? = null,
        patentTitles: List<String>? = null
    ) = ExpertProfile(
        esDocId = docId,
        orcidId = if (docId == null) "orcid-null" else "orcid-$docId",
        email = "expert@example.com",
        givenNames = null,
        familyNames = null,
        country = null,
        keyword = null,
        employment = employment,
        researchFields = researchFields,
        institution = institution,
        lastPublicationYear = lastPublicationYear,
        hIndex = hIndex,
        worksCount = worksCount,
        recentWorkTitles = recentWorkTitles,
        patentTitles = patentTitles
    )

    private fun production(docId: String?) =
        profile(docId, employment = "R&D Engineer, MedTech Ltd", researchFields = "medical device", patentTitles = listOf("Implantable sensor"))

    private fun academic(docId: String?) =
        profile(docId, researchFields = "drug development", lastPublicationYear = 2025, recentWorkTitles = listOf("Kinase inhibitors"), hIndex = 10, worksCount = 20, institution = "University Laboratory")

    private fun unknown(docId: String?) = profile(docId)

    private fun surgeon(docId: String?) =
        profile(docId, employment = "surgeon", lastPublicationYear = 2025, recentWorkTitles = listOf("Surgery advances"))

    private fun stubScan(
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE,
        batchSize: Int = 500,
        batches: List<List<ExpertProfile>>
    ) {
        Mockito.`when`(
            searchService.searchAfterExpertsFiltered(
                eqValue(level),
                anyList<Map<String, Any>>(),
                eqValue(batchSize),
                anyValue<(List<ExpertProfile>) -> Boolean>({ true })
            )
        ).thenAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            for (batch in batches) {
                if (!handler(batch)) break
            }
            Unit
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> listCaptor(): ArgumentCaptor<List<T>> =
        ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<T>>

    private fun dryRunRequest(
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE,
        batchSize: Int = 500,
        maxDocs: Long? = null,
        onlyPending: Boolean = true,
        delayMs: Int = 0
    ) = ExpertClassificationBackfillRequest(
        level = level,
        mode = BackfillMode.DRY_RUN,
        version = ExpertClassificationService.VERSION,
        batchSize = batchSize,
        delayMs = delayMs,
        maxDocs = maxDocs,
        onlyPending = onlyPending
    )

    private fun executeRequest(
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE,
        batchSize: Int = 500,
        maxDocs: Long? = null,
        delayMs: Int = 0
    ) = ExpertClassificationBackfillRequest(
        level = level,
        mode = BackfillMode.EXECUTE,
        version = ExpertClassificationService.VERSION,
        batchSize = batchSize,
        delayMs = delayMs,
        maxDocs = maxDocs,
        onlyPending = true,
        confirmation = "EXECUTE_${level.name}:${ExpertClassificationService.VERSION}"
    )

    private fun bulkResult(vararg results: Pair<String, ClassificationBulkItemStatus>, allMapper: Boolean = false, wholesale: String? = null): ClassificationBulkResult =
        ClassificationBulkResult(
            items = results.map { (docId, status) -> ClassificationBulkItemResult(docId, status) },
            failureSamples = results.filter { it.second == ClassificationBulkItemStatus.FAILED }.map { "docId=${it.first} error: x" },
            allFailedWithMapperError = allMapper,
            wholesaleError = wholesale
        )

    @Test
    fun `DRY_RUN scans multiple batches aggregates only and never writes (I2-1)`() {
        stubScan(batches = listOf(
            listOf(production("0001"), academic("0002")),
            listOf(unknown("0003"))
        ))
        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId)))
            .thenReturn(false, false, false)

        val result = service.run(dryRunRequest(), executionId)

        Mockito.verify(indexWriter, Mockito.never()).bulkUpdateExpertClassifications(anyValue(ExpertIndexLevel.CANDIDATE), anyList())
        assertEquals(3L, result.scanned)
        assertEquals(1L, result.classifiedByType[ExpertType.PRODUCTION_RND.name])
        assertEquals(1L, result.classifiedByType[ExpertType.ACADEMIC_RND.name])
        assertEquals(0L, result.classifiedByType[ExpertType.HYBRID_RND.name])
        assertEquals(0L, result.classifiedByType[ExpertType.SERVICE_ONLY.name])
        assertEquals(0L, result.classifiedByType[ExpertType.OUT_OF_SCOPE.name])
        assertEquals(1L, result.classifiedByType[ExpertType.UNKNOWN.name])
        assertEquals(2L, result.sendable)
        assertEquals(1L, result.notSendable)
        assertEquals(0L, result.writeSuccess)
        assertEquals(0L, result.writeNoop)
        assertEquals(0L, result.writeFailure)
        assertEquals(0L, result.skippedMissingDocId)
        assertEquals("SUCCESS", result.taskFinalStatus)
        assertEquals(3, result.taskSuccessCount)
        assertEquals(0, result.taskFailureCount)

        // I2-6 恒等式：六类之和 == scanned；sendable == 前三类之和
        assertEquals(result.scanned, result.classifiedByType.values.sum())
        assertEquals(
            result.sendable,
            result.classifiedByType[ExpertType.PRODUCTION_RND.name]!! +
                result.classifiedByType[ExpertType.ACADEMIC_RND.name]!! +
                result.classifiedByType[ExpertType.HYBRID_RND.name]!!
        )

        // 每批 + 终态进度都以真实 executionId 写
        val progressCaptor = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeast(3)).update(
            eqValue(ExpertClassificationBackfillService.TASK_TYPE), progressCaptor.capture() ?: TaskProgress("", "", 0, 0, 0), eqValue(executionId)
        )
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("SUCCESS", finalProgress.status)
        assertEquals(3L, finalProgress.processedCount)
        assertEquals(3L, finalProgress.details!!["scanned"])
    }

    @Test
    fun `EXECUTE writes only pending docs through writer and aggregates (I2-2)`() {
        stubScan(batches = listOf(listOf(production("0001"), academic("0002"))))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(
                eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>()
            )
        ).thenReturn(bulkResult("0001" to ClassificationBulkItemStatus.UPDATED, "0002" to ClassificationBulkItemStatus.NOOP))

        val result = service.run(executeRequest(), executionId)

        val captor = listCaptor<ClassificationBulkItem>()
        Mockito.verify(indexWriter).bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), captor.capture() ?: emptyList())
        val items = captor.value
        assertEquals(listOf("0001", "0002"), items.map { it.esDocId })
        assertEquals(listOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND), items.map { it.classification.type })
        assertEquals(1L, result.writeSuccess)
        assertEquals(1L, result.writeNoop)
        assertEquals(0L, result.writeFailure)
        assertEquals("SUCCESS", result.taskFinalStatus)
        assertEquals(1, result.taskSuccessCount)
        assertEquals(0, result.taskFailureCount)
    }

    @Test
    fun `missing esDocId is counted skipped and never written (I2-2)`() {
        stubScan(batches = listOf(listOf(production(null), academic("0002"))))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(bulkResult("0002" to ClassificationBulkItemStatus.UPDATED))

        val result = service.run(executeRequest(), executionId)

        assertEquals(2L, result.scanned)
        assertEquals(1L, result.skippedMissingDocId)
        val captor = listCaptor<ClassificationBulkItem>()
        Mockito.verify(indexWriter).bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), captor.capture() ?: emptyList())
        assertEquals(listOf("0002"), captor.value.map { it.esDocId })
    }

    @Test
    fun `cancel before second batch returns CANCELLED with scanned so far (I2-4)`() {
        stubScan(batches = listOf(
            listOf(production("0001"), academic("0002")),
            listOf(unknown("0003"))
        ))
        // 批次1 开始 false、delay(0) 复查 false、批次2 开始 true
        Mockito.`when`(progressStore.isCancelled(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(executionId)))
            .thenReturn(false, false, true)

        val result = service.run(dryRunRequest(), executionId)

        assertTrue(result.wasCancelled)
        assertEquals("CANCELLED", result.taskFinalStatus)
        assertEquals(2L, result.scanned)
        assertEquals(2L, result.sendable)
    }

    @Test
    fun `partial item failures return PARTIAL_SUCCESS with complete failure total (I2-4)`() {
        stubScan(batches = listOf(listOf(production("0001"), academic("0002"), unknown("0003"))))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(
            bulkResult(
                "0001" to ClassificationBulkItemStatus.UPDATED,
                "0002" to ClassificationBulkItemStatus.UPDATED,
                "0003" to ClassificationBulkItemStatus.FAILED
            )
        )

        val result = service.run(executeRequest(), executionId)

        assertEquals(2L, result.writeSuccess)
        assertEquals(1L, result.writeFailure)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(2, result.taskSuccessCount)
        assertEquals(1, result.taskFailureCount)
    }

    @Test
    fun `all items failed returns FAILED (I2-4)`() {
        stubScan(batches = listOf(listOf(production("0001"), academic("0002"))))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(bulkResult("0001" to ClassificationBulkItemStatus.FAILED, "0002" to ClassificationBulkItemStatus.FAILED))

        val result = service.run(executeRequest(), executionId)

        assertEquals(2L, result.writeFailure)
        assertEquals(0L, result.writeSuccess)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `onlyPending filter uses missing-or-other-version should with minimum_should_match 1 (I2-4)`() {
        stubScan(batches = listOf(listOf(production("0001"))))
        service.run(dryRunRequest(onlyPending = true), executionId)

        val captor = listCaptor<Map<String, Any>>()
        Mockito.verify(searchService).searchAfterExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), captor.capture() ?: emptyList(), eqValue(500), anyValue<(List<ExpertProfile>) -> Boolean>({ true })
        )
        val filters = captor.value
        assertEquals(1, filters.size)
        @Suppress("UNCHECKED_CAST")
        val bool = filters[0]["bool"] as Map<String, Any>
        assertEquals(1, bool["minimum_should_match"])
        @Suppress("UNCHECKED_CAST")
        val should = bool["should"] as List<Map<String, Any>>
        assertEquals(2, should.size)
        @Suppress("UNCHECKED_CAST")
        val missing = (should[0]["bool"] as Map<String, Any>)["must_not"] as List<Map<String, Any>>
        assertEquals("expertClassification.version", (missing[0]["exists"] as Map<String, Any>)["field"])
        @Suppress("UNCHECKED_CAST")
        val notTerm = (should[1]["bool"] as Map<String, Any>)["must_not"] as List<Map<String, Any>>
        assertEquals(
            mapOf("expertClassification.version" to ExpertClassificationService.VERSION),
            notTerm[0]["term"]
        )
    }

    @Test
    fun `onlyPending false passes empty filters (force recompute)`() {
        stubScan(batches = listOf(listOf(production("0001"))))
        service.run(dryRunRequest(onlyPending = false), executionId)

        val captor = listCaptor<Map<String, Any>>()
        Mockito.verify(searchService).searchAfterExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), captor.capture() ?: emptyList(), eqValue(500), anyValue<(List<ExpertProfile>) -> Boolean>({ true })
        )
        assertTrue(captor.value.isEmpty())
    }

    @Test
    fun `missing mapping aborts immediately with FAILED and never scans (I2-2)`() {
        Mockito.`when`(indexWriter.checkExpertClassificationMapping(anyValue(ExpertIndexLevel.CANDIDATE))).thenReturn(false)

        val result = service.run(executeRequest(), executionId)

        assertTrue(result.immediateFailed)
        assertEquals("FAILED", result.taskFinalStatus)
        Mockito.verify(searchService, Mockito.never()).searchAfterExpertsFiltered(anyValue(ExpertIndexLevel.CANDIDATE), anyList(), anyValue(500), anyValue<(List<ExpertProfile>) -> Boolean>({ true }))
        Mockito.verify(indexWriter, Mockito.never()).bulkUpdateExpertClassifications(anyValue(ExpertIndexLevel.CANDIDATE), anyList())
        val progressCaptor = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore).update(
            eqValue(ExpertClassificationBackfillService.TASK_TYPE), progressCaptor.capture() ?: TaskProgress("", "", 0, 0, 0), eqValue(executionId)
        )
        assertEquals("FAILED", progressCaptor.value.status)
        assertTrue(progressCaptor.value.message!!.contains("mapping"))
    }

    @Test
    fun `first batch all mapper errors aborts immediately (I2-2)`() {
        stubScan(batches = listOf(
            listOf(production("0001"), academic("0002")),
            listOf(unknown("0003"))
        ))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(bulkResult("0001" to ClassificationBulkItemStatus.FAILED, "0002" to ClassificationBulkItemStatus.FAILED, allMapper = true))

        val result = service.run(executeRequest(), executionId)

        assertEquals(2L, result.writeFailure)
        assertEquals("FAILED", result.taskFinalStatus)
        Mockito.verify(indexWriter, Mockito.times(1)).bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList())
        assertNotNull(result.terminalMessage)
        assertTrue(result.terminalMessage!!.contains("mapper"))
    }

    @Test
    fun `wholesale bulk failure stops the scan (I2-4)`() {
        stubScan(batches = listOf(
            listOf(production("0001"), academic("0002")),
            listOf(unknown("0003"))
        ))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(bulkResult("0001" to ClassificationBulkItemStatus.FAILED, "0002" to ClassificationBulkItemStatus.FAILED, wholesale = "Bulk request failed for index orcid_info_candidate: ES unavailable"))

        val result = service.run(executeRequest(), executionId)

        assertEquals(2L, result.writeFailure)
        assertEquals("FAILED", result.taskFinalStatus)
        assertNotNull(result.terminalMessage)
        assertTrue(result.terminalMessage!!.contains("ES unavailable"))
        Mockito.verify(indexWriter, Mockito.times(1)).bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList())
    }

    @Test
    fun `maxDocs sampling stops after the limit (I2-6)`() {
        stubScan(batches = listOf(
            listOf(production("0001"), academic("0002"), unknown("0003")),
            listOf(production("0004"), academic("0005"))
        ))
        Mockito.`when`(
            indexWriter.bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), anyList<ClassificationBulkItem>())
        ).thenReturn(bulkResult("0001" to ClassificationBulkItemStatus.UPDATED, "0002" to ClassificationBulkItemStatus.UPDATED, "0003" to ClassificationBulkItemStatus.UPDATED))

        val result = service.run(executeRequest(maxDocs = 3), executionId)

        assertEquals(3L, result.scanned)
        assertEquals(3L, result.writeSuccess)
        val captor = listCaptor<ClassificationBulkItem>()
        Mockito.verify(indexWriter).bulkUpdateExpertClassifications(eqValue(ExpertIndexLevel.CANDIDATE), captor.capture() ?: emptyList())
        assertEquals(listOf("0001", "0002", "0003"), captor.value.map { it.esDocId })
    }

    @Test
    fun `reasonCounts aggregates negative evidence codes (I2-6)`() {
        stubScan(batches = listOf(listOf(unknown("0003"), surgeon("0004"))))
        val result = service.run(dryRunRequest(), executionId)

        assertEquals(1L, result.reasonCounts["INSUFFICIENT_EVIDENCE"])
        assertEquals(1L, result.reasonCounts["CLINICAL_ROLE"])
        assertEquals(1L, result.classifiedByType[ExpertType.UNKNOWN.name])
        assertEquals(1L, result.classifiedByType[ExpertType.SERVICE_ONLY.name])
    }

    @Test
    fun `segmented cancellable delay sleeps at most 1s per step and rechecks cancel (I2-4)`() {
        val sleeps = mutableListOf<Long>()
        var checks = 0
        val cancelled = service.cancellableDelay(2500, isCancelled = { checks++ >= 2 }, sleep = { sleeps.add(it) })
        assertFalse(cancelled)
        assertEquals(listOf(1000L, 1000L), sleeps, "第二次检查后取消，不能再睡第三个 500ms")
        assertEquals(3, checks)

        val fullSleeps = mutableListOf<Long>()
        val completed = service.cancellableDelay(2500, isCancelled = { false }, sleep = { fullSleeps.add(it) })
        assertTrue(completed)
        assertEquals(listOf(1000L, 1000L, 500L), fullSleeps, "单次 sleep 不超过 1000ms")
    }

    @Test
    fun `validation rejects missing fields wrong version and bad confirmation (I2-3)`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.run(ExpertClassificationBackfillRequest(mode = BackfillMode.DRY_RUN, version = ExpertClassificationService.VERSION), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(ExpertClassificationBackfillRequest(level = ExpertIndexLevel.CANDIDATE, version = ExpertClassificationService.VERSION), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(version = "rnd-v2"), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(confirmation = null), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(confirmation = "EXECUTE_RAW:rnd-v1-2026"), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(batchSize = 50), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(delayMs = 6000), executionId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.run(executeRequest().copy(maxDocs = 0), executionId)
        }
        Mockito.verify(indexWriter, Mockito.never()).bulkUpdateExpertClassifications(anyValue(ExpertIndexLevel.CANDIDATE), anyList())
        Mockito.verify(searchService, Mockito.never()).searchAfterExpertsFiltered(anyValue(ExpertIndexLevel.CANDIDATE), anyList(), anyValue(500), anyValue<(List<ExpertProfile>) -> Boolean>({ true }))
    }
}
