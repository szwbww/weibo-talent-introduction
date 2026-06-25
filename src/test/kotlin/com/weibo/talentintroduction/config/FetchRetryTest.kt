package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException

class FetchRetryTest {

    @Test
    fun `retryOnRecoverableIo retries then succeeds`() {
        var callCount = 0
        val result = FetchRetry.retryOnRecoverableIo(
            maxRetries = 2,
            initialBackoffMs = 0
        ) {
            callCount++
            if (callCount == 1) throw SocketTimeoutException("read timed out")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryOnRecoverableIo does not retry non recoverable errors`() {
        var callCount = 0
        try {
            FetchRetry.retryOnRecoverableIo(maxRetries = 2, initialBackoffMs = 0) {
                callCount++
                throw RuntimeException("404 Not Found")
            }
        } catch (_: RuntimeException) {
        }

        assertEquals(1, callCount)
    }

    @Test
    fun `isRecoverableIoException detects wrapped read timeout`() {
        val wrapped = ResourceAccessException("I/O error", SocketTimeoutException("read timed out"))
        assertTrue(FetchRetry.isRecoverableIoException(wrapped))
    }

    @Test
    fun `isRecoverableIoException rejects 404`() {
        assertFalse(FetchRetry.isRecoverableIoException(HttpClientErrorException(HttpStatus.NOT_FOUND)))
    }
}
