package com.weibo.talentintroduction.expert.domain

/**
 * 专家可达性四档。枚举不含未知档：未知档由 classify() 返回 null 表示（I-2-3），
 * 写入侧通过「字段缺失」表达，避免未知字符串被写进 ES。
 */
enum class ExpertReachability(val esValue: String) {
    BLOCKED_UNSUBSCRIBED("BLOCKED_UNSUBSCRIBED"),
    BLOCKED_BOUNCED("BLOCKED_BOUNCED"),
    HIGH("HIGH"),
    LOW("LOW")
}
