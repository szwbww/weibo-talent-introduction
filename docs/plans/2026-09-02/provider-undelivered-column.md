# 服务商分布表：用「未送达(人)」替换「硬退率 + 软退」

创建日期：2026-09-02
适用 skill：create-p
前置：本仓库已落地 `docs/plans/2026-09-02/monitoring-window-and-cohort-reply-rate.md`（时间窗口 + 队列口径回复率），本计划在其基础上改动。

---

## 需求描述

### Observable outcome

1. **O-1**：「按收件方服务商分布」表把现有的 `硬退率` 与 `软退` 两列，替换为一列 **`未送达(人)`**（净减一列）。数值 = 该服务商桶下，窗口内「发送失败」与「被退回」的去重专家数，**不区分硬退 / 软退**。
2. **O-2**：该表下方多一行灰字表尾，逐字为：`另有 N 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。` N 为 0 时不显示该行。
3. **O-3**：概览卡片「最高退信服务商」改名为「最高未送达服务商」，值由 `provider (x.x%)` 改为 `provider (未送达人数/队列人数)`。

### What must NOT change

1. **N-1**：表的前 6 列（服务商 / 分布 / 队列(人) / 已回复(人) / 窗口内回复率 / 7日成熟回复率）的取值、口径、样本不足灰显与「样本不足」徽标行为完全不变。
2. **N-2**：`PROVIDER_ORDER`（`gmail, outlook, yahoo, edu, tencent, netease, other`）的顺序与桶定义不变；`ProviderResolver.resolve()` 一行不改。
3. **N-3**：`BounceDetector`、`BounceCollectionService`、`ImapMailReceiveService` 一行不改 —— 本计划不依赖 HARD/SOFT 分类是否准确。
4. **N-4**：`/api/mail-monitoring/bounce-stats`（`getBounceStats`）与「退信名单」子标签（`/api/mail/bounces`）行为不变；`BounceRateMonitorService` 的 7 天 / 5% 自动暂停逻辑不变。
5. **N-5**：「按专家地区分布」表、域信誉趋势、发件账号限额监控、其余 5 个明细子标签均不改。
6. **N-6**：概览区其余 14 张卡片的文案、数值与 hint 不变（含两条「细分统计不可相加」原文，`src/test/js/monitoringDateDefault.test.js:101-103` 断言）。

### Out of scope（明确不做）

- **不做**「未送达名单」页面（一行一个专家的明细）。
- **不做**退订（`email_suppression`）的任何统计或展示。
- **不做**未送达率（只给绝对数，率由使用者对着同行「队列(人)」自行判断，理由见 L-1）。
- **不做**地区表的未送达列。
- **不做**中文退信模板的 DSN 解析修复、`failed_recipient` 提取增强、HARD/SOFT 分类修正（另案，见 L-3）。
- **不做**发送失败的错误原因（`error_summary`）展示。
- **不做**「未送达」按发信账号维度的拆分。

---

## 关键不变量

### Invariant I-1: 未送达 = 发送失败 ∪ 被退回，按专家去重

- Rule：`undeliveredCount` = 满足下列任一条件的 **去重 `expert_contact_id` 数量**：
  (a) 存在 `mail_record` 行 `direction='OUTBOUND' AND mail_type='INTRODUCTION' AND send_status='FAILED'` 且 `created_at` 落在 `[from, to)`；
  (b) 存在 `bounce_record` 行 `original_expert_contact_id IS NOT NULL` 且 `received_at` 落在 `[from, to)`。
  两个来源用 `UNION`（非 `UNION ALL`）合并，外层再 `COUNT(DISTINCT expert_contact_id)`；同一专家既发送失败又退信、或被退多次，**只计 1**。
- Applies to：本计划新增的唯一查询 `MailRecordRepository.aggregateUndeliveredByDomain`。
- Violation consequence：用 `UNION ALL` 或 `COUNT(*)` 会让同一个人被重复计数——你截图的线上数据里同一专家多次退信是常态（同一批次重投），数字会虚高且无法与「队列(人)」对比。
- 来源：original

### Invariant I-2: 未送达的时间口径是「事件发生时间」，不是「首发时间」

- Rule：来源 (a) 用 `mail_record.created_at`，来源 (b) 用 `bounce_record.received_at`，**都不是** `sent_at`。因此「近 7 天未送达」= 这 7 天内发生的投递失败，**可能对应更早批次发出的邮件**，与同行「队列(人)」（= 窗口内首发的去重专家）不是同一批人。
- Applies to：同 I-1 的查询；`## 已知限制` L-1。
- Violation consequence（依据）：`mail_record` 中 `send_status='FAILED'` 的三个写入点全部同时写 `sentAt = null`（`ManualOutreachTxHelper.kt:123/126`、`ManualReplySendAttemptService.kt:303/304`、`:320/322`），用 `sent_at` 过滤会得到恒为 0 的结果。现有 `countFailedOutboundBetween`（`MailRecordRepository.kt:201-209`）正因如此用 `created_at`，本计划与之保持一致。（来源: K-failed-mail-record-has-null-sent-at）
- 来源：original

### Invariant I-3: 分桶键是专家邮箱域名，不是 `bounce_record.failed_recipient`

- Rule：服务商桶由 `SUBSTRING_INDEX(ec.expert_email, '@', -1)` 经 `ProviderResolver.resolve("x@$domain")` 得到，`ec` 来自 `JOIN expert_contact ec ON ec.id = <去重后的 expert_contact_id>`。**禁止**使用 `bounce_record.failed_recipient` 分桶。
- Applies to：同 I-1 的查询。
- Violation consequence：`failed_recipient` 由 `BounceDetector.extractFailedRecipient(body)` 文本启发式提取，允许为 NULL（`V43__add_bounce_record_failed_recipient.sql:2` 建列即 NULL）。`SUBSTRING_INDEX(NULL,'@',-1)` 得 NULL → `providerResolver.resolve(null)`（`ProviderResolver.kt:8-9`：domain 为空返回 `"other"`）→ **所有解析失败的退信全部堆进 `other` 桶**，让 other 虚高、其余桶虚低。这正是被替换掉的 `aggregateBouncesByDomain`（`BounceRecordRepository.kt:58-68`）的缺陷。
  归因质量对比（2026-09-02 线上「退信名单」截图，9 条可见记录）：`failed_recipient` 有值 8 条，`original_expert_contact_id`（表现为「关联专家」列）有值 9 条中的 8 条且**多覆盖一条**（`Will Steffen` 那条 failed_recipient 为 `-` 但靠 Message-ID 反查到了专家）。换 join 键严格优于现状。
- 来源：original

### Invariant I-4: 关联不到专家的退信不进任何桶，单独计数并显示

- Rule：「未归因退信」的定义是 **`bounce_record` 中无法经 `expert_contact` 分桶的行**，恰好两类，缺一不可：
  (a) `original_expert_contact_id IS NULL`；
  (b) `original_expert_contact_id` 非空，但 `expert_contact` 中**不存在**该 id 的行（孤儿引用）。
  两类都**不得**塞进 `other` 或任何桶，必须由 `BounceRecordRepository.countUnattributedBouncesBetween(from, to)`
  一并计数，随 `ProviderDistributionResponse.unattributedBounceCount` 返回，渲染成表尾一行（O-2）。
  计数单位是**退信条数**，不是人数（无法去重到人）。
  表尾文案必须与判据一致，逐字为 **「另有 N 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。」**
  —— **禁止**写成「收件人与关联专家均缺失」，因为查询根本不判断 `failed_recipient`（见下）。
- Applies to：`BounceRecordRepository.countUnattributedBouncesBetween`；`MailMonitoringService.providerDistribution`；`app.js` 的 `renderMonitoringProviderDistribution`。
- Violation consequence：
  - 漏 (a)：线上确实存在三个归因字段全空的记录（2026-09-02「退信名单」截图末行）。
  - 漏 (b)：`bounce_record` **没有任何 FOREIGN KEY**（`V29__create_bounce_record.sql` 全文无 `CONSTRAINT`/`FOREIGN KEY`，仅三个 `INDEX`），
    而 `mail_record.expert_contact_id` **有** FK（`V1__create_business_tables.sql:16`）。因此只有退信这一支会产生孤儿引用。
    主查询的 `JOIN expert_contact` 会静默丢弃孤儿行；若表尾也只判 NULL，这些退信在 UI 上**凭空消失**，总数对不上且无人察觉。
    生产代码目前不删 `expert_contact`（全仓 `grep 'expertContactRepository.delete\|DELETE FROM expert_contact' src/main/` 无命中），
    但测试与人工 SQL 会删——本计划新增的 IT 正是通过 `seedBaseContact()` 的 `DELETE FROM expert_contact` 制造这一情形。
  - 文案与判据不一致：运营看到「收件人缺失」，去查却发现这些退信的 `failed_recipient` 有值，会误判为系统 bug 或数据错乱。
- 来源：original（(b) 与文案两点为 2026-09-02 人工评审发现，P0/P1）

### Invariant I-9: 未送达列与「最高未送达服务商」卡片不得以队列人数为前提

- Rule：`未送达(人)` 单元格**恒渲染 `undeliveredCount` 本身**（0 就显示 `0`），不得出现 `cohort > 0 ? ... : '-'` 之类的分支。
  「最高未送达服务商」卡片的候选集是 **`undeliveredCount > 0` 的全部行**（不是 `sentCount > 0` 的行），
  排序键是 **`undeliveredCount` 降序**，**不做任何除法**。
