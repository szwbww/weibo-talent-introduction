package com.weibo.talentintroduction.discovery.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("discovery_source_cursor")
data class DiscoverySourceCursor(
    @Id val id: Long? = null,
    val sourceName: String,
    val cursorValue: String? = null,
    val papersProcessedTotal: Long = 0,
    val lastRunAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
