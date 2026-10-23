# Child03 execution log

## Epoch 1 — PLAN_CONFLICT

- Executor: PaperParserImplementer; approval basis: current child03 invocation.
- Plan canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-03-parser.md`.
- Plan SHA-256: `a785748d5726576bc46fab4700914fd1e2af8cf8a74078a47f8e77aafc9c41bd`.
- EXECUTION_ID: canonical path above `@a785748d5726576bc46fab4700914fd1e2af8cf8a74078a47f8e77aafc9c41bd`; epoch NEW.
- Exact seed `c18dbc060dcf4c8440f853ede4d1d3aaf737994f` child bytes hash matches disk.
- Master seed `87c5268976807c19c165fe50c91ba3372ed0075d` SHA-256: `c5033fb7ad4ff8252a58d9c15dc554e109eac3da810c70f6b70eaa89a44cc2c4`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`; branch `fast/discovery-paper-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`.
- WORKTREE_ID: worktree root above `@fast/discovery-paper-master@` Git directory above.
- Approved product base: `c37341aacfc0937e829132ad73d8af7fa24b4b97`.
- Pre-execution HEAD / current HEAD: `b2dc81e4725a1e2127b9465bb9af497e7cf915ec` (earlier child evidence checkpoint).
- Post-execution product SHA: N/A; no product/test commit created. Six-file edits remain uncommitted for authorized continuation. Evidence HEAD: N/A.
- Existing ledger modification and untracked child03 evidence directory were preserved.

### Precise scope conflict C-1

`src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt:126`
asserts `assertEquals(0,first.path("parserVersion").path("pdfLayout").asInt())`.
This test creates a fresh capture at line 118, not a historical persisted first snapshot.
T-2 requires fresh capture metadata `pdfLayout=20261023`; retaining the placeholder 0 would violate the approved production contract.
The test is outside the authorized six-file table but its suite is explicitly mandatory in the brief.
The fresh required run failed exactly here: expected 0, actual 20261023.
Required human authorization: add this seventh file and update the fresh-capture expectation to 20261023, retaining the existing first/latest equality assertion.
No test exclusion, bypass, production special case or unauthorized test edit was made.
Controller explicitly directed preservation of current edits, no complete implementation commit, and PLAN_CONFLICT handoff.

### Task status / preserved authorized files

| Requirement | Status | Evidence |
|---|---|---|
| T-1 rules | PARTIAL, not accepted | PdfAuthorContactLayout adds full-name geometry scan before header fallback; original contact evidence remains byte-identical when rule is null; all claims still go to existing resolver. Fresh regressions expose issues listed below. |
| T-2 version | IMPLEMENTED, contract test blocked by C-1 | Independent RULE_VERSION=20261023, structured capture pdfLayout reads it. EXTRACTION_VERSION/proof VERSION/queue gates untouched. |
| T-2 actual PDF regressions | PARTIAL | PdfEmailExtractorTest adds actual-PDF boundaries; new DiscoveryPaperLayoutRegressionTest synthetic double-column smoke passes; private entry explicitly skipped. |
| T-2 resolver regression | PARTIAL | SourceAuthorEmailResolverTest adds real-layout new-contact versus old text/marker conflict checks; blacklist assertion fails. |
| T-2 comparator | Implemented/exercised on controlled fixtures only | verify_layout_gain.py compares body-bound identities/configs, old losses, additions, conflicts, new mailboxes and labels. |
| I-3 real294 effect verification | INCOMPLETE / SKIPPED | 79 exact originals unavailable; no replacement corpus, replay gain or 79/52 effect claim. |

Authorized product edits are exactly:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt`
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt`
6. `scripts/discovery_paper_archive/verify_layout_gain.py`

### Private originals prerequisite

Used the existing native SSH target found in repository deployment tooling (BatchMode, no credential reads/output).
Source experiment `/root/discovery-layout-gain-czy_fjjk` and original private labels remain reachable.
Read approved `discovery-paper-layout-probe.py` in full before implementation.
Private source manifests describe 294 original samples / 295 retained-body references.
Matched exact source package paths under `/opt/talent/data/discovery-samples/<executionId>/<sampleId>/<bodyFile>` against manifest body SHA-256.

