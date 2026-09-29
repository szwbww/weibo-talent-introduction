# Child 01 Baseline — seed boundary `4dccc7404dad92fc3a1dfe3224e2e6fe03feb331`

Product code at this boundary is byte-identical to master base `ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3` (the seed commit changes only `docs/plans/**`).

## Shared baseline run (all child-command classes; worktree, JDK 11)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest='BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest' test
```

Result: `Tests run: 270, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS (02:32 min).

Per-class counts: MailSenderAccountControllerMvcTest 8; BounceRateMonitorServiceTest 5; BounceDetectorTest 11; BounceCollectionServiceTest 16; BounceBackfillServiceTest 3; OperatorStatusReconcileServiceTest 12; ExpertOperatorStatusServiceTest 15; OperatorStatusWriteSeamGuardTest 1; ManualExpertMaterialUploadFlowTest 13; ManualInitialOutreachServiceTest 158; BatchSendTaskRuntimeIntegrationTest 22; MailOpenTrackingPersistenceTest 6.

This child's exact required Java command (`BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest`) is a subset of the green run: expected 13 tests / 0 failures.

## JS baseline

```bash
node --test src/test/js/senderBindingDisplay.test.js   # 8 pass / 0 fail / 0 skipped
node --check src/main/resources/static/app.js          # exit 0
```

Full JS suite (informational): `node --test src/test/js/*.test.js` → 1227 pass / 0 fail.

## Full-suite baseline (informational, JDK 11)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

Result: BUILD SUCCESS (04:50 min). Surefire `Tests run: 4320, Failures: 0, Errors: 0, Skipped: 13`; exec-bound Node suite 1227 pass / 0 fail; `node-check-app` / `node-check-task-modal-runtime` exit 0.

Pre-existing skipped tests: 13 (opt-in/Docker-gated integration classes), none failing.
