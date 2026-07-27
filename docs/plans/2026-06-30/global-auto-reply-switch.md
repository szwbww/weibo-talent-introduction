# 全局自动回复开关

## 需求描述

- **可观察结果**：运营在 UI 点击"自动回复：全部关闭"后，系统持久化一个全局开关 `autoReply.globalEnabled=false`。此后无论新旧专家、无论 contact 级 `autoReplyEnabled` 状态如何，所有自动回复管道均不会发出邮件。
- **必须不变**：
  - 单个专家的 `autoReplyEnabled` 字段含义不变（二级开关），现有人工/自动切换逻辑继续工作
  - 入站邮件仍正常拉取、保存 `MailRecord`/`InboundMailProcessing`、做意图分类
  - `MANUAL_HANDOFF` 状态语义不变
  - `batch_send_setting` 表逻辑不受影响
- **不做**：
  - 不改 QA 规则自身的 `autoReplyEnabled`
  - 不改 sender account 级别逻辑
  - 不做 UI 重大改版（只改按钮文案和 summary API）

## 关键不变量

### Invariant I-1: 全局开关是硬闸门
- Rule: 当 `autoReply.globalEnabled = false` 时，`AutoMailReplyService.processSingle()` 不得发送任何自动邮件（QA 回复、会议邀请均不发），入站邮件仍保存并标记 reason=`GLOBAL_AUTO_REPLY_DISABLED`
- Applies to: `AutoMailReplyService.processSingle()`
- Violation consequence: 用户关了总开关仍发邮件，即本次 bug 复现
- 来源: original

### Invariant I-2: 全局开关持久化且唯一来源
- Rule: 全局开关存储在 `batch_send_setting` 表（复用已有 key-value 表），key=`autoReply.globalEnabled`，value=`true`/`false`。不依赖运行时内存状态。
- Applies to: `AutoReplySettingService.isGlobalEnabled()`, `AutoReplySettingService.setGlobalEnabled()`
- Violation consequence: 服务重启后开关状态丢失
- 来源: original

### Invariant I-3: 新建 contact 继承全局开关状态
- Rule: 当 `autoReply.globalEnabled=false` 时，新建 `ExpertContact` 的 `autoReplyEnabled` 字段应为 `false`
- Applies to: `InitialOutreachService` (line 45), `ManualInitialOutreachService` (line 276)
- Violation consequence: 新专家在全局关闭期间仍能被自动回复
- 来源: original

### Invariant I-4: UI 按钮语义改为全局开关
- Rule: `/api/expert-contacts/auto-reply/summary` 返回 `globalEnabled` 字段；`/api/expert-contacts/auto-reply/bulk` 改为切换全局开关（不再批量遍历 contacts）
- Applies to: `ExpertContactManagementController`, `ExpertContactManagementService`, `app.js`
- Violation consequence: UI 操作不持久 / 行为不匹配预期
- 来源: original

### Invariant I-5: 优先级链
- Rule: 发送前检查顺序为 `全局开关 → 专家 autoReplyEnabled → MANUAL_HANDOFF 状态 → QA 规则 autoReplyEnabled`。任一环节为 false 则不发。
- Applies to: `AutoMailReplyService.processSingle()`
- Violation consequence: 优先级混乱导致某些场景漏拦截
- 来源: original

## 现状审计

### batch_send_setting 表（复用）
- Schema: `id BIGINT PK, setting_key VARCHAR(64) UNIQUE, setting_value VARCHAR(255), updated_at DATETIME`
- Write paths:
  1. `BatchSendSettingService.upsert()` — 通用 key-value 写入
  2. V27 migration — 初始化种子数据
- Read paths:
  1. `BatchSendSettingService.loadAll()` / `getConfig()` — 全量加载
  2. `BatchSendSettingService.getRuntimeStatus()` / `getPauseReason()` — 按 key 读取
- Interaction points: 本计划新增 key `autoReply.globalEnabled`，不影响已有 `batchSend.*` 相关逻辑

### expert_contact.autoReplyEnabled 字段
- Write paths:
  1. `ExpertContactManagementService.pauseAutoReply()` — 单专家暂停 (line 167)
  2. `ExpertContactManagementService.resumeAutoReply()` — 单专家恢复 (line 206/410)
  3. `ExpertContactManagementService.bulkUpdateAutoReply()` — 批量更新 (line 456)
  4. `AutoMailReplyService.markManualReview()` — 自动回复转人工时关闭 (line 804)
  5. `ExpertContactManagementService` 人工介入路径 (line 97, 141, 298, 332)
  6. `InitialOutreachService` / `ManualInitialOutreachService` — 新建时默认 true
- Read paths:
  1. `AutoMailReplyService.processSingle()` (line 108) — 决定是否自动回复
  2. `ExpertContactManagementService.getAutoReplySummary()` (line 442) — 统计
  3. `ExpertContactManagementController.toResponse()` — 前端展示
  4. `AutoReplyPreviewService` — 预览是否可自动回复
- Interaction points: I-1 要求在 line 108 检查之前加全局开关检查；I-3 要求新建处赋值

### AutoMailReplyService.processSingle() 入口
- 调用方:
  1. `AutoMailReplyService.receiveAndAutoReply()` — 单账号批量
  2. `AutoMailReplyService.processByUids()` — UID 回补
  3. `BatchAutoMailReplyService.pollAccounts()` → `autoMailReplyService.receiveAndAutoReply()` — 全账号批量
  4. `MailAutomationController.checkReplies()` → `batchAutoMailReplyService` — 手动检查回复
  5. `MailQueueConsumer` (async) → `autoMailReplyService.receiveAndAutoReply()`
  6. `MailAutomationScheduler` → `batchAutoMailReplyService.receiveAndAutoReplyAll()`
