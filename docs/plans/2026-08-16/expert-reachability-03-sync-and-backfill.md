# 计划 03 — 写入方法、sync 服务、手动端点与增量挂载点

> 依赖：计划 02。后继：计划 04 / 05（可并行）。共享证据见主计划。
>
> **2026-08-16 口径修正**：`classify()` 不再需要 `currentYear` 参数；UNKNOWN 判据改为 `emailSource` 缺失。
> 详见主计划「修正记录」A-1~A-4。

## 需求描述

**Observable outcome**

1. 存在 `POST /api/experts/sync-reachability` 端点，可手动触发全量扫描并回填 `reachability`；返回 `BulkSyncResult`（total/success/failure/skipped）。
2. 该任务在任务记录页可见（走 `TaskExecutionService`），执行中重复触发返回 409。
3. 专家退订或首次硬退后，该专家的 `reachability` 立即变为对应 BLOCKED 值，无需等待下一轮全量扫描。

**What must NOT change**

- N-1 `syncOperatorStatusBatch()`（`ExpertIndexWriterService:113-211`）与 `resolveOrcidToDocIds()`（`:213`）一行不改。
- N-2 `EmailSuppressionService.suppress()` 的既有幂等语义与返回值含义（「是否新增」）不变；其**事务边界与 MySQL 写入结果不受 ES 影响**。
- N-3 `BounceCollectionService` 的退信落库、`EMAIL_INVALID` 标记与账号限流逻辑不变。
- N-4 不改 `BulkSyncResult` 定义（`:673-688`）。

**Out of scope**

- O-1 只读对账服务（主计划 O-2）。
- O-2 RAW 层写入（主计划 I-4）。
- O-3 前端触发按钮 —— 本计划只交付端点；按钮属计划 04 的前端范围，或由运营用现有任务面板触发。

## 关键不变量

### Invariant I-3-1: UNKNOWN 走删字段脚本，绝不写字符串
- Rule: `syncReachabilityBatch(updates: List<Pair<String, ExpertReachability?>>)` 中，value 为 `null` 时必须走 `ctx._source.remove('reachability')` 的 script 分支；非 null 时走 `doc` 分支写 `esValue`。禁止任何分支写入 `"UNKNOWN"` 字符串。
- Applies to: `ExpertIndexWriterService.syncReachabilityBatch()`。
- Violation consequence: 违反主计划 I-2，双重表示并存。
- 来源: 主计划 I-2；实现范本见主计划 R-8（`syncOperatorStatusBatch:132-139`）

### Invariant I-3-2: 层级循环 = CANDIDATE + APPLICATION
- Rule: `syncReachabilityBatch` 的层级循环为 `listOf(ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)`；全量扫描的驱动层为 CANDIDATE。
- Applies to: `syncReachabilityBatch()`、`ExpertReachabilitySyncService.syncAll()`。
- Violation consequence: 违反主计划 I-4；写 RAW 会因该层无 mapping 而静默不可查。
- 来源: 主计划 I-4

### Invariant I-3-3: 扫描驱动，非联系人驱动
- Rule: 全量扫描必须以 `expertSearchService.scrollExperts(CANDIDATE, ...)` 为驱动源，遍历全部候选专家；禁止以 `expertContactRepository.findAll()` 为驱动。
- Applies to: `ExpertReachabilitySyncService.syncAll()`。
- Violation consequence: `expert_contact` 只含**已建立联系**的专家，而可达性的核心用途是决定「首封发给谁」，目标恰是从未联系过的人。以联系人驱动会让绝大多数候选专家永远保持 UNKNOWN，且现象是「功能上线后没什么变化」，极难归因。
- 来源: original（与 `CandidateOperatorStatusSyncService.reconcileAll()` 的 MySQL 驱动方向**相反**，是本计划与该先例的唯一结构性差异）

### Invariant I-3-4: 长任务走 progressStore，不走简单端点
- Rule: 端点必须使用 `progressStore.tryStartWithToken(...)` + 已在跑返回 409 + `bindExecutionId` + `finally clearExecutionContext` 的完整模式，并在每批 scroll 回调中 `progressStore.update(...)` 上报 `processedCount` / `totalCount`。
- Applies to: `ExpertIndexController` 新增端点。
- Violation consequence: 全量扫描是万级文档量级，非 `/backfill-operator-status` 的两千行量级；无并发保护时运营连点两次即并发跑两遍，且前端无进度。
- 来源: original（模式范本：`ExpertIndexController.revalidateCandidates()` `:157-199`）

