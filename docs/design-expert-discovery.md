# 专家发现与过滤系统设计方案

## 1. 目标

从公开学术资源自动发现符合规则的专家，**以获取有效邮箱为第一优先级**。写入 ES RAW 索引（L3），经过增强过滤规则链验证后晋升到 CANDIDATE（L2），进入现有外联流程。同时支持对存量专家的规则回溯验证。

---

## 2. 核心问题：邮箱从哪来

### 现实情况

| 数据源 | 邮箱可用性 | 说明 |
|--------|-----------|------|
| Europe PMC search API | ~2-5% | `authorEmail` 字段极少填充 |
| CrossRef API | 0% | 出于隐私保护不返回邮箱 |
| PubMed/NCBI E-utilities | ~1% | 标准 XML 无邮箱字段 |
| OpenAlex API | 0% | 不暴露邮箱 |
| Semantic Scholar API | 0% | 无邮箱字段 |
| ORCID API | ~10-15% | 需作者主动公开 |

**结论：Profile 级别 API 几乎无法获取邮箱。**

### 可行方案：论文全文提取

开放获取论文的全文 XML（JATS 格式）中包含通讯作者邮箱：

```xml
<!-- Europe PMC / PubMed Central 全文 XML 示例 -->
<article>
  <front>
    <article-meta>
      <contrib-group>
        <contrib contrib-type="author" corresp="yes">
          <name><surname>Smith</surname><given-names>John</given-names></name>
          <email>john.smith@cambridge.ac.uk</email>
          <xref ref-type="aff" rid="aff1"/>
        </contrib>
      </contrib-group>
      <author-notes>
        <corresp id="cor1">*Corresponding author: <email>john.smith@cambridge.ac.uk</email></corresp>
      </author-notes>
    </article-meta>
  </front>
</article>
```

**这是公开发表的论文内容，邮箱是作者自行公开的，合规性最好。**

---

## 3. 邮箱获取策略（优先级排序）

```
策略 1: 论文全文 XML 提取（主力）
   Europe PMC Full-Text API → JATS XML → 解析 <email> 标签
   覆盖: 约 800 万篇开放获取全文
   邮箱命中率: ~60-70%（开放获取论文中通讯作者通常有邮箱）

策略 2: ORCID 公开邮箱（补充）
   现有对接，邮箱公开率 ~10-15%
   用于策略 1 命中的作者交叉验证

策略 3: 机构邮箱推断（兜底）
   从论文 affiliation 提取机构域名
   按常见格式推断: firstname.lastname@institution.edu
   必须经 MX + SMTP 验证才能使用
```

---

## 4. 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      触发方式                                     │
│        定时任务（cron）          管理后台手动触发                     │
└────────────┬──────────────────────────┬─────────────────────────┘
             │                          │
             ▼                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  ExpertDiscoveryService                          │
│                                                                  │
│  Step 1: 搜论文                                                   │
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────────┐    │
│  │ Europe PMC     │  │ OpenAlex       │  │ CrossRef        │    │
│  │ Search API     │  │ /works         │  │ /works          │    │
│  │ (发现论文)     │  │ (发现论文)      │  │ (补充 DOI)      │    │
│  └───────┬────────┘  └───────┬────────┘  └────────┬────────┘    │
│          └───────────────────┼────────────────────┘             │
│                              ▼                                   │
│  Step 2: 提取邮箱                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Europe PMC Full-Text XML API                             │    │
│  │ GET /articles/{PMCID}/fullTextXML                        │    │
│  │ 解析 <email> + <corresp> + <contrib corresp="yes">      │    │
│  └───────────────────────────┬─────────────────────────────┘    │
│                              ▼                                   │
│  Step 3: 构建专家 Profile                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 论文元数据 + 作者信息 + 提取的邮箱                          │    │
│  │ → RawExpertData                                          │    │
│  └───────────────────────────┬─────────────────────────────┘    │
│                              ▼                                   │
│  Step 4: ORCID 交叉关联（可选）                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 用作者名+机构匹配 ORCID → 补充 orcidId                    │    │
│  └───────────────────────────┬─────────────────────────────┘    │
└──────────────────────────────┼──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      过滤规则链                                    │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │邮箱格式  │→│一次性邮箱│→│MX 记录   │→│邮箱去重  │           │
│  │校验      │ │黑名单    │ │校验      │ │检查      │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │国籍过滤  │→│学位过滤  │→│年龄过滤  │→│活跃度    │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│                                                                  │
│  每条拒绝记录原因 → filterRejectReason                             │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                        通过 ──►│◄── 拒绝
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  ES RAW (L3)  ──CandidateEligibility──►  CANDIDATE (L2)        │
│                                              │                   │
│                                      现有外联流程                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 数据源对接详细设计

