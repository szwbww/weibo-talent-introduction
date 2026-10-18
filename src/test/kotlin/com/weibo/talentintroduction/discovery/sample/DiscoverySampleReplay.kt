package com.weibo.talentintroduction.discovery.sample

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.BoundedHttpExecutor
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.service.JatsXmlEmailParser
import com.weibo.talentintroduction.discovery.service.PdfEmailExtractor
import com.weibo.talentintroduction.discovery.service.PlainTextEmailExtractor
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.*
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.system.exitProcess

/** Test-classpath-only CLI. No Spring context, sample writer, HTTP client or business repository. */
object DiscoverySampleReplay {
    private const val METADATA_LIMIT = 1_048_576
    private const val BODY_LIMIT = 10_485_760
    private val hex = Regex("[0-9a-f]{64}")
    private val mapper = jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
    internal var beforeBodyRead: ((Path) -> Unit)? = null

    data class Problem(val sampleId: String?, val attempt: Int?, val code: String)
    private class Invalid(val code: String, val sequence: Int? = null) : RuntimeException(code)
    private data class Loaded(val manifest: DiscoverySampleManifest, val directory: Path) {
        val savedBodies: Int get() = manifest.attempts.count { it.bodyStatus == DiscoverySampleBodyStatus.SAVED }
    }
    private data class Scan(val root: Path, val packages: List<Loaded>, val errors: List<Problem>)
    data class BodyResult(val sampleId: String, val attempt: Int, val inputSha256: String,
        val bodySha256: String?, val parserConfig: Map<String, Any?>, val status: String,
        val bindings: List<AuthorEmail>, val duplicateBindings: Int, val parseFailure: String?,
        val elapsedMs: Long, val networkCalls: Int) {
        val parserCalls: Int get() = if (status == "PARSED" || status == "PARSE_ERROR") 1 else 0
    }
    data class Provenance(val codeHead: String, val dirtyDiffHash: String, val sourceTreeSha256: String,
        val classesRoot: String, val classesSha256: String, val classCount: Int,
        val testClassesRoot: String, val testClassesSha256: String, val testClassCount: Int,
        val javaVersion: String, val classpathSha256: String, val cleanBuild: Boolean)
    data class ReplayReport(val reportVersion: Int = 1, val scope: String = "PARSER_ONLY",
        val samplesRoot: String, val generatedAt: String = Instant.now().toString(), val provenance: Provenance,
        val bodies: List<BodyResult>, val errors: List<Problem>, val productionObservations: List<Map<String, Any?>>,
        val accuracy: Any? = null, val labelledBodies: Int = 0,
        val limitations: String = "Offline single-body parser only; deadline=null; no retries/fallback reconstruction, OpenAlex postprocessing, network improvement or population accuracy inference.")

