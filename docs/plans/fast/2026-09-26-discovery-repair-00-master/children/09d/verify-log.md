## Light Verification: LIGHT_FAIL
Child: 09d — `docs/plans/2026-09-26/discovery-repair-09d-scope-admission.md` (SHA-256 `ef0f7b067bfe0f46b5b29bd748031c858686c4273158458021d95a5a47f619c9`)
Boundary: `5724b4ff34619acf8abd8ee68c724ea25669742c..3f27b1bab8be159339e237eae8522df2c006b448`
Verifier: ScopeAdmissionVerifier (independent of ScopeAdmissionImplementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact product boundary changes four production and four test Kotlin files, all among brief's nine; `OperatorStatusWriteSeamGuardTest.kt` unchanged. No sender/config/migration file changed; bounded `git diff --check` clean. |
| Plan and invariants | FAIL | Current classification is recomputed by `CandidateEligibilityService.kt:54-65`, shared by the direct/queued admission seam `ExpertDiscoveryService.kt:1071-1091,1395-1423`; RAW-first job enqueue and stored classification at `:1077-1084,1400-1412,1948-1950`. Discovery revalidation reads RAW, evaluates current eligibility and reconciles before worker completion (`ExpertRevalidationService.kt:325-350`; `ExpertDiscoveryService.kt:2844-2864,2511-2535`), with conditional RAW/candidate CAS and candidate-only delete (`ExpertIndexWriterService.kt:695-709,744-787`); existing candidate receives no full overwrite. However plan §实现方案 6 explicitly requires test-generated `09d.json` with job terminal/retry as well as layer counts, RAW reason, CAS outcomes and retained fields: the fresh command's actual `target/discovery-plan-acceptance/09d.json:1-26` has no `jobTransitions` or initial RAW-first admission, while `ExpertDiscoveryServiceTest.kt:5490-5500` writes transitions only to separate `09d-worker.json` and `ExpertIndexWriterServiceTest.kt:1138-1140` overwrites `09d.json` with its narrower snapshot. The earlier combined artifact was not reproducible by the required command. |
| Required commands | PASS | Fresh `env JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest,CandidateEligibilityServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,OperatorStatusWriteSeamGuardTest,CandidateEligibilityServiceEnhancedTest,ExpertRevalidationServiceTest,ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertClassificationVersionGateGuardTest`: exit 0, 270 JVM tests, 0 failures/errors/skips, Maven-bound Node 1,194 pass/0 fail/skipped, BUILD SUCCESS (`artifact://909`). Recorded baseline `artifact://866`: exit 0, 264 JVM tests, 0 failures/errors/skips, Node 1,194 pass. No whole-system suite or clean package run. |
| Downstream interfaces | FAIL | 09b `researchFieldIds`, 09c current classification and the existing job outcome vocabulary are consumed without contract changes; however the final handoff's required isolated `09d.json` lacks the worker retry/success transitions and initial RAW-first admission after fresh regeneration. The final JDK11 clean package remains deferred to whole-plan/human review, not this child. |

### AUTO_FIX
- F-01 — Plan §实现方案 6 and brief line 26 require the **test-generated** `target/discovery-plan-acceptance/09d.json` to contain before/after layers, RAW reason, job terminal/retry, candidate CAS outcomes and retained-field snapshots. Fresh `artifact://909` regenerates a file with only replica evidence (`09d.json:1-26`); job evidence is in `09d-worker.json`, and initial admission only in assertions. Smallest authorized correction: update only the listed focused test files so one reproducibly generated `09d.json` includes the asserted initial-admission and retry/success results alongside the existing replica evidence, without depending on test order or post-test manual assembly; rerun the exact focused command and inspect the generated artifact.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX
## Epoch 1 — Reverification after round 1

## Light Verification: LIGHT_PASS
Child: 09d — `docs/plans/2026-09-26/discovery-repair-09d-scope-admission.md` (SHA-256 `ef0f7b067bfe0f46b5b29bd748031c858686c4273158458021d95a5a47f619c9`)
Boundary: `5724b4ff34619acf8abd8ee68c724ea25669742c..4ad9e79b034798ee78f12c3285faf5882991b3bc` (implementation `3f27b1bab8be159339e237eae8522df2c006b448`; round-1 fix `4ad9e79b034798ee78f12c3285faf5882991b3bc`)
Verifier: AdmissionEvidenceReVerifier (independent of ScopeAdmissionImplementer, AdmissionEvidenceFixer and ScopeAdmissionVerifier)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Product diff contains precisely four approved production and four approved test files; the guard test is unchanged. Round-1 fix changes only `ExpertDiscoveryServiceTest.kt` and `ExpertIndexWriterServiceTest.kt`. No sender/config/migration product changes; bounded `git diff --check` clean; retained branch HEAD equals fix commit. Ancillary child/ledger evidence changes are separate from the product boundary. |
| Plan and invariants | PASS | I-1 current discovery classification and distinct UNKNOWN/non-target reasons in `CandidateEligibilityService.kt:54-65`; both discovery admission branches and RAW-first enqueue/counts in `ExpertDiscoveryService.kt:1071-1091,1395-1423`, with same-input index classification at `:1933-1951` (I-2). Targeted real-RAW revalidation and pre-completion worker outcome in `ExpertRevalidationService.kt:325-350` and `ExpertDiscoveryService.kt:2494-2536,2844-2863` (I-3). RAW snapshot/identity checks, partial RAW CAS, candidate CAS DELETE/404 idempotence and retained candidate fields in `ExpertIndexWriterService.kt:695-709,740-787` (I-4). No hidden sending/config change (I-5). Fresh test-generated `target/discovery-plan-acceptance/09d.json:1-60` now combines initial RAW 0→1, CANDIDATE 0, REJECTED/RND_SCOPE_UNCONFIRMED, queued job 1; worker RetryableError pending 1 then Success promoted 1; replica CANDIDATE 1→0, CAS/404/409/500/identity outcomes, and PAUSED/retained operator field snapshots. One isolated test writes it; standalone tests no longer overwrite it, independent of test order. |
| Required commands | PASS | Fresh exact ten-class JDK11 Maven command: `env JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest,CandidateEligibilityServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,OperatorStatusWriteSeamGuardTest,CandidateEligibilityServiceEnhancedTest,ExpertRevalidationServiceTest,ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertClassificationVersionGateGuardTest` exited 0: JVM 271 tests, zero failures/errors/skips; Maven-bound Node 1,194 passed, zero failed/skipped; BUILD SUCCESS (`artifact://936`). Acceptance JSON mtime advanced from 17:36:38 to 17:42:30 +08:00 during this run and content inspected afterward. Previous baseline 264 and first verification 270 were not substituted. No whole suite/clean package/formatter/linter run. |
| Downstream interfaces | PASS | 09b researchFieldIds, 09c current classification, real ES `_id` and existing job outcome vocabulary remain on the same bounded path; generated combined evidence is available to final handoff. Full JDK11 clean package is deferred to independent whole-plan/human review per brief, not claimed here. |

### AUTO_FIX
- F-01 CLOSED by round-1 fix `4ad9e79b034798ee78f12c3285faf5882991b3bc`: required exact focused command regenerated a single order-independent `09d.json` containing initial admission, retry→success job evidence, replica CAS outcomes, layer counts, RAW reasons and preserved-field snapshots. No further auto-fix requested.

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD