# Child 01 Execution Log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-01-parser.md`

Plan SHA-256: `508fd62d84283bd223aca0970c9e265944762f6c48f36ffaf127ba2b88260608`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-01-parser.md@508fd62d84283bd223aca0970c9e265944762f6c48f36ffaf127ba2b88260608`

Execution epoch: NEW

Approval basis: approved `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` invocation dated 2026-11-01, exact child 01 brief, and explicit delegated instruction to implement and commit child 01.

Executor: `PaperParserImplementer01`, executing `execute-p`; not the independent verifier.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`

Target branch: `fast/paper-reprocess-master`

Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master@fast/paper-reprocess-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`

Pre-execution code SHA / child_base_sha: `1f73037375bc53082ab07f0603debad684f5fc3e`

Pre-execution HEAD including completed child 00 evidence/checkpoint: `7f8b0f2d6204ba8ecac79bc533ddc2f7989bd159`

Post-execution code SHA: `41c24f879591062b0efbe39eb9df4ef3d802754d`

Evidence HEAD at implementer handoff: `41c24f879591062b0efbe39eb9df4ef3d802754d`; no separate evidence commit was made by this executor.

Implementation boundary: `7f8b0f2d6204ba8ecac79bc533ddc2f7989bd159..41c24f879591062b0efbe39eb9df4ef3d802754d`, exact subject `feat(fast-p): implement 01`, seven authorized files, 395 insertions / 41 deletions. The wider child product boundary starts at `1f73037375bc53082ab07f0603debad684f5fc3e`.

### Task Status

| Requirement | Status | Files | Execution evidence |
|---|---|---|---|
| Plan step 1 / I-1 explicit contact grammar | IMPLEMENTED | SourceAuthorEmailResolver.kt, SourceAuthorEmailResolverTest.kt | Named contact plus institution/Email continuation; trailing full-name or unique initials labels, including undotted initials; closed full-name mailbox wrappers; Unicode hyphen/whitespace comparison normalization without rewriting original evidence. Duplicate initials, shared lists, contradictory owner claims and metadata-only authors remain unknown. |
| Plan step 2 / I-1/I-2 physical layout | IMPLEMENTED | PdfAuthorContactLayout.kt, both service tests | Named-contact parsing uses existing physical lines and bounded same-column blocks; existing x/y thresholds are not relaxed. Detached smaller superscript marker rows are joined only to their physically overlapping author baseline. Unknown signatures and shared markers still defeat uniqueness. PDF page-flattened text supplies mailbox clues, not cross-column ownership. |
| Plan step 3 / I-2 page coverage | IMPLEMENTED | PdfEmailExtractor.kt, PaperBodyAnalyzer.kt, both layout/archive tests | Existing head/tail selection retained; selected page text preserves order and form-feed page boundaries. Explicit selected-tail contact fixture 31479 recovers without reading new pages. Analyzer reports PAGE_NOT_SCANNED; 34257 page 15 remains outside selection. |
| Plan step 4 / I-3 versions and facts | IMPLEMENTED | PdfAuthorContactLayout.kt, PaperBodyAnalyzer.kt, DiscoveryPaperAnalysisServiceTest.kt | Shared parser version `20261101`; identity `20260925` and extraction `20261004` unchanged. Cache invalidation includes the independent layout/text parser version. Qualification computation remains independent and historical. Original first.outcome and source bytes are read-only. |
| Plan step 5 / X-1/X-2 first extraction, analysis, replay | IMPLEMENTED | Three authorized tests | Original-byte fixtures validate manifest/source/body/metadata/first hashes, compare first extraction with analyzer bindings and qualification facts, check validEvidence, and exercise actual PAPER/RECORD consumer with the existing extraction version. Full 244-record/337-combination replay and separate real CLI consumer smoke completed. |
| Global I-1/I-7 count discipline | IMPLEMENTED | External delta/runtime/replay reports, this execution log | Frozen baseline classifications retained; 49 reproduced gains, 281 unresolved reproduced combinations, one excluded incidental gain, no audited losses or conflicts. No binding count presented as admission or person count. |
| Global I-2/I-3/I-4/I-6/I-8 downstream boundaries | IMPLEMENTED / unchanged | Authorized parser seams only | No production clients invoked, business writes, direct promotion, qualification-policy change, Apollo change, source overwrite, original deletion, later-child implementation or frontend changes. |
| Commit and handoff rules | IMPLEMENTED | Authorized implementation commit; this uncommitted execution log | Exact seven-file commit; fast-p logs, existing child 00 evidence, source inputs and external reports excluded. Independent verification remains pending. |

### Original-byte evidence and rules

Frozen manifest: `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`, SHA-256 `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`. Source directory: `/Users/lukai/.local/share/paper-reprocess-30711`. Retained old replay: `/tmp/paper-reprocess-30711-baseline.json`; it was neither replaced nor reinterpreted as immutable first.outcome.

Original fixtures used in the executable regressions include:

| Record / page | Observed original evidence | Bounded rule |
|---|---|---|
| 31372 / 1 | Mailbox followed by `(Emilio Martínez-Pañeda)`; metadata uses a Unicode hyphen variant | Normalize comparison punctuation, preserve original sentence |
| 31375 / 1 | Separate explicit mailbox labels `(MIC)`, `(MS)`, `(ATW)` | Match unique initials with or without dots, never mailbox-prefix inference |
| 31398 / 1 | `Tri Huynh <...>, Maryam Khademi` followed by her closed mailbox wrapper on the next aligned line | Complete named mailbox wrapper in the same physical contact block |
| 31494 / 1 | `* Corresponding  author: Nitin  Liladhar Rane, University ... E-mail: ...` | Whitespace-tolerant explicit label, bounded affiliation continuation |
| 31992 / 1 | Named Yanhui correspondence plus department/university/Email continuation | Existing same-column distance bounds; no nearest-author search |
| 32137 / 1 | Antoine author signature and unique `1,*` marker rendered on an overlapping smaller superscript row | Physical marker-to-author reattachment, preserving complete-signature uniqueness |
| 32689 / 1 | Dabo mailbox with `(I.D.)` on the next aligned contact line | Explicit initials continuation; no author-sequence guess |
| 31479 / selected page 13 | Kumar Vijay Mishra named parenthesized mailbox biography | Already-selected tail contact, no page-selection expansion |

Synthetic negative cases cover competing names, shared mailbox lists, duplicate initials, unknown signatures/shared markers, different columns/pages, next-author/abstract/references boundaries and historical qualification. The pre-existing original Nguyen fixture still rejects assignment to Sao Mai Nguyen; its explicit `(D.B)` ownership correctly names Damien Bouchabou.

### Fresh required commands

All commands below ran in the exact target worktree after the final implementation changes, with JDK11 explicitly selected.

1. Required tests:

   ```text
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SourceAuthorEmailResolverTest,DiscoveryPaperLayoutRegressionTest,DiscoveryPaperAnalysisServiceTest
   ```

   Exit 0, BUILD SUCCESS, wall 224.58s. Fresh JUnit XML: SourceAuthorEmailResolverTest 29 tests / 0 failures / 0 errors / 0 skips; DiscoveryPaperLayoutRegressionTest 6 / 0 / 0 / 1; DiscoveryPaperAnalysisServiceTest 10 / 0 / 0 / 0. Total 45, 44 passed, 1 skipped. The skip is the pre-existing optional original294 corpus gate; the required #30711 original-byte tests executed. Maven's bound frontend phase also ran 2018 Node tests with 0 failures/0 skips and the configured JS syntax checks. Full command evidence: `artifact://85`.

