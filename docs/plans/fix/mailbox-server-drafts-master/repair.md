# Repair Plan: mailbox-server-drafts-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/2026-10-08/mailbox-server-drafts-master.md`
Governing identity: sha256 `fca914b11731d582381dde9254b687b86fe37c1198ef09317d2353e6d48ac676`, approved commit `02af6d42cdf3617c48335a9ad3aeddb025d1302f`, CONSISTENT; approved amendments A1–A4 in the selected fast-p ledger.
Verification report: `aggregate/master`, epoch 1, reviewer `/root/aggregate_reviewer`, 2026-10-09, `verify-p FAIL / INITIAL`, finding V-1. The complete report is returned to the controller; its destination is `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/review/mailbox-server-drafts-master/machine-verification.md`. The reviewer does not write that evidence file.
Implementation boundary: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d..6d80d6243be6af9c78f4ce44a54a29c7c1bc9033`; tested evidence HEAD `71e0296e69083817f008d005b740dbb503680ef3` differs only in fast-p evidence files.
Execution worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master`, branch `fast/mailbox-server-drafts-master`.

## Objective

After a confirmed session/owner invalidation, no previous owner's draft subject, preview, card, count or selected draft content remains visible, including after an old asynchronous list response completes. Server drafts remain intact and old-owner work remains unable to dispatch under another or anonymous Session.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1, REPAIRABLE | Child03 I-8 and T-2 require clearing visible old data on Session changes and isolating old responses. | `mailbox-chat.js:2642–2652` invalidates authentication and the module owner but leaves owner-scoped list items/total and selected state; `renderDraftList:2110–2127` renders retained private subjects/previews without an auth guard. Fresh actual-component diagnostic changes `/api/auth/me` to authenticated=false: private subject/preview remain, one card remains despite displayed total0, and still remain after a held admin list response is released. No PUT/DELETE occurred. |

`/api/auth/me` legitimately returns HTTP200 with authenticated=false (`AuthController.kt:54–64`); the existing response handler only intercepts401/403. These are read-only dependencies, not additional repair files.

## Findings Excluded

| Finding | Reason |
|---|---|
| Child03 F-1 | RESOLVED: baseline reversal regression now saves current DOM bytes and has passing fresh behavior tests. |
| Child03 F-2 | RESOLVED for its recorded successful A→B auth-resolution and applyOptions seams. V-1 is the newly proven unauthenticated seam; do not undo those repairs. |
| Child03 O-1, RECORD_ONLY | Incomplete human/browser experiments remain PENDING. No model configuration, expanded browser matrix or architecture/security redesign is authorized by this repair. |
| Initial sandbox target-write failures | Superseded by approved fresh passing command reruns; environment-only, not product defects. |

## Unchanged Contract

- Preserve master M-1–M-5 and all three child contracts. In particular, server ACK is required for saved status; only explicit discard and durable exact-version SENT can clear server content.
- Preserve typed snapshots, ownership checks, CAS, terminal anti-resurrection, version-aligned sends, request IDs, original send fingerprints, SMTP/meeting/QA/RAG/attachment rules and unchanged business tables.
- Preserve 800ms debounce, dirty/ACK sequencing, LRU-independent persistence, successful A→B clearing, old-response isolation, awaited navigation/logout and offline/error retention. Do not turn a transient network lookup failure into authorization to delete server drafts or discard recoverable unsaved input.
- Clear visible old-owner state on confirmed invalidation without server DELETE, automatic reopen, cross-owner replay or a new authentication system.
- Preserve frozen S-1–S-4 CSS/DOM, resource keys/order, existing editor and host boolean send contract.

## Authorized Files

| File | Purpose |
|---|---|
| `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/src/main/resources/static/mailbox-chat.js` | Correct owner-invalidation handling for visible draft state and asynchronous ownership. |
| `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/src/test/js/mailboxServerDrafts.test.js` | Add one discriminating behavioral regression using the existing component/controlled-transport harness. |

## Repair Tasks

### R-1: Clear old visible drafts on confirmed authentication invalidation

- Resolves: V-1. Convergence is INITIAL; one root cause, one task, two authorized files, one frontend subsystem.
- Root cause: confirmed anonymous/session-invalid handling invalidates dispatch but does not clear the previous owner's visible list state; the retained list is rendered again.
- Files: only the two Authorized Files above.
- Change: when an auth check confirms the captured owner is no longer authenticated, promptly invalidate that owner's visible draft/list/selection state and stale response ownership before rendering or awaiting another auth/list result. Cleared list/card/count/pager state must remain cleared while anonymous and after delayed old-owner responses. Keep existing dispatch guards and correct subsequent authenticated loading. Do not mutate server drafts or weaken the existing offline/error retention contract.
- Regression test: seed admin with private subject/preview, open the actual draft tab/editor, hold a genuinely issued admin list response, make `/api/auth/me` return `{authenticated:false,username:null}`, and trigger the existing auth-check/flush seam. Assert flush rejection, zero previous-owner subject/preview/cards/selected content and zero counts before and after releasing that response, no PUT/DELETE under the invalidated Session, and unchanged persisted admin draft. Use the existing parsed-DOM/component harness, not a function-name/source-only assertion.
- Existing verification: all commands below; retain and rerun F-1 reversal and F-2 successful A→B delayed-list regressions.
- Must not change: backend/API/schema/auth response protocol, server lifecycle, content persistence semantics, offline retention, existing authenticated identity switches, old-owner dispatch isolation, mail/meeting/attachment behavior or frozen style contracts.
- Prohibited: product/test files outside Authorized Files, plan amendments, runtime auth redesign, extra test matrices unrelated to V-1, broad cleanup, model setup, production data operations, automatic implementation/review loops, push/merge/deployment.

## Verification Commands

Run in the exact execution worktree. Required MySQL and migration tests must execute against real Docker/MySQL; skipped integration tests do not pass. Use the necessary JDK11/API1.44 environment, not a source/pom change.

1. `node --test src/test/js/mailboxServerDrafts.test.js`
2. `node --check src/main/resources/static/mailbox-chat.js`
3. `node --check src/main/resources/static/app.js`
4. `node --test src/test/js/*.test.js`
5. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 mvn -Dapi.version=1.44 -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test`
6. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test`
7. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 mvn -Dapi.version=1.44 -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test`
8. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 mvn -Dapi.version=1.44 -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test`
9. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_API_VERSION=1.44 mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest test`

## Completion Criteria

- The V-1 regression fails on the reviewed product head and passes after the bounded repair, with no retained private cards/subject/preview/selected content or counts before/after the old response, no forbidden writes, and the admin server draft intact.
- Existing F-1/F-2, offline/navigation/logout, GET/ACK/CAS/terminal, send/discard, upload/LRU and restoration regressions remain passing.
- All required commands exit0, with zero failures/errors/skipped mandatory tests; integration evidence is real MySQL, not mocks/H2.
- Product/test changed files remain exactly within Authorized Files. No baseline plan or unrelated tracked files are modified.
- Executor returns READY_FOR_VERIFICATION only after the authorized local product commit and exact execution evidence handoff described below. Independent aggregate review and human acceptance remain required; this draft does not declare the product repaired or accepted.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/fix/mailbox-server-drafts-master/repair.md` invocation approves its current contents. No agent-originated transition is approval.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/fix/mailbox-server-drafts-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(mailbox-drafts): clear drafts on session invalidation`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/review/mailbox-server-drafts-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record mailbox draft repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
