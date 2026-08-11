# Plan 02 — 抑制名单收口到投递层（fail-closed）

> 顺序位置：退订链路补全的第 2 个子计划，与 Plan 01 无相互依赖。
> 见 [unsubscribe-closure-master.md](unsubscribe-closure-master.md)
> 优先级：P1 —— Gmail 实际收信测试的前置阻塞项
> 共享证据引用：主索引 E-3、E-4
>
> **v2 修订（2026-08-11）**：初稿把 mailto 退订通道一并放在本计划，且假设"3 条未覆盖路径全是操作端同步路径，异常自然冒泡成 400"。
> 复核发现该假设对 `PendingMailOperationService` **不成立**（见 I-5），修复它需要第 11 个文件，超出 create-p 文件上限。
> 故本计划收窄为纯抑制拦截，mailto 通道拆到 [unsubscribe-02b-mailto-channel.md](unsubscribe-02b-mailto-channel.md)。

## 需求描述

### Observable outcome

1. 任何邮箱进入抑制名单后，**7 条外发路径全部发不出去**；操作端触发的 3 条路径返回可读错误（HTTP 400 + 中文原因 `收件人已退订，禁止外发：<邮箱>`），自动路径按既有跳过逻辑跳过。
2. 待办工作台对已退订收件人点发送时，**不得**把发送尝试烧成 `DELIVERY_UNKNOWN`，也不得提示"发送状态未知，请勿重复发送"。
3. 人工单发在明确需要时可显式绕过抑制拦截（API 传 `allowSuppressed = true`），默认不绕过。

### What must NOT change

- 已有 6 个 `isSuppressed` 前置检查点的行为与统计口径（`InitialOutreachService:46`、`ManualInitialOutreachService:266/556/1171`、`AutoReplyPreviewService:149`、`AutoMailReplyService:825`）—— 继续作为早退优化，跳过原因码与计数不变。
- `SmtpErrorCategory` 枚举不新增值；`DeliveredMail` 结构不变。
- `MailDeliveryService.send()` 的方法签名不变（9 个测试文件依赖，见现状审计）。
- 退订话术的入站判定（正文 `contains`）行为不变 —— 主题判定是 Plan 02b。
- 抑制名单幂等语义（`EmailSuppressionService.suppress()` `:25-41`）不变。
- 人工富文本回复的幂等占位/claim 机制（`ManualReplySendAttemptService.prepareAndClaim`）语义不变 —— 本计划只在它**之前**加拦截，不改它本身。
- 退订 header 的生成条件不变（收窄是 Plan 03）。

### Out of scope

- mailto 退订通道 → Plan 02b。
- override 的前端勾选框 → Plan 05（含前端文件，需样式契约）。本计划只做到 API 层可传参。
- 批量人工发信（`sendBatchMail`）的 override —— 批量一律不允许绕过，固定 false。
- 会议邮件族补 `unsubscribeUrl` → Plan 04。

## 关键不变量

### Invariant I-1：投递层是抑制拦截的兜底边界，且 fail-closed

- Rule：`SmtpMailDeliveryService.send()` 必须在**接触任何 SMTP 资源之前**（即 `smtpSenderFactory.getSender(account)` 之前）检查 `emailSuppressionService.isSuppressed(mail.to)`；命中且未显式 override 时抛 `RecipientSuppressedException`，不得发出网络请求、不得构造 MimeMessage、不得返回 `DeliveredMail`。
- Applies to：主索引 E-3 表中全部 7 个 `mailDeliveryService.send` 调用点。
- Violation consequence：检查放在 `getSender()` 之后会先建立 SMTP 连接；只靠调用方分散检查则新增第 8 条路径必然再漏一次（当前 7 条漏 3 条正是分散检查的直接后果）。
- 来源：original（证据见主索引 E-3）

### Invariant I-2：抑制拦截不得表达为投递失败

