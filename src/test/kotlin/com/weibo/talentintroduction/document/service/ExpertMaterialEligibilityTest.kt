package com.weibo.talentintroduction.document.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class ExpertMaterialEligibilityTest {
    @Test
    fun `classification uses only ASCII trimmed filename and normalized MIME`() {
        for ((name, mime, expected) in cases) {
            assertEquals(expected, ExpertMaterialEligibility.isMaterial(name, mime), "$name / $mime")
        }
    }

    @Test
    fun `classification is independent of default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(false, ExpertMaterialEligibility.isMaterial("SIGNATURE.P7S", null))
            assertEquals(false, ExpertMaterialEligibility.isMaterial("signature.bin", "APPLICATION/PKCS7-SIGNATURE"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        val cases = listOf(
            Triple("smime.p7s", null, false),
            Triple(" SMIME.P7S ", "application/octet-stream", false),
            Triple("cv.p7s", "application/pdf", false),
            Triple("signature.bin", " Application/PKCS7-Signature ; name=foo ", false),
            Triple("signature.bin", "application/x-pkcs7-signature", false),
            Triple("CV.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true),
            Triple("certificate.png", "image/png", true),
            Triple("image001.jpg", "image/jpeg", true),
            Triple("smime.p7m", "application/pkcs7-mime", true),
            Triple("smime.p7s.pdf", "application/pdf", true),
            Triple("file", null, true), Triple("file", "", true), Triple("file", "unknown", true),
            Triple("cv.pdf", "application/pkcs7-signaturé", true),
            Triple("smime.p7s\t", null, true),
            Triple("file", "\tapplication/pkcs7-signature", true),
            Triple("file", "application/pkcs7-signature\n", true)
        )
    }
}
