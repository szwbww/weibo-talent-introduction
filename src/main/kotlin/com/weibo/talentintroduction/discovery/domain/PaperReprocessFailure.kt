package com.weibo.talentintroduction.discovery.domain

import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/** Store diagnostic structure without provider response bodies, credentials or personal data. */
internal object PaperReprocessFailure {
    private val log = LoggerFactory.getLogger(PaperReprocessFailure::class.java)
    fun record(result: ObjectNode, error: Exception, runId: Long, itemId: Long) {
        val failure = result.putObject("failure").put("reference", "$runId/$itemId")
            .put("type", error.javaClass.simpleName)
        val causes = failure.putArray("causes")
        var current: Throwable? = error
        repeat(4) {
            val cause = current ?: return@repeat
            val code = cause.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,95}")) }
            val entry = causes.addObject().put("type", cause.javaClass.simpleName)
            code?.let { entry.put("code", it) }
            val frames = entry.putArray("frames")
            cause.stackTrace.filter { it.className.startsWith("com.weibo.talentintroduction.") }
                .take(6).forEach { frames.add("${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
            current = cause.cause?.takeUnless { it === cause }
        }
        log.warn("Paper reprocess failure run={} item={} detail={}", runId, itemId, failure)
    }
}
