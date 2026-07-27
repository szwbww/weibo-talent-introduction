package com.weibo.talentintroduction.llm.service

enum class AiReplyValidationAttempt { INITIAL, REPAIR }

enum class AiReplyValidationStage { STRUCTURE, CLAIM, TRUST, ACTION }

data class AiReplyValidationIssue(
    val stage: AiReplyValidationStage,
    val code: String,
    val claimKey: String? = null
)

data class AiReplyValidationDiagnostic(
    val attempt: AiReplyValidationAttempt,
    val stage: AiReplyValidationStage,
    val code: String,
    val claimKey: String? = null
)

data class AiReplyValidationDiagnostics(
    val items: List<AiReplyValidationDiagnostic> = emptyList(),
    val total: Int = 0,
    val truncated: Boolean = false
) {
    companion object {
        const val MAX_ITEMS = 20
        const val MAX_CLAIM_KEY_LENGTH = 120

        fun from(
            initial: List<AiReplyValidationDiagnostic> = emptyList(),
            repair: List<AiReplyValidationDiagnostic> = emptyList()
        ): AiReplyValidationDiagnostics {
            val seen = linkedSetOf<String>()
            val all = (initial + repair).filter { diagnostic ->
                val key = listOf(
                    diagnostic.attempt.name,
                    diagnostic.stage.name,
                    diagnostic.code,
                    diagnostic.claimKey.orEmpty()
                ).joinToString("\u0000")
                seen.add(key)
            }
            return AiReplyValidationDiagnostics(
                items = all.take(MAX_ITEMS).map { it.copy(claimKey = it.claimKey?.take(MAX_CLAIM_KEY_LENGTH)) },
                total = all.size,
                truncated = all.size > MAX_ITEMS
            )
        }
    }
}

object AiReplyValidationCodes {
    const val JSON_INVALID = "AI_REPLY_STRUCTURE_JSON_INVALID"
    const val TOP_LEVEL_FIELDS_INVALID = "AI_REPLY_STRUCTURE_TOP_LEVEL_FIELDS_INVALID"
    const val CLAIMS_INVALID = "AI_REPLY_STRUCTURE_CLAIMS_INVALID"
    const val CLAIM_FIELDS_INVALID = "AI_REPLY_STRUCTURE_CLAIM_FIELDS_INVALID"
    const val CLAIM_KEY_DUPLICATE = "AI_REPLY_STRUCTURE_CLAIM_KEY_DUPLICATE"
    const val CLAIM_KEY_UNKNOWN = "AI_REPLY_STRUCTURE_CLAIM_KEY_UNKNOWN"
    const val CLAIM_SET_MISMATCH = "AI_REPLY_STRUCTURE_CLAIM_SET_MISMATCH"
    const val CLAIM_TEXT_INVALID = "AI_REPLY_STRUCTURE_CLAIM_TEXT_INVALID"
    const val ACTION_TEXT_INVALID = "AI_REPLY_ACTION_TEXT_INVALID"
    const val ACTION_NOT_ALLOWED = "AI_REPLY_ACTION_NOT_ALLOWED"
    const val ACTION_BODY_MISMATCH = "AI_REPLY_ACTION_BODY_MISMATCH"
}
