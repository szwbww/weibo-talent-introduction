# 03A · 硬退率硬暂停改为软告警（后端）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `execute-p` to implement this approved plan, then use `fix-v` for independent verification. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保留现有“近 7 天、至少 20 封、硬退率 > 5%”判据，但命中后只产生告警信号与 WARN 日志，不再写账号暂停；部署时仅解除历史 `BOUNCE_RATE_HIGH:` 暂停。

**Architecture:** 继续复用 `BounceRecordRepository.countHardBouncesSince` 与 `MailRecordRepository.countSentByAccountSince`，不新增统计 SQL。`BounceRateMonitorService` 拆出纯计算方法供账号 API 读取布尔告警，同时保留 IMAP 轮询后的日志检查；旧暂停数据由单一、前缀限定的 Flyway 数据迁移清除。

**Tech Stack:** Kotlin、Spring Boot 2.7、Spring Data JDBC、Flyway、JUnit 5、Mockito、MySQL 8。

**Spec:** 需求方 2026-09-03 决策：“不要把这个门槛做成硬门槛，只提示硬退率过高”。本计划与 `03b-bounce-rate-soft-warning-frontend.md` 共同取代 `01-bounce-gate-run-cohort.md`；旧计划不得执行。

**Dependency:** 无。03B 依赖本计划产出的 `MailSenderAccountResponse.hardBounceRateHigh`。

## Global Constraints

