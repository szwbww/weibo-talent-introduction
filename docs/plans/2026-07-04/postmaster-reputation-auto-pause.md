# Postmaster 投诉率联动自动暂停

## 需求描述

**可观察结果**：系统每天自动从 Google Postmaster Tools API v2 拉取各发件域名的投诉率（spam rate）和域信誉（domain reputation），超阈值时自动暂停该域名下的所有发件账号（`autoSendPaused=true`），投诉率连续达标后自动恢复。管理后台监控页展示信誉趋势图表。

**不能改变的行为**：
- 现有 `autoSendPaused` 暂停/恢复机制（BounceRateMonitorService、SelfCheckService、DAILY_LIMIT）不受影响
- `resumeAutoSend` 的通用入口保持不变
- `resumeDailyLimitPausedAccounts` 只恢复 `DAILY_LIMIT%` 前缀，不影响新前缀
- 发送流程（isSendable、selectAccount 等）无需修改，已通过 `autoSendPaused` 字段过滤

**不做**：
- Microsoft SNDS/JMRP API 集成
- 自动调整 `dailySendLimit` 或 `batchSend.runtimeStatus`
- DMARC 报告解析
- FBL 邮件自动抑制（已在 external-reputation-monitoring 计划中标记为 Task C 不做）
- IP reputation 自动暂停（v2 API 不再返回 IP reputation，参见 Google 迁移文档）

## 关键不变量

### Invariant I-1: REPUTATION 前缀隔离
- Rule: Postmaster 信誉暂停使用 `REPUTATION:` 前缀（如 `REPUTATION:spam_rate=6.9%`）。`resumeDailyLimitPausedAccounts` 的 `LIKE 'DAILY_LIMIT%'` 条件不会误恢复信誉暂停。信誉恢复只通过新建的 `resumeReputationPausedAccounts()` 方法或运营手动 `resumeAutoSend()`。
- Applies to: `ReputationAutoPauseService.pauseByDomain()`, `ReputationAutoPauseService.resumeByDomain()`, `MailSenderAccountRepository.resumeDailyLimitPausedAccounts()`
- Violation consequence: 每日重置任务误恢复被投诉暂停的账号，导致继续发送并恶化投诉率
- 来源: original（参考 K-operator-send-quota-paths 中 pauseAutoSend 的 reason 前缀模式）

### Invariant I-2: 域名级粒度
- Rule: 暂停/恢复粒度为发件域名（从 `senderEmail` 提取 `@` 后部分）。同域名下所有 `enabled=true` 账号同时暂停/恢复，不同域名独立判断。
- Applies to: `ReputationAutoPauseService.checkAndAct()`, `MailSenderAccountRepository` 新增的按域名批量暂停/恢复查询
- Violation consequence: 部分账号继续发送导致投诉率不降，或误暂停不相关域名的账号
- 来源: original

### Invariant I-3: 采集与决策分离
- Rule: `PostmasterDataCollector` 只负责拉取 API 数据并写入 `domain_reputation_history`，不做任何暂停/恢复决策。`ReputationAutoPauseService` 只读取 `domain_reputation_history` 做决策，不调用外部 API。两者通过数据库解耦，便于独立测试和故障隔离。
- Applies to: `PostmasterDataCollector`, `ReputationAutoPauseService`
- Violation consequence: 采集失败导致误暂停，或决策逻辑绑定外部 API 导致无法单测
- 来源: original

### Invariant I-4: 恢复需连续达标
- Rule: 自动恢复条件为投诉率**连续 N 天**（默认 3 天）低于恢复阈值（默认 0.1%）。不是"最近一天低于阈值就恢复"。查询 `domain_reputation_history` 最近 N 天的记录，全部 `spam_rate < resumeThreshold` 才恢复。
- Applies to: `ReputationAutoPauseService.shouldResume()`
- Violation consequence: 投诉率刚降一天就恢复发送，引起投诉率反弹
- 来源: original

