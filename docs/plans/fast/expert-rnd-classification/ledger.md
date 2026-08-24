# Fast-P Ledger — master: docs/plans/2026-08-24/00-expert-rnd-classification-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-24/00-expert-rnd-classification-master.md (commit 3a4162c9c458f899470f59ac6e1a07b9ba748b3a)
- Amendments: A1,A2,A3
- Master base: c004a18d675b86040597f17f5911aa52f718d156
- Branch: fast/expert-rnd-classification
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-24T15:43:44+0800
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

Approved execution start: master base `c004a18d675b86040597f17f5911aa52f718d156` (branch `main`). Plans seeded on the fast branch in commit `3a4162c9c458f899470f59ac6e1a07b9ba748b3a` (docs-only, expert-rnd-classification plan files under `docs/plans/2026-08-24/`).

Baseline command results, run in the retained fast worktree at the seed commit (docs-only):

- `git diff --check` -> exit 0 (clean)
- `node --check src/main/resources/static/app.js` -> exit 0 (APPJS_OK)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0, `Tests run: 2739, Failures: 0, Errors: 0, Skipped: 4`, `BUILD SUCCESS` (total time 02:19 min). Baseline fully green (no pre-existing failures to compare against). Node suite within mvn: pass.

JDK 11 (zulu-11) verified: `openjdk version "11.0.15"`. Node `v25.7.0`.

Finalization note (2026-08-24): child 01-03 evidence commits originally recorded fix-logs without the canonical `- Fix commit: <SHA>` binding line the validator requires (subagent fixers used non-canonical formats). With HUMAN approval (ask option 「Authorize evidence-commit rebuild」), the branch was rebuilt three times via `git filter-branch --index-filter` replacing ONLY the fix-log blobs inside the three evidence commits with canonical content (SHA references inside fix-logs updated to rebuilt commits); all other trees byte-identical. Original commits preserved under `refs/original/`. All SHAs in this ledger/handoff reflect the final rebuilt history; the plan files, implementation trees, and test results are unchanged.

Plan family facts (from master plan):
- Master invariants M-1..M-6 bind all children; each child carries its own I<child>-<n> invariants.
- Ordered execution 01 -> 02 -> 03 -> 04; production release is one combined release after all four; 03 fails closed, so INTRODUCTION targets are 0 before backfill.
- Global constraints: JDK 11 mandatory (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command); full regression gate `mvn test` must end BUILD SUCCESS; shared files across children must be modified serially by one writer at a time; no push/merge/rebase/amend; fast-p evidence excluded from implementation commits.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 01 | docs/plans/2026-08-24/01-expert-rnd-classification-core.md | commit:10ec2d4f64806f07979e858022dae7a2569c7894 | none | 2 | LIGHT_PASS | c004a18d675b86040597f17f5911aa52f718d156 | a8cf1723df1403682a04babbf213f3c17a8ccc1b | 1 | 773527c7ed2ac65d4ae92d0233be82ab7417b1ef | 773527c7ed2ac65d4ae92d0233be82ab7417b1ef | 03ea91109e2ed50e35934734817cdf4acc852792 | epoch 1 PLAN_CONFLICT resolved via A1; round 1 FIXED; all four gates PASS, no notes; verify-log in children/01 |
| 02 | docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md | commit:2a0788dcf8cd412a1dca5218622d21e441ea7661 | 01 | 2 | LIGHT_PASS | 773527c7ed2ac65d4ae92d0233be82ab7417b1ef | 4937fe6ff32f36b655a173f4b742581700f2e2b5 | 1 | fb19d05290f1865f4ce862b74ba22d8bd3235451 | fb19d05290f1865f4ce862b74ba22d8bd3235451 | 6cdf9ed845bc398643c6fcc81e35ee746eb41c04 | epoch 1 PLAN_CONFLICT resolved via A2; round 1 FIXED; all four gates PASS, no notes; verify-log in children/02 |
| 03 | docs/plans/2026-08-24/03-expert-rnd-send-gate.md | commit:c49bece1aadb4d09565c5a68293087f14a591ea4 | 01 | 2 | LIGHT_PASS | fb19d05290f1865f4ce862b74ba22d8bd3235451 | b2188438ee45321b718efa5f70f3bbcaca1180e0 | 1 | 13d7edde81441cb19babcf681c4446fe26eabee2 | 13d7edde81441cb19babcf681c4446fe26eabee2 | 7ba170385b601fa6cd81ac89f6cc449b9832dd83 | epoch 1 PLAN_CONFLICT resolved via A3; round 1 FIXED; all four gates PASS, no notes; verify-log in children/03 |
| 04 | docs/plans/2026-08-24/04-expert-rnd-incremental-classification.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | 01,02,03 | 1 | LIGHT_PASS | 13d7edde81441cb19babcf681c4446fe26eabee2 | 0bc071bf24c84426315bc4b138d8aa4394182910 | 0 | — | 0bc071bf24c84426315bc4b138d8aa4394182910 | 2ed6901fc75fe70d888e3f4bd1c3b169bf1cd76c | all four gates PASS, no notes; verify-log in children/04 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-24/01-expert-rnd-classification-core.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | commit:10ec2d4f64806f07979e858022dae7a2569c7894 | 计划 01 验收标准「回归：运行 mvn test」vs 变更文件清单（9 文件） | T3 强制三份 mapping 同构新增 expertClassification：RAW 声明字段 32→33 使 ExpertIndexServiceTest per-field PUT 计数 pin 必然过期；ExpertSearchService 新增解析/logger 导入使 seam guard pin 431→445 必然过期，任何合规实现都无法保绿；按 guard 自带规程（:130）与 expert-reachability A1-A3 先例仅同步计数 32→33 与行号 431→445，context 不变 | HUMAN:ask 选项「Approve amendment A1」2026-08-24 |
| A2 | docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | commit:2a0788dcf8cd412a1dca5218622d21e441ea7661 | 计划 02 验收标准「回归：mvn test 全绿」vs 变更文件清单（10 文件） | T4 强制登记 EXPERT_CLASSIFICATION_BACKFILL（hasProgressUi=true）使 TaskExecutionSummaryExtractorTest 三个库存守卫 pin（hasProgressUi 白名单 6 项、taskType 全集 17 项、总数 17）必然过期，任何合规实现都无法保绿；按 A1 先例仅追加新类型 code，零断言语义变更 | HUMAN:ask 选项「Approve amendment A2」2026-08-24 |
| A3 | docs/plans/2026-08-24/03-expert-rnd-send-gate.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | commit:c49bece1aadb4d09565c5a68293087f14a591ea4 | 计划 03 验收标准「回归：所有现有…测试继续通过；mvn test 全绿」vs 变更文件清单（7 文件） | T1 新增 ~46 行使 seam guard pin 445→491 必然过期（context 不变，同 A1 机制）；I3-2 INTRODUCTION 门禁使 BatchSendTaskRuntimeIntegrationTest（baseSnapshot 默认 INTRODUCTION）无分类 fixture 的 matchesExpert 断言必然失败；修复为 pin 行号同步 + expert() helper 默认可发信分类 fixture，零生产逻辑变更 | HUMAN:ask 选项「Approve amendment A3」2026-08-24 |
