package com.weibo.talentintroduction.expert.service

import java.security.MessageDigest

object ExpertIdGenerator {
    fun generate(orcidId: String?, email: String?): String = when {
        !orcidId.isNullOrBlank() -> orcidId
        !email.isNullOrBlank() -> generateFromEmail(email)
        else -> error("Cannot generate ID: both orcidId and email are null")
    }

    private fun generateFromEmail(email: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(19)
        return "EMAIL-$hash"
    }
}
