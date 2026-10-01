package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.ContactCountryEntry
import com.weibo.talentintroduction.mail.service.ContactCountryTimezoneCatalog
import com.weibo.talentintroduction.mail.service.ContactLocationView
import com.weibo.talentintroduction.mail.service.ContactTimezoneEntry
import com.weibo.talentintroduction.mail.service.ExpertContactLocationCatalog
import com.weibo.talentintroduction.mail.service.ExpertContactLocationService
import com.weibo.talentintroduction.mail.service.SaveContactLocationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 01（c1）人工所在地接口契约测试（@WebMvcTest，service/catalog 为 mock）。
 *
 * 只断言接口层事实：真实 URL、冻结的 JSON 字段集合与取值、401/400/404 状态码与错误体、
 * Session 身份（body 中的 username/operatorName 必须被忽略）。真实 SQL、迁移与外键由
 * [com.weibo.talentintroduction.mail.service.ExpertContactLocationServiceIT]（mysqlIt 门禁）承担。
 */
@WebMvcTest(controllers = [ExpertContactLocationController::class])
@Import(AuthWebConfig::class, ExpertContactLocationControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class ExpertContactLocationControllerTest {

    /**
     * 本 slice 需要解析 Kotlin data class @RequestBody；测试侧显式提供带 KotlinModule 的
     * primary ObjectMapper（与 MeetingCalendarControllerTest 同款手法；生产 Boot 默认同为
     * FAIL_ON_UNKNOWN_PROPERTIES=false）。
     */
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var service: ExpertContactLocationService

    @MockBean
    private lateinit var catalog: ExpertContactLocationCatalog

    @MockBean
    private lateinit var authService: AuthService

    private fun session(username: String = "admin"): MockHttpSession {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, username)
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(
                id = 1,
                username = username,
                passwordHash = "hash",
                mustChangePassword = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        return session
    }

    private fun unconfigured(): ContactLocationView = ContactLocationView(
        contactId = 42L,
        configured = false,
        countryCode = null,
        countryLabel = null,
        zoneId = null,
        effectiveZoneId = null,
        zoneLabel = null,
        usingDefaultZone = false
    )

    private fun configured(): ContactLocationView = ContactLocationView(
        contactId = 42L,
        configured = true,
        countryCode = "BR",
        countryLabel = "巴西",
        zoneId = "America/Manaus",
        effectiveZoneId = "America/Manaus",
        zoneLabel = "巴西 · 马瑙斯",
        usingDefaultZone = false
    )

    // ------------------------------------------------------------------
    // 目录
    // ------------------------------------------------------------------

    @Test
    fun `countries endpoint serves the frozen catalog without extra serialized fields`() {
        Mockito.`when`(catalog.all()).thenReturn(
            ContactCountryTimezoneCatalog(
                sourceVersion = "2026c",
                sourceUrl = "https://data.iana.org/time-zones/tzdb/zone.tab",
                defaultPolicy = "preview-28-overrides-otherwise-first-zone-tab-row",
                countries = listOf(
                    ContactCountryEntry(
                        code = "AD",
                        labelZh = "安道尔",
                        defaultZoneId = "Europe/Andorra",
                        zones = listOf(ContactTimezoneEntry("Europe/Andorra", "安道尔"))
                    )
                )
            )
        )

        mvc.perform(get("/api/mail/contact-locations/countries").session(session()))
            .andExpect(status().isOk)
            .andExpect(content().json(
                """
                {
                  "sourceVersion": "2026c",
                  "sourceUrl": "https://data.iana.org/time-zones/tzdb/zone.tab",
                  "defaultPolicy": "preview-28-overrides-otherwise-first-zone-tab-row",
                  "countries": [
                    {
                      "code": "AD",
                      "labelZh": "安道尔",
                      "defaultZoneId": "Europe/Andorra",
                      "zones": [{"id": "Europe/Andorra", "labelZh": "安道尔"}]
                    }
                  ]
                }
                """.trimIndent(),
                true
            ))
    }

    // ------------------------------------------------------------------
    // 读配置
    // ------------------------------------------------------------------

    @Test
    fun `unconfigured GET is 200 with null zone fields and no placeholder write`() {
        Mockito.`when`(service.get(42L)).thenReturn(unconfigured())

        mvc.perform(get("/api/mail/contact-locations/42").session(session()))
            .andExpect(status().isOk)
            .andExpect(content().json(
                """
                {
                  "contactId": 42,
                  "configured": false,
                  "countryCode": null,
                  "countryLabel": null,
                  "zoneId": null,
                  "effectiveZoneId": null,
                  "zoneLabel": null,
                  "usingDefaultZone": false
                }
                """.trimIndent(),
                true
            ))

        verify(service).get(42L)
        assertEquals(emptyList<List<Any?>>(), saveInvocations(), "只读 GET 不得触发任何写入")
    }

    @Test
    fun `missing contact GET is 404`() {
        Mockito.`when`(service.get(404L)).thenThrow(NoSuchElementException("联系人不存在：404"))

        mvc.perform(get("/api/mail/contact-locations/404").session(session()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    // ------------------------------------------------------------------
    // 写配置
    // ------------------------------------------------------------------

    @Test
    fun `PUT persists the session identity and returns the persisted view`() {
        Mockito.`when`(service.save("admin", 42L, SaveContactLocationRequest("BR", "America/Manaus")))
            .thenReturn(configured())

        mvc.perform(
            put("/api/mail/contact-locations/42").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"BR","zoneId":"America/Manaus"}""")
        )
            .andExpect(status().isOk)
            .andExpect(content().json(
                """
                {
                  "contactId": 42,
                  "configured": true,
                  "countryCode": "BR",
                  "countryLabel": "巴西",
                  "zoneId": "America/Manaus",
                  "effectiveZoneId": "America/Manaus",
                  "zoneLabel": "巴西 · 马瑙斯",
                  "usingDefaultZone": false
                }
                """.trimIndent(),
                true
            ))

        verify(service).save("admin", 42L, SaveContactLocationRequest("BR", "America/Manaus"))
    }

    @Test
    fun `missing zoneId is bound as null and body username is ignored`() {
        Mockito.`when`(service.save("admin", 42L, SaveContactLocationRequest("BR", null)))
            .thenReturn(configured())

        mvc.perform(
            put("/api/mail/contact-locations/42").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"BR","username":"root","operatorName":"root"}""")
        ).andExpect(status().isOk)

        verify(service).save("admin", 42L, SaveContactLocationRequest("BR", null))
        assertEquals(
            listOf(listOf<Any?>("admin", 42L, SaveContactLocationRequest("BR", null))),
            saveInvocations(),
            "身份只取 Session；body 里的 username/operatorName 必须被忽略"
        )
    }

    @Test
    fun `anonymous requests are 401 and never reach the write entry`() {
        mvc.perform(get("/api/mail/contact-locations/countries")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/mail/contact-locations/42")).andExpect(status().isUnauthorized)
        mvc.perform(
            put("/api/mail/contact-locations/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"BR"}""")
        ).andExpect(status().isUnauthorized)

        verifyNoInteractions(service)
    }

    @Test
    fun `invalid country zone and body map to 400 and missing contact maps to 404`() {
        Mockito.`when`(service.save("admin", 42L, SaveContactLocationRequest("ZZ", null)))
            .thenThrow(IllegalArgumentException("未知国家代码：ZZ"))
        mvc.perform(
            put("/api/mail/contact-locations/42").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"ZZ","zoneId":null}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))

        Mockito.`when`(service.save("admin", 42L, SaveContactLocationRequest("BR", "Asia/Tokyo")))
            .thenThrow(IllegalArgumentException("国家 BR 不支持时区：Asia/Tokyo"))
        mvc.perform(
            put("/api/mail/contact-locations/42").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"BR","zoneId":"Asia/Tokyo"}""")
        ).andExpect(status().isBadRequest)

        // 缺少 countryCode 无法绑定 → 400，且不进入 service
        val before = saveInvocations().size
        mvc.perform(
            put("/api/mail/contact-locations/42").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"zoneId":"America/Manaus"}""")
        ).andExpect(status().isBadRequest)
        assertEquals(before, saveInvocations().size)

        Mockito.`when`(service.save("admin", 404L, SaveContactLocationRequest("BR", null)))
            .thenThrow(NoSuchElementException("联系人不存在：404"))
        mvc.perform(
            put("/api/mail/contact-locations/404").session(session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countryCode":"BR","zoneId":null}""")
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    /** 真实调用参数（避免对 Kotlin 非空形参使用 matcher 的 null 陷阱）。 */
    private fun saveInvocations(): List<List<Any?>> =
        Mockito.mockingDetails(service).invocations
            .filter { it.method.name == "save" }
            .map { it.arguments.toList() }
}
