# P4：换绑接口 + 审计留痕 + 变更标记（决策 ④）

> 依赖 P1（绑定列 + 解析服务）、P2（绑定优先解析）。与 P3 无技术依赖、**相对顺序可换**，
> 但按主计划 M-1 **禁止与 P3 并行执行**（二者都要改 `ExpertContactRepository.kt`）。
> 主计划（跨计划约束 M-1..M-8、全局不变量 G-1..G-3）见 [00-main-plan-sender-binding.md](00-main-plan-sender-binding.md)。

## 需求描述

**Observable outcome**

1. 运营可对单个专家改绑发件账号，改绑后该专家被打上「发送账号已变更」标记
   （`expert_contact.sender_account_changed = 1`）。
2. 账号被烧掉时的**批量迁移**（把某账号名下全部专家迁到另一账号）走独立接口，
   **不打标记** —— 这是决策 ④ 的核心区分。
3. 两种操作都在 `operator_action_log` 留下逐专家的变更记录
   （`before_value` / `after_value` 携带旧/新账号 code），可在审计查询中检索。
4. 运营可清除某个专家的变更标记（标记已知悉），清除动作同样留痕。

**What must NOT change**

- 发送行为：换绑只改"下一次新发起主题邮件"的账号；
  **不重发、不追溯、不改动任何已存在的 `mail_record`**（全局 G-1）。
- 回复路径：换绑后专家回信仍进入**旧账号**信箱并由旧账号回复
  （`PendingMailOperationService` / `AutoMailReplyService` 零改动）。
- `expert_contact` 的其他列（状态、运营状态、层级、跟进标记等）不得被换绑写路径触碰。
- `operator_action_log` 既有的 15 个 action type 语义与查询筛选行为。
- ES 索引：本计划不向 ES 写任何绑定/标记字段。

**Out of scope**

- 前端 UI（换绑按钮、标记徽标、迁移入口）→ P5
- 分发打分 → P3
- 换绑时对"存在活跃会话"的硬性阻断（只记 note 提示，不阻断）
- 定时/自动触发的迁移（迁移只由运营显式调用）

## 关键不变量

### I-1: 主动换绑与批量迁移是两个 action type，标记只由前者置位
- Rule:
  - `CHANGE_SENDER_ACCOUNT("变更发送账号")` —— 单专家主动换绑，
    **必须**同时置 `sender_account_changed = 1` 与 `sender_account_changed_at = now`。
  - `MIGRATE_SENDER_ACCOUNT("迁移发送账号")` —— 按源账号批量迁移，
    **禁止**触碰 `sender_account_changed` 与 `sender_account_changed_at`（保持原值）。
  - `CLEAR_SENDER_CHANGE_MARK("清除发送账号变更标记")` —— 只把
    `sender_account_changed` 置 0、`sender_account_changed_at` 置 NULL，
    **禁止**触碰 `bound_sender_account_code`。
- Applies to: `SenderAccountBindingService` 的三个新方法；
  `OperatorActionType`（`audit/domain/OperatorActionType.kt`）。
- Violation consequence: 迁移若打标，一次账号封禁会让整批专家同时挂标，
  标记的信息量归零 —— 正是决策 ④ 要避免的。
- 来源: 决策 ④

### I-2: 三种写路径都必须走列级 UPDATE
- Rule: 换绑写 `(bound_sender_account_code, sender_account_bound_at,
  sender_account_changed, sender_account_changed_at)`；
  迁移写前两列；清标写后两列。三者各自一个 `@Modifying @Query`，
  **禁止** `repository.save(contact.copy(...))`。
- Applies to: `ExpertContactRepository` 的三个新方法。
- Violation consequence: 聚合 save 会把整行回写，覆盖同一时刻
  `ConversationStateService.transition` 或 `AutoMailReplyService` 写入的状态列。
- 来源: K-backfill-column-specific-update（severity P1）

### I-3: 目标账号必须 enabled 且非模拟器
- Rule: 换绑与迁移的**目标**账号必须 `enabled = true` 且
  `accountCode != SIMULATOR_NOOP`，否则抛 `IllegalArgumentException`。
  源账号无此要求（迁移的典型场景就是源账号已被禁用）。
