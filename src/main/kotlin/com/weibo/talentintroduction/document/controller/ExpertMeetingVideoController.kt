package com.weibo.talentintroduction.document.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.document.domain.MeetingVideoRenameRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.document.service.ExpertMeetingVideoService
import com.weibo.talentintroduction.document.service.MeetingVideoFile
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

/**
 * 04（I-1–I-10）：会议视频 raw 上传 / 列表 / 重命名 / 播放 / 下载。
 *
 * - 上传体是 **application/octet-stream 原始字节**：原地读取 [HttpServletRequest.getInputStream]，
 *   不调用 `getParts`/`MultipartFile`、不使用 `@RequestBody ByteArray`，因此 1 GiB 不受
 *   `spring.servlet.multipart`（100MB/101MB）限制，也不会把整个文件读进内存；
 * - 全部端点要求现有登录会话（`AuthSessionKeys.USERNAME`）；用户名只取服务端会话；
 * - 播放/下载返回 [FileSystemResource]：Range/206/Content-Range/416 全部由 Spring 5.3 的
 *   Resource/ResourceRegion 转换器处理（鉴权与归属检查在此之前完成），不手动设置整文件
 *   Content-Length、不自制 Range 解析器、不自行截流；
 * - 错误映射全部在本 controller 的局部 `@ExceptionHandler`（不动全局处理器，避免内部异常
 *   message 外泄），响应固定 `{code,message,detail}`；未登录 401 UNAUTHORIZED。
 */
@RestController
@RequestMapping("/api/experts/meeting-videos")
class ExpertMeetingVideoController(private val service: ExpertMeetingVideoService) {

    @PostMapping(consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun upload(
        request: HttpServletRequest,
        @RequestParam(required = false) expertId: String?,
        @RequestParam(required = false) level: ExpertIndexLevel?,
        @RequestParam(required = false) originalName: String?,
        @RequestParam(required = false) nameStem: String?,
        @RequestParam(required = false) expectedSize: String?,
        @RequestParam(required = false) meetingRecordId: String? = null
    ): ResponseEntity<Any> {
        val username = sessionUsername(request) ?: return unauthorized()
        val command = MeetingVideoUploadRequest(
            expertId = expertId,
            level = level ?: ExpertIndexLevel.RAW,
            originalName = originalName,
            nameStem = nameStem,
            expectedSize = expectedSize,
            declaredLength = request.contentLengthLong.takeIf { it >= 0L },
            meetingRecordId = meetingRecordId
        )
        val view = request.inputStream.use { body -> service.upload(command, username, request.contextPath, body) }
        return ResponseEntity.status(HttpStatus.CREATED).body(view)
    }

    /**
     * 非 `application/octet-stream` 的 POST 落到本方法（consumes 不匹配时映射层退到更宽的
     * 映射，不会进入上面的方法体），因此错 Content-Type 的请求体既不会被解析成参数，也不会被
     * 当成文件写盘，固定 415 UNSUPPORTED_VIDEO_TYPE。
     */
    @PostMapping
    fun rejectUnsupportedBody(request: HttpServletRequest): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        throw MeetingVideoException.unsupportedType(UNSUPPORTED_BODY_MESSAGE)
    }

    @GetMapping
    fun list(
        request: HttpServletRequest,
        @RequestParam(required = false) expertId: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) meetingRecordId: String? = null
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return ResponseEntity.ok(service.list(expertId, page, size, request.contextPath, meetingRecordId))
    }

    @PatchMapping("/{id}")
    fun rename(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false) expertId: String?,
        @RequestBody(required = false) body: MeetingVideoRenameBody?
    ): ResponseEntity<Any> {
        val username = sessionUsername(request) ?: return unauthorized()
        val payload = body ?: throw MeetingVideoException.invalidName("请求体无效")
        if (payload.nameStem.isNullOrBlank() || payload.expectedVersion == null) {
            throw MeetingVideoException.invalidName("请求体必须包含 nameStem 与 expectedVersion")
        }
        val command = MeetingVideoRenameRequest(payload.nameStem, payload.expectedVersion)
        return ResponseEntity.ok(service.rename(command, expertId, id, username, request.contextPath))
    }

    @GetMapping("/{id}/preview")
    fun preview(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false) expertId: String?
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return render(service.resolveFile(expertId, id), inline = true)
    }

    @GetMapping("/{id}/download")
    fun download(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false) expertId: String?
    ): ResponseEntity<Any> {
        sessionUsername(request) ?: return unauthorized()
        return render(service.resolveFile(expertId, id), inline = false)
    }

    /** 展示名只来自服务端元数据；UTF-8 文件名经 ContentDisposition 编码，不拼接路径。 */
    private fun render(file: MeetingVideoFile, inline: Boolean): ResponseEntity<Any> {
        val disposition = ContentDisposition
            .builder(if (inline) "inline" else "attachment")
            .filename(file.displayFilename, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(X_CONTENT_TYPE_OPTIONS, "nosniff")
            .body<Any>(FileSystemResource(file.path.toFile()))
    }

    @ExceptionHandler(MeetingVideoException::class)
    fun handleMeetingVideoFailure(ex: MeetingVideoException): ResponseEntity<ApiErrorResponse> =
        error(ex.status, ex.code, ex.message)

    /** 请求体/查询参数在进入业务前就无法解析：固定 400，不回显原始输入。 */
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class
    )
    fun handleUnreadableRequest(): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, INVALID_VIDEO_NAME_CODE, "请求参数无效")

    /** 非 octet-stream 的请求体（或未知扩展名之外的媒体类型）一律 415。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_VIDEO_TYPE_CODE, "只接受 application/octet-stream 原始字节流")

    /** 未预期异常：固定文案，绝不把内部 SQL/路径/字段值透给客户端。 */
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedFailure(): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, VIDEO_STORE_FAILED_CODE, "视频处理失败，请稍后重试")

    private fun error(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(ApiErrorResponse(code, message ?: status.reasonPhrase, status.reasonPhrase))

    private fun sessionUsername(request: HttpServletRequest): String? =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))

    private companion object {
        const val INVALID_VIDEO_NAME_CODE = "INVALID_VIDEO_NAME"
        const val UNSUPPORTED_VIDEO_TYPE_CODE = "UNSUPPORTED_VIDEO_TYPE"
        const val VIDEO_STORE_FAILED_CODE = "VIDEO_STORE_FAILED"
        const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
        const val UNSUPPORTED_BODY_MESSAGE = "只接受 application/octet-stream 原始字节流"
    }
}

/** PATCH 请求体 `{nameStem, expectedVersion}`；未知字段由 Jackson 宽松忽略，缺失在 service 校验。 */
data class MeetingVideoRenameBody(
    val nameStem: String? = null,
    val expectedVersion: Long? = null
)
