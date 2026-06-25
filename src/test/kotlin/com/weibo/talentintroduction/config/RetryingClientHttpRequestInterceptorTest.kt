package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class RetryingClientHttpRequestInterceptorTest {

    @Test
    fun `retries twice on consecutive 429 then succeeds`() {
        val execution = Mockito.mock(ClientHttpRequestExecution::class.java)
        val request = Mockito.mock(HttpRequest::class.java)
        var callCount = 0
        Mockito.doAnswer {
            callCount++
            mockResponse(if (callCount <= 2) 429 else 200)
        }.`when`(execution).execute(Mockito.eq(request), Mockito.any())

        val backoffMs = mutableListOf<Long>()
        val interceptor = RetryingClientHttpRequestInterceptor(
            maxRetries = 2,
            initialBackoffMs = 500,
            sleeper = { backoffMs.add(it) }
        )

        val response = interceptor.intercept(request, ByteArray(0), execution)

        assertEquals(200, response.statusCode.value())
        assertEquals(3, callCount)
        assertEquals(listOf(500L, 1000L), backoffMs)
    }

    @Test
    fun `does not retry on 404`() {
        val execution = Mockito.mock(ClientHttpRequestExecution::class.java)
        val request = Mockito.mock(HttpRequest::class.java)
        var callCount = 0
        Mockito.doAnswer {
            callCount++
            mockResponse(404)
        }.`when`(execution).execute(Mockito.eq(request), Mockito.any())

        val backoffMs = mutableListOf<Long>()
        val interceptor = RetryingClientHttpRequestInterceptor(
            maxRetries = 2,
            initialBackoffMs = 500,
            sleeper = { backoffMs.add(it) }
        )

        val response = interceptor.intercept(request, ByteArray(0), execution)

        assertEquals(404, response.statusCode.value())
        assertEquals(1, callCount)
        assertTrue(backoffMs.isEmpty())
    }

    @Test
    fun `retries on IOException then succeeds`() {
        val execution = Mockito.mock(ClientHttpRequestExecution::class.java)
        val request = Mockito.mock(HttpRequest::class.java)
        var callCount = 0
        Mockito.doAnswer {
            callCount++
            if (callCount == 1) throw IOException("connection reset")
            mockResponse(200)
        }.`when`(execution).execute(Mockito.eq(request), Mockito.any())

        val backoffMs = mutableListOf<Long>()
        val interceptor = RetryingClientHttpRequestInterceptor(
            maxRetries = 2,
            initialBackoffMs = 500,
            sleeper = { backoffMs.add(it) }
        )

        val response = interceptor.intercept(request, ByteArray(0), execution)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, callCount)
        assertEquals(listOf(500L), backoffMs)
    }

    private fun mockResponse(statusCode: Int): ClientHttpResponse {
        val response = Mockito.mock(ClientHttpResponse::class.java)
        Mockito.doReturn(HttpStatus.valueOf(statusCode)).`when`(response).statusCode
        return response
    }
}