### 5.1 Europe PMC（主力 — 论文搜索 + 全文邮箱提取）

#### 搜索接口 — 发现论文

```
GET https://www.ebi.ac.uk/europepmc/webservices/rest/search
    ?query=OPEN_ACCESS:y AND PUB_YEAR:[2020 TO 2026] AND AFF:"university"
    &resultType=core
    &pageSize=100
    &cursorMark=*
    &format=json
```

返回字段（用于构建专家 profile）：
- `pmcid` — PMC 全文 ID（用于拉全文）
- `doi` — 论文 DOI
- `title` — 论文标题
- `authorList.author[]` — 作者列表（name, affiliation, authorId.type=ORCID）
- `pubYear` — 发表年份
- `journalTitle` — 期刊

可用查询条件：
- `OPEN_ACCESS:y` — 限定开放获取（才能拉全文）
- `PUB_YEAR:[2020 TO 2026]` — 近N年
- `AFF:"keyword"` — 机构关键词
- `AUTH:"name"` — 作者名
- `KW:"keyword"` 或 `TITLE:"keyword"` — 研究领域

速率限制：无需 API key，~10 req/s

#### 全文接口 — 提取邮箱

```
GET https://www.ebi.ac.uk/europepmc/webservices/rest/{PMCID}/fullTextXML
```

返回 JATS XML 全文。邮箱位置：

```kotlin
// 解析优先级
1. <contrib contrib-type="author" corresp="yes"> 下的 <email>   // 通讯作者直接标注
2. <author-notes> → <corresp> 下的 <email>                     // 通讯信息区
3. <contrib contrib-type="author"> 下的 <email>                  // 普通作者（少见但有）
```

#### 实现

```kotlin
@Service
class EuropePmcDataSource(
    private val restTemplate: RestTemplate,
    private val properties: EuropePmcProperties
) : AcademicDataSource {

    override val sourceName = "EUROPE_PMC"

    // Step 1: 搜索论文
    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val query = buildQuery(criteria)
        val url = "${properties.baseUrl}/search?query=$query&resultType=core&pageSize=${criteria.pageSize}&cursorMark=${criteria.cursor ?: "*"}&format=json"
        val response = restTemplate.getForObject(url, JsonNode::class.java)
        return parsePaperSearchResult(response)
    }

    // Step 2: 拉全文 XML 提取邮箱
    fun extractEmailsFromFullText(pmcId: String): List<AuthorEmail> {
        val url = "${properties.baseUrl}/$pmcId/fullTextXML"
        val xml = restTemplate.getForObject(url, String::class.java) ?: return emptyList()
        return JatsXmlEmailParser.parse(xml)
    }
}
```

### 5.2 OpenAlex（辅助 — 学术指标补充）

不用于邮箱获取。用于：
- 按研究领域/机构批量发现论文 DOI → 到 Europe PMC 拉全文
- 补充作者 h-index、引用数、论文数

```
GET https://api.openalex.org/works
    ?filter=is_oa:true,publication_year:2020-2026,authorships.institutions.country_code:!CN
    &per_page=200
    &cursor=*
    &mailto=your@email.com
```

