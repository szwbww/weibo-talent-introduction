# P-C：operator_status 状态对账作业

优先级 **P1（根源 P2）** ｜ 前置：P-A ｜ 子系统：1（后端） ｜ 文件数：7

## 需求描述

**Observable outcome**

1. 存在一个可定时、可手动触发的对账作业，从事件（`mail_record` / `operator_action_log`）
   反推每位联系人的**期望** `operator_status`，与 DB 实际值、ES 实际值三方比对。
2. 对账产出差异报告（数量 + 明细样本 + 分类原因），记入 `task_execution`，可在任务面板查看。
3. **首版只报告、不自动修**。

**What must NOT change**

- 任何 `operator_status` 的现有写入行为——本计划只读不写。
- 「回刷 ES」按钮的行为。

**Out of scope**

- 自动修复（首版刻意不做，理由见下）。
- 其他字段的对账。

## 为什么这是"根源"的一部分

P-A 消除了漂移的**成因**（双写入口），P-D 阻止成因**复发**。但两者都不能发现
**已经发生**的漂移，也不能覆盖未来那些不经代码路径的漂移（人工改库、迁移脚本、数据导入）。

当前状态：本次 bug 是运营用肉眼发现的，系统没有任何机制会报警。
`CandidateOperatorStatusSyncService`（「回刷 ES」）是 **DB → ES 单向推送**，
它无法**发现**不一致，只会把 DB 的值（无论对错）盖过去。

**为什么首版不自动修**：自动修会与人工覆盖打架。运营手工把某人设为 `COMPLETED`
是合法且不可派生的（见 I-2），自动修必须能识别并让路；在报告阶段先验证识别逻辑的准确率，
再考虑放开写权限，才是安全的推进顺序。

## 关键不变量

### I-1：对账只读
- **Rule**：本计划新增的任何代码不得写入 `expert_contact` 或任何 ES 索引。
- **Violation consequence**：一个尚未验证准确率的判定逻辑一旦获得写权限，
  可能批量污染 2062 行数据。
- **来源**：original

### I-2：人工覆盖不参与差异判定
- **Rule**：若某联系人存在 `action_type='CHANGE_OPERATOR_STATUS'` 的
  `operator_action_log` 记录，其状态视为**人工权威**，只在报告中单列，不计入"异常"。
- **判据证据**（无需新增列）：

```
operator_action_log 表（V19:32-52）含 action_type / before_value / after_value / operator_name
grep -rn "CHANGE_OPERATOR_STATUS" src/main/kotlin
  → OperatorActionType.kt:4
  → ExpertOperatorStatusService.kt:34   （仅 changeStatus 内，:31-41）
```

`changeStatus`（人工）写审计日志；`updateAutomatically`（自动，`:60-63`）**不写**。
故"有该日志"= 被人工覆盖过，这是现成的判别器。

- **来源**：original

### I-3：COMPLETED 不可派生
- **Rule**：`COMPLETED` 无任何事件来源，一律视为人工终态，不参与期望值计算。
- **证据**：`grep -rn "OperatorStatus.COMPLETED" src/main/kotlin` → 仅
  `ExpertOperatorStatusService:53` 一处，且是读取判断，非写入。
- **Violation consequence**：把 COMPLETED 判为"异常"会让报告充满噪声，掩盖真问题。
- **来源**：original

### I-4：期望值映射有据可依
- **Rule**：每条"事件 → 期望状态"的映射必须对应仓库中已有的自动推进实现，不得自创。
- **映射表与出处**：

