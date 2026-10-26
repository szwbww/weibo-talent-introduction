package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.service.PdfEmailExtractor
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class DiscoveryPaperAnalysisService(private val archive: DiscoveryPaperArchiveService,
    pdf: PdfEmailExtractor, qualification: PaperQualificationEvidenceExtractor,
    private val mapper: ObjectMapper) {
    private val analyzer = PaperBodyAnalyzer(pdf, qualification, mapper)
    private val running = ConcurrentHashMap.newKeySet<Long>()
    internal var beforeParse: () -> Unit = {}
    fun analyze(id: Long, force: Boolean = false): JsonNode {
        if (!running.add(id)) throw ArchiveException("ANALYSIS_RUNNING", 409)
        try {
            archive.analysisLease(id).use { lease ->
                val record = lease.record
                val metadata = mapper.readTree(record.metadataJson)
                val first = record.outcomeJson?.let { mapper.readTree(it).path("first") }
                val available = lease.fileSet.map { it.first }.toSet()
                val (baselineCoverage, missing) = analyzer.baselineCoverage(first, available)
                val hashes = lease.bodies.map { it.hash }.sorted()
                val versions = analyzer.versions()
                record.analysisJson?.let { cached ->
                    val value = mapper.readTree(cached)
                    if (!force && value.path("schemaVersion").asInt() == 1 && value.path("analysisVersion").asInt() == 2 &&
                        value.path("parserVersion") == mapper.valueToTree<JsonNode>(versions) &&
                        value.path("inputBodyHashes") == mapper.valueToTree<JsonNode>(hashes) &&
                        value.path("baselineCoverage").asText() == baselineCoverage &&
                        value.path("missingBodies") == mapper.valueToTree<JsonNode>(missing)) return value
                }
                beforeParse()
                val bodies = lease.bodies.map { PaperBodyInput(it.hash, it.attempt.kind, it.input.readBytes()) }
                val tree = mapper.valueToTree<JsonNode>(analyzer.compute(bodies, metadata, first, available, Instant.now(), versions))
                archive.commitAnalysis(lease, tree)
                return tree
            }
        } finally { running.remove(id) }
    }
}
