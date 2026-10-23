# 代码证据快照

日期：2026-10-23；基线 HEAD `c6e9709f349f32c5feb1d067b47c697b0dff05b6`。以下逐字来自当前工作树，SHA256 对整文件；不是未来实现。工作树已有未提交改动，执行前比对差异，不能重置。

## `src/main/kotlin/com/weibo/talentintroduction/config/DiscoverySampleProperties.kt`

SHA256: `25ff5df75bd0e5f2bbe4bf8d411ebabdd5f6018f6eec06b4bdf455948d72cb78`

行 1–35:

```text
1: package com.weibo.talentintroduction.config
2: 
3: import org.springframework.boot.context.properties.ConfigurationProperties
4: import org.springframework.boot.context.properties.EnableConfigurationProperties
5: import org.springframework.context.annotation.Configuration
6: import java.nio.file.Paths
7: 
8: @ConfigurationProperties("talent-introduction.expert-discovery.samples")
9: data class DiscoverySampleProperties(
10:     var enabled: Boolean = false,
11:     var directory: String = "/opt/talent/data/discovery-samples",
12:     var retentionDays: Int = 7,
13:     var maxTotalBytes: Long = 536870912,
14:     var maxTotalSamples: Int = 1000,
15:     var maxBodyBytes: Long = 10485760,
16:     var maxBodiesPerSample: Int = 2,
17:     var maxMetadataBytes: Int = 1048576,
18:     var maxAttemptsPerSample: Int = 16,
19:     var maxSamplesPerBucketPerRun: Int = 20,
20:     var maxSuccessControlsPerRun: Int = 10,
21:     var maxInFlightCaptures: Int = 2
22: ) {
23:     fun valid(): Boolean = try {
24:         Paths.get(directory).isAbsolute && retentionDays > 0 && maxTotalSamples > 0 &&
25:             maxBodyBytes in 1..10485760 && maxBodiesPerSample in 1..2 &&
26:             maxMetadataBytes in 1..1048576 && maxAttemptsPerSample in 1..16 &&
27:             maxSamplesPerBucketPerRun > 0 && maxSuccessControlsPerRun > 0 &&
28:             maxInFlightCaptures in 1..2 &&
29:             maxTotalBytes >= maxBodyBytes * maxBodiesPerSample + maxMetadataBytes
30:     } catch (_: Exception) { false }
31: }
32: 
33: @Configuration
34: @EnableConfigurationProperties(DiscoverySampleProperties::class)
35: class DiscoverySampleConfiguration
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleModels.kt`

SHA256: `c3287ed9b8b90ac49503e4558035f1e7416a63e203ee4a8498446c5a6884f9ed`

行 10–90:

```text
10: import java.net.URI
11: import java.security.MessageDigest
12: 
13: enum class DiscoverySampleEntry { SYNC, QUEUE }
14: data class DiscoverySampleContext(val executionId: Long, val source: String, val entry: DiscoverySampleEntry,
15:     val sourceBatchNumber: Int?, val queueJobId: Long?) {
16:     fun valid() = executionId > 0 && source == "OPENALEX" && when (entry) {
17:         DiscoverySampleEntry.SYNC -> (sourceBatchNumber ?: 0) > 0 && queueJobId == null
18:         DiscoverySampleEntry.QUEUE -> (queueJobId ?: 0) > 0 && sourceBatchNumber == null
19:     }
20: }
21: enum class DiscoverySampleBucket { EXTRACTION_EXCEPTION, IDENTITY_UNRESOLVED, NO_FULLTEXT_ADDRESS, NO_EMAIL,
22:     BODY_TOO_LARGE, DOWNLOAD_FAILED, PARSE_FAILED, OTHER_FAILURE, SUCCESS_CONTROL }
23: enum class DiscoverySampleBodyStatus { SAVED, NOT_AVAILABLE, BODY_LIMIT }
24: enum class DiscoverySampleAttemptKind { PDF_URL, PMC_XML }
25: enum class DiscoverySampleDecision { SAVED, DISABLED, INVALID_CONTEXT, CONFIG_ERROR, CAPTURE_BUSY, HOST_BUSY,
26:     DUPLICATE, QUOTA, METADATA_LIMIT, LOCK_BUSY, IO_ERROR, STORE_INVALID }
27: data class DiscoverySampleAttempt(val sequence: Int, val kind: DiscoverySampleAttemptKind, val url: String,
28:     val urlSha256: String, val elapsedMs: Long, val httpStatus: Int?, val contentType: String?,
29:     val exceptionClass: String?, val bodyStatus: DiscoverySampleBodyStatus, val bodyFile: String?,
30:     val bodySha256: String?, val bodyBytes: Long?, val pdfSettings: PdfExtractionProperties?)
31: data class DiscoverySampleManifest(val schemaVersion: Int = 1, val stage: String = "EXTRACTION",
32:     val sampleId: String, val inputSha256: String, val capturedAt: String, val context: DiscoverySampleContext,
33:     val paper: PaperMetadata, val urlFingerprints: List<String?>, val bucket: DiscoverySampleBucket,
34:     val unresolvedAuthorEmailCount: Int, val outcome: EmailExtractionOutcome?, val extractionExceptionClass: String?,
35:     val identityExtractionVersion: Int = DiscoveryIdentity.EXTRACTION_VERSION, val attempts: List<DiscoverySampleAttempt>,
36:     val attemptsTruncated: Boolean, val bodyBytesRetained: Long)
37: 
38: object DiscoverySampleJson {
39:     val mapper = jacksonObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
40:     /** Stop serialization before allocating an unbounded diagnostic JSON buffer. */
41:     fun bounded(value: Any, limit: Int): ByteArray? {
42:         val bytes = java.io.ByteArrayOutputStream(minOf(limit, 8192))
43:         var exceeded = false
44:         val output = object : java.io.OutputStream() {
45:             override fun write(value: Int) {
46:                 if (bytes.size() >= limit) { exceeded = true; throw java.io.IOException("metadata limit") }
47:                 bytes.write(value)
48:             }
49:             override fun write(buffer: ByteArray, offset: Int, length: Int) {
50:                 if (length > limit - bytes.size()) { exceeded = true; throw java.io.IOException("metadata limit") }
51:                 bytes.write(buffer, offset, length)
52:             }
53:         }
54:         try { mapper.writeValue(output, value) }
55:         catch (error: Exception) { if (!exceeded) throw error }
56:         return if (exceeded) null else bytes.toByteArray()
57:     }
58:     fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
59:         .joinToString("") { "%02x".format(it) }
60:     fun hash(text: String) = hash(text.toByteArray(Charsets.UTF_8))
61:     private fun sorted(node: JsonNode): JsonNode = when {
62:         node.isObject -> mapper.createObjectNode().also { out -> node.fieldNames().asSequence().sorted().forEach {
63:             out.set<JsonNode>(it, sorted(node[it])) } }
64:         node.isArray -> mapper.createArrayNode().also { out -> node.forEach { out.add(sorted(it)) } }
65:         else -> node
66:     }
67:     fun canonical(value: Any): ByteArray = mapper.writeValueAsBytes(sorted(mapper.valueToTree(value)))
68:     fun scrub(url: String): String = try {
69:         val uri = URI(url)
70:         if (uri.isOpaque) uri.scheme + ":" + uri.rawSchemeSpecificPart.substringBefore('?').substringBefore('#')
71:         else buildString {
72:             uri.scheme?.let { append(it).append(':') }
73:             uri.rawAuthority?.let { append("//").append(it.substringAfterLast('@')) }
74:             append(uri.rawPath.orEmpty())
75:         }
76:     } catch (_: Exception) { "" }
77:     fun paper(paper: PaperMetadata) = paper.copy(downloadUrl = paper.downloadUrl?.let(::scrub),
78:         candidateDownloadUrls = paper.candidateDownloadUrls.map(::scrub))
79:     fun bucket(outcome: EmailExtractionOutcome?, error: Throwable?): DiscoverySampleBucket = when {
80:         error != null -> DiscoverySampleBucket.EXTRACTION_EXCEPTION
81:         outcome!!.emails.any { it.givenNames.isNullOrBlank() || it.familyNames.isNullOrBlank() } -> DiscoverySampleBucket.IDENTITY_UNRESOLVED
82:         outcome.failureReason == "NO_PMC_ID" -> DiscoverySampleBucket.NO_FULLTEXT_ADDRESS
83:         outcome.failureReason?.startsWith("NO_EMAIL_IN_") == true -> DiscoverySampleBucket.NO_EMAIL
84:         outcome.failureReason == "PDF_TOO_LARGE" -> DiscoverySampleBucket.BODY_TOO_LARGE
85:         outcome.failureReason in listOf("PDF_DOWNLOAD_FAILED", "FULLTEXT_FETCH_FAILED") -> DiscoverySampleBucket.DOWNLOAD_FAILED
86:         outcome.failureReason in listOf("PDF_PARSE_FAILED", "XML_PARSE_FAILED") -> DiscoverySampleBucket.PARSE_FAILED
87:         !outcome.failureReason.isNullOrEmpty() || outcome.emails.isEmpty() -> DiscoverySampleBucket.OTHER_FAILURE
88:         else -> DiscoverySampleBucket.SUCCESS_CONTROL
89:     }
90: }
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStore.kt`

SHA256: `6c52af7f1e5ec70098d594304ec0b3598cc62547a7cfd2bcad44bb3e66d63127`

行 117–204:

