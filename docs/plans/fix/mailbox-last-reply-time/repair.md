# Repair Plan: mailbox-last-reply-time

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-02/mailbox-last-reply-time.md
Verification report: aggregate/master verify-p re-verification, epoch 2 (2026-10-02)
Implementation boundary: MASTER_BASE_SHA=bf19fdfcb24336a41106d1c46fa7147bc6546892 .. final_code_head=8319dd8bcf286b6b61d25b778bff0f74f976a5b8

## Objective

Show “尚未回复” only when `latestInbound === null` and `receivedCount === 0`; a missing, null, string, or otherwise non-numeric count must remain “回复时间暂不可用”.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1 | I-3: only `latestInbound === null && receivedCount === 0` is no-reply; missing/structurally invalid fields are unavailable | `lastReplyDisplay` coerces absent or invalid `receivedCount` through `Number(value) || 0`, making it zero. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved by committed `8319dd8`: arbitrary precision fractions are truncated to milliseconds and B-4b passes under both required TZ processes. |
| O-1 / O-2 | Control-document whitespace only; neither affects product behavior or the master worktree-level `git diff --check` gate. |

## Unchanged Contract

- Source remains only `summary.latestInbound.receivedAt`.
- Preserve strict Beijing rendering, invalid-calendar rejection, high-precision fraction support, slot-only refresh, race guards, CSS byte lock, and the 11-resource cache key.
- Do not modify backend, DTOs, APIs, persistence, cache state, polling, `mailbox-chat.css`, or unrelated UI behavior.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/mailbox-chat.js | Restrict the no-reply branch to an actual numeric zero count without coercion. |
| src/test/js/mailboxChatBehavior.test.js | Add a mounted production-script regression for `latestInbound: null` with a missing/invalid count. |

## Repair Tasks

### R-1: Preserve the I-3 no-reply discriminator

- Resolves: V-2
- Root cause: `Number(item.receivedCount) || 0` treats absent, null, empty, non-numeric, and string counts as zero.
- Files: src/main/resources/static/mailbox-chat.js, src/test/js/mailboxChatBehavior.test.js
- Change: In the exact-null inbound branch, choose “尚未回复” only when `receivedCount === 0`; every other count representation is unavailable.
- Regression test: Mount the actual `mailbox-chat.js` with `latestInbound: null` and each of an omitted count, `null`, and `"0"`; assert list and detail show “回复时间暂不可用” and have no `<time>`. Retain the existing `null + 0` no-reply assertion.
- Existing verification: Run the two required TZ named suites, targeted behavior/style tests, full JS suite, syntax check, CSS byte comparison, and `git diff --check`.
- Must not change: Valid `null + 0`, all non-null inbound parsing/display cases, V-1 high-precision fractions, source selection, and refresh behavior.
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

- Omitted, null, string, and non-numeric counts with `latestInbound: null` render unavailable in both locations; numeric zero still renders no-reply.
- Only the two Authorized Files change; all seven commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this exact artifact.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/mailbox-last-reply-time/repair.md` invocation authorizes:

1. Only `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxChatBehavior.test.js`, and the seven verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only those two Authorized Files, with commit subject `fix(fast-p): preserve last-reply no-reply discriminator`.
3. Appending `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md` with the exact approval source, repair identity and SHA256, pre-repair code SHA `8319dd8bcf286b6b61d25b778bff0f74f976a5b8`, post-repair code SHA, changed files, seven command results and counts, deviations, executor identity when exposed, and clean-state evidence. The prior aggregate report is `docs/plans/review/2026-10-02-mailbox-last-reply-time/machine-verification.md`.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`, with commit subject `docs(review-fast-p): record mailbox last-reply repair execution`.
5. Returning to the already authorized aggregate re-review in the same task when the user's invocation requests it: `$review-fast-p docs/plans/fast/2026-10-02-mailbox-last-reply-time/human-review-handoff.md` using the committed `docs/plans/review/2026-10-02-mailbox-last-reply-time/repair-execution.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
