# P2：发送路径按绑定强一致解析 + enabled 门禁

> 依赖 P1 已落地（`bound_sender_account_code` 列、`SenderAccountBindingService`）。
> 主计划（跨计划约束 M-1..M-8、全局不变量 G-1..G-3、既有知识冲突口径）见
> [00-main-plan-sender-binding.md](00-main-plan-sender-binding.md)。

## 需求描述

**Observable outcome**

1. 已绑定专家的**新发起主题邮件**一律由绑定账号发出：单封人工发送、会议确认邮件、
   材料提醒批量、批量首封复用已有 contact 时，均不再重新选号。
2. 绑定账号 `enabled=0` 时，发送**报错拦截**并返回明确原因，不降级换号
   —— 这是 2026-08-10 `LiLei` 事故的直接修复。
3. `ManualExpertMailService` 收到显式 `senderAccountCode` 且与绑定不一致时报错，
   不再静默按调用方指定的账号发送。

**What must NOT change**

- **回复路径**：`PendingMailOperationService.kt:642-647`（人工回复用收信账号）与
  `AutoMailReplyService.processSingle(account, ...)`（自动回复用收信账号）
  完全不改，禁用账号仍能收信并回复（全局 G-1 + K-sender-account-enabled-scope 原始场景）。
- **人工发送脱离每日配额**：人工路径不得新增 `todaySentCount` 判定
  （K-operator-send-quota-paths，锁定测试 `MailSenderAccountServiceTest.kt:35-46`）。
- **人工发送不受自动暂停阻塞**：`autoSendPaused=true` 的账号人工仍可发
  （锁定测试 `MailSenderAccountServiceTest.kt:48-57`）。
- `todaySentCount` 的四条写路径本身（K-operator-send-quota-paths 记载）数量与位置不变。
- 无绑定的历史 contact 仍能正常发送（走兜底选号 + 补写绑定），不得直接报错。

**Out of scope**

- 分发打分计入存量绑定 → P3
- 换绑接口与变更标记 → P4
- 前端 → P5
- `selectAccountForManualSending()` 的删除（保留为兜底，只修其 `enabled` 谓词）

## 关键不变量

### I-1: 绑定优先于一切选号（新发起主题）
- Rule: 对**已有 contact** 发起新主题邮件时，账号解析顺序恒为
  `resolveForSend(contact, manual)` → 抛 `SenderAccountNotBoundException` 才允许兜底选号；
  兜底成功后必须立刻 `bindIfAbsent` 补写绑定。任何路径不得先选号再看绑定。
- Applies to: `ManualExpertMailService.sendManualMail`、
  `MeetingScheduleService.confirmMeetingAndEmail`、
  `ManualInitialOutreachService` 材料提醒轮（`:272-303`）、
  `ManualInitialOutreachService` 首封轮的 `existingContact` 分支（`:552`/`:573`）。
- Violation consequence: 先选号会消耗 `SenderAccountAssignmentService` 的分发名额并污染
  `assignments`，即使最终用绑定账号发送，统计与后续专家的打分都已被扭曲。

### I-2: 门禁矩阵由 `manual` 形参唯一决定
- Rule: 沿用 P1 的 I-7 矩阵，`manual` 取值在本计划固定为：
  | 调用点 | `manual` |
  |---|---|
  | `ManualExpertMailService.sendManualMail` | `true` |
  | `MeetingScheduleService.confirmMeetingAndEmail` | `true` |
  | `ManualInitialOutreachService` 材料提醒轮 | `true` |
  | `ManualInitialOutreachService` 首封轮（`existingContact` 分支） | `false` |
- Applies to: 上述四处。
- Violation consequence: 材料提醒轮若传 `manual=false`，会因每日额度/自动暂停被拦，
  与"人工发送脱离配额"既有决策冲突，回归 K-operator-send-quota-paths。
- 来源: 决策 ② + K-sender-account-enabled-scope + K-operator-send-quota-paths 调和口径

