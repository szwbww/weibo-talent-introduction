# AI 训练页面重构

## 需求描述

**可观察结果：**
1. 「历史邮件模拟回复」面板改为按**专家标签**（ES `tags` 字段）和**回信标签**（`inbound_mail_tag` 表）筛选邮件列表；点击邮件直接展示完整正文，不再需要「搜索专家 → 选择下拉 → 生成模拟回复」三步。
2. 「AI 提示词与约束」面板打开时默认显示系统当前生效的提示词和约束全文（即 `AiReplyDraftService.buildFreeFormSystemPrompt()` 构造的默认值），而非空白 placeholder。
3. 整体样式优化：三面板改为顶部 tab 切换（QA 知识库 / 提示词与约束 / 模拟回复），模拟回复改为左右分栏（邮件列表 | 正文+模拟），提示词改为双栏并排。
4. QA 知识库新增两条高频专家问题条目（中介角色定位、信息来源渠道），并支持运营通过 UI 手动添加/编辑/删除条目。

**必须不变：**
- QA 知识库面板已有功能不变（来源筛选、分页、刷新）
- 提示词保存逻辑不变（PUT `/api/ai-training/prompt-config`）
- 模拟回复生成逻辑不变（POST `/api/ai-training/simulate`）
- 已有的 `loadAiTraining()` 入口不变（来源: K-view-registration-triad）
- QA 自动提炼逻辑（`AiQaExtractionService`）不变

**不在范围：**
- 不改 QA 自动提炼/LLM 抽取逻辑
- 不改 AI 回复生成链路（`AiReplyDraftService`）
- 不改邮件发送/接收链路
- 不动数据库 migration

## 关键不变量

### Invariant I-1: 模拟回复仍然通过 contactId 调用
- Rule: 前端选中邮件后，模拟回复仍然通过 `POST /api/ai-training/simulate { expertContactId }` 调用。新的邮件列表 API 必须返回 `expertContactId` 以供前端传递。
- Applies to: 新增的邮件列表 API、前端 simulate 调用
- Violation consequence: 模拟回复无法工作
- 来源: original

### Invariant I-2: 回信标签筛选复用已有 `inbound_mail_tag` 查询模式
- Rule: 回信标签筛选采用与 `InboundMailSummaryController.listMails()` 相同的 `tagKey` 解析模式（`qa:<ruleId>` / `custom:<label>`），通过 `inbound_mail_tag` 表 EXISTS 子查询实现。不新建索引，不改表结构。
- Applies to: 新增的 `AiTrainingController.listSimulateMails()` 及其 repository 查询
- Violation consequence: 标签筛选不一致或查询失败
- 来源: original

### Invariant I-3: 专家标签筛选通过 ES tags 字段 + expert_contact.orcid_id 关联
- Rule: 专家标签是 ES `tags` 字段（keyword array）。按专家标签筛选邮件需要先从 ES 查出匹配的 `orcidId` 列表，再通过 `expert_contact.orcid_id` 关联到 `mail_record`。这是跨存储查询（ES → MySQL），不能在单个 SQL 中完成。
- Applies to: 新增的后端 API `/api/ai-training/simulate/mails`
- Violation consequence: 专家标签筛选无效
- 来源: original

### Invariant I-4: 默认提示词由 AiReplyDraftService 生成，前端只读展示
- Rule: 当 `AiPromptConfigService.getRaw().freeFormSystemPrompt` 为空时，系统使用 `AiReplyDraftService.buildFreeFormSystemPrompt()` 的硬编码默认值。新增 API 返回有效提示词（不管自定义还是默认），前端直接展示。不在前端硬编码默认值。
- Applies to: 新增 `GET /api/ai-training/prompt-config/effective`、前端 `fillAiTrainingPromptForm`
- Violation consequence: 前后端默认提示词不一致
- 来源: K-free-form-fallback-nonempty

### Invariant I-5: 四联注册契约
- Rule: AI 训练视图已经完成四联注册（nav-tab、view section、viewMeta、refreshCurrentView），本次只修改 view section 内容和 JS 逻辑，不改注册入口。
- Applies to: index.html `#view-ai-training`、app.js `loadAiTraining()`
- Violation consequence: 页面切换报错或不加载
- 来源: K-view-registration-triad

