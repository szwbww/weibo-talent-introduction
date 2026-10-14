# Child 09-send-guard — Execution Report

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-person-09-send-guard.md`
- Plan SHA-256: `1489119289122de3e89935744084c1fc30b302c3f2a54404d423d6f112aa59de`（A14 修订版）
- Execution ID: `.../discovery-person-09-send-guard.md@1489119289122de3e89935744084c1fc30b302c3f2a54404d423d6f112aa59de`
- Execution epoch: NEW（epoch 1）
- Executor: `Impl09` (fast-p child 09)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Target branch: `fast/2026-10-13-discovery-identity-recovery-master`
- Worktree ID: `...weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master@fast/2026-10-13-discovery-identity-recovery-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Pre-execution code SHA / HEAD: `be1408eb8bf03a5c660e216b8726dbc830a57919`
- Implementation boundary: HEAD `be1408eb…` → working tree（11 个授权文件）
- child_base_sha: `4ee251f7c90bea7cf556c5536f8bb522c5cebc09`（已核对）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| V157 `discovery_person_outreach` 新表（复合主键 `(person_id, mail_type)` + 唯一键 + FK） | IMPLEMENTED | `V157__create_discovery_person_outreach.sql` | C2：46 tests / 0 failures（含 V157 表存在/复合主键/唯一/列可空性/FK 断言） |
| `DiscoveryPersonOutreachGuardService` 单写者（JDBC 条件更新 + 行锁 + CAS；无 JVM synchronized；无超时解锁） | IMPLEMENTED | `DiscoveryPersonOutreachGuardService.kt` | C1：`DiscoveryPersonOutreachGuardServiceTest` 11 tests / 0 failures（真库并发 2 worker、状态机、历史保护、SAFE_RETRY 绑定、revision 变化、SENDING 超时不解锁） |
| 三入口接入（旧定时 / 新批量含重试 / 详情手动模板发送），reserve→CAS SENDING→SEND/UNKNOWN/SAFE_RETRY | IMPLEMENTED | `InitialOutreachService.kt`、`ManualInitialOutreachService.kt`、`ManualExpertMailService.kt` | C1：`InitialOutreachServiceTest` 22 / `ManualInitialOutreachServiceTest` 185 / `ManualExpertMailServiceTest` 35，0 failures（含新增门禁连线、非 INTRODUCTION 原路径） |
| `ManualOutreachTxHelper` 成功原事务联动 SENT | IMPLEMENTED | `ManualOutreachTxHelper.kt` | C1：成功路径 verify 通过（新增 `outreachGuardTicket` 参数，SENT 与 mail_record 同事务） |
| 非 INTRODUCTION / 非 discovered 保持原路径 | IMPLEMENTED | 同上三入口 | C1 既有 200+/21/33 用例全部通过；新用例覆盖非 INTRODUCTION 不触发 guard |
| A14：`FlywayMigrationIntegrationTest` 156→157 + V157 断言 | IMPLEMENTED | `FlywayMigrationIntegrationTest.kt` | C2：全量断言由 156 改为 157（32 处），新增 V157 用例 |

## Commands（fresh，本次执行）

| Command | Result | Evidence |
|---|---|---|
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test` | PASS | exit 0；`ManualExpertMailServiceTest` 35/0/0；`ManualInitialOutreachServiceTest` 185/0/0；`InitialOutreachServiceTest` 22/0/0；`DiscoveryPersonOutreachGuardServiceTest` 11/0/0（合计 253，Failures 0，Errors 0）；JS 门禁 `tests 1677 / pass 1677 / fail 0` |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0；`FlywayMigrationIntegrationTest` 46/0/0；JS 门禁 `tests 1677 / pass 1677 / fail 0` |

Docker 前缀实跑形式：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`（OrbStack socket，未改动）。

## Changed Files（恰好 11 个授权文件）

- `src/main/resources/db/migration/V157__create_discovery_person_outreach.sql` — 新增：首发占位表，复合主键 `(person_id, mail_type)` + FK→`discovery_person`。
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt` — 新增：唯一写者；`reserve/reserveIntroduction`、`claimSending`、`markSent`、`markUnknown`、`markSafeRetry`、`stateOf`、`findReservation`；只读历史检查（全部成员：原联系人/邮箱别名/mail_record/mail_send_attempt/人工准入/抑制）。先锁人物行（`FOR UPDATE`）再锁占位行；RESERVED→SENDING→{SENT|UNKNOWN|SAFE_RETRY}。
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — 新批量入口：discovered INTRODUCTION 在建 contact 前 reserve，全门禁通过后 claimSending 才发 SMTP；非 SENT 依类别 → SAFE_RETRY/UNKNOWN；异常时按 `smtpAttempted` 区分 UNKNOWN/SAFE_RETRY；成功经 `recordSuccess` 联动 SENT。
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` — 旧定时入口：同上（reserve 在建 contact 前；claim 在 send 前；仅 discovered）。
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` — 详情手动模板发送：仅实际 `mailType=INTRODUCTION` 且当前画像 discovered 时 reserve+claim；未获许可抛 `IllegalStateException`（事务回滚，绝不 SMTP）；其余类型原样。
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt` — `recordSuccess` 新增可选 `outreachGuardTicket`，在同一成功事务内 `markSent`。
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt` — 新增：真库并发/状态机/历史保护验收（11 用例，mysqlIt）。
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — 新增门禁连线用例；新增人物级 helper 的 guard 形参；recordSuccess verify 补第 9 个 matcher。
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` — 新增门禁连线用例；recordSuccess verify 补第 9 个 matcher（4 处）。
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` — 新增 detail 门禁用例（INTRODUCTION 拒绝 fail-closed；非 INTRODUCTION 不触发 guard）。
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — 32 处 `156`→`157` + 新增 V157 用例。

## Deviations

- 计划称「31 处最新版本断言 156→157」；实测 `assertEquals("156", …targetSchemaVersion)` 为 **32 处**（含 V156 用例自身 1 处），已全部改为 157。行为等价、更完整。
- 设计细节：`recordSuccess` 新增第 9 个参数 `outreachGuardTicket`，导致既有 Mockito `verify` 的参数匹配数需同步补齐 —— 已在授权测试文件内补 `Mockito.nullable(OutreachGuardTicket::class.java)`（InitialOutreachServiceTest 4 处、ManualInitialOutreachServiceTest 2 处）。
- `DiscoveryPersonOutreachGuardService.reserveIntroduction` 定型为 3 参（内部以 `historyReference=null` 委托 `reserve`），`historyReference` 仅保留在底层 `reserve`。属实现内聚，不改变不变量。
- 本子计划未新增/修改任何前端与共享业务表 schema；`docs/plans/fast/**` 未纳入提交。

## Commits

- `657849948e400b054eb5b3c0c8d91c1e10260342` — `feat(fast-p): implement 09-send-guard`（11 文件，恰好授权清单；不含 `docs/plans/fast/**`）
- Post-execution code SHA: `657849948e400b054eb5b3c0c8d91c1e10260342`

## Freshness

- Plan identity rechecked: YES（执行前后同一 SHA-256）
- Worktree identity rechecked: YES
- Reported commits reachable from target branch: YES（见提交）
- Required commands run this invocation: YES（C1、C2 fresh）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`.
