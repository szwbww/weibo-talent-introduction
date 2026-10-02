# Repair Plan: mailbox-last-reply-time

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-02/mailbox-last-reply-time.md
Verification report: aggregate/master verify-p re-verification, epoch 3 (2026-10-03)
Implementation boundary: MASTER_BASE_SHA=bf19fdfcb24336a41106d1c46fa7147bc6546892 .. final_code_head=5a792f50ec006ff7297db75e0ff6c09e5efd3d9c

## Objective

Accept only an exact, unpadded `ISO_LOCAL_DATE_TIME` for a latest-inbound reply; leading or trailing whitespace must render “回复时间暂不可用”.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-3 | P1 | I-2/T-1: accept only a strict complete ISO-local string and reject whitespace. | `formatLastReplyTime` trims `receivedAt` before applying its strict regex, normalizing padded values into valid timestamps. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved: arbitrary-length fractional seconds are truncated to milliseconds; B-4b passes in both required TZ processes. |
| V-2 | Resolved: only `latestInbound === null && receivedCount === 0` renders “尚未回复”; B-3c passes. |
| O-1 / O-2 | RECORD_ONLY control-document whitespace; neither changes product behavior nor the master worktree `git diff --check` gate. |

## Unchanged Contract

- Keep `summary.latestInbound.receivedAt` as the sole display source, strict Beijing rendering, calendar-rollover rejection, high-precision fraction support, and the null/count discriminator.
- Keep slot-only refresh, race guards, draft/editor state, CSS byte lock, 11-resource cache key, and all existing DOM/CSS contracts.
- Do not modify backend, DTOs, APIs, persistence, cache state, polling, `mailbox-chat.css`, or unrelated UI behavior.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/mailbox-chat.js | Preserve the original string for strict ISO validation; do not trim padded timestamps into valid input. |
| src/test/js/mailboxChatBehavior.test.js | Add a mounted production-script regression for leading/trailing whitespace timestamps in both display locations. |

## Repair Tasks

### R-1: Reject padded latest-inbound timestamps

- Resolves: V-3
- Root cause: `formatLastReplyTime` applies `.trim()` before the complete-string regex, so values such as `" 2026-10-02T17:59:00"` incorrectly produce a normal reply time.
- Files: src/main/resources/static/mailbox-chat.js, src/test/js/mailboxChatBehavior.test.js
- Change: Apply the existing ISO grammar to the original string value. Leading/trailing spaces, tabs, or line breaks must take the unavailable branch; exact valid strings retain their current display.
- Regression test: Mount the real `mailbox-chat.js` with a valid timestamp padded at the front, end, and with tab/newline whitespace; assert list and detail both render “回复时间暂不可用” and contain no `<time>`. Retain an exact unpadded timestamp assertion.
- Existing verification: Run all seven master commands, including both required TZ named suites and the full JS suite.
- Must not change: Valid ISO parsing (including 4–9+ digit fractional seconds), Beijing date/weekday/minute output, invalid-calendar rejection, V-2's exact-null numeric-zero behavior, source selection, or refresh behavior.
- Prohibited: Backend/API/schema/style/cache-key change, extra request/storage/timer, or changes outside the two listed files.

## Verification Commands

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js`
3. `TZ=UTC node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js`
4. `TZ=America/Los_Angeles node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js`
5. `node --test src/test/js/*.test.js`
6. `cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css`
7. `git diff --check`

## Completion Criteria

- Padded ISO-local timestamps are unavailable in list and detail with no `<time>`; exact unpadded input still renders the specified Beijing text.
- Only the two Authorized Files change; all seven commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this exact artifact.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-mailbox-last-reply-time/docs/plans/fix/mailbox-last-reply-time/repair.md` invocation authorizes:

1. Only `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxChatBehavior.test.js`, and the seven verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only those two Authorized Files, with commit subject `fix(fast-p): reject padded last-reply timestamps`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-mailbox-last-reply-time/docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md` with the exact approval source, repair identity and SHA256, pre-repair code SHA `5a792f50ec006ff7297db75e0ff6c09e5efd3d9c`, post-repair code SHA, changed files, seven command results and counts, deviations, executor identity when exposed, and clean-state evidence. The prior aggregate report is `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-mailbox-last-reply-time/docs/plans/review/2026-10-02-mailbox-last-reply-time/machine-verification.md`.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`, with commit subject `docs(review-fast-p): record mailbox last-reply repair execution`.
5. Returning automatically to the same-task aggregate re-review after the evidence commit: `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-mailbox-last-reply-time/docs/plans/fast/2026-10-02-mailbox-last-reply-time/human-review-handoff.md` using the committed `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
