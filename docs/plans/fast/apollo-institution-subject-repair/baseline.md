# Fast-P baseline — apollo-institution-subject-repair

- Master base: fcd6719ecd03645284c3df0f8a6b95bf4686ba95
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair
- Branch: fast/apollo-institution-subject-repair
- Seed commit: b3b3a71a865337c15c556c21225c0eb9a2300b66 (docs/plans only; product tree identical to master base)
- Master plan sha256: 49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397
- Child 02 plan sha256: 3d746ca5fc357fbf3887af627e967c5ce6f82d3c20c5f3070b029ef109125f67
- Ordered children: 01-institution-subject -> 02-country-supplement
- Toolchain: Zulu JDK 11.0.15, Maven /opt/homebrew/bin/mvn, Docker 29.8.2.
- Uncommitted main-worktree changes were not copied; only the contract plans and their docs/plans audit attachments were seeded.
- Predecessor evidence: docs/plans/fast/apollo-assist-repair-master ledger is READY_FOR_HUMAN_REVIEW; child 01/02 terminal LIGHT_PASS_WITH_NOTES, zero repairs; code head fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0.
- Baseline focused command omits DiscoveryInstitutionNormalizerTest because that class does not exist before child 01 implementation; the post-implementation required command includes it.

## Baseline commands — b3b3a71

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`: exit 0; 55 JVM tests, 0 failures/errors/skips; 1,724 JS tests passed; BUILD SUCCESS in 2:52.
- `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test`: exit 0; 43 JVM tests, 0 failures/errors/skips; actual isolated Docker MySQL; BUILD SUCCESS in 4:28.
- Logs: target/baseline-focused.log, target/baseline-it.log (gitignored build output).
- Predecessor manual acceptance is PENDING (`docs/plans/review/apollo-assist-repair-master/ledger.md`), while machine verification is PASS. The child plan's T-0 requires recording that status; it does not make manual sign-off a start gate.

