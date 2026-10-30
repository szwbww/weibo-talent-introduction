# Repair Plan: draft-review-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-10-29/draft-review-master.md`, governing commit `32407a46d5b05971fc913cda1802b6ba002e8dc7`, SHA256 `a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b`; ordered child plans and recorded A1–A18 amendments remain authoritative.
Verification report: initial aggregate/master review returned by `DraftReviewAggregateReviewer3`, finding `V-1`, FAIL / INITIAL; controller destination `docs/plans/review/draft-review-master/machine-verification.md` (initial review epoch). This plan references that returned report, not a historical child LIGHT_PASS or interrupted reviewer attempt.
Implementation boundary: `b15bb069f43f89c45ee6859dba04a0baff5d774d..bec9e40faae946b0971e95af9c3a9edec83cd888`; evidence HEAD `d309757a23bfffd5c0086ce1682aa638dd7f851e`.
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`, branch `fast/draft-review-master`.
Prior aggregate repair: N/A. Human approval of this repair: NOT RECORDED.

## Objective

Approval and explicit context confirmation must reject an administrator's old observed token when a conversation change committed before their authoritative post-lock context check, including when the request waited for the draft row lock. Return `409 REVIEW_CONTEXT_CHANGED` without recording an approval or confirmation. Preserve the existing explicit-send boundary.

## Findings in Scope

| Finding | Severity | Requirement | Root cause | Classification |
|---|---|---|---|---|
| V-1 | P1 | Child01 I-6 and T3; context/approve/confirm-context applicability; child03 I-12 consumes confirmations | `MailReviewDraftService.access` performs a consistent read before its locking read. Default MySQL REPEATABLE READ retains that earlier snapshot for `matchingContext` after the lock wait. Both `approve` and `confirmContext` therefore accept a token made stale by an independently committed mail change before lock release. | REPAIRABLE; one shared transaction/read-consistency root cause |

### Verification evidence

- `MailReviewDraftService.kt:110–118,154–161,234–246,325–330`; `MailReviewDraftRepository.kt:28–30`; `MailboxConversationRepository.kt:33–69`.
- Fresh production HTTP / real MySQL diagnostics: `/tmp/draft-review-aggregate-reviewer3/fresh-approval-lock-race.json` and `fresh-confirmation-lock-race.json`. Each records an actual `performance_schema.data_lock_waits` wait, an independently committed new token visible before unlocking, and an incorrect HTTP200 carrying the old token afterward.
- Approval incorrectly changed draft1 from SUBMITTED/version6 to APPROVED/version7; confirmation incorrectly appended event13. The correctly isolated send seam still rejects stale confirmation, so this finding is not a demonstrated SMTP bypass.

## Findings Excluded

| Item | Reason |
|---|---|
| Historical child05 F-01 retarget race | Resolved in the reviewed code and fresh regression suites; not reopened. |
| Historical child05 O-01 Discovery thread-state assertion | Explicitly adjudicated by A17/A18; fresh full Maven passes. No Discovery product/test work authorized here. |
| Initial C11/C12 external database connection failures | Environment-only attempts preserved in the report; corrected explicit disposable DB runs pass. No product repair. |
| Saved, clean reader editor caret on focus refresh | Non-blocking observation; I-17 specifically protects dirty editing, whose native caret is freshly proven preserved. No task. |
| Human acceptance | Remains PENDING; machine/browser evidence does not constitute human approval. |

## Unchanged Contract

- Preserve I-1–I-24, all recorded amendments, exact identities/role checks, grant-before-draft-lock ordering, CAS versions, frozen canonical content, append-only transactional history, and server-authoritative target ownership.
- Approval and confirmation never send; confirmation still requires a separate explicit send click. Do not strengthen this into locking all mail writes until SMTP.
- Preserve `ManualReplySendAttemptService.prepareAndClaim` and its existing READ_COMMITTED/send-time context check, fixed request identity, fingerprint bytes for ordinary sends, and SENT/IN_PROGRESS/UNKNOWN handling.
- Preserve ordinary manual/AI/QA/attachment/meeting/suppression/safety paths and all UI behavior. No UI, schema, migration, framework, global configuration or historical data changes.
- A concurrent mail change after the authoritative context check is not an expanded atomic-SMTP requirement; the existing send-time check remains the later independent boundary.

## Authorized Files

Only these product/test files may change and enter the product repair commit:

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | Make the existing approval and confirmation authoritative context reads fresh after draft-lock contention, using local transaction/read semantics. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt` | Add real concurrent-transaction regressions for both failing operations in the existing suite. |

Separately authorized evidence-only output, after successful execution: `docs/plans/review/draft-review-master/repair-execution.md`, append-only and committed by itself as specified below. The repair plan itself is not an execution-time product-edit authorization.

## Repair Tasks

