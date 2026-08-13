# 04 Execution Report — P-C operator_status 状态对账作业

## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md
Plan SHA-256: 3da75190c24d5f037d997dba9889565688f2ccbedfed9e09b32f1f8845680a53
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md@3da75190c24d5f037d997dba9889565688f2ccbedfed9e09b32f1f8845680a53
Execution epoch: NEW
Approval basis: current invocation（child 04 brief + 完整计划全文从磁盘读取；Plan Identity Gate 执行前后一致）
Executor: Impl04
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency
Target branch: fast/batch-send-status-consistency
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency@fast/batch-send-status-consistency@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-status-consistency
Pre-execution code SHA: bdf853ceb2536772f9b1fcd4f0283877536e4376（child 03 代码头；执行前 HEAD=2044281 为 03 的 evidence commit，docs-only）
Post-execution code SHA: N/A（未提交——PLAN_CONFLICT 停止，实现保留在工作树）
Evidence HEAD: N/A（无提交）
Implementation boundary: 未提交；工作树含 7 个授权文件改动（见 Changed Files）

## Plan Identity Gate / Worktree Gate

- `scripts/plan_identity.py <plan>` → canonical path + sha256 `3da75190…`（执行前后一致 → 无 PLAN_CHANGED_DURING_EXECUTION）。
- `scripts/worktree_identity.py <plan> --worktree <target>` → root/branch/git-dir 均匹配；HEAD 仍为 2044281（本次零提交）。

## 结论（PLAN_CONFLICT 的核心事实）

T-1~T-4 + 知识条目已按计划完整实现（7 个授权文件，见 Task Status / Changed Files），
`OperatorStatusReconcileServiceTest` 12/0/0/0 通过、`git diff --check` 通过。
**但必跑命令 `mvn test` / `mvn clean package` 无法 exit 0**：计划 T-2 要求在授权文件
`ExpertIndexController.kt` 增加构造参数 + 端点，导致既有**未授权**守卫测试
`OperatorStatusWriteSeamGuardTest.kt`（child 02 P-D 产物）的两个**行号钉死**的
`EXCLUDED_NOISE_SITES` 排除项失配（DTO 噪声行 85→90、410→431 平移）。

按 execute-p：「Edit only files explicitly authorized by the plan」+「If completion requires an
unlisted file … stop with PLAN_CONFLICT」→ 判定 PLAN_CONFLICT；**未触碰该未授权测试文件，未提交**。

## 实测证据

| 步骤 | 命令 | 结果 |
|---|---|---|
| 聚焦测试 | `mvn test -Dtest=OperatorStatusReconcileServiceTest` | PASS：12/0/0/0 |
| 全量 | `mvn test` | **FAIL**：`Tests run: 2404, Failures: 1, Errors: 0, Skipped: 4`（唯一失败 = OperatorStatusWriteSeamGuardTest 1/1；基线 2392 + 新增 12） |
| 构建 | `mvn clean package` | **FAIL**：同上（2404/1/0/4，BUILD FAILURE） |

守卫测试失败明细（surefire，最终状态）：

```
期望白名单：[ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt]
实际命中文件：[ExpertIndexController.kt, ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt]
未登记白名单的写入点（违规）：
  com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:90: operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"
  com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:431: operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED",
```

### 冲突根因（逐条核实，非推测）

`OperatorStatusWriteSeamGuardTest.kt` 的 `EXCLUDED_NOISE_SITES` 以 **path+line+context 精确钉死**
child 02 计划明列的 DTO 噪声行（`ExpertIndexController.kt:85` / `:410`，见 02 计划「实现注意」）。
本计划 T-2 的**授权改动**在 `ExpertIndexController.kt` 增加了：① 1 行 import；② 构造参数块 4 行；
③ `POST /api/experts/reconcile-operator-status` 端点 16 行 → 上述两行分别平移到 90 / 431。
这两个命中是**既有 DTO 赋值噪声**（守卫自己声明过的非写入命中），不是新增的 `operator_status` 写入；
守卫测试自带自检「排除名单失效必须同步更新 EXCLUDED_NOISE_SITES（宁可误报、不放过）」必然失败。
**修复被该守卫测试唯一确定**（仅改 2 个行号，context 不变、白名单闭包断言不弱化），
但该文件**不在 04 计划授权清单内**，且 brief 明令「Modify ONLY the 7 authorized files」、
验收「implementation commit contains exactly the 7 authorized files」——
按 execute-p 不得自行扩权，必须 human 决策（与 child 03 Impl03b → A3 amendment 同型）。

