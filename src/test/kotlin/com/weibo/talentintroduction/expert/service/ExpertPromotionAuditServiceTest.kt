package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class ExpertPromotionAuditServiceTest {
    @Test
    fun `audit writes always run in an independent transaction`() {
        val writeMethods = ExpertPromotionAuditService::class.java.declaredMethods
            .filter { it.name in setOf("create", "markSuccess", "markFailed") }

        assertEquals(3, writeMethods.size)
        writeMethods.forEach { method ->
            val transaction = method.getAnnotation(Transactional::class.java)
            assertNotNull(transaction, "${method.name} must be transactional")
            assertEquals(Propagation.REQUIRES_NEW, transaction.propagation)
        }
    }
}
