# Child Brief — 03-fact-order-drag (P3)

## Approved contract
- Plan: `docs/plans/2026-08-19/03-fact-order-drag.md` (plan identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)
- Read the plan file in full. It is the complete approved contract; this brief only adds global constraints and downstream contracts.
- Master plan: `docs/plans/2026-08-19/00-grounded-coverage-master.md` (identity `commit:af1723f37021328f8ffa61261504727e514fbb4b`)

## Global constraints
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage` (branch `fast/grounded-coverage`)
- Child base SHA: equals child 02's terminal `Code head` (recorded in ledger). Verify via `git log -1` before starting.
- JDK 11 required: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` — bare `mvn` fails to build.
- Use skill `execute-p` against the child plan.
- Commit the implementation locally as `feat(fast-p): implement 03-fact-order-drag`.
- Do NOT commit fast-p reports/logs (docs/plans/fast/**) in the implementation commit; controller commits evidence separately.
- No push, no merge, no rebase, no amend, no history rewrite. One commit for implementation.
- Do not review later children, repair unrelated behavior, or add files outside Authorized Files.
- JS tests run inside `mvn test` via exec-maven-plugin node-test. Do NOT pass `-DskipNodeTests=true`.

## Authorized files (exact, from plan 变更文件清单)
1. `src/main/resources/static/trust-reply-workbench.js` (modify)
2. `src/main/resources/static/styles.css` (modify)
3. `src/test/js/trustReplyWorkbench.test.js` (modify)

## Required commands (all must run; JDK11)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full regression gate, includes src/test/js)
- `node --test src/test/js/trustReplyWorkbench.test.js` (focused)
- `git diff --check`
- Pass criteria: exit 0, `Tests run: N, Failures: 0, Errors: 0`.

## Key invariants (from plan; full set in plan)
- I-1 reorder must go through `changeRequestFacts` (same path as add/remove) — no bypass.
- I-2 frontend order = payload order = server-accepted order; no sort/reverse on factRuleIds.
- I-3 keyboard equivalent (ArrowLeft/ArrowRight on focusable grip with aria-label) required.
- I-4 spike first (HTML5 drag on grip inside chip with `<button>`); if spike fails, degrade to keyboard + arrow buttons and record the degradation.
- S-1/S-2/S-3: chip template and styles must match the contract verbatim; styles.css 7720-7819 untouched; no inline styles; grip uses `data-role="fact-grip"`, `draggable="true"` on the grip only (not the chip).
- Do NOT touch app.js (N5) or styles.css 6566-6605 (N4).
- Backend zero changes.

## Downstream interfaces
- None: P3 is the last child. Frontend-only.

## Verification contract
- After READY_FOR_VERIFICATION, a fresh verifier audits the four gates. Keep your execution report at:
  `docs/plans/fast/grounded-coverage/children/03-fact-order-drag/execution.md`
- Report shape: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
