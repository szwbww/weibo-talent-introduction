# Repair Execution Evidence — discovery-identity-recovery-master

## Execution identity

- Approval source (exact human invocation): `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`
- Plan canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`
- Plan SHA-256: `845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5` (61,462 bytes) — unchanged at start and at handoff.
- Execution ID: `<plan-path>@845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5`
- Execution epoch: **NEW** (no prior `repair-execution.md` existed; no prior commit named this plan identity).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Target branch: `fast/2026-10-13-discovery-identity-recovery-master`
- Worktree ID: `<worktree-root>@fast/2026-10-13-discovery-identity-recovery-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Executor: `execute-p` main agent session (integrator) with three delegated implementation groups executed as in-process task agents — `RepairDiscoveryCore` (R-3..R-5), `RepairQueryUi` (R-9..R-12), `RepairOutreach` (R-6..R-8) — all diffs reviewed and integrated by the executor; R-1, R-2, R-13..R-18 implemented directly by the executor.
- Pre-execution code SHA (clean worktree, verified before any edit): `e60ed1379af8049853160d32f3305e0e811c618b`
- Post-execution code SHA (single product commit): `b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9` — subject `fix(discovery): repair person identity and outreach invariants`
- Implementation boundary: `e60ed13..b08b521`

## Task status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| R-1 V-1 default-off switch + irreversible protection | IMPLEMENTED | DiscoveryPerson.kt, DiscoveryPersonService.kt, DiscoveryIdentityRepairService.kt, DiscoveryPersonQueryService.kt, DiscoveryPersonRecipientService.kt | `DiscoveryPersonFeatureGate` (`talent-introduction.discovery-person.enabled`, default `false`) consulted by 05 registration (`DiscoveryPersonRegistrationService.refresh` → `SKIPPED/FEATURE_DISABLED`), 06/07 display (per-archive fallbacks in search/count/aggregations/detail + `resolvePersonIds`), 08 targeting (per-doc units). 09 guard un-gated (protection stays effective when the switch is off); 10 maintenance explicitly exempt. No `application.yml` change (switch configuration modification is prohibited by the plan). |
| R-2 V-2 live discovered scope | IMPLEMENTED | DiscoveryPerson.kt (`DiscoveryPersonScope.isLiveDiscovered`), DiscoveryPersonService.kt:155, DiscoveryIdentityRepairService.kt facts, DiscoveryIdentityRecoveryService.kt:190, DiscoveryPersonMaintenanceService.kt readMember, DiscoveryPersonQueryService.kt isStale | Predicate requires discovery-source evidence **and** literal `discovered` tag; removing the tag invalidates relation/registration/recovery/maintenance application (`NOT_DISCOVERY`/`STALE`). Legacy `isDiscoveryOutreach` breadth untouched. |
| R-3 V-3 whole-group re-verification | IMPLEMENTED | DiscoveryPersonService.kt, DiscoveryPersonServiceTest.kt | Group re-verified from current ES on every attempt (incl. conflict retries); canonical full-name consistency (`GROUP_NAME_MISMATCH`); existing root's current members/author keys included in the per-class uniqueness check (`EXISTING_AUTHOR_KEY_CONFLICT`); bridge of two roots still `NEEDS_REVIEW`. C08 20/20. |
| R-4 V-4/V-19 explicit failure + resumable checkpoints | IMPLEMENTED | DiscoveryIdentityRecoveryService.kt, DiscoveryIdentityRepairService.kt + tests, replay.py, test_replay.py | Protection read exception ⇒ zero writes (`CONTACT_CHECK_FAILED`); registration STALE/FAILED ⇒ `PARTIAL`, never complete `APPLIED`; `inspect` compares against this request's deterministic target (same original request resumable; third value ⇒ `NEEDS_MANUAL:EXISTING_PROOF`); CLI treats PARTIAL/STALE as resumable with a fresh checkpoint. C05 163/163, C06 15/15. |
| R-5 V-20 PMCID source identity | IMPLEMENTED | DiscoveryIdentityRecoveryService.kt + test | JATS `article-id` must equal the stored locator; mismatch signs no proof. C05. |
| R-6 V-5 whole-person hard protection + pre-send eligibility | IMPLEMENTED | DiscoveryPersonRecipientService.kt, BatchRecipientSelectionService.kt (`selectProtection`), DiscoveryPersonOutreachGuardService.kt, ManualExpertMailService.kt + 4 tests | Whole valid group read first (all layers) so hidden/ineligible HOLD members block; unmapped live-discovered profile excluded (`PERSON_MAPPING_MISSING`); unreadable member fails closed; `reserve` (incl. SAFE_RETRY branch) and `claimSending` re-check live tag/group/revision/recipient/eligibility; unknown profile of a known person no longer degrades to an unguarded mail. C16 245/245, C17 263/263. |
| R-7 V-6 shared person set + default address | IMPLEMENTED | DiscoveryPersonRecipientService.kt, ManualInitialOutreachService.kt, OutreachTargetIterator.kt + 4 tests | Estimate/execute/cross-page/retry share the person set (`personKeysOf` after the full decision); iterator consumes real person keys; primaryDocId email preferred only when eligible. C02 219/219, C16. |
| R-8 V-7/V-8/V-9 persistent conservative lifecycle | IMPLEMENTED | DiscoveryPersonOutreachGuardService.kt (REQUIRES_NEW on reserve/reserveIntroduction/claimSending/markUnknown/markSafeRetry; `markSent` stays REQUIRED to stay coupled with the success fact), ManualInitialOutreachService.kt, InitialOutreachService.kt, ManualExpertMailService.kt, ManualOutreachTxHelper.kt + tests | Pre-SMTP delivery right persists independently of the business transaction; SAFE_RETRY only for provably-not-sent exits (no inference from FAILED/TRANSIENT); post-reserve pre-SMTP exits cleaned; SENDING/UNKNOWN never timeout-released. C02/C16/C17. |
| R-9 V-10 deep pagination | IMPLEMENTED | DiscoveryPersonQueryService.kt, ExpertSearchService.kt + tests | Merge prefix read in ≤1000-item pages with `from` advanced per page; exact non-discovered count kept; `from=1000,size=50` returns page 21; shallower pages keep ordering. C12 143/143. |
| R-10 V-13 reject incomplete ES results | IMPLEMENTED | ExpertSearchService.kt (`ExpertSearchUnavailableException`), DiscoveryPersonQueryService.kt + tests | `timed_out`/`_shards.failed`/missing body/incomplete scroll terminate with an explicit unavailable failure; total published only after a complete traversal; scroll closed on every failure path. C12. |
| R-11 V-11 DB contacts grouped only by verified relations | IMPLEMENTED | DiscoveryPersonQueryService.kt (`resolveVerifiedPersonRelations`), ExpertContactManagementController.kt + tests | Only currently verified relations group; ordinary contacts keep the original contactId unit (`contact:<id>`) and the original order. C12. |
| R-12 V-12 per-email admission truth | IMPLEMENTED | DiscoveryPersonQueryService.kt, app.js, discoveryPersonEmails.test.js + tests | Member admission/email status read from the unified selector + suppression truth, else `UNKNOWN`; legacy `filterResult` never means sendable; only `ADMITTED` renders as 可发送. C12/C13/C14/C15. |
| R-13 V-14 expected state covers full audit facts | IMPLEMENTED | DiscoveryPersonMaintenanceService.kt, DiscoveryPerson.kt, DiscoveryPersonService.kt, DiscoveryPersonRepository.kt, reconcile.py, test_reconcile.py + MAT | `identityDigest` binds three-layer versions, live `discovered` tag, proof source/hash/version, layer seq/primary-term, identity fingerprint, manual admission decision and history-protection judgement; relation reads/admission reads/protection reads fail explicitly (`RELATION_READ_FAILED`/`ADMISSION_READ_FAILED`/`HISTORY_READ_FAILED`) instead of degrading to "no fact"; CLI persists every original review field. C18 26/26, C19 18/18. |
| R-14 V-15 atomic protection import + conservative rollback | IMPLEMENTED | DiscoveryPersonMaintenanceService.kt (explicit `TransactionTemplate` group transaction, `GroupRollbackSignal`), DiscoveryPersonOutreachGuardService.kt (`importExistingProtection`, REQUIRED; `PROTECTED_STATES`) + MAT/GT | Existing SENT/UNKNOWN history persisted through the 09 write service inside the same group transaction; import failure rolls the group back and never reports `protectionImported`; rollback proposals refuse SENT/UNKNOWN/SENDING/RESERVED, later members and relation-version changes. C18/C19/(C17 guard IT). |
| R-15 V-16 frozen list vs selection subset | IMPLEMENTED | DiscoveryPersonMaintenanceService.kt, DiscoveryPersonMaintenanceController.kt, DiscoveryPerson.kt, DiscoveryPersonService.kt, DiscoveryPersonRepository.kt, reconcile.py, test_reconcile.py + MAT/MCT | Preview digest binds the **complete frozen list** (`frozenDigest`); apply carries `frozenDocIds` + per-group `expectedFingerprint`/`expectedRelationFingerprint`; a legitimate 50-group pilot or resume of the remaining groups passes; non-frozen ids refused; already-completed groups are recognised as `UNCHANGED` (`RESUMED_TARGET_STATE`, zero writes) while third-party identity/relation changes are refused. C18/C19. |
| R-16 V-17 exactly one disposition per frozen id | IMPLEMENTED | DiscoveryPersonMaintenanceService.kt + MAT, reconcile.py + test_reconcile.py | Preview (and apply) reject overlapping candidate groups with `OVERLAPPING_CANDIDATE_GROUPS`; `classCounts` sum equals the unique frozen denominator. C18/C19. |
| R-17 V-18 CLI session auth | IMPLEMENTED | replay.py, test_replay.py, reconcile.py, test_reconcile.py | Both CLIs accept an existing session cookie (`--cookie` / `TALENT_SESSION_COOKIE`), send it only as a `Cookie` header, refuse to run without one, surface an actionable `UNAUTHORIZED` error, and never write credentials into audit/log/file names. C06 15/15, C19 18/18. |
| R-18 V-25 read-only pins + mandatory build | IMPLEMENTED | OperatorStatusWriteSeamGuardTest.kt | Exact pins re-verified (all 10 previous noise sites re-checked; 4 shifted pins updated: ExpertContactManagementController 578→644, ExpertIndexController 91→80 and 435→477, ExpertSearchService 499→505) plus 2 newly added read-only projections (ExpertIndexController:108, DiscoveryPersonQueryService:282); write whitelist stays exactly `ExpertOperatorStatusService.kt` + `ManualInitialOutreachService.kt`; exclusion-staleness check kept. C22 exit 0. |

