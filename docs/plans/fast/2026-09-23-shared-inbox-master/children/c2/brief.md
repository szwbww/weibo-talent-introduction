# Child c2 Brief — 共享收件箱单次抓取与逻辑账号路由

- Child ID: `c2`
- Approved plan (complete contract): `docs/plans/2026-09-23/02-shared-inbox-routing.md` — plan identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`
- Master plan (upper constraint): `docs/plans/2026-09-23/00-shared-inbox-main.md` (invariants M-1…M-6, especially M-1 and M-2)
- Base SHA: `a15cb52b599e81753bb9fa3bcbb6e04969f44813` (product base = c1's terminal code head; c1's evidence commit precedes this implementation in Git ancestry)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Branch: `fast/2026-09-23-shared-inbox-master`
- Execution report: `docs/plans/fast/2026-09-23-shared-inbox-master/children/c2/execution.md`

## Deliverable

One physical IMAP poll per physical mailbox. Several business accounts sharing one IMAP INBOX must be polled once through the owner's credentials and the owner's `mail_inbox_cursor`; each incoming message is routed to a single logical account by its original top-level `To`/`Cc` (falling back to a unique `In-Reply-To` match against an `OUTBOUND` record), or — when it cannot be resolved uniquely — lands once as `MANUAL_REVIEW/RECIPIENT_UNRESOLVED` under the owner without any automatic mail or expert-state change.

## Authorized files (exactly these 10, no others)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

Do not modify `BatchAutoMailReplyServiceTest.kt` (it is a regression target, not an authorized edit) and do not create new files.

## Upstream contract delivered by c1 (verify against `children/c1/execution.md` before editing)

- `MailSenderAccount.inboundMailboxCode: String?` — NULL means the account owns its own physical mailbox.
- `InboundMailProcessing.mailboxOwnerCode: String?` — nullable physical owner code; legacy rows stay NULL.
- Migration `V134__shared_inbox_owner.sql` added `mail_sender_account.inbound_mailbox_code` (non-cascading FK to `mail_sender_account(account_code)`) and `inbound_mail_processing.mailbox_owner_code` plus the unique key `(mailbox_owner_code, uid_validity, imap_uid)`. No backfill; legacy rows keep NULL.
- `MailSenderAccountService` already enforces the single-level relation (self/missing/simulator/child-as-principal/principal-with-children guards), normalizes blank to `null`, and keeps `enabled`/SMTP/limits untouched; `listAutoReceiveAccounts()` still returns every non-simulator account, including disabled ones — reworking exactly that method into owner-only semantics is this child's job, not a c1 defect.
- c1's delivered state is recorded in `docs/plans/fast/2026-09-23-shared-inbox-master/children/c1/execution.md` (and its verification in `.../c1/verify-log.md`); use those as the authoritative description of the interfaces you build on, and re-read the actual sources before editing.

## Invariants (from the approved plan; violations are light-gate failures)

- `I-1` Physical polling is separate from business attribution: per physical owner and per check, exactly one IMAP login and one `mail_inbox_cursor`; business `sender_account_code` is the uniquely resolved logical recipient account; attachment `ImapAttachmentSource.accountCode`, later attachment fetching and `markSeen` always use the owner; `enabled` never filters receiving.
- `I-2` No guessing: a group with only the owner keeps today's behavior. Otherwise parse original top-level `To`/`Cc` addresses and match case-insensitively against the group's `sender_email`; exactly one hit wins. Zero or several hits may only fall back to a unique `In-Reply-To` hit among the group's `direction='OUTBOUND'` records. Anything still non-unique (BCC, missing/incorrect headers, conflicting duplicates) becomes one `MANUAL_REVIEW/RECIPIENT_UNRESOLVED` under the owner — never an automatic send, expert-state change, or silent QF attribution.
- `I-3` One physical UID is processed once: new rows carry `mailbox_owner_code=owner` plus the real positive UIDVALIDITY; the DB unique key is the concurrency backstop. Check the physical key before the business transaction, then check legacy NULL-owner rows and only treat a legacy row as already processed when UID and non-empty Message-ID/From/second-level receivedAt all match. `uid_validity=0` keeps the `LEGACY_UID_UNVERIFIABLE` manual path; never backfill the current generation. A unique-key violation is a duplicate only when the exact physical key exists; every other exception propagates.
- `I-4` Confirmation and cursor continuity: `markSeen(owner, uid)` only after the business transaction succeeds; only confirmed or verifiably duplicate UIDs enter the owner cursor's success set. An unresolved-recipient message counts as confirmed once its manual row and attachment registration succeed; failures must not mark seen or advance the cursor. UID backfill for an alias resolves to the owner's IMAP and must not consume the alias's own stale cursor.

## Plan-required ordering (M-3, reused by c3)

`processSingle` receives the logical account and resolves the physical owner internally; **every** non-bounce/non-DMARC inbound message — including ones that end unresolved — must enter `processSingle` before any manual row is created, so that child c3 can insert the group-wide self-check filter at that single entry point.

## Required commands (run in the worktree; JDK 11 mandatory)

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
mvn -B -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,OperatorStatusWriteSeamGuardTest test
node --test src/test/js/*.test.js
```

No Docker/Testcontainers command is required for this child. Do not run the whole project suite, and do not run formatters or linters.

Baseline at master base for the JVM classes above (recorded in the ledger, reproducible from `baseline/mvn-unit.txt`): 161 tests, 0 failures, across the eight classes then selected — `ImapMailReceiveServiceTest` 23, `AutoMailReplyServiceTest` 54, `BatchAutoMailReplyServiceTest` 21, `OperatorStatusWriteSeamGuardTest` 1. JS suite: 1121/1121.

## Acceptance (from the approved plan)

- `I-1`: with two logical accounts sharing an owner, automatic and contact-scoped checks each perform one owner IMAP fetch and one owner cursor advance; logical accounts still own INBOUND/processing rows; attachment source and `markSeen` stay on the owner; an independent account keeps polling itself.
- `I-2`: tests cover direct-to-updates, direct-to-alias, case/display-name variants, BCC without `To`, `To`+`Cc` both containing group members, no/unique `In-Reply-To`, and duplicate OUTBOUND Message-IDs; unresolved routing yields exactly one `MANUAL_REVIEW/RECIPIENT_UNRESOLVED`, zero SMTP, zero expert-state transitions.
- `I-3`: replaying the same owner/UIDVALIDITY/UID adds no row; legacy NULL-owner rows are skipped only on the strict three-field match; the same UID under a different UIDVALIDITY is a new row; concurrent unique conflicts do not swallow unrelated exceptions; generation 0 keeps the manual branch.
- `I-4`: processing or attachment-registration failure leaves IMAP unseen and the cursor unmoved; success or a verified duplicate advances the owner cursor; an alias's old cursor does not move.
- Cross-path: processing rows read back through `MailboxConversationRepository` show the logical account and count 1; attachment transfer later logs in with the owner.
- `OperatorStatusWriteSeamGuardTest.kt` pins exact line numbers in its `EXCLUDED_NOISE_SITES`; inserting lines into `MailRecordRepository.kt` shifts them, so update only those exact line numbers (never the exclusion text or rules) and keep the test green.

## Commit and reporting

- Commit locally as `feat(fast-p): implement c2` (implementation + tests only; never include `docs/plans/fast/**`).
- Write the full result to the execution report path above: files changed, decisions, exact commands with exit codes and counts, deviations, blockers.
- Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
- Do not push, merge, rebase, amend, or rewrite history; do not modify files outside this worktree.
