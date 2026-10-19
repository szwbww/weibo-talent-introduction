# Child 02 execution

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-02-replay-evaluation.md
Plan SHA-256: d46ebb59a1bed7301ac38743929b009e0808069d83bad2edf0d28b25f9c83099
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-02-replay-evaluation.md@d46ebb59a1bed7301ac38743929b009e0808069d83bad2edf0d28b25f9c83099
Execution epoch: NEW
Approval basis: Explicit child-02 assignment, exact child brief and approved child/master contracts; controller explicitly authorized an isolated updated JDK11 after the installed runtime's timezone failure.
Executor: ApolloRepair02Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master
Target branch: fast/apollo-assist-repair-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master@fast/apollo-assist-repair-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-assist-repair-master
Pre-execution code SHA / child_base_sha: 41e95f05754cff6e7239abc04e515bdd386f3f80
Initial actual HEAD: 28a1826d25d87e3fef06fdc5fc5cd34f40f2d077
Post-execution code SHA: fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0
Evidence HEAD: N/A — this appended execution report is deliberately excluded from the product commit.
Implementation boundary: 28a1826d25d87e3fef06fdc5fc5cd34f40f2d077..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0 (one product commit); the earlier child base remains 41e95f05754cff6e7239abc04e515bdd386f3f80, with prior evidence/checkpoint commits preserved.
Commit: `feat(fast-p): implement 02`
Master identity: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-master.md@ecf15cc88cecdf2c3e019285d079935fce8c8ec2492cc88acd5fa60bfd06145d

### Preflight and authority

- Read the complete exact child/master plans, child brief, baseline, child01 execution and light-verification handoff. Child01 O-1 remains RECORD_ONLY; no repair to its client/parser identity guard was authorized or made.
- Initial tree contained the controller's modified `ledger.md`; it remains untouched and unstaged. Prior child01 evidence and checkpoint identities were not rewritten.
- No Worker, Gate, Commit, policy, schema, frontend, sample Store/Capture or runtime configuration file changed. No production deployment, original cohort apply, paid external API, real discovery run, job restart, push, merge or additional worktree occurred.
- Baseline evidence was used only as baseline. Its known bare-Docker transport failure was not rerun.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 / I-1, I-5, I-6, I-7 | IMPLEMENTED | ReplayTool.kt, replay_verification.sh, ReplayToolTest.kt, FlowIT.kt | Ordinary main with freeze/evaluate/apply/inspect; no Application/context/scheduler. Repeatable-read read-only freeze, explicit bounded cohort, immutable before/manifest digest binding, restricted atomic/fsynced files, symlink refusal, environment-only credentials and redacted standalone database failures. Independent JVMs use stable Parser.parse/Rules.evaluate and fixed Clock; every group is run twice and compared. |
| T-2 / I-2 through I-5 | IMPLEMENTED | DiscoveryVerificationRepository.kt, RepositoryIT.kt | Explicit CLI TransactionTemplate plus mandatory active transaction. Run→sorted admission→sorted item locking; settled-run check once per batch; full frozen item/admission equality plus snapshot/evidence/reasons SHA CAS; protected and stale receipts; stage-only JSON validation before writes; coherent QUEUED/outcome-null reset, generation increment and run revision bump. Actual MySQL regressions exercise a 100-item batch, concurrent repeat, stale hash, wrong owner, manual HOLD, protected reasons and rollback. |
| T-3 / I-3, I-4, I-6, I-7 | IMPLEMENTED | ReplayTool.kt, FlowIT.kt | Inspect separates rule suggestions from durable admission revisions/projection and request/attempt deltas; missing inputs and unavailable original baseline are explicit. Real isolated Worker/Gate/Commit/projection replay yields APOLLO_UNCLEAR with zero HTTP, including projection failure/retry and report-write-failure recovery via inspect. Store configuration and original samples remain untouched. |
| T-4 / I-1 through I-7 | IMPLEMENTED | ReplayToolTest.kt, RepositoryIT.kt, FlowIT.kt | Required focused command passes all 69 JVM tests with zero skips, including real Docker MySQL; 1,724 bound JS tests also pass. Full required project command succeeds under an isolated current JDK11 (details below). Standalone actual wrapper smoke executes all four commands, old/parser-only/fixed JVM comparisons, duplicate apply, original worker and projection retry. |
| Original historical/new live observation boundary | INTENTIONALLY NOT EXECUTED | Existing evidence and isolated reports only | Original released 89-input payload cannot be recreated and is explicitly UNAVAILABLE; old compiled classes are available and were used against newly frozen synthetic local input. Production cohort operations and a real external new discovery run require separate authority and were expressly prohibited in this invocation. No historical/live-effectiveness claim is made. |

