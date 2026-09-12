package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * 受控 Google Drive 文件分享链接的识别与登记描述构造（I-1/I-2/I-5/I-6/I-7）。
 *
 * 只接受 `https` + 精确主机 `drive.google.com` + 精确路径 `/file/d/{fileId}/view`；
 * query 只忽略，绝不参与任何下载地址拼装。识别阶段只产出内存描述（content=null），
 * 不触网、不建目录、不写文件。
 *
 * partPath 固定为 `gdrive:{fileId}`（[PART_PATH_PREFIX]）：它是传输 worker 分派来源
 * 语法与 V119 来源唯一身份 `(account_code,folder,uid_validity,imap_uid,part_path)`
 * 的组成部分，与点分数字 MIME 路径语法互不重叠。
 */
internal object GoogleDriveMaterialSource {

    /** 外链来源的 partPath 前缀（248 = `part_path VARCHAR(255)` - 本前缀 7 字符）。 */
    const val PART_PATH_PREFIX = "gdrive:"

    const val MAX_FILE_ID_LENGTH = 248

    /** 文件名是受限提示，不是路径（I-7）。 */
    const val MAX_FILE_NAME_LENGTH = 255

    /** 外链来源的 disposition 标记（只作可判别标记，不参与任何获取逻辑）。 */
    const val EXTERNAL_LINK_DISPOSITION = "external-link"

    private const val DRIVE_HOST = "drive.google.com"
    private const val DEFAULT_HTTPS_PORT = 443

    private val DRIVE_FILE_PATH = Regex("^/file/d/([A-Za-z0-9_-]+)/view$")
    private val FILE_ID = Regex("^[A-Za-z0-9_-]+$")

    private val SCRIPT_OR_STYLE = Regex("(?is)<\\s*(script|style)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>")
    private val ANCHOR = Regex(
        "(?is)<a\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>\"']+))[^>]*?>(.*?)<\\s*/\\s*a\\s*>"
    )
    private val BLOCK_BOUNDARY = Regex(
        "(?i)<\\s*/?\\s*(?:br|p|div|tr|li|h[1-6]|blockquote|table|ul|ol|hr)\\b[^>]*>"
    )
    private val ANY_TAG = Regex("(?is)<[^>]*>")
    private val INLINE_SPACE = Regex("[ \\t\\u00a0]+")

    private val BARE_URL = Regex("https://[^\\s<>\"']+")
    private val URL_TAIL = Regex("[).,;:!?\\]}>»”’]+$")
    private val PREFIX_TAIL_WRAPPERS = Regex("[<({\\[\"'«“‘:]+$")
    private val WHITESPACE = Regex("\\s+")
    private val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")
    private val EXTENSION = Regex("^[A-Za-z0-9]{1,10}$")
    private val NAME_WRAPPERS = "<>\"'()[]{}«»“”‘’".toSet()
    private val ENTITY = Regex("&(#x?[0-9A-Fa-f]{1,8}|[A-Za-z][A-Za-z0-9]{1,10});")

