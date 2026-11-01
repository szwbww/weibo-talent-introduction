# Child 00 Verification Log

## Epoch 1 / Attempt 1 / 2026-11-01T13:44:35.951111+00:00
Reviewed range: 3a425461dbe6088e4d4dda1a53141416a392670c..1f73037375bc53082ab07f0603debad684f5fc3e

## Light Verification: LIGHT_PASS
Child: 00 — docs/plans/2026-11-01/paper-reprocess-00-replay.md
Boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..1f73037375bc53082ab07f0603debad684f5fc3e
Verifier: PaperReplayVerifier00

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff --name-status` identified the six authorized implementation/audit files listed below; the other additions are fast-p control-plane artifacts. No parser, business writer, configuration, migration, or unrelated test changes are in this child boundary. |
| Plan and invariants | PASS | `DiscoveryPaperIdentityReplayTool.kt:93-158,179-213` validates and retains immutable source/metadata/outcome/body identities and emits combination-level statuses; `:38-49,218-233` constructs only offline calculation dependencies with denied network clients. Focused tests at `DiscoveryPaperIdentityReplayToolTest.kt:48-101` passed. Fresh original-byte replay and post-run SHA checks below confirm all frozen sources and reports remain unchanged. |
| Required commands | PASS | Fresh recount exit 0; explicit JDK11 focused Maven test exit 0 (6 tests, 0 failures/errors/skips); two fresh real offline PDFBox runs exit 0 (337 rows each, networkCalls=0); timed reconciliation exit 0 (337 rows, 8 reproduced pattern/condition pairs). Exact commands and measurements follow. No prior product-test failure was asserted or used to excuse a result. |
| Downstream interfaces | PASS | Frozen manifest SHA `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`; 244 records and 337 unique combination rows. `reconcile_paper_identity_replay.py:24-36,51-88` validates complete receipt keys/statuses and forwards every row, while restricting repair categories to REPRODUCED. Fresh CSV equals the committed CSV except elapsedFileMs. Child 01 must reuse this manifest and the approved evidence restrictions, including the excluded negative set. |

#### Authority and scope evidence

- Mode: AUDIT_ONLY, constrained exclusively to the selected fast-p four gates; only this verification log is appended in the tracked tree. No product/test/plan/index/branch/commit edits, fixes, fix plans, knowledge writes, release actions, production calls, or whole-system verdict.
- Approved child plan SHA-256 independently matched `f9cb78a3c967c67fbbeab0c272a0894cb4928cd6a650e05ae419853dee6a2132`. Amendments: N/A.
- Native read-only Git identity commands resolved `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`, branch `fast/paper-reprocess-master`, HEAD `1f73037375bc53082ab07f0603debad684f5fc3e`. The initial index diff was empty; the initial working-tree diff contained only controller-owned brief/execution/ledger artifacts. The stale unrelated-worktree identity helper was not invoked or repaired.
- `git diff --name-status 3a425461dbe6088e4d4dda1a53141416a392670c..1f73037375bc53082ab07f0603debad684f5fc3e` returned authorized implementation additions:
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayTool.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt`
  - `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py`
  - `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`
  - `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-results.csv`
  - `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-report.md`
- Remaining boundary additions are under `docs/plans/fast/paper-reprocess-master/` from initialization commit `02bdd4be0eadfb02150df713359a61f030265eda`; implementation commit is `1f73037375bc53082ab07f0603debad684f5fc3e`. Full scope-command receipt: `artifact://48`.

#### Explicit requirement and consumer evidence

- I-1: the frozen key is recordId/bodySha256/emailHash/nameHash/page (`DiscoveryPaperIdentityReplayTool.kt:95-100`; reconciler `:19-36,55-65`); actual CSV has 337 rows and 337 unique keys. Recount returns `PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1`. Independently grouping CSV personGroup/sameBatchStatus yields 325 no-complete-binding groups, 7 recovered groups, and 1 same-name-unconfirmed group. These are historical audit counts, never admissions or promised new experts.
- I-2: original-byte directory `/Users/lukai/.local/share/paper-reprocess-30711` is available outside Git. All 244 body files (657,180,346 bytes), 244 source snapshots, body lengths, manifest-bound source SHA values and seven original audit hashes matched before and after execution. Metadata/first.outcome hashes and attempt anchors are checked by the CLI at `:121-138`; fresh receipts contain no SOURCE_MISSING or AMBIGUOUS rows.
- The real consumption path is `DiscoveryPaperIdentityReplayTool.replay -> PaperBodyAnalyzer.parse` (`:138`), PDF dispatch in `PaperBodyAnalyzer.kt:28-46`, then `PdfEmailExtractor.kt:322-347 -> PdfAuthorContactLayout.collect -> SourceAuthorEmailResolver.resolvePdf`. No synthetic text substitute was used. Both independent invocations use the child-00 current parser, because this child changes no parsing rules; oldNames remains the immutable historical first.outcome, not a claim to have executed a historical deployment binary.
- Statuses from both fresh receipts and the reconciler: 330 REPRODUCED, 1 ALREADY_RECOVERED, 6 NOT_REPRODUCED. Of reproduced combinations, 329 have NO_PRODUCTION_LAYOUT_CONTACT and 1 has AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION; the six non-reproduced cases remain excluded. These are observed coverage/contact conditions, not an asserted repair of all 325 opportunities. The separate 47 unconfirmed column candidates remain in manifest.negativeCandidates and are not repair truth or newly replayed original-byte negatives.
- I-3: network traps are installed on both HTTP entry mechanisms (`DiscoveryPaperIdentityReplayTool.kt:38-49`); receipts report zero network calls, and inspected construction has no Spring application context or business writer graph. Tests exercise the real PDF path, corruption, absent source/configuration evidence, duplicate/negative cases and receipt overwrite refusal. No verifier production ES/DB queries were made; unchanged remote ES/admission/contact version digests are retained export evidence in `/tmp/paper-reprocess-30711-runtime.json`, not misrepresented as fresh remote observations. Independent fresh evidence is the offline path, zero network calls and unchanged local input hashes.
- Bounded inventory classification consumes the frozen matching-email snapshots (`DiscoveryPaperIdentityReplayTool.kt:51-83`), preserving docId/index/version/seqNo/primaryTerm/admission and protections. Fresh CSV matches all committed classifications: NOT_CREATED 241, EMPTY_NAME 6, COMPLETE_NAME_MISSING_PROOF 41, COMPLETE 11, MANUAL_CONTACT_OR_APPLICATION_PROTECTED 37, MULTIPLE_DOCUMENT_CONFLICT 1 (combination counts, not people).
- X-0/X-1: all five replay statuses are explicitly accepted by the reconciler, every expected key is required exactly once, all rows are serialized, and only REPRODUCED contributes to categories. Downstream child 01 still has its own explicit ownership/negative/page-coverage gates; this verification grants no permission for unproved heuristics or wider PDF scanning.

