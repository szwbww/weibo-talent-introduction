package com.weibo.talentintroduction.handoff.repository

import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import org.springframework.data.repository.CrudRepository

interface ManualHandoffRepository : CrudRepository<ManualHandoff, Long> {
    fun findAllByOrderByUpdatedAtDesc(): List<ManualHandoff>

    fun findAllByHandoffStatusOrderByUpdatedAtDesc(handoffStatus: String): List<ManualHandoff>

    fun findFirstByExpertContactIdOrderByUpdatedAtDesc(expertContactId: Long): ManualHandoff?

    fun findFirstByExpertContactIdAndReasonAndHandoffStatusOrderByUpdatedAtDesc(
        expertContactId: Long,
        reason: String,
        handoffStatus: String
    ): ManualHandoff?
}
