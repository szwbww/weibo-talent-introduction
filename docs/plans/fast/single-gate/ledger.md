# Fast-P Ledger — master: docs/plans/2026-08-28/00-single-gate-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-08-28/00-single-gate-master.md (commit 1f5a916489933fc9b2e8e469037fc912d55edd5d)
- Amendments: A1
- Master base: de228e17cc0134a7c11dea7cbf82054e8d249f99
- Branch: fast/single-gate
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-28T15:30:17+0800
- Current child: 04
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: 子计划 04 实施提交 742d1a2（8 个授权文件，机器判据全过）触发两个未授权测试文件的既有用例失效：① ExpertClassificationServiceTest.kt:505 I5a2-10 用例断言被 I4-6 删除的 ACCEPTED_CLASSIFICATION_VERSIONS（编译失败）；② OperatorStatusWriteSeamGuardTest.kt:67 NoiseSite 行钉 :545 因 Task 1 删除偏移至 :498（陈旧排除自检失败）。两处修复唯一确定（删用例 / 更新行号），需人工批准 A2 授权后恢复。
- Resume from: 04 epoch 1, base bc8a93762cca39c2542d79d1f3801589b6e4e155, implementation 742d1a27261d47c0aec00775a7da2f2dae92b7ee retained, next action A2 批准后验证

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
| 03 | docs/plans/2026-08-28/03-expert-types-required.md | commit:9058d028e9dcfe160d0bf74d45462c2f581af08f | none | 2 | LIGHT_PASS_WITH_NOTES | 658b60c25370bd8dd974e6a98d6eacc48315943b | bc8a93762cca39c2542d79d1f3801589b6e4e155 | 0 | — | bc8a93762cca39c2542d79d1f3801589b6e4e155 | — | impl Impl03TypesRequired; verify Verify03Light (epoch 2, A1 authorized 5 fixture files); RECORD_ONLY O-1 (boundary spans 02 evidence commit) |
| 04 | docs/plans/2026-08-28/04-single-gate-remove-sendable.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | 02,03 | 1 | PAUSED_FOR_HUMAN | bc8a93762cca39c2542d79d1f3801589b6e4e155 | 742d1a27261d47c0aec00775a7da2f2dae92b7ee | 0 | — | 742d1a27261d47c0aec00775a7da2f2dae92b7ee | — | impl Impl04RemoveGates; 机器判据全过；2 个未授权测试文件待 A2 批准；隔离全量 2943 Kotlin 1 failure |
| 05 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | 04 | 1 | PENDING | — | — | 0 | — | — | — | 删 sendable 概念/序列化/统计/DTO；10 文件 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-28/03-expert-types-required.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | commit:9058d028e9dcfe160d0bf74d45462c2f581af08f | 子计划 03「验证命令」全量回归门禁（mvn test / node --test 退出码 0）+ I3-1/I3-2（INTRODUCTION 研发类型必填） | I3-1/I3-2 校验使 10 Kotlin + 5 JS 既有空集合用例失效，授权文件集内无法同时满足行为变更与全量绿；修复由计划唯一确定（fixture 补三类默认值，机械、不改断言语义） | HUMAN:批准 A1 2026-08-28T16:47:41+0800 |
