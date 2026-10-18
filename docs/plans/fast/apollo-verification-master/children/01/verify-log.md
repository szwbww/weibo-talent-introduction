# Child 01 independent light verification log


# Epoch 1 — Attempt 1
Timestamp: 2026-10-18T19:43:37+08:00
Reviewed range: 3efc192faf043badc1ef27bef124a71d3120c4c5..772429192fdab1fd7ac75cb084f08f45aec5c965

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 — docs/plans/2026-10-18/apollo-01-ledger.md
Boundary: 3efc192faf043badc1ef27bef124a71d3120c4c5..772429192fdab1fd7ac75cb084f08f45aec5c965
Verifier: ApolloLedgerVerifier (fresh isolated verifier; distinct from ApolloLedgerImplementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-status 3efc192faf043badc1ef27bef124a71d3120c4c5..772429192fdab1fd7ac75cb084f08f45aec5c965` (exit 0; receipt artifact://47) identifies the six authorized product/test additions listed below; all other additions are frozen plans/evidence or fast-p orchestration, explicitly exempted by dispatch. `git log -3 --format=...` identifies product commit 772429192fdab1fd7ac75cb084f08f45aec5c965. No existing discovery, review, admission, contact/mail, application.yml or frontend product file is modified. |
| Plan and invariants | PASS | Read child brief first, complete child plan, complete C-1–C-7 appendix, master invariants, audit and execution report. V158:1–75 matches C-2 three-table DDL, including binary doc-id collation, identity/request uniqueness and no foreign keys. Domain:7–156 defines explicit modes/states/results/evidence/progress/counts, positive IDs, bounds, UTC conversion and incomplete-result validation. Repository:24–68 freezes run/first owner/source/batch and closes input; :98–129, :175–229, :247–263 implement current-owner lease checks and run/item locking; :132–172 uses same-connection named-lock budget reservation, current reads, persisted attempt placeholder and transactional revision updates. Fresh MySQL IT and contract tests pass, including concurrency, stale token/generation, rollback, budget release and default-disabled startup. Detailed requirement mapping below. |
| Required commands | PASS | Fresh JDK11/OrbStack/API1.44 Maven command ran selected new tests plus existing discovery/review/admission/write-seam regressions: Kotlin 261 run, 0 failures, 0 errors, 0 skipped. Overall Maven exit 1 is solely the bound Node suite: 1694 total / 1653 pass / 41 fail / 0 skipped, identical to supplied ledger Baseline. Fresh selected discovery JS: exit 0, 70/70 pass. Fresh app.js syntax check: exit 0. Fresh explicit full Node suite: exit 1, 1694/1653/41/0, same baseline. Exact commands and receipts below. No Docker skip or baseline failure was counted as a passing test. |
| Downstream interfaces | PASS | Domain:15,32 supplies exact UNMANAGED/MANAGED_AUDIT/MANAGED_ENFORCE/UNAVAILABLE and RegistrationRecovery.recoverReserved(limit:Int):Int seams. Repository supplies every named 01 method; findManagedContexts at :80–95 enforces <=500 IDs, preserves ENFORCE precedence and propagates DB failures. C-2 fields and count keys remain available for later reporting. Read child 02/03/04/05/06 consuming requirements; 03 needs managed-context reads, 04 uses leases/budgets and optional recovery, 05 supplies recovery and registration, 06 adds read-only aggregates. Existing production dispatch is intentionally absent in child 01, not a dropped event: scoped grep found the new contracts consumed by the repository/tests only. Future repository worker/query extensions are explicitly authorized in 04/06; they are not missing 01 business integration. |

Authorized implementation paths (diff receipt artifact://47):
- src/main/resources/db/migration/V158__create_discovery_verification.sql
- src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryVerificationProperties.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerificationContractTest.kt

Requirement/invariant evidence (paths abbreviated to the filenames above):
- I-1 / C-1–C-3: migration :1–75; DiscoveryVerification.kt :7–19,34–82,85–156; DiscoveryVerificationContractTest.kt :40–75; RepositoryIT :100–115,226–238. Explicit error/null and terminal-result rules prevent unfinished rows becoming completed clear results; reserved registration errors retain null activated_at and remain outside the denominator. C-4 evidence/progress objects are separate domain structures. This child does not implement later-child rule evaluation or reporting aggregation.
- I-2 / A-1: repository :24–61,247–251; RepositoryIT :80–98 and :100–115. Fresh fixture runs 20 parallel reserves, preserves first execution/source, immutable activated batch and completed replay, and separates mode/identity keys. Duplicate registration reads back the persisted identity instead of resetting terminal state.
- I-3 / A-2: repository :98–229,253–267; RepositoryIT :117–178,180–224. Fresh tests exercise persisted lease takeover after reader reconstruction, old-token and generation rejection, exactly one accepted completion/revision increment, transaction rollback, shared day-cap concurrency, UTC rollover and named-lock release after commit/rollback. Actual isolated mysql:8.0.36 started at localhost:32777/verification_ledger and migrated through V158; fresh log artifact://52:372–376,622–624. This is direct fresh execution evidence, not reuse of the implementer test claim.
- I-4 / C-1 / N-1/N-2: DiscoveryVerificationProperties.kt :11–45 defaults enabled=false and workerEnabled=false, validates numeric/time ranges, omits key fields from toString and registers configuration in-file. ContractTest :16–37 starts without keys/recovery implementation and checks invalid configuration. Diff shows no scheduler, HTTP client, business caller, sending path or existing transactional-table alteration. Fresh existing regressions preserve admission 22/22, review 35/35 and discovery 190/190.
- Execution runtime evidence: execution.md records an isolated JDK11 jshell/MySQL driver observing `stale=false accepted=true`, owner 9001, batch 1, COMPLETED/PENDING, generation 2. Accepted as implementer execution evidence only, not independent smoke proof. Independent proof here is the fresh isolated IT/contract/regression execution. No deployment, browser session, production dependency startup, real Apollo/ORCID call or real-world discovery/review manual acceptance is claimed. Full deployment/manual A-3 remains a human acceptance activity; default-disabled behavior and unchanged legacy paths have direct code/test evidence above.

Fresh commands (all cwd = /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master):
1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,ExpertDiscoveryServiceTest,OperatorStatusWriteSeamGuardTest` — exit 1 at exec:node-test only. Kotlin 261 run / 0 failures / 0 errors / 0 skipped: IT 8, domain 5, discovery 190, review 35, admission 22, guard 1. Bound Node 1694 total / 1653 pass / 41 fail / 0 skipped. Receipt artifact://52:624,2353–2371,4724–4730,5277–5282; finished 2026-10-18T19:42:36+08:00. Admission baseline was 22 pass; no new Kotlin failure.
2. `node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js` — exit 0; 70 tests, 70 pass, 0 fail, 0 skipped (fresh tool result). The appendix future discoveryVerification.test.js does not exist, confirmed by exact-path glob; C-7 explicitly excludes nonexistent future tests. No test was fabricated or silently skipped.
3. `node --check src/main/resources/static/app.js` — exit 0 (fresh tool result).
4. `node --test src/test/js/*.test.js` — exit 1; 1694 tests, 1653 pass, 41 fail, 0 skipped, receipt artifact://51:2350–2356. Failures include resetRecipientPreview/setRecipientPreviewState extraction-sandbox errors and the meeting asset preview-key assertion, matching the supplied baseline classifications. Frontend sources/tests have no changes in the exact boundary. Baseline source: ledger.md Baseline and supplied baseline-command.log; the latter preserves an excerpt/pointer, not a replacement for fresh results.

Only these four gates were reviewed. No broad quality/security/performance review or unrelated repair was performed. No product/test/index/commit/branch/plan/evidence file other than this verification report was intentionally written; Maven generated normal build outputs while running the explicitly required commands.

### AUTO_FIX
- N/A — no proven in-scope deviation uniquely repairable within authorized files was found.

### RECORD_ONLY
- O-01 — Full Node regression remains red at the supplied pre-existing baseline (1694 total, 1653 pass, 41 fail), both standalone and Maven-bound. The affected frontend product/tests are unchanged and outside child 01 authorization. Record this existing regression debt without repairing it or treating Maven as globally green.

### Required Action
- COMPLETE_CHILD
