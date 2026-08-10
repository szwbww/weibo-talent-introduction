# Fast-P Child Brief — p2 (sender-binding-02-send-path-consistency)

> 唯一权威契约 = `docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md`。
> 本 brief 只承载主计划级约束与跨子计划接口，不重述子计划正文；以子计划为准。
> P1 已落地（V85 回填 + `SenderAccountBindingService` + 两处建行固化），上游接口见下。

## 上游交付（P1，已验）

- `SenderAccountBindingService.bindingFieldsFor(accountCode, now)` / `resolveForSend(contact, manual)` / `bindIfAbsent(contactId, accountCode, now)`
- 异常 `SenderAccountNotBoundException(contactId)` / `BoundSenderAccountUnavailableException(contactId, accountCode, reason)` 均继承 `IllegalStateException`（400 映射）
- `ExpertContactRepository.updateBindingById(id, accountCode: String?, boundAt: LocalDateTime?)`
- `expert_contact.bound_sender_account_code` / `sender_account_bound_at`（V85）
- P1 期 `SenderAccountBindingServiceTest` 10 用例全部通过 —— **P2 加 `ignoreWarmup` 形参必须用默认值 `= false`，保证这 10 个用例零改动通过（M-3）**

## 全局约束（主计划，违反即 LIGHT_FAIL）

- **M-1**：本批次严格串行 P1→P2→P3→P4→P5；你是唯一 writer。
- **M-2 方法级所有权（P2 范围，排他）**：
  - `mail/service/MailSenderAccountService.kt` — P2 只改私有谓词 `isManualSendable`（`:227-228`，仅加 `account.enabled &&` 一行）
  - `mail/service/SenderAccountBindingService.kt` — P2 只给 `resolveForSend` 加 `ignoreWarmup: Boolean = false` 形参（M-3 阶段 2），并把 `requireAvailable` 额度分支改为 `warmup.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup)`
  - `mail/service/ManualExpertMailService.kt` — P2 只改 `resolveAccount` + `:55-58`
  - `campaign/service/MeetingScheduleService.kt` — P2 只改 `:109` 账号解析
  - `campaign/service/ManualInitialOutreachService.kt` — P2 只改 `:272`（材料提醒轮解析）+ `:550-582`（首封轮调序）
  - 测试：`MailSenderAccountServiceTest`（改 1 加 1）、`ManualExpertMailServiceTest`（改 1 加 4）、`MeetingScheduleServiceTest`（加 2）
  - 发现需要改本计划名下之外的方法/字段 → 停止并上报，禁止顺手改
- **M-4 锁定测试（红线）**：`MailSenderAccountServiceTest.kt:35-46`（`selects account at daily limit`）与 `:48-57`（`includes auto-paused accounts`）**任何计划不得改，逐字保留**；`:62-74`（`includes disabled accounts`）**仅 P2** 改写为 `excludes disabled accounts`；`ManualExpertMailServiceTest.kt:351-363` **仅 P2** 改写为绑定禁用抛异常。
- **M-5 门禁矩阵**：`manual=true` 只判 `enabled && 非模拟器`（不判暂停、不判额度）；`manual=false` 判全量。矩阵唯一权威实现是 P1 的 I-7。
- **M-6**：本计划迁移：无。禁止新增迁移。
- **M-7 知识写回（P2 Phase 6，串行追加，先读当前内容）**：
  1. `docs/knowledge/mail/K-sender-account-enabled-scope.md` — 把规划阶段的"收窄口径"补充段里"计划中"改为"已生效 + 落地 commit"
  2. `docs/knowledge/mail/K-sender-account-selection-sites.md` — 更新 A/B 两类选号决策点的现状（已收口到 `resolveForSend`）
  3. （可选）`docs/knowledge/mail/K-operator-send-quota-paths.md` — 只追加"本批次未改变该决策"一句，**禁止改写既有正文**
- **M-8**：不做五件事（存量再平衡 / 人工超限 / ES 字段 / 已变更筛选 / DB tags 缺陷）。
- **G-1**：回复路径零改动（I-6 名单：`PendingMailOperationService.kt`、`AutoMailReplyService.kt`、`AutoReplyPreviewService.kt`、`TrustReplyWorkbenchService.kt`）。
- **G-2**：NULL = 未绑定。**G-3**：SIMULATOR_NOOP 永不入绑定。

## 变更文件清单（P2 的 10 个授权文件；A3/A4 修订新增第 9/10 项）

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`（**A3 修订授权，仅测试适配**：补 `senderAccountBindingService.resolveForSend` 桩 —— 已绑定 contact → 返回该账号；未绑定 → 抛 `SenderAccountNotBoundException`。不新增用例、不改既有断言语义）
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt`（**A4 修订授权，仅编译修复**：`:54` 构造实参尾部追加 `Mockito.mock(SenderAccountBindingService::class.java)`）

知识写回：上列 M-7 的 2-3 个 knowledge 文件（追加）。

## 恢复要求（epoch 2，A3/A4/A5 已批准）

