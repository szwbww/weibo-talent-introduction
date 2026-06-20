package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class SenderAccountAssignmentServiceTest {
    private val repository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val warmupService = SenderWarmupService(WarmupProperties(enabled = false))
    private val service = SenderAccountAssignmentService(repository, warmupService)

    @Test
    fun `avoids repeatedly assigning same country segment to same account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            currentBatchAssignments = listOf(
                SenderExpertAssignment(
                    accountCode = "chen",
                    expertId = "0000-0001",
                    distributionKey = "united states"
                )
            )
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `excludes simulator account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("SIMULATOR_NOOP", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States")
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `excludes auto-paused account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, autoSendPaused = true),
                account("zoe", strategyWeight = 100, autoSendPaused = false)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States")
        )

        assertEquals("zoe", selected.accountCode)
    }

    private fun account(accountCode: String, strategyWeight: Int = 100, autoSendPaused: Boolean = false): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@qftechtalent.com",
            imapPassword = "secret",
            strategyWeight = strategyWeight,
            autoSendPaused = autoSendPaused
        )

    private fun expert(country: String): ExpertProfile =
        ExpertProfile(
            orcidId = "0000-0002",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = country,
            keyword = "computer science",
            employment = "University"
        )
}