```kotlin
@Service
class OpenAlexDataSource(
    private val restTemplate: RestTemplate,
    private val properties: OpenAlexProperties
) : AcademicDataSource {

    override val sourceName = "OPENALEX"

    // 搜论文，返回 DOI 列表
    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult { ... }

    // 补充作者学术指标
    fun enrichAuthor(openAlexAuthorId: String): AuthorEnrichment {
        val url = "https://api.openalex.org/authors/$openAlexAuthorId"
        val response = restTemplate.getForObject(url, JsonNode::class.java)
        return AuthorEnrichment(
            hIndex = response?.path("summary_stats")?.path("h_index")?.asInt(),
            citationCount = response?.path("cited_by_count")?.asInt(),
            worksCount = response?.path("works_count")?.asInt()
        )
    }
}
```

### 5.3 ORCID API（现有 — 邮箱交叉验证）

现有对接。扩展用途：
- 用论文中解析到的 ORCID ID 反查公开邮箱，做交叉验证
- 如论文邮箱验证失败，尝试 ORCID 公开邮箱作为备选

### 5.4 统一接口

```kotlin
interface AcademicDataSource {
    val sourceName: String
    fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult
}

data class PaperSearchCriteria(
    val keywords: List<String> = emptyList(),
    val affiliationKeywords: List<String> = emptyList(),
    val excludeCountries: List<String> = listOf("CN"),
    val publicationYearFrom: Int = 2020,
    val publicationYearTo: Int = 2026,
    val openAccessOnly: Boolean = true,
    val pageSize: Int = 100,
    val cursor: String? = null
)

data class PaperSearchResult(
    val papers: List<PaperMetadata>,
    val nextCursor: String?,
    val totalResults: Long
)

data class PaperMetadata(
    val pmcId: String?,
    val doi: String?,
    val title: String,
    val pubYear: Int,
    val journal: String?,
    val authors: List<PaperAuthor>,
    val source: String
)

data class PaperAuthor(
    val givenNames: String?,
    val familyNames: String?,
    val orcidId: String?,
    val affiliation: String?,
    val isCorresponding: Boolean,
    val email: String?
)
```

---

## 6. JATS XML 邮箱解析器

论文全文邮箱提取的核心组件：

```kotlin
object JatsXmlEmailParser {

    data class AuthorEmail(
        val email: String,
        val givenNames: String?,
        val familyNames: String?,
        val isCorresponding: Boolean,
        val affiliation: String?,
        val orcidId: String?
    )

    fun parse(xml: String): List<AuthorEmail> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())

        val results = mutableListOf<AuthorEmail>()

        // 策略 1: <contrib corresp="yes"> 下的 <email>
        results += parseContribEmails(doc, correspondingOnly = true)

        // 策略 2: <author-notes>/<corresp> 下的 <email>
        results += parseCorrespNotes(doc)

        // 策略 3: 所有 <contrib> 下的 <email>（去重后）
        results += parseContribEmails(doc, correspondingOnly = false)

        return results.distinctBy { it.email.lowercase() }
    }
}
```

---

## 7. 邮箱验证链

### 7.1 分层验证

| 层级 | 校验方式 | 耗时 | 执行阶段 | 阻断性 |
|------|---------|------|---------|--------|
| L1 | 正则格式校验（现有） | <1ms | 入库前同步 | 是 |
| L2 | 一次性邮箱域名黑名单 | <1ms | 入库前同步 | 是 |
| L3 | MX 记录校验（DNS） | 50-500ms | 入库后批量 | 是 |
| L4 | SMTP RCPT TO（可选） | 1-5s | 晋升前可选 | 否 |
| L5 | 退信监控 | 被动 | 外联后 | 标记 |

### 7.2 实现

