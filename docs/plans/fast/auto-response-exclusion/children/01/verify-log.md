# Child 01 Verification Log

Append one complete light-verifier report per attempt.
## Light Verification: LIGHT_FAIL
Child: 01 docs/plans/2026-10-20/auto-response-exclusion.md
Boundary: 5f1e99d4fe024d698759c16ae5e2c9ba40554648..1e7f8b3a01b418b0667282e6fe4449c1fcdd47e2
Verifier: AutoResponseBackendVerifier

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git show --stat 1e7f8b3` lists exactly the 10 authorized paths (4 main Kotlin, 5 test Kotlin, 1 script); `git show --name-only --format= 1e7f8b3 \| grep '^docs/'` is empty, so fast-p docs are excluded from the implementation commit. Worktree only has uncommitted controller evidence (`execution.md`, `ledger.md`).|
|Plan and invariants|FAIL|I-1 header normalization violates plan T1 (plan line 160) / brief line 44: `InboundAutoResponseDetector.kt:168` uses `normalized.distinct().singleOrNull()`, and `ImapMailReceiveService.kt:382` passes every `Auto-Submitted` occurrence as a list, so two identical `Auto-Submitted: auto-replied` headers collapse to one value and count as a header hit; the contract requires duplicate headers not to count (`ImapMailReceiveServiceTest.kt:676` only covers conflicting distinct values). All other checked invariants pass: HEADER wins over window, `no`/`auto-generated` do not veto FAST_5M (detector `classify`, tests :105-140); full-duration 0/300 inclusive and 300.001 excluded (`InboundAutoResponseDetector.kt:99-108`, test :120-140); first-reply check precedes both rules and short-circuits without header/anchor reads (`:52-62`, test :150-170); contact `FOR UPDATE` before any write (`AutoMailReplyService.kt:280`, detector `LOCK_CONTACT_SQL`), prior processing/INBOUND current reads with `FOR UPDATE`, same logical account anchor (`LATEST_SENT_OUTBOUND_SQL`); reclassification only for newly created MANUAL_REVIEW row with reason_type/process_reason/updated_at only, resolved_* untouched (`AutoMailReplyService.kt:203-235`); no early return, callback after introduction check and before global auto branch (`AutoMailReplyService.kt:344`); `AUTO_RESPONSE_PENDING_EXCLUDED` absent from `MANUAL_REVIEW_OUTCOMES` (`:1673-1687`); duplicate results skip reclassification; script defaults to scan/dry-run, conditional apply, repeat zero-update, snapshot-only rollback (`scripts/reclassify-auto-responses.py:1-56,347-381,520-560,590-620`).|
|Required commands|PASS|Worktree, zulu-11.0.32 JDK. `mvn test`: exit 0, `Tests run: 5305, Failures: 0, Errors: 0, Skipped: 21` (baseline 5279 + 26 new, no new failures). `mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT`: exit 1, `Tests run: 46, Failures: 0, Errors: 1`; sole error is the recorded baseline `replied filter excludes followed and dismissed experts until a new inbound arrives:241` (`expert_replied_dismissal.dismissed_at` has no default; line shifted from 226 by the added test code). `python3 -m py_compile scripts/reclassify-auto-responses.py`: exit 0; `--help` exits 0 with scan/apply/rollback arguments.|
|Downstream interfaces|PASS|No `src/main/resources` or `mail/repository` files in the commit; no controller/frontend changes. `unmatchedOnly` / `/unmatched-inbound` untouched; `MANUAL_REVIEW_OUTCOMES` membership unchanged; new enum value only surfaces through the pre-existing `outcome` string in the batch summary.|

### AUTO_FIX
- F-1: Plan T1 (auto-response-exclusion.md:160) and child brief line 44 require "重复/冲突头不当成单值命中" — duplicate and conflicting headers must not count as a header hit. Evidence: `ImapMailReceiveService.kt:382` passes all top-level occurrences to `AutoSubmittedHeader.normalize`, and `InboundAutoResponseDetector.kt:160-168` collapses identical values with `distinct().singleOrNull()`, so headers `Auto-Submitted: auto-replied` + `Auto-Submitted: auto-replied` yield `"auto-replied"` and a HEADER classification; only conflicting values are rejected by the test at `ImapMailReceiveServiceTest.kt:676`. Smallest authorized correction in `InboundAutoResponseDetector.kt` (`normalize`): require exactly one occurrence (`if (values == null || values.size != 1) return null`) before stripping parameters/lowercasing, and add a duplicate-identical-header case to the authorized `ImapMailReceiveServiceTest.kt` asserting `autoSubmitted == null`.

### RECORD_ONLY
- O-1: `scripts/reclassify-auto-responses.py` scan (`CANDIDATE_SQL`, `prove_first_reply`) filters only `expert_contact_id IS NOT NULL`, `MANUAL_REVIEW` and `INTRODUCTION` existence; it does not exclude historical original-safety reasons such as `BODY_TRUNCATED`/`LEGACY_UID_UNVERIFIABLE`, so "原安全条件满足" in T4 is only partially enforced. No manifest/apply test exercises these rows, so impact is unproven.

### Required Action
- AUTO_FIX

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 docs/plans/2026-10-20/auto-response-exclusion.md
Boundary: 5f1e99d4fe024d698759c16ae5e2c9ba40554648..a63376a9129dbcb057ee9277e1bc9347e0b36066
Verifier: AutoResponseBackendReverifier1

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git show --name-only --format= 1e7f8b3` lists exactly the 10 authorized paths (4 main Kotlin, 5 test Kotlin, `scripts/reclassify-auto-responses.py`); `git show --name-only --format= a63376a` lists only `InboundAutoResponseDetector.kt` and `ImapMailReceiveServiceTest.kt`. No `docs/` path in either commit; worktree has only docs report modifications.|
|Plan and invariants|PASS|F-1 closed: `InboundAutoResponseDetector.kt:164-165` now returns null unless `values.size == 1`; `ImapMailReceiveService.kt:382` passes every top-level occurrence, so duplicate identical headers normalize to null. Regression `ImapMailReceiveServiceTest.kt:692-703` asserts duplicate `auto-replied` yields null while a single header still yields `auto-replied`; conflicting values remain covered at :684-688. I-1: detector `classify` (:61-76) header-wins then window, `no`/`auto-generated` do not veto, no subject/body input; tests `InboundAutoResponseDetectorTest.kt:61-108`. I-2: `ImapMailReceiveService.kt:333-351` uses actual `receivedDate`, `receivedAtReliable = receivedAt != null`; SQL `LATEST_SENT_OUTBOUND_SQL` (:143-150) same contact + logical account + SENT + non-null `sent_at`, ordered nearest; Duration 0..300 inclusive (:126-128, tests :92-108). I-3: `AutoMailReplyService.kt:224-239` only `processStatus/processReason/reasonType/updatedAt`, resolved fields untouched, only still-`MANUAL_REVIEW` new row. I-4: `:147-155` no early return, `result.copy(outcome=AUTO_RESPONSE_PENDING_EXCLUDED, reason=...)`; enum `:1642` absent from `MANUAL_REVIEW_OUTCOMES` (:1673-1686). I-5: reclassify inside `transactionTemplate.execute` (:153-176), markSeen after commit (:184), duplicates/safety branches return before callback (:282-344). I-6: script default `scan` (:614-636), conditional UPDATE with before snapshot (:414-440), rollback compares post-write snapshot (:527-585); `py_compile` exit 0. I-7: prior processing/inbound checks and `FOR UPDATE` reads (`InboundAutoResponseDetector.kt:43-58,136-150`), lock before writes (`AutoMailReplyService.kt:282`), never uses `first_reply_at`.|
|Required commands|PASS|JDK `zulu-11.0.32`. Full `mvn test`: exit 0, `Tests run: 5306, Failures: 0, Errors: 0, Skipped: 21` (baseline 5279 + 27 new, no new failures). `mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT`: exit 1, `Tests run: 46, Failures: 0, Errors: 1`; sole error is the recorded baseline `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives:241` (`Field 'dismissed_at' doesn't have a default value`), not a new failure. `python3 -m py_compile scripts/reclassify-auto-responses.py`: exit 0.|
|Downstream interfaces|PASS|Zero diff under `mail/repository`, `mail/controller`, `src/main/resources`, or `MailboxService.kt` across the boundary. `unmatchedOnly`/`unmatched-inbound` untouched; queue membership/tag `"待匹配"` untouched (`MailboxService.kt:363` unmodified); `MANUAL_REVIEW_OUTCOMES` set unchanged (only a comment mentions it in the diff); new outcome is not frontend-visible API shape.|

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: `scripts/reclassify-auto-responses.py` `CANDIDATE_SQL`/`prove_first_reply` still do not exclude historical `BODY_TRUNCATED`/`LEGACY_UID_UNVERIFIABLE` reasons, so T4 "原安全条件满足" remains only partially enforced; no test exercises those rows, impact unproven, outside the light gate.
- O-2: `InboundAutoResponseDetector.kt:155` object-level KDoc still says "单个/完全一致的合法值才返回该值", which contradicts the new exactly-one rule documented at :161-165; comment-only inconsistency, behavior and regression are correct.

### Required Action
- COMPLETE_CHILD
