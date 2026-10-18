package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.discovery.repository.VerificationReportFilter
import com.weibo.talentintroduction.discovery.service.DiscoveryVerificationQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/expert-discovery/verification")
class DiscoveryVerificationController(private val query: DiscoveryVerificationQueryService) {
    @GetMapping("/capabilities")
    fun capabilities() = read { query.capabilities() }

    @GetMapping("/summary")
    fun summary(@RequestParam executionIds: String) = parseExecutionIds(executionIds).let { ids -> read { query.summary(ids) } }

    @GetMapping("/executions/{id}/batches")
    fun batches(@PathVariable id: Long, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int): com.weibo.talentintroduction.discovery.service.VerificationBatches {
        positive(id); validatePage(page, size)
        return read { query.batches(id, page, size) }
    }

    @GetMapping("/executions/{id}/records")
    fun records(@PathVariable id: Long, @RequestParam(defaultValue = "ALL") route: String,
        @RequestParam(defaultValue = "ALL") result: String, @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) batchNumber: Int?): com.weibo.talentintroduction.discovery.service.VerificationRecords {
        val filter = VerificationReportFilter(positive(id), route, result, q, batchNumber)
        validatePage(page, size)
        return read { query.records(filter, page, size) }
    }

    @GetMapping("/items/{itemId}")
    fun item(@PathVariable itemId: Long) = positive(itemId).let { id -> read { query.item(id) } }

    @ExceptionHandler(IllegalArgumentException::class, MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class)
    fun invalidParameter() = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("code" to "INVALID_VERIFICATION_PARAMETER"))

    @ExceptionHandler(ResponseStatusException::class)
    fun status(error: ResponseStatusException) = ResponseEntity.status(error.status)
        .body(mapOf("code" to if (error.status == HttpStatus.NOT_FOUND) "VERIFICATION_ITEM_NOT_FOUND" else "VERIFICATION_UNAVAILABLE"))

    @ExceptionHandler(Exception::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("code" to "VERIFICATION_READ_FAILED"))

    private fun positive(id: Long): Long { require(id > 0); return id }
    private fun validatePage(page: Int, size: Int) { require(page >= 0 && size in 1..100) }
    private fun <T> read(action: () -> T): T = try {
        action()
    } catch (error: ResponseStatusException) {
        throw error
    } catch (error: Exception) {
        // Invalid persisted data is a read failure too, never a client parameter error or an empty result.
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Verification read failed", error)
    }
    private fun parseExecutionIds(value: String): List<Long> {
        val tokens = value.split(',')
        require(tokens.size in 1..50)
        return tokens.map { token ->
            require(token.matches(Regex("[0-9]+")))
            token.toLongOrNull()?.let(::positive) ?: throw IllegalArgumentException("Invalid execution ID")
        }
    }
}
