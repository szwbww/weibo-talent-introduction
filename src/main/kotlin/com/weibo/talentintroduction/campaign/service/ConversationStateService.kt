package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ConversationStateService(
    private val expertContactRepository: ExpertContactRepository,
    private val statusHistoryRepository: ExpertContactStatusHistoryRepository
) {
    fun transition(
        contact: ExpertContact,
        toStatus: ConversationStatus,
        reason: String,
        source: String,
        now: LocalDateTime = LocalDateTime.now(),
        update: (ExpertContact) -> ExpertContact = { it }
    ): ExpertContact {
        val contactId = contact.id ?: error("Expert contact id is required")
        val fromStatus = contact.currentStatus
        val saved = expertContactRepository.save(
            update(
                contact.copy(
                    currentStatus = toStatus.name,
                    updatedAt = now
                )
            )
        )
        if (fromStatus != toStatus.name) {
            statusHistoryRepository.save(
                ExpertContactStatusHistory(
                    expertContactId = contactId,
                    fromStatus = fromStatus,
                    toStatus = toStatus.name,
                    reason = reason,
                    source = source,
                    createdAt = now
                )
            )
        }
        return saved
    }

    fun recommendedNextAction(status: String, manualHandoffRequired: Boolean): String =
        when {
            manualHandoffRequired -> "请人工处理当前待办，并在完成后更新阶段状态。"
            status == ConversationStatus.NEW.name -> "可以筛选专家并发送项目介绍邮件。"
            status == ConversationStatus.INTRO_SENT.name -> "等待专家回复，低频轮询收件箱。"
            status == ConversationStatus.INTEREST_CONFIRMED.name -> "发送会议邀约或确认专家可沟通时间。"
            status == ConversationStatus.MEETING_SCHEDULING.name -> "确认专家时间、时区和会议工具，生成会议链接。"
            status == ConversationStatus.MEETING_SCHEDULED.name -> "等待会议进行，会议后记录沟通结论。"
            status == ConversationStatus.MEETING_DONE.name -> "根据会议结论推进材料收集或企业匹配。"
            status == ConversationStatus.MATERIALS_REQUESTED.name -> "跟进专家提交 CV、证书、护照等材料。"
            status == ConversationStatus.MATERIALS_PARTIAL.name -> "审核已收到材料，并提醒补充缺失材料。"
            status == ConversationStatus.MATERIALS_RECEIVED.name -> "人工审核材料有效性，准备企业或项目匹配。"
            status == ConversationStatus.COMPANY_MATCHED.name -> "向专家确认企业与项目方向是否匹配。"
            status == ConversationStatus.APPLICATION_PREPARING.name -> "准备申请材料，必要时让专家确认关键信息。"
            status == ConversationStatus.VIDEO_REQUESTED.name -> "跟进专家提交视频或 VCR 材料。"
            status == ConversationStatus.VIDEO_RECEIVED.name -> "审核视频材料并推进承诺书或提交环节。"
            status == ConversationStatus.COMMITMENT_REQUESTED.name -> "跟进专家签署承诺文件。"
            status == ConversationStatus.COMMITMENT_RECEIVED.name -> "确认承诺文件后进入提交准备。"
            status == ConversationStatus.SUBMITTED.name -> "等待项目评审结果，定期跟进。"
            status == ConversationStatus.RESULT_PENDING.name -> "关注结果公布时间，准备结果通知。"
            status == ConversationStatus.REJECTED_THIS_ROUND.name -> "评估是否进入下一轮跟进。"
            status == ConversationStatus.NEXT_ROUND_FOLLOW_UP.name -> "等待下一轮项目窗口并保持联系。"
            status == ConversationStatus.QA_AUTO_REPLIED.name -> "等待专家对 QA 回复的进一步反馈。"
            status == ConversationStatus.MANUAL_HANDOFF.name -> "请人工接管当前专家沟通。"
            status == ConversationStatus.MANUAL_REVIEW.name -> "请人工审核邮件内容、材料或风险问题。"
            status == ConversationStatus.CLOSED.name -> "该专家联系已关闭，无需继续自动跟进。"
            else -> "请人工确认下一步动作。"
        }
}