#### Fresh required commands and results

All commands ran from `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master` after inspection, once each. Baseline availability evidence was runtime/validator availability only; no known product-test baseline failure.

1. `python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py`
   - Exit 0; `PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1`.
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest`
   - Exit 0; BUILD SUCCESS; 6 JVM tests, 0 failures, 0 errors, 0 skipped; Maven elapsed 03:03 (tool wall 184.90 s). The existing Maven-bound JS phase also executed: 2,018 passed, 0 failed/skipped/cancelled; existing syntax-check phases passed. No additional whole-system suite was requested or separately run. Full receipt: `artifact://54`.
3. `/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-baseline.json`
   - Exit 0; replayed=337, networkCalls=0; 65.05 s real, 102.95 s user, 3.18 s system; maximum RSS 1,106,935,808 bytes. Per-file elapsedFileMs: 244 files, min 49, max 2556, sum 63215. Log/time receipt: `artifact://55` (tool retained head/tail; some verbose middle output was not saved).
4. `/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-candidate.json`
   - Exit 0; replayed=337, networkCalls=0; 65.14 s real, 101.65 s user, 2.68 s system; maximum RSS 1,065,058,304 bytes. Per-file elapsedFileMs: 244 files, min 53, max 2654, sum 63572. Log/time receipt: `artifact://57` (tool retained head/tail; some verbose middle output was not saved).
5. `/usr/bin/time -l python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-baseline.json --new-replay /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-candidate.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-results.csv --report /tmp/PaperReplayVerifier00-e1-a1-20261101T133655-report.md --runtime-evidence /tmp/paper-reprocess-30711-runtime.json`
   - Exit 0; rows=337; statuses ALREADY_RECOVERED=1, NOT_REPRODUCED=6, REPRODUCED=330; reproducedCategories=8; 0.08 s real, 0.04 s user, 0.01 s system; maximum RSS 26,132,480 bytes.
   - The runtime-evidence input deliberately remains the retained writer/export evidence. Its embedded earlier command results are not these independent measurements; the fresh verifier measurements are recorded immediately above. No original receipt, runtime evidence, manifest or committed report was overwritten.

Independent Python comparison/hash checks exited 0: fresh baseline matches retained baseline excluding timing, fresh baseline and candidate agree excluding timing, fresh CSV matches the committed CSV excluding timing, changedAuditFiles=[], sourceIntegrityFailures=[]. Final comparison receipt: `artifact://61`.

| Artifact | SHA-256 |
|---|---|
| Frozen manifest, unchanged | de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c |
| Committed results CSV, unchanged | 7dbcc5e91bd0d77ef6527cdec98303ce48a273755b836629f7655f88c3b85e46 |
| Committed report, unchanged | fd018b41a4559c44e3a804597f4f895135d6e9b0a60f3b347b7b8809a83b3bff |
| Fresh baseline JSON | 322088146f2fae18762b0b91c3ce8d3d5ddd390142734ee26ef4e0677c9464da |
| Fresh candidate JSON | 9077019b74be68e803106a8399c228574a879dfddfb1e43ebc10aea8f82e8dd6 |
| Fresh results CSV | e5c3d7d65d7acb5ff3dff52cd251337c3cb8ea07e0f6a6d201d010c43d8c4528 |
| Fresh report | b793861c90142bf3b2b9610073723ff06e6ba1599ccdfc3843c69bbba784c832 |
| Retained baseline JSON, unchanged | e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c |
| Retained candidate JSON, unchanged | 4f4f30bb5f5c32eeb042db366341fefbaf2a61082a8f92fb8faebf44d4fa5e38 |

PDFBox/font mapping warnings were visible and not suppressed; neither replay nor this light result claims complete extraction of every visible glyph. Concurrency stayed 1. Manual acceptance A-1/A-2 remains PENDING and is not replaced by these machine checks. Global workflow/UI/admission invariants unrelated to this offline child were not broadly re-audited.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

This is only the child-00 four-gate light result, not final whole-system or human acceptance. The controller owns artifact checkpointing and any advance to child 01.
