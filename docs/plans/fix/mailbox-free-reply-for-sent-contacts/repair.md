# Repair Plan: mailbox-free-reply-for-sent-contacts

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-09/mailbox-free-reply-for-sent-contacts.md
Verification report: review-p / verify-p, 2026-09-09, finding V-1
Implementation boundary: `4e3613a (HEAD) → working tree`; exactly the ten baseline-authorized implementation/test files

## Objective

Only records with a nonblank real sender account may become a conversation free-reply anchor; a newer blank-account `SENT` row must not mask an older valid anchor.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-1; T1.1; I-1 acceptance requires empty accounts be excluded | The native anchor SQL checks `sender_account_code IS NOT NULL`, but accepts `''` and whitespace-only values. |

## Findings Excluded

| Finding | Reason |
|---|---|
| MySQL controller suite unavailable | Test host cannot connect to 127.0.0.1 MySQL (`SocketException: Operation not permitted`); not an implementation defect. |
| Full `mvn test` failure in `UnmatchedInboundAiReplyTurnKnowledgeTest` | Unchanged, unrelated test path; not in the reviewed diff or baseline scope. |

## Unchanged Contract

- Keep all I-1 filters and ordering: contact, `OUTBOUND`, `SENT`, simulator exclusion, optional account scope, `COALESCE(sent_at, created_at) DESC, id DESC`.
- Do not modify send lifecycle, HTTP contract, frontend, schema, migrations, or existing inbound behavior.
- Remain within the baseline's ten authorized files.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | Exclude blank and whitespace-only anchor account codes in the existing native query. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | Add a focused repository-query contract guard for the nonblank account predicate. |

## Repair Tasks

### R-1: Exclude blank sender-account anchors

- Resolves: V-1.
- Root cause: `sender_account_code IS NOT NULL` does not reject empty or whitespace-only SQL values.
- Files: the two authorized files above.
- Change: require a trimmed, nonempty `sender_account_code` before ordering/selecting the latest `SENT` anchor; preserve all other predicates and ordering exactly.
- Regression test: assert the `findLatestSentOutboundAnchor` native query contains the trimmed nonblank account predicate, so blank/whitespace values cannot be selected as an anchor.
- Existing verification: `mvn -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test`; MySQL controller suite when its documented test database is available.
- Must not change: account-scope semantics, simulator exclusion, fallback behavior, or attempt/SMTP execution.
- Prohibited: migrations, new repository methods, changes outside the two files.

## Verification Commands

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --check src/main/resources/static/app.js`
3. `node --test src/test/js/mailboxChatBehavior.test.js`
4. `mvn -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test`
5. `mvn -DmysqlIt=true -Dtest=MailboxConversationControllerTest test` (when MySQL is available)
6. `mvn test`
7. `git diff --check`

## Completion Criteria

- A blank or whitespace-only `sender_account_code` cannot be returned by `findLatestSentOutboundAnchor`.
- A later blank-account `SENT` record cannot prevent selecting an earlier valid matching anchor.
- The new regression guard and required available suites pass; changed files remain within the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
