# 来信汇总改版

## 需求描述

**可观测结果**：来信汇总页面四处交互优化：①列表只展示已匹配专家的来信 ②详情线程中高亮当前选中的来信气泡 ③标签编辑器从顶部统一区移至每条来信气泡内 ④列表增加按专家聚合模式且默认开启。

**不可变更（must NOT change）**：
- 收发件箱（mailbox view）的标签编辑器不受影响，保留顶部统一编辑模式
- 标签 API 接口契约不变（`/api/inbound-summary/mails/{inboundId}/tags/*`）
- 来信汇总的统计图表（标签排行、占比饼图）逻辑不变
- 标签筛选 chip 行为不变

**不在范围（out of scope）**：
- 后端分页改为按专家分组分页（前端展示层聚合已足够）
- 来信列表跨页合并同一专家（可接受同一专家分布在不同页）
- 收发件箱标签编辑器改造

---

## 关键不变量

### Invariant I-1: 来信列表只展示已关联专家的记录
- Rule: `listInboundSummary` 和 `countInboundSummary` SQL 必须包含 `AND p.expert_contact_id IS NOT NULL` 条件，确保未匹配专家的来信不出现在列表中
- Applies to: `InboundMailProcessingRepository.listInboundSummary()`, `InboundMailProcessingRepository.countInboundSummary()`
- Violation consequence: 列表仍然显示"未关联专家"行，按专家聚合模式下出现无法分组的孤立行
- 来源: original

### Invariant I-2: 线程中当前来信具有明确视觉高亮
- Rule: `renderInboundThread` 中 `isCurrent === true` 的气泡必须具有区别于普通选中/hover 的视觉标识（金色左竖线 + 浅黄底色 + "当前来信"角标），且不影响其他气泡样式
- Applies to: `renderInboundThread()` (app.js), `.inbound-thread-bubble.current` (styles.css)
- Violation consequence: 用户在多封来信场景下无法辨别"是哪一封"
- 来源: original

### Invariant I-3: 每条来信气泡的标签操作独立绑定到各自的 inboundProcessingId
- Rule: 标签操作（添加/自动/删除）必须以消息对应的 `inboundProcessingId` 为粒度执行，不可混用线程顶层的 `selectedId`。仅 INBOUND 方向且有 `inboundProcessingId` 的消息才渲染标签操作区。OUTBOUND 消息无标签操作。
- Applies to: `ThreadMessageView`（后端新增字段）、`renderInboundThread()`（前端标签渲染和事件委托）
- Violation consequence: 对消息A的标签操作错误写入消息B，或 OUTBOUND 消息出现无效的标签操作按钮
- 来源: original

### Invariant I-4: 前端聚合模式为纯展示层分组，不改变 API 调用
- Rule: 聚合/平铺切换仅影响 `renderInboundMailList()` 的 HTML 输出。API 请求参数（分页、排序、tagKey 筛选）保持不变。聚合分组键为 `expertContactId`。
- Applies to: `renderInboundMailList()`, `state.inboundSummary.groupByExpert`
- Violation consequence: 切换聚合模式导致额外 API 请求或丢失数据
- 来源: original

### Invariant I-5: MailRecord → InboundMailProcessing 映射使用 sourceInboundId 字段
- Rule: 线程中 `MailRecord` 关联回 `InboundMailProcessing` 通过 `MailRecord.sourceInboundId` 字段（直接外键）。对于 OUTBOUND 消息，`sourceInboundId` 指向其回复的来信记录；对于 INBOUND 消息，通过 `messageId` 匹配 `InboundMailProcessing.messageId`。当无法关联时标签列表为空（安全降级）。
- Applies to: `InboundMailSummaryController.getThread()`
- Violation consequence: 标签错误关联到错误的来信，或 N+1 查询导致性能问题
- 来源: original

---

## 现状审计

### Store: `inbound_mail_processing` 表

