# Child 04 — Verification log

## Light Verification: LIGHT_FAIL
Child: 04 — docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md
Boundary: 8024e30cc57c4188d829761f59444af3061edf51..043c843774fc3c4388d01d2a3eb5114b549bb92d
Verifier: ApolloVerify04
Timestamp: 2026-10-21T06:21:47Z
Execution epoch: 1; verification attempt: 1; child fix_round at dispatch: 0.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary `git diff <base> <head> -- src/main src/test` shows the nine files in plan lines 58–66 (full scoped diff: artifact://135). The boundary additionally contains prior child03/controller workflow artifacts, not additional product/test changes. HEAD is the designated head and base is its ancestor. |
| Plan and invariants | FAIL | I-1: Registration settings freeze rule2/alias2; Worker lines 207–221 uses supported integral run rule only for absent evidence, missing setting falls back to1, existing payload stays frozen; Rules accepts0..2 and version reaches both Worker filters. I-2: Normalizer lines 280–315 keeps versioned sourced TUM/Geneva aliases and explicit-ID protection; Rules attributes the actual alias. I-3: Rules lines 234–252 requires precise title plus positive headline prefix and rejects negative qualifiers; email identity route remains institution-only. RulesTest lines 35–84 covers separate/combined effects, one person, original title, UNKNOWN qualification, ambiguity/ORCID/manual protection and negative headlines. I-4/integration evidence is incomplete because the new runtime smoke fails at WorkerTest:54 before Gate/Query/admission assertions and the frozen-version matrix. F-04-01 identifies the authorized fixture correction. Human/production acceptance remains unperformed. |
| Required commands | FAIL | Fresh exact required command below ran once after inspection for independent gate-3 suite/freshness evidence, not as a root-cause confirmation probe. Exit1; 70 tests, 1 failure, 0 errors, 0 skipped; completed 2026-10-21T14:21:40+08:00, 04:08 elapsed (artifact://138; full output artifact://136). Same known failure as execution.md: WorkerTest:54 expected PROJECTED, actual NOT_REQUIRED. All six fresh Surefire XML files have mtimes 06:20:15–06:21:40Z; suite counts respectively Normalizer9/0/0/0, Rules18/0/0/0, Worker15/1/0/0, Gate8/0/0/0, Query11/0/0/0, ApolloMatchingV2 9/0/0/0 (tests/failures/errors/skips). No environment skip or new failure. |
| Downstream interfaces | PASS | Existing settings/evidence structures retained; ruleVersion=2 and institutionAliasVersion=2 are exposed without new SQL/ES fields. `matchReason` adds optional final ruleVersion default0; Worker passes frozen versions at surname/detail filters; Gate:66–69 and Query:141–142 consume persisted payload version. Existing Commit identity/fact/manual/CAS checks and ENFORCE-only attachAdmission at Commit:94–97 are unchanged. No direct admission/ES bypass introduced. Normative E-6/E-7 read. |

Required command (repository root; brief environment):
```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
export JAVA_TOOL_OPTIONS=-Dapi.version=1.44
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test
```

### AUTO_FIX
- F-04-01 — Governing requirement: child04 I-4 and acceptance integration newrun→Worker→SQL→Gate→Query, with required command failures0/errors0/skips0 (plan lines 52, 76, 83–86). `DiscoveryVerificationWorkerTest.kt:33` constructs Registration properties without mode; `DiscoveryVerificationProperties.kt:16` defaults to AUDIT. The smoke nevertheless demands PROJECTED/admission at lines54–60. Commit correctly attaches admission only for ENFORCE policyVersion (`DiscoveryVerificationCommitService.kt:94–97`). Smallest uniquely plan-determined authorized correction: explicitly set `mode = VerificationMode.ENFORCE` in that registration fixture constructor, keeping projection/admission/Gate/Query/frozen-version assertions and production AUDIT behavior unchanged. Only the already authorized WorkerTest file is needed. Then execute the exact required command and obtain fresh independent light verification; later assertions are not yet proven. No inline fix applied. Implementer's exhausted local mechanical attempts do not consume the controller's child fix_round0 or require a PAUSE for this authorized correction.

### RECORD_ONLY
- O-04-01 — execution.md:102 reports out-of-whitelist `DiscoveryVerificationIntegrationTest` expectations for new payload ruleVersion1. That broader suite was not run or investigated here; record for controller's broader regression boundary, not an authorization to edit that file or expand this light review.

### Required Action
- AUTO_FIX

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04 — docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md
Boundary: 8024e30cc57c4188d829761f59444af3061edf51..835bfae52e32482313df2f76f99572f578aeb468
Verifier: ApolloReverify04R1
Timestamp: 2026-10-21T06:33:31Z
Execution epoch: 1; verification attempt: 2; repair round: 1.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff 8024e30cc57c4188d829761f59444af3061edf51..835bfae52e32482313df2f76f99572f578aeb468 -- src/main src/test` lists exactly the nine authorized files at plan:58–66 (artifact://148). Other boundary changes are prior child03/controller workflow artifacts. HEAD equals the designated head, base is its ancestor, and `git diff --name-only <head> -- src/main src/test` is empty. Repair changes only WorkerTest registration fixture to explicit ENFORCE, as authorized by F-04-01. |
| Plan and invariants | PASS | I-1: Registration:64–68 freezes rule2/alias2; Worker:199–217 consumes integral supported run rule only for absent payload, retains old-run fallback1 and existing frozen versions; version reaches surname/detail filters at Worker:321/369. I-2: Normalizer:280–312 preserves versioned sourced TUM/Geneva entries and ID protection; Rules:114–125 reports actual alias. I-3: Rules:233–251 requires exact Assistenzprofessor and positive Assistant Professor prefix without negative qualifiers; name/unique-person/ORCID checks remain at Rules:95–112 and email identity is not expanded. RulesTest:35–84 covers separate/combined one-person effects, raw title, UNKNOWN qualification/date, negative headlines, ambiguity and protection. I-4: F-04-01 CLOSED — WorkerTest:33 now selects ENFORCE, leaving production AUDIT and assertions unchanged; freshly passing MySQL smoke executes Registration→Worker→SQL→Gate→Commit projection→Query and legacy/frozen-version matrix (WorkerTest:21–103). E-4's two RND_OUT_OF_SCOPE and one IDENTITY_CHANGED remain protected baseline differences, not new gains; no production replay/admission gain or human acceptance is claimed. |
| Required commands | PASS | Exact required Maven command below ran freshly once after inspection, exit0; 70 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 04:03 elapsed, completed 2026-10-21T14:33:22+08:00 (artifact://150:1550,3959–3962). Six fresh XML mtimes are 06:31:46–06:33:15Z, after command start 06:29:17Z. Counts (tests/failures/errors/skips): Normalizer9/0/0/0, Rules18/0/0/0, Worker15/0/0/0, Gate8/0/0/0, Query11/0/0/0, ApolloMatchingV2 9/0/0/0. Baseline attempt1's sole Worker expected PROJECTED/actual NOT_REQUIRED failure is resolved; no new failures or skips. |
| Downstream interfaces | PASS | Existing settings/evidence JSON structures retained with ruleVersion2/aliasVersion2; no SQL/ES fields added. `matchReason` has optional final ruleVersion default0; frozen Worker calls pass it. Gate:65–69 and Query:140–142 consume persisted payload version. Commit:52–97 retains lease/generation, identity/fact, manual/contact/CAS and policy/Gate protections and ENFORCE-only admission attach; no direct admission/ES bypass introduced. Normative E-6/E-7 read. |

Required command (repository root; brief environment):
```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
export JAVA_TOOL_OPTIONS=-Dapi.version=1.44
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test
```

Fresh runtime receipt (artifact://150:449–450,700–701): Docker available via brief socket, real disposable MySQL 8.0.36 and Flyway through v158; `IDENTITY_V2_SMOKE run=2001 workerRule=2 alias=2 sql=APOLLO_CLEAR gate=eligible projection=PROJECTED admission=0 requests=5`. The passing Worker test additionally executes old-run missing-setting fallback1, frozen payload0/1/2 against run setting2, and unsupported3→ERROR without Apollo requests. This is test-environment integration evidence, not production/human acceptance.

### AUTO_FIX
- N/A — F-04-01 is closed by repair1 and fresh passing end-to-end/matrix evidence; no new four-gate violation.

### RECORD_ONLY
- O-04-01 — Preserved unchanged: execution.md:102 reports out-of-whitelist `DiscoveryVerificationIntegrationTest` expectations for new payload ruleVersion1. That broader suite was not run or investigated here; controller's broader regression boundary only, not authority to edit it or expand this light review.

### Required Action
- COMPLETE_CHILD
