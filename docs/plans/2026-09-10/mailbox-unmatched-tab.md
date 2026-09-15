# 收发件箱“待匹配”Tab 开发计划

> 状态：待人工批准；本文件只定义实施范围，不修改业务代码。  
> 目标页面：收发件箱专家聊天视图。  
> 范围结论：8 个既有文件，2 个协作子系统（后端只读队列、前端聊天视图）；不新增表、字段、迁移、页面或处理动作。

## 需求描述

在收发件箱左侧现有“全部 / 关注 / 待处理”之后增加“待匹配”Tab。该 Tab 只列出已激活收发范围内、仍为 `MANUAL_REVIEW` 且尚未关联专家（`expert_contact_id IS NULL`）的来信；点击一封来信后，在右栏显示现有“来信详情与处理”面板，明确提供已有三种处理方式：系统推荐候选直接绑定、搜索专家后手动绑定、直接标记已处理。

必须保持：

1. “全部 / 关注 / 待处理”继续使用现有专家会话接口、筛选、分页、草稿和滚动缓存语义。
2. 顶部“收发件箱 N”继续统计已激活收发范围内全部 `MANUAL_REVIEW`，不改成“待匹配”数量。
3. 绑定后仍保持 `MANUAL_REVIEW`：来信从“待匹配”消失、进入对应专家的“待处理”，顶部数量不变。
4. “标记已处理”继续走既有确认框、状态变更和操作日志链；成功后顶部数量及当前列表由服务端重查。
5. `enabled=false` 的真实账号仍属于收信和收发件箱可见范围；只排除 `SIMULATOR_NOOP`。
6. 邮件监控中的待处理列表、旧表格兼容分支及其详情入口继续可用。

明确不做：

- 不修改候选专家推荐算法、置信度、搜索算法或绑定副作用。
- 不新增自动匹配、批量绑定、删除邮件、恢复本轮已手工处理的两封历史系统通知。
- 不改变 `reason_type` 枚举、顶部 badge 口径、专家 `needs_manual_attention` 语义。
- 不给“待匹配”接入方向、日期、标签等高级筛选；本范围仅支持顶部搜索框按发件邮箱或主题搜索。
- 不新增 CSS、不重做页面布局、不复制一套详情 DOM；只复用现有 class 和唯一 `#unmatchedDetailPanel`。
- 不部署、不直接修改线上数据；实施和发布另行批准。

## 关键不变量

### Invariant I-1: 待匹配资格由状态与空关联共同决定
- Rule: “待匹配”成员必须且只能满足 `process_status='MANUAL_REVIEW' AND expert_contact_id IS NULL`；不得用 `reason_type='UNMATCHED_CONTACT'` 代替，因为 V14 只对历史行回填该原因，而撤销人工处理可把 `reason_type` 清空。
- Applies to: 新增 repository 列表/计数查询、service 分支、前端列表与空页回退测试。
- Violation consequence: 已绑定待办混入待匹配，或重新打开的未绑定来信永久不可见。
- 来源: original

### Invariant I-2: 账号范围与顶部 badge 同源
- Rule: 待匹配列表只允许 `MailSenderAccountRepository.findAllByAccountCodeNot(SIMULATOR_NOOP)` 返回的账号；不能增加 `enabled=true` 条件。账号集合为空时直接返回空列表/0，禁止生成空 `IN (...)` SQL。
- Applies to: `UnmatchedInboundMailService.listManualReviewQueue` 的待匹配分支、repository 参数、service 测试。
- Violation consequence: 停用自动外发但仍在收信的真实账号来信被隐藏，列表与顶部 badge 再次口径不一致。
- 来源: K-sender-account-enabled-scope

### Invariant I-3: 两类待办保持分层
- Rule: “待匹配”是邮件级队列；“待处理”是已关联专家的会话级队列。前者读取 `inbound_mail_processing` 行，后者继续读取 `MailboxConversationRepository` 的专家聚合，禁止把未匹配邮件伪造成专家会话。
- Applies to: 前端 API 分流、列表 DTO 归一化、选中状态、分页单位。
- Violation consequence: 空专家 ID 会破坏会话选择、消息加载和按专家分页，并可能重复统计入站邮件。
- 来源: K-mailbox-inbound-source-authority

### Invariant I-4: 处理动作只复用现有写链
- Rule: 新 Tab 不新增任何处理 POST 或客户端直接写状态。系统候选绑定、搜索后绑定、标记已处理必须继续由 `showUnmatchedDetail` + `handleUnmatchedAction` 调用现有 `/bind`、`/search-contacts`、`/mark-resolved`。
- Applies to: app.js 宿主适配、静态详情面板复用、前端行为测试。
- Violation consequence: 两套处理规则产生状态、别名、附件文档或审计日志不一致。
- 来源: K-inbound-processing-write-paths

