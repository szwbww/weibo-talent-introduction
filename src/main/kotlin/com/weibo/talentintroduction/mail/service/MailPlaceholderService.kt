package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class MailPlaceholderService {
    fun variableMetadata(): List<VariableMeta> =
        VARIABLE_LABELS.map { (key, label) ->
            VariableMeta(
                key = key,
                label = label,
                nullable = key in EXPERT_KEYS,
                example = VARIABLE_EXAMPLES[key].orEmpty(),
                esField = ES_FIELD_BY_KEY[key]
            )
        }

    fun placeholderKeysIn(vararg texts: String): List<String> {
        val keys = linkedSetOf<String>()
        texts.forEach { text ->
            PLACEHOLDER_REGEX.findAll(text).forEach { match ->
                keys.add(parsePlaceholderToken(match.groupValues[1]).key)
            }
        }
        return keys.toList()
    }

    fun filterableEsFields(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val esFieldByKey = variableMetadata().associate { it.key to it.esField }
        val fields = linkedSetOf<String>()
        PLACEHOLDER_REGEX.findAll(text).forEach { match ->
            val parsed = parsePlaceholderToken(match.groupValues[1])
            esFieldByKey[parsed.key]?.let { fields.add(it) }
        }
        return fields.toList()
    }

    fun validatePlaceholders(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val metaByKey = variableMetadata().associateBy { it.key }
        val violations = linkedSetOf<String>()
        PLACEHOLDER_REGEX.findAll(text).forEach { match ->
            val token = match.value
            val parsed = parsePlaceholderToken(match.groupValues[1])
            val meta = metaByKey[parsed.key]
            when {
                meta == null -> violations.add(token)
                meta.nullable && parsed.fallback?.trim().isNullOrEmpty() -> violations.add(token)
            }
        }
        return violations.toList()
    }

    fun requireValidPlaceholders(text: String) {
        val violations = validatePlaceholders(text)
        require(violations.isEmpty()) {
            "Invalid placeholders: ${violations.joinToString(", ")}"
        }
    }

    fun unknownPlaceholderTokens(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val metaByKey = variableMetadata().associateBy { it.key }
        val tokens = linkedSetOf<String>()
        PLACEHOLDER_REGEX.findAll(text).forEach { match ->
            val token = match.value
            val parsed = parsePlaceholderToken(match.groupValues[1])
            if (metaByKey[parsed.key] == null) {
                tokens.add(token)
            }
        }
        return tokens.toList()
    }

    fun detectFallbackKeys(text: String, variables: Map<String, String>): List<String> {
        val metaByKey = variableMetadata().associateBy { it.key }
        val keys = linkedSetOf<String>()
        PLACEHOLDER_REGEX.findAll(text).forEach { match ->
            val parsed = parsePlaceholderToken(match.groupValues[1])
            if (parsed.fallback == null) {
                return@forEach
            }
            val meta = metaByKey[parsed.key] ?: return@forEach
            if (meta.nullable && variables[parsed.key].orEmpty().isEmpty()) {
                keys.add(parsed.key)
            }
        }
        return keys.toList()
    }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("""\$\{([^}]*)\}""")

        val EXPERT_KEYS: Set<String> = setOf(
            "expertName",
            "expertFamilyName",
            "researchFields",
            "institution",
            "keyword",
            "expertCountry",
            "employment",
            "hIndex",
            "worksCount",
            "lastPublicationYear",
            "degree",
            "recentWorkTitle",
            "patentTitle"
        )

        val VARIABLE_LABELS: Map<String, String> = mapOf(
            "senderEmail" to "发件邮箱",
            "senderName" to "发件人姓名",
            "senderTitle" to "发件人职位",
            "teamName" to "团队名称",
            "countryName" to "发件人国家",
            "expertName" to "专家姓名",
            "expertFamilyName" to "专家姓氏",
            "researchFields" to "研究方向",
            "institution" to "所属机构",
            "keyword" to "关键词",
            "expertCountry" to "专家国家",
            "employment" to "职位",
            "hIndex" to "H-Index",
            "worksCount" to "论文数",
            "lastPublicationYear" to "最近发表年份",
            "degree" to "学历",
            "recentWorkTitle" to "近期论文标题",
            "patentTitle" to "专利标题",
            "unsubscribeUrl" to "退订链接"
        )

        val ES_FIELD_BY_KEY: Map<String, String?> = mapOf(
            "senderEmail" to null,
            "senderName" to null,
            "senderTitle" to null,
            "teamName" to null,
            "countryName" to null,
            "expertName" to null,
            "expertFamilyName" to "familyNames",
            "researchFields" to "researchFields",
            "institution" to "institution",
            "keyword" to "keyword",
            "expertCountry" to "country",
            "employment" to "employment",
            "hIndex" to "hIndex",
            "worksCount" to "worksCount",
            "lastPublicationYear" to "lastPublicationYear",
            "degree" to "degree",
            "recentWorkTitle" to "recentWorkTitles",
            "patentTitle" to "patentTitles",
            "unsubscribeUrl" to null
        )

        private val VARIABLE_EXAMPLES: Map<String, String> = mapOf(
            "senderEmail" to "chenjj@qftechtalent.com",
            "senderName" to "Chen",
            "senderTitle" to "Customer Care Officer",
            "teamName" to "Qingfei Tech Talent Team",
            "countryName" to "China",
            "expertName" to "Ada Lovelace",
            "expertFamilyName" to "Lovelace",
            "researchFields" to "Machine Learning",
            "institution" to "MIT",
            "keyword" to "AI",
            "expertCountry" to "United Kingdom",
            "employment" to "Professor",
            "hIndex" to "42",
            "worksCount" to "120",
            "lastPublicationYear" to "2025",
            "degree" to "PhD",
            "recentWorkTitle" to "A Study on Neural Networks",
            "patentTitle" to "Method for Data Processing",
            "unsubscribeUrl" to "https://example.com/u/unsubscribe?token=preview"
        )

        private fun parsePlaceholderToken(inner: String): ParsedPlaceholder {
            val pipeIndex = inner.indexOf('|')
            return if (pipeIndex >= 0) {
                ParsedPlaceholder(key = inner.substring(0, pipeIndex), fallback = inner.substring(pipeIndex + 1))
            } else {
                ParsedPlaceholder(key = inner, fallback = null)
            }
        }
    }
}

private data class ParsedPlaceholder(
    val key: String,
    val fallback: String?
)
