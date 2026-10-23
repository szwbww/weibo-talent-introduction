CREATE TABLE discovery_apollo_setting (
  id INT NOT NULL,
  search_enabled TINYINT(1) NOT NULL,
  detail_enabled TINYINT(1) NOT NULL,
  revision BIGINT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_discovery_apollo_setting_id CHECK (id = 1),
  CONSTRAINT chk_discovery_apollo_setting_values CHECK (
    search_enabled IN (0,1) AND detail_enabled IN (0,1) AND revision > 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
