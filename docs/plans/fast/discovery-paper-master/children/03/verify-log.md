# Child03 light verification log


## Epoch 1 — Verification not dispatched
- Implementer returned PLAN_CONFLICT; required command failed, implementation is not complete or committed.
- No independent verdict exists. CaptureTest fresh-version expectation needs a seventh-file authorization; authorized regression failures remain in execution.md.
- Available historical originals were privately frozen, but79 body files are missing. No real294 replay/effect PASS is claimed.


## Epoch 2 — Attempt 1
Timestamp: 2026-10-23T16:58:13.331914+00:00
Execution epoch: 2
Verification attempt: 1
Fix round: 0

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 03 — docs/plans/2026-10-23/discovery-paper-03-parser.md
Boundary: c37341aacfc0937e829132ad73d8af7fa24b4b97..5b7bc8919202876e7d1b6884cd9c9cd50d1f23af
Verifier: PaperParserVerifierE2

Authority: child/master amended at 8d2313da868251b68dcaee02b744dfa777b41674; ledger A3/A4; brief Epoch2; master I-1–I-4 and child I-1–I-4. This is an independent fast-p four-gate check, not verify-p/review-p/fix-v or whole-system acceptance. No later-child implementation/plan was inspected. Epoch1 remains unchanged.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary `git diff --name-only c37341aacfc0937e829132ad73d8af7fa24b4b97..5b7bc8919202876e7d1b6884cd9c9cd50d1f23af` contains the seven authorized product/test/script files listed below; remaining paths are docs/plans governance interleavings, excluded from product scope. Product diff: 322 insertions/8 deletions. At verification start `git diff 5b7bc8919202876e7d1b6884cd9c9cd50d1f23af -- src scripts` and `git diff --cached --name-only` returned empty; `git log -1 --format=%H` identified the exact code head on fast/discovery-paper-master. |
| Plan and invariants | PASS | PdfAuthorContactLayout.kt:69,114–169 implements the independent scan before header fallback, exact unique complete-name normalization/edge-marker removal, ordered geometry limits, next-author/section/first-@ stops, five intervening lines, whole mailbox, existing PlainTextEmailExtractor filtering, institution/repeated-distinct-owner block support. Reference-heading boundary at :129–137 excludes later reference anchors without changing the old roster-bound end-contact path. PdfEmailExtractorTest.kt:46–104 covers actual PDF bytes and the approved boundary matrix; :160–171 preserves original null-rule contacts rather than asserting incidental multiplicity. Resolver integration at SourceAuthorEmailResolverTest.kt:260–273 preserves conflict downgrade, blacklist and found-email-only behavior. Real-effect acceptance remains expressly incomplete under O-1, not a synthetic-effect PASS. |
| Required commands | PASS | Fresh required JDK11 Maven command below: exit0/BUILD SUCCESS, 84 JVM tests, 0 failures, 0 errors, 1 true missing-original skip; 1807 Node tests passed, 0 failures/skips. Matches writer Epoch2 baseline exactly. Fresh comparator CLI controls exit0/2 as expected; separate actual-PDF LayoutPdfFixtures JVM exits0 with two resolutions and cross-column/conflict rejection. |
| Downstream interfaces | PASS | Contact.rule defaults null and preserves old evidence bytes; new rule values are included in evidenceText (PdfAuthorContactLayout.kt:14–16). Producer contacts flow through PdfEmailExtractor.kt:327–346 to SourceAuthorEmailResolver.kt:35–45; results :165–180 combines text/old/new claims, resolves only a unique owner and never manufactures an extracted mailbox. No rule-code dispatch silently drops new contacts. Capture :104–110 writes structured identityExtraction/pdfLayout version metadata; repository :66–79 atomically preserves first and updates latest, service :292–294 still reads frozen first. CaptureTest :114–157 verifies version and first/latest/legacy preservation. DiscoveryIdentity.kt:24–26 remains proof20260925/cache20261004; ExpertDiscoveryService.kt:2161–2190 retains exact cache-version admission and the original consumption path. Replay BodyResult :41–45 and config/replay :310–368 supply body-bound identities, bindings, parserConfig and purity/status fields consumed by verify_layout_gain.py:24–73; private entry calls existing offline replay, no production Spring context or business writers. |

Authorized product paths (boundary receipt from `git diff --name-only ... -- scripts src`):
- scripts/discovery_paper_archive/verify_layout_gain.py
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt

Plan identity evidence: immutable `git show 8d2313da868251b68dcaee02b744dfa777b41674:<path> | shasum -a 256` and current file hashes match: child `c46019e05d5315854277057d2550541093b5f4649a7961c2f4a30f9ccf999abe`; master `21480ccb1ed9844e454cfe271194bfd65700aeacf56ae862e9424b818358453d`. No plan amendment or product edit was made by this verifier.

