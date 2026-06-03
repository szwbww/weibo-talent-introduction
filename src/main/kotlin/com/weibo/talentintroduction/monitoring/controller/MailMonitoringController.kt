package com.weibo.talentintroduction.monitoring.controller

import com.weibo.talentintroduction.monitoring.service.MailMonitoringService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/mail-monitoring")
class MailMonitoringController(
    private val mailMonitoringService: MailMonitoringService
) {
    @GetMapping("/summary")
    fun summary(@RequestParam(required = false) date: LocalDate?): MailMonitoringService.DailySummary =
        mailMonitoringService.summary(date)

    @GetMapping("/introductions")
    fun introductions(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) senderAccountCode: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): IntroductionListResponse =
        mailMonitoringService.listIntroductions(from, to, senderAccountCode, pageSize, pageOffset)

    @GetMapping("/outbound-replies")
    fun outboundReplies(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) triggeredBy: String?,
        @RequestParam(required = false) mailType: String?,
        @RequestParam(required = false) senderAccountCode: String?,
        @RequestParam(required = false) sendStatus: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): OutboundReplyListResponse =
        mailMonitoringService.listOutboundReplies(
            from, to, triggeredBy, mailType, senderAccountCode, sendStatus, pageSize, pageOffset
        )

    @GetMapping("/inbound")
    fun inbound(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) processStatus: String?,
        @RequestParam(required = false) reasonType: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): InboundListResponse =
        mailMonitoringService.listInbound(from, to, processStatus, reasonType, pageSize, pageOffset)

    @GetMapping("/promotions")
    fun promotions(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) promotionStatus: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): PromotionListResponse =
        mailMonitoringService.listPromotions(from, to, promotionStatus, pageSize, pageOffset)

    @PostMapping("/promotions/{id}/retry")
    fun retryPromotion(@PathVariable id: Long): PromotionRow =
        mailMonitoringService.retryPromotion(id)

    @GetMapping("/sender-accounts")
    fun senderAccounts(@RequestParam(required = false) date: LocalDate?): List<SenderAccountHealthRow> =
        mailMonitoringService.senderAccountHealth(date)
}
