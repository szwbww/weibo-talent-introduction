-- 1. expert_contact 增加 ES 层级与人工待办标记
ALTER TABLE expert_contact
    ADD COLUMN current_index_level VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE'
        COMMENT 'RAW / CANDIDATE / APPLICATION';

ALTER TABLE expert_contact
    ADD COLUMN needs_manual_attention BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '系统判定该 contact 需要人工介入，红条/badge 显隐依据';

-- 历史 application_indexed=true 的行回填为 APPLICATION
UPDATE expert_contact SET current_index_level = 'APPLICATION'
 WHERE application_indexed = TRUE;

-- CLOSED 状态清理：迁到 MANUAL_HANDOFF + 关掉自动回复 + 标红 + 转人工要求
UPDATE expert_contact
   SET current_status = 'MANUAL_HANDOFF',
       auto_reply_enabled = FALSE,
       needs_manual_attention = TRUE,
       manual_handoff_required = TRUE
 WHERE current_status = 'CLOSED';

-- 插入对应的 PENDING 手动派单工单，避免状态不一致
INSERT INTO manual_handoff (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
SELECT ec.id, 'CLOSED_MIGRATION', 'PENDING', NULL, 'Migrated from CLOSED status', NOW(), NOW()
  FROM expert_contact ec
 WHERE ec.current_status = 'MANUAL_HANDOFF'
   AND ec.manual_handoff_required = TRUE
   AND NOT EXISTS (
       SELECT 1
         FROM manual_handoff mh
        WHERE mh.expert_contact_id = ec.id
          AND mh.handoff_status IN ('PENDING', 'ASSIGNED')
   );


-- 2. inbound_mail_processing 增加 reason_type 列，给前端按原因过滤用
ALTER TABLE inbound_mail_processing
    ADD COLUMN reason_type VARCHAR(32) NULL
        COMMENT 'UNMATCHED_CONTACT / QA_NO_MATCH / NOT_INTERESTED / UNCLEAR_INTENT / MANUAL_RESOLVED 等',
    ADD INDEX idx_imp_reason_type_status (reason_type, process_status);

-- 历史 MANUAL_REVIEW 行 backfill
UPDATE inbound_mail_processing
   SET reason_type = CASE
       WHEN process_status <> 'MANUAL_REVIEW' THEN reason_type
       WHEN expert_contact_id IS NULL THEN 'UNMATCHED_CONTACT'
       WHEN process_reason LIKE '%NOT_INTERESTED%' THEN 'NOT_INTERESTED'
       WHEN process_reason LIKE 'QA%' THEN 'QA_NO_MATCH'
       ELSE 'UNCLEAR_INTENT'
   END;

-- 3. qa_rule 增加 display_name
ALTER TABLE qa_rule
    ADD COLUMN display_name VARCHAR(120) NULL COMMENT '运营在 QA 管理页维护的中文显示名';