### Early two-version technical checkpoint

No old rules were copied into permanent implementation. Two exact Git revisions were exported into isolated temporary directories (not Git worktrees), compiled with explicit Zulu JDK11, and exercised with a temporary Java compatibility probe calling the stable `Parser.parse(snapshot, read)` and `Rules.evaluate(snapshot, evidence, search, protected)` interfaces.

- Old revision: `e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f`; export `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua`.
- Fixed revision: `41e95f05754cff6e7239abc04e515bdd386f3f80`; export `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-fixed-x8kn2v_r`.
- Old compile: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dmaven.test.skip=true compile dependency:build-classpath -Dmdep.outputFile=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/classpath.txt` → exit 0, `artifact://278`.
- Fixed compile: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dmaven.test.skip=true compile` → exit 0, `artifact://283`.
- Probe used a verified, bound ORCID snapshot and lowercase-public person document, both at UTC `2026-10-19T00:00:00Z`. Separate real JVMs printed `version=old facts=0 outcome=PENDING evaluatedOn=2026-10-19` and `version=fixed facts=1 outcome=PENDING evaluatedOn=2026-10-19`, both exit 0. Earlier unbound/stale-target exploratory probe results were not used as compatibility evidence.
- Old parser SHA: `889a0b5def6a59cdc32efab3a2cdd7101f61948835c4d891161baeb3ee004960`; old rules SHA: `7198dee48b904e5b4e9cc8d3d5932dd29a562381af836de36e31c19b52abc500` (match approved baseline hashes).
- Fixed parser SHA: `3f2df3ce1c5187dd0db4f8dbd17b23d781d82435c0ebb3f948e7efa9cdcf7ca7`; fixed rules SHA: `8ac3233fd71e820db4ae8ce139216981de8ea2ef20d27b33cb16746a4e2eb110`.
- The final CLI smoke below then proves the complete newly frozen three-way comparison, not just binary entry-point compatibility. Original historical raw input remains UNAVAILABLE, not reconstructed from old redacted output.

### Commands

All repository commands ran against the exact target worktree. No implementation edit followed the passing focused run, full runs or CLI smoke.

