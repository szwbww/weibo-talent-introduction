# fast-p child 05 brief — 资源激活与整体检查

- Master: docs/plans/2026-09-09/00-meeting-confirmation-master.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/05-meeting-confirmation-assets.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Evidence/contract copies: docs/plans/2026-09-09/meeting-confirmation-evidence/ (cache-key.txt = current version keys; frontend-before.md; meeting-confirmation.target.css)
- Dependencies: 01..04 all verified (04 terminal code head below). No later children.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation (branch fast/meeting-confirmation)
- Child base SHA: 314965645acf2ad95e03549093bdca12285a0e32 (child 04 terminal code head)
- Execution report: docs/plans/fast/meeting-confirmation/children/05/execution.md
- Fix log: docs/plans/fast/meeting-confirmation/children/05/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 9; modify ONLY these)

1. src/main/resources/static/index.html
2. src/test/js/manualReplySubjectPrefill.test.js
3. src/test/js/ragKnowledgeBasePage.test.js
4. src/test/js/overlayAndDialogContrast.test.js
5. src/test/js/ragWorkbenchRender.test.js
6. src/test/js/batchSendTaskConsoleVisualFix.test.js
7. src/test/js/checkRepliesRelocation.test.js
8. src/test/js/trustReplyWorkbenchSharedMount.test.js
9. src/test/js/meetingConfirmationAssets.test.js (NEW)

## Work to implement (from child plan T1..T3 + Invariants I-1/I-2; S-1 verbatim)

- T1 (I-1/I-2/S-1): index.html — keep the 7 existing versioned resources' relative positions; change ALL version keys to `20260909-meeting-confirmation` (current value is 20260909-mailbox-refinement — verify against the live source before editing, evidence/cache-key.txt); ADD `<link rel="stylesheet" href="meeting-confirmation.css?v=20260909-meeting-confirmation">` after mailbox-chat.css and `<script src="meeting-confirmation.js?v=20260909-meeting-confirmation">` BEFORE mailbox-chat.js per S-1 full block. 9 versioned resources total, no duplicates, no layout nodes added, no new classes/inline styles; task-modal-runtime.js keeps its unversioned position; links stay in head, scripts stay at body end.
- T2 (I-1/S-1): the 7 existing fixed-cache-key test files — update the pinned version string (and any count assertions 7→9 / resource arrays gain the two new entries), keep relative-order/path/function assertions intact; never delete a check for always-true.
- T3 (I-1/I-2/S-1): NEW src/test/js/meetingConfirmationAssets.test.js — asserts 9 versioned assets all `20260909-meeting-confirmation`, CSS order (meeting CSS after mailbox-chat CSS), script order (meeting JS before mailbox-chat.js before app.js), no duplicate registrations, component script referenced, no sample-fetch/preview-mock data in index registrations. Run full JS suite + mvn test once.
- Real-browser acceptance (04 A-1..A-8 / master A-1..A-2) is a HUMAN-review activity deferred out of this workflow (needs isolated env + SMTP + browser); do NOT fake it. If you cannot execute any of it, mark NOT_RUN in execution.md explicitly.

## Environment

- Frontend tests: node --test (repo convention). JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home for mvn.
- Baselines: seed node 766/0 + mvn 3234/0/9; child-03 head node 766/0 + mvn 3310/0/10; child-04 head node 829/0 + mvn 3310/0/10. After adding meetingConfirmationAssets.test.js expect node ≥830 (1 new suite) with 0 fail.
- Do NOT modify meeting-confirmation.js/.css or mailbox-chat.js/app.js (child-04 outputs, read-only here); only index.html registration + tests.

## Required commands (run all freshly; exact command/exit/counts in execution.md; unverifiable → NOT_RUN)

1. node --test src/test/js/meetingConfirmationAssets.test.js
2. node --test src/test/js/*.test.js (full JS; expect ≥830/0)
3. Full: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test (fresh; expect 3310+ Java / 0 fail / ~10 skipped, node suites green)
4. Optionally: node --check on index-adjacent static files is N/A (HTML); if a resource reference check via existing suites suffices, record it.

## Downstream / final state

- This is the last child: after it passes, the controller canonicalizes the ledger + handoff and runs the final artifact validator, then emits READY_FOR_HUMAN_REVIEW (human acceptance incl. real-browser A-1..A-2 of the master plan happens then).
- Whole-system behavior expectations: page loads 9 versioned assets; meeting trigger appears only when meeting-confirmation.js loaded; old views/workbench intact.

## Constraints

- Only the 9-file whitelist. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- If the live version key differs from evidence/cache-key.txt, re-read current index.html source first (evidence is the audit-time snapshot) and adapt only the version-string/test pins — record the discrepancy in execution.md.
- Commit implementation locally as: feat(fast-p): implement 05
- Exclude docs/plans/fast/** from the implementation commit; controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review earlier children, repair unrelated behavior, push, merge, or rewrite history.
