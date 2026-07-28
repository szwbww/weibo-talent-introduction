package com.weibo.talentintroduction.llm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
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
}
