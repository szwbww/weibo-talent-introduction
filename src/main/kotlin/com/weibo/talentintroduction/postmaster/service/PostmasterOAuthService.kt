package com.weibo.talentintroduction.postmaster.service

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.UserCredentials
import com.weibo.talentintroduction.config.PostmasterProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

@Service
class PostmasterOAuthService(
    private val properties: PostmasterProperties
) {
    private val log = LoggerFactory.getLogger(PostmasterOAuthService::class.java)

    fun clientConfigured(): Boolean = properties.oauthClientId.isNotBlank() &&
        properties.oauthClientSecret.isNotBlank() &&
        properties.oauthRedirectUri.isNotBlank()

    fun authorized(): Boolean = runCatching { loadCredentials() != null }.getOrDefault(false)

    fun authorizationUrl(state: String): String {
        require(state.isNotBlank()) { "OAuth state must not be blank" }
        check(clientConfigured()) { "Postmaster OAuth client is not configured" }

        val authorizationUrl = authorizationFlow()
            .newAuthorizationUrl()
            .setRedirectUri(properties.oauthRedirectUri)
            .setAccessType("offline")
        authorizationUrl.set("prompt", "consent")
        return authorizationUrl.setState(state).build()
    }

    fun exchangeCode(code: String): GoogleCredentials {
        require(code.isNotBlank()) { "OAuth authorization code must not be blank" }
        check(clientConfigured()) { "Postmaster OAuth client is not configured" }

        val token = authorizationFlow()
            .newTokenRequest(code)
            .setRedirectUri(properties.oauthRedirectUri)
            .execute()
        val refreshToken = token.refreshToken ?: loadUserCredentials()?.refreshToken
            ?: error("Google did not return a refresh token")
        val credentials = UserCredentials.newBuilder()
            .setClientId(properties.oauthClientId)
            .setClientSecret(properties.oauthClientSecret)
            .setRefreshToken(refreshToken)
            .build()
        saveCredentials(credentials)
        return credentials.createScoped(listOf(GoogleDomainStatsFetcher.REQUIRED_SCOPE))
    }

    fun loadCredentials(): GoogleCredentials? {
        return loadUserCredentials()?.createScoped(listOf(GoogleDomainStatsFetcher.REQUIRED_SCOPE))
    }

    private fun loadUserCredentials(): UserCredentials? {
        val path = tokenPath()
        if (!Files.isRegularFile(path)) {
            return null
        }
        return Files.newInputStream(path).use { stream ->
            UserCredentials.fromStream(stream)
        }
    }

    private fun authorizationFlow(): GoogleAuthorizationCodeFlow =
        GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            properties.oauthClientId,
            properties.oauthClientSecret,
            listOf(GoogleDomainStatsFetcher.REQUIRED_SCOPE)
        )
            .setAccessType("offline")
            .build()

    private fun saveCredentials(credentials: UserCredentials) {
        val path = tokenPath()
        path.parent?.let { Files.createDirectories(it) }
        credentials.save(path.toString())
        restrictFilePermissions(path)
        log.info("Postmaster OAuth credentials saved to {}", path)
    }

    private fun tokenPath(): Path = Paths.get(properties.oauthTokenFile)

    private fun restrictFilePermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        }.onFailure { error ->
            log.warn("Could not restrict Postmaster OAuth token file permissions for {}: {}", path, error.message)
        }
    }

    companion object {
        internal fun sameState(expected: String?, actual: String?): Boolean {
            if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
            return MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                actual.toByteArray(Charsets.UTF_8)
            )
        }
    }
}