- Applies to: `SenderAccountBindingService.rebind` / `migrateAccount`。
- Violation consequence: 换到一个禁用账号，P2 的门禁会立刻让该专家发不出信，
  运营在 UI 上看不出原因；模拟器目标会让邮件静默丢失（全局 G-3）。

### I-4: 审计逐专家一条，payload 只存有界元数据
- Rule: 迁移 N 个专家写 N 条 `operator_action_log`，每条带 `expert_contact_id`；
  **禁止**把 contact id 列表塞进单条日志的 `note` 或 `after_value`。
  `before` / `after` 的 JSON 只含 `{"boundSenderAccountCode": "<code>"}`
  加可选 `{"senderAccountChanged": true|false}`；不含专家姓名、邮箱、正文或任何长文本。
  `note` 长度上限 500 字符，超长截断并追加 `…(truncated)`。
- Applies to: 三个新方法对 `OperatorActionLogService.record(...)` 的调用。
- Violation consequence: `operator_action_log.note` 是 `TEXT`（V19），
  塞入上千个 id 后审计查询（`OperatorActionLogService.search`）返回体膨胀；
  且单条日志无 `expert_contact_id`，无法按专家追溯。
- 来源: K-review-event-audit-payload-bounds、K-training-evaluation-bounded-action-log

### I-5: 换绑是幂等的空操作而非重复留痕
- Rule: 目标账号与当前绑定**相同**时，直接返回，不写库、不写审计。
- Applies to: `rebind` / `migrateAccount` 的入口判定。
- Violation consequence: 前端误重复提交会刷屏审计，并把
  `sender_account_changed_at` 推到最新，扰乱"何时被改过"的判断。

### I-6: 迁移的作用域由源账号 code 唯一确定
- Rule: `migrateAccount(from, to)` 的作用集合恒为
  `WHERE bound_sender_account_code = :from`，不接受额外筛选条件
  （不按状态、不按国别、不按 id 列表）。
- Applies to: `ExpertContactRepository.migrateBindingByAccount`。
- Violation consequence: 带筛选的迁移会留下部分专家仍绑在被烧账号上，
  运营以为迁完了，实际仍有一批发不出信；且作用集合不可复现，审计无法核对。

### I-7: 换绑不改变任何已存在的邮件线程归属（全局 G-1）
- Rule: 本计划的 diff **不得包含**
  `PendingMailOperationService.kt`、`AutoMailReplyService.kt`、
  `MailRecordRepository.kt`、`MailboxService.kt`。
  存在未结会话时只在 `note` 里写一句提示，不做任何阻断或数据修改。
- Applies to: 变更文件清单。
- Violation consequence: 换绑后从新账号回复旧线程 → `In-Reply-To` 与 `From` 域不一致，
  投递失败；旧账号信箱中的线程失去回复方。
- 来源: 决策 ①

## 现状审计

### MySQL `expert_contact`（P1 后状态）

- P1 已加：`bound_sender_account_code VARCHAR(64) NULL`、
  `sender_account_bound_at DATETIME NULL`、索引 `idx_expert_contact_bound_sender`。
- 本计划再加两列。加列写法参照 `V48__add_country_to_expert_contact.sql`
  （直白 `ALTER TABLE ... ADD COLUMN`，无幂等包装；V19 的 `PREPARE` 包装是历史特例）。
- Kotlin 映射 `ExpertContact.kt`（P1 后含 33 行左右），Spring Data JDBC 需显式建模。
- **写路径全集**（P1/P2 后）：P1 的 2 处构造 + P1 的 `updateBindingById`（兜底补写）
  + 本计划新增 3 个列级 UPDATE。其余全部是 `copy(...)`。

### `operator_action_log`（`V19__add_operator_status_and_action_log.sql:31-52`）

