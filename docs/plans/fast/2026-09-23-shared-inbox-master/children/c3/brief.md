# Child c3 Brief — 共享收件箱机器邮件过滤与退信归属

- Child ID: `c3`
- Approved plan (complete contract): `docs/plans/2026-09-23/03-shared-inbox-bounce.md` — plan identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`
- Master plan (upper constraint): `docs/plans/2026-09-23/00-shared-inbox-main.md` (invariants M-1, M-3, M-5, M-6)
- Base SHA: `e28da464bb4bf310698079340796382e32acd2d0` (product base = c2's terminal code head; c2's evidence commit `77746e27c4de3ec6c8d4a32d579d61d1bd2b774c` precedes this implementation in Git ancestry)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Branch: `fast/2026-09-23-shared-inbox-master`
- Execution report: `docs/plans/fast/2026-09-23-shared-inbox-master/children/c3/execution.md`

## Deliverable

Two machine-mail behaviours on top of the shared inbox: (1) a bounce is attributed to the logical sender account that actually sent the original message, proven only by a unique `OUTBOUND` `mail_record` match, and hard-bounce monitoring runs for every member of the physical group; (2) `[self-check]` probes sent by any member of the same physical group are recognised at the single inbound entry point and acknowledged without writing any business table.

## Authorized files (exactly these 6, no others)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
3. `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SelfCheckProbeDetector.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/SelfCheckProbeDetectorTest.kt`

Do not create new files; do not modify `SelfCheckProbeSender`/`SenderAccountSelfCheckService` (probe send format stays as-is); do not modify `BatchAutoMailReplyServiceTest.kt` (regression target).

## Upstream contract delivered by c1 and c2 (verify against their execution/verify logs, then re-read the sources)

- c1: `MailSenderAccount.inboundMailboxCode`, `InboundMailProcessing.mailboxOwnerCode`, migration `V134__shared_inbox_owner.sql`, service-level single-level relation validation.
- c2: owner-only polling list plus alias→owner resolution in `MailSenderAccountService`; `ReceivedMail` exposes the parsed original top-level `To`/`Cc`; `AutoMailReplyService.processSingle` is the single inbound entry point (logical account in, owner resolved internally, owner used for IMAP/`markSeen`/cursor and attachment source); physical dedup via `(mailbox_owner_code, uid_validity, imap_uid)`; `MailRecordRepository` exposes a read-only **OUTBOUND-only** Message-ID candidate query returning a list.
- c2 delivered (see `children/c2/execution.md`; re-read the sources before editing):
  - `AutoMailReplyService.processSingle(account, received)` is the single inbound entry point: it accepts either a physical owner or an alias logical code, resolves the owner internally, and resolves the logical recipient from headers. Its precedence is physical-key duplicate → group legacy-fingerprint claim → recipient routing → `BODY_TRUNCATED` → `LEGACY_UID_UNVERIFIABLE` → business path, all before any business write. **Your group-wide probe filter belongs at this entry, before that routing/business logic.**
  - `MailRecordRepository.findOutboundCandidatesByMessageId(messageId): List<MailRecord>` — read-only, `direction='OUTBOUND'` only, list-returning so callers reject ambiguity. It is the only lookup allowed for bounce attribution.
  - `ReceivedMail.recipientAddresses: List<String>` — parsed original top-level `To`/`Cc`.
  - `MailSenderAccountService`: `listAutoReceiveAccounts()` returns owners only; `resolveInboundOwner(account)`; `getAutoReceiveAccount(accountCode)` / `getAutoReceiveAccountOrNull(accountCode)` resolve alias→owner; `getReceiveAccount` still returns the raw logical account. Group members are derived from the account list by `inboundMailboxCode`.
  - `InboundMailProcessingRepository.findByMailboxOwnerCodeAndUidValidityAndImapUid(...)` and `findLegacyOwnerlessByGroupAndImapUid(...)`; both processing-row writers fill `mailboxOwnerCode`; outcome codes in play include `DUPLICATE_IMAP_UID`, `LEGACY_UID_UNVERIFIABLE`, `RECIPIENT_UNRESOLVED`.
  - Known state your plan must replace: the **batch** path still short-circuits `[self-check]` probes before `processSingle` with its own branch that compares only the polling account's mailbox (`AutoMailReplyService.kt:847-853`, byte-identical to the pre-c2 base, and it writes no business tables). Plan 03 requires one group-wide filter at the single entry point and forbids keeping that separate current-account-only branch, so fold it into the new detection while keeping the batch handled-set/cursor semantics.

## Invariants (from the approved plan; violations are light-gate failures)

- `I-1` Bounce attribution only from a unique original send: `signal.originalMessageId` goes through the existing normalised candidate lookup restricted to `direction='OUTBOUND'`; only when there is exactly one candidate **and** its `sender_account_code` belongs to the current physical group may `bounce_record.sender_account_code` use it. Otherwise keep the passed-in attribution (the owner for shared-mailbox polling), log a searchable warning, never infer an alias, and never treat the `failedRecipient` expert address as the sender account.
- `I-2` The bounce source stays the physical mailbox: `collectBounces` keeps logging in as the owner for unseen mail; `bounce_message_id` uniqueness and expert matching are unchanged; only the confirmed logical code may change. After one shared-mailbox collection, run the existing hard-bounce-rate check for every logical member of the group, not only the owner.
- `I-3` Probes are recognised only by same-group sender identity: a probe must satisfy both From (case-insensitive exact match against some account's `sender_email` in the current physical group) and Subject (the complete generated form `[self-check] {accountCode} {decimal timestamp}` for that account, keeping the existing `[ self - check ]` spacing tolerance). Prefix-only, From-only, other-group, `Re:`, wrong code, or non-decimal tails must not match. Never drop an expert message on subject alone.
- `I-4` Ignoring a probe is not a business write: for a probe with a real positive UIDVALIDITY, return `SELF_CHECK_IGNORED` (`recorded=false`, not part of `MANUAL_REVIEW_OUTCOMES`) before the business transaction; write no `inbound_mail_processing`, `mail_record`, intent, tag, attachment or expert state; only `markSeen(owner,uid)` when `skipImapAck=false`, and let the batch entry add that UID to its handled set so the owner cursor advances. Missing UIDVALIDITY or a failed IMAP ack must not advance the cursor; UID backfill must not create a manual row either.

## Required commands (run in the worktree; JDK 11 mandatory)

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
mvn -B -Dtest=BounceCollectionServiceTest,AutoMailReplyServiceTest,SelfCheckProbeDetectorTest,BatchAutoMailReplyServiceTest,ImapMailReceiveServiceTest,OperatorStatusWriteSeamGuardTest test
node --test src/test/js/*.test.js
```

