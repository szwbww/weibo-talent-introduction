# 收发件箱集成 InboundMailTag 标签

## 需求描述

- **可观测结果**：在收发件箱（mailbox）页面，INBOUND 类型邮件卡片上展示其 `inbound_mail_tag` 标签芯片，点击卡片进入邮件详情时可查看、添加（QA / 自定义）、删除标签，与来信汇总页面能力对齐。
- **不可变更**：
  1. 来信汇总（inbound-summary）页面的标签功能保持不变。
  2. 收发件箱已有的计算型标签（专家/待匹配/收件/发件/自动回复/手动回复/首发/待处理）保持不变，`inbound_mail_tag` 标签作为**额外**一行展示，两者共存。
  3. OUTBOUND 邮件不展示 `inbound_mail_tag` 标签（该表只关联 `inbound_processing_id`）。
  4. `InboundMailTagService` 和 `InboundMailTagRepository` 的接口不做修改。
  5. 不新增数据库迁移。
- **不在范围**：
  1. 收发件箱按 `inbound_mail_tag` 筛选（可作后续计划）。
  2. 收发件箱标签统计图表。
  3. 自动打标签的管道变更（已在 `AutoMailReplyService` 中完成）。

## 关键不变量

### Invariant I-1: InboundMailTag 只关联 INBOUND 邮件
- Rule: `inbound_mail_tag.inbound_processing_id` 引用 `inbound_mail_processing.id`。仅 `source=INBOUND_PROCESSING` 的 mailbox 行才有 `inboundProcessingId`；OUTBOUND / `source=MAIL_RECORD` 行的 `inboundProcessingId` 为 null，不得对其调用标签 API。
- Applies to: 前端 `renderMailboxTable()`、`showMailDetail()` 中的标签渲染逻辑；后端 `MailboxService.listMailbox()` 中的标签批量查询。
- Violation consequence: 对 null `inboundProcessingId` 调用标签 API 导致 400/500 错误。

### Invariant I-2: 标签操作复用现有 InboundMailSummaryController API
- Rule: 收发件箱前端的标签增删操作直接调用已有的 `/api/inbound-summary/mails/{inboundId}/tags`（POST）、`/api/inbound-summary/mails/{inboundId}/tags/auto`（POST）、`/api/inbound-summary/tags/{tagId}`（DELETE）端点。后端 `MailboxController` 不新增标签写入端点。
- Applies to: 前端标签编辑交互。
- Violation consequence: 重复实现标签 CRUD，增加维护成本和不一致风险。

### Invariant I-3: 列表页标签为只读展示，编辑仅在详情面板
- Rule: 收发件箱列表的邮件卡片上，`inbound_mail_tag` 标签仅以只读芯片展示（不含删除按钮）。标签的增、删操作仅出现在详情面板（`unmatchedDetailPanel`）中。
- Applies to: `renderMailboxTable()` 中的标签芯片渲染、`showMailDetail()` 中的标签编辑区渲染。
- Violation consequence: 列表卡片出现删除按钮，误操作风险高且交互不一致。

### Invariant I-4: 计算标签与 inbound_mail_tag 标签共存、视觉区分
- Rule: 已有的 `computeTags()` 返回的 `List<String>` 标签使用 `renderMailboxTagBadges()` 渲染为 badge 样式；新增的 `inbound_mail_tag` 标签使用已有的 `renderInboundTagChip()` 渲染为芯片样式。两者在同一行并排展示，不互相替代。
- Applies to: `renderMailboxTable()` 中 `.mailbox-card-tags` 区域。
- Violation consequence: 两种标签样式混淆，无法区分计算标签和用户打的标签。

## 现状审计

