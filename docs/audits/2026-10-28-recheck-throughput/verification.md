# Recheck throughput verification

Baseline: `371a008`; code head: `ed16401`. Approved scope: [implementation plan](../../superpowers/plans/2026-10-28-recheck-throughput.md), documenting the user's direct authorization to optimize local main. No deployment in this task.

Verification: fix-v AUDIT_ONLY source audit, separate authorized implementation/repair passes and documentation completion. Build: PASS. Final combined tests: PASS — 76 passed, 0 failed, 0 skipped. Final outcome: PASS. Manual production acceptance: PENDING deployment.

## Implemented behavior

- Archive recovery hashes outside the whole-archive mutation lock, pins the source, then rechecks metadata/file identity before changing state. Single-flight scan; first scheduled scan delayed five minutes. Existing source integrity/deletion safeguards remain.
- Verification defaults to four lanes, configurable from one through eight. A lane claims immediately before processing; no queued whole-roster leases. Independent heartbeats, ownership/dispatch checks, auth latch and cancellation remain effective.
- Eligible academic requests share a bounded 25 ms gathering window, grouped by execution/mode/frozen evaluation time; at most eight outstanding callers. Individual guards run before dispatch and delivery. Cancellation releases waiters; incompatible requests stay separate.
- Same-paper computations reuse verified SHA + kind + typed authors/year + analyzer-version keys. At most 64 entries / 8 MiB serialized payload and eight active coalescing callers. No PDF bytes or per-person assembled evidence retained. Failed/partial computations are not retained; overflow computes uncached. Every person's frozen source, hash, binding, timestamp and provenance still validates independently.

Configuration: `talent-introduction.discovery-verification.concurrency`, default `4`, accepted `1..8`. Constructor default supplies the value; this task did not edit existing dirty `application.yml`.

## Verification evidence

| Area | Tests / result |
| --- | --- |
| Archive recovery/service/capture | 35 PASS: concurrency7 + service17 + capture11 |
| OpenAlex backoff | 45 PASS: concurrent1 + existing policy44 |
| Worker/batching initial | 22 PASS: batch5 + scheduling8 + academic8 + real MySQL child07 flow1 |
| Task2 repair | 50 PASS: ORCID7 + heartbeat1 + Flow32 + Country8 + credit/runtime2 |
| Paper reuse | 44 PASS: cache5 + evidence13 + qualification17 + analysis9 |
| Paper shutdown repair | 20 PASS: cache5 + cancellation worker1 + existing shutdown worker1 + evidence13 |
| Final combined flow suite | 76 PASS: DiscoveryVerificationWorkerTest 23, DiscoveryVerificationFlowIT 32, DiscoveryCountrySupplementFlowIT 8, DiscoveryAcademicBatchTest 5, DiscoveryRecheckAcademicEvidenceServiceTest 8; 0 failures/errors/skipped |

Counts above describe separate runs and overlap; do not sum them as unique test cases. Docker integration tests use disposable local MySQL. No selected integration tests skipped after Docker access was available. No full repository test suite or frontend tests were requested by this backend-only scope.

