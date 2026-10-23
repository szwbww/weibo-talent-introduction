package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryPaperArchiveProperties
import com.weibo.talentintroduction.config.DiscoverySampleProperties
import com.weibo.talentintroduction.discovery.controller.DiscoveryPaperArchiveController
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.mock.web.MockHttpServletResponse
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Disposable MySQL, production V159, real JDBC transactions; no production application context. */
internal object ArchiveMysqlFixture {
    private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    private val mysql by lazy {
        check(DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required, not skipped" }
        Mysql().withDatabaseName("archive_child01").withUsername("test").withPassword("test").also { it.start() }
    }
    val dataSource by lazy { DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password).also {
        ResourceDatabasePopulator(ClassPathResource("db/migration/V159__create_discovery_paper_archive.sql")).execute(it)
    } }
    val jdbc by lazy { JdbcTemplate(dataSource) }
    val mapper = jacksonObjectMapper().findAndRegisterModules()
    fun repository() = DiscoveryPaperArchiveRepository(jdbc, DataSourceTransactionManager(dataSource))
    fun clean() {
        jdbc.update("DELETE FROM discovery_paper_archive_attempt")
        jdbc.update("DELETE FROM discovery_paper_archive_blob")
        jdbc.update("DELETE FROM discovery_paper_archive_record")
        jdbc.update("DELETE FROM discovery_paper_archive_setting")
    }
    fun stop() { if (mysql.isRunning) mysql.stop() }
}

internal class ArchiveFixture(val realDisk: Boolean = false) : AutoCloseable {
    val directory: Path = Files.createTempDirectory("archive-child01-").toRealPath()
    val root: Path = directory.resolve("papers")
    val props = DiscoveryPaperArchiveProperties(root.toString())
    val sampleProps = DiscoverySampleProperties(directory = directory.resolve("legacy-samples").toString())
    val mapper = ArchiveMysqlFixture.mapper
    val repository = ArchiveMysqlFixture.repository()
    var service = newService()
    fun newService() = DiscoveryPaperArchiveService(repository, props, sampleProps, mapper).also {
        if (!realDisk) it.diskProbe = { ArchiveDisk(100 * ARCHIVE_GIB,80 * ARCHIVE_GIB,70 * ARCHIVE_GIB) }
    }
    fun enable(max: Long = 20 * ARCHIVE_GIB) = service.updateSettings(ArchiveSettingsRequest(service.settings().revision,true,max,ARCHIVE_GIB))
    fun paper(key: String, execution: Long = 1) = service.beginRecord(execution,"OPENALEX",key,
        mapper.readTree("""{"schemaVersion":1,"title":"Synthetic $key","year":2026,"doi":"10.fixture/$key","authors":["Fixture Author"],"fullText":"never metadata"}"""))
    fun body(record: ArchiveRecord, bytes: ByteArray, kind: ArchiveAttemptKind = ArchiveAttemptKind.PDF_URL,
             media: String = "application/pdf"): ArchiveAttempt {
        val attempt=service.beginAttempt(record.id,UUID.randomUUID(),0,kind,"https://fixture.invalid/paper?api_key=secret#fragment")
        return service.saveBody(attempt.id,bytes,media)
    }
    fun protect(recordId: Long,value: Boolean): ArchiveProtectionResult = service.protection(ArchiveProtectionRequest(
        listOf(ArchiveRevisionItem(recordId,service.detail(recordId).record.revision)),value))
    fun pdf(size: Int = 1024, fill: Byte = 65): ByteArray = ByteArray(size) { fill }.also { "%PDF-1.4\n".toByteArray().copyInto(it) }
    override fun close() { directory.toFile().deleteRecursively() }
}