### Invariant I-3-5: 增量写入 fail-open
- Rule: 两个增量挂载点必须 `try { ... } catch (e: Exception) { log.warn(...) }` 包裹，异常一律吞掉。
- Applies to: `EmailSuppressionService.suppress()` 的调用点、`BounceCollectionService:104` 的 HARD 分支。
- Violation consequence: 违反主计划 I-5。退订接口因 ES 不可用而失败 = 用户点了退订没生效 = 合规风险。
- 来源: 主计划 I-5

### Invariant I-3-6: mapping 断言前置且 fail-fast
- Rule: `syncAll()` 第一行调用 `expertIndexService.checkReachabilityMapping()`，返回 false 时抛 `IllegalStateException`，不执行任何写入。
- Applies to: `ExpertReachabilitySyncService.syncAll()`。
- Violation consequence: 无 mapping 时字段写进 `_source` 但不进倒排索引，全部筛选恒为 0 命中且无报错。`GlobalExceptionHandler` 将 `IllegalStateException` 映射为 400（`K-custom-exception-http-status-mapping`），端点用 `catch (ex: IllegalStateException)` 返回 badRequest，与 `/backfill-operator-status`（`ExpertIndexController:217-219`）同款。
- 来源: `CandidateOperatorStatusSyncService.reconcileAll():13-17` 的既有前置断言

## 现状审计

### 复制源：`syncOperatorStatusBatch`

见主计划 R-8。关键结构（供逐段对照复制）：
`:119` 层级循环 → `:121` `updates.chunked(500)` → `:126` `resolveOrcidToDocIds` →
`:128-149` 构造 ndjson bulk body（`update` meta + `script`/`doc` data）→
`:152-157` 未命中 `_id` 的计入 `skipped` → `:164-173` `POST /_bulk`（`application/x-ndjson`）→
`:174-190` 逐 item 统计 `success` / `failure`。

**决策：复制而非泛化。** 理由：该方法是发信链路关键写入点（`operatorStatus` 驱动批量发送目标集），
泛化会让 `operatorStatus` 行为进入本计划的回归范围；重复约 60 行 bulk 样板可接受。
`resolveOrcidToDocIds`（`:213`，私有）**直接复用**，不复制。

### 数据源：两张 MySQL 表

```bash
grep -n "fun " src/main/kotlin/com/weibo/talentintroduction/mail/repository/EmailSuppressionRepository.kt
grep -n "fun " src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt
```
两者均无「全量取 email / 全量取 HARD」的现成方法，但 `OperatorStatusReconcileService` 已用
`bounceRecordRepository.findAll()`（`CrudRepository` 继承方法）并在类注释中论证过规模前提：
「全表扫描 + 内存比对（expert_contact 2062 行 / mail_record 2157 行，规模小，一次读入）」。
本计划沿用同一前提，**不新增仓储方法**（控制文件数）。

- 退订集合：`emailSuppressionRepository.findAll()` → `map { it.email }`（该列已按
  `EmailSuppressionService.normalize()` 归一化存储，见 `V30__create_email_suppression.sql:3` 的
  列注释「归一化邮箱(小写trim)」与 `EmailSuppressionService:16`）
- 硬退集合：`bounceRecordRepository.findAll().filter { it.bounceType == "HARD" && it.originalExpertContactId != null }`
  → 经 `expertContactRepository.findAll()` 建 contactId → orcidId 映射
  （过滤写法与 `OperatorStatusReconcileService` 内既有实现逐字同款）

### 增量挂载点

```bash
grep -n "HARD" src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt
```
```
104:        if (signal.bounceType == "HARD" && originalContact != null) {
```

`EmailSuppressionService.suppress()`（`:26-45`）返回 `Boolean`（是否新增），
`repository.save` 之后、`return true` 之前是挂载位置。

### Interaction points

| # | 写入 | 读取 | 处置 |
|---|------|------|------|
| IP-1 | `syncReachabilityBatch` 写 ES | 计划 05 的 4 处筛选 | 由 I-3-1/I-3-2 保证值语义与层级一致 |
| IP-2 | `syncReachabilityBatch` 写 ES | 计划 04 的列表徽章 | 同上 |
| IP-3 | `EmailSuppressionService.suppress` | 全量扫描的退订集合 | 增量与全量同源同口径（都读 `email_suppression`），全量会覆盖增量结果，天然自愈 |
| IP-4 | `BounceCollectionService:104` HARD 分支 | 全量扫描的硬退集合 | 同 IP-3 |
| IP-5 | `syncReachabilityBatch` 的 `updatedAt` 写入 | `ExpertSearchService` 的 `sortBy=updatedAt` | **注意**：`syncOperatorStatusBatch` 同时更新 `updatedAt`；本方法若照抄，全量回填会把全部候选专家的 `updatedAt` 刷成同一时刻，破坏「按更新时间排序」的既有语义。**决策：本方法不写 `updatedAt`。** |