- Rule：因抑制拒发**只能**抛异常，禁止返回 `DeliveredMail(status = "SUPPRESSED"/"FAILED", errorCategory = PERMANENT|TRANSIENT|INFRASTRUCTURE)`。
- Applies to：`SmtpMailDeliveryService.send()`。
- Violation consequence：`ManualInitialOutreachService.kt:704-730` 按 `errorCategory` 分支 —— `PERMANENT` 把联系人写成 `operatorStatus = "EMAIL_INVALID"` 并同步 ES（`:713-715`），`TRANSIENT` + 421/452 触发 `accountRateLimiter.recordThrottled()`（`:729`）。用失败状态表达"已退订"会误标邮箱无效、误限流账号。
- 来源：original（证据见主索引 E-4）

### Invariant I-3：异常类型决定运营可见性

- Rule：`RecipientSuppressedException` 必须继承 `IllegalStateException`；message 必须含被拦截邮箱且为中文可读文案。需要区分处理时，其 catch 分支必须排在通用 `catch (e: Exception)` 之前。
- Applies to：`RecipientSuppressedException` 定义；三条操作端路径。
- Violation consequence：`GlobalExceptionHandler.kt:18-20` 只把 `IllegalStateException` 映射为 400；继承 `RuntimeException` 会落到 fallback 变成 500 `INTERNAL_ERROR`，运营看到"服务器错误"。因 `error(...)` 也抛 `IllegalStateException`，子类 catch 必须在前。
- 来源：K-custom-exception-http-status-mapping

### Invariant I-4：override 是显式、逐次、默认关闭的

- Rule：override 表达为 `ComposedMail.allowSuppressedRecipient: Boolean = false`，默认必须 `false`。只有 `ManualExpertMailService.composeComposeTemplate()` 允许置为调用方传入值；其余 7 个 `ComposedMail(` 构造点一律不传。批量路径（`sendBatchMail`、`ManualInitialOutreachService.kt:317` 材料提醒批量）固定 false。
- Applies to：`ComposedMail`（`IntroductionMailComposer.kt:69-78`）；`ManualMailSendCommand`；`ManualMailSendRequest.toCommand()`；`BatchMailSendRequest.toCommand()`。
- Violation consequence：默认 true 或全局开关会让拦截变成装饰；批量允许 override 等于给群发开绕过退订的后门。
- 来源：original（需求方 2026-08-11 决策）

### Invariant I-5：幂等占位之前必须先判抑制（**v2 新增，修复初稿缺陷**）

- Rule：`PendingMailOperationService.sendManualRichReply()` 必须在 `manualReplySendAttemptService.prepareAndClaim(payload)`（`:253`）**之前**检查 `isSuppressed(contact.expertEmail)`，命中即抛 `ResponseStatusException(HttpStatus.BAD_REQUEST, "收件人已退订，禁止外发：<邮箱>")`。禁止依赖投递层（I-1）在该路径兜底。
- Applies to：`PendingMailOperationService.sendManualRichReply()`（`:128` 起）。
- Violation consequence（**已逐行验证，不是推测**）：`:270` 的 `mailDeliveryService.send` 被 `:359` 的 `catch (deliveryEx: Exception)` 包住。该 catch 的 `when` 只对 `ResponseStatusException` 直接重抛（`:361`），其余一律走 `else` 分支 → `finalizeFailure(..., resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN, ...)`（`:365-371`）→ 抛 `ResponseStatusException(HttpStatus.CONFLICT, "发送状态未知，请勿重复发送 (Message-ID: ...)")`（`:376-379`）。后果三重：
  1. 运营看到 409「发送状态未知」而不是「收件人已退订」，且文案暗示邮件**可能已发出** —— 实际一封都没发；
  2. 该 attempt 被烧成 `DELIVERY_UNKNOWN`，按幂等 fail-closed 设计（[[K-smtp-idempotency-reservation-before-delivery]]）后续相同请求会被阻断，该待办变成发不出去也解不掉；
  3. 直接违反本计划的 I-2 与 I-3。
