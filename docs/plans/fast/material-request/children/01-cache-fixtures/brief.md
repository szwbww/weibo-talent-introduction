# Child brief — 01-cache-fixtures

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request`
- Branch: `fast/material-request`
- `child_base_sha`: `44136f5dc7bbf94f429afa72c55242c5137d7f4f`
- Approved plan (complete contract): `docs/plans/2026-09-17/01-material-request-cache-fixtures.md`
- Master plan cross-plan constraints: `docs/plans/2026-09-17/00-material-request-master.md`
- Execution report to write: `docs/plans/fast/material-request/children/01-cache-fixtures/execution.md`

## Authorized files (exactly these nine; production resources are out of scope)

1. `src/test/js/batchSendTaskConsoleVisualFix.test.js`
2. `src/test/js/checkRepliesRelocation.test.js`
3. `src/test/js/mailboxChatStyle.test.js`
4. `src/test/js/manualReplySubjectPrefill.test.js`
5. `src/test/js/meetingConfirmationAssets.test.js`
6. `src/test/js/overlayAndDialogContrast.test.js`
7. `src/test/js/ragKnowledgeBasePage.test.js`
8. `src/test/js/ragWorkbenchRender.test.js`
9. `src/test/js/trustReplyWorkbenchSharedMount.test.js`

Do not modify `src/main/resources/static/**`, any Kotlin source, any migration, or any other test file. Do not touch the fast-p ledger or any other child's artifacts.

## Invariants (from the approved plan)

- I-1: each of the nine tests must take the shared version key from `index.html` by exactly capturing the `styles.css?v=<key>` value (escaped before use in a regex), then assert its own registered resource set: every listed resource appears exactly once and with that same key. A test that passes for any key is a failed implementation.
- I-2: keep every existing assertion intent — resource names, count, relative order, single-resource presence, `mailbox-chat.css` sharing the common key, CSS cache version, and the syntax checks. Only the source of the version literal changes.
- `meetingConfirmationAssets.test.js` still asserts exactly 11 registered resources; `ragKnowledgeBasePage.test.js` still asserts the full ordered set.

## Required commands (run all; JDK not needed)

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/mailboxChatStyle.test.js src/test/js/manualReplySubjectPrefill.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/overlayAndDialogContrast.test.js src/test/js/ragKnowledgeBasePage.test.js src/test/js/ragWorkbenchRender.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
git diff --check
```

Recorded baseline at `child_base_sha`: `node --test src/test/js/*.test.js` -> exit 0, `tests 990 / pass 990 / fail 0`; `git diff --check` -> exit 0.

## Mandatory negative experiment (plan acceptance I-1)

After the nine tests pass: temporarily change ONE `?v=` value in `src/main/resources/static/index.html` to a different string, re-run the nine tests plus the full JS suite, and confirm the shared-key assertions fail (record the exact failing assertion and counts). Then restore `index.html` byte-for-byte (verify with `git status --porcelain` showing no change under `src/main/resources/static/`) and re-run to confirm green. `index.html` must not appear in the implementation commit.

Re-read the live key from `index.html` before editing; never assume the value written in the plan prose.

## Downstream interface for child 03

Child 03 will set a brand-new value on all 11 `?v=` references in `index.html`. That bump is only allowed if these nine tests derive the key from `index.html`, so the post-change state must be: no version literal remains hard-coded in any of the nine files, and `rg -F '<old key>' src/test/js` returns nothing while the full JS suite stays green. Record in the execution report the exact `rg` command and output.

## Commit

Commit only the nine authorized files locally as:

```
feat(fast-p): implement 01-cache-fixtures
```

Exclude `docs/plans/fast/**` reports from that commit; the controller commits evidence separately.
