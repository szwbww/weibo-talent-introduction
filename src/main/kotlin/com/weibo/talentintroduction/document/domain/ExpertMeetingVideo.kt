package com.weibo.talentintroduction.document.domain

import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.springframework.http.HttpStatus

/**
 * 04（I-2/I-3/I-4/I-5/I-9）：会议视频的固定业务边界与协议常量。
 *
 * 这些是本次需求与代码审计确定的固定值，不从请求覆盖：`expectedSize` 只能在
 * 1..[MAX_BYTES] 之间，缓冲/并发/余量也不接受客户端参数。测试与下游 05 一律引用这里，
 * 不各自复制字面量。
 */
object MeetingVideoLimits {
    /** 单文件上限：1024 MiB = 1,073,741,824 字节（常量必须保持 Long）。 */
    const val MAX_BYTES = 1073741824L

    /** 写文件前要求的剩余空间余量：`expectedSize + 64 MiB` 才允许开始写。 */
    const val STORAGE_HEADROOM_BYTES = 64L * 1024 * 1024

    /** 同一应用实例同时进行的视频上传上限（槽只在处理期持有，失败一律 429）。 */
    const val MAX_CONCURRENT_UPLOADS = 2

    /** 流式读写缓冲固定 64 KiB，不随文件大小增长。 */
    const val STREAM_BUFFER_BYTES = 64 * 1024

    /** `original_filename` 上限（Unicode code point）：超长报 400，绝不截断。 */
    const val MAX_ORIGINAL_NAME_CODE_POINTS = 255

    /** `display_filename` 完整长度（含扩展名）上限；主体超长时要求修改而不是截断。 */
    const val MAX_DISPLAY_NAME_CODE_POINTS = 100

    /** 审计列 `uploaded_by` / `updated_by` 的用户名上限（超长报错，绝不截断成另一个用户）。 */
    const val MAX_ACTOR_CODE_POINTS = 64

    /** 列表分页：page 从 0 开始，size 默认 20 且 1..50。 */
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50

    /** 允许的扩展名（小写、不带点）。扩展名由原始 basename 的最后后缀派生。 */
    val ALLOWED_EXTENSIONS = listOf("mp4", "mov", "webm")

    /** 扩展名 → 响应 Content-Type（I-7）；只接受 [ALLOWED_EXTENSIONS]。 */
    fun contentTypeOf(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> throw IllegalArgumentException("unsupported video extension: $extension")
    }
}

/**
 * 上传请求（controller 从 query/header 装配；service 在读请求体之前完成全部校验）。
 *
 * [expectedSize] 保持字符串：缺失/非数字/负数与「与实际字节数不一致」共用
 * `VIDEO_SIZE_MISMATCH`，解析与错误码收口在 service 一处。
 * [declaredLength] 是已知 `Content-Length`（chunked/未知时为 null），必须与 [expectedSize] 相等。
 */
data class MeetingVideoUploadRequest(
    val expertId: String?,
    val level: ExpertIndexLevel,
    val originalName: String?,
    val nameStem: String?,
    val expectedSize: String?,
    val declaredLength: Long?,
    val meetingRecordId: String? = null
)

/** 重命名请求（PATCH body）：`{nameStem, expectedVersion}`；两者都必填。 */
data class MeetingVideoRenameRequest(
    val nameStem: String?,
    val expectedVersion: Long?
)

/**
 * 上传/列表/重命名响应（不返回磁盘路径）。JSON 字段顺序即声明顺序：
 * `{id,expertId,originalFilename,displayFilename,extension,byteLength,uploadedBy,uploadedAt,
 * updatedBy,updatedAt,version,previewUrl,downloadUrl}`。
 *
 * [extension] 是不带点的小写扩展名（`mp4`/`mov`/`webm`），由不可变的 [originalFilename] 派生；
 * [previewUrl]/[downloadUrl] 是 contextPath 下的同源路径（不含域名与磁盘路径）。
 */
data class MeetingVideoView(
    val id: String,
    val expertId: String,
    val originalFilename: String,
    val displayFilename: String,
    val extension: String,
    val byteLength: Long,
    val uploadedBy: String,
    val uploadedAt: String,
    val updatedBy: String,
    val updatedAt: String,
    val version: Long,
    val previewUrl: String,
    val downloadUrl: String
)