## 实现方案

### T1 — `syncReachabilityBatch`（遵 I-3-1、I-3-2、IP-5）

`ExpertIndexWriterService` 新增。签名 `fun syncReachabilityBatch(updates: List<Pair<String, ExpertReachability?>>): BulkSyncResult`。
结构逐段对照 `:113-211` 复制，四点差异：① 层级列表去 RAW；② 字段名 `reachability`；
③ script 分支的触发条件由 `== "NOT_CONTACTED"` 改为 `value == null`；
④ **doc 与 script 分支均不写 `updatedAt`**（见 IP-5）。

### T2 — `checkReachabilityMapping` 调用（遵 I-3-6）
计划 02 已实现该方法，本计划只在 `syncAll()` 首行调用。

### T3 — `ExpertReachabilitySyncService`（遵 I-3-3、I-3-4、I-3-6）

新建 `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncService.kt`：

```
fun syncAll(): BulkSyncResult
  1. checkReachabilityMapping() 否则 IllegalStateException
  2. 装配 suppressedEmails / hardBouncedOrcids（各一次全表读）
  3. val currentYear = Year.now().value（在此取一次，传给 classify —— 遵计划 02 的 I-2-2）
  4. scrollExperts(CANDIDATE, 500) { batch, batchNumber, totalHits ->
         val updates = batch.map { it.orcidId to classifier.classify(it, suppressedEmails, hardBouncedOrcids) }
         result += writerService.syncReachabilityBatch(updates)
         progressStore.update("EXPERT_REACHABILITY_SYNC", TaskProgress(...))
         !progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")
     }
```

注意 `updates` 用 `map` 而非 `mapNotNull`：value 为 null 时仍需下发 remove 脚本，
以便「曾经是 HIGH、现在数据被清空」的专家能退回 UNKNOWN。

### T4 — 端点（遵 I-3-4）

`ExpertIndexController` 新增 `POST /sync-reachability`，逐段照抄
`revalidateCandidates()`（`:157-199`）的 progressStore 模式，taskType 为 `EXPERT_REACHABILITY_SYNC`，
`catch (ex: IllegalStateException)` 返回 400。

### T5 — 两个增量挂载点（遵 I-3-5）

- `EmailSuppressionService.suppress()`：新增成功后调用
  `reachabilitySyncService.markBlockedByEmail(normalizedEmail)`（新方法：查 orcid → 单条
  `syncReachabilityBatch(listOf(orcid to BLOCKED_UNSUBSCRIBED))`），try/catch 吞异常。
- `BounceCollectionService:104` 的 HARD 分支：同款调用
  `markBlockedByContact(originalContact)`，try/catch 吞异常。

**循环依赖检查**：`EmailSuppressionService` 属 `mail` 模块，`ExpertReachabilitySyncService` 属 `expert` 模块，
后者依赖 `EmailSuppressionRepository`（repository 而非 service），不构成 service 层环。
若 Spring 仍报环，改用 `ObjectProvider` 惰性注入（先例：`K-mail-queue-fallback` 的
`ObjectProvider.getIfAvailable()` 模式）。

### T6 — 定时挂载

`MailAutomationScheduler` 新增日频 cron 项，包在 `taskExecutionService.runAndRecordWithResult(...)` 中
（签名见主计划 R-15），受既有 `talent-introduction.scheduling.enabled` 开关门控。

### T7 — 测试

`ExpertReachabilitySyncServiceTest`：mapping 断言失败即抛且零写入、scroll 分批聚合结果、
null value 走 remove 分支、层级只含 CANDIDATE+APPLICATION、增量方法异常被吞。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | T1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncService.kt` | 新增（T3/T5） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | T4 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt` | T5 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` | T5 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt` | T6 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncServiceTest.kt` | 新增（T7） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt` | 补增量调用断言 |

文件数 8 ≤ 10。子系统 2（expert 写入/同步 / mail 增量挂载）。新增 ES 字段 0（计划 02 已加）。

## 验证命令

