# Aggregate Machine Verification — draft-review-master

## Epoch 1 — 2026-10-30T15:44:01+08:00

- Master plan: docs/plans/2026-10-29/draft-review-master.md (commit:32407a46d5b05971fc913cda1802b6ba002e8dc7)
- Governing master identity: sha256:a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b
- Master identity state: AMENDMENT_RECORDED; exactA1–A18 approval/rule records in fast-p ledger and complete report below; invoked copy sha256:63472802e91831bff5295d9e3f63a9af81d60d69471c6cd8bb2ec76570e4f3ff, governingdiffartifact1541.
- Boundary: b15bb069f43f89c45ee6859dba04a0baff5d774d..bec9e40faae946b0971e95af9c3a9edec83cd888
- Reviewer: DraftReviewAggregateReviewer3
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/draft-review-master/repair.md; sha256:52b488ad5e1fa4dc3eba3b1ae60e71dab62bec9bf493c8daed48b88047a02b9c; DRAFT_READY
- Evidence parent before this evidence commit: d309757a23bfffd5c0086ce1682aa638dd7f851e
- Human acceptance: PENDING

The following is the complete returned review-p report, preserved without shortening failed or unavailable evidence. Controller performed orchestration only; no product code or test repair.

## Verification Result: FAIL

Plan: `docs/plans/2026-10-29/draft-review-master.md`, aggregate/master phase, with ordered children 01–05 and recorded amendments A1–A18.
Implementation boundary: `b15bb069f43f89c45ee6859dba04a0baff5d774d..bec9e40faae946b0971e95af9c3a9edec83cd888`.
Evidence HEAD: `d309757a23bfffd5c0086ce1682aa638dd7f851e`.
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`; branch `fast/draft-review-master`.
Reviewer: `DraftReviewAggregateReviewer3`.
Convergence: INITIAL.
Manual acceptance: PENDING.

The fresh mandatory command gates ultimately pass, but a newly demonstrated mandatory context-freshness violation makes the aggregate result FAIL. Passing tests do not override the real transaction/HTTP counterexample.

### Governing identity and evidence boundary

- Read the exact supplied input `local://draft-review-aggregate-input.md`, complete worktree master and ordered child plans, fast-p ledger/handoff, child execution/verification/fix evidence, complete implementation diff, and all A1–A18 amendment rows and their corresponding plan-only patches.
- Governing master: commit `32407a46d5b05971fc913cda1802b6ba002e8dc7`; worktree SHA256 `a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b`.
- Invoked original-copy SHA256 `63472802e91831bff5295d9e3f63a9af81d60d69471c6cd8bb2ec76570e4f3ff` differs. Read the complete supplied diff `artifact://1541:raw`; its future permission/UI work is not governing scope. Identity state: AMENDMENT_RECORDED, not permission to adopt the original-copy additions.
- Child current SHA256 identities: 01 `5259533f437110da772b8a2035c84957e6b24a3dbb0b79b1a455f7e1dd2d63fd`; 02 `55fc2fcc6a63d069019d14c570f579e7468f33cf1e1d0f28a5be5082e45f3752`; 03 `f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7`; 04 `2eadefbae3db8205e028ce2cebad46b66f7912ce5fe465d33a7947496ed3c97a`; 05 `b9669ef5dac2ccb10b8f4a64cdd6c9d3b09d7881f489c5b3eee8c303fbcbf5f8`.
- Ledger SHA256 `0b43a67cb5ad705d1a498576ccf265c21d3d049b5c19c69260d259f0ac640e2a`; handoff SHA256 `25c2d1f50b0ff45a7b403fa836a2c3a62039062348de905a8bb32bebc2f71d05`.
- Earlier interrupted reviewer processes/logs were treated as interrupted, not PASS or a prior aggregate verdict. Resources were stopped only after explicit ownership transfer and PID/command matching. Historical child LIGHT_PASS and browser evidence were not substituted for this review's commands/runtime.
- Mechanical per-child changed/authorized path counts are 11/11, 6/6, 8/8, 8/8, 14/14, with no unauthorized paths; the implementation union matches the authorized union. Receipt: `/tmp/draft-review-aggregate-reviewer3/scope.json`, produced from the recorded child boundaries and exact plan whitelists. Complete amendment-patch receipt: `amendment-commits.txt` in the same directory. No previously applied migration was changed; V169 adds the two review stores, not columns to existing shared tables.

### Commands

All fresh command logs and actual argv/exit/timing metadata are retained under `/tmp/draft-review-aggregate-reviewer3/` (`commands.json`, `command-summaries.json`, `external-gates-corrected.json`). Maven was sequential with normal compilation enabled.

Common environment:
`JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home MAVEN_OPTS=-Xmx3072m DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`.

The fresh Java-version diagnostic identified genuine OpenJDK11.0.33 / Zulu11.92+109-CA. Retained official archive SHA256: `4947b01fcb220ee69710dbf63375f4628688d84750fccd289f5a6d62b494aa94`. No timezone source workaround or compilation/test suppression was used.

| ID | Exact fresh command | Result | Evidence |
|---|---|---|---|
| C01 | `node --check src/main/resources/static/mailbox-chat.js` | PASS, exit0 | C01.log |
| C02 | `node --check src/main/resources/static/app.js` | PASS, exit0 | C02.log |
| C03 | `node --check src/main/resources/static/mailbox-readonly.js` | PASS, exit0 | C03.log |
| C04 | `node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js` | PASS, exit0; 334 pass, 0 fail/skip | C04.log |
| C05 | `node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js` | PASS, exit0; 131 pass, 0 fail/skip | C05.log |
| C06 | `node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js` | PASS, exit0; 65 pass, 0 fail/skip | C06.log |
| C07 | `node --test src/test/js/*.test.js` | PASS, exit0; 1956 pass, 0 fail/skip | C07.log |
| C08 | `mvn test '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit0; JVM5715, 0 failures/errors, 24 default opt-in skips | C08.log; 754.07s |
| C09 | `mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit0; 12, 0 failures/errors/skips | C09.log |
| C10 | `mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit0; 55, 0 failures/errors/skips | C10.log |
| C11 initial | `mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | FAIL, exit1; 45, 0 assertion failures, 40 errors; external DB connection refused | C11.log; retained, not erased |
| C11R | Same exact C11 command, explicit corrected owned external DB environment | PASS, exit0; 45, 0 failures/errors/skips | C11R.log:751,1022,1028,3710; 238.13s |
| C12 initial | `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | FAIL, exit1; 46, 0 assertion failures, 46 errors; external DB connection refused | C12.log; retained, not erased |
| C12R | Same exact C12 command, explicit corrected owned external DB environment | PASS, exit0; 46, 0 failures/errors/skips | C12R.log:610,614,3296; 200.60s |
| C13 | `mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit0; 45, 0 failures/errors/skips | C13.log |
| C14 | `mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit0; 160, 0 failures/errors/skips | C14.log |

The initial C11/C12 environment failures are not product findings and are not silently reported as passing initial runs. Corrected runs used the owned container `draft-reviewer3-runtime-mysql`, explicitly `DB_URL='jdbc:mysql://127.0.0.1:33406/reviewer3_gates?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=runtime3-local`. The runtime application was stopped while these corrected Maven gates ran. The initial external endpoint's refusal is observed; no unsupported claim about its underlying cause is made.

All required named JVM groups ultimately executed with 363 target tests and zero target skips. Each successful Maven invocation also ran the configured Node suite1956/1956 and syntax executions. Full Maven's 24 default opt-in skips are disclosed, not counted as enabled database proof. No WAR packaging/deployment claim is made.

Fresh query receipt: C11R.log:749 reports `REVIEW_QUERY_SIZE100 rows=100 sqlCount=10 reviewSqlCount=2`; line750 shows actual EXPLAIN using `idx_review_draft_pending`. This is measured behavior, not a fixed-query-count or production performance guarantee.

### Fresh genuine runtime proof

Used production compiled application/controllers/services/repositories and current static assets against a separately owned real MySQL runtime schema. No production bean or repository was replaced. A disposable external GreenMail fixture provided actual SMTP33407/IMAP33408, with a control endpoint33409. A local deterministic translation-provider fixture33410 was reached through the actual translation service; it is not evidence of a real provider's translation quality. Authentication was enabled, with separate actual admin/user1 cookie contexts and browser logins. Scheduling, queue automation and LLM automatic reply were disabled. All addresses were `.test` fixtures; ES was explicitly loopback port9, not a production service.

1. Actual restricted-reader browser UI created, edited, saved and submitted draft1 using native contenteditable and bold toolbar. Actual API actions commented, returned, revised and resubmitted it; canonical HTML and complete CREATE/SAVE/SUBMIT/COMMENT/RETURN/SAVE/SUBMIT history persisted.
2. Additional real draft submissions produced a pending total3 across two experts, per-expert2/1, including size1 server pagination. The actual admin toolbar and expert cards displayed the server values, not a separate draft-list dialog.
3. Admin selected a submitted review into the existing single editor. The editor was read-only; ordinary send and review send were absent. In manual mode a real original body/subject and uploaded attachment were saved. Repeated switches review→manual retained the settled original body/subject/attachment; review did not inherit the attachment. An initial attachment-label comparison was made while upload was still settling and differed only by 上传中→待发送; the settled repeat was equal, so it is not reported as data loss.
4. Actual UI approval of draft2 left SMTP-to-expert count zero, decreased pending count, removed the last pending Alpha entry from the filtered left list, and retained Alpha's right-hand approved draft for explicit send.
5. Real SMTP injected inbound messages into GreenMail. The production IMAP auto-reply endpoint fetched/recorded them and returned manual review with zero automatic replies. Thus changed-context proof included genuine inbound ingestion, not just direct fixture inserts.
6. Actual native context dialog was exercised at CSS viewport1440 and390. Desktop measurement: width480, opaque white background, initial cancel focus; Tab reached confirm, Escape cancelled. Mobile: x16, width358/right374 in390 viewport, both actions44px high. Cancel/reopen worked. Confirm-only persisted an event and left draft2 NOT_SENT with no SMTP-to-expert message.
7. A further actual IMAP arrival invalidated that confirmation. Actual dedicated send with the old event returned409 REVIEW_CONTEXT_CHANGED and no outgoing SMTP. A fresh UI confirmation still did not send; the subsequent separate explicit UI send opened the existing identity/safety confirmation. Confirming that safety prompt sent exactly one reviewed message, bound attempt1 and mail_record90005, with SEND_CLAIMED/SEND_SUCCEEDED events.
8. Repeated actual dedicated HTTP send returned the same SENT message ID without another message. Review state remained APPROVED with sendStatus SENT. Actual mobile UI showed one editor, 已发送, and no send button.
9. Switching back preserved the original manual text and ready attachment. A separate ordinary manual UI send, without review approval, traversed the existing identity/safety prompt and delivered a second outgoing message with the original attachment.
10. Parsed actual expert-inbox MIME: reviewed mail `multipart/alternative` with text/plain and text/html, no attachment; ordinary mail `multipart/mixed` with text/plain, text/html and `reviewer3-attachment.txt`, preserving its fixture content. These are new reviewer deliveries, not historical executor MIME.
11. Actual draft-scoped translation rendered below the body with an11px small-text control and left frozen HTML unchanged.
12. Actual reader native list and sanitizing paste handlers were exercised; server canonical HTML retained list/bold while excluding image/event/JavaScript URL content. Native dirty-editor caret and HTML remained unchanged across a focus-triggered refresh. Live grant revocation yielded actual HTTP403; the reader cleared timeline/editor/discussion and hid the review section. Reader mobile/desktop surfaces retained restricted navigation, no attachment input and no fixture expert email in the mailbox DOM.

