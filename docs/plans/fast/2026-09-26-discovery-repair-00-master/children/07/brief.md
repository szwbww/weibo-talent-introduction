# Child 07 Brief — structured HTML contact and known challenge fallback

Approved child plan: `docs/plans/2026-09-26/discovery-repair-07-html-contact.md` (approved bytes commit `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read it completely; it is authoritative for acceptance.

## Boundary

- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`.
- Product base child06 terminal code head `d5a98877b18e321c61b2dd3295521d049af360f2`; its separate verification evidence commit `69d32cf...` precedes this implementation. Master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Preserve earlier source/PDF fixes, selected page and mailto bounds from child06, `tailPages` 0/1, same download and deadline budget, author identity uniqueness/conflict, M1–M3 qualification/create/non-overwrite, pause/send configuration. Child07 only adds HTML author contact and challenge fallback; no crawler, CAPTCHA circumvention, additional URLs, migration, data cleanup, actual method-used statistics, or 09 subject scope.

## Authorized Files — exhaustive 10

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
9. `src/test/resources/discovery/html-contact-recall.zip`
10. `src/test/resources/discovery/html-contact-recall.md`

## Original source evidence

Read-only `/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/original-sources.zip` (SHA-256 `0607b402b9ffdeadcc8726710d3031277fbc7ee05f1989caf2c7da25bc0f4205`) contains complete original HTML; matching original metadata resides under audit `evidence/sources/<work>/metadata.json`. File SHA-256 from original-source manifest:
- W3194730353 source.html `5e60e14e3312cc37a48ef33c45b8caf4f0b1b70660c8bc18d14760d7b667d23d`
- W3094704314 source.html `f05891933c9a47bdc505bf8b12452715fbf5c18bdf297a57a13c1b44930f4f40`
- W3135028703 source.html `0a99dd6054d3537ef453d591673fd2f31ff1d768dc21545cc48307d606d9163a`
- W4381304672 source.html `3c5b6e6d6762458bc348a82eb660a72d722a82bcfae37e4074706836027a811c`

Include exact-byte originals and metadata in the authorized test ZIP with provenance manifest; preserve Edward/Hang prior fixtures.

## Plan invariants and acceptance

- I-1: Only independent named `a[href=mailto:]` under actual `corresponding-author-list` with same region corresponding-author heading; visible anchor full name must uniquely match metadata. Do not infer name from aria-label/order, link position or shared unnamed list. Parse exactly one URI mailbox, strip query, retain `+` local-part during percent decoding, reject multiple recipients/CRLF, blacklist/dedup. Add href mailbox to final found set, not just claim. Contradictions continue unified conflict resolution. Original full HTML: W3094704314 Vijay Kumar/vijaykumarchahar@gmail.com; W3135028703 and W3194730353 Iqbal H. Sarker/msarker@swin.edu.au. Three paper relations, two distinct experts; duplicate third RAW=2/CANDIDATE=2 total. Edward two explicit mailboxes, Hang shared remains unbound.
- I-2: Known W4381304672 Anubis challenge requires combined script marker and challenge title structure; strip script/style and reject truly empty HTML. Return `failureReason=PDF_DOWNLOAD_FAILED`, `downloadFailureCategory=INVALID_CONTENT`, `fulltextObtained=false`, empty emails so existing OA fallback requests next unique public URL. Academic text merely saying bot/challenge does not trigger, nor does ordinary nonempty email-less HTML (`NO_EMAIL_IN_HTML`, obtained=true). No CAPTCHA bypass/paywall treatment.
- I-3: Existing max 3 deduplicated public fulltext URLs, same per-paper deadline, no new hosts. `EXTRACTION_VERSION=20261001`, proof VERSION=20260925 unchanged; old nonempty cached version fails `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`, no re-extraction/new writer, pipeline FAILED. New version consumes normally. No DB/schema/payload change.
- I-4: Unknown identity no validation/RAW; validation rejects no RAW; qualification rejects RAW=1/CANDIDATE=0; duplicate no overwrite; same-name different email and one person two explicit emails remain separate; pause blocks new writes. No sending changes.
- Generate actual isolated `target/discovery-plan-acceptance/07.json` with fixture hashes, actual parser/request/consumer outcomes, checkpoint/terminal, boundaryCases, versionCases, ES request counts; no fake success flags or human `-acceptance.md`. Source bytes genuine, external HTTP/validation/qualification/ES may be isolated substitutes; identity parser not mocked.

## Required command

`JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=SourceAuthorEmailResolverTest,PdfEmailExtractorTest,OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,DiscoveryPipelineServiceTest`

Fresh after final edits, includes configured Node phase. No project-wide suite.

## Downstream child 08

Keep identity and source behavior stable; 08 modifies only two discovery funnel log lines, task by-source renderer and static asset cache keys. 09 amendments are applied only after 08 verified; 07 must not implement 09.