### Invariant I-5: 唯一详情面板实行可归还 lease
- Rule: 页面只能存在一个 `#unmatchedDetailPanel`。进入待匹配详情时把该既有节点临时挂入右栏；切换 Tab、选中记录经刷新消失、聊天组件 unmount 或关闭详情前，必须先归还到原父节点和原兄弟位置，并调用统一 workbench teardown 使旧异步详情响应失效。
- Applies to: `mcHostMountUnmatchedDetail`、`mcHostReleaseUnmatchedDetail`、MailboxChat 的 Tab/刷新/unmount 生命周期。
- Violation consequence: `innerHTML` 清空会永久销毁静态面板；晚到的旧请求会把错误邮件渲染到新上下文。
- 来源: K-relocated-control-refresh-owner

### Invariant I-6: 待匹配状态不进入专家会话缓存
- Rule: 待匹配选中键单独保存为 `selectedUnmatchedId`，不得写入 `selectedContactId`、`sessionStore`、草稿 Map 或滚动锚点缓存；切回专家 Tab 后原专家会话缓存仍可恢复。
- Applies to: MailboxChat instance 状态、选择/刷新/卸载逻辑、行为测试。
- Violation consequence: 邮件 ID 被误当联系人 ID，导致请求错误、草稿串线或滚动状态污染。
- 来源: original

### Invariant I-7: 原接口默认行为兼容
- Rule: `GET /api/mail/unmatched-inbound` 未传 `unmatchedOnly=true` 时，现有 `reasonType/email/subject/pageSize/pageOffset` 行为与响应字段必须保持不变；新模式仍复用 `InboundMailProcessingListResponse`，不新增响应字段。
- Applies to: controller 参数转发、service 默认分支、既有监控页读路径及测试。
- Violation consequence: 邮件监控“待处理”页被意外缩成未匹配队列，或旧前端解析失败。
- 来源: original

### Invariant I-8: 搜索、排序、分页全部由服务端完成
- Rule: 新查询以 `received_at DESC, id DESC` 排序；`query` 对 `from_email` 与 `subject` 做 OR 模糊匹配；过滤在 `LIMIT/OFFSET` 前完成，前端不得拉取通用待办后再过滤空关联。
- Applies to: repository SQL、service 参数规范化、前端 URL/分页、repository 集成测试。
- Violation consequence: 页数、总数和列表不一致，某页可能为空但后续页仍有待匹配邮件。
- 来源: original

## 样式契约

### S-1: 第四个 Tab
- 复用：`.mc-filters`、`.mc-filter`、`.mc-filter:hover`、`.mc-filter:active`、`.mc-filter[aria-pressed=true]`、`.mc-filter:disabled`（`src/main/resources/static/mailbox-chat.css:9-14,85-88`）。不修改这些规则。
- 新增 CSS：无。
- DOM 结构必须为：

```html
<div class="mc-filters">
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="all" aria-pressed="false">全部</button>
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="followed" aria-pressed="false">关注</button>
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="pending" aria-pressed="false">待处理</button>
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="unmatched" aria-pressed="true">待匹配</button>
</div>
```

- 实值基准：Tab 间距 `12px`；按钮透明背景、左右 `5px`、下内边距 `10px`；选中下边框 `2px solid #2e59c7`、文字 `#244ca9`；hover `#edf3ff/#93b4ec`；active `#dbeafe`；disabled opacity `.45`。
- 禁止项：新增 class、inline style、修改前三个 Tab 的 label/key/顺序或交互状态。

### S-2: 待匹配邮件左列表
- 复用：`.mc-expert-list`（`mailbox-chat.css:15`）、`.mc-person` 及 hover/active（`:16-18`）、`.mc-person-main`（`:19-23`）、`.mc-person-heading`（`:157-158`）、`.mc-badge[data-tone=pending]`（`:29-30,105-106`）、`.mc-pager`（`:33`）。不新增/修改 CSS。
- 新增 CSS：无。
- DOM 结构必须为：

```html
<div class="mc-person" data-active="true" data-unmatched-id="191">
  <button class="mc-person-main" type="button" data-action="mc-select-unmatched" data-unmatched-id="191" aria-current="true">
    <span class="mc-person-heading"><strong>邮件主题</strong></span>
    <small>sender@example.com</small>
    <small>2026-09-10T10:00:00 · 账号：account-code</small>
    <span class="mc-person-meta"><span class="mc-badge" data-tone="pending">未关联专家</span></span>
  </button>
</div>
```

- 文案实值：空列表“暂无待匹配来信”；分页“第 X/Y 页 · 共 N 封”；加载失败“待匹配来信加载失败，请重试”。主题为空显示“（无主题）”。
- 禁止项：关注星标、专家标签、收/发计数、客户端排序、inline style、新 class。

### S-3: 右栏现有详情面板
- 复用：`.mc-conversation`（`mailbox-chat.css:5`）、`.mc-scroll`（`:39`）、`.mc-empty`（`:58`）；详情本体继续复用 `#unmatchedDetailPanel.panel`、`.panel-head/.panel-head-actions`（`styles.css:948-984`）和 `.unmatched-detail-body`（`styles.css:2115-2120`）。不新增/修改 CSS。
- 新增 CSS：无。
- 未选中 DOM：

