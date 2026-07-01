package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.InboundMailTag
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.InboundMailTagRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.mail.repository.CustomTagCount
import com.weibo.talentintroduction.mail.repository.QaTagCount
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class InboundMailTagServiceTest {
    private val tagRepository = Mockito.mock(InboundMailTagRepository::class.java)
    private val processingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val service = InboundMailTagService(
        tagRepository,
        processingRepository,
        qaRuleRepository,
        qaMatchService
    )

    private val fundingRule = QaRule(
        id = 1L,
        categoryId = 1L,
        keywords = "salary,funding",
        displayName = "Funding support",
        replySubject = "Funding",
        replyBody = "Funding answer"
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(processingRepository.existsById(100L)).thenReturn(true)
    }

    @Test
    fun `addQaTag stores qa_rule_id and label snapshot`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(fundingRule))
        Mockito.`when`(tagRepository.existsByInboundProcessingIdAndQaRuleId(100L, 1L)).thenReturn(false)
        Mockito.`when`(tagRepository.save(Mockito.any(InboundMailTag::class.java))).thenAnswer { invocation ->
            val tag = invocation.getArgument<InboundMailTag>(0)
            tag.copy(id = 10L)
        }

        service.addQaTag(100L, 1L, "operator")

        val captor = ArgumentCaptor.forClass(InboundMailTag::class.java)
        Mockito.verify(tagRepository).save(captor.capture())
        assertEquals(1L, captor.value.qaRuleId)
        assertEquals("Funding support", captor.value.label)
        assertEquals("QA", captor.value.tagType)
    }

    @Test
    fun `listTags marks disabled qa rule inactive but keeps row label`() {
        val disabledRule = fundingRule.copy(enabled = false)
        Mockito.`when`(tagRepository.findAllByInboundProcessingIdOrderByIdAsc(100L)).thenReturn(
            listOf(
                InboundMailTag(
                    id = 10L,
                    inboundProcessingId = 100L,
                    tagType = "QA",
                    qaRuleId = 1L,
                    label = "Funding support",
                    source = "AUTO",
                    createdAt = LocalDateTime.now()
                )
            )
        )
        Mockito.`when`(qaRuleRepository.findAllById(listOf(1L))).thenReturn(listOf(disabledRule))

        val tags = service.listTags(100L)

        assertEquals(1, tags.size)
        assertFalse(tags[0].active)
        assertEquals("Funding support", tags[0].label)
    }

    @Test
    fun `listTags keeps custom tags active`() {
        Mockito.`when`(tagRepository.findAllByInboundProcessingIdOrderByIdAsc(100L)).thenReturn(
            listOf(
                InboundMailTag(
                    id = 11L,
                    inboundProcessingId = 100L,
                    tagType = "CUSTOM",
                    qaRuleId = null,
                    label = "VIP",
                    source = "MANUAL",
                    createdAt = LocalDateTime.now()
                )
            )
        )

        val tags = service.listTags(100L)

        assertTrue(tags[0].active)
        assertEquals("VIP", tags[0].label)
    }

    @Test
    fun `autoApplyQaTags is idempotent`() {
        Mockito.`when`(qaMatchService.matchAllRuleIds("salary question")).thenReturn(listOf(1L))
        Mockito.`when`(tagRepository.existsByInboundProcessingIdAndQaRuleId(100L, 1L))
            .thenReturn(false)
            .thenReturn(true)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(fundingRule))
        Mockito.`when`(tagRepository.save(Mockito.any(InboundMailTag::class.java))).thenAnswer { invocation ->
            val tag = invocation.getArgument<InboundMailTag>(0)
            tag.copy(id = 10L)
        }

        val first = service.autoApplyQaTags(100L, "salary question")
        val second = service.autoApplyQaTags(100L, "salary question")

        assertEquals(1, first)
        assertEquals(0, second)
        Mockito.verify(tagRepository, Mockito.times(1)).save(Mockito.any(InboundMailTag::class.java))
    }

    @Test
    fun `autoApplyQaTags re-adds deleted auto tag`() {
        Mockito.`when`(qaMatchService.matchAllRuleIds("salary question")).thenReturn(listOf(1L))
        Mockito.`when`(tagRepository.existsByInboundProcessingIdAndQaRuleId(100L, 1L)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(fundingRule))
        Mockito.`when`(tagRepository.save(Mockito.any(InboundMailTag::class.java))).thenAnswer { invocation ->
            val tag = invocation.getArgument<InboundMailTag>(0)
            tag.copy(id = 10L)
        }

        val first = service.autoApplyQaTags(100L, "salary question")
        val second = service.autoApplyQaTags(100L, "salary question")

        assertEquals(1, first)
        assertEquals(1, second)
        Mockito.verify(tagRepository, Mockito.times(2)).save(Mockito.any(InboundMailTag::class.java))
    }

    @Test
    fun `stats aggregates full table counts`() {
        Mockito.`when`(tagRepository.countQaTagsGroupedByRule()).thenReturn(
            listOf(QaTagCount(1L, "Funding support", 3L))
        )
        Mockito.`when`(tagRepository.countCustomTagsGroupedByLabel()).thenReturn(
            listOf(CustomTagCount("VIP", 2L))
        )
        Mockito.`when`(qaRuleRepository.findAllById(listOf(1L))).thenReturn(listOf(fundingRule.copy(enabled = false)))

        val stats = service.stats()

        assertEquals(5L, stats.total)
        assertEquals(2, stats.items.size)
        assertEquals("qa:1", stats.items[0].tagKey)
        assertFalse(stats.items[0].active)
        assertEquals(3L, stats.items[0].count)
        assertEquals("Funding support", stats.items[0].label)
    }

    @Test
    fun `stats preserves snapshot label when qa rule deleted`() {
        Mockito.`when`(tagRepository.countQaTagsGroupedByRule()).thenReturn(
            listOf(QaTagCount(99L, "Legacy funding tag", 4L))
        )
        Mockito.`when`(tagRepository.countCustomTagsGroupedByLabel()).thenReturn(emptyList())
        Mockito.`when`(qaRuleRepository.findAllById(listOf(99L))).thenReturn(emptyList())

        val stats = service.stats()

        assertEquals(1, stats.items.size)
        assertEquals("Legacy funding tag", stats.items[0].label)
        assertFalse(stats.items[0].active)
        assertEquals(4L, stats.items[0].count)
        assertEquals("qa:99", stats.items[0].tagKey)
    }

    @Test
    fun `autoApplyQaTags returns zero for blank body`() {
        assertEquals(0, service.autoApplyQaTags(100L, "   "))
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `addCustomTag rejects blank label`() {
        assertThrows<IllegalArgumentException> {
            service.addCustomTag(100L, "   ", "operator")
        }
    }
}
