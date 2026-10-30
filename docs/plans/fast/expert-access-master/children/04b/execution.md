# Child 04b — execution

## Epoch 1 — PLAN_CONFLICT, prerequisite audit before product mutation

- Executor: ExpertAccess04bImpl; invocation approval: standing continuous user authorization, exact epoch1 brief.
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md`.
- Execution ID: canonical plan path above + `@3b01f10a42d1544992a23ff6166ccab9ba3638d504c75f67a3a8f26a3fa4b463`; NEW.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Pre-execution product SHA: `020bda37127f92187589e9d8f10e3bb3cbccb187`; initial/current evidence HEAD: `264b046fc405435dc820b0f3122d2b24fa89be75`.
- Initial `git status --short` (exit 0): modified controller-owned `docs/plans/fast/expert-access-master/children/04b/brief.md` and `docs/plans/fast/expert-access-master/ledger.md` only. `git log -3 --oneline` (exit 0): `264b046 docs(fast-p): checkpoint 04a`; `fb17910 docs(fast-p): record 04a light verification`; `020bda3 feat(fast-p): implement 04a`. Preserved all.
- Read complete exact child/master, epoch1 brief and execute-p before source. No LSP tool exposed; used exact constructor/type/import/direct-call/lineguard searches.

### Proven prerequisite: current seven-file scope cannot preserve true consumers

Use existing non-null `ExpertMailPermissionGuard`, not a second permission-query implementation, nullable/default security shim, or prefix admission. `MailReplyDraftService` currently has no permission dependency (constructor lines 17–22), and its owner/target/id/send paths do not enforce current FULL. Injecting the existing guard requires direct-constructor and Spring fixture updates outside the seven files. Calendar create's body target needs the same non-null guard; interceptor already resolves calendar query/event targets. Unmatched detail must join the existing fixed PROCESSING resolver before `getDetail`/candidate lookup; its central registry file is not authorized. This is not unavailable information or a speculative test failure: signatures, imports and fixed routing map directly prove the prerequisite.

### Exact search receipts

All paths below are relative to target root. Searches were performed with the specialized grep tool; equivalent reproducible commands are printed to make the patterns and outputs explicit. No builds/tests/lint/formatters ran during this audit.

1. `grep -R -n -E 'MailReplyDraftService\(|MeetingCalendarController\(|UnmatchedInboundMailController\(|MailReviewDraftService\(|MailReplyDraftRepository\(|import .*\.(MailReplyDraftService|MeetingCalendarController|UnmatchedInboundMailController|MailReviewDraftService|MailReplyDraftRepository)' src`:
   - Product constructors: `campaign/controller/MeetingCalendarController.kt:23`; `mail/service/MailReplyDraftService.kt:17`; `mail/service/MailReviewDraftService.kt:19`; `mail/repository/MailReplyDraftRepository.kt:16`; `mail/controller/UnmatchedInboundMailController.kt:70` (all under src/main/kotlin/com/weibo/talentintroduction).
   - `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt:86,179`: direct MailReplyDraftService constructors, respectively real attachment metadata and proxied transaction/runtime fixture.
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt:19`: direct five-argument constructor.
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:107-108`: direct five-argument constructor.
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt:117`: direct `MeetingCalendarController(calendar)` in manual MVC context.
   - Existing unmatched direct constructors: `mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:97,797,1646`, `UnmatchedInboundMarkResolvedIdentityTest.kt:57`, `UnmatchedInboundTrustWorkbenchTest.kt:53`. Avoid changing that constructor: register detail's fixed PROCESSING target in the existing guard instead.
   - Review direct constructor: `mail/repository/MailReviewQueryIT.kt:67-68`; no new review-service dependency is needed, so this consumer stays unchanged.

