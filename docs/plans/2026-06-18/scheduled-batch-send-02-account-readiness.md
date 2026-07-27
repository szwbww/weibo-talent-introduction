# 子计划 02：账号自动暂停字段 + 真实投递自检服务 + 选号谓词统一

> 主计划：`2026-06-18-scheduled-batch-send-00-master.md`。依赖子计划 01（读自检 TTL 配置）。

## 需求描述
- 可观察结果：
  - `mail_sender_account` 新增「自动暂停」字段组（与人工 `enabled` 解耦）。
  - 新增 `SenderAccountSelfCheckService`：对账号自身真发探针邮件做轻量自检（SMTP 250=通过），结果带 TTL 缓存；失败则自动暂停该账号。
  - 所有账号选号读路径统一并入「可发送」谓词（I-3）。
  - 账号列表响应透出自动暂停状态；提供手动恢复（解除自动暂停）端点。
- 不可改变：人工 `enabled` 的语义、`SmtpMailDeliveryService` 业务发送、模拟账号排除。
- 不做：编排器轮循环（子计划 03 调用本服务）；前端展示（子计划 04）。

## 关键不变量（引用 + 专属）
- 引用 I-3（可发送谓词唯一口径）、I-4（自检语义 SMTP 自发 + TTL）。
- Invariant L2-1：自动暂停与人工停用解耦且可独立恢复。
  - 规则：`auto_send_paused` 仅由系统（自检失败 / 子计划 03 限额耗尽）置 true，由「手动恢复」端点或自检通过置 false；**绝不**修改 `enabled`。`enabled` 仅由 `setEnabled` 改。两字段独立存取。
  - 适用于：`SenderAccountSelfCheckService`、`MailSenderAccountService.pauseAutoSend/resumeAutoSend`。
  - 违反后果：人工停用被自检覆盖 / 自动暂停与人工状态混淆（违反 I-3 解耦）。
- Invariant L2-2：自检探针不污染业务数据。
  - 规则：探针发送**不**写 `mail_record`、不自增 `today_sent_count`、不触发状态机；仅更新自检缓存与（失败时）`auto_send_paused`。探针收件人固定为账号自身 `senderEmail`。
  - 适用于：`SenderAccountSelfCheckService`。
  - 违反后果：统计虚高 / 误占额度。

## 现状审计（专属，已 grep 验证）
- `mail_sender_account` 写/读路径见主计划「现状审计」。关键读路径需改：
  - `SenderAccountAssignmentService.selectAccount`（行 18-25）：`filter { it.todaySentCount<it.dailySendLimit && it.accountCode!=SIMULATOR }`。
  - `MailSenderAccountService.selectAccountForSending`（行 105-109）：同口径。
- 自检可复用构造：`SmtpMailDeliveryService.send` 用 `JavaMailSenderImpl{host/port/username/password/javaMailProperties}` + `smtpProperties(port)`（465→ssl，否则 starttls）。`MailAccountConnectivityService.testSmtp` 用 `testConnection()`（仅建连+AUTH，**不发**，不满足「真实投递」要求）。
- TTL 配置来源：子计划 01 `BatchSendSettingService.getConfig().selfCheckTtlMinutes`。
- `MailSenderAccountResponse`（`MailSenderAccountController.kt`）当前无暂停字段，需补。

## 实现方案

### 任务 1：迁移 V28 增列
文件：`src/main/resources/db/migration/V28__add_sender_account_auto_pause.sql`
- `ALTER TABLE mail_sender_account ADD COLUMN auto_send_paused TINYINT(1) NOT NULL DEFAULT 0;`
- `ADD COLUMN auto_send_paused_reason VARCHAR(255) NULL;`
- `ADD COLUMN auto_send_paused_at DATETIME NULL;`
- （字段组同属「自动暂停」一个概念，由 I-3/L2-1 统一治理。）

### 任务 2：领域字段
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailSenderAccount.kt`
- 增 `val autoSendPaused: Boolean = false`、`val autoSendPausedReason: String? = null`、`val autoSendPausedAt: LocalDateTime? = null`。

### 任务 3：仓储查询（遵循 I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt`
- 新增 `@Modifying @Query` 暂停/恢复（避免读改写竞态）：
  - `pauseAutoSend(accountCode, reason, at): Int`（`SET auto_send_paused=1, auto_send_paused_reason=:reason, auto_send_paused_at=:at WHERE account_code=:accountCode`）。
  - `resumeAutoSend(accountCode): Int`（`SET auto_send_paused=0, auto_send_paused_reason=NULL, auto_send_paused_at=NULL`）。
- 说明：选号仍走内存 `filter`（账号量小），不新增「可发送」SQL finder，统一谓词在 service 层（任务 5）。