### Invariant I-5: 功能默认关闭
- Rule: `PostmasterProperties.enabled` 默认 `false`。未配置时不创建 `PostmasterDataCollector` 和 `ReputationAutoPauseService` Bean，不注册定时任务，对现有系统零影响。
- Applies to: `PostmasterAutoConfig`, `PostmasterProperties`
- Violation consequence: 未配置 Google API 凭证的部署环境启动失败
- 来源: original（参考 scheduling.enabled 的 ConditionalOnProperty 模式）

## 现状审计

### mail_sender_account（已有表）

- Schema: `auto_send_paused` TINYINT(1) DEFAULT 0, `auto_send_paused_reason` VARCHAR(500), `auto_send_paused_at` DATETIME（V28 迁移添加）
- Write paths (autoSendPaused 相关):
  1. `MailSenderAccountRepository.pauseAutoSend(accountCode, reason, pausedAt)` — 按 accountCode 设置暂停
  2. `MailSenderAccountRepository.resumeAutoSend(accountCode)` — 按 accountCode 恢复
  3. `MailSenderAccountRepository.resumeDailyLimitPausedAccounts()` — 批量恢复 `DAILY_LIMIT%` 前缀
  4. `MailSenderAccountService.pauseAutoSend(accountCode, reason)` — 委托 repository
  5. `MailSenderAccountService.resumeAutoSend(accountCode)` — 委托 repository + invalidate selfCheck cache
  6. `SenderAccountSelfCheckService.runProbe()` — 失败时调用 `repository.pauseAutoSend()`
  7. `BounceRateMonitorService.checkAndPause()` — 超阈值调用 `mailSenderAccountService.pauseAutoSend()`
  8. `ManualInitialOutreachService` — SMTP 临时错误时调用 `mailSenderAccountService.pauseAutoSend()`
- Read paths (autoSendPaused 相关):
  1. `MailSenderAccountService.isSendable()` — `!account.autoSendPaused` 过滤
  2. `MailSenderAccountService.isManualSendable()` — `!account.autoSendPaused` 过滤
  3. `MailSenderAccountService.remainingDailyCapacity()` — `!it.autoSendPaused` 过滤
  4. `MailSenderAccountService.warmupActiveCount()` — `!it.autoSendPaused` 过滤
  5. `MailSenderAccountService.todayTotalCapacity()` — `!it.autoSendPaused` 过滤
  6. `SenderAccountAssignmentService.selectAccount()` — `!it.autoSendPaused` 过滤
  7. `SenderWarmupService.dailyState()` — `autoSendPaused` 时返回 `PAUSED_FAULT`
  8. `MailSenderAccountController.toResponse()` — 透传到前端
- Interaction points: **本计划不新增 `mail_sender_account` 字段**，只通过已有 `pauseAutoSend/resumeAutoSend` 写入，read paths 全部自动生效。与 `resumeDailyLimitPausedAccounts()` 的 `LIKE 'DAILY_LIMIT%'` 不冲突（I-1）。

### domain_reputation_history（新表）

- Schema: 见实现方案 Task 1
- Write paths:
  1. `PostmasterDataCollector.collect()` — 新建，每日写入一行/域名
- Read paths:
  1. `ReputationAutoPauseService.checkAndAct()` — 读最近 N 天记录判断暂停/恢复
  2. `MailMonitoringController` (新端点) — 读历史记录返回前端趋势图
- Interaction points: 采集写入 → 决策服务读取 → 调用已有 `pauseAutoSend()`。采集与决策通过表解耦（I-3）。

### MailAutomationScheduler（已有调度器）