Durable fresh runtime evidence:
- `/tmp/draft-review-aggregate-reviewer3/authenticated-http-receipts.json`
- `final-runtime-receipt.json`, `browser-final.json`, `dirty-caret.json`
- `fresh-approval-lock-race.json`, `fresh-confirmation-lock-race.json`
- `reviewed.eml`, `ordinary.eml`
- `reader-wide.webp`, `reader-mobile.webp`, `reader-revoked.webp`, `dialog-wide.webp`, `dialog-mobile.webp`, `admin-mobile-sent.webp`
- `runtime-app.log`, `runtime-app-final.log`, `cleanup.json`.

Chromium reported a managed headless backend, although headed opening was requested. These are genuine browser/native-DOM executions and screenshots, not human visual acceptance. A transient detached-page automation error was inspected before retrying the enabled actual button; no send was blindly duplicated. Initial guessed-selector/automation-call failures were corrected against the actual DOM and are not product failures.

### Contract Matrix

All machine contract rows below are mandatory unless marked otherwise. Evidence abbreviations: Cxx are the exact fresh command receipts above; RT is the fresh production HTTP/MySQL/browser/SMTP/IMAP evidence above. Source paths are relative to the worktree; service/repository short names refer to their existing mail/service or mail/repository paths.

| ID | Requirement and source | Verdict | Required evidence and actual result |
|---|---|---|---|
| R1 | Master demand1: authorized reader, multiple rich drafts, lifecycle, comments/full history, approval lock | PASS except context item separately below | C05/C09/C10; actual native draft, return/revise/resubmit and immutable approved detail; service/controller access/state/CAS paths |
| R2 | Master demand2: server draft count, expert list, original draft chip, one shared admin editor, original body/attachments retained | PASS | C04/C11R/C12R; RT3/2 experts, card2/1, one-editor and original attachment round-trip |
| R3 | Master demand3: approve≠send, explicit reviewed send, latest-context confirmation, inline translation/history | FAIL in authoritative context freshness | RT proves normal explicit-send/confirm separation and inline translation; V-1 proves stale approve/confirmation under contention |
| I-1 | Session actor, exact roles, current grants on every reader action, owner-only drafts, never-submitted admin privacy | PASS | MailReviewDraftService access/requireActor/requireReader/requireAdmin; strict controller; MailReadOnlyPolicy; C09/C10 and RT403 clearing |
| I-2 | DRAFT/SUBMITTED/RETURNED/APPROVED transitions; frozen approved content; soft delete | PASS | Service transition handlers and writable/state checks; C09; RT persisted transition history and approved read-only mode |
| I-3 | Version CAS and complete snapshots/event atomicity; comments serialize without version bump; failed/no-op writes do not invent history | PASS | MailReviewDraftRepository change/appendEvent and transaction guard; C09 rollback/concurrency/history; RT canonical snapshots |
| I-4 | Only two new stores, append events, no old admin-draft pollution, retained soft-delete history | PASS | V169, models/repository, complete diff/scope receipt; C09/C10/C04; no existing-table schema delta |
| I-5 | Server Safelist/canonical HTML-derived text; strict fields/IDs; byte/subject/comment limits; no placeholders; final length | PASS | Service canonical/validateSubmission including strict validator; Controller strict JSON handling; C09/C10/C13; RT native paste canonicalization |
| I-6 | Full real context digest and freshness for approve/confirm, independent of UI filters | FAIL — V-1 | Digest construction itself is correct at MailboxConversationRepository:33–69; fresh lock-contended production approve and confirmation both accepted an already stale token |
| I-7 | Immutable identity/target; authoritative processing/outbound anchor; grouped approval fields and send binding | PASS | Repository target/target validation and service creation/approve/bind paths; C09/C13; RT inbound/outbound fixture targets and fixed send request |
| I-8 | Pending draft total vs expert total/per-expert count; shared SQL membership before pagination | PASS | Repository pendingCounts/pendingTotal/expertPredicates; C11R/C12R and RT total3/experts2/page size1 |
| I-9 | Admin-only optional pending filter, account scope, existing filters/chips and reader zero/no-summary reads | PASS | Controller→service role branch→filter SQL; C10/C11R/C12R and C04/C05 |
| I-10 | Immediate state-driven counts; revoked grants retain admin record; deleted/missing account/contact excluded; no cached truth | PASS | Shared EXISTS/summary queries; C11R/C12R; actual approval decremented toolbar and removed left entry while retaining right draft |
| I-11 | Dedicated admin-only frozen send; no client content/attachments; approval/confirmation SMTP0 | PASS | MailReviewSendService:11–24 and strict send request; C10/C13; actual confirm-only0 and reviewed MIME without attachment |
| I-12 | Send-time context recheck and exact current confirmation; confirm alone never sends | PARTIAL / FAIL for confirmation freshness via V-1 | Existing send-time seam explicitly READ_COMMITTED at ManualReplySendAttemptService:280–304; C13 and actual stale-event409/noSMTP pass. The producer confirm-context can nevertheless record an unseen old token, V-1 |
| I-13 | Review-domain fingerprint, ordinary bytes unchanged, fixed request/attempt; SENT dedup and UNKNOWN/IN_PROGRESS block | PASS | Attempt fingerprint/prepare and bound-result paths; C13/C14; real dedicated replay returned same SENT message without extra SMTP |
| I-14 | APPROVED remains state; truthful attempt/mail_record projection and transactional send events; no duplicate audit | PASS | Repository sendProjection/appendSendResult and attempt finalization; C13/C14; RT attempt1/mail90005/events and retained body |
| I-15 | Ordinary manual/AI/QA/attachments/meeting/identity/suppression behavior unchanged, no historical AI approval gate | PASS | Optional review reference branches and unchanged ordinary fingerprint test; C06/C13/C14; actual ordinary unreviewed attachment send |
| I-16 | Independent restricted reader, precise method/path guards, name-only DOM, no admin/attachment/send/translation expansion | PASS | app.js exact allowlist; mailbox-readonly dedicated module; C05/C10; RT restricted DOM and403 revocation |
| I-17 | One current reader draft; explicit canonical save, dirty flush, conflict preservation, dirty beforeunload/focus behavior | PASS | mailbox-readonly save/flush/navigation paths; C05; actual dirty native caret/HTML unchanged across focus refresh |
| I-18 | Captured request identity/seq, stale-response isolation, immediate401/403 clear, abort/listener cleanup, no localStorage truth | PASS | mailbox-readonly request/reset/destroy paths; C05; actual revocation clears all displayed/new review state |
| I-19 | Unique toolbar count, expert filtering, flush/chip/page semantics and old-chip exit | PASS | app.js toolbar bridge and mailbox-chat conversationsParams/filter/render; C04/C11R/C12R and RT |
| I-20 | Existing single editor; capture owner/target; no review autosave/tools/manual-send contamination; preserve attachments and stale-callback guards | PASS | mailbox-chat reviewCapture/reviewCurrent/select and consuming tool/retarget guards; C04 controlled late GET/dialog regressions; RT original manual body/attachment preserved and later delivered |
| I-21 | Hidden unapproved send; dedicated approved explicit send; SENT/UNKNOWN UI; approve does not send | PASS | mailbox-chat renderAdminReview/mutation/send; C04/C13; real submitted no send, approval0SMTP, sent-state no button |
| I-22 | Modal cancel/Escape clears confirmation; confirm≠send; recheck; source-isolated inline translation/comments/history | UI PASS; server confirmation limitation V-1 | mailbox-chat context/dialog/send and captured callbacks; C04; actual1440/390 cancel/Tab/Escape/confirm/reconfirm/translation. Authoritative confirmation freshness is not declared passing |
| I-23 | Ordered child boundaries and exact whitelist exceptions; no shared-table columns, no parallel shared-file implementation | PASS | Recorded code/evidence boundaries, ledger amendments and full patch audit; scope.json counts11/6/8/8/14; amendment matrix below |
| I-24 | Distinguish plans/prototypes/historical/fresh proof; real API, current assets/cache keys, full non-skipped required targets | PASS | Identity/amendment receipts, complete diff, actual production RT and fresh C01–C14/C11R/C12R; all limitations disclosed |
| S-1 | Reader editing/history structure, exact shared CSS, safe native bold/list/paste/caret | PASS | C05 literal DOM/CSS checks and real native contenteditable work;1440/390 reader captures; dirty caret receipt |
| S-2 | Existing restricted shell/identity badge/inline feedback, no new toast or capabilities | PASS | Current index/app/reader paths, C05; actual restricted reader DOM and cleared revoked surface |
| S-3 | Existing toolbar insertion/expert badges; hide outside live admin; no draft-list dialog | PASS | index/app narrow bridge, C04; actual server count/card list and screenshots |
| S-4 | One original editor, read-only review/tools matrix, small inline translation/history/comments | PASS | mailbox-chat current consuming dispatch and C04; actual one-editor,11px translation, preserved manual attachment |
| S-5 | Only context uses native opaque dialog; keyboard/mobile reachable controls; exact CSS | PASS | C04 CSS contract; actual white rgb255 background, desktop480/mobile358px, Tab/Escape and44px actions |
| 01 T1/T2/T4 | Two stores/minimal models/repository, strict named APIs, real transactions/auth/migration tests | PASS | Complete V169/controller/service/repository diff; C09/C10; input/auth/history/rollback target coverage |
| 01 T3 | Streaming complete hash and fresh observed-context admission | FAIL — V-1 | Streaming hash passes source/query tests; stale snapshot violates explicit post-lock observed-token contract |
| 02 T1–T3 | Optional DTO/filter, shared EXISTS and batched counts, actual pagination/EXPLAIN/legacy fixtures | PASS | C11R/C12R, sqlCount10/reviewSqlCount2 measurement; RT expert pagination/counts |
| 03 T1–T4 | Frozen adapter, final rendered comparison, transaction binding/dedup, normal send regression | PASS with confirmation producer caveat | C13/C14 and actual MIME/idempotent send; send-time checks remain effective despite V-1 |
| 04 T1–T3 | Exact reader network/lifecycle, canonical history UI, unified resources and native browser proof | PASS | C03/C05/C07/C10; actual reader native editing and revocation; current source identity guards |
| 05 T1–T5 | Toolbar/expert list, shared editor, actions/history/translation, explicit dialog/send, assets/regressions | PASS UI; inherited I-6 FAIL | C01/C02/C04/C07; actual UI/send/runtime; V-1 is the inherited backend defect, not a new UI finding |
| Keep-1 | Existing mail timeline/filter/pagination and old admin draft chip | PASS | C04/C11R/C12R; existing query predicates and ordinary draft routes retained |
| Keep-2 | Ordinary AI adoption/QA/manual/attachments/meeting/suppression/safety | PASS | C06/C13/C14 and optional-review branches; actual ordinary attachment and existing identity confirmation |
| Keep-3 | Reader mail-only/name-only/authorization/attachment restrictions | PASS | C05/C10 and live revocation/browser DOM |
| Keep-4 | Old draft persistence and truthful/idempotent send outcomes | PASS | C04/C13/C14; actual original manual preservation and SENT replay |
| NG | No permission-admin UI, multi-role workflow, approval levels, push/queue, ES index, review center, collaboration, automatic send, draft attachments, deployment | PASS | Complete changed-path/diff audit; only review-local stores/APIs/components; no queue/ES/global architecture changes or deployment |
| Required gates | Every prescribed command freshly exercised with enabled IT proof | PASS after disclosed external-fixture correction | Exact command table; no target skip substitution |
| Human-A | Master A-1/A-2; child01 A-1…A-4;02 A-1/A-2;03 A-1…A-3;04 A-1…A-4;05 A-1…A-5 | PENDING | No human-originated acceptance or sign-off; no acceptance artifacts fabricated |

