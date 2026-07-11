package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
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
        MailPlaceholderService()
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

    /** V68 post-migration keyword set for gap-fix regression fixtures. */
    private fun stubV68KeywordRules() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 24,
                    categoryId = 2,
                    keywords = "learn more,more information,name and background,objectives and scope,before sharing,understand the program,additional information,about the initiative,participating institution,participating organization,why was i selected,why did you choose me,why did you contact me,official website,program objectives,tell me more,more details,more detail,know more details,want to know more details,further information,purpose and structure,structure of the program,more about the program,know more about",
                    priority = 5,
                    replySubject = "Program overview",
                    replyBody = "Two tracks:",
                    supersedesChildren = true
                ),
                QaRule(
                    id = 18,
                    categoryId = 3,
                    keywords = "accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,who are you,real company,are you real,registered location,registered address,company registration,name of your company,your company name,full name and registered,where is your company,where are you based",
                    priority = 100,
                    replySubject = "Agency credentials",
                    replyBody = "Credentials answer"
                ),
                QaRule(
                    id = 5,
                    categoryId = 1,
                    keywords = "duty,my rights,responsibility,responsibilities,benefit,benefits,what will i get,what do i get,deliverables,my duties,expected responsibilities",
                    priority = 100,
                    replySubject = "Responsibilities and benefits",
                    replyBody = "Responsibilities answer"
                ),
                QaRule(
                    id = 23,
                    categoryId = 1,
                    keywords = "which company,partner company,company profile,is it a good match,within the scope,selected and matched,how do you match,matching process,enterprise projects",
                    priority = 100,
                    replySubject = "Partner company information",
                    replyBody = "Partner answer"
                ),
                QaRule(
                    id = 9,
                    categoryId = 1,
                    keywords = "application process,the process,procedure,timeline,next stages,next steps,what happens next,stages of the application,selection process,how are researchers selected",
                    priority = 100,
                    replySubject = "Application process",
                    replyBody = "Process answer"
                ),
                QaRule(
                    id = 33,
                    categoryId = 2,
                    keywords = "what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,provide my cv,what should i provide",
                    priority = 35,
                    replySubject = "Getting started materials",
                    replyBody = "Materials answer"
                ),
                QaRule(
                    id = 40,
                    categoryId = 1,
                    keywords = "intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the",
                    priority = 120,
                    replySubject = "Contract and IP arrangements",
                    replyBody = "After selection, you will sign a labor contract directly with the matched enterprise"
                )
            )
        )
    }

    @Test
    fun `v68 overview multi question mail hits id24 supersede without gap or id33`() {
        stubV68KeywordRules()

        val overviewMail = """
            Dear team,

            Could you provide further information regarding the purpose and structure of the program?

            Specifically:
            - What is the registered location of your company?
            - What are the expected responsibilities and deliverables?
            - How are researchers selected and matched within the scope of enterprise projects?
            - What are the intellectual property arrangements?
            - What are the next stages of the application?
            - What materials should I send?

            Best regards
        """.trimIndent()

        val suggest = service.suggestComposition(overviewMail)
        assertTrue(suggest.suggestedRuleIds.contains(24L))
        assertFalse(suggest.gapDetected)

        val rawIds = service.matchAllRuleIds(overviewMail)
        assertTrue(rawIds.contains(24L))
        assertFalse(rawIds.contains(33L))
    }

    @Test
    fun `v68 single sentence fixtures hit contract process credentials and responsibilities`() {
        stubV68KeywordRules()

        assertEquals(listOf(40L), service.matchAllRuleIds("what are the intellectual property arrangements?"))
        assertEquals(listOf(9L), service.matchAllRuleIds("the next stages of the application"))
        assertEquals(listOf(18L), service.matchAllRuleIds("registered location of your company"))
        assertEquals(listOf(5L), service.matchAllRuleIds("expected responsibilities and deliverables"))
    }

    @Test
    fun `v68 regression materials and application process still match`() {
        stubV68KeywordRules()

        assertEquals(listOf(33L), service.matchAllRuleIds("what should i send you?"))
        assertEquals(listOf(9L), service.matchAllRuleIds("what is the application process?"))
    }

    @Test
    fun `v68 provide further information hits overview not materials`() {
        stubV68KeywordRules()

        val rawIds = service.matchAllRuleIds("Could you provide further information about the program?")
        assertTrue(rawIds.contains(24L))
        assertFalse(rawIds.contains(33L))
    }
}
