package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository.EventRow
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.core.Ordered

@RestController
@RequestMapping("/api/meeting-calendar")
class MeetingCalendarController(
    private val service: MeetingCalendarService
) {
    @GetMapping("/events")
    fun events(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) contactId: Long?,
        @RequestParam(defaultValue = "false") showCancelled: Boolean,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) cursor: String?
    ): MeetingCalendarPageResponse = service.list(from, to, contactId, showCancelled, limit, cursor).let { page ->
        MeetingCalendarPageResponse(page.items.map(::response), page.nextCursor)
    }

    @GetMapping("/events/{id}")
    fun event(@PathVariable id: Long): MeetingCalendarEventResponse = response(service.get(id))

    @GetMapping("/summaries")
    fun summaries(@RequestParam(required = false) contactIds: String?): List<MeetingCalendarSummaryResponse> {
        val ids = contactIds?.trim()?.takeIf { it.isNotEmpty() }?.split(',')?.map {
            it.trim().toLongOrNull() ?: throw IllegalArgumentException("contactIds must contain numbers")
        } ?: emptyList()
        return service.summaries(ids).map { MeetingCalendarSummaryResponse(it.contactId, it.activeCount, it.next?.let(::response)) }
    }

    @PostMapping("/events")
    fun create(@RequestBody request: CreateMeetingCalendarRequest): ResponseEntity<MeetingCalendarEventResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            response(service.createManual(request.contactId, request.startBeijing, request.endBeijing, request.meetingLink, request.note))
        )

    @PutMapping("/events/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdateMeetingCalendarRequest): MeetingCalendarEventResponse =
        response(service.update(id, request.startBeijing, request.endBeijing, request.meetingLink, request.note, request.expectedUpdatedAt))

    @PostMapping("/events/{id}/cancel")
    fun cancel(@PathVariable id: Long, @RequestBody request: CancelMeetingCalendarRequest): MeetingCalendarEventResponse =
        response(service.cancel(id, request.expectedUpdatedAt, request.reason))

    private fun response(row: EventRow): MeetingCalendarEventResponse = MeetingCalendarEventResponse(
        id = row.event.id!!,
        contactId = row.event.expertContactId,
        expertName = row.expertName,
        expertEmail = row.expertEmail,
        sourceMailRecordId = row.event.sourceMailRecordId,
        startUtc = row.event.startsAtUtc.toString(),
        endUtc = row.event.endsAtUtc.toString(),
        meetingLink = row.event.meetingLink,
        note = row.event.note,
        status = row.event.status,
        cancelReason = row.event.cancelReason,
        createdAt = row.event.createdAt.toString(),
        updatedAt = row.event.updatedAt.toString()
    )
}

@RestControllerAdvice(assignableTypes = [MeetingCalendarController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class MeetingCalendarExceptionHandler {
    @ExceptionHandler(MeetingCalendarService.MeetingCalendarConflictException::class)
    fun conflict(ex: MeetingCalendarService.MeetingCalendarConflictException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse("CONFLICT", ex.message ?: "Calendar event conflict", "Conflict"))
}

data class CreateMeetingCalendarRequest(
    val contactId: Long,
    val startBeijing: String,
    val endBeijing: String,
    val meetingLink: String? = null,
    val note: String? = null
)

data class UpdateMeetingCalendarRequest(
    val startBeijing: String,
    val endBeijing: String,
    val meetingLink: String? = null,
    val note: String? = null,
    val expectedUpdatedAt: String
)

data class CancelMeetingCalendarRequest(
    val expectedUpdatedAt: String,
    val reason: String? = null
)

data class MeetingCalendarPageResponse(
    val items: List<MeetingCalendarEventResponse>,
    val nextCursor: String?
)

data class MeetingCalendarSummaryResponse(
    val contactId: Long,
    val activeCount: Int,
    val next: MeetingCalendarEventResponse?
)

data class MeetingCalendarEventResponse(
    val id: Long,
    val contactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val sourceMailRecordId: Long?,
    val startUtc: String,
    val endUtc: String,
    val meetingLink: String?,
    val note: String?,
    val status: String,
    val cancelReason: String?,
    val createdAt: String,
    val updatedAt: String
)
