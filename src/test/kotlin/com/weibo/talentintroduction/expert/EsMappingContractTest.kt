package com.weibo.talentintroduction.expert

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * I-1 guard: the three mapping JSON files under src/main/resources/es are the single
 * declaration source for the expert index mappings. Kotlin must not carry a field-name
 * whitelist, and the JSONs must stay parseable and aligned with the contract the index
 * depends on.
 *
 * File-reading precedent: QaRuleManagementServiceTest reads src/main/resources via
 * relative paths (mvn test runs with the project root as working directory).
 */
class EsMappingContractTest {

    private val mapper = ObjectMapper()

    private fun mappingJson(name: String): String =
        Files.readString(Paths.get("src/main/resources/es/$name"))

    private fun propertiesOf(name: String): JsonNode =
        mapper.readTree(mappingJson(name)).path("mappings").path("properties")

    @Test
    fun `all three mapping JSONs parse`() {
        for (name in listOf(
            "orcid_info_application.json",
            "orcid_info_candidate.json",
            "orcid_info_raw.json"
        )) {
            val properties = propertiesOf(name)
            assertTrue(properties.isObject, "$name must declare a mappings.properties object")
            assertTrue(properties.size() > 0, "$name must declare at least one property")
        }
    }

    @Test
    fun `application JSON declares operatorStatus keyword`() {
        val operatorStatus = propertiesOf("orcid_info_application.json").path("operatorStatus")
        assertTrue(operatorStatus.isObject, "application mapping must declare operatorStatus")
        assertEquals("keyword", operatorStatus.path("type").asText(), "operatorStatus must be keyword")
    }

    @Test
    fun `candidate JSON declares operatorStatus keyword`() {
        val operatorStatus = propertiesOf("orcid_info_candidate.json").path("operatorStatus")
        assertTrue(operatorStatus.isObject, "candidate mapping must declare operatorStatus")
        assertEquals("keyword", operatorStatus.path("type").asText(), "operatorStatus must be keyword")
    }

    @Test
    fun `type alignments for live-index conflicts are declared`() {
        // I-2: these types match the live indexes (dynamic-template products), so the batch
        // mapping PUT does not fail on them.
        val candidate = propertiesOf("orcid_info_candidate.json")
        val raw = propertiesOf("orcid_info_raw.json")
        for (name in listOf("givenNames", "familyNames", "employment", "keyword")) {
            assertEquals(
                "keyword",
                candidate.path(name).path("type").asText(),
                "candidate $name must be keyword"
            )
            assertEquals(
                "keyword",
                raw.path(name).path("type").asText(),
                "raw $name must be keyword"
            )
        }
        val application = propertiesOf("orcid_info_application.json")
        assertEquals(
            "keyword",
            application.path("enrichedAt").path("type").asText(),
            "application enrichedAt must be keyword (existing-live-index tech debt)"
        )
    }

    @Test
    fun `expert index service source has no field-name whitelist constant`() {
        val source = Files.readString(
            Paths.get("src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt")
        )
        // A `setOf(` whose first element is a double-quoted field-name string is the old
        // whitelist shape (phase5NewFields); I-1 forbids it in the service.
        val whitelistPattern = Regex("""setOf\(\s*"[a-zA-Z]""")
        assertFalse(
            whitelistPattern.containsMatchIn(source),
            "ExpertIndexService must not contain a field-name whitelist (setOf with a quoted field name)"
        )
    }
}
