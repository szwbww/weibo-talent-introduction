-- ============================================================================
-- V128 expert_material_status 目录代码域扩展（fast-p 02 材料索取五项）
--
-- 只替换 chk_expert_material_code：
--   * 原样保留 V111 的旧 7 代码（CV / PASSPORT / DEGREE / EMPLOYMENT /
--     PUBLICATIONS / PATENTS / RESEARCH）—— 旧 7 项目录、${pendingExpertMaterials}
--     与 RAG 的 CV 读取依赖这些行继续合法；
--   * 追加 02 独立目录 5 代码（REQ_PUBLICATIONS / REQ_PROJECTS / REQ_PATENTS /
--     REQ_AWARDS / REQ_DEGREES）。
-- 不改唯一键 uk_expert_material_contact_code、不改状态 CHECK chk_expert_material_status
-- （存储态仍只有 PROVIDED/DECLINED，缺行仍是唯一 PENDING 形态），
-- 不 INSERT/UPDATE/DELETE 任何既有行，也不做新旧目录互相推断（I-1、I-2）。
-- ============================================================================

ALTER TABLE expert_material_status
    DROP CHECK chk_expert_material_code;

ALTER TABLE expert_material_status
    ADD CONSTRAINT chk_expert_material_code
        CHECK (material_code IN (
            'CV', 'PASSPORT', 'DEGREE', 'EMPLOYMENT', 'PUBLICATIONS', 'PATENTS', 'RESEARCH',
            'REQ_PUBLICATIONS', 'REQ_PROJECTS', 'REQ_PATENTS', 'REQ_AWARDS', 'REQ_DEGREES'
        ));
