package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ArxivProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.arxiv", name = ["enabled"], havingValue = "true")
class ArxivDataSource(
    private val restTemplate: RestTemplate,
    private val properties: ArxivProperties,
    private val pdfEmailExtractor: PdfEmailExtractor
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(ArxivDataSource::class.java)

    override val sourceName = "ARXIV"
    override val emailExtractionMethod = "PDF_PARSE"
    override val maxPapersPerSource get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val keywordQuery = if (criteria.keywords.isNotEmpty()) {
            criteria.keywords.joinToString("+AND+") { "all:\"${URLEncoder.encode(it, "UTF-8")}\"" }
        } else {
            // I4-2: 无关键词且无学科分类时兜底 "all:*"，与改动前逐字相同。
            val cats = SubjectScopeCatalog.arxivCategories(criteria.subjectScope)
            if (cats.isNotEmpty()) {
                cats.joinToString("+OR+") { "cat:$it*" }
            } else {
                "all:*"
            }
        }

        val start = criteria.cursor?.toIntOrNull() ?: 0
        val url = "${properties.baseUrl}/query?" +
            "search_query=$keywordQuery" +
            "&start=$start" +
            "&max_results=${criteria.pageSize}" +
            "&sortBy=submittedDate" +
            "&sortOrder=descending"

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, String::class.java)
        } catch (e: Exception) {
            log.error("arXiv search failed: {}", e.message)
            throw e
        }

        return parseAtomResponse(response, criteria)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        val arxivId = paper.doi?.removePrefix("arXiv:") ?: paper.pmcId
        if (arxivId == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_DOI")
        }

        val pdfUrl = "https://arxiv.org/pdf/$arxivId"
        return pdfEmailExtractor.extract(pdfUrl, paper.authors, sourceName)
    }

    fun parseAtomResponse(xml: String?, criteria: PaperSearchCriteria): PaperSearchResult {
        if (xml.isNullOrBlank()) return PaperSearchResult(emptyList(), null, 0)

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(org.xml.sax.InputSource(StringReader(xml)))

            val totalResultsNodes = doc.getElementsByTagName("opensearch:totalResults")
            val totalResults = if (totalResultsNodes.length > 0) {
                totalResultsNodes.item(0).textContent.trim().toLongOrNull() ?: 0L
            } else 0L

            val entries = doc.getElementsByTagName("entry")
            val rawEntryCount = entries.length
            val papers = mutableListOf<PaperMetadata>()

            for (i in 0 until rawEntryCount) {
                val entry = entries.item(i) as org.w3c.dom.Element
                val id = entry.getElementsByTagName("id").item(0)?.textContent?.trim()
                    ?.removePrefix("http://arxiv.org/abs/")
                val title = entry.getElementsByTagName("title").item(0)?.textContent?.trim() ?: ""
                val published = entry.getElementsByTagName("published").item(0)?.textContent?.trim()
                val pubYear = published?.substring(0, 4)?.toIntOrNull() ?: 0

                if (pubYear < criteria.publicationYearFrom || pubYear > criteria.publicationYearTo) {
                    continue
                }

                val authorNodes = entry.getElementsByTagName("author")
                val authors = mutableListOf<PaperAuthor>()
                for (j in 0 until authorNodes.length) {
                    val authorEl = authorNodes.item(j) as org.w3c.dom.Element
                    val name = authorEl.getElementsByTagName("name").item(0)?.textContent?.trim()
                    if (name != null) {
                        val parts = name.split(" ", limit = 2)
                        authors.add(PaperAuthor(
                            givenNames = parts.getOrNull(0),
                            familyNames = parts.getOrNull(1),
                            orcidId = null,
                            affiliation = null,
                            isCorresponding = false
                        ))
                    }
                }

                papers.add(PaperMetadata(
                    pmcId = null,
                    pmid = null,
                    doi = id?.let { "arXiv:$it" },
                    title = title,
                    pubYear = pubYear,
                    journal = "arXiv",
                    authors = authors,
                    source = sourceName
                ))
            }

            val start = criteria.cursor?.toIntOrNull() ?: 0
            val nextCursor = if (rawEntryCount > 0 && start + rawEntryCount < totalResults) {
                (start + rawEntryCount).toString()
            } else null

            return PaperSearchResult(papers, nextCursor, totalResults)
        } catch (e: Exception) {
            log.error("Failed to parse arXiv Atom response: {}", e.message)
            return PaperSearchResult(emptyList(), null, 0)
        }
    }
}