```kotlin
@Service
class EmailValidationService(
    private val properties: EmailValidationProperties,
    private val emailValidationCacheRepository: EmailValidationCacheRepository
) {
    private val disposableDomains: Set<String> = loadDisposableDomains()

    fun validate(email: String): EmailValidationResult {
        // 检查缓存
        val cached = emailValidationCacheRepository.findByEmail(email.lowercase())
        if (cached != null && !cached.isExpired()) {
            return cached.toResult()
        }

        // L1: 格式校验
        if (!isValidFormat(email)) {
            return reject(email, 0, "INVALID_FORMAT")
        }

        // L2: 一次性邮箱
        val domain = email.substringAfter("@").lowercase()
        if (domain in disposableDomains) {
            return reject(email, 1, "DISPOSABLE_EMAIL")
        }

        // L3: MX 记录
        if (properties.enableMxCheck) {
            if (!hasMxRecord(domain)) {
                return reject(email, 2, "NO_MX_RECORD")
            }
        }

        val result = EmailValidationResult(level = 3, valid = true)
        cacheResult(email, result)
        return result
    }

    fun hasMxRecord(domain: String): Boolean {
        return try {
            val env = InitialDirContext(Hashtable<String, String>().apply {
                put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
            })
            val attrs = env.getAttributes(domain, arrayOf("MX"))
            val mx = attrs.get("MX")
            mx != null && mx.size() > 0
        } catch (e: NamingException) {
            false
        }
    }

    private fun isValidFormat(email: String): Boolean =
        EMAIL_REGEX.matches(email)

    private fun loadDisposableDomains(): Set<String> {
        // 从 classpath:email/disposable-domains.txt 加载
        // 约 3000+ 域名
    }
}

data class EmailValidationResult(
    val level: Int,
    val valid: Boolean,
    val rejectReason: String? = null
)
```

### 7.3 一次性邮箱黑名单

`src/main/resources/email/disposable-domains.txt`，常见域名：
```
guerrillamail.com
tempmail.org
throwaway.email
mailinator.com
yopmail.com
sharklasers.com
guerrillamailblock.com
...（约 3000+ 域名）
```

---

## 8. ExpertProfile 扩展

### 8.1 字段新增

```kotlin
data class ExpertProfile(
    // 现有字段（不变）
    val orcidId: String,
    val email: String?,
    val givenNames: String?,
    val familyNames: String?,
    val country: String?,
    val keyword: String?,
    val employment: String?,
    val age: Int? = null,
    val degree: String? = null,
    val nationality: String? = null,

    // 新增
    val hIndex: Int? = null,
    val citationCount: Int? = null,
    val lastPublicationYear: Int? = null,
    val researchFields: String? = null,
    val institution: String? = null,
    val emailSource: String? = null,       // PAPER_FULLTEXT / ORCID / INFERRED
    val emailVerifiedLevel: Int? = null,   // 1-5
    val dataSource: String? = null,        // EUROPE_PMC / OPENALEX / ORCID
    val externalIds: String? = null        // JSON: {"pmcId":"PMC123","doi":"10.1234/..."}
)
```

### 8.2 ES mapping 新增字段

RAW / CANDIDATE / APPLICATION 索引追加（PUT mapping API，不影响现有数据）：

```json
{
  "properties": {
    "hIndex": { "type": "integer" },
    "citationCount": { "type": "integer" },
    "lastPublicationYear": { "type": "integer" },
    "researchFields": { "type": "keyword" },
    "institution": { "type": "text" },
    "emailSource": { "type": "keyword" },
    "emailVerifiedLevel": { "type": "integer" },
    "dataSource": { "type": "keyword" },
    "externalIds": { "type": "object", "enabled": false },
    "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
    "filterResult": { "type": "keyword" },
    "filterRejectReason": { "type": "keyword" }
  }
}
```

---

## 9. 增强过滤规则

### 9.1 CandidateEligibilityService 增强

