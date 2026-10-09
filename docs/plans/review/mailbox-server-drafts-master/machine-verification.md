# Aggregate Machine Verification — mailbox-server-drafts-master

## Epoch 1 — 2026-10-09

- Master plan: `docs/plans/2026-10-08/mailbox-server-drafts-master.md` (sha256 `fca914b11731d582381dde9254b687b86fe37c1198ef09317d2353e6d48ac676`)
- Governing master identity: identical worktree sha256; recorded commit `02af6d42cdf3617c48335a9ad3aeddb025d1302f`; `CONSISTENT`.
- Amendments: A1–A4 recorded and human-approved in `docs/plans/fast/mailbox-server-drafts-master/ledger.md`. A1/A2 authorize the exact five child03 frontend regression fixtures; A3/A4 authorize only `MailReplyDraftRepositoryIT.kt` as the transaction-proxy fixture. The aggregate boundary follows those approvals.
- Boundary: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d..6d80d6243be6af9c78f4ce44a54a29c7c1bc9033`
- Tested evidence HEAD: `71e0296e69083817f008d005b740dbb503680ef3`; it differs from the final code head only by five fast-p evidence files.
- Worktree / branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master` / `fast/mailbox-server-drafts-master`
- Resolution: `DISCOVERED_FROM_GIT_WORKTREES`. `discover_fast_p.py` was invoked with the exact repository and master path but exceeded the command window before returning JSON. Its registered-worktree source set independently yielded exactly one exact master-ledger match: this worktree's `docs/plans/fast/mailbox-server-drafts-master/ledger.md`. The selected fast ledger, handoff, and each concrete child `brief.md`, `execution.md`, `fix-log.md`, and `verify-log.md` were read; no review ledger from another worktree was used.
- Reviewer: `/root/aggregate_reviewer`, fresh isolated reviewer created after `final_code_head` and distinct from all recorded fast-p writers/verifiers.
- Result: **FAIL**
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/mailbox-server-drafts-master/repair.md` (sha256 `6c36cfb4774ed9ca160b2d39a731dab68ba33afc5fb567c7ad169b084c64c9e4`), `DRAFT_READY`.

## Fresh Command Evidence

All Maven commands used `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, `DOCKER_API_VERSION=1.44`, and `-Dapi.version=1.44`; required integration runs used real Docker/MySQL.

| ID | Fresh command | Result |
|---|---|---|
| C1 | `mvn -Dapi.version=1.44 -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test` | PASS, exit 0; 17 tests (11 service, 6 controller); failures/errors/skips 0. |
| C2 | `mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test` | PASS, exit 0; real MySQL, 8 tests; failures/errors/skips 0. |
| C3 | `mvn -Dapi.version=1.44 -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test` | PASS, exit 0; real MySQL, 39 tests; failures/errors/skips 0. |
| C4 | `mvn -Dapi.version=1.44 -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test` | PASS, exit 0; 100 tests (42 Pending, 58 ManualReply); failures/errors/skips 0. |
| C5 | `mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest test` | PASS, exit 0; real MySQL, 27 tests (draft-send 20, meeting 7); failures/errors/skips 0. |
| C6 | `node --check src/main/resources/static/mailbox-chat.js` | PASS, exit 0. |
| C7 | `node --check src/main/resources/static/app.js` | PASS, exit 0. |
| C8 | `node --test src/test/js/*.test.js` | PASS, exit 0; 1567 tests, 298 suites; failures/cancelled/skips/todo 0. |
| D1 | Controlled-transport actual-component auth-invalidation diagnostic | PASS, exit 0; establishes V-1: private subject/preview/card remain after confirmed anonymous auth result and after stale old-owner list release; zero PUT/DELETE. |
| D2 | `git diff --check 7c86599f85f6462e00a3fcd2c5a74f1ca57f013d 6d80d6243be6af9c78f4ce44a54a29c7c1bc9033 -- src` | PASS, exit 0. |
| D3 | Node SHA256 manifest comparison with baseline and current product files | PASS, exit 0; 315 baseline files; `baseMismatch=[]`, 9 current source changes, `outsideBoundary=[]`. |

The initial sandbox-denied target-write attempts for C1 and the frontend fixture were superseded by approved fresh runs above. A first D3 invocation exceeded Node's default read buffer; the exact same read-only diagnostic reran with 16MiB buffer and passed. C3's logged V24 error is the intentional historical-negative migration case and the suite finished 39/39 PASS. None is a product finding.

## Aggregate Contract Matrix

