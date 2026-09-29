# Child 03 Brief — 深度发现按整数小时动态调度（后端）

Approved child plan: `docs/plans/2026-09-29/discovery-repair-03-schedule-backend.md`
Approved bytes identity: plan-seed commit `70f550658d078b228fe619b735d81f0e740c4db3`; sha256 `21c451244e16ab58ecba05aaa34e86592a394ef3dd872c3281473e062a759058`.
Read the full child plan from disk before implementing; it is the complete approved contract (需求描述 / 关键不变量 / 现状审计 / 实现方案 T-1–T-4 / 变更文件清单 / 验收标准 / 人工验收清单).

## Execution context

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- `child_base_sha`: the terminal `Code head` of child 02, exactly as recorded in `docs/plans/fast/2026-09-29-discovery-repair-00-master/ledger.md` and stated in the dispatch message.
- Master plan: `docs/plans/2026-09-29/discovery-repair-00-master.md` (same seed commit). Master invariant **M-3（只调整后续触发）** is the parent rule; child invariants I-1–I-4 below are governing.
- Execution report to write: `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/03/execution.md` (controller commits it as fast-p evidence; never include `docs/plans/fast/**` in the implementation commit).
- Use the `execute-p` skill with this brief plus the exact child plan; return its report shape.

## Hard constraints

- Only the 9 Authorized Files below may change.
- Migration version: `V144` is confirmed free at this base (latest applied migration in this worktree is `V143__create_expert_replied_dismissal.sql`). Never edit an already-applied migration.
- New table is business-purpose only: `id TINYINT NOT NULL PRIMARY KEY` fixed 1, `interval_hours SMALLINT NOT NULL`, `updated_at DATETIME(3) NOT NULL` (UTC written/read by application, **no** `ON UPDATE`), no seed row, no foreign key, no JSON settings blob. Do not reuse the `batch_send_setting` KV compatibility table.
- Preserve: 未保存时既有 cron；手动启动/取消、统一运行互斥、查询条件/检查点/限额；连续流水线 30 秒恢复 tick 与暂停状态；`enabled=false`/`cron=-` 原有禁用语义；同步模式固定查询条件（excludeCountries=[CN]、openAccessOnly=true、subjectScope=RND_TARGET、includeRawScan 沿用配置）不得被小时设置暗中改变。
- Out of scope: cron编辑器、分钟/日历调度、开关/删除设置、多份发现查询、启用连续模式、跨实例调度平台、历史数据处理、新线程池.
- Reuse `SchedulingConfig.taskScheduler` via `@Qualifier("taskScheduler")`; do not create a new thread pool. Do not modify the batch-send scheduler.
- Failure semantics: DB commit then synchronous re-apply; re-apply failure ⇒ HTTP 503 with `applied=false` message; DB failure must not cancel the existing schedule; `cancel(false)` must not interrupt a running task; a newly cancelled generation's late callback must not start a task.
- Minimal diff; no unrelated refactoring. One local commit with exact subject `feat(fast-p): implement 03`. No push/merge/rebase/amend/squash; do not touch other worktrees.

## Authorized Files (9)

| # | File | Action |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepository.kt` | 新表SQL与小模型 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingService.kt` | 事务提交后同步应用、DTO |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleController.kt` | GET/PUT、校验与错误 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` | 单一动态触发与生命周期 |
| 5 | `src/main/resources/db/migration/V144__create_discovery_schedule_setting.sql` | 仅建表 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` | 周期、互斥、模式回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingServiceTest.kt` | 提交/应用/失败 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleControllerTest.kt` | 严格输入、响应 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepositoryIT.kt` | 实际MySQL持久化 |

## Child invariants (verbatim contract)

### Invariant I-1：一个设置、一条事实来源
- Rule：单例表 id=1，interval_hours 为1～168整数。无行代表"沿用部署 cron"，不是停用也不是已保存2小时。updated_at 作为该小时周期的锚点，使用UTC精度毫秒；首次保存/值变化更新锚点，相同值重存不重置。GET不建行。
- Violation consequence：重启/重复保存改变节奏，或把默认 cron 擅自迁移成不同语义。

### Invariant I-2：按持续小时计算，不按日内 cron 取模
- Rule：锚点 T、间隔 N 的候选点为 T+k×N小时（k≥1）。只选择严格晚于当前时间及上次完成时间的点；不立即执行、不补排漏掉的点、不因跨午夜改周期。任务运行中跳过，不排队追加一次。
- Violation consequence：每5小时被错误写成 cron */5，跨午夜出现4小时间隔或发生补跑。

### Invariant I-3：已保存与已应用必须一致才报成功
- Rule：DB提交后同步重排；重排成功才返回200和applied=true。重排失败返回503，明确"已保存但定时应用失败，请重试保存"；DB失败不取消原调度。重存相同值可重新应用。新旧同步定时最多一个有效代次，cancel(false)不打断运行任务。
- Violation consequence：页面假成功、重复启动、保存失败却停掉原计划。