- 列：`target_type` / `target_id` / `expert_contact_id` / `inbound_processing_id` /
  `action_type` / `action_summary VARCHAR(255)` / `before_value TEXT` / `after_value TEXT` /
  `operator_name VARCHAR(128)` / `note TEXT` / `created_at`。
- 索引：`idx_operator_action_contact_created(expert_contact_id, created_at)`、
  `idx_operator_action_type_created(action_type, created_at)`、
  `idx_operator_action_operator_created(operator_name, created_at)`。
- FK：`fk_operator_action_contact → expert_contact(id)`（逐专家写入天然满足）。
- 写入唯一入口 `OperatorActionLogService.record(...)`
  （`audit/service/OperatorActionLogService.kt:19-44`）：
  `before` / `after` 经 `objectMapper.writeValueAsString` 序列化，
  `actionSummary` 默认取 `actionType.summary`，支持 `summaryOverride`。
- 现有 action type 15 个（`audit/domain/OperatorActionType.kt:3-18`），
  本计划新增 3 个，尾部追加。

- **既有调用范式**（本计划照抄）：`ExpertOperatorStatusService.kt:31-41`
  ```kotlin
  operatorActionLogService.record(
      targetType = "EXPERT_CONTACT", targetId = contactId,
      actionType = OperatorActionType.CHANGE_OPERATOR_STATUS,
      expertContactId = contactId,
      before = mapOf("operatorStatus" to oldStatus),
      after = mapOf("operatorStatus" to target.name),
      operatorName = operatorName, note = note
  )
  ```

### `ExpertContactManagementController`（`campaign/controller/`，584 行）

- `@RequestMapping("/api/expert-contacts")`。
- 同形状的既有端点（本计划照抄其请求体与调用范式）：
  - `POST /{contactId}/operator-status`（`:146-153`）+ `ChangeOperatorStatusRequest`（`:254-258`）
  - `POST /{contactId}/index-level`（`:155-162`）+ `ChangeIndexLevelRequest`（`:260-264`）
  - `POST /{contactId}/switch-to-manual`（`:132-137`）+ `SwitchToManualRequest`（`:243-247`）
- 请求体统一带 `operatorName: String?` / `note: String?`（见 `:254-264`）。
- 响应统一 `ExpertContactResponse`（`:376-397`），本计划**不改**该 DTO
  （新增字段的 DTO 暴露属于 P5）。

### 异常映射（`common/controller/GlobalExceptionHandler.kt`）

- `IllegalArgumentException` → 400 `BAD_REQUEST`（`:14-16`）
- `IllegalStateException` → 400 `BAD_REQUEST`（`:18-20`）
- 其他 `Exception` → 500 `INTERNAL_ERROR`（`:38-40`）
- 因此 I-3 的校验必须用 `require(...)`（抛 `IllegalArgumentException`），
  才能返回可读的 400 而非 500。

### Interaction points

- **IP-1**：本计划的换绑（写 `bound_sender_account_code`）×
  P2 的 `resolveForSend`（读）—— 换绑后**下一次**新发起主题邮件即生效，
  无缓存、无预热；但已在 `@Transactional` 中的发送不受影响。
- **IP-2**：本计划的迁移（批量写）× P3 的存量快照（读）——
  迁移会瞬间改变存量分布；P3 的快照在**批次开始**时取，
  迁移发生在批次进行中时快照会陈旧一轮。这是可接受的（快照本就是近似），
  但必须在验收中确认迁移不会导致进行中的批量任务报错。
- **IP-3**：本计划的标记列（写）× P5 的列表 DTO（读）——
  P5 未上线前标记不可见，只能查库验证。本计划的人工验收因此全部走 SQL。

## 实现方案

### 阶段 1 — 数据层

**T1.1 新建 `V86__add_expert_contact_sender_change_mark.sql`**（遵 I-1）

```sql
-- V86: 发送账号「主动变更」标记。
-- 只由运营单专家主动换绑置位；账号被禁用后的批量迁移不置位（决策 ④）。
ALTER TABLE expert_contact
    ADD COLUMN sender_account_changed TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '发送账号是否被运营主动变更过；批量迁移不置位',
    ADD COLUMN sender_account_changed_at DATETIME NULL
    COMMENT '最近一次主动变更时间';
```

