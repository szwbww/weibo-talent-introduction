# p2 execution log


## Execution Result: PLAN_CONFLICT（完成需改未授权文件）

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding/docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md`
- Plan SHA-256: `fcd38ca61fab23171ab801a28333832cf2e205a4f84fa252f4c0c5bf61cf9107`
- Execution ID: `…/sender-binding-02-send-path-consistency.md@fcd38ca6`
- Execution epoch: NEW（本批次首个 p2 执行）
- Approval basis: 本 invocation（controller 分派）
- Executor: `P2Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding`
- Target branch: `fast/sender-binding`（git_dir `.git/worktrees/sender-binding`）
- Worktree ID: `…/sender-binding@fast/sender-binding@…/.git/worktrees/sender-binding`
- Pre-execution code SHA（p1 code head）: `d957683635a304d7b2f7611053250546f720e638`
- Post-execution code SHA: 无提交（PLAN_CONFLICT，未 commit）
- Evidence HEAD: 无
- Implementation boundary: working-tree diff（base d9576836..未提交工作树）

### 结论（一句话）

8 个授权文件已按计划实现（含 M-3 签名、M-4 红线改写、I-1..I-6、IP-1..IP-3），全部验证命令已执行；
但计划强制要求的「绑定优先解析」（T4.1/T4.2）会使 **未授权文件 `ManualInitialOutreachServiceTest.kt` 的 9 个用例 NPE 失败**
（其 `senderAccountBindingService` mock 未 stub `resolveForSend` → 返回 null），
修好它们必须编辑该文件（加 `resolveForSend` 默认 stub + 改写 `existing contact binding is not overwritten`）。
子计划授权清单（8 文件）与主计划 M-2 矩阵（该测试文件 P2 列 = —）均未授权此文件 → 按 brief「发现需要改本计划名下之外的方法/字段 → 停止并上报」与 execute-p 规则，判定 **PLAN_CONFLICT**。

### 已实现（全部在授权范围内）

| 任务 | 文件 | 内容 |
|---|---|---|
| T1.1 | `MailSenderAccountService.kt` | `isManualSendable` 改为 `account.enabled && account.accountCode != SIMULATOR_ACCOUNT_CODE`（I-5 逐字形态） |
| T1.2 | `SenderAccountBindingService.kt` | `resolveForSend` 加 `ignoreWarmup: Boolean = false` 默认形参（M-3）；`requireAvailable` 额度分支改 `warmup.effectiveDailyLimit(account, ignoreWarmup=ignoreWarmup)` |
| T2.1 | `ManualExpertMailService.kt` | 构造注入 `senderAccountBindingService`；`:55-58` 改 `resolveAccount(contact, command.senderAccountCode)`；新增 `resolveAccount`（I-3 一致性校验 + 绑定优先 + 无绑定兜底 `getManualSendAccount`/`selectAccountForManualSending` + `bindIfAbsent` 补写） |
| T3.1 | `MeetingScheduleService.kt` | 构造注入；`:109` 改 `try { resolveForSend(contact, manual=true) } catch (SenderAccountNotBoundException) { selectAccountForSending() + bindIfAbsent }`；`BoundSenderAccountUnavailableException` 不捕获（400 直达） |
| T4.1 | `ManualInitialOutreachService.kt` | 材料提醒轮 `:272` 改绑定优先：`resolveForSend(manual=true)` → NotBound 兜底选号+bindIfAbsent（NoAvailable→PAUSED+midRoundStop）→ BoundUnavailable per-contact `recordSkipped(SEND_EXCEPTION)` + 四计数器自增 + `updateProgressWithAccumulator` + `continue`（I-4；两个绑定异常 catch 均在通用 catch 之前） |
| T4.2 | 同上 | 首封轮调序：`existingContact` 有绑定 → `resolveForSend(manual=false, ignoreWarmup=ignoreWarmup)`，BoundUnavailable → 跳过+continue；无绑定 → 原 `selectAccount` 兜底（PAUSED/FAILED 语义保持，只收窄包裹范围）；新建分支沿用 P1 建行固化 |
| T5.1 | `MailSenderAccountServiceTest.kt` | `:62-74` 改写为 `excludes disabled accounts`；新增 `throws when all accounts disabled`；`:35-46`/`:48-57` 逐字未动（M-4 红线） |
| T5.2 | `ManualExpertMailServiceTest.kt` | `:351-363` 改写为 `throws when bound account is disabled`；新增 4 例（uses bound account / conflicts with binding / falls back and binds / allows auto-paused bound account） |
| T5.3 | `MeetingScheduleServiceTest.kt` | 新增 `uses bound account`、`throws when bound account disabled`；既有无绑定用例补 `resolveForSend→NotBound` stub 走兜底（计划明示保留 selectAccountForSending stub） |
| M-7 | 3 个 knowledge 文件 | `K-sender-account-enabled-scope.md`（"正在被收窄"→"已收窄并生效"+落地 commit）、`K-sender-account-selection-sites.md`（A/B 收口到 resolveForSend）、`K-operator-send-quota-paths.md`（追加"本批次未改变该决策"一句） |

### 唯一偏差（授权文件内，已最小化）

`ManualExpertMailService` 的 `senderAccountBindingService` 形参声明为 `SenderAccountBindingService? = null`
（默认 null），`resolveAccount` 中绑定分支用 `!!`、兜底补写用 `?.`。
原因：计划 T2.1 要求构造注入，而 **`ManualExpertMailServiceGateTest.kt`（未授权）以位置参数+具名参数构造该服务**，
非空无默认形参会使其编译失败；该测试的 contact 全部无绑定，`?.` 在测试中为 no-op、生产由 Spring 恒注入非空，行为零差异。
若主计划后续授权 GateTest，可将形参改回非空并删除 `?.`。

### 命令结果（全部于本 invocation 新鲜执行，JDK 11 zulu）

| 命令 | 结果 |
|---|---|
| `mvn test -Dtest=MailSenderAccountServiceTest,ManualExpertMailServiceTest,ManualExpertMailServiceGateTest,MeetingScheduleServiceTest,ManualInitialOutreachServiceTest,SenderAccountBindingServiceTest` | exit 1；`Tests run: 136, Failures: 0, Errors: 2, Skipped: 0`（2 Errors 均在 ManualInitialOutreachServiceTest，即下述冲突证据；其余 5 类全绿，含 SenderAccountBindingServiceTest 10 例零改动通过） |
| `mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending selects account at daily limit'` | exit 0；`Tests run: 1, Failures: 0, Errors: 0` |
| `mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending includes auto-paused accounts'` | exit 0；`Tests run: 1, Failures: 0, Errors: 0` |
| `mvn test`（全量） | exit 1；`Tests run: 2256, Failures: 0, Errors: 9, Skipped: 4` → BUILD FAILURE |
| `mvn clean package` | exit 1；同上 `Tests run: 2256, Failures: 0, Errors: 9, Skipped: 4` → BUILD FAILURE |
| `git diff --check` | exit 0，无空白/换行问题 |
| 基线对照 | P1 后基线 2249/0/0/4；本实现净增 7 个测试（+1/+4/+2）→ 2256，非冲突用例全部通过 |

