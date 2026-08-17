# Child 05 Fix Log — fast-p expert-reachability

## Epoch 2 — Round 1/3
- Findings: F-1 (A3-authorized stale EXCLUDED_NOISE_SITES pins in OperatorStatusWriteSeamGuardTest.kt: 94→95, 484→485, 431→476; contexts byte-unchanged)
- Before: f5025fcfd2d98d16f55c1cf79d55bf12c24ad4b6
- Fix commit: f6d81f1d4b64060ebf762715ad19b28452b463b8
- Authorized files changed: src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
- Commands:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest` -> exit 0, BUILD SUCCESS (584 pass / 0 fail)
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReachabilityFilterSeamTest` -> exit 0, BUILD SUCCESS (584 pass / 0 fail)
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0, BUILD SUCCESS, Tests run: 2509, Failures: 0, Errors: 0, Skipped: 4
  - `git diff --check` -> clean (exit 0)
- Result: FIXED
- Notes: Pin targets re-verified against source before edit: `operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"` at ExpertIndexController.kt:95, `operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"` at ExpertIndexController.kt:485, `operatorStatus = source.nullableText("operatorStatus")` at ExpertSearchService.kt:476. No other guard content touched; no RECORD_ONLY items addressed.
