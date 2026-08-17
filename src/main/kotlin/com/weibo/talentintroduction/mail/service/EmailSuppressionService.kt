package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.service.ExpertReachabilitySyncService
import com.weibo.talentintroduction.mail.domain.EmailSuppression
import com.weibo.talentintroduction.mail.repository.EmailSuppressionRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Locale

enum class SuppressionSource { INBOUND_REPLY, ONE_CLICK, MAILTO, MANUAL }

@Service
class EmailSuppressionService(
    private val repository: EmailSuppressionRepository,
    private val reachabilitySyncService: ExpertReachabilitySyncService
) {
    private val log = LoggerFactory.getLogger(EmailSuppressionService::class.java)
    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun isSuppressed(email: String): Boolean {
        val n = normalize(email)
        if (n.isBlank()) return false
        return repository.existsByEmail(n)
    }

    /** 幂等：已存在则忽略。返回是否新增。 */
    fun suppress(email: String, source: SuppressionSource, reason: String?): Boolean {
        val n = normalize(email)
        if (n.isBlank() || repository.existsByEmail(n)) return false
        return try {
            repository.save(
                EmailSuppression(
                    email = n,
                    source = source.name,
                    reason = reason?.take(500),
                    createdAt = LocalDateTime.now()
                )
            )
            // I-3-5/IP-3: 新增成功后立即增量写 reachability=BLOCKED_UNSUBSCRIBED；
            // ES 写失败不得回传为退订失败（合规风险），只记 warn，下一轮全量扫描会自愈。
            markBlockedReachability(n)
            true
        } catch (e: DuplicateKeyException) {
            false
        }
    }

    /** I-3-5: 增量可达性写入 fail-open——吞掉全部异常，只记 warn 日志。 */
    private fun markBlockedReachability(normalizedEmail: String) {
        try {
            reachabilitySyncService.markBlockedByEmail(normalizedEmail)
        } catch (e: Exception) {
            log.warn("Failed to mark reachability BLOCKED_UNSUBSCRIBED for email={}", normalizedEmail, e)
        }
    }

    /** 幂等：不存在也不报错。返回是否删除。 */
    fun remove(email: String): Boolean {
        val n = normalize(email)
        if (n.isBlank()) return false
        return repository.deleteByEmail(n) > 0
    }

    fun list(keyword: String?, page: Int, size: Int): SuppressionPage {
        val safeSize = if (size <= 0) 50 else minOf(size, MAX_PAGE_SIZE)
        val safePage = if (page < 0) 0 else page
        val offset = safePage * safeSize
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { normalize(it) }
        val items = if (normalizedKeyword.isNullOrBlank()) {
            repository.findAllOrderByCreatedAtDesc(safeSize, offset)
        } else {
            repository.findByEmailContainingOrderByCreatedAtDesc(normalizedKeyword, safeSize, offset)
        }
        val total = if (normalizedKeyword.isNullOrBlank()) {
            repository.countAll()
        } else {
            repository.countByEmailContaining(normalizedKeyword)
        }
        return SuppressionPage(
            items = items,
            page = safePage,
            size = safeSize,
            total = total
        )
    }

    /** 独立退订关键词判定，不复用 InboundIntentClassifier。 */
    fun looksLikeUnsubscribe(body: String?): Boolean = containsUnsubscribePhrase(body)

    private fun containsUnsubscribePhrase(text: String?): Boolean {
        val b = text?.lowercase(Locale.ROOT) ?: return false
        return UNSUBSCRIBE_PHRASES.any { b.contains(it) }
    }

    /** 主题触发的退订只接受精确相等，禁止 contains。见 plan I-1。 */
    private fun subjectRequestsUnsubscribe(subject: String?): Boolean {
        val s = subject?.trim()?.lowercase(Locale.ROOT) ?: return false
        return s in SUBJECT_UNSUBSCRIBE_PHRASES
    }

    /** 主题优先，其次正文；都不命中返回 null。见 plan I-2。 */
    fun detectUnsubscribeSource(subject: String?, body: String?): SuppressionSource? = when {
        subjectRequestsUnsubscribe(subject) -> SuppressionSource.MAILTO
        containsUnsubscribePhrase(body) -> SuppressionSource.INBOUND_REPLY
        else -> null
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100

        private val UNSUBSCRIBE_PHRASES = listOf(
            "unsubscribe",
            "please remove me",
            "remove me from",
            "stop emailing",
            "opt out",
            "opt-out",
            "取消订阅",
            "退订",
            "不要再发"
        )

        private val SUBJECT_UNSUBSCRIBE_PHRASES = setOf("unsubscribe", "退订", "取消订阅")
    }
}

data class SuppressionPage(
    val items: List<EmailSuppression>,
    val page: Int,
    val size: Int,
    val total: Long
)

/**
 * 收件人已在抑制名单中，拒绝外发。继承 IllegalStateException 以便
 * GlobalExceptionHandler 映射为 400 BAD_REQUEST（见 plan I-3）。
 */
class RecipientSuppressedException(email: String) :
    IllegalStateException("收件人已退订，禁止外发：$email")
