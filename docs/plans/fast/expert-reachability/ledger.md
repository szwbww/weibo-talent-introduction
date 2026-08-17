# Fast-P Ledger — master: docs/plans/2026-08-16/expert-reachability-00-execution-order.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-16/expert-reachability-00-execution-order.md (commit 1c7cf0e4c11c53d1f4d20f28964fce837f70442b)
- Amendments: A1,A2
- Master base: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- Branch: fast/expert-reachability
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-16T22:30:00+08:00
- Current child: 04
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

Approved execution start: master base `edda3e4e67e8b4511f3c7ca76b09926c56e4f69a` (branch `main`, commit `fix: compact batch task console layout`). Plans seeded on the fast branch in commit `1c7cf0e4c11c53d1f4d20f28964fce837f70442b` (docs-only, expert-reachability plan files under `docs/plans/2026-08-16/`).

Baseline command results, run in the retained fast worktree at the master base (seed commit is docs-only):

- `git diff --check` -> exit 0 (clean)
- `node --check src/main/resources/static/app.js` -> exit 0 (APPJS_OK)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0, `Tests run: 2456, Failures: 0, Errors: 0, Skipped: 4`, `BUILD SUCCESS`. Baseline fully green (no pre-existing failures to compare against). Node suite within mvn: 584 pass / 0 fail.

JDK 11 (zulu-11) verified: `openjdk version "11.0.15"`. Node `v25.7.0`.

Plan family facts (from master plan):
- Migrations: current max V99 (R-11); child 06 adds V100.
- Frontend anchors for child 04: `loadContacts` 4507, `renderContactListItems` 4739, badge helper 1463; index.html filters 458-545.
- Filter construction points for child 05: exactly 4 (R-6): `buildExpertFilters` 905, `buildEsFiltersForLevel` 1272, `buildMaterialReminderEsFilters` 1129, `matchesExpert` BatchExecutionModels 60.
- Child 06 front-end touch scale: `gateFilterEnabled` 12 touches in app.js (R-13).

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 02 | docs/plans/2026-08-16/expert-reachability-02-classifier-and-mapping.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | none | 1 | LIGHT_PASS | edda3e4e67e8b4511f3c7ca76b09926c56e4f69a | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | 0 | — | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | c878763b29fcd66066664f820023677152c9ac38 | all four gates PASS, no notes; verify-log in children/02 |
| 03 | docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md | commit:2663ecc9c5644fa3df2cb39e2cf723cf583ed2d2 | 02 | 2 | LIGHT_PASS_WITH_NOTES | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | 111aea17aa434bc5836a9409b451dc72954d62be | 0 | — | 111aea17aa434bc5836a9409b451dc72954d62be | ccaae40638386a4e1ffefc7d57615fbf365e5d78 | epoch 1 PLAN_CONFLICT resolved via A1; RECORD_ONLY O-1 (nullable ctor param style) / O-2 (boundary spans docs commits) |
| 04 | docs/plans/2026-08-16/expert-reachability-04-list-badge.md | commit:9e92424a44025b65b4c9091e139c28c596901205 | 03 | 2 | PENDING | 111aea17aa434bc5836a9409b451dc72954d62be | — | 0 | — | — | — | epoch 1 PLAN_CONFLICT (guard pin 483 stale vs 484) resolved via amendment A2; 4 files work retained uncommitted |
| 03 | docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | 02 | 1 | PENDING | — | — | 0 | — | — | — | — |
| 04 | docs/plans/2026-08-16/expert-reachability-04-list-badge.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | 03 | 1 | PENDING | — | — | 0 | — | — | — | — |
| 05 | docs/plans/2026-08-16/expert-reachability-05-filter-seams.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | 03 | 1 | PENDING | — | — | 0 | — | — | — | — |
| 06 | docs/plans/2026-08-16/expert-reachability-06-batch-config.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | 05 | 1 | PENDING | — | — | 0 | — | — | — | — |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:2663ecc9c5644fa3df2cb39e2cf723cf583ed2d2 | 计划 03 验证命令「回归：全量测试通过」vs 变更文件清单（8 文件） | T4 对 ExpertIndexController.kt 的授权改动使 OperatorStatusWriteSeamGuardTest 的 EXCLUDED_NOISE_SITES 行号 pin（90/431）必然过期，任何 T4 实现都无法保绿；按 guard 自带规程（:130）与 bdf853c 先例仅同步行号 90→94、431→483，context 不变 | HUMAN:继续 2026-08-16 |
| A2 | docs/plans/2026-08-16/expert-reachability-04-list-badge.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:9e92424a44025b65b4c9091e139c28c596901205 | 计划 04 验证命令「回归：全量测试通过」vs 变更文件清单（4 文件） | T1 对 ExpertIndexResponse 的授权字段追加使 guard pin 483 必然过期（实际 484），任何 T1 实现都无法保绿；按 guard 自带规程与 A1 同机制仅同步行号 483→484，context 不变，:94 pin 不受影响 | HUMAN:ask 选项「Approve amendment A2」2026-08-16 |
