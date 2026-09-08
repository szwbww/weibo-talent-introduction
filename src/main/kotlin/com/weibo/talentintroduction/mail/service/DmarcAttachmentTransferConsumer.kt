package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * 05 I-3：purpose=DMARC 的传输消费者（经 02 的 [AttachmentTransferPurposeConsumer] seam
 * 注册进 [AttachmentTransferWorker]，领取/下载由 worker 完成）。
 *
 * 职责（只处理已完整下载的机器报表 .part 临时文件，绝不经手专家材料/文档）：
 * - 有界解压：总解压 XML 上限 [MailAttachmentStorageProperties.dmarcMaxExtractedBytes]
 *   （默认 20MiB）、归档成员上限 [MailAttachmentStorageProperties.dmarcMaxArchiveMembers]
 *   （默认 10）；拒绝路径穿越与 zip-bomb/超限（任务 FAILED + 明确 errorCode，不静默跳过）；
 * - 每个解出的 XML 包装为原 [ReceivedMailAttachment] 形态，**先用原
 *   [DmarcReportParser] 确认解析非 null**，再交给原 [DmarcReportIngestService] 入库
 *   （parse-null 绝不能被当成成功——原 ingest 对 parse-null 是静默跳过，等于丢报表）；
 * - 绝不把压缩内容交给原 parser 的无界 readBytes 解压路径（包装名恒为 .xml 且内容已解压）；
 * - 成功后不残留机器报告临时文件（worker 在 consume 返回后删除 .part；本消费者也不落盘）；
 * - 解析失败抛 [AttachmentTransferConsumeError]（DMARC_PARSE_FAILED），worker 记任务
 *   FAILED、errorCode 可见；队列重试从头获取是第一版允许行为。
 */