2. `grep -R -n -E 'MailReplyDraftService|MeetingCalendarController|UnmatchedInboundMailController|MailReviewDraftService|mail\.service\.\*|campaign\.controller\.\*|mail\.controller\.\*' src/test`:
   - `campaign/controller/MeetingCalendarControllerTest.kt:52-53`: WebMvcTest imports only mapper config; new controller guard dependency must be supplied. Same file `:254-265` real-MySQL MVC class already has MockBean resourceGuard.
   - `mail/service/MailReplyDraftSendIntegrationTest.kt:64-65`: imports real MailReplyDraftService and MeetingCalendarSendIntegrationTestSupport.
   - `mail/service/MailReviewSendIT.kt:62-65`: imports real MailReplyDraftService and the same support.
   - `mail/service/MeetingCalendarSendIntegrationTest.kt:82-87`: imports real MailReplyDraftService and the same support.
   - Shared support is declared at `MeetingCalendarSendIntegrationTest.kt:695-717`; it currently supplies calendar, audit and outbound attachments only. Add real ExpertMailPermissionGuard + ExpertMailReadAccessService imports in this existing support, rather than changing each importing IT or inventing new fixture classes. Both dependencies use existing NamedParameterJdbcTemplate.
   - `mail/service/MailReviewDraftIT.kt:43-44`: imports existing review service; constructor unchanged.
   - `auth/config/AuthInterceptorTest.kt:154` and `mail/controller/MailReplyDraftControllerTest.kt:15`: mock draft service, so no constructor amendment needed there.

3. `grep -R -n -E '\.(getTarget|validateInboundSend|validateConversationSend|completedBinding|lockForClaim|lockForFinalize|closeSent)\(' src`:
   - `mail/controller/MailReplyDraftController.kt:17`: getTarget(owner,target).
   - `mail/service/PendingMailOperationService.kt:207,329,438,466`: completedBinding and inbound/conversation validation; existing method signatures can remain.
   - `mail/service/ManualReplySendAttemptService.kt:307,442,452,539,556`: claim/finalize/close callbacks. Preserve already-authorized in-flight SMTP finalization semantics; do not introduce a post-SMTP revocation failure.
   - `mail/service/MailReplyDraftServiceTest.kt:35,36,85`: target-shape and deleted-admin-target behavior. Admin historical behavior must remain, with new strict checks only for restricted username.

4. `grep -n -E 'draft|calendar|Unmatched|getUnmatchedDetail|summaries|create|events' src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`:
   - `AuthInterceptorTest.kt:428` pins drafts-list global 403, `:449` pins no service interaction; this interim denial must retire when guarded SQL list opens, not be re-pinned to a new incidental response. Preserve NONE/READ/direct-target safety through real service/SQL behavioral tests.
   - `AuthInterceptorTest.kt:525-584` pins the exact allowed-handler set, currently lacks calendar create, unmatched detail and personal draft methods. Must update only these completed capabilities; keep all other exact deny defaults.
   - `ExpertMailPermissionGuardTest.kt:163-166` already checks calendar missing query / READ / mixed summary ids rejected before calendar service. Retain those assertions while wiring controller's non-null guard.

5. `grep -n -E 'EXCLUDED_NOISE_SITES|UnmatchedInboundMailController.kt|operatorStatus' src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`:
   - `:53`: `NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 228, "operatorStatus = request.operatorStatus")`.
   - `:69`: `NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 1155, "operatorStatus = operatorStatus")`.
   - `:183-189` requires exact line/context matches. Unmatched detail/session changes shift those existing pins. Authorize line numbers only; do not change snippets or weaken guard.

6. `grep -R -n -E 'getUnmatchedDetail\(|markResolved\(|cancelResolved\(' src`:
   - `UnmatchedInboundMailController.kt:134`: getUnmatchedDetail currently no servlet request, reads record then always suggestCandidates at :136.
   - Same file `:174-184`: markResolved already derives resolvedBy/operatorName from session.
   - Same file `:195-202`: cancelResolved currently accepts body operatorName and must switch to session.
   - Direct tests call PendingMailOperationService.cancelResolved, not controller cancelResolved; keep service signature unchanged.