    @JvmStatic fun main(args: Array<String>) {
        // Production parser debug logs can contain URLs. Only fixed CLI diagnostics go to the console.
        (org.slf4j.LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)
            ?.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)?.level = ch.qos.logback.classic.Level.OFF
        exitProcess(run(args))
    }

    fun run(args: Array<out String>): Int {
        val options = try { arguments(args) } catch (_: IllegalArgumentException) {
            System.err.println("ARGUMENT_ERROR"); return 64
        }
        val command = args[0]
        val output = Paths.get(options.getValue("--output"))
        return try {
            when (command) {
                "list", "replay" -> {
                    val root = canonicalRoot(Paths.get(options.getValue("--samples")))
                    protectOutput(output, listOf(root))
                    val scan = scan(root, options["--execution-id"]?.toLong())
                    if (command == "list") {
                        val groups = scan.packages.groupBy { it.manifest.context.executionId to it.manifest.bucket.name }
                            .toSortedMap(compareBy<Pair<Long, String>> { it.first }.thenBy { it.second }).map { (key, values) ->
                                mapOf("executionId" to key.first, "bucket" to key.second, "savedSamples" to values.size,
                                    "savedBodies" to values.sumOf { it.savedBodies }, "metadataOnly" to values.count { it.savedBodies == 0 },
                                    "truncatedSamples" to values.count { it.manifest.attemptsTruncated },
                                    "retainedBytes" to values.sumOf { it.manifest.bodyBytesRetained }) }
                        writeReport(output, mapOf("reportVersion" to 1, "scope" to "PARSER_ONLY", "samplesRoot" to root.toString(),
                            "groups" to groups, "savedSamples" to scan.packages.size, "savedBodies" to scan.packages.sumOf { it.savedBodies },
                            "invalidSamples" to scan.errors, "limitations" to "Currently retained packages/bodies only; not people or historical failure rates."))
                        if (scan.errors.isEmpty()) 0 else 2
                    } else {
                        val report = replay(scan)
                        writeReport(output, report)
                        if (report.errors.isEmpty()) 0 else 2
                    }
                }
                else -> compare(Paths.get(options.getValue("--before")), Paths.get(options.getValue("--after")), output,
                    options["--labels"]?.let { Paths.get(it) })
            }
        } catch (error: Invalid) {
            // Never serialize exception messages (which can contain original URLs, emails or paths).
            System.err.println(error.code)
            2
        } catch (_: Exception) { System.err.println("IO_OR_REPORT_ERROR"); 2 }
    }

    private fun arguments(args: Array<out String>): Map<String, String> {
        require(args.isNotEmpty() && args[0] in setOf("list", "replay", "compare"))
        val allowed = if (args[0] == "compare") setOf("--before", "--after", "--output", "--labels")
            else setOf("--samples", "--execution-id", "--output")
        val required = if (args[0] == "compare") setOf("--before", "--after", "--output") else setOf("--samples", "--output")
        require(args.size % 2 == 1)
        val options = linkedMapOf<String, String>()
        for (index in 1 until args.size step 2) {
            require(args[index] in allowed && args[index + 1].isNotBlank() && !options.containsKey(args[index]))
            options[args[index]] = args[index + 1]
        }
        require(options.keys.containsAll(required))
        options["--execution-id"]?.let { require(it.matches(Regex("[1-9][0-9]*")) && (it.toLongOrNull() ?: 0) > 0) }
        return options
    }

    private fun canonicalRoot(path: Path): Path {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw Invalid("INVALID_PATH")
        return path.toRealPath()
    }
    private fun resolvedOutput(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        var parent = absolute
        val suffix = mutableListOf<Path>()
        while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            suffix.add(parent.fileName); parent = parent.parent ?: throw Invalid("INVALID_PATH")
        }
        var result = parent.toRealPath()
        for (part in suffix.asReversed()) result = result.resolve(part)
        return result
    }
    private fun protectOutput(output: Path, roots: List<Path>, inputs: List<Path> = emptyList()) {
        val resolved = resolvedOutput(output)
        if (roots.any { resolved.startsWith(it) } || inputs.any { resolved == it.toRealPath() }) throw Invalid("OUTPUT_INSIDE_INPUT")
    }
    private fun readBounded(path: Path, max: Int, tooLarge: String): ByteArray {
        if (Files.isSymbolicLink(path)) throw Invalid("INVALID_PATH")
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw NoSuchFileException(path.toString())
            throw Invalid("INVALID_PATH")
        }
        if (Files.size(path) > max) throw Invalid(tooLarge)
        Files.newByteChannel(path, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = java.nio.ByteBuffer.allocate(8192)
            while (channel.read(buffer) != -1) {
                if (output.size() + buffer.position() > max) throw Invalid(tooLarge)
                output.write(buffer.array(), 0, buffer.position()); buffer.clear()
            }
            return output.toByteArray()
        }
    }
    private fun readPackageFile(dir: Path, name: String, max: Int, tooLarge: String): ByteArray {
        // macOS/JDK11 does not provide SecureDirectoryStream. Validate the entire canonical
        // directory chain and immutable file identities on both sides of a NOFOLLOW read.
        val directories = listOf(dir.parent.parent, dir.parent, dir)
        fun directoryKeys(): List<Any> = directories.map { path ->
            val attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS)
            if (!attrs.isDirectory || attrs.isSymbolicLink || path.toRealPath() != path) throw Invalid("INVALID_PATH")
            attrs.fileKey() ?: throw Invalid("INVALID_PATH")
        }
        val keys = directoryKeys()
        val path = dir.resolve(name)
        val before = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!before.isRegularFile || before.isSymbolicLink) throw Invalid("INVALID_PATH")
        val bytes = readBounded(path, max, tooLarge)
        val after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (directoryKeys() != keys || before.fileKey() != after.fileKey() || !after.isRegularFile ||
            before.size() != after.size() || before.lastModifiedTime() != after.lastModifiedTime())
            throw Invalid("EXPIRED_OR_REMOVED")
        return bytes
    }
    private fun children(path: Path): List<Path> = Files.newDirectoryStream(path).use { it.toList().sortedBy { p -> p.fileName.toString() } }
    private fun scan(root: Path, executionId: Long?): Scan {
        val packages = mutableListOf<Loaded>(); val errors = mutableListOf<Problem>()
        for (execution in children(root)) {
            val executionName = execution.fileName.toString()
            if (!executionName.matches(Regex("[1-9][0-9]*")) || executionName.toLongOrNull() == null) continue
            if (executionId != null && executionName != executionId.toString()) continue
            if (Files.isSymbolicLink(execution) || !Files.isDirectory(execution, LinkOption.NOFOLLOW_LINKS)) {
                errors += Problem(null, null, "INVALID_PATH"); continue
            }
            val directories = try { children(execution) } catch (_: NoSuchFileException) {
                errors += Problem(null, null, "EXPIRED_OR_REMOVED"); continue
            }
            for (dir in directories) {
                val id = dir.fileName.toString()
                if (!hex.matches(id)) continue // .tmp and unknown names are not canonical committed packages.
                try {
                    if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw Invalid("INVALID_PATH")
                    packages += load(dir, executionName.toLong(), id)
                } catch (error: Invalid) { errors += Problem(id, error.sequence, error.code) }
                catch (_: NoSuchFileException) { errors += Problem(id, null, "EXPIRED_OR_REMOVED") }
                catch (_: Exception) { errors += Problem(id, null, "INVALID_SCHEMA") }
            }
        }
        return Scan(root, packages, errors)
    }

    private fun requireFields(node: JsonNode, names: String) {
        if (!node.isObject || names.split(' ').any { !node.has(it) }) throw Invalid("INVALID_SCHEMA")
    }
    private fun load(dir: Path, execution: Long, id: String): Loaded {
        val tree = mapper.readTree(readPackageFile(dir, "sample.json", METADATA_LIMIT, "METADATA_LIMIT"))
        if (!tree.path("schemaVersion").isIntegralNumber || !tree["schemaVersion"].canConvertToInt() ||
            tree["schemaVersion"].intValue() != 1) throw Invalid("UNSUPPORTED_SCHEMA")
        requireFields(tree, "schemaVersion stage sampleId inputSha256 capturedAt context paper urlFingerprints bucket unresolvedAuthorEmailCount outcome extractionExceptionClass identityExtractionVersion attempts attemptsTruncated bodyBytesRetained")
        requireFields(tree["context"], "executionId source entry sourceBatchNumber queueJobId")
        requireFields(tree["paper"], "pmcId pmid doi title pubYear journal authors source fullText downloadUrl candidateDownloadUrls")
        for (field in listOf("attempts", "urlFingerprints")) if (!tree[field].isArray) throw Invalid("INVALID_SCHEMA")
        if (!tree["paper"]["authors"].isArray || !tree["paper"]["candidateDownloadUrls"].isArray) throw Invalid("INVALID_SCHEMA")
        for (author in tree["paper"]["authors"]) requireFields(author,
            "givenNames familyNames orcidId affiliation isCorresponding email institutionType openAlexAuthorId institutionName institutionCountry institutionSource")
        if (tree["attempts"].size() > 16) throw Invalid("ATTEMPT_LIMIT")
        for (attempt in tree["attempts"]) {
            requireFields(attempt, "sequence kind url urlSha256 elapsedMs httpStatus contentType exceptionClass bodyStatus bodyFile bodySha256 bodyBytes pdfSettings")
            if (!attempt["pdfSettings"].isNull) requireFields(attempt["pdfSettings"],
                "maxPdfSizeBytes downloadTimeoutMs maxPages tailPages blacklistPrefixes maxRetries retryBackoffMs htmlFallbackEnabled")
        }
        // No numeric/string or boolean coercion: compare canonical typed serialization back to the supplied JSON.
        val m = mapper.treeToValue(tree, DiscoverySampleManifest::class.java)
        if (!DiscoverySampleJson.canonical(m).contentEquals(DiscoverySampleJson.canonical(tree))) throw Invalid("INVALID_SCHEMA")
        if (m.stage != "EXTRACTION" || m.sampleId != id || !m.context.valid() || m.context.executionId != execution ||
            !hex.matches(m.inputSha256) || m.unresolvedAuthorEmailCount < 0 || m.bodyBytesRetained < 0) throw Invalid("INVALID_SCHEMA")
        Instant.parse(m.capturedAt)
        if (m.paper != DiscoverySampleJson.paper(m.paper) || m.urlFingerprints.size != m.paper.candidateDownloadUrls.size + 1 ||
            m.urlFingerprints.any { it != null && !hex.matches(it) }) throw Invalid("INVALID_SCHEMA")
        if (DiscoverySampleJson.hash(DiscoverySampleJson.canonical(m.paper)) != m.inputSha256 ||
            DiscoverySampleJson.hash("$execution\n${m.context.source}\n${m.inputSha256}") != id) throw Invalid("HASH_MISMATCH")
        var previous = 0; var saved = 0; var retained = 0L
        val bodyFiles = mutableSetOf<String>()
        for (a in m.attempts) {
            if (a.sequence <= previous || a.elapsedMs < 0 || !hex.matches(a.urlSha256) || a.url != DiscoverySampleJson.scrub(a.url)) throw Invalid("INVALID_SCHEMA", a.sequence)
            previous = a.sequence
            val bodyFile = a.bodyFile
            val bodyHash = a.bodySha256
            val bodyBytes = a.bodyBytes
            if (a.kind == DiscoverySampleAttemptKind.PDF_URL && a.pdfSettings == null ||
                a.kind == DiscoverySampleAttemptKind.PMC_XML && a.pdfSettings != null) throw Invalid("INVALID_SCHEMA", a.sequence)
            when (a.bodyStatus) {
                DiscoverySampleBodyStatus.SAVED -> {
                    saved++
                    if (bodyFile == null || !bodyFile.matches(Regex("body-0[12]\\.bin")) || !bodyFiles.add(bodyFile)) throw Invalid("INVALID_PATH", a.sequence)
                    if (bodyHash == null || !hex.matches(bodyHash) || bodyBytes == null || bodyBytes !in 1..BODY_LIMIT.toLong()) throw Invalid("INVALID_SCHEMA", a.sequence)
                    retained += bodyBytes
                }
                DiscoverySampleBodyStatus.NOT_AVAILABLE -> if (bodyFile != null || bodyHash != null || bodyBytes != null) throw Invalid("INVALID_SCHEMA", a.sequence)
                DiscoverySampleBodyStatus.BODY_LIMIT -> if (bodyFile != null || bodyHash != null || bodyBytes == null || bodyBytes <= 0) throw Invalid("INVALID_SCHEMA", a.sequence)
            }
        }
        if (saved > 2 || retained != m.bodyBytesRetained) throw Invalid("BODY_LIMIT")
        for (a in m.attempts.filter { it.bodyStatus == DiscoverySampleBodyStatus.SAVED }) {
            try {
                val path = dir.resolve(a.bodyFile!!)
                beforeBodyRead?.invoke(path)
                validatedBody(dir, a)
            } catch (error: Invalid) { throw Invalid(error.code, a.sequence) }
            catch (_: NoSuchFileException) { throw Invalid("EXPIRED_OR_REMOVED", a.sequence) }
        }
        return Loaded(m, dir)
    }

    private fun validatedBody(dir: Path, a: DiscoverySampleAttempt): ByteArray {
        try {
            // Every parent is a canonical final directory; never follow a replaced/symlinked package.
            if (Files.isSymbolicLink(dir.parent) || Files.isSymbolicLink(dir) || dir.toRealPath() != dir)
                throw Invalid("INVALID_PATH", a.sequence)
            val bytes = readPackageFile(dir, a.bodyFile!!, BODY_LIMIT, "BODY_TOO_LARGE")
            if (bytes.size.toLong() != a.bodyBytes || DiscoverySampleJson.hash(bytes) != a.bodySha256)
                throw Invalid("HASH_MISMATCH", a.sequence)
            return bytes
        } catch (_: NoSuchFileException) { throw Invalid("EXPIRED_OR_REMOVED", a.sequence) }
    }

    internal fun sortedBindings(bindings: List<AuthorEmail>) = bindings.sortedBy { String(DiscoverySampleJson.canonical(it), Charsets.UTF_8) }
    internal fun rejectingRestTemplate(onRequest: () -> Unit = {}): RestTemplate =
        RestTemplate(org.springframework.http.client.ClientHttpRequestFactory { _, _ ->
            onRequest()
            throw Invalid("OFFLINE_NETWORK_FORBIDDEN")
        })
    internal class LocalBodyExecutor(private val url: String, private val bytes: ByteArray,
        private val status: Int?, private val contentType: String?) : BoundedHttpExecutor {
        var calls = 0; private set
        var violations = 0; private set
        private fun forbidden(): Nothing { violations++; throw Invalid("OFFLINE_NETWORK_FORBIDDEN") }
        override fun <T : Any> getForObject(base: RestTemplate, url: String, responseType: Class<T>,
            connectCapMs: Long, readCapMs: Long, deadline: Instant?): T? = forbidden()
        override fun <T> execute(base: RestTemplate, uri: URI, connectCapMs: Long, readCapMs: Long,
            deadline: Instant?, responseExtractor: ResponseExtractor<T>): T? {
            if (uri.toString() != url || deadline != null || calls != 0) forbidden()
            calls++
            return responseExtractor.extractData(object : ClientHttpResponse {
                override fun getHeaders() = HttpHeaders().also { h -> contentType?.let { h.set(HttpHeaders.CONTENT_TYPE, it) } }
                override fun getBody() = ByteArrayInputStream(bytes)
                override fun getRawStatusCode() = status ?: throw Invalid("INVALID_SCHEMA")
                override fun getStatusCode() = HttpStatus.valueOf(rawStatusCode)
                override fun getStatusText() = "OFFLINE_SAVED_RESPONSE"
                override fun close() {}
            })
        }
    }
    private fun config(a: DiscoverySampleAttempt): Map<String, Any?> = linkedMapOf("kind" to a.kind.name,
        "pdfSettings" to a.pdfSettings, "httpStatus" to a.httpStatus, "contentType" to a.contentType,
        "deadline" to null, "postprocessing" to "NONE")
    private val parserMonitor = Any()
    private fun <T> quietParser(block: () -> T): T = synchronized(parserMonitor) {
        // JAXP's default error handler writes source-derived diagnostics to stderr before throwing.
        val original = System.err
        java.io.PrintStream(java.io.OutputStream.nullOutputStream()).use { sink ->
            try { System.setErr(sink); block() } finally { System.setErr(original) }
        }
    }
    private fun replay(scan: Scan): ReplayReport {
        val results = mutableListOf<BodyResult>(); val errors = scan.errors.toMutableList()
        for (loaded in scan.packages) {
            val m = loaded.manifest
            for (a in m.attempts) {
                val start = System.nanoTime()
                val bytes = if (a.bodyStatus == DiscoverySampleBodyStatus.SAVED) try {
                    validatedBody(loaded.directory, a)
                } catch (error: Invalid) {
                    errors += Problem(m.sampleId, a.sequence, error.code)
                    results += BodyResult(m.sampleId, a.sequence, m.inputSha256, a.bodySha256, config(a),
                        error.code, emptyList(), 0, null, 0, 0)
                    continue
                } else null
                var failure: String? = null; var bindings = emptyList<AuthorEmail>()
                var networkCalls = 0
                var status = if (bytes != null) "PARSED" else if (a.bodyStatus == DiscoverySampleBodyStatus.BODY_LIMIT) "BODY_LIMIT" else "NO_BODY"
                if (bytes != null) try { quietParser {
                    if (a.kind == DiscoverySampleAttemptKind.PMC_XML) bindings = JatsXmlEmailParser.parse(bytes)
                    else {
                        val executor = LocalBodyExecutor(a.url, bytes, a.httpStatus, a.contentType)
                        val rest = rejectingRestTemplate { networkCalls++ }
                        val outcome = PdfEmailExtractor(rest, PlainTextEmailExtractor(), a.pdfSettings!!, executor)
                            .extract(a.url, m.paper.authors, "OFFLINE", deadline = null)
                        bindings = outcome.emails; failure = outcome.failureReason
                        if (executor.calls != 1 || executor.violations != 0 || networkCalls != 0)
                            throw Invalid("OFFLINE_NETWORK_FORBIDDEN", a.sequence)
                    }
                    }
                } catch (error: Exception) {
                    val code = if (error is Invalid) error.code else "PARSER_EXCEPTION"
                    status = "PARSE_ERROR"; failure = code
                    errors += Problem(m.sampleId, a.sequence, code)
                }
                results += BodyResult(m.sampleId, a.sequence, m.inputSha256, a.bodySha256, config(a), status,
                    sortedBindings(bindings), bindings.size - bindings.distinct().size, failure, (System.nanoTime() - start) / 1_000_000, networkCalls)
            }
            if (m.attempts.isEmpty()) results += BodyResult(m.sampleId, 0, m.inputSha256, null, emptyMap(), "NO_BODY", emptyList(), 0, null, 0, 0)
        }
        return ReplayReport(samplesRoot = scan.root.toString(), provenance = provenance(), bodies = results.sortedWith(compareBy<BodyResult> { it.sampleId }.thenBy { it.attempt }),
            errors = errors, productionObservations = scan.packages.map { mapOf("sampleId" to it.manifest.sampleId,
                "outcome" to it.manifest.outcome, "extractionExceptionClass" to it.manifest.extractionExceptionClass) })
    }

    private fun git(root: Path, vararg args: String): ByteArray {
        val process = ProcessBuilder(listOf("git", "-C", root.toString()) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = process.inputStream.readBytes()
        if (process.waitFor() != 0) throw Invalid("PROVENANCE_UNAVAILABLE")
        return bytes
    }
    private fun aggregate(root: Path, files: List<Path>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (path in files.sortedBy { root.relativize(it).toString() }) {
            digest.update(root.relativize(path).toString().toByteArray(Charsets.UTF_8)); digest.update(0.toByte())
            Files.newInputStream(path).use { input -> val buffer = ByteArray(8192); while (true) {
                val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size)
            } }
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun provenance(): Provenance {
        val root = Paths.get(System.getProperty("replay.repository", System.getProperty("user.dir"))).toRealPath()
        val classes = Paths.get(PdfEmailExtractor::class.java.protectionDomain.codeSource.location.toURI()).toRealPath()
        if (classes != root.resolve("target/classes").toRealPath()) throw Invalid("PROVENANCE_UNAVAILABLE")
        val classFiles = Files.walk(classes).use { stream -> stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }.toArray().map { it as Path } }
        if (classFiles.isEmpty()) throw Invalid("PROVENANCE_UNAVAILABLE")
        val sources = listOf("src/main", "src/test", "scripts/expert_discovery/replay_samples.sh", "pom.xml")
        val diff = git(root, "diff", "--binary", "HEAD", "--", *sources.toTypedArray())
        val untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z", "--", *sources.toTypedArray())
            .toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }.map { root.resolve(it) }
        val dirty = MessageDigest.getInstance("SHA-256").apply { update(diff); update(aggregate(root, untracked).toByteArray()) }
            .digest().joinToString("") { "%02x".format(it) }
        val sourceFiles = sources.flatMap { name -> val path = root.resolve(name)
            if (Files.isDirectory(path)) Files.walk(path).use { it.filter { p -> Files.isRegularFile(p) }.toArray().map { p -> p as Path } }
            else listOf(path) }
        val testClasses = Paths.get(DiscoverySampleReplay::class.java.protectionDomain.codeSource.location.toURI()).toRealPath()
        if (testClasses != root.resolve("target/test-classes").toRealPath()) throw Invalid("PROVENANCE_UNAVAILABLE")
        val testClassFiles = Files.walk(testClasses).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }.toArray().map { it as Path } }
        return Provenance(git(root, "rev-parse", "HEAD").toString(Charsets.UTF_8).trim(), dirty, aggregate(root, sourceFiles),
            classes.toString(), aggregate(classes, classFiles), classFiles.size,
            testClasses.toString(), aggregate(testClasses, testClassFiles), testClassFiles.size, System.getProperty("java.version"),
            DiscoverySampleJson.hash(System.getProperty("java.class.path")), System.getProperty("replay.cleanBuild") == "true")
    }

    private fun report(path: Path): JsonNode {
        // Reports may cover 1,000 packages; body/metadata input limits remain enforced separately.
        val node = mapper.readTree(readBounded(path, 128 * METADATA_LIMIT, "REPORT_LIMIT"))
        requireFields(node, "reportVersion scope samplesRoot provenance bodies errors")
        return node
    }
    private fun key(body: JsonNode) = body["sampleId"].asText() + ":" + body["attempt"].asText()
    private fun triple(binding: JsonNode): List<String?> = listOf(binding["email"].asText(), binding["givenNames"].takeUnless { it.isNull }?.asText(), binding["familyNames"].takeUnless { it.isNull }?.asText())
    private fun email(binding: JsonNode) = binding["email"].asText().trim().lowercase(Locale.ROOT)
    private fun diagnostics(bodies: Collection<JsonNode>): Map<String, Int> {
        val bindings = bodies.flatMap { it["bindings"].toList() }
        return mapOf("identityLoss" to bindings.count { it["givenNames"].isNull || it["givenNames"].asText().isBlank() ||
            it["familyNames"].isNull || it["familyNames"].asText().isBlank() },
            "nameConflicts" to bodies.sumOf { body -> body["bindings"].groupBy(::email).count { (_, values) ->
                values.map { triple(it).drop(1) }.distinct().size > 1 } },
            "duplicateBindings" to bodies.sumOf { body -> body["bindings"].size() - body["bindings"].toSet().size })
    }
    private fun metrics(bodies: List<JsonNode>, labels: Map<Pair<String, String>, Set<List<String?>>>): Map<String, Any?> {
        var tp = 0; var fp = 0; var fn = 0; var loss = 0; var conflicts = 0
        // Count every labelled replay body; a shared SHA must not arbitrarily choose one attempt/config.
        for (body in bodies) {
            val expected = labels[body["inputSha256"].asText() to body["bodySha256"].asText()] ?: continue
            val predicted = body["bindings"].map(::triple).toSet()
            tp += predicted.intersect(expected).size; fp += (predicted - expected).size; fn += (expected - predicted).size
            loss += predicted.count { it[1].isNullOrBlank() || it[2].isNullOrBlank() }
            conflicts += predicted.groupBy { it[0]?.trim()?.lowercase(Locale.ROOT) }.count { (_, values) -> values.map { it.drop(1) }.distinct().size > 1 }
        }
        return linkedMapOf("TP" to tp, "FP" to fp, "FN" to fn, "precision" to if (tp + fp == 0) null else tp.toDouble() / (tp + fp),
            "recall" to if (tp + fn == 0) null else tp.toDouble() / (tp + fn), "identityLoss" to loss, "nameConflicts" to conflicts)
    }
    private fun compare(before: Path, after: Path, output: Path, labelsPath: Path?): Int {
        val a = report(before); val b = report(after)
        val roots = listOf(a, b).map { Paths.get(it["samplesRoot"].asText()).toAbsolutePath().normalize() }
        protectOutput(output, roots.map { if (Files.exists(it)) it.toRealPath() else resolvedOutput(it) }, listOfNotNull(before, after, labelsPath))
        val base = linkedMapOf<String, Any?>("reportVersion" to 1, "scope" to "PARSER_ONLY", "accuracy" to null, "labelledBodies" to 0,
            "beforeProvenance" to a["provenance"], "afterProvenance" to b["provenance"],
            "limitations" to "Differences are not correctness. Metrics, when supplied, cover only exact manually labelled inputs/bodies; not sampled-population or network improvement.")
        fun mismatch(): Int { base["status"] = "INPUT_MISMATCH"; writeReport(output, base); return 2 }
        try {
            if (listOf(a, b).any { !it["reportVersion"].isIntegralNumber || !it["reportVersion"].canConvertToInt() ||
                    it["reportVersion"].intValue() != 1 || it["scope"].asText() != "PARSER_ONLY" ||
                    !it["bodies"].isArray || !it["errors"].isArray }) return mismatch()
            if (!a["errors"].isEmpty || !b["errors"].isEmpty) return mismatch()
            fun indexed(node: JsonNode): Map<String, JsonNode> {
                val result = linkedMapOf<String, JsonNode>()
                for (body in node["bodies"]) {
                    requireFields(body, "sampleId attempt inputSha256 bodySha256 parserConfig status bindings duplicateBindings parseFailure elapsedMs networkCalls")
                    if (!hex.matches(body["sampleId"].asText()) || !body["attempt"].isIntegralNumber || !hex.matches(body["inputSha256"].asText()) ||
                        !body["bindings"].isArray || body["status"].asText() !in setOf("PARSED", "NO_BODY", "BODY_LIMIT")) throw Invalid("INPUT_MISMATCH")
                    if (body["status"].asText() == "PARSED" && !hex.matches(body["bodySha256"].asText())) throw Invalid("INPUT_MISMATCH")
                    for (binding in body["bindings"]) {
                        val value = mapper.treeToValue(binding, AuthorEmail::class.java)
                        if (!DiscoverySampleJson.canonical(value).contentEquals(DiscoverySampleJson.canonical(binding))) throw Invalid("INPUT_MISMATCH")
                    }
                    if (result.put(key(body), body) != null) throw Invalid("INPUT_MISMATCH")
                }
                return result
            }
            val left = indexed(a); val right = indexed(b)
            if (left.keys != right.keys || left.any { (key, value) -> listOf("inputSha256", "bodySha256", "parserConfig", "status").any { value[it] != right.getValue(key)[it] } }) return mismatch()
            val successful = left.values.filter { it["status"].asText() == "PARSED" }
            val labels = linkedMapOf<Pair<String, String>, Set<List<String?>>>()
            if (labelsPath != null) {
                val labelTree = mapper.readTree(readBounded(labelsPath, 16 * METADATA_LIMIT, "LABEL_LIMIT"))
                requireFields(labelTree, "reportVersion bodies")
                if (!labelTree["reportVersion"].isIntegralNumber || !labelTree["reportVersion"].canConvertToInt() ||
                    labelTree["reportVersion"].intValue() != 1 || !labelTree["bodies"].isArray) return mismatch()
                val inputs = successful.map { it["inputSha256"].asText() to it["bodySha256"].asText() }.toSet()
                for (label in labelTree["bodies"]) {
                    requireFields(label, "inputSha256 bodySha256 bindings")
                    val key = label["inputSha256"].asText() to label["bodySha256"].asText()
                    if (key !in inputs || labels.containsKey(key) || !label["bindings"].isArray) return mismatch()
                    val triples = label["bindings"].map { binding ->
                        requireFields(binding, "email givenNames familyNames")
                        if (listOf("email", "givenNames", "familyNames").any { !binding[it].isTextual || binding[it].asText().isBlank() }) throw Invalid("INPUT_MISMATCH")
                        triple(binding)
                    }
                    if (triples.distinct().size != triples.size) return mismatch()
                    labels[key] = triples.toSet()
                }
            }
            var added = 0; var removed = 0; var changed = 0; var unchanged = 0
            val differences = mutableListOf<Map<String, Any?>>()
            val conflicts = mutableListOf<Map<String, Any?>>()
            for ((key, old) in left.toSortedMap()) {
                val new = right.getValue(key)
                val oldGroups = old["bindings"].groupBy(::email); val newGroups = new["bindings"].groupBy(::email)
                for (mail in (oldGroups.keys + newGroups.keys).sorted()) {
                    val previous = oldGroups[mail].orEmpty().sortedBy { String(DiscoverySampleJson.canonical(it), Charsets.UTF_8) }
                    val current = newGroups[mail].orEmpty().sortedBy { String(DiscoverySampleJson.canonical(it), Charsets.UTF_8) }
                    if (previous.size > 1 || current.size > 1) {
                        conflicts += mapOf("bodyKey" to key, "normalizedEmail" to mail, "before" to previous, "after" to current,
                            "beforeCount" to previous.size, "afterCount" to current.size, "reason" to "DUPLICATE_OR_AMBIGUOUS_EMAIL")
                        // Multiset exact matches only; never pair conflicting identities arbitrarily.
                        val remaining = current.toMutableList()
                        val lost = previous.filter { if (remaining.remove(it)) { unchanged++; false } else true }
                        removed += lost.size; added += remaining.size
                        if (lost.isNotEmpty() || remaining.isNotEmpty()) differences += mapOf("bodyKey" to key, "kind" to "AMBIGUOUS", "removed" to lost, "added" to remaining)
                    } else when {
                        previous.isEmpty() -> { added++; differences += mapOf("bodyKey" to key, "kind" to "ADDED", "after" to current.single()) }
                        current.isEmpty() -> { removed++; differences += mapOf("bodyKey" to key, "kind" to "REMOVED", "before" to previous.single()) }
                        previous.single() == current.single() -> unchanged++
                        else -> { changed++; differences += mapOf("bodyKey" to key, "kind" to "CHANGED", "before" to previous.single(), "after" to current.single()) }
                    }
                }
            }
            base.putAll(mapOf("status" to "COMPARABLE", "added" to added, "removed" to removed, "changed" to changed,
                "unchanged" to unchanged, "differences" to differences, "conflicts" to conflicts,
                "beforeDiagnostics" to diagnostics(left.values), "afterDiagnostics" to diagnostics(right.values),
                "nonParsedBodies" to left.values.filter { it["status"].asText() != "PARSED" },
                "labelledBodies" to successful.count { (it["inputSha256"].asText() to it["bodySha256"].asText()) in labels },
                "beforeMetrics" to if (labelsPath == null) null else metrics(successful, labels),
                "afterMetrics" to if (labelsPath == null) null else metrics(right.values.filter { it["status"].asText() == "PARSED" }, labels)))
            writeReport(output, base); return 0
        } catch (_: Exception) { return mismatch() }
    }
    private fun writeReport(path: Path, value: Any) {
        val output = path.toAbsolutePath().normalize()
        Files.createDirectories(output.parent)
        Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
}
