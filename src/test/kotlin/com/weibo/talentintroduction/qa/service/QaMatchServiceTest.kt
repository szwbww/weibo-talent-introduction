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
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `v68 multi-request mail returns detailed rule ids not overview-only`() {
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

        // Multi-request mail: suggestComposition must return raw matches (no supersede),
        // so all matched rules are present — not just overview id24.
        val suggest = service.suggestComposition(overviewMail)
        assertTrue(suggest.suggestedRuleIds.contains(24L), "should contain id24 (overview)")
        assertTrue(suggest.suggestedRuleIds.contains(18L), "should contain id18 (registered location)")
        assertTrue(suggest.suggestedRuleIds.contains(5L), "should contain id5 (responsibilities/deliverables)")
        assertTrue(suggest.suggestedRuleIds.contains(23L), "should contain id23 (partner/within scope)")
        assertTrue(suggest.suggestedRuleIds.contains(9L), "should contain id9 (process/selection)")
        assertTrue(suggest.suggestedRuleIds.contains(40L), "should contain id40 (IP/contract)")
        // id33 does NOT match: bullet "What materials should I send?" has no keyword substring match
        assertFalse(suggest.suggestedRuleIds.contains(33L), "id33 should not match bullet text")

        // gapItems should contain an entry for each request unit (bullets + uncovered question)
        assertTrue(suggest.gapItems.isNotEmpty(), "gapItems should be non-empty")
        val researchItem = suggest.gapItems.find {
            it.text.contains("researchers selected", ignoreCase = true) ||
                it.text.contains("within the scope", ignoreCase = true)
        }
        assertNotNull(researchItem, "research-matching bullet should appear in gapItems")
        // research bullet matches id9/id23 → not a gap
        assertTrue(researchItem!!.candidateRuleIds.isNotEmpty(), "research bullet has candidate rules")

        // Materials bullet "What materials should I send?" has no keyword substring match for any rule
        // → candidateRuleIds empty → gapDetected true
        val materialItem = suggest.gapItems.find { it.text.contains("materials", ignoreCase = true) }
        assertNotNull(materialItem, "materials bullet should appear in gapItems")
        assertTrue(materialItem!!.candidateRuleIds.isEmpty(), "materials bullet has no matching rule")
        assertTrue(suggest.gapDetected, "gapDetected when any request unit has no candidate rules")

        // match() still applies supersede: only id24 returned
        val matchResult = service.match(overviewMail)
        assertEquals(listOf(24L), matchResult?.matchedRuleIds)
        assertFalse(matchResult!!.gapDetected, "match() gap should be false when supersede rule active")
    }

    @Test
    fun `suggestComposition orders request items by source offset`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(id = 1, categoryId = 1, keywords = "salary,funding", replySubject = "Funding", replyBody = "Funding answer"),
                QaRule(id = 2, categoryId = 2, keywords = "deadline", replySubject = "Deadline", replyBody = "Deadline answer")
            )
        )

        val body = """
            What is the timeline?
            - salary and funding bullet
        """.trimIndent()

        val result = service.suggestComposition(body)
        assertEquals(2, result.gapItems.size)
        assertTrue(result.gapItems[0].text.contains("timeline", ignoreCase = true),
            "question before bullet must stay first by offset")
        assertTrue(result.gapItems[1].text.contains("salary", ignoreCase = true) ||
            result.gapItems[1].text.contains("funding", ignoreCase = true),
            "bullet item should be second")
    }

    @Test
    fun `suggestComposition single request applies supersede`() {
        val overviewRule = QaRule(
            id = 100,
            categoryId = 2,
            keywords = "more information,understand the program",
            priority = 5,
            replySubject = "Program overview",
            replyBody = "Overview answer",
            supersedesChildren = true
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                overviewRule,
                QaRule(id = 1, categoryId = 1, keywords = "salary,funding", replySubject = "Funding", replyBody = "Funding answer")
            )
        )

        // Single sentence: only one request unit → supersede applied
        val result = service.suggestComposition(
            "I would like more information about the program and funding."
        )
        assertEquals(listOf(100L), result.suggestedRuleIds)
    }

    @Test
    fun `suggestComposition no question no bullet uses whole body as one unit`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(id = 1, categoryId = 1, keywords = "interested", replySubject = "Interest", replyBody = "Interest answer")
            )
        )

        val body = "Thank you for reaching out. I am interested in this opportunity."
        val result = service.suggestComposition(body)

        // No bullets, no question marks → whole body = 1 request unit → 1 gapItem
        assertEquals(1, result.gapItems.size)
        assertTrue(result.gapItems[0].candidateRuleIds.contains(1L))
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

    @Test
    fun `suggestComposition ignores Scholar and Scopus URL query question marks`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val researchQuestion =
            "Based on my research profile and areas of expertise, could you confirm whether my background fits the enterprise projects your team manages?"
        val body = """
            Thank you for your message. Here are my research profiles:
            https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
            https://www.scopus.com/authid/detail.uri?authorId=57201234567

            Based on my research profile and
            areas of expertise, could you confirm whether my background fits the
            enterprise projects your team manages?

            Specifically:
            - What is the registered location of your company?
            - What are the expected responsibilities and deliverables?
            - How are researchers selected and matched within the scope of enterprise projects?
            - What are the intellectual property arrangements?
            - What are the next stages of the application?
            - What materials should I send?

            Best regards
        """.trimIndent()

        val result = service.suggestComposition(body)
        val texts = result.gapItems.map { it.text }

        assertTrue(texts.none { it.contains("com/citations?", ignoreCase = true) }, "no Scholar URL fragment: $texts")
        assertTrue(texts.none { it.contains("detail.uri?", ignoreCase = true) }, "no Scopus URL fragment: $texts")
        assertTrue(texts.none { it.contains("authorId", ignoreCase = true) }, "no authorId fragment: $texts")
        assertTrue(
            texts.none {
                it.trim().startsWith("http://", ignoreCase = true) ||
                    it.trim().startsWith("https://", ignoreCase = true)
            },
            "URL-only lines must not become request items: $texts"
        )
        val researchItem = texts.find { it.contains("research profile", ignoreCase = true) }
        assertNotNull(researchItem, "research match question must remain")
        assertEquals(researchQuestion, researchItem!!.trim())
        assertFalse(researchItem.contains("\n"), "cross-line research question must fold soft newlines")
        assertEquals(7, result.gapItems.size, "1 research question + 6 bullets")
        assertEquals(researchQuestion, texts[0].trim(), "research question must be first by source offset")
        assertTrue(texts[1].contains("registered location", ignoreCase = true))
        assertTrue(texts[6].contains("materials", ignoreCase = true))
    }

    @Test
    fun `ordinary two-question mail shares extractor count between suggest and match gap`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "funding,salary",
                    priority = 80,
                    replySubject = "Funding",
                    replyBody = "Funding answer"
                )
            )
        )

        val body = "What funding is available? Can we arrange a meeting?"
        val suggest = service.suggestComposition(body)
        assertEquals(2, suggest.gapItems.size)

        val match = service.match(body)
        assertNotNull(match)
        // 2 request units, 1 matched category → gapDetected
        assertTrue(match!!.gapDetected)
    }

    @Test
    fun `suggestComposition keeps ordinary multi-question count and bullet with URL once`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val multi = service.suggestComposition(
            "What funding is available? Can we arrange a meeting?"
        )
        assertEquals(2, multi.gapItems.size)
        assertTrue(multi.gapItems[0].text.contains("funding", ignoreCase = true))
        assertTrue(multi.gapItems[1].text.contains("meeting", ignoreCase = true))

        val bulletWithUrl = service.suggestComposition(
            """
            - Please review https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en for my publications
            """.trimIndent()
        )
        assertEquals(1, bulletWithUrl.gapItems.size)
        assertTrue(bulletWithUrl.gapItems[0].text.contains("Please review", ignoreCase = true))
        assertTrue(bulletWithUrl.gapItems[0].text.contains("scholar.google.com", ignoreCase = true))
    }

    @Test
    fun `suggestComposition drops URL-only body and URL-only bullet`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val urlOnlyBody = service.suggestComposition(
            "https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en"
        )
        assertTrue(urlOnlyBody.gapItems.isEmpty(), "URL-only body must yield no request items")

        val urlOnlyBullet = service.suggestComposition(
            "- https://www.scopus.com/authid/detail.uri?authorId=57201234567"
        )
        assertTrue(urlOnlyBullet.gapItems.isEmpty(), "URL-only bullet must yield no request items")

        val plainFallback = service.suggestComposition(
            "Thank you for reaching out. I am interested in this opportunity."
        )
        assertEquals(1, plainFallback.gapItems.size)
        assertEquals(
            "Thank you for reaching out. I am interested in this opportunity.",
            plainFallback.gapItems[0].text
        )
    }

    @Test
    fun `match ignores URL-only bullets when detecting gap`() {
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

        val withUrlOnlyBullets = service.match(
            """
            What funding is available?
            - https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
            - https://www.scopus.com/authid/detail.uri?authorId=57201234567
            """.trimIndent()
        )
        assertNotNull(withUrlOnlyBullets)
        assertFalse(
            withUrlOnlyBullets!!.gapDetected,
            "URL-only bullets must not inflate automatic question unit count"
        )

        val withTextUrlBullet = service.match(
            """
            - salary and funding overview https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
            - deadline for the offer https://www.scopus.com/authid/detail.uri?authorId=57201234567
            """.trimIndent()
        )
        assertNotNull(withTextUrlBullet)
        assertTrue(
            withTextUrlBullet!!.gapDetected,
            "text+URL bullets still count toward automatic gap detection"
        )
    }

    // ── V75: company identity / trust split regression ─────────────────────────

    /** V75 fixture: id=18 trust-only keywords; id=41 new company identity rule. */
    private fun stubV75KeywordRules() {
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
                    keywords = "accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,who are you,real company,are you real",
                    priority = 100,
                    replySubject = "Agency credentials",
                    replyBody = "Credentials answer"
                ),
                QaRule(
                    id = 41,
                    categoryId = 3,
                    keywords = "registered location,registered address,company registration,name of your company,your company name,full name and registered,where is your company,where are you based,full legal name,legal name,full name,company name",
                    priority = 90,
                    replySubject = "Company registered identity and location",
                    replyBody = "Our full registered name is Jiangsu Qingfei Talent Technology Co., Ltd."
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
    fun `v75 full name and registered location matches company identity not credentials`() {
        stubV75KeywordRules()

        val suggest = service.suggestComposition("Please provide the full name and registered location of your company.")

        assertTrue(suggest.suggestedRuleIds.contains(41L), "should match id41 (company identity)")
        assertFalse(suggest.suggestedRuleIds.contains(18L), "should NOT match id18 (credentials)")
    }

    @Test
    fun `v77 standalone legal name matches company identity not credentials`() {
        stubV75KeywordRules()

        val suggest = service.suggestComposition("What is your full legal name?")

        assertEquals(listOf(41L), suggest.suggestedRuleIds)
        assertEquals(listOf(41L), suggest.gapItems.single().candidateRuleIds)
    }

    @Test
    fun `v75 legitimacy and verify still matches agency credentials`() {
        stubV75KeywordRules()

        val suggest = service.suggestComposition("How can I verify that your agency is legitimate?")

        assertTrue(suggest.suggestedRuleIds.contains(18L), "should match id18 (credentials)")
        assertFalse(suggest.suggestedRuleIds.contains(41L), "should NOT match id41 (company identity)")
    }

    @Test
    fun `v75 combined company identity and legitimacy matches both rules`() {
        stubV75KeywordRules()

        val mail = """
            Could you provide the registered location of your company?
            Also, how can I verify that your agency is legitimate and trusted?
        """.trimIndent()

        val suggest = service.suggestComposition(mail)

        assertTrue(suggest.suggestedRuleIds.contains(41L), "should match id41 (company identity)")
        assertTrue(suggest.suggestedRuleIds.contains(18L), "should match id18 (credentials)")
    }

    @Test
    fun `v75 multi-request overview mail matches company identity not credentials`() {
        stubV75KeywordRules()

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
        assertTrue(suggest.suggestedRuleIds.contains(24L), "should contain id24 (overview)")
        assertTrue(suggest.suggestedRuleIds.contains(41L), "should contain id41 (company identity)")
        assertFalse(suggest.suggestedRuleIds.contains(18L), "should NOT contain id18 (credentials)")
        assertTrue(suggest.suggestedRuleIds.contains(5L), "should contain id5 (responsibilities/deliverables)")
        assertTrue(suggest.suggestedRuleIds.contains(23L), "should contain id23 (partner/within scope)")
        assertTrue(suggest.suggestedRuleIds.contains(9L), "should contain id9 (process/selection)")
        assertTrue(suggest.suggestedRuleIds.contains(40L), "should contain id40 (IP/contract)")
    }
}
