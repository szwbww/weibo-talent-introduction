package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.document.domain.MeetingVideoLimits
import com.weibo.talentintroduction.document.domain.MeetingVideoPage
import com.weibo.talentintroduction.document.domain.MeetingVideoRenameRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoView
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.util.UriUtils
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Semaphore

/**
 * 播放/下载解析结果：真实文件路径 + 展示名 + 扩展名派生的 Content-Type。
 * 只在本 service 与 controller 之间传递，绝不进入 JSON 响应。
 */
data class MeetingVideoFile(
    val path: Path,
    val displayFilename: String,
    val extension: String,
    val contentType: String,
    val byteLength: Long
)

/**
 * 04（I-1–I-10）：`expert_meeting_video` 与 `basePath/meeting-videos` 的唯一写者。
 *
 * - 身份：上传先 [ExpertProfileIdentityResolver.requireKnown]（01 canonical expertId）；
 *   列表/重命名/播放只做规范化，再用 `expert_key + video_id` 同时查元数据（归属不符 404），
 *   因此**播放不依赖 ES**，存量视频在 ES 临时故障时仍可拖动；
 * - 流式：expectedSize/名称/身份/容量/同名语义全部在**读请求体之前**校验（同名预检先查 canonical
 *   expert_key + NFC 后 display_filename，唯一索引仍是并发竞争的最终裁决），随后以固定 64 KiB 缓冲
 *   有界写入 `root/.tmp-<id>`，按实际读入字节计数，close 后同目录 ATOMIC_MOVE 到 `root/<id>`，
 *   再在独立短事务里 INSERT；数据库失败/唯一冲突删 final，IO 失败删 tmp；清理失败只吞 IOException
 *   （不覆盖主结果），且 finally **无条件释放**并发槽；
 * - 上限：单文件 [MeetingVideoLimits.MAX_BYTES]；同一个应用实例最多
 *   [MeetingVideoLimits.MAX_CONCURRENT_UPLOADS] 个上传（tryAcquire 失败 429）。
 *
 * [usableSpaceProbe] 只为「低磁盘 507」提供可注入的剩余空间读数（默认真实 FileStore），
 * 不改其它行为；生产路径不传参数即使用真实磁盘。
 */
