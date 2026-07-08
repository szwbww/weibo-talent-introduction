package com.weibo.talentintroduction.template.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.PreviewVariableItem
import com.weibo.talentintroduction.mail.service.RenderPreviewResult
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.domain.ComposeBlockType
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MailComposeTemplateService(
    private val templateRepository: MailComposeTemplateRepository,
    private val blockRepository: MailComposeTemplateBlockRepository,
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetRepository: ReplySnippetRepository,
    private val objectMapper: ObjectMapper,
    @Lazy private val mailVariableService: MailVariableService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailSenderAccountService: MailSenderAccountService
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
                subjectVariants = command.subjectVariants,
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
                subjectVariants = command.subjectVariants,
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

    fun render(id: Long, variables: Map<String, String> = emptyMap(), variantSeed: Int = 0): ComposeTemplateRenderResult {
        val template = findTemplate(id)
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
        return renderTemplate(template, blocks, variables, variantSeed)
    }

    fun renderByCode(
        templateCode: String,
        variables: Map<String, String> = emptyMap(),
        variantSeed: Int = 0
    ): ComposeTemplateRenderResult {
        val template = templateRepository.findByTemplateCodeAndEnabledTrue(templateCode)
            ?: error("Enabled compose template not found: $templateCode")
        val templateId = template.id ?: error("Compose template id is required")
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId)
        return renderTemplate(template, blocks, variables, variantSeed)
    }

    private fun renderTemplate(
        template: MailComposeTemplate,
        blocks: List<MailComposeTemplateBlock>,
        variables: Map<String, String>,
        variantSeed: Int = 0
    ): ComposeTemplateRenderResult {
        val subjectText = selectSubjectVariant(template, variantSeed)
        val resolved = resolveBlocks(blocks.map { it.toDraftBlock() }, variables, variantSeed)
        return ComposeTemplateRenderResult(
            subject = renderText(subjectText, variables),
            body = resolved.includedTexts.joinToString("\n\n"),
            qaRuleIds = resolved.qaRuleIds,
            mailType = template.mailType
        )
    }

    fun preview(id: Long): ComposeTemplatePreviewResult {
        val template = findTemplate(id)
        val blocks = blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)
            .map { it.toDraftBlock() }
        val resolved = resolveBlocks(blocks)
        return ComposeTemplatePreviewResult(
            subject = template.subject,
            body = resolved.includedTexts.joinToString("\n\n"),
            blocks = resolved.previewBlocks
        )
    }

    fun previewDraft(request: ComposeTemplatePreviewDraftRequest): ComposeTemplatePreviewDraftResult {
        val variantSeed = request.variantIndex ?: 0
        val draftBlocks = request.blocks.map { block ->
            ComposeDraftBlock(
                blockOrder = block.blockOrder,
                blockType = block.blockType.uppercase(),
                refId = block.refId,
                customText = block.customText
            )
        }
        val baseResolved = resolveBlocks(draftBlocks, variantSeed = variantSeed, renderVariables = false)
        val subjectTemplate = selectDraftSubject(request.subject, request.subjectVariants, request.variantIndex)
        val contact = resolvePreviewContact(request.contactId, request.orcidId)
        val account = resolvePreviewAccount(request.senderAccountCode)

        if (contact == null) {
            val texts = listOf(subjectTemplate) + baseResolved.rawTextsByOrder.values
            return ComposeTemplatePreviewDraftResult(
                subject = subjectTemplate,
                body = baseResolved.includedTexts.joinToString("\n\n"),
                blocks = baseResolved.previewBlocks,
                fallbackKeys = mailVariableService.placeholderKeysIn(*texts.toTypedArray()),
                toEmail = null,
                variables = emptyList()
            )
        }

        val subjectResult = mailVariableService.renderPreview(subjectTemplate, account, contact)
        val allFallbackKeys = subjectResult.fallbackKeys.toMutableList()
        val allVariables = subjectResult.variables.toMutableList()
        val bodyParts = mutableListOf<String>()
        val finalBlocks = mutableListOf<ComposeTemplatePreviewBlock>()

        baseResolved.previewBlocks.forEach { block ->
            if (!block.included) {
                finalBlocks += block
                return@forEach
            }
            val rawText = baseResolved.rawTextsByOrder[block.blockOrder].orEmpty()
            val rendered = mailVariableService.renderPreview(rawText, account, contact)
            allFallbackKeys += rendered.fallbackKeys
            mergePreviewVariables(allVariables, rendered.variables)
            if (request.strictPlaceholders && !placeholdersSatisfied(rendered)) {
                finalBlocks += block.copy(
                    included = false,
                    skipReason = "存在未满足占位符",
                    textPreview = null
                )
            } else {
                bodyParts += rendered.rendered
                finalBlocks += block.copy(
                    textPreview = rendered.rendered.take(200).ifBlank { null }
                )
            }
        }

        val finalSubject = if (request.strictPlaceholders && !placeholdersSatisfied(subjectResult)) {
            "占位符未满足，无法预览"
        } else {
            subjectResult.rendered
        }

        return ComposeTemplatePreviewDraftResult(
            subject = finalSubject,
            body = bodyParts.joinToString("\n\n"),
            blocks = finalBlocks,
            fallbackKeys = allFallbackKeys.distinct(),
            toEmail = contact.expertEmail,
            variables = allVariables
        )
    }

    private fun resolvePreviewContact(contactId: Long?, orcidId: String?): ExpertContact? {
        contactId?.let { id ->
            return expertContactRepository.findById(id).orElse(null)
        }
        val trimmedOrcid = orcidId?.trim().orEmpty()
        if (trimmedOrcid.isBlank()) {
            return null
        }
        return ExpertContact(
            campaignId = 0,
            orcidId = trimmedOrcid,
            expertEmail = "preview@local",
            expertName = "Preview",
            currentIndexLevel = "CANDIDATE"
        )
    }

    private fun resolvePreviewAccount(senderAccountCode: String?): MailSenderAccount? {
        val code = senderAccountCode?.trim().orEmpty()
        if (code.isBlank()) {
            return null
        }
        return runCatching { mailSenderAccountService.getAccount(code) }.getOrNull()
    }

    private fun selectDraftSubject(subject: String, variants: List<String>?, variantIndex: Int? = null): String {
        val pool = buildSubjectPool(subject, variants)
        return pool[Math.floorMod(variantIndex ?: 0, pool.size)]
    }

    private fun placeholdersSatisfied(result: RenderPreviewResult): Boolean {
        if (result.fallbackKeys.isNotEmpty() || result.invalidTokens.isNotEmpty()) {
            return false
        }
        return result.variables.all { it.filled && !it.usedFallback }
    }

    private fun mergePreviewVariables(
        target: MutableList<PreviewVariableItem>,
        incoming: List<PreviewVariableItem>
    ) {
        val existingKeys = target.map { it.key }.toMutableSet()
        incoming.forEach { item ->
            if (existingKeys.add(item.key)) {
                target += item
            }
        }
    }

    private fun MailComposeTemplateBlock.toDraftBlock(): ComposeDraftBlock =
        ComposeDraftBlock(
            blockOrder = blockOrder,
            blockType = blockType,
            refId = refId,
            customText = customText
        )

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
            subjectVariants = template.subjectVariants,
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
        validateSubjectVariants(command.subject, command.subjectVariants)
        command.blocks.forEach { block ->
            validateBlockCommand(block)
        }
    }

    private fun validateSubjectVariants(subject: String, subjectVariantsJson: String?) {
        if (subjectVariantsJson == null) return
        val root = try {
            objectMapper.readTree(subjectVariantsJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("主题变体不是合法的 JSON 数组")
        }
        if (!root.isArray) {
            throw IllegalArgumentException("主题变体不是合法的 JSON 字符串数组")
        }
        val variants = mutableListOf<String>()
        for (node in root) {
            if (!node.isTextual) {
                throw IllegalArgumentException("主题变体不是合法的 JSON 字符串数组")
            }
            variants.add(node.asText())
        }
        val trimmedSubject = subject.trim()
        val seen = mutableSetOf<String>()
        variants.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("主题变体第 ${index + 1} 项不能为空")
            }
            if (trimmed == trimmedSubject) {
                throw IllegalArgumentException("主题变体不能与主主题重复")
            }
            if (!seen.add(trimmed)) {
                throw IllegalArgumentException("主题变体不能重复")
            }
            val violations = mailVariableService.validatePlaceholders(trimmed)
            if (violations.isNotEmpty()) {
                throw IllegalArgumentException("主题变体包含非法占位符: ${violations.joinToString(", ")}")
            }
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
        blocks: List<ComposeDraftBlock>,
        variables: Map<String, String> = emptyMap(),
        variantSeed: Int = 0,
        renderVariables: Boolean = true
    ): ResolvedBlocks {
        val includedTexts = mutableListOf<String>()
        val qaRuleIds = mutableListOf<Long>()
        val previewBlocks = mutableListOf<ComposeTemplatePreviewBlock>()
        val rawTextsByOrder = mutableMapOf<Int, String>()

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
                    val text = if (renderVariables) {
                        renderText(rule.replyBody, variables).trim()
                    } else {
                        rule.replyBody.trim()
                    }
                    rawTextsByOrder[block.blockOrder] = text
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
                    val resolvedSnippet = resolveSnippetVariant(snippet, variantSeed)
                    val displayName = "${resolvedSnippet.snippetType} #${resolvedSnippet.id}"
                    if (!resolvedSnippet.enabled) {
                        previewBlocks += skippedPreviewBlock(block, "已禁用", refId, displayName)
                        return@forEach
                    }
                    val text = if (renderVariables) {
                        renderText(resolvedSnippet.content, variables).trim()
                    } else {
                        resolvedSnippet.content.trim()
                    }
                    rawTextsByOrder[block.blockOrder] = text
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
                    val text = if (renderVariables) {
                        block.customText?.let { renderText(it, variables) }?.trim().orEmpty()
                    } else {
                        block.customText?.trim().orEmpty()
                    }
                    rawTextsByOrder[block.blockOrder] = text
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
            previewBlocks = previewBlocks,
            rawTextsByOrder = rawTextsByOrder
        )
    }

    private fun resolveSnippetVariant(snippet: ReplySnippet, variantSeed: Int): ReplySnippet {
        val variantGroup = snippet.variantGroup?.trim()?.takeIf { it.isNotBlank() } ?: return snippet
        val groupSnippets = replySnippetRepository
            .findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(variantGroup, snippet.snippetType)
        if (groupSnippets.isEmpty()) return snippet
        return groupSnippets[Math.floorMod(variantSeed + variantGroup.hashCode(), groupSnippets.size)]
    }

    private fun selectSubjectVariant(template: MailComposeTemplate, seed: Int): String {
        val pool = buildSubjectPool(template.subject, parseSubjectVariants(template.subjectVariants))
        return pool[Math.floorMod(seed, pool.size)]
    }

    private fun buildSubjectPool(subject: String, variants: List<String>?): List<String> {
        if (variants.isNullOrEmpty()) return listOf(subject)
        return listOf(subject) + variants
    }

    private fun parseSubjectVariants(json: String?): List<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            null
        }
    }

    private fun skippedPreviewBlock(
        block: ComposeDraftBlock,
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
        val previewBlocks: List<ComposeTemplatePreviewBlock>,
        val rawTextsByOrder: Map<Int, String> = emptyMap()
    )

    fun renderWithVariables(text: String, variables: Map<String, String>): String = renderText(text, variables)

    private fun renderText(text: String, variables: Map<String, String>): String {
        val withFallback = FALLBACK_PLACEHOLDER_REGEX.replace(text) { match ->
            val key = match.groupValues[1]
            val fallback = match.groupValues[2]
            variables[key]?.takeIf { it.isNotEmpty() } ?: fallback
        }
        return variables.entries.fold(withFallback) { rendered, (key, value) ->
            rendered.replace("\${$key}", value)
        }
    }

    companion object {
        private val FALLBACK_PLACEHOLDER_REGEX = Regex("""\$\{(\w+)\|([^}]*)\}""")

        fun variantSeedFor(orcidId: String?, email: String?): Int {
            val trimmedOrcid = orcidId?.trim()?.takeIf { it.isNotBlank() }
            if (trimmedOrcid != null) return trimmedOrcid.hashCode()
            val trimmedEmail = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            if (trimmedEmail != null) return trimmedEmail.hashCode()
            return 0
        }
    }
}

data class MailComposeTemplateCommand(
    val templateCode: String? = null,
    val templateName: String,
    val subject: String,
    val description: String? = null,
    val mailType: String? = null,
    val subjectVariants: String? = null,
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
    val subjectVariants: String?,
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

data class ComposeDraftBlock(
    val blockOrder: Int,
    val blockType: String,
    val refId: Long? = null,
    val customText: String? = null
)

data class ComposeTemplatePreviewDraftRequest(
    val subject: String,
    val subjectVariants: List<String> = emptyList(),
    val blocks: List<ComposeDraftBlock> = emptyList(),
    val variantIndex: Int? = null,
    val orcidId: String? = null,
    val contactId: Long? = null,
    val senderAccountCode: String? = null,
    val strictPlaceholders: Boolean = false
)

data class ComposeTemplatePreviewDraftResult(
    val subject: String,
    val body: String,
    val blocks: List<ComposeTemplatePreviewBlock>,
    val fallbackKeys: List<String>,
    val toEmail: String? = null,
    val variables: List<PreviewVariableItem> = emptyList()
)
