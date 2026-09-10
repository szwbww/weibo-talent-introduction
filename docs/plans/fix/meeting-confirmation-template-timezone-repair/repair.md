# Repair Plan: meeting-confirmation-template-timezone-repair

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-10/meeting-confirmation-template-timezone-repair.md
Verification report: review-p / V-1 (2026-09-10)
Implementation boundary: HEAD `3e83d80` → current worktree. The plan-associated implementation is in the nine listed files; an additional cache-key change in `index.html` and `meetingConfirmationAssets.test.js` is recorded as excluded V-2. This repair changes only the two files below.

## Objective

Ensure the meeting template render map is exactly the `MailVariableService.buildVariables(...)` result plus only `meeting_time` and `zoom_url`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-2: the generic-variable map may only add/override `meeting_time` and `zoom_url`. | `meetingTemplateVariables` additionally injects `senderDisplayName`, which `buildVariables` does not produce. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-2 | P2 scope mismatch: `index.html` and `meetingConfirmationAssets.test.js` revise the nine shared static-asset cache keys, but neither file is in the approved change list. The baseline has no mandatory acceptance rule requiring this cache-key value, and no human approval was supplied to expand scope. |

## Unchanged Contract

- Continue resolving expert profile then calling `buildVariables(account, expert, contact.expertEmail, false, contact)`.
- Keep the sole body source as enabled `MEETING_INVITATION` through `renderByCode`; preserve reply snippets, custom text, and existing values supplied by the generic map.
- Do not modify `MailVariableService`, `MailComposeTemplateService`, `PendingMailOperationService`, frontend files, CSS, migrations, or template records.
- Preserve DTO compatibility, preview/send rebuild SHA and body consistency validation, and all time-zone behavior.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt | Remove the unauthorized `senderDisplayName` render-map entry and related rationale. |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt | Replace the out-of-contract `senderDisplayName` expectation with a discriminating assertion that the render call receives only generic variables plus the two meeting variables. |

## Repair Tasks

### R-1: Restrict meeting-template variable additions to the approved pair

- Resolves: V-1.
- Root cause: `MeetingConfirmationService.kt:236-240` combines the generic map with three entries, despite I-2 limiting additions to two.
- Files: the two Authorized Files above.
- Change: remove `senderDisplayName` from the overlay map. Keep only `meeting_time` and `zoom_url`; do not change the preceding generic-variable call or template renderer.
- Regression test: capture the map passed to `renderByCode` and prove it contains generic-map keys plus the two meeting keys, has neither a `senderDisplayName` entry nor another meeting-specific key, and still renders reply snippet/custom text with `${expertFamilyName|Colleague}`, `${meeting_time}`, and `${zoom_url}`.
- Existing verification: rerun the meeting service, controller, and pending-send JVM tests, plus required frontend checks.
- Must not change: generic template block rendering, error translation, ICS construction, or all existing non-meeting variable behavior.
- Prohibited: adding `senderDisplayName` to `MailVariableService`, registering any new global template variable, altering stored templates, or relaxing unresolved-placeholder validation.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,PendingMailOperationServiceTest`
2. `node --check src/main/resources/static/meeting-confirmation.js`
3. `node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/meetingConfirmationStyle.test.js`
4. `node --test src/test/js/*.test.js`

## Completion Criteria

- The render map contains no key outside the generic map except `meeting_time` and `zoom_url`.
- V-1's discriminating regression test passes with the required JVM suite.
- All four verification commands pass.
- Changed files remain inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.
