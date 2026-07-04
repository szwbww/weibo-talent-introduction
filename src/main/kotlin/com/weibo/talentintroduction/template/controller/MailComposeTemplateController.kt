package com.weibo.talentintroduction.template.controller

import com.weibo.talentintroduction.template.service.ComposeTemplatePreviewResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateBlockCommand
import com.weibo.talentintroduction.template.service.MailComposeTemplateCommand
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/compose-templates")
class MailComposeTemplateController(
    private val service: MailComposeTemplateService
) {
    @GetMapping
    fun list(): List<MailComposeTemplateDetail> = service.listAll()

    @PostMapping
    fun create(@RequestBody request: MailComposeTemplateRequest): MailComposeTemplateDetail =
        service.create(request.toCommand())

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: MailComposeTemplateRequest
    ): MailComposeTemplateDetail =
        service.update(id, request.toCommand())

    @PostMapping("/{id}/enable")
    fun enable(@PathVariable id: Long): MailComposeTemplateDetail =
        service.setEnabled(id, true)

    @PostMapping("/{id}/disable")
    fun disable(@PathVariable id: Long): MailComposeTemplateDetail =
        service.setEnabled(id, false)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    @GetMapping("/{id}/preview")
    fun preview(@PathVariable id: Long): ComposeTemplatePreviewResult =
        service.preview(id)
}

data class MailComposeTemplateRequest(
    val templateName: String,
    val subject: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val blocks: List<MailComposeTemplateBlockRequest> = emptyList()
) {
    fun toCommand(): MailComposeTemplateCommand =
        MailComposeTemplateCommand(
            templateName = templateName,
            subject = subject,
            description = description,
            enabled = enabled,
            blocks = blocks.map { it.toCommand() }
        )
}

data class MailComposeTemplateBlockRequest(
    val blockOrder: Int,
    val blockType: String,
    val refId: Long? = null,
    val customText: String? = null
) {
    fun toCommand(): MailComposeTemplateBlockCommand =
        MailComposeTemplateBlockCommand(
            blockOrder = blockOrder,
            blockType = blockType,
            refId = refId,
            customText = customText
        )
}
