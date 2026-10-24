package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory

class DiscoveryVerificationWorkerTest {
    @org.junit.jupiter.api.io.TempDir lateinit var replayRoot: java.nio.file.Path

    @Test fun `child07 real pipeline traces frozen REUSE refresh cancellation and projection only retry`() {
        runChild07Pipeline()
    }
    @Test fun `frozen paid access gates cache and permits while preserving legacy and channel accounting`() =
        apolloAccessScenario()

    private fun apolloAccessScenario() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required" }
        DiagnosticMysql().withDatabaseName("apollo_access").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true,
                    mode = VerificationMode.ENFORCE, apolloApiKey = "fixture-key", requestsPerMinute = 1000)
                VerificationFlowFixture(jdbc, manager, props).use { f ->
                    val registration = DiscoveryVerificationRegistrationService(props, f.repository, f.writer, f.policy,
                        f.admissions, jdbc, org.springframework.transaction.support.TransactionTemplate(manager), f.apolloSettings, f.clock)
                    fun task(id: Long) {
                        jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (?,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)",
                            id, f.clock.instant().verificationUtc())
                    }
                    fun seed(run: VerificationRun, doc: String): VerificationItem {
                        val profile = ruleSnapshot().profile.copy(esDocId = doc, researchFieldIds = listOf("22"), lastPublicationYear = 2026)
                        val snapshot = DiscoveryVerificationAdmissionGate.snapshot(doc, profile, f.policy.evaluate(profile).configSnapshot)
                        @Suppress("UNCHECKED_CAST")
                        val raw = f.mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
                        f.raws[doc] = raw
                        val item = f.repository.reserveItem(VerificationReservation(run.executionId, doc, snapshot.identityHash,
                            run.policyVersion, "ORCID", doc, f.mapper.writeValueAsString(snapshot), snapshot.factFingerprint), f.clock.instant())
                        return f.repository.activateItem(item.id, 1, f.clock.instant())
                    }
                    fun detailPayload() = pendingDetailPayload().let { it.copy(ruleVersion = 2,
                        search = it.search!!.copy(candidates = it.search!!.candidates.take(1))) }
                    fun execute(run: VerificationRun, doc: String, payload: VerificationWorkerEvidence? = detailPayload(),
                        repository: DiscoveryVerificationRepository = f.repository): VerificationItem {
                        val row = seed(run, doc)
                        if (payload != null) f.cached(payload)
                        f.worker(repository).process(f.claim())
                        return f.item(row.id)
                    }
                    task(2101)
                    f.saveApolloSettings(true, true)
                    val a = registration.begin(2101, registration.resolveOptions(PaperSearchCriteria())).run!!
                    f.saveApolloSettings(true, false)
                    assertEquals(a, registration.begin(2101, PaperSearchCriteria(apolloAssist = false)).run)
                    task(2102)
                    val b = registration.begin(2102, PaperSearchCriteria()).run!!
                    task(2103)
                    val legacy = f.repository.createRun(2103, VerificationMode.ENFORCE, true,
                        """{"apolloMatchingVersion":2,"ruleVersion":2}""", f.clock.instant())
                    assertTrue(a.frozenApolloAccess(f.mapper).detailEnabled)
                    assertFalse(b.frozenApolloAccess(f.mapper).detailEnabled)
                    assertEquals(ApolloSettingSource.LEGACY, legacy.frozenApolloAccess(f.mapper).source)
                    val oldA = execute(a, "frozen-paid-a")
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, oldA.outcome)
                    assertEquals(1, f.repository.findAttempts(oldA.id).count { it.queryKind == VerificationQueryKind.DETAIL })
                    val oldL = execute(legacy, "legacy-paid-l")
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, oldL.outcome)
                    assertEquals(1, f.repository.findAttempts(oldL.id).count { it.queryKind == VerificationQueryKind.DETAIL })
                    println("APOLLO_ACCESS_SMOKE frozen A=true/true revision=${a.frozenApolloAccess(f.mapper).settingRevision} unchanged=true B=true/false revision=${b.frozenApolloAccess(f.mapper).settingRevision} legacy=LEGACY/true/true oldDetailHttp=2")

                    // A same-run paid result exists, but the free target must not even look it up.
                    val source = seed(b, "synthetic-paid-cache")
                    val sourceLease = f.claim()
                    assertEquals(source.id, sourceLease.id)
                    val detailJson = f.mapper.writeValueAsString(ApolloCandidate("person-1", "Ada", "Lee", null,
                        "Ada Lee", "Professor", VerificationInstitution("Example University")))
                    val sourceAttempt = f.repository.reserveAttemptWithBudget(source.id, sourceLease.leaseToken!!, sourceLease.generation,
                        VerificationAttemptRequest("synthetic-cache", VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1, 1,
                            """{"personId":"person-1","matchingVersion":2}"""), 1000, 1000, 12, f.clock.instant()).attemptId!!
                    assertTrue(f.repository.completeAttempt(source.id, sourceLease.leaseToken!!, sourceLease.generation, sourceAttempt,
                        200, "OK", detailJson, "d".repeat(64), f.clock.instant()))
                    assertTrue(f.repository.saveEvidenceAndOutcome(source.id, sourceLease.leaseToken!!, sourceLease.generation,
                        VerificationResult(VerificationState.COMPLETED, VerificationOutcome.PENDING, VerificationQualification.UNKNOWN,
                            VerificationRoute.NONE, f.mapper.writeValueAsString(detailPayload()), "[]"), f.clock.instant()))
                    assertNotNull(f.repository.findSuccessfulDetails(b.executionId, listOf("person-1"))["person-1"])
                    val observed = Mockito.mock(DiscoveryVerificationRepository::class.java,
                        org.mockito.AdditionalAnswers.delegatesTo<DiscoveryVerificationRepository>(f.repository))
                    val beforeHttp = f.apolloRequests.size
                    val free = execute(b, "free-cache-target", repository = observed)
                    assertEquals(VerificationState.COMPLETED, free.state)
                    assertEquals(VerificationOutcome.PENDING, free.outcome)
                    assertTrue(free.reasonsJson!!.contains("PAID_DETAILS_DISABLED"))
                    assertTrue(f.repository.findAttempts(free.id).isEmpty())
                    assertEquals(beforeHttp, f.apolloRequests.size)
                    val freePayload = f.mapper.readValue<VerificationWorkerEvidence>(free.evidenceJson!!)
                    assertFalse(freePayload.search!!.detailsComplete)
                    assertTrue(freePayload.search!!.detailsCompleted.isEmpty())
                    assertTrue(freePayload.diagnostics!!.detailReuses.isEmpty())
                    assertFalse(Mockito.mockingDetails(observed).invocations.any { it.method.name == "findSuccessfulDetails" })
                    assertFalse(Mockito.mockingDetails(observed).invocations.any { it.method.name == "reserveAttemptWithBudget" })
                    val recomputed = f.rules.evaluate(f.mapper.readValue<VerificationSnapshot>(free.snapshotJson),
                        freePayload.evidence, freePayload.search, institutionNormalization = freePayload.institutionNormalization,
                        ruleVersion = freePayload.ruleVersion)
                    assertEquals(recomputed.outcome, free.outcome)
                    assertEquals(recomputed.qualification, free.qualification)
                    assertEquals(recomputed.route, free.route)
                    assertEquals(recomputed.matchedPersonId, free.apolloPersonId)
                    assertEquals((recomputed.reasonCodes + "PAID_DETAILS_DISABLED").distinct(),
                        f.mapper.readValue<List<String>>(free.reasonsJson!!))
                    assertFalse(f.policy.evaluate(free.expertDocId, f.mapper.readValue<VerificationSnapshot>(free.snapshotJson).profile).eligible)
                    println("APOLLO_ACCESS_SMOKE free-required-detail outcome=${free.outcome} reason=PAID_DETAILS_DISABLED detailsComplete=false attempts=0 permits=0 cacheLookups=0 detailHttp=0 gate=not-admitted")

                    f.jobRole = null
                    f.apolloBody = """{"people":[{"id":"person-1","first_name":"Ada","last_name_obfuscated":"L**"}],"total_entries":1}"""
                    val liveStart = f.apolloRequests.size
                    val liveFree = execute(b, "free-live-search", payload = null)
                    assertEquals(VerificationOutcome.PENDING, liveFree.outcome)
                    assertTrue(liveFree.reasonsJson!!.contains("PAID_DETAILS_DISABLED"))
                    assertTrue(f.apolloRequests.size > liveStart)
                    assertTrue(f.apolloRequests.drop(liveStart).all { it.contains("/mixed_people/api_search") })
                    val strong = ApolloCandidate("person-strong", "Ada", "Lee", null, "Ada Lee", "Professor",
                        VerificationInstitution("Example University"))
                    val needless = execute(b, "free-needs-no-detail", detailPayload().let {
                        it.copy(search = it.search!!.copy(candidates = listOf(strong)))
                    })
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, needless.outcome)
                    assertEquals(VerificationProjection.PROJECTED, needless.projectionState)
                    assertTrue(f.repository.findAttempts(needless.id).isEmpty())
                    assertTrue(f.policy.evaluate(needless.expertDocId, f.mapper.readValue<VerificationSnapshot>(needless.snapshotJson).profile).eligible)
                    println("APOLLO_ACCESS_SMOKE live-free-search requests=${f.apolloRequests.size - liveStart} detailHttp=0 needless-detail=${needless.outcome} gate=eligible projection=${needless.projectionState}")

                    task(2104)
                    val malformed = f.repository.createRun(2104, VerificationMode.ENFORCE, true,
                        """{"apolloAccess":{"schemaVersion":99}}""", f.clock.instant())
                    val beforeInvalid = f.apolloRequests.size + f.orcidRequests.size
                    val invalid = execute(malformed, "invalid-access", payload = null)
                    assertEquals(VerificationState.ERROR, invalid.state)
                    assertEquals(listOf("CONFIG_INVALID"), f.mapper.readValue<List<String>>(invalid.reasonsJson!!))
                    assertEquals(beforeInvalid, f.apolloRequests.size + f.orcidRequests.size)
                    assertTrue(f.repository.findAttempts(invalid.id).isEmpty())
                    println("APOLLO_ACCESS_SMOKE malformed=CONFIG_INVALID providerHttp=0 attempts=0")

                    task(2105)
                    val channelsRun = registration.begin(2105, PaperSearchCriteria()).run!!
                    val channelItem = seed(channelsRun, "channel-attempts")
                    val lease = f.claim()
                    assertEquals(channelItem.id, lease.id)
                    val kinds = listOf(VerificationQueryKind.BROAD, VerificationQueryKind.NARROW, VerificationQueryKind.VARIANT,
                        VerificationQueryKind.DETAIL, VerificationQueryKind.DETAIL, VerificationQueryKind.ORCID)
                    kinds.forEachIndexed { index, kind ->
                        val permit = f.repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                            VerificationAttemptRequest("channel-$index", if (kind == VerificationQueryKind.ORCID) VerificationProvider.ORCID
                                else VerificationProvider.APOLLO, kind, 1, 1, "{}"), 1000, 1000, 100, f.clock.instant()).attemptId!!
                        if (index != 5) assertTrue(f.repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation,
                            permit, if (index == 3) 429 else 200, if (index == 3) "RATE_LIMIT" else "OK", null, null, f.clock.instant()))
                    }
                    task(2106)
                    registration.begin(2106, PaperSearchCriteria())
                    val query = DiscoveryVerificationQueryService(f.repository, f.admissions, props)
                    val summaries = query.summary(listOf(2101, 2102, 2103, 2104, 2105, 2106, 9999)).associateBy { it.executionId }
                    assertTrue(summaries.getValue(2101).apolloAccess!!.detailEnabled)
                    assertFalse(summaries.getValue(2102).apolloAccess!!.detailEnabled)
                    assertEquals(ApolloSettingSource.LEGACY, summaries.getValue(2103).apolloAccess!!.source)
                    assertNull(summaries.getValue(2104).apolloAccess)
                    val channels = summaries.getValue(2105).apolloAttempts!!
                    assertEquals(3L, channels.search)
                    assertEquals(2L, channels.detail)
                    assertEquals(1L, channels.other)
                    assertEquals(1L, channels.pendingDispatch)
                    assertEquals(6, f.repository.findAttempts(channelItem.id).size)
                    assertEquals(0L, summaries.getValue(2106).apolloAttempts!!.search)
                    assertEquals(0L, summaries.getValue(2106).apolloAttempts!!.detail)
                    assertNull(summaries.getValue(9999).apolloAccess)
                    assertNull(summaries.getValue(9999).apolloAttempts)
                    assertEquals(a.settingsJson, f.repository.findRun(a.executionId)!!.settingsJson)
                    assertEquals(legacy.settingsJson, f.repository.findRun(legacy.executionId)!!.settingsJson)
                    println("APOLLO_ACCESS_SMOKE channels search=${channels.search} detail=${channels.detail} other=${channels.other} pendingDispatch=${channels.pendingDispatch} totalRegistered=6 billing=not-inferred emptyRun=zero missingRun=unknown")
                    println("APOLLO_ACCESS_SMOKE PASS isolatedMySQL=true strictMockHTTP=true productionContext=false oldSettingsImmutable=true")
                }
            }
    }

    private fun worker(props: DiscoveryVerificationProperties, repo: DiscoveryVerificationRepository,
        factory: DefaultListableBeanFactory = DefaultListableBeanFactory()): DiscoveryVerificationWorker =
        DiscoveryVerificationWorker(props, repo, Mockito.mock(OrcidVerificationEvidenceClient::class.java),
            Mockito.mock(ApolloPeopleSearchClient::class.java), Mockito.mock(QualificationEvidenceParser::class.java),
            DiscoveryVerificationRules(ruleClock), Mockito.mock(DiscoveryVerificationCommitService::class.java),
            factory.getBeanProvider(RegistrationRecovery::class.java), ruleClock)

    @Test fun `runtime version three registration worker SQL gate query smoke and frozen legacy runs`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        DiagnosticMysql().withDatabaseName("identity_v2").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                VerificationFlowFixture(jdbc, manager).use { f ->
                    jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (2001,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)",
                        f.clock.instant().verificationUtc())
                    f.saveApolloSettings(true, true)
                    val registration = DiscoveryVerificationRegistrationService(DiscoveryVerificationProperties(enabled = true, mode = VerificationMode.ENFORCE, apolloApiKey = "fixture-key"), f.repository, f.writer, f.policy,
                        f.admissions, jdbc, org.springframework.transaction.support.TransactionTemplate(manager), f.apolloSettings, f.clock)
                    val context = registration.begin(2001, PaperSearchCriteria(apolloAssist = true))
                    assertEquals(3, f.mapper.readTree(context.run!!.settingsJson).path("ruleVersion").intValue())
                    assertEquals(2, f.mapper.readTree(context.run!!.settingsJson).path("institutionAliasVersion").intValue())
                    val doc = "identity-v2"
                    val profile = ruleSnapshot().profile.copy(esDocId = doc, researchFieldIds = listOf("22"),
                        lastPublicationYear = 2026, institution = "University of Geneva")
                    val frozen = DiscoveryVerificationAdmissionGate.snapshot(doc, profile, f.policy.evaluate(profile).configSnapshot)
                    @Suppress("UNCHECKED_CAST")
                    val raw = f.mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
                    f.raws[doc] = raw
                    val reserved = f.repository.reserveItem(VerificationReservation(2001, doc, frozen.identityHash,
                        context.run!!.policyVersion, "ORCID", doc, f.mapper.writeValueAsString(frozen), frozen.factFingerprint), f.clock.instant())
                    val row = f.repository.activateItem(reserved.id, 1, f.clock.instant())
                    f.jobRole = null
                    f.apolloBody = """{"people":[{"id":"person-1","name":"Ada Lee","first_name":"Ada","last_name":"Lee","title":"Assistenzprofessor","headline":"Assistant Professor at University of Southern Denmark","organization":{"name":"University of Southern Denmark"}}],"total_entries":1}"""
                    f.detailBody = """{"person":{"id":"person-1","name":"Ada Lee","first_name":"Ada","last_name":"Lee","title":"Assistenzprofessor","headline":"Assistant Professor at University of Southern Denmark","organization":{"name":"University of Southern Denmark"},"employment_history":[{"organization_name":"Université de Genève","title":"Researcher","current":false,"start_date":"2011-01-01","end_date":"2024-12-01"}]}}"""
                    f.worker().process(f.claim())
                    val done = f.item(row.id)
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, done.outcome)
                    assertEquals(VerificationProjection.PROJECTED, done.projectionState)
                    assertNotNull(done.admissionRevision)
                    val payload = f.mapper.readValue<VerificationWorkerEvidence>(done.evidenceJson!!)
                    assertEquals(3, payload.ruleVersion)
                    assertEquals(2, payload.search!!.institutionAliasVersion)
                    assertEquals("Assistenzprofessor", payload.search!!.candidates.single().title)
                    val trace = payload.diagnostics!!.decision!!
                    assertEquals("RECORDED", trace.traceOrigin)
                    assertEquals(3, trace.ruleVersion)
                    assertEquals(2, trace.aliasVersion)
                    assertEquals(done.apolloPersonId, trace.candidates.single().personId)
                    assertEquals("MATCH", trace.candidates.single().historicalInstitution)
                    assertEquals("NOT_EVALUATED", trace.candidates.single().professor)
                    assertEquals(1L, f.autoCount())
                    val snapshot = f.mapper.readValue<VerificationSnapshot>(done.snapshotJson)
                    assertTrue(f.policy.evaluate(done.expertDocId, snapshot.profile).eligible)
                    val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(done.id)
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, detail.outcome)
                    assertEquals("Assistenzprofessor", detail.matchedCandidate!!.title)
                    assertEquals(listOf("completedDoctorate", "reliableAge"), detail.record.missingFields)
                    assertEquals(trace, detail.diagnostics!!.decision)
                    val tool = DiscoveryVerificationReplayTool
                    val classpaths = mapOf("old" to System.getProperty("java.class.path"),
                        "fixed" to System.getProperty("java.class.path"))
                    val directory = tool.freeze(jdbc, manager, 2001, listOf(done.id),
                        replayRoot.toRealPath().resolve("frozen"), f.clock, classpaths)
                    val manifest = directory.resolve("manifest.json")
                    val manifestHash = tool.sha(java.nio.file.Files.readAllBytes(manifest))
                    val frozenInput = tool.load(manifest, manifestHash)
                    val beforeHash = tool.sha(java.nio.file.Files.readAllBytes(directory.resolve("before.json")))
                    val report = tool.evaluateFrozenRules(frozenInput, classpaths, 2, 2)
                    assertEquals("RULE_REEVALUATION", report.path("layer").asText())
                    assertEquals("APOLLO_CLEAR", report.path("rows").single().path("newRecomputedOutcome").asText())
                    assertEquals("RECOMPUTED", report.path("rows").single().path("newTrace").path("traceOrigin").asText())
                    assertEquals("NOT_EVALUATED", report.path("rows").single().path("gateResult").asText())
                    assertEquals(0, report.path("admissionWrites").asInt())
                    assertEquals(manifestHash, tool.sha(java.nio.file.Files.readAllBytes(manifest)))
                    assertEquals(beforeHash, tool.sha(java.nio.file.Files.readAllBytes(directory.resolve("before.json"))))
                    assertEquals(done, f.item(done.id))
                    assertEquals(1L, f.autoCount())
                    java.nio.file.Files.list(directory).use { paths -> paths.filter {
                        it.fileName.toString().startsWith("frozen-rules-")
                    }.forEach { path ->
                        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                            java.nio.file.Files.getPosixFilePermissions(path))
                    } }
                    println("DECISION_AUDIT_SMOKE HTTP->Rules->Worker/SQL->Query->freeze->isolated-fixed-time-report admissionDelta=0 immutable=true")
                    assertEquals(context.run!!.settingsJson, registration.begin(2001, PaperSearchCriteria(apolloAssist = true)).run!!.settingsJson)
                    println("QUALIFICATION_VERSION_SMOKE run=2001 workerRule=3 alias=2 sql=${done.outcome} gate=eligible projection=${done.projectionState} admission=${done.admissionRevision} requests=${done.requestCount}")
                }
                listOf("discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
                    "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
                // An old run without frozen settings retains its historical default.
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val row = f.seed("legacy-no-settings")
                    f.worker().process(f.claim())
                    assertEquals(1, f.mapper.readValue<VerificationWorkerEvidence>(f.item(row.id).evidenceJson!!).ruleVersion)
                }
                listOf("discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
                    "expert_discovery_admission").forEach { jdbc.update("DELETE FROM $it") }
                for (version in listOf(0, 1, 2, 3, 4)) {
                    VerificationFlowFixture(jdbc, manager).use { f ->
                        val row = f.seed("frozen-$version")
                        jdbc.update("UPDATE discovery_verification_run SET settings_json=? WHERE execution_id=2001",
                            """{"ruleVersion":3,"apolloMatchingVersion":2,"institutionAliasVersion":2}""")
                        val candidate = ApolloCandidate("person-frozen", "Ada", "Lee", null, "Ada Lee", "Assistenzprofessor",
                            VerificationInstitution("Other University"), headline = "Assistant Professor at Other University")
                        f.cached(VerificationWorkerEvidence(ruleVersion = version, progress = VerificationProgress(stage = "DECIDE"),
                            search = ApolloSearchEvidence(listOf(candidate), true, true, setOf("Ada Lee"), matchingVersion = 2,
                                detailsComplete = true, institutionAliasVersion = 1)))
                        f.worker().process(f.claim())
                        val done = f.item(row.id)
                        if (version == 4) assertEquals(VerificationState.ERROR, done.state)
                        else {
                            assertEquals(version, f.mapper.readValue<VerificationWorkerEvidence>(done.evidenceJson!!).ruleVersion)
                            assertEquals(if (version >= 2) VerificationOutcome.APOLLO_CLEAR else VerificationOutcome.PENDING, done.outcome)
                        }
                        assertTrue(f.apolloRequests.isEmpty())
                        assertEquals("""{"ruleVersion":3,"apolloMatchingVersion":2,"institutionAliasVersion":2}""",
                            f.repository.findRun(2001)!!.settingsJson)
                        println("QUALIFICATION_VERSION_SMOKE frozenPayloadRule=$version state=${done.state} outcome=${done.outcome} externalHttp=0")
                    }
                    listOf("discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
                        "expert_discovery_admission").forEach { jdbc.update("DELETE FROM $it") }
                }
            }
    }

    @Test fun `formal ORCID name is queried alongside middle initial snapshot name`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = "Duncan", familyNames = "S. Sutherland")) }
            val queries = current.searchQueries(snapshot, listOf(ruleFact("name", "Duncan Sutherland")))
            assertEquals(listOf("Duncan S. Sutherland", "Duncan Sutherland"), queries.map { it.name })
            val robert = snapshot.copy(profile = snapshot.profile.copy(givenNames = "Robert", familyNames = "H. Hadfield"))
            assertEquals(listOf("Robert H. Hadfield", "Robert Hadfield"), current.searchQueries(robert,
                listOf(ruleFact("name", "Robert Hadfield"))).map { it.name })
        }
    }
    @Test fun `new spelling queries are bounded while historical query lists retain original spelling`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = "Markus", familyNames = "Kästner")) }
            assertEquals(listOf("Markus Kästner", "Markus Kaestner", "Markus Kastner"),
                current.searchQueries(snapshot, emptyList(), ruleVersion = 1).map { it.name })
            assertEquals(listOf("Markus Kästner"), current.searchQueries(snapshot, emptyList(), ruleVersion = 0).map { it.name })
            val max = snapshot.copy(profile = snapshot.profile.copy(givenNames = "Max C.", familyNames = "Lemme"))
            assertEquals(listOf("Max C. Lemme", "Max Lemme"), current.searchQueries(max, emptyList(), ruleVersion = 1).map { it.name })
            assertNull(current.surnameFallback(snapshot, null))
            val ambiguous = DiscoveryInstitutionNormalizer.buildInputs("CNRS UMI 3288- NTU - THALES", emptyList(), emptyList())
            assertNull(current.surnameFallback(snapshot, ambiguous))
        }
    }
    @Test fun `disabled worker neither claims work nor calls optional recovery`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(), repo, factory).useWorker { it.tick() }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `enabled worker tolerates absent registration recovery and never creates an execution`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker { it.tick() }
        Mockito.verify(repo).listProjectionDue(10)
        Mockito.verify(repo).claimDue(ruleClock.instant(), 120)
        Mockito.verifyNoMoreInteractions(repo)
    }

    @Test fun `bounded registration recovery uses the already existing optional seam`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { it.tick() }
        Mockito.verify(recovery).recoverReserved(10)
        Mockito.verifyNoMoreInteractions(recovery)
    }

    @Test fun `reading current unblocked latch never claims or recovers work`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { current ->
            repeat(20) { assertFalse(current.isAuthBlocked()) }
        }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `new queries normalize aliases and narrow only one eligible identity institution`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val facts = listOf(ruleJob(source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF),
                ruleFact("otherName", "ＡＤＡ, Lee"), ruleFact("otherName", "Known Alias"), ruleFact("otherName", "known alias"))
            val queries = current.searchQueries(ruleSnapshot(), facts)
            assertEquals(listOf("Ada Lee", "Known Alias", "Ada Lee"), queries.map { it.name })
            assertEquals("Example University", queries.last().institution!!.name)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts + ruleJob(
                source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF, institution = "Other University")).size)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts).count { it.institution == null })
        }
    }
    private fun DiscoveryVerificationWorker.useWorker(action: (DiscoveryVerificationWorker) -> Unit) {
        try { action(this) } finally { close() }
    }

    private val gachon = "2Department of BioNano Technology, Gachon University, 1342 Seongnam-daero, Sujeong-gu, Seongnam-si 13120, Gyeonggi-do, Republic of Korea"

    @Test fun `new queries narrow once with the frozen subject and never with an ambiguous one`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val facts = listOf(ruleJob(institution = gachon))
            val context = DiscoveryInstitutionNormalizer.buildInputs(gachon,
                listOf(InstitutionEmploymentInput("e1", gachon)), emptyList())
            val queries = current.searchQueries(ruleSnapshot(), facts, context)
            assertEquals(listOf("Ada Lee", "Ada Lee"), queries.map { it.name })
            assertNull(queries.first().institution)
            assertEquals("Gachon University", queries.last().institution!!.name)

            val joint = "CNRS UMI 3288- NTU - THALES"
            val ambiguous = DiscoveryInstitutionNormalizer.buildInputs(joint,
                listOf(InstitutionEmploymentInput("e1", joint)), emptyList())
            assertNull(ambiguous.queryInstitution)
            assertEquals(1, current.searchQueries(ruleSnapshot(), listOf(ruleJob(institution = joint)), ambiguous).size)
            assertTrue(current.searchQueries(ruleSnapshot(), listOf(ruleJob(institution = joint)), ambiguous).all { it.institution == null })

            val rawOnly = DiscoveryInstitutionNormalizer.buildInputs(gachon, emptyList(), emptyList())
            assertNull(rawOnly.queryInstitution)
            assertEquals(1, current.searchQueries(ruleSnapshot(), emptyList(), rawOnly).size)
            assertNull(current.searchQueries(ruleSnapshot(), emptyList(), rawOnly).single().institution)
        }
    }

    @Test fun `unknown normalization version is accepted as data and rejected only by evaluation`() {
        val payload: VerificationWorkerEvidence = jacksonObjectMapper().readValue(
            """{"institutionNormalization":{"version":"INST_SUBJECT_V9","queryInstitution":"X","inputs":[]},
               "progress":{"stage":"DECIDE","schemaVersion":1}}""")
        assertEquals("INST_SUBJECT_V9", payload.institutionNormalization!!.version)
        val snapshot = ruleSnapshot()
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryVerificationRules(ruleClock).evaluate(snapshot, listOf(ruleJob()), null,
                institutionNormalization = payload.institutionNormalization)
        }
    }

    @Test fun `unparseable evidence persists the original JSON instead of an empty payload`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val lease = lease("{not-json")
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker { it.process(lease) }
        val saved = Mockito.mockingDetails(repo).invocations.filter { it.method.name == "saveFailure" }
        assertEquals(1, saved.size)
        val result = saved.single().arguments[3] as VerificationResult
        assertEquals("{not-json", result.evidenceJson)
        assertEquals("""["VERIFICATION_FAILED"]""", result.reasonsJson)
        assertEquals(VerificationState.ERROR, result.state)
    }

    private fun lease(evidenceJson: String?): VerificationItem {
        val now = ruleClock.instant()
        return VerificationItem(id = 7, ownerExecutionId = 1, expertDocId = "doc", identityHash = "b".repeat(64),
            policyVersion = VerificationMode.ENFORCE.policyVersion, sourceName = "ORCID", sourceItemKey = "doc",
            batchNumber = 1, snapshotJson = "{}", factFingerprint = "c".repeat(64), state = VerificationState.RUNNING,
            outcome = null, qualification = VerificationQualification.NOT_EVALUATED, route = VerificationRoute.NONE,
            apolloEnteredAt = null, searchCompletedAt = null, candidateFoundAt = null, personMatchedAt = null,
            apolloPersonId = null, evidenceJson = evidenceJson, reasonsJson = null, requestCount = 0,
            transientAttempts = 0, generation = 0, leaseToken = "token", leaseUntil = now.plusSeconds(120),
            nextAttemptAt = null, admissionRevision = null, projectionState = VerificationProjection.NOT_REQUIRED,
            projectionError = null, activatedAt = now, completedAt = null, createdAt = now, updatedAt = now)
    }
    private class DiagnosticMysql : org.testcontainers.containers.MySQLContainer<DiagnosticMysql>("mysql:8.0.36")

    @Test fun `mock HTTP failures persist atomically in MySQL and appear in item details once per request`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        DiagnosticMysql().withDatabaseName("verification_diagnostics").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                val cases = listOf(
                    """{"message":"insufficient credits token=private-token alice@example.invalid +1 (415) 555-0123","people":[{"name":"private-person"}]}""" to "APOLLO_CREDITS_EXHAUSTED",
                    """{"message":"invalid parameter"}""" to "REQUEST_INVALID",
                    com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(mapOf(
                        "message" to """invalid parameter {"password":"alpha TOPSECRET omega"}""")) to "REQUEST_INVALID",
                    com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(mapOf(
                        "message" to """invalid parameter {'token':'alpha TOPSECRET omega'}""")) to "REQUEST_INVALID",
                    "" to "APOLLO_HTTP_422_UNCLASSIFIED")
                cases.forEachIndexed { index, (body, expected) ->
                    VerificationFlowFixture(jdbc, manager).use { f ->
                        val row = f.seed("diagnostic-$index")
                        f.cached(VerificationWorkerEvidence(progress = VerificationProgress(stage = "APOLLO_SEARCH"),
                            search = ApolloSearchEvidence(), queries = listOf(ApolloSearchQuery("Ada Lee"))))
                        val http = verificationRestTemplate(f.props)
                        val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build()
                        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                                .withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).body(body)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                        DiscoveryVerificationWorker(f.props, f.repository,
                            Mockito.mock(OrcidVerificationEvidenceClient::class.java), ApolloPeopleSearchClient(f.props, http, f.clock),
                            QualificationEvidenceParser(f.clock), f.rules, f.commits,
                            DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock).useWorker { it.process(f.claim()) }
                        server.verify()
                        val attempts = f.repository.findAttempts(row.id)
                        assertEquals(1, attempts.size)
                        val attempt = attempts.single()
                        assertEquals(expected, attempt.resultCode)
                        assertNull(attempt.responseSha256)
                        assertEquals(1, f.item(row.id).requestCount)
                        val json = requireNotNull(attempt.responseJson)
                        listOf("private-token", "alice@example.invalid", "415", "private-person", "alpha TOPSECRET omega", "TOPSECRET omega").forEach {
                            assertFalse(json.contains(it), it)
                        }
                        val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
                        val detail = query.item(row.id)
                        assertEquals(expected, detail.attempts.single().diagnostic!!.classification)
                        val apiJson = f.mapper.writeValueAsString(detail.attempts.single().diagnostic)
                        listOf("private-token", "alice@example.invalid", "415", "private-person", "alpha TOPSECRET omega", "TOPSECRET omega").forEach {
                            assertFalse(apiJson.contains(it), it)
                        }
                        if (expected == "REQUEST_INVALID") {
                            assertTrue(json.contains("invalid parameter"))
                            assertTrue(apiJson.contains("invalid parameter"))
                        }
                        assertEquals(if (expected == "APOLLO_CREDITS_EXHAUSTED") VerificationState.RETRY_WAIT else VerificationState.ERROR, detail.state)
                        jdbc.update("UPDATE discovery_verification_attempt SET response_json=NULL WHERE id=?", attempt.id)
                        assertNull(query.item(row.id).attempts.single().diagnostic)
                        assertEquals(expected, f.repository.findAttempts(row.id).single().resultCode)
                        assertEquals(1, f.item(row.id).requestCount)
                        jdbc.update("DELETE FROM discovery_verification_attempt")
                        jdbc.update("DELETE FROM discovery_verification_item")
                        jdbc.update("DELETE FROM discovery_verification_run")
                    }
                }
            }
    }
    @Test fun `credit deferral retains retryAt without HTTP transient retries or auth latch`() {
        val retryAt = ruleClock.instant().plusSeconds(1793)
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java) { invocation ->
            if (invocation.method.name == "reserveAttemptWithBudget")
                VerificationAttemptReservation(deferred = VerificationBudgetReason.CREDITS_WAIT, retryAt = retryAt)
            else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val snapshot = ruleSnapshot()
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val payload = pendingDetailPayload()
        val lease = lease(mapper.writeValueAsString(payload)).copy(
            snapshotJson = mapper.writeValueAsString(snapshot), identityHash = snapshot.identityHash, factFingerprint = snapshot.factFingerprint,
            requestCount = 42, transientAttempts = 2)
        Mockito.`when`(repo.findRun(1)).thenReturn(VerificationRun(1, VerificationMode.ENFORCE, VerificationMode.ENFORCE.policyVersion,
            true, """{"apolloMatchingVersion":2}""", createdAt = ruleClock.instant(), updatedAt = ruleClock.instant()))
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker {
            it.process(lease)
            assertFalse(it.isAuthBlocked())
        }
        val saved = Mockito.mockingDetails(repo).invocations.single { it.method.name == "reschedule" }.arguments
        assertEquals(retryAt, saved[3]); assertEquals(2, saved[4])
        assertEquals(mapper.readTree(lease.evidenceJson), mapper.readTree(saved[5] as String))
        assertEquals("[\"CREDITS_WAIT\"]", saved[6])
        assertFalse(Mockito.mockingDetails(repo).invocations.any { it.method.name in setOf("saveFailure", "completeAttemptAndProgress") })
    }

    private fun pendingDetailPayload() = VerificationWorkerEvidence(
        search = ApolloSearchEvidence(candidates = listOf(
            ApolloCandidate("person-1", "Ada", null, "L**", null, null, null),
            ApolloCandidate("person-2", "Ada", null, "L**", null, null, null)),
            broadComplete = true, complete = true, matchingVersion = 2, queryNames = setOf("Ada Lee")),
        progress = VerificationProgress(stage = "APOLLO_SEARCH", queryIndex = 1, complete = true,
            candidateIds = listOf("person-1", "person-2")),
        queries = listOf(ApolloSearchQuery("Ada Lee")), ruleVersion = 1)

    @Test fun `runtime credit recovery smoke restarts resumes remaining details and commits through normal gate`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        DiagnosticMysql().withDatabaseName("credit_recovery").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val row = f.seed("credits-smoke")
                    f.cached(pendingDetailPayload())
                    var exhausted = true
                    val detailCalls = mutableListOf<String>()
                    val http = verificationRestTemplate(f.props)
                    val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build()
                    server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond { request ->
                        val id = request.uri.path.substringAfterLast('/')
                        detailCalls += id
                        if (exhausted) org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                            .body("""{"message":"insufficient credits"}""").contentType(org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request)
                        else org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            """{"person":{"id":"$id","first_name":"Ada","last_name":"${if (id == "person-1") "Gray" else "Lee"}",
                                "title":"Professor of Physics","organization":{"name":"Example University"}}}""",
                            org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request)
                    }
                    fun restarted() = DiscoveryVerificationWorker(f.props, f.repository,
                        OrcidVerificationEvidenceClient(f.props, http, f.clock), ApolloPeopleSearchClient(f.props, http, f.clock),
                        QualificationEvidenceParser(f.clock), f.rules, f.commits,
                        DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock)
                    restarted().useWorker { it.process(f.claim()); assertFalse(it.isAuthBlocked()) }
                    val waiting = f.item(row.id)
                    assertEquals(VerificationState.RETRY_WAIT, waiting.state)
                    assertNull(waiting.outcome); assertEquals(VerificationRoute.NONE, waiting.route)
                    assertEquals(0, waiting.transientAttempts); assertEquals(1, waiting.requestCount)
                    assertEquals(f.clock.instant().plusSeconds(1800), waiting.nextAttemptAt)
                    assertEquals(pendingDetailPayload(), f.mapper.readValue<VerificationWorkerEvidence>(waiting.evidenceJson!!))
                    val oldAttempt = f.repository.findAttempts(row.id).single()

                    // A second item reaches the same database guard after process restart: no wire call or billing.
                    val blocked = f.seed("credits-blocked")
                    f.cached(pendingDetailPayload())
                    restarted().useWorker { it.process(f.claim()) }
                    assertEquals(0, f.item(blocked.id).requestCount)
                    assertEquals(waiting.nextAttemptAt, f.item(blocked.id).nextAttemptAt)
                    assertEquals("[\"CREDITS_WAIT\"]", f.item(blocked.id).reasonsJson)
                    assertEquals(listOf("person-1"), detailCalls)

                    // DETAIL outage does not stop an already admitted projection, ORCID, or direct admission.
                    val direct = f.seed("direct-during-credit-pause")
                    f.cached(VerificationWorkerEvidence(orcidCompleted = f.selfDocuments(), ruleVersion = 1))
                    f.failProjection = true
                    f.worker().process(f.claim())
                    assertEquals(VerificationOutcome.DIRECT_CLEAR, f.item(direct.id).outcome)
                    assertEquals(VerificationProjection.ERROR, f.item(direct.id).projectionState)
                    f.failProjection = false
                    val orcid = f.seed("orcid-during-credit-pause")
                    f.worker().tick()
                    assertTrue(f.orcidRequests.isNotEmpty())
                    assertEquals(VerificationProjection.PROJECTED, f.item(direct.id).projectionState)
                    assertNotEquals(VerificationState.QUEUED, f.item(orcid.id).state)

                    exhausted = false
                    f.clock.now = waiting.nextAttemptAt!!
                    restarted().useWorker { it.process(f.claim()) }
                    val completed = f.item(row.id)
                    assertEquals(VerificationState.COMPLETED, completed.state)
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, completed.outcome)
                    assertNotNull(completed.admissionRevision)
                    assertEquals(VerificationProjection.PROJECTED, completed.projectionState)
                    assertEquals(listOf("person-1", "person-1", "person-2"), detailCalls)
                    assertEquals(oldAttempt, f.repository.findAttempts(row.id).first())
                    assertEquals(3, completed.requestCount)
                    assertEquals(setOf("person-1", "person-2"), f.mapper.readValue<VerificationWorkerEvidence>(completed.evidenceJson!!).search!!.detailsCompleted)
                    assertEquals(row.ownerExecutionId, completed.ownerExecutionId)
                    assertEquals(row.batchNumber, completed.batchNumber)
                    server.verify()
                }
            }
    }

    companion object {
        /** Direct runtime smoke entry: same real SQL/HTTP/worker/commit scenario, outside the JUnit runner. */
        @JvmStatic fun main(args: Array<String>) {
            if (args.contentEquals(arrayOf("--apollo-access-smoke"))) {
                DiscoveryVerificationWorkerTest().apolloAccessScenario()
                DiscoveryVerificationIntegrationTest.main(arrayOf("--queue-access-smoke"))
                return
            }
            DiscoveryVerificationWorkerTest().`runtime search trace survives interrupted pages and reuses only same run successes`()
            println("SEARCH_TRACE_RUNTIME_SMOKE completed: pagination restart, bounded diagnostics, same-run reuse, Gate parity")
        }
    }

    @Test fun `runtime search trace survives interrupted pages and reuses only same run successes`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        DiagnosticMysql().withDatabaseName("search_trace").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                listOf("before-ack", "after-ack", "legacy-page-six").forEach { crash ->
                    VerificationFlowFixture(jdbc, manager).use { f ->
                        val row = f.seed("trace-$crash")
                        val queries = listOf(ApolloSearchQuery("Ada Lee"),
                            ApolloSearchQuery("Ada Lee", institution = VerificationInstitution("Example University")),
                            ApolloSearchQuery("Ada Empty"))
                        f.cached(VerificationWorkerEvidence(search = ApolloSearchEvidence(matchingVersion = 2),
                            progress = VerificationProgress(stage = "APOLLO_SEARCH"), queries = queries))
                        val calls = mutableListOf<Pair<String, Int>>()
                        val http = verificationRestTemplate(f.props)
                        val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build()
                        server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                            org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond { request ->
                            check(!request.uri.path.startsWith("/api/v1/people/")) { "Incomplete search must not request DETAIL" }
                            val params = org.springframework.web.util.UriComponentsBuilder.fromUri(request.uri).build().queryParams
                            val page = params.getFirst("page")!!.toInt()
                            assertEquals("100", params.getFirst("per_page"))
                            val kind = if (params.containsKey("q_organization_name")) "narrow"
                                else if (params.getFirst("q_person_name")!!.contains("Empty")) "empty" else "broad"
                            calls += kind to page
                            val body = when (kind) {
                                "broad" -> """{"people":[{"id":"broad-$page","first_name":"Ada","last_name_obfuscated":"L**"}],"total_entries":600}"""
                                "narrow" -> """{"people":[{"id":"narrow-person","name":"Ada Lee","title":"Professor"}],"total_entries":1}"""
                                else -> """{"people":[],"total_entries":0}"""
                            }
                            org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(body,
                                org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request)
                        }
                        var responses = 0
                        val crashing = Mockito.mock(DiscoveryVerificationRepository::class.java) { invocation ->
                            val fifth = invocation.method.name == "completeAttemptAndProgress" && ++responses == 5
                            if (fifth && crash == "before-ack") throw IllegalStateException("injected before acknowledgement")
                            val result = invocation.method.invoke(f.repository, *invocation.arguments)
                            if (fifth) throw IllegalStateException("injected after acknowledgement")
                            result
                        }
                        fun worker(repo: DiscoveryVerificationRepository = f.repository) = DiscoveryVerificationWorker(
                            f.props, repo, OrcidVerificationEvidenceClient(f.props, http, f.clock),
                            ApolloPeopleSearchClient(f.props, http, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
                            DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock)
                        worker(crashing).useWorker { it.process(f.claim()) }
                        val interrupted = f.mapper.readValue<VerificationWorkerEvidence>(f.item(row.id).evidenceJson!!)
                        assertEquals(5, calls.size)
                        assertEquals(if (crash == "before-ack") 0 else 1, interrupted.progress.queryIndex)
                        assertEquals(if (crash == "before-ack") 5 else 1, interrupted.progress.page)
                        assertEquals(VerificationState.RUNNING, f.item(row.id).state)
                        if (crash == "legacy-page-six") {
                            val legacy = interrupted.copy(diagnostics = null, progress = interrupted.progress.copy(queryIndex = 0, page = 6))
                            jdbc.update("UPDATE discovery_verification_item SET evidence_json=? WHERE id=?",
                                f.mapper.writeValueAsString(legacy), row.id)
                        }
                        f.clock.now = f.clock.instant().plusSeconds(121)
                        worker().useWorker { it.process(f.claim()) }
                        val done = f.item(row.id)
                        val payload = f.mapper.readValue<VerificationWorkerEvidence>(done.evidenceJson!!)
                        assertEquals(queries, payload.queries)
                        assertEquals(VerificationOutcome.PENDING, done.outcome)
                        assertTrue(done.reasonsJson!!.contains("SEARCH_PAGE_LIMIT"))
                        assertFalse(payload.search!!.complete); assertFalse(payload.search!!.broadComplete)
                        assertFalse(payload.search!!.detailsComplete)
                        assertEquals(setOf("1", "2"), payload.progress.finishedQueryKeys.toSet())
                        assertEquals((1..5).map { "broad" to it } +
                            (if (crash == "before-ack") listOf("broad" to 5) else emptyList()) +
                            listOf("narrow" to 1, "empty" to 1), calls)
                        assertEquals((1..5).map { "broad-$it" }.toSet() + "narrow-person",
                            payload.search!!.candidates.map { it.id }.toSet())
                        val trace = payload.diagnostics!!
                        assertEquals(listOf("TRUNCATED", "EXHAUSTED", "EXHAUSTED"), trace.queries.map { it.state })
                        assertEquals(5, trace.queries[0].pagesReceived)
                        // Deleted historical diagnostics are NOT_RECORDED; never invent a last supplied total.
                        assertEquals(if (crash == "legacy-page-six") null else 600L, trace.queries[0].totalEntries)
                        val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(row.id)
                        assertEquals(trace, detail.diagnostics)
                        assertEquals(calls.size, detail.attempts.size)
                        assertEquals(calls.size, done.requestCount)
                        val profile = f.mapper.readValue<VerificationSnapshot>(done.snapshotJson).profile
                        val withTrace = f.policy.evaluate(done.expertDocId, profile)
                        jdbc.update("UPDATE discovery_verification_item SET evidence_json=? WHERE id=?",
                            f.mapper.writeValueAsString(payload.copy(diagnostics = null)), row.id)
                        val withoutTrace = f.policy.evaluate(done.expertDocId, profile)
                        assertEquals(withTrace, withoutTrace)
                        assertNull(DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(row.id).diagnostics)
                        server.verify()
                    }
                }
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val calls = mutableListOf<String>()
                    val http = verificationRestTemplate(f.props)
                    val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build()
                    var fail = false
                    server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond { request ->
                        val id = request.uri.path.substringAfterLast('/')
                        calls += id
                        if (fail) org.springframework.test.web.client.response.MockRestResponseCreators
                            .withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY).body("""{"message":"unclassified failure"}""")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request)
                        else org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            """{"person":{"id":"$id","first_name":"Ada","last_name":"Lee","title":"Professor"}}""",
                            org.springframework.http.MediaType.APPLICATION_JSON).createResponse(request)
                    }
                    fun run(doc: String, owner: Long = 2001, person: String = "shared-person", conflict: Boolean = false): VerificationItem {
                        val row = f.seed(doc)
                        if (owner != 2001L) {
                            f.repository.createRun(owner, VerificationMode.ENFORCE, true, "{}", f.clock.instant())
                            jdbc.update("UPDATE discovery_verification_item SET owner_execution_id=? WHERE id=?", owner, row.id)
                        }
                        val payload = pendingDetailPayload().copy(search = pendingDetailPayload().search!!.copy(
                            candidates = listOf(ApolloCandidate(person, "Ada", null, "L**", null, null, null)),
                            conflictingIds = if (conflict) setOf(person) else emptySet()))
                        f.cached(payload)
                        DiscoveryVerificationWorker(f.props, f.repository, OrcidVerificationEvidenceClient(f.props, http, f.clock),
                            ApolloPeopleSearchClient(f.props, http, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
                            DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock).useWorker { it.process(f.claim()) }
                        return f.item(row.id)
                    }
                    val first = run("reuse-first")
                    val source = f.repository.findAttempts(first.id).single()
                    val second = run("reuse-second")
                    val secondPayload = f.mapper.readValue<VerificationWorkerEvidence>(second.evidenceJson!!)
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, second.outcome)
                    assertEquals(0, second.requestCount); assertTrue(f.repository.findAttempts(second.id).isEmpty())
                    assertEquals(listOf(WorkerDetailReuse(source.id, first.id, "shared-person", source.responseSha256!!)),
                        secondPayload.diagnostics!!.detailReuses)
                    assertEquals(source, f.repository.findAttempts(first.id).single())
                    assertNotNull(second.admissionRevision)
                    assertEquals(VerificationProjection.PROJECTED, second.projectionState)
                    assertEquals(listOf("shared-person"), calls)
                    val otherRun = run("reuse-other-run", owner = 2002)
                    assertEquals(1, otherRun.requestCount)
                    val otherDiagnostics = f.mapper.readValue<VerificationWorkerEvidence>(otherRun.evidenceJson!!).diagnostics!!
                    assertTrue(otherDiagnostics.detailReuses.isEmpty())
                    assertEquals("RECORDED", otherDiagnostics.decision!!.traceOrigin)
                    assertEquals(listOf("shared-person", "shared-person"), calls)
                    val conflict = run("reuse-conflict", conflict = true)
                    assertEquals(VerificationOutcome.PENDING, conflict.outcome)
                    assertTrue(conflict.reasonsJson!!.contains("APOLLO_ID_CONFLICT"))
                    assertEquals(0, conflict.requestCount)
                    fail = true
                    assertEquals(VerificationState.ERROR, run("failed-detail-1", person = "failed-person").state)
                    assertEquals(VerificationState.ERROR, run("failed-detail-2", person = "failed-person").state)
                    assertEquals(2, calls.count { it == "failed-person" })
                    val api = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(second.id)
                    assertTrue(api.attempts.isEmpty()); assertEquals(secondPayload.diagnostics, api.diagnostics)
                    val oversized = secondPayload.copy(diagnostics = WorkerDiagnostics(
                        queries = (0..39).map { WorkerQueryDiagnostic(it, "a".repeat(64), "VARIANT") },
                        detailReuses = List(205) { secondPayload.diagnostics!!.detailReuses.single() }))
                    val oversizedJson = f.mapper.writeValueAsString(oversized)
                    jdbc.update("UPDATE discovery_verification_item SET evidence_json=? WHERE id=?", oversizedJson, second.id)
                    val bounded = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(second.id).diagnostics!!
                    assertEquals(32, bounded.queries.size); assertEquals(40, bounded.totalQueryCount)
                    assertEquals(200, bounded.detailReuses.size); assertEquals(205, bounded.totalReused)
                    assertTrue(bounded.truncated)
                    assertEquals(oversizedJson, f.item(second.id).evidenceJson)
                    server.verify()
                }
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val row = f.seed("bounded-query-trace")
                    val queries = (0..33).map { ApolloSearchQuery("Ada query $it") }
                    f.apolloBody = """{"people":[],"total_entries":0}"""
                    f.cached(VerificationWorkerEvidence(search = ApolloSearchEvidence(matchingVersion = 2),
                        progress = VerificationProgress(stage = "APOLLO_SEARCH"), queries = queries))
                    f.worker().process(f.claim())
                    val payload = f.mapper.readValue<VerificationWorkerEvidence>(f.item(row.id).evidenceJson!!)
                    assertEquals(queries, payload.queries)
                    assertEquals(34, f.apolloRequests.size); assertEquals(34, payload.progress.finishedQueryKeys.size)
                    assertTrue(payload.search!!.complete); assertTrue(payload.search!!.broadComplete)
                    assertEquals(32, payload.diagnostics!!.queries.size)
                    assertEquals(34, payload.diagnostics!!.totalQueryCount); assertTrue(payload.diagnostics!!.truncated)
                }
            }
    }
}

