# 邮件监控页问题修复计划

> 创建日期：2026-06-21
> 适用 skill：create-p（验收用 fix-v）
> 范围：`邮件监控` 页面（概览卡片 + 账号健康表 + 日期筛选）后端统计口径与前端默认行为修复

---

## 需求描述

**可观察结果**
1. `今日发送失败` 卡片与账号健康表「失败」列只统计真正发送失败的邮件（不再把成功发出的邮件算成失败，也不再漏掉真实失败）。
2. 邮件监控页打开时，日期筛选框默认显示并按「当天（Asia/Shanghai）」过滤，而不是空白。
3. 统计时间窗口与邮件时间戳使用同一时区，跨天数据归入正确日期。
4. `triggered_by` 取值被前后端正确识别；重叠口径的概览卡片在 UI 上明确标注为「细分、不可相加」。

**不可改变（must NOT change）**
- 成功邮件写入 `mail_record.send_status='SENT'`、失败写 `'FAILED'` 的既有约定（仅修读取侧统计 SQL，不改写入值）。
- `今日介绍邮件`、`今日收到回复`、`今日回复专家数` 等正常卡片的现有口径与数值。
- 各列表（introductions / inbound / outbound / promotions）分页、筛选 API 的入参与返回结构。
- `countSentByAccountSince`（已正确使用 `send_status='SENT'`）保持不变。
- 数据库已应用的迁移（V1–V30）一律不改，新增只能用 V31+。

**Out of scope（显式延后）**
- 把 `今日自动回复 / 今日会议邀约` 与 `今日人工待办新增 / 今日未匹配来信` 改成互斥（不可相加）口径——属产品口径决策，本计划仅做 UI 标注，不改计数 SQL。
- `今日人工待办新增` 是否应以「升级为人工的时刻」而非 `received_at` 计数——需新增状态时间列，单独立计划。
- 失败重试成功后晋级/邮件的归日逻辑。
- 全站其余使用 `LocalDateTime.now()` 的业务逻辑语义审查（本计划只统一 JVM 默认时区）。

---

## 关键不变量

### Invariant I-1：失败态的判定值
- 规则：`mail_record` 的发送结果仅有两种写入值——成功 `'SENT'`、失败 `'FAILED'`，**从不写 `'SUCCESS'`**。任何「失败」统计必须用 `send_status = 'FAILED'`（等价 `<> 'SENT'`），**禁止再比较 `<> 'SUCCESS'`**。
- 适用于：`MailRecordRepository.countFailedOutboundBetween`、`MailRecordRepository.aggregateSenderAccountStats` 的 `failed_count`。
- 违反后果：`'SENT'` 满足 `<>'SUCCESS'`，成功邮件被计为失败，失败数被严重高估。

### Invariant I-2：失败统计的时间列
- 规则：失败记录的 `sent_at` 可能为 `null`（手动批量路径 `ManualOutreachTxHelper:118`）或为 `now`（自动路径 `AutoMailReplyService:454/761`）。因此**失败类统计的时间窗口必须用 `created_at`**（所有路径写入时均赋值，见各 `createdAt = now`），禁止用 `sent_at`，否则手动路径真实失败会被时间窗口过滤掉。
- 适用于：`countFailedOutboundBetween` 的时间过滤；`aggregateSenderAccountStats` 的 `failed_count` 在按 `sent_at` 分组窗口内的语义（见任务 T2 说明）。
- 违反后果：真实失败漏报。

### Invariant I-3：日期筛选默认当天
- 规则：进入监控页时 `state.monitoring.date` 必须初始化为「当天（Asia/Shanghai）」字符串，`#monitoringDate` 输入框 value 同步显示该日期；后端 `summary`/列表在收到该 `date` 时按当天过滤。空值仅在用户主动清空时出现。
- 适用于：`app.js` 监控 state 初始化、`renderMonitoring*` 首次加载、`index.html` 日期输入框。
- 违反后果：用户看不到当前过滤的是哪一天，且前端 `monitoringRangeParams` 的 UTC 兜底日期与后端 Shanghai 兜底日期可能错位。

### Invariant I-4：时间戳与统计窗口同时区
- 规则：监控窗口由 `MonitoringDateRangeResolver`（`Asia/Shanghai`）计算；写入侧 `LocalDateTime.now()` 必须解析到同一时区。通过在启动时将 JVM 默认时区固定为 `Asia/Shanghai` 实现。
- 适用于：新增 `config/TimeZoneConfig`；不改各 `now()` 调用点。
- 违反后果：容器跑 UTC 时窗口与时间戳差 8 小时，跨天数据归错日期。

