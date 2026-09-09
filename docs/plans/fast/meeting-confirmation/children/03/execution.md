# 03 人工回复接入与历史日历下载 — Execution Report

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation/docs/plans/2026-09-09/03-meeting-confirmation-send-download.md
- Plan SHA-256: 4cd5c1ffeb697f7aaf7459ef615f6bd679a791fd4b7208ccda4f6482fd504101
- Execution ID: <plan>@4cd5c1ffeb697f7aaf7459ef615f6bd679a791fd4b7208ccda4f6482fd504101
- Executor: ImplementChild03c
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation
- Target branch: fast/meeting-confirmation
- Worktree HEAD at start: 31e058a981fa9044db192ceb47c705cad5926eba (child 02 light-verification docs commit; product tree identical to child base 6a1b546 — tracked tree clean verified before start)
- Pre-execution code SHA: 6a1b54697778bdea64002516d5c63fbabedd0f13
- JDK: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home

## Task status

| Requirement | Status | Evidence |
|---|---|---|
| T1 (I-1/I-2) Pending request+service meeting/digest; controller passthrough; 01 rebuild/verify before Safety/claim/SMTP | IMPLEMENTED | PendingMailOperationService.kt, UnmatchedInboundMailController.kt |
| T2 (I-3/I-4) MailboxConversationService batch snapshot metadata; DTO trailing nullable; new CalendarAttachmentController | IMPLEMENTED | MailboxConversationService.kt, MailboxConversationController.kt, CalendarAttachmentController.kt |
| T3 tests: Pending calendar scenarios (real generator + controller-shaped params + actual ComposedMail); 3 constructor sites; MailboxConversationControllerTest @MockBean + CalendarAttachmentIntegrationTest | IMPLEMENTED | 3 Pending test files + MailboxConversationControllerTest.kt |
| T4 OperatorStatusWriteSeamGuardTest line sync (rg-measured) | IMPLEMENTED | 1118 → 1121 (`rg -n 'operatorStatus = '`), 215 unchanged |

## Commands (fresh; final results appended at the end)

1. Targeted: `JAVA_HOME=... mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest` → PASS (see below).
2. mysql-it gate (isolated container DB talent_introduction_mc03): listener check + `mvn -Pmysql-it -Dtest=MailboxConversationControllerTest test`.
3. Full suite: `JAVA_HOME=... mvn test`.

