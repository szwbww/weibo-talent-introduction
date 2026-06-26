package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.AttachmentMetaResponse
import com.weibo.talentintroduction.mail.service.MailboxAttachmentService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/mail/mailbox")
class MailboxAttachmentController(
    private val mailboxAttachmentService: MailboxAttachmentService
) {
    @GetMapping("/{source}/{id}/attachments")
    fun listAttachments(
        @PathVariable source: String,
        @PathVariable id: Long
    ): List<AttachmentMetaResponse> = mailboxAttachmentService.listAttachments(source, id)

    @GetMapping("/attachments/{attachmentId}/download")
    fun download(@PathVariable attachmentId: Long): ResponseEntity<Resource> {
        val resource = mailboxAttachmentService.download(attachmentId)
        val fileResource = FileSystemResource(resource.path.toFile())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resource.contentType))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.builder("attachment")
                    .filename(resource.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .contentLength(resource.fileSize)
            .body(fileResource)
    }
}
