package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.discovery.domain.AdmissionReason
import com.weibo.talentintroduction.discovery.domain.AdmissionReasonCodes
import com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 01（I-1/I-2/I-3）：准入解释器测试。全部样本为结构化合成数据，不使用任何真实专家姓名、邮箱或属性。
 */
class DiscoveryAdmissionPolicyTest {

    private val filterService = mock(EligibilityFilterService::class.java)
    private val emailValidationService = mock(EmailValidationService::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-10-04T08:00:00Z"), ZoneOffset.UTC)

    private fun policy(
        candidate: CandidateFilterProperties = CandidateFilterProperties(),
        academic: AcademicFilterProperties = AcademicFilterProperties()
    ): DiscoveryAdmissionPolicy {
        `when`(filterService.getCandidateFilter()).thenReturn(candidate)
        `when`(filterService.getAcademicFilter()).thenReturn(academic)
        return DiscoveryAdmissionPolicy(
            CandidateEligibilityService(filterService, emailValidationService), filterService, clock)
    }

    /** 合成样本：虚构姓名/机构/邮箱；研发分类所需证据齐备（研究领域 ID + 近年发表 + 研究方向）。 */
    private fun syntheticProfile(
        email: String = "synthetic.sample@example.org",
        givenNames: String = "Sample",
        familyNames: String = "Synthetic",
        orcidId: String = "0000-0002-0000-0001"
    ): ExpertProfile = ExpertProfile(
        orcidId = orcidId,
        email = email,
        givenNames = givenNames,
        familyNames = familyNames,
        country = "Germany",
        keyword = "synthetic engineering",
        employment = "Synthetic University",
        degree = "Doctoral Degree",
        nationality = "Germany",
        researchFields = "synthetic research field",
        institution = "Synthetic University",
        institutionType = "education",
        emailSource = "PAPER_FULLTEXT",
        dataSource = "JATS",
        externalIds = """{"pmcId":"PMC0000001"}""",
        researchFieldIds = listOf("22"),
        lastPublicationYear = 2026,
        identityVerification = DiscoveryIdentity.verified(
            email, givenNames, familyNames, "JATS_SHA256:" + "a".repeat(64), null, null)
    )

    /** 基础校验全过样本：身份有效、机构非空、国家可映射、机构来源凭证已签发。 */
    private val complete: ExpertProfile = syntheticProfile().let {
        it.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(it, DiscoveryIdentity.EVIDENCE_SOURCE_JATS))
    }

    private fun withValidLegacyReceipt(profile: ExpertProfile): ExpertProfile = profile.copy(
        identityVerification = IdentityVerification(
            status = "LEGACY_APPROVED",
            version = DiscoveryIdentity.VERSION,
            source = DiscoveryIdentity.LEGACY_APPROVAL_SOURCE,
            email = DiscoveryIdentity.normalizedEmail(profile.email),
            givenNames = profile.givenNames,
            familyNames = profile.familyNames,
            evidenceHash = DiscoveryIdentity.legacyApprovalDigest(profile)
        )
    )

    private fun codes(reasons: List<AdmissionReason>): List<String> = reasons.map { it.code }