- Applies to：`app.js` 的 `renderMonitoringProviderDistribution`（单元格）与 `renderMonitoringCards`（卡片候选集与排序）。
- Violation consequence：未送达按事件时间落窗口（I-2），队列按首发时间落窗口，两者是不同批人。
  完全可能出现「某服务商本窗口没有新首发（队列 = 0），但上一批发出去的邮件本窗口退回来了（未送达 > 0）」——
  这恰恰是最该被看见的情形。若按队列为 0 显示 `-`，真实未送达数被直接隐藏；
  若卡片沿用现有的 `.filter(row => row.sentCount > 0)`（`app.js:11202`），该服务商永远选不中，
  且对 `cohort = 0` 做除法会得到 `Infinity`/`NaN`。原计划 S-1 与 B-2 曾与 I-2 / L-1 自相矛盾，本条为修正。
- 来源：original（2026-09-02 人工评审发现，P0）

### Invariant I-5: 表格列数与 colspan 必须同步

- Rule：表头列数由 8 变为 7；`renderMonitoringProviderDistribution` 中空状态行的 `colspan` 必须由 `8` 改为 `7`；新增的表尾行 `colspan` 也必须是 `7`。
- Applies to：`app.js` `renderMonitoringProviderDistribution`。
- Violation consequence：colspan 与实际列数不符会让空状态行 / 表尾行的背景与边框错位一格。
- 来源：original

### Invariant I-6: `hardBounceCount` / `softBounceCount` 字段被删除后，其全部消费点必须同步

- Rule：`ProviderStatRow` 删除 `hardBounceCount`、`softBounceCount` 两个字段后，前端两处消费点必须一并改：`renderMonitoringProviderDistribution`（`app.js:11273/11285`）与 `renderMonitoringCards` 的 `worstBounceProvider`（`app.js:11202-11207/11224-11226`）。
- Applies to：`MailMonitoringResponses.kt` 的 **`ProviderStatRow`**、`MailMonitoringService.providerDistribution` 及其 `MutableProviderStats`、`app.js`。
- **范围限定（必读）**：本条**只约束服务商分布链路**。`BounceStatsResponse`（`MailMonitoringResponses.kt:116-117`）与
  `MailMonitoringService.getBounceStats`（`:277-278`）**必须保留**这两个字段——它们服务于 `/bounce-stats` 端点，
  受 N-4 保护。因此**禁止**把验收写成「全仓零命中」，那与 N-4 直接冲突且不可满足。
- Violation consequence：JS 读不存在的字段得 `undefined`，`(undefined || 0) / cohort` 恒为 0——**不会报错**，「最高未送达服务商」会静默显示第一个非零队列的服务商且比率恒为 0%。这是无声失败，必须靠验收断言兜住。
- 来源：original（范围限定为 2026-09-02 第三轮评审修正，P0）

### Invariant I-7: 接口返回形状由数组变为对象，前端的错误兜底必须同步

- Rule：`/api/mail-monitoring/provider-distribution` 的返回由 `List<ProviderStatRow>` 改为 `ProviderDistributionResponse(rows, unattributedBounceCount)`。`app.js` `loadMonitoring` 中该请求的 `.catch(() => [])` 必须改为 `.catch(() => ({ rows: [], unattributedBounceCount: 0 }))`，且 `state.monitoring.providerDistribution` 仍赋值为**数组**（`resp?.rows || []`），新增 `state.monitoring.unattributedBounceCount`。
- Applies to：`MailMonitoringController.kt`、`app.js` `loadMonitoring`。
- Violation consequence：`renderMonitoringCards` 对 `state.monitoring.providerDistribution` 直接调 `.filter` / `.map`（`app.js:11197-11207`）。若把对象整体赋给它，接口正常时页面直接抛 `TypeError: providers.filter is not a function`，整块概览白屏；若只改赋值不改 catch，接口失败时兜底成 `[]`——那反而是对的，所以只有前者会炸，属于「正常路径炸、异常路径不炸」的反直觉缺陷。
- 来源：original

### Invariant I-8: 静态资源缓存键三元组同值同时 bump

- Rule：`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三处必须同值，本次一起由 `20260902-monitoring-window` 改为 `20260902-undelivered`。
- Applies to：`index.html:11 / :2110 / :2111`；**4 个**固化该字符串的测试文件——
  `batchSendTaskConsoleVisualFix.test.js:49-51`（`assert.ok(html.includes('<res>?v=<literal>'))`）、
  `checkRepliesRelocation.test.js:11`、`manualReplySubjectPrefill.test.js:13`、
  `overlayAndDialogContrast.test.js:15`（后三者为 `const CACHE_KEY = "<literal>";`）。
- Violation consequence：只 bump 部分键或漏改任一测试 → `node --test` 断言失败，`mvn test` 在 test 阶段中止。
- 来源：K-frontend-cache-key-triad（**该条曾两次记错**：原记 4 个文件固定字符串；2026-09-02 第二轮据
  `grep 'styles.css?v='` 订正为「只剩 1 个」——**该订正本身是错的**，漏掉了 `const CACHE_KEY` 这种拼写。
  第三轮实测确认为 4 个，并把「按当前键值反查」的健壮命令写进条目，见变更文件清单下方。）

---

## 样式契约

本计划触及 `index.html`（仅缓存键）与 `app.js`。**新增 CSS 行数：0。** 全部复用既有 class 与既有写法。

### S-1: `未送达(人)` 列

- **复用**：`th, td`（`styles.css:983-987`，`padding:8px 14px`）。数值单元格为纯文本，无 class。
- **新增**：无。
- **DOM 结构**：表头该列逐字为 `<th>未送达(人)</th>`，位置在 `<th>7日成熟回复率</th>` 之后、行尾。数据单元格**恒为一个数字**，包括 0：

```html
<td>12</td>
<!-- undeliveredCount 为 0 时 -->
<td>0</td>
```

  **禁止**因「队列(人) 为 0」而把本格渲染成 `-` 或空——见 I-9。前 6 列在队列为 0 时的既有渲染（回复率 `-`、成熟回复率 `—`）不受影响，逐字保留。
- **禁止项**：inline style；新 class；给该列加进度条或百分比；任何以 `cohort` 为条件的分支；改动前 6 列任何一格。

### S-2: 表尾「未归因退信」提示行

- **复用**：与**同一函数内既有的空状态行逐字同构**——现有写法为
  `app.js:11287`：`<tr><td colspan="8" class="text-muted" style="text-align:center;">暂无数据</td></tr>`
  该 `style="text-align:center;"` 是**既有代码**，本契约沿用其形状，不视为新增 inline style。
- **新增**：无 CSS。
- **DOM 结构**：追加在 `tbody` 内容末尾（在数据行之后），`colspan` 为 **7**（I-5）：

```html
<tr><td colspan="7" class="text-muted" style="text-align:center;">另有 6 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。</td></tr>
```

  `unattributedBounceCount` 为 0 时**不渲染这一行**。
- **禁止项**：在 `index.html` 里新增任何 DOM 节点；把提示放进 `.panel-head`（会让 `.panel-head` 出现第 2 个子元素以外的结构，见 S-3）；新建 `.panel-note` 之类的 class。

### S-3: 面板头保持单子元素

- **复用**：`.panel-head`（`styles.css:946-952`，`display:flex; justify-content:space-between`）。
- **新增**：无。
- **DOM 结构**：`index.html` 中「按收件方服务商分布」面板的 `.panel-head` 保持 `<div class="panel-head"><h2>按收件方服务商分布</h2></div>`，**本计划对 index.html 的唯一改动是三处 `?v=` 缓存键**。
- **禁止项**：往该 `.panel-head` 加口径说明 `<span>`。依据 K-panel-head-space-between-third-child：第三个子元素会被 `space-between` 均分，飘到标题与右侧之间。

### S-4: 「最高未送达服务商」卡片

- **复用**：`.metric-card` / `.metric-label` / `.metric-value`（`styles.css:2861-2905`）、`.card-grid`（`:2853-2859`）、`.metric-hint`（`app.js` 内联 span，无 CSS 规则，现状即如此）。
- **新增**：无。
- **DOM 结构**：卡片由 `renderMonitoringCards` 的统一模板生成，本计划只改数组里那一项的 label / value / hint 三个字符串，模板不动：

```javascript
["最高未送达服务商", worstUndeliveredProvider
    ? `${worstUndeliveredProvider.provider} (${worstUndeliveredProvider.undelivered}/${worstUndeliveredProvider.cohort})`
    : "-", "未送达最多的服务商。未送达 = 发送失败 + 被退回（不分硬退软退），按事件时间落窗口；括号内为 未送达人数 / 同窗口队列人数，两者不是同一批人，故不换算成比率"]
