package com.weibo.talentintroduction.monitoring.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.CountryCount
import com.weibo.talentintroduction.mail.repository.DomainBounceCount
import com.weibo.talentintroduction.mail.repository.DomainCount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalDateTime

class MailMonitoringServiceTest {
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val promotionRepository = Mockito.mock(ExpertApplicationPromotionRepository::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val dateRangeResolver = MonitoringDateRangeResolver()
    private val providerResolver = ProviderResolver()

    private val service = MailMonitoringService(
        mailRecordRepository = mailRecordRepository,
        bounceRecordRepository = bounceRecordRepository,
        inboundMailProcessingRepository = inboundMailProcessingRepository,
        promotionRepository = promotionRepository,
        mailSenderAccountRepository = mailSenderAccountRepository,
        expertContactRepository = expertContactRepository,
        qaRuleRepository = qaRuleRepository,
        expertIndexWriterService = expertIndexWriterService,
        dateRangeResolver = dateRangeResolver,
        providerResolver = providerResolver
    )

    private val date = LocalDate.of(2026, 6, 29)
    private lateinit var from: LocalDateTime
    private lateinit var to: LocalDateTime

    @BeforeEach
    fun setUp() {
        val range = dateRangeResolver.resolveDay(date)
        from = range.first
        to = range.second
    }

    @Test
    fun `providerDistribution folds domains via ProviderResolver`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroSentByDomain(from, to)).thenReturn(
            listOf(
                DomainCount("gmail.com", 10),
                DomainCount("hotmail.com", 5),
                DomainCount("mit.edu", 3),
                DomainCount("qq.com", 2),
                DomainCount("unknown.xx", 1)
            )
        )
        Mockito.`when`(mailRecordRepository.aggregateInboundByDomain(from, to)).thenReturn(
            listOf(
                DomainCount("gmail.com", 4),
                DomainCount("hotmail.com", 1)
            )
        )
        Mockito.`when`(bounceRecordRepository.aggregateBouncesByDomain(from, to)).thenReturn(
            listOf(
                DomainBounceCount("gmail.com", hardCount = 2, softCount = 1),
                DomainBounceCount(null, hardCount = 1, softCount = 0)
            )
        )

        val rows = service.providerDistribution(date).associateBy { it.provider }

        assertEquals(10, rows.getValue("gmail").sentCount)
        assertEquals(4, rows.getValue("gmail").repliedCount)
        assertEquals(0.4, rows.getValue("gmail").replyRate)
        assertEquals(2, rows.getValue("gmail").hardBounceCount)
        assertEquals(1, rows.getValue("gmail").softBounceCount)
        assertEquals(5, rows.getValue("outlook").sentCount)
        assertEquals(1, rows.getValue("outlook").repliedCount)
        assertEquals(3, rows.getValue("edu").sentCount)
        assertEquals(2, rows.getValue("tencent").sentCount)
        assertEquals(1, rows.getValue("other").sentCount)
        assertEquals(1, rows.getValue("other").hardBounceCount)
        assertEquals(listOf("gmail", "outlook", "yahoo", "edu", "tencent", "netease", "other"), rows.keys.toList())
    }

    @Test
    fun `regionDistribution folds countries via CountryContinentMapping in allRegions order`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroSentByCountry(from, to)).thenReturn(
            listOf(
                CountryCount("Germany", 8),
                CountryCount("US", 6),
                CountryCount(null, 2)
            )
        )
        Mockito.`when`(mailRecordRepository.aggregateInboundByCountry(from, to)).thenReturn(
            listOf(
                CountryCount("Germany", 3),
                CountryCount("US", 1)
            )
        )
        Mockito.`when`(promotionRepository.aggregateSuccessByCountry(from, to)).thenReturn(
            listOf(CountryCount("US", 2))
        )

        val rows = service.regionDistribution(date)

        assertEquals(CountryContinentMapping.allRegions(), rows.map { it.region })
        val byRegion = rows.associateBy { it.region }
        assertEquals(8, byRegion.getValue(CountryContinentMapping.REGION_EUROPE).sentCount)
        assertEquals(3, byRegion.getValue(CountryContinentMapping.REGION_EUROPE).repliedCount)
        assertEquals(6, byRegion.getValue(CountryContinentMapping.REGION_NORTH_AMERICA).sentCount)
        assertEquals(2, byRegion.getValue(CountryContinentMapping.REGION_NORTH_AMERICA).promotionCount)
        assertEquals(2, byRegion.getValue(CountryContinentMapping.REGION_OTHER).sentCount)
    }

    @Test
    fun `distribution sent totals align with summary introductions`() {
        Mockito.`when`(mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", from, to)).thenReturn(21)
        Mockito.`when`(mailRecordRepository.aggregateIntroSentByDomain(from, to)).thenReturn(
            listOf(DomainCount("gmail.com", 13), DomainCount("qq.com", 8))
        )
        Mockito.`when`(mailRecordRepository.aggregateInboundByDomain(from, to)).thenReturn(emptyList())
        Mockito.`when`(bounceRecordRepository.aggregateBouncesByDomain(from, to)).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.aggregateIntroSentByCountry(from, to)).thenReturn(
            listOf(CountryCount("US", 21))
        )
        Mockito.`when`(mailRecordRepository.aggregateInboundByCountry(from, to)).thenReturn(emptyList())
        Mockito.`when`(promotionRepository.aggregateSuccessByCountry(from, to)).thenReturn(emptyList())

        val summary = service.summary(date)
        val providerTotal = service.providerDistribution(date).sumOf { it.sentCount }
        val regionTotal = service.regionDistribution(date).sumOf { it.sentCount }

        assertEquals(21, summary.introductions)
        assertEquals(summary.introductions, providerTotal)
        assertEquals(summary.introductions, regionTotal)
    }

    @Test
    fun `providerDistribution separates hard and soft bounces by domain bucket`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroSentByDomain(from, to)).thenReturn(
            listOf(DomainCount("yahoo.com", 4))
        )
        Mockito.`when`(mailRecordRepository.aggregateInboundByDomain(from, to)).thenReturn(emptyList())
        Mockito.`when`(bounceRecordRepository.aggregateBouncesByDomain(from, to)).thenReturn(
            listOf(
                DomainBounceCount("yahoo.com", hardCount = 1, softCount = 2),
                DomainBounceCount("gmail.com", hardCount = 0, softCount = 5)
            )
        )

        val rows = service.providerDistribution(date).associateBy { it.provider }

        assertEquals(1, rows.getValue("yahoo").hardBounceCount)
        assertEquals(2, rows.getValue("yahoo").softBounceCount)
        assertEquals(0, rows.getValue("gmail").hardBounceCount)
        assertEquals(5, rows.getValue("gmail").softBounceCount)
    }
}
