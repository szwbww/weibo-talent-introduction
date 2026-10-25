package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

class DiscoveryVerificationQueryServiceTest {
    private val repository = mock(DiscoveryVerificationRepository::class.java)
    private val admissions = mock(DiscoveryReviewRepository::class.java)
    private val service = DiscoveryVerificationQueryService(repository, admissions,
        DiscoveryVerificationProperties(enabled = true, apolloApiKey = "NEVER-OUTPUT-SECRET", orcidAccessToken = "TOKEN-SECRET"))
    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private fun run(id: Long, mode: VerificationMode = VerificationMode.ENFORCE) =
        VerificationRun(id, mode, mode.policyVersion, true, "{}", true, 17, now, now)

    @Test fun `cancelled rechecks retain separate counts frozen control full history and actual saved steps`() {
        val f = AdmissionGateFixture()
        val item = f.item().copy(id = 20, ownerExecutionId = 20, reviewEpoch = 20,
            state = VerificationState.CANCELLED, outcome = null, route = VerificationRoute.NONE)
        val spec = RecheckSpec("operator", "request", "{}", RecheckEvidenceMode.REUSE, now, "code", "parser")
        val recheck = RecheckRun(spec, RecheckControl(RecheckPhase.CANCELLED, "a".repeat(64)))
        val run = run(20).copy(recheckJson = f.mapper.writeValueAsString(recheck))
        val filter = VerificationReportFilter(20, result = "CANCELLED")
        val counts = VerificationCounts(profiles = 1, cancelled = 1)
        val steps = (1..8).map { VerificationStep(it.toLong(), item.id, it, 1, VerificationStepState.NOT_STARTED,
            null, null, null, null, null, null, null, now) }
        `when`(repository.findRun(20)).thenReturn(run)
        `when`(repository.recheck(run)).thenReturn(recheck)
        `when`(repository.findItem(item.id)).thenReturn(item)
        `when`(repository.findSteps(item.id)).thenReturn(steps)
        `when`(repository.reportingCounts(filter)).thenReturn(mapOf(0L to counts))
        `when`(repository.reportingCounts(VerificationReportFilter(20))).thenReturn(mapOf(0L to counts))
        `when`(repository.reportingRecordTotal(filter)).thenReturn(1L)
        `when`(repository.reportingRecords(filter, 0, 20)).thenReturn(listOf(item))
        `when`(repository.findHistoryByDocIds(listOf(f.docId))).thenReturn(listOf(f.item(), item))
        val records = service.records(filter, 0, 20)
        assertEquals("CANCELLED", records.lifecycle)
        assertEquals("CANCELLED", records.items.single().resultLabel)
        assertEquals(20L, records.items.single().reviewEpoch)
        assertEquals(1L, records.counts!!.cancelled)
        assertEquals(0L, records.counts!!.queued + records.counts!!.errors + records.counts!!.autoAdmitted)
        val detail = service.item(item.id)
        assertEquals(recheck, detail.recheck); assertEquals(steps, detail.steps); assertEquals(20L, detail.reviewEpoch)
        assertEquals(listOf(0L, 20L), service.history(listOf(f.docId)).map { it.reviewEpoch })
        verify(repository).findHistoryByDocIds(listOf(f.docId))
        verify(repository, never()).findItemsByDocIds(anyCollection<String>() ?: emptyList())
    }