```text
117:     private fun locked(action: () -> DiscoverySampleDecision): DiscoverySampleDecision {
118:         if (!properties.enabled) return DiscoverySampleDecision.DISABLED
119:         if (!properties.valid()) return decision(DiscoverySampleDecision.CONFIG_ERROR)
120:         val lock = locks.computeIfAbsent(root) { ReentrantLock() }
121:         if (!lock.tryLock()) return decision(DiscoverySampleDecision.LOCK_BUSY)
122:         try {
123:             prepareRoot()
124:             val lockFile = root.resolve(".writer.lock")
125:             if (Files.isSymbolicLink(lockFile)) throw InvalidStore()
126:             if (!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) write(lockFile, byteArrayOf())
127:             FileChannel.open(lockFile, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
128:                 val fileLock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
129:                 if (fileLock == null) return decision(DiscoverySampleDecision.LOCK_BUSY)
130:                 fileLock.use { return decision(action()) }
131:             }
132:         } catch (_: InvalidStore) { return decision(DiscoverySampleDecision.STORE_INVALID) }
133:         catch (_: Exception) { return decision(DiscoverySampleDecision.IO_ERROR) }
134:         finally { lock.unlock() }
135:     }
136:     private fun retained(): MutableList<Package> {
137:         val packages = scan()
138:         val cutoff = clock.instant().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
139:         val expired = packages.filter { Instant.parse(it.manifest.capturedAt).isBefore(cutoff) }
140:         expired.forEach { remove(it.path); reclaimEmptyExecution(it.path.parent) }; packages.removeAll(expired.toSet())
141:         return packages
142:     }
143:     @PostConstruct
144:     fun startup() { cleanup() }
145:     @Scheduled(fixedDelay = 3600000)
146:     fun cleanup() { if (properties.enabled) locked { retained(); DiscoverySampleDecision.SAVED } }
147: 
148:     fun commit(manifest: DiscoverySampleManifest, bodies: Map<String, ByteArray>): DiscoverySampleDecision = locked {
149:         if (!manifest.context.valid()) return@locked DiscoverySampleDecision.INVALID_CONTEXT
150:         val json = DiscoverySampleJson.bounded(manifest, properties.maxMetadataBytes)
151:             ?: return@locked DiscoverySampleDecision.METADATA_LIMIT
152:         if (manifest.schemaVersion != 1 || manifest.stage != "EXTRACTION" ||
153:             manifest.inputSha256 != DiscoverySampleJson.hash(DiscoverySampleJson.canonical(manifest.paper)) ||
154:             manifest.sampleId != DiscoverySampleJson.hash("${manifest.context.executionId}\n${manifest.context.source}\n${manifest.inputSha256}"))
155:             return@locked DiscoverySampleDecision.STORE_INVALID
156:         val expected = manifest.attempts.filter { it.bodyStatus == DiscoverySampleBodyStatus.SAVED }
157:         if (expected.size > properties.maxBodiesPerSample || manifest.attempts.size > properties.maxAttemptsPerSample ||
158:             expected.map { it.bodyFile }.toSet() != bodies.keys || manifest.bodyBytesRetained != bodies.values.sumOf { it.size.toLong() })
159:             return@locked DiscoverySampleDecision.STORE_INVALID
160:         if (manifest.attempts.map { it.sequence } != (1..manifest.attempts.size).toList() ||
161:             manifest.attempts.any { attempt ->
162:                 attempt.elapsedMs < 0 || attempt.url != DiscoverySampleJson.scrub(attempt.url) ||
163:                     when (attempt.bodyStatus) {
164:                         DiscoverySampleBodyStatus.SAVED -> attempt.bodyFile == null || attempt.bodySha256 == null || attempt.bodyBytes == null
165:                         DiscoverySampleBodyStatus.NOT_AVAILABLE -> attempt.bodyFile != null || attempt.bodySha256 != null || attempt.bodyBytes != null
166:                         DiscoverySampleBodyStatus.BODY_LIMIT -> attempt.bodyFile != null || attempt.bodySha256 != null || attempt.bodyBytes == null || attempt.bodyBytes < 0
167:                     }
168:             } || manifest.paper != DiscoverySampleJson.paper(manifest.paper))
169:             return@locked DiscoverySampleDecision.STORE_INVALID
170:         for (attempt in expected) {
171:             val body = bodies[attempt.bodyFile] ?: return@locked DiscoverySampleDecision.STORE_INVALID
172:             if (!attempt.bodyFile.orEmpty().matches(Regex("body-0[12]\\.bin")) || body.size > properties.maxBodyBytes ||
173:                 body.size.toLong() != attempt.bodyBytes || DiscoverySampleJson.hash(body) != attempt.bodySha256)
174:                 return@locked DiscoverySampleDecision.STORE_INVALID
175:         }
176:         val packages = retained()
177:         if (packages.any { it.manifest.sampleId == manifest.sampleId }) return@locked DiscoverySampleDecision.DUPLICATE
178:         val limit = if (manifest.bucket == DiscoverySampleBucket.SUCCESS_CONTROL) properties.maxSuccessControlsPerRun else properties.maxSamplesPerBucketPerRun
179:         if (packages.count { it.manifest.context.executionId == manifest.context.executionId && it.manifest.bucket == manifest.bucket } >= limit)
180:             return@locked DiscoverySampleDecision.QUOTA
181:         val needed = json.size + manifest.bodyBytesRetained
182:         for (old in packages.sortedBy { Instant.parse(it.manifest.capturedAt) }) {
183:             if (packages.size < properties.maxTotalSamples && packages.sumOf { it.bytes } + needed <= properties.maxTotalBytes) break
184:             remove(old.path); reclaimEmptyExecution(old.path.parent); packages.remove(old)
185:         }
186:         if (Files.getFileStore(root).usableSpace < needed) return@locked DiscoverySampleDecision.IO_ERROR
187:         val execution = root.resolve(manifest.context.executionId.toString())
188:         if (!Files.exists(execution)) privateDirectory(execution)
189:         val temporary = root.resolve(".tmp-${UUID.randomUUID()}")
190:         try {
191:             privateDirectory(temporary)
192:             // The execution parent is reclaimed even if temporary-directory creation fails.
193:             bodies.forEach { (name, bytes) -> write(temporary.resolve(name), bytes) }
194:             write(temporary.resolve("sample.json"), json)
195:             bodies.forEach { (name, bytes) -> if (DiscoverySampleJson.hash(Files.readAllBytes(temporary.resolve(name))) != DiscoverySampleJson.hash(bytes)) throw InvalidStore() }
196:             atomicMove(temporary, execution.resolve(manifest.sampleId))
197:             logger.info("event=DISCOVERY_SAMPLE_CAPTURE decision=SAVED executionId={} sampleId={} bucket={}", manifest.context.executionId, manifest.sampleId, manifest.bucket)
198:             DiscoverySampleDecision.SAVED
199:         } finally {
200:             try { if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) remove(temporary) }
201:             finally { reclaimEmptyExecution(execution) }
202:         }
203:     }
204: }
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`

SHA256: `73ff71a6b7ad3ae2952cdab03436f31ee893b5a021bd992083907be21e32b3bb`

行 932–944:

```text
932:             var consumedInBatch = 0
933:             // 01（I-3）：本页是否有补证未完成（部分/失败）；有则本页不推进、不记成功。
934:             var identityRepairFailedInPage = false
935:             // 05（I-3）：本页是否有可重试登记失败（STALE/FAILED）；有则本页不推进、不记成功。
936:             var personRegistrationFailedInPage = false
937: 
938:             val sampleContext = execId?.takeIf { it > 0 }?.let {
939:                 DiscoverySampleContext(it, source.sourceName, DiscoverySampleEntry.SYNC, batchNumber, null)
940:             }
941:             val extractions = parallelExtractOutcomes(batch.papers, source, sampleContext)
942:             for ((paper, extraction) in extractions) {
943:                 if (consumedInBatch % 10 == 0 && progressStore.isCancelled("EXPERT_DISCOVERY")) { limitReached = true; break }
944:                 if (timeBudgetReached(deadline)) { timeBudgetExpired = true; break }
```

行 1602–1621:

```text
1602:     private data class PaperExtraction(
1603:         val outcome: EmailExtractionOutcome?,
1604:         val extractionError: String?
1605:     )
1606: 
1607:     private fun extractOutcome(
1608:         paper: PaperMetadata,
1609:         source: AcademicDataSource,
1610:         sampleContext: DiscoverySampleContext?
1611:     ): PaperExtraction {
1612:         return try {
1613:             val outcome = if (source.sourceName == "OPENALEX" && sampleContext?.valid() == true && sampleCapture != null) {
1614:                 sampleCapture.capture(sampleContext, paper) { source.extractAuthorEmails(paper) }
1615:             } else source.extractAuthorEmails(paper)
1616:             PaperExtraction(outcome, null)
1617:         } catch (e: Exception) {
1618:             PaperExtraction(null, e.message ?: e.javaClass.simpleName)
1619:         }
1620:     }
1621: 
```

行 1670–1691:

```text
1670:         papers: List<PaperMetadata>,
1671:         source: AcademicDataSource,
1672:         sampleContext: DiscoverySampleContext?
1673:     ): List<Pair<PaperMetadata, PaperExtraction>> {
1674:         if (papers.isEmpty()) return emptyList()
1675:         val trafficSession = DiscoveryTrafficMeter.currentSession()
1676:         fun extract(paper: PaperMetadata): PaperExtraction = if (trafficSession == null) {
1677:             extractOutcome(paper, source, sampleContext)
1678:         } else {
1679:             DiscoveryTrafficMeter.measure(trafficSession, source.sourceName, DiscoveryTrafficMeter.Category.FULLTEXT) {
1680:                 extractOutcome(paper, source, sampleContext)
1681:             }
1682:         }
1683:         if (discoveryProperties.fetchConcurrency <= 1) {
1684:             return papers.map { it to extract(it) }
1685:         }
1686:         val futures = papers.map { paper ->
1687:             paper to CompletableFuture.supplyAsync({ extract(paper) }, discoveryFetchExecutor)
1688:         }
1689:         return futures.map { (paper, future) -> paper to future.join() }
1690:     }
1691: 
```

行 2096–2133:

```text
2096:                         EmailExtractionOutcome(
2097:                             emails = orcid.orcidRecordToAuthorEmails(readOrcidRecord(envelope.payloadJson)),
2098:                             methodUsed = "API_FIELD"
2099:                         )
2100:                     }
2101:                     else -> {
2102:                         val source = requireNotNull(paperSource)
2103:                         val paper = readPaper(envelope.payloadJson)
2104:                         if (envelope.unit == QueueItemUnit.PAPER && source.sourceName == "OPENALEX" &&
2105:                             sampleContext?.valid() == true && sampleCapture != null) {
2106:                             sampleCapture.capture(sampleContext, paper) { source.extractAuthorEmails(paper) }
2107:                         } else source.extractAuthorEmails(paper)
2108:                     }
2109:                 }
2110:                 if (FulltextRequestGate.hostBusyInScope()) {
2111:                     QueuedItemExtraction.HostBusy
2112:                 } else {
2113:                     val json = objectMapper.writeValueAsString(outcome.copy(identityRuleVersion = DiscoveryIdentity.EXTRACTION_VERSION))
2114:                     if (utf8Bytes(json) > extractionMaxBytes) {
2115:                         QueuedItemExtraction.TooLarge
2116:                     } else {
2117:                         QueuedItemExtraction.Extracted(
2118:                             extractionJson = json,
2119:                             emailsEmpty = outcome.emails.isEmpty(),
2120:                             httpRequests = outcome.httpRequests,
2121:                             contentObtained = outcome.resolvedFulltextObtained(),
2122:                             failureReason = outcome.failureReason
2123:                         )
2124:                     }
2125:                 }
2126:             } catch (e: FulltextRequestGate.HostBusyException) {
2127:                 QueuedItemExtraction.HostBusy
2128:             } catch (e: Exception) {
2129:                 QueuedItemExtraction.Failed(e.message ?: e.javaClass.simpleName, retryable = true)
2130:             }
2131:         }
2132:     }
2133: 
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`

SHA256: `56a8122f0f931283fdb883187d4113f54b3e094d365d1a79a5ce0e8e6b4d06a8`

行 1024–1077:

```text
1024:                 when (val extraction = DiscoveryTrafficMeter.measure(
1025:                     state.trafficSession, stream.source, DiscoveryTrafficMeter.Category.FULLTEXT
1026:                 ) { expertDiscoveryService.extractQueuedItem(
1027:                     envelope = envelope,
1028:                     criteria = criteria,
1029:                     perHostConcurrency = properties.perHostConcurrency,
1030:                     extractionMaxBytes = properties.extractionMaxBytes,
1031:                     sampleContext = state.executionId.get().takeIf { it > 0 }?.let {
1032:                         DiscoverySampleContext(it, stream.source, DiscoverySampleEntry.QUEUE, null, job.id)
1033:                     }
1034:                 ) }) {
1035:                     is QueuedItemExtraction.Extracted -> {
1036:                         val bytes = extraction.extractionJson.toByteArray(Charsets.UTF_8).size.toLong()
1037:                         val saved = repository.saveExtraction(
1038:                             jobId = job.id, leaseToken = leaseToken, generation = job.generation,
1039:                             extractionJson = extraction.extractionJson, extractionBytes = bytes, now = now()
1040:                         )
1041:                         if (!saved) {
1042:                             // 租约/generation 已失效（已被重新领取）：绝不消费。
1043:                             log.info("job {} 抽取结果未保存（租约/generation 失效），留给下一次尝试", job.id)
1044:                             return
1045:                         }
1046:                         extractionJson = extraction.extractionJson
1047:                         synchronized(state) { state.extractionDownloads += extraction.httpRequests }
1048:                     }
1049:                     QueuedItemExtraction.HostBusy -> {
1050:                         // I-6：域名许可不可得 —— 延期，不消耗 attempts、不记永久失败。
1051:                         val at = now()
1052:                         repository.returnToPending(
1053:                             job.id, leaseToken, job.generation, at.plus(HOST_BUSY_BACKOFF),
1054:                             FulltextRequestGate.HOST_BUSY, at
1055:                         )
1056:                         state.waitReasons.add(FulltextRequestGate.HOST_BUSY)
1057:                         return
1058:                     }
1059:                     QueuedItemExtraction.TooLarge -> {
1060:                         val at = now()
1061:                         repository.completeJob(
1062:                             job.id, leaseToken, job.generation, QueueJobStatus.FAILED,
1063:                             job.attempts, at, QUEUE_EXTRACTION_TOO_LARGE, at
1064:                         )
1065:                         synchronized(state) { state.failedItems++ }
1066:                         return
1067:                     }
1068:                     is QueuedItemExtraction.Failed -> {
1069:                         handleRetryableFailure(state, job, leaseToken, extraction.reason, extraction.retryable)
1070:                         return
1071:                     }
1072:                 }
1073:             }
1074: 
1075:             // I-3 唯一例外：人工暂停（generation 递增）后**不再消费专家**，但上面刚保存的可靠抽取结果保留，
1076:             // job 退回 PENDING —— 不 complete、不消耗 attempts，恢复后由新的 generation 重新领取。
1077:             val current = repository.findPipeline()
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`

