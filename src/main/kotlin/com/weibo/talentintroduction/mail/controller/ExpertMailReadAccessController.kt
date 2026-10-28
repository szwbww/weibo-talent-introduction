package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.mail.service.ExpertMailReadAccessService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * admin 管理内置 user1 的专家邮件查看授权（I-2/I-3）。
 *
 * 路径固定 `…/{contactId}/read-access/user1`：目标用户不可由请求指定，`grantedBy`
 * 与权限枚举也不从 body 接收。身份只取 Session `AUTH_USERNAME`，且显式要求精确
 * admin（非 admin → 403 `ADMIN_REQUIRED`）；服务层再校验一次，防止绕过 controller
 * 直接调用。所有端点只写 `expert_mail_read_access`，不改变关注/挂起/邮件状态/ES。
 */
@RestController
@RequestMapping("/api/mail/mailbox/conversations/{contactId}/read-access")
class ExpertMailReadAccessController(
    private val service: ExpertMailReadAccessService
) {

    @GetMapping("/user1")
    fun get(
        servletRequest: HttpServletRequest,
        @PathVariable contactId: Long
    ): ExpertMailReadAccessResponse {
        val actor = sessionUsername(servletRequest)
        MailReadOnlyPolicy.requireAdmin(actor)
        return response(contactId, service.getAccess(actor, contactId))
    }

    @PutMapping("/user1")
    fun grant(
        servletRequest: HttpServletRequest,
        @PathVariable contactId: Long
    ): ExpertMailReadAccessResponse {
        val actor = sessionUsername(servletRequest)
        MailReadOnlyPolicy.requireAdmin(actor)
        return response(contactId, service.grant(actor, contactId))
    }

    @DeleteMapping("/user1")
    fun revoke(
        servletRequest: HttpServletRequest,
        @PathVariable contactId: Long
    ): ExpertMailReadAccessResponse {
        val actor = sessionUsername(servletRequest)
        MailReadOnlyPolicy.requireAdmin(actor)
        return response(contactId, service.revoke(actor, contactId))
    }

    private fun response(contactId: Long, canRead: Boolean): ExpertMailReadAccessResponse =
        ExpertMailReadAccessResponse(
            username = MailReadOnlyPolicy.READER_USERNAME,
            contactId = contactId,
            canRead = canRead
        )

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)
            ?.takeIf { it.isNotBlank() }
}

data class ExpertMailReadAccessResponse(
    val username: String,
    val contactId: Long,
    val canRead: Boolean
)