    @Test fun `details return only saved recorded trace tolerate unknown fields and never synthesize legacy trace`() {
        val f = AdmissionGateFixture()
        val base = f.item(facts = emptyList())
        val search = ruleSearch().copy(matchingVersion = 2, detailsComplete = true)
        val trace = DiscoveryVerificationRules(ruleClock).evaluate(ruleSnapshot(), emptyList(), search, ruleVersion = 2).trace!!
        `when`(repository.findRun(base.ownerExecutionId)).thenReturn(run(base.ownerExecutionId))
        for (saved in listOf(null, trace, trace.copy(traceOrigin = "RECOMPUTED"))) {
            val payload = VerificationWorkerEvidence(search = search, ruleVersion = 2,
                diagnostics = WorkerDiagnostics(decision = saved))
            val json = f.mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(payload)
            (json.path("diagnostics") as com.fasterxml.jackson.databind.node.ObjectNode).put("futureField", true)
            if (saved != null) (json.path("diagnostics").path("decision") as com.fasterxml.jackson.databind.node.ObjectNode)
                .put("futureStep", "NEW")
            `when`(repository.findItem(base.id)).thenReturn(base.copy(evidenceJson = f.mapper.writeValueAsString(json)))
            val detail = service.item(base.id)
            assertEquals(saved?.takeIf { it.traceOrigin == "RECORDED" }, detail.diagnostics!!.decision)
        }
        `when`(repository.findItem(base.id)).thenReturn(base.copy(evidenceJson = """{"evidence":[],"ruleVersion":1}"""))
        assertNull(service.item(base.id).diagnostics)
        verify(repository, times(4)).findItem(base.id)
        verify(repository, times(4)).findRun(base.ownerExecutionId)
        verify(repository, times(4)).findAttempts(base.id)
        verify(repository, times(4)).findSteps(base.id)
        verifyNoMoreInteractions(repository)
    }

    @Test fun `detail recomputes persisted version two without upgrading old evidence or writing`() {
        val f = AdmissionGateFixture()
        val base = f.item(facts = emptyList())
        val candidate = ApolloCandidate("v2-person", "Ada", "Lee", null, "Ada Lee", "Assistenzprofessor",
            VerificationInstitution("Other University"), headline = "Assistant Professor at Other University")
        val search = ApolloSearchEvidence(listOf(candidate), true, true, setOf("Ada Lee"), matchingVersion = 2,
            detailsComplete = true, institutionAliasVersion = 2)
        val payload = VerificationWorkerEvidence(search = search, ruleVersion = 2)
        val row = base.copy(outcome = VerificationOutcome.APOLLO_CLEAR, route = VerificationRoute.APOLLO,
            evidenceJson = f.mapper.writeValueAsString(payload), apolloPersonId = candidate.id, personMatchedAt = ruleClock.instant())
        `when`(repository.findItem(row.id)).thenReturn(row)
        `when`(repository.findRun(row.ownerExecutionId)).thenReturn(run(row.ownerExecutionId))
        val detail = service.item(row.id)
        assertEquals(VerificationOutcome.APOLLO_CLEAR, detail.outcome)
        assertEquals("Assistenzprofessor", detail.matchedCandidate!!.title)
        assertEquals(listOf("completedDoctorate", "reliableAge"), detail.record.missingFields)
        assertEquals(2, f.mapper.readTree(repository.findItem(row.id)!!.evidenceJson).path("ruleVersion").intValue())
        verify(repository, times(2)).findItem(row.id)
        verify(repository).findRun(row.ownerExecutionId)
        verify(repository).findAttempts(row.id)
        verify(repository).findSteps(row.id)
        verifyNoMoreInteractions(repository)
    }

