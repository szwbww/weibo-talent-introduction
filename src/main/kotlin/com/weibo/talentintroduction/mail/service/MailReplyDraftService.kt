package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailReplyDraftRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class MailReplyDraftService(
    private val repository: MailReplyDraftRepository,
    private val contacts: ExpertContactRepository,
    private val inbound: InboundMailProcessingRepository,
    private val accounts: MailSenderAccountRepository,
    private val attachments: OutboundAttachmentService
) {
    private val mailContent = MailContentService()
    fun getTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftDetail {
        identity(owner); targetShape(target)
        val row = repository.findTarget(owner,target)
        if (row != null) return detail(row)
        validateTarget(target)
        return MailReplyDraftDetail(target=target)
    }
    fun get(owner: String, id: Long): MailReplyDraftDetail { identity(owner); return detail(owned(owner,id)) }
    // Each write is one atomic CAS statement; conflict rereads must not use an old REPEATABLE READ snapshot.
    fun save(owner: String, target: MailReplyDraftTarget, request: MailReplyDraftSaveRequest): MailReplyDraftDetail {
        identity(owner); targetShape(target)
        if (request.expectedVersion < 0) invalid("非法 expectedVersion")
        val existing = repository.findTarget(owner,target)
        if (existing == null) {
            if (request.expectedVersion != 0L || request.reopen) conflict(null)
        } else {
            if (existing.version != request.expectedVersion) conflict(existing)
            if (existing.state != MailReplyDraftState.ACTIVE && !request.reopen) conflict(existing, true)
        }
        validateTarget(target)
        val content = normalizeAttachments(owner,target,request.content,existing)
        val json = MailReplyDraftContextCodec.serialize(content.context)
        if (content.subject.codePointCount(0,content.subject.length) > 255) invalid("主题最多255字符")
        val bytes = sequenceOf(content.subject,content.html,content.text,json).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        if (bytes > MAIL_REPLY_DRAFT_MAX_BYTES) throw MailReplyDraftException(HttpStatus.PAYLOAD_TOO_LARGE,"DRAFT_TOO_LARGE","草稿文本总量超过1MiB")
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        if (existing == null) {
            val id = try { repository.insert(owner,target,content,json,now) } catch (e: DuplicateKeyException) { conflict(repository.findTarget(owner,target)) }
            return detail(owned(owner,id))
        }
        if (!repository.save(owner,existing.id,existing.version,existing.state,content,json,now)) conflict(owned(owner,existing.id))
        return detail(existing.copy(version=existing.version+1,state=MailReplyDraftState.ACTIVE,subject=content.subject,html=content.html,text=content.text,contextJson=json,updatedAt=now,
            sendAttemptId=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptId else null,
            sendVersion=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendVersion else null,
            sendAttemptStatus=if (existing.state == MailReplyDraftState.ACTIVE) existing.sendAttemptStatus else null))
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun discard(owner: String, id: Long, expectedVersion: Long): MailReplyDraftDetail {
        identity(owner)
        repository.lockOwned(owner,id)
        val row = owned(owner,id)
        if (expectedVersion <= 0 || row.version != expectedVersion) conflict(row)
        if (row.state != MailReplyDraftState.ACTIVE) return detail(row)
        if (row.sendAttemptStatus == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
            throw MailReplyDraftException(HttpStatus.CONFLICT,"DRAFT_SEND_IN_PROGRESS","草稿正在发送，不能放弃",row.version,row.state)
        }
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        if (!repository.discard(owner,id,expectedVersion,now)) conflict(owned(owner,id))
        return detail(row.copy(version=row.version+1,state=MailReplyDraftState.DISCARDED,subject=null,html=null,text=null,contextJson=null,updatedAt=now))
    }
    fun list(owner: String, search: String?, accountScope: String?, page: Int, size: Int): MailReplyDraftList {
        identity(owner); filterScope(accountScope)
        if (page < 0 || size !in 1..100) invalid("非法草稿分页")
        return repository.list(owner,accountScope?.takeIf { it.isNotEmpty() },search,page,size)
    }
    fun summaries(owner: String, contactIds: List<Long>, accountScope: String?): MailReplyDraftSummaries {
        identity(owner); filterScope(accountScope)
        val ids = contactIds.distinct()
        if (ids.size > 100 || ids.any { it <= 0 }) invalid("contactIds最多100个真实id")
        return repository.summaries(owner,accountScope?.takeIf { it.isNotEmpty() },ids)
    }

    internal fun validateInboundSend(owner: String, ref: MailReplyDraftRef, target: MailReplyDraftTarget,
        subject: String, html: String, text: String?, ragCodes: List<String>?, ragFingerprint: String?,
        edited: Boolean?, freeTextPreview: String?, meeting: MeetingInput?, previewSha: String?,
        attachmentIds: List<String>, unsupportedQa: Boolean): ValidatedDraftSendRef {
        val row = activeSend(owner,ref)
        val content = content(row)
        val qa = content.context.qa
        val codes = qa?.ragFactCodes.orEmpty()
        val expectedEdited = if (codes.isEmpty()) null else
            text.orEmpty().trim() != mailContent.normalizeManualTextLineBreaks(qa?.baselineText.orEmpty()).trim()
        val savedMeeting = content.context.meeting
        val submittedInput = meeting?.let { MailReplyDraftMeetingInput(it.zoneId,it.startLocal,it.endLocal,it.zoomUrl,it.generatedAt) }
        if (row.target != target || content.context.followUpAnchorMailRecordId != null || unsupportedQa ||
            !sameBody(content,subject,html,text) || codes != ragCodes.orEmpty() ||
            qa?.ragCorpusFingerprint.orEmpty() != ragFingerprint.orEmpty() || edited != expectedEdited ||
            !freeTextPreview.isNullOrBlank() || savedMeeting?.input != submittedInput ||
            savedMeeting?.preview?.attachment?.sha256 != previewSha ||
            savedMeeting != null && savedMeeting.state != "ready" ||
            content.context.meetingAccountCode?.takeIf { it.isNotEmpty() }?.let { it != target.accountScope } == true ||
            !sameAttachments(content,attachmentIds)) mismatch(row)
        return validated(row,content)
    }

    internal fun validateConversationSend(owner: String, ref: MailReplyDraftRef, contactId: Long, scope: String?,
        requestId: String, anchorId: Long?, subject: String, html: String, text: String?,
        attachmentIds: List<String>): ValidatedDraftSendRef {
        val row = activeSend(owner,ref)
        val content = content(row)
        if (!conversationTarget(row,contactId,scope) || !sameBody(content,subject,html,text) ||
            content.context.requestId != requestId || content.context.followUpAnchorMailRecordId != anchorId ||
            row.target.kind == MailReplyDraftKind.INBOUND && anchorId == null ||
            content.context.meeting != null || !sameAttachments(content,attachmentIds)) mismatch(row)
        return validated(row,content)
    }

    /** Body-free terminal retries are admitted only by the durable binding, never by requestId alone. */
    internal fun completedBinding(owner: String, ref: MailReplyDraftRef, contactId: Long,
        processingId: Long?, scope: String?, attemptId: Long? = null): Long? {
        identity(owner)
        val row = owned(owner,ref.id)
        val targetMatches = if (processingId != null) row.target == MailReplyDraftTarget(contactId,MailReplyDraftKind.INBOUND,processingId,scope.orEmpty())
            else conversationTarget(row,contactId,scope)
        if (!targetMatches) mismatch(row)
        return row.sendAttemptId?.takeIf {
            row.state == MailReplyDraftState.SENT && row.sendVersion == ref.version &&
                row.version == ref.version + 1 && row.sendAttemptStatus == MailSendAttemptStatus.SENT &&
                (attemptId == null || it == attemptId)
        }
    }

    /** Caller already owns the sending transaction; take the draft lock before touching attempts. */
    internal fun lockForClaim(ref: ValidatedDraftSendRef): MailReplyDraftRow {
        val row = locked(ref.owner,ref.id)
        if (row.state != MailReplyDraftState.ACTIVE || row.version != ref.version) conflict(row)
        if (row.target != ref.target || content(row) != ref.content) mismatch(row)
        return row
    }
    internal fun lockForFinalize(ref: ValidatedDraftSendRef) { locked(ref.owner,ref.id) }
    internal fun releaseBinding(row: MailReplyDraftRow) {
        check(repository.releaseCompletedBinding(row.username,row.id,requireNotNull(row.sendVersion),requireNotNull(row.sendAttemptId))) {
            "Draft binding release failed"
        }
    }
    internal fun bind(ref: ValidatedDraftSendRef, attemptId: Long) {
        check(repository.bindAttempt(ref.owner,ref.id,ref.version,attemptId)) { "Draft send binding failed" }
    }
    internal fun closeSent(ref: ValidatedDraftSendRef, attemptId: Long, now: LocalDateTime) {
        if (repository.closeSent(ref.owner,ref.id,ref.version,attemptId,now)) return
        val row = owned(ref.owner,ref.id)
        if (row.sendAttemptId != attemptId || row.sendVersion != ref.version) error("Draft send binding changed")
        // An edit or an explicitly discarded UNKNOWN send is not resurrected by a late success.
        if (row.version > ref.version) { releaseBinding(row); return }
        error("Draft sent close failed")
    }
    private fun activeSend(owner: String, ref: MailReplyDraftRef): MailReplyDraftRow {
        identity(owner)
        val row = owned(owner,ref.id)
        if (ref.version <= 0 || row.version != ref.version || row.state != MailReplyDraftState.ACTIVE) conflict(row)
        return row
    }
    private fun locked(owner: String, id: Long): MailReplyDraftRow =
        repository.lockOwned(owner,id) ?: throw MailReplyDraftException(HttpStatus.NOT_FOUND,"NOT_FOUND","草稿不存在")
    private fun content(row: MailReplyDraftRow) = MailReplyDraftContent(row.subject.orEmpty(),row.html.orEmpty(),row.text.orEmpty(),
        MailReplyDraftContextCodec.parse(requireNotNull(row.contextJson)))
    private fun validated(row: MailReplyDraftRow, content: MailReplyDraftContent) =
        ValidatedDraftSendRef(row.username,row.id,row.version,row.target,content,row.sendAttemptId,row.sendVersion)
    private fun conversationTarget(row: MailReplyDraftRow, contactId: Long, scope: String?) =
        row.target.contactId == contactId && row.target.accountScope == scope.orEmpty()
    private fun sameBody(content: MailReplyDraftContent, subject: String, html: String, text: String?) =
        content.subject.trim() == subject.trim() && content.html == html && content.text == text.orEmpty()
    private fun sameAttachments(content: MailReplyDraftContent, ids: List<String>): Boolean {
        val items = content.context.outboundAttachmentDraft?.items.orEmpty()
        return items.all { it.state == "ready" } && items.mapNotNull { it.id } == ids
    }
    private fun mismatch(row: MailReplyDraftRow): Nothing = throw MailReplyDraftException(HttpStatus.CONFLICT,
        "DRAFT_CONTENT_MISMATCH","发送内容与已保存草稿不一致",row.version,row.state)
    private fun identity(owner: String) { if (owner.isBlank() || owner.length > 64) throw MailReplyDraftException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录") }
    private fun targetShape(target: MailReplyDraftTarget) {
        if (target.contactId <= 0 || target.accountScope.length > 100 || target.accountScope != target.accountScope.trim()) invalid("非法草稿目标")
        if (target.kind == MailReplyDraftKind.OUTBOUND && target.processingId != 0L || target.kind == MailReplyDraftKind.INBOUND && (target.processingId <= 0 || target.accountScope.isEmpty())) invalid("非法来信id/账号")
    }
    private fun validateTarget(target: MailReplyDraftTarget) {
        if (!contacts.existsById(target.contactId)) invalid("原专家已不存在")
        if (target.kind == MailReplyDraftKind.INBOUND) {
            val source = inbound.findById(target.processingId).orElse(null) ?: invalid("来信不存在")
            if (source.expertContactId != target.contactId || source.senderAccountCode != target.accountScope) invalid("来信不属于该专家/账号")
        } else if (target.accountScope.isNotEmpty()) {
            val account = accounts.findByAccountCode(target.accountScope) ?: invalid("账号不存在")
            if (account.accountCode == "SIMULATOR_NOOP") invalid("非法发件账号")
        }
    }
    private fun filterScope(scope: String?) { if (scope != null && (scope.length > 100 || scope != scope.trim())) invalid("非法账号范围") }
    private fun owned(owner: String, id: Long): MailReplyDraftRow = repository.findOwned(owner,id) ?: throw MailReplyDraftException(HttpStatus.NOT_FOUND,"NOT_FOUND","草稿不存在")
    private fun normalizeAttachments(owner: String, target: MailReplyDraftTarget, content: MailReplyDraftContent, existing: MailReplyDraftRow?): MailReplyDraftContent {
        MailReplyDraftContextCodec.validate(content.context)
        val field = content.context.outboundAttachmentDraft ?: return content
        val previous = existing?.contextJson?.let(MailReplyDraftContextCodec::parse)?.outboundAttachmentDraft?.items.orEmpty().associateBy { it.id }
        val items = field.items.map { item ->
            if (item.id == null) return@map if (item.state == "uploading") item.copy(state="failed",error="上传未完成，请重新选择文件") else item
            val snapshot = try { attachments.loadSnapshots(target.contactId,listOf(item.id),owner).single() } catch (e: OutboundAttachmentException) {
                // A newly submitted missing/foreign id is never accepted. Previously validated missing references remain recoverable.
                val old = previous[item.id]
                if (e.status == HttpStatus.NOT_FOUND && old != null) return@map old.copy(key=item.key,state="failed",error="附件已不可用，请重新选择文件")
                invalid("附件不存在或不属于当前用户/专家")
            }
            item.copy(filename=snapshot.filename,contentType=snapshot.contentType,byteLength=snapshot.byteLength,sha256=snapshot.sha256,
                state=if (item.state == "uploading") "failed" else item.state,error=if (item.state == "uploading") "上传未完成，请重新选择文件" else item.error)
        }
        return content.copy(context=content.context.copy(outboundAttachmentDraft=field.copy(items=items)))
    }
    private fun detail(row: MailReplyDraftRow): MailReplyDraftDetail {
        val content = if (row.state == MailReplyDraftState.ACTIVE) MailReplyDraftContent(row.subject.orEmpty(),row.html.orEmpty(),row.text.orEmpty(),MailReplyDraftContextCodec.parse(requireNotNull(row.contextJson))) else null
        val downloads = linkedMapOf<String,String>()
        var sendable = row.contactExists
        val restored = content?.context?.outboundAttachmentDraft?.let { field -> field.copy(items=field.items.map { item ->
            if (item.state == "uploading") { sendable=false; return@map item.copy(state="failed",error="上传未完成，请重新选择文件") }
            if (item.id != null) {
                try {
                    attachments.loadSnapshots(row.target.contactId,listOf(item.id),row.username).single()
                    if (item.state == "ready") downloads[item.id] = "/api/mail/conversations/${row.target.contactId}/outbound-attachments/${item.id}/download"
                } catch (e: OutboundAttachmentException) { sendable=false; return@map item.copy(state="failed",error="附件已不可用，请重新选择文件") }
            }
            if (item.state != "ready") sendable=false
            item
        }) }
        return MailReplyDraftDetail(row.id,row.version,row.state,row.target,
            content?.let { it.copy(context=it.context.copy(outboundAttachmentDraft=restored)) },row.createdAt,row.updatedAt,row.contactExists,row.expertName,row.expertEmail,
            row.sendAttemptId,row.sendVersion,row.sendAttemptStatus,downloads,sendable)
    }
    private fun conflict(row: MailReplyDraftRow?, closed: Boolean = false): Nothing = throw MailReplyDraftException(HttpStatus.CONFLICT,
        if (closed) "DRAFT_CLOSED" else "DRAFT_VERSION_CONFLICT",if (closed) "草稿已关闭，请显式重新编辑" else "草稿版本冲突，请重新读取",row?.version,row?.state)
    private fun invalid(message: String): Nothing = throw MailReplyDraftException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_DRAFT",message)
}
