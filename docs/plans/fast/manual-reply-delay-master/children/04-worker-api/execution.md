## Epoch 1 — PLAN_CONFLICT (bounded prerequisite check)

Executor: `Impl04WorkerApi`.

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md`
- Plan SHA-256: `b68fedd43c52e61c4a2502808d3514986baf22c932b24f5029945dcba261ae4d`
- Execution ID: the canonical plan path above plus `@b68fedd43c52e61c4a2502808d3514986baf22c932b24f5029945dcba261ae4d`.
- Epoch: NEW, bound to the user-approved plan at `05342b4be63bbc7d9b30416ae73ada2d44b562df`; read-only `git diff --quiet` confirmed those plan bytes match.
- Master approval supplied: `0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Branch: `fast/manual-reply-delay-master`.
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Pre-execution product SHA: `9bbb3b050f533a0010d48d545b4756abc8aa7e82`.
- Pre-execution/checkpoint HEAD: `82ba0687a37d6cf26e908053763c2227cebe2476`.
- Post-execution product SHA: unchanged `9bbb3b050f533a0010d48d545b4756abc8aa7e82`; no child04 product commit.
- Implementation range: empty. No production or test file edited; only this execution report appended.
- Existing controller modification to `docs/plans/fast/manual-reply-delay-master/ledger.md` was preserved.

### Confirmed scope conflict

04 T3 requires restore to return original input (`manual-reply-delay-04-worker-api.md:77,81`). The downstream contract explicitly requires complete LEGACY input and QA/assembly/RAG/adopt context, recovered from server snapshots after refresh (`manual-reply-delay-06-frontend.md:12,113,145,208-210`). The controller explicitly authorized this bounded downstream prerequisite inspection, then instructed no product edits until the missing production-file authority is resolved.

The current LEGACY restore model is lossy for that contract:

| Original input / missing persisted identity | Source evidence | Consumer / consequence |
|---|---|---|
| Original `TrustReplyAssembleRequest`: top-level expected source/evidence versions, requested fact IDs, per-request fact selections, frame selection/version, final-paragraph request structure and operator facts are not captured as the original request. | `TrustReplyWorkbenchService.kt:357-370` declares the request. `DelayedManualReplyModels.kt:183-188` retains only source, verified itemVersions, paragraph-by-request mapping and diagnostics. Its factory `:598-605,712-723` persists that reduced result, not the request. Per-item source/evidence versions do exist at `TrustReplyWorkbenchService.kt:298-314`; they are not the original top-level aggregate request or frame/selection data. | Existing legacy editor `app.js:17699-17721` builds `expectedSourceVersion`, `expectedEvidenceSetVersion`, `requestFactSelections`, `frameSnapshot` and locked items; `:18497-18506` resubmits `adopt.trustReplyAssembly`. 06 T1 requires restoration of precisely this legacy context. The current model cannot faithfully recover a non-null original frame snapshot or the original selection/request structure. |
| Original `MeetingInput`, notably `zoneId`, `startLocal`, `endLocal`, `generatedAt`, and original `zoomUrl` spelling. Legacy input also contains ignored compatibility fields (`templateId`, `templateBody`, `expertSalutation`, `senderSignature`), which are not retained as raw input. | `MeetingConfirmationModels.kt:40-58` declares the input; `PendingMailOperationService.kt:189-190` accepts it. `DelayedLegacyRestore` at Models `:191-203` retains ICS and `DelayedMeetingEventSnapshot` only; the latter at `:108-112` stores UTC milliseconds and meeting link. Factory `:721-723,731-737` copies only those final artifacts. `PreparedManualReply` at Pending `:2025-2030` retains raw subject/html/text, but no original meeting or assembly request. | 04 restore's original-input contract includes accepted LEGACY request data. Meeting editor serialization explicitly retains zone/local-time/generatedAt at `mailbox-chat.js:149-153`, and send consumes `meeting.input` at `:9455-9456`; this current editor evidence is not a claim that the existing app.js legacy editor already exposes meeting controls. UTC/ICS must not be substituted for original input in the new restore API. |

Meeting inference is not a safe substitute: `MeetingConfirmationService.kt:173-183` normalizes the URL/zone/local times; `:202-225` generates a semantic digest and ICS. Its `:460,473-474` renders a country label rather than preserving the original IANA zone identifier in the human meeting text, and `:538-542` writes UTC DTSTART/DTEND. A digest is not reversible original input. Re-parsing or guessing a zone would violate the explicit instruction not to fabricate original payload from UTC/ICS.

