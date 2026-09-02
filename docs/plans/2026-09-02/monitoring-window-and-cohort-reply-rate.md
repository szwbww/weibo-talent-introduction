# 邮件监控：时间窗口（今日 / 近 7 天 / 近 30 天）+ 服务商与地区的队列口径回复率

创建日期：2026-09-02
适用 skill：create-p

---

## 需求描述

### Observable outcome

1. **O-1**：邮件监控页顶部工具栏出现「今日 / 近 7 天 / 近 30 天」窗口切换；切换后「全链路指标概览」、「按收件方服务商分布」、「按专家地区分布」三块同步按该窗口重算，概览卡片文案随窗口变化（近 7 天下显示「近 7 天介绍邮件」而非「今日介绍邮件」）。
2. **O-2**：「按收件方服务商分布」的回复率改为队列口径，并新增「已回复(人)」「7 日成熟回复率」两列；队列量 < 30 的行回复率灰显并挂「样本不足」标签。
3. **O-3**：「按专家地区分布」同样改为队列口径、新增同样两列，且每个大区行可展开，显示该大区下按国家（`expert_contact.country` 原始值）拆分的队列量 / 已回复 / 回复率 / 7 日成熟回复率，子行数字加总等于父行。

### What must NOT change

1. **N-1**：`/api/mail-monitoring/sender-accounts`（「当前发件账号限额监控」）语义保持「当日」，仍只接 `date` 参数，不跟随窗口。
2. **N-2**：「域信誉趋势」面板、其自带的近 7/30/90 天下拉、`/api/mail-monitoring/reputation-history` 完全不动。
3. **N-3**：下方 6 个子标签（首发邮件 / 收信分类 / 发信回复 / 未匹配邮件 / 退信名单 / 漏斗晋级）的列、筛选、分页行为不变；它们的 `from/to` 跟随新窗口（原本就是 `from`/`to` 参数）。
4. **N-4**：概览卡片现有 10 项统计口径（介绍邮件 / 收到回复 / 回复专家数 / 自动回复 / 人工外发 / 会议邀约 / 人工待办新增 / 未匹配来信 / 发送失败 / APPLICATION 晋级）的 SQL 不变，只是窗口从「一天」变成「N 天」；「细分统计不可相加」提示文案保留。
5. **N-5**：`#monitoringDate` 这个 `<input type="date">` 保留（语义变为「窗口结束日 / 锚点日」），`#monitoringSenderAccount`、`#monitoringRefreshBtn`、`#monitoringLastRefreshed` 的 id 与行为保留。

### Out of scope（明确不做）

- **不做**「按发信账号（我方邮箱）回复率」面板 —— 需求方已明确排除。
- **不做**中位首回时延、退订看板、自动回复质量看板、漏斗看板等其他监控面板。
- **不做**「自定义时间区间」的双日期框（预览稿中该按钮无实际行为，本次不实现）。
- **不做** `expert_contact.country` 的取值归一化（同一国家的多种写法将各成一行，详见 L-2）。
- **不做** `email_domain` 冗余列或每日聚合表等性能优化（详见 L-1）。
- **不做**「窗口内回复率 / 旧口径」双口径切换开关。
- **不做**给退信率、软退数改口径（这两列沿用现有 `bounce_record` 按域名聚合）。

---

## 关键不变量

### Invariant I-1: 队列口径的分母是「窗口内首发 INTRODUCTION 的去重专家」

- Rule：分母 `cohortCount` = 在 `[from, to)` 内 `mail_record.direction='OUTBOUND' AND mail_type='INTRODUCTION' AND sent_at` 落在窗口内的记录，**按 `expert_contact_id` 去重后的人数**（`COUNT(*)` over a `GROUP BY expert_contact_id` 子查询）。禁止使用 `COUNT(*)` 直接数邮件条数。
- Applies to：`MailRecordRepository.aggregateIntroCohortByCountry`、`aggregateIntroCohortByDomain`（本计划新增的两条 SQL，且是仅有的两条）。
- Violation consequence：同一专家的重复投递会把分母吹大，而分子恒去重 → 回复率被系统性低估。这正是现有 `aggregateIntroSentByDomain`（`MailRecordRepository.kt:360`，`COUNT(*)`）配 `aggregateInboundByDomain`（`:373`，`COUNT(DISTINCT expert_contact_id)`）的缺陷。
- 来源：original

### Invariant I-2: 队列口径的分子是「该队列成员在其 INTRODUCTION 之后收到的首封 INBOUND」

- Rule：分子 `repliedCount` = 队列成员中，存在 `mail_record.direction='INBOUND'` 且 `received_at >= 该成员的 INTRODUCTION sent_at` 的人数。回复时间取 `MIN(received_at)`。**分子与分母必须是同一批 `expert_contact_id`**，禁止各自独立按窗口聚合后相除。
- Applies to：同 I-1 的两条 SQL。
- Violation consequence：现状即是反例 —— 分子是「窗口内任何回信的人」，分母是「窗口内发信的人」，两者是不同队列。窗口越短失真越大，停发日分母为 0。
- 来源：original

### Invariant I-3: 回复时间戳只认 `mail_record` 的 INBOUND，不认 `expert_contact.first_reply_at`

- Rule：判定「是否回复」与「何时回复」一律以 `mail_record` INBOUND 的 `received_at` 为准。**禁止**使用 `expert_contact.first_reply_at` 或 `last_reply_at` 作为回复信号。
- Applies to：本计划新增的两条 SQL。
- Violation consequence：`first_reply_at` 的**唯一 DB 写入点**是 `AutomaticApplicationPromotionService.doPromote()`（`AutomaticApplicationPromotionService.kt:84-90`，`expertContactRepository.save(contact.copy(..., firstReplyAt = firstReplyAt))`），且只在**晋级 APPLICATION 成功时**才写；`UnmatchedInboundMailService.kt:157-158` 只是构造 copy 传给 ES writer。因此凡是回了信但未晋级的专家，`first_reply_at` 恒为 NULL —— 用它当分子会把回复率压到「晋级率」。`last_reply_at`（`AutoMailReplyService.kt:480/654/898`）虽然每封来信都写，但它是「最后一封」，无法支撑 I-4 的「7 日内首回」。
- 来源：original（本次 Phase 1b 实测发现，推翻了立项讨论中「用 firstReplyAt」的初步设想）

### Invariant I-4: 「7 日成熟回复率」的分母是队列中已满 7 天的子集

- Rule：`matureCohortCount` = 队列中 `INTRODUCTION sent_at < :matureBefore` 的人数（`:matureBefore` = 查询时刻 - 7 天）；`matureRepliedCount` = 该子集中首封 INBOUND 落在 `[sent_at, sent_at + 7 天)` 的人数。SQL 中的 `INTERVAL 7 DAY` 与 Kotlin 侧计算 `:matureBefore` 用的天数**必须是同一个常量** `MailMonitoringService.MATURITY_DAYS = 7`。
- Applies to：同 I-1 的两条 SQL；`MailMonitoringService.providerDistribution` / `regionDistribution` 计算 `matureBefore` 的位置。
- Violation consequence：两处天数不一致 → 分子分母取自不同成熟窗口，比率无意义且不会报错。若省略 `matureCohortCount` 而直接用 `cohortCount` 当分母，近 7 天窗口下大部分队列尚未满 7 天，成熟回复率会被稀释成接近 0。
- 来源：original

### Invariant I-5: 每位专家至多一封 INTRODUCTION，队列成员的 `first_sent_at` 即其唯一首发时间

