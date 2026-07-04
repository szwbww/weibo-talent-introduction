package com.weibo.talentintroduction.postmaster.service

import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.postmaster.domain.DomainReputationHistory
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.LocalDate

class ReputationAutoPauseServiceTest {
    private val properties = PostmasterProperties(
        enabled = true,
        domains = listOf("talents.example.com", "mail.example.com"),
        pauseThresholdSpamRate = 0.003,
        resumeThresholdSpamRate = 0.001,
        resumeConsecutiveDays = 3
    )
    private val historyRepository = Mockito.mock(DomainReputationHistoryRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val service = ReputationAutoPauseService(
        properties,
        historyRepository,
        mailSenderAccountService,
        mailSenderAccountRepository
    )

    @Test
    fun `pauses enabled unpaused accounts for domain when spam rate exceeds threshold`() {
        stubAccounts(
            account("a1", "ops@talents.example.com"),
            account("a2", "team@mail.example.com"),
            account("a3", "other@talents.example.com", autoSendPaused = true, reason = "DAILY_LIMIT:100")
        )
        Mockito.`when`(historyRepository.findFirstByDomainOrderByReportDateDesc("talents.example.com"))
            .thenReturn(history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0069))

        service.checkDomain("talents.example.com")

        Mockito.verify(mailSenderAccountService).pauseAutoSend(
            ArgumentMatchers.eq("a1") ?: "a1",
            ArgumentMatchers.eq("REPUTATION:spam_rate=0.7%") ?: ""
        )
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(
            ArgumentMatchers.eq("a2") ?: "a2",
            ArgumentMatchers.anyString() ?: ""
        )
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(
            ArgumentMatchers.eq("a3") ?: "a3",
            ArgumentMatchers.anyString() ?: ""
        )
    }

    @Test
    fun `does not resume when fewer than consecutive days below threshold`() {
        stubAccounts(
            account(
                "a1",
                "ops@talents.example.com",
                autoSendPaused = true,
                reason = "REPUTATION:spam_rate=0.7%"
            )
        )
        Mockito.`when`(historyRepository.findFirstByDomainOrderByReportDateDesc("talents.example.com"))
            .thenReturn(history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0005))
        Mockito.`when`(historyRepository.findByDomainOrderByReportDateDesc("talents.example.com", 3))
            .thenReturn(
                listOf(
                    history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0005)
                )
            )

        service.checkDomain("talents.example.com")

        Mockito.verify(mailSenderAccountService, Mockito.never()).resumeAutoSend(ArgumentMatchers.anyString() ?: "")
    }

    @Test
    fun `resumes reputation paused accounts after consecutive days below threshold`() {
        stubAccounts(
            account(
                "a1",
                "ops@talents.example.com",
                autoSendPaused = true,
                reason = "REPUTATION:spam_rate=0.7%"
            ),
            account(
                "a2",
                "backup@talents.example.com",
                autoSendPaused = true,
                reason = "DAILY_LIMIT:100"
            )
        )
        Mockito.`when`(historyRepository.findFirstByDomainOrderByReportDateDesc("talents.example.com"))
            .thenReturn(history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0005))
        Mockito.`when`(historyRepository.findByDomainOrderByReportDateDesc("talents.example.com", 3))
            .thenReturn(
                listOf(
                    history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0005),
                    history("talents.example.com", LocalDate.of(2026, 7, 2), spamRate = 0.0008),
                    history("talents.example.com", LocalDate.of(2026, 7, 1), spamRate = 0.0009)
                )
            )

        service.checkDomain("talents.example.com")

        Mockito.verify(mailSenderAccountService).resumeAutoSend(ArgumentMatchers.eq("a1") ?: "a1")
        Mockito.verify(mailSenderAccountService, Mockito.never()).resumeAutoSend(ArgumentMatchers.eq("a2") ?: "a2")
    }

    @Test
    fun `does not resume when one day in window exceeds threshold`() {
        assertFalse(
            service.shouldResume(
                domain = "talents.example.com",
                records = listOf(
                    history("talents.example.com", LocalDate.of(2026, 7, 3), spamRate = 0.0005),
                    history("talents.example.com", LocalDate.of(2026, 7, 2), spamRate = 0.002),
                    history("talents.example.com", LocalDate.of(2026, 7, 1), spamRate = 0.0005)
                )
            )
        )
    }

    @Test
    fun `extractDomain returns lowercase domain suffix`() {
        assertEquals("talents.example.com", ReputationAutoPauseService.extractDomain("Ops@Talents.Example.com"))
    }

    @Test
    fun `collector upserted high spam rate triggers service pause`() {
        val date = LocalDate.of(2026, 7, 3)
        val fetcher = Mockito.mock(DomainStatsFetcher::class.java)
        val collector = PostmasterDataCollector(properties, historyRepository, fetcher)
        Mockito.`when`(fetcher.fetch("talents.example.com", date))
            .thenReturn(CollectedDomainStats(spamRate = 0.0069, rawJson = "{}"))
        Mockito.`when`(fetcher.fetch("mail.example.com", date)).thenReturn(null)
        Mockito.`when`(historyRepository.findByDomainAndReportDate("talents.example.com", date))
            .thenReturn(null)

        collector.collect(date)

        val captor = ArgumentCaptor.forClass(DomainReputationHistory::class.java)
        Mockito.verify(historyRepository).save(captor.capture())
        val persisted = captor.value

        stubAccounts(account("a1", "ops@talents.example.com"))
        Mockito.`when`(historyRepository.findFirstByDomainOrderByReportDateDesc("talents.example.com"))
            .thenReturn(persisted)

        service.checkDomain("talents.example.com")

        Mockito.verify(mailSenderAccountService).pauseAutoSend(
            ArgumentMatchers.eq("a1") ?: "a1",
            ArgumentMatchers.eq("REPUTATION:spam_rate=0.7%") ?: ""
        )
    }

    private fun stubAccounts(vararg accounts: MailSenderAccount) {
        Mockito.`when`(mailSenderAccountRepository.findAllByEnabledTrue()).thenReturn(accounts.toList())
    }

    private fun account(
        code: String,
        email: String,
        autoSendPaused: Boolean = false,
        reason: String? = null
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = code,
            senderEmail = email,
            senderName = code,
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp",
            smtpPort = 465,
            smtpUsername = email,
            smtpPassword = "pw",
            imapHost = "imap",
            imapPort = 993,
            imapUsername = email,
            imapPassword = "pw",
            autoSendPaused = autoSendPaused,
            autoSendPausedReason = reason
        )

    private fun history(
        domain: String,
        date: LocalDate,
        spamRate: Double? = null,
        domainReputation: String? = null
    ): DomainReputationHistory =
        DomainReputationHistory(
            domain = domain,
            reportDate = date,
            spamRate = spamRate,
            domainReputation = domainReputation
        )

    private fun ReputationAutoPauseService.shouldResume(domain: String, records: List<DomainReputationHistory>): Boolean {
        Mockito.`when`(historyRepository.findByDomainOrderByReportDateDesc(domain, properties.resumeConsecutiveDays))
            .thenReturn(records)
        return shouldResume(domain)
    }
}
