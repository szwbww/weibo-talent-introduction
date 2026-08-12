# 04a 执行报告（execute-p）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter/docs/plans/fast/batch-send-rhythm-and-filter/children/04a/brief.md`
- Plan SHA-256: `8adc46663e2d8ed4fd077e83b97b12dd605a9913831345bcbfb16cdf349f96d7`
- Execution ID: `…/children/04a/brief.md@8adc46663e2d8ed4fd077e83b97b12dd605a9913831345bcbfb16cdf349f96d7`
- Execution epoch: NEW
- Executor: Impl04a
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter`
- Target branch: `fast/batch-send-rhythm-and-filter`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter@fast/batch-send-rhythm-and-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-rhythm-and-filter`
- Pre-execution code SHA: `4004c387920eaa6a99997ca833d038da5b281729`（child 03 product base；执行前 HEAD = `1ea4367` 为 docs 证据提交）
- Post-execution code SHA: `f3738e89a286764e3fb8a5c93dd178b89ffa0a42`（本实现提交）
- Evidence HEAD: 不适用（本计划无需单独证据提交；实现提交即 HEAD）
- Implementation boundary: `4004c38..f3738e8`（工作树提交；docs/plans/fast 工件未入提交）

## A-1 框架 spike 结论

**DTO 投影成功，未降级。** Spring Data JDBC（Boot 2.7.18 / Spring Data JDBC 2.4.x）的 `@Query` 支持返回非实体 DTO 投影：
`List<BatchConfigLastExecution>`（`data class BatchConfigLastExecution(batchConfigId: Long, lastStartedAt: LocalDateTime)`），
SQL 别名 `batch_config_id` / `last_started_at` 正确映射到构造参数，`MAX(started_at)` 聚合正确，`batch_config_id IS NULL` 的独立手动执行行被 `WHERE batch_config_id IN (:ids)` 天然排除。

验证方式：A-1 仓储方法（生产代码，已按 brief 写入 `TaskExecutionRepository.kt`）+ 临时 spike 测试
`TaskExecutionRepositoryProjectionSpikeTest`（`@DataJdbcTest` + 真实 MySQL 8.0.36 + Flyway 迁移，`-DmigrationIt=true` 门控），
结果 `Tests run: 1, Failures: 0, Errors: 0`。spike 测试为临时脚手架，已在提交前删除，不属于 7 个授权文件。

### spike 环境说明（非计划偏差，仅环境适配）

- 本机 Testcontainers 无法连接 Docker daemon：docker-java 固定以 API 1.32 协商，而 daemon（OrbStack，Docker 29.4.0）要求 ≥ 1.40（`DOCKER_HOST` / `DOCKER_API_VERSION` 均无效）。这正是 brief 已知基线「Docker 类集成测试失败」的根因。
- 因此改用 docker CLI 手动启动 `mysql:8.0.36` 容器（端口 13306），spike 测试通过 `@DynamicPropertySource` 直连，不经过 Testcontainers。
- Flyway 全量迁移在 V82 触发既有 `v82_trust_reply_baseline_gate`（fresh DB 上 QA 规则漂移检查失败 —— 与 brief 已知基线一致）；spike 将 `spring.flyway.target=73`（`task_execution.batch_config_id` 在 V73 已就绪），并在 `spring.flyway.placeholder-replacement=false`（V2 模板正文含字面 `${...}`）下完成迁移。生产/全量测试门禁不受影响（不传 `-DmigrationIt`）。

## 变更文件清单（提交内 7 个授权文件）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | +`BatchConfigLastExecution` 投影 data class；+`findLastStartedAtByBatchConfigIds(batchConfigIds: Collection<Long>): List<BatchConfigLastExecution>`（`SELECT batch_config_id, MAX(started_at) … GROUP BY batch_config_id`，I-4/I-5，SQL 无 trigger_type 过滤） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt` | +`lastExecutedAtByBatchConfigIds(ids)`：空集合返回 `emptyMap()`，否则单次聚合查询转 `Map<Long, LocalDateTime>` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | `BatchSendTaskConfigView` 末尾 +`nextFireTime: LocalDateTime? = null`、+`lastExecutedAt: LocalDateTime? = null`（带默认值，不打断既有构造点） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 构造器 +`taskExecutionService`；`toView(row, lastExecutedAt = null)` 填充两字段；+`computeNextFireTime`（I-1/I-2/I-3，`runCatching` 降级 null）；`list()` 单次聚合（I-4）；`get`/`update`/`setEnabled` 单条查询；+`previewCron(cron, count=5)`（I-3，非法返回 valid=false 不抛异常）；+`CronPreviewResult(valid, message, nextFireTimes)` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | +`POST /cron/preview`（置于 DELETE /configs/{id} 之后、POST /configs/{id}/execute 之前）；+`CronPreviewRequest(cron, count: Int? = null)`；恒返回 200 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | +7 用例（nextFireTime 非空/空、脏 cron 降级、list 恰好 1 次聚合 + 3 个 id、0 条配置空集合调用、previewCron 5 个严格递增时间、previewCron 非法 valid=false） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt` | +3 用例（合法 200 valid=true 长度 5、非法 **200** valid=false、GET /configs JSON 含 nextFireTime/lastExecutedAt 键） |

