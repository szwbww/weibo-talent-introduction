package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.system.exitProcess

/** Offline value-only comparison. No Spring context, database, ES client or provider is created here. */
object DiscoveryHistoricalReplayTool {
    const val VERSION = 2
    const val REPLAY_BUDGET_MODE = "FROZEN_WORK_BOUNDED_100_PAGES_2_MIB_100_FACTS"
    val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    data class Paper(val source: PaperSourceRef, val path: String, val kind: ArchiveAttemptKind,
        val metadata: PaperMetadata, val baseline: List<AuthorEmail>?, val baselineAttemptIds: List<Long> = emptyList())
    data class Expert(val docId: String, val identityHash: String, val factFingerprint: String,
        val admissionRevision: Long?, val profile: ExpertProfile)
    data class Scope(val papers: List<Paper>, val experts: List<Expert>, val historyComplete: Boolean,
        val sourceComplete: Boolean, val config: Map<String, String>, val evaluationAt: Instant,
        val pendingDocIds: List<String> = emptyList(), val reasons: List<String> = emptyList())
    data class Manifest(val version: Int = VERSION, val scope: Scope, val parserVersions: Map<String, Int>,
        val ruleVersion: Int = 3, val implementationHashes: Map<String, String>,
        val replayBudgetMode: String = REPLAY_BUDGET_MODE)
    data class Frozen(val directory: Path, val hash: String, val manifest: Manifest)
    data class Row(val source: PaperSourceRef, val coverage: String, val reasons: List<String>,
        val before: List<AuthorEmail>?, val after: List<AuthorEmail>, val analysis: PaperAnalysisResult?)
    data class Comparison(val version: Int, val manifestHash: String, val coverage: String,
        val reasons: List<String>, val denominators: Map<String, Int>, val counts: Map<String, Int?>,
        val intersections: Map<String, List<String>>, val rows: List<Row>, val businessWrites: Int = 0,
        val providerHttpRequests: Int = 0, val externalRefresh: Boolean = false,
        val qualificationDecision: String = "NOT_EVALUATED_USE_FROZEN_AUDIT_REUSE_RUN",
        val replayBudgetMode: String = REPLAY_BUDGET_MODE)

