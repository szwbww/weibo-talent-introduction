# Repair Plan: 00-discovery-enrichment-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
Verification report: aggregate epoch 1, findings V-1–V-4
Implementation boundary: `f0c41271fc56d7455e14d28a71d563a5341dfdeb..e12c3471f89a9bc333ef8e7777703214abe29d5e`

## Objective

Restore required aggregate test validity, truthful automatic-enrichment reporting, idle-worker lifecycle behavior, and the per-paper fulltext time bound.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Java 11 targeted and full Maven suites must pass | `PdfEmailExtractor.extract` grew to five JVM parameters; three-argument Mockito stubs in retained adapter tests now leave matcher state unfinished. |
| V-2 | P1 | R-3/R-6, I-5: automatic enrichment and task details show truthful, separate stage counts | `AutoEnrichmentBatchResult` serializes `claimed/succeeded/pending/unmatched/failed`, while the common `EXPERT_ENRICHMENT` summary and renderer read discovery/manual keys. |
| V-3 | P1 | R-2/R-3, I-5: task lifecycle reflects real work and failure | An idle 30-second worker tick persists a RUNNING progress row before it knows whether a job exists; clearing memory later leaves a false interrupted/failed record. |
| V-4 | P1 | R-6 and 10/I-1: each paper's complete fulltext chain is bounded to 90 seconds | OpenAlex starts the deadline before PMC XML, but the PMC XML and Unpaywall requests do not receive or enforce the remaining deadline. |

## Findings Excluded

| Finding | Reason |
|---|---|
| c2 O-1, c3 O-1..O-3, c4 O-1..O-4, c6 O-1, c7 O-1..O-2, c9 O-1 | No confirmed mandatory aggregate violation in this boundary, or pre-existing/out-of-scope baseline issue. |
| c10 O-1..O-3 | Does not prove a mandatory defect beyond V-4; retain as review evidence only. |
| c7 Flyway V124 test error | Fresh MySQL run reproduces the pre-existing FK fixture error; V131 jobs pass 18/0. |

## Unchanged Contract

- Preserve M-1 through M-5: no email sending, identity mutation, scope expansion, paid API use, migration rewrite, or Key exposure.
- Keep one page/one batch accounting separate; do not fold enrichment counts into discovery counts.
- Retain 10MB/two-page limits, public-only URLs, at-most-three fallback URLs, duplicate-URL suppression, and trusted identity rules.
- Do not change the pending/retry/unmatched/failed job semantics, migration V131, or manual enrichment entry points.

## Authorized Files

| File | Purpose |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt` | Repair the affected adapter mock and preserve its behavior assertion. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` | Repair the affected adapter mock and preserve its behavior assertion. |
| `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt` | Parse both manual and automatic enrichment result shapes truthfully. |
| `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` | Prove automatic terminal summary totals. |
| `src/main/resources/static/app.js` | Render automatic enrichment source counts as enrichment stages rather than discovery columns. |
| `src/test/js/taskRecordsSemantics.test.js` | Prove the task details display the automatic enrichment stages. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorker.kt` | Prevent idle checks from persisting false progress/task state while retaining lock safety. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | Supply the smallest safe due-work/claim seam needed by the worker change. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorkerTest.kt` | Prove an idle tick creates neither execution nor persisted progress, while nonempty and contended paths remain correct. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | Prove the due-work seam does not claim, duplicate, or advance a job. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | Carry one deadline across XML, OA URL, and Unpaywall stages. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt` | Accept/enforce the remaining deadline for the XML stage without changing normal callers. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClient.kt` | Accept/enforce the remaining deadline for lookup delay/request work. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | Prove the aggregate fulltext deadline covers every stage. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSourceTest.kt` | Prove expired/elapsed XML handling stops as timeout. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClientTest.kt` | Prove expired/elapsed lookup handling stops as timeout. |

## Repair Tasks

### R-1: Restore adapter-test compatibility

