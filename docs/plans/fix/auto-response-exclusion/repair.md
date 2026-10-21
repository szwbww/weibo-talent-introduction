# Repair Plan: auto-response-exclusion

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-10-20/auto-response-exclusion.md` (governing SHA-256 `01004d3ded06a9ac2fcf17ac642a27643445064beb0f342f283b646cc04e7625`; amendment A1 recorded)
Verification report: aggregate review epoch 1 — `docs/plans/review/auto-response-exclusion/machine-verification.md` (controller persistence pending)
Implementation boundary: `5f1e99d4fe024d698759c16ae5e2c9ba40554648..aee499beaaea8c282e6f453d0cecbe863724df01`

## Objective

Historical reclassification must never select or update records carrying the master-plan protected fallback reasons.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master I-5 / T4: `BODY_TRUNCATED`, `LEGACY_UID_UNVERIFIABLE`, `RECIPIENT_UNRESOLVED`, `CONTACT_NOT_FOUND`, and `INTRODUCTION_NOT_SENT` bypass new logic and remain unchanged. | The scan SQL selects every contact-bound `MANUAL_REVIEW` row, and apply re-proves and updates a manifest row without a protected-reason guard. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-2 | P2 stale detector KDoc; not mandatory and no human approval for cleanup. |
| V-3 | P2 brittle frontend literal-count assertion; test maintainability only, not a confirmed behavioral violation. |

## Unchanged Contract

- Online IMAP processing, header semantics, first-reply locking, five-minute/account/outbound proof, and duplicate-header behavior do not change.
- The historical script remains explicit `scan` → reviewed manifest → `apply` → optional `rollback`; normal invocation remains read-only.
- Reclassification remains restricted to qualifying first replies and writes only `process_status`, `process_reason`, `reason_type`, and `updated_at`.
- Protected fallback rows remain `MANUAL_REVIEW` with their original reason/type/timestamp/body; no fallback is converted to the auto-response outcome.
- No frontend, schema, migration, cache-key, or unrelated test change.

## Authorized Files

| File | Purpose |
|---|---|
| `scripts/reclassify-auto-responses.py` | Enforce protected-reason exclusion during scan and before any apply write. |
| `scripts/test_reclassify_auto_responses.py` | Focused standard-library regression coverage for scan and apply protected-reason behavior. |

## Repair Tasks

### R-1: Protect historical fallback rows at both reclassification gates

- Resolves: V-1.
- Root cause: Candidate selection and manifest application lack the master-required protected-reason predicate.
- Files: `scripts/reclassify-auto-responses.py`, `scripts/test_reclassify_auto_responses.py`.
- Change: Ensure each of the five protected fallback reasons is excluded before first-reply proof during scan, and a crafted/stale reviewed manifest cannot cause apply to update such a current row. Preserve the existing conflict/skip and audit semantics for all other rows.
- Regression test: Prove scan omits every protected reason while retaining an otherwise eligible ordinary manual-review row; prove apply records a protected current row as skipped and performs no update, even when its manifest snapshot otherwise matches.
- Existing verification: `python3 -m unittest scripts/test_reclassify_auto_responses.py`; `python3 -m py_compile scripts/reclassify-auto-responses.py`.
- Must not change: `scan` default safety, manifest schema/rule version, first-reply proof criteria, apply/rollback conditional-write semantics, or any non-authorized file.
- Prohibited: Broad reason-policy changes, migrations, automatic execution, frontend changes, and changing online ingestion behavior.

## Verification Commands

1. `python3 -m unittest scripts/test_reclassify_auto_responses.py`
2. `python3 -m py_compile scripts/reclassify-auto-responses.py`
3. `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH mvn test`
4. `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home PATH=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin:$PATH mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT` — recognize only the recorded baseline `dismissed_at` no-default error; no new failure.
5. `node --check src/main/resources/static/mailbox-chat.js`
6. `node --check src/main/resources/static/app.js`
7. `node --test src/test/js/*.test.js`

## Completion Criteria

- Each protected reason is absent from scan candidates before proof and cannot be updated by apply.
- Focused regression and compilation pass; all aggregate commands retain their documented result, with no new failures.
- Changed product/test files are exactly the Authorized Files.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/auto-response-exclusion/repair.md` invocation authorizes:

1. Only `scripts/reclassify-auto-responses.py` and `scripts/test_reclassify_auto_responses.py`, and the required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before `READY_FOR_VERIFICATION`, staging only those Authorized Files, with subject `fix(mail): preserve protected historical auto-response rows`.
3. Appending `docs/plans/review/auto-response-exclusion/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/auto-response-exclusion/repair-execution.md`, with subject `docs(review-fast-p): record repair execution`.
5. Returning to aggregate re-review in the same task when requested, via `$review-fast-p docs/plans/fast/auto-response-exclusion/human-review-handoff.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
