## Epoch 1 — pre-implementation prerequisite receipt

Execution identity: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-03-resource-guard.md@0033f9c3d1460f43b2fc1e26a00250a88f679fcb410be0b51f02a2b018055e47` (NEW). Executor ExpertAccess03Impl. Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`. Pre-execution product SHA `6c0981eb4e148e9789c3af49e9d2dca9739c2508`; initial evidence HEAD `a2cc129c69f805a750f8ba6242fe50de60a0b561`. Existing brief/ledger changes belong to controller and are preserved.

Before symbol edits, exact constructor search using functions.grep, pattern `(AuthInterceptor\(|AuthWebConfig\(|PendingMailOperationService\()` over target `src/test`, yielded:

```
discovery/controller/DiscoveryPaperArchiveControllerTest.kt:32 AuthInterceptor(auth,f.mapper)
discovery/controller/DiscoveryVerificationControllerTest.kt:32 AuthInterceptor(auth, mapper)
discovery/service/DiscoveryApolloSettingsIT.kt:309 AuthWebConfig(auth, APOLLO_MAPPER)
document/controller/ExpertMaterialArchiveFlowTest.kt:42 AuthInterceptor(auth, mapper)
document/controller/ManualExpertMaterialUploadFlowTest.kt:200 AuthInterceptor(authService, ObjectMapper())
mail/RagSendBridgeTest.kt:137 PendingMailOperationService(...)
mail/controller/OutboundAttachmentFlowTest.kt:212 PendingMailOperationService(...), :260 AuthInterceptor(...)
mail/service/MailReplyDraftSendIntegrationTest.kt:133 PendingMailOperationService(...)
mail/service/MailReviewSendIT.kt:129 PendingMailOperationService(...)
mail/service/MeetingCalendarSendIntegrationTest.kt:183 PendingMailOperationService(...)
mail/service/OutboundAttachmentServiceTest.kt:103 AuthInterceptor(...)
mail/service/PendingMailOperationServiceTest.kt:120 PendingMailOperationService(...)
mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt:107 PendingMailOperationService(...)
mail/service/ReplyIdentityServiceTest.kt:181 AuthInterceptor(...)
config/RestTemplateConfigTest.kt:198 OpenAlexAuthInterceptor(...) [false positive, excluded]
```

14 unlisted fixture paths require mechanical wiring if the required security dependencies use non-null constructors. No LSP tool is exposed in this execution; exact all-caller constructor/handler searches substitute, not an asserted LSP run. Requested controller's bounded amendment before any unlisted edit. No nullable/default permissive security shim will be added. This receipt is not completion or verification evidence.

### Complete fixture amendment receipt

Tool command (read-only): `functions.grep({"i":"Auditing constructor call sites","pattern":"(AuthInterceptor\\(|AuthWebConfig\\(|PendingMailOperationService\\()","path":"/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test","case":true,"gitignore":true,"skip":0})`; follow-up same command `skip=20` returned `No more results (15 files total; skip=20 is past the end)`. Output above preserves every actual constructor hit (the OpenAlex hit is explicitly excluded).

Exact necessary additional paths:

1. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt`
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt`
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
11. `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt`
12. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
13. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
14. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt`

Mandatory-constructor evidence: current `AuthInterceptor.kt:8-11` accepts only AuthService/ObjectMapper, `AuthWebConfig.kt:13-20` constructs it directly, `PendingMailOperationService.kt:62-99` has no resource-access dependency. The plan explicitly requires AuthWebConfig injection and authenticatedUsername service-entry recheck, therefore a mandatory non-null guard is needed at these seams; these exact direct callers must supply the new dependency to compile. Allowed additional-fixture change requested: constructor wiring only, using non-null mock or real guard as appropriate, preserving original authorization logic/assertions and all original regressions. New resource/security behavioral coverage stays in authorized tests; no default/null fallback, no weakening of old fixtures.

### Required unmatched-detail product prerequisite

Exact additional product path: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`.

Read-only source receipt (`functions.read` path ending `UnmatchedInboundMailController.kt:132-270,500-565`):

```kotlin
133: @GetMapping("/unmatched-inbound/{id}")
134: fun getUnmatchedDetail(@PathVariable id: Long): UnmatchedDetailResponse {
135:     val record = unmatchedInboundMailService.getDetail(id)
136:     val candidates = unmatchedInboundMailService.suggestCandidates(record)
137:     val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
...
153:         candidates = candidates.map { it.toResponse() },
```

Governing master exact-table excerpt: `UnmatchedInboundMailController.getUnmatchedDetail/sendManualRichReply/markResolved/cancelResolved | FULL，id→processing→contact | 无contact拒绝；详情user1 candidates为空；list/search/bind/AI/QA旁路不开放`.