Build command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/bin/mvn -q test-compile -DskipTests -DskipNodeTests=true` — exit0. This full compilation preceded the final cache interruption method-body change. The exact changed cache/test files were subsequently recompiled successfully with Java11/Kotlin1.9.25; public signatures unchanged. Full compile log: `/tmp/recheck-throughput-full-compile.log`.

Final combined command: same Java11 environment, `mvn -q surefire:test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryCountrySupplementFlowIT,DiscoveryAcademicBatchTest,DiscoveryRecheckAcademicEvidenceServiceTest`. Log: `/tmp/recheck-throughput-final-tests.log`.

Controlled gains: four compatible academic callers made one lookup in `DiscoveryAcademicBatchTest`; Alice and Bob sharing one paper made one analyzer parse, with distinct individually bound evidence and equal cold/warm Alice results in `PaperVerificationEvidenceServiceTest`. Recovery latch tests prove another paper can be opened while recovery hashing is paused. These prove fewer duplicate operations / removal of demonstrated blocking, not a production completion-time estimate.

Initial paper-cache pre-change test was blocked at Docker fixture setup; no behavioral RED assertion is claimed for that initial implementation. The subsequent shutdown regression has real RED/GREEN proof below.

## Resolved findings and independent review

- Task2 repair `ad728ab`: independent heartbeat starvation and missing ORCID post-reservation guard corrected. Separate scoped re-review PASS.
- WR-1, INTRODUCED, RESOLVED by `ed16401`: interrupted cache follower previously escaped as `InterruptedException` into worker generic failure, writing ERROR. RED MySQL regression expected RUNNING and observed ERROR. Cache now throws `CancellationException` with interrupted flag and cause preserved, using the existing worker no-write cancellation path. GREEN regression proves no `saveFailure` or provider reservation, the lease stays RUNNING and is reclaimed with a new token after expiry; the owner completes and cache remains usable.
- Whole source review of `371a008..ed16401`, including I-1 through I-9 re-entry, PASS; no remaining blocking findings. Reviewer operated independently and read-only. Final combined test gate also passed; machine verification PASS, manual production acceptance pending.

## Contract audit

| ID | Machine audit | Evidence |
| --- | --- | --- |
| I-1 Rules/settings/roster/schema/frontend unchanged | ✅ | Committed diff contains eight production Kotlin files and tests; no rules, YAML, migrations or static files |
| I-2 Short recovery critical sections, safeguards | ✅ | `DiscoveryPaperArchiveService.kt:497` pin/hash/revalidate; `:544` single-flight and per-item cleanup; recovery concurrency tests |
| I-3 Bounded default-four worker claims | ✅ | `DiscoveryVerificationProperties.kt:35,44`; `DiscoveryVerificationWorker.kt:90-114`; scheduling tests |
| I-4 Ownership/cancel/auth/backoff/shutdown | ✅ | Worker ownership/dispatch distinction and independent renewals; ORCID dispatch guard; atomic capped OpenAlex backoff; WR-1 repair regression |
| I-5 Academic context/guard/result isolation | ✅ | `DiscoveryRecheckAcademicEvidenceService.kt:58-137`; batch/interrupt/cancel fixtures |
| I-6 Paper key and per-person assembly | ✅ | `PaperVerificationEvidenceService.kt:117-195`; cross-person/cold-warm/key-miss tests |
| I-7 Bounded pure retention, no PDF or failed result cache | ✅ | `PaperBodyComputationCache.kt:16-88`; capacity, independence, overflow and retry tests |
| I-8 Warm cache detects missing/corrupt source | ✅ | `PaperVerificationEvidenceService.kt:66-99` before lookup; source failure fixtures |
| I-9 Scope/dirty-file preservation, no production mutation | ✅ | Owned source/tests selectively committed; unrelated working changes remain; no deployment or SSH in this implementation pass |

Accumulation, state-machine, cross-plan contracts, replacement paths, no-extras and scope checks: ✅. Existing lease expiry/reclaim transitions retained; process cache intentionally resets after restart and never overrides durable evidence or quotas. Knowledge probe `K-no-cache-framework-in-repo` satisfied through explicit bounds, lifecycle and failure semantics; no shared knowledge rule changed. Plan quality correction/decomposition: N/A, no required amendment. Preexisting extreme Retry-After seconds-to-milliseconds overflow remains an adjacent observation; no such production input observed and no change made here.

## Manual acceptance / limits

PENDING after a separately authorized deployment: measure elapsed time, memory and provider429 behavior on a fixed roster; compare each person's qualification outcome and traces with baseline. This change promises neither a higher admission count nor a specific speedup. Startup recovery remains synchronous and individual source integrity reads remain; this task removes the whole-scan lock, not all archive I/O.

## Commits

`9944cca` archive lock; `812163c` concurrent backoff; `a81fee4` worker/academic batching; `ad728ab` worker/ORCID repair; `0c475df` paper reuse; `ed16401` shutdown repair. Local main only, not published.
