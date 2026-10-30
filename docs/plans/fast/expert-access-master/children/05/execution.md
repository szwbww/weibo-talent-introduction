# Child 05 — execution

## Epoch 1 — PLAN_CONFLICT: required existing test consumers outside Authorized Files

- Executor: `ExpertAccess05Impl`; execution epoch: NEW.
- Approval basis: current epoch1 brief and standing authorization recorded there.
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-05-pending-owner.md`.
- Plan SHA-256 before and after audit: `557020506b327615acea966f31c3983fd5b3095ba2b3e3d3b00b7c11ba72a7bb`.
- Execution ID: canonical plan path above `@557020506b327615acea966f31c3983fd5b3095ba2b3e3d3b00b7c11ba72a7bb`.
- Governing master: read all lines 1–451; authoritative master commit supplied by brief: `e9f93fb260c6b25518ccbbf579c420c8aa6cb406`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`.
- Branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Pre-execution product/code SHA: `a2d4b0e68691d115cff2a6ad3bcc115a06b28d27` (`feat(fast-p): implement 04b`); ancestry check to current target HEAD exited 0.
- Pre-execution HEAD and post-audit HEAD: `fc2fce1887d7e586d30d09db657ad4851d9501b4`.
- Post-execution product SHA: unchanged; no product commit, staging, source/test mutation, service, or scaffold created.
- Evidence HEAD: no executor evidence commit; controller owns evidence commits.
- Initial dirty paths: `docs/plans/fast/expert-access-master/children/05/brief.md`, `docs/plans/fast/expert-access-master/ledger.md`; preserved.
- This append is the only executor mutation.

### Proven prerequisite / requested bounded controller amendment

Before product mutation, authorize these additional existing test consumers and amend child/master/brief/ledger to a new immutable plan identity:

1. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
   - Lines 1055–1069 and 1090–1092 explicitly require two entity `save` calls and inspect the second saved entity for reclassification. Plan §3 / I-12 requires replacing that second save with an explicit narrow UPDATE. These expectations cannot coexist with the approved implementation.
   - Lines 1191–1193 inject the reclassification failure through the second `save`; the failure/rollback/no-ack regression must move to the actual narrow UPDATE rather than becoming a silently unexercised assertion.
   - Preserve first persistence, automatic classification, UID/cursor/physical-mailbox, notifications, tags, no-SMTP, and failure/ack assertions. Adapt the changed SQL consumer and its real argument assertions, not unrelated test hygiene.
