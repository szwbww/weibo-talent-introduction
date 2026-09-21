package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import java.security.MessageDigest

/** I-2: 检查点状态。EXHAUSTED 表示上一周期已把来源翻到底，下一个定时扫描周期可重开。 */
enum class CheckpointState { ACTIVE, EXHAUSTED }

/**
 * I-2: 一行 `discovery_source_cursor` 解码后的内容。
 *
 * - [cursor] 是下次请求该用的真实游标；本编码内 ACTIVE + null 表示「尚未消费任何页」。
 * - [state] 为 EXHAUSTED 时不带游标，下一次扫描周期从头开始。
 */
data class SourceCheckpoint(val cursor: String?, val state: CheckpointState) {
    val exhausted: Boolean get() = state == CheckpointState.EXHAUSTED

    companion object {
        /** 无检查点：key 从未写过，或历史值不可采信。按「从第一页开始」处理。 */
        val EMPTY = SourceCheckpoint(null, CheckpointState.ACTIVE)
    }
}

/**
 * I-2: 查询隔离与检查点编码的唯一实现点。
 *
 * `source_name` = `<SOURCE>:v2:<24 位 SHA256>`，hash 覆盖规范化后的真实查询条件、scope、年份、
 * 页大小与查询版本，**绝不包含临时 cursor**（否则每次翻页都会换 key）。总长受
 * [MAX_SOURCE_NAME_LENGTH] 约束（V32 的 `source_name VARCHAR(50)`）。
 *
 * `cursor_value` 使用版本化 envelope `v2|ACTIVE|<cursor>` / `v2|EXHAUSTED|`。旧条件写下的历史行
 * （裸 source_name + 裸游标）不会被 [decode] 采信，只保留在表中作备份。
 *
 * c4 的 CORE offset envelope 与 ORCID 分片游标必须复用本对象，不得另造第二套编码。
 */
object DiscoveryCheckpointCodec {
    const val QUERY_VERSION = "v2"

    /** V32 中 `discovery_source_cursor.source_name` 为 VARCHAR(50)。 */
    const val MAX_SOURCE_NAME_LENGTH = 50

    /** 24 位十六进制 = SHA-256 前 12 字节。 */
    const val HASH_LENGTH = 24

    private const val KEY_SEPARATOR = ':'
    private const val FIELD_SEPARATOR = '|'
    private const val CRITERIA_SEPARATOR = ';'
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /**
     * 本次查询条件对应的检查点 key。`criteria.cursor` 是临时翻页位置，不参与 hash；
     * 累计论文数（`papersProcessedTotal`）同样不参与 —— 不能用它反推恢复位置。
     */
    fun sourceKey(sourceName: String, criteria: PaperSearchCriteria): String {
        val key = "$sourceName$KEY_SEPARATOR$QUERY_VERSION$KEY_SEPARATOR${hash(canonicalCriteria(criteria))}"
        require(key.length <= MAX_SOURCE_NAME_LENGTH) {
            "检查点 key 超出 $MAX_SOURCE_NAME_LENGTH 字符: $key"
        }
        return key
    }

    /**
     * 规范化实际查询条件：空白/空列表归一，集合类条件排序，使同一查询的不同书写顺序得到同一个 key。
     */
    fun canonicalCriteria(criteria: PaperSearchCriteria): String = listOf(
        "kw=" + normalizeSet(criteria.keywords),
        "aff=" + normalizeSet(criteria.affiliationKeywords),
        "ex=" + normalizeSet(criteria.excludeCountries),
        "y=" + criteria.publicationYearFrom + "-" + criteria.publicationYearTo,
        "oa=" + criteria.openAccessOnly,
        "ps=" + criteria.pageSize,
        "scope=" + criteria.subjectScope.orEmpty().trim(),
        "src=" + normalizeSet(criteria.sources)
    ).joinToString(CRITERIA_SEPARATOR.toString())

    /** I-2: 把检查点写成版本化 envelope。 */
    fun encode(cursor: String?, exhausted: Boolean): String {
        val state = if (exhausted) CheckpointState.EXHAUSTED else CheckpointState.ACTIVE
        return "$QUERY_VERSION$FIELD_SEPARATOR${state.name}$FIELD_SEPARATOR${cursor.orEmpty()}"
    }

    /**
     * I-2: 只采信本编码写出的 envelope。返回 null 表示该值不是本版本的检查点（旧行/损坏值），
     * 调用方必须按「无检查点」处理并保留原值，禁止静默挪用。
     */
    fun decode(stored: String?): SourceCheckpoint? {
        if (stored == null) return null
        val parts = stored.split(FIELD_SEPARATOR, limit = 3)
        if (parts.size != 3 || parts[0] != QUERY_VERSION) return null
        val state = when (parts[1]) {
            CheckpointState.ACTIVE.name -> CheckpointState.ACTIVE
            CheckpointState.EXHAUSTED.name -> CheckpointState.EXHAUSTED
            else -> return null
        }
        return SourceCheckpoint(parts[2].takeIf { it.isNotEmpty() }, state)
    }

    private fun normalizeSet(values: List<String>): String =
        values.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            .joinToString(",")

    private fun hash(canonical: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(HASH_LENGTH)
        for (i in 0 until HASH_LENGTH / 2) {
            val b = digest[i].toInt()
            sb.append(HEX_DIGITS[(b shr 4) and 0xF]).append(HEX_DIGITS[b and 0xF])
        }
        return sb.toString()
    }
}
