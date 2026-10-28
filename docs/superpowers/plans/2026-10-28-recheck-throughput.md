# Recheck throughput implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute continuously under the user's explicit authorization to modify local main; no deployment.

**Goal:** Remove demonstrated recheck serialization and archive scan blocking without changing admission results.
**Architecture:** Short archive maintenance critical sections, a bounded worker pool, bounded academic request coalescing, and bounded reuse of pure paper computations. Durable item leases and per-person evidence remain authoritative.
**Tech Stack:** Kotlin, Spring Boot 2.7, Java 11, JDBC/MySQL, Maven/JUnit.
**Spec:** User-approved scope in this conversation: speed up recheck using lock reduction, bounded concurrency, academic batching, and shared-paper parsing reuse; modify main locally. This document records that scope, not a new approval gate.

## Global Constraints

- Local main only. No deployment, production mutation, schema migration, qualification rule change, frozen roster change, or frontend change.
- Preserve token/generation ownership checks, cancellation, auth latch, Apollo free/paid settings, provider rate-limit/backoff, per-person traces, and candidate projection idempotency.
- Concurrency default 4, configurable from 1 through 8; claim only when execution capacity exists, never queue unstarted leases without heartbeats.
- No unbounded queues/caches, no retained PDF bytes, no new dependencies or cache framework.
- Shared computations never imply shared author qualification: per-person binding, baseline, timestamp, source validation and evidence assembly remain separate.
- Preserve all unrelated dirty files, especially application.yml. Add constructor defaults/config documentation without overwriting it.
- Test before implementation; capture failure and passing evidence. Commit only task-owned paths if committing; no push.

## Evidence

- DiscoveryVerificationWorker.tick currently calls process synchronously inside repeat(10).
- DiscoveryPaperArchiveService.recover wraps all blobs, SHA hashing, walks and records in one mutation lock; scheduledRecovery has no initial delay.
- Production thread dump 2026-10-28 10:01:07: app-sched-1 hashes in recover while app-sched-2 waits in analysisLease for the same lock.
- DiscoveryRecheckAcademicEvidenceService.collect accepts up to 100 snapshots; worker passes listOf(snapshot).
- PaperVerificationEvidenceService.collect only deduplicates body parsing within one person; analyzer.parse depends on bytes, kind, metadata authors/year and versions.
- Observed task #29524 completed-step means: paper 3.74s, local/academic 3.80s, Apollo 4.73s; these include skipped/retried work and are not a completion-time promise.

## Review Focus

1. Recovery racing analysis/deletion must not delete a reader's source or mark a replaced valid file missing (task 1).
2. Worker cancellation/shutdown/auth failure must not claim work beyond capacity or leak leases (task 2).
3. Mixed run/mode/evaluation time or one cancelled member must not mix academic evidence or bypass dispatch ownership (task 2).
4. Equal paper bytes with different authors/year/kind/version must not reuse incompatible parses (task 3).
5. Missing/corrupt/deleted sources and failed computations must remain observable on warm reuse paths (task 3).

### Task 1: Remove whole-archive recovery lock blocking

**Files:** DiscoveryPaperArchiveService.kt and archive service concurrency tests under src/test/kotlin/com/weibo/talentintroduction/discovery/archive/.
**Interfaces:** Preserve analysisLease, commitAnalysis, recover and all public APIs. Existing failpoint may provide deterministic test barriers.

- [x] Add failing deterministic latch tests proving analysis of another blob can proceed while recovery hashes a slow blob, concurrent recover calls do not duplicate a scan, and readers still block deletion.
- [x] Run targeted tests and record the relevant pre-change failure.
- [x] Make recovery single-flight. Snapshot maintenance candidates under a short lock; hash outside the mutation lock with an active-reader pin, then revalidate current blob/file identity before applying a missing-state result. File walks and record loops must acquire short per-item critical sections and recheck current state before destructive actions. Preserve symlink protection, file-lock mutation protocol, interrupted-write cleanup and integrity checking. Delay first scheduled recovery by 300000 ms to avoid immediate startup duplication. Avoid startup whole-scan lock blocking; don't remove recovery guarantees.
- [x] Run archive service/capture and new concurrency tests. Keep a bounded fixture showing reader completion before slow recovery release.
- [x] Report exact files, test counts/commands, red/green evidence and remaining limitations; task-owned commit only.

### Task 2: Bounded verification concurrency and academic batching

