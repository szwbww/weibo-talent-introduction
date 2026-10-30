package com.weibo.talentintroduction.auth.config

import com.weibo.talentintroduction.auth.controller.AuthController
import com.weibo.talentintroduction.expert.controller.ExpertIndexController
import com.weibo.talentintroduction.expert.controller.ExpertManualProfileController
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import com.weibo.talentintroduction.mail.controller.MailReviewDraftController
import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
import com.weibo.talentintroduction.campaign.controller.MeetingCalendarController
import com.weibo.talentintroduction.mail.controller.ExpertContactLocationController
import com.weibo.talentintroduction.mail.controller.InboundMailSummaryController
import com.weibo.talentintroduction.mail.controller.MeetingConfirmationController
import com.weibo.talentintroduction.mail.controller.TranslationController
import com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
import org.springframework.web.method.HandlerMethod
import java.util.Locale

/**
 * 固定受限账号策略：身份只有精确 admin 与 user1；user1 仍使用邮箱受限宿主。
 * 会话读取/审核稿沿各自服务的实时授权；单专家写与文件操作同时要求精确
 * handler/verb 和 ExpertMailPermissionGuard 的真实资源 FULL 校验。
 * 未完成 DTO/owner 守卫的后续阶段入口保持默认拒绝。
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
        AllowedHandler("POST", MailReviewDraftController::class.java, "create"),
        AllowedHandler("GET", MailReviewDraftController::class.java, "list"),
        AllowedHandler("GET", MailReviewDraftController::class.java, "detail"),
        AllowedHandler("PUT", MailReviewDraftController::class.java, "save"),
        AllowedHandler("POST", MailReviewDraftController::class.java, "submit"),
        AllowedHandler("POST", MailReviewDraftController::class.java, "withdraw"),
        AllowedHandler("DELETE", MailReviewDraftController::class.java, "delete"),
        AllowedHandler("POST", MailReviewDraftController::class.java, "comment"),
        AllowedHandler("GET", MailReviewDraftController::class.java, "events"),
        AllowedHandler("GET", ExpertContactLocationController::class.java, "countries"),
        AllowedHandler("GET", InboundMailSummaryController::class.java, "tagOptions"),
        AllowedHandler("POST", InboundMailSummaryController::class.java, "addTag"),
        AllowedHandler("POST", InboundMailSummaryController::class.java, "autoApplyTags"),
        AllowedHandler("DELETE", InboundMailSummaryController::class.java, "deleteTag"),
        AllowedHandler("POST", ExpertIndexController::class.java, "addTag"),
        AllowedHandler("POST", ExpertIndexController::class.java, "removeTag"),
        AllowedHandler("GET", ExpertIndexController::class.java, "getExpertProfile"),
        AllowedHandler("GET", ExpertManualProfileController::class.java, "get"),
        AllowedHandler("PATCH", ExpertManualProfileController::class.java, "patch"),
        AllowedHandler("POST", MeetingCalendarController::class.java, "create"),
        AllowedHandler("GET", MailReplyDraftController::class.java, "target"),
        AllowedHandler("PUT", MailReplyDraftController::class.java, "save"),
        AllowedHandler("GET", MailReplyDraftController::class.java, "detail"),
        AllowedHandler("DELETE", MailReplyDraftController::class.java, "discard"),
        AllowedHandler("GET", MailReplyDraftController::class.java, "list"),
        AllowedHandler("GET", MailReplyDraftController::class.java, "summaries"),
        AllowedHandler("GET", MeetingConfirmationController::class.java, "timeZones"),
        AllowedHandler("POST", TranslationController::class.java, "translate"),
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
        return ExpertMailPermissionGuard.isResourceHandler(handler, verb) || READER_ALLOWED_HANDLERS.any { allowed ->
            allowed.httpMethod == verb &&
                allowed.declaringClass == method.declaringClass &&
                allowed.methodName == method.name
        }
    }
}
