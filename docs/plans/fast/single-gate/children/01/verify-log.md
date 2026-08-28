## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 (docs/plans/2026-08-28/01-lastpublicationyear-recovery.md)
Boundary: 1f5a916489933fc9b2e8e469037fc912d55edd5d..cec6ce15ba3b41a6bf76e70eae503cdc5a925560
Verifier: Verify01Light

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | `git diff --name-only` yields exactly the 8 authorized files (ExpertDiscoveryService.kt, OpenAlexDataSource.kt, app.js, index.html, ExpertDiscoveryControllerTest.kt, ExpertDiscoveryServiceTest.kt, OpenAlexDataSourceTest.kt, author-response-sample.json). `styles.css` and `ExpertClassificationService.kt` zero-diff (absent from boundary). |
| 2. Plan and invariants | PASS | I1-1/I1-2: `parseAuthorEnrichmentFromNode` uses `counts_by_year.filter { works_count > 0 }.mapNotNull(year).maxOrNull()`; tests cover ascending fixture (max 2010 vs zero-works 2022), descending order (2022 with zero-works 2025 above), and batch path (2020). I1-3: `maxOrNull()` yields null for missing/empty/all-zero; `?.let` doc write; tests assert `doc` lacks the key when null. I1-4: unconditional `enrichment.lastPublicationYear?.let { doc["lastPublicationYear"] = it }` right after institutionType line; test overwrites prior 2015 with 2026. I1-5: filter verbatim per plan — must `exists enrichedAt` + must_not `exists lastPublicationYear` + prefix `orcidId "EMAIL-"`; test asserts each clause. I1-6: `buildEnrichmentFilters` and both existing `when` branches byte-unchanged (only third branch appended); `EnrichmentScope` new value appended; 21 no-arg calls pass. I1-7: `AuthorEnrichment.lastPublicationYear: Int? = null` in last position. I1-8: `ExpertClassificationService.kt` zero-diff, `VERSION` untouched. S1-1: index.html `+1/-0` (numstat `1 0`); line 600 is byte-verbatim contract block with 28-space indent, placed after enrichBackfillBtn, before `<hr class="dropdown-divider">`; app.js adds exactly the plan's branch (`mode === 'enrichYearBackfill'` → `handleEnrichExperts("LAST_PUBLICATION_YEAR_BACKFILL")`); `EnrichmentStats` gains trailing `lastPublicationYearPending`. |
| 3. Required commands | PASS | Fresh runs with JAVA_HOME=zulu-11: directed tests 34+67+15=116 Kotlin, 0 failures/errors, BUILD SUCCESS; ExpertClassificationServiceTest 30/30, BUILD SUCCESS; `node --check app.js` OK; `git diff --check` clean (boundary + worktree); full `mvn test` → `Tests run: 2960, Failures: 0, Errors: 0, Skipped: 5`, JS 755 pass/0 fail, BUILD SUCCESS. Baseline de228e1 = 2952/0/0/5 + 755 → no regressions; +8 new tests consistent with added tests. |
| 4. Downstream interfaces | PASS | Nothing in child 01 consumed by later children; `ExpertClassificationService` untouched; `expertTypesFilter`/`expertTypePredicate` live in ExpertSearchService.kt / ManualInitialOutreachService.kt — neither in the boundary; no gate changes (no gate-related files in boundary). |

### AUTO_FIX
N/A

### RECORD_ONLY
- ExpertDiscoveryControllerTest.kt (~line 294): existing test helper line rewritten `execution.copy(id = execution.id ?: 1L)` → `taskExecution.copy(id = 1L)` (local rename + dropped null-fallback). Inside an authorized file, functionally equivalent, all tests green; not called for by the plan. No gate violation; note only.

### Required Action
- COMPLETE_CHILD
