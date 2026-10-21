# Child 01 Execution — Backend First-Reply Auto-Response Exclusion

- Epoch: 1
- Base: `5f1e99d4fe024d698759c16ae5e2c9ba40554648`
- Branch: `fast/auto-response-exclusion`
- Implementation commit: `1e7f8b3a01b418b0667282e6fe4449c1fcdd47e2` (`feat(fast-p): implement 01`)
- Status: READY_FOR_VERIFICATION
- JDK: `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (11.0.32, tzdb 2026b)

## Scope

Exactly the 10 authorized files (4 production incl. 1 new, 5 tests incl. 1 new, 1 script). No repository,
`MailboxService`, `AutoReplyPreviewService`, matching service, frontend, ES, migration, `pom.xml`, or plan
edits. No schema change, no new field/ThreadLocal for the eligibility context.

## Implementation

### T1 — detection inputs (`MailReceiveService.kt`, `ImapMailReceiveService.kt`)

- `ReceivedMail` gains tail-defaulted in-memory `autoSubmitted: String? = null` and
  `receivedAtReliable: Boolean = false` (positional compatibility preserved; no DB column).
- `ImapMailReceiveService.convertToReceivedMail` reads `message.receivedDate` **once**, uses it for both
  `receivedAt` and `receivedAtReliable`; missing date keeps the old `now` fallback with `reliable=false`.
- `fetchEnvelopeHeaders` reads every top-level `Auto-Submitted` value. `AutoSubmittedHeader.normalize`
  strips `; params`, lowercases, and only returns a value when all headers agree on one non-blank value;
  duplicate/conflicting headers yield `null` (no header hit).

### T2 — detector (`InboundAutoResponseDetector.kt`, new)

- `lockContact(contactId)`: `SELECT id FROM expert_contact WHERE id = :contactId FOR UPDATE`; no row → error.
  Asserts `TransactionSynchronizationManager.isActualTransactionActive()`.
- `evaluateFirstReply(contactId, accountCode, received)`: current-read prior `inbound_mail_processing`
  (`ORDER BY id LIMIT 1 FOR UPDATE`) and prior INBOUND `mail_record`; any row → immediate `null`
  (header not parsed, anchor not queried).
- Then header rule (`auto-replied`), else only when `receivedAtReliable`: nearest
  `OUTBOUND/SENT/sent_at非空/sent_at <= receivedAt` for the same contact **and** logical account
  (`ORDER BY sent_at DESC, id DESC LIMIT 1 FOR UPDATE`).
- Pure `classify(autoSubmitted, receivedAt, receivedAtReliable, sentAt)`: header first (wins when both),
  then `0 <= Duration <= 300s` inclusive; 300.001/301 excluded; negative delta, null anchor, unreliable
  time → `null`. No subject/body inputs.

### T3 — original pipeline preserved (`AutoMailReplyService.kt`)

- Detector tail-injected into the constructor; all other dependencies unchanged.
- Per-invocation local `firstReplyReason` captured by a callback passed to `processSingleCore`; no service
  field/ThreadLocal.
- `lockContact` is called immediately after the expert match, before body-truncated/legacy/unmatched
  writes; unmatched/legacy paths never evaluate the rules.
- `onEligibleFirstReply(contactId, accountCode)` runs after the existing `hasIntroductionInquiry` check and
  before the global auto-reply branch — i.e. after original safety/first-outreach checks and before this
  mail's processing/mail_record writes.
- After the original `processSingleCore` returns (no early return, all promotion/attachment/intent/QA/
  sending/notification logic unchanged), `reclassifyFirstReplyAutoResponse` re-reads the row by
  `(owner, uidValidity, uid)`, and only if the **new** row is still `MANUAL_REVIEW`:
  `PROCESSED` + `reason_type=AUTO_RESPONSE` + `process_reason=AUTO_RESPONSE_HEADER|AUTO_RESPONSE_FAST_5M`
  + `updated_at`; `resolved_at/resolved_by` and every other field unchanged.
- Only a genuinely reclassified mail returns `AUTO_RESPONSE_PENDING_EXCLUDED` (new enum value, deliberately
  not in `MANUAL_REVIEW_OUTCOMES`) with all other result fields copied. Duplicate outcomes never
  reclassify. Notification condition/call is unchanged and sees the final result.

### T4 — history script (`scripts/reclassify-auto-responses.py`, new)

- Modes exactly `scan` (default/dry-run), `apply`, `rollback`; no implicit apply.
- Manifest `schemaVersion=1`, `ruleVersion=FIRST_REPLY_V3`, `generatedAt`, rows with processing/contact/
  outbound ids, logical/physical account, UIDVALIDITY/UID, Message-ID, receive/send times, delta,
  first-reply proof, body SHA-256 (+null flag), old status/reason/type/updated_at, approved reason.
- First-reply proof: earliest processing by `(created_at, id)` (nulls last), exactly one same-mail INBOUND
  record (same contact/account/message-id, received time within 1s), no independent earlier/same-time
  INBOUND history, existing OUTBOUND INTRODUCTION, nearest same-account `SENT` anchor within 0..300s.
- `apply`: per-row transaction, `expert_contact` `FOR UPDATE`, current-read re-proof (never trusts
  `firstReply=true`), body hash recheck, conditional UPDATE of only the four I-3 fields; changed rows are
  skipped/conflicted; repeat apply is zero-update. Audit stores before/actual-persisted after snapshot and
  counts.
- `rollback`: restores only rows still equal to the post-write snapshot (status/reason/type/updated_at +
  body hash); later manual changes are reported as skipped. No secrets/bodies/headers are logged or
  persisted.

## Verification

### Commands (all executed in the worktree)

```bash
# targeted (iterating)
JAVA_HOME=<zulu-11.0.32> mvn test -Dtest=InboundAutoResponseDetectorTest,ImapMailReceiveServiceTest,AutoMailReplyServiceTest
JAVA_HOME=<zulu-11.0.32> mvn test -Dtest=InboundAutoResponseDetectorTest

