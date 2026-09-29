package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleResult
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleSettingService
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleStatus
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * T-3（03）：`GET/PUT /api/expert-discovery/schedule` 的严格输入与状态码契约。
 *
 * 输入用**真实 Jackson** 解析后交给 controller，因此「小数/字符串/布尔/越界」这些
 * 会让 `1.5` 被截成 `1` 的陷阱是被真实类型判据挡住的，而不是靠测试自己预先归一化。
 */
class DiscoveryScheduleControllerTest {

    private val service = Mockito.mock(DiscoveryScheduleSettingService::class.java)
    private val controller = DiscoveryScheduleController(service)
    private val objectMapper = ObjectMapper()

    private fun body(json: String): JsonNode = objectMapper.readTree(json)

    private fun view(
        mode: String = DiscoveryScheduleSpec.MODE_LEGACY,
        editable: Boolean = true,
        source: String = DiscoveryScheduleSpec.SOURCE_OVERRIDE,
        intervalHours: Int? = 3,
        anchorAt: String? = "2026-09-29T02:00:00Z",
        nextTriggerAt: String? = "2026-09-29T05:00:00Z",
        applied: Boolean = true,
        saved: Boolean = true,
        reason: String? = null,
        message: String = "已设置每 3 小时执行一次"
    ) = DiscoveryScheduleView(
        mode, editable, source, intervalHours, anchorAt, nextTriggerAt, applied, saved, reason, message
    )

    private fun stub(result: DiscoveryScheduleResult) {
        Mockito.`when`(service.save(Mockito.anyInt())).thenReturn(result)
        Mockito.`when`(service.get()).thenReturn(result)
    }

    @Test
    fun `a valid integer body is saved and returns 200 with the fixed response shape`() {
        stub(DiscoveryScheduleResult(DiscoveryScheduleStatus.OK, view()))

        val response = controller.updateSchedule(body("""{"intervalHours":3}"""))

        assertEquals(200, response.statusCodeValue)
        val payload = response.body as DiscoveryScheduleView
        assertEquals(DiscoveryScheduleSpec.MODE_LEGACY, payload.mode)
        assertTrue(payload.editable)
        assertEquals(DiscoveryScheduleSpec.SOURCE_OVERRIDE, payload.source)
        assertEquals(3, payload.intervalHours)
        assertEquals("2026-09-29T02:00:00Z", payload.anchorAt)
        assertEquals("2026-09-29T05:00:00Z", payload.nextTriggerAt)
        assertTrue(payload.applied)
        assertTrue(payload.saved)
        assertEquals(null, payload.reason)
        Mockito.verify(service).save(3)
    }

    @Test
    fun `extra fields are ignored but the interval still has to be an integer`() {
        stub(DiscoveryScheduleResult(DiscoveryScheduleStatus.OK, view()))

        assertEquals(200, controller.updateSchedule(body("""{"intervalHours":4,"note":"x"}""")).statusCodeValue)
        Mockito.verify(service).save(4)
    }

    @Test
    fun `non integer and out of range bodies are rejected with 400 and never saved`() {
        val rejected = listOf(
            """{"intervalHours":null}""",
            """{"intervalHours":"3"}""",
            """{"intervalHours":1.5}""",
            """{"intervalHours":1.0}""",
            """{"intervalHours":true}""",
            """{"intervalHours":[3]}""",
            """{"intervalHours":{}}""",
            """{"intervalHours":0}""",
            """{"intervalHours":-1}""",
            """{"intervalHours":169}""",
            """{"intervalHours":100000000000}""",
            """{"interval":3}""",
            """{}""",
            """[3]""",
            """3"""
        )

        for (json in rejected) {
            val response = controller.updateSchedule(body(json))
            assertEquals(400, response.statusCodeValue, "必须拒绝：$json")
            val payload = response.body as Map<*, *>
            assertEquals("INVALID_INTERVAL_HOURS", payload["reason"])
            assertTrue((payload["message"] as String).contains("1～168"), payload["message"].toString())
        }

        assertEquals(400, controller.updateSchedule(null).statusCodeValue)
        Mockito.verify(service, Mockito.never()).save(Mockito.anyInt())
    }

