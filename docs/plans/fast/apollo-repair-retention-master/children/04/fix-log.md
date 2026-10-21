# Child 04 — Repair log

## Epoch 1 — Round 1/3
- Findings: F-04-01
- Before: 043c843774fc3c4388d01d2a3eb5114b549bb92d
- Fix commit: 835bfae52e32482313df2f76f99572f578aeb468
- Authorized files changed: src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt
- Commands: `export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock && export JAVA_TOOL_OPTIONS=-Dapi.version=1.44 && mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test` -> exit 0; 70 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, completed 2026-10-21T14:27:19+08:00 (artifact://143). Worker 15/0/0/0; Normalizer 9/0/0/0; Rules 18/0/0/0; Gate 8/0/0/0; Query 11/0/0/0; ApolloMatchingV2 9/0/0/0.
- Result: FIXED
- Notes: Only the Registration fixture now explicitly selects VerificationMode.ENFORCE; production AUDIT behavior and all assertions remain unchanged. Real MySQL smoke printed `IDENTITY_V2_SMOKE run=2001 workerRule=2 alias=2 sql=APOLLO_CLEAR gate=eligible projection=PROJECTED admission=0 requests=5`. The successful complete Worker smoke test also exercised downstream saved-payload/Gate/Query assertions, legacy missing-setting fallback1, frozen payload versions0/1/2 against run setting2, and unsupported version3 rejection without Apollo requests. Reports excluded from fix commit; controller owns evidence commits and independent light verification. RECORD_ONLY O-04-01 untouched; human/production acceptance remains pending.
