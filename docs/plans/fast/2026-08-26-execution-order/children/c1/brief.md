# Child Brief — c1 · LLM 全库事实检索接入 auto 路

- Plan: `docs/plans/2026-08-26/01-llm-fact-retrieval.md` (Plan identity: `commit:ee0749d3beedea7e26f4bf4e097b3d33a1684b7d`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order`
- Branch: `fast/2026-08-26-execution-order`
- Child base (product boundary): `f2935072c819a9167e75220a6a959b0769462fde`
- Master plan: `docs/plans/2026-08-26/00-execution-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 7 authorized files listed in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**` (fast-p evidence; the controller commits it separately). Do not touch any other product/test file.
3. Preserve every invariant I-1..I-9 and every `What must NOT change` item exactly as written. The fixed system prompt in 阶段 1 must be used verbatim.
4. `AiReplyDraftService.kt` may be modified ONLY by appending the `retrievedFactRuleIds` field to `RequestFactItem` (one line, with default). No other change to that file.
5. All 5 `buildRequestFact` call sites must set `retrievedRuleIds` explicitly per I-2.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactRetrieverTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionRetrievalTest
# affected existing tests (must stay green)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `git diff --check` exit 0.

## Downstream interfaces (for later children)

- `QaFactSelectionService` constructor gains two optional params (`qaFactRetriever: QaFactRetriever? = null`, `factRetrieverProperties: FactRetrieverProperties = FactRetrieverProperties()`) — nullable-with-default so existing tests compile.
- `RequestFactItem` gains `retrievedFactRuleIds: List<Long> = emptyList()` (diagnostic only, never enters any hash).
- Status rule: `UNSUPPORTED && factRuleIds.isNotEmpty() -> PARTIAL` (child c2 composes its cap on top of this same expression).
- `select()`'s `sendQaRuleIds` must include retrieved facts (I-5).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c1
```

Write the full execution report to `docs/plans/fast/2026-08-26-execution-order/children/c1/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