SHA256: `cd6046f1964ffd390642974718fd5179b19dd44f5c53c06d63892e9c476bd41b`

行 235–298:

```text
235:         val attempt = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PDF_URL, uri.toString(), properties)
236:         var attemptError: Throwable? = null
237:         return try {
238:             boundedHttp.execute<DownloadedContent>(
239:                 restTemplate, uri,
240:                 PDF_DOWNLOAD_CONNECT_TIMEOUT_MS, properties.downloadTimeoutMs, deadline
241:             ) { response ->
242:                 onResponseHeaders?.invoke(response.headers)
243:                 val contentType = response.headers.contentType
244:                 if (attempt != null) {
245:                     try {
246:                         DiscoverySampleCapture.headers(attempt, response.rawStatusCode, contentType?.toString())
247:                     } catch (_: Exception) { /* Diagnostic header observation is not a business read. */ }
248:                 }
249:                 val isPdfContentType = contentType != null &&
250:                     (contentType.isCompatibleWith(MediaType.APPLICATION_PDF) ||
251:                      contentType.subtype?.lowercase() == "pdf")
252: 
253:                 val maxSize = properties.maxPdfSizeBytes
254:                 val buffer = java.io.ByteArrayOutputStream()
255:                 val chunk = ByteArray(8192)
256:                 var totalRead = 0L
257: 
258:                 response.body.use { input ->
259:                     while (true) {
260:                         // R-1（V-4）：每次分片读取前后都对照绝对时限 —— 服务端只要在单次读超时前吐一点数据
261:                         // 就能让「按次读超时」永远不到期，因此这里必须按绝对 deadline 截断。
262:                         if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = true)
263:                         val n = input.read(chunk)
264:                         if (n == -1) break
265:                         totalRead += n
266:                         if (totalRead > maxSize) {
267:                             throw PdfTooLargeException()
268:                         }
269:                         buffer.write(chunk, 0, n)
270:                         if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = true)
271:                     }
272:                 }
273: 
274:                 val bytes = buffer.toByteArray()
275:                 if (bytes.isEmpty()) throw RuntimeException("Empty body")
276:                 DiscoverySampleCapture.fullBody(attempt, bytes)
277: 
278:                 val hasMagic = bytes.size >= 4 && bytes.take(4).toByteArray().contentEquals(magicBytes)
279:                 if (isPdfContentType || hasMagic) {
280:                     return@execute DownloadedContent(bytes, ContentKind.PDF)
281:                 }
282: 
283:                 if (properties.htmlFallbackEnabled && isHtmlContent(contentType, bytes)) {
284:                     return@execute DownloadedContent(bytes, ContentKind.HTML)
285:                 }
286: 
287:                 DownloadedContent(bytes, ContentKind.OTHER)
288:             } ?: throw RuntimeException("Empty response")
289:         } catch (error: Throwable) {
290:             attemptError = error
291:             if (attempt != null && error is HttpStatusCodeException) {
292:                 try {
293:                     DiscoverySampleCapture.headers(attempt, error.statusCode.value(), error.responseHeaders?.contentType?.toString())
294:                 } catch (_: Exception) { /* Preserve the original transport exception. */ }
295:             }
296:             throw error
297:         } finally {
298:             DiscoverySampleCapture.endAttempt(attempt, attemptError)
```

行 313–349:

```text
313:         PdfAuthorContactLayout.selectedPages(pageCount, properties.maxPages, properties.tailPages)
314: 
315:     private fun extractEmailsFromBytes(bytes: ByteArray, knownAuthors: List<PaperAuthor>, deadline: Instant?): List<AuthorEmail> {
316:         ByteArrayInputStream(bytes).use { stream ->
317:             PDDocument.load(stream).use { doc ->
318:                 val text = StringBuilder()
319:                 val clues = linkedSetOf<String>()
320:                 val contacts = PdfAuthorContactLayout.collect(
321:                     doc, properties.maxPages, knownAuthors, properties.tailPages,
322:                     beforePage = {
323:                         if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = true)
324:                     },
325:                     onPage = { page, pageText ->
326:                         if (page <= properties.maxPages) text.append(pageText).append('\n')
327:                         else clues += plainTextExtractor.extract(pageText, properties.blacklistPrefixes)
328:                         for (annotation in doc.getPage(page - 1).annotations) {
329:                             val uri = ((annotation as? PDAnnotationLink)?.action as? PDActionURI)?.uri ?: continue
330:                             if (!uri.startsWith("mailto:", ignoreCase = true)) continue
331:                             val address = uri.substring(7).substringBefore('?').trim()
332:                             if (!mailboxUri.matches(address)) continue
333:                             if (plainTextExtractor.extract(address, properties.blacklistPrefixes).singleOrNull() ==
334:                                 address.lowercase()) clues += address.lowercase()
335:                         }
336:                     }
337:                 )
338:                 return SourceAuthorEmailResolver.resolvePdf(
339:                     text.append('\n').append(clues.joinToString("\n")).toString(),
340:                     knownAuthors, contacts, properties.blacklistPrefixes
341:                 )
342:             }
343:         }
344:     }
345: 
346: 
347: }
348: 
349: /** c10（I-3）：下载失败的低基数类别词汇表（进入任务 details_json 的 failureReasons）。 */
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt`

SHA256: `93c5ed2c346b0b3c7b58cdca1f3869515d03a8ebc291ef9a70d1b2e0efda2aad`

行 8–42:

```text
8: import java.util.Locale
9: 
10: /** Bounded, same-page author/contact evidence; no inference from the article body or reference list. */
11: internal object PdfAuthorContactLayout {
12:     data class Contact(val email: String, val authorIndex: Int, val page: Int, val authorText: String, val contactText: String) {
13:         val evidenceText get() = "PDF_PAGE:$page\nAUTHOR:$authorText\nCONTACT:$contactText"
14:     }
15: 
16:     private data class Line(val page: Int, val x: Float, val right: Float, val y: Float, val text: String)
17:     private val mailbox = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
18:     private val emailExtractor = PlainTextEmailExtractor()
19:     /** I-1/I-2：作者与联系两侧共用的标记集合，`∗` 与 `*` 归一。 */
20:     private val markerGlyph = Regex("[*∗†‡§]")
21:     private val markerContact = Regex("^\\s*([*∗†‡§])")
22:     private val paragraphStart = Regex("^\\s*((?:[A-Z]\\.\\s*){1,4}[A-Z][A-Za-z'-]+|[A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3})\\s+is\\s+with\\b", RegexOption.IGNORE_CASE)
23:     /** I-2：下一个作者的联系语句（含 `are with` 与 `A. K. Singh` 这类多首字母）终止上一段，与元数据匹配无关。 */
24:     private val paragraphBoundary = Regex("^\\s*((?:[A-Z]\\.\\s*){1,4}[A-Z][A-Za-z'-]+|[A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3})\\s+(?:is|are)\\s+with\\b", RegexOption.IGNORE_CASE)
25:     private val emailLabel = Regex("\\be-?mail\\s*:", RegexOption.IGNORE_CASE)
26:     /** I-1：作者区之后的行（编号机构脚注、联系行、摘要等），其中的标记不是作者署名。 */
27:     private val outsideAuthorArea = Regex(
28:         "(?i)^\\s*(?:\\d|[*∗†‡§]|abstract\\b|keywords?\\b|received\\b|accepted\\b|published\\b|doi\\b|https?://|©|downloaded\\b|corresponding\\b|e-?mail\\b)")
29:     /** I-1：机构行关键词；这类行只说明脚注归属，不产生邮箱拥有者。 */
30:     private val affiliationLine = Regex(
31:         "(?i)\\b(?:universit|institut|school|department|faculty|college|centre|center|academy|laborator|gmbh|fondazione|foundation|hospital|chair\\s+of|tno)\\b")
32:     /** I-1：紧贴标记左侧的署名词串；中间只允许数字/逗号/空白，因此不会跨过上一个标记或前一位署名。 */
33:     private val signatureBeforeMarker = Regex("([\\p{L}][\\p{L}'’.-]*(?:[\\s,]+[\\p{L}][\\p{L}'’.-]*)*)[\\d,\\s]*$")
34: 
35:     fun selectedPages(pageCount: Int, maxPages: Int, tailPages: Int): List<Int> {
36:         if (pageCount < 1) return emptyList()
37:         return ((1..minOf(maxPages, pageCount)).toList() +
38:             (if (tailPages == 1) listOf(pageCount) else emptyList())).distinct()
39:     }
40: 
41:     fun collect(
42:         document: PDDocument,
```

行 62–113:

```text
62:             stripper.endPage = page
63:             val pageText = stripper.getText(document)
64:             onPage(page, pageText)
65:             if (authors.isEmpty()) continue
66:             val height = document.getPage(page - 1).mediaBox.height
67:             val width = document.getPage(page - 1).mediaBox.width
68:             val lines = toLines(page, glyphs, width)
69:             contacts += endContactBlocks(page, pageText, authors)
70:             val header = authorHeader(lines, height, authors) ?: continue
71:             // I-1：拥有者计数取自作者署名区（含匹配不上元数据的署名）；分不清作者段则回退为空集。
72:             val owners = markerOwners(authorArea(lines, header, height).orEmpty(), authors)
73:             for (line in lines) {
74:                 if (line.y <= header.y || line.y > height * .90f) continue
75:                 if (header.right < width * .48f && line.x > width * .52f) continue
76:                 if (header.x > width * .52f && line.right < width * .48f) continue
77:                 // I-2：同一联系行按标记边界切分，各自抽邮箱；分段不明确则整行不出证明。
78:                 for ((marker, segment) in markerSegments(line.text)) {
79:                     val owner = uniqueOwner(owners, marker) ?: continue
80:                     // I-3：把该片段自己的标记一起交给提取器，裸标记残缺邮箱（`∗ lun yue@msn.com`）
81:                     // 才与整页文本走同一条过滤；Contact/evidenceText 仍保留原始行。
82:                     for (address in emailExtractor.extract("$marker$segment")) {
83:                         contacts += Contact(address, owner, page, header.text, line.text)
84:                     }
85:                 }
86:             }
87:             // A contact paragraph is its own column-local record, not a line chosen by nearest email.
88:             for (start in lines.indices) {
89:                 val line = lines[start]
90:                 if (line.y <= header.y || line.y < height * .60f || line.y > height * .94f) continue
91:                 val anchor = paragraphStart.find(line.text)?.groupValues?.get(1) ?: continue
92:                 val owner = uniqueAuthor(anchor, header.text, authors) ?: continue
93:                 val paragraph = mutableListOf(line)
94:                 for (next in lines.drop(start + 1)) {
95:                     if (next.y - paragraph.last().y > 16f || next.y > height * .95f) break
96:                     if (line.x < width * .5f && next.x >= width * .5f) continue
97:                     if (next.x > line.right + 24f || next.right < line.x - 24f) continue
98:                     if (paragraphBoundary.containsMatchIn(next.text) ||
99:                         next.text.startsWith("Corresponding author:", ignoreCase = true) || paragraph.size == 5) break
100:                     paragraph += next
101:                 }
102:                 val text = paragraph.joinToString(" ") { it.text }
103:                 if (!emailLabel.containsMatchIn(text)) continue
104:                 // A second author's explicit mention or a shared contact list defeats the single owner.
105:                 if (authors.indices.any { it != owner && mentionsAuthor(text, authors[it]) }) continue
106:                 val addresses = emailExtractor.extract(text)
107:                 for (address in addresses) contacts += Contact(address, owner, page, header.text, text)
108:             }
109:         }
110:         return contacts
111:     }
112: 
113:     /** Section heading + actual metadata author roster + independent name/mailbox records. */
```

行 293–316:

```text
293:             var left = ordered.first().xDirAdj
294:             var right = left
295:             fun flush() {
296:                 if (text.isNotBlank()) lines += Line(page, left, right, row.first().yDirAdj, text.toString().trim())
297:                 text = StringBuilder()
298:             }
299:             for (position in ordered) {
300:                 val x = position.xDirAdj
301:                 val gap = x - right
302:                 if (text.isNotEmpty() && (gap > 24f ||
303:                         (gap > 6f && right < pageWidth * .5f && x > pageWidth * .5f))) {
304:                     flush()
305:                     left = x
306:                 } else if (text.isNotEmpty() && gap > 1.2f) text.append(' ')
307:                 if (position.unicode.isBlank()) {
308:                     if (text.isNotEmpty() && text.last() != ' ') text.append(' ')
309:                 } else text.append(position.unicode)
310:                 right = maxOf(right, x + position.widthDirAdj)
311:             }
312:             flush()
313:         }
314:         return lines.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })
315:     }
316: }
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt`

