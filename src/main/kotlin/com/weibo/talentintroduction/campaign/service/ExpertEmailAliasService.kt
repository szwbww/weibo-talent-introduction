package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertEmailAliasRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ExpertEmailAliasService(
    private val expertEmailAliasRepository: ExpertEmailAliasRepository,
    private val expertContactRepository: ExpertContactRepository
) {
    fun normalizeEmail(email: String): String =
        email.trim().lowercase()

    fun findContactByEmail(email: String): ExpertContact? {
        val normalized = normalizeEmail(email)
        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
        val alias = expertEmailAliasRepository.findByNormalizedEmail(normalized)
            ?: return null
        return expertContactRepository.findById(alias.expertContactId).orElse(null)
    }

    fun findContactByEmailOrAlias(email: String): ExpertContact? {
        val normalized = normalizeEmail(email)
        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(normalized)?.let { return it }
        expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)?.let { return it }
        val alias = expertEmailAliasRepository.findByNormalizedEmail(normalized)
            ?: return null
        return expertContactRepository.findById(alias.expertContactId).orElse(null)
    }

    @Transactional
    fun addAlias(
        expertContactId: Long,
        email: String,
        source: String = "MANUAL_ADD"
    ): ExpertEmailAlias {
        expertContactRepository.findById(expertContactId)
            .orElseThrow { error("Expert contact not found: $expertContactId") }
        val normalized = normalizeEmail(email)
        require(!expertEmailAliasRepository.existsByNormalizedEmail(normalized)) {
            "Alias already bound to another contact: $email"
        }
        return expertEmailAliasRepository.save(
            ExpertEmailAlias(
                expertContactId = expertContactId,
                email = email,
                normalizedEmail = normalized,
                source = source,
                verified = true,
                createdAt = LocalDateTime.now()
            )
        )
    }

    @Transactional
    fun bindAlias(
        expertContactId: Long,
        email: String,
        source: String = "MANUAL_BIND"
    ): ExpertEmailAlias {
        val normalized = normalizeEmail(email)
        val existing = expertEmailAliasRepository.findByNormalizedEmail(normalized)
        if (existing != null) {
            require(existing.expertContactId == expertContactId) {
                "Alias already bound to another contact: $email"
            }
            return existing
        }
        return addAlias(expertContactId, email, source)
    }

    fun listAliases(expertContactId: Long): List<ExpertEmailAlias> =
        expertEmailAliasRepository.findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId)

    @Transactional
    fun deleteAlias(aliasId: Long) {
        expertEmailAliasRepository.deleteById(aliasId)
    }
}
