package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PostmasterOAuthConfigurationTest {

    @Test
    fun `oauth settings default to blank values and a private token path`() {
        val properties = PostmasterProperties()
        val type = properties.javaClass

        val clientIdMethod = type.methods.firstOrNull { it.name == "getOauthClientId" }
        val clientSecretMethod = type.methods.firstOrNull { it.name == "getOauthClientSecret" }
        val redirectUriMethod = type.methods.firstOrNull { it.name == "getOauthRedirectUri" }
        val tokenFileMethod = type.methods.firstOrNull { it.name == "getOauthTokenFile" }

        assertNotNull(clientIdMethod, "oauthClientId property is required")
        assertNotNull(clientSecretMethod, "oauthClientSecret property is required")
        assertNotNull(redirectUriMethod, "oauthRedirectUri property is required")
        assertNotNull(tokenFileMethod, "oauthTokenFile property is required")

        val clientId = clientIdMethod!!.invoke(properties) as String
        val clientSecret = clientSecretMethod!!.invoke(properties) as String
        val redirectUri = redirectUriMethod!!.invoke(properties) as String
        val tokenFile = tokenFileMethod!!.invoke(properties) as String

        assertEquals("", clientId)
        assertEquals("", clientSecret)
        assertEquals("", redirectUri)
        assertNotNull(tokenFile)
        assertEquals("/etc/talent/secrets/postmaster-oauth-token.json", tokenFile)
    }
}
