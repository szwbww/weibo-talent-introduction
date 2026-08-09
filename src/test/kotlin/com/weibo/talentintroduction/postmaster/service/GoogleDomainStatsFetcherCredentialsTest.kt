package com.weibo.talentintroduction.postmaster.service

import com.google.api.services.gmailpostmastertools.v2.PostmasterToolsScopes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class GoogleDomainStatsFetcherCredentialsTest {

    private val serviceAccountJson = """
        {"type":"service_account","project_id":"demo","client_email":"a@b.iam.gserviceaccount.com"}
    """.trimIndent()

    @Test
    fun `inline json is read directly without touching the filesystem`() {
        val stream = GoogleDomainStatsFetcher.openCredentialsStream(serviceAccountJson)

        val content = stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        assertEquals(serviceAccountJson, content)
    }

    @Test
    fun `inline json surrounded by whitespace or newlines is still detected`() {
        // 环境变量里粘贴多行 JSON 时常常带首尾换行
        val padded = "\n  $serviceAccountJson  \n"

        val content = GoogleDomainStatsFetcher.openCredentialsStream(padded)
            .use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertEquals(serviceAccountJson, content)
    }

    @Test
    fun `a filesystem path is still supported for backward compatibility`(@TempDir dir: Path) {
        val file = dir.resolve("sa.json")
        Files.write(file, serviceAccountJson.toByteArray(StandardCharsets.UTF_8))

        val content = GoogleDomainStatsFetcher.openCredentialsStream(file.toString())
            .use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertEquals(serviceAccountJson, content)
    }

    @Test
    fun `a non-json value that is not an existing file fails loudly`() {
        assertThrows(FileNotFoundException::class.java) {
            GoogleDomainStatsFetcher.openCredentialsStream("/no/such/credentials.json")
        }
    }

    @Test
    fun `required scope is a scope that actually exists in the v2 api`() {
        // v1 时代的 postmaster.readonly 在 v2 已被移除，误用会导致鉴权失败后被静默吞掉
        assertEquals(PostmasterToolsScopes.POSTMASTER_TRAFFIC_READONLY, GoogleDomainStatsFetcher.REQUIRED_SCOPE)
        assertTrue(GoogleDomainStatsFetcher.REQUIRED_SCOPE.endsWith("/auth/postmaster.traffic.readonly"))
        assertTrue(GoogleDomainStatsFetcher.REQUIRED_SCOPE != "https://www.googleapis.com/auth/postmaster.readonly")
    }
}
