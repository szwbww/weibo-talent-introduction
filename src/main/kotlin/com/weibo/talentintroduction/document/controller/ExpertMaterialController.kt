package com.weibo.talentintroduction.document.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.document.service.AttachmentTransferQueueFullException
import com.weibo.talentintroduction.document.service.ExpertMaterialPage
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.document.service.MaterialNotReadyException
import com.weibo.talentintroduction.document.service.MaterialTransferBatchResponse
import com.weibo.talentintroduction.document.service.ReconcileResult
import com.weibo.talentintroduction.mail.controller.MailboxAttachmentController
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/expert-contacts/{contactId}/materials")
class ExpertMaterialController(
    private val service: ExpertMaterialService
) {
    /**
     * GET 绝无 IMAP/下载入队/审核写：只做参数化只读查询 + 本地文件核验。
     */
    @GetMapping
    fun listMaterials(
        @PathVariable contactId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) sourceId: Long?,
        @RequestParam(required = false) state: String?
    ): ExpertMaterialPage = service.listMaterials(contactId, page, size, q, source, sourceId, state)

    /**
     * 显式请求下载：Session 登录（AuthInterceptor 之外再取 username 作 requested_by）
     * + 逐 ID 归属先全量校验；任一外来 ID 整批拒绝；容量耗尽 429。
     */
    @PostMapping("/transfers")
    fun requestTransfers(
        @PathVariable contactId: Long,
        @RequestBody request: TransferRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<MaterialTransferBatchResponse> {
        val session = servletRequest.getSession(false)
        val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String
            ?: throw IllegalArgumentException("未登录")
        val response = service.requestTransfers(contactId, request.attachmentIds, username)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    /** 显式历史修复：dryRun 只返回候选不写库；apply 幂等补缺 ExpertDocument。 */
    @PostMapping("/reconcile")
    fun reconcile(
        @PathVariable contactId: Long,
        @RequestBody request: ReconcileRequest
    ): ReconcileResult {
        requireNotNull(request.dryRun) { "dryRun is required" }
        return if (request.dryRun) {
            service.reconcileDryRun(contactId)
        } else {
            service.reconcileApply(contactId)
        }
    }
}

data class TransferRequest(
    val attachmentIds: List<Long>
)

data class ReconcileRequest(
    val dryRun: Boolean? = null
)

data class MaterialNotReadyResponse(
    val code: String = "MATERIAL_NOT_READY",
    val attachmentId: Long,
    val state: String?,
    val message: String
)

data class TransferQueueFullResponse(
    val code: String = "TRANSFER_QUEUE_FULL",
    val message: String
)

/**
 * 只作用于新材料控制器 + 既有三个文件控制器的高优先级 advice：
 * MaterialNotReadyException → 409 MATERIAL_NOT_READY；传输容量 → 429。
 * 不能依赖 GlobalExceptionHandler（其 catch(Exception) 会把裸异常变 500），
 * 故本 advice 必须 @Order(HIGHEST_PRECEDENCE) 抢在全局 advice 前。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = [
        ExpertMaterialController::class,
        ExpertDocumentBrowseController::class,
        ExpertDocumentAnalysisController::class,
        MailboxAttachmentController::class
    ]
)
class ExpertMaterialHttpExceptionAdvice {
    @ExceptionHandler(MaterialNotReadyException::class)
    fun handleNotReady(ex: MaterialNotReadyException): ResponseEntity<MaterialNotReadyResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            MaterialNotReadyResponse(
                attachmentId = ex.attachmentId,
                state = ex.state,
                message = ex.message ?: "Material is not ready"
            )
        )

    @ExceptionHandler(AttachmentTransferQueueFullException::class)
    fun handleTransferQueueFull(
        ex: AttachmentTransferQueueFullException
    ): ResponseEntity<TransferQueueFullResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            TransferQueueFullResponse(message = ex.message ?: "Attachment transfer queue is full")
        )
}
