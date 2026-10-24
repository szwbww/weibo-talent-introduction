package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewCountsBusyException
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewCountsReadException
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewCountsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/discovery/review/counts")
class DiscoveryReviewCountsController(private val service: DiscoveryReviewCountsService) {
    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.refresh(actor))
    }

    @GetMapping
    fun status(request: HttpServletRequest, @RequestParam requestKey: String): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.status(requestKey))
    }

    @ExceptionHandler(DiscoveryReviewCountsBusyException::class)
    fun busy(ex: DiscoveryReviewCountsBusyException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse("COUNTS_BUSY", ex.message ?: "统计繁忙", null))

    @ExceptionHandler(DiscoveryReviewCountsReadException::class)
    fun readFailed(ex: DiscoveryReviewCountsReadException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiErrorResponse("COUNTS_DATABASE_FAILED", ex.message ?: "统计记录读取失败", null))

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
