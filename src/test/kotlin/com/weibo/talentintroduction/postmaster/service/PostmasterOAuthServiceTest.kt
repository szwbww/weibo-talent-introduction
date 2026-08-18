package com.weibo.talentintroduction.postmaster.service

import com.weibo.talentintroduction.config.PostmasterProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostmasterOAuthServiceTest {

    @Test
    fun `authorization url carries offline consent and state`() {
        val serviceType = runCatching {
            Class.forName("com.weibo.talentintroduction.postmaster.service.PostmasterOAuthService")
        }.getOrNull()
        assertNotNull(serviceType, "PostmasterOAuthService is required")

        val properties = PostmasterProperties(
            oauthClientId = "client-id.apps.googleusercontent.com",
            oauthClientSecret = "client-secret",
            oauthRedirectUri = "https://qingfei.szwbww.com/talent/api/mail-monitoring/postmaster/oauth/callback"
        )
        val constructor = serviceType!!.getConstructor(PostmasterProperties::class.java)
        val service = constructor.newInstance(properties)
        val url = serviceType.getMethod("authorizationUrl", String::class.java)
            .invoke(service, "state-123") as String

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/auth?"))
        assertTrue(url.contains("access_type=offline"))
        assertTrue(url.contains("prompt=consent"))
        assertFalse(url.contains("approval_prompt="))
        assertTrue(url.contains("state=state-123"))
        assertTrue(url.contains("postmaster.traffic.readonly"))
    }

    @Test
    fun `state comparison rejects missing or altered values`() {
        assertTrue(PostmasterOAuthService.sameState("state-123", "state-123"))
        assertFalse(PostmasterOAuthService.sameState("state-123", "state-456"))
        assertFalse(PostmasterOAuthService.sameState(null, "state-123"))
    }
}
