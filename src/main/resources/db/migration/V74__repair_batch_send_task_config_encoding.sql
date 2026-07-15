-- Keep this migration ASCII-only. Some deployment paths previously decoded
-- non-ASCII Flyway literals with the connection charset before persistence.
ALTER TABLE batch_send_task_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Repair only the two mojibake legacy seed names. HEX prefixes C3A9/C3A6 are
-- the UTF-8 encodings of the first mojibake characters, so operator renames
-- are not overwritten.
UPDATE batch_send_task_config
SET config_name = CONVERT(UNHEX('E9BB98E8AEA4E4BB8BE7BB8DE982AEE4BBB6E4BBBBE58AA1') USING utf8mb4)
WHERE legacy_code = 'INTRODUCTION'
  AND HEX(config_name) LIKE 'C3A9%';

UPDATE batch_send_task_config
SET config_name = CONVERT(UNHEX('E69D90E69699E68F90E98692E4BBBBE58AA1') USING utf8mb4)
WHERE legacy_code = 'MATERIAL_REMINDER'
  AND HEX(config_name) LIKE 'C3A6%';
