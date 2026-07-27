# AI 回复训练 Tab — 总体分解方案（Overview）

> 生成方式：create-p。本文件是**分解入口**，不含可执行任务；每个子计划单独可部署、单独验收。
> 目标特性：新增「AI 训练」Tab，用于训练 / 调试 DeepSeek 自由回复能力。

## 特性目标（用户可见结果）

1. 后台新增「AI 训练」侧边栏 Tab。
2. **默认导入** `docs/qa提炼-完整版.md` 的提炼 QA 作为知识库种子（随启动自动导入，**不做成按钮**）。
3. 定时提炼历史专家对话，持续补充 QA 知识库（可区分“人工导入” vs “自动提炼”来源）。
4. 页面可查看提炼后的 QA 记录。
5. 页面可查看 / 编辑喂给 AI 的**提示词与约束**。
6. 页面可选历史专家邮件，让 AI **模拟回复**，用于验证提示词调整是否有效（只读，不外发、不落审计）。

## 为什么必须分解

create-p 硬性限制：单计划 ≤10 文件、≤2 子系统、每个共享 store 最多 +1 字段。本特性天然横跨
**新数据表 + 提示词配置 + 定时提炼 + 前端页面 + 模拟接口** 五块，超限。按“最小可交付切片”拆为 4 个顺序子计划：

| 序 | 子计划 | 交付的独立价值 | 依赖 |
|----|--------|----------------|------|
| 1 | `01-qa-knowledge-store-and-seed.md` | `ai_training_qa` 表 + 启动默认导入 md + 只读列表 API | 无 |
| 2 | `02-prompt-config.md` | `ai_prompt_config` 单行可编辑配置，接入 `AiReplyDraftService` 自由回复提示词 | 无（可与 1 并行，但建议顺序）|
| 3 | `03-scheduled-extraction.md` | 定时从历史邮件提炼 QA 写入 `ai_training_qa`（source=AUTO_EXTRACTED）| 依赖 1 |
| 4 | `04-frontend-tab.md` | 「AI 训练」前端视图（知识列表 + 提示词编辑 + 历史邮件模拟回复）+ 模拟接口 | 依赖 1、2；模拟展示 3 的产物 |

顺序：**1 → 2 → 3 → 4**。1、2 是数据/配置地基；3 依赖 1 的表；4 依赖 1/2 的 API 并展示 3 的成果。

## 现有可复用资产（Phase 1b 审计摘要，各子计划复用）

- **DeepSeek 链路已存在**：`config/LlmProperties.kt`（`enabled/apiUrl/apiKey/model/timeoutMs/temperature/freeFormTemperature`）、
  `config/LlmClientConfig.kt`（`llmRestTemplate` 已接 connect/read timeout，来源: K-llm-timeout-fallback）、
  `llm/service/HttpLlmDraftClient.kt`（OpenAI 兼容 `chat(messages, temperature)`，DeepSeek 直接兼容）。
  application.yml 已有 `talent-introduction.llm.*`（当前 `model` 默认 `gpt-4o-mini`，DeepSeek 场景改 env 即可）。
- **AI 自由回复服务已存在**：`llm/service/AiReplyDraftService.kt`，含 `FREE_FORM`/`QA_MATCHED` 双模式，
  **系统提示词目前硬编码**于 `buildBaseSystemPrompt/buildFreeFormSystemPrompt`（子计划 2 将其改为读 DB 配置 + 回退默认）。
- **QA 现有表 `qa_rule`**：关键词匹配自动回复规则，`QaRule.kt`；**与本特性的“提炼 QA 知识库”职责不同**，不复用同一张表。
- **调度模式**：`task/service/MailAutomationScheduler.kt`（`@ConditionalOnProperty scheduling.enabled`，
  cron 走 `MailSchedulingProperties`，统一 `TaskExecutionService.runAndRecord(...)` 记审计）。
- **历史邮件读路径**：`MailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)`
  （`UnmatchedInboundMailController.buildMailHistory` 已在用）。
- **前端视图机制**：`static/index.html` 侧栏 `.nav-tab[data-view]` + `<section class="view" id="view-<name>">`；
  `static/app.js` 的 `viewMeta`、`setView(view)`、`refreshCurrentView()` 负责标题/激活/加载。
  已有 `ai-reply-section` / `ai-chat-*` 样式（`styles.css` 末段）可复用于模拟面板。

## 跨子计划共享不变量（各子计划在本地 `## 关键不变量` 中按需引用）

### Invariant G-1: 提炼 QA 知识库与发送用 QA 规则严格分离
- Rule: `ai_training_qa` 仅作为 **LLM 自由回复的 prompt 知识**，**永不**进入发送用 `qaRuleIds`、`mail_record_qa_rule`、QA 审计。
- Applies to: 子计划 1（建表/导入）、3（自动提炼写入）、4（模拟接口读取）。
- Violation consequence: 无关知识被当作命中规则写入审计关联表，污染人工组装台与审计报表。
- 来源: K-ai-reply-prompt-vs-send-rule-ids

### Invariant G-2: 默认导入是幂等启动种子，不是按钮
- Rule: `docs/qa提炼-完整版.md` 的提炼结果以**随应用启动的幂等 seeder** 导入；重复启动不得产生重复行；前端无“导入”按钮。
- Applies to: 子计划 1。
- Violation consequence: 每次重启翻倍数据 / 违背用户明确要求（不要按钮）。
- 来源: original（用户明确要求）

### Invariant G-3: LLM 链路必须真超时并静默回退
- Rule: 所有新增 LLM 调用必须复用 `llmRestTemplate`（已接 timeout），异常/超时返回 null 并走确定性兜底，不得阻塞。
- Applies to: 子计划 3（提炼）、4（模拟）。
- Violation consequence: 慢请求阻塞调度线程 / 前端请求。
- 来源: K-llm-timeout-fallback

### Invariant G-4: 模拟回复为只读旁路
- Rule: 模拟回复**不得**落 `mail_record`、不得进 QA 审计、不得标记 inbound 已处理、不得外发。
- Applies to: 子计划 4。
- Violation consequence: 测试动作污染真实会话状态与审计。
- 来源: K-composed-reply-order-contract / K-rich-reply-qa-audit-reuse

## 明确不做（Out of scope，全特性级）

- 不改造 `qa_rule` 关键词匹配自动回复链路。
- 不让 AI 自动外发（仍是人工确认；本特性只到“模拟/草稿”）。
- 不引入向量检索 / embedding（知识库先做结构化行存 + 全量/分类喂 prompt）。
- 不替换现有 `AiReplyDraftService` 的 `QA_MATCHED` 组装逻辑（只扩 FREE_FORM 提示词来源）。
- 不做多语言 UI，沿用中文后台。
