# Child 08 — independent light verification

## Epoch 5 — fresh attempt 1 — independent bounded verification

- Verifier: `ExpertAccess08Verifier`; no writer conversation inherited, no subagents. Read `skill://fast-p/references/light-verifier.md`; only its four gates were assessed.
- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, observed HEAD `39f38a6676c4d4d0fdb7cb679326834b4a17e97f`.
- Approval/master identity `commit:04ba02e9f971cdebe26d8f37dd4de13a771eae93`; exact child plan `docs/plans/2026-10-29/expert-access-08-admin-controls.md`, observed SHA256 `24bba1e77ed429800b38a05fcc168c07751c9464cca9639fac0e845dbe52981d`. Latest brief epoch5 and A37–A44 govern; closed geometry/fixture decisions were not reopened.
- Boundary `be6437501d88a3eb283ede009eedf5cbca16b117..39f38a6676c4d4d0fdb7cb679326834b4a17e97f`. `git diff --name-only <base> <head>` returned exactly the ten authorized product/test paths below, plus interleaved controller-owned planning/evidence documents excluded from product scope. No product/test/index/commit/plan/ledger/brief mutations by this verifier.

### Fresh command receipts

Commands ran from the exact worktree with no source changes or concurrent test/build gates. Node commands were sequential, followed by unrestricted Maven, followed by the explicitly enabled named matrix and pristine calendar command. Dependency classpath generation did not compile or run tests; actual runtime acceptance used the unchanged final product while unrestricted tests ran.

```bash
node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js &&
node --test src/test/js/mailReviewAdmin.test.js &&
node --test src/test/js/authFlow.test.js src/test/js/mailboxServerDrafts.test.js &&
node --test src/test/js/*.test.js &&
node --check src/main/resources/static/app.js &&
node --check src/main/resources/static/mailbox-chat.js
```

- Exit0, `artifact://3575`: 239/17/80/1985 tests respectively, all passed, zero failures/cancellations/skips. Both syntax checks exit0. Exact totals independently extracted from the fresh output, not copied from execution.md.

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

- Exit0/BUILD SUCCESS, 21:54, `artifact://3676`: JVM5727, failures0/errors0/default conditional skips24; integrated Node1985, failures0/skips0. These are observed counts, not pinned acceptance constants; default opt-in skips are not represented as enabled integration coverage.

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33428/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verify08 ES_BASE_URL=http://127.0.0.1:33930 mvn surefire:test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1 &&
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33428/expert_access_08_calendar?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verify08 ES_BASE_URL=http://127.0.0.1:33930 mvn surefire:test -Dtest=MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

- Both exit0/BUILD SUCCESS, `artifact://3679`: matrix260, failures0/errors0/skips0, 12:47; pristine calendar3, failures0/errors0/skips0, 14.211s. The pristine schema applied170 migrations throughV171. `mysqlIt` and `migrationIt` were explicitly true, including AuthFlowIntegrationTest.
- Production runtime classpath command: `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-08-v5-classpath.txt`, exit0. JVM classpath was `target/classes` plus this dependency output, never expanded WAR or target/test-classes.

### Independent changed-path runtime receipts