### Invariant I-6: QA 手动条目与种子/自动提炼条目共存
- Rule: 手动添加的 QA 条目 `source=MANUAL_IMPORT`, `sourceRef` 为 `MANUAL:<timestamp>` 或运营自定义。与种子数据（`AiTrainingQaSeeder` 按 `sourceRef` 去重）和自动提炼（`source=AUTO_EXTRACTED`）互不干扰。`buildKnowledgeContext()` 读取所有 `enabled=true` 的条目——手动条目自动参与 AI 上下文。
- Applies to: 新增 `POST /api/ai-training/qa`、`PUT /api/ai-training/qa/{id}`、`DELETE /api/ai-training/qa/{id}`；种子 JSON 新增条目
- Violation consequence: 手动添加的条目被种子覆盖或不参与 AI 上下文
- 来源: original

### Invariant I-7: 种子去重逻辑——按 source+sourceRef 幂等
- Rule: `AiTrainingQaSeeder` 启动时按 `findBySourceAndSourceRef(MANUAL_IMPORT, sourceRef)` 判重。新增的两条种子条目必须使用唯一的 `sourceRef`（`MEDIATOR_ROLE`, `HOW_FOUND_ME`）。运营手动添加的条目使用不同的 `sourceRef` 模式，不会被种子覆盖。
- Applies to: `qa-seed.json` 新增条目、`AiTrainingQaSeeder`
- Violation consequence: 重启后条目被重复插入或意外覆盖
- 来源: original

## 现状审计

### mail_record 表（MySQL）
- Schema: id, expert_contact_id, direction, mail_type, message_id, subject, body, cleaned_body, received_at, ...
- Write paths:
  1. `SmtpMailDeliveryService` — 写 OUTBOUND 记录
  2. `AutoMailReplyService.processSingleInbound()` — 写 INBOUND 记录
  3. `InitialOutreachService` — 写 INTRODUCTION OUTBOUND 记录
- Read paths (与本计划相关):
  1. `MailRecordRepository.findExpertContactIdsWithInboundMail()` — 当前模拟面板按关键字查有来信的 contactId
  2. `MailRecordRepository.findLatestInboundByExpertContactId()` — 获取最新来信
  3. `MailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc()` — 获取全部通信记录
- Interaction points: 新增查询需要 JOIN `expert_contact` 和 `inbound_mail_tag`（通过 `mail_record.source_inbound_id` 或 `inbound_mail_processing.expert_contact_id` 关联）

### inbound_mail_tag 表（MySQL）
- Schema: id, inbound_processing_id, tag_type(QA/CUSTOM), qa_rule_id, label, source, created_by, created_at
- Write paths:
  1. `InboundMailTagService.autoApplyQaTags()` — 自动回复时打标
  2. `InboundMailTagService.addQaTag()` — 手动打 QA 标签
  3. `InboundMailTagService.addCustomTag()` — 手动打自定义标签
- Read paths (与本计划相关):
  1. `InboundMailTagService.stats()` — 聚合标签统计（已有 `/api/inbound-summary/tags/options`）
  2. `InboundMailProcessingRepository.listInboundSummary()` — EXISTS 子查询按标签筛选（模式可复用）
- Interaction points: 新增查询复用 EXISTS 模式，不新增写入

### expert_contact 表（MySQL）
- Schema: id, orcid_id, expert_email, expert_name, current_status, ...
- Read paths (与本计划相关):
  1. `ExpertContactRepository.findAllById()` — 根据 id 批量查 contact 信息
- Interaction points: 需要通过 `orcid_id` 关联 ES 的专家标签

### ES CANDIDATE/APPLICATION 索引 — `tags` 字段
- Write paths:
  1. `ExpertIndexWriterService.addTag()` — 添加标签
  2. `ExpertIndexWriterService.removeTag()` — 移除标签
- Read paths (与本计划相关):
  1. `ExpertSearchService.aggregateTags()` — 聚合所有标签（已有 `/api/experts/tags/aggregation`）
  2. `ExpertSearchService.search()` — 按 tag 筛选搜索