### I-3: 显式 `senderAccountCode` 与绑定必须一致
- Rule: `ManualMailSendCommand.senderAccountCode` 非空时，
  必须等于 `contact.boundSenderAccountCode`，否则抛
  `IllegalArgumentException("发件账号与专家绑定不一致")`；
  唯一例外是该 contact **无绑定**（此时显式 code 生效并补写绑定）。
- Applies to: `ManualExpertMailService.sendManualMail:55-58`；
  其调用方 `ManualInitialOutreachService:300-305`（材料提醒）与
  `ExpertContactManagementController.sendManualMail:168`（前端，当前恒传 null）。
- Violation consequence: 绑定形同虚设——任何调用方仍可指定任意账号绕过，
  本次事故的修复不彻底。

### I-4: 批量路径的绑定异常按专家跳过，不中断整批
- Rule: `BoundSenderAccountUnavailableException` 在
  `ManualInitialOutreachService` 的两个轮次里必须被 per-contact 捕获，
  计入 `accumulator.recordSkipped(...)` 并 `continue`；
  不得升级为 `midRoundStop` / `stopReason = "SYSTEM_ERROR"`。
- Applies to: `ManualInitialOutreachService` 材料提醒轮 `:294-336` 的 catch 链、
  首封轮 `:585` 起的 catch 链；`ManualExpertMailService.sendBatchMail:129-137`
  已有 per-contact `catch (ex: Exception)`，天然满足。
- **顺序要求**：P1 已把两个绑定异常声明为 `IllegalStateException` 子类，
  而 Kotlin `error(...)` 抛的也是 `IllegalStateException`。因此具体类型的 catch 分支
  必须写在通用 `catch (e: Exception)` **之前**，否则被吞掉并升级为整批 FAILED。
- Violation consequence: 一个账号被禁用会让整批任务 FAILED，而不是只跳过它名下的专家。

### I-5: `isManualSendable` 补 enabled，但不补额度与暂停
- Rule: `MailSenderAccountService.isManualSendable`（`:227-228`）改为
  `account.enabled && account.accountCode != SIMULATOR_ACCOUNT_CODE`。
  **禁止**在此加入 `todaySentCount` 或 `autoSendPaused` 判定。
- Applies to: `MailSenderAccountService.kt:227-228`，
  唯一消费方 `selectAccountForManualSending()`（`:197-201`）。
- Violation consequence: 加额度判定 → 回归 K-operator-send-quota-paths；
  加暂停判定 → 回归 `MailSenderAccountServiceTest.kt:48-57`。
  不加 enabled → LiLei 事故在"无绑定兜底"分支重现。

### I-6: 回复路径零改动（全局 G-1）
- Rule: 本计划的 diff **不得包含**
  `PendingMailOperationService.kt`、`AutoMailReplyService.kt`、
  `AutoReplyPreviewService.kt`、`TrustReplyWorkbenchService.kt`。
- Applies to: 变更文件清单。
- Violation consequence: 换绑后回复走新账号，`In-Reply-To` 与 `From` 域不一致；
  且禁用账号收到的来信将无法回复，破坏 K-sender-account-enabled-scope 的核心场景。

## 现状审计

### 发件账号选取路径全集（本次 grep 实测）

`grep -rn "selectAccountForManualSending\|selectAccountForSending\|selectAccount(" src/main/kotlin`：

| # | 位置 | 当前行为 | 本计划处置 |
|---|---|---|---|
| 1 | `InitialOutreachService.kt:48` | `senderAccountAssignmentService.selectAccount(expert, assignments)`，**新建 contact** | 不改（P1 已在建行时固化绑定） |
| 2 | `ManualInitialOutreachService.kt:552` | `selectAccount(expert, assignments, ignoreWarmup)`，contact 可能已存在 | **改**：`existingContact` 有绑定时先解析绑定 |
| 3 | `ManualInitialOutreachService.kt:272` | 同上，**材料提醒轮，targets 全是已有 contact** | **改**：一律先解析绑定 |
| 4 | `ManualExpertMailService.kt:55-58` | 显式 code 或 `selectAccountForManualSending()` | **改**：绑定优先 + I-3 一致性校验 |
| 5 | `MeetingScheduleService.kt:109` | `selectAccountForSending()`，**每次会议确认重新选号** | **改**：`resolveForSend(contact, manual=true)` |
| 6 | `PendingMailOperationService.kt:642-647` | `requestedCode ?: record.senderAccountCode` | **不改**（G-1 / I-6） |
| 7 | `AutoMailReplyService.processSingle(account, ...)` | 用 IMAP 收信账号 | **不改**（G-1 / I-6） |