| 期望状态 | 事件判据 | 出处 |
|---|---|---|
| `CONTACTED` | 存在 `direction=OUTBOUND AND mail_type=INTRODUCTION AND send_status=SENT` 的 mail_record | `ManualInitialOutreachService.hasSentIntroduction():895` 逐字 |
| `INVITED` | 存在 `mail_type=MEETING_INVITATION` 的 OUTBOUND SENT | `AutoMailReplyService:484` |
| `REPLIED` | 存在 INBOUND mail_record | `AutoMailReplyService:802` |
| `MATERIALS_RECEIVED` | 有材料附件 | `AutomaticApplicationPromotionService:50,57` |
| `EMAIL_INVALID` | 退信记录 | `BounceCollectionService:105`、`ManualInitialOutreachService:706` |
| `COMPLETED` | —— 不可派生（I-3） | —— |

- **来源**：original

## 现状审计

### 可复用的既有设施

**任务执行框架**：`TaskExecutionService.runAndRecordWithResult`（`:87-93`）签名：

```kotlin
fun <T : Any?> runAndRecordWithResult(
    taskType: String, triggerType: String, request: Any,
    onStarted: ((executionId: Long) -> Unit)? = null,
    batchConfigId: Long? = null,
    block: () -> T
): Pair<TaskExecution, T>
```

**定时挂载点**：`MailAutomationScheduler` 的既有模式（`:66-72`）：

```kotlin
@Scheduled(cron = "\${talent-introduction.scheduling.operator-status-sync-cron:-}")
fun scheduleOperatorStatusSync() {
    taskExecutionService.runAndRecord("CANDIDATE_OPERATOR_STATUS_SYNC", "SCHEDULED", "operator-status-sync") { … }
}
```

**配置槽先例**：`MailSchedulingProperties`（全 11 字段）已有
`operatorStatusSyncCron: String = "-"`，`"-"` 表示关闭。新增 cron 属性照此模式。

**手动触发先例**：`ExpertIndexController:197-216` 的 `POST /backfill-operator-status`
用 `runAndRecordWithResult` 包裹并返回结构化结果，可照抄。

### 数据规模（影响实现方式）

`expert_contact` 2062 行、`mail_record` 2157 行。规模很小，**可以全表扫描 + 内存比对**，
不需要分页或流式处理。ES 侧用 `syncCandidateOperatorStatusBatch` 同款的 500 条分批
`terms` 查询取回实际值。

### Interaction points

| # | 读 | 用途 | 验收 |
|---|---|---|---|
| IP-1 | `mail_record` | 期望值反推 | A-1 |
| IP-2 | `operator_action_log` | 人工覆盖识别 | A-3 |
| IP-3 | ES CANDIDATE（P-B 后为三层） | ES 侧实际值 | A-2 |

## 实现方案

### T-1 对账服务【I-1, I-2, I-3, I-4】
新增：`campaign/service/OperatorStatusReconcileService.kt`

1. 全量读 `expert_contact`（2062 行，一次性）。
2. 全量读 `mail_record` 按 `expert_contact_id` 分组，按 I-4 映射表算期望状态。
3. 读 `operator_action_log` 中 `action_type='CHANGE_OPERATOR_STATUS'` 的 contact id 集合。
4. 分批查 ES 实际值。
5. 产出 `ReconcileReport`：总数 / 一致 / DB 与期望不符 / ES 与 DB 不符 /
   人工覆盖（单列）/ 各类差异的前 20 条样本（contactId + orcid + 三方取值）。
6. **全程不调用任何 save / update / index 写方法**（I-1）。

### T-2 定时 + 手动入口
文件：`task/service/MailAutomationScheduler.kt`、`config/MailSchedulingProperties.kt`

新增属性 `operatorStatusReconcileCron: String = "-"`（默认关闭，与既有 10 个 cron 属性一致），
新增 `@Scheduled` 方法，taskType 用 `OPERATOR_STATUS_RECONCILE`，
走 `runAndRecordWithResult` 使报告进入 `task_execution.result_summary`。

文件：`expert/controller/ExpertIndexController.kt` —— 新增
`POST /api/experts/reconcile-operator-status`，照抄 `:197-216` 的写法。

### T-3 查询方法
文件：`campaign/repository/ExpertContactRepository.kt`

