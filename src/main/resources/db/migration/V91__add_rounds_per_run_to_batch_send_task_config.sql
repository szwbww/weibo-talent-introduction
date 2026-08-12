-- I-5: rounds_per_run is NOT NULL with DEFAULT 1; backfill keeps existing rows at their
-- prior effective round budget (I-6): CEIL(daily_cap / round_size) is exactly the number
-- of rounds the dailyCap gate allowed to start, so the per-run send volume is unchanged.
ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size;

UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));
