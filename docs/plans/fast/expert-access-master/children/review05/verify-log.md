# Child review05 — independent light verification

## Epoch1 Attempt1 — 2026-10-30T11:41:41+08:00

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: review05 — docs/plans/2026-10-29/draft-review-05-admin.md
Boundary: 59eb60cf00534a342c1019827a62f2b7b1db0f2b..036fc19c88b3a7f5509bbf167a68db6a8af7a20f
Verifier: ExpertBootstrap05Verifier

Independent verification of this committed child only, using the four lightweight gates. Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, HEAD `036fc19c88b3a7f5509bbf167a68db6a8af7a20f` were observed directly. Execution.md was input evidence, not my verdict. Master lines 206 and 438–444 and child final composition correction lines 262–267 govern: no top pending-review button; later child08 owns the final visible filter entry.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff --name-only ... -- src` returned the 11 files listed below, all within brief Authorized Files; receipt artifact://2005. Other boundary changes are planning/evidence, not additional product/tests. The service patch only changes `requireValidPlaceholders` to existing `requireValidTemplatePlaceholders` at MailReviewDraftService.kt:303, as expressly authorized. Styles append only the seven-line approved S-5 block. |
| Plan and invariants | PASS | mailbox-chat.js:5647–5665 resets and captures review/user/session/contact/target/version identity; 5679–5715 preserves server pending filter/paging; 5749–5804 implements single-editor read-only review and explicit state/send matrix; 5807–5833 and 5908–5940 separate confirmation from send; 5941–6002 handle approval, comments, conflict and uncertain send; 7037–7041, 7210–7225, 8999–9002, 9219–9222 and 9576–9578 guard original manual paths. Direct behavior tests mailReviewAdmin.test.js:69–225 and mailboxServerDrafts.test.js:672 onward passed freshly. Independent real-browser/HTTP/MySQL/SMTP smoke below exercises the changed surface. |
| Required commands | PASS | Every specified frontend/full-JVM/explicit opt-in selection was run freshly. Final corrected-fixture commands all exit 0; full JS 1952/1952; full Maven 5715 tests, 0 failures/errors, 24 default opt-in skips, with required IT selections separately enabled and zero skips. Receipts artifact://2000, artifact://2002, artifact://2055; explicit migration XML was independently read before default Maven overwrote it: 46/0/0/0. Initial fixture-only failure and correction are retained below, not suppressed. |
| Downstream interfaces | PASS | Public narrow `setReviewPendingOnly(active)` remains at mailbox-chat.js:5679–5690 and public handles :10414/:10443; conversationsParams :2055 emits `reviewPendingOnly=true`; reviewSummary :5672–5676 retains server count/loading/error/active callback; cards :2157 retain per-expert counts. Actual HTTP returned reviewPendingTotal=3, expert total=2 and card counts 2/1. Existing controller/model/send routes are unchanged; actual approval, confirm-context, dedicated send and idempotent repeat worked. No obsolete toolbar DOM/binding was introduced; app.js intentionally unchanged. Reader/auth/style and inherited adoption/meeting/attachment regressions passed. |

#### Authorized product/test diff receipt

Command: `git diff --name-only 59eb60cf00534a342c1019827a62f2b7b1db0f2b..036fc19c88b3a7f5509bbf167a68db6a8af7a20f -- src` (exit 0, artifact://2005):

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt
src/main/resources/static/index.html
src/main/resources/static/mailbox-chat.js
src/main/resources/static/styles.css
src/test/js/discoveryRecheck.test.js
src/test/js/mailReviewAdmin.test.js
src/test/js/mailReviewReader.test.js
src/test/js/mailboxMailReadAccess.test.js
src/test/js/mailboxServerDrafts.test.js
src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt
```

The bounded test compatibility changes preserve exact handler/verb allowlisting, rejection assertions, SMTP seam matcher arity, existing module CSS bytes, resource-key consistency and script adjacency. No test skip or production permission/send-contract change was introduced.

#### Fresh required commands and baseline comparison

Frontend chain exit 0, artifact://2000; each command completed:

```sh
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/mailbox-readonly.js
node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js
node --test src/test/js/*.test.js
node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js
```

| Frontend selection | Tests | Pass | Fail | Skip |
|---|---:|---:|---:|---:|
| review05 focused | 330 | 330 | 0 | 0 |
| full JS | 1952 | 1952 | 0 | 0 |
| inherited adoption/meeting/attachments | 65 | 65 | 0 | 0 |
| inherited reader/auth/style | 131 | 131 | 0 | 0 |

