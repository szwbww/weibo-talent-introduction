# Fast-P Ledger — master: docs/plans/2026-08-16/expert-reachability-00-execution-order.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-16/expert-reachability-00-execution-order.md (commit 1c7cf0e4c11c53d1f4d20f28964fce837f70442b)
- Amendments: A1,A2,A3,A4
- Master base: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- Branch: fast/expert-reachability
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-16T22:30:00+08:00
- Current child: N/A
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
| 02 | docs/plans/2026-08-16/expert-reachability-02-classifier-and-mapping.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | none | 1 | LIGHT_PASS | edda3e4e67e8b4511f3c7ca76b09926c56e4f69a | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | 0 | — | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | 2e61b972e7f0ba0919fdc306f11541c7323a0182 | all four gates PASS, no notes; verify-log in children/02 |
| 03 | docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md | commit:a84d93291a7cb8acd9a5c6d24873166de270a5cc | 02 | 2 | LIGHT_PASS_WITH_NOTES | 5396782203892adcc0dc69cc5160a2ec9a21fa6e | 8bd808383cad0253ca60032166cd954328b0f794 | 0 | — | 8bd808383cad0253ca60032166cd954328b0f794 | c88cc48216b9a219783224c19b73fe287e2b5093 | epoch 1 PLAN_CONFLICT resolved via A1; RECORD_ONLY O-1 (nullable ctor param style) / O-2 (boundary spans docs commits) |
| 04 | docs/plans/2026-08-16/expert-reachability-04-list-badge.md | commit:67c2a347a4ec52b3044e86513d33d9024616e70d | 03 | 2 | LIGHT_PASS_WITH_NOTES | 8bd808383cad0253ca60032166cd954328b0f794 | ae5634ebba7008ada65d496824aeea660960118d | 0 | — | ae5634ebba7008ada65d496824aeea660960118d | b648a72891a2fcebddaf1072583ed4eda4f8b6f5 | epoch 1 PLAN_CONFLICT resolved via A2; RECORD_ONLY O-1 (T3 in-function placement) / O-2 (worktree docs) / O-3 (title emailSource empty, plan-faithful) |
| 05 | docs/plans/2026-08-16/expert-reachability-05-filter-seams.md | commit:919ac436625fa98b3937a3c548e60f2660075857 | 03 | 2 | LIGHT_PASS | ae5634ebba7008ada65d496824aeea660960118d | 67cd47383467815856516274c2352a68f1e54ad1 | 1 | f6d81f1d4b64060ebf762715ad19b28452b463b8 | f6d81f1d4b64060ebf762715ad19b28452b463b8 | d69dc673a53f6580dfab474dd9c2305828f543b9 | epoch 1 PLAN_CONFLICT resolved via A3; round 1 = guard pin sync; no RECORD_ONLY |
| 06 | docs/plans/2026-08-16/expert-reachability-06-batch-config.md | commit:25236b115770fe17b716f335ddbc9563aebc1130 | 05 | 1 | LIGHT_PASS | f6d81f1d4b64060ebf762715ad19b28452b463b8 | 59f33864c0cd91f6699f83eabf5fa88e7c1d7839 | 0 | — | 59f33864c0cd91f6699f83eabf5fa88e7c1d7839 | 47cc02317cb1eafe83895feb0cf96314bf8f4111 | epoch 1 amendment A4 authorized 9th file (snapshot carrier) mid-run; Flyway migration IT Docker-gated environmental failure recorded, not faked |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-16/expert-reachability-03-sync-and-backfill.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:a84d93291a7cb8acd9a5c6d24873166de270a5cc | 计划 03 验证命令「回归：全量测试通过」vs 变更文件清单（8 文件） | T4 对 ExpertIndexController.kt 的授权改动使 OperatorStatusWriteSeamGuardTest 的 EXCLUDED_NOISE_SITES 行号 pin（90/431）必然过期，任何 T4 实现都无法保绿；按 guard 自带规程（:130）与 bdf853c 先例仅同步行号 90→94、431→483，context 不变 | HUMAN:继续 2026-08-16 |
| A2 | docs/plans/2026-08-16/expert-reachability-04-list-badge.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:67c2a347a4ec52b3044e86513d33d9024616e70d | 计划 04 验证命令「回归：全量测试通过」vs 变更文件清单（4 文件） | T1 对 ExpertIndexResponse 的授权字段追加使 guard pin 483 必然过期（实际 484），任何 T1 实现都无法保绿；按 guard 自带规程与 A1 同机制仅同步行号 483→484，context 不变，:94 pin 不受影响 | HUMAN:ask 选项「Approve amendment A2」2026-08-16 |
| A3 | docs/plans/2026-08-16/expert-reachability-05-filter-seams.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:919ac436625fa98b3937a3c548e60f2660075857 | 计划 05 验证命令「回归：全量测试通过」vs 变更文件清单（7 文件） | T3 的 listExperts @RequestParam（+1 行）与 T1 的 companion reachabilityFilter 块（+45 行）使 guard 三条 pin 必然过期（controller 94/484、service 431），任何 T1/T3 实现都无法保绿；按 guard 自带规程与 A1/A2 同机制仅同步行号 94→95、484→485、431→476，context 不变 | HUMAN:ask 选项「Approve amendment A3」2026-08-16 |
| A4 | docs/plans/2026-08-16/expert-reachability-06-batch-config.md | commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b | commit:25236b115770fe17b716f335ddbc9563aebc1130 | 计划 06 T6（resolveScope 接线）vs 变更文件清单（8 文件） | resolveScope(snapshot) 仅经 RecipientScope.fromSnapshot 构造，配置值须经 BatchExecutionSnapshot/toExecutionSnapshot/fromSnapshot 三个载体（全在 BatchExecutionModels.kt）；缺载体则 T6 静默 no-op、配置永不生效；按 gateFilterEnabled snapshot 载体先例（p4a）仅加 3 行默认值增量 | HUMAN:ask 选项「Approve amendment A4」2026-08-16 |
