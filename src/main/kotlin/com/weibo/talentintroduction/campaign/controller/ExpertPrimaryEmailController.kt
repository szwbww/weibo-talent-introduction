package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.campaign.service.ExpertPrimaryEmailService
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

data class ChangePrimaryEmailRequest(val email: String, val expectedEmail: String)

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertPrimaryEmailController(private val service: ExpertPrimaryEmailService) {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException::class)
    fun statusError(e: org.springframework.web.server.ResponseStatusException) =
        org.springframework.http.ResponseEntity.status(e.status).body(
            com.weibo.talentintroduction.common.controller.ApiErrorResponse("EXPERT_MAIL_CONFLICT", e.reason ?: "操作失败", null))

    @PutMapping("/{contactId}/primary-email")
    fun change(request: HttpServletRequest, @PathVariable contactId: Long, @RequestBody body: ChangePrimaryEmailRequest): Map<String, String> {
        val actor = request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String
        MailReadOnlyPolicy.requireAdmin(actor)
        return mapOf("email" to service.change(contactId, body.email, body.expectedEmail, requireNotNull(actor)))
    }
}
