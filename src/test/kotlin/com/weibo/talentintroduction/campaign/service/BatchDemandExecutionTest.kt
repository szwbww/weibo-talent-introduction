package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.*
import com.weibo.talentintroduction.campaign.repository.*
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.expert.domain.*
import com.weibo.talentintroduction.expert.service.*
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.mail.service.*
import com.weibo.talentintroduction.task.service.*
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.Executors

/** Exercises the production run entry with a real PIT cursor and capture-only delivery seams. */
class BatchDemandExecutionTest {
    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `thirty successes stop without population count or terminal prefetch`(type: String) {
        Fixture(type).use { f ->
            val result = f.run()
            assertEquals(30, result.sent)
            assertEquals(30, result.total)
            assertEquals(30, f.delivered.size)
            assertEquals(listOf(60), f.pageSizes)
            assertFalse(result.execution!!.sourceExhausted)
            assertEquals(0L, result.execution!!.remainingSuccessQuota)
            f.assertProtocol(result)
            f.assertNoPopulationRead()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `five failures and five changed candidates supplement to thirty successes`(type: String) {
        Fixture(type).use { f ->
            f.failedIds = (1..5).map { "d$it" }.toSet()
            f.changedIds = (6..10).map { "d$it" }.toSet()
            val result = f.run()
            assertEquals(30, result.sent)
            assertEquals(5, result.failed)
            assertEquals(5, result.skipped)
            assertEquals(40, result.total)
            assertEquals(0, result.remaining)
            assertEquals(5, result.outcome!!.skippedReasons[BatchOutcomeReasonCodes.CANDIDATE_CHANGED]!!.count)
            assertEquals(0L, result.execution!!.remainingSuccessQuota)
            assertEquals(listOf(60), f.pageSizes)
            assertTrue(f.delivered.none { it in (6..10).map { n -> "d$n" } })
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `raw filtered empty page advances and scans without inventing skips`(type: String) {
        Fixture(type, population = 100, chunk = 30).use { f ->
            f.filteredIds = (1..30).map { "d$it" }.toSet()
            val result = f.run()
            assertEquals(30, result.sent)
            assertEquals(0, result.skipped)
            assertEquals(30, result.total)
            assertEquals(listOf(60, 60), f.pageSizes)
            assertEquals(60L, result.execution!!.scanned)
            assertFalse(result.execution!!.sourceExhausted)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `candidate exhaustion leaves genuine success deficit and no virtual skip`(type: String) {
        Fixture(type, population = 7).use { f ->
            val result = f.run()
            assertEquals(7, result.sent)
            assertEquals(7, result.total)
            assertEquals(0, result.skipped)
            assertEquals(0, result.remaining)
            assertTrue(result.execution!!.sourceExhausted)
            assertEquals(23L, result.execution!!.remainingSuccessQuota)
            assertEquals(listOf(60, 46), f.pageSizes)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `strict candidate read failure before success fails rather than exhausting`(type: String) {
        Fixture(type).use { f ->
            f.failStrictRead = true
            val result = f.run()
            assertEquals(0, result.sent)
            assertEquals(0, result.total)
            assertEquals("CANDIDATE_READ_FAILED", result.stopReason)
            assertEquals("FAILED", result.taskFinalStatus)
            assertFalse(result.execution!!.sourceExhausted)
            assertEquals(30L, result.execution!!.remainingSuccessQuota)
            assertTrue(f.delivered.isEmpty())
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `next page read failure after ten successes is partial success`(type: String) {
        Fixture(type, chunk = 10).use { f ->
            f.failPage = 2
            val result = f.run()
            assertEquals(10, result.sent)
            assertEquals(10, result.total)
            assertEquals(0, result.skipped)
            assertEquals("CANDIDATE_READ_FAILED", result.stopReason)
            assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
            assertFalse(result.execution!!.sourceExhausted)
            assertEquals(20L, result.execution!!.remainingSuccessQuota)
            assertEquals(listOf(60, 40), f.pageSizes)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `cancellation after enumeration prevents prepared attempt and delivery and closes cursor`(type: String) {
        Fixture(type).use { f ->
            f.cancelOnPage = true
            val result = f.run()
            assertTrue(result.wasCancelled)
            assertEquals(0, result.sent)
            assertEquals(0, result.skipped)
            assertEquals(0, result.total)
            assertTrue(f.delivered.isEmpty())
            Mockito.verifyNoInteractions(f.delivery, f.manualMail, f.tx)
            Mockito.verify(f.attempts, Mockito.never()).save(any(throwawayAttempt()))
            assertEquals(1, f.closed)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `zero account capacity never fetches candidates`(type: String) {
        Fixture(type, capacity = 0).use { f ->
            val result = f.run()
            assertEquals(0, result.sent)
            assertEquals(0, result.total)
            assertEquals(0, result.skipped)
            assertTrue(f.pageSizes.isEmpty())
            assertTrue(f.delivered.isEmpty())
            f.assertProtocol(result)
            f.assertNoPopulationRead()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `account capacity smaller than round size bounds successful delivery`(type: String) {
        Fixture(type, capacity = 10).use { f ->
            val result = f.run()
            assertEquals(10, result.sent)
            assertEquals(10, f.delivered.size)
            assertEquals(listOf(20), f.pageSizes)
            assertEquals(20L, result.execution!!.remainingSuccessQuota)
            assertFalse(result.execution!!.sourceExhausted)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `two rounds and one round override consume only their success budget`(type: String) {
        Fixture(type).use { f ->
            val result = f.run(roundSize = 10, rounds = 2)
            assertEquals(20, result.sent)
            assertEquals(listOf(20), f.pageSizes)
            assertEquals(20L, result.execution!!.plannedSuccess)
            f.assertProtocol(result)
        }
        Fixture(type).use { f ->
            val result = f.run(roundSize = 10, rounds = 2, oneRound = true)
            assertEquals(10, result.sent)
            assertEquals(listOf(20), f.pageSizes)
            assertEquals(10L, result.execution!!.plannedSuccess)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `actual execution reduces next read from sixty to twenty after twenty successes`(type: String) {
        Fixture(type, chunk = 20).use { f ->
            val result = f.run()
            assertEquals(30, result.sent)
            assertEquals(listOf(60, 20), f.pageSizes)
            f.assertProtocol(result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTRODUCTION", "MATERIAL_REMINDER"])
    fun `filtered candidates beyond ten thousand do not truncate execution`(type: String) {
        Fixture(type, population = 11001).use { f ->
            f.filteredIds = (1..10971).map { "d$it" }.toSet()
            val result = f.run()
            assertEquals(30, result.sent)
            assertEquals(11001L, result.execution!!.scanned)
            assertEquals(0, result.skipped)
            assertTrue(f.pageSizes.all { it in 1..60 })
            f.assertProtocol(result)
            f.assertNoPopulationRead()
        }
    }

    @org.junit.jupiter.api.Test
    fun `explicit material count traverses eleven thousand and one contacts without writes`() {
        Fixture("MATERIAL_REMINDER", population = 11001).use { f ->
            assertEquals(11001, f.count())
            assertTrue(f.pageSizes.all { it == 500 })
            f.assertNoBusinessWrites()
            assertTrue(f.delivered.isEmpty())
            assertEquals(1, f.closed)
        }
    }

    @org.junit.jupiter.api.Test
    fun `NEW keyset crosses five hundred and duplicate first orcid documents cannot crowd out other contacts`() {
        Fixture("INTRODUCTION", population = 501, retryCount = 501, duplicateFirst = 600).use { f ->
            val result = f.run(roundSize = 501)
            assertEquals(501, result.sent)
            assertEquals(501, result.total)
            assertEquals(501, f.delivered.toSet().size)
            assertEquals(listOf(500, 2), f.retryPageSizes)
            assertTrue(f.pageSizes.all { it in 1..500 })
            f.assertProtocol(result)
            f.assertNoPopulationRead()
        }
    }

    private class Fixture(
        val type: String,
        population: Int = 100,
        private val chunk: Int = 500,
        capacity: Int = 1000,
        private val retryCount: Int = 0,
        duplicateFirst: Int = 0
    ) : AutoCloseable {
        private val mapper = ObjectMapper()
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        private val search = mock(ExpertSearchService::class.java)
        private val assignment = mock(SenderAccountAssignmentService::class.java)
        private val composer = mock(IntroductionMailComposer::class.java)
        val delivery = mock(MailDeliveryService::class.java)
        private val contacts = mock(ExpertContactRepository::class.java)
        private val campaigns = mock(CampaignRepository::class.java)
        private val records = mock(MailRecordRepository::class.java)
        private val accounts = mock(MailSenderAccountRepository::class.java)
        val attempts = mock(MailSendAttemptRepository::class.java)
        private val progress = mock(TaskProgressStore::class.java)
        val tx = mock(ManualOutreachTxHelper::class.java)
        private val accountService = mock(MailSenderAccountService::class.java)
        private val check = mock(SenderAccountSelfCheckService::class.java)
        private val binding = mock(SenderAccountBindingService::class.java)
        private val suppression = mock(EmailSuppressionService::class.java)
        val manualMail = mock(ManualExpertMailService::class.java)
        val delivered = mutableListOf<String>()
        val pageSizes = mutableListOf<Int>()
        val retryPageSizes = mutableListOf<Int>()
        private val snapshots = mutableListOf<TaskProgress>()
        private val cursors = mutableListOf<ExpertSearchCursor>()
        var closed = 0
        var failPage: Int? = null
        var failStrictRead = false
        var cancelOnPage = false
        private var cancelled = false
        var failedIds = emptySet<String>()
        var changedIds = emptySet<String>()
        var filteredIds = emptySet<String>()
        private val reads = mutableMapOf<String, Int>()
        private val baseProfiles = (1..population).map { n -> profile(n) }
        private val profiles = if (duplicateFirst == 0) baseProfiles else listOf(baseProfiles.first()) +
            (1..duplicateFirst).map { n -> profile(1).copy(esDocId = "d${population + n}") } + baseProfiles.drop(1)
        private val byId = profiles.associateBy { it.esDocId!! }
        private val contactById = baseProfiles.mapIndexed { i, p ->
            ExpertContact(id = i.toLong() + 1, campaignId = 10, orcidId = p.orcidId,
                expertEmail = if (type == "MATERIAL_REMINDER") "contact${i + 1}@example.org" else p.email!!,
                expertName = "Fixture", currentStatus = if (retryCount > 0) "NEW" else "WAITING_REPLY",
                updatedAt = LocalDateTime.of(2026, 10, 22, 0, 0))
        }.associateBy { it.id!! }
        private val account = MailSenderAccount(
            accountCode = "capture", senderEmail = "capture@example.org", senderName = "Capture",
            senderTitle = "Title", senderDisplayName = "Capture", teamName = "Team", countryName = "China",
            smtpHost = "invalid.example", smtpPort = 465, smtpUsername = "capture", smtpPassword = "unused",
            imapHost = "invalid.example", imapPort = 993, imapUsername = "capture", imapPassword = "unused",
            dailySendLimit = capacity
        )
        private val service: ManualInitialOutreachService

        init {
            Mockito.`when`(campaigns.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
                Campaign(id = 10, campaignCode = "MANUAL_OUTREACH", campaignName = "Fixture", description = null, senderAccountId = 1))
            Mockito.`when`(contacts.findNewRetryUpperId(10)).thenReturn(retryCount.takeIf { it > 0 }?.toLong())
            Mockito.`when`(contacts.findNewRetryPage(Mockito.anyLong(), Mockito.anyLong(),
                anyNullableDate(), anyNullableLong(), Mockito.anyInt())).thenAnswer { call ->
                val after = call.getArgument<Long?>(3) ?: 0L
                val size = call.getArgument<Int>(4)
                retryPageSizes.add(size)
                contactById.values.filter { val id = it.id!!; id > after && id <= retryCount }.take(size)
            }
            Mockito.`when`(contacts.findByOrcidIdIn(any(emptyList()))).thenAnswer { call ->
                val ids = call.getArgument<List<String>>(0).map(ExpertIdNormalizer::normalize).toSet()
                if (type == "MATERIAL_REMINDER" || retryCount > 0) contactById.values.filter {
                    ExpertIdNormalizer.normalize(it.orcidId) in ids
                } else emptyList<ExpertContact>()
            }
            Mockito.`when`(contacts.findById(Mockito.anyLong())).thenAnswer { call -> Optional.ofNullable(contactById[call.getArgument<Long>(0)]) }
            Mockito.`when`(contacts.save(any(contactById.values.first()))).thenAnswer { call ->
                call.getArgument<ExpertContact>(0).let { if (it.id == null) it.copy(id = 1000) else it }
            }
            Mockito.`when`(attempts.save(anyAttempt())).thenAnswer { call -> call.getArgument<MailSendAttempt>(0).copy(id = 1) }
            Mockito.`when`(accountService.listSendableAccounts(Mockito.anyBoolean())).thenReturn(listOf(account))
            Mockito.`when`(accountService.listAccounts()).thenReturn(listOf(account))
            Mockito.`when`(accountService.listEnabledAccounts()).thenReturn(listOf(account))
            Mockito.`when`(check.checkSendable(any(account), Mockito.anyInt())).thenReturn(SelfCheckResult("capture", true, null, true))
            Mockito.`when`(binding.bindingFieldsFor(Mockito.anyString(), any(LocalDateTime.now())))
                .thenReturn("capture" to LocalDateTime.of(2026, 10, 22, 0, 0))
            Mockito.`when`(assignment.loadBindingStock()).thenReturn(SenderBindingStock.EMPTY)
            Mockito.`when`(assignment.selectAccount(any(profiles.first()), any(mutableListOf()), Mockito.anyBoolean(), any(SenderBindingStock.EMPTY)))
                .thenReturn(account)
            Mockito.`when`(progress.isCancelled(Mockito.anyString(), Mockito.anyLong())).thenAnswer { cancelled }
            Mockito.`when`(progress.update(Mockito.anyString(), any(TaskProgress("x", "RUNNING", 0, 0, 0)), anyNullableLong()))
                .thenAnswer { call -> snapshots.add(call.getArgument(1)); true }
            Mockito.`when`(search.openCandidateCursor(any(ExpertIndexLevel.CANDIDATE), any(emptyList()), Mockito.anyInt()))
                .thenAnswer { call ->
                    val filters = call.getArgument<List<Map<String, Any>>>(1)
                    @Suppress("UNCHECKED_CAST")
                    val orcids = filters.asSequence().mapNotNull {
                        (it["terms"] as? Map<String, Any>)?.get("orcidId") as? List<String>
                    }.firstOrNull()
                    newCursor(if (orcids == null) profiles else profiles.filter {
                        ExpertIdNormalizer.normalize(it.orcidId) in orcids
                    })
                }
            Mockito.`when`(search.findByDocumentIdsStrict(any(ExpertIndexLevel.CANDIDATE), any(emptyList())))
                .thenAnswer { call ->
                    if (failStrictRead) throw ExpertSearchUnavailableException("fixture strict read failed")
                    call.getArgument<List<String>>(1).mapNotNull { id ->
                        val count = (reads[id] ?: 0) + 1
                        reads[id] = count
                        byId[id]?.let { p -> when {
                            id in changedIds && count > 1 -> p.copy(email = "changed@example.org")
                            id in filteredIds -> p.copy(disciplineCategory = "OTHER")
                            else -> p
                        } }
                    }
                }
            Mockito.`when`(composer.compose(Mockito.anyString(), any(profiles.first()), Mockito.isNull(), Mockito.anyBoolean()))
                .thenAnswer { call -> ComposedMail(call.getArgument<ExpertProfile>(1).email!!, "Subject", "Body") }
            Mockito.`when`(delivery.send(any(account), any(ComposedMail("", "", "")))).thenAnswer { call ->
                val mail = call.getArgument<ComposedMail>(1)
                val id = profiles.first { it.email == mail.to }.esDocId!!
                delivered.add(id)
                if (id in failedIds) DeliveredMail("capture-$id", "FAILED", SmtpErrorCategory.PERMANENT)
                else DeliveredMail("capture-$id", "SENT")
            }
            Mockito.`when`(manualMail.sendManualMail(Mockito.anyLong(), any(ManualMailSendCommand("", "", null)), Mockito.anyBoolean()))
                .thenAnswer { call ->
                    val contactId = call.getArgument<Long>(0)
                    val contact = contactById.getValue(contactId)
                    // The production material path must retain the stored contact recipient.
                    assertEquals("contact$contactId@example.org", contact.expertEmail)
                    val id = "d$contactId"
                    delivered.add(id)
                    ManualMailSendResult(contactId, "capture", "MATERIAL_REMINDER", "Subject",
                        if (id in failedIds) "FAILED" else "SENT", "capture-$id")
                }
            service = ManualInitialOutreachService(
                search, assignment, composer, delivery, contacts, campaigns, records, accounts, attempts, progress,
                ManualOutreachProperties(sendIntervalMs = 0), tx, mock(BatchSendSettingService::class.java), accountService,
                check, mock(ExpertIndexWriterService::class.java), AccountRateLimiter(), suppression, ProviderResolver(),
                SenderWarmupService(WarmupProperties(), mapper), mock(AutoReplySettingService::class.java), manualMail,
                mock(TaskExecutionService::class.java), binding, mock(MailComposeTemplateService::class.java),
                mock(BatchEmailVerificationService::class.java), mock(ExpertOperatorStatusService::class.java)
            )
        }

        fun run(roundSize: Int = 30, rounds: Int = 1, oneRound: Boolean = false): ManualOutreachResult = service.run(
            BatchExecutionSnapshot(type, roundSize, rounds, 0, 0, 30,
                funnelLevel = if (type == "INTRODUCTION") "CANDIDATE" else "APPLICATION",
                discipline = "STEM", expertTypes = listOf("PRODUCTION_RND"),
                templateId = if (type == "MATERIAL_REMINDER") 42 else null),
            12345, ExecutionMode.MANUAL, oneRound
        )

        fun count(): Int = service.countBySnapshot(
            BatchExecutionSnapshot(type, 30, 1, 0, 0, 30, funnelLevel = "APPLICATION",
                discipline = "STEM", templateId = 42, expertTypes = listOf("PRODUCTION_RND"))
        ).totalSendable

        fun assertNoBusinessWrites() {
            Mockito.verifyNoInteractions(delivery, manualMail, tx, attempts, binding, assignment, accounts)
            assertTrue(Mockito.mockingDetails(contacts).invocations.none { it.method.name == "save" })
            assertTrue(Mockito.mockingDetails(records).invocations.none { it.method.name == "save" })
        }

        private fun newCursor(sourceProfiles: List<ExpertProfile>): ExpertSearchCursor {
            var offset = 0
            return ExpertSearchCursor(
                listOf(if (type == "INTRODUCTION") ExpertIndexLevel.CANDIDATE else ExpertIndexLevel.APPLICATION), 500,
                { "fixture" }, { mapOf("sort" to listOf(mapOf("_shard_doc" to "asc"))) },
                { path, method, body ->
                    when {
                        method == HttpMethod.DELETE -> mapper.readTree("{}")
                        path.contains("/_pit?") -> mapper.readTree("{\"id\":\"fixture-pit\"}")
                        else -> {
                            val size = body!!["size"] as Int
                            pageSizes.add(size)
                            if (pageSizes.size == failPage) throw ExpertSearchUnavailableException("fixture next page failed")
                            val page = sourceProfiles.drop(offset).take(minOf(size, chunk))
                            offset += page.size
                            if (cancelOnPage) cancelled = true
                            mapper.valueToTree(mapOf(
                                "_shards" to mapOf("total" to 1, "successful" to 1, "failed" to 0),
                                "hits" to mapOf("hits" to page.mapIndexed { index, p -> mapOf(
                                    "_id" to p.esDocId, "_source" to mapOf("id" to p.esDocId),
                                    "sort" to listOf(offset - page.size + index)
                                ) })
                            ))
                        }
                    }
                }, { hit -> byId.getValue(hit.path("_id").asText()) }, scheduler, { closed++ }
            ).also { cursors.add(it) }
        }

        fun assertProtocol(result: ManualOutreachResult) {
            assertNotNull(result.execution)
            assertTrue(snapshots.isNotEmpty(), "run must publish progress including early and failure exits")
            snapshots.forEach {
                assertNotNull(it.details?.get("execution"), "every progress event must carry demand metadata")
                val actualProcessed = listOf("sentTotal", "failedTotal", "skippedTotal")
                    .sumOf { key -> (it.details!![key] as Number).toLong() }
                assertEquals(actualProcessed, it.processedCount, "processed count must reflect actual outcomes")
                assertEquals(if (it.status == "RUNNING") 0L else actualProcessed, it.totalCount,
                    "running population is unknown; final total is actual processed")
            }
            assertEquals(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(result.execution),
                mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(snapshots.last().details!!["execution"]))
            assertEquals(result.sent + result.failed + result.skipped, result.total)
            assertEquals(0, result.remaining)
            assertEquals(cursors.size, closed, "all created cursors must close exactly once")
        }

        fun assertNoPopulationRead() {
            val forbidden = setOf("countExperts", "scrollExperts", "scrollExpertsFiltered",
                "scrollExpertsFilteredWithSort", "searchExpertsFiltered", "searchByOrcidIds")
            assertTrue(Mockito.mockingDetails(search).invocations.none { it.method.name in forbidden })
            assertTrue(Mockito.mockingDetails(contacts).invocations.none {
                it.method.name == "findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc"
            })
        }

        override fun close() {
            cursors.forEach { it.close() }
            scheduler.shutdownNow()
        }

        private fun anyAttempt(): MailSendAttempt = Mockito.any(MailSendAttempt::class.java) ?: throwawayAttempt()
    }

    companion object {
        private fun profile(n: Int) = ExpertProfile(
            orcidId = "o$n", esDocId = "d$n", email = "expert$n@example.org", givenNames = "Given", familyNames = "Family",
            country = "China", keyword = "keyword", employment = "University", disciplineCategory = "STEM",
            tags = listOf("承诺回复材料"),
            expertClassification = ExpertClassification(ExpertType.PRODUCTION_RND, 80, 20, listOf("RND_PRODUCTION"),
                emptyList(), ExpertClassificationService.VERSION, "fixture", LocalDateTime.of(2026, 10, 22, 0, 0))
        )
        private fun throwawayAttempt() = MailSendAttempt(orcidId = "x", mailType = "INTRODUCTION", accountCode = "capture", messageId = "fixture", status = "PREPARED")
        private fun <T> mock(type: Class<T>): T = Mockito.mock(type)
        private fun <T> any(fallback: T): T = Mockito.any<T>() ?: fallback
        private fun anyNullableLong(): Long? = Mockito.any<Long>()
        private fun anyNullableDate(): LocalDateTime? = Mockito.any<LocalDateTime>()
    }
}