### `MailSenderAccountService`（`mail/service/MailSenderAccountService.kt`）

- `selectAccountForManualSending():197-201` → `findAllByAccountCodeNot(SIMULATOR_NOOP)`
  + `isManualSendable():227-228`（**只排除模拟器**）+ `maxWithOrNull(selectionScore, thenBy id)`。
  这是本次事故的直接代码位置。
- `selectAccountForSending():191-195` → `findAllByEnabledTrue()` + `isSendable():221-225`
  （enabled && !autoSendPaused && 未超额 && 非模拟器）。
- `getAccount(code):30-32` 取任意状态；`getEnabledAccount(code):26-28` 只取 enabled；
  `getManualSendAccount(code):59-66` 取任意状态但排除模拟器。
- 额度读判定唯一来源：`SenderWarmupService.effectiveDailyLimit`（K-operator-send-quota-paths）。

- **锁定既有决策的测试（本计划必须区分对待）**
  | 测试 | 断言 | 处置 |
  |---|---|---|
  | `MailSenderAccountServiceTest.kt:35-46` `selects account at daily limit` | 满额账号仍可被人工选中 | **保留不改**（I-5） |
  | `MailSenderAccountServiceTest.kt:48-57` `includes auto-paused accounts` | 自动暂停账号仍可被人工选中 | **保留不改**（I-5） |
  | `MailSenderAccountServiceTest.kt:62-74` `includes disabled accounts` | 禁用账号可被人工选中 | **改写**为 `excludes disabled accounts`（I-5） |
  | `MailSenderAccountServiceTest.kt:76-87` `excludes simulator account` | — | 保留 |
  | `ManualExpertMailServiceTest.kt:351-363` `succeeds when selectAccountForManualSending returns disabled account` | 禁用账号可完成人工发送 | **改写**为绑定禁用时抛 `BoundSenderAccountUnavailableException` |

### `ManualExpertMailService`（`mail/service/ManualExpertMailService.kt`）

- `sendManualMail:50-123`，`@Transactional`。`contact` 于 `:51` 取得，
  账号解析在 `:55-58`，随后 `compose(contact, account, command)` 依赖 account 渲染变量。
- `:101` `mailSenderAccountRepository.save(account.copy(lastSentAt = now))`
  —— 只写 `lastSentAt`，**不写 `todaySentCount`**（与 K-operator-send-quota-paths 一致）。
- `sendBatchMail:125-144` 已 per-contact catch，满足 I-4。
- 调用方全集（`grep -rn "sendManualMail("`）：
  `ExpertContactManagementController.kt:168`（前端，`app.js:8358` 恒传 `senderAccountCode: null`）、
  `ManualInitialOutreachService.kt:305`（材料提醒，**当前显式传 `account.accountCode`**）。

### `MeetingScheduleService`（`campaign/service/MeetingScheduleService.kt`）

- `confirmMeetingAndEmail:85-174`，`@Transactional`。`contact` 于 `:91-92` 取得，
  账号在 `:109` 重新选号 —— 与该专家首封的账号无关，是当前"换号"的主要来源之一。
- `:155-160` `mailSenderAccountRepository.save(account.copy(todaySentCount = +1, lastSentAt = now))`
  —— 这是 K-operator-send-quota-paths 记载的写路径 ②，本计划**保持不变**。
- 触发者：`ExpertContactManagementController.kt:194-201`（运营点击）→ `manual=true`。

### `ManualInitialOutreachService`（`campaign/service/ManualInitialOutreachService.kt`）