    /**
     * 锚文本边界标记（只存在于内存扫描视图，绝不进入正文/落库/URL）：让「合法锚文本」
     * 与「URL 前紧邻 token」两种文件名提示可判别，而不必猜测前缀从哪里开始。
     */
    private const val ANCHOR_START = '\u0001'
    private const val ANCHOR_END = '\u0002'

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ensp" to " ",
        "emsp" to " ",
        "ndash" to "\u2013",
        "mdash" to "\u2014",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201c",
        "rdquo" to "\u201d"
    )

    // ------------------------------------------------------------------
    // 严格 URL 校验（I-1）
    // ------------------------------------------------------------------

    /**
     * 仅接受 `https://drive.google.com/file/d/{fileId}/view`（query 忽略）：返回 fileId；
     * 其他 scheme/主机/端口/用户信息/路径形态一律返回 null。结构校验交给 [URI]，
     * 不用宽松的包含判断（防 `drive.google.com.evil`、`open?id=`、文件夹链接）。
     */
    fun fileIdFromUrl(raw: String): String? {
        val candidate = raw.trim()
        if (!candidate.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val uri = try {
            URI(candidate)
        } catch (e: URISyntaxException) {
            return null
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (uri.userInfo != null) return null
        if (!DRIVE_HOST.equals(uri.host, ignoreCase = true)) return null
        if (uri.port != -1 && uri.port != DEFAULT_HTTPS_PORT) return null
        val match = DRIVE_FILE_PATH.matchEntire(uri.path ?: return null) ?: return null
        val fileId = match.groupValues[1]
        if (fileId.length > MAX_FILE_ID_LENGTH) return null
        return fileId
    }

    /** 从已登记的 partPath 取回 fileId；非 `gdrive:` 语法或非法 id 返回 null。 */
    fun fileIdFromPartPath(partPath: String): String? {
        if (!partPath.startsWith(PART_PATH_PREFIX)) return null
        val fileId = partPath.substring(PART_PATH_PREFIX.length)
        return fileId.takeIf { isValidFileId(it) }
    }

    /** fileId 只允许 URL-safe 字符且长度受 `part_path` 列宽约束（I-1）。 */
    fun isValidFileId(fileId: String): Boolean =
        fileId.isNotEmpty() && fileId.length <= MAX_FILE_ID_LENGTH && FILE_ID.matches(fileId)

    fun partPathFor(fileId: String): String = PART_PATH_PREFIX + fileId

    // ------------------------------------------------------------------
    // 当前回复范围内的链接提取（I-6）
    // ------------------------------------------------------------------

    /** text/plain：直接清洗当前回复范围后提取。 */
    fun fromPlainText(
        text: String?,
        context: ImapMailReceiveService.MailSourceContext,
        cleaner: MailBodyCleaner
    ): List<ReceivedMailAttachment> = buildAttachments(cleaner.clean(text), context)

    /**
     * text/html：先用同一份原始有界文本生成「锚文本 <href>」扫描视图（保留 href 与锚文本、
     * `<br>/<p>` 行边界，去 script/style/其他标签并解实体），再清洗当前回复范围后提取。
     * 正文 [ReceivedMail.body] 仍走既有 stripHtml，绝不因材料识别而改写。
     */
    fun fromHtml(
        html: String?,
        context: ImapMailReceiveService.MailSourceContext,
        cleaner: MailBodyCleaner
    ): List<ReceivedMailAttachment> = buildAttachments(cleaner.clean(toHtmlScanText(html.orEmpty())), context)

    /** HTML 单次有界文本 -> 纯文本扫描视图（不访问 part 流，不产生第二次读取）。 */
    fun toHtmlScanText(html: String): String {
        // 顺序要点：锚点先展开成「锚文本 + 裸 URL」再删标签——若先把 href 包成 <url>，
        // 后续的标签清理会连同它一起删掉。
        val withAnchors = ANCHOR.replace(SCRIPT_OR_STYLE.replace(html, " ")) { match ->
            val href = match.groupValues[1]
                .ifEmpty { match.groupValues[2] }
                .ifEmpty { match.groupValues[3] }
            val anchorText = unescapeEntities(ANY_TAG.replace(match.groupValues[4], "")).trim()
            val decodedHref = unescapeEntities(href).trim()
            when {
                decodedHref.isBlank() -> anchorText
                // 锚文本两侧加内存标记：文件名提示优先取锚文本（I-7），且不把锚文本前
                // 的正文误当文件名。
                else -> "$ANCHOR_START$anchorText$ANCHOR_END $decodedHref"
            }
        }
        val withBreaks = BLOCK_BOUNDARY.replace(withAnchors, "\n")
        return INLINE_SPACE.replace(unescapeEntities(ANY_TAG.replace(withBreaks, " ")), " ")
    }

    /** 保序去重（同一消息同一 fileId 至多一份，I-5）。 */
    fun dedupe(materials: List<ReceivedMailAttachment>): List<ReceivedMailAttachment> {
        if (materials.size < 2) return materials
        val seen = HashSet<String>()
        return materials.filter { seen.add(it.source?.partPath.orEmpty()) }
    }

    private fun buildAttachments(
        currentReply: String,
        context: ImapMailReceiveService.MailSourceContext
    ): List<ReceivedMailAttachment> {
        if (currentReply.isBlank()) return emptyList()
        val seen = HashSet<String>()
        val materials = ArrayList<ReceivedMailAttachment>()
        for (match in BARE_URL.findAll(currentReply)) {
            val fileId = fileIdFromUrl(match.value.replace(URL_TAIL, "")) ?: continue
            if (!seen.add(fileId)) continue
            materials.add(
                ReceivedMailAttachment(
                    fileName = fileNameHint(currentReply, match.range.first, fileId),
                    contentType = null,
                    content = null,
                    source = ImapAttachmentSource(
                        accountCode = context.accountCode,
                        folder = context.folder,
                        uidValidity = context.uidValidity,
                        uid = context.uid,
                        partPath = partPathFor(fileId),
                        messageId = context.messageId,
                        encodedSize = null,
                        disposition = EXTERNAL_LINK_DISPOSITION
                    )
                )
            )
        }
        return materials
    }

    /** URL 之前同一行的文本（去掉紧贴 URL 的包围符），用作文件名提示。 */
    private fun prefixOnLine(text: String, urlStart: Int): String {
        val lineStart = text.lastIndexOf('\n', (urlStart - 1).coerceAtLeast(0))
        val from = if (lineStart < 0) 0 else lineStart + 1
        return text.substring(from, urlStart)
            .trimEnd()
            .replace(PREFIX_TAIL_WRAPPERS, "")
            .trimEnd()
    }

    /**
     * 文件名提示（I-7）：优先合法锚文本，其次 URL 前紧邻 token，都不合法时用
     * `GoogleDrive-{fileId}`。要求 1-10 位字母数字扩展名，去包围符/控制符与路径分隔符。
     */
    private fun fileNameHint(text: String, urlStart: Int, fileId: String): String {
        val prefix = prefixOnLine(text, urlStart)
        anchorTextIn(prefix)?.let { anchor ->
            sanitizeFileName(anchor)?.let { return it }
        }
        prefix.split(WHITESPACE).lastOrNull { it.isNotBlank() }?.let { adjacentToken ->
            sanitizeFileName(adjacentToken)?.let { return it }
        }
        return "GoogleDrive-$fileId"
    }

    /** 取扫描视图里 URL 前紧贴的锚文本（两侧标记之间），非锚文本链接返回 null。 */
    private fun anchorTextIn(prefix: String): String? {
        val end = prefix.lastIndexOf(ANCHOR_END)
        if (end < 0) return null
        val start = prefix.lastIndexOf(ANCHOR_START, end)
        if (start < 0 || start >= end) return null
        return prefix.substring(start + 1, end)
    }

    private fun sanitizeFileName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = CONTROL_CHARS.replace(raw, "")
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .trim { it in NAME_WRAPPERS }
            .trim()
        val dot = cleaned.lastIndexOf('.')
        if (dot <= 0 || dot == cleaned.length - 1) return null
        val stem = cleaned.substring(0, dot)
        val extension = cleaned.substring(dot + 1)
        if (!EXTENSION.matches(extension)) return null
        val maxStem = MAX_FILE_NAME_LENGTH - extension.length - 1
        val clippedStem = if (stem.length > maxStem) stem.take(maxStem) else stem
        if (clippedStem.isBlank()) return null
        return "$clippedStem.$extension"
    }

    private fun unescapeEntities(input: String): String = ENTITY.replace(input) { match ->
        val body = match.groupValues[1]
        val decoded = when {
            body.startsWith("#x", ignoreCase = true) -> codePoint(body.substring(2), 16)
            body.startsWith("#") -> codePoint(body.substring(1), 10)
            else -> NAMED_ENTITIES[body.lowercase()]
        }
        decoded ?: match.value
    }

    private fun codePoint(digits: String, radix: Int): String? {
        val value = digits.toIntOrNull(radix) ?: return null
        if (value <= 0 || value > 0x10FFFF) return null
        if (value in 0xD800..0xDFFF) return null
        return String(Character.toChars(value))
    }
}