- Write paths: 无直接数据库写入，委派给各 service
- Read paths: 读 `MailSchedulingProperties` 的 cron 表达式
- Interaction points: 本计划新增一个定时方法 `schedulePostmasterCollection()`，复用 `taskExecutionService.runAndRecord()` 模式。需要在 `MailSchedulingProperties` 新增 cron 字段，并在 `application.yml` 暴露环境变量。但考虑 I-5（功能默认关闭），**改用独立的 `PostmasterScheduler`** 类，用 `@ConditionalOnProperty(postmaster.enabled)` 控制，避免污染现有调度器。

## 实现方案

### Phase A: 数据层 + 采集服务

#### Task 1: Flyway 迁移 + domain 实体（I-3）
- 新建 `src/main/resources/db/migration/V60__create_domain_reputation_history.sql`
- 建表 `domain_reputation_history`：id, domain, report_date, spam_rate, domain_reputation, spf_success_rate, dkim_success_rate, dmarc_success_rate, raw_json, collected_at
- UNIQUE KEY `uk_domain_date` ON (domain, report_date) 防重复采集
- 新建 `src/main/kotlin/.../postmaster/domain/DomainReputationHistory.kt` — `@Table` data class
- 新建 `src/main/kotlin/.../postmaster/repository/DomainReputationHistoryRepository.kt` — `CrudRepository` + 自定义查询

**文件**: `V60__create_domain_reputation_history.sql`, `DomainReputationHistory.kt`, `DomainReputationHistoryRepository.kt`

#### Task 2: 配置类 + Maven 依赖（I-5）
- `pom.xml` 新增依赖 `google-api-services-gmailpostmastertools` (v2) + `google-auth-library-oauth2-http`
- 新建 `src/main/kotlin/.../config/PostmasterProperties.kt` — `@ConfigurationProperties(prefix = "talent-introduction.postmaster")`：
  - `enabled: Boolean = false`
  - `credentialsJson: String = ""` (Service Account JSON 文件路径)
  - `domains: List<String> = emptyList()`
  - `cron: String = "0 0 8 * * *"`
  - `pauseThresholdSpamRate: Double = 0.003` (0.3%)
  - `resumeThresholdSpamRate: Double = 0.001` (0.1%)
  - `resumeConsecutiveDays: Int = 3`
- 在 `RestTemplateConfig.kt` 的 `@EnableConfigurationProperties` 列表中添加 `PostmasterProperties::class`
- `application.yml` 添加对应配置节

**文件**: `PostmasterProperties.kt`, `RestTemplateConfig.kt`, `application.yml`, `pom.xml`

#### Task 3: PostmasterDataCollector（I-3, I-5）
- 新建 `src/main/kotlin/.../postmaster/service/PostmasterDataCollector.kt`
- `@Service` + `@ConditionalOnProperty("talent-introduction.postmaster.enabled", havingValue = "true")`
- 构造函数注入 `PostmasterProperties`, `DomainReputationHistoryRepository`
- `fun collect(date: LocalDate = LocalDate.now().minusDays(1))`: 遍历配置的 domains，调用 `PostmasterTools.domains().domainStats().query()` 拉取 `date` 的统计数据，解析 spam_rate/domain_reputation/spf/dkim/dmarc，upsert 到 `domain_reputation_history`
- 内部初始化 `PostmasterTools` 客户端（`GoogleCredentials.fromStream()` → `HttpCredentialsAdapter` → `PostmasterTools.Builder`）
- 异常 best-effort：单域名失败不阻断其他域名，log.warn

**文件**: `PostmasterDataCollector.kt`

### Phase B: 决策服务 + 定时调度

