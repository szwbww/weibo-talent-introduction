# Repair Plan: mailbox-progress-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-04/mailbox-progress-master.md
Verification report: aggregate review epoch 1 (FAIL / INITIAL)
Implementation boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..61d630b080266220c078cb38e64bf7f542141e01

## Objective

Every linked-expert card preserves the full expert name as the title tooltip while keeping the approved three-state status control.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | 02 S-2 requires the card-name `title` to retain the full name; master A-4 requires a long name to reveal fully on hover. | `renderPerson` emits `<strong>` without a `title` attribute. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Unavailable fresh MySQL IT and Flyway command evidence | Aggregate-test evidence is blocked or timed out; it is not a product repair finding. |
| Full Maven gate timing out before test execution | Environment evidence gap, not a confirmed mailbox-progress defect. |
| Child RECORD_ONLY O-1 items | Re-evaluated by aggregate review; neither changes this confirmed UI root cause. |

## Unchanged Contract

- Keep the seven Tab order, status words, menu behavior, progress API, busy/async guards, and server-side pagination unchanged.
- Keep `mailbox-chat.css` byte-identical; do not change CSS, resource keys, backend files, APIs, database migrations, or data.
- Preserve name escaping and the existing card/selection/keyboard behavior. Do not add a detail-page status control, icon, tooltip library, or inline style.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/mailbox-chat.js | Add the escaped full card name as the existing card-name `<strong>` element's `title` attribute. |
| src/test/js/mailboxChatBehavior.test.js | Add one regression assertion proving a long/special-character expert name is both escaped in text and preserved as the exact `title` value. |

## Repair Tasks

### R-1: Restore card-name hover title

- Resolves: V-1.
- Root cause: `src/main/resources/static/mailbox-chat.js:1630` renders the visible name but omits the mandatory `title` attribute.
- Files: `src/main/resources/static/mailbox-chat.js`; `src/test/js/mailboxChatBehavior.test.js`.
- Change: Render the current full expert display name in the card-name `<strong title="…">` using the same escaping boundary as its visible text; retain the existing fallback name/email/`-` behavior.
- Regression test: Mount a card with a long name containing HTML-sensitive characters; assert the `<strong>` text is escaped and its DOM `title` equals the full original name.
- Existing verification: run the focused mailbox behavior suite and syntax check, then the full JS suite.
- Must not change: card status/menu DOM, accessibility labels, selection flow, all API calls, CSS contracts, tab layout, or name fallback semantics.
- Prohibited: backend/database changes, `mailbox-chat.css`, `styles.css`, `index.html`, new dependencies, inline styles, tooltip components, or unrelated cleanup.

## Verification Commands

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mobileCoreNavigation.test.js`
3. `node --test src/test/js/*.test.js`

## Completion Criteria

- The card-name `<strong>` has an escaped `title` that exposes the full expert name, including for a long special-character name.
- The discriminating regression test fails without the title and passes with it.
- All three verification commands pass.
- Changed product/test files remain exactly within the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with `docs/plans/fix/mailbox-progress-master/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/mailbox-progress-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(mailbox-progress): restore card-name title`.
3. Appending `docs/plans/review/2026-10-04-mailbox-progress-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