```kotlin
@Service
class CandidateEligibilityService(
    private val filterProps: CandidateFilterProperties,
    private val academicProps: AcademicFilterProperties,
    private val emailValidationService: EmailValidationService
) {
    // 向后兼容：现有调用方不受影响
    fun isEligibleForCandidateIndex(expert: ExpertProfile): Boolean =
        evaluateEligibility(expert).eligible

    // 新方法：返回详细结果
    fun evaluateEligibility(expert: ExpertProfile): EligibilityResult {
        val reasons = mutableListOf<String>()

        // --- 现有规则（保持不变）---
        if (expert.orcidId.isBlank())
            reasons += "MISSING_ORCID"
        if (filterProps.requireValidEmail && !emailValidationService.isValidFormat(expert.email.orEmpty()))
            reasons += "INVALID_EMAIL_FORMAT"
        if (filterProps.requireDoctoralDegree && !hasDoctoralDegree(expert.degree))
            reasons += "NO_DOCTORAL_DEGREE"
        if (filterProps.enableAgeFilter && !isUnderMaxAge(expert.age))
            reasons += "AGE_EXCEEDED"
        if (filterProps.excludeChineseNationality && !isNotChineseNationality(expert.nationality ?: expert.country))
            reasons += "CHINESE_NATIONALITY"

        // --- 新增规则 ---
        if (filterProps.requireValidEmail && emailValidationService.isDisposableEmail(expert.email.orEmpty()))
            reasons += "DISPOSABLE_EMAIL"
        if (academicProps.enableHIndexFilter && (expert.hIndex ?: 0) < academicProps.minHIndex)
            reasons += "H_INDEX_TOO_LOW"
        if (academicProps.enableCitationFilter && (expert.citationCount ?: 0) < academicProps.minCitationCount)
            reasons += "CITATION_COUNT_TOO_LOW"
        if (academicProps.enableActivityFilter) {
            val cutoff = java.time.Year.now().value - academicProps.recentYearsThreshold
            if ((expert.lastPublicationYear ?: 0) < cutoff)
                reasons += "INACTIVE"
        }

        return EligibilityResult(reasons.isEmpty(), reasons)
    }
}

data class EligibilityResult(
    val eligible: Boolean,
    val rejectReasons: List<String> = emptyList()
)
```

### 9.2 配置

```yaml
talent-introduction:
  candidate-filter:
    # 现有配置保持不变
    require-valid-email: true
    exclude-chinese-nationality: true
    require-doctoral-degree: false
    enable-age-filter: false
    max-age-exclusive: 70
  academic-filter:
    enable-h-index-filter: ${ACADEMIC_ENABLE_H_INDEX_FILTER:false}
    min-h-index: ${ACADEMIC_MIN_H_INDEX:5}
    enable-citation-filter: ${ACADEMIC_ENABLE_CITATION_FILTER:false}
    min-citation-count: ${ACADEMIC_MIN_CITATION_COUNT:50}
    enable-activity-filter: ${ACADEMIC_ENABLE_ACTIVITY_FILTER:false}
    recent-years-threshold: ${ACADEMIC_RECENT_YEARS_THRESHOLD:5}
```

---

## 10. 专家发现编排服务

```kotlin
@Service
class ExpertDiscoveryService(
    private val europePmc: EuropePmcDataSource,
    private val openAlex: OpenAlexDataSource?,
    private val emailValidationService: EmailValidationService,
    private val eligibilityService: CandidateEligibilityService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val discoveryProperties: ExpertDiscoveryProperties
) {
    fun discover(criteria: PaperSearchCriteria, triggeredBy: String): DiscoveryResult {
        val stats = DiscoveryStats()

        // Step 1: 搜索论文
        var cursor: String? = null
        do {
            val batch = europePmc.searchPapers(criteria.copy(cursor = cursor))

            for (paper in batch.papers) {
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) break

                stats.totalPapers++
                val pmcId = paper.pmcId ?: continue

                // Step 2: 拉全文提取邮箱
                val authorEmails = europePmc.extractEmailsFromFullText(pmcId)
                if (authorEmails.isEmpty()) {
                    stats.noEmailPapers++
                    continue
                }

                // Step 3: 对每个有邮箱的作者构建 profile
                for (authorEmail in authorEmails) {
                    stats.totalAuthors++

                    // 邮箱验证（L1 + L2 + L3）
                    val emailResult = emailValidationService.validate(authorEmail.email)
                    if (!emailResult.valid) {
                        stats.emailRejected++
                        continue
                    }

                    // 去重检查（ES RAW 索引中已存在）
                    if (existsInRawIndex(authorEmail.email)) {
                        stats.duplicates++
                        continue
                    }

                    // 构建 ExpertProfile
                    val profile = buildProfile(paper, authorEmail, emailResult)

                    // 写入 RAW 索引
                    writeToRawIndex(profile)
                    stats.indexed++

                    // 候选人资格校验 → 晋升
                    val eligibility = eligibilityService.evaluateEligibility(profile)
                    if (eligibility.eligible) {
                        promoteToCandidate(profile)
                        stats.promoted++
                    } else {
                        stats.filtered++
                        recordFilterReason(profile, eligibility.rejectReasons)
                    }
                }
            }
            cursor = batch.nextCursor
        } while (cursor != null && stats.totalPapers < discoveryProperties.maxPapersPerRun)

        return DiscoveryResult(triggeredBy, stats)
    }
}
```

