# Repair Plan: mail-open-tracking-00-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-09-25/mail-open-tracking-00-master.md` (SHA-256 `14ff85b2875caf62e2adf936973c3b30f8f4a66b375c86a16c51f4f7c11f86a1`; recorded commit `ab2dda0f86c52d8bd9570d994fa895f087931df3`)
Approved amendments: A1 (master G-6 eleven-file exception) and A2 (child 03 G-2 clean cutover), recorded in `docs/plans/fast/mail-open-tracking-00-master/ledger.md`
Verification report: `docs/plans/review/mail-open-tracking-00-master/machine-verification.md`, epoch 1, `FAIL` / `INITIAL`, finding V-1
Implementation boundary: `f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..2fd810b50ebded265cb0cf5eeaca2a2b6550521a`

## Objective

Make the required MySQL date-boundary fixture use timestamps representable by the existing `mail_record.sent_at DATETIME` column. The test must prove that a previous-day mail is excluded, a start-of-day mail is included, and all existing list, status, and summary assertions execute.

## Findings in Scope

| Finding | Severity | Requirement | Root cause |
|---|---|---|---|
| V-1 | P2, mandatory gate | Child 01 I-7 date boundary and master G-6 MySQL integration command | `MailOpenTrackingRepositoryIT.kt:170` inserts `at.minusNanos(1000)` (23:59:59.999999) into the existing zero-fraction `mail_record.sent_at DATETIME` (`V1__create_business_tables.sql:109`). MySQL stores that value on the next day; the required test fails at line 173 before subsequent count and summary assertions run. The repository query itself uses the approved `[start,end)` predicates (`MailOpenTrackingRepository.kt:114-115`). |

## Findings Excluded

| Item | Reason |
|---|---|
| Docker API 1.32 startup error | Environment compatibility issue, resolved for verification by `JAVA_TOOL_OPTIONS=-Dapi.version=1.40`; no product change authorized. |
| Live HTTPS, mailbox, and browser A-checks | Human acceptance remains pending; no verified repair finding. |

## Unchanged Contract

- Preserve master G-1 through G-6, child 01 I-1 through I-7, and approved amendments A1/A2.
- Preserve the production schema, query, transaction boundaries, SMTP path, frontend, and all test expectations for 2026-09-25 records and summary counts.
- Do not change the `mail_record.sent_at` type or migration history to accommodate a test fixture.
- Do not relax or delete the date-boundary assertion, counts, status filtering, or orphan exclusion.

## Authorized Files

| File | Purpose |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` | Repair the previous-day fixture and assert its persisted day before evaluating the existing snapshot expectations. |

## Repair Tasks

### R-1: Use a representable previous-day fixture

- Resolves: V-1.
- Root cause: the current subsecond fixture is rounded into the next day by the existing zero-fraction `DATETIME` column.
- File: `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` only.
- Change: insert the previous-day row at a whole-second instant before midnight (for example, `at.minusSeconds(1)`); keep the exact-midnight and next-midnight rows, expected record IDs, and summary assertions. Assert that MySQL persisted the previous-day fixture before the list assertion so a future precision change cannot make this test silently misleading.
- Regression test: the existing `only successful associated outbound mail counts with stable filtering and Shanghai date boundaries` test must exclude row 9206, include the three 2026-09-25 successful outbound rows, retain the 2/1/0.5 summary and OPENED filter behavior, and exercise the empty-denominator assertion.
- Existing verification: rerun the focused MySQL/Flyway command below and the full approved matrix before claiming aggregate PASS.
- Must not change: production date range SQL, schema, status semantics, or other test fixtures.
- Prohibited: product changes, new migrations, widened file scope, skipped assertions, or altered expected counts.

## Verification Commands

Use JDK 11 at `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. The Docker daemon requires the process-level compatibility setting shown for the MySQL/Flyway command.

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true`
2. `JAVA_TOOL_OPTIONS=-Dapi.version=1.40 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest,PendingMailOperationServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SmtpMailDeliveryServiceTest,MailOpenTrackingPersistenceTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true`
5. `node --test src/test/js/mailOpenTracking.test.js`
6. `node --test src/test/js/*.test.js`
7. `node --check src/main/resources/static/app.js`
8. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`

## Completion Criteria

- The MySQL/Flyway gate runs test bodies and passes with zero failures/errors; V-1's persisted previous-day assertion and every existing snapshot assertion execute.
- The remaining required commands pass. Record exact counts, exit codes, and any skips in the aggregate re-review.
- The only changed product/test path is the one listed under Authorized Files.
- No live human A-check is inferred from machine tests.

## Human Approval

Execution is prohibited until a human explicitly approves this exact plan by invoking `$execute-p docs/plans/fix/mail-open-tracking-00-master/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/mail-open-tracking-00-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `test(mail-open-tracking): use representable date boundary fixture`.
3. Appending `docs/plans/review/mail-open-tracking-00-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record mail open repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
