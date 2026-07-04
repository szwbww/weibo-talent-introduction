package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.domain.ComposeBlockType
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MailComposeTemplateService(
    private val templateRepository: MailComposeTemplateRepository,
    private val blockRepository: MailComposeTemplateBlockRepository,
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetRepository: ReplySnippetRepository
) {
    fun listAll(): List<MailComposeTemplateDetail> =
        templateRepository.findAllByOrderByIdAsc().map { toDetail(it) }

    fun listEnabled(): List<MailComposeTemplate> =
        templateRepository.findAllByEnabledTrueOrderByIdAsc()

    fun getById(id: Long): MailComposeTemplateDetail {
        val template = findTemplate(id)
        return toDetail(template)
    }

    @Transactional
    fun create(command: MailComposeTemplateCommand): MailComposeTemplateDetail {
        validateCommand(command)
        val now = LocalDateTime.now()
        val saved = templateRepository.save(
            MailComposeTemplate(
                templateCode = command.templateCode?.trim()?.takeIf { it.isNotBlank() },
                templateName = command.templateName.trim(),
                subject = command.subject.trim(),
                description = command.description?.trim()?.takeIf { it.isNotBlank() },
                mailType = command.mailType?.trim()?.takeIf { it.isNotBlank() },
                enabled = command.enabled,
                createdAt = now,
                updatedAt = now
            )
        )
        val templateId = saved.id ?: error("Compose template id is required")
        saveBlocks(templateId, command.blocks)
        return getById(templateId)
    }

    @Transactional
    fun update(id: Long, command: MailComposeTemplateCommand): MailComposeTemplateDetail {
        validateCommand(command)
        val existing = findTemplate(id)
        val now = LocalDateTime.now()
        templateRepository.save(
            existing.copy(
                templateCode = command.templateCode?.trim()?.takeIf { it.isNotBlank() } ?: existing.templateCode,
                templateName = command.templateName.trim(),
                subject = command.subject.trim(),
                description = command.description?.trim()?.takeIf { it.isNotBlank() },
                mailType = command.mailType?.trim()?.takeIf { it.isNotBlank() } ?: existing.mailType,
                enabled = command.enabled,
                updatedAt = now
            )
        )
        blockRepository.deleteAllByTemplateId(id)
        saveBlocks(id, command.blocks)
        return getById(id)
    }

    @Transactional
    fun setEnabled(id: Long, enabled: Boolean): MailComposeTemplateDetail {
        val existing = findTemplate(id)
        val now = LocalDateTime.now()
        templateRepository.save(
            existing.copy(
                enabled = enabled,
                updatedAt = now
            )
        )
        return getById(id)
    }

    @Transactional
    fun delete(id: Long) {
        findTemplate(id)
        blockRepository.deleteAllByTemplateId(id)
        templateRepository.deleteById(id)
    }

    fun render(id: Long, variables: Map<String, String> = emptyMap()): ComposeTemplateRenderResult {
        val template = findTemplate(id)
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
        return renderTemplate(template, blocks, variables)
    }

    fun renderByCode(templateCode: String, variables: Map<String, String> = emptyMap()): ComposeTemplateRenderResult {
        val template = templateRepository.findByTemplateCodeAndEnabledTrue(templateCode)
            ?: error("Enabled compose template not found: $templateCode")
        val templateId = template.id ?: error("Compose template id is required")
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
        return renderTemplate(template, blocks, variables)
    }

    private fun renderTemplate(
        template: MailComposeTemplate,
        blocks: List<MailComposeTemplateBlock>,
        variables: Map<String, String>
    ): ComposeTemplateRenderResult {
        val resolved = resolveBlocks(blocks, variables)
        return ComposeTemplateRenderResult(
            subject = renderText(template.subject, variables),
            body = resolved.includedTexts.joinToString("\n\n"),
            qaRuleIds = resolved.qaRuleIds,
            mailType = template.mailType
        )
    }

    fun preview(id: Long): ComposeTemplatePreviewResult {
        val template = findTemplate(id)
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
        val resolved = resolveBlocks(blocks)
        return ComposeTemplatePreviewResult(
            subject = template.subject,
            body = resolved.includedTexts.joinToString("\n\n"),
            blocks = resolved.previewBlocks
        )
    }

    private fun findTemplate(id: Long): MailComposeTemplate =
        templateRepository.findById(id).orElseThrow { error("Compose template not found: $id") }

    private fun toDetail(template: MailComposeTemplate): MailComposeTemplateDetail {
        val templateId = template.id ?: error("Compose template id is required")
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
            .map { block -> toBlockDetail(block) }
        return MailComposeTemplateDetail(
            id = templateId,
            templateCode = template.templateCode,
            templateName = template.templateName,
            subject = template.subject,
            description = template.description,
            mailType = template.mailType,
            enabled = template.enabled,
            blocks = blocks,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt
        )
    }

    private fun toBlockDetail(block: MailComposeTemplateBlock): MailComposeTemplateBlockDetail {
        val refDisplayName = resolveRefDisplayName(block.blockType, block.refId)
        return MailComposeTemplateBlockDetail(
            id = block.id,
            blockOrder = block.blockOrder,
            blockType = block.blockType,
            refId = block.refId,
            refDisplayName = refDisplayName,
            customText = block.customText
        )
    }

    private fun resolveRefDisplayName(blockType: String, refId: Long?): String? {
        if (refId == null) {
            return null
        }
        return when (blockType) {
            ComposeBlockType.QA_RULE -> {
                qaRuleRepository.findById(refId).orElse(null)?.let { rule ->
                    rule.displayName?.takeIf { it.isNotBlank() }
                        ?: rule.replySubject?.takeIf { it.isNotBlank() }
                        ?: "Rule #$refId"
                }
            }
            ComposeBlockType.REPLY_SNIPPET -> {
                replySnippetRepository.findById(refId).orElse(null)?.let { snippet ->
                    "${snippet.snippetType} #${snippet.id}"
                }
            }
            else -> null
        }
    }

    private fun saveBlocks(templateId: Long, blocks: List<MailComposeTemplateBlockCommand>) {
        blocks.forEach { blockCommand ->
            blockRepository.save(
                MailComposeTemplateBlock(
                    templateId = templateId,
                    blockOrder = blockCommand.blockOrder,
                    blockType = blockCommand.blockType.uppercase(),
                    refId = blockCommand.refId,
                    customText = blockCommand.customText?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun validateCommand(command: MailComposeTemplateCommand) {
        require(command.templateName.isNotBlank()) { "templateName is required" }
        require(command.subject.isNotBlank()) { "subject is required" }
        require(command.blocks.isNotEmpty()) { "At least one content block is required" }
        command.blocks.forEach { block ->
            validateBlockCommand(block)
        }
    }

    private fun validateBlockCommand(block: MailComposeTemplateBlockCommand) {
        require(block.blockOrder >= 0) { "blockOrder must be non-negative" }
        when (block.blockType.uppercase()) {
            ComposeBlockType.QA_RULE -> require(block.refId != null) { "QA_RULE block requires refId" }
            ComposeBlockType.REPLY_SNIPPET -> require(block.refId != null) { "REPLY_SNIPPET block requires refId" }
            ComposeBlockType.CUSTOM_TEXT -> require(!block.customText.isNullOrBlank()) { "CUSTOM_TEXT block requires customText" }
            else -> error("Unsupported block type: ${block.blockType}")
        }
    }

    private fun resolveBlocks(
        blocks: List<MailComposeTemplateBlock>,
        variables: Map<String, String> = emptyMap()
    ): ResolvedBlocks {
        val includedTexts = mutableListOf<String>()
        val qaRuleIds = mutableListOf<Long>()
        val previewBlocks = mutableListOf<ComposeTemplatePreviewBlock>()

        blocks.sortedBy { it.blockOrder }.forEach { block ->
            when (block.blockType) {
                ComposeBlockType.QA_RULE -> {
                    val refId = block.refId
                    if (refId == null) {
                        previewBlocks += skippedPreviewBlock(block, "缺少 QA 规则引用")
                        return@forEach
                    }
                    val rule = qaRuleRepository.findById(refId).orElse(null)
                    if (rule == null) {
                        previewBlocks += skippedPreviewBlock(block, "QA 规则不存在", refId, null)
                        return@forEach
                    }
                    val displayName = rule.displayName?.takeIf { it.isNotBlank() }
                        ?: rule.replySubject?.takeIf { it.isNotBlank() }
                        ?: "Rule #$refId"
                    if (!rule.enabled) {
                        previewBlocks += skippedPreviewBlock(block, "已禁用", refId, displayName)
                        return@forEach
                    }
                    val text = renderText(rule.replyBody, variables).trim()
                    if (text.isNotBlank()) {
                        includedTexts += text
                        qaRuleIds += refId
                    }
                    previewBlocks += ComposeTemplatePreviewBlock(
                        blockOrder = block.blockOrder,
                        blockType = block.blockType,
                        refId = refId,
                        refDisplayName = displayName,
                        included = text.isNotBlank(),
                        skipReason = if (text.isBlank()) "正文为空" else null,
                        textPreview = text.take(200).ifBlank { null }
                    )
                }
                ComposeBlockType.REPLY_SNIPPET -> {
                    val refId = block.refId
                    if (refId == null) {
                        previewBlocks += skippedPreviewBlock(block, "缺少回复片段引用")
                        return@forEach
                    }
                    val snippet = replySnippetRepository.findById(refId).orElse(null)
                    if (snippet == null) {
                        previewBlocks += skippedPreviewBlock(block, "回复片段不存在", refId, null)
                        return@forEach
                    }
                    val displayName = "${snippet.snippetType} #${snippet.id}"
                    if (!snippet.enabled) {
                        previewBlocks += skippedPreviewBlock(block, "已禁用", refId, displayName)
                        return@forEach
                    }
                    val text = renderText(snippet.content, variables).trim()
                    if (text.isNotBlank()) {
                        includedTexts += text
                    }
                    previewBlocks += ComposeTemplatePreviewBlock(
                        blockOrder = block.blockOrder,
                        blockType = block.blockType,
                        refId = refId,
                        refDisplayName = displayName,
                        included = text.isNotBlank(),
                        skipReason = if (text.isBlank()) "正文为空" else null,
                        textPreview = text.take(200).ifBlank { null }
                    )
                }
                ComposeBlockType.CUSTOM_TEXT -> {
                    val text = block.customText?.let { renderText(it, variables) }?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        includedTexts += text
                    }
                    previewBlocks += ComposeTemplatePreviewBlock(
                        blockOrder = block.blockOrder,
                        blockType = block.blockType,
                        refId = null,
                        refDisplayName = null,
                        included = text.isNotBlank(),
                        skipReason = if (text.isBlank()) "正文为空" else null,
                        textPreview = text.take(200).ifBlank { null }
                    )
                }
                else -> previewBlocks += skippedPreviewBlock(block, "未知块类型")
            }
        }

        return ResolvedBlocks(
            includedTexts = includedTexts,
            qaRuleIds = qaRuleIds,
            previewBlocks = previewBlocks
        )
    }

    private fun skippedPreviewBlock(
        block: MailComposeTemplateBlock,
        reason: String,
        refId: Long? = block.refId,
        refDisplayName: String? = resolveRefDisplayName(block.blockType, block.refId)
    ): ComposeTemplatePreviewBlock =
        ComposeTemplatePreviewBlock(
            blockOrder = block.blockOrder,
            blockType = block.blockType,
            refId = refId,
            refDisplayName = refDisplayName,
            included = false,
            skipReason = reason,
            textPreview = null
        )

    private data class ResolvedBlocks(
        val includedTexts: List<String>,
        val qaRuleIds: List<Long>,
        val previewBlocks: List<ComposeTemplatePreviewBlock>
    )

    private fun renderText(text: String, variables: Map<String, String>): String =
        variables.entries.fold(text) { rendered, (key, value) ->
            rendered.replace("\${$key}", value)
        }
}

data class MailComposeTemplateCommand(
    val templateCode: String? = null,
    val templateName: String,
    val subject: String,
    val description: String? = null,
    val mailType: String? = null,
    val enabled: Boolean = true,
    val blocks: List<MailComposeTemplateBlockCommand>
)

data class MailComposeTemplateBlockCommand(
    val blockOrder: Int,
    val blockType: String,
    val refId: Long? = null,
    val customText: String? = null
)

data class MailComposeTemplateDetail(
    val id: Long,
    val templateCode: String?,
    val templateName: String,
    val subject: String,
    val description: String?,
    val mailType: String?,
    val enabled: Boolean,
    val blocks: List<MailComposeTemplateBlockDetail>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class MailComposeTemplateBlockDetail(
    val id: Long?,
    val blockOrder: Int,
    val blockType: String,
    val refId: Long?,
    val refDisplayName: String?,
    val customText: String?
)

data class ComposeTemplateRenderResult(
    val subject: String,
    val body: String,
    val qaRuleIds: List<Long> = emptyList(),
    val mailType: String? = null
)

data class ComposeTemplatePreviewResult(
    val subject: String,
    val body: String,
    val blocks: List<ComposeTemplatePreviewBlock>
)

data class ComposeTemplatePreviewBlock(
    val blockOrder: Int,
    val blockType: String,
    val refId: Long?,
    val refDisplayName: String?,
    val included: Boolean,
    val skipReason: String?,
    val textPreview: String?
)
