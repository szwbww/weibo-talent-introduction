package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDateTime
import java.util.Locale

/**
 * rnd-v2-2026 专家研发类型分类策略（I1-1 ~ I1-4，M-2）。
 *
 * 确定性的纯函数：同一输入 + 同一 [clock] 产出逐字一致的结果；只有
 * [ExpertClassification.classifiedAt] 来自注入的 [clock]。
 *
 * 归一化、证据、分数、优先级、类型与 `sendable` 只在这里计算；词表和计分规则
 * 以子计划 01 的规范清单为准（临床词、医学域词、制药/器械白名单均为逐字规范，
 * 不做语义改写）。本服务不读取 email、国籍、性别等字段，degree 不作为临床判据。
 */
@Service
class ExpertClassificationService(
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * 对 [profile] 产出版本化分类。纯函数：不写库、不写 ES、不依赖外部状态。
     */
    fun classify(profile: ExpertProfile): ExpertClassification {
        val normalized = normalizeInputs(profile)
        val production = productionScore(normalized)
        val research = researchScore(normalized)
        val positiveEvidence = (production.evidence + research.evidence).distinct()

        val clinical = CLINICAL_OCCUPATION_TERMS.any {
            containsTerm(normalized.employment, it) || containsTerm(normalized.keyword, it)
        }
        val medicalDomain = MEDICAL_DOMAIN_TERMS.any { term ->
            normalized.allText.any { containsTerm(it, term) }
        }
        val whitelist = WHITELIST_TERMS.any { term ->
            normalized.allText.any { containsTerm(it, term) }
        }
        val serviceRole = SERVICE_ROLE_TERMS.any { containsTerm(normalized.employment, it) }

        // 判定优先级逐字固定（计划 Task 2 第 9 条）。
        val (type, negativeEvidence) = when {
            clinical -> ExpertType.SERVICE_ONLY to listOf(E_CLINICAL_ROLE)
            medicalDomain && !whitelist -> ExpertType.OUT_OF_SCOPE to listOf(E_MEDICAL_DOMAIN_NO_WHITELIST)
            production.score >= PRODUCTION_THRESHOLD && research.score >= RESEARCH_THRESHOLD ->
                ExpertType.HYBRID_RND to emptyList()
            production.score >= PRODUCTION_THRESHOLD -> ExpertType.PRODUCTION_RND to emptyList()
            research.score >= RESEARCH_THRESHOLD -> ExpertType.ACADEMIC_RND to emptyList()
            serviceRole -> ExpertType.SERVICE_ONLY to listOf(E_SERVICE_ROLE)
            else -> ExpertType.UNKNOWN to listOf(E_INSUFFICIENT_EVIDENCE)
        }

        return ExpertClassification(
            type = type,
            productionScore = production.score,
            researchScore = research.score,
            positiveEvidence = positiveEvidence,
            negativeEvidence = negativeEvidence,
            version = VERSION,
            sourceFingerprint = fingerprint(normalized),
            classifiedAt = LocalDateTime.now(clock)
        )
    }

    private data class NormalizedInputs(
        val employment: String,
        val keyword: String,
        val researchFields: String,
        val institution: String,
        val recentWorkTitles: String,
        val patentTitles: String,
        val lastPublicationYear: Int?,
        val hIndex: Int?,
        val worksCount: Int?
    ) {
        val allText: List<String> =
            listOf(employment, keyword, researchFields, institution, recentWorkTitles, patentTitles)
    }

    private data class ScoreResult(val score: Int, val evidence: List<String>)

    private fun normalizeInputs(profile: ExpertProfile): NormalizedInputs =
        NormalizedInputs(
            employment = normalizeText(profile.employment),
            keyword = normalizeText(profile.keyword),
            researchFields = normalizeText(profile.researchFields),
            institution = normalizeText(profile.institution),
            recentWorkTitles = normalizeList(profile.recentWorkTitles),
            patentTitles = normalizeList(profile.patentTitles),
            lastPublicationYear = profile.lastPublicationYear,
            hIndex = profile.hIndex,
            worksCount = profile.worksCount
        )

    /** 生产分：五类证据各最多计一次，封顶 100（计划 Task 2 第 7 条）。 */
    private fun productionScore(n: NormalizedInputs): ScoreResult {
        val evidence = mutableListOf<String>()
        var score = 0

        if (n.patentTitles.isNotEmpty()) {
            score += 45
            evidence += E_PROD_PATENTS
        }
        if (PRODUCTION_ROLE_TERMS.any { containsTerm(n.employment, it) }) {
            score += 35
            evidence += E_PROD_ROLE
        }
        if (PRODUCTION_THEME_TERMS.any { containsTerm(n.keyword, it) || containsTerm(n.researchFields, it) }) {
            score += 20
            evidence += E_PROD_THEME
        }
        if (COMPANY_TERMS.any { containsTerm(n.employment, it) || containsTerm(n.institution, it) }) {
            score += 15
            evidence += E_PROD_COMPANY
        }
        if (WHITELIST_TERMS.any { term -> n.allText.any { containsTerm(it, term) } }) {
            score += 15
            evidence += E_PROD_WHITELIST
        }

        return ScoreResult(minOf(score, SCORE_CAP), evidence)
    }

    /** 科研分：六类证据各最多计一次，封顶 100（计划 Task 2 第 8 条）。 */
    private fun researchScore(n: NormalizedInputs): ScoreResult {
        val evidence = mutableListOf<String>()
        var score = 0

        if (n.lastPublicationYear != null && n.lastPublicationYear >= RECENT_PAPER_CUTOFF_YEAR) {
            score += 35
            evidence += E_RESEARCH_RECENT_PUBLICATION
        }
        if (n.recentWorkTitles.isNotEmpty()) {
            score += 25
            evidence += E_RESEARCH_TITLES
        }
        if (n.researchFields.isNotEmpty()) {
            score += 15
            evidence += E_RESEARCH_FIELDS
        }
        if (n.hIndex != null && n.hIndex >= 20) {
            score += 20
            evidence += E_RESEARCH_HINDEX
        } else if (n.hIndex != null && n.hIndex >= 5) {
            score += 10
            evidence += E_RESEARCH_HINDEX
        }
        if (n.worksCount != null && n.worksCount >= 20) {
            score += 15
            evidence += E_RESEARCH_WORKS
        } else if (n.worksCount != null && n.worksCount >= 5) {
            score += 10
            evidence += E_RESEARCH_WORKS
        }
        if (RESEARCH_INSTITUTION_TERMS.any {
                containsTerm(n.employment, it) || containsTerm(n.institution, it)
            }
        ) {
            score += 20
            evidence += E_RESEARCH_INSTITUTION
        }

        return ScoreResult(minOf(score, SCORE_CAP), evidence)
    }

    /**
     * sourceFingerprint：对"归一化后的六个文本字段 + 三个数值字段"做 SHA-256
     * （I1-4）。分隔符使用 NUL，归一化后任何字段都不可能含 NUL，保证一一映射。
     */
    private fun fingerprint(n: NormalizedInputs): String {
        val input = listOf(
            n.employment, n.keyword, n.researchFields, n.institution,
            n.recentWorkTitles, n.patentTitles,
            n.lastPublicationYear?.toString() ?: "",
            n.hIndex?.toString() ?: "",
            n.worksCount?.toString() ?: ""
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 词边界匹配：英文字母/数字术语要求前后为非 [A-Za-z0-9_] 字符（容忍复数 s）；
     * 含 CJK 的术语（中文没有空格分词）按子串匹配。
     */
    private fun containsTerm(normalizedText: String, term: String): Boolean {
        if (term.isEmpty()) return false
        if (term.any { it.code in CJK_RANGE }) {
            return normalizedText.contains(term)
        }
        val escaped = Regex.escape(term)
        return Regex("(?<![A-Za-z0-9_])$escaped(?:s)?(?![A-Za-z0-9_])")
            .containsMatchIn(normalizedText)
    }

    private fun normalizeList(list: List<String>?): String =
        (list ?: emptyList()).map { normalizeText(it) }.filter { it.isNotEmpty() }.joinToString(" ")

    /** 归一化：NFKC → Locale.ROOT lowercase → 标点转空格 → 连续空白折叠（计划 Task 2 第 1 条）。 */
    private fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            if (Character.isLetterOrDigit(ch)) sb.append(ch) else sb.append(' ')
        }
        return sb.toString().lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()
    }

    companion object {
        const val VERSION = "rnd-v2-2026"

        const val RECENT_PAPER_CUTOFF_YEAR = 2021
        const val PRODUCTION_THRESHOLD = 50
        const val RESEARCH_THRESHOLD = 50
        const val SCORE_CAP = 100

        private val CJK_RANGE = 0x4E00..0x9FFF
        private val WHITESPACE = Regex("\\s+")

        // 稳定证据 code（声明顺序即输出顺序）。
        private const val E_PROD_PATENTS = "PROD_PATENTS"
        private const val E_PROD_ROLE = "PROD_ROLE"
        private const val E_PROD_THEME = "PROD_THEME"
        private const val E_PROD_COMPANY = "PROD_COMPANY"
        private const val E_PROD_WHITELIST = "PROD_WHITELIST"
        private const val E_RESEARCH_RECENT_PUBLICATION = "RESEARCH_RECENT_PUBLICATION"
        private const val E_RESEARCH_TITLES = "RESEARCH_TITLES"
        private const val E_RESEARCH_FIELDS = "RESEARCH_FIELDS"
        private const val E_RESEARCH_HINDEX = "RESEARCH_HINDEX"
        private const val E_RESEARCH_WORKS = "RESEARCH_WORKS"
        private const val E_RESEARCH_INSTITUTION = "RESEARCH_INSTITUTION"
        private const val E_CLINICAL_ROLE = "CLINICAL_ROLE"
        private const val E_MEDICAL_DOMAIN_NO_WHITELIST = "MEDICAL_DOMAIN_NO_WHITELIST"
        private const val E_SERVICE_ROLE = "SERVICE_ROLE"
        private const val E_INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"

        // 明确临床职业词（I1-2；计划 Task 2 第 2 条，逐字规范）。
        // 裸 doctor / MD / PhD / doctorate 明确禁止加入（第 3 条）。
        private val CLINICAL_OCCUPATION_TERMS = listOf(
            "physician", "clinician", "surgeon", "general practitioner", "family practitioner",
            "attending physician", "resident physician", "consultant physician", "hospitalist",
            "cardiologist", "oncologist", "neurologist", "psychiatrist", "pediatrician",
            "paediatrician", "dermatologist", "anesthesiologist", "anaesthesiologist", "radiologist",
            "urologist", "gynecologist", "gynaecologist", "obstetrician", "ophthalmologist",
            "otolaryngologist", "gastroenterologist", "nephrologist", "pulmonologist",
            "rheumatologist", "endocrinologist", "hematologist", "haematologist", "pathologist",
            "dentist", "nurse practitioner",
            "医师", "医生", "主任医师", "副主任医师", "主治医师", "住院医师",
            "临床医生", "外科医生", "内科医生", "牙医"
        )

        // 医学域词（I1-3；计划 Task 2 第 4 条，逐字规范）。
        private val MEDICAL_DOMAIN_TERMS = listOf(
            "medicine", "medical", "clinical", "patient", "hospital", "healthcare",
            "therapy", "disease", "oncology", "cardiology", "surgery", "diagnosis",
            "dental", "dentistry", "odontology", "oral medicine", "oral surgery",
            "orthodontics", "endodontics", "periodontics", "prosthodontics",
            "neurology", "neurological", "neurosurgery", "neuroscience",
            "pediatrics", "paediatrics", "pediatric", "paediatric", "neonatology", "child health",
            "dermatology", "anesthesiology", "anaesthesiology", "radiology", "urology",
            "gynecology", "gynaecology", "obstetrics", "ophthalmology", "otolaryngology",
            "gastroenterology", "nephrology", "pulmonology", "rheumatology", "endocrinology",
            "hematology", "haematology", "pathology", "psychiatry", "internal medicine",
            "family medicine", "emergency medicine", "nursing", "public health", "critical care",
            "intensive care",
            "医学", "临床", "患者", "医院", "疾病", "诊疗", "牙科", "口腔", "牙医学",
            "正畸", "牙髓", "牙周", "口腔颌面", "神经科", "神经学", "神经科学", "神经外科",
            "儿科", "小儿科", "儿童医学", "新生儿", "皮肤科", "麻醉科", "放射科", "泌尿科",
            "妇产科", "眼科", "耳鼻喉", "消化科", "肾脏科", "呼吸科", "风湿科", "内分泌科",
            "血液科", "病理科", "精神科", "内科", "急诊", "重症", "护理", "公共卫生"
        )

        // 制药研发白名单（I1-3；计划 Task 2 第 5 条，逐字规范）。
        private val PHARMA_WHITELIST_TERMS = listOf(
            "drug discovery", "drug development", "drug design", "drug delivery",
            "medicinal chemistry", "pharmaceutical", "pharmacology", "pharmacokinetics",
            "pharmacodynamics", "toxicology", "formulation", "preclinical", "biologics",
            "biopharma", "vaccine development", "therapeutic development", "target validation",
            "small molecule", "antibody development",
            "药物研发", "药物发现", "药物设计", "药剂", "制剂", "药代动力学", "毒理", "生物制药", "疫苗研发"
        )

        // 医疗器械研发白名单（I1-3；计划 Task 2 第 6 条，逐字规范）。
        private val DEVICE_WHITELIST_TERMS = listOf(
            "medical device", "biomedical engineering", "biomaterial", "biosensor",
            "in vitro diagnostic", "IVD", "medical imaging", "implant", "prosthetic",
            "surgical robot", "medical instrumentation", "diagnostic device", "wearable medical",
            "医疗器械", "生物医学工程", "生物材料", "生物传感器", "体外诊断", "医学影像", "植入物", "假体", "手术机器人"
        )

        private val WHITELIST_TERMS = PHARMA_WHITELIST_TERMS + DEVICE_WHITELIST_TERMS

        // 明确 R&D/产品/工艺/设计/制造岗位（计划 Task 2 第 7 条）。
        private val PRODUCTION_ROLE_TERMS = listOf(
            "r d", "research and development", "rd", "product", "process", "design",
            "manufacturing", "engineer",
            "研发", "产品", "工艺", "设计", "制造", "工程师"
        )

        // 产品/工程/制造主题（计划 Task 2 第 7 条）。
        private val PRODUCTION_THEME_TERMS = listOf(
            "product", "engineering", "manufacturing", "production",
            "产品", "工程", "制造", "生产"
        )

        // 公司形态词（计划 Task 2 第 7 条）。
        private val COMPANY_TERMS = listOf(
            "inc", "incorporated", "ltd", "limited", "gmbh", "corp", "corporation",
            "company", "pharma", "biotech", "medtech",
            "有限公司", "制药", "生物科技", "医疗科技"
        )

        // 大学/研究所/实验室/教授/研究员/科学家语义（计划 Task 2 第 8 条）。
        private val RESEARCH_INSTITUTION_TERMS = listOf(
            "university", "institute", "laboratory", "lab", "professor", "researcher",
            "scientist", "research fellow", "academy", "college", "faculty",
            "大学", "研究所", "研究院", "实验室", "教授", "研究员", "科学家", "学院"
        )

        // 服务岗位（计划 Task 2 第 9 条：存在服务岗位且两分均不足 → SERVICE_ONLY）。
        private val SERVICE_ROLE_TERMS = listOf(
            "service", "services", "support", "sales", "consultant", "coordinator", "administrative",
            "客服", "服务", "销售", "顾问", "专员"
        )
    }
}
