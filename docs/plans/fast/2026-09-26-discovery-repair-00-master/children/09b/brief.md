# Child 09b Brief — structured OpenAlex field-ID fact contract

Exact approved child plan: `docs/plans/2026-09-26/discovery-repair-09b-scope-facts.md` (A1 plan-only commit `152028fb4f6adf467a5254ed3627bf84c561f6bc`), plus shared 09 scope audit. Read entire plan. Only start after 09a terminal LIGHT_PASS, using its code head as product base; do not use this brief's current preparation time as authority to start.

## Global invariants

- Single retained worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`; master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Preserve prior 01–08 source/identity/eligibility/UI and 09a ORCID query/checkpoint semantics. No new DB table/index, backfill, production ES mutation, send configuration, frontend control, candidate admission (09d), field extraction/classification (09c), or migration. This child adds only one cross-layer fact contract, with no immediate candidate behavior.

## Authorized Files — exhaustive ten

1. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt`
4. `src/main/resources/es/orcid_info_raw.json`
5. `src/main/resources/es/orcid_info_candidate.json`
6. `src/main/resources/es/orcid_info_application.json`
7. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

## Explicit acceptance

- I-1: Only `ExpertProfile` trailing default `researchFieldIds: List<String>? = null`; exactly same-named keyword mapping in RAW/CANDIDATE/APPLICATION. Stored list de-duplicated, ascending numeric OpenAlex field-ID strings. Missing/null/empty means no usable structured evidence, never known outside target. `ExpertSearchService` explicitly reads it in all profile/sourceFields paths without inventing from researchFields/discipline/institution; old documents and constructor remain compatible. Mapping roots keep dynamic=false; bootstrap checks on all three existing/new indices, conflict reports failure.
- I-2: Extend existing `SubjectScopeCatalog` with read-only target ID set/predicate, exactly `22,31,17,25,21,15`. Keep existing query fragments and six target fields; university research staff in those fields remain eligible for later classification, not filtered as non-corporate/non-patent.
- I-3: Existing full `_source` promotion transparently passes this new field; this child does not modify writer. 09c's `updateExpertAcademicFields` is only later explicit fact writer across three layers. `OperatorStatusWriteSeamGuardTest` may receive mechanical ExpertSearchService line-number corrections only, no snippet/whitelist broadening. No send/DB/production writer diff.
- Generate actual isolated `target/discovery-plan-acceptance/09b.json` from mapping/reader checks: field type in all layers, old absent/null/empty and actual ["17","22"], target set, real ES `_id`. No source text assertions in lieu of actual behavior.
- Required focused JDK11 command includes four modified tests `ExpertSearchServiceTest,ExpertIndexServiceTest,SubjectScopeCatalogTest,OperatorStatusWriteSeamGuardTest` (Maven-bound Node phase). Use available JAVA_HOME `/Users/lukai/.jenv/versions/zulu64-11.0.15`, Maven `/opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn`; no full suite.

## Downstream 09c/09d

`researchFieldIds` is the one structured fact contract. 09c populates it only from trustworthy top-five OpenAlex author `topics.field.id` in existing enrichment path and classifies using catalog; 09d gates automatic discovery candidate eligibility and revalidation. Do not implement either here.
