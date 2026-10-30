# Child 07 — independent light verification

## Light Verification: LIGHT_FAIL
Child: 07 — docs/plans/2026-10-29/expert-access-07-user-workspace.md
Boundary: b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc..f9db55399b0f77c378bbcfd8f69a714ade4c4b9f
Verifier: ExpertAccess07Verifier

Epoch: 5; fresh independent attempt 1. Plan identity: commit:b65a7436b49995b882ff71e08f7b271c1ecf00e8, SHA-256 a96241f8124c20ee5e9fbcfa6df7c64bb72f04bb3b37f292346eed66d1c98303. Target worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, observed HEAD f9db55399b0f77c378bbcfd8f69a714ade4c4b9f. Writer execution and historical failures were guidance, not independent proof.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-only <base> <head> -- src` gives the exact fifteen epoch5 paths: six static files, eight JS fixtures, one Kotlin projection fixture. Interleaved controller plan/evidence files excluded as authorized by dispatch. Projection diff adds only the required two-line nonnull Pair stub at MailboxReadAccessProjectionTest.kt:284–285. CSS diff adds only the two S-4 rules. No verifier product/test/index/commit/plan/ledger changes. |
| Plan and invariants | FAIL | F-07-1, real production AuthWebConfig/JDBC and Chromium reproduction below: FULL expert tag modal remains open after actual server downgrade to READ and focus reauthorization. Violates I-10 and implementation step5 (all portals/context-owned editors cleared). Other fresh commands pass but do not erase this runtime failure. Remaining full runtime acceptance is NOT_RUN in this failed attempt, not inferred from writer receipts or tests. |
| Required commands | PASS | Fresh sequential exact commands below: focused239, style47, shared97, full1980, four syntax checks; projection8; precise backend matrix260; isolated calendar3; automation198; calendar attachment6. All exit0 and zero target failures/errors/skips. Java11.0.33, explicit mysqlIt/migrationIt and API1.41, bounded class forks. No blanket all-opt-in aggregate rerun. |
| Downstream interfaces | PASS | mailbox-readonly.js:828–864 takes fresh response accessLevel before FULL mount; independent list retained. mailbox-chat.js delegated fixed contact guard and new mode use existing shared editor, immutable review history and separate manual draft; app.js:12167–12185 reuses existing profile session and calendar/material seam. Personal pending expert count remains separate from per-row mail count. No child08 final controls/routes demanded or introduced. F-07-1 is the explicit lifecycle invariant failure, not a proposed downstream redesign. |

Fresh command receipts (all run in target worktree, final committed product state, sequential):

```bash
node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js &&
node --test src/test/js/mailboxChatStyle.test.js &&
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/mailboxReplyIdentity.test.js &&
node --check src/main/resources/static/mailbox-readonly.js &&
node --check src/main/resources/static/mailbox-chat.js &&
node --check src/main/resources/static/app.js &&
node --check src/main/resources/static/contact-details.js &&
node --test src/test/js/*.test.js
```

Exit0, artifact2956: 239/47/97/1980 respectively, zero failures/cancellations/skips/todos; syntax commands exit0.

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn test -Dtest=MailboxReadAccessProjectionTest
```

Exit0, artifact2965: 8 tests/0 failures/0 errors/0 skips; fresh production/test compilation.

Backend environment for the following commands:

```bash
JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33427/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=child07
ES_BASE_URL=http://127.0.0.1:33928
```

Each invocation used `env` with those six explicit values (not merely shell-local declarations):

```bash
mvn surefire:test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Exit0, artifact2969:260/0 failures/0 errors/0 skips;12:22. Actual AuthFlow opt-in enabled. Per-class Testcontainers/context lifetime bounded.

Calendar invocation used the same environment except URL schema `expert_access_07_calendar`, independently pristine:

```bash
mvn resources:resources surefire:test -Dtest=MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Exit0, artifact2971:3/0/0/0,170 migrations throughV171.

Then original external test schema/environment:

```bash
mvn surefire:test -Dtest=AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,PendingMailOperationServiceTest,UnmatchedInboundMailServiceTest,OutboundAttachmentFlowTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
mvn surefire:test -Dtest=CalendarAttachmentIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Both exit0, artifact2973:198/0/0/0 and6/0/0/0. Same artifact records `env JAVA_HOME=<above> mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-07-light-classpath.txt` exit0. Actual app used target/classes + generated125 dependency entries, never testclasses/expanded WAR.

Independent actual runtime evidence (not tests substituted for UI):

- Owned MySQL8.0.36 container `expert-access-07-light-mysql`, ID dc303029ebc87f9261b8d020fcf1c332a7ae181d8631cc99653493b55a5d13ac, loopback33427; separate runtime schema `expert_access_07_runtime`, Asia/Shanghai. Java11.0.33 app PID47551, loopback33827, production AuthWebConfig/API/JDBC/Flyway; queue/schedulers/discovery disabled; temporary attachment root; loopback ES33928. No production database or external SMTP used.
- Actual independent admin HTTP cookie session logged in and changed mandatory initial password204. Actual Chromium browser user1 login with production UI200. SQL fixtures FULL9701/READ9702/NONE9703, disabled real sender `light07`, pending inbound9711/9712 for FULL/user1,9721 for READ/admin. Browser observed personal chip1 expert and row2 mails, user only mailbox/logout. FULL rendered exactly one original editor and no email/account identity line; malicious name literal, zero `img[src=x]`.
- At1440×1000 actual “专家资料” opened original manage/profile host. Fixture correction retained: initial disposable ES `_search` response intentionally had no hits, so tag editor unavailable and first selector wait timed out; corrected fixture to a real search hit, closed/reopened manage host. No source workaround. Actual “+ 添加标签” opened shared `#actionDialog`, title “添加专家标签”, writable `#expertCustomTag`.
- Focus dispatch in the real page synchronously removed FULL editor and profile sessions (0/0), but `#actionDialog.open` stayed true. Independent actual admin `PUT /api/mail/mailbox/conversations/9701/read-access/user1` body `{"accessLevel":"READ"}` returned200 READ. Another real-page focus and completed READ refresh still left openDialogs=`["actionDialog"]`, writable tag input present, editors0. Browser accessibility tree contained only orphan modal controls (preset/custom/cancel/confirm), blocking the now-READ workspace. Explicit Cancel was required to dismiss it. This is F-07-1, not just a source inference.
- Source chain: `openExpertTagAddDialog` app.js:8582–8625 owns listeners and modal but cleanup is reachable only via its submit/cancel handlers; `mcHostReleaseDelegatedContext` clears materials/calendar only; MailboxChat.unmount removes its own manage portal, not this shared modal. expertAccessWorkspace.test.js:192–207 stubs the modal promise and proves no late mutation, not actual modal cleanup. Existing late mutation guard means this report does NOT claim a write-authorization bypass.
- After cancel, real READ body/tags rendered; ES labels新发现/瑞典, zero injected image. Actual mapped GET sender-account/task/unmatched routes and NONE9703 messages each403. Current9701 messages200 READ with attachmentCount0/outboundAttachments[] on both rows.390×844 actual browser scrollWidth390, no page overflow. Real logout from this now-clean page removed private body/editors/dialogs (false/0/0), login form shown; browser page errors empty.
- Full A-5/A-6/A-7/A-11/A-12/A-14/admin baseline and safe SMTP acceptance is NOT claimed complete by this failed attempt. Native OS background transitions and forced late200 logout delivery were not exercised here; focus above is an explicit event in a real page. Future08 permission controls remain downstream, not a07 demand. Historical executor runtime/command mistakes and broad5963-test/3fail/24error/11skip observation remain untouched.
- Cleanup: browser released; owned app terminated; loopback ES shutdown/server_close; runtime temp directory and dependency-classpath temp removed; owned Docker removed exit0. No owned services/scaffolds remain; no product mutation or commit.

### AUTO_FIX
- F-07-1 — Governing I-10 and implementation step5 require ALL context-owned portals to disappear on focus/visible reauthorization, expert change, downgrade/revoke and logout/unmount. Actual FULL→READ plus focus leaves shared expert-tag `#actionDialog` open and interactive after its owning conversation/profile have been destroyed. Smallest authorized correction: bind this existing tag-dialog lifetime to the delegated conversation and invoke its existing cancel/cleanup semantics on delegated context release (resolve pending promise/remove listeners/clear owned dialog contents without closing unrelated admin dialogs); keep the existing late mutation guard and admin default behavior. Add actual-dialog lifecycle regression in authorized expertAccessWorkspace.test.js, not just a stubbed completion guard. Authorized app.js/mailbox-chat.js fixture seam suffices; no new product routes/stores/classes. Requires fresh independent verification after correction.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 07 — docs/plans/2026-10-29/expert-access-07-user-workspace.md
Boundary: b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc..be6437501d88a3eb283ede009eedf5cbca16b117
Verifier: ExpertAccess07ReVerifierR1

Epoch: 5; fresh independent attempt 2, after round1 F-07-1. Approved plan identity `commit:b65a7436b49995b882ff71e08f7b271c1ecf00e8`; independently observed plan SHA-256 `a96241f8124c20ee5e9fbcfa6df7c64bb72f04bb3b37f292346eed66d1c98303`. Target worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`; observed HEAD `be6437501d88a3eb283ede009eedf5cbca16b117`. Initial implementation `f9db55399b0f77c378bbcfd8f69a714ade4c4b9f`, repair `be6437501d88a3eb283ede009eedf5cbca16b117`; these are separate commits, not a comma-valued terminal boundary. Writer/verifier1 receipts were guidance, not this attempt's proof.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-only b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc be6437501d88a3eb283ede009eedf5cbca16b117 -- src` returned exactly the fifteen epoch5 authorized files. Fresh initial-implementation..repair diff returned only app.js, mailbox-chat.js, expertAccessWorkspace.test.js. Interleaved controller evidence is excluded, not attributed to product scope. fix-log records exact Before/Fix SHA and bounded modal smoke. No product/test/index/commit/plan/ledger mutation by this verifier. |
| Plan and invariants | PASS | Existing reader authority seam mailbox-readonly.js:828–864 mounts delegated FULL only from the fresh message response; lifecycle handlers1018–1025 and destroy1049–1080 discard old contexts. Repaired app.js:8582–8644/12195–12208 and mailbox-chat.js:6820 bind the real tag modal to its owner. Fresh real AuthWebConfig/API/JDBC/Chromium/SMTP observations below independently cover changed-path sends, files, native profile/tag/calendar forms, READ review states, downgrade/revoke/races/logout and admin baseline. F-07-1 is closed by actual dialog cancellation, null settlement, empty title/body and zero listeners, not merely by its new tests. |
| Required commands | PASS | All required commands freshly executed sequentially on terminal product state: frontend242/47/97/1983, four syntax checks; projection8; precise backend260; pristine calendar3; automation198; calendar attachment6. Every invocation exit0, zero target failures/errors/skips. Explicit mysqlIt/migrationIt/API1.41 and class-fork isolation used. No broad aggregate rerun. Exact receipts/environment below. |
| Downstream interfaces | PASS | Independent READ list retained; delegated-conversation fixed contact reuses original rich editor and native profile/material/calendar hosts, no global user bootstrap. READ review history and FULL manual payloads remain distinct; APPROVED stays immutable after FULL upgrade. Personal pending chip remains server expert total versus row mail count. app.js:21483–21502 retains historical scoped flush→logout→navigation, not an unrelated logout redesign. No child08 final permission UI/routes demanded or introduced. |

Fresh command receipts (target cwd above):

```bash
node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js &&
node --test src/test/js/mailboxChatStyle.test.js &&
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/mailboxReplyIdentity.test.js &&
node --check src/main/resources/static/mailbox-readonly.js &&
node --check src/main/resources/static/mailbox-chat.js &&
node --check src/main/resources/static/app.js &&
node --check src/main/resources/static/contact-details.js &&
node --test src/test/js/*.test.js
```

Exit0, artifact3045. Fresh count receipt: grep `ℹ (tests|pass|fail|skipped|cancelled|todo)` over artifact3045 gives lines315–321:242/242/0/0/0/0,390–396:47/47/0/0/0/0,521–527:97/97/0/0/0/0,3227–3233:1983/1983/0/0/0/0. Four syntax commands exit0.

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn test -Dtest=MailboxReadAccessProjectionTest
```

Exit0, artifact3057:8 tests/0 failures/0 errors/0 skips, fresh production/test compilation,03:15.

All backend invocations below actually used `env` with these explicit values (not unexported shell declarations); calendar substituted its independently pristine schema only:

```text
JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:33437/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=child07
ES_BASE_URL=http://127.0.0.1:33938
```

```bash
mvn surefire:test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Exit0, artifact3060:260/0/0/0,12:29. AuthFlow migration opt-in enabled; class contexts bounded by separate forks.

Calendar URL used schema `expert_access_07_calendar`, otherwise exactly the same explicit environment:

```bash
mvn resources:resources surefire:test -Dtest=MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Exit0, artifact3062:3/0/0/0;170 migrations throughV171, pristine schema.

Original external test schema/environment restored for:

```bash
mvn surefire:test -Dtest=AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,PendingMailOperationServiceTest,UnmatchedInboundMailServiceTest,OutboundAttachmentFlowTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
mvn surefire:test -Dtest=CalendarAttachmentIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-07-r1-classpath.txt
```

All exit0, artifact3064:198/0/0/0 and6/0/0/0; classpath generation successful. Runtime used target/classes plus125 dependency entries only, no expanded WAR or testclasses.

Independent actual production runtime, not writer receipts or tests relabeled as UI:

- Owned MySQL8.0.36 container `expert-access-07-reverify-mysql`, ID `c9bc168e3306687ce66c86f045a21f16bca0c86d5e3104771d6e00e63f5f9fff`, loopback33437, separate runtime schema `expert_access_07_runtime`, Asia/Shanghai JDBC. Java11.0.33 app33837, PID76579 then corrected-fixture restart78800; production AuthWebConfig/controllers/services/JDBC/Flyway170 migrations throughV171. Loopback ES33938 and safe SMTP33939; temporary files only, no production DB/ES/SMTP. Queue/scheduling disabled; corrected runtime explicitly disabled discovery.
- Real admin login200/mandatory password change204 and user1 login200; independent HTTP cookie clients and real Chromium150 UI sessions. Browser tabs share browser cookies, so admin/user browser phases explicitly reauthenticated and reloaded; not claimed as concurrent isolated browser identities. SQL fixtures FULL9701/READ9702/NONE9703, disabled real senderverify07, two user-owned Alpha mails and admin-owned Beta/Gamma mails. Initial UI pending chip1 expert versus row2 mails. Actual user only visible mailbox/logout; malicious expert name literal, zero injected images; FULL header has no email/account identity line, exactly one original editor after load.
- A-5: actual original user rich editor sends9712 through existing safety422→real confirmation→200. SMTP DATA843bytes, SENT9742, `SEND_MANUAL_RICH_REPLY` actoruser1. Upgrade Beta FULL: separate new direct manual request9721 returns200; identical replay returns the same messageId with exactly one SMTP DATA699bytes, SENT9743. Body `operatorName=admin` still auditsuser1. Existing admin original rich editor independently safety422→confirmation→200, SMTP683bytes, SENT9744; historical admin actorconsole retained. Total SMTP DATA exactly3. Profile/tag/calendar/upload/review operations produced no additional SMTP.
- A-6: original drawer shows MAIL_RECORD9771/INBOUND_PROCESSING9772/MANUAL_UPLOAD9773. Actual browser follows its real download URLs and receives exact original27-byte payloads for all three; actual authenticated preview endpoints also200 and byte-equal for all three. Existing native material upload form201 adds a document/attachment without SMTP. Foreign attachment and path-owner mismatches403; mixed transfer/ZIP members reject400 before effects. READ downgrade direct original link403 and all message attachmentCount0/outboundAttachments[].
- Runtime fixture correction retained, not source repair: first app used wrong attachment-storage environment name, so material rows were SOURCE_UNAVAILABLE and upload500 reported `/opt/talent`. Corrected to existing `MAIL_ATTACHMENT_STORAGE_BASE_PATH=<owned temp root>`, restarted actual app, reauthenticated, then original files were STORED and native upload201. Initial shutdown exceeded30s and owned process was killed before restart; final owned app later exited143 cleanly. Initial ES HEAD returned501 in the fixture; scoped profile search/doc responses were explicitly supplied before profile/tag acceptance. No product workaround.
- A-7: actual existing delegated identity editor PATCH200 saves VERIFY07R1/独立核验, version1, updatedByuser1; independent admin GET reads same snapshot. Full valid stale identity group expectedVersion0 returns409 PROFILE_CONFLICT. Initial guessed field names returned400 and were not counted as CAS proof. Original tag modal add200/remove200 updates displayed tags without admin-global reloads. Native desktop fixed-contact calendar form creates201, edits200, cancels200; JDBC event1/contact9701 CANCELLED with edited note/cancel reason. Mobile390 native same form creates201 event3/contact9701. Separate real admin competing update200 makes stale user update409; foreign schedule GET/PUT/cancel403 and foreign profile PATCH403, zeroeffects.
- READ review lifecycle: actual authenticated API creates DRAFT1/2/3/4, submits2/3/4, returns2, approves4 with real context token; comments and withdrawal/events exercised. Actual READ UI opens all four completed renders: DRAFT/RETURNED editabletrue, SUBMITTED/APPROVED false. Upgrade Beta FULL shows initially empty separate manual editor; selecting APPROVED4 finishes with its exact review HTML and contenteditablefalse, returning manual does not adopt its content. Direct PUT/DELETE APPROVED after FULL both409. Approval/comments/withdrawal/upgrade did not send; new Beta direct manual above did.
- A-12 actual transport races: browser wrapper held the already completed real200 FULL A message response, selected B, released A; B header/editor remained, Alpha private body absent. Another held old200 FULL A response was overtaken by actual admin downgrade plus real-page focus obtaining READ; released oldFULL never restored editor. Actual continued-manual-draft PUT200 was held after backend persistence; admin downgrade and focus synchronously removed editor/dialogs, then late200 release left0 editors and no private draft text. Original sent-draft continuation required its real “继续编辑并保存” action; an initial wait before clicking it timed out and was not mistaken for save proof.
- F-07-1 repaired actual modal: desktop1440 FULL profile→tag modal records owner9701. Actual admin downgradeREAD200 then explicit real-page focus synchronously gives modalfalse/bodyempty/titleempty/no owner/0 editors; captured actual promise resolvesnull. CDP DOMDebugger.getEventListeners on real dialog/form/cancel button returns `[[],[],[]]`. Mobile390 revokeDELETE200 plus explicit visible handler gives the same modal/title/body/editor cleanup and null promise. Matching-owner release only: release9702 leaves owner9701 open. Actual replacement `openActionDialog("confirm-typed")` resolves old tagnull; release9701 plus queued native close leaves replacement open, unrelated title intact, no tag owner; replacement cancellation resolvesnull. Actual unmount clears owned modal/title/body/owner/editors and resolvesnull. Native Escape cancels and clears it. Default actual admin tag dialog ownernull survives delegated release and submits expected `"Admin unchanged tag result"`. Permanent actual-dialog regressions additionally cover context switch and no late tag mutation.
- Completed logout follows historical implementation, not a new pre-request cancellation claim: mobile logout handler invoked from actual open modal completes204/navigation to login,0 editors/dialogs/private body. Desktop held already completed real200 A message response then actual logout likewise navigates to login with0 private body/editors/dialogs and old document callback scope undefined. This proves old-document cancellation/destruction, not a forced callback executing in a destroyed document. Revocation followed by real relogin omits A; only mailbox/logout visible, no private Alpha body.
- A-14 real browser1440×1000/390×844: original desktop/mobile profile and native mobile calendar visibly fit; document scrollWidth equals1440/390, malicious tags/name remain text. READ QA薪资/CUSTOM补充材料/malicious HTML labels render with zero edit controls/injected images; [] expert tags render no header chips, null Beta shows“标签暂不可用”. Intentional page fetch failure for A messages shows original“加载失败，请刷新重试”,0 writable editors and no private Alpha body; restored transport afterward. This is deliberate failure injection, not a server failure. Future08 controls remain downstream.
- Real denial snapshot: mapped sender-account/task/unmatched routes, NONE messages, READ expert-tag writes, foreign/READ files, NONE/mixed calendar, valid READ rich request and user grant mutation403; mixed materials400. Snapshot mail_record/mail_send_attempt/attachment/manual-profile/calendar/operator-log counts and ES/SMTP counts unchanged. Foreign schedule/profile denial snapshot independently unchanged. No unsupported-no-handler method was misrepresented as mapped-handler defaultdeny proof; exact wrongverb policy is covered by fresh AuthInterceptorTest. Known inherited foreign-anchor500 was not rerun or repaired.
- Admin baseline: actual original full mailbox/list/filter popover and seven original filter fields visible at1440; same original mobile shell/conversation at390, material/editor controls retained; actual original administrator save/send above succeeded. No final08 UI arbitration was reopened.
- Automation limitations/errors retained: early captures sometimes preceded asynchronous completion; final results above wait for actual response/render. Selector guesses timed out (wrong review action, hidden mobile header refresh, hidden calendar submit on cancel); corrected to observed controls, not source workarounds. Initial review clientRequestId was non-UUID422; canonical UUID retries succeeded. SQL audit/attempt/draft column guesses failed read-only queries and were corrected or not claimed. A raw isolated-world wait referenced a main-world instrumentation variable and generated one pageerror; the held real response was subsequently observed/released through main-world evaluate. Other browser errors were intentional transport aborts/loopback-blocked Google fonts, not an assertion of an entirely empty browser error log.
- Actual screenshots retained as tool artifacts: mobile revoke `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15bad590650047c4.webp`; admin mobile `.../omp-sshots-15bad60d1af9ae14.webp`; READ mobile `.../omp-sshots-15bad627c00047c5.webp`; final desktop relogin `.../omp-sshots-15bad6b9410047c7.webp`. Other profile/calendar images were captured inline; no invented screenshot path.
- Cleanup: real final user logout; both owned browser tabs released. Owned final app exit143; ES/SMTP shutdown/server_close; temporary runtime/log/attachments and classpath file removed. Owned Docker `rm -f expert-access-07-reverify-mysql` exit0. No owned runtime service/scaffold remains. Only this designated verification-log append is written.

### AUTO_FIX
- N/A. F-07-1 is independently closed by the repaired production modal lifecycle evidence above.

### RECORD_ONLY
- O-07-1 — Native OS background/foreground transitions are not established by shared headless Chromium. Focus/visibility evidence is explicitly a real-page handler event; actual native Escape, document navigation/cancellation and completed logout are separately exercised. No fabricated OS transition or forced post-destruction callback claim. Historical5963-test aggregate failures remain in execution/ledger for08/whole-plan review, not rerun/repaired/reclassified as passing here.

### Required Action
- COMPLETE_CHILD

### Controller evidence closing record — 2026-10-31
- Independent verdict/action above remain unchanged: LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD, epoch5 attempt2, terminal code be6437501d88a3eb283ede009eedf5cbca16b117 and one repair.
- Checkpoint correction 2 records this unchanged verifier report together with execution/fix closing facts. Previous replacement 8f2dcca49ceaff5bd17da6bee5c9d9a5b32c63af lacked a verify-log change in that commit. No controller product verification, test rerun or new finding is asserted.
