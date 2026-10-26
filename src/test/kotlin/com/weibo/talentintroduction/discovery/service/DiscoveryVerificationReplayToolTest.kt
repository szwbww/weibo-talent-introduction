package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class DiscoveryVerificationReplayToolTest {
    @TempDir lateinit var root: Path
    private val tool = DiscoveryVerificationReplayTool
    @org.junit.jupiter.api.BeforeEach fun canonicalRoot() { root = root.toRealPath() }
    @Test fun `frozen rules uses saved facts never reparses and keeps protections separate from pure rule gains`() {
        val f = AdmissionGateFixture()
        val candidate = ApolloCandidate("synthetic", "Ada", "Lee", null, "Ada Lee", "Assistenzprofessor",
            VerificationInstitution("Other University"), headline = "Assistant Professor at Other University")
        val payload = VerificationWorkerEvidence(ruleVersion = 1,
            search = ApolloSearchEvidence(listOf(candidate), true, true, setOf("Ada Lee"), matchingVersion = 2,
                detailsComplete = true, institutionAliasVersion = 1),
            // Malformed cached parser document is intentionally not consumed by frozen-rules.
            orcidCompleted = listOf(OrcidEndpointCompletion("person", OrcidEvidenceDocument("person",
                tool.mapper.readTree("""{"name":{"given-names":{"value":"Contradictory"}}}"""), "d".repeat(64), ruleClock.instant()))))
        val items = listOf("RND_OUT_OF_SCOPE", "IDENTITY_CHANGED", "REVIEW_PROTECTED").mapIndexed { index, reason ->
            f.item(facts = emptyList()).copy(id = index + 1L, outcome = VerificationOutcome.PENDING,
                reasonsJson = tool.mapper.writeValueAsString(listOf(reason)),
                evidenceJson = tool.mapper.writeValueAsString(payload))
        }
        val run = VerificationRun(items.first().ownerExecutionId, VerificationMode.ENFORCE, VerificationMode.ENFORCE.policyVersion, true, "{}",
            true, 1, ruleClock.instant(), ruleClock.instant())
        val attempts = items.associate { item -> item.id to listOf(VerificationAttempt(item.id, item.id,
            "attempt-${item.id}", VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1, 1,
            ruleClock.instant(), ruleClock.instant(), 422, "REQUEST_INVALID", "{}", null, null)) }
        val before = DiscoveryVerificationReplayTool.Before(executionId = run.executionId, itemIds = items.map { it.id },
            capturedAt = ruleClock.instant(), run = run, items = items, attempts = attempts, admissions = emptyMap())
        val bytes = tool.mapper.writeValueAsBytes(before)
        val cp = System.getProperty("java.class.path")
        val hashes = tool.hashes(cp)
        assertTrue(hashes.containsKey("DiscoveryInstitutionNormalizer"))
        val manifest = DiscoveryVerificationReplayTool.Manifest(executionId = run.executionId,
            sortedItemIds = items.map { it.id }, capturedAt = ruleClock.instant(), evaluatedAt = ruleClock.instant(),
            beforeSha256 = tool.sha(bytes), baselineClassHashes = mapOf("old" to hashes, "fixed" to hashes))
        val directory = root.resolve("frozen")
        Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        tool.atomicWrite(directory.resolve("before.json"), bytes)
        tool.atomicWrite(directory.resolve("manifest.json"), tool.mapper.writeValueAsBytes(manifest))
        val frozen = DiscoveryVerificationReplayTool.Frozen(directory, manifest, before)
        val classpaths = mapOf("old" to cp, "fixed" to cp)
        fun isolatedOutput(): com.fasterxml.jackson.databind.JsonNode {
            val child = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", cp, DiscoveryVerificationReplayTool.FrozenRulesEvaluator::class.java.name,
                directory.resolve("before.json").toString(), ruleClock.instant().toString(), "fixed", "2", "2")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val output = child.inputStream.readBytes()
            assertEquals(0, child.waitFor())
            return tool.mapper.readTree(output)
        }
        assertEquals(isolatedOutput(), isolatedOutput())
        val first = tool.evaluateFrozenRules(frozen, classpaths, 2, 2)
        assertEquals(first, tool.evaluateFrozenRules(frozen, classpaths, 2, 2))
        assertEquals(0, first.path("businessWrites").asInt())
        assertEquals(0, first.path("admissionWrites").asInt())
        first.path("rows").forEach { row ->
            assertEquals("PENDING", row.path("oldStoredOutcome").asText())
            assertEquals("PENDING", row.path("oldRecomputedOutcome").asText())
            assertEquals("APOLLO_CLEAR", row.path("newRecomputedOutcome").asText())
            assertEquals("NOT_EVALUATED", row.path("gateResult").asText())
            assertEquals("RECORDED_PROTECTION", row.path("protectionStatus").asText())
            assertEquals("NOT_EVALUATED", row.path("currentCommitProtection").asText())
            assertEquals("NOT_RECORDED", row.path("errorBodyStatus").asText())
            assertTrue(row.path("storedTrace").isNull)
            assertEquals("RECOMPUTED", row.path("newTrace").path("traceOrigin").asText())
            assertEquals("RECOMPUTED", row.path("oldTrace").path("traceOrigin").asText())
            assertEquals(1, row.path("oldRuleVersion").intValue())
            assertEquals(2, row.path("newRuleVersion").intValue())
        }
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("before.json")))
        assertEquals(manifest, tool.mapper.readValue(Files.readAllBytes(directory.resolve("manifest.json")),
            DiscoveryVerificationReplayTool.Manifest::class.java))
        Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("frozen-rules-") }.forEach { path ->
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path))
            val envelope = tool.mapper.readTree(Files.readAllBytes(path))
            assertEquals(tool.sha(tool.mapper.writeValueAsBytes(envelope.path("report"))), envelope.path("reportSha256").asText())
            assertFalse(Files.readString(path).contains("Contradictory"))
            assertFalse(Files.readString(path).contains("api_key"))
        } }
        val changed = frozen.copy(manifest = manifest.copy(baselineClassHashes = mapOf("old" to emptyMap(), "fixed" to hashes)))
        assertThrows(IllegalArgumentException::class.java) { tool.evaluateFrozenRules(changed, classpaths, 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { tool.evaluateFrozenRules(frozen, classpaths, 1, 2) }
        listOf(arrayOf("evaluate", "--mode", "frozen-rules"),
            arrayOf("evaluate", "--mode", "unknown"),
            arrayOf("evaluate", "--mode", "frozen-rules", "--target-rule-version", "3", "--target-alias-version", "2"),
            arrayOf("evaluate", "--target-rule-version", "2")).forEach { args ->
            assertThrows(IllegalArgumentException::class.java) { tool.execute(args) }
        }
    }

    @Test fun `bounded explicit unique cohort and stage only transform`() {
        listOf(emptyList(), (1L..101L).toList(), listOf(1L, 1L), listOf(0L)).forEach { ids ->
            assertThrows(IllegalArgumentException::class.java) { tool.validateIds(ids) }
        }
        val original = """{"evidence":[],"search":{"complete":true},"orcidCompleted":[{"endpoint":"person"}],"progress":{"stage":"DONE","page":3},"extra":7}"""
        val expected = tool.mapper.readTree(original) as ObjectNode
        (expected.get("progress") as ObjectNode).put("stage", "DECIDE")
        assertEquals(expected, tool.mapper.readTree(DiscoveryVerificationRepository.replayEvidence(original)))
        assertThrows(Exception::class.java) { DiscoveryVerificationRepository.replayEvidence("broken") }
    }
    @Test fun `atomic restricted files cannot be replaced and symlinks are refused`() {
        val directory = root.resolve("private"); Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val file = directory.resolve("before.json")
        tool.atomicWrite(file, "{}".toByteArray())
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
        assertThrows(IllegalArgumentException::class.java) { tool.atomicWrite(file, "other".toByteArray()) }
        val link = root.resolve("link"); Files.createSymbolicLink(link, directory)
        assertThrows(IllegalArgumentException::class.java) { tool.atomicWrite(link.resolve("bad"), byteArrayOf()) }
        assertEquals("{}", Files.readString(file))
    }
    @Test fun `corrupt manifest and bad digest are rejected without exposing credentials`() {
        val directory = root.resolve("private"); Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val path = directory.resolve("manifest.json"); val invalid = "not-json".toByteArray(); tool.atomicWrite(path, invalid)
        assertThrows(Exception::class.java) { tool.load(path, tool.sha(invalid)) }
        assertThrows(IllegalArgumentException::class.java) { tool.load(path, "a".repeat(64)) }
        val source = Files.readString(Path.of("src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt"))
        assertFalse(source.contains("SpringApplication")); assertFalse(source.contains("@Service")); assertFalse(source.contains("@Scheduled"))
        assertFalse(source.contains("printStackTrace")); assertFalse(source.contains("println(e"))
    }
    @Test fun `offline stable entry preserves clock provider dates and source classifications`() {
        val snapshot = ruleSnapshot()
        val doc = OrcidEvidenceDocument("employments", tool.mapper.readTree("""{"affiliation-group":[{"summaries":[{"employment-summary":{"visibility":"public","organization":{"name":"Example University"},"role-title":"Professor","source":{"source-orcid":{"path":"0000-0002-1825-0097"}},"start-date":{"year":{"value":"2020"}},"end-date":null}}]}]}"""), "d".repeat(64), ruleClock.instant().minusSeconds(100))
        val payload = VerificationWorkerEvidence(search = ruleSearch(), orcidCompleted = listOf(OrcidEndpointCompletion("employments", doc)))
        val input = tool.mapper.valueToTree<ObjectNode>(mapOf("items" to listOf(mapOf("id" to 1,
            "snapshotJson" to tool.mapper.writeValueAsString(snapshot), "evidenceJson" to tool.mapper.writeValueAsString(payload), "reasonsJson" to "[\"NAME_CONFLICT\"]"))))
        val first = DiscoveryVerificationReplayTool.Evaluator.evaluateInput(input, ruleClock.instant(), "fixed")
        assertEquals(first, DiscoveryVerificationReplayTool.Evaluator.evaluateInput(input, ruleClock.instant(), "fixed"))
        assertEquals("APOLLO_UNCLEAR", first.single().outcome)
        assertEquals(mapOf("employment:SELF:CURRENT" to 1), first.single().factCounts)
        assertTrue(first.single().historicalNameConflict)
        assertEquals("PENDING", first.single().localOutcome)
        assertEquals(ruleClock.instant().minusSeconds(100), doc.fetchedAt)
    }
    @Test fun `standalone database failures redact URL username and password`() {
        val ids = root.resolve("ids.json"); Files.writeString(ids, "[1]")
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), DiscoveryVerificationReplayTool::class.java.name,
            "freeze", "--execution-id", "1", "--item-ids", ids.toString(), "--output", root.resolve("output").toString())
            .redirectErrorStream(true)
        val secret = "UNIQUE_PRIVATE_CREDENTIAL_5d243c"
        process.environment().putAll(mapOf("DB_URL" to "jdbc:missing:$secret", "DB_USERNAME" to secret, "DB_PASSWORD" to secret))
        val child = process.start()
        val output = child.inputStream.bufferedReader().readText()
        assertEquals(2, child.waitFor()); assertTrue(output.contains("REPLAY_FAILED")); assertFalse(output.contains(secret))
    }
    private class RecoveryMysql : org.testcontainers.containers.MySQLContainer<RecoveryMysql>("mysql:8.0.36")

    @Test fun `apply errors frozen manifest retains stage protects operators and is repeatable without provider calls`() {
        check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
        RecoveryMysql().withDatabaseName("replay_errors").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val ds = org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
                val jdbc = org.springframework.jdbc.core.JdbcTemplate(ds)
                val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val payload = VerificationWorkerEvidence(
                        search = ApolloSearchEvidence(candidates = listOf(ApolloCandidate("person-1", "Ada", null, "L**", null, null, null)),
                            complete = true, matchingVersion = 2, queryNames = setOf("Ada Lee")),
                        progress = VerificationProgress(stage = "APOLLO_SEARCH", queryIndex = 1, complete = true, candidateIds = listOf("person-1")),
                        queries = listOf(ApolloSearchQuery("Ada Lee")), ruleVersion = 1)
                    val items = (1..4).map { index ->
                        val item = f.seed("recovery-$index")
                        val lease = f.claim()
                        val attempt = f.repository.reserveAttemptWithBudget(item.id, lease.leaseToken!!, lease.generation,
                            VerificationAttemptRequest("frozen-$index", VerificationProvider.APOLLO, VerificationQueryKind.DETAIL,
                                1, 1, """{"personId":"person-1","matchingVersion":2}"""), 1, 1, 1, f.clock.instant()).attemptId!!
                        assertTrue(f.repository.completeAttempt(item.id, lease.leaseToken!!, lease.generation,
                            attempt, 422, "REQUEST_INVALID", null, null, f.clock.instant()))
                        assertTrue(f.repository.saveFailure(item.id, lease.leaseToken!!, lease.generation,
                            VerificationResult(VerificationState.ERROR, null, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE,
                                tool.mapper.writeValueAsString(payload), if (index == 3) """["FACT_CHANGED"]""" else """["REQUEST_INVALID"]"""),
                            0, f.clock.instant()))
                        if (index == 2) f.manual(item)
                        f.item(item.id)
                    }
                    f.settleHistorical()
                    val directory = tool.freeze(jdbc, manager, 2001, items.map { it.id }, root.resolve("freeze"), f.clock,
                        mapOf("fixed" to System.getProperty("java.class.path")))
                    val manifest = directory.resolve("manifest.json")
                    val hash = tool.sha(Files.readAllBytes(manifest))
                    val frozen = tool.load(manifest, hash)
                    val beforeBytes = Files.readAllBytes(directory.resolve("before.json"))
                    val attempts = items.associate { it.id to f.repository.findAttempts(it.id) }
                    // A change after freeze is stale, not permission to overwrite a newer operator result.
                    f.manual(items[3])
                    assertEquals(listOf("INELIGIBLE", "PROTECTED_ADMISSION", "INELIGIBLE", "STALE_OR_ALREADY_APPLIED"),
                        tool.apply(frozen, jdbc, manager, f.clock).map { it.status })
                    val receipts = tool.applyErrors(frozen, jdbc, manager, f.clock)
                    assertEquals(listOf("QUEUED", "PROTECTED_ADMISSION", "PROTECTED_REASON", "STALE_OR_ALREADY_APPLIED"), receipts.map { it.status })
                    assertEquals(VerificationState.QUEUED, f.item(items[0].id).state)
                    assertEquals(items[0].evidenceJson, f.item(items[0].id).evidenceJson)
                    assertEquals("APOLLO_SEARCH", tool.mapper.readTree(f.item(items[0].id).evidenceJson).path("progress").path("stage").asText())
                    assertEquals("STALE_OR_ALREADY_APPLIED", tool.applyErrors(frozen, jdbc, manager, f.clock).first().status)
                    assertEquals(items[0].generation + 1, f.item(items[0].id).generation)
                    assertArrayEquals(beforeBytes, Files.readAllBytes(directory.resolve("before.json")))
                    assertEquals(hash, tool.sha(Files.readAllBytes(manifest)))
                    items.forEach { assertEquals(attempts[it.id], f.repository.findAttempts(it.id)); assertEquals(it.requestCount, f.item(it.id).requestCount) }
                    assertTrue(f.apolloRequests.isEmpty()); assertTrue(f.orcidRequests.isEmpty()); assertEquals(0, f.autoCount()!!.toInt())
                    val reports = Files.walk(directory).use { paths -> paths.filter { it.fileName.toString() == "apply-errors.json" }.toArray().map { it as Path } }
                    assertEquals(2, reports.size)
                    reports.forEach { path ->
                        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path))
                        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(path.parent))
                        val text = Files.readString(path)
                        assertFalse(text.contains("Ada")); assertFalse(text.contains("example.invalid"))
                        assertEquals("QUEUE_ONLY", tool.mapper.readTree(text).path("layer").asText())
                    }
                    listOf("--execution-id", "--item-ids", "--fixed-classpath", "--unknown").forEach { extra ->
                        assertThrows(IllegalArgumentException::class.java) { tool.execute(arrayOf("apply-errors", "--manifest", manifest.toString(),
                            "--manifest-sha256", hash, extra, "forbidden")) }
                    }
                    assertThrows(IllegalArgumentException::class.java) { tool.execute(arrayOf("apply-errors", "--manifest", manifest.toString(),
                        "--manifest-sha256", hash, "--manifest-sha256", hash)) }
                    assertThrows(IllegalArgumentException::class.java) { tool.execute(arrayOf("apply-errors", "--manifest", manifest.toString(),
                        "--manifest-sha256", "0".repeat(64))) }
                }
            }
    }
}
