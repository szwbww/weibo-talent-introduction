# Repair Plan: mailbox-suspension

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-10-03/mailbox-suspension.md` (`a507377f29cdda0f1f77b80767f2c924f99bfb1c6f84301d258fb880d632b5b7`)
Verification report: aggregate review epoch 1, result `FAIL` / convergence `INITIAL`; controller destination: `docs/plans/review/2026-10-03-mailbox-suspension/machine-verification.md`
Implementation boundary: `9d7e389f00521582e45beba213d32508485cb536..0837c372f45d69374084113d8258d8c3320d748c`

## Objective

Ensure a late suspension-state or suspension-operation response for a no-longer-current mobile detail cannot change the active contact’s UI, remove its form, navigate it, or refresh its list context.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---:|---|---|
| V-1 | P1 | Child 02 I-5 / T3: every suspension UI request and callback is bound to current `contactId`, `listSeq`, `convEpoch`, `paneEpoch`, user, and disposal state; mobile return/switch clears pending UI work. | `loadSuspensionState`, `onSuspensionClick`, suspend/resume/end completion callbacks only check a subset of state (or `disposed`); `returnToMobileList` does not invalidate or detach suspension UI. A late A callback can render or remove UI while B is active. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Fast-p 01 O-1 | Pre-existing backend test-context/data error; unchanged from baseline. |
| Fast-p 01 O-2; 01b O-1 | Resolved: full Node suite now passes 1421/1421. |
| Fast-p 02 O-1 | Observation only: disabled controls cannot receive the recorded click in a browser. |
| Fast-p 02 O-2 | Manual browser-acceptance evidence remains pending; not repair authority. |

## Unchanged Contract

- Keep all existing mailbox endpoints, payloads, persistence, suspension semantics, reason text handling, tab/routing behavior, and styling unchanged.
- Keep the feature session-scoped; do not add client persistence, polling, new routes, or backend changes.
- Do not alter unrelated mailbox lifecycle behavior or test files.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/mailbox-chat.js` | Invalidate suspension UI work on context teardown and reject stale callbacks before DOM/global UI effects. |
| `src/test/js/mailboxSuspension.test.js` | Prove stale state and completion callbacks cannot affect another active/mobile-list context. |

## Repair Tasks

### R-1: Bind suspension callbacks to live detail context

- Resolves: V-1.
- Root cause: callbacks can pass only `disposed` or partial sequence checks after the selected contact/detail pane changed.
- Files: exactly the Authorized Files above.
- Change: make state-load, reason-form, suspend, resume, and end callbacks apply effects only while their captured suspension context is still current; mobile return and contact/detail teardown must invalidate pending suspension UI work and detach any reason/confirmation UI. A stale callback must be a no-op: no DOM render/remove, route/tab change, list refresh, or active-contact mutation.
- Regression test: delay A’s state/edit/completion response, return to the mobile list or select B and open B’s suspension UI, then resolve A. Assert B’s form/detail and navigation remain intact and no stale A effect runs. Cover a delayed load response after mobile return as well.
- Existing verification:
  1. `node --check src/main/resources/static/mailbox-chat.js`
  2. `node --test src/test/js/mailboxSuspension.test.js`
  3. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js`
  4. `node --test src/test/js/*.test.js`
- Must not change: backend API contracts, suspension state meaning, successful current-context behavior, existing auth/session controls, or unrelated UI flows.
- Prohibited: edits outside Authorized Files; new endpoint/storage/polling; suppressing callbacks merely by swallowing all errors; test-only production behavior.

## Verification Commands

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --test src/test/js/mailboxSuspension.test.js`
3. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js`
4. `node --test src/test/js/*.test.js`

## Completion Criteria

- The V-1 stale A-to-B/mobile-list interleavings are covered by discriminating regression tests and pass.
- A stale suspension callback has no DOM, route/tab, list-refresh, or active-contact effect.
- Changed product/test files are exactly the Authorized Files.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension/docs/plans/fix/mailbox-suspension/repair.md` invocation authorizes:

1. Only the Authorized Files and verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before `READY_FOR_VERIFICATION`, staging only `src/main/resources/static/mailbox-chat.js` and `src/test/js/mailboxSuspension.test.js`, with subject `fix(mailbox): guard stale suspension UI callbacks`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension/docs/plans/review/2026-10-03-mailbox-suspension/repair-execution.md` with exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension/docs/plans/fast/2026-10-03-mailbox-suspension/human-review-handoff.md` in the same task when the invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this exact file.
