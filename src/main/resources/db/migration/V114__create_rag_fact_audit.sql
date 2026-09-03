-- ============================================================================
-- V114 rag_fact_audit（plan 04: 04-rag-knowledge-base-page.md）
--
-- 事实改动审计表：运营在 RAG 知识库页每次保存/启停，按字段逐条落一行
-- （I-21），与 03b 的 mail_record_rag_fact.corpus_fingerprint 闭环 —— 单看存证
-- 无法还原「这封信发出时那条事实的原文」，还原路径是「存证行的
-- corpus_fingerprint → 在本表按 fingerprint_after 定位那次变更 → 沿
-- old_value 链回放」；因此 fingerprint_before / fingerprint_after 两列是必需
-- 的闭环键，不是可选诊断字段。
--
-- 关键不变量：
--   I-21  fact_code/field/old_value/new_value/fingerprint_before/fingerprint_after/
--         operator/created_at；answer 的 old/new 存全文（MEDIUMTEXT）。
--   I-20  audit 行与 fact 写入在同一 republish 事务内（RagFactAdminService），
--         事务回滚时审计行一并回滚。
--   G-4   不复用 QaRuleAuditService / qa_rule 审计表，rag_* 与 qa_* 零运行时耦合。
--   G-1   fact_code 是唯一业务标识；自增 id 绝不进入提示词/响应。
--
-- 不声明外键到 rag_fact：事实可能被改写，审计应保留历史而非随之失效
-- （与 V113 mail_record_rag_fact 不声明外键的既有基线一致）。
-- ============================================================================
CREATE TABLE rag_fact_audit (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    fact_code         VARCHAR(32) NOT NULL COMMENT 'RAG 事实业务键（G-1），如 KB-FUND-033',
    field             VARCHAR(32) NOT NULL COMMENT 'answer / title / render_mode / ... 被改的列名',
    old_value         MEDIUMTEXT  NULL COMMENT '改动前值；answer 为全文（I-21）',
    new_value         MEDIUMTEXT  NULL COMMENT '改动后值；answer 为全文（I-21）',
    fingerprint_before VARCHAR(64) NOT NULL COMMENT 'republish 前快照指纹；与 03b 存证 corpus_fingerprint 对接（I-21）',
    fingerprint_after VARCHAR(64)  NOT NULL COMMENT 'republish 算出的新指纹，定位该次变更（I-21 闭环）',
    operator          VARCHAR(64)  NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rag_fact_audit_code (fact_code, id),
    KEY idx_rag_fact_audit_fp (fingerprint_after)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 事实改动审计：与 mail_record_rag_fact.corpus_fingerprint 闭环还原发信时的原文版本（I-21/D-16）';
