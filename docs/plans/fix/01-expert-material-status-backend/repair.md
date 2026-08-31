# Repair Plan: 01-expert-material-status-backend

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-31/01-expert-material-status-backend.md
Verification report: review-p re-verification dated 2026-08-31 (V-2, V-3)
Implementation boundary: uncommitted working-tree implementation for the baseline plan (includes the already-executed V-1 change in `IntroductionMailComposerTest.kt`)

## Objective

Close the two confirmed mandatory-requirement misses: the I1-1 acceptance grep hits new feature code, and one test file was modified outside the authorized file list. No product behavior changes.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P2 | I1-1 acceptance: `rg "Chinese institutions\|合作证明"` must not hit new feature code | `ExpertMaterialService.kt:12` doc comment names the eighth item (“中国机构合作证明”), so the plan's own acceptance grep hits the new file. The catalog itself is exactly 7 items; only the comment matches. |
| V-3 | P2 | File-scope clause: no changes outside the 10-file table plus repair-authorized `IntroductionMailComposerTest.kt` | The in-scope controller additions (`materials` GET/PUT endpoints, `UpdateExpertMaterialStatusRequest` DTO, +15 lines before :549) force the `OperatorStatusWriteSeamGuardTest.kt` noise-site line pin to shift 549→564; the mechanical update was applied without authorization. Reverting it would false-fail the guard test. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Already resolved in the working tree: `IntroductionMailComposerTest.kt:69` contains `"pendingExpertMaterials" to ""`; `IntroductionMailComposerTest` passes. |
| I1-9 Flyway runtime evidence | Docker is unavailable; blocked evidence, not a repairable implementation defect. |

## Unchanged Contract

- `pendingExpertMaterials` remains empty without an `expert_contact` context; the fixed seven-item catalog, its order, and its status semantics do not change.
- No product behavior, mail templates, meeting render paths, migrations, or frontend files change.
- The V-2 repair touches only a doc comment in `ExpertMaterialService.kt` — never the enum values, `requestText`, API, database, or variable output.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt | Comment rewording only (V-2) |
| src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | Retain the already-applied 549→564 noise-site pin update; scope authorization (V-3) |

## Repair Tasks

### R-1: Remove the eighth-item name from the catalog doc comment

- Resolves: V-2
- Root cause: the `ExpertMaterialCode` doc comment quotes the eighth item's Chinese name, so the plan's I1-1 acceptance grep (`rg "Chinese institutions|合作证明"`) matches new feature code even though no eighth item exists in the catalog, API, database, or variable.
- Files: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertMaterialService.kt`
- Change: reword the comment on `enum class ExpertMaterialCode` so it no longer contains `Chinese institutions` or `合作证明` (e.g. drop the parenthetical eighth-item name, keep the statement that the eighth item is excluded). No other line changes.
- Regression test: `rg "Chinese institutions|合作证明" src/main/kotlin` returns zero hits after the change; existing `MailVariableServiceTest` assertions on the 7-item catalog remain the behavioral proof.
- Existing verification: focused suite, then full `mvn test`.
- Must not change: enum values, `label`, `requestText`, `listMaterials`, `updateStatus`, `renderPendingMaterials`, or any runtime path.
- Prohibited: touching any file other than the comment line in `ExpertMaterialService.kt`.

### R-2: Authorize the guard-test line-pin update

- Resolves: V-3
- Root cause: the baseline file table omitted a test file that the in-scope controller change necessarily shifts. `OperatorStatusWriteSeamGuardTest.kt` pins noise-site line 549 in `ExpertContactManagementController.kt`; the plan-01 endpoint/DTO additions moved the site to :564, so the pin had to follow or the guard test would false-fail on the whole `mvn test`.
- Files: `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- Change: none — retain the already-applied pin update (`NoiseSite(...ExpertContactManagementController.kt, 564, "operatorStatus = operatorStatus")`) and its plan-01 attribution comment. This is a scope authorization, not a new behavior.
- Regression test: `OperatorStatusWriteSeamGuardTest` passes in the full suite with the controller at its current line layout.
- Existing verification: focused suite (includes the controller test), then full `mvn test`.
- Must not change: the exclusion list content or semantics — only the line number and its attribution comment.
- Prohibited: reverting the pin (would break the suite) or expanding the authorized file set further.

## Verification Commands

1. `rg "Chinese institutions|合作证明" src/main/kotlin` — exit 1 (no matches) after R-1
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -q -DskipNodeTests=true -Dtest=MailVariableServiceTest,ExpertContactManagementControllerTest,ManualExpertMailServiceTest,IntroductionMailComposerTest,MeetingScheduleServiceTest,OperatorStatusWriteSeamGuardTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

## Completion Criteria

- `rg "Chinese institutions|合作证明" src/main/kotlin` returns no hits.
- `OperatorStatusWriteSeamGuardTest` and the focused suite pass; full `mvn test` is green.
- Changed files remain inside the authorized list (baseline 10 files + `IntroductionMailComposerTest.kt` + the two files above).
- The seven-item catalog, its order, and all material-status semantics are unchanged.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
