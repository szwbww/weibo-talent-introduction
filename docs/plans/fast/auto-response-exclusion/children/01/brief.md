# Child 01 Brief — Backend First-Reply Auto-Response Exclusion

## Contract
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion`
- Branch: `fast/auto-response-exclusion`
- Child base SHA: `5f1e99d4fe024d698759c16ae5e2c9ba40554648`
- Approved plan (complete contract, read first): `docs/plans/2026-10-20/auto-response-exclusion.md`
- Plan identity: `sha256:9d77540b671b0cef36f02ea72862215b9f50d928b34e4577540f66f6f1e1472c`
- Execution report: `docs/plans/fast/auto-response-exclusion/children/01/execution.md`
- Use `execute-p`. Implement the plan exactly; do not widen scope.

## Goal
Only a proven first reply from an already matched expert, which the original pipeline would place in `MANUAL_REVIEW`, is reclassified to `PROCESSED / AUTO_RESPONSE` when either:
- top-level `Auto-Submitted` normalizes to `auto-replied`, or
- the inbound arrives 0..300 seconds (inclusive) after the nearest successful OUTBOUND for the same expert and same logical account.

Second and later replies never enter either rule. Unmatched mail, unclear account ownership, and all original safety fallbacks keep their original behavior. No schema change.

## Authorized Files (only these 10)
1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundAutoResponseDetector.kt` (new)
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundAutoResponseDetectorTest.kt` (new)
8. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
10. `scripts/reclassify-auto-responses.py` (new)

Do not modify repositories, `MailboxService`, `AutoReplyPreviewService`, matching services, frontend files, Elasticsearch code, migrations, pom.xml, or any other path. Do not edit the approved plan.

## Invariants (must hold)
- I-1: exactly two OR rules, computed only after I-7 first-reply eligibility. `Auto-Submitted: no` does not veto the 5-minute rule. Header match wins when both match. No subject/body input.
- I-2: same expert (including bound aliases), same logical sender account, nearest prior `OUTBOUND/SENT/non-null sent_at`; `0 <= receivedAt - sentAt <= 300s` inclusive; 300.001s excluded. Use IMAP actual `receivedDate` and persisted `sent_at`; never polling time/`created_at`/minute rounding. Missing reliable receive time may only try HEADER.
- I-3: only the newly created processing row that is still `MANUAL_REVIEW` becomes `PROCESSED`, `reason_type=AUTO_RESPONSE`, `process_reason=AUTO_RESPONSE_HEADER|AUTO_RESPONSE_FAST_5M`; `resolved_by/resolved_at` stay null; `updated_at` updated; no other field changes.
- I-4: original `processSingleCore` business flow continues unchanged; no early return. Only a genuinely reclassified new mail returns `AUTO_RESPONSE_PENDING_EXCLUDED` (not in `MANUAL_REVIEW_OUTCOMES`) with all other result fields preserved.
- I-5: physical/historical duplicates, self-check, bounce, DMARC, `BODY_TRUNCATED`, `LEGACY_UID_UNVERIFIABLE`, `RECIPIENT_UNRESOLVED`, `CONTACT_NOT_FOUND`, `INTRODUCTION_NOT_SENT` keep original behavior; same transaction; no markSeen/cursor advance on failure.
- I-6: history script is dry-run by default, apply is idempotent, rollback only restores rows still matching the post-write snapshot.
- I-7: first-reply scope is `expert_contact.id` across all logical accounts and bound aliases. Eligibility requires no prior `inbound_mail_processing` row and no prior INBOUND `mail_record` for that expert, checked before this mail is written. Lock `expert_contact` with `SELECT ... FOR UPDATE` in the caller transaction; current reads only. A rolled-back transaction releases eligibility. Never use `expert_contact.first_reply_at`.

## Key implementation boundaries
- `ReceivedMail`: add in-memory `autoSubmitted: String? = null`, `receivedAtReliable: Boolean = false`; no DB field.
- IMAP: parse only the top-level `Auto-Submitted`, strip parameters, case-normalize, accept only a single `auto-replied`; duplicate/conflicting headers must not count as a header hit. Mark reliable only when the real `receivedDate` exists.
- New `InboundAutoResponseDetector` Spring component with `NamedParameterJdbcTemplate`; `lockContact`, `evaluateFirstReply`, pure `classify`. No saves/sends. Assert an actual caller transaction is active.
- `AutoMailReplyService`: local per-call context (no service field/ThreadLocal), lock after contact match and before this mail's writes, evaluate after the existing introduction-inquiry check and before the global auto-reply branch, re-read the new processing row by owner+uidValidity+uid after core returns, then update only I-3 fields. Constructor tail-inject the detector and update tests.
- `scripts/reclassify-auto-responses.py`: scan/apply/rollback exactly as specified; default dry-run; manifest schema and audit output as specified; no secrets in logs.

## Required commands
Run from the worktree with JDK11:
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT`
  - Only against the isolated local test database; never production. If MySQL/Docker is unavailable, record the exact reason as not executed, not as pass.
- Targeted tests named by plan T5 while iterating (same JDK).
- `python3 -m py_compile scripts/reclassify-auto-responses.py`

Baseline failures already known from the pre-implementation run: recorded in ledger `## Baseline`. Do not fix unrelated baseline failures.

## Downstream interface for child 02
Child 02 is display-only and independently verified. Keep all existing API shapes, `unmatchedOnly`/`unmatched-inbound` parameters, DOM ids/classes/actions, queue membership rules, and the internal tag value `"待匹配"` unchanged. Do not touch frontend files.

## Git
- Commit product/tests/script only as `feat(fast-p): implement 01`.
- Exclude `docs/plans/fast/**` and all fast-p reports from the implementation commit; the controller commits evidence separately.
- No push, merge, rebase, squash, amend, reset, or worktree removal.

## Return
`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
