package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.CalendarAttachmentCodec
import com.weibo.talentintroduction.mail.service.CalendarAttachmentSnapshot
import com.weibo.talentintroduction.mail.service.MeetingConfirmationDomain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 01（I-1～I-5）排期服务契约测试。
 *
 * 本类（无门禁，`mvn test` 全量执行）只断言服务自己算出的值：所有断言读
 * `repository.insert` 的捕获参数或抛出的异常类型，绝不复述 Mockito stub 的回值。
 * 真实 MySQL 语义（交集跨日、排序分页、唯一来源、同源并发、锁行/版本、来源
 * 邮件与专家行不被改动）由同文件 [MeetingCalendarServiceMysqlTest] 在 -DmysqlIt=true
 * 下用真实库证明。
 */
class MeetingCalendarServiceTest {
    private val repository = Mockito.mock(MeetingCalendarEventRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val service = MeetingCalendarService(repository, contacts)

    private val startBeijing = "2026-09-18T10:00"
    private val endBeijing = "2026-09-18T10:30"
    private val startUtc = Instant.parse("2026-09-18T02:00:00Z")
    private val endUtc = Instant.parse("2026-09-18T02:30:00Z")

    // ---------------------------------------------------------------- I-2 时间语义

    @Test
    fun `manual Beijing input is stored as UTC instants with ACTIVE status and no source mail`() {
        stubContact(1L)
        stubInsert(7L)
        Mockito.`when`(repository.findById(7L)).thenReturn(row(7L, startUtc, endUtc))

        service.createManual(1L, startBeijing, endBeijing, "https://meet.example/a", "First meeting")

        val stored = capturedInsert()
        assertEquals(startUtc, stored.startsAtUtc)
        assertEquals(endUtc, stored.endsAtUtc)
        assertEquals(1L, stored.expertContactId)
        assertEquals("https://meet.example/a", stored.meetingLink)
        assertEquals("First meeting", stored.note)
        assertEquals(MeetingCalendarEvent.ACTIVE, stored.status)
        assertNull(stored.sourceMailRecordId, "手工新增排期的来源邮件恒为空（I-1）")
    }

    @Test
    fun `UTC conversion does not depend on the JVM default timezone`() {
        stubContact(1L)
        Mockito.`when`(repository.insert(anyArg(sampleEvent()))).thenReturn(1L, 2L, 3L)
        Mockito.`when`(repository.findById(Mockito.anyLong())).thenReturn(row(1L, startUtc, endUtc))

        val original = TimeZone.getDefault()
        try {
            listOf("UTC", "Asia/Shanghai", "America/New_York").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                service.createManual(1L, startBeijing, endBeijing, null, null)
            }
        } finally {
            TimeZone.setDefault(original)
        }

        val stored = capturedInserts()
        assertEquals(3, stored.size)
        stored.forEach {
            assertEquals(startUtc, it.startsAtUtc, "北京 10:00 恒为 02:00Z，与 JVM 默认时区无关")
            assertEquals(endUtc, it.endsAtUtc)
        }
    }

