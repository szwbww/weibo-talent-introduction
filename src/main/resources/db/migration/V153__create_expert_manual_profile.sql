-- 01（I-1–I-23）：专家人工档案独立私有表。
-- 只由 ExpertManualProfileService 读写；不加 contact 外键（未联系专家也要能存），不加全局人才编号唯一约束。
-- expert_key 为 ExpertIdNormalizer.normalize(trim(expertId))；utf8mb4_bin 保证大小写敏感的身份比较。
CREATE TABLE expert_manual_profile (
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    talent_code VARCHAR(64) NULL,
    chinese_name VARCHAR(200) NULL,
    birth_date_text VARCHAR(10) NULL,
    document_number VARCHAR(100) NULL,
    source_country VARCHAR(100) NULL,
    graduated_school TEXT NULL,
    school_ranking VARCHAR(500) NULL,
    pre_china_organization TEXT NULL,
    organization_ranking VARCHAR(500) NULL,
    postdoc_organization TEXT NULL,
    postdoc_period VARCHAR(200) NULL,
    professional_title VARCHAR(200) NULL,
    administrative_titles TEXT NULL,
    industry VARCHAR(500) NULL,
    technical_expertise TEXT NULL,
    program_categories VARCHAR(500) NULL,
    research_ability VARCHAR(16) NULL,
    company_match_status VARCHAR(16) NOT NULL DEFAULT 'NOT_MATCHED',
    matched_companies TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