- Schema: `id`, `sender_account_code`, `imap_uid`, `message_id`, `in_reply_to`, `from_email`, `subject`, `body`, `cleaned_body`, `received_at`, `process_status`, `process_reason`, `reason_type`, `expert_contact_id`, ...
- Write paths:
  1. `AutoMailReplyService.confirmProcessed()` — 主落库点，写入 PROCESSED/MANUAL_REVIEW (来源: K-inbound-processing-write-paths)
  2. `AutoMailReplyService.confirmManualReviewWithBody()` — 带 cleanedBody，QA_NO_MATCH/QA_GAP/退订等
  3. `UnmatchedInboundMailService.bindToContact()` — copy 改状态（非新建），更新 expertContactId
  4. `UnmatchedInboundMailService.markResolved()` — copy 改状态
  5. `PendingMailOperationService.markResolved()` — copy 改状态
- Read paths（本计划涉及）:
  1. `InboundMailSummaryController.listMails()` → `listInboundSummary()` SQL — 来信汇总列表 ← **本计划修改**
  2. `InboundMailSummaryController.getThread()` → `findById()` — 来信详情线程 ← **本计划修改**
  3. `MailMonitoringService` — 监控统计（不涉及）
  4. `MailboxService` — 收发件箱（不涉及）
- Interaction points: listInboundSummary 的过滤条件变更影响分页总数和列表行，需同步修改 count 查询

### Store: `inbound_mail_tag` 表

- Schema: `id`, `inbound_processing_id`, `tag_type`, `qa_rule_id`, `label`, `source`, `created_by`, `created_at`
- Write paths: `InboundMailTagService` (addQaTag/addCustomTag/autoApplyQaTags/deleteTag) — 不变
- Read paths（本计划涉及）:
  1. `InboundMailTagService.listTags(inboundProcessingId)` — 按单条来信查标签 → 用于线程每条消息
  2. `InboundMailTagService.listTagsBatch(ids)` — 批量查标签 → 用于列表
- Interaction points: 线程详情需要按每条 INBOUND 消息获取其标签，需要 `listTagsBatch` 而非单次 `listTags`

### Store: `mail_record` 表

- Schema: `id`, `expert_contact_id`, `direction`, `mail_type`, `source_inbound_id`, `message_id`, ...
- Read paths（本计划涉及）:
  1. `InboundMailSummaryController.getThread()` → `findAllByExpertContactIdOrderByCreatedAtAsc()` — 获取线程所有消息
- Interaction points: `MailRecord.sourceInboundId` 字段直接链接回 `inbound_mail_processing.id`，是 INBOUND 类型 MailRecord 到标签的桥梁

### Frontend: `app.js` 来信汇总模块

- State: `state.inboundSummary` (from/to/page/pageSize/activeTagKey/search/mails/total/selectedId/stats/options/thread/datesInitialized)
- Render paths:
  1. `renderInboundMailList()` — 列表渲染 ← **本计划修改（聚合分支）**
  2. `renderInboundTagEditor(threadData)` — 顶部标签编辑器 ← **本计划移除调用**
  3. `renderInboundThread(threadData)` — 线程气泡 ← **本计划修改（嵌入标签 + 高亮）**
  4. `selectInboundMail(inboundId)` — 选中来信后加载线程 ← **调整：不再调用 renderInboundTagEditor**
- Event paths:
  1. `bindInboundSummaryEvents()` 中 `#inboundTagEditor` 的 click delegate ← **本计划修改为 `#inboundThread` 内事件委托**
  2. `inboundAutoApplyTags()` / `inboundRemoveTag()` — 使用 `state.inboundSummary.selectedId` ← **本计划修改为从 `data-inbound-id` 属性读取**

---

## 实现方案

### Phase A: 后端 — 列表过滤未匹配专家 (I-1)

**Task A-1**: 修改 `listInboundSummary` 和 `countInboundSummary` SQL

