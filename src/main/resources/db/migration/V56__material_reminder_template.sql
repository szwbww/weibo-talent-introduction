INSERT INTO mail_template (
    template_code,
    template_name,
    subject,
    body,
    language,
    enabled
) VALUES (
    'MATERIAL_REMINDER',
    'Material Reminder Email',
    'Reminder: Requested Materials for the Talent Program',
    'Dear Professor,

I hope this message finds you well. I am writing to follow up on the materials you previously indicated you would share with us for the talent program application.

We have not yet received the documents, and we would appreciate it if you could send them at your earliest convenience so that we can continue processing your application.

If you have already sent the materials, please accept our apologies for this reminder and kindly let us know. If you need more time or have any questions, please feel free to reply to this email.

Thank you for your time and consideration.

Sincerely,
${senderName}, ${senderTitle}
${teamName} ${countryName}',
    'en',
    1
)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject = VALUES(subject),
    body = VALUES(body),
    language = VALUES(language),
    enabled = VALUES(enabled);