> 附带说明：`OperatorStatusReconcileService.kt` 的日志串原含 `operator_status` 子串会被守卫正则误扫，
> 已按授权文件内修复为 `operatorStatus reconcile done`（该命中已消除）；剩余失败**仅**为上述行号平移。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 对账服务（I-1 只读 / I-2 人工覆盖 / I-3 COMPLETED / I-4 映射 + ReconcileReport + ES 三层 500/批 terms） | IMPLEMENTED（工作树，未提交） | OperatorStatusReconcileService.kt（新增） | 服务不注入任何 writer；仅 `_search` 只读；映射逐条对应既有实现（hasSentIntroduction:895 逐字 / AutoMailReplyService:484,802 / AutomaticApplicationPromotionService:50,57 / BounceCollectionService:105 / ManualInitialOutreachService:706） |
| T-2 定时 + 手动入口 | IMPLEMENTED（工作树，未提交） | MailAutomationScheduler.kt、MailSchedulingProperties.kt、ExpertIndexController.kt | `operatorStatusReconcileCron: String = "-"`（默认关闭）；`@Scheduled` taskType=OPERATOR_STATUS_RECONCILE 走 runAndRecordWithResult；`POST /api/experts/reconcile-operator-status` 照抄 :197-216 模式 |
| T-3 CHANGE_OPERATOR_STATUS 查询 | IMPLEMENTED（工作树，未提交） | OperatorActionLogRepository.kt（grep 确认该 repository 存在，按计划 T-3 归属判定换入） | `findContactIdsWithChangeOperatorStatusLogs(contactIds)`：SELECT DISTINCT expert_contact_id WHERE action_type='CHANGE_OPERATOR_STATUS' AND expert_contact_id IN (:contactIds) |
| T-4 测试 | IMPLEMENTED（工作树，未提交） | OperatorStatusReconcileServiceTest.kt（新增） | 12 例：映射逐条（CONTACTED/INVITED/REPLIED/MATERIALS_RECEIVED/EMAIL_INVALID×2/最高里程碑/TRANSIENT 阴性）、人工覆盖单列、COMPLETED 不判异常、ES-vs-DB、零写入（全部写方法 verify(never) + verifyNoMoreInteractions 总闭包 + ES 全为 _search） |
| 知识条目 | IMPLEMENTED（工作树，未提交） | docs/knowledge/campaign/K-operator-status-reconcile.md（新增） | 映射表/分类规则/ES 读取约定/关键坑 |
| 回归（必跑命令 exit 0） | **CONFLICT**（被未授权守卫测试行号钉死阻塞） | OperatorStatusWriteSeamGuardTest.kt（未授权，未修改） | mvn test / clean package 均 2404/1/0/4，唯一失败为守卫测试 |

## Commands（最终状态 fresh 运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL | exit 1；`Tests run: 2404, Failures: 1, Errors: 0, Skipped: 4`（唯一失败：OperatorStatusWriteSeamGuardTest） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusReconcileServiceTest` | PASS | exit 0；`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`（最终状态 fresh：`-Dtest=OperatorStatusReconcileServiceTest,OperatorStatusWriteSeamGuardTest` 组合运行中 12/0/0/0） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | FAIL | exit 1；`Tests run: 2404, Failures: 1, Errors: 0, Skipped: 4`（唯一失败：OperatorStatusWriteSeamGuardTest） |
| `git diff --check` | PASS | exit 0（无空白错误） |

> FlywayMigrationIntegrationTest 未运行（遵守指令）。

## Changed Files（工作树，未提交；全部为授权文件）

