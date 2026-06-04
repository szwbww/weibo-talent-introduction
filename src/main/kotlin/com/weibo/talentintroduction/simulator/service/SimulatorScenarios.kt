package com.weibo.talentintroduction.simulator.service

enum class ScenarioStepKind { INBOUND, MANUAL_API }

data class ScenarioStep(
    val kind: ScenarioStepKind,
    val presetKey: String? = null,
    val customBody: String? = null,
    val overrideFromEmail: String? = null,
    val stripIntroductionBeforeRun: Boolean = false,
    val forceDuplicateOfPreviousImapUid: Boolean = false,
    val manualApiPath: String? = null,
    val manualApiBody: Map<String, Any?>? = null,
    val expectedIntent: String? = null,
    val expectedAutoAction: String? = null,
    val expectedNewStatus: String,
    val expectMeetingScheduleCreated: Boolean = false,
    val expectHandoffPending: Boolean = false
)

data class Scenario(val key: String, val name: String, val steps: List<ScenarioStep>)

val SIMULATOR_SCENARIOS: List<Scenario> = listOf(
    Scenario("attachment", "附件触发人工", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "CV_ATTACHED",
            expectedIntent = "CV_ATTACHED", expectedAutoAction = "MANUAL_REVIEW",
            expectedNewStatus = "MANUAL_HANDOFF")
    )),
    Scenario("happy-path", "兴趣→会议", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "INTERESTED",
            expectedIntent = "INTERESTED", expectedAutoAction = "SEND_MEETING_INVITATION",
            expectedNewStatus = "MEETING_SCHEDULING"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "MEETING_TIME",
            expectedIntent = "MEETING_TIME_PROVIDED", expectedAutoAction = "MANUAL_REVIEW",
            expectedNewStatus = "MANUAL_HANDOFF", expectMeetingScheduleCreated = true)
    )),
    Scenario("qa-loop", "QA 闭环", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS", expectedNewStatus = "QA_AUTO_REPLIED"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_REMOTE", expectedNewStatus = "QA_AUTO_REPLIED"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_FUNDING", expectedNewStatus = "MANUAL_HANDOFF",
                     expectHandoffPending = true)
    )),
    Scenario("not-interested", "拒绝", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "NOT_INTERESTED", expectedNewStatus = "MANUAL_HANDOFF")
    )),
    Scenario("auto-manual-auto", "自动→人工→自动", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS", expectedNewStatus = "QA_AUTO_REPLIED"),
        ScenarioStep(ScenarioStepKind.MANUAL_API, manualApiPath = "/api/expert-contacts/{id}/switch-to-manual",
            manualApiBody = mapOf("reason" to "SCN_TEST", "note" to "switch"),
            expectedNewStatus = "MANUAL_HANDOFF"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS", expectedNewStatus = "MANUAL_HANDOFF"),
        ScenarioStep(ScenarioStepKind.MANUAL_API, manualApiPath = "/api/expert-contacts/{id}/switch-to-auto",
            manualApiBody = mapOf("note" to "switch back"),
            expectedNewStatus = "WAITING_REPLY"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS", expectedNewStatus = "QA_AUTO_REPLIED")
    )),
    Scenario("unmatched", "未匹配联系人", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, customBody = "Hello, who are you?",
            overrideFromEmail = "stranger@unknown.com",
            expectedIntent = null, expectedAutoAction = null,
            expectedNewStatus = "INTRO_SENT")
    )),
    Scenario("intro-not-sent", "INTRODUCTION 未发", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "INTERESTED",
            stripIntroductionBeforeRun = true,
            expectedNewStatus = "MANUAL_HANDOFF")
    )),
    Scenario("duplicate-uid", "重复入站", listOf(
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS", expectedNewStatus = "QA_AUTO_REPLIED"),
        ScenarioStep(ScenarioStepKind.INBOUND, presetKey = "ASK_PROCESS",
            forceDuplicateOfPreviousImapUid = true,
            expectedNewStatus = "QA_AUTO_REPLIED")
    ))
)
