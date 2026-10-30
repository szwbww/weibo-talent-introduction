# Child 05 — verify-log


## Light Verification: LIGHT_FAIL

- Epoch: 5; Attempt: 1.
- Timestamp: 2026-10-30T11:48:35+08:00
- Child/path: `05` / `docs/plans/2026-10-29/draft-review-05-admin.md`.
- Boundary: `a02477be34b3308899820fcc9bd3060ca5bccb70..ae6898ffe92e6e31b45819e5edbde8ac77e0ebff`.
- Verifier: `DraftReview05Verifier`; independent of `DraftReview05Implementer` / `DraftReview05Resume` and previous verifiers.
- Approved exact plan/master commit: `59eb60cf00534a342c1019827a62f2b7b1db0f2b`; current plan SHA-256 freshly measured `bcd0ccec1bd3dcd3283915411b78b95f67458a41d7b6f56bd3580541d01be55c`.
- Read the complete current child05 plan, complete brief including authoritative Epoch5 amendments, and complete execution report. Historical DRAFT/six-file/earlier-epoch text is superseded by Epoch5's exact narrow authorization. This is only the requested four-gate child05 check, not a whole-master audit or later review.
- Product/tests/Git/other repository documents remain read-only. No AUTO_FIX was applied in this append-only verifier assignment. Historical infrastructure/test errors were retained, not rerun to confirm them; the required final commands below were independently executed freshly.

### Four Gates

| Gate | Verdict | Evidence |
|---|---|---|
| 1. Exact authorized scope | PASS | `git diff --stat a02477be34b3308899820fcc9bd3060ca5bccb70 ae6898ffe92e6e31b45819e5edbde8ac77e0ebff` and complete `git diff ... -- src` (`artifact://1403`, `artifact://1405`) identify exactly the authorized 13 product/test paths. Intervening plan/controller/child04 evidence changes in the broader boundary are distinguished from product scope. The only backend product change is the existing `MailReviewDraftService.validateSubmission` call at line303; no migrations/schema/authority/send code changed. Bounded obsolete frontend assertions and the two Kotlin compatibility files match Epoch3–5 limits. |
| 2. I-19–I-22 / S-3–S-5 and inherited semantics | FAIL | I-19 summary/expert routing/filter/chip behavior, I-21 status/explicit-send matrix, I-22 context/confirmation/translation/comment isolation, exact S-5 CSS, and restricted-reader routes were inspected with current consuming dispatch points and exercised by fresh named suites. **F-01** proves an uncovered I-20 asynchronous retarget callback still mutates the original manual target after entering review. Other observed save/upload/manual-mode guards do not close this path. |
| 3. All planned fresh command gates | FAIL | All 14 required command invocations completed freshly: 13 passed, 1 failed. All explicitly enabled 01–03 database/auth/migration/controller/send targets were non-skipped and passed. Full Maven failed with an outside-scope DiscoveryVerificationWorkerTest assertion (**O-01**), so historical executor success is not substituted for this run. Default full Maven's 24 opt-in skips are disclosed separately. |
| 4. Final combined downstream contracts | PASS WITH F-01 EXCEPTION | Traced `onReviewPendingSummary` into the app toolbar; `setReviewPendingOnly` into component list params and backend controller/service/filter dispatch; draft action routes into `MailReviewDraftController`; dedicated send into `MailReviewSendService` and existing safety-confirmation seam. No schema additions, permission widening, old manual-send authority gate, or ordinary/review send fallback was introduced in this patch. Unified resource keys and inherited S-1 block coexist with appended S-5. Fresh manual/send/reader gates pass; original manual target restoration nevertheless fails in F-01's race. Historical authenticated backend/browser/SMTP/IMAP evidence was inspected as historical evidence only, not claimed as a fresh browser run or human acceptance. |

### Fresh runtime and exact command receipts