**无回填**：历史换号（V85 之前 mail_record 里体现的换号）一律视为系统行为，
不算"运营主动变更"（决策 ④）。

**T1.2 `ExpertContact.kt` 加两字段**

紧跟 P1 加入的两字段之后：

```kotlin
    val senderAccountChanged: Boolean = false,
    val senderAccountChangedAt: LocalDateTime? = null,
```

**T1.3 `ExpertContactRepository.kt` 加三个列级更新**（遵 I-2/I-6）

```kotlin
    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :changedAt,
               sender_account_changed = true,
               sender_account_changed_at = :changedAt
         WHERE id = :id
    """)
    fun rebindSenderAccountById(id: Long, accountCode: String, changedAt: LocalDateTime): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :toAccountCode,
               sender_account_bound_at = :migratedAt
         WHERE bound_sender_account_code = :fromAccountCode
    """)
    fun migrateBindingByAccount(
        fromAccountCode: String,
        toAccountCode: String,
        migratedAt: LocalDateTime
    ): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET sender_account_changed = false,
               sender_account_changed_at = NULL
         WHERE id = :id
    """)
    fun clearSenderChangeMarkById(id: Long): Int

    fun findAllByBoundSenderAccountCode(boundSenderAccountCode: String): List<ExpertContact>
```

`findAllByBoundSenderAccountCode` 用于迁移前取快照以逐专家写审计（I-4）。

**T1.4 `OperatorActionType.kt` 追加三个枚举**（遵 I-1，尾部追加不改既有顺序）

```kotlin
    CHANGE_SENDER_ACCOUNT("变更发送账号"),
    MIGRATE_SENDER_ACCOUNT("迁移发送账号"),
    CLEAR_SENDER_CHANGE_MARK("清除发送账号变更标记")
```

### 阶段 2 — 服务

**T2.1 `SenderAccountBindingService.kt` 新增三个方法**

注入 `operatorActionLogService: OperatorActionLogService` 与
`mailRecordRepository`（仅用于活跃会话提示，只读）。

```kotlin
    @Transactional
    fun rebind(contactId: Long, command: RebindCommand): ExpertContact {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
        val target = requireEnabledTarget(command.senderAccountCode)          // I-3
        val old = contact.boundSenderAccountCode
        if (old == target.accountCode) return contact                         // I-5

        val now = LocalDateTime.now()
        expertContactRepository.rebindSenderAccountById(contactId, target.accountCode, now)  // I-2

        operatorActionLogService.record(                                      // I-4
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.CHANGE_SENDER_ACCOUNT,
            expertContactId = contactId,
            before = mapOf("boundSenderAccountCode" to old),
            after = mapOf("boundSenderAccountCode" to target.accountCode),
            operatorName = command.operatorName,
            note = boundedNote(command.note, activeThreadHint(contact, old))  // I-4/I-7
        )
        return expertContactRepository.findById(contactId).orElseThrow()
    }

    @Transactional
    fun migrateAccount(command: MigrateCommand): MigrateResult {
        val target = requireEnabledTarget(command.toAccountCode)              // I-3
        require(command.fromAccountCode.isNotBlank()) { "fromAccountCode is required" }
        require(command.fromAccountCode != target.accountCode) {              // I-5
            "源账号与目标账号相同，无需迁移"
        }
        val affected = expertContactRepository
            .findAllByBoundSenderAccountCode(command.fromAccountCode)         // I-6
        if (affected.isEmpty()) return MigrateResult(0, command.fromAccountCode, target.accountCode)

        val now = LocalDateTime.now()
        val updated = expertContactRepository.migrateBindingByAccount(
            command.fromAccountCode, target.accountCode, now
        )                                                                     // I-2/I-6

        affected.forEach { c ->                                               // I-4：逐专家一条
            operatorActionLogService.record(
                targetType = "EXPERT_CONTACT",
                targetId = c.id!!,
                actionType = OperatorActionType.MIGRATE_SENDER_ACCOUNT,
                expertContactId = c.id,
                before = mapOf("boundSenderAccountCode" to command.fromAccountCode),
                after = mapOf("boundSenderAccountCode" to target.accountCode),
                operatorName = command.operatorName,
                note = boundedNote(command.reason, null)
            )
        }
        return MigrateResult(updated, command.fromAccountCode, target.accountCode)
    }

    @Transactional
    fun clearChangeMark(contactId: Long, operatorName: String?, note: String?): ExpertContact { ... }
```

