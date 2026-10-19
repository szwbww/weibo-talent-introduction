package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.system.exitProcess

/** Single-purpose, opt-in operations main. No Spring context, scheduler, provider or ES client. */
object DiscoveryVerificationReplayTool {
    val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val classNames = listOf("QualificationEvidenceParser", "DiscoveryVerificationRules")
    data class Before(val schemaVersion: Int = 1, val executionId: Long, val itemIds: List<Long>,
        val capturedAt: Instant, val run: VerificationRun, val items: List<VerificationItem>,
        val attempts: Map<Long, List<VerificationAttempt>>, val admissions: Map<String, ReplayAdmission?>)
    data class Manifest(val schemaVersion: Int = 1, val executionId: Long, val sortedItemIds: List<Long>,
        val capturedAt: Instant, val evaluatedAt: Instant, val beforeSha256: String,
        val baselineClassHashes: Map<String, Map<String, String>>, val oldInputStatus: String = "UNAVAILABLE_ORIGINAL_INPUT_RELEASED")
    data class Frozen(val directory: Path, val manifest: Manifest, val before: Before)
    data class EvaluationRow(val itemId: Long, val localOutcome: String, val outcome: String, val qualification: String,
        val route: String, val matched: Boolean, val reasons: List<String>, val missing: List<String>,
        val factCounts: Map<String, Int>, val evidenceCount: Int, val unqueriedAliasCount: Int,
        val persistentGateOutcome: String, val historicalNameConflict: Boolean)

