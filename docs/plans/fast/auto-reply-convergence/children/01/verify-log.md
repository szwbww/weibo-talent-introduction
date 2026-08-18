# Light Verification — child 01

(awaiting verifier)

## Light Verification: LIGHT_PASS
Child: 01 — plan `docs/plans/2026-08-18/01-decide-context-closure.md` (child brief `docs/plans/fast/auto-reply-convergence/children/01/brief.md`, sha256 68b064ac… matches)
Boundary: c24da14..f867dd4e
Verifier: Ver01

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-status c24da14..f867dd4e` → exactly the 7 authorized files (M: GroundedAutoReplyDecisionService.kt, AutoReplyPreviewService.kt, AutoMailReplyService.kt, GroundedAutoReplyDecisionServiceTest.kt, AutoReplyPreviewServiceTest.kt, AutoMailReplyServiceTest.kt, AiReplyDraftServiceTest.kt). No new files, no deletions, no other paths touched.|
|Plan and invariants|PASS|I-1: `aiReplyContextService.build` and `buildKnowledgeContext` each exactly 1 occurrence in GroundedAutoReplyDecisionService.kt (:211, :210), both inside `buildAutoReplyContext()` (:196-218); `generate()` consumes all four context fields (`expertProfile`/`mailHistory`/`contextWarnings`/`researchProfileSufficient`, :67-70); no hand-built profile text. I-2: `researchProfileSufficient = context.researchProfileSufficient` (:70); T5 test 1 captures 10th `generate()` arg and asserts false in BOTH scenarios incl. empty warnings (back-inference default would be true → proves no back-inference). I-3: null-contact fail-closed branch :201-208 (`profileText=""`, `mailHistory=""`, `contextWarnings=["EXPERT_PROFILE_NOT_FOUND"]`, `researchProfileSufficient=false`), skips context construction; T5 test 2 asserts `verifyNoInteractions(aiReplyContextService)`. I-4: `grep "Transactional\|\.save(\|\.send("` over AutoReplyPreviewService.kt → zero matches; diff of that file touches only the QA branch :108-123. I-5: `grep -rn "\.decide(" src/main` → exactly 2 lines (AutoReplyPreviewService.kt:112, AutoMailReplyService.kt:505). must-NOT-change: `git diff 4583525..f867dd4e` on the decision service shows only additive hunks (5 imports, 3 ctor deps, `decide()` signature/context/generate params, new private method); byte-compare of `object GroundedAutoReplyReason` (7 consts), `buildReplySubject`, `verifyAutoEvidenceRuleIds`, `resolveReason`, `passesSendGate`, `hasValidationFailure` vs 4583525 → SAME; `disabledDecision` untouched (no diff hunk; expression-bodied fun confirmed unchanged). `hasValidationFailure` (:236-244) still matches only the original 7 patterns; `EXPERT_PROFILE_NOT_FOUND`/`EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` match none. `AutoReplyPreviewKind` enum (AutoReplyPreviewService.kt:12-19) untouched. `processSingle()` branch order unchanged (diff at :505 only; `AutoIntentAction.QA -> Unit` still precedes). 3 T5 tests present at GroundedAutoReplyDecisionServiceTest.kt :303-353 (real sufficiency), :355-378 (null contact fail-closed + zero build calls), :380-415 (KNOWLEDGE-MARKER propagation into `aiReplyContextService.build` 4th arg) — all assert their contract, not plumbing.|
|Required commands|PASS|All run FRESH in worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`: (1) `mvn test -Dtest=GroundedAutoReplyDecisionServiceTest,AutoReplyPreviewServiceTest,AutoMailReplyServiceTest,AiReplyDraftServiceTest` → exit 0; surefire 15/0/0/0 + 21/0/0/0 + 41/0/0/0 + 166/0/0/0 (baseline 12/21/41/166 + exactly the 3 new tests). (2) `mvn test` → exit 0; `Tests run: 2574, Failures: 0, Errors: 0, Skipped: 4`, BUILD SUCCESS. (3) `mvn clean package` → exit 0; 2574/0/0/4, BUILD SUCCESS. (4) `git diff --check` → exit 0, no output. The 4 skips identified in surefire XML: OperatorActionLogRepositoryTest, AuthFlowIntegrationTest, FlywayMigrationIntegrationTest (all `@EnabledIfSystemProperty(named="migrationIt", matches="true")` — Flyway skip explicitly permitted), EuropePmcDataSourceTest (permanent `@Disabled`) — all pre-existing, in untouched packages.|
|Downstream interfaces|PASS|`decide(inboundText: String, inboundSubject: String?, contact: ExpertContact?, currentInboundMessageId: String? = null)` — exact shape 02/03 depend on. `GroundedAutoReplyDecision` data class fields unchanged. Reason constant set: 7 `const val` in `GroundedAutoReplyReason` byte-identical vs `git show 4583525:...`. EXPERT_* warnings excluded from `hasValidationFailure` (method byte-identical; no match against its 7 patterns), so they cannot surface as `AI_REPLY_VALIDATION_FAILED`. `AiReplyContextService.build(contact, records, inboundText, trainingKnowledge, currentInboundMessageId=null)` real signature matches the new call site; `AiReplyContext(profileText, mailHistory, contextWarnings, researchProfileSufficient)` matches the fail-closed construction.|

### AUTO_FIX
- N/A — no four-gate violation found.

### RECORD_ONLY
- N/A — nothing outside the light gate observed. (Note: `PendingMailOperationService.kt:535` is a 4th pre-existing `aiReplyContextService.build` caller; plan explicitly scopes it out — "这不是缺陷…本计划不改它", not a violation.)

### Required Action
- COMPLETE_CHILD
