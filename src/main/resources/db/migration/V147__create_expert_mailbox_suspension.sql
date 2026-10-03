-- ============================================================================
-- V147 expert_mailbox_suspension：收发件箱专家会话挂起（fast-p 01）
--
-- 复合主键 (username, expert_contact_id)：同一登录用户对同一专家至多一行。
-- 行存在即挂起（I-1）：reason 可空 = 未填写原因，绝不表示未挂起；没有第二个
-- suspended 布尔列、完成状态或墓碑。取消即 DELETE 行。
--
-- username = 登录会话 AUTH_USERNAME（沿 V121 expert_follow 的现有关注模式；
-- 仅当前 admin 单用户体系，不新增用户表）。唯一写者 MailboxSuspensionService
-- （参数化 JDBC，PUT INSERT / DELETE，无自动结束写路径 I-2）。
-- ============================================================================
CREATE TABLE expert_mailbox_suspension (
    username VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_mailbox_suspension_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
