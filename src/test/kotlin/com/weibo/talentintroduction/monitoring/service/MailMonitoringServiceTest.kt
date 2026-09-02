package com.weibo.talentintroduction.monitoring.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.CountryCohortStat
import com.weibo.talentintroduction.mail.repository.CountryCount
import com.weibo.talentintroduction.mail.repository.DomainCohortStat
import com.weibo.talentintroduction.mail.repository.DomainUndeliveredCount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun cohortDomain(domain: String?, cohort: Long, replied: Long = 0, matureCohort: Long = 0, matureReplied: Long = 0) =
        DomainCohortStat(domain, cohort, replied, matureCohort, matureReplied)

    private fun cohortCountry(country: String?, cohort: Long, replied: Long = 0, matureCohort: Long = 0, matureReplied: Long = 0) =
        CountryCohortStat(country, cohort, replied, matureCohort, matureReplied)

    @Test
    fun `providerDistribution folds domains via ProviderResolver`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(
                cohortDomain("gmail.com", 10, replied = 4, matureCohort = 8, matureReplied = 3),
                cohortDomain("hotmail.com", 5, replied = 1, matureCohort = 5, matureReplied = 1),
                cohortDomain("mit.edu", 3, replied = 0, matureCohort = 3, matureReplied = 0),
                cohortDomain("qq.com", 2),
                cohortDomain("unknown.xx", 1, replied = 0, matureCohort = 1, matureReplied = 0)
            )
        )
        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
            listOf(
                DomainUndeliveredCount("gmail.com", 3),
                DomainUndeliveredCount(null, 1)
            )
        )

        val rows = service.providerDistribution(date, date).rows.associateBy { it.provider }

        assertEquals(10, rows.getValue("gmail").sentCount)
        assertEquals(4, rows.getValue("gmail").repliedCount)
        assertEquals(0.4, rows.getValue("gmail").replyRate)
        assertEquals(8, rows.getValue("gmail").matureCohortCount)
        assertEquals(3, rows.getValue("gmail").matureRepliedCount)
        assertEquals(0.375, rows.getValue("gmail").matureReplyRate)
        assertEquals(3, rows.getValue("gmail").undeliveredCount)
        assertEquals(5, rows.getValue("outlook").sentCount)
        assertEquals(1, rows.getValue("outlook").repliedCount)
        assertEquals(3, rows.getValue("edu").sentCount)
        assertEquals(2, rows.getValue("tencent").sentCount)
        assertEquals(1, rows.getValue("other").sentCount)
        assertEquals(1, rows.getValue("other").undeliveredCount)

        assertEquals(listOf("gmail", "outlook", "yahoo", "edu", "tencent", "netease", "other"), rows.keys.toList())
    }

    @Test
    fun `regionDistribution folds countries via CountryContinentMapping in allRegions order`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(
                cohortCountry("Germany", 8, replied = 3, matureCohort = 8, matureReplied = 2),
                cohortCountry("US", 6, replied = 1, matureCohort = 6, matureReplied = 1),
                cohortCountry(null, 2)
            )
        )
        Mockito.`when`(promotionRepository.aggregateSuccessByCountry(from, to)).thenReturn(
            listOf(CountryCount("US", 2))
        )

        val rows = service.regionDistribution(date, date)

        assertEquals(CountryContinentMapping.allRegions(), rows.map { it.region })
        val byRegion = rows.associateBy { it.region }
        assertEquals(8, byRegion.getValue(CountryContinentMapping.REGION_EUROPE).sentCount)
        assertEquals(3, byRegion.getValue(CountryContinentMapping.REGION_EUROPE).repliedCount)
        assertEquals(8, byRegion.getValue(CountryContinentMapping.REGION_EUROPE).matureCohortCount)
        assertEquals(6, byRegion.getValue(CountryContinentMapping.REGION_NORTH_AMERICA).sentCount)
        assertEquals(2, byRegion.getValue(CountryContinentMapping.REGION_NORTH_AMERICA).promotionCount)
        assertEquals(2, byRegion.getValue(CountryContinentMapping.REGION_OTHER).sentCount)
        assertEquals(listOf("Germany"), byRegion.getValue(CountryContinentMapping.REGION_EUROPE).countries.map { it.country })
        assertEquals(listOf("US"), byRegion.getValue(CountryContinentMapping.REGION_NORTH_AMERICA).countries.map { it.country })
        assertEquals(listOf("未知"), byRegion.getValue(CountryContinentMapping.REGION_OTHER).countries.map { it.country })
    }

    @Test
    fun `regionDistribution region totals equal sum of country rows`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(
                cohortCountry("Germany", 8, replied = 3, matureCohort = 8, matureReplied = 2),
                cohortCountry("France", 4, replied = 0, matureCohort = 2, matureReplied = 0),
                cohortCountry("US", 6, replied = 1, matureCohort = 6, matureReplied = 1),
                cohortCountry("Canada", 3),
                cohortCountry(null, 2, replied = 1)
            )
        )
        Mockito.`when`(promotionRepository.aggregateSuccessByCountry(from, to)).thenReturn(emptyList())

        val rows = service.regionDistribution(date, date)

        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            assertEquals(row.countries.sumOf { it.sentCount }, row.sentCount, "sentCount sum mismatch for ${row.region}")
            assertEquals(row.countries.sumOf { it.repliedCount }, row.repliedCount, "repliedCount sum mismatch for ${row.region}")
            assertEquals(row.countries.sumOf { it.matureCohortCount }, row.matureCohortCount, "matureCohortCount sum mismatch for ${row.region}")
            assertEquals(row.countries.sumOf { it.matureRepliedCount }, row.matureRepliedCount, "matureRepliedCount sum mismatch for ${row.region}")
        }
        val europe = rows.first { it.region == CountryContinentMapping.REGION_EUROPE }
        assertEquals(12, europe.sentCount)
        assertEquals(3, europe.repliedCount)
        assertEquals(10, europe.matureCohortCount)
        assertEquals(2, europe.matureRepliedCount)
        val other = rows.first { it.region == CountryContinentMapping.REGION_OTHER }
        assertEquals(2, other.sentCount)
        assertEquals(1, other.repliedCount)
        assertEquals(0.5, other.replyRate)
    }

    @Test
    fun `regionDistribution keeps country rows sorted by cohort desc and maps blank country to unknown`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByCountry(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(
                cohortCountry("Germany", 2),
                cohortCountry(null, 5),
                cohortCountry("", 3),
                cohortCountry("US", 7, replied = 2, matureCohort = 7, matureReplied = 1)
            )
        )
        Mockito.`when`(promotionRepository.aggregateSuccessByCountry(from, to)).thenReturn(emptyList())

        val rows = service.regionDistribution(date, date)

        rows.forEach { row ->
            assertEquals(
                row.countries.map { it.sentCount }.sortedDescending(),
                row.countries.map { it.sentCount },
                "countries not sorted by cohort desc for ${row.region}"
            )
        }
        val other = rows.first { it.region == CountryContinentMapping.REGION_OTHER }
        assertEquals(listOf("未知", "未知"), other.countries.map { it.country })
        assertEquals(listOf(5L, 3L), other.countries.map { it.sentCount })
        val europe = rows.first { it.region == CountryContinentMapping.REGION_EUROPE }
        assertEquals(listOf("Germany"), europe.countries.map { it.country })
    }

    @Test
    fun `replyRate and matureReplyRate use their own denominators and return 0 when denominator is 0`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(
                // 10 人队列 4 人已回，但无一人满 7 天 → 成熟回复率必须是 0，而不是 0.4。
                cohortDomain("gmail.com", 10, replied = 4),
                // 队列为 0：两个比率都必须是 0。
                cohortDomain("yahoo.com", 0),
                // 成熟子集分母独立：满 7 天仅 2 人，其中 1 人 7 日内首回 → 0.5。
                cohortDomain("mit.edu", 8, replied = 1, matureCohort = 2, matureReplied = 1)
            )
        )
        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(emptyList())

        val rows = service.providerDistribution(date, date).rows.associateBy { it.provider }

        assertEquals(0.0, rows.getValue("gmail").matureReplyRate)
        assertEquals(0.4, rows.getValue("gmail").replyRate)
        assertEquals(0.0, rows.getValue("yahoo").replyRate)
        assertEquals(0.0, rows.getValue("yahoo").matureReplyRate)
        assertEquals(0.5, rows.getValue("edu").matureReplyRate)
        assertEquals(0.125, rows.getValue("edu").replyRate)
    }

    @Test
    fun `providerDistribution folds undelivered counts by domain bucket`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
            listOf(
                DomainUndeliveredCount("gmail.com", 3),
                DomainUndeliveredCount("yahoo.com", 1),
                DomainUndeliveredCount(null, 2)
            )
        )

        val rows = service.providerDistribution(date, date).rows.associateBy { it.provider }

        assertEquals(3, rows.getValue("gmail").undeliveredCount)
        assertEquals(1, rows.getValue("yahoo").undeliveredCount)
        // I-3：null 域名（无法解析）折叠进 other 桶，而不是被丢弃
        assertEquals(2, rows.getValue("other").undeliveredCount)
    }

    @Test
    fun `providerDistribution reports unattributed bounce count separately`() {
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(
            listOf(cohortDomain("gmail.com", 4))
        )
        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(emptyList())
        Mockito.`when`(bounceRecordRepository.countUnattributedBouncesBetween(from, to)).thenReturn(5L)

        val response = service.providerDistribution(date, date)

        // I-4：未归因退信单独计数，不混入任何 rows 元素
        assertEquals(5L, response.unattributedBounceCount)
        assertEquals(0L, response.rows.sumOf { it.undeliveredCount })
    }

    @Test
    fun `providerDistribution keeps undelivered count when cohort is zero`() {
        // I-9：队列 = 0（本窗口无首发）但窗口内有退信时，undeliveredCount 必须原样保留
        Mockito.`when`(mailRecordRepository.aggregateIntroCohortByDomain(
            Mockito.eq(from) ?: from, Mockito.eq(to) ?: to, Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.aggregateUndeliveredByDomain(from, to)).thenReturn(
            listOf(DomainUndeliveredCount("gmail.com", 4))
        )

        val rows = service.providerDistribution(date, date).rows.associateBy { it.provider }

        assertEquals(0L, rows.getValue("gmail").sentCount)
        assertEquals(4L, rows.getValue("gmail").undeliveredCount)
    }
}
