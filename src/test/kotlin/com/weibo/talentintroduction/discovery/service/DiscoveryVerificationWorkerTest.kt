package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.RegistrationRecovery
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory

class DiscoveryVerificationWorkerTest {
    private fun worker(props: DiscoveryVerificationProperties, repo: DiscoveryVerificationRepository,
        factory: DefaultListableBeanFactory = DefaultListableBeanFactory()): DiscoveryVerificationWorker =
        DiscoveryVerificationWorker(props, repo, Mockito.mock(OrcidVerificationEvidenceClient::class.java),
            Mockito.mock(ApolloPeopleSearchClient::class.java), Mockito.mock(QualificationEvidenceParser::class.java),
            DiscoveryVerificationRules(ruleClock), Mockito.mock(DiscoveryVerificationCommitService::class.java),
            factory.getBeanProvider(RegistrationRecovery::class.java), ruleClock)

    @Test fun `disabled worker neither claims work nor calls optional recovery`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(), repo, factory).useWorker { it.tick() }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `enabled worker tolerates absent registration recovery and never creates an execution`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker { it.tick() }
        Mockito.verify(repo).listProjectionDue(10)
        Mockito.verify(repo).claimDue(ruleClock.instant(), 120)
        Mockito.verifyNoMoreInteractions(repo)
    }

    @Test fun `bounded registration recovery uses the already existing optional seam`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { it.tick() }
        Mockito.verify(recovery).recoverReserved(10)
        Mockito.verifyNoMoreInteractions(recovery)
    }

    @Test fun `reading current unblocked latch never claims or recovers work`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { current ->
            repeat(20) { assertFalse(current.isAuthBlocked()) }
        }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `new queries normalize aliases and narrow only one eligible identity institution`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val facts = listOf(ruleJob(source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF),
                ruleFact("otherName", "ＡＤＡ, Lee"), ruleFact("otherName", "Known Alias"), ruleFact("otherName", "known alias"))
            val queries = current.searchQueries(ruleSnapshot(), facts)
            assertEquals(listOf("Ada Lee", "Known Alias", "Ada Lee"), queries.map { it.name })
            assertEquals("Example University", queries.last().institution!!.name)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts + ruleJob(
                source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF, institution = "Other University")).size)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts).count { it.institution == null })
        }
    }
    private fun DiscoveryVerificationWorker.useWorker(action: (DiscoveryVerificationWorker) -> Unit) {
        try { action(this) } finally { close() }
    }
}