见主计划「验证命令」节。本计划专属：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilitySyncServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=EmailSuppressionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest
```

## 验收标准

- I-3-1：单测断言 value=null 时 bulk body 含 `ctx._source.remove('reachability')`；`grep -rn '"UNKNOWN"' src/main/kotlin/com/weibo/talentintroduction/expert/` 零命中。
- I-3-2：`grep -n "ExpertIndexLevel.RAW" src/main/kotlin/.../ExpertIndexWriterService.kt` 在 `syncReachabilityBatch` 函数体范围内零命中。
- I-3-3：`grep -n "expertContactRepository" src/main/kotlin/.../ExpertReachabilitySyncService.kt` 的命中仅出现在硬退 contactId→orcidId 映射中，不出现在扫描驱动位置；`grep -n "scrollExperts" src/main/kotlin/.../ExpertReachabilitySyncService.kt` 命中 1 处。
- I-3-4：`grep -n "tryStartWithToken\|clearExecutionContext" src/main/kotlin/.../ExpertIndexController.kt` 在新端点函数体内各命中 ≥1。
- I-3-5：`grep -n "catch" src/main/kotlin/.../EmailSuppressionService.kt src/main/kotlin/.../BounceCollectionService.kt` 覆盖新增调用；单测断言 sync 抛异常时 `suppress()` 仍返回 true 且 MySQL 记录存在。
- I-3-6：单测断言 `checkReachabilityMapping()` 返回 false 时抛 `IllegalStateException` 且 `syncReachabilityBatch` 零调用。
- IP-5：`git diff` 中 `syncReachabilityBatch` 函数体不含 `updatedAt`。
- N-1：`git diff src/main/kotlin/.../ExpertIndexWriterService.kt` 中 `syncOperatorStatusBatch` 与 `resolveOrcidToDocIds` 函数体零改动行。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 全量回填首次执行
- 前置条件: 计划 02 已上线且 A-1（mapping 推送）通过；ES 中 CANDIDATE 层有数据。
- 操作步骤: 1) 调用 `POST /api/experts/sync-reachability`。2) 在任务记录页找到 `EXPERT_REACHABILITY_SYNC` 记录。3) 记录返回的 total / success / failure / skipped 四个数字。
- 预期结果: 任务状态 `SUCCESS` 或 `PARTIAL_SUCCESS`；`failure` 为 0；`skipped` 应显著小于 total（skipped 表示「该 orcid 在该层查不到 `_id`」，APPLICATION 层天然少于 CANDIDATE，故 skipped 不为 0 属正常，但若接近 total 说明 `resolveOrcidToDocIds` 有问题，须停止并排查）。
- 覆盖: Observable outcome 1

### A-2: 执行中重复触发返回 409
- 前置条件: 全量任务正在执行（数据量足够大或人为放慢）。
- 操作步骤: 在第一次调用未返回时，再次调用 `POST /api/experts/sync-reachability`。
- 预期结果: 第二次返回 HTTP 409，响应体 message 为「任务正在执行中，请等待完成」；任务记录页只有一条 RUNNING 记录。
- 覆盖: I-3-4 / Observable outcome 2

### A-3: 从未联系过的专家也被赋值
- 前置条件: 在 CANDIDATE 层选一位 `expert_contact` 表中**不存在**对应行的专家（从未发过信）。
- 操作步骤: 1) 回填完成后，ES 查询该文档 `_source.reachability`。
- 预期结果: 该字段有值（`HIGH` / `LOW` 之一），或在该专家无 `emailSource` 时字段缺失。**不得**出现「有 emailSource 却字段缺失」的情况。
- 覆盖: I-3-3

### A-4: 退订后立即生效
- 前置条件: 选一位当前 `reachability` 为 `HIGH` 或 `LOW` 的专家。
- 操作步骤: 1) 通过一键退订链接或 `POST /api/email-suppressions` 将其邮箱加入抑制名单。2) 立即（不触发全量任务）查询该专家 ES 文档。
- 预期结果: `_source.reachability` 变为 `BLOCKED_UNSUBSCRIBED`；`email_suppression` 表新增一行。
- 覆盖: Observable outcome 3 / IP-3

### A-5: 回归 —— ES 不可用时退订仍成功
- 前置条件: 临时使 ES 不可达（改配置指向错误端口并重启，或断开网络）。
- 操作步骤: 1) 执行一次退订操作。2) 查询 `email_suppression` 表。3) 查看应用日志。
- 预期结果: 退订接口返回成功；`email_suppression` 表新增该行；日志中有一条 WARN 记录 ES 写入失败；**接口未返回 5xx**。
- 覆盖: I-3-5 / N-2

### A-6: 回归 —— operatorStatus 与更新时间排序未受影响
- 前置条件: 全量回填已跑完。
- 操作步骤: 1) 专家列表按「更新时间」排序，记录前 10 位。2) 触发一次 `POST /api/experts/backfill-operator-status`（若 A-1 前该端点可用）。3) 再次按更新时间排序对比。
- 预期结果: 回填可达性**没有**把全部专家的更新时间刷成同一时刻；排序结果与回填前基本一致（仅因期间真实业务变更而不同）。
- 覆盖: IP-5 / N-1 / N-4