### 冲突证据（全量 9 个 Errors 全部位于未授权文件 `ManualInitialOutreachServiceTest.kt`）

```
ManualInitialOutreachServiceTest.existing contact binding is not overwritten:313 » NullPointer
ManualInitialOutreachServiceTest.runMaterialReminderBatch gate rejection … (V-1):1377 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch does not modify tags … (I-5):1543 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch sends via COMPOSE_TEMPLATE … (I-10):1594 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch with oneRoundOnly … (I-9):1638 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch respects dailyCap … (I-9):1689 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch seeds dailyCap … (R1):1746 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch does not count FAILED … (R1):1803 » NullPointer
ManualInitialOutreachServiceTest$ReminderBatchTests.runMaterialReminderBatch second invocation … (R1):1860 » NullPointer
```

根因：该测试文件的 `senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)` **没有任何 `resolveForSend` stub**（setUp 只 stub `bindingFieldsFor`）。
计划 T4.1/T4.2 要求两轮恒先调用 `resolveForSend`：mock 对未 stub 方法返回 `null`（而非抛 `SenderAccountNotBoundException`）→ `account = null` →
`runAccountStats.getOrPut(account.accountCode)` NPE。材料提醒轮全部目标、以及 `existing contact binding is not overwritten` 的已绑定 contact 均命中。
修复必须编辑该未授权文件：setUp 加默认 stub（`resolveForSend(any, anyBoolean, anyBoolean)` → throw `SenderAccountNotBoundException`，或逐用例 stub 返回账号），
并把 `existing contact binding is not overwritten` 改写为绑定优先语义（stub `resolveForSend` 返回账号并断言仍不重绑、不 save）——该用例的旧前提（已绑定 contact 用**另选**账号发送）恰是 P2 要修复的缺陷，语义已被计划取代。
生产侧无合法规避（对 `resolveForSend` 返回 null 做 `?: throw NotBound` 属于为测试而写的死代码，违反「不解决症状」原则，且无法挽救 `existing contact binding is not overwritten` 的绑定语义）。

