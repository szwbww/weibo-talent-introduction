# Repair Plan: provider-undelivered-column

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-02/provider-undelivered-column.md
Verification report: review-p verification, 2026-09-02
Implementation boundary: `a409cb9` to the current working-tree diff restricted to the baseline plan's 10 changed files

## Objective

When the provider table has rows and at least one unattributed bounce, render the required unattributed-bounce footer after the data rows.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | O-2, I-4, S-2 | `dataRows || emptyRow + footerRow` binds so a non-empty `dataRows` value discards `footerRow`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| SQL integration-test evidence | Docker is unavailable; this is an evidence boundary, not a confirmed product defect. |

## Unchanged Contract

- Keep the exact O-2 footer text, `colspan="7"`, class, and existing inline alignment style.
- Keep the seven-column header, all data-cell behavior, empty-state behavior, and the zero-unattributed case unchanged.
- Do not change back-end queries, API DTOs, styles, cache keys, or any monitoring behavior outside this footer composition.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/app.js | Compose data, empty-state, and footer rows so the footer appears after present data rows. |
| src/test/js/monitoringDateDefault.test.js | Add a discriminating DOM-level regression test for rows plus a positive unattributed-bounce count. |

## Repair Tasks

### R-1: Always append the unattributed-bounce footer

- Resolves: V-1
- Root cause: JavaScript `||` short-circuiting returns non-empty `dataRows` before evaluating the concatenated footer expression.
- Files: `src/main/resources/static/app.js`; `src/test/js/monitoringDateDefault.test.js`
- Change: Render the existing footer after data rows whenever `unattributedBounceCount > 0`; preserve the existing empty row when no data rows exist.
- Regression test: Set provider rows non-empty and `unattributedBounceCount` positive; assert the table body contains both the data row and the exact footer text after it. Also retain coverage that a zero count renders no footer.
- Existing verification: `node --test src/test/js/monitoringDateDefault.test.js`; `node --test src/test/js/*.test.js`; `node --check src/main/resources/static/app.js`; `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`.
- Must not change: O-1, O-3, I-1 through I-9, N-1 through N-6, S-1 through S-4.
- Prohibited: Product-code changes outside `app.js`; CSS changes; back-end changes; cache-key changes; test-only masking of the behavior.

## Verification Commands

1. `node --test src/test/js/monitoringDateDefault.test.js`
2. `node --test src/test/js/*.test.js`
3. `node --check src/main/resources/static/app.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordRepositoryMonitoringIT -DmigrationIt=true` (requires Docker)
6. `git diff --check`

## Completion Criteria

- With non-empty provider rows and a positive unattributed count, the body contains the exact required footer after the data rows.
- With a zero unattributed count, the footer is absent.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
