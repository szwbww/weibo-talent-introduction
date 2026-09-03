package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagIntentCoverage
import com.weibo.talentintroduction.rag.domain.RagKbMeta
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import com.weibo.talentintroduction.rag.domain.RagPhraseGroup
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import com.weibo.talentintroduction.rag.repository.RagFactRepository
import com.weibo.talentintroduction.rag.repository.RagRetrievalRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.annotation.PostConstruct

/**
 * 计划 01 (T4): RAG 语料的不可变内存快照 + G-2 指纹门禁。
 *
 * 两个互不相同的入口（I-3，P0-3 的直接钉死点，绝不合并）：
 * - [verifyAndPublish]：**只在启动时调用**（[@PostConstruct]）。读全表 → 算指纹 →
 *   与 `rag_kb_meta.fingerprint` 比对，不一致抛 [IllegalStateException]（消息含
 *   期望值与实际值）终止启动 → 发布快照。**只读，绝不写 meta**。
 * - [republish]：**运营编辑时调用**（04 的 `RagFactAdminService`）。在同一个事务内：
 *   执行传入的写操作 → 重读全表 → 重算指纹 → UPDATE `rag_kb_meta` → 事务提交后才发布
 *   新快照（I-3b）。**全程不做旧指纹比对** —— 否则第一次合法编辑就会被自己的门禁拦下。
 *
 * [load] 是两者共用的纯读部分：不比对、不写库。
 */