2. `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
   - Lines 206–207, 316–317, 341–342, 365–366 mock whole-entity save as the successful binding/completion writer.
   - Lines 209–214, 319–324, 344–349, 373–376 assert the resulting updated entity, and line 324 explicitly verifies `save`. Plan §3 requires narrow UPDATE plus reread, not `save(record.copy(...))`. The current `findById` stubs return the pre-update entity, so preserving those fixtures would return the old unbound/unresolved state.
   - Move the fixtures to the actual narrow UPDATE and persisted reread, preserving alias binding, metadata/document bridging, no automatic reply, resolution, and expert-wide attention behavior.
3. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
   - Lines 2334–2350, 2432–2445 and 2491–2505 execute real `cancelResolved` with linked contact 1 while mocking its inbound repository; only findById/reopen are currently supplied.
   - Plan §4 requires contact-first lock before processing UPDATE. A strict repository lock returning/verifying the actual contact ID requires a real non-null lock fixture in these existing linked-contact consumers; do not ignore a missing lock result or introduce a nullable/default security dependency to keep them passing.
   - Preserve exact resolved triplet, zero-row 409, missing-row 404, null-contact, operator identity, audit and expert-attention assertions. Add/adapt lock-order assertions without broadening production behavior.

Requested scope: original 9 plus these 3 existing fixtures, requiring an explicit I-14 bounded file-count amendment. No new constructor dependency, product endpoint, resource permission, schema field beyond the approved owner column, or second fixture convention is requested. Required extra fixture commands must execute these classes freshly with the final gates; no skip may substitute for execution.

### Consumer and write-path audit receipts

The following are exact `functions.grep` patterns and paths used (all paths below relative to TARGET_WORKTREE). Results were inspected before source mutation; no LSP tool is mounted.

**Constructor/type audit**

- Pattern: `\b(AutoMailReplyService|PendingMailOperationService|UnmatchedInboundMailService|ExpertMailReadAccessService)\s*\(`; path: `src`.
- Output declarations: `AutoMailReplyService.kt:35`, `ExpertMailReadAccessService.kt:24`, `PendingMailOperationService.kt:62`, `UnmatchedInboundMailService.kt:20`.
- Output direct consumers:
  - `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt:137`
  - `.../mail/controller/OutboundAttachmentFlowTest.kt:212`
  - `.../mail/repository/MailReplyDraftRepositoryIT.kt:60`
  - `.../mail/repository/MailReviewQueryIT.kt:74`
  - `.../mail/service/AutoMailReplyServiceTest.kt:121`
  - `.../mail/service/ExpertMailPermissionGuardTest.kt:66`
  - `.../mail/service/MailReplyDraftSendIntegrationTest.kt:133`
  - `.../mail/service/MailReviewSendIT.kt:129`
  - `.../mail/service/MeetingCalendarSendIntegrationTest.kt:183`
  - `.../mail/service/PendingMailOperationServiceTest.kt:121`
  - `.../mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:107`
  - `.../mail/service/UnmatchedInboundMailServiceTest.kt:33`
- Constructor reads: Auto lines 35–75 has existing inbound repository, TransactionTemplate and InboundAutoResponseDetector; access lines 24–25 has NamedParameterJdbcTemplate; Pending lines 63–82 and Unmatched lines 20–29 already have inbound/contact repositories. No new constructor dependency is necessary: lock/current-read/narrow writes belong to authorized repositories/JDBC seams.

**Spring/type-reference audit**

- Pattern: `(AutoMailReplyService|PendingMailOperationService|UnmatchedInboundMailService|ExpertMailReadAccessService)::class`; path: `src/test`.
- Actual service imports/support registrations from output: `MailboxConversationControllerTest.kt:131,2195`; `ExpertMailReadAccessIT.kt:79`; `ExpertScopedOperationsIT.kt:65`; `MeetingCalendarSendIntegrationTest.kt:697`.
- Reflection constructor consumer: `ExpertInboundNotificationRepositoryIT.kt:430` (real dependency map begins :431; real inbound repository/contact/alias bindings).
- Other matches are Mockito mocks in MailAutomationControllerTest, OutboundAttachmentFlowTest, UnmatchedInboundAiReplyTurnKnowledgeTest, UnmatchedInboundMarkResolvedIdentityTest, UnmatchedInboundTrustWorkbenchTest, MailQueueConsumerTest, AutoMailReplyServiceTest, BatchAutoMailReplyServiceTest, MailReplyDraftServiceTest, MailboxReadAccessProjectionTest, ReplyIdentityServiceTest, BounceCollectionSchedulerTest; these do not create a new Spring dependency requirement.

**Production write receipts**

- Pattern: `inboundMailProcessingRepository\.(save|reopenManualResolved)|inboundMailProcessingRepository`; paths: authorized `AutoMailReplyService.kt`, `PendingMailOperationService.kt`.
- Auto actual saves: `:246` reclassification; `:1473` confirmManualReviewWithBody new row; `:1529` confirmProcessed new row.
- Pending actual writes: `:1644` completion save; `:1715` reopenManualResolved.
- Pattern: `inboundMailProcessingRepository\.(save|reopenManualResolved)|@Transactional|class AutoMailReplyService|private val .*|fun processSingle`; paths: authorized Auto, Pending, Unmatched.
- Unmatched actual saves: `:193` bind, `:238` completion; both already public `@Transactional` (:151,232).
- Read exact field sets at Auto :246–253,1473–1494,1529–1550; Unmatched :193–202,238–245; Pending :1644–1653,1715–1728; repository :69–83.
- Auto transaction owns group lock at :164 and whole process core; existing matched-contact lock is :303 (`inboundAutoResponseDetector.lockContact(it)`). Detector implementation :28–31 requires active transaction and checks the returned contact row. New owner reads must remain inside this same actual transaction and use current grant reads (not an older repeatable-read snapshot).
- Existing repository reopen keeps the exact PROCESSED/MANUAL_RESOLVED/MANUAL_RESOLVED predicate. Pending/Unmatched attention counts are expert-wide `countByExpertContactIdAndProcessStatus`, not owner-specific; preserve them.

**Existing write-behavior fixture receipts**

- Pattern: `inboundMailProcessingRepository\.save|processingRepository\.save|reopenManualResolved|AutoMailReplyService\(|UnmatchedInboundMailService\(|PendingMailOperationService\(|ExpertMailReadAccessService\(`; path: `src/test`.
- Additional real repository reopen callers: `InboundMailProcessingRepositoryTest.kt:225,248,261,274`; `ExpertContactLocationServiceIT.kt:446`. Both use migrated MySQL, not an inline owner-less schema.
- Pattern: `\.save\(|reclassif|markResolved|bindToContact|cancelResolved`; paths: existing Auto/Pending/PendingTrust/Unmatched service tests.
- Pattern: `times\(2\)\).*save|\.save\(captor\.capture\(\)\)|thenThrow\(IllegalStateException\("reclassification|reopenManualResolved|\.save\(Mockito.any\(InboundMailProcessing::class.java\)\)`; paths: existing Auto/Unmatched/PendingTrust tests.
- Relevant exact output:
  - Auto :1056 `Mockito.verify(inboundMailProcessingRepository, Mockito.times(2)).save(captor.capture())`
  - Auto :1091 same two-save pin.
  - Auto :1191 `Mockito.when(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))`, :1192 `.thenReturn(created)`, :1193 `.thenThrow(IllegalStateException("reclassification write failed"))`.
  - Unmatched :206,:316,:341,:365 whole-save stubs; :324 `Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))`.
  - PendingTrust :2338,:2387,:2436,:2469,:2497 `inboundMailProcessingRepository.reopenManualResolved(...)` stubs (success and zero-row failure fixtures).
- Pattern: `\.(processSingle|processByUids|receiveAndAutoReply|bindToContact|markResolved|cancelResolved)\(`; path: `src/test`.
- Real pipeline invocation groups: AutoMailReplyServiceTest; ExpertInboundNotificationRepositoryIT (:158,308,341,342,361,364,394,399). Real bind/markResolved invocations: UnmatchedInboundMailServiceTest. Real cancelResolved invocations: PendingMailOperationServiceTrustWorkbenchTest. Controller/queue/task matches otherwise mock the services, including ExpertScopedOperationsIT and UnmatchedInboundMarkResolvedIdentityTest.
- Pattern: `(?i)CREATE TABLE(?: IF NOT EXISTS)?\s+[\x60"]?inbound_mail_processing|(?i)pending_owner_username`; path: `src`.
- Output: production V5 migration :1; ExpertMailPermissionGuardTest.kt :72 inline table contains only id/contact metadata. Guard does not read the new owner column; it is not a changed writer. No owner implementation existed at this audit.

**Line-number guard audit**

- Pattern: `AutoMailReplyService|PendingMailOperationService|UnmatchedInboundMailService|InboundMailProcessingRepository|EXCLUDED_NOISE_SITES`; path: `src/test/kotlin/com/weibo/talentintroduction/campaign`.
- Guard output only EXCLUDED_NOISE_SITES declaration/use at OperatorStatusWriteSeamGuardTest.kt :44,178,183,189,199; service/repository names occur in unrelated ExpertContactManagementServiceTest.kt repository mock only.
- Read guard :44–120: its pinned exclusions concern UnmatchedInboundMailController, MailboxService, ExpertContactManagementController, ExpertIndexController, DiscoveryPersonQueryService, ExpertSearchService, ExpertContactRepository, BatchRecipientReadRepository and MailRecordRepository. None is an authorized child05 product mutation. No lineguard amendment requested.
- Migration slot `functions.glob` path `src/main/resources/db/migration/V17*` returns V170 and V17 only; V171 remains available. Existing migrations untouched.

### Task status / evidence limits

| Requirement | Status | Evidence |
|---|---|---|
| Plan/worktree/predecessor identity | IMPLEMENTED preflight | helpers below, product ancestor exit 0 |
| I-5 migration/domain/two new sinks | PENDING | no source edits before prerequisite decision |
| I-6 downgrade/revoke/reopen transaction | PENDING | required strict contact-first fixtures identified |
| I-12 four narrow writes/attention | CONFLICT under current Authorized Files | incompatible old-save fixtures outside whitelist |
| I-1/I-13 stable security/admin/background | PENDING implementation regression | no changed capability or runtime claim |
| Permanent MySQL/thread regressions | PENDING | tests not created/run |
| Actual login/AuthWebConfig/API/JDBC runtime | NOT_RUN | no product state yet; later 06 personal queue UI/SQL not claimed |
| Final required commands/zero skips | NOT_RUN | held before mutation; not PASS |
| Product-only commit | NOT_CREATED | prerequisite required first |

### Commands actually exercised

All shell commands ran with cwd exactly TARGET_WORKTREE:

1. `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master && git status --short` — exit 0; matching identity, target branch/root/git-dir and initial dirty paths recorded above; output artifact `artifact://2471`.
2. `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master && git merge-base --is-ancestor a2d4b0e68691d115cff2a6ad3bcc115a06b28d27 HEAD && git log -1 --format=%H:%s a2d4b0e68691d115cff2a6ad3bcc115a06b28d27` — exit 0; identity unchanged and predecessor reachable; output artifact `artifact://2472`.

No builds, tests, linters, formatters, runtime smoke, deployment or external effects ran. No target skip/pass count exists. Historical predecessor verification is baseline only.

### Handoff

Controller must record the bounded existing-fixture amendment and publish the new child identity/brief before any source mutation. Original nine files remain pending, not implemented or verified. Retain this epoch as failed preflight evidence; do not transfer readiness to a new identity. Next executor must reread the new exact bytes and perform fresh final commands/runtime before READY_FOR_VERIFICATION. No verify-p/review-p/repair-p run by this child.

## Epoch 2 — READY_FOR_VERIFICATION

This is execution evidence, **not PASS** and not independent review.

- Executor: `ExpertAccess05Resume`; execution epoch NEW for the amended identity (prior epoch retained above).
- Approval basis: current epoch2 brief and standing human authorization recorded there.
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-05-pending-owner.md`.
- Plan SHA-256 at entry and pre-commit: `70976d0211d8e5debd09c5a5d152d5a26a15704dd761af58f3113796664d3ca9`.
- Execution ID: canonical plan path above `@70976d0211d8e5debd09c5a5d152d5a26a15704dd761af58f3113796664d3ca9`.
- Approved plan/master identity: `commit:6b950d7e0ce1e460e197645a6bcaca485ae83088`. Read complete latest brief, child plan and master (including remaining master lines301–453), and predecessor epoch1 receipts before source.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`.
- Branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Pre-execution product/code SHA: `a2d4b0e68691d115cff2a6ad3bcc115a06b28d27`; pre-execution HEAD: `6b950d7e0ce1e460e197645a6bcaca485ae83088`.
- Post-execution product/code SHA and Evidence HEAD at executor handoff: `e4c5d83a580228a92f1dd2c427538829651a40a6`.
- Product-only commit: `feat(fast-p): implement 05` — exactly12 approved product/test files; no plans, reports or scaffolds staged.
- Implementation boundary: predecessor product `a2d4b0e68691d115cff2a6ad3bcc115a06b28d27..e4c5d83a580228a92f1dd2c427538829651a40a6` (intervening controller plan commits are not product changes).
- Initial dirty files: child05 brief/execution and master ledger; preserved. Only this section is appended by this executor. Controller owns subsequent evidence commits.

### Task status and changed files

| Requirement | Status | Product/test evidence |
|---|---|---|
| I-5 historical default / physical-key preservation | IMPLEMENTED | V171 adds `pending_owner_username VARCHAR(64) NOT NULL DEFAULT 'admin'` and the single approved composite index; domain maps defaultadmin. Actual V170→171 upgrade proves old rows admin despite FULL and divergent dates, NOT NULL/default and exact index columns. No applied migration changed. |
| I-5 two real creation sinks / current authorization / dedup | IMPLEMENTED | AutoMailReplyService populates owner in both constructors. Existing real processSingle transaction and existing detector contact lock precede all matched writes; allocation uses `FOR UPDATE` current grant read, not an old repeatable-read snapshot. No duplicate contact-lock round trip added. Unmatched remains admin. Both real pipeline sink branches, no-contact, duplicate UID, grant-before/after receipt and downgrade/revoke lock races exercise JDBC. |
| I-6 grant/revoke transaction | IMPLEMENTED | ExpertMailReadAccessService locks contact, current-reads grant, then changes grant and reclaims only MANUAL_REVIEW user1 rows in the same transaction. Legacy grant keeps FULL; upgrade/repeated FULL do not transfer admin history. Real trigger failure proves grant+queue rollback for downgrade and revoke. |
| I-6 completed reopen | IMPLEMENTED | Pending cancelResolved locks contact before processing UPDATE; existing exact resolved triplet/zero-row409/missing404/operator audit behavior retained. Conditional SQL falls back to admin only when user1 no longer FULL; clears original resolved fields. |
| I-12 stale entity writes | IMPLEMENTED | Repository has explicit reclassify/bind/resolve field sets, no owner SET. Auto reclassification, Unmatched binding/completion, Pending completion use them; Unmatched rereads result. Contact-first completion/binding/reopen serialize with access mutation. Stale consumer snapshots plus real downgrade prove reclaimed owner remains admin. Existing reason/body/physical source/resolution semantics asserted. |
| I-12 expert-wide attention | IMPLEMENTED | Existing expert-wide remaining-count queries remain owner-independent. Real completion consumers preserve true while another admin-owned pending row remains; reopen restores attention. |
| I-1/I-13 stable guards/background | IMPLEMENTED within child boundary | No route policy, permission guard, frontend, config or automatic reply strategy changes. Existing AuthFlow/ExpertScoped/access regressions run with real JDBC/auth configuration. Runtime genuine admin/user1 login; FULL resolution session-audited user1 despite forged body, READ/NONE resolution403, global sender-account403, READ messages200. |

Exact authorized changed paths:

1. `src/main/resources/db/migration/V171__add_inbound_pending_owner.sql` — schema/default/index.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt` — mapped business owner.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` — current grant/contact locks, narrow writes, reopen fallback.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` — two sink allocation and narrow reclassification.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — contact-first completion/reopen.
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` — contact-first binding/completion and reread.
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt` — transactional access mutation/reclaim.
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt` — permanent real-MySQL sink/concurrency/stale-consumer/rollback/upgrade tests.
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` — new latest migration expectations and idempotence/rollback/ownership lifecycle.
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` — stateful persistence fixture for actual reclassification result/failure, not replacement call-count pins.
11. `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` — narrow writer/reread fixtures retaining binding/alias/document/attention behavior.
12. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` — strict non-null linked-contact lock fixture; original behavioral assertions retained.

### Fresh consumer audit

No LSP tool mounted. Repeated exact constructor/Spring audit before edits with functions.grep:

```text
pattern: \b(AutoMailReplyService|PendingMailOperationService|UnmatchedInboundMailService|ExpertMailReadAccessService)\s*\(|(AutoMailReplyService|PendingMailOperationService|UnmatchedInboundMailService|ExpertMailReadAccessService)::class
path: TARGET_WORKTREE/src
```

Paged the complete result (`skip=20`). Same declarations/direct constructors/Spring imports as epoch1 above; no constructor dependency changed, no additional fixture needed. Read original fields at Auto246–253/1473–1494/1529–1550; Unmatched193–202/238–245; Pending1644–1653/1715–1728; repository69–83, and entire access service before mutation. Rechecked actual internal completion callers with pattern `markResolved\(|resolvePending\(|\.reopenManualResolved\(` over `src/main`: output is controller→Pending, Pending resolve/reopen, Unmatched resolve, repository declaration; no extra product writer discovered. Original lineguard receipt above remains applicable: none of its pinned source files changed; fresh OperatorStatusWriteSeamGuardTest passes.

Fresh direct-consumer search over Pending test classes used `\.(markResolved|cancelResolved|bindToContact)\(|reopenManualResolved|lockContact|lockReceivingGroup`: real existing reopen consumers reside in authorized TrustWorkbench fixture. Existing unit test save-count/second-entity assumptions are retired only at changed reclassification/bind sites. Failure injection targets actual narrow reclassification writer; no new forwarding/count pin. SQL safety is proven through actual JDBC and consumer outcomes rather than mock invocation counts.

### Commands, environment, results and failures

All repository commands cwd exactly TARGET_WORKTREE. No source edits while a build/test command ran. All final commands below executed sequentially after final source state.

Common final test environment, expanded exactly in each invocation:

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32771/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=owner-test
```

| Exact command following common environment | Result |
|---|---|
| `mvn test -Dtest=InboundPendingOwnerIT,ExpertMailReadAccessIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` initial implementation | exit1;23 tests,0 failures,2 errors,0 skips. New test fixture attempted Mockito spy of final Spring JDK proxy and selected synthetic default constructor. Full failures in surefire and `artifact://2480` (snapshot2478/2481). Fixed only authorized new IT: interface mock delegates to real repository and selects non-synthetic constructor. No production workaround. |
| Same owner gate after fixture correction | exit0;23/0/0/0,04:33; `artifact://2483`. Historical for final state: subsequently strengthened true NONE→FULL concurrency orders and removed redundant already-held contact-lock query. |
| Same owner gate, final state | exit0;**23 tests,0 failures,0 errors,0 skipped**,04:26; `artifact://2485`. ExpertMailReadAccessIT16; InboundPendingOwnerIT7. |
| `mvn test -Dtest=FlywayMigrationIntegrationTest,AutoMailReplyServiceTest,UnmatchedInboundMailServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,InboundMailProcessingRepositoryTest,ExpertInboundNotificationRepositoryIT,ExpertScopedOperationsIT,AuthFlowIntegrationTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` final state | exit0;**310 tests,0 failures,0 errors,0 skipped**,18:15; `artifact://2486` (large log partially elided by harness). Exact per-suite XML parsed freshly below; no counts inferred from missing log. Both opt-ins enabled, including AuthFlow's migrationIt requirement. |
| Maven-bound `node-test`, `node-check-app`, `node-check-task-modal-runtime` in final commands | exit0;**1961 JS tests,352 suites,1961 pass,0 fail/cancel/skip/todo**; syntax checks complete. Receipts `artifact://2485` lines4010–4026 and final second gate2486. |
| `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-05-epoch2-runtime/classpath` | exit0; fresh runtime dependency classpath. |
| `git diff --check` | exit0 before staging/commit; `artifact://2494`. |
| Identity helpers before source and pre-commit; worktree helper again between staging and commit | exit0, canonical plan hash/root/branch/git-dir unchanged; `artifact://2475`,2494,2495. |
| Exact twelve-path `git add …`; `git commit -m 'feat(fast-p): implement 05'`; `git rev-parse HEAD`; `git merge-base --is-ancestor HEAD fast/expert-access-master` | exit0; commit `e4c5d83a580228a92f1dd2c427538829651a40a6`,12 files,542 insertions/70 deletions; `artifact://2495`. No evidence/plan staged. |

Final second gate per-suite XML counts (tests/failures/errors/skips), parsed from `target/surefire-reports/TEST-*.xml` after that command via ElementTree, receipt `artifact://2492` and `/tmp/expert-access-05-epoch2-runtime/final-test-counts.json`:

```text
FlywayMigrationIntegrationTest                  46/0/0/0
OperatorStatusWriteSeamGuardTest                 1/0/0/0
PendingMailOperationServiceTrustWorkbenchTest   60/0/0/0
UnmatchedInboundMailServiceTest                 15/0/0/0
AutoMailReplyServiceTest                       102/0/0/0
ExpertScopedOperationsIT                       11/0/0/0
PendingMailOperationServiceTest                 42/0/0/0
InboundMailProcessingRepositoryTest              9/0/0/0
ExpertInboundNotificationRepositoryIT           14/0/0/0
AuthFlowIntegrationTest                         10/0/0/0
TOTAL                                         310/0/0/0
```

Disposable MySQL setup commands (all exit0):

```bash
docker -H unix:///Users/lukai/.orbstack/run/docker.sock run --name expert-access-05-epoch2-mysql -e MYSQL_ROOT_PASSWORD=owner-test -e MYSQL_DATABASE=talent_introduction -p 127.0.0.1::3306 -d mysql:8.0.36 --log-bin-trust-function-creators=1
docker -H unix:///Users/lukai/.orbstack/run/docker.sock port expert-access-05-epoch2-mysql 3306
docker -H unix:///Users/lukai/.orbstack/run/docker.sock exec expert-access-05-epoch2-mysql mysql -uroot -powner-test -e 'CREATE DATABASE owner_runtime CHARACTER SET utf8mb4'
```

Container ID `d8c35ce307f1fa87955b1fb7e42b549702da22e2ffb3fd5374004e27bc4e410e`, bound loopback32771. Existing self-contained Testcontainers gates use their own disposable containers.

### Actual changed-path runtime, beyond tests

A temporary Java harness outside the repository started **production TalentIntroductionApplication** with actual Spring beans/JDBC/AuthService/AuthWebConfig and real HTTP. Classpath was freshly generated dependencies plus `target/classes` and the temporary harness directory; **no target/test-classes or expanded WAR**. It started only loopback ES and a real loopback SMTP-connection collector; no production service. Runtime used separate `owner_runtime` schema and temporary attachment base path. Auth users were provisioned through real PasswordEncoder hashes in that isolated schema; login used genuine cookies, not synthetic sessions.

Exact compiler operation via subprocess argument array (exit0):

```text
/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/javac
  -cp TARGET_WORKTREE/target/classes:<fresh dependency:build-classpath bytes>
  /tmp/expert-access-05-epoch2-runtime/OwnerRuntime.java
```

Exact runtime command (exit0,19.15s, `artifact://2490`):

```bash
env SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32771/owner_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=owner-test MAIL_ATTACHMENT_STORAGE_BASE_PATH=/tmp/expert-access-05-epoch2-runtime/attachments /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java @/tmp/expert-access-05-epoch2-runtime/java.args
```

Generated argfile contained `-cp`, `<temporary harness directory>:TARGET_WORKTREE/target/classes:<fresh dependency bytes>`, then `OwnerRuntime`. Runtime sets explicit system properties for dynamic ES loopback URL, server.port0, auth enabled and discovery/scheduling/queue/LLM disabled; does not mutate application.yml.

Observed actual runtime:

- Application pid81883, HTTP54574, ES fixture54482; genuine admin/user1 login200.
- Real AutoMailReplyService.processSingle created2 old admin pending rows via both sink branches; FULL API grant200, then3 new user1 rows via both branches. Reprocessing UID3 left exactly5 rows and unchanged owner. Unmatched new row remained admin.
- user1 mark-resolved200 despite body claiming admin; DB resolved_by=user1. DowngradeREAD200 moved only unfinished rows to admin, completed row remained user1. user1 reopen403; admin reopen200 and SQL owneradmin.
- READ messages200. FULL then legacy no-body PUT remained FULL. Existing admin history unchanged; new receiptuser1. DELETE authorization200 reclaimed it; subsequent user1 completion403. Global sender-account endpoint403.
- Real global needsManualAttention remained true while pending remained. Real SMTP collector observed0 connections; DB OUTBOUND mail-record count0.
- Terminal receipt: `OWNER_RUNTIME_OK both_sinks=true duplicate=true legacy_admin=true downgrade=true revoke=true reopen=true session_audit=true no_smtp=true downstream_personal_query=DEFERRED_TO_06`.
- Runtime ES fixture is deliberately not a real Elasticsearch persistence implementation. Startup mapping patches logged local fixture HEAD/connection warnings; ownership/auth/JDBC path and final assertions completed. No ES feature acceptance is claimed.

Owned cleanup (exit0): runtime closed production context/Tomcat/Hikari/ES/SMTP in finally; `docker -H unix:///Users/lukai/.orbstack/run/docker.sock rm -f expert-access-05-epoch2-mysql && rm /tmp/expert-access-05-epoch2-runtime/OwnerRuntime.java /tmp/expert-access-05-epoch2-runtime/OwnerRuntime.class /tmp/expert-access-05-epoch2-runtime/classpath /tmp/expert-access-05-epoch2-runtime/java.args`. No scaffolds remain in product. Immutable log receipts and exact count JSON retained only as evidence.

### Downstream interface and limits

- 06 consumes `pending_owner_username`, admin/user1 only from new allocation/fallback paths; physical `mailbox_owner_code`, V167 unique key and sender-account meaning unchanged.
- No personal-query/queue/count/UI capability activated here. Admin/user1 pending counts and final browser acceptance A-3/A-4/A-13/A-15 involving future owner-filtered UI remain for06/08, per the child plan's staged API-equivalent rule; this child proves persistence and real service/API transitions, not future query behavior.
- No frontend files changed; no desktop/mobile/browser screenshot claim. Existing frontend regression command passed via Maven.
- Master-wide unrestricted `mvn test`/final all-child artifact checks and independent review are controller gates; only the exact required child gates plus listed extra related regressions are claimed here. No verify-p/review-p/repair-p run.
- No unrelated hygiene, second convention, nullable/default security shim, route-prefix allow, deployment, merge, push, rewrite or other worktree mutation.

### Freshness and handoff

- Plan identity rechecked unchanged before product commit; worktree identity/root/branch/git-dir matched before staging and commit.
- Reported product is HEAD and target-branch ancestor, proven by post-commit commands above.
- Required commands fresh on final source: YES. Historical evidence used only as baseline: YES.
- Remaining blocker: none identified within approved child05 scope.
- Outcome: **READY_FOR_VERIFICATION**, not PASS. Controller should run its fresh independent light verifier, then continue ordered child06 from this product SHA.

Final handoff recheck after report append (`artifact://2497`, exit0):

```bash
python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-05-pending-owner.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master && git merge-base --is-ancestor e4c5d83a580228a92f1dd2c427538829651a40a6 fast/expert-access-master && git status --short
```

Plan SHA remains `70976d0211d8e5debd09c5a5d152d5a26a15704dd761af58f3113796664d3ca9`; HEAD remains product `e4c5d83a580228a92f1dd2c427538829651a40a6`. Index/product working tree clean; only expected controller brief/ledger and appended execution report dirty. No evidence commit by executor.


## Controller terminal checkpoint — 2026-10-30T13:19:44.212Z
- Terminal independent verdict: LIGHT_PASS; exact report retained in verify-log.md.
- Final child code head: e4c5d83a580228a92f1dd2c427538829651a40a6
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
