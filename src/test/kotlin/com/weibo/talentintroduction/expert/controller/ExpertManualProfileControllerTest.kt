package com.weibo.talentintroduction.expert.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileDto
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileFields
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileGroup
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfilePatch
import com.weibo.talentintroduction.expert.domain.ExpertProfileConflictException
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.domain.ExpertProfileStoreException
import com.weibo.talentintroduction.expert.service.ExpertManualProfileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * 01：鉴权、API 契约与局部错误映射（400/404/409/503/500 + 401）。
 */
@WebMvcTest(controllers = [ExpertManualProfileController::class])
@Import(AuthWebConfig::class, ExpertManualProfileControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class ExpertManualProfileControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired private lateinit var mvc: MockMvc
    @MockBean private lateinit var service: ExpertManualProfileService
    @MockBean private lateinit var jdbc: org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
    @MockBean private lateinit var authService: AuthService

    private val jackson = ObjectMapper()

    private fun session(username: String = "admin", mustChangePassword: Boolean = false): MockHttpSession {
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(id = 1, username = username, passwordHash = "hash",
                mustChangePassword = mustChangePassword, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        )
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }
    }

    private val fullDto = ExpertManualProfileDto(
        expertId = "0000-0001",
        version = 3,
        identity = ExpertManualProfileDto.Identity("G0007", "测试译名", "1980", "TEST001234", "法国"),
        education = ExpertManualProfileDto.Education(
            "测试大学甲\n测试大学乙", "QS 100 / 学科前列", "测试历史单位", "世界500强",
            "测试博士后单位", "2018–2020", "副教授", "主任\n学会委员"
        ),
        technology = ExpertManualProfileDto.Technology("新能源", "储能材料\n测试技术", "QM/BS/工程师/RJ"),
        matching = ExpertManualProfileDto.Matching("STRONG", "MATCHED", listOf("测试企业甲", "测试企业乙")),
        updatedBy = "admin",
        updatedAt = "2026-10-11T10:18:00.123+08:00"
    )

    private val fullJson = """
        {"expertId":"0000-0001","version":3,
         "identity":{"talentCode":"G0007","chineseName":"测试译名","birthDateText":"1980",
                     "documentNumber":"TEST001234","sourceCountry":"法国"},
         "education":{"graduatedSchool":"测试大学甲\n测试大学乙","schoolRanking":"QS 100 / 学科前列",
                      "preChinaOrganization":"测试历史单位","organizationRanking":"世界500强",
                      "postdocOrganization":"测试博士后单位","postdocPeriod":"2018–2020",
                      "professionalTitle":"副教授","administrativeTitles":"主任\n学会委员"},
         "technology":{"industry":"新能源","technicalExpertise":"储能材料\n测试技术",
                       "programCategories":"QM/BS/工程师/RJ"},
         "matching":{"researchAbility":"STRONG","companyMatchStatus":"MATCHED",
                     "matchedCompanies":["测试企业甲","测试企业乙"]},
         "updatedBy":"admin","updatedAt":"2026-10-11T10:18:00.123+08:00"}
    """.trimIndent()

    private fun valuesOf(group: ExpertManualProfileGroup): Map<String, Any?> =
        group.fields.associate { it.apiName to null }

    private fun identityBody(extra: String = ""): String =
        """{"expertId":"0000-0001","level":"RAW","group":"identity","expectedVersion":2,
            "values":{"talentCode":"G0007","chineseName":null,"birthDateText":null,
                      "documentNumber":null,"sourceCountry":null}$extra}"""

    private fun identityCommand(expectedVersion: Long = 2) = ExpertManualProfilePatch(
        expertId = "0000-0001",
        level = ExpertIndexLevel.RAW,
        group = ExpertManualProfileGroup.IDENTITY,
        expectedVersion = expectedVersion,
        values = mapOf("talentCode" to "G0007", "chineseName" to null, "birthDateText" to null,
            "documentNumber" to null, "sourceCountry" to null)
    )

    @Test
    fun `GET returns the complete nineteen field contract and passes expertId and level through`() {
        Mockito.`when`(service.get("0000-0001", ExpertIndexLevel.CANDIDATE)).thenReturn(fullDto)
        val body = mvc.perform(
            get("/api/experts/manual-profile").param("expertId", "0000-0001").param("level", "CANDIDATE")
                .session(session())
        ).andExpect(status().isOk).andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        assertEquals(jackson.readTree(fullJson), jackson.readTree(body))
        Mockito.verify(service).get("0000-0001", ExpertIndexLevel.CANDIDATE)

        // 分组键集必须与 01 白名单逐字一致：不混入读不到的 ES 字段，也不缺任何一项。
        val tree: JsonNode = jackson.readTree(body)
        val actualByGroup = ExpertManualProfileGroup.values().associateWith { group ->
            tree.get(group.wire).fieldNames().asSequence().toList().toSet()
        }
        ExpertManualProfileGroup.values().forEach { group ->
            assertEquals(group.fields.map { it.apiName }.toSet(), actualByGroup.getValue(group), group.wire)
        }
        assertEquals(
            ExpertManualProfileFields.ALL.map { it.apiName }.toSet(),
            actualByGroup.values.flatten().toSet()
        )
        assertEquals(19, actualByGroup.values.flatten().size)
        assertEquals(
            setOf("expertId", "version", "identity", "education", "technology", "matching", "updatedBy", "updatedAt"),
            tree.fieldNames().asSequence().toSet()
        )
    }

    @Test
    fun `GET defaults the level to RAW validates unknown levels and forwards blank expert ids`() {
        Mockito.`when`(service.get("0000-0001", ExpertIndexLevel.RAW)).thenReturn(fullDto)
        mvc.perform(get("/api/experts/manual-profile").param("expertId", "0000-0001").session(session()))
            .andExpect(status().isOk).andExpect(content().json(fullJson, true))
        Mockito.verify(service).get("0000-0001", ExpertIndexLevel.RAW)

        mvc.perform(get("/api/experts/manual-profile").param("expertId", "0000-0001").param("level", "INVALID").session(session()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))

        Mockito.`when`(service.get(null, ExpertIndexLevel.RAW))
            .thenThrow(ExpertManualProfileInvalidRequestException("缺少专家标识，无法保存"))
        mvc.perform(get("/api/experts/manual-profile").session(session()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("缺少专家标识，无法保存"))
    }

    @Test
    fun `PATCH success returns the latest DTO and forwards the parsed command with session identity`() {
        Mockito.`when`(service.patch(identityCommand(), "userA")).thenReturn(fullDto)
        mvc.perform(
            patch("/api/experts/manual-profile").session(session("userA"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(identityBody(extra = ""","updatedBy":"root","updatedAt":"2026-01-01T00:00:00.000+08:00","version":99"""))
        ).andExpect(status().isOk).andExpect(content().json(fullJson, true))
        Mockito.verify(service).patch(identityCommand(), "userA")

        // level 省略时默认 RAW，group 只接受 01 的四个 wire 名。
        val educationCommand = ExpertManualProfilePatch(
            expertId = "0000-0001",
            level = ExpertIndexLevel.RAW,
            group = ExpertManualProfileGroup.EDUCATION,
            expectedVersion = 2,
            values = valuesOf(ExpertManualProfileGroup.EDUCATION)
        )
        Mockito.`when`(service.patch(educationCommand, "admin")).thenReturn(fullDto)
        mvc.perform(
            patch("/api/experts/manual-profile").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertId":"0000-0001","group":"education","expectedVersion":2,
                    "values":{"graduatedSchool":null,"schoolRanking":null,"preChinaOrganization":null,
                    "organizationRanking":null,"postdocOrganization":null,"postdocPeriod":null,
                    "professionalTitle":null,"administrativeTitles":null}}""")
        ).andExpect(status().isOk).andExpect(content().json(fullJson, true))
        Mockito.verify(service).patch(educationCommand, "admin")
    }

    @Test
    fun `PATCH malformed bodies and unknown groups map to 400 without service calls`() {
        val malformed = listOf(
            "",
            "{}",
            """{"expertId":"0000-0001","expectedVersion":0,"values":{}}""",
            """{"expertId":"0000-0001","group":"unknown","expectedVersion":0,"values":{}}""",
            """{"expertId":"0000-0001","group":"identity","values":{}}""",
            """{"expertId":"0000-0001","group":"identity","expectedVersion":0,"values":[]}""",
            """{"expertId":"0000-0001","group":"identity","expectedVersion":0,"values":"all"}""",
            """{"expertId":"0000-0001","group":"identity","expectedVersion":0,"values":{"talentCode":"G0007","chineseName":null,"birthDateText":null,"documentNumber":null,"sourceCountry":null},"level":"LEVEL"}"""
        )
        malformed.forEach { body ->
            mvc.perform(
                patch("/api/experts/manual-profile").session(session())
                    .contentType(MediaType.APPLICATION_JSON).content(body)
            ).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `business failures map to their fixed codes and never leak internals`() {
        Mockito.`when`(service.patch(identityCommand(), "admin"))
            .thenThrow(ExpertManualProfileInvalidRequestException("字段 documentNumber 超过长度上限 100"))
        mvc.perform(patch("/api/experts/manual-profile").session(session()).contentType(MediaType.APPLICATION_JSON).content(identityBody()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("字段 documentNumber 超过长度上限 100"))

        listOf(
            Triple(ExpertProfileNotFoundException("专家不存在"), 404, "EXPERT_NOT_FOUND"),
            Triple(ExpertProfileConflictException("资料已被更新，请重新加载后再编辑"), 409, "PROFILE_CONFLICT"),
            Triple(ExpertProfileLookupUnavailableException("专家身份查询服务不可用，请稍后重试"), 503, "EXPERT_LOOKUP_UNAVAILABLE"),
            Triple(ExpertProfileStoreException("人工档案保存失败，请稍后重试"), 500, "PROFILE_STORE_FAILED")
        ).forEach { (failure, statusCode, code) ->
            Mockito.reset(service)
            Mockito.`when`(service.patch(identityCommand(), "admin")).thenThrow(failure)
            mvc.perform(
                patch("/api/experts/manual-profile").session(session())
                    .contentType(MediaType.APPLICATION_JSON).content(identityBody())
            ).andExpect(status().`is`(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(failure.message))
                .andExpect(jsonPath("$.detail").value(org.springframework.http.HttpStatus.valueOf(statusCode).reasonPhrase))
        }
        Mockito.verify(service, Mockito.atLeast(1)).patch(identityCommand(), "admin")

        // 未预期异常：固定 500 消息，内部 SQL/路径与字段值不外泄。
        Mockito.reset(service)
        Mockito.`when`(service.patch(identityCommand(), "admin")).thenThrow(
            RuntimeException("jdbc:mysql://host/expert_manual_profile SELECT * FROM expert_manual_profile failed")
        )
        val leaked = mvc.perform(
            patch("/api/experts/manual-profile").session(session())
                .contentType(MediaType.APPLICATION_JSON).content(identityBody())
        ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("PROFILE_STORE_FAILED"))
            .andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        assertFalse(leaked.contains("SELECT"))
        assertFalse(leaked.contains("jdbc"))
        assertFalse(leaked.contains("expert_manual_profile"))
    }

    @Test
    fun `feedback says conflict without exposing stored values`() {
        Mockito.`when`(service.patch(identityCommand(), "admin"))
            .thenThrow(ExpertProfileConflictException("资料已被更新，请重新加载后再编辑"))
        val body = mvc.perform(
            patch("/api/experts/manual-profile").session(session())
                .contentType(MediaType.APPLICATION_JSON).content(identityBody())
        ).andExpect(status().isConflict).andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        assertEquals("资料已被更新，请重新加载后再编辑", jackson.readTree(body).get("message").asText())
        assertFalse(body.contains("TEST001234"))
    }

    @Test
    fun `no session expired session and forced password change preserve auth rules for both routes`() {
        val expired = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "deleted-user") }
        mvc.perform(get("/api/experts/manual-profile").param("expertId", "0000-0001"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mvc.perform(
            patch("/api/experts/manual-profile").contentType(MediaType.APPLICATION_JSON).content(identityBody())
        ).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mvc.perform(get("/api/experts/manual-profile").param("expertId", "0000-0001").session(expired))
            .andExpect(status().isUnauthorized)
        mvc.perform(
            patch("/api/experts/manual-profile").session(expired)
                .contentType(MediaType.APPLICATION_JSON).content(identityBody())
        ).andExpect(status().isUnauthorized)
        val mustChange = session("password-user", mustChangePassword = true)
        mvc.perform(get("/api/experts/manual-profile").param("expertId", "0000-0001").session(mustChange))
            .andExpect(status().isForbidden)
        mvc.perform(
            patch("/api/experts/manual-profile").session(mustChange)
                .contentType(MediaType.APPLICATION_JSON).content(identityBody())
        ).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `controller independently refuses absent and blank session usernames`() {
        val controller = ExpertManualProfileController(service, jdbc)
        listOf<String?>(null, " ", "\t").forEach { username ->
            val request = MockHttpServletRequest()
            if (username != null) request.getSession().setAttribute(AuthSessionKeys.USERNAME, username)
            assertEquals(401, controller.get(request, "0000-0001", ExpertIndexLevel.RAW).statusCodeValue)
            assertEquals(401, controller.patch(request, ExpertManualProfilePatchRequest()).statusCodeValue)
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `error responses always carry code message and detail`() {
        Mockito.`when`(service.get("missing", ExpertIndexLevel.RAW))
            .thenThrow(ExpertProfileNotFoundException("专家不存在"))
        val body = mvc.perform(
            get("/api/experts/manual-profile").param("expertId", "missing").session(session())
        ).andExpect(status().isNotFound).andReturn().response.getContentAsString(StandardCharsets.UTF_8)
        val tree = jackson.readTree(body)
        assertEquals(setOf("code", "message", "detail"), tree.fieldNames().asSequence().toSet())
        assertEquals("EXPERT_NOT_FOUND", tree.get("code").asText())
        assertEquals("专家不存在", tree.get("message").asText())
        assertTrue(tree.get("detail").isTextual)
    }
}