    @Test
    fun `the boundaries are accepted`() {
        stub(DiscoveryScheduleResult(DiscoveryScheduleStatus.OK, view(intervalHours = 1)))

        assertEquals(200, controller.updateSchedule(body("""{"intervalHours":1}""")).statusCodeValue)
        assertEquals(200, controller.updateSchedule(body("""{"intervalHours":168}""")).statusCodeValue)
        Mockito.verify(service).save(1)
        Mockito.verify(service).save(168)
    }

    @Test
    fun `a mode that forbids the hour setting returns 409 with the reason code (I-4)`() {
        stub(
            DiscoveryScheduleResult(
                DiscoveryScheduleStatus.NOT_EDITABLE,
                view(
                    mode = DiscoveryScheduleSpec.MODE_CONTINUOUS,
                    editable = false,
                    source = DiscoveryScheduleSpec.SOURCE_CONFIG,
                    applied = false,
                    saved = false,
                    reason = DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE,
                    message = "当前为连续发现模式，小时周期设置不适用"
                )
            )
        )

        val response = controller.updateSchedule(body("""{"intervalHours":3}"""))

        assertEquals(409, response.statusCodeValue)
        val payload = response.body as DiscoveryScheduleView
        assertFalse(payload.editable)
        assertEquals(DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE, payload.reason)
        assertFalse(payload.applied)
    }

    @Test
    fun `a database failure returns 503 with saved=false (I-3)`() {
        stub(
            DiscoveryScheduleResult(
                DiscoveryScheduleStatus.UNAVAILABLE,
                view(
                    source = DiscoveryScheduleSpec.SOURCE_CONFIG,
                    intervalHours = null,
                    anchorAt = null,
                    nextTriggerAt = null,
                    applied = false,
                    saved = false,
                    reason = DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE,
                    message = "定时设置保存失败（数据库不可用），请稍后重试"
                )
            )
        )

        val response = controller.updateSchedule(body("""{"intervalHours":3}"""))

        assertEquals(503, response.statusCodeValue)
        val payload = response.body as DiscoveryScheduleView
        assertFalse(payload.saved)
        assertFalse(payload.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, payload.reason)
    }

    @Test
    fun `an apply failure returns 503 with saved=true applied=false (I-3)`() {
        stub(
            DiscoveryScheduleResult(
                DiscoveryScheduleStatus.UNAVAILABLE,
                view(
                    source = DiscoveryScheduleSpec.SOURCE_CONFIG,
                    applied = false,
                    saved = true,
                    reason = DiscoveryScheduleSpec.REASON_APPLY_FAILED,
                    message = "已保存但定时应用失败，请重试保存"
                )
            )
        )

        val response = controller.updateSchedule(body("""{"intervalHours":3}"""))

        assertEquals(503, response.statusCodeValue)
        val payload = response.body as DiscoveryScheduleView
        assertTrue(payload.saved)
        assertFalse(payload.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_APPLY_FAILED, payload.reason)
        assertTrue(payload.message.contains("重试保存"))
    }

    @Test
    fun `get returns 200 and a 503 when the setting store is unavailable (I-1)`() {
        stub(DiscoveryScheduleResult(DiscoveryScheduleStatus.OK, view(applied = false, source = DiscoveryScheduleSpec.SOURCE_CONFIG)))
        assertEquals(200, controller.getSchedule().statusCodeValue)

        stub(
            DiscoveryScheduleResult(
                DiscoveryScheduleStatus.UNAVAILABLE,
                view(
                    source = DiscoveryScheduleSpec.SOURCE_CONFIG,
                    intervalHours = null,
                    saved = false,
                    applied = false,
                    reason = DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE
                )
            )
        )
        val response = controller.getSchedule()
        assertEquals(503, response.statusCodeValue)
        assertEquals(
            DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE,
            (response.body as DiscoveryScheduleView).reason
        )
    }
}