- `src/main/kotlin/…/campaign/service/OperatorStatusReconcileService.kt`（新增）— T-1 对账服务 + ReconcileReport/ReconcileSample
- `src/main/kotlin/…/config/MailSchedulingProperties.kt` — T-2 `operatorStatusReconcileCron: String = "-"`
- `src/main/kotlin/…/task/service/MailAutomationScheduler.kt` — T-2 `scheduleOperatorStatusReconcile()` @Scheduled
- `src/main/kotlin/…/expert/controller/ExpertIndexController.kt` — T-2 构造参数（尾部可空默认参数，见 Deviations ①）+ `POST /reconcile-operator-status`
- `src/main/kotlin/…/audit/repository/OperatorActionLogRepository.kt` — T-3 `findContactIdsWithChangeOperatorStatusLogs`（按计划 T-3「取决于既有归属」grep 确认存在后落位；授权文件 #5 换入，仍为 7 文件）
- `src/test/kotlin/…/campaign/service/OperatorStatusReconcileServiceTest.kt`（新增）— T-4
- `docs/knowledge/campaign/K-operator-status-reconcile.md`（新增）— 知识条目

## Deviations

- ① **ExpertIndexController 构造参数为尾部可空默认参数**（`OperatorStatusReconcileService? = null`）：
  既有未授权测试 `ExpertIndexControllerTest.kt:47` 以 9 个位置参数直接构造控制器，
  新增必需参数会破坏其编译（同 child 03 A3 冲突类，但 04 无 amendment）。采用仓库既有先例
  `ManualExpertMailService` 尾部默认参数（`PersonalizationGateService = PersonalizationGateService()`、
  `MailVariableService? = null`，均为 @Service 由 Spring 注入、默认值仅测试兜底）：
  生产 Spring 按类型注入真实 bean，端点内 `requireNotNull` 兜底；测试文件零改动。
  端点行为与计划「照抄 :197-216」完全一致（行为无偏差，仅注入形态适配未授权测试）。
- ② 日志串 `operator_status` → `operatorStatus`：消除守卫正则对服务文件的误扫（授权文件内修复，无行为影响）。
- ③ T-3 落位 OperatorActionLogRepository（计划 T-3 明示二选一「取决于既有归属；执行前 grep 确认」）：
  该 repository 存在（`audit/repository/OperatorActionLogRepository.kt`），故授权文件 #5 由
  ExpertContactRepository 换为 OperatorActionLogRepository，文件数仍为 7。
- ④ `ReconcileReport` 的 task 语义（TaskExecutionSummaryProvider）：successCount=一致、failureCount=异常总数、
  finalStatus=null 走既有推导（无异常=SUCCESS，有异常=PARTIAL_SUCCESS/FAILED，与 BulkSyncResult 先例一致，
  对账发现漂移即告警，明细在 result_summary）。计划未规定 status 语义，此为可复现的实现选择。

## Freshness

- Plan identity rechecked: YES（pre 3da75190… = post 3da75190…）
- Worktree identity rechecked: YES（root/branch/git-dir 一致，提交前后无变化）
- Reported commits reachable from target branch: N/A（无提交）
- Required commands run this invocation: 部分（mvn test / 聚焦 / clean package / diff --check 均已最终状态 fresh 运行；
  mvn test 与 clean package 因守卫测试失败 exit 1——READY_FOR_VERIFICATION 不成立，冲突证据见上）
- Historical evidence used only as baseline: YES（2392/0/0/4 基线仅对照）

## Remaining Blocker