    fun analyzer(): PaperBodyAnalyzer = bodyAnalyzer(PaperQualificationEvidenceExtractor())
    private fun replayAnalyzer(): PaperBodyAnalyzer = bodyAnalyzer(PaperQualificationEvidenceExtractor.forFrozenReplay())
    private fun bodyAnalyzer(qualification: PaperQualificationEvidenceExtractor): PaperBodyAnalyzer {
        val deny = org.springframework.web.client.RestTemplate().apply {
            interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { _, _, _ ->
                error("OFFLINE_PROVIDER_DISPATCH_FORBIDDEN") })
        }
        val bounded = object : BoundedHttpExecutor {
            override fun <T : Any> getForObject(base: org.springframework.web.client.RestTemplate, url: String,
                responseType: Class<T>, connectCapMs: Long, readCapMs: Long, deadline: Instant?): T? =
                error("OFFLINE_PROVIDER_DISPATCH_FORBIDDEN")
            override fun <T> execute(base: org.springframework.web.client.RestTemplate, uri: java.net.URI,
                connectCapMs: Long, readCapMs: Long, deadline: Instant?,
                responseExtractor: org.springframework.web.client.ResponseExtractor<T>): T? =
                error("OFFLINE_PROVIDER_DISPATCH_FORBIDDEN")
        }
        return PaperBodyAnalyzer(PdfEmailExtractor(deny, PlainTextEmailExtractor(), PdfExtractionProperties(), bounded),
            qualification, mapper)
    }
    fun implementationHashes(): Map<String, String> = listOf(DiscoveryHistoricalReplayTool::class.java,
        PaperBodyAnalyzer::class.java, PdfEmailExtractor::class.java, PdfAuthorContactLayout::class.java,
        SourceAuthorEmailResolver::class.java, JatsXmlEmailParser::class.java,
        PaperQualificationEvidenceExtractor::class.java, DiscoveryVerificationRules::class.java).associate { type ->
        type.name to DiscoveryPaperArchiveService.sha(requireNotNull(type.getResourceAsStream("/${type.name.replace('.', '/')}.class")).use { it.readBytes() })
    }.toSortedMap()
    fun freeze(scope: Scope, directory: Path, analyzer: PaperBodyAnalyzer = analyzer()): Frozen {
        require(scope.config.isNotEmpty()) { "CONFIG_SNAPSHOT_REQUIRED" }
        require(scope.papers.map { it.source }.distinct().size == scope.papers.size)
        require(scope.experts.map { it.docId }.distinct().size == scope.experts.size)
        require(scope.pendingDocIds.distinct().size == scope.pendingDocIds.size && scope.pendingDocIds.all { id -> scope.experts.any { it.docId == id } })
        scope.experts.forEach { verificationHash(it.identityHash); verificationHash(it.factFingerprint) }
        require(scope.papers.all { it.metadata.source == it.source.sourceName })
        // Missing bytes are retained in the fixed scope, never dropped from the denominator.
        scope.papers.forEach { p -> if (Files.exists(Paths.get(p.path), LinkOption.NOFOLLOW_LINKS)) readBody(p) }
        secureDirectory(directory)
        val manifest = Manifest(scope = scope.copy(papers = scope.papers.sortedWith(compareBy({ it.source.archiveRecordId }, { it.source.attemptId })),
            experts = scope.experts.sortedBy { it.docId }, pendingDocIds = scope.pendingDocIds.sorted(), config = scope.config.toSortedMap()),
            parserVersions = analyzer.versions().toSortedMap(), implementationHashes = implementationHashes())
        val bytes = mapper.writeValueAsBytes(manifest)
        DiscoveryVerificationReplayTool.atomicWrite(directory.resolve("manifest.json"), bytes)
        // Roster is private, separate from binding differences; neither is written to stdout.
        DiscoveryVerificationReplayTool.atomicWrite(directory.resolve("pending-roster.json"), mapper.writeValueAsBytes(manifest.scope.experts.filter { it.docId in scope.pendingDocIds }))
        return Frozen(directory, DiscoveryPaperArchiveService.sha(bytes), manifest)
    }
    fun load(path: Path, expectedHash: String? = null): Frozen {
        privateFile(path)
        val bytes = Files.readAllBytes(path); val hash = DiscoveryPaperArchiveService.sha(bytes)
        if (expectedHash != null) require(hash == expectedHash) { "MANIFEST_HASH_MISMATCH" }
        val manifest: Manifest = mapper.readValue(bytes)
        require(manifest.version == VERSION && manifest.ruleVersion == 3 && manifest.replayBudgetMode == REPLAY_BUDGET_MODE)
        require(manifest.parserVersions == analyzer().versions() && manifest.implementationHashes == implementationHashes()) { "IMPLEMENTATION_CHANGED_FREEZE_AGAIN" }
        return Frozen(path.parent, hash, manifest)
    }
    fun readBody(paper: Paper): ByteArray {
        val path = Paths.get(paper.path); noLinks(path)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "SOURCE_MISSING" }
        val bytes = Files.readAllBytes(path)
        require(DiscoveryPaperArchiveService.sha(bytes) == paper.source.bodySha256) { "SOURCE_HASH_CHANGED" }
        return bytes
    }
    fun compare(frozen: Frozen, analyzer: PaperBodyAnalyzer = replayAnalyzer()): Comparison {
        require(frozen.manifest.version == VERSION && frozen.manifest.replayBudgetMode == REPLAY_BUDGET_MODE &&
            frozen.manifest.parserVersions == analyzer.versions())
        val scope = frozen.manifest.scope
        val rows = scope.papers.map { paper ->
            try {
                val body = PaperBodyInput(paper.source.bodySha256, paper.kind, readBody(paper))
                val parsed = analyzer.parse(body, mapper.valueToTree(paper.metadata))
                val first = paper.baseline?.let { mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(mapOf(
                    "attemptIds" to paper.baselineAttemptIds, "outcome" to mapOf("emails" to it))) }
                val analysis = analyzer.assemble(mapOf(body.hash to parsed), first,
                    scope.papers.filter { it.source.archiveRecordId == paper.source.archiveRecordId }.map { it.source.attemptId }.toSet(), scope.evaluationAt)
                val complete = analysis.baselineCoverage == "COMPLETE" && analysis.coverage.status !in setOf(PaperEvidenceStatus.ERROR, PaperEvidenceStatus.PARTIAL)
                Row(paper.source, if (complete) "COMPLETE" else "PARTIAL", analysis.reasons, paper.baseline, parsed.emails, analysis)
            } catch (e: Exception) {
                val reason = if (e is IllegalArgumentException && e.message == "SOURCE_HASH_CHANGED") "SOURCE_HASH_CHANGED"
                    else if (!Files.isRegularFile(Paths.get(paper.path), LinkOption.NOFOLLOW_LINKS)) "SOURCE_MISSING" else "SOURCE_READ_OR_PARSE_FAILED"
                Row(paper.source, "PARTIAL", listOf(reason), paper.baseline, emptyList(), null)
            }
        }
        fun email(a: AuthorEmail) = DiscoveryIdentity.normalizedEmail(a.email)
        fun binding(a: AuthorEmail): String? = if (a.givenNames.isNullOrBlank() || a.familyNames.isNullOrBlank()) null
            else email(a) + "|" + DiscoveryIdentity.hash(listOf(a.givenNames, a.familyNames).joinToString(" ").lowercase().trim().replace(Regex("\\s+"), " "))
        val before = rows.flatMap { it.before.orEmpty() }; val after = rows.flatMap { it.after }
        val oldEmails = before.map(::email).toSortedSet(); val newEmails = after.map(::email).toSortedSet()
        val oldBindings = before.mapNotNull(::binding).toSortedSet()
        val proofBindings = after.filter { DiscoveryIdentity.validEvidence(it.identityEvidence) }.mapNotNull(::binding).toSortedSet()
        val conflictingEmails = proofBindings.groupBy { it.substringBefore('|') }.filterValues { it.size > 1 }.keys
        val safeBindings = proofBindings.filterNot { it.substringBefore('|') in conflictingEmails }.toSortedSet()
        val historyEmails = scope.experts.mapNotNull { it.profile.email?.let(DiscoveryIdentity::normalizedEmail) }.toSet()
        val historyBindings = scope.experts.mapNotNull { e -> binding(AuthorEmail(e.profile.email.orEmpty(), e.profile.givenNames, e.profile.familyNames, false, null, null)) }.toSet()
        val added = safeBindings - oldBindings
        val reasons = (scope.reasons + rows.flatMap { it.reasons } +
            (if (!scope.historyComplete) listOf("EXPERT_HISTORY_INCOMPLETE") else emptyList()) +
            (if (!scope.sourceComplete) listOf("SOURCE_INVENTORY_INCOMPLETE") else emptyList())).distinct().sorted()
        val complete = scope.historyComplete && scope.sourceComplete && rows.all { it.coverage == "COMPLETE" } && reasons.isEmpty()
        val intersections = linkedMapOf("baselineAndReparsedEmails" to (oldEmails intersect newEmails).sorted(),
            "observedAddedEmails" to (newEmails - oldEmails).sorted(), "observedRemovedEmails" to (oldEmails - newEmails).sorted(),
            "observedAddedBindings" to added.sorted(), "observedRemovedBindings" to (oldBindings - safeBindings).sorted(),
            "existingPersonEvidence" to (added intersect historyBindings).sorted(),
            "bindingsWithExistingEmail" to added.filter { it.substringBefore('|') in historyEmails }.sorted(),
            "potentialNewRawBindings" to added.filterNot { it.substringBefore('|') in historyEmails }.sorted(),
            "conflictingEmails" to conflictingEmails.sorted())
        return Comparison(VERSION, frozen.hash, if (complete) "COMPLETE" else "PARTIAL", reasons,
            mapOf("papers" to scope.papers.map { it.source.archiveRecordId }.distinct().size, "attempts" to scope.papers.size,
                "readAttempts" to rows.count { it.analysis != null }, "completeAttempts" to rows.count { it.coverage == "COMPLETE" },
                "historicalExperts" to scope.experts.size, "pendingExperts" to scope.pendingDocIds.size),
            linkedMapOf("baselineEmails" to oldEmails.size, "reparsedEmails" to newEmails.size,
                "baselineBindings" to oldBindings.size, "reparsedProofBindings" to safeBindings.size,
                "newEmails" to if (complete) (newEmails - oldEmails).size else null,
                "newBindings" to if (complete) added.size else null,
                "existingPersonEvidence" to if (complete) (added intersect historyBindings).size else null,
                "potentialNewRaw" to if (complete) intersections.getValue("potentialNewRawBindings").size else null,
                "qualificationFacts" to rows.sumOf { it.analysis?.qualificationFacts?.size ?: 0 },
                "wouldAdmit" to null, "newRaw" to 0, "actualAdmitted" to 0, "candidateSync" to 0), intersections, rows)
    }
    fun writeComparison(frozen: Frozen): Path {
        val bytes = mapper.writeValueAsBytes(compare(frozen))
        val path = frozen.directory.resolve("compare-${DiscoveryPaperArchiveService.sha(bytes)}.json")
        if (Files.exists(path)) { privateFile(path); require(Files.readAllBytes(path).contentEquals(bytes)) }
        else DiscoveryVerificationReplayTool.atomicWrite(path, bytes)
        return path
    }
    internal fun noLinks(path: Path) {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) { require(!Files.isSymbolicLink(current)) { "UNSAFE_PATH" }; current = current.parent }
    }
    internal fun secureDirectory(path: Path) {
        noLinks(path)
        if (!Files.exists(path)) {
            path.parent?.let { if (!Files.exists(it)) secureDirectory(it) }
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        }
        require(Files.isDirectory(path) && Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rwx------"))
    }
    internal fun privateFile(path: Path) {
        noLinks(path); secureDirectory(path.parent)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rw-------"))
    }
    @JvmStatic fun main(args: Array<String>) {
        java.util.logging.LogManager.getLogManager().reset()
        (org.slf4j.LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)?.level = ch.qos.logback.classic.Level.OFF
        try { execute(args) } catch (_: Exception) { System.err.println("HISTORICAL_REPLAY_FAILED: input, source, identity or storage rejected; private details withheld"); exitProcess(2) }
    }
    fun execute(args: Array<String>) {
        val command = args.firstOrNull()?.takeUnless { it.startsWith("--") } ?: "compare"
        require(command in setOf("freeze", "compare", "apply-bindings", "report"))
        val pairs = (if (args.firstOrNull() == command) args.drop(1) else args.toList()).chunked(2)
        require(pairs.all { it.size == 2 && it[0].startsWith("--") } && pairs.map { it[0] }.distinct().size == pairs.size)
        val options = pairs.associate { it[0] to it[1] }
        require(options.keys.all { it in if (command == "freeze") setOf("--scope", "--output") else setOf("--manifest", "--manifest-hash") })
        fun required(key: String) = requireNotNull(options[key])
        if (command == "freeze") {
            val source = Paths.get(required("--scope")); privateFile(source)
            val frozen = freeze(mapper.readValue<Scope>(Files.readAllBytes(source)), Paths.get(required("--output")))
            println("FROZEN version=$VERSION manifestHash=${frozen.hash}")
        } else {
            val frozen = load(Paths.get(required("--manifest")), options["--manifest-hash"])
            when (command) {
                "apply-bindings" -> {
                    require(required("--manifest-hash") == frozen.hash)
                    HistoricalBindingsRuntime.open(frozen).use { runtime ->
                        val result = runtime.service.apply(frozen, frozen.hash)
                        println("APPLY version=$VERSION manifestHash=${frozen.hash} completed=${result.completed} newRaw=${result.newRaw} cancelled=${result.cancelled}")
                    }
                }
                else -> {
                    val report = writeComparison(frozen); val result = mapper.readValue<Comparison>(Files.readAllBytes(report))
                    println("REPORT version=$VERSION manifestHash=${frozen.hash} reportHash=${DiscoveryPaperArchiveService.sha(Files.readAllBytes(report))} coverage=${result.coverage} attempts=${result.denominators["attempts"]} businessWrites=0 providerHttpRequests=0")
                }
            }
        }
    }
}

