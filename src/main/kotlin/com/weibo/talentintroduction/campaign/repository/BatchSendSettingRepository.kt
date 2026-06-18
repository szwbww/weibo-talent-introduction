package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.BatchSendSetting
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface BatchSendSettingRepository : CrudRepository<BatchSendSetting, Long> {

    @Query("SELECT * FROM batch_send_setting WHERE setting_key = :settingKey LIMIT 1")
    fun findBySettingKey(settingKey: String): BatchSendSetting?

    override fun findAll(): List<BatchSendSetting>
}
