# 收发件箱标签编辑修复：补齐待处理面板 + 移除列表标签

## 需求描述

- **可观测结果**：
  1. 在收发件箱点击"查看/处理"打开的待处理来信详情面板中，能看到标签编辑区（查看、添加、删除标签），与点击"查看"打开的普通邮件详情面板一致。
  2. 收发件箱列表的邮件卡片上不再展示 `inbound_mail_tag` 标签芯片（仅保留原有计算标签 badge）。
- **不可变更**：
  1. 来信汇总页面的标签功能不动。
  2. 收发件箱点击"查看"打开的普通详情面板中的标签编辑区保持不变。
  3. 已有的计算型标签（专家/待匹配/收件/发件/自动回复/手动回复/首发/待处理）不变。
  4. `InboundMailTagService` 和后端 API 不做修改。
  5. 不新增数据库迁移。
- **不在范围**：
  1. 后端 `MailboxItemResponse` 的 `inboundTags` 字段保留（详情 API 仍需要返回），仅前端列表不渲染。
  2. 后端 `listMailbox` 的批量标签查询可作后续性能优化移除（列表不渲染后可省此查询），但本计划不改后端以避免 API 不兼容。

## 关键不变量

### Invariant I-1: showUnmatchedDetail 使用前端独立查询标签，不改后端 API
- Rule: `showUnmatchedDetail()` 通过前端并行调用已有的 `/api/inbound-summary/mails/{inboundId}/thread` 获取标签（`threadData.tags`），或直接调用一个轻量的标签列表接口。不修改 `UnmatchedInboundMailController.getUnmatchedDetail()` 的返回体。
- Applies to: `showUnmatchedDetail()` 函数。
- Violation consequence: 修改后端 API 返回结构会影响其他消费方、增加变更范围。

### Invariant I-2: 标签编辑操作复用已有的 mailbox 标签操作函数
- Rule: `showUnmatchedDetail` 面板中的标签增删操作复用 `mailboxAutoApplyTags()`、`showMailboxAddTagModal()`、`mailboxRemoveTag()` 等已有函数。这些函数依赖 `state.mailbox.detailContext.inboundProcessingId`，因此 `showUnmatchedDetail` 必须正确设置 `state.mailbox.detailContext`。
- Applies to: `showUnmatchedDetail()` 中的 `state.mailbox.detailContext` 赋值、`handleUnmatchedAction()` 中标签 action 的分支。
- Violation consequence: `mailboxAutoApplyTags` / `showMailboxAddTagModal` 取到 null inboundProcessingId，操作静默无效。

### Invariant I-3: 标签变更后仅刷新编辑区，不重新加载整个面板
- Rule: 标签增删后调用 `refreshMailboxInboundTagsAfterChange()` 局部刷新编辑区 DOM（通过 `outerHTML` 替换 `#mailboxInboundTagEditor`）。不能调用 `showUnmatchedDetail(id)` 重新加载整个面板，否则会丢失用户正在操作的 QA 回复选择、搜索结果等临时状态。
- Applies to: `refreshMailboxInboundTagsAfterChange()`。
- Violation consequence: 用户在面板中正在进行的操作（绑定搜索、QA 选择）因全面板刷新被丢弃。

### Invariant I-4: 列表卡片不渲染 inbound_mail_tag 芯片
- Rule: `renderMailboxTable()` 中不渲染 `row.inboundTags`，仅渲染 `renderMailboxTagBadges(row.tags)` 计算标签。
- Applies to: `renderMailboxTable()` 中 `.mailbox-card-tags` 区域。
- Violation consequence: 列表页信息过载，且与用户明确需求矛盾。

## 现状审计

### showUnmatchedDetail() (app.js ~5688)
- 调用 `/api/mail/unmatched-inbound/${id}` 获取详情。
- Line 5690: `state.mailbox.detailContext = null` — **将标签操作上下文清空**，导致 `mailboxTagEditInboundId()` 返回 null。
- 渲染面板 HTML（line 5803-5881）中无 `renderMailboxInboundTagEditor` 调用。
- 不查询标签数据。

### showMailDetail() (app.js ~5095)
- 调用 `/api/mail/mailbox/${source}/${id}` 获取详情（返回含 `inboundTags`）。
- Line 5124: 正确设置 `state.mailbox.detailContext = { source, id, inboundProcessingId }`。
- Line 5128: 调用 `renderMailboxInboundTagEditor(detail.inboundTags, inboundProcessingId)` 渲染标签编辑区。

