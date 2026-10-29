# Child 03 — execution

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-03-send.md`
Plan SHA-256: `f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-03-send.md@f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7`
Execution epoch: NEW
Approval basis: current child03 assignment, exact brief, user invocation and continuous authority recorded in that brief. Historical DRAFT heading did not change the approved bytes.
Executor: `DraftReview03Implementer`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`
Target branch: `fast/draft-review-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master@fast/draft-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-draft-review-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA / child_base_sha: `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`
Pre-execution evidence HEAD: `f0394c4a708df8599fa368b09877400a0edc6f4a`
Post-execution code SHA: `f7b24c9c7364ea64adc4fa1013083343399756d7`
Evidence HEAD at handoff: `f7b24c9c7364ea64adc4fa1013083343399756d7`; this report remains separately uncommitted for controller-owned evidence persistence.
Implementation boundary: `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b..f7b24c9c7364ea64adc4fa1013083343399756d7`; product delta is the eight-file `feat(fast-p): implement 03` commit.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 / I-11, I-15 | IMPLEMENTED | Review controller, models, send coordinator, draft service | Exact session-admin endpoint; strict server-only body/target identity; nonapproved409, reader403, unknown fields422; original target/account/contact revalidated, no latest-target fallback. Downstream ResponseStatusException mapped locally without swallowing the original safety response. Real HTTP403/422 and SMTP proof below; enabled SendIT covers target reassignment and invalid references. |
| T2 / I-11, I-15 | IMPLEMENTED | Draft service, pending service, models | Safelist then existing HTML/text normalization, trimmed subject, existing placeholder parser and full final-validation length check. Final rendered subject/HTML/text compared byte-for-byte to frozen approval before claim. Existing safety, suppression, identity and attachment paths retained. Real MIME plain/HTML matched frozen fields; variable/length/render-difference SendIT cases passed. |
| T3 / I-12 | IMPLEMENTED | Draft service, repository, attempt service | Review row lock precedes attempt work; current context checked in claim transaction against approval or exact actor/draft/version/token confirmation event. Claim uses READ_COMMITTED so a lock wait cannot reuse a snapshot opened before a committed conversation update. Enabled real lock-contention test and real SMTP→IMAP ingest→API reconfirmation proof below. Confirmation alone never called SMTP. |
| T3 / I-13 | IMPLEMENTED | Attempt service, pending service, repository | Optional review-only length-prefixed `review-draft-v1` domain and id/version appended; ordinary identity has no added bytes. Fixed approved send_request_id retained for OUTBOUND. Exact completed-payload convergence validates bound review; conversation request-id early shortcut disabled for review requests. SENT DEDUP precedes context check; IN_PROGRESS/UNKNOWN retain binding and block; only existing safe-retry state reclaims same attempt. Enabled concurrent, DEDUP, completed lookup, two-review/ordinary separation and unknown cases passed. |
| T3 / I-14 | IMPLEMENTED | Attempt service, repository, draft service, models | Bind/claim/SEND_CLAIMED share transaction. Final result row lock precedes attempt; result persistence does not discard SMTP outcome on a concurrent grant/target change. SEND_SUCCEEDED or SEND_RESULT commits with attempt/mail_record; result events deduplicated under review lock. Detail projects actual status and valid SENT record id, with review remaining APPROVED. Claim/success/failure event-trigger rollback tests and post-send response-failure case passed. |
| T4 / I-11–I-15 | IMPLEMENTED | MailReviewSendIT | 18 new enabled/non-skipped real-MySQL cases. Existing DraftSend integration27 and manual/unit/trust160 all passed freshly. Real authenticated API and local SMTP smoke performed in addition to tests. |
| Exact scope / predecessor compatibility | IMPLEMENTED | Exact eight authorized product/test files | No migration/shared-table column, old admin-draft storage, whitelist, global handler, frontend, later-child, plan, ledger, handoff or verifier-report edits. No scope amendment needed; existing mandatory DraftSend fixtures passed unchanged. |

### Commands

Final required commands ran freshly, sequentially, after the last product/test edit. JDK11 and OrbStack were used; no full verification skill was invoked.

```sh
# C1 — enabled MySQL gate, both IT classes non-skipped
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest \
  -DmysqlIt=true -DargLine=-Dapi.version=1.41

