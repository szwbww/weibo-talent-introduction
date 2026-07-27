# 收发件箱列表页：精简列 + 统一查看详情

> 计划类型：前端为主 + 1 个只读后端详情接口
> 创建日期：2026-06-23

## 需求描述

可观察结果：
1. 「已激活账号收发邮件记录」列表（收发件箱视图）不再显示「内容预览」「邮件类型」两列。
2. 列表中**每一行邮件**（含发件、含收件中当前无法处理/无操作按钮的行）都有一个「查看」按钮，点击后弹出该邮件的完整详情（完整正文、主题、方向、类型、时间、发送状态、附件标识等）。

不可改变（必须保留）：
- 列表既有的过滤（标签/账号/日期/仅待处理）、分页、行内既有动作按钮（`查看/处理`、`标记已处理`、`查看专家`）行为不变。
- 「来源」「发送状态」两列保留（它们本就仅对发件渲染，收件显示 `-`，符合"收件下不需要显示"的诉求，无需改动）。
- `state.mailbox.items` 的数据流、列表接口 `/api/mail/mailbox` 的查询逻辑不变。

不在范围内（明确推迟）：
- 不调整收件/发件以外的其它表格列。
- 不重构 `renderMailItem` 现有联系人时间线渲染。
- 不为详情弹窗增加回复/转发等操作，仅做"只读查看"。
- 不改动数据库 schema、不新增 Flyway 迁移。

## 关键不变量

### Invariant I-1：列模型前后端一致
- Rule：列表 `<thead>` 列数必须与 `renderMailboxTable()` 每行 `<td>` 数、以及空态 `colspan` 三者严格相等。删除「内容预览」「邮件类型」后，三处统一为 **11 列**。
- Applies to：`index.html`（表头）、`app.js#renderMailboxTable`（数据行 `<td>` 与空态 `colspan`）。
- Violation consequence：列错位/表格渲染破裂或空态占位宽度错误。

### Invariant I-2：查看按钮对所有行可用且不替换既有动作
- Rule：`renderMailboxActions(row)` 对**任意** row 都至少返回一个「查看」按钮；既有条件按钮（`open-pending` / `mark-unmatched-resolved` / `open-monitoring-contact`）在满足原条件时仍照常追加，「查看」按钮**追加**在动作单元格内，不替换、不互斥。原先返回 `-` 的分支不再出现 `-`（因为至少有「查看」）。
- Applies to：`app.js#renderMailboxActions`。
- Violation consequence：发件行/不可处理收件行仍无查看入口（即本需求未达成），或既有操作按钮被覆盖丢失。

### Invariant I-3：详情来源按 source 正确取数
- Rule：详情接口必须依据 `source` 字段分流：`MAIL_RECORD` → 读 `mail_record`（按 `id`），`INBOUND_PROCESSING` → 读 `inbound_mail_processing`（按 `id`，等于 `inboundProcessingId`）。完整正文优先取 `cleanedBody`，回退 `body`。不得跨表用错主键。
- Applies to：新增 `MailboxController` 详情端点、`MailboxService` 详情方法。
- Violation consequence：取到错误邮件正文或 404/空白详情。

### Invariant I-4：详情接口只读、不改变列表语义
- Rule：详情接口为 `GET`，不写库、不触发状态机；返回字段是列表行字段的超集（额外含完整正文）。前端弹窗失败时仅提示错误，不破坏列表当前状态/分页。
- Applies to：新增端点、`app.js#showMailDetail`。
- Violation consequence：查看动作产生副作用或污染列表状态。

## 现状审计

### 前端：收发件箱表格
- 表头：`index.html` L546-561，当前 13 列：时间 / 方向 / 邮箱账号 / 专家邮箱 / 专家姓名 / 主题 / 标签 / **内容预览** / **邮件类型** / 来源 / 附件 / 发送状态 / 操作。
- 行渲染：`app.js#renderMailboxTable` L5786-5846；空态 `colspan="13"`（L5795）；数据行 13 个 `<td>`，其中「内容预览」L5837（`row.bodyPreview`）、「邮件类型」L5838（`row.mailType`）。
- 动作渲染：`app.js#renderMailboxActions` L4132-4141。当前逻辑：
  1. `source==="INBOUND_PROCESSING" && processStatus==="MANUAL_REVIEW" && inboundProcessingId` → `查看/处理`+`标记已处理`；
  2. 否则 `expertContactId` 存在 → `查看专家`；
  3. 否则返回 `-`（**发件行、非待处理收件行落此分支，无任何查看入口** —— 本次要修复点）。
