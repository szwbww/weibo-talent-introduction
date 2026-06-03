package com.weibo.talentintroduction.monitoring.service

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class MonitoringDateRangeResolver {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun resolveDay(date: LocalDate? = null): Pair<LocalDateTime, LocalDateTime> {
        val resolved = date ?: LocalDate.now(zone)
        return resolved.atStartOfDay() to resolved.plusDays(1).atStartOfDay()
    }

    fun resolveRange(from: LocalDate?, to: LocalDate?): Pair<LocalDateTime, LocalDateTime> {
        val effectiveFrom = (from ?: LocalDate.now(zone)).atStartOfDay()
        val effectiveTo = (to ?: from ?: LocalDate.now(zone)).plusDays(1).atStartOfDay()
        return effectiveFrom to effectiveTo
    }

    fun todayString(): String = LocalDate.now(zone).toString()
}