Source confirmation: `ExpertMailPermissionGuard.kt:40-44` currently registers unmatched send/mark/cancel only, explicitly defers detail; :109 resolves PROCESSING ownership before service; :139-152 already checks calendar events/summaries/event ids. Reuse these contracts. `MailReplyDraftRepository.kt:52-72` currently filters owner/state/scope only; both count/page and summary count need SQL FULL EXISTS for user1, not post-pagination filtering. `MailReviewDraftService.kt:19-25` can retain constructor; state rules remain separate from FULL.

### Bounded amendment requested (controller owns plan/brief/ledger)

Retain the original seven files and add these nine existing files (16 total; no new classes, stores or product capability beyond approved 04b):

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` — register exact unmatched-detail GET as existing PROCESSING target; no prefix or global widening.
2. `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt` — non-null guard fixture for plain MVC slice; preserve admin/CAS/date behavior and existing real-MySQL slice.
3. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt` — direct calendar constructor wiring; preserve target/zero-effects matrix.
4. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt` — direct service constructors get non-null real guard/access on existing JDBC; preserve owner/CAS/admin deleted-contact/runtime behavior.
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt` — direct service constructor gets non-null guard; preserve existing behavior and add restricted behavioral checks where appropriate.
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` — direct service constructor wiring only, existing assertions retained.
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt` — existing shared support imports real guard/access for its three real draft-service consumers; retain sending/finalization tests.
8. `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` — exact completed method matrix and retire interim personal-drafts global-denial/no-interaction pins; preserve stable NONE/READ/cross-resource/admin/default-deny protections.
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` — shifted unmatched-controller noise-site line numbers only, original contexts unchanged.

Execute original 04b required commands plus gates of these added fixtures after final implementation, sequentially/fresh. Include `MeetingCalendarControllerMysqlTest`, `MeetingCalendarServiceMysqlTest`, `ExpertMailPermissionGuardTest`, `MailReplyDraftRepositoryIT`, `MailReplyDraftSendIntegrationTest`, `MailReviewSendIT`, `MeetingCalendarSendIntegrationTest`, `MailReplyDraftServiceTest`, `PendingMailOperationServiceTest`, `AuthInterceptorTest`, `OperatorStatusWriteSeamGuardTest`, using explicit `-DmysqlIt=true -DmigrationIt=true` where applicable and zero target skips. Final master gates remain unchanged.

### Commands and freshness

- `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md`: exit 0, canonical identity/hash above, run before source and again before handoff; unchanged.
- `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`: exit 0, initial identity above.
- Final helper adds `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`: exit 0, unchanged HEAD/worktree/branch.
- Required machine commands: NOT_RUN (no product edits; authorization prerequisite).
- Actual runtime: NOT_RUN (same prerequisite); no SMTP/ES/files/browser claims.
- Post-execution product SHA: unchanged `020bda37127f92187589e9d8f10e3bb3cbccb187`; product commit: NONE; tests/skips: no execution, not PASS.
- Changed by this invocation: this append-only execution artifact only. No plans/brief/ledger/product/tests edited.
- Outcome: PLAN_CONFLICT — exact AuthorizedFiles excludes proven guard/consumer/contract-test prerequisites. Hold product read-only pending controller amendment and new immutable plan identity. This epoch is not READY_FOR_VERIFICATION, not PASS, and does not consume an independent verification repair round.

## Epoch 2 — Execution Result: READY_FOR_VERIFICATION

This is execution evidence, not an independent PASS.

### Immutable identity and provenance

- Executor: `ExpertAccess04bResume`; NEW execution identity, resuming the authorized child after Epoch1's bounded amendment. Approval: current Epoch2 brief and standing human authorization recorded there.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md`.
- Approved identity commit/master: `e9f93fb260c6b25518ccbbf579c420c8aa6cb406`.
- Plan SHA-256: `17b4fbca276961816c9985e3682372a775bc07a5bd022ba2187958d77378a8fe`; execution ID is canonical plan path above + `@17b4fbca276961816c9985e3682372a775bc07a5bd022ba2187958d77378a8fe`.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch: `fast/expert-access-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`; common directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- WORKTREE_ID: target root + `@fast/expert-access-master@` + Git directory above.
- Pre-execution code/product SHA: `020bda37127f92187589e9d8f10e3bb3cbccb187`; initial evidence HEAD: `e9f93fb260c6b25518ccbbf579c420c8aa6cb406`.
- Post-execution code SHA / current evidence HEAD: `a2d4b0e68691d115cff2a6ad3bcc115a06b28d27`.
- Product-only commit: `a2d4b0e68691d115cff2a6ad3bcc115a06b28d27 feat(fast-p): implement 04b`; 15 product/test files, no plans/reports/artifacts staged. Implementation boundary: prior product base above through this product commit.
- Initial `git status --short`: controller-owned brief/execution/ledger were already dirty; preserved them. Final status has only those same documentation paths dirty, index empty, product clean. Controller owns subsequent evidence commit.
- Read execute-p and complete current child/master before source; no LSP mounted. Re-ran constructor/type/import/direct-call/lineguard receipts from Epoch1 against current sources before edits. No other worktree, subagent, push, merge, rewrite or deployment used.

