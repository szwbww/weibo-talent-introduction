# Fast-P Ledger — master: docs/plans/2026-09-24/emailable-pre-send-verification.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-09-24/emailable-pre-send-verification.md (commit 99aa2ed7e9c93cfc5552f47889670fae96816f01)
- Amendments: N/A
- Master base: 7c7a9e747e471750ff776e37f7a6cb00f25e4d5c
- Branch: fast/2026-09-24-emailable-pre-send-verification
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-24-emailable-pre-send-verification
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-24T05:13:30Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- 授权依据：显式 `$fast-p docs/plans/2026-09-24/emailable-pre-send-verification.md` 调用（2026-09-24），授权本 master plan 的一次本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase/squash/amend/reset/worktree 删除。
- `MASTER_BASE_SHA` = `7c7a9e747e471750ff776e37f7a6cb00f25e4d5c` = `main` HEAD，与 `emailable-evidence.md` 声明的审计基线一致；`fast/2026-09-24-emailable-pre-send-verification` 在该 SHA 上创建于专用 worktree。
- 计划播种：`99aa2ed7e9c93cfc5552f47889670fae96816f01`（`docs(plans): seed emailable pre-send verification master and child plans`），五份计划文件字节与主工作区规划产物一致（sha256 见下），播种不是 amendment。
- 计划 sha256：master `5d68d9c6a9d2b0f93a4fe230ea8fb2d096d75e9dc87ee5bc5d9082b9c16b5ad8`；01 `868b1e18377516ee9de9742542d723e199fa8cc7ea77c94d65dd666449172f42`；02 `c14acac5ce8c0974b7e7c181defacf282a69ef53783c3db38fbf5d45db9c7549`；03 `e78e15a25a6128d4243ca07fe6235a7682915c08e51ac66f908620aba8dbb61f`；evidence `2d61baa1c8fdce457eb2673b800ef85ff4ea1e229af3b10136ec5f0a8bafa445`。
- `master_base..c1 Base` 只有四个 plan-only 提交（`99aa2ed`、`9e25c1d`、`512e226`、`f64e9f6`），产品代码等同 `main @ 7c7a9e7`。
- 主工作区（`~/IdeaProjects/weibo-talent-introduction`）存在与本 run 无关的未提交改动（`index.html`/`styles.css` 缓存键 `20260924-snippet-dialog-contrast`、`docs/knowledge/**` 规划期修订、`docs/releases.json`、若干脚本）；本 run 不纳入、不覆盖、不提交它们。因此本 worktree 的前端缓存键基线为 `20260924-account-editor`（11 处），由 c3 按计划 T4「实施前复核」改为 `20260924-emailable-verification`。
- 环境：JDK zulu-11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）；Docker 29.4.0（OrbStack，testcontainers MySQL 8.0.36，需 `-Dapi.version=1.40`）；node v25.7.0。
- 迁移：本 run 新增 `V138__create_batch_email_verification.sql`（c1）与 `V139__add_batch_email_verification_enabled.sql`（c2），最高版本 139；未修改 V1..V137。
- 无全系统验证；每个 child 只过四道轻量门禁。人工验收 A-1..A-5（主计划）与各子计划 A-n 未执行。

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
| c2 | docs/plans/2026-09-24/emailable-02-task-config.md | commit:99aa2ed7e9c93cfc5552f47889670fae96816f01 | c1 | 1 | LIGHT_PASS | 0965a037d94e198f6ce8b13900149a09d35683b4 | 1968f01d0d07afda1f0d2de571cf3dc3bf72e776 | 0 | — | 1968f01d0d07afda1f0d2de571cf3dc3bf72e776 | 29427f8e6d07bfaeaa2d6d4a68311ca1ee0dd6eb | 实现者 C2Impl；验证者 C2Verify 返回 LIGHT_PASS/COMPLETE_CHILD（四门全过、无 AUTO_FIX）；定向 152 tests 0 fail（基线 135）、migration IT 30 tests 0 fail（V139，基线 19 红全清）；报告另记 O-1..O-3 三条 RECORD_ONLY（400/422 混合为规格内、报告行号引用漂移、拒绝文案双份字面量） |
| c3 | docs/plans/2026-09-24/emailable-03-console-logs.md | commit:99aa2ed7e9c93cfc5552f47889670fae96816f01 | c1,c2 | 1 | LIGHT_PASS_WITH_NOTES | 1968f01d0d07afda1f0d2de571cf3dc3bf72e776 | b74ef51a48974924feb3f1c649b22af1dd772b43 | 0 | — | b74ef51a48974924feb3f1c649b22af1dd772b43 | 04c42a65a1271b1e23b91a94e7c6b237f81e25e1 | 实现者 C3Impl；验证者 C3Verify 返回 LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD（四门全过、无 AUTO_FIX）；Kotlin 38 tests 0 fail（基线 25），JS 1178 pass（基线 1152），11 处缓存键统一为 `20260924-email-verification`；O-1 S-2 CSS 插在 `task-center-contract:start` 标记前（该标记块被未授权测试钉为末尾，移到 EOF 非唯一授权修法）、O-2 标签失败文案嵌套括号、O-3 原因码标签为硬编码映射（未识别码原样展示，计划允许） |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