- Interaction points: 需新增按 tag 搜索返回 orcidId 列表的方法或复用 `search()` 的 `tag` 参数

### ai_training_qa 表（MySQL）
- Schema: id, topic, question, answer, keywords, source(MANUAL_IMPORT/AUTO_EXTRACTED), source_ref, enabled, created_at, updated_at
- Write paths:
  1. `AiTrainingQaSeeder.run()` — 启动时从 `qa-seed.json` 导入，按 `source+sourceRef` 去重（只插不更新）
  2. `AiQaExtractionService.extractBatch()` — LLM 自动提炼，`source=AUTO_EXTRACTED`，按 `sourceRef` upsert
  3. **[新增]** `AiTrainingController.createQa()` — 手动添加，`source=MANUAL_IMPORT`
  4. **[新增]** `AiTrainingController.updateQa()` — 编辑条目
  5. **[新增]** `AiTrainingController.deleteQa()` — 删除条目
- Read paths:
  1. `AiTrainingQaService.list()` — QA 面板列表（已有 `GET /api/ai-training/qa`）
  2. `AiTrainingQaService.buildKnowledgeContext()` — AI 回复上下文，读所有 `enabled=true` 条目
  3. `AiTrainingQaSeeder` — 启动去重查询 `findBySourceAndSourceRef`
- Interaction points: 新增的 CRUD 写入的条目会被 `buildKnowledgeContext()` 读取参与 AI 回复；种子去重按 `sourceRef` 不会覆盖手动条目（只要 `sourceRef` 不同）

### AiPromptConfig 表（MySQL）
- Schema: id(固定=1), free_form_system_prompt, constraints, updated_at
- Write paths:
  1. `AiPromptConfigService.update()` — PUT 保存
- Read paths:
  1. `AiPromptConfigService.getDto()` — 返回原始配置（可能为 null）
  2. `AiPromptConfigService.getEffectiveFreeFormSystemPrompt(defaultPrompt)` — 返回有效提示词（含 fallback）
- Interaction points: 新增 API 需返回有效值（组合 raw + default），但不改写入逻辑

## 实现方案

### 阶段一：后端 API 新增/修改（3 个文件）

#### Task 1.1: 新增邮件列表 API `GET /api/ai-training/simulate/mails`
- **文件**: `AiTrainingController.kt`
- **遵守**: I-1, I-2, I-3
- **实现**:
  - 新增端点 `GET /api/ai-training/simulate/mails`
  - 参数: `expertTag: String?`（ES 标签）, `inboundTagKey: String?`（回信标签，格式 `qa:<id>` / `custom:<label>`）, `page: Int`, `size: Int`
  - 逻辑:
    1. 如果 `expertTag` 非空，调用 `ExpertSearchService.search(tag=expertTag)` 获取匹配的 `orcidId` 列表
    2. 通过 `ExpertContactRepository` 查出对应的 `contactId` 列表
    3. 查询 `mail_record` 表 direction='INBOUND' 且 `expert_contact_id IN (contactIds)`
    4. 如果 `inboundTagKey` 非空，通过 `inbound_mail_tag` EXISTS 子查询过滤（需要 `mail_record.source_inbound_id` 或通过 `inbound_mail_processing.expert_contact_id` 关联）
    5. 返回邮件列表（包含 expertContactId, expertName, expertEmail, subject, receivedAt, body/cleanedBody, inboundTags, expertTags）
  - 需注入: `ExpertSearchService`, `ExpertContactRepository`（新增注入）

#### Task 1.2: 新增 Repository 查询方法
- **文件**: `MailRecordRepository.kt`
- **遵守**: I-2
- **实现**:
  - 新增 `findInboundMailsForSimulation()` 自定义 @Query：
    ```sql
    SELECT mr.* FROM mail_record mr
    WHERE mr.direction = 'INBOUND'
      AND mr.expert_contact_id IS NOT NULL
      AND (:contactIds IS NULL OR mr.expert_contact_id IN (:contactIds))
      AND (:qaRuleId IS NULL OR EXISTS (
            SELECT 1 FROM inbound_mail_tag t
            JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
            WHERE p.expert_contact_id = mr.expert_contact_id
              AND t.qa_rule_id = :qaRuleId))
      AND (:customLabel IS NULL OR EXISTS (
            SELECT 1 FROM inbound_mail_tag t2
            JOIN inbound_mail_processing p2 ON p2.id = t2.inbound_processing_id
            WHERE p2.expert_contact_id = mr.expert_contact_id
              AND t2.tag_type = 'CUSTOM'
              AND t2.label = :customLabel))
    GROUP BY mr.expert_contact_id
    ORDER BY MAX(mr.id) DESC
    LIMIT :limit OFFSET :offset
    ```
  - 注：GROUP BY 取每个 contact 最新一封来信，与现有 `findExpertContactIdsWithInboundMail` 模式一致

