# Child 08 Brief — discovery source reason display and truthful funnel label

Approved child plan `docs/plans/2026-09-26/discovery-repair-08-source-report.md` (approved bytes commit `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read entire plan, including S-1 style/DOM contract; it is the authority.

## Boundary and constraints

- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`.
- Child07 terminal product/code head `a5cf6fcbc2af3567c0b39203be6452b8921a4bee` is child08 product base. Separate evidence commit `472ab2d...` precedes this child. Master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Preserve M1–M3: unique same-source person/email evidence, email validation, eligibility, create/non-overwrite, manual pause, download/runtime budgets, sending config. No schema/stat counter changes, actual methodUsed field, historical log rewrite, URL/identity logic, sending/admission changes or 09 subject scope. 08 modifies source funnel wording and shared UI renderer only.

## Authorized Files — exhaustive six

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
2. `src/main/resources/static/app.js`
3. `src/main/resources/static/index.html`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
5. `src/test/js/taskRecordsSemantics.test.js`
6. `src/test/resources/discovery/task-20240-by-source.json`

## Source replay

Read-only original audit `docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/source-stop.json`, SHA-256 `64f6bb57bd2046399416f29db8a07f6ea08687f5ebcf180f1bbf0c2c3fa42508` in evidence-sha256.json. Derive fixture from `task[0]` fifth TSV field using `split(limit=5)` then `stats.bySource`; do not manually type counts or use intermediate snapshot3. 20240 by-source numbers: original 635 email/6 valid/4 RAW indexed/4 promoted, IDENTITY_UNRESOLVED 629, stop SEARCH_FAILED, HTTP_403 323. Numbers are recorded facts, not sums across overlapping reasons.

## Explicit invariants

- I-1: Existing `bySource` `filterReasons`, `failureReasons`, `stopReason` rendered in separate sections, all persisted keys not first three/truncated; old missing fields show `未记录`, never guess zero. Preserve counters; overlapping failure categories do not sum to people.
- I-2: `extractionMethod` remains source preference; heading `首选方式` not actual observed method. Two new discovery funnel log sites use `过滤（含身份未确认）` with existing filterReasons, not `资格淘汰629`; do not rewrite old logs or compute new qualification count.
- I-3/S-1: Same `renderBySourceTable` for live/historical `EXPERT_DISCOVERY`, preserve `isEnrichmentBySource` branch. Reuse only existing `table-wrap`/`data-table` and derived table/details CSS, no new CSS, inline style, DOM class or tooltip library. Exactly plan-specified table and `<details>` structure (8 columns, filtering/failure/stop blocks). All external strings escaped, numeric only finite nonnegative, missing fields `未记录`. Explicit existing CSS changes local padding to 6px; table min-width 720px in horizontally scrollable wrapper, th 11px/body 13px/details 11px, summary color #1e40af. `index.html` only bump three resource cache references together to `20260926-discovery-repair`, preserve loading order.
- I-4: Existing identity/qualification/write/pause tests preserved, no sender or migration scope.
- Generate actual `target/discovery-plan-acceptance/08.html` from renderer plus real local styles.css reference, and `08.json` input/output summary via authorized JS test. No human acceptance checklist yet. Visual inspect actual browser at 800px, expand details, horizontal scroll, compare live/historical, escaped script, old missing fields/enrichment, font/padding and asset keys. If file access prevents browser open, use local serving but do not create production service or additional source files.

## Required focused commands

1. `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest`
2. `node --test src/test/js/taskRecordsSemantics.test.js`
3. `node --check src/main/resources/static/app.js`

Maven includes configured Node tests. No whole-project suite, formatter, linter or CSS modifications.

## Downstream 09

09a acts on `ExpertDiscoveryService` after 08 and must retain source reporting labels/fields. 09c/09d later consume same file from verified 08 plus 09a. Do not pre-implement 09 scope or change candidate admission in 08.