```

- **禁止项**：改卡片顺序（仍为数组最后一项）；改 `.metric-card` 规则块；给该卡加颜色或角标。

---

## 现状审计

### Store 1：`mail_record`（MySQL）

- **相关列**：`direction`、`mail_type`、`expert_contact_id`（NOT NULL）、`send_status`、`sent_at`、`created_at`。
- **相关索引**：`idx_mail_record_status_created (direction, send_status, created_at)`（`V32__add_mail_record_status_created_index.sql:2`）——本计划来源 (a) 的三个谓词正好命中该索引全部三列。
- **本计划相关的写路径**（全仓 `grep 'sendStatus = "'` + `grep 'mailType = "INTRODUCTION"'`，2026-09-02 实测）：
  1. `ManualOutreachTxHelper.kt:60-78` — INTRODUCTION 成功：`sendStatus="SENT"`, `sentAt=now`。
  2. `ManualOutreachTxHelper.kt:110-131` — INTRODUCTION 失败：`sendStatus="FAILED"`, `sentAt=null`, `errorSummary=errorSummary?.take(1000)`, `createdAt=now`。**这是来源 (a) 的唯一供数点。**
  - `MailRecord(mailType = "INTRODUCTION")` 全仓仅上述 2 处构造；K-dual-outreach-paths 记载的两条首发链路（`InitialOutreachService.kt:26`、`ManualInitialOutreachService.kt:73`）都注入并汇入 `ManualOutreachTxHelper`，无遗漏。（来源: K-dual-outreach-paths、K-failed-mail-record-has-null-sent-at）
  - 另有两处非 INTRODUCTION 的 FAILED 写入（`ManualReplySendAttemptService.kt:303/320`），**不进本计划分子**（查询限定 `mail_type='INTRODUCTION'`）。
- **本计划相关的读路径**：
  1. `MailMonitoringService.providerDistribution`（`:283-315`）— **本计划改**。
  2. `MailMonitoringService.regionDistribution`（`:317-361`）、`summary`（`:62`起）、`senderAccountHealth`、四个 list 方法 — 不改。
  3. `countFailedOutboundBetween`（`MailRecordRepository.kt:201-209`）— 概览「发送失败」卡片，**按 `created_at` 计数，不去重**。与本计划新数分母不同、单位不同（封 vs 人），两个数不应相等，见 L-2。

### Store 2：`bounce_record`（MySQL）

- **Schema**（`V29__create_bounce_record.sql` + `V43__add_bounce_record_failed_recipient.sql`）：`sender_account_code NOT NULL`、`bounce_message_id NOT NULL UNIQUE`、`original_message_id NULL`、`original_expert_contact_id BIGINT NULL`、`failed_recipient VARCHAR(255) NULL`、`bounce_type NOT NULL`（HARD/SOFT）、`dsn_status NULL`、`bounce_reason NULL`、`received_at NOT NULL`。
- **索引**：`idx_sender_account`、`idx_received_at`、`idx_original_contact (original_expert_contact_id)` — 本计划来源 (b) 与未归因计数均走 `idx_received_at`。
- **写路径**（全仓 `grep 'BounceRecord('`）：
  1. `BounceCollectionService.ingest()`（`:78-106`）— **唯一写入点**。`originalExpertContactId = originalContact?.id`，`originalContact` 由 `resolveOriginalContact(signal)`（`:141-150`）解析：先用 `originalMessageId` 经 `MessageIdNormalizer.candidatesFor` 反查 `mail_record` 拿 `expertContactId`，失败则用 `failedRecipient` 走 `expertEmailAliasService.findContactByEmailOrAlias`。**两条路都失败时该列为 NULL** → 落入 I-4。
  - 两个调用方：`AutoMailReplyService.kt:703-717`（轮询内联，纯文本 `detect`，命中后 `markSeen` 并 `return@forEach`）与 `BounceCollectionService.collectBounces()`（`:33-75`，`fetchUnseenMessages` + MIME `parseBounceDetails`）。内联先执行并标已读，故实际绝大多数退信由内联路径写入。**本计划不改这两条路径**（N-3），也不依赖它们写出的 `bounce_type` 是否准确。
- **读路径**：
  1. `MailMonitoringService.providerDistribution` → `aggregateBouncesByDomain`（`BounceRecordRepository.kt:58-68`）— **本计划删除此调用与该查询**。
  2. `MailMonitoringService.getBounceStats` → `countHardBouncesSince` / `countSoftBouncesSince`（`:19/:29`）— 不改（N-4）。
  3. `BounceRateMonitorService.checkAndPause`（`:17-40`）→ `countHardBouncesSince` — 不改（N-4）。
  4. `BounceController.listBounces`（`:24-51`）→ `findPaged` / `countPaged` — 不改（N-4）。
  5. `OperatorStatusReconcileService.kt:74`、`ExpertReachabilitySyncService` → `findAllBySenderAccountCodeOrderByReceivedAtDesc` — 不改。
- **`aggregateBouncesByDomain` 与 `DomainBounceCount` 的全部引用**（2026-09-02 全仓 grep）：生产侧仅 `MailMonitoringService.kt:296`；定义在 `BounceRecordRepository.kt:58-68` 与 `:71`；测试侧 `MailMonitoringServiceTest.kt:10/80-83/215/234-237`。**无其他调用方，可安全删除。**

### Store 3：`expert_contact`（MySQL）

- 本计划**只读** `id` 与 `expert_email`，用于分桶（I-3）。不写。
- 相关索引：无需——JOIN 走主键。

### Interaction points

- **IP-1**：新查询（读）↔ `ManualOutreachTxHelper` 的 FAILED 写入（写）。FAILED 行 `sent_at` 为 NULL，必须用 `created_at` 过滤（I-2）。
- **IP-2**：新查询（读）↔ `BounceCollectionService.ingest` 的 `originalExpertContactId` 写入（写）。该列为 NULL 的行不进桶，由 I-4 的独立计数承接。
- **IP-3**：`MailMonitoringService.providerDistribution` 的返回形状（后端）↔ `app.js` `loadMonitoring` 的解构与 `.catch(() => [])` 兜底（前端）↔ `renderMonitoringCards` 对 `state.monitoring.providerDistribution` 的 `.filter/.map`。→ I-7。
- **IP-4**：`ProviderStatRow` 删字段（后端）↔ `app.js` 两处 `hardBounceCount` / `softBounceCount` 消费点（前端）。→ I-6，且失败是**无声的**。
- **IP-5**：`index.html` 缓存键 ↔ `batchSendTaskConsoleVisualFix.test.js:49-51`。→ I-8。

### 前端样式盘点

- **可复用 class**：
  - `table` — `styles.css:977-981`（`width:100%; border-collapse:collapse; min-width:720px`）
  - `th, td` — `styles.css:983-987`（`padding:8px 14px; text-align:left; border-bottom:1px solid var(--line)`）
  - `.text-muted` — `styles.css:2461-2464`（`color: var(--text-muted); font-size:12px`）
  - `.badge` / `.badge.warn` — `styles.css:1038-1050` / `1058-1062`（「样本不足」用，本计划不改）
  - `.metric-card` / `.metric-label` / `.metric-value` — `styles.css:2861-2905`
  - `.panel` — `:932-939`；`.panel-head` — `:946-952`；`.table-wrap`、`.card-grid` — `:2853-2859`
- **设计基准 token 实值**（`styles.css:1-80`）：`--text-main:#1e293b`；`--text-muted:#94a3b8`；`--text-secondary:#475569`；`--line:rgba(15,23,42,0.055)`；`--primary:#1e40af`；`--warning:#d97706`。
- **DOM 结构约定**：分布表为 `<section class="panel"><div class="panel-head"><h2>…</h2></div><div class="table-wrap"><table id="…"><thead></thead><tbody></tbody></table></div></section>`（`index.html:206-215`），`thead`/`tbody` 全由 `app.js` 的 render 函数 `innerHTML` 填充；`index.html` 内不含任何单元格。
- **改动前基线** —— `app.js:11261-11288` `renderMonitoringProviderDistribution` 全文（逐字）：

```javascript
function renderMonitoringProviderDistribution() {
    const table = $("#monitoringProviderDistributionTable");
    if (!table) return;
    const rows = state.monitoring.providerDistribution || [];
    const maxSent = Math.max(0, ...rows.map((row) => row.sentCount || 0));
    table.querySelector("thead").innerHTML = `
        <tr>
            <th>服务商</th><th>分布</th><th>队列(人)</th><th>已回复(人)</th><th>窗口内回复率</th><th>7日成熟回复率</th><th>硬退率</th><th>软退</th>
        </tr>
    `;
    table.querySelector("tbody").innerHTML = rows.map((row) => {
        const cohort = row.sentCount || 0;
        const hardRate = cohort > 0 ? (row.hardBounceCount || 0) / cohort : 0;
        const matureRate = (row.matureCohortCount || 0) === 0
            ? '<span class="text-muted">—</span>'
            : formatPercent(row.matureRepliedCount / row.matureCohortCount);
        return `<tr>
            <td><strong>${escapeHtml(row.provider)}</strong></td>
            <td>${monitoringDistributionBar(cohort, maxSent)}</td>
            <td>${escapeHtml(cohort)}</td>
            <td>${escapeHtml(row.repliedCount ?? 0)}</td>
            <td>${monitoringReplyRateCell(row.repliedCount, cohort)}</td>
            <td>${matureRate}</td>
            <td>${escapeHtml(formatPercent(hardRate))}</td>
            <td>${escapeHtml(row.softBounceCount ?? 0)}</td>
        </tr>`;
    }).join("") || `<tr><td colspan="8" class="text-muted" style="text-align:center;">暂无数据</td></tr>`;
}
```

- **改动前基线** —— `app.js:11201-11207` 与 `:11224-11226`（逐字）：

```javascript
    const worstBounceProvider = providers
        .filter((row) => (row.sentCount || 0) > 0)
        .map((row) => ({
            provider: row.provider,
            rate: (row.hardBounceCount || 0) / row.sentCount
        }))
        .sort((a, b) => b.rate - a.rate)[0];
```

```javascript
        ["最高退信服务商", worstBounceProvider
            ? `${worstBounceProvider.provider} (${formatPercent(worstBounceProvider.rate)})`
            : "-", "按硬退率（硬退数/发送量）"]
```

---

## 实现方案

### 阶段 A：后端

#### A-1. `MailRecordRepository.kt`：新增 1 个投影 + 1 条查询（遵守 I-1、I-2、I-3）

在 `data class DomainCohortStat`（`:29-35`）之后新增：

```kotlin
data class DomainUndeliveredCount(
    val domain: String?,
    val undeliveredCount: Long
)
```

在 `aggregateIntroCohortByDomain`（`:415-419`）之后新增查询。**注释必须写明 I-2 的依据**（FAILED 行 `sent_at` 恒为 NULL，故用 `created_at`）：

```sql
SELECT SUBSTRING_INDEX(ec.expert_email, '@', -1) AS domain,
       COUNT(DISTINCT u.expert_contact_id) AS undelivered_count
  FROM (
        SELECT expert_contact_id
          FROM mail_record
         WHERE direction = 'OUTBOUND' AND mail_type = 'INTRODUCTION'
           AND send_status = 'FAILED'
           AND created_at >= :from AND created_at < :to
        UNION
        SELECT original_expert_contact_id AS expert_contact_id
          FROM bounce_record
         WHERE original_expert_contact_id IS NOT NULL
           AND received_at >= :from AND received_at < :to
       ) u
  JOIN expert_contact ec ON ec.id = u.expert_contact_id
 GROUP BY SUBSTRING_INDEX(ec.expert_email, '@', -1)
```

