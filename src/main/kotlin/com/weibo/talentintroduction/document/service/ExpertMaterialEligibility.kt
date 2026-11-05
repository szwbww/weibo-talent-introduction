package com.weibo.talentintroduction.document.service

import java.util.Locale

/** Metadata-only classification shared by material readers and admission paths. */
object ExpertMaterialEligibility {
    const val ERROR_MESSAGE = "所选文件包含邮件数字签名，不属于专家材料"

    // Fixed attachment alias only; BINARY prevents accent-insensitive collation matches.
    const val MATERIAL_SQL = """(
        BINARY LOWER(TRIM(COALESCE(a.file_name, ''))) NOT LIKE '%.p7s'
        AND BINARY LOWER(TRIM(SUBSTRING_INDEX(COALESCE(a.content_type, ''), ';', 1)))
            NOT IN ('application/pkcs7-signature', 'application/x-pkcs7-signature')
    )"""

    fun isMaterial(fileName: String, contentType: String?): Boolean {
        val name = fileName.trim(' ').lowercase(Locale.ROOT)
        val mime = contentType.orEmpty().substringBefore(';').trim(' ').lowercase(Locale.ROOT)
        return !name.endsWith(".p7s") &&
            mime != "application/pkcs7-signature" && mime != "application/x-pkcs7-signature"
    }
}
