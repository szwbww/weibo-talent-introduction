# Light Verification Log — 01-region

（由轻量验证者写入；append-only）

---

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01-region (docs/plans/2026-10-14/batch-filter-01-region.md)
Boundary: b02eeb6405b27d5d4f30a32d17019e19c855483d..bba581fe011730b52ad05d35e6248c9599372c2d
Verifier: Verify01Region

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-only b02eeb6..bba581f` lists exactly the 4 authorized product/test files; `git show --stat bba581f` = those 4 only (218 insertions / 13 deletions). Remaining files in the range are fast-p evidence docs under `docs/plans/fast/**` seeded by prior commits `f4e521b`/`6a200c4`, not product/test.|
|Plan and invariants|PASS|I-1: `BatchExecutionModels.kt:241` `return CountryContinentMapping.toRegion(profile.country) in regions`; `CountryContinentMapping.kt:259-260` maps null/blank/unmapped→`REGION_OTHER`, and known countries keep their real region (Japan≠Other). `regions` empty is guarded by `mismatchKeys` `regions.isNotEmpty()` (`BatchExecutionModels.kt:207`). Non-discovery branch unchanged. I-2: `ManualInitialOutreachService.kt` `withDiscoveryUnmappedCountryBranch` (calls at `:2025-2027`, def `:~2058+`) wraps the existing `regionsFilter` in `bool.should=[regionFilter, {must:[term tags=discovered], must_not:[exists country]}], minimum_should_match=1` only when `REGION_OTHER in regions`, else returns the filter verbatim — union-only, no existing condition added/narrowed. I-3: diff touches read-only predicates/filters only; no write call, no identity admission, dedup, snapshot or account mutation introduced. MAIN M-2: `ManualInitialOutreachServiceTest` `discovered experts without a country pass the Other sieve in preview and run` asserts `countBySnapshot`==`run` same-snapshot parity (`preview.totalSendable == result.total == 2`). MAIN M-4: only reads; no SMTP/provider/write in new tests (memory fixtures + Mockito read stubs). Tests: `BatchRecipientSelectionServiceTest` new matrix (null/""/"   "/"Other"/"Atlantis" admitted under Other; Japan/China rejected; empty regions unrestricted; non-discovery country OR nationality regression) and `ManualInitialOutreachServiceTest` ES-wrapper structure + parity tests.|
|Required commands|PASS|Fresh run in worktree: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false` → BUILD SUCCESS, exit 0, 03:13 min (2026-10-14). Surefire: BatchRecipientSelectionServiceTest 13/0/0/0, ManualInitialOutreachServiceTest 185/0/0/0, CountryContinentMappingTest 9/0/0/0, ExpertSearchServiceTest 75/0/0/0 = 282 tests, 0 failures/errors/skips. vs baseline (231 for the 3-class run, CountryContinentMapping 9, ExpertSearchService 75): no new failures; JS gate also green (1657 pass / 0 fail).|
|Downstream interfaces|PASS|(a) `RecipientScope.matchesRegion` (`BatchExecutionModels.kt:241`) is the single discovered-region rule (`toRegion(country) in regions`), reached by all consumers through public `matchesExpert`/`matchesEsTarget`; no duplicate old function reintroduced. (b) `withDiscoveryUnmappedCountryBranch` is the `buildEsFiltersForLevel` seam for the Other branch; it is a union-only widening of the region filter, so child 02 can reuse it as a base-set query without narrowing non-region targets. Test `ES region sieve widens ... (I-2)` pins both the wrapper shape and the verbatim `regionsFilter(["Europe"])` passthrough.|

### AUTO_FIX
- N/A

### RECORD_ONLY
- `BatchExecutionModels.kt:300-312` `matchesDiscoveryOutreach` still encodes the old discovered rejects (`country.isNullOrBlank()` / `toRegion == REGION_OTHER` → false) and its KDoc at `:292-296` still says "空/未映射值不等于 Other", now inconsistent with the fixed `matchesRegion`. It is dead code (`grep -rn matchesDiscoveryOutreach src/main src/test` → only the definition plus a `InitialOutreachService.kt:63` comment stating it is no longer called), so it has no effect on I-1; call out only.
- The I-2 ES branch keys solely on `tags == "discovered"` (brief I-2 verbatim), while the memory predicate `isDiscoveryOutreach` (`:288-289`) also matches `DiscoveryIdentity.isDiscovery(profile)` and the `待确认` tag. A discovered-shaped doc lacking the `discovered` tag with no `country` field could still be dropped by the coarse filter; write path writes both in one doc, so no proven defect — recorded for child 02's baseline/diagnostic母集 check.

### Required Action
- COMPLETE_CHILD
