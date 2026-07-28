# Repair Plan: trusted-reply-shared-workbench-04-single-frontend-workbench

Status: DRAFT — HUMAN APPROVAL REQUIRED  
Baseline plan: `docs/plans/2026-07-27/trusted-reply-shared-workbench-04-single-frontend-workbench.md`  
Verification report: phase4 re-verification, 2026-07-28 (`FAIL`, `INITIAL`)  
Implementation boundary: `2a6521b7` (`feat: add training reply evaluation audit`) to current eight-file working-tree implementation

## Objective

Reject stale or cross-item generation results before they can become lockable, assembled, or adopted, and restore the mandatory shared-workbench card/style contract.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-5, I-6, I-7; acceptance: stale/foreign item responses cannot update state or be adopted | `adjustItem` appends the terminal result without verifying response source/sourceVersion/evidenceSetVersion or the returned version's `requestKey`; full-result identity checks accept missing identity fields and silently return on mismatch, while stale errors can leave the previous assembly completable. |
| V-2 | P2 (mandatory) | Style DOM contract; S-1–S-5 acceptance | Item cards omit required `compose-panel`; new `trust-reply-fact-option` is neither styled nor an explicit reuse of an existing class. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Manual visual acceptance A-1–A-12 | Requires browser/operator evidence; no confirmed machine violation is converted into scope. |
| Existing compiler warnings and expected test log warnings | Pre-existing and unrelated to phase4 files/behavior. |

## Unchanged Contract

- `SIMULATION` remains evaluation-only; `LIVE` remains adopt-to-editor only. Neither path may send mail.
- Server remains sole authority for source/evidence validation, item versions, locks, assembly, `rawDraftText`, rendered output, and draft hash.
- No backend/API, manual-rich-reply, preflight, training-list, or old compatibility-endpoint change.
- Keep one IIFE export (`window.TrustReplyWorkbench`) and the existing two host adapters.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/trust-reply-workbench.js` | Validate generation identity before state mutation; use mandated shared card/reused class markup. |
| `src/main/resources/static/styles.css` | Define any retained new fact-option layout class within the phase4 style block. |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | Add discriminating stale-item-result and shared-card/style-contract regressions. |

## Repair Tasks

### R-1: Fail closed on stale or cross-item generation terminal results

- Resolves: V-1
- Root cause: The component's item SSE completion path accepts `result.version` or a matching array entry without requiring the server response identity to equal the mounted source/evidence snapshot; its stale-error path does not invalidate/rebootstrap the prior assembly. A full generation can therefore leave an old assembly available for completion after the server reports the source or evidence stale.
- Files: `src/main/resources/static/trust-reply-workbench.js`, `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- Change: Require every accepted full or item terminal result to contain and match the mounted `source`, `sourceVersion`, and `evidenceSetVersion`; require an item version to match the requested `requestKey`. Treat stale/missing/foreign identity and `TRUST_REPLY_SOURCE_STALE`/`TRUST_REPLY_EVIDENCE_STALE` errors as non-adoptable: do not append or lock them, invalidate the current assembly/final action, and follow I-6's stale refresh/confirmation path rather than silently retaining it as current.
- Regression test: Simulate an item terminal SSE response with the active request sequence but a different source/evidence identity and another `requestKey`; assert no item version, lock, assembly, or completion state is accepted. Cover a missing identity field in the full-result path and a stale full-generation error while a prior assembly exists; both must disable completion.
- Existing verification: `node --check src/main/resources/static/trust-reply-workbench.js`; `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyLoadingFeedback.test.js src/test/js/aiReplyReviewConfirmation.test.js`.
- Must not change: Exact `mailRecordId`/`inboundProcessingId` source mapping, server request payload shapes, valid current result behavior, or the existing server-side stale validation.
- Prohibited: Backend/API edits, local answer composition, automatic adoption, automatic send, and broad retry/error-framework replacement.

### R-2: Restore mandatory common card and fact-option style contract

- Resolves: V-2
- Root cause: `renderRequest` does not apply the required common card class, and the new fact-option class has no corresponding style or documented reuse.
- Files: `src/main/resources/static/trust-reply-workbench.js`, `src/main/resources/static/styles.css`, `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- Change: Render each item card with the required `compose-panel` base class. Either reuse the existing fact-option class explicitly or define the retained `trust-reply-fact-option` in the phase4 style block using existing tokens; do not introduce a new color/shadow system.
- Regression test: Assert the shared fixture contains the common item-card class and that every retained phase4-specific fact-option class has its stylesheet definition.
- Existing verification: `node --check src/main/resources/static/trust-reply-workbench.js`; `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js`; `node --test src/test/js/*.test.js`.
- Must not change: S-1–S-5 tokens, responsive breakpoints, accessibility roles/labels, or the same internal role tree for the two modes.
- Prohibited: Global `.button`, `.badge`, `.panel`, typography, unrelated visual cleanup, and layout redesign.

## Verification Commands

1. `node --check src/main/resources/static/trust-reply-workbench.js`
2. `node --check src/main/resources/static/app.js`
3. `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyLoadingFeedback.test.js src/test/js/aiReplyReviewConfirmation.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
5. `node --test src/test/js/*.test.js`

## Completion Criteria

- A terminal result with mismatched or absent source/evidence identity, or a mismatched item `requestKey`, cannot change state or enable assembly/adoption.
- Valid current results retain existing per-item behavior and server-authoritative assembly.
- Every item card carries `compose-panel`; every retained new fact-option class is styled with existing design tokens.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
