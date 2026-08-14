ALTER TABLE reply_snippet
    ADD COLUMN name VARCHAR(120) NULL COMMENT '运营维护的片段显示名，留空时按内容首行摘要显示';