2. Required full original-byte replay:

   ```text
   /usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-reprocess-30711-child01-final.json
   ```

   Exit 0; 337 rows from all 244 frozen records; every source/body/metadata/first-outcome digest checked by the replay. Runtime 88.88 real / 129.16 user / 4.23 system seconds, maximum RSS 1,071,300,608 bytes. Network calls 0; business write services not constructed. Full evidence `artifact://86`; final command summary `artifact://89`.

3. Required old/new reconciliation:

   ```text
   python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay /tmp/paper-reprocess-30711-child01-final.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv /tmp/paper-reprocess-30711-child01-final.csv --report /tmp/paper-reprocess-30711-child01-final.md --runtime-evidence /tmp/paper-reprocess-30711-child01-runtime.json
   ```

   Exit 0; 337 matched rows; frozen audit hashes accepted. The existing reconciler's candidate-run labels are 51 ALREADY_RECOVERED / 284 NOT_REPRODUCED / 2 REPRODUCED; these do **not** replace the child 00 frozen truth set. The fresh output report explicitly explains that distinction and compares actual baseline-run and candidate-run bindings.

4. Separate real original-byte CLI/consumer smoke:

   ```text
   /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java @/tmp/paper-reprocess-30711-child01-smoke.args
   ```

   Exit 0, 3.29s. The argument file selects the fresh Surefire classpath and `com.weibo.talentintroduction.discovery.service.PaperReprocessParserSmoke`. Actual stdout:

   ```text
   PARSER01_RUNTIME originalRecord=31375 named=3 identityVersion=20260925 extractionVersion=20261004 parserVersion=20261101 consumer=PAPER,RECORD emailGate=REJECT indexed=0 promoted=0 network=0 businessWrites=0
   ```

   This loads the real original PDF and real parser, validates identity evidence and calls the actual queued consumer twice. The test email gate intentionally rejects so no external validation or admission writes occur; successful parsing is not treated as admission.

