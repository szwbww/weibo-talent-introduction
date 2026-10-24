# Child 07 — Fix log

## Epoch 2 — Round 1/3
- Findings: F-07-E2-01
- Before: 93e3cda5e661a98fe045bbc100ff82c769b1f159
- Fix commit: f37ff178554cb19b3264f81ed4deac7a6b43bd8c
- Authorized files changed:
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`
- Commands:
  - All commands ran in `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`, after the final product/test edit.
  - `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true` → exit 0; 37 JVM tests, failures/errors/skips 0; 05:33, finished 2026-10-25T05:55:37+08:00; output `artifact://1162`.
  - `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT -DmysqlIt=true` → exit 0; 77 JVM tests, failures/errors/skips 0; 05:31, finished 2026-10-25T06:01:12+08:00; output `artifact://1164`.
  - `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckAcademicEvidenceServiceTest,OpenAlexDataSourceTest -DmysqlIt=true` → exit 0; 98 JVM tests, failures/errors/skips 0; 02:49, finished 2026-10-25T06:04:07+08:00; output `artifact://1167`.
  - Each Maven test command separately executed 1827 JS cases, all passed with failures/skips 0, and both bound syntax checks (`node-check-app`, `node-check-task-modal-runtime`). These are repeated suites, not 5481 distinct cases. Required disposable real MySQL ITs executed without skips.
  - `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/child07-fixer-epoch2-round1-classpath.txt -DincludeScope=test` → exit 0, 1.048 seconds.
  - `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.40 -cp <target/test-classes:target/classes:contents-of-/tmp/child07-fixer-epoch2-round1-classpath.txt> com.weibo.talentintroduction.discovery.service.RecheckPipelineRuntimeLauncher` → exit 0, 41.604 seconds. Fresh independent standalone subprocess outside JUnit, transparently reusing the reviewed committed launcher plus the authorized regression added to its shared pipeline helper.
  - Actual changed-path receipt: `CHILD07_LEGACY PASS realWorkerSQL=true rule3=true inputContextAbsent=true evidenceAbsent=true outputContextAbsent=true executionClock=2026-10-20T00:00:00Z liveQualification=NOT_MET frozenQualification=MET crossBirthdayOutcomesDiffer=true explicitOrdinaryFrozen=true`.
  - Pipeline receipt: `CHILD07_RUNTIME PASS actualRegistrationWorkerRulesGateCommitTrace=true realMySQL=true ordinaryPaper=DIRECT_CLEAR eightSteps=true AUDIT_businessWrites=0 REUSE_HTTP=0 originalFetchedAt=true frozenParity=true freeDetailHTTP=0 lateDiagnostic=once academicCancelWire=1 cancelled429Failures=0 cancelCommitBothOrders=true admittedProjectionRetryProviderHTTP=0 projectionAttempts=3 archiveFirstSHA=unchanged`.
  - Academic receipts: `CHILD07_ACADEMIC cancelDuringWait=false wire=1 cancelledRetryWire=0 ordinaryWire=3 requestKind=HISTORY_ENRICHMENT` and `CHILD07_ACADEMIC cancelDuringWait=true wire=1 cancelledRetryWire=0 ordinaryWire=3 requestKind=HISTORY_ENRICHMENT`.
- Result: FIXED
- Notes: Plan step 4 bound correction only: removed ordinary legacy context synthesis; retained explicit ordinary snapshot and recheck-spec freezing, no Rules/version changes, old-input backfill or re-signing. Regression observes actual Worker/SQL absent input/evidence and absent persisted context, then compares real Rules with that absent evaluation time versus explicit live/frozen instants using identical doctorate/birthdate facts across the fortieth birthday (1986-10-19); it does not claim ORCID supplied those birthday facts. Explicit ordinary and recheck timestamps remain asserted, as does existing frozen outcome parity. Existing protection/cancellation/projection tests remain. Runtime uses disposable MySQL and fixture/loopback provider and ES boundaries, not live providers or real Elasticsearch; expected fixture projection HTTP 500 is part of successful projection-only retry proof. No production data, provider costs, deployment, full application boot, human acceptance, whole-suite verification, later-child work, or full review claimed. Product commit contains only the two authorized files; historical execution/verification logs unchanged and this evidence log excluded from the commit.

### Round 1 terminal closure
- Independent fresh Recheck07Reverify1 LIGHT_PASS/COMPLETE_CHILD; F-07-E2-01 CLOSED, none OPEN.
- Final code headf37ff178554cb19b3264f81ed4deac7a6b43bd8c; actual repair count1 for epoch2, no second repair dispatch.
