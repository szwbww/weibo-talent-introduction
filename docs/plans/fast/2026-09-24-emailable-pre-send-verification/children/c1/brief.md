# Fast-P Child Brief — c1（介绍邮件发送前验证与持久化明细）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-24/emailable-pre-send-verification.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-09-24/emailable-01-runtime-audit.md`，identity `commit:99aa2ed7e9c93cfc5552f47889670fae96816f01`。
- 只读证据附件（计划引用的 E-*/F-* 原文）：`docs/plans/2026-09-24/emailable-evidence.md`。
- Worktree / branch：见派发消息中的 `worktree`、`branch`、`child_base_sha`。
- 依赖：none。后续 c2（配置贯通）、c3（控制台与日志）依赖本 child 的接口，见下。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」表内的文件；不得新建白名单外文件，不得改动已应用的 Flyway 迁移（V1..V137 只读）。
3. 迁移号：V138 归本 child（实施前确认 `src/main/resources/db/migration` 中 V138 仍空缺；当前最高为 V137）。V139 留给 c2，本 child 不得创建。
4. 不得修改 `docs/plans/**`、不得写 `docs/plans/fast/**`（fast-p 证据由控制方提交）、不得修改 `.worktrees/**`。
5. 产品代码提交格式：`feat(fast-p): implement c1`；把 fast-p 报告/日志排除在该提交之外。
6. 基线事实（本 worktree 实测，勿按其它工作区推测）：
   - worktree 基线 = `main @ 7c7a9e7`；`src/main/resources/static/index.html` 的缓存键当前为 `20260924-account-editor`（主工作区里那份未提交的 `20260924-snippet-dialog-contrast` 改动不属于本 run，本 child 不涉及前端）。
   - `FlywayMigrationIntegrationTest` 现有约 20 处 `assertEquals("136", flyway().migrate().targetSchemaVersion)`（无显式 target 的“迁到最新”断言）在 V137 已存在的基线上是**预置红**；显式历史 target 断言（如 `flyway(MigrationVersion.fromVersion("130"))` → `"130"`）必须保持原值。
   - `docs/knowledge/**` 在本 worktree 中可能落后于规划会话在主工作区的未提交修订；以本 brief 与计划文本为准。
7. 本 child 不实现 UI、不实现配置表字段（c2）、不新增只读接口（c3）。

## Authorized Files（10）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 快照布尔开关 `emailVerificationEnabled`；跳过原因码 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 在介绍邮件共享引擎接入验证与审计收尾 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` | 新增：HTTP 验证、逐次执行上下文、标签与审计协调 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` | 新增：JdbcTemplate 仓储及同文件小型 DTO |
| 5 | `src/main/resources/db/migration/V138__create_batch_email_verification.sql` | 新增明细表、索引和级联外键 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt` | 新增：HTTP/标签/异常分类测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` | 新增：真实 MySQL 约束、分页、聚合、清理测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 发送边界与计数；补必需依赖 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 共享引擎回归；补必需依赖 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 新迁移与 latest 版本断言（138） |

计划 T1～T4 逐条给出了每文件的契约（列定义、结果矩阵、处理顺序、测试覆盖）。以计划文本为准，本 brief 只补充边界与下游接口。

## 必须保持不变（计划「必须不变」+ 主计划 I-1..I-4）

- 开关关闭时原发送行为、材料提醒行为、退订/抑制、研发类型、绑定账号、模板门禁、发送去重、账号限额、原成功/失败/跳过/剩余含义、已有专家标签与联系状态。
- 默认关闭；旧 `request_payload` JSON 缺字段 = false；只有 `INTRODUCTION` 允许 true；`MATERIAL_REMINDER + true` 在任何业务写入前拒绝。
- 服务故障绝不当邮箱异常；审计不可写时停止发送且不静默降级；`EMAIL_INVALID` 等现有语义不被新链路改写。

## 下游接口（c2/c3 依赖，必须按计划精确实现）

- `BatchExecutionSnapshot.emailVerificationEnabled: Boolean = false`（c2 从配置写入该字段；c3 读取 `requestSnapshot` 判定开关）。
- 新表 `batch_email_verification` 的列名/语义（c3 的只读接口与前端直接依赖）：`id, task_execution_id, expert_doc_id, orcid_id, expert_name, email, decision, provider_state, provider_reason, error_code, request_count, checked_at, send_status, send_reason, tag_status, tag_error, created_at, updated_at`。
- 仓储查询契约（c3 复用）：`listAfter(executionId, afterId, limit+1)`、`aggregate(executionId)`、`readPage`（同一 readOnly 事务内组合分页与汇总）；分页默认 50、最大 100，`hasMore` 由 limit+1 判定。
- 新增跳过码 `EMAIL_VERIFICATION_REJECTED`（文案“邮箱验证未通过”）与错误码 `EMAIL_VERIFY_AUTH_ERROR / EMAIL_VERIFY_NO_CREDITS / EMAIL_VERIFY_RATE_LIMITED / EMAIL_VERIFY_TIMEOUT / EMAIL_VERIFY_INCOMPLETE / EMAIL_VERIFY_BAD_RESPONSE / EMAIL_VERIFY_SERVICE_ERROR` 必须按计划字面实现（c3 会按 code 展示）。
- `decision`/`send_status`/`tag_status` 的取值集合必须与计划表一致（PENDING/PASS/SKIP/ERROR；NOT_SENT/SENDING/SENT/FAILED/SKIPPED；NOT_REQUIRED/PENDING/APPLIED/FAILED）。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationRepositoryIT,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true
```

第二条需要本地 Docker（已确认可用，testcontainers MySQL 8.0.36）。第一条会同时触发 `pom.xml` 绑定的 node 测试（`node --test src/test/js/*.test.js`、`node --check app.js`）。

## 交付

- 执行报告写入 `docs/plans/fast/2026-09-24-emailable-pre-send-verification/children/c1/execution.md`（报告本身不进实现提交）。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码、不得 push/merge/amend/squash。