### 需要主计划裁决的缺口（建议 amendment 内容）

1. **授权 `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` 给 P2**（仅 mock stub 与 `existing contact binding is not overwritten` 改写；不新增用例），并把 brief 变更文件清单 8→9；
2. （可选）**授权 `ManualExpertMailServiceGateTest.kt`**（构造实参 +1），从而把 `senderAccountBindingService` 形参改回非空、删除 `?.` 偏差。

### 变更文件（未提交，全部在授权范围内）

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt`
- `docs/knowledge/mail/K-sender-account-enabled-scope.md`、`K-sender-account-selection-sites.md`、`K-operator-send-quota-paths.md`（M-7）

未触碰：ledger/verify-log/fix-log/brief、回复路径四文件（I-6/G-1）、`docs/plans/fast/` 其余内容。未 commit（PLAN_CONFLICT 不产生提交）。

### 后续

- 主计划 amendment（上述两项）后，可 RESUME 本执行：补齐未授权测试文件改动 → 全量验证（应恢复 2256/0/0/4 全绿）→ 按 brief 提交 `feat(fast-p): implement p2`。

---

## Execution Result: READY_FOR_VERIFICATION（epoch 2，RESUME；A3/A4/A5 已批准）

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding/docs/plans/2026-08-10/sender-binding-02-send-path-consistency.md`
- Plan SHA-256: `fb7ede3619db031f80d208878cb338e3c647b4a331015b90108077b5ca0be41f`
- Execution ID: `…/sender-binding-02-send-path-consistency.md@fb7ede3619db031f80d208878cb338e3c647b4a331015b90108077b5ca0be41f`
- Execution epoch: RESUME（epoch 1 在 PLAN_CONFLICT 停于未提交工作树；本 invocation 持 A3/A4/A5 修订继续）
- Approval basis: 本 invocation（controller 分派，human-approved amendments A3/A4/A5）
- Executor: `P2Implementer-2`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/sender-binding`
- Target branch: `fast/sender-binding`（git_dir `.git/worktrees/sender-binding`）
- Worktree ID: `…/sender-binding@fast/sender-binding@…/.git/worktrees/sender-binding`
- Pre-execution code SHA: `5d222f1a1c5851cf54478cbca9353ee4819d634f`（A3/A4/A5 修订后的 HEAD）
- Post-execution code SHA: `5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b`
- Evidence HEAD: `5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b`（实现 commit 即 HEAD；无独立证据 commit）
- Implementation boundary: `5d222f1..5dc9f95`（13 文件：10 授权 + 3 M-7 knowledge）

### 结论（一句话）

epoch 1 的全部实现经核对符合计划并保留；唯一偏差（`ManualExpertMailService.senderAccountBindingService` 可空形参 + `!!`/`?.`）已按要求回退为非空注入；
A3（`ManualInitialOutreachServiceTest` 补 `resolveForSend` 桩，9 个 NPE 用例修复，零新增用例、零断言语义改动）与 A4（`ManualExpertMailServiceGateTest` 构造实参尾部追加 mock）已落地；
计划验证命令全部通过（全量 2256/0/0/4），已提交 `feat(fast-p): implement p2`（fast-p 报告/日志排除在外）。

### epoch 2 处置明细

- **回退（MUST）**：`ManualExpertMailService.kt` 构造形参 `SenderAccountBindingService? = null` → 非空 `private val senderAccountBindingService: SenderAccountBindingService`；`resolveAccount` 绑定分支 `!!.resolveForSend` → 直接调用；兜底补写 `?.bindIfAbsent` → 直接调用。无 `!!`/`?.` 残留（grep 验证：:31 非空形参、:157/:163 直接调用）。
- **保留（epoch 1 核对通过）**：T1.1 `isManualSendable` 逐字形态（`account.enabled && account.accountCode != SIMULATOR_ACCOUNT_CODE`）；T1.2 `resolveForSend(contact, manual, ignoreWarmup=false)` 默认形参 + `requireAvailable` 额度分支；T3.1 `MeetingScheduleService:109` 绑定解析 + NotBound 兜底；T4.1 材料提醒轮（绑定优先、NotBound 兜底 + bindIfAbsent、BoundUnavailable per-contact `recordSkipped(SEND_EXCEPTION)` + 四计数器 + `continue`，两个绑定异常 catch 在通用 catch 之前）；T4.2 首封轮调序（先 contact 后解析、`manual=false, ignoreWarmup=ignoreWarmup`、BoundUnavailable 跳过、无绑定兜底 PAUSED/FAILED 语义收窄保留）；计数器纪律与改动前一致（逐段对照 HEAD 原码）；T5.1/T5.2/T5.3 测试改写；M-7 三文件写回。
- **A3 落地**：`ManualInitialOutreachServiceTest.kt` 新增私有桩 `stubReminderResolveForSendNotBound()`（未绑定 → 抛 `SenderAccountNotBoundException` → 走各用例既有 `selectAccount` 兜底；manual=true/ignoreWarmup=false 匹配材料提醒轮调用形态）+ `SenderAccountNotBoundException` import；8 个材料提醒用例（V-1、I-5、I-10、I-9×2、R1×3）在 selectAccount 桩后各加 1 行调用；`existing contact binding is not overwritten` 内联桩 `resolveForSend(any, eqValue(false), eqValue(true))` → 返回该用例账号（I-1 绑定优先、不重选号、不补写绑定）。未新增用例、未改任何断言。核对了 I-6 等其余用例：`runMaterialReminderBatch skips contact with SENT MATERIAL_REMINDER (I-6)` 因 target 构建期 `hasSentMaterialReminder` 过滤（生产 :1170）根本不到达 `resolveForSend`，无需桩，保持零改动。
- **A4 落地**：`ManualExpertMailServiceGateTest.kt` 构造实参（`conversationStateService` 之后、具名参数之前）追加 `Mockito.mock(SenderAccountBindingService::class.java)`，其余零改动。

### 命令结果（全部于本 invocation 新鲜执行，JDK 11 zulu）

| 命令 | 结果 |
|---|---|
| `mvn test -Dtest=ManualInitialOutreachServiceTest,ManualInitialOutreachServiceTest$ReminderBatchTests` | exit 0；`Tests run: 53, Failures: 0, Errors: 0`（外类 42 + 嵌套 11 全绿） |
| `mvn test -Dtest=MailSenderAccountServiceTest,ManualExpertMailServiceTest,ManualExpertMailServiceGateTest,MeetingScheduleServiceTest,ManualInitialOutreachServiceTest,SenderAccountBindingServiceTest` | exit 0；`Tests run: 136, Failures: 0, Errors: 0`（含 SenderAccountBindingServiceTest 10 例零改动通过，M-3 满足） |
| `mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending selects account at daily limit'` | exit 0；`Tests run: 1, Failures: 0, Errors: 0`（M-4 红线，文件逐字未改） |
| `mvn test -Dtest='MailSenderAccountServiceTest#selectAccountForManualSending includes auto-paused accounts'` | exit 0；`Tests run: 1, Failures: 0, Errors: 0`（M-4 红线，文件逐字未改） |
| `mvn test`（全量） | exit 0；`Tests run: 2256, Failures: 0, Errors: 0, Skipped: 4`，BUILD SUCCESS |
| `mvn clean package` | exit 0；`Tests run: 2256, Failures: 0, Errors: 0, Skipped: 4`，BUILD SUCCESS |
| `git diff --check` | exit 0，无空白/换行问题 |

### 提交

- `5dc9f95cb4782c68b53dd0ecdbaa89853ecb9a3b` — `feat(fast-p): implement p2`（13 文件 +428 -48：10 授权文件 + 3 M-7 knowledge 文件）
- 已排除：ledger.md、p2/p3/p4/p5 children 目录（brief/verify-log/fix-log/execution.md）——fast-p 证据由 controller 单独提交。
- 提交后工作树仅剩 `M docs/plans/fast/sender-binding/ledger.md` 与 4 个 untracked fast-p 目录（均非本计划产物，未触碰）。

### Deviations

- None（epoch 1 的可空形参偏差已在 epoch 2 按计划回退；`-Dtest` 使用逗号分隔，`+` 语法为 surefire 2.22.2 已知故障，brief 已注明不影响判据）。

### Freshness

- Plan identity rechecked: YES（前后均 `fb7ede36…`）
- Worktree identity rechecked: YES
- Reported commit reachable from target branch: YES（HEAD = 5dc9f95，branch fast/sender-binding）
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
