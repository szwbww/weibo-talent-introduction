package com.weibo.talentintroduction.expert.service

import java.util.Locale

object ExpertIdNormalizer {
    fun normalize(id: String): String {
        val trimmed = id.trim()
        return if (trimmed.startsWith("EMAIL-", ignoreCase = true)) {
            "EMAIL-" + trimmed.substringAfter("-").lowercase(Locale.ROOT)
        } else {
            trimmed.uppercase(Locale.ROOT)
        }
    }
}