Working directory for every command: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`.

Exact common Maven environment (passed through `env` on every Maven invocation):

`JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home MAVEN_OPTS=-Xmx3072m DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`

Rows marked **external DB** additionally passed:

`DB_URL=jdbc:mysql://127.0.0.1:33386/fast_review05?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai DB_USERNAME=root DB_PASSWORD=review05-local`

The URL was one shell-quoted environment argument. This reproduces the executor's external-MySQL harness with an independently owned local port/container rather than reusing/deleting its database. Owned container launch was:

`env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker run --rm -d --name fast-draft05-verifier-epoch5-attempt1-mysql -p 127.0.0.1:33386:3306 -e MYSQL_ROOT_PASSWORD=review05-local -e MYSQL_DATABASE=fast_review05 mysql:8.0 --log-bin-trust-function-creators=1`

Launch exited0, container id `44febd575eb91764c31ef3df0ec4ff3961e90858414b847310556f3f81d6a29b`. Testcontainers targets used their own normal MySQL instances. Only one Maven process ran at a time; no global OrbStack restart/configuration/resource kill occurred.

| # | Fresh command (Maven rows use the exact common environment above) | Observed result | Receipt |
|---|---|---|---|
| 1 | `node --check src/main/resources/static/mailbox-chat.js` | exit0 | `artifact://1406`, successful `&&` chain |
| 2 | `node --check src/main/resources/static/app.js` | exit0 | same |
| 3 | `node --check src/main/resources/static/mailbox-readonly.js` | exit0 | same |
| 4 | `node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js` | exit0; 331 tests /331 pass /0 fail /0 skip | `artifact://1406:442-449` |
| 5 | `node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js` | exit0; 131 /131 pass /0 fail /0 skip | `artifact://1406:625-632` |
| 6 | `node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js` | exit0; 65 /65 pass /0 fail /0 skip | `artifact://1406:734-741` |
| 7 | `node --test src/test/js/*.test.js` | exit0; 1953 /1953 pass /0 fail /0 skip | `artifact://1406:3404-3411` |
| 8 | `mvn test '-DargLine=-Xmx1536m -Dapi.version=1.41'` | **exit1 / BUILD FAILURE**; 5715 JVM tests /1 failure /0 errors /24 default opt-in skips; 5690 passed. Failure is O-01. Bound Node executions were not reached in this failed invocation. | `artifact://1407:12495-12510`; 12:51; finished2026-10-30T11:09:02+08:00 |
| 9 | `mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; 12 /0 failures /0 errors /0 skips | `artifact://1417:1287-1296,3959-3976`; 04:41; finished11:13:50+08:00 |
| 10 | `mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; AuthFlow9 +Flyway46 =55 /0 failures /0 errors /0 skips | `artifact://1418:920,174190-174260` and final90lines; 17:28; finished11:31:24+08:00 |
| 11 | **external DB** `mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; Query5 +Repository40 =45 /0 failures /0 errors /0 skips | `artifact://1422:749-751,1022-1031,3694-3711`; 04:08; finished11:35:42+08:00 |
| 12 | **external DB** `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; 46 /0 failures /0 errors /0 skips | `artifact://1424:610-617,3280-3297`; 03:21; finished11:39:15+08:00 |
| 13 | **external DB** `mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; ReviewSend18 +DraftSend27 =45 /0 failures /0 errors /0 skips | `artifact://1424:4178,4516-4527,7190-7207`; 03:36; finished11:42:53+08:00 |
| 14 | `mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest '-DargLine=-Xmx1536m -Dapi.version=1.41'` | exit0 /BUILD SUCCESS; 58 +42 +60 =160 /0 failures /0 errors /0 skips | `artifact://1424:7688,7766,7773-7780,10443-10460`; 02:47; finished11:45:41+08:00 |

