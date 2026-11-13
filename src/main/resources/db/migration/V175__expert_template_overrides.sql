CREATE TABLE expert_template_override (
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL,
    values_json LONGTEXT NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