### Task status / binding downstream interfaces

| Requirement | Status | Implementation and behavioral evidence |
|---|---|---|
| 04b-1 / I-2/I-4/I-13 calendar | IMPLEMENTED | Existing exact guard resolves query/event DB owners; create DTO checks session FULL with non-null same guard before service. Repeated summary query values checked individually. Real MVC/JDBC test covers READ/NONE/cross contact, missing/mixed ids, UTC, readback, changed write then stale CAS409, cancel, downgrade; inherited admin/calendar MySQL tests retained. |
| 04b-2 / I-4/I-8 personal drafts | IMPLEMENTED | Non-null shared guard in service checks current FULL + real target before target/id/save/discard/completed-send/active-send/claim. Owner filtering remains. List/count/summaries use the same SQL FULL EXISTS before pagination, including real inbound target/contact/account equality. Admin and historical deleted-target behavior unchanged. Finalize/close callbacks deliberately do not add post-SMTP permission checks. |
| 04b-3 unmatched | IMPLEMENTED | Exact PROCESSING detail GET activated only with candidate redaction; user1 never calls suggestCandidates, candidates=[], actual record/contact/logs only. cancel-resolved now derives operator from session, matching existing mark-resolved. Real guard/JDBC plus controller tests prove denied requests never reach detail/log/pending collaborators and forged operator is ignored. Runtime proves real audit. |
| 04b-4 / I-11 review locks | IMPLEMENTED (existing product intentionally unchanged) | MailReviewDraftService already independently requires READ ownership, state and CAS, never promotes review state from FULL; APPROVED edits/deletes/comments reject409, prepareSend remains admin-only. New regression creates DRAFT/SUBMITTED/RETURNED/APPROVED while READ, upgrades FULL, reads all unchanged, edits only editable states, confirms context with no SMTP, creates independent personal draft. Old MailReviewDraftIT and MailReviewSendIT gates retained. No redundant new FULL branch added. |
| 04b-5/6 admission and consumers | IMPLEMENTED | Exact calendar create, six personal-draft methods (discard DELETE), unmatched detail GET, confirmation preview POST; no prefix or class-wide allow. Existing shared support imports real guard/access. Direct constructors migrated, admin/CAS/owner assertions retained. Interim personal-drafts list global-denial pin retired; stable global/default-deny matrix retained. Only unmatched-controller noise line numbers changed in seam guard. |
| Master confirmation options/preview | IMPLEMENTED | Existing options GET guard resolves processingId and query contact equality. Preview POST guard resolves processingId to FULL; existing real service first calls requireProcessingForContact (before account/template), rejecting DTO mismatch. No unlisted controller edit needed. Permanent mismatch fixture supplies all required MeetingInput fields and asserts the real ownership-error message, not just incidental HTTP400. |

Consumer audit receipts (specialized grep, relative to target root; outputs were inspected before mutation):

