# 子计划 3 — 定时从历史对话提炼 QA

## 需求描述

- Observable outcome：新增定时任务，周期性读取历史专家往来邮件，调用 DeepSeek 把对话蒸馏为
  QA 条目，写入 `ai_training_qa`（`source="AUTO_EXTRACTED"`）；执行走 `TaskExecutionService.runAndRecord` 记审计。
- 必须不变：
  - 现有三条 `@Scheduled`（auto-reply-all / initial-outreach / operator-status-sync）行为不变。
  - `mail_record` 只读；不改会话状态、不标记已处理、不外发。
  - LLM/scheduling 关闭时该任务不注册、不执行（默认关闭）。
- Out of scope：前端展示（子计划 4）、手动触发按钮（本期不做，纯定时；如需手动触发留待子计划 4 评估）。

## 关键不变量

### Invariant I-1: 提炼产物只入知识库
- Rule：提炼结果只 `INSERT` 进 `ai_training_qa`（source=AUTO_EXTRACTED），永不进 `qa_rule` / `mail_record_qa_rule` / 发送链路。
- Applies to：`AiQaExtractionService`。
- Violation consequence：G-1 违背，污染审计。
- 来源：K-ai-reply-prompt-vs-send-rule-ids（G-1）

### Invariant I-2: mail_record 只读
- Rule：提炼只调用 `MailRecordRepository` 读方法；不得调用任何写/状态变更/标记已处理。
- Applies to：`AiQaExtractionService`。
- Violation consequence：破坏收信处理写路径契约。
- 来源：K-inbound-processing-write-paths / K-backfill-readonly-inbound

### Invariant I-3: LLM 真超时 + 静默回退 + 双开关
- Rule：调用复用 `HttpLlmDraftClient.chat`（底层 `llmRestTemplate` 已接 timeout）；异常/超时/空返回时该批次跳过，不抛出中断调度；
  任务注册需同时满足 `scheduling.enabled=true` 且运行时 `llmProperties.enabled=true`（关闭则空跑返回）。
- Applies to：`AiQaExtractionScheduler`、`AiQaExtractionService`。
- Violation consequence：慢/失败的 LLM 阻塞或中断调度线程。
- 来源：K-llm-timeout-fallback（G-3）

### Invariant I-4: 自动提炼去重
- Rule：以每个专家会话为提炼单元，`source_ref` = 稳定会话键（如 `contact:{contactId}`）；
  同 `(AUTO_EXTRACTED, source_ref)` 已存在则更新该行而非新增（避免每次调度翻倍）。
- Applies to：`AiQaExtractionService`。
- Violation consequence：G-2 类重复膨胀。
- 来源：original（G-2）

## 现状审计

### `ai_training_qa`（子计划 1 已建）
- Write paths：本计划新增 `AiQaExtractionService.upsertAuto(...)`（source=AUTO_EXTRACTED，按 I-4 upsert）。
- 复用子计划 1 的 `AiTrainingQaRepository.findBySourceRefAndSource(...)` 做 upsert 判定（若缺则本计划补该方法——但子计划 1 已含）。

### 调度基座 `task/service/MailAutomationScheduler.kt`
- `@ConditionalOnProperty(prefix="talent-introduction.scheduling", name=["enabled"])`；
  cron 从 `MailSchedulingProperties` 注入；统一 `taskExecutionService.runAndRecord(type, trigger, req){...}`。
- `config/MailSchedulingProperties.kt` 持有各 cron/参数（本计划新增一个 cron 字段）。
- 决策：**不**把新任务塞进 `MailAutomationScheduler`（它聚合的是发信/收信自动化，语义不同且会加大其依赖面）。
  新建独立 `AiQaExtractionScheduler`，同样 `@ConditionalOnProperty(scheduling.enabled)`，自带 cron 属性。

### 历史邮件读路径
- `MailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`（已被 `UnmatchedInboundMailController.buildMailHistory` 使用，只读）。
- 需要“有往来的专家会话”列表：审计 `ExpertContactRepository` 现有查询；若无“有邮件的 contactId”批量查法，
  提炼服务分页遍历 `MailRecordRepository` 去重 contactId（读侧，规模可控；实现细节在执行期按实际 repo 方法定，禁止“and related”——见变更清单已锁定 repo 文件为只读，不改）。
