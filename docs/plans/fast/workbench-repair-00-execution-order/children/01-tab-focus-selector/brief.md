# Fast-P Child Brief — 01-tab-focus-selector

- Master: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Plan: docs/plans/2026-08-19/workbench-repair-01-tab-focus-selector.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
- Branch: fast/workbench-repair-00-execution-order
- Child base (product): b830ec208e9fe51bd693436f92158f1fde76622b
- Downstream: children 02, 03a, 03b run after this child; 01 is standalone (no dependencies) but must leave the workbench DOM contracts (I-2 tab/panel id + aria attributes) intact.

## Approved contract

The plan file is the complete approved contract. Read it fully from disk before starting and treat its bytes as authoritative:

- Authorized files (exactly 2):
  1. `src/main/resources/static/trust-reply-workbench.js` — T1: only the `setActivePage` function body (plan :1510-1516 region).
  2. `src/test/js/trustReplyWorkbenchSharedMount.test.js` — T2: one new `it(...)` case after the arrow/home/end navigation test; existing cases unchanged.
- Invariants: I-1 (no bare `#${instanceId...}` selectors), I-2 (tab/panel id + aria attributes unchanged; `tabId`/`panelId` retained), I-3 (focus must land on the target element after `render()`), I-4 (`[role="tab"][data-page=...]` / `[data-page-panel=...]` unique selectors).
- Style contract S-1: zero CSS/DOM/class changes; diff outside `setActivePage` body or in `styles.css`/`index.html` is out of scope.
- Out of scope: `gripHintId`, `makeId()`, max-height styling, paragraphs (02), version granularity (03a/03b).

## Execution rules (fast-p)

- Use `execute-p` against this exact plan. Run the plan identity gate (sha256) and worktree gate; the worktree above is the target.
- Modify only the two authorized files. Preserve every invariant listed in the plan.
- Run every required command from the plan's 验证命令 section freshly (JDK 11 zulu-11; node v22 is on PATH):
  - `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js`
  - `node --test src/test/js/*.test.js`
  - `node --check src/main/resources/static/trust-reply-workbench.js`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
  - `git diff --check`
- Commit the implementation locally as a single commit:
  `feat(fast-p): implement 01-tab-focus-selector`
  The commit must contain only the two authorized files. Do NOT include any file under `docs/plans/fast/` (the controller commits evidence separately).
- Do not touch `docs/plans/`, the ledger, later child plans, `styles.css`, `index.html`, or any file outside the authorized list.
- Do not review later children, repair unrelated behavior, push, merge, amend, or rewrite history.

## Reporting

Write the full `execute-p` result (Execution Result, Task Status, Commands, Changed Files, Deviations, Freshness, Remaining Blocker) to `docs/plans/fast/workbench-repair-00-execution-order/children/01-tab-focus-selector/execution.md` (append, do not overwrite prior content; file starts empty).

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the implementation commit SHA, a command summary, and the report path.