| Contract | Verdict | Fresh evidence |
|---|---|---|
| Master requirements 1–3: server ACK persistence/recovery; only discard or durable exact-version SENT cleanup; mailbox-native drafts UI | PASS | C1/C2/C5/C8; service/repository/controller/UI path inspection. |
| M-1: only current server ACK displays saved | PASS | C1/C8; localSeq/ACK behavior inspected. |
| M-2: owner/id/sendVersion precise two cleanup paths | PASS | C2/C5; draft repository/service and success/failure lifecycle inspection. |
| M-3: dedicated table; no shared mail table, count/status, ES, or attachment writes | PASS | C2/C3; V152 and CRUD write-path audit. |
| M-4: optional `draftRef`; old fingerprint, account, anchor, validation and send semantics unchanged | PASS | C4/C5; legacy and draft paths inspected. |
| M-5: 01→02→03 sequence, final shared-file inheritance, individually recorded gates | PASS | approved ledger boundaries; child 9/9, 9/9, 13/13 authorized-file checks; C1–C8. |
| Master preservation: pagination/filtering, follow/suspension/processing state, templates/safety/QA/RAG/meeting/attachments/idempotency | PASS | combined UI/send path inspection; C2/C4/C5/C8. |
| Master execution gates 1–5 and non-goals | PASS, except release remains prohibited by V-1 | D3 baseline/manifest; C3 V152; A1–A4 authority; no destructive SQL, TTL, deployment, platform or queue changes. |
| 01 O-1/O-2 and N-1/N-2: authenticated storage/recovery; no implicit removal/revival; no business/ES/SMTP write; attachment authorization | PASS | C1/C2; controller/session, service, repository and real MySQL tests. |
| 01 I-1–I-7: normalized owner/target unique key; monotonic state; two cleanup paths; CAS/409; typed ≤1MiB snapshot; paired send binding; incomplete/attachment snapshot safety | PASS | C1/C2, V152/repository/service audit. |
| 01 T-1–T-3/scope: MySQL-compatible schema/JDBC, six APIs/statuses, real MySQL CAS/unique/terminal and migration testing, nine-file/no UI-send scope | PASS | C1/C2/C3 and segment `base..099e372c2eca32596a9db670c0f13a0a30ce2b7e`. |
| 02 O-1/O-2 and N-1/N-2: original send entry; durable success close; failures/unknown/cancel/new version safe; old calls and render/safety/QA/RAG/meeting/attachments/audit retained | PASS | C4/C5 and send-service/pending-operation audit. |
| 02 I-1–I-7: owner/version/request projection; raw-template compatibility; short transaction claim/bind; atomic success; safe non-success; trusted completed/DEDUP path; correct discard/concurrency | PASS | C4/C5, including 27 real-MySQL tests. |
| 02 T-1–T-4/scope: nullable DTO compatibility; reference validation; bind/close/failure state machine; strict shortcut proof; real transaction/CAS/latch/fault testing; nine-file/no queue/rewrite scope | PASS | C4/C5 and segment `099e372c2eca32596a9db670c0f13a0a30ce2e3e801a4` authorization audit. |
| 03 O-1/O-2/O-3 and N-1/N-2: entry/markers/target context, automatic persistence/recovery, explicit discard/send closure, pre-existing UI/feature preservation | PASS, with auth-visible-data exception below | C5/C8; UI/app/CSS/index audit. |
| 03 I-1–I-7: GET/ACK/no browser persistence; write-site owner/target/seq; debounce/flush/offline retention; conflict handling; sanitized target/context restore; version-aligned send; explicit discard | PASS | C5/C8; component/regression and runtime-path inspection. |
| **03 I-8: session change clears visible old data and isolates old responses** | **FAIL — V-1** | C8 does not cover confirmed anonymous seam; D1 proves retained private card/subject/preview after `/api/auth/me` returns authenticated=false and after delayed stale list response. |
| 03 S-1–S-4: required tab/cards/status/confirm/ARIA styling and no unapproved CSS/dialog/inline-style change | PASS, except V-1 renders retained card | C8; UI/CSS audit. |
| 03 T-1, T-3–T-5: in-module coordinator, owner/target/async/LRU isolation; draft entry/paging/open/retarget; resource/behavior/real-app limits; 13-file scope/non-goals | PASS, except V-1 | C5/C8; segment `05bb797a51dd94b8aa712bc8f36e2426e3e801a4..6d80d6243be6af9c78f4ce44a54a29c7c1bc9033`. |
| **03 T-2: logout/navigation/unmount and Session-change visible-data clearing** | **FAIL — V-1** | D1: confirmed unauthenticated branch invalidates dispatch but retains old list/selection for rendering. |
| Required commands C1–C8 | PASS | all fresh; mandatory integration tests executed against MySQL with zero skips. |

### Finding V-1 — P1, Repairable

