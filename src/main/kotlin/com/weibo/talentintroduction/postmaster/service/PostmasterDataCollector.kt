package com.weibo.talentintroduction.postmaster.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmailpostmastertools.v2.PostmasterTools
import com.google.api.services.gmailpostmastertools.v2.model.BaseMetric
import com.google.api.services.gmailpostmastertools.v2.model.Date
import com.google.api.services.gmailpostmastertools.v2.model.DateList
import com.google.api.services.gmailpostmastertools.v2.model.MetricDefinition
import com.google.api.services.gmailpostmastertools.v2.model.QueryDomainStatsRequest
import com.google.api.services.gmailpostmastertools.v2.model.TimeQuery
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.postmaster.domain.DomainReputationHistory
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalDateTime

data class CollectedDomainStats(
    val spamRate: Double? = null,
    val domainReputation: String? = null,
    val spfSuccessRate: Double? = null,
    val dkimSuccessRate: Double? = null,
    val dmarcSuccessRate: Double? = null,
    val rawJson: String? = null
)

fun interface DomainStatsFetcher {
    fun fetch(domain: String, date: LocalDate): CollectedDomainStats?
}

@Service
@ConditionalOnProperty(prefix = "talent-introduction.postmaster", name = ["enabled"], havingValue = "true")
class GoogleDomainStatsFetcher(
    private val properties: PostmasterProperties,
    private val objectMapper: ObjectMapper
) : DomainStatsFetcher {
    private val log = LoggerFactory.getLogger(GoogleDomainStatsFetcher::class.java)

    override fun fetch(domain: String, date: LocalDate): CollectedDomainStats? {
        if (properties.credentialsJson.isBlank()) {
            log.warn("Postmaster credentialsJson not configured, skipping domain {}", domain)
            return null
        }
        return try {
            val service = buildClient()
            val parent = "domains/$domain"
            val request = QueryDomainStatsRequest().apply {
                metricDefinitions = METRIC_DEFINITIONS
                timeQuery = TimeQuery().setDateList(
                    DateList().setDates(
                        listOf(
                            Date()
                                .setYear(date.year)
                                .setMonth(date.monthValue)
                                .setDay(date.dayOfMonth)
                        )
                    )
                )
                aggregationGranularity = "DAILY"
            }
            val response = service.domains().domainStats().query(parent, request).execute()
            parseResponse(response, objectMapper)
        } catch (ex: Exception) {
            log.warn("Failed to fetch Postmaster stats for domain {} on {}: {}", domain, date, ex.message)
            null
        }
    }

    private fun buildClient(): PostmasterTools {
        val credentials = GoogleCredentials.fromStream(FileInputStream(properties.credentialsJson))
            .createScoped(listOf("https://www.googleapis.com/auth/postmaster.readonly"))
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        return PostmasterTools.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName("weibo-talent-introduction")
            .build()
    }

    companion object {
        private val METRIC_DEFINITIONS = listOf(
            metric("spamRate", "SPAM_RATE"),
            metric("spfSuccessRate", "AUTH_SUCCESS_RATE", """auth_type = "spf""""),
            metric("dkimSuccessRate", "AUTH_SUCCESS_RATE", """auth_type = "dkim""""),
            metric("dmarcSuccessRate", "AUTH_SUCCESS_RATE", """auth_type = "dmarc"""")
        )

        private fun metric(name: String, standardMetric: String, filter: String? = null): MetricDefinition =
            MetricDefinition().apply {
                this.name = name
                baseMetric = BaseMetric().setStandardMetric(standardMetric)
                if (filter != null) {
                    this.filter = filter
                }
            }

        fun parseResponse(
            response: com.google.api.services.gmailpostmastertools.v2.model.QueryDomainStatsResponse,
            objectMapper: ObjectMapper
        ): CollectedDomainStats {
            val values = mutableMapOf<String, Double?>()
            response.domainStats?.forEach { stat ->
                val metricName = stat.metric ?: return@forEach
                values[metricName] = stat.value?.doubleValue
            }
            return CollectedDomainStats(
                spamRate = values["spamRate"],
                spfSuccessRate = values["spfSuccessRate"],
                dkimSuccessRate = values["dkimSuccessRate"],
                dmarcSuccessRate = values["dmarcSuccessRate"],
                rawJson = objectMapper.writeValueAsString(response)
            )
        }
    }
}

@Service
@ConditionalOnProperty(prefix = "talent-introduction.postmaster", name = ["enabled"], havingValue = "true")
class PostmasterDataCollector(
    private val properties: PostmasterProperties,
    private val repository: DomainReputationHistoryRepository,
    private val fetcher: DomainStatsFetcher
) {
    private val log = LoggerFactory.getLogger(PostmasterDataCollector::class.java)

    fun collect(date: LocalDate = LocalDate.now().minusDays(1)) {
        properties.domains.forEach { domain ->
            try {
                val stats = fetcher.fetch(domain, date) ?: return@forEach
                upsert(domain, date, stats)
            } catch (ex: Exception) {
                log.warn("Failed to collect Postmaster data for domain {} on {}: {}", domain, date, ex.message)
            }
        }
    }

    internal fun upsert(domain: String, date: LocalDate, stats: CollectedDomainStats) {
        val existing = repository.findByDomainAndReportDate(domain, date)
        val row = DomainReputationHistory(
            id = existing?.id,
            domain = domain,
            reportDate = date,
            spamRate = stats.spamRate,
            domainReputation = stats.domainReputation,
            spfSuccessRate = stats.spfSuccessRate,
            dkimSuccessRate = stats.dkimSuccessRate,
            dmarcSuccessRate = stats.dmarcSuccessRate,
            rawJson = stats.rawJson,
            collectedAt = LocalDateTime.now()
        )
        repository.save(row)
    }
}
