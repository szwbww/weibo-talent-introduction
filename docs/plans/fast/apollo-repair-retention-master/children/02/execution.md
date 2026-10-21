# Child 02 — Execution log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md`
Plan SHA-256: `13e24fb1c83a1035ba58a902642833947755c7c79320fc5d474ddb7c819b6b16`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md@13e24fb1c83a1035ba58a902642833947755c7c79320fc5d474ddb7c819b6b16`
Execution epoch: NEW
Approval basis: current child02 assignment, exact brief and r2 child plan; no amendments.
Executor: ApolloRetention02, using execute-p; test-fixture corrections constrained by fix-v mechanical-repair rules.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`
Target branch: `fast/apollo-repair-retention-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master@fast/apollo-repair-retention-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`
Pre-execution code SHA / supplied child_base_sha: `4c2339e13d90d06735dd00edeaaefeede845ed7c`
Pre-execution HEAD (later evidence-only checkpoint): `5d9896057db1ade2cee249c3e198897538bb2984`
Post-execution code SHA: `b2d0666118224e119b95fd02b57d6b25a6fd7bec`
Product commit subject: `feat(fast-p): implement 02`
Evidence HEAD at handoff: `b2d0666118224e119b95fd02b57d6b25a6fd7bec`; this execution log remains for the controller's separate evidence commit.
Implementation boundary: supplied child_base_sha through the post-execution code SHA; this writer's product commit changes exactly the nine authorized source/test files. No fast-p artifacts are in the product commit.
Pre-existing unrelated working-tree modification: `docs/plans/fast/apollo-repair-retention-master/ledger.md`; preserved and excluded from staging/commit.

### Task Status

| Requirement | Status | Implementation and execution evidence |
|---|---|---|
| I-1: credits are a channel outage, not an eligibility verdict | IMPLEMENTED | Worker persists the completed HTTP attempt, reschedules RETRY_WAIT with NULL outcome/NONE route, retains evidence/cursors and transient_attempts, and does not set authBlocked. Runtime smoke asserts these persisted fields and the original failed attempt remains unchanged. |
| I-2: persistent single DETAIL probe | IMPLEMENTED | DETAIL guard executes inside the existing Apollo GET_LOCK. Current locking ledger reads reject pre-fault late successes, compute max(fault completion, latest post-fault reservation)+1800 seconds, and count unfinished reservations. Repository IT exercises two concurrent callers, reconstructed repository, a crashed probe window, exact retryAt, successful reopening, non-OK HTTP200 rejection, and independent SEARCH/ORCID permits. |
| I-2: worker consumes retryAt and other paths continue | IMPLEMENTED | Reservation.retryAt flows through BudgetDeferred into reschedule. Mockito execution asserts the exact deadline and no HTTP completion/billing. Runtime smoke exercises a second blocked item with requestCount=0, real ORCID client calls, DIRECT_CLEAR, and retry of an existing projection during the DETAIL outage. |
| I-3: independent bounded historical ERROR recovery | IMPLEMENTED | New requeueErrorsForCreditRecovery transaction checks settled run, frozen item/hash/admission equality, protections, final APOLLO DETAIL422 classification, valid saved APOLLO_SEARCH search/query cursor and unfinished detail candidate. It queues without rewriting evidence or stage. Tests cover operator HOLD, FACT_CHANGED, stale admission, damaged evidence, missing queries, disallowed stages, completed candidate, wrong final result, duplicates and >100 inputs. |
| I-3: CLI contract and legacy isolation | IMPLEMENTED | apply-errors accepts only manifest/hash options, validates the existing restricted frozen files and absolute manifest path, uses original evidence rather than expected(before), and writes person-free QUEUE_ONLY receipts. apply-errors.json is stored in a unique 0700 per-application report directory with 0600 file permissions so repeated receipts are immutable. Existing apply still rejects ERROR and retains its DECIDE-only PENDING behavior. |
| I-4: old ledger/billing/ownership preserved | IMPLEMENTED | Recovery SQL changes only the authorized queue-state fields and generation; no attempts, request_count, run/batch, admission or ES writes. Repository IT compares full attempt lists and complete before/after item values, races repeated recovery, and verifies only a subsequent reservation increases request_count. CLI test verifies frozen before/manifest bytes and request counts remain unchanged. |
| Chinese reason labels | IMPLEMENTED | QueryService maps APOLLO_CREDITS_EXHAUSTED and CREDITS_WAIT to “额度不足，等待恢复”; focused test asserts both labels. |

### Environment

All successful Maven commands used this exact prefix:

```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44
```

Environment checks: `java -version` exited 0, reporting Zulu OpenJDK 11.0.32.1; `docker info --format '{{.ServerVersion}}'` exited 0, reporting 29.8.2. Testcontainers started disposable MySQL 8.0.36 containers; Docker unavailability was not skipped.

### Commands and Results