- **材料提醒轮** `:271-341`：`targets` 是 `(contact, expert)` 对，contact 必然已存在。
  `:272` 选号 → `:300-305` 组 `ManualMailSendCommand(senderAccountCode = account.accountCode)`
  → `manualExpertMailService.sendManualMail(contactId, command)` →
  `:309` `incrementTodaySentCount(account.accountCode, ...)` →
  `:338-341` `assignments.add(...)`。
  `account` 被四处消费（rateLimiter、command、计数、assignments），
  改造必须让这四处指向同一个"最终实际使用"的账号。
- **首封轮** `:550-560` 选号 → `:573-582` `existingContact ?: run { save(...) }`。
  当前顺序是"先选号后取 contact"，与 I-1 冲突，必须调序。
- 异常处理现状：`:552-566` 对 `NoAvailableSenderAccountException` → `PAUSED`，
  对其他 `Exception` → `FAILED` + `midRoundStop`。
  绑定异常若落入后者会中断整批，违反 I-4，必须在 catch 链**前置**独立分支。

### Interaction points

- **IP-1**：P1 建立的绑定（写）× 本计划四条解析路径（读）——
  P1 期间新建的 contact 全部有绑定；P1 之前且从未发过 INTRODUCTION 的 contact
  （例如只发过 MATERIAL_REMINDER 的、或 V85 回填未覆盖的）绑定为 NULL，
  必须走兜底而非报错，否则这批专家彻底发不出信。
- **IP-2**：`ManualInitialOutreachService` 材料提醒轮（写 `todaySentCount`）×
  `resolveForSend(manual=true)`（不判额度）——
  该轮会持续给同一个绑定账号加计数直至超过 `dailySendLimit`，
  但因 `manual=true` 不拦截，计数会超出上限。这是既有决策的自然后果
  （人工发送脱离配额），**不在本计划修正**，但必须在验收中确认不产生异常。
- **IP-3**：`ManualExpertMailService`（读绑定）× `ManualInitialOutreachService`（传显式 code）——
  I-3 的一致性校验会让材料提醒轮在传错 code 时立刻失败；
  因此材料提醒轮必须改为传"绑定解析出的 code"而非"selectAccount 选出的 code"。

## 实现方案

### 阶段 1 — 兜底能力与谓词修正

**T1.1 `MailSenderAccountService.kt` 修 `isManualSendable`**（遵 I-5）

```kotlin
    private fun isManualSendable(account: MailSenderAccount): Boolean =
        account.enabled &&
            account.accountCode != SIMULATOR_ACCOUNT_CODE
```

只加 `account.enabled &&` 一行。**不动** `selectAccountForManualSending`（`:197-201`）
其余部分，不动 `isSendable`（`:221-225`）。

**T1.2 `SenderAccountBindingService.kt` 增加 `ignoreWarmup` 透传**（服务于首封轮）

`resolveForSend` 增加默认形参：

```kotlin
    fun resolveForSend(
        contact: ExpertContact,
        manual: Boolean,
        ignoreWarmup: Boolean = false
    ): MailSenderAccount
```

`requireAvailable` 的额度分支改为
`account.todaySentCount >= warmup.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup)`。
`manual=true` 时该分支不可达，`ignoreWarmup` 对人工路径无影响。
依据：K-operator-send-quota-paths 补充段落记载的
"给 `effectiveDailyLimit` 加 `ignoreWarmup` 形参并逐层下传"的既有模式。

### 阶段 2 — 单封人工发送（遵 I-1/I-2/I-3）

**T2.1 `ManualExpertMailService.kt` 改账号解析**

构造函数注入 `senderAccountBindingService: SenderAccountBindingService`。
`:55-58` 替换为：

```kotlin
        val account = resolveAccount(contact, command.senderAccountCode)
```

新增私有方法（置于 `compose` 之前）：

