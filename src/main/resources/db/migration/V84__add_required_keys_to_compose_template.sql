-- V84: add required_keys to mail_compose_template for the send-side personalization gate.
--
-- NULL or an empty array disables the gate (I-4): the template keeps sending with
-- whatever fallback values are configured. No backfill: existing rows stay NULL.
ALTER TABLE mail_compose_template
    ADD COLUMN required_keys VARCHAR(500) NULL
    COMMENT '必填个性化变量 key 的 JSON 数组，NULL 或空数组表示不启用门禁';
