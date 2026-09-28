package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration

/**
 * I-1：OpenAlex 元数据搜索的有限延迟恢复间隔是**进程配置**（不是数据库字段）：
 * 默认 30s / 120s / 300s；YAML 逗号列表可绑定；空列表关闭延迟恢复（回退既有「3 次短尝试后终止」）；
 * 零值/负值/递减/超过 5 分钟/超过 3 项都在启动时直接失败。
 */
class ExpertDiscoveryPropertiesTest {

    /** 只喂本前缀的两个键：绑定路径与生产 `application.yml` 逐字相同（同一个 @ConfigurationProperties）。 */
    private fun bind(value: String?): ExpertDiscoveryProperties {
        val properties = mutableMapOf<String, Any>(
            "talent-introduction.expert-discovery.enabled" to "true"
        )
        if (value != null) {
            properties["talent-introduction.expert-discovery.open-alex-search-recovery-delays"] = value
        }
        val source = MapConfigurationPropertySource(properties)
        return Binder(source)
            .bind("talent-introduction.expert-discovery", ExpertDiscoveryProperties::class.java)
            .get()
    }

    private fun failureMessage(error: Throwable): String =
        generateSequence(error as Throwable?) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `default recovery delays are 30s 120s and 300s`() {
        assertEquals(
            listOf(Duration.ofSeconds(30), Duration.ofSeconds(120), Duration.ofSeconds(300)),
            ExpertDiscoveryProperties().openAlexSearchRecoveryDelays
        )
    }

    @Test
    fun `a comma separated list binds in order`() {
        assertEquals(
            listOf(Duration.ofSeconds(45), Duration.ofMinutes(2)),
            bind("45s,2m").openAlexSearchRecoveryDelays
        )
    }

    @Test
    fun `an empty list switches deferred recovery off`() {
        // 空列表是合法配置：表示不做延迟恢复，只保留既有 3 次短尝试。
        assertTrue(
            ExpertDiscoveryProperties(openAlexSearchRecoveryDelays = emptyList())
                .openAlexSearchRecoveryDelays.isEmpty()
        )
        assertTrue(bind("").openAlexSearchRecoveryDelays.isEmpty(), "空配置值必须绑定成空列表而不是一项非法值")
    }

    @Test
    fun `illegal delay lists fail at construction`() {
        val cases = mapOf(
            "零值" to listOf(Duration.ZERO),
            "负值" to listOf(Duration.ofSeconds(-1)),
            "递减" to listOf(Duration.ofSeconds(120), Duration.ofSeconds(30)),
            "超过 5 分钟" to listOf(Duration.ofMinutes(6)),
            "超过 3 组" to listOf(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4)
            )
        )
        cases.forEach { (label, delays) ->
            val error = assertThrows(IllegalArgumentException::class.java, {
                ExpertDiscoveryProperties(openAlexSearchRecoveryDelays = delays)
            }, label)
            assertTrue(
                failureMessage(error).contains("open-alex-search-recovery-delays"),
                "$label 必须报出可调旋钮名：${failureMessage(error)}"
            )
        }
    }

    @Test
    fun `an illegal bound value fails startup with an actionable message`() {
        listOf("0s", "10m", "300s,60s").forEach { value ->
            val error = assertThrows(Exception::class.java) { bind(value) }
            assertTrue(
                failureMessage(error).contains("open-alex-search-recovery-delays"),
                "非法绑定值 '$value' 必须启动即失败并指出旋钮：${failureMessage(error)}"
            )
        }
    }
}
