# 代码路径回执
审计日期：2026-10-21；HEAD：962fcfe924ad7562a4ea917d174180df82ac3b5c。仅代码/结构，不含生产人物原文和密钥。
以下摘要与行号直接从工作区文件生成。复验时若摘要改变，重新核对关联计划。
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt`

SHA256 `4dcac143c184bd8ac36efaf382598bd7752435e95fbad8942bb066942ac542cb`

```text
22: class RequestPermit(val attemptId: Long, val provider: VerificationProvider) {
25:     internal fun consume(expected: VerificationProvider) {
30: sealed class VerificationHttpResult<out T> {
31:     data class Success<T>(val value: T, val responseSha256: String, val httpStatus: Int = 200) : VerificationHttpResult<T>()
32:     data class RateLimited(val retryAt: Instant, val httpStatus: Int = 429) : VerificationHttpResult<Nothing>()
33:     data class TransientFailure(val code: String, val httpStatus: Int? = null) : VerificationHttpResult<Nothing>()
34:     data class PermanentFailure(val code: String, val httpStatus: Int? = null) : VerificationHttpResult<Nothing>()
37: data class VerificationInstitution(val name: String, val standardId: String? = null, val aliases: Set<String> = emptySet())
38: data class ApolloCandidate(val id: String, val firstName: String?, val lastName: String?, val obfuscatedLastName: String?,
41: data class ApolloEmployment(val institution: VerificationInstitution?, val title: String?, val current: Boolean?,
43: data class ApolloSearchQuery(val name: String, val page: Int = 1, val institution: VerificationInstitution? = null) {
46: data class ApolloSearchPage(val candidates: List<ApolloCandidate>, val totalEntries: Long, val page: Int) {
50: internal fun verificationRestTemplate(properties: DiscoveryVerificationProperties): RestTemplate {
52:         override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
61:             override fun hasError(response: org.springframework.http.client.ClientHttpResponse) = false
62:             override fun handleError(response: org.springframework.http.client.ClientHttpResponse) = Unit
68: internal class VerificationHttp(private val http: RestTemplate, private val clock: Clock) {
70:     fun request(uri: URI, method: HttpMethod, headers: Map<String, String>, permit: RequestPermit,
85:                     status !in 200..299 -> VerificationHttpResult.PermanentFailure(when (status) {
87:                         400, 422 -> "REQUEST_INVALID"
111: class ApolloPeopleSearchClient(
119:     fun detail(id: String, permit: RequestPermit): VerificationHttpResult<ApolloCandidate> {
144:         return VerificationHttpResult.Success(candidate, result.responseSha256, result.httpStatus)
146:     fun search(query: ApolloSearchQuery, permit: RequestPermit): VerificationHttpResult<ApolloSearchPage> {
177:         return VerificationHttpResult.Success(ApolloSearchPage(candidates, total.longValue(), query.page), result.responseSha256, result.httpStatus)
179:     private fun text(node: JsonNode, field: String): String? = node.path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.take(4096)
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`

SHA256 `aad3a14e2369e654055630c7d053806fcffda8a3648805bdc31cc5537fffc60c`

```text
23: data class VerificationWorkerEvidence(
29:     val ruleVersion: Int = 0
33: class DiscoveryVerificationWorker(
48:     @PreDestroy fun close() { heartbeat.shutdownNow() }
51:     fun isAuthBlocked(): Boolean = authBlocked.get()
54:     fun tick() {
67:     private class LeaseLost : IllegalStateException()
68:     private class BudgetDeferred(val reason: VerificationBudgetReason) : IllegalStateException()
69:     private enum class ContextIssue { NONE, PARSER_DRIFT, INVALID_PROTOCOL }
72:     internal fun process(lease: VerificationItem) {
80:         var payload = VerificationWorkerEvidence(ruleVersion = 1)
87:         fun json() = rawPayload ?: mapper.writeValueAsString(payload)
88:         fun contextIssue(snapshot: VerificationSnapshot, saved: InstitutionNormalization): ContextIssue {
111:         fun evaluate(snapshot: VerificationSnapshot): VerificationDecision {
117:                     ContextIssue.PARSER_DRIFT -> return rules.evaluate(snapshot, payload.evidence, payload.search, ruleVersion = payload.ruleVersion).copy(
121:                         institutionNormalization = normalization, ruleVersion = payload.ruleVersion)
124:             return rules.evaluate(snapshot, payload.evidence, payload.search, ruleVersion = payload.ruleVersion)
126:         fun ensureOwned() { if (lost.get()) throw LeaseLost() }
127:         fun save() {
131:         fun reserve(provider: VerificationProvider, kind: VerificationQueryKind, page: Int, request: String): RequestPermit {
141:         fun completed(permit: RequestPermit, result: VerificationHttpResult<*>, response: Any? = null) {
156:                 response?.let { mapper.writeValueAsString(it) }, (result as? VerificationHttpResult.Success)?.responseSha256,
160:         fun finish(decision: VerificationDecision) {
169:         fun fail(code: String, transientAttempts: Int = lease.transientAttempts) {
175:         fun retry(result: VerificationHttpResult<Nothing>) {
199:             val aliasVersion = mapper.readTree(run.settingsJson).path("institutionAliasVersion").asInt(0)
200:             require(aliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
235:                     search = ApolloSearchEvidence(matchingVersion = matchingVersion, academicResearchFields = snapshot.profile.researchFields, institutionAliasVersion = aliasVersion),
237:                         formalName = matchingVersion == 2, ruleVersion = payload.ruleVersion))
262:                     if (last && payload.ruleVersion >= 1 && search.matchingVersion == 2 &&
263:                         search.candidates.none { rules.nameMatches(it, rules.allowedNames(snapshot, payload.evidence), search.queryNames, true, payload.ruleVersion) &&
264:                             rules.matchReason(snapshot, payload.evidence, it, null, search.institutionAliasVersion) in
278:                         finish(decision.copy(reasonCodes = (decision.reasonCodes + "SEARCH_INCOMPLETE" + "SEARCH_PAGE_LIMIT").distinct()))
288:                             (rules.nameMatches(it, names, payload.search!!.queryNames, masked = true, ruleVersion = payload.ruleVersion) ||
289:                                 payload.ruleVersion >= 1 && rules.emailNameMatches(snapshot, payload.evidence, it, payload.search!!.institutionAliasVersion, masked = true))
291:                         val strong = candidates.filter { rules.matchReason(snapshot, payload.evidence, it, null, payload.search?.institutionAliasVersion ?: 0) in
375:     internal fun searchQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
376:         normalization: InstitutionNormalization? = null, formalName: Boolean = true, ruleVersion: Int = 0): List<ApolloSearchQuery> {
379:             return nameQueries(snapshot, evidence, formalName, ruleVersion) + listOfNotNull(normalization.queryInstitution?.let {
390:         return nameQueries(snapshot, evidence, formalName, ruleVersion) + listOfNotNull(institutions.singleOrNull()?.let {
395:     internal fun surnameFallback(snapshot: VerificationSnapshot, normalization: InstitutionNormalization?): ApolloSearchQuery? {
402:     private fun nameQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>, formalName: Boolean = true, ruleVersion: Int = 0) =
406:             .let { names -> if (ruleVersion >= 1) names.flatMap(rules::queryNameVariants) else names }
409:     private fun legacySearchQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): List<ApolloSearchQuery> {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`

SHA256 `aa8d9fce8fb3c252ea4355b61c30f003cbdef58e868b736c6c7893d839d69e3a`

```text
13: data class ApolloSearchEvidence(val candidates: List<ApolloCandidate> = emptyList(), val broadComplete: Boolean = false,
17:     val institutionAliasVersion: Int = 0) {
18:     fun merge(page: ApolloSearchPage, query: ApolloSearchQuery, broad: Boolean, allQueriesComplete: Boolean = false): ApolloSearchEvidence {
30: data class VerificationDecision(val qualification: VerificationQualification, val route: VerificationRoute,
35: class DiscoveryVerificationRules(private val clock: Clock = Clock.systemUTC()) {
37:     fun evaluate(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
39:         institutionNormalization: InstitutionNormalization? = null, ruleVersion: Int = 0): VerificationDecision {
40:         require(ruleVersion in 0..1)
41:         fun sameName(left: String, right: String) = sameName(left, right, ruleVersion)
44:         require(search == null || search.institutionAliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
45:         fun decision(q: VerificationQualification, reasons: List<String>, missing: List<String> = emptyList(),
63:             ruleVersion >= 1 && it.assertionSource == AssertionSource.SELF }
96:             val named = search.candidates.filter { (nameMatches(it, namesToMatch, search.queryNames, masked = false, ruleVersion = ruleVersion) ||
97:                 ruleVersion >= 1 && emailNameMatches(snapshot, evidence, it, search.institutionAliasVersion)) }
101:                 matchReason(snapshot, evidence, candidate, search.academicResearchFields, search.institutionAliasVersion)?.let { candidate to it }
109:                     (nameMatches(it, namesToMatch, search.queryNames, masked = true, ruleVersion = ruleVersion) ||
110:                         ruleVersion >= 1 && emailNameMatches(snapshot, evidence, it, search.institutionAliasVersion, masked = true)) &&
111:                     matchReason(snapshot, evidence, it, search.academicResearchFields, search.institutionAliasVersion) != null })
117:             return decision(qualification, aliasReasons + (if (ruleVersion >= 1 && !nameMatches(candidate, namesToMatch, search.queryNames, false, ruleVersion))
156:         fun institutionMatches(candidate: ApolloCandidate): Boolean {
200:     internal fun allowedNames(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): Set<String> =
204:     private fun explicitName(candidate: ApolloCandidate): String? = candidate.fullName ?:
207:     internal fun nameMatches(candidate: ApolloCandidate, names: Set<String>, queries: Set<String>, masked: Boolean, ruleVersion: Int = 1): Boolean {
208:         fun sameName(left: String, right: String) = sameName(left, right, ruleVersion)
213:         } else masked && names.flatMap { if (ruleVersion >= 1) nameVariants(it) else listOf(it) }.any { known ->
224:     internal fun matchReason(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
225:         candidate: ApolloCandidate, academicFields: String?, institutionAliasVersion: Int = 0): String? {
228:         fun institutionMatch(institution: VerificationInstitution?): Boolean = institution != null && originals.any {
229:             DiscoveryInstitutionNormalizer.compareWithAliases(it, institution, institutionAliasVersion) == InstitutionComparison.MATCH
251:     internal fun apolloProfessor(role: String?): Boolean {
259:     fun identityEmployments(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): List<VerificationEvidence> {
269:     fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
272:     internal fun nameVariants(name: String): List<String> {
275:         fun ascii(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
278:     internal fun queryNameVariants(name: String): List<String> {
285:     fun sameName(left: String, right: String, ruleVersion: Int = 1): Boolean {
286:         fun matches(a: List<String>, b: List<String>): Boolean {
288:             return ruleVersion >= 1 && a.size >= 3 && a.size == b.size && a.first() == b.first() && a.last() == b.last() &&
293:         val aa = if (ruleVersion >= 1) nameVariants(left) else listOf(left)
294:         val bb = if (ruleVersion >= 1) nameVariants(right) else listOf(right)
298:     internal fun familyNameHint(snapshot: VerificationSnapshot): String = normalizeName(
303:     internal fun emailNameMatches(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>, candidate: ApolloCandidate,
322:     fun sameInstitution(a: VerificationInstitution, b: VerificationInstitution): Boolean {
327:     private fun maskedCompatible(candidate: ApolloCandidate, name: String): Boolean {
335:     private fun value(evidence: VerificationEvidence, key: String): String = runCatching { mapper.readTree(evidence.value).path(key).asText("") }.getOrDefault("")
336:     private fun isProfessor(role: String): Boolean {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt`

SHA256 `39e28c63f1c3e2e48aa8878ab652edfa770ee540ea85b4bbd62c791eebe7fcfc`

```text
11: enum class InstitutionSource { PROFILE, ORCID_EMPLOYMENT, APOLLO_CANDIDATE }
14: enum class InstitutionSubjectStatus { RESOLVED, AMBIGUOUS, UNRESOLVED }
17: enum class InstitutionComparison { MATCH, DIFFERENT, UNKNOWN }
19: data class InstitutionRemovedPart(val text: String, val rule: String)
26: data class InstitutionInputNormalization(
38: data class InstitutionNormalization(
44: data class InstitutionEmploymentInput(val sourceRef: String, val raw: String?)
45: data class InstitutionCandidateInput(val sourceRef: String, val raw: String?)
111:     private sealed class Parsed {
112:         data class Subjects(val subjects: List<String>, val removed: List<InstitutionRemovedPart>) : Parsed()
113:         data class Multiple(val subjects: List<String>, val removed: List<InstitutionRemovedPart>) : Parsed()
114:         data class Unresolved(val removed: List<InstitutionRemovedPart>) : Parsed()
120:     fun resolve(raw: String?, source: InstitutionSource, sourceRef: String): InstitutionInputNormalization {
122:         fun record(status: String, reason: String, subjects: List<String> = emptyList(),
186:     fun build(profileInstitution: String?, employments: List<VerificationEvidence>,
190:     fun buildInputs(profileInstitution: String?, employments: List<InstitutionEmploymentInput>,
204:     fun withCandidates(base: InstitutionNormalization, candidates: List<ApolloCandidate>): InstitutionNormalization {
213:     fun validate(expected: InstitutionNormalization, saved: InstitutionNormalization, includeCandidates: Boolean = true) {
226:     fun queryInstitution(inputs: List<InstitutionInputNormalization>): String? {
234:     fun employmentInputs(employments: List<VerificationEvidence>): List<InstitutionEmploymentInput> {
243:     fun candidateInputs(candidates: List<ApolloCandidate>): List<InstitutionCandidateInput> =
247:     fun compare(left: InstitutionInputNormalization, right: InstitutionInputNormalization): InstitutionComparison {
259:     fun compare(left: VerificationInstitution, right: VerificationInstitution,
284:     const val ALIAS_VERSION = 1
288:     fun compareWithAliases(left: VerificationInstitution, right: VerificationInstitution, aliasVersion: Int): InstitutionComparison {
289:         require(aliasVersion in 0..ALIAS_VERSION) { "Unsupported institution alias version" }
294:         fun knownSubject(institution: VerificationInstitution): Boolean {
303:     fun canonical(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
306:     fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
309:     private data class ContactScan(val text: String, val removed: List<String>, val contactOnly: Boolean, val glue: Boolean)
311:     private fun scanContact(segment: String): ContactScan {
333:     private fun jointSplit(text: String): List<String> {
370:     private fun parseSegment(text: String, source: InstitutionSource): Parsed {
432:     private fun cleanStructured(whole: String, parts: List<String>): Boolean =
437:     private fun isOrganisation(part: String): Boolean =
441:     private fun gluedUnitAndOrganisation(part: String): Boolean {
446:     private fun isAddressTail(parts: List<String>, organisation: String, hadLeadingUnit: Boolean): Boolean {
459:     private fun isStrongAddressPart(part: String): Boolean =
462:     private fun isSystemName(organisation: String): Boolean =
465:     private fun isCampusQualifier(part: String): Boolean =
468:     private fun isLocationToken(part: String): Boolean {
475:     private fun isCountry(part: String): Boolean =
478:     private fun isAddressPart(part: String): Boolean =
482:     private fun institutionOf(evidence: VerificationEvidence): String? =
486:     private fun collapse(value: String): String =
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt`

SHA256 `1b67c1f6f2cbf347b28c6a968d5460cfc58a60af005de3d8582a920699e0f147`

```text
13: data class VerificationAdmissionContext(
22: data class VerificationAdmissionEvidence(
27:     val ruleVersion: Int = 0
33: class DiscoveryVerificationAdmissionGate(
37:     fun findContext(docId: String, profile: ExpertProfile): VerificationAdmissionContext =
40:     fun findContexts(keys: Map<String, ExpertProfile>): Map<String, VerificationAdmissionContext> {
46:     fun evaluateManaged(baseAutomatic: AutomaticAdmissionResult, context: VerificationAdmissionContext): AutomaticAdmissionResult {
49:         fun result(codes: List<String>): AutomaticAdmissionResult {
67:                 institutionNormalization = payload.institutionNormalization, ruleVersion = payload.ruleVersion).also {
86:         fun snapshot(docId: String, profile: ExpertProfile, config: AdmissionConfigSnapshot): VerificationSnapshot {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`

SHA256 `b55e2aadf324fef52c3b9be572ac1715304c090ef52993fde0869297bfa0fb40`

```text
22: class DiscoveryVerificationQueryService(
31:     fun capabilities(): VerificationCapabilities {
40:     fun summary(executionIds: List<Long>): List<VerificationSummary> {
51:     fun batches(executionId: Long, page: Int, size: Int): VerificationBatches {
78:     fun records(filter: VerificationReportFilter, page: Int, size: Int): VerificationRecords {
97:     fun item(itemId: Long): VerificationItemDetail {
109:                 it.pageNumber, it.attemptNumber, it.requestedAt, it.completedAt, it.httpStatus, it.resultCode, it.responseSha256) },
117:     private fun summaryRow(id: Long, run: VerificationRun?, counts: VerificationCounts, status: String?, asOf: Instant,
123:     private fun lifecycle(run: VerificationRun, counts: VerificationCounts) = when {
129:     private fun suggested(run: VerificationRun, values: Triple<Long, Long, Long>?): VerificationSuggestions? =
133:     private fun record(item: VerificationItem, decision: String?, revision: Long?,
141:                 institutionNormalization = payload.institutionNormalization, ruleVersion = payload.ruleVersion).missingFields
157:     private fun workerPayload(item: VerificationItem): VerificationWorkerEvidence? = try {
163:     private fun reasonCodes(item: VerificationItem): List<String> = item.reasonsJson?.let { mapper.readValue<List<String>>(it).distinct() } ?: emptyList()
164:     private fun reasons(filter: VerificationReportFilter): List<VerificationReason> =
167:     private fun aggregateReasons(rows: List<Pair<String, Long>>): List<VerificationReason> {
174:     private fun validatePage(page: Int, size: Int) { require(page >= 0 && size in 1..100) }
175:     private fun reasonLabel(code: String): String = REASON_LABELS[code] ?: code
195:             "SEARCH_PAGE_LIMIT" to "查询未完成：达到单次查询页数上限",
203: data class VerificationCapabilities(val enabled: Boolean, val workerEnabled: Boolean, val mode: VerificationMode,
206: data class VerificationSuggestions(val autoAdmitted: Long, val directAdmitted: Long, val apolloAdmitted: Long)
207: data class VerificationReason(val code: String, val label: String, val count: Long)
208: data class VerificationSummary(val executionId: Long, val coverage: String, val counts: VerificationCounts?,
211: data class VerificationBatch(val batchNumber: Int, val counts: VerificationCounts, val suggested: VerificationSuggestions?,
213: data class VerificationBatches(val coverage: String, val totalBatches: Long?, val items: List<VerificationBatch>,
216: data class VerificationRecords(val coverage: String, val total: Long?, val items: List<VerificationRecord>,
220: data class VerificationRecord(val itemId: Long, val docId: String, val name: String, val email: String?,
225: data class VerificationAttemptSummary(val id: Long, val provider: VerificationProvider, val queryKind: VerificationQueryKind,
227:     val httpStatus: Int?, val resultCode: String, val responseSha256: String?)
228: data class VerificationItemDetail(val record: VerificationRecord, val executionId: Long, val state: VerificationState,
236: data class VerificationApolloStages(val enteredAt: Instant?, val searchedAt: Instant?,
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`

SHA256 `329a6ff151afe7e44dbf1d6e42011d33bb75e8232f5ce6ea26daaf3fec447a67`

```text
20: data class DiscoveryVerificationExecution(val executionId: Long?, val run: VerificationRun? = null) {
27: data class DiscoveryRegistration(val item: VerificationItem? = null, val protected: Boolean = false,
29: class DiscoveryRegistrationException(cause: Exception) : IllegalStateException("VERIFICATION_REGISTRATION_FAILED", cause)
32: class DiscoveryVerificationRegistrationService(
46:     fun resolveOptions(criteria: PaperSearchCriteria): PaperSearchCriteria {
53:     fun begin(executionId: Long?, criteria: PaperSearchCriteria, historical: Boolean = false): DiscoveryVerificationExecution {
67:             "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION,
76:     fun closeInput(context: DiscoveryVerificationExecution) {
80:     fun reserve(context: DiscoveryVerificationExecution, docId: String, profile: ExpertProfile,
138:     private fun cancellationCause(error: Throwable): Throwable? {
152:     private fun resumeExisting(item: VerificationItem, rawProfile: ExpertProfile, sourceName: String,
162:     private fun freeze(item: VerificationItem, rawProfile: ExpertProfile, sourceName: String,
182:     private fun frozenProfile(item: VerificationItem): ExpertProfile {
188:     private fun factsWithoutCountry(profile: ExpertProfile) =
192:     private fun validateReceipt(snapshotJson: String, frozen: ExpertProfile) {
221:     fun activate(registration: DiscoveryRegistration) {
238:     fun recoverDuplicate(docId: String, sourceName: String, sourceItemKey: String) {
246:     private fun recover(item: VerificationItem): Boolean {
274:     override fun recoverReserved(limit: Int): Int = repository.listReserved(limit).count { recover(it) }
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt`

SHA256 `8d2dc66b86c26167a35ec2031d2525eaeba78e6ced43fe29dbc3209a39491b9d`

```text
17: data class VerificationCommitInput(val snapshot: VerificationSnapshot?, val admission: ExpertDiscoveryAdmission?, val protection: String?)
20: class DiscoveryVerificationCommitService(
31:     fun prepare(item: VerificationItem): VerificationCommitInput {
49:     fun commit(lease: VerificationItem, proposed: VerificationResult, input: VerificationCommitInput): VerificationItem? {
67:         fun withheld(code: String, protected: Boolean = false) = proposed.copy(
103:     fun project(item: VerificationItem) {
106:         fun current(): Boolean = admissions.findAdmission(item.expertDocId)?.let {
109:         fun mark(state: VerificationProjection, code: String? = null) = repository.markProjection(item.id, revision, state, code, clock.instant())
131:     private fun contacted(snapshot: VerificationSnapshot): Boolean {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt`

SHA256 `93cba1d5608582267326008a8701ee497199b8a1528ec4d3b69b08a977c7d101`

```text
30:     data class Before(val schemaVersion: Int = 1, val executionId: Long, val itemIds: List<Long>,
33:     data class Manifest(val schemaVersion: Int = 1, val executionId: Long, val sortedItemIds: List<Long>,
36:     data class Frozen(val directory: Path, val manifest: Manifest, val before: Before)
37:     data class EvaluationRow(val itemId: Long, val localOutcome: String, val outcome: String, val qualification: String,
42:     @JvmStatic fun main(args: Array<String>) {
52:     fun execute(args: Array<String>) {
64:         fun required(key: String) = requireNotNull(options[key])
81:     private fun classpaths(options: Map<String, String>) = mapOf("fixed" to (options["--fixed-classpath"] ?: System.getProperty("java.class.path"))) +
83:     private fun database(): Pair<JdbcTemplate, DataSourceTransactionManager> {
88:     fun validateIds(ids: List<Long>) { require(ids.size in 1..100 && ids.all { it > 0 } && ids.distinct().size == ids.size) }
89:     fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
90:     fun hashes(classpath: String): Map<String, String> = classNames.associateWith { name ->
101:         }.firstOrNull() ?: error("Required compiled class unavailable")
104:     fun freeze(jdbc: JdbcTemplate, manager: DataSourceTransactionManager, executionId: Long, ids: List<Long>,
132:     private fun <T> readTransaction(manager: DataSourceTransactionManager, block: () -> T): T =
135:     private fun noLinks(path: Path) {
139:     private fun secureDirectory(path: Path) {
145:     fun atomicWrite(path: Path, bytes: ByteArray) {
157:     private fun restrictedRead(path: Path): ByteArray {
163:     fun load(path: Path, expectedManifestHash: String): Frozen {
177:     fun expected(before: Before) = before.items.map { ReplayExpected(it, before.admissions[it.expertDocId],
179:     fun apply(frozen: Frozen, jdbc: JdbcTemplate, manager: DataSourceTransactionManager, clock: Clock): List<ReplayReceipt> {
188:     fun inspect(frozen: Frozen, jdbc: JdbcTemplate, manager: DataSourceTransactionManager, clock: Clock): Map<String, Any> {
213:     private fun report(frozen: Frozen, kind: String, value: Any) = atomicWrite(
215:     fun evaluate(frozen: Frozen, classpaths: Map<String, String>): Map<String, List<EvaluationRow>> {
237:     private fun fork(frozen: Frozen, classpath: String, group: String): List<EvaluationRow> {
248:         @JvmStatic fun main(args: Array<String>) {
253:         fun evaluateInput(input: JsonNode, instant: Instant, group: String): List<EvaluationRow> {
272:                 val version = payload.path("ruleVersion").asInt(0)
273:                 val local = rules.evaluate(snapshot, facts, null, false, ruleVersion = version)
277:                     local else rules.evaluate(snapshot, facts, search, false, ruleVersion = version)
278:                 val gate = rules.evaluate(snapshot, facts, search, false, ruleVersion = version)
286:         private fun normalizePublic(node: JsonNode) {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPromotionProgressService.kt`

SHA256 `5cab554e3e73a73de98beba2665e824d32b83a507c9a24d5224b5c1d6149ae69`

```text
25: class DiscoveryPromotionProgressService(
35:     fun refresh(progress: TaskProgress): TaskProgress {
58:     fun refresh(execution: TaskExecution): TaskExecution {
71:     internal fun counts(execution: TaskExecution): Map<String, Int> {
84:                     "_source" to listOf("discoveredAt", "tags")) }
95:                 if (source.path("tags").none { it.asText() == "discovered" }) continue
96:                 val discovered = parseDate(source.path("discoveredAt").asText()) ?: continue
106:     private fun merge(details: ObjectNode, counts: Map<String, Int>) {
124:     private fun parseDate(value: String): LocalDateTime? = runCatching {
128:     private fun <T> safely(original: T, action: () -> T): T = try {
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`

SHA256 `9bb4ec0b425dfe799105402d466ae62b62ee745be7800ccf776d21c88617a3f9`

```text
23: class DiscoveryVerificationRepository(private val jdbc: JdbcTemplate) {
24:     data class ReplayAdmission(val identityHash: String, val decision: String, val revision: Long, val policyVersion: String?)
25:     data class ReplayExpected(val item: VerificationItem, val admission: ReplayAdmission?, val evidenceJson: String,
29:     data class ReplayReceipt(val itemId: Long, val status: String)
32:     fun replayAdmission(docId: String, locked: Boolean = false): ReplayAdmission? =
39:     fun requeuePendingForEvidenceReplay(executionId: Long, expectedItems: List<ReplayExpected>, now: Instant): List<ReplayReceipt> {
46:         fun stale(expected: ReplayExpected): Boolean {
97:     fun reportingCollectionStatuses(ids: List<Long>): Map<Long, String> {
103:     fun reportingRuns(ids: List<Long>): Map<Long, VerificationRun> {
109:     fun reportingCounts(filter: VerificationReportFilter, batches: Boolean = false, executionIds: List<Long>? = null): Map<Long, VerificationCounts> {
113:         fun sum(condition: String, alias: String) = "COALESCE(SUM(CASE WHEN $condition THEN 1 ELSE 0 END),0) AS $alias"
153:     fun reportingSuggestions(filter: VerificationReportFilter, batches: Boolean = false,
171:     fun reportingReasons(filter: VerificationReportFilter): List<Pair<String, Long>> {
178:     fun reportingBatchTimes(executionId: Long): Map<Int, Instant> =
183:     fun reportingBatchReasons(executionId: Long): List<Triple<Int, String, Long>> =
189:     fun reportingRecordTotal(filter: VerificationReportFilter): Long {
195:     fun reportingRecords(filter: VerificationReportFilter, page: Int, size: Int): List<VerificationItem> {
203:     private fun reportingWhere(filter: VerificationReportFilter): Pair<String, List<Any>> {
233:     fun createRun(executionId: Long, mode: VerificationMode, apolloRequested: Boolean, settingsJson: String, now: Instant): VerificationRun {
235:         jdbc.update("INSERT IGNORE INTO discovery_verification_run (execution_id,mode,policy_version,apollo_requested,settings_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
243:     fun reserveItem(input: VerificationReservation, now: Instant): VerificationItem {
262:     fun activateItem(itemId: Long, batchNumber: Int, now: Instant): VerificationItem {
274:     fun closeInput(executionId: Long, now: Instant): Boolean {
281:     fun findRun(executionId: Long): VerificationRun? {
285:     fun findItem(itemId: Long): VerificationItem? {
289:     fun findManagedContexts(docIds: Collection<String>): Map<String, VerificationContext> {
308:     fun findItemsByDocIds(docIds: Collection<String>): List<VerificationItem> {
319:     fun claimDue(now: Instant, leaseSeconds: Long): VerificationItem? {
341:     fun renewLease(itemId: Long, token: String, generation: Long, now: Instant, leaseSeconds: Long): Boolean {
352:     fun reserveAttemptWithBudget(itemId: Long, token: String, generation: Long, request: VerificationAttemptRequest,
364:                         override fun afterCompletion(status: Int) {
388:     fun completeAttempt(itemId: Long, token: String, generation: Long, attemptId: Long, httpStatus: Int?, resultCode: String,
389:                         responseJson: String?, responseSha256: String?, now: Instant): Boolean {
392:         responseSha256?.let { verificationHash(it) }
396:             WHERE id=? AND item_id=? AND completed_at IS NULL""", now.verificationUtc(), httpStatus, resultCode, responseJson, responseSha256, attemptId, itemId)
402:     fun saveEvidenceAndOutcome(itemId: Long, token: String, generation: Long, result: VerificationResult, now: Instant): Boolean {
418:     fun saveFailure(itemId: Long, token: String, generation: Long, result: VerificationResult,
431:     fun markProjection(itemId: Long, admissionRevision: Long, projection: VerificationProjection, error: String?, now: Instant): Boolean {
444:     fun reschedule(itemId: Long, token: String, generation: Long, nextAttemptAt: Instant, transientAttempts: Int,
457:     fun listReserved(limit: Int): List<VerificationItem> {
464:     fun failReserved(itemId: Long, reasonsJson: String, now: Instant): Boolean {
475:     fun lockForCommit(itemId: Long): VerificationItem {
484:     fun saveProgress(itemId: Long, token: String, generation: Long, evidenceJson: String,
499:     fun completeAttemptAndProgress(itemId: Long, token: String, generation: Long, attemptId: Long,
500:         httpStatus: Int?, resultCode: String, responseJson: String?, responseSha256: String?,
502:         if (!completeAttempt(itemId, token, generation, attemptId, httpStatus, resultCode, responseJson, responseSha256, now)) return false
508:     fun attachAdmission(itemId: Long, revision: Long, now: Instant) {
517:     fun listProjectionDue(limit: Int): List<VerificationItem> {
524:     fun findAttempts(itemId: Long): List<VerificationAttempt> = jdbc.query(
531:     private fun identity(input: VerificationReservation) = jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id=? AND identity_hash=? AND policy_version=?",
533:     private fun checkedIdentity(item: VerificationItem, input: VerificationReservation): VerificationItem {
537:     private fun lockRun(id: Long): VerificationRun {
542:     private fun lockItem(id: Long): VerificationItem {
547:     private fun lockedItem(id: Long) = jdbc.query("SELECT * FROM discovery_verification_item WHERE id=? FOR UPDATE", itemMapper, id).single()
548:     private fun owns(item: VerificationItem, token: String, generation: Long, now: Instant): Boolean =
550:     private fun bump(owner: Long, now: Instant) {
556:         fun replayHash(value: String?): String = MessageDigest.getInstance("SHA-256")
559:         fun replayEvidence(value: String?): String {
569:         private fun ResultSet.time(name: String): Instant? = getTimestamp(name)?.toLocalDateTime()?.verificationInstant()
570:         private fun ResultSet.nullLong(name: String): Long? = getLong(name).let { if (wasNull()) null else it }
572:             rs.getString("policy_version"), rs.getBoolean("apollo_requested"), rs.getString("settings_json"), rs.getBoolean("input_closed"), rs.getLong("revision"), rs.time("created_at")!!, rs.time("updated_at")!!) }
584: data class VerificationReportFilter(
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepository.kt`

SHA256 `d9f05f1676b40c694cd425d2b4d6974053e40cfdf3e32d5d93ddbe45afe9c0e8`

```text
32:     fun findDiscoveryDocuments(executionId: Long): List<DiscoveryJobDocument>
35:     fun findByExpertDocId(docId: String): ExpertAcademicEnrichmentJob?
50:     fun insertIfAbsent(
77:     fun reopenUnmatchedOrStaleSuccess(
93:     fun findDueCandidates(limit: Int, now: LocalDateTime): List<ExpertAcademicEnrichmentJob>
112:     fun claimById(
131:     fun renewLeaseById(
159:     fun completeWithToken(
186:     fun reopenFailedById(id: Long, now: LocalDateTime): Int
189: data class DiscoveryJobDocument(val expertDocId: String, val source: String)
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt`

SHA256 `821b2ad6a530aa8801bc2128c908f5a25cdcb8e1cd7ca08d229ac7e9a0e46f49`

```text
7: enum class VerificationMode { AUDIT, ENFORCE;
10: enum class VerificationState { RESERVED, QUEUED, RUNNING, RETRY_WAIT, COMPLETED, ERROR }
11: enum class VerificationOutcome { DIRECT_CLEAR, APOLLO_CLEAR, APOLLO_UNCLEAR, PENDING, PROTECTED }
12: enum class VerificationQualification { NOT_EVALUATED, MET, UNKNOWN, NOT_MET, CONFLICT }
13: enum class VerificationRoute { NONE, DIRECT, APOLLO }
14: enum class VerificationProjection { NOT_REQUIRED, PENDING, PROJECTED, ALREADY_PRESENT, APPLICATION_PRESENT, ERROR }
15: enum class VerificationContext { UNMANAGED, MANAGED_AUDIT, MANAGED_ENFORCE, UNAVAILABLE }
16: enum class VerificationProvider { APOLLO, ORCID }
17: enum class VerificationQueryKind { LOCAL_EVIDENCE, BROAD, VARIANT, NARROW, ORCID, DETAIL }
18: enum class AssertionSource { ORGANIZATION, SELF, UNKNOWN }
19: enum class TemporalScope { CURRENT, HISTORICAL, UNKNOWN }
21: fun verificationText(value: String, max: Int): String {
25: fun verificationHash(value: String): String {
29: fun Instant.verificationUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
30: fun LocalDateTime.verificationInstant(): Instant = toInstant(ZoneOffset.UTC)
32: interface RegistrationRecovery { fun recoverReserved(limit: Int): Int }
34: data class VerificationRun(
42: data class VerificationReservation(
56: data class VerificationItem(
85: data class VerificationEvidence(
87:     val sourceUrl: String, val fetchedAt: Instant, val responseSha256: String,
95:         verificationHash(responseSha256); verificationText(provider, 16); verificationText(sourceUrl, 2048)
102: data class VerificationProgress(
110: data class VerificationResult(
141: data class VerificationAttemptRequest(
147: data class VerificationAttempt(
151:     val requestJson: String, val responseJson: String?, val responseSha256: String?
153: enum class VerificationBudgetReason { LEASE_LOST, LOCK_BUSY, MINUTE_LIMIT, DAY_LIMIT, ITEM_LIMIT, DUPLICATE_REQUEST }
154: data class VerificationAttemptReservation(val attemptId: Long? = null, val deferred: VerificationBudgetReason? = null)
155: data class VerificationCounts(
```
## `src/main/kotlin/com/weibo/talentintroduction/config/DiscoverySampleProperties.kt`

SHA256 `25ff5df75bd0e5f2bbe4bf8d411ebabdd5f6018f6eec06b4bdf455948d72cb78`

```text
9: data class DiscoverySampleProperties(
12:     var retentionDays: Int = 7,
19:     var maxSamplesPerBucketPerRun: Int = 20,
20:     var maxSuccessControlsPerRun: Int = 10,
23:     fun valid(): Boolean = try {
24:         Paths.get(directory).isAbsolute && retentionDays > 0 && maxTotalSamples > 0 &&
27:             maxSamplesPerBucketPerRun > 0 && maxSuccessControlsPerRun > 0 &&
35: class DiscoverySampleConfiguration
```
## `src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStore.kt`

SHA256 `6c52af7f1e5ec70098d594304ec0b3598cc62547a7cfd2bcad44bb3e66d63127`

```text
20: class DiscoverySampleStore(val properties: DiscoverySampleProperties, private val clock: Clock = Clock.systemUTC()) {
28:     private data class Package(val path: Path, val manifest: DiscoverySampleManifest, val bytes: Long)
29:     private class InvalidStore : Exception()
32:     fun decision(value: DiscoverySampleDecision): DiscoverySampleDecision {
43:     fun active(): Boolean {
48:     private fun privateDirectory(path: Path) {
51:     private fun prepareRoot() {
61:     private fun write(path: Path, bytes: ByteArray) {
65:     private fun remove(path: Path, depth: Int = 0, budget: IntArray = intArrayOf(0)) {
72:     private fun reclaimEmptyExecution(path: Path) {
80:     private fun scan(): MutableList<Package> {
117:     private fun locked(action: () -> DiscoverySampleDecision): DiscoverySampleDecision {
136:     private fun retained(): MutableList<Package> {
138:         val cutoff = clock.instant().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
144:     fun startup() { cleanup() }
146:     fun cleanup() { if (properties.enabled) locked { retained(); DiscoverySampleDecision.SAVED } }
148:     fun commit(manifest: DiscoverySampleManifest, bodies: Map<String, ByteArray>): DiscoverySampleDecision = locked {
178:         val limit = if (manifest.bucket == DiscoverySampleBucket.SUCCESS_CONTROL) properties.maxSuccessControlsPerRun else properties.maxSamplesPerBucketPerRun
```
## `src/main/resources/db/migration/V158__create_discovery_verification.sql`

SHA256 `855a85c27da96e252c95e2e9e58d9d566fe7eb6c0452d1bed18977f7563592c5`

```text
1: CREATE TABLE discovery_verification_run (
6:   settings_json TEXT NOT NULL,
11:   PRIMARY KEY (execution_id)
14: CREATE TABLE discovery_verification_item (
23:   snapshot_json MEDIUMTEXT NOT NULL,
34:   evidence_json MEDIUMTEXT NULL,
49:   PRIMARY KEY (id),
50:   UNIQUE KEY uk_dvi_identity (expert_doc_id, identity_hash, policy_version),
51:   KEY idx_dvi_run_batch (owner_execution_id, batch_number, id),
52:   KEY idx_dvi_claim (state, next_attempt_at, lease_until, id),
53:   KEY idx_dvi_projection (projection_state, updated_at, id)
56: CREATE TABLE discovery_verification_attempt (
69:   response_json MEDIUMTEXT NULL,
71:   PRIMARY KEY (id),
72:   UNIQUE KEY uk_dva_request (request_key),
73:   KEY idx_dva_item (item_id, id),
74:   KEY idx_dva_budget (provider, requested_at)
```
## `src/main/resources/db/migration/V131__create_expert_academic_enrichment_job.sql`

SHA256 `df6939a7baa0869bc03b89c7c06b3bd4c3f8328563b21e7ece5cbe3f14f481de`

```text
29: CREATE TABLE expert_academic_enrichment_job (
43:     PRIMARY KEY (id),
44:     UNIQUE KEY uk_expert_academic_enrichment_job_doc (expert_doc_id),
45:     KEY idx_expert_academic_enrichment_job_due (status, next_attempt_at, id),
```
## `src/main/resources/es/orcid_info_candidate.json`

SHA256 `922847bf07f1bd89f1c8cfd32cae6bebe9f1972a34f99c274ac40f35bef14ddf`

```text
7:     "dynamic": false,
34:       "identityVerification": { "type": "object", "dynamic": false, "properties": {
43:       "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
47:       "tags": { "type": "keyword" },
```
## `src/main/resources/es/orcid_info_application.json`

SHA256 `1dd76c89a3a652cdfc09cd97338da28a9342c15fee4369f2b6f5bdb61d312020`

```text
7:     "dynamic": false,
45:       "identityVerification": { "type": "object", "dynamic": false, "properties": {
54:       "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
57:       "tags": { "type": "keyword" },
```

## r2执行性复核补充

### `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`

SHA256 `702bf1c4a6d29abc15627b3be8fe967767b967f5522cdbc6086095cd473c0841`

```text
903:         val now = LocalDateTime.now().format(dateFormatter)
904:         val candidateDoc = source.toMutableMap().apply {
905:             put("candidateValidatedAt", now)
906:             put("updatedAt", now)
907:             val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty()
908:             put("tags", (existingTags + DISCOVERY_TAG).distinct())
```

### `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt`

SHA256 `8fa41536efb00f4f408227c4a2deb954bc2c0934e0592e5594308b54545fc051`

```text
1: package com.weibo.talentintroduction.discovery.service
2: 
3: import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
4: import com.weibo.talentintroduction.discovery.domain.VerificationProvider
5: import org.junit.jupiter.api.Assertions.*
6: import org.junit.jupiter.api.Test
7: import org.springframework.http.HttpMethod
8: import org.springframework.http.HttpStatus
9: import org.springframework.http.MediaType
10: import org.springframework.test.web.client.MockRestServiceServer
11: import org.springframework.test.web.client.match.MockRestRequestMatchers.*
12: import org.springframework.test.web.client.response.MockRestResponseCreators.*
13: import java.time.Clock
14: import java.time.Instant
15: import java.time.ZoneOffset
16: 
17: class ApolloPeopleSearchClientTest {
18:     private val clock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC)
19:     private val properties = DiscoveryVerificationProperties(apolloApiKey = "secret")
20:     private val http = verificationRestTemplate(properties)
21:     private val server = MockRestServiceServer.bindTo(http).build()
22:     private val client = ApolloPeopleSearchClient(properties, http, clock)
23:     private fun permit() = RequestPermit(1, VerificationProvider.APOLLO)
```

### `pom.xml`

SHA256 `88097630a54cc5f3a207cb4982006be3799477c07a8218fa08df489b822a2642`

```text
175:                 <groupId>org.apache.maven.plugins</groupId>
176:                 <artifactId>maven-surefire-plugin</artifactId>
177:                 <configuration>
178:                     <systemPropertyVariables>
179:                         <migrationIt>${migrationIt}</migrationIt>
180:                         <mysqlIt>${mysqlIt}</mysqlIt>
181:                     </systemPropertyVariables>
182:                     <environmentVariables>
183:                         <DYLD_LIBRARY_PATH>/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib</DYLD_LIBRARY_PATH>
184:                     </environmentVariables>
185:                 </configuration>
```