# C2
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest \
  -DargLine=-Dapi.version=1.41

# C3
node --test src/test/js/ragAdoptSendBridge.test.js \
  src/test/js/meetingConfirmationIntegration.test.js \
  src/test/js/mailboxOutboundAttachments.test.js

# Runtime classpath preparation, not a smoke/test substitute
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft03-classpath -DincludeScope=test
```

| Command | Result | Evidence |
|---|---|---|
| Final C1 | Exit0; 45 tests, 0 failures, 0 errors, 0 skipped | Finished 2026-10-29 23:20:01 +08:00, `artifact://1008`; SendIT18 + original DraftSend27. MySQL8.0.36 containers and production Flyway/JDBC/transaction code, SMTP double as planned. |
| Final C2 | Exit0; 160 tests, 0 failures, 0 errors, 0 skipped | Finished 23:23:12 +08:00, `artifact://1010`; attempt58, pending42, trust-workbench60. |
| Maven-bound frontend hooks in C1 and C2 | Exit0 each | Both invocations: JS1912 pass, 0 fail, 0 skipped; node-check-app and node-check-task-modal-runtime completed. Receipts `artifact://1008:3845-3868`, `artifact://1010:3186-3208`. Not a full JVM suite claim. |
| C3 | Exit0; 65 tests, 65 pass, 0 fail, 0 skipped | Named RAG-adopt/meeting/attachment regressions; terminal tool result “Running named frontend compatibility tests”. |
| dependency:build-classpath | Exit0 | Finished 23:23:14 +08:00, `artifact://1010`. Production runtime excluded target/test-classes. |
| Real application startup and actual HTTP/SMTP/IMAP smoke | Exit0 startup; observed results below | `artifact://1011` and smoke receipts `artifact://1015`, `artifact://1016`, `artifact://1020`, `artifact://1022`, `artifact://1023`. |
| `git diff --cached --check` and explicit-whitelist staging | Exit0 | `artifact://1025`; exactly the authorized eight product/test paths staged, no evidence documents. |
| Plan/worktree helpers before staging and before commit; commit and branch ancestry | Exit0 | `artifact://1025`; exact plan hash unchanged, expected worktree/branch/git-dir enforced; commit `f7b24c9c7364ea64adc4fa1013083343399756d7` was target HEAD and branch-reachable. |
| Runtime cleanup | Exit0 | Stopped fast-draft03-api; `docker stop fast-draft03-mysql fast-draft03-smtp fast-draft03-es`; all were disposable `--rm` containers. Removed temporary classpath file. ES foreground supervisor's143 is the expected requested SIGTERM, not a smoke failure. |

#### Earlier execution attempts, retained rather than hidden

- First C1 invocation exited1 at23:10:12 (`artifact://1002`): the new test account fixture omitted required `senderDisplayName`; test compilation stopped. Added that field in the authorized new test.
- Next C1 invocation exited1 at23:14:47 (`artifact://1005`): 44 tests / 0 failures / 1 error / 0 skipped. The new safe-retry test incorrectly assumed failed OUTBOUND mail_record creation leaves context unchanged. The production context correctly changed; the test now explicitly confirms that current context before its safe retry. All27 original DraftSend cases already passed in that invocation.
- Added the READ_COMMITTED lock-contention regression and correction within authorized files before final C1/C2/C3. Final45/160/65 results above are fresh after those edits. No existing mandatory test was changed or skipped.

### Actual changed API smoke (not MockMvc, not test-only SMTP)

Production `target/classes` ran under JDK11 with embedded Tomcat port18093, real session authentication/cookies, real services/repositories, a fresh MySQL8.0.36 database migrated throughV169 and real GreenMail SMTP/IMAP. Third-party dependency classpath contained no target/test-classes. SMTP was loopback11025, IMAP loopback11143; every envelope used `expert@fixture.test` or `sender@fixture.test`. No production server/recipient was configured.

