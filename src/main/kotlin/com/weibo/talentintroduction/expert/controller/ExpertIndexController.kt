package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/experts")
class ExpertIndexController(
    private val expertSearchService: ExpertSearchService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    @GetMapping
    fun listExperts(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(defaultValue = "50") size: Int
    ): List<ExpertIndexResponse> =
        expertSearchService.searchExperts(size, level)
            .map { expert ->
                val contact = expert.orcidId
                    .takeIf { it.isNotBlank() }
                    ?.let(expertContactRepository::findFirstByOrcidIdOrderByUpdatedAtDesc)
                ExpertIndexResponse.from(expert, level, contact?.id, contact?.currentStatus)
            }

    @PostMapping("/reindex-applications")
    fun reindexApplications(
        @RequestParam(defaultValue = "100") limit: Int
    ): ReindexResult {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
        val contacts = expertContactRepository
            .findAllByOrderByUpdatedAtDesc()
            .filter { !it.applicationIndexed && it.firstReplyAt != null }
            .take(limit)
        var success = 0
        var failure = 0
        contacts.forEach { contact ->
            val firstReply = contact.firstReplyAt ?: return@forEach
            val ok = expertIndexWriterService.promoteToApplication(
                orcid = contact.orcidId,
                contact = contact,
                firstReplyAt = firstReply.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(firstReply))
            )
            if (ok) {
                expertContactRepository.save(contact.copy(applicationIndexed = true))
                success += 1
            } else {
                failure += 1
            }
        }
        return ReindexResult(total = contacts.size, success = success, failure = failure)
    }
}

data class ReindexResult(
    val total: Int,
    val success: Int,
    val failure: Int
)

data class ExpertIndexResponse(
    val indexLevel: String,
    val indexLevelName: String,
    val orcidId: String,
    val email: String?,
    val displayName: String,
    val country: String?,
    val keyword: String?,
    val employment: String?,
    val age: Int?,
    val degree: String?,
    val nationality: String?,
    val contactId: Long?,
    val contactStatus: String?
) {
    companion object {
        fun from(
            expert: ExpertProfile,
            level: ExpertIndexLevel,
            contactId: Long?,
            contactStatus: String?
        ): ExpertIndexResponse =
            ExpertIndexResponse(
                indexLevel = level.name,
                indexLevelName = when (level) {
                    ExpertIndexLevel.RAW -> "原始"
                    ExpertIndexLevel.CANDIDATE -> "筛选"
                    ExpertIndexLevel.APPLICATION -> "有效"
                },
                orcidId = expert.orcidId,
                email = expert.email,
                displayName = expert.displayName,
                country = expert.country,
                keyword = expert.keyword,
                employment = expert.employment,
                age = expert.age,
                degree = expert.degree,
                nationality = expert.nationality,
                contactId = contactId,
                contactStatus = contactStatus
            )
    }
}
