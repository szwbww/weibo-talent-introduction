# Child 05 Execution Report — FAIL (epoch 2)

- Status: **FAIL** — full-suite gate red; required fix touches a file NOT in the 12 authorized files
- Executor: Impl05Finalize (fast-p child 05 executor, epoch 2)
- Date: 2026-08-28
- Branch: `fast/single-gate`
- Plan: `docs/plans/2026-08-28/05-sendable-vocabulary-cleanup.md` (amended A3+A4)
- Supersedes: epoch-1 execution.md (PLAN_CONFLICT, pre-A3/A4) — that conflict was resolved by amendments; epoch 2 re-ran from amended change list.

## Files Changed (12 authorized)

All 12 were edited; 11 carried over from epoch-2 implementation work, 1 (ManualInitialOutreachServiceTest.kt) received the A4 amendment fix in this epoch. NOT COMMITTED (see Blocker).

1. `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` (pre-existing edit: removed `expertSendable` DTO field + mapper line)
2. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationVersionGateGuardTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt`
11. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`
12. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — **A4 fix applied this epoch**: in private `classification(type)` fixture helper, all 3 `ExpertClassification.SENDABLE_TYPES` occurrences (productionScore / researchScore / negativeEvidence lines) replaced with `setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)` (former constant's exact values). Nothing else changed in the file.

## Commands + Exit Codes

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `JAVA_HOME=zulu-11 mvn test -Dtest='ExpertClassificationServiceTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,ExpertClassificationSchedulerTest,ExpertClassificationVersionGateGuardTest,ManualInitialOutreachServiceTest,ExpertSearchServiceTest'` | 0 | BUILD SUCCESS — all 8 classes green |
| 2 | `JAVA_HOME=zulu-11 mvn test` (full suite) | 1 | BUILD FAILURE — **Tests run: 2969, Failures: 1, Errors: 0, Skipped: 5**; sole failure `OperatorStatusWriteSeamGuardTest` (see Blocker) |
| 3 | `node --check src/main/resources/static/app.js` | 0 | OK |
| 4 | `git diff --check` | 0 | clean |

## Test Counts

Kotlin (targeted 8-class run, all 0 fail / 0 error):
- ExpertClassificationAdminControllerTest: 8
- ExpertClassificationServiceTest: 28
- ExpertClassificationSchedulerTest: 6
- ExpertSearchServiceTest: 67
- ExpertClassificationVersionGateGuardTest: 2
- ExpertClassificationBackfillServiceTest: 15
- ExpertIndexWriterServiceTest: 30
- ManualInitialOutreachServiceTest: 129
- Subtotal: 285 Kotlin tests, 0 fail

JS (node-check-app exec in run #1): 755 tests, 120 suites, 755 pass, 0 fail, 0 skipped, 0 todo.

Full suite (run #2): 2969 Kotlin run, 1 failure, 0 errors, 5 skipped (skips in unrelated environmental classes). JS exec goals did not run in run #2 (surefire failure aborts the build before the exec phase).

## Machine Criteria (final working-tree state)

| Criterion | Result | Evidence |
|---|---|---|
| Guard whitelist empty | PASS | `ExpertClassificationVersionGateGuardTest.kt:38` `ALLOWED_SENDABLE_SITES = emptySet()`; `:31` `ALLOWED_GATE_SITES = emptySet()`; guard test 2/2 green |
| `grep notSendable/可发信/不可发信` zero in `src/main/kotlin` | PASS | only hits in `src` are comments in `V109__require_expert_types_on_batch_send_task_config.sql` (resources) and `V109ExpertTypesMigrationTest.kt` (test) — zero code refs in `src/main/kotlin` |
| No `SENDABLE_TYPES` code refs outside comments | PASS | remaining 2 hits are comment-only (SQL migration comment, migration test comment); zero code references anywhere in `src` |

## Blocker (FAIL reason)

- **File needing edit (NOT in the 12 authorized files):** `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- **Failing test:** `operator_status write sites exactly match whitelist` (`:139`, class `com.weibo.talentintroduction.campaign.OperatorStatusWriteSeamGuardTest`)
- **Root cause (verified):** child-05's authorized edit to `ExpertIndexController.kt` deleted the `expertSendable` DTO field + mapper line (2 lines above the pre-existing `operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"` write), shifting that write from line 436 → 435. The guard's line-pinned exclusion `EXCLUDED_NOISE_SITES` entry `NoiseSite("…/ExpertIndexController.kt", 436, "operatorStatus = operatorStatus ?: expert.operatorStatus")` (`:60`) no longer matches (match requires exact line number), so `ExpertIndexController.kt` is flagged as an unregistered `operatorStatus` write site: expected whitelist `[ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt]` vs actual `[ExpertIndexController.kt, ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt]`.
- **Baseline proof:** guard test run in a temp worktree at HEAD `6c3cf91` (pre-edit state) → BUILD SUCCESS, 1/1 green. Failure exists only with the child-05 working tree → genuine child-05 regression, not pre-existing.
- **Required fix (unique, mechanical):** `OperatorStatusWriteSeamGuardTest.kt:60` `NoiseSite` line `436` → `435` (plus adjacent comment). Same class of line-pin amendment as child 04's human-approved A2 (545 → 498).
- **No authorized-file-only alternative exists:** the write must stay at its current line; re-adding lines to `ExpertIndexController.kt` would undo the intended deletion. Per assignment instructions ("If a failure's fix requires touching a file NOT in the 12 authorized files, STOP and return FAIL") the fix was NOT applied in-place.

## Deviations

- No implementation deviations. A4 applied exactly as specified (3 occurrences, fixture-local set, nothing else).
- Commit NOT created: Step 2 full-suite gate is red; committing a non-green state was not done. Epoch-2 change set remains uncommitted in the working tree (12 files + pre-existing unstaged docs bookkeeping `brief.md`/`ledger.md` + untracked fast-p docs), ready for immediate commit once the amendment authorizes the guard-test line fix.
- 8-class targeted run (Step 2 command 1) green; JS node-check green; `git diff --check` clean.

## Outcome

- Result: **FAIL** (per assignment protocol: required fix outside authorized file set)
- Blocker: `OperatorStatusWriteSeamGuardTest.kt` NoiseSite `ExpertIndexController.kt` 436 → 435
- Next action: controller/human amendment authorizing that one-line guard-test fix (same pattern as child 04 A2); after re-run, commit the 12 authorized files as `feat(fast-p): implement child 05` and re-verify.

## Epoch 2 — Final green full-suite record (post-A5)

- Full suite: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0; Tests run: 2969, Failures: 0, Errors: 0, Skipped: 5; BUILD SUCCESS (02:40 min)
- `node --check src/main/resources/static/app.js` -> exit 0
- `git diff --check` -> exit 0 (clean)
- Commit: `4636727` feat(fast-p): implement child 05 (13 authorized files, incl. A5 guard-test line fix 436 -> 435)
