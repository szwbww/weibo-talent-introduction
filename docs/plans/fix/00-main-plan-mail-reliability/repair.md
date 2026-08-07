# Repair Plan: 00-main-plan-mail-reliability

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/2026-08-06/00-main-plan-mail-reliability.md` (governing SHA-256 `5b8ca123301a2b9819470392bef3044cd33fbe1dcebe2ebb002dcbd628344e7d`, recorded identity `commit 9bbb046`; A1 applies)
Verification report: aggregate/master epoch 1, finding V-1
Implementation boundary: `d911bd6..ef7e471`

## Objective

An ES tag-profile request failure must show an error and render the affected detail panel with the profile-missing tag notice, instead of aborting the entire panel.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | P1 observable outcomes 1–4 and I-5: decorative tag-data failures must not block `showExpertDetail`, contact detail, mailbox read-only detail, or `showUnmatchedDetail`; failures remain visible via `showStatus(..., "error")`. | Each detail renderer awaits `fetchExpertTagsFromEs()` before completing its panel render, without a local fallback. The existing listener catch only reports the rejection after rendering has already been aborted. |

## Findings Excluded

| Finding | Reason |
|---|---|
| P4/P2/P3 aggregate requirements | Fresh verification passed; unrelated to V-1. |
| ES transport/backend behavior | P1 requires `fetchExpertTagsFromEs()` to keep propagating API failures; this repair must not catch or reclassify them inside that function. |

## Unchanged Contract

- `fetchExpertTagsFromEs()` retains its no-`catch` behavior; an ES/API failure remains distinct from `{ found: false, tags: [] }` returned by a successful no-profile response.
- `found === false` continues to select the existing S-1 profile-missing DOM; the S-2 present-profile DOM and tag mutation semantics remain unchanged.
- `handleContactAction`'s no-profile guard, tag level source, and all existing error-status wording/shape remain unchanged.
- No backend, migration, CSS, Message-ID, unsubscribe, or plan-authority changes.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/app.js` | Localize decorative tag-fetch failure handling at the four affected detail renderers. |
| `src/test/js/expertProfileAbsence.test.js` | Add discriminating regression coverage for each renderer's rejected tag fetch. |

## Repair Tasks

### R-1: Degrade rejected tag fetches after reporting them

- Resolves: V-1.
- Root cause: `showExpertDetail`, contact-detail rendering, `showMailDetail`, and `showUnmatchedDetail` await the tag fetch on their critical rendering path; rejection prevents their `innerHTML` render (and in `showUnmatchedDetail`, even `panel.hidden = false`).
- Files: `src/main/resources/static/app.js`, `src/test/js/expertProfileAbsence.test.js`.
- Change: At each of those four renderer call sites only, catch a rejected `fetchExpertTagsFromEs` request, call the existing `showStatus(error.message, "error")`, then continue with `{ found: false, tags: [] }` so the established S-1 tag editor is rendered and the rest of the panel completes. Keep the successful `found === false` path and `fetchExpertTagsFromEs` itself unchanged.
- Regression test: Extend `expertProfileAbsence.test.js` with a DOM-stubbed, extracted-function harness that makes the tag API reject for each of the four renderer paths. Assert the panel/detail HTML is populated, contains the existing profile-missing notice, and records the error status. Keep the existing unit test asserting `fetchExpertTagsFromEs` itself rejects.
- Existing verification: run the commands below.
- Must not change: S-1/S-2 exact output, `data-level` sourcing, mutation guard, or any non-tag detail data failure handling.
- Prohibited: catching inside `fetchExpertTagsFromEs`, treating ES failure as a successful backend no-profile response, adding CSS/inline styles, editing backend/tests outside the two authorized files, or changing Message-ID/unsubscribe code.

## Verification Commands

1. `node --test src/test/js/expertProfileAbsence.test.js`
2. `node --test src/test/js/expertTagBatchFix.test.js`
3. `node --check src/main/resources/static/app.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
6. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
7. `git diff --check`

## Completion Criteria

- A rejected tag fetch for every affected detail renderer reports an error and leaves the relevant panel rendered with the S-1 notice.
- Successful no-profile responses still render S-1, while `fetchExpertTagsFromEs` still rejects API failures in isolation.
- Only the two Authorized Files change; every verification command exits 0.

## Human Approval

Execution is prohibited until a human explicitly approves this plan.
After approval, run `execute-p` with `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/fix/00-main-plan-mail-reliability/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/fix/00-main-plan-mail-reliability/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(mail-reliability): degrade tag-load failures in detail panels`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/review/mail-reliability/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record mail-reliability repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
