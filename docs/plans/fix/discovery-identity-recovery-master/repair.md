# Repair Plan: discovery-identity-recovery-master

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`
Baseline SHA256: `f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238`
Verification report: current-task `verify-p` rerun, 2026-10-16 — FAIL / PROGRESSING
Implementation boundary: product `86e03e4..a585fd7`; cumulative `9d353934..a585fd7`
Current repair base: `a585fd7`

This revision supersedes the prior repair draft. Its five production repairs are present in
`a585fd7`; this plan is limited to the two remaining mandatory P2 test-contract failures.

## Objective

Add discriminating regressions proving the repaired behavior through required real seams and
actual outreach entries. Do not change production behavior or repair the unrelated Initial FK.

## Findings in Scope

| Finding | Severity | Requirement | Evidenced root cause |
|---|---|---|---|
| V-31 | P2 mandatory, persistent | Approved R-6 / E01 real-entry contract | GT leaves reserve→admission-revoke explicitly unasserted; E01 Manual Initial/Expert call guard directly; existing three entry suites mock the guard. |
| V-32 | P2 mandatory, new | Approved R-1—R-5 discriminating-regression contract | Product code changed, but the required PT/RT/EST/SELT/CCT/RECT/REVT discriminator coverage was not added; QT/GT changes alone do not prove R-1—R-5. |

## Findings Excluded

| Finding / item | Reason |
|---|---|
| V-3, V-5, V-12, V-13, V-29, V-30 | Resolved production behavior at `a585fd7`; this plan only adds the omitted proof. |
| B-1 | Still requires independent scale evidence; no test-only repair can provide it. |
| B-2 / E01 | E01 was executed. `b2HealthyInitial` is blocked by the existing FK; it is evidence, not this repair's product scope. |
| `InitialOutreachService` `attemptId = 0L` / `fk_mail_record_send_attempt` | NEW_SCOPE_REQUIRED. Do not alter caller, helper, schema, fixtures, or manufacture ID 0. |
| Optional P2 / cleanup / frontend / policy changes | No mandatory finding or approval. |

## Unchanged Contract

- No production Kotlin, schema, migration, ES mapping, sender, SMTP, configuration, or frontend change.
- No new test file. No mock may replace the decision, repository transaction, recipient selection,
  guard, or business persistence that the specific regression claims to prove.
- Preserve existing identity, admission, address, suppression, UNKNOWN/SENDING, SAFE_RETRY, and
  manual-decision contracts. Do not weaken assertions or turn a negative test into an expected pass.
- Initial's existing FK remains a blocked external precondition. Its test may record the exact
  failure chain only; it must not be "fixed" here.

## Authorized Files

Only these existing tests may change.

| File | Purpose |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt` | V-32 R-1 service revision/readback discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt` | V-32 R-1 real repository/transaction discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | V-32 R-2 strict `_mget` response discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt` | V-32 R-2/R-3 query status and unavailable-read discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt` | V-32 R-2 strict read → recipient exclusion discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | V-32 R-2 unavailable relation/count discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt` | V-32 R-4 partial-target recovery discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | V-32 R-5 current-policy-before-old-admission discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt` | V-31 real MySQL guard/E01 evidence |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | V-31 actual Initial entry discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | V-31 actual Manual Initial entry discriminator |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | V-31 actual Manual Expert entry discriminator |

## Repair Tasks

### R-1: Prove root revision binds the verified group

- Resolves: V-32 / V-3 evidence gap.
- Files: `DiscoveryPersonServiceTest.kt`, `DiscoveryPersonRepositoryTest.kt`.
- Regression: Interleave a valid same-identity member addition after A reads the root but before
  A completes full-group verification. If that new member becomes invalid, A returns STALE/zero
  write; if it remains valid, the compatible control succeeds. Use real service plus real MySQL
  repository/transaction for the decisive path.
- Must not change: UUID/key uniqueness, locking, schema, or production retry rules.

### R-2: Prove strict person reads do not become absence

- Resolves: V-32 / V-5 and V-13 evidence gap.
- Files: `ExpertSearchServiceTest.kt`, `DiscoveryPersonQueryServiceTest.kt`,
  `DiscoveryPersonRecipientServiceTest.kt`, `ExpertContactManagementControllerTest.kt`.
- Regression: Cover item `error`, omitted requested ID, duplicate/wrong ID, and `found=true`
  without `_source`; each must surface unavailable and never yield a recipient or fabricated
  person/contact count. `found=false` is the positive legal-absence control. Include the
  cross-campaign same-person count-one control through the actual controller/query seam.
- Must not change: non-person wide reads, legal empty results, pagination, or DTO/error contract.

### R-3: Prove ES-only invalid email is not admitted

- Resolves: V-32 / V-12 evidence gap.
- Files: `DiscoveryPersonQueryServiceTest.kt`.
- Regression: Same non-stale identity, AUTO_PASSED and no contact: ES `EMAIL_INVALID` yields
  non-ADMITTED/UNKNOWN. Valid email and an explicit invalid contact remain controls.
- Must not change: status enums, UI, suppression, or manual-decision behavior.

### R-4: Prove a partial target-proof recovery resumes safely

- Resolves: V-32 / V-29 evidence gap.
- Files: `DiscoveryIdentityRecoveryServiceTest.kt`.
- Regression: RAW completes target proof, Candidate fails, then a retry completes only Candidate
  and closes registration/admission. Pure fully named 01, third-proof conflict, and changed-source
  controls remain blocked/no-write.
- Must not change: CAS, source binding, missing-layer behavior, or recovery routes outside this case.

### R-5: Prove current policy outranks old automatic admission

- Resolves: V-32 / V-30 evidence gap.
- Files: `ExpertRevalidationServiceTest.kt`.
- Regression: Same identity with old AUTO_PASSED but current hard credential INVALID must reconcile
  to the current review result rather than project an approved candidate. Valid credentials,
  soft-only missing data, and valid manual/legacy exceptions are controls.
- Must not change: admission policy, classifier, CAS, or human decision semantics.

### R-6: Prove all three actual outreach entries observe current admission

- Resolves: V-31.
- Files: `DiscoveryPersonOutreachGuardServiceTest.kt`, `InitialOutreachServiceTest.kt`,
  `ManualInitialOutreachServiceTest.kt`, `ManualExpertMailServiceTest.kt`.
- Regression: reserve under approval, create the entry's real NEW contact/PREPARED attempt,
  revoke current approval before delivery, then invoke each actual entry. Each must make zero SMTP
  calls and not reach SENT. Include an actual-entry allowed control with exactly one SMTP attempt;
  it may not be replaced by direct `claimSending` or a mocked guard.
- E01: retain the four named methods and `b2Acceptance` gate. Each method must invoke its matching
  actual service. `b2HealthyInitial` must require successful business persistence/SENT and therefore
  truthfully fail on the known FK; record one SMTP call and durable SENDING as the failure evidence.
  It is not an expected/pass test. Manual Initial/Expert must be real entry evidence, not guard seams.
- Prohibited: fixing the FK, creating ID 0, mocking transaction success, broad exception catches,
  or reducing SMTP/persistence assertions.

## Verification Commands

Run serially in this worktree. C01—C19, C21, and C22 must pass; C20 remains the approved old-JDK
tzdb N/A exception. E01 must execute and be classified exactly.

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test # C01
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test # C02
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test # C03
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test # C04
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test # C05
python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py' # C06
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test # C07
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test # C08
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test # C09
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test # C10
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test # C11
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test # C12
node --check src/main/resources/static/app.js # C13
node --test src/test/js/discoveryPersonEmails.test.js # C14
node --test src/test/js/*.test.js # C15
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test # C16
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test # C17
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test # C18
python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py' # C19
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package # C21
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test # C22
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Db2Acceptance=true '-Dtest=DiscoveryPersonOutreachGuardServiceTest#b2HealthyInitial+b2HealthyManualInitial+b2HealthyManualExpert+b2SamePersonCompetition' test # E01
```

