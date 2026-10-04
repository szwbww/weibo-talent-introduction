package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmRequest
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewRevokeRequest
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewConflictException
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewTimeoutException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * 02（I-3）：深度发现审核 API。全部端点要求登录态，actor 只取会话
 * （`AuthSessionKeys.USERNAME`），绝不接受客户端伪造用户名；不发送任何邮件。
 * 冲突/超时由本控制器自己的 `@ExceptionHandler` 映射为 409 / 503（其余 400/404 由全局
 * advice 处理）。
 */
@RestController
@RequestMapping("/api/discovery/review")
class DiscoveryReviewController(private val service: DiscoveryReviewService) {

    @GetMapping("/experts")
    fun listExperts(
        request: HttpServletRequest,
        @RequestParam(required = false) level: String?,
        @RequestParam(defaultValue = "discovered") tag: String,
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) issue: String?,
        @RequestParam(required = false) decision: String?
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.listExperts(level, tag, from, size, q, issue, decision))
    }

    @PostMapping("/batches/prepare")
    fun prepare(
        request: HttpServletRequest,
        @RequestBody body: DiscoveryReviewPrepareRequest
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.prepare(body, actor))
    }

    @PostMapping("/batches/{batchKey}/confirm")
    fun confirm(
        request: HttpServletRequest,
        @PathVariable batchKey: String,
        @RequestBody body: DiscoveryReviewConfirmRequest
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.confirm(batchKey, body.batchHash, actor))
    }

    @GetMapping("/batches/{batchKey}")
    fun batchDetail(
        request: HttpServletRequest,
        @PathVariable batchKey: String
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.batchDetail(batchKey))
    }

    @GetMapping("/history")
    fun history(
        request: HttpServletRequest,
        @RequestParam docId: String,
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.history(docId, limit))
    }

    @PostMapping("/items/{id}/revoke")
    fun revoke(
        request: HttpServletRequest,
        @PathVariable id: Long,
        @RequestBody(required = false) body: DiscoveryReviewRevokeRequest?
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.revoke(id, actor, body?.note))
    }

    @ExceptionHandler(DiscoveryReviewConflictException::class)
    fun handleConflict(ex: DiscoveryReviewConflictException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse("CONFLICT", ex.message ?: "状态冲突", null))

    @ExceptionHandler(DiscoveryReviewTimeoutException::class)
    fun handleTimeout(ex: DiscoveryReviewTimeoutException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiErrorResponse("REVIEW_SCAN_TIMEOUT", ex.message ?: "查询超时", null))

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
