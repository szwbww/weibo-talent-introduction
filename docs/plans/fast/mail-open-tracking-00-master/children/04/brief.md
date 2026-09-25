# Child 04 approved brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery
- Branch: fast/mail-open-tracking-00-master
- Exact approved plan: docs/plans/2026-09-25/mail-open-tracking-04-monitoring-ui.md (commit 7bfd699e17a872cfefdc18e19a9a1e2b5bad07ce)
- Master: docs/plans/2026-09-25/mail-open-tracking-00-master.md (commit ab2dda0f86c52d8bd9570d994fa895f087931df3)
- Product base: 439031c5c815de8a49a3b6fb7dfc72e58326db1a
- Dependencies: 03 terminal light pass
- Constraints: Use execute-p for implementation. Only authorized files, preserve master G-1..G-6 and downstream contracts. JDK11. No push/merge/rebase/reset/amend/worktree deletion. Product changes must be separate from evidence commits.
- Authorized Files:
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
  - `src/main/resources/static/styles.css`
  - `src/test/js/mailOpenTracking.test.js`
- Commands: Run every command in docs/plans/2026-09-25/mail-open-tracking-04-monitoring-ui.md acceptance criteria; Docker unavailable is NOT_RUN, not PASS.
- Outputs: committed implementation, execution.md and fix-log.md (explicit N/A for zero rounds). Independent verifier writes verify-log.md. All four child artifacts must change in the child's evidence commit, even when no automatic fix was needed.