```kotlin
    private fun resolveAccount(contact: ExpertContact, requestedCode: String?): MailSenderAccount {
        val contactId = contact.id ?: error("Expert contact id is required")
        val requested = requestedCode?.takeIf { it.isNotBlank() }
        val bound = contact.boundSenderAccountCode?.takeIf { it.isNotBlank() }

        // I-3: 显式指定必须与绑定一致
        if (requested != null && bound != null && requested != bound) {
            throw IllegalArgumentException(
                "发件账号与专家绑定不一致：请求 $requested，绑定 $bound（contactId=$contactId）"
            )
        }
        // I-1: 有绑定一律走绑定解析（含 enabled 门禁）
        if (bound != null) {
            return senderAccountBindingService.resolveForSend(contact, manual = true)
        }
        // 无绑定兜底：显式 code 优先，否则选号；两者都要补写绑定（IP-1）
        val account = requested
            ?.let(mailSenderAccountService::getManualSendAccount)
            ?: mailSenderAccountService.selectAccountForManualSending()
        senderAccountBindingService.bindIfAbsent(contactId, account.accountCode, LocalDateTime.now())
        return account
    }
```

保留 `mailSenderAccountService` 依赖（兜底仍用）。
`:101` 的 `save(account.copy(lastSentAt = now))` 与 QA 落库、状态迁移全部不动。

> 兜底分支里 `getManualSendAccount`（`:59-66`）只排除模拟器、不判 enabled。
> 这是刻意的：显式指定 + 无绑定 = 调用方明确知道自己在做什么。
> `selectAccountForManualSending` 已由 T1.1 补上 enabled 门禁。

### 阶段 3 — 会议确认（遵 I-1/I-2）

**T3.1 `MeetingScheduleService.kt:109`**

构造函数注入 `senderAccountBindingService`。`:109` 替换为：

```kotlin
        val account = try {
            senderAccountBindingService.resolveForSend(contact, manual = true)
        } catch (e: SenderAccountNotBoundException) {
            val fallback = mailSenderAccountService.selectAccountForSending()
            senderAccountBindingService.bindIfAbsent(contactId, fallback.accountCode, LocalDateTime.now())
            fallback
        }
```

`BoundSenderAccountUnavailableException` **不捕获**，直接向上抛到
`ExpertContactManagementController` —— 会议确认是单次运营动作，报错即可，无需批量跳过语义。
该异常在 P1 已声明为 `IllegalStateException` 子类，因此
`GlobalExceptionHandler.handleIllegalState`（`common/controller/GlobalExceptionHandler.kt:18-20`）
会将其映射为 **400 BAD_REQUEST + `code="BAD_REQUEST"` + `message=异常文案`**，
前端可直接展示；若误改为普通 `RuntimeException`，会落到 `handleException` 变成 500 `INTERNAL_ERROR`。
`:155-160` 的 `todaySentCount + 1` 写入不动。

### 阶段 4 — 批量路径（遵 I-1/I-2/I-4/IP-3）

**T4.1 材料提醒轮 `ManualInitialOutreachService.kt:271-341`**

`:272` 的 `selectAccount` 调用替换为绑定优先解析。由于 `targets` 元素是
`(contact, expert)` 对，`contact` 在 `:252` 已解构可用：

```kotlin
                val account = try {
                    senderAccountBindingService.resolveForSend(contact, manual = true)
                } catch (e: SenderAccountNotBoundException) {
                    try {
                        val picked = senderAccountAssignmentService
                            .selectAccount(expert, assignments, ignoreWarmup)
                        senderAccountBindingService
                            .bindIfAbsent(contactId, picked.accountCode, LocalDateTime.now())
                        picked
                    } catch (ex: NoAvailableSenderAccountException) {
                        stopReason = "NO_AVAILABLE_ACCOUNT"; finalStatus = "PAUSED"
                        midRoundStop = true; break
                    }
                } catch (e: BoundSenderAccountUnavailableException) {
                    // I-4: 单专家跳过，不中断整批
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.SEND_EXCEPTION,
                        "绑定账号不可用（${e.accountCode}/${e.reason}）：$email"
                    )
                    processedTotal++; roundSent++; roundProcessed++; roundRejected++
                    updateProgressWithAccumulator(/* 沿用同轮既有实参 */)
                    continue
                }
```

