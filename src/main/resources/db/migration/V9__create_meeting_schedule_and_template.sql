CREATE TABLE meeting_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    source_mail_record_id BIGINT,
    expert_available_text VARCHAR(1024),
    expert_timezone VARCHAR(64),
    china_time VARCHAR(255),
    meeting_tool VARCHAR(64),
    meeting_link VARCHAR(1024),
    meeting_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_meeting_schedule_contact (expert_contact_id),
    CONSTRAINT fk_meeting_schedule_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_meeting_schedule_mail FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id)
);

INSERT INTO mail_template (
    template_code,
    template_name,
    subject,
    body,
    language,
    enabled
) VALUES (
    'MEETING_CONFIRMATION',
    'Meeting Confirmation Email',
    'Meeting Confirmed: Research Collaboration Discussion',
    'Dear Professor,

Thank you for your response and availability.

I am pleased to confirm our meeting details:
Time: ${meetingTime} (China Time)
Meeting Tool: ${meetingTool}
Meeting Link: ${meetingLink}

If you have any issues connecting, please reply to this email. Looking forward to speaking with you soon!

Sincerely,
${senderName}, ${senderTitle}
${teamName}',
    'en',
    1
)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject = VALUES(subject),
    body = VALUES(body),
    language = VALUES(language),
    enabled = VALUES(enabled);
