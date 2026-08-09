package com.weibo.talentintroduction.postmaster.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmailpostmastertools.v2.PostmasterTools
import com.google.api.services.gmailpostmastertools.v2.PostmasterToolsScopes
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
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime

data class CollectedDomainStats(
    val spamRate: Double? = null,
    /**
     * v2 的 StandardMetric 枚举中没有域名信誉指标（仅 SPAM_RATE / AUTH_SUCCESS_RATE /
     * TLS_ENCRYPTION_* / DELIVERY_ERROR_* / FEEDBACK_LOOP_*），域名信誉是 v1 的概念。
     * 该字段在当前采集链路下恒为 null，落库与接口返回都会是空，属预期而非故障。
     */
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
            // 带上异常本身：鉴权/权限类失败（scope 不对、域名未在 Postmaster 验证、服务账号无权限）
            // 只看 message 往往分辨不出，缺少堆栈会让接入排查无从下手。
            log.warn("Failed to fetch Postmaster stats for domain {} on {}: {}", domain, date, ex.message, ex)
            null
        }
    }

    private fun buildClient(): PostmasterTools {
        val credentials = openCredentialsStream(properties.credentialsJson).use { stream ->
            GoogleCredentials.fromStream(stream).createScoped(listOf(REQUIRED_SCOPE))
        }
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
        /**
         * v2 的 domainStats.query 只接受 postmaster / postmaster.traffic.readonly 两个 scope。
         * v1 时代的 postmaster.readonly 在 v2 已不存在，误用会直接鉴权失败。
         */
        internal val REQUIRED_SCOPE: String = PostmasterToolsScopes.POSTMASTER_TRAFFIC_READONLY

        /**
         * 配置项既支持直接内联服务账号 JSON（以 { 开头），也支持文件路径。
         * 环境变量名为 POSTMASTER_CREDENTIALS_JSON，容易被误当成内容粘贴，这里两种都接受。
         */
        internal fun openCredentialsStream(credentials: String): InputStream {
            val trimmed = credentials.trim()
            return if (trimmed.startsWith("{")) {
                ByteArrayInputStream(trimmed.toByteArray(StandardCharsets.UTF_8))
            } else {
                FileInputStream(trimmed)
            }
        }

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
