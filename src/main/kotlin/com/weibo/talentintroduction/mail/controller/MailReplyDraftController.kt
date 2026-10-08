package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.mail.service.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/mail/mailbox/drafts")
class MailReplyDraftController(private val service: MailReplyDraftService) {
    @GetMapping("/target")
    fun target(request: HttpServletRequest, @RequestParam contactId: Long, @RequestParam kind: MailReplyDraftKind,
        @RequestParam(defaultValue="0") processingId: Long, @RequestParam(defaultValue="") accountScope: String): ResponseEntity<Any> =
        respond(request) { service.getTarget(it,MailReplyDraftTarget(contactId,kind,processingId,accountScope)) }
    @PutMapping("/target")
    fun save(request: HttpServletRequest, @RequestParam contactId: Long, @RequestParam kind: MailReplyDraftKind,
        @RequestParam(defaultValue="0") processingId: Long, @RequestParam(defaultValue="") accountScope: String,
        @RequestBody body: JsonNode): ResponseEntity<Any> =
        respond(request) { service.save(it,MailReplyDraftTarget(contactId,kind,processingId,accountScope),MailReplyDraftContextCodec.parseSaveRequest(body)) }
    @GetMapping("/{id}")
    fun detail(request: HttpServletRequest, @PathVariable id: Long): ResponseEntity<Any> = respond(request) { service.get(it,id) }
    @DeleteMapping("/{id}")
    fun discard(request: HttpServletRequest, @PathVariable id: Long, @RequestParam expectedVersion: Long): ResponseEntity<Any> = respond(request) { service.discard(it,id,expectedVersion) }
    @GetMapping
    fun list(request: HttpServletRequest, @RequestParam(required=false) search: String?, @RequestParam(required=false) accountScope: String?,
        @RequestParam(defaultValue="0") page: Int, @RequestParam(defaultValue="20") size: Int): ResponseEntity<Any> = respond(request) { service.list(it,search,accountScope,page,size) }
    @GetMapping("/summaries")
    fun summaries(request: HttpServletRequest, @RequestParam(required=false) contactIds: List<Long>?, @RequestParam(required=false) accountScope: String?): ResponseEntity<Any> = respond(request) { service.summaries(it,contactIds.orEmpty(),accountScope) }

    /** api() consumes raw JSON success objects and error.data, not a success envelope. */
    private fun respond(request: HttpServletRequest, action: (String) -> Any): ResponseEntity<Any> {
        val owner = request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String
        if (owner.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("code" to "UNAUTHORIZED","message" to "请先登录"))
        return try { ResponseEntity.ok(action(owner)) } catch (e: MailReplyDraftException) {
            ResponseEntity.status(e.status).body(mapOf("code" to e.code,"message" to e.message,"currentVersion" to e.currentVersion,"currentState" to e.currentState))
        }
    }
}
