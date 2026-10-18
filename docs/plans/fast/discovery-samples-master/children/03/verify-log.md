# Child 03 Verification Log


## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 03 — docs/plans/2026-10-18/discovery-samples-03-replay.md
Boundary: e40863f6d5864ac5c882e7cb83f49e639a84396e..eb6080147141bc3d7dd00054705ba8973ea2b270
Verifier: SampleReplayLightVerifier
Epoch: 1; Attempt: 1
Timestamp: 2026-10-19T01:09:10.910553+08:00
Plan SHA256: 33d8fc1e3b2e00a2a865c683b8c8677ba2655cdaa4af30aea6a7eb7ef8a429b0 (fresh shasum match)
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master; HEAD eb6080147141bc3d7dd00054705ba8973ea2b270, branch fast/discovery-samples-master.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary git diff --name-status lists the four authorized new deliverables: src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplay.kt; src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplayTest.kt; scripts/expert_discovery/replay_samples.sh; docs/runbooks/discovery-sample-replay.md. Remaining boundary paths are controller evidence only: child02 execution/fix-log/verify-log, child03 brief, master ledger. No production/parser/pom/resource/old-test change. Fresh git diff HEAD-boundary -- src/main src/test launcher runbook pom.xml is empty. |
| Plan and invariants | PASS | DiscoverySampleReplay.kt:134,155,206,266 validate output isolation, canonical identities, schema, bounds and SHA before parse; :279,284,316 reject HTTP and dispatch saved PDF/HTML bytes, saved author order and every PdfExtractionProperties field to production PdfEmailExtractor.extract(deadline=null), and XML bytes to production JatsXmlEmailParser.parse. Consumer dispatch checked in PdfEmailExtractor.kt:60-162,218-315 and JatsXmlEmailParser.kt:35-44; no copied parser or business application context. :377 records actual loaded main/test class roots and SHA; :432 checks report keys/input/body/config, labels and every binding field with explicit duplicate/conflict handling. DiscoverySampleReplayTest.kt:44,112,135,178 freshly passed offline/readonly/JATS and unchanged real-PDF equivalence, malformed/no-body/SHA/schema/race/limits, config/input/labels, duplicate and field comparisons. Fresh shell report configs equal frozen manifest configs; 17 input hashes unchanged; independent-evidence.json recomputes class/source digests. |
| Required commands | PASS | Fresh JDK11 focused Maven exit0: 77 run, 76 passed, 0 failures/errors, 1 pre-existing disabled online EuropePMC smoke skip (4 replay + 42 PDF + 31 EuropePMC). bash -n exit0. Actual list/replayA/replayB/compare wrapper invocations all exit0 and each ran mandatory clean test-compile + dependency:build-classpath before Java, sequentially in this worktree. List 9 samples/7 bodies/0 invalid; execution9001 3 parsed bodies + 1 NO_BODY403; compare added=removed=changed=0, accuracy=null, labelledBodies=0. Fresh behavior matches execution.md baseline; unrelated broad baseline fixtures not rerun. Raw receipts artifact://157 and artifact://158. |
| Downstream interfaces | PASS | N/A — child03 is terminal in this master; no later-child interface required. Existing schema/store/parser interfaces remain unchanged. |

#### Fresh commands and outputs
All commands ran in the bound worktree with JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Output root O=/tmp/discovery-child03-light-epoch1-attempt1-20261019, chosen separately from preserved earlier smoke outputs; input F=/tmp/discovery-replay-frozen-20261019.

- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-child03-light-epoch1-attempt1-20261019 test — exit0, finished 2026-10-19T00:56:58+08:00, 2:48; artifact://157 lines390/424/431/435/447.
- bash -n scripts/expert_discovery/replay_samples.sh — exit0.
- With the same explicit JAVA_HOME, bash scripts/expert_discovery/replay_samples.sh list --samples /tmp/discovery-replay-frozen-20261019 --output /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-list.json — exit0.
- With the same explicit JAVA_HOME, bash scripts/expert_discovery/replay_samples.sh replay --samples /tmp/discovery-replay-frozen-20261019 --execution-id 9001 --output /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-A.json — exit0.
- With the same explicit JAVA_HOME, bash scripts/expert_discovery/replay_samples.sh replay --samples /tmp/discovery-replay-frozen-20261019 --execution-id 9001 --output /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-B.json — exit0.
- With the same explicit JAVA_HOME, bash scripts/expert_discovery/replay_samples.sh compare --before /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-A.json --after /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-B.json --output /tmp/discovery-child03-light-epoch1-attempt1-20261019/cli-diff.json — exit0.