- 必须使用 JDK 11：`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
- 不实现上一 run cohort 统计，不新增 `stopReason`，不修改 `ManualInitialOutreachService`。
- 不修改 `DEFAULT_WINDOW_DAYS = 7`、`DEFAULT_THRESHOLD = 0.05`、`MIN_SAMPLE_SIZE = 20`。
- 不改变 HARD-only 口径；HARD/SOFT 分类准确性另案处理。
- `auto_send_paused` 通用机制继续服务 `SELF_CHECK_FAILED:`、`SMTP_TRANSIENT:`、`SMTP_INFRA:`、`REPUTATION:` 等故障。

---

## 需求描述

### Observable outcome

1. 账号近 7 天已发送量不少于 20 且硬退率严格大于 5% 时，`GET /api/mail/sender-accounts` 返回 `hardBounceRateHigh=true`；否则返回 `false`。
2. 同一判据命中时，IMAP 自动收信轮询只写 WARN 日志，不调用任何暂停写路径，账号仍可被自动发送选号逻辑选中。
3. 部署迁移后，历史 `auto_send_paused_reason` 以 `BOUNCE_RATE_HIGH:` 开头的暂停被解除；其他暂停原因原样保留。

### What must NOT change

1. 判据保持：窗口 7 天、最小样本 20、只数 `bounce_type='HARD'`、比较符为 `>`，恰好 5% 不告警。
2. `/api/mail-monitoring/bounce-stats` 的字段及统计口径不变。
3. `SELF_CHECK_FAILED:`、`SMTP_TRANSIENT:`、`SMTP_INFRA:`、`REPUTATION:` 的暂停与恢复行为不变。
4. 手工“恢复发送”端点与 `DAILY_LIMIT%` 每日恢复行为不变。
5. `BounceCollectionService` 的采集、去重、HARD/SOFT 分类及 `EMAIL_INVALID` 写回不变。
6. 自动发送、绑定账号发送、预热容量对 `autoSendPaused=true` 的统一过滤不变。

### Out of scope

- 不实现 `01-bounce-gate-run-cohort.md` 中的上一 run cohort 门禁、查询、阈值或失败终态。
- 不修复退信 MIME 解析和 HARD/SOFT 误分类。
- 不调整阈值，不做 per-account/per-config 配置化。
- 不增加告警历史表、通知中心、邮件/短信告警、自动恢复策略。
- 不改监控页、批量任务页、账号编辑弹窗；账号列表 UI 由 03B 单独完成。
- 不做批量聚合查询优化；账号管理接口沿用现有两条计数 SQL逐账号计算，避免为本修复新增 repository 投影和 SQL。

## 关键不变量

### Invariant I-1: 硬退率只产生告警，不再写暂停

- Rule: `BounceRateMonitorService` 命中 `rate > threshold` 后只能记录 WARN 并返回计算结果；类中不得依赖 `MailSenderAccountService`，不得调用 `pauseAutoSend`、`resumeAutoSend` 或 `MailSenderAccountRepository`。
- Applies to: `BounceRateMonitorService.checkAndWarn`、`AutoMailReplyService.receiveAndAutoReply`。
- Violation consequence: 账号再次进入 `auto_send_paused=1`，自动选号与绑定发送继续被硬拦截，直接违背需求。
- 来源: original；并消除 `K-bounce-rate-monitor-denominator-starvation` 记录的“暂停导致分母饿死”。

### Invariant I-2: 判据逐字保持

- Rule: `since = LocalDateTime.now().minusDays(7)`；分子仍调用 `countHardBouncesSince`；分母仍调用 `countSentByAccountSince`；`sentCount < 20` 返回 `-1.0`；告警条件仍是 `rate > 0.05`，不是 `>=`。
- Applies to: `calculateHardBounceRate`、`isHardBounceRateHigh`、`checkAndWarn`。
- Violation consequence: 同一份线上数据在改造前后产生不同告警，修复被扩大为口径变更。
- 来源: `K-bounce-rate-monitor-denominator-starvation`。

### Invariant I-3: API 告警值由服务端单源判断

- Rule: `MailSenderAccountResponse` 只新增一个非空布尔字段 `hardBounceRateHigh`；Controller 必须调用 `BounceRateMonitorService.isHardBounceRateHigh(accountCode)`，前端不得自行复制 7/20/5% 判据。
- Applies to: `MailSenderAccountController.listAccounts/getAccount/create/update/enable/disable/reset/resume` 的统一 `toResponse` 路径。
- Violation consequence: 不同响应入口或前端阈值漂移，出现同一账号一处告警、一处不告警。
- 来源: original。

### Invariant I-4: 历史清理只匹配 BOUNCE_RATE_HIGH 前缀

- Rule: V117 仅更新 `auto_send_paused = 1 AND auto_send_paused_reason LIKE 'BOUNCE_RATE_HIGH:%'` 的行，将暂停布尔、原因、时间三列同时清空；不得匹配其他前缀，不得修改 `enabled`。
- Applies to: `V117__convert_bounce_rate_pause_to_warning.sql`。
- Violation consequence: 自检、SMTP、Postmaster 等真实故障账号被误恢复并重新进入自动发送。
- 来源: `K-auto-send-paused-reason-prefix-routing`。

### Invariant I-5: 通用暂停消费者保持不变

- Rule: 不修改 `MailSenderAccountService.isSendable/listSendableAccounts/remainingDailyCapacity/todayTotalCapacity`、`SenderAccountAssignmentService.selectAccount`、`SenderWarmupService.dailyState`、`SenderAccountBindingService.requireAvailable` 对 `autoSendPaused` 的判断。
- Applies to: 所有自动发送选号、容量、绑定账号可用性读路径。
- Violation consequence: 为放行硬退率告警而整体削弱故障暂停，造成 SMTP 或声誉故障账号继续发信。
- 来源: `K-auto-send-paused-reason-prefix-routing`；代码 grep 复核。

### Invariant I-6: 退信采集事实不变

- Rule: 本计划不改 `bounce_record` schema、`BounceCollectionService.ingest`、`BounceDetector`、`countHardBouncesSince` SQL、`countSentByAccountSince` SQL。
- Applies to: 退信写入、HARD 计数、已发计数。
- Violation consequence: “取消硬暂停”被混入退信识别修复，无法判断回归来自行为改造还是统计口径变化。
- 来源: `K-inline-bounce-path-preempts-mime-parse`。

## 现状审计

### `bounce_record`（MySQL，只读复用）

- Schema/mapping:
  - `V29__create_bounce_record.sql`：`sender_account_code NOT NULL`、`bounce_message_id UNIQUE`、`bounce_type NOT NULL`、`received_at NOT NULL`；无任何外键。
  - `V43__add_bounce_record_failed_recipient.sql`：新增 nullable `failed_recipient`。
  - `BounceRecord.kt` 与上述列一一映射。
- Write paths:
  1. `BounceCollectionService.ingest()` 是唯一 `bounceRecordRepository.save(BounceRecord(...))` 生产写入口。
  2. 调用入口 A：`AutoMailReplyService.receiveAndAutoReply()` 内联文本检测命中后调用 `ingest`。
  3. 调用入口 B：`BounceCollectionService.collectBounces()` 完整 MIME 扫描后调用同一 `ingest`。
- Read paths:
  1. `BounceRateMonitorService`：`countHardBouncesSince`，本计划继续复用。
  2. `MailMonitoringService.getBounceStats`：`countHardBouncesSince`、`countSoftBouncesSince`。
  3. `MailMonitoringService.providerDistribution`：`countUnattributedBouncesBetween`。
  4. `BounceController.listBounces`：`findPaged`、`countPaged`。
  5. `OperatorStatusReconcileService`：`findAll()`。
  6. `BounceCollectionService`：`existsByBounceMessageId` 去重。
- Interaction points:
  - `BounceCollectionService.ingest` 写入 HARD 行 → `BounceRateMonitorService` 计数 → `MailSenderAccountController` 输出 `hardBounceRateHigh`。
  - HARD/SOFT 误分类事实已确认，但需求明确保留 HARD-only 口径，本计划不绕行、不修复。（来源: `K-inline-bounce-path-preempts-mime-parse`）

### `mail_record`（MySQL，只读复用）

- Schema/mapping:
  - `V1__create_business_tables.sql` 创建 `mail_record`；`expert_contact_id` 有 FK，`sent_at` nullable。
  - 后续迁移增加 `sender_account_code` 等字段；当前 `MailRecord.kt` 映射 `direction`、`sendStatus`、`sentAt`、`senderAccountCode` 等。
- Write paths（grep `mailRecordRepository.save|MailRecord(` 复核）:
  1. `ManualOutreachTxHelper`：首发成功/失败两条写入。
  2. `MeetingScheduleService`：会议邀请写入。
  3. `ManualExpertMailService`：人工邮件写入。
  4. `ManualReplySendAttemptService`：人工回复成功/失败写入。
  5. `AutoMailReplyService`：入站、自动回复、会议邀请等写入。
- Read paths:
  1. `BounceRateMonitorService` 与 `MailMonitoringService.getBounceStats` 共用 `countSentByAccountSince`。
  2. 其他读取集中在监控聚合、Mailbox、AI training/reply、文档读取、联系人状态核对；本计划不改 repository 或其 SQL，均不受影响。
- Existing SQL evidence:

```sql
SELECT COUNT(*) FROM mail_record
 WHERE sender_account_code = :accountCode
   AND direction = 'OUTBOUND'
   AND send_status = 'SENT'
   AND sent_at >= :since
```

- Interaction points:
  - 各外发写路径生成 SENT 行 → `countSentByAccountSince` 成为告警分母；本计划只改变消费后的动作，不改变任一写路径。

### `mail_sender_account`（MySQL，V117 定向数据更新）

- Schema/mapping:
  - `V1` 创建账号表，`account_code UNIQUE`、`enabled NOT NULL DEFAULT 1`。
  - `V28` 增加 `auto_send_paused NOT NULL DEFAULT 0`、nullable reason/at。
  - `MailSenderAccount.kt` 映射 `autoSendPaused`、`autoSendPausedReason`、`autoSendPausedAt`。
- Write paths:
  1. `MailSenderAccountService.createAccount/updateAccount/setEnabled/resetTodaySentCount`：Spring Data `save`，copy 保留未改暂停字段。
  2. `MeetingScheduleService`、`ManualExpertMailService`：`save(account.copy(lastSentAt=...))`，保留暂停字段。
  3. `MailSenderAccountRepository.incrementTodaySentCount/resetDailyCountsBeforeDate`：只改计数/最后发送时间。
  4. `MailSenderAccountRepository.pauseAutoSend`：三列成组写为暂停；调用来源为 `MailSenderAccountService.pauseAutoSend` 和 `SenderAccountSelfCheckService` 直调。
  5. `MailSenderAccountService.pauseAutoSend` 当前调用来源：`BounceRateMonitorService`、`ReputationAutoPauseService`、`ManualInitialOutreachService` 的 SMTP transient/infrastructure 分支。本计划只删除第一处来源。
  6. `MailSenderAccountRepository.resumeAutoSend`：人工恢复端点及 reputation 恢复经 service 调用。
  7. `resumeDailyLimitPausedAccounts`：仅恢复 `LIKE 'DAILY_LIMIT%'`。
  8. 迁移写入：V16 模拟器插入/启用，V20 模拟器禁用，V28/V34 schema 变更；本计划新增 V117 定向清理。
- Read paths:
  1. `MailSenderAccountService.isSendable/listSendableAccounts/remainingDailyCapacity/warmupActiveCount/todayTotalCapacity`。
  2. `SenderAccountAssignmentService.selectAccount`。
  3. `SenderWarmupService.dailyState`。
  4. `SenderAccountBindingService.requireAvailable`。
  5. `ManualInitialOutreachService` 账号进度快照。
  6. `ReputationAutoPauseService` 前缀限定暂停/恢复。
  7. `MailSenderAccountController` 与 `MailMonitoringService.senderAccountHealth` API 输出。
  8. `app.js` 账号列表/编辑弹窗及 reputation 状态展示。
- Interaction points:
  - 当前：`BounceRateMonitorService` 写 `BOUNCE_RATE_HIGH:` → 上述 1–4 读到 paused 并拒绝自动发送。
  - 改后：运行时代码不再产生该写入；V117 清除历史同前缀行；其他暂停写入仍被上述消费者硬拦截。

### API DTO（内存/JSON，无持久化）

- Schema: `MailSenderAccountResponse` 当前有 enabled、auto pause、warmup 等字段；全仓只有 `MailSenderAccountController.toResponse` 一个生产构造点。
- Write path: `toResponse` 构造 JSON DTO。
- Read path: `app.js.loadAccounts`；03B 将新增读取 `hardBounceRateHigh`。
- Interaction point: Controller 调用纯读告警判定 → JSON 布尔 → 03B 渲染；无数据库告警字段、无告警持久化。

## 实现方案

### Task 1: 先用测试固化“只告警、不暂停”判据（I-1、I-2、I-6）

**Files:**
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt`

**Interfaces:**
- Produces test contract for:
  - `fun calculateHardBounceRate(accountCode: String, windowDays: Int = 7): Double`
  - `fun isHardBounceRateHigh(accountCode: String): Boolean`
  - `fun checkAndWarn(accountCode: String, windowDays: Int = 7, threshold: Double = 0.05): Double`

- [ ] **Step 1: Rewrite the high-rate test before production code**

Remove the `MailSenderAccountService` mock and third constructor argument. Replace the old pause assertion with tests covering these exact cases:

```kotlin
// 2 / 20 = 10%: calculate returns 0.1; isHardBounceRateHigh is true.
// 1 / 20 = 5% exactly: isHardBounceRateHigh is false (strict >).
// 2 / 10: calculate returns -1.0; isHardBounceRateHigh is false.
// 0 / 50: calculate returns 0.0; isHardBounceRateHigh is false.
// checkAndWarn on 2 / 20 returns 0.1 and has no sender-account collaborator to mutate.
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=BounceRateMonitorServiceTest test
```

Expected: compilation failure because the new methods do not exist and the old constructor still requires `MailSenderAccountService`.

### Task 2: 实现纯计算与 WARN 检查（I-1、I-2、I-6）

**Files:**
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt`

**Interfaces:**
- Consumes existing repository methods unchanged.
- Produces the three methods declared in Task 1.

- [ ] **Step 1: Apply the minimal service implementation**

Remove `MailSenderAccountService` from the constructor. Replace `checkAndPause` with this method shape; keep the three constants unchanged:

```kotlin
fun calculateHardBounceRate(
    accountCode: String,
    windowDays: Int = DEFAULT_WINDOW_DAYS
): Double {
    val since = LocalDateTime.now().minusDays(windowDays.toLong())
    val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
    val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
    if (sentCount < MIN_SAMPLE_SIZE) return -1.0
    return hardBounces.toDouble() / sentCount
}

fun isHardBounceRateHigh(accountCode: String): Boolean =
    calculateHardBounceRate(accountCode) > DEFAULT_THRESHOLD

fun checkAndWarn(
    accountCode: String,
    windowDays: Int = DEFAULT_WINDOW_DAYS,
    threshold: Double = DEFAULT_THRESHOLD
): Double {
    val rate = calculateHardBounceRate(accountCode, windowDays)
    if (rate > threshold) {
        log.warn(
            "Hard bounce rate high for {}: {}% > {}%; warning only, automatic sending continues",
            accountCode,
            String.format("%.2f", rate * 100),
            String.format("%.2f", threshold * 100)
        )
    }
    return rate
}
```

- [ ] **Step 2: Prove pause mutation is absent**

```bash
rg -n "MailSenderAccountService|pauseAutoSend|resumeAutoSend" \
  src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt
```

Expected: no matches.

- [ ] **Step 3: Run Task 1 tests GREEN**

Run the Task 1 Maven command. Expected: `Tests run: 5`, zero failures/errors.

### Task 3: 切换 IMAP 轮询调用点（I-1、I-2）

**Files:**
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`

**Interfaces:**
- Consumes `BounceRateMonitorService.checkAndWarn(accountCode)` from Task 2.

- [ ] **Step 1: Update the two in-order test assertions and mock default**

Replace exactly three `checkAndPause` references in the test file: the mock default method-name branch and the two `inOrder.verify` calls. New name: `checkAndWarn`。默认返回仍为 `-1.0`。

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=AutoMailReplyServiceTest test
```

Expected: failure while production still calls `checkAndPause`.

- [ ] **Step 3: Replace the sole production call**

```kotlin
bounceRateMonitorService.checkAndWarn(accountCode)
```

位置保持在 `bounceCollectionService.collectBounces(account)` 之后；不移动退信收集顺序。

- [ ] **Step 4: Run focused test GREEN**

Run the Step 2 command. Expected: zero failures/errors.

### Task 4: API 暴露单一布尔告警（I-2、I-3）

**Files:**
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt`

**Interfaces:**
- Consumes `BounceRateMonitorService.isHardBounceRateHigh(accountCode)`.
- Produces JSON field `hardBounceRateHigh: Boolean` on every sender-account response path.

- [ ] **Step 1: Add the failing MVC assertions**

Add `@MockBean lateinit var bounceRateMonitorService: BounceRateMonitorService`。在列表测试中明确 stub：

```kotlin
Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("a1")).thenReturn(true)
Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("a2")).thenReturn(false)
```

并断言：

```kotlin
.andExpect(jsonPath("$[0].hardBounceRateHigh").value(true))
.andExpect(jsonPath("$[1].hardBounceRateHigh").value(false))
```

- [ ] **Step 2: Run MVC test RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=MailSenderAccountControllerMvcTest test
```

Expected: missing Spring bean constructor dependency and/or missing JSON field.

- [ ] **Step 3: Add the controller dependency and DTO field**

Constructor新增：

```kotlin
private val bounceRateMonitorService: BounceRateMonitorService
```

`MailSenderAccountResponse` 新增唯一字段：

```kotlin
val hardBounceRateHigh: Boolean,
```

统一 `toResponse` 构造赋值：

```kotlin
hardBounceRateHigh = bounceRateMonitorService.isHardBounceRateHigh(account.accountCode),
```

不得在各 endpoint 分支分别计算；必须保持一个构造点。

- [ ] **Step 4: Run MVC test GREEN**

Run the Step 2 command. Expected: zero failures/errors.

### Task 5: V117 只解除历史硬退率暂停（I-4、I-5）

**Files:**
- Create: `src/main/resources/db/migration/V117__convert_bounce_rate_pause_to_warning.sql`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

**Interfaces:**
- Consumes V28 三个 auto-pause columns.
- Produces no schema; only a one-time data-state correction.

- [ ] **Step 1: Write the migration integration test first**

新增测试：先用 `flyway(MigrationVersion.fromVersion("116"))` clean+migrate；插入四个账号：

```text
bounce-paused: auto_send_paused=1, reason=BOUNCE_RATE_HIGH:6.25%
selfcheck-paused: auto_send_paused=1, reason=SELF_CHECK_FAILED:timeout
reputation-paused: auto_send_paused=1, reason=REPUTATION:spam_rate=0.5%
normal: auto_send_paused=0, reason=NULL
```

再执行 latest migration，断言：

```sql
SELECT COUNT(*) FROM mail_sender_account
 WHERE account_code='bounce-paused'
   AND auto_send_paused=0
   AND auto_send_paused_reason IS NULL
   AND auto_send_paused_at IS NULL;
-- expected 1

SELECT COUNT(*) FROM mail_sender_account
 WHERE account_code='selfcheck-paused'
   AND auto_send_paused=1
   AND auto_send_paused_reason='SELF_CHECK_FAILED:timeout';
-- expected 1

SELECT COUNT(*) FROM mail_sender_account
 WHERE account_code='reputation-paused'
   AND auto_send_paused=1
   AND auto_send_paused_reason='REPUTATION:spam_rate=0.5%';
-- expected 1
```

同步把当前文件中 5 个“latest” `assertEquals("112", ...targetSchemaVersion)` 改为 `117`；显式 V23/V24 target 断言保持原值。名称含“through/upgrades to V112”的 latest 测试改为 V117；专门验证 V112 RAG 内容的测试名保持不变。

- [ ] **Step 2: Run migration test RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test
```

Expected: latest version/数据状态断言失败，因为 V117 尚不存在。需要本机 Docker。

- [ ] **Step 3: Create the exact V117 SQL**

```sql
UPDATE mail_sender_account
   SET auto_send_paused = 0,
       auto_send_paused_reason = NULL,
       auto_send_paused_at = NULL
 WHERE auto_send_paused = 1
   AND auto_send_paused_reason LIKE 'BOUNCE_RATE_HIGH:%';
```

- [ ] **Step 4: Run migration test GREEN**

Run the Step 2 command. Expected: target schema version `117`; all tests pass.

### Task 6: 更新处置手册（I-1、I-4、I-6）

**Files:**
- Modify: `docs/runbooks/bounce-rate-auto-pause.md`

- [ ] **Step 1: Correct only behavior made stale by this plan**

必须做以下逐项修改：

1. 标题改为 `# 发件账号硬退率告警（BOUNCE_RATE_HIGH）处置手册`。
2. 开头说明改为“只读核对 + 名单侧人工止血；告警不暂停账号”。
3. 第 0 节“解除”行改为：`无需解除；V117 会清除历史 BOUNCE_RATE_HIGH 暂停，新告警不写暂停状态。`
4. 第 1.1 节不再要求 reason 必须为 `BOUNCE_RATE_HIGH:`；改为核对 API 的 `hardBounceRateHigh`。
5. 第 4 节改名为 `## 4. 确认发送未被告警阻断`，删除 `POST .../resume-auto-send` 操作；改为刷新账号 API 并确认 `enabled=true`、`autoSendPaused=false`、`hardBounceRateHigh=true`。
6. 删除所有“全绿才能解除”“解除账号”表述；保留退信真伪、名单问题、流量搬家、口径缺陷和根因处置内容。
7. 明确人工“恢复发送”仍仅用于其他真实暂停原因，硬退率告警不显示恢复按钮。

### Task 7: 后端整体验证

- [ ] **Step 1: Run focused backend tests**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn -Dtest=BounceRateMonitorServiceTest,AutoMailReplyServiceTest,MailSenderAccountControllerMvcTest test
```

- [ ] **Step 2: Run full non-Docker regression**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn test package
```

Expected: `BUILD SUCCESS`；Maven test 阶段绑定的 JS tests 同时通过。

- [ ] **Step 3: Run static scope guards**

```bash
rg -n "checkAndPause|BOUNCE_RATE_HIGH" src/main/kotlin src/test/kotlin
rg -n "pauseAutoSend|MailSenderAccountService" src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt
git diff --check
```

Expected:
- `checkAndPause` 无命中。
- 生产 Kotlin 中 `BOUNCE_RATE_HIGH` 无暂停写入；该前缀只允许出现在 V117、测试、文档。
- 第二条命令无命中。
- `git diff --check` 无输出。

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt` | 硬暂停改纯计算+WARN | 邮件账号健康 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 唯一调用点改 `checkAndWarn` | 邮件账号健康 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt` | DTO 新增服务端布尔告警 | 邮件账号健康 |
| 4 | `src/main/resources/db/migration/V117__convert_bounce_rate_pause_to_warning.sql` | 定向解除历史暂停 | 账号状态数据 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt` | 判据与无暂停测试 | 测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 调用顺序回归 | 测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt` | JSON 告警字段测试 | 测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | V117 数据效果与 latest pin | 测试 |
| 9 | `docs/runbooks/bounce-rate-auto-pause.md` | 处置流程改为告警语义 | 运维文档 |

合计 9 文件；生产子系统 2 个（邮件账号健康、账号状态数据）；无新增持久化字段。

## 验收标准

- I-1：`BounceRateMonitorService.kt` 无 `MailSenderAccountService`、`pauseAutoSend`、`resumeAutoSend`；高比例单测返回 0.1；`AutoMailReplyService` 只调用 `checkAndWarn`。
- I-2：单测覆盖 10%、恰好 5%、样本 10、零硬退四个边界；三个常量与两条 repository SQL diff 为零。
- I-3：MVC 测试断言 true/false 两行；全仓 `MailSenderAccountResponse(` 仍只有 Controller 一个构造点；字段非 nullable。
- I-4：V117 SQL WHERE 同时含 `auto_send_paused = 1` 与 `LIKE 'BOUNCE_RATE_HIGH:%'`；迁移 IT 证明其他两种暂停未变。
- I-5：`git diff` 不含 `MailSenderAccountService.kt`、`SenderAccountAssignmentService.kt`、`SenderWarmupService.kt`、`SenderAccountBindingService.kt`；其既有暂停测试全量通过。
- I-6：`git diff` 不含 `BounceCollectionService.kt`、`BounceDetector.kt`、`BounceRecordRepository.kt`、`MailRecordRepository.kt`、`MailMonitoringService.kt`。
- Integration：退信写入后触发轮询，账号 API 返回 `hardBounceRateHigh=true` 且 `autoSendPaused=false`；发送选号继续可用该账号。
- Regression：JDK 11 `mvn test package` 成功；Docker migration IT 成功；`git diff --check` 无输出。

## 人工验收清单

### A-1: 高硬退率只告警、不暂停

- 前置条件: 测试账号 `WARN_ONLY` 已启用、未暂停；近 7 天有 20 条 `OUTBOUND/SENT` mail_record 和 2 条 `HARD` bounce_record，账号今日额度未满。
- 操作步骤: 1. 调用 `POST /api/mail/auto-reply?accountCode=WARN_ONLY&maxMessages=1`。2. 调用 `GET /api/mail/sender-accounts`。3. 查应用日志。
- 预期结果: `WARN_ONLY.hardBounceRateHigh=true`；`autoSendPaused=false`；`autoSendPausedReason=null`；日志出现 `warning only, automatic sending continues`；数据库三列保持 `0/NULL/NULL`。
- 覆盖: Observable 1、2；I-1、I-2、I-3；bounce/mail_record → monitor → API interaction。

### A-2: 恰好 5% 不告警

- 前置条件: 账号 `BOUNDARY` 近 7 天 20 封 SENT、1 条 HARD，未暂停。
- 操作步骤: 调用 `GET /api/mail/sender-accounts`。
- 预期结果: `BOUNDARY.hardBounceRateHigh=false`；`autoSendPaused=false`。
- 覆盖: What must NOT change 1；I-2。

### A-3: 样本不足不告警

- 前置条件: 账号 `SMALL_SAMPLE` 近 7 天 10 封 SENT、2 条 HARD，未暂停。
- 操作步骤: 调用 `GET /api/mail/sender-accounts`。
- 预期结果: `SMALL_SAMPLE.hardBounceRateHigh=false`；`autoSendPaused=false`。
- 覆盖: What must NOT change 1；I-2。

### A-4: 历史 BOUNCE_RATE_HIGH 暂停被定向解除

- 前置条件: 部署 V117 前准备三行：`BOUNCE_RATE_HIGH:6.25%`、`SELF_CHECK_FAILED:timeout`、`REPUTATION:spam_rate=0.5%`，三者 `auto_send_paused=1`。
- 操作步骤: 部署并完成 Flyway；调用 `GET /api/mail/sender-accounts`。
- 预期结果: BOUNCE 行为 `autoSendPaused=false/reason=null/at=null`；SELF_CHECK 与 REPUTATION 两行仍为 `autoSendPaused=true` 且 reason 逐字不变；所有账号 enabled 值不变。
- 覆盖: Observable 3；I-4；migration write → sendability/API read interaction。

### A-5: 其他故障暂停仍是硬门槛

- 前置条件: 一个未满额度账号 reason 为 `SELF_CHECK_FAILED:timeout` 且 paused=true；另一个健康账号可发送。
- 操作步骤: 启动一次 INTRODUCTION 自动批量发送并查看账号统计。
- 预期结果: SELF_CHECK 账号发送成功数为 0；健康账号承担发送；SELF_CHECK 账号仍显示暂停，未被本次改造恢复。
- 覆盖: What must NOT change 3、6；I-5。

### A-6: 手工恢复与 DAILY_LIMIT 恢复回归

- 前置条件: 一个 `SELF_CHECK_FAILED:` 暂停账号；一个 `DAILY_LIMIT%` 暂停账号。
- 操作步骤: 1. 对前者调用 `POST /api/mail/sender-accounts/{code}/resume-auto-send`。2. 对后者运行既有每日重置任务。
- 预期结果: 两者分别通过原有路径恢复；接口仍返回 200；没有 `hardBounceRateHigh` 参与恢复判断。
- 覆盖: What must NOT change 4；I-5。

### A-7: 监控统计接口口径不变

- 前置条件: 任一账号近 7 天有确定的 HARD、SOFT、SENT 数据。
- 操作步骤: 改造前后分别调用 `GET /api/mail-monitoring/bounce-stats?accountCode={code}&days=7`。
- 预期结果: `hardBounceCount`、`softBounceCount`、`sentCount`、`bounceRate` 四字段数值逐项相同。
- 覆盖: What must NOT change 2、5；I-6。

### A-8: 退信采集与 EMAIL_INVALID 回归

- 前置条件: 准备一封能被现有 `BounceDetector` 判为 HARD 且可关联专家的退信。
- 操作步骤: 触发该账号自动收信，随后查询退信列表和专家详情。
- 预期结果: 退信列表新增 1 条 HARD；关联专家仍被标记 `EMAIL_INVALID`；发件账号不因这条退信自动暂停。
- 覆盖: What must NOT change 5；I-1、I-6；bounce ingest 跨路径。

> 人工验收开始时，才从本节导出 `03a-bounce-rate-soft-warning-backend-acceptance.md`；现在不要生成。
