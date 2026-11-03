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

# Repair Execution Epoch 3 (revised repair: identity validation and child sync retry)

## Identity and approval

- Approval source: explicit human invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md` in the executing chat. It satisfies the plan's Human Approval gate for this exact artifact.
- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`
- Plan SHA-256 (start and end, recomputed): `ca1e7a97965282cd3bd91354d5cbf7cd3e1e3aa8a39ef2bd77a4dbed8681a3aa`
- Superseded lineage: same path at commit `3ab2e86c80993929350d1dc40c859fed1a24adbc`, SHA-256 `47b6bccdb128dd02177fe0d629e251afe781064d75dcbc5f18c07ab24a829110`; the epoch-1/2 receipts above are preserved unchanged and were not reused as proof for this artifact.
- Execution epoch: NEW for this plan identity. Prior FAIL / DIVERGING receipts in `machine-verification.md` remain as-is.
- Required pre-repair product baseline: `291fb34b35be496673e28f124d443b9c46ba66f4`; `git diff --name-only 291fb34..HEAD -- src scripts` was empty before edits.
- Pre-execution docs HEAD: `07dcbd00b4bcd6b05b8e2ad1bee2736fd476cfb7` (docs-only; `git diff --name-only 291fb34..07dcbd0` contains only `docs/`).
- Post-execution product SHA: `7e131474f403d9b28178148909180ac8f1d7d1bb`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`; branch `fast/paper-reprocess-master`; worktree git dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`; common git dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Executor: `PaperRepairExecutor2` (execute-p worker). `review-fast-p` was not run.

## Changed files (exactly the four Authorized Files)

- `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` — run-target evidence now requires the actual `target.email` to be a non-empty string whose trim + locale-independent lowercase UTF-8 SHA-256 prefix-24 equals the immutable manifest combination's `emailHash`; missing/null/blank/non-string/wrong address is `INVALID`/`REGRESSION` and stays in the per-row output. Flat `--new-replay` semantics, SOURCE_MISSING, honest absence, EXTERNAL_DATA_CHANGED, extras and duplicate/conflict/outcome/docId behaviour are unchanged.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt` — the accepted emailHash is now derived from a real address via `DiscoveryIdentity.normalizedEmail`/`hash`; the run export carries a per-target `email`; new discriminating cases: valid, case/whitespace control, wrong address, absent key, JSON null, blank, non-string. A valid flat `--new-replay` receipt without `targetEmail` still reconciles as `BOUND`/`EXPECTED_MATCH`.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` — `syncRetry` dispatches on the stored run type (`findRun(id)?.recheckJson != null`). A real recheck (including a paper-reprocess child) keeps `requireRecheck`/`requireRecheckItem` (owner + `reviewEpoch == runId`); only an ordinary run with server-proven paper-reprocess ownership uses `requirePaperOwnedItem`. Guards, `COMPLETED`/non-null `admissionRevision` (0 valid)/projection checks, idempotence and the projection-only leaf are unchanged.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessEndToEndTest.kt` — new real-MySQL + strict-loopback-ES test: admitted ordinary epoch0 item (revision 1) and same-parent REPAIRED child (run 30717/item 4, epoch 30717, legitimate admission revision 0) both start with candidate projection `ERROR`; the ordinary item is reprojected through `syncRetry`, the child through the same seam, both reach `PROJECTED`, ids/epochs/revisions and admissions are unchanged, providers and raw writes do not increase, and wrong/foreign/unowned combinations stay `RECHECK_ITEM_NOT_FOUND`/`RECHECK_NOT_FOUND`. Registered in the standalone runtime `main`; the fixed 12-target test and its counts were not changed.

## Commands (all run freshly in this invocation, final code state)