- 另两条操作端路径**无此问题**（已逐行确认）：`ManualExpertMailService.sendManualMail()`（`:51`）中 `:63` 的 send 无 try/catch 包裹，`@Transactional` 回滚后异常直达 `GlobalExceptionHandler`；`MeetingScheduleService.confirmMeetingAndEmail()`（`:89`）中 `:141` 的 send 无 try/catch，该方法唯一的 catch 在 `:114` 且只捕 `SenderAccountNotBoundException`。
- 来源：original + K-smtp-idempotency-reservation-before-delivery

## 现状审计

> Step 1b-fe **未触发**：变更文件清单中无 `src/main/resources/static` 下文件，无 `.html` / `.css` / 前端 `.js`。故无 `## 样式契约` 节。override 的前端接线划归 Plan 05。

### Store：MySQL `email_suppression`

- Schema：`V30__create_email_suppression.sql`（唯一涉及该表的迁移）。域对象 `mail/domain/EmailSuppression.kt`，仓储 `mail/repository/EmailSuppressionRepository.kt`（Spring Data JDBC `CrudRepository`）。
- **本计划不改 schema，不新增字段。**

**Write paths（全量 grep `SuppressionSource\.`）：** `AutoMailReplyService.kt:839-843`（`INBOUND_REPLY`）、`UnsubscribeController.kt:24`/`:38`（`ONE_CLICK`）、`EmailSuppressionController.kt:30`（`MANUAL`）、`EmailSuppressionService.remove()`（删除）。**本计划全部不改。**

**Read paths：** `EmailSuppressionService.isSuppressed()` `:18-22` → `repository.existsByEmail(n)`；`list()` `:55-71` → 运营页面。

**`isSuppressed` 现有 6 个调用点：** `InitialOutreachService:46`、`ManualInitialOutreachService:266/556/1171`、`AutoReplyPreviewService:149`、`AutoMailReplyService:825`。本计划新增 2 个：`SmtpMailDeliveryService.send()`（兜底）与 `PendingMailOperationService.sendManualRichReply()`（前置，I-5）。

### Store：`ComposedMail`（进程内值对象，非持久化）

- 定义：`IntroductionMailComposer.kt:69-78`，8 个字段。
- **8 个构造点（全量 grep `ComposedMail(` 排除 data class）：** `PendingMailOperationService.kt:258`、`MeetingInvitationMailComposer.kt:22`、`IntroductionMailComposer.kt:36`、`AutoMailReplyService.kt:567`、`AutoMailReplyService.kt:959`、`ManualExpertMailService.kt:244`、`MeetingScheduleService.kt:135`（7 个生产构造点 + 测试内多处）。
- 新增字段带默认值 `= false`，故仅 `ManualExpertMailService.kt:244` 传值，其余零改动。

### Store：`MailDeliveryService` 接口

- 唯一实现：`SmtpMailDeliveryService.kt:15`（`grep ": MailDeliveryService"` 仅此一处实现，其余 6 处是构造器注入）。
- **9 个测试文件引用该接口或其 mock**：`BatchSendTaskRuntimeIntegrationTest`、`InitialOutreachServiceTest`、`ManualInitialOutreachServiceTest`、`MeetingScheduleServiceTest`、`AutoMailReplyServiceTest`、`ManualExpertMailServiceGateTest`、`ManualExpertMailServiceTest`、`PendingMailOperationServiceTrustWorkbenchTest`、`SmtpMailDeliveryServiceTest`。
- **这是不改 `send()` 签名的直接原因**（I-4 把 override 放在 `ComposedMail`）：给 data class 加带默认值的字段对 Mockito `any()` 桩零影响；改方法签名波及 9 个文件的桩定义。

### Store：`SmtpMailDeliveryService` 构造器