    @Test
    fun `complete synthetic profile auto passes with empty reasons and hints`() {
        val result = policy().evaluate(complete)

        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, result.status)
        assertTrue(result.eligible)
        assertTrue(result.blockingReasons.isEmpty(), result.blockingReasons.toString())
        assertTrue(result.hints.isEmpty(), result.hints.toString())
        assertEquals(DiscoveryAdmissionPolicy.POLICY_VERSION, result.policyVersion)
        assertEquals(Instant.parse("2026-10-04T08:00:00Z"), result.checkedAt)
        assertFalse(result.configSnapshot.requireOrcid)
        assertTrue(result.configSnapshot.requireValidEmail)
        assertFalse(result.configSnapshot.requireDoctoralDegree)
        assertEquals(70, result.configSnapshot.maxAgeExclusive)
        assertEquals(5, result.configSnapshot.minHIndex)
    }

    @Test
    fun `toggling one candidate rule changes only that rule's reason`() {
        val noDegree = complete.copy(degree = null)
        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, policy().evaluate(noDegree).status)

        val strict = policy(CandidateFilterProperties(requireDoctoralDegree = true)).evaluate(noDegree)
        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, strict.status)
        assertEquals(listOf("NO_DOCTORAL_DEGREE"), codes(strict.blockingReasons))
        val reason = strict.blockingReasons.single()
        assertEquals("degree", reason.field)
        assertNull(reason.observed)
        assertTrue(strict.configSnapshot.requireDoctoralDegree)

        val requireOrcid = policy(CandidateFilterProperties(requireOrcid = true))
            .evaluate(complete.copy(orcidId = ""))
        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, requireOrcid.status)
        assertEquals(listOf("MISSING_ORCID"), codes(requireOrcid.blockingReasons))
        assertEquals("orcidId", requireOrcid.blockingReasons.single().field)

        val academic = policy(academic = AcademicFilterProperties(enableHIndexFilter = true, minHIndex = 5))
            .evaluate(complete.copy(hIndex = 1))
        assertEquals(listOf("H_INDEX_TOO_LOW"), codes(academic.blockingReasons))
        assertEquals("hIndex", academic.blockingReasons.single().field)
        assertEquals("1", academic.blockingReasons.single().observed)
    }

    @Test
    fun `no new thresholds - all existing filters off still auto passes a sparse profile`() {
        val sparse = complete.copy(degree = null, age = null, hIndex = null, citationCount = null,
            nationality = null, lastPublicationYear = 2026)
        val result = policy(
            candidate = CandidateFilterProperties(requireValidEmail = false, excludeChineseNationality = false),
            academic = AcademicFilterProperties()
        ).evaluate(sparse)

        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, result.status, result.blockingReasons.toString())
        assertTrue(result.hints.isEmpty(), result.hints.toString())
    }

    @Test
    fun `missing institution produces INSTITUTION_MISSING`() {
        val result = policy().evaluate(complete.copy(institution = null, institutionEvidence = null))

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertTrue(codes(result.blockingReasons).contains(AdmissionReasonCodes.INSTITUTION_MISSING))
        val reason = result.blockingReasons.first { it.code == AdmissionReasonCodes.INSTITUTION_MISSING }
        assertEquals("institution", reason.field)
        assertNull(reason.observed)
    }

    @Test
    fun `blank and unmapped country produce their own codes`() {
        val blank = policy().evaluate(complete.copy(country = null))
        assertTrue(codes(blank.blockingReasons).contains(AdmissionReasonCodes.COUNTRY_MISSING))
        assertEquals("country", blank.blockingReasons.first { it.code == AdmissionReasonCodes.COUNTRY_MISSING }.field)

        val unmapped = policy().evaluate(complete.copy(country = "Atlantis"))
        assertTrue(codes(unmapped.blockingReasons).contains(AdmissionReasonCodes.COUNTRY_UNMAPPED))
        assertEquals("Atlantis",
            unmapped.blockingReasons.first { it.code == AdmissionReasonCodes.COUNTRY_UNMAPPED }.observed)
    }

    @Test
    fun `missing evidence token is reported as missing not as mismatch`() {
        val result = policy().evaluate(complete.copy(institutionEvidence = null))

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertEquals(listOf(AdmissionReasonCodes.INSTITUTION_EVIDENCE_MISSING), codes(result.blockingReasons))
        val reason = result.blockingReasons.single()
        assertNull(reason.observed)
        assertEquals("institutionEvidence", reason.field)
        assertFalse(codes(result.blockingReasons).contains(AdmissionReasonCodes.IDENTITY_SOURCE_INVALID))
    }

    @Test
    fun `tampered institution invalidates the evidence token`() {
        val result = policy().evaluate(complete.copy(institution = "Another Synthetic Institute"))

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertEquals(listOf(AdmissionReasonCodes.INSTITUTION_EVIDENCE_INVALID), codes(result.blockingReasons))
        assertEquals("JATS", result.blockingReasons.single().observed)
    }

    @Test
    fun `removed source id reports missing source id together with invalid evidence`() {
        val result = policy().evaluate(complete.copy(externalIds = """{"orcid":"0000-0002-0000-0001"}"""))

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertEquals(listOf(AdmissionReasonCodes.INSTITUTION_EVIDENCE_INVALID, AdmissionReasonCodes.SOURCE_ID_MISSING),
            codes(result.blockingReasons))
        assertEquals("externalIds.pmcId",
            result.blockingReasons.first { it.code == AdmissionReasonCodes.SOURCE_ID_MISSING }.field)
    }

    @Test
    fun `conflicting identity source ids are reported as a conflict`() {
        val conflicting = complete.copy(
            externalIds = """{"pmcId":"PMC0000001","orcid":"0000-0009-0000-0009"}""",
            identityVerification = complete.identityVerification!!.copy(orcid = "0000-0001-0000-0001")
        )
        val result = policy().evaluate(conflicting)

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertTrue(codes(result.blockingReasons).contains(AdmissionReasonCodes.SOURCE_ID_CONFLICT))
        assertTrue(codes(result.blockingReasons).contains(AdmissionReasonCodes.INSTITUTION_EVIDENCE_INVALID))
    }

    @Test
    fun `valid legacy approval is admitted alone with facts shown as hints`() {
        val approved = withValidLegacyReceipt(
            syntheticProfile().copy(institution = null, country = null, institutionEvidence = null)
        )
        val result = policy().evaluate(approved)

        assertEquals(DiscoveryAdmissionStatus.LEGACY_APPROVED, result.status)
        assertTrue(result.eligible)
        assertTrue(result.blockingReasons.isEmpty(), result.blockingReasons.toString())
        // I-2：人工认可不升级为学术来源验证。
        assertFalse(DiscoveryIdentity.allowed(approved))
        val hintCodes = codes(result.hints)
        assertTrue(hintCodes.contains(AdmissionReasonCodes.IDENTITY_STATUS_UNVERIFIED), hintCodes.toString())
        assertTrue(hintCodes.contains(AdmissionReasonCodes.INSTITUTION_MISSING))
        assertTrue(hintCodes.contains(AdmissionReasonCodes.COUNTRY_MISSING))
        assertTrue(hintCodes.contains(AdmissionReasonCodes.INSTITUTION_EVIDENCE_MISSING))
    }

    @Test
    fun `legacy approval invalidated by email change falls back to automatic review`() {
        val approved = withValidLegacyReceipt(complete)
        val changed = approved.copy(email = "synthetic.other@example.org")

        assertTrue(DiscoveryIdentity.legacyOutreachApproved(approved))
        assertFalse(DiscoveryIdentity.legacyOutreachApproved(changed))
        val result = policy().evaluate(changed)
        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        assertTrue(codes(result.blockingReasons).contains(AdmissionReasonCodes.IDENTITY_STATUS_UNVERIFIED))
    }

    @Test
    fun `non discovery profile keeps the original entry without reasons`() {
        val businessProfile = ExpertProfile(orcidId = "legacy-business-key", email = "business@example.org",
            givenNames = "Sample", familyNames = "Business", country = null, keyword = null, employment = null)
        val result = policy().evaluate(businessProfile)

        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, result.status)
        assertTrue(result.blockingReasons.isEmpty())
        assertTrue(result.hints.isEmpty())
    }

    @Test
    fun `candidate rule vocabulary is explained with real fields`() {
        AdmissionReasonCodes.CANDIDATE_RULE_CODES.forEach { code ->
            val reason = policy().evaluate(complete, EligibilityResult(false, listOf(code))).blockingReasons.single()
            assertEquals(code, reason.code)
            assertNotEquals(AdmissionReason.UNKNOWN_FIELD, reason.field, code)
            assertEquals("CandidateEligibilityService.evaluateEligibility()", reason.sourceLocation)
        }
    }

    @Test
    fun `unknown candidate rule code is preserved verbatim`() {
        val result = policy().evaluate(complete, EligibilityResult(false, listOf("SOME_FUTURE_RULE")))

        assertEquals(DiscoveryAdmissionStatus.NEEDS_REVIEW, result.status)
        val reason = result.blockingReasons.single()
        assertEquals("SOME_FUTURE_RULE", reason.code)
        assertEquals(AdmissionReason.UNKNOWN_FIELD, reason.field)
        assertNull(reason.observed)
        assertNull(reason.expected)
    }

    @Test
    fun `status is derived from the blocking reasons across the sample matrix`() {
        val samples = listOf(
            complete,
            complete.copy(institution = null),
            complete.copy(country = null),
            complete.copy(country = "Atlantis"),
            complete.copy(institutionEvidence = null),
            complete.copy(identityVerification = null),
            complete.copy(degree = null),
            complete.copy(researchFields = null)
        )
        samples.forEach { profile ->
            val result = policy().evaluate(profile)
            assertEquals(result.blockingReasons.isEmpty(), result.status == DiscoveryAdmissionStatus.AUTO_PASSED,
                profile.toString())
            assertEquals(result.blockingReasons.isNotEmpty(), result.status == DiscoveryAdmissionStatus.NEEDS_REVIEW,
                profile.toString())
            assertEquals(result.status != DiscoveryAdmissionStatus.NEEDS_REVIEW, result.eligible)
        }
    }

    @Test
    fun `research direction missing is only a hint and never blocks`() {
        val result = policy().evaluate(complete.copy(researchFields = null))

        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, result.status, result.blockingReasons.toString())
        assertEquals(listOf(AdmissionReasonCodes.RESEARCH_DIRECTION_MISSING), codes(result.hints))
        assertEquals("researchFields", result.hints.single().field)
        assertFalse(codes(result.blockingReasons).contains(AdmissionReasonCodes.RESEARCH_DIRECTION_MISSING))
    }

    @Test
    fun `checked at comes from the injected clock and eligibility may be supplied by the caller`() {
        val callerProvided = policy().evaluate(complete, EligibilityResult.pass())

        assertEquals(DiscoveryAdmissionStatus.AUTO_PASSED, callerProvided.status)
        assertEquals(Instant.parse("2026-10-04T08:00:00Z"), callerProvided.checkedAt)
    }
}
