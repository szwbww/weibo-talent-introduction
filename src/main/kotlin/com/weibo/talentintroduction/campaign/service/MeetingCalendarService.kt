package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.CalendarAttachmentCodec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.NoSuchElementException
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent.Companion.ACTIVE

@Service
class MeetingCalendarService(
    private val repository: MeetingCalendarEventRepository,
    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
) {
    data class Page(val items: List<MeetingCalendarEventRepository.EventRow>, val nextCursor: String?)

    @Transactional
    fun createManual(
        contactId: Long,
        startBeijing: String,
        endBeijing: String,
        meetingLink: String?,
        note: String?
    ): MeetingCalendarEventRepository.EventRow {
        requireContact(contactId)
        val input = MeetingCalendarInput(
            startUtc = parseBeijing(startBeijing),
            endUtc = parseBeijing(endBeijing),
            meetingLink = normalizeMeetingLink(meetingLink)
        )
        validateInput(input)
        val now = Instant.now().truncatedTo(MICROS)
        val id = repository.insert(
            MeetingCalendarEvent(
                expertContactId = contactId,
                startsAtUtc = input.startUtc,
                endsAtUtc = input.endUtc,
                meetingLink = input.meetingLink,
                note = normalizeNote(note),
                status = ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        )
        return repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun createFromSentMail(record: MailRecord, input: MeetingCalendarInput): MeetingCalendarEventRepository.EventRow {
        val recordId = record.id ?: throw IllegalArgumentException("Sent mail record id is required")
        require(record.direction == "OUTBOUND") { "Calendar source mail must be OUTBOUND" }
        require(record.mailType == "MANUAL_RICH_REPLY") { "Calendar source mail type must be MANUAL_RICH_REPLY" }
        require(record.sendStatus == "SENT") { "Calendar source mail must be SENT" }
        require(CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) != null) {
            "Calendar source mail must contain a valid calendar attachment"
        }
        requireContact(record.expertContactId)
        validateInput(input.copy(meetingLink = normalizeMeetingLink(input.meetingLink)))

        repository.findBySourceMailRecordId(recordId)?.let { existing ->
            return existingSourceFor(record, existing)
        }

        val now = Instant.now().truncatedTo(MICROS)
        val event = MeetingCalendarEvent(
            expertContactId = record.expertContactId,
            sourceMailRecordId = recordId,
            startsAtUtc = input.startUtc,
            endsAtUtc = input.endUtc,
            meetingLink = normalizeMeetingLink(input.meetingLink),
            status = ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        return try {
            val id = repository.insert(event)
            repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
        } catch (ex: DataIntegrityViolationException) {
            repository.findBySourceMailRecordIdForUpdate(recordId)?.let { existingSourceFor(record, it) } ?: throw ex
        }
    }

    private fun existingSourceFor(
        record: MailRecord,
        existing: MeetingCalendarEventRepository.EventRow
    ): MeetingCalendarEventRepository.EventRow {
        require(existing.event.expertContactId == record.expertContactId) {
            "Calendar source mail belongs to another expert contact"
        }
        return existing
    }

    fun get(id: Long): MeetingCalendarEventRepository.EventRow =
        repository.findById(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")

    fun list(
        from: String?,
        to: String?,
        contactId: Long?,
        showCancelled: Boolean,
        limit: Int,
        cursor: String?
    ): Page {
        require(limit in 1..200) { "limit must be between 1 and 200" }
        require(contactId == null || contactId > 0) { "contactId must be positive" }
        require((from == null) == (to == null)) { "from and to must be provided together" }
        if (contactId == null) require(from != null) { "from and to are required without contactId" }

        val fromInstant = from?.let(::parseInstant)
        val toInstant = to?.let(::parseInstant)
        if (fromInstant != null && toInstant != null) {
            require(toInstant > fromInstant) { "to must be after from" }
            require(Duration.between(fromInstant, toInstant) <= MAX_RANGE) {
                "calendar range must not exceed 62 days"
            }
        }

        val decoded = cursor?.let { decodeCursor(it) }
        val filter = CursorFilter(fromInstant, toInstant, contactId, showCancelled)
        if (decoded != null) require(decoded.filter == filter) { "cursor does not match filters" }
        val rows = repository.list(
            fromUtc = fromInstant?.toUtcLocalDateTime(),
            toUtc = toInstant?.toUtcLocalDateTime(),
            contactId = contactId,
            showCancelled = showCancelled,
            afterStartsAtUtc = decoded?.startsAtUtc?.toUtcLocalDateTime(),
            afterId = decoded?.id,
            limit = limit + 1
        )
        val hasMore = rows.size > limit
        val pageItems = if (hasMore) rows.dropLast(1) else rows
        val next = if (hasMore) {
            val last = pageItems.last()
            encodeCursor(filter, last.event.startsAtUtc, last.event.id!!)
        } else null
        return Page(pageItems, next)
    }

    fun summaries(contactIds: List<Long>): List<MeetingCalendarSummary> {
        require(contactIds.size <= 100) { "at most 100 contactIds are allowed" }
        require(contactIds.all { it > 0 }) { "contactIds must be positive" }
        if (contactIds.isEmpty()) return emptyList()
        val now = Instant.now()
        val rows = repository.findActiveByContactIds(contactIds)
        val byContact = rows.groupBy { it.event.expertContactId }
        return contactIds.map { contactId ->
            val events = byContact[contactId].orEmpty()
            val future = events.filter { it.event.startsAtUtc >= now }.minWithOrNull(EVENT_ORDER)
            val next = future ?: events.filter { it.event.startsAtUtc < now }.maxWithOrNull(EVENT_ORDER)
            MeetingCalendarSummary(contactId, events.size, next)
        }
    }

    @Transactional
    fun update(
        id: Long,
        startBeijing: String,
        endBeijing: String,
        meetingLink: String?,
        note: String?,
        expectedUpdatedAt: String
    ): MeetingCalendarEventRepository.EventRow {
        val existing = locked(id)
        val expected = parseVersion(expectedUpdatedAt)
        requireVersion(existing, expected)
        if (existing.event.status == MeetingCalendarEvent.CANCELLED) {
            throw MeetingCalendarConflictException("Cancelled calendar events cannot be rescheduled")
        }
        val input = MeetingCalendarInput(parseBeijing(startBeijing), parseBeijing(endBeijing), normalizeMeetingLink(meetingLink))
        validateInput(input)
        val normalizedNote = normalizeNote(note)
        if (existing.event.startsAtUtc == input.startUtc && existing.event.endsAtUtc == input.endUtc &&
            existing.event.meetingLink == input.meetingLink && existing.event.note == normalizedNote
        ) return existing
        val updatedAt = nextVersion(existing.event.updatedAt)
        val changed = repository.updateMutable(
            id, expected.toUtcLocalDateTime(), input.startUtc.toUtcLocalDateTime(), input.endUtc.toUtcLocalDateTime(),
            input.meetingLink, normalizedNote, updatedAt.toUtcLocalDateTime()
        )
        if (changed != 1) throw MeetingCalendarConflictException("Calendar event was changed by another client")
        return get(id)
    }

    @Transactional
    fun cancel(id: Long, expectedUpdatedAt: String, reason: String?): MeetingCalendarEventRepository.EventRow {
        val existing = locked(id)
        if (existing.event.status == MeetingCalendarEvent.CANCELLED) return existing
        val expected = parseVersion(expectedUpdatedAt)
        requireVersion(existing, expected)
        val updatedAt = nextVersion(existing.event.updatedAt)
        val changed = repository.cancel(
            id, expected.toUtcLocalDateTime(), normalizeCancelReason(reason), updatedAt.toUtcLocalDateTime()
        )
        if (changed != 1) throw MeetingCalendarConflictException("Calendar event was changed by another client")
        return get(id)
    }

    private fun locked(id: Long) =
        repository.findByIdForUpdate(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")

    private fun requireContact(contactId: Long) {
        require(contactId > 0) { "contactId must be positive" }
        if (!expertContactRepository.existsById(contactId)) {
            throw NoSuchElementException("Expert contact not found: $contactId")
        }
    }

    private fun validateInput(input: MeetingCalendarInput) {
        require(input.endUtc > input.startUtc) { "end must be after start" }
        validateMeetingLink(input.meetingLink)
    }

    private fun validateMeetingLink(value: String?) {
        if (value == null) return
        require(value.length <= 1024) { "meetingLink must be at most 1024 characters" }
        val uri = try { URI(value) } catch (ex: Exception) {
            throw IllegalArgumentException("meetingLink must be an http or https URL")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "meetingLink must be an http or https URL"
        }
        require(!uri.host.isNullOrBlank()) { "meetingLink must be an http or https URL" }
    }

    private fun normalizeMeetingLink(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    private fun normalizeNote(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
        require(it.length <= 200) { "note must be at most 200 characters" }
    }
    private fun normalizeCancelReason(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
        require(it.length <= 200) { "reason must be at most 200 characters" }
    }

    private fun parseBeijing(value: String): Instant = try {
        LocalDateTime.parse(value, LOCAL_FORMATTER).atZone(SHANGHAI).toInstant()
    } catch (ex: Exception) {
        throw IllegalArgumentException("meeting time must use yyyy-MM-dd'T'HH:mm")
    }

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (ex: Exception) {
        throw IllegalArgumentException("from and to must be ISO instants")
    }

    private fun parseVersion(value: String): Instant = parseInstant(value)

    private fun requireVersion(row: MeetingCalendarEventRepository.EventRow, expected: Instant) {
        if (row.event.updatedAt != expected) throw MeetingCalendarConflictException("Calendar event version is stale")
    }

    private fun nextVersion(previous: Instant): Instant {
        val now = Instant.now().truncatedTo(MICROS)
        val minimum = previous.plus(1, ChronoUnit.MICROS)
        return if (now > minimum) now else minimum
    }

    private data class CursorFilter(
        val from: Instant?, val to: Instant?, val contactId: Long?, val showCancelled: Boolean
    )
    private data class Cursor(val filter: CursorFilter, val startsAtUtc: Instant, val id: Long)

    private fun encodeCursor(filter: CursorFilter, startsAtUtc: Instant, id: Long): String {
        val raw = listOf(
            "1", filter.from?.toString().orEmpty(), filter.to?.toString().orEmpty(),
            filter.contactId?.toString().orEmpty(), filter.showCancelled.toString(), startsAtUtc.toString(), id.toString()
        ).joinToString("|")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    private fun decodeCursor(value: String): Cursor {
        val parts = try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8).split('|')
        } catch (ex: Exception) {
            throw IllegalArgumentException("invalid cursor")
        }
        require(parts.size == 7 && parts[0] == "1") { "invalid cursor" }
        return try {
            Cursor(
                CursorFilter(parts[1].takeIf { it.isNotEmpty() }?.let(::parseInstant), parts[2].takeIf { it.isNotEmpty() }?.let(::parseInstant),
                    parts[3].takeIf { it.isNotEmpty() }?.toLong(), parts[4].toBooleanStrict()),
                parseInstant(parts[5]), parts[6].toLong()
            )
        } catch (ex: Exception) {
            throw IllegalArgumentException("invalid cursor")
        }
    }

    data class MeetingCalendarSummary(
        val contactId: Long,
        val activeCount: Int,
        val next: MeetingCalendarEventRepository.EventRow?
    )

    class MeetingCalendarConflictException(message: String) : RuntimeException(message)

    companion object {
        private val SHANGHAI = ZoneId.of("Asia/Shanghai")
        private val LOCAL_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
            .withResolverStyle(ResolverStyle.STRICT)
        private val MAX_RANGE = Duration.ofDays(62)
        private val MICROS = ChronoUnit.MICROS
        private val EVENT_ORDER = compareBy<MeetingCalendarEventRepository.EventRow> { it.event.startsAtUtc }
            .thenBy { it.event.id }
    }
}

private fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
