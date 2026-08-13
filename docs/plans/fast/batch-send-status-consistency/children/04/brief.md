# 04 · P-C：operator_status 状态对账作业

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/04-operator-status-reconciler.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：**01（P-A，已落地）** ｜ 子系统：1（后端）｜ 7 文件

## 授权文件（Authorized Files）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/kotlin/…/campaign/service/OperatorStatusReconcileService.kt` | 新增 |
| 2 | `src/main/kotlin/…/task/service/MailAutomationScheduler.kt` | 改 |
| 3 | `src/main/kotlin/…/config/MailSchedulingProperties.kt` | 改 |
| 4 | `src/main/kotlin/…/expert/controller/ExpertIndexController.kt` | 改 |
| 5 | `src/main/kotlin/…/campaign/repository/ExpertContactRepository.kt` | 改 |
| 6 | `src/test/kotlin/…/campaign/service/OperatorStatusReconcileServiceTest.kt` | 新增 |
| 7 | `docs/knowledge/campaign/K-operator-status-reconcile.md` | 新增 |

**禁止**：任何写路径改动（本计划只读不写）；其他文件；`docs/plans/fast/*`。

## 关键不变量（详见计划文件）

- **I-1** 对账只读：新增代码不得写入 `expert_contact` 或任何 ES 索引；服务类不注入任何 writer；测试断言全部写方法 `verifyNoInteractions`。
- **I-2** 人工覆盖不参与差异判定：有 `action_type='CHANGE_OPERATOR_STATUS'` 的 `operator_action_log` → 单列"人工覆盖"，不计异常（changeStatus 写日志、updateAutomatically 不写——01 落地后该判别器仍成立）。
- **I-3** COMPLETED 不可派生：一律视为人工终态，不参与期望值计算，不判异常。
- **I-4** 期望值映射有据可依：CONTACTED=OUTBOUND INTRODUCTION SENT（`hasSentIntroduction():895` 逐字）；INVITED=MEETING_INVITATION OUTBOUND SENT；REPLIED=存在 INBOUND；MATERIALS_RECEIVED=有材料附件；EMAIL_INVALID=退信记录。
- T-1：全表扫描 + 内存比对（2062 行）；ES 侧 500 条分批 terms；产出 `ReconcileReport`（总数/一致/DB 与期望不符/ES 与 DB 不符/人工覆盖/各差异前 20 条样本）。
- T-2：新增属性 `operatorStatusReconcileCron: String = "-"`（默认关闭）；`@Scheduled` 方法 taskType=`OPERATOR_STATUS_RECONCILE` 走 `runAndRecordWithResult`；`POST /api/experts/reconcile-operator-status` 照抄 `ExpertIndexController:197-216`。
- T-3：查询归属（ExpertContactRepository 或 OperatorActionLogRepository）执行前 grep 确认。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusReconcileServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。

## 基线（Baseline）

`mvn clean package` exit 0：surefire 2378+ / 0 / 0 / 4 skipped（01 落地后数字可能微增，以 fresh 实际为准）；JS 496 pass。

## 下游接口（Downstream Interfaces）

- 无下游子计划。本计划只读，不改变 01/02 建立的状态语义。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循。
2. 只改上述 7 个授权文件；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. 最终实现状态上 fresh 运行全部必跑命令。
4. 实现提交：`feat(fast-p): implement 04`。
5. 完整执行结果追加写入 `<worktree>/docs/plans/fast/batch-send-status-consistency/children/04/execution.md`。
6. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
7. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
