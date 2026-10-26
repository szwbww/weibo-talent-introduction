package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

object HistoricalReplayFixtures {
    val tool = DiscoveryHistoricalReplayTool
    fun paper(root: Path, id: Long = 1, email: String = "ada@fixture.edu", given: String = "Ada",
        missing: Boolean = false, baseline: List<AuthorEmail>? = emptyList()): DiscoveryHistoricalReplayTool.Paper {
        val bytes = """<?xml version="1.0"?><article><front><article-meta><title-group><article-title>Fixture</article-title></title-group><contrib-group><contrib contrib-type="author"><name><surname>Lee</surname><given-names>$given</given-names></name><email>$email</email><xref ref-type="aff" rid="aff1"/></contrib></contrib-group><aff id="aff1"><institution content-type="university">Example University</institution><country>United Kingdom</country></aff></article-meta></front><body><p>$given Lee holds a PhD in physics.</p></body></article>""".toByteArray()
        val path = root.toRealPath().resolve("source-$id.xml")
        if (!missing) { Files.write(path, bytes); Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
        return DiscoveryHistoricalReplayTool.Paper(PaperSourceRef(id, id, DiscoveryPaperArchiveService.sha(bytes), 28671,
            "CROSSREF", "fixture-$id"), path.toString(), ArchiveAttemptKind.PMC_XML,
            PaperMetadata(null, null, "10.9999/fixture-$id", "Synthetic paper", 2026, null,
                listOf(PaperAuthor(given, "Lee", null, null, true)), "CROSSREF"), baseline, listOf(id))
    }
    fun scope(papers: List<DiscoveryHistoricalReplayTool.Paper>, experts: List<DiscoveryHistoricalReplayTool.Expert> = emptyList(),
        config: Map<String, String> = mapOf("fixture" to "isolated")) = DiscoveryHistoricalReplayTool.Scope(papers, experts, true, true,
        config, Instant.parse("2026-10-24T12:00:00Z"))
    fun freeze(root: Path, papers: List<DiscoveryHistoricalReplayTool.Paper>) = tool.freeze(scope(papers), root.toRealPath().resolve("frozen"))
}

class DiscoveryHistoricalReplayToolTest {
    @TempDir lateinit var root: Path
    @Test fun `production component scan keeps CLI clients out of HTTP wiring`() {
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { context ->
            context.defaultListableBeanFactory.setAllowBeanDefinitionOverriding(false)
            context.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("isolatedClients", mapOf(
                "talent-introduction.elasticsearch.base-url" to "http://127.0.0.1:1",
                "talent-introduction.elasticsearch.username" to "fixture",
                "talent-introduction.elasticsearch.password" to "fixture",
                "talent-introduction.elasticsearch.raw-index-name" to "raw",
                "talent-introduction.elasticsearch.candidate-index-name" to "candidate",
                "talent-introduction.elasticsearch.application-index-name" to "application")))
            context.beanFactory.registerSingleton("restTemplateBuilder", org.springframework.boot.web.client.RestTemplateBuilder())
            context.beanFactory.registerSingleton("budgetStore",
                org.mockito.Mockito.mock(com.weibo.talentintroduction.config.OpenAlexBudgetStore::class.java))
            val scanner = org.springframework.context.annotation.ClassPathBeanDefinitionScanner(context)
            val boundary = setOf(com.weibo.talentintroduction.config.RestTemplateConfig::class.java.name,
                HistoricalBindingsConfiguration::class.java.name)
            scanner.addExcludeFilter { metadata, _ -> metadata.classMetadata.className !in boundary }
            scanner.scan("com.weibo.talentintroduction")
            context.refresh()
            val standard = context.getBean("restTemplate", org.springframework.web.client.RestTemplate::class.java)
            val europe = context.getBean("europePmcRestTemplate", org.springframework.web.client.RestTemplate::class.java)
            assertNotSame(standard, europe)
            assertFalse(standard.interceptors.any { it is com.weibo.talentintroduction.config.RetryingClientHttpRequestInterceptor })
            assertTrue(europe.interceptors.any { it is com.weibo.talentintroduction.config.RetryingClientHttpRequestInterceptor })
            assertTrue(context.getBeansOfType(HistoricalBindingsConfiguration::class.java).isEmpty())
            assertFalse(context.containsBean("analyzer"))
            assertFalse(context.containsBean("discoveryFetchExecutor"))
        }
    }
    @Test fun `fixed production parser compare is private deterministic and layered`() {
        val paper = HistoricalReplayFixtures.paper(root)
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(paper))
        val first = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals("COMPLETE", first.coverage)
        assertEquals(1, first.counts["newBindings"])
        assertEquals(1, first.counts["newEmails"])
        assertEquals(0, first.counts["newRaw"])
        assertNull(first.counts["wouldAdmit"])
        assertEquals(0, first.businessWrites); assertEquals(0, first.providerHttpRequests)
        assertNotNull(first.rows.single().after.single().identityEvidence)
        assertEquals(1, first.rows.single().analysis!!.qualificationFacts.size)
        assertEquals(first, DiscoveryHistoricalReplayTool.compare(frozen))
        val a = DiscoveryHistoricalReplayTool.writeComparison(frozen)
        assertEquals(a, DiscoveryHistoricalReplayTool.writeComparison(frozen))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(frozen.directory))
        Files.list(frozen.directory).use { paths -> paths.forEach { assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(it)) } }
        assertEquals(frozen, DiscoveryHistoricalReplayTool.load(frozen.directory.resolve("manifest.json"), frozen.hash))
        assertThrows(IllegalArgumentException::class.java) { DiscoveryHistoricalReplayTool.load(frozen.directory.resolve("manifest.json"), "0".repeat(64)) }
    }
    @Test fun `missing source baseline or history is partial never zero gain`() {
        val missing = HistoricalReplayFixtures.paper(root, missing = true)
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(missing))
        val report = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals("PARTIAL", report.coverage); assertNull(report.counts["newBindings"])
        assertEquals(0, report.denominators["readAttempts"]); assertTrue("SOURCE_MISSING" in report.reasons)
        val other = root.resolve("other"); Files.createDirectory(other)
        val baselineMissing = HistoricalReplayFixtures.paper(other, baseline = null)
        val second = HistoricalReplayFixtures.freeze(other, listOf(baselineMissing))
        assertNull(DiscoveryHistoricalReplayTool.compare(second).counts["newBindings"])
        val incomplete = second.copy(manifest = second.manifest.copy(scope = second.manifest.scope.copy(historyComplete = false)))
        assertTrue("EXPERT_HISTORY_INCOMPLETE" in DiscoveryHistoricalReplayTool.compare(incomplete).reasons)
    }
    @Test fun `source hash changes and cross paper ownership cannot claim safe new people`() {
        val a = HistoricalReplayFixtures.paper(root, id = 1)
        val b = HistoricalReplayFixtures.paper(root, id = 2, given = "Eve")
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(a, b))
        val report = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals(1, report.intersections.getValue("conflictingEmails").size)
        assertEquals(0, report.counts["newBindings"])
        Files.write(Paths.get(a.path), "changed".toByteArray())
        val changed = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals("PARTIAL", changed.coverage); assertNull(changed.counts["newBindings"])
        assertTrue("SOURCE_HASH_CHANGED" in changed.reasons)
    }
    @Test fun `frozen replay recomputes real PDF with deterministic work limits not wall time`() {
        val bytes = QualificationFixtures.pdf(pages = 101, lines = listOf("Alice Smith is a Professor."))
        val path = root.toRealPath().resolve("bounded.pdf")
        Files.write(path, bytes)
        val paper = HistoricalReplayFixtures.paper(root).let {
            it.copy(path = path.toString(), kind = ArchiveAttemptKind.PDF_URL,
                source = it.source.copy(bodySha256 = DiscoveryPaperArchiveService.sha(bytes)),
                metadata = it.metadata.copy(authors = listOf(PaperAuthor("Alice", "Smith", null, null))))
        }
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(paper))
        val first = DiscoveryHistoricalReplayTool.compare(frozen)
        val second = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals(first, second)
        assertEquals(DiscoveryHistoricalReplayTool.REPLAY_BUDGET_MODE, frozen.manifest.replayBudgetMode)
        assertEquals(frozen.manifest.replayBudgetMode, first.replayBudgetMode)
        assertEquals("PARTIAL", first.coverage)
        assertNull(first.counts["newBindings"])
        assertTrue("PAGE_LIMIT" in first.reasons)
        assertFalse("SOFT_TIME_LIMIT" in first.reasons)
        assertEquals(100, first.rows.single().analysis!!.coverage.scannedPages)
        assertEquals(101, first.rows.single().analysis!!.coverage.totalPages)
    }
    @Test fun `baseline anchored to other attempt prevents complete delta`() {
        val paper = HistoricalReplayFixtures.paper(root).copy(baselineAttemptIds = listOf(999))
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(paper))
        val report = DiscoveryHistoricalReplayTool.compare(frozen)
        assertEquals("PARTIAL", report.coverage); assertNull(report.counts["newBindings"])
        assertTrue("BASELINE_BODIES_MISSING" in report.reasons)
    }
    @Test fun `default CLI compare never asks for database and apply requires manifest hash`() {
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(HistoricalReplayFixtures.paper(root)))
        DiscoveryHistoricalReplayTool.execute(arrayOf("--manifest", frozen.directory.resolve("manifest.json").toString()))
        assertThrows(IllegalArgumentException::class.java) { DiscoveryHistoricalReplayTool.execute(arrayOf("apply-bindings",
            "--manifest", frozen.directory.resolve("manifest.json").toString(), "--manifest-hash", "0".repeat(64))) }
        assertFalse(Files.exists(frozen.directory.resolve("bindings")))
    }
}
