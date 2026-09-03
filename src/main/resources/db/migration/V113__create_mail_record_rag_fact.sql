-- ============================================================================
-- V113 mail_record_rag_fact（plan 03b: 03b-rag-send-bridge.md）
--
-- RAG 回信发出后的事实存证表：与 mail_record_qa_rule 并列、互不干扰 ——
-- 一封信只会写其中一张（I-39：三条发送路径互斥，RAG 路径绝不写 mail_record_qa_rule）。
--
-- 关键不变量：
--   I-39  存证与旧表互斥：本表行存在 ⇔ 该信走 RAG 发送路径（fact_code 证据）。
--   I-41  corpus_fingerprint 记录发出时的语料版本，用于事后复盘「当时发的原文是哪一版」
--         （与 04 的 rag_fact_audit.fingerprint_before/after 闭环，D-16）。
--   I-42  ordinal 保存请求中 ragFactCodes 的原始顺序，不排序、不去重；
--         UNIQUE(mail_record_id, ordinal) 对齐 mail_record_qa_rule.ordinal 语义
--         （MailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc）。
--   G-1   fact_code 是唯一业务标识；自增 id 只作分页/外键，绝不进入任何响应。
--
-- 不声明外键到 rag_fact / mail_record：事实可能被停用或改写，存证不应随之失效
-- （与 mail_record_qa_rule 不声明 ON DELETE CASCADE 的既有基线一致）；RAG 回信
-- 的 mail_record 生命周期独立于本表。
-- ============================================================================
CREATE TABLE mail_record_rag_fact (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    mail_record_id    BIGINT       NOT NULL COMMENT '已发出的 RAG 回信 mail_record.id',
    fact_code         VARCHAR(32)  NOT NULL COMMENT 'RAG 事实业务键（G-1），如 KB-FUND-033',
    ordinal           INT          NOT NULL COMMENT '请求中 ragFactCodes 的原始下标，不排序不去重（I-42）',
    corpus_fingerprint VARCHAR(64) NOT NULL COMMENT '发出时的语料指纹（I-41），用于复盘原文版本',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_record_rag_fact (mail_record_id, ordinal),
    KEY idx_mail_record_rag_fact_record (mail_record_id),
    KEY idx_mail_record_rag_fact_code (fact_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 回信的事实存证：与 mail_record_qa_rule 并列，一封信只会写其中一张（I-39）';
