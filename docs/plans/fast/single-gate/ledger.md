# Fast-P Ledger — master: docs/plans/2026-08-28/00-single-gate-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-08-28/00-single-gate-master.md (commit 1f5a916489933fc9b2e8e469037fc912d55edd5d)
- Amendments: A1, A2, A3, A4
- Master base: de228e17cc0134a7c11dea7cbf82054e8d249f99
- Branch: fast/single-gate
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-28T15:30:17+0800
- Current child: 05
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: 子计划 05 epoch 2 全量回归唯一失败：OperatorStatusWriteSeamGuardTest.kt:139（未授权文件）。原因：授权改动从 ExpertIndexController.kt 删除 expertSendable 2 行，其 operatorStatus 写点从 :436 上移 1 行至 :435，守卫的 NoiseSite 行钉（:60 处 ExpertIndexController.kt:436）失配 —— 与子计划 04 A2（545→498）同类行钉修正。守卫在 HEAD 6c3cf91 通过、仅在 child-05 工作区失败，确为 child-05 回归。修复唯一确定：行钉 436→435。12 个授权文件已全部改完（未提交），A4 修复已应用，定向测试 285 Kotlin 绿。需人工批准 A5 授权该文件一行修改。
- Resume from: 05 epoch 2, base 960fbe48e0b1ad7edd3f2ca68eccd29adafa654b, 12 authorized files edited uncommitted, next action A5 批准后一行修复 + 全量回归 + 提交

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
| 04 | docs/plans/2026-08-28/04-single-gate-remove-sendable.md | commit:44c3d656cbab10d9f279f11d90725e9864198222 | 02,03 | 2 | LIGHT_PASS_WITH_NOTES | bc8a93762cca39c2542d79d1f3801589b6e4e155 | 742d1a27261d47c0aec00775a7da2f2dae92b7ee | 1 | 960fbe48e0b1ad7edd3f2ca68eccd29adafa654b | 960fbe48e0b1ad7edd3f2ca68eccd29adafa654b | — | impl Impl04RemoveGates; fix Impl04Fix2 round 1 FIXED; verify Verify04Light; epoch 1 PLAN_CONFLICT resolved by A2; machine criteria pass; RECORD_ONLY O-1..O-3 (grep criterion precision, bookkeeping docs, test-entry migration) |
| 05 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:6c3cf91f4a167b4cd07d9c6874a3a527012e048d | 04 | 2 | PAUSED_FOR_HUMAN | 960fbe48e0b1ad7edd3f2ca68eccd29adafa654b | — | 0 | — | — | — | impl Impl05Cleanup2/3 crash + Impl05Finalize (12 文件已改未提交, A4 修复已应用); 唯一失败 OperatorStatusWriteSeamGuardTest 行钉 436→435 待 A5; 定向 285 Kotlin 绿, 机器判据全过 |
| 05 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | 04 | 1 | PENDING | — | — | 0 | — | — | — | 删 sendable 概念/序列化/统计/DTO；10 文件 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-28/03-expert-types-required.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | commit:9058d028e9dcfe160d0bf74d45462c2f581af08f | 子计划 03「验证命令」全量回归门禁（mvn test / node --test 退出码 0）+ I3-1/I3-2（INTRODUCTION 研发类型必填） | I3-1/I3-2 校验使 10 Kotlin + 5 JS 既有空集合用例失效，授权文件集内无法同时满足行为变更与全量绿；修复由计划唯一确定（fixture 补三类默认值，机械、不改断言语义） | HUMAN:批准 A1 2026-08-28T16:47:41+0800 |
| A2 | docs/plans/2026-08-28/04-single-gate-remove-sendable.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | commit:44c3d656cbab10d9f279f11d90725e9864198222 | 子计划 04 I4-6（删 ACCEPTED_CLASSIFICATION_VERSIONS）+「验证命令」全量回归门禁（mvn test 退出码 0） | I4-6 删除常量使既有 I5a2-10 用例编译失败（断言被删常量）；Task 1 删除致 OperatorStatusWriteSeamGuardTest 的 NoiseSite 行钉 545 过期；两处修复唯一确定（删用例 / 行号 545→498，同 05A-2 先例） | HUMAN:批准 A2 2026-08-28T17:35:54+0800 |
| A3 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:1f5a916489933fc9b2e8e469037fc912d55edd5d | commit:f20f06091dd3e34bd59df4c5ac0a5b45b7d28ee0 | 子计划 05 I5-5 范围闸门（执行前 grep 命中须 ⊆ 变更清单） | 基线审计与执行后工作区不符：ExpertSearchServiceTest.kt 两个 I1-5 用例（~:1874/:1929）读被删的 c.sendable 属性（编译阻塞）；其余 6 个命中文件为计划零改动规约（M-3/I5-4）或纯装饰注释/helper，不读属性不触发守卫。授权删两用例并修订 I5-5 固定排除项 | HUMAN:批准 A3 2026-08-28T20:35:50+0800 |
| A4 | docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md | commit:f20f06091dd3e34bd59df4c5ac0a5b45b7d28ee0 | commit:6c3cf91f4a167b4cd07d9c6874a3a527012e048d | 子计划 05 I5-5 范围闸门（A3 固定排除项） | A3 排除结论对 ManualInitialOutreachServiceTest.kt 不成立：classification(type) helper :4304/:4305/:4307 有 3 处真实代码引用 SENDABLE_TYPES（大写常量名，小写 grep 漏检），Task 1 删除即编译失败；全库复查其余命中均注释。授权 fixture 局部集合修复（原常量前三值，行为逐字不变）并从排除项移入清单 | HUMAN:批准 A4 2026-08-28T20:55:48+0800 |
