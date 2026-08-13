# 05 · P-E：RecipientScope 接入专家状态过滤

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/05-recipient-scope-status-filter.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：**01（P-A）+ 03（P-B），均已落地** ｜ 子系统：2（后端 + 前端）｜ 10 文件（= 上限，若超出必须停下报告，不得自行扩）

## 授权文件（Authorized Files）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql` | 新增 |
| 2 | `src/main/kotlin/…/campaign/domain/BatchSendTaskConfig.kt` | 改 |
| 3 | `src/main/kotlin/…/campaign/domain/BatchExecutionModels.kt` | 改 |
| 4 | `src/main/kotlin/…/campaign/service/BatchSendTaskConfigService.kt` | 改 |
| 5 | `src/main/kotlin/…/campaign/service/ManualInitialOutreachService.kt` | 改 |
| 6 | `src/main/kotlin/…/expert/service/ExpertSearchService.kt` | 改 |
| 7 | `src/main/resources/static/index.html` | 改 |
| 8 | `src/main/resources/static/app.js` | 改 |
| 9 | `src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 改 |
| 10 | `docs/knowledge/campaign/K-recipient-scope-status-filter.md` | 新增 |

> **Amendments A5（HUMAN 批准 2026-08-13）**：授权 `src/test/kotlin/…/campaign/OperatorStatusWriteSeamGuardTest.kt`
> 排除项更新——`ExpertSearchService.kt` 钉死点 `:332`→`:345`，新增 BatchExecutionModels.kt /
> BatchSendTaskConfigService.kt 的 10 处配置映射排除项（均附 file:line+context；
> `ALLOWED_WRITE_SITES` 白名单闭包与断言**不动**）。该守卫更新随本计划提交。

**禁止**：`styles.css`（零 diff）；其他文件；`docs/plans/fast/*`。

## 关键不变量（详见计划文件）

- **I-1** 状态过滤覆盖全部查询旁路：① `buildEsFiltersForLevel()`（:1211-1224）② `RecipientScope.matchesExpert()`（BatchExecutionModels.kt:55-77，重试路径）③ `buildMaterialReminderEsFilters()`（:1075）。三条缺一不可（K-batch-send-filter-retry-parity）。
- **I-2** APPLICATION 分支（else 分支）必须有状态过滤。
- **I-3** `NOT_CONTACTED` 语义唯一：复用 `ExpertSearchService` 的 `must_not exists` 表达（`notContactedWithEmailFilters:116-127`），不得写 `term operatorStatus=NOT_CONTACTED`；抽出 `operatorStatusFilter(status)` 供三处调用。
- **I-4** 旧适配器显式保留：`updateLegacyConfig()`（:165-200）显式写 `operatorStatus = existing.operatorStatus`。
- **I-5** 三类映射区分：加 `toView()`（:368）与三个 `*Fields()`（:497/:514/:531）与 `ConfigFields`（:462）；**不要**加 `toLegacyConfig()`（:208）与 updateLegacyConfig 返回值构造。
- 校验白名单引用 `OperatorStatus.entries`（照 :561 的 `ALLOWED_DISCIPLINES` 范式）。
- 前端：两个面板各加"专家状态"下拉，option 取自 `operatorStatusOptions`（app.js:618-624）+ "全部"空值；样式复用 `.batch-config-field` + `.bsc-input`，styles.css 零改动。
- 前端注册点行号可能漂移（知识条目注明），执行时重新 grep 复核。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
# FlywayMigrationIntegrationTest：Amendments A2（HUMAN 指令 2026-08-13）起跳过——本机该 IT 在
# pre-existing V82 drift-gate 失败（与 05 无关）；不再属于必跑命令
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。Docker 本机可用，Flyway IT 须实际运行。

## 基线（Baseline）

`mvn clean package` exit 0：surefire 2378+ / 0 / 0 / 4 skipped（01/03 落地后以 fresh 实际为准）；JS 496 pass。

## 下游接口（Downstream Interfaces）

- **06（P-F）**：消费 `RecipientScope`（含新 `operatorStatus` 字段）与 `BatchExecutionSnapshot`；预估复用 `countEsTargets`/`buildRetryableTargets`/`PendingOutreachSummary`——05 不得破坏这些构件的签名与语义。
- 验收标准：`grep "operatorStatus" ManualInitialOutreachService.kt` 在三个旁路函数内均有命中；`matchesExpert` 内有命中；`updateLegacyConfig` 单测保持字段。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循。
2. 只改上述 10 个授权文件；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. 发现任一项超上限（文件数/子系统数）→ 停下返回 PLAN_CONFLICT（计划要求二次拆分）。
4. 最终实现状态上 fresh 运行全部必跑命令。
5. 实现提交：`feat(fast-p): implement 05`。
6. 完整执行结果追加写入 `<worktree>/docs/plans/fast/batch-send-status-consistency/children/05/execution.md`。
7. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
8. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
