# Child 02 — Verification log

## Light Verification: LIGHT_PASS
Child: 02 — docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md
Boundary: 4c2339e13d90d06735dd00edeaaefeede845ed7c..b2d0666118224e119b95fd02b57d6b25a6fd7bec
Verifier: ApolloVerify02

Timestamp: 2026-10-21T13:17:32+08:00 (epoch1, attempt1; fresh required-command completion).

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only 4c2339e13d90d06735dd00edeaaefeede845ed7c b2d0666118224e119b95fd02b57d6b25a6fd7bec` shows precisely the plan's nine authorized source/test files under `src/`. Other boundary entries are interleaved fast-p evidence only: children/01/{execution,fix-log,verify-log}.md and ledger.md, explicitly excluded from the product whitelist by dispatch. No schema/frontend/ES mapping change. |
| Plan and invariants | PASS | Repository.kt:101–152 independently locks frozen run/admission/items, checks full item/hash/admission equality, settled run/no active items, protections and eligibility, then changes queue fields/generation only; :155–181 rejects malformed/missing/invalid-stage or completed DETAIL cursors and requires the final APOLLO DETAIL422 classification. :440–497 keeps Apollo GET_LOCK and quota bypass, checks only DETAIL's persistent explicit-credit fault, rejects pre-fault late success, counts unfinished probe reservation and derives the 1800-second deadline. Worker.kt:180–194 retains transient count and cursor on explicit credit failure; :357–373 consumes retryAt. RepositoryIT.kt:324–377 covers concurrent single permit, restart, crash window, old late success, non-OK200 and independent SEARCH/ORCID; :401–445 covers CAS/history/protections. WorkerTest.kt:246–324 exercises real Worker/client/repository/Gate/Commit with mocked transports: 422→wait→reconstruction→probe200→remaining DETAIL→APOLLO_CLEAR/PROJECTED, plus ORCID/direct/projection continuation and unchanged old attempt. Fresh required runs passed these assertions. Normative E-6/E-7 and master constraints inspected. |
| Required commands | PASS | Both exact plan commands ran once freshly with explicit environment below. Focused command exit 0, 28 tests/0 failures/0 errors/0 skipped (artifact://76, finished 13:13:48+08:00); MySQL command exit 0, 16/0/0/0 (artifact://78, finished 13:17:32+08:00). Counts match execution.md's successful 28/16 baseline; no baseline failure remains. Surefire XML mtimes checked: ReplayTool/QueryService 13:12:59, Worker 13:13:42, RepositoryIT 13:17:26 (+0800), all fresh to this invocation. Docker-backed required cases actually ran, not skipped. |
| Downstream interfaces | PASS | Domain VerificationAttemptReservation.retryAt: Instant? and CREDITS_WAIT are carried through Worker BudgetDeferred/reschedule. Repository.kt:101 exposes independent requeueErrorsForCreditRecovery(executionId, expectedItems, now), retaining original evidence/stage and normal Worker prepare/commit authority. ReplayTool.kt:52–79 accepts apply-errors with only manifest/hash options and requires absolute manifest; :190–202 uses original evidence rather than legacy expected(), writes secured unique per-application apply-errors.json with QUEUE_ONLY, receipts and queued count. ReplayToolTest.kt:114–143 verifies legacy apply isolation, stale/repeated/protected manifests, unchanged frozen bytes/attempts/counts, no provider calls, receipt permissions and option restrictions. QueryService maps both reason codes to the approved Chinese label, exercised by its focused test. |

Fresh environment prefix for both required commands:
```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44
```
Exact required commands:
```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test
```

Scope of conclusion: lightweight four-gate inspection only. Fresh smoke is included in WorkerTest, not a live Apollo/OS multi-process deployment or historical production recovery claim. Human A-1/A-2/A-3 remains deferred; no production data or product/tests/plans/index/commits/branch modified by verifier.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
