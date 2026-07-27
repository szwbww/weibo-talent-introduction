# 子计划 2 — 可编辑的 AI 提示词与约束配置

## 需求描述

- Observable outcome：新增单行配置 `ai_prompt_config`，存放**自由回复系统提示词**与**约束项**；
  `AiReplyDraftService` 的 FREE_FORM 系统提示词改为**读该配置**，配置缺省时回退到现有硬编码默认（行为兼容）。
  提供 `GET/PUT /api/ai-training/prompt-config`（供子计划 4 前端编辑）。
- 必须不变：
  - `QA_MATCHED` 模式提示词与组装逻辑不变。
  - LLM 关闭 / 无配置时，FREE_FORM 输出与当前一致（回退默认）。
  - `LlmProperties`（apiUrl/model/temperature…）语义不变，本计划不动。
- Out of scope：知识库注入 prompt（放子计划 4 的模拟接口时统一处理）、前端页面、自动提炼。

## 关键不变量

### Invariant I-1: 配置单行 + 缺省回退
- Rule：`ai_prompt_config` 全局单行（`id=1`）；`AiReplyDraftService` 读取时若行缺失或字段空白，
  必须回退到现有 `buildBaseSystemPrompt/buildFreeFormSystemPrompt` 的硬编码文本。
- Applies to：`AiPromptConfigService.getEffective()`、`AiReplyDraftService.buildFreeFormSystemPrompt()`。
- Violation consequence：升级后未配置即导致空提示词，AI 输出退化。
- 来源：original

### Invariant I-2: 只改 FREE_FORM 系统提示词来源
- Rule：本计划仅把 **FREE_FORM 的系统提示词**来源从常量改为“配置优先 + 常量回退”；
  `QA_MATCHED` 分支、`buildMatchedUserContent` 逐段 verbatim 约束一律不动。
- Applies to：`AiReplyDraftService`。
- Violation consequence：破坏 K-ai-reply-prompt-vs-send-rule-ids 与逐段保真约束。
- 来源：K-ai-reply-prompt-vs-send-rule-ids

## 现状审计

### 新表 `ai_prompt_config`（本计划新建，单行）
- 无既有 schema。用 **V55** 迁移。domain data class + `CrudRepository`。
- Write paths（新增）：`V55` 建表并 `INSERT` 一行 `id=1`（字段留空→触发 I-1 回退）；`AiPromptConfigService.update(...)`（PUT）。
- Read paths（新增）：`AiPromptConfigService.getEffective()` ← ①`AiReplyDraftService`（FREE_FORM）②`AiTrainingController GET`。

### 既有硬编码提示词（本计划要改的读点）
- `llm/service/AiReplyDraftService.kt`：
  - `buildBaseSystemPrompt()`（L154-161）通用语气/语言/段数约束。
  - `buildFreeFormSystemPrompt()`（L174-179）自由回复专用。
  - `buildMatchedSystemPrompt()`（L163-172）**不动**。
  - 构造函数已注入多个依赖；本计划新增注入 `AiPromptConfigService`。
- Interaction points：`AiReplyDraftService` 被 `UnmatchedInboundMailController.aiReplyTurn`（L286）与子计划 4 的模拟接口共用；
  改提示词来源会同时影响“人工 AI 草稿”与“模拟回复”——这是期望行为（同一套提示词），但验收需覆盖“配置为空时人工草稿不变”。

### 约束项落地方式
- “约束”（禁止承诺费用/隐私不施压/优先引导视频会议/英文回复等）以**可编辑文本行**存储（`constraints TEXT`，每行一条），
  拼接进系统提示词尾部。不做成布尔开关枚举（避免与 create-p“最小字段”冲突且更灵活）。

## 实现方案

### 阶段 A：配置表与领域对象（遵守 I-1）
1. `V55__create_ai_prompt_config.sql`：建 `ai_prompt_config`
   - 列：`id BIGINT PK`（固定 1）、`free_form_system_prompt TEXT`、`constraints TEXT`（每行一条约束）、
     `updated_at DATETIME`。`INSERT (id) VALUES (1)`（其余 NULL → 回退默认）。
2. `llm/domain/AiPromptConfig.kt`：data class。
3. `llm/repository/AiPromptConfigRepository.kt`：`CrudRepository<AiPromptConfig, Long>`。

### 阶段 B：配置服务（遵守 I-1）
4. `llm/service/AiPromptConfigService.kt`：
   - `getRaw(): AiPromptConfig`（无则返回空对象）。
   - `getEffectiveFreeFormSystemPrompt(defaultPrompt: String): String`：配置非空则用配置 + 追加 constraints 行；否则返回 `defaultPrompt`。
   - `update(freeFormSystemPrompt: String?, constraints: String?)`：更新 id=1 行。
   - DTO `AiPromptConfigDto`。

### 阶段 C：接入 AiReplyDraftService（遵守 I-1/I-2）
5. 修改 `llm/service/AiReplyDraftService.kt`：
   - 构造注入 `aiPromptConfigService`。
   - `buildFreeFormSystemPrompt()` 改为：先构造现有默认文本 `default`，再
     `return aiPromptConfigService.getEffectiveFreeFormSystemPrompt(default)`。
   - `QA_MATCHED` 分支与其它方法保持原样。

### 阶段 D：读写 API（前端在子计划 4 消费）
6. 修改 `llm/controller/AiTrainingController.kt`（子计划 1 已建）：新增
   `GET /api/ai-training/prompt-config` 与 `PUT /api/ai-training/prompt-config`（body=`AiPromptConfigDto`）。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V55__create_ai_prompt_config.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/domain/AiPromptConfig.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/repository/AiPromptConfigRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiPromptConfigService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改（仅 FREE_FORM 提示词来源 + 注入）|
| 6 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改（+2 端点）|
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改（补回退 + 配置生效用例）|

文件数 7 ≤ 10。子系统：1（llm 配置层，含对 AiReplyDraftService 的窄修改）。

## 验收标准

- I-1（回退）：`ai_prompt_config` 字段为空时，`buildFreeFormSystemPrompt()` 输出与旧硬编码逐字相同（新增单测断言）。
- I-1（生效）：配置写入自定义提示词 + 2 条约束后，FREE_FORM 系统提示词包含自定义正文与两条约束行。
- I-2：`QA_MATCHED` 相关测试（现有 `AiReplyDraftServiceTest`）全绿，未因本改动变化。
- 集成：`PUT` 后 `GET /api/ai-training/prompt-config` 回读一致；`UnmatchedInboundMailController.aiReplyTurn` 在配置为空时输出与改动前一致。
