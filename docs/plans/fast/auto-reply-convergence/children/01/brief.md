# Child 01 Brief — decide 上下文收口

- Child ID: `01`
- Plan: `docs/plans/2026-08-18/01-decide-context-closure.md` (sha256:68b064ac4ee6e44b88ca580fcfd14c2b502d6ea8a85c181ba5a8720b3d4f6805)
- Master plan: `docs/plans/2026-08-18/00-auto-reply-convergence-master.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence`
- Branch: `fast/auto-reply-convergence`
- Base: `c24da14` (HEAD of branch at dispatch; plan files seeded)
- Plan identity: sha256:68b064ac4ee6e44b88ca580fcfd14c2b502d6ea8a85c181ba5a8720b3d4f6805

## Contract

The approved plan file is the complete contract. Read it fully and implement every task T1–T5 exactly as specified. This brief adds only execution context; where this brief and the plan differ, the plan wins.

Use the `execute-p` skill against the plan path above.

## Authorized files (exactly these 7)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

No other file may change. No new files.

## Global invariants (master plan, binding on all children)

- **X-1**: `GroundedAutoReplyDecisionService.decide()` remains the ONLY shared decision point for auto preview and auto send — exactly 2 production callers (`AutoReplyPreviewService`, `AutoMailReplyService`). No third caller, no bypass overload.
- **X-2**: Preview stays counterfactual and read-only: no `@Transactional`, no `save`/`send` in preview; runtime gates are `wouldBeBlockedBy` markers only, never hide the body.
- **X-3**: `ANSWER_FROM_OPERATOR_INPUT` semantics unchanged: stays `UNSUPPORTED`, empty claims, explicit adoption required, no QA evidence, no auto-send permission.
- **X-4**: No per-item `generateItem()` pipeline restructuring in this round.

## Child-specific invariants (must verify in code after change)

- **I-1**: `decide()` builds expertProfile/mailHistory/contextWarnings/researchProfileSufficient entirely from one `aiReplyContextService.build(...)` call.
- **I-2**: `researchProfileSufficient` passed to `generate()` is the `AiReplyContext.researchProfileSufficient` value (from actual ES query), never the default `!contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")` back-inference.
- **I-3**: null contact → fail-closed: `researchProfileSufficient = false` + `EXPERT_PROFILE_NOT_FOUND` in warnings; skip context construction.
- **I-4**: Preview read-only; new context construction uses only read ops.
- **I-5**: After signature change, `grep -rn "\.decide(" src/main --include=*.kt` returns exactly 2 lines.
- must-NOT-change: `resolveReason()`, `passesSendGate()`, `verifyAutoEvidenceRuleIds()` AUTO/enabled/non-empty checks, `disabledDecision()`, `buildReplySubject()`, `AutoReplyPreviewKind` enum, `processSingle()` branch order, and the reason constant set (7 constants). The new `EXPERT_PROFILE_NOT_FOUND` / `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` warnings must NOT be added to `hasValidationFailure` matching.

## Required commands (run all; exit 0 + `Failures: 0, Errors: 0`)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoReplyPreviewServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test          # full regression
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package # build
git diff --check
```

Baseline at `4583525`: GroundedAutoReplyDecisionServiceTest 12/0/0, AutoReplyPreviewServiceTest 21/0/0, AutoMailReplyServiceTest 41/0/0, AiReplyDraftServiceTest 166/0/0. Skipped allowed only for `@EnabledIfSystemProperty`-gated `FlywayMigrationIntegrationTest`.

## Downstream interfaces (consumed by children 02/03)

- `decide(inboundText, inboundSubject, contact, currentInboundMessageId)` — new signature; 02/03 depend on its semantics, not its shape.
- `GroundedAutoReplyDecision` fields unchanged; reason constant set unchanged (7 constants; grep `const val` vs `git show 4583525:...` identical).
- New regression tests in `GroundedAutoReplyDecisionServiceTest` per plan T5 (3 tests).

## Deliverable

- Commit the implementation locally as `feat(fast-p): implement 01`. Exclude all fast-p artifacts (`docs/plans/fast/**`) from the commit.
- Write the full execution report to `docs/plans/fast/auto-reply-convergence/children/01/execution.md` (append to existing file if present): commands run with exit codes/counts, files changed, verification of each invariant I-1..I-5 with file:line evidence, any deviations.
- Do NOT commit the execution report; the controller commits evidence separately.
- Do not review children 02/03, repair unrelated behavior, push, merge, or rewrite history.