- Pattern `MailReplyDraftService\(|MeetingCalendarController\(` over `src`: product declarations and direct tests `MailReplyDraftRepositoryIT.kt:86,179`, `MailReplyDraftServiceTest.kt:19`, `PendingMailOperationServiceTest.kt:107-108`, `ExpertMailPermissionGuardTest.kt:117`; same set as Epoch1.
- Pattern `MailReplyDraftService|MeetingCalendarController|MeetingCalendarSendIntegrationTestSupport|import .*ExpertMailPermissionGuard` over tests: existing shared send support consumed by MailReplyDraftSendIntegrationTest, MailReviewSendIT, MeetingCalendarSendIntegrationTest; real dependencies added in that shared support, not separate fixtures.
- Pattern `\.(getTarget|validateInboundSend|validateConversationSend|completedBinding|lockForClaim|lockForFinalize|closeSent)\(`: existing PendingMailOperationService entry validation and ManualReplySendAttemptService claim/finalize seams preserved; no send signature changed.
- Pattern `getUnmatchedDetail\(|cancelResolved\(`: no direct controller calls requiring an unlisted signature fixture; direct Pending service calls unchanged.
- Pattern `EXCLUDED_NOISE_SITES|UnmatchedInboundMailController.kt|operatorStatus`: original noise snippets now at `233` and `1160`; only the two numbers updated.
- Confirmation signatures inspected: `MeetingConfirmationController.options(processingId, contactId, senderAccountCode)` / `preview(processingId, MeetingPreviewRequest)`; `MeetingConfirmationService.preview:150-154` calls `requireProcessingForContact` before `findContact/resolveAccount/validateAndBuild`; `:262-272` reads processing DB row and rejects unequal request contact. Exact route coverage does not require a new DTO guard.

### Changed files (all within Epoch2 AuthorizedFiles)

Product:
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` — precise body/service-guarded admissions.
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt` — non-null guard and DTO/session FULL create check.
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` — scoped detail redaction and session cancel actor.
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt` — pre-pagination SQL access/real-target predicate.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` — exact detail/preview routes and complete summary query members.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` — current FULL/real target checks without post-SMTP reauthorization.

Tests:
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

Authorized `MailReviewDraftService.kt` intentionally unchanged for the reasons/evidence above. No migrations, schemas, application.yml, frontend or other product files changed.

### Fresh commands, failures and final counts

All repository commands used the exact target cwd. JDK: `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home` (actual Java11.0.33); Docker: `unix:///Users/lukai/.orbstack/run/docker.sock`. External JDBC was a newly created disposable mysql:8.0.36 container; self-contained Testcontainers suites used their own isolated containers.

Exact regression command G:

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32768/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=fixture mvn test -Dtest=ExpertScopedOperationsIT,ExpertManualProfileControllerTest,ExpertManualProfileServiceTest,MeetingCalendarControllerTest,MeetingCalendarControllerMysqlTest,MeetingCalendarServiceTest,MeetingCalendarServiceMysqlTest,MailReviewDraftIT,ExpertMailPermissionGuardTest,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewSendIT,MeetingCalendarSendIntegrationTest,MailReplyDraftServiceTest,PendingMailOperationServiceTest,AuthInterceptorTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

| Command / attempt | Exit and observed result |
|---|---|
| Initial `python .../plan_identity.py ... && python .../worktree_identity.py ...` | 127; workstation has no `python` command. Corrected to python3; no product work depended on failed command. |
| G, initial implementation state | 1; 226 tests, 0 failures, 11 errors, 0 skipped; all errors scoped IT context attempted to mock final LlmProperties. Replaced only that fixture mock with EnableConfigurationProperties; product unchanged. Full output `artifact://2388`. |
| Same env as G, `mvn test -Dtest=ExpertScopedOperationsIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` | 1; 11 tests, 1 failure, 0 errors/skips; new calendar test submitted identical update so existing no-op semantics correctly kept version, second identical update200 rather than409. Fixture changed note for a real mutation; production CAS unchanged. Output `artifact://2391`. |
| G after fixture correction | 0; 226/0/0/0; output `artifact://2395`. Historical success only: later strengthened confirmation mismatch fixture after runtime revealed missing generatedAt. |
| G final current committed state | 0; **226 tests, 0 failures, 0 errors, 0 skipped**, 08:36; output `artifact://2408`. Includes genuine mismatch-message assertion with generatedAt. |
| Maven-bound node-test and node-check-app/node-check-task-modal-runtime during final G | 0; **1961 JS tests, 352 suites, 1961 pass, 0 fail/cancel/skip/todo**; syntax checks also complete. `artifact://2408:5702-5738`. |
| `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-04b-runtime/classpath` | 0; runtime classpath freshly built from dependencies + target/classes, never expanded WAR or target/test-classes. |
| `git diff --check` | 0; before product commit, no whitespace errors. |
| python3 plan/worktree identity helpers | 0; at entry, before staging/commit and final handoff; exact hash unchanged. |
| `git commit -m 'feat(fast-p): implement 04b'` | 0; exact 15 staged source/test files; product SHA above. |
| `git merge-base --is-ancestor a2d4b0e68691d115cff2a6ad3bcc115a06b28d27 fast/expert-access-master` and `git diff --cached --quiet` | 0; product commit is current branch HEAD, index empty. |