---

## 11. 存量专家验证

```kotlin
@Service
class ExpertRevalidationService(
    private val expertSearchService: ExpertSearchService,
    private val eligibilityService: CandidateEligibilityService,
    private val emailValidationService: EmailValidationService,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    /**
     * 对 CANDIDATE 索引存量专家按新规则重新验证
     * 不合格的降级回 RAW（数据不丢失）
     */
    fun revalidateCandidates(): RevalidationResult {
        val stats = RevalidationStats()
        // scroll 遍历 CANDIDATE 索引
        // 每条记录：邮箱 MX 校验 + 全规则检查
        // 不合格 → demoteToRaw + 记录原因
        // 合格 → 更新 emailVerifiedLevel
        return RevalidationResult(stats)
    }

    /**
     * 对 RAW 索引中未晋升的专家按新规则重新筛选
     * 符合条件的晋升到 CANDIDATE
     */
    fun promoteEligibleRawExperts(): PromotionResult {
        // scroll 遍历 RAW 索引
        // 仅处理未在 CANDIDATE 中的记录
        // 规则通过 → promoteToCandidate
        return PromotionResult(...)
    }
}
```

---

## 12. 调度与触发

### 12.1 定时调度（复用现有模式）

```kotlin
@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery", name = ["enabled"], havingValue = "true")
class ExpertDiscoveryScheduler(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val discoveryProperties: ExpertDiscoveryProperties
) {
    @Scheduled(cron = "\${talent-introduction.expert-discovery.cron:-}")
    fun scheduleDiscovery() {
        val criteria = PaperSearchCriteria(
            excludeCountries = listOf("CN"),
            openAccessOnly = true
        )
        taskExecutionService.runAndRecord("EXPERT_DISCOVERY", "SCHEDULED", criteria) {
            discoveryService.discover(criteria, "SCHEDULED")
        }
    }
}
```

### 12.2 手动触发（REST API）

```kotlin
@RestController
@RequestMapping("/api/expert-discovery")
class ExpertDiscoveryController(
    private val discoveryService: ExpertDiscoveryService,
    private val revalidationService: ExpertRevalidationService,
    private val taskExecutionService: TaskExecutionService
) {
    // 手动触发发现
    @PostMapping("/run")
    fun triggerDiscovery(@RequestBody criteria: PaperSearchCriteria): ResponseEntity<DiscoveryResult> {
        val result = taskExecutionService.runAndRecord("EXPERT_DISCOVERY", "MANUAL", criteria) {
            discoveryService.discover(criteria, "MANUAL")
        }
        return ResponseEntity.ok(result.resultAs<DiscoveryResult>())
    }

    // 存量验证
    @PostMapping("/revalidate")
    fun revalidate(): ResponseEntity<RevalidationResult> {
        val result = taskExecutionService.runAndRecord("EXPERT_REVALIDATION", "MANUAL", "all") {
            revalidationService.revalidateCandidates()
        }
        return ResponseEntity.ok(result.resultAs<RevalidationResult>())
    }

    // 查看最近发现任务
    @GetMapping("/executions")
    fun listExecutions(): ResponseEntity<List<TaskExecution>> { ... }
}
```

