package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRecoveryService
import com.weibo.talentintroduction.discovery.service.RecoveryApplyStatus
import com.weibo.talentintroduction.discovery.service.RecoveryStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/** I-3：预览请求 —— 客户端**只**能提交 `docId`。 */
data class RecoveryPreviewRequest(val docId: String? = null)

/** I-3：apply 请求 —— 客户端带 `docId` + 两项服务端产生的 hash，绝不携带证据/姓名。 */
data class RecoveryApplyRequest(
    val docId: String? = null,
    val snapshotHash: String? = null,
    val proposalHash: String? = null
)

/**
 * 02（I-3）：登录保护的旧名单回源维护 API。actor 只取会话（[AuthSessionKeys.USERNAME]），
 * 请求体伪造的 actor/作者名/proof/URL 一律被忽略；每次只处理一个 `docId`，不发送任何邮件。
 *
 * 状态码：未登录 401；缺 `docId`/hash 400；预览或 apply 依赖的外部额度延期 503；
 * apply 的两项 hash 与服务端重算不一致 409；其余 200。
 */
@RestController
@RequestMapping("/api/expert-discovery/identity-recovery")
class DiscoveryIdentityRecoveryController(private val service: DiscoveryIdentityRecoveryService) {

    @PostMapping("/preview")
    fun preview(request: HttpServletRequest, @RequestBody body: RecoveryPreviewRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        val docId = body.docId?.trim().orEmpty()
        if (docId.isEmpty()) return badRequest("docId 必填")
        val result = service.preview(docId)
        return if (result.status == RecoveryStatus.DEFERRED) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result)
        } else {
            ResponseEntity.ok(result)
        }
    }

    @PostMapping("/apply")
    fun apply(request: HttpServletRequest, @RequestBody body: RecoveryApplyRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        val docId = body.docId?.trim().orEmpty()
        if (docId.isEmpty()) return badRequest("docId 必填")
        if (body.snapshotHash.isNullOrBlank() || body.proposalHash.isNullOrBlank()) {
            return badRequest("snapshotHash 与 proposalHash 必填")
        }
        val result = service.apply(docId, body.snapshotHash, body.proposalHash)
        return when (result.status) {
            RecoveryApplyStatus.STALE -> ResponseEntity.status(HttpStatus.CONFLICT).body(result)
            RecoveryApplyStatus.DEFERRED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result)
            else -> ResponseEntity.ok(result)
        }
    }

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse("UNAUTHORIZED", "未登录", null))

    private fun badRequest(message: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse("INVALID_REQUEST", message, null))
}
