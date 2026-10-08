package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId

class ExpertContactNoteServiceTest {
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val service = ExpertContactNoteService(jdbc)

    private fun setup(exists: Long = 1, note: String? = null) {
        Mockito.doAnswer { exists }.`when`(jdbc).queryForObject(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java), Mockito.eq(Long::class.java)
        )
        Mockito.doAnswer { invocation ->
            if (note == null) emptyList<ExpertContactNoteView>() else {
                val rs = Mockito.mock(ResultSet::class.java)
                Mockito.`when`(rs.getString("note")).thenReturn(note)
                Mockito.`when`(rs.getString("updated_by")).thenReturn("userB")
                Mockito.`when`(rs.getTimestamp("updated_at"))
                    .thenReturn(Timestamp.valueOf("2026-10-08 10:18:00.123"))
                val mapper = invocation.getArgument<RowMapper<ExpertContactNoteView>>(2)
                listOf(mapper.mapRow(rs, 0)!!)
            }
        }.`when`(jdbc).query(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any<RowMapper<ExpertContactNoteView>>()
        )
    }

    private fun writes() = Mockito.mockingDetails(jdbc).invocations.filter { it.method.name == "update" }

    @Test
    fun `GET absent note returns unique empty state without writes`() {
        setup()
        assertEquals(ExpertContactNoteView(42, "", null, null), service.get(42))
        assertTrue(writes().isEmpty())
        assertTrue(Mockito.mockingDetails(jdbc).invocations.all { (it.arguments[0] as String).startsWith("SELECT") })
    }

    @Test
    fun `RowMapper returns persisted text identity and fixed Beijing offset milliseconds`() {
        setup(note = "中文\n<img src=x> 'quoted'")
        assertEquals(
            ExpertContactNoteView(42, "中文\n<img src=x> 'quoted'", "userB", "2026-10-08T10:18:00.123+08:00"),
            service.get(42)
        )
    }

    @Test
    fun `save normalizes only line endings and edges and reads persisted authoritative view`() {
        setup(note = "persisted")
        val before = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val result = service.save("userA", 42, " \r\n中文 'quoted'\r\n  内部  \r末尾\n ")
        val write = writes().single()
        val sql = write.arguments[0] as String
        val params = write.arguments[1] as MapSqlParameterSource
        assertTrue(sql.startsWith("INSERT INTO expert_contact_note"))
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"))
        assertEquals("中文 'quoted'\n  内部  \n末尾", params.getValue("note"))
        assertEquals("userA", params.getValue("username"))
        assertEquals(42L, params.getValue("contactId"))
        val time = params.getValue("updatedAt") as LocalDateTime
        assertEquals(0, time.nano % 1_000_000)
        assertFalse(time.isBefore(before.minusSeconds(1)))
        assertFalse(time.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
        assertEquals("persisted", result.note)
        assertEquals("userB", result.updatedBy)
    }

    @Test
    fun `empty and whitespace saves delete only note row and clear metadata`() {
        setup()
        listOf("", " \r\n\t ").forEach { raw ->
            assertEquals(ExpertContactNoteView(42, "", null, null), service.save("admin", 42, raw))
        }
        writes().forEach {
            assertEquals("DELETE FROM expert_contact_note WHERE expert_contact_id=:contactId", it.arguments[0])
            assertEquals(setOf("contactId"), (it.arguments[1] as MapSqlParameterSource).values.keys)
        }
    }

    @Test
    fun `UTF16 boundaries accept 2000 units including emoji and reject before normalization`() {
        setup()
        listOf("中".repeat(2000), "😀".repeat(1000)).forEach { service.save("a".repeat(64), 42, it) }
        val count = writes().size
        listOf("中".repeat(2001), "😀".repeat(1001), " ".repeat(2001)).forEach {
            assertThrows(IllegalArgumentException::class.java) { service.save("admin", 42, it) }
        }
        assertEquals(count, writes().size)
    }

    @Test
    fun `invalid identity ids and missing contacts never write`() {
        setup(exists = 0)
        listOf(0L, -1L).forEach {
            assertThrows(IllegalArgumentException::class.java) { service.get(it) }
            assertThrows(IllegalArgumentException::class.java) { service.save("admin", it, "note") }
        }
        listOf("", "  ", "a".repeat(65)).forEach {
            assertThrows(IllegalArgumentException::class.java) { service.save(it, 42, "note") }
        }
        assertThrows(NoSuchElementException::class.java) { service.get(42) }
        assertThrows(NoSuchElementException::class.java) { service.save("admin", 42, "note") }
        assertTrue(writes().isEmpty())
    }
}