/** 列表响应：`{items,total,page,size}`；无记录时 `items=[]`（不返回 404）。 */
data class MeetingVideoPage(
    val items: List<MeetingVideoView>,
    val total: Long,
    val page: Int,
    val size: Int
)

/**
 * 04 私有业务异常：只由 `ExpertMeetingVideoController` 的局部 `@ExceptionHandler` 映射为固定
 * `{code,message,detail}`，不改全局异常处理器（全局处理器会把异常 message 透给客户端）。
 *
 * 状态与码是计划 §2 的固定集合：400 INVALID_VIDEO_NAME / VIDEO_SIZE_MISMATCH / EMPTY_VIDEO、
 * 404 VIDEO_NOT_FOUND、409 VIDEO_NAME_CONFLICT / VIDEO_VERSION_CONFLICT、413 VIDEO_TOO_LARGE、
 * 415 UNSUPPORTED_VIDEO_TYPE、429 VIDEO_UPLOAD_BUSY、503 EXPERT_LOOKUP_UNAVAILABLE、
 * 507 VIDEO_STORAGE_FULL、500 VIDEO_STORE_FAILED（401 由 controller 与环境返回）。
 *
 * 请求形状类问题（缺失/非法 expertId、非法分页参数、缺失 expectedVersion、身份列超宽）统一落到
 * 400 的三个码里：`INVALID_VIDEO_NAME` 是其中唯一的「请求描述无效」码，具体原因写在 message；
 * 名称本身的校验也用它。message 只写面向操作员的固定文案，绝不包含磁盘路径、SQL 或堆栈。
 */
class MeetingVideoException private constructor(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /** 400：名称/标识/分页等请求描述不合法。 */
        fun invalidName(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME", message)

        /** 400：声明的字节数与实际不符（含 Content-Length 与 expectedSize 不一致）。 */
        fun sizeMismatch(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH", message)

        /** 400：零字节视频（expectedSize=0 或流实际读到 0 字节）。 */
        fun empty(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.BAD_REQUEST, "EMPTY_VIDEO", message)

        /** 404：元数据不存在/不属于该专家、文件缺失（三者不区分，避免泄露存在性）。 */
        fun notFound(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", message)

        /** 409：同一专家内规范化展示名精确撞唯一索引（大小写敏感）。 */
        fun nameConflict(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.CONFLICT, "VIDEO_NAME_CONFLICT", message)

        /** 409：重命名的 expectedVersion 不是当前版本（CAS 失败）。 */
        fun versionConflict(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.CONFLICT, "VIDEO_VERSION_CONFLICT", message)

        /** 413：expectedSize/Content-Length 前置超限或流实际字节数超过上限。 */
        fun tooLarge(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.PAYLOAD_TOO_LARGE, "VIDEO_TOO_LARGE", message)

        /** 415：扩展名不在 mp4/mov/webm 白名单，或请求不是 octet-stream 原始字节流。 */
        fun unsupportedType(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_VIDEO_TYPE", message)

        /** 429：本实例两个上传槽都已占用。 */
        fun busy(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.TOO_MANY_REQUESTS, "VIDEO_UPLOAD_BUSY", message)

        /** 503：身份查询服务不可用（绝不伪装 404）。 */
        fun lookupUnavailable(message: String, cause: Throwable? = null): MeetingVideoException =
            MeetingVideoException(HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_LOOKUP_UNAVAILABLE", message, cause)

        /** 507：剩余空间不足（前置余量检查或写入期 ENOSPC）。 */
        fun storageFull(message: String): MeetingVideoException =
            MeetingVideoException(HttpStatus.INSUFFICIENT_STORAGE, "VIDEO_STORAGE_FULL", message)

        /** 500：文件/数据库读写失败或落盘完整性核验失败；固定文案，内部 SQL/路径不外泄。 */
        fun storeFailed(message: String, cause: Throwable? = null): MeetingVideoException =
            MeetingVideoException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_STORE_FAILED", message, cause)
    }
}
