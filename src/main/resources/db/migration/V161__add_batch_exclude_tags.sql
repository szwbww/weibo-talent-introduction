-- SQL NULL preserves legacy tasks and old-client inserts; new tasks store a JSON array.
ALTER TABLE batch_send_task_config ADD COLUMN exclude_tags_json TEXT NULL AFTER tags_json;