class DiscoveryPaperArchiveRepositoryIT {
    @BeforeEach fun clean() { ArchiveMysqlFixture.clean() }
    @Test fun `schema singleton ranges binary identities and UTC milliseconds`() {
        val r=ArchiveMysqlFixture.repository()
        assertEquals(ArchiveSetting(),r.setting())
        val now=Instant.parse("2026-10-23T08:12:34.123Z")
        val saved=r.saveSetting(ArchiveSettingsRequest(0,true,25*ARCHIVE_GIB,15*ARCHIVE_GIB),now)
        assertEquals(now,saved.updatedAt); assertEquals(1L,saved.revision)
        assertEquals(saved,ArchiveMysqlFixture.repository().setting())
        assertEquals("REVISION_CONFLICT",assertThrows(ArchiveException::class.java) {
            r.saveSetting(ArchiveSettingsRequest(0,false,ARCHIVE_GIB,ARCHIVE_GIB),now)
        }.code)
        assertThrows(Exception::class.java) { ArchiveMysqlFixture.jdbc.update("INSERT INTO discovery_paper_archive_setting VALUES (2,0,1,1,0,NOW())") }
        assertThrows(ArchiveException::class.java) { r.saveSetting(ArchiveSettingsRequest(1,true,ARCHIVE_GIB+1,ARCHIVE_GIB),now) }
        ArchiveMysqlFixture.jdbc.update("UPDATE discovery_paper_archive_setting SET max_bytes=1")
        assertThrows(ArchiveException::class.java) { r.setting() }
        val a=r.beginRecord(1,"OPENALEX","W-key","{\"schemaVersion\":1}",now)
        val b=r.beginRecord(1,"OPENALEX","w-key","{\"schemaVersion\":1}",now)
        assertNotEquals(a.id,b.id)
        assertEquals(a,r.beginRecord(1,"OPENALEX","W-key","{}",now))
    }
    @Test fun `transaction rollback attempt idempotency outcome freeze and analysis CAS`() {
        ArchiveFixture().use { f ->
            val p=f.paper("rollback"); val uuid=UUID.randomUUID()
            val a=f.service.beginAttempt(p.id,uuid,0,ArchiveAttemptKind.PMC_XML)
            assertEquals(a,f.service.beginAttempt(p.id,uuid,0,ArchiveAttemptKind.PMC_XML))
            assertThrows(IllegalStateException::class.java) { f.repository.transaction {
                f.repository.protect(p.id,p.revision,true,Instant.now()); error("rollback fixture")
            } }
            assertFalse(f.repository.record(p.id)!!.protected)
            val first=f.mapper.readTree("{\"schemaVersion\":1,\"emails\":[\"one@fixture.invalid\"],\"resolved\":1,\"unresolved\":0}")
            f.service.finishExtraction(p.id,first)
            f.service.finishExtraction(p.id,f.mapper.readTree("{\"schemaVersion\":1}"))
            assertEquals(first,f.service.detail(p.id).outcome)
            val revision=f.repository.record(p.id)!!.revision
            f.service.saveAnalysis(p.id,revision,f.mapper.readTree("{\"schemaVersion\":1,\"hasGain\":true,\"summary\":{\"delta\":1}}"))
            assertThrows(ArchiveException::class.java) { f.service.saveAnalysis(p.id,revision,first) }
            assertEquals(0,f.service.list(ArchiveQuery(hasGain=true)).items.size)
        }
    }
    @Test fun `parallel singleton CAS has exactly one winner`() {
        val pool=Executors.newFixedThreadPool(2)
        try {
            val results=(0..1).map { pool.submit<Boolean> {
                try { ArchiveMysqlFixture.repository().saveSetting(ArchiveSettingsRequest(0,true,ARCHIVE_GIB,ARCHIVE_GIB),Instant.now()); true }
                catch (_: Exception) { false }
            } }.map { it.get(30,TimeUnit.SECONDS) }
            assertEquals(1,results.count { it }); assertEquals(1L,ArchiveMysqlFixture.repository().setting().revision)
        } finally { pool.shutdownNow() }
    }
    @Test fun `SQL pagination LIKE escaping state and historical aggregates`() {
        ArchiveFixture().use { f ->
            f.enable()
            val a=f.paper("one%_"); val b=f.paper("second",2); f.paper("no-file")
            val body=f.pdf(); f.body(a,body); f.body(a,body); f.body(b,body)
            val bad=f.service.beginAttempt(a.id,UUID.randomUUID(),0,ArchiveAttemptKind.PMC_XML)
            f.service.finishAttempt(bad.id,ArchiveAttemptStatus.DOWNLOAD_FAILED,"HTTP_FAILED",503)
            assertEquals(listOf(a.id),f.service.list(ArchiveQuery(q="%_")).items.map { it.id })
            assertEquals(2L,f.service.list(ArchiveQuery(status=ArchiveRecordState.STORED)).total)
            assertEquals(0L,f.service.list(ArchiveQuery(page=1)).items.size.toLong())
            assertEquals(1L,f.service.stats().storage.fileCount)
            val counts=f.service.stats().counts
            assertEquals(ArchiveCounts(3,4,3,1,2,1,1),counts)
            assertEquals(2L,f.service.stats(1).counts.savedAttempts)
            val preview=f.service.deletePreview(listOf(a.id,b.id))
            f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
            assertEquals(counts.savedAttempts,f.service.stats().counts.savedAttempts)
            assertEquals(0L,f.service.stats().storage.fileCount)
            assertNotNull(f.service.detail(a.id).paper)
        }
    }
}