- 文件: `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- 改动: 在两个 SQL 的 WHERE 子句中添加 `AND p.expert_contact_id IS NOT NULL`
- 遵守: I-1
- 影响: 列表接口返回数据减少（过滤掉未匹配项），分页总数同步减少

**Task A-2**: 修改单元测试

- 文件: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt`
- 改动: 现有测试用例的 mock 数据 `expertContactId = null` 需改为非 null 值以匹配新 SQL 语义；或新增测试验证 `expertContactId IS NULL` 的记录被过滤
- 遵守: I-1

### Phase B: 后端 — 线程 API 返回每条消息的标签 (I-3, I-5)

**Task B-1**: 扩展 `ThreadMessageView` 增加标签字段

- 文件: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt`
- 改动:
  - `ThreadMessageView` 新增 `inboundProcessingId: Long? = null` 和 `tags: List<TagView> = emptyList()` 字段
  - `MailRecord.toThreadMessage()` 扩展函数中：通过 `sourceInboundId` 作为 `inboundProcessingId` 传入（仅 INBOUND 方向消息有值）
  - `InboundMailProcessing.toThreadMessage()` 直接使用 `id` 作为 `inboundProcessingId`
  - `getThread()` 方法中：收集所有消息的 `inboundProcessingId`，调用 `listTagsBatch()` 一次性获取全部标签，然后按 id 分配到各消息
- 遵守: I-3, I-5
- 注意: `MailRecord.toThreadMessage()` 是文件底部的 private 扩展函数，签名要保持 `private fun MailRecord.toThreadMessage()`，只需补充参数或在调用处传入 sourceInboundId 到返回值

### Phase C: 前端 — 来信列表聚合模式 (I-4)

**Task C-1**: 扩展 state 和工具栏

- 文件: `src/main/resources/static/app.js`
- 改动:
  - `state.inboundSummary` 新增 `groupByExpert: true`
  - 工具栏搜索框旁增加一个聚合开关（使用与项目现有 checkbox-row 一致的样式）
  - 开关切换时设置 `state.inboundSummary.groupByExpert` 并重新渲染列表（不触发 API 调用）
- 遵守: I-4
- 关联 HTML 改动: `index.html` 的 `#view-inbound-summary` toolbar 区域增加聚合开关元素

**Task C-2**: `renderInboundMailList()` 增加聚合渲染分支

- 文件: `src/main/resources/static/app.js`
- 改动:
  - 聚合模式: 按 `expertContactId` 分组，每组渲染一个可折叠的专家头（专家名 + fromEmail + 来信数量），下辖该专家的来信行
  - 非聚合模式: 保持现有平铺渲染（代码不变）
  - 专家头样式: 使用 `.inbound-expert-group-header` 类，与 `.inbound-mail-row` 视觉层级区分
  - 来信行在聚合模式下增加左侧缩进（padding-left 增加）
- 遵守: I-4

**Task C-3**: 聚合模式 CSS 样式

- 文件: `src/main/resources/static/styles.css`
- 改动: 新增 `.inbound-expert-group-header` 及子元素样式，使用项目现有 CSS 变量（`--text-muted`, `--line`, `--primary`, `--border` 等），与现有 `.inbound-mail-row` 风格统一

### Phase D: 前端 — 线程高亮 + 每条消息标签编辑器 (I-2, I-3)

**Task D-1**: 增强 `.current` 高亮样式

- 文件: `src/main/resources/static/styles.css`
- 改动: 强化 `.inbound-thread-bubble.current` 样式：
  - `border-left: 3px solid #d97706` (amber-600)
  - `background: #fffbeb` (amber-50，仅 `.inbound` 方向)
  - 伪元素或嵌套 span 显示"当前来信"角标
- 遵守: I-2

**Task D-2**: `renderInboundThread()` 改造

- 文件: `src/main/resources/static/app.js`
- 改动:
  - 每条气泡底部渲染该消息的标签 chips（从 `msg.tags` 读取）
  - 仅 INBOUND 方向且 `msg.inboundProcessingId` 存在时渲染"+ 添加标签"和"自动 QA 标签"按钮，按钮携带 `data-inbound-id` 属性
  - `isCurrent` 的气泡在 subject 旁增加"当前来信"标记