- Interaction points：与子计划 4 的模拟共用同一历史读法；本计划只读，无写冲突。

### 配置
- `application.yml` `talent-introduction.scheduling.*`（新增 `ai-qa-extraction-cron`）与 `talent-introduction.llm.*`（复用）。
- `config/MailSchedulingProperties.kt` 新增 `aiQaExtractionCron` 字段（或独立属性类；为省文件，复用该属性类新增一字段）。

## 实现方案

### 阶段 A：提炼服务（遵守 I-1/I-2/I-3/I-4）
1. `llm/service/AiQaExtractionService.kt`：
   - `extractBatch(maxContacts: Int): ExtractionSummary`
     - 若 `!llmProperties.enabled` → 直接返回空 summary（I-3 双开关）。
     - 选取近 N 个“有往来邮件”的 contactId（只读，去重）。
     - 每个 contact：用 `buildMailHistory(findAllByExpertContactIdOrderByCreatedAtAsc)` 组装对话文本；
       调 `HttpLlmDraftClient.chat`（专用提炼 system prompt：要求输出结构化 QA JSON 数组）→ 解析。
     - LLM 空/异常 → 跳过该 contact（I-3），累加 skipped。
     - 解析出的每条按 `source_ref="contact:{id}"` upsert（I-4），`source="AUTO_EXTRACTED"`。
   - 返回 `ExtractionSummary(processed, upserted, skipped)`。
   - 复用子计划 2 的模式：提炼用 system prompt 先内置常量（本期不做成可配置，避免再加字段）。

### 阶段 B：调度 + 配置（遵守 I-3）
2. `llm/service/AiQaExtractionScheduler.kt`：
   - `@Service @ConditionalOnProperty(prefix="talent-introduction.scheduling", name=["enabled"], havingValue="true")`。
   - `@Scheduled(cron="\${talent-introduction.scheduling.ai-qa-extraction-cron:-}")` →
     `taskExecutionService.runAndRecord("AI_QA_EXTRACTION","SCHEDULED", req){ aiQaExtractionService.extractBatch(...) }`。
3. 修改 `config/MailSchedulingProperties.kt`：新增 `aiQaExtractionCron: String = "-"`、`aiQaExtractionMaxContacts: Int = 20`。
4. 修改 `src/main/resources/application.yml`：`scheduling:` 下新增
   `ai-qa-extraction-cron: ${MAIL_SCHEDULING_AI_QA_EXTRACTION_CRON:-}`、
   `ai-qa-extraction-max-contacts: ${MAIL_SCHEDULING_AI_QA_EXTRACTION_MAX_CONTACTS:20}`。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionService.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionScheduler.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/MailSchedulingProperties.kt` | 修改（+2 字段）|
| 4 | `src/main/resources/application.yml` | 修改（+2 配置项）|
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionServiceTest.kt` | 新增（LLM stub：回退跳过、upsert 去重、source=AUTO_EXTRACTED、mail_record 只读）|

文件数 5 ≤ 10。子系统：1（llm 提炼 + 调度）。

## 验收标准

- I-1：单测断言提炼产物 `source="AUTO_EXTRACTED"` 且未触达任何 `qa_rule`/审计写方法（mock 校验无交互）。
- I-2：mock `MailRecordRepository` 只验证读方法被调用，写方法零调用。
- I-3：`llmProperties.enabled=false` 时 `extractBatch` 立即返回空 summary；LLM 抛异常时该 contact 记 skipped、不冒泡。
- I-4：对同一 contact 连续两次 `extractBatch`，`ai_training_qa` 中 `source_ref="contact:{id}"` 行数不增（upsert）。
- 集成：设 `ai-qa-extraction-cron` + `llm.enabled=true`（stub client）跑一次，`GET /api/ai-training/qa?source=AUTO_EXTRACTED` 出现新条目；`TaskExecution` 有 `AI_QA_EXTRACTION` 审计行。

## 修正记录

- **2026-07-01 fix-1**：`findExpertContactIdsWithInboundMail` 由 `SELECT DISTINCT ... ORDER BY mr.id` 改为 `GROUP BY mr.expert_contact_id ORDER BY MAX(mr.id) DESC`，并加 `expert_contact_id IS NOT NULL`；原因：MySQL 8 语义错误 + fix-v P1-1（见 `docs/plans/fix/03-scheduled-extraction/fix-1.md`）。
