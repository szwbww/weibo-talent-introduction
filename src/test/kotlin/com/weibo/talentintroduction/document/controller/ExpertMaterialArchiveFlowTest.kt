package com.weibo.talentintroduction.document.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.document.service.ExpertMaterialArchiveService
import com.weibo.talentintroduction.document.service.MaterialArchiveFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

class ExpertMaterialArchiveFlowTest {
    @TempDir lateinit var root: Path
    private lateinit var f: MaterialArchiveFixture
    private lateinit var mvc: MockMvc
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val session = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "tester") }

    @BeforeEach
    fun setup() {
        f = MaterialArchiveFixture(root)
        val auth = Mockito.mock(AuthService::class.java)
        Mockito.`when`(auth.findUser("tester")).thenReturn(AdminUser(username = "tester", passwordHash = "unused", mustChangePassword = false, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
        mvc = MockMvcBuilders.standaloneSetup(ExpertMaterialController(f.material, archiveService = f.archive))
            .addInterceptors(AuthInterceptor(auth, mapper, org.mockito.Mockito.mock(com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard::class.java)))
            .setControllerAdvice(ExpertMaterialHttpExceptionAdvice(), GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper)).build()
    }

    private fun download(body: String, loggedIn: Boolean = true) = mvc.perform(
        post("/api/expert-contacts/7/materials/download-zip")
            .contentType(MediaType.APPLICATION_JSON).content(body)
            .also { if (loggedIn) it.session(session) }
    ).andReturn().response

    @Test
    fun `authenticated response contains exact complete archive and download headers`() {
        f.file(1, "简历.pdf", "pdf bytes".toByteArray(), "record")
        f.file(2, "照片.jpg", byteArrayOf(0, 1, 2), "manual")
        val response = download("{\"attachmentIds\":[1,2]}")
        assertEquals(200, response.status)
        assertEquals("application/zip", response.contentType)
        assertEquals("attachment; filename=\"materials-7.zip\"", response.getHeader("Content-Disposition"))
        assertEquals("no-store", response.getHeader("Cache-Control"))
        assertEquals(response.contentAsByteArray.size.toString(), response.getHeader("Content-Length"))
        ZipInputStream(ByteArrayInputStream(response.contentAsByteArray)).use { zip ->
            assertEquals("简历.pdf", zip.nextEntry.name)
            assertArrayEquals("pdf bytes".toByteArray(), zip.readBytes())
            assertEquals("照片.jpg", zip.nextEntry.name)
            assertArrayEquals(byteArrayOf(0, 1, 2), zip.readBytes())
            assertNull(zip.nextEntry)
        }
        f.assertNoWritesOrTransfers()
    }

    @Test
    fun `unauthenticated request rejected before archive reads`() {
        assertEquals(401, download("{\"attachmentIds\":[1]}", false).status)
        Mockito.verifyNoInteractions(f.attachments)
    }

    @Test
    fun `invalid missing foreign and not ready files return JSON errors never ZIP`() {
        f.file(1, contact = 8)
        f.register(2, "未获取.pdf", null)
        val cases = listOf("[]" to 400, "[0]" to 400, "[null]" to 400, "[1]" to 400, "[99]" to 404, "[2]" to 409)
        for ((ids, status) in cases) {
            val response = download("{\"attachmentIds\":$ids}")
            assertEquals(status, response.status, ids)
            assertFalse(response.contentType.orEmpty().contains("zip"))
            assertNull(response.getHeader("Content-Disposition"))
            if (status == 409) {
                val json = mapper.readTree(response.contentAsByteArray)
                assertEquals("MATERIAL_NOT_READY", json["code"].asText())
                assertEquals(2, json["attachmentId"].asInt())
                assertTrue(json["message"].asText().contains("未获取.pdf"))
            }
        }
        f.assertNoWritesOrTransfers()
    }

    @Test
    fun `oversized files return 413 with safe message and no binary headers`() {
        val path = f.file(1)
        RandomAccessFile(path.toFile(), "rw").use { it.setLength(ExpertMaterialArchiveService.MAX_BYTES + 1) }
        val response = download("{\"attachmentIds\":[1]}")
        assertEquals(413, response.status)
        assertEquals("所选资料超过 500 MiB，请分批下载", mapper.readTree(response.contentAsByteArray)["message"].asText())
        assertNull(response.getHeader("Content-Disposition"))
    }
}
