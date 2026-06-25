package com.weibo.talentintroduction.config

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class RetryingClientHttpRequestInterceptor(
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 500,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        var attempt = 0
        while (true) {
            try {
                val response = execution.execute(request, body)
                val statusCode = response.statusCode.value()
                if (isRetryableStatus(statusCode) && attempt < maxRetries) {
                    response.close()
                    backoff(attempt)
                    attempt++
                    continue
                }
                return response
            } catch (e: IOException) {
                if (isRetryableIOException(e) && attempt < maxRetries) {
                    backoff(attempt)
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    private fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == 429 || statusCode == 503

    private fun isRetryableIOException(e: IOException): Boolean = true

    private fun backoff(attempt: Int) {
        sleeper(initialBackoffMs * (1L shl attempt))
    }
}
