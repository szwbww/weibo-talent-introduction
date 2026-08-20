package com.weibo.talentintroduction.llm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiReplyGenerationCoordinatorTest {
    @Test
    fun `requires canonical uuid and isolates cancellation by scope`() {
        val executor = Executors.newCachedThreadPool()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val release = CountDownLatch(1)
        val id = "00000000-0000-0000-0000-000000000001"
        val policy = AiReplyTimeoutPolicy(10, 30)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.start("TRAINING_MAIL:1", "not-a-uuid", policy) { _, _, _ -> "ok" }
            }

            coordinator.start("TRAINING_MAIL:1", id, policy) { token, _, _ ->
                release.await(5, TimeUnit.SECONDS)
                token.throwIfCancelled()
                "ok"
            }
            assertEquals("NOT_ACTIVE", coordinator.cancel("LIVE_INBOUND:1", id))
            assertEquals("CANCEL_REQUESTED", coordinator.cancel("TRAINING_MAIL:1", id))
            assertEquals("NOT_ACTIVE", coordinator.cancel("TRAINING_MAIL:1", id))
        } finally {
            release.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `duplicate generation id is rejected while active`() {
        val executor = Executors.newCachedThreadPool()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val release = CountDownLatch(1)
        val id = "00000000-0000-0000-0000-000000000002"
        try {
            coordinator.start("TRAINING_MAIL:2", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ ->
                release.await(5, TimeUnit.SECONDS)
                "ok"
            }
            assertThrows(TrustReplyWorkbenchException::class.java) {
                coordinator.start("TRAINING_MAIL:2", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ -> "other" }
            }
        } finally {
            release.countDown()
            coordinator.cancel("TRAINING_MAIL:2", id)
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `start returns sse emitter`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val id = "00000000-0000-0000-0000-000000000003"
        try {
            val emitter = coordinator.start("TRAINING_MAIL:3", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ -> "ok" }
            assertEquals(true, SseEmitter::class.java.isAssignableFrom(emitter.javaClass))
        } finally {
            coordinator.cancel("TRAINING_MAIL:3", id)
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `enforces active limit and refuses cancellation after commit begins`() {
        val executor = Executors.newCachedThreadPool()
        val scheduler = Executors.newScheduledThreadPool(2)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val release = CountDownLatch(1)
        val committed = CountDownLatch(1)
        val ids = (1..40).map { index -> "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}" }
        try {
            ids.forEach { id ->
                coordinator.start("TRAINING_MAIL:$id", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ ->
                    release.await(5, TimeUnit.SECONDS)
                    "ok"
                }
            }
            val overflowId = "00000000-0000-0000-0000-000000000041"
            val overflow = assertThrows(TrustReplyWorkbenchException::class.java) {
                coordinator.start("TRAINING_MAIL:overflow", overflowId, AiReplyTimeoutPolicy(10, 30)) { _, _, _ -> "overflow" }
            }
            assertEquals("TRUST_REPLY_GENERATION_QUEUE_FULL", overflow.code)

            val commitId = "00000000-0000-0000-0000-000000000042"
            coordinator.cancel("TRAINING_MAIL:1", ids.first())
            release.countDown()
            val cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (coordinator.activeGenerationCount() != 0 && System.nanoTime() < cleanupDeadline) {
                Thread.sleep(10)
            }
            coordinator.start("TRAINING_MAIL:commit", commitId, AiReplyTimeoutPolicy(10, 30)) { _, _, beforeCommit ->
                assertTrue(beforeCommit())
                committed.countDown()
                Thread.sleep(100)
                "committed"
            }
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            assertEquals("TOO_LATE", coordinator.cancel("TRAINING_MAIL:commit", commitId))
        } finally {
            release.countDown()
            ids.forEach { coordinator.cancel("TRAINING_MAIL:$it", it) }
            coordinator.cancel("TRAINING_MAIL:commit", "00000000-0000-0000-0000-000000000042")
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    // P0 (I-1): a known business exception surfaces its real code in the error
    // event payload while the fixed generic message stays.
    @Test
    fun `business exception surfaces its code in the error event`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val id = "00000000-0000-0000-0000-000000000010"
        val release = CountDownLatch(1)
        val capture = SseCapture()
        try {
            val emitter = coordinator.start("TRAINING_MAIL:10", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ ->
                release.await(5, TimeUnit.SECONDS)
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_EVIDENCE_STALE")
            }
            capture.attach(emitter)
            release.countDown()
            assertTrue(capture.done.await(5, TimeUnit.SECONDS))
            val error = capture.events.firstOrNull { it.first == "error" }
            assertTrue(error != null, "expected an error event, got ${capture.events.map { it.first }}")
            @Suppress("UNCHECKED_CAST")
            val payload = error!!.second as Map<String, Any>
            assertEquals("TRUST_REPLY_EVIDENCE_STALE", payload["code"])
            assertEquals("AI generation failed", payload["message"])
            assertEquals(id, payload["generationId"])
            assertEquals(setOf("generationId", "code", "message"), payload.keys)
        } finally {
            release.countDown()
            coordinator.cancel("TRAINING_MAIL:10", id)
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    // P0 (I-1): unknown exceptions surface the fixed code and the generic
    // message only; no exception text, class name or stack may leak.
    @Test
    fun `unknown exception surfaces the fixed code and no exception text`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val id = "00000000-0000-0000-0000-000000000011"
        val release = CountDownLatch(1)
        val capture = SseCapture()
        try {
            val emitter = coordinator.start("TRAINING_MAIL:11", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ ->
                release.await(5, TimeUnit.SECONDS)
                throw IllegalStateException("db password is xyz")
            }
            capture.attach(emitter)
            release.countDown()
            assertTrue(capture.done.await(5, TimeUnit.SECONDS))
            val error = capture.events.firstOrNull { it.first == "error" }
            assertTrue(error != null, "expected an error event, got ${capture.events.map { it.first }}")
            @Suppress("UNCHECKED_CAST")
            val payload = error!!.second as Map<String, Any>
            assertEquals("AI_REPLY_GENERATION_FAILED", payload["code"])
            assertEquals("AI generation failed", payload["message"])
            assertEquals(setOf("generationId", "code", "message"), payload.keys)
            val serialized = payload.toString()
            assertFalse(serialized.contains("xyz"))
            assertFalse(serialized.contains("IllegalStateException"))
            assertFalse(serialized.contains("db password"))
        } finally {
            release.countDown()
            coordinator.cancel("TRAINING_MAIL:11", id)
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    // P0 (must-NOT-change 2): cancellation keeps emitting `cancelled` and never
    // surfaces an error event.
    @Test
    fun `cancellation still emits cancelled and never error`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newScheduledThreadPool(1)
        val coordinator = AiReplyGenerationCoordinator(executor, scheduler)
        val id = "00000000-0000-0000-0000-000000000012"
        val release = CountDownLatch(1)
        val capture = SseCapture()
        try {
            val emitter = coordinator.start("TRAINING_MAIL:12", id, AiReplyTimeoutPolicy(10, 30)) { _, _, _ ->
                release.await(5, TimeUnit.SECONDS)
                throw AiReplyGenerationCancelledException()
            }
            capture.attach(emitter)
            release.countDown()
            assertTrue(capture.done.await(5, TimeUnit.SECONDS))
            assertTrue(capture.events.any { it.first == "cancelled" }, "expected a cancelled event, got ${capture.events.map { it.first }}")
            assertFalse(capture.events.any { it.first == "error" })
            val cancelled = capture.events.first { it.first == "cancelled" }
            @Suppress("UNCHECKED_CAST")
            assertEquals(id, (cancelled.second as Map<String, Any>)["generationId"])
        } finally {
            release.countDown()
            coordinator.cancel("TRAINING_MAIL:12", id)
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    // P0: captures SSE events emitted by a coordinator-owned SseEmitter.
    // ResponseBodyEmitter.Handler and initialize() are package-private, so the
    // handler is attached reflectively after start(); sends made before the
    // handler exists are queued by Spring and flushed on initialize.
    private class SseCapture {
        val events = CopyOnWriteArrayList<Pair<String, Any?>>()
        val done = CountDownLatch(1)
        @Volatile
        private var currentEvent: String? = null

        fun attach(emitter: SseEmitter) {
            val handlerClass = Class.forName("org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter\$Handler")
            val handler = java.lang.reflect.Proxy.newProxyInstance(
                handlerClass.classLoader,
                arrayOf(handlerClass)
            ) { _, method, args ->
                when (method.name) {
                    "send" -> {
                        val arg = args?.getOrNull(0)
                        if (arg is Set<*>) {
                            arg.forEach { entry ->
                                if (entry is ResponseBodyEmitter.DataWithMediaType) {
                                    deliver(entry.data, entry.mediaType)
                                }
                            }
                        } else {
                            deliver(arg, null)
                        }
                        null
                    }
                    "complete", "completeWithError" -> {
                        done.countDown()
                        null
                    }
                    else -> null
                }
            }
            val initialize = ResponseBodyEmitter::class.java.getDeclaredMethod("initialize", handlerClass)
            initialize.isAccessible = true
            runCatching { initialize.invoke(emitter, handler) }.getOrThrow()
        }

        private fun deliver(data: Any?, mediaType: MediaType?) {
            if (data is String && data.startsWith("event:")) {
                // SseEventBuilderImpl flushes the "event:<name>\ndata:" prefix
                // as one String entry; keep only the event name itself.
                currentEvent = data.removePrefix("event:").substringBefore('\n').trim()
            } else {
                events.add((currentEvent ?: "message") to data)
            }
        }
    }
}
