# Child 03 Execution

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master/docs/plans/2026-10-18/discovery-samples-03-replay.md`
Plan SHA-256: `33d8fc1e3b2e00a2a865c683b8c8677ba2655cdaa4af30aea6a7eb7ef8a429b0`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master/docs/plans/2026-10-18/discovery-samples-03-replay.md@33d8fc1e3b2e00a2a865c683b8c8677ba2655cdaa4af30aea6a7eb7ef8a429b0`
Execution epoch: NEW
Approval basis: current child03 brief and explicit user assignment, with Main's current-message authorization to use the preserved independent child02 verifier dataset after the existing regression helper deleted shared acceptance inputs.
Executor: `SampleReplayImplementer`, execute-p; constrained-verification guidance used for compiler repair classification, not independent verification.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master`
Target branch: `fast/discovery-samples-master`
Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-samples-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master@fast/discovery-samples-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-samples-master`
Pre-execution code SHA: `e40863f6d5864ac5c882e7cb83f49e639a84396e`
Pre-execution retained HEAD: `93755b42d9ca6ae4bf8d233ef65b477823c8fd5f`
Post-execution code SHA: `eb6080147141bc3d7dd00054705ba8973ea2b270`
Evidence HEAD at handoff: `eb6080147141bc3d7dd00054705ba8973ea2b270`; this execution report is intentionally uncommitted for the controller.
Implementation boundary: `93755b42d9ca6ae4bf8d233ef65b477823c8fd5f..eb6080147141bc3d7dd00054705ba8973ea2b270`.

### Task status

| Requirement | Status | Files and execution evidence |
|---|---|---|
| O-1 / C-3..C-5 | IMPLEMENTED | Test-only CLI list scans canonical final packages, validates complete typed metadata, context, canonical input/sample SHA, body bounds/SHA, symlinks and directory/file identities. Valid retained packages grouped by execution/bucket; invalid packages reported separately. Actual shell list: 9 packages, 7 saved bodies, no invalid packages. |
| I-1 / master I-2 | IMPLEMENTED | Saved authors/order/settings feed production PdfEmailExtractor through one local saved-body executor with deadline=null; original bytes/content type/status are not replaced. XML calls production JatsXmlEmailParser directly, no OpenAlex postprocessing. Real PDF uses unchanged `discovery/pdf-contact-coverage.zip` source bytes and metadata author construction from the existing regression; generated HTML/JATS and real PDF match all direct-parser AuthorEmail fields. |
| O-2 / I-2 / C-6 | IMPLEMENTED | Stable report ordering; all AuthorEmail fields and multiplicity retained; unique normalized-email changes versus ambiguous/duplicate multisets are separate. Comparison checks report version, key sets, SHA, status and config; mismatches exit2 without metrics. Manual-label SHA matching and exact triples yield synthetic TP1/FP1/FN0, precision0.5/recall1.0. No labels means accuracy=null/labelledBodies0. |
| I-3 / master I-4 | IMPLEMENTED | No application context/business repositories or provider runtime. Rejecting request factory is exercised; current-body executor rejects other requests. External entity fixture yields no bindings without fetching its HTTP entity. Output-inside-root, symlink, traversal, SHA/size/metadata/schema errors, missing fields, deterministic cleanup race and malformed XML are tested. Actual frozen input/source hashes remain unchanged across shell operations. |
| A-1 machine smoke | IMPLEMENTED | Four actual shell invocations each independently performed mandatory clean test-compile/dependency classpath followed by Java main: list/replayA/replayB/compare all exit0. Execution9001 has PDF/HTML/XML plus metadata-only403. Diff added=removed=changed=0, accuracy=null, labelledBodies=0; main and test loaded-class SHA agree across A/B. |
| A-2 / A-3 machine artifacts | IMPLEMENTED | Final test invocation exports replay-fixtures good/no-body/hash-mismatch/unsupported and replay/cases.json, labels, A/B, labelled/unlabelled comparisons, per-case reports, before/after fixture hashes. Expected case exits0/0/2/2 and cross-input comparison2 are asserted. Human acceptance and independent verification remain pending. |

### Changed files / local commit

Commit `eb6080147141bc3d7dd00054705ba8973ea2b270`, subject exactly `feat(fast-p): implement 03`, contains only:

