package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Regression tests for QA category restructure (V38): existing match behavior must not drift (I-2).
 * Mirrors the post-migration rule set — original 12 V3 rules unchanged + 11 new rules.
 */
class QaMatchServiceRestructureTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val categoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val service = QaMatchService(repository, categoryRepository)

    @BeforeEach
    fun setUp() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(restructuredRules())
        Mockito.`when`(categoryRepository.findAll()).thenReturn(
            listOf(
                QaCategory(id = 100, categoryCode = "PROGRAM_AND_ELIGIBILITY", categoryName = "Program", description = null, composeOrder = 10),
                QaCategory(id = 101, categoryCode = "ROLE_AND_WORKSTYLE", categoryName = "Role", description = null, composeOrder = 20),
                QaCategory(id = 102, categoryCode = "FUNDING_AND_TIMELINE", categoryName = "Funding", description = null, composeOrder = 30),
                QaCategory(id = 103, categoryCode = "PROCESS_ACTIONS", categoryName = "Process", description = null, composeOrder = 40),
                QaCategory(id = 104, categoryCode = "TRUST_AND_COMPLIANCE", categoryName = "Trust", description = null, composeOrder = 50),
                QaCategory(id = 105, categoryCode = "COMMUNICATION_AND_OTHER", categoryName = "Communication", description = null, composeOrder = 60)
            )
        )
    }

    @Test
    fun `overview inquiry matches overview composite rule`() {
        val overviewRule = overviewRule()
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            restructuredRules() + overviewRule
        )
        Mockito.`when`(categoryRepository.findAll()).thenReturn(
            listOf(
                QaCategory(id = 106, categoryCode = "OVERVIEW", categoryName = "Overview", description = null, composeOrder = 0),
                QaCategory(id = 100, categoryCode = "PROGRAM_AND_ELIGIBILITY", categoryName = "Program", description = null, composeOrder = 10),
                QaCategory(id = 101, categoryCode = "ROLE_AND_WORKSTYLE", categoryName = "Role", description = null, composeOrder = 20),
                QaCategory(id = 102, categoryCode = "FUNDING_AND_TIMELINE", categoryName = "Funding", description = null, composeOrder = 30),
                QaCategory(id = 103, categoryCode = "PROCESS_ACTIONS", categoryName = "Process", description = null, composeOrder = 40),
                QaCategory(id = 104, categoryCode = "TRUST_AND_COMPLIANCE", categoryName = "Trust", description = null, composeOrder = 50),
                QaCategory(id = 105, categoryCode = "COMMUNICATION_AND_OTHER", categoryName = "Communication", description = null, composeOrder = 60)
            )
        )

        val body = """
            Dear team,
            I would like to learn more about your talent program and understand the program
            before sharing my background. Could you share more information on the objectives and scope?
        """.trimIndent()

        val result = service.match(body)

        assertEquals(24L, result?.ruleId)
        assertEquals("Program overview", result?.replySubject)
        assertEquals(overviewRule.replyBody, result?.replyBody)
    }

    @Test
    fun `funding inquiry still matches funding support rule`() {
        val result = service.match("Could you explain the salary and funding support available after selection?")

        assertEquals(8L, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
    }

    @Test
    fun `deadline inquiry still matches application deadline rule`() {
        val result = service.match("When is the submission deadline for the current application cycle?")

        assertEquals(10L, result?.ruleId)
        assertEquals("Application deadline", result?.replySubject)
    }

    @Test
    fun `criteria inquiry still matches application criteria rule`() {
        val result = service.match("What are the eligibility requirements and qualification criteria for applicants?")

        assertEquals(3L, result?.ruleId)
        assertEquals("Application criteria", result?.replySubject)
    }

    @Test
    fun `process inquiry still matches application process rule`() {
        val result = service.match("Could you walk me through the application process and overall timeline?")

        assertEquals(9L, result?.ruleId)
        assertEquals("Application process", result?.replySubject)
    }

    @Test
    fun `retired expert still matches retired rule`() {
        val result = service.match("Thank you for reaching out. I am retired and no longer working in academia.")

        assertEquals(12L, result?.ruleId)
        assertEquals("Thank you for your reply", result?.replySubject)
    }

    @Test
    fun `confirmation video inquiry matches new VCR rule`() {
        val result = service.match("Do I need to record a confirmation video and show my passport while reading the statement?")

        assertEquals(13L, result?.ruleId)
        assertEquals("Confirmation video requirement", result?.replySubject)
    }

    @Test
    fun `single application inquiry matches new commitment rule`() {
        val result = service.match("Is duplicate application allowed, or must I apply through one company only?")

        assertEquals(14L, result?.ruleId)
        assertEquals("Single application commitment", result?.replySubject)
    }

    @Test
    fun `partner company inquiry matches new company info rule`() {
        val result = service.match("Which partner company have you matched for me, and is it a good match for my field?")

        assertEquals(23L, result?.ruleId)
        assertEquals("Partner company information", result?.replySubject)
    }

    @Test
    fun `confidentiality inquiry matches new trust rule not project content`() {
        val result = service.match("Will you keep my documents confidential, and do you ever charge any fee or ask for a money transfer?")

        assertEquals(17L, result?.ruleId)
        assertEquals("Document confidentiality and no fees", result?.replySubject)
    }

    @Test
    fun `meeting inquiry matches new meeting rule`() {
        val result = service.match("Can we arrange a meeting via Zoom? I am in a different time zone but available for a call.")

        assertEquals(21L, result?.ruleId)
        assertEquals("Meeting arrangement", result?.replySubject)
    }

    @Test
    fun `new rules have planned display names`() {
        val newRules = restructuredRules().filter { it.id != null && it.id!! >= 13L }
        assertEquals(11, newRules.size)

        newRules.forEach { rule ->
            val expected = EXPECTED_NEW_RULE_DISPLAY_NAMES[rule.replySubject]
            assertFalse(rule.displayName.isNullOrBlank(), "display_name must be non-empty for ${rule.replySubject}")
            assertEquals(expected, rule.displayName, "display_name for ${rule.replySubject}")
        }
        assertEquals(EXPECTED_NEW_RULE_DISPLAY_NAMES.size, newRules.size)
    }

    @Test
    fun `generic thank you without keywords still matches nothing`() {
        assertNull(service.match("Thank you for your email. I will get back to you soon."))
    }

    private fun overviewRule(): QaRule = QaRule(
        id = 24,
        categoryId = 106,
        keywords = "learn more,more information,name and background,objectives and scope,before sharing,understand the program",
        matchMode = "ANY",
        priority = 5,
        replySubject = "Program overview",
        replyBody = "Bundled overview answer for opening inquiries.",
        displayName = "项目总览",
        supersedesChildren = true
    )

    private fun restructuredRules(): List<QaRule> = listOf(
        // Original 12 V3 seed rules — keywords/priority/reply unchanged (I-1)
        QaRule(id = 1, categoryId = 100, keywords = "project,program,scheme,what is this project", matchMode = "ANY", priority = 10, replySubject = "About the talent program", replyBody = "There are two projects: Innovative Talent Schemes and Entrepreneurial Talent Schemes. Innovative Talent Schemes are intended for individual talents who aim to join an enterprise with an exceptionally high salary. Entrepreneurial Talent Schemes are designed for talents who can convert ideas into useful products. A substantial amount of funding is allocated to this program."),
        QaRule(id = 2, categoryId = 100, keywords = "apply individually,apply jointly,team,partner", matchMode = "ANY", priority = 20, replySubject = "Application format", replyBody = "You may apply individually or jointly. You may also join the project with research partners and participate as a team to start a business in China."),
        QaRule(id = 3, categoryId = 100, keywords = "criteria,qualification,eligible,requirements", matchMode = "ANY", priority = 30, replySubject = "Application criteria", replyBody = "Applicants should hold the title of associate professor or above, have outstanding research achievements in their field, and be able to contribute to industrial services and scientific and technological innovation. Complete application materials are also required, including a passport, doctoral degree certificate, CV, proof of employment, and publication list."),
        QaRule(id = 4, categoryId = 101, keywords = "role,position,what would i do", matchMode = "ANY", priority = 40, replySubject = "Possible role", replyBody = "You may work as a researcher in a company related to your field, or you may establish your own company in cooperation with a Chinese company."),
        QaRule(id = 5, categoryId = 101, keywords = "duty,right,responsibility,benefit", matchMode = "ANY", priority = 50, replySubject = "Responsibilities and benefits", replyBody = "You may use your expertise to support a company or start a business. You may receive salary support and other coordinated assistance according to the project arrangement."),
        QaRule(id = 6, categoryId = 101, keywords = "full time,part time,remote,technical consultant", matchMode = "ANY", priority = 60, replySubject = "Full-time and part-time options", replyBody = "The applicant may negotiate with the company and work in a part-time capacity as a technical consultant, provide remote technical guidance, and visit China when necessary. Related travel expenses can be covered according to the project arrangement."),
        QaRule(id = 7, categoryId = 101, keywords = "workplace,china,relocate,come to china", matchMode = "ANY", priority = 70, replySubject = "Workplace arrangement", replyBody = "You may decide whether to come to China, and you may take up to two years to consider the commitment. It is also possible to visit China several times a year for technical exchanges, with related travel expenses covered according to the project arrangement."),
        QaRule(id = 8, categoryId = 102, keywords = "salary,subsidy,funding,compensation", matchMode = "ANY", priority = 80, replySubject = "Funding support", replyBody = "After a successful application, personal subsidies and research funding may be available. If you are willing to establish a technology company in China, further support may also be provided for start-up capital or subsequent project funding."),
        QaRule(id = 9, categoryId = 102, keywords = "process,procedure,application process,timeline", matchMode = "ANY", priority = 90, replySubject = "Application process", replyBody = "First, you submit the required materials. Then, our PhD team matches relevant enterprises according to your research direction and prepares the application documents. Finally, the materials are submitted for review. The whole process usually takes about half a year or longer."),
        QaRule(id = 10, categoryId = 102, keywords = "deadline,when to apply,submission deadline", matchMode = "ANY", priority = 100, replySubject = "Application deadline", replyBody = "We are inviting outstanding experts and scholars to participate in the current project cycle. The final submission deadline should be confirmed according to the latest project notice before application."),
        QaRule(id = 11, categoryId = 104, keywords = "why you,advantage,experience,support", matchMode = "ANY", priority = 110, replySubject = "Our support", replyBody = "We have extensive experience in supporting experts and scholars from around the world. In addition to government subsidies, we can also provide financial support for suitable entrepreneurial projects."),
        QaRule(id = 12, categoryId = 105, keywords = "retired,i am retired,no longer working", matchMode = "ANY", priority = 5, replySubject = "Thank you for your reply", replyBody = "Thank you very much for your reply. I apologize for any inconvenience this message may have caused. I hope you are enjoying a pleasant and fulfilling retirement, and I wish you all the best. If it is convenient for you, we would greatly appreciate it if you could refer this project to anyone you believe might be a suitable candidate."),
        // 11 new V38 rules
        QaRule(id = 13, categoryId = 103, keywords = "record a video,confirmation video,self-statement video,show passport,read the statement", matchMode = "ANY", priority = 120, replySubject = "Confirmation video requirement", replyBody = "To prevent AI-forged materials and duplicate applications, we need a short confirmation video (about 3–7 minutes) showing you holding your passport and reading the commitment statement. Please submit it together with your application materials.", displayName = "承诺视频 VCR"),
        QaRule(id = 14, categoryId = 103, keywords = "apply through one company,duplicate application,only apply,single agency,commitment to apply", matchMode = "ANY", priority = 120, replySubject = "Single application commitment", replyBody = "For the same project each year, you may apply through only one agency. Duplicate applications will be invalidated. Please confirm your single-application commitment.", displayName = "单一申报承诺"),
        QaRule(id = 15, categoryId = 103, keywords = "after selected,research topic,labor contract,sign contract,after selection", matchMode = "ANY", priority = 120, replySubject = "After selection process", replyBody = "After selection, you will work with the enterprise to finalize the research topic and sign a labor contract. You may also enjoy complimentary visits to China and landing support such as mobile phone setup, bank account assistance, and document submission.", displayName = "入选后流程"),
        QaRule(id = 16, categoryId = 102, keywords = "success rate,not selected,chance of success,probability of selection", matchMode = "ANY", priority = 120, replySubject = "Success rate and reapplication", replyBody = "This is a national-level project with an approximate success rate of about 10%. Competition is strong. If you are not selected in the first year, you may apply again in a subsequent cycle.", displayName = "成功率/未入选"),
        QaRule(id = 17, categoryId = 104, keywords = "confidential,keep my documents,never charge,any fee,money transfer", matchMode = "ANY", priority = 120, replySubject = "Document confidentiality and no fees", replyBody = "Your materials are kept strictly confidential and used only for application purposes. We never charge any fees throughout the entire process. Technical details you prefer not to disclose can be handled with appropriate redaction.", displayName = "资料保密·绝不收费"),
        QaRule(id = 18, categoryId = 104, keywords = "accredited,official agency,prove government,cooperation with government,authorized", matchMode = "ANY", priority = 120, replySubject = "Agency credentials and government cooperation", replyBody = "The project is confidential and does not have a public website, but our cooperation with the government is documented through talent office certificates and talent summit participation, which we can share as evidence.", displayName = "代理资质·政府合作证明"),
        QaRule(id = 19, categoryId = 104, keywords = "other agency,switch agency,guarantee selection,subsidy not paid,protect my rights", matchMode = "ANY", priority = 120, replySubject = "Multi-agency rights protection", replyBody = "When duplicate applications occur, the authorities require the agency to provide video authorization from the expert to prevent material misuse. We guarantee transparent subsidy disbursement and an open process.", displayName = "多代理·权益保障"),
        QaRule(id = 20, categoryId = 104, keywords = "sensitive project,classified,national project confidential,security concern", matchMode = "ANY", priority = 120, replySubject = "Project sensitivity concerns", replyBody = "The project is legitimate and information is kept confidential. We can build trust step by step at your pace.", displayName = "项目敏感性"),
        QaRule(id = 21, categoryId = 105, keywords = "arrange a meeting,zoom,teams,webex,time zone,available for a call", matchMode = "ANY", priority = 120, replySubject = "Meeting arrangement", replyBody = "Zoom, Teams, or Webex are all fine. We typically schedule 15–20 minutes and will arrange a time based on your time zone, sending the meeting link before the call.", displayName = "会议安排"),
        QaRule(id = 22, categoryId = 105, keywords = "only email,not on linkedin,no social media,contact me by email", matchMode = "ANY", priority = 120, replySubject = "Email-only communication preference", replyBody = "Understood. We will contact you via email only going forward.", displayName = "只邮件·不用LinkedIn"),
        QaRule(id = 23, categoryId = 105, keywords = "which company,partner company,company profile,is it a good match", matchMode = "ANY", priority = 120, replySubject = "Partner company information", replyBody = "We will introduce the matched partner company with a profile, website, address, and how their direction aligns with your research expertise.", displayName = "合作企业信息")
    )

    companion object {
        private val EXPECTED_NEW_RULE_DISPLAY_NAMES = mapOf(
            "Confirmation video requirement" to "承诺视频 VCR",
            "Single application commitment" to "单一申报承诺",
            "After selection process" to "入选后流程",
            "Success rate and reapplication" to "成功率/未入选",
            "Document confidentiality and no fees" to "资料保密·绝不收费",
            "Agency credentials and government cooperation" to "代理资质·政府合作证明",
            "Multi-agency rights protection" to "多代理·权益保障",
            "Project sensitivity concerns" to "项目敏感性",
            "Meeting arrangement" to "会议安排",
            "Email-only communication preference" to "只邮件·不用LinkedIn",
            "Partner company information" to "合作企业信息"
        )
    }
}
