CREATE TABLE reply_snippet (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    snippet_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 100,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO reply_snippet (snippet_type, content, display_order, is_default, enabled)
VALUES
    ('SALUTATION', 'Dear Professor,', 10, 1, 1),
    ('GREETING', 'Thank you for your email. Please find our answers below.', 10, 1, 1),
    ('CLOSING', 'Please let us know if you have any further questions.

Best regards,
Talent Introduction Team', 10, 1, 1),
    ('ACK', 'Thank you for sharing your CV.', 10, 0, 1),
    ('ACK', 'Thank you for sharing your materials.', 20, 0, 1),
    ('ACK', 'Thank you for your prompt reply.', 30, 0, 1);
