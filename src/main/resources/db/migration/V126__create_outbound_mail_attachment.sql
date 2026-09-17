-- ============================================================================
-- V126 outbound_mail_attachment（fast-p 04：人工回复通用附件上传原件）
--
-- 一行 = 一次成功上传的通用附件原件元数据。id 是服务端随机 UUID（CHAR(36)），
-- 同时就是磁盘文件名 outbound/<id>（不再落库第二份 storage_key）。元数据与原
-- 字节创建后不可变：本表只有 INSERT 与按 id 批量读取，没有 UPDATE/DELETE API，
-- 「移除草稿附件」只解除草稿引用（客户端引用），不删本行、不删已上传文件。
--
-- 关键不变量：
--   I-1 上传只写本表与 outbound/ 目录：不写 mail_attachment/expert_document，
--       不调用 SMTP、不建排期、不变更专家状态；UUID 由主键保证永不复用。
--   I-2 expert_contact_id 必须是当前已存在专家（FK ON DELETE RESTRICT）；
--       created_by 取会话登录名，请求体 operatorName 不参与身份。
--   I-3 无扩展名/MIME 白名单列；file_name 只存展示名（最多 255 字符），
--       byte_length 与 sha256 都是原件真实字节口径；0 字节合法。
--   I-5 发送快照 JSON 由 05 写入 mail_record 新列；本表既不存字节也不存快照。
--
-- 索引 (expert_contact_id, created_at)：按专家列出本人上传附件的稳定顺序。
-- 未被引用的上传文件本期保留原样（不引入状态列/租约/清理调度）。
-- ============================================================================
CREATE TABLE outbound_mail_attachment (
    id                CHAR(36)     NOT NULL,
    expert_contact_id BIGINT       NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(255) NOT NULL,
    byte_length       BIGINT       NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_outbound_mail_attachment_contact (expert_contact_id, created_at),
    CONSTRAINT fk_outbound_mail_attachment_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '人工回复通用附件上传原件元数据（fast-p 04）';
