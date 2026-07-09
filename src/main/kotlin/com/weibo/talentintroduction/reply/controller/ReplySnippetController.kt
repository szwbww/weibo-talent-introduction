package com.weibo.talentintroduction.reply.controller

import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.service.ReplySnippetCreateCommand
import com.weibo.talentintroduction.reply.service.ReplySnippetDetail
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ReplySnippetUpdateCommand
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reply-snippets")
class ReplySnippetController(
    private val service: ReplySnippetService
) {
    @GetMapping
    fun list(@RequestParam(required = false) snippetType: String?): List<ReplySnippetResponse> =
        if (snippetType.isNullOrBlank()) {
            service.listAll()
        } else {
            service.listByType(snippetType)
        }.map { it.toResponse() }

    @PostMapping
    fun create(@RequestBody request: ReplySnippetCreateRequest): ReplySnippetResponse =
        service.create(request.toCommand()).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: ReplySnippetUpdateRequest
    ): ReplySnippetResponse =
        service.update(id, request.toCommand()).toResponse()

    @PostMapping("/{id}/enable")
    fun enable(@PathVariable id: Long): ReplySnippetResponse =
        service.setEnabled(id, true).toResponse()

    @PostMapping("/{id}/disable")
    fun disable(@PathVariable id: Long): ReplySnippetResponse =
        service.setEnabled(id, false).toResponse()

    @PostMapping("/{id}/default")
    fun setDefault(@PathVariable id: Long): ReplySnippetResponse =
        service.setDefault(id).toResponse()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }
}

data class ReplySnippetCreateRequest(
    val snippetType: String,
    val content: String,
    val displayOrder: Int = 100,
    val variantGroup: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val variants: List<String> = emptyList()
) {
    fun toCommand(): ReplySnippetCreateCommand =
        ReplySnippetCreateCommand(
            snippetType = snippetType,
            content = content,
            displayOrder = displayOrder,
            variantGroup = variantGroup,
            isDefault = isDefault,
            enabled = enabled,
            variants = variants
        )
}

data class ReplySnippetUpdateRequest(
    val content: String,
    val displayOrder: Int,
    val variantGroup: String? = null,
    val isDefault: Boolean,
    val enabled: Boolean,
    val variants: List<String> = emptyList()
) {
    fun toCommand(): ReplySnippetUpdateCommand =
        ReplySnippetUpdateCommand(
            content = content,
            displayOrder = displayOrder,
            variantGroup = variantGroup,
            isDefault = isDefault,
            enabled = enabled,
            variants = variants
        )
}

data class ReplySnippetResponse(
    val id: Long?,
    val snippetType: String,
    val content: String,
    val displayOrder: Int,
    val variantGroup: String?,
    val isDefault: Boolean,
    val enabled: Boolean,
    val variants: List<String> = emptyList()
)

private fun ReplySnippetDetail.toResponse(): ReplySnippetResponse =
    snippet.toResponse(variants)

private fun ReplySnippet.toResponse(variants: List<String> = emptyList()): ReplySnippetResponse =
    ReplySnippetResponse(
        id = id,
        snippetType = snippetType,
        content = content,
        displayOrder = displayOrder,
        variantGroup = variantGroup,
        isDefault = isDefault,
        enabled = enabled,
        variants = variants
    )
