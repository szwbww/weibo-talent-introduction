CREATE TABLE batch_send_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(64) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO batch_send_setting (setting_key, setting_value) VALUES
    ('batchSend.autoEnabled', 'false'),
    ('batchSend.cron', '0 0 0 * * ?'),
    ('batchSend.dailyCap', '1000'),
    ('batchSend.roundSize', '50'),
    ('batchSend.perMailIntervalMs', '1000'),
    ('batchSend.perRoundIntervalMs', '60000'),
    ('batchSend.selfCheckTtlMinutes', '30'),
    ('batchSend.runtimeStatus', 'IDLE'),
    ('batchSend.runtimeMode', 'NONE'),
    ('batchSend.pauseReason', '');