```html
<section class="mc-conversation" aria-label="待匹配来信处理">
  <div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情">
    <div class="mc-empty">请选择左侧待匹配来信</div>
  </div>
</section>
```

- 选中后：同一 `.mc-scroll` 只接收页面既有 `#unmatchedDetailPanel` 节点；不得 clone、复制 innerHTML 或创建第二个同 id 节点。面板内现有文案“候选推荐联系人”“搜索并手动绑定”“标记已处理”保持原样（`app.js:11283-11313,11336-11345`）。
- 响应式/滚动：继续使用 `.mc-scroll` 的 `overflow:auto; padding:16px 18px` 及现有 `@media(max-width:760px)` 的 `max-height:65dvh; padding:12px`（`mailbox-chat.css:39,70,151`）。嵌入时 `focusMailboxProcessingPanel` 只把该 `.mc-scroll.scrollTop` 置 0，不滚动整个 `.main`。
- 禁止项：新 CSS、inline style、新详情模板、新 action、把面板挂到 `body`、修改现有候选/搜索/标记按钮样式。

## 现状审计

### 需求缺口与直接代码证据

- `mailbox-chat.js:46-54` 只定义 `all/followed/pending` 三个 chip；`:226-229` 只把关注映射为 `followed=true`、待处理映射为 `pendingOnly=true`。
- `mailbox-chat.js:1017-1035` 的列表请求固定为 `/api/mail/mailbox/conversations`；`:1043-1074` 的卡片要求 `contactId/name/receivedCount/sentCount`，不能承载 `expert_contact_id IS NULL` 的邮件。
- `MailboxConversationRepository.kt:491` 的入站聚合必须 join `expert_contact`，所以未关联来信不会进入专家会话；这是当前“顶部有数量、待处理看不到”的直接原因。
- `app.js:10655` 的 `refreshUnmatchedBadge` 读取 `/api/mail/unmatched-inbound?pageSize=1&pageOffset=0` 的 `manualReviewTotal`，而 `UnmatchedInboundMailService.kt:50-59` 的 badge 计数覆盖所有真实账号的 `MANUAL_REVIEW`，不要求已关联专家。
- `app.js:11209-11442` 已完整渲染来信详情；未关联分支在 `11283-11313` 提供候选直接绑定与搜索后绑定，`11336-11345` 提供标记已处理。缺失的是进入该面板的聊天页队列，不是处理能力。

### `inbound_mail_processing`

- Schema/mapping：`V5__create_inbound_mail_processing.sql:1-20` 定义 `process_status NOT NULL`、可空外键 `expert_contact_id`、状态/时间索引；V10 `:97-120` 增加 `in_reply_to/body/cleaned_body/resolved_at/resolved_by`；V14 `:36-50` 增加 `reason_type` 并按当时状态回填；V120 `:17-22` 把唯一键改为 `(sender_account_code, uid_validity, imap_uid)`。本计划无迁移、无字段变更。
- 全部生产运行时写路径（grep：`rg -n "InboundMailProcessing\\(|inboundMailProcessingRepository\\.(save|reopenManualResolved)|UPDATE inbound_mail_processing" src/main`）：
  1. `AutoMailReplyService.kt:1221-1252`、`:1274-1305` — 两个新建行 sink，写入初始关联、状态、原因、正文。
  2. `UnmatchedInboundMailService.kt:136-214` — 绑定专家后把 `expertContactId` 写入，状态仍为 `MANUAL_REVIEW`、原因改为 `MANUAL_BOUND`。
  3. `UnmatchedInboundMailService.kt:217-243` — 旧 service 级人工完成方法，写 `PROCESSED/MANUAL_RESOLVED`；当前 controller 不走此方法。
  4. `PendingMailOperationService.kt:1343-1400` — controller 当前“标记已处理”的真实写链，写状态/解决人/时间并记日志。
  5. `InboundMailProcessingRepository.reopenManualResolved`（repository `:33-47`，service `PendingMailOperationService.kt:1402-1433`）— 条件 UPDATE 恢复 `MANUAL_REVIEW` 并清空 `reason_type/resolved_*`。
  6. 历史迁移 V14/V15 只做一次性 reason 回填，不是运行时入口。
- 全部生产读路径按职责归组（grep：`rg -n "inboundMailProcessingRepository\\.|FROM inbound_mail_processing|JOIN inbound_mail_processing" src/main/kotlin`）：
  1. 当前队列/详情：`UnmatchedInboundMailService`、`UnmatchedInboundMailController`。
  2. 专家会话/旧收发件箱：`MailboxConversationRepository`、`MailRecordRepository`、`MailboxService`。
  3. 入站汇总/标签：`InboundMailSummaryController`、`InboundMailTagRepository/Service`。
  4. 监控/回填：`MailMonitoringService`、`BounceBackfillService`。
  5. 处理/回复/会议：`PendingMailOperationService`、`AutoReplyPreviewService`、`MeetingConfirmationService`。
  6. AI/RAG/材料：`AiTrainingController`、`TrustReplyWorkbenchService`、`RagReplyController`、`ExpertMaterialService`。