### Complete interaction crosswalk

| Interaction | Verdict | Evidence |
|---|---|---|
| X1 grant/revoke→review admission | PASS | C09/C10; actual403 and full reader clearing |
| X2 reader CAS writes→admin reads→history | PASS | C09 and actual revision/return/comment history |
| X3 all real mail data→digest→approval/confirmation | FAIL V-1 | Full-data hash correct, but post-lock admission can use stale snapshot; real lock-race receipts |
| X4 new review store vs original admin store | PASS | V169/repository isolation; C04/C13; actual original body/attachment preserved |
| X5 lifecycle writes→count/page/badge | PASS | C11R/C12R and actual approval decrement |
| X6 old filters/account visibility→pending membership | PASS | Shared predicates, C11R/C12R |
| X7 reader DTO extension without admin-workload leak | PASS | Role branch and C10/C12R |
| X8 frozen approval→internal send ref→SMTP/record | PASS | C13/C14; reviewed actual MIME and bound mail90005 |
| X9 mail change→context→pre-claim confirmation | PASS at send boundary; V-1 at confirmation producer | Actual further IMAP change makes old event409; READ_COMMITTED claim intact |
| X10 SMTP outcome→attempt transaction→detail/history | PASS | C13/C14; RT SENT/claim/result event/duplicate no extraSMTP |
| X11 ordinary reply/draft without review ref | PASS | C06/C13/C14 and real unreviewed attachment send |
| X12 reader edit/save→admin approve→reader lock | PASS | C05/C09 and actual readonly review state |
| X13 grant/401/403→clear new UI state | PASS | C05/C10 and live grant revocation |
| X14 focus/select/logout→flush/old-response isolation | PASS | C05; native dirty caret/HTML unchanged; source abort/seq guards |
| X15 toolbar→filter→SQL→expert cards | PASS | C04/C11R/C12R; actual3/2/2+1 |
| X16 manual map→review→restore incl late callbacks | PASS | C04 delayed retarget/save/tool regressions and actual stable manual body/attachment restoration |
| X17 approval→count refresh→explicit send | PASS | Actual left removal/right retention, approval SMTP0, later separate reviewed delivery |
| X18 new mail/confirm/new mail→409/reopen | PASS UI/send; V-1 authoritative confirmation caveat | Actual IMAP events, native cancel/reconfirm and stale send409 |
| X19 user/expert/draft changes→translation/comments/detail isolation | PASS | C04/C05 captured identities/late-response tests; RT translation frozen body/revocation |

### Amendment and extra-file adjudication

Each item is judged against its recorded amendment and master rule, including files already authorized in an earlier child. Source: complete A1–A18 ledger rows and complete corresponding plan-only commits, retained in `amendment-commits.txt`; product/test patch separately inspected.

| File | Authority/master rule | Verdict | Observed bounded change |
|---|---|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | A1–A4; I-23/I-24/full fresh migration gate | PASS | Retires stale latest157 assertion without repinning169; aligns full migrated folder uniqueness with already-applied V167, retaining uid-validity/history checks; C10 passes |
| `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` | A1/A2 within original01 file; I-1/I-23 | PASS | Test-local worker test-config scan isolation, not global bean override; real review-role authentication cases retained; C10 passes |
| `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | A5/A6; I-8–I-10/I-23 | PASS | Only old dismissal fixture adds required dismissed_at/NOW; no production/schema/assertion weakening; C11R40 passes |
| `src/test/js/mailboxChatStyle.test.js` | A7/A8; I-5/S-1/I-23 | PASS | Retires obsolete global ban on new safe review HTML/link and fixed old copy/title; meaningful safety/permissions/CSS remain; C04/C05 pass |
| `src/test/js/mailReviewReader.test.js` | A9/A10 for05, distinct from04 authorization; I-24/S-1/S-5 | PASS | Removes old key/EOF/title pins, retains uniform resources and literal CSS/real DOM-id checks; native/permission regressions retained |
| `src/test/js/batchEntryRelocation.test.js` | A11/A12; I-23/shared-toolbar coexistence | PASS | Removes first-child coupling while preserving action relative ordering |
| `src/test/js/discoveryRecheck.test.js` | A11/A12; I-23/CSS coexistence | PASS | Retires CSS EOF coupling; original module literal CSS remains checked |
| `src/test/js/mailboxMailReadAccess.test.js` | A11/A12; I-16/I-24 | PASS | Removes old resource suffix pin, retains uniformity/read-access constraints |
| `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` | A13/A14; I-1/I-16 | PASS | Updates exact handler/verb matrix for approved reader endpoints; disallowed send/admin paths remain denied |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | A13/A14; I-15/full gate | PASS | Exactly four stale Mockito matcher arities corrected for optional null review ref; actual HTTP/dedup assertions retained; no send signature change |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | A15/A16 for05; inherited I-5 | PASS for amendment scope | Adds strict existing template-placeholder validation at current submission seam; malformed token no longer bypasses; shared placeholder service unchanged. V-1 concerns original approval/confirmation transaction behavior, not an unauthorized expansion in this amendment |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | A17/A18; I-23/I-24/mandatory full Maven | PASS | Removes only the three-line transient thread-state busy wait/assertion; retains interrupt/join, lease/token/budget/owner-completion/cache assertions; no Discovery production change; fresh C08 passes |

The broad continued-execution authorization was not treated as unlimited repair permission: each amendment's exact narrow file/change boundary was checked. There is no basis to reopen these closed contract decisions as new repair tasks.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child05 Epoch5 O-01: Discovery WAITING/RUNNABLE assertion failed mandatory full Maven | Master explicitly requires complete full Maven; outside one child was not a permanent aggregate waiver | RESOLVED under A17/A18, not silently ignored | Exact authorized test-only removal retains substantive checks; fresh C08 JVM5715/0fail/0error/24default skips |
| Child05 terminal Epoch6 RECORD_ONLY | No open item recorded; historical failures still retained | N/A, independently checked | Read full lineage; fresh own gates, rather than adopting LIGHT_PASS |
| Other child terminal RECORD_ONLY sections | Master mandatory requirements remain governing | No additional open aggregate item identified | Reviewed supplied child execution/verify/fix lineage; fresh complete combined commands/matrix |
| Child04 F-01 initial-detail recovery and F-02 UUID fallback, historical AUTO_FIX rather than RECORD_ONLY | I-17/I-18 and backend UUID admission | RESOLVED, not parent aggregate verdict | Current reader code and C05 behavioral regressions retain recovery identity/confirmation and canonical retry UUID |
| Child05 F-01 late manual retarget, historical AUTO_FIX | I-20 | RESOLVED | Current retarget captures/rechecks review epoch/manual owner before GET/dialog/apply; fresh C04 controlled empty/existing GET and pending-dialog regressions pass |
| Interrupted aggregate reviewer attempt | Fresh independent aggregate proof required | NOT A VERDICT | Interrupted logs not reused as PASS; own final gates/runtime supplied |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | Initial aggregate review; both production HTTP/MySQL lock-race counterexamples on the exact final code |
| Prior aggregate findings | N/A | No prior aggregate verdict supplied; interrupted attempts are not convergence epochs |

### Findings

#### P1

**V-1 — Approval and context confirmation can accept an unseen committed conversation update after a draft-lock wait.**

Requirement: Child01 I-6 and T3 require comparing the administrator's observed token with the current real conversation and returning409 when they differ. T3 explicitly prohibits silently recording an unseen new conversation as reviewed. The requirement applies to approve and confirm-context; I-12 later consumes the confirmation.

Implementation:
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt:110–118` and154–161 use default `@Transactional` on approve and confirmContext.
- `access` at234–241 first calls the ordinary `repository.find(id)`, then locks the draft with `find(id,true)`.
- `MailReviewDraftRepository.kt:28–30` makes the first find a consistent SELECT and the second SELECT FOR UPDATE.
- `matchingContext` at327–330 subsequently runs ordinary conversation reads through `MailboxConversationRepository.kt:33–69`.
- Fresh actual MySQL isolation was REPEATABLE-READ. The pre-lock consistent read establishes a snapshot that the locking read does not refresh for later consistent context queries.

Fresh discriminating production proof:
1. For approval, draft1 was SUBMITTED/version6 and the admin supplied token `adbca37463c0ee3310be91242f9a630d36c8bb82952884e16c586bb63e4566f6`.
2. An independent transaction held its draft-row lock. The actual HTTP approval blocked; actual `performance_schema.data_lock_waits` was1.
3. Another transaction committed a new inbound context row. Before releasing the draft lock, an independent authenticated context request observed new token `6302bb70c007eef053b0d12e7faddf4def54845e69a9159cf5acc581683455c2`.
4. Releasing the lock produced incorrect HTTP200 APPROVED/version7 with the old approved/current token. A later context request immediately reported the new token and changed=true.
5. Independently repeated for confirm-context: a held draft lock, actual wait1, committed existing-inbound body change and independently visible new token `86594661e5398b95e639f22e271980656cf35bb7745b9557610ece9c42656b5b` before unlock. Actual confirmation incorrectly returned200 and appended event13 containing the preceding6302… token.

