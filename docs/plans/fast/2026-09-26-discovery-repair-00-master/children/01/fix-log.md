## Epoch 1 — Round 1/3
- Findings: F-1
- Before: 56f153df3f42d9ab5149b986c621ccf784184539
- Fix commit: 29db24e66b8cb98eceb782812da34d1acbd6da06
- Authorized files changed: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
- Commands: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest` -> exit 0; 229 tests, 0 failures, 0 errors, 0 skipped.
- Result: FIXED
- Notes: Restored `stats.refreshGlobalCounts()` immediately before the ORCID per-author global-limit check. N/A