### Invariant I-5：triggered_by 取值集合
- 规则：`triggered_by` 合法取值 = `{SYSTEM, OPERATOR, MANUAL, null}`。枚举对象 `TriggeredBy` 必须声明 `MANUAL`；前端 `triggeredByLabels` 必须覆盖 `MANUAL`。`今日人工外发` 口径保持 `triggered_by='OPERATOR' OR IS NULL`，**不含 `MANUAL`**（`MANUAL` 为批量介绍邮件，已计入 `今日介绍邮件`，纳入人工外发会重复计数）。
- 适用于：`TriggeredBy.kt`、`app.js` 的 `triggeredByLabels`；`countOperatorOutboundBetween` 不变。
- 违反后果：列表出现未知 `triggered_by` 显示为空；或重复计数。

### Invariant I-6：重叠卡片为细分关系
- 规则：`今日自动回复 ⊇ 今日会议邀约`（系统会议邀约同时计入两者）、`今日人工待办新增 ⊇ 今日未匹配来信`（未匹配是人工待办子集）。这些卡片为有意的细分展示，**不可相加**，UI 必须以 tooltip/说明文字标注。
- 适用于：`index.html`/`app.js` 卡片渲染。
- 违反后果：用户误把细分项相加，得出错误总量。

---

## 现状审计

### Store：`mail_record`（MySQL）
- Schema：`direction`、`mail_type`、`triggered_by`、`send_status`、`sent_at`、`received_at`、`created_at`（`created_at` 各路径必填，见各 `createdAt = now`）。V15 已建索引 `idx_mail_record_dir_type_sent(direction,mail_type,sent_at)`、`idx_mail_record_sender_sent`、`idx_mail_record_triggered_sent`，**无 `created_at` 索引**。
- 写路径（send_status / sent_at）：
  1. `SmtpMailDeliveryService:59` — 成功 `status="SENT"`（DeliveredMail）。
  2. `SmtpErrorClassifier:*` — 失败 `status="FAILED"`。
  3. `ManualOutreachTxHelper:63/65` — 成功记录 `sendStatus="SENT", sentAt=now`（INTRODUCTION, triggered_by="MANUAL"）。
  4. `ManualOutreachTxHelper:108/115/118` — 失败记录 `triggered_by="MANUAL", sendStatus="FAILED", sentAt=null`。
  5. `AutoMailReplyService:444/452/454`、`751/759/761` — 系统自动回复，`triggeredBy=SYSTEM, sendStatus=delivered.status, sentAt=now`（失败时 status=FAILED 但 sentAt 仍为 now）。
  6. `AutoMailReplyService:170/178/180`、`556/564/566` — 占位/入站记录，`triggeredBy=null, sendStatus=null, sentAt=null`。
  7. `ManualExpertMailService:81/88/90`、`MeetingScheduleService:138/145/147`、`PendingMailOperationService:108/115/117 等` — `triggeredBy=OPERATOR/SYSTEM, sendStatus=delivered.status, sentAt=now`。
- 读路径（监控相关，均在 `MailRecordRepository`）：
  1. `countOutboundByMailTypeBetween`（INTRODUCTION / MEETING_INVITATION）— 按 `sent_at` 窗口。**正确，保持。**
  2. `countInboundBetween` / `countDistinctRepliedExpertsBetween` — 按 `received_at`。**正确，保持。**
  3. `countAutoRepliesBetween`（line 63）— `triggered_by='SYSTEM' AND mail_type IN(QA_REPLY,MEETING_INVITATION,MEETING_CONFIRMATION) AND sent_at` 窗口。**保持（I-6 仅 UI 标注）。**
  4. `countOperatorOutboundBetween`（line 73）— `triggered_by='OPERATOR' OR IS NULL`，`sent_at` 窗口。**保持（I-5）。**
  5. `countFailedOutboundBetween`（line 83）— **缺陷**：`send_status<>'SUCCESS' AND sent_at` 窗口 → 违反 I-1、I-2。
  6. `aggregateSenderAccountStats`（line 196，failed_count line 187）— **缺陷**：`SUM(CASE WHEN send_status IS NOT NULL AND send_status<>'SUCCESS' …)` → 违反 I-1。
  7. `countSentByAccountSince`（line 211）— `send_status='SENT'`。**已正确，作为 I-1 的反证。**
- Interaction points：写路径 1–7 的 `send_status/sent_at/created_at` × 读路径 5、6 是本计划的核心交叉点；尤其手动失败（sent_at=null）vs 自动失败（sent_at=now）必须被 created_at 统一覆盖。

### Store：`inbound_mail_processing`（MySQL）
- 读路径：`countManualReviewBetween`、`countUnmatchedBetween`、`listInboundActivity` 均按 `received_at` 窗口。本计划不改其计数（见 Out of scope / I-6），仅 UI 标注。

