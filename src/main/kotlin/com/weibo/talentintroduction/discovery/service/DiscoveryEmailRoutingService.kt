package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EmailRouting
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.EmailRoutingService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

data class EmailRoutingRefreshResult(val docId: String, val routing: EmailRouting?,
    val updatedLayers: List<String>, val missingLayers: List<String>, val failedLayers: Map<String, String>)

@Service
class DiscoveryEmailRoutingService(private val writer: ExpertIndexWriterService, private val routing: EmailRoutingService) {
    fun refresh(docId: String): EmailRoutingRefreshResult {
        require(docId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid document ID" }
        val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, docId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "发现档案不存在")
        require(DiscoveryIdentity.isDiscoveryMap(raw.source)) { "仅支持深度发现档案" }
        val evidence = routing.identify(raw.source["email"] as? String)
            ?: return EmailRoutingRefreshResult(docId, null, emptyList(), emptyList(), mapOf("RAW" to "INVALID_EMAIL"))
        val updated = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        // RAW first. If its identity/version changed, do not propagate stale evidence to other layers.
        for (level in ExpertIndexLevel.values().sortedBy { if (it == ExpertIndexLevel.RAW) 0 else 1 }) {
            try {
                val snapshot = if (level == ExpertIndexLevel.RAW) raw else writer.readDiscoveryDocument(level, docId)
                if (snapshot == null) { missing += level.name; continue }
                if (EmailRouting.domainOf(snapshot.source["email"] as? String) != evidence.domain) {
                    failed[level.name] = "EMAIL_CHANGED"
                    continue
                }
                if (writer.writeEmailRoutingIfVersionMatches(level, docId, snapshot, evidence)) updated += level.name
                else failed[level.name] = "WRITE_FAILED"
            } catch (e: HttpClientErrorException) {
                failed[level.name] = if (e.statusCode == HttpStatus.CONFLICT) "VERSION_CONFLICT" else "HTTP_${e.rawStatusCode}"
            } catch (_: Exception) { failed[level.name] = "WRITE_FAILED" }
            if (level == ExpertIndexLevel.RAW && level.name in failed) break
        }
        return EmailRoutingRefreshResult(docId, evidence, updated, missing, failed)
    }
}