### Invariant I-4：小时设置只控制当前同步定时
- Rule：enabled=true、cron非"-"、pipelineEnabled=false才允许保存。其他模式GET返回editable=false，PUT 409且不写库。连续模式保持现有 cron tick + pipelineTick，不应用小时设置；暂停不会被保存或页面读取解除。同步模式移除原@Scheduled注册，统一交给一个动态future。
- Violation consequence：两个启动器同时运行、页面看似限速却被30秒tick绕过，或恢复已暂停流水线。

## Tasks

- **T-1（I-1）** V144 迁移 + repository(JdbcTemplate，仅 find 与按 id=1 保存) + service（保存串行化；`TransactionTemplate` 完成 DB 事务后再调用 scheduler 重新应用；first-save 与不同值保存使用一次 Clock 读数；同值保留 updated_at；JSON 严格只接受整数数值，拒绝缺失/null/字符串/小数/布尔/越界，禁止 Jackson 把 1.5 截成 1，校验用 controller 本地 JsonNode，不改全局 ObjectMapper）。
- **T-2（I-2、I-3、I-4）** `ExpertDiscoveryScheduler.kt` 去掉原 `@Scheduled`；`ApplicationReadyEvent` 读取设置并注册唯一 future；同步模式无设置用 `CronTrigger(cron, Asia/Shanghai)`，有设置用小时 Trigger（`Instant` 运算：`base=max(clock.now,lastCompletion)`，`next=T+(floor((base-T)/N)+1)×N`，且 k≥1；同步执行结束后再算下一未来槽；禁止 `scheduleAtFixedRate`）；保留 `scheduleDiscovery` 入口并抽取"短时占槽/长时执行"供动态回调复用（小锁内检查代次并占用 `TaskProgressStore` 运行槽，释放锁再 discover）；保存时代次递增、`cancel(false)` 旧 future、注册新 future；启动读取失败时不启动同步发现并显式告警，不得默默按旧默认频率执行。
- **T-3（I-1–I-4）** `DiscoveryScheduleController.kt`：GET/PUT `/api/expert-discovery/schedule`（复用现有认证与错误响应约定，不新增角色）；响应字段固定 `mode`(LEGACY/CONTINUOUS)、`editable`、`source`(CONFIG/OVERRIDE)、`intervalHours`（未保存默认已知 cron 显示 2，其他 cron 为 null）、`anchorAt`、`nextTriggerAt`、`applied`、`reason`；默认 cron 识别只对准确的 `0 0 */2 * * ?` 返回 2；HTTP 400/409/503 语义与 saved 标志按计划；GET 无副作用。
- **T-4（I-1–I-4）** 测试：可控 `Clock` + 捕获 `TaskScheduler` 的 fake future 验证真实 Trigger（不用 sleep 等小时）；IT 走 testcontainers + 真实 V144 迁移（首写、同值不改时间、变化更新、事务失败）；覆盖同值重存恢复应用、两次相反顺序保存、DB失败不取消、注册失败503、旧回调不能启动、运行中保存及时返回、重启不补跑、手动/定时互斥、连续暂停保持、cron=-与enabled=false；DB断连与坏存量值不得被解释为"无行"。

验收标准与人工验收清单（A-1–A-3）以子计划原文为准。

## Required command (fresh, after final implementation state, from the worktree root)

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=ExpertDiscoverySchedulerTest,DiscoveryScheduleSettingServiceTest,DiscoveryScheduleControllerTest,DiscoveryScheduleSettingRepositoryIT test
```

A local Docker/OrbStack runtime is available; testcontainers MySQL `8.0.36` image is already pulled. Gate the new IT with `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` so the command above actually executes it (same precedent as `ExpertAcademicEnrichmentJobRepositoryIT`), and follow the existing `DataJdbcTest` + `AutoConfigureTestDatabase.Replace.NONE` + dynamic-properties testcontainers pattern. Never mark a skipped IT as passed. Record exit code and per-class test/failure/error/skip counts, and state explicitly that the IT executed. Do not run project-wide or full-package suites; do not run `mvn clean package`.

## Downstream interface (child 04 consumes this API)

- `GET/PUT /api/expert-discovery/schedule` response shape and status codes above are the contract child 04 renders; `nextTriggerAt`/`anchorAt` are ISO-8601 with offset/Z, displayed in Beijing time by the UI.
- `reason` codes must be distinguishable for: 不可编辑模式、应用失败、DB不可用; `applied=false` must accompany 503 responses (both `saved=false` and `saved=true` variants).
- Scheduler snapshot access must go through `ObjectProvider<ExpertDiscoveryScheduler>` from the service side (scheduler depends only on the repository) — no circular dependency.
- Child 04 does not read the DB directly.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