    @Test
    fun `equal or reversed Beijing end time is rejected`() {
        stubContact(1L)

        assertThrows(IllegalArgumentException::class.java) {
            service.createManual(1L, endBeijing, endBeijing, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createManual(1L, endBeijing, startBeijing, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.createManual(1L, "18-09-2026 10:00", endBeijing, null, null)
        }
        Mockito.verifyNoInteractions(repository)
    }

    @Test
    fun `meeting link must be an http or https url of at most 1024 characters`() {
        stubContact(1L)
        stubInsert(7L)
        Mockito.`when`(repository.findById(7L)).thenReturn(row(7L, startUtc, endUtc))

        service.createManual(1L, startBeijing, endBeijing, "  https://meet.example/a  ", null)
        assertEquals("https://meet.example/a", capturedInsert().meetingLink, "链接去掉首尾空白后保存")

        service.createManual(1L, startBeijing, endBeijing, "   ", null)
        assertNull(capturedInsert().meetingLink, "空白链接等价于未填")

        listOf(
            "ftp://meet.example/a",
            "meet.example/a",
            "//meet.example/a",
            "https://meet.example/" + "a".repeat(1024)
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java, {
                service.createManual(1L, startBeijing, endBeijing, invalid, null)
            }, "非法链接必须 400：$invalid")
        }
    }

    @Test
    fun `note and cancel reason are bounded to 200 characters`() {
        stubContact(1L)
        stubInsert(7L)
        Mockito.`when`(repository.findById(7L)).thenReturn(row(7L, startUtc, endUtc))

        service.createManual(1L, startBeijing, endBeijing, null, "n".repeat(200))
        assertEquals(200, capturedInsert().note!!.length)

        assertThrows(IllegalArgumentException::class.java) {
            service.createManual(1L, startBeijing, endBeijing, null, "n".repeat(201))
        }

        Mockito.`when`(repository.findByIdForUpdate(9L)).thenReturn(row(9L, startUtc, endUtc))
        assertThrows(IllegalArgumentException::class.java) {
            service.cancel(9L, startUtc.toString(), "r".repeat(201))
        }
    }

    // ---------------------------------------------------------------- I-3 版本与取消

    @Test
    fun `stale version is a conflict and cancelled rows cannot be rescheduled`() {
        val updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        Mockito.`when`(repository.findByIdForUpdate(9L)).thenReturn(row(9L, updatedAt, updatedAt.plusSeconds(1800)))
        assertThrows(MeetingCalendarService.MeetingCalendarConflictException::class.java) {
            service.update(9L, "2026-09-19T10:00", "2026-09-19T10:30", null, null, "2026-09-18T02:00:01Z")
        }

        Mockito.`when`(repository.findByIdForUpdate(10L)).thenReturn(
            row(10L, updatedAt, updatedAt.plusSeconds(1800), status = MeetingCalendarEvent.CANCELLED)
        )
        assertThrows(MeetingCalendarService.MeetingCalendarConflictException::class.java) {
            service.update(10L, "2026-09-19T10:00", "2026-09-19T10:30", null, null, updatedAt.toString())
        }
    }

    @Test
    fun `rescheduling bumps the version strictly above the previous one`() {
        val updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        val locked = row(9L, startUtc, endUtc, updatedAt = updatedAt)
        Mockito.`when`(repository.findByIdForUpdate(9L)).thenReturn(locked)
        Mockito.`when`(repository.updateMutable(
            Mockito.anyLong(), anyArg(sampleLocal()), anyArg(sampleLocal()), anyArg(sampleLocal()),
            Mockito.any(), Mockito.any(), anyArg(sampleLocal())
        )).thenReturn(1)
        Mockito.`when`(repository.findById(9L)).thenReturn(
            row(
                9L, Instant.parse("2026-09-19T02:00:00Z"), Instant.parse("2026-09-19T02:30:00Z"),
                updatedAt = updatedAt.plusMillis(2)
            )
        )

        service.update(9L, "2026-09-19T10:00", "2026-09-19T10:30", null, null, updatedAt.toString())

        val captor = ArgumentCaptor.forClass(LocalDateTime::class.java)
        Mockito.verify(repository).updateMutable(
            eqArg(9L), eqArg(updatedAt.toUtcLocalDateTime()), anyArg(sampleLocal()), anyArg(sampleLocal()),
            Mockito.any(), Mockito.any(), captureArg(captor, sampleLocal())
        )
        val written = captor.value.toInstant(ZoneOffset.UTC)
        assertTrue(written.isAfter(updatedAt), "新版本必须严格大于旧版本")
        assertTrue(
            java.time.Duration.between(updatedAt, written) >= java.time.Duration.ofNanos(1000),
            "递增至少 1 微秒"
        )
    }

    @Test
    fun `unchanged reschedule returns the stored row without rewriting the version`() {
        val updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        val unchanged = row(
            9L, Instant.parse("2026-09-19T02:00:00Z"), Instant.parse("2026-09-19T02:30:00Z"),
            meetingLink = "https://meet.example/a", note = "note", updatedAt = updatedAt
        )
        Mockito.`when`(repository.findByIdForUpdate(9L)).thenReturn(unchanged)

        val result = service.update(
            9L, "2026-09-19T10:00", "2026-09-19T10:30", "https://meet.example/a", "note", updatedAt.toString()
        )

        assertEquals(updatedAt, result.event.updatedAt)
        Mockito.verify(repository, Mockito.never()).updateMutable(
            Mockito.anyLong(), anyArg(sampleLocal()), anyArg(sampleLocal()), anyArg(sampleLocal()),
            Mockito.any(), Mockito.any(), anyArg(sampleLocal())
        )
    }

    @Test
    fun `repeated cancellation returns the current row without overwriting the original reason`() {
        val updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        val cancelled = row(
            11L, startUtc, endUtc, status = MeetingCalendarEvent.CANCELLED,
            cancelReason = "original", updatedAt = updatedAt
        )

        Mockito.`when`(repository.findByIdForUpdate(11L)).thenReturn(cancelled)
        val result = service.cancel(11L, "stale-version", "replacement")

        assertEquals(MeetingCalendarEvent.CANCELLED, result.event.status)
        assertEquals("original", result.event.cancelReason)
        Mockito.verify(repository, Mockito.never()).cancel(
            Mockito.anyLong(), anyArg(sampleLocal()), Mockito.any(), anyArg(sampleLocal())
        )
    }

    @Test
    fun `cancellation stores the reason and keeps the row`() {
        val updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        Mockito.`when`(repository.findByIdForUpdate(11L)).thenReturn(row(11L, startUtc, endUtc))
        Mockito.`when`(repository.cancel(
            Mockito.anyLong(), anyArg(sampleLocal()), Mockito.any(), anyArg(sampleLocal())
        )).thenReturn(1)
        Mockito.`when`(repository.findById(11L)).thenReturn(
            row(11L, startUtc, endUtc, status = MeetingCalendarEvent.CANCELLED, cancelReason = "专家时间调整")
        )

        val result = service.cancel(11L, updatedAt.toString(), "专家时间调整")

        val reason = ArgumentCaptor.forClass(String::class.java)
        val written = ArgumentCaptor.forClass(LocalDateTime::class.java)
        Mockito.verify(repository).cancel(
            eqArg(11L), eqArg(updatedAt.toUtcLocalDateTime()),
            captureArg(reason, "reason"), captureArg(written, sampleLocal())
        )
        assertEquals("专家时间调整", reason.value)
        assertTrue(written.value.toInstant(ZoneOffset.UTC).isAfter(updatedAt))
        assertEquals(MeetingCalendarEvent.CANCELLED, result.event.status)
        assertEquals("专家时间调整", result.event.cancelReason)
    }

    @Test
    fun `cancelled rows stay readable and rescheduling a missing row is a 404`() {
        Mockito.`when`(repository.findById(404L)).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service.get(404L) }
        assertThrows(NoSuchElementException::class.java) {
            service.update(404L, startBeijing, endBeijing, null, null, startUtc.toString())
        }

        val cancelled = row(11L, startUtc, endUtc, status = MeetingCalendarEvent.CANCELLED, cancelReason = "reason")
        Mockito.`when`(repository.findById(11L)).thenReturn(cancelled)
        assertEquals(MeetingCalendarEvent.CANCELLED, service.get(11L).event.status)
    }

