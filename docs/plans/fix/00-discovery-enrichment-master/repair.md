# Repair Plan: 00-discovery-enrichment-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
Verification report: aggregate epoch 4 re-review, V-4 persistent; V-1–V-3 resolved
Implementation boundary: reviewed master code `f0c41271fc56d7455e14d28a71d563a5341dfdeb..276cf733326a204a70b061ecda01072960eedaf2`; latest repair delta `52d22cc74abf811cbaec38de2791af53e8491cc..276cf733326a204a70b061ecda01072960eedaf2`

## Objective

Make every non-null fulltext deadline wrap response-body reads from dispatch, including the normal fresh 90-second budget that is larger than an individual XML/Unpaywall socket-timeout cap.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-4 | P1 | R-6; 10/I-1: one paper's fulltext chain has a total 90-second bound | `BoundedFulltextHttp.bounded` returns the raw `RestTemplate` when the remaining deadline is at least both socket caps. At a fresh 90-second paper deadline this is true for Europe PMC (5s/30s) and Unpaywall's caps, so their XML/JSON body is not wrapped by `DeadlineBoundedInputStream`; a peer can trickle bytes below the per-read timeout beyond the absolute deadline. Existing trickle tests use 400–500ms deadlines, which take the wrapped branch only. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved: c3 fresh gate passes; five-argument Mockito stubs match the runtime signature. |
| V-2 | Resolved: automatic enrichment summaries/details map their actual JSON shape; c8 and JS pass. |
| V-3 | Resolved: idle worker probes due work before taking the progress lock; c6/c8 pass. |
| c2/c3/c4/c6/c7/c9/c10 RECORD_ONLY observations other than V-4 | Pre-existing, explicitly deferred, ambiguous, or not a confirmed mandatory violation. |
| c7 Flyway V124 fixture error | Reproduced unchanged; V131 repository/service cases pass. |

## Unchanged Contract

- Preserve M-1 through M-5: no mail, identity mutation, scope expansion, paid API use, migration rewrite, or key exposure.
- Preserve public-only URLs, at-most-three URL attempts, URL deduplication, 10MB/two-page limits, existing timeout vocabulary, trusted identity rules, and normal unbounded (`deadline == null`) callers.
- Do not reset a fresh 90-second budget per stage or URL; do not add retries, threads, queues, sources, or higher timeouts/concurrency.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | Ensure a non-null deadline always uses deadline-wrapped response handling while preserving the raw base client only for unbounded callers. |
| `src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` | Prove a deadline longer than both socket caps still uses body wrapping and terminates a trickling response at the absolute deadline. |

## Repair Tasks

### R-1: Wrap the normal-budget fulltext response path

- Resolves: V-4.
- Root cause: the wrapper is conditionally installed only when the remaining deadline is already tighter than a socket cap; the ordinary 90-second fulltext deadline bypasses it.
- Files: exactly the Authorized Files above.
- Change: for every non-null deadline, build a deadline-wrapped request factory/client even when its connect/read timeout values remain at their original caps. Keep returning the exact original client only when `deadline == null`; retain timeout narrowing and retry suppression when the deadline becomes tighter.
- Regression test: use a deadline longer than both supplied socket caps against a trickling-body server; assert body cancellation at the absolute deadline and one accepted connection. Also assert the unbounded path retains the base client and its existing behavior.
- Existing verification: c1 and c10 targeted commands, JS, and the full Java 11 Maven command below.
- Must not change: normal null-deadline client identity/behavior; OpenAlex authentication; response converters/error handling; stage order; timeout category; retry/cost/scope limits.
- Prohibited: per-stage budget reset; raw-client bypass for any non-null deadline; larger configured timeouts; test suppression.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RestTemplateConfigTest,OpenAlexRequestPolicyTest,OpenAlexDataSourceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest`
3. `node --test src/test/js/*.test.js`
4. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dapi.version=1.40`

## Completion Criteria

- V-4 is resolved for both compressed and normal remaining budgets: XML/lookup/PDF response bodies cannot outlive the one absolute per-paper deadline.
- A non-null deadline never returns an unwrapped response path; `deadline == null` retains the original client.
- No fulltext stage starts after the deadline, and no later stage starts once timeout ends the chain.
- Changed files remain inside the authorized list.
- Fresh required commands report no introduced failure; record V124 separately only if it persists in migration IT.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/fix/00-discovery-enrichment-master/repair.md` invocation authorizes:

1. Only the Authorized Files in this plan and its required verification commands.
2. After all repair tasks and required commands pass, exactly one local product commit, staging only Authorized Files, with subject `fix(discovery): always wrap fulltext deadline`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/review/2026-09-21-discovery-enrichment-master/repair-execution.md` with the approval source, repair identity, pre/post code SHAs, changed files, command evidence, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