- 点击委托：`app.js` L5434-5446，`#mailboxTableBody` 上监听 `data-action`，现支持 `open-monitoring-contact`、`open-pending`、`mark-unmatched-resolved`、`view-unmatched`、`open-contact-from-unmatched`。需新增 `view-mail` 分支。
- 数据来源：`loadMailbox()` L5739-5777，调用 `/api/mail/mailbox?...`，结果存 `state.mailbox.items`。**列表项不含完整正文，仅 `bodyPreview`（截断）** → 故"查看完整正文"需后端详情接口。
- 既有正文渲染参考：`renderMailItem` L3188-3227（联系人时间线用，含 `<details>查看完整正文`），可作为弹窗正文样式参考，但不复用其数据结构。
- 既有弹窗基础设施：`#taskProgressModal`（任务进度，L442 起）为专用模态，不通用。本计划弹窗采用轻量自建（详情面板/overlay），不依赖任务模态。

### 后端：列表与详情
- 列表 DTO：`MailboxController.MailboxItemResponse`（L10-30），字段含 `id`、`source`、`direction`、`mailType`、`subject`、`bodyPreview`、`sendStatus`、`hasAttachment`、`inboundProcessingId`、`expertContactId` 等；**无完整正文**。
- 列表服务：`MailboxService.listMailbox`（L17-87），由 `MailRecordRepository.listMailbox` 以 UNION 产出两类 `source`：`MAIL_RECORD`（`mail_record`，L220）、`INBOUND_PROCESSING`（`inbound_mail_processing`，L250）；`id` 即各自表主键。
- 可复用读路径：
  - `MailRecordRepository.findByIdOrNull(id)`（L168）→ `MailRecord`（`body` L20 / `cleanedBody` L21 / `subject` L19）。
  - 收件 `InboundMailProcessing` 域有 `body` L17 / `cleanedBody` L18 / `subject` L16；需确认其仓储 `findById` 可用（`UnmatchedInboundMailController` 已有按 id 取 inbound 详情的服务，可参照/复用其仓储）。
- **交互点**：详情接口的 `source` 分流必须与 `MailRecordRepository.listMailbox` 中 UNION 产出的 `source` 取值（`MAIL_RECORD` / `INBOUND_PROCESSING`）完全对齐（I-3）。这是唯一跨模块交互点。

## 实现方案

### 阶段 A：前端精简列（满足需求 1，遵循 I-1）
1. `index.html` L555-556：删除 `<th>内容预览</th>` 与 `<th>邮件类型</th>` 两行。表头降为 11 列。
2. `app.js#renderMailboxTable`：
   - 删除 L5837（内容预览 `<td>`）、L5838（邮件类型 `<td>`）两个单元格。
   - L5795 空态 `colspan="13"` → `colspan="11"`。

### 阶段 B：后端只读详情接口（支撑需求 2，遵循 I-3 / I-4）
3. `MailboxController`：新增
   ```
   @GetMapping("/{source}/{id}")
   fun detail(@PathVariable source: String, @PathVariable id: Long): MailboxDetailResponse
   ```
   新增 `MailboxDetailResponse` DTO = 列表字段超集 + `body: String?`（完整正文）。
4. `MailboxService`：新增 `getMailboxDetail(source, id)`：
   - `source=="MAIL_RECORD"` → `mailRecordRepository.findByIdOrNull(id)`；正文取 `cleanedBody ?: body`。
   - `source=="INBOUND_PROCESSING"` → 经收件仓储 `findById(id)`；正文取 `cleanedBody ?: body`。
   - 命不中 → 抛 404（沿用项目 `GlobalExceptionHandler` 既有的未找到处理方式）。
   - 其余展示字段（主题、方向、类型、时间、发送状态、附件、专家信息）按对应域映射；复用 `computeTags(row)` 不在此处必需。

