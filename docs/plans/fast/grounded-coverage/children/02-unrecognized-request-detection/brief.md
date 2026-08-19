# Child Brief — 02-unrecognized-request-detection (P2a)

## Approved contract
- Plan: `docs/plans/2026-08-19/02-unrecognized-request-detection.md` (plan identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)
- Read the plan file in full. It is the complete approved contract; this brief only adds global constraints and downstream contracts.
- Master plan: `docs/plans/2026-08-19/00-grounded-coverage-master.md` (identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)

## Global constraints
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage` (branch `fast/grounded-coverage`)
- Child base SHA: equals child 01's terminal `Code head` (recorded in ledger). Verify via `git log -1` before starting.
- JDK 11 required: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` — bare `mvn` fails to build.
- Use skill `execute-p` against the child plan.
- Commit the implementation locally as `feat(fast-p): implement 02-unrecognized-request-detection`.
- Do NOT commit fast-p reports/logs (docs/plans/fast/**) in the implementation commit; controller commits evidence separately.
- No push, no merge, no rebase, no amend, no history rewrite. One commit for implementation.
- Do not review later children, repair unrelated behavior, or add files outside Authorized Files.

## Authorized files (exact, from plan 变更文件清单)
1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumerator.kt` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/llm/config/AskEnumeratorProperties.kt` (new)
3. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` (modify)
4. `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` (modify)
5. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` (modify)
6. `src/test/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumeratorTest.kt` (new)
7. `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` (modify)
8. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` (modify)

## Required commands (all must run; JDK11)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='InboundAskEnumeratorTest,QaFactSelectionServiceTest,AiReplyIntentCatalogTest'`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full regression gate)
- `git diff --check`
- Pass criteria: exit 0, `Tests run: N, Failures: 0, Errors: 0`.

## Key invariants (from plan; full set in plan)
- I-1 enumerator quotes must be verbatim substrings (whitespace-folded), length >= 8, else discarded.
- I-2 enumeration results must NEVER enter requestKey/versionId/evidenceSetVersion hashes (no changes to those three function bodies).
- I-3 shadow period: `unrecognizedAsks`/`unrecognizedAskCount` are separate fields; status/groundedRequestCount/unsupportedRequests/allowedHandlings computations unchanged. `QaFactSelectionService.kt:341-348` when block untouched.
- I-4 enumerator fail-open: all failure paths return `AskEnumeration(false, emptyList())`, never throws into bootstrap.
- I-5 unrecognized data never reaches AiReplyPointByPointComposer / AiReplyGroundedContentPlanner / AiReplyGroundedDraftMaterializer or any outbound text/prompt.
- I-6 `llm.ask-enumerator.enabled-for-auto-reply` default false; auto-reply path skips enumeration when off.
- I-7 span claiming uses alias hit spans (matchIntentsWithSpans), not count subtraction.
- Do NOT spread `unrecognizedAskCount` to AiReplyDraftService/AiTrainingController/UnmatchedInboundMailController DTOs, and do NOT create any new table — both are P2b scope.

## Downstream interfaces (consumed by later children)
- P3 (03-fact-order-drag) is frontend-only and independent; it consumes `trust-reply-workbench.js` state, which P2's workbench wiring must not restructure (only adds a DTO field with default).
- `RequestFactItem.unrecognizedAsks` and `ResolvedQaRules.unrecognizedAskCount` defaults keep all existing constructors compiling unchanged.

## Verification contract
- After READY_FOR_VERIFICATION, a fresh verifier audits the four gates. Keep your execution report at:
  `docs/plans/fast/grounded-coverage/children/02-unrecognized-request-detection/execution.md`
- Report shape: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