辅助方法：

```kotlin
    private fun requireEnabledTarget(code: String): MailSenderAccount {       // I-3
        require(code.isNotBlank()) { "senderAccountCode is required" }
        require(code != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            "模拟器账号不可作为绑定目标"
        }
        val account = mailSenderAccountService.getAccount(code)
        require(account.enabled) { "目标发件账号已禁用，不可绑定：$code" }
        return account
    }

    /** I-7：只提示，不阻断、不改数据。 */
    private fun activeThreadHint(contact: ExpertContact, oldCode: String?): String? {
        if (oldCode == null) return null
        val terminal = setOf("NEW", "MANUAL_HANDOFF")
        if (contact.currentStatus in terminal) return null
        return "存在进行中的会话（${contact.currentStatus}），该专家的回复仍由 $oldCode 处理"
    }

    /** I-4：note 有界。 */
    private fun boundedNote(note: String?, hint: String?): String? {
        val merged = listOfNotNull(note?.trim()?.takeIf { it.isNotEmpty() }, hint)
            .joinToString(" | ")
            .takeIf { it.isNotEmpty() } ?: return null
        return if (merged.length <= NOTE_MAX) merged
               else merged.take(NOTE_MAX) + "…(truncated)"
    }
    // companion object { const val NOTE_MAX = 500 }
```

命令/结果类型（同文件末尾）：

```kotlin
data class RebindCommand(val senderAccountCode: String, val operatorName: String?, val note: String?)
data class MigrateCommand(
    val fromAccountCode: String, val toAccountCode: String,
    val operatorName: String?, val reason: String?
)
data class MigrateResult(val migrated: Int, val fromAccountCode: String, val toAccountCode: String)
```

> `terminal` 集合的取值依据：`common/domain/ConversationStatus`。
> `NEW` = 尚未发过信（无线程），`MANUAL_HANDOFF` = 已交人工（不再自动回复）。
> 若 `ConversationStatus` 的成员与此不符，以该枚举实际定义为准并在实现时修正注释。

### 阶段 3 — 接口

**T3.1 `ExpertContactManagementController.kt` 加三个端点**

照抄 `changeOperatorStatus`（`:146-153`）的形状，放在其后：

```kotlin
    @PostMapping("/{contactId}/sender-account")
    fun rebindSenderAccount(
        @PathVariable contactId: Long,
        @RequestBody request: RebindSenderAccountRequest
    ): ExpertContactResponse =
        toResponse(senderAccountBindingService.rebind(contactId, request.toCommand()))

    @PostMapping("/{contactId}/sender-account/clear-change-mark")
    fun clearSenderChangeMark(
        @PathVariable contactId: Long,
        @RequestBody request: ClearSenderChangeMarkRequest
    ): ExpertContactResponse =
        toResponse(senderAccountBindingService
            .clearChangeMark(contactId, request.operatorName, request.note))

    @PostMapping("/sender-account/migrate")
    fun migrateSenderAccount(@RequestBody request: MigrateSenderAccountRequest): MigrateResult =
        senderAccountBindingService.migrateAccount(request.toCommand())
```

> `/sender-account/migrate` 是**集合级**端点，必须放在 `/{contactId}/...` 之前
> 或使用不冲突的路径段；`migrate` 不是数字，Spring 的 `@PathVariable Long contactId`
> 不会误匹配，但为可读性建议置于文件的单专家端点之后、批量端点区域（`:175` 附近）。

请求体（追加到 `ChangeIndexLevelRequest`（`:260-264`）之后）：

