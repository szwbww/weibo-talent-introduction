# Repair Plan: discovered-institution-repair-00-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-28/discovered-institution-repair-00-master.md
Verification report: aggregate verify-p report, 2026-09-28
Implementation boundary: d90084841d400e75eb0f2b6c4c6726e54307260a..70e6144065335beee72dbd22a84e4bb975a68928 (evidence HEAD f455045ef153b047baa5c60b0a4cbd2a691c8019)

## Objective

Academic enrichment preserves the root `updatedAt` field while retaining the approved academic-field updates and institution-evidence safeguards.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master I-3: academic enrichment must not modify root `updatedAt`. | `updateExpertAcademicFields` includes `updatedAt` in its partial-update document. |

## Findings Excluded

| Finding | Reason |
|---|---|
| RECORD_ONLY observations | Non-mandatory documentation, test-depth, or existing-backlog items; no approved repair authority. |

## Unchanged Contract

- I-1: institution facts remain source-bound and conservative.
- I-2: discovery outreach remains identity-, evidence-, and `PASSED`-gated; non-discovery behavior remains unchanged.
- I-3: academic enrichment must continue not to overwrite institution type or institution evidence, change operations/history, or create absent layers.
- No mapping migration, historical backfill, outreach-policy change, or unrelated cleanup.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | Remove the root `updatedAt` write from the academic-enrichment update document. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | Add a discriminating assertion that each academic-enrichment update payload omits root `updatedAt` while preserving approved academic writes. |

## Repair Tasks

### R-1: Preserve root update timestamp during academic enrichment

- Resolves: V-1.
- Root cause: `updateExpertAcademicFields` places `updatedAt` in `doc` before sending all layer partial updates.
- Files: exactly the two Authorized Files above.
- Change: omit `updatedAt` from the partial-update document; retain `enrichedAt`, allowed academic fields, identity guard, classification recomputation, and per-layer behavior.
- Regression test: exercise academic enrichment and assert the sent update document lacks `updatedAt`, retains `enrichedAt` and a supplied academic field, and does not include `institutionType` or `institutionEvidence`.
- Existing verification: rerun the targeted discovery suite and the full Maven suite.
- Must not change: discovery identity fields, operational state, historical mail, mapping, promotion behavior, or outreach paths.
- Prohibited: historical data mutation, `PUT _mapping`, changes outside the authorized files, and test-only weakening.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `git diff --check`

## Completion Criteria

- The academic-enrichment update payload never includes root `updatedAt`.
- The discriminating regression test proves the omission and preserved allowed fields.
- Both Maven commands pass with zero failures/errors.
- Changed files remain inside the authorized list.

## Review-Fast-P Execution Handoff

- Approval authority: one explicit human approval of this exact artifact via `$execute-p docs/plans/fix/discovered-institution-repair-00-master/repair.md`. It authorizes only R-1 and the Authorized Files; no amendment, scope expansion, or second repair round.
- Product execution: make exactly one local product commit, and only after all Verification Commands pass. Subject: `fix(discovery): preserve root updatedAt during academic enrichment`.
- Durable execution evidence: write exactly `docs/plans/review/2026-09-28-discovered-institution-repair-00-master/repair-execution.md` after the product commit. Record the verified product commit, exact command exit codes/counts, changed files, V-1 resolution evidence, and clean product/index status.
- Evidence finalization: make exactly one docs-only evidence commit after the durable handoff. Subject: `docs(review-fast-p): record repair execution`. It contains only the handoff evidence and no product or test changes.
- Return: remain in this task and return the completed execution handoff to aggregate re-review. Do not invoke another repair plan, create a PR, merge, push, or change HEAD outside the two stated commits.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
