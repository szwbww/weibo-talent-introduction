# Child 05 Execution

## Controller baseline
- Product base: `6e2244be0f20d7af0ee710a734f7d252d7160f36` (child 04 terminal code head).
- Required focused command: `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,JatsXmlEmailParserTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest`.
- Result: exit 0, BUILD SUCCESS; backend 327 tests, 0 failures, 0 errors, 0 skipped; configured Node phase passed. Log: `artifact://345`.

## Implementation report

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-source-contact-recall.md`
Plan SHA-256: `9f8256dab926e8b61bd7fda814942f544a92c708b8c029d34d47a22f7b3d1e33`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-source-contact-recall.md@9f8256dab926e8b61bd7fda814942f544a92c708b8c029d34d47a22f7b3d1e33`
Execution epoch: NEW
Approval basis: current child-05 user assignment and approved plan bytes in `b8789cb6062d9110218c08ce8099dba8dddd73e4`
Executor: `PdfContactImplementer`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`
Target branch: `fast/2026-09-26-discovery-repair-00-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master@fast/2026-09-26-discovery-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`
Pre-execution code SHA: `6e2244be0f20d7af0ee710a734f7d252d7160f36`
Pre-execution HEAD: `25b7b7ac47b8cbd2842a9778b502cf901d6f889e` (fast-p evidence preparation)
Post-execution code SHA: `0c1489dd3734965918ef0271754480967391b38f`
Evidence HEAD: `0c1489dd3734965918ef0271754480967391b38f` (no separate evidence commit; this report is outside the product commit)
Implementation boundary: `6e2244be0f20d7af0ee710a734f7d252d7160f36..0c1489dd3734965918ef0271754480967391b38f`, product commit subject `feat(fast-p): implement 05`

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 / I-1–I-4 | IMPLEMENTED | `src/test/resources/discovery/source-contact-recall.zip`, `.md`; `PdfEmailExtractorTest.kt` | Original three PDF and metadata members equal read-only audit bytes; six SHA-256 values match brief. Manifest separates originals and synthetic controls. The real-PDF test uses HTTP download stub → PDFBox and checks page, author/contact text, SOURCE_SHA256 and four relationships/four unknown shared addresses. |
| T-2 / I-1–I-2 | IMPLEMENTED | `PdfAuthorContactLayout.kt`, `PdfEmailExtractor.kt` | TextPosition-based bounded same-document helper reconstructs page/column-local lines, unique author markers and explicit independent contact paragraphs. Original page-one four contacts pass; synthetic shared marker, duplicate name, ambiguous initials, cross-column and references remain unbound. |
| T-3 / I-1–I-3 | IMPLEMENTED | `SourceAuthorEmailResolver.kt`, `SourceAuthorEmailResolverTest.kt` | PDF layout claims join legacy text claims before per-email conflict rejection; same-email competing owners remain identity-empty. One unique marker can bind two explicitly listed addresses. CORE/HTML existing routes unchanged. |
| T-4 / I-3–I-4 | IMPLEMENTED | `DiscoveryIdentity.kt`, `DiscoveryIdentityTest.kt`, `ExpertDiscoveryServiceTest.kt` | EXTRACTION_VERSION=20260929, evidence VERSION=20260925. Isolated original-PDF parser→consumer→ES writer produces RAW=4/CANDIDATE=4 with validation=4, replay creates zero and preserves documents. Synthetic isolated boundary matrix covers unknown/invalid/ineligible/qualified/duplicate/same-name-distinct-email/two-email cases. Cached original-PDF pipeline latest succeeds, 20260928 fails `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED` without re-extraction or write, paused tick has zero writes; checkpoint and terminal observed. |
| T-5 / I-1–I-4 | IMPLEMENTED | `ExpertDiscoveryServiceTest.kt` | Test-generated `target/discovery-plan-acceptance/05.json` contains fixture SHA-256, original page excerpts, parsed evidence, real and synthetic boundary cases, version cases, checkpoint, terminal and ES requests. Direct all-four consumer versus first-PDF cached pipeline explicitly distinguished. |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,JatsXmlEmailParserTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest` | PASS, exit 0; backend 332 tests, 0 failures/errors/skipped; Node 1193 pass, 0 fail/skipped; configured Node syntax checks and BUILD SUCCESS | Fresh final run after last edit: `artifact://400`, 2026-09-27 11:31:56 +08:00. |
| `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test '-Dtest=SourceAuthorEmailResolverTest#one unique PDF contact marker retains two explicit mailboxes'` | Expected RED before implementation: 1 failed assertion, then final command passed this regression | `artifact://398` → `artifact://400`. |
| `git diff --cached --check` | PASS, exit 0 | Staged ten-file product boundary; staged name list checked immediately before commit. |
| Exact-byte fixture comparison against read-only audit originals | PASS | W3014974815, W2999309192, W2907492528 PDFs and corresponding metadata each byte-identical; test ZIP SHA-256 `83c25f11dfc69ba458d91e505f71b4228163707a72f048c92483985c3ccb07a2`. |

### Changed Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` — bounded PDF page/column author-contact evidence.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` — collect layout in existing PDDocument lifecycle.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` — combine layout and old claims under conflict rejection.
- `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` — extraction cache version only.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` — real original-PDF provenance and four-pair assertions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` — conflicts, negative layouts, two explicit mailboxes.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — original-PDF consumer/writer, pipeline and acceptance output.
- `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` — version boundary.
- `src/test/resources/discovery/source-contact-recall.zip` — byte-exact original source fixture with manifest.
- `src/test/resources/discovery/source-contact-recall.md` — authorized fixture provenance description.

### Deviations
- None. No migration, sender configuration/service, schedule, cleanup, tail sampling, push, merge, or unrelated file in product commit. Fast-p report remains outside product commit.

### Freshness
- Plan identity rechecked: YES, same SHA-256.
- Worktree identity rechecked before staging and commit: YES, target branch and worktree Git directory match.
- Product commit is HEAD and reachable from target branch: YES.
- Required focused command run freshly after final edit: YES.
- Historical child-04 baseline used only as baseline: YES.
- Target index/product tree clean after product commit; this execution report is the only subsequent intended worktree change.

### Remaining Blocker
- None.

### Next Action
- Hand off `0c1489dd3734965918ef0271754480967391b38f` and `target/discovery-plan-acceptance/05.json` to the fast-p controller for child 06 and deferred independent review.
