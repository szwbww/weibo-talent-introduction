-- Phase 2a: compose_order for multi-rule reply aggregation ordering.

ALTER TABLE qa_category
    ADD COLUMN compose_order INT NOT NULL DEFAULT 100 COMMENT '聚合回复时主题章节排序，越小越靠前';

UPDATE qa_category SET compose_order = 10 WHERE category_code = 'PROGRAM_AND_ELIGIBILITY';
UPDATE qa_category SET compose_order = 20 WHERE category_code = 'ROLE_AND_WORKSTYLE';
UPDATE qa_category SET compose_order = 30 WHERE category_code = 'FUNDING_AND_TIMELINE';
UPDATE qa_category SET compose_order = 40 WHERE category_code = 'PROCESS_ACTIONS';
UPDATE qa_category SET compose_order = 50 WHERE category_code = 'TRUST_AND_COMPLIANCE';
UPDATE qa_category SET compose_order = 60 WHERE category_code = 'COMMUNICATION_AND_OTHER';
