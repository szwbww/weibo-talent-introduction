# Child 02 Brief — Mailbox "待匹配" → "待确认" Display

## Contract
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion`
- Branch: `fast/auto-response-exclusion`
- Child base: child 01 terminal `Code head` (the controller fills the exact SHA at dispatch)
- Approved plan (complete contract, read first): `docs/plans/2026-10-20/mailbox-pending-confirmation-label.md`
- Plan identity: `sha256:96de6aeac5609cfb4a049b0c631525fcb5096d91e56f96e292914f7b81bbd9b9`
- Execution report: `docs/plans/fast/auto-response-exclusion/children/02/execution.md`
- Use `execute-p`. Implement exactly; display-only.

## Goal
Change only user-visible "待匹配" strings to "待确认" in the mailbox unmatched queue (tab, empty states, error, aria-labels, detail placeholder, legacy tag display), and bump all versioned static asset references to the same new key `20261020-auto-response-confirmation`.

## Authorized Files (only these 5)
1. `src/main/resources/static/mailbox-chat.js`
2. `src/main/resources/static/app.js`
3. `src/main/resources/static/index.html`
4. `src/test/js/mailboxChatBehavior.test.js`
5. `src/test/js/mailboxChatStyle.test.js`

Do not modify CSS, backend, API parameters, enums, DB, DOM ids/classes/actions, or any other file.

## Invariants
- I-1: data unchanged. Keep `CHIP_UNMATCHED`, `unmatchedOnly`, `/api/mail/unmatched-inbound`, DOM ids, and option value `"待匹配"`; only display text becomes "待确认". Backend tag value stays `"待匹配"`.
- I-2: complete copy update across tab, empty states, error, aria-label, detail placeholder, and legacy tag badge; keep action names such as 绑定专家.
- I-3: update all existing versioned CSS/JS references in `index.html` to one identical new key `20261020-auto-response-confirmation`; check existing tests for the old key first.
- S-1: no CSS/class/inline-style changes; preserve structure, button actions, filter keys, counts, and responsive behavior.

## Required commands
- `node --check src/main/resources/static/mailbox-chat.js`
- `node --check src/main/resources/static/app.js`
- `node --test src/test/js/*.test.js`

## Downstream
None; this is the final child.

## Git
- Commit only the 5 product/test files as `feat(fast-p): implement 02`.
- Exclude `docs/plans/fast/**` and all fast-p reports; controller commits evidence separately.
- No push, merge, rebase, squash, amend, reset, or worktree removal.

## Return
`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