## Changed files (40; all inside the plan's 47 authorized files)

Product (21): `discovery/domain/DiscoveryPerson.kt`, `discovery/service/DiscoveryPersonService.kt`, `discovery/service/DiscoveryIdentityRepairService.kt`, `discovery/service/DiscoveryIdentityRecoveryService.kt`, `discovery/service/DiscoveryPersonQueryService.kt`, `discovery/service/DiscoveryPersonMaintenanceService.kt`, `discovery/controller/DiscoveryPersonMaintenanceController.kt`, `expert/service/ExpertSearchService.kt`, `campaign/controller/ExpertContactManagementController.kt`, `campaign/service/DiscoveryPersonRecipientService.kt`, `campaign/service/BatchRecipientSelectionService.kt`, `campaign/service/DiscoveryPersonOutreachGuardService.kt`, `campaign/service/ManualInitialOutreachService.kt`, `campaign/service/InitialOutreachService.kt`, `campaign/service/OutreachTargetIterator.kt`, `campaign/service/ManualOutreachTxHelper.kt`, `mail/service/ManualExpertMailService.kt`, `resources/static/app.js`, `tools/discovery-identity-recovery/replay.py`, `tools/discovery-person-recovery/reconcile.py`.

Tests (19): `DiscoveryPersonServiceTest.kt`, `DiscoveryIdentityRepairServiceTest.kt`, `DiscoveryIdentityRecoveryServiceTest.kt`, `DiscoveryPersonQueryServiceTest.kt`, `ExpertSearchServiceTest.kt`, `ExpertIndexControllerTest.kt`, `ExpertContactManagementControllerTest.kt`, `DiscoveryPersonRecipientServiceTest.kt`, `BatchRecipientSelectionServiceTest.kt`, `ManualInitialOutreachServiceTest.kt`, `InitialOutreachServiceTest.kt`, `OutreachTargetIteratorTest.kt`, `DiscoveryPersonOutreachGuardServiceTest.kt`, `ManualExpertMailServiceTest.kt`, `DiscoveryPersonMaintenanceServiceTest.kt`, `DiscoveryPersonMaintenanceControllerTest.kt`, `test/js/discoveryPersonEmails.test.js`, `tools/discovery-identity-recovery/test_replay.py`, `tools/discovery-person-recovery/test_reconcile.py`, `campaign/OperatorStatusWriteSeamGuardTest.kt`.

