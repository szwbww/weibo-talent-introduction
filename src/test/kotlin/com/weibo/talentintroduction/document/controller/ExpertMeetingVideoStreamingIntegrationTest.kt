package com.weibo.talentintroduction.document.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.MeetingVideoLimits
import com.weibo.talentintroduction.document.service.ExpertMeetingVideoService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import org.apache.catalina.startup.Tomcat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

/**
 * 04（I-1/I-2/I-6/I-7/I-8/I-9）：**真实 HTTP + 真实 servlet 容器**的 raw 上传与 Range 播放测试。
 *
 * - 真实 Tomcat（随机端口）+ 真实 DispatcherServlet/消息转换器：206/Content-Range/416 全部由
 *   Spring 5.3 的 Resource/ResourceRegion 支持产生，`Range` 只在登录与归属检查之后生效；
 * - 真实文件流：请求体由生成器写进 socket（fixed-length/chunked 两种模式），服务端以固定 64 KiB
 *   缓冲落盘，绝不构造大数组、绝不把视频读进内存；
 * - 替身只有 JDBC 边界（内存行台账）与 01 的身份解析器；文件、HTTP、范围响应、Content-Disposition
 *   全部真实。真实唯一键/CAS 竞争在 `FlywayMigrationIntegrationTest`（真实 MySQL）覆盖。
 *
 * 默认档只跑小体积协议测试；100 MiB+1 与 1 GiB 真实 HTTP 档由 `-DvideoLargeIt=true`
 * （或 `VIDEO_LARGE_IT=true`）显式开启——没有跑过就不得声称 1 GiB 已支持。
 */
class ExpertMeetingVideoStreamingIntegrationTest {