/** Explicit apply-only bean graph: no component scan, scheduling, listeners, Flyway or worker. */
@org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = [
    org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration::class,
    org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration::class])
@org.springframework.data.jdbc.repository.config.EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction"])
@org.springframework.boot.context.properties.EnableConfigurationProperties(ElasticsearchProperties::class,
    ExpertDiscoveryProperties::class, OpenAlexProperties::class, EuropePmcProperties::class,
    CandidateFilterProperties::class, AcademicFilterProperties::class, EmailValidationProperties::class,
    ExpertClassificationProperties::class, DiscoveryVerificationProperties::class,
    DiscoveryPaperArchiveProperties::class, DiscoverySampleProperties::class)
@org.springframework.context.annotation.Import(
    ExpertDiscoveryService::class, EuropePmcDataSource::class,
    com.weibo.talentintroduction.expert.service.EmailValidationService::class,
    com.weibo.talentintroduction.expert.service.DnsMxLookupClient::class,
    com.weibo.talentintroduction.expert.service.EligibilityFilterService::class,
    com.weibo.talentintroduction.expert.service.CandidateEligibilityService::class,
    com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class,
    com.weibo.talentintroduction.expert.service.ExpertIndexService::class,
    com.weibo.talentintroduction.expert.service.ExpertRevalidationService::class,
    com.weibo.talentintroduction.expert.service.ExpertSearchService::class,
    com.weibo.talentintroduction.expert.service.ExpertClassificationService::class,
    com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService::class,
    com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService::class,
    com.weibo.talentintroduction.task.service.TaskProgressStore::class,
    ExpertAcademicEnrichmentJobService::class, DiscoveryIdentityRepairService::class,
    DiscoveryPersonRegistrationService::class, DiscoveryPersonService::class, DiscoveryPersonFeatureGate::class,
    com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository::class,
    com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository::class,
    com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository::class,
    com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository::class,
    com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingRepository::class,
    DiscoveryReviewService::class, DiscoveryAdmissionPolicy::class, DiscoveryVerificationAdmissionGate::class,
    DiscoveryVerificationRules::class, DiscoveryVerificationRegistrationService::class,
    DiscoveryApolloSettingsService::class, DiscoveryVerificationQueryService::class,
    DiscoveryVerificationCommitService::class, DiscoveryReviewScanService::class, DiscoveryRecheckService::class,
    DiscoveryPaperArchiveRepository::class, DiscoveryPaperArchiveService::class, PaperVerificationEvidenceService::class)
