package com.weibo.talentintroduction.discovery.domain

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientResponseException

/** Store diagnostic structure without provider response bodies, credentials or personal data. */
internal object PaperReprocessFailure {
    private val log = LoggerFactory.getLogger(PaperReprocessFailure::class.java)
    private val mapper = jacksonObjectMapper()
    fun record(result: ObjectNode, error: Exception, runId: Long, itemId: Long) {
        val failure = details(error).put("reference", "$runId/$itemId")
        result.set<ObjectNode>("failure", failure)
        log.warn("Paper reprocess failure run={} item={} detail={}", runId, itemId, failure)
    }

    fun log(event: String, error: Exception, runId: Long? = null) {
        log.warn("event={} run={} detail={}", event, runId, details(error))
    }

    private fun details(error: Exception): ObjectNode {
        val failure = mapper.createObjectNode().put("type", error.javaClass.simpleName)
        val causes = failure.putArray("causes")
        var current: Throwable? = error
        repeat(4) {
            val cause = current ?: return@repeat
            val code = cause.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,95}")) }
            val entry = causes.addObject().put("type", cause.javaClass.simpleName)
            code?.let { entry.put("code", it) }
            if (cause is RestClientResponseException) {
                entry.put("httpStatus", cause.rawStatusCode)
                // Never retain response bodies, reason strings, URLs, cookies or authorization headers.
                fun marker(value: String?): String? = value?.takeIf { it.matches(Regex("[A-Za-z0-9_./:;= -]{1,160}")) }
                marker(cause.responseHeaders?.getFirst("Server"))?.let { entry.put("server", it) }
                marker(cause.responseHeaders?.getFirst("Content-Type"))?.let { entry.put("contentType", it) }
                marker(cause.responseHeaders?.getFirst("X-Request-Id"))?.let { entry.put("requestId", it) }
                val bytes = cause.responseBodyAsByteArray
                entry.put("responseBytes", bytes.size)
                if (bytes.size <= 65_536) {
                    val type = runCatching { mapper.readTree(bytes)?.path("error")?.path("type")?.asText() }.getOrNull()
                    type?.takeIf { it.matches(Regex("[a-z_]{1,80}")) }?.let { entry.put("esErrorType", it) }
                }
            }
            val frames = entry.putArray("frames")
            cause.stackTrace.filter { it.className.startsWith("com.weibo.talentintroduction.") }
                .take(6).forEach { frames.add("${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
            current = cause.cause?.takeUnless { it === cause }
        }
        return failure
    }
}
