package com.weibo.talentintroduction.variant.repository

import com.weibo.talentintroduction.variant.domain.ContentVariant
import org.springframework.data.repository.CrudRepository

interface ContentVariantRepository : CrudRepository<ContentVariant, Long> {
    fun findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
        ownerType: String,
        ownerId: Long
    ): List<ContentVariant>

    fun findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
        ownerType: String,
        ownerId: Long
    ): List<ContentVariant>

    fun deleteByOwnerTypeAndOwnerId(ownerType: String, ownerId: Long)
}
