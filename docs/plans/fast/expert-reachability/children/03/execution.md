# Child 03 执行报告 — 写入方法、sync 服务、手动端点与增量挂载点

- Child: 03
- Plan: docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md
- Plan SHA-256: `34f601959d002917722457c40b92c68791823139d4569fde94f88539935add59`（开始与结束各复核一次，未变）
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability@fast/expert-reachability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-reachability`
- Pre-execution code SHA: `5396782203892adcc0dc69cc5160a2ec9a21fa6e`（child 02 code head）
- Evidence HEAD（开始/结束）: `c878763b29fcd66066664f820023677152c9ac38`（child 02 证据提交，未产生任何新提交）
- 执行日期: 2026-08-17
- **结果: PLAN_CONFLICT**（详见「冲突说明」）

## 变更总览（8 个授权文件，无越界）

| # | 文件 | 任务 | 状态 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | T1 | 新增 `syncReachabilityBatch(updates: List<Pair<String, ExpertReachability?>>): BulkSyncResult` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncService.kt` | T3/T5 | 新增 `@Service`：`syncAll()` / `markBlockedByEmail()` / `markBlockedByContact()` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | T4 | 新增 `POST /api/experts/sync-reachability` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt` | T5 | `suppress()` 新增成功后调 `markBlockedByEmail`，try/catch 吞异常 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` | T5 | HARD 分支（`:104`）新增 `markBlockedByContact`，try/catch 吞异常 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt` | T6 | 新增日频 cron `reachability-sync`，包 `runAndRecordWithResult` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilitySyncServiceTest.kt` | T7 | 新增 10 用例 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt` | T7 | 补 4 个增量调用断言 |

未改动：`syncOperatorStatusBatch()` / `resolveOrcidToDocIds()`（N-1，writer diff 为纯增量）、`BulkSyncResult`（N-4）、
`EmailSuppressionService.suppress()` 幂等语义（N-2）、`BounceCollectionService` 退信落库 / `EMAIL_INVALID` / 限流（N-3）。

## 各任务实现要点

### T1 — syncReachabilityBatch（I-3-1 / I-3-2 / IP-5）

逐段对照 `syncOperatorStatusBatch`（`:113-211`）复制，四点差异：
① 层级循环 `listOf(ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)`（无 RAW）；
② 字段名 `reachability`，doc 分支写 `reachability.esValue`；
③ script 分支触发条件 `reachability == null`，script 源为
`if (ctx._source.containsKey('reachability')) { ctx._source.remove('reachability'); }`（绝不写未知档字符串）；
④ **两个分支均不写 `updatedAt`**（IP-5）。
复用私有 `resolveOrcidToDocIds`（直接调用，未复制）。`chunked(500)` / `_bulk` ndjson / skipped 统计 / 逐 item 计数全部同构。

### T3 — ExpertReachabilitySyncService（I-3-3 / I-3-4 / I-3-6）

- `syncAll()` 首行 `checkReachabilityMapping()`，false 即抛 `IllegalStateException`（I-3-6，fail-fast，零写入）；
- 判定集合装配：`emailSuppressionRepository.findAll().map { it.email }.toSet()` +
  硬退集合（`bounceRecordRepository.findAll().filter { it.bounceType == "HARD" && it.originalExpertContactId != null }`
  → `expertContactRepository.findAll()` 建 contactId → orcidId 映射，过滤写法与 `OperatorStatusReconcileService` 同款）；
- 驱动源：`scrollExperts(CANDIDATE, 500) { batch, batchNumber, totalHits -> ... }`（三参重载，**恰 1 处** scrollExperts 调用）；
- `updates` 用 `map` 而非 `mapNotNull`：null value 仍下发 remove 脚本；
- 每批 `progressStore.update("EXPERT_REACHABILITY_SYNC", TaskProgress(...))`（processedCount=累计 total / totalCount=totalHits），
  返回 `!progressStore.isCancelled(...)` 支持取消；
- `markBlockedByEmail(normalizedEmail)`：CANDIDATE 层 email term 查 orcid（查询内部 try/catch fail-open），
  命中则 `syncReachabilityBatch(orcids.map { it to BLOCKED_UNSUBSCRIBED })`；
- `markBlockedByContact(contact)`：`syncReachabilityBatch(listOf(normalize(orcidId) to BLOCKED_BOUNCED))`。

### T4 — 端点（I-3-4）

`POST /api/experts/sync-reachability` 逐段照抄 `revalidateCandidates()` 的 progressStore 模式：
`tryStartWithToken("EXPERT_REACHABILITY_SYNC", TaskProgress(RUNNING))` → 未启动返回 409
（message「任务正在执行中，请等待完成」）→ `runAndRecordWithResult("EXPERT_REACHABILITY_SYNC", "MANUAL", "sync-reachability",
onStarted = bindExecutionId)` 内执行 `syncAll()` → `catch (ex: IllegalStateException)` 返回 400（同 `/backfill-operator-status`）→
`finally clearExecutionContext`。构造参数 `expertReachabilitySyncService: ExpertReachabilitySyncService? = null`
（照 `operatorStatusReconcileService` 尾部可空默认参数先例，既有 `ExpertIndexControllerTest` 9 位置参数无需改动）。

### T5 — 两个增量挂载点（I-3-5）

- `EmailSuppressionService.suppress()`：`repository.save` 之后、`return true` 之前调
  `markBlockedReachability(n)`（私有方法内 try/catch Exception → warn，吞掉）。
  新增构造依赖 `ExpertReachabilitySyncService`（mail→expert 服务依赖；expert 侧仅依赖 mail **repository**，无 service 环）。
- `BounceCollectionService` HARD 分支：`syncOperatorStatus(orcid, "EMAIL_INVALID")` 之后
  `try { reachabilitySyncService?.markBlockedByContact(originalContact) } catch (e) { log.warn }`。
  构造参数带 `= null` 默认（`BounceCollectionServiceTest` / `BounceBackfillServiceTest` 为未授权文件，位置/具名构造不破坏）。

### T6 — 定时挂载

`MailAutomationScheduler` 新增 `@Scheduled(cron = "\${talent-introduction.scheduling.reachability-sync-cron:-}")`
`scheduleReachabilitySync()`，包 `taskExecutionService.runAndRecordWithResult("EXPERT_REACHABILITY_SYNC", "SCHEDULED",
"reachability-sync")`。类级 `@ConditionalOnProperty(talent-introduction.scheduling.enabled)` 门控既有；
cron 未配置默认 `-`（禁用），不新增配置文件键（yml 非授权文件）。

### T7 — 测试

`ExpertReachabilitySyncServiceTest`（10 用例）：
1. mapping 失败即抛 `IllegalStateException` 且 `syncReachabilityBatch` 零调用（I-3-6）；
2. scroll 分批聚合 total/success + progressStore.update 两次（I-3-4）；
3. 取消时 handler 返回 false 停止滚动，writer 仅 1 次调用；
4. classify 返回 null 时 updates 含 `Pair(orcid, null)`（map 非 mapNotNull，I-3-1 service 层）；
5. 硬退集合只含 HARD+可溯源 contact 的 orcid（captor 断言，I-1）；
6. `markBlockedByEmail` 查 orcid 后写 BLOCKED_UNSUBSCRIBED；
7. `markBlockedByEmail` 查询失败不抛、零写入（fail-open）；
8. `markBlockedByContact` 写 BLOCKED_BOUNCED；
9. writer 级：bulk body 含 `ctx._source.remove('reachability')` 与 `"reachability":"HIGH"`、无 `UNKNOWN`、无 `updatedAt`、
   仅 CANDIDATE+APPLICATION 索引（I-3-1 / I-3-2 / IP-5）；
10. writer 级：无 `_id` 映射的 orcid 计入 skipped。

`EmailSuppressionServiceTest`（+4 用例）：新增成功后调 `markBlockedByEmail`（归一化邮箱）；
sync 抛异常时 `suppress()` 仍返回 true 且记录已保存（I-3-5）；已存在/并发重复时不调用增量。

## 验证命令（全部以 JDK 11 实际执行）

| 命令 | 结果 | 证据 |
|------|------|------|
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ExpertReachabilitySyncServiceTest` | PASS | exit 0；Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=EmailSuppressionServiceTest` | PASS | exit 0；Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ExpertIndexControllerTest` | PASS | exit 0；Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 |
| `JAVA_HOME=...zulu-11... mvn test`（全量回归） | **FAIL（1）** | Tests run: 2483, Failures: 1, Errors: 0, Skipped: 4；唯一失败 `OperatorStatusWriteSeamGuardTest`，见冲突说明 |
| `git diff --check` | PASS | exit 0，无空白/换行告警 |

