package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailInboxCursor
import com.weibo.talentintroduction.mail.repository.MailInboxCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class CursorState(
    val uidValidity: Long?,
    val lastUid: Long
)

@Service
class MailInboxCursorService(
    private val repository: MailInboxCursorRepository
) {
    private val log = LoggerFactory.getLogger(MailInboxCursorService::class.java)

    fun get(accountCode: String): CursorState {
        val row = repository.findBySenderAccountCode(accountCode)
        return CursorState(
            uidValidity = row?.uidValidity,
            lastUid = row?.lastUid ?: 0L
        )
    }

    fun resolveStart(stored: CursorState, currentUidValidity: Long): Long {
        if (stored.uidValidity != null && stored.uidValidity != currentUidValidity) {
            log.warn(
                "UIDVALIDITY mismatch: stored={} current={}; rescanning from UID 0",
                stored.uidValidity,
                currentUidValidity
            )
            return 0L
        }
        return stored.lastUid
    }

    fun advance(
        accountCode: String,
        currentUidValidity: Long,
        fetchedUids: List<Long>,
        handledUids: Set<Long>,
        oldStart: Long
    ) {
        if (handledUids.isEmpty() || fetchedUids.isEmpty()) {
            return
        }

        val newLastUid = computeConsecutiveHandledMax(fetchedUids, handledUids, oldStart)
            ?: return

        if (newLastUid <= oldStart) {
            return
        }

        val now = LocalDateTime.now()
        val existing = repository.findBySenderAccountCode(accountCode)
        repository.save(
            if (existing == null) {
                MailInboxCursor(
                    senderAccountCode = accountCode,
                    uidValidity = currentUidValidity,
                    lastUid = newLastUid,
                    updatedAt = now
                )
            } else {
                existing.copy(
                    uidValidity = currentUidValidity,
                    lastUid = newLastUid,
                    updatedAt = now
                )
            }
        )
    }

    internal fun computeConsecutiveHandledMax(
        fetchedUids: List<Long>,
        handledUids: Set<Long>,
        oldStart: Long
    ): Long? {
        var target = oldStart
        for (uid in fetchedUids.sorted()) {
            if (uid in handledUids) {
                target = maxOf(target, uid)
            } else {
                break
            }
        }
        return if (target > oldStart) target else null
    }
}
