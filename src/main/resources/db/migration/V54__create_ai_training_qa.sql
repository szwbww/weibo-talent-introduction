CREATE TABLE ai_training_qa (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    question TEXT,
    answer TEXT NOT NULL,
    keywords VARCHAR(512),
    source VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_training_qa_source_ref (source, source_ref)
);