**Files:** DiscoveryVerificationWorker.kt, DiscoveryVerificationProperties.kt, DiscoveryRecheckAcademicEvidenceService.kt; optional one narrowly scoped academic batch helper in discovery/service; corresponding unit tests. Direct dependency OpenAlexRequestPolicy.kt may receive only atomic/synchronized backoff-state access if needed for concurrent callers, preserving policy values. No application.yml change.
**Interfaces:** Keep worker.process synchronous for fixtures. Existing academic.collect remains a deterministic direct batch API; worker uses a coalesced path that returns the same per-person RecheckAcademicEvidence.

- [x] Add failing tests: four blocked workers run simultaneously; fifth lease not claimed until a slot is free; overlapping ticks don't duplicate claims; disabled/auth-blocked/closed worker stops claiming; single worker remains supported.
- [x] Add failing academic tests: concurrent eligible REFRESH calls with compatible context share batch HTTP lookup; REUSE/no trusted ID do not dispatch; incompatible contexts stay separate; cancellation is checked immediately before provider dispatch and one invalid member does not attribute results to another; partial/rate-limit outcomes preserve per-person retry semantics.
- [x] Add constructor property concurrency=4 validated 1..8 at the end for compatibility. Use fixed bounded executor/slots; each executing worker claims its own due item just before processing; no per-item unbounded queue. Ensure heartbeat, close and all task-finally paths release slots; do not hold scheduler threads waiting for provider work.
- [x] Coalesce only requests that have reached academic step after identity/protection checks. Use at most 25 ms gather window, max 8 requests per batch and bounded pending capacity. Group by owner execution ID, mode and frozen evaluationAt; preserve caller-specific ownership guards before dispatch and result delivery. Do not prefetch the entire roster, fetch by guessed names, or make cancelled requests on behalf of other members. Keep existing OpenAlex budget and HTTP request guards. Failures must complete all waiting callers; close must not strand futures. If batch selection changes during dispatch, safely abort/retry surviving requests with fresh guards rather than relaxing them.
- [x] Run targeted worker scheduling and academic tests plus existing applicable fixtures; record Docker-dependent blockers accurately.
- [x] Report and commit only owned files.

### Task 3: Bounded pure paper computation reuse

**Files:** PaperVerificationEvidenceService.kt and optionally one small internal cache/helper in discovery/service; PaperVerificationEvidenceServiceTest.kt or dedicated reuse tests. PaperBodyAnalyzer.kt only if required for a narrow version/key seam.
**Interfaces:** Keep collect return meanings and individual source/binding assembly. Cache only PaperBodyComputation, never assembled per-person evidence or original body bytes.

- [x] Add failing tests: two experts sharing a body parse once and keep separate binding/evidence; different metadata/kind/version miss cache; concurrent identical requests compute once; errors aren't retained; eviction is bounded; deleted/corrupt source still fails after a warm cache.
- [x] Add a process-local bounded LRU (max 64 entries and max 8 MiB serialized computation payload); skip oversized results, retain successful complete computations only, no disk persistence. Key uses verified SHA, attempt kind, canonical relevant authors/year metadata and analyzer versions. Use bounded in-flight coalescing (max 8); overflow computes without retention rather than accumulating waiters. Never hold global cache lock during parsing or waiting. Return independent values to avoid caller mutation of cached lists.
- [x] Continue opening/validating every frozen archive reference before cache reuse; every person's baseline, analyzedAt, source provenance and author qualification binding is recomputed. Preserve parsedBodies' existing per-person logical source count to avoid changing stored diagnostics; expose actual parse counts only in tests, not invented production metrics.
- [x] Run paper evidence/analyzer/qualification regressions and controlled cold-versus-warm comparison; assert identical per-person outcomes with fewer parse calls. No production performance promises from stub timings.
- [x] Report and commit owned files.

## Final validation

- [x] Java 11 compile and targeted regression suite; explicit list of skipped/unavailable integration tests.
- [x] Independent task reviews and whole-change review, then fix-v audit against this user-approved scope.
- [x] Check no admission rules, migrations, UI, provider settings, frozen data, or unrelated dirty files changed.
- [x] Report default concurrency, deterministic measured gains, and need for post-deployment production timing. Do not deploy.

## Completion evidence

Implemented locally on main through `ed16401`; no deployment. Full Java11 test compilation and exact-file compilation after the final method-body repair passed. Final combined suite passed; see [verification report](../../audits/2026-10-28-recheck-throughput/verification.md) for counts, commands and independent review.

Task3 initial RED attempt was blocked by Docker fixture setup, not an observed behavioral failure; current tests and the subsequent shutdown repair have recorded evidence. Cache interruption repair reproduced ERROR before the change and verified resumable lease afterward. Startup remains synchronous; production latency/memory/provider429 checks remain pending a separately authorized deployment.
