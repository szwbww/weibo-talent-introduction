package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.ComposeTemplateSnapshot
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IntroductionMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val mailVariableService: MailVariableService,
    private val personalizationGateService: PersonalizationGateService = PersonalizationGateService(),
    private val mailContentService: MailContentService = MailContentService()
) {
    fun compose(
        accountCode: String,
        expert: ExpertProfile,
        templateId: Long? = null,
        enforcePersonalizationGate: Boolean = true
    ): ComposedMail {
        // A3 (I-4): 未显式传入内存快照时按当前模板读取一次（人工单发/旧调用逐字不变）。
        val snapshot = runCatching { loadTemplateSnapshot(templateId) }.getOrNull()
        return composeWith(accountCode, expert, templateId, enforcePersonalizationGate, snapshot)
    }

    /**
     * A3 (I-4): 批量执行使用**本次内存模板内容快照**渲染，运行中模板变化不混用。
     */
    fun composeFromSnapshot(
        accountCode: String,
        expert: ExpertProfile,
        templateId: Long?,
        enforcePersonalizationGate: Boolean,
        snapshot: ComposeTemplateSnapshot
    ): ComposedMail = composeWith(accountCode, expert, templateId, enforcePersonalizationGate, snapshot)

    private fun loadTemplateSnapshot(templateId: Long?): ComposeTemplateSnapshot? =
        if (templateId != null) {
            mailComposeTemplateService.loadSnapshot(templateId)
        } else {
            mailComposeTemplateService.loadSnapshotByCode("INTRODUCTION")
        }

    private fun composeWith(
        accountCode: String,
        expert: ExpertProfile,
        templateId: Long?,
        enforcePersonalizationGate: Boolean,
        snapshot: ComposeTemplateSnapshot?
    ): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val variables = buildVariables(account, expert)
        val variantSeed = MailComposeTemplateService.variantSeedFor(expert.orcidId, expert.email)
        val rendered = renderTemplate(templateId, variables, variantSeed, snapshot)

        // I-4/I-5: 批量显式门禁开关关闭时不再检查个性化缺项（裸变量按 renderText 变空串）。
        // 人工单发/旧调用保持默认 true，行为逐字不变。
        if (enforcePersonalizationGate) {
            val gateTemplateId = templateId ?: rendered.templateId
            val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
            val gate = personalizationGateService.evaluate(rendered.rawTexts, variables, requiredKeys)
            if (gate.blocked) {
                throw PersonalizationGateException(gate.missingKeys)
            }
        }

        val domain = account.senderEmail.substringAfter("@")
        val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"

        val plain = rendered.body
        val mail = ComposedMail(
            to = expert.email ?: error("Expert email is required for introduction mail"),
            subject = rendered.subject,
            body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
            html = true,
            text = plain,
            messageId = messageId
        )
        personalizationGateService.requireNoPlaceholderResidue(mail.subject, plain)
        return mail
    }

    /**
     * I-4: 与 [compose] 共用同一模板 ID/版本、seed、实际选中变体与 [MailVariableService] 实际值，
     * 只返回缺失的个性化 key（不发送、不写库）。预估与执行用同一判定，避免"关了仍挡/开了不挡"。
     *
     * A3：`accountCode` 可为 null —— 预估侧用 null 只判**专家变量**；账号变量（见
     * [MailComposeTemplateService.ACCOUNT_VARIABLE_KEYS]）缺项是启动前配置错误，由启动校验负责，
     * 绝不用 `account=null` 伪造缺项把全部专家筛掉。
     */
    fun evaluateForBatch(accountCode: String?, expert: ExpertProfile, templateId: Long? = null): BatchTemplateEvaluation {
        val account = accountCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { mailSenderAccountService.getEnabledAccount(it) }
        val variables = buildVariables(account, expert)
        val variantSeed = MailComposeTemplateService.variantSeedFor(expert.orcidId, expert.email)
        val rendered = renderTemplate(templateId, variables, variantSeed, snapshot = null)
        val gateTemplateId = templateId ?: rendered.templateId
        val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
        val gate = personalizationGateService.evaluate(rendered.rawTexts, variables, requiredKeys)
        return BatchTemplateEvaluation(
            missingKeys = gate.missingKeys,
            templateId = gateTemplateId,
            variantSeed = variantSeed
        )
    }

    private fun renderTemplate(
        templateId: Long?,
        variables: Map<String, String>,
        variantSeed: Int,
        snapshot: ComposeTemplateSnapshot?
    ): ComposeTemplateRenderResult = when {
        snapshot != null -> snapshot.render(variables, variantSeed)
        templateId != null -> mailComposeTemplateService.render(templateId, variables, variantSeed)
        else -> mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
    }

    fun buildTemplateVariables(expert: ExpertProfile, accountCode: String?): List<TemplateVariableItem> {
        val account = accountCode?.let { mailSenderAccountService.getEnabledAccount(it) }
        return toTemplateVariableItems(buildVariables(account, expert))
    }

    fun buildVariables(account: MailSenderAccount?, expert: ExpertProfile): Map<String, String> =
        mailVariableService.buildVariables(account, expert)

    fun toTemplateVariableItems(variables: Map<String, String>): List<TemplateVariableItem> =
        mailVariableService.toTemplateVariableItems(variables)

    companion object {
        val VARIABLE_LABELS: Map<String, String> = MailVariableService.VARIABLE_LABELS
    }
}

data class TemplateVariableItem(
    val key: String,
    val label: String,
    val value: String,
    val filled: Boolean
)

/**
 * I-4: 批量模板门禁判定结果 —— 与 [IntroductionMailComposer.compose] 同一模板 ID/seed/变体/变量。
 */
data class BatchTemplateEvaluation(
    val missingKeys: List<String>,
    val templateId: Long?,
    val variantSeed: Int
)

data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false,
    val text: String? = null,
    val messageId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
    /** 显式绕过抑制名单拦截。只允许人工单发路径按操作端请求置 true；批量与自动路径恒为 false。见 plan I-4。 */
    val allowSuppressedRecipient: Boolean = false,
    /** 会议日历附件快照（fast-p 02，I-3）：null=无附件，旧无附件构造形态不变；
     *  非 null 时 SMTP 以 multipart/mixed 携带该快照的 icsText（text/calendar 附件）。 */
    val calendarAttachment: CalendarAttachmentSnapshot? = null,
    /** 通用附件原件（fast-p 05，I-3）：默认空 = 旧无附件/仅 ICS 形态逐字不变；
     *  元素是 04 已验证快照 + 已核过尺寸/hash 的原件字节，非空时 SMTP 在既有
     *  ICS 之后按选择顺序以 multipart/mixed 携带。所有既有调用点保持默认不改。 */
    val outboundAttachments: List<OutboundMailFile> = emptyList(),
    /** Business reply context, independent of subject and SMTP thread headers. */
    val isReply: Boolean = false
)