/**
 * 受控 Drive 外链的固定域 HTTP 取件（I-1/I-8/I-9）。
 *
 * - 只从已校验的 fileId 构造固定下载端点，绝不使用邮件正文里的原始 URL，也绝不跟随
 *   任何重定向：分享页是 `text/html`，不能当附件保存。
 * - 必须 HTTP 200 + 非 `text/html` + `Content-Disposition: attachment`；声明长度超限
 *   立即 [AttachmentFetchException.LimitExceeded]，未知长度仍由流式 `transferMaxBytes`
 *   限制。
 * - connect/read 超时取 [MailAttachmentStorageProperties]；总时限、租约续租、watchdog、
 *   `.part` 与原子转正全部复用 [AttachmentTransferWorker] 既有机制；[forceClose] 真断开
 *   连接，让阻塞读立即以 IO 异常结束。
 * - 失败只抛既有 [AttachmentFetchException] 分类，消息固定脱敏（不含 URL、响应正文、
 *   Cookie、代理或邮箱凭据）。
 *
 * 生产构造只接受 [MailAttachmentStorageProperties]；package-internal 测试构造只替换
 * endpoint factory（IT 用 loopback fixture），业务代码无法传入任意下载 URL。
 */
@Component
class GoogleDriveAttachmentContentFetcher internal constructor(
    private val properties: MailAttachmentStorageProperties,
    private val downloadUrlFor: (String) -> URL
) {
    @Autowired
    constructor(properties: MailAttachmentStorageProperties) :
        this(properties, PRODUCTION_DOWNLOAD_ENDPOINT)

    private val log = LoggerFactory.getLogger(GoogleDriveAttachmentContentFetcher::class.java)

    /** 打开固定域下载连接并校验响应；未通过校验抛脱敏的 [AttachmentFetchException]。 */
    fun resolve(fileId: String): ResolvedAttachmentPart {
        if (!GoogleDriveMaterialSource.isValidFileId(fileId)) {
            throw AttachmentFetchException.SourceUnavailable(
                "INVALID_FILE_ID",
                "registered drive source does not carry a valid file id"
            )
        }
        val connection = openConnection(fileId)
        try {
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw AttachmentFetchException.TransferFailed(
                    "TIMEOUT",
                    "file download endpoint did not respond"
                )
            }
            if (status != HTTP_OK) {
                throw classifyStatus(status)
            }
            verifyResponseHeaders(connection)
            val input = try {
                connection.inputStream
            } catch (e: IOException) {
                throw AttachmentFetchException.TransferFailed(
                    "TIMEOUT",
                    "file download stream could not be opened"
                )
            }
            return DriveResolvedPart(
                connection,
                input,
                connection.contentLengthLong,
                bufferBytes(),
                log
            )
        } catch (e: AttachmentFetchException) {
            runCatching { connection.disconnect() }
            throw e
        } catch (e: Exception) {
            runCatching { connection.disconnect() }
            log.info("drive download could not be prepared", e)
            throw AttachmentFetchException.TransferFailed(
                "TIMEOUT",
                "file download endpoint failed before download"
            )
        }
    }

    private fun openConnection(fileId: String): HttpURLConnection {
        val connection = try {
            downloadUrlFor(fileId).openConnection()
        } catch (e: IOException) {
            log.info("drive download connection could not be created", e)
            throw AttachmentFetchException.TransferFailed(
                "TIMEOUT",
                "cannot connect to the file download endpoint"
            )
        } catch (e: ClassCastException) {
            throw AttachmentFetchException.SourceUnavailable(
                "UNSUPPORTED_SOURCE",
                "configured drive endpoint is not an HTTP endpoint"
            )
        }
        return (connection as? HttpURLConnection)?.apply {
            instanceFollowRedirects = false
            connectTimeout = properties.transferConnectTimeoutMs
            readTimeout = properties.transferReadTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", "*/*")
        } ?: throw AttachmentFetchException.SourceUnavailable(
            "UNSUPPORTED_SOURCE",
            "configured drive endpoint is not an HTTP endpoint"
        )
    }

    /** 200 之外的状态按可重试性分派（I-9）：只有 408/429/5xx 是可重试的 FAILED。 */
    private fun classifyStatus(status: Int): AttachmentFetchException = when {
        status in 300..399 -> AttachmentFetchException.SourceUnavailable(
            "REDIRECT",
            "download endpoint redirected away from the shared file"
        )
        status == HTTP_NOT_FOUND -> AttachmentFetchException.SourceUnavailable(
            "HTTP_404",
            "shared file is no longer available"
        )
        status == HTTP_FORBIDDEN -> AttachmentFetchException.SourceUnavailable(
            "HTTP_403",
            "shared file is not accessible with link sharing"
        )
        status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS ->
            AttachmentFetchException.TransferFailed(
                "HTTP_$status",
                "download endpoint is temporarily rate limited or unavailable"
            )
        status in 400..499 -> AttachmentFetchException.SourceUnavailable(
            "HTTP_4XX",
            "download endpoint refused the shared file request"
        )
        status in 500..599 -> AttachmentFetchException.TransferFailed(
            "HTTP_5XX",
            "download endpoint failed temporarily"
        )
        else -> AttachmentFetchException.TransferFailed(
            "HTTP_$status",
            "download endpoint returned an unexpected status"
        )
    }

    private fun verifyResponseHeaders(connection: HttpURLConnection) {
        val contentType = connection.getHeaderField("Content-Type")
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
        if (contentType.isNullOrBlank() || contentType == "text/html") {
            throw AttachmentFetchException.SourceUnavailable(
                "NOT_AN_ATTACHMENT",
                "download endpoint did not return a file"
            )
        }
        val disposition = connection.getHeaderField("Content-Disposition")
        if (disposition == null || !disposition.lowercase().contains("attachment")) {
            throw AttachmentFetchException.SourceUnavailable(
                "NOT_AN_ATTACHMENT",
                "download endpoint did not return a file attachment"
            )
        }
        if (connection.contentLengthLong > properties.transferMaxBytes) {
            throw AttachmentFetchException.LimitExceeded(
                "file exceeds the configured per-file byte limit before download"
            )
        }
    }

    private fun bufferBytes(): Int = properties.transferBufferBytes.coerceAtLeast(MIN_BUFFER_BYTES)

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MIN_BUFFER_BYTES = 1024

        /** 固定下载端点：分享页 `drive.google.com/file/d/...` 是 text/html，不能当附件。 */
        private val PRODUCTION_DOWNLOAD_ENDPOINT: (String) -> URL = { fileId ->
            URL("https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t")
        }
    }
}