SHA256: `246231bd019267450f9c23038bca259995ef44c298b8b818ab1ca0ddf98d5f9b`

行 159–178:

```text
159:     override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
160:         if (paper.fullText != null) {
161:             val emails = plainTextExtractor.extract(paper.fullText, emptyList())
162:             if (emails.isNotEmpty()) {
163:                 return EmailExtractionOutcome(
164:                     SourceAuthorEmailResolver.resolveText(paper.fullText, paper.authors), "FULLTEXT_TEXT", null, httpRequests = 0
165:                 )
166:             }
167:             // fullText had no emails, try PDF if available
168:             if (paper.downloadUrl != null) {
169:                 return pdfEmailExtractor.extract(paper.downloadUrl, paper.authors, sourceName)
170:             }
171:             return EmailExtractionOutcome(emptyList(), "FULLTEXT_TEXT", "NO_EMAIL_IN_FULLTEXT", httpRequests = 0)
172:         }
173:         if (paper.downloadUrl != null) {
174:             return pdfEmailExtractor.extract(paper.downloadUrl, paper.authors, sourceName)
175:         }
176:         return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_FULLTEXT")
177:     }
178: 
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PmcOaDataSource.kt`

SHA256: `d26d3cbd0f059d0c2eed9d098bbeec052920b729c5fb9f1e76b5364013a486f9`

行 47–81:

```text
47:     override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
48:         val pmcId = paper.pmcId
49:         if (pmcId == null) {
50:             return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID", httpRequests = 0)
51:         }
52: 
53:         val xml = fetchFullTextXml(pmcId)
54:         if (xml == null) {
55:             return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "FULLTEXT_FETCH_FAILED", httpRequests = 1)
56:         }
57: 
58:         return try {
59:             val emails = JatsXmlEmailParser.parse(xml)
60:             if (emails.isEmpty()) {
61:                 EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_EMAIL_IN_FULLTEXT", httpRequests = 1)
62:             } else {
63:                 EmailExtractionOutcome(emails, emailExtractionMethod, null, httpRequests = 1)
64:             }
65:         } catch (e: Exception) {
66:             log.debug("Failed to parse JATS XML for {}: {}", pmcId, e.message)
67:             EmailExtractionOutcome(emptyList(), emailExtractionMethod, "XML_PARSE_FAILED", httpRequests = 1)
68:         }
69:     }
70: 
71:     private fun fetchFullTextXml(pmcId: String): ByteArray? {
72:         val url = "${properties.baseUrl}/efetch.fcgi?db=pmc&id=$pmcId&rettype=xml"
73:         return try {
74:             if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
75:             restTemplate.getForObject(url, ByteArray::class.java)
76:         } catch (e: Exception) {
77:             log.debug("Failed to fetch full text XML for {}: {}", pmcId, e.message)
78:             null
79:         }
80:     }
81: 
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt`

SHA256: `7830561ba3ed93d445078fb9e5c0fd1ba265701a53acb7aca1a69e12a6eb24e1`

行 103–127:

```text
103:                 if (remaining <= 0L) return@retryOnRecoverableIo null
104:                 requestsIssued++
105:                 // R-1（V-4）：连接/读取超时取 min(既有配置, 剩余预算)，响应体也在同一个绝对 deadline
106:                 // 内读完 —— 细水长流的 XML 会被截断，而不是永远读下去。
107:                 val attempt = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, url)
108:                 var attemptError: Throwable? = null
109:                 try {
110:                     BoundedFulltextHttp.getForObject(
111:                         restTemplate, url, ByteArray::class.java,
112:                         properties.connectTimeoutMs.toLong(), properties.readTimeoutMs.toLong(), deadline
113:                     ).also { bytes -> if (bytes != null) DiscoverySampleCapture.fullBody(attempt, bytes) }
114:                 } catch (error: Throwable) {
115:                     attemptError = error
116:                     if (attempt != null && error is HttpStatusCodeException) {
117:                         try {
118:                             DiscoverySampleCapture.headers(attempt, error.statusCode.value(), error.responseHeaders?.contentType?.toString())
119:                         } catch (_: Exception) { /* Preserve the original transport exception. */ }
120:                     }
121:                     throw error
122:                 } finally {
123:                     DiscoverySampleCapture.endAttempt(attempt, attemptError)
124:                 }
125:             }
126:             if (payload == null) XmlFetchResult.BudgetExhausted(requestsIssued) else XmlFetchResult.Bytes(payload)
127:         } catch (e: Exception) {
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt`

SHA256: `71688dc2eab666e02109c65474bf80776c60563e6e9c34704b0a69a4cc86d334`

行 123–155:

```text
123:     fun detail(id: String, permit: RequestPermit): VerificationHttpResult<ApolloCandidate> {
124:         require(properties.apolloApiKey.isNotBlank() && id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
125:         val uri = UriComponentsBuilder.fromHttpUrl("https://api.apollo.io/api/v1/people")
126:             .pathSegment(id).build().toUri()
127:         val result = transport.request(uri, HttpMethod.GET,
128:             mapOf("X-Api-Key" to properties.apolloApiKey, "Accept" to "application/json"), permit, VerificationProvider.APOLLO)
129:         if (result !is VerificationHttpResult.Success) return result as VerificationHttpResult<Nothing>
130:         val person = result.value.path("person")
131:         if (!person.isObject || text(person, "id") != id ||
132:             listOf("first_name", "last_name", "name", "title", "headline", "orcid").any {
133:                 person.hasNonNull(it) && !person.path(it).isTextual
134:             }) return invalid(result, uri.path)
135:         val history = person.path("employment_history")
136:         if (person.hasNonNull("employment_history") && !history.isArray ||
137:             history.size() > 200 || history.any { !it.isObject ||
138:                 it.hasNonNull("current") && !it.path("current").isBoolean })
139:             return invalid(result, uri.path)
140:         val candidate = ApolloCandidate(id, text(person, "first_name"), text(person, "last_name"),
141:             text(person, "last_name_obfuscated"), text(person, "name"), text(person, "title"),
142:             text(person.path("organization"), "name")?.let(::VerificationInstitution), text(person, "orcid"),
143:             text(person, "headline"), history.map { job ->
144:                 ApolloEmployment(text(job, "organization_name")?.let(::VerificationInstitution),
145:                     text(job, "title"), job.path("current").takeIf { it.isBoolean }?.booleanValue(),
146:                     text(job, "start_date"), text(job, "end_date"), text(job, "major"))
147:             })
148:         return VerificationHttpResult.Success(candidate, result.responseSha256, result.httpStatus)
149:     }
150:     fun search(query: ApolloSearchQuery, permit: RequestPermit): VerificationHttpResult<ApolloSearchPage> {
151:         require(properties.apolloApiKey.isNotBlank()) { "Apollo configuration unavailable" }
152:         val builder = UriComponentsBuilder.fromHttpUrl("https://api.apollo.io/api/v1/mixed_people/api_search")
153:             .queryParam("q_person_name", "{name}").queryParam("page", query.page).queryParam("per_page", 100)
154:         val variables = mutableMapOf<String, String>("name" to query.name)
155:         query.institution?.let {
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`

SHA256: `8995306fa6063ef7eacad2e611f4b410ab0279ff29549a61454ef4a57ba8d47d`

行 46–76:

```text
46:     fun resolveOptions(criteria: PaperSearchCriteria): PaperSearchCriteria {
47:         if (criteria.apolloAssist == true && (!properties.enabled || properties.apolloApiKey.isBlank())) {
48:             throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Apollo verification is not ready")
49:         }
50:         return criteria.copy(apolloAssist = criteria.apolloAssist ?: (properties.enabled && properties.apolloApiKey.isNotBlank() && properties.apolloDefault))
51:     }
52: 
53:     fun begin(executionId: Long?, criteria: PaperSearchCriteria, historical: Boolean = false): DiscoveryVerificationExecution {
54:         require(executionId == null || executionId > 0) { "A real positive execution ID is required" }
55:         val existing = executionId?.let { repository.findRun(it) }
56:         if (existing != null) return DiscoveryVerificationExecution(executionId, existing)
57:         if (!properties.enabled) return DiscoveryVerificationExecution(executionId)
58:         require(executionId != null) { "Verification requires a real positive execution ID" }
59:         check(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, executionId) == 1L) {
60:             "Verification requires an existing task execution"
61:         }
62:         // Old persisted JSON is OFF, never reinterpreted through today's default.
63:         val apollo = if (historical && criteria.apolloAssist == null) false else requireNotNull(resolveOptions(criteria).apolloAssist)
64:         val settings = mapper.writeValueAsString(linkedMapOf(
65:             "mode" to properties.mode.name, "policyVersion" to properties.mode.policyVersion,
66:             "apolloMatchingVersion" to 2,
67:             "ruleVersion" to 2,
68:             "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION,
69:             "apolloLimitPolicy" to "PROVIDER",
70:             "maxRequestsPerItem" to properties.maxRequestsPerItem, "maxPagesPerQuery" to properties.maxPagesPerQuery,
71:             "maxTransientAttempts" to properties.maxTransientAttempts, "leaseSeconds" to properties.leaseSeconds,
72:             "httpConnectTimeout" to properties.httpConnectTimeout.toString(), "httpReadTimeout" to properties.httpReadTimeout.toString()
73:         ))
74:         return DiscoveryVerificationExecution(executionId, repository.createRun(executionId, properties.mode, apollo, settings, clock.instant()))
75:     }
76: 
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`

SHA256: `534498bc4d2a571f9a282a8c69ca1e9670d048c9a953498e674e9e99b2e3e8d7`

行 205–216:

```text
205:             require(snapshot.identityHash == lease.identityHash && snapshot.factFingerprint == lease.factFingerprint)
206:             val run = repository.findRun(lease.ownerExecutionId) ?: error("Verification run missing")
207:             val matchingVersion = mapper.readTree(run.settingsJson).path("apolloMatchingVersion").asInt(1)
208:             require(matchingVersion in 1..2)
209:             val aliasVersion = mapper.readTree(run.settingsJson).path("institutionAliasVersion").asInt(0)
210:             require(aliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
211:             if (lease.evidenceJson == null) {
212:                 val frozenRule = mapper.readTree(run.settingsJson).get("ruleVersion")
213:                 require(frozenRule == null || frozenRule.isIntegralNumber && frozenRule.canConvertToInt()) {
214:                     "Invalid frozen rule version"
215:                 }
216:                 payload = payload.copy(ruleVersion = frozenRule?.intValue() ?: 1)
```

行 344–415:

