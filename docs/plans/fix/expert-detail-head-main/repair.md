# Repair Plan: expert-detail-head-main

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-14/expert-detail-head-main.md
Verification report: aggregate/master review, 2026-08-15 (not written; report destination docs/plans/review/expert-detail-head/machine-verification.md)
Implementation boundary: 90498efb768f74a2371e895d984bde1ac4743c49..7b914c44e6410aa8c49c51d3bd25e8eb1f893322

## Objective

Treat a blank or whitespace-only bound sender-account code as unbound in the expert-detail header, so its pill remains visibly gray and says `未绑定`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | P2 I-9 requires empty **or whitespace-only** `contact.boundSenderAccountCode` to render the gray `.sender-binding-dot.is-unbound` and `未绑定`. | `loadContactDetail` uses the raw value as a truthy condition and label; a whitespace-only string is truthy. |

## Findings Excluded

| Finding | Reason |
|---|---|
| P1 stale `13 → 16` execution narrative | A1 approved the `10 → 13` criterion; evidence narrative only. |
| P1 `getEnabledAccount` doc-comment text | The functional call is `getAccount`; the comment mirrors the approved plan. |
| P2 pre-existing metadata `style=` text | New action-bar region adds none; base/head hit-set is identical. |
| P2 `.contact-head-actions .button[disabled]` rule | S-4 explicitly authorizes it when no existing disabled-button rule exists. |

## Unchanged Contract

- Manual-send payload remains `{ optionType, optionValue, senderAccountCode: null }`.
- `data-original` remains the only dirty-check source; a default-selected account for an unbound contact remains dirty.
- Bound, nonblank account codes retain their existing selected option and displayed value.
- No server/API behavior, popup logic, CSS, layouts, mailbox editor output, or other files change.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/app.js | Normalize the header's display/dirty/select binding value before rendering. |
| src/test/js/contactHeadLayout.test.js | Add one discriminating whitespace-only-binding regression case. |

## Repair Tasks

### R-1: Normalize the header binding value

- Resolves: V-1
- Root cause: the raw `contact.boundSenderAccountCode` is used by JavaScript truthiness, which treats whitespace as bound.
- Files: `src/main/resources/static/app.js`; `src/test/js/contactHeadLayout.test.js`
- Change: derive one trimmed-or-empty local binding value in `loadContactDetail`; use that same value for the pill dot/label, `#senderBindingSelect[data-original]`, and selected-option comparison.
- Regression test: with `boundSenderAccountCode = "   "`, assert rendered header has `sender-binding-dot is-unbound`, label `未绑定`, and empty `data-original`; preserve the existing assertion that an automatically selected account is dirty against that empty original.
- Existing verification: `node --test src/test/js/contactHeadLayout.test.js`; master focused JS gate; `node --check src/main/resources/static/app.js`.
- Must not change: P2 I-1/I-2/I-3 behavior, normal nonblank bindings, or any API payload.
- Prohibited: trimming or rewriting the stored backend value, changing CSS, changing the send branch, introducing selected-index logic, or widening file scope.

## Verification Commands

1. `node --test src/test/js/contactHeadLayout.test.js`
2. `node --test src/test/js/expertProfileAbsence.test.js src/test/js/senderBindingDisplay.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/composeTemplatePreview.test.js src/test/js/contactHeadLayout.test.js`
3. `node --check src/main/resources/static/app.js`
4. `node --test src/test/js/*.test.js`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
6. `git diff --check 90498efb768f74a2371e895d984bde1ac4743c49..HEAD`

## Completion Criteria

- Whitespace-only binding values render exactly as unbound and pass the new regression test.
- Existing unbound dirty-gate and normal bound-account behavior remain green.
- Changed files remain inside the authorized list.

## Review-Fast-P Execution Handoff

Resolved repair artifact: `docs/plans/fix/expert-detail-head-main/repair.md`.

Product/test Authorized Files are exactly:

1. `src/main/resources/static/app.js`
2. `src/test/js/contactHeadLayout.test.js`

Resolved product commit subject: `fix(fast-p): render whitespace sender binding as unbound`.

Resolved docs-only execution-handoff commit subject: `docs(review-fast-p): record repair execution`.

An explicit human-originated `$execute-p docs/plans/fix/expert-detail-head-main/repair.md` invocation authorizes:

1. Only the two Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the two Authorized Files, with subject `fix(fast-p): render whitespace sender binding as unbound`.
3. Appending `docs/plans/review/expert-detail-head/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/expert-detail-head/repair-execution.md`, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it: after `READY_FOR_VERIFICATION`, resume `$review-fast-p docs/plans/fast/expert-detail-head/human-review-handoff.md` using the committed `docs/plans/review/expert-detail-head/repair-execution.md`; do not ask the user to relay executor metadata.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
