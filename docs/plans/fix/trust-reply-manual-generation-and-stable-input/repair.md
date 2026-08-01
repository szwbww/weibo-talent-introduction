# Repair Plan: trust-reply-manual-generation-and-stable-input

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-01/trust-reply-manual-generation-and-stable-input.md
Verification report: verify-p — FAIL / INITIAL (2026-08-01, current review)
Implementation boundary: working-tree diff against `32b1286eafb3d102eddece4ebc8c91acdf8f67f9` on `main`

## Objective

Restore the established stale fail-closed path for an assemble-triggered `FULL_DRAFT`: discard stale request versions, offer bootstrap refresh, and never post `/assemble`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-5 | P1 | I-5; AC-6; M-8 | `generateMissingGrounded()` converts a server `TRUST_REPLY_SOURCE_STALE` / `TRUST_REPLY_EVIDENCE_STALE` error into a retryable generic failure instead of routing it to `handleStaleGeneration()`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1–V-4 | Resolved in the current implementation boundary; cancellation/malformed full-generation handling, duplicate rejection, validator use, and focused input-stability coverage are outside this repair. |

## Unchanged Contract

- No mount-time generation; only explicit per-item `ADJUST_ITEM` and assemble-triggered `FULL_DRAFT` remain.
- Cancellation, malformed terminals, and identity-invalid response payloads retain valid human decisions and never call `/assemble`.
- A server-declared stale result remains distinct: clear stale versions through the existing `handleStaleGeneration()` path, then offer explicit bootstrap refresh.
- Do not change APIs, SSE/TTL/cancel protocol, backend/audit paths, mail send behavior, CSS, cache keys, canonical ordering, or `serializeResolvedVersion` ownership.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/trust-reply-workbench.js` | Restore stale-error routing in the `FULL_DRAFT` transaction. |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | Prove server-declared stale `FULL_DRAFT` resets stale versions and prevents assembly. |

## Repair Tasks

### R-1: Route server stale errors through the existing stale handler

- Resolves: V-5
- Root cause: the new full-generation catch branch bypasses `isStaleError()` / `handleStaleGeneration()`, unlike the established generation path.
- Files: `src/main/resources/static/trust-reply-workbench.js`, `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- Change: distinguish `TRUST_REPLY_SOURCE_STALE` and `TRUST_REPLY_EVIDENCE_STALE` from cancellation, malformed payload, and ordinary generation failures; pass only the stale errors to the existing stale reset-and-explicit-refresh flow.
- Regression test: start with a resolved manual item and missing GROUNDED item; return a server stale error from `FULL_DRAFT`; assert zero `/assemble` calls, cleared stale versions/assembly, stale status, and no implicit regeneration.
- Existing verification: targeted workbench tests, full Node suite, Maven test, and `git diff --check`.
- Must not change: malformed or identity-invalid response terminals keep existing manual decisions; valid full-generation allowlist merge remains unchanged.
- Prohibited: backend/API changes, automatic retry, automatic bootstrap without confirmation, local reply composition, or send-path changes.

## Verification Commands

1. `node --check src/main/resources/static/trust-reply-workbench.js`
2. `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/unmatchedQaReplySource.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`
3. `node --test src/test/js/*.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
5. `git diff --check`

## Completion Criteria

- `FULL_DRAFT` server stale errors invoke the existing stale fail-closed path.
- That path makes zero `/assemble` calls, clears stale decision/assembly state, and does not implicitly generate or refresh.
- Non-stale cancellation, malformed, duplicate, and identity-invalid cases retain manual state.
- Changed files remain limited to the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