```sh
docker run --rm -d --name fast-draft03-mysql -p 127.0.0.1:33373:3306 \
  -e MYSQL_ROOT_PASSWORD='<throwaway>' -e MYSQL_DATABASE=fast_review03_smoke \
  mysql:8.0.36 --log-bin-trust-function-creators=1
docker run --rm -d --name fast-draft03-smtp -p 127.0.0.1:11025:3025 -p 127.0.0.1:11143:3143 \
  -e GREENMAIL_OPTS='-Dgreenmail.setup.test.smtp -Dgreenmail.setup.test.imap -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.auth.disabled' \
  greenmail/standalone:2.1.3
env DB_URL='jdbc:mysql://127.0.0.1:33373/fast_review03_smoke?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=root DB_PASSWORD='<throwaway>' ES_BASE_URL=http://127.0.0.1:19298 \
  AUTH_ENABLED=true MAIL_SCHEDULING_ENABLED=false MAIL_QUEUE_ENABLED=false \
  LLM_ENABLED=false LLM_AUTO_REPLY_ENABLED=false \
  /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java \
  -cp 'target/classes:<generated-third-party-classpath>' \
  com.weibo.talentintroduction.TalentIntroductionApplicationKt \
  --server.port=18093 --spring.main.lazy-initialization=true \
  --spring.rabbitmq.listener.simple.auto-startup=false
```

Local account `review03-local`, contact90301 and test campaign90301 were SQL-seeded only in that throwaway database. Reader created/submitted and admin approved the actual drafts through HTTP. Existing auto-reply controls were disabled for the fixture. For actual inbound processing, started an additional disposable real Elasticsearch7.17.26 at loopback19298 with security disabled and512MiB heap; no ES product implementation was mocked or edited.

| Scenario | Observed result |
|---|---|
| Real admin login + required initial password change; real reader login | 200 / 204 / 200. |
| Reader create/submit; admin approve draft1 | All200, state APPROVED; local expert SMTP mailbox remained empty. |
| Reader POST draft1/send | 403 READ_ONLY_FORBIDDEN from the real interceptor. Handler not reader-whitelisted. |
| Admin POST draft1/send with forged html field | 422 REVIEW_INVALID_CONTENT; no attempt/SMTP. |
| Send frozen draft1 without existing identity-warning confirmation | Original422 MANUAL_SEND_SAFETY_BLOCKED structure, actual salutation/identity findings, attempt count0 and mailbox0. |
| Re-submit same draft/version with safety confirmation | 200 SENT, exactly1 expert SMTP message. Actual captured multipart alternative contained the DB-frozen plain text and HTML, no attachment. |
| Repeat same SENT draft1 without new identity confirmation | 200, same Message-ID `<manual-rich-40f0562eb5944a688eeb113a852a2876@weibo.com>`, still1 message. Detail APPROVED/SENT with mailRecord90302. |
| Draft3 approved with original fixed OUTBOUND target90303; actual new test inbound sent over SMTP and imported by POST /api/mail/auto-reply | Real processing row1 attached to contact90301; then send409 REVIEW_CONTEXT_CHANGED and no new attempt/delivery. |
| GET context then POST confirm-context for draft3 | 200 event14; expert SMTP mailbox unchanged at2. Confirmation itself sent nothing. |
| Send another actual test inbound and import it | Real processing row2 attached to contact90301, MANUAL_REVIEW; old event14 used to send gave409 REVIEW_CONTEXT_CHANGED, mailbox still2. |
| Read/confirm newest context, then explicitly send draft3 | Event15; send200, exactly one additional message. SENT replay200 without a confirmation credential; mailbox stayed3. Original target remained90303 despite newer inbound targets. |
| Draft4 created after those actual inbounds | Fixed INBOUND target2, approved frozen content; its first unconfirmed send returned the existing safety422. |
| Inject disposable DB trigger rejecting draft4 SEND_SUCCEEDED after real SMTP accepted | Send409 REVIEW_SEND_PENDING with explicit “发送状态未知，请勿重复发送”; actual expert mailbox rose from3 to4, success transaction rolled back, attempt4 DELIVERY_UNKNOWN and failed mail record90309, one SEND_RESULT carrying actual unknown result. Approved content/history retained. |
| Remove trigger and repeat draft4/send | Still409 with the same Message-ID `<manual-rich-46cbb736ce6247f996a63951c6b835f7@weibo.com>`; attempt count unchanged, expert mailbox still4. Detail APPROVED/DELIVERY_UNKNOWN with no sentMailRecordId. No automatic resend. |
| Original /api/mail/unmatched-inbound/2/manual-rich-reply, no review reference | 200 SENT with ordinary manual body, expert mailbox5. No approval step added to the original path. |
| Final detail/history rereads | Drafts1–3 APPROVED/SENT with record90302/90303/90307; draft4 APPROVED/DELIVERY_UNKNOWN. Each retained its exact approved HTML/text and APPROVE event. One SEND_CLAIMED and one terminal result event per review attempt; replays added no result events. |