- Interaction points：绑定写 `expert_contact_id` 但保留 `MANUAL_REVIEW` → 新待匹配查询移除该邮件 → `MailboxConversationRepository` 在“待处理”专家会话中读到；标记完成写 `PROCESSED` → 新查询和顶部 badge 均移除；撤销完成写回未绑定 `MANUAL_REVIEW` → 新查询重新读到。（来源: K-inbound-processing-write-paths, K-mailbox-inbound-source-authority）

### `mail_sender_account`（本计划只读账号范围）

- Schema：`V1__create_business_tables.sql:1-25` 定义唯一 `account_code` 与 `enabled`；`MailSenderAccountRepository.kt:13-17` 同时存在 enabled-only 和仅排除账号 code 的 finder。
- 生产写路径：`MailSenderAccountService.createAccount/updateAccount/setEnabled/resetTodaySentCount/deleteAccount/resetDailyCounts/pauseAutoSend/resumeAutoSend`；`ManualExpertMailService`、`MeetingScheduleService` 更新最后发信；`ManualInitialOutreachService`、`ManualOutreachTxHelper` 增加发送计数；`ReputationAutoPauseService` 调暂停/恢复；V16/V20/V117 是历史迁移。
- 生产读路径按语义分组：自动发件使用 enabled finder（`MailSenderAccountService`、`SenderAccountAssignmentService`）；收信/收发件箱/待办使用 `findAllByAccountCodeNot(SIMULATOR_NOOP)`（`MailSenderAccountService.listAutoReceiveAccounts`、`MailboxService`、`MailboxConversationService:471-474`、`UnmatchedInboundMailService:50-52`）；监控、附件、日历和 LLM preview 各按现有 finder 读取。
- Interaction point：账号 `enabled` 被管理入口写为 false 后，新待匹配查询仍必须沿用非模拟器集合读到其来信；模拟器即使 enabled=true 也必须排除。（来源: K-sender-account-enabled-scope）

### 复用的处理写链（不修改）

- 候选读取：`UnmatchedInboundMailService.suggestCandidates:75-129` 先用 `inReplyTo → mail_record` 给 90 分候选，再做姓名/邮箱 60/50 分匹配，最多 5 个；新 Tab 不计算候选，只在选中后调用现有详情 GET。
- 绑定写链：`bindToContact:136-214` 依次写 `expert_email_alias`、必要时 APPLICATION 晋级、`expert_contact.needs_manual_attention=true`、`inbound_mail_processing.expert_contact_id`、附件文档关联、`BIND_INBOUND_MAIL` 操作日志。新 Tab 只暴露既有按钮，不增加写路径。
- 标记写链：`PendingMailOperationService.markResolved:1343-1400` 写 `PROCESSED/MANUAL_RESOLVED`，必要时清理专家待办，并写 `MARK_INBOUND_RESOLVED` 日志。新 Tab 继续走该方法。
- 详情读取：controller `GET /unmatched-inbound/{id}`（`:125-148`）返回 record/candidates/contact/logs；`app.js:11220-11231` 并行读取详情、操作日志和 thread tags。

### API 兼容面

- `UnmatchedInboundMailController.list:88-122` 现有参数是 `reasonType/email/subject/pageSize/pageOffset`，响应固定为 `records/totalCount/manualReviewTotal/countsByReasonType`（DTO `:830-865`）。
- `UnmatchedInboundMailService.listManualReviewQueue:31-67` 当前 `records/totalCount` 来自未按账号收口的通用队列，`manualReviewTotal/countsByReasonType` 才按非模拟器账号计数。
- 新模式必须在同一 endpoint 增加默认 `false` 的 `unmatchedOnly` 和可空 `query`；只切换 `records/totalCount` 的查询。`manualReviewTotal/countsByReasonType` 继续保持现有全局 badge 口径。
- 邮件监控 `app.js:12369-12387` 未传 `unmatchedOnly`，因此继续得到原通用 MANUAL_REVIEW 列表（I-7）。

### 前端生命周期与状态

- `mailbox-chat.js:754-756` 初次挂载会 `host.innerHTML = skeletonHtml()`；`:1179-1183`、`:1397-1405`、`:1754-1763` 会改写右栏 `innerHTML`；`:4129-4152` unmount 最终清空 host。静态详情节点进入这些区域前必须建立归还责任（I-5）。
- `showUnmatchedDetail` 开头调用 `unmountMailboxTrustReplyHosts`；该 teardown 在 `app.js:186-197` 增加 `liveDetailLoadSeq`，而详情请求在 `11211/11226/11248` 用序号拒绝晚响应，可直接复用为 lease 失效机制。
- `refreshMailboxAfterPendingAction:10837-10840` 统一执行 `loadMailbox + refreshUnmatchedBadge`；绑定/标记成功都调用它（`:11463-11501`）。归还 lease 必须放在该统一刷新入口之前，不能分散补两个 action。
- `mailbox-chat.js:75` 的 `sessionStore` 及 `:243-424` 的会话缓存均以 contactId 为身份。邮件级 selection 必须独立，不进入该 Map（I-6）。

