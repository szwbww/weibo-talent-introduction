package com.weibo.talentintroduction.common.controller

import com.weibo.talentintroduction.document.service.AnalysisFailedException
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
