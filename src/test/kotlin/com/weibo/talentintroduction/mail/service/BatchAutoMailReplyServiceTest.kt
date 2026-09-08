package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BatchAutoMailReplyServiceTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val autoReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val service = BatchAutoMailReplyService(accountService, autoReplyService, mailRecordRepository)

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq<T>(value) ?: value

    @Test
    fun `polls all auto-receive accounts and aggregates results`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 2, recorded = 2, replied = 1, manualReview = 1))

        val result = service.receiveAndAutoReplyAll(5)

        assertEquals(2, result.accountCount)
        assertEquals(2, result.successAccountCount)
        assertEquals(0, result.failedAccountCount)
        assertEquals(3, result.fetched)
        assertEquals(3, result.recorded)
        assertEquals(2, result.replied)
        assertEquals(1, result.manualReview)
        assertEquals(2, result.taskSuccessCount)
        assertEquals(0, result.taskFailureCount)
        assertNull(result.taskFinalStatus)
    }

    @Test
    fun `isolates per-account failure`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"), account("a3"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 3, null, null))
            .thenThrow(RuntimeException("IMAP connection timeout"))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a3", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 2, recorded = 1, replied = 0, manualReview = 1))

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(3, result.accountCount)
        assertEquals(2, result.successAccountCount)
        assertEquals(1, result.failedAccountCount)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(3, result.fetched)
        assertEquals(2, result.recorded)
        assertEquals(1, result.replied)
        assertEquals(1, result.manualReview)

        val failed = result.accounts.find { it.status == "FAILED" }
        assertNotNull(failed)
        assertEquals("a2", failed!!.accountCode)
        assertEquals("IMAP connection timeout", failed.errorMessage)
        assertEquals(0, failed.fetched)

        val successAccounts = result.accounts.filter { it.status == "SUCCESS" }
        assertEquals(2, successAccounts.size)
        assertEquals(listOf("a1", "a3"), successAccounts.map { it.accountCode })
    }

    @Test
    fun `all accounts fail results in FAILED status`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply(anyValue(""), anyValue(0), anyValue(null), anyValue(null)))
            .thenThrow(RuntimeException("IMAP connection timeout"))

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(2, result.accountCount)
        assertEquals(0, result.successAccountCount)
        assertEquals(2, result.failedAccountCount)
        assertEquals("FAILED", result.taskFinalStatus)
        assertEquals(0, result.fetched)
    }

    @Test
    fun `no auto-receive accounts returns empty result`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(emptyList())

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(0, result.accountCount)
        assertEquals(0, result.successAccountCount)
        assertEquals(0, result.failedAccountCount)
        assertNull(result.taskFinalStatus)
    }

    @Test
    fun `error message does not contain password`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenThrow(RuntimeException("Authentication failed with password secret"))

        val result = service.receiveAndAutoReplyAll(3)

        val failed = result.accounts.first { it.status == "FAILED" }
        assertTrue(failed.errorMessage!!.length <= 1000)
        assertTrue(!failed.errorMessage!!.contains("secret"))
        assertTrue(failed.errorMessage!!.contains("[REDACTED]"))
    }

    @Test
    fun `simulator account excluded from polling`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("real_acct"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("real_acct", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(1, result.accountCount)
        assertTrue(result.accounts.none { it.accountCode == "SIMULATOR_NOOP" })
    }

    @Test
    fun `all accounts succeed returns null taskFinalStatus`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(1, result.successAccountCount)
        assertEquals(0, result.failedAccountCount)
        assertNull(result.taskFinalStatus)
    }

    @Test
    fun `selective check polls only mapped account`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
            .thenReturn(listOf("a1"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a1"))
            .thenReturn(account("a1"))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))

        val result = service.receiveAndAutoReplyForContacts(listOf(1L), 3)

        assertEquals(1, result.accountCount)
        assertEquals("SUCCESS", result.accounts.first().status)
        Mockito.verify(accountService, Mockito.never()).listEnabledAccounts()
        Mockito.verify(accountService, Mockito.never()).listAutoReceiveAccounts()
    }

    @Test
    fun `selective check polls multiple accounts`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
            .thenReturn(listOf("a1", "a2"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a1")).thenReturn(account("a1"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a2")).thenReturn(account("a2"))
        Mockito.`when`(autoReplyService.receiveAndAutoReply(anyValue(""), anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))

        val result = service.receiveAndAutoReplyForContacts(listOf(1L, 2L), 3)

        assertEquals(2, result.accountCount)
        Mockito.verify(autoReplyService).receiveAndAutoReply("a1", 3, null, null)
        Mockito.verify(autoReplyService).receiveAndAutoReply("a2", 3, null, null)
        Mockito.verify(accountService, Mockito.never()).listAutoReceiveAccounts()
    }

    @Test
    fun `selective check deduplicates to same account`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
            .thenReturn(listOf("a1"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a1")).thenReturn(account("a1"))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 2, recorded = 2, replied = 1, manualReview = 1))

        val result = service.receiveAndAutoReplyForContacts(listOf(1L, 2L), 5)

        assertEquals(1, result.accountCount)
        Mockito.verify(autoReplyService, Mockito.times(1)).receiveAndAutoReply("a1", 5, null, null)
    }

    @Test
    fun `selective check no accounts throws error`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
            .thenReturn(emptyList())

        val ex = assertThrows(IllegalStateException::class.java) {
            service.receiveAndAutoReplyForContacts(listOf(1L, 2L), 3)
        }
        assertTrue(ex.message!!.contains("No auto-receive account"))
        Mockito.verify(accountService, Mockito.never()).listAutoReceiveAccounts()
    }

    @Test
    fun `selective check disabled account throws error`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
            .thenReturn(listOf("disabled"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("disabled")).thenReturn(null)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.receiveAndAutoReplyForContacts(listOf(1L), 3)
        }
        assertTrue(ex.message!!.contains("unavailable"))
        assertTrue(ex.message!!.contains("disabled"))
    }

    @Test
    fun `selective check mixed valid and unavailable accounts fails entirely`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
            .thenReturn(listOf("a1", "disabled"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a1")).thenReturn(account("a1"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("disabled")).thenReturn(null)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.receiveAndAutoReplyForContacts(listOf(1L, 2L), 3)
        }
        assertTrue(ex.message!!.contains("disabled"))
        assertTrue(ex.message!!.contains("unavailable"))
        // a1 must NOT have been polled
        Mockito.verify(autoReplyService, Mockito.never()).receiveAndAutoReply(
            Mockito.anyString(), Mockito.anyInt(), anyValue(null), anyValue(null)
        )
    }

    @Test
    fun `selective check SIMULATOR_NOOP excluded`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L)))
            .thenReturn(listOf("SIMULATOR_NOOP"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("SIMULATOR_NOOP")).thenReturn(null)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.receiveAndAutoReplyForContacts(listOf(1L), 3)
        }
        assertTrue(ex.message!!.contains("unavailable"))
        assertTrue(ex.message!!.contains("SIMULATOR_NOOP"))
    }

    @Test
    fun `selective check one account fails others continue`() {
        Mockito.`when`(mailRecordRepository.findDistinctSenderAccountCodesByExpertContactIds(listOf(1L, 2L)))
            .thenReturn(listOf("a1", "a2"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a1")).thenReturn(account("a1"))
        Mockito.`when`(accountService.getAutoReceiveAccountOrNull("a2")).thenReturn(account("a2"))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 3, null, null))
            .thenThrow(RuntimeException("IMAP connection timeout"))

        val result = service.receiveAndAutoReplyForContacts(listOf(1L, 2L), 3)

        assertEquals(2, result.accountCount)
        assertEquals(1, result.successAccountCount)
        assertEquals(1, result.failedAccountCount)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `experts with reply deduplicated by lowercase email`() {
        val experts = listOf(
            RepliedExpertInfo(1L, "A@test.com", null, "QA_REPLIED"),
            RepliedExpertInfo(2L, "a@test.com", null, "QA_REPLIED"),
            RepliedExpertInfo(3L, "b@test.com", null, "QA_REPLIED")
        )
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(listOf(account("a1")))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 3, null, null))
            .thenReturn(AutoMailReplyBatchResult(
                fetched = 3, recorded = 3, replied = 3, manualReview = 0, repliedExperts = experts
            ))

        val result = service.receiveAndAutoReplyAll(3)

        assertEquals(2, result.expertsWithReply.size)
        assertTrue(result.expertsWithReply.contains("a@test.com"))
        assertTrue(result.expertsWithReply.contains("b@test.com"))
    }

    @Test
    fun `receiveAndAutoReplyAll supports progress callback`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 2, recorded = 2, replied = 1, manualReview = 1))

        val progressResults = mutableListOf<AccountAutoMailReplyResult>()
        val progressCounts = mutableListOf<Pair<Int, Int>>()
        val onProgress: (AccountAutoMailReplyResult, Int, Int) -> Unit = { result, processed, total ->
            progressResults.add(result)
            progressCounts.add(processed to total)
            Unit
        }

        val result = service.receiveAndAutoReplyAll(5, onProgress = onProgress)

        assertEquals(2, result.accountCount)
        assertEquals(2, progressResults.size)
        assertEquals("a1", progressResults[0].accountCode)
        assertEquals("a2", progressResults[1].accountCode)
        assertEquals(1 to 2, progressCounts[0])
        assertEquals(2 to 2, progressCounts[1])
    }

    @Test
    fun `receiveAndAutoReplyAll stops polling on cancellation`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"), account("a3"))
        )
        var cancelledAfterFirst = false
        val isCancelled: () -> Boolean = {
            cancelledAfterFirst
        }
        // 批量把取消判定原样转发进账号接收：stub 用同一 lambda 引用匹配。
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5, null, isCancelled))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))

        val startedEvents = mutableListOf<String>()
        val onAccountStarted: (AccountAutoMailReplyStage) -> Unit = { stage ->
            startedEvents.add(stage.accountCode)
        }
        val onProgress: (AccountAutoMailReplyResult, Int, Int) -> Unit = { _, processed, _ ->
            if (processed == 1) {
                cancelledAfterFirst = true
            }
        }

        val result = service.receiveAndAutoReplyAll(
            5,
            onProgress = onProgress,
            isCancelled = isCancelled,
            onAccountStarted = onAccountStarted
        )

        assertEquals(3, result.accountCount)
        assertEquals(1, result.accountsPolled)
        assertEquals(1, result.accounts.size)
        assertEquals("a1", result.accounts[0].accountCode)
        assertEquals("CANCELLED", result.taskFinalStatus)
        assertTrue(result.wasCancelled)
        // I-1/I-2：取消后的账号既不被轮询也不发布开始事件（安全边界）。
        assertEquals(listOf("a1"), startedEvents)
        Mockito.verify(autoReplyService, Mockito.never()).receiveAndAutoReply("a2", 5, null, null)
        Mockito.verify(autoReplyService, Mockito.never()).receiveAndAutoReply("a3", 5, null, null)
    }

    @Test
    fun `account started callback fires before entering each account and after previous completion`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        val events = mutableListOf<String>()
        Mockito.`when`(autoReplyService.receiveAndAutoReply(
            eqValue("a1"), eqValue(5), anyValue(null), anyValue(null)
        )).thenAnswer { invocation ->
            events.add("work:a1")
            AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0)
        }
        Mockito.`when`(autoReplyService.receiveAndAutoReply(
            eqValue("a2"), eqValue(5), anyValue(null), anyValue(null)
        )).thenAnswer { invocation ->
            events.add("work:a2")
            AutoMailReplyBatchResult(fetched = 1, recorded = 0, replied = 0, manualReview = 1)
        }

        val startedPhases = mutableListOf<Pair<String, String>>()
        val result = service.receiveAndAutoReplyAll(
            5,
            onAccountStarted = { stage ->
                events.add("started:${stage.accountCode}")
                startedPhases.add(stage.accountCode to stage.phase)
            },
            onStage = { stage ->
                events.add("stage:${stage.phase}")
            }
        )

        // I-1：进入账号前回调（CONNECTING）先于该账号工作；上一账号完成后才轮到下一账号开始。
        assertEquals(
            listOf(
                "started:a1", "work:a1",
                "started:a2", "work:a2"
            ),
            events
        )
        assertEquals(
            listOf("a1" to AccountAutoMailReplyPhases.CONNECTING, "a2" to AccountAutoMailReplyPhases.CONNECTING),
            startedPhases
        )
        assertEquals(2, result.accountsPolled)
        assertEquals(2, result.successAccountCount)
    }

    @Test
    fun `intra-account phases from receive service are forwarded to onStage in order`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply(anyValue(""), anyValue(0), anyValue(null), anyValue(null)))
            .thenAnswer { invocation ->
                val onPhase = invocation.getArgument<(String) -> Unit>(2)
                onPhase(AccountAutoMailReplyPhases.READING_METADATA)
                onPhase(AccountAutoMailReplyPhases.PROCESSING_MAIL)
                AutoMailReplyBatchResult(fetched = 2, recorded = 2, replied = 1, manualReview = 0)
            }

        val recorded = mutableListOf<AccountAutoMailReplyStage>()
        val started = mutableListOf<AccountAutoMailReplyStage>()
        val result = service.receiveAndAutoReplyAll(
            5,
            onAccountStarted = { stage -> started.add(stage) },
            onStage = { stage -> recorded.add(stage) }
        )

        // 阶段顺序 CONNECTING(started) -> READING_METADATA -> PROCESSING_MAIL；
        // 同一账号的 startedAt 一致、updatedAt 单调不减；totalAccounts 传递。
        assertEquals(1, started.size)
        assertEquals(AccountAutoMailReplyPhases.CONNECTING, started[0].phase)
        assertEquals(listOf("a1", "a1"), recorded.map { it.accountCode })
        assertEquals(
            listOf(AccountAutoMailReplyPhases.READING_METADATA, AccountAutoMailReplyPhases.PROCESSING_MAIL),
            recorded.map { it.phase }
        )
        val accountStartedAt = started[0].startedAt
        recorded.forEach { stage ->
            assertEquals(accountStartedAt, stage.startedAt)
            assertTrue(stage.updatedAt >= stage.startedAt)
            assertEquals(1, stage.totalAccounts)
        }
        assertEquals(1, result.successAccountCount)
    }

    @Test
    fun `failed account reports failure via onProgress while completed counts are retained`() {
        Mockito.`when`(accountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5, null, null))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 5, null, null))
            .thenThrow(RuntimeException("IMAP receive window exceeded"))

        val progress = mutableListOf<Pair<Int, Int>>()
        val result = service.receiveAndAutoReplyAll(
            5,
            onProgress = { _, processed, total -> progress.add(processed to total) }
        )

        // I-4：partial 失败保留已完成账号计数（accountsPolled=2、成功 1、失败 1）。
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(2, result.accountsPolled)
        assertEquals(1, result.successAccountCount)
        assertEquals(1, result.failedAccountCount)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    private fun account(accountCode: String): MailSenderAccount =
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
            imapPassword = "secret"
        )
}