The storage issue cannot be solved solely inside the authorized controller/worker/Pending changes: `DelayedManualReplySnapshotCodec` serializes the declared model and rejects unknown JSON properties (`DelayedManualReplyModels.kt:274-305`). Packing restoration input into unrelated evidence/audit text or diagnostics would violate the explicit whitelist and conflate user input with trusted internal evidence.

### Smallest authority needed

Authorize `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` in child04, in addition to already-authorized Pending and child04 tests:

- Capture original legacy restore inputs at preparation in `PendingMailOperationService.kt`, keeping these distinct from trusted delivery evidence.
- Extend the explicit restore model/factory/validation in `DelayedManualReplyModels.kt` to persist the missing original assembly and meeting input through a safe, bounded whitelist. Decide exact snapshot compatibility/version handling in the amendment; do not silently invalidate previously valid snapshots.
- Exercise round-trip restoration in the already-authorized child04 test files. No additional existing-test authorization is requested without concrete compatibility evidence.
- Keep worker SMTP payload frozen and unchanged; original input is for restoration only and must not become new send authority.

This is a prerequisite/scope finding, not a repair plan or permission to modify Models. No applied migration or later-child production change is needed to establish this finding. No existing plan was changed.

### Task status

| Requirement | Status | Evidence |
|---|---|---|
| T1 runtime reconstruction | BLOCKED before implementation | Full child execution paused at prerequisite scope gate. |
| T2 bounded worker | BLOCKED before implementation | No worker file created. |
| T3 original-input restore | CONFLICT | Missing declared original-input fields require prohibited Models edit. |
| T4 fixed public mapping verification | BLOCKED before implementation | Existing lifecycle mapping read only; no child04 test/verification claim. |

### Command evidence

All commands ran in the target worktree, never the main worktree.

| Command | Observed result |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` | Exit 0, canonical path and SHA-256 above; repeated before report, unchanged. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master` | Exit 1: unrelated stale publish-worktree registry path `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b` is absent. Used explicitly allowed native read-only fallback; did not prune. |
| `git rev-parse --show-toplevel && git symbolic-ref --short HEAD && git rev-parse HEAD --git-dir --git-common-dir` | Exit 0; root, branch, HEAD and Git directories shown above. |
| `git merge-base --is-ancestor 9bbb3b050f533a0010d48d545b4756abc8aa7e82 HEAD` | Exit 0; supplied product base is reachable. |
| `git diff --quiet 05342b4be63bbc7d9b30416ae73ada2d44b562df -- docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` | Exit 0; exact approved child bytes match. |
| `git status --short` (before this report) | Only controller-owned `M docs/plans/fast/manual-reply-delay-master/ledger.md`; no product changes. |

Targeted grep receipts:

- Pattern `data class MeetingInput|data class CalendarAttachmentSnapshot`, main Kotlin source: hits `MeetingConfirmationModels.kt:40,134`; those declaration ranges were read.
- Pattern `data class TrustReplyAssembleRequest|function buildTrustReplyAssemblySnapshot|function buildManualReplySendPayload|trustReplyAssembly`, scoped to `TrustReplyWorkbenchService.kt` and `app.js`: request declaration `:357`, serializer `app.js:17699`, adoption `:17751`, actual send assignment `:18505-18506`; full relevant ranges were read.
- Pattern `startLocal|generatedAt|previewAttachmentSha256`, `mailbox-chat.js`: input whitelist `:150`, request attachment digest `:9456`; these are existing consumers, not newly implemented restore behavior.
- Pattern `restore|原输入|恢复|meeting`, child06 plan: original-input obligations at `:12,113,145,208-210`.

### Verification / runtime evidence

No build, compilation, test, formatter, SMTP, database or runtime smoke command ran. Required Maven commands and runtime scenarios are NOT SATISFIED. No passing test counts, skips or smoke results are claimed. A field-declaration scope conflict does not require running product commands to reproduce missing persisted data; controller explicitly prohibited doing so.

### Freshness and next action