#### Task 1.3: 新增有效提示词 API
- **文件**: `AiTrainingController.kt`, `AiPromptConfigService.kt`
- **遵守**: I-4
- **实现**:
  - `AiPromptConfigService` 新增 `getEffectiveDto(): AiPromptConfigDto` 方法：
    - 如果 `freeFormSystemPrompt` 为空，填入 `AiReplyDraftService` 中的默认提示词（`buildBaseSystemPrompt()` + free-form 后缀）
    - 如果 `constraints` 为空，返回空串（约束无默认值）
  - `AiTrainingController` 新增 `GET /api/ai-training/prompt-config/effective`
  - 返回 `AiPromptConfigDto`，增加 `isCustom: Boolean` 字段指示是否为自定义值
  - 注意：需要解耦默认提示词的构造——将 `buildBaseSystemPrompt()` + free-form 后缀提取为 `companion object` 常量或 `AiPromptConfigService` 可访问的方法，避免循环依赖

#### Task 1.4: 新增 QA 条目 CRUD API
- **文件**: `AiTrainingController.kt`, `AiTrainingQaService.kt`
- **遵守**: I-6, I-7
- **实现**:
  - `AiTrainingQaService` 新增方法：
    - `create(topic, question?, answer, keywords?): AiTrainingQaDto` — 插入 `source=MANUAL_IMPORT`, `sourceRef=MANUAL:<System.currentTimeMillis()>`
    - `update(id, topic, question?, answer, keywords?): AiTrainingQaDto` — 更新已有条目（不改 source/sourceRef）
    - `delete(id)` — 物理删除
  - `AiTrainingController` 新增端点：
    - `POST /api/ai-training/qa` — 创建条目
    - `PUT /api/ai-training/qa/{id}` — 编辑条目
    - `DELETE /api/ai-training/qa/{id}` — 删除条目

#### Task 1.5: QA 种子数据新增两条高频问题
- **文件**: `qa-seed.json`
- **遵守**: I-7
- **实现**:
  - 新增条目 1 — 中介角色定位:
    ```json
    {
      "topic": "中介角色定位",
      "question": "Are you acting as a mediator? What is your role in this process?",
      "answer": "We are a professional talent service agency authorized by local government talent offices. We assist with the full application process including enterprise matching, material preparation, and submission. We are not a simple middleman — we provide end-to-end support funded by government subsidies, and we never charge experts any fees.",
      "keywords": "mediator,middleman,intermediary,broker,your role,acting as",
      "sourceRef": "MEDIATOR_ROLE"
    }
    ```
  - 新增条目 2 — 信息来源渠道:
    ```json
    {
      "topic": "信息来源渠道",
      "question": "How did you come to know me? How did you find my contact information?",
      "answer": "We identified your profile through publicly available academic databases and research publications (such as ORCID, Google Scholar, and university faculty pages). Your research background and expertise closely match the requirements of China's national talent programs, which is why we reached out.",
      "keywords": "how did you find me,how did you know,where did you get,my information,my contact,come to know",
      "sourceRef": "HOW_FOUND_ME"
    }
    ```
  - 种子去重保证：`sourceRef` 为 `MEDIATOR_ROLE` / `HOW_FOUND_ME`，全局唯一，不与已有条目冲突

#### Task 1.6: 标签选项 API（复用已有）
- **不新建**：前端直接复用已有 API：
  - 专家标签: `GET /api/experts/tags/aggregation`（已有）
  - 回信标签: `GET /api/inbound-summary/tags/options`（已有）

