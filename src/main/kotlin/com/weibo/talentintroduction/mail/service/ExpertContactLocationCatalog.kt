package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.ZoneId

/**
 * 随包国家/时区目录（plan 01 / c1，I-2/I-3）。
 *
 * 唯一数据来源是 classpath 上的 `contact-country-timezones.json` 快照（证据目录
 * `country-timezones.proposed.json` 的逐字副本）：不联网查位置、不解析
 * `expert_contact.country` 文本、不回退 UTC、不静默丢弃无法识别的时区、不新增国家
 * 自动识别器。加载即校验；任何结构非法都抛出确定的配置错误（启动即失败），
 * 绝不提供一个「差不多」的目录。
 *
 * 顺序即资源快照顺序：国家顺序、国家内时区顺序都不重排、不按偏移去重。
 */
@Service
class ExpertContactLocationCatalog {

    private val catalog: ContactCountryTimezoneCatalog = load()

    /** 完整目录（GET /api/mail/contact-locations/countries 的响应模型）。 */
    fun all(): ContactCountryTimezoneCatalog = catalog

    /** 按大写 ISO 二字码查国家；未知代码返回 null（调用方决定 400，绝不猜国家）。 */
    fun country(code: String): ContactCountryEntry? = catalog.find(code)

    private fun load(): ContactCountryTimezoneCatalog {
        val resource = ClassPathResource(RESOURCE_PATH)
        if (!resource.exists()) {
            configError("缺少目录资源 $RESOURCE_PATH")
        }
        val parsed = try {
            resource.inputStream.use { jacksonObjectMapper().readValue(it, ContactCountryTimezoneCatalog::class.java) }
        } catch (ex: Exception) {
            configError("目录资源 $RESOURCE_PATH 无法解析：${ex.message}", ex)
        }
        validate(parsed)
        return parsed
    }

    private fun validate(catalog: ContactCountryTimezoneCatalog) {
        if (catalog.sourceVersion.isBlank()) configError("目录缺少 sourceVersion")
        if (catalog.sourceUrl.isBlank()) configError("目录缺少 sourceUrl")
        if (catalog.defaultPolicy.isBlank()) configError("目录缺少 defaultPolicy")
        if (catalog.countries.isEmpty()) configError("目录没有任何国家")
        val codes = mutableSetOf<String>()
        catalog.countries.forEach { country ->
            if (!ISO_ALPHA2.matches(country.code)) configError("国家代码不是大写 ISO 二字码：${country.code}")
            if (!codes.add(country.code)) configError("国家代码重复：${country.code}")
            if (country.labelZh.isBlank()) configError("国家 ${country.code} 缺少展示名")
            if (country.zones.isEmpty()) configError("国家 ${country.code} 没有任何时区")
            val zoneIds = mutableSetOf<String>()
            country.zones.forEach { zone ->
                if (!zoneIds.add(zone.id)) configError("国家 ${country.code} 时区重复：${zone.id}")
                if (zone.labelZh.isBlank()) configError("国家 ${country.code} 的时区 ${zone.id} 缺少展示名")
                try {
                    ZoneId.of(zone.id)
                } catch (ex: DateTimeException) {
                    configError("国家 ${country.code} 的时区 id 无法解析：${zone.id}", ex)
                }
            }
            if (country.defaultZoneId !in zoneIds) {
                configError("国家 ${country.code} 的默认时区 ${country.defaultZoneId} 不在该国的 zones 中")
            }
        }
    }

    private fun configError(message: String, cause: Throwable? = null): Nothing =
        throw IllegalStateException("$ERROR_PREFIX$message", cause)

    private companion object {
        const val RESOURCE_PATH = "contact-country-timezones.json"
        const val ERROR_PREFIX = "国家时区目录配置错误："
        val ISO_ALPHA2 = Regex("^[A-Z]{2}$")
    }
}