Unconditional candidate discovery would leak a global candidate/ES capability even after resource admission, so the interceptor cannot fulfill this required response contract alone. Requested narrow change: session username consumption for this detail method; exact user1 returns `candidates=[]` and never calls `suggestCandidates`; all admin behavior unchanged. No constructor change, no API/body cancellation audit expansion. This is one additional required product path, not a mechanical fixture exception. Total requested amendment is 23 paths (original 8 + 14 fixture-only + this product controller), with explicit I-14 exception. No product file has been edited; awaiting controller's committed amended identity/new epoch.

### Staged pending admission decision and final bounded prerequisite list

Read-only command: `functions.grep` pattern `(fun |pending|username)` on `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`. Relevant output: lines 14-15 document `suspensionPendingCount` across all accounts; :89 checks `pendingCount(contactId)` without owner; :122 `pendingBadge(username)`; :126 counts `imp.reason_type` globally; :133 username only restricts `expert_mailbox_suspension`; :189 returns `pendingCount(contactId)`; :230 declares `pendingCount(contactId)`. No owner condition exists at this stage. Master exact table explicitly labels pendingBadge “06的个人owner SQL” and suspension “挂起只能处理本人的pending”.

Controller decision received before mutation: keep pendingBadge plus suspension GET/write handlers denied in03 until05/06 owner-scoped implementation; this is staged fail-closed admission, not removal of final06/08 requirements. No premature owner changes in MailboxSuspensionService. Original global admin behavior remains untouched.

Bounded03 prerequisite review completed: final amendment request remains original8 +14 mandatory mechanical constructor fixtures +1 unmatched detail controller =23 paths. No further product dependency is required by the investigated03 matrix. Body-target-dependent profile/tag/manual-draft/calendar-create/meeting-preview handlers remain denied until04. Resource-path writes whose body does not choose another resource retain fixed path checks; rich send remains the explicit required exception with existing all-member/anchor/draft service validation. Existing material batch/ZIP own all-member validation remains in its original service; no request inputStream inspection or generic framework. No changes requested to automatic/background flows, suppression, attempt fingerprint/status or file path/snapshot logic.

Downstream06 activation seam: MailReadOnlyPolicy's exact class+method+verb admission and ExpertMailPermissionGuard's fixed resolver map must add `MailboxConversationController.pendingBadge:GET` as session-only personal SQL, plus `suspension:GET`, `suspend:PUT`, `updateSuspensionReason:PATCH`, `resumeSuspension:DELETE` as FULL path-contact rules, only after06's owner-scoped SQL exists. No global flag, schema probe, dynamic allowlist or fallback; existing06 integration fixtures must prove real AuthWebConfig admission and current-session owner counts. These keys remain absent in03, final master intent unchanged.

### Controller arbitration superseding provisional product expansion

Controller found exact04b ownership of unmatched-detail redaction; therefore **closed decision: no additional UnmatchedInboundMailController edit in03**. Keep `getUnmatchedDetail` denied until04b implements its already assigned `candidates=[]`/no ES discovery contract. Original14-fixture request remains necessary; final amendment is **22 paths (original8+14 test-wiring-only)**. The provisional23-path request above is historical discovery, not current authority.

Controller explicitly allows material requestTransfers/downloadZip in03 using FULL path-contact plus existing all-member validation before side effects. Source proof: `ExpertMaterialService.kt:600-611` deduplicates all attachment IDs and verifies each `expert_document.expertContactId == contactId` before queue operations (:612 onward). `ExpertMaterialArchiveService.kt:28-36` resolves every selected file through `materialService.resolveReadyFile(contactId,id)` (its :107-118 verify document+actual attachment owner), before archive creation (:44), zip writes (:48), or content stream opening (:53). Foreign member failure prevents archive/file content output and transfer queue changes. Required new regressions will cover positive batches and a valid+foreign mixed batch.

## Epoch 2 — additional mandatory Spring-context consumers (before any product mutation)

Controller plan-only amendment commit `b8625c35f461816397b34d167493d62205e5f1d3` authorizes22 paths. Current source remains unchanged. A final Spring injection audit (beyond direct constructor references) discovered additional mandatory test-context wiring, so product work remains stopped pending exact scope authority.

Exact read-only tool command: `functions.grep({"i":"Checking injected web configuration consumers","pattern":"(Import\\(AuthWebConfig|auth.enabled=true|auth.enabled.*true|AuthWebConfig::class)","path":"/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin","case":true,"gitignore":true,"skip":0})`, then `skip=20`. Complete matched paths/line receipts:

