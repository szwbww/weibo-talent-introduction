package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

/**
 * 01（I-1）：已存在专家身份核验。04 视频创建复用同一核验（404/503 语义经显式映射）。
 *
 * - 稳定身份 = [ExpertIdNormalizer.normalize]`(trim(expertId))`，长度 1–64（Unicode code point）；
 * - 存在性证据依次为：本表已有档案行 → `expert_contact` 存在同 key 联系人 → ES 各层按 orcidId 精确命中；
 * - ES 只按 `orcidId` 字段查询，**不用 `_id` 猜身份**；命中记录的 orcidId 规范化后必须等于请求 key；
 * - 已有档案行在 ES 临时失联时仍可读写；查询服务失败一律 503，绝不伪装 404，也不新建空专家。
 */
@Service
class ExpertProfileIdentityResolver(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val expertContactRepository: ExpertContactRepository,
    private val expertSearchService: ExpertSearchService
) {
    /** 规范化并核验身份；返回可直接落库的专家 key。 */
    fun requireKnown(expertId: String?, level: ExpertIndexLevel): String {
        val raw = expertId?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw ExpertManualProfileInvalidRequestException("缺少专家标识，无法保存")
        }
        val expertKey = ExpertIdNormalizer.normalize(raw)
        if (expertKey.codePointCount(0, expertKey.length) > MAX_EXPERT_KEY_LENGTH || expertKey.isEmpty()) {
            throw ExpertManualProfileInvalidRequestException("专家标识长度必须在1到${MAX_EXPERT_KEY_LENGTH}个字符之间")
        }
        val lookupKeys = listOf(raw, expertKey).distinct()

        if (profileRowExists(expertKey)) return expertKey
        if (contactExists(lookupKeys)) return expertKey

        for (candidateLevel in orderedLevels(level)) {
            for (lookupKey in lookupKeys) {
                val profile = findProfile(lookupKey, candidateLevel) ?: continue
                if (ExpertIdNormalizer.normalize(profile.orcidId) == expertKey) return expertKey
            }
        }
        throw ExpertProfileNotFoundException("专家不存在")
    }

    private fun profileRowExists(expertKey: String): Boolean =
        try {
            val count = jdbcTemplate.queryForObject(
                PROFILE_EXISTS_SQL,
                MapSqlParameterSource("expertKey", expertKey),
                Long::class.java
            ) ?: 0L
            count > 0L
        } catch (ex: DataAccessException) {
            throw ExpertProfileLookupUnavailableException(LOOKUP_UNAVAILABLE_MESSAGE, ex)
        }

    private fun contactExists(lookupKeys: List<String>): Boolean =
        try {
            lookupKeys.any { expertContactRepository.existsByOrcidId(it) }
        } catch (ex: ExpertManualProfileException) {
            throw ex
        } catch (ex: Exception) {
            throw ExpertProfileLookupUnavailableException(LOOKUP_UNAVAILABLE_MESSAGE, ex)
        }

    private fun findProfile(orcidId: String, level: ExpertIndexLevel): ExpertProfile? =
        try {
            expertSearchService.findByOrcidId(orcidId, level)
        } catch (ex: Exception) {
            throw ExpertProfileLookupUnavailableException(LOOKUP_UNAVAILABLE_MESSAGE, ex)
        }

    /** 先查请求层，再按 RAW/CANDIDATE/APPLICATION 顺序补齐其余层（晋升/降级后仍定位同一专家）。 */
    private fun orderedLevels(level: ExpertIndexLevel): List<ExpertIndexLevel> =
        listOf(level) + ExpertIndexLevel.values().filter { it != level }

    private companion object {
        const val MAX_EXPERT_KEY_LENGTH = 64
        const val LOOKUP_UNAVAILABLE_MESSAGE = "专家身份查询服务不可用，请稍后重试"
        const val PROFILE_EXISTS_SQL = "SELECT COUNT(*) FROM expert_manual_profile WHERE expert_key=:expertKey"
    }
}
