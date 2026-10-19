# Child 04 independent light verification log


# Epoch2 — Attempt1
Timestamp: 2026-10-18T21:48:19+08:00
Range: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab..a0103c2ea9284c030ad0b0b10c4177474c4f4d47
Identity: ApolloWorkerVerifierE2 — fresh isolated verifier, distinct from ApolloWorkerImplementer and ApolloWorkerImplementerE2; no inherited writer conversation.
Plan: docs/plans/2026-10-18/apollo-04-worker.md, immutable commit bb9eda7df6fde5f4eb0a5d433a1058f008d42b16, SHA-256 9b8d742b37fefd332cf9799db5f479c9b24653f5e3ce945d6b01727591e25819.
Authority: brief Epoch2 supersedes Epoch1; ledger A3/A4 record human “批准该计划并执行” at 2026-10-18T21:06:49+08:00, authorizing existing ORCID client/test durability/resume (nine product/test paths). Plan/evidence commits inside this range are authorized metadata, not product scope violations.
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Branch: fast/apollo-verification-master
Git directory: /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master
Observed HEAD: a0103c2ea9284c030ad0b0b10c4177474c4f4d47 (worktree identity helper exit 0).
Method: ONLY the four light gates; read complete current brief/plan/contracts/master, execution history/matrix and applicable audit/downstream plans. Execution.md and prior artifacts were context/baseline only, never fresh proof. No full verify-p/review-p/repair-p/fix-v, broad review, product/test edits, staging, branch changes or commits.

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04 — docs/plans/2026-10-18/apollo-04-worker.md
Boundary: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab..a0103c2ea9284c030ad0b0b10c4177474c4f4d47
Verifier: ApolloWorkerVerifierE2

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact range diff and product commit a0103c2 show nine changed product/test paths, all in the amended plan table. Other changed paths are approved plans/fast-p evidence. No migration, configuration, frontend or other product path changed. Exact product inventory below. |
| Plan and invariants | PASS | Worker.kt:48-58 finite ten-item tick, optional RegistrationRecovery, disabled/auth gates and single active tick; :65-113 independent lease heartbeat, persisted permits and atomic completion/progress; :146-200 local/direct before Apollo, cumulative union/cursor and explicit HTTP branches; :204-225 bounded deferral/fail-closed persistence. CommitService.kt:31-99 external preflight then distinct transactional bean, fixed repository locks, lease/revision/identity/fingerprint/protection checks, shared policy and strict real-row CAS confirmation, AUDIT no admission/project writes. :103-129 projection-only recovery and explicit success enums/current revision checks. FlowIT:183-403 exercises required lifecycle, failure, recovery, protection and race scenarios on real MySQL; fresh results below. |
| Required commands | PASS | Fresh JDK11 matrix exit 0: 264 reported, 263 executed, zero failures/errors, one explicitly optional legacy skip. Mandatory FlowIT 11/0/0/0 and RepositoryIT 8/0/0/0 on separate disposable MySQL8 containers. Fresh external-JVM smoke exit 0. Focused Node 70/70 and syntax check exit 0. Fresh full Node exit 1: 1694/1653/41/0, exactly the supplied pre-existing baseline; O-04-E2-01. No skipped IT is counted as passing. |
| Downstream interfaces | PASS | New OrcidEndpointCompletion is consumed by worker callback at Worker.kt:152-159 and client resume/allowlist dispatch at OrcidVerificationEvidenceClient.kt:65-119; saved endpoint results reconstruct detail reads, callbacks persist before the next HTTP. Worker evidence remains compatible with AdmissionGate.kt:22-27,55-69 (schema 1, ignores independent progress/orcidCompleted, re-evaluates evidence/search). Repository.kt:274-323 preserves run→admission→item→attempt commit order, attempt/progress atomicity and projection recovery. Existing RegistrationRecovery interface remains optional for child05. Child06 can read existing item/attempt/run fields, admissionRevision history and separate projection status without provider side effects. Shared candidate consumer ReviewService.kt:1033-1048 / ExpertIndexWriterService.kt:872-935 preserves existing candidate/application content. |

### AUTO_FIX
- N/A — no proven four-gate deviation requiring an authorized correction was found. No product edits made.