`:300-305` 的 `ManualMailSendCommand(senderAccountCode = account.accountCode)` **保持传值**
——此时 `account.accountCode` 已等于绑定值，通过 I-3 校验（IP-3）。
`:309` 的 `incrementTodaySentCount(account.accountCode, ...)`、
`:338-341` 的 `assignments.add(...)` 不动（兜底分支仍需 assignments 参与打分）。

> `BatchOutcomeReasonCodes` 若无合适枚举项，复用 `SEND_EXCEPTION`；
> **不新增枚举值**（新增会波及批量控制台的 reason 展示映射，超出本计划范围）。

**T4.2 首封轮 `ManualInitialOutreachService.kt:550-582` 调序**

把 `:550-566` 的选号块移到 `:573-582` 的 contact 解析**之后**，改为：

```kotlin
                try {
                    // 1. 先确定 contact（I-1：绑定必须先于选号）
                    val contact = existingContact ?: null   // 占位：新建推迟到确定 account 之后

                    val account = if (contact?.boundSenderAccountCode != null) {
                        try {
                            senderAccountBindingService
                                .resolveForSend(contact, manual = false, ignoreWarmup = ignoreWarmup)
                        } catch (e: BoundSenderAccountUnavailableException) {
                            accumulator.recordSkipped(
                                BatchOutcomeReasonCodes.SEND_EXCEPTION,
                                "绑定账号不可用（${e.accountCode}/${e.reason}）：${expert.email}"
                            )
                            processedTotal++; roundSent++; roundProcessed++; roundRejected++
                            continue
                        }
                    } else {
                        senderAccountAssignmentService.selectAccount(expert, assignments, ignoreWarmup)
                    }
                    // 2. 新建分支沿用 P1 的建行固化
                    val resolvedContact = contact ?: run { /* P1 的 ExpertContact(...) 构造，绑定=account */ }
```

`NoAvailableSenderAccountException` / 通用 `Exception` 的原有 catch 语义
（`PAUSED` / `FAILED` + `midRoundStop`）保持不变，只是包裹范围从
"选号语句"变成"else 分支的选号语句"。

> 本任务改动的是控制流骨架，执行时须逐行核对 `processedTotal` / `roundSent` /
> `roundProcessed` / `roundRejected` 四个计数器的推进次数与改动前一致——
> 该文件已有注释（`:325-327`）明确警告过重复计数问题（V-1）。

### 阶段 5 — 测试

**T5.1 `MailSenderAccountServiceTest.kt`**（改 1 例，保 3 例）

- `selectAccountForManualSending includes disabled accounts`（`:62-74`）→ 改名
  `excludes disabled accounts`：预置一个高分但 `enabled=false` 的账号 + 一个低分 enabled 账号，
  断言选中后者。
- `selects account at daily limit`（`:35-46`）、`includes auto-paused accounts`（`:48-57`）、
  `excludes simulator account`（`:76-87`）**逐字不改**（I-5 回归证据）。
- 新增 `selectAccountForManualSending throws when all accounts disabled`。

**T5.2 `ManualExpertMailServiceTest.kt`**（改 1 例，加 4 例）

- `succeeds when selectAccountForManualSending returns disabled account`（`:351-363`）→ 改写为
  `throws when bound account is disabled`：contact 带绑定且该账号 `enabled=false`，
  断言抛 `BoundSenderAccountUnavailableException` 且
  `Mockito.verify(mailDeliveryService, never()).send(any(), any())`。
- 新增 `uses bound account and never calls selectAccountForManualSending`（I-1）。
- 新增 `throws when requested code conflicts with binding`（I-3）。
- 新增 `falls back to selection and binds when contact has no binding`：
  断言调用 `selectAccountForManualSending()` 且 `bindIfAbsent(...)` 被调用一次（IP-1）。
- 新增 `allows auto-paused bound account`（must-NOT-change：人工不受暂停阻塞）。

**T5.3 `MeetingScheduleServiceTest.kt`**（加 2 例）

- `confirmMeetingAndEmail uses bound account`：断言
  `Mockito.verify(mailSenderAccountService, never()).selectAccountForSending()`
  且 `mailDeliveryService.send` 的 account 实参为绑定账号（I-1）。