### 前端样式盘点

- 可复用 class：完整引用见 S-1～S-3；本计划不修改 `mailbox-chat.css` 或 `styles.css`。
- 设计 token：Tab 选中 `#2e59c7/#244ca9`；列表 hover `#eff5ff`、active `#eaf1ff`、边框 `#c2d3f2`；待办 badge `#fff5e9/#f6dfc6/#bb7838`；主体文字 `#475569`、弱文字 `#64748b`；卡片圆角 `10px`、两栏圆角 `14px`；按钮 focus `2px solid #3b82f6`。
- 当前 Tab 基线（`mailbox-chat.js:50-54`）：

```javascript
const FILTER_CHIPS = [
    { key: CHIP_ALL, label: "全部" },
    { key: CHIP_FOLLOWED, label: "关注" },
    { key: CHIP_PENDING, label: "待处理" }
];
```

- 当前骨架基线（`mailbox-chat.js:729-737`）：`.mc-filters → .mc-expert-list → .mc-pager` 位于左栏，右栏是唯一 `.mc-conversation`。新 DOM 必须保持该层级，仅增加第四个由数组生成的按钮，并在右栏内部复用 `.mc-scroll`。
- 既有 class 全部使用位置：本计划不就地修改任何 class，因此其他使用点不受影响；`mailboxChatStyle.test.js:101-149` 已对新增字面 class、inline style及非 `.mail-chat` 作用域规则做全局门禁。

### 知识输入处置

- 已采用：K-inbound-processing-write-paths、K-mailbox-inbound-source-authority、K-sender-account-enabled-scope、K-relocated-control-refresh-owner；均已用当前 grep/源码重新核验。
- 有意识排除：K-mailbox-popover-scope-and-fixed-containing-block。新 Tab 不新增 fixed 后代，确认框仍由既有全局 `openActionDialog` 管理，故不引入 portal/CSS 改造。
- 衰减：本次采用条目均在 90 天内，无需归档。

## 实现方案

### T1：增加精确、分页正确的待匹配只读查询

文件：

- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`

遵守：I-1～I-3、I-7、I-8。

1. Repository 新增成对方法 `findUnmatchedManualReviewQueue(accountCodes, query, limit, offset)` / `countUnmatchedManualReviewQueue(accountCodes, query)`：
   - 固定 `process_status='MANUAL_REVIEW'`、`expert_contact_id IS NULL`、`sender_account_code IN (:accountCodes)`；
   - `query IS NULL OR from_email LIKE ... OR subject LIKE ...`；
   - 列表固定 `ORDER BY received_at DESC, id DESC LIMIT/OFFSET`；count 使用逐字相同 WHERE。
2. Service 给 `listManualReviewQueue` 增加默认参数 `unmatchedOnly=false`、`query=null`：
   - 先按现有 finder 取得非模拟器账号 code；
   - `unmatchedOnly=true` 时 trim query，空串转 null；账号集合为空直接使用 `emptyList()/0L`；否则调用新查询；
   - 默认分支继续调用现有 `findManualReviewQueue/countManualReviewQueue`；
   - `manualReviewTotal/countsByReasonType` 仍按当前账号集合统计全部 MANUAL_REVIEW，不随 `unmatchedOnly/query` 缩小。
3. Controller 的 list 增加 `@RequestParam(defaultValue="false") unmatchedOnly: Boolean` 和可空 `query`，转发给 service；不改 DTO。
4. Repository IT 用真实 MySQL 构造：未绑定 MANUAL_REVIEW、已绑定 MANUAL_REVIEW、未绑定 PROCESSED、不同账号及相同时间不同 id；断言成员、OR 搜索、稳定顺序、分页与 count 同 WHERE。
5. Service test 断言：新模式传入包括 disabled 账号的非模拟器 code；全局 `manualReviewTotal` 可大于筛选后的 `totalCount`；空账号不调用 IN 查询；默认模式仍调用旧 repository 方法。

数据关系：本任务只新增 `inbound_mail_processing` 读路径，不新增写路径。它消费 AutoMailReplyService、bind/resolve/reopen 既有写入；测试必须覆盖每类状态对队列成员资格的影响。

### T2：增加第四 Tab 与邮件级列表模式

文件：

- `src/main/resources/static/mailbox-chat.js`
- `src/test/js/mailboxChatBehavior.test.js`

遵守：I-1、I-3、I-5～I-8，S-1～S-3。

1. 增加 `CHIP_UNMATCHED='unmatched'`，追加到 `FILTER_CHIPS` 最后；不修改前三项。
2. 把列表获取归一为两条显式分支：
   - 专家模式保持当前 `/api/mail/mailbox/conversations?page&size...` 和现有 filters；
   - 待匹配模式请求 `/api/mail/unmatched-inbound?unmatchedOnly=true&pageSize=20&pageOffset=<page*20>&query=<q>`，只归一化 `records → items`、`totalCount → total`，不传高级筛选。
3. 待匹配模式把搜索框 aria-label/placeholder 改为“搜索待匹配来信”/“搜索发件邮箱、主题”，隐藏现有 `mc-more-filters` 按钮；离开后恢复“搜索专家”/“搜索专家姓名、邮箱”及按钮。已提交的高级筛选值只保留，不参与待匹配请求。
4. 新增独立 `selectedUnmatchedId` 和邮件卡片 renderer，严格使用 S-2 DOM；pager 单位切为“封”。不复用 `selectedContactId`、`resolveFocusAndSelection` 或 sessionStore。
5. 点击邮件时：更新 active 卡片，先生成 S-3 的 `.mc-scroll` 宿主，再调用全局 `mcHostMountUnmatchedDetail(scrollHost, id)`。没有宿主函数时右栏显示“来信处理面板不可用，请刷新页面重试”，不伪造处理 UI。
6. 以下边界先调用 `mcHostReleaseUnmatchedDetail`，再允许右栏或根节点 `innerHTML` 改写：离开待匹配 Tab、当前选中 id 不在服务端刷新结果、切页、组件 unmount。当前 id 仍存在时保留详情，不重复请求。
7. 复用现有 listSeq 竞态守卫；切 Tab 后晚到的待匹配响应不得覆盖专家列表，反向同理。
8. 扩展行为测试：四 Tab 顺序/参数、搜索 URL、邮件 DOM/分页单位、点击挂载、切 Tab/切页/刷新消失/unmount 归还、晚响应隔离，以及前三 Tab 原参数和草稿缓存回归。

### T3：为现有详情面板增加聊天右栏宿主 lease

文件：

- `src/main/resources/static/app.js`
- `src/test/js/mailboxChatBehavior.test.js`

遵守：I-4～I-7，S-3。

1. 在 app.js 增加唯一 lease 状态，保存 `#unmatchedDetailPanel` 的原父节点和原 `nextSibling`；不得保存/复制面板 HTML。
2. 新增全局 `mcHostMountUnmatchedDetail(hostEl, id)`：校验宿主和正整数 id，先释放旧 lease，再把同一 panel 节点 append 到传入的 `.mc-scroll`，最后 `await showUnmatchedDetail(id)`。失败时归还节点并继续向组件抛错，供右栏显示错误。
3. 新增幂等 `mcHostReleaseUnmatchedDetail()`：调用 `unmountMailboxTrustReplyHosts()` 使未完成详情响应失效；隐藏 panel、清空 `state.mailbox.detailContext`；若原 `nextSibling` 仍属于原父节点则 `insertBefore`，否则 append 回原父节点；重复调用无副作用。
4. 修改 `focusMailboxProcessingPanel`：panel 位于 `.mc-scroll` 内时只设该滚动容器 `scrollTop=0`；旧位置继续当前 `.main` 平滑滚动。
5. `refreshMailboxAfterPendingAction` 在 `loadMailbox()` 前释放 lease；`close-unmatched-detail` 分支也调用统一 release。绑定/标记现有 POST、确认框、提示语、日志写入不改。
6. 在既有 `mailboxChatBehavior.test.js` 的 app adapter 区增加真实最小 DOM 测试：挂载后节点身份相同、原位置保存；release 精确归位；重复 release 安全；切换期间的 `liveDetailLoadSeq` 失效；禁止 `cloneNode`/第二个 id。

数据关系：T3 不新增写路径。处理面板的所有按钮继续由原节点上的 `click/input/paste` listener 处理；节点移动不会丢监听器。

## 变更文件清单

| # | 文件 | 类型 | 精确改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 后端生产 | 新增待匹配列表/count SQL |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 后端生产 | 增加 unmatchedOnly/query 分支并沿用账号范围 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 后端生产 | 增加两个可选 GET 参数并转发 |
| 4 | `src/main/resources/static/mailbox-chat.js` | 前端生产 | 第四 Tab、邮件级列表/选择/lease 生命周期 |
| 5 | `src/main/resources/static/app.js` | 前端生产 | 唯一详情面板宿主 lease 与滚动/刷新接入 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepositoryTest.kt` | 后端测试 | SQL 成员、搜索、顺序、分页/count IT |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` | 后端测试 | 分支、账号范围、badge/default 兼容测试 |
| 8 | `src/test/js/mailboxChatBehavior.test.js` | 前端测试 | Tab/API/DOM/竞态/lease/回归测试 |

文件数：8（≤10）。不允许实施时修改本表之外文件；若发现必须改 CSS、HTML、DTO 或处理 service，先停止并修订计划。

## 验收标准

### 机器验证

