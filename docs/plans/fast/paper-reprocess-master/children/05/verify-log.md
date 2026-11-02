# Child 05 Verification Log

## Epoch 2 — Attempt 1 — PaperFrontendVerifier05

## Independent Verification: FAIL

Child: 05 — docs/plans/2026-11-01/paper-reprocess-05-frontend.md
Plan identity: commit:d678780155fc4fac5ecc4896135806729efaee2f (A10/A11)
Plan SHA-256: b329427077b2011fd2b8d1b828698351eb86c362d91d5dd519e354f47657bd5b
Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
Master SHA-256: dd89f8c5807824dcacb59a149b302dc6f98d54dd9c8771403dd751968b6358fb
Brief SHA-256: f2f3055b4712323110bd7d4fb81e6a0ed1e5004fbba8f7bd7623b599418e4889
Product commit: ccdb6de43796ec7e65e844840932b1cf5e7f02a0 (`feat(fast-p): implement child05 paper reprocess UI`)
Product-only boundary: 6676d6b..ccdb6de, exactly the nineteen authorized paths
Base: 736535ce9487782e52a73af6cb52517fa799b194
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Branch: fast/paper-reprocess-master
Worktree HEAD at verification: 4bbcfa6916bdd17348e429c247ccec5088a26204 (product ccdb6de plus controller evidence/handoff)
Verifier: PaperFrontendVerifier05
Verification timestamp: 2026-11-02T04:07:18Z

