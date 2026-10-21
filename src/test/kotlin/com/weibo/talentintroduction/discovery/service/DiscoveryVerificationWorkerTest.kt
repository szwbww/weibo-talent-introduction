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
    private fun worker(props: DiscoveryVerificationProperties, repo: DiscoveryVerificationRepository,
        factory: DefaultListableBeanFactory = DefaultListableBeanFactory()): DiscoveryVerificationWorker =
        DiscoveryVerificationWorker(props, repo, Mockito.mock(OrcidVerificationEvidenceClient::class.java),
            Mockito.mock(ApolloPeopleSearchClient::class.java), Mockito.mock(QualificationEvidenceParser::class.java),
            DiscoveryVerificationRules(ruleClock), Mockito.mock(DiscoveryVerificationCommitService::class.java),
            factory.getBeanProvider(RegistrationRecovery::class.java), ruleClock)

    @Test fun `runtime version two registration worker SQL gate query smoke and frozen legacy runs`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        DiagnosticMysql().withDatabaseName("identity_v2").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                VerificationFlowFixture(jdbc, manager).use { f ->
                    jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (2001,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)",
                        f.clock.instant().verificationUtc())
                    val registration = DiscoveryVerificationRegistrationService(DiscoveryVerificationProperties(enabled = true, apolloApiKey = "fixture-key"), f.repository, f.writer, f.policy,
                        f.admissions, jdbc, org.springframework.transaction.support.TransactionTemplate(manager), f.clock)
                    val context = registration.begin(2001, PaperSearchCriteria(apolloAssist = true))
                    assertEquals(2, f.mapper.readTree(context.run!!.settingsJson).path("ruleVersion").intValue())
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
                    assertEquals(2, payload.ruleVersion)
                    assertEquals(2, payload.search!!.institutionAliasVersion)
                    assertEquals("Assistenzprofessor", payload.search!!.candidates.single().title)
                    assertEquals(1L, f.autoCount())
                    val snapshot = f.mapper.readValue<VerificationSnapshot>(done.snapshotJson)
                    assertTrue(f.policy.evaluate(done.expertDocId, snapshot.profile).eligible)
                    val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(done.id)
                    assertEquals(VerificationOutcome.APOLLO_CLEAR, detail.outcome)
                    assertEquals("Assistenzprofessor", detail.matchedCandidate!!.title)
                    assertEquals(listOf("completedDoctorate", "reliableAge"), detail.record.missingFields)
                    assertEquals(context.run!!.settingsJson, registration.begin(2001, PaperSearchCriteria(apolloAssist = true)).run!!.settingsJson)
                    println("IDENTITY_V2_SMOKE run=2001 workerRule=2 alias=2 sql=${done.outcome} gate=eligible projection=${done.projectionState} admission=${done.admissionRevision} requests=${done.requestCount}")
                }
                listOf("discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
                    "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
                // New payload on a pre-existing run without settings must retain the old default, not v2.
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val row = f.seed("legacy-no-settings")
                    f.worker().process(f.claim())
                    assertEquals(1, f.mapper.readValue<VerificationWorkerEvidence>(f.item(row.id).evidenceJson!!).ruleVersion)
                }
                listOf("discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
                    "expert_discovery_admission").forEach { jdbc.update("DELETE FROM $it") }
                for (version in listOf(0, 1, 2, 3)) {
                    VerificationFlowFixture(jdbc, manager).use { f ->
                        val row = f.seed("frozen-$version")
                        jdbc.update("UPDATE discovery_verification_run SET settings_json=? WHERE execution_id=2001",
                            """{"ruleVersion":2,"apolloMatchingVersion":2,"institutionAliasVersion":2}""")
                        val candidate = ApolloCandidate("person-frozen", "Ada", "Lee", null, "Ada Lee", "Assistenzprofessor",
                            VerificationInstitution("Other University"), headline = "Assistant Professor at Other University")
                        f.cached(VerificationWorkerEvidence(ruleVersion = version, progress = VerificationProgress(stage = "DECIDE"),
                            search = ApolloSearchEvidence(listOf(candidate), true, true, setOf("Ada Lee"), matchingVersion = 2,
                                detailsComplete = true, institutionAliasVersion = 1)))
                        f.worker().process(f.claim())
                        val done = f.item(row.id)
                        if (version == 3) assertEquals(VerificationState.ERROR, done.state)
                        else {
                            assertEquals(version, f.mapper.readValue<VerificationWorkerEvidence>(done.evidenceJson!!).ruleVersion)
                            assertEquals(if (version == 2) VerificationOutcome.APOLLO_CLEAR else VerificationOutcome.PENDING, done.outcome)
                        }
                        assertTrue(f.apolloRequests.isEmpty())
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
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                val cases = listOf(
                    """{"message":"insufficient credits token=private-token alice@example.invalid +1 (415) 555-0123","people":[{"name":"private-person"}]}""" to "APOLLO_CREDITS_EXHAUSTED",
                    """{"message":"invalid parameter"}""" to "REQUEST_INVALID",
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
                        listOf("private-token", "alice@example.invalid", "415", "private-person").forEach { assertFalse(json.contains(it), it) }
                        val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
                        val detail = query.item(row.id)
                        assertEquals(expected, detail.attempts.single().diagnostic!!.classification)
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
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
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
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
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
                    assertNull(f.mapper.readValue<VerificationWorkerEvidence>(otherRun.evidenceJson!!).diagnostics)
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
