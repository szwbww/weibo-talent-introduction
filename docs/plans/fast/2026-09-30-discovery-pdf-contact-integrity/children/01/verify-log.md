## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 — docs/plans/2026-09-30/discovery-pdf-contact-integrity.md
Boundary: 8aa82c87848273313bd9239249eb77cff6c05c14..827b8b0f7df5c51e06db6d06be528d06e6ee2620
Verifier: VerifyPdfContact01

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --name-only 827b8b0f` = exactly the 10 authorized files (3 production, 6 test, `src/test/resources/discovery/ownership-20260930.zip`); `git diff --name-only fb8e1ed5..827b8b0f` same 10; `fb8e1ed` docs-only (`children/01/baseline.md`); no `docs/plans/**` in the implementation commit; worktree carries only the implementer's `children/01/execution.md` modification. |
| Plan and invariants | PASS | I-1: `PdfAuthorContactLayout.kt:170-195` consecutive-group parse (gap only digits/commas/space), `:198-205` unmatched signature registered `null`, `:208-209` header predicate kept equivalent to pre-fix `markerOwners(text).isNotEmpty()` (compared against `8aa82c87`); I-2: `:171-172`,`:192` `[?\uFFFD]` checked on `authorArea` only, no `?→*`; I-3: `PlainTextEmailExtractor.kt:27,45-46` + consumer `PdfAuthorContactLayout.kt:82` passes `"$marker$segment"`; I-4: report shows chee/lyderic/lun contacts empty, retained emails all identity fields null (isCorresponding=false); I-5: `DiscoveryIdentity.kt:22,24` (VERSION 20260925, EXTRACTION_VERSION 20261004); I-6: real PDFBox replay from archived bytes with SHA assertions. Tests: `SourceAuthorEmailResolverTest.kt:697-745`, `PlainTextEmailExtractorTest.kt:95-127` (11-case table), `PdfEmailExtractorTest.kt:149-259`, `ExpertDiscoveryServiceTest.kt:1619-1756`, `DiscoveryPipelineServiceTest.kt:1850-1938`. |
| Required commands | PASS | `mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test` → exit 0, `Tests run: 362, Failures: 0, Errors: 0, Skipped: 0` (baseline 358/0/0/0; per-class 25, 7, 40, 7, 177, 47, 19, 40). Reports inspected: `target/discovery-plan-acceptance/pdf-contact-integrity.json` (archiveSha256 `9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`, lun `yue@msn.com` absent from contacts+resolved, `lun_yue@msn.com` kept unowned), `pdf-contact-consumer.json` (producer stamped 20261004; 20261003/20261002/20260928/null → `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`, 0 writes), `pdf-contact-cache.json` (20261003/20261002/null FAILED, 0 downloads/0 writes; current version SUCCEEDED/indexedExperts=1). Fixture: `git show 827b8b0f:src/test/resources/discovery/ownership-20260930.zip \| shasum -a 256` = the same `9f5802…` hash and `cmp` against the archived `original-inputs.zip` reported identical. Optional pre-fix replay executed and reverted: with the 3 files restored from `8aa82c87`, exactly the new/changed negative tests failed (6 failures: Chee contacts present, `yue@msn.com` returned, version 20261003 accepted by pipeline/identity, marker group produced 1 contact); restored from `827b8b0f` and `git diff HEAD --stat` for those files is empty with file hashes equal to the pre-replay backup. |
| Downstream interfaces | PASS | N/A — single-child run (`children/` contains only `01`, no later child/downstream consumer contract). The one cross-module seam touched, `discovery_paper_job.extraction_json` producer→consumer, is served by unchanged production code (`ExpertDiscoveryService.kt:2046-2047`, `DiscoveryPipelineService.kt:1022`) and was exercised end-to-end by the consumer/pipeline tests and reports above. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: The U+FFFD half of invariant I-2 has no independent fixture evidence (`PdfAuthorContactLayout.kt:192` handles it in the same character class/branch as the verified `?`). The synthetic harness renders with `PDType1Font` standard fonts (cannot encode U+FFFD) and the archived originals contain no U+FFFD; already documented in `children/01/execution.md` §8.1. Evidence-surface limitation only, no observed behavior defect; outside the four light gates.

### Required Action
- COMPLETE_CHILD
