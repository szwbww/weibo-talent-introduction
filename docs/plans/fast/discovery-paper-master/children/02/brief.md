# Child 02 — Full-download archive capture

- Exact approved plan: docs/plans/2026-10-23/discovery-paper-02-capture.md (commit c18dbc060dcf4c8440f853ede4d1d3aaf737994f); read the entire file before execution.
- Master contract: docs/plans/2026-10-23/discovery-paper-master.md (commit c18dbc060dcf4c8440f853ede4d1d3aaf737994f); master I-1 through I-4 apply unchanged.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
- Branch: fast/discovery-paper-master
- Child product base: 74c00bdab46796084c5d4a1a7a3c572b7bee6915
- Dependency 01: LIGHT_PASS after one repair; terminal code 74c00bdab46796084c5d4a1a7a3c572b7bee6915, evidence 1ba166e3da369f73fe1519834dea8a04123239df. Complete eight-child artifact checkpoint passed cleanly at e0fd89d8c14c659001cf7a4da469e4275ac066d9. Do not replay child 01.
- Approval: explicit user fast-p invocation; no amendments approved.

## Authority and invariants
Authorized product/test files are exactly the eight paths in this child plan's change table. All T/I/A requirements and downstream interface fields are binding. Do not alter 01 files, parser rules, cache protocol versions, migrations, admission logic, other tests or frontend. Product-scope expansion/contract conflict must be returned as PLAN_CONFLICT, never worked around.

Use 01 service as the only archive SQL/filesystem seam: beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction. Its fixed DTOs and controller are already implemented. New scope must capture all six PAPER sources in SYNC/QUEUE worker threads; restore ThreadLocal in finally. Preserve independent legacy sample hooks; disabled archive must not add SQL/file writes. Download caching is not a new download. Store original full response bytes, scrub URLs, exclude metadata.fullText; structured parserVersion is identityExtraction=20261004/pdfLayout=0. Freeze first, update latest; do not consume on archive infrastructure failure. Await all page futures; QUEUE PENDING+60s through existing CAS without consuming attempts/cache. Do not change existing download/429/retry/admission/lease boundaries. ORCID RECORD is not a paper.

## Required commands and behavioral proof
All named existing suites were located in this worktree. Run freshly after final edits:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest -DmigrationIt=true`

FlowIT must actually exercise production bean registration and all six sources/CORE/fallback/retry/queue/sync/cancellation against local fake HTTP, isolated SQL and real temporary files as the plan requires; no null-injection bypass. Report exact executed/skipped counts. Run a separate throwaway smoke/harness through changed capture/worker behavior, observing archive bytes/hash, unchanged cursor/zero page consumption under storage blocking, or equivalent actual changed-path scenario. Keep harness in an authorized test fixture or remove temporary scaffolding. No real Apollo/paid calls, production DB/context or data-service network.

Baseline evidence: original JDK11 sample suites 11 tests passed; child01 terminal required command 37 JVM tests and Maven-bound 1807 Node tests passed; separate real-files/MySQL/controller smoke passed. Docker 29.8.2 available. These are baselines only, not substitute for fresh commands.

## Execution discipline and outputs
Use execute-p bound to exact approved bytes/worktree. One product writer, no additional worktree. Skip builds/tests/linters/formatters mid-flight; run checks after final edits. Commit product/tests only as `feat(fast-p): implement 02`. Append execution result only to `docs/plans/fast/discovery-paper-master/children/02/execution.md`; controller owns ledger, briefs and other logs. Do not update knowledge/plan/docs outside that report. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit, commands, report path. Do not inspect or implement later children.
