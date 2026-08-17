package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertReachability
import com.weibo.talentintroduction.mail.service.ProviderResolver
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * 专家可达性判定的唯一实现（I-2-1）：四档口径表见计划 02。
 *
 * 纯函数（I-2-2）：不注入数据源与时钟，唯一依赖是 [ProviderResolver]
 * （其 resolve() 为纯字符串运算，无 IO）。
 *
 * BLOCKED 短路优先于一切（I-2-4）；emailSource 缺失返回 null = 未知档（I-2-5）。
 */
@Component
class ExpertReachabilityClassifier(
    private val providerResolver: ProviderResolver
) {
    /**
     * @return BLOCKED/HIGH/LOW 四档之一；null 表示未知档（I-2-3）。
     */
    fun classify(
        profile: ExpertProfile,
        suppressedEmails: Set<String>,
        hardBouncedOrcids: Set<String>
    ): ExpertReachability? {
        // I-2-4: BLOCKED 判定第一段，命中立即返回；退订优先于硬退。
        profile.email?.let { email ->
            val normalized = normalizeEmail(email)
            if (normalized.isNotBlank() && suppressedEmails.contains(normalized)) {
                return ExpertReachability.BLOCKED_UNSUBSCRIBED
            }
        }
        val normalizedOrcid = ExpertIdNormalizer.normalize(profile.orcidId)
        if (hardBouncedOrcids.contains(normalizedOrcid)) {
            return ExpertReachability.BLOCKED_BOUNCED
        }
        // I-2-5: 信息缺失返回 null，不返回 LOW。
        val emailSource = profile.emailSource
        if (emailSource.isNullOrBlank()) {
            return null
        }
        return if (emailSource == "PAPER_FULLTEXT" && !isConsumerEmail(profile.email)) {
            ExpertReachability.HIGH
        } else {
            ExpertReachability.LOW
        }
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun isConsumerEmail(email: String?): Boolean =
        providerResolver.resolve(email) in CONSUMER_PROVIDERS

    companion object {
        /** 消费级邮箱域名（A-3 反向使用约束；只做负向判据，不进配置文件）。 */
        val CONSUMER_PROVIDERS = setOf("gmail", "outlook", "yahoo", "tencent", "netease")
    }
}