- Plan identity rechecked: YES, unchanged.
- Native worktree identity rechecked: YES.
- Product changes/commit: NONE.
- Required implementation commands run: NO.
- Historical test evidence used as current proof: NO.
- Remaining blocker: explicit production scope/snapshot compatibility amendment for original LEGACY input persistence.
- Next action: controller persists PLAN_CONFLICT pause and obtains exact authority. Resume execution against newly approved immutable plan bytes; do not mark this epoch READY_FOR_VERIFICATION.

## Epoch 2 — Controller authority resume

- User selected 「补齐原输入并继续」; A7/A8 plan commit d57b856427a83a6ccf23c60e58df9d92db9f266d resolves the original-input Models scope conflict and fixes compatibility semantics. Production base remains9bbb3b050f533a0010d48d545b4756abc8aa7e82; epoch1 implementation range was empty.
- Fresh isolated implementer will implement amended04 in the retained worktree; fix counter0. No test execution/result is claimed by this resume record.

## Epoch 2 — Implementer execution result: READY_FOR_VERIFICATION

Executor: `Impl04Epoch2`. This is implementation/execution evidence, **not an independent verification PASS**.

### Identity and authority

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md`.
- Final plan SHA-256: `56da95916445b1137b6c1127f8d64209f6e58b24bd8c981fa668e7ebd37a4878`.
- Execution ID: the canonical path above plus `@56da95916445b1137b6c1127f8d64209f6e58b24bd8c981fa668e7ebd37a4878`.
- Execution epoch: NEW implementation in epoch2; epoch1's prerequisite finding remains historical.
- Initial approval: A7/A8 `d57b856427a83a6ccf23c60e58df9d92db9f266d`; initial child SHA-256 was `5d7b0192e445be098d274da566a12382290fa9d0842ef5222bdd0f896dde1d95`.
- Explicitly approved in-flight compatibility amendment: A9/A10 at `de076f6413b65809ca13610c0455965134056dcb`. The amended child was reread and rebound before using the extra test-file authority. This was a controller-approved authority advance, not an unnoticed plan change.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Branch: `fast/manual-reply-delay-master`.
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Worktree ID: target root above + `@fast/manual-reply-delay-master@` + worktree Git directory above.
- Supplied pre-execution docs HEAD: `a4aca9bc05b3997dc201d0948dc8d3e61b75f338`; observed pre-product-commit HEAD after A9/A10: `de076f6413b65809ca13610c0455965134056dcb`.
- Pre-execution product SHA: `9bbb3b050f533a0010d48d545b4756abc8aa7e82`.
- Post-execution product SHA: **`9cebea6d41cc3b749382deb7691ba704471b131e`**.
- Product subject: `feat(fast-p): implement 04-worker-api`.
- Product commit contains the nine authorized production/test paths and no fast-p evidence files.
- Evidence HEAD at handoff: product HEAD above; this appended report and controller-owned brief/ledger changes are intentionally not committed by the implementer.

### Task status

| Requirement | Status | Implementation and exercised evidence |
|---|---|---|
| T1 / I-4 / I-7 runtime reconstruction | IMPLEMENTED | Pending reconstructs runtime account/contact/thread and validated draft/review references, checks persisted actor existence/current permission, current recipient/account, suppression, attachment identity and ICS bytes; worker checks the reconstructed full fingerprint against the persisted attempt. No public send entry, template/fact selection or ICS generator is invoked by replay. Worker tests cover revoked permission, changed recipient/thread, disabled account, suppression, attachment mismatch, current review context and author permission. |
| T1a / I-6 / A8 | IMPLEMENTED | Typed `originalInput` retains the original LEGACY request, including assembly source/evidence versions, requested facts, request matrix, frame/version, final paragraphs/operator facts, QA/RAG/adoption fields, original MeetingInput and ordered attachment IDs. It is restore-only, not payload/fingerprint or trusted-reference authority. Schema remains1; old missing-input JSON still decodes/replays, but restore returns422 SNAPSHOT_INVALID. New input has count/size/range/type/unknown-field checks. Existing frozen-payload decoding is not made dependent on original input. |
| T2 / I-1 / I-5 / I-11 | IMPLEMENTED | Fixed one-thread `manual-reply-delay` executor with SynchronousQueue, rejected busy ticks ignored, no local task backlog; separate scheduled stale recovery; entry switch is not a worker gate. Tests exercise59999/60000, blocked delivery capacity, independent recovery, competing workers, frozen MIME bytes, DATA disconnect and failed completion. Required real-MySQL lifecycle suite ran freshly40/40. |
| Inherited03 result-finalizer forwarding | IMPLEMENTED | Pending dispatches success, classified failure, completion failure and unchecked delivery failure through the delayed finalizers when the inherited DelayedDispatchRef is present; synchronous calls retain their old finalizers. Loopback smoke observes the original token on SENT and UNKNOWN completion paths. |
| T3 / I-7 / I-10 | IMPLEMENTED | Exact-source list with fixed LIMIT20; get/restore/cancel/retry; current contact permission and actor/review-action restrictions; strict integral expectedRevision; opaque public projection; source-bound restoration; historical missing input422, oversized restore413, missing/cross-contact404, access403, state/revision409. Late cancel retains DELAYED_REPLY_TOO_LATE/已超过撤回期限 even after claim wins. Actual Spring MVC handler execution and real HandlerMethod/resource-policy tests cover READ/FULL/NONE/admin and review restrictions. |
| T4 | IMPLEMENTED | Tests assert all ten fixed public messages; unknown stored error text maps to SNAPSHOT_INVALID; UNKNOWN/permanent retry is rejected before preflight. No raw SMTP detail or snapshot is exposed by public state DTOs. |
| A10 existing-test compatibility | IMPLEMENTED | The existing SnapshotTest Prepared helper and meeting fixture now supply explicit original requests. Existing assertions were retained. SnapshotTest participates in the required unit command and passes11/11. |

### Concrete compatibility amendment evidence

Before editing the then-unlisted test, the implementer reported that `DelayedManualReplySnapshotTest.kt:291-312` constructed PreparedManualReply without originalInput while its existing fromPrepared cases required newly generated LEGACY snapshots. A8 makes that new-factory input mandatory. The controller recorded A9/A10 under the user's preapproved A5 compatibility authority, supplied the exact amended commit and authorized this test path. No failing baseline was rerun to reconfirm this source-level mismatch. The test change supplies explicit original input/MeetingInput; it does not infer missing input in production or weaken codec/business assertions.

### Final required commands

All commands ran in the retained target worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Actual runtime: Zulu OpenJDK11.0.15, build11.0.15+10-LTS.

The final execution ran the following commands sequentially, joined by `&&`, after the final source/test edit. Combined process exit0 confirms each command exited0. Complete output: `artifact://2227`.

