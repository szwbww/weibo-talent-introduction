# Fast-P Verify Log — 04-worker-api

## Epoch 1 — Preflight scope pause

- Independent verification: NOT PERFORMED.
- Writer Impl04WorkerApi returned PLAN_CONFLICT before product edits or required commands.
- Evidence: execution.md, Confirmed scope conflict. Child04 T3 / child06 T1 require original LEGACY restoration; the closed snapshot model omits original assembly request and MeetingInput. Models is outside child04 authorization.
- Controller action: PAUSED_FOR_HUMAN pending exact production-file and snapshot-compatibility authority. This is not a LIGHT_PASS, LIGHT_FAIL, independent review or product test result.
- No product commit; retained product head: 9bbb3b050f533a0010d48d545b4756abc8aa7e82.


## Epoch 2 — Attempt 1

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04-worker-api — docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md
Boundary: 9bbb3b050f533a0010d48d545b4756abc8aa7e82..9cebea6d41cc3b749382deb7691ba704471b131e
Verifier: Verify04Epoch2 (fresh isolated lightweight verifier; distinct from Impl04WorkerApi and Impl04Epoch2)
Epoch: 2
Attempt: 1
Timestamp: 2026-11-05T02:49:36Z
Implementation: 9cebea6d41cc3b749382deb7691ba704471b131e
Approved master identity: docs/plans/2026-11-04/manual-reply-delay-master.md (commit de076f6413b65809ca13610c0455965134056dcb)
Approved child identity: docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md (commit de076f6413b65809ca13610c0455965134056dcb)
Brief: docs/plans/fast/manual-reply-delay-master/children/04-worker-api/brief.md (latest nine-file A8/A10 authority)
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Branch: fast/manual-reply-delay-master
Fix round: 0
Fix commits: —

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary inventory and implementation commit show the nine authorized product/test paths below. Models changes stay in restoration model/factory/codec; SnapshotTest changes only supply explicit original-request/meeting fixtures and retain its assertions. Other boundary paths are approved plan amendments and controller-owned fast-p evidence. |
| Plan and invariants | PASS | T1/T1a/T2/T3/T4 and I-1/I-4/I-5/I-6/I-7/I-10/I-11 have direct code and freshly exercised component/HTTP/real-MySQL evidence detailed below. Original-input persistence is restore-only and backward-compatible, not a new delivery authority. |
| Required commands | PASS | All three exact brief commands ran freshly once, sequentially after analysis, with JDK11. Exits 0/0/0; required JVM matrices 43/43 and 40/40, failures/errors/skips all 0; bound JS tests 2038/2038 with 0 skips in each Maven test command. Fresh results match the supplied passing baseline. |
| Downstream interfaces | PASS | Inherited DelayedDispatchRef reaches the existing atomic delayed finalizers; fixed public projection, exact source query, revision actions, raw-original-input restore and HTTP/error contracts match 05/06/07 prerequisites. Activation and full integrated acceptance remain assigned to 07, not silently implemented or claimed here. |

#### Identity and authorized-file receipts

