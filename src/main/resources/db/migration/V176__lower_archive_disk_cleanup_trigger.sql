-- Only replace the previously deployed 10 GiB trigger. Preserve enabled, quota and custom values.
UPDATE discovery_paper_archive_setting
SET min_free_bytes = 5368709120, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 1 AND min_free_bytes = 10737418240;