新增按 contact id 批量取"是否存在 CHANGE_OPERATOR_STATUS 日志"的查询
（或在 `OperatorActionLogRepository` 侧新增，取决于既有归属；执行前 grep 确认该 repository 是否存在）。

### T-4 测试
新增：`test/…/campaign/service/OperatorStatusReconcileServiceTest.kt`

覆盖：期望值映射逐条正确 / 人工覆盖被单列 / COMPLETED 不判异常 /
**断言全程零写入**（`verifyNoInteractions` 于所有写方法）。

## 变更文件清单（7 个）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `campaign/service/OperatorStatusReconcileService.kt` | 新增 |
| 2 | `task/service/MailAutomationScheduler.kt` | 改 |
| 3 | `config/MailSchedulingProperties.kt` | 改 |
| 4 | `expert/controller/ExpertIndexController.kt` | 改 |
| 5 | `campaign/repository/ExpertContactRepository.kt` | 改 |
| 6 | `test/…/campaign/service/OperatorStatusReconcileServiceTest.kt` | 新增 |
| 7 | `docs/knowledge/campaign/K-operator-status-reconcile.md` | 新增 |

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusReconcileServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

## 验收标准

- **I-1**：`OperatorStatusReconcileServiceTest` 断言全部写方法 `verifyNoInteractions`；
  人工 review 确认服务类未注入任何 writer。
- **I-2**：造一条有 `CHANGE_OPERATOR_STATUS` 日志且状态与期望不符的数据，
  断言其归入"人工覆盖"而非"异常"。
- **I-3**：造一条 `COMPLETED` 数据，断言不出现在异常列表。
- **I-4**：逐条映射的单测断言。
- **回归**：执行『验证命令』节全部通过。

## 人工验收清单

### A-1：对账能发现 P-A 修复前的那类漂移【outcome 1 / IP-1】
- 前置：手工造一条 `operator_status='NOT_CONTACTED'` 但有 SENT INTRODUCTION 的联系人
  （即 P-A 修复前 id=2089 的形态）。
- 步骤：调 `POST /api/experts/reconcile-operator-status`。
- 预期：报告"DB 与期望不符"计数 ≥1，明细样本中出现该 contactId，
  期望值为 `CONTACTED`、DB 值为 `NOT_CONTACTED`。

### A-2：对账能发现 DB 与 ES 不一致【outcome 1 / IP-3】
- 前置：手工在 ES 里把某专家的 `operatorStatus` 改成与 DB 不同的值。
- 步骤：跑对账。
- 预期："ES 与 DB 不符"计数 ≥1，明细含该 orcid 与两侧取值。

### A-3：人工覆盖不被误报【outcome 1 / I-2 / IP-2】
- 前置：在专家详情页手工把某专家状态改成与事件期望不符的值（如无任何邮件却设为「已回复」）。
- 步骤：跑对账。
- 预期：该专家出现在**「人工覆盖」**分类，**不**出现在"异常"分类。

### A-4：报告可在任务面板查看【outcome 2】
- 步骤：跑完对账后打开任务执行面板，找到 `OPERATOR_STATUS_RECONCILE` 记录。
- 预期：能看到总数 / 一致 / 各类差异的数字与样本，无需查日志。

### A-5：确认只读【outcome 3 / I-1】
- 步骤：① 记录跑对账前 `expert_contact` 的
  `SELECT operator_status, COUNT(*) FROM expert_contact GROUP BY operator_status`；
  ② 跑一次对账；③ 再查一次。
- 预期：两次结果**完全一致**，一行都没被改动。

### A-6：默认关闭【must-NOT-change】
- 步骤：不配置 `operator-status-reconcile-cron`，正常启动运行一天。
- 预期：无 `OPERATOR_STATUS_RECONCILE` 的 SCHEDULED 执行记录（默认 `"-"` 即关闭）。