- 当前 3 个依赖（`:11-14`）：`smtpSenderFactory`、`unsubscribeTokenService`、`mailContentService`。新增 `emailSuppressionService`。
- 依赖链 `SmtpMailDeliveryService → EmailSuppressionService → EmailSuppressionRepository`，**无循环依赖**（`EmailSuppressionService` 只依赖仓储，`:13-15`）。
- `SmtpMailDeliveryServiceTest.kt:6-20` 手工 new 该服务（非 Spring 上下文），构造器变更**必然**要求改测试，现有 21 个用例全部要补一个 stub 依赖。

### 三条未覆盖操作端路径的异常传播形态（逐行确认，I-5 的依据）

| 路径 | send 行 | 是否被 try/catch 包裹 | 抛 `RecipientSuppressedException` 的实际后果 |
|---|---|---|---|
| `ManualExpertMailService.sendManualMail()` `:51` | `:63` | 否（方法内无 catch） | `@Transactional` 回滚 → `GlobalExceptionHandler` → 400 ✅ |
| `MeetingScheduleService.confirmMeetingAndEmail()` `:89` | `:141` | 否（唯一 catch 在 `:114`，只捕 `SenderAccountNotBoundException`） | 直达 `GlobalExceptionHandler` → 400 ✅ |
| `PendingMailOperationService.sendManualRichReply()` `:128` | `:270` | **是**，`:359 catch (deliveryEx: Exception)` | 落 `else` 分支 → `finalizeFailure(DELIVERY_UNKNOWN)` `:365-371` → 409「发送状态未知，请勿重复发送」`:376-379` ❌ |

### Interaction points

- **IP-1**：投递层拦截 × 6 个既有前置检查点。前置存在时永不触发异常；缺失的 3 条中 2 条已确认可安全冒泡，第 3 条由 I-5 单独处理。
- **IP-2**：投递层拦截 × `InitialOutreachService.kt:72-85` 的 `try { send } catch (e: Exception)`。该 catch 把异常记为 `status = "FAILED"` 的 `InitialOutreachSendResult`。因 `:46` 已前置检查实际不可达；但**不得**依赖"不可达"，须有测试断言前置检查仍先命中（否则退订邮箱会被记为发送失败而非跳过）。
- **IP-3**：I-5 的前置检查 × `prepareAndClaim` 的幂等占位。检查必须在 `:253` 之前、`:231` 的 payload 构造之后或之前均可，但**绝不能**在 `:253` 之后 —— 否则 attempt 行已写入。
- **IP-4**：`ManualMailSendCommand` 新增字段 × `ManualInitialOutreachService.kt:317` 的构造点（材料提醒批量）。必须继承默认 false（I-4）；`:1171` 已前置检查，双保险。

## 实现方案

### 任务 T-1：`ComposedMail` 增加 override 字段（遵循 I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`

在 `ComposedMail`（`:69-78`）末尾追加，**必须带默认值**：

```kotlin
    /** 显式绕过抑制名单拦截。只允许人工单发路径按操作端请求置 true；批量与自动路径恒为 false。见 plan I-4。 */
    val allowSuppressedRecipient: Boolean = false
```

`IntroductionMailComposer.kt:36` 的构造点**不传**该参数。

### 任务 T-2：新增 `RecipientSuppressedException`（遵循 I-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`

在文件末尾追加顶层类（不新建文件，控制文件数）：

```kotlin
/**
 * 收件人已在抑制名单中，拒绝外发。继承 IllegalStateException 以便
 * GlobalExceptionHandler 映射为 400 BAD_REQUEST（见 plan I-3）。
 */
class RecipientSuppressedException(email: String) :
    IllegalStateException("收件人已退订，禁止外发：$email")
```

`EmailSuppressionService` 类体本身**不改**（主题判定属 Plan 02b）。

### 任务 T-3：投递层 fail-closed 拦截（遵循 I-1、I-2、I-3、I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

1. 构造器新增第 4 个依赖 `private val emailSuppressionService: EmailSuppressionService`。
2. `send()` 方法体**第一行**（在 `val sender = smtpSenderFactory.getSender(account)` 之前）插入：