Final JVM class counts (all failures/errors/skips zero): AuthInterceptorTest19; MailReplyDraftRepositoryIT8; PendingMailOperationServiceTest42; MailReviewDraftIT12; MailReplyDraftSendIntegrationTest27; ExpertMailPermissionGuardTest7; MeetingCalendarSendIntegrationTest7; ExpertScopedOperationsIT11; MailReplyDraftServiceTest14; MailReviewSendIT18; OperatorStatusWriteSeamGuardTest1; MeetingCalendarControllerMysqlTest3; MeetingCalendarControllerTest6; MeetingCalendarServiceTest18; MeetingCalendarServiceMysqlTest7; ExpertManualProfileControllerTest9; ExpertManualProfileServiceTest17. Exact final command output receipts: `artifact://2408`.

### Actual changed-path runtime (beyond tests)

Disposable container setup:

```bash
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker run --name expert-access-04b-resume-mysql -e MYSQL_ROOT_PASSWORD=fixture -e MYSQL_DATABASE=talent_introduction -p 127.0.0.1::3306 -d mysql:8.0.36 --max-allowed-packet=16777216 --log-bin-trust-function-creators=1
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker port expert-access-04b-resume-mysql 3306/tcp
env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker exec expert-access-04b-resume-mysql mysql -uroot -pfixture -e 'CREATE DATABASE expert04b_runtime CHARACTER SET utf8mb4'
```

All exit0; loopback port32768. Runtime used a separate schema `expert04b_runtime`, real production application/AuthService/AuthWebConfig/services/JDBC, temporary loopback SMTP collector54134 and ES fixture54135. All fixture accounts point only to loopback; no production access. Main class loaded from target/classes, excluding target/test-classes.

Exact launch (exit service-ready, pid89815; startup receipt `artifact://2398`; generated args contained `-cp target/classes:<fresh dependency classpath>` then production main class):

```bash
env SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32768/expert04b_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=fixture ES_BASE_URL=http://127.0.0.1:54135 AUTH_ENABLED=true MAIL_ATTACHMENT_STORAGE_BASE_PATH=/tmp/expert-access-04b-runtime/attachments EXPERT_DISCOVERY_ENABLED=false MAIL_QUEUE_ENABLED=false MAIL_SCHEDULING_ENABLED=false LLM_ENABLED=false LLM_AUTO_REPLY_ENABLED=false /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java @/tmp/expert-access-04b-runtime/java.args --server.address=127.0.0.1 --server.port=54204
```

Observed runtime receipts: `/tmp/expert-access-04b-runtime/receipts.json`, `summary.json`, `message.eml`, `collectors.json`, `cleanup.json`. 79 recorded actual HTTP requests (including one corrected fixture failure), using genuine login cookies for admin/user1:

