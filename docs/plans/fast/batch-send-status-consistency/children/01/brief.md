# 01 · P-A：operator_status 收敛为唯一写入口

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/01-operator-status-single-writer.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：none ｜ 与 02（P-D）同发布列车 ｜ 子系统：1（后端）｜ 9 文件

## 授权文件（Authorized Files）

| # | 文件（包路径按实际定位） | 类型 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/…/campaign/service/ExpertOperatorStatusService.kt` | 改 | T-1 |
| 2 | `src/main/kotlin/…/mail/service/ManualExpertMailService.kt` | 改 | T-2 |
| 3 | `src/main/kotlin/…/campaign/service/ManualOutreachTxHelper.kt` | 改 | T-3 |
| 4 | `src/main/resources/db/migration/V94__backfill_operator_status_for_manual_sends.sql` | 新增 | T-4 |
| 5 | `src/test/kotlin/…/campaign/service/ExpertOperatorStatusServiceTest.kt` | 改 | T-5 |
| 6 | `src/test/kotlin/…/mail/service/ManualExpertMailServiceTest.kt` | 改 | T-5 |
| 7 | `src/test/kotlin/…/mail/service/ManualExpertMailServiceGateTest.kt` | 改 | T-5 |
| 8 | `src/test/kotlin/…/campaign/service/ManualOutreachTxHelperTest.kt` | 改 | T-5 |
| 9 | `docs/knowledge/campaign/K-operator-status-single-writer.md` | 新增 | Phase 6 |

**禁止**：任何前端文件；`ExpertIndexWriterService.kt`（零改动）；其他后端/测试/资源文件；`docs/plans/fast/*`（证据由控制器提交）。

## 关键不变量（详见计划文件）

- **I-1** 单调不回退：自动写入仅沿 ordinal 正向；目标 ordinal ≤ 当前 → 返回入参，不写 DB/ES。替换 `:56-59` 的 REPLIED 专用判断；`:53-55` COMPLETED 短路保留。
- **I-2** `EMAIL_INVALID` 旁路终态：`current == "EMAIL_INVALID"` 无条件短路；不进枚举。
- **I-3** 唯一自动写入口：全仓自动写仅 `updateAutomatically` 一处；`ManualOutreachTxHelper`（删 `:46` 硬编码 + `:84` 直接 ES 同步）与 `ManualExpertMailService`（transition 之后追加调用，顺序不可颠倒）都收敛到它。
- **I-4** mailType 白名单：仅 `INTRODUCTION → CONTACTED`、`MEETING_INVITATION → INVITED`，其余返回 null（零调用）。
- **I-5** ES 侧 NOT_CONTACTED = 字段缺失；`ExpertIndexWriterService` 零改动。
- T-2 构造参数位置：`expertOperatorStatusService` 加在 `senderAccountBindingService` 之后、两个带默认值参数之前。
- 依赖方向核验：`ManualExpertMailService` → `ExpertOperatorStatusService` 已存在先例（AutoMailReplyService:484），不构成新循环。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertOperatorStatusServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualExpertMailServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualExpertMailServiceGateTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualOutreachTxHelperTest
# FlywayMigrationIntegrationTest：Amendments A1（HUMAN 指令 2026-08-13）起跳过——本机该 IT 在
# pre-existing V82 drift-gate 失败（与 01 无关）；不再属于必跑命令
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。Docker 本机可用，Flyway IT 须实际运行。

## 基线（Baseline，seed 37ebb35）

`mvn clean package` exit 0：surefire 2378 / 0 / 0 / 4 skipped；JS 496 pass。`git diff --check` clean。

## 下游接口（Downstream Interfaces）

- **02（P-D）**：01 落地后 `grep "operatorStatus = " src/main/kotlin` 的 DB 写入恰为 4 处（changeStatus / updateAutomatically / ManualInitialOutreachService:611 初始化 / :706 EMAIL_INVALID）；`ManualOutreachTxHelper.kt` 中 `operatorStatus` 出现 0 次。02 的白名单初始内容 = 本计划的验收 A-8 结果。
- **04（P-C）**：`operator_action_log` 的 `CHANGE_OPERATOR_STATUS` 是人工覆盖判别器（changeStatus 写日志、updateAutomatically 不写）——本计划不得改变该事实。
- 7 处 DTO 噪声位置（UnmatchedInboundMailController:203/1097、MailboxService:165、ExpertContactManagementController:549、ExpertIndexController:85/410、ExpertSearchService:332）不得被误伤为写入点。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循（Plan Identity Gate / Target Worktree Gate / 输出契约）。
2. 只改上述 9 个授权文件；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. T-5 测试按计划 +4/+3/构造参数/+1；`ManualOutreachTxHelper` 换构造参数前 grep 复核该文件内 `expertIndexWriterService` 除 `:84` 外无其他用途。
4. 最终实现状态上 fresh 运行全部必跑命令，记录退出码与计数。
5. 实现提交：`feat(fast-p): implement 01`。
6. 完整执行结果（execute-p 输出契约 + 变更文件说明 + 命令表 + 偏差）追加写入
   `<worktree>/docs/plans/fast/batch-send-status-consistency/children/01/execution.md`。
7. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
8. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
