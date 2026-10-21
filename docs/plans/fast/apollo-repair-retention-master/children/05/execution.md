# Child 05 — Execution log

## Execution Result: READY_FOR_VERIFICATION

- Executor: ApolloRetention05; execution epoch: NEW.
- Approval basis: current child05 invocation; child04 LIGHT_PASS_WITH_NOTES checkpoint accepted. O-04-01 remains a record-only broader integration observation and was not repaired.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-05-decision-audit.md`
- Plan SHA-256: `de1aba525acca6e45b8ef434360f85fd79715944a46af36b18f33c44e42e1809`.
- Execution ID: the canonical plan path above followed by `@de1aba525acca6e45b8ef434360f85fd79715944a46af36b18f33c44e42e1809`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`.
- Branch: `fast/apollo-repair-retention-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: canonical target root + `@fast/apollo-repair-retention-master@` + Git directory above.
- Pre-execution code SHA / child_base_sha: `835bfae52e32482313df2f76f99572f578aeb468`.
- Pre-execution HEAD: `6948ceeb7299fa7fa6b2e487cebf20d6c9dc989a` (child04 evidence/checkpoint commits followed the code base).
- Post-execution code SHA: `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`.
- Product subject: `feat(fast-p): implement 05`.
- Implementation boundary: `835bfae52e32482313df2f76f99572f578aeb468..a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`; product commit excludes fast-p artifacts.
- Evidence HEAD at executor handoff: `a489c28e93e0357e26f6e5e85ab8b49962ed0b5c`; this execution log remains unstaged for the controller-owned evidence commit.
- Initial unrelated change: `docs/plans/fast/apollo-repair-retention-master/ledger.md`; preserved, not staged in the product commit.

### Task status and actual changed files

All product paths below are relative to `src/`; no product files outside the approved whitelist were changed.

| Requirement | Status | File | Implementation / exercised evidence |
|---|---|---|---|
| I-1/I-2 same-decision diagnostics | IMPLEMENTED | `main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt` | Optional decision member under existing WorkerDiagnostics; bounded candidate traces, versions/time/hash, references, total/truncated. |
| I-1/I-2 sole evaluator | IMPLEMENTED | `main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | Existing name/ORCID/institution/title/specialty branches populate trace while executing. Decision.trace defaults NULL for compatibility; full business candidate set precedes trace projection. Stable sorted serialization for input hash. |
| I-1 same persistence boundary | IMPLEMENTED | `main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | finish copies trace into diagnostics.decision before constructing existing VerificationResult and invoking CommitService; no independent diagnostic SQL write. |
| I-3 saved-only detail | IMPLEMENTED | `main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | Returns bounded saved RECORDED trace, NULL for absent/nonrecorded trace; never synthesizes a historical trace. |
| I-2/I-3 frozen comparison | IMPLEMENTED | `main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt` | Explicit frozen-rules CLI, required target rule/alias 2, old/fixed manifest hash checks, isolated fixed-clock replay of frozen facts, bounded recomputed traces, separate rule/gate/protection columns, report digest, existing secure immutable writer. Normalizer added to class hashes. |
| I-1/I-2 tests | IMPLEMENTED | `test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | Current/history/professor/specialty, name miss, ORCID conflict, search incomplete, no candidates, ambiguity across 201, selected last candidate prioritized, total/truncation and frozen-time assertions. Legacy equality compares business fields separately from input-dependent trace hash. |
| I-1 runtime persistence / smoke | IMPLEMENTED | `test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | Existing real HTTP-client/MySQL runtime fixture extended through SQL saved trace, Query, freeze and isolated report; immutable input and zero offline admission delta assertions. Existing no-reuse assertion updated to retain its meaning with new decision diagnostics. |
| I-3 compatibility tests | IMPLEMENTED | `test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | RECORDED exact saved trace, missing/RECOMPUTED NULL behavior, future diagnostic fields tolerated, repository interactions remain reads. |
| I-2/I-3 export tests | IMPLEMENTED | `test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt` | Fixed-time isolated replay, old1/new2 frozen input delta, no Parser consumption, missing legacy trace, missing 422 body NOT_RECORDED, recorded protection independent of rule gain, unknown target/mode and class hash mismatch rejection, 0600 files, immutable before/manifest and reproducible report hash. |

### Commands and fresh results

Environment for every Maven invocation:

```sh
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
JAVA_TOOL_OPTIONS=-Dapi.version=1.44
```

Environment preflight (exit 0):

```sh
env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 java -version
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker info --format '{{.ServerVersion}}'
```

Observed JDK: Zulu `11.0.32.1`, build `11.0.32.1+1-LTS`; Docker server `29.8.2`. Tests explicitly required Docker and started real `mysql:8.0.36` containers; Docker absence was not treated as a skip.

Exact required command, executed freshly after implementation, with the environment above:

```sh
env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test
```

| Invocation | Result | Evidence |
|---|---|---|
| Initial implementation command | FAIL, exit 1 | 53 tests, 2 failures / 2 errors / 0 skipped. Stale legacy trace/equality assertions, invalid test run policy literal, and subprocess hash serialization order exposed by reproducibility guard. Output `artifact://160`. |
| After bounded implementation corrections | FAIL, exit 1 | 53 tests, 1 failure / 0 errors / 0 skipped. Only report digest round-trip mismatch remained: native Instant decimal timestamp changed numeric JSON representation when re-read. Output `artifact://165`. |
| Final command after timestamp correction | PASS, exit 0 | 53 tests, 0 failures / 0 errors / 0 skipped; BUILD SUCCESS, finished `2026-10-21T14:57:56+08:00`. Output `artifact://167`. |
| Maven lifecycle frontend tests | PASS within final command | 1733 tests / 326 suites, 1733 passed / 0 failed / 0 skipped / 0 cancelled; node-check-app and node-check-task-modal-runtime completed. |
| `git diff --check` | PASS, exit 0 | Executed after final source change and before staging. |

