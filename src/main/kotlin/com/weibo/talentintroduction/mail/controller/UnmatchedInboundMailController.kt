package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.service.CandidateSuggestion
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/mail")
class UnmatchedInboundMailController(
    private val unmatchedInboundMailService: UnmatchedInboundMailService,
    private val expertEmailAliasService: ExpertEmailAliasService
) {
    @GetMapping("/unmatched-inbound")
    fun listUnmatched(): UnmatchedListResponse {
        val records = unmatchedInboundMailService.listUnmatched()
        val count = unmatchedInboundMailService.countUnmatched()
        return UnmatchedListResponse(
            totalCount = count,
            records = records.map { it.toResponse() }
        )
    }

    @GetMapping("/unmatched-inbound/{id}")
    fun getUnmatchedDetail(@PathVariable id: Long): UnmatchedDetailResponse {
        val record = unmatchedInboundMailService.getDetail(id)
        val candidates = unmatchedInboundMailService.suggestCandidates(record)
        return UnmatchedDetailResponse(
            record = record.toResponse(),
            candidates = candidates.map { it.toResponse() }
        )
    }

    @PostMapping("/unmatched-inbound/{id}/bind")
    fun bindUnmatched(
        @PathVariable id: Long,
        @RequestBody request: BindUnmatchedRequest
    ): InboundMailProcessingResponse {
        val result = unmatchedInboundMailService.bindToContact(
            recordId = id,
            contactId = request.contactId,
            resolvedBy = request.resolvedBy
        )
        return result.toResponse()
    }

    @GetMapping("/unmatched-inbound/search-contacts")
    fun searchContacts(@RequestParam query: String): List<CandidateResponse> {
        val contacts = unmatchedInboundMailService.searchContacts(query)
        return contacts.map {
            CandidateResponse(
                contactId = it.id ?: 0,
                orcidId = it.orcidId,
                expertName = it.expertName,
                expertEmail = it.expertEmail,
                reason = "SEARCH",
                confidence = 100
            )
        }
    }
}

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertEmailAliasController(
    private val expertEmailAliasService: ExpertEmailAliasService
) {
    @GetMapping("/{contactId}/email-aliases")
    fun listAliases(@PathVariable contactId: Long): List<ExpertEmailAliasResponse> =
        expertEmailAliasService.listAliases(contactId).map { it.toResponse() }

    @PostMapping("/{contactId}/email-aliases")
    fun addAlias(
        @PathVariable contactId: Long,
        @RequestBody request: AddAliasRequest
    ): ExpertEmailAliasResponse =
        expertEmailAliasService.addAlias(
            expertContactId = contactId,
            email = request.email,
            source = request.source ?: "MANUAL_ADD"
        ).toResponse()

    @DeleteMapping("/{contactId}/email-aliases/{aliasId}")
    fun deleteAlias(
        @PathVariable contactId: Long,
        @PathVariable aliasId: Long
    ) {
        expertEmailAliasService.deleteAlias(aliasId)
    }
}

data class UnmatchedListResponse(
    val totalCount: Long,
    val records: List<InboundMailProcessingResponse>
)

data class UnmatchedDetailResponse(
    val record: InboundMailProcessingResponse,
    val candidates: List<CandidateResponse>
)

data class InboundMailProcessingResponse(
    val id: Long?,
    val senderAccountCode: String,
    val imapUid: Long,
    val messageId: String?,
    val inReplyTo: String?,
    val fromEmail: String,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val receivedAt: String?,
    val processStatus: String,
    val processReason: String,
    val resolvedAt: String?,
    val resolvedBy: String?,
    val expertContactId: Long?
)

data class CandidateResponse(
    val contactId: Long,
    val orcidId: String,
    val expertName: String?,
    val expertEmail: String,
    val reason: String,
    val confidence: Int
)

data class BindUnmatchedRequest(
    val contactId: Long,
    val resolvedBy: String
)

data class AddAliasRequest(
    val email: String,
    val source: String?
)

data class ExpertEmailAliasResponse(
    val id: Long?,
    val expertContactId: Long,
    val email: String,
    val normalizedEmail: String,
    val source: String,
    val verified: Boolean,
    val createdAt: String?
)

private fun InboundMailProcessing.toResponse() = InboundMailProcessingResponse(
    id = id,
    senderAccountCode = senderAccountCode,
    imapUid = imapUid,
    messageId = messageId,
    inReplyTo = inReplyTo,
    fromEmail = fromEmail,
    subject = subject,
    body = body,
    cleanedBody = cleanedBody,
    receivedAt = receivedAt.toString(),
    processStatus = processStatus,
    processReason = processReason,
    resolvedAt = resolvedAt?.toString(),
    resolvedBy = resolvedBy,
    expertContactId = expertContactId
)

private fun CandidateSuggestion.toResponse() = CandidateResponse(
    contactId = contact.id ?: 0,
    orcidId = contact.orcidId,
    expertName = contact.expertName,
    expertEmail = contact.expertEmail,
    reason = reason,
    confidence = confidence
)

private fun ExpertEmailAlias.toResponse() = ExpertEmailAliasResponse(
    id = id,
    expertContactId = expertContactId,
    email = email,
    normalizedEmail = normalizedEmail,
    source = source,
    verified = verified,
    createdAt = createdAt?.toString()
)
