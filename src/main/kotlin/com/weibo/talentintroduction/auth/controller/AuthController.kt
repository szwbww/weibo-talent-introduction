package com.weibo.talentintroduction.auth.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest
    ): LoginResponse {
        val username = request.username?.trim()
        val password = request.password

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            throw IllegalArgumentException("用户名或密码错误")
        }
        if (username != "admin") {
            throw IllegalArgumentException("用户名或密码错误")
        }

        val adminUser = authService.login(username, password)

        val session = servletRequest.getSession(true)
        servletRequest.changeSessionId()
        session.setAttribute(AuthSessionKeys.USERNAME, adminUser.username)

        return LoginResponse(
            username = adminUser.username,
            mustChangePassword = adminUser.mustChangePassword
        )
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
    }

    @GetMapping("/me")
    fun me(servletRequest: HttpServletRequest): MeResponse {
        val session = servletRequest.getSession(false)
        val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String
        if (username == null) {
            return MeResponse(authenticated = false, username = null, mustChangePassword = false)
        }

        val user = authService.findUser(username)
        if (user == null) {
            session.invalidate()
            return MeResponse(authenticated = false, username = null, mustChangePassword = false)
        }

        return MeResponse(
            authenticated = true,
            username = user.username,
            mustChangePassword = user.mustChangePassword
        )
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        servletRequest: HttpServletRequest
    ) {
        val session = servletRequest.getSession(false)
        val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String
            ?: throw IllegalArgumentException("未登录")

        val oldPassword = request.oldPassword
        val newPassword = request.newPassword

        if (oldPassword.isNullOrBlank() || newPassword.isNullOrBlank()) {
            throw IllegalArgumentException("输入参数不能为空")
        }

        authService.changePassword(username, oldPassword, newPassword)
    }
}

data class LoginRequest(
    val username: String?,
    val password: String?
)

data class LoginResponse(
    val username: String,
    val mustChangePassword: Boolean
)

data class MeResponse(
    val authenticated: Boolean,
    val username: String?,
    val mustChangePassword: Boolean
)

data class ChangePasswordRequest(
    val oldPassword: String?,
    val newPassword: String?
)
