CREATE TABLE expert_contact_location (
    expert_contact_id BIGINT NOT NULL,
    country_code CHAR(2) NOT NULL,
    zone_id VARCHAR(64) NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_location_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='人工配置的联系人所在地；zone_id为空表示使用国家默认时区';
