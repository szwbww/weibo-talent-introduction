package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.service.ExpertContactNoteService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

data class SaveExpertContactNoteRequest(val note: String)

@RestController
@RequestMapping("/api/mail/contact-notes")
class ExpertContactNoteController(private val service: ExpertContactNoteService) {
    @GetMapping("/{contactId}")
    fun get(request: HttpServletRequest, @PathVariable contactId: Long): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.get(contactId))
    }

    @PutMapping("/{contactId}")
    fun save(
        request: HttpServletRequest,
        @PathVariable contactId: Long,
        @RequestBody body: SaveExpertContactNoteRequest
    ): ResponseEntity<Any> {
        val username = sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.save(username, contactId, body.note))
    }

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)
            ?.takeIf { it.isNotBlank() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
