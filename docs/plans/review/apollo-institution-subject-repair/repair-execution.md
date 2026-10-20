# Repair Execution — apollo-institution-subject-repair

- Plan: `docs/plans/fix/apollo-institution-subject-repair/repair.md`
- Plan SHA-256: `cfd5a2179390fb1c18470c0e2854d27b0df52b8ac37fab1bca6e443574cdab85`
- Baseline subject plan: `docs/plans/2026-10-19/apollo-institution-subject-repair.md` (sha256 `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397`)
- Baseline country plan: `docs/plans/2026-10-19/discovery-institution-country-supplement.md` (sha256 `3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67`)
- Human approval source: explicit human-originated invocation in this session, `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/fix/apollo-institution-subject-repair/repair.md`, matching the plan's `Human Approval` section.
- Executor: omp coding assistant (single execution context; no separate subagent identity exposed).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair`
- Branch: `fast/apollo-institution-subject-repair`
- Prior product code head: `0953b2f3570725bf1de5cc138422220c1731a48d`
- Post-repair product code head: `8e4e7d7e6cdd4670ccc372cd9ac6e67c2ba72078`
- Evidence parent before handoff commit: `8e4e7d7e6cdd4670ccc372cd9ac6e67c2ba72078`
- Worktree/index before handoff: clean product/test tree; only this handoff artifact added after the product commit.

## Repaired Findings

| Finding | Result | Change |
|---|---|---|
| V-1 | FIXED | Normalizer no longer treats an alphabetic tail before a country as address. Campus qualifiers and `University of …` system names are retained; unknown qualifiers without a unit prefix return UNRESOLVED. |
| V-2 | FIXED | Contextual Rules comparison uses validated subjects together with original standard IDs/aliases; same-namespace ID conflict no longer matches by name. |
| V-3 | FIXED | Country complete-name equality uses only NFKC, whitespace collapse and case normalization; punctuation is not removed. |
| V-4 | FIXED | QueryService contains malformed optional evidence per row; records keep the saved state/outcome and flag `institutionSubject`; Gate remains strict. |
| V-5 | FIXED | `CancellationException` and wrapped cancellation propagate through supplement and registration; interruption behavior is preserved. |
| V-6 | FIXED | Over-limit observation text is omitted, so a SKIPPED/EVIDENCE_TOO_LARGE receipt remains a valid registration input. |
| V-7 | FIXED | Worker distinguishes invalid saved protocol (ERROR/VERIFICATION_FAILED, evidence retained) from lawful parser drift (PENDING/EVIDENCE_CONFLICT, no HTTP). |
| V-8 | FIXED | Valid countries are deduplicated before ambiguity is decided; identical duplicates fill, true disagreement remains MULTIPLE_COUNTRIES. |

## Changed Product/Test Files

All eleven paths are in the plan's Authorized Files; no other product, test, resource, build, config, migration, ES, frontend, or plan file changed.

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt`

## Fresh Required Commands

Run after the final implementation state, with JDK 11.

| # | Command | Result |
|---|---|---|
| 1 | `JAVA_HOME=…zulu-11… mvn -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS; exit 0; 71 JVM tests, 0 failures/errors/skips; JS 1,724 passed; BUILD SUCCESS |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=…zulu-11… mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS; exit 0; 51 JVM tests (FlowIT 29 + Repository 12 + Reporting 10), 0 failures/errors/skips; JS 1,724 passed; BUILD SUCCESS |
| 3 | `JAVA_HOME=…zulu-11… mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS; exit 0; 89 JVM tests, 0 failures/errors/skips; JS 1,724 passed; BUILD SUCCESS |
| 4 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=…zulu-11… mvn -Dapi.version=1.40 -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test` | PASS; exit 0; 49 JVM tests (CountrySupplementFlowIT 8 + FlowIT 29 + Repository 12), 0 failures/errors/skips; JS 1,724 passed; BUILD SUCCESS |

## Deviations

- None in product scope. No new file, schema, index, migration, configuration, frontend, or provider change was introduced.
- Cancellation handling uses `java.util.concurrent.CancellationException` (the JVM cancellation type); no new business state.
- Real-cohort institution identity impact and country effectiveness remain `NOT_EVALUATED`; no synthetic result was promoted to a real success rate.
- Deployment, restart, live backfill, push, merge, amend, and history rewrite were not performed.

## Verification Handoff

The product commit `8e4e7d7e6cdd4670ccc372cd9ac6e67c2ba72078` is HEAD of the selected worktree and reachable from `fast/apollo-institution-subject-repair`. This execution report is committed separately by the authorized docs-only evidence commit; no commit SHA is written into itself.
