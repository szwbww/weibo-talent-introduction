package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MeetingConfirmationService
import com.weibo.talentintroduction.mail.service.MeetingOptionsResponse
import com.weibo.talentintroduction.mail.service.MeetingPreviewRequest
import com.weibo.talentintroduction.mail.service.MeetingPreviewResponse
import com.weibo.talentintroduction.mail.service.MeetingTimeZoneOption
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 专家会议确认 · 只读接口（fast-p 01）：options / time-zones / preview。
 *
 * 三个入口均沿既有 AuthInterceptor（/api 前缀路径），无登录豁免；只读，不触发任何
 * SMTP/写库（I-1）。NoSuchElementException → 404、IllegalArgumentException
 * → 400 由 GlobalExceptionHandler 统一映射；本控制器不抛会落入通用 500 的
 * ResponseStatusException。
 */
@RestController
@RequestMapping("/api/mail")
class MeetingConfirmationController(
    private val meetingConfirmationService: MeetingConfirmationService
) {

    @GetMapping("/unmatched-inbound/{processingId}/meeting-confirmation/options")
    fun options(
        @PathVariable processingId: Long,
        @RequestParam("contactId") contactId: Long?,
        @RequestParam("senderAccountCode") senderAccountCode: String?
    ): MeetingOptionsResponse {
        requireNotNull(contactId) { "contactId is required" }
        return meetingConfirmationService.options(processingId, contactId, senderAccountCode)
    }

    @GetMapping("/meeting-confirmation/time-zones")
    fun timeZones(
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): List<MeetingTimeZoneOption> {
        requireNotNull(date) { "date is required, ISO-8601 e.g. 2026-09-11" }
        return meetingConfirmationService.timeZones(date)
    }

    @PostMapping("/unmatched-inbound/{processingId}/meeting-confirmation/preview")
    fun preview(
        @PathVariable processingId: Long,
        @RequestBody request: MeetingPreviewRequest
    ): MeetingPreviewResponse =
        meetingConfirmationService.preview(processingId, request)
}