    @Configuration
    @EnableWebMvc
    class ItWebConfig {
        @Bean
        fun meetingVideoService(): ExpertMeetingVideoService = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = storageRoot.toString()),
            identityResolver,
            jdbc,
            TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java))
        )
    }

    companion object {
        private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
        private val identityResolver = Mockito.mock(ExpertProfileIdentityResolver::class.java)
        private val ledger = ConcurrentHashMap<String, Map<String, Any?>>()
        private lateinit var workDir: Path
        private lateinit var storageRoot: Path
        private lateinit var server: Tomcat
        private var port = 0
        private val json = ObjectMapper()

        @JvmStatic
        fun videoLargeTierEnabled(): Boolean =
            System.getProperty("videoLargeIt") == "true" || System.getenv("VIDEO_LARGE_IT") == "true"

        @JvmStatic
        @BeforeAll
        fun startServer() {
            workDir = Files.createTempDirectory("meeting-video-it-")
            storageRoot = workDir.resolve("storage")
            stubCollaborators()
            val docBase = Files.createTempDirectory(workDir, "docbase").toFile()
            val tomcatBase = Files.createTempDirectory(workDir, "tomcat")
            server = Tomcat().apply {
                port = 0
                setBaseDir(tomcatBase.toString())
            }
            val context = server.addContext("", docBase.absolutePath)
            val appContext = AnnotationConfigWebApplicationContext().apply {
                setServletContext(context.servletContext)
                register(
                    ItWebConfig::class.java,
                    ExpertMeetingVideoController::class.java,
                    ItSessionController::class.java
                )
                refresh()
            }
            Tomcat.addServlet(context, "dispatcher", DispatcherServlet(appContext))
            context.addServletMappingDecoded("/*", "dispatcher")
            server.start()
            port = server.connector.localPort
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            if (::server.isInitialized) {
                try {
                    server.stop()
                    server.destroy()
                } catch (ex: Exception) {
                    // 容器已停：清理阶段不再掩盖测试结论。
                }
            }
            if (::workDir.isInitialized && Files.exists(workDir)) {
                // 大文件档的临时数据只落在系统临时目录，结束时整体删除。
                Files.walk(workDir).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }

        /** 真实 Tomcat + 内存行台账：上传/列表/播放共用同一份行数据。 */
        private fun stubCollaborators() {
            ExpertIndexLevel.values().forEach { level ->
                listOf("0000-0001", "0000-0002").forEach { id ->
                    Mockito.`when`(identityResolver.requireKnown(id, level)).thenReturn(id.uppercase())
                }
                Mockito.`when`(identityResolver.requireKnown("missing-expert", level))
                    .thenThrow(ExpertProfileNotFoundException("专家不存在"))
                Mockito.`when`(identityResolver.requireKnown(null, level))
                    .thenThrow(ExpertManualProfileInvalidRequestException("缺少专家标识，无法保存"))
            }
            Mockito.doAnswer { invocation ->
                val params = invocation.arguments[1] as MapSqlParameterSource
                ledger[params.getValue("id") as String] = mapOf(
                    "id" to params.getValue("id"),
                    "expert_key" to params.getValue("expertKey"),
                    "original_filename" to params.getValue("originalFilename"),
                    "display_filename" to params.getValue("displayFilename"),
                    "byte_length" to params.getValue("byteLength"),
                    "uploaded_by" to params.getValue("uploadedBy"),
                    "uploaded_at" to params.getValue("uploadedAt"),
                    "updated_by" to params.getValue("updatedBy"),
                    "updated_at" to params.getValue("updatedAt"),
                    "version" to 1L
                )
                1
            }.`when`(jdbc).update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java))
            Mockito.doAnswer { invocation ->
                val params = invocation.arguments[1] as MapSqlParameterSource
                ledger.values.count { it["expert_key"] == params.getValue("expertKey") }.toLong()
            }.`when`(jdbc).queryForObject(
                Mockito.anyString(),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.eq(Long::class.java)
            )
            Mockito.doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                val params = invocation.arguments[1] as MapSqlParameterSource
                val mapper = invocation.arguments[2] as RowMapper<Any>
                val matching = ledger.values.filter { it["expert_key"] == params.getValue("expertKey") }
                val selected = when {
                    sql.contains("ORDER BY") -> matching
                        .sortedWith(
                            compareByDescending<Map<String, Any?>> { it["uploaded_at"] as LocalDateTime }
                                .thenByDescending { it["id"] as String }
                        )
                        .drop(((params.getValue("offset") as Long).toInt()))
                        .take(params.getValue("limit") as Int)
                    sql.contains("WHERE id = :id") -> matching.filter { it["id"] == params.getValue("id") }
                    else -> emptyList()
                }
                selected.map { mapper.mapRow(resultSet(it), 0) }
            }.`when`(jdbc).query(
                Mockito.anyString(),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.any<RowMapper<Any>>()
            )
        }

        private fun resultSet(row: Map<String, Any?>): ResultSet {
            val rs = Mockito.mock(ResultSet::class.java)
            Mockito.`when`(rs.getString("id")).thenReturn(row["id"] as String)
            Mockito.`when`(rs.getString("expert_key")).thenReturn(row["expert_key"] as String)
            Mockito.`when`(rs.getString("original_filename")).thenReturn(row["original_filename"] as String)
            Mockito.`when`(rs.getString("display_filename")).thenReturn(row["display_filename"] as String)
            Mockito.`when`(rs.getLong("byte_length")).thenReturn(row["byte_length"] as Long)
            Mockito.`when`(rs.getString("uploaded_by")).thenReturn(row["uploaded_by"] as String)
            Mockito.`when`(rs.getTimestamp("uploaded_at")).thenReturn(Timestamp.valueOf(row["uploaded_at"] as LocalDateTime))
            Mockito.`when`(rs.getString("updated_by")).thenReturn(row["updated_by"] as String)
            Mockito.`when`(rs.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(row["updated_at"] as LocalDateTime))
            Mockito.`when`(rs.getLong("version")).thenReturn(row["version"] as Long)
            return rs
        }
    }

    @BeforeEach
    fun resetLedger() {
        ledger.clear()
        if (Files.exists(storageRoot)) {
            Files.walk(storageRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    // ------------------------------------------------------------------
    // 默认档：小体积协议测试（真实 HTTP）
    // ------------------------------------------------------------------

    @Test
    fun `raw upload list preview and download work end to end over real http`() {
        val session = login()
        val bytes = ByteArray(7) { (it + 1).toByte() }
        val uploaded = upload(
            session = session,
            expertId = "0000-0001",
            originalName = "企业对接会.mp4",
            nameStem = "企业对接会",
            expectedSize = 7L,
            body = bytes
        )
        assertEquals(201, uploaded.status)
        val view = json.readTree(uploaded.body)
        val id = view.get("id").asText()
        assertEquals(7L, view.get("byteLength").asLong())
        assertEquals("企业对接会.mp4", view.get("displayFilename").asText())
        assertEquals("企业对接会.mp4", view.get("originalFilename").asText())
        assertEquals("mp4", view.get("extension").asText())
        assertEquals(1L, view.get("version").asLong())
        assertEquals("/api/experts/meeting-videos/$id/preview?expertId=0000-0001", view.get("previewUrl").asText())
        assertEquals("/api/experts/meeting-videos/$id/download?expertId=0000-0001", view.get("downloadUrl").asText())
        assertTrue(view.get("uploadedAt").asText().endsWith("+08:00"))
        // 磁盘上只有服务端 UUID 命名的文件，字节完全一致，无临时残留。
        assertTrue(Files.exists(storedFile(id)))
        assertTrue(Files.readAllBytes(storedFile(id)).contentEquals(bytes))
        assertEquals(listOf(id), storageFiles())
        assertEquals(1, ledger.size)

        val list = request("GET", "/api/experts/meeting-videos?expertId=0000-0001", session = session)
        assertEquals(200, list.status)
        val page = json.readTree(list.body)
        assertEquals(1, page.get("total").asInt())
        assertEquals(0, page.get("page").asInt())
        assertEquals(20, page.get("size").asInt())
        assertEquals(id, page.get("items").get(0).get("id").asText())

        val full = request("GET", "/api/experts/meeting-videos/$id/preview?expertId=0000-0001", session = session)
        assertEquals(200, full.status)
        assertEquals("video/mp4", full.header("Content-Type"))
        assertEquals("no-store", full.header("Cache-Control"))
        assertEquals("nosniff", full.header("X-Content-Type-Options"))
        assertEquals("bytes", full.header("Accept-Ranges"))
        assertTrue(full.body.contentEquals(bytes))

        val download = request("GET", "/api/experts/meeting-videos/$id/download?expertId=0000-0001", session = session)
        assertEquals(200, download.status)
        val disposition = download.header("Content-Disposition")!!
        assertTrue(disposition.startsWith("attachment;"), disposition)
        assertTrue(disposition.contains("filename*=UTF-8''%E4%BC%81%E4%B8%9A%E5%AF%B9%E6%8E%A5%E4%BC%9A.mp4"), disposition)
        assertTrue(download.body.contentEquals(bytes))
    }

    @Test
    fun `range requests over real http return 206 with exact slices and 416 beyond the end`() {
        val session = login()
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val uploaded = upload(session, "0000-0001", "会议.mp4", "会议", 4096L, bytes)
        assertEquals(201, uploaded.status)
        val id = json.readTree(uploaded.body).get("id").asText()
        val base = "/api/experts/meeting-videos/$id"

        val head = request("GET", "$base/preview?expertId=0000-0001", session = session, range = "bytes=0-1023")
        assertEquals(206, head.status)
        assertEquals("bytes 0-1023/4096", head.header("Content-Range"))
        assertEquals(1024, head.body.size)
        assertTrue(head.body.contentEquals(bytes.copyOfRange(0, 1024)))

        val middle = request("GET", "$base/download?expertId=0000-0001", session = session, range = "bytes=2000-2004")
        assertEquals(206, middle.status)
        assertEquals("bytes 2000-2004/4096", middle.header("Content-Range"))
        assertTrue(middle.body.contentEquals(bytes.copyOfRange(2000, 2005)))

        val tail = request("GET", "$base/preview?expertId=0000-0001", session = session, range = "bytes=4000-99999")
        assertEquals(206, tail.status)
        assertEquals("bytes 4000-4095/4096", tail.header("Content-Range"))

        val suffix = request("GET", "$base/preview?expertId=0000-0001", session = session, range = "bytes=-16")
        assertEquals(206, suffix.status)
        assertEquals("bytes 4080-4095/4096", suffix.header("Content-Range"))
        assertTrue(suffix.body.contentEquals(bytes.copyOfRange(4080, 4096)))

        val beyond = request("GET", "$base/preview?expertId=0000-0001", session = session, range = "bytes=4096-")
        assertEquals(416, beyond.status)
        assertEquals("bytes */4096", beyond.header("Content-Range"))

        val unknown = request("GET", "$base/preview?expertId=0000-0002", session = session, range = "bytes=0-10")
        assertEquals(404, unknown.status)
        assertEquals("VIDEO_NOT_FOUND", json.readTree(unknown.body).get("code").asText())
    }

    @Test
    fun `medium streamed upload keeps byte exact content and serves middle ranges`() {
        val session = login()
        val total = 12L * 1024 * 1024 + 123
        val uploaded = uploadStreamed(session, "0000-0001", "中型会议.mov", "中型会议", total, chunked = false)
        assertEquals(201, uploaded.status)
        val view = json.readTree(uploaded.body)
        val id = view.get("id").asText()
        assertEquals(total, view.get("byteLength").asLong())
        assertEquals("mov", view.get("extension").asText())
        assertEquals(total, Files.size(storedFile(id)))
        assertEquals(listOf(id), storageFiles())

        val slice = request(
            "GET", "/api/experts/meeting-videos/$id/preview?expertId=0000-0001",
            session = session, range = "bytes=12582900-12582999"
        )
        assertEquals(206, slice.status)
        assertEquals("bytes 12582900-12582999/$total", slice.header("Content-Range"))
        assertEquals(100, slice.body.size)
        // 生成器与磁盘内容同源：区间字节必须逐字节相等。
        assertTrue(slice.body.contentEquals(generatedSlice(12582900, 100)))
    }

    @Test
    fun `authentication ownership content type and size gates reject over real http`() {
        val session = login()
        // 未登录：所有端点 401，且不创建任何记录。
        listOf(
            "GET" to "/api/experts/meeting-videos?expertId=0000-0001",
            "GET" to "/api/experts/meeting-videos/11111111-1111-1111-1111-111111111111/preview?expertId=0000-0001",
            "GET" to "/api/experts/meeting-videos/11111111-1111-1111-1111-111111111111/download?expertId=0000-0001"
        ).forEach { (method, path) ->
            val anonymous = request(method, path, range = "bytes=0-10")
            assertEquals(401, anonymous.status, path)
            assertEquals("UNAUTHORIZED", json.readTree(anonymous.body).get("code").asText())
        }
        val anonymousUpload = upload(session = null, expertId = "0000-0001", originalName = "a.mp4", nameStem = null, expectedSize = 1L, body = byteArrayOf(1))
        assertEquals(401, anonymousUpload.status)
        assertEquals(0, ledger.size)

        // 未知专家：上传 404（归属不符），列表空数组。
        val unknownExpert = upload(session, "missing-expert", "a.mp4", null, 1L, byteArrayOf(1))
        assertEquals(404, unknownExpert.status)
        assertEquals("VIDEO_NOT_FOUND", json.readTree(unknownExpert.body).get("code").asText())
        val emptyList = request("GET", "/api/experts/meeting-videos?expertId=0000-0009", session = session)
        assertEquals(200, emptyList.status)
        assertEquals(0, json.readTree(emptyList.body).get("total").asInt())
        assertEquals(0, ledger.size)

        // 非 octet-stream 请求体：415（不解析 multipart、不落盘）。
        listOf("application/json", "text/plain", "application/x-www-form-urlencoded").forEach { type ->
            val rejected = request(
                "POST", "/api/experts/meeting-videos?expertId=0000-0001&originalName=a.mp4&expectedSize=1",
                session = session, contentType = type, body = byteArrayOf(1)
            )
            assertEquals(415, rejected.status, "type=$type body=${String(rejected.body, StandardCharsets.UTF_8)}")
            assertEquals("UNSUPPORTED_VIDEO_TYPE", json.readTree(rejected.body).get("code").asText())
        }
        // 白名单外扩展名同样 415。
        val avi = upload(session, "0000-0001", "会议.avi", null, 1L, byteArrayOf(1))
        assertEquals(415, avi.status)
        assertEquals("UNSUPPORTED_VIDEO_TYPE", json.readTree(avi.body).get("code").asText())

        // 前置超限（expectedSize 超过上限）在读取请求体之前 413。
        val tooLarge = upload(
            session, "0000-0001", "会议.mp4", null, MeetingVideoLimits.MAX_BYTES + 1, byteArrayOf(1)
        )
        assertEquals(413, tooLarge.status)
        assertEquals("VIDEO_TOO_LARGE", json.readTree(tooLarge.body).get("code").asText())

        // Content-Length 与实际请求体不符 → 400；零字节声明 → 400 EMPTY_VIDEO。
        val mismatch = request(
            "POST", "/api/experts/meeting-videos?expertId=0000-0001&originalName=%E4%BC%9A%E8%AE%AE.mp4&expectedSize=4",
            session = session, contentType = "application/octet-stream", body = byteArrayOf(1, 2, 3, 4, 5)
        )
        assertEquals(400, mismatch.status)
        assertEquals("VIDEO_SIZE_MISMATCH", json.readTree(mismatch.body).get("code").asText())
        val empty = upload(session, "0000-0001", "会议.mp4", null, 0L, ByteArray(0))
        assertEquals(400, empty.status)
        assertEquals("EMPTY_VIDEO", json.readTree(empty.body).get("code").asText())

        // 所有失败都不留下文件与行。
        assertEquals(emptyList<String>(), storageFiles())
        assertTrue(ledger.isEmpty())
    }

    @Test
    fun `upload url encoded file name parameters decode exactly once`() {
        val session = login()
        val uploaded = upload(
            session = session,
            expertId = "0000-0001",
            originalName = "冰雪 会 A.webm",
            nameStem = "冰雪会",
            expectedSize = 3L,
            body = byteArrayOf(9, 8, 7)
        )
        assertEquals(201, uploaded.status)
        val view = json.readTree(uploaded.body)
        assertEquals("冰雪会.webm", view.get("displayFilename").asText())
        assertEquals("冰雪 会 A.webm", view.get("originalFilename").asText())
        assertEquals("video/webm", request(
            "GET", view.get("previewUrl").asText(), session = session
        ).header("Content-Type"))

        // 名字里的字面 % 只能被解码一次：%25 必须还原成 %，不能被当成二次转义。
        val percent = upload(session, "0000-0001", "100%会议.mp4", "100%会议", 1L, byteArrayOf(1))
        assertEquals(201, percent.status)
        assertEquals("100%会议.mp4", json.readTree(percent.body).get("originalFilename").asText())
        assertEquals("100%会议.mp4", json.readTree(percent.body).get("displayFilename").asText())
    }

    // ------------------------------------------------------------------
    // 大文件档：100 MiB+1 与 1 GiB（-DvideoLargeIt=true）
    // ------------------------------------------------------------------

    @Test
    @EnabledIf("videoLargeTierEnabled")
    fun `hundred mebibytes plus one byte uploads over real http`() {
        val session = login()
        val total = 100L * 1024 * 1024 + 1
        val started = System.nanoTime()
        val uploaded = uploadStreamed(session, "0000-0001", "百兆会议.mp4", "百兆会议", total, chunked = false)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(201, uploaded.status)
        val view = json.readTree(uploaded.body)
        val id = view.get("id").asText()
        assertEquals(total, view.get("byteLength").asLong())
        assertEquals(total, Files.size(storedFile(id)))
        println("[it] 100MiB+1 upload: $elapsedMs ms (${total * 1000 / maxOf(elapsedMs, 1L) / 1024} KiB/s)")

        val slice = request(
            "GET", "/api/experts/meeting-videos/$id/preview?expertId=0000-0001",
            session = session, range = "bytes=104857000-104857099"
        )
        assertEquals(206, slice.status)
        assertEquals("bytes 104857000-104857099/$total", slice.header("Content-Range"))
        assertTrue(slice.body.contentEquals(generatedSlice(104857000, 100)))
    }

    @Test
    @EnabledIf("videoLargeTierEnabled")
    fun `one gibibyte uploads over real http without buffering and serves ranges`() {
        val session = login()
        val total = MeetingVideoLimits.MAX_BYTES
        val heapBefore = usedHeapBytes()
        val started = System.nanoTime()
        val uploaded = uploadStreamed(session, "0000-0001", "企业对接会.mp4", "企业对接会", total, chunked = true)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(201, uploaded.status)
        val view = json.readTree(uploaded.body)
        val id = view.get("id").asText()
        assertEquals(total, view.get("byteLength").asLong())
        assertEquals(total, Files.size(storedFile(id)))
        val heapGrowth = usedHeapBytes() - heapBefore
        println(
            "[it] 1GiB upload: $elapsedMs ms (${total * 1000 / maxOf(elapsedMs, 1L) / (1024 * 1024)} MiB/s), " +
                "used heap ${heapBefore / (1024 * 1024)} MiB → ${usedHeapBytes() / (1024 * 1024)} MiB"
        )
        // 1 GiB 文件不允许整段驻留内存：堆增长必须远小于文件大小。
        assertTrue(heapGrowth < 512L * 1024 * 1024, "1 GiB 上传的堆增长过大：$heapGrowth 字节")

        val base = "/api/experts/meeting-videos/$id"
        listOf(
            "bytes=0-1023" to (0L to 1024L),
            "bytes=536870912-536870931" to (536870912L to 20L),
            "bytes=1073741000-1073741823" to (1073741000L to 824L),
            "bytes=-16" to (1073741808L to 16L)
        ).forEach { (range, slice) ->
            val (start, length) = slice
            val response = request("GET", "$base/preview?expertId=0000-0001", session = session, range = range)
            assertEquals(206, response.status, range)
            assertEquals("bytes $start-${start + length - 1}/$total", response.header("Content-Range"))
            assertEquals(length.toInt(), response.body.size, range)
            // 返回的字节必须与磁盘同区间一致（真实 seek，而不是从头截流）。
            assertTrue(response.body.contentEquals(fileSlice(storedFile(id), start, length)), range)
        }

        val beyond = request("GET", "$base/preview?expertId=0000-0001", session = session, range = "bytes=$total-")
        assertEquals(416, beyond.status)
        assertEquals("bytes */$total", beyond.header("Content-Range"))
    }

    @Test
    @EnabledIf("videoLargeTierEnabled")
    fun `one gibibyte plus one byte is rejected without leaving files or rows`() {
        val session = login()
        // 声明超限：在读取请求体之前 413（小请求体，连接可正常收尾）。
        val declared = upload(
            session, "0000-0001", "超限.mp4", null, MeetingVideoLimits.MAX_BYTES + 1, byteArrayOf(1)
        )
        assertEquals(413, declared.status)
        assertEquals("VIDEO_TOO_LARGE", json.readTree(declared.body).get("code").asText())

        // 真实 1 GiB+1 字节（chunked，声明 MAX 与流一致）：到上限立即中止。
        val overflow = uploadStreamed(
            session = session,
            expertId = "0000-0001",
            originalName = "溢出.mp4",
            nameStem = "溢出",
            total = MeetingVideoLimits.MAX_BYTES + 1,
            chunked = true,
            expectedSize = MeetingVideoLimits.MAX_BYTES,
            tolerateBrokenPipe = true
        )
        if (overflow.status != BROKEN_PIPE_STATUS) {
            assertEquals(413, overflow.status)
            assertEquals("VIDEO_TOO_LARGE", json.readTree(overflow.body).get("code").asText())
        } else {
            println("[it] 1GiB+1 overflow: 服务端按计划提前断连（容器未返回 JSON），属计划的未知结果路径")
        }
        assertTrue(ledger.isEmpty(), "超限请求不得落库")
        assertEquals(emptyList<String>(), storageFiles(), "超限请求不得留下文件")
    }

    // ------------------------------------------------------------------
    // HTTP 客户端与工具
    // ------------------------------------------------------------------

    /** 客户端未拿到响应（服务端按计划断连）时的显式状态：不是任何真实 HTTP 状态码。 */
    private val BROKEN_PIPE_STATUS = -1

    /** 查询参数一次编码（与 05 的 URLSearchParams 同形）；服务端只解码一次，不二次 URLDecoder。 */
    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun login(username: String = "it-user"): String {
        val response = request("GET", "/it/session?username=$username")
        assertEquals(200, response.status)
        val cookie = response.header("Set-Cookie")!!
        return cookie.substringAfter("JSESSIONID=").substringBefore(';')
    }

    private fun upload(
        session: String?,
        expertId: String?,
        originalName: String,
        nameStem: String?,
        expectedSize: Long,
        body: ByteArray
    ): HttpResult {
        val query = buildString {
            append("/api/experts/meeting-videos")
            append("?expertId=").append(enc(expertId ?: ""))
            append("&level=RAW")
            append("&originalName=").append(enc(originalName))
            if (nameStem != null) append("&nameStem=").append(enc(nameStem))
            append("&expectedSize=").append(expectedSize)
        }
        return request("POST", query, session = session, contentType = "application/octet-stream", body = body)
    }

    /** 以生成器流式发送：fixed-length 或 chunked（未知长度）两种模式。 */
    private fun uploadStreamed(
        session: String?,
        expertId: String,
        originalName: String,
        nameStem: String?,
        total: Long,
        chunked: Boolean,
        expectedSize: Long = total,
        tolerateBrokenPipe: Boolean = false
    ): HttpResult {
        val query = buildString {
            append("/api/experts/meeting-videos?expertId=").append(enc(expertId))
            append("&level=RAW&originalName=").append(enc(originalName))
            if (nameStem != null) append("&nameStem=").append(enc(nameStem))
            append("&expectedSize=").append(expectedSize)
        }
        return request(
            "POST", query, session = session, contentType = "application/octet-stream",
            streamLength = if (chunked) null else total,
            chunked = chunked,
            writer = { output -> writeGenerated(output, total) },
            tolerateBrokenPipe = tolerateBrokenPipe
        )
    }

    private data class HttpResult(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, List<String>>
    ) {
        fun header(name: String): String? = headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
    }

    private fun request(
        method: String,
        path: String,
        session: String? = null,
        contentType: String? = null,
        range: String? = null,
        body: ByteArray? = null,
        streamLength: Long? = null,
        chunked: Boolean = false,
        writer: ((OutputStream) -> Unit)? = null,
        tolerateBrokenPipe: Boolean = false
    ): HttpResult {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 300_000
        connection.instanceFollowRedirects = false
        if (session != null) {
            connection.setRequestProperty("Cookie", "JSESSIONID=$session")
        }
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType)
        }
        if (range != null) {
            connection.setRequestProperty("Range", range)
        }
        try {
            when {
                body != null -> {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                writer != null -> {
                    connection.doOutput = true
                    if (chunked) {
                        connection.setChunkedStreamingMode(64 * 1024)
                    } else {
                        connection.setFixedLengthStreamingMode(streamLength!!)
                    }
                    connection.outputStream.use { writer(it) }
                }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val payload = stream?.let { readBounded(it) } ?: ByteArray(0)
            return HttpResult(status, payload, connection.headerFields.filterKeys { it != null })
        } catch (ex: java.io.IOException) {
            // 服务端按计划提前断连（未读请求体 > maxSwallowSize）：结果未确认，不伪装成功，
            // 用显式的 -1 状态表示「没有拿到响应」。
            if (tolerateBrokenPipe) return HttpResult(BROKEN_PIPE_STATUS, ByteArray(0), emptyMap())
            throw ex
        } finally {
            connection.disconnect()
        }
    }

    /** 响应体上限保护：协议测试的响应都很小；防止误读整文件（416 情况）。 */
    private fun readBounded(stream: InputStream, limit: Int = 8 * 1024 * 1024): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var read = 0
        while (read < limit) {
            val count = stream.read(chunk)
            if (count < 0) break
            buffer.write(chunk, 0, count)
            read += count
        }
        return buffer.toByteArray()
    }

    private fun writeGenerated(output: OutputStream, total: Long) {
        val chunk = ByteArray(64 * 1024)
        var written = 0L
        while (written < total) {
            val count = minOf(chunk.size.toLong(), total - written).toInt()
            for (index in 0 until count) {
                chunk[index] = ((written + index) % 251).toByte()
            }
            output.write(chunk, 0, count)
            written += count
        }
    }

    private fun generatedSlice(start: Long, length: Int): ByteArray =
        ByteArray(length) { ((start + it) % 251).toByte() }

    private fun fileSlice(path: Path, start: Long, length: Long): ByteArray {
        Files.newByteChannel(path).use { channel ->
            channel.position(start)
            val buffer = java.nio.ByteBuffer.allocate(length.toInt())
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break
            }
            return buffer.array()
        }
    }

    /** 服务端存储目录 = basePath/meeting-videos；只有 UUID 会出现在这里。 */
    private fun storedFile(id: String): Path = storageRoot.resolve("meeting-videos").resolve(id)

    private fun storageFiles(): List<String> {
        val videoRoot = storageRoot.resolve("meeting-videos")
        return if (!Files.exists(videoRoot)) {
            emptyList()
        } else {
            Files.list(videoRoot).use { stream -> stream.map { it.fileName.toString() }.toList() }
        }
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}

/** 真实会话建立端点（IT 专用）：把会话用户名写进容器会话，之后所有 /api 请求只带 JSESSIONID。 */
@RestController
class ItSessionController {
    @GetMapping("/it/session")
    fun session(
        request: javax.servlet.http.HttpServletRequest,
        @RequestParam(defaultValue = "it-user") username: String
    ): Map<String, String> {
        request.getSession(true).setAttribute(AuthSessionKeys.USERNAME, username)
        return mapOf("username" to username)
    }
}