方法签名：`fun aggregateUndeliveredByDomain(from: LocalDateTime, to: LocalDateTime): List<DomainUndeliveredCount>`

#### A-2. `BounceRecordRepository.kt`：新增 1 条计数、删除 1 条查询与 1 个投影（遵守 I-4）

新增（**两类未归因缺一不可**，见 I-4）：

```sql
SELECT COUNT(*) FROM bounce_record br
 WHERE br.received_at >= :from AND br.received_at < :to
   AND (br.original_expert_contact_id IS NULL
        OR NOT EXISTS (SELECT 1 FROM expert_contact ec WHERE ec.id = br.original_expert_contact_id))
```

`fun countUnattributedBouncesBetween(from: LocalDateTime, to: LocalDateTime): Long`

> 方法注释须写明：`bounce_record` 无任何外键（`V29__create_bounce_record.sql`），
> `original_expert_contact_id` 可能指向已不存在的 `expert_contact`；主查询的 `JOIN expert_contact`
> 会丢弃这类孤儿行，故本计数必须把它们接住，否则 UI 上凭空消失。
> `mail_record.expert_contact_id` 有 FK（`V1__create_business_tables.sql:16`），故发送失败那一支不存在孤儿问题。

删除：`aggregateBouncesByDomain`（`:58-68`）与 `data class DomainBounceCount`（`:71-75`）。
**保留不动**：`existsByBounceMessageId`、`countHardBouncesSince`、`countSoftBouncesSince`、`findAllBySenderAccountCodeOrderByReceivedAtDesc`、`findPaged`、`countPaged`（N-4）。

#### A-3. `MailMonitoringResponses.kt`：改 DTO（遵守 I-6）

`ProviderStatRow`（`:123-133`）删除 `hardBounceCount`、`softBounceCount`，新增 `undeliveredCount: Long`（放在 `matureReplyRate` 之后、行尾）。新增包装响应：

```kotlin
data class ProviderDistributionResponse(
    val rows: List<ProviderStatRow>,
    // I-4：未能关联到专家的退信条数，两类并集——(a) original_expert_contact_id IS NULL；
    //      (b) 该 id 在 expert_contact 中不存在（孤儿引用，bounce_record 无外键）。均不计入任何 rows 元素。
    val unattributedBounceCount: Long
)
```

#### A-4. `MailMonitoringService.kt`：重写 `providerDistribution`（遵守 I-1 ~ I-4）

- 返回类型由 `List<ProviderStatRow>` 改为 `ProviderDistributionResponse`。
- `MutableProviderStats`（`:371-377`）的 `hardBounce` / `softBounce` 字段改为单个 `undelivered`。
- 队列四个计数仍由 `aggregateIntroCohortByDomain(start, end, matureBefore)` 供数，**逐字不动**（N-1）。
- 把 `bounceRecordRepository.aggregateBouncesByDomain(start, end)` 那一段（`:296-301`）替换为 `mailRecordRepository.aggregateUndeliveredByDomain(start, end)`，折叠方式沿用 `resolveProviderFromDomain(row.domain)`（`:363-367`，逐字不动）。
- `unattributedBounceCount = bounceRecordRepository.countUnattributedBouncesBetween(start, end)`。
- `regionDistribution`、`summary`、`senderAccountHealth`、`getBounceStats` **一行不改**（N-4、N-5）。

#### A-5. `MailMonitoringController.kt`：改返回类型（遵守 I-7）

`/provider-distribution`（`:107-112`）的返回由 `List<ProviderStatRow>` 改为 `ProviderDistributionResponse`，参数与注解逐字不动。`/region-distribution` 及其余端点不改。

### 阶段 B：前端（遵守 I-5 ~ I-8、S-1 ~ S-4）

#### B-1. `index.html`

三处 `?v=20260902-monitoring-window` 改为 `?v=20260902-undelivered`（`:11`、`:2110`、`:2111`）。**除此之外不改一个字符**（S-3）。

#### B-2. `app.js`

1. `state.monitoring`（`:39` 起）新增 `unattributedBounceCount: 0`。
2. `loadMonitoring`：`/provider-distribution` 的 `.catch(() => [])` 改为 `.catch(() => ({ rows: [], unattributedBounceCount: 0 }))`；赋值改为
   `state.monitoring.providerDistribution = providerDistribution?.rows || [];`
   `state.monitoring.unattributedBounceCount = providerDistribution?.unattributedBounceCount || 0;`
   `/region-distribution` 的 `.catch(() => [])` 与赋值**不动**（N-5）。（I-7）
3. `renderMonitoringProviderDistribution`：表头末两列 `<th>硬退率</th><th>软退</th>` 换成 `<th>未送达(人)</th>`；删除 `const hardRate = ...` 一行；末两个 `<td>` 换成一个，内容恒为 `${escapeHtml(row.undeliveredCount ?? 0)}`，**不得带任何以 `cohort` 为条件的分支**（S-1、I-9）；空状态行 `colspan` 由 8 改 7（I-5）；数据行之后按 S-2 追加表尾行（`unattributedBounceCount > 0` 时才追加）。**前 6 个 `<td>` 与 `maxSent` / `matureRate` / `monitoringReplyRateCell` 的写法逐字不动**（N-1）。
4. `renderMonitoringCards`：`worstBounceProvider` 改名为 `worstUndeliveredProvider`；候选集的过滤条件由 `.filter((row) => (row.sentCount || 0) > 0)` 改为 **`.filter((row) => (row.undeliveredCount || 0) > 0)`**；`.map` 的产出改为 `{ provider, undelivered: row.undeliveredCount || 0, cohort: row.sentCount || 0 }`；排序改为 **`(a, b) => b.undelivered - a.undelivered`（纯计数降序，不做除法）**。数组最后一项按 S-4 改 label / value / hint。**其余 14 项逐字不动**（N-6）。（I-6、I-9）

### 阶段 C：测试

#### C-1. `MailMonitoringServiceTest.kt`

- 删除 `import ...DomainBounceCount`（`:10`）与全部 `aggregateBouncesByDomain` mock（`:80-83`、`:215`、`:234-237`）及其断言（`:95-96`、`:102`、`:243-246`）。
- 原用例 `providerDistribution separates hard and soft bounces by domain bucket`（`:230` 起）整体替换为 `providerDistribution folds undelivered counts by domain bucket`：mock `aggregateUndeliveredByDomain` 返回 `DomainUndeliveredCount("gmail.com", 3)` / `DomainUndeliveredCount("yahoo.com", 1)` / `DomainUndeliveredCount(null, 2)`，断言 gmail=3、yahoo=1、other=2（I-3 的 null → other 折叠仍成立）。
- 新增 `providerDistribution reports unattributed bounce count separately`：mock `countUnattributedBouncesBetween` 返回 5，断言 `response.unattributedBounceCount == 5L` **且** `response.rows.sumOf { it.undeliveredCount }` 不包含这 5（I-4）。
- 新增 `providerDistribution keeps undelivered count when cohort is zero`（I-9）：`aggregateIntroCohortByDomain` 返回空、`aggregateUndeliveredByDomain` 返回 `DomainUndeliveredCount("gmail.com", 4)` → 断言 gmail 行 `sentCount == 0L` 且 `undeliveredCount == 4L`（**不是** 0、也不被丢弃）。
- 其余用例（队列折叠、地区折叠、父子加总、成熟度分母）中凡调用 `service.providerDistribution(...)` 的地方，取值改为 `.rows`。

#### C-2. `MailRecordRepositoryMonitoringIT.kt`（真 MySQL，验证 SQL 语义）

**C-2a（必须先做，否则后面三个用例互相污染）**：现有 `@BeforeEach fun cleanMailRecords()` 只删两张表：

```kotlin
    @BeforeEach
    fun cleanMailRecords() {
        jdbcTemplate.execute("DELETE FROM inbound_mail_processing")
        jdbcTemplate.execute("DELETE FROM mail_record")
    }
```

必须在最前面补一行 `jdbcTemplate.execute("DELETE FROM bounce_record")`。

理由（2026-09-02 实测，非推断）：`bounce_record` 不在清理列表内，而 `seedBaseContact()`（`:682-685`）
每次都 `DELETE FROM expert_contact` 再以**固定 id = 1** 重建。于是上一个用例残留的 `bounce_record`
行（`original_expert_contact_id = 1`）会**被重新关联到下一个用例新建的专家 1** 上，
使 `undeliveredCount` 无声地多算。`bounce_record` 无外键，`DELETE FROM expert_contact` 不会报错，
因此这条污染完全静默。**本行是 C-2b/c/d 三个用例结果可信的前提。**

对既有 7 个用例无影响：它们均不读写 `bounce_record`（全文 grep 该表名零命中）。