@Service
class RagKnowledgeBase(
    private val factRepository: RagFactRepository,
    private val ruleRepository: RagRetrievalRuleRepository,
    private val jdbc: NamedParameterJdbcTemplate
) {

    private val log = LoggerFactory.getLogger(RagKnowledgeBase::class.java)

    /** I-6: 一经发布不得就地修改；整体替换。 */
    @Volatile
    private var snapshot: RagCorpusSnapshot? = null

    /**
     * I-3: 启动门禁。@PostConstruct 在 Spring 创建 bean 后、应用就绪前执行；
     * 指纹不符抛异常 → 上下文初始化失败 → 应用启动失败（G-2、需求 observable
     * outcome 2）。不写 meta、不做任何事务。
     */
    @PostConstruct
    fun verifyAndPublish() {
        val loaded = load()
        val meta = metaRow()
        if (loaded.fingerprint != meta.fingerprint) {
            throw IllegalStateException(
                "RAG corpus fingerprint mismatch: expected ${meta.fingerprint} " +
                    "(rag_kb_meta, fact_count=${meta.factCount}) but computed " +
                    "${loaded.fingerprint} from ${loaded.facts.size} rag_fact rows. " +
                    "The corpus drifted from the V112 machine-generated seed; restore " +
                    "rag_fact from scripts/export_rag_kb_sql.py before starting."
            )
        }
        publish(loaded)
        log.info(
            "RAG knowledge base verified and published: {} facts, fingerprint {}",
            loaded.facts.size,
            loaded.fingerprint
        )
    }

    /**
     * I-3 / I-3b: 运营编辑的唯一原子入口（04 I-20）。事务内执行 [writeInTx] →
     * 重读全表重算指纹 → UPDATE `rag_kb_meta`；快照在事务提交后（afterCommit）
     * 才发布 —— 回滚时快照保持旧值。不做旧指纹比对，返回新指纹。
     */
    @Transactional
    fun republish(writeInTx: () -> Unit): String {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw IllegalStateException("republish must run inside an active transaction")
        }
        writeInTx()
        val loaded = load()
        jdbc.update(
            """
            UPDATE rag_kb_meta
               SET fingerprint = :fingerprint, fact_count = :factCount
             WHERE id = 1
            """,
            MapSqlParameterSource("fingerprint", loaded.fingerprint)
                .addValue("factCount", loaded.facts.size)
        )
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                publish(loaded)
                log.info(
                    "RAG corpus republished after commit: {} facts, fingerprint {}",
                    loaded.facts.size,
                    loaded.fingerprint
                )
            }
        })
        return loaded.fingerprint
    }

    /** 当前已发布快照；未初始化（启动门禁未过）时抛 [IllegalStateException]。 */
    fun snapshot(): RagCorpusSnapshot =
        snapshot ?: throw IllegalStateException("RAG knowledge base is not initialized yet")

    /** I-2: enabled=true 且归一状态非 DISABLED 的事实（种子 45 条中为 44 条）。 */
    fun enabledFacts(): List<RagFact> =
        snapshot().facts.filter { it.enabled && it.effectiveStatus() != "DISABLED" }

    /** 当前快照指纹（03b I-41 发送新鲜度比对用）。 */
    fun fingerprint(): String = snapshot().fingerprint

    /**
     * I-6: 45 行一次性读入不可变快照的纯读部分（verifyAndPublish 与 republish 共用）。
     * 读全表 → 校验 I-1 → 计算指纹。不比对 meta、不写库。
     */
    private fun load(): RagCorpusSnapshot {
        val facts = factRepository.findAllByOrderBySortOrderAscIdAsc()
        validateFactCodes(facts)
        val rules = ruleRepository.loadAll()
        return RagCorpusSnapshot(
            facts = facts.toList(),
            phraseGroups = rules.phraseGroups,
            intentCoverage = rules.intentCoverage,
            mandatoryRules = rules.mandatoryRules,
            exclusions = rules.exclusions,
            fingerprint = fingerprintOf(facts)
        )
    }

    /** I-1: fact_code 唯一、格式恒为 `KB-<AREA>-<NNN>` 且与 area/seq 列自洽。 */
    private fun validateFactCodes(facts: List<RagFact>) {
        val pattern = Regex("^KB-([A-Z]+)-(\\d{3})$")
        facts.forEach { fact ->
            val match = pattern.matchEntire(fact.factCode)
                ?: throw IllegalStateException(
                    "rag_fact.fact_code '${fact.factCode}' violates KB-<AREA>-<NNN> (I-1)"
                )
            val expected = "KB-${fact.area}-${fact.seq.toString().padStart(3, '0')}"
            if (match.groupValues[1] != fact.area || fact.factCode != expected) {
                throw IllegalStateException(
                    "rag_fact.fact_code '${fact.factCode}' is not self-consistent with " +
                        "area=${fact.area}, seq=${fact.seq} (I-1); expected '$expected'"
                )
            }
        }
        val codes = facts.map { it.factCode }
        if (codes.size != codes.toSet().size) {
            throw IllegalStateException("rag_fact.fact_code must be unique (I-1)")
        }
    }

    private fun metaRow(): RagKbMeta =
        jdbc.query(
            "SELECT fingerprint, fact_count FROM rag_kb_meta WHERE id = 1",
            RowMapper { rs, _ ->
                RagKbMeta(
                    fingerprint = rs.getString("fingerprint"),
                    factCount = rs.getInt("fact_count")
                )
            }
        ).firstOrNull()
            ?: throw IllegalStateException(
                "rag_kb_meta has no row: the V112 migration must seed the singleton " +
                    "rag_kb_meta row with the corpus fingerprint"
            )

    private fun publish(loaded: RagCorpusSnapshot) {
        snapshot = loaded
    }

    /**
     * G-2: 语料指纹 = 45 行 `rag_fact` 按 `fact_code` 升序、字段有序序列化后的
     * SHA-256 前 16 位。算法在此处唯一实现；`scripts/export_rag_kb_sql.py` 的
     * `_corpus_fingerprint` 必须与之逐字等价，由 `RagKnowledgeBaseTest` 用固定
     * 样本交叉验证。
     */
    private fun fingerprintOf(facts: List<RagFact>): String {
        val canonical = facts
            .sortedBy { it.factCode }
            .joinToString(separator = "\n") { fact ->
                listOf(
                    fact.factCode,
                    fact.area,
                    fact.seq.toString(),
                    fact.title,
                    fact.category,
                    fact.questionVariants,
                    fact.keywords,
                    fact.answer,
                    fact.coverageKeys,
                    fact.replyPolicy,
                    fact.status,
                    fact.riskLevel,
                    fact.renderMode,
                    fact.sourceRefs,
                    fact.legacyRuleId?.toString() ?: "",
                    if (fact.enabled) "1" else "0",
                    fact.sortOrder.toString()
                ).joinToString(separator = "|")
            }
        return sha256Hex16(canonical)
    }

    private fun sha256Hex16(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

/**
 * I-6 不可变快照：facts（按 sort_order、id 升序 = 语料顺序）+ 四张规则表 +
 * 指纹。一经发布不得就地修改；republish 成功后整体替换为新实例。
 */
data class RagCorpusSnapshot(
    val facts: List<RagFact>,
    val phraseGroups: List<RagPhraseGroup>,
    val intentCoverage: List<RagIntentCoverage>,
    val mandatoryRules: List<RagMandatoryRule>,
    val exclusions: List<RagPrefilterExclusion>,
    val fingerprint: String
)
