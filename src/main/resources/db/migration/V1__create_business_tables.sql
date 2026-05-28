CREATE TABLE mail_sender_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL UNIQUE,
    sender_email VARCHAR(255) NOT NULL,
    sender_name VARCHAR(128) NOT NULL,
    sender_title VARCHAR(128),
    sender_display_name VARCHAR(128),
    team_name VARCHAR(128),
    country_name VARCHAR(64),
    smtp_host VARCHAR(255) NOT NULL,
    smtp_port INT NOT NULL,
    smtp_username VARCHAR(255) NOT NULL,
    smtp_password VARCHAR(255) NOT NULL,
    imap_host VARCHAR(255) NOT NULL,
    imap_port INT NOT NULL,
    imap_username VARCHAR(255) NOT NULL,
    imap_password VARCHAR(255) NOT NULL,
    strategy_weight INT NOT NULL DEFAULT 100,
    daily_send_limit INT NOT NULL DEFAULT 100,
    today_sent_count INT NOT NULL DEFAULT 0,
    last_sent_at DATETIME,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE mail_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL UNIQUE,
    template_name VARCHAR(128) NOT NULL,
    subject VARCHAR(255),
    body TEXT NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE qa_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(64) NOT NULL UNIQUE,
    category_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE qa_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    keywords TEXT NOT NULL,
    match_mode VARCHAR(16) NOT NULL DEFAULT 'ANY',
    priority INT NOT NULL DEFAULT 100,
    reply_subject VARCHAR(255),
    reply_body TEXT NOT NULL,
    auto_reply_enabled TINYINT(1) NOT NULL DEFAULT 1,
    handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_qa_rule_category
        FOREIGN KEY (category_id) REFERENCES qa_category(id)
);

CREATE TABLE campaign (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_code VARCHAR(64) NOT NULL UNIQUE,
    campaign_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    sender_account_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_sender
        FOREIGN KEY (sender_account_id) REFERENCES mail_sender_account(id)
);

CREATE TABLE expert_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    orcid_id VARCHAR(64) NOT NULL,
    expert_email VARCHAR(255) NOT NULL,
    expert_name VARCHAR(255),
    current_status VARCHAR(64) NOT NULL DEFAULT 'NEW',
    last_mail_at DATETIME,
    last_reply_at DATETIME,
    manual_handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    closed_reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id),
    CONSTRAINT fk_expert_contact_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign(id)
);

CREATE TABLE mail_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    mail_type VARCHAR(64) NOT NULL,
    message_id VARCHAR(255),
    in_reply_to VARCHAR(255),
    subject VARCHAR(255),
    body LONGTEXT,
    matched_qa_rule_id BIGINT,
    send_status VARCHAR(32),
    received_at DATETIME,
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mail_record_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_mail_record_rule
        FOREIGN KEY (matched_qa_rule_id) REFERENCES qa_rule(id)
);

CREATE TABLE manual_handoff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    handoff_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_to VARCHAR(128),
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_manual_handoff_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