| Exact command after the environment prefix | Exit / observed result | Evidence |
|---|---|---|
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test` | 0; BUILD SUCCESS; 28 tests, 0 failures, 0 errors, 0 skipped | Finished 2026-10-21 13:02:58 +0800; session output `artifact://64`. Worker 13, ReplayTool 6, QueryService 9. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test` | 0; BUILD SUCCESS; 16 tests, 0 failures, 0 errors, 0 skipped | Finished 2026-10-21 13:06:10 +0800; session output `artifact://66`. Real concurrent MySQL transactions and database locks exercised. |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true '-Dtest=DiscoveryVerificationWorkerTest#runtime credit recovery smoke restarts resumes remaining details and commits through normal gate' surefire:test` | 0; BUILD SUCCESS; 1 test, 0 failures, 0 errors, 0 skipped | Finished 2026-10-21 13:06:49 +0800; session output `artifact://68`. Separate final-state changed-runtime-path smoke using the freshly compiled classes. |
| `git diff --check` | 0; no whitespace errors | Before staging the product commit. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md` | 0; unchanged canonical path/hash | Initial and pre-handoff checks returned the SHA-256 above. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master --expect-branch fast/apollo-repair-retention-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master` | 0; exact root/branch/git-dir matched | Repeated before git add and git commit; target identity unchanged. |
| `git commit -m 'feat(fast-p): implement 02'` | 0; nine source/test files, 480 insertions and 9 deletions | Commit `b2d0666118224e119b95fd02b57d6b25a6fd7bec`; session output `artifact://70`. |
| `git rev-parse HEAD && git merge-base --is-ancestor HEAD fast/apollo-repair-retention-master` | 0; HEAD is the product SHA and reachable from the target branch | Same commit command's post-commit gate. |

Fresh Surefire report filesystem timestamps were checked explicitly (all +0800): ReplayTool and QueryService 13:02:17, RepositoryIT 13:06:05, Worker 13:06:48. The separate smoke intentionally overwrote WorkerTest's report with its single selected method; the preceding full 13-method Worker execution remains evidenced by the successful 28-test command output.

### Earlier Failures and Bounded Corrections

- An initial Maven invocation without a per-call environment prefix exited 1 before tests because the shell did not retain the previous export and used Java 25.0.1. The command was rerun with the explicit required prefix; no project configuration was changed.
- The first correctly configured focused run exited 1: 28 tests, 2 failures, 3 errors, 0 skipped (`artifact://61`). Root causes were new test-fixture wiring: Kotlin-null-unsafe Mockito matchers (also poisoning the following test), missing aggregate-map stub, missing broadComplete=true in the smoke's frozen search, and an incorrect expectation about the unchanged legacy apply protection-check order.
- One mechanical test-only correction set fixed these fixture/stale-assertion errors without changing production behavior or weakening approved criteria. The final full focused run, database IT and standalone smoke all passed afterward. No behavioral repair round or plan amendment was created.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt` — CREDITS_WAIT and optional reservation.retryAt.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — persistent DETAIL fault/probe guard and independent bounded ERROR recovery CAS.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — credit RETRY_WAIT and exact retryAt propagation.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt` — apply-errors command, immutable original-stage input and restricted receipts.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — localized credit-wait reasons.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` — persistent/concurrent permits and recovery protections/history.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — exact deferral deadline, persisted outage semantics and end-to-end changed-path smoke.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt` — real freeze/load/recovery, immutable bytes, idempotency, protections and permissions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — both recovery reason labels.

Workflow evidence only: this `execution.md` append; excluded from product commit. No other docs, frontend, schemas, mappings, production data or later children were modified or implemented.

### Smoke Evidence and Limits

The standalone smoke used the real Worker, HTTP client/classifier, transactional MySQL repository, prepare/commit admission policy/Gate and ES projection writer; HTTP/ES transports were the existing MockRestServiceServer fixture. It observed:

1. Explicit DETAIL422 insufficient credits → one completed error attempt → RETRY_WAIT; no outcome/admission and no transient-attempt increment.
2. Worker reconstruction and a second item during the same window → no additional DETAIL wire request, no request_count increment, exact shared retry deadline.
3. During the outage → real ORCID client requests, DIRECT_CLEAR and successful retry of a previously failed admitted projection.
4. Clock advances 1800 seconds → one probe returns HTTP200 → remaining detail executes → normal Gate/Commit produces APOLLO_CLEAR and PROJECTED.
5. Detail wire sequence exactly `person-1` (422), `person-1` (probe200), `person-2` (remaining200); recovered item's request_count=3, both detail IDs acknowledged, original failed attempt unchanged, run/batch unchanged.

Concurrency and crash-window assertions were separately executed against real database locks in RepositoryIT. The restart smoke reconstructs Worker/repository state; it is not an OS-level multi-process deployment test. No live Apollo request, production recovery, production deployment or factual historical-264 success claim was made. Existing historical attempts/samples were not touched. A-1/A-2/A-3 human acceptance remains PENDING, explicitly deferred.

### Downstream Interfaces

- `VerificationAttemptReservation.retryAt: Instant?` and `VerificationBudgetReason.CREDITS_WAIT`; Worker consumes the deadline end to end.
- `DiscoveryVerificationRepository.requeueErrorsForCreditRecovery(executionId, expectedItems, now)` is a short transaction-only queue seam and does not bypass prepare/commit.
- CLI `apply-errors --manifest <absolute-path> --manifest-sha256 <sha256>`; report basename `apply-errors.json`, layer QUEUE_ONLY, itemId/status receipts and queued count, in immutable secured per-invocation directories.
- Existing PENDING apply/expected/replayEvidence behavior, leases, authentication latch, Apollo quota bypass, SEARCH/ORCID behavior and HTTP-attempt accounting remain intact.

### Deviations and Freshness

- Plan amendments/deviations: none.
- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, exact root/branch/git-dir.
- Product commit reachable from target branch: YES.
- Required commands run freshly in this invocation: YES.
- Historical evidence used only as baseline: YES.
- Required runtime smoke: executed and passed; not a production or human-acceptance claim.
- Remaining blocker: none known for execution handoff.
- Next action: independent verify-p for child02; human acceptance and any production action remain separately authorized work.