### 12.3 配置

```yaml
talent-introduction:
  expert-discovery:
    enabled: ${EXPERT_DISCOVERY_ENABLED:false}
    cron: ${EXPERT_DISCOVERY_CRON:0 0 2 * * ?}       # 每天凌晨2点
    max-papers-per-run: ${EXPERT_DISCOVERY_MAX_PAPERS:500}
    max-authors-per-run: ${EXPERT_DISCOVERY_MAX_AUTHORS:2000}
    europe-pmc:
      base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
      request-delay-ms: 100                            # 请求间隔，避免限速
    openalex:
      enabled: ${OPENALEX_ENABLED:false}
      polite-email: ${OPENALEX_POLITE_EMAIL:}
  email-validation:
    enable-mx-check: ${EMAIL_ENABLE_MX_CHECK:true}
    enable-smtp-verify: ${EMAIL_ENABLE_SMTP_VERIFY:false}
    cache-ttl-days: ${EMAIL_CACHE_TTL_DAYS:30}
    disposable-domain-list: classpath:email/disposable-domains.txt
```

---

## 13. 新增模块结构

```
com.weibo.talentintroduction/
├── discovery/                               # 新模块
│   ├── controller/
│   │   └── ExpertDiscoveryController.kt
│   ├── service/
│   │   ├── ExpertDiscoveryService.kt            # 发现编排
│   │   ├── ExpertRevalidationService.kt         # 存量验证
│   │   ├── ExpertDiscoveryScheduler.kt          # 定时调度
│   │   ├── JatsXmlEmailParser.kt                # 全文 XML 邮箱解析
│   │   └── source/
│   │       ├── AcademicDataSource.kt            # 统一接口
│   │       ├── EuropePmcDataSource.kt           # Europe PMC 搜索+全文
│   │       └── OpenAlexDataSource.kt            # OpenAlex 学术指标
│   └── domain/
│       ├── PaperSearchCriteria.kt
│       ├── PaperMetadata.kt
│       ├── DiscoveryResult.kt
│       └── RevalidationResult.kt
├── expert/
│   └── service/
│       ├── CandidateEligibilityService.kt       # 增强（返回拒绝原因）
│       └── EmailValidationService.kt            # 新增
├── config/
│   ├── CandidateFilterProperties.kt             # 不变
│   ├── AcademicFilterProperties.kt              # 新增
│   ├── ExpertDiscoveryProperties.kt             # 新增
│   └── EmailValidationProperties.kt             # 新增
└── resources/
    └── email/
        └── disposable-domains.txt               # 黑名单
```

---

## 14. DB 迁移

`V11__expert_discovery.sql`：

```sql
CREATE TABLE email_validation_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    format_valid BOOLEAN DEFAULT FALSE,
    disposable BOOLEAN DEFAULT FALSE,
    mx_valid BOOLEAN DEFAULT NULL,
    verified_level INT DEFAULT 0,
    reject_reason VARCHAR(128),
    verified_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    UNIQUE INDEX uk_email (email),
    INDEX idx_domain (domain),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

发现任务执行记录复用现有 `task_execution` 表（`taskType = 'EXPERT_DISCOVERY'`），无需新表。

---

## 15. 完整数据流示例

```
1. 定时任务触发，搜索条件：keywords=["machine learning"], excludeCountries=["CN"], year>=2022

2. Europe PMC search API → 返回 500 篇开放获取论文

