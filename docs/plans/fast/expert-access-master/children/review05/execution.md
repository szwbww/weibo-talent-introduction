# Child review05 — execution

## Resume execution — 2026-10-30

### Identity and retained-work reconciliation

- Mode: `RESUME`; implementation evidence, not independent verification or human acceptance.
- Approved master identity: `08d6af05bae3349ca46f92df61b48ab0bac87183`; child base: `59eb60cf00534a342c1019827a62f2b7b1db0f2b`.
- Approved child: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/draft-review-05-admin.md`.
- SHA-256: `614aa3346654e13727d9211ae32009493c355c477ac6a003a17a7c183f9c609e` (31,345 bytes).
- `EXECUTION_ID`: canonical child path above `@614aa3346654e13727d9211ae32009493c355c477ac6a003a17a7c183f9c609e`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Starting HEAD: `e706afc87b3696ded01af140ecbff9254c368138`.
- Preserved and reconciled the failed writer's existing product/test diff against the exact approved child and final master arbitration; did not wipe, replay, or commit its work separately.
- Final arbitration applied: **no `mailboxReviewPendingBtn`, no toolbar binding**. Pending filtering is the public internal `setReviewPendingOnly` interface with server totals/pagination and per-expert badges. The later-child entry point was not implemented or reviewed.
- The controller-owned pre-existing ledger modification was preserved and excluded from the product commit. No plans were changed.

### Product and test scope

Changed authorized product/tests:

1. `src/main/resources/static/mailbox-chat.js`: shared single-editor review mode, original owner/target draft flush/restore, reviewed-state-only dedicated sends, native context confirmation, translation/discussion/history, server pending filtering, sequence/identity isolation, safe unknown/in-progress presentation.
2. `src/main/resources/static/index.html`: uniform `20261029-review-admin` resource keys; no new toolbar control.
3. `src/main/resources/static/styles.css`: only the approved verbatim S-5 native-dialog block.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`: retained authorized strict placeholder-validation correction only.
5. `src/test/js/mailReviewAdmin.test.js`: public DOM/API behavior, state matrix, confirmation/cancellation/stale response protection, paging, exact approved CSS and absent-toolbar contract.
6. `src/test/js/mailboxServerDrafts.test.js`: old draft ownership/flush boundary regression and bounded existing test sandbox compatibility.
7. `src/test/js/mailReviewReader.test.js`: approved cross-step resource/CSS coexistence compatibility.
8. `src/test/js/discoveryRecheck.test.js`: approved EOF-position-only compatibility.
9. `src/test/js/mailboxMailReadAccess.test.js`: retired only the fixed obsolete cache-key suffix assertion.
10. `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`: approved exact reader handler/verb matrix.
11. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`: approved matcher compatibility only.

`app.js` and `batchEntryRelocation.test.js` were intentionally unchanged: final no-top-toolbar-button arbitration removes the integration/order change they would otherwise need. No backend APIs, migrations, shared sanitizer, permissions, ordinary-send authority, or later-child behavior were added.

Final scope/identity and whitespace receipt: `artifact://1981` (`git diff --check` exit 0; status lists the authorized product/tests and controller ledger only at that point).

### Final-source frontend commands

The last product edit preceded this entire command chain. Exit 0, receipt `artifact://1926`:

```sh
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/mailbox-readonly.js
node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js
node --test src/test/js/*.test.js
node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js
```

- Review05 focused suite: 330 passed, 0 failed, 0 skipped.
- Entire JavaScript suite: 1,952 passed, 0 failed, 0 skipped.
- Inherited send/adoption/meeting/attachment suite: 65 passed, 0 failed, 0 skipped.
- Inherited reader/auth/style suite: 131 passed, 0 failed, 0 skipped.
- Earlier failed execution evidence was superseded by the final-source run: own test/public-handle and asynchronous assertion corrections, bounded existing logout test sandbox compatibility, and the explicitly authorized old cache-key assertion retirement. No product behavior was weakened to satisfy a test.

### Actual isolated backend/browser smoke

Real Chromium, actual source-served `index.html`/JS/CSS, current compiled application classes and current Maven-resolved dependencies, JDK 11.0.33 Zulu11.92+109, disposable MySQL 8.0.36 and GreenMail 2.1.3. HTTP `127.0.0.1:23805`; database `127.0.0.1:23365/review05`; SMTP `127.0.0.1:23025`. ES was redirected to refused loopback, and LLM, mail queue and automation scheduling were disabled. Attachment storage was isolated at `/tmp/review05-resume-attachments`. Only `.test` recipients were used. No production database, SMTP, IMAP or Elasticsearch was contacted.

