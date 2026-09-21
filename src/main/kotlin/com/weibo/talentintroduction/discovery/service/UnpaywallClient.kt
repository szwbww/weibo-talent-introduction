package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.UnpaywallProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class UnpaywallClient(
    private val restTemplate: RestTemplate,
    private val properties: UnpaywallProperties
) {
    private val log = LoggerFactory.getLogger(UnpaywallClient::class.java)

    fun isConfigured(): Boolean = properties.email.isNotBlank()

    fun findPdfUrl(doi: String): String? = findPdfUrls(doi).firstOrNull()

    /**
     * c10（I-1）：Unpaywall 返回的**全部**开放位置 —— best_oa_location 优先，其后按返回顺序去重，
     * 且只保留公开 http(s) 链接（付费墙/非公开协议一律不下发，调用方也不会去绕）。
     * 有界回退链在首选地址失效时按这个顺序继续尝试，而不是只认第一条。
     */
    fun findPdfUrls(doi: String): List<String> {
        if (!isConfigured()) {
            log.debug("Unpaywall not configured (email missing)")
            return emptyList()
        }

        val url = "${properties.baseUrl}/$doi?email=${properties.email}"
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = restTemplate.getForObject(url, JsonNode::class.java) ?: return emptyList()
            val urls = LinkedHashSet<String>()
            publicFulltextUrl(response.path("best_oa_location").path("url_for_pdf").asText(null))?.let(urls::add)

            val locations = response.path("oa_locations")
            if (locations.isArray) {
                for (loc in locations) {
                    publicFulltextUrl(loc.path("url_for_pdf").asText(null))?.let(urls::add)
                }
            }
            urls.toList()
        } catch (e: Exception) {
            log.debug("Unpaywall lookup failed for {}: {}", doi, e.message)
            emptyList()
        }
    }
}
