# Plan B: Warmup 配置化与分发策略优化

> 日期: 2026-06-24
> 前置: Plan A — 发件账号生命周期改进（可独立部署，但建议先完成 A）
> 后续: 无

---

## 需求描述

**可观测结果**：(1) 每个发件账号可独立开启/关闭 warmup，设置独立的 warmup 起始时间和阶梯步骤，不依赖全局开关；(2) 前端发件账号列表显示"有效限额"而非静态的 dailySendLimit；(3) 批量分发时国家分布惩罚值与 strategyWeight 成比例，避免低权重账号被惩罚项淹没。

**不可变更**：
- 全局 `WarmupProperties`（`talent-introduction.warmup.*`）作为新账号的默认值保留，未开启 per-account warmup 的账号仍走全局配置
- `SenderWarmupService.effectiveDailyLimit()` 的返回值语义不变（返回当日可发送上限）
- `SenderAccountAssignmentService.selectAccount()` 的方法签名不变
- `isSendable()` 中 `todaySentCount < effectiveLimit` 的核心判断不变

**不在范围**：
- Warmup 前端独立管理页面（仅在现有账号编辑表单中增加 warmup 区域）
- Warmup 步骤的图形化编辑器
- 按 provider 限流（已有独立计划）
- Google Postmaster Tools API 接入

---

## 关键不变量

### Invariant I-1: Per-account warmup 优先级
- Rule: `SenderWarmupService.effectiveDailyLimit()` 判断优先级为：(1) 若 `account.warmupEnabled == true` 且 `account.warmupStartedAt != null`，使用 account 级别的 warmupSteps 和 warmupStartedAt 计算；(2) 若 `account.warmupEnabled == false`（或 null），回退到全局 `WarmupProperties.enabled` + `WarmupProperties.steps` + `account.createdAt`（现有行为）；(3) 无论哪条路径，最终 `return minOf(account.dailySendLimit, rampLimit)`。
- Applies to: `SenderWarmupService.effectiveDailyLimit()`
- Violation consequence: warmup 限额计算错误，要么发送过多触发风控，要么发送过少延误业务。

### Invariant I-2: warmupSteps JSON 格式
- Rule: `warmup_steps_json` 列存储 JSON 数组格式 `[{"dayFrom":1,"limit":20},{"dayFrom":3,"limit":40}]`。为 NULL 时表示使用全局默认步骤。非 NULL 时必须能被解析为 `List<WarmupStep>`。写入时由 service 层序列化，读取时由 service 层反序列化。`MailSenderAccount` domain class 中该字段类型为 `String?`（Spring Data JDBC 不支持自动 JSON 转换）。
- Applies to: `MailSenderAccountService.updateAccount()`, `SenderWarmupService.effectiveDailyLimit()`
- Violation consequence: JSON 格式错误导致反序列化失败，warmup 计算异常。

### Invariant I-3: 有效限额显示
- Rule: `MailSenderAccountResponse` 新增 `effectiveDailyLimit: Int` 字段，值等于 `SenderWarmupService.effectiveDailyLimit(account)`。前端列表中"今日额度"列显示 `todaySentCount/effectiveDailyLimit` 而非 `todaySentCount/dailySendLimit`。
- Applies to: `MailSenderAccountController.toResponse()`, `app.js loadAccounts()`
- Violation consequence: 运营看到的限额与实际执行限额不一致，误判账号容量。

### Invariant I-4: 分发惩罚值与权重成比例
- Rule: `SenderAccountAssignmentService.assignmentScore()` 中国家分布惩罚和总量惩罚改为 strategyWeight 的百分比：`sameSegmentPenalty = account.strategyWeight * 0.2 * sameSegmentCount`，`totalPenalty = account.strategyWeight * 0.02 * totalAccountCount`。不再使用硬编码常数 20 和 2。
- Applies to: `SenderAccountAssignmentService.assignmentScore()`
- Violation consequence: 低权重账号（如 strategyWeight=10）被常数惩罚 20 直接打入负分，永远不被选中。

---

## 现状审计

### mail_sender_account 表
- Schema: V1 创建 + V28 加 auto_pause 字段。当前无 warmup 相关列。
- Write paths (warmup 相关):
  1. `MailSenderAccountService.createAccount()` — 新建时 warmup 字段应取默认值
  2. `MailSenderAccountService.updateAccount()` — 需支持更新 warmup 字段