- `confirmMeetingAndEmail throws when bound account disabled`（I-2）。
- 既有 `:124` 的 `selectAccountForSending` stub 需保留，供无绑定兜底用例复用。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 修改 | `isManualSendable` 加 `enabled` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt` | 修改 | `resolveForSend` 加 `ignoreWarmup` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改 | `resolveAccount` 绑定优先 + I-3 校验 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt` | 修改 | `:109` 改绑定解析 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 两轮次绑定优先 + I-4 跳过 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | 修改 | 改 1 加 1 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 修改 | 改 1 加 4 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt` | 修改 | 加 2 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | 测试适配：补 `senderAccountBindingService.resolveForSend` 桩（A3 授权；不新增用例、不改既有断言语义） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` | 修改 | 编译修复：构造实参尾部追加 `Mockito.mock(SenderAccountBindingService::class.java)`（A4 授权） |

文件数 10 ≤ 10 ✓　子系统 1（后端发送链路）≤ 2 ✓　新增存储字段 0 ✓

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。
> 来源：项目根 `CLAUDE.md`「Commands」+「项目元信息」。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=MailSenderAccountServiceTest+ManualExpertMailServiceTest+ManualExpertMailServiceGateTest+MeetingScheduleServiceTest+ManualInitialOutreachServiceTest+SenderAccountBindingServiceTest

# 既有决策的回归证据（必须全绿且未被修改）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending selects account at daily limit'

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending includes auto-paused accounts'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`，`BUILD SUCCESS`。
来源：`CLAUDE.md` 项目元信息；过滤语法取自「Commands」章节示例。

## 验收标准

- **I-1**: `git diff` 中四条路径的账号解析语句均以 `resolveForSend(` 开头；
  `ManualExpertMailServiceTest#uses bound account and never calls selectAccountForManualSending` 与
  `MeetingScheduleServiceTest#confirmMeetingAndEmail uses bound account` 通过。
- **I-2**: `grep -n "resolveForSend(" src/main/kotlin` 的四处调用，
  `manual` 实参逐一对照 I-2 表格；材料提醒轮为 `manual = true`，首封轮为 `manual = false`。
- **I-3**: `ManualExpertMailServiceTest#throws when requested code conflicts with binding` 通过；
  `ManualInitialOutreachService:300-305` 传入的 code 来自 `account.accountCode`
  且该 `account` 由 `resolveForSend` 产出。
- **I-4**: `ManualInitialOutreachService` 中 `BoundSenderAccountUnavailableException`
  的 catch 分支以 `continue` 结束，且不出现在设置 `midRoundStop = true` 的分支内。
- **I-5**: `MailSenderAccountService.kt:227-228` 的 `isManualSendable` 逐字为
  `account.enabled && account.accountCode != SIMULATOR_ACCOUNT_CODE`（无额度/暂停判定）；
  `MailSenderAccountServiceTest.kt` 的 `selects account at daily limit` 与
  `includes auto-paused accounts` 两例在 diff 中**零改动**。
- **I-6**: `git diff --name-only` 不含 `PendingMailOperationService.kt`、
  `AutoMailReplyService.kt`、`AutoReplyPreviewService.kt`、`TrustReplyWorkbenchService.kt`。
- **IP-1**: `ManualExpertMailServiceTest#falls back to selection and binds when contact has no binding` 通过。
- **回归**: 执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 禁用绑定账号后，人工发送被拦截（事故直接修复）
- 前置条件: 取一位已绑定账号 `X` 的专家；在「发件账号池」把 `X` 禁用。
- 操作步骤:
  1. 打开该专家详情，选择任一邮件模板，点「发送」。
- 预期结果: 页面出现错误提示，文案包含账号 code `X` 与原因 `DISABLED`；
  专家的邮件往来列表**没有新增 OUTBOUND 记录**；
  收件人邮箱**未收到**任何邮件。
- 覆盖: I-2、需求描述第 2 条

