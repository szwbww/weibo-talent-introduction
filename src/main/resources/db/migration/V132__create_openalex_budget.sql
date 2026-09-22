-- ============================================================================
-- V132 OpenAlex 账号免费预算账本（fast-p 01：账号周期预算 / 预占 / 校准）
--
-- 三张表 = 账号级预算的唯一持久化事实源：同一 API Key 的全部本系统实例共用同一
-- account_scope（非秘密稳定标识），因此重启、多实例、换 Key 都不会各自拿到一份额度。
--
-- 关键不变量（子计划 01 I-2/I-3/I-6）：
--   I-2 account_scope 是稳定非秘密账号标识，全表**没有**任何 API Key 列；额度单位是
--       整数 credits，free_limit 只来自官方日免费额度与配置保护上限的较小值，预付余额
--       永不入库、永不参与可用额计算。
--   I-3 预占先于外部请求：reserve 在一个事务里锁 account 行，再锁本周期 day 行，
--       最后写 reservation；permit_id 唯一，状态只允许 RESERVED→SETTLED|UNKNOWN，
--       UNKNOWN 不能自动当未花费（仍计入 outstanding_reserved），只有有依据的结算或
--       周期关闭才能归档它。amount_reserved（本次预占）与 amount_actual（确认成本）分列。
--   I-6 每列都有生命周期：day 唯一 (account_scope, reset_at)（官方 reset 即周期标识）；
--       reservation 唯一 permit_id 并用 day_id 关联其周期（旧周期 permit 只结算旧行）；
--       免费上限 free_limit、确认消耗 confirmed_spent、未结算预占 outstanding_reserved 与
--       provider_ceiling 分别存储 —— 禁止把快照的「可用」值再当原始余额扣减；
--       rate_next_at / cooldown_until / sync_lease_token / sync_lease_until / last_synced_at
--       归账号行，重启不清除。
--   时间列一律 DATETIME(3) 且按 UTC 写入（本仓 V119/V130/V131 约定），整数列 BIGINT。
--
-- 固定锁序 account → day → reservation：多实例并发时按同一顺序加行锁，不交叉死锁。
-- 归档：已关闭周期（closed_at 非空）超过保留期后按批删除；活跃周期与未解决 permit
-- 不按短 TTL 释放，因此清理谓词只看 closed_at。不加外键：清理按 day_id 先删明细再删周期。
-- MySQL 兼容性：DATETIME(3)/utf8mb4_bin/命名 CHECK 在 5.7 均可解析（5.7 忽略 CHECK 谓词，
-- 8.0.16+ 真实生效）；不使用 `DEFAULT (...)` 等 8.0.13+ 专属语法。
-- ============================================================================
CREATE TABLE openalex_budget_account (
    account_scope          VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    rate_next_at           DATETIME(3) NOT NULL,
    cooldown_until         DATETIME(3) NULL,
    sync_lease_token       VARCHAR(64) NULL,
    sync_lease_until       DATETIME(3) NULL,
    last_synced_at         DATETIME(3) NULL,
    last_new_enrichment_at DATETIME(3) NULL,
    created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (account_scope)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'OpenAlex 账号级预算状态：限速槽、429 冷却、校准租约与最近同步时间（无凭证列）';

CREATE TABLE openalex_budget_day (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    account_scope        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    reset_at             DATETIME(3) NOT NULL,
    free_limit           BIGINT      NOT NULL,
    confirmed_spent      BIGINT      NOT NULL DEFAULT 0,
    outstanding_reserved BIGINT      NOT NULL DEFAULT 0,
    provider_ceiling     BIGINT      NULL,
    closed_at            DATETIME(3) NULL,
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_openalex_budget_day_cycle (account_scope, reset_at),
    KEY idx_openalex_budget_day_open (account_scope, closed_at, reset_at),
    KEY idx_openalex_budget_day_closed (closed_at, id),
    CONSTRAINT chk_openalex_budget_day_amounts
        CHECK (free_limit >= 0 AND confirmed_spent >= 0 AND outstanding_reserved >= 0
               AND (provider_ceiling IS NULL OR provider_ceiling >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'OpenAlex 官方 reset 周期账本：免费上限/确认消耗/未结算预占/provider ceiling 分列';

CREATE TABLE openalex_budget_reservation (
    permit_id       CHAR(36)    NOT NULL,
    day_id          BIGINT      NOT NULL,
    request_kind    VARCHAR(32) NOT NULL,
    operation       VARCHAR(16) NOT NULL,
    amount_reserved BIGINT      NOT NULL,
    amount_actual   BIGINT      NULL,
    status          VARCHAR(16) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    settled_at      DATETIME(3) NULL,
    PRIMARY KEY (permit_id),
    KEY idx_openalex_budget_reservation_day_status (day_id, status),
    KEY idx_openalex_budget_reservation_kind_settled (request_kind, status, settled_at),
    CONSTRAINT chk_openalex_budget_reservation_status
        CHECK (status IN ('RESERVED', 'SETTLED', 'UNKNOWN')),
    CONSTRAINT chk_openalex_budget_reservation_operation
        CHECK (operation IN ('LIST', 'SEARCH', 'SINGLETON', 'CONTENT', 'RATE_LIMIT')),
    CONSTRAINT chk_openalex_budget_reservation_kind
        CHECK (request_kind IN ('DISCOVERY', 'HISTORY_ENRICHMENT', 'NEW_ENRICHMENT')),
    CONSTRAINT chk_openalex_budget_reservation_amounts
        CHECK (amount_reserved >= 0 AND (amount_actual IS NULL OR amount_actual >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'OpenAlex 请求预占明细：permit 唯一、只结算一次、关联其 reset 周期';