- **必须回退** `ManualExpertMailService.kt` 中 epoch 1 的可空参数变通（`senderAccountBindingService: SenderAccountBindingService? = null` + `!!`/`?.` 守卫）→ 恢复计划 T2.1 的**非空**构造注入 `private val senderAccountBindingService: SenderAccountBindingService`，`resolveAccount` 中直接调用（无 `!!`/`?.`）。
- epoch 1 的其余实施成果保留在工作区未提交：核对其符合计划后继续（`MailSenderAccountService.kt` 的 `isManualSendable` 逐字形态、`SenderAccountBindingService` 的 ignoreWarmup 默认形参、材料提醒轮/首封轮控制流、`MeetingScheduleService:109`、测试改写等）。
- 完成后跑全量命令并提交 `feat(fast-p): implement p2`。

## 关键实现要点（详见计划正文）

- **材料提醒轮（`:271-341`）**：绑定优先解析，`SenderAccountNotBoundException` → 兜底选号 + `bindIfAbsent`；`BoundSenderAccountUnavailableException` → **per-contact 跳过**（`accumulator.recordSkipped(SEND_EXCEPTION, "绑定账号不可用（${code}/${reason}）：$email")`）+ `continue`，**不得**升级 `midRoundStop`。两个绑定异常的 catch 分支必须写在通用 `catch (e: Exception)` **之前**（Kotlin 按声明顺序匹配）。`ManualMailSendCommand(senderAccountCode = account.accountCode)` 保持传值；`incrementTodaySentCount` / `assignments.add` 不动。**不新增 `BatchOutcomeReasonCodes` 枚举值**。
- **首封轮（`:550-582`）调序**：先确定 contact 再解析账号；`existingContact` 有绑定 → `resolveForSend(contact, manual = false, ignoreWarmup = ignoreWarmup)`，`BoundSenderAccountUnavailableException` → 跳过 + continue；无绑定 → 原 `selectAccount` 兜底。`NoAvailableSenderAccountException` → PAUSED / 通用 Exception → FAILED 的既有语义保持，只缩小包裹范围。
- **计数器纪律**：`processedTotal` / `roundSent` / `roundProcessed` / `roundRejected` 四计数器每专家推进次数与改动前一致（文件 `:325-327` 有重复计数警告注释）。
- **`ManualExpertMailService.resolveAccount`**：I-3 一致性校验（显式 code ≠ 绑定 → `IllegalArgumentException("发件账号与专家绑定不一致：请求 $requested，绑定 $bound（contactId=$contactId）")`）；有绑定 → `resolveForSend(contact, manual = true)`；无绑定 → 显式 code 优先（`getManualSendAccount`）否则 `selectAccountForManualSending()`，然后 `bindIfAbsent`。
- **`MeetingScheduleService:109`**：`resolveForSend(contact, manual = true)`，仅 `SenderAccountNotBoundException` 时兜底 `selectAccountForSending()` + `bindIfAbsent`；`BoundSenderAccountUnavailableException` 不捕获（单次运营动作，直接 400）。`:155-160` 的 `todaySentCount + 1` 不动。
- **`isManualSendable`** 逐字改为 `account.enabled && account.accountCode != SIMULATOR_ACCOUNT_CODE`，禁止加额度/暂停判定。

## 必须验证的命令（JDK 11 zulu；裸 mvn 会失败）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailSenderAccountServiceTest,ManualExpertMailServiceTest,ManualExpertMailServiceGateTest,MeetingScheduleServiceTest,ManualInitialOutreachServiceTest,SenderAccountBindingServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending selects account at daily limit'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending includes auto-paused accounts'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

> 已知怪癖（P1 验证发现）：`-Dtest=A+B` 的 `+` 分隔在 surefire 2.22.2 下报 "No tests were executed"（exit 1）；**用逗号分隔**。计划正文里的 `+` 写法是文档瑕疵，不影响判据。
> 通过判据：退出码 0、`Tests run: N, Failures: 0, Errors: 0`、`BUILD SUCCESS`。基线：P1 后 2249 tests / 0 F / 0 E / 4 skipped、node 479/0。

## 下流接口（后续子计划依赖）

- `resolveForSend(contact: ExpertContact, manual: Boolean, ignoreWarmup: Boolean = false): MailSenderAccount`（阶段 2 最终签名；P3/P4/P5 不得再改）
- 首封轮/材料提醒轮语义：绑定优先、无绑定兜底 + 补写绑定、绑定异常 per-contact 跳过（P3 的快照只影响**无绑定兜底**的 `selectAccount` 调用）
- `selectAccount(expert, assignments, ignoreWarmup)` 仍是兜底入口（P3 会加 `stock` 形参，默认 EMPTY）
- 兜底后 `bindIfAbsent` 补写 → 新绑定对 P3 存量快照（批次开始时取）不可见，属 IP-1 语义

## 产物与提交

- 实现 commit 消息：`feat(fast-p): implement p2`
- 完整执行结果追加到 `docs/plans/fast/sender-binding/children/p2/execution.md`
- 只提交实现文件 + execution.md；**不提交** ledger/verify-log/fix-log。
- 返回格式：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + report 路径。
