package com.weibo.talentintroduction.common.controller

import com.weibo.talentintroduction.document.service.AnalysisFailedException
import com.weibo.talentintroduction.mail.service.ManualSendSafetyBlockedException
import com.weibo.talentintroduction.mail.service.SafetySeverity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Invalid request")

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Invalid state")

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.message ?: "Resource not found")

    @ExceptionHandler(AnalysisFailedException::class)
    fun handleAnalysisFailed(ex: AnalysisFailedException): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_FAILED", ex.message ?: "分析失败，请重试")

    @ExceptionHandler(ManualSendSafetyBlockedException::class)
    fun handleManualSendSafetyBlocked(
        ex: ManualSendSafetyBlockedException
    ): ResponseEntity<ManualSendSafetyBlockedResponse> {
        val findings = ex.findings
        val truncated = findings.size > 20
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ManualSendSafetyBlockedResponse(
                message = "发送内容安全校验未通过，共 ${findings.size} 项，请在界面上逐条确认",
                detail = HttpStatus.UNPROCESSABLE_ENTITY.reasonPhrase,
                requiresStrongConfirmation = findings.any { it.severity == SafetySeverity.STRONG },
                truncated = truncated,
                findings = findings.take(20).map { finding ->
                    SafetyFindingResponse(
                        code = finding.code,
                        severity = finding.severity.name,
                        sentence = finding.sentence?.takeIf { it.isNotEmpty() }?.let { sentence ->
                            if (sentence.length > 200) sentence.take(199) + "…" else sentence
                        }
                    )
                }
            )
        )
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class
    )
    fun handleRequestBinding(ex: Exception): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Invalid request")

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.message ?: "Internal server error")

    private fun error(
        status: HttpStatus,
        code: String,
        message: String
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(
            ApiErrorResponse(
                code = code,
                message = message,
                detail = status.reasonPhrase
            )
        )
}

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val detail: String?
)

data class ManualSendSafetyBlockedResponse(
    val code: String = "MANUAL_SEND_SAFETY_BLOCKED",
    val message: String,
    val detail: String?,
    val requiresStrongConfirmation: Boolean,
    val truncated: Boolean,
    val findings: List<SafetyFindingResponse>
)

data class SafetyFindingResponse(
    val code: String,
    val severity: String,
    val sentence: String?
)
