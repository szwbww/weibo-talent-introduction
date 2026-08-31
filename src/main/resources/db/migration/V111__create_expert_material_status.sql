-- I1-1: 目录与顺序固定为 7 项，由后端常量定义，数据库不保存目录正文。
-- I1-2: 只保存 PROVIDED/DECLINED；缺行唯一解释为 PENDING（改回 PENDING 即删除该行）。
-- I1-3: 持久化状态域严格两值，由 CHECK 约束在数据库层拒绝非法值。
-- I1-4: 每个联系人每种材料至多一行，由唯一键 (expert_contact_id, material_code) 保证。
-- I1-9: 不 INSERT 任何联系人材料行；存量联系人自然解析为 7 项 PENDING。
-- 不声明 ON DELETE CASCADE，与现有 expert_contact 子表外键基线一致。
CREATE TABLE expert_material_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    material_code VARCHAR(32) NOT NULL,
    material_status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_expert_material_contact_code (expert_contact_id, material_code),
    CONSTRAINT chk_expert_material_code
        CHECK (material_code IN ('CV', 'PASSPORT', 'DEGREE', 'EMPLOYMENT', 'PUBLICATIONS', 'PATENTS', 'RESEARCH')),
    CONSTRAINT chk_expert_material_status
        CHECK (material_status IN ('PROVIDED', 'DECLINED')),
    CONSTRAINT fk_expert_material_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