- Resolves: V-1.
- Root cause: Mockito sees the JVM five-argument `PdfEmailExtractor.extract` signature, not Kotlin default arguments.
- Files: the two adapter test files above.
- Change: make each mock match every JVM argument and retain the original delegation assertions.
- Regression test: run the exact c3 command; both adapter extraction tests execute without matcher leakage into later tests.
- Existing verification: c3/c4 targeted commands and full Maven suite.
- Must not change: production adapter behavior or `PdfEmailExtractor` API semantics.
- Prohibited: production-only workaround, test suppression, `failIfNoTests`, or weakening the adapter assertions.

### R-2: Make automatic enrichment reporting truthful

- Resolves: V-2.
- Root cause: automatic and manual enrichment serialize different names but share one task type/summary rule.
- Files: summary extractor/test and task JS/test above.
- Change: map automatic `claimed/succeeded/pending/unmatched/failed` into explicit task totals and render those same named stages in automatic source details.
- Regression test: an automatic result with `claimed=3,succeeded=1,pending=1,unmatched=1,failed=0` reports three processed, one passed, and two unfinished/rejected according to the existing terminal contract; UI presents its actual stage values, not discovery zeros.
- Existing verification: c8 targeted command, `node --test src/test/js/*.test.js`, full Maven suite.
- Must not change: manual `enriched/failed` totals, discovery source rendering, task schema, or count separation.
- Prohibited: new DB columns, rewriting historical JSON, or merging enrichment into discovery totals.

### R-3: Keep idle worker checks non-persistent

- Resolves: V-3.
- Root cause: progress persistence begins before an empty claim is known.
- Files: worker/service and their tests above.
- Change: introduce the smallest race-safe sequencing/seam so an empty scheduled check neither records an execution nor writes a progress-log row; preserve mutual exclusion and lease recovery when jobs exist.
- Regression test: an enabled empty tick leaves no `EXPERT_ENRICHMENT` execution/progress row; a claimed job and lock-contention paths remain exclusive and durable.
- Existing verification: c8 targeted command and full Maven suite.
- Must not change: 30-second cadence, max-100 tail-batch behavior, dedicated executor, manual mutual exclusion, cancellation, and pending lease recovery.
- Prohibited: polling-only in-memory state, dropping claimed jobs, or changing job-table statuses.

### R-4: Enforce the complete per-paper deadline

- Resolves: V-4.
- Root cause: the shared deadline is not propagated to the XML and Unpaywall stages.
- Files: OpenAlex/EuropePMC/Unpaywall sources and tests above.
- Change: use one deadline started before the first fulltext stage; each XML, lookup, and URL request must observe remaining time and terminate with the existing timeout category without issuing a later stage after expiry.
- Regression test: a delayed XML and a delayed Unpaywall path both exhaust the same 90-second budget and prevent subsequent fulltext requests; normal fallbacks remain URL-deduped and capped at three URLs.
- Existing verification: c10 targeted command and full Maven suite.
- Must not change: public-only URL guard, current identity attachment rules, 10MB/two-page limit, HTTP failure categories, and ordinary non-expired caller behavior.
- Prohibited: per-stage 90-second resets, increased concurrency/size/page limits, paid/credentialed sources, or unbounded retries.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest`
4. `node --test src/test/js/*.test.js`
5. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dapi.version=1.40`

## Completion Criteria

- Every V-1–V-4 regression passes and its changed files stay inside this Authorized Files list.
- Full Maven has no introduced aggregate failure; report the known Flyway V124 baseline separately if it persists.
- Automatic enrichment terminal/detail values are accurate without changing discovery counts.
- Empty worker ticks leave no false lifecycle evidence.
- The 90-second budget covers XML, lookup, and URL work as one chain.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/fix/00-discovery-enrichment-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(discovery): restore aggregate discovery contracts`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/review/2026-09-21-discovery-enrichment-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