### Fresh required command evidence
Executed in the retained worktree:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PdfEmailExtractorTest,SourceAuthorEmailResolverTest,DiscoverySampleReplayTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperArchiveCaptureTest -DmigrationIt=true`

Exit0; BUILD SUCCESS; total04:15; finished2026-10-24T00:56:41+08:00; output artifact://198.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| PdfEmailExtractorTest | 43 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 26 | 0 | 0 | 0 |
| DiscoverySampleReplayTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 2 | 0 | 0 | 1 |
| DiscoveryPaperArchiveCaptureTest | 9 | 0 | 0 | 0 |

Maven-bound Node:1807 tests/334 suites/1807 passed/0 failed/0 skipped; node-check-app and node-check-task-modal-runtime completed. Fresh surefire XML `TEST-com.weibo.talentintroduction.discovery.service.DiscoveryPaperLayoutRegressionTest.xml:69–74` identifies the aborted private test with `SKIPPED: exact original294 frozen raw corpus unavailable; no real-effect claim`; synthetic actual double-column test executed and passed. These are the scoped required suites, not a full backend suite or WAR build.

Separate actual-PDF parser smoke, run after Maven completed (Python is only the launcher that expands the existing classpath file; the executed program is a separate JDK11 JVM):

`python3 -c 'import pathlib, subprocess, sys; sys.exit(subprocess.call(["/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java", "-cp", "target/test-classes:target/classes:" + pathlib.Path("target/archive-smoke-classpath.txt").read_text().strip(), "com.weibo.talentintroduction.discovery.service.LayoutPdfFixtures"]))'`

Exit0; `PURE_PARSER_SMOKE resolved=2 crossColumnRejected=1 conflictingOwnerRejected=1 network=0 businessWrites=0`. Actual double-column and shared-mailbox conflicting-owner PDF bytes pass through PDFBox/layout/resolver; not source-text assertions or injected contact claims. Executed PdfAuthorContactLayout.class SHA256 `159b1bc2a19f50ab9b40a89cd0d501bd0a1b64bfef14899d6a711e26857eb002`; LayoutPdfFixtures.class `0073efbddd682a99ff75ccee76892e817294bb54af3c4c049cfb83833aed2f4e`; classpath file `72dffb7c0bba9b5a52c82b63b4df201d2baa84c4abc910805027b3f2d0a30d84`.

Fresh comparator commands, using only the execution.md controlled synthetic fixtures. In the exact commands below F denotes `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/child03-epoch2-comparator-sl95ynov` (tool invocations expanded that absolute prefix):

- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/after.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-unchanged.json` → exit0/all difference counters0.
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/lost.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-lost.json` → exit2/lostAssociations1.
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/new.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-new.json` → exit2/newEmails1/addedAssociations1/incorrectAssociations1.
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/conflict.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-conflict.json` → exit2/conflicts1/addedAssociations1/incorrectAssociations1.
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/hash.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-hash.json` → exit2/bodyHashMismatches2 (old/new identity).
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/before.json" --after "$F/config.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-config.json` → exit2/configMismatches1.
- `python3 scripts/discovery_paper_archive/verify_layout_gain.py --before "$F/gainBefore.json" --after "$F/after.json" --labels "$F/labels.json" --output /tmp/PaperParserVerifierE2-gain.json` → exit0/addedAssociations1/newEmails0.

### AUTO_FIX
- N/A — no proven four-gate violation found; no product repair authorized or attempted.

### RECORD_ONLY
- O-1: Original294 real-effect acceptance remains INCOMPLETE/SKIPPED. Historical private prerequisite evidence in execution.md records216 matching bodies/215 complete samples/79 missing files/0 existing-file hash mismatches, manifest SHA256 `376a9d7273a73a48df431f9d13164523ff6d0b3da75c62b10f3497125af75df9`. Page34 actual-tail confirmation and same-complete-corpus baseline/new replay remain unverified. No downloads, replacement corpus, private raw/label fetch, or real replay was performed by this verifier. Neither79 preserved nor52 gains (34+18), nor real-corpus0 new emails is a verified implementation outcome. The master explicitly permits completed code under this evidence limitation; retain it for human release/effect review rather than treating the true skip as PASS or an automatic repair.

### Required Action
- COMPLETE_CHILD

Report: docs/plans/fast/discovery-paper-master/children/03/verify-log.md. Only this append is a tracked verifier write; product/tests/plans/index/branch/commits remain untouched. Required-command outputs are ignored build artifacts or synthetic comparator results outside Git. No full-system review, production writes, deployment or later-child inspection was performed.
