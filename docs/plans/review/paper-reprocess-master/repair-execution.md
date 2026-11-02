# Repair Execution: paper-reprocess-master

## Identity and approval

- Approval source: explicit human invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`.
- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`
- Plan SHA-256 (start and end): `47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`
- Reviewed product boundary: `e213acb50791fe39a0de0a4d9d599bc4e2398cc1`; delta `e213acb..54a7d14` was documentation-only (7 commits, 8 docs files) before editing.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`; branch `fast/paper-reprocess-master`.
- Pre-execution code SHA (HEAD at dispatch): `54a7d14648ec7c2580c01c3cef26bbabaf0c5021`
- Post-execution code SHA (product commit): `291fb34b35be496673e28f124d443b9c46ba66f4`
- Executor: `PaperRepairExecutor` (execute-p worker); no review/repair skill was run.

## Changed files (exactly the ten Authorized Files)

- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` — explicit retry requeues this parent's cancelled, unfinished owned ordinary qualification once (state `CANCELLED`+no admission → `QUEUED`, activation preserved, evidence retained, `reasons_json='[]'`); completed/skipped targets untouched.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt` — resumes this parent's cancelled owned child before observing targets; qualification step 5 reports `CANCELLED` (never `SUCCESS`), and a cancelled/unadmitted item remains unfinished instead of completing the target.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — `resumeCancelledPaperRecheck` under parent permission: only own cancelled child, only never-admitted cancelled items requeued, phase `CANCELLED→RUNNING` in the same epoch, frozen spec/completed peers/cancellation receipts preserved.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` — `resumeFromPaperReprocess` (paper-owned, permission-checked, idempotent); `syncRetry` accepts a paper-owned ordinary run and still enforces `ITEM_NOT_ADMITTED` and the project-only leaf.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — the existing eight-step detail route reads ordinary epoch0 items only when the run is a server-owned paper-reprocess ENFORCE parent; non-paper runs and foreign run/item pairs keep `RECHECK_NOT_FOUND`/`RECHECK_ITEM_NOT_FOUND`.
- `src/main/resources/static/discovery-paper-reprocess.js` — dialog-level Escape `stopPropagation` (host stays open, native close/focus unchanged); summary reads `summary.targetTotalKnown` from the actual DTO.
- `src/test/js/discoveryPaperReprocess.test.js` — new regressions using the actual app.js host handler (host close count 0 for confirm and detail) and the real nested DTO shape (root field deliberately opposite).
- `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` — explicit evidence verdict `BOUND`/`ABSENT`/`INVALID`: rejects missing/unknown outcome, `AMBIGUOUS_TARGETS`, conflicting replay bindings, and any known expected docId mismatch; `SKIPPED` binds only with a matching actual docId; evidence recorded per row.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt` — CLI-level reconciliation regressions with temporary synthetic inputs (wrong doc, duplicate targets, missing outcome, SKIPPED wrong doc, conflicting replay name, valid control, honest absence).
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt` — real-SQL cancel→explicit-retry regressions (ordinary and existing-author child), ordinary/child detail seam, rejection of wrong/foreign/unrelated reads, and public sync-retry projection-only proof.

No other tracked file was modified. `git diff --check -- src scripts` reported no whitespace errors.

## Commands (all run freshly in this invocation with the plan commands)

JAVA_HOME for every Maven/child Java command: `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (Zulu 11.0.32.1).