说明：2483 = 基线 2469（child 02 后）+ 本计划 14（10 + 4）。三条聚焦套件在全量回归中也各自为 0 失败。

## 验收标准逐项核对

- I-3-1：`grep -rn '"UNKNOWN"' src/main/kotlin/.../expert/` 零命中（注释亦已避免字面量）；单测断言 null → remove 脚本。
- I-3-2：`ExpertIndexLevel.RAW` 在 `syncReachabilityBatch` 函数体内零命中（awk 切片 grep 空）。
- I-3-3：`expertContactRepository` 仅出现在构造参数与硬退 contactId→orcidId 映射（`:86`）；`scrollExperts` 恰 1 处。
- I-3-4：新端点函数体内 `tryStartWithToken` 1 处、`clearExecutionContext` 2 处（finally if/else）。
- I-3-5：`EmailSuppressionService.kt:54` 与 `BounceCollectionService.kt:117` 的 `catch (e: Exception)` 覆盖新增调用；
  单测断言 sync 抛异常时 `suppress()` 仍返回 true 且记录存在。
- I-3-6：单测断言 mapping false → `IllegalStateException` 且 `syncReachabilityBatch` 零调用。
- IP-5：`syncReachabilityBatch` 函数体内 `updatedAt` 零命中。
- N-1：`git diff 5396782 -- ExpertIndexWriterService.kt` 无任何 `-` 行（纯增量：import + 新方法），
  `syncOperatorStatusBatch` / `resolveOrcidToDocIds` 函数体零改动行。
