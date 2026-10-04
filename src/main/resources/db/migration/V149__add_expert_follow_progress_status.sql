-- ============================================================================
-- V149 expert_follow.progress_status：收发件箱三态标记（fast-p 01）
--
-- 沿用 V121 的 (username, expert_contact_id) 唯一行，不新表、不加 provided 布尔列
-- （I-1）：行值 FOLLOWING（跟进中）/ PROVIDED（已提供）；无行 = NONE（未标记），
-- 禁止写入 NONE 行。旧关注行由 DEFAULT 'FOLLOWING' 原地升级为跟进中（P-1），
-- 原 username / expert_contact_id / created_at 全部保留。
--
-- 复合主键与 FK 不变；唯一写者仍为 ExpertFollowService（参数化 JDBC：
-- FOLLOWING/PROVIDED 单条 upsert，NONE 单条 DELETE）。
-- ============================================================================
ALTER TABLE expert_follow
    ADD COLUMN progress_status VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'
    COMMENT 'FOLLOWING=跟进中；PROVIDED=已提供；无行=未标记';
