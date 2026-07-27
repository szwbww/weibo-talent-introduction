# 人工发送邮件与每日配额解耦

> 计划类型：行为变更（人工发送脱离每日配额体系）
> 创建日期：2026-06-27
> 来源讨论：人工发送不应消耗、也不应被每日发送上限阻塞

## 需求描述

**可观察结果**：操作员手动触发的邮件发送（联系人管理页"人工发送"、待处理邮件审阅页的 QA 回复 / 富文本回复 / 组合回复），即使当天所有账号已达每日上限，仍能成功发出；且这些人工发送**不增加** `todaySentCount`，不挤占自动/批量发送的当日配额。

**不可改变**：
- 自动批量外联（`ManualInitialOutreachService.runScheduledBatch`，含 AUTO 与 MANUAL 两种 ExecutionMode 的批量按钮）的配额逻辑、轮次配额、`DAILY_CAP_REACHED`/`DAILY_LIMIT_REACHED` 判定完全不变。
- `selectAccountForSending()` / `listSendableAccounts()` / `isSendable()` 的语义不变（自动链路继续受每日上限约束）。
- 预热（warmup）对自动发送的限速不变。
- 邮件落库（`mail_record`）、会话状态机流转（`ConversationStateService.transition`）、操作日志（`operator_action_log`）行为不变。
- 操作员**显式指定账号**时仍可发送（原本就绕过上限，行为不退化）。

**超出范围（明确不做）**：
- `MeetingScheduleService.confirmSchedule`（`MEETING_CONFIRMATION`，`TriggeredBy.SYSTEM`，第108行选号 + 第154行自增配额）——它标记为系统触发的会议确认自动化步骤，不属于本次"人工发送"定义，保持现状。
- 每日重置定时任务、warmup 配置、账号增删改接口，均不动。
- 不新增任何"人工每日上限"概念（人工发送本次彻底不计配额，不引入第二套计数）。

## 关键不变量

### Invariant I-1: 人工发送不消耗每日配额
- Rule: 所有 `TriggeredBy.OPERATOR` 的真实 SMTP 发送路径，发送成功后**不得**修改账号的 `todaySentCount`（既不 `+1`，也不调用 `incrementTodaySentCount`）。
- Applies to: `ManualExpertMailService.sendManualMail`（当前第95-100行 `account.copy(todaySentCount = +1)` 必须移除）；`PendingMailOperationService.sendQaReply` / `sendManualRichReply` / `sendManualComposedReply`（当前已不自增，保持不自增）。
- Violation consequence: 人工发送挤占自动外联当日额度，导致自动外联提前触发 `DAILY_CAP_REACHED`，与需求"不消耗配额"相悖。
- 来源: original

### Invariant I-2: 人工发送不被每日上限阻塞
- Rule: 人工发送在操作员**未指定**账号时的兜底选号，**不得**以 `todaySentCount < effectiveDailyLimit` 作为过滤条件；只过滤 `enabled == true && !autoSendPaused && accountCode != SIMULATOR_NOOP`。仍必须排除停用、故障暂停、模拟器账号。
- Applies to: 4 个兜底选号点——`ManualExpertMailService.kt:70`、`PendingMailOperationService.kt:101/185/293`，改为调用新方法 `selectAccountForManualSending()`。
- Violation consequence: 当天自动发送已填满上限时，人工发送报 `No available mail sender account` 而失败，与需求"不受每日上限限制"相悖。
- 来源: original

### Invariant I-3: 自动链路配额语义零回归
- Rule: `selectAccountForSending()`、`listSendableAccounts()`、`isSendable()`、`remainingCapacity()`、`SenderAccountAssignmentService` 的过滤逻辑保持不变；新方法 `selectAccountForManualSending()` 是**新增**路径，不得复用/修改既有方法签名或内部判定。
- Applies to: `MailSenderAccountService.kt`、`SenderAccountAssignmentService.kt`（只读校验，不改）。
- Violation consequence: 自动外联/会议确认的配额、暂停、预热行为漂移，引发回归。
- 来源: original

