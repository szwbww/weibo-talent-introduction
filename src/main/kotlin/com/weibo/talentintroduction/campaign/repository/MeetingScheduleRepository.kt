package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import org.springframework.data.repository.CrudRepository

interface MeetingScheduleRepository : CrudRepository<MeetingSchedule, Long> {
    fun findAllByExpertContactIdOrderByCreatedAtDesc(expertContactId: Long): List<MeetingSchedule>
}
