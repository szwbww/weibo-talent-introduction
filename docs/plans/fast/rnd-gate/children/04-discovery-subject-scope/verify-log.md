## Light Verification: LIGHT_PASS
Child: 04-discovery-subject-scope (docs/plans/2026-08-25/04-discovery-subject-scope.md)
Boundary: b2fdf028d16b1669c9c3f481fb5b94abd77d4e60..ee152d2b21030f6b86da16769f638b29d4be094b
Verifier: Verify04Light

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-only b2fdf02..ee152d2` = 16 files: 4 docs (03 execution.md/fix-log.md/verify-log.md, ledger.md — docs-only evidence commits) + exactly the 12 authorized product/test files: SubjectScopeCatalog.kt (new), PaperSearchCriteria.kt, OpenAlexDataSource.kt, ArxivDataSource.kt, ExpertDiscoveryService.kt, ExpertDiscoveryScheduler.kt, application.yml, OpenAlexDataSourceTest.kt, ArxivDataSourceTest.kt, ExpertDiscoveryServiceTest.kt, ExpertDiscoverySchedulerTest.kt, SubjectScopeCatalogTest.kt (new). No other product/test file changed.|
|Plan and invariants|PASS|I4-1: `primary_topic.field.id` only in SubjectScopeCatalog.kt:44 (ids listOf("22","31","17","25","21","15") at :25) + the two required hard-coded test anchors (SubjectScopeCatalogTest.kt:25, OpenAlexDataSourceTest.kt:168); arXiv cats "cs/eess/cond-mat/physics" only at SubjectScopeCatalog.kt:32, consumed via `cat:$it*` (ArxivDataSource.kt:39); single fragment `primary_topic.field.id:22|31|17|25|21|15`; `domain.id:!4` only in comment (SubjectScopeCatalog.kt:22), zero code usage. I4-2: OpenAlex `parts += openAlexFilterParts(...)` appended after keywords branch (OpenAlexDataSource.kt:71-72); null→emptyList→byte-identical; hard-coded anchors `filter=is_oa:true,publication_year:2022-2025,authorships.institutions.country_code:!CN&per_page=100&cursor=*` and `search_query=all:*` (OpenAlexDataSourceTest.kt:153-156, ArxivDataSourceTest.kt:44-46); arXiv 3-branch at ArxivDataSource.kt:34-43, keywords branch verbatim + unaffected-by-scope test. I4-3: six `add(...)` lines all preserved (ExpertDiscoveryService.kt:217-222); closure condition adds `&& name !in SubjectScopeCatalog.excludedSources(criteria.subjectScope)` (:211); excludedSources(RND_TARGET)=={EUROPE_PMC,PMC_OA} (SubjectScopeCatalog.kt:64); manual-override test `resolveEnabledSources scope exclusion overrides manual sources selection`. I4-4: `if (europePmcProperties.enabled) add({ europePmc }, ...)` (ExpertDiscoveryService.kt:217) + regression test `excludes EUROPE_PMC when disabled even with empty sources`. I4-5: yml quota defaults only — OPENALEX 1200→2500, ARXIV 200→800, CROSSREF 500→300 (application.yml:169,183,192); no *Properties.kt in range diff; EuropePmcProperties.enabled default true untouched. Scheduler criteria `subjectScope = SubjectScopeCatalog.RND_TARGET` (ExpertDiscoveryScheduler.kt:43) + scheduler test. PaperSearchCriteria.subjectScope trailing `= null` (PaperSearchCriteria.kt:12). coreKeywords declared but unwired (SubjectScopeCatalog.kt:55, comment :37-40/:54; no main consumer). EuropePmcProperties appended last in constructor (ExpertDiscoveryService.kt:73).|
|Required commands|PASS|JDK 11.0.15 Zulu confirmed. `mvn test`: exit 0, Tests run: 2878, Failures: 0, Errors: 0 (Skipped 4; vs child-03 baseline 2863 = +15 new tests: 4+2+4+4+1). Targeted `mvn test -Dtest='SubjectScopeCatalogTest,OpenAlexDataSourceTest,ArxivDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest'`: exit 0, BUILD SUCCESS, Tests run: 103, Failures: 0, Errors: 0, Skipped: 0. `git diff --check`: exit 0, no output.|
|Downstream interfaces|PASS|No later child consumes child 04 → N/A. Crossref/Orcid/Core query construction untouched: CrossrefDataSource.kt / OrcidDataSource.kt / CoreDataSource.kt absent from range diff. EuropePMC/PMC_OA enabled defaults NOT changed: EuropePmcProperties.kt:11 (`enabled: Boolean = true`) and PmcOaProperties.kt:9 (`enabled: Boolean = false`) unchanged, europe-pmc/pmc-oa yml sections not in diff.|

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
