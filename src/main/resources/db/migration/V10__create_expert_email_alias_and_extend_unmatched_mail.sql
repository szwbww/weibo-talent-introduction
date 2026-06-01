CREATE TABLE IF NOT EXISTS expert_email_alias (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  expert_contact_id BIGINT NOT NULL,
                                                  email VARCHAR(255) NOT NULL,
                                                  normalized_email VARCHAR(255) NOT NULL,
                                                  source VARCHAR(50) NOT NULL DEFAULT 'MANUAL_BIND',
                                                  verified BOOLEAN NOT NULL DEFAULT TRUE,
                                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  CONSTRAINT uk_expert_email_alias_normalized_email UNIQUE (normalized_email),
                                                  CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_missing//
CREATE PROCEDURE add_column_if_missing(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND COLUMN_NAME = target_column
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_index_if_missing//
CREATE PROCEDURE add_index_if_missing(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND INDEX_NAME = target_index
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_foreign_key_if_missing//
CREATE PROCEDURE add_foreign_key_if_missing(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND CONSTRAINT_NAME = target_constraint
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DELIMITER ;

CALL add_index_if_missing(
        'expert_email_alias',
        'uk_expert_email_alias_normalized_email',
        'CREATE UNIQUE INDEX uk_expert_email_alias_normalized_email ON expert_email_alias(normalized_email)'
     );
CALL add_index_if_missing(
        'expert_email_alias',
        'idx_expert_email_alias_expert_contact_id',
        'CREATE INDEX idx_expert_email_alias_expert_contact_id ON expert_email_alias(expert_contact_id)'
     );
CALL add_foreign_key_if_missing(
        'expert_email_alias',
        'fk_expert_email_alias_expert_contact',
        'ALTER TABLE expert_email_alias ADD CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)'
     );

CALL add_column_if_missing(
        'inbound_mail_processing',
        'in_reply_to',
        'ALTER TABLE inbound_mail_processing ADD COLUMN in_reply_to VARCHAR(255) DEFAULT NULL AFTER message_id'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'body',
        'ALTER TABLE inbound_mail_processing ADD COLUMN body TEXT DEFAULT NULL AFTER subject'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'cleaned_body',
        'ALTER TABLE inbound_mail_processing ADD COLUMN cleaned_body TEXT DEFAULT NULL AFTER body'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'resolved_at',
        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_at DATETIME DEFAULT NULL AFTER process_reason'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'resolved_by',
        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_by VARCHAR(100) DEFAULT NULL AFTER resolved_at'
     );

DROP PROCEDURE IF EXISTS add_foreign_key_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS add_column_if_missing;