## Agent Availability Events

| Child | Role | Attempt | Error | Timestamp | Code head | Action |
|---|---|---:|---|---|---|---|
| — | — | — | — | — | — | — |

（无 agent 派发失败：C1Impl、C1Verify、C2Impl、C2Verify、C3Impl、C3Verify 均一次派发成功，agent_attempt 始终为 0。）

## Verification Log

每个 child 的完整轻量验证报告在 `children/<id>/verify-log.md`；本 run 无自动修复轮次（三个 child 的 fix_round 均为 0，`children/<id>/fix-log.md` 记录 epoch 1 无轮次）。终态：

- c1：`LIGHT_PASS_WITH_NOTES`，Required Action `COMPLETE_CHILD`，boundary `f64e9f6..0965a03`，验证者 C1Verify；命令：定向 178 tests/0 fail（25+131+22）、IT 37 tests/0 fail（Flyway 29 + IT 8）、JS 1152 pass。
- c2：`LIGHT_PASS`，Required Action `COMPLETE_CHILD`，boundary `fa4db33..1968f01`，验证者 C2Verify；命令：定向 152 tests/0 fail（85+40+22+5）、migration IT 30 tests/0 fail、JS 1152 pass。
- c3：`LIGHT_PASS_WITH_NOTES`，Required Action `COMPLETE_CHILD`，boundary `1968f01..b74ef51`，验证者 C3Verify；命令：Kotlin 38 tests/0 fail（8+30）、JS 1178 pass（基线 1152 + 26 新增）、`node --check app.js` 通过。

RECORD_ONLY 汇总（无 AUTO_FIX、无 PAUSE）：

| ID | Child | 摘要 | 来源报告 |
|---|---|---|---|
| O-1 | c1 | I-2 地址变更终止分支已实现（`ManualInitialOutreachService.kt:69,843-852`）但缺引擎级测试 | `children/c1/verify-log.md` |
| O-2 | c1 | 两处验证门控取消检查（`:687-694`、`:859-866`）缺 `emailVerificationEnabled=true` 的引擎级测试 | `children/c1/verify-log.md` |
| O-3 | c1 | 三个附加受控码为加法式扩展；抛异常型 SMTP 未知结果保持 SENDING 且不停止本次执行（计划文本此处有歧义） | `children/c1/verify-log.md` |
| O-1 | c2 | `validateSnapshotFields` 新守卫返回 400 而同方法其它校验保持 422（计划/验收要求 400） | `children/c2/verify-log.md` |
| O-2 | c2 | 执行报告引用的入口行号实为测试文件行号（引用漂移，行为已核实） | `children/c2/verify-log.md` |
| O-3 | c2 | 拒绝文案在 service `:369` 与 control `:421` 为两份字面量，未来改文案需同时改 | `children/c2/verify-log.md` |
| O-1 | c3 | S-2 CSS 位于 `task-center-contract:start` 标记块之前而非文件末尾（被未授权测试的 EOF 断言所迫，块逐字一致） | `children/c3/verify-log.md` |
| O-2 | c3 | 标签失败单元格文案嵌套括号（外观问题） | `children/c3/verify-log.md` |
| O-3 | c3 | 原因码中文标签为 `app.js` 内硬编码映射，未识别码原样安全展示 | `children/c3/verify-log.md` |
