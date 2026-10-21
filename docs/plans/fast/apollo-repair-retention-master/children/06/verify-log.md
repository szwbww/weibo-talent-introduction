# Child 06 — Verification log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 06 — docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md
Boundary: a489c28e93e0357e26f6e5e85ab8b49962ed0b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4
Verifier: ApolloVerify06E2

Epoch: 2; attempt: 1; timestamp: 2026-10-21T16:41:27+08:00.
Authority: governing plan commit:c337ee2aec8a7fc5f36584afeb0d00464ca5103e, including approved A1; committed plan SHA-256 bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999. Read superseding brief, complete child plan, normative E-6/E-7 and epoch2 execution evidence. Interleaved plan/evidence-only commits are excluded from product scope.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff --name-only` and source diff show the five authorized product/test paths: DiscoveryVerificationRepository.kt, DiscoveryPromotionProgressService.kt, DiscoveryPromotionProgressServiceTest.kt, DiscoveryVerificationReportingIT.kt and ExpertDiscoveryServiceTest.kt. A1 caller diff contains only import, empty-ledger repository mock/stub and constructor argument; original assertions preserved. Remaining boundary paths are plan/workflow evidence, not product changes. |
| Plan and invariants | PASS | Repository:189–203 uses one read query, run-batch index, owner/ENFORCE/activation filters, revision+PROJECTED qualification and descending-id qualifying source dedup. Service:73–109 excludes managed IDs from legacy existence checks then unions projected documents by ID, retaining legacy ES/time semantics; :112–128 preserves the explicit legacy aggregate floor. Both refresh overloads (:37–70) return copies and :135–139 preserves originals with diagnostic logging on failure. Service tests:125–207 cover overlap/disjoint sources, old jobs/post-finish events, excluded states, both readers, failures and floor. Reporting IT:109–207 exercises real SQL filters and real SQL→service→both existing MockMvc controllers with no writes or stored-summary mutation. |
| Required commands | PASS | All commands freshly executed after inspection in brief's JDK11/Docker environment with JAVA_TOOL_OPTIONS=-Dapi.version=1.44; sequential shell exit0, artifact://211. Exact command/count/time receipts below. Necessary tests skipped0. Fresh final results match epoch2 execution baseline (12+12+217 passing); initial fixture-only failure recorded there is absent in this fresh run. |
| Downstream interfaces | PASS | Repository:186–203 exposes reportingPromotionDocuments(Long): PromotionDocuments(managedDocIds: Set<String>, projectedSources: Map<String,String>); service constructor requires repository dependency with migrated caller. Existing controller interfaces unchanged and both actual HTTP read paths exercised by IT:159–207. Missing/AUDIT run emptiness tested at IT:127–130. No task writer, schema, ES mapping or downstream policy mutation introduced. |

Fresh command evidence (each completed BUILD SUCCESS, exit0; failures0/errors0/skipped0):

| Command | Tests | Finished (+08:00) |
|---|---|---|
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test` | 12 | 2026-10-21T16:34:05 |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test` | 12 | 2026-10-21T16:37:55 |
| `mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ExpertDiscoveryServiceTest,TaskProgressControllerTest,TaskExecutionControllerTest test` | 217 | 2026-10-21T16:41:27 |

Environment: JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home; its bin prepended to PATH; DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock; JAVA_TOOL_OPTIONS=-Dapi.version=1.44. Fresh surefire text-report mtimes: service16:34:00, reportingIT16:37:50, caller16:41:21, progress controller16:41:23, execution controller16:41:21 (+0800, 2026-10-21); timestamp command exit0.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-04-01 — Preserved historical out-of-whitelist DiscoveryVerificationIntegrationTest payload ruleVersion1 expectation observation; not investigated, rerun or repaired in this child verification.
- O-06-01 — Human A-1/A-2/A-3 and production task27254 reconciliation remain pending. Disposable MySQL/MockMvc smoke uses mocked ES responses and progress store, not production; no deployment or production203-person outcome claimed. Outside this light verification's automated gates.
- O-06-02 — LEGACY_AGGREGATE_FLOOR compatibility is explicit: verified union1 versus retained recorded5 (difference4), service test:182–186. This approved lower bound is not evidence for four additional projected documents.

### Required Action
- COMPLETE_CHILD