/** Independent disposable runtime entry. It invokes production services, never a JUnit method. */
object RecheckPipelineRuntimeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        runChild07Pipeline()
        println("CHILD07_ACADEMIC ${academicDispatchCancellation(false)}")
        println("CHILD07_ACADEMIC ${academicDispatchCancellation(true)}")
    }
}

private class Child07Mysql : org.testcontainers.containers.MySQLContainer<Child07Mysql>("mysql:8.0.36")

internal fun runChild07Pipeline() {
    check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Real isolated MySQL required, no skip" }
    Child07Mysql().withDatabaseName("pipeline_07").withUsername("test").withPassword("test")
        .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
        mysql.start()
        val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
        val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
        val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
        val tx = org.springframework.transaction.support.TransactionTemplate(manager)
        val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true, apolloApiKey = "fixture-only",
            mode = VerificationMode.AUDIT, requestsPerMinute = 1000)
        VerificationFlowFixture(jdbc, manager, props).use { f ->
            com.weibo.talentintroduction.discovery.archive.ArchiveMysqlFixture.clean()
            try {
                com.weibo.talentintroduction.discovery.archive.ArchiveFixture(realDisk = true).use { archive ->
                    archive.enable()
                    val bridge = PaperBridgeFixture(archive)
                    val source = bridge.paper("child07-paper", 9001,
                        value = "Ada Lee: ada@example.invalid\n\nBiographies\n\nAda Lee received her PhD in 2020. Ada Lee was born on 1987-10-25.",
                        people = listOf(PaperAuthor("Ada", "Lee", "0000-0002-1825-0097", null)))
                    val sourceBefore = archive.repository.record(source.archiveRecordId)!!
                    f.clock.now = java.time.Instant.parse("2026-10-24T00:00:00Z")
                    f.apolloBody = """{"people":[{"id":"person-1","name":"Ada Lee","first_name":"Ada","last_name":"Lee","title":"Professor of Physics","organization":{"name":"Example University"}}],"total_entries":1}"""
                    fun <T : Any> proxy(target: T): T {
                        val factory = org.springframework.aop.framework.ProxyFactory(target)
                        factory.isProxyTargetClass = true
                        factory.addAdvice(org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                            org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()))
                        @Suppress("UNCHECKED_CAST") return factory.proxy as T
                    }
                    val trace = proxy(com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository(jdbc))
                    val commits = proxy(DiscoveryVerificationCommitService(f.repository, f.admissions, f.review, f.policy,
                        f.writer, jdbc, f.clock, trace))
                    val wire = java.util.concurrent.CopyOnWriteArrayList<String>()
                    val onRequest = java.util.concurrent.atomic.AtomicReference<((String) -> Unit)?>(null)
                    var searchStatus = 200
                    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
                    val base = "http://127.0.0.1:${server.address.port}"
                    server.createContext("/") { exchange ->
                        val path = exchange.requestURI.path
                        wire += path
                        onRequest.get()?.invoke(path)
                        val body = when {
                            path == "/authors" -> AcademicClientFixture.success.replace("\"counts_by_year\"", "\"works_api_url\":\"$base/works\",\"counts_by_year\"")
                            path == "/works" -> """{"results":[]}"""
                            path.contains("mixed_people/api_search") -> f.apolloBody
                            path.endsWith("/employments") -> """{"affiliation-group":[]}"""
                            path.endsWith("/educations") || path.endsWith("/person") -> "{}"
                            else -> error("Unexpected fixture HTTP $path")
                        }.toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        if (path.contains("mixed_people") && searchStatus == 429) exchange.responseHeaders.add("Retry-After", "60")
                        exchange.sendResponseHeaders(if (path.contains("mixed_people")) searchStatus else 200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }; exchange.close()
                    }
                    server.start()
                    val transport = org.springframework.web.client.RestTemplate(object : org.springframework.http.client.ClientHttpRequestFactory {
                        val delegate = org.springframework.http.client.SimpleClientHttpRequestFactory().apply { setConnectTimeout(2000); setReadTimeout(5000) }
                        override fun createRequest(uri: java.net.URI, method: org.springframework.http.HttpMethod): org.springframework.http.client.ClientHttpRequest =
                            delegate.createRequest(java.net.URI("$base${uri.rawPath}${uri.rawQuery?.let { "?$it" }.orEmpty()}"), method)
                    })
                    val verificationTransport = verificationRestTemplate(props).apply { requestFactory = transport.requestFactory }
                    val academicProperties = com.weibo.talentintroduction.config.OpenAlexProperties(enabled = true, baseUrl = base,
                        requestDelayMs = 0, enrichmentDelayMs = 0, maxRequestsPerSecond = 100.0, fetchWorksEnabled = true, fetchPatentsEnabled = true)
                    val budgetTime = object : com.weibo.talentintroduction.config.PolicyTimeSource {
                        var current = f.clock.instant()
                        override fun now() = current
                        override fun sleep(ms: Long) { current = current.plusMillis(ms) }
                    }
                    val store = com.weibo.talentintroduction.config.InMemoryOpenAlexBudgetStore().also {
                        it.reconcile(academicProperties.accountScope, f.clock.instant().plusSeconds(86400), 10000, 10000, 10000, f.clock.instant())
                    }
                    val academicClient = OpenAlexDataSource(transport, academicProperties, Mockito.mock(EuropePmcDataSource::class.java),
                        Mockito.mock(PdfEmailExtractor::class.java), Mockito.mock(UnpaywallClient::class.java),
                        com.weibo.talentintroduction.config.OpenAlexRequestPolicy(academicProperties, budgetTime, store))
                    val academic = academicService(academicClient, academicProperties)
                    val workers = mutableListOf<DiscoveryVerificationWorker>()
                    fun worker() = DiscoveryVerificationWorker(props, f.repository, OrcidVerificationEvidenceClient(props, verificationTransport, f.clock),
                        ApolloPeopleSearchClient(props, verificationTransport, f.clock), QualificationEvidenceParser(f.clock), f.rules, commits,
                        DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock, trace, bridge.bridge, academic)
                        .also { workers += it }
                    fun task(id: Long) = jdbc.update("INSERT INTO task_execution(id,task_type,trigger_type,status,started_at) VALUES (?,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)", id, f.clock.instant().verificationUtc())
                    fun raw(doc: String, valid: Boolean = true): com.weibo.talentintroduction.expert.domain.ExpertProfile {
                        val p = ruleSnapshot().profile.copy(orcidId = "EMAIL-$doc", esDocId = "EMAIL-$doc", tags = listOf("discovered"),
                            researchFieldIds = listOf("22"), lastPublicationYear = 2026,
                            externalIds = """{"orcid":"0000-0002-1825-0097"}""")
                        return if (valid) p else p.copy(identityVerification = null)
                    }
                    fun putRaw(p: com.weibo.talentintroduction.expert.domain.ExpertProfile) {
                        @Suppress("UNCHECKED_CAST")
                        val fields = f.mapper.convertValue(p, Map::class.java) as Map<String, Any?>
                        f.raws[p.esDocId!!] = fields + ("externalIds" to p.externalIds?.let { f.mapper.readTree(it) })
                    }
                    fun frozen(p: com.weibo.talentintroduction.expert.domain.ExpertProfile) =
                        DiscoveryVerificationAdmissionGate.snapshot(p.esDocId!!, p, f.policy.evaluate(p).configSnapshot)
                    fun cached(snapshot: VerificationSnapshot, rawDocuments: Boolean = true): VerificationWorkerEvidence {
                        val completions = f.selfDocuments().map { it.copy(document = it.document?.copy(fetchedAt = java.time.Instant.parse("2024-01-01T00:00:00Z"))) }
                        val facts = QualificationEvidenceParser(f.clock).rebuild(snapshot, completions.mapNotNull { it.document }, emptyList())
                        return VerificationWorkerEvidence(evidence = facts, orcidCompleted = if (rawDocuments) completions else emptyList(),
                            progress = VerificationProgress("APOLLO_SEARCH", 7, 3), queries = listOf(ApolloSearchQuery("DO NOT COPY")), ruleVersion = 1)
                    }
                    var nextRun = 9100L
                    fun recheck(p: com.weibo.talentintroduction.expert.domain.ExpertProfile, mode: VerificationMode = VerificationMode.AUDIT,
                        evidenceMode: RecheckEvidenceMode = RecheckEvidenceMode.REUSE, sources: List<PaperSourceRef> = emptyList(),
                        evidence: VerificationWorkerEvidence? = null): VerificationItem {
                        val id = nextRun++; task(id); putRaw(p)
                        val settings = """{"ruleVersion":3,"apolloMatchingVersion":2,"institutionAliasVersion":2,"apolloAccess":{"schemaVersion":1,"settingRevision":1,"searchEnabled":true,"detailEnabled":false,"source":"SAVED"}}"""
                        f.repository.createRecheckRun(id, mode, true, settings,
                            RecheckSpec("fixture", "child07-$id", "{}", evidenceMode, f.clock.instant(), "child07", "3"), f.clock.instant())
                        val snapshot = frozen(p)
                        val current = f.admissions.findAdmission(p.esDocId!!)
                        val root = f.mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(snapshot)
                        root.set<com.fasterxml.jackson.databind.JsonNode>("reviewInput", f.mapper.valueToTree(
                            VerificationReviewInput(current?.revision, current?.decision, sources, evidence,
                                evidence?.let { com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository.replayHash(f.mapper.writeValueAsString(it)) })))
                        val item = f.repository.reserveRecheckItem(VerificationReservation(id, p.esDocId!!, snapshot.identityHash,
                            mode.policyVersion, "OPENALEX", "child07-paper", root.toString(), snapshot.factFingerprint), f.clock.instant())
                        check(item.state == VerificationState.RESERVED && !trace.read(item.id).recorded)
                        val preparing = f.repository.findRun(id)!!
                        check(f.repository.compareAndSetRecheckControl(id, preparing.revision,
                            RecheckControl(RecheckPhase.READY, f.repository.recheckScopeHash(id)), f.clock.instant()))
                        check(!trace.read(item.id).recorded && f.repository.claimDue(f.clock.instant(), 120) == null)
                        val ready = f.repository.findRun(id)!!
                        check(f.repository.compareAndSetRecheckControl(id, ready.revision,
                            f.repository.recheck(ready).control.copy(phase = RecheckPhase.RUNNING), f.clock.instant()))
                        return f.repository.activateItem(item.id, 1, f.clock.instant())
                    }
                    fun execute(item: VerificationItem): VerificationItem {
                        val lease = f.claim(); check(lease.id == item.id); worker().process(lease)
                        val result = f.item(item.id)
                        check(result.state in setOf(VerificationState.COMPLETED, VerificationState.ERROR, VerificationState.CANCELLED, VerificationState.RETRY_WAIT)) { "Unsettled ${result.reasonsJson}" }
                        if (result.state in setOf(VerificationState.COMPLETED, VerificationState.ERROR)) {
                            val latest = trace.read(item.id).steps.groupBy { it.stepNo }.mapValues { it.value.last() }
                            check(latest.keys == (1..8).toSet() && latest.values.all { it.state.terminal }) { latest.toString() }
                        }
                        return result
                    }
                    fun cancel(item: VerificationItem) {
                        tx.executeWithoutResult {
                            jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run WHERE execution_id=? FOR UPDATE", Long::class.java, item.ownerExecutionId)
                            val run = f.repository.findRun(item.ownerExecutionId)!!
                            check(f.repository.compareAndSetRecheckControl(run.executionId, run.revision,
                                f.repository.recheck(run).control.copy(phase = RecheckPhase.CANCELLING), f.clock.instant()))
                            f.repository.cancelRecheckItem(item.id, f.clock.instant())
                        }
                    }
                    try {
                        task(9001); f.saveApolloSettings(true, false)
                        val registration = DiscoveryVerificationRegistrationService(props, f.repository, f.writer, f.policy, f.admissions,
                            jdbc, tx, f.apolloSettings, f.clock, traces = trace, papers = bridge.bridge)
                        val ordinaryRun = registration.begin(9001, PaperSearchCriteria()).run!!
                        val reserved = registration.reserve(DiscoveryVerificationExecution(9001, ordinaryRun), "EMAIL-ordinary-paper", raw("ordinary-paper"),
                            "OPENALEX", "child07-paper")
                        check(!reserved.protected && !trace.read(reserved.item!!.id).recorded)
                        putRaw(reserved.preparedProfile!!); registration.activate(reserved)
                        val initialWire = wire.size
                        val ordinary = execute(f.item(reserved.item!!.id))
                        check(ordinary.outcome == VerificationOutcome.DIRECT_CLEAR && wire.size == initialWire) {
                            "ordinary state=${ordinary.state} outcome=${ordinary.outcome} reasons=${ordinary.reasonsJson} wire=$wire evidence=${ordinary.evidenceJson}"
                        }
                        check(f.admissions.findAdmission(ordinary.expertDocId) == null && f.candidates.isEmpty())
                        val ordinaryPayload = f.mapper.readValue<VerificationWorkerEvidence>(ordinary.evidenceJson!!)
                        check(ordinaryPayload.executionContext!!.paper!!.sources.single().source == source)
                        check(trace.read(ordinary.id).steps.last { it.stepNo == 6 }.reasonJson!!.contains("DIRECT_SUFFICIENT"))

                        val reuseProfile = raw("reuse-direct")
                        val baseline = cached(frozen(reuseProfile))
                        val reuse = execute(recheck(reuseProfile, evidence = baseline))
                        val reused = f.mapper.readValue<VerificationWorkerEvidence>(reuse.evidenceJson!!)
                        check(reuse.outcome == VerificationOutcome.DIRECT_CLEAR && reuse.requestCount == 0 && wire.size == initialWire)
                        check(reused.queries == null && reused.progress.queryIndex == 0 && reused.progress.page == 1)
                        check(reused.evidence.all { it.fetchedAt == java.time.Instant.parse("2024-01-01T00:00:00Z") })
                        check(reused.executionContext!!.cacheReasons.contains("CACHE_REUSED"))
                        check(trace.read(reuse.id).steps.last { it.stepNo == 2 }.state == VerificationStepState.INSUFFICIENT)
                        check(f.admissions.findAdmission(reuseProfile.esDocId!!) == null)
                        val notReparsedProfile = raw("reuse-derived")
                        val notReparsed = execute(recheck(notReparsedProfile, evidence = cached(frozen(notReparsedProfile), false)))
                        check(f.mapper.readValue<VerificationWorkerEvidence>(notReparsed.evidenceJson!!).executionContext!!.cacheReasons.contains("ORCID_NOT_REPARSED"))
                        check(wire.size == initialWire)
                        val noIdentity = execute(recheck(raw("no-identity", false), sources = listOf(source)))
                        check(trace.read(noIdentity.id).blockingStep == 3 && wire.size == initialWire)
                        check(trace.read(noIdentity.id).steps.last { it.stepNo == 4 }.state == VerificationStepState.SKIPPED)
                        val conflictProfile = raw("qualification-conflict")
                        val conflictingCache = cached(frozen(conflictProfile), false).let { cache ->
                            cache.copy(evidence = cache.evidence.map { it.copy(conflicts = listOf("PROVIDER_FACT_CONFLICT")) })
                        }
                        val conflict = execute(recheck(conflictProfile, evidence = conflictingCache))
                        check(conflict.qualification == VerificationQualification.CONFLICT && trace.read(conflict.id).blockingStep == 5)
                        check(wire.size == initialWire)

                        val apollo = execute(recheck(raw("refresh-free"), evidenceMode = RecheckEvidenceMode.REFRESH))
                        check(apollo.outcome == VerificationOutcome.APOLLO_CLEAR && apollo.admissionRevision == null) {
                            "refresh state=${apollo.state} outcome=${apollo.outcome} reasons=${apollo.reasonsJson} wire=$wire"
                        }
                        check(wire.none { it.startsWith("/api/v1/people/") })
                        val allBeforeAudit = jdbc.queryForObject("SELECT COUNT(*) FROM expert_discovery_admission", Long::class.java)
                        check(allBeforeAudit == 0L && f.candidates.isEmpty() && f.applications.isEmpty())
                        val payload = f.mapper.readValue<VerificationWorkerEvidence>(apollo.evidenceJson!!)
                        val recomputed = f.rules.evaluate(f.mapper.readValue<VerificationSnapshot>(apollo.snapshotJson), payload.evidence, payload.search,
                            institutionNormalization = payload.institutionNormalization, ruleVersion = payload.ruleVersion, evaluatedAt = payload.executionContext!!.evaluationAt)
                        check(listOf(recomputed.outcome, recomputed.qualification, recomputed.route, recomputed.matchedPersonId) ==
                            listOf(apollo.outcome, apollo.qualification, apollo.route, apollo.apolloPersonId))
                        val cachedSearchWire = wire.size
                        val reusedSearch = execute(recheck(raw("refresh-free"), evidence = payload))
                        val reusedSearchPayload = f.mapper.readValue<VerificationWorkerEvidence>(reusedSearch.evidenceJson!!)
                        check(listOf(reusedSearch.outcome, reusedSearch.qualification, reusedSearch.route, reusedSearch.apolloPersonId) ==
                            listOf(apollo.outcome, apollo.qualification, apollo.route, apollo.apolloPersonId))
                        check(reusedSearchPayload.search == payload.search && reusedSearchPayload.queries == null)
                        check(reusedSearch.requestCount == 0 && wire.size == cachedSearchWire)

                        val syncProfile = raw("sync-only")
                        f.failProjection = true
                        val sync = execute(recheck(syncProfile, VerificationMode.ENFORCE, evidence = cached(frozen(syncProfile))))
                        check(sync.admissionRevision != null && sync.projectionState == VerificationProjection.ERROR)
                        val providerBeforeSync = wire.size; cancel(sync); f.failProjection = false; commits.project(f.item(sync.id))
                        val projected = f.item(sync.id)
                        check(projected.admissionRevision == sync.admissionRevision && projected.projectionState == VerificationProjection.PROJECTED)
                        check(wire.size == providerBeforeSync && trace.read(sync.id).steps.count { it.stepNo == 8 } == 3)
                        check(f.policy.evaluate(projected.expertDocId, syncProfile).eligible)
                        val unsuccessful = execute(recheck(syncProfile, VerificationMode.ENFORCE))
                        check(unsuccessful.admissionRevision == null && f.admissions.findAdmission(syncProfile.esDocId!!)!!.revision == sync.admissionRevision)
                        check(f.policy.evaluate(projected.expertDocId, syncProfile).eligible)

                        val changedProfile = raw("changed-admission")
                        val changed = recheck(changedProfile, VerificationMode.ENFORCE, evidence = cached(frozen(changedProfile)))
                        f.admissions.initializeAdmission(changed.expertDocId, changed.identityHash, "NEEDS_REVIEW", changed.policyVersion, f.clock.instant().verificationUtc())
                        val rejected = execute(changed)
                        check(rejected.reasonsJson!!.contains("ADMISSION_CHANGED") && rejected.admissionRevision == null)
                        val staleProfile = raw("stale-facts")
                        val stale = recheck(staleProfile, VerificationMode.ENFORCE, evidence = cached(frozen(staleProfile)))
                        putRaw(staleProfile.copy(institution = "Changed University"))
                        check(execute(stale).reasonsJson!!.contains("STALE_INPUT"))

                        val late = recheck(raw("late-apollo"), VerificationMode.ENFORCE, RecheckEvidenceMode.REFRESH)
                        onRequest.set { path -> if (path.contains("mixed_people")) cancel(late) }
                        val cancelled = execute(late); onRequest.set(null)
                        check(cancelled.state == VerificationState.CANCELLED && cancelled.admissionRevision == null)
                        val attempt = f.repository.findAttempts(late.id).last()
                        check(attempt.completedAt != null && attempt.httpStatus == 200 && attempt.resultCode == "OK")
                        check(f.admissions.findAdmission(late.expertDocId) == null)
                        check(!f.repository.completeDispatchedDiagnostic(late.id, "wrong", cancelled.generation, attempt.id, attempt.requestKey,
                            200, "OK", "{}", null, f.clock.instant()))
                        val academicCancel = recheck(raw("cancel-academic"), VerificationMode.ENFORCE, RecheckEvidenceMode.REFRESH)
                        val beforeAcademicCancel = wire.size
                        onRequest.set { path -> if (path == "/authors") cancel(academicCancel) }
                        check(execute(academicCancel).state == VerificationState.CANCELLED); onRequest.set(null)
                        check(wire.drop(beforeAcademicCancel) == listOf("/authors"))
                        check(f.repository.findAttempts(academicCancel.id).isEmpty())

                        searchStatus = 429
                        val waiting = execute(recheck(raw("waiting-429"), evidenceMode = RecheckEvidenceMode.REFRESH))
                        check(waiting.state == VerificationState.RETRY_WAIT && waiting.transientAttempts == 0) {
                            "waiting state=${waiting.state} failures=${waiting.transientAttempts} reasons=${waiting.reasonsJson}"
                        }
                        check(f.repository.findAttempts(waiting.id).last().httpStatus == 429)
                        check(trace.read(waiting.id).steps.last { it.stepNo == 6 }.state == VerificationStepState.INSUFFICIENT)
                        cancel(waiting); searchStatus = 200

                        // Both lock orders are exercised with separate JDBC connections and a real shared run lock.
                        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
                        try {
                            for (cancelWins in listOf(true, false)) {
                                val p = raw("race-$cancelWins")
                                val item = recheck(p, VerificationMode.ENFORCE, evidence = cached(frozen(p)))
                                val lease = f.claim()
                                val evidence = cached(frozen(p)).let { it.copy(progress = VerificationProgress(), ruleVersion = 3,
                                    executionContext = VerificationExecutionContext(f.clock.instant())) }
                                val decision = f.rules.evaluate(frozen(p), evidence.evidence, ruleVersion = 3, evaluatedAt = f.clock.instant())
                                val proposed = VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification, decision.route,
                                    f.mapper.writeValueAsString(evidence), f.mapper.writeValueAsString(decision.reasonCodes))
                                val prepared = commits.prepare(lease)
                                val started = java.util.concurrent.CountDownLatch(1)
                                val contender = tx.execute {
                                    jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run WHERE execution_id=? FOR UPDATE", Long::class.java, item.ownerExecutionId)
                                    val future = if (cancelWins) pool.submit(java.util.concurrent.Callable { started.countDown(); commits.commit(lease, proposed, prepared) })
                                        else pool.submit(java.util.concurrent.Callable { started.countDown(); cancel(item); null })
                                    check(started.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                    if (cancelWins) cancel(item) else check(commits.commit(lease, proposed, prepared)!!.admissionRevision != null)
                                    future
                                }!!
                                contender.get(30, java.util.concurrent.TimeUnit.SECONDS)
                                val result = f.item(item.id)
                                check(if (cancelWins) result.state == VerificationState.CANCELLED && result.admissionRevision == null
                                    else result.state == VerificationState.COMPLETED && result.admissionRevision != null)
                            }
                        } finally { pool.shutdownNow() }
                        check(archive.repository.record(source.archiveRecordId) == sourceBefore)
                        check(archive.repository.attempt(source.attemptId)!!.blobSha256 == source.bodySha256)
                        check(wire.none { it.startsWith("/api/v1/people/") })
                        println("CHILD07_RUNTIME PASS actualRegistrationWorkerRulesGateCommitTrace=true realMySQL=true ordinaryPaper=DIRECT_CLEAR eightSteps=true AUDIT_businessWrites=0 REUSE_HTTP=0 originalFetchedAt=true frozenParity=true freeDetailHTTP=0 lateDiagnostic=once academicCancelWire=1 cancelled429Failures=0 cancelCommitBothOrders=true admittedProjectionRetryProviderHTTP=0 projectionAttempts=3 archiveFirstSHA=unchanged")
                    } finally { workers.forEach { it.close() }; server.stop(0) }
                }
            } finally { com.weibo.talentintroduction.discovery.archive.ArchiveMysqlFixture.stop() }
        }
    }
}