# required full gate
JAVA_HOME=<zulu-11.0.32> mvn test
# -> BUILD SUCCESS, 05:53, surefire (fresh reports) 5305 tests, 0 failures, 0 errors, 21 skipped
#    baseline was 5279: +26 new tests (12 detector, 10 AutoMailReplyService, 4 IMAP); JS 1725 passed.

# required isolated MySQL gate (127.0.0.1:3306/talent_introduction, root/root)
JAVA_HOME=<zulu-11.0.32> mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT
# -> 46 tests, 0 failures, 0 skipped, 1 error:
#    MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives:241
#    (PRE-EXISTING baseline error: expert_replied_dismissal.dismissed_at has no default; baseline line 226
#     shifted +15 by inserted test code. Not fixed, per instruction.)
#    All 5 new MailboxConversationRepositoryIT tests and the new MailboxSuspensionServiceIT test pass.

# script
python3 -m py_compile scripts/reclassify-auto-responses.py   # exit 0
```

### Script behavior proof (isolated local DB, temporary fixtures 990001-990003, cleaned afterwards)

- `scan`: 1 candidate (19.0s delta); skipped `outside-five-minute-window` (301s) and
  `independent-earlier-inbound`; zero writes.
- `apply`: requested 1, affected 1; row became `PROCESSED/AUTO_RESPONSE_FAST_5M/AUTO_RESPONSE` with
  `resolved_at/resolved_by = NULL`; 301s and prior-history rows untouched.
- repeat `apply`: affected 0, skipped 1 (idempotent).
- `rollback`: restored 1 to `MANUAL_REVIEW/GLOBAL_AUTO_REPLY_DISABLED`, original `updated_at`.
- after re-apply + simulated manual resolve: `rollback` skipped 1 (`changed-after-apply`), `apply` skipped 1
  (`row-changed-since-scan`); manual row preserved.

### Boundary coverage (plan T5)

| Boundary | Where |
|---|---|
| 0/19/299/300 inclusive; 300.001/301 excluded; negative delta | `InboundAutoResponseDetectorTest` |
| Header variants (`auto-replied; owner=`, `Auto-Replied`, `no`, `auto-generated`, conflicting duplicates) | detector + `ImapMailReceiveServiceTest` |
| Header wins when both match; `no`/`auto-generated` do not veto 5-minute rule | detector |
| Missing `receivedDate` → fallback but unreliable; header-only | IMAP test + detector |
| Non-first short circuit (no header parse, no anchor query) via processing and via INBOUND record | detector |
| Same logical account anchor only; missing anchor | detector (params) + conversation IT |
| Existing history across accounts / only-INBOUND / only-processing | conversation IT |
| Concurrent first replies: committed first wins, second sees committed row | conversation IT (real row lock) |
| Rolled-back transaction releases eligibility | conversation IT |
| Pending list/count + suspension pending count before/after reclassification; unmatched stays MANUAL_REVIEW | conversation IT + suspension IT |
| Batch `manualReview` excludes new outcome; `recorded`/`repliedExperts` preserved | service test |
| UID backfill uses the same classification | service test |
| Physical duplicate: no lock/evaluate/no second row | service test |
| Reclassification write failure propagates, UID not acknowledged | service test |
| Truncated body: lock only, no rule evaluation | service test |
| Unmatched: no detector interaction | service test |

## Notes / deviations

- Used the controller-designated JDK `zulu-11.0.32` instead of the plan's literal path (older tzdb fails 19
  unrelated `ExpertContactLocationServiceTest` cases).
- MySQL gate DB schema was already at Flyway v158 from the baseline run; no migration was touched.
- The known baseline MySQL IT error was not repaired; its reported line moved from 226 to 241 only because
  tests were inserted above it.
- `docs/plans/fast/**` and this report are uncommitted by design (controller commits evidence).
