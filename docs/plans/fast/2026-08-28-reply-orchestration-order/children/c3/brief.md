# Child Brief — c3 · 14-workbench-concurrency（局部遮罩 + 条目级持久化 + autoBootstrap）

- Plan: `docs/plans/2026-08-28/14-workbench-concurrency.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): c2 terminal Code head (branch HEAD at dispatch time is the true base)
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 8 authorized files in the plan's `## 变更文件清单`: `src/main/resources/static/trust-reply-workbench.js`, `src/main/resources/static/app.js` (two `mount` call sites only: `mountAiTrainingTrustReply` ~app.js:3786 and `mountLiveTrustReply` ~app.js:9960), `src/main/resources/static/styles.css` (S-2 block only), `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` (new `PATCH /state/item`), `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` (per-item merge), `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`, `src/test/js/trustReplyWorkbenchSharedMount.test.js`, `src/test/js/autoRunOrchestration.test.js`. Nothing else. Do not touch `docs/plans/fast/**`.
3. The S-2 CSS block must be copied verbatim from the plan; the S-1 toolbar template string must match the plan's HTML skeleton (class names one-to-one). No inline styles, no new classes beyond the contract.
4. Default trade-off per plan: read-only mode KEEPS auto-analysis (方案 A). The `mount()` guard must carry a code comment stating this is the default trade-off, not an oversight.
5. Preserve every invariant I-1..I-5 and every `What must NOT change` item exactly as written.

## Global constraints (master plan 10)

- **G-7 (requestKey hash purity)**: `requestKey = sha256(sourceVersion, index, requestText, intentKeys)` — per-item persistence must not change the hash inputs; `expectedStateVersion` optimistic-lock semantics of `PUT /state` unchanged (integration path only).
- Scope freeze: no three-step UI, no fact set, no paragraph pinning, no operator facts (all in plan 15). No change to SSE stream/cancel/TTL semantics.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# backend tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest
# JS authority gate (per-file; these are the authoritative gates for this child)
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/autoRunOrchestration.test.js
# full JS regression
node --test src/test/js/*.test.js
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; every `node --test` exit 0 with `pass N, fail 0`; `git diff --check` exit 0. Verify `mvn test` output shows the `node --test` exec record.

## Downstream interfaces (for later children)

- c5 (15-workbench-three-step) depends on this child's: `PATCH /api/trust-reply/workbench/state/item` endpoint contract `{source, expectedStateVersion, schemaVersion, sourceVersion, evidenceSetVersion, requestKey, lockedItem}` returning the new `stateVersion`; `persistResolvedItem(request)` frontend function; per-item `itemBusyState(request)` / `busyOverlayState()` scope split (per-item `stateSavePending`; global mask only for `completePending` / `frameSavePending` / `generation.pending`); `autoBootstrap` mount option; `data-action="start-analysis"` toolbar contract (becomes `重新分析` secondary after analysis).
- c3 is parallel-capable with c2 by the master plan; it is serialized here by the one-writer-at-a-time rule.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c3
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c3/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
