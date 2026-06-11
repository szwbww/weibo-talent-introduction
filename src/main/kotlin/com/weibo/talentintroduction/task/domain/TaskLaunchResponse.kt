package com.weibo.talentintroduction.task.domain

data class TaskLaunchResponse<T>(
    val executionId: Long,
    val result: T
)
