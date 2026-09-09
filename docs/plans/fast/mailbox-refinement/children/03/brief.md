# fast-p child 03 brief — 静态资源版本统一更新

- Master: docs/plans/2026-09-09/00-mailbox-refinement-master.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/03-mailbox-refinement-assets.md (commit 351d69a538bcf891514f234a8d717cb5ef64c63c)
- Dependencies: child 01 (LIGHT_PASS), child 02 (LIGHT_PASS_WITH_NOTES). Base carries the full backend contract and the completed frontend (S-6 CSS verbatim, new DOM).
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement (branch fast/mailbox-refinement)
- Child base SHA: 1dd2e53f33d5817c9340c44fe733c308db8d5bea (child 02 terminal Code head; interleaved evidence commit b1df4aa7 precedes in ancestry)
- Execution report: docs/plans/fast/mailbox-refinement/children/03/execution.md
- Fix log: docs/plans/fast/mailbox-refinement/children/03/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. The child plan file is the authority.

## Authorized files (exactly 8; modify ONLY these)

1. src/main/resources/static/index.html
2. src/test/js/overlayAndDialogContrast.test.js
3. src/test/js/ragWorkbenchRender.test.js
4. src/test/js/checkRepliesRelocation.test.js
5. src/test/js/trustReplyWorkbenchSharedMount.test.js
6. src/test/js/batchSendTaskConsoleVisualFix.test.js
7. src/test/js/ragKnowledgeBasePage.test.js
8. src/test/js/manualReplySubjectPrefill.test.js

NOT authorized: mailbox-chat.css, mailbox-chat.js, app.js, styles.css, any other JS test file, backend Kotlin, migrations, plans.

## Work to implement (child plan T1/T2, I-1/I-2, S-1)

- T1: FIRST verify by full-tree search that the old cache key literal `20260907-material-chat` still hits exactly the 8 whitelist files (rg -n '20260907-material-chat' src/main/resources/static src/test). The child-plan whitelist is based on a live `rg -l` measurement; if any NEW file hits, do NOT widen scope — STOP and report PLAN_CONFLICT so the plan can be updated. Then replace `20260907-material-chat` → `20260909-mailbox-refinement` at:
  - index.html: all 7 resource registrations (styles.css, expert-materials.css, mailbox-chat.css, trust-reply-workbench.js, expert-materials.js, mailbox-chat.js, app.js) — query values only, keep link/script order and every other attribute identical (plan S-1; registrations live around index.html:11-13 and 2109-2112).
  - The 7 whitelisted JS test files: sync the same literal (fixed-value tests assert resource presence; keep one deliberate old-key negative assertion where the file already has one — the plan forbids weakening assertions).
- T2: run the required commands freshly (below). Real-browser refresh acceptance (A-1) is deferred to human acceptance per master plan — do not fabricate browser evidence.

## Repo traps (controller-verified)

- The 7 test files asserting the literal are exactly those in the whitelist (measured live at child 02 head: rg -l '20260907-material-chat' src/test → 7 files, plus index.html = 8 total). If your own fresh `rg -l` disagrees, STOP and report PLAN_CONFLICT (10-file scope ceiling in create-p forbids widening silently).
- Do not touch the S-6 CSS text, DOM structure, or resource ORDER in index.html — only query values change. Diff must show only the version literal (and test-name comments if already present).

## Required commands (run all freshly; exact output + exit codes in execution.md)

1. rg -n '20260907-material-chat|20260909-mailbox-refinement' src/main/resources/static/index.html src/test   (proof: old key absent, new key at 7 index registrations + 7 test files)
2. node --test src/test/js/*.test.js   (baseline at child-02 head: 765 pass / 0 fail)
3. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test package   (full suite + WAR package; baseline BUILD SUCCESS, JVM 3255 run / 0 fail / 9 skipped at child-02 head, node 765 pass)

## Constraints

- Only query version/literal and identical test constants/test-name comments may change; no assertion weakening, no test exclusion, no CSS/DOM/behavior change, no resource reorder.
- Commit implementation locally as: feat(fast-p): implement 03
- Exclude fast-p evidence (docs/plans/fast/**) from the commit; the controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