Not changed (authorized but untouched — no change required): `DiscoveryPersonRepository.kt`, `ExpertDiscoveryService.kt`, `DiscoveryPipelineService.kt`, `ExpertIndexController.kt`, `DiscoveryPersonRepositoryTest.kt`, `DiscoveryPipelineServiceTest.kt`, `ExpertDiscoveryServiceTest.kt`.

Forbidden files: no Flyway migration, no `application.yml`, no `index.html`, no other schema/ES mapping file was modified.

## Executor commands (fresh, on the final code state)

JDK: C01–C20 commands use `/Library/Java/JavaVirtualMachines/zulu-11.jdk` (11.0.15); C21/C22 use `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk` (11.0.32.1) per the plan's environment note. Docker commands set `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`.

| Command | Result | Evidence |
|---|---|---|
| C01 `mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test` | PASS | exit 0; Tests run 149, F0/E0/S0 |
| C02 `mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | PASS | exit 0; 219, F0/E0/S0 |
| C03 `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | PASS | exit 0; 306, F0/E0/S0 |
| C04 `mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | PASS | exit 0; 48, F0/E0/S0 |
| C05 `mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` | PASS | exit 0; 163, F0/E0/S0 |
| C06 `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` | PASS | exit 0; Ran 15 tests, OK |
| C07 `DOCKER_HOST=… mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test` | PASS | exit 0; 6, F0/E0/S0 (real MySQL, not skipped) |
| C08 `mvn -Dtest=DiscoveryPersonServiceTest test` | PASS | exit 0; 20, F0/E0/S0 |
| C09 `DOCKER_HOST=… -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0; 46, F0/E0/S0 (real MySQL) |
| C10 `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` | PASS | exit 0; 280, F0/E0/S0 |
| C11 `DOCKER_HOST=… -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` | PASS | exit 0; 26, F0/E0/S0 (real MySQL) |
| C12 `mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test` | PASS | exit 0; 143, F0/E0/S0 |
| C13 `node --check src/main/resources/static/app.js` | PASS | exit 0, no output |
| C14 `node --test src/test/js/discoveryPersonEmails.test.js` | PASS | exit 0; tests 23, pass 23, fail 0 |
| C15 `node --test src/test/js/*.test.js` | PASS | exit 0; tests 1680, pass 1680, fail 0 |
| C16 `mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` | PASS | exit 0; 245, F0/E0/S0 |
| C17 `DOCKER_HOST=… -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test` | PASS | exit 0; 263, F0/E0/S0 (real MySQL; guard suite 16 not skipped) |
| C18 `mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` | PASS | exit 0; 26, F0/E0/S0 |
| C19 `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` | PASS | exit 0; Ran 18 tests, OK |
| C21 `JAVA_HOME=zulu-11.0.32.1 mvn clean package` | PASS | exit 0; Tests run 5047, F0/**E0**/S20 — full JDK11 build gate |
| C22 `JAVA_HOME=zulu-11.0.32.1 mvn -Dtest=OperatorStatusWriteSeamGuardTest test` | PASS | exit 0; 1, F0/E0/S0 |

