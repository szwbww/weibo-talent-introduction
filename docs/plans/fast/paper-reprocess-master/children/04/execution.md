# Child 04 Execution Log

## Epoch 1 — Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-04-apply.md
Plan SHA-256: 77f145af6d11dec017b7f52596af0713b3ec40e68ce4370be60f05c68d9e6162
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-04-apply.md@77f145af6d11dec017b7f52596af0713b3ec40e68ce4370be60f05c68d9e6162
Execution epoch: NEW (epoch 1)
Approval basis: Current child04 invocation and exact approved master commit a6e14b63448fecfa0cd1319074c8d0b5bb2026be; controller subsequently required a collective dependency audit and PLAN_CONFLICT handoff before product edits so that the necessary scope amendment is recorded.
Executor: PaperApplyImplementer04
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
Target branch: fast/paper-reprocess-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master@fast/paper-reprocess-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master
Pre-execution code SHA: ff7e67506545a31bc38a3d3f674056c522c57917
Pre-execution metadata HEAD: 7520bcd06d7f6218783d12817d8efaf3deb94361
Post-execution code SHA: N/A — no product/test edit or commit
Evidence HEAD: N/A — this append remains for the controller artifact checkpoint
Implementation boundary: No child04 implementation boundary; target src working tree and index were clean at the native handoff check. Existing controller-owned ledger.md changes were preserved.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Exact plan/worktree identity and prerequisite reconciliation | IMPLEMENTED | Read-only plan, brief, child03 execution/fix/verify artifacts and actual service/repository interfaces | Full 113-line approved child read; plan identity returned the approved hash at entry and handoff; strict native assertions bound the exact target and confirmed base/master ancestry. |
| I-2 durable TARGET proposal/before/after receipts, crash recovery, I-4 REVIEW lifecycle and persistent statistics | CONFLICT | Necessary unlisted DiscoveryPaperReprocessRepository.kt | Existing repository only persists a result through terminal completeItem; retry deletes incomplete TARGET rows and clears result JSON; interruption/cancel synthesizes replacement results; summary leaves business count DTO defaults at zero. Exact receipts below. |
| I-1/I-2/I-3 existing consume-chain per-effect permission and proved-owner recovery | CONFLICT | Necessary unlisted ExpertDiscoveryService.kt | Parsed permit is checked once before entry, while reserve/RAW/enrichment/activate/person effects occur later without intervening parent checks. Duplicate recovery enters a generic repair path, not an exclusive proved-parent-owned resume. Exact receipts below. |
| I-3/I-4 parent permission before feature-owned ordinary worker dispatch | CONFLICT | Necessary unlisted DiscoveryVerificationWorker.kt | ensureOwned and the explicit academic dispatch ownership branch are conditioned on reviewEpoch > 0; the new ordinary items use epoch 0. Repository changes alone do not insert the required last-moment worker permission check. Exact receipts below. |
| Permit factory, frozen internal registration/recheck, APPLY orchestration, cancellation/compensation and all required tests/runtime | PENDING | Original ten-file plan plus the necessary amended seams | No implementation started. No acceptance criterion is reduced, marked satisfied by historical output, or delegated to child06. |
| Product commit and completed execution evidence | PENDING | Authorized product files; this execution artifact | No staging/commit; no completed implementation or READY claim. |

### Collective Necessary Amendment and Search/Read Receipts

