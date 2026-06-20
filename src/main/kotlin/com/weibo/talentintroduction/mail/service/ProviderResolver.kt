package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class ProviderResolver {
    fun resolve(email: String?): String {
        val domain = email?.substringAfterLast('@', "")?.lowercase()?.trim().orEmpty()
        if (domain.isBlank()) return "other"
        return when {
            domain.endsWith(".edu") || domain.contains(".edu.") || domain.endsWith(".ac.uk") -> "edu"
            domain in GMAIL -> "gmail"
            domain in OUTLOOK -> "outlook"
            domain in YAHOO -> "yahoo"
            domain in TENCENT -> "tencent"
            domain in NETEASE -> "netease"
            else -> "other"
        }
    }

    companion object {
        private val GMAIL = setOf("gmail.com", "googlemail.com")
        private val OUTLOOK = setOf("outlook.com", "hotmail.com", "live.com", "msn.com")
        private val YAHOO = setOf("yahoo.com", "ymail.com")
        private val TENCENT = setOf("qq.com", "foxmail.com")
        private val NETEASE = setOf("163.com", "126.com", "yeah.net")
    }
}
