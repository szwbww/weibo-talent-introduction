-- V89: 退订 token 的不透明随机 id 存储。
-- 旧格式 token（base64url(email).base64url(hmac)）把收件人邮箱明文编码进 URL，
-- 本表把映射搬到服务端，URL 只留随机串（Plan 07 I-1）。
-- uk_email 保证一个邮箱最多一条记录，sign() 复用（I-2）。
-- uk_token 保证校验查询走唯一索引。
-- 不回填历史数据：历史邮件里的旧 token 由 verifyLegacy() 兜底（I-7）。

CREATE TABLE unsubscribe_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL COMMENT '归一化邮箱(小写trim)',
    token VARCHAR(64) NOT NULL COMMENT '不透明随机 id：SecureRandom 32 字节的 base64url 无填充编码，43 字符',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