No Docker command is required for this child. Do not run the whole project suite; do not run formatters or linters. Baseline at master base: `BounceCollectionServiceTest` 6, `AutoMailReplyServiceTest` 54, `SelfCheckProbeDetectorTest` 4, `BatchAutoMailReplyServiceTest` 21, `ImapMailReceiveServiceTest` 23, `OperatorStatusWriteSeamGuardTest` 1 — all passing; JS suite 1121/1121.

## Acceptance (from the approved plan)

- `I-1`: a unique alias/owner OUTBOUND writes the matching account; no match, multiple matches, or an INBOUND-only hit keeps the passed-in owner; `BounceBackfillService`'s known logical account is never rewritten.
- `I-2`: a second identical DSN is still deduplicated by `bounce_message_id`; the owner logs in once; each group member's hard-bounce check runs once; existing original-expert matching and `EMAIL_INVALID` tests still pass.
- `I-3`: real probes from the owner and from any alias are recognised by From + full Subject; another physical group, a wrong code, `Re:`, a non-decimal tail, or prefix-only subject is not.
- `I-4`: batch, queue and UID backfill all return `SELF_CHECK_IGNORED`, `recorded=false`; zero writes to `inbound_mail_processing`, `mail_record`, `inbound_intent`, `inbound_mail_tag`, `mail_attachment`; only the owner `markSeen` and a continuous cursor advance; `skipImapAck` and absent positive UIDVALIDITY never acknowledge.
- Cross-path: a simulated real DSN shows up once in `BounceController` under the alias and increments that alias's hard-bounce count without touching the owner's.

## Commit and reporting

- Commit locally as `feat(fast-p): implement c3` (implementation + tests only; never include `docs/plans/fast/**`).
- Write the full result to the execution report path above: files changed, decisions, exact commands with exit codes and counts, deviations, blockers.
- Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
- Do not push, merge, rebase, amend, or rewrite history; do not modify files outside this worktree.
