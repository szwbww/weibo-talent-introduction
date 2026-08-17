package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.campaign.service.OperatorStatusReconcileService
import com.weibo.talentintroduction.expert.service.CandidateOperatorStatusSyncService
import com.weibo.talentintroduction.expert.service.ExpertReachabilitySyncService
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.mail.queue.MailQueuePublisher
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.scheduling", name = ["enabled"], havingValue = "true")
class MailAutomationScheduler(
    private val properties: MailSchedulingProperties,
    private val mailQueuePublisherProvider: ObjectProvider<MailQueuePublisher>,
    private val batchAutoMailReplyService: BatchAutoMailReplyService,
    private val initialOutreachService: InitialOutreachService,
    private val candidateOperatorStatusSyncService: CandidateOperatorStatusSyncService,
    private val operatorStatusReconcileService: OperatorStatusReconcileService,
    private val expertReachabilitySyncService: ExpertReachabilitySyncService,
    private val taskExecutionService: TaskExecutionService
) {
    @Scheduled(cron = "\${talent-introduction.scheduling.auto-reply-all-cron:-}")
    fun scheduleAutoReplyAll() {
        val request = TaskDispatchRequest(
            maxMessagesPerAccount = properties.autoReplyMaxMessagesPerAccount,
            dispatchMode = dispatchMode()
        )
        taskExecutionService.runAndRecord("AUTO_REPLY_ALL", "SCHEDULED", request) {
            val publisher = mailQueuePublisherProvider.getIfAvailable()
            if (publisher != null) {
                publisher.publishAutoReplyAll(properties.autoReplyMaxMessagesPerAccount)
            } else {
                batchAutoMailReplyService.receiveAndAutoReplyAll(properties.autoReplyMaxMessagesPerAccount)
            }
        }
    }

    @Scheduled(cron = "\${talent-introduction.scheduling.initial-outreach-cron:-}")
    fun scheduleInitialOutreach() {
        if (properties.initialOutreachCampaignId <= 0) {
            return
        }

        val request = TaskDispatchRequest(
            campaignId = properties.initialOutreachCampaignId,
            batchSize = properties.initialOutreachBatchSize,
            dispatchMode = dispatchMode()
        )
        // 通过 runAndRecord 已有的 onStarted 回调拿到本次执行的 id（I2a-3），传给同步分支。
        // 队列分支（publisher != null）刻意不传 executionId：MailQueueConsumer 在另一个
        // runAndRecord 上下文里跑，会有自己的 execution 行；队列模式下 mail_record.
        // task_execution_id 保持 null（该执行经队列派发，邮件未直接关联）。
        var executionId: Long? = null
        taskExecutionService.runAndRecord(
            "INITIAL_OUTREACH", "SCHEDULED", request,
            onStarted = { executionId = it }
        ) {
            val publisher = mailQueuePublisherProvider.getIfAvailable()
            if (publisher != null) {
                publisher.publishInitialOutreach(
                    campaignId = properties.initialOutreachCampaignId,
                    size = properties.initialOutreachBatchSize
                )
            } else {
                initialOutreachService.sendInitialBatch(
                    campaignId = properties.initialOutreachCampaignId,
                    size = properties.initialOutreachBatchSize,
                    taskExecutionId = executionId
                )
            }
        }
    }

    @Scheduled(cron = "\${talent-introduction.scheduling.operator-status-sync-cron:-}")
    fun scheduleOperatorStatusSync() {
        taskExecutionService.runAndRecord(
            "CANDIDATE_OPERATOR_STATUS_SYNC",
            "SCHEDULED",
            "operator-status-sync"
        ) {
            candidateOperatorStatusSyncService.reconcileAll()
        }
    }

    @Scheduled(cron = "\${talent-introduction.scheduling.operator-status-reconcile-cron:-}")
    fun scheduleOperatorStatusReconcile() {
        // 走 runAndRecordWithResult 使对账报告（ReconcileReport）落入 task_execution.result_summary，可在任务面板查看
        taskExecutionService.runAndRecordWithResult(
            "OPERATOR_STATUS_RECONCILE",
            "SCHEDULED",
            "operator-status-reconcile"
        ) {
            operatorStatusReconcileService.reconcile()
        }
    }

    @Scheduled(cron = "\${talent-introduction.scheduling.reachability-sync-cron:-}")
    fun scheduleReachabilitySync() {
        // 走 runAndRecordWithResult 使 BulkSyncResult 落入 task_execution.result_summary，可在任务面板查看
        taskExecutionService.runAndRecordWithResult(
            "EXPERT_REACHABILITY_SYNC",
            "SCHEDULED",
            "reachability-sync"
        ) {
            expertReachabilitySyncService.syncAll()
        }
    }

    private fun dispatchMode(): String =
        if (mailQueuePublisherProvider.getIfAvailable() == null) "SYNC" else "QUEUE"
}