### 任务 4：自检服务（遵循 I-4/L2-1/L2-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountSelfCheckService.kt`
- 依赖：`MailSenderAccountRepository`、`BatchSendSettingService`（取 TTL）。
- 内存缓存 `ConcurrentHashMap<accountCode, SelfCheckCacheEntry(passed, checkedAt, message)>`。
- `checkSendable(account): SelfCheckResult`：
  1. TTL 内有缓存 → 直接返回缓存结果（I-4）。
  2. 否则构造 `JavaMailSenderImpl`（复用 `SmtpMailDeliveryService` 的属性构造方式），发一封探针到 `account.senderEmail`（主题如 `[self-check] <code> <ts>`，极简正文）。
  3. SMTP 无异常 = 通过：写缓存 passed=true；若该账号此前因 `SELF_CHECK_FAILED` 被暂停，可选地不自动恢复（保守：自检通过不自动 resume，避免与人工恢复语义冲突；恢复仅由手动端点/限额清零触发——在验收中固化此选择）。
  4. 抛异常 = 失败：写缓存 passed=false + message；调用 `repository.pauseAutoSend(code, "SELF_CHECK_FAILED:<msg 截断>", now)`（L2-1）。
- **不**写 mail_record/不自增计数/不流转状态（L2-2）。
- DTO：`data class SelfCheckResult(accountCode, passed, message, fromCache)`。
- 提供 `invalidate(accountCode)`（手动恢复后清缓存，下次强制重检）。

### 任务 5：选号谓词统一（遵循 I-3）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt`
  - `selectAccount` 的 `filter` 增加 `&& !it.autoSendPaused`（与现有 `enabled`（注：本服务用 `findAllByEnabledTrue` 已含 enabled）/`todaySentCount<limit`/`!=SIMULATOR` 合并为 I-3 谓词）。
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
  - `selectAccountForSending` 同步增加 `&& !it.autoSendPaused`。
  - 新增 `pauseAutoSend(accountCode, reason)` / `resumeAutoSend(accountCode)`（封装仓储 @Modifying，恢复时调 `SenderAccountSelfCheckService.invalidate`）。
  - 新增 `countSendableAccounts(): Int` 或 `listSendableAccounts(): List<MailSenderAccount>`（供子计划 03 的「无可用账号」判定 I-5，统一口径）。

### 任务 6：控制器透出 + 手动恢复端点
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt`
- `MailSenderAccountResponse` 增 `autoSendPaused`、`autoSendPausedReason`、`autoSendPausedAt`；`toResponse()` 映射。
- 新增 `POST /{accountCode}/resume-auto-send` → `service.resumeAutoSend(code)`（手动解除自动暂停）。
- 可选 `POST /{accountCode}/self-check` → `selfCheckService.checkSendable(account)`（手动触发一次自检，便于运维验证）。

## 变更文件清单（7）
| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V28__add_sender_account_auto_pause.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailSenderAccount.kt` | 改（增字段） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt` | 改（增 @Modifying） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountSelfCheckService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt` | 改（谓词） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 改（谓词+pause/resume+sendable） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt` | 改（响应字段+恢复/自检端点） |

测试（不计上限）：`SenderAccountSelfCheckServiceTest`（TTL 命中/失效、失败暂停、不写记录）、更新 `SenderAccountAssignmentServiceTest`（暂停账号被排除）、`MailSenderAccountServiceTest`（pause/resume、selectAccountForSending 排除暂停）。

## 验收标准
- I-3：构造 `enabled=false` / `autoSendPaused=true` / `today>=limit` 三种账号，`selectAccount` 与 `selectAccountForSending` 均不选中；三状态可独立设置与读取。
- I-4：mock SMTP 成功→passed 且 TTL 内第二次 `fromCache=true` 不再发探针；SMTP 抛异常→该账号 `auto_send_paused=1` 且 reason 前缀 `SELF_CHECK_FAILED:`。
- L2-1：`pauseAutoSend`/`resumeAutoSend` 不改 `enabled`；`resumeAutoSend` 后缓存失效、下次重检。
- L2-2：自检前后 `mail_record` 行数不变、`today_sent_count` 不变。
- 控制器：`GET /sender-accounts` 响应含三个新字段；`POST /{code}/resume-auto-send` 将 `auto_send_paused` 置 0。
- Flyway：V28 通过；未改已应用迁移。

## 自检清单
- [x] 新字段组（auto_send_paused…）有不变量 I-3/I-4/L2-1。
- [x] 文件数 7 ≤10；单子系统（账号）。
- [x] 任务引用不变量编号。
- [x] 全部账号选号读路径并入 I-3（grep 确认仅 2 处）。
- [x] 每不变量有验收。
- [x] 文件清单无「等」。
