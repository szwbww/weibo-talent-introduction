# Child 03 Baseline — seed boundary `4dccc7404dad92fc3a1dfe3224e2e6fe03feb331`

Product code at this boundary is byte-identical to master base `ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3` (the seed commit changes only `docs/plans/**`).

## Shared baseline run (all child-command classes; worktree, JDK 11)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest='BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest' test
```

Result: `Tests run: 270, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS (02:32 min).

Per-class counts for this child's classes: ManualInitialOutreachServiceTest 158; OperatorStatusReconcileServiceTest 12; BatchSendTaskRuntimeIntegrationTest 22; MailOpenTrackingPersistenceTest 6; ExpertOperatorStatusServiceTest 15; OperatorStatusWriteSeamGuardTest 1 → 214 pre-existing tests green.

`RecipientAddressFailureClassifierTest` does not exist at this boundary; it is created by child 02 and extended by this child.

## Full-suite baseline (this child's second required command, JDK 11)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

Result at seed: BUILD SUCCESS (04:50 min). Surefire `Tests run: 4320, Failures: 0, Errors: 0, Skipped: 13`; exec-bound Node suite 1227 pass / 0 fail; `node-check-app` / `node-check-task-modal-runtime` exit 0.

Pre-existing skipped tests: 13 (opt-in/Docker-gated integration classes), none failing. Any failure introduced by this child must be compared against this zero-failure baseline.
