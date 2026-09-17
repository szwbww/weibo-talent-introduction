# Fast-P Child Brief 02

- Child ID: 02
- Approved plan: `docs/plans/2026-09-17/global-world-clock-02-registration.md` (identity `commit:96a0a411c88eaa447c2c6512f9c70fa8d8aaffb1`)
- Master plan: `docs/plans/2026-09-17/global-world-clock-master.md`
- child_base_sha: the terminal `Code head` of child 01 (recorded in the ledger; supplied at dispatch time)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-global-world-clock-master`
- Branch: `fast/global-world-clock-master`

The approved plan is the complete contract. Read it in full first, plus the master plan and the audit attachment `docs/plans/2026-09-17/global-world-clock-audit.txt`.

## Authorized files (exactly these ten)

1. `src/main/resources/static/index.html`
2. `src/test/js/batchSendTaskConsoleVisualFix.test.js`
3. `src/test/js/checkRepliesRelocation.test.js`
4. `src/test/js/mailboxChatStyle.test.js`
5. `src/test/js/manualReplySubjectPrefill.test.js`
6. `src/test/js/meetingConfirmationAssets.test.js`
7. `src/test/js/overlayAndDialogContrast.test.js`
8. `src/test/js/ragKnowledgeBasePage.test.js`
9. `src/test/js/ragWorkbenchRender.test.js`
10. `src/test/js/trustReplyWorkbenchSharedMount.test.js`

No other file may be created, modified, or deleted. `app.js`, `styles.css`, the back end, and the DB are out of scope. Child 01's three files are already committed and must not be edited in this child.

## Hard constraints

- I-1/I-7: the header keeps exactly the original `#currentUserDisplay` and `#logoutBtn`; the clock is inserted at runtime by child 01's script, so `index.html` must contain **no** static clock DOM. Delete only the whole `#showPollLogBtn` button; keep `#pollLogPanel`, `#pollLogBody`, `#closePollLogPanelBtn`, their handlers, and the polling implementation.
- I-8: exactly 11 versioned `?v=` asset references in `index.html`, all using `20260917-global-world-clock`; `task-modal-runtime.js` stays unversioned and in place. CSS order: styles, expert-materials, mailbox-chat, meeting-confirmation, world-clock. JS order: trust-reply-workbench, expert-materials, meeting-confirmation, mailbox-chat, app, world-clock (world-clock after app.js). Paths stay relative (no leading `/`).
- S-5/S-6 give the byte-exact target HTML for the `.topnav-side` block and the resource lines.
- I-9: child 01's CSS/DOM byte contracts stay untouched; no new inline style or `onclick`.
- The nine listed tests: update only the cache-key version value, the 9→11 counts, and the ordered asset collections/regexes; do not weaken them to "any version passes" and do not change their business assertions. `meetingConfirmationAssets.test.js` additionally gains the new-resource assertions (present once, CSS after the last old CSS, JS after app) and the header-entry regression assertions (no `showPollLogBtn`; `logoutBtn`/`currentUserDisplay`/`pollLogPanel`/`closePollLogPanelBtn` still present; the nine `data-view` values unchanged in order).
- Re-verify the fixed-key hit set before editing (`rg -l '20260914-followup-email' src/test`); the plan's table lists the nine files expected at this snapshot. If the set differs, return `PLAN_CONFLICT` rather than editing an unlisted file.
- No new product behaviour in this child; registration and deletion only.

## Required commands (run all, from the worktree root)

```bash
node --check src/main/resources/static/world-clock.js
node --test src/test/js/worldClock.test.js
node --test src/test/js/*.test.js
git diff --check
```

Baseline for comparison: full JS suite at the seed commit was tests 886 / pass 886 / fail 0. The suite must end with 0 failures. Do not run the Maven build (not required by either child plan); do not run formatters or linters.

## Deliverable

- `index.html` matching S-5/S-6 exactly and the nine test files updated as above.
- Local commit containing only the ten authorized files, message: `feat(fast-p): implement 02`
- Full result written to `docs/plans/fast/global-world-clock-master/children/02/execution.md` (commands, exit codes, pass/fail counts, files, commit SHA, deviations).
- Explicitly exclude `docs/plans/fast/**` from the implementation commit; the controller commits evidence separately.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, the commit SHA, a command summary, and the report path. Never push, merge, rebase, amend, or rewrite history.