### inbound_mail_tag 表
- Schema: `V53__inbound_mail_tag.sql`。PK `id`，UK `(inbound_processing_id, qa_rule_id)`，字段 `tag_type`/`qa_rule_id`/`label`/`source`/`created_by`/`created_at`。
- Write paths:
  1. `InboundMailTagService.autoApplyQaTags()` — 收件管道自动打 QA 标签（调用方：`AutoMailReplyService` confirmProcessed 后）。
  2. `InboundMailTagService.addQaTag()` — 前端手动添加 QA 标签（调用方：`InboundMailSummaryController.addTag()`）。
  3. `InboundMailTagService.addCustomTag()` — 前端手动添加自定义标签（同上）。
  4. `InboundMailTagService.deleteTag()` — 前端删除标签（调用方：`InboundMailSummaryController.deleteTag()`）。
- Read paths:
  1. `InboundMailTagService.listTags()` — 单条来信的标签列表（`InboundMailSummaryController.getThread()`）。
  2. `InboundMailTagService.listTagsBatch()` — 批量查询标签（`InboundMailSummaryController.listMails()`）。
  3. `InboundMailTagService.stats()` — 标签统计（`InboundMailSummaryController.tagStats()`/`tagOptions()`）。
- Interaction points: 本计划新增 `MailboxService.listMailbox()` → `InboundMailTagService.listTagsBatch()` 读路径。

### MailboxController / MailboxService
- `MailboxItemResponse.tags: List<String>` — 计算型标签（硬编码分类），来自 `MailboxService.computeTags()`。
- `MailboxItemResponse.inboundProcessingId: Long?` — 已有字段，INBOUND 类型邮件有值，OUTBOUND 为 null。
- `MailboxDetailResponse` — 无 tags 字段，也无 `inboundProcessingId`（实际上有，但字段名不同需确认）。
  - 确认：`MailboxDetailResponse` 有 `inboundProcessingId: Long?` 字段（line 58）。

### 前端 app.js
- `renderMailboxTagBadges(tags)` (line ~5068) — 渲染计算标签为 badge。
- `renderInboundTagChip(tag, options)` (line ~7864) — 渲染 `TagView` 为芯片，支持 `removable`/`clickable` 选项。已被来信汇总页面使用。
- `renderMailboxTable()` (line ~7724) — 渲染邮件列表卡片。
- `showMailDetail()` (line ~5092) — 展示邮件详情面板。
- `renderInboundTagEditor()` (line ~8017) — 来信汇总的标签编辑器。可提取为复用函数。

## 实现方案

### Phase 1: 后端 — MailboxService 返回 inbound_mail_tag 标签

**Task 1.1: MailboxItemResponse 增加 inboundTags 字段** [I-1, I-4]
- 文件: `MailboxController.kt`
- `MailboxItemResponse` 新增 `inboundTags: List<TagView>` 字段（默认 `emptyList()`）。
- `MailboxDetailResponse` 新增 `inboundTags: List<TagView>` 字段（默认 `emptyList()`）。
- 引入 `TagView` import。

**Task 1.2: MailboxService 注入 InboundMailTagService 并填充 inboundTags** [I-1]
- 文件: `MailboxService.kt`
- 构造函数注入 `InboundMailTagService`。
- `listMailbox()`: 收集所有 rows 中非 null 的 `inboundProcessingId`，调用 `inboundMailTagService.listTagsBatch(ids)`，在 map 中填充 `inboundTags`。
- `toDetailFromInbound()`: 调用 `inboundMailTagService.listTags(recordId)` 填充 `inboundTags`。
- `toDetailFromMailRecord()`: `inboundTags` 保持 `emptyList()`（OUTBOUND 邮件无 inbound tag）。

**Task 1.3: 更新 MailboxControllerTest** [I-1]
- 文件: `MailboxControllerTest.kt`
- 更新已有测试中的 `MailboxItemResponse` 构造，添加 `inboundTags = emptyList()` 参数。

### Phase 2: 前端 — 列表卡片展示 inbound_mail_tag 标签