Final real HTTP receipt counted51 requests: 41×200, 1×204, 1×403, 3×422, 1×400, 4×409. Final expert mailbox contained5 real test messages: three completed review sends, one SMTP-accepted/DB-unknown review send, and one ordinary manual send. Draft2 was a separate completed baseline fixture, described below. Final database had5 attempts and4 SENT manual mail records; the unknown SMTP delivery was deliberately not projected as confirmed SENT.

#### Smoke preparation failures and resolution

- Initial seed attempted to delete preseeded sender accounts and hit an existing campaign FK. Removed that deletion from the disposable seed; no product/schema change.
- The first receive request initialized the existing IMAP cursor at UID1 and intentionally imported no history. The initial draft2 send therefore legitimately succeeded with unchanged context; it was retained as a separate baseline send, not misreported as a context409. Created draft3 for the actual post-cursor update/reconfirmation scenario.
- First post-cursor ingest returned400 because ES had intentionally been configured to a refusing loopback endpoint; real existing operator-status synchronization required it. Started disposable Elasticsearch7.17.26 at that address and retried the retained unread message successfully (fetched1/recorded1/replied0). No production bypass or fake product fallback was introduced.
- An inspection query requested a nonexistent account column; corrected the diagnostic, not code. A compound mysql-client trigger statement initially failed delimiter parsing; used an equivalent single-statement trigger only on the disposable database. The successful real-SMTP unknown-result receipt is `artifact://1022`.
- All disposable containers, fixture data and trigger were removed after smoke. No scaffolding file was added.

### Changed Files

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt` — server-only approved-send coordinator, fixed target/account/content, original manual pipeline.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` — send request/internal reference and actual send projection fields.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt` — original-target validation, transactional binding, event/result dedup, real attempt/record projection.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` — canonical trim/submission rejection, approved snapshot checks, transaction-context credential and result helpers, detail projection.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt` — exact admin send handler, strict fields/types, local downstream status mapping while preserving original safety handler.
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — optional internal review reference, conflicting-reference rejection, final content equality, review-safe completed convergence.
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` — review-domain fingerprint, lock/claim/bind/context, existing result transaction events and actual durable convergence.
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt` —18 enabled real-MySQL transaction/send/controller cases including contention and event-trigger failures.

### Deviations

- No authorized-file amendment, plan edit, migration edit, interface caller repair, existing-test fixture repair or acceptance reduction was necessary.
- Added `-DargLine=-Dapi.version=1.41` and OrbStack DOCKER_HOST as brief-permitted runtime compatibility flags.
- No build/package/WAR, deployment, push, merge, history rewrite, extra worktree, verify-p/review-p/repair-p/fix-v or later-child work was performed.
- Product commit excludes fast-p evidence. Existing controller-owned dirty brief and ledger were preserved. This separately-authorized execution report is uncommitted evidence, not part of the product commit.

### Freshness

- Plan identity rechecked: YES, unchanged exact approved hash.
- Worktree identity rechecked: YES, expected canonical root/branch/worktree Git directory enforced.
- Reported commit reachable from target branch: YES.
- Required commands run this invocation after final product/test edits: YES.
- Historical evidence used only as baseline: YES.
- Product/index clean at handoff: YES; separately authorized execution report plus pre-existing controller brief/ledger changes remain unstaged.

### Remaining Blocker

None.

### Next Action

Controller dispatches the isolated four-gate verifier for child03. READY_FOR_VERIFICATION is execution evidence, not an independent PASS.

## Epoch 1 — Controller Terminal Evidence Record

Independent DraftReview03Verifier returned LIGHT_PASS/COMPLETE_CHILD for `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b..f7b24c9c7364ea64adc4fa1013083343399756d7`; fresh mandatory45/160/65 tests passed with zero failures/errors/skips. Full four-gate report in verify-log.md. Automatic repair rounds0; no whole-system verification/human acceptance claimed.