1. `src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplay.kt` — full local CLI, reader, production-parser adapters, reports/provenance/comparison/metrics.
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplayTest.kt` — parser equivalence, readonly/schema/path/limits/race/offline/labels/duplicate/config/version tests and synthetic acceptance export.
3. `scripts/expert_discovery/replay_samples.sh` — executable argv-safe JDK11/link-worktree/lock guarded mandatory clean-build launcher.
4. `docs/runbooks/discovery-sample-replay.md` — command/format/bounds/security/provenance/labels/capture-retention runbook and release documentation.

No production/parser/store/pom/resource/previous regression-test changes. No ledger, plans, verify-log or fix-log edits. Existing dirty controller ledger preserved unstaged. Runbook is ignored by repository rules; normal staging reported that fact, then only this exact approved new runbook was force-added. Staged whitespace check exited0; exact four-file staged list and commit/reachability receipts are `artifact://143` / `artifact://144`. No push, merge, reset, rebase, amendment, deployment or later-child work.

### Required commands and observed implementation attempts

All commands ran in the bound target worktree using JDK11:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoverySampleReplayTest -DskipNodeTests=true test
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test
bash -n scripts/expert_discovery/replay_samples.sh
```

| Invocation | Result / counts | Evidence |
|---|---|---|
| Required failing-before test, before implementation class existed | Exit1, expected unresolved DiscoverySampleReplay references at test compilation; no test execution | `artifact://123`, 2:20 |
| First required regression invocation after implementation | Exit1, new-tool compilation: cross-module nullable-property smart casts and captured-local smart cast | `artifact://128`, 2:34 |
| Next required regression invocation after local immutable-variable compiler correction | Exit1, 77 run / 4 failures / 0 errors / 1 optional skip (72 passed); macOS JDK11 does not expose SecureDirectoryStream, so all new-reader scenarios rejected input | `artifact://131`, 3:07 |
| Final required regression invocation after portable canonical-directory/file-identity + NOFOLLOW reader implementation | Exit0, 77 run / 76 passed / 0 failures / 0 errors / 1 optional skip | `artifact://134`, 3:13; exported `/tmp/discovery-sample-acceptance/replay-cli/test-results.json` |
| Final shell syntax invocation | Exit0 | `bash -n scripts/expert_discovery/replay_samples.sh`, fresh after final implementation |

Final suite counts: DiscoverySampleReplayTest 4/0 failures/0 errors/0 skipped; PdfEmailExtractorTest 42/0/0/0; EuropePmcDataSourceTest 31/0/0/1. The skip is the existing optional real-EuropePMC API test, not a substituted network request. The existing broad service-fixture baseline errors were not part of this required focused command and were not repaired.

The compiler correction stayed in the new authorized tool: snapshot nullable model properties into local vals, and snapshot parser failure code before assigning captured mutable state. The platform adaptation remained in the new read-only reader: validate canonical ancestors and file identities before and after bounded NOFOLLOW reads; disappeared/replaced input does not become empty success. No production interface, parser algorithm or new runtime dependency was introduced.

### Shared acceptance-helper deletion and authorized replacement input

The required focused Maven command executes the existing `PdfEmailExtractorTest` acceptance helper. `SampleCaptureAcceptance` at its existing lines1119–1124 initializes `base=<sample.acceptanceDir>/capture` and recursively deletes that directory. Because the focused command does not execute the service scenarios that create execution9001/9002/9011, it removed the original shared `capture/samples` and `capture/apollo.json` rather than restoring them. The new replay code/test never deletes capture inputs.

Initial full-shell list therefore completed its mandatory clean build, then Java correctly exited2 `INVALID_PATH`. That observed run is preserved in:

