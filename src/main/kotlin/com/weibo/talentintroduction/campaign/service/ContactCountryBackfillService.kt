package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(
    prefix = "talent-introduction.backfill.contact-country",
    name = ["enabled"],
    havingValue = "true"
)
class ContactCountryBackfillService(
    private val expertContactRepository: ExpertContactRepository,
    private val expertSearchService: ExpertSearchService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(ContactCountryBackfillService::class.java)

    override fun run(args: ApplicationArguments?) {
        runBackfill()
    }

    @Transactional
    fun runBackfill(): ContactCountryBackfillResult {
        val all = expertContactRepository.findAll().toList()
        val pending = all.filter { it.country == null }
        val skipped = all.size - pending.size

        if (pending.isEmpty()) {
            log.info(
                "Contact country backfill complete: processed=0, matched=0, unmatched=0, skipped={}",
                skipped
            )
            return ContactCountryBackfillResult(processed = 0, matched = 0, unmatched = 0, skipped = skipped)
        }

        val countryByOrcid = mutableMapOf<String, String?>()
        pending.groupBy { it.currentIndexLevel }
            .forEach { (indexLevel, contacts) ->
                val level = toExpertIndexLevel(indexLevel) ?: return@forEach
                contacts.map { ExpertIdNormalizer.normalize(it.orcidId) }
                    .distinct()
                    .chunked(ORCID_BATCH_SIZE)
                    .forEach { orcidBatch ->
                        expertSearchService.searchByOrcidIds(orcidBatch, level)
                            .forEach { profile ->
                                countryByOrcid[ExpertIdNormalizer.normalize(profile.orcidId)] = profile.country
                            }
                    }
            }

        var matched = 0
        var unmatched = 0
        pending.forEach { contact ->
            val contactId = contact.id
            if (contactId == null) {
                log.warn("Skipping contact without id, orcidId={}", contact.orcidId)
                unmatched++
                return@forEach
            }
            val country = countryByOrcid[ExpertIdNormalizer.normalize(contact.orcidId)]
            if (country != null) {
                matched++
            } else {
                unmatched++
            }
            expertContactRepository.updateCountryById(contactId, country)
        }

        val result = ContactCountryBackfillResult(
            processed = pending.size,
            matched = matched,
            unmatched = unmatched,
            skipped = skipped
        )
        log.info(
            "Contact country backfill complete: processed={}, matched={}, unmatched={}, skipped={}",
            result.processed,
            result.matched,
            result.unmatched,
            result.skipped
        )
        return result
    }

    private fun toExpertIndexLevel(currentIndexLevel: String): ExpertIndexLevel? =
        when (currentIndexLevel) {
            ExpertIndexLevel.CANDIDATE.name -> ExpertIndexLevel.CANDIDATE
            ExpertIndexLevel.APPLICATION.name -> ExpertIndexLevel.APPLICATION
            else -> {
                log.warn("Skipping contacts with unsupported currentIndexLevel={}", currentIndexLevel)
                null
            }
        }

    companion object {
        private const val ORCID_BATCH_SIZE = 500
    }
}

data class ContactCountryBackfillResult(
    val processed: Int,
    val matched: Int,
    val unmatched: Int,
    val skipped: Int
)