需要 human 对以下最小冲突拍板（与 child 03 → A3 同型）。T-1~T-4 实现完整且正确，
唯一阻塞是**未授权文件** `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
的两条行号钉死排除项随授权改动平移而失效：

1. **授权更新该守卫测试的 2 条 EXCLUDED_NOISE_SITES 行号**（`ExpertIndexController.kt:85`→`:90`、
   `:410`→`:431`，context 不变、白名单闭包断言不弱化、守卫测试自身整改指引即要求同步排除名单）；
   之后实现提交将含 8 文件（7 授权 + 守卫测试）；
2. 或将 T-2 改为「不触碰 ExpertIndexController 构造签名」的其他注入形态（如 @Autowired 字段注入），
   但 import 仍会平移行号，守卫排除项依然失配——该路径不可行；
3. 或对守卫测试豁免/另行安排。

任何选项都需要 04 计划 amendment 或 human 明示。实现（7 授权文件）已保留在工作树，amend 后可直接续跑。

## Next Action

- PLAN_CONFLICT → 取得 human 决策 / 04 计划 amendment 后 RESUME（同 EXECUTION_ID）：
  更新守卫测试排除行号 → 重跑 4 条必跑命令 → 提交（subject `feat(fast-p): implement 04`，含授权文件+amend 文件）。

---

# Epoch 2 Execution Report（2026-08-13，A4 修正后重跑，Executor=Impl04b）

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md
Plan SHA-256: d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md@d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b
Execution epoch: RESUME（epoch 2，对 A4 修正后的同一计划路径新哈希执行；prior epoch-1 报告哈希 3da75190… 属旧计划身份，仅作历史基线）
Approval basis: child 04 brief（含 Amendments A4）+ 完整计划全文从磁盘读取；Plan Identity Gate 执行前后一致
Executor: Impl04b
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency
Target branch: fast/batch-send-status-consistency
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency@fast/batch-send-status-consistency@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-status-consistency
Pre-execution code SHA: bdf853ceb2536772f9b1fcd4f0283877536e4376（child 03 代码头；epoch 2 起点 HEAD=d12f9fc 为 A4 修正 commit，docs-only）
Post-execution code SHA: 9df711a（实现提交，见 Evidence HEAD）
Evidence HEAD: 9df711a feat(fast-p): implement 04（实现提交本身；无单独 evidence commit）
Implementation boundary: d12f9fc..9df711a（8 个授权文件，含 A4 守卫测试更新）

## Plan Identity Gate / Worktree Gate

- `plan_identity.py` → canonical path + sha256 `d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b`（执行前后一致）。
- `worktree_identity.py` 执行前 + `--expect-root/--expect-branch/--expect-git-dir` 重跑均通过：root/branch/git-dir 匹配，HEAD=d12f9fc（stage/commit 前复核一致）。

## 对 epoch-1 实现的复核（Phase 1：全部按 A4 计划重新验证，非盲信）

- T-1 `OperatorStatusReconcileService.kt`：全表扫描（findAll ×4）+ 内存比对；I-4 映射逐字比对
  `ManualInitialOutreachService.hasSentIntroduction():893-896`（OUTBOUND+INTRODUCTION+SENT）；INVITED/REPLIED/MATERIALS_RECEIVED/EMAIL_INVALID
  （HARD bounce `BounceCollectionService:105` + 首封外发 `PERMANENT:` 失败 `ManualInitialOutreachService:706`）；COMPLETED 豁免（I-3）；
  人工覆盖经 `OperatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs`（I-2）；ES 三层（RAW/CANDIDATE/APPLICATION）
  各 500 条分批 terms `_search`（与 `ExpertIndexWriterService.syncOperatorStatusBatch` chunked(500) 同款，字段 `orcidId` 归一化一致），
  值优先级 CANDIDATE>APPLICATION>RAW；`ReconcileReport` 实现 `TaskExecutionSummaryProvider`（successCount=一致、failureCount=异常，finalStatus 走既有推导）。
  **零写入**：仅 repository findAll + ES `_search`；不注入任何 writer（构造参数 7 个全为只读依赖）。
- T-2：`MailSchedulingProperties.operatorStatusReconcileCron="-"`
  （前缀 `talent-introduction.scheduling`，`-` 默认关闭）；`MailAutomationScheduler.scheduleOperatorStatusReconcile()`
  `@Scheduled(cron="${talent-introduction.scheduling.operator-status-reconcile-cron:-}")` taskType=`OPERATOR_STATUS_RECONCILE` 走 `runAndRecordWithResult`；
  `ExpertIndexController.reconcileOperatorStatus()` `POST /api/experts/reconcile-operator-status` 照抄 `:197-216`（backfill 同款 try/catch + runAndRecordWithResult）。
- T-3：`OperatorActionLogRepository`（既有，grep 确认）新增 `@Query` `SELECT DISTINCT expert_contact_id … WHERE action_type='CHANGE_OPERATOR_STATUS' AND expert_contact_id IN (:contactIds)`；列名对 V19:36,38 核实。
- T-4：`OperatorStatusReconcileServiceTest` 12 用例：I-4 逐条映射（CONTACTED/INVITED/REPLIED/MATERIALS_RECEIVED 含 OUTBOUND 附件反例/EMAIL_INVALID×2/TRANSIENT 反例/最高里程碑）+ I-2 人工覆盖单列 + I-3 COMPLETED + ES-DB 不符 + I-1 零写入
  （全部 CrudRepository 写方法 verify(never) + verifyNoMoreInteractions 总闭包 + ES URL 全部 `/_search` 断言）。
- 知识文档 `K-operator-status-reconcile.md`：映射表/分类规则/ES 读取约定/关键坑齐全。
- A4：`OperatorStatusWriteSeamGuardTest.EXCLUDED_NOISE_SITES` 行号 85→90、410→431（上下文未动；白名单闭包与断言未动）；
  对照当前 `ExpertIndexController.kt` 实际行 90/431 逐行验证，guard 排除自检 `lines[line-1].contains(context)` 精确命中。

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 对账服务（I-1/I-2/I-3/I-4，ReconcileReport，零写入） | IMPLEMENTED | OperatorStatusReconcileService.kt | 代码复核 + 单测 12/12 + verifyNoInteractions/`/_search`-only 断言 |
| T-2 属性+定时+手动端点 | IMPLEMENTED | MailSchedulingProperties.kt、MailAutomationScheduler.kt、ExpertIndexController.kt | diff 复核 + 全量测试绿 |
| T-3 查询方法 | IMPLEMENTED | OperatorActionLogRepository.kt | grep 归属确认 + @Query 实现 + V19 列名核实 |
| T-4 测试（12 例含零写入） | IMPLEMENTED | OperatorStatusReconcileServiceTest.kt | 12/0/0/0（fresh） |
| 知识文档 | IMPLEMENTED | K-operator-status-reconcile.md | 内容复核 |
| A4 守卫测试行号更新 | IMPLEMENTED | OperatorStatusWriteSeamGuardTest.kt | guard 1/1（fresh），行 90/431 命中 |

### Commands（全部 fresh，JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home，均在本 worktree 根执行）

| Command | Result | Evidence |
|---|---|---|
| `mvn test` | PASS（exit 0） | surefire 2404 tests / 0 failures / 0 errors / 4 skipped；JS 496 pass / 0 fail；guard 1/1、reconcile 12/12 |
| `mvn test -Dtest=OperatorStatusReconcileServiceTest` | PASS（exit 0） | Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn clean package` | PASS（exit 0） | surefire 2404 / 0 / 0 / 4 skipped；JS 496 pass / 0 fail；BUILD SUCCESS；guard 1/1 |
| `git diff --check` | PASS（exit 0） | 无空白错误输出 |