- 遵守: I-2, I-3

**Task D-3**: 移除顶部标签编辑器、调整事件绑定

- 文件: `src/main/resources/static/app.js`
- 改动:
  - `selectInboundMail()` 中移除 `renderInboundTagEditor(threadData)` 调用
  - `renderInboundDetailEmpty()` 中对 `#inboundTagEditor` 仍设置空状态提示（保留 DOM 结构用于兼容）
  - `bindInboundSummaryEvents()` 中将原 `#inboundTagEditor` 上的 click delegate 改为 `#inboundThread` 上的事件委托
  - `inboundAutoApplyTags()` 和 `inboundRemoveTag()` 改为接受 `inboundId` 参数而非读取 `state.inboundSummary.selectedId`
  - 标签变更后的刷新逻辑：仅重新加载当前线程（`selectInboundMail`），不需要全量刷新列表（因为列表显示的是条目级标签，在 `renderInboundMailList` 中）
- 遵守: I-3

**Task D-4**: HTML 调整

- 文件: `src/main/resources/static/index.html`
- 改动:
  - `#inboundTagEditor` div 保留但内容清空（由 JS 在空状态时写入提示），或将其 `display:none`
  - `#view-inbound-summary` toolbar 增加聚合开关（配合 Task C-1）
- 遵守: I-2, I-3, I-4

---

## 变更文件清单

| # | 文件路径 | 改动类型 | 涉及不变量 |
|---|---------|---------|-----------|
| 1 | `src/main/kotlin/.../repository/InboundMailProcessingRepository.kt` | SQL 修改 | I-1 |
| 2 | `src/main/kotlin/.../controller/InboundMailSummaryController.kt` | 扩展 ThreadMessageView + getThread | I-3, I-5 |
| 3 | `src/test/kotlin/.../controller/InboundMailSummaryControllerTest.kt` | 测试适配 | I-1 |
| 4 | `src/main/resources/static/app.js` | 列表聚合 + 线程标签 + 事件重绑 | I-2, I-3, I-4 |
| 5 | `src/main/resources/static/index.html` | toolbar 聚合开关 + 标签编辑器调整 | I-4 |
| 6 | `src/main/resources/static/styles.css` | 聚合样式 + 高亮样式 + 气泡内标签样式 | I-2, I-4 |

共 6 个文件，≤ 10 ✓；涉及 2 个子系统（后端 API + 前端 UI）≤ 2 ✓；无新数据字段添加到共享存储 ✓。

---

## 验收标准

- **I-1**: 来信列表 API `GET /api/inbound-summary/mails` 返回的所有记录 `expertContactId` 均非 null；前端列表无"未关联专家"行
- **I-2**: 在专家有多封来信时，点击左侧某封来信，右侧线程中对应气泡有金色左边框 + 浅黄底色 + "当前来信"角标，其他气泡无此样式
- **I-3**: 线程中每条 INBOUND 消息气泡底部显示该消息自身的标签 chips 和操作按钮；点击"添加标签"后添加的标签仅出现在该条消息上；OUTBOUND 消息无标签操作区；顶部统一标签编辑器不再显示
- **I-4**: 工具栏"按专家聚合"开关默认开启；开启时列表按专家分组显示带专家头行；关闭时恢复平铺；切换不触发 API 请求
- **I-5**: 线程 API `GET /api/inbound-summary/mails/{id}/thread` 返回的 `messages` 数组中，INBOUND 类型消息含 `inboundProcessingId` 和 `tags` 字段，OUTBOUND 类型消息 `inboundProcessingId` 为 null、`tags` 为空数组
- **集成场景**: 在聚合模式下选中某专家组内第二封来信 → 线程正确高亮该封 → 对其添加标签 → 标签仅出现在该条气泡上 → 列表中该条来信的标签也同步更新
- **构建**: `mvn test` 全部通过