Final Surefire evidence, not historical reports:

| Test class | Tests | Failures / errors / skipped | XML modification time (+0800) |
|---|---:|---|---|
| DiscoveryVerificationRulesTest | 19 | 0 / 0 / 0 | 2026-10-21T14:57:51 |
| DiscoveryVerificationWorkerTest | 15 | 0 / 0 / 0 | 2026-10-21T14:57:50 |
| DiscoveryVerificationQueryServiceTest | 12 | 0 / 0 / 0 | 2026-10-21T14:56:35 |
| DiscoveryVerificationReplayToolTest | 7 | 0 / 0 / 0 | 2026-10-21T14:56:35 |

Files: `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.<TestClass>.xml`. XML suite headers and `stat -f '%Sm %N' -t '%Y-%m-%dT%H:%M:%S%z'` were read after the final invocation.

### Real changed-path smoke / invariant evidence

The final required command actually executed the existing test `runtime version two registration worker SQL gate query smoke and frozen legacy runs`, extended in the authorized WorkerTest. It uses the existing MockRestServiceServer-backed real provider clients and isolated MySQL fixture, not a substitute evaluator or a production mutation.

Observed runtime receipt (`artifact://167`, lines 938–939):

```text
DECISION_AUDIT_SMOKE HTTP->Rules->Worker/SQL->Query->freeze->isolated-fixed-time-report admissionDelta=0 immutable=true
IDENTITY_V2_SMOKE run=2001 workerRule=2 alias=2 sql=APOLLO_CLEAR gate=eligible projection=PROJECTED admission=0 requests=5
```

The `admission=0` in the existing smoke marker is the persisted admission revision value, not an admission count. The test separately asserted `autoCount() == 1` before and after offline export, a non-null admission revision, PROJECTED state, unchanged completed SQL item, immutable before.json/manifest hashes, RECORDED trace in SQL/detail, RECOMPUTED new report trace, and gateResult NOT_EVALUATED. Existing policy evaluation supplies the real runtime gate check; the offline report never claims to invoke AdmissionGate.