- Native read-only fallback confirmed the target root, branch and Git directories. The unrelated stale optional publish-registry helper was not rerun or repaired. Git directory: /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master; common directory: /Users/lukai/IdeaProjects/weibo-talent-introduction/.git.
- `git show -s --format=%H%n%D%n%s HEAD` returned 9cebea6d41cc3b749382deb7691ba704471b131e, HEAD -> fast/manual-reply-delay-master, and feat(fast-p): implement 04-worker-api. Read-only identity and product/plan comparisons were successful before commands and again at the timestamp above.
- `git diff --quiet de076f6413b65809ca13610c0455965134056dcb -- docs/plans/2026-11-04/manual-reply-delay-master.md docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` returned no differences. `git diff --exit-code 9cebea6d41cc3b749382deb7691ba704471b131e -- src/main src/test` and `git diff --cached --name-status` also returned no differences.
- `git diff --name-status 9bbb3b050f533a0010d48d545b4756abc8aa7e82..9cebea6d41cc3b749382deb7691ba704471b131e` and `git show --format=fuller --stat 9cebea6d41cc3b749382deb7691ba704471b131e` establish this complete product/test inventory (receipts artifact://2234 and artifact://2236):

| Change | Authorized product/test path |
|---|---|
| A | src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt |
| A | src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorker.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt |
| A | src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt |
| M | src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplySnapshotTest.kt |
| A | src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt |
| M | src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt |

- Read epoch2 execution evidence and the epoch1 authority pause. A7/A8 resolve that historical production-file/restore conflict; A9/A10 authorize the concrete existing SnapshotTest fixture adaptation. None is reopened or treated as a missing prerequisite. No epoch2 repair occurred.

#### Explicit requirements and consumer-boundary evidence

| Requirement | Direct evidence and result |
|---|---|
| T1 / I-4 frozen reconstruction | PendingMailOperationService.kt:952-1088 validates the codec, ICS digest, persisted actor existence/current FULL permission, contact/ORCID/recipient, enabled nonsimulator account, suppression, inbound or real SENT anchor/thread, validated draft/review identity, current review context and ordered attachment metadata/bytes through the existing attachment resolver. It builds the payload from frozen subject/text/HTML/ICS; it does not invoke public send, template rendering, fact selection or ICS generation. Worker.kt:43-65 compares the reconstructed full fingerprint, recipient and account with the persisted attempt inside a short transaction. WorkerTest.kt:204-239,259-310 checks old/new fingerprint equality, frozen content, no workbench/meeting regeneration, attachment mismatch and current admission failures. |
| Inherited dispatch ref / I-5 atomic results | Worker.kt:76-84 passes claim.dispatchRef to deliverPrepared. Pending.kt:1137-1141,1186-1193,1230-1236,1261-1268 forwards it on success, completion failure, classified failure and unchecked failure. The receiving file-level adapters at :2354-2370 explicitly choose finalizeDelayedSuccess/finalizeDelayedFailure when non-null, retaining synchronous finalizers for null. Consumer ManualReplySendAttemptService.kt:621-626,639-653,794-825,1039-1113 validates token/source/fingerprint and commits job with the existing result transaction. The fresh loopback smoke observes the original token for SENT and UNKNOWN; fresh lifecycle IT exercises actual SQL result atomicity, rollback, stale recovery and late same-token convergence. |
| T2 / I-1/I-11 capacity, deadline and recovery | Worker.kt:26-41 uses a fixed one-thread executor named manual-reply-delay and SynchronousQueue; tick only submits and ignores busy rejection. The separate recover callback calls recoverStale directly, without waiting for the SMTP executor; worker has no entry-enabled gate or 60-second sleep. Repository.kt:54-58,129-134 scans database-time due IDs with LIMIT20; lifecycle claim :364-393 checks the database deadline and performs attempt/job CAS in its own transaction before delivery. WorkerTest.kt:313-354 proves no claim at59999, first claim at60000, no additional claim through twenty busy ticks, actual delivery thread name, and recovery while delivery is blocked. Two competing worker instances share the smoke claim CAS and produce one SMTP; real database concurrency/CAS is separately covered by LifecycleIT.kt:560-583 and its fresh40-test execution. |
| T2 / I-5 known failure versus uncertainty | Preflight failures use lifecycle.preflightFailure before SMTP (Worker.kt:85-104). Delivery-result routing preserves the existing safe/permanent/unknown classification; post-claim interruption/completion uncertainty is not automatically redelivered. Recovery changes state only. Fresh smoke covers DATA disconnect and completion failure as UNKNOWN with smtp=1; subsequent recovery/scan leaves SMTP count unchanged. LifecycleIT.kt:630-717,770-784 exercises actual recovery, no reclaim, old-token rejection and late success under the original token. |
| T1a / I-6 / A8 original-input persistence | Pending.kt:386-395 captures the submitted PendingManualRichReplyRequest, not a verified assembly summary or inferred UTC meeting. Prepared carries it to Models.kt:202-204,483-511,764-769. The typed request retains original source/evidence versions, requestedFactIds, requestFactSelections, frame/version, finalParagraphs/operatorFacts, QA/RAG/adoption fields, MeetingInput including zoneId/startLocal/endLocal/generatedAt and ordered attachment IDs. New LEGACY factory output requires originalInput. Codec.kt:284-293,307-320 keeps schemaVersion1 and historical absent/null restore data compatible, while rejecting unknown fields/coercions, invalid IDs/counts and total size overflow. Restored request data is not read as trusted delivery evidence or added to computeFingerprint. |
| I-6 historical compatibility and A10 | WorkerTest.kt:204-231 proves exact new-input round-trip, removal of originalInput from historical JSON, unchanged frozen payload/full fingerprint, and no regeneration. :234-256 covers RAG/adoption metadata, unknown schema/field, scalar-type rejection and oversize. The real SMTP smoke deserializes historical v1 with absent originalInput and sends the original frozen MIME/ICS/ordered bytes. ControllerTest.kt:49-61 returns original non-UTC assembly input for new tasks and fixed422 SNAPSHOT_INVALID with no invented copy for historical absence. SnapshotTest fixture diff supplies explicit original request and actual MeetingInput without removing assertions; its11 tests passed in the mandatory unit matrix. |
| T3 / I-7 exact HTTP and current permissions | Controller.kt:14-126 implements exact-source list(max20), get, restore, cancel and retry; list checks source contact and actor, owned checks job/contact/actor, action checks current FULL plus REVIEW admin restriction. Restore only permits CANCELLED/FAILED non-REVIEW and never exposes historical guessed input. Guard.kt:34-39 registers get/list as READ_CONTACT and restore/cancel/retry as CONTACT. Receiving MailReadOnlyPolicy.kt:123-134 admits registered resource HandlerMethods; Guard.authorize :132-148 resolves the current contact/permission, without broadening any path whitelist. Fresh ControllerTest exercises actual Spring MVC handlers; PermissionGuardTest.kt:151-186 uses real HandlerMethods/current metadata SQL for FULL/READ/NONE/admin and deleted persisted actor. READ state GET has false action capabilities and restore/actions403. |
| T3 / I-1/I-10 errors and capabilities | Controller.kt:73-98 strictly validates integral positive expectedRevision and state/revision; retry first runs current preflight, then lifecycle.retry. Projection :100-105 combines lifecycle status/deadline eligibility with current action permission and current preflight for retry. :77-79 preserves409 DELAYED_REPLY_TOO_LATE / 已超过撤回期限 even after the worker wins. ControllerTest covers missing/cross-contact404, unauthorized403, state/deadline409, malformed request/old snapshot422 and oversize restore413. |
| T4 fixed public errors / no snapshot JSON | Inherited DelayedReplyPublicError at Service.kt:26-44 has the exact ten approved code/message mappings; unknown values map to SNAPSHOT_INVALID. ControllerTest.kt:40-47 asserts the exact public field set without snapshot/originalInput/credentials; :105-126 checks UNKNOWN/permanent retry rejection and the fixed error messages, including opaque mapping of an unknown private-looking stored string. Public list/get/action responses are views, not snapshots/evidence/credentials. Restore-only input is separately authorized. |

Search receipts for boundary claims: targeted grep of `finalizeSuccess\(|finalizeFailure\(|frozenAssembly|archiveLiveUnsupportedAnswers` in Pending returned result sites1137/1186/1230/1261, routing adapters2354/2362, and archive consumers1206/1555/1571; their complete function context and the receiving attempt finalizers were read. Targeted grep of `fun (claim|retry|preflightFailure|recoverStale|view|lockDelayed|finalizeDelayed)` in the lifecycle/attempt/source services located claim364, retry287, preflightFailure403, recovery425, and the concrete source/ref and finalizer consumers read above. Scope counts come from the exact Git inventory, not an assumed call-site total.

#### A1-A4 runtime acceptance evidence and limits

| Child acceptance | Fresh evidence |
|---|---|
| A-1 close page/restart/deadline | Worker is server-side and independent of any browser/session request or entry switch. Fresh tests reconstruct a new worker from decoded persisted historical JSON, replay frozen MIME/ICS/ordered attachments once, and exercise59999/60000 and busy capacity. This is component restart/deserialization evidence, not an observed full application kill/restart or browser-close deployment exercise. Full integrated/process acceptance remains07. |
| A-2 permissions/deadline | Fresh real MockMvc controller requests plus actual HandlerMethod/resource policy and isolated MySQL permission metadata exercise READ state visibility, false capabilities, denied restore/actions, cross actor/contact and REVIEW admin restrictions, exact late-cancel409 wording, and malformed revision422. |
| A-3 unknown/context | Fresh actual loopback SMTP DATA disconnect yields UNKNOWN; a completion failure also yields UNKNOWN, one SMTP each and no subsequent redelivery. Controller rejects retry for UNKNOWN. Review context/author authorization failures stop real Pending rehydration before SMTP; real lifecycle IT separately verifies review context/revocation admission and claim at :957-995. These are complementary component/SQL proofs, not one full controller-to-MySQL-to-SMTP application chain. |
| A-4 restoration/old compatibility | Fresh round-trip/restore requests retain raw non-UTC meeting fields, original assembly frame/request-fact selections and ordered attachments; READ restore403, historical missing-input422 without a guessed copy, and old frozen payload actual SMTP delivery are exercised. QA/assembly and RAG/adoption metadata remain separate restore cases. |

The new live path was exercised by the retained WorkerTest loopback harness, so no throwaway harness was added. This harness executes the real worker, Pending rehydrate/deliver, SmtpMailDeliveryService and JavaMail, with controlled lifecycle/attempt collaborators for claim/completion injection. The required real MySQL lifecycle40 tests separately prove the transactional/CAS boundary. Historical implementer artifact://2227 was read as context only; the results below are fresh verifier evidence and do not claim full E2E.

#### Fresh command evidence

All commands ran in the retained worktree above with JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Actual Java version command returned OpenJDK11.0.15 / Zulu11.56+19-CA, build11.0.15+10-LTS. Fresh Testcontainers logs show OrbStack, Docker29.8.2, mysql:8.0.36, isolated localhost:33501/resource_guard and localhost:33503/delayed_reply; no default development/production datasource or production SMTP was used.

| Required command (each executed once, in this order) | Exit | Counts/skips and receipt |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -DskipTests test-compile` | 0 | Completed successfully with no quiet output; job bg_123, wall183.84s. Compilation only, no test-execution count claimed. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest="DelayedManualReplyWorkerTest,DelayedManualReplyControllerTest,ExpertMailPermissionGuardTest,DelayedManualReplySnapshotTest"` | 0 |43 JVM tests, failures0/errors0/skipped0: Worker9, Controller12, Guard11, Snapshot11. Bound JS2038 passed/failed0/skipped0 and bound JS syntax commands succeeded. BUILD SUCCESS, finished2026-11-05T10:45:01+08:00. Full output artifact://2242. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true` | 0 |40 actual MySQL lifecycle tests, failures0/errors0/skipped0; not gated off. Bound JS2038 passed/failed0/skipped0 and bound JS syntax commands succeeded. BUILD SUCCESS, finished2026-11-05T10:49:24+08:00. Full output artifact://2244. |

Fresh artifact://2242 smoke markers:
- disconnect=true completionFailure=false result=UNKNOWN smtp=1 token=smoke-token
- disconnect=false completionFailure=true result=UNKNOWN smtp=1 token=smoke-token
- disconnect=false completionFailure=false result=SENT smtp=1 token=smoke-token

The warnings logged for the injected DATA/completion failures are expected scenario diagnostics, not test failures. No required command was omitted or rerun. No optional full suite, MailReviewQueryIT baseline reproduction, timezone test, WAR build, full-system verification or deployment was run.

#### Downstream contract handoff

- 05 consumes DelayedManualReplyView with its fixed fields, current capabilities and safe reasons; public job JSON contains no persisted snapshot/evidence. Query/UI attachment dispatch remains the explicitly separate05 work.
- 06 receives id/expectedCancelledRevision/originKind/originId plus the authorized originalInput from restore. LEGACY is the original typed request, including raw assembly/frame/matrix and original MeetingInput; old missing restoration data gives422 only on restore, not on frozen delivery. DRAFT source restoration stays typed and REVIEW has no editable copy. No frontend implementation or browser-whitelist expansion was performed early.
- 07 can consume Prepared.originalInput, snapshot factory, the inherited claim reference and completed delayed-result seam; the current public-send activation still intentionally stays synchronous until07. A3/A4 review revision propagation is still the approved07 responsibility. Actual full controller/session→prepare→MySQL queue→worker→atomic finalize→query/SMTP E2E and full process/browser acceptance remain07; this report neither waives nor claims them.

### AUTO_FIX
- N/A. No proven four-gate defect requiring an authorized plan-directed correction was found. No product/test/index/commit/plan change was made by this verifier.

### RECORD_ONLY
- O-1: Runtime evidence is intentionally component SMTP plus separate real-MySQL lifecycle/CAS proof, not full application E2E or observed process/browser restart. Evidence: WorkerTest.kt:80-200 controls lifecycle collaborators while exercising real worker/Pending/Smtp/JavaMail; LifecycleIT has its own real transaction configuration. Full integrated acceptance is assigned to07 T4 and is not waived, silently narrowed, or counted as passed here.
- O-2: The supplied MailReviewQueryIT:239 baseline and optional tzdata failures are outside these required commands. Neither was repaired, reproduced or rerun; no fresh result for them is claimed. They are not unresolved04 authority or repair items.

### Required Action
- COMPLETE_CHILD
