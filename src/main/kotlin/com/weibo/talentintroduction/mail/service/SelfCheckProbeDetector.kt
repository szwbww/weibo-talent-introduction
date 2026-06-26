package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class SelfCheckProbeDetector {
    fun isSelfCheckProbe(from: String?, subject: String?, accountEmail: String): Boolean {
        val compactSubject = subject?.replace(" ", "") ?: return false
        if (!compactSubject.startsWith("[self-check]", ignoreCase = true)) return false
        val normalizedFrom = from?.trim()?.lowercase() ?: return false
        return normalizedFrom == accountEmail.trim().lowercase()
    }
}
