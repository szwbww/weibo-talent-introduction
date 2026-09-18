# Fast-P Ledger — master: docs/plans/2026-09-17/00-material-request-master.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-17/00-material-request-master.md (commit 44136f5dc7bbf94f429afa72c55242c5137d7f4f)
- Amendments: A1, A2
- Master base: 7f7b3a821f09d4255dc735c1e7b96eacf9c32164
- Branch: fast/material-request
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-18T00:20:00+08:00
- Current child: 03-ui
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- `Master base` `7f7b3a821f09d4255dc735c1e7b96eacf9c32164` is the `main` HEAD the branch was created from. The master and child plans were seeded on the branch by docs-only commit `44136f5dc7bbf94f429afa72c55242c5137d7f4f`; that commit is the recorded plan identity for all three children.
- Baseline at the seed commit, in the worktree: `node --test src/test/js/*.test.js` -> exit 0 (`tests 990`, `pass 990`, `fail 0`); `git diff --check` -> exit 0.
- Baseline Java: `mvn test -Dtest=ExpertMaterialRequestServiceTest,ExpertContactManagementControllerTest` -> recorded in the seed-commit baseline of this run (no matching test classes existed before child 02).
- Environment: JDK 11 (zulu-11) required for every Maven command; Node v25.7.0 for `node --test`; Docker 29.4.0 available for the `-DmigrationIt=true` testcontainers group (`FlywayMigrationIntegrationTest` starts its own MySQL container, no external 127.0.0.1:3306 required).
- The `main` checkout carries uncommitted calendar-layout work (app.js/styles.css/index.html/`meetingCalendar.test.js` plus knowledge docs). This run is isolated in its own worktree created from `7f7b3a8`, so that work is neither consumed nor disturbed. Child plans re-read the live cache key from `index.html` rather than assuming the value recorded when the plans were written; the calendar-layout diff lies entirely after `app.js:18279` and `styles.css:11198`, so all plan baselines (`app.js:8001-8130`, `styles.css:10406-10467`, marker `/* meeting-mail-07: outbound files */`) hold at `7f7b3a8`.
- Child order and dependencies: `01-cache-fixtures` none; `02-status-api` none (the master plan states 02 may be developed independently of 01); `03-ui` depends on `01-cache-fixtures,02-status-api`.

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01-cache-fixtures | docs/plans/2026-09-17/01-material-request-cache-fixtures.md | commit:44136f5dc7bbf94f429afa72c55242c5137d7f4f | none | 1 | LIGHT_PASS_WITH_NOTES | 44136f5dc7bbf94f429afa72c55242c5137d7f4f | 535f76f3bcbee243ae5bef09686bbe57921075e7 | 0 | — | 535f76f3bcbee243ae5bef09686bbe57921075e7 | 236872237950c53da0081e611dd4ad26d87bd2d2 | Implementer Implementer01; verifier LightVerifier01 gates 1-4 PASS; RECORD_ONLY O-1 four fixtures re-extract keys with a `[0-9a-z-]+` charset (constraint on child 03's bump value), O-2 plan prose/A-1 name the stale key `20260917-calendar-layout-align` while the live key is `20260917-meeting-mail-global-world-clock` (human gate). |
| 02-status-api | docs/plans/2026-09-17/02-material-request-status-api.md | commit:3fcccc8341fe3965c20a4eaead1ad99965228e51 | none | 3 | LIGHT_PASS_WITH_NOTES | 535f76f3bcbee243ae5bef09686bbe57921075e7 | 71e587477e0b2defb9775042ae8e9cee03365321 | 1 | 4a91a6135c031cc60fd8091b1fe8ae61f77c4a2b | 4a91a6135c031cc60fd8091b1fe8ae61f77c4a2b | — | Epoch 1 commit 5aaa801 returned PLAN_CONFLICT (fixing the pin was unauthorized); A1 widened the child to 7 files, epoch 2 commit 71e5874 applied the line-number-only pin correction, epoch 3 repair round 1 (4a91a61) replaced the child's own CHECK_CLAUSE text loops with the behavioural probes after human arbitration. Verifier LightVerifier02 returned PAUSE on gate 3; re-verifier LightVerifier02b gates 1-4 PASS. RECORD_ONLY O-1 pre-existing V124 fk_eap_contact red (reproduces at base, out of scope by human decision), O-2 the Docker group starts only with -Dapi.version=1.44 on the Maven command line, O-3 stale comment at OperatorStatusWriteSeamGuardTest.kt:67-68, O-4 requestText re-spelled in test assertions, O-5 "no Ambiguous mapping" proven statically plus the epoch-1 live boot. |
| 03-ui | docs/plans/2026-09-17/03-material-request-ui.md | commit:4f4eaf7d17ce48e1253e54384e6de2776874c0cb | 01-cache-fixtures,02-status-api | 2 | LIGHT_VERIFYING | 4a91a6135c031cc60fd8091b1fe8ae61f77c4a2b | 75628fb9ae6201e7aa2c26f3dcb880dde8faa9ab | 0 | — | 75628fb9ae6201e7aa2c26f3dcb880dde8faa9ab | — | Epoch 1 commit ee9cc36 implemented the six authorized files (full suite 1001/1003, only the two out-of-list toolbar enumerations red) and returned PLAN_CONFLICT; A2 widened the child to 8 files; epoch 2 commit 75628fb updates only those two assertions, full JS suite now 1003/1003. Verifier LightVerifier03 running. |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-17/02-material-request-status-api.md | commit:44136f5dc7bbf94f429afa72c55242c5137d7f4f | commit:3fcccc8341fe3965c20a4eaead1ad99965228e51 | master 变更文件清单「每份保持 ≤10 文件」+ 02 的 I-4 新独立路由（ExpertContactManagementController） | 新增 GET/PUT 及其 import 共 14 行使 OperatorStatusWriteSeamGuardTest 钉死的 ExpertContactManagementController.kt:564 位移到 578，该守卫未在 02 的 6 文件授权内；按 K-line-number-guard-breaks-on-any-insertion 仅改行号即可完成 I-4，扩权是该改动唯一在计划内的路径 | HUMAN:2026-09-18T09:12+08:00 ask 抉择「Approve: widen child 02 to 7 files, line-number-only pin update 564→578」 |
| A2 | docs/plans/2026-09-17/03-material-request-ui.md | commit:44136f5dc7bbf94f429afa72c55242c5137d7f4f | commit:4f4eaf7d17ce48e1253e54384e6de2776874c0cb | master 变更文件清单「每份保持 ≤10 文件」+ 03 的 S-2 工具栏契约（入口位于会议按钮与跟进按钮之间） | S-2 的新入口使两份未授权测试的工具栏封闭列表断言必红（mailboxOutboundAttachments.test.js:1559、meetingConfirmationIntegration.test.js:1425），而二者都在 03 的定向命令集内；只改断言即可满足 S-2，扩权是该命令转绿的唯一在计划内路径 | HUMAN:2026-09-18T09:40+08:00 ask 抉择「Approve A2: authorize the two toolbar-enumerating tests」 |
