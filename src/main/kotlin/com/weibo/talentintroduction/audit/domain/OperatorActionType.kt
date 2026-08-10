package com.weibo.talentintroduction.audit.domain

enum class OperatorActionType(val summary: String) {
    CHANGE_OPERATOR_STATUS("变更专家状态"),
    CHANGE_INDEX_LEVEL("变更专家层级"),
    SWITCH_REPLY_MODE("切换自动/人工回复"),
    BIND_INBOUND_MAIL("绑定待处理邮件"),
    SEND_QA_REPLY("发送 QA 邮件"),
    SEND_MANUAL_RICH_REPLY("人工回复邮件"),
    SEND_MANUAL_COMPOSED_REPLY("发送组装 QA 回复"),
    MARK_INBOUND_RESOLVED("标记待处理邮件已处理"),
    AI_REPLY_DRAFT_READY("AI 草稿生成-完整就绪"),
    AI_REPLY_DRAFT_NEEDS_REVIEW("AI 草稿生成-需审核"),
    AI_REPLY_DRAFT_BLOCKED("AI 草稿生成-被阻止"),
    AI_REPLY_SEND_BLOCKED("AI 回复发送被阻止"),
    AI_REPLY_REVIEW_CONFIRMED("AI 回复人工审核确认"),
    AI_TRAINING_REPLY_EVALUATED("AI 训练回复评估"),
    CHANGE_SENDER_ACCOUNT("变更发送账号"),
    MIGRATE_SENDER_ACCOUNT("迁移发送账号"),
    CLEAR_SENDER_CHANGE_MARK("清除发送账号变更标记")
}