#### Task 4: ReputationAutoPauseService（I-1, I-2, I-4, I-5）
- 新建 `src/main/kotlin/.../postmaster/service/ReputationAutoPauseService.kt`
- `@Service` + `@ConditionalOnProperty("talent-introduction.postmaster.enabled", havingValue = "true")`
- 构造函数注入 `PostmasterProperties`, `DomainReputationHistoryRepository`, `MailSenderAccountService`, `MailSenderAccountRepository`
- `fun checkAndAct()`: 
  1. 遍历配置的 domains
  2. 读取最新一条 `domain_reputation_history` 记录
  3. 如果 `spam_rate >= pauseThreshold` 或 `domain_reputation == "LOW"/"BAD"` → 按域名查找所有 `enabled=true && autoSendPaused=false` 的账号，逐个调用 `mailSenderAccountService.pauseAutoSend(accountCode, "REPUTATION:spam_rate=X%")` (I-1)
  4. 如果已暂停（`autoSendPausedReason LIKE 'REPUTATION:%'`），检查是否满足恢复条件：读最近 `resumeConsecutiveDays` 天的记录，全部 `spam_rate < resumeThreshold` → 逐个调用 `mailSenderAccountService.resumeAutoSend(accountCode)` (I-4)
- 域名提取辅助方法 `extractDomain(senderEmail: String): String` → `@` 后部分 (I-2)

**文件**: `ReputationAutoPauseService.kt`

#### Task 5: PostmasterScheduler（I-5）
- 新建 `src/main/kotlin/.../postmaster/service/PostmasterScheduler.kt`
- `@Service` + `@ConditionalOnProperty("talent-introduction.postmaster.enabled", havingValue = "true")`
- 构造函数注入 `PostmasterDataCollector`, `ReputationAutoPauseService`, `TaskExecutionService`
- `@Scheduled(cron = "\${talent-introduction.postmaster.cron}")` `fun runDaily()`:
  ```
  taskExecutionService.runAndRecord("POSTMASTER_REPUTATION", "SCHEDULED", "postmaster-daily") {
      collector.collect()
      autoPauseService.checkAndAct()
  }
  ```
- 独立于 `MailAutomationScheduler`，避免在未启用 postmaster 时加载（I-5）

**文件**: `PostmasterScheduler.kt`

### Phase C: API + 前端

#### Task 6: 监控 API 端点
- 在 `MailMonitoringController` 新增 `GET /api/mail-monitoring/reputation-history?domain=&days=30`
  - 返回 `List<ReputationHistoryRow>`: date, spamRate, domainReputation, spfSuccessRate, dkimSuccessRate, dmarcSuccessRate
  - 读 `DomainReputationHistoryRepository.findByDomainOrderByReportDateDesc(domain, limit)`
  - 同时返回可选的 `domains` 列表: `DomainReputationHistoryRepository.findDistinctDomains()`

**文件**: `MailMonitoringController.kt`（已有文件，新增端点）

#### Task 7: 前端信誉趋势面板
- 在 `app.js` 监控页新增"域信誉"子面板
- 调用 `/api/mail-monitoring/reputation-history` 渲染投诉率折线图（含 0.3% 暂停线 + 0.1% 恢复线）
- 显示域名选择器、当前信誉状态、最近暂停/恢复事件

**文件**: `app.js`（已有文件，新增渲染逻辑）

### Phase D: 测试

#### Task 8: 单元测试
- `PostmasterDataCollectorTest.kt` — mock PostmasterTools 客户端，验证数据解析与落库
- `ReputationAutoPauseServiceTest.kt` — 验证暂停/恢复逻辑：
  - 投诉率超阈值 → 暂停对应域名所有账号
  - 连续 3 天低于恢复阈值 → 恢复
  - 不足 3 天 → 不恢复
  - REPUTATION: 前缀隔离（不影响 DAILY_LIMIT/SELF_CHECK_FAILED 暂停的账号）
  - 不同域名独立判断

**文件**: `PostmasterDataCollectorTest.kt`, `ReputationAutoPauseServiceTest.kt`

## 变更文件清单