```kotlin
if (!mail.allowSuppressedRecipient && emailSuppressionService.isSuppressed(mail.to)) {
    throw RecipientSuppressedException(mail.to)
}
```

约束：不得写在 `getSender()` 之后；不得改成返回 `DeliveredMail`；不得加 `try/catch` 把它降级为失败状态。

### 任务 T-4：待办工作台前置拦截（遵循 I-5，覆盖 IP-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`

在 `sendManualRichReply()` 内、`val claim = manualReplySendAttemptService.prepareAndClaim(payload)`（`:253`）**之前**插入：

```kotlin
if (emailSuppressionService.isSuppressed(contact.expertEmail)) {
    throw ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "收件人已退订，禁止外发：${contact.expertEmail}"
    )
}
```

说明：此处用 `ResponseStatusException` 而非 `RecipientSuppressedException`，是为了与该方法既有的错误表达方式一致（`:284-287` 等处已用 `ResponseStatusException`），且 `:361` 的 `is ResponseStatusException -> throw deliveryEx` 分支保证它即使被后续 catch 触及也会原样重抛。构造器需新增 `emailSuppressionService: EmailSuppressionService` 依赖（该类当前已注入 `mailDeliveryService`，`:61`）。

### 任务 T-5：人工单发 override 透传（遵循 I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`

1. `ManualMailSendCommand`（`:312-317`）追加 `val allowSuppressed: Boolean = false`。
2. `compose()`（`:167-183`）已持有 `command`，把 `command.allowSuppressed` 传给 `composeComposeTemplate()`；`ComposedMail(:244)` 置 `allowSuppressedRecipient = allowSuppressed`。
3. `sendBatchMail` 构造的 command 保持默认 false（I-4）。

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`

4. `ManualMailSendRequest`（`:309-322`）追加 `val allowSuppressed: Boolean = false`，`toCommand()`（`:315-321`）透传。
5. `BatchMailSendRequest.toCommand()`（`:330-336`）**不加**该字段。

### 任务 T-6：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`（既有 21 个用例）

1. 为全部既有用例补齐新构造器依赖：stub 默认返回 `isSuppressed(any()) = false`，现有 21 个用例行为不变。
2. 新增：
   - `send throws RecipientSuppressedException before touching smtp when recipient suppressed` —— 断言抛该异常**且** `Mockito.verify(smtpSenderFactory, never()).getSender(any())`。这是 I-1「接触 SMTP 之前」唯一可机器验证的形式。
   - `send proceeds when recipient suppressed but allowSuppressedRecipient is true` —— 断言正常走到发送。
   - `RecipientSuppressedException is an IllegalStateException` —— 断言类型（I-3）。

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`（既有）

3. 新增（覆盖 I-5、IP-3）：
   - `sendManualRichReply rejects suppressed recipient before claiming an attempt` —— 抑制邮箱下断言抛 `ResponseStatusException` 且 `status == BAD_REQUEST`，**且** `Mockito.verify(manualReplySendAttemptService, never()).prepareAndClaim(any())`，**且** `Mockito.verify(mailDeliveryService, never()).send(any(), any())`。
   - `sendManualRichReply never finalizes DELIVERY_UNKNOWN for suppressed recipient` —— 断言 `finalizeFailure` 零调用。

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`（既有）

4. 新增（覆盖 IP-2）：抑制邮箱在 `sendInitialBatch` 中被 `:46` 前置跳过，`mailDeliveryService.send` 零调用，结果里不出现 `status = "FAILED"` 记录。

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 修改 | `ComposedMail` 加 `allowSuppressedRecipient`（T-1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt` | 修改 | 追加 `RecipientSuppressedException`（T-2） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | 修改 | 新增依赖 + fail-closed 拦截（T-3） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改 | claim 前前置拦截 + 新增依赖（T-4） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改 | `ManualMailSendCommand.allowSuppressed` + 透传（T-5） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 | `ManualMailSendRequest.allowSuppressed` + `toCommand()`（T-5） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` | 修改 | 21 个用例补依赖 + 3 条新用例（T-6.1/2） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 修改 | 2 条新用例（T-6.3） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 修改 | 1 条新用例（T-6.4） |

