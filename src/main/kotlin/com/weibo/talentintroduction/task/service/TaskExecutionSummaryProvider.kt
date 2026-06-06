package com.weibo.talentintroduction.task.service

interface TaskExecutionSummaryProvider {
    val taskSuccessCount: Int
    val taskFailureCount: Int
    val taskFinalStatus: String?
}