- Rule：`MIN(sent_at)` 作为队列成员的首发时间是安全的，因为 INTRODUCTION 在业务上每专家至多一封。
- Applies to：I-1 / I-2 / I-4 的 SQL 语义前提。
- Violation consequence（依据）：`mail_send_attempt` 有 `UNIQUE KEY uq_orcid_mail_type (orcid_id, mail_type)`（`V6__create_mail_send_attempt.sql` 建表），且 `ManualInitialOutreachService.kt:734` 在发信前 `findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")` 做存在性判定。若将来放开重复首发，I-2 的「回复晚于首发」判定需改为按每封 INTRODUCTION 分别归因。
- 来源：original

### Invariant I-6: 发送失败的 INTRODUCTION 天然不进队列，无需 `send_status` 过滤

- Rule：SQL 用 `sent_at >= :from AND sent_at < :to` 即可，**不要**再加 `AND send_status = 'SENT'`。
- Applies to：I-1 的两条 SQL。
- Violation consequence（依据）：`mail_record` 中 `sendStatus = "FAILED"` 的三个写入点全部同时写 `sentAt = null` —— `ManualOutreachTxHelper.kt:123/126`、`ManualReplySendAttemptService.kt:303/304`、`ManualReplySendAttemptService.kt:320/322`；INTRODUCTION 的成功写入点 `ManualOutreachTxHelper.kt:63/72/74` 写 `sendStatus="SENT", sentAt=now`。因此 `sent_at` 非空即等价于已发出。多加过滤条件不会改变结果，但会让 `idx_mail_record_dir_type_sent (direction, mail_type, sent_at)` 之外多一个无用谓词。
- 来源：original

### Invariant I-7: 大区行的数字必须等于其国家子行之和

- Rule：`RegionStatRow` 的 `cohortCount` / `repliedCount` / `matureCohortCount` / `matureRepliedCount` 必须由其 `countries: List<RegionCountryRow>` 逐字段求和得到，**不得**由服务端另算一遍或由前端分别请求。回复率是求和后再相除，不是子行比率的平均。
- Applies to：`MailMonitoringService.regionDistribution`。
- Violation consequence：父子行对不上，是用户第一眼就会发现的信任崩塌点（预览稿第一版即踩过：父行 31 回复 vs 子行加总 20）。
- 来源：original

### Invariant I-8: 窗口切换后所有跟随窗口的读路径必须同时刷新，包括 60 秒自动刷新

- Rule：`scheduleMonitoringAutoRefresh()`（`app.js:11490`）内部对 `/summary` 的调用必须使用与 `loadMonitoring()` 相同的 `from`/`to`，不得回落到单日 `date`。
- Applies to：`app.js` 的 `loadMonitoring`、`monitoringWindowParams`、`scheduleMonitoringAutoRefresh`。
- Violation consequence：用户切到近 30 天，60 秒后自动刷新把概览卡片悄悄改回当日数字，而标题仍写「近 30 天」。当前 `scheduleMonitoringAutoRefresh` 确实自建 `params.set("date", ...)`（`app.js:11495-11496`），是必然踩中的点。
- 来源：original（形态同 K-relocated-control-refresh-owner：搬控件要跟着搬首次/周期刷新的责任）

### Invariant I-9: 静态资源缓存键三元组必须同值同时 bump