- I-1：Repository IT 断言只有 `MANUAL_REVIEW + expert_contact_id NULL` 入选；`reason_type NULL` 仍入选，已绑定/PROCESSED 均排除。
- I-2：Service test 断言 disabled 真实账号 code 被传入、`SIMULATOR_NOOP` 不在集合、空集合不调用新 SQL。
- I-3：JS test 断言 unmatched 只请求 `/unmatched-inbound`，前三 Tab 只请求 `/mailbox/conversations`；邮件 id 从不进入 contact 消息 endpoint。
- I-4：JS/app source 与行为断言三种动作仍来自同一个移动后的 `#unmatchedDetailPanel`，endpoint 分别保持 `/bind`、`/search-contacts`、`/mark-resolved`；无新增处理 POST。
- I-5：JS DOM test 以对象 identity 断言 mount/release 前后是同一节点；切 Tab、记录消失、unmount 均先归还；晚详情响应不落 DOM。
- I-6：JS test 在待匹配选择前后检查 `selectedContactId`、会话 draft/scroll cache 不变；切回原专家仍恢复原会话。
- I-7：Service 默认分支测试及现有监控/JS 全量测试通过；无参 endpoint 仍返回通用 MANUAL_REVIEW。
- I-8：Repository IT 断言 OR 搜索、`received_at DESC,id DESC`、第 2 页与 count 一致；JS 断言 offset=`page*20` 且不做客户端过滤。
- S-1：DOM 测试断言四个 label/key/顺序与 active aria；`mailbox-chat.css` 无 diff。
- S-2：DOM 测试断言只使用契约骨架、空文案及“共 N 封”；`mailboxChatStyle.test.js` 的 class 白名单/无 inline style门禁继续通过。
- S-3：DOM 测试断言右栏只有 `.mc-scroll` 与唯一详情 panel；未新增 CSS/inline style；桌面和 760px 以下人工截图核对滚动。

执行命令：

```bash
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailboxChatBehavior.test.js
mvn -q -Dtest=UnmatchedInboundMailServiceTest test
mvn -q -DmigrationIt=true -Dtest=InboundMailProcessingRepositoryTest test
mvn test
git diff --check
```

Repository IT 需要 Docker；若环境无 Docker，只能记录为环境阻塞，不能以 mock 测试替代 SQL 验证。

### 跨路径集成断言

1. 新入站 sink 写 `MANUAL_REVIEW + NULL expert_contact_id` → 待匹配查询/列表读到。
2. `/bind` 写入 contactId 且保留 MANUAL_REVIEW → 待匹配重查移除、专家待处理重查出现、顶部 badge 不变。
3. `/mark-resolved` 写 PROCESSED → 待匹配和顶部 badge 同时减少，操作日志仍可在详情/日志页读到。
4. `/cancel-resolved` 对未绑定记录恢复 MANUAL_REVIEW 且清 reasonType → 待匹配重查重新出现。
5. 管理端把真实账号 enabled 改为 false → 该账号既有未匹配来信仍可见；模拟器记录不可见。

## 人工验收清单

### A-1: 第四 Tab 位置与空态
- 前置条件: 测试库不存在符合 `MANUAL_REVIEW + expert_contact_id NULL + 非模拟器账号` 的记录。
- 操作步骤: 1. 打开收发件箱；2. 查看左侧 Tab；3. 点击“待匹配”。
- 预期结果: Tab 顺序逐字为“全部、关注、待处理、待匹配”；待匹配显示蓝色 `2px` 下划线；左栏显示“暂无待匹配来信”，右栏显示“请选择左侧待匹配来信”，分页为“第 1/1 页 · 共 0 封”。
- 覆盖: I-1、I-3、S-1～S-3、需求 observable outcome

### A-2: 待匹配详情展示三种处理方式
- 前置条件: 在测试环境通过真实收信或测试夹具准备 1 封非模拟器账号的 `MANUAL_REVIEW` 且 `expert_contact_id=NULL` 来信；主题为 `Unmatched acceptance mail`，发件人为 `unmatched.acceptance@example.com`。
- 操作步骤: 1. 进入“待匹配”；2. 点击该邮件。
- 预期结果: 左卡片显示主题、发件邮箱、收信时间、账号及“未关联专家”；右栏标题为“来信详情与处理”，同时能看到“候选推荐联系人”“搜索并手动绑定”和顶部“标记已处理”。页面中只有一个详情面板。
- 覆盖: I-3～I-5、S-2、S-3

### A-3: 系统候选直接绑定后的跨队列状态
- 前置条件: 1 封待匹配来信，详情中至少有 1 个系统推荐候选；记录绑定前顶部 badge 为 N。
- 操作步骤: 1. 点击候选后的“绑定”；2. 填写操作人并确认；3. 观察待匹配；4. 切到“待处理”。
- 预期结果: 该邮件从待匹配消失；对应专家出现在待处理并包含该来信；顶部 badge 仍为 N；操作日志新增 `BIND_INBOUND_MAIL`。
- 覆盖: I-1、I-3、I-4、interaction bind→read、must-not-change 3

