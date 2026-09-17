# Repair Plan: meeting-mail-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-16/meeting-mail-master.md
Verification report: aggregate/master review, epoch 1
Implementation boundary: 24f5c8205a304d3682e09e02458960bc2caa0463..ae5d947b7257bf714e1d70e93dafbaf1894cbff6

## Objective

Make SQL `NULL` the sole no-outbound-attachment representation; reject every non-null blank snapshot as corrupted data.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Child 05 I-1/I-5: `outbound_attachments_json` absence is only SQL NULL; non-null snapshots must be valid, and damaged JSON must be rejected. | `parseOrThrow` uses `isNullOrBlank`, conflating blank persisted data with NULL; its unit test codifies this behavior. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Pre-existing Flyway V124 FK failure | Outside implementation boundary and repair scope; no master-code regression attribution. |
| Child RECORD_ONLY UI/test-depth/configuration observations | Not confirmed mandatory violations requiring this repair. |

## Unchanged Contract

- Valid ordered snapshots, SMTP bytes, message download authorization, and SENT-only projection remain unchanged.
- `parseOrNull` may keep its presentation-safe `null` result for malformed stored values; strict send/retry parsing must distinguish NULL from non-null corruption.
- No migration, schema, controller, API, UI, attachment storage, hash, or MIME change.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentModels.kt | Make strict snapshot parsing accept only null as absence and reject blank non-null values. |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt | Replace the incorrect blank-as-null expectation with discriminating conflict assertions. |

## Repair Tasks

### R-1: Reject blank persisted snapshots

- Resolves: V-1.
- Root cause: `OutboundAttachmentSnapshotCodec.parseOrThrow` currently returns null for `""` and whitespace at `OutboundAttachmentModels.kt:165-168`; `OutboundAttachmentServiceTest.kt:467-469` asserts that defect.
- Files: exactly the two Authorized Files.
- Change: retain null → null; make empty/whitespace non-null input fail strict parsing with `OutboundAttachmentException` HTTP 409, while presentation parsing continues to return null by catching that strict failure.
- Regression test: assert strict parsing rejects `""` and whitespace as conflict and `parseOrNull` remains null for them; retain valid snapshot and actual-null assertions.
- Existing verification: rerun focused codec/service tests and affected message-flow tests.
- Must not change: snapshot schema v1, valid JSON behavior, storage shape, API contracts, or any product file outside this list.
- Prohibited: normalizing or repairing stored blank data, adding a migration/DB check, changing `parseOrNull` presentation semantics, and broad refactors.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=OutboundAttachmentServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=ManualReplySendAttemptServiceTest,OutboundAttachmentFlowTest,MailboxConversationControllerTest -DmysqlIt=true -Dapi.version=1.44 -DfailIfNoTests=false`
3. `node --test src/test/js/*.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH DOCKER_API_VERSION=1.44 mvn test`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH DOCKER_API_VERSION=1.44 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44`

## Completion Criteria

- Null is accepted solely as absence; every non-null blank snapshot produces HTTP 409 in strict parsing.
- A regression test proves both empty-string and whitespace behavior.
- All changed files are exactly the Authorized Files.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/meeting-mail-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with subject `fix(meeting-mail): reject blank outbound attachment snapshots`.
3. Appending `docs/plans/review/meeting-mail-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
