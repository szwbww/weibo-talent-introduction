package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.service.DiscoveryPaperReprocessService
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** Covered by the existing API authentication interceptor; never exposes archive paths. */
@RestController
@RequestMapping("/api/expert-discovery/paper-reprocess")
class DiscoveryPaperReprocessController(private val service: DiscoveryPaperReprocessService) {
    @GetMapping("/preview") fun preview(@RequestParam sourceExecutionId: Long) = service.preview(sourceExecutionId)
    @PostMapping("/runs") fun create(@RequestBody body: JsonNode): ResponseEntity<ReprocessRun> {
        fields(body, setOf("requestKey", "sourceExecutionId", "mode", "expectedScopeHash"))
        val mode = ReprocessMode.values().firstOrNull { it.name == text(body, "mode") }
            ?: throw ReprocessException("INVALID_MODE", 400, "任务模式无效")
        return ResponseEntity.accepted().body(service.create(CreateReprocessRequest(text(body, "requestKey"),
            number(body, "sourceExecutionId"), mode, text(body, "expectedScopeHash"))))
    }
    @GetMapping("/runs") fun runs(@RequestParam(required = false) sourceExecutionId: Long?,
        @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) requestKey: String?,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) =
        service.runs(sourceExecutionId, active, requestKey, page, size)
    @GetMapping("/runs/{id}") fun detail(@PathVariable id: Long) = service.detail(id)
    @GetMapping("/runs/{id}/items") fun items(@PathVariable id: Long, @RequestParam(required = false) kind: ReprocessKind?,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = service.items(id, kind, page, size)
    @GetMapping("/runs/{id}/items/{itemId}") fun item(@PathVariable id: Long, @PathVariable itemId: Long) = service.item(id, itemId)
    @PostMapping("/runs/{id}/cancel") fun cancel(@PathVariable id: Long, @RequestBody body: JsonNode): ReprocessRun {
        fields(body, setOf("revision")); return service.cancel(id, number(body, "revision"))
    }
    @PostMapping("/runs/{id}/retry") fun retry(@PathVariable id: Long, @RequestBody body: JsonNode): ReprocessRun {
        fields(body, setOf("revision")); return service.retry(id, number(body, "revision"))
    }
    private fun fields(body: JsonNode, expected: Set<String>) {
        if (!body.isObject || body.fieldNames().asSequence().toSet() != expected)
            throw ReprocessException("INVALID_REQUEST", 400, "请求字段不正确")
    }
    private fun text(body: JsonNode, key: String): String = body.path(key).takeIf { it.isTextual }?.asText()
        ?: throw ReprocessException("INVALID_REQUEST", 400, "请求字段类型不正确")
    private fun number(body: JsonNode, key: String): Long = body.path(key).takeIf { it.isIntegralNumber && it.canConvertToLong() }?.longValue()
        ?: throw ReprocessException("INVALID_INTEGER", 400, "请求字段必须为整数")
    @ExceptionHandler(ReprocessException::class)
    fun failure(e: ReprocessException): ResponseEntity<Map<String, String>> = ResponseEntity.status(e.status)
        .body(mapOf("code" to e.code, "message" to e.message.orEmpty()))
    @ExceptionHandler(DataAccessException::class)
    fun unavailable(): ResponseEntity<Map<String, String>> = ResponseEntity.status(503)
        .body(mapOf("code" to "DB_UNAVAILABLE", "message" to "审计存储暂不可用，请稍后重试"))
}
