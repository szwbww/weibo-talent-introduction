package com.weibo.talentintroduction.monitoring.controller

import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.monitoring.service.MailMonitoringService
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import com.weibo.talentintroduction.postmaster.service.PostmasterDataCollector
import com.weibo.talentintroduction.postmaster.service.PostmasterOAuthService
import com.weibo.talentintroduction.postmaster.service.ReputationAutoPauseService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/mail-monitoring")
class MailMonitoringController(
    private val mailMonitoringService: MailMonitoringService,
    private val domainReputationHistoryRepository: DomainReputationHistoryRepository,
    // PostmasterProperties 由 RestTemplateConfig 无条件注册，可直接注入；
    // 但下面两个 service 带 @ConditionalOnProperty，未启用时 bean 不存在，
    // 沿用项目既有的 ObjectProvider 可选依赖模式，避免拖垮整个监控 Controller。
    private val postmasterProperties: PostmasterProperties,
    private val postmasterOAuthService: PostmasterOAuthService,
    private val postmasterDataCollector: ObjectProvider<PostmasterDataCollector>,
    private val reputationAutoPauseService: ObjectProvider<ReputationAutoPauseService>
) {
    private val log = LoggerFactory.getLogger(MailMonitoringController::class.java)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): MailMonitoringService.DailySummary =
        mailMonitoringService.summary(from, to)

    @GetMapping("/introductions")
    fun introductions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) senderAccountCode: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): IntroductionListResponse =
        mailMonitoringService.listIntroductions(from, to, senderAccountCode, pageSize, pageOffset)

    @GetMapping("/outbound-replies")
    fun outboundReplies(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
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
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) processStatus: String?,
        @RequestParam(required = false) reasonType: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): InboundListResponse =
        mailMonitoringService.listInbound(from, to, processStatus, reasonType, pageSize, pageOffset)

    @GetMapping("/promotions")
    fun promotions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) promotionStatus: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): PromotionListResponse =
        mailMonitoringService.listPromotions(from, to, promotionStatus, pageSize, pageOffset)

    @PostMapping("/promotions/{id}/retry")
    fun retryPromotion(@PathVariable id: Long): PromotionRow =
        mailMonitoringService.retryPromotion(id)

    @GetMapping("/sender-accounts")
    fun senderAccounts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?): List<SenderAccountHealthRow> =
        mailMonitoringService.senderAccountHealth(date)

    @GetMapping("/bounce-stats")
    fun bounceStats(
        @RequestParam accountCode: String,
        @RequestParam(required = false, defaultValue = "7") days: Int
    ): BounceStatsResponse =
        mailMonitoringService.getBounceStats(accountCode, days)

    @GetMapping("/provider-distribution")
    fun providerDistribution(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): ProviderDistributionResponse =
        mailMonitoringService.providerDistribution(from, to)

    @GetMapping("/region-distribution")
    fun regionDistribution(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): List<RegionStatRow> =
        mailMonitoringService.regionDistribution(from, to)

    @GetMapping("/reputation-history")
    fun reputationHistory(
        @RequestParam(required = false) domain: String?,
        @RequestParam(required = false, defaultValue = "30") days: Int
    ): ReputationHistoryResponse {
        val availableDomains = domainReputationHistoryRepository.findDistinctDomains()
        val selectedDomain = domain?.takeIf { it.isNotBlank() }
            ?: availableDomains.firstOrNull()
        val history = selectedDomain?.let {
            domainReputationHistoryRepository.findByDomainOrderByReportDateDesc(it, days.coerceIn(1, 200))
                .sortedBy { row -> row.reportDate }
                .map { row ->
                    ReputationHistoryRow(
                        date = row.reportDate.toString(),
                        spamRate = row.spamRate,
                        domainReputation = row.domainReputation,
                        spfSuccessRate = row.spfSuccessRate,
                        dkimSuccessRate = row.dkimSuccessRate,
                        dmarcSuccessRate = row.dmarcSuccessRate
                    )
                }
        } ?: emptyList()
        return ReputationHistoryResponse(
            domain = selectedDomain,
            domains = availableDomains,
            history = history
        )
    }

    @GetMapping("/postmaster/oauth/status")
    fun postmasterOAuthStatus(): PostmasterOAuthStatusResponse = PostmasterOAuthStatusResponse(
        clientConfigured = postmasterOAuthService.clientConfigured(),
        authorized = postmasterOAuthService.authorized()
    )

    @GetMapping("/postmaster/oauth/start")
    fun startPostmasterOAuth(request: HttpServletRequest, response: HttpServletResponse) {
        if (!postmasterOAuthService.clientConfigured()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Postmaster OAuth 客户端未配置")
            return
        }
        val state = UUID.randomUUID().toString()
        request.getSession(true).setAttribute(OAUTH_STATE_SESSION_KEY, state)
        response.sendRedirect(postmasterOAuthService.authorizationUrl(state))
    }

    @GetMapping("/postmaster/oauth/callback")
    fun completePostmasterOAuth(
        request: HttpServletRequest,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> {
        if (!error.isNullOrBlank()) {
            return ResponseEntity.badRequest().body("Google OAuth 授权未完成")
        }
        val session = request.getSession(false)
        val expectedState = session?.getAttribute(OAUTH_STATE_SESSION_KEY) as? String
        if (!PostmasterOAuthService.sameState(expectedState, state)) {
            return ResponseEntity.badRequest().body("OAuth state 校验失败，请重新开始授权")
        }
        session.removeAttribute(OAUTH_STATE_SESSION_KEY)
        if (code.isNullOrBlank()) {
            return ResponseEntity.badRequest().body("缺少 OAuth authorization code")
        }
        return try {
            postmasterOAuthService.exchangeCode(code)
            ResponseEntity.ok("Postmaster OAuth 授权成功，可以返回监控页面")
        } catch (ex: Exception) {
            log.error("Postmaster OAuth token exchange failed", ex)
            ResponseEntity.internalServerError().body("Postmaster OAuth 授权失败，请查看应用日志")
        }
    }

    /**
     * 手动触发一次 Postmaster 采集，用于接入后立即验证，无需等待每日 cron。
     * 默认采集昨天的数据（Postmaster 数据本身有延迟，当天通常查不到）。
     */
    @PostMapping("/postmaster/collect")
    fun collectPostmaster(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "false") skipAutoPause: Boolean
    ): PostmasterCollectResponse {
        val collector = postmasterDataCollector.getIfAvailable()
            ?: return PostmasterCollectResponse(
                triggered = false,
                reportDate = null,
                domains = emptyList(),
                message = "Postmaster 未启用，请设置 talent-introduction.postmaster.enabled=true（POSTMASTER_ENABLED）"
            )
        val domains = postmasterProperties.domains
        if (domains.isEmpty()) {
            return PostmasterCollectResponse(
                triggered = false,
                reportDate = null,
                domains = emptyList(),
                message = "未配置任何域名，请设置 talent-introduction.postmaster.domains（POSTMASTER_DOMAINS）"
            )
        }
        if (!postmasterOAuthService.authorized()) {
            return PostmasterCollectResponse(
                triggered = false,
                reportDate = null,
                domains = domains,
                message = "Postmaster OAuth 尚未授权，请先访问 /api/mail-monitoring/postmaster/oauth/start"
            )
        }
        val reportDate = date ?: LocalDate.now().minusDays(1)
        collector.collect(reportDate)
        if (!skipAutoPause) {
            reputationAutoPauseService.getIfAvailable()?.checkAndAct()
        }
        return PostmasterCollectResponse(
            triggered = true,
            reportDate = reportDate.toString(),
            domains = domains,
            message = "采集完成，请调用 /api/mail-monitoring/reputation-history 查看结果；" +
                "若指标为空，多为发信量未达 Postmaster 阈值，属正常情况"
        )
    }

    companion object {
        private const val OAUTH_STATE_SESSION_KEY = "postmaster.oauth.state"
    }
}