```text
344:                 if (payload.search?.matchingVersion == 2 && payload.search?.detailsComplete != true) {
345:                     // Search pagination is finished and durable before detail calls begin. Recovery never repeats
346:                     // acknowledged details or changes the saved query sequence.
347:                     val names = rules.allowedNames(snapshot, payload.evidence)
348:                     fun mergeDetail(candidate: ApolloCandidate, detail: ApolloCandidate) {
349:                         require(detail.id == candidate.id)
350:                         // Search is a projection. Preserve missing identity hints but retain real ORCID conflicts.
351:                         val updated = detail.copy(orcid = detail.orcid ?: candidate.orcid,
352:                             obfuscatedLastName = detail.obfuscatedLastName ?: candidate.obfuscatedLastName)
353:                         val conflicting = detail.orcid != null && candidate.orcid != null &&
354:                             normalizeOrcid(detail.orcid) != normalizeOrcid(candidate.orcid)
355:                         val search = payload.search!!.copy(
356:                             candidates = payload.search!!.candidates.map { if (it.id == candidate.id) updated else it },
357:                             conflictingIds = payload.search!!.conflictingIds + if (conflicting) setOf(candidate.id) else emptySet(),
358:                             detailsCompleted = payload.search!!.detailsCompleted + candidate.id)
359:                         payload = payload.copy(search = search, institutionNormalization = payload.institutionNormalization?.let {
360:                             DiscoveryInstitutionNormalizer.withCandidates(it, search.candidates)
361:                         })
362:                     }
363:                     val reusable = mutableMapOf<String, VerificationAttempt>()
364:                     val lookedUp = mutableSetOf<String>()
365:                     while (true) {
366:                         val candidates = payload.search!!.candidates.filter {
367:                             (rules.nameMatches(it, names, payload.search!!.queryNames, masked = true, ruleVersion = payload.ruleVersion) ||
368:                                 payload.ruleVersion >= 1 && rules.emailNameMatches(snapshot, payload.evidence, it, payload.search!!.institutionAliasVersion, masked = true))
369:                         }
370:                         val strong = candidates.filter { rules.matchReason(snapshot, payload.evidence, it, null, payload.search?.institutionAliasVersion ?: 0, payload.ruleVersion) in
371:                             setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
372:                         // Current institution / professor evidence can distinguish a candidate from unrelated
373:                         // same-name people. Only fall back to history/specialty when no strong candidate exists.
374:                         val needingDetails = strong.ifEmpty { candidates }.filter {
375:                             it.id !in payload.search!!.detailsCompleted &&
376:                                 !(it in strong && (it.fullName != null || it.lastName != null))
377:                         }
378:                         val candidate = needingDetails.firstOrNull() ?: break
379:                         val ids = needingDetails.map { it.id }.filter { it !in lookedUp }
380:                         if (ids.isNotEmpty()) {
381:                             reusable.putAll(repository.findSuccessfulDetails(lease.ownerExecutionId, ids))
382:                             lookedUp.addAll(ids)
383:                         }
384:                         val source = reusable[candidate.id]
385:                         if (source != null) {
386:                             val detail = mapper.readValue<ApolloCandidate>(requireNotNull(source.responseJson))
387:                             mergeDetail(candidate, detail)
388:                             payload = payload.copy(diagnostics = (payload.diagnostics ?: WorkerDiagnostics()).reuse(
389:                                 WorkerDetailReuse(source.id, source.itemId, candidate.id, requireNotNull(source.responseSha256))))
390:                             save()
391:                             continue
392:                         }
393:                         val permit = reserve(VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1,
394:                             mapper.writeValueAsString(mapOf("personId" to candidate.id, "matchingVersion" to 2)))
395:                         val result = apollo.detail(candidate.id, permit)
396:                         if (result is VerificationHttpResult.Success) {
397:                             val detail = result.value
398:                             mergeDetail(candidate, detail)
399:                             completed(permit, result, detail)
400:                         } else if (result is VerificationHttpResult.PermanentFailure && result.code == "EVIDENCE_MISSING") {
401:                             payload = payload.copy(search = payload.search!!.copy(
402:                                 detailsCompleted = payload.search!!.detailsCompleted + candidate.id))
403:                             completed(permit, result)
404:                         } else {
405:                             completed(permit, result)
406:                             retry(result as VerificationHttpResult<Nothing>); return
407:                         }
408:                     }
409:                     // Academic enrichment runs independently. Freeze the fields already available at decision
410:                     // time; replay and the gate consume these same fields, never a later mutable profile.
411:                     if (payload.search!!.candidates.isNotEmpty()) {
412:                         val latest = commits.prepare(lease).snapshot
413:                         if (latest != null && latest.identityHash == snapshot.identityHash &&
414:                             latest.factFingerprint == snapshot.factFingerprint) {
415:                             payload = payload.copy(search = payload.search!!.copy(
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`

SHA256: `2116431276c584366d6128d33a136548f43203d7abee4f0c318ef983c01b5469`

行 80–120:

```text
80:         }
81:         if (evidence.any { it.identityHash != snapshot.identityHash || it.factFingerprint != snapshot.factFingerprint })
82:             return decision(VerificationQualification.CONFLICT, listOf("FACT_CHANGED"))
83:         if (!snapshot.identityValid) return decision(VerificationQualification.UNKNOWN, listOf("IDENTITY_EVIDENCE_MISSING"), listOf("identity"))
84:         if (evidence.any { it.conflicts.isNotEmpty() }) return decision(VerificationQualification.CONFLICT, listOf("EVIDENCE_CONFLICT"))
85:         val names = evidence.filter { it.field == "name" }.map { normalizeName(it.value) }.distinct()
86:         if (names.any { !sameName(it, snapshot.fullName) })
87:             return decision(VerificationQualification.CONFLICT, listOf("NAME_CONFLICT"))
88:         val facts = evidence.filter { it.assertionSource == AssertionSource.ORGANIZATION ||
89:             ruleVersion >= 1 && it.assertionSource == AssertionSource.SELF }
90:         val jobs = facts.filter { it.field == "employment" && it.temporalScope == TemporalScope.CURRENT }
91:         val professor = jobs.any { fact -> value(fact, "institution").isNotBlank() && isProfessor(value(fact, "role")) }
92:         val doctorates = facts.filter { it.field == "education" }.mapNotNull { fact ->
93:             runCatching { mapper.readTree(fact.value) }.getOrNull()?.takeIf {
94:                 it.path("completed").isBoolean && it.path("completed").booleanValue() &&
95:                     QualificationEvidenceParser(clock).completedDoctorate(it.path("role").asText())
96:             }
97:         }
98:         val ages = facts.filter { it.field == "birthDate" }.mapNotNull { fact ->
99:             runCatching { LocalDate.parse(fact.value) }.getOrNull()?.takeIf { !it.isAfter(today) }?.let { Period.between(it, today).years }
100:         }.distinct()
101:         if (ages.size > 1) return decision(VerificationQualification.CONFLICT, listOf("AGE_CONFLICT"))
102:         val age = ages.singleOrNull()
103:         val qualification = when {
104:             professor -> VerificationQualification.MET
105:             doctorates.isNotEmpty() && age != null && age < 40 -> VerificationQualification.MET
106:             doctorates.isNotEmpty() && age != null && age >= 40 -> VerificationQualification.NOT_MET
107:             else -> VerificationQualification.UNKNOWN
108:         }
109:         val missing = mutableListOf<String>()
110:         if (!professor) {
111:             if (doctorates.isEmpty()) missing += "completedDoctorate"
112:             if (age == null) missing += "reliableAge"
113:             if (age != null && age >= 40) missing += "currentAssociateProfessor"
114:         }
115:         if (search?.matchingVersion == 2) {
116:             if (!search.complete || !search.broadComplete || !search.detailsComplete)
117:                 return decision(qualification, listOf("SEARCH_INCOMPLETE"), missing)
118:             if (search.conflictingIds.isNotEmpty())
119:                 return decision(VerificationQualification.CONFLICT, listOf("APOLLO_ID_CONFLICT"), missing)
120:             if (search.candidates.isEmpty()) return decision(qualification, listOf("APOLLO_NO_MATCH"), missing)
```

## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt`

SHA256: `1b67c1f6f2cbf347b28c6a968d5460cfc58a60af005de3d8582a920699e0f147`

行 55–84:

```text
55:         }
56:         val snapshot = snapshot(context.docId, context.profile, baseAutomatic.configSnapshot)
57:         val item = context.items.filter { it.policyVersion == VerificationMode.ENFORCE.policyVersion && it.identityHash == snapshot.identityHash }
58:             .maxByOrNull { it.id } ?: return result(listOf("IDENTITY_CHANGED"))
59:         if (item.factFingerprint != snapshot.factFingerprint) return result(listOf("FACT_CHANGED"))
60:         if (item.state != VerificationState.COMPLETED) return result(listOf("VERIFICATION_PENDING"))
61:         val decision = try {
62:             val frozen = mapper.readValue<VerificationSnapshot>(item.snapshotJson)
63:             require(frozen.identityHash == item.identityHash && frozen.factFingerprint == item.factFingerprint)
64:             require(snapshot(context.docId, frozen.profile, baseAutomatic.configSnapshot).factFingerprint == item.factFingerprint)
65:             val payload = mapper.readValue<VerificationAdmissionEvidence>(requireNotNull(item.evidenceJson))
66:             rules.evaluate(snapshot, payload.evidence, payload.search,
67:                 institutionNormalization = payload.institutionNormalization, ruleVersion = payload.ruleVersion).also {
68:                 require(it.outcome == item.outcome && it.qualification == item.qualification && it.route == item.route)
69:                 require(it.matchedPersonId == item.apolloPersonId)
70:             }
71:         } catch (_: Exception) {
72:             return result(listOf("VERIFICATION_EVIDENCE_INVALID"))
73:         }
74:         return result(if (decision.outcome in ADMITTED_OUTCOMES) emptyList() else decision.reasonCodes.ifEmpty { listOf("VERIFICATION_PENDING") })
75:     }
76: 
77:     companion object {
78:         const val POLICY_VERSION = "APV_20261018_V1"
79:         val REPLACED_CODES = setOf("NO_DOCTORAL_DEGREE", "AGE_EXCEEDED")
80:         private val ADMITTED_OUTCOMES = setOf(VerificationOutcome.DIRECT_CLEAR, VerificationOutcome.APOLLO_CLEAR, VerificationOutcome.APOLLO_UNCLEAR)
81:         private val mapper = jacksonObjectMapper().findAndRegisterModules()
82:             .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
83:             .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
84: 
```

## `src/main/resources/db/migration/V158__create_discovery_verification.sql`

SHA256: `855a85c27da96e252c95e2e9e58d9d566fe7eb6c0452d1bed18977f7563592c5`

行 1–75:

```text
1: CREATE TABLE discovery_verification_run (
2:   execution_id BIGINT NOT NULL,
3:   mode VARCHAR(16) NOT NULL,
4:   policy_version VARCHAR(32) NOT NULL,
5:   apollo_requested TINYINT(1) NOT NULL,
6:   settings_json TEXT NOT NULL,
7:   input_closed TINYINT(1) NOT NULL DEFAULT 0,
8:   revision BIGINT NOT NULL DEFAULT 0,
9:   created_at DATETIME(3) NOT NULL,
10:   updated_at DATETIME(3) NOT NULL,
11:   PRIMARY KEY (execution_id)
12: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
13: 
14: CREATE TABLE discovery_verification_item (
15:   id BIGINT NOT NULL AUTO_INCREMENT,
16:   owner_execution_id BIGINT NOT NULL,
17:   expert_doc_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
18:   identity_hash CHAR(64) NOT NULL,
19:   policy_version VARCHAR(32) NOT NULL,
20:   source_name VARCHAR(32) NOT NULL,
21:   source_item_key VARCHAR(128) NOT NULL,
22:   batch_number INT NOT NULL,
23:   snapshot_json MEDIUMTEXT NOT NULL,
24:   fact_fingerprint CHAR(64) NOT NULL,
25:   state VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
26:   outcome VARCHAR(32) NULL,
27:   qualification VARCHAR(24) NOT NULL DEFAULT 'NOT_EVALUATED',
28:   route VARCHAR(16) NOT NULL DEFAULT 'NONE',
29:   apollo_entered_at DATETIME(3) NULL,
30:   search_completed_at DATETIME(3) NULL,
31:   candidate_found_at DATETIME(3) NULL,
32:   person_matched_at DATETIME(3) NULL,
33:   apollo_person_id VARCHAR(128) NULL,
34:   evidence_json MEDIUMTEXT NULL,
35:   reasons_json TEXT NULL,
36:   request_count INT NOT NULL DEFAULT 0,
37:   transient_attempts INT NOT NULL DEFAULT 0,
38:   generation BIGINT NOT NULL DEFAULT 0,
39:   lease_token VARCHAR(64) NULL,
40:   lease_until DATETIME(3) NULL,
41:   next_attempt_at DATETIME(3) NULL,
42:   admission_revision BIGINT NULL,
43:   projection_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
44:   projection_error VARCHAR(128) NULL,
45:   activated_at DATETIME(3) NULL,
46:   completed_at DATETIME(3) NULL,
47:   created_at DATETIME(3) NOT NULL,
48:   updated_at DATETIME(3) NOT NULL,
49:   PRIMARY KEY (id),
50:   UNIQUE KEY uk_dvi_identity (expert_doc_id, identity_hash, policy_version),
51:   KEY idx_dvi_run_batch (owner_execution_id, batch_number, id),
52:   KEY idx_dvi_claim (state, next_attempt_at, lease_until, id),
53:   KEY idx_dvi_projection (projection_state, updated_at, id)
54: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
55: 
56: CREATE TABLE discovery_verification_attempt (
57:   id BIGINT NOT NULL AUTO_INCREMENT,
58:   item_id BIGINT NOT NULL,
59:   request_key VARCHAR(128) NOT NULL,
60:   provider VARCHAR(16) NOT NULL,
61:   query_kind VARCHAR(16) NOT NULL,
62:   page_number INT NOT NULL,
63:   attempt_number INT NOT NULL,
64:   requested_at DATETIME(3) NOT NULL,
65:   completed_at DATETIME(3) NULL,
66:   http_status INT NULL,
67:   result_code VARCHAR(64) NOT NULL,
68:   request_json TEXT NOT NULL,
69:   response_json MEDIUMTEXT NULL,
70:   response_sha256 CHAR(64) NULL,
71:   PRIMARY KEY (id),
72:   UNIQUE KEY uk_dva_request (request_key),
73:   KEY idx_dva_item (item_id, id),
74:   KEY idx_dva_budget (provider, requested_at)
75: ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`