**C-2b ~ C-2e**，沿用文件现有的 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` + `JdbcTemplate` 造数套路：

- `undelivered dedupes a contact that both failed and bounced`（I-1）：同一专家造 1 条 FAILED INTRODUCTION + 2 条 bounce_record → 该域名 `undeliveredCount == 1`。
- `undelivered counts failed sends by created_at because sent_at is null`（I-2）：造一条 `send_status='FAILED', sent_at=NULL, created_at=<窗口内>` → 被计入；另造一条 `created_at=<窗口外>` → 不计入。
- `undelivered excludes bounces with null contact and counts them as unattributed`（I-3 + I-4a）：造 1 条 `original_expert_contact_id IS NULL` 且 **`failed_recipient='x@gmail.com'`** 的 bounce → **不出现在 gmail 行也不出现在任何 domain 行**（证明未按 `failed_recipient` 分桶），且 `countUnattributedBouncesBetween` 返回 1。
- `undelivered counts orphaned contact reference as unattributed`（I-4b）：造 1 条 `original_expert_contact_id = 999999`（`expert_contact` 中不存在该 id）的 bounce → 主查询任何 domain 行都不含它，且 `countUnattributedBouncesBetween` 返回 1。**该用例证明 `NOT EXISTS` 分支生效；若只判 `IS NULL` 会返回 0 而使本例红。**

#### C-3. `batchSendTaskConsoleVisualFix.test.js`

`:49-51` 三处字符串改为 `20260902-undelivered`（I-8）。

---

## 变更文件清单

| # | 文件 | 改动性质 |
|---|------|----------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 新增 `DomainUndeliveredCount` + `aggregateUndeliveredByDomain` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt` | 新增 `countUnattributedBouncesBetween`；删除 `aggregateBouncesByDomain` 与 `DomainBounceCount` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/controller/MailMonitoringResponses.kt` | `ProviderStatRow` 删 2 字段增 1 字段；新增 `ProviderDistributionResponse` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt` | 重写 `providerDistribution`；`MutableProviderStats` 改字段 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/controller/MailMonitoringController.kt` | `/provider-distribution` 返回类型 |
| 6 | `src/main/resources/static/index.html` | 仅 3 处缓存键 |
| 7 | `src/main/resources/static/app.js` | state / loadMonitoring / 两个 render 函数 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt` | 重写退信相关用例 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt` | `cleanMailRecords()` 补 `DELETE FROM bounce_record`；新增 4 个 SQL 语义用例 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键字符串同步 |
| 11 | `src/test/js/checkRepliesRelocation.test.js` | 缓存键字符串同步（`const CACHE_KEY`） |
| 12 | `src/test/js/manualReplySubjectPrefill.test.js` | 缓存键字符串同步（`const CACHE_KEY`） |
| 13 | `src/test/js/overlayAndDialogContrast.test.js` | 缓存键字符串同步（`const CACHE_KEY`） |
| 14 | `src/test/js/monitoringDateDefault.test.js` | 受 `renderMonitoringCards` 改动波及的断言同步 |
| 15 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 行号守卫：`MailRecordRepository` 新增行使 `NoiseSite` 的 `:583 → :612`、`:640 → :669` |
| 16 | `src/test/js/providerUndeliveredColumn.test.js` | **新建**：I-4 / I-5 / I-7 / I-9 与 S-2 的前端行为测试（21 例） |

> **文件数 16，远超 create-p 的 10 文件硬上限。这是本计划最严重的结构性问题，如实记录如下。**
>
> 计划最初列 10 项，属**审计不完整**，不是执行越界。漏掉的两类都是机械连带、无法通过拆分计划回避：
>
> 1. **缓存键扇出（第 11-13 项）**：仓库有 **4 个** JS 测试固化缓存键，其中 3 个用
>    `const CACHE_KEY = "20260902-undelivered";` 的写法（`checkRepliesRelocation:11`、
>    `manualReplySubjectPrefill:13`、`overlayAndDialogContrast:15`），只有
>    `batchSendTaskConsoleVisualFix:49-51` 用 `assert.ok(html.includes('...?v=...'))`。
>    原计划只审到后一种拼写，是因为 grep 用了 `styles.css?v=` 这一种模式。
>    **正确的审计命令**（bump 前必跑，两种拼写都覆盖）：
>    ```bash
>    KEY=$(grep -o 'v=[0-9a-z-]*' src/main/resources/static/index.html | sort -u | head -1 | cut -d= -f2)
>    grep -rln "$KEY" src/test/
>    ```
> 2. **行号守卫（第 15 项）**：`OperatorStatusWriteSeamGuardTest` 用 `NoiseSite(文件, 行号, 片段)`
>    钉死 `MailRecordRepository.kt` 的两处行号。本计划往该文件加了投影与查询，把 `:583` 顶到 `:612`、
>    `:640` 顶到 `:669`。**任何往 `MailRecordRepository.kt` 增删行的计划都会撞上它**，这与改动内容无关。
>
> 两者都必须与主改动同一次提交落地，否则构建立即红。拆成独立计划只会制造"实现已合、守卫未同步"的破窗。
> 故本计划以 16 文件执行完毕，并把这两条审计遗漏写进知识库（见 Phase 6），供后续计划在 Phase 1b 阶段直接继承。

**子系统数：2**（后端 monitoring/mail 模块 / 前端静态资源）。
**新增 CSS 行数：0。新增 DDL / 迁移：0。新增数据列：0。写路径：0（本计划纯读 + 前端）。**

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11）跑 Maven，裸 `mvn` 会构建失败。前端 JS 用例由 `exec-maven-plugin` 绑定在 `test` 阶段（`pom.xml:186-232`），也可用 `node --test` 单跑，无需 JAVA_HOME 前缀。

```bash
# 1) 全量回归门禁（Kotlin 单测 + node --test + node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 2) 本计划相关 Kotlin 单测（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailMonitoringServiceTest

# 3) 本计划新增的 SQL 语义集成测试（需要 Docker，默认跳过，必须显式开启）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordRepositoryMonitoringIT -DmigrationIt=true

# 4) 本计划相关前端用例（单跑）
node --test src/test/js/providerUndeliveredColumn.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/monitoringDateDefault.test.js

