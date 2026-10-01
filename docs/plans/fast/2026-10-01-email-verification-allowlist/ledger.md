# Fast-P Ledger — master: docs/plans/2026-10-01/email-verification-allowlist.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-10-01/email-verification-allowlist.md (commit 143d9caccaef16e848927dd03923f3172f1b74a1)
- Amendments: N/A
- Master base: 2b036ccce7e9956f6ea27420d8bc9e057a011da2
- Branch: fast/2026-10-01-email-verification-allowlist
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-10-01T13:08:05Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| c1 | docs/plans/2026-10-01/email-verification-allowlist-backend.md | commit:143d9caccaef16e848927dd03923f3172f1b74a1 | none | 1 | LIGHT_PASS | 26a81bfa0467aaa6ea07613730a326759b9337f4 | e7441004aa6dd68fb3f8e486537d3e520f71e0fd | 0 | — | e7441004aa6dd68fb3f8e486537d3e520f71e0fd | 692262514628e65d4da3da97be54e73e16cec865 | 10 授权文件；实现者 C1Impl；验证者 C1Verify 四门全过 LIGHT_PASS/COMPLETE_CHILD（264/0 与 17/0，基线 247/14，+17/+3 为新增用例）；无 AUTO_FIX；O-1 见 verify-log |
| c2 | docs/plans/2026-10-01/email-verification-allowlist-config.md | commit:143d9caccaef16e848927dd03923f3172f1b74a1 | c1 | 1 | LIGHT_PASS | e7441004aa6dd68fb3f8e486537d3e520f71e0fd | ac37fcd9fc570897ec42b3ce7745a9a7104cebe7 | 0 | — | ac37fcd9fc570897ec42b3ce7745a9a7104cebe7 | c67e5703b87904cf18f4d7b3a057881240b17f3d | 7 授权文件；V145；实现者 C2Impl；验证者 C2Verify 四门全过 LIGHT_PASS/COMPLETE_CHILD（targeted 156/0；迁移 IT 34/0，基线 33/24 红转全绿）；无 AUTO_FIX/RECORD_ONLY；verify-log 初稿经相对路径误落主工作区，controller 原样移入并清理主工作区 |
| c3 | docs/plans/2026-10-01/email-verification-allowlist-frontend.md | commit:143d9caccaef16e848927dd03923f3172f1b74a1 | c2 | 1 | LIGHT_PASS | ac37fcd9fc570897ec42b3ce7745a9a7104cebe7 | e6c36e294cb26ce36684a96a0908c305f871e4b4 | 0 | — | e6c36e294cb26ce36684a96a0908c305f871e4b4 | 22f07989565da43508f210f34ca0c128c730fca5 | 9 授权文件（8 改动，batchManualExecutionLog.test.js 计划允许无需改动）；缓存键 11 处统一 20261001-email-verification-allowlist；实现者 C3Impl；验证者 C3Verify 四门全过 LIGHT_PASS/COMPLETE_CHILD（JS 1273/0；全量 mvn test 4483/0/0/13）；无 AUTO_FIX；O-1 见 verify-log |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

## Agent Availability Events
| Child | Role | Attempt | Error | Timestamp | Code head | Action |
|---|---|---:|---|---|---|---|
| — | — | — | — | — | — | — |

