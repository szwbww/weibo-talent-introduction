package com.weibo.talentintroduction.reply.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

    /**
     * I-1/I-3 options reader: enabled, non-blank, main snippets of the four
     * frame slots only, in fixed slot order then displayOrder then id.
     * CUSTOM and content variants are never selectable frame options.
     */
    fun listSelectableFrameOptions(): List<ReplyFrameOption> =
        FRAME_SLOT_TYPES.flatMap { type ->
            repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(type.name)
                .asSequence()
                .filter { it.enabled && it.content.isNotBlank() }
                .sortedWith(compareBy({ it.displayOrder }, { it.id ?: Long.MAX_VALUE }))
                .mapNotNull { snippet ->
                    snippet.id?.let { id ->
                        ReplyFrameOption(
                            id = id,
                            snippetType = snippet.snippetType,
                            content = snippet.content,
                            displayOrder = snippet.displayOrder,
                            isDefault = snippet.isDefault
                        )
                    }
                }
                .toList()
        }

    /**
     * I-2 default frame: current enabled defaults for SALUTATION/GREETING/CLOSING,
     * ACK is never part of the default frame. A slot without an enabled default
     * resolves to null (absent), matching the legacy [resolveManualFrame] shape.
     */
    fun resolveDefaultSelectableFrame(): ResolvedReplyFrame {
        val selection = ReplyFrameSelection(
            salutationSnippetId = defaultSnippetId(SnippetType.SALUTATION),
            greetingSnippetId = defaultSnippetId(SnippetType.GREETING),
            ackSnippetId = null,
            closingSnippetId = defaultSnippetId(SnippetType.CLOSING)
        )
        return resolveSelectableFrame(selection)
    }

    /**
     * I-1 strict selectable-frame resolver: every non-null id must resolve to an
     * enabled snippet whose type matches the slot exactly and whose content is
     * non-blank, otherwise this fails closed with [IllegalArgumentException].
     * Null ids explicitly omit that slot; an all-null selection is a deliberate
     * "no frame" choice and never falls back to defaults (I-2).
     */
    fun resolveSelectableFrame(selection: ReplyFrameSelection): ResolvedReplyFrame {
        val salutation = resolveFrameSlot(selection.salutationSnippetId, SnippetType.SALUTATION)
        val greeting = resolveFrameSlot(selection.greetingSnippetId, SnippetType.GREETING)
        val ack = resolveFrameSlot(selection.ackSnippetId, SnippetType.ACK)
        val closing = resolveFrameSlot(selection.closingSnippetId, SnippetType.CLOSING)
        return ResolvedReplyFrame(
            selection = selection,
            version = frameVersion(salutation, greeting, ack, closing),
            salutation = salutation?.content,
            greeting = greeting?.content,
            acknowledgement = ack?.content,
            closing = closing?.content
        )
    }

    private fun defaultSnippetId(type: SnippetType): Long? =
        repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(type.name)
            .asSequence()
            .filter { it.content.isNotBlank() }
            .sortedWith(compareBy({ it.displayOrder }, { it.id ?: Long.MAX_VALUE }))
            .firstNotNullOfOrNull { it.id }

    private fun resolveFrameSlot(id: Long?, expected: SnippetType): ReplySnippet? {
        if (id == null) {
            return null
        }
        val snippet = repository.findById(id).orElse(null)
            ?: throw IllegalArgumentException("frame snippet not found for ${expected.name}: $id")
        if (!snippet.enabled) {
            throw IllegalArgumentException("frame snippet disabled for ${expected.name}: $id")
        }
        if (snippet.snippetType != expected.name) {
            throw IllegalArgumentException(
                "frame snippet type mismatch for ${expected.name}: ${snippet.snippetType} (id $id)"
            )
        }
        if (snippet.content.isBlank()) {
            throw IllegalArgumentException("frame snippet content is blank for ${expected.name}: $id")
        }
        return snippet
    }

    /**
     * I-3 deterministic frame version: fixed slot order (SALUTATION, GREETING,
     * ACK, CLOSING), and per slot the id/NULL, type, enabled flag, updatedAt and
     * content SHA-256. No observed time enters the version.
     */
    private fun frameVersion(
        salutation: ReplySnippet?,
        greeting: ReplySnippet?,
        ack: ReplySnippet?,
        closing: ReplySnippet?
    ): String {
        val canonical = listOf(
            frameSlotIdentity(SnippetType.SALUTATION, salutation),
            frameSlotIdentity(SnippetType.GREETING, greeting),
            frameSlotIdentity(SnippetType.ACK, ack),
            frameSlotIdentity(SnippetType.CLOSING, closing)
        ).joinToString("\u0001")
        return sha256Hex(canonical)
    }

    private fun frameSlotIdentity(slot: SnippetType, snippet: ReplySnippet?): String {
        if (snippet == null) {
            return "${slot.name}\u0000NULL"
        }
        return listOf(
            slot.name,
            snippet.id?.toString() ?: "NULL",
            snippet.snippetType,
            snippet.enabled.toString(),
            snippet.updatedAt?.toString().orEmpty(),
            sha256Hex(snippet.content)
        ).joinToString("\u0000")
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
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

    companion object {
        val FRAME_SLOT_TYPES = listOf(
            SnippetType.SALUTATION,
            SnippetType.GREETING,
            SnippetType.ACK,
            SnippetType.CLOSING
        )
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

/**
 * Client-supplied frame choice: four nullable snippet ids, one per slot.
 * An all-null selection is the explicit "no frame" choice (I-2).
 */
data class ReplyFrameSelection(
    val salutationSnippetId: Long? = null,
    val greetingSnippetId: Long? = null,
    val ackSnippetId: Long? = null,
    val closingSnippetId: Long? = null
)

data class ReplyFrameOption(
    val id: Long,
    val snippetType: String,
    val content: String,
    val displayOrder: Int,
    val isDefault: Boolean
)

/**
 * Server-resolved frame: the effective selection, its deterministic version
 * (I-3) and the authoritative per-slot text read fresh from [reply_snippet].
 * The text is only used for this assembly/response; it is never persisted.
 */
data class ResolvedReplyFrame(
    val selection: ReplyFrameSelection,
    val version: String,
    val salutation: String?,
    val greeting: String?,
    val acknowledgement: String?,
    val closing: String?
)

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
