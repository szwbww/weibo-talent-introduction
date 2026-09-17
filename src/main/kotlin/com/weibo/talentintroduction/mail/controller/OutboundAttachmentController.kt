package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentException
import com.weibo.talentintroduction.mail.service.OutboundAttachmentService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentSnapshot
import com.weibo.talentintroduction.mail.service.OutboundAttachmentSnapshotCodec
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
 * 04 T3：通用附件上传与草稿原件下载；06 T4：已发送消息原件下载。
 *
 * - `POST /api/mail/conversations/{contactId}/outbound-attachments`，multipart 字段
 *   `file`（一次一个原件），201 返回 id/filename/contentType/byteLength/sha256/downloadUrl。
 * - `GET /api/mail/conversations/{contactId}/outbound-attachments/{id}/download`，
 *   仅本人草稿可下载；强制 attachment 下载、nosniff、private,no-store。
 * - `GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/outbound-attachments/
 *   {attachmentId}/download`（06 I-4）：已发消息原件，授权以消息关系为准（快照成员资格、
 *   真实非模拟发件账号、同专家），任何登录操作员可下载已发文件。
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
    private val service: OutboundAttachmentService,
    // 06 (I-4)：已发消息原件下载的消息归属校验所需（与 CalendarAttachmentController 同款
    // 读取规则）。可空默认值只让既有的直接构造（standalone）测试零改动；Spring 运行时按
    // 主构造器完整注入，新端点在入口 requireNotNull 防御性校验，绝不静默放行。
    private val mailRecordRepository: MailRecordRepository? = null,
    private val mailSenderAccountRepository: MailSenderAccountRepository? = null
) {
    private companion object {
        const val DIRECTION_OUTBOUND = "OUTBOUND"
        const val MANUAL_RICH_REPLY_MAIL_TYPE = "MANUAL_RICH_REPLY"
        const val SEND_STATUS_SENT = "SENT"

        /** 06 已发下载的统一 404 文案：不存在/错专家/错消息/快照未引用一律同款，不泄露存在性。 */
        const val SENT_NOT_AVAILABLE_MESSAGE = "已发送附件不可用"
    }

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

    /**
     * 06 (I-4)：已发送消息的通用附件原件下载。
     *
     * `GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/outbound-attachments/{attachmentId}/download`
     *
     * 授权以消息关系为准，而不是「任意上传 id」：
     * 1. 必须有真实会话（匿名 401，与 04 其它端点同款兜底）；
     * 2. `mail_record` 行必须存在且 `expertContactId` 等于 path、OUTBOUND +
     *    MANUAL_RICH_REPLY + SENT、发件账号是真实非 `SIMULATOR_NOOP` 账号（禁用账号仍可读）；
     * 3. `attachmentId` 必须在该记录持久快照里（成员资格先判，绝不接受任意上传 id 冒充已发）；
     * 4. 再按 04 服务按 (专家, id) 读原件（尺寸/hash/路径校验在 04），且元数据必须与快照
     *    的 name/type/size/hash 逐字一致。
     *
     * 已发文件对任何登录操作员可见（团队协作，不要求是上传者）；草稿下载仍然只上传者可下。
     * 上列任一不满足统一 404 固定文案，不泄露附件是否存在；绝不改状态、不重新渲染。
     */
    @GetMapping("/{contactId}/messages/{mailRecordId}/outbound-attachments/{attachmentId}/download")
    fun downloadSentAttachment(
        @PathVariable contactId: Long,
        @PathVariable mailRecordId: Long,
        @PathVariable attachmentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Any> {
        sessionUsername(servletRequest) ?: return unauthorized()
        val record = requireNotNull(mailRecordRepository) {
            "MailRecordRepository is not wired for sent attachment download"
        }.findById(mailRecordId).orElse(null)
        val snapshot = sentAttachmentSnapshot(record, contactId, attachmentId)
        val file = service.resolveForMessageDownload(contactId, attachmentId)
        // 快照成员资格已成立；元数据与快照必须逐字一致（同名/型/尺寸/hash），否则视为
        // 不属于该消息，绝不返回别行的原件。
        if (!sameAttachmentMetadata(snapshot, file.snapshot)) {
            throw OutboundAttachmentException.notFound(SENT_NOT_AVAILABLE_MESSAGE)
        }
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(file.snapshot.contentType)
            contentDisposition = ContentDisposition.attachment()
                .filename(file.snapshot.filename, StandardCharsets.UTF_8)
                .build()
            contentLength = file.bytes.size.toLong()
            set(HttpHeaders.CACHE_CONTROL, "private,no-store")
        }
        headers.set("X-Content-Type-Options", "nosniff")
        return ResponseEntity(file.bytes, headers, HttpStatus.OK)
    }

    /**
     * 06 (I-4)：已发消息快照成员资格判定 —— 记录归属/方向/类型/状态/发件账号可见性 + 该 id
     * 必须出现在存档快照里；任一不满足（含快照缺失/损坏）统一 404 固定文案。
     *
     * 账号可见性复制 `CalendarAttachmentController` 的既有读取规则
     * （`findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)`，无 enabled 附加条件、无新造
     * 用户级 ACL）；不依赖请求里的 accountScope 当权限。
     */
    private fun sentAttachmentSnapshot(
        record: MailRecord?,
        contactId: Long,
        attachmentId: String
    ): OutboundAttachmentSnapshot {
        val eligible = record?.takeIf {
            it.expertContactId == contactId &&
                it.direction == DIRECTION_OUTBOUND &&
                it.mailType == MANUAL_RICH_REPLY_MAIL_TYPE &&
                it.sendStatus == SEND_STATUS_SENT
        }
        // 展示语义解析：NULL/空白与损坏都归为「无可用快照」→ 404，绝不 500。
        val snapshots = eligible?.let {
            OutboundAttachmentSnapshotCodec.parseOrNull(it.outboundAttachmentsJson)
        }
        val accountCode = eligible?.senderAccountCode
        val accountInScope = accountCode != null && accountCode in requireNotNull(mailSenderAccountRepository) {
            "MailSenderAccountRepository is not wired for sent attachment download"
        }.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            .map { it.accountCode }
            .toSet()
        val snapshot = snapshots?.firstOrNull { it.id == attachmentId }
        if (snapshot == null || !accountInScope) {
            throw OutboundAttachmentException.notFound(SENT_NOT_AVAILABLE_MESSAGE)
        }
        return snapshot
    }

    private fun sameAttachmentMetadata(
        snapshot: OutboundAttachmentSnapshot,
        metadata: OutboundAttachmentSnapshot
    ): Boolean =
        snapshot.id == metadata.id &&
            snapshot.filename == metadata.filename &&
            snapshot.contentType == metadata.contentType &&
            snapshot.byteLength == metadata.byteLength &&
            snapshot.sha256 == metadata.sha256

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
