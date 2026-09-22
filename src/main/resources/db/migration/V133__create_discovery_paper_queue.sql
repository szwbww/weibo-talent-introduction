-- ============================================================================
-- V133 持久化采集队列与窗口控制（fast-p 02：队列、恢复、窗口服务）
--
-- 三张表 = 深度发现的唯一持久化事实源：pipeline（单例控制 + 计数 + 容量）、
-- collection_stream（每来源的查询位置）、paper_job（每条工作的生命周期）。
-- 采集/抽取位置与处理完成彻底解耦：整页入队与 cursor 推进在同一事务，处理进度只落在 job 行。
--
-- 关键不变量（子计划 02 I-1 至 I-8）：
--   I-1 pipeline 只保存控制与累计计数；每个来源自己的查询位置在 collection_stream：
--       UNIQUE(query_hash, source, epoch)，cursor 仅在**整页入队同一事务**提交后才推进，
--       cursor_state 只允许 ACTIVE/EXHAUSTED；EXHAUSTED 不因重启/次日/新窗口重置。
--       query_hash 由「本源规范化有效条件」算出（关键词、机构、国家排除、年份、OA、scope、
--       页大小、本源自己的名字），不含其他 sources 的排列/省略方式。
--   I-2 paper_job UNIQUE(stream_id, item_key)：item_key 是带类型前缀的 SHA-256（DOI/PMCID/PMID/
--       ORCID），无 ID 时是规范序列化元数据的 SHA-256 且 identity_quality=PAYLOAD_HASH
--       （绝不用标题模糊合并）。unit 显式区分 PAPER/RECORD，payload_version 未知版本不得消费。
--   I-3 job 状态机 PENDING→RUNNING→{SUCCEEDED|RETRY_WAIT|FAILED}；claim/heartbeat/finish/
--       saveExtraction 全部是 lease_token + generation 条件 CAS（代码层 WHERE，本迁移不引入触发器）；
--       租约过期的 RETRY_WAIT/RUNNING 可重新领取，旧 token 的终态更新影响 0 行。
--       generation 记录「领取时的 pipeline.generation」：人工暂停递增 pipeline.generation 后，
--       未重新领取且租约仍有效的旧 job 仍可保存抽取结果并退回 PENDING，但不能 complete。
--   I-5 active_count/payload_bytes/reserved_result_bytes 由入队与状态 CAS 同事务维护；
--       未抽取的活跃 job 在入队时预留 reserved_result_bytes，保存抽取结果时转成实际
--       payload_bytes。PDF 二进制永不入库（本表只存元数据与抽取出的邮箱结构）。
--   I-6 paper_job.priority 记录「该条是否可公开下载」，消费者在来源内优先高优先任务，
--       同时每 10 个高优先任务至少取 1 个最老普通任务。
--   I-7 pipeline 是**单例**（id=1）：desired_state 只允许 PAUSED/RUNNING，phase 只允许
--       QUEUED/RUNNING/WAITING/DRAINED/FAULTED；初始 PAUSED；owner_token/owner_until 是
--       30 秒窗口属主租约，window_until 是 4 小时窗口截止；next_wake_at/wait_reason 表达
--       可进展时间；raw_scan_done 保证 RAW 扫描不重复。
--   I-8 七个计数分别累加、一次 CAS 最多累加一次；queueDepth 只计活跃 job；
--       已终态负载 7 天后可清空（completed_at），去重键/状态 90 天保留。
--
-- 锁序固定 pipeline → collection_stream → paper_job；网络与 ES 调用一律不持锁。
-- 时间列一律 DATETIME(3) 且按 UTC 写入（本仓 V119/V130/V131/V132 约定），
-- 计数与字节列一律 BIGINT/INT，租约 token 用 VARCHAR(64)（UUID）。
-- 不加外键：task_execution 与 expert 相关表都有各自的清理/保留语义，外键会让清理失败；
-- 清理顺序由 repository 显式保证（先 job 后 stream 后 pipeline）。
-- MySQL 兼容性：DATETIME(3)/utf8mb4_bin/命名 CHECK 在 5.7 均可解析（5.7 忽略 CHECK 谓词，
-- 8.0.16+ 真实生效）；不使用 `DEFAULT (...)` 等 8.0.13+ 专属语法。
-- ============================================================================

