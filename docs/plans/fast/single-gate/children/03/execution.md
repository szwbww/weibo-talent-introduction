# Child 03 Execution Report — 研发类型改为必填非空（写侧先行）

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/03-expert-types-required.md`
- Plan SHA-256: `f0a49326b57719ff3b439db3bde66422e01e6a4f3f40d36f958fba5b4dca28da` (recomputed at handoff, unchanged)
- Execution ID: `…/docs/plans/2026-08-28/03-expert-types-required.md@f0a49326b57719ff3b439db3bde66422e01e6a4f3f40d36f958fba5b4dca28da`
- Execution epoch: NEW
- Approval basis: controller brief + approved child plan (this invocation)
- Executor: `Impl03TypesRequired`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate`
- Target branch: `fast/single-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate@fast/single-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-single-gate`
- Child base SHA: `658b60c25370bd8dd974e6a98d6eacc48315943b`
- Pre-execution code SHA (HEAD before this commit): `229feeb0e8c74774998f3241cad22f57162430d9`
- Implementation commit: `bc8a93762cca39c2542d79d1f3801589b6e4e155` (`feat(fast-p): implement child 03`) — HEAD of `fast/single-gate`, reachable from branch
- Implementation boundary: `229feeb..bc8a937`

## Files changed

### Authorized (7/7 — the brief's exact list)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | Task 1 (I3-1/I3-2): in `normalizeAndValidate`, after `mailType = resolveMailType(...)`, `if (mailType == BatchSendType.INTRODUCTION.name) require(expertTypes.isNotEmpty()) { "研发类型至少选择一个" }` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | Task 2 (I3-2): in `validateSnapshotFields`, after the regions loop, `if (snapshot.mailType == BatchSendType.INTRODUCTION.name) require(snapshot.expertTypes.any { it.isNotBlank() }) { "研发类型至少选择一个" }` — same `IllegalArgumentException` → 422 channel, no new branch |
| 3 | `src/main/resources/db/migration/V109__require_expert_types_on_batch_send_task_config.sql` | **NEW** (Task 3, I3-3/I3-4/I3-5): `UPDATE batch_send_task_config SET expert_types_json = '["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]' WHERE expert_types_json IS NULL OR expert_types_json = '' OR expert_types_json = '[]';` — no `${`, exact three-value array. V109 confirmed as next free version (V108 is latest in `db/migration/`) |
| 4 | `src/main/resources/static/app.js` | Task 4 (S3-1/I3-6): (a) editor new-config default `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` (`showBatchConfigEditor`, edit-mode still uses `config.expertTypes` verbatim); (b) `fillManualFormDefaults` `expertTypes` default same three; (c) editor submit validation in the existing numeric-check paragraph: `if (readBatchMultiPickerValue("batchConfigEditorExpertTypes").length === 0) { showStatus("请至少选择一个研发类型", "error"); return; }` — message verbatim per S3-1, `showStatus` channel, no inline style / new class / new DOM node |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | Task 5 items 1–3: (1) INTRODUCTION + empty → `IllegalArgumentException` with 「研发类型至少选择一个」, never saves (replaces the child-02 `I2-3` empty-persists test whose write-side assertion the new contract supersedes; its read-side assertion — stored `'[]'` row still views as empty, read semantics unchanged until child 04 — is preserved); (2) MATERIAL_REMINDER + empty → saves `"[]"` (I3-1 regression); (3) INTRODUCTION + non-empty → saves, `expertTypesJson` verbatim. Helpers `createCmd`/`updateCmd` now default `expertTypes` to the three types (mirrors post-change production default); 6 `updateLegacyConfig` INTRODUCTION fixtures gained the post-V109 `expertTypesJson` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | Task 5 items 4–5: (4) INTRODUCTION snapshot + empty → 422 with 「研发类型至少选择一个」, executor never invoked; (5) MATERIAL_REMINDER snapshot + empty (enabled template) → 202 ACCEPTED, launches (I3-1). setUp `legacyIntroConfig`, the `startScheduled` config, and the capacity-test snapshot carry the post-V109 three types so existing launch tests pass the new gate |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/V109ExpertTypesMigrationTest.kt` | **NEW** (Task 5 item 6, QaSeedEncodingRepairMigrationTest paradigm, no Docker): text asserts — contains `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` (I3-3); contains `WHERE`, `'[]'`, `IS NULL`, `= ''` (I3-4); does NOT contain `${` (I3-5) |

### REQUIRED test-fixture adaptations (5 files NOT in the plan's authorized list — see Deviations)

| # | File | Change (mechanical, uniquely determined by I3-1 + V109) |
|---|---|---|
| 8 | `src/test/js/batchExpertTypeFilter.test.js` | `editor save payload includes expertTypes key`: sandbox stub now returns the three default types; assertion `captured.expertTypes` updated `[]` → the three types |
| 9 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 4 `saveBatchConfigEditor` tests: `readBatchMultiPickerValue` stub `() => []` → `() => ["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` (assertions untouched) |
| 10 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | `enabledConfig()` gains post-V109 `expertTypesJson`; `baseSnapshot()` gains the three `expertTypes` |
| 11 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | `updateLegacyConfig preserves operatorStatus when only cron changes (I-4)` fixture gains post-V109 `expertTypesJson` |
| 12 | `src/test/kotlin/.../mail/controller/BatchSendConfigControllerTest.kt` | `introEntity()` helper gains post-V109 `expertTypesJson` (fixes both failing PUT tests) |

## Invariant compliance

- **I3-1**: backend gate condition is exactly `mailType == BatchSendType.INTRODUCTION.name` in both write paths; MATERIAL_REMINDER empty still saves (test 2) and launches (test 5). `expertTypesFilter()` contract untouched.
- **I3-2**: both write entries validated — `BatchSendTaskConfigService.normalizeAndValidate` (create + update + updateLegacyConfig) and `BatchSendControlService.validateSnapshotFields` (all snapshot paths: `startScheduled`, `startManual(request)`, `startManualFromConfig`, legacy `startAuto`/`startManual`/`runManualOnce`). `grep -rn "研发类型至少选择一个" src/main/kotlin` → exactly the 2 intended files.
- **I3-3**: V109 array verbatim `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]`; `ExpertClassification.SENDABLE_TYPES` still enum first three (verified, unchanged).
- **I3-4**: V109 `WHERE expert_types_json IS NULL OR expert_types_json = '' OR expert_types_json = '[]'` — only empty rows covered; operator manual selections not overwritten.
- **I3-5**: V109 body contains no `${` (asserted by test + grep 0 hits).
- **I3-6**: frontend defaults are exactly the three literals matching `batchExpertTypeOptions()` first three values verbatim; no hand-written six-value list anywhere in the diff (verified by grep: no `SERVICE_ONLY`/`OUT_OF_SCOPE`/`UNKNOWN`/`UNCLASSIFIED` literals added).
- **S3-1**: `git diff src/main/resources/static/styles.css` and `git diff src/main/resources/static/index.html` both empty; app.js diff contains no `style=`, no new class, no new DOM node; message verbatim `请至少选择一个研发类型` via `showStatus(…, "error")`.
- No gates deleted (`expertSendableFilter`, in-memory checks, `expertTypesFilter` empty→null contract all untouched); read-side semantics unchanged (empty `expertTypes` = unrestricted until child 04); `ExpertClassificationService` untouched (M-3).

## Commands (all ran freshly in this invocation, final state, JDK 11 zulu)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn test -Dtest='BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,V109ExpertTypesMigrationTest'` | 0 | BUILD SUCCESS; 101 tests (64 + 34 + 3), 0 failures, 0 errors |
| `node --check src/main/resources/static/app.js` | 0 | OK |
| `node --test src/test/js/*.test.js` | 0 | **755 tests, 755 pass, 0 fail** (120 suites) |
| `JAVA_HOME=…zulu-11… mvn test`（full regression） | 0 | BUILD SUCCESS; **Tests run: 2974, Failures: 0, Errors: 0, Skipped: 5** (baseline 2952 + children 01/02 + this child's 8 new tests: +3 config-service, +2 control-service, +3 V109-migration) |
| `git diff --check` | 0 | no output |

## Deviations

1. **5 unlisted test-fixture files updated (files 8–12 above).** The plan's Task 1/Task 2 validation (I3-1/I3-2) is a contract flip for INTRODUCTION empty `expertTypes`. It breaks 10 Kotlin tests in 3 test classes the plan did not list (`BatchSendTaskRuntimeIntegrationTest` ×7, `BatchSendConfigControllerTest` ×2, `ManualInitialOutreachServiceTest` ×1) and 5 JS tests in 2 files (`batchExpertTypeFilter.test.js` ×1, `batchSendTaskConsoleInteraction.test.js` ×4) — all fixtures stubbed the pre-change contract (empty types allowed). The plan's own required commands (`node --test src/test/js/*.test.js`, full-suite `mvn test`) cannot pass without aligning these fixtures, and the brief mandates fixing failures caused by the changes. Every fixture edit is mechanical and uniquely determined by the plan's own invariants (post-V109: all stored INTRODUCTION configs/snapshots non-empty, three default types), zero production behavior change, no test weakened. Flagged prominently for the human gate; revert these 5 files if the gate prefers a plan amendment instead.
2. **worktree_identity.py helper failure**: script errors on a stale locked worktree entry (`/sessions/…` no longer exists) in `git worktree list`; identity computed manually with identical logic (root/branch/git-dir/HEAD via `git rev-parse`) and re-verified at handoff. Same environment note as children 01/02.

## Post-execution state

- Commit message: `feat(fast-p): implement child 03`; SHA `bc8a93762cca39c2542d79d1f3801589b6e4e155`; is HEAD of `fast/single-gate` and reachable from the branch (verified).
- Commit contains the 12 files above; fast-p docs (`docs/plans/fast/**`) and `docs/runbooks/institutiontype-backfill-run.md` excluded (left for controller's evidence commit). Working tree clean apart from that untracked runbook.
- No push/merge/rebase/amend/reset; no history rewrite; no gates deleted; nothing beyond child 03 touched (child 04 out of scope).

## Freshness

- Plan identity rechecked: YES (sha unchanged)
- Worktree identity rechecked: YES
- Reported commit reachable from target branch: YES
- Required commands run this invocation on final state: YES (focused mvn re-run after last fixture edit; full mvn ×2 green)
- Historical evidence used only as baseline: YES

## Remaining Blocker

None.
