package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ManualExpertMaterialUploadRepository
import com.weibo.talentintroduction.document.domain.ManualExpertMaterialUpload
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.document.repository.MaterialFileRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Semaphore

data class MaterialFileEdit(val displayName: String, val contentNote: String = "", val tags: List<String> = emptyList())

@Service
class MaterialFileService(
    private val query: ExpertMaterialService,
    private val metadata: MaterialFileRepository,
    private val documents: ExpertDocumentRepository,
    private val attachments: MailAttachmentRepository,
    private val uploads: ManualExpertMaterialUploadRepository,
    private val extractor: MaterialArchiveExtractor,
    private val properties: MailAttachmentStorageProperties,
    private val tx: TransactionTemplate
) {
    private val slots = Semaphore(2)
    private fun authorize(contactId: Long, id: Long) {
        val doc = documents.findFirstByMailAttachmentId(id) ?: throw NoSuchElementException("附件不存在")
        require(doc.expertContactId == contactId) { "附件不属于当前专家" }
        val attachment = attachments.findById(id).orElseThrow { NoSuchElementException("附件不存在") }
        val owner = query.resolveOwnerContact(attachment)
        require(owner == null || owner == contactId) { "附件来源不属于当前专家" }
    }
    fun edit(contactId: Long, id: Long, request: MaterialFileEdit) {
        authorize(contactId, id)
        val name = request.displayName.trim()
        require(name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() || it == '/' || it == '\\' }) { "文件显示名称须为 1–255 字符，不含路径或控制字符" }
        require(request.contentNote.length <= 4000) { "内容说明不能超过 4000 字符" }
        require(request.tags.size <= 20 && request.tags.all { it.isNotBlank() && it.trim().length <= 40 && it.none(Char::isISOControl) }) { "最多 20 个标签，每个 1–40 字符" }
        tx.executeWithoutResult {
            metadata.ensure(id); metadata.lock(id)
            require(!metadata.isDeleted(id)) { "请先恢复文件" }
            metadata.edit(id, name, request.contentNote.trim(), request.tags.map(String::trim).distinct())
        }
    }
    fun delete(contactId: Long, id: Long, cascade: Boolean) {
        authorize(contactId, id)
        tx.executeWithoutResult {
            metadata.ensure(id); metadata.lock(id)
            metadata.deleted(id, true)
            if (cascade) metadata.forContact(contactId).values.filter { it.parentAttachmentId == id }.forEach { metadata.deleted(it.attachmentId, true) }
        }
    }
    fun restore(contactId: Long, id: Long) {
        authorize(contactId, id)
        tx.executeWithoutResult { metadata.ensure(id); metadata.lock(id); metadata.deleted(id, false) }
    }

    /** Synchronous, bounded extraction; per-root DB lock makes retries idempotent across JVMs. */
    fun parse(contactId: Long, id: Long): Map<String, Int> {
        authorize(contactId, id)
        check(slots.tryAcquire()) { "解压任务繁忙，请稍后重试" }
        var directory: Path? = null
        var committed = false
        try {
            metadata.ensure(id)
            val count = tx.execute {
                metadata.lock(id)
                val info = metadata.forContact(contactId)[id]!!
                require(!info.deleted) { "请先恢复文件" }
                require(info.parentAttachmentId == null) { "解析后文件不再递归解压" }
                if (info.extractionState == "DONE") return@execute metadata.forContact(contactId).values.count { it.parentAttachmentId == id && !it.deleted }
                val ready = query.resolveReadyFile(contactId, id)
                require(Files.size(ready.path) <= 100L * 1024 * 1024) { "原始文件超过 100 MB 限制" }
                val root = Path.of(properties.basePath).toRealPath()
                val targetRoot = root.resolve("parsed-materials")
                Files.createDirectories(targetRoot)
                require(targetRoot.toRealPath().startsWith(root)) { "解析目录无效" }
                val dir = Files.createTempDirectory(targetRoot, "material-")
                directory = dir
                metadata.state(id, "PROCESSING")
                val archive = ready.attachment.fileName.lowercase().let { it.endsWith(".zip") || it.endsWith(".rar") }
                val files = if (archive) extractor.extract(ready.path, dir) else {
                    require(Files.size(ready.path) <= 100L * 1024 * 1024) { "独立文件超过 100 MB 限制" }
                    val target = dir.resolve("file")
                    Files.copy(ready.path, target)
                    listOf(ExtractedMaterialFile(ready.attachment.fileName, target, Files.size(target)))
                }
                val parsedOwner = requireNotNull(uploads.save(ManualExpertMaterialUpload(expertContactId = contactId,
                    uploadedBy = "文件解析", createdAt = LocalDateTime.now())).id)
                files.forEach { file ->
                    val name = file.entryPath.substringAfterLast('/')
                    val attachment = attachments.save(ready.attachment.copy(id = null, mailRecordId = null, inboundProcessingId = null, manualUploadId = parsedOwner, fileName = name,
                        contentType = contentType(name), fileSize = file.size, storagePath = file.path.toString(), createdAt = LocalDateTime.now()))
                    val childId = requireNotNull(attachment.id)
                    documents.save(ExpertDocument(expertContactId = contactId, mailAttachmentId = childId,
                        documentType = "OTHER", createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
                    metadata.addChild(childId, id, file.entryPath)
                }
                metadata.state(id, "DONE")
                files.size
            } ?: 0
            committed = true
            return mapOf("fileCount" to count)
        } catch (ex: Exception) {
            // Record error only for an eligible root, never overwrite DONE after a competing successful request.
            tx.executeWithoutResult {
                metadata.lock(id)
                val info = metadata.forContact(contactId)[id]
                if (info != null && !info.deleted && info.parentAttachmentId == null && info.extractionState != "DONE")
                    metadata.state(id, "FAILED", (if (ex is IllegalArgumentException || ex is IllegalStateException) ex.message ?: "解析失败" else "解析失败，请重试").take(500))
            }
            throw ex
        } finally {
            if (!committed) directory?.let { extractor.clean(it) }
            slots.release()
        }
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "txt", "md", "csv", "log" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        else -> "application/octet-stream"
    }
}
