package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface DiscoverySourceCursorRepository : CrudRepository<DiscoverySourceCursor, Long> {

    @Query("SELECT * FROM discovery_source_cursor WHERE source_name = :sourceName")
    fun findBySourceName(sourceName: String): DiscoverySourceCursor?
}