Exact receipts: `/tmp/draft-review-aggregate-reviewer3/fresh-approval-lock-race.json` and `fresh-confirmation-lock-race.json`.

Impact: An admin action can record approval of a conversation they have not seen, or record a stale latest-context confirmation, despite the new data being committed before the authoritative check. This is a demonstrated mandatory review/audit contract violation, not merely a preferred isolation style.

Boundary: This is NOT a demonstrated SMTP bypass. `ManualReplySendAttemptService.prepareAndClaim:280–304` already uses READ_COMMITTED and the fresh send regressions/runtime stale-event409 remain effective. No global lock spanning mail writes/SMTP is required or proposed.

Smallest implicated repair scope: the existing `MailReviewDraftService.kt` transaction/read semantics for approve and confirmContext, plus real lock-contention regressions in existing `MailReviewDraftIT.kt`. No new class/state/interface/migration, UI, global datasource setting or mail-write hook is needed.

#### P2

N/A — no separately confirmed mandatory P2 item is being made a repair requirement.

#### Observations

- A saved clean reader editor's selection was repositioned by focus-triggered detail reload. The explicitly required dirty-editor case was separately exercised and preserved its exact HTML, selection node and offset15. The clean-focus observation is not promoted into a new mandatory task or used to reopen I-17.
- The owned diagnostic runtime application did not exit within30 seconds after SIGTERM and required SIGKILL. This cleanup observation is disclosed; no proof ties it to the draft-review feature and no unrelated shutdown repair is authorized.
- Initial external-DB connection errors are retained as failed attempts; corrected explicit isolated fixture runs supply the required proof. No product workaround was applied.

### Evidence Boundaries and Cleanup

- No required command/runtime proof remains unavailable. Normal-path browser/transport proof is fresh; not every race or failure branch was repeated in Chromium, and the corresponding current behavioral/integration suites and source consumption paths are identified above.
- Translation quality with a real remote provider, production data/schema, deployment, production performance and human acceptance were not exercised or claimed.
- Testcontainers/fixture SMTP delivery in prescribed suites remains distinct from the fresh actual GreenMail/production SMTP/IMAP runtime proof.
- Product/tests/approved plans/fast-p evidence/index/HEAD/branch were not modified. The only repository write was the eligible bounded repair draft below. No stage/commit/push/merge/deploy or repair execution occurred.
- Released both owned browser tabs and stopped the translation fixture; positively identified application PID62483 was terminated, with forced exit-9 disclosed. Owned `draft-reviewer3-runtime-mysql` and explicitly transferred interrupted-reviewer `draft-aggregate-review-mysql` were stopped, docker exit0. No unrelated database/container/daemon was restarted or deleted.
- Removed only owned throwaway Java harness/class, downloaded GreenMail fixture jar, upload source and disposable storage. Logs, JSON receipts, screenshots and actual MIME remain under the evidence scratch directory. Cleanup receipt: `cleanup.json`.

### Next Action

FAIL + INITIAL permits bounded repair planning only. `repair-p` produced the following draft; it has not authorized or executed implementation. Human acceptance remains PENDING and cannot be substituted by these machine screenshots.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-10-29/draft-review-master.md`, exact governing identity above.
Verification result: FAIL / INITIAL.
Repair artifact: `docs/plans/fix/draft-review-master/repair.md`.

### Included Findings

- V-1: one transaction/read-freshness root cause covering approval and confirmation.
- Authorized product/test files only: `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` and `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt`.
- Deterministic real concurrent-transaction regression must demonstrate committed context before lock release, reject old-token approval/confirmation with409 and no business/event changes, then accept a freshly observed token with SMTP0.
- Preserve all send-time READ_COMMITTED/idempotency/safety and ordinary reply behavior; rerun the exact complete required command set with enabled targets, no test suppression.

### Excluded Findings

- Historical resolved reader/retarget fixes and explicitly adjudicated Discovery assertion decision.
- Initial external-fixture connection failures, clean-editor focus observation and unrelated shutdown behavior.
- Human acceptance or scope beyond the original mandatory context contract.

### Required Human Decision

Approve the current bounded repair artifact before execution. The draft includes the fully resolved one-approval Review-Fast-P Execution Handoff:
- Product commit subject: `fix(review-fast-p): repair draft review master`.
- Append-only handoff: `docs/plans/review/draft-review-master/repair-execution.md`.
- Evidence-only commit subject: `docs(review-fast-p): record repair execution`.
- Return to already authorized aggregate re-review only when the explicit human execute-p invocation requests it; no automatic execution or continuation.

No implementation was performed.

To approve and execute this repair, send:
$execute-p docs/plans/fix/draft-review-master/repair.md

No product code was modified.


## Epoch 2 — 2026-10-30T21:17:52+08:00

- Master plan: docs/plans/2026-10-29/draft-review-master.md; governing commit32407a46d5b05971fc913cda1802b6ba002e8dc7; sha256:a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b
- Master identity state: AMENDMENT_RECORDED; exact A1–A18 rule/reason/approval and per-file adjudications retained in this complete report; original invoked sha256:63472802e91831bff5295d9e3f63a9af81d60d69471c6cd8bb2ec76570e4f3ff, exact governing divergence artifact1541.
- Boundary: b15bb069f43f89c45ee6859dba04a0baff5d774d..60142d42148b9ab94a358249ca784bb8da1f48cd
- Reviewer: DraftReviewAggregateEpoch2
- Result: PASS
- Convergence: PROGRESSING; V-1 RESOLVED
- Repair artifact/result: N/A — no new repair planning; approved repair sha256:52b488ad5e1fa4dc3eba3b1ae60e71dab62bec9bf493c8daed48b88047a02b9c unchanged
- Repair evidence mode: DURABLE_HANDOFF; repair-execution.md sha256:47fee0cd9f9e965ea8632e503bc787841c1fb9bb6178ac82da6ffc19180cbbe4
- Evidence parent before next commit: edab0286a671bad508697ffbfa90519d80b71f01
- Human acceptance: PENDING
- Complete returned expanded report source: /tmp/draft-review-aggregate-epoch2/aggregate-epoch2-report.md,51710bytes,sha256:65af9d1416f06158cf47b463b35e93483960cf35b48eb6b7bfad12d1ced5824b; preserved verbatim below.

## Verification Result: PASS

Plan: `docs/plans/2026-10-29/draft-review-master.md`, requested phase `aggregate/master`, under `review-p` → `verify-p`, applying the repository's `fix-v` constrained-verification discipline in read-only verification mode.

Implementation boundary: `b15bb069f43f89c45ee6859dba04a0baff5d774d..60142d42148b9ab94a358249ca784bb8da1f48cd`.

Convergence: **PROGRESSING**. Stable P1 **V-1 is RESOLVED**; its two original production lock-wait counterexamples now reject stale context. No new mandatory violation was established. The complete cumulative matrix and all prescribed commands were exercised freshly, not inherited from the executor or child LIGHT_PASS reports.

Manual acceptance: **PENDING**. Machine review readiness: **READY_FOR_HUMAN_REVIEW**. This is not deployment, merge, release, or human acceptance authorization.

Reviewer: `DraftReviewAggregateEpoch2`, a new independent reviewer acquired after the committed repair, distinct from the prior aggregate reviewer and recorded fast-p writers/verifiers. No executor conversation was inherited.

### Authority, identity and repair evidence mode

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`; branch `fast/draft-review-master`. Worktree resolution: `DISCOVERED_FROM_GIT_WORKTREES`; this review uses the exact retained-worktree contract supplied by `local://draft-review-aggregate-epoch2-input.md`.
- Governing master identity: commit `32407a46d5b05971fc913cda1802b6ba002e8dc7`; actual SHA256 `a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b`.
- Invoked original-copy identity remains distinct: SHA256 `63472802e91831bff5295d9e3f63a9af81d60d69471c6cd8bb2ec76570e4f3ff`. The complete divergence in `artifact://1541:raw` was read; the recorded worktree master/A1–A18 govern, not the original copy's future permission/UI wording. The corrected historical absolute discovery selected this worktree; the earlier wrong relative-path NONE is not a second verdict.
- All five ordered child plans, their complete brief/execution/verify/fix evidence, fast-p ledger and handoff, current review ledger, prior Epoch1 machine report, exact repair plan, and durable execution handoff were read. The approved amendments override the historical DRAFT headings and closed incidental-test decisions. No amendment was invented or reopened.
- Ordered child identities: 01 `87a4ba94b070afebeb83d1c6b9fe885ed66a2aaa`; 02 `2af9b9ca1025c5741086558365f92793357a307e`; 03 SHA256 `f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7`; 04 `c17c9013ece5bf7967d6233cd97f5bfed47d1cab`; 05 `32407a46d5b05971fc913cda1802b6ba002e8dc7`, SHA256 `b9669ef5dac2ccb10b8f4a64cdd6c9d3b09d7881f489c5b3eee8c303fbcbf5f8`.
- Complete aggregate code head: `60142d42148b9ab94a358249ca784bb8da1f48cd`; evidence HEAD: `edab0286a671bad508697ffbfa90519d80b71f01`. The original fast-p code head `bec9e40faae946b0971e95af9c3a9edec83cd888` and original final validator identity `d309757a23bfffd5c0086ce1682aa638dd7f851e` remain immutable historical lineage, not fresh aggregate evidence.
- Repair evidence mode: **DURABLE_HANDOFF**. Exact artifact: `docs/plans/fix/draft-review-master/repair.md`, actual SHA256 `52b488ad5e1fa4dc3eba3b1ae60e71dab62bec9bf493c8daed48b88047a02b9c`. Handoff: `docs/plans/review/draft-review-master/repair-execution.md`, supplied SHA256 `47fee0cd9f9e965ea8632e503bc787841c1fb9bb6178ac82da6ffc19180cbbe4`. Pre-execution evidence HEAD `06e65de2896a39e6d31c0ef6ebd068d1f647eea4`; repair execution ID is the canonical artifact path plus `@52b488ad5e1fa4dc3eba3b1ae60e71dab62bec9bf493c8daed48b88047a02b9c`.
- Exact human approval is reproduced in repair-execution.md:25–32:

```text
$execute-p docs/plans/fix/draft-review-master/repair.md
授权执行计划内修复、指定本地提交及 repair-execution.md 交接记录。
完成后在同一任务自动继续：
$review-fast-p docs/plans/fast/draft-review-master/human-review-handoff.md
```

