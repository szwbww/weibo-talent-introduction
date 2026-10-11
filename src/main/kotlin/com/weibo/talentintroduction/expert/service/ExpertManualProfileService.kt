package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.CompanyMatchStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileDto
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileField
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileFields
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileGroup
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfilePatch
import com.weibo.talentintroduction.expert.domain.ExpertProfileConflictException
import com.weibo.talentintroduction.expert.domain.ExpertProfileStoreException
import com.weibo.talentintroduction.expert.domain.ResearchAbility
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.LinkedHashSet

/**
 * 01（I-2/I-3/I-4）：`expert_manual_profile` 的唯一写者与读入口。
 *
 * - 组 SQL 全部是编译期常量、参数化；客户端列名永不拼接（未知键在写事务前就被拒绝）；
 * - 身份核验与参数验证都在事务写入前完成；成功响应在同一事务内回读；
 * - 字符串按 Unicode code point 计长、统一 CRLF/CR→LF 并 trim、不静默截断；单行列拒绝换行；
 * - `updated_by` 只取会话用户，`updated_at` 只取服务端 Asia/Shanghai 毫秒；
 * - 数据库失败统一转 [ExpertProfileStoreException]，内部 SQL/路径不外泄，也不记录字段值。
 */
@Service
class ExpertManualProfileService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val identityResolver: ExpertProfileIdentityResolver,
    private val transactionTemplate: TransactionTemplate
) {
    /** GET：无行返回默认 DTO（version=0）且不插入（I-3）。 */
    fun get(expertId: String?, level: ExpertIndexLevel): ExpertManualProfileDto {
        val expertKey = identityResolver.requireKnown(expertId, level)
        return read(expertKey) ?: ExpertManualProfileDto.empty(expertKey)
    }

    /** PATCH：只更新指定组全部字段，expectedVersion CAS，成功 version+1 并返回完整最新 DTO。 */
    fun patch(command: ExpertManualProfilePatch, username: String): ExpertManualProfileDto {
        val expertKey = identityResolver.requireKnown(command.expertId, command.level)
        requireActor(username)
        if (command.expectedVersion < 0L) {
            throw ExpertManualProfileInvalidRequestException("expectedVersion 必须为非负整数")
        }
        val values = validatedGroupValues(command.group, command.values)
        val now = LocalDateTime.now(BEIJING).truncatedTo(ChronoUnit.MILLIS)

        val result = try {
            transactionTemplate.execute {
                val updated = jdbcTemplate.update(
                    updateSql(command.group),
                    updateParams(expertKey, command.group, values, command.expectedVersion, username, now)
                )
                if (updated == 0) {
                    if (command.expectedVersion != 0L) throw profileConflict()
                    insertFirstRow(expertKey, values, username, now)
                }
                read(expertKey) ?: throw ExpertProfileStoreException(STORE_FAILED_MESSAGE)
            }
        } catch (ex: ExpertManualProfileException) {
            throw ex
        } catch (ex: DataAccessException) {
            throw ExpertProfileStoreException(STORE_FAILED_MESSAGE, ex)
        }
        return result ?: throw ExpertProfileStoreException(STORE_FAILED_MESSAGE)
    }

    private fun insertFirstRow(expertKey: String, values: Map<String, Any?>, username: String, now: LocalDateTime) {
        try {
            jdbcTemplate.update(INSERT_SQL, insertParams(expertKey, values, username, now))
        } catch (ex: DuplicateKeyException) {
            // 并发首次插入：另一连接已插入同一 key（I-3：首次插入冲突与旧版本统一 409）。
            throw profileConflict()
        } catch (ex: PessimisticLockingFailureException) {
            // 同一 expert_key 并发 INSERT 时 InnoDB 可能在唯一索引上先报死锁/锁等待（IT 实测 ER_LOCK_DEADLOCK），
            // 本表除主键外没有其它唯一约束，因此这同样是「首次插入冲突」，与旧版本统一 409。
            throw profileConflict()
        }
    }

    private fun read(expertKey: String): ExpertManualProfileDto? =
        try {
            jdbcTemplate.query(
                SELECT_SQL,
                MapSqlParameterSource("expertKey", expertKey),
                ROW_MAPPER
            ).firstOrNull()
        } catch (ex: DataAccessException) {
            throw ExpertProfileStoreException(STORE_FAILED_MESSAGE, ex)
        }

    private fun updateParams(
        expertKey: String,
        group: ExpertManualProfileGroup,
        values: Map<String, Any?>,
        expectedVersion: Long,
        username: String,
        now: LocalDateTime
    ): MapSqlParameterSource {
        val params = MapSqlParameterSource()
        group.fields.forEach { params.addValue(it.apiName, values[it.apiName]) }
        return params
            .addValue("expertKey", expertKey)
            .addValue("expectedVersion", expectedVersion)
            .addValue("updatedBy", username)
            .addValue("updatedAt", now)
    }

    /** INSERT 完整默认值 + 本组字段，version 固定为 1（I-3）。 */
    private fun insertParams(
        expertKey: String,
        values: Map<String, Any?>,
        username: String,
        now: LocalDateTime
    ): MapSqlParameterSource =
        MapSqlParameterSource(ExpertManualProfileFields.INSERT_DEFAULTS)
            .addValues(values)
            .addValue("expertKey", expertKey)
            .addValue("updatedBy", username)
            .addValue("updatedAt", now)

    private fun updateSql(group: ExpertManualProfileGroup): String = when (group) {
        ExpertManualProfileGroup.IDENTITY -> UPDATE_IDENTITY_SQL
        ExpertManualProfileGroup.EDUCATION -> UPDATE_EDUCATION_SQL
        ExpertManualProfileGroup.TECHNOLOGY -> UPDATE_TECHNOLOGY_SQL
        ExpertManualProfileGroup.MATCHING -> UPDATE_MATCHING_SQL
    }

    private fun requireActor(username: String) {
        if (username.isBlank() || username.codePointCount(0, username.length) > MAX_ACTOR_LENGTH) {
            throw ExpertManualProfileInvalidRequestException("用户名不能为空且不能超过${MAX_ACTOR_LENGTH}个字符")
        }
    }

    private fun profileConflict() = ExpertProfileConflictException(CONFLICT_MESSAGE)

    /**
     * 组键集校验 + 逐字段规范化（写事务之前）。键集必须与白名单完全一致：
     * 缺失或额外键都是 400，客户端无法用额外键触碰其它组的列。
     */
    private fun validatedGroupValues(group: ExpertManualProfileGroup, raw: Map<String, Any?>?): Map<String, Any?> {
        if (raw == null) throw ExpertManualProfileInvalidRequestException("缺少 values")
        val expectedNames = group.fields.map { it.apiName }
        val missing = expectedNames.filterNot { raw.containsKey(it) }
        val extra = raw.keys.filterNot { it in expectedNames }
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            throw ExpertManualProfileInvalidRequestException(
                buildString {
                    append("values 必须且只能包含 ${group.wire} 组全部字段")
                    if (missing.isNotEmpty()) append("；缺少：").append(missing.joinToString(","))
                    if (extra.isNotEmpty()) append("；不接受：").append(extra.joinToString(","))
                }
            )
        }
        val normalized = LinkedHashMap<String, Any?>()
        group.fields.forEach { normalized[it.apiName] = normalizeField(it, raw[it.apiName]) }
        if (group == ExpertManualProfileGroup.MATCHING &&
            normalized[ExpertManualProfileFields.COMPANY_MATCH_STATUS.apiName] == CompanyMatchStatus.MATCHED.name &&
            (normalized[ExpertManualProfileFields.MATCHED_COMPANIES.apiName] as? String).isNullOrEmpty()
        ) {
            throw ExpertManualProfileInvalidRequestException("匹配上时必须至少填写一家企业")
        }
        return normalized
    }

    private fun normalizeField(field: ExpertManualProfileField, value: Any?): Any? = when (field) {
        ExpertManualProfileFields.BIRTH_DATE_TEXT -> normalizeBirthDateText(field, value)
        ExpertManualProfileFields.RESEARCH_ABILITY -> normalizeResearchAbility(field, value)
        ExpertManualProfileFields.COMPANY_MATCH_STATUS -> normalizeCompanyMatchStatus(field, value)
        ExpertManualProfileFields.MATCHED_COMPANIES -> normalizeMatchedCompanies(value)
        else -> normalizeText(field, value)
    }

    /** 普通文本：按**原始输入**的 Unicode code point 计长（I-4 先计长再规范化）、CRLF/CR→LF、trim、空即清空（NULL）；单行列拒绝换行。 */
    private fun normalizeText(field: ExpertManualProfileField, value: Any?): String? {
        if (value == null) return null
        if (value !is String) {
            throw ExpertManualProfileInvalidRequestException("字段 ${field.apiName} 必须是字符串")
        }
        requireWithinLimit(field, value)
        val text = value.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return null
        if (!field.multiline && text.indexOf('\n') >= 0) {
            throw ExpertManualProfileInvalidRequestException("字段 ${field.apiName} 不能包含换行")
        }
        return text
    }

    /** I-7：只接受 YYYY 或真实 YYYY-MM-DD；年份 0001–当年；完整日期不得晚于当天；年份不补 1 月 1 日。 */
    private fun normalizeBirthDateText(field: ExpertManualProfileField, value: Any?): String? {
        val text = normalizeText(field, value) ?: return null
        val today = LocalDate.now(BEIJING)
        if (YEAR_PATTERN.matches(text)) {
            val year = text.toInt()
            if (year !in 1..today.year) {
                throw ExpertManualProfileInvalidRequestException("出生年份必须在 0001 到 ${today.year} 之间")
            }
            return text
        }
        if (DATE_PATTERN.matches(text)) {
            val date = try {
                LocalDate.parse(text)
            } catch (ex: DateTimeParseException) {
                throw ExpertManualProfileInvalidRequestException("出生日期必须是真实存在的日期")
            }
            if (date.year !in 1..today.year) {
                throw ExpertManualProfileInvalidRequestException("出生年份必须在 0001 到 ${today.year} 之间")
            }
            if (date.isAfter(today)) {
                throw ExpertManualProfileInvalidRequestException("出生日期不能晚于当天")
            }
            return text
        }
        throw ExpertManualProfileInvalidRequestException("出生日期只接受 YYYY 或 YYYY-MM-DD")
    }

    /** I-21：六档枚举；空值清空为 NULL（未评估）。 */
    private fun normalizeResearchAbility(field: ExpertManualProfileField, value: Any?): String? {
        val text = normalizeText(field, value) ?: return null
        return ResearchAbility.values().firstOrNull { it.name == text }?.name
            ?: throw ExpertManualProfileInvalidRequestException(
                "科研能力只接受 ${ResearchAbility.values().joinToString("/") { it.name }}"
            )
    }

    /** I-22：三档枚举；空值清空为默认 NOT_MATCHED（NOT NULL 列，未匹配即空态）。 */
    private fun normalizeCompanyMatchStatus(field: ExpertManualProfileField, value: Any?): String {
        val text = normalizeText(field, value) ?: return CompanyMatchStatus.NOT_MATCHED.name
        return CompanyMatchStatus.values().firstOrNull { it.name == text }?.name
            ?: throw ExpertManualProfileInvalidRequestException(
                "匹配企业进度只接受 ${CompanyMatchStatus.values().joinToString("/") { it.name }}"
            )
    }

    /**
     * I-23：字符串数组；去每项首尾空白、去空行、精确去重、保留顺序；库内换行连接；
     * 每项不得含换行；总文本 ≤2000；空数组等价于数据库 NULL。
     */
    private fun normalizeMatchedCompanies(value: Any?): String? {
        if (value == null) return null
        if (value !is List<*>) {
            throw ExpertManualProfileInvalidRequestException("字段 matchedCompanies 必须是字符串数组")
        }
        val items = LinkedHashSet<String>()
        value.forEach { item ->
            if (item !is String) {
                throw ExpertManualProfileInvalidRequestException("字段 matchedCompanies 的每一项必须是字符串")
            }
            val text = item.replace("\r\n", "\n").replace('\r', '\n').trim()
            if (text.isEmpty()) return@forEach
            if (text.indexOf('\n') >= 0) {
                throw ExpertManualProfileInvalidRequestException("字段 matchedCompanies 的每一项都不能包含换行")
            }
            items.add(text)
        }
        if (items.isEmpty()) return null
        val joined = items.joinToString("\n")
        requireWithinLimit(ExpertManualProfileFields.MATCHED_COMPANIES, joined)
        return joined
    }

    private fun requireWithinLimit(field: ExpertManualProfileField, text: String) {
        if (text.codePointCount(0, text.length) > field.maxCodePoints) {
            throw ExpertManualProfileInvalidRequestException("字段 ${field.apiName} 超过长度上限 ${field.maxCodePoints}")
        }
    }

    companion object {
        private const val MAX_ACTOR_LENGTH = 64
        private const val STORE_FAILED_MESSAGE = "人工档案保存失败，请稍后重试"
        const val CONFLICT_MESSAGE = "资料已被更新，请重新加载后再编辑"

        private val BEIJING = ZoneId.of("Asia/Shanghai")
        private val BEIJING_OFFSET = ZoneOffset.ofHours(8)
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private val YEAR_PATTERN = Regex("\\d{4}")
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

        private val SELECT_SQL = """
            SELECT expert_key, talent_code, chinese_name, birth_date_text, document_number, source_country,
                   graduated_school, school_ranking, pre_china_organization, organization_ranking,
                   postdoc_organization, postdoc_period, professional_title, administrative_titles,
                   industry, technical_expertise, program_categories,
                   research_ability, company_match_status, matched_companies,
                   version, updated_by, updated_at
              FROM expert_manual_profile
             WHERE expert_key=:expertKey
        """.trimIndent()

        private val UPDATE_IDENTITY_SQL = """
            UPDATE expert_manual_profile
               SET talent_code=:talentCode,
                   chinese_name=:chineseName,
                   birth_date_text=:birthDateText,
                   document_number=:documentNumber,
                   source_country=:sourceCountry,
                   version=version+1,
                   updated_by=:updatedBy,
                   updated_at=:updatedAt
             WHERE expert_key=:expertKey AND version=:expectedVersion
        """.trimIndent()

        private val UPDATE_EDUCATION_SQL = """
            UPDATE expert_manual_profile
               SET graduated_school=:graduatedSchool,
                   school_ranking=:schoolRanking,
                   pre_china_organization=:preChinaOrganization,
                   organization_ranking=:organizationRanking,
                   postdoc_organization=:postdocOrganization,
                   postdoc_period=:postdocPeriod,
                   professional_title=:professionalTitle,
                   administrative_titles=:administrativeTitles,
                   version=version+1,
                   updated_by=:updatedBy,
                   updated_at=:updatedAt
             WHERE expert_key=:expertKey AND version=:expectedVersion
        """.trimIndent()

        private val UPDATE_TECHNOLOGY_SQL = """
            UPDATE expert_manual_profile
               SET industry=:industry,
                   technical_expertise=:technicalExpertise,
                   program_categories=:programCategories,
                   version=version+1,
                   updated_by=:updatedBy,
                   updated_at=:updatedAt
             WHERE expert_key=:expertKey AND version=:expectedVersion
        """.trimIndent()

        private val UPDATE_MATCHING_SQL = """
            UPDATE expert_manual_profile
               SET research_ability=:researchAbility,
                   company_match_status=:companyMatchStatus,
                   matched_companies=:matchedCompanies,
                   version=version+1,
                   updated_by=:updatedBy,
                   updated_at=:updatedAt
             WHERE expert_key=:expertKey AND version=:expectedVersion
        """.trimIndent()

        private val INSERT_SQL = """
            INSERT INTO expert_manual_profile (
                expert_key, talent_code, chinese_name, birth_date_text, document_number, source_country,
                graduated_school, school_ranking, pre_china_organization, organization_ranking,
                postdoc_organization, postdoc_period, professional_title, administrative_titles,
                industry, technical_expertise, program_categories,
                research_ability, company_match_status, matched_companies,
                version, updated_by, updated_at
            ) VALUES (
                :expertKey, :talentCode, :chineseName, :birthDateText, :documentNumber, :sourceCountry,
                :graduatedSchool, :schoolRanking, :preChinaOrganization, :organizationRanking,
                :postdocOrganization, :postdocPeriod, :professionalTitle, :administrativeTitles,
                :industry, :technicalExpertise, :programCategories,
                :researchAbility, :companyMatchStatus, :matchedCompanies,
                1, :updatedBy, :updatedAt
            )
        """.trimIndent()

        private val ROW_MAPPER = RowMapper { rs, _ ->
            ExpertManualProfileDto(
                expertId = rs.getString("expert_key"),
                version = rs.getLong("version"),
                identity = ExpertManualProfileDto.Identity(
                    talentCode = rs.getString("talent_code"),
                    chineseName = rs.getString("chinese_name"),
                    birthDateText = rs.getString("birth_date_text"),
                    documentNumber = rs.getString("document_number"),
                    sourceCountry = rs.getString("source_country")
                ),
                education = ExpertManualProfileDto.Education(
                    graduatedSchool = rs.getString("graduated_school"),
                    schoolRanking = rs.getString("school_ranking"),
                    preChinaOrganization = rs.getString("pre_china_organization"),
                    organizationRanking = rs.getString("organization_ranking"),
                    postdocOrganization = rs.getString("postdoc_organization"),
                    postdocPeriod = rs.getString("postdoc_period"),
                    professionalTitle = rs.getString("professional_title"),
                    administrativeTitles = rs.getString("administrative_titles")
                ),
                technology = ExpertManualProfileDto.Technology(
                    industry = rs.getString("industry"),
                    technicalExpertise = rs.getString("technical_expertise"),
                    programCategories = rs.getString("program_categories")
                ),
                matching = ExpertManualProfileDto.Matching(
                    researchAbility = rs.getString("research_ability"),
                    companyMatchStatus = rs.getString("company_match_status"),
                    matchedCompanies = rs.getString("matched_companies")
                        ?.split("\n")
                        ?: emptyList()
                ),
                updatedBy = rs.getString("updated_by"),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
                    .atOffset(BEIJING_OFFSET)
                    .format(TIME_FORMAT)
            )
        }
    }
}
