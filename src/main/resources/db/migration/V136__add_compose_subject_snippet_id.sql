ALTER TABLE mail_compose_template
    ADD COLUMN subject_snippet_id BIGINT NULL COMMENT '邮件主题引用的回复片段 ID；NULL 为自定义主题';
