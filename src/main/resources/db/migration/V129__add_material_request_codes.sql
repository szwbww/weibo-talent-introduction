-- ============================================================================
-- V129 expert_material_status 目录代码域扩展（fast-p 02 材料索取五项）
--
-- 版本号说明：本迁移原为 V128，与并行合并的 batch-research-direction-filter 分支的
-- V128__add_research_direction_filter_to_batch_send_task_config.sql 撞号；后者已于
-- 2026-09-18 10:57 应用到生产（multi_ai_kit_schema_history V128 = 4521c9c8…），
-- 故本文件改号 V129；SQL 语义不变，只把 DROP 改为存在性守卫（见下）。
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
--
-- MySQL 兼容性（本次发布实测）：生产库为 MySQL 5.7.41，命名 CHECK 约束根本不落地
-- （V111 的 CHECK 被解析后忽略，生产 information_schema.TABLE_CONSTRAINTS 全库零 CHECK 行），
-- 而 `ALTER TABLE ... DROP CHECK` 是 8.0.16+ 语法，在 5.7 上直接 E1064 语法错误并中止发布。
-- 因此这里先用 TABLE_CONSTRAINTS（5.7/8.0 都有该视图）探测约束是否真实存在，
-- 仅在存在时动态执行 DROP；ADD CONSTRAINT ... CHECK 沿用 V36 的写法
-- （5.7 解析并忽略，8.0.16+ 真实生效）。
-- ============================================================================

SET @has_material_code_check := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'expert_material_status'
      AND CONSTRAINT_NAME = 'chk_expert_material_code'
      AND CONSTRAINT_TYPE = 'CHECK'
);

SET @drop_material_code_check := IF(
    @has_material_code_check > 0,
    'ALTER TABLE expert_material_status DROP CHECK chk_expert_material_code',
    'SELECT 1'
);

PREPARE drop_material_code_check_stmt FROM @drop_material_code_check;
EXECUTE drop_material_code_check_stmt;
DEALLOCATE PREPARE drop_material_code_check_stmt;

ALTER TABLE expert_material_status
    ADD CONSTRAINT chk_expert_material_code
        CHECK (material_code IN (
            'CV', 'PASSPORT', 'DEGREE', 'EMPLOYMENT', 'PUBLICATIONS', 'PATENTS', 'RESEARCH',
            'REQ_PUBLICATIONS', 'REQ_PROJECTS', 'REQ_PATENTS', 'REQ_AWARDS', 'REQ_DEGREES'
        ));
