package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.CompanyMatchStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileDto
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileField
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileFields
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileGroup
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertManualProfilePatch
import com.weibo.talentintroduction.expert.domain.ExpertProfileConflictException
import com.weibo.talentintroduction.expert.domain.ExpertProfileStoreException
import com.weibo.talentintroduction.expert.domain.ResearchAbility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DeadlockLoserDataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.LinkedHashMap

/**
 * 01（I-2/I-3/I-4/I-5–I-23）：19 字段、分组键集、版本冲突与错误映射。
 */
class ExpertManualProfileServiceTest {
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val resolver = Mockito.mock(ExpertProfileIdentityResolver::class.java)
    private val service: ExpertManualProfileService

    init {
        // 无 Spring 代理：真实 TransactionTemplate + 事务管理器替身，回调直接执行。
        service = ExpertManualProfileService(
            jdbc, resolver, TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java))
        )
    }

    private val beijing = ZoneId.of("Asia/Shanghai")

    /** 身份核验替身：Kotlin 非空参数不接受 null matcher，按三层枚举与真实入参逐值打桩。 */
    private fun identity(expertKey: String = "0000-0001", expertId: String = expertKey) {
        ExpertIndexLevel.values().forEach { level ->
            Mockito.`when`(resolver.requireKnown(expertId, level)).thenReturn(expertKey)
        }
    }

    private fun updatesReturn(rows: Int) {
        Mockito.doAnswer { rows }.`when`(jdbc).update(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)
        )
    }

    /** 首次 INSERT 的并发冲突替身：写入 SQL 抛出给定异常（同 key 撞主键或 InnoDB 锁冲突）。 */
    private fun insertThrowsOnInsert(failure: () -> RuntimeException) {
        Mockito.doAnswer {
            val sql = it.arguments[0] as String
            if (sql.startsWith("INSERT")) throw failure() else 0
        }.`when`(jdbc).update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java))
    }

    /** 把真实 RowMapper 跑在受控 ResultSet 上，验证列读取、NULL 语义与时间格式。 */
    private fun persistedRows(
        rows: List<Map<String, String?>>,
        version: Long = 6,
        updatedBy: String = "userB",
        updatedAt: Timestamp = Timestamp.valueOf("2026-10-11 10:18:00.123"),
        expertKey: String = "0000-0001"
    ) {
        Mockito.doAnswer { invocation ->
            val mapper = invocation.getArgument<RowMapper<ExpertManualProfileDto>>(2)
            rows.map { row ->
                val rs = Mockito.mock(ResultSet::class.java)
                Mockito.`when`(rs.getString("expert_key")).thenReturn(expertKey)
                Mockito.`when`(rs.getLong("version")).thenReturn(version)
                Mockito.`when`(rs.getString("updated_by")).thenReturn(updatedBy)
                Mockito.`when`(rs.getTimestamp("updated_at")).thenReturn(updatedAt)
                row.forEach { (column, value) -> Mockito.`when`(rs.getString(column)).thenReturn(value) }
                mapper.mapRow(rs, 0)!!
            }
        }.`when`(jdbc).query(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any<RowMapper<ExpertManualProfileDto>>()
        )
    }

    private fun updates() = Mockito.mockingDetails(jdbc).invocations.filter { it.method.name == "update" }
    private fun updateCalls(): List<Pair<String, MapSqlParameterSource>> =
        updates().map { it.arguments[0] as String to it.arguments[1] as MapSqlParameterSource }
    private fun insertCall(): Pair<String, MapSqlParameterSource>? =
        updateCalls().firstOrNull { it.first.startsWith("INSERT") }
    private fun sqlCalls(name: String) = Mockito.mockingDetails(jdbc).invocations.filter { it.method.name == name }

    private fun blankValues(group: ExpertManualProfileGroup): MutableMap<String, Any?> =
        group.fields.associateTo(LinkedHashMap()) { it.apiName to null }

    private fun groupOf(field: ExpertManualProfileField): ExpertManualProfileGroup =
        ExpertManualProfileGroup.values().first { field in it.fields }

    private fun identityValues(talentCode: Any? = "0007"): MutableMap<String, Any?> =
        blankValues(ExpertManualProfileGroup.IDENTITY).apply { this["talentCode"] = talentCode }

    private fun patch(
        group: ExpertManualProfileGroup,
        values: Map<String, Any?>,
        expectedVersion: Long = 0,
        expertId: String? = "0000-0001",
        level: ExpertIndexLevel = ExpertIndexLevel.RAW,
        username: String = "admin"
    ): ExpertManualProfileDto =
        service.patch(ExpertManualProfilePatch(expertId, level, group, expectedVersion, values), username)

    /** 只断言写入参数的业务字段（不含审计与 CAS 参数）。 */
    private fun businessParams(params: MapSqlParameterSource): Map<String, Any?> =
        params.values.filterKeys { it in ExpertManualProfileFields.BY_API_NAME }

    @Test
    fun `GET without a row returns the default DTO with version zero and never writes`() {
        identity(expertId = " 0000-0001 ")
        persistedRows(rows = emptyList())
        val dto = service.get(" 0000-0001 ", ExpertIndexLevel.CANDIDATE)
        assertEquals(ExpertManualProfileDto.empty("0000-0001"), dto)
        assertEquals(0L, dto.version)
        assertEquals(CompanyMatchStatus.NOT_MATCHED.name, dto.matching.companyMatchStatus)
        assertEquals(emptyList<String>(), dto.matching.matchedCompanies)
        assertNull(dto.updatedBy)
        assertNull(dto.updatedAt)
        assertTrue(updates().isEmpty())
        val read = sqlCalls("query").single()
        assertTrue((read.arguments[0] as String).startsWith("SELECT expert_key"))
        assertTrue((read.arguments[0] as String).contains("FROM expert_manual_profile"))
        assertEquals("0000-0001", (read.arguments[1] as MapSqlParameterSource).getValue("expertKey"))
    }

    @Test
    fun `GET maps every persisted column with Beijing milliseconds and newline joined companies`() {
        identity()
        val full = mapOf(
            ExpertManualProfileFields.TALENT_CODE.column to "0007",
            ExpertManualProfileFields.CHINESE_NAME.column to "测试译名",
            ExpertManualProfileFields.BIRTH_DATE_TEXT.column to "1980",
            ExpertManualProfileFields.DOCUMENT_NUMBER.column to "00001234",
            ExpertManualProfileFields.SOURCE_COUNTRY.column to "法国",
            ExpertManualProfileFields.GRADUATED_SCHOOL.column to "学校甲\n学校乙",
            ExpertManualProfileFields.SCHOOL_RANKING.column to "QS 100",
            ExpertManualProfileFields.PRE_CHINA_ORGANIZATION.column to "历史单位",
            ExpertManualProfileFields.ORGANIZATION_RANKING.column to "世界500强",
            ExpertManualProfileFields.POSTDOC_ORGANIZATION.column to "博士后单位",
            ExpertManualProfileFields.POSTDOC_PERIOD.column to "2018–2020",
            ExpertManualProfileFields.PROFESSIONAL_TITLE.column to "副教授",
            ExpertManualProfileFields.ADMINISTRATIVE_TITLES.column to "主任\n委员",
            ExpertManualProfileFields.INDUSTRY.column to "新能源",
            ExpertManualProfileFields.TECHNICAL_EXPERTISE.column to "储能材料",
            ExpertManualProfileFields.PROGRAM_CATEGORIES.column to "QM/BS",
            ExpertManualProfileFields.RESEARCH_ABILITY.column to ResearchAbility.ABOVE_AVERAGE.name,
            ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to CompanyMatchStatus.MATCHING.name,
            ExpertManualProfileFields.MATCHED_COMPANIES.column to "企业甲\n企业乙"
        )
        persistedRows(rows = listOf(full))
        val dto = service.get("0000-0001", ExpertIndexLevel.RAW)
        assertEquals("0000-0001", dto.expertId)
        assertEquals(6L, dto.version)
        assertEquals("userB", dto.updatedBy)
        assertEquals("2026-10-11T10:18:00.123+08:00", dto.updatedAt)
        assertEquals("0007", dto.identity.talentCode)
        assertEquals("测试译名", dto.identity.chineseName)
        assertEquals("1980", dto.identity.birthDateText)
        assertEquals("00001234", dto.identity.documentNumber)
        assertEquals("法国", dto.identity.sourceCountry)
        assertEquals(listOf("学校甲", "学校乙"), dto.education.graduatedSchool!!.split("\n"))
        assertEquals("副教授", dto.education.professionalTitle)
        assertEquals(listOf("主任", "委员"), dto.education.administrativeTitles!!.split("\n"))
        assertEquals("新能源", dto.technology.industry)
        assertEquals("储能材料", dto.technology.technicalExpertise)
        assertEquals("QM/BS", dto.technology.programCategories)
        assertEquals(ResearchAbility.ABOVE_AVERAGE.name, dto.matching.researchAbility)
        assertEquals(CompanyMatchStatus.MATCHING.name, dto.matching.companyMatchStatus)
        assertEquals(listOf("企业甲", "企业乙"), dto.matching.matchedCompanies)

        Mockito.reset(jdbc)
        identity()
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        val empty = service.get("0000-0001", ExpertIndexLevel.RAW)
        assertEquals(emptyList<String>(), empty.matching.matchedCompanies)
        assertNull(empty.matching.researchAbility)
        assertNull(empty.identity.talentCode)
    }

    @Test
    fun `PATCH updates exactly the requested group columns and returns the reread row`() {
        val requested = LinkedHashMap<ExpertManualProfileGroup, String>()
        val samples = mapOf<ExpertManualProfileGroup, Map<String, Any?>>(
            ExpertManualProfileGroup.IDENTITY to mapOf(
                "talentCode" to "0007", "chineseName" to "测试译名", "birthDateText" to "1980",
                "documentNumber" to "00001234", "sourceCountry" to "法国"
            ),
            ExpertManualProfileGroup.EDUCATION to mapOf(
                "graduatedSchool" to "学校甲", "schoolRanking" to "QS 100", "preChinaOrganization" to "历史单位",
                "organizationRanking" to "世界500强", "postdocOrganization" to "博士后单位",
                "postdocPeriod" to "2018–2020", "professionalTitle" to "副教授", "administrativeTitles" to "主任"
            ),
            ExpertManualProfileGroup.TECHNOLOGY to mapOf(
                "industry" to "新能源", "technicalExpertise" to "储能材料", "programCategories" to "QM/BS"
            ),
            ExpertManualProfileGroup.MATCHING to mapOf(
                "researchAbility" to "STRONG", "companyMatchStatus" to "MATCHED", "matchedCompanies" to listOf("企业甲")
            )
        )
        ExpertManualProfileGroup.values().forEach { group ->
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = samples.getValue(group)
            val dto = patch(group, values, expectedVersion = 5)
            assertEquals(6L, dto.version, "响应必须来自同事务回读")

            val (sql, params) = updateCalls().single()
            val setClause = sql.substringAfter("SET").substringBefore("WHERE")
            assertTrue(sql.startsWith("UPDATE expert_manual_profile"))
            assertTrue(sql.contains("version=version+1"))
            assertTrue(sql.contains("updated_by=:updatedBy"))
            assertTrue(sql.contains("updated_at=:updatedAt"))
            assertTrue(sql.contains("WHERE expert_key=:expertKey AND version=:expectedVersion"))
            group.fields.forEach { field ->
                assertTrue(setClause.contains(field.column), "缺少 ${field.column}")
                assertTrue(setClause.contains(":${field.apiName}"), "缺少参数 :${field.apiName}")
            }
            requested[group] = setClause
            val otherColumns = ExpertManualProfileFields.ALL.filter { it !in group.fields }.map { it.column }
            otherColumns.forEach { column ->
                assertFalse(setClause.contains(column), "组间串写：$column")
            }
            assertEquals(
                values.mapValues { (_, value) -> if (value is List<*>) value.joinToString("\n") else value },
                businessParams(params)
            )
            assertEquals(5L, params.getValue("expectedVersion"))
            assertEquals("0000-0001", params.getValue("expertKey"))
            assertEquals("admin", params.getValue("updatedBy"))
            assertTrue(params.getValue("updatedAt") is java.time.LocalDateTime)
            assertNull(insertCall())
            assertEquals(listOf("update", "query"), Mockito.mockingDetails(jdbc).invocations.map { it.method.name })
        }
        // 四组 SET 逐字覆盖 19 列，一列不多一列不少。
        val covered = ExpertManualProfileFields.ALL.filter { field ->
            requested.values.any { it.contains(field.column) }
        }
        assertEquals(ExpertManualProfileFields.ALL.size, covered.size)
        assertEquals(19, covered.size)
    }

    @Test
    fun `PATCH first insert writes all nineteen columns with defaults and version one`() {
        identity()
        updatesReturn(0)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")), version = 1)
        val dto = patch(
            ExpertManualProfileGroup.TECHNOLOGY,
            mapOf("industry" to "新能源", "technicalExpertise" to "储能材料", "programCategories" to "QM/BS"),
            expectedVersion = 0
        )
        assertEquals(1L, dto.version)
        val (sql, params) = insertCall()!!
        assertTrue(sql.startsWith("INSERT INTO expert_manual_profile"))
        val columns = sql.substringAfter("(").substringBefore(")").split(",").map { it.trim() }
        assertEquals(
            listOf(
                "expert_key", "talent_code", "chinese_name", "birth_date_text", "document_number", "source_country",
                "graduated_school", "school_ranking", "pre_china_organization", "organization_ranking",
                "postdoc_organization", "postdoc_period", "professional_title", "administrative_titles",
                "industry", "technical_expertise", "program_categories",
                "research_ability", "company_match_status", "matched_companies",
                "version", "updated_by", "updated_at"
            ),
            columns
        )
        assertTrue(sql.contains("1, :updatedBy, :updatedAt"))
        assertEquals(
            ExpertManualProfileFields.INSERT_DEFAULTS + mapOf(
                "industry" to "新能源", "technicalExpertise" to "储能材料", "programCategories" to "QM/BS"
            ),
            businessParams(params)
        )
        assertEquals(CompanyMatchStatus.NOT_MATCHED.name, params.getValue("companyMatchStatus"))
        assertNull(params.getValue("talentCode"))
        assertEquals("0000-0001", params.getValue("expertKey"))
        assertEquals("admin", params.getValue("updatedBy"))
    }

    @Test
    fun `PATCH conflicts map to 409 for stale versions and duplicate first inserts`() {
        identity()
        updatesReturn(0)
        val stale = assertThrows(ExpertProfileConflictException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, identityValues(), expectedVersion = 5)
        }
        assertEquals("资料已被更新，请重新加载后再编辑", stale.message)
        assertNull(insertCall())
        assertTrue(sqlCalls("query").isEmpty())

        listOf<Pair<String, () -> RuntimeException>>(
            "duplicate-key" to { DuplicateKeyException("duplicate expert_key") },
            // IT 实测：同 key 并发 INSERT 时 MySQL 8 可能报 ER_LOCK_DEADLOCK，同样必须收敛为 409。
            "deadlock" to { DeadlockLoserDataAccessException("Deadlock found when trying to get lock; try restarting transaction", null) }
        ).forEach { (label, failure) ->
            Mockito.reset(jdbc)
            identity()
            insertThrowsOnInsert(failure)
            val race = assertThrows(ExpertProfileConflictException::class.java, {
                patch(ExpertManualProfileGroup.IDENTITY, identityValues(), expectedVersion = 0)
            }, label)
            assertEquals("资料已被更新，请重新加载后再编辑", race.message)
            assertTrue(insertCall()!!.first.startsWith("INSERT"))
        }
    }

    @Test
    fun `negative expected version is rejected before any write`() {
        identity()
        assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, identityValues(), expectedVersion = -1)
        }
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `group key set is enforced before any write`() {
        identity()
        val full = identityValues()
        val missing = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, full.filterKeys { it != "talentCode" })
        }
        assertTrue(missing.message!!.contains("缺少：talentCode"))
        val extra = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, full + mapOf("industry" to "新能源"))
        }
        assertTrue(extra.message!!.contains("不接受：industry"))
        val nullValues = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            service.patch(
                ExpertManualProfilePatch("0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, 0, null),
                "admin"
            )
        }
        assertEquals("缺少 values", nullValues.message)
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `all nineteen fields accept samples and clear on blank input`() {
        val samples = mapOf<String, Pair<ExpertManualProfileGroup, Any?>>(
            "talentCode" to (ExpertManualProfileGroup.IDENTITY to "0007"),
            "chineseName" to (ExpertManualProfileGroup.IDENTITY to "测试译名"),
            "birthDateText" to (ExpertManualProfileGroup.IDENTITY to "1980"),
            "documentNumber" to (ExpertManualProfileGroup.IDENTITY to "00001234"),
            "sourceCountry" to (ExpertManualProfileGroup.IDENTITY to "法国"),
            "graduatedSchool" to (ExpertManualProfileGroup.EDUCATION to "学校甲\n学校乙"),
            "schoolRanking" to (ExpertManualProfileGroup.EDUCATION to "QS 100"),
            "preChinaOrganization" to (ExpertManualProfileGroup.EDUCATION to "历史单位"),
            "organizationRanking" to (ExpertManualProfileGroup.EDUCATION to "世界500强"),
            "postdocOrganization" to (ExpertManualProfileGroup.EDUCATION to "博士后单位"),
            "postdocPeriod" to (ExpertManualProfileGroup.EDUCATION to "2018–2020"),
            "professionalTitle" to (ExpertManualProfileGroup.EDUCATION to "副教授"),
            "administrativeTitles" to (ExpertManualProfileGroup.EDUCATION to "主任\n委员"),
            "industry" to (ExpertManualProfileGroup.TECHNOLOGY to "新能源"),
            "technicalExpertise" to (ExpertManualProfileGroup.TECHNOLOGY to "储能材料"),
            "programCategories" to (ExpertManualProfileGroup.TECHNOLOGY to "QM/BS"),
            "researchAbility" to (ExpertManualProfileGroup.MATCHING to "STRONG"),
            "companyMatchStatus" to (ExpertManualProfileGroup.MATCHING to "MATCHING"),
            "matchedCompanies" to (ExpertManualProfileGroup.MATCHING to listOf("企业甲", "企业乙"))
        )
        assertEquals(ExpertManualProfileFields.ALL.map { it.apiName }.toSet(), samples.keys)
        samples.forEach { (apiName, sample) ->
            val (group, value) = sample
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = blankValues(group)
            if (group == ExpertManualProfileGroup.MATCHING) values["companyMatchStatus"] = "MATCHING"
            values[apiName] = value
            patch(group, values)
            val expected = when (value) {
                is List<*> -> value.joinToString("\n")
                else -> value
            }
            assertEquals(expected, updateCalls().single().second.getValue(apiName), apiName)

            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val cleared = blankValues(group)
            if (group == ExpertManualProfileGroup.MATCHING) cleared["companyMatchStatus"] = "MATCHING"
            cleared[apiName] = if (apiName == "matchedCompanies") emptyList<String>() else " \r\n\t "
            patch(group, cleared)
            val clearedValue = updateCalls().single().second.getValue(apiName)
            if (apiName == "companyMatchStatus") {
                assertEquals(CompanyMatchStatus.NOT_MATCHED.name, clearedValue)
            } else {
                assertNull(clearedValue, "$apiName 空白应清空为 NULL")
            }
        }
    }

    @Test
    fun `free text fields enforce code point limits without truncation`() {
        val textFields = ExpertManualProfileFields.ALL.filterNot {
            it == ExpertManualProfileFields.BIRTH_DATE_TEXT ||
                it == ExpertManualProfileFields.RESEARCH_ABILITY ||
                it == ExpertManualProfileFields.COMPANY_MATCH_STATUS ||
                it == ExpertManualProfileFields.MATCHED_COMPANIES
        }
        assertEquals(15, textFields.size)
        textFields.forEach { field ->
            val group = groupOf(field)
            val atLimit = "中".repeat(field.maxCodePoints)
            val emojiAtLimit = "😀".repeat(field.maxCodePoints)
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = blankValues(group)
            values[field.apiName] = atLimit
            patch(group, values)
            assertEquals(atLimit, updateCalls().single().second.getValue(field.apiName))
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val emojiValues = blankValues(group)
            emojiValues[field.apiName] = emojiAtLimit
            patch(group, emojiValues)
            assertEquals(
                emojiAtLimit,
                updateCalls().single().second.getValue(field.apiName),
                "${field.apiName} 上限按 code point 计长（${field.maxCodePoints} 个 emoji 合法）"
            )
            listOf(
                "中".repeat(field.maxCodePoints + 1),
                "😀".repeat(field.maxCodePoints + 1),
                " ".repeat(field.maxCodePoints + 1)
            ).forEach { rejected ->
                Mockito.reset(jdbc, resolver)
                identity()
                val overValues = blankValues(group)
                overValues[field.apiName] = rejected
                assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
                    patch(group, overValues)
                }
                assertTrue(updates().isEmpty(), "${field.apiName} 超限不得写库")
            }
        }
    }

    @Test
    fun `single line fields reject newlines while multiline fields normalize and trim`() {
        val singleLine = ExpertManualProfileFields.ALL.filter { !it.multiline }
        singleLine.forEach { field ->
            Mockito.reset(jdbc, resolver)
            identity()
            val group = groupOf(field)
            val values = blankValues(group)
            if (group == ExpertManualProfileGroup.MATCHING) values["companyMatchStatus"] = "MATCHING"
            values[field.apiName] = "第一行\n第二行"
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) { patch(group, values) }
            assertTrue(updates().isEmpty(), "${field.apiName} 单行列必须拒绝换行")
        }
        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        patch(
            ExpertManualProfileGroup.EDUCATION,
            mapOf(
                "graduatedSchool" to " \r\n学校甲\r学校乙\r\n ",
                "schoolRanking" to " QS 100 ",
                "preChinaOrganization" to "\r\n",
                "organizationRanking" to null,
                "postdocOrganization" to null,
                "postdocPeriod" to null,
                "professionalTitle" to null,
                "administrativeTitles" to null
            )
        )
        val params = updateCalls().single().second
        assertEquals("学校甲\n学校乙", params.getValue("graduatedSchool"))
        assertEquals("QS 100", params.getValue("schoolRanking"))
        assertNull(params.getValue("preChinaOrganization"))
    }

    @Test
    fun `birthDateText accepts only years and real dates up to today`() {
        val today = LocalDate.now(beijing)
        val accepted = listOf("0001", today.year.toString(), today.toString(), "1980", "2000-02-29", "1900-01-01")
        val rejected = listOf(
            "0000", "1980-02-30", "2023-04-31", "1900-02-29", "1980-2-3", "1980/01/01", "19800101",
            today.plusDays(1).toString(), "9999-12-31", "10000", "abcd", "1", "19"
        )
        accepted.forEach { value ->
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = blankValues(ExpertManualProfileGroup.IDENTITY)
            values["birthDateText"] = value
            patch(ExpertManualProfileGroup.IDENTITY, values)
            assertEquals(value, updateCalls().single().second.getValue("birthDateText"), value)
        }
        (rejected + listOf(" " + today.plusDays(1).toString())).forEach { value ->
            Mockito.reset(jdbc, resolver)
            identity()
            val values = blankValues(ExpertManualProfileGroup.IDENTITY)
            values["birthDateText"] = value
            val failure = runCatching { patch(ExpertManualProfileGroup.IDENTITY, values) }.exceptionOrNull()
            assertTrue(
                failure is ExpertManualProfileInvalidRequestException,
                "出生日期 $value 必须被拒绝，实际：${failure ?: "被接受"}"
            )
            assertTrue(updates().isEmpty(), value)
        }
        Mockito.reset(jdbc, resolver)
        identity()
        assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            val values = blankValues(ExpertManualProfileGroup.IDENTITY)
            values["birthDateText"] = "1980-13-01"
            patch(ExpertManualProfileGroup.IDENTITY, values)
        }
    }

    @Test
    fun `enums accept exactly the declared values and reject everything else`() {
        ResearchAbility.values().forEach { ability ->
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = blankValues(ExpertManualProfileGroup.MATCHING)
            values["researchAbility"] = ability.name
            values["companyMatchStatus"] = "NOT_MATCHED"
            patch(ExpertManualProfileGroup.MATCHING, values)
            assertEquals(ability.name, updateCalls().single().second.getValue("researchAbility"))
        }
        CompanyMatchStatus.values().forEach { status ->
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val values = blankValues(ExpertManualProfileGroup.MATCHING)
            values["companyMatchStatus"] = status.name
            if (status == CompanyMatchStatus.MATCHED) values["matchedCompanies"] = listOf("企业甲")
            patch(ExpertManualProfileGroup.MATCHING, values)
            assertEquals(status.name, updateCalls().single().second.getValue("companyMatchStatus"))
        }
        listOf("very_strong", "STRONGER", "未评估", "1").forEach { invalid ->
            Mockito.reset(jdbc, resolver)
            identity()
            val values = blankValues(ExpertManualProfileGroup.MATCHING)
            values["researchAbility"] = invalid
            values["companyMatchStatus"] = "NOT_MATCHED"
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) { patch(ExpertManualProfileGroup.MATCHING, values) }
        }
        listOf("matched", "MATCH", "MATCHING_STATUS").forEach { invalid ->
            Mockito.reset(jdbc, resolver)
            identity()
            val values = blankValues(ExpertManualProfileGroup.MATCHING)
            values["companyMatchStatus"] = invalid
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) { patch(ExpertManualProfileGroup.MATCHING, values) }
        }
        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        val blank = blankValues(ExpertManualProfileGroup.MATCHING)
        patch(ExpertManualProfileGroup.MATCHING, blank)
        val params = updateCalls().single().second
        assertNull(params.getValue("researchAbility"))
        assertEquals(CompanyMatchStatus.NOT_MATCHED.name, params.getValue("companyMatchStatus"))
        assertNull(params.getValue("matchedCompanies"))
    }

    @Test
    fun `matchedCompanies normalizes dedups preserves order and requires a company when matched`() {
        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        val values = blankValues(ExpertManualProfileGroup.MATCHING)
        values["companyMatchStatus"] = "MATCHED"
        values["matchedCompanies"] = listOf(" 企业甲 ", "", "  ", "企业甲", "企业乙", "\r\n企业丙\t")
        patch(ExpertManualProfileGroup.MATCHING, values)
        assertEquals("企业甲\n企业乙\n企业丙", updateCalls().single().second.getValue("matchedCompanies"))

        // 改变进度不得隐式清空企业：切回匹配中时企业原样写入。
        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        val changed = blankValues(ExpertManualProfileGroup.MATCHING)
        changed["companyMatchStatus"] = "MATCHING"
        changed["matchedCompanies"] = listOf("企业甲", "企业乙")
        patch(ExpertManualProfileGroup.MATCHING, changed)
        assertEquals("企业甲\n企业乙", updateCalls().single().second.getValue("matchedCompanies"))

        val rejected = listOf<Any?>(
            listOf("企业甲\n企业乙"),
            listOf("企业甲\r\n企业乙"),
            listOf(1),
            listOf(mapOf("name" to "企业甲")),
            "企业甲"
        )
        rejected.forEach { invalid ->
            Mockito.reset(jdbc, resolver)
            identity()
            val invalidValues = blankValues(ExpertManualProfileGroup.MATCHING)
            invalidValues["companyMatchStatus"] = "MATCHING"
            invalidValues["matchedCompanies"] = invalid
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
                patch(ExpertManualProfileGroup.MATCHING, invalidValues)
            }
            assertTrue(updates().isEmpty())
        }
        listOf(null, emptyList<String>(), listOf("  ", "")).forEach { empty ->
            Mockito.reset(jdbc, resolver)
            identity()
            updatesReturn(1)
            persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
            val cleared = blankValues(ExpertManualProfileGroup.MATCHING)
            cleared["companyMatchStatus"] = "MATCHED"
            cleared["matchedCompanies"] = empty
            val ex = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
                patch(ExpertManualProfileGroup.MATCHING, cleared)
            }
            assertEquals("匹配上时必须至少填写一家企业", ex.message)
        }
        val jointLimit = listOf("中".repeat(1998), "b")
        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        val atLimit = blankValues(ExpertManualProfileGroup.MATCHING)
        atLimit["companyMatchStatus"] = "MATCHING"
        atLimit["matchedCompanies"] = jointLimit
        patch(ExpertManualProfileGroup.MATCHING, atLimit)
        assertEquals(jointLimit.joinToString("\n"), updateCalls().single().second.getValue("matchedCompanies"))

        val overLimit = listOf("中".repeat(2000), "b")
        Mockito.reset(jdbc, resolver)
        identity()
        val overValues = blankValues(ExpertManualProfileGroup.MATCHING)
        overValues["companyMatchStatus"] = "MATCHING"
        overValues["matchedCompanies"] = overLimit
        assertThrows(ExpertManualProfileInvalidRequestException::class.java) { patch(ExpertManualProfileGroup.MATCHING, overValues) }
        assertTrue(updates().isEmpty())
    }

    @Test
    fun `wrong value types actor identity and store failures never leak internals or write invalid data`() {
        Mockito.reset(jdbc, resolver)
        identity()
        listOf(1, true, mapOf("a" to "b"), listOf("a")).forEach { invalid ->
            val values = blankValues(ExpertManualProfileGroup.IDENTITY)
            values["talentCode"] = invalid
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) { patch(ExpertManualProfileGroup.IDENTITY, values) }
        }
        assertTrue(updates().isEmpty())

        listOf("", "  ", "u".repeat(65)).forEach { actor ->
            assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
                patch(ExpertManualProfileGroup.IDENTITY, identityValues(), username = actor)
            }
        }
        assertTrue(updates().isEmpty())

        // 证件号等字段值绝不进入异常消息。
        val secret = "P1234567890SECRET"
        val ex = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            val values = blankValues(ExpertManualProfileGroup.IDENTITY)
            values["documentNumber"] = "$secret".repeat(10)
            patch(ExpertManualProfileGroup.IDENTITY, values)
        }
        assertFalse(ex.message!!.contains(secret))
        assertTrue(ex.message!!.contains("documentNumber"))
    }

    @Test
    fun `database failures map to store errors without internal sql and successful writes are never silent`() {
        Mockito.reset(jdbc, resolver)
        identity()
        Mockito.`when`(jdbc.query(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any<RowMapper<ExpertManualProfileDto>>()))
            .thenThrow(DataAccessResourceFailureException("jdbc:mysql://host/expert_manual_profile SELECT failed"))
        val readFailure = assertThrows(ExpertProfileStoreException::class.java) {
            service.get("0000-0001", ExpertIndexLevel.RAW)
        }
        assertEquals("人工档案保存失败，请稍后重试", readFailure.message)
        assertFalse(readFailure.message!!.contains("SELECT"))
        assertFalse(readFailure.message!!.contains("expert_manual_profile"))
        assertFalse(readFailure.message!!.contains("jdbc"))
        assertEquals("jdbc:mysql://host/expert_manual_profile SELECT failed", readFailure.cause?.message)

        Mockito.reset(jdbc, resolver)
        identity()
        Mockito.doAnswer { throw DataAccessResourceFailureException("UPDATE expert_manual_profile failed") }
            .`when`(jdbc).update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java))
        val writeFailure = assertThrows(ExpertProfileStoreException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, identityValues())
        }
        assertEquals("人工档案保存失败，请稍后重试", writeFailure.message)
        assertFalse(writeFailure.message!!.contains("UPDATE"))

        Mockito.reset(jdbc, resolver)
        identity()
        updatesReturn(1)
        persistedRows(rows = emptyList())
        val emptyAfterWrite = assertThrows(ExpertProfileStoreException::class.java) {
            patch(ExpertManualProfileGroup.IDENTITY, identityValues())
        }
        assertEquals("人工档案保存失败，请稍后重试", emptyAfterWrite.message)
    }

    @Test
    fun `responses echo the canonical key returned by the identity resolver`() {
        identity(expertKey = "EMAIL-abc@x.com", expertId = " email-ABC@x.com ")
        updatesReturn(1)
        persistedRows(
            rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")),
            expertKey = "EMAIL-abc@x.com"
        )
        val dto = service.patch(
            ExpertManualProfilePatch(" email-ABC@x.com ", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, 3,
                mapOf("talentCode" to null, "chineseName" to null, "birthDateText" to null,
                    "documentNumber" to null, "sourceCountry" to null)),
            "admin"
        )
        assertEquals("EMAIL-abc@x.com", dto.expertId)
    }

    @Test
    fun `other group columns are never touched by an update`() {
        identity()
        updatesReturn(1)
        persistedRows(rows = listOf(mapOf(ExpertManualProfileFields.COMPANY_MATCH_STATUS.column to "NOT_MATCHED")))
        patch(ExpertManualProfileGroup.IDENTITY, identityValues())
        val (sql, params) = updateCalls().single()
        assertFalse(sql.contains("research_ability"))
        assertFalse(sql.contains("matched_companies"))
        assertFalse(sql.contains("company_match_status"))
        assertFalse(params.values.keys.contains("researchAbility"))
        assertFalse(params.values.keys.contains("matchedCompanies"))
    }
}