### RECORD_ONLY
- O-04-E2-01: Full Node suite freshly exits 1 with tests=1694, pass=1653, fail=41, skipped=0, matching the supplied baseline exactly. Failures include existing extraction-sandbox resetRecipientPreview/setRecipientPreviewState references and meetingConfirmationAssets rejecting the pre-existing recipient-preview-manual key. No JS changed in the reviewed product patch; outside this child/light AUTO_FIX scope. Fresh output: artifact://202 (totals lines2350-2357, failure details2361-2868).
- O-04-E2-02: DiscoveryReviewRepositoryIT remains gated by mysqlIt=true and reports one skipped class, not an executed/pass result. Its pre-existing shared-DB gate is intentionally not enabled. Mandatory disposable-MySQL FlowIT and RepositoryIT DID execute with zero skips (artifact://208:653,1081). This is not a Docker skip or missing mandatory evidence.

### Required Action
- COMPLETE_CHILD — four gates pass with the two recorded observations. Controller owns evidence commit/checkpoint and any subsequent child dispatch; no later child or whole-system verification performed here.

### Authorized product/test inventory
Exact command: `git diff --stat 98c7b78fde54f69b72f175b94b2ab0eed25d86ab..a0103c2ea9284c030ad0b0b10c4177474c4f4d47`, followed by `git diff <same-range> -- src/main src/test` and `git show --format=fuller --stat a0103c2ea9284c030ad0b0b10c4177474c4f4d47`; exit 0 (artifact://197, artifact://199 and fresh product-commit receipt).
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClient.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClientTest.kt

### Fresh command receipts
All commands below ran in this exact worktree after implementation was complete. No historical PASS reused.

1. Backend matrix (exit 0; finished 2026-10-18T21:46:17+08:00; artifact://208):

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationFlowIT,OrcidVerificationEvidenceClientTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRulesTest,QualificationEvidenceParserTest,ApolloPeopleSearchClientTest,DiscoveryReviewServiceTest,DiscoveryAdmissionPolicyTest,OperatorStatusWriteSeamGuardTest,DiscoveryReviewRepositoryIT,DiscoveryReviewAllPagesTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,BatchRecipientSelectionServiceTest`

| Class | Reported | Executed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| DiscoveryVerificationWorkerTest | 3 | 3 | 0 | 0 | 0 |
| DiscoveryVerificationCommitServiceTest | 4 | 4 | 0 | 0 | 0 |
| DiscoveryVerificationFlowIT | 11 | 11 | 0 | 0 | 0 |
| OrcidVerificationEvidenceClientTest | 6 | 6 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 8 | 8 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 6 | 6 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 7 | 7 | 0 | 0 | 0 |
| QualificationEvidenceParserTest | 5 | 5 | 0 | 0 | 0 |
| ApolloPeopleSearchClientTest | 6 | 6 | 0 | 0 | 0 |
| DiscoveryReviewServiceTest | 38 | 38 | 0 | 0 | 0 |
| DiscoveryAdmissionPolicyTest | 22 | 22 | 0 | 0 | 0 |
| OperatorStatusWriteSeamGuardTest | 1 | 1 | 0 | 0 | 0 |
| DiscoveryReviewRepositoryIT (optional gated legacy class) | 1 | 0 | 0 | 0 | 1 |
| DiscoveryReviewAllPagesTest | 19 | 19 | 0 | 0 | 0 |
| ExpertRevalidationServiceTest | 35 | 35 | 0 | 0 | 0 |
| ExpertRevalidationServiceBehaviorTest | 25 | 25 | 0 | 0 | 0 |
| ExpertIndexWriterServiceTest | 51 | 51 | 0 | 0 | 0 |
| BatchRecipientSelectionServiceTest | 16 | 16 | 0 | 0 | 0 |
| Total | 264 | 263 | 0 | 0 | 1 |

Docker resolved the required OrbStack socket; repository fixture mysql:8.0.36 container aaf55bf0fd05… used jdbc:mysql://localhost:32816/verification_ledger; FlowIT separate mysql:8.0.36 container 7bd438fde42d… used jdbc:mysql://localhost:32817/verification_flow. No shared DB enabled. Java11.0.15 is confirmed in the fresh repository IT startup. Trigger fault and ES500 stack traces are expected fixture injections, with final test failure/error counts zero.

2. Standalone external-JVM smoke (exit 0; finished 2026-10-18T21:46:45+08:00; artifact://210):

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn exec:exec -Dapi.version=1.44 -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test -Dexec.args="-Dapi.version=1.44 -classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT"`

Fresh disposable MySQL8 container 0d5e0c09f4f5… used jdbc:mysql://localhost:32819/verification_flow. Real worker/clients/transaction proxies with mock HTTP; initial ES500 then newly constructed worker repaired only projection. Exact emitted result: `RUNTIME_SMOKE auto=1 projection=PROJECTED apolloRequests=2 no-provider-replay=true candidates=1`. No real Apollo/ORCID request or mail sending.

3. Focused Node and syntax:

`node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js`

Exit 0; tests70, pass70, fail0, skipped0; subsequent syntax check exit0. Fresh bg_46 completion receipt. Named future src/test/js/discoveryVerification.test.js is absent (exact glob returned Path not found), so C-7 instruction not to run nonexistent future tests applies; no pretend PASS.

4. Full Node:

`node --test src/test/js/*.test.js`

Exit 1; tests1694, pass1653, fail41, skipped0 (artifact://202); unchanged baseline classified above, not repaired.

5. Identity and patch checks:

`python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-18/apollo-04-worker.md` — exit0, current canonical plan SHA matches approved hash, size9222 bytes.

`git show bb9eda7df6fde5f4eb0a5d433a1058f008d42b16:docs/plans/2026-10-18/apollo-04-worker.md | shasum -a 256` — exit0, immutable commit plan produces the same 9b8d742b37fefd332cf9799db5f479c9b24653f5e3ce945d6b01727591e25819 hash.

`python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-18/apollo-04-worker.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master --expect-branch fast/apollo-verification-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master` — exit0, root/branch/git-dir and requested product HEAD match (artifact://204).

`git diff --check 98c7b78fde54f69b72f175b94b2ab0eed25d86ab..a0103c2ea9284c030ad0b0b10c4177474c4f4d47` — exit0, no output.

### Explicit acceptance mapping
- I-1 / amendment: persisted local direct evidence skips providers; reliable ORCID professor skips Apollo; 429 resumes without transient increment; malformed/403/transient exhaustion retain ERROR; page/item limits retain incomplete rather than no-match. Successful broad-page union resumes failed page. Client reconstruction skips completed person/employment and rebuilds detail cursor. Acknowledgement failure issues no next request. Evidence: Worker.kt:146-221; OrcidVerificationEvidenceClientTest.kt:79-126; fresh FlowIT tests at183,239,251,300,337,361.
- I-2: evidence plus admission/revision share commit transaction; injected attach trigger rolls back both, strict persisted-row seam rejects nonexistent revision-zero fallback but accepts actual revision0; AUDIT keeps suggestions without admission/candidate change. Evidence: CommitService.kt:48-99; Repository.kt:274-315; CommitServiceTest.kt:63-75; FlowIT:231,251.
- I-3: ES500 leaves auto event1/projectionERROR, reconstruction repairs to PROJECTED without another provider call/admission event; ALREADY_PRESENT distinct, NOT_ADMITTED explicitly ERROR despite sync.ok; later HOLD preserves history and halts recovery. Evidence: CommitService.kt:103-129; CommitServiceTest.kt:49-61; FlowIT:199-229; external runtime smoke above.
- I-4 / N-1,N-2: collection SUCCESS/input_closed do not stop queued verification or create task rows; disabled worker does not claim; replaced/expired lease cannot commit; identity/fact changes stay PENDING, failed protection readERROR; HOLD/REJECTED/MANUAL_APPROVED/LEGACY_APPROVED/SBIR/contact alias/APPLICATION retain protected decisions and no candidate. Evidence: WorkerTest.kt:19-42; FlowIT:183-198,217-229,275-298,381-403. Worker/commit dependencies contain no task progress/task execution writer or mail/send service; RAW/layer use is read-only and projection uses existing create-only shared writer.
- A-1 through A-4: collection completion/retry, ES failure/recovery, HOLD before response and after admission, and AUDIT are directly exercised by those fresh FlowIT tests. Later child06 API/browser rollout checks are not claimed as executed by child04.

Only this verify-log was appended; prior history preserved. No AUTO_FIX applied or requested. Real provider credentials/account-specific caps and later frontend browser acceptance remain rollout/later-child matters, not part of this four-gate result.

### Controller artifact closing correction
The existing independent verdict above remains LIGHT_PASS_WITH_NOTES. Its action line included explanatory prose; the artifact parser requires the exact token below. This closing record restates ApolloWorkerVerifierE2's actual COMPLETE_CHILD action, without any new verification or test claim.

### Required Action
- COMPLETE_CHILD