Each successful named Maven invocation also freshly ran the bound full Node suite: 1953 pass /0 fail /0 skip, plus the configured JS syntax executions. No compile/test skip flags were used. Full Maven's default opt-in skips do not count as enabled database proof; rows9–13 explicitly provide that proof. Target counts and BUILD SUCCESS were read from current receipts, not copied from execution.md. Migration output retained unavailable-database background-worker shutdown noise; target46/55 totals and final success were read directly, not inferred from absence of errors. The large artifact contains a saved head/tail sample with middle truncation; relevant final target summaries are retained.

Fresh query measurement: `artifact://1422:749-750` reports `REVIEW_QUERY_SIZE100 rows=100 sqlCount=10 reviewSqlCount=2` and real EXPLAIN includes `idx_review_draft_pending` ref access /`Using where; Using index`; no extrapolated performance promise.

Additional diagnostics, not counted among the14 required gates:

- `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java -version`: exit0; genuine OpenJDK11.0.33 Zulu11.92+109-CA /11.0.33+9-LTS.
- `shasum -a 256 docs/plans/2026-10-29/draft-review-05-admin.md /tmp/fast-draft05-zulu11.tar.gz`: exit0; expected plan identity above; official archive SHA256 `4947b01fcb220ee69710dbf63375f4628688d84750fccd289f5a6d62b494aa94`.
- `git show --no-patch --format=fuller HEAD`: exact final candidate `ae6898ffe92e6e31b45819e5edbde8ac77e0ebff` (`feat(fast-p): implement 05`).
- Controlled race probe against the actual component and existing parsed-DOM/CAS harness: successful fresh `node /Users/lukai/.omp/agent/sessions/-IdeaProjects-weibo-talent-introduction/2026-10-27T14-09-30-488Z_01a17db1-db78-75c7-a0b0-cddb7cc9fd61/local/draft05-retarget-probe.cjs`, exit0, `artifact://1412`; source retained as `local://draft05-retarget-probe.cjs`. A preceding invocation using literal `local://` as a Node pathname failed with MODULE_NOT_FOUND, exit1; resolving its real path and executing it succeeded. This was a launcher-path error, not a product test failure or a rerun of O-01.

### AUTO_FIX

**F-01 — NEW / P1 / proven in-scope I-20 violation; not applied by this append-only verifier.**

Patch anchor: `src/main/resources/static/mailbox-chat.js:5864-5870` (`selectAdminReview` changes review epoch/mode but does not invalidate asynchronous manual retarget). Affected consumer: same file `retargetManual:9511-9552`.

Trigger: open an original saved draft for processing90, click “为新来信另建草稿” for processing101, hold the original target GET, select review501, then release the unchanged GET. Its callbacks validate only conversation epoch/draft-owner liveness. Review entry leaves these valid, so the late operation still opens its ordinary confirmation, changes `manual.targetKey`, and marks/copies the old original body and attachment into a new target while review is displayed. Fresh probe output: `BEFORE_RELEASE Review B 1:90:acc1`; `AFTER_RELEASE Review B 1 0`; switching back produces `RESTORED_TARGET 1:101:acc1 BODY original old-target body`. Both old90 and new101 persisted rows are present in `artifact://1412`. Thus the captured original owner/target is not restored, despite review never authorizing this old operation. This is introduced by adding review-mode transitions around a previously manual-only asynchronous path.

Uniquely plan-determined correction boundary: existing `mailbox-chat.js` retarget path must capture and recheck manual mode/review epoch and original target/owner before applying asynchronous GET/dialog results; review entry must make its old callbacks ineligible. Add a controlled delayed-retarget regression in the already authorized `mailboxServerDrafts.test.js` or `mailReviewAdmin.test.js`, asserting no late confirmation/target change/new draft and original body/attachment/target restore. No new API/schema/state subsystem is needed. The exact implementation remains for the authorized writer; no product/test modification was performed here.

### RECORD_ONLY

**O-01 — outside child05 scope; fresh mandatory full-Maven gate failure, not a patch finding.**

