# Fast-P Ledger — master: docs/plans/2026-09-20/00-manual-expert-material-upload-main.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-20/00-manual-expert-material-upload-main.md (commit d2a7f65ecbc46b5165863dfcab94ae5972f50605)
- Amendments: N/A
- Master base: d2a7f65ecbc46b5165863dfcab94ae5972f50605
- Branch: fast/manual-expert-material-upload-main
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-20T05:56:57Z
- Current child: backend
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

Master base `d2a7f65ecbc46b5165863dfcab94ae5972f50605` is a snapshot commit of the main worktree's
exact working-tree content at run start (parent `9ec9ff57997d0977f86de3f74acbf3b88d16a94a`), created with a
temporary `GIT_INDEX_FILE` so the main worktree and its index were not touched. It carries the
uncommitted SharePoint file-card WIP (`app.js`, `index.html`, `mailbox-chat.js`, `styles.css`,
`src/test/js/sharepointFileCardDisplay.test.js`) that both child plans require to be preserved.

- Frontend baseline `node --test --test-reporter=tap src/test/js/*.test.js`: exit 1, `# tests 1023 / # pass 1006 / # fail 17` in 12 files, all caused by the known pre-existing split cache key (`styles.css/mailbox-chat.js/app.js` = `20260919-sharepoint-file-card-display`, the other 8 assets = `20260918-material-request-ui`). Frontend child I-7 is required to unify those keys.
- Backend baseline `mvn test` (JDK 11): exit 1 after 03:06 min. Surefire aggregate over 498 report files is
  3490 tests / 0 failures / 0 errors / 13 skipped; the build fails at the `node-test` `exec-maven-plugin`
  step, i.e. the same 12 pre-existing JS files / 17 tests above. The Java suite is green at baseline and the
  JS red is pre-existing and unrelated to the backend child.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| backend | docs/plans/2026-09-20/manual-expert-material-upload-backend.md | commit:d2a7f65ecbc46b5165863dfcab94ae5972f50605 | none | 1 | LIGHT_PASS_WITH_NOTES | d2a7f65ecbc46b5165863dfcab94ae5972f50605 | 80beb2bddfc77f8f65f8c51446c9a6c14f2e10df | 0 | — | 80beb2bddfc77f8f65f8c51446c9a6c14f2e10df | — | Implementer BackendImplementer; verifier BackendLightVerifier; 5 RECORD_ONLY (O-1..O-5). |
| frontend | docs/plans/2026-09-20/manual-expert-material-upload-frontend.md | commit:d2a7f65ecbc46b5165863dfcab94ae5972f50605 | backend | 1 | LIGHT_PASS_WITH_NOTES | 80beb2bddfc77f8f65f8c51446c9a6c14f2e10df | 5f4967b8d5f94663266c095e6a2e9ec69f570505 | 0 | — | 5f4967b8d5f94663266c095e6a2e9ec69f570505 | — | Implementer FrontendImplementer; verifier FrontendLightVerifier; 3 RECORD_ONLY (O-1..O-3); JS suite 1035/1034/1 with the single red pre-existing. |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
