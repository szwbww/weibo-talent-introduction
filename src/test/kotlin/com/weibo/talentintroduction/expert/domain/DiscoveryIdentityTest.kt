package com.weibo.talentintroduction.expert.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscoveryIdentityTest {
    private val legacy = ExpertProfile(orcidId = "old-business-key", email = "a@example.org", givenNames = "Jane",
        familyNames = "Doe", country = null, keyword = null, employment = null)
    private fun verified() = legacy.copy(emailSource = "PAPER_FULLTEXT", identityVerification =
        DiscoveryIdentity.verified(legacy.email!!, "Jane", "Doe", "JATS_SHA256:" + "a".repeat(64), null, null))
    @Test fun `legacy other source unaffected but discovery without proof blocked`() {
        assertTrue(DiscoveryIdentity.allowed(legacy))
        assertFalse(DiscoveryIdentity.allowed(legacy.copy(tags = listOf("discovered"))))
        assertFalse(DiscoveryIdentity.allowed(legacy.copy(emailSource = "PAPER_FULLTEXT")))
    }
    @Test fun `explicit text source supports bound academic identity`() {
        val profile = legacy.copy(emailSource = "PAPER_FULLTEXT", identityVerification =
            DiscoveryIdentity.verified(legacy.email!!, "Jane", "Doe", "SOURCE_SHA256:" + "b".repeat(64), "real-orcid", "A123"))
        assertTrue(DiscoveryIdentity.allowed(profile))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(givenNames = "Other")))
    }

    @Test fun `cache compatibility does not change historical proof version`() {
        assertEquals(20260925, DiscoveryIdentity.VERSION)
        assertNotEquals(DiscoveryIdentity.VERSION, DiscoveryIdentity.EXTRACTION_VERSION)
        assertTrue(DiscoveryIdentity.allowed(verified()))
    }

    @Test fun `proof binds names email and version`() {
        val profile = verified()
        assertTrue(DiscoveryIdentity.allowed(profile))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(givenNames = "John")))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(email = "b@example.org")))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification = profile.identityVerification!!.copy(version = 0))))
    }
    @Test fun `source supported identity is accepted without a historical email blacklist`() {
        val p = verified()
        assertTrue(DiscoveryIdentity.allowed(p.copy(email = "hanlei1974@sina.com",
            identityVerification = p.identityVerification!!.copy(email = "hanlei1974@sina.com"))))
    }
    @Test fun `explicit source review authorizes only the bound stored identity and never automatic discovery`() {
        val p = verified()
        val reviewed = p.copy(identityVerification = p.identityVerification!!.copy(source = "REVIEWED_SOURCE_SHA256"))
        assertTrue(DiscoveryIdentity.allowed(reviewed))
        assertFalse(DiscoveryIdentity.validEvidence("REVIEWED_SOURCE_SHA256:" + "a".repeat(64)))
        assertFalse(DiscoveryIdentity.allowed(reviewed.copy(givenNames = "Other")))
        assertFalse(DiscoveryIdentity.allowed(reviewed.copy(identityVerification = reviewed.identityVerification!!.copy(evidenceHash = null))))
        assertTrue(DiscoveryIdentity.allowed(reviewed.copy(email = "hanlei1974@sina.com",
            identityVerification = reviewed.identityVerification!!.copy(email = "hanlei1974@sina.com"))))
    }

    @Test fun `new extraction cache version is accepted and prior version remains rejected`() {
        assertEquals(20260925, DiscoveryIdentity.VERSION)
        // I-5：抽取兼容版本随 PDF 标记/残缺邮箱规则升级；历史身份证明版本 20260925 不变。
        assertEquals(20261004, DiscoveryIdentity.EXTRACTION_VERSION)
        val profile = verified()
        assertTrue(DiscoveryIdentity.allowed(profile))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification =
            profile.identityVerification!!.copy(version = 20261003))))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification =
            profile.identityVerification!!.copy(version = 20260929))))
    }

    // ── 01（I-1/I-2/I-3）：解释与布尔判定同源 ────────────────────────────────
    // 只读解释，逐项列出与 allowed / validInstitutionEvidence 相同的失败条件，不签发也不改写凭证。

    private fun evidenceProfile(): ExpertProfile = verified().copy(
        orcidId = "0000-0002-0000-0001", country = "Germany", institution = "Example University",
        institutionType = "education", externalIds = """{"pmcId":"PMC0000001"}""")

    private fun withEvidence(profile: ExpertProfile): ExpertProfile = profile.copy(
        institutionEvidence = DiscoveryIdentity.institutionEvidence(profile, DiscoveryIdentity.EVIDENCE_SOURCE_JATS))

    private fun identityCodes(profile: ExpertProfile): List<String> =
        DiscoveryIdentity.explainIdentity(profile).map { it.code }

    private fun evidenceCodes(profile: ExpertProfile): List<String> =
        DiscoveryIdentity.explainInstitutionEvidence(profile).map { it.code }

    private fun legacyApprovedProfile(): ExpertProfile {
        val base = evidenceProfile()
        return base.copy(identityVerification = IdentityVerification(
            status = "LEGACY_APPROVED", version = DiscoveryIdentity.VERSION,
            source = DiscoveryIdentity.LEGACY_APPROVAL_SOURCE,
            email = DiscoveryIdentity.normalizedEmail(base.email),
            givenNames = base.givenNames, familyNames = base.familyNames,
            evidenceHash = DiscoveryIdentity.legacyApprovalDigest(base)))
    }

    @Test fun `identity explanation is empty exactly when allowed`() {
        val proof = verified().identityVerification!!
        val samples = listOf(
            legacy,
            legacy.copy(emailSource = "PAPER_FULLTEXT"),
            legacy.copy(tags = listOf("discovered")),
            verified(),
            verified().copy(givenNames = "Other"),
            verified().copy(email = "other@example.org"),
            verified().copy(identityVerification = proof.copy(version = 0)),
            verified().copy(identityVerification = proof.copy(status = "UNRESOLVED")),
            verified().copy(identityVerification = proof.copy(email = null)),
            verified().copy(identityVerification = proof.copy(givenNames = null)),
            verified().copy(identityVerification = proof.copy(familyNames = "")),
            verified().copy(identityVerification = proof.copy(evidenceHash = "zz")),
            verified().copy(identityVerification = proof.copy(source = "REVIEWED_SOURCE_SHA256")),
            verified().copy(identityVerification = proof.copy(source = "REVIEWED_SOURCE_SHA256",
                evidenceHash = "0".repeat(63))),
            legacyApprovedProfile()
        )
        samples.forEach { profile ->
            assertEquals(DiscoveryIdentity.allowed(profile),
                DiscoveryIdentity.explainIdentity(profile).isEmpty(), profile.toString())
        }
    }

    @Test fun `identity explanation separates missing version field and source reasons`() {
        val proof = verified().identityVerification!!
        assertEquals(listOf("IDENTITY_MISSING"), identityCodes(legacy.copy(tags = listOf("discovered"))))
        assertEquals(listOf("IDENTITY_STATUS_UNVERIFIED"),
            identityCodes(verified().copy(identityVerification = proof.copy(status = "UNRESOLVED"))))
        assertEquals(listOf("IDENTITY_VERSION_UNSUPPORTED"),
            identityCodes(verified().copy(identityVerification = proof.copy(version = 1))))
        val emailMismatch = DiscoveryIdentity.explainIdentity(
            verified().copy(identityVerification = proof.copy(email = "other@example.org")))
        assertEquals(listOf("IDENTITY_FIELDS_MISMATCH"), emailMismatch.map { it.code })
        assertEquals("identityVerification.email", emailMismatch.single().field)
        assertEquals("identityVerification.givenNames",
            DiscoveryIdentity.explainIdentity(verified().copy(identityVerification = proof.copy(givenNames = "Other")))
                .single().field)
        assertEquals(listOf("IDENTITY_SOURCE_INVALID"),
            identityCodes(verified().copy(identityVerification = proof.copy(evidenceHash = "zz"))))
        assertTrue(identityCodes(legacy).isEmpty())
    }

    @Test fun `legacy receipt is never upgraded to a source verified identity`() {
        val approved = legacyApprovedProfile()
        assertTrue(DiscoveryIdentity.legacyOutreachApproved(approved))
        assertFalse(DiscoveryIdentity.allowed(approved))
        assertEquals(listOf("IDENTITY_STATUS_UNVERIFIED", "IDENTITY_SOURCE_INVALID"), identityCodes(approved))
        assertEquals("LEGACY_APPROVED", DiscoveryIdentity.explainIdentity(approved).first().observed)
    }

    @Test fun `institution evidence explanation is empty exactly when signature verifies`() {
        val issued = withEvidence(evidenceProfile())
        val proof = issued.identityVerification!!
        val samples = listOf(
            evidenceProfile(),
            issued,
            issued.copy(institution = "Another University"),
            issued.copy(institution = null),
            issued.copy(institutionEvidence = "SBIR:" + "a".repeat(64)),
            issued.copy(institutionEvidence = "JATS:not-a-hash"),
            issued.copy(externalIds = """{"orcid":"0000-0002-0000-0001"}"""),
            issued.copy(externalIds = """{"pmcId":"PMC0000001","orcid":"0000-0009-0000-0009"}""",
                identityVerification = proof.copy(orcid = "0000-0001-0000-0001")),
            issued.copy(email = "other@example.org"),
            issued.copy(country = "France")
        )
        samples.forEach { profile ->
            assertEquals(DiscoveryIdentity.validInstitutionEvidence(profile),
                DiscoveryIdentity.explainInstitutionEvidence(profile).isEmpty(), profile.toString())
        }
        assertTrue(DiscoveryIdentity.explainInstitutionEvidence(issued).isEmpty())
    }

    @Test fun `evidence explanation names the missing input without inventing history`() {
        val issued = withEvidence(evidenceProfile())
        val missing = DiscoveryIdentity.explainInstitutionEvidence(evidenceProfile())
        assertEquals(listOf("INSTITUTION_EVIDENCE_MISSING"), missing.map { it.code })
        assertNull(missing.single().observed)
        assertEquals("institutionEvidence", missing.single().field)

        val removedId = DiscoveryIdentity.explainInstitutionEvidence(
            issued.copy(externalIds = """{"orcid":"0000-0002-0000-0001"}"""))
        assertEquals(listOf("INSTITUTION_EVIDENCE_INVALID", "SOURCE_ID_MISSING"), removedId.map { it.code })
        assertEquals("externalIds.pmcId", removedId.last().field)
        assertNull(removedId.last().observed)

        val conflict = DiscoveryIdentity.explainInstitutionEvidence(issued.copy(
            externalIds = """{"pmcId":"PMC0000001","orcid":"0000-0009-0000-0009"}""",
            identityVerification = issued.identityVerification!!.copy(orcid = "0000-0001-0000-0001")))
        assertEquals("INSTITUTION_EVIDENCE_INVALID", conflict.first().code)
        assertEquals(3, conflict.count { it.code == "SOURCE_ID_CONFLICT" }, conflict.map { it.code }.toString())
        assertTrue(conflict.filter { it.code == "SOURCE_ID_CONFLICT" }.all { it.observed!!.contains("=") })
    }

    @Test fun `institution absence is not attributed to zero or multiple institutions`() {
        val reasons = DiscoveryIdentity.explainInstitutionEvidence(evidenceProfile().copy(institution = null))
        assertEquals(listOf("INSTITUTION_EVIDENCE_MISSING"), reasons.map { it.code })
        assertNull(reasons.single().observed)
        assertEquals("来源种类:<64位小写SHA256>", reasons.single().expected)
    }
}
