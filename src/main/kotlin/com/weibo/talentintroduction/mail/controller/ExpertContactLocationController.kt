package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.service.ContactCountryTimezoneCatalog
import com.weibo.talentintroduction.mail.service.ContactLocationView
import com.weibo.talentintroduction.mail.service.ExpertContactLocationCatalog
import com.weibo.talentintroduction.mail.service.ExpertContactLocationService
import com.weibo.talentintroduction.mail.service.SaveContactLocationRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * 人工所在地配置接口（plan 01 / c1；三个路由冻结给 c2/c3 逐字复用）。
 *
 * - `GET /countries`：只读目录（随包快照）；
 * - `GET /{contactId}`：未配置返回 200 `configured:false`，不创建占位行；
 * - `PUT /{contactId}`：body `{countryCode, zoneId:null|string}`，返回已持久化配置。
 *
 * 身份边界（I-4）：PUT 显式要求会话 username（`AuthSessionKeys.USERNAME`），
 * 绝不从请求体接收 username；`/api/` 下另有 AuthInterceptor 兜底 401。
 * 参数校验与 404 由 service 抛出、经 GlobalExceptionHandler 映射 400/404。
 */
@RestController
@RequestMapping("/api/mail/contact-locations")
class ExpertContactLocationController(
    private val service: ExpertContactLocationService,
    private val catalog: ExpertContactLocationCatalog
) {

    @GetMapping("/countries")
    fun countries(): ContactCountryTimezoneCatalog = catalog.all()

    @GetMapping("/{contactId}")
    fun get(@PathVariable contactId: Long): ContactLocationView = service.get(contactId)

    @PutMapping("/{contactId}")
    fun save(
        request: HttpServletRequest,
        @PathVariable contactId: Long,
        @RequestBody body: SaveContactLocationRequest
    ): ResponseEntity<Any> {
        val username = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.save(username, contactId, body))
    }

    private fun sessionUsername(request: HttpServletRequest): String? =
        request.getSession(false)
            ?.getAttribute(AuthSessionKeys.USERNAME) as? String

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
