# Repair Plan: trust-reply-atomic-facts-and-duplicate-guard

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-08-04/trust-reply-atomic-facts-and-duplicate-guard.md`
Verification report: `review-p / verify-p 2026-08-04, V-1`
Implementation boundary: `c154b54011914768ecfa262b22f91720436c714d` → current working-tree diff (including untracked `V82__split_trust_reply_atomic_facts.sql`)

## Objective

An invalid persisted controlled-coverage rule can always be disabled, while enabling that same rule still fails before `repository.save`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-5: `setRuleEnabled(..., true)` revalidates controlled rules; disable adds no validation | `setRuleEnabled` calls `validateControlledBody` unconditionally, so an invalid controlled rule cannot be disabled. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Prior V82 predicate-repair topic | The current boundary already contains audited IDs and keyword predicates in the pre-write gate and both guarded disables; no current violation was found. |
| A-1 through A-7 | Manual acceptance remains pending; no machine-verifiable failure was found. |

## Unchanged Contract

- Enable of a controlled coverage group must retain the current exact canonical-body validation and must not call `repository.save` on failure.
- Create and update coverage persistence, the V82 migration, coverage-based selection, claims, assembly ordering, and composer behavior remain unchanged.
- No frontend, schema, production-data, historical-migration, or non-QA-management changes are authorized.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | Restrict controlled-body revalidation in `setRuleEnabled` to transitions that enable a rule. |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | Prove invalid controlled rules can be disabled and remain blocked on enable without saving. |

## Repair Tasks

### R-1: Revalidate controlled bodies only when enabling

- Resolves: V-1.
- Root cause: `QaRuleManagementService.setRuleEnabled` lines 134-142 validates every toggle rather than the `enabled=true` path only.
- Files: the two authorized files above.
- Change: apply `QaCoverageKeyCatalog.validateControlledBody` only when the requested state is enabled; preserve the existing validation and fail-before-save behavior for enable.
- Regression test: use a persisted `fees.policy` rule with a noncanonical body; disabling returns the disabled rule and saves it, while enabling throws `IllegalArgumentException` and makes no additional `save` call.
- Existing verification: rerun `QaRuleManagementServiceTest`, `git diff --check`, and the complete Maven suite.
- Must not change: create/update controlled-body validation, coverage serialization, reply-body synchronization, or noncontrolled legacy rule handling.
- Prohibited: do not normalize or rewrite an invalid persisted body during disable, loosen enable validation, or alter V82 bodies/coverage.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=QaRuleManagementServiceTest test`
2. `git diff --check`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

## Completion Criteria

- A malformed persisted controlled rule can be disabled with one save and without body mutation.
- Enabling that rule throws `IllegalArgumentException` before an additional save.
- A valid controlled rule still enables normally.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