JAVA_HOME for every Maven/child Java command: `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (Zulu 11.0.32.1). Maven: `/opt/homebrew/bin/mvn`. Fresh diagnostics directory: `/tmp/paper-reprocess-master-identity-sync.po_d25vi` (`REPAIR_TMP`, created by `mktemp`).

| # | Command | Exit | Evidence |
|---|---|---|---|
| 1 | `mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | Tests 14, failures 0, errors 0, skipped 0 (ReplayTool 9, EndToEnd 5); `cmd01.log` |
| 2 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | 0 | 46 tests / 46 pass / 0 fail; `cmd02.log` |
| 3 | 15-class gate with `-DmysqlIt=true -DpaperReprocessIt=true` | 0 | Tests 206, failures 0, errors 0, skipped 1 (known optional `DISCOVERY_LAYOUT_FROZEN_SAMPLES` corpus skip); `cmd03.log` |
| 4 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | Tests 5, failures 0, errors 0, skipped 0; `cmd04.log` |
| 5 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 0 | Tests 48, failures 0, errors 0, skipped 0 (real MySQL empty schema + upgrade); `cmd05.log` |
| 6 | `mvn package -DskipTests` | 0 | BUILD SUCCESS; war `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` 53,149,457 bytes; Maven-bound JS test task ran; `cmd06.log` |
| 7 | `node --test src/test/js/*.test.js` | 0 | 2037 tests / 354 suites, 2037 pass, 0 fail, 0 skip; `cmd07.log` |
| 8 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | 0 | `PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1`; no writes; `cmd08.log` |
| 9 | `Paper03RuntimeSmoke` (exec:exec, JDK11) | 0 | `PAPER03_RUNTIME_HTTP_OK records=121 pages=7 sourceFrozen=true duplicateRunId=9002 conflicts=true actualNew=0 actualUpdated=0 actualAdmitted=0 providerCalls=0 archiveMutations=0`; `cmd09.log` |
| 10 | `DiscoveryVerificationIntegrationTest --paper-reprocess-smoke` | 0 | `PAPER04_RUNTIME_OK realSQL=true loopbackEsFixture=true originalRetainedPDF=true consumer=true recheck=true worker=true commit=true projection=true legacyCLI=true paidCalls=0`; `cmd10.log` |
| 11 | `DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | 0 | `PAPER06_RUNTIME_OK realMySQL=true loopbackEsCAS=true realServices=true paidCalls=0 productionWrites=0` (5 fixture tests incl. the new sync-retry case); `cmd11.log` |
| 12 | replay tool with `/usr/bin/time -l` (exact plan command) | 0 | `replayed=337 networkCalls=0`; 98.37 s real; max RSS 1,156,612,096; `$REPAIR_TMP/replay.json`; `cmd12.log` |
| 13 | reconcile (exact plan command; CSV/report paths absent first) | 0 | 337 rows, extras 0, 51 EXPECTED_MATCH / 286 EXPECTED_MISSING; `$REPAIR_TMP/reconciled.csv` (338 lines), `reconciled.md`; `cmd13.log` |
| 14 | Fresh isolated browser/API acceptance (real API + real MySQL; see below) | 0 | `audit-evidence.json`, `cross-evidence.json`, `static-contract.json`, screenshots; `c14-audit-browser.log`, `c14-cross-browser.log`, `c14-static-contract.log` |

Frozen input hashes re-verified before/after: manifest `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`, baseline `/tmp/paper-reprocess-30711-baseline.json` `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c`, runtime `/tmp/paper-reprocess-30711-runtime.json` `5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3`.

## Command 14 browser evidence

Diagnostics directory: `/tmp/paper-reprocess-master-identity-sync.po_d25vi` (only place screenshots/JSON were written). Harnesses were rebuilt from the prior isolated patterns into this fresh directory; the AUDIT runtime is the real `DiscoveryPaperReprocessController` over real MySQL with 121 papers, the cross runtime is the real reprocess/query/recheck controllers plus the real 12-target fixture and strict loopback ES.

- AUDIT (run 9002, 121/121 papers): empty state disabled entry with “请选择一个执行任务”; running state `处理中` (progress 25); an injected real-HTTP 500 on the run-detail GET rendered “更新失败，显示上次结果” while keeping the durable panel; recovered completion rendered `summary.targetTotalKnown` (“目标总量已固定”, no “形成中”). Confirm and detail Escape closed only their dialog (`hostCloses=0`, focus returned to the trigger/row, host mounted, `cancelPosts=0`); the fixture keydown handler matched the real `app.js` handler byte-for-byte after whitespace normalization. Layouts captured with physical 1440/390 and 100%/125% viewport equivalents as separate contexts (CSS viewports 1440/1152/390/312 at deviceScaleFactor 1/1.25); dialog and start button stayed reachable and inside the viewport in all four.
- Cross path: parent run 30716; admitted ordinary epoch0 item 1 (admission revision 1, projection `ERROR`/`CANDIDATE_SYNC_FAILED`) and owned REPAIRED child run 30717/item 4 (epoch 30717, admission revision 0, projection `ERROR`). Both opened the real eight-step view through the public HTTP routes (`GET .../items/{id}/steps` 200 ×5) with the source reader open and `analysis POST = 0`. The ordinary item was reprojected via the original “仅重试候选同步” button and the child via the public `POST /api/discovery/rechecks/30717/sync-retry` (200); both reached `PROJECTED`, admission revisions stayed 1 and 0, run/item/epoch values stayed 30716/1/0 and 30717/4/30717, admission rows were identical, provider calls stayed 19 → 19 → 19 and raw writes 3 → 3. Rejections: ordinary-run + child-item 404 `RECHECK_ITEM_NOT_FOUND`, child-run + ordinary-item 404 `RECHECK_ITEM_NOT_FOUND`, non-paper-owned ordinary run 99001 + ordinary item 404 `RECHECK_NOT_FOUND`, with item projections/admissions/providers unchanged afterwards.
- S-1..S-6 static contract (`static-contract.json`): contract CSS block equals the plan block byte-for-byte (5,515 bytes), preceding committed CSS prefix unchanged, all four S-6 HTML blocks and five S-6 DOM ids present, no inline style/`innerHTML`/`localStorage` in the new module, the three cache keys equal (`20261102-paper-reprocess`), the reprocess script precedes `discovery-papers.js`, and no client-side global aggregation (`size=100`).
- Screenshots: `audit-empty-1440.png`, `audit-running-1440.png`, `audit-500-long-reason.png`, `audit-complete-1440-1.png`, `audit-escape-confirm.png`, `audit-escape-detail.png`, `audit-confirm-{1440,390}-{100,125}.png`, `cross-eightstep-NEW.png`, `cross-ordinary-projected.png`, `cross-eightstep-REPAIRED.png`, `cross-child-projected.png`, `cross-reader.png`.

## Deviations

- None to the authorized file scope or behavior. Only the four Authorized Files changed; no QueryService/Repository/Worker/frontend/plan/ledger/report/migration was modified.
- `execute-p` helper `scripts/worktree_identity.py` fails at line 30 on a stale unrelated Codex publish registration (`/private/var/folders/.../multi-ai-kit-publish-...` no longer exists). Worktree identity was therefore recorded manually from `git rev-parse --show-toplevel/--git-dir/--git-common-dir`, the branch and HEAD, and every git operation was run with `-C`/cwd on this worktree.
- Command 3 retains the pre-existing optional layout-corpus skip (1); it is disclosed, not converted to PASS.
- Command 13 uses the legitimate flat `--new-replay` receipt (no target address); the new actual-email requirement is proven by the CLI regressions in commands 1/3 and by the real run export (`reportRun`) which carries real target emails.
- The epoch-2 C10 trace-lease observation was not re-run in this epoch; its receipts above are preserved untouched and no claim is made about it.
- Production #30711 ENFORCE, live ES/ORCID/Apollo and human acceptance remain outside this execution; no push/merge/release/deploy/production write was performed.

## Clean-state evidence

- Plan SHA-256 recomputed unchanged after all edits and commits: `ca1e7a97965282cd3bd91354d5cbf7cd3e1e3aa8a39ef2bd77a4dbed8681a3aa`.
- Frozen input hashes unchanged (see above).
- Product commit `7e131474f403d9b28178148909180ac8f1d7d1bb` contains exactly the four Authorized Files and is `HEAD` of `fast/paper-reprocess-master` before this evidence file; `git status --porcelain` was empty.
- Post-execution product diff: `git diff --name-only 291fb34..7e131474 -- src scripts` lists only the four Authorized Files.
- After this file is appended, only `docs/plans/review/paper-reprocess-master/repair-execution.md` remains for the docs-only commit.
- No applied migration, audit manifest, baseline receipt, ledger, baseline plan or review report was modified.

---

# Epoch 3 execution — bind evidence source and isolate pending dialog Escape

## Identity and approval

- Approval source: explicit human invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/fix/paper-reprocess-master/repair.md`.
- Repair plan SHA-256 (start and end): `50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9` (the prior drafts `47b6bccd…` at `3ab2e86` and `ca1e7a97…` at `30b2b46` are lineage, not this execution).
- Required product baseline: `7e131474f403d9b28178148909180ac8f1d7d1bb`; HEAD at dispatch `a5f209ccb777f2762f517e0449f260ddb891b6f0` (docs-only).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`; branch `fast/paper-reprocess-master`; git-dir `…/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`.
- Pre-execution code SHA: `7e131474f403d9b28178148909180ac8f1d7d1bb`.
- Post-execution code SHA (product commit): `0b3c707147e8afa8ce4fe4002cb83752f28da0c8`.
- Evidence HEAD: the docs-only commit recorded below.
- Executor: `PaperRepairExecutor3` (execute-p worker); no review/repair skill was run.

## Findings resolved

- **V-3** — the reconciler now chooses the evidence protocol from the CLI input origin (`--reprocess-run` vs `--new-replay`), never from a flattened candidate's self-reported `status`/fields. A run row that also carries `status=ALREADY_RECOVERED` still enforces run outcome, actual `targetEmail` normalization fingerprint, docId, and duplicate/conflict checks; wrong/missing/null emails stay `INVALID`/`REGRESSION` (never `ABSENT`/`EXPECTED_MISSING`, never flat success).
- **V-2** — the component now owns Escape at the document capture phase, before the host's document handler, but only while a confirm/detail dialog is open. When a disabled start button leaves real focus on `BODY`, the native Escape still closes only the component dialog (`hostCloses=0`, `cancelPOST=0`), the native close/focus restore is untouched, the pending request key/UUID/run resume after the deferred create, and unrelated Escape still reaches the host after both dialogs close/dispose. No `preventDefault`, no app.js/CSS/DOM change.

## Changed files (exactly the four Authorized Files)

- `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py` — `evidence`/`classify` split into `flat_evidence` (flat `--new-replay`) and `run_evidence` (real run); `is_run` derived from `new is None` and threaded through `evidence`/`classify`.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt` — `target(...)` gains an optional `candidateStatus`; new test `run evidence origin is decided by CLI input, never candidate self-reported status` (wrong/missing/null email with `status=ALREADY_RECOVERED` → `REGRESSION`/`INVALID`; correct email → `EXPECTED_MATCH`/`BOUND`).
- `src/main/resources/static/discovery-paper-reprocess.js` — `listen` accepts an optional `capture`; a document-capture `keydown` listener stops propagation for Escape while either dialog is open and is removed on dispose.
- `src/test/js/discoveryPaperReprocess.test.js` — minimal document-level capture/bubble event model inside this test file only; new test `pending create BODY focus still owns Escape and resumes the same run` extending the real-app-handler assembly.

