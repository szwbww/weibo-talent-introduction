package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexSourceMode
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ai-training/unsupported-answers")
class UnsupportedAnswerIndexController(
    private val unsupportedAnswerIndexService: UnsupportedAnswerIndexService
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sourceMode: String?
    ): ResponseEntity<Any> {
        val resolvedSize = size ?: 20
        if (page < 0 || resolvedSize !in 1..100) return invalidRequest()
        val resolvedSourceMode = sourceMode?.takeIf(String::isNotBlank)?.let {
            runCatching { UnsupportedAnswerIndexSourceMode.valueOf(it) }.getOrNull() ?: return invalidRequest()
        }
        return try {
            ResponseEntity.ok(unsupportedAnswerIndexService.list(page, resolvedSize, resolvedSourceMode))
        } catch (_: UnsupportedAnswerIndexUnavailableException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf("code" to "UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE", "message" to "无依据回答索引暂不可用")
            )
        }
    }

    private fun invalidRequest(): ResponseEntity<Any> = ResponseEntity.badRequest().body(
        mapOf("code" to "INVALID_UNSUPPORTED_ANSWER_INDEX_QUERY", "message" to "分页或来源参数无效")
    )
}
