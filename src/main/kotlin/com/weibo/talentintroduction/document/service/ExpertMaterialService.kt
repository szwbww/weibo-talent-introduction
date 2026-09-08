package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AttachmentTransferService
import com.weibo.talentintroduction.mail.service.MailAttachmentService
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Locale
import java.util.NoSuchElementException

/**
 * 材料统一查询、所有权和文件就绪 API（06）。
 *
 * 单一读模型（I-1）：GET /api/expert-contacts/{id}/materials 只按已确认的
 * expert_document 归属展示材料；附件状态只来自 transfer 行 + 已验证的本地文件；
 * GET 从不登记任务、不写审核、不触网。所有权链 = document → attachment →
 * 其唯一 owner（mail_record 或 inbound_mail_processing）→ contact。
 *
 * 精确来源（I-2）：新附件经 transfer.inbound_processing_id 桥接来信；旧
 * processing-owner 附件直接取 owner；旧 mailRecord 附件只允许
 * 同账号+同专家+INBOUND 方向+非空 messageId 的**唯一** processing 候选匹配，
 * 歧义显式拒绝（SOURCE_AMBIGUOUS 诊断，不猜、不展示其它账号的信），
 * mail_record.source_inbound_id 永远不作为 processing id 使用。
 *
 * 就绪判定（I-3）：[resolveReadyFile] / [resolveReadyFileUnscoped] 集中两种
 * owner 的归属校验 + realpath/基目录/就绪判定，ExpertDocumentBrowseService、
 * DocumentTextExtractor、MailboxAttachmentService 一律委托本方法，无路径旁路。
 *
 * 注：@Service 显式 bean 名是为了不与 campaign 模块同名
 * ExpertMaterialService（材料目录状态）共用默认 bean 名
 * expertMaterialService（否则其中一个定义会被静默覆盖）。
 */
