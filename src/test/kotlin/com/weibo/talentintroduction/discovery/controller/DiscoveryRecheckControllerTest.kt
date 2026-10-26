package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.RecheckCounts
import com.weibo.talentintroduction.discovery.repository.RecheckTask
import com.weibo.talentintroduction.discovery.service.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(DiscoveryRecheckController::class)
@Import(AuthWebConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class DiscoveryRecheckControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @MockBean private lateinit var service: DiscoveryRecheckService
    @MockBean private lateinit var queries: DiscoveryVerificationQueryService
    @MockBean private lateinit var authService: AuthService
    private val key = UUID.randomUUID().toString()
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private fun session(): MockHttpSession {
        val now = LocalDateTime.now()
        Mockito.`when`(authService.findUser("operator")).thenReturn(AdminUser(1, "operator", "hash", false, createdAt = now, updatedAt = now))
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "operator") }
    }
    private fun view() = RecheckRunView(42, 42, key, "operator", RecheckPhase.PREPARING, VerificationMode.AUDIT,
        RecheckEvidenceMode.REUSE, 0, null, "a".repeat(64), "IDS", null, false,
        RecheckCounts(0,0,0,0,0,0,0,0,0,0), RecheckTask("RUNNING", null), Instant.EPOCH,
        "fixture", "fixture", mapper.createObjectNode(), Instant.EPOCH, Instant.EPOCH)

    @Test fun `every new endpoint rejects anonymous sessions without invoking services`() {
        listOf("/active", "/runs", "/latest?docIds=real-doc", "/42", "/42/items", "/42/items/7/steps", "/42/manifest").forEach {
            mvc.perform(get("/api/discovery/rechecks$it")).andExpect(status().isUnauthorized)
        }
        listOf("/prepare" to "{}", "/42/start" to "{}", "/42/cancel" to "{}", "/42/sync-retry" to "{}").forEach { (path, body) ->
            mvc.perform(post("/api/discovery/rechecks$path").contentType("application/json").content(body)).andExpect(status().isUnauthorized)
        }
        Mockito.verifyNoInteractions(service, queries)
    }

    @Test fun `session is sole actor and default options are audit reuse`() {
        val body = RecheckPrepareRequest(key, docIds = listOf("real-doc"))
        Mockito.`when`(service.prepare(body, "operator")).thenReturn(view())
        mvc.perform(post("/api/discovery/rechecks/prepare?actor=forged").session(session()).contentType("application/json")
            .content("""{"requestKey":"$key","docIds":["real-doc"],"actor":"forged","snapshot":{"decision":"AUTO_PASSED"}}"""))
            .andExpect(status().isAccepted).andExpect(jsonPath("$.actor").value("operator"))
            .andExpect(jsonPath("$.mode").value("AUDIT")).andExpect(jsonPath("$.evidenceMode").value("REUSE"))
            .andExpect(jsonPath("$.taskId").value(42))
        Mockito.verify(service).prepare(body, "operator")
    }

    @Test fun `active roster restoration uses only the authenticated operator`() {
        Mockito.`when`(service.active("operator")).thenReturn(listOf(view()))
        mvc.perform(get("/api/discovery/rechecks/active?actor=forged").session(session()))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].runId").value(42))
        Mockito.verify(service).active("operator")
        Mockito.verifyNoMoreInteractions(service)
    }

    @Test fun `invalid enum types and missing fields return 400`() {
        listOf("\"mode\":\"NOPE\"", "\"evidenceMode\":\"NOPE\"", "\"scope\":\"NOPE\"").forEach { field ->
            mvc.perform(post("/api/discovery/rechecks/prepare").session(session()).contentType("application/json")
                .content("""{"requestKey":"$key","docIds":["real-doc"],$field}""")).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/api/discovery/rechecks/42/items?state=INVALID").session(session())).andExpect(status().isBadRequest)
        mvc.perform(post("/api/discovery/rechecks/42/start").session(session()).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(service, queries)
    }

    @Test fun `missing cross run items and precise conflicts retain status and reason`() {
        Mockito.`when`(queries.recheckItem(42, 7)).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "RECHECK_ITEM_NOT_FOUND"))
        mvc.perform(get("/api/discovery/rechecks/42/items/7/steps").session(session()))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("RECHECK_ITEM_NOT_FOUND"))
        val start = RecheckStartRequest("a".repeat(64), 8)
        Mockito.`when`(service.start(42, start)).thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "REVISION_CONFLICT"))
        mvc.perform(post("/api/discovery/rechecks/42/start").session(session()).contentType("application/json").content(mapper.writeValueAsString(start)))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"))
    }

    @Test fun `read routes use query service and preserve explicit paging and doc identity`() {
        Mockito.`when`(queries.recheckRuns(1, 50, "real-doc")).thenReturn(RecheckPage(0, 1, 50, emptyList()))
        mvc.perform(get("/api/discovery/rechecks/runs?page=1&size=50&docId=real-doc").session(session()))
            .andExpect(status().isOk).andExpect(jsonPath("$.page").value(1))
        Mockito.`when`(queries.recheckManifest(42, 0, 100)).thenReturn(RecheckPage(0, 0, 100, emptyList()))
        mvc.perform(get("/api/discovery/rechecks/42/manifest?size=100").session(session())).andExpect(status().isOk)
        Mockito.`when`(queries.latestRechecks(listOf("real-doc"))).thenReturn(mapOf("real-doc" to view()))
        mvc.perform(get("/api/discovery/rechecks/latest?docIds=real-doc").session(session())).andExpect(status().isOk)
        Mockito.verifyNoInteractions(service)
    }
}