> 未运行 FlywayMigrationIntegrationTest（计划明令禁止）。

### Changed Files（commit 9df711a，8 个授权文件，`git show --name-only` 核对）

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt` — 新增：对账服务（只读，I-1）
- `src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt` — 新增 @Scheduled 对账任务
- `src/main/kotlin/com/weibo/talentintroduction/config/MailSchedulingProperties.kt` — 新增 `operatorStatusReconcileCron="-"`
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` — 新增 `POST /api/experts/reconcile-operator-status`
- `src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt` — 新增 I-2 判别器查询（T-3）
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt` — 新增：12 用例（T-4）
- `docs/knowledge/campaign/K-operator-status-reconcile.md` — 新增：知识条目
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — A4：排除项行号 85→90、410→431

### Deviations

- `ExpertIndexController` 新构造参数 `operatorStatusReconcileService` 声明为尾部可空默认参数（`? = null`，端点内 `requireNotNull` 兜底），
  而非必填参数：既有**未授权**测试 `ExpertIndexControllerTest` 以 9 个位置参数直接构造控制器（:47-54），
  必填参数会强制改动该文件。生产路径由 Spring 注入 `@Service` bean，行为等价（照 `ManualExpertMailService.mailVariableService` 既有可空注入先例）。
  这是唯一偏差，其余严格照计划。

### Freshness

- Plan identity rechecked: YES（d41ec156… 前后一致）
- Worktree identity rechecked: YES（--expect-root/--expect-branch/--expect-git-dir 通过；commit 后 HEAD=9df711a 为本 worktree 分支头）
- Reported commits reachable from target branch: YES（9df711a = 当前 HEAD，`fast/batch-send-status-consistency` 上）
- Required commands run this invocation: YES（4/4，final state 后 fresh）
- Historical evidence used only as baseline: YES（epoch-1 报告仅作旧计划身份基线；实现逐条重新验证）

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`

---

