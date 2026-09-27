# Child 06 Brief — bounded PDF tail contacts and mailto evidence

Approved child plan: `docs/plans/2026-09-26/discovery-repair-06-pdf-coverage.md` (exact approved bytes committed as `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read the full plan first; it is authoritative for exact fixture and acceptance criteria.

## Global and prior-child constraints

- Master `docs/plans/2026-09-26/discovery-repair-00-master.md`; master base `64c0394a940bd79c2ecc04e5c497650f045faa75`. Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`.
- Child 05 terminal product/code head and child 06 product base: `0c1489dd3734965918ef0271754480967391b38f`. Its bounded PDF TextPosition helper, original three-PDF evidence, same-email conflict resolution, and single PDDocument lifecycle must be preserved. EXTRACTION_VERSION was 20260929; child06 advances only to 20260930. Evidence VERSION stays 20260925; old nonempty cached results rejected without re-extraction, no schema/payload change.
- Preserve master M1–M4/I1–I5: unique same-source identity evidence, shared/ambiguous unbound; existing email validation, candidate qualification, create/non-overwrite duplicate semantics, sender configuration, pause, quota, download size and time limits. No migrations, production cleanup/deletion, deployment, scheduling, OCR, all-page scans, more download URLs, link-rectangle identity guessing, hidden send/admission gate, or 09 functionality.

## Authorized Files — exhaustive 10

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt`
4. `src/main/resources/application.yml`
5. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
9. `src/test/resources/discovery/pdf-contact-coverage.zip`
10. `src/test/resources/discovery/pdf-contact-coverage.md`

## Original source evidence

Read-only source archive is `/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/original-sources.zip` (SHA-256 `0607b402b9ffdeadcc8726710d3031277fbc7ee05f1989caf2c7da25bc0f4205`). Original source archive members and SHA-256, with matching metadata JSON under that audit bundle's `evidence/sources/<work>/metadata.json`:

- W4288039037 PDF `893779fb4224db40396710d6f8ecb413977b9c79b01e9b3fd62d20c5ca4bed93`, metadata `3c4145269737b89f85e6cbdff7a24b2e490fdf833a85f3173d5d8e81cfe5a7cd` — original 62-page contact-page positive.
- W4292779060 PDF `f61786a8da8169ec86c742cb5292ee8df68b6f6bc2915a10be7ae1fe4e71cff7`, metadata `a3fcf377781865822ab1cca1ba587c4e4b2b0c32bfb62e7cb8969a43097d7c91` — source-mismatched metadata, explicit identity-empty negative.
- W4385245566 PDF `82f7e32dc25bd5f25de5e09db4ce5622bc9a115f61b6434d0350e39ef161a1ee`, metadata `42c50e1926c0eab8692393f87c3c61bba15bf5366798ea20d34dc2ab31d6ed9e`.
- W4293584584 PDF `06707d1b9009d08e837ce3c02d010d84af60df51b8be3839203117bd2e705246`, metadata `f8ab107d556b83346e89e0d5e2e55da0e05c38ff8cf9d9c372de4dda0742e591`.

Real link evidence: same audit bundle `evidence/missed-pdf-links.json` SHA-256 `1449c787b03e2724ec3e057fddd90b6e2c0f006d28dfdcdfd1a6f384e5a0c1f8`; contact-page image is additional read-only source. Copy full original PDFs and JSON into authorized test ZIP with provenance manifest; do not crop/rearrange the 62-page PDF or modify user source audit.

## Plan invariants and acceptance

- I-1: Existing `maxPages` remains count of leading pages. Add public PDF property `tailPages` default 1, only 0/1 accepted at initialization; application.yml `pdf-extraction.tail-pages: ${PDF_TAIL_PAGES:1}` in existing config. Selected pages are unique union of first `maxPages` and final `tailPages`: 62 pages default [1,2,62]; 1-page [1]; 2-page [1,2]; tailPages=0 [1,2]. Same deadline checked between pages, not a reset budget; no claim about interrupting internal PDFBox calls. Preserve 10MiB download cap.
- I-2: On selected pages only, inspect PDAnnotationLink/PDActionURI with mailto; strip query but not parse cc/bcc; exactly one valid mailbox through existing normalization/blacklist/dedup. Mailto yields email clue only, no new author identity; existing proven text owner of same mailbox remains. Real diagnostic's 11 mailto targets in three PDFs are clues, not promised experts. Misidentified W4292779060 two emails stay identity-empty.
- I-3: Tail page must have explicit contributor contact region or independent author-Email block per child05 rules, matched uniquely to same-paper metadata; no arbitrary references/foreign authors. Five exact real contacts from W4288039037 page 62: Salvatore Cuomo/salvatore.cuomo@unina.it; Vincenzo Schiano Di Cola/vincenzo.schianodicola@unina.it; Fabio Giampaolo/fabio.giampaolo@unina.it; Gianluigi Rozza/grozza@sissa.it; Maziar Raissi/mara4513@colorado.edu. With isolated external validation/qualification pass, RAW=5/CANDIDATE=5. Bump extraction version to 20260930 only, proof VERSION unchanged.
- I-4: Unknown identity zero verification/RAW writes; invalid email zero RAW; qualification rejects RAW=1/CANDIDATE=0; duplicate non-overwrite; same-name/different-email and one person/two explicit mailboxes remain separate; pause blocks new consumption. Keep version/cache and pipeline terminal behavior explicit.
- Generate `target/discovery-plan-acceptance/06.json` from actual isolated parser/request/consumer/pipeline results with source hashes, selectedPages, mailto clues versus verified identities, boundaryCases, versionCases, checkpoint/terminal and ES request counts. No hard-coded success flags or invented external requests. Do not generate human `-acceptance.md`.

## Required command

`JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,SourceAuthorEmailResolverTest,OpenAlexDataSourceTest,CoreDataSourceTest,DiscoveryPipelineServiceTest`

Run from worktree root; fresh after final edits. It includes the repo-configured Node phase. No project-wide suite.

## Downstream child 07 contract

Preserve PDF body/annotation extraction as bounded source evidence, including the original source fallback and identity conflict semantics. Child07 consumes PDF fallback behavior and may add HTML contact/challenge handling while updating extraction version to 20261001; do not implement HTML in child06.