SHA256: `f41a744f244f28346dfa4f8c1da37abe2cfd795c496699edf5f9ac8679a78ebb`

行 22–28:

```text
22: 
23:     override fun addInterceptors(registry: InterceptorRegistry) {
24:         registry.addInterceptor(authInterceptor())
25:             .addPathPatterns("/api/**")
26:             .excludePathPatterns("/api/auth/login", "/api/auth/me")
27:     }
28: }
```

## `src/main/resources/static/index.html`

SHA256: `909fca0d6fc123b8fae02c544eda8555785c17dcb217aab769ff89e029a010fa`

行 1129–1144:

```text
1129: <div id="taskProgressModal" class="modal-overlay" hidden>
1130:     <div class="modal-content task-modal">
1131:         <div class="modal-header">
1132:             <h3 id="taskModalTitle">任务管理</h3>
1133:             <button class="button small" onclick="closeTaskModal()">×</button>
1134:         </div>
1135:         <nav id="discoveryReviewTabs" class="batch-send-tabs" role="tablist" aria-label="深度发现功能" hidden>
1136:           <button class="batch-send-tab is-active" type="button" role="tab" aria-selected="true" aria-controls="discoveryPipelinePane" id="discoveryPipelineTab">采集运行</button>
1137:           <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewPane" id="discoveryReviewTab" tabindex="-1">专家审核</button>
1138:           <button class="batch-send-tab" type="button" role="tab" aria-selected="false" aria-controls="discoveryReviewHistoryPane" id="discoveryReviewHistoryTab" tabindex="-1">审核记录</button>
1139:         </nav>
1140:         <div id="discoveryPipelinePane" role="tabpanel" aria-labelledby="discoveryPipelineTab">
1141:         <div id="taskModalToast" class="task-modal-toast" hidden></div>
1142:         <div class="modal-body" style="gap: 20px;">
1143:             <div id="discoverySchedulePanel" class="discovery-schedule-panel" hidden>
1144:                 <div id="discoveryScheduleControls" class="discovery-schedule-row">
```

行 1191–1198:

```text
1191:                 <!-- S-2: Apollo is a per-launch option; existing source controls are unchanged. -->
1192:                 <div class="apv-option" id="taskLaunchApolloRow" hidden>
1193:                     <label for="taskLaunchApolloAssist"><input type="checkbox" id="taskLaunchApolloAssist" aria-describedby="taskLaunchApolloHint" disabled>启用 Apollo 辅助核验</label>
1194:                     <p id="taskLaunchApolloHint">正在读取资格核验能力…</p>
1195:                     <details class="apv-rules"><summary>查看准入口径</summary><p>当前本人教授类职称可明确符合；其他情况核对已取得博士及年龄、职称条件。Apollo 仅返回候选不能直接准入。缺少可靠来源时保留待核验。</p></details>
1196:                 </div>
1197: 
1198:                 <div id="taskLaunchFiltersRow" hidden>
```

## `src/main/resources/static/styles.css`

SHA256: `c19989a43d11b2de9a5d77966d9256e1c969a23a7bbf538a1e47f33e98fdbce9`

行 1–79:

```text
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
77:     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
78:     --shadow: var(--shadow-md);
79: 
```

行 802–870:

```text
802: .button {
803:     display: inline-flex;
804:     align-items: center;
805:     justify-content: center;
806:     gap: 6px;
807:     min-height: 32px;
808:     height: 32px;
809:     padding: 0 12px;
810:     border-radius: var(--radius-sm);
811:     font-weight: 500;
812:     font-size: 12px;
813:     cursor: pointer;
814:     border: 1px solid var(--border);
815:     background-color: transparent;
816:     color: var(--text-main);
817:     transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
818:     outline: none;
819:     user-select: none;
820:     font-family: var(--font-body);
821:     position: relative;
822:     overflow: hidden;
823: }
824: 
825: .button:hover {
826:     border-color: rgba(15, 23, 42, 0.2);
827:     background-color: var(--surface);
828:     transform: translateY(-1px);
829:     box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
830: }
831: 
832: .button:active {
833:     transform: translateY(0) scale(0.97);
834:     box-shadow: none;
835:     opacity: 0.85;
836: }
837: 
838: .button.primary {
839:     background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
840:     background-color: var(--primary);
841:     border-color: transparent;
842:     color: #ffffff;
843:     font-weight: 600;
844:     box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
845: }
846: 
847: .button.primary:hover {
848:     background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
849:     box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
850: }
851: 
852: .button.secondary {
853:     background-color: var(--primary-light);
854:     border-color: rgba(var(--primary-rgb), 0.12);
855:     color: var(--primary);
856: }
857: 
858: .button.secondary:hover {
859:     background-color: rgba(var(--primary-rgb), 0.1);
860: }
861: 
862: .button.danger {
863:     background-color: var(--error-bg);
864:     border-color: var(--error-border);
865:     color: var(--error);
866: }
867: 
868: .button.danger:hover {
869:     background-color: rgba(var(--error-rgb), 0.1);
870: }
```

行 1000–1020:

```text
1000:     padding: 8px 14px;
1001:     text-align: left;
1002:     border-bottom: 1px solid var(--line);
1003:     vertical-align: middle;
1004: }
1005: 
1006: th {
1007:     color: var(--text-sidebar);
1008:     font-size: 11px;
1009:     font-weight: 600;
1010:     background-color: var(--surface);
1011:     text-transform: uppercase;
1012:     letter-spacing: 0.5px;
1013:     font-family: var(--font-body);
1014:     position: sticky;
1015:     top: 0;
1016: }
1017: 
1018: tr {
1019:     transition: background-color 0.12s ease;
1020: }
```

行 1117–1160:

```text
1117: label {
1118:     display: flex;
1119:     flex-direction: column;
1120:     gap: 4px;
1121:     color: var(--text-muted);
1122:     font-size: 11px;
1123:     font-weight: 600;
1124:     font-family: var(--font-body);
1125:     text-transform: uppercase;
1126:     letter-spacing: 0.3px;
1127: }
1128: 
1129: input, select, textarea {
1130:     width: 100%;
1131:     height: 34px;
1132:     min-height: 34px;
1133:     border: 1px solid var(--border);
1134:     border-radius: var(--radius-sm);
1135:     padding: 6px 10px;
1136:     color: var(--text-main);
1137:     background-color: var(--panel-bg);
1138:     transition: var(--transition);
1139:     outline: none;
1140:     font-size: 13px;
1141: }
1142: 
1143: input:hover, select:hover, textarea:hover {
1144:     border-color: rgba(15, 23, 42, 0.2);
1145: }
1146: 
1147: input:focus, select:focus, textarea:focus {
1148:     border-color: var(--primary);
1149:     background-color: var(--panel-bg);
1150:     box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1), 0 0 12px rgba(var(--primary-rgb), 0.06);
1151:     transition: border-color 0.2s ease, box-shadow 0.25s ease;
1152: }
1153: 
1154: input:disabled, select:disabled, textarea:disabled {
1155:     background-color: var(--surface);
1156:     color: var(--text-muted);
1157:     cursor: not-allowed;
1158:     border-color: var(--border);
1159: }
1160: 
```

行 4494–4531:

```text
4494: .modal-content {
4495:     background-color: var(--bg-sidebar);
4496:     border-radius: var(--radius-lg);
4497:     border: 1px solid var(--panel-border);
4498:     box-shadow: var(--shadow-xl);
4499:     width: 100%;
4500:     max-height: 80vh;
4501:     overflow-y: auto;
4502:     display: flex;
4503:     flex-direction: column;
4504: }
4505: 
4506: .modal-content.task-modal {
4507:     max-width: 700px;
4508: }
4509: 
4510: .modal-header {
4511:     display: flex;
4512:     justify-content: space-between;
4513:     align-items: center;
4514:     padding: 16px 20px;
4515:     border-bottom: 1px solid var(--panel-border);
4516: }
4517: 
4518: .modal-header h3 {
4519:     font-family: var(--font-body);
4520:     font-size: 15px;
4521:     font-weight: 600;
4522:     margin: 0;
4523: }
4524: 
4525: .modal-body {
4526:     padding: 20px;
4527:     display: flex;
4528:     flex-direction: column;
4529:     gap: 16px;
4530: }
4531: 
```

行 9150–9183:

```text
9150: .batch-send-tabs {
9151:   display: flex;
9152:   gap: 28px;
9153:   min-height: 48px;
9154:   padding: 0 28px;
9155:   border-bottom: 1px solid rgba(15, 23, 42, .08);
9156:   flex-shrink: 0;
9157: }
9158: 
9159: .batch-send-tab {
9160:   position: relative;
9161:   border: 0;
9162:   background: transparent;
9163:   color: var(--text-sidebar);
9164:   padding: 0 2px;
9165:   font: inherit;
9166:   font-weight: 600;
9167:   cursor: pointer;
9168: }
9169: 
9170: .batch-send-tab::after {
9171:   content: "";
9172:   position: absolute;
9173:   right: 0;
9174:   bottom: -1px;
9175:   left: 0;
9176:   height: 2px;
9177:   background: transparent;
9178: }
9179: 
9180: .batch-send-tab:hover { color: var(--primary); }
9181: .batch-send-tab.is-active { color: var(--primary); }
9182: .batch-send-tab.is-active::after { background: var(--primary); }
9183: 
```

行 12716–12749:

```text
12716: /* ── c5/S-1：深度发现审核容器与页签（仅 data-discovery-review 生效） ───────────── */
12717: #taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
12718:     max-width: 1180px;
12719:     width: calc(100vw - 48px);
12720: }
12721: .dr-review {
12722:     color: var(--text-main);
12723:     font-size: 13px;
12724:     line-height: 1.6;
12725: }
12726: .dr-review [role="alert"] {
12727:     padding: 12px;
12728:     color: var(--error-strong);
12729:     background: var(--error-bg);
12730:     border: 1px solid var(--error-border);
12731:     border-radius: var(--radius-sm);
12732: }
12733: .dr-review button:disabled {
12734:     opacity: 0.45;
12735:     cursor: not-allowed;
12736:     transform: none;
12737:     box-shadow: none;
12738: }
12739: .dr-review :focus-visible,
12740: #discoveryReviewTabs :focus-visible {
12741:     outline: 2px solid var(--primary);
12742:     outline-offset: 3px;
12743: }
12744: @media (max-width: 640px) {
12745:     #taskProgressModal[data-discovery-review="true"] .modal-content.task-modal {
12746:         width: calc(100vw - 20px);
12747:     }
12748: }
12749: 
```

## `src/main/resources/static/app.js`

SHA256: `9970ede8c9b1bc65142a31a53d91fffb85a74551d5ef5751ba33370c203d4305`

行 2451–2454:

```text
2451: const DISCOVERY_REVIEW_ACTION_LABELS = { APPROVE: "通过", HOLD: "暂缓", REJECT: "不通过" };
2452: const DISCOVERY_REVIEW_TABS = ["pipeline", "review", "history"];
2453: const DISCOVERY_REVIEW_SCOPE_LABELS = { SINGLE: "单人", SELECTED: "所选", PAGE: "当前页", ALL: "所有页" };
2454: // decision / phase / item state 的值域与后端枚举逐字一致（词义前后端一致，不新增同义词）。
```

行 2770–2834:

