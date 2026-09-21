-- ============================================================================
-- V131 可恢复的补全任务存储（fast-p 07：待补全/限流待重试/失败任务持久化）
--
-- 一行 = 一个专家文档（真实 ES `_id`）的学术补全生命周期。本表是补全任务的
-- **唯一**生命周期存储：不新增 ES 根级状态字段，不建第二套队列/索引表。
--
-- 关键不变量：
--   I-1 UNIQUE(expert_doc_id)：同专家多次入队合并成一行；status 仅
--       PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/UNMATCHED/FAILED；
--       SUCCEEDED 且 updated_at 在 30 天内不重入；UNMATCHED 可被可靠身份
--       变更后的再次入队重开。expert_doc_id 用列级 utf8mb4_bin：ES `_id`
--       区分大小写，唯一键绝不能把两个不同文档合并（M-1 真实专家身份）。
--   I-2 claim/续租/完成全部是 status + lease_token + 租约条件 CAS（代码层
--       WHERE，本迁移不引入触发器）：claim 条件与
--       `(status, next_attempt_at, lease_until)` 一致，租期 10 分钟；进程崩溃后
--       过期 RUNNING 重新可领，旧 token 不能覆盖新结果；不依赖 SKIP LOCKED。
--   I-3 attempts 只计故障尝试（429/日额度延期不消耗、可无限延期）；
--       last_error 只存脱敏原因码，绝不写 API Key 或明文邮箱。
--   I-4 仅新增本迁移，不改写任何已应用迁移，不启用 out-of-order。
--
-- 队列索引 (status, next_attempt_at, id) 与 claim 谓词同形；
-- discovery_execution_id 可空且**不设外键**：它只是审计归属（08 记录发现的
-- task_execution），而 task_execution 有保留期清理，外键会让清理失败。
-- 时间列沿用本仓 DATETIME(3) 约定（V119/V130）。
-- MySQL 兼容性：DATETIME(3)/utf8mb4_bin/命名 CHECK 在 5.7 均可解析（5.7 忽略
-- CHECK 谓词，8.0.16+ 真实生效）；不使用 `DEFAULT (...)` 与 `DROP CHECK` 等
-- 8.0.13+/8.0.16+ 专属语法。
-- ============================================================================
CREATE TABLE expert_academic_enrichment_job (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    expert_doc_id          VARCHAR(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source                 VARCHAR(32)   NOT NULL,
    discovery_execution_id BIGINT        NULL,
    status                 VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    attempts               INT           NOT NULL DEFAULT 0,
    next_attempt_at        DATETIME(3)   NOT NULL,
    lease_token            VARCHAR(64)   NULL,
    lease_until            DATETIME(3)   NULL,
    last_error             VARCHAR(1000) NULL,
    result_json            TEXT          NULL,
    created_at             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_academic_enrichment_job_doc (expert_doc_id),
    KEY idx_expert_academic_enrichment_job_due (status, next_attempt_at, id),
    CONSTRAINT chk_expert_academic_enrichment_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'UNMATCHED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '专家学术补全任务生命周期（fast-p 07，可恢复/可跨进程领取）';