### 阶段二：前端 HTML 结构重构（1 个文件）

#### Task 2.1: 改造 `#view-ai-training` section
- **文件**: `index.html`
- **遵守**: I-5
- **实现**:
  - 保留 `<section class="view" id="view-ai-training">` 外壳（I-5 四联注册）
  - 内部替换为 tab 切换结构：
    ```html
    <div class="ai-training-tabs">
      <button class="ai-tab active" data-tab="qa">QA 知识库</button>
      <button class="ai-tab" data-tab="prompts">AI 提示词与约束</button>
      <button class="ai-tab" data-tab="simulate">历史邮件模拟回复</button>
    </div>
    <div class="ai-tab-content" id="aiTabQa">...</div>
    <div class="ai-tab-content" id="aiTabPrompts">...</div>
    <div class="ai-tab-content" id="aiTabSimulate">...</div>
    ```
  - QA 面板: 保持已有功能（表格、分页、来源筛选），新增：
    - 工具栏增加「添加条目」按钮
    - 表格每行增加操作列（编辑、删除按钮）
    - 添加/编辑弹窗表单：主题(topic)、问题(question, 可选)、标准回复(answer)、关键词(keywords, 可选)
    - 删除确认对话框
  - 提示词面板:
    - 将 `textarea` 的 `placeholder` 改为空（默认值由 API 填充）
    - 新增「恢复默认」按钮
    - 新增 info 提示区显示「当前显示系统生效提示词」
  - 模拟回复面板:
    - 去掉搜索专家 input + 搜索按钮 + 专家下拉 select
    - 新增双行标签筛选区（专家标签 Pill 行 + 回信标签 Pill 行）
    - 左侧: 邮件列表容器 `#aiSimulateMailList`
    - 右侧: 邮件正文详情 `#aiSimulateMailDetail` + 模拟回复区域（保留 promptOverride textarea + 模拟按钮 + 草稿展示区）

### 阶段三：前端 JS 逻辑重构（1 个文件）

#### Task 3.1: 状态扩展
- **文件**: `app.js`
- **遵守**: I-1, I-5
- **实现**:
  - `state.aiTraining` 新增字段:
    ```js
    activeTab: "simulate",         // 当前 tab
    expertTagOptions: [],          // 可选专家标签列表
    inboundTagOptions: [],         // 可选回信标签列表
    selectedExpertTag: "",         // 选中的专家标签
    selectedInboundTagKey: "",     // 选中的回信标签 key
    simulateMails: [],             // 筛选后的邮件列表
    simulateMailsTotal: 0,
    simulateMailsPage: 0,
    simulateMailsSize: 20,
    selectedSimulateMailContactId: null,  // 选中邮件的 contactId
    selectedSimulateMail: null,    // 选中邮件的详情对象
    promptIsCustom: false,         // 提示词是否为自定义
    ```
  - 移除字段: `expertKeyword`, `simulateExperts`, `selectedContactId`

#### Task 3.2: Tab 切换逻辑
- **文件**: `app.js`
- **遵守**: I-5
- **实现**:
  - 新增 `switchAiTrainingTab(tab)` 函数：切换 `.ai-tab.active` 和 `.ai-tab-content` 显隐
  - 事件绑定 `.ai-tab` 按钮点击

#### Task 3.3: 模拟面板重构
- **文件**: `app.js`
- **遵守**: I-1, I-2, I-3
- **实现**:
  - 移除: `loadAiTrainingSimulateExperts()`, `renderAiTrainingExpertSelect()`, 对应事件绑定
  - 新增 `loadAiTrainingTagOptions()`:
    - 并发请求 `GET /api/experts/tags/aggregation` 和 `GET /api/inbound-summary/tags/options`
    - 渲染标签 Pill 到对应行
  - 新增 `loadAiTrainingSimulateMails()`:
    - 调用 `GET /api/ai-training/simulate/mails?expertTag=&inboundTagKey=&page=&size=`
    - 渲染左侧邮件列表
  - 新增 `renderAiTrainingMailList()`:
    - 渲染每封邮件为可点击的行（专家名、主题、日期、标签 pills）
    - 选中态高亮
  - 新增 `selectSimulateMail(mail)`:
    - 设置 `state.aiTraining.selectedSimulateMailContactId = mail.expertContactId`
    - 在右侧展示邮件正文（`mail.body`，使用 `.pre` 类名 — 来源: K-mail-body-display-sites）
    - 清空之前的模拟结果
  - 修改 `runAiTrainingSimulate()`:
    - 从 `state.aiTraining.selectedSimulateMailContactId` 读 contactId（不再从 select 读）
  - 标签 Pill 点击: 切换选中状态 → 调用 `loadAiTrainingSimulateMails()` 刷新列表

