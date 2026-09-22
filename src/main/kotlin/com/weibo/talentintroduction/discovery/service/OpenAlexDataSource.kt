package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexMeteredDestinations
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OpenAlexRequestPolicy
import com.weibo.talentintroduction.config.Operation
import com.weibo.talentintroduction.config.Permit
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.discovery.domain.resolvedFulltextObtained
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.Instant

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.openalex", name = ["enabled"], havingValue = "true")
class OpenAlexDataSource(
    @Qualifier("openAlexRestTemplate") private val restTemplate: RestTemplate,
    private val properties: OpenAlexProperties,
    private val europePmc: EuropePmcDataSource,
    private val pdfEmailExtractor: PdfEmailExtractor,
    private val unpaywallClient: UnpaywallClient,
    private val requestPolicy: OpenAlexRequestPolicy = OpenAlexRequestPolicy(properties)
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(OpenAlexDataSource::class.java)

    override val sourceName = "OPENALEX"
    override val emailExtractionMethod = "FULLTEXT_XML"
    override val maxPapersPerSource: Int
        get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val filter = buildFilter(criteria)
        var url = "${properties.baseUrl}/works?filter=$filter&per_page=${criteria.pageSize}"
        if (criteria.cursor != null) url += "&cursor=${criteria.cursor}"
        else url += "&cursor=*"
        if (properties.politeEmail.isNotBlank()) url += "&mailto=${properties.politeEmail}"

        // I-1：关键词/语义搜索 10 credits，纯列表 1 credit —— 由构造方显式声明，再由 policy 校验目标路径。
        val operation = if (criteria.keywords.isNotEmpty()) Operation.SEARCH else Operation.LIST
        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            getJson(RequestKind.DISCOVERY, operation, url)
        } catch (e: Exception) {
            log.error("OpenAlex search failed: {}", e.message)
            throw e
        }
        return parseResponse(response)
    }

    /**
     * Every OpenAlex HTTP call passes through the shared [OpenAlexRequestPolicy]: the caller names its [RequestKind]
     * (purpose) and [Operation] (cost) explicitly, the request slot and its estimated cost are reserved before the
     * call, and the real quota headers reconcile the budget right after. A deferred budget raises
     * [OpenAlexBudgetDeferredException] instead of calling; a timeout marks the permit UNKNOWN (never refunded).
     * Every retry goes through this method again, so a retry reserves a second time (I-1/I-3).
     */
    private fun getJson(kind: RequestKind, operation: Operation, url: String): JsonNode? {
        val permit = requestPolicy.reserve(kind, operation, url)
        if (permit is Permit.Deferred) throw OpenAlexBudgetDeferredException(permit.reason, permit.retryAt)
        val permitId = (permit as Permit.Allowed).permitId
        val response = try {
            restTemplate.exchange(url, HttpMethod.GET, null, JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            requestPolicy.recordResponse(permitId, e.responseHeaders ?: HttpHeaders())
            throw e
        } catch (e: Exception) {
            requestPolicy.recordTimeout(permitId)
            throw e
        }
        requestPolicy.recordResponse(permitId, response?.headers ?: HttpHeaders())
        return response?.body
    }

    /**
     * c10（I-1）：单篇论文的全文回退链。顺序固定为 PMC XML → 首选 OA PDF → 其他去重 OA PDF →
     * DOI→Unpaywall 开放位置；整篇共享一个 [FULLTEXT_PER_PAPER_DEADLINE_MS] 总时限，URL 侧最多
     * [MAX_FULLTEXT_ADDRESSES] 个地址（正文里说的「全文地址」就是 URL，PMC XML 按 ID 取，不算地址），
     * 同一 URL 只尝试一次，Unpaywall 只在前面都没取到内容时才问一次。
     * 任一地址取到内容（有邮箱或无邮箱）即停手：不再为同一篇发第二轮无效请求。
     */
    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome =
        extractAuthorEmails(paper, Instant.now().plusMillis(FULLTEXT_PER_PAPER_DEADLINE_MS))

    /** 直接传 deadline 的入口：测试用它验证「共享总时限」而不是每个地址各给一份。 */
    internal fun extractAuthorEmails(paper: PaperMetadata, deadline: Instant): EmailExtractionOutcome {
        var requests = 0
        var lastFailure: EmailExtractionOutcome? = null

        if (paper.pmcId != null) {
            // R-4（V-4）：XML 阶段也在同一个共享总时限内 —— 时限已过就整段按 TIMEOUT 收口
            // （0 次请求），不再接着走 URL / Unpaywall。
            val europePmcOutcome = europePmc.extractAuthorEmails(paper, deadline)
            // I-2: PMC/JATS 是另一来源的提取结果，姓名/邮箱相似都不算证据 ——
            // 只有 ORCID 精确等值到一个唯一作者时才允许补接该作者的 OpenAlex ID。
            val xmlOutcome = europePmcOutcome.copy(
                emails = europePmcOutcome.emails.map { it.copy(openAlexAuthorId = verifiedAuthorIdByOrcid(it, paper.authors)) },
                methodUsed = "FULLTEXT_XML"
            )
            requests += xmlOutcome.httpRequests
            if (xmlOutcome.resolvedFulltextObtained()) return xmlOutcome.copy(httpRequests = requests)
            // XML 没取到内容才继续回退；取到内容但没有邮箱按 I-3 算成功，不再重复下载。
            lastFailure = xmlOutcome
        }

        val attemptedUrls = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        (listOfNotNull(paper.downloadUrl) + paper.candidateDownloadUrls)
            .mapNotNullTo(queue) { publicCandidateUrl(it) }
        var unpaywallConsulted = false

        while (attemptedUrls.size < MAX_FULLTEXT_ADDRESSES) {
            if (deadlineExpired(deadline)) {
                // I-1：总时限是单篇自己的约束，不是来源耗尽 —— 没有拿到内容时单列 TIMEOUT。
                if (lastFailure == null) lastFailure = timeoutOutcome()
                break
            }
            if (queue.isEmpty()) {
                if (unpaywallConsulted) break
                unpaywallConsulted = true
                val doi = paper.doi
                if (doi != null && unpaywallClient.isConfigured()) {
                    // R-4（V-4）：Unpaywall 查询也是这一篇的全文阶段，同样受共享总时限约束；
                    // 时限已过就不再发这次查询，并按已有的 TIMEOUT 类别收口。
                    if (deadlineExpired(deadline)) {
                        if (lastFailure == null) lastFailure = timeoutOutcome()
                        break
                    }
                    requests++
                    unpaywallClient.findPdfUrls(doi, deadline).mapNotNullTo(queue) { publicCandidateUrl(it) }
                    // R-1（V-4）：查询被共享预算截断（客户端按剩余时间中止）且没拿到地址 → 按 TIMEOUT 收口，
                    // 不再对同一篇发起后续下载。
                    if (queue.isEmpty() && deadlineExpired(deadline)) {
                        if (lastFailure == null) lastFailure = timeoutOutcome()
                        break
                    }
                }
                if (queue.isEmpty()) break
            }
            val url = queue.removeFirst()
            if (!attemptedUrls.add(url)) continue
            // I-1 防御：计量主机绝不被下载（候选构建已过滤，这里再挡一次，未来调用方也不会误入）。
            if (OpenAlexMeteredDestinations.isMetered(url)) continue
            val outcome = pdfEmailExtractor.extract(url, paper.authors, sourceName, deadline) { }
            requests += outcome.httpRequests
            if (outcome.resolvedFulltextObtained()) return outcome.copy(httpRequests = requests)
            lastFailure = outcome
        }

        lastFailure?.let { return it.copy(httpRequests = requests) }
        // 一个地址都没有：保持改动前的语义（下游按 papersSkippedNoId 计数）。
        return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID")
    }

    private fun timeoutOutcome(): EmailExtractionOutcome = EmailExtractionOutcome(
        emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED", httpRequests = 0,
        fulltextObtained = false, downloadFailureCategory = FULLTEXT_FAILURE_TIMEOUT
    )

    /**
     * I-1：公开全文候选 = 公开 http(s) 地址，且**不是** OpenAlex 计量主机。
     *
     * OpenAlex 的 Content API（`content.openalex.org`，100 credits/次）与 API 本体都不作为全文候选：
     * 本阶段拒绝新增计量下载，遇到这类地址就跳过、改试已有公开链接（出版社/PMC/仓库）。计量请求一律经
     * [OpenAlexRequestPolicy.reserve] 的显式操作预占，下载回调不是预占的替代品。
     */
    private fun publicCandidateUrl(raw: String?): String? =
        publicFulltextUrl(raw)?.takeIf { !OpenAlexMeteredDestinations.isMetered(it) }

    private fun deadlineExpired(deadline: Instant): Boolean = !Instant.now().isBefore(deadline)

    /**
     * I-2: 只有「提取出的邮箱带 ORCID，且该 ORCID 精确等值到唯一一个带作者 ID 的作者」才返回该 ID。
     * ORCID 缺失、不匹配，或同一 ORCID 对应多个不同作者 ID 时一律返回 null（不猜）。
     */
    private fun verifiedAuthorIdByOrcid(email: AuthorEmail, authors: List<PaperAuthor>): String? {
        val orcid = normalizeOrcid(email.orcidId) ?: return null
        return authors.mapNotNull { author ->
            if (normalizeOrcid(author.orcidId) != orcid) return@mapNotNull null
            normalizeOpenAlexAuthorId(author.openAlexAuthorId)
        }.distinct().singleOrNull()
    }

    private fun normalizeOrcid(raw: String?): String? {
        val bare = raw?.trim()?.removePrefix("https://orcid.org/")?.removePrefix("http://orcid.org/")?.trim()
        return bare?.takeIf { it.isNotEmpty() }?.lowercase()
    }

    private fun buildFilter(criteria: PaperSearchCriteria): String {
        val parts = mutableListOf<String>()
        if (criteria.openAccessOnly) parts += "is_oa:true"
        parts += "publication_year:${criteria.publicationYearFrom}-${criteria.publicationYearTo}"
        if (criteria.excludeCountries.isNotEmpty()) {
            for (country in criteria.excludeCountries) parts += "authorships.institutions.country_code:!$country"
        }
        if (criteria.keywords.isNotEmpty()) {
            parts += "title_and_abstract.search:${criteria.keywords.joinToString("|")}"
        }
        // I4-2: subjectScope == null 时返回空列表，parts 内容与改动前逐字相同（含参数顺序）。
        parts += SubjectScopeCatalog.openAlexFilterParts(criteria.subjectScope)
        return parts.joinToString(",")
    }

    private fun parseResponse(response: JsonNode?): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)
        val nextCursor = response.path("meta").path("next_cursor").asText(null)
        val totalResults = response.path("meta").path("count").asLong(0)
        val papers = response.path("results").mapNotNull { node ->
            try {
                val doi = node.path("doi").asText(null)?.removePrefix("https://doi.org/")
                val pmcId = node.path("ids").path("pmcid")?.asText(null)
                    ?.removePrefix("https://www.ncbi.nlm.nih.gov/pmc/articles/")
                val pmid = node.path("ids").path("pmid")?.asText(null)
                    ?.removePrefix("https://pubmed.ncbi.nlm.nih.gov/")
                val pdfUrl = node.path("best_oa_location").path("pdf_url").asText(null)
                // c10（I-1）：locations 里的开放 PDF 是首选失效时的备用地址；只取 is_oa 的公开 http(s) 地址，
                // 去掉首选本身与重复项 —— 付费墙与非公开协议不成其为候选。
                val primaryPdfUrl = publicCandidateUrl(pdfUrl)
                val otherOaPdfUrls = node.path("locations")
                    .filter { it.path("is_oa").asBoolean(false) }
                    .mapNotNull { publicCandidateUrl(it.path("pdf_url").asText(null)) }
                    .filter { it != primaryPdfUrl }
                    .distinct()
                val authors = node.path("authorships").map { authorship ->
                    val author = authorship.path("author")
                    val orcid = author.path("orcid").asText(null)?.removePrefix("https://orcid.org/")
                    val nameParts = author.path("display_name").asText("").split(" ", limit = 2)
                    val institution = authorship.path("institutions").firstOrNull()
                    // I5a-2: 与 affiliation 取自同一个（第一个）机构对象；I5a-3: 无 type/空串均产出 null。
                    val institutionType = institution?.path("type")?.asText(null)?.takeIf { it.isNotBlank() }
                    PaperAuthor(
                        givenNames = nameParts.getOrNull(0), familyNames = nameParts.getOrNull(1),
                        orcidId = orcid, affiliation = institution?.path("display_name")?.asText(null),
                        isCorresponding = authorship.path("is_corresponding").asBoolean(false),
                        institutionType = institutionType,
                        openAlexAuthorId = normalizeOpenAlexAuthorId(author.path("id").asText(null))
                    )
                }
                PaperMetadata(pmcId = pmcId, pmid = pmid, doi = doi,
                    title = node.path("title").asText(""), pubYear = node.path("publication_year").asInt(0),
                    journal = node.path("primary_location").path("source").path("display_name").asText(null),
                    authors = authors, source = sourceName, downloadUrl = pdfUrl,
                    candidateDownloadUrls = otherOaPdfUrls)
            } catch (e: Exception) { log.debug("Failed to parse OpenAlex: {}", e.message); null }
        }
        return PaperSearchResult(papers, nextCursor, totalResults)
    }

    /** Legacy entry point: the existing backfill callers are history enrichment (lowest priority). */
    fun enrichAuthor(openAlexAuthorId: String): AuthorEnrichment? =
        enrichAuthor(openAlexAuthorId, RequestKind.HISTORY_ENRICHMENT)

    fun enrichAuthor(openAlexAuthorId: String, kind: RequestKind): AuthorEnrichment? =
        (enrichAuthorOutcome(openAlexAuthorId, kind) as? EnrichmentOutcome.Success)?.data

    /**
     * I-3：单人作者补全的完整结果 —— 保留「基础事实有效、开关控制的最近论文/专利标题子请求失败」
     * 的细分（[EnrichmentOutcome.Success.titlesFailed]），附加数据可单独重试，不改变基础事实的可用性。
     * 404/网络错误与限流分开：前者 [EnrichmentOutcome.NotFound]/[EnrichmentOutcome.ApiError]，限流原样抛出。
     */
    private fun enrichAuthorOutcome(openAlexAuthorId: String, kind: RequestKind): EnrichmentOutcome {
        val url = "${properties.baseUrl}/authors/$openAlexAuthorId" +
            if (properties.politeEmail.isNotBlank()) "?mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = getJson(kind, Operation.SINGLETON, url) ?: return EnrichmentOutcome.NotFound
            enrichmentOutcome(response, kind)
        } catch (e: OpenAlexBudgetDeferredException) {
            throw e
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) throw e
            log.debug("OpenAlex author enrichment failed for {}: {} (HTTP {})", openAlexAuthorId, e.message, code)
            // V-3：404 是「查无此人」，其余 HTTP 失败可重试 —— 两者绝不混为一谈。
            if (code == 404) EnrichmentOutcome.NotFound else EnrichmentOutcome.ApiError("HTTP $code")
        } catch (e: Exception) {
            log.debug("OpenAlex author enrichment failed for {}: {}", openAlexAuthorId, e.message)
            EnrichmentOutcome.ApiError(e.message ?: "unknown error")
        }
    }

    /**
     * I-3：可单独重试的附加标题子请求（最近论文 / 专利共用一套开关与失败语义）。
     * - [titles] 为 null 且 [failed] = true：这次请求失败，可单独重试，绝不是「该作者没有作品」；
     * - [titles] 为空列表：请求成功但确实没有作品，不算失败；
     * - `NOT_REQUESTED`：按开关未请求（不计入失败）。
     * 限流（429/503）原样抛出，由批量调用方统一降级。
     */
    private fun fetchTitles(url: String, kind: RequestKind): TitlesFetch {
        val fullUrl = url + if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = getJson(kind, Operation.LIST, fullUrl) ?: return TitlesFetch.failed()
            TitlesFetch.ok(
                response.path("results")
                    .mapNotNull { it.path("title").asText(null)?.takeIf { title -> title.isNotBlank() } }
            )
        } catch (e: OpenAlexBudgetDeferredException) {
            throw e
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) throw e
            log.debug("OpenAlex titles fetch failed for {}: {} (HTTP {})", fullUrl, e.message, code)
            TitlesFetch.failed()
        } catch (e: Exception) {
            log.debug("OpenAlex titles fetch failed for {}: {}", fullUrl, e.message)
            TitlesFetch.failed()
        }
    }

    /** 最近论文标题：按发表年降序取前 [RECENT_TITLES_LIMIT] 条。 */
    private fun recentWorksUrl(worksUrl: String): String =
        "$worksUrl?sort=publication_year:desc&per_page=$RECENT_TITLES_LIMIT&select=title,publication_year"

    /** 专利标题：同样只取标题；专利开关默认关闭，本轮不接入专利数据。 */
    private fun patentWorksUrl(worksUrl: String): String =
        "$worksUrl?filter=type:patent&per_page=$RECENT_TITLES_LIMIT&select=title,publication_year"

    /** Legacy entry point: history enrichment (lowest priority). */
    fun enrichAuthorByOrcid(orcid: String): AuthorEnrichment? =
        enrichAuthorByOrcid(orcid, RequestKind.HISTORY_ENRICHMENT)

    fun enrichAuthorByOrcid(orcid: String, kind: RequestKind): AuthorEnrichment? {
        return when (val outcome = enrichAuthorByOrcidWithReason(orcid, kind)) {
            is EnrichmentOutcome.Success -> outcome.data
            else -> null
        }
    }

    /** Legacy entry point: history enrichment (lowest priority). */
    fun enrichAuthorByOrcidWithReason(orcid: String): EnrichmentOutcome =
        enrichAuthorByOrcidWithReason(orcid, RequestKind.HISTORY_ENRICHMENT)

    fun enrichAuthorByOrcidWithReason(orcid: String, kind: RequestKind): EnrichmentOutcome {
        val searchUrl = "${properties.baseUrl}/authors?filter=orcid:$orcid" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val searchResponse = getJson(kind, Operation.LIST, searchUrl)
            val authorId = searchResponse?.path("results")?.get(0)?.path("id")?.asText(null)
                ?.removePrefix("https://openalex.org/")
            if (authorId == null) {
                return EnrichmentOutcome.NotFound
            }
            // V-3：作者详情请求的 404（查无此人）与可重试失败必须继续分开传递。
            enrichAuthorOutcome(authorId, kind)
        } catch (e: OpenAlexBudgetDeferredException) {
            throw e
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) {
                val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
                return EnrichmentOutcome.RateLimited(retryAfter)
            }
            log.debug("OpenAlex ORCID lookup failed for {}: {} (HTTP {})", orcid, e.message, code)
            EnrichmentOutcome.ApiError("HTTP $code: ${e.message}")
        } catch (e: Exception) {
            log.debug("OpenAlex ORCID lookup failed for {}: {}", orcid, e.message)
            EnrichmentOutcome.ApiError(e.message ?: "unknown error")
        }
    }

    /** Legacy entry point: history enrichment (lowest priority). */
    fun batchEnrichByOrcids(orcids: List<String>): Map<String, EnrichmentOutcome> =
        batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT)

    fun batchEnrichByOrcids(orcids: List<String>, kind: RequestKind): Map<String, EnrichmentOutcome> {
        if (orcids.isEmpty()) return emptyMap()
        val url = "${properties.baseUrl}/authors?filter=orcid:${orcids.joinToString("|")}" +
            "&per_page=${orcids.size}" + mailtoSuffix()
        return batchEnrichIdentities(orcids, url, kind) { node ->
            node.path("orcid").asText(null)?.removePrefix("https://orcid.org/")
        }
    }

    /** Legacy entry point: history enrichment (lowest priority). */
    fun batchEnrichByAuthorIds(authorIds: List<String>): Map<String, EnrichmentOutcome> =
        batchEnrichByAuthorIds(authorIds, RequestKind.HISTORY_ENRICHMENT)

    /**
     * I-1：按可信 OpenAlex 作者 ID 批量查询（`filter=openalex:A…|A…`），用于覆盖「有作者 ID 但没有 ORCID」的人。
     * 调用方保证每批 ≤100 个不同身份；结果对每个传入身份逐一给出（响应中缺失 = [EnrichmentOutcome.NotFound]）。
     * 响应里的 `id` 必须经 [normalizeOpenAlexAuthorId] 才是可信作者身份，其他形状绝不参与匹配。
     */
    fun batchEnrichByAuthorIds(authorIds: List<String>, kind: RequestKind): Map<String, EnrichmentOutcome> {
        if (authorIds.isEmpty()) return emptyMap()
        val url = "${properties.baseUrl}/authors?filter=openalex:${authorIds.joinToString("|")}" +
            "&per_page=${authorIds.size}" + mailtoSuffix()
        return batchEnrichIdentities(authorIds, url, kind) { node ->
            normalizeOpenAlexAuthorId(node.path("id").asText(null))
        }
    }

    private fun mailtoSuffix(): String =
        if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""

    /**
     * 两种身份共用的批量查询：一次请求拿回多个作者，用 [identityOf] 把响应归因回传入身份。
     * 结果覆盖全部入参（缺失 = NotFound，绝不猜测归属）；限流（429/503）标
     * [EnrichmentOutcome.RateLimited]，其余 HTTP/网络失败标 [EnrichmentOutcome.ApiError]。
     */
    private fun batchEnrichIdentities(
        identities: List<String>,
        url: String,
        kind: RequestKind,
        identityOf: (JsonNode) -> String?
    ): Map<String, EnrichmentOutcome> {
        val response = try {
            getJson(kind, Operation.LIST, url)
        } catch (e: OpenAlexBudgetDeferredException) {
            throw e
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) {
                val retryAfterHeader = e.responseHeaders?.getFirst("Retry-After")
                log.warn(
                    "OpenAlex batch rate limited: status={}, Retry-After={}, body={}",
                    code, retryAfterHeader, e.responseBodyAsString.take(500)
                )
                return identities.associateWith {
                    EnrichmentOutcome.RateLimited(retryAfterHeader?.toLongOrNull()?.times(1000))
                }
            }
            return identities.associateWith { EnrichmentOutcome.ApiError("HTTP $code") }
        } catch (e: Exception) {
            return identities.associateWith { EnrichmentOutcome.ApiError(e.message ?: "unknown") }
        }

        val foundEntries = mutableListOf<Pair<String, JsonNode>>()
        response?.path("results")?.forEach { node ->
            val identity = identityOf(node) ?: return@forEach
            foundEntries += identity to node
        }

        val results = mutableMapOf<String, EnrichmentOutcome>()
        for (identity in identities) {
            if (foundEntries.none { it.first == identity }) {
                results[identity] = EnrichmentOutcome.NotFound
            }
        }

        for ((index, entry) in foundEntries.withIndex()) {
            val (identity, node) = entry
            try {
                results[identity] = enrichmentOutcome(node, kind)
            } catch (e: OpenAlexBudgetDeferredException) {
                throw e
            } catch (e: HttpStatusCodeException) {
                // I-3：限流只影响开关控制的附加标题 —— 基础事实照常返回，其余身份标限流、不再打接口。
                results[identity] = EnrichmentOutcome.Success(parseAuthorBase(node), titlesFailed = true)
                val code = e.statusCode.value()
                if (code != 429 && code != 503) continue
                val rateLimited = EnrichmentOutcome.RateLimited(
                    e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
                )
                for (remaining in foundEntries.drop(index + 1)) {
                    if (remaining.first !in results) results[remaining.first] = rateLimited
                }
                break
            }
        }

        return identities.associateWith { results[it] ?: EnrichmentOutcome.NotFound }
    }

    /**
     * I-3：基础学术事实 —— hIndex / 引用数 / 论文数 / 研究方向 / 学科 / 最近发表年份。
     * 全部来自作者节点本身，与论文/专利开关无关，缺失一律为 null（null 不覆盖存量值）。
     */
    private fun parseAuthorBase(node: JsonNode): AuthorEnrichment {
        val topicsNode = node.path("topics").takeIf { it.isArray }
        val topics = topicsNode
            ?.sortedByDescending { it.path("count").asInt(0) }
            ?.take(5)
            ?.mapNotNull { it.path("display_name").asText(null) }
        val disciplineCategory = resolveDisciplineCategory(topicsNode)
        // I5a-2/I5a-7: 取 last_known_institutions 第一项的 type（与 works 路径的署名机构不同源）；
        // I5a-3: 数组为空、无 type 键、type 为空串均产出 null。
        val institutionType = node.path("last_known_institutions").firstOrNull()
            ?.path("type")?.asText(null)?.takeIf { it.isNotBlank() }
        // I1-1/I1-2：取 works_count > 0 的最大 year；数组顺序不可依赖（CP-1 实测为升序）。
        // I1-3：无该键、空数组、或全部 works_count = 0 时为 null。
        val lastPublicationYear = node.path("counts_by_year")
            .filter { it.path("works_count").asInt(0) > 0 }
            .mapNotNull { it.path("year").let { y -> if (y.isInt) y.asInt() else null } }
            .maxOrNull()
        return AuthorEnrichment(
            hIndex = node.path("summary_stats").path("h_index").let { if (it.isInt) it.asInt() else null },
            citationCount = node.path("cited_by_count").let { if (it.isInt) it.asInt() else null },
            worksCount = node.path("works_count").let { if (it.isInt) it.asInt() else null },
            topics = topics,
            disciplineCategory = disciplineCategory,
            institutionType = institutionType,
            lastPublicationYear = lastPublicationYear
        )
    }

    /**
     * I-3：基础事实 + 开关控制的最近论文/专利标题。单人与批量共用同一套开关与失败语义：
     * 关闭的开关不发请求（[TitlesFetch.NOT_REQUESTED]），打开的开关失败只标 [ParsedAuthorEnrichment.titlesFailed]，
     * 绝不因此丢掉基础事实。
     */
    private fun parseAuthorEnrichment(node: JsonNode, kind: RequestKind): ParsedAuthorEnrichment {
        val base = parseAuthorBase(node)
        val worksUrl = node.path("works_api_url").asText(null)
        val recentWorks = if (worksUrl != null && properties.fetchWorksEnabled) {
            fetchTitles(recentWorksUrl(worksUrl), kind)
        } else {
            TitlesFetch.NOT_REQUESTED
        }
        val patents = if (worksUrl != null && properties.fetchPatentsEnabled) {
            fetchTitles(patentWorksUrl(worksUrl), kind)
        } else {
            TitlesFetch.NOT_REQUESTED
        }
        return ParsedAuthorEnrichment(
            data = base.copy(
                recentWorkTitles = recentWorks.titles?.takeIf { it.isNotEmpty() },
                patentTitles = patents.titles?.takeIf { it.isNotEmpty() }
            ),
            titlesFailed = recentWorks.failed || patents.failed
        )
    }

    /** 基础事实 + 附加标题的成败标志（I-3：标题可单独重试，空结果不算失败）。 */
    private data class ParsedAuthorEnrichment(val data: AuthorEnrichment, val titlesFailed: Boolean)

    /** 作者节点 → 结果；限流（429/503）与额度延期照原样抛出，由调用方决定降级方式。 */
    private fun enrichmentOutcome(node: JsonNode, kind: RequestKind): EnrichmentOutcome {
        val parsed = parseAuthorEnrichment(node, kind)
        return EnrichmentOutcome.Success(parsed.data, parsed.titlesFailed)
    }

    private fun resolveDisciplineCategory(topicsNode: JsonNode?): String? {
        if (topicsNode == null) return null
        var stem = 0
        var humanities = 0
        for (topic in topicsNode) {
            when (topic.path("domain").path("display_name").asText(null)) {
                "Physical Sciences", "Life Sciences", "Health Sciences" -> stem += topic.path("count").asInt(0)
                "Social Sciences" -> humanities += topic.path("count").asInt(0)
            }
        }
        if (stem == 0 && humanities == 0) return null
        return if (stem >= humanities) "STEM" else "HUMANITIES"
    }
}