- Executor identity is exposed only as the prior parent-session execute-p controller; its agent/task identity is **UNAVAILABLE**, not fabricated. Executor `READY_FOR_VERIFICATION` and its test/runtime assertions were treated as historical claims, not this review's result.
- Independent repair-scope receipt: `git diff --numstat bec9e40faae946b0971e95af9c3a9edec83cd888 60142d42148b9ab94a358249ca784bb8da1f48cd -- src pom.xml` returned exactly:

```text
4  2  src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt
80 0  src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt
```

Both are the repair's exact Authorized Files. The production delta is the two READ_COMMITTED transaction annotations plus their import/comment; the test delta adds the two real lock-wait regressions and helper. No caller/API, new state/store, migration, frontend, shared rule, or unrelated repair was added. Receipt: `/tmp/draft-review-aggregate-epoch2/repair-scope-receipt.txt`.

### Fresh command environment and receipts

Audit/design of the entire matrix preceded every build/test/lint/syntax command. Fourteen prescribed commands then ran once, in order; all Maven invocations were sequential with normal compilation. No compiler/test suppression, weakened assertion, formatter, retry-to-hide-failure, or production code edit was used.

Common environment:

```text
JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home
MAVEN_OPTS=-Xmx3072m
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
DB_URL=jdbc:mysql://127.0.0.1:33426/draft_epoch2?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=<reviewer-owned local fixture credential>
```

Genuine Java output: OpenJDK `11.0.33`, Zulu `11.92+109-CA`, build `11.0.33+9-LTS`. Official retained archive SHA256 independently calculated as `4947b01fcb220ee69710dbf63375f4628688d84750fccd289f5a6d62b494aa94`. No timezone stub or product workaround was introduced.

Owned external MySQL container: `draft-review-aggregate-epoch2-mysql` (`mysql:8.0.36`, loopback33426), created specifically for this review; not another actor's schema. Testcontainers targets retained their normal own MySQL. Explicit JDBC Unicode/charset/SSL/public-key/timezone parameters were supplied. Runtime used a separate fresh `draft_runtime_e2` schema, never an executor's old schema.

All unabridged command output is preserved under `/tmp/draft-review-aggregate-epoch2/C01.log` … `C14.log`; `commands.json` contains exact commands, exit codes, elapsed seconds and per-suite XML counts; `command-input.json` and `commands-driver.py` retain the invocation procedure. All full outputs were consumed for results; `command-summaries.json` is an index, not a replacement for the logs.

| ID | Exact command | Result and fresh counts | Log |
|---|---|---|---|
| C01 | `node --check src/main/resources/static/mailbox-chat.js` | PASS, exit 0; syntax check; no test count; 0.24s | `C01.log` |
| C02 | `node --check src/main/resources/static/app.js` | PASS, exit 0; syntax check; no test count; 0.14s | `C02.log` |
| C03 | `node --check src/main/resources/static/mailbox-readonly.js` | PASS, exit 0; syntax check; no test count; 0.13s | `C03.log` |
| C04 | `node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js` | PASS, exit 0; 334 tests, 334 pass, 0 fail, 0 skip; 2.47s | `C04.log` |
| C05 | `node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js` | PASS, exit 0; 131 tests, 131 pass, 0 fail, 0 skip; 1.18s | `C05.log` |
| C06 | `node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js` | PASS, exit 0; 65 tests, 65 pass, 0 fail, 0 skip; 1.04s | `C06.log` |
| C07 | `node --test src/test/js/*.test.js` | PASS, exit 0; 1956 tests, 1956 pass, 0 fail, 0 skip; 4.43s | `C07.log` |
| C08 | `mvn test '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 5715 tests, 0 failures, 0 errors, 24 skipped; 674.49s | `C08.log` |
| C09 | `mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 14 tests, 0 failures, 0 errors, 0 skipped; 281.97s | `C09.log` |
| C10 | `mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 55 tests, 0 failures, 0 errors, 0 skipped; 978.82s | `C10.log` |
| C11 | `mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 45 tests, 0 failures, 0 errors, 0 skipped; 282.86s | `C11.log` |
| C12 | `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 46 tests, 0 failures, 0 errors, 0 skipped; 233.93s | `C12.log` |
| C13 | `mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 45 tests, 0 failures, 0 errors, 0 skipped; 287.13s | `C13.log` |
| C14 | `mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest '-DargLine=-Xmx1536m -Dapi.version=1.41'` | PASS, exit 0; 160 tests, 0 failures, 0 errors, 0 skipped; 190.83s | `C14.log` |

Full-Maven C08: 5,715 JVM tests, 5,691 non-skipped, 0 failures/errors, **24 default opt-in skips disclosed**, plus the Maven-bound frontend executions. These default skips are not treated as enabled-IT proof. Fresh explicitly enabled target results:

| Command | Target class | Tests / failures / errors / skips |
|---|---|---|
| C09 | MailReviewDraftIT | 14 / 0 / 0 / 0 |
| C10 | AuthFlowIntegrationTest | 9 / 0 / 0 / 0 |
| C10 | FlywayMigrationIntegrationTest | 46 / 0 / 0 / 0 |
| C11 | MailReviewQueryIT | 5 / 0 / 0 / 0 |
| C11 | MailboxConversationRepositoryIT | 40 / 0 / 0 / 0 |
| C12 | MailboxConversationControllerTest | 46 / 0 / 0 / 0 |
| C13 | MailReviewSendIT | 18 / 0 / 0 / 0 |
| C13 | MailReplyDraftSendIntegrationTest | 27 / 0 / 0 / 0 |
| C14 | ManualReplySendAttemptServiceTest | 58 / 0 / 0 / 0 |
| C14 | PendingMailOperationServiceTest | 42 / 0 / 0 / 0 |
| C14 | PendingMailOperationServiceTrustWorkbenchTest | 60 / 0 / 0 / 0 |

Thus the explicitly named JVM targets contributed **365 non-skipped tests**, including the repair's two added lock-wait cases. C11 also printed `REVIEW_QUERY_SIZE100 rows=100 sqlCount=10 reviewSqlCount=2` and its real MySQL EXPLAIN, including `idx_review_draft_pending`. This is a fresh measured receipt, not a new statement-count acceptance constraint. The required build evidence is successful normal Maven compile/test compilation; a WAR package/deployment was not requested or claimed.

### Contract Matrix — whole master and every child invariant

Evidence abbreviations: `Service` = `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`; `Repo` = `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt`; `ConversationRepo` = its `MailboxConversationRepository.kt`; `Attempt` = `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`; `Pending` = same directory `PendingMailOperationService.kt`; frontend names below are under `src/main/resources/static/`. `RT` refers to this review's fresh `http-receipts.json`, `runtime-final.json`, `lock-races.json`, actual MIME files and browser/network receipts, not earlier execution evidence. All listed master invariants, implementation tasks, styles, keeping rules and machine criteria are mandatory; human A-items remain PENDING.