    @Test fun `summary distinguishes missing empty expired and audit suggestions using batched aggregate`() {
        val ids = listOf(3001L, 3002L, 3003L, 3004L)
        `when`(repository.reportingRuns(ids)).thenReturn(mapOf(3001L to run(3001), 3003L to run(3003), 3004L to run(3004, VerificationMode.AUDIT)))
        `when`(repository.reportingCollectionStatuses(ids)).thenReturn(mapOf(3002L to "SUCCESS", 3003L to "SUCCESS", 3004L to "SUCCESS"))
        val filter = VerificationReportFilter(3001)
        `when`(repository.reportingCounts(filter, executionIds = ids)).thenReturn(mapOf(3001L to VerificationCounts(profiles = 1, autoAdmitted = 1, directAdmitted = 1, clear = 1),
            3004L to VerificationCounts(profiles = 2, clear = 1, unclear = 1)))
        `when`(repository.reportingSuggestions(filter, executionIds = ids)).thenReturn(mapOf(3004L to Triple(2L, 1L, 1L)))
        val rows = service.summary(ids)
        assertEquals("EXPIRED", rows[0].collectionStatus)
        assertEquals(ApolloSettingSource.LEGACY, rows[0].apolloAccess!!.source)
        assertTrue(rows[0].apolloAccess!!.searchEnabled); assertTrue(rows[0].apolloAccess!!.detailEnabled)
        assertNull(rows[1].apolloAccess); assertNull(rows[1].apolloAttempts)
        assertNull(rows[2].apolloAttempts) // An absent aggregate is unknown, not a fabricated zero.
        assertEquals("NOT_RECORDED", rows[1].coverage); assertNull(rows[1].counts); assertNull(rows[1].snapshotVersion)
        assertEquals("RECORDED", rows[2].coverage); assertEquals(0L, rows[2].counts!!.profiles)
        assertEquals("COMPLETED", rows[2].lifecycle); assertEquals(17L, rows[2].snapshotVersion)
        assertEquals(0L, rows[3].counts!!.autoAdmitted); assertEquals(VerificationSuggestions(2, 1, 1), rows[3].suggested)
        verify(repository).reportingCounts(filter, executionIds = ids)
        verify(repository).reportingRuns(ids)
        verify(repository).reportingCollectionStatuses(ids)
        verify(repository).reportingSuggestions(filter, executionIds = ids)
        verify(repository).reportingAttemptChannels(ids)
        verifyNoInteractions(admissions)
        verifyNoMoreInteractions(repository)
    }

    @Test fun `legacy batches and records remain null and have no fabricated items`() {
        `when`(repository.reportingCollectionStatuses(listOf(3002L))).thenReturn(mapOf(3002L to "SUCCESS"))
        assertNull(service.batches(3002, 0, 20).runCounts)
        val records = service.records(VerificationReportFilter(3002), 0, 20)
        assertNull(records.total); assertNull(records.counts); assertEquals(emptyList<VerificationRecord>(), records.items)
        verifyNoInteractions(admissions)
    }

    @Test fun `batch page retains full run cumulative time and complete per profile reason counts`() {
        val filter = VerificationReportFilter(3001)
        `when`(repository.findRun(3001)).thenReturn(run(3001))
        `when`(repository.reportingCounts(filter)).thenReturn(mapOf(0L to VerificationCounts(profiles = 123)))
        `when`(repository.reportingCounts(filter, batches = true)).thenReturn(linkedMapOf(
            1L to VerificationCounts(profiles = 40), 2L to VerificationCounts(profiles = 40),
            3L to VerificationCounts(profiles = 43)))
        `when`(repository.reportingBatchTimes(3001)).thenReturn(mapOf(2 to now))
        `when`(repository.reportingBatchReasons(3001)).thenReturn(listOf(
            Triple(2, """["IDENTITY_AMBIGUOUS","UNKNOWN_LONG_CODE","UNKNOWN_LONG_CODE","R3","R4","R5"]""", 2L),
            Triple(2, """["UNKNOWN_LONG_CODE"]""", 1L)))
        val page = service.batches(3001, 1, 1)
        assertEquals(123L, page.runCounts!!.profiles)
        assertEquals(80L, page.items.single().cumulativeProfiles)
        assertEquals(now, page.items.single().updatedAt)
        assertEquals(5, page.items.single().reasons.size)
        assertEquals(3L, page.items.single().reasons.single { it.code == "UNKNOWN_LONG_CODE" }.count)
        assertEquals("UNKNOWN_LONG_CODE", page.items.single().reasons.single { it.code == "UNKNOWN_LONG_CODE" }.label)
        assertEquals("身份存在歧义", page.items.single().reasons.single { it.code == "IDENTITY_AMBIGUOUS" }.label)
        assertEquals(17L, page.snapshotVersion)
        verifyNoInteractions(admissions)
    }

