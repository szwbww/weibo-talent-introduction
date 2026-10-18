package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.discovery.service.DiscoveryVerificationQueryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import java.time.Instant
import java.time.LocalDateTime

class DiscoveryVerificationControllerTest {
    private val repository = mock(DiscoveryVerificationRepository::class.java)
    private val admissions = mock(DiscoveryReviewRepository::class.java)
    private val auth = mock(AuthService::class.java)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val query = DiscoveryVerificationQueryService(repository, admissions, DiscoveryVerificationProperties())
    private val mvc = MockMvcBuilders.standaloneSetup(DiscoveryVerificationController(query))
        .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
        .addInterceptors(AuthInterceptor(auth, mapper)).build()
    private val base = "/api/expert-discovery/verification"
    private fun session(): MockHttpSession {
        val now = LocalDateTime.now()
        `when`(auth.findUser("fixture")).thenReturn(AdminUser(1, "fixture", "unused", false, null, now, now))
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "fixture") }
    }

    @Test fun `every endpoint requires existing authentication and never reaches reader when anonymous`() {
        listOf("/capabilities", "/summary?executionIds=1", "/executions/1/batches", "/executions/1/records", "/items/1")
            .forEach { path -> mvc.perform(get(base + path)).andExpect(status().isUnauthorized) }
        verifyNoInteractions(repository, admissions)
    }

    @Test fun `all parameter errors return 400 before SQL`() {
        val authenticated = session()
        val paths = listOf("/summary", "/summary?executionIds=", "/summary?executionIds=0", "/summary?executionIds=-1",
            "/summary?executionIds=1,,2", "/summary?executionIds=9223372036854775808", "/summary?executionIds=" + (1..51).joinToString(","),
            "/executions/0/records", "/executions/x/records", "/items/0", "/items/x", "/executions/1/batches?page=-1",
            "/executions/1/batches?size=101", "/executions/1/records?size=0", "/executions/1/records?page=x",
            "/executions/1/records?route=NONE", "/executions/1/records?result=UNKNOWN", "/executions/1/records?batchNumber=0",
            "/executions/1/records?q=" + "x".repeat(101))
        paths.forEach { path -> mvc.perform(get(base + path).session(authenticated)).andExpect(status().isBadRequest) }
        verifyNoInteractions(repository, admissions)
    }

    @Test fun `missing item is 404 and storage failures on every data GET are 503 not fake zero`() {
        val authenticated = session()
        mvc.perform(get("$base/items/12").session(authenticated)).andExpect(status().isNotFound)
        `when`(repository.findItem(13)).thenThrow(DataAccessResourceFailureException("SECRET database"))
        `when`(repository.findRun(1)).thenThrow(DataAccessResourceFailureException("SECRET database"))
        `when`(repository.reportingRuns(listOf(1L))).thenThrow(DataAccessResourceFailureException("SECRET database"))
        listOf("/summary?executionIds=1", "/executions/1/batches", "/executions/1/records", "/items/13").forEach { path ->
            val result = mvc.perform(get(base + path).session(authenticated)).andExpect(status().isServiceUnavailable).andReturn()
            assertFalse(result.response.contentAsString.contains("SECRET"))
        }
        `when`(repository.findItem(14)).thenThrow(IllegalArgumentException("Invalid persisted state SECRET"))
        mvc.perform(get("$base/items/14").session(authenticated)).andExpect(status().isServiceUnavailable)
        verifyNoInteractions(admissions)
    }

    @Test fun `authenticated endpoints retain null legacy and recorded empty zero semantics`() {
        val authenticated = session()
        val now = Instant.parse("2026-10-18T10:00:00Z")
        val run = VerificationRun(3, VerificationMode.ENFORCE, VerificationMode.ENFORCE.policyVersion, false, "{}", true, 9, now, now)
        `when`(repository.reportingRuns(listOf(2L, 3L))).thenReturn(mapOf(3L to run))
        `when`(repository.reportingCollectionStatuses(listOf(2L, 3L))).thenReturn(mapOf(2L to "SUCCESS", 3L to "SUCCESS"))
        `when`(repository.reportingCounts(VerificationReportFilter(2), executionIds = listOf(2L, 3L))).thenReturn(emptyMap())
        `when`(repository.reportingSuggestions(VerificationReportFilter(2), executionIds = listOf(2L, 3L))).thenReturn(emptyMap())
        mvc.perform(get("$base/summary?executionIds=2,3").session(authenticated)).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].coverage").value("NOT_RECORDED")).andExpect(jsonPath("$[0].counts").isEmpty)
            .andExpect(jsonPath("$[1].coverage").value("RECORDED")).andExpect(jsonPath("$[1].counts.profiles").value(0))
            .andExpect(jsonPath("$[1].snapshotVersion").value(9))
        mvc.perform(get("$base/executions/2/batches").session(authenticated)).andExpect(status().isOk)
            .andExpect(jsonPath("$.runCounts").isEmpty).andExpect(jsonPath("$.coverage").value("NOT_RECORDED"))
        mvc.perform(get("$base/executions/2/records").session(authenticated)).andExpect(status().isOk)
            .andExpect(jsonPath("$.counts").isEmpty).andExpect(jsonPath("$.total").isEmpty)
        mvc.perform(get("$base/capabilities").session(authenticated)).andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.ready").value(false))
        verifyNoInteractions(admissions)
    }
}
