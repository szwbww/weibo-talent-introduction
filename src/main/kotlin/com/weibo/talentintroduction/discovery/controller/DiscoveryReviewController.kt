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
 * 02（I-3）+ 03：深度发现审核 API。全部端点要求登录态，actor 只取会话
 * （`AuthSessionKeys.USERNAME`），绝不接受客户端伪造用户名；不发送任何邮件。
 * 冲突/超时由本控制器自己的 `@ExceptionHandler` 映射为 409 / 503（其余 400/404 由全局
 * advice 处理）。
 *
 * 03 追加：`scope=ALL_MATCHING` 的异步名单固定与批量应用（`prepare`/`confirm`/`retry`/`cancel`），
 * 以及持久明细驱动的 `GET /batches/{batchKey}` 状态。控制器只做会话/参数与状态码编解码，
 * 任务标识、phase、hash、计数全部来自 [DiscoveryReviewService] 的持久状态。
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

    /**
     * `POST /batches/prepare`。
     *
     * - `scope=IDS`（默认）：同步准备所选 docId 的快照，返回 200。
     * - `scope=ALL_MATCHING`：固定服务端筛选（`tag/q/issue`，服务端再强制 `NEEDS_REVIEW`），
     *   异步 ES scroll 全量扫描，202 受理；完成前 `GET /batches/{key}` 显示「正在固定名单」。
     *   可选 `requestKey` 作为批次标识，相同 requestKey + 相同筛选幂等返回，不同筛选 409。
     */
    @PostMapping("/batches/prepare")
    fun prepare(
        request: HttpServletRequest,
        @RequestBody body: DiscoveryReviewPrepareRequest,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) issue: String?,
        @RequestParam(required = false) requestKey: String?
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return if (body.scope?.trim().equals(DiscoveryReviewService.SCOPE_ALL_MATCHING, ignoreCase = true)) {
            ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.prepareAllMatching(body, tag, q, issue, requestKey, actor))
        } else {
            ResponseEntity.ok(service.prepare(body, actor))
        }
    }

    /** `POST /batches/{batchKey}/confirm`：IDS 与全页共用；全页消费已存 batchKey+hash，重复确认幂等。 */
    @PostMapping("/batches/{batchKey}/confirm")
    fun confirm(
        request: HttpServletRequest,
        @PathVariable batchKey: String,
        @RequestBody body: DiscoveryReviewConfirmRequest
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.confirm(batchKey, body.batchHash, actor))
    }

    /** `POST /batches/{batchKey}/retry`：仅用户明确点击后重领 FAILED/未处理项；STALE 需重新 prepare。 */
    @PostMapping("/batches/{batchKey}/retry")
    fun retry(
        request: HttpServletRequest,
        @PathVariable batchKey: String
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.retryBatch(batchKey, actor))
    }

    /** `POST /batches/{batchKey}/cancel`：只影响未应用项，已应用结果不倒退。 */
    @PostMapping("/batches/{batchKey}/cancel")
    fun cancel(
        request: HttpServletRequest,
        @PathVariable batchKey: String
    ): ResponseEntity<Any> {
        val actor = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.cancelBatch(batchKey, actor))
    }

    /** `GET /batches/{batchKey}`：阶段 + 计数 + id 游标分页明细（`total = applied+stale+failed+cancelled+pending`）。 */
    @GetMapping("/batches/{batchKey}")
    fun batchStatus(
        request: HttpServletRequest,
        @PathVariable batchKey: String,
        @RequestParam(defaultValue = "0") afterId: Long,
        @RequestParam(defaultValue = "200") limit: Int
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.batchStatus(batchKey, afterId, limit))
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