Fresh independent child verification only. The complete approved plan, master plan, A10/A11 ledger rows, child05 brief, epoch1/epoch2 execution log, fix log, and the committed product/evidence boundary were read. The prior execution log was used as disclosure context, not as an independent verdict. Product/test/plan/ledger files were not modified; the only repository write owned by this verifier is this append. No review-fast-p, review-p, verify-p, repair-p, push, merge, release, production write, Apollo/ES provider call, or browser rerun was performed.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 6676d6b ccdb6de` returned exactly the nineteen authorized files and no other product/test/plan path. Both new files are the authorized UI module and its focused JS test. No migration, controller, worker, or shared schema was added. Base ancestry `736535ce...` is reachable from ccdb6de. The worktree was clean before and after the fresh commands. |
| Plan and invariants | FAIL | CSS/DOM/cache/read-only wording, pagination, scope ownership, missing-source predicate, eight-step opener, and pane visibility are strong. Two P1 plan failures remain: F-01 (production proposal counts are never populated/displayed) and F-02 (a reopened confirmation can replay an ENFORCE submission while displaying AUDIT). F-03/F-04 are bounded P2 recovery/reader defects. |
| Required commands | PASS | Fresh `node --check src/main/resources/static/discovery-paper-reprocess.js` exited 0. Focused `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` passed 41/41. Full `node --test src/test/js/*.test.js` passed 2032/2032 (354 suites). Fresh JDK11 `mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest -DpaperReprocessIt=true -DmysqlIt=true` exited BUILD SUCCESS; fresh surefire reports show 12 tests/0 failures/0 errors/0 skipped for the repository integration class and 6 tests/0 failures/0 errors/0 skipped for the service class; the integration class actually executed against testcontainers MySQL (60.5 s). |
| Downstream interfaces | FAIL | The exported existing eight-step `openDetail(runId,itemId,trigger)`, real-run/item identity, pane reveal/restore, 121-paper scope, 20-row independent pagers, and textContent rendering are wired. The authoritative summary interface does not deliver the plan's proposed counts in production (F-01), and the confirmation interface can submit a different mode than the one displayed (F-02). |

### Findings

#### F-01 [P1] Proposed `wouldCreate`/`wouldRepair` counts are dead in every production path

Plan requirement: child I-3 says “AUDIT实际业务数0，拟补录/拟补齐单列”; implementation step 4 says AUDIT displays `wouldCreate/wouldRepair`; A-1 expects “实际新增/补齐/准入0；拟变化另外显示”.

Evidence:
- `DiscoveryPaperReprocessService.kt:319-320` is the only production writer of `wouldCreate`/`wouldRepair`; it writes them for AUDIT together with `candidateOnly=true` and `businessExistenceEvaluated=false`.
- `DiscoveryPaperReprocessApplyService.kt` (ENFORCE) never writes `wouldCreate`, `wouldRepair`, or `businessExistenceEvaluated`. A repository-wide grep for `businessExistenceEvaluated` returns only the four read predicates in `DiscoveryPaperReprocessRepository.kt:539-545` and the single false-valued AUDIT write at `DiscoveryPaperReprocessService.kt:320`.
- `DiscoveryPaperReprocessRepository.kt:539-546` requires `businessExistenceEvaluated=true` and a boolean `wouldCreate/wouldRepair` before counting; `proposal()` at `:585-586` additionally returns null unless the known count equals `targets`. Therefore AUDIT (false) and ENFORCE (field absent) both yield null.
- Fresh runtime evidence: `runtime-9502.json` is a real UI-created AUDIT run (121 papers, 2 targets) and has `wouldCreate=null`, `wouldRepair=null`, `unchanged=null`, `protected=null`. `rp05-audit-complete-1440-100.png` visibly renders “拟补录 — / 拟补齐 —”.
- The 9600 fixture values (`wouldCreate=1`, `wouldRepair=0`) come from manually inserted target result JSON, not from the production apply path.
- Tests codify the gap instead of covering the real writer: `DiscoveryPaperReprocessRepositoryIntegrationTest.kt:290` manually sets `businessExistenceEvaluated=true` for the ENFORCE SQL test, `:324-326` manually sets false for AUDIT and asserts null, and `DiscoveryPaperReprocessServiceTest.kt:56` asserts null. No test runs a real AUDIT/ENFORCE writer through `summary()`.

Impact: the user cannot see proposed new/repaired counts for AUDIT (and real ENFORCE rows show “—” for 拟补录/拟补齐), so the plan’s explainability/acceptance requirement is not implemented. This is a plan mismatch, not a disclosed environment limit.

#### F-02 [P1] Reopened confirmation can replay a pending ENFORCE submission while the UI shows AUDIT

Plan requirement: I-2 “默认AUDIT；ENFORCE明确补录补齐并自动重审”; I-4 “AUDIT不写业务”; execution requires an explicit mode choice.

Evidence:
- `discovery-paper-reprocess.js:156` unconditionally resets the confirm radio to `AUDIT` on every `openConfirm()`.
- `:173-176` reuses `requests.get(id)` when present and creates the request object (including `mode`) only once. `close-confirm` (`:266`) and `suspend()` (`:280`) do not clear `requests`.
- Scenario: an ENFORCE submit fails or times out before a run is found; the pending object remains with `mode=ENFORCE`. The user closes and reopens the confirm; the dialog shows AUDIT selected. Clicking start reuses the pending object and posts ENFORCE. The backend can then perform补录/补齐/自动重审 while the visible confirmation said AUDIT.
- The focused test “uncertain submission repeated click retains original ENFORCE mode” covers repeated clicks in the same dialog, not close/reopen or hide/show; no test asserts the visible radio matches `pending.mode`.

Impact: a user can trigger business writes (ENFORCE) while the confirmation displays the non-writing AUDIT mode, violating the explicit-mode/audit-no-write invariant. This is a safety defect, not a style preference.

#### F-03 [P2] Retained pending key/hash can wedge submission after a deterministic scope/version rejection

Evidence: `discovery-paper-reprocess.js:173-176` keeps `pending.expectedScopeHash` from the first attempt; `openConfirm()` (`:155-167`) refreshes `preview` but never updates or clears the pending object. If `create` returns `SCOPE_CHANGED` (source archive changed) or `VERSION_CHANGED` (parser version changed), every retry replays the stale hash. The catch path (`:185-193`) reports “提交结果未知；重试仍使用同一请求标识及模式” and retains the pending object; `suspend()` does not clear `requests`. Only a full page reload clears it. The user therefore cannot recover through the UI after a deterministic rejection.

Impact: recoverable UI flow is wedged after a legitimate 409/version change; the message also mislabels a deterministic rejection as an unknown submission. P2 because no data corruption and the primary happy path is unaffected.

#### F-04 [P2] Reprocess “查看原文” rejection is unhandled and the new reader reference bypasses the fixed-SHA/error path

Evidence:
- `discovery-paper-reprocess.js:254` attaches `openPaper({recordId, attemptId}, original)` without a catch.
- `discovery-papers.js:299-302` rethrows reader errors when `attemptId != null`; a deleted/missing attempt therefore produces a rejected promise with no visible Chinese error in the reprocess detail.
- The new `openPaper` branch (`discovery-papers.js:306-310`) accepts `{recordId, attemptId}` and calls `reader(...)` with `ref=null`; it does not pass `itemEvidence`’s `bodyHash` and therefore does not perform the existing fixed-record `bodySha256`/readability checks (`:283-286`). `itemEvidence` at `discovery-paper-reprocess.js:225-228` discards `ref.bodyHash`.
- The focused test only checks attempt existence and invalid attempt IDs; it does not assert the missing/deleted-attempt UI error or fixed-body SHA verification for reprocess references.

Impact: source-missing/deleted cases can fail silently for the user, and the reprocess reader lacks the fixed-source integrity check used by the existing archive reader. P2 (read-only display; no business write).

### Observations (not counted as additional findings)

- O-01: both `rp05-running-1440-100.png` and `rp05-running-1440-125.png` show the archive table scrolled below the batch/run area; neither file visibly shows the running run panel or the ENFORCE 9600 state. The claimed running-panel state is actually supported by `rp05-error-500-1440.png` and `rp05-six-steps-390-100.png`, not by the files named “running”.
- O-02: `rp05-confirm-1440-100.png` shows the ENFORCE 9600 run behind the dialog while the start button is enabled and the hint says “默认仅对照”, implying `preview.activeRun` was null. This is consistent with the disclosed synthetic 9600 row lacking an active-key/real lifecycle, and with the absence of a focused activeRun-disable test; it weakens the browser proof of the active-run/recovery branch, not the code’s explicit `Boolean(preview.activeRun)` branch.
- O-03: The disclosed limits are fairly supported: the 401 screenshot is the application-wide login redirect and the component 401 stop/preserve path is unit-tested; JDK 11.0.15 tests versus 11.0.32 runtime is an environment constraint; 9502 is a real UI-created production-scheduler run, while 9600/9700/item 1 and the empty/eight-step details are explicitly synthetic. These are not treated as PASS substitutes or failures by themselves.
- O-04: The CSS contract is byte-for-byte exact (plan block 5444 bytes equals `styles.css` block 5444 bytes), the S-6 blocks are present, all 20 index resources share `20261102-paper-reprocess`, and the new script precedes `discovery-papers.js`. No inline style/innerHTML was found in the new module. Pre-existing global CSS is only appended to.
- O-05: `makeUrl` and `onStatus` are accepted by the reprocess mount but unused; minor dead seam, no behavioral impact observed.
- O-06: `targetTotalKnown` remains false when any paper errors even after TARGET enumeration is complete; this is conservative “known-so-far” behavior, not proven wrong by the plan’s acceptance cases.

### Commands Run This Attempt

| Command | Result |
|---|---|
| `node --check src/main/resources/static/discovery-paper-reprocess.js` | exit 0 |
| `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | 41 passed / 0 failed |
| `node --test src/test/js/*.test.js` | 2032 passed / 0 failed / 354 suites |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest -DpaperReprocessIt=true -DmysqlIt=true` | BUILD SUCCESS; 12 integration tests / 0 failures / 0 errors / 0 skipped; 6 service tests / 0 failures / 0 errors / 0 skipped |
| Python extraction/comparison of the plan S-5 block and `styles.css` contract block | exact byte equality, 5444/5444 bytes |
| `git diff --name-status 6676d6b ccdb6de`; `git merge-base --is-ancestor 736535ce... ccdb6de`; `git status --short` | exactly 19 authorized paths; ancestry 0; clean worktree |
| Browser evidence review (screenshots + `runtime-9502.json`/`9600.json`/`apollo.json`/`runs.json`) | reviewed, not rerun; consistency notes O-01/O-02 above |

### Verdict

**FAIL** — two P1 plan failures (F-01, F-02) and two bounded P2 defects (F-03, F-04) remain in authorized child05 code/read-only seams. The fresh required commands and CSS/DOM/readonly/missing-source/eight-step portions pass, and the disclosed environment limits are not the cause of the failure. A bounded repair plan should first reconcile the pending request object with the displayed mode and current preview hash, and make the authoritative summary expose the proposed counts the plan requires (including the production writer contract), then add focused regressions. No product/test/plan/ledger repair or review-fast-p was performed here.

## Epoch 2 — Attempt 2 — PaperFrontendVerifier05

## Independent Re-verification: LIGHT_PASS_WITH_NOTES

Child: 05 — docs/plans/2026-11-01/paper-reprocess-05-frontend.md
Plan identity: commit:d678780155fc4fac5ecc4896135806729efaee2f (unchanged; A10/A11)
Plan SHA-256: b329427077b2011fd2b8d1b828698351eb86c362d91d5dd519e354f47657bd5b
Master SHA-256: dd89f8c5807824dcacb59a149b302dc6f98d54dd9c8771403dd751968b6358fb
Product repair: 46bcb8a3875a6957ed18494200d158de09dd0f38 (`fix(fast-p): repair child05 verification findings`), seven authorized files
Evidence: aec7617c4d22d5ee0efe46ff30c8b924fcce92de; ledger a8b56578d80c5a5450365a6ca8b40f7fa77aa8e0; 9502 evidence deletion e6ced758bccd03d09e20f13c2ff041abfab65a83
Base: 736535ce9487782e52a73af6cb52517fa799b194
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Branch: fast/paper-reprocess-master
Worktree HEAD at verification: e6ced758bccd03d09e20f13c2ff041abfab65a83
Verifier: PaperFrontendVerifier05
Re-verification timestamp: 2026-11-02T04:47:00Z

Fresh independent re-verification of the repair boundary only. The unchanged plan/master, A10/A11, brief, attempt1 report, repair diff, new execution evidence and runtime JSON were read. Product files were not edited; only this append is owned by the verifier. No review-fast-p/review-p/verify-p/repair-p, push, merge, release, production write, Apollo/ES call, or browser rerun was performed. The repair touched seven files, all inside the nineteen-file authorization; `git diff --name-status 87873b0 46bcb8a` returned only those seven.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Repair files: repository summary, reprocess UI, papers reader seam, and four directly affected tests. All are within the nineteen authorized paths; no migration/controller/worker/shared schema change. Working tree clean before this append. |
| Plan and invariants | PASS with notes | F-01..F-04 from attempt1 have direct code/test/runtime fixes below. One P2 recovery edge (F-05) and evidence-consistency notes remain; they do not reproduce the explicit-mode/audit-no-write or dead-proposal defects. |
| Required commands | PASS | Fresh `node --check` exit0; focused 44/44; full 2035/2035; JDK11 Maven BUILD SUCCESS with fresh surefire 12/0/0/0 integration and 6/0/0/0 service, real testcontainers MySQL; CSS contract still byte-exact 5444/5444. |
| Downstream interfaces | PASS with notes | Real AUDIT writer -> repository summary -> JSON now yields wouldCreate=1/wouldRepair=1; real ENFORCE UI says proposals are AUDIT-only and uses actual counts. Pending mode is visible/locked and deterministic 4xx uses a fresh UUID. Reader SHA/catch and eight-step/pane seams remain wired. F-05 is a 5xx recovery edge. |

### Resolved Attempt-1 Findings

- **F-01 (P1) resolved.** `DiscoveryPaperReprocessRepository.kt:539-546` now counts `wouldCreate`/`wouldRepair` booleans without requiring `businessExistenceEvaluated=true`. `runtime-9003.json` is a real UI-created AUDIT writer run (121 papers, 2 targets) with `wouldCreate=1`, `wouldRepair=1`, actuals 0. `paper03WholeSourceScenario` now asserts the positive values from production rows. The UI displays proposals for AUDIT and for ENFORCE explicitly states “正式补录按实际结果统计；拟变化仅对照模式提供”, so the earlier misleading “—” claim is gone. The focused tests and real-writer scenario pass.
- **F-02 (P1) resolved.** `discovery-paper-reprocess.js:159-163` restores the pending mode and locks both radios; `start` locks immediately on pending creation; the hint names the pending mode. The new test closes/reopens an uncertain ENFORCE submission and asserts ENFORCE checked, AUDIT unchecked, both disabled, and the next POST reuses the same key/mode. `rp05-pending-enforce-1440.png` shows the locked ENFORCE state. No visible-AUDIT/replayed-ENFORCE counterexample remains.
- **F-03 (P2) resolved for deterministic 4xx.** The start catch now treats 4xx as rejected-before-durable, deletes the pending key/hash, unlocks the mode, re-reads preview, and the new test proves a fresh UUID after 409. The old stale-hash wedge on `SCOPE_CHANGED`/`VERSION_CHANGED` is closed.
- **F-04 (P2) resolved.** `itemEvidence` carries `bodySha256`; `openPaper` routes SHA-bearing reprocess refs through the existing fixed archive-record SHA/readability checks; invalid SHA rejects; the click handler catches and writes the Chinese error to `detailSubtitle`. New tests assert `PAPER_SHA_CONFLICT`, invalid SHA, and visible rejection. The hash-less fallback remains only for legacy refs with no SHA and is still caught by the UI.

### Remaining Finding

#### F-05 [P2] Deterministic 5xx ENFORCE rejection retains the pending key and locks the mode

`discovery-paper-reprocess.js:196-212` classifies only 4xx as deterministic. A 503 from `create` (`APPLY_NOT_AVAILABLE` / `VERIFICATION_UNAVAILABLE`, or controller `DataAccessException`) takes the non-deterministic branch: the pending object and its `ENFORCE` mode are retained, `lockMode(true)` remains, and the user gets “提交结果未知；重试仍使用同一请求标识及模式”. The retry can repeat the same 503 indefinitely; switching to AUDIT requires a full page reload. The plan’s ENFORCE flow is explicit and the backend’s 503 is raised before a durable run for these codes, so this is a recoverable UI state, not a write bug. P2.

### Observations

- O-07: Browser zoom evidence is duplicated. SHA-256 equality confirms `rp05-audit-complete-1440-100.png == ...-125.png`, `rp05-confirm-1440-100 == ...-125`, `rp05-running-1440-100 == ...-125`, `rp05-empty-1440-100 == ...-125`, and `rp05-six-steps-390-100 == ...-125`. These pairs cannot demonstrate 100%/125% layout separately, although other pairs (confirm-390, eight-step, reader) differ.
- O-08: Execution log lines 117/119 still cite 9502 and null proposals, while 9502 JSON was deleted and repaired runtime is 9003 with proposals 1/1. Line 133 still claims Apollo revision 1 / searchEnabled=true, while `runtime-apollo.json` and the new confirm screenshot show revision 0 / DEPLOYMENT_DEFAULT / searchEnabled=false. The product GET/display path is covered, but the new browser evidence no longer demonstrates the A-4 saved search=true/detail=false setting.
- O-09: `rp05-audit-complete-1440-100/125.png` shows the run card and 121/121/0/0/0/0 stats but not the summary line; positive “拟补录 1 / 拟补齐 1” is proven by `runtime-9003.json`, the repository/service tests, and code inspection, not by a visible screenshot. The focused UI fixture still uses `wouldCreate:null`, so no UI test asserts positive AUDIT proposal text.
- O-10: The F-01 summary documentation still says null proposals mean “not evaluated”; after the repair AUDIT can be non-null by design. Minor wording debt.
- O-11: The disclosed 401/JDK 11.0.32/synthetic 9600 limits remain fairly disclosed and are not the cause of any remaining note.

### Fresh Commands

| Command | Result |
|---|---|
| `node --check src/main/resources/static/discovery-paper-reprocess.js` | exit 0 |
| `node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js` | 44 passed / 0 failed |
| `node --test src/test/js/*.test.js` | 2035 passed / 0 failed / 354 suites |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest -DpaperReprocessIt=true -DmysqlIt=true` | BUILD SUCCESS; 12 integration / 0 failures / 0 errors / 0 skipped; 6 service / 0 failures / 0 errors / 0 skipped; fresh report timestamps 12:46; real MySQL fixture |
| Plan S-5 vs `styles.css` extraction | exact equality, 5444/5444 bytes |
| `git diff --name-status 87873b0 46bcb8a`; `git status --short` | exactly seven authorized repair paths; clean worktree |

### Verdict

**LIGHT_PASS_WITH_NOTES** — the two P1 defects are closed and F-03/F-04 are fixed with focused regressions and fresh command proof. One P2 5xx-mode recovery edge (F-05) and evidence-consistency notes (O-07/O-08/O-09) remain; they should be fixed in a future bounded change or accepted explicitly. No product/test/plan/ledger repair, review-fast-p, or aggregate review was performed here.