- Read paths (warmup 相关):
  1. `SenderWarmupService.effectiveDailyLimit()` — 读取 warmup 配置计算限额
  2. `MailSenderAccountService.isSendable()` — 通过 effectiveDailyLimit 间接读取
  3. `MailSenderAccountService.selectionScore()` — 通过 effectiveDailyLimit 间接读取
  4. `SenderAccountAssignmentService.selectAccount()` — 通过 effectiveDailyLimit 间接读取
  5. `MailSenderAccountController.toResponse()` — 需新增 effectiveDailyLimit 返回
- Interaction points:
  - write path 2 × read path 1: updateAccount 写入的 warmup 字段直接影响 effectiveDailyLimit 计算
  - read path 1 × read paths 2/3/4: effectiveDailyLimit 变化传播到所有发送/分发决策

### WarmupProperties 全局配置
- 当前 `application.yml` 中 `talent-introduction.warmup.enabled: ${WARMUP_ENABLED:false}`
- 默认步骤硬编码在 `WarmupProperties` data class 中
- Read paths: `SenderWarmupService.effectiveDailyLimit()` 唯一消费者
- 计划保留作为全局默认值，per-account 优先

### SenderAccountAssignmentService 评分
- 当前 `assignmentScore()` 使用 `sameSegmentCount * 20.0` 和 `totalAccountCount * 2.0` 硬编码
- 当 strategyWeight=10 时，baseScore 最大为 10，但单次 sameSegment 惩罚就是 20，直接打入负分
- Read paths: `selectAccount()` 是唯一调用者

---

## 实现方案

### 阶段 1: 数据库迁移 (I-2)

**Task 1.1**: 创建 V34 迁移
- 文件: `src/main/resources/db/migration/V34__add_sender_account_warmup_fields.sql`
- 内容:
  ```sql
  ALTER TABLE mail_sender_account
      ADD COLUMN warmup_enabled TINYINT(1) NULL DEFAULT NULL,
      ADD COLUMN warmup_started_at DATETIME NULL DEFAULT NULL,
      ADD COLUMN warmup_steps_json TEXT NULL DEFAULT NULL;
  ```
- `warmup_enabled` NULL = 使用全局配置, 0 = 关闭, 1 = 开启
- 遵守: I-2

**Task 1.2**: 更新 domain class
- 文件: `MailSenderAccount.kt`
- 添加字段: `warmupEnabled: Boolean? = null`, `warmupStartedAt: LocalDateTime? = null`, `warmupStepsJson: String? = null`
- 遵守: I-2

### 阶段 2: Warmup 服务改造 (I-1, I-2)

**Task 2.1**: 重构 `SenderWarmupService.effectiveDailyLimit()`
- 文件: `SenderWarmupService.kt`
- 逻辑:
  1. 若 `account.warmupEnabled == true`: 使用 `account.warmupStartedAt` 作为起始日，使用 `parseSteps(account.warmupStepsJson)` 获取步骤（为 null 时用 `props.steps` 默认值）
  2. 若 `account.warmupEnabled == null` 或 `false`: 走现有逻辑（检查 `props.enabled`，用 `account.createdAt`，用 `props.steps`）
  3. 最终 `return minOf(account.dailySendLimit, rampLimit)`
- 添加私有方法 `parseSteps(json: String?): List<WarmupStep>` 做 JSON 反序列化，用 Jackson ObjectMapper
- 遵守: I-1, I-2

### 阶段 3: API 与前端 (I-3)

**Task 3.1**: 更新 Controller DTOs
- 文件: `MailSenderAccountController.kt`
- `MailSenderAccountResponse` 新增: `effectiveDailyLimit: Int`, `warmupEnabled: Boolean?`, `warmupStartedAt: String?`, `warmupStepsJson: String?`
- `MailSenderAccountUpdateRequest` 新增: `warmupEnabled: Boolean? = null`, `warmupStartedAt: String? = null`, `warmupStepsJson: String? = null`
- `MailSenderAccountUpdateCommand` 新增对应字段
- `toResponse()` 中 `effectiveDailyLimit` 值从注入的 `SenderWarmupService.effectiveDailyLimit(account)` 获取。需在 Controller 注入 `SenderWarmupService`，或在 `MailSenderAccountService` 中提供一个 `toResponseWithEffectiveLimit()` 方法。推荐后者，避免 Controller 直接依赖 warmup service。
- 遵守: I-3

