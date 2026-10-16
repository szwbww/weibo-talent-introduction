# Child 05-ingestion — Verify Log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 05-ingestion (`docs/plans/2026-10-13/discovery-person-05-ingestion.md`)
Boundary: de4c1a042a48ec139c95f659858e78746b6208a6..7a96b5957b75096110e392b25a98bb9ea44990a5
Verifier: Verify05-2

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status de4c1a0..7a96b59`: product/test changes are exactly the 8 authorized files (`ExpertDiscoveryService.kt`, `DiscoveryPipelineService.kt`, `DiscoveryIdentityRepairService.kt`, `DiscoveryIdentityRecoveryService.kt` + their 4 tests). Remaining changes are docs bookkeeping only (`docs/plans/fast/**/04-registry/*`, `.../05-ingestion/*`, `ledger.md`); no 9th code file, no migration, no ES/DB field. |
| Plan and invariants | PASS | I-1: every success exit registers via 04 — sync ORCID new `registerPerson` (`ExpertDiscoveryService.kt:1459`) and duplicate `recordIdentityDuplicate` (`:1437`); paper/queue `consumeOutcomeInternal` (`:1797`) covers `consumeOutcome`+`consumeQueuedItem`; duplicate branch no early-return and registers even without repair (`:2326-2334`); 01 refresh on REPAIRED/ALREADY_VERIFIED (`DiscoveryIdentityRepairService.kt:325-331`), 02 refresh after APPLIED only (`DiscoveryIdentityRecoveryService.kt:110-115`). Params built from persisted facts via `DiscoveryPersonIdentity` single source (`DiscoveryIdentityRepairService.kt:refresh/facts`), not externalIds. I-2: registration after ES persist (`admitDiscoveryExpert`→`registerPerson`), retryable STALE/FAILED→`registrationFailed=true`/`succeeded=false` (`:3733`), page not advanced (`PERSON_REGISTRATION_INCOMPLETE`), pipeline retry/backoff `PERSON_REGISTRATION_FAILED` (`DiscoveryPipelineService.kt:1109`). I-3: separate counts `personCreated/personAttached/personNeedsReview/personScopeConflict/registrationFailed`; `maxAuthors`/batch/budget untouched. Regression: multi-email HTML fixture `created=1/attached=1`, 4 docs kept; same-ORCID two-emails → `CREATED`+`ATTACHED`, persons=1/members=2, 2 RAW evidences (`DiscoveryIdentityRepairServiceTest`); interruption/repeat/pause assertions present. |
| Required commands | PASS | C1 `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` → BUILD SUCCESS, Surefire 190/0, 49/0, 16/0, 17/0; JS gate 1657 tests / 0 fail. C2 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock ... -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` → BUILD SUCCESS, Surefire 17/0, 6/0; JS gate 1657 / 0 fail. Matches additive deltas (184→190, 48→49, 13→16, 16→17); no new failures. |
| Downstream interfaces | PASS | Shared registration function `DiscoveryPersonRegistrationService.refresh` (declared in authorized `DiscoveryIdentityRepairService.kt`) reused by ExpertDiscoveryService + 01/02; `DiscoveryPipelineService` retry/conflict semantics reusable by 10's maintenance scan (`handleRetryableFailure` retryable=true, 5-attempt cap); count units exposed on `QueuedItemConsumption`/`ConsumeOutcomeResult` for 06/08. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (P2, coverage note): no test drives `DiscoveryPersonRefreshStatus.SCOPE_CONFLICT` through `registerPerson`/`refresh` to assert the `personScopeConflict` count and `PERSON_SCOPE_CONFLICT` reason-without-failure path (`ExpertDiscoveryService.kt:2368-2384`). The symmetric `NEEDS_REVIEW` path is covered and conflict outcomes are covered at the 01 repair level, so no proven production defect.

### Required Action
- COMPLETE_CHILD
