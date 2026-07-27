# 专家标签管理 + 材料提醒 + 批量发送 + 移除待跟进

## 需求描述

- **可观测结果**：
  1. 专家详情面板新增标签编辑区：查看当前标签、添加预设/自定义标签（如"承诺回复材料"）、删除标签。
  2. 专家列表标签筛选下拉从硬编码改为动态加载（ES 聚合），自动包含新增的自定义标签。
  3. 新增"材料提醒"邮件模板，运营在专家详情面板可选择发送。
  4. 专家列表页新增「批量发送提醒」按钮：按标签筛选后，一键向筛选结果中的全部专家发送指定邮件（如材料提醒）。
  5. 移除前端「待跟进」筛选和标记按钮（被标签系统替代）。
- **不可变更**：
  1. 已有的自动标签写入（`discovered`/`auto_promoted`/`verified`）逻辑不变。
  2. `ExpertIndexWriterService.addTag()`/`removeTag()` 签名不变。
  3. `ManualExpertMailService.sendManualMail()` 单条发送逻辑不变。
  4. 后端 followUp 相关代码保留不删（endpoints 仅不再被前端调用），避免 API 破坏性变更。
- **不在范围**：
  1. 收到附件自动移除标签（远期联动优化）。
  2. 标签改名/合并管理。
  3. 后端删除 followUp 字段和接口（仅隐藏前端入口）。

## 关键不变量

### Invariant I-1: 标签操作使用 orcidId 定位 ES 文档
- Rule: 前端持有的是 `orcidId`，不是 `esDocId`。后端 tag API 接受 `orcidId` + `level`，内部先通过 `ExpertSearchService` 搜索确认文档存在并获取 `esDocId`（使用 `profile.esDocId ?: orcidId` 回退），再调用 `ExpertIndexWriterService.addTag(docId, tag, level)` / `removeTag(...)`。
- Applies to: `ExpertIndexController` 新增的 tag 端点。
- Violation consequence: discovery 来源的专家 `_id`（由 `ExpertIdGenerator` 生成）与 `orcidId` 不一致，直接用 `orcidId` 调用 `addTag` 会 404。

### Invariant I-2: 聚合使用 buildExpertFilters 共享口径
- Rule: `aggregateTags` 新方法必须复用 `buildExpertFilters(tag=null, operatorStatus, emailDomain, region)` 构建筛选条件（排除 tag 自身维度），与 `aggregateEmailDomains`/`aggregateRegions` 保持一致的互斥联动口径。（来源: K-agg-filter-source-of-truth）
- Applies to: `ExpertSearchService.aggregateTags()`。
- Violation consequence: 标签下拉的计数与列表实际命中数脱节。

### Invariant I-3: 批量发送逐条调用 sendManualMail，失败不中断
- Rule: 批量发送遍历筛选结果中有 `contactId` 的专家，逐个调用 `ManualExpertMailService.sendManualMail()`。单个发送失败以 best-effort 跳过（记录失败数），不中断批量流程。批量操作返回 `{ total, success, failed }` 统计。
- Applies to: 新增的批量发送 service 方法。
- Violation consequence: 一个专家发送失败导致整批中断。

### Invariant I-4: MATERIAL_REMINDER 模板纳入 fixedTemplateCodes
- Rule: 新模板的 `template_code` 为 `MATERIAL_REMINDER`，加入 `ManualExpertMailService.fixedTemplateCodes` 集合。`nextStatus()` 对 `MATERIAL_REMINDER` 不改变专家会话状态（保持当前状态）。`toChineseTemplateName()` 映射为 "材料提醒邮件"。
- Applies to: `ManualExpertMailService`。
- Violation consequence: 新模板在"手动发送邮件"下拉中不出现。

### Invariant I-5: 前端移除 followUp 仅隐藏 UI 入口
- Rule: 移除 `index.html` 中 `#contactFollowUpFilter` 下拉和 `app.js` 中 `toggle-follow-up` 按钮。`loadContacts()` 不再传 `followUpMarked` 参数。后端 `markFollowUp`/`unmarkFollowUp` 接口保留但不再被调用。
- Applies to: `index.html`、`app.js`。
- Violation consequence: 如果删除后端接口，已标记 followUp 的专家数据处理可能出问题。

