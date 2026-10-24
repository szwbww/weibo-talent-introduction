package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.config.DiscoveryPaperArchiveProperties
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.DefaultTransactionStatus
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiscoveryPaperArchiveServiceTest {
    private lateinit var f: ArchiveFixture
    @BeforeEach fun setup() { ArchiveMysqlFixture.clean(); f=ArchiveFixture() }
    @AfterEach fun cleanup() { f.close() }
    private fun code(expected: String, action: () -> Unit): ArchiveException = assertThrows(ArchiveException::class.java,action).also { assertEquals(expected,it.code) }
    private fun path(a: ArchiveAttempt) = f.root.resolve("blobs/${a.blobSha256!!.take(2)}/${a.blobSha256}.bin")
    private class Crash : Error("simulated process interruption")

    @Test fun `first email array counts names without proof and missing outcomes stay unknown`() {
        f.enable(); val p = f.paper("names")
        f.service.finishExtraction(p.id, f.mapper.readTree("""{"schemaVersion":1,"first":{"outcome":{"emails":[{"email":"a@x.org","givenNames":"Alice","familyNames":"Smith"},{"email":"b@x.org","givenNames":"Bob","familyNames":"Jones"},{"email":"c@x.org","givenNames":"Carol"}]}}}"""))
        assertEquals(ArchiveExtractionSummary(3,2,1), f.service.detail(p.id).record.extractionSummary)
        assertNull(f.service.detail(f.paper("unknown").id).record.extractionSummary)
        val missing = f.paper("missing-outcome")
        f.service.finishExtraction(missing.id, f.mapper.readTree("""{"schemaVersion":1,"first":{}}"""))
        assertNull(f.service.detail(missing.id).record.extractionSummary)
    }

    @Test fun `PDF magic overrides MIME on save and historical preview without analysis writes`() {
        f.enable(); val p = f.paper("mime")
        val bytes = QualificationFixtures.pdf()
        val b = f.body(p,bytes,ArchiveAttemptKind.PDF_URL,"text/plain")
        assertEquals("application/pdf", f.repository.blob(b.blobSha256!!)!!.mediaType)
        ArchiveMysqlFixture.jdbc.update("UPDATE discovery_paper_archive_blob SET media_type = 'text/plain' WHERE sha256 = ?",b.blobSha256)
        val before = f.repository.record(p.id)
        f.service.withContent(p.id,b.id) { content, input ->
            assertEquals("application/pdf",content.mediaType); assertArrayEquals(bytes,input.readBytes())
        }
        assertEquals(before,f.repository.record(p.id))
        val html = f.paper("fake")
        val h = f.body(html,"<html>not PDF</html>".toByteArray(),ArchiveAttemptKind.PDF_URL,"application/pdf")
        f.service.withContent(html.id,h.id) { content,_ -> assertTrue(content.mediaType.startsWith("text/plain")) }
    }

    @Test fun `default disabled absent root and persistent settings reread`() {
        f.service.startupRecovery()
        assertFalse(Files.exists(f.root)); assertFalse(f.service.settings().effectiveEnabled)
        assertEquals(20*ARCHIVE_GIB,f.service.settings().maxBytes)
        assertFalse(f.service.stats().storage.directoryAvailable)
        f.service.updateSettings(ArchiveSettingsRequest(0,true,25*ARCHIVE_GIB,15*ARCHIVE_GIB))
        val restarted=f.newService()
        assertEquals(25*ARCHIVE_GIB,restarted.settings().maxBytes)
        assertEquals(15*ARCHIVE_GIB,restarted.settings().minFreeBytes)
        assertEquals(ArchiveGate.READY,restarted.settings().gate)
        assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(f.root))
        code("REVISION_CONFLICT") { restarted.updateSettings(ArchiveSettingsRequest(0,false,ARCHIVE_GIB,ARCHIVE_GIB)) }
        val blockedRoot=f.directory.resolve("not-a-directory")
        Files.write(blockedRoot,byteArrayOf(1))
        val blocked=DiscoveryPaperArchiveService(f.repository,DiscoveryPaperArchiveProperties(blockedRoot.toString()),f.sampleProps,f.mapper)
        blocked.startupRecovery()
        blocked.scheduledRecovery()
        assertEquals(503,code("STORAGE_UNAVAILABLE") { blocked.settings() }.httpStatus)
    }
    @Test fun `shared files protected batch cancellation and immutable audit survive deletion`() {
        f.enable()
        val a=f.paper("A"); val b=f.paper("B"); val c=f.paper("C")
        val bytes=f.pdf(); val aa=f.body(a,bytes); val ba=f.body(b,bytes); f.body(c,f.pdf(2048,66))
        assertFalse(aa.reused); assertTrue(ba.reused)
        assertEquals(3072L,f.service.stats().storage.archiveUsedBytes)
        assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(path(aa)))
        val justA=f.service.deletePreview(listOf(a.id)); assertEquals(0,justA.deletableFiles); assertEquals(1,justA.sharedFiles.size)
        f.protect(b.id,true)
        val protected=f.service.deletePreview(listOf(a.id,b.id,c.id))
        assertEquals(1,protected.deletableFiles); assertEquals(2048L,protected.deletableBytes)
        assertEquals(listOf(b.id),protected.protectedRecords)
        assertEquals(1,f.protect(b.id,false).changed)
        assertEquals(1,f.protect(b.id,false).unchanged)
        assertArrayEquals(bytes,f.service.withContent(b.id,ba.id) { _,input -> input.readBytes() })
        val preview=f.service.deletePreview(listOf(a.id,b.id,c.id))
        val result=f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
        assertEquals(2,result.deletedFiles); assertEquals(3072L,result.deletedBytes); assertEquals(0,result.failed.size)
        assertEquals(ArchiveRecordState.DELETED,f.service.detail(a.id).record.state)
        assertEquals(1024L,f.service.detail(a.id).attempts.single().bytes)
        assertEquals(3L,f.service.stats().counts.savedAttempts)
        assertTrue(f.service.detail(b.id).attempts.single().reused)
        code("SOURCE_DELETED") { f.service.withContent(a.id,aa.id) { _,_ -> Unit } }
    }
    @Test fun `multiple file record deletes only unshared bodies`() {
        f.enable(); val a=f.paper("multi"); val b=f.paper("other")
        f.body(a,f.pdf()); f.body(b,f.pdf()); f.body(a,f.pdf(2048,66))
        val preview=f.service.deletePreview(listOf(a.id))
        assertEquals(1,preview.deletableFiles)
        f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
        assertEquals(ArchiveRecordState.STORED,f.service.detail(a.id).record.state)
        assertEquals(1024L,f.service.detail(a.id).record.storedBytes)
    }
    @Test fun `protection and new references invalidate snapshots and protection CAS reports per item`() {
        f.enable(); val a=f.paper("A"); f.body(a,f.pdf())
        val initial=f.service.deletePreview(listOf(a.id))
        val stale=f.service.detail(a.id).record.revision
        f.protect(a.id,true)
        val changed=code("DELETE_SNAPSHOT_CHANGED") { f.service.delete(ArchiveDeleteRequest(initial.ids,initial.snapshotHash)) }
        assertNotNull(changed.preview)
        val result=f.service.protection(ArchiveProtectionRequest(listOf(ArchiveRevisionItem(a.id,stale),ArchiveRevisionItem(999999,0)),false))
        assertEquals(0,result.changed); assertEquals(setOf("REVISION_CONFLICT","NOT_FOUND"),result.conflicts.map { it.reasonCode }.toSet())
        f.protect(a.id,false)
        val before=f.service.deletePreview(listOf(a.id))
        f.body(f.paper("new-owner",2),f.pdf())
        val shared=code("DELETE_SNAPSHOT_CHANGED") { f.service.delete(ArchiveDeleteRequest(before.ids,before.snapshotHash)) }
        assertEquals(1,shared.preview!!.sharedFiles.size)
    }
    @Test fun `disk boundary exactly byte gate and unknown never zero`() {
        f.enable(); val a=f.paper("boundary"); val bytes=f.pdf()
        val attempt=f.service.beginAttempt(a.id,UUID.randomUUID(),0,ArchiveAttemptKind.PDF_URL)
        f.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,80*ARCHIVE_GIB,ARCHIVE_GIB+1023) }
        code("ARCHIVE_STORAGE_BLOCKED") { f.service.saveBody(attempt.id,bytes,"application/pdf") }
        assertEquals(0L,f.service.stats().counts.savedAttempts)
        f.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,80*ARCHIVE_GIB,ARCHIVE_GIB+1024) }
        f.service.saveBody(attempt.id,bytes,"application/pdf")
        assertEquals(1L,f.service.stats().counts.savedAttempts)
        f.service.diskProbe={ throw java.io.IOException("fixture") }
        val unknown=f.service.stats().storage
        assertNull(unknown.diskAvailableBytes); assertNull(unknown.diskTotalBytes); assertEquals(ArchiveGate.UNAVAILABLE,unknown.gate)
    }
    @Test fun `concurrent writes cannot exceed exact archive cap and reducing cap never deletes`() {
        f.enable(ARCHIVE_GIB)
        val filler=f.root.resolve("blobs/filler.bin")
        FileChannel.open(filler,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use {
            it.position(ARCHIVE_GIB-1024-1); it.write(ByteBuffer.wrap(byteArrayOf(0)))
        }
        val a=f.paper("capA"); val b=f.paper("capB")
        val attempts=listOf(a,b).map { f.service.beginAttempt(it.id,UUID.randomUUID(),0,ArchiveAttemptKind.PDF_URL) }
        val pool=Executors.newFixedThreadPool(2)
        try {
            val results=attempts.mapIndexed { i,attempt -> pool.submit<Boolean> {
                try { f.service.saveBody(attempt.id,f.pdf(fill=(65+i).toByte()),"application/pdf"); true }
                catch (e: ArchiveException) { assertEquals("ARCHIVE_STORAGE_BLOCKED",e.code); false }
            } }.map { it.get(30,TimeUnit.SECONDS) }
            assertEquals(1,results.count { it }); assertEquals(ARCHIVE_GIB,f.service.stats().storage.archiveUsedBytes)
        } finally { pool.shutdownNow() }
        // Lowering below current usage is legal and does not unlink any body.
        FileChannel.open(filler,StandardOpenOption.WRITE).use { it.position(ARCHIVE_GIB); it.write(ByteBuffer.wrap(byteArrayOf(0))) }
        val revision=f.service.settings().revision
        assertEquals(ArchiveGate.BLOCKED,f.service.updateSettings(ArchiveSettingsRequest(revision,true,ARCHIVE_GIB,ARCHIVE_GIB)).gate)
        assertTrue(Files.exists(filler)); assertEquals(1L,f.service.stats().counts.savedAttempts)
    }
    @Test fun `save crash before and after rename never reports saved and orphan recovery is idempotent`() {
        f.enable()
        listOf("BEFORE_RENAME","AFTER_RENAME").forEachIndexed { i,point ->
            val a=f.service.beginAttempt(f.paper("crash$i").id,UUID.randomUUID(),0,ArchiveAttemptKind.PDF_URL)
            f.service.failpoint={ if (it==point) throw Crash() }
            assertThrows(Crash::class.java) { f.service.saveBody(a.id,f.pdf(fill=(65+i).toByte()),"application/pdf") }
            assertEquals(ArchiveAttemptStatus.STARTED,f.repository.attempt(a.id)!!.status)
            f.service=f.newService(); f.service.recover(); f.service.recover()
            assertEquals(0L,f.service.stats().counts.savedAttempts)
            assertEquals(0L,f.service.stats().storage.blobBytes)
        }
        val orphan=f.root.resolve(".tmp/old-orphan"); Files.write(orphan,ByteArray(32))
        Files.setLastModifiedTime(orphan,FileTime.from(Instant.now().minusSeconds(3601)))
        val recent=f.root.resolve(".tmp/recent"); Files.write(recent,ByteArray(16))
        f.service.recover(); assertFalse(Files.exists(orphan)); assertTrue(Files.exists(recent))
    }
    @Test fun `real SQL commit failure leaves no saved attempt and does not remove shared committed blob`() {
        f.enable(); val a=f.paper("commit"); val b=f.paper("existing")
        val existing=f.body(b,f.pdf())
        class FailingCommit : DataSourceTransactionManager(ArchiveMysqlFixture.dataSource) {
            var fail=false
            init { isRollbackOnCommitFailure=true }
            override fun doCommit(status: DefaultTransactionStatus) {
                if (fail) { fail=false; throw TransactionSystemException("fixture commit failure") }
                super.doCommit(status)
            }
        }
        val manager=FailingCommit()
        val repo=DiscoveryPaperArchiveRepository(ArchiveMysqlFixture.jdbc,manager)
        val service=DiscoveryPaperArchiveService(repo,f.props,f.sampleProps,f.mapper)
        service.diskProbe=f.service.diskProbe
        val attempt=service.beginAttempt(a.id,UUID.randomUUID(),0,ArchiveAttemptKind.PDF_URL)
        manager.fail=true
        code("SAVE_ERROR") { service.saveBody(attempt.id,f.pdf(2048,66),"application/pdf") }
        assertEquals(ArchiveAttemptStatus.SAVE_ERROR,repo.attempt(attempt.id)!!.status)
        service.recover(); assertEquals(1024L,service.stats().storage.archiveUsedBytes)
        assertTrue(Files.exists(path(existing)))
        assertEquals(1L,service.stats().counts.savedAttempts)
    }
    @Test fun `delete crashes after marking and after unlink recover identically without deleting metadata`() {
        f.enable()
        listOf("AFTER_DELETING","AFTER_UNLINK").forEachIndexed { i,point ->
            val r=f.paper("delete-crash$i"); val a=f.body(r,f.pdf(fill=(65+i).toByte()))
            val preview=f.service.deletePreview(listOf(r.id))
            f.service.failpoint={ if(it==point) throw Crash() }
            assertThrows(Crash::class.java) { f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash)) }
            assertEquals(ArchiveBlobState.DELETING,f.repository.blob(a.blobSha256!!)!!.state)
            assertEquals("DELETE_IN_PROGRESS",f.protect(r.id,true).conflicts.single().reasonCode)
            val other=f.service.beginAttempt(f.paper("blocked-ref$i").id,UUID.randomUUID(),0,ArchiveAttemptKind.PDF_URL)
            code("DELETE_IN_PROGRESS") { f.service.saveBody(other.id,f.pdf(fill=(65+i).toByte()),"application/pdf") }
            f.service=f.newService(); f.service.recover(); f.service.recover()
            assertFalse(Files.exists(path(a)))
            assertEquals(ArchiveRecordState.DELETED,f.service.detail(r.id).record.state)
            assertNotNull(f.service.detail(r.id).paper)
        }
    }
    @Test fun `unlink failure retains deleting and retry recovery completes`() {
        f.enable(); val r=f.paper("unlink"); val a=f.body(r,f.pdf()); val file=path(a)
        val preview=f.service.deletePreview(listOf(r.id))
        f.service.failpoint={ if(it=="AFTER_DELETING") { Files.delete(file); Files.createDirectory(file); Files.write(file.resolve("blocker"),byteArrayOf(1)) } }
        val result=f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
        assertEquals("DELETE_IO_ERROR",result.failed.single().reasonCode)
        assertEquals(ArchiveBlobState.DELETING,f.repository.blob(a.blobSha256!!)!!.state)
        Files.delete(file.resolve("blocker")); Files.delete(file)
        f.service=f.newService(); f.service.recover()
        assertEquals(ArchiveAttemptStatus.DELETED,f.repository.attempt(a.id)!!.status)
    }
    @Test fun `missing and corrupt files lose preview and reuse verifies actual hash`() {
        f.enable(); val r=f.paper("missing"); val a=f.body(r,f.pdf())
        Files.delete(path(a))
        assertFalse(f.service.detail(r.id).attempts.single().readable)
        code("SOURCE_MISSING") { f.service.withContent(r.id,a.id) { _,_ -> Unit } }
        assertEquals(ArchiveBlobState.MISSING,f.repository.blob(a.blobSha256!!)!!.state)
        val second=f.body(f.paper("replacement"),f.pdf())
        assertFalse(second.reused)
        Files.write(path(second),f.pdf(fill=67))
        val third=f.body(f.paper("corrupt-reuse"),f.pdf())
        assertFalse(third.reused)
        assertArrayEquals(f.pdf(),Files.readAllBytes(path(third)))
    }
    @Test fun `symlinks relative paths ancestor overlap and file lock fail closed`() {
        f.enable(); val r=f.paper("path"); val a=f.body(r,f.pdf())
        FileChannel.open(f.root.resolve(".archive.lock"),StandardOpenOption.WRITE).use { channel -> channel.lock().use {
            code("ARCHIVE_BUSY") { f.protect(r.id,true) }
        } }
        val outside=f.directory.resolve("outside"); Files.write(outside,f.pdf())
        Files.delete(path(a)); Files.createSymbolicLink(path(a),outside)
        code("UNSAFE_PATH") { f.service.withContent(r.id,a.id) { _,_ -> Unit } }
        assertArrayEquals(f.pdf(),Files.readAllBytes(outside))
        listOf("relative/papers",f.sampleProps.directory,f.directory.toString()).forEach { directory ->
            val invalid=DiscoveryPaperArchiveService(f.repository,DiscoveryPaperArchiveProperties(directory),f.sampleProps,f.mapper)
            code("INVALID_DIRECTORY") { invalid.settings() }
        }
        val link=f.directory.resolve("root-link"); Files.createSymbolicLink(link,f.root)
        val invalid=DiscoveryPaperArchiveService(f.repository,DiscoveryPaperArchiveProperties(link.toString()),f.sampleProps,f.mapper)
        code("UNSAFE_PATH") { invalid.settings() }
    }
    @Test fun `all JSON text positions sanitize paths and URL secrets while retaining other scalars`() {
        val input=f.mapper.readTree("""{"schemaVersion":1,"links":["https://user:password@fixture.invalid/p?api_key=secret#fragment","/private/source.pdf",["https://user:password@fixture.invalid/nested?token=secret",["/private/nested.pdf",{"values":["https://user:password@fixture.invalid/deep?token=secret","/private/deep.pdf","ordinary",42,true,null]}]]],"plain":"ordinary","number":42,"flag":true,"empty":null}""")
        val expected=f.mapper.readTree("""{"schemaVersion":1,"links":["https://fixture.invalid/p",null,["https://fixture.invalid/nested",[null,{"values":["https://fixture.invalid/deep",null,"ordinary",42,true,null]}]]],"plain":"ordinary","number":42,"flag":true,"empty":null}""")
        val record=f.service.beginRecord(1,"OPENALEX","recursive-sanitization",input)
        val extracted=f.service.finishExtraction(record.id,input)
        f.service.saveAnalysis(record.id,extracted.revision,input)
        val persisted=f.repository.record(record.id)!!
        assertEquals(expected,f.mapper.readTree(persisted.metadataJson))
        assertEquals(expected,f.mapper.readTree(persisted.outcomeJson))
        assertEquals(expected,f.mapper.readTree(persisted.analysisJson))
        val detail=f.service.detail(record.id)
        assertEquals(expected,detail.paper)
        assertEquals(expected,detail.outcome)
        assertEquals(expected,detail.analysis)
    }
    @Test fun `metadata limits remove full text secrets and URL parameters without truncation`() {
        val metadata=f.mapper.createObjectNode().put("schemaVersion",1).put("title","safe").put("fullText","x".repeat(600000))
            .put("sourceUrl","https://user:password@fixture.invalid/p?token=secret#hash").put("apiKey","secret").put("localPath","/private/file")
        val record=f.service.beginRecord(1,"OPENALEX","safe",metadata)
        val encoded=f.mapper.writeValueAsString(f.service.detail(record.id))
        assertFalse(encoded.contains("secret")); assertFalse(encoded.contains("/private")); assertFalse(encoded.contains("fullText"))
        code("METADATA_TOO_LARGE") { f.service.beginRecord(1,"OPENALEX","too-big",metadata.deepCopy().put("title","x".repeat(524288))) }
        code("METADATA_TOO_LARGE") { f.service.finishExtraction(record.id,f.mapper.createObjectNode().put("schemaVersion",1).put("evidence","x".repeat(4194304))) }
        code("METADATA_TOO_LARGE") { f.service.saveAnalysis(record.id,0,f.mapper.createObjectNode().put("schemaVersion",1).put("evidence","x".repeat(4194304))) }
        assertNull(f.service.detail(record.id).record.extractionSummary)
        assertNull(f.service.detail(record.id).record.analysisSummary)
        val evidence=f.mapper.createObjectNode().put("schemaVersion",1).put("institutionToken","non-secret-identity-evidence")
        f.service.finishExtraction(record.id,evidence)
        assertEquals(evidence,f.service.detail(record.id).outcome)
    }
    @Test fun `database failure is unavailable not missing setting default`() {
        val repo=Mockito.mock(DiscoveryPaperArchiveRepository::class.java)
        Mockito.`when`(repo.setting()).thenThrow(DataAccessResourceFailureException("fixture database down"))
        val service=DiscoveryPaperArchiveService(repo,f.props,f.sampleProps,f.mapper)
        assertEquals(503,code("DB_UNAVAILABLE") { service.settings() }.httpStatus)
        assertEquals(503,code("DB_UNAVAILABLE") { service.stats() }.httpStatus)
    }
}
