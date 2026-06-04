package com.weibo.talentintroduction.audit.domain

enum class OperatorActionType(val summary: String) {
    CHANGE_OPERATOR_STATUS("变更专家状态"),
    CHANGE_INDEX_LEVEL("变更专家层级"),
    SWITCH_REPLY_MODE("切换自动/人工回复"),
    BIND_INBOUND_MAIL("绑定待处理邮件"),
    SEND_QA_REPLY("发送 QA 邮件"),
    SEND_MANUAL_RICH_REPLY("人工回复邮件"),
    MARK_INBOUND_RESOLVED("标记待处理邮件已处理")
}