class HistoricalBindingsConfiguration {
    @org.springframework.context.annotation.Bean fun objectMapper() = DiscoveryHistoricalReplayTool.mapper
    @org.springframework.context.annotation.Bean(name = ["restTemplate", "europePmcRestTemplate"])
    fun restTemplate() = org.springframework.web.client.RestTemplate()
    @org.springframework.context.annotation.Bean fun clock(): java.time.Clock = java.time.Clock.systemUTC()
    @org.springframework.context.annotation.Bean(name = ["discoveryFetchExecutor", "discoveryRecheckPrepareExecutor", "enrichmentExecutor"])
    fun executor(): java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
    @org.springframework.context.annotation.Bean fun transactions(manager: org.springframework.transaction.PlatformTransactionManager) =
        org.springframework.transaction.support.TransactionTemplate(manager)
    @org.springframework.context.annotation.Bean fun analyzer() = DiscoveryHistoricalReplayTool.analyzer()
}

/** Apply has no automatic qualification dispatch. Existing people receive a NEW READY AUDIT/REUSE run. */
class HistoricalBindingsRuntime private constructor(private val context: org.springframework.context.ConfigurableApplicationContext,
    val service: DiscoveryPaperBackfillService) : AutoCloseable {
    override fun close() = context.close()
    companion object {
        fun open(frozen: DiscoveryHistoricalReplayTool.Frozen): HistoricalBindingsRuntime {
            fun env(key: String) = requireNotNull(System.getenv(key)) { "EXPLICIT_APPLY_CONNECTION_REQUIRED" }
            val context = org.springframework.context.annotation.AnnotationConfigApplicationContext()
            val yaml = org.springframework.boot.env.YamlPropertySourceLoader().load("replayDefaults",
                org.springframework.core.io.ClassPathResource("application.yml"))
            yaml.forEach { context.environment.propertySources.addLast(it) }
            context.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("explicitReplay", mapOf(
                "spring.datasource.url" to env("REPLAY_DB_URL"), "spring.datasource.username" to env("REPLAY_DB_USERNAME"),
                "spring.datasource.password" to env("REPLAY_DB_PASSWORD"),
                "talent-introduction.elasticsearch.base-url" to env("REPLAY_ES_URL"),
                "talent-introduction.elasticsearch.username" to env("REPLAY_ES_USERNAME"),
                "talent-introduction.elasticsearch.password" to env("REPLAY_ES_PASSWORD"),
                "talent-introduction.discovery-verification.enabled" to true,
                "talent-introduction.discovery-verification.worker-enabled" to false,
                "talent-introduction.discovery-verification.country-supplement-enabled" to false,
                "spring.flyway.enabled" to false, "spring.jmx.enabled" to false)))
            // refresh(), unlike SpringApplication.run(), publishes no ApplicationReadyEvent recovery hooks.
            context.register(HistoricalBindingsConfiguration::class.java)
            context.refresh()
            try {
                val target = HistoricalSqlBindingsTarget(context.getBean(com.weibo.talentintroduction.task.repository.TaskExecutionRepository::class.java),
                    context.getBean(DiscoveryVerificationRegistrationService::class.java),
                    context.getBean(com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository::class.java),
                    context.getBean(com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository::class.java),
                    context.getBean(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java),
                    context.getBean(DiscoveryAdmissionPolicy::class.java), context.getBean(DiscoveryRecheckService::class.java))
                target.validate(frozen)
                return HistoricalBindingsRuntime(context, DiscoveryPaperBackfillService(context.getBean(ExpertDiscoveryService::class.java),
                    context.getBean(PaperBodyAnalyzer::class.java), target))
            } catch (e: Exception) { context.close(); throw e }
        }
    }
}

