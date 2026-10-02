# Repair Plan: mailbox-last-reply-time

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-02/mailbox-last-reply-time.md
Verification report: aggregate/master verify-p review, 2026-10-02
Implementation boundary: MASTER_BASE_SHA=bf19fdfcb24336a41106d1c46fa7147bc6546892 .. final_code_head=bb0b9f1be4abd1fbd7b63aacaec333d4ec3e6019

## Objective

Accept a valid ISO local inbound timestamp with more than three fractional-second digits, truncate its fraction to milliseconds for `Date` creation, and render the required Beijing date, weekday, and minute rather than reporting it unavailable.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-2 / T-1 step 3: optional fractional seconds are supported and truncated to millisecond precision before creating `Date` | `formatLastReplyTime` accepts only 1–3 fractional digits, rejecting valid 4–9 digit ISO fractions. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-1 | Control-document EOF blank lines are outside product scope and do not violate the master’s worktree-level `git diff --check` requirement. |

## Unchanged Contract

- Display source remains only `summary.latestInbound.receivedAt`; do not use timeline or latest outbound data.
- Preserve fixed `Asia/Shanghai` rendering, strict invalid-calendar rejection, empty/unavailable branches, local slot-only refresh, race guards, CSS byte lock, and the 11-resource cache key.
- Do not modify backend, DTOs, APIs, persistence, cache state, polling, `mailbox-chat.css`, or unrelated UI behavior.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/mailbox-chat.js | Make the local parser accept a fractional component longer than three digits and pass only its first three digits to `Date`. |
| src/test/js/mailboxChatBehavior.test.js | Add one mounted production-script regression assertion for a 4–9 digit fractional timestamp, including required visible Beijing output. |

## Repair Tasks

### R-1: Preserve valid high-precision ISO local timestamps

- Resolves: V-1
- Root cause: The parser’s `\d{1,3}` fraction grammar excludes valid `ISO_LOCAL_DATE_TIME` nanosecond precision instead of truncating it as the approved plan requires.
- Files: `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxChatBehavior.test.js`
- Change: Accept the complete optional decimal fraction, retain validation of all other components and calendar rollover, then truncate the fraction passed to `Date` to three digits without changing displayed date/weekday/minute semantics.
- Regression test: Mount the actual `mailbox-chat.js` with `latestInbound.receivedAt=2026-10-02T17:59:59.123456789`; assert list and detail render `2026-10-02 星期五 17:59`, not the unavailable branch.
- Existing verification: Run the named last-reply suite under UTC and America/Los_Angeles, targeted behavior/style tests, full JS suite, syntax check, CSS byte comparison, and `git diff --check`.
- Must not change: Source selection, no-reply/unavailable distinction, current 1–3 digit fraction behavior, and all other baseline plan contracts.
- Prohibited: Any backend/API/schema/style/cache-key change, extra request/storage/timer, or changes outside the two listed files.

## Verification Commands

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `TZ=UTC node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js`
3. `TZ=America/Los_Angeles node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js`
4. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js`
5. `node --test src/test/js/*.test.js`
6. `cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css`
7. `git diff --check`

## Completion Criteria

- A valid four-to-nine digit fractional ISO local timestamp renders the required fixed Beijing date, weekday, and minute in both locations.
- Invalid calendar values remain unavailable, and `null + receivedCount=0` remains the only no-reply branch.
- Only the authorized files change for this repair; all commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/mailbox-last-reply-time/repair.md` invocation authorizes:

1. Only `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxChatBehavior.test.js`, and the seven required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only those two Authorized Files, with commit subject `fix(fast-p): accept high-precision last-reply timestamps`.
3. Appending `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md` with the exact approval source, repair identity and SHA256, pre-repair code SHA `bb0b9f1be4abd1fbd7b63aacaec333d4ec3e6019`, post-repair code SHA, changed files, seven command results and counts, deviations, executor identity when exposed, and clean-state evidence. The prior aggregate report is `docs/plans/review/2026-10-02-mailbox-last-reply-time/machine-verification.md`.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`, with commit subject `docs(review-fast-p): record mailbox last-reply repair execution`.
5. Returning to the already authorized aggregate re-review in the same task when the user's invocation requests it: `$review-fast-p docs/plans/fast/2026-10-02-mailbox-last-reply-time/human-review-handoff.md` using the committed `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