/** Explicit behavioral smoke launcher, run separately from JUnit using real SQL and FileStore. */
object ArchiveSmokeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        try {
            ArchiveMysqlFixture.clean()
            ArchiveFixture(realDisk=true).use { f ->
                val initial=f.service.settings()
                check(!initial.enabled && initial.maxBytes==20*ARCHIVE_GIB && initial.minFreeBytes==15*ARCHIVE_GIB)
                // A-1 defaults and a persisted 25/15 GiB update, then a fresh coordinator/repository read.
                f.service.updateSettings(ArchiveSettingsRequest(0,true,25*ARCHIVE_GIB,15*ARCHIVE_GIB))
                f.service=f.newService()
                check(f.service.settings().maxBytes==25*ARCHIVE_GIB)
                val disk=f.service.stats().storage
                check(disk.diskTotalBytes != null && disk.diskAvailableBytes != null)
                println("SMOKE settings reread: 25/15 GiB; FileStore total=${disk.diskTotalBytes}, usable=${disk.diskAvailableBytes}, measuredAt=${disk.measuredAt}")
                val a=f.paper("A"); val b=f.paper("B"); val c=f.paper("C")
                val bytes=f.pdf(); val aa=f.body(a,bytes); val ba=f.body(b,bytes); f.body(c,f.pdf(2048,66))
                check(f.service.stats().storage.archiveUsedBytes==3072L)
                f.service.finishExtraction(a.id,f.mapper.readTree("{\"schemaVersion\":1,\"emails\":1,\"resolved\":1,\"unresolved\":0}"))
                f.service.saveAnalysis(a.id,f.service.detail(a.id).record.revision,f.mapper.readTree("{\"schemaVersion\":1,\"hasGain\":false}"))
                check(f.protect(b.id,true).changed==1)
                val first=f.service.deletePreview(listOf(a.id,b.id,c.id))
                check(first.deletableFiles==1 && first.deletableBytes==2048L && first.protectedRecords==listOf(b.id))
                check(f.protect(b.id,false).changed==1)
                check(f.service.withContent(b.id,ba.id) { _, input -> input.readBytes().contentEquals(bytes) })
                val controller=DiscoveryPaperArchiveController(f.service)
                val range=MockHttpServletResponse(); controller.content(a.id,aa.id,false,"bytes=0-99",range)
                check(range.status==206 && range.contentAsByteArray.contentEquals(bytes.copyOfRange(0,100)))
                println("SMOKE saved=3072 bytes/2 files; B protected => deletable=2048; unprotected => all bytes unchanged; controller Range=206/100 bytes")
                val second=f.service.deletePreview(listOf(a.id,b.id,c.id))
                val deleted=f.service.delete(ArchiveDeleteRequest(second.ids,second.snapshotHash))
                check(deleted.deletedFiles==2 && deleted.deletedBytes==3072L && deleted.remainingFiles==0L)
                check(f.service.detail(a.id).outcome!=null && f.service.detail(a.id).analysis!=null)
                val gone=assertThrows(ArchiveException::class.java) { f.service.withContent(a.id,aa.id) { _,_ -> Unit } }
                check(gone.httpStatus==410)
                println("SMOKE shared delete=2 files/3072 logical bytes; remaining=0; metadata/outcome/analysis retained; content=410")
            }
        } finally { ArchiveMysqlFixture.stop() }
    }
}
