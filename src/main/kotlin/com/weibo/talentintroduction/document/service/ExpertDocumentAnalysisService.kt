package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.document.domain.ExpertAnalysisResult
import com.weibo.talentintroduction.document.repository.ExpertAnalysisResultRepository
import com.weibo.talentintroduction.llm.service.LlmChatMessage
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AnalysisFieldView(
    val id: Long,
    val fieldKey: String,
    val fieldLabel: String,
    val value: String,
    val sourceAttachmentId: Long?,
    val sourceFileName: String?,
    val sourceExcerpt: String?,
    val verified: Boolean,
    val displayOrder: Int
)

data class AnalysisResultView(
    val fields: List<AnalysisFieldView>
)

@Service
class ExpertDocumentAnalysisService(
    private val documentTextExtractor: DocumentTextExtractor,
    private val analysisResultRepository: ExpertAnalysisResultRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val llmProperties: LlmProperties,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun analyze(contactId: Long, attachmentIds: List<Long>): AnalysisResultView {
        require(attachmentIds.isNotEmpty()) { "attachmentIds must not be empty" }

        attachmentIds.forEach { attachmentId ->
            documentTextExtractor.validateAttachmentBelongsToContact(contactId, attachmentId)
        }

        val extracted = documentTextExtractor.extract(contactId, attachmentIds)
        val supported = extracted.values.filter { it.supported && it.text.isNotBlank() }
        if (supported.isEmpty()) {
            throw AnalysisFailedException("所选文件均无法提取文本，请选择 PDF 或文本文件")
        }

        if (!llmProperties.enabled || llmProperties.apiUrl.isBlank()) {
            throw AnalysisFailedException("LLM 服务未启用，无法执行分析")
        }

        val client = llmDraftClientProvider.getIfAvailable()
            ?: throw AnalysisFailedException("LLM 客户端不可用")

        val prompt = buildAnalysisPrompt(supported)
        val rawResponse = try {
            client.chat(
                listOf(
                    LlmChatMessage(role = "system", content = SYSTEM_PROMPT),
                    LlmChatMessage(role = "user", content = prompt)
                )
            )
        } catch (ex: Exception) {
            throw AnalysisFailedException("分析超时或 LLM 调用失败，请重试", ex)
        }

        if (rawResponse.isNullOrBlank()) {
            throw AnalysisFailedException("分析超时或 LLM 无响应，请重试")
        }

        val parsedFields = parseLlmResponse(rawResponse)
        val textByAttachmentId = supported.associate { it.attachmentId to it.text }
        val verifiedFields = parsedFields.map { field ->
            val attachmentId = parseAttachmentId(field.sourceFileId)
            val sourceText = attachmentId?.let { textByAttachmentId[it] }
            val verified = verifyExcerpt(sourceText, field.excerpt)
            ParsedAnalysisField(
                key = field.key,
                label = field.label,
                value = field.value,
                sourceAttachmentId = attachmentId,
                sourceExcerpt = field.excerpt,
                verified = verified
            )
        }

        analysisResultRepository.deleteAllByExpertContactId(contactId)
        val saved = verifiedFields.mapIndexed { index, field ->
            analysisResultRepository.save(
                ExpertAnalysisResult(
                    expertContactId = contactId,
                    fieldKey = field.key,
                    fieldLabel = field.label,
                    value = field.value,
                    sourceAttachmentId = field.sourceAttachmentId,
                    sourceExcerpt = field.sourceExcerpt,
                    excerptVerified = field.verified,
                    displayOrder = index
                )
            )
        }

        return toView(saved)
    }

    fun getResults(contactId: Long): AnalysisResultView {
        val results = analysisResultRepository.findAllByExpertContactIdOrderByDisplayOrderAsc(contactId)
        return toView(results)
    }

    @Transactional
    fun updateField(contactId: Long, fieldId: Long, value: String): AnalysisFieldView {
        val existing = analysisResultRepository.findById(fieldId)
            .orElseThrow { NoSuchElementException("Analysis field not found: $fieldId") }
        require(existing.expertContactId == contactId) {
            "Analysis field $fieldId does not belong to expert contact $contactId"
        }

        val updated = analysisResultRepository.save(
            existing.copy(value = value.trim())
        )
        return toFieldView(updated)
    }

    @Transactional
    fun addField(contactId: Long, fieldKey: String, fieldLabel: String, value: String): AnalysisFieldView {
        val existing = analysisResultRepository.findAllByExpertContactIdOrderByDisplayOrderAsc(contactId)
        val nextOrder = (existing.maxOfOrNull { it.displayOrder } ?: -1) + 1
        val saved = analysisResultRepository.save(
            ExpertAnalysisResult(
                expertContactId = contactId,
                fieldKey = fieldKey.trim(),
                fieldLabel = fieldLabel.trim(),
                value = value.trim(),
                displayOrder = nextOrder
            )
        )
        return toFieldView(saved)
    }

    @Transactional
    fun clearResults(contactId: Long) {
        analysisResultRepository.deleteAllByExpertContactId(contactId)
    }

    internal fun buildAnalysisPrompt(extracted: List<ExtractedText>): String = buildString {
        appendLine("Extract structured profile information from the following expert documents.")
        appendLine()
        extracted.forEach { file ->
            appendLine("<FILE name=\"${file.fileName}\" id=\"att_${file.attachmentId}\">")
            appendLine(file.text.take(MAX_FILE_TEXT_CHARS))
            appendLine("</FILE>")
            appendLine()
        }
    }

    internal fun verifyExcerpt(sourceText: String?, excerpt: String?): Boolean {
        if (sourceText.isNullOrBlank() || excerpt.isNullOrBlank()) {
            return false
        }
        if (sourceText.contains(excerpt)) {
            return true
        }
        val normalizedSource = normalizeForMatch(sourceText)
        val normalizedExcerpt = normalizeForMatch(excerpt)
        return normalizedSource.contains(normalizedExcerpt)
    }

    private fun toView(results: List<ExpertAnalysisResult>): AnalysisResultView =
        AnalysisResultView(fields = results.map { toFieldView(it) })

    private fun toFieldView(result: ExpertAnalysisResult): AnalysisFieldView {
        val fileName = result.sourceAttachmentId?.let { attachmentId ->
            mailAttachmentRepository.findById(attachmentId).orElse(null)?.fileName
        }
        return AnalysisFieldView(
            id = result.id ?: error("Analysis result id is required"),
            fieldKey = result.fieldKey,
            fieldLabel = result.fieldLabel,
            value = result.value,
            sourceAttachmentId = result.sourceAttachmentId,
            sourceFileName = fileName,
            sourceExcerpt = result.sourceExcerpt,
            verified = result.excerptVerified,
            displayOrder = result.displayOrder
        )
    }

    private fun parseLlmResponse(rawResponse: String): List<LlmFieldPayload> {
        val jsonText = extractJsonBlock(rawResponse)
        val root = try {
            objectMapper.readTree(jsonText)
        } catch (ex: Exception) {
            throw AnalysisFailedException("LLM 返回格式无效，请重试", ex)
        }
        val fieldsNode = root.path("fields")
        if (!fieldsNode.isArray || fieldsNode.isEmpty) {
            throw AnalysisFailedException("LLM 未返回有效字段，请重试")
        }

        return fieldsNode.mapNotNull { node ->
            val key = node.path("key").asText(null)?.trim().orEmpty()
            val label = node.path("label").asText(null)?.trim().orEmpty()
            val value = node.path("value").asText(null)?.trim().orEmpty()
            if (key.isBlank() || label.isBlank() || value.isBlank()) {
                null
            } else {
                LlmFieldPayload(
                    key = key,
                    label = label,
                    value = value,
                    sourceFileId = node.path("sourceFileId").asText(null),
                    excerpt = node.path("excerpt").asText(null)
                )
            }
        }.ifEmpty {
            throw AnalysisFailedException("LLM 未返回有效字段，请重试")
        }
    }

    private fun extractJsonBlock(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return trimmed
        }
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (fenceMatch != null) {
            return fenceMatch.groupValues[1].trim()
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }

    private fun parseAttachmentId(sourceFileId: String?): Long? {
        if (sourceFileId.isNullOrBlank()) {
            return null
        }
        val normalized = sourceFileId.trim()
        val numeric = when {
            normalized.startsWith("att_") -> normalized.removePrefix("att_")
            else -> normalized
        }
        return numeric.toLongOrNull()
    }

    private fun normalizeForMatch(text: String): String =
        text.replace("\\s+".toRegex(), " ").trim()

    private data class LlmFieldPayload(
        val key: String,
        val label: String,
        val value: String,
        val sourceFileId: String?,
        val excerpt: String?
    )

    private data class ParsedAnalysisField(
        val key: String,
        val label: String,
        val value: String,
        val sourceAttachmentId: Long?,
        val sourceExcerpt: String?,
        val verified: Boolean
    )

    companion object {
        private const val MAX_FILE_TEXT_CHARS = 120_000

        internal val SYSTEM_PROMPT = """
            You are analyzing academic expert documents. Extract structured profile information from the provided files.

            RULES:
            1. Output valid JSON matching the schema below.
            2. For each field, include "excerpt" — the EXACT substring from the source file that supports this value. Do NOT paraphrase.
            3. If a field cannot be determined, omit it from the output.

            OUTPUT SCHEMA:
            {
              "fields": [
                {
                  "key": "name",
                  "label": "姓名",
                  "value": "extracted value",
                  "sourceFileId": "att_123",
                  "excerpt": "exact substring from source"
                }
              ]
            }

            EXPECTED KEYS (extract if available):
            - name (姓名)
            - nationality (国籍)
            - email (邮箱)
            - phone (电话)
            - current_institution (当前单位)
            - current_position (当前职位)
            - phd_institution (博士院校)
            - phd_field (博士专业)
            - phd_year (博士毕业年份)
            - master_institution (硕士院校)
            - research_areas (研究方向)
            - publications (代表论文，逗号分隔)
            - patents (专利，逗号分隔)
            - awards (获奖，逗号分隔)
            - h_index (h-index)
        """.trimIndent()
    }
}
