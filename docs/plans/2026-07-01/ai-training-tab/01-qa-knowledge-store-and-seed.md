# 子计划 1 — 提炼 QA 知识库表 + 默认导入 md

## 需求描述

- Observable outcome：新增 `ai_training_qa` 表存放“提炼 QA 知识”；应用启动时**幂等地**把
  `docs/qa提炼-完整版.md` 第二部分的可复用 QA 规则导入为种子行（source=MANUAL_IMPORT）；
  提供只读 REST `GET /api/ai-training/qa`（分页 + 按 source 过滤）供后续前端消费。
- 必须不变：
  - `qa_rule` 表及其匹配/审计链路完全不动。
  - 现有 `AiReplyDraftService` 行为不变（本子计划不接入 prompt）。
- Out of scope：前端页面（子计划 4）、自动提炼（子计划 3）、写接口/编辑（子计划 4 再评估）、提示词配置（子计划 2）。

## 关键不变量

### Invariant I-1: 独立知识表，永不进发送链路
- Rule：`ai_training_qa` 与 `qa_rule` 无外键、无同步；其行**永不**转换为发送用 `qaRuleIds` 或写入 `mail_record_qa_rule`。
- Applies to：建表迁移、seeder、`AiTrainingQaService`。
- Violation consequence：污染 QA 审计（见 G-1）。
- 来源：K-ai-reply-prompt-vs-send-rule-ids（G-1）

### Invariant I-2: 导入幂等
- Rule：seeder 以稳定业务键去重（`source_ref` = md 中的规则主题标识，如 `VCR_VIDEO`/`SINGLE_APPLICATION`…）；
  已存在同 `(source, source_ref)` 的行跳过，不更新、不重复插入。重复启动行数不变。
- Applies to：`AiTrainingQaSeeder`。
- Violation consequence：每次重启数据翻倍（见 G-2）。
- 来源：original（G-2）

### Invariant I-3: source 为受控枚举
- Rule：`source` 仅允许 `MANUAL_IMPORT` | `AUTO_EXTRACTED`（本子计划只产生 `MANUAL_IMPORT`）。
- Applies to：建表（约定值）、seeder、查询过滤。
- Violation consequence：来源徽章无法区分人工/自动。
- 来源：original

## 现状审计

### 新表 `ai_training_qa`（本计划新建）
- 无既有 schema。DB 为 MySQL + Spring Data JDBC，domain 为不可变 `data class` + `@Table`/`@Id`，repo 继承 `CrudRepository`。
- Flyway 目录 `src/main/resources/db/migration`，最新为 `V53__inbound_mail_tag.sql`；本计划用 **V54**。
- Write paths（本计划新增，全部受 I-1/I-2 约束）：
  1. `V54__create_ai_training_qa.sql` — 建表 + 索引。
  2. `AiTrainingQaSeeder`（`ApplicationRunner`）— 启动幂等插入 MANUAL_IMPORT 种子。
- Read paths（本计划新增）：
  1. `AiTrainingQaService.list(...)` ← `AiTrainingQaController GET /api/ai-training/qa`。
- Interaction points：子计划 3 将新增 write path（AUTO_EXTRACTED）；子计划 4 新增 read path（前端 + 模拟）。本表字段设计需预留这两者，但本计划不实现。

### 种子来源 `docs/qa提炼-完整版.md`
- 现状是 `docs/` 下文档，**不在 classpath**。第二部分“归纳为可复用 QA 规则”是 A（命中现有规则，11 行）+ B（建议新增，12 行）两张 markdown 表。
- 直接在启动时解析 markdown 表格脆弱（表格列/中英文混排）。**决策**：把第二部分蒸馏为结构化 JSON 种子资源
  `src/main/resources/ai-training/qa-seed.json`（随代码维护），seeder 读该 classpath 资源导入。md 仍为人读文档。

### 既有参考实现（复用范式，不修改）
- `qa/domain/QaRule.kt`（domain 范式）、`qa/repository/QaRuleRepository.kt`（repo 范式）、
  `qa/service/QaRuleManagementService.kt`（service+DTO 范式）、`qa/controller/QaRuleManagementController.kt`（controller 范式）。

