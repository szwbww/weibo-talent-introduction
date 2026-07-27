# Plan A: 发件账号生命周期改进

> 日期: 2026-06-24
> 前置: 无
> 后续: Plan B — Warmup 配置化与分发策略优化

---

## 需求描述

**可观测结果**：发件账号管理页面增加三项改进：(1) 启用账号前必须通过连通性测试；(2) 编辑账号时不再强制填写授权码，已存储的授权码保持不变；(3) 可物理删除无关联的发件账号。

**不可变更**：
- 现有已启用账号的行为不受影响
- SMTP/IMAP 连通性测试逻辑本身不变
- `SIMULATOR_NOOP` 账号不可被删除
- `campaign.sender_account_id` 外键约束继续生效

**不在范围**：
- Warmup 配置化（Plan B）
- 策略权重分发优化（Plan B）
- 前端 UI 重构（仅在现有结构上增量修改）

---

## 关键不变量

### Invariant I-1: 启用前必须通过连通性测试
- Rule: 调用 `setEnabled(accountCode, true)` 时，服务层必须先执行 `MailAccountConnectivityService.testAccount(accountCode)` 并验证 `result.passed == true`，否则抛出异常拒绝启用。
- Applies to: `MailSenderAccountService.setEnabled()`
- Violation consequence: 配置错误的账号被启用，发送邮件全部失败，浪费当日限额并触发自动暂停。

### Invariant I-2: 授权码编辑时可选
- Rule: `MailSenderAccountUpdateCommand` 的 `smtpPassword` 和 `imapPassword` 改为 `String?`（nullable）。当值为 `null` 或空字符串时，`updateAccount()` 保留 `existing` 记录中的密码值；当值非空时，用新值覆盖。`MailSenderAccountCreateCommand` 的密码字段保持 `String` 非空——新建时必须提供。
- Applies to: `MailSenderAccountService.updateAccount()`, `MailSenderAccountController.MailSenderAccountUpdateRequest`
- Violation consequence: 编辑账号时未填授权码导致密码被清空，SMTP/IMAP 连接全部失败。

### Invariant I-3: 物理删除安全检查
- Rule: 删除前必须检查 `campaign` 表中是否存在 `sender_account_id = account.id` 的记录。若存在，拒绝删除并返回错误信息。`SIMULATOR_NOOP` 账号始终拒绝删除。
- Applies to: `MailSenderAccountService.deleteAccount()`
- Violation consequence: 违反外键约束导致 SQL 异常；或删除模拟器账号导致测试流程中断。

### Invariant I-4: Response 不泄露密码
- Rule: `MailSenderAccountResponse` 不包含 `smtpPassword` 和 `imapPassword` 字段（现状已满足，保持不变）。
- Applies to: `MailSenderAccountController.toResponse()`
- Violation consequence: 前端暴露授权码，安全风险。

---

## 现状审计

### mail_sender_account 表
- Schema: V1 创建，V28 加 auto_send_paused 字段。字段见 `MailSenderAccount.kt`（36 行，15+ 列）。
- Write paths:
  1. `MailSenderAccountService.createAccount()` — 新建记录
  2. `MailSenderAccountService.updateAccount()` — 全字段更新（含 smtpPassword, imapPassword）
  3. `MailSenderAccountService.setEnabled()` — 更新 enabled 字段
  4. `MailSenderAccountService.resetTodaySentCount()` — 清零 todaySentCount
  5. `MailSenderAccountService.resetDailyCounts()` — 批量清零 + 解除限额暂停
  6. `MailSenderAccountService.pauseAutoSend()` / `resumeAutoSend()` — 暂停/恢复
  7. `MailSenderAccountRepository.incrementTodaySentCount()` — 原子+1
  8. `MailSenderAccountRepository.pauseAutoSend()` / `resumeAutoSend()` — 直接 SQL
  9. V16, V20 migration — seed/update SIMULATOR_NOOP
- Read paths:
  1. `MailSenderAccountService.listAccounts()` — 列出所有账号（前端列表）
  2. `MailSenderAccountService.getAccount()` / `getEnabledAccount()` — 单账号查询
  3. `MailSenderAccountService.listSendableAccounts()` / `selectAccountForSending()` — 发送选择
  4. `MailSenderAccountService.listAutoReceiveAccounts()` — IMAP 收件选择
  5. `SenderAccountAssignmentService.selectAccount()` — 批量分发选择
  6. `MailAccountConnectivityService.testAccount()` — 读取连接配置做测试
  7. `SmtpSenderFactory` — 读取 SMTP 配置创建 sender
  8. `ImapMailReceiveService` — 读取 IMAP 配置收件
  9. `BounceRateMonitorService` / `BounceCollectionService` — 按 accountCode 查询
  10. `MailMonitoringService` — 健康看板
- Interaction points:
  - I-2 影响 write path 2 与 read paths 6/7/8 的交互：密码为空会导致连接失败

### campaign 表
- FK: `sender_account_id BIGINT NOT NULL REFERENCES mail_sender_account(id)` (V1)
- 物理删除 sender account 需确保无 campaign 引用

### bounce_record / mail_send_attempt
- 通过 `sender_account_code` / `account_code` VARCHAR 关联，无 FK。删除 sender account 不会导致 SQL 异常，但历史记录中的 accountCode 变成孤立引用（可接受）。

---

## 实现方案

### 阶段 1: 启用前连通性测试 (I-1)

