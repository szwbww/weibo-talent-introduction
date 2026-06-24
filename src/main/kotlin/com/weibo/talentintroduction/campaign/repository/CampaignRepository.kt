package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.Campaign
import org.springframework.data.repository.CrudRepository

interface CampaignRepository : CrudRepository<Campaign, Long> {
    fun findByCampaignCode(campaignCode: String): Campaign?

    fun existsBySenderAccountId(senderAccountId: Long): Boolean
}