CREATE TABLE discovery_pipeline (
    id                    BIGINT       NOT NULL,
    desired_state         VARCHAR(16)  NOT NULL DEFAULT 'PAUSED',
    phase                 VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    criteria_json         MEDIUMTEXT   NULL,
    criteria_version      INT          NOT NULL DEFAULT 0,
    query_hash            CHAR(64)     NULL,
    generation            BIGINT       NOT NULL DEFAULT 0,
    owner_token           VARCHAR(64)  NULL,
    owner_until           DATETIME(3)  NULL,
    execution_id          BIGINT       NULL,
    window_until          DATETIME(3)  NULL,
    next_wake_at          DATETIME(3)  NULL,
    wait_reason           VARCHAR(32)  NULL,
    raw_scan_done         TINYINT(1)   NOT NULL DEFAULT 0,
    queued_papers         BIGINT       NOT NULL DEFAULT 0,
    queued_records        BIGINT       NOT NULL DEFAULT 0,
    processed_papers      BIGINT       NOT NULL DEFAULT 0,
    processed_records     BIGINT       NOT NULL DEFAULT 0,
    indexed_experts       BIGINT       NOT NULL DEFAULT 0,
    duplicate_experts     BIGINT       NOT NULL DEFAULT 0,
    failed_items          BIGINT       NOT NULL DEFAULT 0,
    active_count          BIGINT       NOT NULL DEFAULT 0,
    payload_bytes         BIGINT       NOT NULL DEFAULT 0,
    reserved_result_bytes BIGINT       NOT NULL DEFAULT 0,
    capacity_paused       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_discovery_pipeline_desired_state
        CHECK (desired_state IN ('PAUSED', 'RUNNING')),
    CONSTRAINT chk_discovery_pipeline_phase
        CHECK (phase IN ('QUEUED', 'RUNNING', 'WAITING', 'DRAINED', 'FAULTED')),
    CONSTRAINT chk_discovery_pipeline_counters
        CHECK (queued_papers >= 0 AND queued_records >= 0 AND processed_papers >= 0
               AND processed_records >= 0 AND indexed_experts >= 0 AND duplicate_experts >= 0
               AND failed_items >= 0 AND active_count >= 0 AND payload_bytes >= 0
               AND reserved_result_bytes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '深度发现流水线单例：持久化控制、窗口属主租约、I-8 计数与 I-5 容量';

CREATE TABLE discovery_collection_stream (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    pipeline_id        BIGINT       NOT NULL,
    query_hash         CHAR(64)     CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source             VARCHAR(50)  NOT NULL,
    epoch              BIGINT       NOT NULL,
    criteria_json      MEDIUMTEXT   NULL,
    cursor_value       TEXT         NULL,
    cursor_state       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    next_attempt_at    DATETIME(3)  NULL,
    lease_token        VARCHAR(64)  NULL,
    lease_until        DATETIME(3)  NULL,
    source_error       VARCHAR(1000) NULL,
    queued_papers      BIGINT       NOT NULL DEFAULT 0,
    queued_records     BIGINT       NOT NULL DEFAULT 0,
    processed_papers   BIGINT       NOT NULL DEFAULT 0,
    processed_records  BIGINT       NOT NULL DEFAULT 0,
    indexed_experts    BIGINT       NOT NULL DEFAULT 0,
    duplicate_experts  BIGINT       NOT NULL DEFAULT 0,
    failed_items       BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_discovery_collection_stream_identity (query_hash, source, epoch),
    KEY idx_discovery_collection_stream_pipeline (pipeline_id, id),
    CONSTRAINT chk_discovery_collection_stream_cursor_state
        CHECK (cursor_state IN ('ACTIVE', 'EXHAUSTED')),
    CONSTRAINT chk_discovery_collection_stream_counters
        CHECK (queued_papers >= 0 AND queued_records >= 0 AND processed_papers >= 0
               AND processed_records >= 0 AND indexed_experts >= 0 AND duplicate_experts >= 0
               AND failed_items >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '每来源的采集位置：query_hash 独立、cursor 仅在整页入队同一事务提交后推进';

CREATE TABLE discovery_paper_job (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    stream_id             BIGINT       NOT NULL,
    item_key              VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    identity_quality      VARCHAR(24)  NOT NULL,
    unit                  VARCHAR(16)  NOT NULL,
    payload_version       INT          NOT NULL,
    priority              TINYINT      NOT NULL DEFAULT 0,
    metadata_json         MEDIUMTEXT   NOT NULL,
    extraction_json       MEDIUMTEXT   NULL,
    payload_bytes         BIGINT       NOT NULL DEFAULT 0,
    reserved_result_bytes BIGINT       NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts              INT          NOT NULL DEFAULT 0,
    next_attempt_at       DATETIME(3)  NOT NULL,
    lease_token           VARCHAR(64)  NULL,
    lease_until           DATETIME(3)  NULL,
    generation            BIGINT       NOT NULL DEFAULT 0,
    last_error            VARCHAR(1000) NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    completed_at          DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_discovery_paper_job_item (stream_id, item_key),
    KEY idx_discovery_paper_job_due (status, next_attempt_at, stream_id, id),
    KEY idx_discovery_paper_job_terminal (completed_at, id),
    CONSTRAINT chk_discovery_paper_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'RETRY_WAIT', 'FAILED')),
    CONSTRAINT chk_discovery_paper_job_unit
        CHECK (unit IN ('PAPER', 'RECORD')),
    CONSTRAINT chk_discovery_paper_job_identity_quality
        CHECK (identity_quality IN ('DOI', 'PMCID', 'PMID', 'ORCID', 'PAYLOAD_HASH')),
    CONSTRAINT chk_discovery_paper_job_amounts
        CHECK (payload_bytes >= 0 AND reserved_result_bytes >= 0 AND attempts >= 0 AND priority >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '采集队列条目（PAPER/RECORD）：租约 + generation CAS 的可恢复工作单元';
