-- ============================================================================
-- V121 expert_follow：专家会话关注（fast-p 07，最终 schema 迁移）
--
-- 复合主键 (username, expert_contact_id)：同一用户对同一专家至多一行。
-- username = 登录会话 AUTH_USERNAME（仅当前 admin 单用户体系；不新增用户表，
-- 也不加 admin_user 外键）。created_at = 首次关注时间；幂等重复 PUT 不刷新。
-- 唯一写者 ExpertFollowService（参数化 JDBC）；读者 = 会话 summary 的 join /
-- followed 过滤。关注不写 ES / expert_contact / 来信处理状态（I-3）。
-- ============================================================================
CREATE TABLE expert_follow (
    username           VARCHAR(64)  NOT NULL COMMENT '登录用户名（Session AUTH_USERNAME）',
    expert_contact_id  BIGINT       NOT NULL,
    created_at         DATETIME     NOT NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_expert_follow_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '专家关注（fast-p 07；复合主键；仅当前 admin 使用）';
