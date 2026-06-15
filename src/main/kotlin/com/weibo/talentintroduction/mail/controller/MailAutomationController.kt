package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.service.InitialOutreachBatchResult
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.campaign.service.PendingOutreachSummary
import com.weibo.talentintroduction.mail.service.AutoMailReplyBatchResult
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyResult
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyService
import com.weibo.talentintroduction.mail.queue.MailQueuePublisher
import com.weibo.talentintroduction.mail.queue.QueuePublishResult
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailAutomationController(
    private val initialOutreachService: InitialOutreachService,
    private val autoMailReplyService: AutoMailReplyService,
    private val batchAutoMailReplyService: BatchAutoMailReplyService,
    private val mailQueuePublisherProvider: ObjectProvider<MailQueuePublisher>,
    private val taskExecutionService: TaskExecutionService,
    private val manualInitialOutreachService: ManualInitialOutreachService,
    private val progressStore: TaskProgressStore,
    @org.springframework.beans.factory.annotation.Qualifier("manualOutreachExecutor")
    private val manualOutreachExecutor: java.util.concurrent.Executor
) {
    @PostMapping("/initial-outreach")
    fun sendInitialOutreach(
        @RequestParam campaignId: Long,
        @RequestParam(defaultValue = "10") size: Int
    ): InitialOutreachBatchResult =
        initialOutreachService.sendInitialBatch(campaignId, size)

    @PostMapping("/auto-reply")
    fun receiveAndAutoReply(
        @RequestParam accountCode: String,
        @RequestParam(defaultValue = "20") maxMessages: Int
    ): AutoMailReplyBatchResult =
        autoMailReplyService.receiveAndAutoReply(accountCode, maxMessages)

    @PostMapping("/auto-reply/all")
    fun receiveAndAutoReplyAll(
        @RequestParam(defaultValue = "20") maxMessagesPerAccount: Int
    ): BatchAutoMailReplyResult =
        batchAutoMailReplyService.receiveAndAutoReplyAll(maxMessagesPerAccount)

    @PostMapping("/initial-outreach/async")
    fun enqueueInitialOutreach(
        @RequestParam campaignId: Long,
        @RequestParam(defaultValue = "10") size: Int
    ): QueuePublishResult =
        queuePublisher().publishInitialOutreach(campaignId, size)

    @PostMapping("/auto-reply/async")
    fun enqueueAutoReply(
        @RequestParam accountCode: String,
        @RequestParam(defaultValue = "20") maxMessages: Int
    ): QueuePublishResult =
        queuePublisher().publishAutoReply(accountCode, maxMessages)

    @PostMapping("/auto-reply/all/async")
    fun enqueueAutoReplyAll(
        @RequestParam(defaultValue = "20") maxMessagesPerAccount: Int
    ): QueuePublishResult =
        queuePublisher().publishAutoReplyAll(maxMessagesPerAccount)

    @PostMapping("/auto-reply/check-replies")
    fun checkReplies(
        @RequestBody request: CheckRepliesRequest
    ): ResponseEntity<Map<String, Any>> {
        val maxMessages = request.maxMessagesPerAccount ?: 20
        require(maxMessages in 1..100) {
            "maxMessagesPerAccount must be between 1 and 100"
        }
        val contactIds = request.contactIds
            ?.distinct()
            .orEmpty()

        if (contactIds.isNotEmpty()) {
            require(contactIds.all { it > 0 }) {
                "contactIds must contain positive ids"
            }
            require(contactIds.size <= 500) {
                "contactIds must not contain more than 500 ids"
            }
        }

        val normalizedRequest = CheckRepliesRequest(
            contactIds = contactIds.takeIf { it.isNotEmpty() },
            maxMessagesPerAccount = maxMessages
        )

        val initialProgress = TaskProgress(
            taskType = "CHECK_REPLIES",
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "正在初始化检查任务..."
        )

        val (started, pendingToken) = progressStore.tryStartWithToken("CHECK_REPLIES", initialProgress)
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "检查回复任务正在执行中"))
        }

        try {
            manualOutreachExecutor.execute {
                var executionId: Long? = null
                try {
                    taskExecutionService.runAndRecordWithResult(
                        "CHECK_REPLIES",
                        if (contactIds.isEmpty()) "MANUAL_ALL" else "MANUAL_SELECTIVE",
                        normalizedRequest,
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId("CHECK_REPLIES", pendingToken, id)
                        }
                    ) {
                        var runningFetched = 0
                        var runningReplied = 0
                        var runningManualReview = 0
                        var runningSuccess = 0
                        var runningFailed = 0

                        val onProgress: (com.weibo.talentintroduction.mail.service.AccountAutoMailReplyResult, Int, Int) -> Unit = { accountResult, processed, total ->
                            if (accountResult.status == "SUCCESS") {
                                runningSuccess++
                                runningFetched += accountResult.fetched
                                runningReplied += accountResult.replied
                                runningManualReview += accountResult.manualReview
                            } else {
                                runningFailed++
                            }
                            val currentExecId = executionId
                            val token = currentExecId ?: pendingToken
                            progressStore.update("CHECK_REPLIES", TaskProgress(
                                taskType = "CHECK_REPLIES",
                                status = "RUNNING",
                                batchNumber = processed,
                                processedCount = processed.toLong(),
                                totalCount = total.toLong(),
                                message = "正在检查邮箱: ${accountResult.accountCode} (${processed}/${total})",
                                details = mapOf(
                                    "totalAccountsToPoll" to total,
                                    "accountsPolled" to processed,
                                    "successAccountCount" to runningSuccess,
                                    "failedAccountCount" to runningFailed,
                                    "fetched" to runningFetched,
                                    "replied" to runningReplied,
                                    "manualReview" to runningManualReview
                                ),
                                executionId = currentExecId
                            ), token)
                            Unit
                        }

                        val isCancelled: () -> Boolean = {
                            val currentExecId = executionId
                            if (currentExecId != null) {
                                progressStore.isCancelled("CHECK_REPLIES", currentExecId)
                            } else {
                                progressStore.isCancelled("CHECK_REPLIES", pendingToken)
                            }
                        }

                        val result = if (contactIds.isEmpty()) {
                            batchAutoMailReplyService.receiveAndAutoReplyAll(maxMessages, onProgress, isCancelled)
                        } else {
                            batchAutoMailReplyService.receiveAndAutoReplyForContacts(
                                contactIds = contactIds,
                                maxMessagesPerAccount = maxMessages,
                                onProgress = onProgress,
                                isCancelled = isCancelled
                            )
                        }

                        val finalStatus = result.taskFinalStatus ?: "COMPLETED"
                        val finalMessage = when (result.taskFinalStatus) {
                            "CANCELLED" -> "检查回复已被取消：共检查 ${result.accountsPolled}/${result.totalAccountsToPoll} 个邮箱账号，获取 ${result.fetched} 封邮件，自动回复 ${result.replied} 封，转人工 ${result.manualReview} 封"
                            "FAILED" -> "检查回复失败：共检查 ${result.accountsPolled}/${result.totalAccountsToPoll} 个邮箱账号均失败，错误信息请查看日志"
                            "PARTIAL_SUCCESS" -> "检查回复部分成功：共检查 ${result.accountsPolled}/${result.totalAccountsToPoll} 个邮箱账号，成功 ${result.successAccountCount} 个，失败 ${result.failedAccountCount} 个"
                            else -> "检查回复完成：共检查 ${result.accountsPolled}/${result.totalAccountsToPoll} 个邮箱账号，获取 ${result.fetched} 封邮件，自动回复 ${result.replied} 封，转人工 ${result.manualReview} 封"
                        }

                        // update final progress
                        progressStore.update("CHECK_REPLIES", TaskProgress(
                            taskType = "CHECK_REPLIES",
                            status = finalStatus,
                            batchNumber = result.accountsPolled,
                            processedCount = result.accountsPolled.toLong(),
                            totalCount = result.totalAccountsToPoll.toLong(),
                            message = finalMessage,
                            details = mapOf(
                                "totalAccountsToPoll" to result.totalAccountsToPoll,
                                "accountsPolled" to result.accountsPolled,
                                "successAccountCount" to result.successAccountCount,
                                "failedAccountCount" to result.failedAccountCount,
                                "fetched" to result.fetched,
                                "replied" to result.replied,
                                "manualReview" to result.manualReview
                            ),
                            executionId = executionId!!
                        ), executionId!!)

                        result
                    }
                } catch (ex: Exception) {
                    progressStore.update("CHECK_REPLIES", TaskProgress(
                        taskType = "CHECK_REPLIES",
                        status = "FAILED",
                        batchNumber = 0, processedCount = 0, totalCount = 0,
                        message = "检查回复失败: ${ex.message}",
                        executionId = executionId
                    ), executionId)
                } finally {
                    val execId = executionId
                    if (execId != null) {
                        progressStore.clearExecutionContext("CHECK_REPLIES", execId)
                    } else {
                        progressStore.clearExecutionContext("CHECK_REPLIES", pendingToken)
                    }
                }
            }
        } catch (reEx: java.util.concurrent.RejectedExecutionException) {
            progressStore.update("CHECK_REPLIES", TaskProgress(
                taskType = "CHECK_REPLIES", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = "启动失败: ${reEx.message}"
            ), pendingToken)
            progressStore.clearExecutionContext("CHECK_REPLIES", pendingToken)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "启动失败: 线程池满或已关闭"))
        }

        return ResponseEntity.accepted().body(mapOf("message" to "已启动"))
    }

    @GetMapping("/manual-outreach/pending-count")
    fun getManualOutreachPendingCount(): PendingOutreachSummary =
        manualInitialOutreachService.countPending()

    @PostMapping("/manual-outreach/start")
    fun startManualOutreach(): ResponseEntity<Map<String, String>> {
        val initialProgress = TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH",
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "正在初始化发送队列...",
            details = mapOf("pending" to 0, "sent" to 0, "failed" to 0)
        )
        val (started, pendingToken) = progressStore.tryStartWithToken("MANUAL_INITIAL_OUTREACH", initialProgress)
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中"))
        }

        try {
            manualOutreachExecutor.execute {
                var executionId: Long? = null
                try {
                    taskExecutionService.runAndRecordWithResult(
                        "MANUAL_INITIAL_OUTREACH", "MANUAL", "manual-outreach",
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId("MANUAL_INITIAL_OUTREACH", pendingToken, id)
                        }
                    ) {
                        manualInitialOutreachService.runBulkOutreach(executionId!!)
                    }
                } catch (ex: Exception) {
                    progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
                        taskType = "MANUAL_INITIAL_OUTREACH", status = "FAILED",
                        batchNumber = 0, processedCount = 0, totalCount = 0,
                        message = ex.message ?: "初始化失败"
                    ), executionId)
                } finally {
                    val execId = executionId
                    if (execId != null) {
                        progressStore.clearExecutionContext("MANUAL_INITIAL_OUTREACH", execId)
                    } else {
                        progressStore.clearExecutionContext("MANUAL_INITIAL_OUTREACH", pendingToken)
                    }
                }
            }
        } catch (reEx: java.util.concurrent.RejectedExecutionException) {
            progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
                taskType = "MANUAL_INITIAL_OUTREACH", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = "启动失败: ${reEx.message}"
            ), pendingToken)
            progressStore.clearExecutionContext("MANUAL_INITIAL_OUTREACH", pendingToken)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "启动失败: 线程池满或已关闭"))
        }

        return ResponseEntity.accepted().body(mapOf("message" to "已启动"))
    }

    private fun queuePublisher(): MailQueuePublisher =
        mailQueuePublisherProvider.getIfAvailable()
            ?: error("Mail queue is not enabled. Set MAIL_QUEUE_ENABLED=true and configure RabbitMQ.")
}

data class CheckRepliesRequest(
    val contactIds: List<Long>? = null,
    val maxMessagesPerAccount: Int? = 20
)
