package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.*
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.*
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.mail.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
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
import java.util.Optional

/** Isolated synthetic schema mirrors the material columns/FKs from V7/V36/V118/V119/V130.
 * No Spring application context or application.yml datasource is used. Both server versions
 * run exactly the same production list/reconcile/count queries, including their real BINARY SQL.
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
class ExpertMaterialEligibilityReadIT {
    @TempDir lateinit var root: Path
    private class Mysql(image: String) : MySQLContainer<Mysql>(image)

    @TestFactory
    fun `production read contract on MySQL 5_7_44 and 8_0_36`() =
        listOf("5.7.44", "8.0.36").map { version ->
            DynamicTest.dynamicTest("MySQL $version production material readers") {
                val mysql = Mysql("mysql:$version").withDatabaseName("material_read")
                    .withUsername("fixture").withPassword("fixture")
                    .withCreateContainerCmdModifier { it.withPlatform("linux/amd64") }
                mysql.start()
                try {
                    val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                    val jdbc = JdbcTemplate(ds)
                    println("MATERIAL_SQL_SERVER=" + jdbc.queryForObject("SELECT VERSION()", String::class.java))
                    runContract(jdbc, NamedParameterJdbcTemplate(ds), TransactionTemplate(DataSourceTransactionManager(ds)), version)
                } finally { mysql.stop() }
            }
        }

    private fun runContract(jdbc: JdbcTemplate, named: NamedParameterJdbcTemplate, tx: TransactionTemplate, version: String) {
        createSchema(jdbc)
        val attachments = Mockito.mock(MailAttachmentRepository::class.java)
        val documents = Mockito.mock(ExpertDocumentRepository::class.java)
        val records = Mockito.mock(MailRecordRepository::class.java)
        val processing = Mockito.mock(InboundMailProcessingRepository::class.java)
        val transfers = Mockito.mock(MailAttachmentTransferRepository::class.java)
        val transferService = Mockito.mock(AttachmentTransferService::class.java)
        val mailService = Mockito.mock(MailAttachmentService::class.java)
        val material = ExpertMaterialService(MailAttachmentStorageProperties(basePath = root.toString()),
            documents, attachments, records, processing, transfers, transferService, mailService, named)
        val counts = MailboxConversationRepository(named)
        fun readAttachments(sql: String, vararg args: Any): List<MailAttachment> = jdbc.query(sql, { rs, _ ->
            MailAttachment(id = rs.getLong("id"), mailRecordId = rs.getObject("mail_record_id")?.let { rs.getLong("mail_record_id") },
                inboundProcessingId = rs.getObject("inbound_processing_id")?.let { rs.getLong("inbound_processing_id") },
                manualUploadId = rs.getObject("manual_upload_id")?.let { rs.getLong("manual_upload_id") },
                fileName = rs.getString("file_name"), contentType = rs.getString("content_type"),
                storagePath = rs.getString("storage_path"), fileSize = rs.getObject("file_size")?.let { rs.getLong("file_size") })
        }, *args)
        fun readDocuments(sql: String, vararg args: Any): List<ExpertDocument> = jdbc.query(sql, { rs, _ ->
            ExpertDocument(id = rs.getLong("id"), expertContactId = rs.getLong("expert_contact_id"),
                mailAttachmentId = rs.getLong("mail_attachment_id"), documentType = rs.getString("document_type"),
                documentStatus = rs.getString("document_status"))
        }, *args)
        Mockito.`when`(documents.findAllByExpertContactIdOrderByCreatedAtAsc(Mockito.anyLong())).thenAnswer {
            readDocuments("SELECT * FROM expert_document WHERE expert_contact_id=? ORDER BY created_at,id", it.getArgument<Long>(0))
        }
        Mockito.`when`(documents.findFirstByMailAttachmentId(Mockito.anyLong())).thenAnswer {
            readDocuments("SELECT * FROM expert_document WHERE mail_attachment_id=?", it.getArgument<Long>(0)).firstOrNull()
        }
        Mockito.`when`(documents.save(Mockito.any(ExpertDocument::class.java))).thenAnswer {
            val doc = it.getArgument<ExpertDocument>(0)
            jdbc.update("INSERT INTO expert_document(expert_contact_id,mail_attachment_id,document_type,document_status) VALUES (?,?,?,?)",
                doc.expertContactId, doc.mailAttachmentId, doc.documentType, doc.documentStatus)
            doc
        }
        Mockito.`when`(attachments.findById(Mockito.anyLong())).thenAnswer {
            Optional.ofNullable(readAttachments("SELECT * FROM mail_attachment WHERE id=?", it.getArgument<Long>(0)).firstOrNull())
        }
        Mockito.`when`(attachments.findAllById(Mockito.anyList<Long>())).thenAnswer {
            val ids = it.getArgument<Iterable<Long>>(0).toSet()
            readAttachments("SELECT * FROM mail_attachment ORDER BY id").filter { a -> a.id in ids }
        }
        Mockito.`when`(attachments.findAllByMailRecordIdOrderByCreatedAtAsc(Mockito.anyLong())).thenAnswer {
            readAttachments("SELECT * FROM mail_attachment WHERE mail_record_id=? ORDER BY created_at,id", it.getArgument<Long>(0))
        }
        Mockito.`when`(mailService.inferDocumentType(Mockito.anyString())).thenReturn(ExpertDocumentType.OTHER)
        val record = MailRecord(id = 1, expertContactId = 1, direction = "INBOUND", mailType = "REPLY", messageId = "synthetic",
            inReplyTo = null, subject = "synthetic", body = "synthetic", matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = null)
        Mockito.`when`(records.findByIdOrNull(1)).thenReturn(record)
        Mockito.`when`(records.findAllByExpertContactIdOrderByCreatedAtAsc(1)).thenReturn(listOf(record))
        jdbc.update("INSERT INTO expert_contact(id) VALUES(1),(2),(3)")
        jdbc.update("INSERT INTO mail_record(id,expert_contact_id,direction,message_id) VALUES(1,1,'INBOUND','synthetic')")
        jdbc.update("INSERT INTO inbound_mail_processing(id,expert_contact_id) VALUES(1,1),(2,2)")
        jdbc.update("INSERT INTO manual_expert_material_upload(id,expert_contact_id,uploaded_by,created_at) VALUES(1,1,'fixture',NOW())")

        // Same exact normalization matrix as the pure rule. A dedicated contact for each row
        // proves the production count SQL's decision, not merely an accidentally equal aggregate.
        ExpertMaterialEligibilityTest.cases.forEachIndexed { index, (name, mime, expected) ->
            val contact = 10L + index
            val id = 100L + index
            jdbc.update("INSERT INTO expert_contact(id) VALUES(?)", contact)
            jdbc.update("INSERT INTO manual_expert_material_upload(id,expert_contact_id,uploaded_by,created_at) VALUES(?,?,'fixture',NOW())", contact, contact)
            jdbc.update("INSERT INTO mail_attachment(id,manual_upload_id,file_name,content_type) VALUES(?,?,?,?)", id, contact, name, mime)
            jdbc.update("INSERT INTO expert_document(expert_contact_id,mail_attachment_id,document_type) VALUES(?,?,'OTHER')", contact, id)
            assertEquals(if (expected) 1L else 0L, counts.materialCountByContacts(listOf(contact))[contact] ?: 0L, "$version SQL $name / $mime")
            assertEquals(if (expected) listOf(id) else emptyList<Long>(), material.listMaterials(contact,0,10,null,null,null,null).items.map { it.attachmentId })
        }
        assertTrue(counts.materialCountByContacts(emptyList()).isEmpty())
        assertEquals(emptyMap<Long,Long>(), counts.materialCountByContacts(listOf(3)))
        val states = listOf("METADATA_ONLY", "QUEUED", "DOWNLOADING", "STORED", "FAILED", "SOURCE_UNAVAILABLE", null)
        for (id in 1L..25L) {
            val owner = when ((id % 3).toInt()) { 0 -> "manual_upload_id"; 1 -> "mail_record_id"; else -> "inbound_processing_id" }
            val name = if (id <= 12) "smime.p7s" else if (id == 13L) "certificate.png" else if (id == 14L) "image001.jpg" else "cv-$id.pdf"
            val mime = if (id == 13L) "image/png" else if (id == 14L) "image/jpeg" else "application/pdf"
            val path = root.resolve("$version-$id")
            Files.write(path, byteArrayOf()) // Ordinary zero-byte files are still material.
            jdbc.update("INSERT INTO mail_attachment(id,$owner,file_name,content_type,file_size,storage_path) VALUES(?,1,?,?,0,?)", id,name,mime,path.toString())
            jdbc.update("INSERT INTO expert_document(expert_contact_id,mail_attachment_id,document_type,document_status) VALUES(1,?,'OTHER','APPROVED')", id)
            val state = states[(id % states.size).toInt()]
            if (state != null) jdbc.update("INSERT INTO mail_attachment_transfer(attachment_id,state) VALUES(?,?)",id,state)
        }
        fun page(index: Int = 0, q: String? = null, state: String? = null) = material.listMaterials(1,index,10,q,null,null,state)
        assertEquals(10, page().items.size)
        assertEquals(3, page(1).items.size)
        assertEquals(13L,page().total)
        assertEquals(4L,page().summary.stored)
        assertEquals(2L,page().summary.metadataOnly)
        assertEquals(4L,page().summary.active)
        assertEquals(2L,page().summary.failed)
        assertEquals(1L,page().summary.sourceUnavailable)
        assertEquals(0L,page(q="smime").total)
        assertEquals(page().summary,page(q="smime",state="FAILED").summary)
        assertEquals(13L,counts.materialCountByContacts(listOf(1))[1])
        assertTrue((page().items + page(1).items).all { it.documentStatus == "APPROVED" })
        val before = snapshot(jdbc)
        val browse = ExpertDocumentBrowseService(documents, attachments, records, material)
        val expectedIds = (13L..25L).toList()
        assertEquals(expectedIds,browse.listDocuments(1).map { it.attachmentId })
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        Mockito.`when`(contacts.findById(1)).thenReturn(Optional.of(ExpertContact(id=1,campaignId=1,orcidId="fixture",expertEmail="fixture@example.invalid",expertName="Synthetic")))
        val conversationState = Mockito.mock(ConversationStateService::class.java)
        Mockito.`when`(conversationState.recommendedNextAction("NEW", false)).thenReturn("synthetic next action")
        val detailService = ExpertContactManagementService(contacts,records,Mockito.mock(ManualHandoffRepository::class.java),attachments,documents,
            Mockito.mock(ExpertContactStatusHistoryRepository::class.java),conversationState,
            Mockito.mock(MeetingScheduleRepository::class.java),Mockito.mock(ExpertIndexWriterService::class.java),processing,
            Mockito.mock(OperatorActionLogService::class.java),Mockito.mock(AutoReplySettingService::class.java))
        val detail = detailService.getContactDetail(1)
        assertEquals(expectedIds,detail.documents.map { it.mailAttachmentId })
        assertEquals(expectedIds.filter { it % 3 == 1L },detail.attachments.map { it.id })
        assertEquals(before,snapshot(jdbc),"all GET readers leave persisted metadata/state unchanged")
        Mockito.verifyNoInteractions(transferService,mailService)
        // Missing attachment metadata must fail explicitly, not silently hide a broken document.
        Mockito.`when`(attachments.findAllById(Mockito.anyList<Long>())).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) { detailService.getContactDetail(1) }

        // Reconcile: normal and signature without documents plus an already documented signature.
        jdbc.update("INSERT INTO mail_attachment(id,inbound_processing_id,file_name,content_type) VALUES(50,2,'normal.pdf','application/pdf'),(51,2,'smime.p7s',NULL),(52,2,'old.p7s',NULL)")
        jdbc.update("INSERT INTO expert_document(expert_contact_id,mail_attachment_id,document_type) VALUES(2,52,'OTHER')")
        assertEquals(listOf(50L),material.reconcileDryRun(2).candidateAttachmentIds)
        assertEquals(1,tx.execute { material.reconcileApply(2).createdCount })
        assertEquals(0,tx.execute { material.reconcileApply(2).createdCount })
        assertEquals(3L,jdbc.queryForObject("SELECT COUNT(*) FROM mail_attachment WHERE id IN (50,51,52)",Long::class.java))
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM expert_document WHERE mail_attachment_id=52",Long::class.java))
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM expert_document WHERE mail_attachment_id=51",Long::class.java))
        assertEquals(listOf(50L),material.listMaterials(2,0,10,null,null,null,null).items.map { it.attachmentId })
        println("MATERIAL_READ_CONTRACT $version: matrix=${ExpertMaterialEligibilityTest.cases.size}, owners=3, transferStates=6+legacy, pagination=10+3, reconcile=1/0")
    }

    private fun snapshot(jdbc: JdbcTemplate) = listOf("mail_attachment","expert_document","mail_attachment_transfer").associateWith {
        jdbc.queryForList("SELECT * FROM $it ORDER BY id")
    }

    private fun createSchema(jdbc: JdbcTemplate) {
        // Reduced unrelated parent columns, with actual production column names/types and FK chains.
        listOf(
            "CREATE TABLE expert_contact(id BIGINT PRIMARY KEY)",
            "CREATE TABLE mail_record(id BIGINT PRIMARY KEY, expert_contact_id BIGINT NOT NULL, direction VARCHAR(32), message_id VARCHAR(255), subject VARCHAR(255),received_at DATETIME,sender_account_code VARCHAR(64), FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id))",
            "CREATE TABLE inbound_mail_processing(id BIGINT PRIMARY KEY,expert_contact_id BIGINT,subject VARCHAR(255),received_at DATETIME,sender_account_code VARCHAR(64), FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id))",
            "CREATE TABLE manual_expert_material_upload(id BIGINT PRIMARY KEY,expert_contact_id BIGINT NOT NULL,uploaded_by VARCHAR(100) NOT NULL,created_at DATETIME(6) NOT NULL,FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id))",
            "CREATE TABLE mail_attachment(id BIGINT PRIMARY KEY AUTO_INCREMENT,mail_record_id BIGINT,inbound_processing_id BIGINT,manual_upload_id BIGINT,file_name VARCHAR(255) NOT NULL,content_type VARCHAR(255),file_size BIGINT,storage_path VARCHAR(1024),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(mail_record_id) REFERENCES mail_record(id),FOREIGN KEY(inbound_processing_id) REFERENCES inbound_mail_processing(id),FOREIGN KEY(manual_upload_id) REFERENCES manual_expert_material_upload(id))",
            "CREATE TABLE expert_document(id BIGINT PRIMARY KEY AUTO_INCREMENT,expert_contact_id BIGINT NOT NULL,mail_attachment_id BIGINT NOT NULL,document_type VARCHAR(64) NOT NULL,document_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id),FOREIGN KEY(mail_attachment_id) REFERENCES mail_attachment(id))",
            "CREATE TABLE mail_attachment_transfer(id BIGINT PRIMARY KEY AUTO_INCREMENT,attachment_id BIGINT UNIQUE,inbound_processing_id BIGINT,state VARCHAR(32) NOT NULL,bytes_downloaded BIGINT NOT NULL DEFAULT 0,encoded_size BIGINT,error_code VARCHAR(64),error_message VARCHAR(1024),FOREIGN KEY(attachment_id) REFERENCES mail_attachment(id),FOREIGN KEY(inbound_processing_id) REFERENCES inbound_mail_processing(id))"
        ).forEach { jdbc.execute(it + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci") }
    }
}
