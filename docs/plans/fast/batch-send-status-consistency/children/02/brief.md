# 02 · P-D：operator_status 唯一写入口的守卫测试

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/02-single-writer-guard-test.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：**01（P-A，已落地）** ｜ 与 01 同发布列车 ｜ 子系统：1（测试）｜ 2 文件

## 前置事实（来自 01 的落地结果，实施前须复核）

- 01 落地后 `grep "operatorStatus = " src/main/kotlin` 的 DB 写入恰为 4 处：
  `ExpertOperatorStatusService.kt`（changeStatus :30 + updateAutomatically :61）、
  `ManualInitialOutreachService.kt`（:611 建行初始化 + :706 EMAIL_INVALID）。
- 白名单 = `{ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt}`。
- 7 处 DTO 赋值噪声（非写入，必须不误报）：
  `UnmatchedInboundMailController.kt:203/1097`、`MailboxService.kt:165`、
  `ExpertContactManagementController.kt:549`、`ExpertIndexController.kt:85/410`、
  `ExpertSearchService.kt:332`。
- 若实际落地结果与此不符（写入点多于 4 处或文件不同）→ 停止并返回 PLAN_CONFLICT，不要自行改白名单。

## 授权文件（Authorized Files）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/test/kotlin/…/campaign/OperatorStatusWriteSeamGuardTest.kt` | 新增 |
| 2 | `docs/knowledge/campaign/K-operator-status-write-seam-guard.md` | 新增 |

**禁止**：任何生产代码改动（本计划只加测试）；其他文件；`docs/plans/fast/*`。

## 关键不变量（详见计划文件）

- **I-1** 白名单闭包：`src/main/kotlin` 下对 `ExpertContact.operatorStatus` 的赋值位置集合**恰好等于**白名单，多一个少一个都失败。正则同时覆盖 `operatorStatus = `（copy/构造命名参数）与 `operator_status`（SQL @Query）。
- **I-2** 白名单变更必须显式：失败信息给出违规 `file:line` 与"新增写入点须登记白名单"说明。
- 排除规则基于**文件路径 + 上下文**（声明/形参/DTO 字段），写成显式列表加注释；排除规则本身不得用模糊启发式。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。

## 基线（Baseline）

`mvn clean package` exit 0：surefire 2378 / 0 / 0 / 4 skipped（01 落地后数字可能微增，以 fresh 实际为准）；JS 496 pass。

## 下游接口（Downstream Interfaces）

- 本计划无下游子计划；守卫测试本身即后续所有计划对 `operator_status` 写入的机器护栏。
- 反向验证（I-2）：临时在非白名单文件加 `contact.copy(operatorStatus = "CONTACTED")` → 守卫必须失败且含 file:line → 回滚。验证过程不得留在最终代码里。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循。
2. 只改上述 2 个授权文件；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. 最终实现状态上 fresh 运行全部必跑命令（含反向验证的临时改动验证与回滚，记录证据）。
4. 实现提交：`feat(fast-p): implement 02`。
5. 完整执行结果追加写入 `<worktree>/docs/plans/fast/batch-send-status-consistency/children/02/execution.md`。
6. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
7. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
