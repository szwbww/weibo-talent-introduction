# Repair Plan: batch-send-rhythm-and-filter-00-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-12/batch-send-rhythm-and-filter-00-master.md
Verification report: aggregate review of boundary a6c27bbbca02a3b018d8a16aeb11822abd905e19..c6a02f84eba853aea5484b7ec102edddd85f5138 (evidence HEAD a9c27009b6bdcc7f61f9542fe435fa77077e98cd)
Implementation boundary: a6c27bbbca02a3b018d8a16aeb11822abd905e19..c6a02f84eba853aea5484b7ec102edddd85f5138

## Objective

Manual execution sourced from a configured task preserves and displays its configured execution-round count, never `undefined`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | 04b A-4 requires the source summary and manual diff to use `source.roundsPerRun`. | `deepCloneConfig()` drops `roundsPerRun`, so both `manualSource` and `manualDraft` lose it before the summary/diff reads it. |

## Findings Excluded

| Finding | Reason |
|---|---|
| B-1 | Flyway migration integration evidence is blocked by an unavailable Docker daemon; it is not a product repair. |
| O-1..O-2, R-1..R-4 | Re-evaluated RECORD_ONLY observations without a confirmed mandatory violation, except V-1. |

## Unchanged Contract

- English region values remain the API/DB/ES domain constants; Chinese remains display-only.
- `dailyCap` stays removed from the new configuration and manual-task UI; the legacy KV compatibility layer remains untouched.
- Manual source/baseline/draft identity and existing diff behavior remain intact.
- No backend, migration, HTML, CSS, or scheduler behavior changes.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/app.js | Preserve `roundsPerRun` while cloning a selected configuration and in the independent manual default. |
| src/test/js/batchSendTaskConsoleInteraction.test.js | Prove a selected source task retains its round count in source/draft/confirmation behavior. |

## Repair Tasks

### R-1: Preserve execution-round count in manual source state

- Resolves: V-1
- Root cause: `deepCloneConfig()` copies `roundSize` but not `roundsPerRun`; `applyBatchManualSource()` uses that clone for the values later rendered by the confirmation summary.
- Files: `src/main/resources/static/app.js`, `src/test/js/batchSendTaskConsoleInteraction.test.js`
- Change: Copy `roundsPerRun` from the selected config with the safe existing default of `1`; include the same default in independent manual-draft initialization.
- Regression test: Select a source config with `roundsPerRun: 2`, then assert source/draft retain `2` and the confirmation summary renders `轮次: 2` rather than `undefined`.
- Existing verification: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js`; `node --check src/main/resources/static/app.js`.
- Must not change: existing source selection, baseline/draft separation, template, filters, interval unit conversions, region payload values, or legacy KV functions.
- Prohibited: changes to Kotlin, migrations, `index.html`, `styles.css`, daily-cap compatibility APIs, or unrelated manual-tab cleanup.

## Verification Commands

1. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js`
2. `node --check src/main/resources/static/app.js`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
5. Before PASS, with Docker available: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`

## Completion Criteria

- A configuration with `roundsPerRun: 2` produces manual source and draft values of `2` and a confirmation text of `轮次: 2 轮`.
- The focused JS test and syntax check pass.
- All changed files remain in the authorized list.
- The full Maven gates pass; Flyway evidence is collected when Docker is available.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/batch-send-rhythm-and-filter-00-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with subject `fix: preserve manual batch execution rounds`.
3. Appending `docs/plans/review/batch-send-rhythm-and-filter/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs: record batch send aggregate repair execution`.
5. Returning to the already authorized `$review-fast-p docs/plans/fast/batch-send-rhythm-and-filter/human-review-handoff.md` aggregate re-review in the same task when the human invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