- 回归：全量测试 2483 中 2482 通过，**1 个失败**（见冲突说明）。

## 冲突说明（PLAN_CONFLICT 依据）

**现象**：全量回归唯一失败为 `OperatorStatusWriteSeamGuardTest.operator_status write sites exactly match whitelist`。

**根因**：该守卫测试（`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`，**不在本计划 8 个授权文件内**）
以「文件路径 + **绝对行号** + 上下文」三要素排除 `ExpertIndexController.kt` 内两处 DTO 回显的噪声命中
（`EXCLUDED_NOISE_SITES`：原 :90 `operatorStatus = contact?.operatorStatus ?: ...`、原 :431 `operatorStatus = operatorStatus ?: ...`）。
本计划 T4（授权文件 `ExpertIndexController.kt` 新增端点）必然向该文件插入 import、构造参数与端点函数体，
使上述两行分别位移至 **:94** 与 **:483** → 排除项失效 → 该行重新进入违规集合（守卫设计上「宁可误报、不放过」）。

**不可避性论证**：无论端点插入在类体何处，控制器类内任何新增行都会使其后所有行号位移；
`ExpertIndexResponse.from`（原 :431）位于类体之后的数据类中，故该钉死点**必然**位移；
构造函数/import 位于 :90 之前，故 :90 也**必然**位移。不存在不改动守卫测试即可让全量回归通过的 T4 实现方式。

**所需的最小修复（未经授权，未执行）**：按守卫自身协议与仓库先例（上一轮 fast-p 的
`bdf853c feat(fast-p): implement 03` 及守卫注释「A5 授权行号修正」），将
`OperatorStatusWriteSeamGuardTest.kt` 的 `EXCLUDED_NOISE_SITES` 中两条
`ExpertIndexController.kt` 噪声项行号更新为 94 / 483（上下文子串不变，语义零变化）。
该文件不在本计划授权清单内，execute-p 规则禁止编辑未授权文件；完成全量回归门禁因此需要**计划外授权**。

**请求裁决**：授权把 `OperatorStatusWriteSeamGuardTest.kt` 纳入本计划改动范围（仅 2 个噪声行号 90→94、431→483），
或提出替代方案。授权后本实现可立即完成收尾（回归全绿、单次提交 `feat(fast-p): implement 03`、剔除 fast-p 报告文件）。

## 其他说明

- 全程未产生任何提交（`git log` HEAD 仍为 c878763）；工作区仅含 8 个授权文件的改动 +
  `docs/plans/fast/expert-reachability/ledger.md`（控制器既有改动，与本实现无关，提交时剔除）。
- 计划身份与工作树身份在开始与结束时一致（SHA-256 `34f60195...`；branch `fast/expert-reachability`）。
- 未执行 `mvn clean package`（brief 必跑命令清单不含构建命令）。
## Epoch 2（恢复执行，amend A1 后） — 结果: READY_FOR_VERIFICATION

