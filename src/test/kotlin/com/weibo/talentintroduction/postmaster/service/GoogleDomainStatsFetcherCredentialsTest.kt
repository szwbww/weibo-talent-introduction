package com.weibo.talentintroduction.postmaster.service

import com.google.api.services.gmailpostmastertools.v2.PostmasterToolsScopes
import com.google.auth.oauth2.UserCredentials
import com.weibo.talentintroduction.config.PostmasterProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GoogleDomainStatsFetcherCredentialsTest {

    @Test
    fun `loads authorized user credentials from the OAuth token file`(@TempDir dir: Path) {
        val tokenFile = dir.resolve("postmaster-oauth-token.json")
        UserCredentials.newBuilder()
            .setClientId("client-id")
            .setClientSecret("client-secret")
            .setRefreshToken("refresh-token")
            .build()
            .save(tokenFile.toString())

        val service = PostmasterOAuthService(
            PostmasterProperties(
                oauthClientId = "client-id",
                oauthClientSecret = "client-secret",
                oauthRedirectUri = "https://example.com/callback",
                oauthTokenFile = tokenFile.toString()
            )
        )

        val credentials = service.loadCredentials()

        assertNotNull(credentials)
        assertEquals(GoogleDomainStatsFetcher.REQUIRED_SCOPE, PostmasterToolsScopes.POSTMASTER_TRAFFIC_READONLY)
        assertTrue(service.authorized())
    }

    @Test
    fun `reports unauthorized when the OAuth token file is absent`(@TempDir dir: Path) {
        val service = PostmasterOAuthService(
            PostmasterProperties(
                oauthClientId = "client-id",
                oauthClientSecret = "client-secret",
                oauthRedirectUri = "https://example.com/callback",
                oauthTokenFile = dir.resolve("missing.json").toString()
            )
        )

        assertEquals(null, service.loadCredentials())
        assertTrue(!service.authorized())
    }

    @Test
    fun `required scope is a scope that actually exists in the v2 api`() {
        // v1 时代的 postmaster.readonly 在 v2 已被移除，误用会导致鉴权失败后被静默吞掉
        assertEquals(PostmasterToolsScopes.POSTMASTER_TRAFFIC_READONLY, GoogleDomainStatsFetcher.REQUIRED_SCOPE)
        assertTrue(GoogleDomainStatsFetcher.REQUIRED_SCOPE.endsWith("/auth/postmaster.traffic.readonly"))
        assertTrue(GoogleDomainStatsFetcher.REQUIRED_SCOPE != "https://www.googleapis.com/auth/postmaster.readonly")
    }
}
