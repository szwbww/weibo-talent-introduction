-- ============================================================================
-- V115 rag_prompt_config（plan 06: 06-prompt-console.md）
--
-- 新链路两次 LLM 调用的约束清单（检索 5 条 / 生成 22 条）的可编辑配置表。
-- 单行表（id=1，由迁移插入），存「运营自定义」的约束 JSON 数组（字符串列表）：
--
--   I-30  retrieval_constraints / generation_constraints 为 NULL 时，服务端
--         （RagPromptConfigService.effective()）回落 03 的 RagPromptConstraints
--         常量，逐字相同、isCustom=false；「全部恢复默认」= 把两列置 NULL，
--         绝不写入一份默认快照（否则 03 改常量后两处默认值悄悄分叉）。
--   I-31  生成调用的第 18/19/21 条为派生只读，由 rag_mandatory_rule 现算，
--         不落本表；本表 generation_constraints 只存其余 19 条（22 − 3）。
--   I-32  存储条目为纯文本数组，不含 no / index 编号字段 —— 页面编号是渲染产物。
--   I-33  每次保存/恢复由 RagPromptConfigService 写 operator_action_log 审计
--         （改动下标 + 改前/改后值、新增、删除、操作人、时间），本表不另设审计表。
--
-- updated_at 由 MySQL ON UPDATE CURRENT_TIMESTAMP 自动维护；
-- updated_by 记最近一次保存/恢复的操作人（I-33 的 operator 冗余列）。
-- ============================================================================
CREATE TABLE rag_prompt_config (
    id                     BIGINT      NOT NULL COMMENT '单行配置主键（恒为 1）',
    retrieval_constraints  TEXT        NULL COMMENT '检索调用约束 JSON 数组；NULL = 用代码默认值（I-30）',
    generation_constraints TEXT        NULL COMMENT '生成调用约束 JSON 数组（不含派生三条，I-31）；NULL = 用代码默认值（I-30）',
    updated_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by             VARCHAR(64) NULL COMMENT '最近一次保存/恢复的操作人（I-33）',
    PRIMARY KEY (id),
    CONSTRAINT chk_rag_prompt_config_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 提示词约束配置：两列 NULL 时回落 RagPromptConstraints 常量；恢复默认是置 NULL 而非写默认快照（I-30）';

INSERT INTO rag_prompt_config (id) VALUES (1);