/**
 * I-1: OpenAlex 作者 ID 的规范形式是 `A` + 数字（API 返回 `https://openalex.org/A123…`）。
 * 其他形状的值不是可信作者身份：既不写 externalIds，也不参与任何主键/文档定位。
 */
internal fun normalizeOpenAlexAuthorId(raw: String?): String? {
    val bare = raw?.trim()?.removePrefix("https://openalex.org/")?.removePrefix("http://openalex.org/")?.trim()
    return bare?.takeIf { OPENALEX_AUTHOR_ID_PATTERN.matches(it) }
}

private val OPENALEX_AUTHOR_ID_PATTERN = Regex("A\\d+")

sealed class EnrichmentOutcome {
    /**
     * 基础事实可用。[titlesFailed] = true 表示开关控制的最近论文/专利标题子请求失败（I-3：可单独重试），
     * 基础事实不受影响；空结果不算失败。
     */
    data class Success(val data: AuthorEnrichment, val titlesFailed: Boolean = false) : EnrichmentOutcome()
    object NotFound : EnrichmentOutcome()
    data class ApiError(val message: String) : EnrichmentOutcome()
    data class RateLimited(val retryAfterMs: Long? = null) : EnrichmentOutcome()
}

/**
 * I-3：附加标题子请求的结果 —— 把「请求失败（可重试）」与「请求成功但没有作品」分开：
 * `titles = null, failed = true` 是失败；`titles = emptyList(), failed = false` 是真实的空结果；
 * `NOT_REQUESTED` 表示按开关未请求。
 */