```kotlin
data class RebindSenderAccountRequest(
    val senderAccountCode: String,
    val operatorName: String? = null,
    val note: String? = null
) { fun toCommand() = RebindCommand(senderAccountCode, operatorName, note) }

data class ClearSenderChangeMarkRequest(
    val operatorName: String? = null,
    val note: String? = null
)

data class MigrateSenderAccountRequest(
    val fromAccountCode: String,
    val toAccountCode: String,
    val operatorName: String? = null,
    val reason: String? = null
) { fun toCommand() = MigrateCommand(fromAccountCode, toAccountCode, operatorName, reason) }
```

`toResponse(...)` 复用控制器内既有的 `ExpertContact → ExpertContactResponse` 映射，
**不新增字段**（DTO 扩展在 P5）。

### 阶段 4 — 测试

**T4.1 `SenderAccountBindingServiceTest.kt`（P1 已建）追加**

| 用例 | 断言 |
|---|---|
| `rebind sets change mark and writes audit` | `verify(repo).rebindSenderAccountById(...)`；`verify(logService).record(actionType=CHANGE_SENDER_ACCOUNT, before/after 含旧新 code)`（I-1/I-2/I-4） |
| `rebind is a no-op when target equals current binding` | `verify(repo, never()).rebindSenderAccountById(any(), any(), any())` 且 `verify(logService, never()).record(...)`（I-5） |
| `rebind rejects disabled target` | 抛 `IllegalArgumentException`，无库写、无审计（I-3） |
| `rebind rejects simulator target` | 同上（I-3、全局 G-3） |
| `rebind note is truncated at 500 chars` | 传 600 字 note，断言落库 note 长度 = 500 + `…(truncated)`（I-4） |
| `rebind appends active thread hint` | `currentStatus="WAITING_REPLY"` → note 含 `存在进行中的会话`（I-7） |
| `migrate does not touch change mark` | `verify(repo).migrateBindingByAccount(...)`；`verify(repo, never()).rebindSenderAccountById(...)`（I-1） |
| `migrate writes one audit row per contact` | 3 个专家 → `verify(logService, times(3)).record(...)`，且每条 `expertContactId` 不同（I-4） |
| `migrate rejects same source and target` | 抛 `IllegalArgumentException`（I-5） |
| `migrate with no affected contacts writes nothing` | `migrated == 0`，无审计（I-6） |
| `migrate scope is source account only` | `verify(repo).findAllByBoundSenderAccountCode("X")`，无其他筛选参数（I-6） |
| `clearChangeMark only clears mark columns` | `verify(repo).clearSenderChangeMarkById(id)`；`verify(repo, never()).rebindSenderAccountById(...)`（I-1/I-2） |

**T4.2 `ExpertContactManagementServiceTest.kt` 不改**（本计划未动该服务）。
如需控制器层用例，追加到 `SenderAccountBindingServiceTest` 中的服务级断言即可，
**不新建控制器测试类**（保持文件数）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V86__add_expert_contact_sender_change_mark.sql` | 新增 | 两列，无回填 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt` | 修改 | 加 2 字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt` | 修改 | 3 个列级 UPDATE + 1 个 finder |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/audit/domain/OperatorActionType.kt` | 修改 | 尾部加 3 个枚举 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt` | 修改 | rebind / migrate / clearChangeMark + 命令类型 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 | 3 个端点 + 3 个请求体 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingServiceTest.kt` | 修改 | +12 例 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | 修改 | 编译修复：`:17` 命名参数构造补 `senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)`（A6 授权，不改断言） |

文件数 8 ≤ 10 ✓　子系统 1（后端）≤ 2 ✓
新增共享存储字段：1 个逻辑字段（变更标记，含其时间戳）✓

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。
> 来源：项目根 `CLAUDE.md`「Commands」+「项目元信息」。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=SenderAccountBindingServiceTest

# 单方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='SenderAccountBindingServiceTest#migrate does not touch change mark'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`，`BUILD SUCCESS`。
来源：`CLAUDE.md` 项目元信息；过滤语法取自「Commands」章节示例。