## 修正记录

| 日期 | 来源 | 修正内容 |
|---|---|---|
| 2026-07-02 | fix-v:fix-1 | I-3 的"筛选结果中有 contactId 的专家"指当前筛选条件命中的完整结果集，不是当前可见页，也不是 ES 单次 `size=1000` 请求的前 1000 条。若 ES `max_result_window` 限制无法完整覆盖，必须显式阻断或改为后端按筛选条件收集全部可发送 contactId。 |
| 2026-07-02 | fix-v:fix-1 | 标签编辑区的当前标签必须来自 ES tags 权威源；DB contact 详情响应不包含 tags 时，前端不得只依赖 `state.contacts` 缓存。打开详情和标签变更后刷新，都必须重新读取权威标签数据或保证缓存来自同一 ES 查询口径。 |

## 现状审计

### ES tags 字段（三层索引）
- Schema: `"tags": { "type": "keyword" }`（raw/candidate/application 三层都有）。
- Write paths:
  1. `ExpertDiscoveryService` — 新发现写 `"discovered"`、晋升写 `"auto_promoted"`
  2. `ExpertRevalidationService` — 重验通过写 `"verified"` 或 `"auto_promoted"`
  3. `ExpertIndexWriterService.addTag(docId, tag, level)` — 通用 ES 脚本追加
  4. `ExpertIndexWriterService.removeTag(docId, tag, level)` — 通用 ES 脚本删除
- Read paths:
  1. `ExpertSearchService.searchExperts()` — 列表查询，tags 字段包含在 `_source`
  2. `ExpertSearchService.buildExpertFilters()` — tag term 筛选
  3. `ExpertIndexController.listExperts()` → `ExpertIndexResponse.tags` — 返回前端
- Interaction points: 本计划新增 Controller API 调用 write path 3/4；新增聚合 read path。

### ExpertIndexController (api/experts)
- 已注入 `expertIndexWriterService`。
- 无 tag CRUD 端点，无 tag 聚合端点。
- 列表查询支持 `tag` 参数筛选。

### ManualExpertMailService
- `fixedTemplateCodes = setOf("INTRODUCTION", "MEETING_INVITATION")`。
- `listSendOptions()` 返回 template + QA 两类选项。
- `sendManualMail()` 接受单个 contactId。
- `nextStatus()` 决定发邮件后的状态跃迁。

### mail_template 表
- 已有模板: `INTRODUCTION`、`MEETING_INVITATION`。
- Schema: `template_code` UNIQUE, `template_name`, `subject`, `body_template`, `enabled`。
- 最新 migration: V55。

### 前端 followUp
- `index.html`: `#contactFollowUpFilter` 下拉（全部 / 仅看待跟进）。
- `app.js` `loadContacts()`: 读取 `followUpMarked` 值传入 API 查询。
- `app.js` `loadContactDetail()`: 渲染 `toggle-follow-up` 按钮。
- `app.js` 事件处理: `toggle-follow-up` action 调用 `POST/DELETE /api/expert-contacts/{id}/mark-follow-up`。
- `app.js` `updateFilterBadge()`: 计数中包含 `contactFollowUpFilter`。

### 前端 expertTagFilter
- `index.html`: `#expertTagFilter` 硬编码 3 个 option（auto_promoted/verified/discovered）。
- `app.js` `loadContacts()`: 读取 tag 值传入 ES 搜索。
- `app.js` `expertTagLabels`: 硬编码 3 个标签的中文映射。

## 实现方案

### Phase 1: 后端 — 标签 CRUD API + 聚合

**Task 1.1: ExpertSearchService 新增按 orcidId 精确查找** [I-1]
- 文件: `ExpertSearchService.kt`
- 新增 `findByOrcidId(orcidId: String, level: ExpertIndexLevel): ExpertProfile?`，使用 `term` 查询 `orcidId` 字段返回第一条命中（取 `_id` 作为 `esDocId`）。
- 此方法用于 tag 操作前的文档定位。

