CREATE TABLE ai_prompt_config (
    id BIGINT NOT NULL PRIMARY KEY,
    free_form_system_prompt TEXT,
    constraints TEXT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO ai_prompt_config (id) VALUES (1);
