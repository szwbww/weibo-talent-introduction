# Child 06-query — Verify Log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 06-query (`docs/plans/2026-10-13/discovery-person-06-query.md`)
Boundary: `7a96b5957b75096110e392b25a98bb9ea44990a5..db8ed30807578f93e8082b636c52f33a9a4d1ac8`
Verifier: Verify06

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 7a96b59..db8ed30` = exactly the 8 authorized files (main: `DiscoveryPersonQueryService.kt` A, `ExpertSearchService.kt` M, `ExpertIndexController.kt` M, `ExpertContactManagementController.kt` M; test: `DiscoveryPersonQueryServiceTest.kt` A, `ExpertSearchServiceTest.kt` M, `ExpertIndexControllerTest.kt` M, `ExpertContactManagementControllerTest.kt` M). Remaining changes are `docs/plans/fast/**` controller bookkeeping (05-ingestion logs, 06-query brief/execution/fix-log/verify-log, ledger), no product code. |
| Plan and invariants | PASS | I-1: `DiscoveryPersonQueryService.kt:78-92` splits on the same `expertFilters` list with only the discovered/not-discovered marker added, so the whole condition set is satisfied by one member; dedupe by valid `personId` with stale/no-relation falling back to `personKey=docId` (`:270-296`); representative = first under display order, docId tie-break (`:297`, `compareHits:330-345`); no name-based merge; `totalHits = othersTotal + personCount` (`:92`). I-2: read-only `DiscoveryPersonDetail` DTO (`:48-58`, incl. personId/revision/representativeDocId/requestedDocId/members/conflicts) built at `:190-240`; members carry per-layer level/contactId/status/evidence; conflicts surfaced (`memberConflicts:404-419`); controller passes no personId into legacy `orcidId` (`ExpertIndexController.kt:86-104`); new `GET /api/experts/person` at `ExpertIndexController.kt:334-347` returns 404 when absent. I-3: over-budget throws retryable `DiscoveryPersonQueryUnavailableException` (100,000 rows / 10,000 ms, `:270-287`), scroll closed in `finally` (`ExpertSearchService.kt:1330-1341`), GET path calls only read methods (asserted in `DiscoveryPersonQueryServiceTest.kt:337-343`); stale relation shown separately, not aggregated (`:281-283`). Low-level units unchanged: `ExpertSearchService.kt` diff is `179 insertions, 0 deletions` (additions only); `searchExpertsFiltered`/`scrollExpertsFiltered`/`aggregateTags/Regions/EmailDomains` untouched. |
| Required commands | PASS | Fresh C1 (JDK 11): exit 0, BUILD SUCCESS; tests run 123, failures 0, errors 0, skipped 0 — DiscoveryPersonQueryServiceTest 11/0/0, ExpertSearchServiceTest 79/0/0, ExpertIndexControllerTest 20/0/0, ExpertContactManagementControllerTest 13/0/0 (surefire XML). Deltas match baseline (75→79, 18→20, 10→13, new 11). JS gate inside `mvn test`: tests 1657 / suites 316 / pass 1657 / fail 0. No new failures. |
| Downstream interfaces | PASS | Brief-declared interfaces present: `GET /api/experts/person?docId=...&level=...` + `DiscoveryPersonDetail`/`DiscoveryPersonMember` structure (`DiscoveryPersonQueryService.kt:22-58`); grouped DB branch `groupDiscoveredPersons=true` returns `groupDiscoveredPersons`/`groupedPersons`/person-level `totalCount` (`ExpertContactManagementController.kt:64-111`, `GroupedContactPerson` + `ExpertContactListResponse`, both in `ExpertContactManagementController.kt:491,500`) with default branch byte-identical for old clients; buckets deduped by person with exact total (`aggregate*Persons`). Matches 07/08 expectations. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (P2, deep-pagination edge): `searchPersons` reads the "其他" prefix via `searchExpertsWithSort(level, othersFilter, 0, from + size, sortBy)` (`DiscoveryPersonQueryService.kt:90`), whose signature enforces `require(size in 1..1000)` (`ExpertSearchService.kt:1254`). For list requests where `from + size > 1000` (e.g. page size 200 at from=900, or page 21 at size 50) this now throws `IllegalArgumentException` (→ HTTP 400 via `GlobalExceptionHandler`), whereas the previous `searchExperts` (separate `from`/`size`, `ExpertSearchService.kt:320-321`) served the same request from ES up to the 10k window. No test covers `from + size > 1000`. Not plan-mandated (plan only says "取前 from+size 条"); repair is not uniquely determined, so recorded for the 07 pagination integration / human review rather than auto-fixed.
- O-2 (bookkeeping honesty): the performance gate (p95≤2s / extra heap ≤128 MiB on the 51,829-row real-shape dataset) is recorded as **unresolved with no fabricated numbers** in `children/06-query/execution.md`; its non-execution is expected in this environment, consistent with the brief. Failure semantics (over-budget → query unavailable) and scroll closure are unit-covered instead. No fabricated measurement found.

### Required Action
- COMPLETE_CHILD
