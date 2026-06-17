package com.weibo.talentintroduction.expert.repository

import com.weibo.talentintroduction.expert.domain.EligibilityFilterSetting
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface EligibilityFilterSettingRepository : CrudRepository<EligibilityFilterSetting, Long> {

    @Query("SELECT * FROM eligibility_filter_setting WHERE setting_key = :settingKey LIMIT 1")
    fun findBySettingKey(settingKey: String): EligibilityFilterSetting?

    override fun findAll(): List<EligibilityFilterSetting>
}