# Epoch 2 Execution Report（2026-08-13，A4 修正后重跑，Executor=Impl04b）

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md
Plan SHA-256: d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency/docs/plans/2026-08-13/04-operator-status-reconciler.md@d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b
Execution epoch: RESUME（epoch 2，对 A4 修正后的同一计划路径新哈希执行；prior epoch-1 报告哈希 3da75190… 属旧计划身份，仅作历史基线）
Approval basis: child 04 brief（含 Amendments A4）+ 完整计划全文从磁盘读取；Plan Identity Gate 执行前后一致
Executor: Impl04b
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency
Target branch: fast/batch-send-status-consistency
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-status-consistency@fast/batch-send-status-consistency@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-status-consistency
Pre-execution code SHA: bdf853ceb2536772f9b1fcd4f0283877536e4376（child 03 代码头；epoch 2 起点 HEAD=d12f9fc 为 A4 修正 commit，docs-only）
Post-execution code SHA: 9df711a（实现提交，见 Evidence HEAD）
Evidence HEAD: 9df711a feat(fast-p): implement 04（实现提交本身；无单独 evidence commit）
Implementation boundary: d12f9fc..9df711a（8 个授权文件，含 A4 守卫测试更新）

## Plan Identity Gate / Worktree Gate

- `plan_identity.py` → canonical path + sha256 `d41ec1563a5464354934927313b898f0377120aedbe33ce936a9e7d7e3291b7b`（执行前后一致）。
- `worktree_identity.py` 执行前 + `--expect-root/--expect-branch/--expect-git-dir` 重跑均通过：root/branch/git-dir 匹配，HEAD=d12f9fc（stage/commit 前复核一致）。

## 对 epoch-1 实现的复核（Phase 1：全部按 A4 计划重新验证，非盲信）

- T-1 `OperatorStatusReconcileService.kt`：全表扫描（findAll ×4）+ 内存比对；I-4 映射逐字比对
  `ManualInitialOutreachService.hasSentIntroduction():893-896`（OUTBOUND+INTRODUCTION+SENT）；INVITED/REPLIED/MATERIALS_RECEIVED/EMAIL_INVALID
  （HARD bounce `BounceCollectionService:105` + 首封外发 `PERMANENT:` 失败 `ManualInitialOutreachService:706`）；COMPLETED 豁免（I-3）；
  人工覆盖经 `OperatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs`（I-2）；ES 三层（RAW/CANDIDATE/APPLICATION）
  各 500 条分批 terms `_search`（与 `ExpertIndexWriterService.syncOperatorStatusBatch` chunked(500) 同款，字段 `orcidId` 归一化一致），
  值优先级 CANDIDATE>APPLICATION>RAW；`ReconcileReport` 实现 `TaskExecutionSummaryProvider`（successCount=一致、failureCount=异常，finalStatus 走既有推导）。
  **零写入**：仅 repository findAll + ES `_search`；不注入任何 writer（构造参数 7 个全为只读依赖）。
- T-2：`MailSchedulingProperties.operatorStatusReconcileCron="-"`
  （前缀 `talent-introduction.scheduling`，`-` 默认关闭）；`MailAutomationScheduler.scheduleOperatorStatusReconcile()`
  `@Scheduled(cron="${talent-introduction.scheduling.operator-status-reconcile-cron:-}")` taskType=`OPERATOR_STATUS_RECONCILE` 走 `runAndRecordWithResult`；
  `ExpertIndexController.reconcileOperatorStatus()` `POST /api/experts/reconcile-operator-status` 照抄 `:197-216`（backfill 同款 try/catch + runAndRecordWithResult）。
- T-3：`OperatorActionLogRepository`（既有，grep 确认）新增 `@Query` `SELECT DISTINCT expert_contact_id … WHERE action_type='CHANGE_OPERATOR_STATUS' AND expert_contact_id IN (:contactIds)`；列名对 V19:36,38 核实。
- T-4：`OperatorStatusReconcileServiceTest` 12 用例：I-4 逐条映射（CONTACTED/INVITED/REPLIED/MATERIALS_RECEIVED 含 OUTBOUND 附件反例/EMAIL_INVALID×2/TRANSIENT 反例/最高里程碑）+ I-2 人工覆盖单列 + I-3 COMPLETED + ES-DB 不符 + I-1 零写入
  （全部 CrudRepository 写方法 verify(never) + verifyNoMoreInteractions 总闭包 + ES URL 全部 `/_search` 断言）。
