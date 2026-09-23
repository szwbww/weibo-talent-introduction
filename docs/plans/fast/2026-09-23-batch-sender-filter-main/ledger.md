# Fast-P Ledger — master: docs/plans/2026-09-23/00-batch-sender-filter-main.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-23/00-batch-sender-filter-main.md (commit a58ce98be8bb828899dd69c7b8a0282cce35eeb1)
- Amendments: A1, A2, A3, A4, A5, A6
- Master base: 9237d6f573335d1624217cbc5501f68a6f52b97b
- Branch: fast/2026-09-23-batch-sender-filter-main
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-batch-sender-filter-main
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-23T10:33:58Z
- Current child: c2
- Waiting role: VERIFIER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- 授权依据：显式 `$fast-p docs/plans/2026-09-23/00-batch-sender-filter-main.md` 调用（2026-09-23），授权本 master plan 的一次本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase/squash/amend/reset/worktree 删除。
- 首次调用在 `main @ 9237d6f` 上返回 `BLOCKED_PREFLIGHT`：MAIN `G-0`/`M-5` 要求共享收件箱 01（V134）先实施并验证，而仓库最高迁移仍是 V133、共享收件箱 run（`fast/2026-09-23-shared-inbox-master`）的 c1 仍 PENDING。记录见 `docs/plans/fast/2026-09-23-batch-sender-filter-main/preflight-blocked.md`。
- 人工解除：用户批准「批准改写计划后立即开跑本组」——放弃 V134 先行门槛，本组迁移改用当时下一个空号 V134，前端 02 不再依赖共享收件箱 owner 基线，共享收件箱计划恢复时自行顺延（其计划已有「版本冲突先修订文件名和本文，不能抢号」条款）。该批准即 A1–A3 三行的 `Approval`。
- 计划播种：`c9babae738f0e155301805fc15316a8e9f3d20f9`（`docs(plans): seed batch-sender-filter master and child plans`），三份计划字节与首次 preflight 记录的 sha256 一致；播种不是 amendment。
- 计划改写：`377a38b91ffd8a0a78815f5ad3041dbc55b2db80`（`docs(plans): amend batch-sender-filter migration order and shared-file gates`），只改 `docs/plans/2026-09-23/` 下三份计划。
- `MASTER_BASE_SHA` = `9237d6f573335d1624217cbc5501f68a6f52b97b` = `main` HEAD；`fast/2026-09-23-batch-sender-filter-main` 在该 SHA 上创建于专用 worktree。`master_base..child1 Base` 只有两个 plan-only 提交（`c9babae`、`377a38b`），产品代码等同 `main @ 9237d6f`。
- 环境：JDK zulu-11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）；Flyway IT 需 Docker（OrbStack Server 29.4.0，`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`）。
- 无全系统验证；每个 child 只过四道轻量门禁。
- 已知外部冲突（已由 A4–A6 仲裁）：本组首次改写曾把迁移号定为 V134；随后发现并行 run `fast/2026-09-23-shared-inbox-master` 已实现并轻量验证 `V134__shared_inbox_owner.sql`（其 c1 LIGHT_PASS、c2 LIGHT_PASS）。Flyway 版本是共享命名空间，同库两个 V134 启动即失败，人工批准本组让号到 V135（保持 V134→V135 顺序）。两分支合入时 `index.html`、`app.js`、`FlywayMigrationIntegrationTest.kt` 三个共享文件需按「方法区分开改」人工解冲突。

## Baseline Commands