### Store：`expert_application_promotion`（MySQL）
- 读路径：`countByStatusAndCreatedAtBetween('SUCCESS', …)`。promotion 表的 `'SUCCESS'` 是该表正确值（`ExpertIndexWriterService:626`），**与 mail_record 无关，保持。**

### 前端（`static/app.js` + `static/index.html`）
- `state.monitoring.date` 初值 `null`（app.js ~L20）；`#monitoringDate` 无 value（index.html:177）。
- `loadMonitoring`（~L4497）：`date` 为 null 时不带 `date` 参数 → 后端按 Shanghai 当天兜底（卡片数值正确，但输入框空白）。
- `monitoringRangeParams`（~L4515）：`date` 为 null 时用 `new Date().toISOString().slice(0,10)`（**UTC** 当天）兜底 → 与后端 Shanghai 当天潜在错位。
- `triggeredByLabels`（L103）：仅 `SYSTEM/OPERATOR`，缺 `MANUAL`。
- 卡片渲染 `renderMonitoringCards`（~L4529）：10 张卡片，无重叠关系说明。

### 时区
- `MonitoringDateRangeResolver`：`ZoneId.of("Asia/Shanghai")`，但 `atStartOfDay()` 产出无偏移 `LocalDateTime`，与列里存的 `LocalDateTime.now()`（JVM 默认时区）比较。JVM 非 Shanghai 时错位（I-4）。

---

## 实现方案

### 阶段 A：后端统计口径修复（子系统 1）

**T1 — 修 `countFailedOutboundBetween`（I-1, I-2）**
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`
将查询改为：
```sql
SELECT COUNT(*) FROM mail_record
WHERE direction = 'OUTBOUND'
  AND send_status = 'FAILED'
  AND created_at >= :from AND created_at < :to
```
保持方法签名 `(from, to)` 不变（`MailMonitoringService.summary` 调用不变，传入的仍是当天 day 窗口）。

**T2 — 修 `aggregateSenderAccountStats` 的 failed_count（I-1）**
文件：同上。
将 `failed_count` 的 CASE 改为：
```sql
SUM(CASE WHEN send_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count
```
说明：该聚合整体 `WHERE … AND sent_at >= :from AND sent_at < :to` 用于「当日发送活动」分账号汇总；手动路径失败 `sent_at=null` 不进该窗口属可接受（账号健康表语义是「当日发出活动」，真实失败总量以 T1 的卡片为准）。**不**把该聚合的窗口列改成 created_at，以免 `introduction_count/auto_reply_count` 的「已发出」语义被污染。仅修 failed_count 的判定值。

**T3 — `TriggeredBy` 增加 MANUAL 常量（I-5）**
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/domain/TriggeredBy.kt`
增加 `const val MANUAL = "MANUAL"`。不改任何写入点（`ManualOutreachTxHelper` 现有字面量与之一致）。`countOperatorOutboundBetween` 维持 `OPERATOR OR NULL`，不纳入 MANUAL。

**T4 — 统一 JVM 时区为 Asia/Shanghai（I-4）**
文件（新增）：`src/main/kotlin/com/weibo/talentintroduction/config/TimeZoneConfig.kt`
`@Configuration`，`@PostConstruct` 内 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))`，使所有 `LocalDateTime.now()` 与 resolver 同时区。
风险提示：此为全局副作用；部署若原本期望 UTC 需在评审确认。已在「不可改变」中限定仅统一默认时区、不改各 now() 调用点。

**T5 — 失败统计 created_at 索引（性能，配合 I-2）**
文件（新增）：`src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql`
```sql
ALTER TABLE mail_record
    ADD INDEX idx_mail_record_status_created (direction, send_status, created_at);
```
不修改任何已应用迁移。

### 阶段 B：前端默认与标注（子系统 2）

**T6 — 日期默认当天（I-3）**
文件：`src/main/resources/static/app.js`、`src/main/resources/static/index.html`
- app.js：新增 `monitoringToday()` 返回 Asia/Shanghai 当天 `YYYY-MM-DD`（用 `Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai'})` 或等价方式，避免 UTC 偏移）。
- 监控 state 初始化 `date` 改为 `monitoringToday()`（或在首次进入监控视图时若为空则赋值）。
- 首次渲染/进入视图时把 `#monitoringDate.value` 设为该日期。
- `monitoringRangeParams` 的兜底由 `new Date().toISOString().slice(0,10)`（UTC）改为 `monitoringToday()`，与后端一致。
- index.html：`#monitoringDate` 由 JS 初始化 value（保持 `<input type="date">`，不写死静态值）。

