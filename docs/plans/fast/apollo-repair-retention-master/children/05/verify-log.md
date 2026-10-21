# Child 05 — Verification log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 05 — docs/plans/2026-10-21/apollo-repair-05-decision-audit.md
Boundary: 835bfae52e32482313df2f76f99572f578aeb468..a489c28e93e0357e26f6e5e85ab8b49962ed0b5c
Verifier: ApolloVerify05

Timestamp: 2026-10-21T07:07:24Z (2026-10-21T15:07:24+08:00); epoch 1, attempt 1.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only 835bfae52e32482313df2f76f99572f578aeb468..a489c28e93e0357e26f6e5e85ab8b49962ed0b5c` identifies the authorized Rules/Worker/QueryService/ReplayTool/VerificationDiagnostics product files and corresponding four tests at child plan:58–66. Interleaved child04 execution/fix/verification and controller ledger artifacts are excluded from product scope. Scoped diff receipt: artifact://174. HEAD equals the designated head, base is its ancestor (exit 0), and `git diff --name-only a489c28e93e0357e26f6e5e85ab8b49962ed0b5c -- src/main src/test` is empty. No schema, mapping, frontend or sample-store change. |
| Plan and invariants | PASS | I-1: Rules.kt:48–72,122–177,239–248,291–337 populate the trace in existing decision branches; unvisited steps default NOT_EVALUATED (VerificationDiagnostics.kt:40–50). Worker.kt:166–174 includes that trace in the existing committed result; QueryService.kt:115–117 returns only saved RECORDED traces. I-2: Rules.kt:138–143 evaluates the full candidate set before trace projection at :61–72; RulesTest.kt:35–98 proves step results, 201-person ambiguity, total=201/truncated=true/200 retained, and selected index200 first. Version/time/hash/reference schema is VerificationDiagnostics.kt:53–80. I-3: ReplayTool.kt:262–367 uses frozen facts without Parser calls, fixed manifest time, isolated JVMs and class-hash checks, separates stored/old/new outcomes, RULE_REEVALUATION, NOT_EVALUATED Gate/current protection, recorded protection and missing error body; QueryServiceTest.kt:22–44 covers absent/recomputed/unknown-field semantics. ReplayToolTest.kt:17–98 covers reproducibility, version rejection, immutable inputs, report hash and 0600 output; ReplayTool.kt:128–170 retains 0700 directories/0600 files. Fresh runtime smoke below closes HTTP-client→Worker/MySQL→Query→freeze→isolated fixed-time report. |
| Required commands | PASS | Fresh exact command below exited 0, BUILD SUCCESS, finished 2026-10-21T15:07:11+08:00 (artifact://177:1763,4172–4175). 53 tests / 0 failures / 0 errors / 0 skipped, matching recorded final baseline execution.md:69 (no baseline failure waiver). Fresh XML headers: Rules 19, Worker 15, QueryService 12, ReplayTool 7, each failures/errors/skipped=0. XML mtimes respectively 15:07:07,15:07:06,15:05:53,15:05:53 +0800, after verifier preflight 15:02:53 +0800. Docker was required, not skipped; real mysql:8.0.36 containers ran (artifact://177:462–466,702–706). Maven lifecycle frontend tests and syntax checks also completed within this successful command. |
| Downstream interfaces | PASS | Optional `VerificationDecision.trace = null` (Rules.kt:30–33); existing `diagnostics.decision`, not a second evidence root (VerificationDiagnostics.kt:70–80). Gate continues comparing business outcome/qualification/route/personId only (AdmissionGate.kt:65–69). New CLI is exactly `evaluate --mode frozen-rules --target-rule-version 2 --target-alias-version 2` with existing manifest/digest/classpath options; explicit versions fail closed, omitted mode preserves prior parser-only behavior (ReplayTool.kt:58–84). Normalizer joins Parser/Rules class digest (:29,100–113); old frozen versions are consumed without mutation (:345–365). Report distinguishes rule, Gate and Commit protection and declares zero offline business/admission writes (:296–320). No later child plan was opened. |

Fresh command (worktree root; artifact://177):

```sh
env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test
```

Preflight exited 0: JDK Zulu 11.0.32.1, Docker server 29.8.2. Fresh smoke receipts (artifact://177:938–939):

```text
DECISION_AUDIT_SMOKE HTTP->Rules->Worker/SQL->Query->freeze->isolated-fixed-time-report admissionDelta=0 immutable=true
IDENTITY_V2_SMOKE run=2001 workerRule=2 alias=2 sql=APOLLO_CLEAR gate=eligible projection=PROJECTED admission=0 requests=5
```

WorkerTest.kt:22–103 runs real provider HTTP clients against MockRestServiceServer-backed fixture responses and isolated MySQL, then asserts saved/detail RECORDED trace, RECOMPUTED offline trace, unchanged frozen hashes and SQL item, and `autoCount()==1` before/after export. The marker's `admission=0` is the admission revision, not a count. No live Apollo or production replay, production sample-hash recheck, or human A-1/A-2/A-3 acceptance was performed; these remain explicitly unclaimed/deferred as in execution.md:114–118. Historical 264 missing bodies are not reconstructed; fixture coverage proves NOT_RECORDED semantics, not historical cohort outcomes. Existing sample directory/limits/seven-day retention code is unchanged.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-04-01 — Preserved unchanged from child04: out-of-whitelist `DiscoveryVerificationIntegrationTest` expectations for new payload ruleVersion1 remain the controller's broader regression observation. That suite was not run or investigated here; this record grants no authority to edit it or expand child05 scope.

### Required Action
- COMPLETE_CHILD
