package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class UnsubscribeTokenService(
    private val properties: UnsubscribeProperties
) {
    fun enabled(): Boolean = properties.baseUrl.isNotBlank() && properties.secret.isNotBlank()

    fun sign(email: String): String {
        val n = email.trim().lowercase(Locale.ROOT)
        val mac = hmac(n)
        return enc(n) + "." + enc(mac)
    }

    /** 校验并返回归一化邮箱；失败返回 null。 */
    fun verify(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 2) return null
        return try {
            val email = String(dec(parts[0]), Charsets.UTF_8)
            val expected = enc(hmac(email))
            if (!MessageDigest.isEqual(expected.toByteArray(), parts[1].toByteArray())) return null
            email
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun unsubscribeUrl(email: String): String =
        "${properties.baseUrl.trimEnd('/')}/u/unsubscribe?token=${sign(email)}"

    private fun hmac(data: String): ByteArray {
        val key = SecretKeySpec(properties.secret.toByteArray(), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data.toByteArray())
    }

    private fun enc(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    private fun enc(s: String): String = enc(s.toByteArray())

    private fun dec(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
}
