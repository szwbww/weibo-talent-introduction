package com.weibo.talentintroduction.mail.service

internal object MailMessageIdNormalizer {
    fun normalize(raw: String?): String =
        raw?.trim()?.removeSurrounding("<", ">")?.trim().orEmpty()
}
