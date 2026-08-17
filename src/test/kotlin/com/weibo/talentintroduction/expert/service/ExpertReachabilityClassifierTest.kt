package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertReachability
import com.weibo.talentintroduction.mail.service.ProviderResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExpertReachabilityClassifierTest {
    private val classifier = ExpertReachabilityClassifier(ProviderResolver())

    private fun profile(
        orcidId: String = "0000-0001-ABCD-EFGH",
        email: String? = "a@mit.edu",
        emailSource: String? = "PAPER_FULLTEXT"
    ): ExpertProfile = ExpertProfile(
        esDocId = null,
        orcidId = orcidId,
        email = email,
        givenNames = null,
        familyNames = null,
        country = null,
        keyword = null,
        employment = null,
        age = null,
        degree = null,
        nationality = null,
        hIndex = null,
        citationCount = null,
        lastPublicationYear = null,
        researchFields = null,
        disciplineCategory = null,
        institution = null,
        emailSource = emailSource
    )

    @Test
    fun `suppressed email wins over hard bounce`() {
        assertEquals(
            ExpertReachability.BLOCKED_UNSUBSCRIBED,
            classifier.classify(profile(), setOf("a@mit.edu"), setOf("0000-0001-ABCD-EFGH"))
        )
    }

    @Test
    fun `hard bounce only is blocked bounced`() {
        assertEquals(
            ExpertReachability.BLOCKED_BOUNCED,
            classifier.classify(profile(), emptySet(), setOf("0000-0001-ABCD-EFGH"))
        )
    }

    @Test
    fun `null emailSource with no blocked fact is unknown`() {
        assertNull(classifier.classify(profile(emailSource = null), emptySet(), emptySet()))
    }

    @Test
    fun `blank emailSource with no blocked fact is unknown`() {
        assertNull(classifier.classify(profile(emailSource = ""), emptySet(), emptySet()))
    }

    @Test
    fun `suppressed email short-circuits before unknown`() {
        assertEquals(
            ExpertReachability.BLOCKED_UNSUBSCRIBED,
            classifier.classify(profile(emailSource = null), setOf("a@mit.edu"), emptySet())
        )
    }

    @Test
    fun `paper fulltext with edu domain is high`() {
        assertEquals(
            ExpertReachability.HIGH,
            classifier.classify(profile(email = "a@mit.edu"), emptySet(), emptySet())
        )
    }

    @Test
    fun `paper fulltext with other domain is high`() {
        assertEquals(
            ExpertReachability.HIGH,
            classifier.classify(profile(email = "a@uni-heidelberg.de"), emptySet(), emptySet())
        )
    }

    @Test
    fun `paper fulltext with gmail is low`() {
        assertEquals(
            ExpertReachability.LOW,
            classifier.classify(profile(email = "a@gmail.com"), emptySet(), emptySet())
        )
    }

    @Test
    fun `paper fulltext with every consumer provider is low`() {
        for (email in listOf("a@qq.com", "a@163.com", "a@outlook.com", "a@yahoo.com")) {
            assertEquals(
                ExpertReachability.LOW,
                classifier.classify(profile(email = email), emptySet(), emptySet()),
                "expected LOW for $email"
            )
        }
    }

    @Test
    fun `orcid public with edu domain is low`() {
        assertEquals(
            ExpertReachability.LOW,
            classifier.classify(profile(emailSource = "ORCID_PUBLIC"), emptySet(), emptySet())
        )
    }

    @Test
    fun `orcid public with consumer email is low`() {
        assertEquals(
            ExpertReachability.LOW,
            classifier.classify(profile(email = "a@gmail.com", emailSource = "ORCID_PUBLIC"), emptySet(), emptySet())
        )
    }

    @Test
    fun `email normalization matches suppressed set case and whitespace insensitively`() {
        assertEquals(
            ExpertReachability.BLOCKED_UNSUBSCRIBED,
            classifier.classify(profile(email = "  A@Mit.EDU  "), setOf("a@mit.edu"), emptySet())
        )
    }

    @Test
    fun `null email does not throw and is treated as non-consumer`() {
        assertEquals(
            ExpertReachability.HIGH,
            classifier.classify(profile(email = null), emptySet(), emptySet())
        )
    }
}
