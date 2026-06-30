package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.BatchSendSetting
import com.weibo.talentintroduction.campaign.repository.BatchSendSettingRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AutoReplySettingService(
    private val repository: BatchSendSettingRepository
) {
    fun isGlobalEnabled(): Boolean {
        val row = repository.findBySettingKey(KEY)
        return row?.settingValue?.toBooleanStrictOrNull() ?: DEFAULT
    }

    fun setGlobalEnabled(enabled: Boolean) {
        val existing = repository.findBySettingKey(KEY)
        repository.save(
            BatchSendSetting(
                id = existing?.id,
                settingKey = KEY,
                settingValue = enabled.toString(),
                updatedAt = LocalDateTime.now()
            )
        )
    }

    private companion object {
        const val KEY = "autoReply.globalEnabled"
        const val DEFAULT = false
    }
}
