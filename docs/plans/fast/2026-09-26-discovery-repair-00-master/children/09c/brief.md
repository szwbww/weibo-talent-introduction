# Child 09c Brief — trusted topic fields and discovery RND classification

Exact approved child plan: `docs/plans/2026-09-26/discovery-repair-09c-scope-classification.md`, committed at `152028fb4f6adf467a5254ed3627bf84c561f6bc` (A1); read full child plan and 09 shared audit. Start only after child09b reaches terminal light verification, using its actual product code head. Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`.

## Authorized Files — exact nine

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt`
9. `src/test/resources/discovery/rnd-scope-evidence.json`

## Invariants

- Preserve verified 01–09b source, identity, ORCID URI/checkpoint, renderer and one `researchFieldIds` fact contract. No extra OpenAlex API requests, fuzzy name or institution-based field inference, new industry vocabulary, send gate, backfill, scheduler enable, candidate admission/revalidation (09d), DB migration, production cleanup.
- I-1: `parseAuthorBase` uses the same existing count-descending top-five topics nodes to read only each `field.id`, not topic/subfield/domain. Accept pure numeric IDs or exact `https://openalex.org/fields/<digits>`; normalize numeric strings, unique ascending. Missing/invalid `field.id` in any selected top-five topic yields null for whole professional fact (no partial outside inference). `AuthorEnrichment` trailing optional field; only trusted author ID/ORCID paths. `ExpertDiscoveryService.updateExpertAcademicFields()` doc map writes nonnull new list explicitly to whichever RAW/CANDIDATE/APPLICATION existing docs can be updated, no missing-layer creates; null never erases prior fact. `enrichedProfile.copy` same fact; preserve identity CAS and all other fields.
- I-2: For `DiscoveryIdentity.isDiscovery(profile)`, at least one valid ID in 09b six-ID `SubjectScopeCatalog` establishes relevant target; all non-target valid IDs = known outside; no IDs = unknown. Mixed relevant retains; no majority/weight requirement, university research staff eligible. Do not claim industrial suitability from provider field alone.
- I-3: Preserve clinical/medical strong-negative precedence; then discovery no IDs→UNKNOWN/RND_SCOPE_UNCONFIRMED, all non-target→OUT_OF_SCOPE/RND_SCOPE_OUTSIDE_TARGET, target ID→existing production/research thresholds. Existing scores unchanged even when outside; non-discovery classification retains prior logic. Fingerprint includes normalized field IDs/discovery marker. No immediate candidate gate in this child.
- I-4: Classification VERSION changes `rnd-v2-2026`→`rnd-v3-20260927`; old JSON readable; non-discovery may write new version without new behavior; sending never compares version. Existing onlyPending scheduler selection effect disclose for release, no automatic backfill.
- Evidence resource `rnd-scope-evidence.json` keeps `real_snapshot` original Gebeyehu/art/business fields, IDs and hashes distinct from explicitly constructed field-ID cases; never attach invented field IDs to real snapshot. Constructed cases: all six positives, mixed, purely medical/agriculture/art/business, top5 missing/invalid, clinical strong negative. Actual `target/discovery-plan-acceptance/09c.json` includes real versus constructed labels, scores/type/negativeEvidence/fingerprint, triple-layer update results/version.
- Required focused JDK11 Maven command includes changed five tests `OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertClassificationServiceTest,ExpertClassificationSchedulerTest,ExpertClassificationAdminControllerTest` plus read-only `ExpertClassificationVersionGateGuardTest`. Maven-bound Node phase included; no project-wide suite.

## Downstream 09d contract

09d consumes field IDs and classification from this child to gate only automatic discovery candidate eligibility, revalidation, and conditional candidate removal. Do not change CandidateEligibilityService, ExpertRevalidationService, ExpertIndexWriterService or send configuration in 09c.
