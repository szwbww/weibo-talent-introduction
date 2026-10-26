package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.discovery.domain.VerificationState
import com.weibo.talentintroduction.discovery.service.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

/** Session identity only. No client snapshot, actor, admission or task identity is accepted. */
@RestController
@RequestMapping("/api/discovery/rechecks")
class DiscoveryRecheckController(private val service: DiscoveryRecheckService,
    private val queries: DiscoveryVerificationQueryService) {
    @PostMapping("/prepare")
    fun prepare(request: HttpServletRequest, @RequestBody body: RecheckPrepareRequest): ResponseEntity<Any> {
        val actor = actor(request) ?: return unauthorized()
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.prepare(body, actor))
    }

    @PostMapping("/{runId}/start")
    fun start(request: HttpServletRequest, @PathVariable runId: Long, @RequestBody body: RecheckStartRequest): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(service.start(runId, body))
    }

    @PostMapping("/{runId}/cancel")
    fun cancel(request: HttpServletRequest, @PathVariable runId: Long): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(service.cancel(runId))
    }

    @PostMapping("/{runId}/sync-retry")
    fun syncRetry(request: HttpServletRequest, @PathVariable runId: Long, @RequestBody body: RecheckSyncRequest): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(service.syncRetry(runId, body))
    }

    @GetMapping("/runs")
    fun runs(request: HttpServletRequest, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int, @RequestParam(required = false) docId: String?): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.recheckRuns(page, size, docId))
    }

    @GetMapping("/active")
    fun active(request: HttpServletRequest): ResponseEntity<Any> {
        val actor = actor(request) ?: return unauthorized()
        return ResponseEntity.ok(service.active(actor))
    }

    @GetMapping("/latest")
    fun latest(request: HttpServletRequest, @RequestParam docIds: List<String>): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.latestRechecks(docIds))
    }

    @GetMapping("/{runId}")
    fun run(request: HttpServletRequest, @PathVariable runId: Long): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.recheckRun(runId))
    }

    @GetMapping("/{runId}/items")
    fun items(request: HttpServletRequest, @PathVariable runId: Long, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int, @RequestParam(required = false) state: VerificationState?): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.recheckItems(runId, page, size, state))
    }

    @GetMapping("/{runId}/items/{itemId}/steps")
    fun steps(request: HttpServletRequest, @PathVariable runId: Long, @PathVariable itemId: Long): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.recheckItem(runId, itemId))
    }

    @GetMapping("/{runId}/manifest")
    fun manifest(request: HttpServletRequest, @PathVariable runId: Long, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int): ResponseEntity<Any> {
        actor(request) ?: return unauthorized()
        return ResponseEntity.ok(queries.recheckManifest(runId, page, size))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun status(ex: ResponseStatusException): ResponseEntity<ApiErrorResponse> = ResponseEntity.status(ex.status)
        .body(ApiErrorResponse(ex.reason ?: ex.status.name, ex.reason ?: ex.status.reasonPhrase, null))

    private fun actor(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