The first runtime attempt using an inherited expanded-WAR dependency directory lacked the test-scoped Flyway MySQL extension; the next exposed an obsolete jsoup dependency. Both were stopped. The smoke was then executed successfully using `mvn dependency:build-classpath -Dmdep.outputFile=/tmp/review05-resume-runtime.classpath -Dmdep.includeScope=test`, `target/classes` and the resulting current dependencies. These were fixture/runtime setup corrections, not repository changes. Fresh Flyway migration created version 169 with 168 migrations; subsequent starts validated the isolated database. Startup's refused-loopback ES warnings were expected isolation, not a successful ES test.

Observed assertions:

- Three user1 submissions across two experts: backend `reviewPendingTotal=3`, expert `total=2`, card counts 2/1. Internal pending filtering selects the submitted draft without a popup; no top toolbar review button exists.
- Actual original admin rich HTML plus one uploaded text attachment persisted before review. User1 review body was read-only, exactly one `mc-editor`, no send node before approval; switching back restored exact HTML and the attachment. The original draft remained available through the ordinary draft chip after expert/context changes.
- Approval had zero attempts/outbound records. Native context dialog cancellation and Escape refreshed the actual updated conversation and restored focus to the selector. Confirmation alone still had zero attempts/outbound records.
- A subsequent real inbound database row invalidated the prior confirmation. Sending with the old event and explicitly acknowledged existing safety findings returned `409 REVIEW_CONTEXT_CHANGED` with zero attempts.
- Separate explicit send invoked the existing actual safety-confirmation dialog and dedicated review endpoint, produced one `SENT` attempt/outbound record, and one GreenMail `MAIL FROM`/recipient transaction. Repeating the same reviewed-send identity returned the same message and did not create another SMTP transaction. Actual multipart plain-text/HTML delivery was observed (`artifact://1954`).
- An ordinary original manual reply was then sent without review approval, including the preserved attachment: totals became two attempts, two outbound records, two SMTP transactions. This is separate from the single reviewed-send assertion.
- A separate isolated Chromium browser context logged in as user1, edited the returned draft and resubmitted it (real state `SUBMITTED`, version 5). Its mailbox showed the expert name and no review send/attachment inputs. Admin-approved last pending drafts removed the corresponding left-list expert while retaining the right expert, approved content and explicit send button. Final pending total was zero.
- Actual comments were safely escaped (`<img ...>` remained text, no injected image), actor/time/action history loaded 20 events followed by 6 older events, and deleted draft #4 exposed no open-editor action—only summary and audit events.
- The actual translation endpoint returned `TRANSLATION_DISABLED`, as configured. **Provider-success and slow-response evidence is fixture-only**: browser-controlled translation responses on the real page proved unchanged subject/HTML, a separate translated-text region, per-version repeat-request caching, and a late Alpha response not appearing in Beta. Computed button font was 11px; translation was 12px with 22.2px line height (1.85).
- **Unknown/in-progress evidence used explicit isolated persisted-state injection**, not a claim of a natural SMTP failure: the existing bound fixture attempt was temporarily set to `DELIVERY_UNKNOWN`, then `DELIVERY_IN_PROGRESS`; real backend projections produced warning text and no send node in the real browser. The original `SENT` state was restored, and send/SMTP totals did not increase.
- Desktop 1440px: opaque white native dialog, width 480px; initial cancel focus, keyboard traversal and blocked focus into underlying navigation. Browser chrome/body may be the native modal's interim Tab stop; no underlying page control received focus. Escape behaved as cancel.
- Mobile 390px: opaque white native dialog fit x=16..374, width 358px; buttons were reachable, one editor remained, cancel returned to the newest conversation. Screenshots: `/tmp/review05-resume-desktop-context.png`, `/tmp/review05-resume-mobile-context.png`, `/tmp/review05-resume-reader.png`.
- Smoke summary: `/tmp/review05-resume-smoke.json` and `artifact://1980`. No JavaScript errors remained after the final runtime start; earlier connection-refused browser entries were from deliberately stopping obsolete test runtimes.

Test-helper assertion mistakes (wrong HTTP delete expectation, localized reader state label, event panel selector/name, and waiting for an ordinary-send success string not rendered by that path) were reconciled against actual persisted outcomes without replaying successful writes. Database/SMTP and browser state, not those assumptions, are the evidence above.

