package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertEmailAliasRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.campaign.service.ExpertPrimaryEmailService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException

class ExpertMailDefaultsTest {
    @Test fun `only expert values are accepted and invalid inputs never write`() {
        val jdbc = mock(JdbcTemplate::class.java)
        val service = ExpertTemplateOverrideService(jdbc, ObjectMapper(), mock(OperatorActionLogService::class.java))
        listOf(mapOf("senderEmail" to "evil@example.org"),mapOf("unsubscribeUrl" to "url"),
            mapOf("expertName" to "\${senderName}"), mapOf("expertFamilyName" to "x".repeat(8001))).forEach {
            assertThrows(IllegalArgumentException::class.java) { service.save("EMAIL-one",0,it,"admin") }
        }
        verifyNoInteractions(jdbc)
    }

    @Test fun `overrides work without ES profile and empty value is different from restore`() {
        val overrides = mock(ExpertTemplateOverrideService::class.java)
        `when`(overrides.read("EMAIL-one")).thenReturn(ExpertTemplateOverrides(2,mapOf("expertFamilyName" to "Corrected", "institution" to "")))
        val service = MailVariableService(mock(ExpertSearchService::class.java),mock(MailComposeTemplateService::class.java),templateOverrides=overrides)
        val contact = ExpertContact(id=1,campaignId=1,orcidId="EMAIL-one",expertEmail="one@example.org",expertName="One")
        val result = service.buildVariables(null,null,unsubscribeEmail=null,contact=contact)
        assertEquals("Corrected",result["expertFamilyName"])
        assertEquals("",result["institution"])
        assertEquals("",service.buildOriginalVariables(null,null,unsubscribeEmail=null,contact=contact)["expertFamilyName"])
        `when`(overrides.read("EMAIL-one")).thenReturn(ExpertTemplateOverrides(3,emptyMap()))
        assertEquals("",service.buildVariables(null,null,unsubscribeEmail=null,contact=contact)["expertFamilyName"])
    }

    @Test fun `primary switch rejects stale identity foreign alias and occupied address before updates`() {
        for (scenario in listOf("stale","foreign","occupied","review")) {
            val writes = mutableListOf<String>()
            val jdbc = mock(JdbcTemplate::class.java) { call ->
                val sql = call.arguments.firstOrNull()?.toString().orEmpty()
                when(call.method.name) {
                    "query" -> listOf("old@example.org")
                    "queryForObject" -> if (scenario=="occupied" && sql.contains("expert_contact") || scenario=="review" && sql.contains("mailbox_review")) 1L else 0L
                    "update" -> { writes.add(sql); 1 }
                    else -> null
                }
            }
            val aliases = mock(ExpertEmailAliasRepository::class.java)
            `when`(aliases.findByNormalizedEmail("new@example.org")).thenReturn(ExpertEmailAlias(expertContactId=if(scenario=="foreign") 2 else 1,email="new@example.org",normalizedEmail="new@example.org"))
            val service = ExpertPrimaryEmailService(jdbc,mock(ExpertContactRepository::class.java),aliases,mock(ExpertEmailAliasService::class.java),mock(OperatorActionLogService::class.java))
            assertThrows(RuntimeException::class.java) { service.change(1,"new@example.org",if(scenario=="stale") "stale@example.org" else "old@example.org","admin") }
            assertTrue(writes.isEmpty(),scenario)
        }
    }

    @Test fun `primary switch preserves old address and pins existing drafts before contact update`() {
        val writes=mutableListOf<String>()
        val jdbc=mock(JdbcTemplate::class.java) { call -> when(call.method.name) {
            "query" -> listOf("old@example.org")
            "queryForObject" -> 0L
            "update" -> { writes.add(call.arguments[0].toString()); 1 }
            else -> null
        } }
        val aliases=mock(ExpertEmailAliasRepository::class.java)
        `when`(aliases.findByNormalizedEmail("new@example.org")).thenReturn(ExpertEmailAlias(expertContactId=1,email="new@example.org",normalizedEmail="new@example.org"))
        val bindings=mock(ExpertEmailAliasService::class.java)
        val service=ExpertPrimaryEmailService(jdbc,mock(ExpertContactRepository::class.java),aliases,bindings,mock(OperatorActionLogService::class.java))
        assertEquals("new@example.org",service.change(1," NEW@example.org ","old@example.org","admin"))
        verify(bindings).bindAlias(1,"old@example.org","PREVIOUS_PRIMARY")
        assertTrue(writes[0].contains("mailbox_reply_draft"));assertTrue(writes[1].contains("UPDATE expert_contact"))
    }
}
