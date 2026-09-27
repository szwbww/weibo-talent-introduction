## Light Verification: LIGHT_PASS
Child: 09b — `docs/plans/2026-09-26/discovery-repair-09b-scope-facts.md`
Boundary: `f9caa0b6fa5a1893aa37a6dd8612c8b930635eaf..3df3d79a1806081639ad318fb6b638c59f5bca85`
Verifier: ScopeFactVerifier (independent of ScopeFactImplementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact `git diff --name-only` under `src` lists only nine of the ten approved files; `OperatorStatusWriteSeamGuardTest.kt` has zero diff and passes unchanged. No discovery writer, promotion writer, candidate/send path, or DB migration changed in this product boundary. |
| Plan and invariants | PASS | `ExpertProfile.kt:42` adds only trailing `researchFieldIds: List<String>? = null`; all three ES mapping files declare the same `keyword` property and retain root `dynamic=false`. `ExpertSearchService.kt:465-509,548-554,584-599` reads a textual array without inferring from free text, preserves missing/null as null and empty as empty, retains hit `_id`, and includes field in shared `sourceFields()` used by profile search/scroll/find paths. `SubjectScopeCatalog.kt:25-32,67-71` exposes an unmodifiable set derived from the existing six IDs, with the old OpenAlex query fragment unchanged. `ExpertIndexService.kt:36-70,75-146` retains three-layer mapping bootstrap and per-field conflict reporting/continued layer processing; focused bootstrap tests exercise existing/new APPLICATION and RAW conflict. Actual isolated `target/discovery-plan-acceptance/09b.json` contains three keyword/dynamic=false/projected layers, absent/null/empty/`["17","22"]` readback, original hit `_id` values, and six target IDs. |
| Required commands | PASS | Fresh JDK11 command `env JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertSearchServiceTest,ExpertIndexServiceTest,SubjectScopeCatalogTest,OperatorStatusWriteSeamGuardTest` exited 0 with `BUILD SUCCESS` (`artifact://796`): JUnit 70+10+8+1=89, 0 failures/errors/skips; Maven-bound Node 1,194 pass, 0 fail/skipped. Baseline `artifact://749` was JUnit 85, 0 failures/errors/skips, exit 0, and Node 1,194 pass. |
| Downstream interfaces | PASS | 09c retains the exact `researchFieldIds: List<String>?` fact contract and `SubjectScopeCatalog.targetOpenAlexFieldIds()`/`isTargetOpenAlexFieldId()` predicates. Canonical top-five author extraction, sorted/deduplicated writes across three layers, classification, and 09d candidate admission remain downstream; 09b does not invent facts or change eligibility/send behavior. Existing full-`_source` promotion implementation is untouched. |

### AUTO_FIX
- N/A; no proven unique in-scope repair.

### RECORD_ONLY
- None.

### Required Action
- COMPLETE_CHILD