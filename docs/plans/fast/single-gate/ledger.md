# Fast-P Ledger — master: docs/plans/2026-08-28/00-single-gate-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-08-28/00-single-gate-master.md (commit 1f5a916489933fc9b2e8e469037fc912d55edd5d)
- Amendments: N/A
- Master base: de228e17cc0134a7c11dea7cbf82054e8d249f99
- Branch: fast/single-gate
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-28T15:30:17+0800
- Current child: 03
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: 子计划 03 实施提交 bc8a937 含 5 个未授权测试 fixture 文件（BatchSendTaskRuntimeIntegrationTest.kt、ManualInitialOutreachServiceTest.kt、BatchSendConfigControllerTest.kt、batchExpertTypeFilter.test.js、batchSendTaskConsoleInteraction.test.js）：I3-1/I3-2 校验使 10 Kotlin + 5 JS 既有空集合用例失效，计划的「验证命令」全量回归无法在不改这些 fixture 的情况下通过；修复由计划唯一确定（补三类默认值，机械、不改断言语义）。需人工批准 A1 授权该 5 文件后恢复。
- Resume from: 03 epoch 1, base 658b60c25370bd8dd974e6a98d6eacc48315943b, implementation bc8a93762cca39c2542d79d1f3801589b6e4e155 retained, next action A1 批准后验证

## Baseline

Approved execution start: master base `de228e1` (branch `main`, commit `fix: keep bound inbound mail pending`).
Plans seeded on the fast branch in commit `1f5a916` (docs-only, plans under `docs/plans/2026-08-28/` + referenced distribution output under `docs/plans/2026-08-25/`). `docs/runbooks/institutiontype-backfill-run.md` copied untracked into the worktree (referenced only by child 01 ops Task 6, not by implementers).

Baseline command results, run in the retained fast worktree at the master base (product tree = de228e1):

- `node --check src/main/resources/static/app.js` -> exit 0 (APPJS_OK)
- `node --test src/test/js/*.test.js` -> exit 0, tests 755, suites 120, pass 755, fail 0
- `git diff --check` -> clean
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0, BUILD SUCCESS, 02:34 min; Kotlin surefire Tests run: 2952, Failures: 0, Errors: 0, Skipped: 5; node suite re-run in test phase (755 pass)

JDK 11 (zulu-11) verified: `openjdk version "11.0.15" 2022-04-19 LTS`. Baseline fully green; no pre-existing failures to compare against.

Execution order: 01, 02, 03 (independent) → 04 (depends 02,03) → 05 (depends 04). Plans share identity commit `1f5a916` (seeded together).

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-08-28/01-lastpublicationyear-recovery.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | none | 1 | LIGHT_PASS_WITH_NOTES | 1f5a916489933fc9b2e8e469037fc912d55edd5d | cec6ce15ba3b41a6bf76e70eae503cdc5a925560 | 0 | — | cec6ce15ba3b41a6bf76e70eae503cdc5a925560 | 427222f | impl Impl01YearBackfill; verify Verify01Light; RECORD_ONLY O-1 (ExpertDiscoveryControllerTest helper local rename, functionally equivalent) |
| 02 | docs/plans/2026-08-28/02-legacy-outreach-explicit-types.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | none | 1 | LIGHT_PASS_WITH_NOTES | cec6ce15ba3b41a6bf76e70eae503cdc5a925560 | 658b60c25370bd8dd974e6a98d6eacc48315943b | 0 | — | 658b60c25370bd8dd974e6a98d6eacc48315943b | — | impl Impl02LegacyTypes; verify Verify02Light; RECORD_ONLY O-1..O-3 (boundary harness docs, execution deviations, bookkeeping) |
| 03 | docs/plans/2026-08-28/03-expert-types-required.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | none | 1 | PAUSED_FOR_HUMAN | 658b60c25370bd8dd974e6a98d6eacc48315943b | bc8a93762cca39c2542d79d1f3801589b6e4e155 | 0 | — | bc8a93762cca39c2542d79d1f3801589b6e4e155 | — | impl Impl03TypesRequired; epoch 1 PLAN_CONFLICT — 5 个未授权 fixture 文件待 A1 批准；全量 2974 Kotlin + 755 JS 绿 |
| 04 | docs/plans/2026-08-28/04-single-gate-remove-sendable.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | 02,03 | 1 | PENDING | — | — | 0 | — | — | — | 删 sendable/版本门禁，类型成唯一收口点；8 文件 |
| 05 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | 04 | 1 | PENDING | — | — | 0 | — | — | — | 删 sendable 概念/序列化/统计/DTO；10 文件 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