```text
2770: function discoveryReviewTabButtons() {
2771:     const els = discoveryReviewElements();
2772:     return { pipeline: els.tabPipeline, review: els.tabReview, history: els.tabHistory };
2773: }
2774: 
2775: function discoveryReviewPanes() {
2776:     const els = discoveryReviewElements();
2777:     return { pipeline: els.pipelinePane, review: els.reviewPane, history: els.historyPane };
2778: }
2779: 
2780: /** S-1/I-3：唯一页签切换接缝；aria-selected/tabindex/hidden 同源同步。 */
2781: function selectDiscoveryReviewTab(name, options) {
2782:     options = options || {};
2783:     if (DISCOVERY_REVIEW_TABS.indexOf(name) < 0) name = "pipeline";
2784:     const els = discoveryReviewElements();
2785:     if (!discoveryReviewState.active) {
2786:         // 其他任务：页签隐藏、采集内容常显，审核页签永不显示。
2787:         if (els.tabs) els.tabs.hidden = true;
2788:         if (els.pipelinePane) els.pipelinePane.hidden = false;
2789:         if (els.reviewPane) els.reviewPane.hidden = true;
2790:         if (els.historyPane) els.historyPane.hidden = true;
2791:         return;
2792:     }
2793:     discoveryReviewBumpAll();
2794:     discoveryReviewState.tab = name;
2795:     if (typeof apvSetVisible === "function") apvSetVisible(name === "pipeline");
2796:     const buttons = discoveryReviewTabButtons();
2797:     const panes = discoveryReviewPanes();
2798:     DISCOVERY_REVIEW_TABS.forEach(function (key) {
2799:         const btn = buttons[key];
2800:         const pane = panes[key];
2801:         const selected = key === name;
2802:         if (btn) {
2803:             btn.classList.toggle("is-active", selected);
2804:             if (typeof btn.setAttribute === "function") {
2805:                 btn.setAttribute("aria-selected", selected ? "true" : "false");
2806:                 btn.setAttribute("tabindex", selected ? "0" : "-1");
2807:             }
2808:             if (selected && options.focus === true && typeof btn.focus === "function") btn.focus();
2809:         }
2810:         if (pane) pane.hidden = !selected;
2811:     });
2812:     if (name !== "review") stopDiscoveryReviewPolling();
2813:     if (name === "review") {
2814:         ensureDiscoveryReviewData();
2815:         resumeDiscoveryReviewPolling();
2816:     } else if (name === "history") {
2817:         loadDiscoveryReviewRecords();
2818:     }
2819: }
2820: 
2821: function discoveryReviewTabKeydown(event) {
2822:     if (!event) return;
2823:     const key = event.key;
2824:     const total = DISCOVERY_REVIEW_TABS.length;
2825:     const current = Math.max(0, DISCOVERY_REVIEW_TABS.indexOf(discoveryReviewState.tab));
2826:     let next = null;
2827:     if (key === "ArrowRight" || key === "Right") next = (current + 1) % total;
2828:     else if (key === "ArrowLeft" || key === "Left") next = (current - 1 + total) % total;
2829:     else if (key === "Home") next = 0;
2830:     else if (key === "End") next = total - 1;
2831:     else return;
2832:     if (typeof event.preventDefault === "function") event.preventDefault();
2833:     selectDiscoveryReviewTab(DISCOVERY_REVIEW_TABS[next], { focus: true });
2834: }
```

行 27112–27142:

```text
27112: 
27113: async function apvLoadCapabilities(generation) {
27114:     const token = apvToken();
27115:     if (!token || token.generation !== generation) return;
27116:     try {
27117:         const capability = await apvGet(APV_PATH + "/capabilities", token);
27118:         if (!capability || !apvIsCurrent(token)) return;
27119:         apvState.capability = capability;
27120:         apvState.capabilityStatus = "ready";
27121:         const checkbox = $("#taskLaunchApolloAssist");
27122:         checkbox.disabled = !capability.enabled || !capability.ready;
27123:         checkbox.checked = capability.enabled && capability.ready && capability.apolloDefault === true;
27124:         const reasons = (capability.reasonCodes || []).join("、");
27125:         $("#taskLaunchApolloHint").textContent = !capability.enabled ? "资格核验未启用"
27126:             : !capability.ready ? "Apollo 辅助核验不可用：" + reasons
27127:             : "可靠资格证据足够的直接准入；其余进入 Apollo 匹配。姓名与当前／历史任职或教授职称匹配为「已明确」准入，专业辅助匹配为「未明确」准入；仅姓名或同名歧义不准入。";
27128:     } catch (error) {
27129:         if (!apvIsCurrent(token)) return;
27130:         apvState.capabilityStatus = "error";
27131:         $("#taskLaunchApolloHint").textContent = "资格核验能力读取失败：" + error.message + "；请重新打开弹窗重试";
27132:         throw error;
27133:     }
27134: }
27135: 
27136: function apvCanLaunch(requested) {
27137:     const s = apvState;
27138:     if (!s || s.capabilityStatus !== "ready" || (requested && (!s.capability?.enabled || !s.capability?.ready))) {
27139:         showStatus("资格核验能力未就绪，请重新打开弹窗检查配置", "error");
27140:         return false;
27141:     }
27142:     return true;
```

## 既有留存全部调用点

```text
src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt:14:import com.weibo.talentintroduction.discovery.sample.DiscoverySampleCapture
src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt:107:                val attempt = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, url)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt:113:                    ).also { bytes -> if (bytes != null) DiscoverySampleCapture.fullBody(attempt, bytes) }
src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt:118:                            DiscoverySampleCapture.headers(attempt, error.statusCode.value(), error.responseHeaders?.contentType?.toString())
src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt:123:                    DiscoverySampleCapture.endAttempt(attempt, attemptError)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:48:import com.weibo.talentintroduction.discovery.sample.DiscoverySampleCapture
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:141:    private val sampleCapture: DiscoverySampleCapture? = null
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1614:                sampleCapture.capture(sampleContext, paper) { source.extractAuthorEmails(paper) }
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2106:                            sampleCapture.capture(sampleContext, paper) { source.extractAuthorEmails(paper) }
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:11:import com.weibo.talentintroduction.discovery.sample.DiscoverySampleCapture
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:235:        val attempt = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PDF_URL, uri.toString(), properties)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:246:                        DiscoverySampleCapture.headers(attempt, response.rawStatusCode, contentType?.toString())
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:276:                DiscoverySampleCapture.fullBody(attempt, bytes)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:293:                    DiscoverySampleCapture.headers(attempt, error.statusCode.value(), error.responseHeaders?.contentType?.toString())
src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt:298:            DiscoverySampleCapture.endAttempt(attempt, attemptError)
src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCapture.kt:12:class DiscoverySampleCapture(private val store: DiscoverySampleStore, private val clock: Clock = Clock.systemUTC()) {
src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCapture.kt:24:    private class Scope(val owner: DiscoverySampleCapture) {
```

## 核验快照读写点

```text
src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt:36:    val apolloRequested: Boolean, val settingsJson: String, val inputClosed: Boolean = false,
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:207:            val matchingVersion = mapper.readTree(run.settingsJson).path("apolloMatchingVersion").asInt(1)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:209:            val aliasVersion = mapper.readTree(run.settingsJson).path("institutionAliasVersion").asInt(0)
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt:212:                val frozenRule = mapper.readTree(run.settingsJson).get("ruleVersion")
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt:74:        return DiscoveryVerificationExecution(executionId, repository.createRun(executionId, properties.mode, apollo, settings, clock.instant()))
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:342:    fun createRun(executionId: Long, mode: VerificationMode, apolloRequested: Boolean, settingsJson: String, now: Instant): VerificationRun {
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:343:        require(executionId > 0 && settingsJson.isNotBlank())
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:344:        jdbc.update("INSERT IGNORE INTO discovery_verification_run (execution_id,mode,policy_version,apollo_requested,settings_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:345:            executionId, mode.name, mode.policyVersion, apolloRequested, settingsJson, now.verificationUtc(), now.verificationUtc())
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:347:        require(run.mode == mode && run.apolloRequested == apolloRequested && run.settingsJson == settingsJson) { "Run snapshot is immutable" }
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt:734:            rs.getString("policy_version"), rs.getBoolean("apollo_requested"), rs.getString("settings_json"), rs.getBoolean("input_closed"), rs.getLong("revision"), rs.time("created_at")!!, rs.time("updated_at")!!) }
```

## 队列抽取结果读写点

```text
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:993:     * 已保存的抽取结果（`extraction_json` 非空）**直接消费、不再下载**；消费前重新读取 pipeline 的
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1017:            var extractionJson = job.extractionJson
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1018:            if (extractionJson.isNullOrEmpty()) {
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1036:                        val bytes = extraction.extractionJson.toByteArray(Charsets.UTF_8).size.toLong()
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1037:                        val saved = repository.saveExtraction(
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1039:                            extractionJson = extraction.extractionJson, extractionBytes = bytes, now = now()
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1046:                        extractionJson = extraction.extractionJson
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt:1089:            val consumption = expertDiscoveryService.consumeQueuedItem(envelope, extractionJson!!, verification)
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:161:    val extractionJson: String?,
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:281:    fun saveExtraction(
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:285:        extractionJson: String,
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1026:    override fun saveExtraction(
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1030:        extractionJson: String,
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1042:               SET extraction_json = ?, payload_bytes = payload_bytes + ?,
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1046:            extractionJson, extractionBytes, toDb(now), jobId,
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1103:        val releasedReserved = if (job.extractionJson.isNullOrEmpty()) job.reservedResultBytes else 0L
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1166:        val releasedReserved = if (job.extractionJson.isNullOrEmpty()) job.reservedResultBytes else 0L
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1390:            "UPDATE discovery_paper_job SET metadata_json = '', extraction_json = NULL, payload_bytes = 0, reserved_result_bytes = 0, updated_at = ? WHERE id IN ($placeholders)",
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1523:        extractionJson = rs.getString("extraction_json"),
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt:1553:                "extraction_json, payload_bytes, reserved_result_bytes, status, attempts, next_attempt_at, " +
```

## 复用 CSS 类的使用文件

```text
src/main/resources/static/meeting-records.js
src/main/resources/static/intro-variants-preview.html
src/main/resources/static/contact-details.js
src/main/resources/static/app.js
src/main/resources/static/styles.css
src/main/resources/static/index.html
src/main/resources/static/contact-history.js
src/main/resources/static/mailbox-chat.js
src/main/resources/static/trust-reply-workbench.js
src/main/resources/static/meeting-confirmation.js
```

## 自审补充：缓存协议、预算、测试与旧配置样式

### src/main/resources/static/styles.css
SHA256 `c19989a43d11b2de9a5d77966d9256e1c969a23a7bbf538a1e47f33e98fdbce9`

```text
12966: }
12967: #taskProgressModal[data-discovery-review="true"] .apv-option {
12968:     padding: 10px 12px;
12969:     border: 1px solid var(--panel-border);
12970:     border-radius: 7px;
12971:     background: #f8fafc;
12972: }
12973: #taskProgressModal[data-discovery-review="true"] .apv-option label {
12974:     display: flex;
12975:     flex-direction: row;
12976:     align-items: center;
12977:     justify-content: flex-start;
12978:     gap: 7px;
12979:     color: #1e293b;
12980:     font-size: 12px;
12981:     font-weight: 600;
12982:     text-transform: none;
12983:     letter-spacing: normal;
12984: }
12985: #taskProgressModal[data-discovery-review="true"] .apv-option input[type="checkbox"] {
12986:     width: 14px;
12987:     height: 14px;
12988:     min-width: 14px;
12989:     min-height: 14px;
12990:     flex: 0 0 14px;
12991:     margin: 0;
12992:     padding: 0;
12993:     accent-color: #1e40af;
12994:     cursor: pointer;
12995: }
12996: #taskProgressModal[data-discovery-review="true"] .apv-option p {
12997:     margin: 5px 0 0 21px;
12998:     color: #475569;
12999:     font-size: 11px;
13000:     line-height: 1.7;
13001: }
13002: #taskProgressModal[data-discovery-review="true"] .apv-rules {
13003:     margin-top: 6px;
13004:     color: #475569;
13005:     font-size: 11px;
13006:     line-height: 1.8;
13007: }
13008: #taskProgressModal[data-discovery-review="true"] .apv-rules summary {
13009:     color: #1e40af;
13010:     cursor: pointer;
13011: }
13012: #taskProgressModal[data-discovery-review="true"] .apv-logs .task-modal-log-table-wrapper {
13013:     max-height: none;
```

