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
    fun `analyze fails with MATERIAL_NOT_READY before any result write when a selected file is not ready`() {
        // I-3：读取前失败（材料未就绪 → 409）绝不能先删除既有分析结果。
        Mockito.doThrow(MaterialNotReadyException(9L, "METADATA_ONLY", "Attachment 9 has no local file"))
            .`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 9L)

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            service.analyze(1L, listOf(9L))
        }
        assertEquals(9L, ex.attachmentId)
        Mockito.verify(analysisResultRepository, Mockito.never())
            .deleteAllByExpertContactId(Mockito.anyLong())
        Mockito.verify(analysisResultRepository, Mockito.never())
            .save(Mockito.any(ExpertAnalysisResult::class.java))
    }

    @Test
    fun `analyze with readable and empty-text mix rejects whole batch naming the file with reason`() {
        // I-2/I-3：extract 之后若任一所选空文本（如扫描 PDF 无可读文字），必须整批拒绝：
        // LLM 调用 0、deleteAll 0、旧结果保留；不能静默过滤后只分析可读部分。
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 10L)
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 11L)
        Mockito.`when`(documentTextExtractor.extract(1L, listOf(10L, 11L)))
            .thenReturn(
                linkedMapOf(
                    10L to ExtractedText(10L, "cv.pdf", "Alice Chen from MIT", supported = true),
                    11L to ExtractedText(11L, "scan-copy.pdf", "   ", supported = true)
                )
            )

        val ex = assertThrows(AnalysisFailedException::class.java) {
            service.analyze(1L, listOf(10L, 11L))
        }

        assertTrue(ex.message!!.contains("scan-copy.pdf"), "message 必须列明空文本文件名")
        assertTrue(ex.message!!.contains("11"), "message 必须列明 attachmentId")
        assertTrue(ex.message!!.contains("无可读文字"), "message 必须给出原因 无可读文字")
        Mockito.verify(llmDraftClientProvider, Mockito.never()).getIfAvailable()
        Mockito.verify(analysisResultRepository, Mockito.never())
            .deleteAllByExpertContactId(Mockito.anyLong())
        Mockito.verify(analysisResultRepository, Mockito.never())
            .save(Mockito.any(ExpertAnalysisResult::class.java))
    }

    @Test
    fun `analyze with unsupported format among selection rejects whole batch naming the file with reason`() {
        // I-2/I-3：任一所选 unsupported（如 JPEG）→ 整批拒绝并给 不支持格式 原因，
        // LLM 与 deleteAll 都不执行，历史结果保留。
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 10L)
        Mockito.doNothing().`when`(documentTextExtractor).validateAttachmentBelongsToContact(1L, 12L)
        Mockito.`when`(documentTextExtractor.extract(1L, listOf(10L, 12L)))
            .thenReturn(
                linkedMapOf(
                    10L to ExtractedText(10L, "cv.pdf", "Alice Chen from MIT", supported = true),
                    12L to ExtractedText(
                        12L,
                        "portrait.jpg",
                        "",
                        supported = false,
                        unsupportedReason = "不支持的文件类型: image/jpeg"
                    )
                )
            )

        val ex = assertThrows(AnalysisFailedException::class.java) {
            service.analyze(1L, listOf(10L, 12L))
        }

        assertTrue(ex.message!!.contains("portrait.jpg"), "message 必须列明不支持格式文件名")
        assertTrue(ex.message!!.contains("12"), "message 必须列明 attachmentId")
        assertTrue(ex.message!!.contains("不支持格式"), "message 必须给出原因 不支持格式")
        Mockito.verify(llmDraftClientProvider, Mockito.never()).getIfAvailable()
        Mockito.verify(analysisResultRepository, Mockito.never())
            .deleteAllByExpertContactId(Mockito.anyLong())
        Mockito.verify(analysisResultRepository, Mockito.never())
            .save(Mockito.any(ExpertAnalysisResult::class.java))
    }

    @Test
    fun `addField keeps display order and clearResults stays a plain delete`() {
        // 字段编辑/新增/清空接口语义不变（结果 schema/保存 API 不因 09 改造受影响）。
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
        Mockito.`when`(analysisResultRepository.findAllByExpertContactIdOrderByDisplayOrderAsc(1L))
            .thenReturn(listOf(existing))
        Mockito.`when`(analysisResultRepository.save(any(ExpertAnalysisResult::class.java)))
            .thenAnswer { invocation ->
                invocation.getArgument<ExpertAnalysisResult>(0).copy(id = 200L)
            }

        val created = service.addField(1L, "custom_note", "补充备注", " 2020 起任职  ")
        assertEquals(200L, created.id)
        assertEquals("custom_note", created.fieldKey)
        assertEquals("补充备注", created.fieldLabel)
        assertEquals("2020 起任职", created.value)
        val saveCaptor = ArgumentCaptor.forClass(ExpertAnalysisResult::class.java)
        Mockito.verify(analysisResultRepository).save(saveCaptor.capture())
        assertEquals(1, saveCaptor.value.displayOrder, "新字段 displayOrder 接在既有字段之后")

        service.clearResults(1L)
        Mockito.verify(analysisResultRepository).deleteAllByExpertContactId(1L)
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
