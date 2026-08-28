package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExpertClassificationServiceTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-15T10:30:00Z"), ZoneOffset.UTC)
    private val service = ExpertClassificationService(clock = fixedClock)

    private fun profile(
        employment: String? = null,
        keyword: String? = null,
        researchFields: String? = null,
        institution: String? = null,
        lastPublicationYear: Int? = null,
        hIndex: Int? = null,
        worksCount: Int? = null,
        recentWorkTitles: List<String>? = null,
        patentTitles: List<String>? = null
    ) = ExpertProfile(
        orcidId = "0000-0001",
        email = "expert@example.com",
        givenNames = null,
        familyNames = null,
        country = null,
        employment = employment,
        keyword = keyword,
        researchFields = researchFields,
        institution = institution,
        lastPublicationYear = lastPublicationYear,
        hIndex = hIndex,
        worksCount = worksCount,
        recentWorkTitles = recentWorkTitles,
        patentTitles = patentTitles
    )

    private fun classification(type: ExpertType): ExpertClassification =
        ExpertClassification(
            type = type,
            productionScore = 60,
            researchScore = 60,
            positiveEvidence = emptyList(),
            negativeEvidence = emptyList(),
            version = "rnd-v1-2026",
            sourceFingerprint = "a".repeat(64),
            classifiedAt = LocalDateTime.of(2026, 1, 15, 10, 30)
        )

    // ---- I1-1: 类型与序列化 ----

    @ParameterizedTest
    @CsvSource(
        "PRODUCTION_RND",
        "ACADEMIC_RND",
        "HYBRID_RND",
        "SERVICE_ONLY",
        "OUT_OF_SCOPE",
        "UNKNOWN"
    )
    fun `classification retains the given type (I1-1)`(typeName: String) {
        val type = ExpertType.valueOf(typeName)
        assertEquals(type, classification(type).type)
    }

    @Test
    fun `json serialization emits type and no sendable key (I1-1)`() {
        val mapper = ObjectMapper().registerModule(JavaTimeModule())
        val json = mapper.writeValueAsString(classification(ExpertType.PRODUCTION_RND))
        assertTrue(json.contains("\"type\":\"PRODUCTION_RND\""), "serialized JSON must contain type: $json")
        assertFalse(json.contains("sendable"), "serialized JSON must not contain sendable: $json")

        val node = mapper.readTree(json)
        assertEquals("PRODUCTION_RND", node.path("type").asText())
    }

    // ---- I1-2: 临床职业最高优先级 ----

    @Test
    fun `explicit clinical role wins over high research and production scores (I1-2)`() {
        val result = service.classify(
            profile(
                employment = "Surgeon",
                researchFields = "drug development",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Advances in Oncology"),
                hIndex = 30,
                worksCount = 50,
                patentTitles = listOf("Surgical device patent")
            )
        )
        assertEquals(ExpertType.SERVICE_ONLY, result.type)
        assertTrue(result.negativeEvidence.contains("CLINICAL_ROLE"))
    }

    @Test
    fun `bare doctor MD PhD doctorate are not clinical evidence (I1-2)`() {
        val result = service.classify(
            profile(
                employment = "MD, PhD",
                keyword = "doctorate",
                researchFields = "drug development",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Drug targets"),
                institution = "University"
            )
        )
        assertNotEquals(ExpertType.SERVICE_ONLY, result.type)
        assertFalse(result.negativeEvidence.contains("CLINICAL_ROLE"))
    }

    // ---- I1-3: 医学范围正向白名单 ----

    @Test
    fun `medical domain without pharma or device whitelist is OUT_OF_SCOPE even with high research score (I1-3)`() {
        val result = service.classify(
            profile(
                researchFields = "oncology",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Tumor biology"),
                hIndex = 30,
                worksCount = 50
            )
        )
        assertEquals(ExpertType.OUT_OF_SCOPE, result.type)
        assertTrue(result.negativeEvidence.contains("MEDICAL_DOMAIN_NO_WHITELIST"))
    }

    @Test
    fun `clinical specialty affiliations without pharma or device evidence are OUT_OF_SCOPE`() {
        val affiliations = listOf(
            "Department of Dentistry, University",
            "Department of Neurology, University",
            "Department of Pediatrics, University"
        )

        affiliations.forEach { affiliation ->
            val result = service.classify(
                profile(
                    employment = affiliation,
                    institution = "University",
                    lastPublicationYear = 2026
                )
            )

            assertEquals(ExpertType.OUT_OF_SCOPE, result.type, affiliation)
            assertTrue(result.negativeEvidence.contains("MEDICAL_DOMAIN_NO_WHITELIST"), affiliation)
        }
    }

    @Test
    fun `adding drug development or medical device whitelist flips OUT_OF_SCOPE to an rnd type (I1-3)`() {
        val base = profile(
            researchFields = "oncology",
            lastPublicationYear = 2025,
            recentWorkTitles = listOf("Tumor biology"),
            hIndex = 30,
            worksCount = 50
        )
        assertEquals(ExpertType.OUT_OF_SCOPE, service.classify(base).type)

        val drug = base.copy(employment = "drug discovery researcher")
        assertEquals(ExpertType.ACADEMIC_RND, service.classify(drug).type)

        val device = base.copy(researchFields = "medical device")
        assertEquals(ExpertType.ACADEMIC_RND, service.classify(device).type)
    }

    // ---- A1-1 人工验收样本 ----

    @Test
    fun `A1-1 fixtures classify as expected`() {
        // ① 明确临床医生，即使有近期论文/专利
        val surgeon = service.classify(
            profile(
                employment = "surgeon",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Surgery advances"),
                patentTitles = listOf("Surgical tool")
            )
        )
        assertEquals(ExpertType.SERVICE_ONLY, surgeon.type)

        // ② 药物研发科研人员：大学实验室、近期论文、专利外的学术信号
        val academic = service.classify(
            profile(
                researchFields = "drug development",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Kinase inhibitors"),
                hIndex = 10,
                worksCount = 20,
                institution = "University Laboratory"
            )
        )
        assertEquals(ExpertType.ACADEMIC_RND, academic.type)

        // ③ 医疗科技公司研发工程师，医疗器械主题 + 专利
        val production = service.classify(
            profile(
                employment = "R&D Engineer, MedTech Ltd",
                researchFields = "medical device",
                patentTitles = listOf("Implantable sensor")
            )
        )
        assertEquals(ExpertType.PRODUCTION_RND, production.type)

        // ④ 非医学领域的科研人员
        val systems = service.classify(
            profile(
                researchFields = "distributed systems",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Consensus protocols"),
                institution = "University Laboratory"
            )
        )
        assertEquals(ExpertType.ACADEMIC_RND, systems.type)

        // ⑤ 除 ORCID 外所有分类输入为空
        val unknown = service.classify(profile())
        assertEquals(ExpertType.UNKNOWN, unknown.type)
    }

    // ---- I1-4: 阈值、封顶、确定性与指纹 ----

    @Test
    fun `production threshold boundary 50 reaches PRODUCTION_RND while 45 stays below`() {
        // 45：仅专利（PROD_PATENTS）
        val below = service.classify(profile(patentTitles = listOf("A patent")))
        assertEquals(45, below.productionScore)
        assertNotEquals(ExpertType.PRODUCTION_RND, below.type)

        // 50：岗位 35 + 白名单 15
        val at = service.classify(profile(employment = "R&D Engineer", researchFields = "drug development"))
        assertEquals(50, at.productionScore)
        assertEquals(ExpertType.PRODUCTION_RND, at.type)
    }

    @Test
    fun `research threshold boundary 50 reaches ACADEMIC_RND while 45 stays below`() {
        // 45：近期论文 35 + hIndex 10
        val below = service.classify(profile(lastPublicationYear = 2025, hIndex = 10))
        assertEquals(45, below.researchScore)
        assertNotEquals(ExpertType.ACADEMIC_RND, below.type)

        // 50：近期论文 35 + researchFields 15
        val at = service.classify(
            profile(lastPublicationYear = 2025, researchFields = "distributed systems")
        )
        assertEquals(50, at.researchScore)
        assertEquals(ExpertType.ACADEMIC_RND, at.type)
    }

    @Test
    fun `scores cap at 100 (I1-4)`() {
        val result = service.classify(
            profile(
                employment = "R&D Engineer, MedTech Ltd",
                keyword = "product engineering manufacturing",
                researchFields = "medical device",
                institution = "Biotech Inc",
                lastPublicationYear = 2025,
                hIndex = 30,
                worksCount = 100,
                recentWorkTitles = listOf("A", "B", "C"),
                patentTitles = listOf("P1", "P2")
            )
        )
        assertEquals(100, result.productionScore)
        assertEquals(100, result.researchScore)
    }

    @Test
    fun `same input and same clock produce byte-identical results (I1-4)`() {
        val p = profile(
            employment = "R&D Engineer",
            researchFields = "medical device",
            lastPublicationYear = 2025,
            recentWorkTitles = listOf("Implant design"),
            patentTitles = listOf("Device patent")
        )
        val a = service.classify(p)
        val b = service.classify(p)
        assertEquals(a, b)
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30), a.classifiedAt)
    }

    @Test
    fun `only classifiedAt changes when the injected clock changes (I1-4)`() {
        val p = profile(employment = "Surgeon")
        val first = service.classify(p)
        val second = ExpertClassificationService(
            clock = Clock.fixed(Instant.parse("2026-02-01T08:00:00Z"), ZoneOffset.UTC)
        ).classify(p)
        assertEquals(first.copy(classifiedAt = second.classifiedAt), second)
        assertNotEquals(first.classifiedAt, second.classifiedAt)
    }

    @Test
    fun `sourceFingerprint is a stable 64-char sha256 and changes with input (I1-4)`() {
        val p = profile(employment = "R&D Engineer", researchFields = "medical device")
        val a = service.classify(p)
        val b = service.classify(p)
        assertEquals(a.sourceFingerprint, b.sourceFingerprint)
        assertEquals(64, a.sourceFingerprint.length)
        assertTrue(a.sourceFingerprint.matches(Regex("[0-9a-f]{64}")))

        val changedText = service.classify(p.copy(employment = "Product Engineer"))
        assertNotEquals(a.sourceFingerprint, changedText.sourceFingerprint)

        val changedNumeric = service.classify(p.copy(hIndex = 12))
        assertNotEquals(a.sourceFingerprint, changedNumeric.sourceFingerprint)
    }

    // ---- 归一化与词边界 ----

    @Test
    fun `input normalization folds case punctuation and fullwidth forms before matching`() {
        val result = service.classify(
            profile(
                employment = "Ｒ＆Ｄ Ｅｎｇｉｎｅｅｒ",
                researchFields = "Medical-Device",
                patentTitles = listOf("device patent")
            )
        )
        assertEquals(ExpertType.PRODUCTION_RND, result.type)
    }

    @Test
    fun `english terms respect word boundaries`() {
        // "surgery" 嵌在 "microsurgery" 中不算医学域
        val embedded = service.classify(
            profile(
                researchFields = "microsurgery",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Deep learning"),
                hIndex = 30
            )
        )
        assertNotEquals(ExpertType.OUT_OF_SCOPE, embedded.type)

        // 独立 "surgery" 触发医学域 → 无白名单 → OUT_OF_SCOPE
        val standalone = service.classify(
            profile(
                researchFields = "surgery",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Deep learning"),
                hIndex = 30
            )
        )
        assertEquals(ExpertType.OUT_OF_SCOPE, standalone.type)
    }

    @Test
    fun `chinese clinical and medical domain terms match`() {
        val clinician = service.classify(profile(employment = "外科医生"))
        assertEquals(ExpertType.SERVICE_ONLY, clinician.type)

        val medicalOnly = service.classify(
            profile(
                keyword = "临床医学",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("研究"),
                hIndex = 30
            )
        )
        assertEquals(ExpertType.OUT_OF_SCOPE, medicalOnly.type)
    }

    @Test
    fun `chinese whitelist terms route to rnd types`() {
        val drug = service.classify(
            profile(
                researchFields = "药物研发",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("新药靶点"),
                hIndex = 15
            )
        )
        assertEquals(ExpertType.ACADEMIC_RND, drug.type)

        val device = service.classify(
            profile(employment = "生物医学工程", patentTitles = listOf("一种医疗器械"))
        )
        assertEquals(ExpertType.PRODUCTION_RND, device.type)
    }

    // ---- 证据与其余优先级 ----

    @Test
    fun `evidence codes are stable enumerated codes in declaration order (I1-4)`() {
        val result = service.classify(
            profile(
                employment = "R&D Engineer, MedTech Ltd",
                keyword = "product engineering",
                researchFields = "medical device",
                institution = "Research Institute",
                lastPublicationYear = 2025,
                hIndex = 30,
                worksCount = 100,
                recentWorkTitles = listOf("A"),
                patentTitles = listOf("P1")
            )
        )
        assertEquals(
            listOf(
                "PROD_PATENTS", "PROD_ROLE", "PROD_THEME", "PROD_COMPANY", "PROD_WHITELIST",
                "RESEARCH_RECENT_PUBLICATION", "RESEARCH_TITLES", "RESEARCH_FIELDS",
                "RESEARCH_HINDEX", "RESEARCH_WORKS", "RESEARCH_INSTITUTION"
            ),
            result.positiveEvidence
        )
    }

    @Test
    fun `hybrid rnd when both scores reach threshold`() {
        val result = service.classify(
            profile(
                employment = "R&D Engineer",
                researchFields = "drug development",
                lastPublicationYear = 2025,
                recentWorkTitles = listOf("Kinase"),
                hIndex = 20,
                patentTitles = listOf("Device")
            )
        )
        // production: 岗位 35 + 白名单 15 + 专利 45 = 95；research: 35+25+15+20 = 95
        assertEquals(ExpertType.HYBRID_RND, result.type)
    }

    @Test
    fun `service position with insufficient scores is SERVICE_ONLY`() {
        val result = service.classify(profile(employment = "Customer Service Representative"))
        assertEquals(ExpertType.SERVICE_ONLY, result.type)
        assertEquals(listOf("SERVICE_ROLE"), result.negativeEvidence)
    }

    @Test
    fun `UNKNOWN carries INSUFFICIENT_EVIDENCE negative evidence`() {
        val result = service.classify(profile())
        assertEquals(ExpertType.UNKNOWN, result.type)
        assertEquals(listOf("INSUFFICIENT_EVIDENCE"), result.negativeEvidence)
        assertTrue(result.positiveEvidence.isEmpty())
    }

    @Test
    fun `classify is a pure function and never reads email nationality or gender`() {
        val base = profile(
            employment = "R&D Engineer",
            researchFields = "medical device",
            patentTitles = listOf("P")
        )
        val a = service.classify(base)
        val withPersonalData = service.classify(
            base.copy(
                email = "somebody.else@example.com",
                nationality = "Other",
                degree = "PhD"
            )
        )
        assertEquals(a, withPersonalData)
    }
}
