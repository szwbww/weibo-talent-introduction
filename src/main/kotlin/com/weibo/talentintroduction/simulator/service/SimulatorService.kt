package com.weibo.talentintroduction.simulator.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.config.SimulatorProperties
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.InboundIntent
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.MailBodyCleaner
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.NoopMailDeliveryService
import com.weibo.talentintroduction.mail.service.ReceivedMail
import com.weibo.talentintroduction.mail.service.ReceivedMailAttachment
import com.weibo.talentintroduction.simulator.dto.ExpertContactResponse
import com.weibo.talentintroduction.simulator.dto.InboundRequest
import com.weibo.talentintroduction.simulator.dto.ResetContactRequest
import com.weibo.talentintroduction.simulator.dto.ScenarioRunResult
import com.weibo.talentintroduction.simulator.dto.SeedContactRequest
import com.weibo.talentintroduction.simulator.dto.SimulateInboundResponse
import com.weibo.talentintroduction.simulator.dto.SimulatorSnapshot
import com.weibo.talentintroduction.simulator.dto.StepResult
import com.weibo.talentintroduction.simulator.dto.toAssertion
import com.weibo.talentintroduction.simulator.dto.toResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Service
@Profile("simulator")
@ConditionalOnProperty("talent-introduction.simulator.enabled", havingValue = "true")
class SimulatorService(
    private val props: SimulatorProperties,
    private val campaignRepository: CampaignRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val inboundIntentRepository: InboundIntentRepository,
    private val manualHandoffRepository: ManualHandoffRepository,
    private val statusHistoryRepository: ExpertContactStatusHistoryRepository,
    private val meetingScheduleRepository: MeetingScheduleRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val autoMailReplyService: AutoMailReplyService,
    private val conversationStateService: ConversationStateService,
    private val expertContactManagementService: ExpertContactManagementService,
    private val noop: NoopMailDeliveryService
) {
    private val imapUidSeq = AtomicLong(1_000_000L)

    private fun resolveCampaignId(): Long {
        val campaign = campaignRepository.findByCampaignCode("SIMULATOR")
            ?: error("Simulator campaign not found. Run Flyway migration V16 first.")
        return campaign.id ?: error("Campaign id is null")
    }

    @Transactional
    fun seedContact(req: SeedContactRequest): ExpertContactResponse {
        val now = LocalDateTime.now()
        val seq = imapUidSeq.incrementAndGet()
        val expertName = req.expertName ?: "Sim Expert $seq"
        val orcidId = "${props.orcidPrefix}${seq}"
        val email = "${props.emailPrefix}${seq}@simulator.local"
        val initialStatus = req.initialStatus ?: ConversationStatus.INTRO_SENT.name

        val resolvedCampaignId = resolveCampaignId()
        val contact = expertContactRepository.save(
            ExpertContact(
                campaignId = resolvedCampaignId,
                orcidId = orcidId,
                expertEmail = email,
                expertName = expertName,
                currentStatus = initialStatus,
                createdAt = now,
                updatedAt = now
            )
        )

        val contactId = contact.id ?: error("Contact id required")
        if (req.createIntroductionMailRecord) {
            mailRecordRepository.save(
                MailRecord(
                    expertContactId = contactId,
                    direction = "OUTBOUND",
                    mailType = "INTRODUCTION",
                    senderAccountCode = props.senderAccountCode,
                    messageId = "sim-intro-${seq}@simulator.local",
                    inReplyTo = null,
                    subject = "Introduction to Talent Program",
                    body = "This is a simulated introduction email.",
                    matchedQaRuleId = null,
                    sendStatus = "SIMULATED",
                    receivedAt = null,
                    sentAt = now,
                    createdAt = now
                )
            )
        }

        return contact.toResponse()
    }

    @Transactional
    fun resetContact(id: Long, req: ResetContactRequest): ExpertContactResponse {
        assertSimulatorContact(id)
        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(id)
        mails.forEach { mail ->
            mail.id?.let { mid ->
                mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(mid).forEach { att ->
                    mailAttachmentRepository.delete(att)
                }
            }
        }
        mails.forEach { mail -> mailRecordRepository.delete(mail) }
        inboundMailProcessingRepository.findAllByExpertContactIdOrderByReceivedAtAsc(id).forEach { inboundMailProcessingRepository.delete(it) }
        inboundIntentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(id).forEach { inboundIntentRepository.delete(it) }
        manualHandoffRepository.findAllByExpertContactIdAndHandoffStatusIn(id, listOf("PENDING", "ASSIGNED")).forEach { manualHandoffRepository.delete(it) }
        meetingScheduleRepository.findAllByExpertContactIdOrderByCreatedAtDesc(id).forEach { meetingScheduleRepository.delete(it) }
        statusHistoryRepository.findAllByExpertContactIdOrderByCreatedAtAsc(id).forEach { statusHistoryRepository.delete(it) }
        expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(id).forEach { expertDocumentRepository.delete(it) }

        val contact = expertContactRepository.findById(id).orElseThrow()
        val initialStatus = req.initialStatus ?: ConversationStatus.INTRO_SENT.name
        val updated = expertContactRepository.save(
            contact.copy(
                currentStatus = initialStatus,
                autoReplyEnabled = true,
                manualHandoffRequired = false,
                needsManualAttention = false,
                applicationIndexed = false,
                currentIndexLevel = "CANDIDATE",
                firstReplyAt = null,
                lastReplyAt = null,
                lastMailAt = null
            )
        )

        if (req.createIntroductionMailRecord) {
            val now = LocalDateTime.now()
            mailRecordRepository.save(
                MailRecord(
                    expertContactId = id,
                    direction = "OUTBOUND",
                    mailType = "INTRODUCTION",
                    senderAccountCode = props.senderAccountCode,
                    messageId = "sim-intro-reset-${id}@simulator.local",
                    inReplyTo = null,
                    subject = "Introduction to Talent Program",
                    body = "This is a simulated introduction email.",
                    matchedQaRuleId = null,
                    sendStatus = "SIMULATED",
                    receivedAt = null,
                    sentAt = now,
                    createdAt = now
                )
            )
        }

        return updated.toResponse()
    }

    fun listContacts(): List<ExpertContactResponse> =
        expertContactRepository
            .findFilteredContacts(resolveCampaignId(), status = null, needsAttention = null)
            .map { it.toResponse() }

    fun snapshot(id: Long): SimulatorSnapshot {
        assertSimulatorContact(id)
        val detail = expertContactManagementService.getContactDetail(id)
        return SimulatorSnapshot(
            contact = detail.contact.toResponse(),
            mails = detail.mails.map { it.toResponse() },
            statusHistory = detail.statusHistory.map { it.toResponse() },
            latestHandoff = detail.latestHandoff?.toResponse(),
            meetingSchedules = detail.meetingSchedules.map { it.toResponse() },
            recommendedNextAction = detail.recommendedNextAction,
            inboundIntents = inboundIntentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(id).map { it.toResponse() },
            inboundProcessing = inboundMailProcessingRepository.findAllByExpertContactIdOrderByReceivedAtAsc(id).map { it.toResponse() },
            applicationIndexLevel = detail.contact.currentIndexLevel
        )
    }

    @Transactional
    fun simulateInbound(id: Long, req: InboundRequest): SimulateInboundResponse {
        assertSimulatorContact(id)
        val account = mailSenderAccountService.getEnabledAccount(props.senderAccountCode)
        val contact = expertContactRepository.findById(id).orElseThrow()
        val received = ReceivedMail(
            imapUid = imapUidSeq.incrementAndGet(),
            from = req.overrideFromEmail ?: contact.expertEmail,
            subject = req.subject,
            body = req.body,
            messageId = "simulator-${UUID.randomUUID()}@local",
            inReplyTo = null,
            receivedAt = LocalDateTime.now(),
            attachments = req.attachments.map { a ->
                ReceivedMailAttachment(
                    fileName = a.fileName,
                    contentType = a.contentType,
                    content = Base64.getDecoder().decode(a.contentBase64)
                )
            }
        )
        val before = expertContactRepository.findById(id).orElseThrow().currentStatus
        val result = autoMailReplyService.processSingle(account, received, skipImapAck = true)
        val after = expertContactRepository.findById(id).orElseThrow().currentStatus

        val assertion = req.toAssertion(result, previousStatus = before, currentStatus = after)
        return SimulateInboundResponse(result = result.toResponse(), assertion = assertion)
    }

    fun listPresets(): List<SimulatorPreset> = SIMULATOR_PRESETS

    fun outboundBuffer(): List<com.weibo.talentintroduction.mail.service.SimulatedOutbound> = noop.snapshot()

    fun listScenarios(): List<Scenario> = SIMULATOR_SCENARIOS

    @Transactional
    fun runScenario(key: String): ScenarioRunResult {
        val scenario = SIMULATOR_SCENARIOS.find { it.key == key }
            ?: throw IllegalArgumentException("Scenario not found: $key")

        val contactResp = seedContact(SeedContactRequest(expertName = "Scenario-$key"))
        val contactId = contactResp.id
        val stepResults = mutableListOf<StepResult>()
        var prevImapUid: Long? = null

        scenario.steps.forEachIndexed { idx, step ->
            when (step.kind) {
                ScenarioStepKind.INBOUND -> {
                    if (step.stripIntroductionBeforeRun) {
                        resetContact(contactId, ResetContactRequest(initialStatus = "INTRO_SENT", createIntroductionMailRecord = false))
                    }

                    val preset = step.presetKey?.let { pk -> SIMULATOR_PRESETS.find { it.key == pk } }
                    val subject = preset?.subject
                    val body = step.customBody ?: preset?.body ?: ""
                    val account = mailSenderAccountService.getEnabledAccount(props.senderAccountCode)

                    val reqBuilder = InboundRequest(
                        subject = subject,
                        body = body,
                        overrideFromEmail = step.overrideFromEmail,
                        expectedIntent = step.expectedIntent ?: preset?.expectedIntent,
                        expectedAutoAction = step.expectedAutoAction ?: preset?.expectedAutoAction,
                        expectedNewStatus = step.expectedNewStatus
                    )

                    val response = if (step.forceDuplicateOfPreviousImapUid && prevImapUid != null) {
                        val contact = expertContactRepository.findById(contactId).orElseThrow()
                        val received = ReceivedMail(
                            imapUid = prevImapUid!!,
                            from = step.overrideFromEmail ?: contact.expertEmail,
                            subject = subject,
                            body = body,
                            messageId = "simulator-${UUID.randomUUID()}@local",
                            inReplyTo = null,
                            receivedAt = LocalDateTime.now(),
                            attachments = emptyList()
                        )
                        val before = expertContactRepository.findById(contactId).orElseThrow().currentStatus
                        val result = autoMailReplyService.processSingle(account, received, skipImapAck = true)
                        val after = expertContactRepository.findById(contactId).orElseThrow().currentStatus
                        val assertion = reqBuilder.toAssertion(result, previousStatus = before, currentStatus = after)
                        SimulateInboundResponse(result = result.toResponse(), assertion = assertion)
                    } else {
                        simulateInbound(contactId, reqBuilder)
                    }

                    val resultImapUid = imapUidSeq.get()
                    prevImapUid = resultImapUid

                    stepResults.add(StepResult(
                        stepIndex = idx,
                        stepKind = "INBOUND",
                        presetKey = step.presetKey,
                        manualApiPath = null,
                        result = response.result,
                        assertion = response.assertion,
                        contactSnapshot = snapshot(contactId)
                    ))
                }

                ScenarioStepKind.MANUAL_API -> {
                    val path = step.manualApiPath ?: ""
                    when {
                        path.contains("switch-to-manual") -> {
                            expertContactManagementService.switchToManual(contactId,
                                step.manualApiBody?.get("reason") as? String,
                                step.manualApiBody?.get("note") as? String)
                        }
                        path.contains("switch-to-auto") -> {
                            expertContactManagementService.switchToAuto(contactId,
                                step.manualApiBody?.get("note") as? String)
                        }
                    }

                    val snap = snapshot(contactId)
                    val currentStatus = snap.contact.currentStatus
                    val statusOk = step.expectedNewStatus == currentStatus
                    stepResults.add(StepResult(
                        stepIndex = idx,
                        stepKind = "MANUAL_API",
                        presetKey = null,
                        manualApiPath = path,
                        result = null,
                        assertion = com.weibo.talentintroduction.simulator.dto.AssertionResult(
                            passed = statusOk,
                            expectedIntent = null, actualIntent = null, intentOk = null,
                            expectedAutoAction = null, actualAutoAction = null, autoActionOk = null,
                            expectedNewStatus = step.expectedNewStatus, actualNewStatus = currentStatus, statusOk = statusOk,
                            previousStatus = null
                        ),
                        contactSnapshot = snap
                    ))
                }
            }
        }

        val passed = stepResults.all { it.assertion?.passed == true }
        return ScenarioRunResult(
            scenarioKey = key,
            scenarioName = scenario.name,
            contactId = contactId,
            steps = stepResults,
            passed = passed
        )
    }

    private fun assertSimulatorContact(id: Long) {
        val contact = expertContactRepository.findById(id)
            .orElseThrow { IllegalArgumentException("contact $id not found") }
        require(contact.campaignId == resolveCampaignId()) {
            "simulator endpoint refuses to touch non-simulator contact (campaignId=${contact.campaignId})"
        }
    }
}