| Command | Exit/result | Counts and evidence |
|---|---|---|
| `mvn -q -DskipTests test-compile` | 0 | Main and test compilation completed; quiet command emitted no output. |
| `mvn test -Dtest='DelayedManualReplyWorkerTest,DelayedManualReplyControllerTest,ExpertMailPermissionGuardTest,DelayedManualReplySnapshotTest'` | 0 | JVM43 tests,0 failures,0 errors,0 skipped: Worker9, Controller12, PermissionGuard11, Snapshot11. Bound Maven JS phase:2038 passed,0 failed,0 skipped. BUILD SUCCESS at10:26:18+08:00. |
| `mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true` | 0 | Real MySQL lifecycle40 tests,0 failures,0 errors,0 skipped. Bound Maven JS phase:2038 passed,0 failed,0 skipped. BUILD SUCCESS at10:30:07+08:00. |
| `git diff --check` | 0 | Patch hygiene checked before staging. |
| Native worktree/branch/Git-directory checks and commit reachability | 0 | Exact retained worktree/branch verified before add and commit; product commit is HEAD and an ancestor of `fast/manual-reply-delay-master`. |
| `plan_identity.py` after implementation/commit | 0 | Final child hash remains `56da95916445b1137b6c1127f8d64209f6e58b24bd8c981fa668e7ebd37a4878`. |

Earlier execution attempts are not substituted for final evidence:

- Initial compile exited1 due to missing required MailSenderAccount fixture fields and nullable-property smart casts in the newly authorized WorkerTest; corrected in that same file.
- A subsequent compile exited0 and unit run39/39 exited0 (`artifact://2218`).
- Intermediate complete command sets exited0 with42/40 (`artifact://2220`) and43/40 (`artifact://2223`). Final proof is the later43/40 run in `artifact://2227`.
- The known baseline MailReviewQueryIT failure was neither rerun nor changed. No whole Kotlin suite or WAR package is claimed.

