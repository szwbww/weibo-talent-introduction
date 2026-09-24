# Fast-P Ledger — master: docs/plans/2026-09-24/emailable-pre-send-verification.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-24/emailable-pre-send-verification.md (commit 99aa2ed7e9c93cfc5552f47889670fae96816f01)
- Amendments: N/A
- Master base: 7c7a9e747e471750ff776e37f7a6cb00f25e4d5c
- Branch: fast/2026-09-24-emailable-pre-send-verification
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-24T05:13:30Z
- Current child: c2
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- 授权依据：显式 `$fast-p docs/plans/2026-09-24/emailable-pre-send-verification.md` 调用（2026-09-24），授权本 master plan 的一次本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase/squash/amend/reset/worktree 删除。
- `MASTER_BASE_SHA` = `7c7a9e747e471750ff776e37f7a6cb00f25e4d5c` = `main` HEAD，与 `emailable-evidence.md` 声明的审计基线一致；`fast/2026-09-24-emailable-pre-send-verification` 在该 SHA 上创建于专用 worktree。
- 计划播种：`99aa2ed7e9c93cfc5552f47889670fae96816f01`（`docs(plans): seed emailable pre-send verification master and child plans`），五份计划文件字节与主工作区规划产物一致（sha256 见下），播种不是 amendment。
- 计划 sha256：master `5d68d9c6a9d2b0f93a4fe230ea8fb2d096d75e9dc87ee5bc5d9082b9c16b5ad8`；01 `868b1e18377516ee9de9742542d723e199fa8cc7ea77c94d65dd666449172f42`；02 `c14acac5ce8c0974b7e7c181defacf282a69ef53783c3db38fbf5d45db9c7549`；03 `e78e15a25a6128d4243ca07fe6235a7682915c08e51ac66f908620aba8dbb61f`；evidence `2d61baa1c8fdce457eb2673b800ef85ff4ea1e229af3b10136ec5f0a8bafa445`。
- 主工作区（`~/IdeaProjects/weibo-talent-introduction`）存在与本 run 无关的未提交改动（`index.html`/`styles.css` 缓存键 `20260924-snippet-dialog-contrast`、`docs/knowledge/**` 规划期修订、`docs/releases.json`、若干脚本）；本 run 不纳入、不覆盖、不提交它们。因此本 worktree 的前端缓存键基线为 `20260924-account-editor`（11 处），由 c3 按计划 T4「实施前复核」自行 bump。
- 环境：JDK zulu-11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）；Docker 29.4.0 可用（testcontainers MySQL 8.0.36，用于 `-DmysqlIt=true` / `-DmigrationIt=true`）；node v25.7.0。
- 迁移基线：`src/main/resources/db/migration` 最高为 `V137__task_execution_interruption_recovery.sql`；V138（c1）与 V139（c2）在实施前必须重新确认空缺。
- 已知预置红：`FlywayMigrationIntegrationTest` 的“迁到最新”断言仍写 `136`（约 20 处），而仓库实际最高迁移为 V137；c1 负责把这些 latest 断言改到 138，显式历史 target 断言保持原值。
- 无全系统验证；每个 child 只过四道轻量门禁。

## Baseline Commands

| Command | Exit | Result |
|---|---|---|
| `node --test src/test/js/*.test.js` | 0 | 1152 tests / 230 suites / 0 fail（`baseline/js.txt`） |
| `node --check src/main/resources/static/app.js` | 0 | 语法通过（`baseline/js.txt`） |
| `mvn -B -Dtest=ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,BatchSendSchedulerTest,BatchSendConfigControllerTest,BatchSendExecutionDetailTest test` | 0 | 279 tests / 0 failures / 0 errors（119+22+74+34+5+8+17；`baseline/mvn-targeted.txt`） |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -B -Dtest=OpenAlexBudgetRepositoryIT,DiscoveryPaperQueueRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | 43 tests / 0 failures（12+31；`baseline/mvn-mysqlit.txt`） |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | 1 | 28 tests / 19 failures，全部 `expected: <136> but was: <137>`（预置红：latest 断言钉 136，仓库最高 V137；`baseline/mvn-migrationit.txt`） |

Docker 环境事实：testcontainers 默认 client API 1.32 被 OrbStack 拒绝（`Minimum supported API version is 1.40`），因此所有 `-DmysqlIt=true` / `-DmigrationIt=true` 命令必须带 `-Dapi.version=1.40`（并可显式设 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`）。不带该参数时 IT 类在容器启动阶段直接 error，不是产品失败。

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| c1 | docs/plans/2026-09-24/emailable-01-runtime-audit.md | commit:99aa2ed7e9c93cfc5552f47889670fae96816f01 | none | 1 | LIGHT_PASS_WITH_NOTES | f64e9f6e9a1e5220d9267d9cde97127ae456df74 | 0965a037d94e198f6ce8b13900149a09d35683b4 | 0 | — | 0965a037d94e198f6ce8b13900149a09d35683b4 | 55afb93d32b23ebfd34dbc12e0268ba2c702393a | 实现者 C1Impl；验证者 C1Verify 返回 LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD（四门全过、无 AUTO_FIX）；定向 178 tests 0 fail、IT 37 tests 0 fail（V138，基线 19 红全清）；O-1 地址变更终止分支缺引擎级测试、O-2 验证门控取消分支缺引擎级测试、O-3 三个附加受控码与 SMTP 未知结果不停止的读法；附加码 EMAIL_CHANGED / EMAIL_VERIFY_AUDIT_FAILED / EMAIL_VERIFY_SEND_STATE_CONFLICT |
| c2 | docs/plans/2026-09-24/emailable-02-task-config.md | commit:99aa2ed7e9c93cfc5552f47889670fae96816f01 | c1 | 1 | LIGHT_PASS | fa4db333fc89f6cb22b67e9e251e5d95148b8942 | 1968f01d0d07afda1f0d2de571cf3dc3bf72e776 | 0 | — | 1968f01d0d07afda1f0d2de571cf3dc3bf72e776 | PENDING_EVIDENCE | 实现者 C2Impl；验证者 C2Verify 返回 LIGHT_PASS/COMPLETE_CHILD（四门全过、无 AUTO_FIX）；定向 152 tests 0 fail（基线 135）、migration IT 30 tests 0 fail（V139，基线 19 红全清）；报告另记 O-1..O-3 三条 RECORD_ONLY（400/422 混合为规格内、报告行号引用漂移、拒绝文案双份字面量） |
| c3 | docs/plans/2026-09-24/emailable-03-console-logs.md | commit:99aa2ed7e9c93cfc5552f47889670fae96816f01 | c1,c2 | 1 | PENDING | — | — | 0 | — | — | — | 两处开关与逐邮箱分页明细；7 文件 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

## Agent Availability Events

| Child | Role | Attempt | Error | Timestamp | Code head | Action |
|---|---|---:|---|---|---|---|
| — | — | — | — | — | — | — |

## Verification Log

（pending）