### renderMailboxTable() (app.js ~7724)
- Line 7800: `${(row.inboundTags || []).map((tag) => renderInboundTagChip(tag)).join("")}` — 列表卡片渲染 inbound tag 芯片。

### refreshMailboxInboundTagsAfterChange() (app.js ~8125)
- 依赖 `state.mailbox.detailContext`。
- 通过 `/api/mail/mailbox/${source}/${id}` 重新获取标签。
- 用 `outerHTML` 替换 `#mailboxInboundTagEditor` 节点。
- 同时更新 `state.mailbox.items` 中对应行的 `inboundTags` 并重新渲染列表。

### handleUnmatchedAction() (app.js ~5894)
- Line 6055-6065: 已有 `mailbox-auto-tags`、`mailbox-add-tag-open`、`mailbox-remove-tag` 三个 action 的处理分支。这些已就绪，只缺 `state.mailbox.detailContext` 的设置。

### 标签数据获取方式
- `/api/inbound-summary/mails/${id}/thread` 返回 `{ tags: TagView[], messages: [...] }` — 可用来获取单条 inbound 的标签。
- `/api/mail/mailbox/${source}/${id}` 返回含 `inboundTags` — 但 `showUnmatchedDetail` 不走此 API。

## 实现方案

### Task 1: showUnmatchedDetail 插入标签编辑区 [I-1, I-2, I-3]
- 文件: `app.js`
- 在 `showUnmatchedDetail()` 中：
  1. 设置 `state.mailbox.detailContext = { source: "INBOUND_PROCESSING", id: Number(id), inboundProcessingId: Number(id) }`（INBOUND_PROCESSING 的 id 就是 inboundProcessingId）。
  2. 并行查询标签：在已有的 `Promise.all` 中加入 `api(\`/api/inbound-summary/mails/${id}/thread\`)` 获取 `threadData.tags`（或单独用一个轻量请求 — 但复用 thread API 更简单且已有）。
  3. 在面板 HTML 中（metadata-grid 之后、正文区域之前）插入 `renderMailboxInboundTagEditor(tags, Number(id))`。
  4. 不改动面板其他部分。

### Task 2: refreshMailboxInboundTagsAfterChange 适配 showUnmatchedDetail 上下文 [I-2, I-3]
- 文件: `app.js`
- `refreshMailboxInboundTagsAfterChange()` 当前通过 `/api/mail/mailbox/${source}/${id}` 获取标签。但 `showUnmatchedDetail` 的 source 是 `"INBOUND_PROCESSING"`，此 API 也支持该 source，所以无需改动。
- 确认：`MailboxService.getMailboxDetail("INBOUND_PROCESSING", id)` 调用 `toDetailFromInbound()` 返回 `inboundTags` — 已实现。无需修改。

### Task 3: 列表卡片移除 inbound_mail_tag 芯片渲染 [I-4]
- 文件: `app.js`
- 在 `renderMailboxTable()` 中删除 line 7800 的 `${(row.inboundTags || []).map((tag) => renderInboundTagChip(tag)).join("")}`。
- 在 `refreshMailboxInboundTagsAfterChange()` 中删除 line 8133-8139 对 `state.mailbox.items` 中 `inboundTags` 的更新和 `renderMailboxTable()` 调用（列表不展示标签，无需同步）。

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|----------|------|
| 1 | `src/main/resources/static/app.js` | 修改 | showUnmatchedDetail 加标签编辑区 + 列表移除 inbound tag 芯片 |

共 1 个文件。纯前端改动。

## 验收标准

- **I-1**: `showUnmatchedDetail` 不修改 `UnmatchedInboundMailController`，标签数据通过已有的 thread API 获取。
- **I-2**: 在"查看/处理"面板中点击「自动添加 QA 标签」→ 标签芯片出现；点击「+ 添加标签」→ 弹窗打开可添加；点击芯片 × → 标签删除。操作后编辑区局部刷新，面板其他内容不丢失。
- **I-3**: 标签变更后不触发 `showUnmatchedDetail` 重载，面板中正在进行的 QA 选择/搜索结果保持。
- **I-4**: 收发件箱列表卡片上不显示 `inbound_mail_tag` 芯片，仅保留原有计算标签 badge。
- **集成场景**: 收发件箱筛选待处理 → 点击"查看/处理"→ 面板中看到标签编辑区 → 添加自定义标签"重要" → 标签出现 → 关闭面板 → 列表卡片无"重要"芯片 → 再次打开同一邮件 → "重要"标签仍在编辑区中。
