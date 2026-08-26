## Light Verification: LIGHT_PASS
Child: 01-expert-list-type-filter (docs/plans/2026-08-25/01-expert-list-type-filter.md)
Boundary: f2935072c819a9167e75220a6a959b0769462fde..7c703e3d5e51c165ee6c75f316de0f018c44e8df
Verifier: Verify01Light

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --stat f2935072..7c703e3` over product/test sources = exactly 7 files, all authorized: ExpertSearchService.kt, ExpertIndexController.kt, index.html, app.js, ExpertSearchServiceTest.kt, new src/test/js/expertTypeFilter.test.js, OperatorStatusWriteSeamGuardTest.kt. A1 guard diff = exactly 3 NoiseSite line updates (ExpertIndexController.kt 90->91, 431->436; ExpertSearchService.kt 498->542) with contexts verbatim; new line numbers verified live at :91/:436/:542 (same context substrings). Intermediate commits in range are docs-only (plan/ledger/briefs). No other product/test file changed. |
|Plan and invariants|PASS|I1-1: ALLOWED_EXPERT_TYPES at ExpertSearchService.kt:113 = `ExpertType.values().map{it.name}.toSet() + "UNCLASSIFIED"` (enum-derived, M-2); require() fail-fast at :125; test asserts exact equality + size == enum.size+1; no second filter whitelist in main sources (grep of the 6 enum names outside ExpertClassification.kt/ExpertSearchService.kt only hits enum references in ExpertClassificationService.kt). I1-2: expertTypesFilter returns null on empty/blank (code :122 + test); buildExpertFilters adds no element on empty (test: filter array identical to default, size 2 with tag+discipline only). I1-3: single `bool.should` + `minimum_should_match:1`, no top-level filter key (code :129-136; test asserts `filter.keys == setOf("bool")`). UNCLASSIFIED = `must_not exists expertClassification.type` (code :141-148 + test); never a term string value (grep: no such term mapping). I1-4: trailing `expertTypes: List<String> = emptyList()` on searchExperts (:324) and buildExpertFilters (:1095); 3 aggregation callsites :993/:1038/:1148 byte-identical (diff shows zero changes there; they pass no expertTypes); searchExperts passes expertTypes at :329. I1-5: expertTypeActiveValues at app.js:4854; loadContacts append :5023 (:4872 typeof-guarded read); initExpertGateFilter append :12184; updateFilterBadge count :11898; change-listener array deliberately NOT extended, chip click handler (app.js:11961-11972) toggles .active + reloadContactsFromStart(). S-1: index.html:537-547 skeleton verbatim — classes only toolbar-label/tag-select/tag-chip, no style=, no new classes, placed after discipline label before H-Index. S-2: row chip app.js:5174 `class="tag-chip"` no .active, title scores escapeHtml'd, absent when expertType missing. S-3: discipline option values "" /STEM/HUMANITIES/UNCLASSIFIED verbatim, only STEM visible text changed. styles.css diff = 0 lines. M-3 honored (no should:[]+minimum_should_match:1 anywhere). |
|Required commands|PASS|Full `JAVA_HOME=zulu-11 mvn test`: exit 0, BUILD SUCCESS; surefire sum Tests run=2839, Failures=0, Errors=0, Skipped=4 (baseline exit 0, same counts; reports fresh 09:46). `mvn test -Dtest=ExpertSearchServiceTest`: exit 0, Tests run=65, Failures=0, Errors=0 (report fresh 09:43). `node --test src/test/js/expertTypeFilter.test.js`: exit 0, 4 pass 0 fail. `node --test src/test/js/*.test.js`: exit 0, 737 pass 0 fail. `node --check src/main/resources/static/app.js`: exit 0. `git diff --check`: exit 0, no output. |
|Downstream interfaces|PASS|`ExpertSearchService.ALLOWED_EXPERT_TYPES: Set<String>` companion val (ExpertSearchService.kt:113-114), enum-derived + "UNCLASSIFIED" exactly as named. `expertTypesFilter(types: List<String>): Map<String, Any>?` (ExpertSearchService.kt:122). Trailing `expertTypes: List<String> = emptyList()` on both buildExpertFilters (:1095) and searchExperts (:324). Child-01 -> 02 contract exists exactly as specified. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