- Real login200 for both identities; no synthetic session or mocked auth.
- Calendar FULL create201, UTC readback200, real update200, stale CAS409, cancel200; admin creates foreign event201 then user1 event GET/update/cancel all403; missing contact/global summaries and mixed summaries403. No calendar SMTP.
- Real unmatched FULL detail200 with candidates=[] and actual contact; READ/NONE403; global list/accounts/experts remain403.
- Created review four states under READ (DRAFT is the existing UNSUBMITTED state spelling), upgraded FULL, read all states/history without transition. APPROVED save/delete409; user send403; admin context confirmation200; approval/confirmation caused zero SMTP.
- New FULL personal inbound draft saved, listed across pages/count/summaries, restored; stale version409. Explicit user1 rich send with body operatorName=admin emitted **one actual SMTP message**, wrote one SENT attempt and one mail_record, closed personal draft SENT. Exact retry returned same Message-ID/SENT with SMTP still one. Audit row is `user1 SEND_MANUAL_RICH_REPLY`, not forged admin.
- Actual confirmation options/preview200 and correct UTC; query mismatch403; DTO mismatch400 contains real service ownership-error message; foreign processing403. Initial smoke omitted required MeetingInput.generatedAt and correctly got binding400, recorded as a fixture failure; supplied generatedAt, exercised successful preview and real mismatch. Strengthened permanent test to assert that message, then reran full G freshly.
- Rebinding draft's processing to foreign contact excludes it from SQL list and id read422. Restored relationship, downgraded READ: target/id/save/discard/new send/completed send retry/calendar all403; list and summary totals0; saved draft version remained1, SMTP unchanged. Denial block ES request delta0. No file-reading capability was opened.
- Real mark/cancel-resolved passed forged admin but persisted `user1 MARK_INBOUND_RESOLVED` and `user1 CANCEL_INBOUND_RESOLVED`.
- Logout204 then old session draft request401. SMTP total remained1. ES recorded startup9 and final15 requests (allowed successful-send effects); denied block measured independently at zero delta, not falsely claimed global zero.

Owned runtime cleanup completed: application service stopped, SMTP/ES collectors stopped, disposable MySQL container removed (`docker rm -f expert-access-04b-resume-mysql`, exit0), generated Java argfile/classpath removed. Only immutable runtime evidence retained outside repo; no throwaway script or production resource remains.

### Freshness, limits and next action

- Plan identity rechecked YES, unchanged SHA above; target worktree/branch/Git directory rechecked YES.
- Product commit reachable/current target HEAD YES; source/test worktree clean, index empty; documentation-only dirty paths preserved for controller.
- Every current child-required gate ran freshly after final test edit with mysqlIt/migrationIt flags and zero target skips. Historical command successes are not substituted for final output.
- No deviations in authorized product scope. No nullable/default security shim, prefix admission, extra abstraction/retry/telemetry or second permission store added.
- Shared review product code intentionally unchanged, now behaviorally covered after FULL upgrade. SMTP finalize still completes an already-admitted send; fresh downgraded requests deny.
- Frontend unchanged; no browser/UI PASS claimed. Per child §153, this stage exercised equivalent APIs; full 1440/390 UI acceptance remains final08. Whole-master unrestricted mvn/Flyway/ownership/final artifact gates remain controller's final-run responsibility, not silently claimed here.
- No remaining child prerequisite or known in-scope failure. READY_FOR_VERIFICATION is **not PASS**. Next action: controller's fresh independent light verifier, then downstream child05 only after that acceptance.

Receipt provenance clarification: the pattern list above is a normalized audit index, not a claim that each normalized regex was reissued byte-for-byte in Epoch2. Epoch2 freshly searched combined constructor/import/direct-controller-call/lineguard/confirmation patterns and read their actual source; the full send-method callsite receipt is the unchanged-base Epoch1 receipt read again at entry, supplemented by source inspection and later exact claim/finalize searches. Epoch1's numeric direct-constructor locations refer to the pre-edit source. No historical gate output is used as final current-identity verification evidence.

## Controller terminal checkpoint — 2026-10-30T12:07:34.458Z
- Terminal independent verdict: LIGHT_PASS; exact report retained in verify-log.md.
- Final child code head: a2d4b0e68691d115cff2a6ad3bcc115a06b28d27
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