**Task 1.1**: 修改 `MailSenderAccountService.setEnabled()`
- 文件: `MailSenderAccountService.kt`
- 当 `enabled = true` 时，注入 `MailAccountConnectivityService`，调用 `testAccount(accountCode)`。若 `!result.passed`，抛出 `IllegalStateException("连通性测试未通过: SMTP=${result.smtp.message}, IMAP=${result.imap.message}")`。
- 当 `enabled = false` 时，行为不变。
- 遵守: I-1

**Task 1.2**: 修改 `MailSenderAccountService.createAccount()`
- 文件: `MailSenderAccountService.kt`
- 新建账号时强制 `enabled = false`，忽略 command 中的 enabled 值。用户必须新建后点击测试再启用。
- 遵守: I-1

**Task 1.3**: 前端启用按钮交互优化
- 文件: `app.js`
- 点击"启用"按钮时，若 API 返回错误（连通性测试失败），显示具体错误信息（SMTP/IMAP 哪个失败）。
- 遵守: I-1

### 阶段 2: 授权码编辑可选 (I-2)

**Task 2.1**: 修改 UpdateCommand 和 UpdateRequest 密码字段为 nullable
- 文件: `MailSenderAccountController.kt`
- `MailSenderAccountUpdateRequest.smtpPassword` 和 `imapPassword` 类型改为 `String?`，默认 `null`
- `MailSenderAccountUpdateCommand.smtpPassword` 和 `imapPassword` 类型改为 `String?`
- 遵守: I-2, I-4

**Task 2.2**: 修改 `MailSenderAccountService.updateAccount()` 密码合并逻辑
- 文件: `MailSenderAccountService.kt`
- `smtpPassword`: 若 `command.smtpPassword.isNullOrBlank()` 则用 `existing.smtpPassword`，否则用 `command.smtpPassword`
- `imapPassword`: 同理
- 当密码字段发生变更时（新值非空且不等于旧值），evict SmtpSenderFactory 缓存（现有逻辑已覆盖）。
- 遵守: I-2

**Task 2.3**: 前端编辑表单密码占位符
- 文件: `app.js`
- 编辑模式下密码字段显示 placeholder="留空保持不变"，不再是空白必填。
- 新建模式下密码字段保持必填。
- 遵守: I-2

### 阶段 3: 物理删除 (I-3)

**Task 3.1**: 添加 `deleteAccount()` 方法
- 文件: `MailSenderAccountService.kt`
- 查找账号，检查 `accountCode != SIMULATOR_ACCOUNT_CODE`，检查无 campaign 引用（需注入 `CampaignRepository` 或直接查询）。
- 通过检查后调用 `repository.deleteById(account.id!!)`。
- `CrudRepository` 已有 `deleteById`，无需自定义 query。
- 遵守: I-3

**Task 3.2**: 检查 campaign 关联
- 文件: `MailSenderAccountService.kt`
- 需要一个方式检查 campaign 是否引用此账号。最简单：注入 `CampaignRepository`，添加 `existsBySenderAccountId(id: Long): Boolean` 方法。
- 若存在引用，抛出 `IllegalStateException("该账号已被活动引用，无法删除")`。
- 遵守: I-3

**Task 3.3**: 添加 DELETE 端点
- 文件: `MailSenderAccountController.kt`
- `@DeleteMapping("/{accountCode}")` → 调用 `service.deleteAccount(accountCode)`
- 返回 204 No Content 或简单的成功消息。
- 遵守: I-3

**Task 3.4**: 前端删除按钮 + 确认框
- 文件: `app.js`
- 在账号操作按钮行增加"删除"按钮。
- 点击后弹出 `confirm("确认删除账号 ${accountCode}？此操作不可恢复。")`，确认后调用 `DELETE /api/mail/sender-accounts/${accountCode}`。
- 若 API 返回错误（有活动引用），显示错误信息。
- 遵守: I-3

---

## 变更文件清单

| # | 文件 | 变更类型 | 涉及不变量 |
|---|------|----------|-----------|
| 1 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | 修改 | I-1, I-2, I-3 |
| 2 | `src/main/kotlin/.../mail/controller/MailSenderAccountController.kt` | 修改 | I-2, I-3, I-4 |
| 3 | `src/main/kotlin/.../campaign/repository/CampaignRepository.kt` | 修改（加查询方法） | I-3 |
| 4 | `src/main/resources/static/app.js` | 修改 | I-1, I-2, I-3 |

**共 4 个文件，0 个新文件，0 个迁移。**

---

## 验收标准

- **I-1**: 
  - 新建账号后 `enabled = false`，验证 API 返回 `enabled: false`
  - 未经测试直接调用 `POST /{accountCode}/enable`，应返回连通性测试失败错误
  - 通过 `POST /{accountCode}/test-connectivity` 后再启用，应成功
  - 禁用账号 `POST /{accountCode}/disable` 不需要测试，直接成功

- **I-2**: 
  - 编辑账号时 smtpPassword/imapPassword 留空提交，账号仍可正常发送（密码未被清空）
  - 编辑账号时填入新密码，密码被更新，SmtpSenderFactory 缓存被清除
  - 新建账号时密码为空，应返回验证错误

- **I-3**: 
  - 删除无 campaign 引用的账号，记录被物理删除，列表中不再显示
  - 删除有 campaign 引用的账号，返回错误信息 "该账号已被活动引用，无法删除"
  - 删除 `SIMULATOR_NOOP`，返回拒绝错误
  - 前端点击删除弹出确认框，取消不执行删除

- **I-4**: 
  - GET 账号列表和详情 API 响应中不包含 smtpPassword/imapPassword 字段（现状已满足）
