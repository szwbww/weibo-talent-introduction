# Child Brief — c5 · 15-workbench-three-step（三步界面 + 运营事实）

- Plan: `docs/plans/2026-08-28/15-workbench-three-step.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): c4 terminal Code head (branch HEAD at dispatch time is the true base)
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the authorized files in the plan's `## 变更文件清单` (9 files after amendment A2, HUMAN-approved 2026-08-28T17:01:32Z): `src/main/resources/static/trust-reply-workbench.js`, `src/main/resources/static/styles.css` (S-2 + S-3 blocks only), `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` (new re-arrange endpoint), `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`, `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` (accept `operatorFacts` / `paragraphPlanDraft` overrides), `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`, `src/test/js/trustReplyWorkbenchThreeStep.test.js` (new), `src/test/js/trustReplyWorkbench.test.js`, `src/test/js/trustReplyWorkbenchSharedMount.test.js` (A2: update the hard two-tab assertions — exactly-2 role=tab count, frame-panel semantics at :2307/:2313-2317, keyboard-nav/setActivePage `[data-page="(facts|frame)"]` selectors, frame-stale activation — to the three-tab contract facts/factset/compose per S-1). Nothing else. Do not touch `docs/plans/fast/**`.
3. The S-2/S-3 CSS blocks must be copied verbatim from the plan; step-01 panel content must remain untouched (I-6 regression). No inline styles; no new classes beyond the contract; `.trust-reply-page-*` / `.trust-reply-item` existing rule blocks must not be modified (attribute-selector modifier `[data-pinned="true"]` only).
4. High-frequency interactions (topic change, adopt/unadopt, pin/unpin, merge-up, move up/down) are LOCAL DRAFT ONLY — zero persistence requests (I-4). Only 重排 (re-arrange) and 整合 talk to the server.
5. Operator facts use the `op1`, `op2`, … id space, incrementing within the letter, NEVER entering any hash (I-1 / G-7). They are verbatim slots (`PlanFact(id="op<n>", verbatim=true, required=true, body=<operator text>)`) protected by the same verbatim validation as controlled facts (I-2).
6. Preserve every invariant I-1..I-6 and every `What must NOT change` item exactly as written.

## Global constraints (master plan 10)

- **G-7 (requestKey hash purity)**: `requestKey = sha256(sourceVersion, index, requestText, intentKeys)` — `op*` facts must never enter the hash; test asserts requestKey identical with and without `op*` facts.
- Scope freeze: no sentence-level pinning; no index ingestion/recirculation (plan 16); step-01 interface itself is not refactored.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# backend tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest,AiReplyGroundedContentPlannerTest
# JS authority gate (per-file)
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/trustReplyWorkbenchThreeStep.test.js
# full JS regression
node --test src/test/js/*.test.js
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; every `node --test` exit 0 with `pass N, fail 0`; `git diff --check` exit 0.

## Downstream interfaces (for later children)

- c6 (16-unsupported-index) treats `op*` operator facts as the new archiving subject (channel B raw material) and expects the 13 protocol (`paragraphPlan` / `facts` / `paragraphs`) to be stable at the archive seam (`finalParagraphText` per paragraph is post-close text, stored separately from per-item versions — plan 12 IP-4).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c5
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c5/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
