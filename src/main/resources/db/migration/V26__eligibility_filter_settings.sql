CREATE TABLE eligibility_filter_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(64) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO eligibility_filter_setting (setting_key, setting_value) VALUES
    ('candidate.requireValidEmail', 'true'),
    ('candidate.requireDoctoralDegree', 'false'),
    ('candidate.excludeChineseNationality', 'true'),
    ('candidate.enableAgeFilter', 'false'),
    ('candidate.maxAgeExclusive', '70'),
    ('academic.enableHIndexFilter', 'false'),
    ('academic.minHIndex', '5'),
    ('academic.enableCitationFilter', 'false'),
    ('academic.minCitationCount', '50'),
    ('academic.enableActivityFilter', 'false'),
    ('academic.recentYearsThreshold', '5'),
    ('email.enableMxCheck', 'true');
