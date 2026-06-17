package com.weibo.talentintroduction.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.service.AuthService
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
class AuthInterceptor(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper
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
