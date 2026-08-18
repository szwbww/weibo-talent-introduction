# Execution — child 01

## Execution identity

- Plan: `docs/plans/2026-08-18/01-decide-context-closure.md`
- Plan SHA-256: `68b064ac4ee6e44b88ca580fcfd14c2b502d6ea8a85c181ba5a8720b3d4f6805` (unchanged before/after execution)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence/docs/plans/2026-08-18/01-decide-context-closure.md@68b064ac…`
- Execution epoch: NEW
- Executor: Impl01 (execute-p worker)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence`
- Target branch: `fast/auto-reply-convergence`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence@fast/auto-reply-convergence@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-auto-reply-convergence`
- Pre-execution code SHA (base): `c24da14c0ebbfccdf6db970def03914ff14f99b6`
- Post-execution code SHA (HEAD): `f867dd4ef51a691a0f48724d4c73050e43d158ea`
- Implementation boundary: `c24da14..f867dd4` (one implementation commit, 7 files)

## Task status

| Task | Status | Files | Evidence |
|---|---|---|---|
| T1 · extend `GroundedAutoReplyDecisionService` deps + `decide()` signature + `buildAutoReplyContext()` fail-closed | IMPLEMENTED | `src/main/kotlin/.../mail/service/GroundedAutoReplyDecisionService.kt` | constructor `:47-49` (+3 deps); `decide()` `:51-56`; context build `:62`; `buildAutoReplyContext()` `:196-218`; kill switch stays first `:58-60` |
| T2 · `AutoReplyPreviewService` passes contact + messageId, merged duplicate `findById` | IMPLEMENTED | `src/main/kotlin/.../mail/service/AutoReplyPreviewService.kt` | `:111-117` single `findById` → `previewContact` reused at `:121-122` |
| T3 · `AutoMailReplyService` passes `effectiveContact` + `received.messageId` | IMPLEMENTED | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | `:505-510` |
| T4 · fix 18 broken test call sites across 4 test files | IMPLEMENTED | 4 test files | `stubGenerate` 5th/6th matcher `isNull()`→`anyString()`; 11 × decide calls in `GroundedAutoReplyDecisionServiceTest`; 3 + 3 matcher sites in preview/auto-mail tests; 1 constructor+call in `AiReplyDraftServiceTest` |
| T5 · 3 new regression tests | IMPLEMENTED | `GroundedAutoReplyDecisionServiceTest.kt` | `:303-353` real research sufficiency; `:355-378` null contact fails closed; `:380-415` training knowledge injection |

## Required commands (all freshly run this invocation, JDK 11)

`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

| # | Command | Exit | Tests run / Failures / Errors / Skipped | Result |
|---|---|---|---|---|
| 1 | `mvn test -Dtest=GroundedAutoReplyDecisionServiceTest` | 0 | 15 / 0 / 0 / 0 | PASS |
| 2 | `mvn test -Dtest=AutoReplyPreviewServiceTest` | 0 | 21 / 0 / 0 / 0 | PASS |
| 3 | `mvn test -Dtest=AutoMailReplyServiceTest` | 0 | 41 / 0 / 0 / 0 | PASS |
| 4 | `mvn test -Dtest=AiReplyDraftServiceTest` | 0 | 166 / 0 / 0 / 0 | PASS |
| 5 | `mvn test` (full regression) | 0 | 2574 / 0 / 0 / 4 | PASS |
| 6 | `mvn clean package` (build) | 0 | 2574 / 0 / 0 / 4, `BUILD SUCCESS` | PASS |
| 7 | `git diff --check` | 0 | no output | PASS |

Baseline at `4583525` (target classes): 12/21/41/166 — current counts match baseline +3 (the 3 new T5 tests). The 4 skips in the full suite are pre-existing environmental gating in untouched packages: `OperatorActionLogRepositoryTest`, `AuthFlowIntegrationTest`, `FlywayMigrationIntegrationTest` (all `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`, unset → skipped; FlywayMigrationIntegrationTest explicitly permitted by the brief) and `EuropePmcDataSourceTest` (permanent `@Disabled`).

