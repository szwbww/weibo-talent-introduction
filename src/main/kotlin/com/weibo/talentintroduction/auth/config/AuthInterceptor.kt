package com.weibo.talentintroduction.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
class AuthInterceptor(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
    private val expertMailPermissionGuard: ExpertMailPermissionGuard
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) {
            return true
        }

        val session = request.getSession(false)
        val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String

        if (username == null) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
            return false
        }

        val user = authService.findUser(username)
        if (user == null) {
            session.invalidate()
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
            return false
        }

        if (user.mustChangePassword) {
            val uri = request.requestURI
            val contextPath = request.contextPath
            val relativeUri = if (uri.startsWith(contextPath)) uri.substring(contextPath.length) else uri

            if (relativeUri != "/api/auth/change-password" &&
                relativeUri != "/api/auth/logout" &&
                relativeUri != "/api/auth/me"
            ) {
                writeErrorResponse(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "PASSWORD_CHANGE_REQUIRED",
                    "首次登录请先修改密码"
                )
                return false
            }
        }

        // I-1/I-4：精确 user1 的已认证请求默认拒绝，仅放行白名单 handler
        // （HandlerMethod 类名+方法名+HTTP method 精确匹配，非 URL 前缀）。
        if (MailReadOnlyPolicy.isReader(username) &&
            !MailReadOnlyPolicy.isReaderAllowed(handler, request.method)
        ) {
            writeErrorResponse(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                MailReadOnlyPolicy.READ_ONLY_FORBIDDEN,
                "只读账号无权访问该资源"
            )
            return false
        }
        if (MailReadOnlyPolicy.isReader(username) &&
            ExpertMailPermissionGuard.isResourceHandler(handler, request.method)
        ) {
            expertMailPermissionGuard.authorize(username, handler, request)
        }

        return true
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        statusCode: Int,
        code: String,
        message: String
    ) {
        response.status = statusCode
        response.contentType = "application/json;charset=UTF-8"
        val errorMap = mapOf(
            "code" to code,
            "message" to message,
            "detail" to null
        )
        objectMapper.writeValue(response.writer, errorMap)
    }
}