`DiscoveryVerificationWorkerTest.interrupted paper cache follower keeps lease resumable and owner completes:65` failed with `expected: <WAITING> but was: <RUNNABLE>` (`artifact://1407:9861-9864,12495-12510`). This file/subsystem is outside the13-file authorization. Recorded once and stopped investigating it: no rerun, diagnosis, assertion weakening, suppression, or unrelated repair. No claim that this is necessarily flaky, infrastructural, or caused by child05. Consequently there is no current passing full-Maven receipt, and the executor's historical5715-success receipt cannot satisfy this attempt.

### Historical production runtime/browser evidence inspected

Read the execution report's real authenticated production backend scenarios and preserved failures in full. It records separate admin/user1 cookie contexts; production controllers/services/repositories; real GreenMail SMTP/IMAP; actual draft submit/return/revise/resubmit/approve; confirm-onlySMTP0 and a subsequent explicit dedicated reviewed send; real safety422→confirmation→same-route success; an ordinary unreviewed manual send with original attachment; context invalidation by further real inbound; revoked-reader content clearing; and malformed `${broken` submission HTTP422 with unchanged detail/events. These are executor observations, not freshly performed by this verifier.

Opened and visually inspected all retained screenshots, each successfully readable:

- `/tmp/fast-draft05-resume-admin-1440.png`
- `/tmp/fast-draft05-resume-admin-pending-1440.png`
- `/tmp/fast-draft05-resume-dialog-1440.png`
- `/tmp/fast-draft05-resume-dialog-390.png`
- `/tmp/fast-draft05-resume-sent-390.png`
- `/tmp/fast-draft05-resume-reader-1440.png`
- `/tmp/fast-draft05-resume-reader-390.png`
- `/tmp/fast-draft05-resume-reader-revoked-390.png`

Desktop pending capture shows the original toolbar with待审核3, expert cards2/1, one review composer with disabled tools, small inline footer, and approve/return without ordinary send. Desktop/mobile dialog captures show the white native-dialog presentation, two reachable actions, and the explicit “确认后仍需手动点击发送” text. Mobile SENT capture shows the same composer and no send button. Reader captures show restricted navigation and the revoked capture's permission error/cleared content. Single-editor/native `showModal`/keyboard behavior is established by source, fresh named tests and historical runtime measurements, not inferred solely from a screenshot. The file labelled admin1440 does not itself expose the full lower translation region; translation details are attributed to execution.md and fresh tests rather than overclaimed as visible in that capture.

Read actual historical MIME `/tmp/fast-draft05-resume-outbound-1.eml` and `...-outbound-2.eml`: reviewed message is multipart/alternative text+HTML without attachment; ordinary message is multipart/mixed containing text+HTML and `fast-draft05-resume-attachment.txt` with the preserved original content. These corroborate the executor's ordinary/manual separation but are historical transport receipts, not a new SMTP delivery by this verifier. No fresh browser execution, fresh runtime SMTP/IMAP exercise, deployment, or human acceptance is claimed.

### Cleanup and evidence boundary

Owned database cleanup command `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker stop fast-draft05-verifier-epoch5-attempt1-mysql` exited0 and printed the owned name; `--rm` disposes that container/database. No unrelated container/database was altered. All launched Maven commands terminated before cleanup. Only this verify-log append is written in the repository; diagnostic scratch artifacts remain outside it. No F-01 fix, full-master audit, later review, or human acceptance was performed.

Exact required-command count: **14 executed /13 passed /1 failed** =3 syntax checks +4 standalone Node suites +7 Maven invocations. Six named Maven gates passed with363 JVM target tests total and zero target skips; the full-Maven5715 run failed once as disclosed. The required full-suite PASS proof remains unavailable in this attempt, independently of the proven F-01 semantic defect.

### Required Action

- PAUSE — Do not complete child05 or start the later whole-master review. Controller must route the bounded in-scope F-01 correction to an authorized writer and adjudicate the outside-scope O-01 full-suite failure; obtain a fresh passing full-Maven receipt and rerun the required affected/final gates after correction. No unrelated repair or blind failure-confirmation rerun is authorized by this report.
