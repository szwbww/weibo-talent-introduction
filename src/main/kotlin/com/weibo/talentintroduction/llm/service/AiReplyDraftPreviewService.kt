package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailVariableService
import org.springframework.stereotype.Service

@Service
class AiReplyDraftPreviewService(
    private val mailVariableService: MailVariableService,
    private val mailSenderAccountRepository: MailSenderAccountRepository
) {
    data class PreviewResult(
        val renderedText: String,
        val warningCodes: List<String>
    )

    fun preview(raw: String, contact: ExpertContact, senderAccountCode: String?): PreviewResult {
        val code = senderAccountCode?.trim()?.takeIf { it.isNotEmpty() }
        if (code == null) {
            return PreviewResult(raw, listOf(WARNING_ACCOUNT_NOT_FOUND))
        }
        val account = mailSenderAccountRepository.findByAccountCode(code)
            ?: return PreviewResult(raw, listOf(WARNING_ACCOUNT_NOT_FOUND))
        val result = mailVariableService.renderPreview(raw, account, contact)
        val warnings = mutableListOf<String>()
        if (result.invalidTokens.isNotEmpty()) {
            warnings += WARNING_INVALID_PLACEHOLDER
        }
        return PreviewResult(result.rendered, warnings)
    }

    companion object {
        const val WARNING_ACCOUNT_NOT_FOUND = "AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND"
        const val WARNING_INVALID_PLACEHOLDER = "AI_REPLY_PREVIEW_INVALID_PLACEHOLDER"
    }
}
