package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class MeetingRecordException(val status: HttpStatus, message: String) : RuntimeException(message)
data class MeetingRecordInput(
    val expertId: String? = null,
    val level: ExpertIndexLevel = ExpertIndexLevel.RAW,
    val title: String = "",
    val time: String? = null,
    val participants: List<String> = emptyList(),
    val minutes: String = "",
    val expectedVersion: Long = 0
)
data class MeetingRecordView(val id: String, val expertId: String, val title: String, val time: String?,
    val participants: List<String>, val minutes: String, val version: Long, val updatedBy: String, val updatedAt: String)
data class MeetingRecordPage(val items: List<MeetingRecordView>, val total: Long, val page: Int, val size: Int)

@Service
class ExpertMeetingRecordService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val identity: ExpertProfileIdentityResolver,
    private val transactions: TransactionTemplate,
    private val mapper: ObjectMapper
) {
    private val rowMapper = RowMapper { rs, _ -> MeetingRecordView(
        rs.getString("id"), rs.getString("expert_key"), rs.getString("title"),
        rs.getTimestamp("meeting_time")?.toLocalDateTime()?.toString(),
        mapper.readValue(rs.getString("participants"), object : TypeReference<List<String>>() {}),
        rs.getString("minutes"), rs.getLong("version"), rs.getString("updated_by"),
        rs.getTimestamp("updated_at").toLocalDateTime().toString()) }

    fun list(expertId: String?, page: Int = 0, size: Int = 20): MeetingRecordPage {
        require(page >= 0 && size in 1..50) { "分页参数无效" }
        val params = MapSqlParameterSource("expertKey", key(expertId)).addValue("limit", size)
            .addValue("offset", page.toLong() * size)
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM expert_meeting_record WHERE expert_key=:expertKey", params, Long::class.java) ?: 0
        val items = jdbc.query("SELECT * FROM expert_meeting_record WHERE expert_key=:expertKey ORDER BY updated_at DESC,id DESC LIMIT :limit OFFSET :offset", params, rowMapper)
        return MeetingRecordPage(items, total, page, size)
    }

    /** Caller-generated UUID makes retry after an interrupted create response safe; never overwrites a different body. */
    fun save(id: String, input: MeetingRecordInput, actor: String): MeetingRecordView {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "会议标识无效" }
        require(actor.isNotBlank() && actor.length <= 64) { "登录身份无效" }
        require(input.expectedVersion >= 0) { "版本无效" }
        val expertKey = key(input.expertId)
        val title = text(input.title, 200).also { require(it.isNotEmpty()) { "请填写会议主题" } }
        val minutes = text(input.minutes, 50000)
        require(input.participants.size <= 100) { "参会人不能超过100人" }
        val participants = input.participants.map { text(it, 100) }.filter { it.isNotEmpty() }.distinct()
        val time = input.time?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try { LocalDateTime.parse(it).withNano(0) } catch (e: Exception) { throw IllegalArgumentException("会议时间无效") }
        }
        val params = MapSqlParameterSource("id", id).addValue("expertKey", expertKey)
            .addValue("title", title).addValue("time", time).addValue("participants", mapper.writeValueAsString(participants))
            .addValue("minutes", minutes).addValue("version", input.expectedVersion).addValue("actor", actor)
            .addValue("now", LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
        if (input.expectedVersion == 0L) identity.requireKnown(expertKey, input.level)
        return transactions.execute {
            val existing = read(params)
            if (input.expectedVersion == 0L && existing != null) {
                if (existing.title == title && existing.time == time?.toString() && existing.minutes == minutes && existing.participants == participants) return@execute existing
                throw conflict()
            }
            if (input.expectedVersion == 0L) {
                jdbc.update("INSERT INTO expert_meeting_record (id,expert_key,title,meeting_time,participants,minutes,version,updated_by,updated_at) VALUES (:id,:expertKey,:title,:time,:participants,:minutes,1,:actor,:now)", params)
            } else {
                if (existing == null) throw MeetingRecordException(HttpStatus.NOT_FOUND, "会议记录不存在")
                val count = jdbc.update("UPDATE expert_meeting_record SET title=:title,meeting_time=:time,participants=:participants,minutes=:minutes,version=version+1,updated_by=:actor,updated_at=:now WHERE id=:id AND expert_key=:expertKey AND version=:version", params)
                if (count != 1) throw conflict()
            }
            read(params) ?: throw IllegalStateException("会议保存失败")
        } ?: throw IllegalStateException("会议保存失败")
    }
    private fun read(params: MapSqlParameterSource) = jdbc.query("SELECT * FROM expert_meeting_record WHERE id=:id AND expert_key=:expertKey", params, rowMapper).firstOrNull()
    private fun conflict() = MeetingRecordException(HttpStatus.CONFLICT, "会议记录已更新，请关闭编辑窗口并刷新后重试")
    private fun text(value: String, max: Int): String = value.replace("\r\n", "\n").replace('\r','\n').trim().also {
        require(it.codePointCount(0, it.length) <= max && !it.contains('\u0000')) { "字段长度超限或包含非法字符" }
    }
    private fun key(raw: String?): String = ExpertIdNormalizer.normalize(raw?.trim().orEmpty()).also {
        require(it.isNotBlank() && it.codePointCount(0, it.length) <= 64) { "专家标识无效" }
    }
}
