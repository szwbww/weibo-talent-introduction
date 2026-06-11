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

    fun findPdfUrl(doi: String): String? {
        if (!isConfigured()) {
            log.debug("Unpaywall not configured (email missing)")
            return null
        }

        val url = "${properties.baseUrl}/$doi?email=${properties.email}"
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = restTemplate.getForObject(url, JsonNode::class.java) ?: return null
            val bestOa = response.path("best_oa_location")
            val pdfUrl = bestOa.path("url_for_pdf").asText(null)
            if (!pdfUrl.isNullOrBlank()) return pdfUrl

            val locations = response.path("oa_locations")
            if (locations.isArray) {
                for (loc in locations) {
                    val locPdf = loc.path("url_for_pdf").asText(null)
                    if (!locPdf.isNullOrBlank()) return locPdf
                }
            }
            null
        } catch (e: Exception) {
            log.debug("Unpaywall lookup failed for {}: {}", doi, e.message)
            null
        }
    }
}