#### Task 3.4: 提示词面板重构
- **文件**: `app.js`
- **遵守**: I-4
- **实现**:
  - 修改 `loadAiTrainingPromptConfig()`:
    - 改为调用 `GET /api/ai-training/prompt-config/effective`
    - 设置 `state.aiTraining.promptIsCustom = config.isCustom`
  - 修改 `fillAiTrainingPromptForm()`:
    - 无论是否自定义，都填入有效值（不再显示 placeholder）
    - 根据 `isCustom` 显示/隐藏「恢复默认」按钮
    - 显示提示文字：自定义 → "自定义提示词生效中" / 默认 → "当前使用系统默认提示词"
  - 新增「恢复默认」按钮逻辑: 调用 `PUT /api/ai-training/prompt-config { freeFormSystemPrompt: null, constraints: null }` 后重新加载

#### Task 3.5: QA 面板 CRUD 交互
- **文件**: `app.js`
- **遵守**: I-6
- **实现**:
  - 修改 `renderAiTrainingQaTable()`:
    - 每行新增操作列，包含「编辑」「删除」按钮（`data-qa-id`）
  - 新增 `showQaEditModal(qaItem?)`:
    - 无参数 = 新增模式，有参数 = 编辑模式（预填字段）
    - 弹窗包含：主题 input、问题 textarea、标准回复 textarea、关键词 input
    - 提交按钮调用 `POST /api/ai-training/qa`（新增）或 `PUT /api/ai-training/qa/{id}`（编辑）
    - 成功后关闭弹窗 + `loadAiTrainingQa()` 刷新列表
  - 新增 `deleteQaItem(id)`:
    - 确认对话框 → `DELETE /api/ai-training/qa/{id}` → 刷新列表
  - 事件绑定：
    - 「添加条目」按钮 → `showQaEditModal()`
    - 表格行「编辑」按钮 → `showQaEditModal(item)`
    - 表格行「删除」按钮 → `deleteQaItem(id)`

#### Task 3.6: loadAiTraining 入口调整
- **文件**: `app.js`
- **遵守**: I-5
- **实现**:
  - `loadAiTraining()` 修改为:
    ```js
    async function loadAiTraining() {
        await Promise.all([
            loadAiTrainingQa(),
            loadAiTrainingPromptConfig(),
            loadAiTrainingTagOptions(),
            loadAiTrainingSimulateMails()
        ]);
    }
    ```

### 阶段四：样式重构（1 个文件）

#### Task 4.1: CSS 样式
- **文件**: `styles.css`
- **遵守**: 无数据不变量，纯视觉
- **实现**:
  - 新增 tab 切换样式（`.ai-training-tabs`, `.ai-tab`, `.ai-tab-content`）
  - 新增模拟面板左右分栏样式（grid `minmax(300px, 380px) minmax(0, 1fr)`）
  - 新增邮件列表项样式（hover、选中态、标签 pill 行）
  - 新增邮件正文详情样式（正文使用 `.pre`）
  - 提示词面板双栏（grid `1fr 1fr`）
  - 保留响应式断点（`@media max-width: 1100px` 回退单栏）
  - 删除/替换旧的 `.ai-training-grid` 相关样式

### 阶段五：测试

#### Task 5.1: 后端单元测试
- **文件**: `AiTrainingSimulateTest.kt`
- **实现**:
  - 测试 `GET /api/ai-training/simulate/mails` 无筛选返回所有来信
  - 测试 `inboundTagKey` 筛选
  - 测试 `expertTag` 筛选
  - 测试 `GET /api/ai-training/prompt-config/effective` 返回默认值
  - 测试自定义值优先于默认值
  - 测试 `POST /api/ai-training/qa` 创建条目成功
  - 测试 `PUT /api/ai-training/qa/{id}` 编辑条目成功
  - 测试 `DELETE /api/ai-training/qa/{id}` 删除条目成功

