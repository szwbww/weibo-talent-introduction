package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.ReplyIdentityInspectRequest
import com.weibo.talentintroduction.mail.service.ReplyIdentityDefaultsPreviewRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** AuthInterceptor protects both API endpoints. Neither endpoint mutates a draft or sends mail. */
@RestController
@RequestMapping("/api/mail/reply-identity")
class ReplyIdentityController(private val operations: PendingMailOperationService) {
    @PostMapping("/inspect")
    fun inspect(@RequestBody request: ReplyIdentityInspectRequest) = operations.inspectReplyIdentity(request.also {
        it.contextRequest().validate()
        require(it.textBody.length <= 100000 && it.htmlBody.length <= 100000) { "Body exceeds 100000 characters" }
    })

    @PostMapping("/defaults-preview")
    fun defaultsPreview(@RequestBody request: ReplyIdentityDefaultsPreviewRequest) = operations.previewReplyIdentityDefaults(request.also {
        it.contextRequest().validate()
        require(it.salutation.content.length <= 10000 && it.signature.content.length <= 10000) { "Content exceeds 10000 characters" }
    })
}