All paths below are repository-relative. Specialized grep calls used absolute paths rooted at the exact target above; line references are the currently inspected baseline, not proposed future locations.

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`
   - Exact discovery query: `functions.grep(pattern="fun |data class|ordinary|registration|parent", path="<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt", case=true)`. Relevant returned declarations: `239 fun claimItem`, `252 fun completeItem`, `273 fun addTarget`, `291 fun setPhase`, `300 fun finish`, `350 fun cancel`, `362 fun retry`, `394 fun releaseRejected`, `401 fun summary`, `433 private fun terminalizeItems`. The displayed declarations expose no in-flight result/proposal receipt update or nonterminal REVIEW handoff.
   - Exact follow-up query: `functions.grep(pattern="fun summary|actualNew|actualUpdated|actualAdmitted|result_json|fun terminalizeItems|result =|result_json=NULL", path="<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt", case=true)`. Relevant output:
     - `265 UPDATE discovery_paper_reprocess_item SET state=?,result_json=?,lease_token=NULL,lease_until=NULL`
     - `379 UPDATE discovery_paper_reprocess_item SET state='PENDING',result_json=NULL,lease_token=NULL,`
     - `401 fun summary(runId: Long): ReprocessSummary`
     - `433 private fun terminalizeItems(...)`
     - `441 val result = json(errorResult(reason, state, item.input), "RESULT_TOO_LARGE", result = true)`
   - Exact reads: `:239-313,478-514`, `:207-239,314-393`, `:401-450`. These show completeItem requires ITEM_TERMINAL at255; retry378 deletes TARGET with state<>'COMPLETE'; retry379–381 clears results; terminalizeItems441 replaces rather than merges evidence; summary405–413 only counts paper/target item states and does not populate actual business increments.
   - Required bounded contract: lease-fenced durable proposal/source/before/after receipts before and after existing effects; preserve ENFORCE receipts/ownership across interruption/cancel/retry; keep AUDIT regeneration behavior where applicable; support resumable nonterminal REVIEW and SQL-derived per-run business totals. Preserve schema1/six steps/4MiB guards and parent→run→item lock order. No additional business writer or migration is justified by this finding.

2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
   - Exact query: `functions.grep(pattern="verificationRegistration|indexToRaw|enqueueEnrichmentJob|registerPerson|recordIdentityDuplicate|archivedPaper", path="<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1868-1882;<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:1935-2059;<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt:2524-2583", case=true)`. Relevant output:
     - `1871 val archived = requireNotNull(newExecutionContext.archivedPaper)`
     - `1990 val disposition = recordIdentityDuplicate(duplicate, authorEmail, sourceStats, sourceName, executionId, sourceItemKey)`
     - `1998 verificationRegistration?.reserve(verification, identity.esDocId, identity.profile, sourceName, sourceItemKey,`
     - `2013 if (!expertIndexWriterService.indexToRaw(identity.esDocId, profileMap))`
     - `2021 enqueueEnrichmentJob(identity.esDocId, sourceName, executionId, sourceStats)`
     - `2023 verificationRegistration?.activate(registration)`
     - `2038 personCounts.fold(registerPerson(sourceName, identity.esDocId, sourceStats))`
     - `2528 verificationRegistration?.recoverDuplicate(existing.docId, sourceName, sourceItemKey)`
     - `2581 val person = registerPerson(sourceName, docId, stats)`
   - Exact reads: `:1868-1884,1935-2060`, `:2500-2588`. Permit requireMatches1877 occurs before the shared consumer; duplicate2557 invokes `identityRepairService?.repairDuplicateIdentity`, with existing identity mismatch handling2541–2552. Registration recovery is separately inspected at DiscoveryVerificationRegistrationService.kt:270–326: it reads source/identity and activates a matching reservation but does not establish a parent-owned end-to-end consumption receipt.
   - Required bounded contract: the existing archived-consumer path must carry a server-minted, non-HTTP parent permission and check it immediately before each existing side effect; proved-parent-owned RAW/reservation recovery resumes the existing enrichment/activation/person chain without writing RAW twice or routing arbitrary existing documents through generic duplicate repair. Other ordinary discovery/ORCID/queue semantics remain unchanged. No second consumer, writer, promotion, identity rule or qualification rule.

3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
   - Exact query: `functions.grep(pattern="fun ensureOwned|fun ensureDispatchAllowed|reviewEpoch > 0|ensureDispatchAllowed\\(|isRunAccepting|lockRunAccepting|lockForCommit", path="<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt;<TARGET>/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt", case=true)`. Relevant output:
     - Worker `206 fun ensureOwned()`
     - Worker `207 if (lost.get() || lease.reviewEpoch > 0 && !repository.isOwned(...)) throw LeaseLost()`
     - Worker `209 fun ensureDispatchAllowed()`
     - Worker `219 ensureDispatchAllowed()`
     - Worker `252 if (lease.reviewEpoch > 0 && !repository.isOwned(...))`
     - Worker `514 ensureDispatchAllowed()`
     - Worker `515 if (lease.reviewEpoch > 0 && !repository.isOwned(...))`
     - Worker `575 beforeDispatch = { ensureDispatchAllowed() }`
     - Worker `647 ensureDispatchAllowed()`
     - Worker `760 ensureDispatchAllowed()`
     - Commit `65 if (lease.reviewEpoch > 0 && !repository.lockRunAccepting(...)) return null`
     - Commit `66 val item = repository.lockForCommit(lease.id)`
   - Exact reads: Worker `:204-240,350-420`; VerificationRepository `:854-933,964-975,1153-1165,1243-1259`. Ordinary items pass the existing claimDue858 epoch0 branch; owns1257 treats epoch0 as run-accepting; lockForCommit1156–1163 is an existing shared repository seam through which feature-only parent locking/acceptance can protect commit without changing the CommitService file.
   - Required bounded contract: only positively identified paper-reprocess-owned epoch0 items gain the parent permission check at worker dispatch/late-response boundaries; their repository operations use the fixed parent→verification-run→item order and reject cancelled/expired parent authority. Preserve unrelated epoch0 worker behavior, provider strategies, paid/free flags, existing recheck identity supplement and existing commit/projection rules.

The collective audit establishes these added paths together, not as incremental writer improvisations. No further unlisted path is already proven unavoidable:
- Existing `DiscoveryPaperReprocess.kt:51-53` already exposes actualNew/actualUpdated/actualAdmitted; the existing result/scope JSON can separately retain already-admitted and projection receipts. A new DTO field is not asserted necessary by this audit.
- `ExpertIndexWriterService.findDiscoveryHitsByEmail` is an existing three-layer lookup (grep `fun findDiscoveryHitsByEmail`, line727; read surrounding contract) and existing readDiscoveryDocument/discoveryProfile supply document/version input. No new ES mapping or writer is required.
- Internal frozen settings/ownership/ref validation can remain in the already authorized RegistrationService, RecheckService and VerificationRepository. Public request DTOs need not gain settings or callbacks.
- Exact direct-test dependency query `functions.grep(pattern="APPLY_NOT_AVAILABLE|ENFORCE|actualNew|CODE_VERSION|DiscoveryPaperReprocessService\\(", path="<TARGET>/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt;<TARGET>/src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt", case=true)` found ServiceTest184 constructs the six-dependency AUDIT fixture and372–373 expects unavailable ENFORCE. Preserving an explicitly unavailable apply dependency in that isolated fixture can retain this assertion; it does not force an unlisted test change. New behavior/failure injections can be covered in the authorized ApplyServiceTest and IntegrationTest. No test weakening is authorized.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-11-01/paper-reprocess-04-apply.md` | Exit0 at entry and handoff | Same canonical plan; SHA-256 unchanged77f145af6d11dec017b7f52596af0713b3ec40e68ce4370be60f05c68d9e6162;13971 bytes. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-11-01/paper-reprocess-04-apply.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master` (handoff adds exact `--expect-root`, `--expect-branch`, `--expect-git-dir`) | Exit1, known helper infrastructure | Brief-documented line30 fails resolving the unrelated stale publish-worktree path ending multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b. No registration/helper/config mutation. |
| Native Python/subprocess assertions of `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse --absolute-git-dir`, `git rev-parse --git-common-dir`, `git rev-parse HEAD`, target branch ref, `git worktree list --porcelain`, base/master `git merge-base --is-ancestor`, `git diff --cached --name-only`, `git diff --name-only -- src`, `git status --short` | Exit0 | Exact root/branch/Git/common directories and registered target; HEAD/branch7520bcd06d7f6218783d12817d8efaf3deb94361; ff7e675 and a6e14b6 ancestors; empty index/product diff. Only existing controller ledger.md dirt before this report. |
| Required JDK11 four-suite Maven command with `-DmysqlIt=true` | NOT_RUN | Controller required conflict handoff before implementation. No baseline failure or passing result is fabricated. |
| Discovered SQL/runtime opt-ins, actual isolated consumer/recheck/worker/commit/projection smoke | NOT_RUN | Existing IntegrationTest41 uses mysqlIt; reprocess runtime fixtures use paperReprocessIt at ServiceTest130/232. Future execution must enable applicable flags and run a separate actual process, not rely only on tests. |
| Build/lint/formatter/staging/commit/provider operations | NOT_RUN | No product edits or mid-flight checks; no real provider/production calls. |

### Changed Files

- `docs/plans/fast/paper-reprocess-master/children/04/execution.md` — append-only canonical conflict report and collective dependency evidence.
- No implementation/test/plan file changed; no new file, product commit or evidence commit.

### Deviations

- The original ten-file allowlist cannot implement the exact required durable receipt and per-effect parent-authority contract without the necessary repository/consumer/worker seams above. The controller has directed a cap13 master/child amendment and a new exact-plan execution epoch before any product edit.
- Known worktree-helper defect used the explicitly authorized strict native fallback.
- One downstream search initially used a nonexistent plan suffix05-ui and returned a missing-path notice; a directory read discovered the actual05-frontend path, then the targeted search succeeded. No conclusion relies on the missing path.
- No acceptance reduction, legacy CLI mode change, ordinary-epoch0 broadening, qualification/provider policy change, production operation, push/merge/history rewrite, independent verification or child05/aggregate-review execution.

### Freshness

- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, native assertions succeeded.
- Reported new commits reachable from target branch: N/A — no new commit.
- Existing base/master/current metadata HEAD reachable and target-bound: YES.
- Required implementation commands run this invocation: NO; not eligible for READY_FOR_VERIFICATION.
- Historical evidence used only as baseline: YES.
- Product working tree/index cleanliness checked before report: YES; controller ledger changes preserved.

### Remaining Blocker

The controller must record the exact necessary scope/contract amendment in master/child/brief, bind its new immutable identity and redispatch execution. Current authority explicitly requires this artifact boundary before edits, despite standing user authority covering the same-objective seams. The entire implementation, fresh required commands, isolated runtime evidence and authorized product commit remain pending.

### Next Action

PLAN_CONFLICT → controller amends and checkpoints the exact cap13 plan/brief; the next executor reads those complete bytes anew and implements the entire amended child04 acceptance, without inheriting a completion claim from this read-only epoch.

## Epoch 2 — Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-04-apply.md`
Plan SHA-256: `9d2899342bc0d9cbb6336930864a7c23220224f310453e95b339b7cbcc45bb61` (15896 bytes).
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-04-apply.md@9d2899342bc0d9cbb6336930864a7c23220224f310453e95b339b7cbcc45bb61`.
Execution epoch: NEW — current approved epoch2; epoch1 above is retained, not reused as completion evidence.
Approval basis: exact master+child commit `5a31faaebf28001fb05d34c6b2aa195cc2a0e01d`, amendments A8/A9 and current invocation.
Master SHA-256: `a3679e54a439fd8095f0788a7c42359a2cfef9f82eb52dc31d95562da21c55ac` (27768 bytes).
Executor: `PaperApplyImplementer04E2`.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`.
Target branch: `fast/paper-reprocess-master`.
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master@fast/paper-reprocess-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`.
Pre-execution code SHA: `ff7e67506545a31bc38a3d3f674056c522c57917`.
Pre-execution metadata HEAD: `0b5c779d7859212692b91e020dccb1a9c95d6c19`.
Post-execution code SHA / current product HEAD: `dff7585906d8a628c87e4c385dba4c822f72e576`.
Product subject: `feat(fast-p): implement 04`.
Evidence HEAD: no separate evidence commit created; this append and pre-existing controller ledger dirt remain excluded from the product commit.
Implementation boundary: `0b5c779d7859212692b91e020dccb1a9c95d6c19..dff7585906d8a628c87e4c385dba4c822f72e576`; prerequisite product base remains an ancestor.

### Task Status

| Requirement | Execution status | Implementation / exercised evidence |
|---|---|---|
| Private server archive/parser authority, independent identity and qualification coverage | IMPLEMENTED | Private permit constructor; exact frozen TARGET/PAPER/source/attempt/body/metadata validation and byte revalidation. Actual XML text-limit fixture has complete author identity, qualification PARTIAL, one RAW and zero admissions; legacy permit still rejects the partial source. |
| New-author sole original consume chain and owned resume | IMPLEMENTED | `consumeArchivedPaper` continues into the existing consumer, registration, RAW, enrichment and person paths with per-effect parent checks. After-RAW crash resumes only the matching parent-owned reserved identity; no duplicate RAW/person/enrichment registration. Foreign same-identity RESERVED item remains unchanged and parent creates no RAW. |
| Existing-author sole child02 repair and confirmed recheck | IMPLEMENTED | Adapter creates internally owned original rechecks; retained identity worker/CAS/commit/project remain the business writers. Original retained PDF repair is exercised against real SQL and versioned loopback ES. |
| Frozen parent Apollo/policy/evaluation context | IMPLEMENTED | Ordinary and recheck settings derive from the parent snapshot; feature-owned ordinary work uses REFRESH without broadening unrelated epoch0 defaults. Parent frozen search=true/detail=false survives globals changing to true/true; fixture refuses any paid detail dispatch. |
| Durable before/proposal/source/version/action/child/admission/projection receipts | IMPLEMENTED | Immutable TARGET input, proposal and before snapshot persist before effects; original consumer checkpoints registration/effects; child id persists before READY start. Actual RAW/prepare/start/admission-observation crash recoveries preserve receipts and single owned registration. |
| Sorted bounded deterministic children and REVIEW waiting | IMPLEMENTED | Actual 101-document old roster produces sorted groups 100,1 with parent/doc-list-derived request keys and persisted child ids. Busy independent READY roster is not cancelled; parent remains nonterminal REVIEW with WAITING_RECHECK_SLOT and resumes after its owner releases the slot. |
| Actual owned terminal states and independent effect counters | IMPLEMENTED | Parent waits for all owned ordinary/recheck items, observes real admission/projection outcomes and retains exclusive target outcomes. Real SQL supplies committed admission/repair counts even when a target acknowledgement is interrupted; original source task/archive/history are preserved. |
| Cancel ownership, dispatch/late-response/prepare/start/activate fences | IMPLEMENTED | Runtime exercises cancellation after ordinary activation, after child preparation, after child start and during provider response. Owned queued work is cancelled, late preparation/start/activation is rejected, committed RAW/admission remains, and an unrelated running recheck remains untouched. |
| Existing projection retry and old CLI/default compatibility | IMPLEMENTED | Forced candidate projection failure retains admission; existing `commits.project` retry succeeds with provider-request delta zero. Separate actual legacy CLI Java process exits0 and produces AUDIT/REUSE/READY without start. Existing required suites exercise ordinary/AUDIT compatibility. |
| Exact authorized file/commit boundary | IMPLEMENTED | Git staged-path assertion and committed `diff-tree --name-only` both contain exactly the 13 brief-authorized files; no plan, migration, fast-p artifact or unrelated user file is in the product commit. |

These are implementation/execution statements, not an independent machine-review verdict.

### Commands and Fresh Evidence

Required test command, executed after final product/test edits:

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true -DtrimStackTrace=false -Dstyle.color=never
```

- Final exit0, BUILD SUCCESS. JVM: 47 run, 0 failures, 0 errors, 0 skipped: ApplyServiceTest2, BackfillTest4, RecheckServiceTest10, VerificationIntegrationTest31. Maven-bound JS: 2018 tests, 2018 successful, 0 failed/skipped; bound app/task-modal syntax checks also executed. Fresh output: `artifact://456`, especially totals near6692–6716.

Separate actual runtime process, after final edits (not a Surefire-only result):

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke' -Dstyle.color=never
```

- Exit0; `PAPER04_RUNTIME_OK` at `artifact://462:1655`. Separate MySQL8.0.36 container `bdd3ce63bb55b383b1ea35d0a2d4dd17926fa7ff293d4b69963609ec31d52b04`, isolated JDBC `jdbc:mysql://localhost:32944/verification_registration`; Flyway applied172 migrations throughV173. Container and temporary fixture roots were cleaned by the runtime.
- Initial main scenario parent30712, loopback ES `http://127.0.0.1:60064`: new1, repaired1, actualAdmitted1, pending1; duplicate same-email memberships across three papers create one ordinary item/RAW, no new-person recheck; repeat parent new0. Original source SHA `488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7`. Archive metadata/first outcome and source task row compare unchanged.
- Four recovery checkpoints execute actual consumer/recheck/worker/commit/project: AFTER_RAW, AFTER_PREPARE, AFTER_START, AFTER_ADMISSION_OBSERVED. Each finishes with one RAW write, one owned old-document recheck and one admitted verification item. Runtime marker wording `admissionEvents=1` is based on the fixture's admitted-item SQL count, not a separately counted event-audit table.
- Cancellation checkpoints preserve the committed-at-cancel quantity, not an assumed one: AFTER_PREPARE observed0, AFTER_START observed1; both retain their durable child registration, cancel only owned work and reject late prepare/start. Provider-response cancellation retains the previously committed admission1 and produces no late admission. Independent foreign running roster remains unchanged.
- Sorted old-document groups100,1, deterministic keys, durable child-before-start and busy-slot REVIEW waiting execute in the same separate runtime. Source-complete/qualification-PARTIAL fixture remains unadmitted. Conflict/APPLICATION targets cause zero writes; foreign same-identity reservation has zero parent RAW/enrichment writes and unchanged owner.
- Actual legacy `DiscoveryHistoricalReplayTool apply-bindings` runs in its own JDK11 subprocess using explicit isolated SQL/ES environment, exits0 and leaves AUDIT/REUSE/READY without start. Original private inputs remain outside Git under `/Users/lukai/.local/share/paper-reprocess-30711`; synthetic archives/manifests/logs use deleted temporary fixture directories.

Additional final command: `git show --format= --check dff7585906d8a628c87e4c385dba4c822f72e576` → exit0.

### Earlier Self-Check Failures and Corrections

All failures below belong to this implementation epoch and are retained rather than recast as historical passing evidence:

- `artifact://439`: test compilation rejected guessed provider constructor slots and `itemDetail`; aligned the authorized integration fixture with actual consumer provider types and existing `queries.item`.
- `artifact://441`: 46 tests, five new runtime setup errors INVALID_SCHEMA; added the required schemaVersion1 to synthetic archive metadata.
- `artifact://444`: 46 tests, two errors. After-RAW resume rejected RAW operational/classification metadata because it compared whole profile objects; changed only its owned-source check to the existing commit canonical identity/fact fingerprint while preserving parent/item/source/proof checks. Late-response cancellation assertion incorrectly demanded a child already exist; cancellation may prevent its creation, so assertions now require no owned active work and preserved committed admission.
- `artifact://447`: new partial-coverage fixture compilation needed the actual archive enum import and a stable local result variable.
- `artifact://449`: foreign-reservation fixture attempted `copy` on a non-data properties class; used its actual explicit constructor.
- `artifact://451`: 47 tests, one fixture error: cancel-after-prepare assumed admission1 although cancellation may win before ordinary commit. It now captures the actual committed count at the completed cancellation boundary and proves it remains unchanged.
- Final required suites and separate runtime above were rerun after the last edit and exit0; no current known in-scope self-check failure remains.

### Changed Files

Product commit contains exactly:

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt` — internal apply/observe/owned-child adapter.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt` — frozen ENFORCE adapter and original parent lifecycle integration.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillService.kt` — private byte/parser permit, unchanged legacy strict entry.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — frozen ordinary registration, per-effect permission/receipt and owned-only crash resume.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` — internal frozen parent preparation/start, existing public defaults retained.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — parent/run/item ordering, feature-owned permission, cancellation and late diagnostic fences.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` — durable receipt/REVIEW/target resume and actual committed counters.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — existing consumer per-effect parent checks/owned resume only.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — parent-owned ordinary dispatch/late permission, no unrelated epoch0 widening.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyServiceTest.kt` — adapter preconditions/private public-contract boundaries and isolated JDBC configuration.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillServiceTest.kt` — exact legacy permit source/metadata regressions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt` — malformed internal bounded roster rejection before SQL.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` — real SQL consumer/recheck/worker/commit/projection fixtures, standalone runtime and actual legacy CLI process.

Scope receipt: native Git `diff --cached --name-only` at staging and `diff-tree --no-commit-id --name-only -r dff7585906d8a628c87e4c385dba4c822f72e576` at commit matched the exact brief allowlist, count13. Only this append is executor-owned control-plane dirt; pre-existing ledger/report content was preserved.

### Deviations and Runtime Limits

- Known worktree helper line30 failed on the brief-listed unrelated missing publish worktree. Invoked the helper, then used authorized strict native root/branch/Git/common-dir/registered-target/HEAD/ancestry assertions at stage/commit/handoff. No helper/config/worktree registration was changed. One native status-path assertion initially trimmed the first line's status prefix; retried only that read-only assertion with preserved leading whitespace, then exact scope checks succeeded.
- ES is a strict loopback fixture implementing the invoked full-document search/read, create and version-CAS routes, not a deployed Elasticsearch engine. SQL task/archive/verification/admission/person/enrichment persistence and original business services are real. Provider HTTP is intercepted deterministically: ORCID self-employment documents and empty Apollo search; detail requests fail the fixture. Fixed eligibility/email checks and empty contact/alias collaborators are fixture boundaries. No live provider, paid detail call, production instance or production write was used.
- Projection recovery exercises the existing `commits.project` seam with zero qualification/provider rerun; no new retry API or qualification rule was introduced. This evidence does not claim production-provider accuracy or full Elasticsearch compatibility.
- No applied migration, public DTO/HTTP authority, second business writer, plan/ledger edit, production operation, push, merge, history rewrite, child05 implementation or aggregate/independent review was performed.

### Freshness and Next Action

- Exact child/master identities unchanged; child identity recomputed after the final product commit.
- Strict native worktree/branch/HEAD/ancestry checks bind the product commit to the required target; approved metadata and prerequisite base remain ancestors.
- Required commands and separate actual runtime ran freshly after the final product/test edit; only authorized execution artifact text changed afterward.
- Product commit excludes every fast-p artifact. Index/product cleanliness and allowed controller dirt are checked at final handoff.
- Remaining implementation blocker: none known from exercised execution.
- READY_FOR_VERIFICATION → controller checkpoints this report and dispatches the separate fresh child04 verifier against `dff7585906d8a628c87e4c385dba4c822f72e576`. No independent verdict, review-fast-p or later child is started here.


## Epoch 2 — Controller Terminal Evidence Closure
- Implementation `dff7585906d8a628c87e4c385dba4c822f72e576`; round1 `503e7e51bff189d57bf9da6bad26015e930c7478`; round2/terminal code `736535ce9487782e52a73af6cb52517fa799b194`.
- Fresh independent PaperApplyReverifier04E2R2 epoch2 attempt3 returned LIGHT_PASS_WITH_NOTES for `ff7e67506545a31bc38a3d3f674056c522c57917..736535ce9487782e52a73af6cb52517fa799b194`; F01–F05 resolved. O01 fixture boundary, O02 generic task summary and O03 historical overlapping projection retry remain RECORD_ONLY.
- Fresh evidence:52 JVM tests,2018 Maven-bound JS tests and separate actual MySQL/transaction-proxied runtime including genuine FAILED/STALE person-action retry; earlier committed effects retained and no qualification/provider replay. Loopback ES/deterministic providers, not live ES/provider/browser; original PDF unchanged.
- Controller records these reports, not an aggregate review or human acceptance. Artifact checkpoint required before05.