**Task 2.1: renderMailboxTable() 增加 inboundTags 芯片渲染** [I-1, I-3, I-4]
- 文件: `app.js`
- 在 `renderMailboxTable()` 的 `.mailbox-card-tags` 区域，于 `renderMailboxTagBadges(row.tags)` 之后追加 inbound tag 芯片：
  - 仅当 `row.inboundTags && row.inboundTags.length > 0` 时渲染。
  - 使用已有的 `renderInboundTagChip(tag)` 渲染每个标签（不传 `removable`，默认只读）。

**Task 2.2: showMailDetail() 增加标签编辑区** [I-1, I-2, I-3]
- 文件: `app.js`
- 在 `showMailDetail()` 中，当 `detail.inboundProcessingId != null` 时：
  1. 渲染标签编辑区（复用 `renderInboundTagChip` 渲染现有标签，含删除按钮）。
  2. 添加「自动添加 QA 标签」和「+ 添加标签」按钮。
  3. 标签操作调用已有 API：
     - 自动打标签: `POST /api/inbound-summary/mails/${inboundId}/tags/auto`
     - 添加标签: `POST /api/inbound-summary/mails/${inboundId}/tags`
     - 删除标签: `DELETE /api/inbound-summary/tags/${tagId}`
  4. 操作完成后重新加载标签列表并刷新编辑区。

**Task 2.3: 提取 mailbox 详情面板的标签事件绑定** [I-2]
- 文件: `app.js`
- 在 `unmatchedDetailPanel` 的 click 事件代理中增加标签操作的 action 处理：
  - `mailbox-auto-tags`: 调用自动打标签 API。
  - `mailbox-add-tag-open`: 打开添加标签弹窗（可复用来信汇总的 `showInboundAddTagModal` 逻辑，或提取共享函数）。
  - `mailbox-remove-tag`: 调用删除标签 API。
- 操作后刷新详情面板中的标签区域，同时刷新列表中对应卡片的 inboundTags。

### Phase 3: 前端 — 标签筛选下拉增加 inbound_mail_tag 选项（可选增强）

此阶段**不在本计划范围**，记录为后续计划。收发件箱现有标签筛选（`#mailboxFilterTag`）仅针对 `computeTags()` 的硬编码分类，按 `inbound_mail_tag` 筛选需要后端查询支持，复杂度较高，单独出计划。

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|----------|------|
| 1 | `src/main/kotlin/.../mail/controller/MailboxController.kt` | 修改 | `MailboxItemResponse`/`MailboxDetailResponse` 增加 `inboundTags` 字段 |
| 2 | `src/main/kotlin/.../mail/service/MailboxService.kt` | 修改 | 注入 `InboundMailTagService`，列表和详情填充 `inboundTags` |
| 3 | `src/test/kotlin/.../mail/controller/MailboxControllerTest.kt` | 修改 | 适配新字段 |
| 4 | `src/test/kotlin/.../mail/service/MailboxServiceTest.kt` | 修改 | 适配 `MailboxService` 构造函数变更，测试 `inboundTags` 填充 |
| 5 | `src/main/resources/static/app.js` | 修改 | 列表展示 + 详情编辑标签 |

共 5 个文件，2 个子系统（后端 service/controller、前端 app.js）。

## 验收标准

- **I-1**: OUTBOUND 邮件的 `inboundTags` 为空数组；INBOUND 邮件的 `inboundTags` 包含其 `inbound_mail_tag` 表中的记录。前端 OUTBOUND 卡片不渲染 inbound tag 芯片。
- **I-2**: 收发件箱详情面板中添加/删除标签调用 `/api/inbound-summary/...` 端点，不经过 `/api/mail/mailbox`。
- **I-3**: 列表卡片中 inbound tag 芯片无删除按钮；详情面板中 inbound tag 芯片有删除按钮。
- **I-4**: 列表卡片中计算标签（badge）和 inbound tag（芯片）两种样式并排展示，视觉可区分。
- **集成场景**: 在收发件箱打开一封 INBOUND 邮件详情 → 点击「自动添加 QA 标签」→ 标签芯片出现在详情面板和列表卡片上 → 点击芯片上的 × 删除 → 标签消失。切到来信汇总页面，同一封邮件的标签状态一致。
