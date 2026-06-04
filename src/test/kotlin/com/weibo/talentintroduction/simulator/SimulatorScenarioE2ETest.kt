package com.weibo.talentintroduction.simulator

import com.weibo.talentintroduction.simulator.service.SIMULATOR_PRESETS
import com.weibo.talentintroduction.simulator.service.SIMULATOR_SCENARIOS
import com.weibo.talentintroduction.simulator.service.ScenarioStepKind
import com.weibo.talentintroduction.mail.service.InboundIntentCode
import com.weibo.talentintroduction.mail.service.AutoIntentAction
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SimulatorScenarioE2ETest {

    @Test
    fun `all presets have valid expected values`() {
        val validIntents = InboundIntentCode.values().map { it.name }.toSet()
        val validActions = AutoIntentAction.values().map { it.name }.toSet()
        val validStatuses = ConversationStatus.values().map { it.name }.toSet()

        SIMULATOR_PRESETS.forEach { preset ->
            assertTrue(validIntents.contains(preset.expectedIntent)) {
                "Preset ${preset.key}: invalid expectedIntent '${preset.expectedIntent}'"
            }
            assertTrue(validActions.contains(preset.expectedAutoAction)) {
                "Preset ${preset.key}: invalid expectedAutoAction '${preset.expectedAutoAction}'"
            }
            assertTrue(validStatuses.contains(preset.expectedNewStatus)) {
                "Preset ${preset.key}: invalid expectedNewStatus '${preset.expectedNewStatus}'"
            }
        }
    }

    @Test
    fun `all scenarios reference valid presets`() {
        val presetKeys = SIMULATOR_PRESETS.map { it.key }.toSet()
        val validIntents = InboundIntentCode.values().map { it.name }.toSet()
        val validActions = AutoIntentAction.values().map { it.name }.toSet()
        val validStatuses = ConversationStatus.values().map { it.name }.toSet()

        SIMULATOR_SCENARIOS.forEach { scenario ->
            assertTrue(scenario.key.isNotBlank()) { "Scenario key is blank" }
            val presetRefs = scenario.steps.filter { it.kind == ScenarioStepKind.INBOUND && it.presetKey != null }
            presetRefs.forEach { step ->
                assertTrue(presetKeys.contains(step.presetKey)) {
                    "Scenario ${scenario.key}: references unknown preset '${step.presetKey}'"
                }
            }
            scenario.steps.forEach { step ->
                if (step.expectedIntent != null) {
                    assertTrue(validIntents.contains(step.expectedIntent)) {
                        "Scenario ${scenario.key} step: invalid expectedIntent '${step.expectedIntent}'"
                    }
                }
                if (step.expectedAutoAction != null) {
                    assertTrue(validActions.contains(step.expectedAutoAction)) {
                        "Scenario ${scenario.key} step: invalid expectedAutoAction '${step.expectedAutoAction}'"
                    }
                }
                assertTrue(validStatuses.contains(step.expectedNewStatus)) {
                    "Scenario ${scenario.key} step: invalid expectedNewStatus '${step.expectedNewStatus}'"
                }
            }
        }
    }

    @Test
    fun `all scenarios have unique keys`() {
        val keys = SIMULATOR_SCENARIOS.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "Duplicate scenario keys found")
    }

    @Test
    fun `all presets have unique keys`() {
        val keys = SIMULATOR_PRESETS.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "Duplicate preset keys found")
    }

    @Test
    fun `scenario count matches expected`() {
        assertEquals(8, SIMULATOR_SCENARIOS.size)
    }

    @Test
    fun `preset count matches expected`() {
        assertEquals(13, SIMULATOR_PRESETS.size)
    }

    @Test
    fun `attachment scenario has valid CV_ATTACHED intent`() {
        val scenario = SIMULATOR_SCENARIOS.find { it.key == "attachment" }
            ?: fail("attachment scenario not found")
        val step = scenario.steps.first()
        assertEquals("CV_ATTACHED", step.expectedIntent)
        assertEquals("MANUAL_REVIEW", step.expectedAutoAction)
        assertEquals("MANUAL_HANDOFF", step.expectedNewStatus)
    }

    @Test
    fun `duplicate-uid scenario uses forceDuplicateOfPreviousImapUid`() {
        val scenario = SIMULATOR_SCENARIOS.find { it.key == "duplicate-uid" }
            ?: fail("duplicate-uid scenario not found")
        val secondStep = scenario.steps[1]
        assertTrue(secondStep.forceDuplicateOfPreviousImapUid)
    }
}