private data class TitlesFetch(val titles: List<String>?, val failed: Boolean) {
    companion object {
        val NOT_REQUESTED = TitlesFetch(null, false)
        fun ok(titles: List<String>) = TitlesFetch(titles, false)
        fun failed() = TitlesFetch(null, true)
    }
}

/** I-3：最近论文/专利标题的取条数上界。 */
private const val RECENT_TITLES_LIMIT = 3

/**
 * c10（I-1）：单篇论文全部全文地址共享的总时限（与尝试上限是两个独立约束，超时按 TIMEOUT 上报，
 * 不冒充来源耗尽）。I-4：不随本次改动放大，仍按方案给定的 90 秒。
 */
internal const val FULLTEXT_PER_PAPER_DEADLINE_MS = 90_000L

/** c10（I-1）：单篇论文最多尝试的全文**地址（URL）**数 —— 第三个失败后不再访问第四个。 */
internal const val MAX_FULLTEXT_ADDRESSES = 3

data class AuthorEnrichment(
    val hIndex: Int?,
    val citationCount: Int?,
    val worksCount: Int?,
    val topics: List<String>? = null,
    val recentWorkTitles: List<String>? = null,
    val patentTitles: List<String>? = null,
    val disciplineCategory: String? = null,
    val institutionType: String? = null,
    val lastPublicationYear: Int? = null
)
