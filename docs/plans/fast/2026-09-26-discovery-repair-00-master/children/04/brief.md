# Child 04 Brief — PMC routing and structured XML author contacts

Approved child plan: `docs/plans/2026-09-26/discovery-repair-04-xml-route.md` (exact approved bytes committed as `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read the full plan first; it is authoritative for exact fixture records, tasks, tests, and acceptance criteria.

## Global constraints

- Master plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`; master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Previous child 03 terminal product/code head: `3fc33d82463cb63602ff41e8a633ef1cd57b4d8e`. Child 03's evidence/binding commits remain ancestors; use its implementation as product base.
- Preserve master M1–M4 and I-1–I-5: bind only unique same-source evidence; do not join identities by name across contributors or sources; preserve existing validation, qualification, create-write, duplicate non-overwrite, sending configuration, pause/quotas/download limits; no schema, persistent fields, cleanup, data deletion, deployment, scheduling, remote push, hidden send/admission gate or unrelated extraction behavior. Do not claim probe or fixture totals as production recovery.
- Child 03 established `EXTRACTION_VERSION=20260927` and leaves evidence `VERSION=20260925`; this child advances extraction version exactly to `20260928`, keeps VERSION unchanged, and retains old-cache unsupported behavior (no automatic re-extraction/allowlist/cleanup). Preserve child 03 email normalization, source identity matching, conflict rejection, and downstream consumer interfaces.
- Only the exact 10 authorized files below may change. No DOI/PMID mapping request, paid-content download, OCR, transliteration/name guessing, generalized body-author parsing, database/ES/schema changes, or send-path change.

## Authorized Files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
10. `src/test/resources/discovery/xml-route-recall.zip`

## Child requirements and invariants

- I-1: Derive candidate PMC IDs only from `ids.pmcid` and structured `locations.landing_page_url`; accept only the plan's exact hosts/protocols/path forms, validate the full path after removing trailing slash/query, and use an ID only if all valid candidates agree. Conflicts produce null. Do not add remote mapping requests. Existing XML OpenAlex author-ID enrichment remains tied only to unique ORCID equality.
- I-2: For OpenAlex/Core display names, only a single comma with nonempty sides means `family, given`; preserve current behavior for no comma; do not guess for multiple commas/empty sides. In JATS, use one direct `<name>` first; otherwise one `<name-alternatives>` with one complete name, or a unique complete `xml:lang="en"` choice. Ambiguous alternatives retain email with empty identity. Never merge contributors by name; conflicting multi-ORCID remains null.
- I-3: Ignore numeric text only in the direct label node of corresp/fn targets; do not ignore ordinary body digits. Bind xref only when its ID target and owner are unique and no explicit conflict exists. Parse only this article's `sec-type=contrib-info` contributor-information section; exclude ref-list/sub-article; require one complete author name plus email in a standalone paragraph and unique front-contributor match; merge all routes through existing candidate conflict resolution.
- I-4: Set `EXTRACTION_VERSION=20260928`, preserve evidence VERSION=20260925; JATS name is XML identity, not a basis for attaching OpenAlex ID. Preserve child03 resolver/consumer interfaces and old-version rejection.
- I-5: Preserve master business boundaries. Unknown identity must not call email validation or write RAW; explicit identity still goes through existing validation/qualification; duplicates do not overwrite; same-name different-email and one person with two explicit emails retain their specified behavior; pause and source limits remain intact.
- Prove the exact plan acceptance criteria, including PMC host/query/conflict rejection, comma-name controls, three real XML contacts, synthetic numeric-label/name-alternatives controls, version/cache behavior, existing shared-note tests, and end-to-end real metadata→XML parser→consumer behavior. Emit actual isolated output as `target/discovery-plan-acceptance/04.json`; no hard-coded pass flag.

## Seeded source evidence

The exact source bytes were copied without alteration from the approved audit bundle into commit `eaa76b197afcc9d66d9b73d77c9459c885307c80`'s evidence tree, then added losslessly under `docs/plans/2026-09-26/discovery-repair-evidence/diagnosis-source/evidence/` in a child-04 evidence seed commit before implementation. Inputs include `round2/PMC7759461.xml`, `openalex-pmc-work.json`, `PMC7759461-fetch.json`, `contributor-information.xml`, the numeric-label, name-alternatives, direct-name and reference controls, `pmc-results.json`, and `sources/W4385245566/{metadata.json,fetch.json,result.json,first-two-pages.txt,all-pages.txt}`. The adjacent `evidence-sha256.json` and `original-source-sha256.json` are copied provenance manifests. Use these byte-identical inputs in the planned test ZIP; label synthetic controls as synthetic and real source bytes as real. The original audit bundle is in the controller's source worktree; do not modify it.

## Required command

`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,CoreDataSourceTest,JatsXmlEmailParserTest,EuropePmcDataSourceTest,PmcOaDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,DiscoveryPipelineServiceTest`

Use the absolute Maven launcher `/opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn`; JDK 11 at `/Users/lukai/.jenv/versions/zulu64-11.0.15` is available in this worktree. Run from the worktree root and freshly execute the full focused command after final edits; record exact test/failure/error/skip counts. No project-wide suite in this child loop.

## Baseline

Before child 04 implementation, the exact required focused Maven command completed successfully at base `3fc33d82463cb63602ff41e8a633ef1cd57b4d8e` (exit 0; JDK 11; Maven 3.9.11). Maven reported 342 backend tests, 0 failures, 0 errors, 1 skipped; its configured exec phase also ran 1,193 Node tests, 0 failures, 0 skips. Final post-change verification must freshly rerun the complete required command.

## Downstream interfaces

Child 05 consumes the `DiscoveryIdentity` v20260928 output and existing unique author/email resolver behavior. Preserve the identity envelope, source conflict rejection, extraction version semantics and all child03 contracts; do not fold PDF parsing changes into this child.
