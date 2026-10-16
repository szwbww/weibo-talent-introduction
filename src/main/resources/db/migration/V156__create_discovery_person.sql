-- ============================================================================
-- V156 discovery_person / discovery_person_member / discovery_person_author_key
--      （fast-p 04：新发现人物主档与邮箱成员）
--
-- I-1：personId 是不带邮箱语义的稳定 UUID；每个原 docId 只能隶属一个人物。
--   主档只记录代表档案引用（primary_doc_id），不复制准入/邮箱验证结论；主邮箱
--   （member.normalized_email）只是展示默认值，**不是发送许可**，因此只建索引、
--   绝不做全局唯一约束（不以邮箱唯一代替人证据）。
-- I-2：只有「当前姓名/邮箱绑定成立」的凭证内 ORCID/OpenAlex 键才能占用
--   author_key（PK，带 ORCID:/OPENALEX: 类型前缀）——一个可信作者键在任何时刻
--   只指向一个人物；唯一约束是并发下「只产生一个主档」的最终防线。
-- I-3：member.identity_fingerprint 是登记当时证据（docId+规范邮箱+规范全名+作者键）
--   的 SHA-256；evidence_json 留存合并依据与规则版本，不替代原文与实时凭证。
--   revision 仅人物关系/代表引用变化时递增（登记与代表都不复制业务真值）。
--
-- 全部为新增独立存储：不改已应用迁移、不给既有共享表/ES 增字段。外键只指向本计划
-- 的三张新表（成员/作者键 → 主档），删除主档即删除其关系（无跨业务表级联）。
-- ============================================================================

CREATE TABLE discovery_person (
    person_id CHAR(36) NOT NULL,
    primary_doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE discovery_person_member (
    expert_doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    person_id CHAR(36) NOT NULL,
    normalized_email VARCHAR(320) NULL,
    identity_fingerprint CHAR(64) NOT NULL,
    evidence_json TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_doc_id),
    INDEX idx_discovery_person_member_person (person_id),
    INDEX idx_discovery_person_member_email (normalized_email),
    CONSTRAINT fk_discovery_person_member_person FOREIGN KEY (person_id)
        REFERENCES discovery_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE discovery_person_author_key (
    author_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
    person_id CHAR(36) NOT NULL,
    PRIMARY KEY (author_key),
    INDEX idx_discovery_person_author_key_person (person_id),
    CONSTRAINT fk_discovery_person_author_key_person FOREIGN KEY (person_id)
        REFERENCES discovery_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
