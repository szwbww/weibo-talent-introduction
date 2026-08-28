package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexSourceMode
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
        @RequestParam(required = false) sourceMode: String?,
        // c6 (T-3 / I-3)：可选 topic keyword 精确过滤；非法值按既有 invalidRequest() 返回 400。
        @RequestParam(required = false) topic: String? = null
    ): ResponseEntity<Any> {
        val resolvedSize = size ?: 20
        if (page < 0 || resolvedSize !in 1..100) return invalidRequest()
        val resolvedSourceMode = sourceMode?.takeIf(String::isNotBlank)?.let {
            runCatching { UnsupportedAnswerIndexSourceMode.valueOf(it) }.getOrNull() ?: return invalidRequest()
        }
        val resolvedTopic = topic?.takeIf(String::isNotBlank)?.let {
            it.takeIf { value -> value.length <= MAX_TOPIC_LENGTH } ?: return invalidRequest()
        }
        return try {
            ResponseEntity.ok(unsupportedAnswerIndexService.list(page, resolvedSize, resolvedSourceMode, resolvedTopic))
        } catch (_: UnsupportedAnswerIndexUnavailableException) {
            unavailable()
        }
    }

    /**
     * c6 (T-5 通道 B)：待转事实队列 —— 按 topic 聚合 `status = CANDIDATE` 条目，
     * 命中次数 ≥ 阈值（默认 3）。threshold 非法（< 1 或 > 1000）返回 400。
     */
    @GetMapping("/pending-topics")
    fun pendingTopics(
        @RequestParam(defaultValue = "3") threshold: Int
    ): ResponseEntity<Any> {
        if (threshold !in 1..1000) return invalidRequest()
        return try {
            ResponseEntity.ok(unsupportedAnswerIndexService.pendingTopics(threshold))
        } catch (_: UnsupportedAnswerIndexUnavailableException) {
            unavailable()
        }
    }

    /**
     * c6 (T-5 通道 B)：运营保存 QA 规则后调用——把该主题全部 CANDIDATE 条目置为
     * ACTIVE（I-5）。不自动创建规则。topic 为空或超长返回 400。
     */
    @PostMapping("/pending-topics/{topic}/activate")
    fun activatePendingTopic(@PathVariable topic: String): ResponseEntity<Any> {
        val normalized = topic.trim()
        if (normalized.isEmpty() || normalized.length > MAX_TOPIC_LENGTH || normalized.contains("/")) {
            return invalidRequest()
        }
        return try {
            ResponseEntity.ok(
                mapOf(
                    "topic" to normalized,
                    "updated" to unsupportedAnswerIndexService.activatePendingTopic(normalized)
                )
            )
        } catch (_: UnsupportedAnswerIndexUnavailableException) {
            unavailable()
        }
    }

    private fun invalidRequest(): ResponseEntity<Any> = ResponseEntity.badRequest().body(
        mapOf("code" to "INVALID_UNSUPPORTED_ANSWER_INDEX_QUERY", "message" to "分页或来源参数无效")
    )

    private fun unavailable(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
        mapOf("code" to "UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE", "message" to "无依据回答索引暂不可用")
    )

    companion object {
        private const val MAX_TOPIC_LENGTH = 512
    }
}