| ID | Governing requirement | Verdict | Fresh evidence and inspected runtime path |
|---|---|---|---|
| R1 | Authorized user1 name-only mailbox; multiple rich drafts, save/submit/withdraw/return/resubmit/delete; comments and complete successful-save/state history; approved lock | PASS | Service:29–164/236–272; C05/C09/C10; RT private admin404, reader/send403, real full return/revise/resubmit, canonical save/conflict, approved mutation409, soft-delete retained events; native reader rich editor/history and live revocation |
| R2 | Admin toolbar counts drafts; list remains experts; shared original composer; original admin body and attachment preserved | PASS | ConversationRepo:214ff/808ff; mailbox-chat.js:5728–6050/6191ff; C04/C11/C12; RT 3 drafts/2 experts/2+1 page counts; real pending toolbar and manual→review→manual body/attachment round trip with one editor |
| R3 | Approval and context confirmation do not send; explicit reviewed send; latest context must be observed; inline translation/comments/history | PASS | Service:112–163; Attempt:280ff; C09/C13/C14; both fresh real lock-wait stale admissions409, fresh200 with SMTP0; actual new-IMAP-message confirmation invalidation, native modal and later explicit SMTP delivery |
| I-1 | Session-only exact admin/user1 identity; current grants and ownership at every reader operation; private never-submitted drafts hidden from admin; narrow admin-only actions | PASS | Controller:19–110/130–145; Service:236–257; MailReadOnlyPolicy precise handlers; C09/C10/C12; RT anonymous401, private detail/events404, forbidden sender accounts/send403, revoked GET403 and full UI clearing |
| I-2 | Exactly four review states, valid transition/action matrix, immutable approved body, user1 cannot mutate/comment/delete after approval | PASS | Service:69–164; C09 Cartesian/state/concurrency tests; actual withdraw/return/revise/resubmit/approve, post-approval reader comment/delete409, admin comment allowed; SMTP unchanged by approve |
| I-3 | Exact integral version, CAS and complete canonical snapshot/event transaction; no-op save no event; comment does not increment version; failed write no partial history | PASS | Controller:137–154; Service:259–272; Repo CAS/event insert; C09 real dual-write races and event-trigger rollback; RT same-version no-op then stale409, full history through approved and send events |
| I-4 | Only two new independent review tables; no old shared columns or review use of original draft store; soft-delete retains body/history; no cascade erasure | PASS | V169__create_mailbox_review_draft.sql; complete migration diff receipt contains only V169; C09 fresh/V168 migration preservation; actual DELETE leaves body/events; original administrator server draft/attachment retained |
| I-5 | Server Safelist canonical HTML/text, strict fields, all placeholders/malformed tokens rejected on submit, UTF-8 and final length bounds, safe display | PASS | Service canonical normalizer and strict existing placeholder validator; controller local integral/field parsing; C09/C10; RT script/onerror/javascript stripped, malformed `${broken`422 with no event; native safe rich paste and stable canonical browser save |
| I-6 | Complete real-mail streaming digest, filters do not narrow it, approval/confirmation compare current observed context after lock wait | PASS — V-1 resolved | ConversationRepo:33–69 streaming length-prefixed SHA256; Service:112/156/327–335; C09 includes full-history/same-time/old-edit/failure/account cases; both fresh HTTP/MySQL lock waits observed in performance_schema and stale tokens rejected with unchanged rows/events/attempts |
| I-7 | Immutable identity and server-resolved fixed target/account; atomic review fields; original target not replaced; stable attempt binding | PASS | Service:29ff/176–233; Repo target lookup/validTarget/sendProjection; C09/C13; RT OUTBOUND anchor remains fixed after actual new inbound; frozen version and bound SENT attempt preserved |
| I-8 | Pending total counts drafts, page total counts experts, per-card count; shared EXISTS before pagination | PASS | ConversationRepo:214ff/808ff; C11/C12 count/page/EXPLAIN; actual 3/2 with size1 cards2+1, no client-page filtering substitute |
| I-9 | Admin-only pending filter; account/search/common filters retained; mutually exclusive chip semantics; disabled real accounts allowed; reader no review-summary work/leak | PASS | MailboxConversationService role branch and pending filter projection; shared SQL membership; C10–C12 with disabled/simulator/filter fixtures; RT reader restricted network, admin toolbar and ordinary chips |
| I-10 | State writes immediately affect next query; revoked grants do not erase admin submitted queue; missing contact/account excluded; no cached truth | PASS | Repo writes→ConversationRepo membership direct reads; C11/C12; actual approve count3→2, browser approve further decrement, withdrawn/deleted history retained; live reader revocation403 without deleting admin review records |
| I-11 | Only admin can explicitly send the current approved frozen DB version; strict request excludes body/recipient/account/attachments; approve/confirm SMTP0 | PASS | MailReviewSendService and Controller:99–110; Pending:658ff final byte comparison; C13/C14; actual forbidden reader send, approved frozen plain+HTML MIME with no attachment, approval/confirmation zero delivery delta |
| I-12 | Same-transaction current context check before claim; exact draft/version/admin/token confirmation; reject stale/forged events; confirmation requires another manual click | PASS | Service:215–233; Attempt:280–304 READ_COMMITTED; C09/C13; actual old-event send409/no SMTP after existing safety gate confirmation; native confirm only then new inbound reopens modal; subsequent explicit send |
| I-13 | Legacy fingerprint unchanged; reviewed domain/id/version segment; review-before-attempt lock/bind; SENT/UNKNOWN/IN_PROGRESS retain fixed idempotency and no blind retry | PASS | Attempt:194–198/254ff/280ff; Pending:437/824; C13/C14 actual concurrent/dedup/unknown/failure seams; RT SENT replay produces no extra SMTP and retains same attempt; no new frontend request ID |
| I-14 | Real attempt/mail_record/send events atomic; approved state remains APPROVED; truthful SENT/UNKNOWN projection; no duplicate events from replay | PASS | Attempt:448ff/546ff; Service:195–233/273ff; C13/C14 transaction/error/DEDUP scenarios; RT SEND_CLAIMED/SEND_SUCCEEDED history, bound SENT projection and three real final SENT attempts; replay produces no new delivery |
| I-15 | Null review-ref ordinary manual/AI adoption/attachments/meetings/QA/RAG/security routes retain existing semantics, no residual AI-approval gate | PASS | Pending nullable seams and final canonical comparison guarded by ref; legacy fingerprint branch; C06/C13/C14 and C08 full suite; RT ordinary unreviewed actual multipart attachment delivery with exact bytes, without review reference |
| I-16 | Separate MailboxReadOnly lifecycle and precise pre-fetch allowlist; no admin tools/sender accounts/attachments/review send/translate; name-only DOM | PASS | app.js reader allowlist/lifecycle and mailbox-readonly.js:190ff; C05/C10; real reader1440/390 has only mailbox tab/mobile option, name-only header, no admin composer; saved network has no forbidden endpoint requests |
| I-17 | One active draft identity, explicit canonical save; submit waits for latest save; failed save prevents navigation/logout; preserve late edits/caret | PASS | mailbox-readonly.js save/navigation/actions:461–539; C05 behavioral regressions; real native bold/list/save, focus keeps same editor/caret; offline expert switch stays on Expert1 and retains dirty text with explicit failure, online save recovers |
| I-18 | Session/generation/contact/draft/version/sequence isolation; 401/403 clears all live content/cache; unmount abort/listener cleanup, no localStorage review persistence | PASS | mailbox-readonly.js captured contexts/request/teardown; C05/C10; actual grant removal + focus clears timeline/editor/comments/history and GET403; complete source guards and late-response tests, no new Storage path |
| I-19 | Unique pending toolbar sets server-side filter/page/chip; preserves other conditions, list remains expert cards | PASS | app.js toolbar bridge; mailbox-chat.js summary/filter and list; C04/C11/C12; actual button opens two expert cards without a draft-list modal, per-expert pending badges and count decrement, mobile toolbar remains real |
| I-20 | One original editor; review state separate from admin map; flush first, guard autosave/send/tools/retarget/late callbacks; restore original body+attachment | PASS | mailbox-chat.js:5728ff and guarded manual/tool/retarget entrypoints; C04 late retarget GET/confirmation/save/tool tests; actual single editor with submitted body and zero visible send buttons, then exact admin body+attachment restoration |
| I-21 | Unapproved review has no send control; APPROVED dedicated route only, no automatic send; SENT removes control, UNKNOWN cannot resend; manual unchanged | PASS | mailbox-chat.js:5812–5844/6050ff; C04/C13; native SUBMITTED no send; browser approve SMTP0, explicit mobile send uses dedicated route422 safety→200, final SENT removes send control; ordinary manual separate |
| I-22 | Native context modal cancel/Escape clears; confirm only, stale event reopens; captured translation/comments/history do not replace source | PASS | mailbox-chat.js:5906ff/5994ff and async capture guards; C04/C05; real white dialog1440/390, cancel/Escape, confirm zero SMTP then new real inbound reopens; translation 11px text action and source HTML unchanged; comment/history actual API |
| I-23 | Ordered01→02→03→04→05; exact amended scope and no parallel shared-file writers; two new stores/no old columns | PASS | Complete fast-p checkpoint/code/evidence lineage, full cumulative diff and per-child `scope.json`:11/6/8/8/14 files, all in respective amended tables; union37 paths; exact two-file repair is separately approved. No unrelated scope expansion |
| I-24 | Distinguish plan/prototype/evidence/runtime; approved amendments before changed scope, cache/migration source identity; never infer machine PASS/human acceptance | PASS | Exact identities/amendment commits and original-copy divergence audited; fresh command/runtime receipts below replace child claims as aggregate evidence; all versioned resources and literal style/DOM checks pass; approval and human acceptance remain distinct |
| S-1 | Exact shared reader compose/history DOM and CSS, safe rich HTML only in new region, original timeline plain | PASS | C05/C07 literal CSS/real-ID and action tests; native reader1440/390 bold/list/paste, no second composer, timeline remains existing render |
| S-2 | Restricted shell copy/tabs/inline error feedback, no new admin tools/toast | PASS | C05/C07, actual restricted wide/mobile shell and offline/revocation feedback |
| S-3 | One toolbar button before preserved actions; expert pending badge, not review-center modal | PASS | index.html:800ff, C04/C07 including amended existing button-order contract; real desktop/mobile toolbar/card observations |
| S-4 | Same manual editor, mode selector, inline comments/history, historical small translation styling | PASS | mailbox-chat.js:6191–6224, C04/C05 exact DOM/CSS; actual body/attachment restoration; translation button measured11px and no source mutation |
| S-5 | Exact opaque white native dialog, viewport bounds, focus and Escape | PASS | C04/C07 literal CSS/DOM; native1440 dialog480px at x480, native390 dialog358px at x16/right374, background rgb(255,255,255), focus inside, Tab/Escape and no document horizontal overflow |
| 01 T1–T4 | Two-table schema, local API/security/canonicalization, complete digest, real transactions/auth/migrations | PASS | Full source/migration scope; C09/C10 fresh enabled14+55; RT genuine controller/MySQL workflow and both repaired admission races |
| 02 T1–T3 | Optional query/DTO compatibility, one membership predicate and bounded batch projections, real count/page/EXPLAIN/query measurement | PASS | C11/C12 fresh45+46; measured size100/10SQL/2reviewSQL; actual count/page API; no statement-count pin invented |
| 03 T1–T4 | Dedicated frozen adapter, final-content seam, transactional attempt linkage/result/fingerprint and old send regression | PASS | C13/C14 fresh45+160; real reviewed and ordinary attachment MIME; exact nullable seam and retained old request semantics |
| 04 T1–T3 | Precise request/lifecycle integration, persistent native reader/history, uniform assets and real-browser behavior | PASS | C03/C05/C07 and RT native rich editing/paste/caret/offline/revocation at both widths |
| 05 T1–T5 | Toolbar/expert list/shared editor/actions/translation/context modal/explicit send/assets/regressions | PASS | C01/C02/C04/C07 plus real pending selection, original draft/attachment preservation, approval/confirmation SMTP0, mobile actual reviewed delivery |
| Keep-1 | Existing manual/AI adoption/attachment/meeting/unsubscribe/security behavior | PASS | C06/C08/C13/C14; ordinary unreviewed actual attachment delivery and real existing safety422→confirmation→dedicated reviewed success |
| Keep-2 | Original timeline/filter/pagination and user1 visibility restrictions | PASS | C04/C05/C10–C12; actual filter2 experts, name-only reader and no admin reads; existing timeline messages preserved |
| Keep-3 | Original administrator draft persistence and truthful result/retry status | PASS | C04/C13/C14; actual admin saved server draft+attachment round trip, reviewed SENT replay and no duplicate mail |
| NG | No role-management UI/general workflow/multilevel approval/push/queue/new ES index/review center/collaboration/automatic send/review attachments/deployment | PASS | Complete cumulative product diff is confined to amended feature scope; only review-local stores/APIs/components plus authorized compatibility tests; no push/merge/deploy performed |
| Required gates | Every prescribed command fresh with explicitly enabled targets nonzero/zero skip | PASS | C01–C14 all exit0; target table above; default24 skips disclosed, not substituted |
| Human-A | Master A-1/A-2 and child01 A-1…A-4;02 A-1/A-2;03 A-1…A-3;04 A-1…A-4;05 A-1…A-5 | PENDING | Machine runtime coverage does not replace human-originated acceptance; no human acceptance artifact or sign-off created |

### Complete ordered-child interaction crosswalk