**Task 1.2: ExpertSearchService 新增 aggregateTags** [I-2]
- 文件: `ExpertSearchService.kt`
- 新增 `aggregateTags(level, operatorStatus?, emailDomain?, region?): List<TagCount>`。
- 复用 `buildExpertFilters(tag=null, operatorStatus, emailDomain, region)` 构建筛选。
- ES 聚合: `"aggs": { "tags": { "terms": { "field": "tags", "size": 100 } } }`。
- 返回 `data class TagCount(val tag: String, val count: Long)`。

**Task 1.3: ExpertIndexController 新增标签端点** [I-1]
- 文件: `ExpertIndexController.kt`
- `POST /api/experts/tags/add` — body `{ orcidId, tag, level }` → 搜索 esDocId → `addTag(esDocId, tag, level)` → 返回成功/失败。
- `POST /api/experts/tags/remove` — body `{ orcidId, tag, level }` → 搜索 esDocId → `removeTag(esDocId, tag, level)` → 返回成功/失败。
- `GET /api/experts/tags/aggregation` — 参数 `level, operatorStatus?, emailDomain?, region?` → 调用 `aggregateTags` → 返回 `List<TagCount>`。

### Phase 2: 后端 — 材料提醒模板 + 批量发送

**Task 2.1: 新增 MATERIAL_REMINDER 模板** [I-4]
- 文件: `V56__material_reminder_template.sql`
- 插入 `mail_template` 行: `template_code='MATERIAL_REMINDER'`, `template_name='Material Reminder Email'`, 英文提醒正文（提示专家之前承诺的材料尚未收到，请尽快提供）。

**Task 2.2: ManualExpertMailService 扩展** [I-3, I-4]
- 文件: `ManualExpertMailService.kt`
- `fixedTemplateCodes` 加入 `"MATERIAL_REMINDER"`。
- `toChineseTemplateName()` 增加 `"MATERIAL_REMINDER" -> "材料提醒邮件"` 映射。
- `nextStatus()` 增加 `"MATERIAL_REMINDER" -> ConversationStatus.fromName(currentStatus)`（不改变状态）。
- 新增 `sendBatchMail(contactIds: List<Long>, command: ManualMailSendCommand): BatchMailSendResult`，遍历逐个调用 `sendManualMail`，best-effort 收集结果。
- `data class BatchMailSendResult(val total: Int, val success: Int, val failed: Int, val errors: List<String>)`。

**Task 2.3: ExpertContactManagementController 新增批量发送端点** [I-3]
- 文件: `ExpertContactManagementController.kt`
- `POST /api/expert-contacts/batch-mail` — body `{ contactIds: List<Long>, optionType, optionValue }` → 调用 `sendBatchMail` → 返回 `BatchMailSendResult`。

### Phase 3: 前端 — 标签编辑 + 动态筛选 + 批量发送 + 移除 followUp

**Task 3.1: 专家详情面板标签编辑区** [I-1]
- 文件: `app.js`
- 在 `loadContactDetail()` 中 profile-header 下方新增标签编辑区:
  - 渲染当前标签芯片（带 × 删除按钮）。
  - 「+ 添加标签」按钮 → 弹出输入框/下拉（预设常用标签 + 自定义输入）。
  - 预设标签: "承诺回复材料"、"重点关注"、"待补充信息"（前端常量，可扩展）。
  - 添加标签调用 `POST /api/experts/tags/add`，删除调用 `POST /api/experts/tags/remove`。
  - 操作后刷新标签区域（重新查询专家详情获取最新 tags）。

**Task 3.2: 标签筛选动态化** [I-2]
- 文件: `app.js`, `index.html`
- `index.html`: 移除 `#expertTagFilter` 内硬编码的 `<option>`，只保留 `<option value="">全部标签</option>`。
- `app.js`: 新增 `loadExpertTagOptions(level, filters)` 函数，调用 `GET /api/experts/tags/aggregation` 获取标签列表，动态渲染 `#expertTagFilter` 的 `<option>`（格式: `标签名 (数量)`）。
- 在 `loadContacts()` 中与 `loadEmailProviders`/`loadRegions` 并行调用 `loadExpertTagOptions`。
- `expertTagLabels` 保留作 display-name 映射但增加 fallback（未知标签直接显示原文）。

