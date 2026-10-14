-- ============================================================================
-- V157 discovery_person_outreach（fast-p 09：人物级首发原子保护）
--
-- I-1：所有介绍邮件入口（旧定时 / 新批量含重试 / 详情手动模板发送）在「实际
--   mailType=INTRODUCTION 且目标为 discovered」时使用同一原子门禁。唯一键
--   `(person_id, mail_type)` 是并发下「同人至多一次首发许可」的最终防线 —— 不以
--   campaign/account/email 切开首发唯一性（换邮箱不能绕过）。
--
-- I-2：发送状态保守推进，语义固定：
--   RESERVED    已占位、尚未交付（未调用 SMTP）
--   SENDING     已取得唯一交付权（SENDING 在 SMTP 之前持久化；进程中断即不可重发）
--   SENT        已确认交付
--   UNKNOWN     SMTP 结果不明（成功但落库失败 / 异常无法证明未交付 / SENDING 中断）
--   SAFE_RETRY  已证明未交付（明确未调用 SMTP 或 SMTP 明确未接受），仅可沿用原 recipient
--   状态迁移一律以 expected revision / owner_token 条件 UPDATE（CAS）完成，绝不因锁过期、
--   超时或换邮箱重新取得首发许可；SAFE_RETRY 仍绑定原 recipient。
--
-- I-3：占位前对全部成员做只读历史检查（原联系人、邮箱别名、mail_record /
--   mail_send_attempt、人工准入、抑制），已有成功或不确定发送不再首发；原历史 ID、
--   内容与地址不重写。history_reference_json 只存检查摘要（不含正文）。
--
-- 独立新表：不改已应用迁移、不给既有共享表增字段；外键只指向本计划的新表
-- （→ discovery_person）。删除主档即删除其首发占位（无跨业务表级联）。
-- ============================================================================

CREATE TABLE discovery_person_outreach (
    person_id CHAR(36) NOT NULL,
    mail_type VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL,
    owner_token CHAR(36) COLLATE utf8mb4_bin NOT NULL,
    selected_doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    message_id VARCHAR(255) NULL,
    attempt_id BIGINT NULL,
    history_reference_json TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (person_id, mail_type),
    CONSTRAINT fk_discovery_person_outreach_person FOREIGN KEY (person_id)
        REFERENCES discovery_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