## Baseline
- 授权依据：显式 `$fast-p docs/plans/2026-10-01/email-verification-allowlist-backend.md` 调用（2026-10-01），经用户确认执行范围为全部 3 个子计划（master = 总览 `docs/plans/2026-10-01/email-verification-allowlist.md`，顺序 backend → config → frontend）；授权一个本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase/squash/amend/reset/worktree 删除。
- `MASTER_BASE_SHA` = `2b036ccce7e9956f6ea27420d8bc9e057a011da2` = `main` HEAD；专用 worktree 于该 SHA 创建；本 run 三个 child 的目标文件在主工作区均无未提交改动（源码基线一致）。
- 计划播种：`143d9caccaef16e848927dd03923f3172f1b74a1`（`docs(plans): seed 2026-10-01 email-verification-allowlist master and child plans`）；5 份计划文件由主工作区逐字复制，播种前后 sha256 一致。
- 计划 sha256：master `49d61d1ebb0babee9517010c245827d406916bf2040a2558c0717ccbc2a94b73`；backend `2b09828c4d45280d9b4870caac4615c5770c608d5678548f494b3f91744a4f6d`；config `074018c394a1394d8de7ab9ec4134a88df8ff44a03d07cae5398aca829213512`；frontend `57c4bf507baf1a8471e543f4d8259dbf6ef03c6a0e28af0e1feab29e9e0be5d6`；evidence `5c87de0087c6ad004565a99e4ce9ab79e538c20a96ddb2e1eb1d76352652b43d`。
- 主工作区（`~/IdeaProjects/weibo-talent-introduction`）存在与本 run 无关的未提交改动（`CLAUDE.md`、`docs/knowledge/**` 规划期修订、`docs/releases.json`、`tools/contactout-visible-export/**` 等）；本 run 不纳入、不覆盖、不提交它们。
- 环境：JDK zulu-11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）；node v25.7.0；Docker OrbStack 29.4.0（API 1.54），经 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40` 使用（testcontainers 默认 API 1.32 会被 OrbStack 拒绝）。
- 全量 `mvn test` 为 run 级收尾命令（master 计划「最后执行项目 mvn test」），于 c3 终态执行并由 C3Verify 独立复跑：4483 tests / 0 failures / 0 errors / 13 skipped（基线 4448/0/0/13），exec-plugin node 1273/0。
- 无全系统验证结论；每个 child 只过四道轻量门禁。

## Baseline Commands
| Command | Exit | Result |
|---|---|---|
| `mvn -B -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,TaskRetentionMigrationTest test`（JAVA_HOME=zulu-11） | 0 | 247 tests / 0 failures / 0 errors / 0 skipped（33+165+43+6），2:10；`baseline/mvn-targeted.txt` |
| `mvn -B test`（JAVA_HOME=zulu-11） | 0 | surefire 4448 tests / 0 failures / 0 errors / 13 skipped，4:29（含 exec-plugin node-test）；`baseline/mvn-full.txt` |
| `node --check src/main/resources/static/app.js` | 0 | 语法通过；`baseline/js-check.txt` |
| `node --test src/test/js/*.test.js` | 0 | 1265 pass / 249 suites / 0 fail；`baseline/js-full.txt` |
| `DOCKER_HOST=… mvn -B -DmysqlIt=true -Dapi.version=1.40 -Dtest=BatchEmailVerificationRepositoryIT test` | 0 | 14 tests / 0 failures（Testcontainers MySQL，OrbStack 29.4.0）；`baseline/mvn-mysqlit.txt` |
| `DOCKER_HOST=… mvn -B -DmigrationIt=true -Dapi.version=1.40 -Dtest=FlywayMigrationIntegrationTest test` | 1 | 33 tests / 24 failures，全部为既有 latest-version 断言 `expected: <142> but was: <144>`（c2 修复为 145 后全绿）；`baseline/mvn-migrationit.txt` |

## Verification Log
- c1：`LIGHT_PASS`，Required Action `COMPLETE_CHILD`，boundary `2452f4a..e744100`，验证者 C1Verify（epoch 1/attempt 1）；必需命令：targeted exit 0 / 264 tests / 0 fail（基线 247/0），MySQL IT exit 0 / 17 tests / 0 fail（基线 14/0）；O-1 见 `children/c1/verify-log.md`。
- c2：`LIGHT_PASS`，Required Action `COMPLETE_CHILD`，boundary `6922625..ac37fcd`，验证者 C2Verify（epoch 1/attempt 1）；必需命令：targeted exit 0 / 156 tests / 0 fail（105+51），迁移 IT exit 0 / 34 tests / 0 fail（基线 33/24 → 0）；无 AUTO_FIX/RECORD_ONLY。
- c3：`LIGHT_PASS`，Required Action `COMPLETE_CHILD`，boundary `c67e570..e6c36e2`，验证者 C3Verify（epoch 1/attempt 1）；必需命令：`node --check` exit 0；focused JS 41/41；全量 JS exit 0 / 1273 pass / 250 suites（基线 1265/249）；全量 `mvn -B test` exit 0 / 4483 / 0 / 0 / 13 skipped；O-1（边界外陈旧 surefire 报告）见 `children/c3/verify-log.md`。