**Task 3.2**: 更新 Service CRUD
- 文件: `MailSenderAccountService.kt`
- `updateAccount()` 中 `existing.copy(...)` 增加 warmup 字段映射
- `createAccount()` 中 toDomain() 无需变更（新字段默认 null）
- 添加方法 `getAccountWithEffectiveLimit(account): Pair<MailSenderAccount, Int>` 或让 Controller 自行调用 warmup service
- 遵守: I-1, I-3

**Task 3.3**: 前端有效限额显示
- 文件: `app.js`
- `loadAccounts()` 中 `${account.todaySentCount}/${account.dailySendLimit}` 改为 `${account.todaySentCount}/${account.effectiveDailyLimit}`
- 若 `effectiveDailyLimit < dailySendLimit`（warmup 生效中），在数字后显示 warmup 标记，如 `<span class="badge info">预热中</span>`
- 遵守: I-3

**Task 3.4**: 前端 warmup 控制区域
- 文件: `app.js`
- 在账号编辑表单中增加 warmup 配置区域：checkbox "启用独立预热"、日期选择器 "预热起始日"、textarea "预热步骤(JSON)"
- 编辑已有账号时回填当前值
- 遵守: I-1, I-2

### 阶段 4: 分发策略修正 (I-4)

**Task 4.1**: 修改惩罚公式
- 文件: `SenderAccountAssignmentService.kt`
- 原: `baseScore - sameSegmentCount * 20.0 - totalAccountCount * 2.0`
- 改: `baseScore - account.strategyWeight * 0.2 * sameSegmentCount - account.strategyWeight * 0.02 * totalAccountCount`
- 效果: strategyWeight=100 时惩罚为 20/2（与现有一致），strategyWeight=10 时惩罚为 2/0.2（合理比例）
- 遵守: I-4

---

## 变更文件清单

| # | 文件 | 变更类型 | 涉及不变量 |
|---|------|----------|-----------|
| 1 | `src/main/resources/db/migration/V34__add_sender_account_warmup_fields.sql` | 新增 | I-2 |
| 2 | `src/main/kotlin/.../mail/domain/MailSenderAccount.kt` | 修改 | I-2 |
| 3 | `src/main/kotlin/.../mail/service/SenderWarmupService.kt` | 修改 | I-1, I-2 |
| 4 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | 修改 | I-1, I-3 |
| 5 | `src/main/kotlin/.../mail/controller/MailSenderAccountController.kt` | 修改 | I-3 |
| 6 | `src/main/kotlin/.../mail/service/SenderAccountAssignmentService.kt` | 修改 | I-4 |
| 7 | `src/main/resources/static/app.js` | 修改 | I-3 |

**共 7 个文件（含 1 个新迁移），1 个子系统。**

> 注：warmup_enabled / warmup_started_at / warmup_steps_json 三个字段属于一个逻辑单元（per-account warmup 配置），由 `SenderWarmupService.effectiveDailyLimit()` 统一消费，不独立影响其他读路径。

---

## 验收标准

- **I-1**:
  - 账号 `warmupEnabled=true, warmupStartedAt=3天前, warmupStepsJson=[{"dayFrom":1,"limit":10},{"dayFrom":5,"limit":50}]`：effectiveDailyLimit 应为 `min(dailySendLimit, 10)`
  - 账号 `warmupEnabled=null`，全局 `warmup.enabled=true`：行为与改造前一致（用 createdAt + 全局 steps）
  - 账号 `warmupEnabled=false`：不论全局开关，effectiveDailyLimit = dailySendLimit
  - 账号 `warmupEnabled=true, warmupStepsJson=null`：使用全局默认步骤 + account.warmupStartedAt

- **I-2**:
  - warmupStepsJson 存入 DB 为合法 JSON 字符串
  - warmupStepsJson = NULL 时不抛反序列化异常
  - 无效 JSON 写入时（手工输入）应在 service 层校验并拒绝

- **I-3**:
  - GET /api/mail/sender-accounts 响应中每个账号包含 `effectiveDailyLimit` 字段
  - warmup 生效时 `effectiveDailyLimit < dailySendLimit`，前端显示预热标记
  - warmup 未生效时 `effectiveDailyLimit == dailySendLimit`

- **I-4**:
  - strategyWeight=10 的账号在有 sameSegment 记录时仍能获得正分（不被惩罚淹没）
  - strategyWeight=100 的账号在 sameSegmentCount=1 时惩罚值为 20（与原行为一致）
  - 两个账号 strategyWeight 差 10 倍时，分发比例大致为 10:1