@Service
class ExpertMeetingVideoService(
    private val properties: MailAttachmentStorageProperties,
    private val identityResolver: ExpertProfileIdentityResolver,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val usableSpaceProbe: (Path) -> Long = { root -> Files.getFileStore(root).usableSpace },
    /**
     * 删除清理（临时/最终产物）的注入点：默认真实 [Files.deleteIfExists]。
     * 只为「清理失败仍必须释放并发槽、且不得覆盖主结果」提供确定性 seam，不改其它行为。
     */
    private val deleteArtifact: (Path) -> Boolean = { path -> Files.deleteIfExists(path) }
) {
    /** 并发槽：每个应用实例 2 个，只在处理期持有（含流式写文件与短事务）。 */
    private val uploadSlots = Semaphore(MeetingVideoLimits.MAX_CONCURRENT_UPLOADS, true)

    /**
     * 原始字节流上传：`POST /api/experts/meeting-videos`（application/octet-stream）。
     * 只接受单个原始请求体，不解析 multipart、不把文件读进内存。
     */
    fun upload(
        request: MeetingVideoUploadRequest,
        username: String?,
        contextPath: String,
        body: InputStream
    ): MeetingVideoView {
        val expertKey = resolveExpertKey(request.expertId, request.level)
        val actor = requireActor(username)
        val expectedSize = parseExpectedSize(request.expectedSize)
        val originalFilename = originalBasename(request.originalName)
        val extension = extensionOf(originalFilename)
        val displayFilename = displayName(request.nameStem, originalFilename, extension)
        verifyDeclaredLength(request.declaredLength, expectedSize)
        val root = storageRoot()
        requireCapacity(root, expectedSize)
        // 04 I-5：NFC 后的完整名已知存在时在读请求体/占用并发槽之前返回固定 409；
        // 唯一索引与唯一冲突映射仍是并发竞争下的最终裁决（见下方 INSERT 分支）。
        if (displayNameExists(expertKey, displayFilename)) {
            throw MeetingVideoException.nameConflict(NAME_CONFLICT_MESSAGE)
        }

        if (!uploadSlots.tryAcquire()) {
            throw MeetingVideoException.busy(UPLOAD_BUSY_MESSAGE)
        }
        val id = UUID.randomUUID().toString()
        val finalPath = root.resolve(id)
        val tempPath = root.resolve(TEMP_FILE_PREFIX + id)
        try {
            val byteLength = writeStreamedFile(body, tempPath, finalPath, expectedSize)
            val now = LocalDateTime.now(BEIJING).truncatedTo(ChronoUnit.MILLIS)
            try {
                transactionTemplate.executeWithoutResult {
                    jdbcTemplate.update(INSERT_SQL, insertParams(id, expertKey, originalFilename, displayFilename, byteLength, actor, now))
                }
            } catch (ex: DuplicateKeyException) {
                deleteQuietly(finalPath)
                throw MeetingVideoException.nameConflict(NAME_CONFLICT_MESSAGE)
            } catch (ex: PessimisticLockingFailureException) {
                // 同名单并发 INSERT 时 InnoDB 可能在唯一索引上先报锁等待/死锁（本表单条 INSERT、
                // 除主键与该唯一索引外没有其它约束，因此这同样是「同名冲突」）。
                deleteQuietly(finalPath)
                throw MeetingVideoException.nameConflict(NAME_CONFLICT_MESSAGE)
            } catch (ex: MeetingVideoException) {
                deleteQuietly(finalPath)
                throw ex
            } catch (ex: Exception) {
                deleteQuietly(finalPath)
                throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
            }
            return MeetingVideoView(
                id = id,
                expertId = expertKey,
                originalFilename = originalFilename,
                displayFilename = displayFilename,
                extension = extension,
                byteLength = byteLength,
                uploadedBy = actor,
                uploadedAt = formatTime(now),
                updatedBy = actor,
                updatedAt = formatTime(now),
                version = INITIAL_VERSION,
                previewUrl = videoUrl(contextPath, id, expertKey, PREVIEW_SUFFIX),
                downloadUrl = videoUrl(contextPath, id, expertKey, DOWNLOAD_SUFFIX)
            )
        } finally {
            // 写成功时 tmp 已被 ATOMIC_MOVE 移走，这里是 no-op；失败/中断分支在此清理临时文件。
            // 清理失败（IOException 等）绝不跳过槽释放：否则少量清理失败即可永久耗尽全部上传槽（04 I-6/I-9）。
            try {
                deleteQuietly(tempPath)
            } finally {
                uploadSlots.release()
            }
        }
    }

    /** 列表（只读）：page 从 0，size 默认 20 且 1..50，`uploaded_at DESC, id DESC`，无记录空数组。 */
    fun list(expertId: String?, page: Int?, size: Int?, contextPath: String): MeetingVideoPage {
        val expertKey = expertKey(expertId)
        val effectivePage = page ?: 0
        val effectiveSize = size ?: MeetingVideoLimits.DEFAULT_PAGE_SIZE
        if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > MeetingVideoLimits.MAX_PAGE_SIZE) {
            throw MeetingVideoException.invalidName(PAGE_INVALID_MESSAGE)
        }
        val params = MapSqlParameterSource("expertKey", expertKey)
        val total = try {
            jdbcTemplate.queryForObject(COUNT_SQL, params, Long::class.java) ?: 0L
        } catch (ex: DataAccessException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        if (total == 0L) {
            return MeetingVideoPage(emptyList(), 0L, effectivePage, effectiveSize)
        }
        val rows = try {
            jdbcTemplate.query(
                SELECT_PAGE_SQL,
                params
                    .addValue("limit", effectiveSize)
                    .addValue("offset", effectivePage.toLong() * effectiveSize),
                ROW_MAPPER
            )
        } catch (ex: DataAccessException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        return MeetingVideoPage(rows.map { view(it, contextPath) }, total, effectivePage, effectiveSize)
    }

    /**
     * 重命名：只改 `display_filename`/`version`/`updated_by`/`updated_at`，不搬磁盘文件、不改 URL。
     * expectedVersion 是 CAS 条件，旧版本 409 [MeetingVideoException.versionConflict]；
     * 同名撞唯一索引 409 [MeetingVideoException.nameConflict]。
     */
    fun rename(
        request: MeetingVideoRenameRequest,
        expertId: String?,
        videoId: String?,
        username: String?,
        contextPath: String
    ): MeetingVideoView {
        val expertKey = expertKey(expertId)
        val actor = requireActor(username)
        val id = videoId?.trim().orEmpty()
        val row = if (id.isEmpty()) null else readRow(id, expertKey)
        if (row == null) {
            throw MeetingVideoException.notFound(VIDEO_NOT_FOUND_MESSAGE)
        }
        val expectedVersion = request.expectedVersion
            ?: throw MeetingVideoException.invalidName("缺少 expectedVersion")
        if (request.nameStem.isNullOrBlank()) {
            throw MeetingVideoException.invalidName(EMPTY_STEM_MESSAGE)
        }
        val extension = extensionOf(row.originalFilename)
        val displayFilename = displayName(request.nameStem, row.originalFilename, extension)
        val now = LocalDateTime.now(BEIJING).truncatedTo(ChronoUnit.MILLIS)
        val updated = try {
            transactionTemplate.execute {
                val changed = jdbcTemplate.update(
                    RENAME_SQL,
                    MapSqlParameterSource("displayFilename", displayFilename)
                        .addValue("updatedBy", actor)
                        .addValue("updatedAt", now)
                        .addValue("id", row.id)
                        .addValue("expertKey", expertKey)
                        .addValue("expectedVersion", expectedVersion)
                )
                if (changed == 0) return@execute null
                readRow(row.id, expertKey)
            }
        } catch (ex: DuplicateKeyException) {
            throw MeetingVideoException.nameConflict(NAME_CONFLICT_MESSAGE)
        } catch (ex: DataAccessException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        val updatedRow = updated ?: throw MeetingVideoException.versionConflict(VERSION_CONFLICT_MESSAGE)
        return view(updatedRow, contextPath)
    }

    /**
     * 播放/下载解析：先按 `expert_key + video_id` 查元数据（归属不符 404），再核验
     * realPath 在真实存储根内、是普通文件、实际大小等于 `byte_length`；不返回绝对路径。
     */
    fun resolveFile(expertId: String?, videoId: String?): MeetingVideoFile {
        val expertKey = expertKey(expertId)
        val id = videoId?.trim().orEmpty()
        val row = if (id.isEmpty()) null else readRow(id, expertKey)
        if (row == null) {
            throw MeetingVideoException.notFound(VIDEO_NOT_FOUND_MESSAGE)
        }
        val root = storageRoot()
        val realRoot = try {
            root.toRealPath()
        } catch (ex: IOException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        val target = root.resolve(row.id)
        if (!Files.isRegularFile(target)) {
            throw MeetingVideoException.notFound(VIDEO_FILE_MISSING_MESSAGE)
        }
        val realPath = try {
            target.toRealPath()
        } catch (ex: IOException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        if (!realPath.startsWith(realRoot)) {
            // 符号链接越界：只可能是存储被外部篡改，固定 500 且不暴露路径。
            throw MeetingVideoException.storeFailed(INTEGRITY_FAILED_MESSAGE)
        }
        val size = try {
            Files.size(realPath)
        } catch (ex: IOException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        if (size != row.byteLength) {
            throw MeetingVideoException.storeFailed(INTEGRITY_FAILED_MESSAGE)
        }
        val extension = extensionOf(row.originalFilename)
        return MeetingVideoFile(
            path = realPath,
            displayFilename = row.displayFilename,
            extension = extension,
            contentType = MeetingVideoLimits.contentTypeOf(extension),
            byteLength = size
        )
    }

    // ------------------------------------------------------------------
    // 请求体校验（全部在读流之前完成）
    // ------------------------------------------------------------------

    /** 上传身份来自 01：已有档案行/联系人/ES 精确命中；ES 故障抛 503，绝不伪装 404。 */
    private fun resolveExpertKey(expertId: String?, level: ExpertIndexLevel): String =
        try {
            identityResolver.requireKnown(expertId, level)
        } catch (ex: ExpertManualProfileInvalidRequestException) {
            throw MeetingVideoException.invalidName(EXPERT_INVALID_MESSAGE)
        } catch (ex: ExpertProfileNotFoundException) {
            throw MeetingVideoException.notFound(EXPERT_NOT_FOUND_MESSAGE)
        } catch (ex: ExpertProfileLookupUnavailableException) {
            throw MeetingVideoException.lookupUnavailable(EXPERT_LOOKUP_MESSAGE, ex)
        }

    /**
     * 同名预检（04 I-5）：`expert_key + NFC 后 display_filename` 精确且区分大小写；
     * 与唯一索引同语义，只为已知冲突在读请求体/占槽之前省下一次完整传输；
     * 数据库不可用时按读取失败处理（500），绝不放行成「无同名」。
     */
    private fun displayNameExists(expertKey: String, displayFilename: String): Boolean =
        try {
            jdbcTemplate.query(
                SELECT_NAME_SQL,
                MapSqlParameterSource("expertKey", expertKey).addValue("displayFilename", displayFilename),
                RowMapper { _, _ -> true }
            ).isNotEmpty()
        } catch (ex: DataAccessException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }

    /**
     * 删除临时/最终产物：失败只吞 IOException（清理是尽力而为），既不能覆盖 409/400/413 等固定主结果，
     * 也不能让异常漏出 finally 而跳过并发槽释放；残留文件按 04「进程硬崩不保证自动清理」人工核对。
     */
    private fun deleteQuietly(path: Path) {
        try {
            deleteArtifact(path)
        } catch (ex: IOException) {
            // 已尽力：残留交给人工核对，不阻断主结果与槽释放。
        }
    }

    /** 上传/重命名的审计身份：只取会话用户名，超列宽报错，绝不截断成另一个用户。 */
    private fun requireActor(username: String?): String {
        val actor = username?.trim().orEmpty()
        if (actor.isEmpty()) {
            throw MeetingVideoException.invalidName(ACTOR_MISSING_MESSAGE)
        }
        if (actor.codePointCount(0, actor.length) > MeetingVideoLimits.MAX_ACTOR_CODE_POINTS) {
            throw MeetingVideoException.invalidName(ACTOR_TOO_LONG_MESSAGE)
        }
        return actor
    }

    /** expertId → 规范化 key（1..64 code point）；非法标识无人能匹配，按 400 请求描述无效处理。 */
    private fun expertKey(expertId: String?): String {
        val raw = expertId?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw MeetingVideoException.invalidName(EXPERT_INVALID_MESSAGE)
        }
        val key = ExpertIdNormalizer.normalize(raw)
        if (key.codePointCount(0, key.length) > MAX_EXPERT_KEY_CODE_POINTS) {
            throw MeetingVideoException.invalidName(EXPERT_INVALID_MESSAGE)
        }
        return key
    }

    private fun parseExpectedSize(raw: String?): Long {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
            ?: throw MeetingVideoException.sizeMismatch(SIZE_MISMATCH_MESSAGE)
        if (value < 0L) {
            throw MeetingVideoException.sizeMismatch(SIZE_MISMATCH_MESSAGE)
        }
        if (value == 0L) {
            throw MeetingVideoException.empty(EMPTY_VIDEO_MESSAGE)
        }
        if (value > MeetingVideoLimits.MAX_BYTES) {
            throw MeetingVideoException.tooLarge(TOO_LARGE_MESSAGE)
        }
        return value
    }

    private fun verifyDeclaredLength(declaredLength: Long?, expectedSize: Long) {
        if (declaredLength == null) return
        if (declaredLength > MeetingVideoLimits.MAX_BYTES) {
            throw MeetingVideoException.tooLarge(TOO_LARGE_MESSAGE)
        }
        if (declaredLength != expectedSize) {
            throw MeetingVideoException.sizeMismatch(SIZE_MISMATCH_MESSAGE)
        }
    }

    /** 原始名去路径（`/` 与 `\` 都算分隔符）后作为 basename；超长报 400，不截断。 */
    private fun originalBasename(raw: String?): String {
        val basename = raw?.trim().orEmpty().substringAfterLast('/').substringAfterLast('\\')
        if (basename.isEmpty()) {
            throw MeetingVideoException.invalidName(ORIGINAL_NAME_INVALID_MESSAGE)
        }
        if (basename.codePointCount(0, basename.length) > MeetingVideoLimits.MAX_ORIGINAL_NAME_CODE_POINTS) {
            throw MeetingVideoException.invalidName(ORIGINAL_NAME_TOO_LONG_MESSAGE)
        }
        return basename
    }

    /** 扩展名 = 最后后缀小写；白名单之外（含无后缀）一律 415。 */
    private fun extensionOf(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in MeetingVideoLimits.ALLOWED_EXTENSIONS) {
            throw MeetingVideoException.unsupportedType(UNSUPPORTED_TYPE_MESSAGE)
        }
        return extension
    }

    /**
     * `display_filename = NFC(trim(主体)) + "." + 固定小写扩展名`：完整 1..100 code point，
     * 主体非空、无控制字符、无 `/\:*?"<>|`、无尾部句点；超长要求修改而不是截断。
     * 主体缺省（上传未传/空白）时取原始 basename 去末扩展名。
     */
    private fun displayName(rawStem: String?, originalFilename: String, extension: String): String {
        val stemSource = rawStem?.takeIf { it.isNotBlank() } ?: originalFilename.substringBeforeLast('.')
        val stem = Normalizer.normalize(stemSource, Normalizer.Form.NFC).trim()
        if (stem.isEmpty()) {
            throw MeetingVideoException.invalidName(EMPTY_STEM_MESSAGE)
        }
        if (stem.any { it.isISOControl() }) {
            throw MeetingVideoException.invalidName(CONTROL_CHAR_MESSAGE)
        }
        if (stem.any { it in FORBIDDEN_STEM_CHARS }) {
            throw MeetingVideoException.invalidName(FORBIDDEN_CHAR_MESSAGE)
        }
        if (stem.endsWith(".")) {
            throw MeetingVideoException.invalidName(TRAILING_DOT_MESSAGE)
        }
        val fullName = "$stem.$extension"
        if (fullName.codePointCount(0, fullName.length) > MeetingVideoLimits.MAX_DISPLAY_NAME_CODE_POINTS) {
            throw MeetingVideoException.invalidName(DISPLAY_NAME_TOO_LONG_MESSAGE)
        }
        return fullName
    }

    // ------------------------------------------------------------------
    // 文件流程
    // ------------------------------------------------------------------

    /** `root = basePath/meeting-videos`；只有 UUID 会成为路径段，客户端文件名永不参与拼路径。 */
    private fun storageRoot(): Path {
        val root = Path.of(properties.basePath).toAbsolutePath().normalize().resolve(VIDEO_DIR)
        try {
            Files.createDirectories(root)
        } catch (ex: IOException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        return root
    }

    /** 写文件前的剩余空间检查：`usableSpace >= expectedSize + 64 MiB`，不足 507。 */
    private fun requireCapacity(root: Path, expectedSize: Long) {
        val required = expectedSize + MeetingVideoLimits.STORAGE_HEADROOM_BYTES
        val free = try {
            usableSpaceProbe(root)
        } catch (ex: Exception) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }
        if (free < required) {
            throw MeetingVideoException.storageFull(STORAGE_FULL_MESSAGE)
        }
    }

    /**
     * 有界流式落盘：64 KiB 缓冲、按实际读入字节计数、先写 `.tmp-<id>` 再同目录 ATOMIC_MOVE。
     * 超过上限立即 413；实际字节数与 expectedSize 不符（含流被截断）400；零字节 400。
     */
    private fun writeStreamedFile(body: InputStream, tempPath: Path, finalPath: Path, expectedSize: Long): Long {
        var written = 0L
        try {
            Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(MeetingVideoLimits.STREAM_BUFFER_BYTES)
                while (true) {
                    val read = body.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    written += read
                    if (written > MeetingVideoLimits.MAX_BYTES) {
                        throw MeetingVideoException.tooLarge(TOO_LARGE_MESSAGE)
                    }
                    if (written > expectedSize) {
                        throw MeetingVideoException.sizeMismatch(SIZE_MISMATCH_MESSAGE)
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (written == 0L) {
                throw MeetingVideoException.empty(EMPTY_VIDEO_MESSAGE)
            }
            if (written != expectedSize) {
                throw MeetingVideoException.sizeMismatch(SIZE_MISMATCH_MESSAGE)
            }
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
        } catch (ex: MeetingVideoException) {
            throw ex
        } catch (ex: IOException) {
            throw if (isNoSpaceLeft(ex)) {
                MeetingVideoException.storageFull(STORAGE_FULL_MESSAGE)
            } else {
                MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
            }
        }
        return written
    }

    /**
     * ENOSPC 判定：JDK 在磁盘写满时抛 `FileSystemException(reason="No space left on device")`，
     * 不同文件系统也可能只给 message；沿因果链检查固定标记。
     */
    private fun isNoSpaceLeft(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            val reason = (current as? java.nio.file.FileSystemException)?.reason
            val markers = listOfNotNull(reason, current.message)
            if (markers.any { marker ->
                    marker.contains("no space left", ignoreCase = true) || marker.contains("ENOSPC")
                }
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    // ------------------------------------------------------------------
    // 元数据读写
    // ------------------------------------------------------------------

    private fun insertParams(
        id: String,
        expertKey: String,
        originalFilename: String,
        displayFilename: String,
        byteLength: Long,
        actor: String,
        now: LocalDateTime
    ): MapSqlParameterSource =
        MapSqlParameterSource("id", id)
            .addValue("expertKey", expertKey)
            .addValue("originalFilename", originalFilename)
            .addValue("displayFilename", displayFilename)
            .addValue("byteLength", byteLength)
            .addValue("uploadedBy", actor)
            .addValue("uploadedAt", now)
            .addValue("updatedBy", actor)
            .addValue("updatedAt", now)

    private fun readRow(id: String, expertKey: String): MeetingVideoRow? =
        try {
            jdbcTemplate.query(
                SELECT_ONE_SQL,
                MapSqlParameterSource("id", id).addValue("expertKey", expertKey),
                ROW_MAPPER
            ).firstOrNull()
        } catch (ex: DataAccessException) {
            throw MeetingVideoException.storeFailed(STORE_FAILED_MESSAGE, ex)
        }

    private fun view(row: MeetingVideoRow, contextPath: String): MeetingVideoView =
        MeetingVideoView(
            id = row.id,
            expertId = row.expertKey,
            originalFilename = row.originalFilename,
            displayFilename = row.displayFilename,
            extension = extensionOf(row.originalFilename),
            byteLength = row.byteLength,
            uploadedBy = row.uploadedBy,
            uploadedAt = formatTime(row.uploadedAt),
            updatedBy = row.updatedBy,
            updatedAt = formatTime(row.updatedAt),
            version = row.version,
            previewUrl = videoUrl(contextPath, row.id, row.expertKey, PREVIEW_SUFFIX),
            downloadUrl = videoUrl(contextPath, row.id, row.expertKey, DOWNLOAD_SUFFIX)
        )

    /** contextPath 下的同源路径；expertId 一次编码，客户端不二次 URLDecoder。 */
    private fun videoUrl(contextPath: String, id: String, expertKey: String, suffix: String): String =
        "$contextPath$API_PATH/$id/$suffix?expertId=" +
            UriUtils.encodeQueryParam(expertKey, StandardCharsets.UTF_8)

    private companion object {
        const val VIDEO_DIR = "meeting-videos"
        const val TEMP_FILE_PREFIX = ".tmp-"
        const val API_PATH = "/api/experts/meeting-videos"
        const val PREVIEW_SUFFIX = "preview"
        const val DOWNLOAD_SUFFIX = "download"
        const val INITIAL_VERSION = 1L
        const val MAX_EXPERT_KEY_CODE_POINTS = 64

        const val EMPTY_VIDEO_MESSAGE = "视频文件为空"
        const val SIZE_MISMATCH_MESSAGE = "声明的文件大小与实际字节数不一致"
        const val TOO_LARGE_MESSAGE = "单个视频不能超过 1073741824 字节"
        const val NAME_CONFLICT_MESSAGE = "同名视频已存在，请修改名称后重试"
        const val VERSION_CONFLICT_MESSAGE = "视频已被更新，请重新加载后再重试"
        const val VIDEO_NOT_FOUND_MESSAGE = "视频不存在或不属于该专家"
        const val VIDEO_FILE_MISSING_MESSAGE = "视频文件不存在"
        const val EXPERT_NOT_FOUND_MESSAGE = "专家不存在"
        const val EXPERT_INVALID_MESSAGE = "专家标识无效"
        const val EXPERT_LOOKUP_MESSAGE = "专家身份查询服务不可用，请稍后重试"
        const val UPLOAD_BUSY_MESSAGE = "当前上传任务过多，请稍后重试"
        const val UNSUPPORTED_TYPE_MESSAGE = "只支持 mp4、mov、webm 视频"
        const val ORIGINAL_NAME_INVALID_MESSAGE = "原始文件名无效"
        const val ORIGINAL_NAME_TOO_LONG_MESSAGE = "原始文件名不能超过 255 个字符"
        const val EMPTY_STEM_MESSAGE = "视频名称主体不能为空"
        const val CONTROL_CHAR_MESSAGE = "视频名称不能包含控制字符"
        const val FORBIDDEN_CHAR_MESSAGE = "视频名称不能包含 / \\ : * ? \" < > | 字符"
        const val TRAILING_DOT_MESSAGE = "视频名称主体不能以句点结尾"
        const val DISPLAY_NAME_TOO_LONG_MESSAGE = "视频名称不能超过 100 个字符"
        const val PAGE_INVALID_MESSAGE = "分页参数无效：page 从 0 开始，size 在 1..50 之间"
        const val ACTOR_MISSING_MESSAGE = "登录身份缺失"
        const val ACTOR_TOO_LONG_MESSAGE = "登录用户名不能超过 64 个字符"
        const val STORAGE_FULL_MESSAGE = "存储空间不足，无法保存视频"
        const val STORE_FAILED_MESSAGE = "视频保存失败，请稍后重试"
        const val INTEGRITY_FAILED_MESSAGE = "视频文件不可读取，请重新上传"

        /** 名称主体禁止字符：路径分隔符与 Windows 保留字符。 */
        val FORBIDDEN_STEM_CHARS = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")
        val BEIJING_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        const val COLUMNS =
            "id, expert_key, original_filename, display_filename, byte_length, " +
                "uploaded_by, uploaded_at, updated_by, updated_at, version"

        val INSERT_SQL = """
            INSERT INTO expert_meeting_video
                (id, expert_key, original_filename, display_filename, byte_length,
                 uploaded_by, uploaded_at, updated_by, updated_at, version)
            VALUES
                (:id, :expertKey, :originalFilename, :displayFilename, :byteLength,
                 :uploadedBy, :uploadedAt, :updatedBy, :updatedAt, 1)
        """.trimIndent()

        val SELECT_ONE_SQL =
            "SELECT $COLUMNS FROM expert_meeting_video WHERE id = :id AND expert_key = :expertKey"

        /** 同名预检：同一 expert_key 内精确（区分大小写）匹配 NFC 后的 display_filename，与唯一索引同语义。 */
        val SELECT_NAME_SQL =
            "SELECT $COLUMNS FROM expert_meeting_video " +
                "WHERE expert_key = :expertKey AND display_filename = :displayFilename"

        val SELECT_PAGE_SQL =
            "SELECT $COLUMNS FROM expert_meeting_video WHERE expert_key = :expertKey " +
                "ORDER BY uploaded_at DESC, id DESC LIMIT :limit OFFSET :offset"

        const val COUNT_SQL = "SELECT COUNT(*) FROM expert_meeting_video WHERE expert_key = :expertKey"

        val RENAME_SQL = """
            UPDATE expert_meeting_video
               SET display_filename = :displayFilename,
                   version = version + 1,
                   updated_by = :updatedBy,
                   updated_at = :updatedAt
             WHERE id = :id AND expert_key = :expertKey AND version = :expectedVersion
        """.trimIndent()

        val ROW_MAPPER = RowMapper { rs, _ ->
            MeetingVideoRow(
                id = rs.getString("id"),
                expertKey = rs.getString("expert_key"),
                originalFilename = rs.getString("original_filename"),
                displayFilename = rs.getString("display_filename"),
                byteLength = rs.getLong("byte_length"),
                uploadedBy = rs.getString("uploaded_by"),
                uploadedAt = rs.getTimestamp("uploaded_at").toLocalDateTime(),
                updatedBy = rs.getString("updated_by"),
                updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
                version = rs.getLong("version")
            )
        }

        fun formatTime(value: LocalDateTime): String =
            value.atOffset(BEIJING_OFFSET).format(TIME_FORMAT)
    }
}

/** 元数据行（只在本 service 内部使用；extension/contentType 由 original_filename 派生）。 */
private data class MeetingVideoRow(
    val id: String,
    val expertKey: String,
    val originalFilename: String,
    val displayFilename: String,
    val byteLength: Long,
    val uploadedBy: String,
    val uploadedAt: LocalDateTime,
    val updatedBy: String,
    val updatedAt: LocalDateTime,
    val version: Long
)
