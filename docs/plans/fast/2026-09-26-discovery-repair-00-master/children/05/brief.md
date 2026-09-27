# Child 05 Brief — original PDF author-contact recall

Approved plan: `docs/plans/2026-09-26/discovery-source-contact-recall.md`, exact approved bytes in commit `b8789cb6062d9110218c08ce8099dba8dddd73e4`. Read it fully; it governs every acceptance criterion and the exhaustive file scope.

## Boundary and global contract

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`. Master plan `docs/plans/2026-09-26/discovery-repair-00-master.md`, master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Child 04 terminal product code head and child 05 product base: `6e2244be0f20d7af0ee710a734f7d252d7160f36`. Child 04 extraction VERSION is `20260928`; evidence VERSION is `20260925`; this child advances EXTRACTION_VERSION only to `20260929` while preserving evidence VERSION, identity envelope, old-cache unsupported behavior, XML route/conflict handling and prior child 03 text normalization.
- Master M1–M4/I1–I5: unique same-source author/email/ID relationship only; ambiguous/shared name/marker/abbreviation remains unbound; preserve email validation, candidate eligibility, create-only/non-overwrite duplicate behavior, sender settings, pause, quotas, download limits and timeouts. No migrations, new database/ES fields, old-data cleanup, deployment, push, scheduling, generic OCR/layout engine, tail-page sampling, or hidden send/admission gates. Scope only to the ten approved files below.

## Authorized Files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
9. `src/test/resources/discovery/source-contact-recall.zip`
10. `src/test/resources/discovery/source-contact-recall.md`

## Source evidence

The original user-owned audit bundle remains read-only in the original checkout at `/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-26-deep-discovery-diagnosis/`. Its full original-PDF archive is `evidence/original-sources.zip` SHA-256 `0607b402b9ffdeadcc8726710d3031277fbc7ee05f1989caf2c7da25bc0f4205`. Use exactly these original binary members and the matching source metadata JSON, without transforming/rearranging PDF pages:

- `sources/W3014974815/source.pdf` SHA-256 `7bc25fa4aa786c3052ec174e4b564905d54a0a508348370b927649d9aae44ad1`, audit `evidence/sources/W3014974815/metadata.json` SHA-256 `8a08806501bc7ba13f0195c72ed33686a9f9c59b58c273aa2698b631032b184a`.
- `sources/W2999309192/source.pdf` SHA-256 `029dab8f480180ef7c0f92dbe2432858c50618a82d0406fa36e9fdfded61ec12`, audit `evidence/sources/W2999309192/metadata.json` SHA-256 `3ae9f721ac32cccd26a87752b3c60cc056b18487def1b7f8b50d7ccba845114b`.
- `sources/W2907492528/source.pdf` SHA-256 `de24ede0f541343b8bce39289120c835a9a2cc6a29e24877430f87f205e84280`, audit `evidence/sources/W2907492528/metadata.json` SHA-256 `0c6b164c459fa5c12d917416bd5f726589d9461f0420ee37633e8ad4aefda74e`.

`original-source-sha256.json` and `evidence-sha256.json` in that audit directory preserve provenance. Copy the exact three PDFs/metadata into the authorized test ZIP with a manifest and label real versus synthetic controls; do not alter the original bundle or write any other production file.

## Child invariants and required output

- I-1: Use only author-region text and author-specific footnote or independent contact paragraph from the same original PDF. Unique `*`, `†`, `‡` contact markers can link one author and email; purely institutional numeric affiliation is not contact evidence. Full author name must uniquely match same-paper metadata. Shared marker, name, abbreviation, cross-column splicing and references remain identity-empty.
- I-2: Independent paragraph needs explicit Email/e-mail and exactly one author; abbreviation like S. Pan / P. S. Yu must uniquely match full surname and every initial across page author region and same-paper metadata. Never choose by local-part/proximity/list order. Preserve true page/snippet provenance in SOURCE_SHA256.
- I-3: Use one bounded PDFBox TextPosition-based helper during the existing PDDocument lifecycle; do not redownload. Combine new explicit claims with existing claims through conflict rejection. No PDF inference in layoutless CORE/HTML; no last-page read until 06. EXTRACTION_VERSION=20260929, evidence VERSION unchanged, old cache rejected without automatic re-extraction.
- I-4: Unknown identity triggers zero validation and RAW writes; explicit identity still uses existing validation/eligibility and duplicate non-overwrite. Four exact real pairs: k.maier-hein@dkfz.de→Klaus H. Maier-Hein; davidechicco@davidechicco.it→Davide Chicco; shirui.pan@monash.edu→Shirui Pan; psyu@uic.edu→Philip S. Yu. Four other shared addresses in W2907492528 remain unbound. Demonstrate authorized negative cases, and real PDFs through actual download stub→PDFBox→resolver→consumer/writer rather than mock identity parsing.
- Emit `target/discovery-plan-acceptance/05.json` from isolated observed parser/request/consumer/pipeline behavior: fixture IDs and hashes, real page/snippet, boundaryCases, versionCases, checkpoint/terminal, and necessary ES request results. No hard-coded pass flags or invented observations; note any direct-consumer versus pipeline difference explicitly. Do not generate human `-acceptance.md` now.

## Required command

`JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,JatsXmlEmailParserTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest`

Run from worktree root with JDK11. The command includes the repo-configured Node phase; do not run a separate project-wide suite. Freshly rerun after final edits and report exact counts.

Before child 05 implementation, the required focused baseline ran against child04 terminal code: exit 0, BUILD SUCCESS, 327 backend tests/0 failures/0 errors/0 skipped (`artifact://345`).

## Downstream child 06 interface

Preserve current PDF contact-layout helper and extractor APIs for the planned next-stage first-two-plus-tail-one PDF sampling, while this child itself keeps first maxPages sampling and existing size/time limits. Child 06 builds on EXTRACTION_VERSION=20260929, source PDF evidence, resolver conflict semantics and provenance.
