package com.weibo.talentintroduction.task.domain

/**
 * taskType 语义的唯一声明源（主计划 M-3 / 子计划 I1-1 落地）。
 *
 * 中文名、分组、计数列语义标签、summary 提取规则 key、drilldown 声明全部只在这里声明一次；
 * [com.weibo.talentintroduction.task.controller.TaskProgressController.allowedTaskTypes]
 * 必须从 [entries] 派生，前端不得硬编码任何 taskType 中文名或选项列表。
 *
 * ## metricLabel 判定口径（2026-08-16 复盘修正，见 b2 计划 T1-1）
 *
 * 列表列渲染的是**已持久化的** `success_count` / `failure_count`（B1 投影只取这两列，主计划 M-1
 * 禁止列表读 `result_summary`）。这两个存量值由写入侧 `TaskExecutionService.TaskResultSummary.from()`
 * 的反射（成功侧 firstInt "sent","replied","accepted","fetched","dispatched"；失败侧
 * firstInt "manualReview","skipped","failureCount"）或 `TaskExecutionSummaryProvider` 决定，
 * 与「这个任务概念上处理了多少个」不是一回事。因此 `metricLabel` 只在存量值确实可信的类型上为非 null；
 * null 表示前端渲染「— 无统计」（I1-2），真实业务指标由 [com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor]
 * 在展开明细时给出（I1-3）。**不要为了让每一行都有数字而编造语义标签。**
 */
data class TaskTypeMeta(
    val code: String,
    val label: String,            // 中文名
    val group: String,            // SCHEDULED / MANUAL / QUEUE（主要触发方式）
    val metricLabel: String?,     // 计数列语义，如 "已发送/失败"；null → 前端渲染「— 无统计」
    val summaryRule: String?,     // extractor 的提取规则 key，null → 无结构化提取
    val hasProgressUi: Boolean,   // 派生 TaskProgressController.allowedTaskTypes
    val drilldown: Drilldown?     // P2b（B4）使用；null → 前端渲染禁用态「该任务无个体明细」（M-4）
)

/**
 * P2b（B4）使用：MAIL_BY_EXECUTION（按执行过滤收发件箱）/ EXPERT_BY_POLL_DETAIL（专家明细跳转）。
 * B4 起 MAIL 类（MANUAL_INITIAL_OUTREACH / INITIAL_OUTREACH）与专家类（AUTO_REPLY_ALL / CHECK_REPLIES）
 * 声明非 null，其余 12 项（含全部 ES 类）保持 null（M-4）。
 */
enum class Drilldown { MAIL_BY_EXECUTION, EXPERT_BY_POLL_DETAIL }

object TaskTypeCatalog {

    /**
     * 17 种类型，按「现状审计」grep 回执的 taskType 全集逐条声明。
     *
     * metricLabel 证据（写入侧源码位置）：
     * - MANUAL_INITIAL_OUTREACH "已发送/失败"：ManualOutreachResult 实现 TaskExecutionSummaryProvider
     *   （taskSuccessCount=sent / taskFailureCount=failed）。
     * - AUTO_REPLY_ACCOUNT "已回复/转人工"：AutoMailReplyBatchResult 反射成功侧首命中 replied，失败侧命中 manualReview。
     * - AUTO_REPLY_ALL_DISPATCH "派发账号数/—"：QueueFanOutResult(dispatched)，成功侧命中 dispatched，失败侧无字段恒 0。
     * - AUTO_REPLY_ALL "轮询账号/失败账号"：BatchAutoMailReplyResult 实现 provider（successAccountCount / failedAccountCount）。
     * - OPERATOR_STATUS_RECONCILE "一致/异常"：ReconcileReport 实现 provider（consistent / dbVsExpected+esVsDb）。
     * - 其余均为 null：存量值由反射猜测（如 EXPERT_ENRICHMENT 的 enriched/failed 全不命中 → 恒 0/0），
     *   显式声明「无统计」比继续显示反射猜出来的 0/0 诚实。
     */
    val entries: Map<String, TaskTypeMeta> = listOf(
        TaskTypeMeta(
            code = "AI_QA_EXTRACTION", label = "AI QA 提炼", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "AUTO_REPLY_ACCOUNT", label = "单账号轮询自动回复", group = "QUEUE",
            metricLabel = "已回复/转人工", summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "AUTO_REPLY_ALL", label = "全量账号自动收信回复", group = "SCHEDULED",
            metricLabel = "轮询账号/失败账号", summaryRule = null, hasProgressUi = false,
            drilldown = Drilldown.EXPERT_BY_POLL_DETAIL
        ),
        TaskTypeMeta(
            code = "AUTO_REPLY_ALL_DISPATCH", label = "批量分发与调度", group = "QUEUE",
            metricLabel = "派发账号数/—", summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "BOUNCE_COLLECTION", label = "退信收集", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "CANDIDATE_OPERATOR_STATUS_SYNC", label = "候选人状态同步", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "CHECK_REPLIES", label = "检查回复", group = "MANUAL",
            metricLabel = null, summaryRule = "CHECK_REPLIES", hasProgressUi = true,
            drilldown = Drilldown.EXPERT_BY_POLL_DETAIL
        ),
        TaskTypeMeta(
            code = "DAILY_COUNT_RESET", label = "每日计数重置", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "EXPERT_DISCOVERY", label = "深度发现（外部数据源）", group = "SCHEDULED",
            metricLabel = null, summaryRule = "EXPERT_DISCOVERY", hasProgressUi = true, drilldown = null
        ),
        TaskTypeMeta(
            code = "EXPERT_ENRICHMENT", label = "学术数据补全", group = "MANUAL",
            metricLabel = null, summaryRule = "EXPERT_ENRICHMENT", hasProgressUi = true, drilldown = null
        ),
        TaskTypeMeta(
            code = "EXPERT_REVALIDATION", label = "专家重新验证", group = "MANUAL",
            metricLabel = null, summaryRule = "EXPERT_REVALIDATION", hasProgressUi = true, drilldown = null
        ),
        TaskTypeMeta(
            code = "INITIAL_OUTREACH", label = "定时首发邮件", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false,
            drilldown = Drilldown.MAIL_BY_EXECUTION
        ),
        TaskTypeMeta(
            code = "MANUAL_INITIAL_OUTREACH", label = "批量首发邮件", group = "MANUAL",
            metricLabel = "已发送/失败", summaryRule = "MANUAL_INITIAL_OUTREACH", hasProgressUi = true,
            drilldown = Drilldown.MAIL_BY_EXECUTION
        ),
        TaskTypeMeta(
            code = "OPERATOR_STATUS_RECONCILE", label = "运营状态对账", group = "SCHEDULED",
            metricLabel = "一致/异常", summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "POSTMASTER_REPUTATION", label = "Postmaster 信誉拉取", group = "SCHEDULED",
            metricLabel = null, summaryRule = null, hasProgressUi = false, drilldown = null
        ),
        TaskTypeMeta(
            code = "RAW_PROMOTION_SCAN", label = "RAW 层晋升扫描", group = "MANUAL",
            metricLabel = null, summaryRule = "RAW_PROMOTION_SCAN", hasProgressUi = true, drilldown = null
        ),
        TaskTypeMeta(
            code = "TASK_AUDIT_RETENTION", label = "任务审计清理", group = "SCHEDULED",
            metricLabel = "删除行数/失败表数", summaryRule = "TASK_AUDIT_RETENTION",
            hasProgressUi = false, drilldown = null
        )
    ).associateBy { it.code }

    fun byCode(code: String): TaskTypeMeta? = entries[code]
}
