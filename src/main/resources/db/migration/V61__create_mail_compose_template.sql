CREATE TABLE mail_compose_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    subject VARCHAR(255) NOT NULL COMMENT '邮件主题',
    description VARCHAR(500) NULL COMMENT '模板用途描述',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE mail_compose_template_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    block_order INT NOT NULL COMMENT '块排序（ASC）',
    block_type VARCHAR(30) NOT NULL COMMENT 'QA_RULE | REPLY_SNIPPET | CUSTOM_TEXT',
    ref_id BIGINT NULL COMMENT 'qa_rule.id 或 reply_snippet.id，CUSTOM_TEXT 时为 NULL',
    custom_text TEXT NULL COMMENT 'CUSTOM_TEXT 时存正文',
    FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
);