Retained failure (kept, not erased): the **first** C21 execution of this invocation failed with `Tests run: 5047, Failures: 0, Errors: 1` — `BatchSendTaskRuntimeIntegrationTest.retry path applies same scope filters as ES matcher` threw `ClassCastException: kotlin.Triple cannot be cast to kotlin.Pair` because R-7 changed the private `buildRetryableTargets` seam return type while that (unauthorized) test reflects the historical `Pair` shape. Repair was made **inside the authorized file** `ManualInitialOutreachService.kt` only (a hand-written legacy `buildRetryableTargets(Long, RecipientScope)` overload returning the original `Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>>`; `@JvmOverloads` removed so the reflected overload is the explicit one; the legacy typed variant and its caller updated accordingly). No unauthorized test file was modified. Focused re-run after the fix: 267 tests, F0/E0/S0, and the final C21 above is green.

C20 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk mvn clean package`) was **not** re-run in this invocation: it is retained as the original contract command/environment evidence and its old-JDK tzdb limitation (`America/Coyhaique`, `Europe/Kyiv`…) is already dispositioned as observation O-3 in the aggregate report; the plan's acceptance set is C01–C19 + C21 + C22, all of which ran freshly above (per the plan's C20/C21 environment rule).

## Deviations and disclosures

1. **R-1 gate placement.** No new product file was allowed, so `DiscoveryPersonFeatureGate` is declared inside the authorized `DiscoveryPersonService.kt`; the switch is read from `talent-introduction.discovery-person.enabled` with a Kotlin default of `false` and no `application.yml` entry (the plan prohibits modifying the switch configuration). 09 protection remains active when the switch is off; 10 maintenance is exempt by design.
2. **R-13/R-15 wire contract.** `MaintenanceApplyRequest`/HTTP model gained `frozenDocIds`; each apply group now carries `expectedFingerprint` (identity facts) plus `expectedRelationFingerprint` (relation facts). Rationale: the preview digest must bind the complete frozen list while a legitimate subset pilot/resume must still pass, and "already applied by this request" must be distinguishable from a third-party relation change. `reconcile.py` stores the frozen list in `state.json` (0600) and sends it on apply.
3. **R-14 transaction split.** `importExistingProtection` is `@Transactional` REQUIRED (joins the maintenance group transaction, so relation + protection import commit/roll back together), while R-8's runtime `reserve`/`claimSending`/`markUnknown`/`markSafeRetry` are `REQUIRES_NEW` (pre-SMTP delivery right persists independently); `markSent` stays REQUIRED so the success fact remains coupled with the mail business write. The two requirements do not conflict.
4. **Self-invocation transaction fix.** `apply()` calls `applyGroup()` internally, so the annotation alone provides no transaction; the group body now runs inside an explicit same-DB `TransactionTemplate`, with a rollback signal so a failed protection import rolls the relation writes back.
5. **Unit-level rollback limitation (disclosed).** C18's service test runs without a transaction manager (fake repository), so it asserts the result semantics (`CHANGED_OR_FAILED`/`PROTECTION_IMPORT_FAILED`/`protectionImported=false`/`memberCount=0`); the actual rollback is provided by the explicit transaction and the same-DB guard import. No real-MySQL maintenance-group test exists in the plan's command set.
6. **V-25 pins.** Four pins shifted and two read-only projections were newly registered, each with a rationale comment; the write whitelist is unchanged and the exclusion-staleness self-check is intact. `OperatorStatusWriteSeamGuardTest.kt` was the only test file authorized for this and it is listed in the plan.
7. **B-1/B-2 remain BLOCKED** (performance budget measurement; three-entry-point concurrency E2E). This execution does not claim master PASS or READY_TO_INTEGRATE; at most READY_FOR_VERIFICATION.

## Clean-state evidence

- Pre-execution: `git status --porcelain` empty at `e60ed13` (clean worktree).
- Product commit: `git add` staged exactly the 40 changed authorized files (no other path), commit `b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9` on `fast/2026-10-13-discovery-identity-recovery-master`; after the commit `git status --porcelain` is empty and `git diff --check` is clean.
- Plan identity re-read at handoff: SHA-256 unchanged (`845500de…`). Worktree identity re-checked: same root/branch/git-dir.
- No push, merge, deploy, migration execution, real SMTP, production data apply, or switch configuration change was performed.

---

# Revised repair execution (round 2) — 2026-10-15

## Execution identity

- Approval source (exact human invocations): ① `$execute-p /Users/lukai/…/docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md` (Epoch-2 report, FAIL / DIVERGING; drove the fix work), ② `$execute-p /Users/lukai/…/docs/plans/fix/discovery-identity-recovery-master/repair.md` (revised plan — the form the revised plan declares as its own execution approval).
- Current repair identity: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`, SHA-256 `1c3736141987d41bdbc1737bcd0756cf35f4a8fca0e97bc8cd661586dbeb1f59` (40,181 bytes) — unchanged at handoff. Prior repair `845500de…` is superseded for this content (its execution record above remains historical evidence only).
- Execution epoch: NEW for the revised identity (the plan bytes changed ⇒ new epoch; the previous round's task statuses, evidence and completion claims were reset and re-derived against the revised bytes).
- Target worktree / branch / Worktree ID: unchanged (`…-fast-2026-10-13-discovery-identity-recovery-master` @ `fast/2026-10-13-discovery-identity-recovery-master` @ the same worktree git dir).
- Executor: `execute-p` main integrator session; three delegated implementation groups (`Round2SendPath`, `Round2IdentityScope`, `Round2QueryMaint`) plus a fourth acceptance group (`Round2Acceptance`) were spawned, and **all four died mid-run on a provider/DNS outage** (`getaddrinfo ENOTFOUND opencode.ai`). The integrator completed, corrected and integrated their in-flight work inline.
- Pre-execution code SHA: worktree clean at `5a82206e9b0f171c332dee7512953b90179f0459` when round 2 began; the revised-plan commit `cfcaaf6c729c756eea0c64425ee99f4092a043ab` (`docs(review-fast-p): revise repair after human adjudication`, docs-only: `repair.md` + `ledger.md`) landed during execution and is the commit base of the product change.
- Post-execution code SHA: `3b6f854e64eae0361b86cbb6af9a9b21b0385f13` — subject `fix(discovery): restore outreach and enforce current person facts`.
- Implementation boundary (this round): `cfcaaf6..3b6f854`; adopted pre-existing dirty diff was verified and continued as the revised plan's "Existing Unreviewed Worktree Changes" clause permits.

## Tasks (revised plan R-1…R-9, findings V-26/V-1/V-2/V-3/V-5/V-8/V-4/V-14/V-15/V-13/V-12)

| Task | Status | Files | Evidence |
|---|---|---|---|
| R-1 (V-26) current attempt vs. prior history | IMPLEMENTED | G, INIT, MI, MEM + GT/INITT/MIT/MEMT | Guard `OutreachAttemptIdentity(contactId, attemptId)` + server-side verification (`verifiedOwnContactId`/`verifiedOwnAttemptId`, own row must exist, match the ticket's bound address and belong to the person's current key space); own-row exclusion only when it is the **only** bound contact, and the address gate is skipped only for that verified case. Real-MySQL GT cases (own row + PREPARED attempt ⇒ Claimed; second same-person contact ⇒ HISTORY_PROTECTED; declared id for another address ⇒ no unlock; declared attempt of another mail type while a real UNKNOWN exists ⇒ UNKNOWN_PENDING); real entry INIT acceptance (healthy mapped person reaches SMTP exactly once with the selected address; no second send). |
| R-2 (V-1) default-off legacy behaviour | IMPLEMENTED | INIT, MI, MEM + tests | `personGuardApplies`: gate on ⇒ current fail-closed behaviour; gate off ⇒ guard only for targets that already have a real person mapping (`isKnownPerson`), so unmapped targets keep the legacy flow with **no** guard state writes; an existing SENT/UNKNOWN/SENDING protection still denies. Three distinguishable states asserted. |
| R-3 (V-2/V-3/V-5) full current group verification | IMPLEMENTED | P, IR, SEL (+G inherit) + PT/IRT/SELT/GT | Registration snapshot fails closed on unreadable/non-live existing members (`MEMBER_UNREADABLE`/`MEMBER_NOT_LIVE` ⇒ STALE, zero rows) instead of `mapNotNull`-dropping them; attaching re-verifies **all** current root members (`verifyRootGroup`: ES re-read + fingerprint + full name + author keys); selection re-reads every member's current facts (live scope + registration fingerprint) including in-batch members, with explicit exclusion reasons; reserve/claim inherit the same protection read. |
| R-4 (V-8) only provable non-acceptance may safe-retry | IMPLEMENTED | INIT, MI, MEM (+G) + tests | Classification now requires a refusal code **and** a non-SUCCESS category; 250/SUCCESS after connection loss, missing response code and ambiguous results ⇒ UNKNOWN (no automatic resend). Real-DB GT acceptance: accepted-then-lost ⇒ UNKNOWN and a following request cannot send again (SMTP total 1). |
| R-5 (V-4) admission failure keeps a resumable state | IMPLEMENTED | REC, RP + RECT/RPT | Admission re-verification failure returns a partial/retryable outcome (`ADMISSION_REVERIFY_FAILED`) instead of a complete APPLIED; the CLI keeps non-complete results resumable and only complete APPLIED terminal. |
| R-6 (V-14/V-15 rollback half) complete expected facts | IMPLEMENTED | MAINT, MC, CP + MAT/MCT/CPT | `resumedTargetResult` now requires provable own completion: identity facts blanked of this apply's own mutable fields must equal the previewed expectation, the previewed relation digest must be the "not yet registered" state, and the root revision must be 0; third-party proof/revision/admission changes are refused with zero writes. Apply refuses requests without a relation expectation (`RELATION_EXPECTATION_REQUIRED`); rollback requires the expected person revision and refuses on any change (`ROLLBACK_UNSAFE`), later members or non-terminal protection. |
| R-7 (V-15 import half) real business-key history import | IMPLEMENTED | G, MAINT + GT/MAT | `importExistingProtection` resolves history over member ES docIds **plus** contacts matched by member email and by alias email (business `orcid_id` may differ from the ES docId); import failure raises a rollback signal so the whole group (relations + protection) commits or rolls back together. Real-MySQL GT: different-business-key SENT ⇒ persisted SENT protection; history-free ⇒ no row; injected import failure ⇒ CHANGED_OR_FAILED + `PROTECTION_IMPORT_FAILED` with **zero** committed person/member/outreach rows. |
| R-8 (V-13) refuse exact results from incomplete responses | IMPLEMENTED | ES, Q + QT/EST/ECT/CCT/SEAM | Missing/malformed `hits` (search, scroll continuation, and the shared `_count` used by person queries), timeouts, failed shards and incomplete traversals surface as an explicit unavailable failure; relation-SQL failure surfaces as unknown instead of an empty mapping; complete empty results still return 0. SEAM pins unchanged (C22 green). |
| R-9 (V-12) stale members are not projected as sendable | IMPLEMENTED | Q + QT/SEAM | The per-email projection now derives the display token from the persistent admission/suppression truth **and** the relation's staleness: a stale relation, a missing email, or unavailable truth projects `UNKNOWN` instead of `ADMITTED`; the renderer is unchanged and out of scope. |

## Commands (fresh, final code state)

JDK per plan: C01–C20 = zulu-11 (11.0.15); C21/C22 = zulu-11.0.32.1. Docker: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`.

| Command | Result | Counts |
|---|---|---|
| C01 | PASS exit 0 | 149 / 0F / 0E / 0S |
| C02 | PASS exit 0 | 230 / 0 / 0 / 0 |
| C03 | PASS exit 0 | 308 / 0 / 0 / 0 |
| C04 | PASS exit 0 | 48 / 0 / 0 / 0 |
| C05 | PASS exit 0 | 164 / 0 / 0 / 0 |
| C06 | PASS exit 0 | 15 tests OK |
| C07 (real MySQL) | PASS exit 0 | 6 / 0 / 0 / 0 |
| C08 | PASS exit 0 | 24 / 0 / 0 / 0 |
| C09 (real migrations) | PASS exit 0 | 46 / 0 / 0 / 0 |
| C10 | PASS exit 0 | 283 / 0 / 0 / 0 |
| C11 (real MySQL) | PASS exit 0 | 30 / 0 / 0 / 0 |
| C12 | PASS exit 0 | 150 / 0 / 0 / 0 |
| C13 | PASS exit 0 | no diagnostics |
| C14 | PASS exit 0 | 23 pass / 0 fail |
| C15 | PASS exit 0 | 1680 pass / 0 fail |
| C16 | PASS exit 0 | 259 / 0 / 0 / 0 |
| C17 (real MySQL) | PASS exit 0 | 289 / 0 / 0 / 0 (guard suite 28, incl. the new R-1/R-4/R-7 real-DB acceptance tests) |
| C18 | PASS exit 0 | 33 / 0 / 0 / 0 |
| C19 | PASS exit 0 | 18 tests OK |
| C21 `clean package` | PASS exit 0 | **5085 / 0F / 0E / 20S**; WAR built |
| C22 | PASS exit 0 | 1 / 0 / 0 / 0 |
| C20 | NOT RERUN | approved environment exception (old-JDK tzdb), same as Epoch 1/Epoch 2 |

Retained failures during this round (kept, not erased): the guard real-DB suite first ran 1F/7E (α's new V-26 tests had Mockito matcher misuse and `(campaign_id, orcid_id)` unique-key collisions; the integrator's own fixtures lacked a sendable classification and used fake member fingerprints, and one fixture mixed two different ORCIDs into one person group) — all fixed inside authorized files, after which C17 is green. A first C21-equivalent full build was not attempted before these fixes, so no green claim rests on a pre-fix state.

## Deviations and disclosures

1. **Three-entry real-DB acceptance is partially delivered.** The real Initial entry is wired to real MySQL + real repositories + real guard + fake SMTP and covers (a) healthy mapped person ⇒ exactly one SMTP call with the selected address, (b) accepted-then-lost (250/SUCCESS) ⇒ UNKNOWN and no second send, (c) SMTP accepted + business write failure ⇒ delivery right stays SENDING and no entry may send again (SMTP total 1). `ManualInitialOutreachService` (27 collaborators) and `ManualExpertMailService` (11) full real-DB drives were **not** wired; their healthy-once behaviour is covered by the real-guard declaration contract in the unit suites (MIT/MEMT) and by the guard-level real-DB cases. This leaves the plan's "three entries in one real DB, competing for one person with exactly 1 SMTP" combined regression **incomplete** — reported, not claimed.
2. **Pre-existing defect discovered (out of the 11 in-scope findings, not fixed):** the Initial success path persists `mail_send_attempt_id = 0L` (`InitialOutreachService.kt:183`, introduced 2026-09-25 by `439031c5`, before this repair plan), which violates `fk_mail_record_send_attempt` on a real MySQL database and rolls the success write back after SMTP acceptance. The new real-DB acceptance test asserts the guard/delivery-right semantics around this failure (SENDING preserved, no second send) and documents it; no product behaviour was changed for it, and it needs its own decision/plan.
3. **Sub-agent infrastructure failure:** all four delegated groups died on `getaddrinfo ENOTFOUND opencode.ai` (provider unreachable). Work already written was verified and continued; incomplete/incorrect parts (α's guard-suite tests, γ's partially applied V-12/V-13/V-14 edits) were finished or corrected inline by the integrator. Because the provider may be unavailable, no further delegation was attempted.
4. **Adopted pre-existing diff:** the revised plan's dirty-file table was reconciled hunk by hunk against the 33-file set; the out-of-scope edits to `src/main/resources/static/app.js` and `src/test/js/discoveryPersonEmails.test.js` (made under the superseded round-2 authorization) were reverted and are **not** in the product commit; no other out-of-scope path was adopted.
5. **One product fix beyond a pure "carry-over" of the pre-existing diff:** the guard's own-row case no longer demands the excluded selection target's `selectedEmail` (the ticket's verified bound address governs instead); without this the live protection read denied a legitimate first send (V-26's live path).
6. **B-1/B-2 remain BLOCKED** (performance evidence; complete three-entry concurrency evidence). No master PASS / READY_TO_INTEGRATE is claimed; at most READY_FOR_VERIFICATION.

## Worktree / index state

- `git status --porcelain` was empty before the product commit (29 files staged, all inside the 33-file authorized set) and empty after it; `git diff --check` clean; HEAD = `3b6f854` on `fast/2026-10-13-discovery-identity-recovery-master`, with `cfcaaf6` and `5a82206` as ancestors.
- Plan identity re-read at handoff: `1c373614…` unchanged. Worktree identity re-checked with `--expect-root/--expect-branch/--expect-git-dir` (exit 0).
- No push, merge, deploy, migration execution, real SMTP, production apply or switch-configuration change.

---

# Epoch-three repair execution — 2026-10-15

## Execution identity

- Approval source (exact human invocations): `$execute-p /Users/lukai/…/docs/plans/fix/discovery-identity-recovery-master/repair.md` (the epoch-three revision) followed by the human continuation instruction "批准 继续 修复".
- Repair identity: `docs/plans/fix/discovery-identity-recovery-master/repair.md`, SHA-256 `23d72492384dd6230800fd5efcbde74eb937ae1cf3c3b1b7e9e4b4624cb98424` (43,542 bytes) — unchanged at handoff. Prior identities (`845500de…`, `1c373614…`) are superseded; their execution records above remain historical evidence only.
- Execution epoch: NEW for the revised bytes (prior task statuses/evidence reset and re-derived).
- Target worktree / branch: unchanged (`…-fast-2026-10-13-discovery-identity-recovery-master` @ `fast/2026-10-13-discovery-identity-recovery-master`).
- Executor: `execute-p` main integrator session (all work performed inline; no delegation this round because the provider outage from round 2 makes subagents unusable).
- Pre-execution code SHA: `509330a` (`docs(review-fast-p): revise repair after epoch three adjudication`; worktree clean at it).
- Post-execution code SHA: `1ab288125b4e00b1237a69c48320d315741eaf76` — subject `fix(discovery): enforce current eligibility and identity write contracts`.
- Implementation boundary (this round): `509330a..1ab2881`.

## Tasks (R-1…R-8, findings V-27/V-3/V-5/V-28/V-13/V-14/V-29/V-12)

| Task | Status | Files | Evidence |
|---|---|---|---|
| R-1 V-27 own-history waiver ≠ current address eligibility | IMPLEMENTED | G, SEL, GT, SELT, INITT, MIT, MEMT | `DiscoveryPersonRecipientService.currentAddressEligibility` (current admission truth ignoring the bound-contact duplicate, current-email consistency, suppression, `EMAIL_INVALID` operator status; fail-closed on unavailable truth) is required in every guard claim path including the verified own-row case; `DiscoveryPersonFeatureGate`-independent. SELT 18/18 with the new seam case; GT `own contact does not waive the current address eligibility (V-27)` covers the grant control and the `EMAIL_INVALID` denial. |
| R-2 V-3 lock-after revision/author-key binding | IMPLEMENTED | P, R, PT, RT | `DiscoveryPersonRepository.attachToPersonVerified` (lock person row → revision must equal the verified one → re-read current author keys → same-class-different-value ⇒ `AUTHOR_KEY_CONFLICT`) and `DiscoveryPersonService.resolve` passes the verified revision; fakes updated. |
| R-3 V-5 failed layer ≠ absent layer | IMPLEMENTED | SEL, ES, SELT, EST, GT | `ExpertSearchService.findByDocumentIdsStrict` (complete response + `docs` array required) plus per-layer `failedLevels` → `PERSON_LAYER_READ_FAILED`; SELT regression green. |
| R-4 V-28 incomplete uniqueness search | IMPLEMENTED | W, IR, IRT, WT | `findDiscoveryHitsByEmail` rejects `timed_out`/failed shards/body/hit-structure defects; WT 51/51 incl. two new cases. |
| R-5 V-13 aggregation structure | IMPLEMENTED | ES, EST, Q, QT, ECT, CCT | `aggregations.bucket.buckets` array + per-bucket `key`/`doc_count` required, legal empty array still zero; EST 91/91. |
| R-6 V-14 expected/attribution | IMPLEMENTED | MAINT, MAT, MCT, CPT | the "own completion" inference was removed (no receipt ⇒ conservative `CHANGED_OR_FAILED`, no `protectionImported`), per-layer `seqNo/primaryTerm` bound into `layerVersions`; MAT 25/25. |
| R-7 V-29 full name ≠ proof | IMPLEMENTED | IR, REC, W, IRT, RECT | a layer is `ALREADY_VERIFIED` only when its current proof equals the request's target; full-name-but-no-proof layers get the target proof via the existing version CAS (names untouched). |
| R-8 V-12 EMAIL_INVALID not sendable | IMPLEMENTED | Q, QT, SEAM | contact-truth readability + `EMAIL_INVALID` ⇒ `UNKNOWN` projection; SEAM pin synced (Q:288). |

## Commands (fresh, final code state)

| Command | Result | Counts |
|---|---|---|
| C01 | PASS | 149 / 0F / 0E / 0S |
| C02 | PASS | 230 / 0 / 0 / 0 |
| C03 | PASS | 310 / 0 / 0 / 0 |
| C04 | PASS | 48 / 0 / 0 / 0 |
| C05 | PASS | 164 / 0 / 0 / 0 |
| C06 | PASS | 15 tests OK |
| C07 (real MySQL) | PASS | 6 / 0 / 0 / 0 |
| C08 | PASS | 24 / 0 / 0 / 0 |
| C09 (real migrations) | PASS | 46 / 0 / 0 / 0 |
| C10 | PASS | 283 / 0 / 0 / 0 |
| C11 (real MySQL) | PASS | 30 / 0 / 0 / 0 |
| C12 | PASS | 151 / 0 / 0 / 0 |
| C13 / C14 / C15 | PASS | no diagnostics / 23 pass / 1680 pass |
| C16 | PASS | 260 / 0 / 0 / 0 |
| C17 (real MySQL) | PASS | 294 / 0F / 0E / 4S (the 4 skips are the E01 methods without `-Db2Acceptance`) |
| C18 | PASS | 33 / 0 / 0 / 0 |
| C19 | PASS | 18 tests OK |
| C22 | PASS | 1 / 0 / 0 / 0 |
| C21 `clean package` | PASS | **5089 / 0F / 0E / 20S**; WAR built |
| C20 | NOT RUN | approved old-JDK tzdb exception |
| **E01** (B-2 collection) | RAN, 4/4 green | `b2HealthyInitial`, `b2HealthyManualInitial`, `b2HealthyManualExpert`, `b2SamePersonCompetition` |

Retained failures during this round (kept, not erased): the first C17 attempt failed with 2F/3E and C22/C21 with the SEAM pin; the causes were (a) test fixtures not stubbing the new `currentAddressEligibility` seam / the strict layer-read method, (b) my own V-27 fixture mixing profiles, (c) the Q pin drifting 284→288 after the R-8 projection edit. All were fixed inside authorized files; the final C17/C22/C21 runs above are green.

## B-1 / B-2 / Initial FK status (unchanged by this repair)

- **B-1: BLOCKED** — no 51,829-row real-shape performance measurement exists; nothing was fabricated or optimised.
- **B-2: BLOCKED — Initial FK prerequisite / remaining evidence.** E01 ran and is recorded, but `b2HealthyInitial` **asserts the known FK causal chain** (`mail_send_attempt_id = 0L` from `InitialOutreachService.kt:180–184` violating `fk_mail_record_send_attempt`) plus the independently persisted `SENDING` delivery right; it does **not** demonstrate a green healthy completion, and the MI/MEM methods drive the entries' **guard seam** (real guard, real repositories, real transaction, fake SMTP) rather than the full service objects. No green B-2 claim is made.
- **Initial FK**: excluded from this plan (`NEW_SCOPE_REQUIRED`); not fixed, not mocked, not deleted.

## Deviations and disclosures

1. **V-27 sub-case "revoke the admission after reserve" is unresolved and reported, not hidden.** In the GT fixture the claim still returned `Claimed` after the current admission was switched to `HOLD` (both via a mutable stub and via an explicit second stub). The test carries an explicit open-question comment; no assertion was written to mask the behaviour. Admission revocation *at reserve* is covered by the existing V-26/SELT HOLD cases. This needs its own diagnosis round.
2. **E01 scope**: as described above, MI/MEM cover the entry seams, not the full service objects; `b2HealthyInitial` records the known FK block.
3. The four E01 methods are gated by `@EnabledIfSystemProperty(named = "b2Acceptance", matches = "true")` and are therefore skipped (4) in the ordinary C17/C21 runs, as the plan requires.
4. No migration, schema, `app.js`/`index.html`, CLI product, controller product or caller product file was touched; the plan's 28-file set bounds the change (19 files modified, +591/−118).

## Worktree / index state

- `git status --porcelain` empty before and after the product commit; `git diff --check` clean; HEAD `1ab2881` on `fast/2026-10-13-discovery-identity-recovery-master` with `509330a` as ancestor.
- Plan identity re-read at handoff: `23d72492…` unchanged. Worktree identity re-checked (`--expect-root/--expect-branch/--expect-git-dir`, exit 0).
- No push, merge, deploy, real SMTP, production apply or switch-configuration change.