# 5) 前端全量用例
node --test src/test/js/*.test.js

# 6) app.js 语法检查
node --check src/main/resources/static/app.js

# 7) 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 8) 空白/换行卫生
git diff --check
```

**通过判据**

- 命令 2 / 3：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`。
- 命令 1（`mvn test`）与 **命令 7（`mvn clean package`）**：两者都经过 `test` 阶段并执行全量 `node --test`
  （`exec-maven-plugin` 的 `node-test` execution 绑在 `test` 阶段，`pom.xml:186-203`，无 `failOnError` / `successCodes`，
  非零退出即失败），因此**同样受下方既有红用例影响，适用同一条豁免**：判据为
  「Kotlin 侧 `Tests run: N, Failures: 0, Errors: 0`，且 node 侧除 `expertTagBatchFix.test.js` 外 `# fail` 为 0」。
  若需要一个能真正退出码 0 的构建，用 `-DskipNodeTests=true` 跳过 Node 测试，
  **但必须同时单独执行命令 4 与命令 5 作为前端门禁**——该参数会把全部 JS 测试一起跳过。
- 命令 3：若输出 `Tests run: 0` 或用例被 skip，说明 `-DmigrationIt=true` 未生效或 Docker 不可用 —— 视为**未验证**，不得据此判定通过。
- 命令 4：退出码 0，输出含 `# fail 0`（`providerUndeliveredColumn.test.js` 应为 `# tests 21 / # pass 21`）。
- 命令 5（前端全量）：**当前存在一个与本计划无关的既有红用例** —— `src/test/js/expertTagBatchFix.test.js` 的
  `log execution identity (I-2)` 套件报 `test did not finish before its parent and was cancelled`
  （`:568-569` 的 async `it` 未结束）。2026-09-02 实测：连跑 5 次全部 exit=1，确定性失败；
  该文件自 `bb07586 feat(fast-p): implement a2` 起未被改动，把本计划新增的测试文件移开后仍是
  `# pass 28 / # fail 1`。**故命令 5 与命令 1（含 node-test 阶段）的通过判据暂为
  「除 `expertTagBatchFix.test.js` 的该套件外，`# fail` 计数为 0」**；该红用例需另案修复，
  不得在本计划内顺手改（属无关改动）。
- 命令 6 / 8：退出码 0，无输出。

**来源**：命令 1/2/3/7 取自 `CLAUDE.md`「Commands」章节（`CLAUDE.md:11/14/17/24`）与项目元信息 `test_command` / `build_command`（`CLAUDE.md:144/146`）；命令 4/5/6 取自 K-js-tests-run-via-exec-plugin（`pom.xml:186-232` 实测）。

---

## 验收标准

### 不变量

- **I-1**：`grep -n "UNION" src/main/kotlin/.../MailRecordRepository.kt` 命中新查询且**不是** `UNION ALL`；同文件 `grep -n "COUNT(DISTINCT u.expert_contact_id)"` 命中 1 处。命令 3 的 `undelivered dedupes a contact that both failed and bounced` 断言 `undeliveredCount == 1`。
- **I-2**：新查询中 FAILED 分支 `grep` 必须含 `created_at >= :from`，且**不得**出现 `sent_at` 用于该分支；bounce 分支必须用 `received_at`。命令 3 的 `undelivered counts failed sends by created_at because sent_at is null` 通过。
- **I-3**：`grep -n "failed_recipient" src/main/kotlin/` 在 `MailRecordRepository.kt` 与 `MailMonitoringService.kt` 中**零命中**；新查询含 `JOIN expert_contact ec ON ec.id = u.expert_contact_id` 与 `SUBSTRING_INDEX(ec.expert_email, '@', -1)`。
- **I-4**：`grep -rn "aggregateBouncesByDomain\|DomainBounceCount" src/` 全仓**零命中**（含测试）。`grep -n "NOT EXISTS" src/main/kotlin/.../BounceRecordRepository.kt` 命中 1 处（孤儿分支）。命令 2 的 `providerDistribution reports unattributed bounce count separately` 断言 `unattributedBounceCount == 5L` 且未混入 `rows`。命令 3 的 `undelivered excludes bounces with null contact and counts them as unattributed` 与 `undelivered counts orphaned contact reference as unattributed` 两条均通过。`grep -n "收件人与关联专家均缺失" src/main/resources/static/app.js` **零命中**；`grep -n "未能关联到专家（关联为空或专家已不存在）"` 命中 1 处。
- **I-9**：`renderMonitoringProviderDistribution` 函数体内，未送达 `<td>` 所在行 `grep` **不得**出现 `cohort`；`grep -n "sentCount || 0) > 0" src/main/resources/static/app.js` 在 `renderMonitoringCards` 的 `worstUndeliveredProvider` 链上**零命中**（应为 `undeliveredCount`）；该链上无 `/` 除法运算符。命令 2 新增用例 `providerDistribution keeps undelivered count when cohort is zero` 断言：喂 `aggregateIntroCohortByDomain` 空结果 + `aggregateUndeliveredByDomain` 返回 `DomainUndeliveredCount("gmail.com", 4)` 时，`rows` 中 gmail 行 `sentCount == 0L` 且 `undeliveredCount == 4L`。
- **I-5**：`grep -n 'colspan="8"' src/main/resources/static/app.js` 在 `renderMonitoringProviderDistribution` 函数体内**零命中**；`grep -c 'colspan="7"'` 在该函数体内为 2（空状态行 + 表尾行）。
- **I-6**：`grep -n "hardBounceCount\|softBounceCount" src/main/resources/static/app.js` **零命中**；
  `MailMonitoringResponses.kt` 中 `data class ProviderStatRow` 的字段列表内**零命中**；
  `MailMonitoringService.providerDistribution` 函数体与 `MutableProviderStats` 内**零命中**。
  **反向断言（防止误删）**：`grep -n "hardBounceCount" src/main/kotlin/.../MailMonitoringResponses.kt` 仍在
  `BounceStatsResponse` 中命中 1 处（`:116`），`MailMonitoringService.getBounceStats` 内仍命中 1 处（`:277`）——
  这两处**必须存在**，删掉即违反 N-4。命令 2 全部通过。
- **I-7**：`grep -n 'unattributedBounceCount: 0' src/main/resources/static/app.js` 命中 ≥ 2 处（state 初值 + catch 兜底）；`grep -n "providerDistribution?.rows" src/main/resources/static/app.js` 命中 1 处；`renderMonitoringCards` 中对 `state.monitoring.providerDistribution` 的 `.filter` 调用保持不变（该行在 diff 中未被修改）。
- **I-8**：`grep -c "v=20260902-undelivered" src/main/resources/static/index.html` 为 3；命令 4 的 `batchSendTaskConsoleVisualFix.test.js` 通过。

### 样式契约

- **S-1**：`git diff src/main/resources/static/app.js` 中，新表头行逐字为
  `<th>服务商</th><th>分布</th><th>队列(人)</th><th>已回复(人)</th><th>窗口内回复率</th><th>7日成熟回复率</th><th>未送达(人)</th>`；
  前 6 个 `<td>` 的表达式与「改动前基线」逐字一致（diff 中这 6 行不出现在变更侧）。
- **S-2**：表尾行的 HTML 与本计划 S-2 代码块逐字一致（`colspan="7" class="text-muted" style="text-align:center;"`）；`git diff src/main/resources/static/styles.css` **为空**（零改动）。
- **S-3**：`git diff src/main/resources/static/index.html` 只含 3 行缓存键改动，无其他行。
- **S-4**：`grep -n "最高未送达服务商" src/main/resources/static/app.js` 命中 1 处，`grep -n "最高退信服务商"` 零命中；该卡片仍是 `cards` 数组最后一项（diff 中未改变位置）。
- **全局**：`git diff src/main/resources/static/app.js | grep '^+.*style="'` 的新增行中，只允许出现 S-2 那一处 `style="text-align:center;"`（与既有空状态行同构），不得有其他 inline style。

### 回归

- 执行「验证命令」节的命令 1、5、6、7、8，通过判据见该节。
- `git diff` 中 `MailMonitoringService` 的 `regionDistribution`、`summary`、`senderAccountHealth`、`getBounceStats`、`listIntroductions`、`listOutboundReplies`、`listInbound`、`listPromotions` 八个方法体无改动（N-4、N-5）。
- `git diff` 中 `BounceDetector.kt`、`BounceCollectionService.kt`、`ImapMailReceiveService.kt`、`BounceRateMonitorService.kt`、`BounceController.kt`、`ProviderResolver.kt` 六个文件**未出现**（N-2、N-3、N-4）。
- `git diff src/main/resources/static/app.js` 中 `renderMonitoringRegionDistribution` 函数体无改动（N-5）。

---

## 人工验收清单

### A-1: 未送达列出现且列数正确（覆盖 O-1、S-1、I-5）

- 前置条件：近 30 天内有 INTRODUCTION 发信，且 `bounce_record` 有窗口内记录（你截图里的退信即可）。
- 操作步骤：
  1. 登录后台 → 「邮件监控」→ 顶部选「近 30 天」。
  2. 看「按收件方服务商分布」表头，从左往右数列名。
  3. 看 `tencent` 与 `netease` 两行（队列通常为 0）的最后一列。
- 预期结果：
  - 步骤 2：表头恰好 **7 列**，逐字为 `服务商 | 分布 | 队列(人) | 已回复(人) | 窗口内回复率 | 7日成熟回复率 | 未送达(人)`。**不存在**「硬退率」和「软退」两列。
  - 步骤 3：这两行的「未送达(人)」显示 **`0`**（黑色数字），**不是** `-`、不是空白。同一行的「窗口内回复率」仍显示 `-`、「7日成熟回复率」仍显示 `—`（这两列的既有行为不变）。
- 覆盖：O-1、S-1、I-5、I-9

### A-2: 同一专家多次退信只算一个（覆盖 I-1）

- 前置条件：用 SQL 找一个 `bounce_record` 里 `original_expert_contact_id` 相同且出现 ≥2 次、`received_at` 都在近 30 天内的专家；若没有，手工插入第二条（`INSERT INTO bounce_record (sender_account_code, bounce_message_id, original_expert_contact_id, bounce_type, received_at, created_at) VALUES ('LuKai', CONCAT('manual-test-', UUID()), <该专家 id>, 'SOFT', NOW(), NOW());`）。
- 操作步骤：
  1. 记下插入前该专家所属服务商桶（按其 `expert_contact.expert_email` 的域名）的「未送达(人)」值 X。
  2. 执行上面的 INSERT。
  3. 回到「邮件监控」，点「手动查询」刷新。
- 预期结果：该桶的「未送达(人)」**仍然是 X，没有变成 X+1**。若变成 X+1，说明用了 `UNION ALL` 或 `COUNT(*)`。
- 覆盖：I-1

### A-3: 发送失败也计入未送达（覆盖 I-2、IP-1）

- 前置条件：找一条 `mail_record` 中 `mail_type='INTRODUCTION' AND send_status='FAILED'` 且 `created_at` 在近 30 天内的记录；若没有，手工插入（`expert_contact_id` 取一个不在 `bounce_record` 里的专家，`sent_at` 必须留 NULL）。
- 操作步骤：
  1. 记下该专家所属服务商桶的「未送达(人)」值 Y。
  2. 插入该 FAILED 记录（`sent_at = NULL, created_at = NOW()`）。
  3. 刷新监控页。
- 预期结果：该桶「未送达(人)」变为 **Y+1**。若没变，说明查询错用了 `sent_at`（FAILED 行该列恒为 NULL，条件永不成立）。
- 覆盖：I-2、IP-1

### A-11: 队列为 0 但有未送达时，数字必须照常显示且卡片能选中（覆盖 I-9）

- 前置条件：挑一个当前「队列(人) = 0」的服务商桶（如 `tencent`）。找一位邮箱域名属于该桶（如 `@qq.com`）、且**首发时间在当前窗口之前**的专家；若专家库里没有，先造一个 `expert_contact`（`expert_email` 用 `test-tencent@qq.com`）。
- 操作步骤：
  1. 选「今日」窗口，确认 `tencent` 行「队列(人)」为 `0`、「未送达(人)」为 `0`。
  2. 为该专家插入一条今天的退信：
     `INSERT INTO bounce_record (sender_account_code, bounce_message_id, original_expert_contact_id, bounce_type, received_at, created_at) VALUES ('LuKai', CONCAT('i9-test-', UUID()), <该专家 id>, 'HARD', NOW(), NOW());`
  3. 点「手动查询」刷新。
  4. 看概览区最后一张卡片。
- 预期结果：
  - 步骤 3：`tencent` 行「队列(人)」仍为 `0`，「未送达(人)」变为 **`1`**。**不得**显示成 `-`，更不得整行消失。
  - 步骤 4：若当天没有别的服务商未送达数 ≥ 1，「最高未送达服务商」显示 **`tencent (1/0)`**。若它显示 `-` 或显示了另一个未送达数更少的服务商，说明卡片仍在按 `sentCount > 0` 过滤（I-9 未满足）。
- 覆盖：I-9

### A-4: 关联不到专家的退信进表尾，不进任何桶（覆盖 O-2、I-4、S-2、IP-2）

- 前置条件：`bounce_record` 中存在 `original_expert_contact_id IS NULL` 且 `received_at` 在窗口内的记录（你 2026-09-02 截图末行「关联专家」为 `-` 的那类）；若没有，手工插入一条，并**故意把 `failed_recipient` 填成 `someone@gmail.com`**。
- 操作步骤：
  1. 记下 `gmail` 行的「未送达(人)」值 Z 和表尾提示里的数字 N。
  2. 插入上述记录。
  3. 刷新监控页。
- 预期结果：
  - `gmail` 行「未送达(人)」**仍然是 Z**（没有因为 `failed_recipient` 是 gmail 就被塞进 gmail 桶）。
  - 表格最下方多出/更新一行居中灰字，逐字为：`另有 N+1 封退信未能关联到专家（关联为空或专家已不存在），未计入上表任何一行。`
    —— 注意文案说的是**关联专家**缺失，不是「收件人缺失」；本例插入的记录 `failed_recipient` 恰恰是有值的，文案若写成「收件人与关联专家均缺失」即为失实。
  - 该行与数据行同宽，横跨整表（不是只占一格）。
- 覆盖：O-2、I-4、S-2、IP-2

### A-12: 孤儿专家引用的退信也计入表尾（覆盖 I-4b）

- 前置条件：无（直接造数）。
- 操作步骤：
  1. 记下当前窗口表尾提示里的数字 N（若无该行则 N = 0）。
  2. 插入一条指向不存在专家的退信：
     `INSERT INTO bounce_record (sender_account_code, bounce_message_id, original_expert_contact_id, failed_recipient, bounce_type, received_at, created_at) VALUES ('LuKai', CONCAT('orphan-test-', UUID()), 999999, 'orphan@gmail.com', 'HARD', NOW(), NOW());`
     （先用 `SELECT COUNT(*) FROM expert_contact WHERE id = 999999;` 确认返回 0）
  3. 刷新监控页。
- 预期结果：表尾数字变为 **N+1**；`gmail` 行的「未送达(人)」**不变**。若表尾仍是 N，说明 `countUnattributedBouncesBetween` 只判了 `IS NULL`，漏了 `NOT EXISTS` 那一支——这条退信就在 UI 上凭空消失了。
- 覆盖：I-4b、IP-2

### A-5: 表尾行在无未归因退信时不出现（覆盖 S-2）

- 前置条件：把顶部窗口切到一个**两类未归因退信都没有**的区间。两类缺一不可（I-4）：
  (a) `original_expert_contact_id IS NULL`；(b) 该 id 在 `expert_contact` 中不存在（孤儿引用）。
  可用这条 SQL 确认该区间返回 0，再开始验收：
  ```sql
  SELECT COUNT(*) FROM bounce_record br
   WHERE br.received_at >= '<窗口起>' AND br.received_at < '<窗口止>'
     AND (br.original_expert_contact_id IS NULL
          OR NOT EXISTS (SELECT 1 FROM expert_contact ec WHERE ec.id = br.original_expert_contact_id));
  ```
  **只排除 (a) 而不排除 (b) 会导致正确实现被误判为失败**（孤儿引用同样会让表尾出现）。
- 操作步骤：切到「今日」，看服务商表最下方。
- 预期结果：**没有**「另有 N 封退信…」这一行（不是显示「另有 0 封」）。
- 覆盖：S-2

### A-6: 概览卡片改名与取值（覆盖 O-3、S-4、I-6）

- 前置条件：同 A-1。
- 操作步骤：
  1. 看概览区最后一张卡片的标题。
  2. 看它的值。
  3. 鼠标悬停在卡片上。
  4. 对照服务商表，找出「未送达(人)」**数值最大**的那一行（不要算比率）。若有并列，取 `PROVIDER_ORDER` 中靠前者（顺序为 `gmail, outlook, yahoo, edu, tencent, netease, other`）。
- 预期结果：
  - 步骤 1：标题为「最高未送达服务商」，**不是**「最高退信服务商」。
  - 步骤 2：值形如 `yahoo (7/120)` —— 服务商名 + 空格 + 括号内「未送达人数/队列人数」，**不是**百分比。
  - 步骤 3：提示文案为「未送达 = 发送失败 + 被退回（不分硬退软退）；括号内为 未送达人数 / 队列人数」。
  - 步骤 4：卡片显示的服务商与你找出的「未送达人数最大」那一行**一致**（I-9 规定按纯计数降序，**不是**按比率）。
    并列时取 `PROVIDER_ORDER` 靠前者——这一行为由两件事共同保证：后端按 `PROVIDER_ORDER.map` 顺序返回，
    且 JS `Array.prototype.sort` 自 ES2019 起保证稳定排序。
    若卡片显示的是队列最大的那个服务商而括号内数字看着像 0，说明前端读了已删除的 `hardBounceCount` 字段（无声失败）。
- 覆盖：O-3、S-4、I-6

### A-7: 接口异常时页面不白屏（覆盖 I-7、IP-3）

- 前置条件：能用浏览器开发者工具拦截请求（DevTools → Network → 对 `/api/mail-monitoring/provider-distribution` 设 Block request URL），或临时停掉后端。
- 操作步骤：
  1. 打开「邮件监控」，正常加载一次，确认概览 15 张卡片都在。
  2. 屏蔽 `provider-distribution` 请求。
  3. 点「手动查询」。
- 预期结果：
  - 概览区**仍然渲染出全部卡片**（「覆盖服务商数」显示 0，「最高未送达服务商」显示 `-`），页面不空白。
  - 服务商表显示「暂无数据」一行，且该行横跨整表 7 列。
  - 浏览器控制台**没有** `TypeError: providers.filter is not a function`。
- 覆盖：I-7、IP-3

### A-8: 回归 —— 前 6 列与样本不足标记不变（覆盖 N-1）

- 前置条件：近 30 天数据中存在一个「队列(人)」大于 0 且小于 30 的服务商桶（如 yahoo）。
- 操作步骤：
  1. 选「近 30 天」，逐列核对该行前 6 列。
  2. 看该行「窗口内回复率」这一格。
  3. 切到「今日」，看「7日成熟回复率」列。
- 预期结果：
  - 步骤 1：前 6 列的数值与改动前一致（若有改动前截图可直接比对）。
  - 步骤 2：回复率数字为灰色，右侧紧跟一枚橙色圆角「样本不足」标签。
  - 步骤 3：该列所有行显示 `—`。
- 覆盖：N-1

### A-9: 回归 —— 退信名单、退信统计接口、地区表不受影响（覆盖 N-4、N-5）

- 操作步骤：
  1. 点「退信名单」子标签，看列与内容。
  2. 点「按专家地区分布」表的「欧洲」行展开三角。
  3. 看「域信誉趋势」面板和「当前发件账号限额监控」表。
- 预期结果：
  - 步骤 1：仍是 `时间 | 账号 | 类型 | 失败收件人 | 失败原因 | 关联专家 | DSN | 原始 Message-ID` 八列，HARD/SOFT 徽标照旧显示。
  - 步骤 2：能正常展开出国家子行，子行数字加总等于父行。
  - 步骤 3：两个面板内容与改动前一致。
- 覆盖：N-4、N-5

### A-10: 回归 —— 概览其余 14 张卡片文案未变（覆盖 N-6）

- 操作步骤：把鼠标依次悬停在「近 30 天自动回复」「近 30 天会议邀约」「近 30 天人工待办新增」「近 30 天未匹配来信」四张卡片上。
- 预期结果：四张卡片仍有 ⓘ 标记，提示文案仍分别包含「会议邀约为自动回复的子项，细分统计不可相加」「属于自动回复子项，细分统计不可相加」「未匹配来信为人工待办子项，细分统计不可相加」「属于人工待办子项，细分统计不可相加」。
- 覆盖：N-6

---

## 已知限制（不在本计划内修复，明确记录）

- **L-1（分子分母不是同一批人，故不给率）**：「未送达(人)」按事件时间落窗口（I-2），而同行「队列(人)」是窗口内首发的去重专家。两者可能不重合——某人上月首发、本月退信，会计入本月未送达但不在本月队列。此外发送失败的专家 `sent_at` 为 NULL，压根不在队列里。因此本计划**只给绝对数不给比率**：表格该列恒显示 `undeliveredCount` 本身（含 0），「最高未送达服务商」卡片按 `undeliveredCount` **纯计数降序**排名并展示 `x/y` 两个原始数字。全链路无任何除法——既避免把它伪装成一个严格的率，也避免 `队列 = 0` 时的零分母与漏选（I-9）。
- **L-2（与「发送失败」卡片对不上是正常的）**：概览「近 N 天发送失败」卡片来自 `countFailedOutboundBetween`（`MailRecordRepository.kt:201-209`），其 WHERE 只有 `direction='OUTBOUND' AND send_status='FAILED'`，**不限 `mail_type`**（QA 回复、会议邀约的失败也算），单位是**邮件条数**且不去重。本计划的「未送达(人)」则限定 `mail_type='INTRODUCTION'`、按专家去重、且额外并入退信。两个数不应相等，也不构成互为上下界的关系。
- **L-3（退信分类不准，本计划刻意绕开）**：`bounce_record.bounce_type` 存在系统性误判——2026-09-02 线上「退信名单」9 条可见记录中，7 条 SOFT 的 `dsn_status` 全为空，即全部落在 `BounceDetector.classifyBounceType` 的 `heuristicBounce -> "SOFT"` 兜底分支，真实类型未知。**本计划不分硬软，因此不受该缺陷影响**，但也不修它。相关的下游影响（`BounceRateMonitorService` 的 5% 硬退率自动暂停只数 HARD，因而保护偏弱；`markEmailInvalid` 只在 HARD 时触发，因而部分永久失效地址未被拉黑）**依然存在**，需另案处理。
- **L-4（归因不到的退信仍归因不到）**：I-4 只是把它们如实计数并显示，不改进解析。其根因是两条解析路径中质量较高的一条实际跑不到——`AutoMailReplyService.kt:703-717` 内联用纯文本 `detect` 处理并 `markSeen`，而 `BounceCollectionService.collectBounces()` 走 `fetchUnseenMessages`，被标已读的邮件它再也看不到。另案。
- **L-6（孤儿引用只计数、不修复也不告警）**：`bounce_record` 无外键，`original_expert_contact_id` 可指向已不存在的 `expert_contact`。本计划把这类行并入表尾「未能关联到专家」计数（I-4b），但**不区分**它与「关联为空」，也不做告警或数据修复。若将来需要区分，`countUnattributedBouncesBetween` 拆成两个计数即可，不影响主查询。
- **L-5（性能）**：新查询按 `SUBSTRING_INDEX(ec.expert_email,'@',-1)` 分组，函数表达式走不了索引；但内层 UNION 两支分别命中 `idx_mail_record_status_created` 与 `idx_received_at`，结果集规模远小于队列查询，不预先优化。

---

## 修正记录

**2026-09-02，人工评审（提交执行前）** —— 原计划存在 4 处缺陷，均已在本文件内就地修正：

| # | 级别 | 问题 | 修正 |
|---|---|---|---|
| 1 | P0 | S-1/B-2 要求「队列 = 0 时未送达显示 `-`」，与 I-2/L-1「未送达按事件时间统计」自相矛盾：旧批次在本窗口退信会造成「队列 = 0、未送达 > 0」，真实数字被隐藏；卡片沿用 `.filter(sentCount > 0)` 还会漏选，并对零分母做除法 | 新增 **I-9**；S-1 改为恒显示数字；B-2.4 候选集改按 `undeliveredCount > 0` 过滤、排序改纯计数降序（去掉所有除法）；新增验收 A-11 与一条 service 单测 |
| 2 | P0 | 表尾文案「收件人与关联专家均缺失」与查询判据（只判 `original_expert_contact_id IS NULL`）不符；A-4 的造数还刻意让 `failed_recipient` 有值，文案必然失实 | 文案改为「未能关联到专家（关联为空或专家已不存在）」，写进 I-4 作为逐字要求；A-4 预期同步并点明这一点 |
| 3 | P1 | 主查询 `JOIN expert_contact` 会丢弃「`original_expert_contact_id` 非空但专家已不存在」的退信，而表尾查询也不计它 → 这类退信在 UI 上凭空消失。`bounce_record` 无任何外键（`V29`），而 `mail_record.expert_contact_id` 有 FK（`V1:16`），故只有退信这一支会出孤儿 | `countUnattributedBouncesBetween` 加 `OR NOT EXISTS(...)` 分支；I-4 拆成 (a)(b) 两类；新增 IT 用例 C-2e 与验收 A-12；新增限制 L-6 |
| 4 | P1 | 新增 IT 会互相污染：`@BeforeEach cleanMailRecords()` 只删 `inbound_mail_processing` / `mail_record`，不删 `bounce_record`；而 `seedBaseContact()`（`:682-685`）每次 `DELETE FROM expert_contact` 后以固定 id = 1 重建，残留退信会被重新关联到新专家上，且因无外键而完全静默 | C-2 拆出 **C-2a**：`cleanMailRecords()` 最前面补 `DELETE FROM bounce_record`，并写明这是后续三个用例结果可信的前提；变更文件清单第 9 项措辞同步 |

评审同时确认无误、未改动的部分：主查询的 `UNION` 去重、`created_at` / `received_at` 时间字段选择、前端对象解包与 `.catch` 兜底方案（I-7）。

**2026-09-02，第二轮人工评审（实现已提交 `bbf0828` 之后）**：

| # | 级别 | 问题 | 处理 |
|---|---|---|---|
| 5 | P1（评审提出） | C-2 要断言 `countUnattributedBouncesBetween`，但 IT 未注入 `BounceRecordRepository`，照计划实现会无法编译 | **已由实现消解，无需改计划**。核对 `bbf0828` 后的文件：`import ...BounceRecordRepository`（`:3`）、`@Autowired private lateinit var bounceRecordRepository`（`:75`）、`DELETE FROM bounce_record`（`:84`，带 C-2a 注释）三处均已就位。计划 C-2 的描述未显式点出注入，属表述不完整而非缺陷；本行即为补记。 |
| 6 | P1（评审提出） | 前端 API 由数组改对象，但 I-5 / I-7 / I-9 与 S-2 无行为测试，只靠人工 grep | **已补**：新建 `src/test/js/providerUndeliveredColumn.test.js`，21 例，覆盖表头 7 列 / colspan / 队列为 0 仍显示未送达 / 卡片按计数降序且能选中零队列桶 / 表尾文案逐字 / 表尾与数据行并存 / catch 兜底形状 / 旧字段已删净。变更文件清单增至 11 项（含超限说明）。 |

补做的验证（2026-09-02 实测，非推断）：对新测试做了 **7 项变异测试**，逐一确认用例非空转——
队列为 0 回退成 `-`（捕获 2 例失败）、表尾改回旧文案（3）、卡片改按 `sentCount` 过滤（3）、
排序改回除法（1）、`colspan` 留 8（1）、表尾被 `||` 短路吞掉（1）、`catch` 兜底改回数组（1）；
未变异对照为 `# pass 21 / # fail 0`。变异在 `$HOME` 的临时副本上进行，未触碰仓库文件。

**2026-09-02，第三轮人工评审**：

| # | 级别 | 问题 | 修正 |
|---|---|---|---|
| 7 | P0 | I-6 验收要求 `hardBounceCount\|softBounceCount` 在 `src/main/ src/test/` 全仓零命中，与 N-4「`/bounce-stats`、`getBounceStats` 不变」直接冲突——`BounceStatsResponse`（`Responses.kt:116-117`）和 `getBounceStats`（`Service.kt:277-278`）必然保留这两个字段，判据不可满足 | I-6 加「范围限定」段，只约束 `ProviderStatRow` + `providerDistribution` 链路 + `app.js`；验收改为分三处定点 grep，并补**反向断言**：`BounceStatsResponse` 与 `getBounceStats` 中这两处**必须仍在**，删掉即违反 N-4 |
| 8 | P0 | A-6 步骤 4 让验收人「算出每行未送达÷队列，找最大比率」，与 I-9「按未送达人数纯计数降序、不做除法」冲突，正确实现会被误判失败 | 改为「找未送达人数最大那行，并列取 `PROVIDER_ORDER` 靠前者」，并说明该并列行为由「后端按 `PROVIDER_ORDER.map` 返回 + JS `sort` 自 ES2019 保证稳定」共同成立 |
| 9 | P1 | 缓存键影响面审计漏 3 个文件，另有行号守卫测试被连带；变更清单从 11 项实际膨胀到 16 项，远超 10 文件硬限 | 清单按实际重写为 16 项，并在其下如实记录超限、两类遗漏的成因与**正确的审计命令**（按当前键值反查，覆盖两种拼写）；I-8 的 Applies-to 补齐 4 个文件 |
| 10 | P1 | 命令 7 `mvn clean package` 同样经过 `test` 阶段跑全量 `node --test`，却未获得命令 1/5 那条既有红用例的豁免，判据不可达 | 通过判据重写：命令 1 与 7 适用同一条豁免；并给出 `-DskipNodeTests=true` 的替代路径及其代价（必须单独补跑命令 4、5） |
| 11 | P1 | A-5 前置条件只排除 `original_expert_contact_id IS NULL`，漏了 I-4 规定同样会触发表尾的孤儿引用，正确实现会被误判失败 | 前置条件改为两类并排除，并给出可直接执行的确认 SQL |
| 12 | 小 | `ProviderDistributionResponse.unattributedBounceCount` 的 DTO 注释只写 NULL，漏孤儿引用 | 注释补齐 (a)(b) 两类 |

## Phase 4 自查

- [x] `关键不变量` 9 条（含评审后新增的 I-9）；新增的唯一语义（未送达的定义、时间口径、分桶键、未归因处理、零队列显示）均有对应不变量
- [ ] `现状审计` 列出 3 个 store 的全部相关写路径与读路径——**数据侧完整，但"改动波及面"审计不完整**：漏了缓存键的第二种拼写（3 个文件）与 `MailRecordRepository.kt` 的行号守卫（1 个文件），见修正记录第 9 项
- [x] 无任务引入未被不变量覆盖的写路径（本计划**零写路径**，纯读 + 前端）
- [x] 含前端改动 → `样式契约` 4 条；每个新增/修改 DOM 元素映射到 S-1 ~ S-4
- [x] 无「样式与现有一致 / 参考 XX」类表述；全部为 `file:line`、token 实值或逐字代码块
- [x] **新增 CSS 为 0 行**，故无「新增 class 需全文逐字」项待办；被修改的既有 class 为 0 个
- [x] `验证命令` 存在且排在 `验收标准` 之前，8 条命令均含 `JAVA_HOME` 前缀或已实测无需前缀，注明来源与通过判据
- [x] 新增/改动的测试类（`MailMonitoringServiceTest`、`MailRecordRepositoryMonitoringIT`、三个 JS 文件）均有单跑命令行及确切过滤语法
- [x] `验收标准` 与 `人工验收清单` 中所有回归项均引用 `验证命令` 节，全文无裸 `mvn test`
- [x] `人工验收清单` 12 条（含评审后新增的 A-11 / A-12）；O-1/O-2/O-3 各有覆盖；N-1/N-4/N-5/N-6 有回归条目；IP-1/IP-2/IP-3 有跨路径场景；S-1/S-2/S-4 有 UI 目测条目；I-9 与 I-4b 各有专属黑盒用例
- [x] 每条 A-n 可黑盒执行，前置条件给出可复制的 SQL 构造方式，预期结果为实值
- [ ] **File count = 16 > 10** —— 审计不完整导致（缓存键扇出 + 行号守卫），已在「变更文件清单」下方如实记录成因、正确审计命令与不可拆分的理由；相关经验已写回知识库
- [x] 子系统数 2
- [x] 每个任务按编号引用其不变量与样式契约
- [x] `验收标准` 对每条 I-n、S-n 均有检查
- [x] 文件清单无「相关文件」「等等」，逐个具名
- [x] `Out of scope` 显式排除了未送达名单页、退订、未送达率、地区表未送达列、BounceDetector 修复、error_summary 展示、按发信账号拆分
- [x] Phase 0 载入的知识均已使用或显式驳回：K-failed-mail-record-has-null-sent-at（用于 I-2）、K-frontend-cache-key-triad（用于 I-8）、K-panel-head-space-between-third-child（用于 S-3）、K-dual-outreach-paths（用于 Store 1 写路径完整性）、K-js-tests-run-via-exec-plugin + K-js-test-invocation-surface（用于验证命令）、K-first-reply-at-is-promotion-snapshot（**驳回**：本计划不读 first_reply_at）、K-html-string-truncation-breaks-cells（**驳回**：本计划不做字符串截断）、K-view-registration-triad（**驳回**：不新增视图）、K-panel-bg-token-is-translucent（**驳回**：不做浮层）
- [x] 计划已保存至 `docs/plans/2026-09-02/`
