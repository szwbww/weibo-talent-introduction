package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.domain.UnsubscribeToken
import com.weibo.talentintroduction.mail.repository.UnsubscribeTokenRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class UnsubscribeTokenService(
    private val properties: UnsubscribeProperties,
    private val repository: UnsubscribeTokenRepository? = null
) {
    private val secureRandom = SecureRandom()

    fun enabled(): Boolean =
        if (repository != null) properties.baseUrl.isNotBlank()
        else properties.baseUrl.isNotBlank() && properties.secret.isNotBlank()

    fun sign(email: String): String {
        val n = email.trim().lowercase(Locale.ROOT)
        val repo = repository ?: return legacySign(n)
        repo.findByEmail(n)?.let { return it.token }
        val token = newToken()
        return try {
            repo.save(UnsubscribeToken(email = n, token = token, createdAt = LocalDateTime.now())).token
        } catch (e: DuplicateKeyException) {
            repo.findByEmail(n)?.token ?: throw e
        }
    }

    /** 校验并返回归一化邮箱；失败返回 null。 */
    fun verify(token: String): String? {
        repository?.findByToken(token)?.let { return it.email }
        return verifyLegacy(token)
    }

    fun unsubscribeUrl(email: String): String =
        "${properties.baseUrl.trimEnd('/')}/u/unsubscribe?token=${sign(email)}"

    private fun legacySign(normalizedEmail: String): String =
        enc(normalizedEmail) + "." + enc(hmac(normalizedEmail))

    private fun verifyLegacy(token: String): String? {
        if (properties.secret.isBlank()) return null          // I-5：禁止空 key HMAC
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

    private fun newToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hmac(data: String): ByteArray {
        val key = SecretKeySpec(properties.secret.toByteArray(), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data.toByteArray())
    }

    private fun enc(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    private fun enc(s: String): String = enc(s.toByteArray())

    private fun dec(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
}