Cleanup completed: all created runtime processes were stopped, both disposable `--rm` containers were stopped/removed, and the managed admin tab plus separate reader contexts were closed. Screenshots and the JSON evidence remain under `/tmp` for the controller; no fixture files were added to the repository.

### Explicit JVM gates — completed first phase and fixture correction

All JVM commands used the actual Zulu JDK at `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home`, Docker at `unix:///Users/lukai/.orbstack/run/docker.sock`, and `-DargLine=-Dapi.version=1.41` for the local Docker API compatibility prerequisite.

Completed after the last product edit:

```sh
mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true -DargLine=-Dapi.version=1.41
mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

- `MailReviewDraftIT`: 12 tests, 0 failures/errors/skips.
- `AuthFlowIntegrationTest`: 9 tests, 0 failures/errors/skips.
- `FlywayMigrationIntegrationTest`: 46 tests, 0 failures/errors/skips.
- Receipts: the sequential chain advanced past both successful commands (`artifact://1924` / recovered job receipt `artifact://1985`); the corresponding final-source Surefire XML suite headers were separately read before later default-test runs could overwrite them.

The chain then stopped at the explicit query/repository gate: `MailReviewQueryIT` passed its 5 tests, but the inherited `MailboxConversationRepositoryIT` attempted its documented external `127.0.0.1:3306` datasource and all 40 tests errored with connection refused. This is an absent **test fixture prerequisite**, not a confirmed production defect and not a reason to repair unrelated tests. The failure is retained in `artifact://1924` / `artifact://1985`.

Source evidence: `MailboxConversationRepositoryIT` documents external JDBC and has no test-owned container; `MailboxConversationRealJdbcConfig` reads `spring.datasource.url/username/password`. A separate disposable MySQL 8.0.36 fixture was therefore started at `127.0.0.1:23366/review05_gates`, and the remaining commands were resumed with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` pointing only to that isolated database. Self-contained Testcontainers tests retain their own dynamic datasource overrides. No source/test change was made for this environment correction.

Temporary Java arg/classpath/upload fixtures and isolated upload storage were removed after the browser smoke; the evidence JSON/screenshots were retained.

### Final-source JVM gate completion

Resumed sequentially with the isolated external JDBC fixture described above; exit 0 for the whole chain, receipt `artifact://1988`:

```sh
mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true -DargLine=-Dapi.version=1.41
mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true -DargLine=-Dapi.version=1.41
mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true -DargLine=-Dapi.version=1.41
mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest -DargLine=-Dapi.version=1.41
mvn test -DargLine=-Dapi.version=1.41
```

| Command selection | JVM tests run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `MailReviewQueryIT,MailboxConversationRepositoryIT` | 45 | 0 | 0 | 0 |
| `MailboxConversationControllerTest` (explicit `mysqlIt=true`) | 46 | 0 | 0 | 0 |
| `MailReviewSendIT,MailReplyDraftSendIntegrationTest` | 45 | 0 | 0 | 0 |
| `ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest` | 160 | 0 | 0 | 0 |
| Complete default Maven test suite | 5,715 | 0 | 0 | 24 |

Every command also completed the Maven-bound full JavaScript suite: 1,952 passed, 0 failed, 0 skipped, and bound JavaScript syntax checks. Default Maven's 24 opt-in skips are reported explicitly and are **not** treated as database evidence; the required review/auth/migration/query/controller/send selections above were separately enabled and had zero skips.

The last product edit preceded all successful final-source frontend and JVM command evidence. No source changes followed those commands. No package/build, independent verification, human acceptance, release, push or merge is claimed.

The separate `review05-resume-gates-mysql` disposable container was stopped/removed after completion. All test services created for this assignment are now stopped. The approved plan bytes remained unchanged.

### Handoff

- Result: `READY_FOR_VERIFICATION` (controller's independent verification and eventual human acceptance remain separate).
- Product commit: `036fc19c88b3a7f5509bbf167a68db6a8af7a20f` — `feat(fast-p): implement review05`.
- Commit contains exactly the 11 authorized product/test files listed above, 713 insertions / 40 deletions; no report, plan, ledger or fast-p artifact was staged.
- Pre-add/pre-commit expected-root/branch/git-dir identity gates passed. Final gate receipt `artifact://1994` confirms the unchanged approved plan SHA-256, expected worktree/branch, new HEAD and ancestry from both starting HEAD and child base.
- Product worktree/index is clean after the commit. The only remaining tracked modifications are this execution report and the preserved controller-owned ledger. No unrelated repair, later-child review, push, merge or history rewrite was performed.
- All required commands and actual isolated browser/backend smoke are documented above; no blocker or plan conflict remains.
