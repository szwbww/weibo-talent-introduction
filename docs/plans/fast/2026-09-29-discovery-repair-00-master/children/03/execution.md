# Child 03 — Execution Report（深度发现按整数小时动态调度 · 后端）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master/docs/plans/2026-09-29/discovery-repair-03-schedule-backend.md`
- Plan SHA-256: `21c451244e16ab58ecba05aaa34e86592a394ef3dd872c3281473e062a759058`（= brief 记录值；实测 `plan_identity.py`）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master/docs/plans/2026-09-29/discovery-repair-03-schedule-backend.md@21c451244e16ab58ecba05aaa34e86592a394ef3dd872c3281473e062a759058`
- Execution epoch: NEW（此前无同一 EXECUTION_ID 的执行记录；`children/03/execution.md` 原为空文件）
- Approval basis: 本次调用 = child brief `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/03/brief.md`（approved bytes = plan-seed commit `70f550658d078b228fe619b735d81f0e740c4db3`，sha256 与实测一致）
- Executor: `Child03Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Target branch: `fast/2026-09-29-discovery-repair-00-master`
- Worktree ID: `<root>@fast/2026-09-29-discovery-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Pre-execution code SHA: `bd0cb377987a800104c393af11254eb760519b68`（child_base_sha = child 02 终端 Code head）
- Pre-execution HEAD（进入时）: `c336b1a766158319a0e141812d4e45c75dec9763`（其后仅 child 02 fast-p 证据提交）
- Post-execution code SHA: `e94425cb0cd275254f33530548ba98033ddca6b4`（`feat(fast-p): implement 03`）
- Evidence HEAD: N/A（本报告由 controller 单独提交，未混入实现提交）
- Implementation boundary: `bd0cb377987a800104c393af11254eb760519b68..e94425cb0cd275254f33530548ba98033ddca6b4`（9 个文件，仅授权清单）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1（I-1）V144 迁移 + repository + service（事务提交后同步应用、严格整数校验、同值不重置锚点、一次 Clock 读数） | IMPLEMENTED | `V144__create_discovery_schedule_setting.sql`、`DiscoveryScheduleSettingRepository.kt`、`DiscoveryScheduleSettingService.kt` | `DiscoveryScheduleSettingRepositoryIT`（真实 MySQL + 真实 V144：建表契约/首写/同值/变化/越界/坏存量/事务回滚）、`DiscoveryScheduleSettingServiceTest`（一次读数、同值不动锚点、变化移动锚点、GET 不建行） |
| T-2（I-2/I-3/I-4）`ExpertDiscoveryScheduler` 去掉 `@Scheduled`、`ApplicationReadyEvent` 唯一动态 future、小时 Trigger、代次+`cancel(false)`、旧回调失效、启动读取失败不启动 | IMPLEMENTED | `ExpertDiscoveryScheduler.kt` | `ExpertDiscoverySchedulerTest`（无 `@Scheduled`；固定时钟直问 Trigger 得 13/16/19、跨午夜 5 小时、重启只取下一未来点、长任务不追补、时钟回拨 k≥1；cancel(false)+旧回调启动 0；运行中重排及时返回且不打断；注册失败 applied=false；读取失败不注册且不取消原调度；cron=-、连续模式） |
| T-3（I-1–I-4）`GET/PUT /api/expert-discovery/schedule`（固定字段、严格输入、400/409/503 与 saved 标志、GET 无副作用） | IMPLEMENTED | `DiscoveryScheduleController.kt` | `DiscoveryScheduleControllerTest`（真实 Jackson 解析后拒绝缺失/null/字符串/`1.5`/`1.0`/布尔/数组/超大数/0/169 且 0 次 save；接受 1 与 168；409 原因码；503 的 saved=false/true 两种变体；GET 200/503） |
| T-4（I-1–I-4）固定时钟 + 捕获 `TaskScheduler` 的 fake future；IT 走 testcontainers + 真实迁移 | IMPLEMENTED | 4 个测试文件 | 下述 Required Command 实测（含 IT 实际执行） |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_TOOL_OPTIONS=-Dapi.version=1.40 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=ExpertDiscoverySchedulerTest,DiscoveryScheduleSettingServiceTest,DiscoveryScheduleControllerTest,DiscoveryScheduleSettingRepositoryIT test` | PASS | exit code `0`；`Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| ↳ `ExpertDiscoverySchedulerTest` | PASS | `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0` |
| ↳ `DiscoveryScheduleSettingServiceTest` | PASS | `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` |
| ↳ `DiscoveryScheduleControllerTest` | PASS | `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0` |
| ↳ `DiscoveryScheduleSettingRepositoryIT` | PASS（**IT 实际执行**，非跳过） | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 27.8 s`；日志含 `tc.mysql:8.0.36 - Container mysql:8.0.36 started in PT13.26S` 与真实 V1..V144 Flyway 迁移 |
| 原始日志 | 证据 | `/tmp/fastp-c03-final.log`（controller 主机，未提交） |