- 216 matching original bodies frozen; 215 complete samples.
- 79 missing original files / 79 incomplete samples; 0 existing-file hash mismatches.
- Freeze location: `/root/discovery-paper-child03-epoch1-frozen`; directories 0700, files 0600. Original experiment JSON/labels and available raw files copied privately; replay-shaped sample packages also prepared there.
- Frozen presence/body-hash manifest SHA-256: `376a9d7273a73a48df431f9d13164523ff6d0b3da75c62b10f3497125af75df9`.
- Original private labels SHA-256: `920b2f3f6b1ffdcd3f8aa7ad7d8ae5f78b603ffa37a27027701461d41239333a`.
- Original summary SHA-256: `cc7cf044ba12f08b15f8f3b2330c59ea66371d01f2b3b0485ba38572f2ea5ff9`.
- Page34 actual-tail confirmation remains incomplete: attempted server PDF-page inspection, but existing Python environment lacks fitz; no dependency installation or widened pages used.
- No raw originals, labels or person-level replay results were placed in Git or fetched locally. No second worktree, substitute corpus, real paper/Apollo request, production SQL/ES write or deployment.
- Real baseline/new replay was not run because exact complete originals are unavailable. Master explicitly permits code completion under this limitation, but NOT a real-effect PASS. Release evidence remains incomplete.

### Fresh commands and outcomes

Identity commands (exit 0): skill `scripts/plan_identity.py <canonical-plan>` and `scripts/worktree_identity.py <canonical-plan> --worktree <target>`, run at preflight and rechecked after edits; `git show <seed>:<plan> | shasum -a 256` confirmed immutable seed hashes.

Required command, after final product edits:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,DiscoverySampleReplayTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperArchiveCaptureTest -DmigrationIt=true
```

Exit 1, BUILD FAILURE, 84 tests / 4 failures / 1 error / 1 skipped. No rerun to confirm known failures.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| PdfEmailExtractorTest | 43 | 2 | 1 | 0 |
| SourceAuthorEmailResolverTest | 26 | 1 | 0 | 0 |
| DiscoverySampleReplayTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 2 | 0 | 0 | 1 |
| DiscoveryPaperArchiveCaptureTest | 9 | 1 | 0 | 0 |

Known failures preserved for continuation, not falsely closed:

- Capture test line126: C-1 expectation 0 versus 20261023.
- New PDF boundary test line79: damaged `Alice ? Smith` anchor produced 1 contact, expected 0 (approved normalization versus damaged-signature expectation needs reconciliation).
- Existing 62-page fixture line149: test assumes single contact per mailbox; new and old matching contacts coexist, so singleOrNull fails. Old evidence itself remains present; update must preserve both claims.
- Existing contact-section boundary test line354: later reference-author email resolves to John, expected unresolved. New independent scan requires review against reference-section invariant.
- New resolver test line273: blacklist fixture expected no address but still returned one; existing blacklist contract must be used, not weakened.

Separate actual-PDF parser smoke:

```sh
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp "target/test-classes:target/classes:<contents of target/archive-smoke-classpath.txt>" com.weibo.talentintroduction.discovery.service.LayoutPdfFixtures
```

First attempt before Maven compilation completed: exit 1, ClassNotFoundException (not parser execution).
After completed compilation, same command: exit 0, `PURE_PARSER_SMOKE resolved=2 crossColumnRejected=1 conflictingOwnerRejected=1 network=0 businessWrites=0`.
Uses generated actual double-column PDF bytes through layout parser and resolver, not source assertions.
Executed class hashes: PdfAuthorContactLayout.class `59d9bee87cf73478dff5fa58efaa1d71ebd8b76943dd35eed9935179877351c8`; LayoutPdfFixtures.class `11d1af0675a0e2ec1929221db3a5f2195a508e7d81e3fbd4cef7f9312f82c4d7`.

Comparator fixture directory `F=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/child03-comparator-o1c_gkyl` contains synthetic data only. Executed supported CLI:

```sh
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/after.json" --labels "$F/labels.json" --output "$F/after-result.json"
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/lost.json" --labels "$F/labels.json" --output "$F/lost-result.json"
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/new.json" --labels "$F/labels.json" --output "$F/new-result.json"
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/conflict.json" --labels "$F/labels.json" --output "$F/conflict-result.json"
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/hash.json" --labels "$F/labels.json" --output "$F/hash-result.json"
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/gainBefore.json" --after "$F/after.json" --labels "$F/labels.json" --output "$F/gain-result.json"
```

Outcomes: unchanged exit0; lost exit2/lostAssociations1; new exit2/newEmails1/incorrectAssociations1; conflicting exit2/conflicts1; hash mismatch exit2/bodyHashMismatches2 (removed+added body identity); labelled existing-mailbox gain exit0/addedAssociations1/newEmails0.
These controlled checks do not establish real294 accuracy.

### Handoff

Plan identity and target worktree identity rechecked unchanged. No product commit/reachability claim; no staging of evidence or unrelated files. No later child inspection or implementation.
Required suites/comparator/smoke were exercised freshly; required suite is not passing.
Next action: human amendment for C-1 seventh-file authorization, then resume exact approved identity/scope and resolve preserved authorized failures before a fresh full required check and implementation commit. Real-effect review independently remains incomplete until missing originals are recovered.