**T7 — `MANUAL` 标签（I-5）**
文件：`src/main/resources/static/app.js`
`triggeredByLabels` 增加 `MANUAL: "批量发送"`（与现有「自动/人工」并列）。

**T8 — 重叠卡片 UI 标注（I-6）**
文件：`src/main/resources/static/index.html` + `src/main/resources/static/app.js`
- 在 `今日自动回复`、`今日会议邀约`、`今日人工待办新增`、`今日未匹配来信` 四张卡片加 `title`/说明：标注「会议邀约为自动回复的子项」「未匹配为人工待办的子项」，提示不可相加。
- 不改任何计数 SQL。

### 阶段 C：测试（随所属子系统）

**T9 — 后端仓储集成测试（验证 I-1, I-2）**
文件（新增）：`src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt`
- 复用现有 Testcontainers/MySQL 模式（参照 `FlywayMigrationIntegrationTest`，`@EnabledIfSystemProperty(named="migrationIt", matches="true")`）。
- 造数：成功 INTRODUCTION（SENT, sent_at=今天）、手动失败（FAILED, sent_at=null, created_at=今天）、自动失败（FAILED, sent_at=今天）。
- 断言：`countFailedOutboundBetween(今天窗口)==2`（两条 FAILED，含 sent_at=null 那条），成功不计入。
- 断言：`aggregateSenderAccountStats` 中对应账号 `failedCount` 只数 FAILED。

**T10 — 前端日期默认测试（验证 I-3）**
文件（新增）：`src/test/js/monitoringDateDefault.test.js`
- jsdom 加载相关逻辑，断言初始化后 `state.monitoring.date` 与 `#monitoringDate.value` 等于 Asia/Shanghai 当天，且 `monitoringRangeParams` 不再用 UTC 兜底。

---

## 变更文件清单

| # | 文件 | 类型 | 涉及不变量 |
|---|------|------|-----------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 改 | I-1, I-2 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/TriggeredBy.kt` | 改 | I-5 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/TimeZoneConfig.kt` | 新增 | I-4 |
| 4 | `src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql` | 新增 | I-2 |
| 5 | `src/main/resources/static/app.js` | 改 | I-3, I-5, I-6 |
| 6 | `src/main/resources/static/index.html` | 改 | I-3, I-6 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt` | 新增 | I-1, I-2 |
| 8 | `src/test/js/monitoringDateDefault.test.js` | 新增 | I-3 |

文件数：8（≤10）。子系统：后端(1–4,7) + 前端(5,6,8)，共 2（≤2）。无新增共享数据字段。

---

## 验收标准

- **I-1**：`countFailedOutboundBetween`、`aggregateSenderAccountStats` 源码不再出现 `'SUCCESS'`；集成测试中成功(SENT)记录不计入失败数。
- **I-2**：集成测试中 `sent_at=null` 的 FAILED 记录被 `countFailedOutboundBetween` 计入（证明改用 created_at 窗口）。
- **I-3**：前端测试断言初始 `state.monitoring.date`、`#monitoringDate.value`、`monitoringRangeParams().get('from')` 三者均为 Asia/Shanghai 当天且一致。
- **I-4**：`TimeZoneConfig` 启动后 `TimeZone.getDefault().id == "Asia/Shanghai"`（可加轻量断言/启动日志核对）。
- **I-5**：`TriggeredBy.MANUAL` 存在；前端列表渲染 `MANUAL` 显示「批量发送」非空白；`countOperatorOutboundBetween` SQL 未变（仍 `OPERATOR OR NULL`）。
- **I-6**：四张重叠卡片在 DOM 中带说明 `title`/文案；计数 SQL 未变更（diff 核对）。
- **集成场景**：构造「同一天 1 封成功 INTRODUCTION + 1 封手动失败 + 1 封自动失败」，`今日发送失败` 卡片 = 2、`今日介绍邮件` = 1，二者互不串扰。

---

## 自检清单

- [x] 关键不变量含每个新增项（MANUAL 常量→I-5；created_at 窗口→I-2；时区→I-4；日期默认→I-3）的不变量
- [x] 现状审计经 grep 列全 `send_status/sent_at` 写路径（1–7）
- [x] 无未被不变量覆盖的新写路径（本计划不新增写路径）
- [x] 文件数 8 ≤ 10
- [x] 子系统 2 ≤ 2
- [x] 每个任务引用其治理不变量编号
- [x] 每条不变量有 ≥1 验收检查
- [x] 文件清单逐一命名，无「等」「相关文件」
- [x] Out of scope 显式延后口径互斥化、人工待办时间列、重试归日、全站 now() 审查