| Command | Exit | Result |
|---|---|---|
| `node --check src/main/resources/static/app.js` | 0 | 见 `baseline/js.txt` |
| `node --test src/test/js/*.test.js` | 0 | 1121 pass / 0 fail（见 `baseline/js.txt`） |
| `JAVA_HOME=<zulu-11> mvn -B -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,ManualInitialOutreachServiceTest test` | 0 | 208 tests / 0 failures / 0 errors（见 `baseline/mvn.txt`） |
| `DOCKER_HOST=<orbstack> JAVA_HOME=<zulu-11> mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | 1 | 26 tests / 17 failures，全部为 `expected: <131> but was: <133>`；预置红（断言钉旧最高版本 131，仓库实际最高为 V133），本 child 计划要求按实值改到 134（见 `baseline/mvn.txt`） |

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| c1 | docs/plans/2026-09-23/01-batch-sender-filter-backend.md | commit:a58ce98be8bb828899dd69c7b8a0282cce35eeb1 | none | 1 | LIGHT_PASS_WITH_NOTES | 377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | c3f694f | 1 | 248c30a | 248c30a | 47505d3f38d91570d639222d652f3cf1a145e5ba | 实现者 C1Backend（c3f694f，修复 248c30a）；验证者 C1Verifier LIGHT_FAIL/AUTO_FIX（F-1：提交内 Flyway 断言仍为 134）→ C1ReVerifier LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD（F-1 关闭）；O-1..O-4 RECORD_ONLY |
| c2 | docs/plans/2026-09-23/02-batch-sender-filter-frontend.md | commit:a58ce98be8bb828899dd69c7b8a0282cce35eeb1 | c1 | 1 | LIGHT_PASS_WITH_NOTES | 248c30a | 75cc1714 | 0 | — | 75cc1714 | PLACEHOLDER_E2 | 实现者 C2Frontend（75cc1714）；验证者 C2Verifier LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD，四门全过；RECORD_ONLY：manualDraft.emailDomains 既有误键表达式保持原样、前端去重依赖构造顺序+后端 distinct()、证据提交落在字面边界内 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-23/00-batch-sender-filter-main.md | commit:c9babae738f0e155301805fc15316a8e9f3d20f9 | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | M-5 / G-0 | 共享收件箱 01 未实施（V134 不存在），原门槛使本组无法开跑；按 M-5 自身「若实际版本或文件基线变了，先改计划」条款把本组迁移号定为下一个空号 V134，并取消共享收件箱基线依赖 | HUMAN:2026-09-23 批准改写计划后立即开跑本组 |
| A2 | docs/plans/2026-09-23/01-batch-sender-filter-backend.md | commit:c9babae738f0e155301805fc15316a8e9f3d20f9 | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | M-5 / G-1 | 同上：后端子计划迁移号 V135 改 V134，Flyway 最新版本断言目标 135 改 134，A-5 前置条件不再依赖共享收件箱 01 配置 UI | HUMAN:2026-09-23 批准改写计划后立即开跑本组 |
| A3 | docs/plans/2026-09-23/02-batch-sender-filter-frontend.md | commit:c9babae738f0e155301805fc15316a8e9f3d20f9 | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | M-5 / G-2 | 同上：前端子计划不再以共享收件箱 01 的 owner UI 为基线，改以当前 `index.html`/`app.js` 为基线 | HUMAN:2026-09-23 批准改写计划后立即开跑本组 |
| A4 | docs/plans/2026-09-23/00-batch-sender-filter-main.md | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | commit:a58ce98be8bb828899dd69c7b8a0282cce35eeb1 | M-5 / G-0 | 并行 run 已实现并轻量验证 `V134__shared_inbox_owner.sql`，本组原 V134 与其重号（同库两个 V134 会使 Flyway 启动即失败）；人工批准本组让号到 V135，恢复 V134→V135 顺序且不 rebase 到并行分支 | HUMAN:2026-09-23 选择「本组改用 V135（推荐）」 |
| A5 | docs/plans/2026-09-23/01-batch-sender-filter-backend.md | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | commit:a58ce98be8bb828899dd69c7b8a0282cce35eeb1 | M-5 / G-1 | 同上：后端子计划迁移号 V134 改 V135，Flyway 最新版本断言目标 134 改 135（本分支允许 V134 缺口） | HUMAN:2026-09-23 选择「本组改用 V135（推荐）」 |
| A6 | docs/plans/2026-09-23/02-batch-sender-filter-frontend.md | commit:377a38b91ffd8a0a78815f5ad3041dbc55b2db80 | commit:a58ce98be8bb828899dd69c7b8a0282cce35eeb1 | M-5 / G-2 | 同上：前端子计划改为「不 rebase 到并行共享收件箱分支，仍以当前 `index.html`/`app.js` 为基线，合入时按方法区分开改人工解冲突」 | HUMAN:2026-09-23 选择「本组改用 V135（推荐）」 |

## Agent Availability Events

| Child | Role | Attempt | Error | Timestamp | Code head | Action |
|---|---|---:|---|---|---|---|
| — | — | — | — | — | — | — |

## Verification Log

（每个 child 的完整轻量验证报告追加在 `children/<id>/verify-log.md`。）
