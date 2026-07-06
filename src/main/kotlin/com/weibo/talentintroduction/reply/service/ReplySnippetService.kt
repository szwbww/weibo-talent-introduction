package com.weibo.talentintroduction.reply.service

import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReplySnippetService(
    private val repository: ReplySnippetRepository
) {
    fun listAll(): List<ReplySnippet> =
        repository.findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc()

    fun listByType(snippetType: String): List<ReplySnippet> {
        validateSnippetType(snippetType)
        return repository.findAllBySnippetTypeOrderByDisplayOrderAscIdAsc(snippetType.uppercase())
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

    fun create(command: ReplySnippetCreateCommand): ReplySnippet {
        val snippetType = command.snippetType.uppercase()
        validateSnippetType(snippetType)
        require(command.content.isNotBlank()) { "content is required" }
        require(command.displayOrder > 0) { "displayOrder must be positive" }
        if (command.isDefault) {
            require(snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
        }

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
        return saved
    }

    fun update(id: Long, command: ReplySnippetUpdateCommand): ReplySnippet {
        val existing = findById(id)
        require(command.content.isNotBlank()) { "content is required" }
        require(command.displayOrder > 0) { "displayOrder must be positive" }
        if (command.isDefault) {
            require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
        }

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
        return updated
    }

    fun setEnabled(id: Long, enabled: Boolean): ReplySnippet {
        val existing = findById(id)
        return repository.save(existing.copy(enabled = enabled))
    }

    @Transactional
    fun setDefault(id: Long): ReplySnippet {
        val existing = findById(id)
        require(existing.snippetType != SnippetType.ACK.name) { "ACK snippets cannot be default" }
        require(existing.enabled) { "Disabled snippet cannot be default" }

        repository.findBySnippetTypeAndIsDefaultTrue(existing.snippetType)
            .filter { it.id != existing.id }
            .forEach { repository.save(it.copy(isDefault = false)) }

        return repository.save(existing.copy(isDefault = true))
    }

    fun delete(id: Long) {
        findById(id)
        repository.deleteById(id)
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
    CLOSING;

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

data class ReplySnippetCreateCommand(
    val snippetType: String,
    val content: String,
    val displayOrder: Int = 100,
    val variantGroup: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true
)

data class ReplySnippetUpdateCommand(
    val content: String,
    val displayOrder: Int,
    val variantGroup: String? = null,
    val isDefault: Boolean,
    val enabled: Boolean
)