- 执行者: Reachability03ImplementerE2
- 执行日期: 2026-08-17
- 基线: 工作树 HEAD `dd94a8d`（docs-only）；产品代码 = child 02 code head `5396782`；8 个 epoch-1 文件改动原样保留。
- **A1 修复（第 9 个授权文件）**：`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
  `EXCLUDED_NOISE_SITES` 两条 `ExpertIndexController.kt` pin 行号 90→94、431→483（context 子串未动；
  已核对修改后控制器实际行号 :94 `operatorStatus = contact?.operatorStatus ...`、:483 `operatorStatus = operatorStatus ?: ...`）。
  仅行号同步，未触碰该 guard 任何断言语义。

### 保留工作复核结论

逐文件审阅 epoch-1 的 8 个文件 diff 与两个新文件全文，与计划 T1/T3/T4/T5/T6/T7 逐条一致：

- T1 `syncReachabilityBatch`：对照 `syncOperatorStatusBatch` 复制的四点差异齐备（层级 CANDIDATE+APPLICATION、
  字段 reachability、null → remove script、双分支不写 updatedAt）；`resolveOrcidToDocIds` 直接复用；
  skipped 统计（未命中 `_id`）与 `_bulk` ndjson 计数与源方法同构。
- T3 `ExpertReachabilitySyncService`：`syncAll()` 首行 `checkReachabilityMapping()` fail-fast（I-3-6）；
  判定集合装配与 `OperatorStatusReconcileService` 同款；`scrollExperts(CANDIDATE, 500)` 恰 1 处驱动（I-3-3）；
  `map` 非 `mapNotNull`（null 值仍下发 remove）；逐批 `progressStore.update` + `isCancelled` 支持取消。
- T4 端点：`tryStartWithToken` + 409 + `runAndRecordWithResult(onStarted=bindExecutionId)` + `catch IllegalStateException` → 400
  + `finally clearExecutionContext` 完整模式；构造参数尾部可空默认（既有 9 位置参数测试不受影响）。
- T5 增量：`EmailSuppressionService.suppress()` 新增成功后调 `markBlockedByEmail`（私有方法 try/catch 吞异常）；
  `BounceCollectionService` HARD 分支 `markBlockedByContact` try/catch 吞异常；两处均为 fail-open（I-3-5）。
- T6 定时：`MailAutomationScheduler` 新增 `reachability-sync` cron（默认 `-` 禁用），包 `runAndRecordWithResult`。
- T7 测试：`ExpertReachabilitySyncServiceTest` 10 用例、`EmailSuppressionServiceTest` +4 用例，
  断言覆盖 I-3-1/2/6、IP-5、硬退集合装配、增量 fail-open、null→remove、层级排除 RAW。

### 验证命令（全部 JDK 11，`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）

| 命令 | 结果 | 证据 |
|------|------|------|
| `mvn test -Dtest=ExpertReachabilitySyncServiceTest` | PASS | exit 0；Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test -Dtest=EmailSuppressionServiceTest` | PASS | exit 0；Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test -Dtest=ExpertIndexControllerTest` | PASS | exit 0；Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn test`（全量回归） | **PASS** | exit 0；`Tests run: 2483, Failures: 0, Errors: 0, Skipped: 4`；`BUILD SUCCESS` |
| `git diff --check` | PASS | exit 0，无空白/换行告警 |

补充：epoch-1 唯一失败 `OperatorStatusWriteSeamGuardTest` 本次通过（`Tests run: 1, Failures: 0, Errors: 0`，全量日志确认）。

### 验收标准核对（epoch 2 复跑）

- I-3-1：`grep -rn '"UNKNOWN"' src/main/kotlin/.../expert/` 零命中；单测断言 null → remove script。
- I-3-2：`syncReachabilityBatch` 函数体（writer :211-313）`ExpertIndexLevel.RAW` 零命中（文件内 RAW 命中均在其它方法）。
- I-3-3：sync 服务 `scrollExperts` 恰 1 处；`expertContactRepository` 仅构造参数 + 硬退映射两处。
- I-3-4：新端点函数体内 `tryStartWithToken|clearExecutionContext` 3 处命中。
- IP-5：`syncReachabilityBatch` 函数体内 `updatedAt` 零命中。
- N-1：writer diff 纯增量（+103/-0），`syncOperatorStatusBatch` / `resolveOrcidToDocIds` 零改动行。
- 回归：全量 2483/0/0/4 BUILD SUCCESS。

### 提交

- 单次本地提交 `feat(fast-p): implement 03`，内容恰为 9 个授权文件（8 个 epoch-1 文件 + guard 行号修正）；
  `docs/plans/fast/` 报告/账本未纳入（证据由控制器另行提交）。未 push / merge / rebase。
