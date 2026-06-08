package com.weibo.talentintroduction.expert.domain

data class EligibilityResult(
    val eligible: Boolean,
    val rejectReasons: List<String> = emptyList()
) {
    companion object {
        fun pass(): EligibilityResult = EligibilityResult(true)
        fun reject(vararg reasons: String): EligibilityResult = EligibilityResult(false, reasons.toList())
    }
}