| Command | Result | Evidence |
|---|---|---|
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationFlowIT,DiscoveryVerificationReportingIT,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS, exit 0 | 69 JVM tests, 0 failures/errors/skips; ReportingIT 10, RepositoryIT 12, FlowIT 21, ToolTest 5, GateTest 6, sample tests 15. Real MySQL 8.0.36 Docker containers. Bound JS: 1,724 passed, 0 failures/skips; both JS syntax checks run. BUILD SUCCESS. `artifact://297`. |
| Same focused command during initial implementation | FAIL, exit 1, subsequently corrected | First compile exposed duplicate companion and missing Java static invocation parentheses (`artifact://288`); second exposed nested-object access through an instance in the new test (`artifact://291`); third reached 69 tests with five file-safety fixture errors because macOS `/var` is a symlink (`artifact://293`). Existing companion was reused, test referenced the object type, and temporary test paths were canonicalized with toRealPath; symlink refusal was not weakened. Final fresh command above passes. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 test` | FAIL, exit 1 — installed JDK timezone database | 5,225 JVM tests, 0 failures, 19 errors, 21 existing gated/disabled skips. All 19 errors are `ExpertContactLocationServiceTest` initialization: Zulu 11.0.15 does not know `America/Coyhaique`. No unrelated source/config/test was altered or failure suppressed. `artifact://301`. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 test` | PASS, exit 0 | 5,225 JVM tests, 0 failures/errors, 21 unchanged gated/disabled skips; 1,724 JS tests passed, 0 failed/skipped; both bound syntax checks ran; BUILD SUCCESS. `artifact://311`. Full Maven default test selection does not replace the explicit *IT command above. |
| `bash -n scripts/expert_discovery/replay_verification.sh` | PASS, exit 0 | Ran after final source state and again at handoff; `artifact://314`. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home 'REPLAY_BASELINE_CLASSPATH=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/target/classes:/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/lib/*' mvn -Dapi.version=1.40 -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java '-Dexec.args=-Dapi.version=1.40 -classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT --replay' -Dexec.classpathScope=test exec:exec` | PASS, exit 0 | Forked actual CLI wrapper four-command smoke, separate six evaluator JVMs, real isolated MySQL and original Worker/Gate/Commit/projection. BUILD SUCCESS; `artifact://308`. |
| `git diff --check` | PASS, exit 0 | Final source/staging preparation; `artifact://312`. |
| Plan/worktree identity helpers with exact expected root/branch/git-dir | PASS, exit 0 | Initial gate `artifact://274`, pre-stage `artifact://312`, pre-commit `artifact://313`, handoff `artifact://314`; exact child/master hashes unchanged. |
| `git diff --cached --name-only` before product commit | PASS, exit 0 | Printed exactly the six approved implementation/test/script paths; `artifact://312`. No report or ledger staged. |
| `git commit -m 'feat(fast-p): implement 02'` and `git merge-base --is-ancestor fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0 fast/apollo-assist-repair-master` | PASS, exit 0 | Commit is exact target HEAD, subject correct, 6 files / 720 insertions; branch reachability reconfirmed at handoff. `artifact://313`, `artifact://314`. |

### Isolated current JDK11 environment

The controller explicitly approved resolving the unrelated installed tzdb failure through a temporary updated JDK11, without source fixes or global JDK changes.

