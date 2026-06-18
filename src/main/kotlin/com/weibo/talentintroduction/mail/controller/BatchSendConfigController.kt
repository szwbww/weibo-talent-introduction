package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.service.BatchSendConfig
import com.weibo.talentintroduction.campaign.service.BatchSendConfigUpdateRequest
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail/batch-send")
class BatchSendConfigController(
    private val batchSendSettingService: BatchSendSettingService
) {
    @GetMapping("/config")
    fun getConfig(): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendSettingService.getConfig())

    @PutMapping("/config")
    fun updateConfig(@RequestBody request: BatchSendConfigUpdateRequest): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendSettingService.updateConfig(request))
}
