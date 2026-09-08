# Repair Plan: 00-mailbox-materials-master

Status: DRAFT — HUMAN APPROVAL REQUIRED  
Baseline plan: `docs/plans/2026-09-07/00-mailbox-materials-master.md` (sha256 `2bbfc191ad4a905e5a42dcb34b76bb490661abf243b280764452b5483fdc4044`)  
Verification report: `docs/plans/review/mailbox-materials/machine-verification.md`, Epoch 2, V-2 P1  
Implementation boundary: `8a0c5360e25e875e52800d17797a7b1ea4bd452c..009d9bab431c75184b00659905f80dcde91e3166`

## Objective

In the production classic-script page, mailbox-chat status and level selectors show the existing option catalogs and submit changes through the existing APIs.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1 | Requirement 3; G-5; I-10 I-5/S-3 | `mailbox-chat.js` reads catalogs from `window`, but `app.js` holds the existing catalogs in top-level lexical `const` bindings rather than `window` properties. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved only as A3 review-command waiver; it is Docker-environment evidence, not a product repair. |
| Child 10 O-2..O-4 and all other RECORD_ONLY observations | Not confirmed mandatory violations; no human approval extends this repair to them. |

## Unchanged Contract

- Keep the existing option arrays as the single source of truth; do not duplicate their values in mailbox-chat.
- Keep existing POST endpoints, payloads, session/QA/send authority, navigation, and all material/download behavior unchanged.
- Do not modify backend, schema, styles, cache keys, resource order, or Flyway migrations.
- Do not change A3: it waives only the aggregate-review Flyway command, not migration behavior.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/app.js` | Expose the existing lexical status and level option arrays on `window` for the already-loaded mailbox-chat host contract. |
| `src/test/js/mailboxChatBehavior.test.js` | Prove visible selector options and the existing status/level POST calls when values change. |

Execution evidence is separately limited to `docs/plans/review/mailbox-materials/repair-execution.md` by the Review-Fast-P handoff below.

## Repair Tasks

### R-1: Publish existing host option catalogs to mailbox chat

- Resolves: V-2.
- Root cause: `mailbox-chat.js:765-766` reads `global.operatorStatusOptions` and `global.indexLevelOptions`, while `app.js:656-670` declares the arrays with top-level `const`; classic-script global lexical bindings are not `window` properties.
- Files: exactly the two Authorized Files above.
- Change: assign the existing arrays to the corresponding `window` properties after their declarations. Do not recreate or transform the arrays. Extend the mailbox-chat behavior fixture with the same catalog values and assert that both selectors contain usable options; select changed values and assert the existing `/operator-status` and `/index-level` POST requests with their established payload keys.
- Regression test: a test must fail if either catalog is absent from the chat global, either selector is empty, or a changed status/level does not issue its established POST.
- Existing verification: run the focused behavior test, syntax checks for both changed runtime files, the full Node suite, and Maven suite below.
- Must not change: direct mail/chat UI calls must remain only those already used by `saveSettings`; no fallback literals, additional endpoint, or altered status semantics.
- Prohibited: product/backend changes outside the two Authorized Files; source-filter drawer work; debug cleanup; Flyway/database changes; dependency changes.

## Verification Commands

1. `node --check src/main/resources/static/app.js`
2. `node --check src/main/resources/static/mailbox-chat.js`
3. `node --test src/test/js/mailboxChatBehavior.test.js`
4. `node --test src/test/js/*.test.js`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`

## Completion Criteria

- Production host publishes the exact existing status and level catalog arrays on `window`.
- A changed status and level render as selectable options and cause the two established POST calls in the focused regression test.
- All listed verification commands pass.
- Product/test changes contain only the two Authorized Files.

## Human Approval

Execution is prohibited until the human explicitly approves this exact plan by invoking `$execute-p docs/plans/fix/00-mailbox-materials-master/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/00-mailbox-materials-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with commit subject `fix(mailbox-chat): expose host option catalogs`.
3. Appending `docs/plans/review/mailbox-materials/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