## 验收标准

- **I-1**: `migrateBindingByAccount` 的 SQL SET 子句**不含**
  `sender_account_changed`；`rebindSenderAccountById` 的 SET 子句**含**
  `sender_account_changed = true`；`clearSenderChangeMarkById` 的 SET 子句
  **不含** `bound_sender_account_code`。三条对应用例通过。
- **I-2**: `grep -n "@Modifying" ExpertContactRepository.kt` 覆盖三个新方法；
  `SenderAccountBindingService` 的三个新方法体内不出现 `expertContactRepository.save(`。
- **I-3**: `rebind rejects disabled target` 与 `rebind rejects simulator target` 通过；
  `requireEnabledTarget` 用 `require(...)` 而非 `check(...)`（保证 400 而非 500）。
- **I-4**: `migrate writes one audit row per contact` 与
  `rebind note is truncated at 500 chars` 通过；
  `record(...)` 的 `before` / `after` 实参只含 `boundSenderAccountCode` 键。
- **I-5**: `rebind is a no-op when target equals current binding` 与
  `migrate rejects same source and target` 通过。
- **I-6**: `migrateBindingByAccount` 的 WHERE 子句恰为
  `bound_sender_account_code = :fromAccountCode`，无其他条件。
- **I-7**: `git diff --name-only` 不含 `PendingMailOperationService.kt`、
  `AutoMailReplyService.kt`、`MailRecordRepository.kt`、`MailboxService.kt`。
- **回归**: 执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 主动换绑生效并打标
- 前置条件: 一位绑定账号为 `A` 的专家（contact id 记为 `<id>`），
  另有 enabled 账号 `B`；记录当前
  `SELECT bound_sender_account_code, sender_account_changed FROM expert_contact WHERE id=<id>;`
- 操作步骤:
  1. `curl -X POST http://<host>/api/expert-contacts/<id>/sender-account
     -H 'Content-Type: application/json'
     -d '{"senderAccountCode":"B","operatorName":"验收人","note":"账号A退信率高"}'`
  2. 复查上面的 SELECT。
  3. `SELECT action_type, before_value, after_value, operator_name, note
      FROM operator_action_log WHERE expert_contact_id=<id> ORDER BY id DESC LIMIT 1;`
- 预期结果:
  - 步骤 2：`bound_sender_account_code='B'`，`sender_account_changed=1`，
    `sender_account_changed_at` 为当前时刻。
  - 步骤 3：`action_type='CHANGE_SENDER_ACCOUNT'`，
    `before_value={"boundSenderAccountCode":"A"}`，
    `after_value={"boundSenderAccountCode":"B"}`，`operator_name='验收人'`。
- 覆盖: I-1、I-2、I-4、需求描述第 1、3 条

### A-2: 换绑后新邮件从新账号发出，旧线程回复仍走旧账号
- 前置条件: A-1 已完成（该专家绑定已为 `B`），且该专家在 `A` 的信箱里有历史往来。
- 操作步骤:
  1. 在专家详情页发送一封模板邮件。
  2. 让该专家回复历史线程中的邮件（或手工往 `A` 的信箱投一封来自该专家的信），
     触发一次收信，然后在收发件箱做一次人工回复。
- 预期结果:
  - 步骤 1：收件人收到的邮件 From 是 `B` 的地址。
  - 步骤 2：来信出现在 `A` 的收件列表；人工回复发出后，
    该回复记录的「账号」列为 `A`（**不是** `B`）。
- 覆盖: I-7、全局 G-1、决策 ①、must-NOT-change 第 1、2 条

### A-3: 批量迁移不打标
- 前置条件: 账号 `A` 名下有 ≥3 位专家
  （`SELECT COUNT(*) FROM expert_contact WHERE bound_sender_account_code='A';` 记为 N）；
  其中至少一位 `sender_account_changed=0`、至少一位为 1（用 A-1 造）。
  另有 enabled 账号 `C`。