**Confirmed authentication invalidation leaves prior owner's private draft subject, preview, card, and selected state visible.** Child03 I-8 and T-2 require visible old data to clear on a Session change and stale old-owner responses to remain isolated.

- `src/main/resources/static/mailbox-chat.js:2642–2652` processes the valid HTTP 200 `{authenticated:false}` result by clearing `auth.ready`, username, module owner and summary, but does not clear `instance.list.items`, `list.total`, pager or selected draft state.
- `renderDraftList` at `mailbox-chat.js:2110–2127` renders retained subjects/previews without an auth guard.
- `/api/auth/me` legitimately returns HTTP 200 `authenticated=false`; the generic response handler's 401/403 branch is not a substitute for clearing this normal application response.
- D1 seeded an admin private draft, opened its draft/editor, held an actual admin list response, changed `/api/auth/me` to unauthenticated, and triggered the existing auth/flush seam. The flush correctly rejected because the login user changed, but `subject=true`, `preview=true`, `cards=1`, displayed total `0`, and the stale response release left the same leak. `PUT/DELETE=[]`.

Impact: anonymous or expired Session users can see the previous owner's private draft summary; list count and visible cards diverge. This is a confirmed privacy violation, not a missing-browser-test inference.

The bounded repair authorizes only `src/main/resources/static/mailbox-chat.js` and `src/test/js/mailboxServerDrafts.test.js`. It preserves transient network-error/offline input retention, server drafts, historical F-1/F-2 protections, and all existing send/discard/CAS/attachment contracts.

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | First aggregate epoch; D1 confirms anonymous retained draft UI. |
| Child03 F-1 | RESOLVED | C8 passes DOM-baseline reversal regression. |
| Child03 F-2 | RESOLVED for its successful A→B seams | C8 passes delayed list/auth-switch regression; V-1 is the untested anonymous seam. |
| Child03 O-1 | RETAINED, RECORD_ONLY | Human/browser coverage limits remain human acceptance work, not repair authorization. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Existing desktop/mobile real-app/MySQL/SMTP smoke | non-static evidence, M-1–M-4, 03 T-5 | historical evidence retained; not a new human/browser pass | child03 execution records actual app/SMTP runs; no fresh browser result asserted. |
| QA/RAG adoption, different-user browser, orphan, uploading→failed, delayed-upload+12-visits combinations | 03 A-4/A-8 and relevant invariants | component/backend contracts pass; human tests remain pending | C1/C2/C4/C5/C8; no fabricated human/brower success. |
| Different-user coverage | 03 I-2/I-8 | successful A→B regression passes, confirmed anonymous seam fails V-1 | C8 plus D1. |

## Manual Acceptance

All required manual items remain `PENDING`: master A-1/A-2; storage A-1–A-4; send A-1–A-5; UI A-1–A-8. Machine verification does not mark any human test passed.

## Review-P / Repair-P Result

`verify-p`: `FAIL / INITIAL`, due solely to V-1. `repair-p`: `DRAFT_READY` at `docs/plans/fix/mailbox-server-drafts-master/repair.md`; the repair contains exactly two authorized frontend files and the required resolved Review-Fast-P one-approval execution handoff (product commit `fix(mailbox-drafts): clear drafts on session invalidation`; execution-evidence commit `docs(review-fast-p): record mailbox draft repair execution`).

No product code was modified by this review. The reviewer did not stage, commit, or write review evidence.

## Epoch 2 — 2026-10-09

- Master plan: `docs/plans/2026-10-08/mailbox-server-drafts-master.md` (worktree SHA-256 `fca914b11731d582381dde9254b687b86fe37c1198ef09317d2353e6d48ac676`; recorded commit `02af6d42cdf3617c48335a9ad3aeddb025d1302f`).
- Invoked identity: SHA-256 `6aff85b10e29fa1528418024c2e05ad52d4b713eb9afc4639c87f513ee88f632`; governing state `AMENDMENT_RECORDED`.
- Governing amendment: A1/A3 in the selected fast-p ledger, authorized by `执行前门禁第3项、第4项；变更文件清单`, with recorded human approval. The governing diff authorizes five named existing frontend fixtures and `MailReplyDraftRepositoryIT.kt`, without production-scope expansion.
- Boundary: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d..65eb16774c64e6c741a0339a481dda35739d9c8c`.
- Repair lineage: `DURABLE_HANDOFF`; prior boundary `6d80d6243be6af9c78f4ce44a54a29c7c1bc9033`, repair SHA-256 `6c36cfb4774ed9ca160b2d39a731dab68ba33afc5fb567c7ad169b084c64c9e4`, approved execution record `repair-execution.md`, exact two-file repair delta verified.
- Reviewer: `/root/aggregate_reviewer_epoch2`, fresh after the repair commit and distinct from the recorded fast-p writers/verifiers and repair executor.
- Result: **PASS**.
- Convergence: `PROGRESSING` — epoch 1 blocking set `{V-1}` is now empty.
- Repair artifact/result: existing `docs/plans/fix/mailbox-server-drafts-master/repair.md`; `repair-p: NO_ACTION`.

### Fresh Command Evidence

All Maven commands used `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, `DOCKER_API_VERSION=1.44`, and `-Dapi.version=1.44`; integration runs used real Docker/MySQL.

