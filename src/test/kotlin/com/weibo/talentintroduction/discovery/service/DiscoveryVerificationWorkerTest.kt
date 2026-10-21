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
}