未运行任何项目级/全包测试，未运行 `mvn clean package`（留给 controller）。

## Changed Files（9 个授权文件，提交用显式路径）

- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepository.kt` — 新表读写（仅 `find` 与单例行 `save`）+ 小模型 + 共享契约常量（模式/来源/原因码/1～168 范围/默认 cron 识别）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingService.kt` — 事务提交后同步应用、串行化保存、响应 DTO 组装
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleController.kt` — GET/PUT `/api/expert-discovery/schedule`、严格 JSON 与 400/409/503
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` — 去掉 `@Scheduled`、唯一动态 future、小时 Trigger、代次/取消/快照
- `src/main/resources/db/migration/V144__create_discovery_schedule_setting.sql` — 仅建表（单例行、无种子、无外键、无 `ON UPDATE`）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` — 周期/互斥/模式回归 + 新增动态触发覆盖
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingServiceTest.kt` — 提交/应用/失败
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleControllerTest.kt` — 严格输入、响应与状态码
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepositoryIT.kt` — 真实 MySQL 持久化与回滚

## Deviations

1. **环境层 API 版本（非计划内容）**：本机 Docker（OrbStack 29.4.0）要求 API ≥ `1.40`，而仓内 testcontainers 1.19.8 / docker-java 3.3.6 默认发 `v1.32`，导致任何 testcontainers IT 在连接阶段即 400（`client version 1.32 is too old. Minimum supported API version is 1.40`）。因此最终命令在**完全一致的 `mvn` 参数**前加了 `JAVA_TOOL_OPTIONS=-Dapi.version=1.40`（仅注入测试 JVM 的 `api.version` 系统属性；未改 `pom.xml`、未改测试代码、未改 `~/.testcontainers.properties`）。不加该变量时 IT 会以 `IllegalState Docker is required…` 失败（这是环境约束，不是实现缺陷）。
2. **响应超集（仅新增字段）**：`GET/PUT` 响应在 T-3 的固定字段之外增加了 `saved`（brief 明确要求区分 503 的 `saved=false/true` 两种变体，且 T-3 要求 GET/PUT 返回同一形状）与 `message`（面向操作端的一句话文本，供 04 按「显示服务端原因/文本」渲染，不替代 `reason` 码）。原有字段名/类型未改、未删。
3. **应用判定包含可编辑性**：`applied` 还要求当前模式可编辑（连续模式/`enabled=false`/`cron=-` 一律 `applied=false`，`reason` 取对应原因码），避免「不可编辑却显示已生效」。
4. 计划未细化的一处实现决策：`applyLegacySchedule` 中 **`cron=-` 优先于库里遗留的小时设置**（禁用语义优先，`nextTriggerAt=null`）；`interval_hours` 值变化与首次保存只在 service 里判定，repository 只负责无条件写入给定锚点。

## Freshness

- Plan identity rechecked: YES（执行前后 sha256 均为 `21c451…5058`，未变）
- Worktree identity rechecked: YES（root/branch/git-dir 与预执行一致；提交为 `fast/2026-09-29-discovery-repair-00-master` 的 HEAD 且可达）
- Reported commits reachable from target branch: YES（`e94425c` = `fast/2026-09-29-discovery-repair-00-master` 的 HEAD，父提交 `c336b1a`）
- Required commands run this invocation: YES（最后一次运行在最终实现状态下，含 IT 实际执行）
- Historical evidence used only as baseline: YES（仅用于对照基线 15 项 SchedulerTest 计数）

## Remaining Blocker

- None（实现与定向验证均完成；项目级 `mvn clean package` 与后续验证由 controller/`verify-p` 负责）

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
