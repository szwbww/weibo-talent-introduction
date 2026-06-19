package com.weibo.talentintroduction.mail.domain

enum class SmtpErrorCategory {
    /** 发送成功 */
    SUCCESS,
    /** 暂时性错误（4xx）：限流、邮箱满、服务器暂不可用。建议暂停账号稍后重试。 */
    TRANSIENT,
    /** 永久性错误（5xx）：收件人不存在、域名无效、被拒。标记邮箱无效，不再重试。 */
    PERMANENT,
    /** 网络/认证等基础设施错误。与收件人无关，暂停账号。 */
    INFRASTRUCTURE
}
