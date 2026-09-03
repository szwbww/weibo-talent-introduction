package com.weibo.talentintroduction.rag.controller

import com.weibo.talentintroduction.rag.service.RagPromptConfigEffective
import com.weibo.talentintroduction.rag.service.RagPromptConfigResetRequest
import com.weibo.talentintroduction.rag.service.RagPromptConfigSaveRequest
import com.weibo.talentintroduction.rag.service.RagPromptConfigService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 计划 06 (T2): RAG 提示词约束配置端点 —— 「AI 提示词与约束」页新清单的数据面。
 *
 * - `GET  /api/rag/prompt-config`    当前生效视图（两段约束 + 头部 + isCustom）。
 * - `PUT  /api/rag/prompt-config`    保存（I-31 忽略派生入参 / I-32 纯文本数组 /
 *   I-33 审计），返回保存后的生效视图。
 * - `POST /api/rag/prompt-config/reset` 全部恢复默认 = 两列置 NULL（I-30）。
 *
 * 与旧自由回复配置端点（/api/ai-training/prompt-config，AiTrainingController）
 * 完全独立：旧表单配置的是 FREE_FORM 兜底路径，本端点只服务 RAG 新链路。
 */
@RestController
@RequestMapping("/api/rag/prompt-config")
class RagPromptConfigController(
    private val service: RagPromptConfigService
) {

    @GetMapping
    fun get(): RagPromptConfigEffective = service.effective()

    @PutMapping
    fun save(@RequestBody request: RagPromptConfigSaveRequest): RagPromptConfigEffective =
        service.save(request)

    @PostMapping("/reset")
    fun reset(@RequestBody(required = false) request: RagPromptConfigResetRequest?): RagPromptConfigEffective =
        service.resetToDefault(request?.operator)
}