- `/tmp/discovery-sample-acceptance/replay-cli/initial-commands.json`
- `/tmp/discovery-sample-acceptance/replay-cli/initial-shell-list.log` (Maven BUILD SUCCESS; Java INVALID_PATH)
- `/tmp/discovery-sample-acceptance/replay-cli/input-hashes-before.txt` (the original, now-deleted shared snapshot's 17-file inventory)

Main explicitly authorized using the already independently generated child02 verifier smoke dataset instead, without modifying the out-of-scope helper or inventing/restoring capturedAt values. Existing source `/tmp/discovery-sample-verifier-smoke-epoch2/capture/samples` was intact. The following actual copy created a dedicated frozen analysis input:

```sh
test ! -e /tmp/discovery-replay-frozen-20261019 && cp -R /tmp/discovery-sample-verifier-smoke-epoch2/capture/samples /tmp/discovery-replay-frozen-20261019
```

This is an exact copy of that existing actual-path synthetic capture dataset, **not** an exact restoration of the deleted original acceptance snapshot. Its metadata SHA differs from the original snapshot; no historical or new production input claim is made. Frozen/source relative-name SHA inventories were equal before CLI and remain equal afterward: 17 files (including the empty .writer.lock), 0 changed entries.

Fresh proof paths:

- `/tmp/discovery-sample-acceptance/replay-cli/input-provenance.json`
- `replay-cli/frozen-input-hashes-before.txt`, `replay-cli/frozen-input-hashes-after.txt`
- `replay-cli/frozen-source-hashes-before.txt`, `replay-cli/frozen-source-hashes-after.txt`
- `/tmp/discovery-sample-acceptance/replay-cli/read-only.json`: inputHashesUnchanged=true, sourceHashesUnchanged=true, copiedBytesStillEqual=true, changedFiles=0.
- Frozen relative hash-inventory SHA256: `69148094214ed9c73c3772966a50cf44ee128a6f3c2f1582ad238264327a8fc4`.

### Actual full-shell / Java commands

Each command below ran with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, argv passed separately, outputs outside canonical `/private/tmp/discovery-replay-frozen-20261019`:

```sh
bash scripts/expert_discovery/replay_samples.sh list --samples /tmp/discovery-replay-frozen-20261019 --output /tmp/discovery-sample-acceptance/replay-cli/list.json
bash scripts/expert_discovery/replay_samples.sh replay --samples /tmp/discovery-replay-frozen-20261019 --execution-id 9001 --output /tmp/discovery-sample-acceptance/replay-cli/A.json
bash scripts/expert_discovery/replay_samples.sh replay --samples /tmp/discovery-replay-frozen-20261019 --execution-id 9001 --output /tmp/discovery-sample-acceptance/replay-cli/B.json
bash scripts/expert_discovery/replay_samples.sh compare --before /tmp/discovery-sample-acceptance/replay-cli/A.json --after /tmp/discovery-sample-acceptance/replay-cli/B.json --output /tmp/discovery-sample-acceptance/replay-cli/diff.json
```

Every launcher ran the exact required `mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=<mktemp>` before Java, not a stale-target shortcut. Results: list exit0 (174.161s); replayA exit0 (148.509s); replayB exit0 (142.515s); compare exit0 (141.696s). Full stdout/stderr logs: `/tmp/discovery-sample-acceptance/replay-cli/shell-{1,2,3,4}.log`; exact argv/exits/durations: `/tmp/discovery-sample-acceptance/replay-cli/commands.json`.

List reports 9 retained samples, 7 saved bodies, 2 metadata-only samples, 0 invalid samples. Execution9001 replay records 3 parsed bodies (PDF/HTML/XML) and 1 NO_BODY403, with NO_BODY parserCalls=0/networkCalls=0. No bindings are produced by these no-email controls. Compare reports COMPARABLE, added0/removed0/changed0/unchanged0, no conflicts, accuracy=null, labelledBodies0, beforeMetrics/afterMetrics=null. These figures are sample/body counts, not expert counts or improvement.

### Loaded source/classes provenance

Both shell replay reports record the same:

- codeHead: `93755b42d9ca6ae4bf8d233ef65b477823c8fd5f`
- dirtyDiffHash: `f99880de35401c76a6388e64e22bbd5c7cc03dbbfd5c894b8c6cda1deeff6523`
- sourceTreeSha256: `0e716a625bfa4c268102c4f9cece32bd2d922129b4d72428dfed8af4436b1719`
- actual loaded main classes: canonical target/classes, 2775 class files; SHA256 `b509834c303ec3a377713173b41a670b5e8fc150e43da1c8980e65bb30d283a4`
- actual loaded tool/test classes: canonical target/test-classes, 1476 class files; SHA256 `54ca3e466a877561aec59d13ea136c67426ebe94ddc1fdb9662db8b18316ca77`
- Java11.0.15; actual classpath-string SHA256 `73756fd669a6ef800f8c9198d6cea3c18b28bfa81df4634211064885f20f7cb6`; cleanBuild=true.

Shell smoke occurred before the product commit, against the exact pending source later committed as eb6080147141bc3d7dd00054705ba8973ea2b270; reports honestly preserve precommit HEAD/dirty source identity. No source was changed after the final successful regression/shell commands. Unit-test exported reports mark cleanBuild=false, and their pre-clean main/test aggregate includes historical stale target classes; they are not misrepresented as the fresh shell compiled identity. Fresh shell A/B classes hashes above are the clean-build evidence. Source state alone is not compiled provenance.

### A-2/A-3 exported artifacts and limits

Root `/tmp/discovery-sample-acceptance/replay-fixtures` contains synthetic `good`, `no-body` (HTTP403), `hash-mismatch`, `unsupported` canonical package directories. Root `/tmp/discovery-sample-acceptance/replay` contains `A.json`, `B.json`, `list.json`, `diff.json`, `labels.json`, `labelled-diff.json`, `good.json`, `no-body.json`, `hash-mismatch.json`, `unsupported.json`, `input-mismatch.json`, `hashes-before.json`, `hashes-after.json`, `cases.json`.

The final tests assert good0, no-body0 with parserCalls0, hash-mismatch2/HASH_MISMATCH, unsupported2/UNSUPPORTED_SCHEMA, cross-input compare2/INPUT_MISMATCH, readonly fixture hashes equal. cases.json records offline networkCalls0/businessWrites0, XML external entity passed with bindings0, inputHashesUnchanged=true. Business-write absence is structural (no business/application dependencies), not an external DB telemetry measurement. Rejecting HTTP factory plus current-body fake is code-level isolation, not an OS sandbox. No heap/runtime/network-recovery improvement claim is made.

Synthetic labelled comparison: labelledBodies1; both beforeMetrics and afterMetrics TP1/FP1/FN0, precision0.5, recall1.0. This fixture truth was explicitly constructed for acceptance, not independently human-checked real-paper truth. The real-PDF equivalence test reads the unchanged existing archive resource; it does not write bytes or alter its original truth.

### Report hashes

| Report | SHA256 |
|---|---|
| replay-cli/list.json | `5c01ee702ea1613aa84d9436f7b6768bf224f844862f56a4822d8ff6a50f2032` |
| replay-cli/A.json | `36e632f2c3c7f64da692cbaf977a59da69414587aabd57d8c74c598848f4557b` |
| replay-cli/B.json | `5af9679903754f09635a680d59b38f1cc8b1a2dee0edb3303277f7cbb7e7d688` |
| replay-cli/diff.json | `7cbd03538f9a6e393829aed1f3c2ebc241c20c4985b0a7ec347b2105d3f4cc8a` |
| replay-cli/read-only.json | `ae5006aea8bc13ae0d479db96d6ff77669c909b4b042d5a1da7816c3aadd3a14` |
| replay/labels.json | `225dd558f235115eb23fbe719b00625ada1914ce2eb462266de1afb98e206609` |
| replay/labelled-diff.json | `96cfd41c5a47074544cfd71895521afbf38f1ce6125637851b6b7e06fac99cc0` |
| replay/cases.json | `f00ed1c7122533b2f485f6c08d34ea15847f51cb1f1c0b57a0b421b3896553cc` |

### Deviations / freshness / handoff

- Authorized dataset substitution described above; no acceptance/runtime contract weakened and no out-of-scope helper changed.
- Final commands ran freshly in this invocation; historical evidence used only for prerequisite provenance.
- Plan identity rechecked unchanged before staging; target identity checked before each staging/commit operation. Commit is HEAD of the correct target branch and its ancestor check exited0.
- Required implementation index clean after product commit; only controller-owned ledger and this execution evidence remain outside product commit. No independent verification or whole-system PASS declared.
- Remaining blocker: none within the approved child03 deliverable.
- Next action: controller runs a distinct fresh independent light verifier / verify-p, commits evidence separately, then decides later workflow. Manual acceptance remains PENDING.


## Epoch 1 — Controller closing record
- Terminal code head: eb6080147141bc3d7dd00054705ba8973ea2b270
- Terminal verdict: LIGHT_PASS_WITH_NOTES; required action COMPLETE_CHILD; independent verifier SampleReplayLightVerifier.
- Fix round: 0; no AUTO_FIX findings.
- Independent required Maven command exit0: 77 tests, 76 passed, 0 failures/errors, 1 existing optional skip; shell syntax exit0.
- Independent actual wrapper list/replayA/replayB/compare each performed full clean build and Java execution, all exit0; added/removed/changed0, accuracy=null; all17 frozen input/source hashes unchanged; loaded source/main/test class aggregates independently matched.
- O-1 records existing acceptance-helper directory deletion and honest independent-child02 frozen dataset provenance. No real production sampling/accuracy improvement or whole-system verification claimed.