```
auth/AuthFlowIntegrationTest.kt:78 auth.enabled=true [original scope; full ComponentScan:91-103 satisfies guard]
auth/config/AuthInterceptorTest.kt:100 @Import(AuthWebConfig...), :101 auth.enabled=true [original scope]
auth/service/ReadOnlyUserBootstrapTest.kt:143 auth.enabled=true [irrelevant; no AuthWebConfig import]
campaign/controller/MeetingCalendarControllerTest.kt:256 AuthWebConfig::class, :262 auth.enabled=true
discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt:37 @Import(AuthWebConfig...), :38 enabled
discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt:47 @Import(AuthWebConfig...), :48 enabled
discovery/controller/DiscoveryRecheckControllerTest.kt:30 @Import(AuthWebConfig...), :31 enabled
discovery/controller/DiscoveryReviewControllerTest.kt:53 @Import(AuthWebConfig...), :54 enabled
discovery/controller/DiscoveryReviewCountsControllerTest.kt:26 @Import(AuthWebConfig...), :27 enabled
discovery/service/DiscoveryRecheckFlowIT.kt:69 @Import(AuthWebConfig...)
document/controller/ExpertMeetingVideoControllerTest.kt:56 @Import(AuthWebConfig...), :57 enabled
expert/controller/ExpertClassificationAdminControllerTest.kt:40 @Import(AuthWebConfig...), :41 enabled
expert/controller/ExpertManualProfileControllerTest.kt:50 @Import(AuthWebConfig...), :51 enabled
mail/controller/EmailSuppressionControllerAuthTest.kt:20 @Import(AuthWebConfig...), :21 enabled
mail/controller/ExpertContactLocationControllerTest.kt:51 @Import(AuthWebConfig...), :52 enabled
mail/controller/ExpertContactNoteControllerTest.kt:32 @Import(AuthWebConfig...), :33 enabled
mail/controller/ExpertInboundNotificationControllerTest.kt:24 enabled, :25 @Import(AuthWebConfig...)
mail/controller/MailOpenTrackingControllerTest.kt:28 @Import(AuthWebConfig...), :29 enabled
mail/controller/MailboxConversationControllerTest.kt:126 AuthWebConfig::class, :136 enabled; :2187 AuthWebConfig::class, :2193 enabled
mail/controller/MeetingConfirmationControllerTest.kt:33 @Import(AuthWebConfig...), :34 enabled
mail/service/ExpertMailReadAccessIT.kt:78 AuthWebConfig::class, :86 enabled
reply/service/ReplyIdentityDefaultsIntegrationTest.kt:238 context.register(... AuthWebConfig::class.java...)
```

Minimal startup-injection proof: current AuthWebConfig is a constructor-injected @Configuration; adding the required non-null ExpertMailPermissionGuard dependency requires a bean before any request can run. The following17 files use `@WebMvcTest`, which excludes ordinary @Service beans; their explicit imports currently include AuthWebConfig but no new guard. They cannot acquire the new service from full application scanning:

1. `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt` — @WebMvcTest:254; explicit import:255-261.
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt` — @WebMvcTest:36; import:37.
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt` — @WebMvcTest:46; import:47.
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt` — @WebMvcTest:29; import:30.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` — @WebMvcTest:52; import:53.
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt` — @WebMvcTest:25; import:26.
7. `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt` — @WebMvcTest:55; import:56.
8. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` — @WebMvcTest:39; import:40.
9. `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt` — @WebMvcTest:49; import:50.
10. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionControllerAuthTest.kt` — @WebMvcTest:19; import:20.
11. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt` — @WebMvcTest:50; import:51.
12. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt` — @WebMvcTest:31; import:32.
13. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt` — @WebMvcTest:24; import:25.
14. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt` — @WebMvcTest:27; import:28.
15. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` — both slices @WebMvcTest:124/2185; imports:125-135/2186-2192.
16. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` — @WebMvcTest:32; import:33.
17. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` — @WebMvcTest:76; imports:77-83 (can import real guard alongside real access service/JDBC).

Two additional manual contexts, not component scans:

18. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` — :61 explicitly “No application component scan”; RecheckRuntimeMvc imports AuthWebConfig:69; runtime creates AnnotationConfigWebApplicationContext:604, registers MVC config:605 and only mapper/auth/controllers:607-610. Requires guard singleton fixture wiring.
19. `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt` — :231 creates AnnotationConfigWebApplicationContext; :234-236 registers identity service/auth/mapper; :238 registers exact MVC/controller/AuthWebConfig/advice classes without scan. Requires guard singleton fixture wiring.

These19 are disjoint from original14 direct-constructor fixtures. Consolidated minimal implementation scope requested is original8 +14 direct fixtures +19 context fixtures =41 paths, with product scope unchanged at5 files. Only mandatory fixture wiring/imports, except the separately identified obsolete temporary assertion below. No production controller signature changes are planned, so no additional controller-method direct consumers need migration. No full-component-scan test is included as unnecessary wiring.

### Obsolete temporary assertion requiring explicit retirement

`ExpertMailReadAccessIT.kt:425-459`, test `real auth projects live NONE READ FULL without opening later write handlers`: loop READ/FULL :440; always asserts follow403 :451-452 and rich reply403 :453-455 even for FULL, then `verifyNoInteractions(... pending,follows)` :459. These were child02 interim absence pins;03 intentionally opens FULL operations. Requested bounded update: keep these denials for READ with current resource403 code, stop pinning FULL absence; preserve live NONE/READ/FULL, cross-expert denial :448-449, admin-only grant403 :456-458, revoke :461-464, and zero side-effect assertions for denied cases. New full positive/send regression remains in originally authorized tests. Do not alter any stable NONE/READ/cross-resource/admin/APPROVED-lock expectation.

Epoch2 identity helper evidence: canonical plan SHA256 `bc3a85bab86baaee146ef29f4381870f67cbeffe28666699abf5cfdf6d083706` (13976 bytes); worktree helper HEAD `b8625c35f461816397b34d167493d62205e5f1d3`, same canonical root/branch/git-dir as Epoch1. No source changes or test runs under this identity; it cannot be READY_FOR_VERIFICATION. Runtime prerequisite fact commands only: genuine JDK11.0.33 and Docker server29.8.2 available (exit0); these are not tests or implementation proof.

## Epoch 3 — execution and bounded fixture blocker

Identity: epoch=3, fix_round=0; amended plan commit `42acd9796c75acf0603101b0ef97134477dba7d4`, canonical plan SHA256 `ff3120f1275af2ac9175116efb8187e6a859a50719f72114bea00faad2083208` (17821 bytes), product base `6c0981eb4e148e9789c3af49e9d2dca9739c2508`. Both identity helpers succeeded before implementation; root/branch/git-dir match Epoch1. Source implementation stayed inside approved41 paths.

Final-gate attempt1: required three targets, `-DmigrationIt=true -DargLine=-Dapi.version=1.41`, genuine JDK11.0.33 and disposable MySQL: exit1, tests36/failures1/errors0/skips0. New expected422 for a foreign conversation anchor was incorrect: existing ResponseStatusException(422, CONVERSATION_SENT_ANCHOR_NOT_FOUND) is mapped by inherited GlobalExceptionHandler to500 INTERNAL_ERROR (artifact2244:1064-1080). Preserved original production behavior; regression now checks exact mismatch token and no additional SMTP/attempt, without inventing422 or expanding global-error-handler scope.

Final-gate attempt2: all current three targets plus all33 added-fixture originals and both secondary test classes, flags `migrationIt=true,mysqlIt=true,argLine=-Dapi.version=1.41`, external disposable MySQL URL uses serverTimezone=Asia/Shanghai. Exit1: tests497/failures0/errors52/skips0. All52 errors are two contexts in the already-authorized `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`, not new guard injection failures: inherited MailboxConversationService requires ExpertMailReadAccessService but both explicit slice import lists omit it. Remaining445 tests passed; Maven stopped before bound JS execution.

Exact grep-tool receipt (before any extra fixture mutation): pattern `(ExpertMailReadAccessService|constructor|class MailboxConversationService|@Import|accessService)`, path `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt;src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`, case=true, gitignore=true:
```text
MailboxConversationService.kt:47 class MailboxConversationService(
MailboxConversationService.kt:57 private val readAccessService: ExpertMailReadAccessService
MailboxConversationControllerTest.kt:125 @Import(
MailboxConversationControllerTest.kt:2188 @Import(
```
Exact source receipt: first imports125-135 include real JDBC/repository/MailboxConversationService/ExpertFollowService/ExpertRepliedDismissalService/MailboxSuspensionService, no access service; second2188-2194 imports real JDBC/repository/MailboxConversationService, no access service. Both already supply NamedParameterJdbcTemplate through MailboxConversationRealJdbcConfig. Surefire reports for MailboxConversationControllerTest:7-9 and CalendarAttachmentIntegrationTest:7-9 show UnsatisfiedDependencyException constructor parameter7, NoSuchBeanDefinitionException ExpertMailReadAccessService. Minimal requested additional fixture scope: add the existing real ExpertMailReadAccessService class to those two @Import lists only; preserve every assertion and all product paths/signatures. No nullable/mock permission fallback, no new path.

Controller decision before correction: this is already-covered mandatory non-null security/context dependency wiring in the authorized mailbox fixture, not new scope. Remain epoch3/41 paths; import existing real ExpertMailReadAccessService in both slices using existing real JDBC; preserve every stable assertion, no default-level mock/shim and no product edit. No plan identity change required. Baseline missing-bean failure retained above; complete final gates must run freshly after correction.

Fresh final-gate attempt3 after real access imports: exit1, tests497/failures0/errors6/skips0 (artifact2252). MailboxConversationControllerTest now passes all46; the secondary CalendarAttachmentIntegrationTest progresses to the next inherited missing constructor collaborator: PendingMailOperationService (Surefire:7-8, MailboxConversationController constructor parameter3). Exact source receipt `MailboxConversationController.kt:221-227` requires conversation/follow/dismissal/pending/suspension services. Secondary slice `MailboxConversationControllerTest.kt:2211-2240` mocks auth/repositories/material/tags/search/follow/dismissal/suspension, but has no pending collaborator. Under the same controller-authorized mandatory-context wiring decision, add a non-null @MockBean PendingMailOperationService only to this download/timeline slice; it does not stub any permission level or change an assertion. This is the third bounded correction (first erroneous new test expectation; second inherited access import; third inherited pending mock), with no product-behavior repair or scope expansion. Re-run complete gates freshly; no further fix round is authorized by repository policy.

## Execution Result: READY_FOR_VERIFICATION

This is execution evidence, **not independent PASS**. Historical Epoch1/2 prerequisite records and failed attempts above remain unchanged.

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-03-resource-guard.md`
- Plan SHA-256: `ff3120f1275af2ac9175116efb8187e6a859a50719f72114bea00faad2083208`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-03-resource-guard.md@ff3120f1275af2ac9175116efb8187e6a859a50719f72114bea00faad2083208`
- Execution epoch: NEW, epoch3; controller fix_round0. Three bounded execution corrections are retained above; no independent verify/repair cycle was run.
- Approval basis: current continuous execution authorization plus controller plan-only commit `42acd9796c75acf0603101b0ef97134477dba7d4` and explicit in-scope mandatory fixture-wiring decision.
- Executor: `ExpertAccess03Impl`.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`.
- Target branch: `fast/expert-access-master`.
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Pre-execution code SHA: `6c0981eb4e148e9789c3af49e9d2dca9739c2508`.
- Pre-execution HEAD: `42acd9796c75acf0603101b0ef97134477dba7d4` (controller plan-only amendments).
- Post-execution code SHA / product commit: `d5093a2509427526784254aba7dbe98ff2a9b1d9`.
- Product subject: `feat(fast-p): implement 03`.
- Evidence HEAD at executor handoff: `d5093a2509427526784254aba7dbe98ff2a9b1d9`; this report is deliberately not in the product commit. Controller owns the subsequent evidence commit.
- Implementation boundary: `6c0981eb4e148e9789c3af49e9d2dca9739c2508..d5093a2509427526784254aba7dbe98ff2a9b1d9`; product delta in the last commit is exactly41 approved paths, +655/-34, no plans/reports/artifacts.

### Task status

| Requirement | Status | Implementation and evidence |
|---|---|---|
| I-2 precise handler+verb admission | IMPLEMENTED | Policy delegates only fixed resource keys; existing authentication/first-password/default-deny paths remain. Real AuthWebConfig exercised in new guard MVC tests, AuthFlowIntegrationTest and unmocked-auth embedded runtime. No prefix/body/operatorName authority. |
| I-4 real resource ownership | IMPLEMENTED | Fixed SQL resolves contact/processing/mail/three attachment owners/outbound UUID/event; validates requested contact against resolved contact; malformed400, absent404, forbidden403. Original document/snapshot/uploader/realpath service checks remain. Seven targeted real-JDBC resource tests plus real HTTP three-owner bytes and cross-resource denials. |
| I-8 FULL direct rich send | IMPLEMENTED | Both existing service entries require current FULL before draft binding/completed-attempt lookup/claim/SMTP, and user1 audit actor is authenticatedUsername, not body operatorName. Existing renderer/fingerprint/attempt/suppression/delivery transaction pipeline unchanged. New authenticated integration test proves two entries, dedup, live downgrade and direct-service recheck; real SMTP runtime independently sends exactly once per entry. |
| I-13 no global expansion | IMPLEMENTED | Global admin/task/search/config paths still denied; dictionary/tool methods only exact approved keys. Admin/internal-null guard contracts preserved; existing manual/draft/review/calendar/attachment regressions run with actual opt-in gates. |
| Existing batch material ownership | IMPLEMENTED | FULL path contact plus existing complete membership checks before queue/ZIP output; permanent positive ZIP/transfer and mixed-foreign tests; actual HTTP mixed batches400 with provider/DB/file-state unchanged, positive ZIP200/transfer202. |
| Deferred04/04b/06 seams | IMPLEMENTED AS APPROVED STAGING | Body-target operations and old draft routes stay denied until04. Unmatched detail stays denied until04b redacts candidates/skips suggestCandidates. Badge/suspension handlers stay denied until06 owner SQL and precise activation. Actual stage-denial smoke only, no new permanent interim absence pins. |
| Mandatory fixture migration | IMPLEMENTED | All33 added fixture paths receive non-null constructor/bean/context wiring, with real access service added to the two inherited JDBC mailbox slices and an unused pending collaborator mock in the calendar-only slice. No nullable/default security shim. Stable assertions retained except explicitly retired02 FULL-global-denial pin / replaced global wording. |

### Final commands and evidence

All repository commands used the exact target worktree and genuine JDK11.0.33. Disposable external MySQL was `127.0.0.1:23391/expert_access03`, not production; own-container tests used their existing disposable fixtures. `serverTimezone=Asia/Shanghai` was explicit.

The final successful command, **after the final source/test correction**, was:

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:23391/expert_access03?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=fixture mvn test -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,DiscoveryPaperArchiveControllerTest,DiscoveryVerificationControllerTest,DiscoveryApolloSettingsIT,ExpertMaterialArchiveFlowTest,ManualExpertMaterialUploadFlowTest,RagSendBridgeTest,OutboundAttachmentFlowTest,MailReplyDraftSendIntegrationTest,MailReviewSendIT,MeetingCalendarSendIntegrationTest,OutboundAttachmentServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ReplyIdentityServiceTest,MeetingCalendarControllerTest,MeetingCalendarControllerMysqlTest,DiscoveryIdentityRecoveryControllerTest,DiscoveryPersonMaintenanceControllerTest,DiscoveryRecheckControllerTest,DiscoveryReviewControllerTest,DiscoveryReviewCountsControllerTest,ExpertMeetingVideoControllerTest,ExpertClassificationAdminControllerTest,ExpertManualProfileControllerTest,EmailSuppressionControllerAuthTest,ExpertContactLocationControllerTest,ExpertContactNoteControllerTest,ExpertInboundNotificationControllerTest,MailOpenTrackingControllerTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MeetingConfirmationControllerTest,ExpertMailReadAccessIT,DiscoveryRecheckFlowIT,ReplyIdentityDefaultsIntegrationTest -DmigrationIt=true -DmysqlIt=true -DargLine=-Dapi.version=1.41
```

| Command / gate | Observed result |
|---|---|
| Final Maven command above | Exit0, BUILD SUCCESS;38 selected Java/Kotlin test classes,497 tests,0 failures,0 errors,0 skips. Artifact2254/2255; exact selected Surefire reports independently summed to497/0/0/0, not all stale reports under target. |
| Maven-bound full JS suite |1961 tests,352 suites,1961 pass,0 fail/cancelled/skipped/todo; same final command. |
| Maven-bound node-check-app / node-check-task-modal-runtime | Both executed successfully; final Maven exit0. |
| `git diff --check` | Exit0, no output. |
| `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/expert-access03-runtime.classpath -Dmdep.includeScope=test` | Exit0, freshly resolved runtime classpath. Runtime used `target/classes` plus this current dependency classpath, not inherited expanded-WAR classes and not target/test-classes. |
| `python3 /tmp/expert-access03-smoke.py` | Final exit0,51 real HTTP calls; detailed matrix below. First temporary fixture attempt exited1 because calendar API requires yyyy-MM-dd'T'HH:mm, not seconds; corrected only temporary fixture, reset owned partial seed, then completed. No product repair. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-03-resource-guard.md` | Exit0 before implementation and handoff; same17821 bytes / SHA above. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-03-resource-guard.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master` | Exit0 immediately before exact-path git add and again before git commit; current root/branch/git-dir match. |
| Exact committed-scope check | `git diff-tree --no-commit-id --name-only -r d5093a2509427526784254aba7dbe98ff2a9b1d9` set equals the41 authorized source/test paths; `git show -s --format=%s` matches exact subject. No product plan/report/artifact. |
| `git rev-parse HEAD && git merge-base --is-ancestor HEAD refs/heads/fast/expert-access-master && git diff --quiet -- src && git diff --cached --quiet` | Exit0; HEAD is d5093a2509427526784254aba7dbe98ff2a9b1d9, target-branch reachable, product tree and index clean. Controller-owned brief/ledger plus this report intentionally remain outside product commit. |

Selected primary target counts: AuthInterceptorTest19, AuthFlowIntegrationTest10, ExpertMailPermissionGuardTest7. Added fixture gate counts: DiscoveryPaperArchive7, DiscoveryVerification4, DiscoveryApolloSettings8, ExpertMaterialArchiveFlow4, ManualExpertMaterialUploadFlow13, RagSendBridge11, OutboundAttachmentFlow9, MailReplyDraftSendIntegration27, MailReviewSend18, MeetingCalendarSendIntegration7, OutboundAttachmentService28, PendingMailOperationService42, PendingMailOperationServiceTrustWorkbench60, ReplyIdentityService20, MeetingCalendarController6+Mysql3, DiscoveryIdentityRecovery7, DiscoveryPersonMaintenance8, DiscoveryRecheckController6, DiscoveryReview18, DiscoveryReviewCounts5, ExpertMeetingVideo10, ExpertClassificationAdmin8, ExpertManualProfile9, EmailSuppressionAuth2, ExpertContactLocation13, ExpertContactNote6, ExpertInboundNotification7, MailOpenTracking3, MailboxConversation46+CalendarAttachment6, MeetingConfirmation16, ExpertMailReadAccess15, DiscoveryRecheckFlow2, ReplyIdentityDefaultsIntegration7. Every selected count has0 failure/error/skip in final run.

Earlier unsuccessful final-gate attempts remain evidence, not hidden:36/1/0/0, then497/0/52/0, then497/0/6/0. Their root causes and bounded corrections are recorded above. The last successful current-state run supersedes them for execution readiness, not for historical truth. No tests/build/lint/formatter ran mid-implementation; required gates were deferred as explicitly instructed. Full unselected Maven suite and WAR packaging were NOT_RUN (not child03 required commands).

### Actual changed-path authenticated runtime smoke

Used real embedded Spring Boot/Tomcat main application with auth enabled and real AuthService/bootstrap users/password hashes, cookie sessions obtained through `/api/auth/login` (admin and user1; no auth mocks/session injection). Before real login, the disposable admin first-password flag was cleared as fixture setup; the unchanged first-password flow remains covered by integration tests. Fresh isolated database `expert_access03_runtime` received the real startup Flyway chain. Loopback collectors: ES HTTP23392, SMTP23393 (real JavaMail AUTH LOGIN/MIME send), application23394; temporary attachment root `/tmp/expert-access03-attachments`. Mail queue/scheduling/discovery/LLM automation disabled for isolation, ES base URL replaced by collector, no production credentials/delivery targets.

The Java main command used JDK11.0.33, `-cp target/classes:<fresh dependency classpath>`, `com.weibo.talentintroduction.TalentIntroductionApplicationKt`, `--server.port=23394`, datasource `jdbc:mysql://127.0.0.1:23391/expert_access03_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`, root/fixture, auth enabled, ES `http://127.0.0.1:23392`, mail-queue/scheduling/expert-discovery/llm/llm.auto-reply disabled, discovery cron `-`, and the temporary storage base path. Service readiness reported real `Started TalentIntroductionApplicationKt`, port23394.

Fixtures: A=9101 FULL, B=9102 READ, C=9103 NONE; real outbound mail rows9201/9202/9203, inbound processing9301/9302/9303 and calendar events1/2/3. Sender runtime03 was intentionally disabled, exercising the existing explicit-thread manual-send semantics without daily-counter increments.

| Actual HTTP operation | Observed behavior |
|---|---|
| Real admin/user1 login |200 each. Admin creates all three calendar fixtures201. |
| Anonymous A rich send / user1 A before grant |401 /403. Admin sets A FULL and B READ200. |
| user1 B/C conversation rich send |403, no provider/DB/file-state change. |
| user1 A conversation send, requestId repeated |200 SENT twice, exactly one collected SMTP message and one attempt; duplicate result identical. Body operatorName=admin, persisted actor=user1. |
| A path with C outbound anchor9203 | Existing500 INTERNAL_ERROR carries CONVERSATION_SENT_ANCHOR_NOT_FOUND; no additional SMTP/ES/attempt/file mutation. Existing global status mapping intentionally not changed. |
| user1 A inbound rich send, identical request repeated |200 SENT twice, exactly one additional collected SMTP message/attempt. B/C processing rich sends403. |
| user1 A actual multipart material upload |201, returned attachment1/document; raw manual-owner bytes persisted, no SMTP. |
| A record-owner9401 / processing-owner9402 / manual-owner1 direct downloads |200 with exact independent original bytes for each owner. |
| Admin C file download |200 exact C bytes; confirms admin unchanged. |
| user1 C direct download / A path+C attachment / C MAIL_RECORD / C INBOUND_PROCESSING attachment list |403 each, no file bytes returned and no provider/DB/file mutation. |
| Calendar B query / mixed A,C summaries / C event id / B event update |403 each before service mutation; A event GET200 with contactId9101. |
| A ZIP/transfers with [A9401,C9403] |400 each before queue/output; state snapshots unchanged. |
| A ZIP of three legitimate owners / transfer request |200 ZIP verified all three exact member bytes /202. |
| Unmatched detail / pending badge / suspension |403 in this approved stage; activation deferred04b/06, not permanent capability removal. |
| Global sender accounts / task executions / experts |403. |
| Admin downgrades A to READ |200; same user1 session immediately gets403 on all three old file links and both rich send routes, including already-completed duplicate requests. |
| A messages after downgrade |200 READ; every item attachmentCount=0, firstAttachmentNames=[], calendarAttachment absent/null, outboundAttachments=[]. |
| Admin revokes A; user1 reuses session | Revoke200; messages403. |

Final smoke totals:51 calls =17×200,4×201,1×202,2×400,1×401,25×403,1×inherited500. Exactly2 SMTP connections/messages,2 attempts, all manual-send audit actors user1, sender todaySentCount=0. ES collector total23 includes positive-path/startup calls; every denial individually asserted **zero delta**, not a fabricated global-zero claim. Every denial also compared mail/attempt/attachment/document/calendar/follow counts, calendar update-time aggregate and the complete temporary-file content hash manifest. Runtime proves no returned file bytes or file mutation on denial; no OS-level file-open tracer was claimed. Permanent real-AuthWebConfig tests separately verify forbidden requests never invoke the file service.

Owned runtime/provider services stopped, own Docker container `expert-access03-mysql` stopped/removed, and only owned `/tmp/expert-access03-*` scripts/classpath/result/files were removed after proof. No temporary scaffold remains in the product commit.

### Changed files

Exact committed manifest, all under the41 authorized paths:

```text
src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt
src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt
src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt
src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt
src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt
src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionControllerAuthTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt
src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt
```

Product purpose: fixed precise permission map/SQL guard, auth wiring, current-FULL/session-actor service recheck. Originally authorized tests add consumer-visible real-byte/real-JDBC/current-grant/dedup/audit regressions. Remaining fixtures are mandatory construction/context migrations and only the expressly authorized interim assertion retirement. Policy KDoc is updated within scope; no separate changelog/document path was authorized, so this requested execution report is the canonical change evidence.

### Downstream interfaces, limits and freshness

- New non-null Spring bean constructor: `ExpertMailPermissionGuard(ExpertMailReadAccessService, NamedParameterJdbcTemplate)`. No optional/default guard dependency.
- Public service seam `requireFull(authenticatedUsername: String?, contactId: Long)` preserves existing admin/internal-null callers; exact user1 uses current database FULL and MAIL_WRITE_FORBIDDEN403.
- Public MVC seam `authorize(username, handler, request)` reads resolved URI variables/query/real metadata only. Companion `isResourceHandler(handler, verb)` is the shared exact-key admission source. No request inputStream/body consumption, generic flags, schema probing, or cache.
-04 must add body-target guards and precise admission only after its approved per-DTO ownership logic; do not infer these denied methods are final product omissions.
-04b must redact unmatched detail candidates / skip suggestCandidates before admitting getUnmatchedDetail.
-06 must add owner-scoped pending/suspension SQL, then explicitly activate the existing precise keys in approved policy/guard scope and prove admission with real AuthWebConfig; do not bypass with a general allow flag.
- No schema migration, new store field, alternate mail-send transaction, fingerprint format, SMTP interface, suppression override or approval-lock behavior introduced.
- Actual UI acceptance, automatic IMAP/UID/tagging runtime, and final personal pending/suspension behavior were NOT_RUN here; source paths were unchanged or explicitly staged, existing named regression targets ran, and final08 retains its complete UI/automation acceptance. This report does not claim those future-stage actions passed.
- LSP tooling was not exposed; true production/direct-constructor/manual-context consumers were inspected through the exact grep/source receipts above before symbol edits.
- No known in-scope failure remains. The inherited anchor ResponseStatusException→500 mapping is an observation, not a hidden repair or reopened requirement.
- Plan identity rechecked: YES, unchanged. Worktree identity rechecked: YES. Exact product commit target-branch reachable: YES. Product tree/index clean: YES; controller-owned brief/ledger and canonical evidence intentionally unstaged. Required commands fresh: YES. Historical evidence used only as baseline: YES.
- Remaining blocker: none for current03 scope.
- Next action: controller dispatches a fresh independent light verifier; this executor does not run verify-p/review-p or claim PASS. No push/merge/deploy performed.

## Controller terminal checkpoint — 2026-10-30T07:45:20.978Z
- Terminal independent verdict: LIGHT_PASS_WITH_NOTES; exact report retained in verify-log.md.
- Final child code head: d5093a2509427526784254aba7dbe98ff2a9b1d9
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.
