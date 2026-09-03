package com.weibo.talentintroduction.rag.controller

import com.weibo.talentintroduction.rag.service.RagFactAdminListResult
import com.weibo.talentintroduction.rag.service.RagFactAdminService
import com.weibo.talentintroduction.rag.service.RagFactSaveResult
import com.weibo.talentintroduction.rag.service.RagFactUpdateRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 计划 04 (T1): RAG 知识库管理端点 —— 只提供「修改」与「启停」（I-23）。
 *
 * - `GET  /api/rag/facts`            全部事实 + 库指纹（页头 45 条/指纹用）。
 * - `PUT  /api/rag/facts/{factCode}` 部分更新（I-20/I-21/I-22），返回新指纹。
 * - `POST /api/rag/facts/{factCode}/enable` / `disable` 启停，返回新指纹。
 *
 * **不提供** create / delete 端点（I-23）：新增事实会让语料指纹与脚本永久
 * 分叉，放开新增前必须先给出新的指纹管理办法。
 */
@RestController
@RequestMapping("/api/rag/facts")
class RagFactAdminController(
    private val service: RagFactAdminService
) {

    @GetMapping
    fun list(): RagFactAdminListResult = service.list()

    @PutMapping("/{factCode}")
    fun update(
        @PathVariable factCode: String,
        @RequestBody request: RagFactUpdateRequest
    ): RagFactSaveResult =
        RagFactSaveResult(fingerprint = service.update(factCode, request))

    @PostMapping("/{factCode}/enable")
    fun enable(
        @PathVariable factCode: String,
        @RequestBody(required = false) request: RagFactToggleRequest?
    ): RagFactSaveResult =
        RagFactSaveResult(fingerprint = service.toggleEnabled(factCode, true, request?.operator))

    @PostMapping("/{factCode}/disable")
    fun disable(
        @PathVariable factCode: String,
        @RequestBody(required = false) request: RagFactToggleRequest?
    ): RagFactSaveResult =
        RagFactSaveResult(fingerprint = service.toggleEnabled(factCode, false, request?.operator))
}

data class RagFactToggleRequest(val operator: String? = null)