No other tracked file was modified. `git diff --check` reported no whitespace errors.

## Commands (all seventeen run freshly in this invocation, exact plan commands)

JAVA_HOME for every Maven/child Java command: `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (Zulu 11.0.32.1). Maven: `/opt/homebrew/bin/mvn`. Fresh `REPAIR_TMP=/tmp/paper-reprocess-master-source-escape.OkcWrq`.

| # | Command | Exit | Evidence |
|---|---|---|---|
| 1 | `mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest,DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | ReplayTool 10/0/0/0, EndToEnd 5/0/0/0; Maven-bound JS 2038/2038; `cmd01.log` |
| 2 | `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | 0 | 47 pass / 0 fail; `cmd02.log` |
| 3 | 15-class gate with `-DmysqlIt=true -DpaperReprocessIt=true` | 0 | Kotlin 207/0/0/1 (known optional layout-corpus skip); JS 2038/2038; `cmd03.log` |
| 4 | `mvn test -Dtest=DiscoveryPaperReprocessEndToEndTest -DpaperReprocessIt=true` | 0 | 5/0/0/0; `cmd04.log` |
| 5 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 0 | 48/0/0/0; real MySQL empty schema + upgrade; `cmd05.log` |
| 6 | `mvn package -DskipTests` | 0 | BUILD SUCCESS; WAR 53,149,648 bytes; Maven-bound JS 2038/2038; `cmd06.log` |
| 7 | `node --test src/test/js/*.test.js` | 0 | 2038 tests / 354 suites, 0 fail; `cmd07.log` |
| 8 | `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py` | 0 | `PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1`; `cmd08.log` |
| 9 | `Paper03RuntimeSmoke` (exec:exec, JDK11) | 0 | `PAPER03_RUNTIME_HTTP_OK records=121 pages=7 sourceFrozen=true duplicateRunId=9002 conflicts=true actualNew=0 actualUpdated=0 actualAdmitted=0 providerCalls=0 archiveMutations=0`; `cmd09.log` |
| 10 | `DiscoveryVerificationIntegrationTest --paper-reprocess-smoke` | 0 | `PAPER04_RUNTIME_OK realSQL=true loopbackEsFixture=true originalRetainedPDF=true consumer=true recheck=true worker=true commit=true projection=true legacyCLI=true paidCalls=0`; `cmd10.log` |
| 11 | `DiscoveryPaperReprocessEndToEndTest --paper-reprocess-e2e-smoke` | 0 | `PAPER06_RUNTIME_OK realMySQL=true loopbackEsCAS=true realServices=true paidCalls=0 productionWrites=0`; `cmd11.log` |
| 12 | replay tool with `/usr/bin/time -l` (exact plan command) | 0 | 337 rows, `networkCalls=0`, `businessWriteServicesConstructed=0`; max RSS 1,020,362,752; `$REPAIR_TMP/replay.json`; `cmd12.log` |
| 13 | reconcile (exact plan command) | 0 | 337 rows, extras 0, 51 EXPECTED_MATCH / 286 EXPECTED_MISSING; `$REPAIR_TMP/reconciled.csv` (338 lines), `reconciled.md`; `cmd13.log` |
| 14 | Fresh isolated browser/API acceptance (real API + real MySQL; below) | 0 | `audit-evidence.json`, `cross-evidence.json`, `cross-targets.json`, 21 screenshots; `cmd14-audit-browser.log`, `cmd14-cross-browser.log` |
| 15 | `mvn test -Dtest=DiscoveryPaperReprocessRuntimeScenarios -DpaperReprocessIt=true -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never` | 0 | 6/0/0/0; `cmd15.log` |
| 16 | `DiscoveryVerificationIntegrationTest --retained-identity-smoke` (exec:exec, JDK11) | 0 | `RETAINED_IDENTITY_RUNTIME` AUDIT+ENFORCE, `isolatedMySQL=true loopbackEsCAS=true providers=0 rosterHashUnchanged=true archiveUnchanged=true`; `cmd16.log` |
| 17 | `node --check src/main/resources/static/discovery-paper-reprocess.js` | 0 | no output; `cmd17.log` |

Frozen input hashes re-verified before/after: manifest `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`, baseline `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c`, runtime `5d00ffbc736f653fe64b93b5e667c8baf776aa33b1320716d212291b45e037c3`.

## Command 14 browser evidence

Diagnostics directory: `/tmp/paper-reprocess-master-source-escape.OkcWrq`. Harnesses rebuilt fresh (`RepairAuditServer` / `RepairCrossPathServer` recompiled against `target/classes`+`target/test-classes`+dependency classpath, DIAG repointed to the fresh dir); AUDIT is the real `DiscoveryPaperReprocessController` over real MySQL (121 papers), cross is the real reprocess/query/recheck controllers plus the real 12-target fixture with loopback ES.

- **AUDIT (run 9002, 121/121)**: empty state disabled entry “请选择一个执行任务”; running `处理中`; injected real-HTTP 500 rendered “更新失败，显示上次结果” with the durable panel kept; completion rendered `targetTotalKnown` (“目标总量已固定”, no “形成中”). **Pending BODY Escape**: `pendingBefore {activeTag:"BODY", activeId:"", confirmOpen:true, startDisabled:true}` after a real 3 s deferred create, then native Escape → `pendingAfter {hostCloses:0, confirmOpen:false, taskHostMounted:true}`. Confirm and detail Escape each `hostCloses:0` with focus returned to the trigger/row; `cancelPosts:0`; the fixture keydown handler matched the real `app.js` handler byte-for-byte. Layouts captured at physical 1440/390 × 100%/125% (CSS viewports 1440/1152/390/312); dialog and start button reachable and inside the viewport in all four.
- **Cross path (parent 30716)**: admitted ordinary epoch0 item 1 (admission revision 1, projection `ERROR`) and owned REPAIRED child run 30717/item 4 (epoch 30717, admission revision 0, projection `ERROR`). Both opened the real eight-step view (8 steps each) with the source reader open and `analysis POST = 0`; ordinary reprojected via “仅重试候选同步”, child via public `POST /api/discovery/rechecks/30717/sync-retry` (200); both reached `PROJECTED`, revisions stayed 1 and 0, run/item/epoch stayed 30716/1/0 and 30717/4/30717, provider calls 19 → 19 → 19, raw writes 3 → 3. Rejections: ordinary-run+child-item, child-run+ordinary-item, unowned-ordinary each 404, with item state unchanged afterwards. `stepsStatus` [200 ×6].

## Deviations

- None to the authorized file scope or behavior; only the four Authorized Files changed. No app.js, shared test library, CSS/DOM, Recheck/Query/Repository, migration, plan, ledger, or report was modified.
- `execute-p` helper `scripts/worktree_identity.py` fails at line 30 on a stale unrelated Codex publish registration; worktree identity recorded manually from `git rev-parse` and every git op run with `-C`/cwd on this worktree (same deviation as the prior epoch).
- Command 3 retains the pre-existing optional layout-corpus skip (1); disclosed, not converted to PASS.
- Command 14 server classpath reused the deterministic dependency classpath from the unchanged `pom.xml` (124 jars, all present); it is a build input, not fix evidence. Screenshots/HTTP/DOM evidence is fresh in `REPAIR_TMP`.
- Production #30711 ENFORCE, live ES/ORCID/Apollo and human acceptance remain outside this execution; no push/merge/release/deploy/production write.

## Clean-state evidence

- Plan SHA-256 recomputed unchanged after all edits and commits: `50cc5a2fceedd09541a1efb080894347e7714262285fcea77f0acf52d7bfbfc9`.
- Frozen input hashes unchanged (see above); original bytes directory `/Users/lukai/.local/share/paper-reprocess-30711` read-only (493 files, not modified).
- `git diff --name-only 7e131474..HEAD -- src scripts src/test/js` was empty before editing (no product drift).
- Product commit `0b3c707147e8afa8ce4fe4002cb83752f28da0c8` contains exactly the four Authorized Files (subject `fix(discovery): bind evidence source and isolate pending dialog Escape`).
- After this file is appended, only `docs/plans/review/paper-reprocess-master/repair-execution.md` remains for the docs-only evidence commit.
