package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.service.OutboundAttachmentException
import com.weibo.talentintroduction.mail.service.OutboundAttachmentService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

/**
 * 04 T3：通用附件上传与草稿原件下载。
 *
 * - `POST /api/mail/conversations/{contactId}/outbound-attachments`，multipart 字段
 *   `file`（一次一个原件），201 返回 id/filename/contentType/byteLength/sha256/downloadUrl。
 * - `GET /api/mail/conversations/{contactId}/outbound-attachments/{id}/download`，
 *   仅本人草稿可下载；强制 attachment 下载、nosniff、private,no-store。
 *
 * 身份只来自会话（`AuthSessionKeys.USERNAME`），不接受请求体里的 operatorName；本
 * 路径沿全站 `/api/` 前缀 AuthInterceptor，未登录在到达本 controller 之前就是 401。
 * multipart 解析阶段的大小超限由容器抛 `MaxUploadSizeExceededException`，经
 * `GlobalExceptionHandler` 固定 413——不在这里用局部 handler 兜（那对解析期异常无效）。
 * `file` 字段用 `required = false` 自行判缺：缺失返回 400 业务文案，而不是让
 * `MissingServletRequestPartException` 落到通用 500。
 */
@RestController
@RequestMapping("/api/mail/conversations")
class OutboundAttachmentController(
    private val service: OutboundAttachmentService
) {

    @PostMapping("/{contactId}/outbound-attachments")
    fun upload(
        @PathVariable contactId: Long,
        @RequestParam(name = "file", required = false) file: MultipartFile?,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val operator = sessionUsername(servletRequest) ?: return unauthorized()
        // 0 字节文件是合法原件（I-3），因此这里只判字段缺失，不判 part 是否为空。
        val part = file ?: throw OutboundAttachmentException.badRequest("缺少 multipart 字段 file")
        val body = part.inputStream.use { stream ->
            service.upload(contactId, operator, part.originalFilename, part.contentType, stream)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body)
    }

    @GetMapping("/{contactId}/outbound-attachments/{id}/download")
    fun downloadDraft(
        @PathVariable contactId: Long,
        @PathVariable id: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        val operator = sessionUsername(servletRequest) ?: return unauthorized()
        val file = service.resolveDraftDownload(contactId, id, operator)
        val snapshot = file.snapshot
        val headers = HttpHeaders().apply {
            // 写入时已规范化为合法 type/subtype；读取前又经 codec 校验，这里不再兜底。
            contentType = MediaType.parseMediaType(snapshot.contentType)
            contentDisposition = ContentDisposition.attachment()
                .filename(snapshot.filename, StandardCharsets.UTF_8)
                .build()
            contentLength = file.bytes.size.toLong()
            set(HttpHeaders.CACHE_CONTROL, "private,no-store")
        }
        headers.set("X-Content-Type-Options", "nosniff")
        return ResponseEntity(file.bytes, headers, HttpStatus.OK)
    }

    private fun sessionUsername(servletRequest: HttpServletRequest): String? =
        (servletRequest.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** 未登录兜底（auth 关闭时 AuthInterceptor 不生效）：固定 401，不落通用 500。 */
    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                code = "UNAUTHORIZED",
                message = "未登录",
                detail = HttpStatus.UNAUTHORIZED.reasonPhrase
            )
        )
}
