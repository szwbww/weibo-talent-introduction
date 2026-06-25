package com.weibo.talentintroduction.config

import org.springframework.web.client.HttpStatusCodeException
import java.io.IOException
import java.net.SocketTimeoutException

object FetchRetry {

    fun isRecoverableIoException(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            when (current) {
                is SocketTimeoutException -> return true
                is IOException -> return true
                is HttpStatusCodeException -> {
                    val code = current.statusCode.value()
                    return code == 429 || code == 503
                }
            }
            current = current.cause
        }
        return false
    }

    fun <T> retryOnRecoverableIo(
        maxRetries: Int,
        initialBackoffMs: Long,
        sleeper: (Long) -> Unit = { Thread.sleep(it) },
        block: () -> T
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isRecoverableIoException(e) || attempt >= maxRetries) {
                    throw e
                }
                sleeper(initialBackoffMs * (1L shl attempt))
                attempt++
            }
        }
    }
}