3. 遍历每篇论文：
   Paper: "Deep Learning for Climate Prediction" (PMC9876543)
     │
     ├─ GET /PMC9876543/fullTextXML
     │
     ├─ JatsXmlEmailParser.parse(xml) →
     │   AuthorEmail(
     │     email = "j.smith@oxford.ac.uk",
     │     givenNames = "John",
     │     familyNames = "Smith",
     │     isCorresponding = true,
     │     affiliation = "University of Oxford, Department of Computer Science",
     │     orcidId = "0000-0001-2345-6789"
     │   )
     │
     ├─ EmailValidationService.validate("j.smith@oxford.ac.uk")
     │   L1 格式: ✅
     │   L2 黑名单: ✅ (oxford.ac.uk 不在黑名单)
     │   L3 MX: ✅ (oxford.ac.uk 有 MX 记录)
     │   → EmailValidationResult(level=3, valid=true)
     │
     ├─ 去重检查: ES RAW 索引无此邮箱 ✅
     │
     ├─ 构建 ExpertProfile:
     │   orcidId = "0000-0001-2345-6789"
     │   email = "j.smith@oxford.ac.uk"
     │   emailSource = "PAPER_FULLTEXT"
     │   emailVerifiedLevel = 3
     │   dataSource = "EUROPE_PMC"
     │   country = "GB" (从 affiliation 推断)
     │   lastPublicationYear = 2024
     │
     ├─ 写入 ES RAW 索引 (L3)
     │
     ├─ CandidateEligibilityService.evaluateEligibility()
     │   orcidId 非空: ✅
     │   邮箱有效: ✅
     │   非中国国籍: ✅ (GB)
     │   → EligibilityResult(eligible=true)
     │
     └─ 晋升到 ES CANDIDATE 索引 (L2) → 等待外联
```

---

## 16. 实现阶段

### Phase 1（P0）—— 邮箱验证增强 + 存量验证（2-3 天）

- [ ] `EmailValidationService`（格式 + 黑名单 + MX）
- [ ] `disposable-domains.txt`
- [ ] `email_validation_cache` 表 + V11 迁移
- [ ] `CandidateEligibilityService` 增强（返回 `EligibilityResult`，向后兼容）
- [ ] `ExpertRevalidationService` + `POST /api/expert-discovery/revalidate`
- [ ] 单元测试

### Phase 2（P1）—— Europe PMC 对接 + 论文邮箱提取（4-5 天）

- [ ] `EuropePmcDataSource`（搜索 + 全文拉取）
- [ ] `JatsXmlEmailParser`（JATS XML 邮箱解析）
- [ ] `ExpertProfile` 新字段 + ES mapping 更新
- [ ] `ExpertDiscoveryService` 编排
- [ ] `ExpertDiscoveryScheduler` 定时任务
- [ ] `ExpertDiscoveryController` REST API
- [ ] 集成测试（mock Europe PMC 响应）

### Phase 3（P2）—— OpenAlex 学术指标 + 多源融合（3-4 天）

- [ ] `OpenAlexDataSource`（论文发现 + 作者指标补充）
- [ ] `AcademicFilterProperties` 配置化
- [ ] h-index / 引用数 / 活跃度过滤规则
- [ ] 多源去重（同一作者不同数据源合并）
- [ ] 前端发现任务面板

### Phase 4（P3）—— 持续优化

- [ ] ORCID 邮箱交叉验证
- [ ] 机构邮箱推断 + SMTP 验证
- [ ] 退信自动标记
- [ ] 过滤规则管理界面（DB 化）
- [ ] 更多数据源接入

---

## 17. 风险与应对

| 风险 | 应对 |
|------|------|
| Europe PMC 全文 XML 拉取慢 | 请求间隔 100ms + 单次上限 500 篇 + 失败重试 |
| 邮箱从论文提取但已过期 | MX 校验兜底 + 退信标记 + 缓存过期重新验证 |
| 同一作者多篇论文不同邮箱 | 取最新论文邮箱为主 + 保留历史 |
| 存量降级误伤 | 降级仅删 CANDIDATE 索引，RAW 保留，可重新晋升 |
| MX/DNS 查询超时 | 超时降级为通过 + 结果缓存 30 天 |
| ES mapping 变更 | 用 PUT mapping API 追加字段，不影响现有数据 |
| 论文非英文 | JATS XML 标签结构一致，邮箱解析不受语言影响 |