- 知识文档 `K-operator-status-reconcile.md`：映射表/分类规则/ES 读取约定/关键坑齐全。
- A4：`OperatorStatusWriteSeamGuardTest.EXCLUDED_NOISE_SITES` 行号 85→90、410→431（上下文未动；白名单闭包与断言未动）；
  对照当前 `ExpertIndexController.kt` 实际行 90/431 逐行验证，guard 排除自检 `lines[line-1].contains(context)` 精确命中。

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 对账服务（I-1/I-2/I-3/I-4，ReconcileReport，零写入） | IMPLEMENTED | OperatorStatusReconcileService.kt | 代码复核 + 单测 12/12 + verifyNoInteractions/`/_search`-only 断言 |
| T-2 属性+定时+手动端点 | IMPLEMENTED | MailSchedulingProperties.kt、MailAutomationScheduler.kt、ExpertIndexController.kt | diff 复核 + 全量测试绿 |
| T-3 查询方法 | IMPLEMENTED | OperatorActionLogRepository.kt | grep 归属确认 + @Query 实现 + V19 列名核实 |
| T-4 测试（12 例含零写入） | IMPLEMENTED | OperatorStatusReconcileServiceTest.kt | 12/0/0/0（fresh） |
| 知识文档 | IMPLEMENTED | K-operator-status-reconcile.md | 内容复核 |
| A4 守卫测试行号更新 | IMPLEMENTED | OperatorStatusWriteSeamGuardTest.kt | guard 1/1（fresh），行 90/431 命中 |

### Commands（全部 fresh，JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home，均在本 worktree 根执行）

| Command | Result | Evidence |
|---|---|---|
| `mvn test` | PASS（exit 0） | surefire 2404 tests / 0 failures / 0 errors / 4 skipped；JS 496 pass / 0 fail；guard 1/1、reconcile 12/12 |
| `mvn test -Dtest=OperatorStatusReconcileServiceTest` | PASS（exit 0） | Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 |
| `mvn clean package` | PASS（exit 0） | surefire 2404 / 0 / 0 / 4 skipped；JS 496 pass / 0 fail；BUILD SUCCESS；guard 1/1 |
| `git diff --check` | PASS（exit 0） | 无空白错误输出 |

> 未运行 FlywayMigrationIntegrationTest（计划明令禁止）。

### Changed Files（commit 9df711a，8 个授权文件，`git show --name-only` 核对）

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt` — 新增：对账服务（只读，I-1）
- `src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt` — 新增 @Scheduled 对账任务
- `src/main/kotlin/com/weibo/talentintroduction/config/MailSchedulingProperties.kt` — 新增 `operatorStatusReconcileCron="-"`
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` — 新增 `POST /api/experts/reconcile-operator-status`
- `src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt` — 新增 I-2 判别器查询（T-3）
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt` — 新增：12 用例（T-4）
- `docs/knowledge/campaign/K-operator-status-reconcile.md` — 新增：知识条目
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — A4：排除项行号 85→90、410→431

### Deviations

- `ExpertIndexController` 新构造参数 `operatorStatusReconcileService` 声明为尾部可空默认参数（`? = null`，端点内 `requireNotNull` 兜底），
  而非必填参数：既有**未授权**测试 `ExpertIndexControllerTest` 以 9 个位置参数直接构造控制器（:47-54），
  必填参数会强制改动该文件。生产路径由 Spring 注入 `@Service` bean，行为等价（照 `ManualExpertMailService.mailVariableService` 既有可空注入先例）。
  这是唯一偏差，其余严格照计划。

### Freshness

- Plan identity rechecked: YES（d41ec156… 前后一致）
- Worktree identity rechecked: YES（--expect-root/--expect-branch/--expect-git-dir 通过；commit 后 HEAD=9df711a 为本 worktree 分支头）
- Reported commits reachable from target branch: YES（9df711a = 当前 HEAD，`fast/batch-send-status-consistency` 上）
- Required commands run this invocation: YES（4/4，final state 后 fresh）
- Historical evidence used only as baseline: YES（epoch-1 报告仅作旧计划身份基线；实现逐条重新验证）

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
