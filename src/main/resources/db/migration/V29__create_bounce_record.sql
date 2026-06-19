CREATE TABLE bounce_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_account_code VARCHAR(64) NOT NULL,
    bounce_message_id VARCHAR(255) NOT NULL,
    original_message_id VARCHAR(255),
    original_expert_contact_id BIGINT,
    bounce_type VARCHAR(20) NOT NULL COMMENT 'HARD or SOFT',
    dsn_status VARCHAR(20) COMMENT 'e.g. 5.1.1, 4.2.2',
    bounce_reason VARCHAR(1000),
    received_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bounce_message_id (bounce_message_id),
    INDEX idx_sender_account (sender_account_code),
    INDEX idx_received_at (received_at),
    INDEX idx_original_contact (original_expert_contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