    @Test fun `specific limits retain old generic label and permit multiple reasons per profile without writes`() {
        val filter = VerificationReportFilter(3001)
        `when`(repository.findRun(3001)).thenReturn(run(3001))
        `when`(repository.reportingCounts(filter)).thenReturn(mapOf(0L to VerificationCounts(profiles = 1)))
        `when`(repository.reportingCounts(filter, batches = true)).thenReturn(mapOf(1L to VerificationCounts(profiles = 1)))
        `when`(repository.reportingBatchReasons(3001)).thenReturn(listOf(Triple(1,
            """["SEARCH_INCOMPLETE","SEARCH_PAGE_LIMIT","ITEM_REQUEST_LIMIT"]""", 1L)))
        val reasons = service.batches(3001, 0, 20).items.single().reasons.associate { it.code to it.label }
        assertEquals("查询尚未完成", reasons["SEARCH_INCOMPLETE"])
        assertEquals("查询未完成：达到单次查询页数上限", reasons["SEARCH_PAGE_LIMIT"])
        assertEquals("核验未完成：达到单档案请求上限", reasons["ITEM_REQUEST_LIMIT"])
        assertEquals(3, reasons.size)
        verifyNoInteractions(admissions)
        verify(repository).findRun(3001)
        verify(repository).reportingCounts(filter)
        verify(repository).reportingCounts(filter, batches = true)
        verify(repository).reportingBatchTimes(3001)
        verify(repository).reportingBatchReasons(3001)
        verify(repository).reportingCollectionStatuses(listOf(3001))
        verify(repository).reportingSuggestions(filter)
        verifyNoMoreInteractions(repository)
    }
    @Test fun `summary projects all frozen switch combinations and recorded attempts without credentials or billing claims`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules()
        val switches = listOf(true to true, true to false, false to true, false to false)
        val ids = (4001L..4004L).toList()
        val runs = ids.zip(switches).associate { (id, flags) ->
            val access = """{"schemaVersion":1,"settingRevision":3,"searchEnabled":${flags.first},"detailEnabled":${flags.second},"source":"SAVED"}"""
            id to run(id).copy(settingsJson = """{"apolloAccess":$access,"unrelatedBudget":17}""")
        }
        val attempts = VerificationAttemptChannels(3, 2, 1, 1)
        `when`(repository.reportingRuns(ids)).thenReturn(runs)
        `when`(repository.reportingAttemptChannels(ids)).thenReturn(ids.associateWith { attempts })
        val rows = service.summary(ids)
        rows.zip(switches).forEach { (row, flags) ->
            val access = row.apolloAccess!!
            assertEquals(flags.first, access.searchEnabled); assertEquals(flags.second, access.detailEnabled)
            assertEquals(3L, access.settingRevision); assertEquals(1, access.schemaVersion)
            assertEquals(ApolloSettingSource.SAVED, access.source)
            assertEquals(attempts, row.apolloAttempts)
            assertEquals(6L, row.apolloAttempts!!.let { it.search + it.detail + it.other })
            assertEquals(0L, row.counts!!.apolloHttpAttempts) // Existing counters are not replaced by channel counters.
        }
        val json = mapper.writeValueAsString(rows)
        assertFalse(json.contains("SECRET")); assertFalse(json.contains("credits", ignoreCase = true))
        assertFalse(json.contains("billed", ignoreCase = true))
        verify(repository).reportingAttemptChannels(ids)
        verifyNoInteractions(admissions)
    }

    @Test fun `legacy access follows recorded requested flag and deployment snapshots retain their saved source`() {
        val ids = listOf(4101L, 4102L, 4103L)
        `when`(repository.reportingRuns(ids)).thenReturn(mapOf(
            4101L to run(4101).copy(apolloRequested = false),
            4102L to run(4102),
            4103L to run(4103).copy(settingsJson = """{"apolloAccess":{"schemaVersion":1,"settingRevision":0,"searchEnabled":true,"detailEnabled":false,"source":"DEPLOYMENT_DEFAULT"}}""")))
        val rows = service.summary(ids)
        assertEquals(listOf(false, true), rows.take(2).map { it.apolloAccess!!.searchEnabled })
        assertEquals(listOf(false, true), rows.take(2).map { it.apolloAccess!!.detailEnabled })
        assertTrue(rows.take(2).all { it.apolloAccess!!.source == ApolloSettingSource.LEGACY })
        assertEquals(ApolloSettingSource.DEPLOYMENT_DEFAULT, rows[2].apolloAccess!!.source)
        assertEquals(0L, rows[2].apolloAccess!!.settingRevision)
        assertFalse(rows[2].apolloAccess!!.detailEnabled)
        verifyNoInteractions(admissions)
    }

    @Test fun `malformed access is unknown while actual registered attempts remain visible`() {
        val valid = """{"schemaVersion":1,"settingRevision":3,"searchEnabled":true,"detailEnabled":false,"source":"SAVED"}"""
        val malformed = listOf("broken", "null", "[]", """{"apolloAccess":null}""",
            """{"apolloAccess":{}}""", """{"apolloAccess":$valid}""".replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            """{"apolloAccess":$valid}""".replace("\"searchEnabled\":true", "\"searchEnabled\":\"true\""),
            """{"apolloAccess":$valid}""".replace("\"settingRevision\":3", "\"settingRevision\":3.5"),
            """{"apolloAccess":$valid}""".replace("\"source\":\"SAVED\"", "\"source\":\"FUTURE\""))
        val ids = malformed.indices.map { 4201L + it }
        `when`(repository.reportingRuns(ids)).thenReturn(ids.zip(malformed).associate { (id, json) ->
            id to run(id).copy(settingsJson = json)
        })
        val attempts = VerificationAttemptChannels(3, 2, 1, 1)
        `when`(repository.reportingAttemptChannels(ids)).thenReturn(ids.associateWith { attempts })
        service.summary(ids).forEach {
            assertEquals("RECORDED", it.coverage); assertNull(it.apolloAccess)
            assertEquals(attempts, it.apolloAttempts)
        }
        verifyNoInteractions(admissions)
    }

    @Test fun `attempt aggregate failure is propagated rather than rendered as zero`() {
        val ids = listOf(4301L)
        `when`(repository.reportingRuns(ids)).thenReturn(mapOf(4301L to run(4301)))
        `when`(repository.reportingAttemptChannels(ids))
            .thenThrow(org.springframework.dao.DataAccessResourceFailureException("attempt ledger offline"))
        assertThrows(org.springframework.dao.DataAccessResourceFailureException::class.java) { service.summary(ids) }
        verifyNoInteractions(admissions)
    }

    @Test fun `summary deduplicates ids before channel SQL and hides aggregates for unrecorded runs`() {
        val ids = listOf(4401L, 4402L)
        `when`(repository.reportingRuns(ids)).thenReturn(mapOf(4401L to run(4401)))
        `when`(repository.reportingAttemptChannels(ids)).thenReturn(mapOf(
            4401L to VerificationAttemptChannels(0, 0, 0, 0), 4402L to VerificationAttemptChannels(9, 9, 9, 9)))
        val rows = service.summary(listOf(4401, 4402, 4401))
        assertEquals(ids, rows.map { it.executionId })
        assertEquals(VerificationAttemptChannels(0, 0, 0, 0), rows[0].apolloAttempts)
        assertNull(rows[1].apolloAccess); assertNull(rows[1].apolloAttempts)
        verify(repository).reportingAttemptChannels(ids)
        verifyNoInteractions(admissions)
    }

    @Test fun `channel repository enforces bounded positive execution identifiers before SQL`() {
        val jdbc = mock(org.springframework.jdbc.core.JdbcTemplate::class.java)
        val sqlRepository = DiscoveryVerificationRepository(jdbc)
        listOf(emptyList(), listOf(0L), listOf(-1L), (1L..51L).toList()).forEach { ids ->
            assertThrows(IllegalArgumentException::class.java) { sqlRepository.reportingAttemptChannels(ids) }
        }
        verifyNoInteractions(jdbc)
        assertEquals(emptyMap<Long, VerificationAttemptChannels>(), sqlRepository.reportingAttemptChannels((1L..50L).toList()))
        assertEquals(1, mockingDetails(jdbc).invocations.size)
    }

    @Test fun `database error is never changed to zero`() {
        `when`(repository.reportingRuns(listOf(1L))).thenThrow(org.springframework.dao.DataAccessResourceFailureException("offline"))
        assertThrows(org.springframework.dao.DataAccessResourceFailureException::class.java) { service.summary(listOf(1)) }
    }

    @Test fun `all public reads share repeatable read transaction contract and expose no credentials`() {
        val annotation = DiscoveryVerificationQueryService::class.java.getAnnotation(Transactional::class.java)
        assertTrue(annotation.readOnly); assertEquals(Isolation.REPEATABLE_READ, annotation.isolation)
        val capabilities = service.capabilities()
        assertTrue(capabilities.ready); assertEquals(emptyList<String>(), capabilities.reasonCodes)
        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(capabilities)
        assertFalse(json.contains("SECRET")); verifyNoInteractions(repository, admissions)
        val disabled = DiscoveryVerificationQueryService(repository, admissions, DiscoveryVerificationProperties()).capabilities()
        assertFalse(disabled.ready); assertTrue(disabled.reasonCodes.contains("VERIFICATION_DISABLED"))
        assertTrue(disabled.reasonCodes.contains("APOLLO_API_KEY_MISSING"))
    }

    @Test fun `invalid request values are rejected before SQL`() {
        listOf(emptyList(), listOf(0L), (1L..51L).toList()).forEach { ids ->
            assertThrows(IllegalArgumentException::class.java) { service.summary(ids) }
        }
        assertThrows(IllegalArgumentException::class.java) { service.records(VerificationReportFilter(1), -1, 20) }
        assertThrows(IllegalArgumentException::class.java) { service.batches(1, 0, 101) }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, route = "NONE") }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, result = "UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, q = "x".repeat(101)) }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, batchNumber = 0) }
        verifyNoInteractions(repository, admissions)
    }
    @Test fun `diagnostic decoder accepts only versioned errors and projects whitelisted fields`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val diagnostic = VerificationDiagnostics.fromBytes(VerificationProvider.APOLLO, "/api/v1/people/person-1", 422,
            """{"message":"insufficient credits"}""".toByteArray(), false, "APOLLO_CREDITS_EXHAUSTED")
        val json = mapper.writeValueAsString(diagnostic)
        assertEquals(diagnostic, VerificationDiagnostics.read(json))
        assertNull(VerificationDiagnostics.read(null))
        assertNull(VerificationDiagnostics.read("""{"person":{"id":"person-1"}}"""))
        assertNull(VerificationDiagnostics.read("""{"endpoint":"person","document":null}"""))
        assertNull(VerificationDiagnostics.read("broken"))
        assertNull(VerificationDiagnostics.read(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertNull(VerificationDiagnostics.read(json.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5")))
        assertNull(VerificationDiagnostics.read((mapper.readTree(json) as com.fasterxml.jackson.databind.node.ObjectNode).put("bodyBytesRead", 1.5).toString()))
        assertNull(VerificationDiagnostics.read(json.replace("APOLLO_CREDITS_EXHAUSTED", "made-up")))
        val extended = mapper.readTree(json) as com.fasterxml.jackson.databind.node.ObjectNode
        extended.put("people", "private-person")
        extended.put("token", "private-token")
        val output = mapper.writeValueAsString(VerificationDiagnostics.read(extended.toString()))
        assertFalse(output.contains("private-person"))
        assertFalse(output.contains("private-token"))
        assertNull(VerificationAttemptSummary(1, VerificationProvider.APOLLO, VerificationQueryKind.DETAIL,
            1, 1, now, now, 422, "REQUEST_INVALID", null).diagnostic)
    }
    @Test fun `credit failure and deferred probe have the same recovery reason label`() {
        val filter = VerificationReportFilter(3001)
        `when`(repository.findRun(3001)).thenReturn(run(3001))
        `when`(repository.reportingCounts(filter)).thenReturn(mapOf(0L to VerificationCounts(profiles = 2)))
        `when`(repository.reportingCounts(filter, batches = true)).thenReturn(mapOf(1L to VerificationCounts(profiles = 2)))
        `when`(repository.reportingBatchReasons(3001)).thenReturn(listOf(Triple(1,
            """["PAID_DETAILS_DISABLED"]""", 1L), Triple(1,
            """["APOLLO_CREDITS_EXHAUSTED","CREDITS_WAIT"]""", 1L)))
        val reasons = service.batches(3001, 0, 20).items.single().reasons
        assertEquals(setOf("PAID_DETAILS_DISABLED", "APOLLO_CREDITS_EXHAUSTED", "CREDITS_WAIT"), reasons.map { it.code }.toSet())
        assertEquals("付费详情未启用，证据仍不完整", reasons.single { it.code == "PAID_DETAILS_DISABLED" }.label)
        assertTrue(reasons.filter { it.code != "PAID_DETAILS_DISABLED" }.all { it.label == "额度不足，等待恢复" })
        verifyNoInteractions(admissions)
    }

    @Test fun `worker diagnostics bound query and reuse projections without changing evidence`() {
        val queries = (0..39).map { WorkerQueryDiagnostic(it, "a".repeat(64), "VARIANT") }
        val reuse = WorkerDetailReuse(11, 7, "person-1", "b".repeat(64))
        val recorded = WorkerDiagnostics(queries = queries, detailReuses = List(205) { reuse }).bounded()
        assertEquals(32, recorded.queries.size); assertEquals(40, recorded.totalQueryCount)
        assertEquals(200, recorded.detailReuses.size); assertEquals(205, recorded.totalReused)
        assertTrue(recorded.truncated)
        var accumulated = WorkerDiagnostics()
        repeat(205) { accumulated = accumulated.reuse(reuse) }
        assertEquals(200, accumulated.detailReuses.size); assertEquals(205, accumulated.totalReused)
        assertTrue(accumulated.truncated)
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules()
        val legacy = mapper.readValue("""{"search":{"complete":true,"broadComplete":true}}""", VerificationWorkerEvidence::class.java)
        assertNull(legacy.diagnostics); assertTrue(legacy.search!!.complete)
        val payload = legacy.copy(diagnostics = recorded)
        assertEquals(recorded, mapper.readValue(mapper.writeValueAsString(payload), VerificationWorkerEvidence::class.java).diagnostics)
        // The API detail contract adds only a nullable diagnostic projection, not a new admission input.
        assertTrue(VerificationItemDetail::class.java.declaredFields.any { it.name == "diagnostics" })
        assertEquals(legacy.search, payload.search); assertEquals(legacy.progress, payload.progress)
    }

    @Test fun `recheck projects bounded saved facts with exact source binding and unknown XML page`() {
        val f = AdmissionGateFixture()
        val base = f.item().copy(ownerExecutionId = 20, reviewEpoch = 20)
        val ref = PaperSourceRef(3, 4, "b".repeat(64), 9, "paper", "fixture")
        val root = f.mapper.readTree(base.snapshotJson) as com.fasterxml.jackson.databind.node.ObjectNode
        root.set<com.fasterxml.jackson.databind.JsonNode>("reviewInput", f.mapper.valueToTree(
            VerificationReviewInput(null, paperSourceRefs = listOf(ref))))
        val fact = ruleJob("Professor").copy(identityHash = base.identityHash, factFingerprint = base.factFingerprint,
            provider = "PAPER", sourceUrl = "https://publisher.invalid/article", responseSha256 = ref.bodySha256,
            excerpt = "Ada completed her PhD.", temporalScope = TemporalScope.HISTORICAL,
            jsonPointer = "/archive/3/attempt/4/page/0/paragraph/2/year/2020")
        `when`(repository.findRun(20)).thenReturn(run(20).copy(recheckJson = "{}"))
        `when`(repository.trace(base.id)).thenReturn(VerificationTraceRead(false, emptyList(), null))
        fun read(facts: List<VerificationEvidence>): com.fasterxml.jackson.databind.JsonNode {
            val json = f.mapper.writeValueAsString(VerificationWorkerEvidence(evidence = facts))
            `when`(repository.findItem(base.id)).thenReturn(base.copy(snapshotJson = root.toString(), evidenceJson = json))
            return service.recheckItem(20, base.id).evidence!!
        }
        val saved = read(listOf(fact))
        val shown = saved.path("items").single()
        assertEquals("RECORDED", saved.path("state").asText())
        assertEquals(fact.excerpt, shown.path("excerpt").asText())
        assertEquals(fact.sourceUrl, shown.path("sourceUrl").asText())
        assertEquals(fact.responseSha256, shown.path("responseSha256").asText())
        assertEquals(fact.jsonPointer, shown.path("jsonPointer").asText())
        assertEquals(fact.fetchedAt, f.mapper.treeToValue(shown.path("fetchedAt"), java.time.Instant::class.java))
        assertEquals("HISTORICAL", shown.path("temporalScope").asText())
        assertTrue(shown.path("page").isNull); assertEquals(2, shown.path("paragraph").asInt())
        assertEquals(f.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(ref), shown.path("sourceRef"))
        assertEquals(7, read(listOf(fact.copy(jsonPointer = "/archive/3/attempt/4/page/7/paragraph/2/year/2020")))
            .path("items").single().path("page").asInt())
        assertTrue(read(listOf(fact.copy(responseSha256 = "c".repeat(64)))).path("items").single().path("sourceRef").isNull)
        assertTrue(read(listOf(fact.copy(jsonPointer = "/archive/3/attempt/5/page/7/paragraph/2/year/2020")))
            .path("items").single().path("sourceRef").isNull)
        assertEquals("EMPTY", read(listOf(fact.copy(identityHash = "a".repeat(64)))).path("state").asText())
        val bounded = read((1..101).map { fact.copy(excerpt = "Saved fact $it") })
        assertEquals(101, bounded.path("total").asInt()); assertTrue(bounded.path("truncated").asBoolean())
        assertEquals(100, bounded.path("items").size())
        assertEquals("Saved fact 100", bounded.path("items").last().path("excerpt").asText())
    }

    @Test fun `recheck distinguishes missing malformed and empty payload without copying snapshot evidence`() {
        val f = AdmissionGateFixture()
        val base = f.item().copy(ownerExecutionId = 20, reviewEpoch = 20)
        val root = f.mapper.readTree(base.snapshotJson) as com.fasterxml.jackson.databind.node.ObjectNode
        root.set<com.fasterxml.jackson.databind.JsonNode>("reviewInput", f.mapper.valueToTree(
            VerificationReviewInput(null, evidence = VerificationWorkerEvidence(evidence = listOf(ruleJob("Professor"))))))
        `when`(repository.findRun(20)).thenReturn(run(20).copy(recheckJson = "{}"))
        `when`(repository.trace(base.id)).thenReturn(VerificationTraceRead(false, emptyList(), null))
        for ((json, expected) in listOf(null to "MISSING", "broken" to "MALFORMED", "{}" to "EMPTY")) {
            `when`(repository.findItem(base.id)).thenReturn(base.copy(snapshotJson = root.toString(), evidenceJson = json))
            val view = service.recheckItem(20, base.id)
            val evidence = view.evidence!!
            assertEquals(expected, evidence.path("state").asText())
            assertEquals(0, evidence.path("items").size())
            assertFalse(view.trace.recorded); assertTrue(view.trace.steps.isEmpty())
        }
    }
}
