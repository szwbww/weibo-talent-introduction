# Child 01 Fix Log

Append one entry per automatic repair round.

## Epoch 1 — Round 1/3
- Findings: F-1
- Before: 1e7f8b3a01b418b0667282e6fe4449c1fcdd47e2
- Fix commit: a63376a9129dbcb057ee9277e1bc9347e0b36066
- Authorized files changed: src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundAutoResponseDetector.kt, src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt
- Commands:
  - `JAVA_HOME=<zulu-11.0.32> mvn test -Dtest=InboundAutoResponseDetectorTest,ImapMailReceiveServiceTest,AutoMailReplyServiceTest` -> exit 0 (detector 12, IMAP 33, service 96; 0 failures/errors)
  - `JAVA_HOME=<zulu-11.0.32> mvn test` -> exit 0, BUILD SUCCESS, 5306 tests, 0 failures, 0 errors, 21 skipped
- Result: FIXED
- Notes: `AutoSubmittedHeader.normalize` now accepts exactly one top-level `Auto-Submitted` occurrence; duplicate identical or conflicting headers return null. Added an IMAP regression proving two identical `auto-replied` headers do not count while one still does. MySQL IT not rerun: the change is confined to header normalization, which no MySQL IT exercises (the IT concurrency/count paths are unchanged; state-level behavior unaffected). Remaining concern O-1 (history-script scan safety reasons) intentionally not addressed.