- Rule：`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三处取值必须完全相同，且本次一起改为 `20260902-monitoring-window`。
- Applies to：`index.html:11 / :2105 / :2106`；`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51`。
- Violation consequence：只 bump 部分键 → `node --test` 断言失败，`mvn test` 在 test 阶段中止。
- 来源：K-frontend-cache-key-triad（**该条已部分过期**：条目称有 4 个测试文件钉死具体字符串，实测 2026-09-02 只有 `batchSendTaskConsoleVisualFix.test.js` 钉字符串，`checkRepliesRelocation` / `overlayAndDialogContrast` / `manualReplySubjectPrefill` 三者已改为 `/\?v=[^"]+/g` 提取后比相等，不含硬编码值。见 Phase 6 修正。）

---

## 样式契约

本计划触及 `index.html` 与 `app.js`，适用本节。**总原则：只新增 1 个 class，其余全部复用既有 class。**

### S-1: 窗口切换控件（工具栏）

- **复用**：`.tabs`（`styles.css:2908-2912`）、`.tab`（`:2914-2925`）、`.tab:hover`（`:2927-2930`）、`.tab.active`（`:2932-2937`）、`.button`（`:786-808`）、`.button.secondary`（`:836-840`）。
  与页面下方已有的子标签用完全相同的组合 `class="tab button secondary"` / 选中态追加 `active`（现有写法见 `index.html:245-250`）。禁止自造近似样式。
- **新增**：无（0 行新 CSS）。
- **DOM 结构**：`index.html` 的 `#view-monitoring > .toolbar` 内，把 `<input type="date" id="monitoringDate">` **之前**插入下面这段，其余子元素顺序与属性不动：

```html
<div class="tabs" id="monitoringRangeTabs">
    <button class="tab button secondary" data-range="1">今日</button>
    <button class="tab button secondary active" data-range="7">近 7 天</button>
    <button class="tab button secondary" data-range="30">近 30 天</button>
</div>
```

- **禁止项**：inline style；新增 class；修改 `.toolbar` / `.tabs` / `.tab` 既有规则块；改动 `#monitoringDate`、`#monitoringSenderAccount`、`#monitoringRefreshBtn`、`#monitoringLastRefreshed` 四个元素的 id、class 与既有 inline style。

### S-2: 「样本不足」标记

- **复用**：`badge()`（`app.js:1492-1494`，输出 `<span class="badge warn">`）、`.badge`（`styles.css:1038-1050`）、`.badge.warn`（`:1058-1062`）、`.text-muted`（`:2461-2464`）。
- **新增**：无（0 行新 CSS）。
- **DOM 结构**：队列量 < 30 时该单元格渲染为

```html
<span class="text-muted">6.9%</span> <span class="badge warn">样本不足</span>
```

  队列量 ≥ 30 时只渲染纯文本百分比；队列量 = 0 时渲染 `<span class="text-muted">-</span>`（与现有 provider 表「硬退率」列 0 值写法一致，见 `app.js:11221`）。
- **禁止项**：为「样本不足」新造 class 或写 inline style。

### S-3: 地区表的展开控件与国家子行

- **复用**：`.button`（`styles.css:786-808`）、`.button.small`（`:2454-2459`）、`.text-muted`（`:2461-2464`）。
- **新增**：**仅此一个 class**，逐字追加到 `styles.css` 末尾（本仓库无暗色适配的表格子行先例，故不写 `prefers-color-scheme` 分支，与 K-panel-bg-token-is-translucent 的「要么整体做要么都不做」一致）：

```css
.monitoring-region-child > td {
    background: rgba(15, 23, 42, 0.018);
    color: var(--text-secondary);
}

.monitoring-region-child > td:first-child {
    padding-left: 34px;
}
```

- **DOM 结构**：大区行第一格与国家子行分别为

```html
<!-- 大区行（可展开时才有 button；无国家数据时第一格只有 strong） -->
<tr>
    <td><button class="button small" data-action="toggle-region" data-region="Europe">▸</button> <strong>欧洲</strong></td>
    ...
</tr>
<!-- 展开后紧随其后的国家子行 -->
<tr class="monitoring-region-child">
    <td>Germany</td>
    ...
</tr>
```

  展开态按钮文本为 `▾`，收起态为 `▸`。
- **禁止项**：inline style；改动 `table` / `th, td`（`styles.css:977-987`）/ `tbody tr`（`:3518-3531`）既有规则；用 `substring` 对拼好的 HTML 字符串限长（K-html-string-truncation-breaks-cells）。
- **`.monitoring-region-child` 使用点 grep**：新建 class，全仓当前 0 处使用；本计划新增的使用点仅 `app.js` 的 `renderMonitoringRegionDistribution` 一处。

### S-4: 面板头不得出现第三个子元素

- **复用**：`.panel-head`（`styles.css:946-952`，`display:flex; justify-content:space-between`）。
- **新增**：无。
- **DOM 结构**：`按收件方服务商分布` / `按专家地区分布` 两个面板的 `.panel-head` **保持只有 `<h2>` 一个子元素**，与现状（`index.html:207-208 / 217-218`）完全一致。
- **禁止项**：往这两个 `.panel-head` 里加说明文字 `<span>`、口径提示或任何第二/第三个子元素（预览稿里那行灰色口径说明**不实现**）。依据 K-panel-head-space-between-third-child：第三个子元素会被 `space-between` 均分，飘到标题与右侧元素之间。

---

## 现状审计

### Store 1：`mail_record`（MySQL）

- **相关列**：`direction`、`mail_type`、`expert_contact_id`（NOT NULL）、`sent_at`、`received_at`、`send_status`。
- **相关索引**：`idx_mail_record_dir_type_sent (direction, mail_type, sent_at)`、`idx_mail_record_dir_received (direction, received_at)`（`V25__add_mail_record_monitoring_indexes.sql:11-12`）。本计划两条新 SQL 的两个子查询分别正好命中这两条索引。
- **本计划相关的写路径**：
  1. `ManualOutreachTxHelper.kt:60-78` — 唯一的 OUTBOUND/INTRODUCTION 成功写入点，`sendStatus="SENT"`, `sentAt=now`, `triggeredBy="MANUAL"`。
  2. `ManualOutreachTxHelper.kt:114-126` — 同上失败分支，`sendStatus="FAILED"`, `sentAt=null`。
  - K-dual-outreach-paths 记载的两条首发链路（`InitialOutreachService` / `ManualInitialOutreachService`）**都注入并汇入** `ManualOutreachTxHelper`（`InitialOutreachService.kt:26`、`ManualInitialOutreachService.kt:73`），全仓 `grep 'mailType = "INTRODUCTION"'` 在 `MailRecord(` 构造中仅命中上述 2 处。故队列口径覆盖两条链路，无遗漏。
  3. `AutoMailReplyService.kt:267-284`（`processSingle` 主路径）— INBOUND/REPLY，`receivedAt=received.receivedAt`。
  4. `AutoMailReplyService.kt:799-822`（`saveMailRecord`，重复来信分支）— INBOUND/REPLY，同上。
  - 退信与 DMARC 报告**不会**进 INBOUND：`AutoMailReplyService.kt:703-717` 在 `processSingle` 之前用 `bounceDetector.detect` 拦截并 `return@forEach`，`:718-727` 同理拦 DMARC。
- **本计划相关的读路径**：
  1. `MailMonitoringService.providerDistribution`（`:276-306`）— 经 `aggregateIntroSentByDomain`（`MailRecordRepository.kt:360`）、`aggregateInboundByDomain`（`:373`）。**本计划替换**。
  2. `MailMonitoringService.regionDistribution`（`:308-340`）— 经 `aggregateIntroSentByCountry`（`:335`）、`aggregateInboundByCountry`（`:347`）。**本计划替换**。
  3. `MailMonitoringService.summary`（`:62-79`）— 10 条 `countXxxBetween`，全部已接受 `(from, to)`，本计划只改传入的窗口，不动 SQL。
  4. `MailMonitoringService.senderAccountHealth`（`:235`）、`listIntroductions` / `listOutboundReplies` / `listInbound` / `listPromotions` — 本计划不改。
- **上述 4 条被替换的 SQL 的全部调用方（grep `aggregateIntroSentBy|aggregateInboundBy` 全仓）**：仅 `MailMonitoringService.kt:281/285/312/316` 与 `MailMonitoringServiceTest.kt:61/70/101/108/132/135/137/140/154/157`。无其他生产调用方，可安全删除。

### Store 2：`expert_contact`（MySQL）

- **相关列**：`country`（`idx_expert_contact_country`，`V26__add_expert_contact_country_index.sql:2`）、`expert_email`、`first_reply_at`（`V12__expert_contact_first_reply.sql:2`）、`last_reply_at`。
- **`first_reply_at` 的全部写路径（grep `firstReplyAt = ` 全仓）**：
  1. `AutomaticApplicationPromotionService.kt:84-90` — **唯一写入 DB 的点**（`expertContactRepository.save`），且被 `if (contact.applicationIndexed) return contact` 与 `promoteToApplication` 成功与否守卫。
  2. `UnmatchedInboundMailService.kt:157-158` — 只构造 `updatedContact` 传给 ES writer 与后续 `currentContact`，非独立的 first_reply_at 落库语义。
  3. `ExpertContactManagementService.kt:265-273`、`ExpertIndexLevelOperationService.kt:74-82 / 91-99`、`ExpertIndexWriterService.kt:483-487`、`ExpertIndexController.kt:109-113` — 全部是**读取已有值并转 Instant 写 ES**，不写 DB 列。
  - 结论：`first_reply_at` ≈「晋级时刻的快照」，不是「首次回信时刻」。→ I-3。
- **`last_reply_at` 的全部写路径**：`AutoMailReplyService.kt:480 / 654 / 898`，每封匹配到专家的来信都写。语义正确但只保留最后一封 → 不满足 I-4。
- **本计划的读路径**：新增两条 SQL 只 `JOIN expert_contact` 取 `country` 与 `expert_email`，不写。

### Store 3：`bounce_record`（MySQL）

- 本计划**不改**。`BounceRecordRepository.aggregateBouncesByDomain(from, to)`（`BounceRecordRepository.kt:58-68`）已接受任意 `(from, to)`，窗口拉长后自动按窗口聚合，无需改动。

### Store 4：`expert_application_promotion`（MySQL）

- 本计划**不改**。`aggregateSuccessByCountry(from, to)`（`ExpertApplicationPromotionRepository.kt:52-62`）已接受任意 `(from, to)`，为地区表「晋级」列供数。

### Interaction points

- **IP-1**：新 SQL（写侧无，读侧新增）↔ `MailMonitoringService.regionDistribution` 的父子求和（I-7）。国家子行由同一条 SQL 的原始行折叠而来，不存在第二次查询。
- **IP-2**：`loadMonitoring()`（`app.js:11109`，窗口的唯一权威来源）↔ `scheduleMonitoringAutoRefresh()`（`:11490`，自建 `date` 参数）。→ I-8。
- **IP-3**：`loadMonitoring()` 的窗口 ↔ `monitoringRangeParams()`（`:11141`，供下方 6 个子标签用 `from`/`to`）↔ `src/test/js/monitoringDateDefault.test.js:78-89`（断言 `from === to === 今天`）。窗口默认改成近 7 天后该断言必然失败，必须同步改。
- **IP-4**：`index.html` 静态资源缓存键 ↔ `batchSendTaskConsoleVisualFix.test.js:49-51`。→ I-9。
- **IP-5**：`/api/mail-monitoring/sender-accounts` 仍收 `date`（N-1）↔ `loadMonitoring()` 现在同时持有窗口与锚点日；必须继续给它传单日 `date`（= 锚点日），不能顺手改成 `from/to`。

### 前端样式盘点

- **可复用 class**：
  - `.tabs` — `styles.css:2908-2912` — `display:flex; flex-wrap:wrap; gap:4px`
  - `.tab` / `.tab:hover` / `.tab.active` — `styles.css:2914-2937` — 选中态 `background: var(--primary); color:#fff; font-weight:600`
  - `.button` — `styles.css:786-808`；`.button.secondary` — `:836-844`；`.button.small` — `:2454-2459`（`height:26px; padding:0 8px; font-size:11px`）
  - `.badge` — `:1038-1050`；`.badge.warn` — `:1058-1062`（`--warning-bg` / `--warning` / `--warning-border`）
  - `.text-muted` — `:2461-2464`（`color: var(--text-muted); font-size:12px`）；`.muted` — `:2967-2970`（同值）
  - `.metric-card` / `.metric-label` / `.metric-value` — `:2861-2905`；`.card-grid` — `:2853-2859`
  - `.panel` — `:932-939`；`.panel-head` — `:946-952`；`.toolbar` — `:351-360`
  - `table` — `:977-981`（`min-width:720px`）；`th, td` — `:983-987`（`padding:8px 14px`）
- **设计基准 token 实值**（`styles.css:1-80`）：`--primary:#1e40af`；`--primary-bright:#3b82f6`；`--text-main:#1e293b`；`--text-secondary:#475569`；`--text-muted:#94a3b8`；`--line:rgba(15,23,42,0.055)`；`--border:rgba(15,23,42,0.11)`；`--surface:rgba(15,23,42,0.022)`；`--warning:#d97706`；`--radius-sm:7px`；`--radius-md:10px`；`--radius-lg:18px`。
- **DOM 结构约定**：分布表统一为 `<section class="panel"><div class="panel-head"><h2>…</h2></div><div class="table-wrap"><table id="…"><thead></thead><tbody></tbody></table></div></section>`（`index.html:206-224`），`thead`/`tbody` 由 `app.js` 的 render 函数 `innerHTML` 填充。进度条统一用 `monitoringDistributionBar(value, maxValue)`（`app.js:11198-11203`），百分比统一用 `formatPercent(value)`（`:11193-11196`）。
- **改动前基线**：
  - 工具栏 `index.html:193-200`（见 S-1，本计划仅在 `#monitoringDate` 前插入 `#monitoringRangeTabs`）。
  - 服务商表头（`app.js:11212-11216`）：`服务商 | 发送量 | 发送 | 回复率 | 硬退率 | 软退`
  - 地区表头（`app.js:11235-11239`）：`地区 | 发送量 | 发送 | 回复率 | 晋级`
  - 概览卡片 13 项定义 `app.js:11168-11184`，其中 11 项文案以「今日」开头。

---

## 实现方案

### 阶段 A：后端 —— 队列口径查询（遵守 I-1 ~ I-7）

#### A-1. `MailRecordRepository.kt`：新增两个投影 + 两条 SQL，删除四条旧 SQL

新增投影（放在现有 `data class DomainCount` 位置附近，构造器映射，命名沿用 `DomainBounceCount` 的 snake_case → camelCase 约定）：

```kotlin
data class CountryCohortStat(
    val country: String?,
    val cohortCount: Long,
    val repliedCount: Long,
    val matureCohortCount: Long,
    val matureRepliedCount: Long
)

data class DomainCohortStat(
    val domain: String?,
    val cohortCount: Long,
    val repliedCount: Long,
    val matureCohortCount: Long,
    val matureRepliedCount: Long
)
```

新增查询（按国家；按域名版本除 `SELECT`/`GROUP BY` 的表达式换成 `SUBSTRING_INDEX(ec.expert_email, '@', -1)` 外完全一致）：

```sql
SELECT ec.country AS country,
       COUNT(*) AS cohort_count,
       SUM(CASE WHEN r.first_reply_at IS NOT NULL THEN 1 ELSE 0 END) AS replied_count,
       SUM(CASE WHEN s.first_sent_at < :matureBefore THEN 1 ELSE 0 END) AS mature_cohort_count,
       SUM(CASE WHEN s.first_sent_at < :matureBefore
                 AND r.first_reply_at IS NOT NULL
                 AND r.first_reply_at < DATE_ADD(s.first_sent_at, INTERVAL 7 DAY)
                THEN 1 ELSE 0 END) AS mature_replied_count
  FROM (SELECT expert_contact_id, MIN(sent_at) AS first_sent_at
          FROM mail_record
         WHERE direction = 'OUTBOUND' AND mail_type = 'INTRODUCTION'
           AND sent_at >= :from AND sent_at < :to
         GROUP BY expert_contact_id) s
  JOIN expert_contact ec ON ec.id = s.expert_contact_id
  LEFT JOIN (SELECT expert_contact_id, MIN(received_at) AS first_reply_at
               FROM mail_record
              WHERE direction = 'INBOUND' AND received_at IS NOT NULL
                AND received_at >= :from
              GROUP BY expert_contact_id) r
    ON r.expert_contact_id = s.expert_contact_id
   AND r.first_reply_at >= s.first_sent_at
 GROUP BY ec.country
```

- `INTERVAL 7 DAY` 的 7 与 `MATURITY_DAYS` 绑定（I-4），在方法上方写注释指明这一耦合。
- 内层 INBOUND 子查询加 `received_at >= :from` 是必需的边界（否则全表聚合）；语义安全性由 I-5 保证。
- 删除：`aggregateIntroSentByCountry`（:324-335）、`aggregateInboundByCountry`（:337-347）、`aggregateIntroSentByDomain`（:349-360）、`aggregateInboundByDomain`（:362-373）；同时删除随之失去引用的 `data class DomainCount`（:21-24）。`data class CountryCount`（:16-19）**保留**（`ExpertApplicationPromotionRepository.kt:62` 仍用）。

#### A-2. `MailMonitoringResponses.kt`：扩展两个行 DTO，新增国家子行 DTO

```kotlin
data class ProviderStatRow(
    val provider: String,
    val sentCount: Long,          // = cohortCount，字段名保留以免破坏既有前端读取
    val repliedCount: Long,
    val replyRate: Double,
    val matureCohortCount: Long,
    val matureRepliedCount: Long,
    val matureReplyRate: Double,
    val hardBounceCount: Long,
    val softBounceCount: Long
)

data class RegionCountryRow(
    val country: String,
    val sentCount: Long,
    val repliedCount: Long,
    val replyRate: Double,
    val matureCohortCount: Long,
    val matureRepliedCount: Long,
    val matureReplyRate: Double
)

data class RegionStatRow(
    val region: String,
    val sentCount: Long,
    val repliedCount: Long,
    val replyRate: Double,
    val matureCohortCount: Long,
    val matureRepliedCount: Long,
    val matureReplyRate: Double,
    val promotionCount: Long,
    val countries: List<RegionCountryRow>
)
```

`sentCount` 名称沿用，语义变为「队列人数」（I-1）；在两个 DTO 上方各加一行注释写明这一点。

#### A-3. `MailMonitoringService.kt`

- 新增 `companion object` 常量 `const val MATURITY_DAYS = 7L`（I-4）。
- `summary(date: LocalDate?)` → `summary(from: LocalDate?, to: LocalDate?)`，内部 `dateRangeResolver.resolveRange(from, to)`（该方法已存在，`MonitoringDateRangeResolver.kt:18-22`，无需新增）；`DailySummary` 增加 `from: String` / `to: String` 两个字段，`date` 字段保留并赋值为窗口结束日字符串。
- `providerDistribution(date)` → `providerDistribution(from: LocalDate?, to: LocalDate?)`：
  - `val (start, end) = dateRangeResolver.resolveRange(from, to)`
  - `val matureBefore = LocalDateTime.now().minusDays(MATURITY_DAYS)`
  - 用 `aggregateIntroCohortByDomain(start, end, matureBefore)` 一次取全部四个计数，按 `providerResolver.resolve("x@$domain")` 折叠进 `PROVIDER_ORDER` 桶（折叠逻辑与现有 `resolveProviderFromDomain`、`:334-338` 保持一致）。
  - 退信仍用 `bounceRecordRepository.aggregateBouncesByDomain(start, end)`，不变。
  - `replyRate = ratio(repliedCount, cohortCount)`；`matureReplyRate = ratio(matureRepliedCount, matureCohortCount)`（`ratio` 已存在，`:342-343`，分母为 0 返回 0.0）。
- `regionDistribution(date)` → `regionDistribution(from: LocalDate?, to: LocalDate?)`：
  - 用 `aggregateIntroCohortByCountry(start, end, matureBefore)` 拿到国家级行；先构造 `RegionCountryRow`（`country` 为空/空白时用字面量 `"未知"`），按 `CountryContinentMapping.toRegion(country)` 分组；**大区四个计数由子行求和得到**（I-7）；子行按 `sentCount` 降序。
  - 「晋级」列仍来自 `promotionRepository.aggregateSuccessByCountry(start, end)` 折叠到大区，只填在大区行，`RegionCountryRow` 无此字段。
  - 输出顺序仍为 `CountryContinentMapping.allRegions()`（现有行为，`:326-338`）。
- 删除 `MutableProviderStats`（:345-350）与 `MutableRegionStats`（:352-356）中不再需要的字段，或整体重写为直接构造 DTO —— 以不引入新中间类为准。
- `senderAccountHealth(date)` **不动**（N-1）。

#### A-4. `MailMonitoringController.kt`

- `/summary`（:39-41）、`/provider-distribution`（:104-108）、`/region-distribution`（:110-114）三个端点的 `date: LocalDate?` 换成 `from: LocalDate?` + `to: LocalDate?`（与同文件 `/introductions`（:43-51）已有的写法逐字一致：`@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`）。
- `/sender-accounts`（:93-95）保持 `date` 不变（N-1）。

### 阶段 B：前端（遵守 I-8、I-9、S-1 ~ S-4）

#### B-1. `index.html`

1. 按 S-1 在 `.toolbar` 内插入 `#monitoringRangeTabs`。
2. 三处缓存键改为 `?v=20260902-monitoring-window`（`:11`、`:2105`、`:2106`）（I-9）。
3. 两个分布面板的 `.panel-head` 保持单子元素（S-4），不做任何改动。

#### B-2. `app.js`

1. `state.monitoring`（`:39-56`）：保留 `date: monitoringToday()` 这一行不动（`monitoringDateDefault.test.js:61-64` 用正则钉死），新增 `rangeDays: 7`。
2. 新增 `monitoringWindowParams()`：由 `state.monitoring.date`（窗口结束日）与 `rangeDays` 推出 `from`/`to` 两个 `YYYY-MM-DD`，`from = date - (rangeDays - 1) 天`。日期推算必须用 `Date.UTC` 纯日历运算，**不得**用 `toISOString().slice(0,10)` 对本地时间取值（`monitoringDateDefault.test.js:65-69` 明确 `doesNotMatch` 该模式）。
3. `monitoringRangeParams()`（`:11141-11153`）：`from`/`to` 改为取自 `monitoringWindowParams()`，其余（`pageSize`、`pageOffset`、`senderAccountCode`）不动 —— 下方 6 个子标签随之跟随窗口（N-3）。
4. `loadMonitoring()`（`:11109-11139`）：`/summary`、`/provider-distribution`、`/region-distribution` 三个请求改用窗口参数；`/sender-accounts` 继续传单日 `date`（IP-5）。同步 `#monitoringDate.value`（保留 `dateInput.value = state.monitoring.date || monitoringToday()` 这行，测试钉死）与 `#monitoringRangeTabs` 的 `active` 态。
5. `scheduleMonitoringAutoRefresh()`（`:11490-11515`）：内部 `/summary` 请求改用同一个 `monitoringWindowParams()`（I-8）。
6. `renderMonitoringCards()`（`:11155-11191`）：新增 `monitoringRangeLabel()` 返回 `"今日"` / `"近 7 天"` / `"近 30 天"`，把 11 项以「今日」开头的卡片文案改为该前缀拼接；**保留**两条含「细分统计不可相加」的 hint 原文（`monitoringDateDefault.test.js:101-103` 断言）；新增两张卡片「窗口内回复率」「7 日成熟回复率」，其值由 `state.monitoring.regionDistribution` 的 `sentCount` / `repliedCount` / `matureCohortCount` / `matureRepliedCount` 求和后相除得出（不新增后端字段）；`rangeDays === 1` 时「7 日成熟回复率」显示 `—`。
7. `renderMonitoringProviderDistribution()`（`:11205-11226`）：表头改为 `服务商 | 分布 | 队列(人) | 已回复(人) | 窗口内回复率 | 7日成熟回复率 | 硬退率 | 软退`；回复率单元格按 S-2 处理样本不足；`matureCohortCount === 0` 时该列渲染 `<span class="text-muted">—</span>`。
8. `renderMonitoringRegionDistribution()`（`:11228-11247`）：表头改为 `地区 | 分布 | 队列(人) | 已回复(人) | 窗口内回复率 | 7日成熟回复率 | 晋级`；按 S-3 渲染展开按钮与国家子行；展开态存于 `state.monitoring.expandedRegions`（对象，key 为 region 原值）；行拼接必须先构造数组再 `join("")`，禁止对拼好的 HTML 做 `substring`（K-html-string-truncation-breaks-cells）。
9. `bindMonitoringEvents()`（`:11517-...`）：
   - 新增 `#monitoringRangeTabs` 的 click 委托：读 `data-range` → 写 `state.monitoring.rangeDays` → `state.monitoring.page = 0` → 切 `active` → `loadMonitoring()`。
   - `#monitoringDate` 的 change 处理保持现状（改锚点日 → `loadMonitoring()`）。
   - 新增地区表 `[data-action="toggle-region"]` 的 click 委托：翻转 `state.monitoring.expandedRegions[region]` → 只重渲染地区表，**不发请求**（数据已在 `state` 中，I-7 保证子行来自同一份数据）。

### 阶段 C：测试同步

#### C-1. `MailMonitoringServiceTest.kt`

- 删除/重写针对 4 条旧 SQL 的 mock 与断言（`:60-98`、`:100-128`、`:130-151`、`:153-171`）。
- 新增用例，全部用 `Mockito.mock` 喂 `CountryCohortStat` / `DomainCohortStat`：
  - `providerDistribution folds domains via ProviderResolver`（保留原意图，改喂新投影）
  - `regionDistribution folds countries via CountryContinentMapping in allRegions order`（同上）
  - `regionDistribution region totals equal sum of country rows`（I-7）
  - `regionDistribution keeps country rows sorted by cohort desc and maps blank country to 未知`
  - `replyRate and matureReplyRate use their own denominators and return 0 when denominator is 0`（I-4）
  - `providerDistribution separates hard and soft bounces by domain bucket`（保留，退信路径未变）
- 移除已删除的 `DomainCount` import。

#### C-2. `MailRecordRepositoryMonitoringIT.kt`（真 MySQL，验证 SQL 语义）

新增 3 个用例（沿用文件现有的 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` 与 `JdbcTemplate` 造数套路）：

- `intro cohort dedupes repeated introductions per expert`（I-1）：同一专家插 2 条 INTRODUCTION → `cohortCount == 1`。
- `intro cohort counts reply only when inbound is after the introduction`（I-2 + I-3）：造 A（有 INBOUND 晚于首发，`first_reply_at` 列留 NULL）、B（无 INBOUND 但 `first_reply_at` 有值）→ `repliedCount == 1`，且计入的是 A。
- `mature cohort excludes introductions newer than seven days`（I-4）：造首发在 2 天前与 10 天前各一 → `matureCohortCount == 1`，10 天前那位若在 3 天内回信则 `matureRepliedCount == 1`。

#### C-3. `src/test/js/monitoringDateDefault.test.js`

- 保留 `monitoring: { date: monitoringToday()`、`dateInput.value = ...`、`toISOString` 反模式、`细分统计不可相加` 四条断言（对应 B-2 中被要求保留的写法）。
- 改写 `monitoringRangeParams uses Shanghai today when date is unset` / `keeps explicit monitoring date` 两条：sandbox 的 `state.monitoring` 增加 `rangeDays`，断言默认 `rangeDays: 7` 时 `to === 锚点日` 且 `from === 锚点日 - 6 天`；`rangeDays: 1` 时 `from === to`；`rangeDays: 30` 时相差 29 天。
- 新增一条：`state.monitoring` 默认含 `rangeDays: 7`（正则断言 `app.js` 源码）。

#### C-4. `src/test/js/batchSendTaskConsoleVisualFix.test.js`

- `:49-51` 三处硬编码字符串改为 `20260902-monitoring-window`（I-9）。

---

## 变更文件清单

| # | 文件 | 改动性质 |
|---|------|----------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 新增 2 投影 + 2 查询；删除 4 查询与 `DomainCount` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/controller/MailMonitoringResponses.kt` | 扩展 `ProviderStatRow` / `RegionStatRow`，新增 `RegionCountryRow` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt` | `summary` / `providerDistribution` / `regionDistribution` 改窗口 + 队列口径；新增 `MATURITY_DAYS` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/monitoring/controller/MailMonitoringController.kt` | 3 个端点 `date` → `from`/`to` |
| 5 | `src/main/resources/static/index.html` | 插入 `#monitoringRangeTabs`；3 处缓存键 bump |
| 6 | `src/main/resources/static/app.js` | 窗口状态与参数、5 个 render/bind 函数、自动刷新 |
| 7 | `src/main/resources/static/styles.css` | 仅追加 `.monitoring-region-child` 两条规则（S-3） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringServiceTest.kt` | 重写分布相关用例 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt` | 新增 3 个 SQL 语义用例 |
| 10 | `src/test/js/monitoringDateDefault.test.js` | 窗口断言改写 + 新增 1 条 |
| 11 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键字符串同步 |

> **文件数 11 > 硬上限 10。** 见下方「文件数超限说明」。

### 文件数超限说明

第 11 项 `batchSendTaskConsoleVisualFix.test.js` 是**单点三行字符串替换**，由 I-9 的缓存键三元组机械带出，不含任何逻辑改动，不构成独立的验证面。第 7 项 `styles.css` 为 6 行纯追加。若严格按 10 文件拆分，唯一可行的切法是把「缓存键 bump」单独成一个计划，这会让本计划交付后前端拿到旧缓存 —— 属于人为制造缺陷。故本计划按 11 文件执行，并在此显式记录该超限及其理由。

**子系统数：2**（后端 monitoring 模块 / 前端静态资源），符合上限。
**每个共享存储新增字段数：0**（无 DDL，无新列，无迁移）。

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11）跑 Maven，裸 `mvn` 会构建失败。前端 JS 用例由 `exec-maven-plugin` 绑定在 `test` 阶段（`pom.xml:186-232`），也可用 `node --test` 单跑，无需 JAVA_HOME 前缀。

```bash
# 1) 全量回归门禁（含 Kotlin 单测 + node --test + node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 2) 本计划相关的 Kotlin 单测（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailMonitoringServiceTest

# 3) 本计划新增的 SQL 语义集成测试（需要 Docker，默认跳过，必须显式开启）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordRepositoryMonitoringIT -DmigrationIt=true

# 4) 本计划相关的前端用例（单跑）
node --test src/test/js/monitoringDateDefault.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# 5) 前端全量用例
node --test src/test/js/*.test.js

# 6) app.js 语法检查
node --check src/main/resources/static/app.js

# 7) 空白/换行卫生
git diff --check
```

**通过判据**

- 命令 1 / 2 / 3：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且命令 1 的输出中出现 `node --test` 的执行记录。
- 命令 3：若输出为 `Tests run: 0` 或用例被 skip，说明 `-DmigrationIt=true` 未生效或 Docker 不可用 —— 视为**未验证**，不得据此判定通过。
- 命令 4 / 5：退出码 0，输出含 `# fail 0`。
- 命令 6：退出码 0，无输出。
- 命令 7：无输出。

**来源**：命令 1/2/3 取自 `CLAUDE.md`「Commands」章节（`CLAUDE.md:11/14/17/24`）与项目元信息 `test_command`（`CLAUDE.md:144`）；命令 4/5/6 取自 K-js-tests-run-via-exec-plugin（`pom.xml:186-232` 实测）。

---

## 验收标准

### 不变量

- **I-1**：`grep -n "GROUP BY expert_contact_id" src/main/kotlin/.../MailRecordRepository.kt` 命中新增的两条 SQL 的内层子查询；全仓 `grep -rn "aggregateIntroSentBy\|aggregateInboundBy" src/` 结果为空。由「验证命令」命令 3 的 `intro cohort dedupes repeated introductions per expert` 断言 `cohortCount == 1`。
- **I-2**：命令 3 的 `intro cohort counts reply only when inbound is after the introduction` 通过；断言只有 A 被计入 `repliedCount`。
- **I-3**：`grep -rn "first_reply_at\|firstReplyAt" src/main/kotlin/.../MailRecordRepository.kt` 只命中新 SQL 中作为**子查询别名**的 `AS first_reply_at`，不得命中 `expert_contact` 的列引用（即不出现 `ec.first_reply_at`）。同一用例中 B（有 `first_reply_at` 无 INBOUND）未被计入。
- **I-4**：`grep -n "MATURITY_DAYS" src/main/kotlin/.../MailMonitoringService.kt` 命中常量定义与 `minusDays` 使用点各一处；`grep -n "INTERVAL 7 DAY" src/main/kotlin/.../MailRecordRepository.kt` 命中 2 处（两条 SQL 各一）。命令 3 的 `mature cohort excludes introductions newer than seven days` 通过。
- **I-5**：不需运行时断言；在 `MailRecordRepository` 新查询上方注释中写明该前提及其依据（`uq_orcid_mail_type` + `ManualInitialOutreachService.kt:734`），由 review 目视确认注释存在。
- **I-6**：`grep -n "send_status" src/main/kotlin/.../MailRecordRepository.kt` 的结果中，新增的两条 SQL 内不含 `send_status`。
- **I-7**：命令 2 的 `regionDistribution region totals equal sum of country rows` 用例，对四个计数逐字段断言 `region.xxx == countries.sumOf { it.xxx }`。
- **I-8**：`grep -n "monitoringWindowParams" src/main/resources/static/app.js` 至少命中 3 处（定义、`loadMonitoring`、`scheduleMonitoringAutoRefresh`）；且 `scheduleMonitoringAutoRefresh` 函数体内 `grep` 不再出现 `params.set("date"`。
- **I-9**：`grep -c "v=20260902-monitoring-window" src/main/resources/static/index.html` 结果为 3；命令 4 的 `batchSendTaskConsoleVisualFix.test.js` 通过。

### 样式契约

- **S-1**：`index.html` 中 `#monitoringRangeTabs` 的三个 button 的 class 逐字为 `tab button secondary`（选中项追加 ` active`）；`git diff styles.css` 中不含针对 `.tabs` / `.tab` / `.toolbar` 的任何改动；`#monitoringDate` / `#monitoringSenderAccount` / `#monitoringRefreshBtn` / `#monitoringLastRefreshed` 四行在 diff 中未被修改。
- **S-2**：`grep -n "badge warn\|badge(\"样本不足\"" src/main/resources/static/app.js` 命中样本不足渲染点；`git diff styles.css` 中不含任何以「样本」「low-sample」「insufficient」命名的新 class。
- **S-3**：`git diff src/main/resources/static/styles.css` 的新增内容与本计划 S-3 代码块**逐字一致**（仅这 6 行 + 空行，无第三条规则）；`grep -c "monitoring-region-child" src/main/resources/static/app.js` 为 1。
- **S-4**：`git diff src/main/resources/static/index.html` 中 `按收件方服务商分布` / `按专家地区分布` 两处 `.panel-head` 块未被修改（仍为 `<div class="panel-head"><h2>…</h2></div>`）。
- **全局**：`git diff src/main/resources/static/app.js | grep 'style="'` 的新增行中，不得出现本计划未在样式契约中声明的 inline style（`monitoringDistributionBar` 内既有的进度条 inline style 属既有代码，保留不算）。

### 回归

- 执行「验证命令」节的命令 1（全量回归），通过判据见该节。
- 执行「验证命令」节的命令 5、6、7。
- `git diff` 中 `MailMonitoringService.senderAccountHealth`、`listIntroductions`、`listOutboundReplies`、`listInbound`、`listPromotions`、`getBounceStats`、`reputationHistory` 七个方法体无改动（N-1 / N-2 / N-3）。
- `git diff` 中 `summary` 的 10 条 `countXxxBetween` 调用行只有变量名/窗口来源变化，SQL 与方法名未变（N-4）。

---

## 人工验收清单

### A-1: 窗口切换驱动三块面板（覆盖 O-1）

- 前置条件：库中近 30 天有 INTRODUCTION 发信记录，且至少两天有数据。
- 操作步骤：
  1. 登录后台，进入「邮件监控」。
  2. 观察工具栏，确认存在「今日 / 近 7 天 / 近 30 天」三个按钮，且「近 7 天」为选中态（蓝底白字）。
  3. 记下概览区「近 7 天介绍邮件」的数值 X7，以及服务商表 `gmail` 行的「队列(人)」值 G7。
  4. 点击「近 30 天」。
  5. 点击「今日」。
- 预期结果：
  - 步骤 3：概览面板标题为「近 7 天全链路指标概览」，卡片文案为「近 7 天介绍邮件」「近 7 天收到回复」等，**不出现「今日」字样**。
  - 步骤 4：标题变为「近 30 天全链路指标概览」，卡片文案前缀全部变为「近 30 天」；介绍邮件数 ≥ X7；`gmail` 行队列人数 ≥ G7；地区表与服务商表同时变化。
  - 步骤 5：标题变为「今日全链路指标概览」，卡片前缀为「今日」，数值 ≤ X7。
- 覆盖：O-1、I-8（部分）

### A-2: 自动刷新不把窗口打回当日（覆盖 I-8 / IP-2）

- 前置条件：同 A-1。
- 操作步骤：
  1. 进入「邮件监控」，点击「近 30 天」，记下「近 30 天介绍邮件」的值。
  2. **停留在该页面不做任何操作，等待 70 秒以上**（自动刷新周期为 60 秒）。
  3. 再次观察概览区。
- 预期结果：标题仍为「近 30 天全链路指标概览」，卡片文案前缀仍为「近 30 天」，数值与步骤 1 相同（除非期间真有新发信）。右上角「最近刷新」时间已更新。**不得**出现「标题写近 30 天、数字却是当日」的情况。
- 覆盖：I-8、IP-2

### A-3: 服务商表的队列口径与样本不足标记（覆盖 O-2、I-1、I-2、S-2）

- 前置条件：近 30 天内存在给同一位专家重复发送 INTRODUCTION 的记录（若无，可用 SQL 造：向 `mail_record` 插入一条 `direction='OUTBOUND', mail_type='INTRODUCTION', expert_contact_id=<已有队列内专家 id>, send_status='SENT', sent_at=<窗口内时间>`）。
- 操作步骤：
  1. 进入「邮件监控」，选「近 30 天」。
  2. 查看「按收件方服务商分布」表头与各行。
  3. 找一个「队列(人)」小于 30 且大于 0 的行（如 `yahoo`）。
- 预期结果：
  - 步骤 2：表头逐列为 `服务商 | 分布 | 队列(人) | 已回复(人) | 窗口内回复率 | 7日成熟回复率 | 硬退率 | 软退`。造数后该专家所属服务商桶的「队列(人)」**不增加**（去重）。
  - 步骤 3：该行「窗口内回复率」为灰色文字，其右侧紧跟一枚橙色圆角标签，文字为「样本不足」。队列为 0 的行（如 `tencent`）该列显示 `-`。
- 覆盖：O-2、I-1、I-2、S-2

### A-4: 地区表展开到国家且父子加总一致（覆盖 O-3、I-7、S-3）

- 前置条件：近 30 天欧洲有 ≥ 2 个国家的发信记录。
- 操作步骤：
  1. 进入「邮件监控」，选「近 30 天」。
  2. 记下「欧洲」行的「队列(人)」P 与「已回复(人)」Q。
  3. 点击「欧洲」行最左侧的 `▸` 按钮。
  4. 把展开出来的所有国家子行的「队列(人)」与「已回复(人)」分别相加。
  5. 再次点击该按钮。
- 预期结果：
  - 步骤 3：按钮字符变为 `▾`，其下方紧接着出现若干国家行，底色比普通行略深，国家名相对大区名有明显缩进；**页面不发起新的网络请求**（可在浏览器 Network 面板确认）。
  - 步骤 4：两个加总值分别**精确等于** P 和 Q。
  - 步骤 5：按钮变回 `▸`，国家行收起。
- 覆盖：O-3、I-7、S-3、IP-1

### A-5: 7 日成熟回复率的语义（覆盖 I-4）

- 前置条件：同 A-1。
- 操作步骤：
  1. 选「近 30 天」，记下地区表「欧洲」行的「窗口内回复率」R30 与「7日成熟回复率」M30。
  2. 切到「近 7 天」，记下同一行的两个值 R7、M7。
  3. 切到「今日」，看「7日成熟回复率」列。
- 预期结果：
  - 步骤 1：近 30 天下 R30 与 M30 接近（差值通常在数个百分点内）。
  - 步骤 2：R7 明显低于 M7（近 7 天的队列多数尚未满 7 天，未成熟）；M7 与 M30 数量级一致。
  - 步骤 3：该列所有行显示 `—`（当日队列不可能满 7 天），概览区「7 日成熟回复率」卡片同样显示 `—`。
- 覆盖：I-4

### A-6: 回信但未晋级的专家必须被计入回复（覆盖 I-3）

- 前置条件：用 SQL 找一位满足「窗口内有 INTRODUCTION、有晚于该 INTRODUCTION 的 INBOUND `mail_record`、但 `expert_contact.first_reply_at IS NULL`」的专家；若不存在则造一条 INBOUND 记录（`direction='INBOUND', mail_type='REPLY', expert_contact_id=<该专家>, received_at=<首发之后>`）并确保其 `first_reply_at` 保持 NULL。
- 操作步骤：
  1. 记下该专家的 `country` 与邮箱域名。
  2. 进入「邮件监控」，选覆盖其首发时间的窗口。
  3. 在地区表中展开其所属大区，找到其国家行；在服务商表中找到其域名对应的桶。
- 预期结果：造数前后，该国家行与该服务商行的「已回复(人)」各 **+1**，「窗口内回复率」相应上升。若该数字没有变化，说明代码错误地依赖了 `expert_contact.first_reply_at`。
- 覆盖：I-3

### A-7: 回归 —— 发件账号限额监控仍是「当日」（覆盖 N-1、IP-5）

- 前置条件：至少一个启用中的发件账号，当天有发信。
- 操作步骤：
  1. 进入「邮件监控」，选「今日」，记下「当前发件账号限额监控」表中某账号的「今日/上限」「介绍」「自动回复」「失败」四列值。
  2. 切到「近 30 天」，再看同一行。
- 预期结果：该表四列值在两个窗口下**完全相同**（该面板不跟随窗口）；表头仍为 `账号 | 邮箱 | 状态 | 今日/上限 | 介绍 | 自动回复 | 失败 | 最近发信 | 最近收信`。
- 覆盖：N-1、IP-5

### A-8: 回归 —— 域信誉趋势与下方明细列表（覆盖 N-2、N-3）

- 前置条件：`domain_reputation_history` 有数据；下方任一子标签有记录。
- 操作步骤：
  1. 进入「邮件监控」，观察「域信誉趋势」面板的域名下拉与「近 7 / 30 / 90 天」下拉。
  2. 把它切到「近 90 天」。
  3. 切换顶部窗口到「今日」，再看该面板。
  4. 点击下方「首发邮件」子标签，选顶部「近 30 天」，再点「退信名单」「漏斗晋级」。
- 预期结果：
  - 步骤 1-3：域信誉面板自己的天数下拉与顶部窗口**互不影响**，切窗口后它仍停在「近 90 天」，折线未变。
  - 步骤 4：「首发邮件」列表的列与分页与改动前一致，条数随窗口从今日扩到近 30 天而增加；「退信名单」与「未匹配邮件」两个子标签**不受**窗口影响（它们本就不传 from/to）。
- 覆盖：N-2、N-3

### A-9: 回归 —— 概览卡片口径提示与重叠说明（覆盖 N-4）

- 操作步骤：
  1. 进入「邮件监控」，把鼠标悬停在「近 7 天自动回复」「近 7 天会议邀约」「近 7 天人工待办新增」「近 7 天未匹配来信」四张卡片上。
- 预期结果：四张卡片仍显示 ⓘ 标记，悬停提示文案仍分别包含「会议邀约为自动回复的子项，细分统计不可相加」「属于自动回复子项，细分统计不可相加」「未匹配来信为人工待办子项，细分统计不可相加」「属于人工待办子项，细分统计不可相加」。
- 覆盖：N-4

### A-10: UI 目测 —— 窗口按钮与既有子标签视觉一致（覆盖 S-1、S-4）

- 操作步骤：
  1. 进入「邮件监控」，把顶部「今日/近 7 天/近 30 天」按钮与页面下方「首发邮件/收信分类/…」子标签并排对比（可缩放页面同屏显示）。
  2. 观察「按收件方服务商分布」与「按专家地区分布」两个面板的标题行。
  3. 鼠标悬停在未选中的窗口按钮上。
- 预期结果：
  - 步骤 1：两组按钮的高度、圆角、字号、未选中态（浅蓝底 `--primary-light`、蓝字 `#1e40af`）、选中态（实心蓝底 `#1e40af`、白字、加粗）**完全一致**。
  - 步骤 2：两个面板标题行**只有标题文字**，右侧无任何附加说明文字或控件，与「今日全链路指标概览」面板的标题行排布一致。
  - 步骤 3：按钮边框与文字变为主色 `#1e40af`，无位移抖动。
- 覆盖：S-1、S-4

---

## 已知限制（不在本计划内修复，明确记录）

- **L-1（性能）**：按域名的聚合使用 `GROUP BY SUBSTRING_INDEX(ec.expert_email, '@', -1)`，函数表达式无法走索引，近 30 天窗口下需对该窗口内的队列成员做一次排序聚合。队列规模受 `mail_sender_account.daily_send_limit` 约束而有界，故不预先优化。若上线后出现慢查询，后续方案是给 `expert_contact` 加 `email_domain` 冗余列或建每日聚合表 —— **本计划不做**。
- **L-2（国家取值）**：国家子行按 `expert_contact.country` 的**原始值**分组，同一国家的不同写法（如 `China` / `CN` / `Chinese`，见 `CountryContinentMapping.MAPPING` 的 key 集合）会各成一行。`toRegion()` 的小写归一只用于映射到大区，不改写展示值。归一化不在本计划范围。
- **L-3（人工绑定的来信不计回复）**：运营在「未匹配邮件」里手工把来信绑定到专家时，`UnmatchedInboundMailService` **不创建** INBOUND `mail_record`（全文无 `MailRecord(` 写入），因此这类回复不进分子。这与改动前 `aggregateInboundByCountry` 的行为一致，**不是本次引入的回归**。
- **L-4（自动回复/休假回执计入回复）**：`AutoMailReplyService` 只在 `processSingle` 之前拦截退信与 DMARC 报告，没有 Out-of-Office / `Auto-Submitted` 识别（全仓 grep `Auto-Submitted|OutOfOffice|Automatic reply` 无命中）。休假自动回执会落成 INBOUND/REPLY 并计入分子。同样与改动前行为一致。
- **L-5（自定义区间）**：窗口只有 1 / 7 / 30 三档 + 可改的锚点日，无任意起止日选择。

---

## Phase 4 自查

- [x] `关键不变量` 存在，9 条；每个新增语义（队列口径分母/分子、回复时间戳来源、成熟度、父子加总、缓存键）均有对应不变量
- [x] `现状审计` 列出 4 个 store 的全部相关写路径与读路径，均由 grep 取得并附 `file:line`
- [x] 无任务引入未被不变量覆盖的写路径（本计划**零写路径**，纯读 + 前端）
- [x] 含前端改动 → `样式契约` 存在，4 条；每个新增/修改 DOM 元素映射到 S-1 ~ S-4
- [x] 无「样式与现有一致 / 参考 XX / 保持风格」类表述；全部为 `file:line` 引用、token 实值或逐字代码块
- [x] 唯一新增 class `.monitoring-region-child` 的 CSS 在 S-3 中全文逐字给出
- [x] 无既有 class 被修改（S-1/S-2/S-4 均为纯复用），故无需列既有 class 的全部使用点
- [x] `验证命令` 存在且排在 `验收标准` 之前，7 条命令均含 `JAVA_HOME` 前缀或已实测无需前缀，注明来源与通过判据
- [x] 新增/改动的测试类（`MailMonitoringServiceTest`、`MailRecordRepositoryMonitoringIT`、两个 JS 文件）均有单跑命令行及确切过滤语法
- [x] `验收标准` 与 `人工验收清单` 中所有回归项均引用 `验证命令` 节，全文无裸 `mvn test`
- [x] `人工验收清单` 10 条；O-1/O-2/O-3 各有覆盖；N-1 ~ N-4 各有回归条目；IP-1/IP-2/IP-5 有跨路径场景；S-1/S-3/S-4 有 UI 目测条目
- [x] 每条 A-n 可黑盒执行，前置条件给出构造方式，预期结果为实值
- [ ] **文件数 11 > 10** — 已在「文件数超限说明」显式记录理由（第 11 项为 3 行字符串同步，拆分反而制造缺陷）
- [x] 子系统数 2
- [x] 每个任务按编号引用其不变量与样式契约
- [x] `验收标准` 对每条 I-n、S-n 均有检查
- [x] 文件清单无「相关文件」「等等」，逐个具名
- [x] `Out of scope` 显式排除了发信账号回复率面板、其他 9 个候选看板、自定义区间、country 归一化、性能优化
- [x] Phase 0 载入的知识均已使用或显式驳回：K-frontend-cache-key-triad（用，且发现部分过期）、K-panel-head-space-between-third-child（用于 S-4）、K-panel-bg-token-is-translucent（用于 S-3 暗色决策）、K-html-string-truncation-breaks-cells（用于 B-2.8）、K-js-test-invocation-surface + K-js-tests-run-via-exec-plugin（用于验证命令）、K-relocated-control-refresh-owner（用于 I-8）、K-distinct-contact-order-query（用于 I-1 的 GROUP BY 写法）、K-view-registration-triad（**驳回**：本计划不新增侧栏视图）、K-spring-data-jdbc-null-default（**驳回**：本计划无实体写入）、K-group-before-pagination（**驳回**：分布表不分页）
- [x] 计划已保存至 `docs/plans/2026-09-02/`