JVM environment: `JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home`; freshly executed java -version returned OpenJDK 11.0.33, Zulu11.92+109-CA, build 11.0.33+9-LTS. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`; each Maven invocation included `-DargLine=-Dapi.version=1.41`.

A new disposable `mysql:8.0.36` container `review05-verifier-mysql` supplied external-JDBC fixtures at loopback port 23367. Final datasource: `SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:23367/review05_verify?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`, username root, disposable password verifier. Self-contained Testcontainers tests retain their dynamic datasource overrides.

| Fresh command (all prefixed `mvn test`, all suffixed `-DargLine=-Dapi.version=1.41`) | Exit | JVM tests | Fail | Error | Skip |
|---|---:|---:|---:|---:|---:|
| `-Dtest=MailReviewDraftIT -DmysqlIt=true` | 0 | 12 | 0 | 0 | 0 |
| `-Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true` | 0 | 55 (9+46) | 0 | 0 | 0 |
| `-Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true` (corrected fixture) | 0 | 45 | 0 | 0 | 0 |
| `-Dtest=MailboxConversationControllerTest -DmysqlIt=true` | 0 | 46 | 0 | 0 | 0 |
| `-Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true` | 0 | 45 | 0 | 0 | 0 |
| `-Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest` | 0 | 160 | 0 | 0 | 0 |
| full default `mvn test` | 0 | 5715 | 0 | 0 | 24 |

First two successful commands are in artifact://2002; their chain then reached the query/repository selection. That initial selection exited 1 (45 tests, 4 failures, 0 errors/skips) because I initially supplied `serverTimezone=UTC` against this host's Asia/Shanghai local-date/time binding. Failures were the timeline keyset case and three first-reply-time eligibility cases in unchanged MailboxConversationRepositoryIT. I read the exact time-bound fixture assertions (:393–424, :1047–1104), changed only my external fixture URL to `serverTimezone=Asia/Shanghai`, and freshly reran the complete query/repository selection: 45/0/0/0. No source/test/config repair was made. Every remaining required Maven command then ran, including full Maven; artifact://2055 records explicit `VERIFIER_EXIT 0` for all five resumed selections. The initial failure remains evidence, not a product finding.

Baseline comparison: ledger Baseline and plan T5 document the old JS contract failures, exact reader whitelist/stub mismatches, old Zulu11.0.15 tzdb errors and malformed-placeholder failure. I did not rerun those old baseline states to reconfirm them. Fresh final-source suites above have no remaining failure in those categories. Default Maven's 24 opt-in skips are not database proof; enabled required IT selections separately completed without skips. Maven also ran its bound full JS and syntax gates successfully.

#### Independent browser/backend smoke

This verifier created a new, isolated HTTP application at `127.0.0.1:23806`, using target/classes, existing current Maven dependency classpath `/tmp/fast-draft05-classpath` (including jsoup 1.23.2 and flyway-mysql), JDK11.0.33, actual production static resources served directly from this worktree, and disposable database `review05_smoke` on port 23367. Actual GreenMail 2.1.3 SMTP ran at `127.0.0.1:23026`; IMAP fixture account pointed at unused loopback 23996. ES pointed at refused loopback port 9; discovery, LLM, auto-reply, queue and scheduling were disabled. No production database, SMTP, IMAP, ES or deployment was used.

Observed independently, not copied from writer evidence:

- Real user1 HTTP create/submit produced three SUBMITTED drafts across Verify Alpha/Beta. Real admin conversations query with `reviewPendingOnly=true` returned total=2, reviewPendingTotal=3 and per-expert counts 2/1. Production internal `MailboxChat.mount(...).setReviewPendingOnly(true)` displayed two expert cards and badges 1/2; no `mailboxReviewPendingBtn` existed.
- In real Chromium at 1440px, admin rich body `<b>Admin original verify</b>` and uploaded `review05-verifier-original.txt` persisted through actual original-draft APIs. Selecting user1 draft showed its body read-only with exactly one mc-editor, no original or review send button before approval, and no attachment input. Returning to manual restored exact original HTML plus the original attachment.
- Actual approval produced zero mail_send_attempt rows and zero new outbound rows. A newly inserted isolated inbound row changed the context; explicit send opened the native dialog, width 480px, opaque rgb(255,255,255), initial focus context-cancel. Escape closed it and restored review-selection focus; another explicit send reopened it.
- At 390px the actual native dialog was x=16, width=358, white background, with one editor and reachable confirmation control. Confirming closed the dialog, but database counts remained zero attempts and zero new outbound rows. This is not human acceptance.
- A separate explicit send then invoked the existing safety-confirmation dialog; after deliberate confirmation it used the dedicated reviewed-send route and persisted exactly one SENT attempt and one outbound record, Message-ID `<manual-rich-a93698865f3d4f339c0fca101eb62316@weibo.com>`. GreenMail received the recipient transaction (its log created alpha@fixture.test); repeating the same review send over actual HTTP returned the same Message-ID with attempt/outbound counts still exactly one.
- For Beta, a real context-confirm HTTP call returned event 13. A later isolated inbound row invalidated that event: actual send returned `409 REVIEW_CONTEXT_CHANGED`; attempt count stayed one (the earlier Alpha send only).
- Actual last-pending Beta approval removed Beta from the pending left list while retaining selected review:3, Beta body and explicit send button on the right, without changing recipients.
- Actual comment POST displayed the verifier comment plus real actor/time/COMMENT, APPROVE, SUBMIT and CREATE history.
- Successful provider translation and delayed-response checks were deliberately **browser fixture-only**, not a claim about a live translation provider: wrapped browser fetch returned a translation for Beta, shown separately in the production translation region while original `<p>Beta review body.</p>` remained unchanged; computed small button font=11px and translated region font=12px. A held Alpha translation resolved only after switching to Beta and did not appear in Beta's DOM or editor.
- Browser error collection was empty. Tool invocation corrections (file upload requires a real path; Escape helper takes a key, and an offscreen send required scrolling) were automation setup only and did not require product changes or replay successful sends. An initial Beta submission without a real target correctly returned 422; adding a real isolated inbound fixture allowed submission.

The application process, managed Chromium tab, SMTP container and database container created by this verifier were stopped; both disposable containers used --rm. Runtime/fixture-only files stayed under /tmp. No product/test/index/commit/branch/plan/ledger/execution report was written by this verifier.

### AUTO_FIX
- N/A — no proven four-gate violation requiring an authorized uniquely determined product correction.

### RECORD_ONLY
- O-1: Initial verifier external-JDBC URL used UTC and caused four failures in unchanged time-sensitive repository fixtures (artifact://2002). Correcting only the disposable fixture URL to the existing application's Asia/Shanghai convention yielded 45/0/0/0, followed by every remaining required gate passing (artifact://2055). Retained as environment evidence; not an authorized product/test repair.

### Required Action
- COMPLETE_CHILD