- I-1 evidence: rule tests assert short-circuit NOT_EVALUATED versus actual NO_MATCH/CONFLICT; worker persists the same decision trace with the existing final result; Query preserves the stored trace rather than rerunning it for display.
- I-2 evidence: 201 same-name supported candidates remain ambiguous; business list retains 201, report trace has total=201/truncated=true/200 entries. A sole selected candidate at original index 200 is placed first in trace. Export tests verify report digest after read-back and secure 0600 output under 0700 directory.
- I-3 evidence: old1/new2 rule outcome change is separate from stored outcome and recorded protection. RND_OUT_OF_SCOPE, IDENTITY_CHANGED and REVIEW_PROTECTED fixture rows remain RECORDED_PROTECTION/currentCommitProtection NOT_EVALUATED, gateResult NOT_EVALUATED. Absent historical trace remains NULL; missing 422 error response remains NOT_RECORDED; offline businessWrites/admissionWrites are 0.

### Downstream interfaces and boundaries

- Existing `VerificationWorkerEvidence.diagnostics` now optionally contains `decision: VerificationDecisionTrace`; no new evidence root, DB column, ES field, or frontend contract.
- Existing `VerificationDecision` business fields are unchanged; optional `trace` defaults NULL. Existing Gate/Commit services are not modified.
- Runtime trace origin is RECORDED; isolated frozen-rule traces are explicitly RECOMPUTED with execution rule/alias versions and fixed timestamp.
- New CLI: `evaluate --mode frozen-rules --target-rule-version 2 --target-alias-version 2` plus existing manifest, manifest-sha256, old-classpath, fixed-classpath arguments. Missing/unknown targets fail closed; absent mode keeps legacy parser comparison.
- New report separates oldStoredOutcome / oldRecomputedOutcome / newRecomputedOutcome; matched identity, reason delta, coverage, HTTP/result/body retention, gate and recorded/current protection are separate fields. Layer is RULE_REEVALUATION, not admission.
- Manifest class hash coverage now includes DiscoveryInstitutionNormalizer alongside Rules and Parser. Frozen comparison rejects supplied classes that do not match the manifest; it does not rewrite old manifests or before files.

### Deviations, limitations and pending human acceptance

- No plan amendments. No later child plan was opened or implemented. No unrelated refactors, production calls/writes, deployment, or history rewriting.
- No failing baseline test was rerun merely to confirm an observation; implementation failures above were followed by bounded, in-whitelist corrections and a fresh full required command.
- No live Apollo or production cohort replay occurred. Synthetic fixtures exercise missing-body semantics; no claim is made that historical 264 errors have recovered bodies or identical causes.
- The smoke uses real isolated SQL/provider-client runtime paths with mocked HTTP responses; no external Apollo request was made. It uses manifest-bound current classes implementing frozen versions, not an archived production WAR. Actual historical binary/production-cohort comparison remains unperformed.
- Existing sample retention store, sample directory, sampling limits and seven-day cleanup were not modified. Production sample hash recheck was not performed; no production sample-read authority was exercised in this child.
- No extra standalone WAR package or full backend suite was required/run. Broader O-04-01 remains observation-only.
- Human acceptance A-1/A-2/A-3 remains PENDING; this report is execution evidence, not independent PASS.

### Freshness and next action

- Plan identity helper ran at start and after final source edits: same canonical path and SHA-256.
- Worktree identity helper ran at start, before staging and before commit with expected root/branch/git-dir guards.
- Product commit was confirmed as target HEAD and reachable from `refs/heads/fast/apollo-repair-retention-master` (`git merge-base --is-ancestor HEAD refs/heads/fast/apollo-repair-retention-master`, exit 0).
- Required command ran in this invocation after final implementation state; historical outputs are retained only as failure history.
- Product index/source cleanliness is checked at handoff; controller-owned ledger and this execution evidence are intentionally outside the product commit.
- Remaining blocker: none identified by execution checks.
- Next action: independent child05 verification by controller / verify-p. Do not interpret READY_FOR_VERIFICATION as human acceptance or independent verification PASS.
