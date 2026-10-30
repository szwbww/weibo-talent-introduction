# Child 01 — repair history

## Epoch 1 — Round 1/3
- Findings: F-01
- Before: af168d86f04f1e2f439e3291d6b7b940f5b60071
- Fix commit: 30c8af9b98f675f628d3ee98bd737f1716ecf54c
- Authorized files changed: src/main/resources/static/mailbox-readonly.js; src/test/js/mailboxReadOnly.test.js
- Commands: `node --test src/test/js/mailboxReadOnly.test.js` -> exit 0; 27 tests, 5 suites, 27 passed, 0 failed/cancelled/skipped/todo. `node --check src/main/resources/static/mailbox-readonly.js` -> exit 0. execute-p plan_identity.py before/after -> exit 0, unchanged SHA-256 77aef654ed91794dbadcfde5207d16b540d191802e4c2e819ee80bf47f8af0a4. worktree_identity.py with expected root/branch/git-dir -> exit 0. `git merge-base --is-ancestor HEAD fast/expert-access-master` -> exit 0.
- Result: FIXED
- Notes: Only F-01 repaired: existing revokeAccess seam clears selected.expertTags to []; selected name, forbidden-body handling and review reset remain unchanged. Four new regression cases cover list/detail × 403/401 after allowed tags/body are displayed. Fresh real headless Chromium loaded the changed module into an isolated DOM host on about:blank with an injected deterministic API responder; all four denial combinations passed twice (8 checks): before denial both list/header displayed 新发现 and 瑞典; after denial no expert tags or body articles remained, 张教授 and 暂无权限查看该专家邮件 remained. Actual viewport was 1440×900 throughout; loop width labels were fixture labels, not mobile viewport evidence. This is changed-surface DOM/behavior evidence, not authenticated backend, CSS/layout, screenshot or mobile acceptance evidence. Browser tab closed. Old code was not rerun. Product commit contains only the two authorized files; evidence remains uncommitted for controller. Plan identity and target worktree matched before commit; commit is target HEAD and reachable from target branch. No RECORD_ONLY work, plan edits, API/policy changes, push, merge or history rewrite.
