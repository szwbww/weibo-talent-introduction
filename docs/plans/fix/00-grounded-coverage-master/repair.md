# Repair Plan: 00-grounded-coverage-master

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/00-grounded-coverage-master.md` (sha256 `3112ffe1c665ebda5295e36a315a5bf65f3a09082febf11ce89bec1ba854d4e7`)

Verification report: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/review/grounded-coverage/machine-verification.md`, Epoch 1, `FAIL / INITIAL`

Implementation boundary: `af1723f37021328f8ffa61261504727e514fbb4b..8c2ec53f4e97d06acb89b81bfb5a388a9d49a566`

## Objective

Make the approved verbatim orthopaedic trigger letter bind production `Funding support` for `finance.arrangements`, so all five recognised intents are `SUPPORTED` and grounded coverage is 5/5.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | Mandatory | P1 observable outcome/N3: the verbatim letter binds `Funding support` for `finance.arrangements` and reaches 5/5 supported intents/facts. | Production Funding support keywords omit the letter's `remuneration`; the regression test injects that unmigrated keyword and therefore masks production state. |

Classification: `REPAIRABLE`. The user explicitly approved a bounded new-migration repair and production-faithful regression test. The report's convergence is `INITIAL`, so the convergence breaker permits repair design.

## Findings Excluded

| Finding | Reason |
|---|---|
| M-7 / `mvn clean package` WAR deletion failure | `BLOCKED` transient-environment evidence; not attributed to product code and explicitly excluded. |
| Child-01 O-1/O-3/O-4/O-5 | `RECORD_ONLY`; no confirmed mandatory violation beyond V-1. |
| Child-02 O-1/O-3 | `RECORD_ONLY`; speculative observations outside approved repair scope. |
| Child-02 O-2 | `RESOLVED/RATIFIED`; no repair remains. |
| Child-03 items | `RECORD_ONLY`/N/A; no repair remains. |

## Unchanged Contract

- Preserve P1's verbatim trigger-letter fixture; do not paraphrase, truncate, or replace it.
- Preserve the existing `finance.arrangements` binding: its evidence is `Funding support` only; preserve the `ip.arrangements` binding to `Pre-contract IP boundary`.
- Do not modify V3–V105, catalogs, intents, coverage keys, fact bodies, categories, priorities, enablement, reply policies, or P2/P3 behavior.
- The migration must append only `remuneration` to the existing `Funding support` keywords, conditionally and without changing `updated_at`, so runtime-added keywords are preserved.
- No production, test, configuration, review evidence, staging, or commit action is authorized before explicit approval of this exact repair plan.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql` | New forward-only migration that conditionally appends the missing production keyword to `Funding support`. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | Production-faithful V-1 regression coverage for the verbatim trigger letter. |
| `docs/plans/review/grounded-coverage/repair-execution.md` | Post-execution handoff evidence only; appended after the product commit and required commands pass. |

## Repair Tasks

### R-1: Align Funding support production keywords with the approved trigger letter

- Resolves: V-1.
- Root cause: V3 plus V81 leave `Funding support` without `remuneration`, while the current test independently injects it into an in-memory rule.
- Files: `src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`.
- Change: add V106 only. Target `Funding support` only; append `remuneration` only when absent, retain all existing keywords and set `updated_at = updated_at`. Do not alter any earlier migration. Replace the masking fixture setup with the exact post-V106 Funding support keyword state; no test may supply `remuneration` through an unmigrated, test-only keyword injection.
- Regression test: use P1's exact verbatim orthopaedic letter. Prove `finance.arrangements` is supported only by `Funding support`, all five recognised intents are `SUPPORTED`, five facts bind, and the overall status is `GROUNDED`. Also assert V106 targets Funding support, conditionally appends `remuneration`, and preserves `updated_at`; this ties the fixture's keyword to the production migration rather than a detached test override.
- Existing verification: run the focused `QaFactSelectionServiceTest`, the full `mvn test` suite, and the boundary diff hygiene command below.
- Must not change: intent recognition/count (five), non-finance fact bindings, P1's other V105 effects, or any existing migration.
- Prohibited: edits outside Authorized Files; broad compensation/salary keyword changes; changing reply/answer bodies or coverage keys; adding new facts, intents, coverage keys, test matrices, Docker-dependent migration tests, or workarounds for the blocked WAR deletion.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `git diff --check af1723f37021328f8ffa61261504727e514fbb4b HEAD`

`mvn clean package` is not a repair completion command: Epoch 1 proved its failure is the excluded transient WAR-deletion environment condition. The subsequent aggregate re-review must record fresh evidence for its own required gate.

## Completion Criteria

- V106 is a new forward-only migration; V3–V105 are byte-for-byte unchanged.
- Production keyword state for `Funding support` includes `remuneration` exactly once, preserves existing keywords and `updated_at`, and does not target another rule.
- The production-faithful regression proves the exact P1 letter reaches five recognised, five supported intents, five bound facts, `GROUNDED`, and `Funding support` as the sole finance evidence.
- Verification Commands 1–3 pass.
- Changed files remain inside the Authorized Files list; product commit contains only the two product/test files and evidence commit contains only `repair-execution.md`.
- Executor returns to the already-authorized aggregate `review-fast-p` re-review; no acceptance or integration conclusion is made by this repair.

## Human Approval

Execution is prohibited until a human explicitly invokes `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only `src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql` and `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`, with subject `fix(qa): support remuneration in funding facts`.
3. Appending `docs/plans/review/grounded-coverage/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/grounded-coverage/repair-execution.md`, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
