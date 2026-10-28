-- ============================================================================
-- V166 expert_mail_read_access：admin 授予内置 user1 的专家邮件查看权（01）
--
-- 关系即状态：行存在 = 允许查看该专家（expert_contact.id）的邮件；
-- 行不存在 = 禁止。不增加 enabled / role / read / send 等冗余列，也没有第二套
-- 状态词汇。主键固定为 (username, expert_contact_id)，username 只允许内置
-- `user1`（由 ExpertMailReadAccessService 固定写入，不来自请求体）。
--
-- granted_by 只记录当前授权的操作者（admin session），created_at = 首次授予时间；
-- 重复 PUT 不刷新，撤销后重新授予写新时间。撤销不保留历史审计（本步不建
-- audit_log 副作用）。
--
-- 唯一写者：ExpertMailReadAccessService（参数化 JDBC，INSERT ... ON DUPLICATE
-- KEY UPDATE / DELETE）。FK 级联是本次设计：专家联系人被删除时自动清理授权行。
-- 不设 admin_user 外键（允许账号开放前预先授权）；无初始 grant / 回填。
-- ============================================================================
CREATE TABLE expert_mail_read_access (
    username VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    granted_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_expert_mail_read_access_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
