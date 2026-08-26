# Child Brief — c3 · 整封信编排（邻近事实去重 + CTA 唯一）与整合预览渲染

- Plan: `docs/plans/2026-08-26/03-orchestration-and-preview.md` (Plan identity: `commit:ee0749d3beedea7e26f4bf4e097b3d33a1684b7d`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order`
- Branch: `fast/2026-08-26-execution-order`
- Child base (product boundary): MUST be c2's terminal Code head (see ledger); not the master base.
- Master plan: `docs/plans/2026-08-26/00-execution-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 6 authorized files listed in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**`. Do not touch any other product/test file.
3. Preserve every invariant I-1..I-7, the S-1/S-2 style contracts, and every `What must NOT change` item exactly as written.
4. `TrustReplyWorkbenchService.kt` changes are confined to `toCoverage` + `suggestedInstructionFor` (and their immediate helpers as the plan specifies). `renderedDraftText`/`rawDraftText` server logic (`:1471-1477`) untouched.
5. `trust-reply-workbench.js`: only `state.previewTab`, `renderSummary` preview block, one action-dispatch branch. No `host.querySelector` for preview tabs. `:1373-1377` write block untouched.
6. `styles.css`: append the S-1 rule blocks verbatim (exact text, attribute order, spacing) after the `.trust-reply-assembly` block; do NOT modify `.trust-reply-assembly`, `.trust-reply-page-nav`, `.trust-reply-page-tab` blocks.
7. JS tests use hand-written DOM stubs (FakeElement.querySelector returns null) — the new tab logic must work through `state` + full `render()`, never direct DOM queries.

## Required commands (run fresh, after final state)

JDK 11 is mandatory for mvn: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new Kotlin test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplySuggestedInstructionTest
# affected existing Kotlin tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchControllerTest
# modified JS tests
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/autoRunOrchestration.test.js
# affected other JS tests
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/aiReplyLoadingFeedback.test.js
# JS full
node --test src/test/js/*.test.js
# static syntax check (same as pom node-check-app)
node --check src/main/resources/static/app.js
# full gate (Kotlin + JS via exec-maven-plugin)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; every `node --test` exit 0 with `# fail 0`; `node --check` exit 0; `git diff --check` exit 0.

## Downstream interfaces

- No downstream child; interfaces consumed at finalization: `data-role="raw-preview"` and `data-role="local-preview"` must remain present (existing JS test assertions).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c3
```

Write the full execution report to `docs/plans/fast/2026-08-26-execution-order/children/c3/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