- Vendor/version: Eclipse Temurin OpenJDK `11.0.32.1+1`, aarch64 macOS, `java -version` confirms `Temurin-11.0.32.1+1`.
- JAVA_HOME: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home`.
- Source: official Adoptium assets API, package `https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jdk_aarch64_mac_hotspot_11.0.32.1_1.tar.gz`.
- Archive SHA-256: `c487a1c3a56588b3a32d0d07cdd526bf3d401c4b8422be772ca178bfcd647f3f`, matched official metadata before extraction.
- Installed `/Library/Java/JavaVirtualMachines/zulu-11.jdk` and global Java configuration were not modified. The failed old-runtime command and successful current-runtime command are both retained above.
- The 21 pre-existing gated/disabled full-suite skips are recorded, not claimed as executed. Required explicit DiscoveryVerificationRepositoryIT/FlowIT/ReportingIT and sample regressions all ran with zero skips in the focused command.

### Actual CLI and database proof

```text
REPLAY_CLI_SMOKE itemId=1 outcome=APOLLO_UNCLEAR admissionRevision=0 projection=PROJECTED HTTP_DELTA=0 attempts=1 auto=1 repeatApply=0
```

Evidence directory:
`/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/verification-cli-1907514589370258349/frozen/2001/4e68a465263607da32b30675a474ca27072c4ffe6df410d3e7ca3a8c3fdafe6e`

- `before.json` SHA: `4e68a465263607da32b30675a474ca27072c4ffe6df410d3e7ca3a8c3fdafe6e`; actual stat observes directory 0700, before/manifest 0600.
- Actual wrapper subprocesses ran freeze → evaluate → apply → repeat apply → inspect. CLI did not instantiate a worker. The isolated fixture resumed the original Worker separately after queue-only apply.
- Same frozen input/time, each group repeated twice: old 0 facts/PENDING/unmatched; parser-only 1 SELF CURRENT employment/PENDING/unmatched; fixed 1 SELF CURRENT employment/APOLLO_UNCLEAR/UNKNOWN/matched. `evaluate-1792414536985-0787545e-7cdf-4ecb-86d6-f8314ac8332b.json`.
- Actual inspect: COMPLETED/APOLLO_UNCLEAR/APOLLO, admission revision 0 (non-null), projection PROJECTED, durable admissions 1, attempt delta 0, request delta 0; no rule-pass/commit-not-admitted rows. `inspect-1792414541081-867b9083-62f7-4780-b890-0ac1dd8fadcc.json`.
- A deliberately injected local projection HTTP 500 was retried by the existing projection path; it is not a smoke failure. Admission revision and one original persisted Apollo attempt remain unchanged; no new ORCID/Apollo HTTP occurred.
- Provider/ES responses are isolated fixture transports; MySQL, parser/rules, worker, gate, transactional commit and projection writer are real production implementations. This is not a claim of production Apollo/Elasticsearch access.
- The original historical 89-input bytes were released before this task; fixed ID attachments and earlier redacted/conflict evidence remain intact. New synthetic evidence is not represented as the old cohort, and no accuracy/recall or live effectiveness metric is asserted.
- These restricted temporary exports/replay artifacts supply verifier evidence. Cleanup paths are recorded in freeze-summary/report; no sample-store cleanup or production-data cleanup was performed.

### Changed Files / implementation changelog

- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — bounded settled-run CAS replay and stage-only evidence validation; no admission/ES/contact writes.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt` — ordinary isolated freeze/evaluate/apply/inspect main, secure files, version-hash validation and independent JVM comparisons, separate rule/actual reports.
- `scripts/expert_discovery/replay_verification.sh` — explicit JDK11, controlled prebuilt classpath/unpacked WAR, environment credentials, no compilation or Application startup.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt` — bounded input, stage-only transform, atomic permissions/symlink refusal, corruption, fixed offline evidence and standalone credential redaction.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` — real MySQL batch/CAS/settlement/manual protection/rollback/repeat tests.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — historical replay→original worker/gate/commit/projection, zero-HTTP/protection/report recovery and actual four-command wrapper smoke.

Exact file-count receipt is the pre-commit `git diff --cached --name-only` output in `artifact://312`, which printed these six paths and no others.

### Deviations

- No implementation whitelist, state/schema, business-policy or production-authority expansion.
- Runtime-only adjustment: controller-approved isolated current JDK11 resolved an observed old-Zulu tzdb limitation. The required command was rerun in full, not filtered or weakened.
- macOS temporary paths were canonicalized in test fixtures; production symlink rejection remains strict.
- Old compiled baseline is available; original historical raw input and live new-run observation are explicitly unavailable/not authorized. Only newly frozen isolated input supports the demonstrated three-way comparison.

### Freshness

- Plan identity rechecked: YES, exact child/master hashes unchanged.
- Worktree identity rechecked: YES, exact root/branch/git-dir unchanged.
- Reported commits reachable from target branch: YES; post-code commit is target HEAD.
- Required commands run this invocation: YES, freshly after final implementation edits.
- Historical evidence used only as baseline: YES.
- Product/index state: clean after product commit; only pre-existing controller `ledger.md` remained dirty before this execution-report append. This report is intentionally unstaged/excluded from product commit.
- No verify-p, review-p or repair-p loop was invoked. READY_FOR_VERIFICATION is an execution handoff, not an independent PASS or final aggregate certification.

### Remaining Blocker

- None for child implementation handoff. Use the isolated current JDK11 for the full project test command; installed Zulu 11.0.15's tzdb limitation remains an environment observation, not a repository change.
- Production cohort/new external-run manual acceptance remains deferred under the explicit no-production/no-paid-API authority boundary.

### Next Action

- Controller dispatches fresh independent four-gate light verification for child 02, passing both runtime environments, complete required-command evidence and the precise product boundary.