5. `git diff --check`: exit 0 before staging. Exact authorized-file and empty-index assertions succeeded; before-add, before-commit, after-commit and handoff native identity/reachability checks bind the code to the target worktree. `git commit -m 'feat(fast-p): implement 01'`: exit 0. Commit subject and exact changed-file set checked.

### Binding deltas and unrepaired cases

Comparison key: `(recordId, bodySha256, emailHash, nameHash, page)`. The old side is the retained old run's `newNames`, not a reconstructed historical binary or rewritten first snapshot.

| Metric | Count |
|---|---:|
| Frozen audited combinations | 337 |
| Frozen REPRODUCED combinations | 330 |
| Expected binding gains within frozen REPRODUCED set | 49 |
| Remaining unresolved frozen REPRODUCED combinations | 281 |
| Baseline recovered control retained | 1 |
| Lost baseline bindings among audited combinations | 0 |
| Conflicting/wrong expected-name bindings among audited combinations | 0 |
| Frozen non-reproduced rows excluded from repair truth | 6 |
| Unconfirmed column candidates excluded from repair truth | 47 |
| Incidental gain within excluded rows, not counted as reproduced repair | 1 |
| PAGE_NOT_SCANNED, unchanged selection | 1 |

Reproduced gains by audit pattern: `email_parenthesized_name` 10; `owner_label_on_email` 13; `immediate_name_email` 18; `single_manual` 3; `explicit_correspondence` 2; `unique_author_marker` 2; `initials_in_contact_block` 1. These audit labels are not themselves new parser-rule definitions.

The excluded incidental observation is record 33816, Qiao Peng. Physical page 1 has explicit `Qiao.Peng@qub.ac.uk (Q. Peng)`; PDFBox stream-order text splits the mailbox as `Qiao.P` and `eng@qub.ac.uk`. A separate original-byte PDFBox/layout inspection confirmed the physical contact line. The already-evidenced bounded named-contact rule recovers this row without an added special case. It remains excluded from the 49 reproduced gains and was not used as repair truth or as justification for a new rule.

PAGE_NOT_SCANNED is record 34257, audited page 15; the selected pages remain the original head-two/tail-one set. This is not a successful recovery. No requirement to recover all historical 325 opportunities is inferred; the 281 unresolved reproduced combinations remain explicitly reported, not relabeled as repaired.

### Output reports

- Full fresh replay: `/tmp/paper-reprocess-30711-child01-final.json`
- Per-combination reconciliation: `/tmp/paper-reprocess-30711-child01-final.csv`
- Human-readable replay report: `/tmp/paper-reprocess-30711-child01-final.md`
- Full keyed gain/loss/conflict/remaining/excluded lists: `/tmp/paper-reprocess-30711-child01-delta.json`
- Fresh runtime facts: `/tmp/paper-reprocess-30711-child01-runtime.json`
- Worktree helper/native checkpoint receipts: `/tmp/paper-reprocess-30711-child01-identity.json`
- Final handoff identity receipt: `/tmp/paper-reprocess-30711-child01-handoff-identity.json`
- CLI arguments: `/tmp/paper-reprocess-30711-child01-smoke.args`

All are outside Git. Completed child 00 reports and source inputs were not modified.

### Execution self-check failures and corrections

The following are historical command results from this execution, not substituted for final fresh evidence:

- First required Maven attempt exited 1 during test compilation: the newly added consumer fixture omitted required ElasticsearchProperties constructor fields. Corrected the authorized fixture constructor; evidence `artifact://74` (152.66s).
- Second Maven attempt exited 1: 45 tests, 5 failures, 1 skip; evidence `artifact://76` (219.41s). Failures classified as (a) strict queued payload decoding of archive-only metadata fields, corrected by supplying the existing typed PaperMetadata serialization; (b) stale original Nguyen no-owner expectation despite explicit `(D.B)` evidence, corrected to assert Damien Bouchabou while retaining the wrong-owner rejection; (c) stale expectation that the newly supported physical named-contact line produced no layout contacts, corrected to assert its real named contact; (d) explicit corresponding-author label with doubled whitespace; (e) superscript marker-group geometry in the new physical-baseline fixture.
- Third Maven attempt exited 1: 45 tests, 2 failures, 1 skip; evidence `artifact://80` (220.58s). Only (d)/(e) remained. Their corrections stayed within the uniquely specified explicit-contact/physical-marker requirements: whitespace normalization at the explicit label and contiguous smaller superscript-group handling at the existing baseline boundary. No heuristic threshold expansion or owner-assertion weakening was used.
- Intermediate full replay `/tmp/paper-reprocess-30711-child01-selfcheck.json` exited 0 (337 rows, network 0; 88.73 real seconds, maximum RSS 1,277,394,944 bytes; `artifact://81`/`artifact://82`). It is superseded by the final fresh replay above.
- An initial delta calculation correctly stopped on an overbroad assertion that every gained row belonged to the frozen REPRODUCED set. Original inspection identified the excluded 33816 incidental gain; the report now separates it rather than altering frozen truth or inflating repaired counts.
- The repository's `fix-v` instructions were read, but no independent fix-v verdict or repair plan was emitted. Main clarified that these are execute-p implementation self-check corrections, not verifier-declared repairs. No new scope or authority decision was introduced.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` — shared explicit contact grammar, unique initials and conflict handling; PDF ownership restricted to physical contacts.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` — bounded named contact blocks and evidenced superscript marker geometry; independent parser version.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` — already-selected page text/coverage result shared with analyzer; existing public list extraction remains compatible.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` — shared extraction/coverage diagnostics and parser-version cache invalidation, qualification kept separate.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` — explicit positive/negative semantic and ownership boundaries.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt` — immutable original corpus checks, real PDFBox/consumer regression and standalone real-byte smoke.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt` — first/analyzer parity, parser cache invalidation, unchanged historical qualification/source/first and unscanned-page regression.

This requested execution-log append is not in the implementation commit. Pre-existing uncommitted child 01 brief and master ledger changes were preserved.

### Deviations

- As pre-authorized by the brief, the execute-p worktree helper fails at its line 30 while strictly resolving the unrelated stale registered publish worktree `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b`. It was invoked at the required checkpoints; exact native read-only root/branch/git-dir/common-dir/registration/HEAD/reachability assertions succeeded. No worktree was pruned and no helper was modified.
- The existing reconciler template hardcodes child 00/same-code prose. Only the newly generated external child 01 report's prose was corrected; the reconciler implementation and all child 00 outputs remain untouched. Candidate diagnostic labels are retained with a warning, and frozen-baseline keyed deltas are reported separately.
- Fresh runtime evidence was supplied to reconciliation rather than relabeling the old runtime file's child 00 assertions as current facts. Old source inventory remains historical; no new production measurement is claimed.
- One unrelated optional original294 regression is skipped due to its external corpus gate. No #30711 source/test/replay input was missing or skipped.
- No product scope expansion. No source deletion/overwrite, push, merge, history rewrite, production access, or later-child work.

### Freshness

- Plan identity rechecked: YES, exact approved SHA unchanged.
- Worktree identity rechecked: YES, helper invocation plus brief-authorized exact native assertions.
- Reported implementation commit is HEAD and reachable from target branch: YES.
- Required commands run freshly in this execution after final implementation changes: YES.
- Historical evidence used only as baseline: YES.
- Implementation index and authorized files clean at handoff: YES; fast-p control artifacts remain uncommitted for the controller's evidence checkpoint.
- Independent verification / human acceptance: NOT PERFORMED. A-1/A-2/A-3 have executable supporting evidence but are not self-approved.

### Remaining Blocker

None for the bounded approved implementation. Unrepaired combinations, excluded observations, unchanged PAGE_NOT_SCANNED and the unrelated optional corpus skip are disclosed above.

### Next Action

Run independent child 01 light verification / `verify-p` against `41c24f879591062b0efbe39eb9df4ef3d802754d`, the exact brief and immutable child 00 baseline. Only the controller may advance the master plan. This result is READY_FOR_VERIFICATION, not PASS.

## Epoch 1 — Controller Closing Record
- Implementation: 41c24f879591062b0efbe39eb9df4ef3d802754d
- Terminal code head: 1979768f08ffe58ca3c919326bb0eebbc833e3a1
- Repair rounds: 2; separate fix commits are preserved in fix-log.md.
- Fresh terminal verifier: PaperParserReverifier01R2, attempt 3.
- Terminal light verdict: LIGHT_PASS_WITH_NOTES; F-01/F-02 resolved; O-01/O-02 remain RECORD_ONLY.
- Required action: COMPLETE_CHILD, subject to the complete-manifest artifact checkpoint.
- No extra product verification was performed by the controller. The preceding generic verify-p handoff is superseded operationally by the user-selected fast-p independent light-verification loop; no whole-system PASS is claimed.
