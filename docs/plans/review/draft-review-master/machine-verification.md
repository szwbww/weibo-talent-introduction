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