- 全局开关放在 `processSingle()` 内部即可拦截所有入口 (I-1)

## 实现方案

### Phase 1: DB migration + Service 层

**Task 1.1: 新增 migration V50__add_global_auto_reply_setting.sql** [I-2]
- 文件: `src/main/resources/db/migration/V50__add_global_auto_reply_setting.sql`
- 内容: 向 `batch_send_setting` 表插入 `autoReply.globalEnabled` = `false`（安全优先）

**Task 1.2: 新增 AutoReplySettingService** [I-2]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplySettingService.kt`
- 依赖 `BatchSendSettingRepository` 读写 key `autoReply.globalEnabled`
- 方法: `isGlobalEnabled(): Boolean`, `setGlobalEnabled(enabled: Boolean)`

### Phase 2: 硬闸门

**Task 2.1: AutoMailReplyService 加全局开关检查** [I-1, I-5]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- 在 `processSingle()` 中，contact 找到之后、`autoReplyEnabled` 检查之前，插入全局开关检查
- 全局关闭时：保存入站邮件记录、保存附件、做意图分类、但不发送；confirm 状态为 `MANUAL_REVIEW`，reason=`GLOBAL_AUTO_REPLY_DISABLED`
- 新增 `SinglePipelineOutcome.GLOBAL_AUTO_REPLY_DISABLED`

### Phase 3: 新建 contact 继承

**Task 3.1: InitialOutreachService 构造 ExpertContact 时赋值** [I-3]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`
- 在 line 46 `ExpertContact(...)` 构造处加 `autoReplyEnabled = autoReplySettingService.isGlobalEnabled()`

**Task 3.2: ManualInitialOutreachService 构造 ExpertContact 时赋值** [I-3]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- 在 line 276 `ExpertContact(...)` 构造处加 `autoReplyEnabled = autoReplySettingService.isGlobalEnabled()`

### Phase 4: UI API 改造

**Task 4.1: 改造 summary API 和 bulk API** [I-4]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt`
- `getAutoReplySummary()` 增加返回 `globalEnabled` 字段
- `bulkUpdateAutoReply()` 改为调用 `autoReplySettingService.setGlobalEnabled(enabled)` + 可选同时批量更新已有 contacts

**Task 4.2: 改造前端按钮逻辑** [I-4]
- 文件: `src/main/resources/static/app.js`
- `refreshAutoReplySummary()` 根据 `summary.globalEnabled` 决定按钮文案
- 点击时调 bulk API 切换全局开关

### Phase 5: 测试

**Task 5.1: 补充 AutoMailReplyServiceTest** [I-1]
- 文件: `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
- 新增测试: 全局关闭时 processSingle 不发邮件，outcome=GLOBAL_AUTO_REPLY_DISABLED

**Task 5.2: 补充 ExpertContactManagementServiceTest** [I-4]
- 文件: `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementServiceTest.kt`
- 新增测试: summary 返回 globalEnabled、bulk 切换全局开关

## 变更文件清单

| # | 文件 | 动作 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V50__add_global_auto_reply_setting.sql` | 新增 |
| 2 | `src/main/kotlin/.../mail/service/AutoReplySettingService.kt` | 新增 |
| 3 | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | 修改 |
| 4 | `src/main/kotlin/.../campaign/service/InitialOutreachService.kt` | 修改 |
| 5 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 6 | `src/main/kotlin/.../campaign/service/ExpertContactManagementService.kt` | 修改 |
| 7 | `src/main/resources/static/app.js` | 修改 |
| 8 | `src/test/kotlin/.../mail/service/AutoMailReplyServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/.../campaign/service/ExpertContactManagementServiceTest.kt` | 修改 |

共 9 个文件，≤10 限制。

## 验收标准

- **I-1**: 单测中 mock `autoReplySettingService.isGlobalEnabled()=false`，调用 `processSingle()`，断言 outcome=`GLOBAL_AUTO_REPLY_DISABLED`，无 `mailDeliveryService.send()` 调用
- **I-2**: migration 执行后 `SELECT setting_value FROM batch_send_setting WHERE setting_key='autoReply.globalEnabled'` 返回 `'false'`；调用 `setGlobalEnabled(true)` 后返回 `'true'`
- **I-3**: 单测中全局关闭时，新建 ExpertContact 的 `autoReplyEnabled=false`
- **I-4**: 手动调用 `GET /api/expert-contacts/auto-reply/summary` 返回 `globalEnabled: false`；调用 bulk API 后 `globalEnabled` 切换
- **I-5**: 全局开→专家关→不发；全局关→专家开→不发；全局开→专家开→正常 QA 回复

## Self-Review Checklist

- [x] 关键不变量 section 有 5 条不变量，覆盖所有新状态
- [x] 现状审计列出 batch_send_setting、expert_contact.autoReplyEnabled、processSingle 入口的所有写/读路径
- [x] 没有遗漏的写路径
- [x] 文件数 = 9 ≤ 10
- [x] 子系统 = 2 (mail + campaign)
- [x] 每个 task 标注了不变量编号
- [x] 验收标准覆盖每条不变量
- [x] 没有 "and related files"
- [x] Out-of-scope 明确
- [x] Knowledge Phase 0 加载了 K-expert-contact-two-write-sites (来源: campaign)
