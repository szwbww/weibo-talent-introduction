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
    @Test fun `proof binds names email and version`() {
        val profile = verified()
        assertTrue(DiscoveryIdentity.allowed(profile))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(givenNames = "John")))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(email = "b@example.org")))
        assertFalse(DiscoveryIdentity.allowed(profile.copy(identityVerification = profile.identityVerification!!.copy(version = 0))))
    }
    @Test fun `deleted email cannot reenter with new identity and excluded expert remains allowed`() {
        assertTrue(DiscoveryIdentity.isBlocked(" HANLEI1974@SINA.COM "))
        assertFalse(DiscoveryIdentity.isBlocked("xzhu3@fau.edu"))
        val p = verified()
        assertFalse(DiscoveryIdentity.allowed(p.copy(email = "hanlei1974@sina.com", identityVerification = p.identityVerification!!.copy(email = "hanlei1974@sina.com"))))
    }
    @Test fun `explicit source review authorizes only the bound stored identity and never automatic discovery`() {
        val p = verified()
        val reviewed = p.copy(identityVerification = p.identityVerification!!.copy(source = "REVIEWED_SOURCE_SHA256"))
        assertTrue(DiscoveryIdentity.allowed(reviewed))
        assertFalse(DiscoveryIdentity.validEvidence("REVIEWED_SOURCE_SHA256:" + "a".repeat(64)))
        assertFalse(DiscoveryIdentity.allowed(reviewed.copy(givenNames = "Other")))
        assertFalse(DiscoveryIdentity.allowed(reviewed.copy(identityVerification = reviewed.identityVerification!!.copy(evidenceHash = null))))
        assertFalse(DiscoveryIdentity.allowed(reviewed.copy(email = "hanlei1974@sina.com",
            identityVerification = reviewed.identityVerification!!.copy(email = "hanlei1974@sina.com"))))
    }
    @Test fun `four explicitly reviewed source identities are released from the historical deletion blocklist`() {
        listOf("raff.edward@umbc.edu", "edward.raff@crowdstrike.com", "hangzhao@hkust-gz.edu.cn", "hangzhao@tsinghua.edu.cn")
            .forEach { assertFalse(DiscoveryIdentity.isBlocked(it)) }
        assertTrue(DiscoveryIdentity.isBlocked("hanlei1974@sina.com"))
    }
}