#### Task 5.2: AiPromptConfigService 单元测试
- **文件**: 新增或扩展已有测试
- **实现**:
  - 测试 `getEffectiveDto()` 在无自定义配置时返回默认提示词
  - 测试有自定义配置时返回自定义值
  - 测试 `isCustom` 标志正确

#### Task 5.3: QA 种子数据验证
- 验证 `AiTrainingQaSeeder` 重启后不重复插入新条目（`sourceRef` 去重）
- 验证新条目参与 `buildKnowledgeContext()` 输出

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|----------|------|
| 1 | `src/main/kotlin/.../llm/controller/AiTrainingController.kt` | 修改 | 新增 `simulate/mails`、`prompt-config/effective`、QA CRUD 端点 |
| 2 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | 修改 | 新增 `findInboundMailsForSimulation()` 查询 |
| 3 | `src/main/kotlin/.../llm/service/AiPromptConfigService.kt` | 修改 | 新增 `getEffectiveDto()` 方法，提取默认提示词常量 |
| 4 | `src/main/kotlin/.../llm/service/AiReplyDraftService.kt` | 修改 | 将 `buildBaseSystemPrompt()` 提取为 companion/共享方法 |
| 5 | `src/main/kotlin/.../llm/service/AiTrainingQaService.kt` | 修改 | 新增 `create()`、`update()`、`delete()` 方法 |
| 6 | `src/main/resources/ai-training/qa-seed.json` | 修改 | 新增 MEDIATOR_ROLE、HOW_FOUND_ME 两条种子条目 |
| 7 | `src/main/resources/static/index.html` | 修改 | 重构 `#view-ai-training` 内部结构，QA 面板增加 CRUD UI |
| 8 | `src/main/resources/static/app.js` | 修改 | 重构 AI 训练面板全部 JS 逻辑，新增 QA CRUD 交互 |
| 9 | `src/main/resources/static/styles.css` | 修改 | 重构 AI 训练面板样式 |
| 10 | `src/test/kotlin/.../llm/controller/AiTrainingSimulateTest.kt` | 修改 | 新增邮件列表、有效提示词、QA CRUD 测试 |

共 10 个文件，= 10 上限。

## 验收标准

- **I-1**: 在模拟面板选中一封邮件后点击「生成模拟回复」，verify 请求 body 包含 `expertContactId` 且返回模拟草稿。
- **I-2**: 选择回信标签「代理资质与政府合作」后，邮件列表只包含打了该 QA 标签的专家来信。取消筛选后恢复全量。
- **I-3**: 选择专家标签 `verified` 后，邮件列表只包含 ES 中 `tags` 含 `verified` 的专家来信。
- **I-4**: 新部署后首次打开提示词面板，textarea 显示系统默认提示词全文（非空白）。保存自定义值后重新打开，显示自定义值。点击「恢复默认」后回到默认值。
- **I-5**: 侧栏点击「AI 训练」正常加载页面，tab 切换正常，无 JS 报错。`refreshCurrentView()` 正常触发 `loadAiTraining()`。

- **I-6**: 在 QA 面板点击「添加条目」，填写主题和回复后保存，列表中出现新条目（`source=人工导入`）。编辑后内容更新。删除后消失。新条目立即参与模拟回复的 AI 上下文（`buildKnowledgeContext()` 包含它）。
- **I-7**: 新增的 `MEDIATOR_ROLE` 和 `HOW_FOUND_ME` 种子条目在首次启动后出现在 QA 列表中。重启后不重复插入（`findBySourceAndSourceRef` 去重）。

**集成场景：**
1. 同时选择专家标签 + 回信标签，验证两个条件 AND 交集筛选。
2. 无匹配结果时显示空状态提示。
3. 邮件列表分页正常。
4. 手动添加一条 QA 条目后，在模拟面板选中一封相关邮件，生成模拟回复，验证 AI 上下文包含新条目内容。
5. `mvn test` 全量通过。