    // ---------------------------------------------------------------- I-5 查询与分页

    @Test
    fun `calendar range and limit are bounded and cursor is bound to the filter set`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.list("2026-01-01T00:00:00Z", "2026-06-01T00:00:00Z", null, false, 100, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.list("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", null, false, 201, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.list("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", null, false, 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.list(null, null, null, false, 100, null)
        }

        val from = "2026-09-18T00:00:00Z"
        val to = "2026-09-19T00:00:00Z"
        Mockito.`when`(repository.list(
            Mockito.any(), Mockito.any(), Mockito.any(), eqArg(false),
            Mockito.any(), Mockito.any(), eqArg(2)
        )).thenReturn(listOf(row(1L, startUtc, endUtc), row(2L, startUtc.plusSeconds(60), endUtc)))

        val first = service.list(from, to, null, false, 1, null)
        assertEquals(1, first.items.size)
        assertNotNull(first.nextCursor)

        assertThrows(IllegalArgumentException::class.java) {
            service.list(from, to, 5L, false, 1, first.nextCursor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.list(from, to, null, true, 1, first.nextCursor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.list(from, to, null, false, 1, "not-a-cursor")
        }
    }

    @Test
    fun `summaries zero-fill contacts and prefer the next future event`() {
        val past = Instant.parse("2020-01-01T02:00:00Z")
        val future = Instant.parse("2099-01-01T02:00:00Z")
        Mockito.`when`(repository.findActiveByContactIds(listOf(1L, 2L, 3L))).thenReturn(
            listOf(
                row(1L, future, future.plusSeconds(1800), contactId = 1L),
                row(2L, past, past.plusSeconds(1800), contactId = 1L),
                row(3L, past, past.plusSeconds(1800), contactId = 3L)
            )
        )

        val summaries = service.summaries(listOf(1L, 2L, 3L))

        assertEquals(listOf(1L, 2L, 3L), summaries.map { it.contactId }, "顺序与请求一致")
        assertEquals(2, summaries[0].activeCount)
        assertEquals(future, summaries[0].next!!.event.startsAtUtc, "优先最近未来开始")
        assertEquals(0, summaries[1].activeCount, "零场明确 activeCount=0")
        assertNull(summaries[1].next)
        assertEquals(1, summaries[2].activeCount)
        assertEquals(past, summaries[2].next!!.event.startsAtUtc, "无未来场次时取最近过去开始")
    }

    @Test
    fun `summaries reject more than 100 contactIds and keep an empty request empty`() {
        assertTrue(service.summaries(emptyList()).isEmpty())
        Mockito.verifyNoInteractions(repository)

        assertThrows(IllegalArgumentException::class.java) {
            service.summaries((1L..101L).toList())
        }
    }

    // ---------------------------------------------------------------- I-1/I-4 来源邮件与副作用

    @Test
    fun `createFromSentMail rejects a mail that is not a sent rich reply with a calendar attachment`() {
        stubContact(1L)
        val valid = sentRecord(55L)

        listOf(
            valid.copy(direction = "INBOUND"),
            valid.copy(mailType = "INTRODUCTION"),
            valid.copy(sendStatus = "FAILED"),
            valid.copy(calendarAttachmentJson = null),
            valid.copy(calendarAttachmentJson = "{\"schemaVersion\":2}")
        ).forEach { record ->
            assertThrows(IllegalArgumentException::class.java, {
                service.createFromSentMail(record, MeetingCalendarInput(startUtc, endUtc, null))
            }, "不合格来源邮件必须拒绝")
        }
        Mockito.verify(repository, Mockito.never()).insert(anyArg(sampleEvent()))
    }

    @Test
    fun `createFromSentMail is idempotent per source mail and never overwrites the original row`() {
        stubContact(1L)
        val record = sentRecord(55L)
        val existing = row(
            3L, Instant.parse("2026-09-18T06:00:00Z"), Instant.parse("2026-09-18T06:30:00Z"),
            status = MeetingCalendarEvent.CANCELLED, sourceMailRecordId = 55L
        )
        Mockito.`when`(repository.findBySourceMailRecordId(55L)).thenReturn(existing)

        val result = service.createFromSentMail(record, MeetingCalendarInput(startUtc, endUtc, "https://meet.example/a"))

        assertEquals(3L, result.event.id)
        assertEquals(MeetingCalendarEvent.CANCELLED, result.event.status, "已取消的来源排期不被原邮件日期覆盖")
        assertEquals(Instant.parse("2026-09-18T06:00:00Z"), result.event.startsAtUtc)
        Mockito.verify(repository, Mockito.never()).insert(anyArg(sampleEvent()))
    }

    @Test
    fun `createFromSentMail refuses a source mail already linked to another expert`() {
        stubContact(1L)
        val existing = row(3L, startUtc, endUtc, contactId = 2L, sourceMailRecordId = 55L)
        Mockito.`when`(repository.findBySourceMailRecordId(55L)).thenReturn(existing)

        assertThrows(IllegalArgumentException::class.java) {
            service.createFromSentMail(sentRecord(55L), MeetingCalendarInput(startUtc, endUtc, null))
        }
    }

    @Test
    fun `calendar writes reach only the calendar repository and never expert state`() {
        stubContact(1L)
        stubInsert(7L)
        Mockito.`when`(repository.findById(7L)).thenReturn(row(7L, startUtc, endUtc))

        service.createManual(1L, startBeijing, endBeijing, null, null)

        val stored = capturedInsert()
        assertEquals(MeetingCalendarEvent.ACTIVE, stored.status)
        assertNull(stored.sourceMailRecordId)
        Mockito.verify(contacts, Mockito.never()).save(
            anyArg(ExpertContact(campaignId = 1L, orcidId = "0000-0000-0000-0001", expertEmail = "expert@example.com", expertName = "Expert"))
        )
        Mockito.verify(contacts).existsById(1L)
        Mockito.verifyNoMoreInteractions(contacts)
        Mockito.verify(repository).findById(7L)
        Mockito.verify(repository, Mockito.never()).cancel(
            Mockito.anyLong(), anyArg(sampleLocal()), Mockito.any(), anyArg(sampleLocal())
        )
        Mockito.verify(repository, Mockito.never()).updateMutable(
            Mockito.anyLong(), anyArg(sampleLocal()), anyArg(sampleLocal()), anyArg(sampleLocal()),
            Mockito.any(), Mockito.any(), anyArg(sampleLocal())
        )
        Mockito.verifyNoMoreInteractions(repository)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Kotlin 非空参数 + Mockito matcher：matcher 返回 null 会撞上 Kotlin 参数空检查
     * （InlineByteBuddyMockMaker 保留 checkNotNullParameter），故用真实实例占位。
     */
    private fun <T> anyArg(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqArg(value: T): T = Mockito.eq(value) ?: value

    private fun <T> captureArg(captor: ArgumentCaptor<T>, defaultValue: T): T = captor.capture() ?: defaultValue

    private fun sampleEvent() = MeetingCalendarEvent(
        expertContactId = 1L,
        startsAtUtc = startUtc,
        endsAtUtc = endUtc,
        createdAt = startUtc,
        updatedAt = startUtc
    )

    private fun sampleLocal(): LocalDateTime = startUtc.toUtcLocalDateTime()

    private fun stubContact(id: Long) {
        Mockito.`when`(contacts.existsById(id)).thenReturn(true)
    }

    private fun stubInsert(id: Long) {
        Mockito.`when`(repository.insert(anyArg(sampleEvent()))).thenReturn(id)
    }

    private fun capturedInsert(): MeetingCalendarEvent {
        val captor = ArgumentCaptor.forClass(MeetingCalendarEvent::class.java)
        Mockito.verify(repository, Mockito.atLeastOnce()).insert(captureArg(captor, sampleEvent()))
        return captor.allValues.last()
    }

    private fun capturedInserts(): List<MeetingCalendarEvent> {
        val captor = ArgumentCaptor.forClass(MeetingCalendarEvent::class.java)
        Mockito.verify(repository, Mockito.atLeastOnce()).insert(captureArg(captor, sampleEvent()))
        return captor.allValues
    }

    private fun row(
        id: Long,
        start: Instant,
        end: Instant,
        contactId: Long = 1L,
        status: String = MeetingCalendarEvent.ACTIVE,
        cancelReason: String? = null,
        sourceMailRecordId: Long? = null,
        meetingLink: String? = null,
        note: String? = null,
        updatedAt: Instant = start
    ): MeetingCalendarEventRepository.EventRow = MeetingCalendarEventRepository.EventRow(
        MeetingCalendarEvent(
            id = id,
            expertContactId = contactId,
            sourceMailRecordId = sourceMailRecordId,
            startsAtUtc = start,
            endsAtUtc = end,
            meetingLink = meetingLink,
            note = note,
            cancelReason = cancelReason,
            status = status,
            createdAt = start,
            updatedAt = updatedAt
        ),
        "Expert",
        "expert@example.com"
    )

    private fun sentRecord(id: Long): MailRecord = MailRecord(
        id = id,
        expertContactId = 1L,
        direction = "OUTBOUND",
        mailType = "MANUAL_RICH_REPLY",
        inReplyTo = null,
        messageId = "out-$id",
        subject = "Meeting",
        body = "body",
        matchedQaRuleId = null,
        sendStatus = "SENT",
        receivedAt = null,
        sentAt = LocalDateTime.ofInstant(Instant.parse("2026-09-18T01:00:00Z"), ZoneOffset.UTC),
        calendarAttachmentJson = validCalendarJson()
    )
}

private fun validCalendarJson(): String {
    val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n"
    return CalendarAttachmentCodec.serialize(
        CalendarAttachmentSnapshot(
            schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
            filename = "meeting-2026-09-18-Alice.ics",
            contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
            icsText = ics,
            sha256 = MessageDigest.getInstance("SHA-256").digest(ics.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
            semanticSha256 = "a".repeat(64)
        )
    )
}

private fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

/**
 * 01（I-1～I-5）真实 MySQL 语义（mysqlIt 门禁，`-Pmysql-it` / `-DmysqlIt=true`）：
 * 时间往返与存储时区无关、交集跨北京日、201 行游标分页、来源唯一（含同源并发）、
 * 锁行版本冲突与取消终态、来源邮件与专家行不被改动。
 *
 * `@Transactional(NOT_SUPPORTED)`：行锁（FOR UPDATE）与并发用例必须各自持有真实
 * 连接与真实提交，测试级回滚会把并发场景变成假象。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(MeetingCalendarEventRepository::class, MeetingCalendarService::class)
class MeetingCalendarServiceMysqlTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var service: MeetingCalendarService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var transactions: TransactionTemplate

    @BeforeEach
    fun setUp() {
        transactions = TransactionTemplate(transactionManager)
        cleanup()
        seedAccount()
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                "VALUES (1, 'FIXTURE-1', 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'acc-a'))"
        )
        seedContact(1, "Alice Expert", "alice@example.org")
        seedContact(2, "Bob Expert", "bob@example.org")
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `Beijing schedule is stored as UTC and read back with Z regardless of the JVM timezone`() {
        val first = service.createManual(1L, "2026-09-18T10:00", "2026-09-18T10:30", "https://meet.example/room", null)
        assertEquals(Instant.parse("2026-09-18T02:00:00Z"), first.event.startsAtUtc)
        assertEquals(Instant.parse("2026-09-18T02:30:00Z"), first.event.endsAtUtc)
        assertEquals(MeetingCalendarEvent.ACTIVE, first.event.status)
        assertNull(first.event.sourceMailRecordId)
        assertEquals("Alice Expert", first.expertName)
        assertEquals("alice@example.org", first.expertEmail)

        val raw = jdbcTemplate.queryForObject(
            "SELECT starts_at_utc FROM meeting_calendar_event WHERE id = ?", String::class.java, first.event.id
        )!!
        assertTrue(raw.startsWith("2026-09-18 02:00:00"), "列里存的是 UTC 墙上时间，不是 +08:00 北京时间：$raw")

        val original = TimeZone.getDefault()
        val shifted = try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            service.createManual(1L, "2026-09-18T10:00", "2026-09-18T10:30", null, null)
        } finally {
            TimeZone.setDefault(original)
        }
        assertEquals(Instant.parse("2026-09-18T02:00:00Z"), shifted.event.startsAtUtc)
        assertEquals("2026-09-18T02:00:00Z", shifted.event.startsAtUtc.toString())
    }

    @Test
    fun `a cross-midnight event intersects both Beijing days and nothing else`() {
        service.createManual(1L, "2026-09-18T22:00", "2026-09-19T02:00", null, null)
        service.createManual(1L, "2026-09-10T10:00", "2026-09-10T10:30", null, null)

        // 北京日边界换算成瞬时：09-18 00:00 CST = 09-17 16:00Z，09-19 00:00 CST = 09-18 16:00Z
        val day18 = service.list("2026-09-17T16:00:00Z", "2026-09-18T16:00:00Z", null, false, 100, null)
        val day19 = service.list("2026-09-18T16:00:00Z", "2026-09-19T16:00:00Z", null, false, 100, null)
        val day20 = service.list("2026-09-19T16:00:00Z", "2026-09-20T16:00:00Z", null, false, 100, null)

        assertEquals(1, day18.items.size, "跨午夜场次出现在第一天的交集里")
        assertEquals(1, day19.items.size, "同一场次也出现在第二天的交集里")
        assertEquals(day18.items[0].event.id, day19.items[0].event.id)
        assertTrue(day20.items.isEmpty(), "09-10 的旧场次不落进 09-20 的窗口")
        assertEquals(
            1,
            service.list("2026-09-10T00:00:00Z", "2026-09-11T00:00:00Z", null, false, 100, null).items.size,
            "09-10 自己的窗口只命中 09-10 那一场"
        )
    }

    @Test
    fun `201 rows are read completely in two cursor pages and limit above 200 is rejected`() {
        val base = Instant.parse("2026-09-18T00:00:00Z")
        insertEvents(201, base)

        val from = "2026-09-17T00:00:00Z"
        val to = "2026-09-19T00:00:00Z"
        val firstPage = service.list(from, to, null, false, 200, null)
        assertEquals(200, firstPage.items.size)
        assertNotNull(firstPage.nextCursor)

        val secondPage = service.list(from, to, null, false, 200, firstPage.nextCursor)
        assertEquals(1, secondPage.items.size)
        assertNull(secondPage.nextCursor, "最后一页没有游标")

        val ids = (firstPage.items + secondPage.items).map { it.event.id }
        assertEquals(201, ids.distinct().size, "两页合起来恰好 201 条，不重不漏")
        val starts = (firstPage.items + secondPage.items).map { it.event.startsAtUtc }
        assertEquals(starts.sorted(), starts, "跨页顺序仍是 starts_at_utc,id 升序")

        assertThrows(IllegalArgumentException::class.java) { service.list(from, to, null, false, 201, null) }
    }

    @Test
    fun `the same source mail keeps exactly one row even under concurrency`() {
        val record = insertSentMail(55L, 1L)
        val json = jdbcTemplate.queryForObject(
            "SELECT calendar_attachment_json FROM mail_record WHERE id = 55", String::class.java
        )!!
        assertNotNull(CalendarAttachmentCodec.parseOrNull(json), "种子邮件必须是可由 01 codec 解析的规范快照")

        val sequential = transactions.execute {
            service.createFromSentMail(record, MeetingCalendarInput(Instant.parse("2026-09-18T02:00:00Z"), Instant.parse("2026-09-18T02:30:00Z"), null))
        }!!
        val again = transactions.execute {
            service.createFromSentMail(record, MeetingCalendarInput(Instant.parse("2026-09-20T02:00:00Z"), Instant.parse("2026-09-20T02:30:00Z"), null))
        }!!
        assertEquals(sequential.event.id, again.event.id)
        assertEquals(1L, calendarRowCount())
        assertEquals(
            Instant.parse("2026-09-18T02:00:00Z"),
            again.event.startsAtUtc,
            "重复提交不覆盖首次成功排期"
        )

        jdbcTemplate.update("DELETE FROM meeting_calendar_event")
        val barrier = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val results = try {
            val callable = Callable<Long> {
                barrier.await(10, TimeUnit.SECONDS)
                transactions.execute {
                    service.createFromSentMail(
                        record,
                        MeetingCalendarInput(
                            Instant.parse("2026-09-18T02:00:00Z"), Instant.parse("2026-09-18T02:30:00Z"), null
                        )
                    ).event.id!!
                }!!
            }
            val futures = listOf(pool.submit(callable), pool.submit(callable))
            barrier.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(2, results.size)
        assertEquals(1, results.distinct().size, "同源并发只产生一行：$results")
        assertEquals(1L, calendarRowCount())
    }

    @Test
    fun `stale version conflicts while cancel is terminal and keeps the original reason`() {
        val created = service.createManual(1L, "2026-09-18T10:00", "2026-09-18T10:30", null, "note")
        val id = created.event.id!!

        val updated = service.update(
            id, "2026-09-19T10:00", "2026-09-19T10:30", "https://meet.example/a", "note",
            created.event.updatedAt.toString()
        )
        assertTrue(updated.event.updatedAt.isAfter(created.event.updatedAt), "成功改期严格递增版本")
        assertEquals("https://meet.example/a", updated.event.meetingLink)

        assertThrows(MeetingCalendarService.MeetingCalendarConflictException::class.java) {
            service.update(id, "2026-09-20T10:00", "2026-09-20T10:30", null, null, created.event.updatedAt.toString())
        }

        val cancelled = service.cancel(id, updated.event.updatedAt.toString(), "专家时间调整")
        assertEquals(MeetingCalendarEvent.CANCELLED, cancelled.event.status)
        assertEquals("专家时间调整", cancelled.event.cancelReason)
        assertTrue(cancelled.event.updatedAt.isAfter(updated.event.updatedAt))

        assertThrows(MeetingCalendarService.MeetingCalendarConflictException::class.java) {
            service.update(id, "2026-09-21T10:00", "2026-09-21T10:30", null, null, cancelled.event.updatedAt.toString())
        }

        val repeated = service.cancel(id, updated.event.updatedAt.toString(), "第二个原因")
        assertEquals("专家时间调整", repeated.event.cancelReason, "重复取消不覆盖原取消原因")
        assertEquals(cancelled.event.updatedAt, repeated.event.updatedAt, "重复取消不重写版本")

        assertEquals(1L, calendarRowCount(), "取消保留行，不删除")
        assertEquals("专家时间调整", jdbcTemplate.queryForObject(
            "SELECT cancel_reason FROM meeting_calendar_event WHERE id = ?", String::class.java, id
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meeting_calendar_event WHERE id = ? AND status = 'CANCELLED'",
            Long::class.java, id
        ))
        // 默认查询只读 ACTIVE，showCancelled=true 同时读两状态
        assertTrue(service.list("2026-09-01T00:00:00Z", "2026-09-30T00:00:00Z", null, false, 100, null).items.isEmpty())
        assertEquals(1, service.list("2026-09-01T00:00:00Z", "2026-09-30T00:00:00Z", null, true, 100, null).items.size)
    }

    @Test
    fun `rescheduling and cancelling never touch the source mail or expert rows`() {
        val record = insertSentMail(55L, 1L)
        val before = mailRowSnapshot(55L)
        val created = transactions.execute {
            service.createFromSentMail(record, MeetingCalendarInput(Instant.parse("2026-09-18T02:00:00Z"), Instant.parse("2026-09-18T02:30:00Z"), null))
        }!!
        assertEquals(55L, created.event.sourceMailRecordId)

        val updated = service.update(
            created.event.id!!, "2026-09-19T10:00", "2026-09-19T10:30", null, null,
            created.event.updatedAt.toString()
        )
        service.cancel(created.event.id!!, updated.event.updatedAt.toString(), "原因")

        assertEquals(before, mailRowSnapshot(55L), "改期/取消前后来源邮件正文与 ICS 字节一致（I-4）")
        assertEquals(
            "NEW",
            jdbcTemplate.queryForObject("SELECT current_status FROM expert_contact WHERE id = 1", String::class.java)
        )
        assertEquals(
            "NOT_CONTACTED",
            jdbcTemplate.queryForObject("SELECT operator_status FROM expert_contact WHERE id = 1", String::class.java)
        )
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact_status_history WHERE expert_contact_id = 1", Long::class.java
        ))
    }

    @Test
    fun `source mails that are not sent rich replies with a calendar are refused without writing`() {
        val wrongType = insertSentMail(55L, 1L, mailType = "INTRODUCTION")
        val failedSend = insertSentMail(56L, 1L, sendStatus = "FAILED")
        val noCalendar = insertSentMail(57L, 2L).copy(calendarAttachmentJson = null)
        jdbcTemplate.update("UPDATE mail_record SET calendar_attachment_json = NULL WHERE id = 57")

        listOf(wrongType, failedSend, noCalendar).forEach { record ->
            assertThrows(IllegalArgumentException::class.java) {
                transactions.execute {
                    service.createFromSentMail(
                        record, MeetingCalendarInput(Instant.parse("2026-09-18T02:00:00Z"), Instant.parse("2026-09-18T02:30:00Z"), null)
                    )
                }
            }
        }
        assertEquals(0L, calendarRowCount())
    }

    // ---------------------------------------------------------------- MySQL fixtures

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM meeting_calendar_event")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun seedAccount() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES ('acc-a', 'acc-a@fixture.local', 'acc-a', 'smtp.fixture', 465,
                    'acc-a', 'pw', 'imap.fixture', 993, 'acc-a', 'pw')
            """.trimIndent()
        )
    }

    private fun seedContact(id: Long, name: String, email: String) {
        jdbcTemplate.update(
            "INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status) " +
                "VALUES (?, 1, ?, ?, ?, 'NEW')",
            id, "0000-0000-0000-%04d".format(id), email, name
        )
    }

    private fun insertEvents(count: Int, base: Instant) {
        val rows = (0 until count).map { index ->
            arrayOf<Any>(
                1L,
                java.sql.Timestamp.from(base.plusSeconds(index * 60L)),
                java.sql.Timestamp.from(base.plusSeconds(index * 60L + 1800)),
                java.sql.Timestamp.from(base),
                java.sql.Timestamp.from(base)
            )
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO meeting_calendar_event " +
                "(expert_contact_id, starts_at_utc, ends_at_utc, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
            rows
        )
    }

    private fun insertSentMail(
        id: Long,
        contactId: Long,
        mailType: String = "MANUAL_RICH_REPLY",
        sendStatus: String = "SENT"
    ): MailRecord {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (id, expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at, calendar_attachment_json)
            VALUES (?, ?, 'OUTBOUND', ?, 'acc-a', 'SYSTEM', ?, 'Meeting', 'body', ?, ?, ?, ?)
            """.trimIndent(),
            id, contactId, mailType, "out-$id", sendStatus,
            if (sendStatus == "SENT") java.sql.Timestamp.from(Instant.parse("2026-09-18T01:00:00Z")) else null,
            java.sql.Timestamp.from(Instant.parse("2026-09-18T01:00:00Z")),
            validCalendarJson()
        )
        return sentRecord(id, contactId, mailType, sendStatus)
    }

    private fun sentRecord(id: Long, contactId: Long, mailType: String = "MANUAL_RICH_REPLY", sendStatus: String = "SENT") =
        MailRecord(
            id = id,
            expertContactId = contactId,
            direction = "OUTBOUND",
            mailType = mailType,
            inReplyTo = null,
            messageId = "out-$id",
            subject = "Meeting",
            body = "body",
            matchedQaRuleId = null,
            sendStatus = sendStatus,
            receivedAt = null,
            sentAt = LocalDateTime.ofInstant(Instant.parse("2026-09-18T01:00:00Z"), ZoneOffset.UTC),
            calendarAttachmentJson = validCalendarJson()
        )

    private fun mailRowSnapshot(id: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT CONCAT(body, '|', COALESCE(calendar_attachment_json, 'null')) FROM mail_record WHERE id = ?",
            String::class.java, id
        )!!

    private fun calendarRowCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meeting_calendar_event", Long::class.java)!!
}