未修改：`BatchSendScheduler.kt`、`TaskExecution.kt`、迁移文件、`app.js`、`index.html`；`pom.xml` 无 diff（I-1：无第三方 cron 依赖）。

## 命令记录（JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn test -Dtest=TaskExecutionRepositoryProjectionSpikeTest -DmigrationIt=true -DfailIfNoTests=false`（DTO 投影 spike，首次） | 1 | Testcontainers 无法连接 daemon（API 1.32 < 1.40）——环境基线，改用直连方案后通过 |
| （同上，直连 scratch MySQL 后） | 0 | `Tests run: 1, Failures: 0, Errors: 0` —— **spike 通过，DTO 投影可用** |
| `mvn test -Dtest=BatchSendTaskConfigServiceTest,BatchSendConfigControllerTest -DfailIfNoTests=false` | 0 | Service `Tests run: 37, Failures: 0, Errors: 0`；Controller `Tests run: 8, Failures: 0, Errors: 0` |
| `mvn test "-Dtest=BatchSendTaskConfigServiceTest#previewCron returns 5 strictly increasing times for valid cron" -DfailIfNoTests=false` | 0 | 单方法通过 |
| `mvn test`（全量回归门禁） | 0 | `Tests run: 2373, Failures: 0, Errors: 0, Skipped: 5`（5 个 skipped = migrationIt 门控集成测试），BUILD SUCCESS |
| `mvn clean package` | 0 | `Tests run: 2373, Failures: 0, Errors: 0, Skipped: 5`，BUILD SUCCESS，war 产出 |
| `git diff --check` | 0 | 无输出 |

已知基线说明：`FlywayMigrationIntegrationTest` 等 Docker/Testcontainers 集成测试在默认 `mvn test` 下按 `@EnabledIfSystemProperty(migrationIt=true)` 跳过（Skipped: 5），未运行；该环境 Testcontainers 与 V82 基线门已知不可用（与 brief 记录一致，非本实现缺陷）。

## 验收标准核对（I-1..I-5 + 框架假设）

- **I-1**：`grep CronExpression.parse` 在 `BatchSendTaskConfigService.kt` 命中 3 处（既有校验 :244 + computeNextFireTime :400 + previewCron :407）；`git diff -- pom.xml` 为空 ✅
- **I-2**：`nextFireTime is null when autoEnabled is false` 用例通过 ✅
- **I-3**：脏 cron `toView` 降级 null 用例、`previewCron("bogus")` 用例通过；`computeNextFireTime` 与 `previewCron` 均含 `runCatching`；控制器用例断言非法表达式返回 **200** ✅
- **I-4**：`list queries lastExecutedAt exactly once with all ids`（`verify(times(1))` + captor）与 0 条配置用例通过；`toView` 方法体 grep 无 repository/service 调用 ✅
- **I-5**：新增查询 SQL grep `trigger_type` 为空（命中仅来自既有 `countActiveSince`）；spike 实测 `batch_config_id = NULL` 行被排除 ✅
- **框架假设已消解**：DTO 投影 spike 完成并记录（见上），采用 brief 首选方案，未触发降级甲/乙；提交信息无需标注降级 ✅

## 测试说明（实现过程中一次修复，非偏差）

新增用例沿用仓内既有 Mockito-Kotlin 惯例（`anyValue`/`captureValue` = matcher 占位 null 与默认值合并，
见 `BatchSendControlServiceTest.kt:50-52`）：`ArgumentCaptor.capture()` 返回 null，直传 Kotlin 非空参数会触发
Kotlin 内建空检查（`capture(...) must not be null`），故经 `captureValue` 辅助函数调用。
控制器 JSON 键断言用 `ObjectMapper` 注册 `JavaTimeModule` + `KotlinModule`（与 `TrustReplyWorkbenchStateStoreTest` 同构）。

## Deviations

- 无计划偏差。实现与 brief 的 A-1..A-6 完全一致，7 个授权文件，无新增生产文件。
- 仅环境适配：spike 数据库由 docker CLI 手动启动并直连（Testcontainers 在本机不可用，属已知基线）；`git diff --check` 通过。
- 提交信息 `feat(fast-p): implement 04a`，仅含 7 个授权文件（docs/plans/fast 工件未入提交）。

## Freshness

- Plan identity rechecked: YES（SHA 未变）
- Worktree identity rechecked: YES（branch/git-dir/HEAD 匹配）
- Reported commit reachable from target branch: YES（`f3738e8` 为 HEAD 且 `git merge-base --is-ancestor HEAD fast/batch-send-rhythm-and-filter` 通过）
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → 运行 `verify-p`（04a 独立验证）
