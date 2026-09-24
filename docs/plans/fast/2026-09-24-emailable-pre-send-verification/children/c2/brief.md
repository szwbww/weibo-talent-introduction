# Fast-P Child Brief — c2（定时配置与手动快照开关贯通）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-24/emailable-pre-send-verification.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-09-24/emailable-02-task-config.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 只读证据附件：`docs/plans/2026-09-24/emailable-evidence.md`（E-2 配置表全路径候选）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：c1（已 LIGHT_PASS；其接口见下）。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」表内的文件；不得改动已应用迁移（V1..V137 只读）与 c1 的 `V138__create_batch_email_verification.sql`。
3. 迁移号：V139 归本 child；实施前确认 `src/main/resources/db/migration` 最高为 V138 且 V139 空缺。
4. 不得修改 `docs/plans/**`、不得写 `docs/plans/fast/**`、不得修改 `.worktrees/**`、不得触碰 `src/main/resources/static/**`（前端属 c3）。
5. 产品代码提交格式：`feat(fast-p): implement c2`；fast-p 报告/日志不进该提交。
6. 本 worktree 前端缓存键基线为 `20260924-account-editor`；主工作区的 `20260924-snippet-dialog-contrast` 未提交改动不属于本 run。

## Authorized Files（8）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 实体/View/Create/Update 的新开关 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 所有读写/规范化/旧接口保留 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 配置→执行快照映射 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 直接手动快照启动校验 |
| 5 | `src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql` | 现有配置表增加一个布尔列 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 读写/旧接口/缺省兼容 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 手动/定时/不合法快照 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 旧配置迁移及 latest 版本断言（139） |

计划 T1～T3 给出逐条契约（列定义、保值语义、快照传递、类型守卫）。以计划文本为准。

## 关键不变量（计划 I-1..I-4）

- I-1：`batch_send_task_config` 只新增 `email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE`；Entity/View/Create 均 `Boolean=false`；Update 用 `Boolean?=null`（缺省或 null 保留现值，显式 false 关闭）。
- I-2：所有写路径保值 —— `create/update/setEnabled/softDelete/updateLegacyConfig`、`ConfigFields/NormalizedConfig`、三组 `toFields`、`toView` 全覆盖；`updateLegacyConfig` 必须显式带 `existing` 值（K-batch-config-legacy-adapter-field-preservation）。
- I-3：`normalizeAndValidate` 与 `validateSnapshotFields` 双入口都只允许 `INTRODUCTION` 为 true；`MATERIAL_REMINDER + true` 返回 400；人数预估不调用 Emailable。
- I-4：启动时把开关固定进 `task_execution.request_payload`；手动临时覆盖不回写配置；运行中改配置/软删不改本次与历史快照；不改 `BatchSendScheduler` 调度机制。

## 上游依赖（c1 已交付，直接复用，不要重复实现）

c1 终态：`LIGHT_PASS_WITH_NOTES`，Code head `0965a037d94e198f6ce8b13900149a09d35683b4`，证据提交 `55afb93d32b23ebfd34dbc12e0268ba2c702393a`；实现报告 `children/c1/execution.md`、验证报告 `children/c1/verify-log.md`。

- `BatchExecutionSnapshot.emailVerificationEnabled: Boolean = false` 已落在 `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`（尾部参数、默认 false，旧 JSON 缺字段读作 false）。本 child 的 `toExecutionSnapshot` 负责从配置实体复制该值；不要重复定义字段。
- 运行期守卫已在 `ManualInitialOutreachService`（入口类型/配置校验、`EMAILABLE_API_KEY` 检查、验证明细表与仓储、`EMAIL_VERIFICATION_REJECTED` 跳过码、`EMAIL_VERIFY_*` 错误码）。本 child 只做配置→快照传递与双入口校验，不修改发送引擎。
- c1 新增的受控码（`EMAIL_CHANGED`、`EMAIL_VERIFY_AUDIT_FAILED`、`EMAIL_VERIFY_SEND_STATE_CONFLICT`）属于 c1 已交付语义，c2 不得重命名或复用为配置校验错误。
- 迁移状态：V138 已被 c1 占用并验证通过；V139 归本 child。

## 下游接口（c3 依赖，必须精确实现）

- `BatchSendTaskConfigView.emailVerificationEnabled: Boolean`（c3 编辑回填与列表 pill 读取）。
- `BatchSendTaskConfigCreateCommand.emailVerificationEnabled: Boolean = false` 与 `BatchSendTaskConfigUpdateCommand.emailVerificationEnabled: Boolean? = null`（c3 的保存/更新请求字段名必须与 JSON 序列化名一致：`emailVerificationEnabled`）。
- `toExecutionSnapshot` 把该 bool 写入快照；`validateSnapshotFields` 对直接手动快照做类型守卫（c3 的手动草稿走同一快照路径）。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendSchedulerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

第二条需要本地 Docker（testcontainers MySQL 8.0.36）。**Docker 环境事实**：OrbStack 拒绝 testcontainers 默认 client API 1.32，因此 IT 命令必须加 `-Dapi.version=1.40` 并显式设 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`：

```sh
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40
```

第一条会同时触发 `pom.xml` 绑定的 node 测试。

## 交付

- 执行报告写入 `docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c2/execution.md`（报告本身不进实现提交）。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码、不得 push/merge/amend/squash。