/**
 * 已打开的 Drive 下载连接。由下载线程持有并在 finally 优雅关闭；watchdog 在总时限/租约
 * 丢失时调用 [forceClose]（关流 + disconnect），让阻塞的流式读立即以 IO 异常结束。
 */
private class DriveResolvedPart(
    private val connection: HttpURLConnection,
    private val input: InputStream,
    /** 声明的 Content-Length；-1 = 未知（chunked / 无长度），此时只靠流式上限约束。 */
    private val declaredLength: Long,
    private val bufferBytes: Int,
    private val log: org.slf4j.Logger
) : ResolvedAttachmentPart {

    override fun streamContent(
        sink: OutputStream,
        maxBytes: Long,
        abortReason: () -> String?
    ): Long {
        val buffer = ByteArray(bufferBytes)
        var total = 0L
        while (true) {
            val reason = abortReason()
            if (reason != null) {
                throw AttachmentFetchException.Aborted(reason, "transfer aborted by caller")
            }
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                throw mapReadFailure(e, abortReason())
            }
            if (read < 0) break
            if (maxBytes - total < read) {
                throw AttachmentFetchException.LimitExceeded(
                    "download exceeds the configured per-file byte limit"
                )
            }
            sink.write(buffer, 0, read)
            total += read
        }
        // 半截下载绝不能当成功：声明长度已知时，实际字节必须完全吻合。
        if (declaredLength >= 0 && total != declaredLength) {
            throw AttachmentFetchException.TransferFailed(
                "INCOMPLETE_DOWNLOAD",
                "download ended before the declared content length"
            )
        }
        return total
    }

    private fun mapReadFailure(e: IOException, abort: String?): AttachmentFetchException {
        if (abort != null) {
            return AttachmentFetchException.Aborted(
                abort,
                "transfer aborted while reading the source part"
            )
        }
        log.info("drive content read interrupted before download completed", e)
        return AttachmentFetchException.TransferFailed(
            "TIMEOUT",
            "source connection interrupted while downloading"
        )
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { connection.disconnect() }
    }

    override fun forceClose() = close()
}