| ID | Command | Result |
|---|---|---|
| C1 | `mvn -Dapi.version=1.44 -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test` | PASS; 17/17. |
| C2 | `mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test` | PASS; real MySQL; 8/8. |
| C3 | `mvn -Dapi.version=1.44 -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test` | PASS; real MySQL/Flyway V152; 39/39. |
| C4 | `mvn -Dapi.version=1.44 -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test` | PASS; 100/100. |
| C5 | `mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest test` | PASS; real MySQL; 27/27. |
| C6 | `node --check src/main/resources/static/mailbox-chat.js` | PASS. |
| C7 | `node --check src/main/resources/static/app.js` | PASS. |
| C8 | `node --test src/test/js/*.test.js` | PASS; 1,568/1,568; 298 suites. |
| D1 | `git diff --check 7c86599f85f6462e00a3fcd2c5a74f1ca57f013d 65eb16774c64e6c741a0339a481dda35739d9c8c -- src` | PASS. |

C8 first encountered sandbox-only fixture-output `EPERM` under `target/`; its exact rerun passed with no assertion failure.

### Aggregate Contract Matrix

| Contract | Verdict | Evidence |
|---|---|---|
| Master persistence/UI objective | PASS | C1–C8; source and complete diff audit. |
| M-1: server ACK only | PASS | C1/C2/C8. |
| M-2: explicit discard or durable sent close only | PASS | C1/C2/C4/C5. |
| M-3: isolated reply-draft table | PASS | C2/C3; V152 migration. |
| M-4: optional `draftRef`; legacy send retained | PASS | C4/C5. |
| M-5: ordered `01→02→03`; shared-file inheritance | PASS | terminal fast-p ledger; scope audit. |
| 01 storage/API/CAS/owner/attachment contract | PASS | C1/C2/C3. |
| 02 send claim/bind/CAS/durable-close contract | PASS | C4/C5. |
| 03 UI autosave/recovery/conflict/discard/send contract | PASS | C5/C8. |
| 03 I-8/T-2 identity isolation | PASS | `mailbox-chat.js:2640–2649`; `mailboxServerDrafts.test.js:320–356`; C8. |
| A1/A3 amended fixtures | PASS | Exactly five authorized frontend fixtures and one backend HTTP fixture. |
| Non-goals/preservation | PASS | Source scope; C1–C8. |
| Required commands | PASS | C1–C8 completed freshly; no skipped mandatory tests. |

Baseline-to-final scope has 29 `src` paths, each within the authorized child/master scope. The repair delta is exactly `src/main/resources/static/mailbox-chat.js` and `src/test/js/mailboxServerDrafts.test.js`.

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 P1 anonymous-session draft disclosure | RESOLVED | Confirmed-anonymous handling clears list/count/selection and increments `listSeq`; delayed old-owner response is rejected; C8 passes. |
| Child03 F-1 | RESOLVED, stable | DOM-baseline regression remains passing. |
| Child03 F-2 | RESOLVED, stable | A→B delayed-list identity seams remain passing. |
| Child03 O-1 | RETAINED, RECORD_ONLY | Browser/human coverage is pending. |

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- O-1 is not a waiver: fresh automated component/backend evidence passed, but it does not replace human/browser acceptance.

### Evidence Boundaries

- Fresh browser/manual acceptance was not performed or inferred from machine tests.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Historical real-app/MySQL/SMTP smoke | M-1–M-4; 03 T-5 | Retained historical evidence; no new browser success claimed. | Child03 execution records. |
| QA/RAG, different-user browser, orphan restore, uploading→failed restore, delayed-upload/twelve-visit combinations | 03 A-4/A-8 and related invariants | Automated component/backend coverage passes; human/browser acceptance remains pending. | C1/C2/C4/C5/C8. |
| Different-user coverage | 03 I-2/I-8 | Automated A→B and confirmed-anonymous regression pass; human acceptance remains pending. | C8; V-1 regression. |

### Manual Acceptance

Master A-1/A-2 remain `PENDING`. Machine verification neither performs nor marks human checks.

### Next Action

- Obtain human manual/browser results for master A-1/A-2 and explicit sign-off of the reviewed boundary and governing master identity.

No product code was modified. The reviewer did not write review evidence, stage, or commit.