**Task 3.3: 批量发送提醒按钮** [I-3]
- 文件: `app.js`, `index.html`
- `index.html`: 在专家列表 toolbar 中添加「批量发送邮件」按钮 `#batchTagMailBtn`。
- `app.js`: 点击后弹窗确认：
  1. 显示当前筛选条件和命中专家数。
  2. 下拉选择要发送的邮件模板（复用 `loadMailSendOptions`）。
  3. 确认后收集当前列表中有 `contactId` 的专家 ID 列表，调用 `POST /api/expert-contacts/batch-mail`。
  4. 显示发送结果（成功/失败数）。

**Task 3.4: 移除 followUp UI** [I-5]
- 文件: `app.js`, `index.html`
- `index.html`: 删除 `#contactFollowUpFilter` 的 `<label>` 整块。
- `app.js` `loadContacts()`: 移除 `followUpMarked` 变量和 `useDbContactPath` 中对 `followUpMarked` 的引用。
- `app.js` `loadContactDetail()`: 移除 `toggle-follow-up` 按钮渲染。
- `app.js` 事件处理: 移除 `toggle-follow-up` action 分支。
- `app.js` `updateFilterBadge()`: 移除 `contactFollowUpFilter` 计数项。
- 注意: `useDbContactPath` 变量的值可能改变（之前 `followUpMarked` 为真时走 DB 路径），移除后应只用 `needsAttention || replyMode` 判断。

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|----------|------|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改 | 新增 `findByOrcidId`、`aggregateTags` 方法 |
| 2 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | 修改 | 新增 tag add/remove/aggregation 端点 |
| 3 | `src/main/kotlin/.../mail/service/ManualExpertMailService.kt` | 修改 | 扩展 fixedTemplateCodes + 批量发送 |
| 4 | `src/main/kotlin/.../campaign/controller/ExpertContactManagementController.kt` | 修改 | 新增批量发送端点 |
| 5 | `src/main/resources/db/migration/V56__material_reminder_template.sql` | 新增 | MATERIAL_REMINDER 模板种子数据 |
| 6 | `src/main/resources/static/app.js` | 修改 | 标签编辑 + 动态筛选 + 批量发送 + 移除 followUp |
| 7 | `src/main/resources/static/index.html` | 修改 | 移除 followUp 筛选 + 标签筛选动态化 + 批量按钮 |
| 8 | `src/test/kotlin/.../expert/controller/ExpertIndexControllerTest.kt` | 修改 | 测试新增标签端点 |

共 8 个文件（7 修改 + 1 新增），2 个子系统（后端、前端）。

## 验收标准

- **I-1**: 在专家详情面板添加标签 "承诺回复材料" → 关闭面板 → 在列表标签筛选下拉中出现该标签 → 选中筛选 → 列表仅显示带此标签的专家。discovery 来源专家（esDocId ≠ orcidId）标签操作同样有效。
- **I-2**: 标签聚合下拉的 (N) 计数随 operatorStatus / emailDomain / region 筛选联动变化，但标签自身维度的筛选不影响标签下拉选项列表。
- **I-3**: 按标签筛选 "承诺回复材料" → 点击"批量发送邮件" → 选择"材料提醒邮件" → 确认 → 成功发送给所有有 contactId 的专家。其中 1 个专家邮箱无效导致发送失败 → 结果显示 "成功 N，失败 1"，其余专家正常收到。
- **I-4**: 专家详情面板的"手动发送邮件"下拉中出现"材料提醒邮件"选项，选中并发送后专家会话状态不变。
- **I-5**: 专家列表筛选区域无"待跟进"下拉；专家详情面板无"标记待跟进"按钮；后端 `POST/DELETE /api/expert-contacts/{id}/mark-follow-up` 仍可调用（未删除）。
- **端到端场景**: 运营读到专家回信"下周发材料" → 在专家详情打标签"承诺回复材料" → 一周后按此标签筛选 → 看到 5 个专家 → 批量发送材料提醒邮件 → 全部成功 → 某专家发来材料 → 打开该专家详情删除标签 → 标签筛选结果减为 4 个。