### A-4: 搜索后手动绑定
- 前置条件: 1 封没有系统推荐候选的待匹配来信；系统内存在可按 ORCID/姓名/邮箱找到的专家。
- 操作步骤: 1. 选中来信；2. 在“搜索并手动绑定”输入专家邮箱；3. 点击“搜索”；4. 选择结果并完成绑定。
- 预期结果: 搜索结果显示目标专家；绑定成功后邮件退出待匹配、进入该专家待处理；使用原“绑定并添加别名”链，无新增处理入口。
- 覆盖: I-4、interaction bind→read、需求处理方式 2

### A-5: 直接标记已处理
- 前置条件: 1 封待匹配来信；操作前顶部 badge 为 N，待匹配总数为 M。
- 操作步骤: 1. 选中来信；2. 点击“标记已处理”；3. 填写操作人/备注并确认。
- 预期结果: 成功提示“已标记为处理完成”；待匹配变为 M-1，顶部 badge 变为 N-1；邮件不进入待处理；日志新增 `MARK_INBOUND_RESOLVED`。
- 覆盖: I-4、interaction resolve→read、must-not-change 2/4

### A-6: 撤销处理重新进入待匹配
- 前置条件: 1 封未绑定来信已由人工标记为 `PROCESSED/MANUAL_RESOLVED`。
- 操作步骤: 1. 在现有支持“取消处理”的入口执行取消；2. 返回收发件箱并刷新；3. 打开“待匹配”。
- 预期结果: 该邮件重新出现；即使 `reasonType` 已清空也不漏；顶部 badge 增加 1。
- 覆盖: I-1、interaction reopen→read

### A-7: 账号范围回归
- 前置条件: disabled 真实账号和 `SIMULATOR_NOOP` 各准备 1 封未绑定 MANUAL_REVIEW 测试记录。
- 操作步骤: 1. 刷新收发件箱；2. 打开待匹配；3. 搜索两封主题。
- 预期结果: disabled 真实账号邮件可见；模拟器邮件不可见；顶部 badge 与该可见范围一致。
- 覆盖: I-2、must-not-change 5

### A-8: 搜索、排序与分页
- 前置条件: 准备 21 封待匹配来信，其中 2 封主题或发件邮箱含 `acceptance-key`；至少 2 封 `received_at` 相同且 id 不同。
- 操作步骤: 1. 打开待匹配；2. 查看第 1/2 页；3. 翻到下一页；4. 搜索 `acceptance-key`。
- 预期结果: 每页最多 20 封；相同时间按 id 降序；下一页无重复/遗漏；搜索结果恰为 2 封且总数显示 2。
- 覆盖: I-8、S-2

### A-9: Tab 切换与详情 lease
- 前置条件: 待匹配有 1 封来信；“全部”中有 1 个专家会话且编辑器有未发送草稿。
- 操作步骤: 1. 选中专家并记住草稿；2. 切待匹配并打开详情；3. 快速切回全部；4. 再切待匹配；5. 离开收发件箱再返回。
- 预期结果: 不出现重复详情、空白永久面板或旧邮件串入；专家草稿仍在；每次待匹配未选中时右栏显示“请选择左侧待匹配来信”。
- 覆盖: I-5、I-6、K-relocated-control-refresh-owner

### A-10: 前三个 Tab 与高级筛选回归
- 前置条件: 全部/关注/待处理各有可识别专家；高级筛选已选择一个账号或标签。
- 操作步骤: 1. 依次点击全部、关注、待处理；2. 在待匹配确认“⋯”隐藏；3. 切回全部并打开“⋯”。
- 预期结果: 全部无 followed/pending 参数、关注只加 `followed=true`、待处理只加 `pendingOnly=true`；原高级筛选值仍在且可应用；待匹配不应用这些条件。
- 覆盖: I-7、must-not-change 1

### A-11: 旧监控待处理入口回归
- 前置条件: 邮件监控“待处理”有 1 条已关联 MANUAL_REVIEW 记录。
- 操作步骤: 1. 进入邮件监控待处理；2. 打开该行详情；3. 返回列表。
- 预期结果: 该已关联记录仍显示；详情仍在原页面位置打开；未被 `unmatchedOnly` 过滤。
- 覆盖: I-7、must-not-change 6

### A-12: UI 实值目测
- 前置条件: 桌面宽度大于 1100px，并另用宽度小于等于 760px 的浏览器窗口。
- 操作步骤: 1. 两种宽度分别打开待匹配；2. 对照 S-1～S-3 检查 Tab、卡片、badge、右栏滚动；3. 检查浏览器控制台和 DOM。
- 预期结果: 选中 Tab 下划线 `#2e59c7/2px`；待匹配 badge 为 `#fff5e9` 背景、`#bb7838` 文字；右栏桌面内边距 `16px 18px`，窄屏 `12px` 且最大高度 `65dvh`；无新增 inline style、无重复 `#unmatchedDetailPanel`、无横向溢出。
- 覆盖: S-1～S-3

人工验收开始时，必须从本节导出 `docs/plans/2026-09-10/mailbox-unmatched-tab-acceptance.md`；本计划阶段不生成该衍生文件。
