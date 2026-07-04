package com.weibo.talentintroduction.document.controller

import com.weibo.talentintroduction.document.service.AnalysisFieldView
import com.weibo.talentintroduction.document.service.AnalysisResultView
import com.weibo.talentintroduction.document.service.ExpertDocumentAnalysisService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/expert-contacts/{contactId}/ai-analysis")
class ExpertDocumentAnalysisController(
    private val service: ExpertDocumentAnalysisService
) {
    @PostMapping
    fun analyze(
        @PathVariable contactId: Long,
        @RequestBody request: AnalyzeRequest
    ): AnalysisResultView = service.analyze(contactId, request.attachmentIds)

    @GetMapping
    fun getResults(@PathVariable contactId: Long): AnalysisResultView =
        service.getResults(contactId)

    @PutMapping("/{fieldId}")
    fun updateField(
        @PathVariable contactId: Long,
        @PathVariable fieldId: Long,
        @RequestBody request: UpdateFieldRequest
    ): AnalysisFieldView = service.updateField(contactId, fieldId, request.value)

    @PostMapping("/fields")
    fun addField(
        @PathVariable contactId: Long,
        @RequestBody request: AddFieldRequest
    ): AnalysisFieldView = service.addField(
        contactId,
        request.fieldKey,
        request.fieldLabel,
        request.value
    )

    @DeleteMapping
    fun clearResults(@PathVariable contactId: Long) {
        service.clearResults(contactId)
    }
}

data class AnalyzeRequest(
    val attachmentIds: List<Long>
)

data class UpdateFieldRequest(
    val value: String
)

data class AddFieldRequest(
    val fieldKey: String,
    val fieldLabel: String,
    val value: String
)