文件数：9（≤10 ✅）。子系统数：2（① 投递层拦截；② 操作端路径前置拦截与 override 透传）（≤2 ✅）。新增数据字段：0（≤1 ✅）。

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SmtpMailDeliveryServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest

# 受构造器变更波及的其余测试类（一次跑完确认无编译与桩失配）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualExpertMailService*Test,ManualInitialOutreachServiceTest,MeetingScheduleServiceTest,AutoMailReplyServiceTest,BatchSendTaskRuntimeIntegrationTest'

# 单方法（定位失败时）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PendingMailOperationServiceTrustWorkbenchTest#sendManualRichReply rejects suppressed recipient before claiming an attempt'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn clean package` 额外要求 `BUILD SUCCESS`）。
来源：CLAUDE.md 「Commands」章节与项目元信息 `test_command` / `build_command`。

## 验收标准

- **I-1**：`SmtpMailDeliveryServiceTest` 新用例断言抛异常**且** `getSender` 零调用；`git diff` 显示拦截代码位于 `send()` 方法体首行、`getSender` 调用之前。
- **I-2**：`grep -n "SUPPRESSED" src/main/kotlin/.../SmtpMailDeliveryService.kt` 零命中；`git diff` 显示 `SmtpErrorCategory.kt` 与 `MailDeliveryService.kt` 零改动。
- **I-3**：`RecipientSuppressedException` 声明为 `: IllegalStateException(...)`；对应测试用例通过。
- **I-4**：`grep -n "allowSuppressedRecipient" src/main/kotlin` 命中恰好 2 处（定义、`ManualExpertMailService.kt:244`）；两个新字段默认值均为 `false`；`BatchMailSendRequest.toCommand()` 不含该字段。
- **I-5**：`PendingMailOperationServiceTrustWorkbenchTest` 两条新用例通过 —— `prepareAndClaim` 零调用、`send` 零调用、`finalizeFailure` 零调用、抛 400。`git diff` 显示拦截代码行号小于 `prepareAndClaim` 调用行号。
- **IP-2**：`InitialOutreachServiceTest` 新用例通过。
- **IP-3**：见 I-5 的行号断言。
- **IP-4**：`git diff` 显示 `ManualInitialOutreachService.kt:317` 附近的 `ManualMailSendCommand(` 构造未新增 `allowSuppressed` 实参。
- 回归：执行「验证命令」节的全量测试命令通过；执行「验证命令」节的构建命令通过。

## 人工验收清单

### A-1：会议确认邮件对已退订邮箱拒发并给出可读错误

- 前置条件：测试邮箱 T 已在抑制名单（后台 → 抑制名单 → 手工添加即可）；T 对应联系人已创建一条会议排程且可确认。
- 操作步骤：
  1. 专家详情页 → 会议排程 → 点「确认会议」，填时间/工具/链接后提交。
  2. 观察页面提示。
  3. 检查 T 的收件箱与该联系人的邮件记录。
- 预期结果：页面红色错误提示，文案含 `收件人已退订，禁止外发：` 加 T 的邮箱（HTTP 400，不是 500，不是「服务器错误」）；T 没收到邮件；`mail_record` 无对应 OUTBOUND 记录。
- 覆盖：observable outcome 1；I-1；I-3；IP-1

### A-2：人工单发默认拒发，API 传 override 后可发

- 前置条件：T 已在抑制名单。
- 操作步骤：
  1. 专家详情页 → 发送邮件 → 选任一模板 → 发送，观察提示。
  2. 用 curl 带 override 重试：
     `curl -X POST '<host>/api/expert-contacts/<contactId>/manual-mail' -H 'Content-Type: application/json' -d '{"optionType":"COMPOSE_TEMPLATE","optionValue":"<templateId>","senderAccountCode":null,"allowSuppressed":true}'`
  3. 检查 T 的收件箱。