## 现状审计

### 账号配额字段 `mail_sender_account.todaySentCount` / `dailySendLimit`
- Schema/mapping: `MailSenderAccount`（`mail/domain/MailSenderAccount.kt`），`todaySentCount: Int = 0`、`dailySendLimit: Int = 100`、`autoSendPaused`、`enabled`、`warmup*` 字段。MySQL，Spring Data JDBC 不可变 data class。
- **写路径（todaySentCount）**：
  1. `ManualExpertMailService.sendManualMail`（:95-100）— OPERATOR，`account.copy(todaySentCount+1, lastSentAt=now)`。**本次移除自增**（I-1）。
  2. `PendingMailOperationService`（sendQaReply / sendManualRichReply / sendManualComposedReply）— OPERATOR，**不写** todaySentCount（已符合 I-1）。
  3. `MeetingScheduleService.confirmSchedule`（:152-157）— SYSTEM，`+1`。**超出范围，不动**。
  4. `ManualOutreachTxHelper.recordSuccess`（:72）→ `MailSenderAccountRepository.incrementTodaySentCount`（:30）— 批量外联成功后自增。**不动**（I-3）。
  5. `MailSenderAccountService.updateAccount`（:120）/ `resetTodaySentCount`（:158）/ `resetDailyCounts`（:166-171，定时重置）— 管理/重置路径。**不动**。
- **读路径（todaySentCount / 每日上限）**：
  1. `SenderWarmupService.dailyState`（:56）、`remainingCapacity`（:70）— 判定 SENDABLE / *_LIMIT_REACHED。供自动链路用。
  2. `MailSenderAccountService.isSendable`（:209）、`selectionScore`（:214）、`selectAccountForSending`（:182）、`listSendableAccounts`（:188）— 自动选号过滤/打分。
  3. `SenderAccountAssignmentService`（:21/:39）— 批量按 country 分配选号。
  4. 展示：`MailSenderAccountController`（:98/:194）、`MailMonitoringService`（:241）、`ManualInitialOutreachService.buildAccountStats`（:616）— 仅读展示。
- **交互点**：
  - I-1 改动后，人工发送不再推高 `todaySentCount`，故 (读路径1/2/3) 看到的当日计数仅反映自动+批量+会议确认发送。这是**预期**：人工独立于配额。展示侧（读路径4）会少计人工发送量——可接受，符合需求语义。
  - I-2 新增 `selectAccountForManualSending()` 与 (读路径2) 并列存在，互不影响自动链路。

### 账号选号入口 `MailSenderAccountService`
- `selectAccountForSending()`（:182-186）：`findAllByEnabledTrue().filter { isSendable }`，`isSendable` 含 `todaySentCount < effectiveDailyLimit`。被 6 处调用：人工 4 处（本次改）、`MeetingScheduleService:108`（不改）、自身/批量经 `listSendableAccounts`。
- `getEnabledAccount(code)`（:26-28）：仅校验 enabled，不看上限——操作员指定账号时已天然绕过上限与配额（配合 I-1 移除自增后，指定账号路径也不再消耗配额）。