- 操作步骤:
  1. `curl -X POST http://<host>/api/expert-contacts/sender-account/migrate
     -H 'Content-Type: application/json'
     -d '{"fromAccountCode":"A","toAccountCode":"C","operatorName":"验收人","reason":"A账号被封"}'`
  2. `SELECT id, bound_sender_account_code, sender_account_changed
      FROM expert_contact WHERE bound_sender_account_code='C';`
  3. `SELECT COUNT(*) FROM operator_action_log
      WHERE action_type='MIGRATE_SENDER_ACCOUNT' AND created_at > <步骤1时刻>;`
  4. `SELECT COUNT(*) FROM expert_contact WHERE bound_sender_account_code='A';`
- 预期结果:
  - 步骤 1 响应体 `{"migrated": N, "fromAccountCode":"A", "toAccountCode":"C"}`。
  - 步骤 2：这 N 位的 `sender_account_changed` **保持迁移前的值**
    （原本 0 的仍是 0，原本 1 的仍是 1）。
  - 步骤 3：计数恰为 N（逐专家一条）。
  - 步骤 4：返回 0（源账号名下清空，I-6）。
- 覆盖: I-1、I-4、I-6、需求描述第 2、3 条、决策 ④

### A-4: 目标账号禁用时换绑被拒
- 前置条件: 账号 `D` 处于 `enabled=0`。
- 操作步骤: `curl -i -X POST http://<host>/api/expert-contacts/<id>/sender-account
  -d '{"senderAccountCode":"D"}' -H 'Content-Type: application/json'`
- 预期结果: HTTP **400**，响应体 `code="BAD_REQUEST"`，
  `message` 包含「目标发件账号已禁用」与 `D`；
  该专家的 `bound_sender_account_code` 未变；无新增 `operator_action_log`。
- 覆盖: I-3

### A-5: 重复换绑到同一账号不留痕
- 前置条件: 某专家当前绑定 `C`。
- 操作步骤:
  1. 记录 `SELECT COUNT(*) FROM operator_action_log WHERE expert_contact_id=<id>;`
  2. 对该专家再次换绑到 `C`。
  3. 复查步骤 1 的计数与 `sender_account_changed_at`。
- 预期结果: 计数不变；`sender_account_changed_at` 不变；接口返回 200。
- 覆盖: I-5

### A-6: 清除变更标记
- 前置条件: 一位 `sender_account_changed=1` 的专家。
- 操作步骤:
  1. `curl -X POST http://<host>/api/expert-contacts/<id>/sender-account/clear-change-mark
     -d '{"operatorName":"验收人","note":"已知悉"}' -H 'Content-Type: application/json'`
  2. `SELECT bound_sender_account_code, sender_account_changed, sender_account_changed_at
      FROM expert_contact WHERE id=<id>;`
  3. 查最新一条 `operator_action_log`。
- 预期结果: `sender_account_changed=0`、`sender_account_changed_at IS NULL`、
  **`bound_sender_account_code` 完全未变**；日志 `action_type='CLEAR_SENDER_CHANGE_MARK'`。
- 覆盖: I-1、I-2、需求描述第 4 条

### A-7（回归）: 换绑不触碰其他列
- 前置条件: 记录某专家换绑前的
  `SELECT current_status, operator_status, current_index_level, follow_up_marked,
   auto_reply_enabled, needs_manual_attention FROM expert_contact WHERE id=<id>;`
- 操作步骤: 对其执行一次换绑，再查同一组列。
- 预期结果: 六个值逐一不变。
- 覆盖: I-2、must-NOT-change 第 3 条

### A-8（回归）: 迁移进行中的批量任务不报错
- 前置条件: 启动一个大批量首封或材料提醒任务（预计运行 ≥1 分钟）。
- 操作步骤: 任务运行期间执行一次 A-3 的迁移。
- 预期结果: 任务不因迁移而 FAILED；最终状态为 `COMPLETED` 或 `PAUSED`
  （PAUSED 仅允许因额度/账号不可用等既有原因）；
  错误列表中不出现空指针、外键或并发修改类异常。
- 覆盖: IP-2
