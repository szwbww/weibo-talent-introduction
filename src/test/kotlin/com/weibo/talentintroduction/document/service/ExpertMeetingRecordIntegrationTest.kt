package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayInputStream
import java.util.UUID

@EnabledIfSystemProperty(named="mysqlIt", matches="true")
class ExpertMeetingRecordIntegrationTest {
    @TempDir lateinit var root: Path
    companion object {
        class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("meetings").withUsername("test").withPassword("test")
        @JvmStatic @BeforeAll fun start() { mysql.start() }
        @JvmStatic @AfterAll fun stop() { mysql.stop() }
    }
    @Test fun `migration persistence version conflict video ownership and old rows survive`() {
        val ds = DriverManagerDataSource(mysql.jdbcUrl,mysql.username,mysql.password)
        val jdbc = JdbcTemplate(ds)
        val migrations = Files.createDirectory(root.resolve("migrations"))
        for (name in listOf("V154__create_expert_meeting_video.sql","V155__create_expert_meeting_record.sql")) {
            Files.copy(Path.of("src/main/resources/db/migration",name),migrations.resolve(name))
        }
        val first = Flyway.configure().placeholderReplacement(false).dataSource(ds).locations("filesystem:$migrations").target("154").load()
        first.migrate()
        jdbc.update("INSERT INTO expert_meeting_video (id,expert_key,original_filename,display_filename,byte_length,uploaded_by,uploaded_at,updated_by,updated_at,version) VALUES ('legacy','EXPERT-A','old.mp4','old.mp4',1,'admin',NOW(),'admin',NOW(),1)")
        assertEquals("155",Flyway.configure().placeholderReplacement(false).dataSource(ds).locations("filesystem:$migrations").load().migrate().targetSchemaVersion)
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM expert_meeting_video WHERE id='legacy' AND meeting_record_id IS NULL",Long::class.java))
        val identity = Mockito.mock(ExpertProfileIdentityResolver::class.java)
        Mockito.`when`(identity.requireKnown("EXPERT-A",ExpertIndexLevel.RAW)).thenReturn("EXPERT-A")
        val named = NamedParameterJdbcTemplate(ds)
        val tx = TransactionTemplate(DataSourceTransactionManager(ds))
        val service = ExpertMeetingRecordService(named,identity,tx,jacksonObjectMapper())
        val id = UUID.randomUUID().toString()
        val input = MeetingRecordInput(expertId="EXPERT-A",title="技术会议",time="2026-10-12T10:30",participants=listOf("专家","企业","专家"),minutes="讨论\r\n结论")
        val saved = service.save(id,input,"admin")
        assertEquals(1L,saved.version)
        assertEquals(listOf("专家","企业"),saved.participants)
        assertEquals("讨论\n结论",saved.minutes)
        assertEquals(saved, service.save(id,input,"admin"), "create retry is idempotent")
        val reloaded = ExpertMeetingRecordService(named,identity,tx,jacksonObjectMapper()).list("EXPERT-A").items.single()
        assertEquals(saved,reloaded)
        assertTrue(service.list("EXPERT-B").items.isEmpty())
        val updated = service.save(id,input.copy(expectedVersion=1,minutes="新结论"),"editor")
        assertEquals(2L,updated.version)
        assertThrows(MeetingRecordException::class.java) { service.save(id,input.copy(expectedVersion=1),"stale") }
        assertThrows(MeetingRecordException::class.java) { service.save(id,input.copy(expertId="EXPERT-B",expectedVersion=2),"other") }
        assertThrows(IllegalArgumentException::class.java) { service.save(UUID.randomUUID().toString(),input.copy(title=""),"admin") }
        assertThrows(IllegalArgumentException::class.java) { service.save(UUID.randomUUID().toString(),input.copy(minutes="x".repeat(50001)),"admin") }
        assertThrows(IllegalArgumentException::class.java) { service.list("EXPERT-A",-1) }
        val props = MailAttachmentStorageProperties(basePath=root.resolve("files").toString())
        val videos = ExpertMeetingVideoService(props,identity,named,tx)
        val request = MeetingVideoUploadRequest("EXPERT-A",ExpertIndexLevel.RAW,"meeting.mp4","演示","8",8, id)
        val uploaded = videos.upload(request,"admin","/talent",ByteArrayInputStream(ByteArray(8)))
        assertEquals(uploaded.id,videos.list("EXPERT-A",0,20,"/talent",id).items.single().id)
        assertEquals(2L,videos.list("EXPERT-A",0,20,"/talent").total)
        assertThrows(MeetingVideoException::class.java) { videos.list("EXPERT-B",0,20,"/talent",id) }
        var read = false
        val body = object : ByteArrayInputStream(ByteArray(8)) { override fun read(b: ByteArray, off: Int, len: Int): Int { read=true;return super.read(b,off,len) } }
        assertThrows(MeetingVideoException::class.java) { videos.upload(request.copy(meetingRecordId=UUID.randomUUID().toString()),"admin","/talent",body) }
        assertFalse(read,"meeting ownership checked before streaming")
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM expert_meeting_record",Long::class.java))
    }
}
