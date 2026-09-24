-- ============================================================================
-- V138 介绍邮件发送前邮箱验证明细（fast-p c1：Emailable 验证 / 标签 / 审计收尾）
--
-- 一次执行一行 = 一个专家（真实身份键 + 实际收件地址）的验证、发送与标签结果。
-- 表存在的原因（子计划 01 I-6/I-9）：现有 task_progress_log 是折叠日志，错误样本最多 20 条，
-- 不能作为「逐邮箱可追溯」的持久化事实源；把每个邮箱数组塞进 progress.details_json 只会让
-- 进度日志膨胀。故新增独立明细表，状态/结果汇总一律来自本表。
--
-- 关键不变量：
--   I-6 task_execution_id + 规范化 ORCID + 规范化邮箱唯一；先插 PENDING，再请求，再写
--       PASS/SKIP/ERROR，随后才允许发送；发送前把 decision='PASS' 且 send_status='NOT_SENT'
--       的行条件更新为 SENDING（必须影响 1 行才允许 SMTP），结果写 SENT/FAILED；
--       崩溃或已发后写库失败保留 SENDING（对外解释「结果未确认」），禁止据此自动重发。
--   I-9 外键 task_execution(id) ON DELETE CASCADE，跟随现有 90 天默认保留期清理，不加新清理任务；
--       分页严格按 (task_execution_id, id) 游标查询，默认 50、最大 100。
--
-- 列语义（下游 03 只读接口依赖，不得改名/改语义）：
--   expert_doc_id 真实 ES `_id`，可缺失（缺失时标签记 FAILED/MISSING_DOC_ID，禁止用 ORCID 冒充）
--   orcid_id      规范化业务 ID，非空（不虚构 _id）
--   expert_name   展示快照；超长只截断名字，绝不截断身份键
--   email         规范化的实际收件地址（trim + lowercase(Locale.ROOT)，保留 +tag、不合并点号）；
--                 超长（> 320）明确拒绝，不截断
--   decision      PENDING / PASS / SKIP / ERROR
--   provider_state / provider_reason  供应商明确结果的状态与原因（原始 reason 只作数据）
--   error_code    受控错误码（EMAIL_VERIFY_*），不保存完整 HTTP 请求/响应
--   request_count 物理请求次数；同执行内同邮箱复用为 0；不是积分余额
--   checked_at    返回明确结果/服务错误的北京时间；复用行沿原结果时间
--   send_status   NOT_SENT / SENDING / SENT / FAILED / SKIPPED
--   send_reason   既有跳过/失败码或 CANCELLED / ACCOUNT_UNAVAILABLE / RESULT_UNCONFIRMED
--   tag_status    NOT_REQUIRED / PENDING / APPLIED / FAILED
--   tag_error     只记层名与受控原因，不记正文
--
-- 排序规则：身份键（orcid_id / email / expert_doc_id）用 utf8mb4_bin 严格比较，
-- 避免 utf8mb4_general_ci 把大小写不同的地址判为同一目标，破坏唯一键语义。
-- 键长：唯一键 (task_execution_id, orcid_id, email) = 8 + (128 + 320) * 4 = 1800 字节 < 3072。
-- 不建 contact 外键：验证拒绝的目标不得创建/绑定 expert_contact。
-- MySQL 兼容性：DATETIME(3)/utf8mb4_bin/命名外键在 5.7 与 8.0 均可解析。
-- ============================================================================
CREATE TABLE batch_email_verification (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    task_execution_id BIGINT       NOT NULL,
    expert_doc_id     VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    orcid_id          VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expert_name       VARCHAR(256) NULL,
    email             VARCHAR(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    decision          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    provider_state    VARCHAR(32)  NULL,
    provider_reason   VARCHAR(128) NULL,
    error_code        VARCHAR(64)  NULL,
    request_count     INT          NOT NULL DEFAULT 0,
    checked_at        DATETIME(3)  NULL,
    send_status       VARCHAR(16)  NOT NULL DEFAULT 'NOT_SENT',
    send_reason       VARCHAR(64)  NULL,
    tag_status        VARCHAR(16)  NOT NULL DEFAULT 'NOT_REQUIRED',
    tag_error         VARCHAR(256) NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_email_verification_target (task_execution_id, orcid_id, email),
    KEY idx_batch_email_verification_execution (task_execution_id, id),
    CONSTRAINT fk_batch_email_verification_execution
        FOREIGN KEY (task_execution_id) REFERENCES task_execution (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '介绍邮件发送前邮箱验证逐专家明细：验证结论/发送结果/标签处理，随执行级联清理';
