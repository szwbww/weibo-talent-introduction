package com.weibo.talentintroduction.document.controller

import com.weibo.talentintroduction.document.service.ExpertDocumentBrowseService
import com.weibo.talentintroduction.document.service.ExpertDocumentFile
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
@RequestMapping("/api/expert-contacts/{contactId}")
class ExpertDocumentBrowseController(
    private val service: ExpertDocumentBrowseService
) {
    @GetMapping("/documents")
    fun listDocuments(@PathVariable contactId: Long): List<ExpertDocumentFile> =
        service.listDocuments(contactId)

    @GetMapping("/attachments/{attachmentId}/download")
    fun download(
        @PathVariable contactId: Long,
        @PathVariable attachmentId: Long
    ): ResponseEntity<Resource> {
        val resource = service.resolveForDownload(contactId, attachmentId)
        val fileResource = FileSystemResource(resource.path.toFile())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resource.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.builder("attachment")
                    .filename(resource.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString())
            .contentLength(resource.fileSize)
            .body(fileResource)
    }

    @GetMapping("/attachments/{attachmentId}/preview")
    fun preview(
        @PathVariable contactId: Long,
        @PathVariable attachmentId: Long
    ): ResponseEntity<Resource> {
        val resource = service.resolveForPreview(contactId, attachmentId)
        val fileResource = FileSystemResource(resource.path.toFile())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resource.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.builder("inline")
                    .filename(resource.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString())
            .contentLength(resource.fileSize)
            .body(fileResource)
    }
}
