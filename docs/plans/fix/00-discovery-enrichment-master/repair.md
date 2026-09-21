# Repair Plan: 00-discovery-enrichment-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
Verification report: aggregate epoch 2 re-review, V-4 persistent; V-1–V-3 resolved
Implementation boundary: reviewed master code `f0c41271fc56d7455e14d28a71d563a5341dfdeb..7312143c484fe00162c8f602b9295f3029ce5588`; post-repair delta `cd39503b7d6dc0d3fa12a02a226e6995bd2910e7..7312143c484fe00162c8f602b9295f3029ce5588`

## Objective

Make the entire OpenAlex fulltext chain return or terminate at its one 90-second per-paper deadline, including an in-flight PMC XML, PDF/HTML, or Unpaywall HTTP operation.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-4 | P1 | R-6; 10/I-1: one paper's fulltext chain has a total 90-second bound | The repair propagates an `Instant` and checks it before XML/Unpaywall work, but neither HTTP client receives the remaining timeout or a cancellation boundary. `EuropePmcDataSource.fetchFullTextXml` and `UnpaywallClient.getForObject` may remain blocked after expiry; Unpaywall receives the unqualified `RestTemplate`, which has no configured read timeout. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved in `7312143`: five-argument Mockito adapter stubs; fresh c3 passes. |
| V-2 | Resolved in `7312143`: automatic enrichment totals and detail stages map their actual JSON; fresh c8 and JS pass. |
| V-3 | Resolved in `7312143`: read-only due-work probe prevents an idle worker from creating task/progress evidence; fresh c6/c8 pass. |
| c2/c3/c4/c6/c7/c9/c10 RECORD_ONLY observations other than V-4 | Pre-existing, explicitly deferred, ambiguous, or not a confirmed mandatory violation in this repair boundary. |
| c7 Flyway V124 fixture error | Reproduced unchanged; V131 repository/service coverage passes. |

## Unchanged Contract

- Preserve M-1 through M-5: no mail, identity mutation, scope expansion, paid API use, migration rewrite, or key exposure.
- Preserve public-only URLs, at-most-three URL attempts, URL deduplication, 10MB/two-page limits, existing failure-category vocabulary, trusted identity logic, and normal non-expired callers.
- Do not reset a fresh 90-second budget per stage or URL; do not add unbounded retries, threads, queues, or paid sources.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | Keep one deadline across the complete fallback chain and consume timeout outcomes consistently. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt` | Enforce the remaining deadline for an in-flight XML request. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClient.kt` | Enforce the remaining deadline for delay and in-flight lookup. |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | Enforce the remaining deadline before headers and while reading an in-flight content response. |
| `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | Supply only bounded-client/request-factory support required for existing clients. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | Prove one elapsed budget covers XML, lookup, and URL stages. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSourceTest.kt` | Prove slow XML returns the existing timeout outcome by the shared deadline. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/UnpaywallClientTest.kt` | Prove slow lookup is cancelled/returns empty by deadline. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | Prove delayed connection/header/body work observes remaining time. |
| `src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` | Prove bounded request support preserves ordinary client/authentication contracts. |

## Repair Tasks

### R-1: Bound every blocking fulltext stage by remaining time

- Resolves: V-4.
- Root cause: deadline propagation is only a preflight guard for XML and Unpaywall; the underlying blocking client call has no remaining-time enforcement.
- Files: exactly the Authorized Files above.
- Change: apply the single remaining deadline to connection, response/header, delay, and body-read work for PMC XML, OA URL, and Unpaywall. On expiry, cancel/terminate the active operation as safely supported and return the existing timeout result; do not launch a later stage.
- Regression test: controlled slow XML, lookup, connection/header, and body responses each complete as timeout within one shared budget; no later URL/lookup request occurs; normal fallback remains deduplicated and capped.
- Existing verification: c1, c6, c8, c10, JS, and full Maven commands below.
- Must not change: normal `null`/non-expired caller behavior; stage order; timeout category; retry/cost/scope limits.
- Prohibited: per-stage budget reset; orphaned uncancelled workers/requests; increased timeouts/concurrency/size/page limits; test suppression.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexRequestPolicyTest,RestTemplateConfigTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionSummaryExtractorTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest`
5. `node --test src/test/js/*.test.js`
6. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dapi.version=1.40`

## Completion Criteria

- V-4 is resolved with elapsed-time regression tests covering in-flight XML, lookup, and URL operations.
- No fulltext stage starts after the one per-paper deadline, and active work does not make the caller exceed it.
- Changed files stay inside Authorized Files.
- Fresh required commands report no introduced failure; record V124 separately if it persists only in migration IT.

## Human Approval

Execution is prohibited until the human explicitly approves this current plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/fix/00-discovery-enrichment-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(discovery): enforce fulltext deadline`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/review/2026-09-21-discovery-enrichment-master/repair-execution.md` with the exact approval source, current repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