- 预期结果：第 1 步失败并提示 `收件人已退订，禁止外发：`；第 2 步返回 200 且带 `messageId`；第 3 步 T 收到邮件。
- 覆盖：observable outcome 1、3；I-4；IP-1
- 备注：UI 暂无 override 勾选框（Plan 05 接线），第 2 步用 curl 验证 API 层能力。

### A-3：待办工作台对已退订邮箱拒发，且不烧掉发送尝试

- 前置条件：T 已在抑制名单；存在一条 T 的待处理入站邮件，在待办工作台可回复。
- 操作步骤：
  1. 打开该待办，撰写任意回复内容，点发送。
  2. 记录页面提示的**完整文案与 HTTP 状态**。
  3. 查 `mail_send_attempt` 表中该联系人的最新一行（或后台发送尝试记录页）。
  4. 把 T 从抑制名单移除，回到该待办再点一次发送。
- 预期结果：
  - 第 2 步提示 `收件人已退订，禁止外发：<T 的邮箱>`，HTTP **400**。**不得**出现「发送状态未知，请勿重复发送」，**不得**是 409。
  - 第 3 步：**没有**新增 `DELIVERY_UNKNOWN` 状态的尝试行（该次点击不应留下任何尝试记录）。
  - 第 4 步：能正常发出，不被幂等机制阻断。
- 覆盖：observable outcome 2；I-5；IP-3
- 备注：这一条是 v2 修订的核心验收项。若第 2 步出现 409「发送状态未知」，说明前置拦截被写到了 `prepareAndClaim` 之后，直接判不通过。

### A-4：群发对已退订邮箱按「跳过」统计，不记为发送失败（回归）

- 前置条件：抑制名单中有邮箱 T，且 T 对应专家仍在 L2 候选池、符合群发筛选条件。
- 操作步骤：
  1. 后台 → 批量发送 → 用能命中 T 的筛选条件启动一次小批量任务（限 2-3 封）。
  2. 任务结束后打开执行日志/结果明细。
- 预期结果：T 出现在**跳过**类目，原因为 `SUPPRESSED`，**不在**失败类目；T 的联系人 `operatorStatus` **未**被改成 `EMAIL_INVALID`；发件账号未被限流或暂停。
- 覆盖：What must NOT change 第 1 项；I-2；IP-2

### A-5：自动回复对已退订邮箱的既有拦截未变（回归）

- 前置条件：T 已在抑制名单；T 对应联系人 `autoReplyEnabled = true`、状态为 `WAITING_REPLY`。
- 操作步骤：
  1. 从 T 发一封能命中 QA 规则的问题邮件。
  2. 等一次收信处理。
  3. 查看该联系人状态与处理面板。
- 预期结果：联系人转为 `MANUAL_HANDOFF`，原因显示 `RECIPIENT_UNSUBSCRIBED`；T 未收到自动回复。**报错文案不应是** `收件人已退订，禁止外发` —— 说明命中的是 `AutoMailReplyService:825` 的既有前置拦截，而非投递层异常。
- 覆盖：What must NOT change 第 1 项；IP-1

### A-6：会议邀请自动路径的既有拦截未变（回归）

- 前置条件：T 已在抑制名单；T 对应联系人处于会触发会议邀请的状态。
- 操作步骤：从 T 发一封表达接受意向的邮件，等一次收信处理，查看联系人状态。
- 预期结果：转 `MANUAL_HANDOFF`，原因 `RECIPIENT_UNSUBSCRIBED`（命中 `AutoMailReplyService.kt:445` 的 `blockedByUnsubscribe(..., "MEETING_INVITATION")`）；T 未收到会议邀请；不出现投递层异常。
- 覆盖：What must NOT change 第 1 项；IP-1

> 人工验收开始时，从本节导出 `docs/plans/2026-08-11/unsubscribe-02-suppression-gate-acceptance.md`。清单本身有误时先改本节再重新导出。
