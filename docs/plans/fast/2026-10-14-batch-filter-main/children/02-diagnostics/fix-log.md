# Fix Log — 02-diagnostics

（自动修复轮记录；append-only）

## Epoch 1 — Round 1/3
- Findings: F-1 (admission not folded into eligibility)
- Before: 8b0736389c92e5d6f4c5766cdaf968720e6d2d03
- Fix commit: 9c9457cadda70e95dc605f720d652d3e4617b803
- Authorized files changed:
  - src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt
  - src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
  - src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt
- Commands: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false -> exit 0 / Tests run: 340, Failures: 0, Errors: 0, Skipped: 0 (BatchRecipientPreflightServiceTest 13, ManualInitialOutreachServiceTest 189, BatchRecipientSelectionServiceTest 14, BatchEmailVerificationServiceTest 40, ExpertSearchServiceTest 75, CountryContinentMappingTest 9); BUILD SUCCESS
- Result: FIXED
- Notes: `RecipientPreflightDecision` 新增 `admissionReasonKeys` 字段并纳入 `reasonKeys`，`eligible = included && reasonKeys.isEmpty()`（MAIN M-3 line 41）；准入未过的母集成员由 `admissionReasonKeys(selected)`（复用 `BatchRecipientSelectionService.reasonKeysOf` 口径，条件未通过时为空）贡献唯一准入原因，因此未获准入的发现目标不再计入 `eligibleBeforeLiveVerification` 而进入 `excludedUnique`，`reasonHits` 也带上 selector 的 `ADMISSION_*`。诊断响应 `BatchRecipientDiagnosticEntry` 同步新增 `admissionReasonKeys` 兼容字段（新 DTO 扩展，不改变旧字段）。回归：`a discovery target without approval is excluded even when explicit conditions pass (I-1 M-3)`（UNINITIALIZED，eligible=false，excludedUnique=1，eligible=0）与 `a needs-review discovery target is excluded with its admission reason (I-1 M-3)`（NEEDS_REVIEW 同理）。未触及只读仓储路径，按约束未重跑 IT 块。G-2 fixture（10/4/6/3、原因合计 6）与其余既有断言不变。