| Interaction | Verdict | Evidence |
|---|---|---|
| X1 grant/revoke→review admission | PASS | C09/C10; actual revoked reader403 and full body clearing |
| X2 reader CAS writes→admin reads→history | PASS | C09; actual no-op/stale409, return/revise/resubmit/comments/history |
| X3 all real mail data→digest→approval/confirmation | PASS; V-1 RESOLVED | Complete streaming digest; C09; both fresh lock-contended HTTP admissions reject a token superseded before unlock |
| X4 new review store vs original admin store | PASS | V169 and Repo separation; C04/C13; actual original admin body/attachment restored |
| X5 lifecycle writes→count/page/badge | PASS | C11/C12 and actual3→2→1 count changes and soft delete |
| X6 old filters/account visibility→pending membership | PASS | Shared predicates; C11/C12 disabled/simulator/account/chip/search fixtures |
| X7 reader DTO extension without admin-workload leak | PASS | Service role branch, C10/C12, real reader network without admin endpoints |
| X8 frozen approval→internal send ref→SMTP/record | PASS | C13/C14 and actual canonical multipart/alternative reviewed MIME without attachments |
| X9 mail change→context→pre-claim confirmation | PASS | READ_COMMITTED prepare/claim; stale-event409 and zeroSMTP after real new inbound; repaired confirmation producer independently fresh |
| X10 SMTP outcome→attempt transaction→detail/history | PASS | C13/C14; actual bound SENT/claim/result events and replay without extraSMTP |
| X11 ordinary reply/draft without review ref | PASS | C06/C13/C14 and real ordinary unreviewed multipart attachment send |
| X12 reader edit/save→admin approve→reader lock | PASS | C05/C09/C10 and RT native canonical save, approved reader write409 |
| X13 grant/401/403→clear new UI state | PASS | C05/C10, actual live revoke/focus clears editor and timeline |
| X14 focus/select/logout→flush/old-response isolation | PASS | C05; real native persistent caret and offline failed navigation with dirty preservation; captured sequence/abort teardown source |
| X15 toolbar→filter→SQL→expert cards | PASS | C04/C11/C12, actual3 drafts/2 experts/2+1 and native toolbar filtering |
| X16 manual map→review→restore incl late callbacks | PASS | C04 late-retarget GET/confirmation/save/tool tests; real exact body+attachment restoration |
| X17 approval→count refresh→explicit send | PASS | Native approval decrements badge while retaining right-side draft; approvalSMTP0, later distinct dedicated send |
| X18 new mail/confirm/new mail→409/reopen | PASS | Actual IMAP ingestion, native cancel/Escape/confirm-only/reconfirmation and stale-event409; V-1 producer defect resolved |
| X19 user/expert/draft changes→translation/comments/detail isolation | PASS | C04/C05 captured identity and late-response tests; actual inline translation leaves canonical body unchanged and live revoked reader clears all content |

### A1–A18 independent amendment adjudication

Every amendment was checked against its exact Before/After plan-only commit, master rule, reason and recorded HUMAN authority in fast-p ledger:31–48. Complete plan-only patches are retained in `/tmp/draft-review-aggregate-epoch2/amendment-patches.txt`. These are not permissions inferred from test failure. The closed decisions are preserved; scope and substance were re-audited against current code.

| Amendment | Recorded contract change and authority | Aggregate judgment |
|---|---|---|
| A1 | Master32176acd→c21b3aa; I-23/implementation preflight; human bounded 授权 adds migration-test exception | PASS. Only the named migration compatibility test added to01 scope, not applied migrations |
| A2 | Child01 same boundary; I-23/I-24/auth+migration gate; obsolete version assertions and local auth isolation | PASS. No global bean override or skip; fresh C10 real auth+migrations all non-skipped |
| A3 | Masterc21b3aa→87a4ba94; I-23/I-24; human continuous in-goal bounded continuation and later review-fast-p | PASS. Recorded before release of remaining folder-schema repair; no unlimited product authority inferred |
| A4 | Child01 same plan boundary; V120 assertions aligned to appliedV167 folder UID uniqueness, preserving data/UID checks | PASS. Test matches current applied schema without editing old migrations or suppressing uniqueness failures; C10 |
| A5 | Master87a4ba94→2af9b9ca; I-23/I-24 adds proved query-IT fixture path | PASS. Added test fixture only, not production schema or SQL behavior |
| A6 | Child02f3417e2→2af9b9ca; missing dismissed_at fixture only; measured query count without pin | PASS. Exactly INSERT timestamp compatibility; C11 substantive tests and fresh measurement retained |
| A7 | Master2af9b9ca→c17c9013; I-23/I-24 eighth reader test file for obsolete source/copy conflict | PASS. Bounded retirement only; no weakening of actual reader security/rich-text/shell contract |
| A8 | Child04f3417e2→c17c9013; I-16/S-1/S-2/T3 removes old all-HTML/link prohibition and wording/title pins | PASS. Current canonical/sanitized review HTML and real native UI are tested; no repinned obsolete wording |
| A9 | Masterc17c9013→0c6111bb; I-23/I-24 reader test added to05 cross-step asset/CSS scope | PASS. File's prior04 authorization was not treated as automatic05 authorization |
| A10 | Child05f3417e2→0c6111bb; old key/EOF/title assertions retired, uniform resources/literalS1/realDOM retained | PASS. Only contradictory implementation-detail pins removed; C04/C05/C07 maintain style/DOM/resource checks |
| A11 | Master0c6111bb→babdb2df; I-23/I-24 three additional failed old frontend contracts | PASS. Exactly the three named test paths, not broader frontend product changes |
| A12 | Child05 same boundary; first-child/EOF/old-cache suffix coupling retired while order/CSS/resources/permissions kept | PASS. Changes obey recorded narrow purpose; fresh fullnode1,956 passes |
| A13 | Masterbabdb2df→08aa4337; I-23/I-24 two backend test-compatibility files,12-file exception | PASS. Product backend behavior not expanded; genuine Java11 patch resolves tzdb without source workaround |
| A14 | Child05 same boundary; reader exact route matrix and four Mockito matcher arities only | PASS. Allowlist matches approved01 handlers; negative HTTP/dedup assertions retained; C08/C10/C14 |
| A15 | Master08aa4337→59eb60cf; inheritedI-5 proven malformed placeholder admission,13-file/3-subsystem exception | PASS. Existing strict-validator reuse only in existing review canonicalization; no shared placeholder behavior change |
| A16 | Child05 same boundary; existing service validator closes broken-token gap, retaining complete-token/HTML/length guards | PASS. Actual malformed `${broken` submission422 with unchanged row/history plus C09; already-authorized01 service still separately authorized for05 |
| A17 | Master59eb60cf→32407a46; I-23/I-24 mandatory fullmvn,14-file exception for incidental JVM thread-state assertion | PASS. Exact test-only retirement kept substantive cancellation/lease/cache/owner checks; fresh C08 passes; closed decision not reopened as product P1 |
| A18 | Child05 same boundary; I-20 retarget correction remains original scope; O-01 only lines63–65 busywait/state retirement | PASS. No discovery production code, no test skip, no gate waiver; C04 delayed-retarget behavior and C08 full suite freshly pass |

### Every amended/extra file individually checked

| File | Amendment/master-rule scope | Judgment and current evidence |
|---|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | A1–A4,01 I-23/I-24/T4 | PASS. Retires stale latest=157 pins without replacing with169; V120 completed-chain index checks align appliedV167 folder uniqueness and retain UID/data preservation; C10 46/0/0/0 |
| `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` | A1/A2 compatibility in an existing01-authorized file | PASS. Test-local conflicting worker isolation, not global overriding, and exact authenticated review-handler regression; C10 9/0/0/0 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | A5/A6,02 I-23/I-24 | PASS. Only missing mandatory dismissed_at/NOW() fixture supply, unchanged behavior assertions; C11 40/0/0/0 |
| `src/test/js/mailboxChatStyle.test.js` | A7/A8,04 I-16/S-1/S-2/T3 | PASS. Removes obsolete global review-HTML ban/fixed copy/title constraints; retains actual shell/style/security assertions. C04/C05/C07 and native reader rich paste |
| `src/test/js/mailReviewReader.test.js` | A9/A10,05 I-23/I-24/T5, although already04-authorized | PASS. Retires old key/EOF/title pins only, retains shared literalCSS/uniform assets/realDOM; C04/C05/C07 |
| `src/test/js/batchEntryRelocation.test.js` | A11/A12,05 T5/S-3 | PASS. Preserves original button container/relative action order while permitting front pending button; C07 |
| `src/test/js/discoveryRecheck.test.js` | A11/A12,05 T5 | PASS. Limits literal CSS comparison to its own dr-recheck block instead of unrelated EOF additions; actual block/DOM remain asserted; C07 |
| `src/test/js/mailboxMailReadAccess.test.js` | A11/A12,05 T5 | PASS. Retires old cache suffix only; uniform key/script adjacency/permission checks preserved; C07 |
| `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` | A13/A14,05 fullmvn exact reader matrix | PASS. Precise approved handler/verb additions only; deny matrix retained, no production permission expansion; C08 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | A13/A14,05 fullmvn | PASS. Four matcher-argument updates for03 nullable seam; HTTP-error/dedup assertions not removed; C08 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` | A15/A16,05 inheritedI-5, despite prior01/03 ownership | PASS for amendment. Strict existing malformed-placeholder validator only; remaining later READ_COMMITTED change is separately covered by the explicit repair authorization, not laundered through A15/A16 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | A17/A18,05 I-23/I-24/fullmvn | PASS. Only incidental lines63–65 thread-state busywait/assertion retired; substantive lease/cancellation/cache/owner completion retained. C08 succeeds, no discovery production edit |

The full cumulative `git diff b15bb069f43f89c45ee6859dba04a0baff5d774d 60142d42148b9ab94a358249ca784bb8da1f48cd -- src pom.xml` was read, not just repair-touched paths or terminal child reports. Its complete patch was inspected through `artifact://1671` and ranged excerpts; `scope.json` records mechanical per-child actual-vs-authorized sets. Result:01 11,02 6,03 8,04 8,05 14 paths, no unauthorized product path in any child; cumulative union37 paths. Only V169 appears in the migration diff; there are two new review tables and zero added old-store columns. Complete union and amended-file judgments, rather than a “file was already authorized somewhere” shortcut, underpin I-23.

### Fast-P RECORD_ONLY and previous decision reevaluation

| Historical entry | Governing aggregate requirement | Current reevaluation |
|---|---|---|
| Child05 Epoch5 O-01, `DiscoveryVerificationWorkerTest` WAITING vs RUNNABLE, originally outside13-file child scope | Master mandatory full-Maven gate; later A17/A18 narrow test-only authority, not a waiver | RESOLVED as a closed incidental-test compatibility decision. Exact current test delta preserves substantive synchronization/results; fresh C08 all5,715/0fail/0error24defaultskip. No discovery product finding invented and no earlier failed receipt erased |
| Child05 terminal Epoch6 RECORD_ONLY N/A | Same whole-master obligations still apply | Independently rechecked; not accepted merely because child LIGHT_PASS said N/A. Current full gate and I-20 delayed-retarget tests/runtime pass |
| Children01/02/03 terminal RECORD_ONLY N/A | All inherited invariants remain mandatory at aggregate level | Complete child lineage and current full cumulative code audited; no additional open aggregate item established |
| Child04 initial/terminal RECORD_ONLY N/A | I-17/I-18, canonical backend UUID admission | No blanket waiver. Historical AUTO_FIX F-01 failed-detail recovery and F-02 fallback UUID remain resolved by current behavioral C05 tests and current lifecycle/identity source; they are not new aggregate findings |
| Child05 F-01 delayed manual retarget (AUTO_FIX, not RECORD_ONLY) | I-20 original-draft ownership and late callbacks | Resolved. Current code invalidates pending retarget GET/confirmation and original-save redraw; fresh C04 specifically executes both delayed retarget cases and original-save completion; real manual/review body+attachment round trip passes |
| Earlier migration/auth/query/wording/cache/matcher/placeholder/timezone failures | Mandatory real gates plus A1–A16 bounded approvals | Historical failures retained; each approved correction independently checked above. Every required current command passed with genuine current Java11, no source/timezone or gate suppression |

