package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.document.domain.ExpertAnalysisResult
import com.weibo.talentintroduction.document.repository.ExpertAnalysisResultRepository
import com.weibo.talentintroduction.llm.service.LlmChatMessage
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException
import java.util.Optional

class ExpertDocumentAnalysisServiceTest {
    private val documentTextExtractor = Mockito.mock(DocumentTextExtractor::class.java)
    private val analysisResultRepository = Mockito.mock(ExpertAnalysisResultRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val llmDraftClientProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
    private val objectMapper = ObjectMapper()

    private lateinit var service: ExpertDocumentAnalysisService

    @BeforeEach
    fun setUp() {
        service = ExpertDocumentAnalysisService(
            documentTextExtractor,
            analysisResultRepository,
            mailAttachmentRepository,
            llmDraftClientProvider,
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            objectMapper
        )
    }

    @AfterEach
    fun tearDown() {
        Mockito.reset(
            documentTextExtractor,
            analysisResultRepository,
            mailAttachmentRepository,
            llmDraftClientProvider
        )
    }

    @Test
    fun `buildAnalysisPrompt wraps files with ids and names`() {
        val prompt = service.buildAnalysisPrompt(
            listOf(
                ExtractedText(1L, "cv.pdf", "Alice Chen", supported = true),
                ExtractedText(2L, "degree.txt", "PhD 2018", supported = true)
            )
        )

        assertTrue(prompt.contains("<FILE name=\"cv.pdf\" id=\"att_1\">"))
        assertTrue(prompt.contains("Alice Chen"))
        assertTrue(prompt.contains("<FILE name=\"degree.txt\" id=\"att_2\">"))
    }

    @Test
    fun `verifyExcerpt accepts exact and whitespace-normalized substrings`() {
        assertTrue(service.verifyExcerpt("Name: Alice Chen\nEmail: a@x.com", "Alice Chen"))
        assertTrue(service.verifyExcerpt("Name:  Alice   Chen", "Alice Chen"))
        assertFalse(service.verifyExcerpt("Name: Bob", "Alice Chen"))
    }

    @Test
    fun `analyze rejects attachment not belonging to contact`() {
        Mockito.doThrow(IllegalArgumentException("Document 9 does not belong to expert contact 1"))
            .`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 9L)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.analyze(1L, listOf(9L))
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `analyze maps llm timeout to AnalysisFailedException`() {
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 10L)
        Mockito.`when`(documentTextExtractor.extract(1L, listOf(10L)))
            .thenReturn(mapOf(10L to ExtractedText(10L, "cv.pdf", "Alice Chen", supported = true)))

        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw ResourceAccessException("timeout")
            }
        }
        Mockito.`when`(llmDraftClientProvider.getIfAvailable()).thenReturn(failingClient)

        val ex = assertThrows(AnalysisFailedException::class.java) {
            service.analyze(1L, listOf(10L))
        }
        assertTrue(ex.message!!.contains("分析超时"))
    }

    @Test
    fun `analyze persists verified flags and keeps source metadata on update`() {
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 10L)
        Mockito.`when`(documentTextExtractor.extract(1L, listOf(10L)))
            .thenReturn(mapOf(10L to ExtractedText(10L, "cv.pdf", "Alice Chen from MIT", supported = true)))

        val llmResponse = """
            {
              "fields": [
                {
                  "key": "name",
                  "label": "姓名",
                  "value": "Alice Chen",
                  "sourceFileId": "att_10",
                  "excerpt": "Alice Chen"
                },
                {
                  "key": "note",
                  "label": "备注",
                  "value": "Fake source",
                  "sourceFileId": "att_10",
                  "excerpt": "not in file"
                }
              ]
            }
        """.trimIndent()

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = llmResponse
        }
        Mockito.`when`(llmDraftClientProvider.getIfAvailable()).thenReturn(client)

        var savedId = 100L
        Mockito.`when`(analysisResultRepository.save(any(ExpertAnalysisResult::class.java)))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ExpertAnalysisResult>(0)
                entity.copy(id = savedId++)
            }

        val result = service.analyze(1L, listOf(10L))

        assertEquals(2, result.fields.size)
        assertTrue(result.fields[0].verified)
        assertFalse(result.fields[1].verified)

        val existing = ExpertAnalysisResult(
            id = 100L,
            expertContactId = 1L,
            fieldKey = "name",
            fieldLabel = "姓名",
            value = "Alice Chen",
            sourceAttachmentId = 10L,
            sourceExcerpt = "Alice Chen",
            excerptVerified = true,
            displayOrder = 0
        )
        Mockito.`when`(analysisResultRepository.findById(100L)).thenReturn(Optional.of(existing))
        Mockito.`when`(analysisResultRepository.save(any(ExpertAnalysisResult::class.java)))
            .thenAnswer { invocation ->
                invocation.getArgument<ExpertAnalysisResult>(0)
            }

        val updated = service.updateField(1L, 100L, "Alice C. Chen")

        assertEquals("Alice C. Chen", updated.value)
        assertEquals(10L, updated.sourceAttachmentId)
        assertEquals("Alice Chen", updated.sourceExcerpt)
        assertTrue(updated.verified)

        val saveCaptor = ArgumentCaptor.forClass(ExpertAnalysisResult::class.java)
        Mockito.verify(analysisResultRepository, Mockito.atLeastOnce()).save(saveCaptor.capture())
        val lastSaved = saveCaptor.allValues.last()
        assertEquals("Alice C. Chen", lastSaved.value)
        assertEquals("Alice Chen", lastSaved.sourceExcerpt)
        assertTrue(lastSaved.excerptVerified)
    }
}
