package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.repository.ExpertInboundNotificationSettingsResponse
import com.weibo.talentintroduction.mail.service.ExpertInboundNotificationNotConfiguredException
import com.weibo.talentintroduction.mail.service.ExpertInboundNotificationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/expert-inbound-notifications/settings")
class ExpertInboundNotificationController(private val service: ExpertInboundNotificationService) {
    @GetMapping
    fun get(request: HttpServletRequest): ResponseEntity<ExpertInboundNotificationSettingsResponse> {
        username(request)
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.settings())
    }

    @PutMapping
    fun put(@RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<ExpertInboundNotificationSettingsResponse> {
        val username = username(request)
        require(body.isObject && body.get("enabled")?.isBoolean == true) { "enabled 必须是 JSON boolean" }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.setEnabled(body.get("enabled").booleanValue(), username))
    }

    private fun username(request: HttpServletRequest): String =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.takeIf { it.isNotBlank() }
            ?: throw UnauthenticatedNotificationRequest()

    // Controller-local mappings also protect infrastructure diagnostics from exposing configuration.
    @ExceptionHandler(ExpertInboundNotificationNotConfiguredException::class)
    fun notConfigured(): ResponseEntity<ApiErrorResponse> = error(HttpStatus.CONFLICT, "NOT_CONFIGURED", "未配置企业微信机器人")

    @ExceptionHandler(UnauthenticatedNotificationRequest::class)
    fun unauthenticated(): ResponseEntity<ApiErrorResponse> = error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录")

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    fun invalid(): ResponseEntity<ApiErrorResponse> = error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "enabled 必须是 JSON boolean")

    @ExceptionHandler(Exception::class)
    fun unavailable(): ResponseEntity<ApiErrorResponse> = error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "群消息设置暂不可用")

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(ApiErrorResponse(code, message, status.reasonPhrase))
}

class UnauthenticatedNotificationRequest : IllegalStateException("未登录")