- Owned MySQL8.0.36 container `expert-access-08-v5-mysql`, ID `670804ed70f63d498d93424de597399fbe118a199395b8013a4235fda6efaff2`, loopback33428; separate runtime, matrix and pristine-calendar schemas. Runtime JDBC explicitly Asia/Shanghai. Real Java11.0.33 Spring application33828 ran production AuthWebConfig/controllers/services/JDBC/Flyway, initially PID40834 then PID44229 with the loopback translation provider enabled. Initial runtime applied170 migrations throughV171 (`artifact://3584`). Loopback ES33930, SMTP33931 and translation33933 only; no production database or transport.
- Actual admin login and password rotation200/204, user1 login200 through the real AuthController. Real API sessions were independently held outside browser cookies. Admin/user mailbox API and genuine Chromium desktop1440×1000/mobile390×844 were exercised; native OS backgrounding is not claimed.
- A8/S3: desktop actual popover430px, first scope34px; mobile popover310px/x40/right350, scope44px, page scrollWidth390. Eight ordered fields (scope plus account/direction/tag/email/keyword/start/end), one Apply, one Reset, no obsolete top review button. Actual screenshot `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15bb00d284132e8b.webp`.
- Desktop AND mobile applied USER1_FULL AND all seven original fields: accountverify08/directionINBOUND/tagAcceptance08/emailalpha@example.test/keywordkeyword/start2026-10-30/end2026-11-01. Genuine SQL-backed result9801 only, summary8filters, independent mail badge unchanged. Browser resource URL contained all actual server query parameters. Reset returnedALL, reentry retained one scope/eight fields/no obsolete top button. Settled actual25-expert page2/2 then USER1_FULL yielded page1/1/one expert.
- A9/I7: disabled real account was initially included; SQL and real API independently showed admin3pending mails/2experts, user1 one pending mail/one expert, submitted5drafts, FULL1expert. Grant downgrade deliberately transferred pending ownership, so later admin4/5 are real fixture transitions, not expected-count pins. Added100 real SQL rows to the current5: API105, UI99+, exact105 aria-label/title. Controlled pending-badge503 produced hidden badge/explicitunavailable while scope remainedPENDING_REVIEW. All105 real mark-resolved POSTs returned200; real API0, UI hidden empty badge and exact0 aria-label.
- I1/I9/S2: actual legacy bodylessPUT retainedFULL; explicitADMIN returned400 without grant mutation; user1 grantPUT403. Desktop grant503 restoredconfirmedFULL plus inlineerror. Mobile controlled failure restoredFULL/nooverflow; independently holding the actual response proved savingdisabledtrue. Actual held grantPUT200 while selecting B did not replace B9802/READ. Settled header dimensions desktop180×34/max180; mobile308×44/x41/right349/max100%, preserving the literal CSS and existing mobile cascade.
- A10/I11: admin personal draft text was entered before selecting pending-review scope. Actual same-editor selection9842, real translation provider request `{q:"Review draft 9842",source:"en",target:"zh-Hans",format:"text"}` and Chinese UI output; approve persistedAPPROVED/version2, outbound0/SMTP0. A new real inbound9900 made Send open the original context-updated confirmation; confirming stilloutbound0/SMTP0. Returning to manual restored `Personal admin preserved` with exactlyone editor. Actual comment on9841 persisted, event history opened, explicit reason/confirm-return persistedRETURNED. Mobile9843 translated and approved with no SMTP.
- I10/I11: user1 FULL had one delegated shell/editor, no admin scope/header identity paragraph. Actual approved9842 was contentEditablefalse; visible review actions onlyhistory/comments/events. Separate new manual draft was editable. Explicit Send plus the original safety confirmation delivered one actual SMTP DATA719bytes; SQL mail_record2 OUTBOUND/SENT and SEND_MANUAL_RICH_REPLY actoruser1. Approval/context confirmation never sent. Mapped sender-account/task routes returned403; this did not use an unmapped wrong-method route as policy proof.
- I10/A12: CDP held an actual backend200 A messages response; selecting READB then releasing left shell0/privateAfalse/writable0/adminscope0. A real delegated management portal was opened, actual admin downgradePUT200 followed by the real page focus handler disposedFULLshell/portal and left no writable editor; observed requests were only list/pending/messages, not accounts/tasks. Mobile READ had noFULLshell/send/adminscope/overflow. Actual adminDELETE200 plus the page visibility handler removedA/privateA/editor. This is handler invocation, not a claimed native OS visibility transition.
- Separate actual logout race: captured backend200 response first (`interception-job-182075.0`), clicked genuine logout, observed before release shell0/editor0/loginvisible/storage{}; then issued release of that held response through its preserved CDP session. A subsequent independent page read still showed shell0/editor0/privateAfalse/loginvisible/storage{}. Genuine logout reload cancels old document work; no synthetic successful response or invented pre/post instrumentation.
- A14 tag fixtures came from loopback ES, while list/count/grants/review/send remained real JDBC/API: A displayed `Long safe <tag>` as `Long safe &lt;tag&gt;` in a leaf span with zero children; B[] had no unavailable placeholder; C absent profile displayed“标签暂不可用”. Original full admin navigation and personal-draft tab remained present.
- Fixture/automation failures are retained, not called PASS: native date fill produced malformed year and was corrected using actual date-input value/change events; premature async inspections, one wrong grant URL, hidden reader refresh/wrong transient selector, and monolithic logout runs timed out across the genuine page reload. Final split CDP capture/action/release/page reads proved the actual logout lifecycle. Initial disposable SMTP credentials were empty, producing an AUTH_FAILED/FAILED row with SMTP0; corrected only the owned fixture credentials/AUTH LOGIN provider, then a separately edited explicit manual send produced SENT/SMTP1. No product or test workaround.
- Cleanup: logged out and closed all owned browser tabs; stopped both owned application services; shut down owned ES/SMTP/translation servers; removed `/tmp/expert-access-08-v5-classpath.txt` and `/tmp/expert-access-08-v5-java.args`; Docker `rm -f expert-access-08-v5-mysql` exit0. No owned runtime services/scaffolds remain; the actual screenshot remains as evidence.

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 08 — docs/plans/2026-10-29/expert-access-08-admin-controls.md
Boundary: be6437501d88a3eb283ede009eedf5cbca16b117..39f38a6676c4d4d0fdb7cb679326834b4a17e97f
Verifier: ExpertAccess08Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary name diff: static mailbox-chat.js/mailbox-chat.css/app.js/index.html; tests mailboxMailReadAccess/mailboxChatBehavior/mailboxChatStyle/mailReviewAdmin/authFlow/mailboxServerDrafts.test.js. Exactly10 product/test files, matching epoch5/A37–A44; interleaved controller documents excluded. Only this verification log was changed by the verifier. |
| Plan and invariants | PASS | Child I1/I7/I9/I10/I11/S2/S3 direct code: mailbox-chat.js1581–1595,1849–1901,1993–1997,2124–2137,2486–2494,3822–3960,5760–6081,10462–10534; app.js17216–17235/21494–21515; literal CSS227–233. Fresh behavioral tests and independent production Auth/JDBC/API/SMTP/browser receipts above cover the changed paths and preserved boundaries, with approved desktop/mobile geometry. |
| Required commands | PASS | Fresh Node239/17/80/full1985 all0fail/0skip and two syntax exit0 (`artifact://3575`); unrestricted JDK11 Maven exit0 JVM5727/0fail/0error/24defaultoptinskip plus integratedNode1985 (`artifact://3676`); explicit mysqlIt+migrationIt matrix260 and pristinecalendar3 each0fail/0error/0skip, both exit0 (`artifact://3679`). Prior failures are baseline only, not replaced by unexercised claims. |
| Downstream interfaces | PASS | mailbox-chat.js1849 uses real filterCounts;1859–1901 uses independent pendingBadge.manualReviewTotal and unavailable/0/99+ semantics;2124 scope parameter preserves original AND filters;3870–3958 enforces current user/contact/generation and confirmed level;10465/10534 event unbind/bind matches app17232 producer. Same-editor review/manual isolation, approved lock and explicit manual SMTP were independently exercised. No later-child routes/final UI were demanded. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-08V5-1: Headless browser cannot establish a native user OS background/foreground transition. Real focus/visibility page handlers and resulting production requests/disposal were exercised; no OS transition is claimed. This is a runtime evidence limitation, not a proven authorized product correction.
- O-08V5-2: Historical broad-all-opt-in5963/3fail/24error/11skip and inherited foreign-anchor/unmapped-method global500 observations remain in baseline/controller evidence. They were not reconfirmed or repaired here; this child independently passed unrestricted defaults and the exact required enabled matrix. Aggregate product audit is outside these four light gates.

### Required Action
- COMPLETE_CHILD