### R-1: Read committed conversation context after obtaining the draft lock

- Resolves: V-1, both approval and confirmation manifestations.
- Root cause: A pre-lock consistent read establishes an old REPEATABLE_READ snapshot; the subsequent `FOR UPDATE` does not refresh later ordinary context SELECTs.
- Files: the two product/test paths in Authorized Files.
- Change: Ensure both approval and confirm-context compare the caller's observed token with conversation data committed before the authoritative post-lock read. Prefer the existing service-local transaction-isolation convention where sufficient; do not change global isolation or add locks/hooks to every mail write. Keep current role/access/CAS/event behavior and transaction atomicity.
- Regression: In the existing real-MySQL IT, hold the draft row lock in one transaction; start the production service operation in another transaction and synchronize so its original read has happened and it is actually waiting; independently commit a new inbound row or an existing mail-body revision; prove that a separate read observes the new token before unlocking. Release the draft lock. Assert old-token approval and old-token confirmation each return `409 REVIEW_CONTEXT_CHANGED`. Assert rejected approval leaves state/version/review fields unchanged and adds no APPROVE event; rejected confirmation adds no CONTEXT_CONFIRMED event and changes no version. Refresh the token and prove the respective operation succeeds. Assert SMTP remains zero. Reuse existing concurrency/transaction helpers and mocks; do not rely on JVM Thread.state or unbounded sleeps as correctness evidence.
- Existing verification: All commands below, including the complete full Maven and enabled MySQL/auth/migration/send targets, not only a narrowed regression.
- Must not change: ordinary send semantics, send-time isolation, approval state machine, read authorization, event payload contract, canonical content or target fields.
- Prohibited: new classes, states, interfaces, migrations, global datasource isolation, shared-table fields, production mail-write hooks, test suppression, assertion retirement, unrelated cleanup, or executing any repair before human approval.

## Verification Commands

Run sequentially from the exact worktree; normal production/test compilation must remain enabled. Use:

`JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home MAVEN_OPTS=-Xmx3072m DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`

Use an independently owned disposable real MySQL for the external DB suites, not an unrelated or deleted reviewer database. A concrete isolated fixture is container `draft-review-master-repair-mysql`, loopback port33416, database `draft_review_repair`, root password `repair-local`; require readiness and pass `DB_URL='jdbc:mysql://127.0.0.1:33416/draft_review_repair?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=repair-local` to the external Repository/Controller/Send groups. If the port/name is occupied, do not stop its owner; obtain an unused owned local endpoint and record the actual environment. Testcontainers suites keep their normal own databases. Stop only executor-owned fixtures after completion.

First run the focused existing suite:

1. `mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`

Then run the complete required aggregate command set:

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --check src/main/resources/static/app.js`
3. `node --check src/main/resources/static/mailbox-readonly.js`
4. `node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js`
5. `node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js`
6. `node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js`
7. `node --test src/test/js/*.test.js`
8. `mvn test '-DargLine=-Xmx1536m -Dapi.version=1.41'`
9. `mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`
10. `mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`
11. `mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`
12. `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`
13. `mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'`
14. `mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest '-DargLine=-Xmx1536m -Dapi.version=1.41'`

Preserve actual outputs, exits, target counts and default opt-in skips; explicitly enabled target groups must execute nonzero tests with zero target skips. Fresh independent aggregate re-review remains required when explicitly requested by the human invocation; executor PASS is not the reviewer verdict.

## Completion Criteria

- Both real lock-contention regressions discriminate old code from repaired code and reject the stale token after an independently committed, demonstrably visible pre-unlock context change.
- A rejection records no approval/confirmation event or business version change; a fresh token succeeds under the unchanged state/access rules.
- Approval/confirmation SMTP count remains zero; existing send-time context, idempotency and ordinary-send regressions still pass.
- All required commands pass on the complete repair boundary; no compilation/test suppression, global environment mutation, production recipient or real external provider delivery.
- Changes remain within the two authorized product/test files; evidence handoff is separate and append-only.

## Self-Review

One task maps to one demonstrated in-scope root cause. No BLOCKED item, closed decision, optional observation or unrelated defect is made a completion requirement. The approved requirement is correct and unambiguous, so no baseline amendment is proposed. No automatic continuation, execution, extra repair round or deployment is authorized by this draft.

## Human Approval

Execution is prohibited until a later explicit human-originated invocation names this exact repair artifact. This review has not approved or executed the repair.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/draft-review-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the two Authorized Files, with subject `fix(review-fast-p): repair draft review master`.
3. Appending `docs/plans/review/draft-review-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence, following `skill://review-fast-p/references/artifacts.md`.
4. Exactly one docs-only evidence commit containing only `docs/plans/review/draft-review-master/repair-execution.md`, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task only when the human's explicit execute-p invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