### 阶段 C：前端查看入口与弹窗（满足需求 2，遵循 I-2 / I-4）
5. `app.js#renderMailboxActions`：在函数末尾、`return` 前，对所有 row 追加：
   ```js
   actions.push(`<button class="button" data-action="view-mail" data-source="${escapeHtml(row.source||"")}" data-id="${escapeHtml(row.id)}">查看</button>`);
   ```
   既有条件分支保持不变；末行 `return actions.join(" ") || "-"` 的 `|| "-"` 可保留（此后永不触发）。
6. `app.js` `#mailboxTableBody` 点击委托（L5434-5446）：新增分支
   ```js
   if (target.dataset.action === "view-mail") {
       await showMailDetail(target.dataset.source, target.dataset.id);
       return;
   }
   ```
7. `app.js`：新增 `showMailDetail(source, id)`：
   - `await api(`/api/mail/mailbox/${source}/${id}`)` 取详情；
   - 渲染只读弹窗（overlay + 关闭按钮），正文样式参照 `renderMailItem`（`escapeHtml` 完整 `body`，`white-space:pre-wrap`）；
   - 失败 `showStatus(err.message, "error")`，不改动 `state.mailbox`（I-4）。
   - 弹窗 DOM 可动态创建或在 `index.html` 增一处隐藏容器；优先动态创建，避免新增结构性元素。

### 验证阶段
8. `mvn test`（JDK11）确认后端编译与现有测试通过；前端手动核对列对齐与各类行的查看按钮。

## 变更文件清单

| 文件 | 改动 | 关联不变量 |
|---|---|---|
| `src/main/resources/static/index.html` | 删除 2 个 `<th>`（内容预览、邮件类型） | I-1 |
| `src/main/resources/static/app.js` | 删 2 个 `<td>` + `colspan`13→11；`renderMailboxActions` 追加查看按钮；点击委托加 `view-mail` 分支；新增 `showMailDetail` 弹窗 | I-1, I-2, I-4 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt` | 新增详情端点 + `MailboxDetailResponse` DTO | I-3, I-4 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 新增 `getMailboxDetail(source,id)`，按 source 分流取完整正文 | I-3, I-4 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`（如无 `findById` 则确认/补只读查询） | 仅在缺少按 id 读取能力时调整；优先复用现有仓储，不新建类 | I-3 |

文件数：≤ 5。子系统：前端（index.html/app.js）+ 后端（controller/service/repository）= 2。无共享存储新增字段。

## 验收标准

- I-1：打开收发件箱，表头为 11 列且无「内容预览」「邮件类型」；任意非空/空数据下行单元格与表头对齐无错位；空态占位横跨整表。
- I-2：分别验证 4 类行——(a) 发件行、(b) `MANUAL_REVIEW` 待处理收件行、(c) 已匹配专家收件行、(d) 无 contact 无法处理收件行——每类都出现「查看」按钮；(b)(c) 的原有按钮仍并存；任何行不再渲染孤立 `-` 作为唯一动作。
- I-3：对 `MAIL_RECORD` 与 `INBOUND_PROCESSING` 各取一行点击查看，弹窗正文与库中对应记录 `cleanedBody ?: body` 一致；错误 id 返回 404 且前端提示错误不崩溃。
- I-4：点击查看前后，列表分页/过滤/选中状态不变；详情接口为 GET 且不产生任何写库或状态流转。
- 集成（跨交互点）：构造同一专家既有 `MAIL_RECORD` 发件又有 `INBOUND_PROCESSING` 收件时，两条记录的查看分别命中正确表与正文（验证 source 分流，I-3）。

## 自检清单
- [x] 关键不变量存在，新增入口/字段均有对应不变量
- [x] 现状审计列出所有相关读/写路径（grep 实证，非记忆）
- [x] 无未被不变量覆盖的新增写路径（本计划仅新增只读读路径）
- [x] 文件数 ≤ 10
- [x] 子系统数 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无"相关文件/等"，逐一具名
- [x] 已显式列出推迟项
