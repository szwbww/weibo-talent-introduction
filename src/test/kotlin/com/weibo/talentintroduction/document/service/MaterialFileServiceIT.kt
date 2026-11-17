package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.*
import com.weibo.talentintroduction.document.repository.*
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
class MaterialFileServiceIT {
    @TempDir lateinit var root: Path
    private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    private fun <T> anyArg(): T = Mockito.any<T>()

    @Test fun `real metadata transactions preserve sources rollback failed extraction and support restore`() {
        val mysql = Mysql().withCreateContainerCmdModifier { it.withPlatform("linux/amd64") }
        mysql.start()
        try {
            val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            val jdbc = JdbcTemplate(ds)
            jdbc.execute("CREATE TABLE mail_attachment(id BIGINT PRIMARY KEY AUTO_INCREMENT,file_name VARCHAR(255),storage_path VARCHAR(1024),manual_upload_id BIGINT)")
            jdbc.execute("CREATE TABLE expert_document(id BIGINT PRIMARY KEY AUTO_INCREMENT,expert_contact_id BIGINT,mail_attachment_id BIGINT)")
            jdbc.execute("CREATE TABLE manual_expert_material_upload(id BIGINT PRIMARY KEY AUTO_INCREMENT,expert_contact_id BIGINT,uploaded_by VARCHAR(100))")
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V177__expert_material_files.sql")))
            val tx = TransactionTemplate(DataSourceTransactionManager(ds))
            val meta = MaterialFileRepository(jdbc, jacksonObjectMapper())
            val query = Mockito.mock(ExpertMaterialService::class.java)
            val docs = Mockito.mock(ExpertDocumentRepository::class.java)
            val attachments = Mockito.mock(MailAttachmentRepository::class.java)
            val uploads = Mockito.mock(ManualExpertMaterialUploadRepository::class.java)
            Mockito.`when`(docs.findFirstByMailAttachmentId(Mockito.anyLong())).thenAnswer { call ->
                jdbc.query("SELECT * FROM expert_document WHERE mail_attachment_id=?", { rs, _ -> ExpertDocument(id=rs.getLong("id"), expertContactId=rs.getLong("expert_contact_id"), mailAttachmentId=rs.getLong("mail_attachment_id"),documentType="OTHER") }, call.getArgument<Long>(0)).firstOrNull()
            }
            Mockito.`when`(attachments.findById(Mockito.anyLong())).thenAnswer { call ->
                Optional.ofNullable(jdbc.query("SELECT * FROM mail_attachment WHERE id=?", { rs, _ -> MailAttachment(id=rs.getLong("id"), fileName=rs.getString("file_name"),storagePath=rs.getString("storage_path"),contentType="application/octet-stream",manualUploadId=rs.getLong("manual_upload_id")) },call.getArgument<Long>(0)).firstOrNull())
            }
            Mockito.`when`(query.resolveOwnerContact(anyArg())).thenReturn(1L)
            Mockito.`when`(query.resolveReadyFile(Mockito.anyLong(),Mockito.anyLong())).thenAnswer { call ->
                val att = attachments.findById(call.getArgument(1)).get()
                require(!meta.isDeleted(att.id!!))
                ReadyFile(att,Path.of(att.storagePath!!))
            }
            Mockito.`when`(uploads.save(anyArg<ManualExpertMaterialUpload>())).thenAnswer { call ->
                val item = call.getArgument<ManualExpertMaterialUpload>(0)
                jdbc.update("INSERT INTO manual_expert_material_upload(expert_contact_id,uploaded_by) VALUES (?,?)",item.expertContactId,item.uploadedBy)
                item.copy(id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long::class.java))
            }
            Mockito.`when`(attachments.save(anyArg<MailAttachment>())).thenAnswer { call ->
                val item = call.getArgument<MailAttachment>(0)
                assertNull(item.mailRecordId); assertNull(item.inboundProcessingId)
                jdbc.update("INSERT INTO mail_attachment(file_name,storage_path,manual_upload_id) VALUES (?,?,?)",item.fileName,item.storagePath,item.manualUploadId)
                item.copy(id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long::class.java))
            }
            Mockito.`when`(docs.save(anyArg<ExpertDocument>())).thenAnswer { call ->
                val item = call.getArgument<ExpertDocument>(0)
                jdbc.update("INSERT INTO expert_document(expert_contact_id,mail_attachment_id) VALUES (?,?)",item.expertContactId,item.mailAttachmentId)
                item.copy(id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long::class.java))
            }
            val service = MaterialFileService(query,meta,docs,attachments,uploads,MaterialArchiveExtractor(),MailAttachmentStorageProperties(basePath=root.toString()),tx)
            val zip = root.resolve("source.zip")
            ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
                listOf("学历/博士/证明.txt","工作/证明.txt").forEach { stream.putNextEntry(ZipEntry(it));stream.write(it.toByteArray());stream.closeEntry() }
            }
            jdbc.update("INSERT INTO mail_attachment VALUES (1,'source.zip',?,1)",zip.toString())
            jdbc.update("INSERT INTO expert_document VALUES (1,1,1)")
            service.edit(1,1,MaterialFileEdit("材料合集.zip","含学历及工作证明",listOf("学历","工作证明","学历")))
            assertEquals(listOf("学历","工作证明"),meta.forContact(1)[1L]!!.tags)
            assertThrows(IllegalArgumentException::class.java) { service.edit(2,1,MaterialFileEdit("越权")) }
            assertEquals(2,service.parse(1,1)["fileCount"])
            assertEquals(2,service.parse(1,1)["fileCount"])
            val children = meta.forContact(1).values.filter { it.parentAttachmentId==1L }
            assertEquals(2,children.size)
            assertEquals(setOf("学历/博士/证明.txt","工作/证明.txt"),children.map { it.entryPath }.toSet())
            service.delete(1,1,false)
            assertTrue(meta.isDeleted(1)); assertTrue(children.none { meta.isDeleted(it.attachmentId) })
            assertThrows(IllegalArgumentException::class.java) { service.parse(1,1) }
            service.restore(1,1)
            service.delete(1,1,true)
            assertTrue(children.all { meta.isDeleted(it.attachmentId) })
            service.restore(1,children.first().attachmentId)
            assertFalse(meta.isDeleted(children.first().attachmentId)); assertTrue(meta.isDeleted(1))
            assertTrue(Files.exists(zip))
            val bad = Files.writeString(root.resolve("bad.rar"),"bad")
            jdbc.update("INSERT INTO mail_attachment VALUES (99,'bad.rar',?,1)",bad.toString())
            jdbc.update("INSERT INTO expert_document VALUES (99,1,99)")
            assertThrows(IllegalArgumentException::class.java) { service.parse(1,99) }
            assertEquals("FAILED",meta.forContact(1)[99L]!!.extractionState)
            assertTrue(meta.forContact(1).values.none { it.parentAttachmentId==99L })
            // Failure after files were extracted rolls back child attachments, ownership rows and metadata.
            jdbc.update("INSERT INTO mail_attachment VALUES (100,'source.zip',?,1)",zip.toString())
            jdbc.update("INSERT INTO expert_document VALUES (100,1,100)")
            val before = jdbc.queryForObject("SELECT COUNT(*) FROM mail_attachment",Long::class.java)
            Mockito.doThrow(IllegalStateException("test DB failure")).`when`(docs).save(anyArg<ExpertDocument>())
            assertThrows(IllegalStateException::class.java) { service.parse(1,100) }
            assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM mail_attachment",Long::class.java))
            assertEquals("FAILED",meta.forContact(1)[100L]!!.extractionState)
            assertEquals(2L,Files.walk(root.resolve("parsed-materials")).use { paths -> paths.filter(Files::isRegularFile).count() })
        } finally { mysql.stop() }
    }
}