### Isolated runtime proof and limits

- Docker endpoint: local OrbStack, Docker29.8.2. Both database suites created disposable `mysql:8.0.36` containers. Final URLs were `jdbc:mysql://localhost:33497/resource_guard` and `jdbc:mysql://localhost:33499/delayed_reply`; no default development/production datasource was used.
- Permanent WorkerTest runtime scenarios execute the real worker, real Pending rehydration/delivery, real SmtpMailDeliveryService and JavaMail against an ephemeral loopback SMTP listener. Historical JSON without originalInput is decoded, two worker instances compete, and the receiving side parses MIME to assert frozen HTML/plain text, original Message-ID/thread header, exact ICS bytes and ordered attachment names/bytes.
- Database/lifecycle collaborators are controlled in that SMTP component smoke to inject claim/result conditions; genuine SQL/CAS/transaction recovery is exercised separately by the required real-MySQL40-test lifecycle suite. This is not represented as a full application deployment/process-restart test.
- Final observed smoke lines in `artifact://2227`:
  - `disconnect=true completionFailure=false result=UNKNOWN smtp=1 token=smoke-token`
  - `disconnect=false completionFailure=true result=UNKNOWN smtp=1 token=smoke-token`
  - `disconnect=false completionFailure=false result=SENT smtp=1 token=smoke-token`
- Recovery and another due scan do not increase the SMTP count. Separate worker-capacity test proves no claim at59999, first claim at60000, twenty busy ticks add no delivery, and recovery is callable while the delivery thread is blocked.
- ControllerTest invokes the actual changed controller through Spring MockMvc. PermissionGuardTest uses actual HandlerMethods/read-only policy and real isolated metadata SQL. It covers READ state visibility with false capabilities and forbidden restoration/actions.
- No throwaway harness files remain: the loopback harness is a retained regression inside the authorized WorkerTest. No production SMTP, production database, browser deployment, later-child implementation, push or merge was performed.

### Changed files

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorker.kt` — bounded delivery/preflight and independent recovery.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt` — state/source query, restore/actions, capability and fixed HTTP/error contracts.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — original request capture, runtime rehydration, delayed-result routing and typed frozen archive replay without inventing a fresh verified-assembly response.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt` — exact route registration and current persisted-actor admission.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` — optional backward-compatible restore-only request, bounded strict new-input validation and mandatory new-factory capture.
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt` — reconstruction, restoration compatibility, capacity and loopback SMTP regressions.
7. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt` — actual HTTP handler contracts.
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt` — real route/resource and actor-existence matrix.
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplySnapshotTest.kt` — authorized existing fixture compatibility only.

### Freshness and handoff

- Final plan identity rechecked: YES.
- Native worktree identity fallback used as explicitly authorized; the known unrelated stale publish-worktree helper failure was not rerun and no worktree registry was modified.
- Required commands run freshly after final implementation: YES.
- Product commit reachable from the retained branch and current HEAD: YES.
- Product/index clean after commit: YES; remaining changes are controller-owned brief/ledger plus this appended report.
- No05–07 production files, applied migrations, configuration or unlisted tests changed.
- Remaining blocker: none identified during execution.
- Next action: controller dispatches fresh independent child verification against `9cebea6d41cc3b749382deb7691ba704471b131e` and the final A9/A10 plan identity. Do not treat READY_FOR_VERIFICATION as independent acceptance.

## Terminal closing record — Epoch 2

- Code head: 9cebea6d41cc3b749382deb7691ba704471b131e.
- Independent Verify04Epoch2 returned LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; exact four-gate evidence is in verify-log.md. Current epoch2 repair rounds0; epoch1 had a scope pause before any product edit.
- Fresh verifier required exits0/0/0; unit43/43, MySQL lifecycle40/40, JS2038/2038 in each test command. Real loopbackSMTP path observed SENT and injected UNKNOWN outcomes with one SMTP delivery each and originaltoken.
- Evidence limit retained as O-1: SMTP component with controlled lifecycle collaborators plus separate realMySQL proof is not full application E2E or process/browser restart. Those explicit07 obligations remain; no whole-system PASS claimed.
- A7/A8/A9/A10 authority chain and original-input compatibility are implemented; further downstream boundaries are recorded by the verifier. No runtime command was run by this controller closing record.

