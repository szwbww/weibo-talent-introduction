# 子计划 4 — 「AI 训练」前端 Tab + 历史邮件模拟回复

## 需求描述

- Observable outcome：后台侧栏新增「AI 训练」Tab，页面含三块：
  ① 提炼 QA 知识库列表（来源徽章区分 人工导入/自动提炼，分页）；
  ② AI 提示词与约束编辑（读写 `ai_prompt_config`）；
  ③ 历史邮件模拟回复（选专家 → 展示其来信 → 生成 DeepSeek 回复 → 可改提示词后重测），**只读旁路**。
- 必须不变：
  - 其它视图与 `setView/viewMeta/refreshCurrentView` 现有逻辑不破坏。
  - 现有 `aiReplyTurn`（人工工作台）接口/行为不变；模拟走**独立只读接口**。
  - 模拟不产生 `mail_record`、不进 QA 审计、不改会话状态。
- Out of scope：知识库行内编辑/删除（本期只读展示，若需要另开计划）、自动外发。

## 关键不变量

### Invariant I-1: 模拟为只读旁路
- Rule：模拟接口只读取历史邮件与配置/知识，调用 `AiReplyDraftService.generate(...)` 得到草稿并**直接返回**；
  绝不写 `mail_record`、`mail_record_qa_rule`、不改 `ExpertContact` 状态、不标记 inbound 已处理、不外发。
- Applies to：新增 `POST /api/ai-training/simulate`。
- Violation consequence：测试动作污染真实会话/审计。
- 来源：K-composed-reply-order-contract / K-rich-reply-qa-audit-reuse（G-4）

### Invariant I-2: 知识库注入走 prompt-only，不入发送
- Rule：模拟时把 `ai_training_qa` 知识作为 prompt 上下文注入（`AiReplyDraftService.generate` 的 `mailHistory`/额外上下文参数），
  返回的 `qaRuleIds` 若为空即空，前端**不得**据此走发送/组装台。
- Applies to：`simulate` 接口、前端模拟面板。
- Violation consequence：G-1 违背。
- 来源：K-ai-reply-prompt-vs-send-rule-ids（G-1）

### Invariant I-3: 前端视图注册契约
- Rule：新 Tab 必须同时满足：`index.html` 加 `.nav-tab[data-view="ai-training"]` 与 `<section class="view" id="view-ai-training">`；
  `app.js` 的 `viewMeta` 加 `ai-training` 条目、`refreshCurrentView()` 加 `if (state.view==="ai-training")` 分支。缺一即标题/加载异常。
- Applies to：`index.html`、`app.js`。
- Violation consequence：切 Tab 报错（`viewMeta[view]` undefined）或不加载。
- 来源：original（现状审计 setView/viewMeta）

## 现状审计

### 前端视图机制（`static/`）
- `index.html`：侧栏 `nav.nav-tabs` 内 `.nav-tab[data-view]`；主区每视图 `<section class="view" id="view-<name>">`。
- `app.js`：`setView(view)`（L1185）按 `data-view` 切 active、写 `viewMeta[view][0/1]` 到 `#viewTitle/#viewSubtitle`、调 `refreshCurrentView()`；
  `refreshCurrentView()`（L1203）按 `state.view` 分派 `loadXxx()`。`viewMeta` 为集中标题表（需新增条目）。
- 复用样式：`styles.css` 末段已有 `.ai-reply-section .ai-chat-panel`、`.ai-chat-messages`、`.ai-chat-bubble(.ai-chat-operator/.ai-chat-assistant)`、
  `.ai-chat-input` 等，可直接用于模拟面板；QA 列表可复用 `.panel/.table-wrap/table`、来源徽章用 `.badge.primary`（人工）/`.badge.ok`（自动）。
- `api(...)` 现有 fetch 封装（贯穿 app.js）复用。

### 后端接口（子计划 1/2 已建 + 本计划新增）
- 已有：`GET /api/ai-training/qa`（1）、`GET/PUT /api/ai-training/prompt-config`（2）。
- 新增：`GET /api/ai-training/simulate/experts`（可选历史专家下拉：有往来邮件的 contact 精简列表）、
  `POST /api/ai-training/simulate`（body: `{expertContactId, promptOverride?}` → 复用 `AiReplyDraftService.generate`，只读返回草稿）。
- 复用 `AiReplyDraftService.generate(inboundText, operatorTurns=[], expertProfile, mailHistory)`（现成，只读，无副作用）；
  `promptOverride` 仅用于本次调用的临时提示词（不落库）——通过给 `AiReplyDraftService` 传 `operatorInstruction` 或新增可选 `systemPromptOverride` 参数实现（优先用现有 `operatorInstruction`，避免改签名；若必须改则见变更清单 AiReplyDraftService 修改项）。

### Interaction points
- 模拟与 `UnmatchedInboundMailController.aiReplyTurn` 共用 `AiReplyDraftService` 与历史读法；模拟为无副作用调用，无写冲突（I-1）。

