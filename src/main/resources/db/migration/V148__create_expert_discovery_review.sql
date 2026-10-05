-- ============================================================================
-- V148 expert_discovery_review：深度发现审核的当前结论与快照历史（fast-p 02）
--
-- I-1：两张表各司其职，不再另建任务表 / 事件总线 / ES 字段。
--   expert_discovery_admission —— 每个真实 docId 一行「当前结论/版本」，支持事务行锁
--     （SELECT ... FOR UPDATE）与版本 CAS（revision）。没有第二个 eligible 布尔：
--     是否准入由 decision 派生。identity_hash 绑定真实 docId + 规范化邮箱 +
--     givenNames/familyNames（I-2）：同身份的人工批准在研究方向/机构/国家/指标/分类
--     变化后仍然有效，身份改变则原审核不适用。
--   expert_discovery_review_item —— 每次 prepare 快照的每个目标一行，保存决策、应用
--     状态与历史。UNIQUE(batch_key, expert_doc_id) 保证同一批同一专家只有一行；
--     (expert_doc_id, id) 支撑按专家的历史查询。
-- 任务头复用 task_execution：execution_id 不设外键，避免任务清理删除审核历史。
-- ============================================================================

CREATE TABLE expert_discovery_admission (
    expert_doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    identity_hash CHAR(64) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    revision BIGINT NOT NULL,
    decision_item_id BIGINT NULL,
    policy_version VARCHAR(32) NULL,
    checked_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_doc_id),
    INDEX idx_discovery_admission_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expert_discovery_review_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_key VARCHAR(64) NOT NULL,
    expert_doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    source_level VARCHAR(16) NOT NULL,
    identity_hash CHAR(64) NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    expected_revision BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    reason_snapshot_json TEXT NULL,
    actor VARCHAR(100) NOT NULL,
    note VARCHAR(1000) NULL,
    previous_item_id BIGINT NULL,
    execution_id BIGINT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    applied_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_discovery_review_batch_doc UNIQUE (batch_key, expert_doc_id),
    INDEX idx_discovery_review_doc (expert_doc_id, id),
    INDEX idx_discovery_review_batch_state (batch_key, state, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
