package com.weibo.talentintroduction.document.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.document.domain.MeetingVideoPage
import com.weibo.talentintroduction.document.domain.MeetingVideoRenameRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoView
import com.weibo.talentintroduction.document.service.ExpertMeetingVideoService
import com.weibo.talentintroduction.document.service.MeetingVideoFile
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * 04：鉴权、归属、raw 上传协议、Range 响应与固定错误码（局部 `@ExceptionHandler`）。
 *
 * Range/206/Content-Range/416 由**真实** Spring MVC 转换器处理（service 只提供真实文件），
 * 因此这里验证的是「不自制 Range 解析器」的实际行为；文件内容与响应头逐项断言。
 */
@WebMvcTest(controllers = [ExpertMeetingVideoController::class])
@Import(AuthWebConfig::class, ExpertMeetingVideoControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class ExpertMeetingVideoControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard

    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var service: ExpertMeetingVideoService

    @MockBean
    private lateinit var authService: AuthService

    @TempDir
    lateinit var tempDir: Path

    private val jackson = ObjectMapper()

    private val videoId = "11111111-1111-1111-1111-111111111111"

    private val view = MeetingVideoView(
        id = videoId,
        expertId = "0000-0001",
        originalFilename = "企业对接会.mp4",
        displayFilename = "企业对接会-第二次.mp4",
        extension = "mp4",
        byteLength = 4096,
        uploadedBy = "admin",
        uploadedAt = "2026-10-11T10:18:00.123+08:00",
        updatedBy = "admin",
        updatedAt = "2026-10-11T10:18:00.123+08:00",
        version = 1,
        previewUrl = "/api/experts/meeting-videos/$videoId/preview?expertId=0000-0001",
        downloadUrl = "/api/experts/meeting-videos/$videoId/download?expertId=0000-0001"
    )

    /** Mockito.any()/eq() 对 Kotlin 非空参数会返回 null；用真实占位实例补位（既有测试同款手法）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun emptyStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    private fun session(username: String = "admin", mustChangePassword: Boolean = false): MockHttpSession {
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(
                id = 1, username = username, passwordHash = "hash",
                mustChangePassword = mustChangePassword, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
            )
        )
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }
    }

    private fun videoFile(size: Int = 4096, display: String = "企业对接会-第二次.mp4"): MeetingVideoFile {
        val path = tempDir.resolve("stored")
        Files.write(path, ByteArray(size) { (it % 251).toByte() })
        return MeetingVideoFile(path, display, "mp4", "video/mp4", size.toLong())
    }

    private fun uploadCommand(
        expertId: String? = "0000-0001",
        level: ExpertIndexLevel = ExpertIndexLevel.RAW,
        originalName: String? = "a.mp4",
        nameStem: String? = null,
        expectedSize: String? = "3",
        declaredLength: Long? = 3L
    ) = MeetingVideoUploadRequest(expertId, level, originalName, nameStem, expectedSize, declaredLength)

    // ------------------------------------------------------------------
    // 鉴权与归属（I-1）
    // ------------------------------------------------------------------

    @Test
    fun `all five endpoints require the session and never touch the service when unauthenticated`() {
        mvc.perform(
            post("/api/experts/meeting-videos").param("expertId", "0000-0001").param("originalName", "a.mp4")
                .param("expectedSize", "3").contentType(MediaType.APPLICATION_OCTET_STREAM).content(byteArrayOf(1, 2, 3))
        ).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mvc.perform(
            post("/api/experts/meeting-videos").param("expertId", "0000-0001")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/experts/meeting-videos").param("expertId", "0000-0001"))
            .andExpect(status().isUnauthorized)
        mvc.perform(
            patch("/api/experts/meeting-videos/$videoId").param("expertId", "0000-0001")
                .contentType(MediaType.APPLICATION_JSON).content("""{"nameStem":"x","expectedVersion":1}""")
        ).andExpect(status().isUnauthorized)
        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001").header("Range", "bytes=0-9")
        ).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/experts/meeting-videos/$videoId/download").param("expertId", "0000-0001"))
            .andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)

        // 会话过期（用户已删除）与首次登录必须先改密码沿用现有规则。
        val expired = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "deleted-user") }
        mvc.perform(get("/api/experts/meeting-videos").param("expertId", "0000-0001").session(expired))
            .andExpect(status().isUnauthorized)
        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001")
                .session(session("password-user", mustChangePassword = true))
        ).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
        Mockito.verifyNoInteractions(service)
    }

    // ------------------------------------------------------------------
    // 上传协议（I-2/I-3/I-4）
    // ------------------------------------------------------------------

    @Test
    fun `upload accepts octet-stream forwards the raw command and answers 201 with the view`() {
        val command = uploadCommand(
            level = ExpertIndexLevel.CANDIDATE,
            originalName = "企业对接会.mp4",
            nameStem = "企业对接会-第二次",
            expectedSize = "4096",
            declaredLength = 4096L
        )
        Mockito.`when`(service.upload(anyValue(command), anyValue("userA"), anyValue(""), anyValue(emptyStream())))
            .thenReturn(view)

        mvc.perform(
            post("/api/experts/meeting-videos")
                .param("expertId", "0000-0001")
                .param("level", "CANDIDATE")
                .param("originalName", "企业对接会.mp4")
                .param("nameStem", "企业对接会-第二次")
                .param("expectedSize", "4096")
                .session(session("userA"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(ByteArray(4096))
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(videoId))
            .andExpect(jsonPath("$.expertId").value("0000-0001"))
            .andExpect(jsonPath("$.originalFilename").value("企业对接会.mp4"))
            .andExpect(jsonPath("$.displayFilename").value("企业对接会-第二次.mp4"))
            .andExpect(jsonPath("$.extension").value("mp4"))
            .andExpect(jsonPath("$.byteLength").value(4096))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.previewUrl").value("/api/experts/meeting-videos/$videoId/preview?expertId=0000-0001"))
            .andExpect(jsonPath("$.downloadUrl").value("/api/experts/meeting-videos/$videoId/download?expertId=0000-0001"))
            // 响应绝不包含磁盘路径或中间状态（字段集就是 VideoView 的 13 项）。
            .andExpect(jsonPath("$.storagePath").doesNotExist())
            .andExpect(jsonPath("$.tempPath").doesNotExist())

        Mockito.verify(service).upload(
            eqValue(command), eqValue("userA"), eqValue(""), anyValue(emptyStream())
        )
    }

    @Test
    fun `upload defaults the level to RAW and forwards missing optional parameters as null`() {
        val command = uploadCommand(originalName = null, expectedSize = null, declaredLength = 0L)
        Mockito.`when`(service.upload(anyValue(command), anyValue("admin"), anyValue(""), anyValue(emptyStream())))
            .thenReturn(view)

        mvc.perform(
            post("/api/experts/meeting-videos").param("expertId", "0000-0001").session(session())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(ByteArray(0))
        ).andExpect(status().isCreated)
        Mockito.verify(service).upload(
            eqValue(command), eqValue("admin"), eqValue(""), anyValue(emptyStream())
        )
    }

    @Test
    fun `non octet-stream bodies are rejected with 415 without reading parts or calling the service`() {
        listOf(MediaType.APPLICATION_JSON, MediaType.MULTIPART_FORM_DATA, MediaType.TEXT_PLAIN).forEach { type ->
            mvc.perform(
                post("/api/experts/meeting-videos").param("expertId", "0000-0001").param("originalName", "a.mp4")
                    .param("expectedSize", "3").session(session()).contentType(type).content("{}")
            ).andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO_TYPE"))
                .andExpect(jsonPath("$.detail").value("Unsupported Media Type"))
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `upload business failures map to their fixed codes and never leak internals`() {
        val command = uploadCommand()
        val failures = listOf(
            Triple(MeetingVideoException.invalidName("视频名称不能超过 100 个字符"), 400, "INVALID_VIDEO_NAME"),
            Triple(MeetingVideoException.sizeMismatch("声明的文件大小与实际字节数不一致"), 400, "VIDEO_SIZE_MISMATCH"),
            Triple(MeetingVideoException.empty("视频文件为空"), 400, "EMPTY_VIDEO"),
            Triple(MeetingVideoException.notFound("专家不存在"), 404, "VIDEO_NOT_FOUND"),
            Triple(MeetingVideoException.nameConflict("同名视频已存在，请修改名称后重试"), 409, "VIDEO_NAME_CONFLICT"),
            Triple(MeetingVideoException.versionConflict("视频已被更新，请重新加载后再重试"), 409, "VIDEO_VERSION_CONFLICT"),
            Triple(MeetingVideoException.tooLarge("单个视频不能超过 1073741824 字节"), 413, "VIDEO_TOO_LARGE"),
            Triple(MeetingVideoException.unsupportedType("只支持 mp4、mov、webm 视频"), 415, "UNSUPPORTED_VIDEO_TYPE"),
            Triple(MeetingVideoException.busy("当前上传任务过多，请稍后重试"), 429, "VIDEO_UPLOAD_BUSY"),
            Triple(MeetingVideoException.lookupUnavailable("专家身份查询服务不可用，请稍后重试"), 503, "EXPERT_LOOKUP_UNAVAILABLE"),
            Triple(MeetingVideoException.storageFull("存储空间不足，无法保存视频"), 507, "VIDEO_STORAGE_FULL"),
            Triple(MeetingVideoException.storeFailed("视频保存失败，请稍后重试"), 500, "VIDEO_STORE_FAILED")
        )
        failures.forEach { (failure, statusCode, code) ->
            Mockito.reset(service)
            Mockito.`when`(
                service.upload(anyValue(command), anyValue("admin"), anyValue(""), anyValue(emptyStream()))
            ).thenThrow(failure)
            val body = mvc.perform(
                post("/api/experts/meeting-videos").param("expertId", "0000-0001").param("originalName", "a.mp4")
                    .param("expectedSize", "3").session(session())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(byteArrayOf(1, 2, 3))
            ).andExpect(status().`is`(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(failure.message))
                .andExpect(jsonPath("$.detail").value(HttpStatus.valueOf(statusCode).reasonPhrase))
                .andReturn().response.getContentAsString(StandardCharsets.UTF_8)
            assertEquals(setOf("code", "message", "detail"), jackson.readTree(body).fieldNames().asSequence().toSet())
            assertFalse(body.contains("/Users"))
        }

        // 未预期异常：固定 500 文案，内部 SQL/路径/字段值不外泄。
        Mockito.reset(service)
        Mockito.`when`(service.upload(anyValue(command), anyValue("admin"), anyValue(""), anyValue(emptyStream())))
            .thenThrow(RuntimeException("insert into expert_meeting_video at /opt/talent/uploads failed"))
        val leaked = mvc.perform(
            post("/api/experts/meeting-videos").param("expertId", "0000-0001").param("originalName", "a.mp4")
                .param("expectedSize", "3").session(session())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(byteArrayOf(1, 2, 3))
        ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("VIDEO_STORE_FAILED"))
            .andExpect(jsonPath("$.message").value("视频处理失败，请稍后重试"))
            .andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        assertFalse(leaked.contains("/opt"))
        assertFalse(leaked.contains("insert into"))
    }

    // ------------------------------------------------------------------
    // 列表与重命名（I-5/I-10）
    // ------------------------------------------------------------------

    @Test
    fun `list forwards pagination and returns items total page size`() {
        Mockito.`when`(service.list("0000-0001", 1, 20, "")).thenReturn(
            MeetingVideoPage(listOf(view), 21L, 1, 20)
        )
        mvc.perform(
            get("/api/experts/meeting-videos").param("expertId", "0000-0001").param("page", "1").param("size", "20")
                .session(session())
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(21))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.items[0].id").value(videoId))
            .andExpect(jsonPath("$.items[0].version").value(1))

        Mockito.`when`(service.list("0000-0009", null, null, "")).thenReturn(MeetingVideoPage(emptyList(), 0L, 0, 20))
        mvc.perform(get("/api/experts/meeting-videos").param("expertId", "0000-0009").session(session()))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"items":[],"total":0,"page":0,"size":20}""", true))

        // 非数字分页参数在进入业务前被拒绝。
        mvc.perform(
            get("/api/experts/meeting-videos").param("expertId", "0000-0001").param("page", "abc").session(session())
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_VIDEO_NAME"))
    }

    @Test
    fun `rename parses the json body validates it and returns the latest view`() {
        Mockito.`when`(
            service.rename(MeetingVideoRenameRequest("企业对接会-第二次", 3L), "0000-0001", videoId, "admin", "")
        ).thenReturn(view)
        mvc.perform(
            patch("/api/experts/meeting-videos/$videoId").param("expertId", "0000-0001").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nameStem":"企业对接会-第二次","expectedVersion":3}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.displayFilename").value("企业对接会-第二次.mp4"))
            .andExpect(jsonPath("$.version").value(1))
        Mockito.verify(service).rename(
            eqValue(MeetingVideoRenameRequest("企业对接会-第二次", 3L)), eqValue("0000-0001"),
            eqValue(videoId), eqValue("admin"), eqValue("")
        )

        // 缺失/损坏请求体：400 固定码，不进入业务。
        Mockito.reset(service)
        listOf("", "{}", "{", """{"nameStem":"x"}""", """{"nameStem":"x","expectedVersion":"one"}""").forEach { body ->
            mvc.perform(
                patch("/api/experts/meeting-videos/$videoId").param("expertId", "0000-0001").session(session())
                    .contentType(MediaType.APPLICATION_JSON).content(body)
            ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO_NAME"))
        }
        Mockito.verifyNoInteractions(service)

        // 重命名业务失败沿用同一组固定码。
        Mockito.reset(service)
        Mockito.`when`(
            service.rename(anyValue(MeetingVideoRenameRequest("x", 1L)), anyValue("0000-0001"), anyValue(videoId),
                anyValue("admin"), anyValue(""))
        ).thenThrow(MeetingVideoException.versionConflict("视频已被更新，请重新加载后再重试"))
        mvc.perform(
            patch("/api/experts/meeting-videos/$videoId").param("expertId", "0000-0001").session(session())
                .contentType(MediaType.APPLICATION_JSON).content("""{"nameStem":"x","expectedVersion":1}""")
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("VIDEO_VERSION_CONFLICT"))
    }

    // ------------------------------------------------------------------
    // 播放与下载（I-7/I-8）
    // ------------------------------------------------------------------

    @Test
    fun `preview and download set inline and attachment utf8 filenames with hardening headers`() {
        Mockito.`when`(service.resolveFile("0000-0001", videoId)).thenReturn(videoFile())
        val preview = mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001").session(session())
        ).andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "video/mp4"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "4096"))
            .andReturn()
        val disposition = preview.response.getHeader("Content-Disposition")!!
        assertTrue(disposition.startsWith("inline;"), disposition)
        assertTrue(
            disposition.contains("filename*=UTF-8''%E4%BC%81%E4%B8%9A%E5%AF%B9%E6%8E%A5%E4%BC%9A-%E7%AC%AC%E4%BA%8C%E6%AC%A1.mp4"),
            disposition
        )
        assertFalse(disposition.contains("\r"))
        assertFalse(disposition.contains("\n"))
        assertEquals(4096, preview.response.contentAsByteArray.size)

        val download = mvc.perform(
            get("/api/experts/meeting-videos/$videoId/download").param("expertId", "0000-0001").session(session())
        ).andExpect(status().isOk).andReturn()
        assertTrue(download.response.getHeader("Content-Disposition")!!.startsWith("attachment;"))
    }

    @Test
    fun `range requests are served after ownership checks with 206 and 416`() {
        val file = videoFile(size = 4096)
        Mockito.`when`(service.resolveFile("0000-0001", videoId)).thenReturn(file)

        val partial = mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001")
                .header("Range", "bytes=0-1023").session(session())
        ).andExpect(status().isPartialContent)
            .andExpect(header().string("Content-Range", "bytes 0-1023/4096"))
            .andExpect(header().string("Content-Length", "1024"))
            .andReturn()
        assertEquals(1024, partial.response.contentAsByteArray.size)
        assertTrue(partial.response.contentAsByteArray.contentEquals(ByteArray(1024) { (it % 251).toByte() }))

        // 末尾范围与后缀范围都被夹到文件末尾。
        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/download").param("expertId", "0000-0001")
                .header("Range", "bytes=4000-9999").session(session())
        ).andExpect(status().isPartialContent)
            .andExpect(header().string("Content-Range", "bytes 4000-4095/4096"))
            .andExpect(header().string("Content-Length", "96"))

        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001")
                .header("Range", "bytes=-100").session(session())
        ).andExpect(status().isPartialContent)
            .andExpect(header().string("Content-Range", "bytes 3996-4095/4096"))

        // 起点越界与非法范围：416 + 总长度提示。
        listOf("bytes=4096-", "bytes=-0", "bytes=abc", "items=0-1").forEach { range ->
            mvc.perform(
                get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001")
                    .header("Range", range).session(session())
            ).andExpect(status().isRequestedRangeNotSatisfiable)
                .andExpect(header().string("Content-Range", "bytes */4096"))
        }

        // 无 Range：整文件 200。
        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0001").session(session())
        ).andExpect(status().isOk)
            .andExpect(header().string("Content-Length", "4096"))
    }

    @Test
    fun `playback resolves ownership before streaming and hides paths on failure`() {
        Mockito.`when`(service.resolveFile("0000-0002", videoId))
            .thenThrow(MeetingVideoException.notFound("视频不存在或不属于该专家"))
        mvc.perform(
            get("/api/experts/meeting-videos/$videoId/preview").param("expertId", "0000-0002")
                .header("Range", "bytes=0-1023").session(session())
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("视频不存在或不属于该专家"))

        Mockito.`when`(service.resolveFile("0000-0001", videoId))
            .thenThrow(MeetingVideoException.storeFailed("视频文件不可读取，请重新上传"))
        val body = mvc.perform(
            get("/api/experts/meeting-videos/$videoId/download").param("expertId", "0000-0001").session(session())
        ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("VIDEO_STORE_FAILED"))
            .andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        assertFalse(body.contains(tempDir.toString()))
    }
}