## Completion Criteria

- Every V-31/V-32 regression is discriminating: it fails against the pre-`a585fd7` behavior or
  pre-repair test seam, and passes only through the intended real path.
- Only the 12 authorized test files change; no new file or production hunk exists.
- C01—C19, C21, C22 pass at the final product boundary.
- E01 runs. It may exit nonzero only because `b2HealthyInitial` truthfully exposes the exact
  pre-existing `fk_mail_record_send_attempt` chain; any other E01 failure fails the repair.
  B-1/B-2 and human acceptance remain separate gates.

## Human Approval

Execution is prohibited until explicit approval of this exact file. After approval:

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`

## Self-Review

- V-31/V-32 are the only included mandatory failures and map to six independent test-contract tasks.
- Initial FK is explicitly excluded as new scope; no test contract is relabeled as a production fix.
- Scope is twelve existing test files, no continuation or automatic additional repair.

## 修正记录

- 2026-10-16：依人类明确选择，R-1 可在根 revision 变化时进行一次有界整组复验；只在新快照稳定且全部成员有效时继续登记。原“不得改生产重试规则”的限制由 `docs/plans/2026-10-16/discovery-identity-recovery-runtime-repair.md` 取代。
- 2026-10-16：依人类明确选择，将 R-6 的同一真实入口“reservation→PREPARED→撤销→claim”验收拆分为 Initial、Manual Initial、Manual Expert 三个实际发送入口的撤销/筛除拒发与有效发送控制，以及独立真实 guard 的 reservation 后撤销、claim 拒绝；不再要求新增生产测试钩子或人工专家 PREPARED 事实。详见 `docs/plans/2026-10-16/discovery-identity-recovery-runtime-repair.md`。
