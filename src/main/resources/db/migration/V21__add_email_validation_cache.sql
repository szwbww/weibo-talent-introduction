CREATE TABLE email_validation_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL COMMENT '邮箱地址（小写）',
    domain VARCHAR(255) NOT NULL COMMENT '邮箱域名',
    format_valid BOOLEAN NOT NULL DEFAULT FALSE COMMENT '格式校验通过',
    disposable BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否一次性邮箱',
    mx_valid BOOLEAN DEFAULT NULL COMMENT 'MX 记录校验结果（NULL=未校验）',
    verified_level INT NOT NULL DEFAULT 0 COMMENT '通过的最高验证层级 0-5',
    reject_reason VARCHAR(128) DEFAULT NULL COMMENT '拒绝原因（最先失败的层级）',
    verified_at DATETIME NOT NULL COMMENT '验证时间',
    expires_at DATETIME NOT NULL COMMENT '缓存过期时间',
    UNIQUE INDEX uk_email (email),
    INDEX idx_domain (domain),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
