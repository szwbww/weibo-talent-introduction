package com.weibo.talentintroduction.reply.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReplySnippetService(
    private val repository: ReplySnippetRepository,
    private val mailVariableService: MailVariableService,
    private val contentVariantService: ContentVariantService
) {
    fun listAll(): List<ReplySnippetDetail> =
        repository.findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc().map { toDetail(it) }

    fun listByType(snippetType: String): List<ReplySnippetDetail> {
        validateSnippetType(snippetType)
        return repository.findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType.uppercase())
            .map { toDetail(it) }
    }

    fun resolveManualFrame(): ManualReplyFrame =
        ManualReplyFrame(
            salutation = resolveDefaultText(SnippetType.SALUTATION),
            greeting = resolveDefaultText(SnippetType.GREETING),
            closing = resolveDefaultText(SnippetType.CLOSING),
            ackOptions = repository
                .findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name)
                .mapNotNull { snippet ->
                    val id = snippet.id ?: return@mapNotNull null
                    AckOption(id = id, content = snippet.content)
                }
        )

    fun resolveAck(ackSnippetId: Long?): String? {
        if (ackSnippetId == null) {
            return null
        }
        val snippet = repository.findById(ackSnippetId).orElse(null) ?: return null
        if (!snippet.enabled || snippet.snippetType != SnippetType.ACK.name) {
            return null
        }
        return snippet.content.takeIf { it.isNotBlank() }
    }

    @Transactional
    fun create(command: ReplySnippetCreateCommand): ReplySnippetDetail {
        val snippetType = command.snippetType.uppercase()
        validateSnippetType(snippetType)
        require(command.content.isNotBlank()) { "content is required" }
        require(command.displayOrder > 0) { "displayOrder must be positive" }
        mailVariableService.requireValidPlaceholders(command.content)
        if (command.isDefault) {
            require(snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
            require(snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
        }
        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)

        val now = LocalDateTime.now()
        val saved = repository.save(
            ReplySnippet(
                snippetType = snippetType,
                content = command.content.trim(),
                displayOrder = command.displayOrder,
                variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() },
                isDefault = command.isDefault,
                enabled = command.enabled,
                createdAt = now,
                updatedAt = now
            )
        )
        if (saved.isDefault) {
            clearOtherDefaults(saved)
        }
        val snippetId = saved.id ?: error("Reply snippet id is required")
        contentVariantService.replaceForOwner(
            ContentVariantOwnerType.REPLY_SNIPPET,
            snippetId,
            saved.content,
            command.variants
        )
        return toDetail(saved)
    }

    @Transactional
    fun update(id: Long, command: ReplySnippetUpdateCommand): ReplySnippetDetail {
        val existing = findById(id)
        require(command.content.isNotBlank()) { "content is required" }
        require(command.displayOrder > 0) { "displayOrder must be positive" }
        mailVariableService.requireValidPlaceholders(command.content)
        if (command.isDefault) {
            require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
            require(existing.snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
        }
        contentVariantService.validateVariantTexts(command.content.trim(), command.variants)

        val updated = repository.save(
            existing.copy(
                content = command.content.trim(),
                displayOrder = command.displayOrder,
                variantGroup = command.variantGroup?.trim()?.takeIf { it.isNotBlank() },
                isDefault = command.isDefault,
                enabled = command.enabled
            )
        )
        if (updated.isDefault) {
            clearOtherDefaults(updated)
        }
        contentVariantService.replaceForOwner(
            ContentVariantOwnerType.REPLY_SNIPPET,
            id,
            updated.content,
            command.variants
        )
        return toDetail(updated)
    }

    fun setEnabled(id: Long, enabled: Boolean): ReplySnippetDetail {
        val existing = findById(id)
        return toDetail(repository.save(existing.copy(enabled = enabled)))
    }

    @Transactional
    fun setDefault(id: Long): ReplySnippetDetail {
        val existing = findById(id)
        require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
        require(existing.snippetType != SnippetType.CUSTOM.name) { "CUSTOM snippets cannot be default" }
        require(existing.enabled) { "Disabled snippet cannot be default" }

        repository.findBySnippetTypeAndIsDefaultTrue(existing.snippetType)
            .filter { it.id != existing.id }
            .forEach { repository.save(it.copy(isDefault = false)) }

        return toDetail(repository.save(existing.copy(isDefault = true)))
    }

    @Transactional
    fun delete(id: Long) {
        findById(id)
        contentVariantService.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, id)
        repository.deleteById(id)
    }

    private fun toDetail(snippet: ReplySnippet): ReplySnippetDetail {
        val snippetId = snippet.id ?: error("Reply snippet id is required")
        return ReplySnippetDetail(
            snippet = snippet,
            variants = contentVariantService.listByOwner(ContentVariantOwnerType.REPLY_SNIPPET, snippetId)
                .map { it.content }
        )
    }

    private fun resolveDefaultText(type: SnippetType): String? =
        repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
            .minByOrNull { it.displayOrder }
            ?.content
            ?.takeIf { it.isNotBlank() }

    private fun clearOtherDefaults(saved: ReplySnippet) {
        repository.findBySnippetTypeAndIsDefaultTrue(saved.snippetType)
            .filter { it.id != saved.id }
            .forEach { repository.save(it.copy(isDefault = false)) }
    }

    private fun findById(id: Long): ReplySnippet =
        repository.findById(id).orElseThrow { error("Reply snippet not found: $id") }

    private fun validateSnippetType(snippetType: String) {
        require(snippetType.uppercase() in SnippetType.ALL) {
            "snippetType must be one of: ${SnippetType.ALL.joinToString()}"
        }
    }
}

enum class SnippetType {
    SALUTATION,
    ACK,
    GREETING,
    CLOSING,
    CUSTOM;

    companion object {
        val ALL = entries.map { it.name }
    }
}

data class ManualReplyFrame(
    val salutation: String?,
    val greeting: String?,
    val closing: String?,
    val ackOptions: List<AckOption>
)

data class AckOption(
    val id: Long,
    val content: String
)

data class ReplySnippetDetail(
    val snippet: ReplySnippet,
    val variants: List<String> = emptyList()
)

data class ReplySnippetCreateCommand(
    val snippetType: String,
    val content: String,
    val displayOrder: Int = 100,
    val variantGroup: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val variants: List<String> = emptyList()
)

data class ReplySnippetUpdateCommand(
    val content: String,
    val displayOrder: Int,
    val variantGroup: String? = null,
    val isDefault: Boolean,
    val enabled: Boolean,
    val variants: List<String> = emptyList()
)