### 现有测试
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` 覆盖 Pending 三个发送方法。需补"账号满额仍可发"用例。
- `ManualExpertMailService` 当前无独立测试，需新增覆盖 I-1（发送后 todaySentCount 不变）。

## 实现方案

### 阶段 1：新增人工选号方法（I-2、I-3）
- 文件：`mail/service/MailSenderAccountService.kt`
- 新增 `fun selectAccountForManualSending(): MailSenderAccount`，从 `findAllByEnabledTrue()` 过滤 `!autoSendPaused && accountCode != SIMULATOR_ACCOUNT_CODE`（**不含** `todaySentCount` 判定），排序复用 `selectionScore`（满额账号 `remainingRatio` 可为负/0，仍可被选中，符合 I-2），无候选时 `error("No available mail sender account for manual send")`。
- 不修改 `selectAccountForSending` / `isSendable` / `listSendableAccounts`（I-3）。

### 阶段 2：人工发送兜底选号改用新方法（I-2）
- 文件：`mail/service/ManualExpertMailService.kt`（:70）、`mail/service/PendingMailOperationService.kt`（:101、:185、:293）
- 将 4 处 `?: mailSenderAccountService.selectAccountForSending()` 改为 `?: mailSenderAccountService.selectAccountForManualSending()`。
- 操作员显式指定账号的分支（`getEnabledAccount`）保持不变。

### 阶段 3：移除人工发送自增配额（I-1）
- 文件：`mail/service/ManualExpertMailService.kt`（:95-100）
- 删除 `mailSenderAccountRepository.save(account.copy(todaySentCount = account.todaySentCount + 1, lastSentAt = now))`。
- 若需保留"最后发送时间"观测，可改为只更新 `lastSentAt`（`account.copy(lastSentAt = now)`，**不动** todaySentCount）；若不需要则整段删除。**默认采用只更新 `lastSentAt`**，避免丢失 last-sent 观测且不触碰配额。
- `PendingMailOperationService` 三个方法本就不自增，无需改动（仅阶段2的选号改动）。
- 阶段2改动后 `ManualExpertMailService` 是否仍需注入 `mailSenderAccountRepository`：若保留 `lastSentAt` 更新则继续需要；若整段删除则移除未用注入。

### 阶段 4：测试（验收）
- 文件：`src/test/kotlin/.../mail/service/ManualExpertMailServiceTest.kt`（新增）
  - I-1：账号 `todaySentCount=5`，调用 `sendManualMail` 成功后断言账号 `todaySentCount` 仍为 5。
  - I-2：唯一启用账号 `todaySentCount == dailySendLimit`（满额），未指定账号调用 `sendManualMail`，断言发送成功而非抛 `No available mail sender account`。
- 文件：`src/test/kotlin/.../mail/service/PendingMailOperationServiceTest.kt`（已存在，补用例）
  - I-2：满额账号下 `sendQaReply` 未指定账号仍成功。

## 变更文件清单

| 文件 | 改动 | 不变量 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 新增 `selectAccountForManualSending()` | I-2, I-3 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 兜底选号改新方法（:70）；移除 todaySentCount 自增、改为只更新 lastSentAt（:95-100） | I-1, I-2 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 3 处兜底选号改新方法（:101/:185/:293） | I-2 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 新增测试（I-1/I-2） | I-1, I-2 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 补满额可发送用例（I-2） | I-2 |

文件数：5（≤10）。子系统：1（mail 选号/发送）。新数据字段：0。

## 验收标准

- **I-1**：单测断言 `sendManualMail` 成功后账号 `todaySentCount` 不变；代码评审确认全工程无任何 `TriggeredBy.OPERATOR` 路径写 `todaySentCount`（grep `todaySentCount` 写点仅剩批量 txHelper 与 MeetingSchedule 系统路径）。
- **I-2**：单测——所有启用账号满额时，未指定账号的人工发送（`sendManualMail`、`sendQaReply`）成功返回；`selectAccountForManualSending` 仍排除 `autoSendPaused`、`SIMULATOR_NOOP`、`enabled=false` 账号（针对这三类的过滤各加断言）。
- **I-3**：`selectAccountForSending` / `isSendable` / `listSendableAccounts` 源码 diff 为空；批量外联现有测试全绿（`ManualInitialOutreachService` 相关测试不回归）。
- **集成场景**：自动外联跑满当日上限 → 自动链路返回 `DAILY_CAP_REACHED`（不变）→ 同一账号下操作员人工发送仍成功且发送后 todaySentCount 不再变化。
- 全量构建/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`、`mvn test` 通过。