| # | Command | Exit | Evidence |
|---|---|---|---|
| 1 | `mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | Tests 12, failures 0, errors 0, skipped 0 (ReplayTool 8, EndToEnd 4); `/tmp/paper-repair-diag-20261102T2142/cmd/01.log` |
| 2 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | 0 | 46 pass / 0 fail (44 before this repair); `cmd/02.log` |
| 3 | 15-class gate with `-DmysqlIt=true -DpaperReprocessIt=true` | 0 | Tests 205, failures 0, errors 0, skipped 1 (known optional `DISCOVERY_LAYOUT_FROZEN_SAMPLES` corpus skip); `cmd/03.log` |
| 4 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | Tests 4, failures 0, errors 0, skipped 0; `cmd/04.log` |
| 5 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 0 | Tests 48, failures 0, errors 0, skipped 0; `cmd/05.log` |
| 6 | `mvn package -DskipTests` | 0 | WAR `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` 53,149,381 bytes; Maven-bound JS 2037/2037; `cmd/06.log` |
| 7 | `node --test src/test/js/*.test.js` | 0 | 2037 tests / 354 suites, 2037 pass, 0 fail, 0 skip; `cmd/07.log` |
| 8 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | 0 | `PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1`; no file writes; `cmd/08.log` |
| 9 | `Paper03RuntimeSmoke` (exec:exec, JDK11 classpath) | 0 | `PAPER03_RUNTIME_HTTP_OK records=121 pages=7 ... actualNew=0 actualAdmitted=0 providerCalls=0 archiveMutations=0`; `cmd/09.log` |
| 10 | `DiscoveryVerificationIntegrationTest --paper-reprocess-smoke` | 0 | `PAPER04_RUNTIME_OK realSQL=true ... legacyCLI=true paidCalls=0`; `cmd/10.log` |
| 11 | `DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | 0 | `PAPER06_RUNTIME_OK realMySQL=true loopbackEsCAS=true realServices=true paidCalls=0 productionWrites=0`; runs: CANCELLED notEvaluated=1 (truthful), RETRIED COMPLETED new=1 admitted=1 notEvaluated=0, CHILD_RETRIED COMPLETED (evaluated); `cmd/11.log` |
| 12 | replay tool with `/usr/bin/time -l` (exact plan command) | 0 | `replayed=337 networkCalls=0`; 100.79s real; max RSS 1,296,171,008; output `/tmp/paper-reprocess-master-repair-replay.json`; `cmd/12.log` |
| 13 | reconcile (exact plan command; CSV/report paths verified absent first) | 0 | 337 rows, extras 0, 51 EXPECTED_MATCH / 286 EXPECTED_MISSING; `/tmp/paper-reprocess-master-repair-reconciled.csv` (308,112 bytes), `.md` (37,351 bytes) |
| 14 | Fresh isolated browser acceptance (real API + real MySQL) | 0 | See below; `cmd/14a.log`, `cmd/14b.log`, `audit-evidence.json`, `cross-evidence.json` |

Frozen input hashes re-verified: manifest `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`, baseline `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c`, runtime `5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3`.

## Command 14 browser evidence

Diagnostics directory: `/tmp/paper-repair-diag-20261102T2142/` (only place screenshots/JSON were written).

- AUDIT (121 papers, real API/MySQL): host handler sliced from the real `app.js` matched byte-for-byte (whitespace-normalized); Escape on the confirm and detail dialogs closed only the dialog (`hostCloses=0`, focus returned to the trigger), `cancelPosts=0`, task host mounted; completed summary rendered `目标总量已固定` (no “形成中”); run id 9002.
- Layouts: 1440@1 (CSS 1440×900), 1440@1.25 (CSS 1152×720), 390@1 (CSS 390×900), 390@1.25 (CSS 312×720); dialog and start button reachable and within the viewport in all four.
- ENFORCE cross-path: NEW `GET /api/discovery/rechecks/30716/items/1/steps` → 200 with 8 rendered steps; REPAIRED child `30717/items/4/steps` → 200 with 8 steps; source reader opened with 0 analysis POSTs; detail Escape kept the host with 0 cancels; public `POST /api/discovery/rechecks/30716/sync-retry` → 200, projection `ERROR→PROJECTED`, admission revision unchanged (1), provider calls 19 before and 19 after.
- Screenshots: `audit-escape-confirm.png`, `audit-complete*.png`, `audit-confirm-{1440,390}-{1,1.25}.png`, `cross-eightstep-NEW.png`, `cross-eightstep-REPAIRED.png`, `cross-reader.png`.

## Deviations

- None to the authorized file scope or behavior. The E2E fixture gained only harness wiring inside its authorized file (child-source seed, `cancelOnFirstStart` hook, task row, two new tests); the fixed 12-target assertions were not reduced.
- Command 3 retains the pre-existing optional layout-corpus skip (1); it is disclosed, not converted to PASS.
- Production #30711 ENFORCE, live ES/ORCID/Apollo and human acceptance remain outside this execution; no push/merge/release/deploy/production write was performed.

## Clean-state evidence

- Plan SHA-256 recomputed unchanged after all edits: `47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`.
- Product commit `291fb34` contains exactly the ten Authorized Files and is `HEAD` of `fast/paper-reprocess-master` before this evidence file.
- Working tree contained only the ten authorized modifications before the product commit; after it, only this evidence file remains for the docs-only commit.
- No applied migration, audit manifest, baseline receipt, ledger or baseline plan was modified.
