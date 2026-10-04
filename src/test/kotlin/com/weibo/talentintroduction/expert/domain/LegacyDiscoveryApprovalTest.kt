package com.weibo.talentintroduction.expert.domain

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LegacyDiscoveryApprovalTest {
    private val base = ExpertProfile(orcidId = "legacy-1", email = "a@example.org", givenNames = "Jürgen",
        familyNames = null, country = "Germany", keyword = null, employment = null,
        institution = "Example University", institutionType = "education", tags = listOf("discovered"),
        filterResult = "PASSED")
    private fun approve(p: ExpertProfile = base) = p.copy(identityVerification = IdentityVerification(
        status = "LEGACY_APPROVED", version = DiscoveryIdentity.VERSION,
        source = DiscoveryIdentity.LEGACY_APPROVAL_SOURCE, email = DiscoveryIdentity.normalizedEmail(p.email),
        givenNames = p.givenNames, familyNames = p.familyNames, evidenceHash = DiscoveryIdentity.legacyApprovalDigest(p)))
    private val scope = RecipientScope(mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
        tags = listOf("discovered"), regions = emptyList(), emailDomains = emptyList(), discipline = null,
        expertTypes = listOf("UNCLASSIFIED"))

    @Test fun `explicit approval admits outreach but never authorizes academic source identity`() {
        val p = approve()
        assertTrue(DiscoveryIdentity.legacyOutreachApproved(p))
        assertTrue(scope.matchesEsTarget(p))
        assertTrue(scope.matchesExpert(p))
        assertFalse(DiscoveryIdentity.allowed(p))
        assertFalse(DiscoveryIdentity.validInstitutionEvidence(p))
        assertNull(DiscoveryIdentity.institutionEvidence(p, "ORCID"))
        assertFalse(scope.matchesEsTarget(base))
        assertFalse(scope.matchesExpert(base))
    }
    @Test fun `approval digest matches Python unicode null receipt`() {
        assertEquals("60ad8ad4b58e121f4ed7deb6bdd7cbe774a69cda32318057e2f0c125b8c537ac", DiscoveryIdentity.legacyApprovalDigest(base))
    }
    @Test fun `approval invalidates on any bound profile change`() {
        val p = approve()
        listOf(p.copy(orcidId="other"), p.copy(email="other@example.org"), p.copy(givenNames="Other"),
            p.copy(familyNames=""), p.copy(institution="Other"), p.copy(country="France"),
            p.copy(institutionType="company")).forEach {
            assertFalse(DiscoveryIdentity.legacyOutreachApproved(it))
            assertFalse(scope.matchesEsTarget(it))
        }
        assertTrue(DiscoveryIdentity.legacyOutreachApproved(p.copy(email=" A@EXAMPLE.ORG ")))
    }
    @Test fun `arbitrary or malformed receipts do not grant approval`() {
        val p=approve(); val proof=p.identityVerification!!
        listOf(proof.copy(status="VERIFIED"), proof.copy(version=0), proof.copy(source="USER"),
            proof.copy(evidenceHash="0".repeat(64)), proof.copy(email="other@example.org"),
            proof.copy(givenNames="Other")).forEach {
            assertFalse(scope.matchesEsTarget(p.copy(identityVerification=it)))
        }
        assertFalse(DiscoveryIdentity.legacyOutreachApproved(approve(base.copy(email=""))))
    }
    @Test fun `approval retains institution country qualification and visible filters`() {
        listOf(base.copy(institution=""), base.copy(country=null), base.copy(country="Not a country"),
            base.copy(filterResult="REJECTED"), base.copy(filterResult=null)).forEach {
            assertFalse(scope.matchesExpert(approve(it)))
            assertFalse(scope.matchesEsTarget(approve(it)))
        }
        val p=approve()
        assertFalse(scope.copy(expertTypes=emptyList()).matchesExpert(p))
        assertFalse(scope.copy(tags=listOf("other")).matchesExpert(p))
        assertFalse(scope.copy(gateEsFields=listOf("degree")).matchesExpert(p))
        assertFalse(scope.copy(operatorStatuses=listOf("WAITING_REPLY")).matchesExpert(p))
    }
}