## 实现方案

### 阶段 A：模拟接口（遵守 I-1/I-2）
1. 修改 `llm/controller/AiTrainingController.kt`：
   - `GET /api/ai-training/simulate/experts?keyword=&limit=`：返回有往来邮件的专家精简列表（id/name/email/lastSubject）。只读。
   - `POST /api/ai-training/simulate`：取该 contact 最近 inbound 作为 `inboundText`、`buildMailHistory` 作为历史、
     `buildExpertProfile` 作为画像 → `AiReplyDraftService.generate(...)`（`operatorTurns=emptyList()`，
     `operatorInstruction = promptOverride`）→ 返回 `{draftText, usedLlm, mode, llmEnabled}`。**不做任何写操作**。
   - 复用 `UnmatchedInboundMailController` 里的 `buildExpertProfile/buildMailHistory` 私有逻辑：
     为避免跨 controller 复制，将这两个函数下沉为 `AiReplyContextBuilder`（新小工具类）供两处调用（**只读**）。
2. 新增 `llm/service/AiReplyContextBuilder.kt`：`buildExpertProfile(contact)`、`buildMailHistory(records)`（从现有私有函数迁移，纯只读拼装）。
3. 修改 `mail/controller/UnmatchedInboundMailController.kt`：改为调用 `AiReplyContextBuilder`（消除重复，行为不变）。

### 阶段 B：前端视图（遵守 I-3）
4. 修改 `static/index.html`：
   - 侧栏新增 `.nav-tab[data-view="ai-training"]`（放在「任务记录」附近，机器人/大脑 svg 图标）。
   - 主区新增 `<section class="view" id="view-ai-training">`：三面板（知识库列表 + 提示词编辑表单 + 模拟面板，复用 `ai-chat-*` 结构）。
5. 修改 `static/app.js`：
   - `viewMeta` 加 `"ai-training": ["AI 回复训练", "导入提炼 QA、配置提示词与约束，用历史邮件模拟 AI 回复效果"]`。
   - `refreshCurrentView()` 加 `if (state.view === "ai-training") await loadAiTraining();`。
   - 新增 `loadAiTraining()`：拉 `GET /api/ai-training/qa`（渲染列表 + 来源徽章 + 分页）与 `GET .../prompt-config`（填表单）。
   - 新增 保存提示词（`PUT`）、加载专家下拉（`GET .../simulate/experts`）、生成模拟（`POST .../simulate` 渲染气泡 + “重新生成/调整后再测”）。
6. 修改 `static/styles.css`：仅补少量 `#view-ai-training` 布局类（三面板栅格 + 来源徽章间距），尽量复用既有类。

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改（+2 模拟端点）|
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt` | 新增（只读上下文拼装）|
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改（改调用 builder，行为不变）|
| 4 | `src/main/resources/static/index.html` | 修改（nav-tab + view section）|
| 5 | `src/main/resources/static/app.js` | 修改（viewMeta/refreshCurrentView/loadAiTraining 等）|
| 6 | `src/main/resources/static/styles.css` | 修改（#view-ai-training 少量布局）|
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 新增（模拟只读、无 mail_record 写、无审计）|

文件数 7 ≤ 10。子系统：2（后端模拟接口 / 前端视图）——达上限，未超。

## 验收标准

- I-1：`AiTrainingSimulateTest` 调 `POST /simulate` 后断言 `mail_record`、`mail_record_qa_rule`、`expert_contact_status_history` 无新增行；inbound 未被标记已处理。
- I-2：模拟返回体不含发送用规则 id 语义误用；前端模拟面板无“发送/组装”入口。
- I-3：切到「AI 训练」Tab 无 JS 报错，标题=“AI 回复训练”，三面板渲染；其它 Tab 不受影响。
- 集成：LLM 关闭时模拟返回确定性兜底草稿（`usedLlm=false`）；LLM 开启（stub）时返回模型草稿；改提示词后重测，草稿随提示词变化。
- UI：列表来源徽章正确区分 MANUAL_IMPORT（蓝）/AUTO_EXTRACTED（绿）；分页可用。

## 修正记录

- **2026-07-01 fix-1（子计划 3 联动）**：`findExpertContactIdsWithInboundMail` SQL 改为 `GROUP BY mr.expert_contact_id ORDER BY MAX(mr.id) DESC`（见 `docs/plans/fix/03-scheduled-extraction/fix-1.md`）；子计划 4 的 `GET /simulate/experts` 依赖此只读查询。
- **2026-07-01 fix-1（本子计划）**：模拟接口改传 `simulateOnly=true`，`AiReplyDraftService` 在 LLM 关闭/失败时用训练知识确定性拼装兜底草稿，不再因空 `qaRuleIds` 返回空字符串（见 `docs/plans/fix/04-frontend-tab/fix-1.md`）。