## Files changed (exactly the 7 authorized)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` — +3 constructor deps, new `decide()` signature, `buildAutoReplyContext()` (I-1/I-2/I-3)
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` — pass contact + messageId, merge duplicate `findById` (I-3/I-4)
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` — pass `effectiveContact` + `received.messageId` (I-2)
4. `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` — stub/constructor/11 call sites + 3 regression tests
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt` — 3 decide matcher sites
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` — 3 decide matcher sites
7. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` — constructor + contact arg in seam test

No other file modified; no new files. `docs/plans/fast/**` untouched and NOT committed.

## Invariant evidence (file:line, current HEAD `f867dd4`)

- **I-1** — all four fields come from one `aiReplyContextService.build(...)` call: `GroundedAutoReplyDecisionService.kt:209-217` (`mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc` → `aiTrainingQaService.buildKnowledgeContext` → `aiReplyContextService.build`), consumed at `:67-70` (`expertProfile`/`mailHistory`/`contextWarnings`/`researchProfileSufficient` all from `context`). `grep -n "aiReplyContextService\.build|buildKnowledgeContext" GroundedAutoReplyDecisionService.kt` → each exactly 1 line (`:211`, `:210`), both inside `buildAutoReplyContext()`. No hand-built profile concatenation anywhere in the file.
- **I-2** — `researchProfileSufficient` passed to `generate()` is `context.researchProfileSufficient` (`:70` ← `AiReplyContext` value from real ES query); the `generate()` default back-inference is never used. Regression test `decide passes real research sufficiency instead of warning absence` (`GroundedAutoReplyDecisionServiceTest.kt:303-353`) captures the 10th `generate()` arg with `ArgumentCaptor` and asserts `false` for both (a) warnings=`["EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"]` and (b) warnings=empty — scenario (b) proves the value is not derived from warning absence (the default expression would have yielded `true`).
- **I-3** — fail-closed branch `GroundedAutoReplyDecisionService.kt:201-208`: `contact?.id == null` → `AiReplyContext(profileText = "", mailHistory = "", contextWarnings = listOf("EXPERT_PROFILE_NOT_FOUND"), researchProfileSufficient = false)` and no context construction. Regression test `decide with null contact fails closed` (`:355-378`) asserts `expertProfile == ""`, `researchProfileSufficient == false`, warnings contain `EXPERT_PROFILE_NOT_FOUND`, and `Mockito.verifyNoInteractions(aiReplyContextService)` (zero `build()` calls).
- **I-4** — preview stays read-only: `AutoReplyPreviewService.kt` has no `@Transactional`, no `.save(`, no `.send(` (`grep -n "Transactional|\.save(|\.send(" AutoReplyPreviewService.kt` → no matches). Context construction uses only read ops (`findAllByExpertContactIdOrderByCreatedAtAsc`, `findByOrcidId` inside `AiReplyContextService`, `buildKnowledgeContext`).
- **I-5** — `grep -rn "\.decide(" src/main --include=*.kt` → exactly 2 lines:
  ```
  src/main/kotlin/.../mail/service/AutoReplyPreviewService.kt:112  (inboundText, record.subject, previewContact, record.messageId)
  src/main/kotlin/.../mail/service/AutoMailReplyService.kt:505   (inboundText, received.subject, effectiveContact, received.messageId)
  ```
  No third caller, no bypass overload.

## must-NOT-change verification

- `resolveReason()` `:80-124`, `passesSendGate()` `:133-158`, `verifyAutoEvidenceRuleIds()` `:77-91`, `disabledDecision()` `:160-171`, `buildReplySubject()` `:73-80` — zero diff hunks (git diff touches only imports, constructor, `decide()`, and the new private method).
- `AutoReplyPreviewKind` enum — unchanged (`AutoReplyPreviewService.kt:20-27`).
- `processSingle()` branch order (classify → effectiveIntent → `when(autoAction)`) — unchanged; `AutoIntentAction.QA -> Unit` still precedes the `decide()` call.
- Reason constant set — `grep "const val" GroundedAutoReplyDecisionService.kt` vs `git show 4583525:...` → IDENTICAL, 7 constants (`:22-28`).
- `hasValidationFailure` (`:236-244`) still matches only the original set: `WARNING_STRUCTURED_RESPONSE_INVALID`, `WARNING_UNNATURAL_GROUNDED_STRUCTURE`, `WARNING_CLAIM_VALIDATION_FAILED`, `TRUST_REPAIR_EXHAUSTED`, `UNAUTHORIZED_ACTION_REMOVED`, prefix `AI_REPLY_CLAIM_`, prefix `AI_REPLY_ACTION_`. `EXPERT_PROFILE_NOT_FOUND` / `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` do NOT match (design intent: insufficiency surfaces via `QA_GROUNDING_GAP`, not `AI_REPLY_VALIDATION_FAILED`).

## Deviations / notes

1. **T5 test 1 extra scenario**: in addition to the plan's exact scenario (`contextWarnings=["EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"]`, sufficiency `false`), the same test stubs a second scenario (empty warnings, sufficiency `false`) to satisfy the I-2 acceptance criterion ("证明未走默认表达式"). Both assert captured arg `== false`.
2. **ArgumentCaptor on non-null params**: Kotlin inserts `Intrinsics` null-checks for matchers that return null (`any()`, `argThat()`, `capture()`) when the target parameter is a non-null reference type, so `capture()` calls on non-null params use the repo's established idiom `captor.capture() ?: defaultValue` (cf. `MailAutomationControllerTest.kt:70`, `ManualExpertMailServiceGateTest.kt:212`), and `build(contact, ...)` stubs/verifies use the `anyValue(default)` helper for the non-null `contact` param. Assertion semantics unchanged (captor values asserted afterwards; `KNOWLEDGE-MARKER` asserted via `knowledgeCaptor.value`).
3. **Existing 11 decision tests pass `contact = null`**: per plan T4 ("全部补 contact 实参"), the 11 pre-existing `decide(...)` calls in `GroundedAutoReplyDecisionServiceTest` pass a null contact, exercising the fail-closed branch; their assertions target decision reasoning (kill switch, reason precedence, send gate) which is orthogonal to context construction. Context construction is covered by the 3 new T5 tests (null contact, real sufficiency, knowledge injection).
4. **`ArgumentCaptor.forClass(Boolean::class.javaObjectType)`** used for the primitive `researchProfileSufficient` boolean arg to avoid primitive-class `Class.cast` issues in `getValue()`.
5. `AiReplyDraftServiceTest` seam test calls `decide("Auto question", "Subject", null)` — fail-closed context (`researchProfileSufficient=false`, `EXPERT_PROFILE_NOT_FOUND`) flows into the real `generate()`; assertions (`subject`, 1 observed LLM call, 0 stream calls) still pass — the legacy seam behavior is preserved (test `grounded auto reply entrypoint remains on legacy seam` green in the 166-test run).

## Freshness

- Plan identity rechecked: YES (sha256 unchanged `68b064ac…`)
- Worktree identity rechecked: YES (same root/branch/git-dir; HEAD now `f867dd4`)
- Reported commit reachable from target branch: YES (`git merge-base --is-ancestor f867dd4 fast/auto-reply-convergence`)
- Required commands run this invocation: YES (all 7)
- Historical evidence used only as baseline: YES
- Execution report committed: NO (left untracked for controller)

## Remaining blocker

None.

## Next action

- READY_FOR_VERIFICATION → run `verify-p`