| # | 文件 | 变更类型 | 备注 |
|---|------|---------|------|
| 1 | `src/main/resources/db/migration/V60__create_domain_reputation_history.sql` | 新建 | 建表 |
| 2 | `src/main/kotlin/.../postmaster/domain/DomainReputationHistory.kt` | 新建 | 实体 |
| 3 | `src/main/kotlin/.../postmaster/repository/DomainReputationHistoryRepository.kt` | 新建 | Repository |
| 4 | `src/main/kotlin/.../config/PostmasterProperties.kt` | 新建 | 配置类 |
| 5 | `src/main/kotlin/.../postmaster/service/PostmasterDataCollector.kt` | 新建 | 采集服务 |
| 6 | `src/main/kotlin/.../postmaster/service/ReputationAutoPauseService.kt` | 新建 | 决策服务 |
| 7 | `src/main/kotlin/.../postmaster/service/PostmasterScheduler.kt` | 新建 | 定时调度 |
| 8 | `src/main/kotlin/.../monitoring/controller/MailMonitoringController.kt` | 修改 | 新增端点 |
| 9 | `src/main/resources/static/app.js` | 修改 | 前端面板 |
| 10 | `pom.xml` + `application.yml` + `RestTemplateConfig.kt` | 修改 | 依赖 + 配置 |

测试文件（不计入 10 文件限制）：`PostmasterDataCollectorTest.kt`, `ReputationAutoPauseServiceTest.kt`

## 验收标准

- **I-1 (REPUTATION 前缀隔离)**:
  - 验证 `ReputationAutoPauseService` 暂停时 reason 以 `REPUTATION:` 开头
  - 验证 `resumeDailyLimitPausedAccounts()` 不会恢复 `REPUTATION:` 前缀的暂停
  - 单测中同时存在 `DAILY_LIMIT` 和 `REPUTATION:` 暂停账号，调用每日重置后只恢复前者

- **I-2 (域名级粒度)**:
  - 配置 domains = `["talents.szwebotech.cn", "mail.szwebotech.cn"]`
  - 一个域名投诉率超标 → 只暂停该域名下的账号
  - 另一个域名不受影响

- **I-3 (采集与决策分离)**:
  - `PostmasterDataCollector` 单测不依赖 `ReputationAutoPauseService`
  - `ReputationAutoPauseService` 单测不依赖 Google API，只 mock repository 数据

- **I-4 (恢复需连续达标)**:
  - 只有 1 天低于阈值 → 不恢复
  - 连续 3 天低于阈值 → 恢复
  - 中间有一天超标 → 重新计算

- **I-5 (功能默认关闭)**:
  - `postmaster.enabled` 默认 false，现有测试全部通过无影响
  - 不设置 `POSTMASTER_ENABLED=true` 时不创建相关 Bean

- **集成场景**:
  - `GET /api/mail-monitoring/reputation-history?domain=talents.szwebotech.cn&days=7` 返回有数据
  - 前端监控页信誉趋势图渲染正确

## 前置条件（运营/基础设施）

1. 在 [Google Cloud Console](https://console.cloud.google.com/) 创建项目并启用 Gmail Postmaster Tools API
2. 创建 Service Account，下载 JSON 凭证文件
3. 在 [Postmaster Tools](https://postmaster.google.com) 中将 Service Account 邮箱添加为对应域名的查看者
4. 部署时将 JSON 文件放到服务器，通过 `POSTMASTER_CREDENTIALS_JSON` 环境变量指定路径
5. 设置 `POSTMASTER_ENABLED=true` 和 `POSTMASTER_DOMAINS=talents.szwebotech.cn,mail.szwebotech.cn`

## 修正记录

| 日期 | 修正 | 理由 | 来源 |
|---|---|---|---|
| 2026-07-04 | Postmaster Tools API v2 已移除 Domain/IP Reputation 指标；自动暂停仅依据 `spam_rate >= pauseThreshold`；`domain_reputation_history.domain_reputation` 列保留但采集恒为 null；删除 `domain_reputation == LOW/BAD` 暂停分支及前端域信誉展示验收 | v2 `StandardMetric` 枚举无 DOMAIN_REPUTATION，Google 官方迁移文档确认该指标已退役 | fix-1 |
