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

---

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 06-query (`docs/plans/2026-10-13/discovery-person-06-query.md`, A13-revised)
Boundary: `7a96b5957b75096110e392b25a98bb9ea44990a5..0755833e3e5e0480d9ad40443ae5ebba0e91ac6a`
Verifier: Verify06b

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 7a96b59..0755833` = 8 authorized 06-query files across the two 06-query commits, plus sibling-child and docs bookkeeping. Epoch-2 commit `0755833` touches exactly 4 of the 8 authorized files (2 main: `DiscoveryPersonQueryService.kt`, `ExpertIndexController.kt`; 2 test: `DiscoveryPersonQueryServiceTest.kt`, `ExpertIndexControllerTest.kt`); the other 4 are untouched by epoch 2. Epoch-1 commit `db8ed30` touches exactly the 8 authorized. Non-06 product files in the range (`app.js`, `index.html`, `src/test/js/discoveryPersonEmails.test.js` from `4fbc761`) are child 07-ui's files, authorized by `docs/plans/2026-10-13/discovery-person-07-ui.md:82-84` (A12). All remaining commits are `docs/plans/fast/**` bookkeeping. Uncommitted working-tree change is only `children/06-query/execution.md` (epoch-2 report, docs). |
| Plan and invariants | PASS | A13 projection present and same-source: `scanDiscovered` accumulates per-person hit counts in `memberCountByKey.merge(key, 1, Int::plus)` inside the same `findMembers` batch loop (`DiscoveryPersonQueryService.kt:303-307`) and emits `memberCountByDocId` keyed by representative docId (`:320-326`); `searchPersons` maps each row to `PersonListEntry(profile, memberCount = scan.memberCountByDocId[esDocId] ?: 1)` (`:93-99`). Computed from the full scan regardless of page → cross-page consistent; non-discovered/others rows default to 1. No ES field additions (epoch-2 diff has none; `ExpertSearchService` untouched). DB grouped branch untouched (`ExpertContactManagementController.kt` not modified in epoch 2). Epoch-1 I-1/I-2/I-3 spot-check: unchanged — the epoch-2 diff is confined to the 4 files and does not touch filter construction (`filters + discoveredFilter()/notDiscoveredFilter()`), dedupe/representative (`:297-307`), `totalHits = othersTotal + scan.personCount` (`:100`), or read-only `personDetail`. Tests: `DiscoveryPersonQueryServiceTest.kt:191-215` (memberCount=3 discovered, cross-page total consistent, other row=1); `ExpertIndexControllerTest.kt:198-224` (stub `PersonListEntry(expert, memberCount=3)`, response row carries 3). |
| Required commands | PASS | Fresh C1 (JDK 11, worktree HEAD `0755833`): exit 0, BUILD SUCCESS; `Tests run: 124, Failures: 0, Errors: 0, Skipped: 0` — DiscoveryPersonQueryServiceTest 12/0/0, ExpertSearchServiceTest 79/0/0, ExpertIndexControllerTest 20/0/0, ExpertContactManagementControllerTest 13/0/0. Epoch 1 was 123; delta +1 = the new A13 service test (11→12); other three classes unchanged. JS gate inside `mvn test`: tests 1675 / suites 320 / pass 1675 / fail 0 (≥1675). No new failures. |
| Downstream interfaces | PASS | `ExpertIndexResponse.memberCount: Int = 1` added after `contactStatus`, with matching `from(...)` param default 1 (`ExpertIndexController.kt:429,459,480`); `listExperts` passes `memberCount = entry.memberCount` (`:109`). Default 1 preserves the old/non-discovered response shape. Satisfies 07 S-1 "数量取响应" (`children/07-ui/brief.md:32`, `discovery-person-07-ui.md:25`). DB grouped branch and `GET /api/experts/person` structure from epoch 1 unchanged. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- R-1 (P2, carried from epoch-1 O-1, unresolved, out of A13 scope): deep-pagination edge — `searchPersons` reads the "其他" prefix via `searchExpertsWithSort(level, othersFilter, 0, from + size, sortBy)` (`DiscoveryPersonQueryService.kt:90`) whose `require(size in 1..1000)` (`ExpertSearchService.kt:1254`) throws `IllegalArgumentException` (HTTP 400) when `from + size > 1000`. Unchanged by epoch 2; no test covers it; repair not uniquely determined. Reserved for 07 pagination.
- R-2 (P2, carried from epoch-1 O-2): performance gate (p95≤2s / extra heap ≤128 MiB on 51,829-row real-shape dataset) recorded as unresolved with no fabricated numbers in `execution.md` (epoch 1 and epoch 2). Failure semantics (100,000-doc cap / 10s budget → retryable `DiscoveryPersonQueryUnavailableException`) and scroll closure remain unit-covered; no fabricated measurement found.

### Required Action
- COMPLETE_CHILD