### mysql-it listener/DB check (before Command 2)
- `docker ps` (DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock): `mailbox-refinement-mysql mysql:8.0.36 0.0.0.0:3306->3306/tcp Up 11 hours`.
- `lsof -nP -iTCP:3306 -sTCP:LISTEN`: OrbStack PID 2291 on `*:3306` → forwarded to the container above.
- `docker exec mailbox-refinement-mysql mysql -uroot -proot -N -e "SHOW DATABASES; SELECT ..."`: databases list contains `talent_introduction` (prior run's DB, NOT used) and `talent_introduction_mc03`; `mc03_tables=0` → dedicated fresh empty DB confirmed.
- DB_URL default in application.yml points at talent_introduction → gate MUST (and does) override with DB_URL=...talent_introduction_mc03.

### Command 2 — mysql-it gate against isolated DB (2026-09-09)
Exact command:
```
env DB_URL='jdbc:mysql://localhost:3306/talent_introduction_mc03?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Pmysql-it -Dtest=MailboxConversationControllerTest,CalendarAttachmentIntegrationTest test
```
- Note: `-Dtest` additionally lists `CalendarAttachmentIntegrationTest` (added in the same whitelisted file by this plan) because surefire only matches classes whose simple name contains the filter; the plan-mandated class name `MailboxConversationControllerTest` is still the primary gate target and both classes run under the same mysqlIt context against talent_introduction_mc03.
- Flyway migrated the empty `talent_introduction_mc03` from scratch (versions 1..123 incl. V122/V123).
- CalendarAttachmentIntegrationTest: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0.
- MailboxConversationControllerTest: Tests run: 21, Failures: 0, Errors: 0, Skipped: 0.
- Total: Tests run: 26, Failures: 0, Errors: 0, Skipped: 0. Exit 0, BUILD SUCCESS.
- Implementation note discovered during gate: controller-level 404s must go through GlobalExceptionHandler's NoSuchElementException → NOT_FOUND channel (ResponseStatusException would be swallowed by the advice catch-all → 500 at HTTP layer); CalendarAttachmentController therefore throws `NoSuchElementException("日历附件不可用")`.

### Command 3 — full suite (2026-09-09, fresh)
```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```
- Surefire aggregate: Tests run: 3310, Failures: 0, **Errors: 4**, Skipped: 10 → BUILD FAILURE (exit 1).
- Node JS suite: 766/766 pass (ran as part of lifecycle).
- All 4 errors are in ONE class NOT in this plan's 10-file whitelist:
  `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
  - 4 test methods stub `pendingMailOperationService.sendManualRichReply(...)` with 19 Mockito argument matchers
    (e.g. lines 226, 256, 283, 317, 381; the matcher-based ones among them) — the plan-mandated trailing
    `meeting`/`previewAttachmentSha256` parameters (21 total) make Mockito raise
    `InvalidUseOfMatchersException: 21 matchers expected, 19 recorded`.
  - Line 174's stub uses all-named raw args → unaffected; only matcher-recorded stubs break.

## Outcome: PLAN_CONFLICT

Cause (exactly the dispatch rule «A compile/test proof requiring another file → STOP and report PLAN_CONFLICT»):
- The approved plan (03-meeting-confirmation-send-download.md) mandates trailing parameters on
  `PendingMailOperationService.sendManualRichReply` AND lists exactly 10 authorized files.
- Keeping the full suite green requires adding 2 matcher arguments to 4 Mockito stub sites in
  `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` —
  an 11th file that appears nowhere in the plan change list, the brief, or the ledger, and has no
  pre-existing amendment authorizing edits (precedent: 03b previously amended to add the same file for the
  RAG trailing params; child 03 has no such amendment for meeting/digest).
- Fixing the red suite without editing that file is impossible: the signature change is plan-fixed, and any
  all-args matcher stub on the method must be updated to the new arity.
- Per execute-p authority/scope rules and the dispatch brief: out-of-scope edit → stop, do NOT commit.

Status of work:
- All 10 whitelisted files are implemented (T1..T4; see Task status table) and left as uncommitted working-tree
  changes in the worktree (no commit created — conflict halts the commit step).
- Targeted unit classes: PASS (80/80). mysql-it gate on talent_introduction_mc03: PASS (26/26, Flyway 1..123).
- Full suite: FAIL only on the 11th-file matcher stubs described above.

Minimal amendment required from the human (then resume): authorize updating the 4 matcher-recorded
`sendManualRichReply` stub sites in `UnmatchedInboundTrustWorkbenchTest.kt` (+2 trailing `Mockito.any()`
matchers each, with a comment noting 03 trailing meeting/previewAttachmentSha256; precedent 03b A3 a21784e),
then re-run full `mvn test`.

### Command 1 — targeted unit classes (2026-09-09, run in worktree)
```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest
```
Exit 0, BUILD SUCCESS.
- PendingMailOperationServiceTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
- PendingMailOperationServiceTrustWorkbenchTest: Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
- RagSendBridgeTest: Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
- Total: Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
- Node JS suite ran as part of lifecycle: tests 766, pass 766, fail 0.

Notes on the new calendar scenarios in PendingMailOperationServiceTest (11 tests total incl. 7 new):
- Real 01 generator (MeetingConfirmationService real; only template listEnabled mocked) produces genuine ICS/sha/text; send path asserts payload+ComposedMail share the one snapshot, thread headers = record.messageId, digest/sha/content-mismatch/pair/template-disabled 400s, outside-block edits pass, safety confirm retains meeting, no-meeting old path keeps calendar null and default thread headers, and controller request DTO (KotlinModule mapper) round-trips meeting/previewAttachmentSha256 with nullable defaults.


