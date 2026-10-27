package com.weibo.talentintroduction.auth.config

import com.weibo.talentintroduction.auth.controller.AuthController
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import org.springframework.web.method.HandlerMethod
import java.util.Locale

/**
 * 固定只读账号策略（I-1/I-4）。
 *
 * 本步不建设通用角色/权限系统：身份只有精确 `admin` 与精确 `user1` 两种语义，
 * 绝不从 query/body/前端隐藏推导。`user1` 的已认证请求默认拒绝，仅放行：
 * - `GET` [MailboxConversationController.list]（会话列表）；
 * - `GET` [MailboxConversationController.messages]（会话时间线）；
 * - `POST` [AuthController.logout]。
 *
 * 白名单按 `HandlerMethod.method.declaringClass + method.name + HTTP method` 精确匹配，
 * 绝不使用 URL 前缀放行（否则新映射会自动继承权限）。登录/`me` 仍由
 * [AuthWebConfig] 的排除规则处理；`OPTIONS` 沿用原拦截器行为直接放行。
 */
class MailReadAccessDeniedException(
    val code: String,
    override val message: String
) : RuntimeException(message)

object MailReadOnlyPolicy {

    /** 内置只读账号：只做精确匹配，不做大小写或前缀匹配。 */
    const val READER_USERNAME = "user1"

    /** 唯一管理员账号：授权管理接口只接受该身份。 */
    const val ADMIN_USERNAME = "admin"

    /** reader 访问非白名单业务接口的默认拒绝码。 */
    const val READ_ONLY_FORBIDDEN = "READ_ONLY_FORBIDDEN"

    /** 邮件详情失权码（02 的详情准入使用；本步只固定常量与 403 映射）。 */
    const val MAIL_READ_FORBIDDEN = "MAIL_READ_FORBIDDEN"

    /** 授权管理接口非 admin 的拒绝码。 */
    const val ADMIN_REQUIRED = "ADMIN_REQUIRED"

    private data class AllowedHandler(
        val httpMethod: String,
        val declaringClass: Class<*>,
        val methodName: String
    )

    private val READER_ALLOWED_HANDLERS = listOf(
        AllowedHandler("GET", MailboxConversationController::class.java, "list"),
        AllowedHandler("GET", MailboxConversationController::class.java, "messages"),
        AllowedHandler("POST", AuthController::class.java, "logout")
    )

    fun isReader(username: String?): Boolean = username == READER_USERNAME

    fun isAdmin(username: String?): Boolean = username == ADMIN_USERNAME

    /**
     * 授权管理接口的唯一身份闸门：非精确 admin（含匿名/reader/其他测试身份）一律
     * 抛出 [ADMIN_REQUIRED] 的权限异常，由 [com.weibo.talentintroduction.common.controller.GlobalExceptionHandler]
     * 固定映射为 403，不落 500。
     */
    fun requireAdmin(actorUsername: String?) {
        if (!isAdmin(actorUsername)) {
            throw MailReadAccessDeniedException(ADMIN_REQUIRED, "仅管理员可管理邮件查看授权")
        }
    }

    /**
     * reader 是否可访问该 handler。非 `HandlerMethod`（如静态资源）默认拒绝；
     * `OPTIONS` 保持原拦截器的直接放行语义。
     */
    fun isReaderAllowed(handler: Any?, httpMethod: String?): Boolean {
        if ("OPTIONS".equals(httpMethod, ignoreCase = true)) {
            return true
        }
        val handlerMethod = handler as? HandlerMethod ?: return false
        val verb = httpMethod?.uppercase(Locale.ROOT) ?: return false
        val method = handlerMethod.method
        return READER_ALLOWED_HANDLERS.any { allowed ->
            allowed.httpMethod == verb &&
                allowed.declaringClass == method.declaringClass &&
                allowed.methodName == method.name
        }
    }
}
