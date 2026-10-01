-- I-1: Emailable 放行结果白名单（任务配置的第三个邮箱验证维度，与 email_verification_enabled /
-- exclude_verified_unavailable_emails 相互独立）。
-- SQL NULL = 升级前的旧配置（维持 deliverable/risky/unknown 三态全放行，不回填存量）；
-- 非 NULL 必须是合法 JSON 数组且元素仅 deliverable/risky/unknown，'[]' = 明确全跳过。
-- 坏 JSON / JSON null / 非数组 / 非法元素由读取与启动两侧拒绝，绝不当旧 NULL 放行。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_verification_allowed_states_json TEXT NULL;
