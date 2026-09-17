package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.http.HttpStatus
import java.security.MessageDigest
import java.util.UUID

// ----------------------------------------------------------------------------
// 04 固定容量边界与协议固定值（计划的明确设计值，不是从现有代码推断的线上限制）。
// 下游 05/06/07 一律引用这里的常量，不各自复制字面量。
// ----------------------------------------------------------------------------

/** 单个通用附件原件字节上限（含 0 字节合法；超过立即 413 并清理本次临时文件）。 */
const val MAX_FILE_BYTES = 10L * 1024 * 1024

/** 一封人工邮件通用附件总字节上限；发送前按服务端元数据校验。 */
const val MAX_TOTAL_BYTES = 20L * 1024 * 1024

/** 一封人工邮件通用附件个数上限；发送前按服务端元数据校验。 */
const val MAX_FILES = 10

/** 快照协议版本（I-5）：只认这一个值。 */
const val OUTBOUND_ATTACHMENT_SCHEMA_VERSION = 1

/** file_name 列宽（字符）；UTF-8 文件名按字符截断，保留扩展名。 */
internal const val MAX_FILE_NAME_CHARS = 255

/** 保留扩展名时认定的「扩展名」最大长度，避免把超长尾巴整段当扩展名。 */
internal const val MAX_EXTENSION_CHARS = 32

/** content_type 列宽（字符）。 */
internal const val MAX_CONTENT_TYPE_CHARS = 255

/** created_by 列宽（字符）；超长身份必须报错，绝不截断成另一个用户。 */
internal const val MAX_CREATED_BY_CHARS = 100

/** 无扩展名/MIME 白名单：无法判定或非法声明时的固定回退值。 */
internal const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

/** 文件名清洗后为空时的固定回退值。 */
internal const val DEFAULT_FILE_NAME = "attachment"

/**
 * 通用附件业务异常（04 T3）：状态固定在 400/404/409/413 四档，由
 * `GlobalExceptionHandler` 统一映射成 `ApiErrorResponse`（不含磁盘路径与堆栈）。
 *
 * [message] 只写面向操作员的固定文案（可带附件 id），**绝不写磁盘路径**。
 * 06 的「该请求已发送，附件与原请求不同」也用 [conflict]。
 */
class OutboundAttachmentException private constructor(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message) {

    companion object {
        /** 400：请求本身不合法（缺 file 字段、id 重复、数量超限、非法 id）。 */
        fun badRequest(message: String): OutboundAttachmentException =
            OutboundAttachmentException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)

        /** 404：不存在、不属于该专家、或非本人上传（三者不区分，避免泄露存在性）。 */
        fun notFound(message: String): OutboundAttachmentException =
            OutboundAttachmentException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)

        /** 409：元数据在但原件缺失以外的损坏（尺寸/hash 不符、越界路径、快照损坏）。 */
        fun conflict(message: String): OutboundAttachmentException =
            OutboundAttachmentException(HttpStatus.CONFLICT, "CONFLICT", message)

        /** 413：单个附件或总量超出固定容量边界。 */
        fun payloadTooLarge(message: String): OutboundAttachmentException =
            OutboundAttachmentException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message)
    }
}

/**
 * 通用附件规范化快照（I-5，schemaVersion=1）。
 *
 * 只有展示与校验所需字段：绝不含绝对路径、字节、上传用户名。字段与顺序就是
 * `mail_record` 存档 JSON 数组里的元素形态（05 写入，06 读取比较）。
 */
data class OutboundAttachmentSnapshot(
    val schemaVersion: Int,
    val id: String,
    val filename: String,
    val contentType: String,
    val byteLength: Long,
    val sha256: String
)

/**
 * 发送载荷（I-5）：已验证快照 + 已从原件读取并核过尺寸/hash 的字节。
 *
 * 只在内存中传递到 SMTP 组装点；字节不写正文、日志或审计 JSON。
 * [bytes] 由调用方只读使用（不可变更约定）。
 */
class OutboundMailFile(
    val snapshot: OutboundAttachmentSnapshot,
    val bytes: ByteArray
)

/**
 * 一次发送解析结果（04 T2，供 05/06 在 claim 之前使用）：不可变文件列表 +
 * 与用户选择顺序逐项一致的有序快照列表。
 */
data class OutboundAttachmentFileSet(
    val files: List<OutboundMailFile>,
    val snapshots: List<OutboundAttachmentSnapshot>
)

/** 上传成功响应（201）：downloadUrl 是 context 相对的 /api/... 路径，不含服务器路径。 */
data class OutboundAttachmentUploadResponse(
    val id: String,
    val filename: String,
    val contentType: String,
    val byteLength: Long,
    val sha256: String,
    val downloadUrl: String
)

/**
 * 快照 JSON 数组编解码（I-5）。
 *
 * 固定契约：`mail_record` 里「没有通用附件」的唯一表示是 SQL NULL / 空串；非空才存
 * JSON 数组，空数组**不是**合法存档形态。严格解析：数量、文件名/类型长度与形态、
 * schemaVersion、id 规范 UUID、sha256 小写 hex、单项与总字节边界全部校验，未知字段
 * 一律拒绝。
 *
 * 两种读取语义，调用方按是否需要 fail-closed 选择：
 *  - [parseOrNull]：展示/可选路径用；NULL、空白与**损坏**都归为 null（不抛）。
 *  - [parseOrThrow]：发送门与审计比较用；只有真正未存档（NULL/空白）才是 null，
 *    损坏一律 409，绝不静默当成「没有附件」继续发信（I-4）。
 */