### Fresh production/runtime evidence

This reviewer exercised the actual current `TalentIntroductionApplicationKt` with normal Spring authentication/controllers/services/JDBC repositories, real MySQL8, GreenMail1.6.15 SMTP+IMAP on loopback33428/33429, and a local translation HTTP fixture on33430. Runtime served the current final compiled product classes, not a copied preview, mock controller, localStorage prototype or prior actor's app. Separate authenticated admin/user1 HTTP cookie contexts were used; actual UI was driven through a restricted-reader Chromium context and an isolated administrator Chrome process/profile/CDP endpoint. ES was explicitly confined to loopback`:9`; LLM and scheduling were disabled; all mail recipients/providers were owned `.test` fixtures.

Preserved fresh evidence:

- `/tmp/draft-review-aggregate-epoch2/http-receipts.json`: full real HTTP statuses and bodies for authorization, draft/workflow/CAS/comments/history/counts, malformed placeholders, context/confirmation/send safety and delivery, soft delete.
- `/tmp/draft-review-aggregate-epoch2/lock-races.json`: both actual InnoDB lock-wait schedules and stale/fresh token results.
- `/tmp/draft-review-aggregate-epoch2/runtime-final.json`: complete runtime matrix and disclosed fixture diagnostics.
- `/tmp/draft-review-aggregate-epoch2/browser-receipt.json`, `admin-network.json`, `reader-network.json`: native UI state, viewport/dialog measurements, actual network routing; the reader network contains no sender-account/attachment/approve/send requests.
- `/tmp/draft-review-aggregate-epoch2/reviewed.eml`: received reviewed MIME multipart/alternative, exact frozen subject and canonical HTML/plain body (SMTP CRLF normalization only), no attachment.
- `/tmp/draft-review-aggregate-epoch2/ordinary.eml`: received ordinary unreviewed reply with one original attachment whose exact22 bytes and hash were checked.
- Native screenshots: `reader-wide.webp`, `reader-mobile.webp`, `reader-revoked.webp`, `admin-wide.webp`, `dialog-wide.webp`, `dialog-mobile.webp`, `admin-mobile-sent.webp` in the same scratch directory.

Observed runtime details:

1. Three submitted drafts across two experts returned toolbar3, expert total2 and per-expert2/1 via real size1 pages. Private never-submitted content/history stayed admin404; anonymous401 and reader generic/send403 held. Save no-op retained version, stale save returned409, malformed incomplete placeholder submit422 retained its sole CREATE event. Canonical XSS cleaning retained safe bold/list and stripped executable content.
2. Real workflow covered reader comment, admin return with reason, reader revise/resubmit, approval, locked reader mutation rejection, admin comment after approval, inline translation with frozen HTML unchanged, and soft deletion with retained body/events. Runtime history included CREATE/SAVE/SUBMIT/RETURN/APPROVE/COMMENT/CONTEXT_CONFIRMED/SEND_CLAIMED/SEND_SUCCEEDED as appropriate.
3. For **approve** and **confirm-context**, the independent blocker locked that exact review row; diagnostics observed one real waiting InnoDB request after the production request's initial access read. A separate commit revised an older existing mail body while the lock remained held, and a separate actual context GET showed the changed digest before unlock. The stale request then returned409 `REVIEW_CONTEXT_CHANGED`; full business row, complete events and attempts were unchanged. Fresh observed-token retry returned200; approval/confirmation alone delivered zero messages. This directly replays the prior defect, not just its new test assertion.
4. Fresh GreenMail SMTP injection plus production `/api/mail/auto-reply` IMAP ingestion created actual current conversation messages. A stale confirmation could not authorize sending (409/noSMTP once the unchanged existing identity-safety prompt was confirmed). Fresh confirmation itself still delivered zero. Explicit reviewed send produced one real canonical MIME; repeated SENT request produced no extra delivery.
5. Ordinary unreviewed manual send used the original route with a real uploaded attachment and delivered exactly those original bytes without any review identity. Total final recipient mail count was3: two independently reviewed replies and one ordinary attachment reply; final database had three SENT attempts and three new outbound records.
6. Native reader at1440/390 used the real contenteditable editor: bold selection, unordered list, dangerous rich paste safely sanitized, explicit canonical save. A focus refresh preserved the same editor DOM node and caret containment. Dirty offline navigation was rejected while keeping the current expert and dirty text; returning online saved successfully. Removing the live grant and focusing cleared timeline/editor/discussion/history state; subsequent detail returned403. Only mailbox was available in the mobile menu, with no admin composer/tools or email in the expert header.
7. Native admin pending toolbar kept expert-card layout. Administrator original text+uploaded attachment survived manual→submitted-review→manual; review contained one editor, its own frozen body and no visible send control. Native approval did not send, and the right-side approved content remained available while the pending count decreased. Translation used the inline11px text action, comments/history used the actual service, and original review HTML stayed unchanged.
8. Context confirmation dialog was a true native modal: at1440 it measured480px wide; at390 it measured358px with x16/right374 and a fully opaque white background. Focus was inside; Tab/Escape/cancel were exercised. Cancel/Escape required the next send attempt to reopen. Confirm produced no SMTP; a subsequent real inbound invalidated it and reopened confirmation. Only a later explicit send entered the dedicated route, followed the existing safety422→confirmation→200 flow, delivered one message and removed the SENT send control. Mobile document scrollWidth remained390.

Runtime diagnostic failures were not hidden or treated as product failures:

- Two initial GreenMail launch configurations combined test presets with explicit protocol ports and failed duplicate-server setup before readiness; corrected to explicit owned protocol endpoints alone.
- The first auto-reply call intentionally initialized an empty cursor without importing historical mail. The next UID exposed the fixture's unreachable local ES operator-status update; after inspecting the unchanged production precondition, only owned contacts were seeded as already REPLIED/application_indexed. The retained UID then ingested through the real pipeline, and subsequent fresh messages ingested normally. No production/source/network provider was enabled.
- An initial attachment upload used a reviewer-mistyped storage environment name and failed at the default `/opt/talent` permission boundary. Only this reviewer's app was restarted with the actual `MAIL_ATTACHMENT_STORAGE_BASE_PATH` override. Successful upload, actual ordinary attachment SMTP and all UI/runtime checks followed; no filesystem permission/global configuration mutation was performed.
- Harness corrections concerned actual DOM state wording/selectors, native tool syntax, asynchronous completion waits, separate browser context recovery, and MIME CRLF comparison. No assertions/product/tests were edited; successful operations were not replayed as replacements for fresh evidence. Existing safety gating legitimately preceded context rejection and was tested in that real order.

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| **V-1** — approval and context confirmation accept unseen committed conversation update after draft-lock wait | **RESOLVED** | Prior Epoch1 FAIL/INITIAL counterexamples remain unchanged historical evidence. Current Service:112 and156 use READ_COMMITTED; repair tests at MailReviewDraftIT:325–401 freshly pass in C09; two independent actual production HTTP/MySQL lock-wait reproductions now return409 with no business/event/attempt mutation, fresh-token200 and SMTP0 |

### Findings

#### P1
N/A — no persistent or new proven mandatory product violation established on this complete boundary.

#### P2
N/A — no additional actionable in-scope nonmandatory defect established. Approved incidental-test/wording/assertion retirements were not reopened as style preferences or product P1s.

#### Observations

- The genuine currentJava11 environment is important: the documented older system11.0.15 tzdb is not equivalent. All current command receipts use the supplied11.0.33, and the supplied archive hash was independently checked.
- MySQL EXPLAIN and the size100 statement measurement are evidence for the approved query shape, not a new fixed optimizer-plan or statement-count contract.
- Deliberately failing local ES and default-storage fixtures required owned-fixture corrections. These did not require or justify source repair, scope expansion, production access, or global Docker/OrbStack restart.

### Evidence Boundaries and Cleanup

- Machine verification is local to the exact retained-worktree code boundary, actual local production surfaces, disposable owned databases and `.test` mail fixtures. No assertion is made about a deployed production version, live provider delivery, production data, or human acceptance.
- Prescribed failure/concurrency/UNKNOWN/rollback permutations were freshly exercised by the named real-transaction integration suites using their plan-authorized mocked delivery seam; actual SMTP/IMAP additionally proved the end-to-end production happy path, stale context, existing safety gate, deduplication and ordinary attachment path. A real-world ambiguous remote delivery was not manufactured against a provider and is not claimed.
- Full-Maven default24 opt-in skips are explicitly disclosed; every named required enabled target ran nonzero tests with zero skips. No mandatory evidence item remains unavailable.
- All owned app/mail/translation/Chrome services were stopped; all owned managed tabs and the raw isolated context were released. `docker stop draft-review-aggregate-epoch2-mysql && docker rm -v draft-review-aggregate-epoch2-mysql` exited0 and disposed only this reviewer's schemas/container. Cleanup receipt: `/tmp/draft-review-aggregate-epoch2/cleanup.json`.
- No global daemon restart, unrelated process/resource mutation, production addresses/provider use, product/test/approved-plan/fast-p/review-evidence write, staging, commit, push, merge or deployment occurred. Scratch logs, downloaded fixture dependencies, local receipt/report artifacts, disposable DB records and ordinary test build outputs were the only writes.

### Constrained-verification summary

Build/normal compilation: PASS. Tests: PASS with disclosed default opt-in skips and all required explicit targets non-skipped. Accumulation check: PASS. State-machine check: PASS. Cross-plan check: PASS. Deleted-code/callsite audit: PASS. No extras: PASS. Scope compliance: PASS. Manual acceptance: PENDING.

Prior blocking set `{V-1}` shrank to empty with both original production schedules materially corrected; no equal-or-higher regression or new required scope appeared. Therefore convergence is **PROGRESSING**, not an initial result, a renamed finding, or an inherited executor PASS.

### Repair planning

**Repair planning: N/A.** Current verification PASS does not route to repair-p. The previously approved single repair artifact remains unchanged; no new/overwritten repair plan, continuation, execution authorization or automatic repair was created.

### Next Action

The controller should preserve this complete Epoch2 report verbatim in its authorized append-only machine-verification evidence and proceed to the pending human acceptance of the complete master plan. Human acceptance remains **PENDING**; do not infer it from this PASS. No new execute-p action is needed or authorized by this review.

**No product code was modified.**