The four shell invocations were joined by &&, so later execution required every preceding exit0. Each wrapper executed mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=<fresh mktemp>, followed by actual Java main; no stale-target/direct-test shortcut. artifact://158 has four clean/build-classpath/BUILD SUCCESS receipts (finishes 00:59:32, 01:02:20, 01:05:40, 01:08:11 +08:00). Java report creation and COMPARABLE final output confirm consumer execution, not just successful builds.

#### A-2/A-3 and offline/input proof
Fresh O/replay-fixtures/{good,no-body,hash-mismatch,unsupported} and O/replay/{cases,labels,labelled-diff,input-mismatch,hashes-before,hashes-after}.json inspected. Exported/asserted exits are 0/0/2/2, with NO_BODY403 parserCalls=networkCalls=0, HASH_MISMATCH, UNSUPPORTED_SCHEMA, and cross-input INPUT_MISMATCH exit2. Labelled synthetic comparison: labelledBodies1; before/after TP1 FP1 FN0 precision0.5 recall1.0. No labels gives accuracy=null/labelledBodies0. These labels are constructed acceptance truth, not independent real-paper annotation. Test-reported networkCalls0/businessWrites0 and XXE-empty-output are backed by rejecting factory/current-body-only executor, unchanged JATS external-entity restrictions, no Spring/context/repositories, and no capture scope (DiscoverySampleCapture.kt:29-59 immediately no-ops without scope); they are structural/code-level evidence, not an OS network sandbox or external DB telemetry measurement. Existing timeout regression cases use local loopback servers; no online/provider test was enabled or invoked.

Actual before/after hash lists are O/frozen-hashes-{before,after}.json and O/source-hashes-{before,after}.json. They each enumerate 17 files including .writer.lock, compare equal, and equal each other: changedFiles0. Fresh exported replay-fixtures inventories also equal actual files (7 files). Frozen input is the dedicated copy of /tmp/discovery-sample-verifier-smoke-epoch2/capture/samples, NOT restoration of the erased original shared acceptance snapshot; only synthetic actual-path child02 captures are claimed.

Fresh A/B provenance is identical and cleanBuild=true: HEAD eb6080147141bc3d7dd00054705ba8973ea2b270; dirtyDiffHash cd372fb85148700fa88095e3492d3f9f5beb43e555e5ff26d95f5a6adc36f8e6; sourceTreeSha256 0e716a625bfa4c268102c4f9cece32bd2d922129b4d72428dfed8af4436b1719; actual loaded target/classes 2775 class files SHA256 b509834c303ec3a377713173b41a670b5e8fc150e43da1c8980e65bb30d283a4; actual loaded target/test-classes 1476 class files SHA256 54ca3e466a877561aec59d13ea136c67426ebe94ddc1fdb9662db8b18316ca77; Java11.0.15; actual classpath-string SHA256 73756fd669a6ef800f8c9198d6cea3c18b28bfa81df4634211064885f20f7cb6. Independent recomputation against actual post-shell files matches all three source/main/test aggregates (O/independent-evidence.json). Non-clean test-export provenance is not substituted for shell proof. Source hash equals execution.md source identity; fresh committed HEAD is reported honestly rather than the implementation smoke precommit HEAD.

### AUTO_FIX
- N/A — no proven four-gate violation; no repair made or requested.

### RECORD_ONLY
- O-1: Existing PdfEmailExtractorTest.kt SampleCaptureAcceptance initializer deletes <acceptanceDir>/capture; this pre-existing out-of-scope helper was left unchanged. Unique verifier output prevented deletion of preserved evidence, and the independently sourced frozen copy above was used honestly; no restoration or production sampling claim. Human acceptance/aggregate system verification and real labelled optimization claims remain outside this child light gate.

### Required Action
- COMPLETE_CHILD