object OutboundAttachmentSnapshotCodec {

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    /** 序列化（05 写 mail_record）；不合法立即抛 [IllegalArgumentException]，不产出可疑存档。 */
    fun serialize(snapshots: List<OutboundAttachmentSnapshot>): String {
        validateOrNull(snapshots)?.let { reason ->
            throw IllegalArgumentException("通用附件快照不合法：$reason")
        }
        return try {
            mapper.writeValueAsString(snapshots)
        } catch (ex: Exception) {
            throw IllegalArgumentException("通用附件快照无法序列化", ex)
        }
    }

    /** 展示/可选路径：NULL、空白与损坏一律 null。 */
    fun parseOrNull(json: String?): List<OutboundAttachmentSnapshot>? =
        try {
            parseOrThrow(json)
        } catch (ex: OutboundAttachmentException) {
            null
        }

    /** 发送门/审计比较：NULL、空白 -> null（真正未存档）；其余任何损坏 -> 409。 */
    fun parseOrThrow(json: String?): List<OutboundAttachmentSnapshot>? {
        if (json.isNullOrBlank()) {
            return null
        }
        val snapshots = try {
            mapper.readValue(json, Array<OutboundAttachmentSnapshot>::class.java).toList()
        } catch (ex: Exception) {
            throw OutboundAttachmentException.conflict("通用附件快照已损坏")
        }
        validateOrNull(snapshots)?.let { reason ->
            throw OutboundAttachmentException.conflict("通用附件快照已损坏：$reason")
        }
        return snapshots
    }

    /** 单条快照校验（原件读取/发送前由服务端按元数据重算后调用）；不通过即 409。 */
    fun validateSnapshot(snapshot: OutboundAttachmentSnapshot) {
        validateOrNull(listOf(snapshot))?.let { reason ->
            throw OutboundAttachmentException.conflict("附件元数据已损坏：$reason")
        }
    }

    private fun validateOrNull(snapshots: List<OutboundAttachmentSnapshot>): String? {
        if (snapshots.isEmpty()) {
            return "空数组不是合法存档形态（无通用附件的唯一形态是 NULL）"
        }
        if (snapshots.size > MAX_FILES) {
            return "数量超过 $MAX_FILES"
        }
        if (snapshots.map { it.id }.distinct().size != snapshots.size) {
            return "id 重复"
        }
        var totalBytes = 0L
        snapshots.forEach { snapshot ->
            if (snapshot.schemaVersion != OUTBOUND_ATTACHMENT_SCHEMA_VERSION) {
                return "不支持的 schemaVersion=${snapshot.schemaVersion}"
            }
            if (!isCanonicalUuid(snapshot.id)) {
                return "非法 id"
            }
            if (!isValidOutboundFileName(snapshot.filename)) {
                return "非法 filename"
            }
            if (!isValidOutboundContentType(snapshot.contentType)) {
                return "非法 contentType"
            }
            if (snapshot.byteLength < 0 || snapshot.byteLength > MAX_FILE_BYTES) {
                return "byteLength 越界"
            }
            if (!SHA256_HEX.matches(snapshot.sha256)) {
                return "非法 sha256"
            }
            totalBytes += snapshot.byteLength
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            return "总字节超过 $MAX_TOTAL_BYTES"
        }
        return null
    }
}

// ----------------------------------------------------------------------------
// 规范化与校验（上传写入前与存档读取时共用同一套谓词；I-3/I-5）
// ----------------------------------------------------------------------------

/**
 * UTF-8 文件名清洗：取最后一个路径段（`/` 与 `\` 都算分隔符）→ 去控制字符 → trim；
 * 空则 [DEFAULT_FILE_NAME]；超长按字符截断且尽量保留扩展名。保留中文与任意扩展名。
 */
internal fun normalizeOutboundFileName(raw: String?): String {
    val lastSegment = raw.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val stripped = lastSegment.filter { !it.isISOControl() }.trim()
    return capFileName(stripped.ifBlank { DEFAULT_FILE_NAME })
}

/** 存档/展示名合法性：非空白、无控制字符、不含路径分隔符、长度 ≤255 字符。 */
internal fun isValidOutboundFileName(name: String): Boolean =
    name.isNotBlank() &&
        name.length <= MAX_FILE_NAME_CHARS &&
        name.none { it.isISOControl() || it == '/' || it == '\\' }

/**
 * MIME 规范化：只接受不带参数的 `type/subtype`（因此天然不含 CRLF/控制字符），
 * 其余（含空、超长、带参数）一律回退 [DEFAULT_CONTENT_TYPE]（I-3，不做白名单）。
 */
internal fun normalizeOutboundContentType(raw: String?): String {
    val value = raw?.trim().orEmpty()
    return if (isValidOutboundContentType(value)) value else DEFAULT_CONTENT_TYPE
}

internal fun isValidOutboundContentType(value: String): Boolean =
    value.isNotEmpty() && value.length <= MAX_CONTENT_TYPE_CHARS && CONTENT_TYPE_REGEX.matches(value)

/** 原件字节的 SHA-256（64 位小写 hex）。 */
internal fun outboundSha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun isCanonicalUuid(value: String): Boolean =
    try {
        UUID.fromString(value).toString() == value
    } catch (ex: IllegalArgumentException) {
        false
    }

private val CONTENT_TYPE_REGEX = Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$")

private fun capFileName(name: String): String {
    if (name.length <= MAX_FILE_NAME_CHARS) {
        return name
    }
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0 && name.length - dot <= MAX_EXTENSION_CHARS) name.substring(dot) else ""
    val head = name.substring(0, MAX_FILE_NAME_CHARS - extension.length)
    return trimSplitSurrogate(head) + extension
}

/** 截断可能落在代理对中间：丢掉尾部孤立的高位代理，避免写入半个 UTF-8 字符。 */
private fun trimSplitSurrogate(value: String): String =
    if (value.isNotEmpty() && value.last().isHighSurrogate()) value.dropLast(1) else value