@Service
class DmarcAttachmentTransferConsumer(
    private val parser: DmarcReportParser,
    private val ingestService: DmarcReportIngestService,
    private val properties: MailAttachmentStorageProperties
) : AttachmentTransferPurposeConsumer {

    private val log = LoggerFactory.getLogger(DmarcAttachmentTransferConsumer::class.java)

    override val purpose: String = MailAttachmentTransfer.PURPOSE_DMARC

    override fun consume(context: AttachmentTransferConsumeContext) {
        val extracted = boundedExtractXml(context.file)
        if (extracted.isEmpty()) {
            throw AttachmentTransferConsumeError(
                ERROR_DMARC_PARSE_FAILED,
                "DMARC archive contained no readable XML report"
            )
        }
        val wrapped = extracted.map { (name, bytes) ->
            ReceivedMailAttachment(
                fileName = name,
                contentType = "application/xml",
                // 已解压 XML；name 恒为 .xml，content 无 gzip/zip magic，
                // 原 parser 走直通分支，绝不把压缩内容交给其无界 readBytes。
                content = bytes,
                source = null
            )
        }
        // I-3：解析确认点先于入库——任一 XML 解析为 null 即整体失败可见可重试，
        // 不允许「parse-null 静默跳过」被误判为成功。
        for (attachment in wrapped) {
            if (parser.parse(attachment) == null) {
                log.warn(
                    "DMARC aggregate report XML could not be parsed (transferId={}, file={})",
                    context.transferId,
                    attachment.fileName
                )
                throw AttachmentTransferConsumeError(
                    ERROR_DMARC_PARSE_FAILED,
                    "DMARC aggregate report XML could not be parsed (file=${attachment.fileName})"
                )
            }
        }
        // 原入库（report_id 查重由原 IngestService 保持；重复报表不重复落库）。
        ingestService.ingest(wrapped)
    }

    // ------------------------------------------------------------------
    // 有界解压（内存流式；只读 .part 临时文件，从不写机器报告临时文件）
    // ------------------------------------------------------------------

    private data class ExtractedXml(val name: String, val bytes: ByteArray)

    private class Budget(private val maxBytes: Long) {
        var remaining: Long = maxBytes
        fun consume(count: Int) {
            if (count > remaining) {
                throw AttachmentTransferConsumeError(
                    ERROR_LIMIT_EXCEEDED,
                    "DMARC extracted XML exceeds the configured total byte limit " +
                        "($maxBytes bytes)"
                )
            }
            remaining -= count
        }
    }

    private fun boundedExtractXml(file: Path): List<ExtractedXml> {
        val magic = ByteArray(4)
        Files.newInputStream(file).use { raw ->
            readUpTo(raw, magic, magic.size)
        }
        return when {
            magic[0] == GZIP_MAGIC_0 && magic[1] == GZIP_MAGIC_1 -> extractGzip(file)
            magic[0] == ZIP_MAGIC_0 && magic[1] == ZIP_MAGIC_1 -> extractZip(file)
            else -> {
                val budget = Budget(properties.dmarcMaxExtractedBytes)
                listOf(ExtractedXml(RAW_XML_NAME, readBounded(Files.newInputStream(file), budget)))
            }
        }
    }

    private fun extractGzip(file: Path): List<ExtractedXml> {
        val budget = Budget(properties.dmarcMaxExtractedBytes)
        val bytes = Files.newInputStream(file).use { raw ->
            GZIPInputStream(raw).use { gzip -> readBounded(gzip, budget) }
        }
        return listOf(ExtractedXml(RAW_XML_NAME, bytes))
    }

    private fun extractZip(file: Path): List<ExtractedXml> {
        val members = mutableListOf<ExtractedXml>()
        val maxMembers = properties.dmarcMaxArchiveMembers
        val budget = Budget(properties.dmarcMaxExtractedBytes)
        Files.newInputStream(file).use { raw ->
            ZipInputStream(raw).use { zip ->
                var fileEntries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (isUnsafeEntryName(entry.name)) {
                        throw AttachmentTransferConsumeError(
                            ERROR_ARCHIVE_INVALID,
                            "DMARC archive member name is unsafe: ${entry.name}"
                        )
                    }
                    fileEntries++
                    if (fileEntries > maxMembers) {
                        throw AttachmentTransferConsumeError(
                            ERROR_LIMIT_EXCEEDED,
                            "DMARC archive exceeds the configured member limit ($maxMembers)"
                        )
                    }
                    val bytes = readBounded(zip, budget)
                    if (bytes.isNotEmpty()) {
                        members.add(ExtractedXml(entry.name, bytes))
                    }
                }
            }
        }
        return members
    }

    /** 拒绝绝对路径 / 盘符 / 含 `..` 路径段的归档成员名（路径穿越）。 */
    private fun isUnsafeEntryName(name: String): Boolean {
        if (name.startsWith("/")) return true
        if (name.length >= 2 && name[1] == ':') return true
        val normalized = name.replace('\\', '/')
        return normalized.split('/').any { it == ".." }
    }

    /** 有界读取到 EOF；超过 [Budget] 剩余额度立即抛 LIMIT_EXCEEDED（zip-bomb 防护）。 */
    private fun readBounded(input: InputStream, budget: Budget): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            budget.consume(n)
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    private fun readUpTo(input: InputStream, target: ByteArray, maxCount: Int): Int {
        var total = 0
        while (total < maxCount) {
            val n = input.read(target, total, maxCount - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    companion object {
        const val ERROR_DMARC_PARSE_FAILED = "DMARC_PARSE_FAILED"
        const val ERROR_LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
        const val ERROR_ARCHIVE_INVALID = "DMARC_ARCHIVE_INVALID"

        /** 无法从 .part 文件名推导原报表名时使用的安全名（只影响 parser 直通判定）。 */
        const val RAW_XML_NAME = "aggregate-report.xml"

        private const val GZIP_MAGIC_0: Byte = 0x1f.toByte()
        private const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
        private const val ZIP_MAGIC_0: Byte = 'P'.code.toByte()
        private const val ZIP_MAGIC_1: Byte = 'K'.code.toByte()
    }
}
