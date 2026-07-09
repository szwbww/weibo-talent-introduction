package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class QaMatchServiceTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val categoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val contentVariantRepository = Mockito.mock(ContentVariantRepository::class.java)
    private val contentVariantService = ContentVariantService(
        contentVariantRepository,
        Mockito.mock(com.weibo.talentintroduction.mail.service.MailVariableService::class.java)
    )
    private val service = QaMatchService(repository, categoryRepository, contentVariantService)

    private fun stubVariant(mainBody: String, variantContent: String) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                1L
            )
        ).thenReturn(
            listOf(
                ContentVariant(
                    id = 100L,
                    ownerType = ContentVariantOwnerType.QA_RULE,
                    ownerId = 1L,
                    variantOrder = 10,
                    content = variantContent
                )
            )
        )
    }

    @BeforeEach
    fun setUp() {
        Mockito.`when`(categoryRepository.findAll()).thenReturn(
            listOf(
                QaCategory(id = 1, categoryCode = "FUNDING_AND_TIMELINE", categoryName = "Funding", description = null, composeOrder = 30),
                QaCategory(id = 2, categoryCode = "PROGRAM_AND_ELIGIBILITY", categoryName = "Program", description = null, composeOrder = 10),
                QaCategory(id = 3, categoryCode = "COMMUNICATION_AND_OTHER", categoryName = "Communication", description = null, composeOrder = 60)
            )
        )
    }

    @Test
    fun `matches first enabled rule by keyword`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,subsidy",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match("Could you explain the salary support?")

        assertEquals(1, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
        assertEquals("Funding answer", result?.replyBody)
    }

    @Test
    fun `returns null when no keyword matches`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "deadline",
                    replySubject = "Deadline",
                    replyBody = "Deadline answer"
                )
            )
        )

        assertNull(service.match("Thank you for your email."))
    }

    @Test
    fun `matches information keyword when inbound asks for details`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 3,
                    categoryId = 2,
                    keywords = "more information",
                    replySubject = "Program overview",
                    replyBody = "Overview answer"
                )
            )
        )

        val result = service.match("I want to know more details.")

        assertEquals(3, result?.ruleId)
        assertEquals("Program overview", result?.replySubject)
    }

    @Test
    fun `prefers rule with more matched keywords before priority`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 2,
                    keywords = "project,program",
                    priority = 10,
                    replySubject = "Project",
                    replyBody = "Project answer"
                ),
                QaRule(
                    id = 2,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match("Could you explain the salary and funding support for this program?")

        assertEquals(2, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
    }

    @Test
    fun `aggregates multiple matches ordered by compose order`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 10,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 20,
                    categoryId = 3,
                    keywords = "meeting,schedule",
                    priority = 50,
                    replySubject = "Meeting arrangement",
                    replyBody = "Meeting answer"
                )
            )
        )

        val result = service.match("Please share salary and funding details and arrange a meeting.")

        assertEquals(10, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Funding answer",
                "Meeting answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result?.replyBody
        )
        assertEquals(listOf(10L, 20L), result?.matchedRuleIds)
        assertFalse(result!!.gapDetected)
    }

    @Test
    fun `handoffRequired when any matched rule requires handoff`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary",
                    replySubject = "Funding",
                    replyBody = "Funding answer",
                    handoffRequired = false
                ),
                QaRule(
                    id = 2,
                    categoryId = 3,
                    keywords = "meeting",
                    replySubject = "Meeting",
                    replyBody = "Meeting answer",
                    handoffRequired = true
                )
            )
        )

        val result = service.match("salary and meeting")

        assertTrue(result!!.handoffRequired)
    }

    @Test
    fun `autoReplyEnabled false when any matched rule disables auto reply`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary",
                    replySubject = "Funding",
                    replyBody = "Funding answer",
                    autoReplyEnabled = true
                ),
                QaRule(
                    id = 2,
                    categoryId = 3,
                    keywords = "meeting",
                    replySubject = "Meeting",
                    replyBody = "Meeting answer",
                    autoReplyEnabled = false
                )
            )
        )

        val result = service.match("salary and meeting")

        assertFalse(result!!.autoReplyEnabled)
    }

    @Test
    fun `supersedes child rules when overview composite matches`() {
        val overviewRule = QaRule(
            id = 100,
            categoryId = 2,
            keywords = "learn more,more information,understand the program",
            priority = 5,
            replySubject = "Program overview",
            replyBody = "Overview answer",
            supersedesChildren = true
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                overviewRule,
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "project,program",
                    priority = 10,
                    replySubject = "About the talent program",
                    replyBody = "Project answer"
                )
            )
        )

        val result = service.match(
            "I would like to learn more about the program before sharing my CV. What funding support is available?"
        )

        assertEquals(100, result?.ruleId)
        assertEquals("Overview answer", result?.replyBody)
        assertEquals(listOf(100L), result?.matchedRuleIds)
    }

    @Test
    fun `matchAllRuleIds returns raw hits including superseded child rules`() {
        val overviewRule = QaRule(
            id = 100,
            categoryId = 2,
            keywords = "learn more,more information,understand the program",
            priority = 5,
            replySubject = "Program overview",
            replyBody = "Overview answer",
            supersedesChildren = true
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                overviewRule,
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val body = "I would like to learn more about the program. What funding support is available?"
        val allIds = service.matchAllRuleIds(body)
        val matchResult = service.match(body)

        assertTrue(allIds.contains(100L))
        assertTrue(allIds.contains(1L))
        assertTrue(allIds.size > (matchResult?.matchedRuleIds?.size ?: 0))
    }

    @Test
    fun `child rule aggregation unchanged when overview does not match`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 10,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 20,
                    categoryId = 3,
                    keywords = "meeting,schedule",
                    priority = 50,
                    replySubject = "Meeting arrangement",
                    replyBody = "Meeting answer"
                )
            )
        )

        val result = service.match("Please share salary and funding details and arrange a meeting.")

        assertEquals(10, result?.ruleId)
        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Funding answer",
                "Meeting answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result?.replyBody
        )
        assertEquals(listOf(10L, 20L), result?.matchedRuleIds)
    }

    @Test
    fun `funding only inquiry does not match overview rule`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 100,
                    categoryId = 2,
                    keywords = "learn more,more information,understand the program",
                    priority = 5,
                    replySubject = "Program overview",
                    replyBody = "Overview answer",
                    supersedesChildren = true
                ),
                QaRule(
                    id = 8,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match("Could you explain the salary and funding support available after selection?")

        assertEquals(8, result?.ruleId)
        assertEquals("Funding answer", result?.replyBody)
    }

    @Test
    fun `detects gap when question units exceed matched categories`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match(
            "What funding is available? What is the deadline? What role would I have?"
        )

        assertTrue(result!!.gapDetected)
    }

    @Test
    fun `no gap when matched categories cover question units`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 10,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 20,
                    categoryId = 3,
                    keywords = "meeting,schedule",
                    priority = 50,
                    replySubject = "Meeting arrangement",
                    replyBody = "Meeting answer"
                )
            )
        )

        val result = service.match("What funding is available? Can we arrange a meeting?")

        assertFalse(result!!.gapDetected)
    }

    @Test
    fun `gap item includes candidate rules matching gap text keywords`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "deadline",
                    replySubject = "Deadline",
                    replyBody = "Deadline answer"
                )
            )
        )

        val result = service.suggestComposition("What funding is available? What is the deadline?")

        val fundingGap = result.gapItems.find { it.text.contains("funding", ignoreCase = true) }
        assertTrue(fundingGap!!.candidateRuleIds.contains(1L))
        assertFalse(fundingGap.candidateRuleIds.contains(2L))

        val deadlineGap = result.gapItems.find { it.text.contains("deadline", ignoreCase = true) }
        assertTrue(deadlineGap!!.candidateRuleIds.contains(2L))
        assertFalse(deadlineGap.candidateRuleIds.contains(1L))
    }

    @Test
    fun `gap item has empty candidates when no rule matches gap text`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.suggestComposition("What is the deadline?")

        assertEquals(1, result.gapItems.size)
        assertTrue(result.gapItems[0].candidateRuleIds.isEmpty())
    }

    @Test
    fun `ALL mode rule not candidate when only partial keywords match gap text`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    matchMode = "ALL",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.suggestComposition("What salary support exists?")

        assertEquals(1, result.gapItems.size)
        assertTrue(result.gapItems[0].candidateRuleIds.isEmpty())
    }

    @Test
    fun `overview supersede does not trigger gap on multi question overview mail`() {
        val overviewRule = QaRule(
            id = 100,
            categoryId = 2,
            keywords = "learn more,more information,understand the program",
            priority = 5,
            replySubject = "Program overview",
            replyBody = "Overview answer",
            supersedesChildren = true
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                overviewRule,
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                ),
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "criteria,requirements",
                    priority = 30,
                    replySubject = "Application criteria",
                    replyBody = "Criteria answer"
                )
            )
        )

        val result = service.match(
            """
            I would like to learn more about the program before sharing my CV.
            What funding support is available?
            What materials are required?
            """.trimIndent()
        )

        assertEquals(100, result?.ruleId)
        assertEquals("Overview answer", result?.replyBody)
        assertEquals(listOf(100L), result?.matchedRuleIds)
        assertFalse(result!!.gapDetected)
    }

    @Test
    fun `match resolves reply body using variant seed`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,subsidy",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )
        stubVariant("Funding answer", "Variant funding answer")

        val seed = com.weibo.talentintroduction.template.service.MailComposeTemplateService
            .variantSeedFor("orcid-test", "expert@test.com")
        val expectedIndex = Math.floorMod(seed + 1L, 2)
        val expectedBody = if (expectedIndex == 0) "Funding answer" else "Variant funding answer"

        val result = service.match("Could you explain the salary support?", variantSeed = seed)

        assertEquals(expectedBody, result?.replyBody)
    }
}