### A-2: 绑定账号自动暂停时，人工发送仍可用（回归既有决策）
- 前置条件: 同一专家，把 `X` 重新启用；然后手工把其 `auto_send_paused` 置 1
  （`UPDATE mail_sender_account SET auto_send_paused=1,
   auto_send_paused_reason='BOUNCE_RATE_HIGH:manual-test' WHERE account_code='X';`）。
- 操作步骤: 重复 A-1 步骤 1。
- 预期结果: **发送成功**，收件人从 `X` 的地址收到邮件；无任何"账号不可用"提示。
- 覆盖: must-NOT-change 第 3 条、I-5

### A-3: 绑定账号今日额度已满时，人工发送仍可用（回归既有决策）
- 前置条件: 恢复 `X` 的 `auto_send_paused=0`；
  `UPDATE mail_sender_account SET today_sent_count = daily_send_limit WHERE account_code='X';`
- 操作步骤: 重复 A-1 步骤 1。
- 预期结果: **发送成功**。
- 覆盖: must-NOT-change 第 2 条、I-5

### A-4: 会议确认邮件从绑定账号发出
- 前置条件: 一位绑定账号为 `X` 的专家，存在一条 `PENDING` 会议安排；
  系统内另有至少一个权重更高、额度更空的 enabled 账号 `Y`。
- 操作步骤:
  1. 在专家详情的会议安排里填写时间/工具/链接，点「确认并发邮件」。
  2. 查看专家的邮件往来中新增的 `MEETING_CONFIRMATION` 记录。
- 预期结果: 该记录的「账号」列显示 `X`（不是 `Y`）；
  专家实际收到的邮件 From 也是 `X` 的地址。
- 覆盖: I-1、需求描述第 1 条

### A-5: 材料提醒批量按绑定分发，禁用账号只跳过其名下专家
- 前置条件: 构造一批材料提醒目标，其中若干专家绑定账号 `X`、若干绑定 `Y`；把 `X` 禁用。
- 操作步骤:
  1. 启动一次材料提醒批量任务。
  2. 任务结束后查看执行日志与「跳过原因」统计。
- 预期结果: 任务最终状态为 `COMPLETED`（**不是** `FAILED`）；
  绑定 `Y` 的专家全部发送成功且发件账号为 `Y`；
  绑定 `X` 的专家全部记为跳过，跳过说明包含 `绑定账号不可用（X/DISABLED）`。
- 覆盖: I-4、I-1、IP-3

### A-6（回归）: 禁用账号仍能收信并被人工回复
- 前置条件: `X` 仍为禁用；`X` 的信箱中有一封来自某专家的未处理来信。
- 操作步骤:
  1. 触发一次收信（或等待定时收信）。
  2. 在「收发件箱 / 待处理」中找到这封信，做一次人工回复。
- 预期结果: 来信能被正常拉取并展示；人工回复**发送成功**，
  且回复的发件账号是 `X`（收信账号），不是该专家当前绑定的其他账号。
- 覆盖: must-NOT-change 第 1 条、全局 G-1、I-6

### A-7（回归）: 无绑定的历史专家仍能发信并自动补绑
- 前置条件: 找一位 `bound_sender_account_code IS NULL` 的 contact
  （若不存在，手工 `UPDATE expert_contact SET bound_sender_account_code=NULL,
   sender_account_bound_at=NULL WHERE id=<id>;` 构造一个）。
- 操作步骤:
  1. 在其详情页发送任一模板邮件。
  2. 发送后再查 `SELECT bound_sender_account_code FROM expert_contact WHERE id=<id>;`
- 预期结果: 步骤 1 发送成功；步骤 2 返回的 code 等于本次实际发件账号，且不为 NULL。
- 覆盖: must-NOT-change 第 5 条、IP-1

### A-8（回归）: 首封批量的账号分布未劣化
- 前置条件: 记录本计划上线前一次首封批量的账号分布。
- 操作步骤: 用相同配置再跑一次首封批量，对比「账号统计」。
- 预期结果: 各 enabled 账号均有承担量；不出现单账号独吞或某账号完全不参与；
  计数字段（已处理/成功/失败/跳过）之和等于目标总数（无重复计数）。
- 覆盖: T4.2 的计数器风险、must-NOT-change 第 4 条