### src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
SHA256 `73ff71a6b7ad3ae2952cdab03436f31ee893b5a021bd992083907be21e32b3bb`

```text
543:         } catch (e: Exception) {
544:             stats.refreshGlobalCounts()
545:             // I-5：异常结束同样清空「仍待执行」的计划时间，避免页面一直显示已过期的等待。
546:             clearRetryWait(stats)
547:             val totalElapsed = System.currentTimeMillis() - startTime
548:             val details = buildProgressDetails(stats).toMutableMap()
549:             details["traffic"] = trafficSession.snapshot()
550:             details["summaryText"] = buildSummaryText(stats, totalElapsed, DiscoveryTerminalStatus.FAILED)
551:             progressStore.update("EXPERT_DISCOVERY", TaskProgress(
552:                 taskType = "EXPERT_DISCOVERY", status = "FAILED",
553:                 batchNumber = -1, processedCount = stats.totalPapers.toLong(), totalCount = 0,
554:                 message = "失败: ${e.message}",
555:                 details = details, errors = snapshotErrors(stats)
556:             ), execId)
557:             throw e
558:         } finally {
559:             verificationRegistration?.closeInput(verification)
560:         }
561:         stats.refreshGlobalCounts()
562:         val finalElapsed = System.currentTimeMillis() - startTime
563:         return DiscoveryResult(
564:             triggeredBy, stats,
565:             summaryText = buildSummaryText(stats, finalElapsed, terminalStatusOfRun),
566:             traffic = trafficSession.snapshot()
567:         )
```

```text
2140:      */
2141:     fun consumeQueuedItem(
2142:         envelope: QueuedItemEnvelope,
2143:         extractionJson: String,
2144:         verification: DiscoveryVerificationExecution
2145:     ): QueuedItemConsumption {
2146:         if (envelope.payloadVersion != QUEUED_PAYLOAD_VERSION_V1) {
2147:             return QueuedItemConsumption(unrecoverableReason = QUEUE_UNKNOWN_PAYLOAD_VERSION)
2148:         }
2149:         val extraction = try {
2150:             objectMapper.readValue(extractionJson, EmailExtractionOutcome::class.java)
2151:         } catch (e: Exception) {
2152:             return QueuedItemConsumption(unrecoverableReason = QUEUE_EXTRACTION_UNREADABLE)
2153:         }
2154:         if (extraction.identityRuleVersion != DiscoveryIdentity.EXTRACTION_VERSION) {
2155:             return QueuedItemConsumption(unrecoverableReason = "IDENTITY_EXTRACTION_VERSION_UNSUPPORTED")
2156:         }
2157:         val identityFactory = try {
2158:             when (envelope.unit) {
2159:                 QueueItemUnit.RECORD -> orcidIdentityFactory(readOrcidRecord(envelope.payloadJson))
2160:                 else -> paperIdentityFactory(readPaper(envelope.payloadJson))
```

### src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
SHA256 `534498bc4d2a571f9a282a8c69ca1e9670d048c9a953498e674e9e99b2e3e8d7`

```text
55:     fun tick() {
56:         if (!properties.workerEnabled || authBlocked.get() || !running.compareAndSet(false, true)) return
57:         try {
58:             recovery.getIfAvailable()?.recoverReserved(10)
59:             repository.listProjectionDue(10).forEach { commits.project(it) }
60:             repeat(10) {
61:                 if (!properties.workerEnabled || authBlocked.get()) return
62:                 val lease = repository.claimDue(clock.instant(), properties.leaseSeconds) ?: return
63:                 process(lease)
64:             }
65:         } finally { running.set(false) }
```

```text
184:             when (result) {
185:                 is VerificationHttpResult.RateLimited -> repository.reschedule(lease.id, token, lease.generation, maxOf(now, result.retryAt),
186:                     lease.transientAttempts, json(), "[\"RATE_LIMIT\"]", now)
187:                 is VerificationHttpResult.TransientFailure -> {
188:                     val count = lease.transientAttempts + 1
189:                     if (count >= properties.maxTransientAttempts) fail(result.code, count)
190:                     else repository.reschedule(lease.id, token, lease.generation, now.plusSeconds(longArrayOf(30, 120, 600)[count - 1]),
191:                         count, json(), mapper.writeValueAsString(listOf(result.code)), now)
192:                 }
193:                 is VerificationHttpResult.PermanentFailure -> if (result.code == "APOLLO_CREDITS_EXHAUSTED") {
194:                     repository.reschedule(lease.id, token, lease.generation, now.plusSeconds(1800),
195:                         lease.transientAttempts, json(), "[\"APOLLO_CREDITS_EXHAUSTED\"]", now)
196:                 } else fail(result.code)
197:                 else -> error("Unexpected retry result")
198:             }
199:         }
200:         try {
```

### src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
SHA256 `22317f5bb5c5cd4ec1b472129096847abc997cb533fffa16ff4439f54d164325`

```text
457:         return true
458:     }
459: 
460:     @Transactional
461:     fun reserveAttemptWithBudget(itemId: Long, token: String, generation: Long, request: VerificationAttemptRequest,
462:                                  requestsPerMinute: Int, requestsPerDay: Int, maxRequestsPerItem: Int, now: Instant): VerificationAttemptReservation {
463:         require(requestsPerMinute > 0 && requestsPerDay > 0 && maxRequestsPerItem > 0)
464:         val item = lockItem(itemId)
465:         if (!owns(item, token, generation, now)) return VerificationAttemptReservation(deferred = VerificationBudgetReason.LEASE_LOST)
466:         // Lock is retained through COMMIT, not merely through INSERT. Same bound JDBC connection.
467:         if (request.provider == VerificationProvider.APOLLO) {
468:             val acquired = jdbc.execute(ConnectionCallback<Boolean> { connection ->
469:                 connection.prepareStatement("SELECT GET_LOCK('discovery_verification_apollo_budget',1)").use { statement ->
470:                     statement.executeQuery().use { rs -> rs.next(); rs.getInt(1) == 1 }
471:                 }.also { acquired ->
472:                     if (acquired) TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
473:                         override fun afterCompletion(status: Int) {
474:                             connection.prepareStatement("SELECT RELEASE_LOCK('discovery_verification_apollo_budget')").use { it.execute() }
475:                         }
476:                     })
477:                 }
478:             }) == true
479:             if (!acquired) return VerificationAttemptReservation(deferred = VerificationBudgetReason.LOCK_BUSY)
480:         }
481:         val duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt WHERE request_key=? FOR UPDATE", Long::class.java, request.requestKey)!!
482:         if (duplicate > 0) return VerificationAttemptReservation(deferred = VerificationBudgetReason.DUPLICATE_REQUEST)
483:         // Apollo quotas are enforced by the provider (429/Retry-After). Legacy quota
484:         // arguments remain compatible with existing callers/configuration, but do not cap Apollo.
485:         if (request.provider != VerificationProvider.APOLLO && item.requestCount >= maxRequestsPerItem)
486:             return VerificationAttemptReservation(deferred = VerificationBudgetReason.ITEM_LIMIT)
487:         if (request.provider == VerificationProvider.APOLLO && request.queryKind == VerificationQueryKind.DETAIL) {
488:             val retryAt = detailCreditsRetryAt()
489:             if (retryAt != null && now < retryAt)
490:                 return VerificationAttemptReservation(deferred = VerificationBudgetReason.CREDITS_WAIT, retryAt = retryAt)
491:         }
492:         jdbc.update("""INSERT INTO discovery_verification_attempt (item_id,request_key,provider,query_kind,page_number,attempt_number,requested_at,result_code,request_json)
493:             VALUES (?,?,?,?,?,?,?,'DISPATCH_RESERVED',?)""", itemId, request.requestKey, request.provider.name, request.queryKind.name,
494:             request.pageNumber, request.attemptNumber, now.verificationUtc(), request.requestJson)
495:         val id = jdbc.queryForObject("SELECT id FROM discovery_verification_attempt WHERE request_key=?", Long::class.java, request.requestKey)!!
496:         jdbc.update("UPDATE discovery_verification_item SET request_count=request_count+1,updated_at=? WHERE id=?", now.verificationUtc(), itemId)
497:         bump(item.ownerExecutionId, now)
498:         return VerificationAttemptReservation(attemptId = id)
499:     }
```

### src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt
SHA256 `08ffb9c945e9073bcc719df740d9fe7dd7c7b87e5c09206a78ab1a6f1e769a51`

```text
52:     @Test fun `new extraction cache version is accepted and prior version remains rejected`() {
53:         assertEquals(20260925, DiscoveryIdentity.VERSION)
54:         // I-5：抽取兼容版本随 PDF 标记/残缺邮箱规则升级；历史身份证明版本 20260925 不变。
55:         assertEquals(20261004, DiscoveryIdentity.EXTRACTION_VERSION)
56:         val profile = verified()
57:         assertTrue(DiscoveryIdentity.allowed(profile))
58:         assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification =
59:             profile.identityVerification!!.copy(version = 20261003))))
60:         assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification =
61:             profile.identityVerification!!.copy(version = 20260929))))
```

### src/test/js/discoveryReview.test.js
SHA256 `7109afe1d560cb4695b18fd18d0c976e745d758ee30aedacda98da81b7d92960`

```text
441:     });
442: 
443:     it("keeps one cache key for all eleven assets and other task modals at 700px", () => {
444:         const keys = Array.from(indexHtml.matchAll(/[?"]v=([^"'&<>]+)/g)).map((m) => m[1]);
445:         const unique = Array.from(new Set(keys));
446:         assert.strictEqual(keys.length, 16, "exactly sixteen cache-busted assets");
447:         assert.strictEqual(unique.length, 1, "all assets must share one cache key");
448:         assert.match(unique[0], /^[0-9]{8}-[a-z0-9-]+$/, "cache key must be <yyyymmdd>-<slug>");
449:         assert.notStrictEqual(unique[0], "20261004-mailbox-suspension-followup",
450:             "the previous release key must be bumped");
451:         // 只有 data-discovery-review 标记触发宽度；其余任务仍是 700px 基规则。
452:         assert.ok(stylesCss.includes(".modal-content.task-modal {\n    max-width: 700px;\n}"));
```

### 构造器与迁移测试接缝
```text
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:147:        val registration = DiscoveryVerificationRegistrationService(props, flow.repository, flow.writer, flow.policy,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:390:        val registration = DiscoveryVerificationRegistrationService(off, flow.repository, flow.writer, flow.policy,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:451:            val otherRegistration = DiscoveryVerificationRegistrationService(f.props, f.flow.repository, f.flow.writer,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:501:            val budgetRegistration = DiscoveryVerificationRegistrationService(f.props, f.flow.repository, f.flow.writer,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt:128:        val registration = DiscoveryVerificationRegistrationService(properties, flow.repository, flow.writer, flow.policy,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt:253:            val disabled = DiscoveryVerificationRegistrationService(DiscoveryVerificationProperties(), f.flow.repository, f.flow.writer,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt:86:        return DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions, jdbc,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt:359:        val interruptRegistration = DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt:376:        val cancelRegistration = DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:34:                    val registration = DiscoveryVerificationRegistrationService(DiscoveryVerificationProperties(enabled = true, mode = VerificationMode.ENFORCE, apolloApiKey = "fixture-key"), f.repository, f.writer, f.policy,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:339:            val row = DiscoveryVerificationQueryService(f.flow.repository, f.flow.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:258:                val row = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:337:                    val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(item.id)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:371:            val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(item.id)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:785:            val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:974:            val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:1074:            val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:1184:            val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt:16:    private val service = DiscoveryVerificationQueryService(repository, admissions,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt:157:        val disabled = DiscoveryVerificationQueryService(repository, admissions, DiscoveryVerificationProperties()).capabilities()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:71:                    val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(done.id)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:321:                        val query = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:550:                        val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(row.id)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:560:                        assertNull(DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(row.id).diagnostics)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:621:                    val api = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(second.id)
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:628:                    val bounded = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).item(second.id).diagnostics!!
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt:29:    private val query = DiscoveryVerificationQueryService(repository, admissions, DiscoveryVerificationProperties())
src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationReportingIT.kt:222:                val reader = DiscoveryVerificationQueryService(repository, DiscoveryReviewRepository(jdbc), flow.props,
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt:164:                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt:53:            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:28:                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:284:                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:385:                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt:476:                org.flywaydb.core.Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt:208:            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt:57:            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
```
