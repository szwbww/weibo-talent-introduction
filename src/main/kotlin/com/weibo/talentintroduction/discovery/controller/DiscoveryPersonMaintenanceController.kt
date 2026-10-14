package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonMaintenanceService
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyGroup
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyOutcome
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyRequest
import com.weibo.talentintroduction.discovery.service.MaintenancePreviewOutcome
import com.weibo.talentintroduction.discovery.service.MaintenancePreviewRequest
import com.weibo.talentintroduction.discovery.service.MaintenanceReasonCodes
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackOutcome
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/** 10：预览请求 —— 冻结 docId 全集 + 候选组标识；客户端不得上传「可信作者结论」。 */
data class MaintenancePreviewHttpRequest(
    val ruleVersion: Int? = null,
    val frozenDocIds: List<String> = emptyList(),
    val candidateGroups: List<List<String>> = emptyList()
)

/** 10：apply 单组 —— 只带成员 ID、expected 指纹与预览时的关系指纹。 */
data class MaintenanceApplyHttpGroup(
    val memberDocIds: List<String> = emptyList(),
    val expectedFingerprint: String? = null,
    val expectedRelationFingerprint: String? = null
)

/** 10：apply 请求 —— 冻结 ID 全集、规则版本、预览摘要与逐组 expected 指纹。 */
data class MaintenanceApplyHttpRequest(
    val ruleVersion: Int? = null,
    val previewDigest: String? = null,
    val frozenDocIds: List<String> = emptyList(),
    val groups: List<MaintenanceApplyHttpGroup> = emptyList()
)

/** 10：回滚预览请求（只提案，不执行）。 */
data class MaintenanceRollbackHttpRequest(
    val ruleVersion: Int? = null,
    val groups: List<List<String>> = emptyList()
)

/**
 * 10（I-1/I-2/I-3）：新发现历史人物**只读复核 + 受限应用 + 回滚提案**维护 API。
 *
 * 沿用现有管理入口访问控制：要求登录态，actor 只取会话（[AuthSessionKeys.USERNAME]），
 * 绝不接受请求体伪造 actor；本控制器不发送任何邮件、不做生产数据修改（apply 只走 04/09 既有写入口）。
 *
 * 状态码：未登录 401；参数/规则版本非法 400；apply 的预览摘要与服务端重算不一致 409；其余 200。
 */
@RestController
@RequestMapping("/api/expert-discovery/person-maintenance")
class DiscoveryPersonMaintenanceController(private val service: DiscoveryPersonMaintenanceService) {

    @PostMapping("/preview")
    fun preview(request: HttpServletRequest, @RequestBody body: MaintenancePreviewHttpRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return when (val outcome = service.preview(
            MaintenancePreviewRequest(
                ruleVersion = body.ruleVersion,
                frozenDocIds = body.frozenDocIds,
                candidateGroups = body.candidateGroups
            )
        )) {
            is MaintenancePreviewOutcome.Ok -> ResponseEntity.ok(outcome.preview)
            is MaintenancePreviewOutcome.Rejected -> badRequest(outcome.reason)
        }
    }

    @PostMapping("/apply")
    fun apply(request: HttpServletRequest, @RequestBody body: MaintenanceApplyHttpRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        val groups = body.groups.map {
            MaintenanceApplyGroup(
                it.memberDocIds,
                it.expectedFingerprint.orEmpty(),
                it.expectedRelationFingerprint
            )
        }
        return when (val outcome = service.apply(
            MaintenanceApplyRequest(
                ruleVersion = body.ruleVersion,
                previewDigest = body.previewDigest,
                frozenDocIds = body.frozenDocIds,
                groups = groups
            )
        )) {
            is MaintenanceApplyOutcome.Ok -> ResponseEntity.ok(outcome.results)
            is MaintenanceApplyOutcome.Rejected ->
                if (outcome.reason == MaintenanceReasonCodes.PREVIEW_DIGEST_MISMATCH) {
                    ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse(outcome.reason, "预览摘要不一致", null))
                } else {
                    badRequest(outcome.reason)
                }
        }
    }

    @PostMapping("/rollback-preview")
    fun rollbackPreview(request: HttpServletRequest, @RequestBody body: MaintenanceRollbackHttpRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return when (val outcome = service.rollbackPreview(
            MaintenanceRollbackRequest(ruleVersion = body.ruleVersion, groups = body.groups)
        )) {
            is MaintenanceRollbackOutcome.Ok -> ResponseEntity.ok(outcome.proposals)
            is MaintenanceRollbackOutcome.Rejected -> badRequest(outcome.reason)
        }
    }

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse("UNAUTHORIZED", "未登录", null))

    private fun badRequest(message: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse("INVALID_REQUEST", message, null))
}