@Service("expertMaterialQueryService")
class ExpertMaterialService(
    private val properties: MailAttachmentStorageProperties,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val mailAttachmentTransferRepository: MailAttachmentTransferRepository,
    private val attachmentTransferService: AttachmentTransferService,
    private val mailAttachmentService: MailAttachmentService,
    private val jdbc: NamedParameterJdbcTemplate
) {

    // ------------------------------------------------------------------
    // 共享所有权与文件就绪（I-1/I-3）——旧三服务委托的统一入口
    // ------------------------------------------------------------------

    /**
     * 附件唯一 owner 的联系人 id：
     * - mail_record owner → record.expert_contact_id；
     * - processing owner → processing.expert_contact_id（未绑定来信返回 null）；
     * - 无 owner / owner 行缺失 → null。
     * 仅用于归属核验；不读取本地文件。
     */
    fun resolveOwnerContact(attachment: MailAttachment): Long? {
        val attachmentId = attachment.id ?: return null
        if (attachment.mailRecordId != null) {
            return mailRecordRepository.findByIdOrNull(attachment.mailRecordId)
                ?.expertContactId
        }
        if (attachment.inboundProcessingId != null) {
            return inboundMailProcessingRepository
                .findById(attachment.inboundProcessingId)
                .orElse(null)
                ?.expertContactId
        }
        error("Attachment $attachmentId has no owner (mail_record_id and inbound_processing_id both null)")
    }

    /**
     * 集中式就绪解析（联系人作用域）：doc 归属 + attachment 唯一 owner 归属 +
     * realpath/基目录/就绪。未就绪抛 [MaterialNotReadyException]（HTTP 409）；
     * 不存在抛 NoSuchElement（404）；归属/路径越权抛 IllegalArgumentException。
     */
    fun resolveReadyFile(contactId: Long, attachmentId: Long): ReadyFile {
        val attachment = mailAttachmentRepository.findById(attachmentId)
            .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
            ?: throw NoSuchElementException("Document not found for attachment $attachmentId")
        require(document.expertContactId == contactId) {
            "Document for attachment $attachmentId does not belong to expert contact $contactId"
        }
        val ownerContactId = resolveOwnerContact(attachment)
        require(ownerContactId == null || ownerContactId == contactId) {
            "Attachment $attachmentId does not belong to expert contact $contactId"
        }
        return resolveFileReady(attachment)
    }

    /**
     * 集中式就绪解析（无联系人作用域，mailbox 附件下载用）：只做
     * attachment 就绪 + realpath/基目录校验。消息级归属在 07 的
     * resolveMessageAttachments 调用链内完成，本方法不读 doc。
     */
    fun resolveReadyFileUnscoped(attachmentId: Long): ReadyFile {
        val attachment = mailAttachmentRepository.findById(attachmentId)
            .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
        return resolveFileReady(attachment)
    }

    private fun resolveFileReady(attachment: MailAttachment): ReadyFile {
        val attachmentId = attachment.id ?: error("Attachment id is required")
        val transfer = mailAttachmentTransferRepository.findByAttachmentId(attachmentId)
        val notReadyState = transfer?.state ?: STATE_SOURCE_UNAVAILABLE

        val rawStoragePath = attachment.storagePath
        if (rawStoragePath.isNullOrBlank()) {
            throw MaterialNotReadyException(
                attachmentId,
                notReadyState,
                "Attachment $attachmentId has no local file (storage_path is null)"
            )
        }
        val storagePath = Path.of(rawStoragePath).toAbsolutePath().normalize()
        if (!Files.exists(storagePath)) {
            throw MaterialNotReadyException(
                attachmentId,
                notReadyState,
                "File not found: ${attachment.fileName}"
            )
        }
        if (!Files.isRegularFile(storagePath)) {
            throw MaterialNotReadyException(
                attachmentId,
                notReadyState,
                "Not a regular file: ${attachment.fileName}"
            )
        }

        val realBasePath = Path.of(properties.basePath).toRealPath()
        val realStoragePath = storagePath.toRealPath()
        require(realStoragePath.startsWith(realBasePath)) {
            "Attachment path is outside configured base path"
        }
        return ReadyFile(attachment, realStoragePath)
    }

    /** 磁盘就绪核验（与 [resolveFileReady] 同一谓词；只读，不抛异常）。 */
    private fun isFileReady(attachment: MailAttachment): Boolean {
        val rawStoragePath = attachment.storagePath
        if (rawStoragePath.isNullOrBlank()) {
            return false
        }
        return try {
            val storagePath = Path.of(rawStoragePath).toAbsolutePath().normalize()
            if (!Files.exists(storagePath) || !Files.isRegularFile(storagePath)) {
                return false
            }
            val realBasePath = Path.of(properties.basePath).toRealPath()
            val realStoragePath = storagePath.toRealPath()
            realStoragePath.startsWith(realBasePath)
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // 内容类型与能力（供材料 items 判断）
    // ------------------------------------------------------------------

    fun isPreviewableContentType(contentType: String): Boolean =
        contentType == "application/pdf" ||
            contentType.startsWith("image/") ||
            contentType.startsWith("text/")

    /** analysisSupported：只表示 PDF/text 格式可解析；未落地 PDF 仍可勾选后获取。 */
    fun isAnalysisSupportedContentType(contentType: String): Boolean =
        contentType == "application/pdf" || contentType.startsWith("text/")

    // ------------------------------------------------------------------
    // 材料列表读模型（I-1/I-4）
    // ------------------------------------------------------------------

    private val materialRowMapper = RowMapper<MaterialRow> { rs, _ ->
        MaterialRow(
            documentId = rs.getLong("document_id"),
            documentType = rs.getString("document_type"),
            documentStatus = rs.getString("document_status"),
            attachmentId = rs.getLong("attachment_id"),
            fileName = rs.getString("file_name"),
            contentType = rs.getString("content_type"),
            fileSize = rs.getLongOrNull("file_size"),
            storagePath = rs.getString("storage_path"),
            attachmentCreatedAt = rs.getTimestampOrNull("attachment_created_at"),
            mailRecordId = rs.getLongOrNull("mail_record_id"),
            attachmentInboundProcessingId = rs.getLongOrNull("attachment_inbound_processing_id"),
            transferId = rs.getLongOrNull("transfer_id"),
            transferState = rs.getString("transfer_state"),
            transferInboundProcessingId = rs.getLongOrNull("transfer_inbound_processing_id"),
            bytesDownloaded = rs.getLong("bytes_downloaded"),
            encodedSize = rs.getLongOrNull("encoded_size"),
            errorCode = rs.getString("error_code"),
            errorMessage = rs.getString("error_message"),
            pSubject = rs.getString("p_subject"),
            pReceivedAt = rs.getTimestampOrNull("p_received_at"),
            pAccountCode = rs.getString("p_account_code"),
            pExpertContactId = rs.getLongOrNull("p_expert_contact_id"),
            mrSubject = rs.getString("mr_subject"),
            mrReceivedAt = rs.getTimestampOrNull("mr_received_at"),
            mrAccountCode = rs.getString("mr_account_code"),
            mrDirection = rs.getString("mr_direction"),
            mrMessageId = rs.getString("mr_message_id"),
            mrExpertContactId = rs.getLongOrNull("mr_expert_contact_id")
        )
    }

    /**
     * 参数化只读投影：一次取该专家全部材料行（doc/attachment/transfer/来源消息
     * 的元数据，绝不读文件字节）。装配阶段在内存中做状态（真实文件核验）、
     * 来源、能力计算后统一过滤/排序/分页，保证 summary 与列表同快照且不受
     * 当前页影响；专家级材料行数有界（≤1000 目录级压力），单页渲染恒为 10 行。
     */
    private fun loadMaterialRows(contactId: Long): List<MaterialRow> {
        return jdbc.query(
            MATERIAL_LIST_SQL,
            MapSqlParameterSource("contactId", contactId),
            materialRowMapper
        )
    }

    /** 全部 processing 候选（材料旧关系唯一匹配 + 07 resolveMessageAttachments 共用语义）。 */
    private fun loadProcessingCandidates(contactId: Long): List<ProcessingCandidateRow> =
        inboundMailProcessingRepository.findAllByExpertContactId(contactId)
            .mapNotNull { p ->
                val id = p.id ?: return@mapNotNull null
                ProcessingCandidateRow(id, p.messageId, p.senderAccountCode)
            }

    /**
     * GET 材料页。校验：page≥0；size 默认 10、1..100；state 必须为存储状态枚举；
     * source/sourceId 必须成对且 type 合法。任何校验失败 IllegalArgumentException(400)。
     */
    fun listMaterials(
        contactId: Long,
        page: Int,
        size: Int,
        q: String?,
        source: String?,
        sourceId: Long?,
        state: String?
    ): ExpertMaterialPage {
        require(page >= 0) { "page must be >= 0" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
        val normalizedState = state?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
        if (normalizedState != null) {
            require(normalizedState in STORAGE_STATES) {
                "Unknown storage state: $state"
            }
        }
        if (source != null || sourceId != null) {
            require(source != null && sourceId != null) {
                "source and sourceId must be provided together"
            }
            require(source in SOURCE_TYPES) {
                "Unknown source type: $source"
            }
        }
        val rows = loadMaterialRows(contactId)
        val candidates = loadProcessingCandidates(contactId)
        return assemblePage(contactId, rows, candidates, page, size, q, source, sourceId, normalizedState)
    }

    /** 纯装配/过滤/分页/统计（同一快照内完成；summary 恒为该专家全体材料）。 */
    internal fun assemblePage(
        contactId: Long,
        rows: List<MaterialRow>,
        candidates: List<ProcessingCandidateRow>,
        page: Int,
        size: Int,
        q: String?,
        source: String?,
        sourceId: Long?,
        state: String?
    ): ExpertMaterialPage {
        val resolved = rows.map { row -> resolveItem(contactId, row, candidates) }

        val query = q?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
        val filtered = resolved.filter { entry ->
            val item = entry.item
            (query == null || item.fileName.lowercase(Locale.ROOT).contains(query)) &&
                (source == null || (item.source?.type == source && item.source.id == sourceId)) &&
                (state == null || item.storageState == state)
        }.sortedWith(
            compareByDescending<ResolvedMaterial> { it.sortTime }
                .thenByDescending { it.item.attachmentId }
        )

        val total = filtered.size.toLong()
        val fromIndex = (page.toLong() * size).toInt().coerceAtMost(filtered.size)
        val pageItems = filtered.drop(fromIndex).take(size).map { it.item }
        return ExpertMaterialPage(
            items = pageItems,
            total = total,
            page = page,
            size = size,
            summary = summarize(resolved.map { it.row })
        )
    }

    private fun resolveItem(
        contactId: Long,
        row: MaterialRow,
        candidates: List<ProcessingCandidateRow>
    ): ResolvedMaterial {
        val contentType = resolveContentType(row)
        val ownerValid = when {
            row.attachmentInboundProcessingId != null ->
                row.pExpertContactId == null || row.pExpertContactId == contactId
            row.mailRecordId != null -> row.mrExpertContactId == contactId
            else -> false
        }
        val sourceResolution = resolveSource(contactId, row, candidates)
        val sourceError = if (sourceResolution.refused) {
            MaterialItemError(code = SOURCE_AMBIGUOUS_CODE, message = SOURCE_AMBIGUOUS_MESSAGE)
        } else {
            null
        }
        val transferError = if (!sourceResolution.refused &&
            (row.transferState == MailAttachmentTransfer.STATE_FAILED ||
                row.transferState == MailAttachmentTransfer.STATE_SOURCE_UNAVAILABLE)
        ) {
            MaterialItemError(
                code = row.errorCode ?: row.transferState,
                message = row.errorMessage
            )
        } else {
            null
        }
        val storageState = storageStateOf(row)
        val fileReady = isFileReady(row)
        val canDownload = fileReady && ownerValid
        val analysisSupported = isAnalysisSupportedContentType(contentType)
        return ResolvedMaterial(
            row = row,
            item = ExpertMaterialItem(
                attachmentId = row.attachmentId,
                documentId = row.documentId,
                source = sourceResolution.source,
                fileName = row.fileName,
                contentType = contentType,
                documentType = row.documentType,
                documentStatus = row.documentStatus,
                actualSize = row.fileSize,
                encodedSize = row.encodedSize,
                storageState = storageState,
                bytesDownloaded = row.bytesDownloaded,
                error = sourceError ?: transferError,
                canFetch = !fileReady &&
                    row.transferId != null &&
                    row.transferState in MailAttachmentTransfer.REQUESTABLE_STATES,
                canDownload = canDownload,
                canPreview = canDownload && isPreviewableContentType(contentType),
                analysisSupported = analysisSupported,
                canAnalyze = analysisSupported && fileReady && ownerValid,
                downloadUrl = if (canDownload) {
                    "/api/expert-contacts/$contactId/attachments/${row.attachmentId}/download"
                } else {
                    null
                },
                previewUrl = if (canDownload) {
                    "/api/expert-contacts/$contactId/attachments/${row.attachmentId}/preview"
                } else {
                    null
                }
            ),
            sortTime = sourceResolution.source?.receivedAt ?: row.attachmentCreatedAt
        )
    }

    private data class SourceResolution(
        val source: MaterialSource?,
        val refused: Boolean
    )

    /**
     * 精确来源解析（I-2）：
     * 1. transfer.inbound_processing_id 桥接（新附件）；
     * 2. attachment.inbound_processing_id 直接 owner（旧 processing 附件）；
     * 3. 旧 mailRecord owner：同账号/同专家/INBOUND/非空 messageId 唯一候选
     *    processing → 归属 processing；0 候选 → 该 mail_record 自身；
     *    ≥2 候选 → 显式拒绝（SOURCE_AMBIGUOUS，来源待核对）。
     * sourceInboundId 永不当作 processing id。
     */
    private fun resolveSource(
        contactId: Long,
        row: MaterialRow,
        candidates: List<ProcessingCandidateRow>
    ): SourceResolution {
        val processingId = row.transferInboundProcessingId ?: row.attachmentInboundProcessingId
        if (processingId != null) {
            val otherContact = row.pExpertContactId != null && row.pExpertContactId != contactId
            if (otherContact) {
                return SourceResolution(null, refused = true)
            }
            return SourceResolution(
                MaterialSource(
                    type = SOURCE_TYPE_INBOUND_PROCESSING,
                    id = processingId,
                    subject = row.pSubject,
                    receivedAt = row.pReceivedAt,
                    accountCode = row.pAccountCode
                ),
                refused = false
            )
        }
        if (row.mailRecordId == null || row.mrExpertContactId != contactId) {
            return SourceResolution(null, refused = true)
        }
        val messageId = row.mrMessageId
        if (row.mrDirection != "INBOUND" || messageId.isNullOrBlank()) {
            return SourceResolution(mailRecordSource(row), refused = false)
        }
        val matching = candidates.filter { candidate ->
            candidate.messageId == messageId &&
                (row.mrAccountCode == null || candidate.accountCode == row.mrAccountCode)
        }
        return when {
            matching.size == 1 -> {
                val candidate = matching.single()
                // 唯一归属：补读 processing 行以展示真实 subject/receivedAt/账号。
                val processing = inboundMailProcessingRepository.findById(candidate.id).orElse(null)
                SourceResolution(
                    MaterialSource(
                        type = SOURCE_TYPE_INBOUND_PROCESSING,
                        id = candidate.id,
                        subject = processing?.subject,
                        receivedAt = processing?.receivedAt,
                        accountCode = processing?.senderAccountCode
                    ),
                    refused = false
                )
            }
            matching.isEmpty() -> SourceResolution(mailRecordSource(row), refused = false)
            else -> SourceResolution(null, refused = true)
        }
    }

    private fun mailRecordSource(row: MaterialRow): MaterialSource =
        MaterialSource(
            type = SOURCE_TYPE_MAIL_RECORD,
            id = row.mailRecordId,
            subject = row.mrSubject,
            receivedAt = row.mrReceivedAt,
            accountCode = row.mrAccountCode
        )

    private fun resolveContentType(row: MaterialRow): String {
        if (!row.contentType.isNullOrBlank()) {
            return row.contentType
        }
        val name = row.fileName.lowercase(Locale.ROOT)
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".log") || name.endsWith(".md") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * 状态口径（I-1/I-4）：有 transfer 行 → 该行状态；无 transfer 行（历史附件）
     * → 仅当真实文件经 realpath/基目录验证为 STORED，否则 SOURCE_UNAVAILABLE。
     * unknown 实际大小 = null（真实 0B 文件仍为 0），encodedSize 独立。
     */
    internal fun storageStateOf(row: MaterialRow): String {
        if (row.transferState != null) {
            return row.transferState
        }
        return if (isFileReady(row)) MailAttachmentTransfer.STATE_STORED else STATE_SOURCE_UNAVAILABLE
    }

    internal fun isFileReady(row: MaterialRow): Boolean {
        val rawStoragePath = row.storagePath
        if (rawStoragePath.isNullOrBlank()) {
            return false
        }
        return try {
            val storagePath = Path.of(rawStoragePath).toAbsolutePath().normalize()
            if (!Files.exists(storagePath) || !Files.isRegularFile(storagePath)) {
                return false
            }
            val realBasePath = Path.of(properties.basePath).toRealPath()
            val realStoragePath = storagePath.toRealPath()
            realStoragePath.startsWith(realBasePath)
        } catch (e: Exception) {
            false
        }
    }

    /** summary 统计该专家全体材料（与筛选无关）；total = rows.size。 */
    private fun summarize(rows: List<MaterialRow>): ExpertMaterialSummary {
        var stored = 0L
        var metadataOnly = 0L
        var active = 0L
        var failed = 0L
        var sourceUnavailable = 0L
        val defaultCandidates = mutableListOf<Long>()
        rows.forEach { row ->
            when (storageStateOf(row)) {
                MailAttachmentTransfer.STATE_STORED -> stored++
                MailAttachmentTransfer.STATE_METADATA_ONLY -> {
                    metadataOnly++
                    if (row.documentType in DEFAULT_ANALYSIS_DOCUMENT_TYPES &&
                        isAnalysisSupportedContentType(resolveContentType(row))
                    ) {
                        defaultCandidates += row.attachmentId
                    }
                }
                MailAttachmentTransfer.STATE_QUEUED, MailAttachmentTransfer.STATE_DOWNLOADING -> active++
                MailAttachmentTransfer.STATE_FAILED -> failed++
                else -> sourceUnavailable++
            }
        }
        return ExpertMaterialSummary(
            total = rows.size.toLong(),
            stored = stored,
            metadataOnly = metadataOnly,
            active = active,
            failed = failed,
            sourceUnavailable = sourceUnavailable,
            defaultAnalysisAttachmentIds = defaultCandidates.sorted()
        )
    }

    // ------------------------------------------------------------------
    // POST transfers（I-3）：登录 + 逐 ID 归属先全量校验，再排队
    // ------------------------------------------------------------------

    /**
     * 显式请求下载。前置校验：1..500 去重；每个 attachmentId 必须是该专家的
     * 已确认材料（expert_document 归属）；任一外来/未知 ID 整批拒绝（400，
     * 不入队）。容量耗尽 → [AttachmentTransferQueueFullException]（429）。
     * 入队委托 child-02 的 [AttachmentTransferService.enqueueMaterial]
     * （事务内再次核验归属后才翻转状态）。GET 路径永不调用本方法。
     */
    fun requestTransfers(
        contactId: Long,
        attachmentIds: List<Long>,
        requestedBy: String
    ): MaterialTransferBatchResponse {
        require(requestedBy.isNotBlank()) { "requestedBy is required" }
        val distinct = attachmentIds.distinct()
        require(distinct.isNotEmpty() && distinct.size <= AttachmentTransferService.MAX_ATTACHMENTS_PER_REQUEST) {
            "attachmentIds must contain 1..${AttachmentTransferService.MAX_ATTACHMENTS_PER_REQUEST} distinct ids"
        }
        val foreignIds = distinct.filter { attachmentId ->
            val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
            document == null || document.expertContactId != contactId
        }
        require(foreignIds.isEmpty()) {
            "Attachments ${foreignIds.sorted()} are not materials of expert contact $contactId"
        }
        if (mailAttachmentTransferRepository.countQueued() >= AttachmentTransferService.MAX_QUEUED_ROWS) {
            throw AttachmentTransferQueueFullException(
                "Attachment transfer queue is full (>= ${AttachmentTransferService.MAX_QUEUED_ROWS}); try again later"
            )
        }
        val enqueued = attachmentTransferService.enqueueMaterial(contactId, distinct, requestedBy)
        if (enqueued.queueFull) {
            // 校验与入队之间存在竞态导致队列在此期间打满：明确 429，
            // 已成功入队的部分保持 QUEUED，由调用方刷新状态后重试。
            throw AttachmentTransferQueueFullException(
                "Attachment transfer queue became full during enqueue; refresh and retry"
            )
        }
        return MaterialTransferBatchResponse(
            items = enqueued.items.map { item ->
                MaterialTransferItem(
                    attachmentId = item.attachmentId,
                    transferId = item.transferId,
                    state = item.transferState
                )
            },
            acceptedCount = enqueued.acceptedCount,
            alreadyReadyCount = enqueued.alreadyReadyCount
        )
    }

    // ------------------------------------------------------------------
    // POST reconcile（I-1/I-2）：显式历史修复（处理过但缺 ExpertDocument）
    // ------------------------------------------------------------------

    /** dryRun：只返回候选（processing.expert_contact_id=id 且 attachment 为
     *  processing owner 且无 document 的行），绝不写库。 */
    fun reconcileDryRun(contactId: Long): ReconcileResult {
        val candidates = findReconcileCandidates(contactId, lock = false)
        return ReconcileResult(candidateAttachmentIds = candidates.map { it.attachmentId }, createdCount = 0)
    }

    /**
     * apply：事务 + 行锁下幂等补缺 ExpertDocument（继承原 attachmentId 与本地
     * 文件；default PENDING_REVIEW；documentType 沿用 filename 分类）。
     * 重复执行不产生第二条 doc。无 IMAP、无审核覆盖、歧义 mailRecord 行不补链。
     */
    @Transactional
    fun reconcileApply(contactId: Long): ReconcileResult {
        val locked = findReconcileCandidates(contactId, lock = true)
        val createdIds = mutableListOf<Long>()
        val now = LocalDateTime.now()
        locked.forEach { candidate ->
            if (expertDocumentRepository.findFirstByMailAttachmentId(candidate.attachmentId) != null) {
                return@forEach
            }
            expertDocumentRepository.save(
                ExpertDocument(
                    expertContactId = contactId,
                    mailAttachmentId = candidate.attachmentId,
                    documentType = mailAttachmentService.inferDocumentType(candidate.fileName).name,
                    documentStatus = DocumentStatus.PENDING_REVIEW.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
            createdIds += candidate.attachmentId
        }
        return ReconcileResult(candidateAttachmentIds = createdIds, createdCount = createdIds.size)
    }

    private fun findReconcileCandidates(contactId: Long, lock: Boolean): List<ReconcileCandidate> {
        val sql = if (lock) RECONCILE_CANDIDATES_SQL_FOR_UPDATE else RECONCILE_CANDIDATES_SQL
        return jdbc.query(
            sql,
            MapSqlParameterSource("contactId", contactId),
            RowMapper<ReconcileCandidate> { rs, _ ->
                ReconcileCandidate(
                    attachmentId = rs.getLong("id"),
                    fileName = rs.getString("file_name")
                )
            }
        )
    }

    private data class ReconcileCandidate(val attachmentId: Long, val fileName: String)

    // ------------------------------------------------------------------
    // resolveMessageAttachments：按消息（source/id）取附件（07 MailboxService
    // 将委托本方法；旧 MailboxService 本阶段不改）。语义 = I-2 精确来源：
    // bridge / 直接 owner / 严格唯一旧关系。
    // ------------------------------------------------------------------

    /**
     * INBOUND_PROCESSING：直接 owner 附件 + transfer 桥接附件；两者皆无且该
     * processing 有 messageId 时，按 同账号+同专家+INBOUND+非空 messageId 严格
     * 唯一匹配历史 mail_record，恰好 1 条才回退其附件；0/多条一律拒绝（空列表，
     * 绝不展示其它账号/其它邮件的文件）。
     * MAIL_RECORD：该 record 的直接附件（调用方已按 scope 校验归属）。
     */
    fun resolveMessageAttachments(source: String, id: Long): List<MailAttachment> {
        return when (source) {
            SOURCE_TYPE_MAIL_RECORD -> {
                val record = mailRecordRepository.findByIdOrNull(id)
                    ?: return emptyList()
                if (record.direction != "INBOUND") {
                    // 历史 INBOUND record 才承载来件附件；OUTBOUND record 的附件
                    // 展示需求不在 mailbox 附件契约内，返回空（不猜）。
                    return emptyList()
                }
                mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(id)
            }
            SOURCE_TYPE_INBOUND_PROCESSING -> {
                val byDirect = mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(id)
                if (byDirect.isNotEmpty()) {
                    return byDirect
                }
                val bridged = bridgedAttachmentIdsByProcessing(id).mapNotNull { attachmentId ->
                    mailAttachmentRepository.findById(attachmentId).orElse(null)
                }
                if (bridged.isNotEmpty()) {
                    return bridged
                }
                val processing = inboundMailProcessingRepository.findById(id).orElse(null)
                    ?: return emptyList()
                val messageId = processing.messageId
                val contactId = processing.expertContactId
                if (messageId.isNullOrBlank() || contactId == null) {
                    return emptyList()
                }
                val legacy = legacyInboundRecordIdsByMessage(
                    messageId = messageId,
                    expertContactId = contactId,
                    accountCode = processing.senderAccountCode
                )
                if (legacy.size != 1) {
                    // 0 = 无旧 record；>1 = 跨账号/重复投递歧义。都不猜。
                    return emptyList()
                }
                mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(legacy.single())
            }
            else -> throw IllegalArgumentException("Unknown mailbox source: $source")
        }
    }

    /** transfer 桥接附件（material 附件行唯一对应一个 transfer 行）。 */
    private fun bridgedAttachmentIdsByProcessing(processingId: Long): List<Long> =
        jdbc.queryForList(
            """
            SELECT attachment_id FROM mail_attachment_transfer
             WHERE inbound_processing_id = :processingId
               AND attachment_id IS NOT NULL
             ORDER BY id
            """,
            MapSqlParameterSource("processingId", processingId),
            Long::class.java
        )

    /**
     * 严格唯一旧关系：同账号/同专家/INBOUND/非空 messageId 的历史 record。
     * account 为空的历史行无法按账号甄别，仅在同 expert+同 messageId 唯一时接受。
     */
    private fun legacyInboundRecordIdsByMessage(
        messageId: String,
        expertContactId: Long,
        accountCode: String
    ): List<Long> =
        jdbc.queryForList(
            """
            SELECT id FROM mail_record
             WHERE direction = 'INBOUND'
               AND expert_contact_id = :expertContactId
               AND message_id = :messageId
               AND (sender_account_code IS NULL OR sender_account_code = :accountCode)
             ORDER BY id
            """,
            MapSqlParameterSource()
                .addValue("expertContactId", expertContactId)
                .addValue("messageId", messageId)
                .addValue("accountCode", accountCode),
            Long::class.java
        )

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val SOURCE_TYPE_INBOUND_PROCESSING = "INBOUND_PROCESSING"
        const val SOURCE_TYPE_MAIL_RECORD = "MAIL_RECORD"
        const val SOURCE_AMBIGUOUS_CODE = "SOURCE_AMBIGUOUS"
        const val SOURCE_AMBIGUOUS_MESSAGE = "来源待核对"
        const val STATE_SOURCE_UNAVAILABLE = MailAttachmentTransfer.STATE_SOURCE_UNAVAILABLE

        val STORAGE_STATES = setOf(
            MailAttachmentTransfer.STATE_METADATA_ONLY,
            MailAttachmentTransfer.STATE_QUEUED,
            MailAttachmentTransfer.STATE_DOWNLOADING,
            MailAttachmentTransfer.STATE_STORED,
            MailAttachmentTransfer.STATE_FAILED,
            MailAttachmentTransfer.STATE_SOURCE_UNAVAILABLE
        )
        val SOURCE_TYPES = setOf(SOURCE_TYPE_INBOUND_PROCESSING, SOURCE_TYPE_MAIL_RECORD)

        /** 09 AI 选件默认文档类型（与旧 AI_ANALYSIS_DEFAULT_TYPES 一致）。 */
        val DEFAULT_ANALYSIS_DOCUMENT_TYPES = setOf("CV", "PHD_DEGREE", "MASTER_DEGREE", "BACHELOR_DEGREE")

        private const val MATERIAL_LIST_SQL = """
            SELECT d.id AS document_id,
                   d.document_type AS document_type,
                   d.document_status AS document_status,
                   a.id AS attachment_id,
                   a.file_name AS file_name,
                   a.content_type AS content_type,
                   a.file_size AS file_size,
                   a.storage_path AS storage_path,
                   a.created_at AS attachment_created_at,
                   a.mail_record_id AS mail_record_id,
                   a.inbound_processing_id AS attachment_inbound_processing_id,
                   t.id AS transfer_id,
                   t.state AS transfer_state,
                   t.inbound_processing_id AS transfer_inbound_processing_id,
                   t.bytes_downloaded AS bytes_downloaded,
                   t.encoded_size AS encoded_size,
                   t.error_code AS error_code,
                   t.error_message AS error_message,
                   p.subject AS p_subject,
                   p.received_at AS p_received_at,
                   p.sender_account_code AS p_account_code,
                   p.expert_contact_id AS p_expert_contact_id,
                   mr.subject AS mr_subject,
                   mr.received_at AS mr_received_at,
                   mr.sender_account_code AS mr_account_code,
                   mr.direction AS mr_direction,
                   mr.message_id AS mr_message_id,
                   mr.expert_contact_id AS mr_expert_contact_id
              FROM expert_document d
              JOIN mail_attachment a ON a.id = d.mail_attachment_id
              LEFT JOIN mail_attachment_transfer t ON t.attachment_id = a.id
              LEFT JOIN inbound_mail_processing p ON p.id = COALESCE(t.inbound_processing_id, a.inbound_processing_id)
              LEFT JOIN mail_record mr ON mr.id = a.mail_record_id
             WHERE d.expert_contact_id = :contactId
        """

        private const val RECONCILE_CANDIDATES_SQL = """
            SELECT a.id AS id, a.file_name AS file_name
              FROM mail_attachment a
             WHERE a.inbound_processing_id IN (
                     SELECT p.id FROM inbound_mail_processing p
                      WHERE p.expert_contact_id = :contactId)
               AND NOT EXISTS (
                     SELECT 1 FROM expert_document d
                      WHERE d.mail_attachment_id = a.id)
             ORDER BY a.id
        """

        private const val RECONCILE_CANDIDATES_SQL_FOR_UPDATE = """
            SELECT a.id AS id, a.file_name AS file_name
              FROM mail_attachment a
             WHERE a.inbound_processing_id IN (
                     SELECT p.id FROM inbound_mail_processing p
                      WHERE p.expert_contact_id = :contactId)
               AND NOT EXISTS (
                     SELECT 1 FROM expert_document d
                      WHERE d.mail_attachment_id = a.id)
             ORDER BY a.id
            FOR UPDATE
        """
    }
}

private fun java.sql.ResultSet.getLongOrNull(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun java.sql.ResultSet.getTimestampOrNull(column: String): LocalDateTime? {
    val value = getTimestamp(column)
    return value?.toLocalDateTime()
}

// ----------------------------------------------------------------------
// 领域 DTO（与查询同文件；固定字段集，绝不输出 storagePath/凭据/workerToken）
// ----------------------------------------------------------------------

/** 文件就绪解析结果：attachment + 已通过 realpath/基目录校验的真实路径。 */
data class ReadyFile(
    val attachment: MailAttachment,
    val path: Path
)

/** 材料来源消息。type/id 为空表示来源待核对（歧义拒绝），不猜测。 */
data class MaterialSource(
    val type: String?,
    val id: Long?,
    val subject: String?,
    val receivedAt: LocalDateTime?,
    val accountCode: String?
)

data class MaterialItemError(
    val code: String?,
    val message: String?
)

/** 材料 items 固定字段（master API 契约）；actualSize/encodedSize/error/URL 可空。 */
data class ExpertMaterialItem(
    val attachmentId: Long,
    val documentId: Long,
    val source: MaterialSource?,
    val fileName: String,
    val contentType: String,
    val documentType: String,
    val documentStatus: String,
    val actualSize: Long?,
    val encodedSize: Long?,
    val storageState: String,
    val bytesDownloaded: Long,
    val error: MaterialItemError?,
    val canFetch: Boolean,
    val canDownload: Boolean,
    val canPreview: Boolean,
    val analysisSupported: Boolean,
    val canAnalyze: Boolean,
    val downloadUrl: String?,
    val previewUrl: String?
)

/** summary 统计该专家全体材料（不受当前筛选影响）；默认候选完整返回（不静默截断）。 */
data class ExpertMaterialSummary(
    val total: Long,
    val stored: Long,
    val metadataOnly: Long,
    val active: Long,
    val failed: Long,
    val sourceUnavailable: Long,
    val defaultAnalysisAttachmentIds: List<Long>
)

data class ExpertMaterialPage(
    val items: List<ExpertMaterialItem>,
    val total: Long,
    val page: Int,
    val size: Int,
    val summary: ExpertMaterialSummary
)

data class MaterialTransferItem(
    val attachmentId: Long,
    val transferId: Long?,
    val state: String?
)

data class MaterialTransferBatchResponse(
    val items: List<MaterialTransferItem>,
    val acceptedCount: Int,
    val alreadyReadyCount: Int
)

data class ReconcileResult(
    val candidateAttachmentIds: List<Long>,
    val createdCount: Int
)

/** 旧下载/预览/AI 路径在文件未就绪时的统一异常 → 409 MATERIAL_NOT_READY。 */
class MaterialNotReadyException(
    val attachmentId: Long,
    val state: String?,
    message: String
) : RuntimeException(message)

/** 附件传输队列容量耗尽 → 429。 */
class AttachmentTransferQueueFullException(message: String) : RuntimeException(message)

// ----------------------------------------------------------------------
// 内部装配行（同一 SQL 快照；不暴露到 API）
// ----------------------------------------------------------------------

internal data class MaterialRow(
    val documentId: Long,
    val documentType: String,
    val documentStatus: String,
    val attachmentId: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long?,
    val storagePath: String?,
    val attachmentCreatedAt: LocalDateTime?,
    val mailRecordId: Long?,
    val attachmentInboundProcessingId: Long?,
    val transferId: Long?,
    val transferState: String?,
    val transferInboundProcessingId: Long?,
    val bytesDownloaded: Long,
    val encodedSize: Long?,
    val errorCode: String?,
    val errorMessage: String?,
    val pSubject: String?,
    val pReceivedAt: LocalDateTime?,
    val pAccountCode: String?,
    val pExpertContactId: Long?,
    val mrSubject: String?,
    val mrReceivedAt: LocalDateTime?,
    val mrAccountCode: String?,
    val mrDirection: String?,
    val mrMessageId: String?,
    val mrExpertContactId: Long?
)

internal data class ProcessingCandidateRow(
    val id: Long,
    val messageId: String?,
    val accountCode: String
)

internal data class ResolvedMaterial(
    val row: MaterialRow,
    val item: ExpertMaterialItem,
    val sortTime: LocalDateTime?
)
