-- 04（I-3/I-4/I-5/I-10）：会议视频独立私有表。
-- 只由 ExpertMeetingVideoService 读写：不写 manual_expert_material_upload / mail_attachment /
-- expert_document / expert_material_status，也不加 contact 外键（未联系专家同样可上传）。
-- id 是服务端 UUID，磁盘文件只用它命名（客户端文件名永不参与拼路径）；
-- original_filename 不可变，扩展名/内容类型由它派生，不加冗余 storagePath/状态/哈希列。
-- expert_key 为 ExpertIdNormalizer.normalize(trim(expertId))；utf8mb4_bin 保证同一专家的
-- display_filename 大小写敏感唯一（跨专家允许同名），唯一索引是最终裁决。
-- 注意：本文件不得出现 Flyway 占位符语法（${...}），生产未关闭 placeholder-replacement。
CREATE TABLE expert_meeting_video (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    display_filename VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    byte_length BIGINT NOT NULL,
    uploaded_by VARCHAR(64) NOT NULL,
    uploaded_at DATETIME(3) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_meeting_video_name (expert_key, display_filename),
    KEY idx_expert_meeting_video_list (expert_key, uploaded_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
