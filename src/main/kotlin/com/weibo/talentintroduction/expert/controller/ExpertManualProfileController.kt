package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileGroup
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfilePatch
import com.weibo.talentintroduction.expert.domain.ExpertProfileConflictException
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.domain.ExpertProfileStoreException
import com.weibo.talentintroduction.expert.service.ExpertManualProfileService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import javax.servlet.http.HttpServletRequest

/**
 * 01：人工档案读写 API。全部端点要求现有登录会话（`AuthSessionKeys.USERNAME`），
 * 用户名与审计时间只取服务端，绝不接受客户端传入。
 *
 * - `GET /api/experts/manual-profile?expertId=...&level=RAW`：完整 DTO，空行返回默认值且不插入；
 * - `PATCH /api/experts/manual-profile`：`{expertId,level,group,expectedVersion,values}`，成功 200 返回完整最新 DTO。
 *
 * 错误映射全部在本 controller 的局部 `@ExceptionHandler`（不动全局处理器），响应固定
 * `{code,message,detail}`：400 INVALID_REQUEST、404 EXPERT_NOT_FOUND、409 PROFILE_CONFLICT、
 * 503 EXPERT_LOOKUP_UNAVAILABLE、500 PROFILE_STORE_FAILED；未登录 401 UNAUTHORIZED。
 */
@RestController
@RequestMapping("/api/experts/manual-profile")
class ExpertManualProfileController(private val service: ExpertManualProfileService) {

    @GetMapping
    fun get(
        request: HttpServletRequest,
        @RequestParam(required = false) expertId: String?,
        @RequestParam(required = false) level: ExpertIndexLevel?
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.get(expertId, level ?: ExpertIndexLevel.RAW))
    }

    @PatchMapping
    fun patch(
        request: HttpServletRequest,
        @RequestBody(required = false) body: ExpertManualProfilePatchRequest?
    ): ResponseEntity<Any> {
        val username = sessionUsername(request) ?: return unauthorized()
        val payload = body ?: throw ExpertManualProfileInvalidRequestException("请求体无效")
        return ResponseEntity.ok(service.patch(payload.toCommand(), username))
    }

    @ExceptionHandler(ExpertManualProfileInvalidRequestException::class)
    fun handleInvalidRequest(ex: ExpertManualProfileInvalidRequestException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, ex.message)

    /** 请求体/查询参数在进入业务前就无法解析：固定消息，不回显原始输入。 */
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        HttpMediaTypeNotSupportedException::class
    )
    fun handleUnreadableRequest(): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, INVALID_REQUEST_CODE, "请求体或参数无效")

    @ExceptionHandler(ExpertProfileNotFoundException::class)
    fun handleNotFound(ex: ExpertProfileNotFoundException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, "EXPERT_NOT_FOUND", ex.message)

    @ExceptionHandler(ExpertProfileConflictException::class)
    fun handleConflict(ex: ExpertProfileConflictException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "PROFILE_CONFLICT", ex.message)

    @ExceptionHandler(ExpertProfileLookupUnavailableException::class)
    fun handleLookupUnavailable(ex: ExpertProfileLookupUnavailableException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_LOOKUP_UNAVAILABLE", ex.message)

    @ExceptionHandler(ExpertProfileStoreException::class)
    fun handleStoreFailure(ex: ExpertProfileStoreException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_STORE_FAILED", ex.message)

    /** 未预期异常：固定消息，绝不把内部 SQL/路径或字段值透给客户端。 */
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedFailure(): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_STORE_FAILED", "人工档案处理失败，请稍后重试")

    private fun error(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(ApiErrorResponse(code, message ?: status.reasonPhrase, status.reasonPhrase))

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))

    private companion object {
        const val INVALID_REQUEST_CODE = "INVALID_REQUEST"
    }
}

/** PATCH 请求体；`group`/`level` 以字符串进入，由 controller 解析为 01 枚举（未知值 400）。 */
data class ExpertManualProfilePatchRequest(
    val expertId: String? = null,
    val level: String? = null,
    val group: String? = null,
    val expectedVersion: Long? = null,
    val values: Map<String, Any?>? = null
) {
    fun toCommand(): ExpertManualProfilePatch {
        val parsedGroup = ExpertManualProfileGroup.fromWire(group)
            ?: throw ExpertManualProfileInvalidRequestException(
                "group 只接受 ${ExpertManualProfileGroup.values().joinToString("/") { it.wire }}"
            )
        val expected = expectedVersion
            ?: throw ExpertManualProfileInvalidRequestException("缺少 expectedVersion")
        return ExpertManualProfilePatch(
            expertId = expertId,
            level = parseLevel(level),
            group = parsedGroup,
            expectedVersion = expected,
            values = values
        )
    }

    private fun parseLevel(raw: String?): ExpertIndexLevel {
        if (raw == null) return ExpertIndexLevel.RAW
        return ExpertIndexLevel.values().firstOrNull { it.name == raw.trim() }
            ?: throw ExpertManualProfileInvalidRequestException(
                "level 只接受 ${ExpertIndexLevel.values().joinToString("/") { it.name }}"
            )
    }
}