## 实现方案

### 阶段 A：数据表与领域对象（遵守 I-1/I-3）
1. `V54__create_ai_training_qa.sql`：建 `ai_training_qa`
   - 列：`id BIGINT PK AUTO_INCREMENT`、`topic VARCHAR(255)`（问题/主题）、`question TEXT`、`answer TEXT`（标准回复要点）、
     `keywords VARCHAR(512)`（草拟触发词，逗号分隔，仅供人读/未来用）、`source VARCHAR(32) NOT NULL`、
     `source_ref VARCHAR(128)`（业务去重键；AUTO 可空/用会话 id）、`enabled TINYINT(1) NOT NULL DEFAULT 1`、
     `created_at DATETIME`、`updated_at DATETIME`。
   - 唯一索引 `uk_ai_training_qa_source_ref (source, source_ref)`（支撑 I-2 幂等；`source_ref` 可空由 seeder 保证非空）。
2. `llm/domain/AiTrainingQa.kt`：对应不可变 data class（字段同上）。
3. `llm/repository/AiTrainingQaRepository.kt`：`CrudRepository<AiTrainingQa, Long>` +
   `findBySourceRefAndSource(sourceRef, source): AiTrainingQa?`、`findAllByOrderByCreatedAtDesc()`（分页在 service 层截断）。

### 阶段 B：种子资源与幂等 seeder（遵守 I-2/I-3，G-2）
4. `src/main/resources/ai-training/qa-seed.json`：数组，每项 `{topic, question, answer, keywords, sourceRef}`，
   由 `docs/qa提炼-完整版.md` 第二部分 A+B 蒸馏（约 20+ 条，`sourceRef` 用大写下划线稳定键：
   `PROJECT_CONTENT / APPLYING_CRITERIA / VCR_VIDEO / SINGLE_APPLICATION / MEETING_SCHEDULE / CONFIDENTIAL_NO_FEE / ...`）。
5. `llm/service/AiTrainingQaSeeder.kt`（`ApplicationRunner`）：
   - 读 classpath `ai-training/qa-seed.json`（Jackson）。
   - 对每条：`findBySourceRefAndSource(sourceRef, "MANUAL_IMPORT")` 命中则 skip；否则插入 `source="MANUAL_IMPORT"`。
   - 记录 `log.info("ai_training_qa seed: inserted=X skipped=Y")`。全程 try/catch 不阻断启动。

### 阶段 C：只读查询 API（遵守 I-1）
6. `llm/service/AiTrainingQaService.kt`：`list(source: String?, page: Int, size: Int): AiTrainingQaPage`
   （内存分页足够，数据量小）+ DTO `AiTrainingQaDto`、`AiTrainingQaPage(items, total, page, size)`。
7. `llm/controller/AiTrainingController.kt`：`GET /api/ai-training/qa?source=&page=&size=` → `AiTrainingQaPage`。
   （子计划 2/4 会往同一 controller 加端点；本计划只加此只读端点。）

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V54__create_ai_training_qa.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/domain/AiTrainingQa.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/repository/AiTrainingQaRepository.kt` | 新增 |
| 4 | `src/main/resources/ai-training/qa-seed.json` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaSeeder.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaService.kt` | 新增 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingQaSeederTest.kt` | 新增（幂等测试）|

文件数 8 ≤ 10。子系统：1（llm/ai-training 数据层）。

## 验收标准

- I-1：全仓 grep 确认 `ai_training_qa`/`AiTrainingQa` 不出现在 `mail_record_qa_rule`、`QaRuleAuditService`、发送 `qaRuleIds` 相关路径。
- I-2：`AiTrainingQaSeederTest` 连续 run seeder 两次，断言表行数第二次不变（inserted=0）。
- I-3：seeder 插入行 `source` 全为 `MANUAL_IMPORT`；`GET /api/ai-training/qa?source=MANUAL_IMPORT` 返回全部种子。
- 集成：启动后 `GET /api/ai-training/qa` 返回 ≥20 条种子，字段完整（topic/answer/keywords）。
