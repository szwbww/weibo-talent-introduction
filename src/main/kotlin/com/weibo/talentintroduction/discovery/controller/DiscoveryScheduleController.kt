package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleResult
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleSettingService
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * I-1～I-4（03）：`GET/PUT /api/expert-discovery/schedule` —— 整数小时设置的唯一 API。
 *
 * - 认证沿用既有 `/api` 前缀认证拦截器，不新增角色；两个方法都复用 service 的固定响应形状与原因码；
 * - **严格输入**：body 只接受 `{"intervalHours": <整数 1～168>}`。用本地 [JsonNode] 校验，
 *   缺失、`null`、字符串、小数（`1.5` 绝不能截成 `1`）、布尔、数组、越界一律 400 且**不写库**，
 *   不修改全局 `ObjectMapper`；
 * - 状态码：400 非法输入、409 当前模式不允许（连续模式/`enabled=false`/`cron=-`）、
 *   503 库不可用或已保存但定时应用失败（`saved` 标志区分两种变体）；
 * - GET 纯读，不通过访问来启动、恢复或重建任何任务。
 */
@RestController
@RequestMapping("/api/expert-discovery")
class DiscoveryScheduleController(
    private val scheduleService: DiscoveryScheduleSettingService
) {

    private val log = LoggerFactory.getLogger(DiscoveryScheduleController::class.java)

    @GetMapping("/schedule")
    fun getSchedule(): ResponseEntity<Any> = respond(scheduleService.get())

    @PutMapping("/schedule")
    fun updateSchedule(@RequestBody(required = false) body: JsonNode?): ResponseEntity<Any> {
        val node = body?.get(INTERVAL_FIELD)
        if (body == null || !body.isObject || node == null || node.isNull ||
            !node.isIntegralNumber || !node.canConvertToInt()
        ) {
            return invalidInterval()
        }
        val intervalHours = node.intValue()
        if (!DiscoveryScheduleSpec.isValidIntervalHours(intervalHours)) {
            return invalidInterval()
        }
        return respond(scheduleService.save(intervalHours))
    }

    private fun invalidInterval(): ResponseEntity<Any> {
        log.info("深度发现定时设置：拒绝非法 intervalHours 输入（未写库）")
        return ResponseEntity.badRequest().body(
            mapOf(
                "reason" to REASON_INVALID_INTERVAL,
                "message" to "执行间隔必须是 ${DiscoveryScheduleSpec.MIN_INTERVAL_HOURS}～" +
                    "${DiscoveryScheduleSpec.MAX_INTERVAL_HOURS} 的整数小时"
            )
        )
    }

    private fun respond(result: DiscoveryScheduleResult): ResponseEntity<Any> = when (result.status) {
        DiscoveryScheduleStatus.OK -> ResponseEntity.ok(result.view)
        DiscoveryScheduleStatus.NOT_EDITABLE ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(result.view)
        DiscoveryScheduleStatus.UNAVAILABLE ->
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result.view)
    }

    private companion object {
        const val INTERVAL_FIELD = "intervalHours"
        const val REASON_INVALID_INTERVAL = "INVALID_INTERVAL_HOURS"
    }
}
