package com.weibo.talentintroduction.variant.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentVariantService(
    private val contentVariantRepository: ContentVariantRepository,
    private val mailVariableService: MailVariableService
) {
    fun resolveBody(
        ownerType: String,
        ownerId: Long?,
        mainBody: String,
        seed: Int,
        useVariants: Boolean = true
    ): String {
        val pool = buildPool(ownerType, ownerId, mainBody, useVariants)
        if (pool.size <= 1) {
            return mainBody
        }
        val index = Math.floorMod(seed + ownerId!!, pool.size)
        return pool[index]
    }

    fun poolSize(ownerType: String, ownerId: Long?, mainBody: String, useVariants: Boolean = true): Int =
        buildPool(ownerType, ownerId, mainBody, useVariants).size

    fun listByOwner(ownerType: String, ownerId: Long): List<ContentVariant> =
        contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(ownerType, ownerId)

    fun validateVariantTexts(mainBody: String, variants: List<String>) {
        validateVariants(mainBody, variants)
    }

    @Transactional
    fun replaceForOwner(ownerType: String, ownerId: Long, mainBody: String, variants: List<String>) {
        require(ContentVariantOwnerType.isKnown(ownerType)) { "Unsupported content variant owner type: $ownerType" }
        validateVariants(mainBody, variants)
        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
        if (variants.isEmpty()) {
            return
        }
        val now = LocalDateTime.now()
        variants.forEachIndexed { index, raw ->
            contentVariantRepository.save(
                ContentVariant(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    variantOrder = index * 10 + 10,
                    content = raw.trim(),
                    enabled = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun deleteForOwner(ownerType: String, ownerId: Long) {
        if (!ContentVariantOwnerType.isKnown(ownerType)) {
            return
        }
        contentVariantRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId)
    }

    private fun validateVariants(mainBody: String, variants: List<String>) {
        val trimmedMain = mainBody.trim()
        val seen = mutableSetOf<String>()
        variants.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("变体第 ${index + 1} 项不能为空")
            }
            if (trimmed == trimmedMain) {
                throw IllegalArgumentException("变体不能与主体重复")
            }
            if (!seen.add(trimmed)) {
                throw IllegalArgumentException("变体不能重复")
            }
            mailVariableService.requireValidPlaceholders(trimmed)
        }
    }

    private fun buildPool(
        ownerType: String,
        ownerId: Long?,
        mainBody: String,
        useVariants: Boolean
    ): List<String> {
        if (!useVariants || ownerId == null || !ContentVariantOwnerType.isKnown(ownerType)) {
            return listOf(mainBody)
        }
        val variants = contentVariantRepository
            .findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(ownerType, ownerId)
        if (variants.isEmpty()) {
            return listOf(mainBody)
        }
        return listOf(mainBody) + variants.map { it.content }
    }
}