    @JvmStatic fun main(args: Array<String>) {
        java.util.logging.LogManager.getLogManager().reset()
        (org.slf4j.LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)
            ?.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)?.level = ch.qos.logback.classic.Level.OFF
        try { execute(args) } catch (_: Exception) {
            // JDBC/provider messages can contain credentials and personal data. Never print their text or stack.
            System.err.println("REPLAY_FAILED: invalid input, unavailable resource, or transaction failure; no secret details emitted")
            exitProcess(2)
        }
    }
    fun execute(args: Array<String>) {
        require(args.isNotEmpty() && args[0] in setOf("freeze", "evaluate", "apply", "inspect"))
        require((args.size - 1) % 2 == 0)
        val pairs = args.drop(1).chunked(2)
        require(pairs.all { it[0].startsWith("--") } && pairs.map { it[0] }.distinct().size == pairs.size)
        val options = pairs.associate { it[0] to it[1] }
        val allowed = when (args[0]) {
            "freeze" -> setOf("--execution-id", "--item-ids", "--output", "--old-classpath", "--fixed-classpath")
            "evaluate" -> setOf("--manifest", "--manifest-sha256", "--old-classpath", "--fixed-classpath")
            else -> setOf("--manifest", "--manifest-sha256")
        }
        require(options.keys.all { it in allowed })
        fun required(key: String) = requireNotNull(options[key])
        if (args[0] == "freeze") {
            val ids: List<Long> = mapper.readValue(Files.readAllBytes(Paths.get(required("--item-ids"))))
            validateIds(ids)
            val db = database()
            val directory = freeze(db.first, db.second, required("--execution-id").toLong(), ids,
                Paths.get(required("--output")), Clock.systemUTC(), classpaths(options))
            println("FROZEN manifest=$directory/manifest.json sha256=${sha(Files.readAllBytes(directory.resolve("manifest.json")))}")
        } else {
            val frozen = load(Paths.get(required("--manifest")), required("--manifest-sha256"))
            when (args[0]) {
                "evaluate" -> evaluate(frozen, classpaths(options))
                "apply" -> { val db = database(); apply(frozen, db.first, db.second, Clock.systemUTC()) }
                "inspect" -> { val db = database(); inspect(frozen, db.first, db.second, Clock.systemUTC()) }
            }
        }
    }
    private fun classpaths(options: Map<String, String>) = mapOf("fixed" to (options["--fixed-classpath"] ?: System.getProperty("java.class.path"))) +
        options["--old-classpath"]?.let { mapOf("old" to it) }.orEmpty()
    private fun database(): Pair<JdbcTemplate, DataSourceTransactionManager> {
        val source = DriverManagerDataSource(requireNotNull(System.getenv("DB_URL")),
            requireNotNull(System.getenv("DB_USERNAME")), requireNotNull(System.getenv("DB_PASSWORD")))
        return JdbcTemplate(source) to DataSourceTransactionManager(source)
    }
    fun validateIds(ids: List<Long>) { require(ids.size in 1..100 && ids.all { it > 0 } && ids.distinct().size == ids.size) }
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun hashes(classpath: String): Map<String, String> = classNames.associateWith { name ->
        val relative = "com/weibo/talentintroduction/discovery/service/$name.class"
        val bytes = classpath.split(java.io.File.pathSeparator).asSequence().mapNotNull { entry ->
            val path = Paths.get(entry)
            when {
                Files.isDirectory(path) && Files.isRegularFile(path.resolve(relative)) -> Files.readAllBytes(path.resolve(relative))
                Files.isRegularFile(path) && entry.endsWith(".jar") -> java.util.jar.JarFile(path.toFile()).use { jar ->
                    jar.getJarEntry(relative)?.let { jar.getInputStream(it).use { input -> input.readBytes() } }
                }
                else -> null
            }
        }.firstOrNull() ?: error("Required compiled class unavailable")
        sha(bytes)
    }
    fun freeze(jdbc: JdbcTemplate, manager: DataSourceTransactionManager, executionId: Long, ids: List<Long>,
        output: Path, clock: Clock, classpaths: Map<String, String>): Path {
        validateIds(ids); require(executionId > 0)
        val repository = DiscoveryVerificationRepository(jdbc)
        val before = readTransaction(manager) {
            val run = requireNotNull(repository.findRun(executionId))
            val items = ids.sorted().mapNotNull(repository::findItem)
            require(items.all { it.ownerExecutionId == executionId })
            Before(executionId = executionId, itemIds = ids.sorted(), capturedAt = clock.instant(), run = run, items = items,
                attempts = items.associate { it.id to repository.findAttempts(it.id) },
                admissions = items.associate { it.expertDocId to repository.replayAdmission(it.expertDocId) })
        }
        val bytes = mapper.writeValueAsBytes(before)
        val hash = sha(bytes)
        val manifest = Manifest(executionId = executionId, sortedItemIds = ids.sorted(), capturedAt = before.capturedAt,
            evaluatedAt = before.capturedAt, beforeSha256 = hash, baselineClassHashes = classpaths.mapValues { hashes(it.value) })
        secureDirectory(output); secureDirectory(output.resolve(executionId.toString()))
        val directory = output.resolve(executionId.toString()).resolve(hash)
        require(!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) { "Frozen input cannot be overwritten" }
        Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        atomicWrite(directory.resolve("before.json"), bytes)
        atomicWrite(directory.resolve("manifest.json"), mapper.writeValueAsBytes(manifest))
        atomicWrite(directory.resolve("freeze-summary.json"), mapper.writeValueAsBytes(mapOf("requested" to ids.size,
            "found" to before.items.size, "missingItemIds" to ids.filterNot { id -> before.items.any { it.id == id } },
            "historicalInputComparison" to "UNAVAILABLE_ORIGINAL_INPUT_RELEASED", "businessWrites" to 0,
            "sampleCopy" to "NOT_REQUESTED_ORIGINAL_STORE_UNCHANGED", "cleanupPath" to directory.toString())))
        return directory
    }
    private fun <T> readTransaction(manager: DataSourceTransactionManager, block: () -> T): T =
        TransactionTemplate(manager).apply { isReadOnly = true; isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ }
            .execute { block() }!!
    private fun noLinks(path: Path) {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) { require(!Files.isSymbolicLink(current)); current = current.parent }
    }
    private fun secureDirectory(path: Path) {
        noLinks(path)
        if (!Files.exists(path)) { path.parent?.let { if (!Files.exists(it)) secureDirectory(it) }; Files.createDirectory(path,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))) }
        require(Files.isDirectory(path) && Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rwx------"))
    }
    fun atomicWrite(path: Path, bytes: ByteArray) {
        noLinks(path); require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)); secureDirectory(path.parent)
        val temp = path.parent.resolve(".${UUID.randomUUID()}.tmp")
        try {
            Files.createFile(temp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true)
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
            FileChannel.open(path.parent, StandardOpenOption.READ).use { it.force(true) }
        } finally { Files.deleteIfExists(temp) }
    }
    private fun restrictedRead(path: Path): ByteArray {
        noLinks(path); secureDirectory(path.parent)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
            Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rw-------"))
        return Files.readAllBytes(path)
    }
    fun load(path: Path, expectedManifestHash: String): Frozen {
        require(expectedManifestHash.matches(Regex("[a-f0-9]{64}")))
        val bytes = restrictedRead(path); require(sha(bytes) == expectedManifestHash)
        val manifest: Manifest = mapper.readValue(bytes); require(manifest.schemaVersion == 1)
        validateIds(manifest.sortedItemIds); require(manifest.sortedItemIds == manifest.sortedItemIds.sorted())
        val beforeBytes = restrictedRead(path.parent.resolve("before.json")); require(sha(beforeBytes) == manifest.beforeSha256)
        val before: Before = mapper.readValue(beforeBytes)
        require(before.schemaVersion == 1 && before.executionId == manifest.executionId && before.itemIds == manifest.sortedItemIds &&
            before.capturedAt == manifest.capturedAt && before.run.executionId == manifest.executionId &&
            before.items.map { it.id }.distinct().size == before.items.size && before.items.all {
                it.ownerExecutionId == manifest.executionId && it.id in manifest.sortedItemIds
            })
        return Frozen(path.parent, manifest, before)
    }
    fun expected(before: Before) = before.items.map { ReplayExpected(it, before.admissions[it.expertDocId],
        DiscoveryVerificationRepository.replayEvidence(it.evidenceJson ?: "{}")) }
    fun apply(frozen: Frozen, jdbc: JdbcTemplate, manager: DataSourceTransactionManager, clock: Clock): List<ReplayReceipt> {
        val repository = DiscoveryVerificationRepository(jdbc)
        val inputs = expected(frozen.before)
        val receipts = (if (inputs.isEmpty()) emptyList() else TransactionTemplate(manager).execute {
            repository.requeuePendingForEvidenceReplay(frozen.before.executionId, inputs, clock.instant())
        }!!) + frozen.before.itemIds.filterNot { id -> frozen.before.items.any { it.id == id } }.map { ReplayReceipt(it, "SOURCE_MISSING") }
        report(frozen, "apply", mapOf("layer" to "QUEUE_ONLY", "receipts" to receipts, "queued" to receipts.count { it.status == "QUEUED" }))
        return receipts
    }
    fun inspect(frozen: Frozen, jdbc: JdbcTemplate, manager: DataSourceTransactionManager, clock: Clock): Map<String, Any> {
        val repository = DiscoveryVerificationRepository(jdbc)
        val rows = readTransaction(manager) { frozen.before.items.map { old ->
            val current = repository.findItem(old.id)
            mapOf("itemId" to old.id, "state" to current?.state?.name, "outcome" to current?.outcome?.name,
                "route" to current?.route?.name, "reasons" to mapper.readTree(current?.reasonsJson ?: "[]"),
                "admissionRevision" to current?.admissionRevision, "projection" to current?.projectionState?.name,
                "attemptDelta" to (repository.findAttempts(old.id).size - frozen.before.attempts[old.id].orEmpty().size),
                "requestDelta" to current?.let { it.requestCount - old.requestCount })
        } }
        val fixed = Files.list(frozen.directory).use { paths -> paths.filter { it.fileName.toString().startsWith("evaluate-") }.sorted().toArray().lastOrNull() as? Path }
        val suggested = fixed?.let { mapper.readTree(restrictedRead(it)).path("groups").path("fixed") }
            ?.filter { it.path("outcome").asText() in setOf("DIRECT_CLEAR", "APOLLO_CLEAR", "APOLLO_UNCLEAR") }
            ?.map { it.path("itemId").asLong() }.orEmpty()
        val result = mapOf("layer" to "ACTUAL_COMMIT_AND_PROJECTION", "executionId" to frozen.before.executionId,
            "inspectedAt" to clock.instant().toString(), "denominator" to frozen.before.itemIds.size, "rows" to rows,
            "durableAdmissions" to rows.count { it["admissionRevision"] != null },
            "outcomes" to rows.groupingBy { it["outcome"] ?: "MISSING" }.eachCount(),
            "routes" to rows.groupingBy { it["route"] ?: "MISSING" }.eachCount(),
            "projectionStates" to rows.groupingBy { it["projection"] ?: "MISSING" }.eachCount(),
            "missingItemIds" to frozen.before.itemIds.filterNot { id -> frozen.before.items.any { it.id == id } },
            "rulePassButCommitNotAdmitted" to rows.filter { it["itemId"] in suggested && it["admissionRevision"] == null }.map { it["itemId"] },
            "newRunHttpObservation" to "NOT_PERFORMED_REQUIRES_SEPARATE_AUTHORIZATION")
        report(frozen, "inspect", result); return result
    }
    private fun report(frozen: Frozen, kind: String, value: Any) = atomicWrite(
        frozen.directory.resolve("$kind-${Instant.now().toEpochMilli()}-${UUID.randomUUID()}.json"), mapper.writeValueAsBytes(value))
    fun evaluate(frozen: Frozen, classpaths: Map<String, String>): Map<String, List<EvaluationRow>> {
        val groups = linkedMapOf<String, List<EvaluationRow>>()
        for (group in listOf("old", "parser-only", "fixed")) {
            val version = if (group == "fixed") "fixed" else "old"
            val cp = classpaths[version] ?: continue
            require(hashes(cp) == frozen.manifest.baselineClassHashes[version]) { "Compiled version changed" }
            val first = fork(frozen, cp, group); val second = fork(frozen, cp, group)
            require(first == second) { "Non-deterministic replay" }; groups[group] = first
        }
        require(groups.containsKey("fixed"))
        report(frozen, "evaluate", mapOf("layer" to "PURE_RULES_NOT_COMMIT", "beforeSha256" to frozen.manifest.beforeSha256,
            "evaluatedAt" to frozen.manifest.evaluatedAt, "denominator" to frozen.before.itemIds.size, "groups" to groups,
            "baselineStatus" to if (groups.containsKey("old")) "SAME_NEWLY_FROZEN_INPUT" else "UNAVAILABLE_OLD_CLASSES",
            "originalHistoricalInput" to frozen.manifest.oldInputStatus,
            "missingItemIds" to frozen.before.itemIds.filterNot { id -> frozen.before.items.any { it.id == id } },
            "changes" to frozen.before.items.map { item -> mapOf("itemId" to item.id, "groups" to groups.mapValues { (_, rows) ->
                rows.single { it.itemId == item.id }.let { mapOf("outcome" to it.outcome, "reasons" to it.reasons, "facts" to it.factCounts) }
            }) },
            "summary" to groups.mapValues { (_, rows) -> mapOf("outcomes" to rows.groupingBy { it.outcome }.eachCount(),
                "reasons" to rows.flatMap { it.reasons }.groupingBy { it }.eachCount(), "matched" to rows.count { it.matched }) }))
        return groups
    }
    private fun fork(frozen: Frozen, classpath: String, group: String): List<EvaluationRow> {
        val bridge = Paths.get(Evaluator::class.java.protectionDomain.codeSource.location.toURI()).toString()
        val executable = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(listOf(executable, "-cp", classpath + java.io.File.pathSeparator + bridge,
            Evaluator::class.java.name, frozen.directory.resolve("before.json").toString(), frozen.manifest.evaluatedAt.toString(), group))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.use { it.readBytes() }; require(process.waitFor() == 0) { "Isolated evaluator failed" }
        return mapper.readValue(output)
    }
    /** This bridge uses ONLY stable parse/evaluate entry points, including under the old classpath. */
    object Evaluator {
        @JvmStatic fun main(args: Array<String>) {
            val input = mapper.readTree(Files.readAllBytes(Paths.get(args[0])))
            val rows = evaluateInput(input, Instant.parse(args[1]), args[2])
            print(mapper.writeValueAsString(rows))
        }
        fun evaluateInput(input: JsonNode, instant: Instant, group: String): List<EvaluationRow> {
            val clock = Clock.fixed(instant, ZoneOffset.UTC)
            val parser = QualificationEvidenceParser(clock); val rules = DiscoveryVerificationRules(clock)
            return input.path("items").map { node ->
                val snapshot: VerificationSnapshot = mapper.readValue(node.path("snapshotJson").asText())
                val payload = mapper.readTree(node.path("evidenceJson").asText("{}"))
                val documents = payload.path("orcidCompleted").mapNotNull { completion -> completion.get("document")?.takeUnless { it.isNull } }
                    .map { original ->
                        val copy = original.deepCopy<JsonNode>()
                        if (group == "parser-only") normalizePublic(copy)
                        mapper.treeToValue(copy, OrcidEvidenceDocument::class.java)
                    }
                val existing: List<VerificationEvidence> = mapper.convertValue(payload.path("evidence").takeIf { it.isArray } ?: mapper.createArrayNode(),
                    mapper.typeFactory.constructCollectionType(List::class.java, VerificationEvidence::class.java))
                val urls = documents.map { "https://pub.orcid.org/v3.0/${snapshot.boundOrcid}/${it.endpoint}" }.toSet()
                val facts = (existing.filterNot { it.provider == "ORCID" && it.sourceUrl in urls &&
                    it.field in setOf("name", "otherName", "employment", "education") } +
                    parser.parse(snapshot, OrcidEvidenceRead(documents, true, emptyList()))).distinct()
                val search = payload.get("search")?.takeUnless { it.isNull }?.let { mapper.treeToValue(it, ApolloSearchEvidence::class.java) }
                val local = rules.evaluate(snapshot, facts, null, false)
                val decision = if (local.outcome == VerificationOutcome.DIRECT_CLEAR || local.qualification in
                    setOf(VerificationQualification.NOT_MET, VerificationQualification.CONFLICT)) local else rules.evaluate(snapshot, facts, search, false)
                val gate = rules.evaluate(snapshot, facts, search, false)
                EvaluationRow(node.path("id").asLong(), local.outcome.name, decision.outcome.name, decision.qualification.name,
                    decision.route.name, decision.matchedPersonId != null, decision.reasonCodes, decision.missingFields,
                    facts.groupingBy { "${it.field}:${it.assertionSource}:${it.temporalScope}" }.eachCount().toSortedMap(), facts.size,
                    facts.count { it.field == "otherName" && search?.queryNames?.contains(it.value) != true }, gate.outcome.name,
                    mapper.readTree(node.path("reasonsJson").asText("[]")).any { it.asText() == "NAME_CONFLICT" })
            }
        }
        private fun normalizePublic(node: JsonNode) {
            if (node is ObjectNode) {
                val visibility = node.get("visibility")
                if (visibility?.isTextual == true && visibility.textValue().equals("public", true)) node.put("visibility", "PUBLIC")
            }
            node.forEach(::normalizePublic)
        }
    }
}