class HistoricalSqlBindingsTarget(
    private val tasks: com.weibo.talentintroduction.task.repository.TaskExecutionRepository,
    private val registration: DiscoveryVerificationRegistrationService,
    private val repository: com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository,
    private val admissions: com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository,
    private val writer: com.weibo.talentintroduction.expert.service.ExpertIndexWriterService,
    private val policy: DiscoveryAdmissionPolicy, private val rechecks: DiscoveryRecheckService) : HistoricalBindingsTarget {
    private val mapper = DiscoveryHistoricalReplayTool.mapper
    private var validatedHash: String? = null
    private var conflictingEmails = emptySet<String>()
    override fun validate(frozen: DiscoveryHistoricalReplayTool.Frozen) {
        require(frozen.manifest.scope.historyComplete && frozen.manifest.scope.sourceComplete) { "APPLY_REQUIRES_COMPLETE_INVENTORY" }
        frozen.manifest.scope.experts.forEach { validateExpert(it) }
        val probe = frozen.manifest.scope.experts.firstOrNull()?.profile ?: ExpertProfile(orcidId = "",
            email = null, givenNames = null, familyNames = null, country = null, keyword = null, employment = null)
        require(frozen.manifest.scope.config["admission"] == mapper.writeValueAsString(policy.evaluate(probe).configSnapshot)) { "CONFIG_CHANGED" }
        conflictingEmails = DiscoveryHistoricalReplayTool.compare(frozen).intersections.getValue("conflictingEmails").toSet()
        validatedHash = frozen.hash
    }
    private fun validateExpert(old: DiscoveryHistoricalReplayTool.Expert) {
        val raw = requireNotNull(writer.readDiscoveryDocument(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW, old.docId)) { "CURRENT_IDENTITY_OR_REVISION_CHANGED" }
        val profile = writer.discoveryProfile(old.docId, raw.source)
        val current = DiscoveryVerificationAdmissionGate.snapshot(old.docId, profile, policy.evaluate(profile).configSnapshot)
        require(current.identityHash == old.identityHash && current.factFingerprint == old.factFingerprint &&
            admissions.findAdmission(old.docId)?.revision == old.admissionRevision) { "CURRENT_IDENTITY_OR_REVISION_CHANGED" }
    }
    override fun begin(manifestHash: String): DiscoveryVerificationExecution {
        val now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
        val task = tasks.save(com.weibo.talentintroduction.task.domain.TaskExecution(taskType = "DISCOVERY_PAPER_BACKFILL",
            triggerType = "MANUAL", status = "RUNNING", requestPayload = mapper.writeValueAsString(mapOf("manifestHash" to manifestHash)),
            resultSummary = null, startedAt = now, createdAt = now, updatedAt = now))
        return registration.begin(task.id, PaperSearchCriteria(apolloAssist = false))
    }
    override fun resume(executionId: Long): DiscoveryVerificationExecution {
        val task = tasks.findById(executionId).orElseThrow()
        require(task.taskType == "DISCOVERY_PAPER_BACKFILL")
        if (task.status != "RUNNING") tasks.save(task.copy(status = "RUNNING", finishedAt = null,
            updatedAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)))
        return DiscoveryVerificationExecution(executionId, requireNotNull(repository.findRun(executionId)))
    }
    override fun validatePaper(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>) {
        if (validatedHash != frozen.hash) validate(frozen)
        require(emails.none { DiscoveryIdentity.normalizedEmail(it.email) in conflictingEmails }) { "CROSS_PAPER_IDENTITY_CONFLICT" }
        val history = frozen.manifest.scope.experts
        emails.forEach { incoming ->
            val address = DiscoveryIdentity.normalizedEmail(incoming.email)
            val frozenPeople = history.filter { DiscoveryIdentity.normalizedEmail(it.profile.email) == address }
            frozenPeople.forEach(::validateExpert)
            val hits = writer.findDiscoveryHitsByEmail(address)
            if (frozenPeople.isNotEmpty()) require(hits.map { it.docId }.toSet() == frozenPeople.map { it.docId }.toSet()) { "CURRENT_IDENTITY_OR_REVISION_CHANGED" }
            // A retry may see RAW created by this manifest; never accept an unrelated newly appeared identity.
            if (frozenPeople.isEmpty() && hits.isNotEmpty()) {
                val owner = mapper.readTree(Files.readAllBytes(frozen.directory.resolve("bindings/execution.json"))).path("executionId").asLong()
                require(hits.map { it.docId }.distinct().all { doc -> repository.findItemsByDocIds(listOf(doc)).any { it.ownerExecutionId == owner } }) { "CURRENT_IDENTITY_OR_REVISION_CHANGED" }
            }
        }
    }
    override fun recheckExisting(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>): List<Long> {
        val addresses = emails.map { DiscoveryIdentity.normalizedEmail(it.email) }.toSet()
        val ids = frozen.manifest.scope.experts.filter { DiscoveryIdentity.normalizedEmail(it.profile.email) in addresses }.map { it.docId }
        return ids.chunked(100).mapIndexed { index, batch ->
            val key = java.util.UUID.nameUUIDFromBytes("${frozen.hash}/${paper.source.archiveRecordId}/${paper.source.attemptId}/$index".toByteArray()).toString()
            val run = rechecks.prepare(RecheckPrepareRequest(key, docIds = batch, mode = VerificationMode.AUDIT,
                evidenceMode = RecheckEvidenceMode.REUSE), "historical-bindings")
            run.runId
        }
    }
    override fun finish(context: DiscoveryVerificationExecution, cancelled: Boolean, failed: Boolean) {
        if (!cancelled && !failed) registration.closeInput(context)
        val task = tasks.findById(context.executionId!!).orElseThrow()
        val now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
        tasks.save(task.copy(status = if (cancelled) "CANCELLED" else if (failed) "FAILED" else "SUCCESS",
            finishedAt = now, updatedAt = now))
    }
}
