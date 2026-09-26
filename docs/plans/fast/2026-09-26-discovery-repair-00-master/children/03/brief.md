# Child 03 Brief — Email text normalization and bounded contact recovery

Approved child plan: `docs/plans/2026-09-26/discovery-repair-03-email-text.md` (exact approved bytes committed as `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read the full plan first; it is authoritative for exact fixture records, tasks, tests, and acceptance criteria.

## Global constraints

- Master plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`; master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Previous child 02 terminal product/code head: `ddc26e020ec692d381b33fe5f575ccb6b14fd595`. Its evidence commit `8e9ffe8113f3a11ccea242e4a691cbf97609e878` and binding commit `4dd0003bb76f2c7992707065a4131b58aa18ba26` are ancestors and must remain before this implementation.
- Preserve master M1–M4 and I-1–I-5: identity only from unique same-source evidence; unknown identity is not email eligibility failure; no schema changes, admission/sending changes, production data cleanup, deployment or scheduling. Do not claim synthetic/probe totals as production recovery.
- Preserve child 01 retained-cursor/`DEDUP_INCOMPLETE` status shape and child 02 OpenAlex retry/request-policy/UNKNOWN behavior. No queue retry/persistence behavior changes beyond the exact child plan.
- Scope is limited to the 8 exact authorized files below. No general OCR, arbitrary whitespace joining, arbitrary separator repair, mail blacklist changes, PDF layout, comma-name or JATS changes.

## Authorized Files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
8. `src/test/resources/discovery/email-text-recall.json`

## Child requirements and invariants

- I-1: Expand only non-nested `{local1,local2}@domain` with comma/semicolon separators and optional whitespace; validate each local part with existing character rules; malformed/empty members cannot yield partial addresses; never bind by expansion order. Repair only `local@` followed by one newline and an immediately valid domain; never cross blank lines, paragraphs, or intervening words. Preserve original text and normalized text as provenance inputs.
- I-2: Add an internal pure `normalizeContactText`; `extract` and resolver use the same bounded contact record/normalized string. Do not globally flatten newlines. Apply existing `(at)/(dot)` normalization consistently without widening name matching. `SOURCE_SHA256` remains derived from original contact text.
- I-3: Set `EXTRACTION_VERSION=20260927`, leave evidence `VERSION=20260925`; unbound new email stays identity-empty and rejected as `IDENTITY_UNRESOLVED`; old cached extraction stays explicitly rejected without re-extraction or cleanup.
- I-4: Preserve M1–M3 eligibility and duplicate non-overwrite semantics.
- Cover all seven verbatim source expressions (expected 30 local expansions per fixture, not 30 predicted experts), W2995022099 wrapped email, synthetic Jane Doe `(at)` equivalence, cross-paragraph/shared/ambiguous/blacklist negative cases, identity version acceptance/rejection, and real extract-to-consumer behavior. Unknown email must cause zero validation/RAW writes; explicit positive goes through existing verification/qualification.
- Emit `target/discovery-plan-acceptance/03.json` from real isolated test/function output, not a hard-coded pass flag.

## Seeded source evidence

The exact input bytes are present in commit `eaa76b197afcc9d66d9b73d77c9459c885307c80` under `docs/plans/2026-09-26/discovery-repair-evidence/diagnosis-source/`. They are lossless copies from the approved audit bundle:

- `evidence/more-probes.json` — SHA-256 `0ba57f83e303bc26bd68a585eb71449ae50d862b553ad12b6e07fafd600c2273`; source `docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/more-probes.json`.
- `evidence/sources/W2995022099/first-two-pages.txt` — SHA-256 `b7814841b104fe08cf95f099831a145d58cf0abeb45c5327fb8498311e92f66c`; source `docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/sources/W2995022099/first-two-pages.txt`; original PDF SHA-256 `e3e9563fe9292d3aa4c8bd470da689698b51d72258ee7a904d8928f965c45c12`.
- Matching `metadata.json`, `fetch.json`, and `result.json`, plus `evidence-sha256.json` and `original-source-sha256.json`, are copied alongside and retain their manifest values. The original PDF itself is not modified or needed for the planned text fixture.

Use these source-copied bytes verbatim for the seven original brace fixtures and wrapped-email case; keep the explicitly synthetic Jane Doe control labeled synthetic. The initial preflight report remains in `execution.md` as historical evidence; this resumption is the same unchanged plan identity with the previously missing source artifacts now committed.

## Required command

`JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PlainTextEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,PdfEmailExtractorTest,DiscoveryPipelineServiceTest`

Use absolute Maven launcher `/opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn` because plain `mvn` fails JAVA_HOME validation here. Run from worktree root; freshly run the full focused command after final edits; report counts. No project-wide suite in the fast-p child loop.

## Downstream interfaces

Child 04 consumes `DiscoveryIdentity` and XML author/email resolution; preserve existing source evidence, identity matching, resolver conflict rejection, extraction/version contracts and test fixture semantics. Keep the added normalizer internal and do not create a new cross-module API.
