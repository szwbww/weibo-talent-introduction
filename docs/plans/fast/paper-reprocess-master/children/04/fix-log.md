# Child 04 Fix Log

## Epoch 2 — Round 1/3
- Findings: F-01, F-02, F-03, F-04, F-05; exact authority is `verify-log.md:33-37`, not O-01/O-02.
- Before: dff7585906d8a628c87e4c385dba4c822f72e576
- Fix commit: 503e7e51bff189d57bf9da6bad26015e930c7478
- Result: FIXED
- Approved master/child plan identity: `5a31faaebf28001fb05d34c6b2aa195cc2a0e01d`; Base `ff7e67506545a31bc38a3d3f674056c522c57917`. Read child brief/execution/report, exact findings and fixer protocol. Controller retains plans, ledger, execution and verification verdict authority; no independent PASS is asserted.
- Authorized files changed:
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`

### Source corrections and final behavioral evidence
- F-01 — Apply catch preserves the actual applied flag rather than inventing success. Target claiming no longer automatically reclaims caught application errors: retry is explicit. ENFORCE retry reopens only this parent's ordinary incomplete application input; it can revive only its never-activated, unadmitted RAW-missing reservation. Successful effects/before/source/item receipts remain. The existing archived consumer consults effect receipts and resumes missing enrichment/activation/person actions, not qualification of committed members. Real MySQL trigger rejects ordinary reservation INSERT; separate loopback RAW PUT returns 503. Both caught failures close the first input, then fault removal plus existing retry reaches exactly one RAW/ordinary item, retains before receipts and leaves the previously committed old item and its provider attempts unchanged. RAW variant also retains its reservation ID.
- F-02 — `claimDue` excludes denied paper parents before ORDER/LIMIT selection. The selected owner still goes through the authoritative locked check; parent→run→item and one verification run per claim transaction remain. No cancellation/adoption of parked or unrelated work.
- F-03 — `listReserved` excludes denied parents before its bounded window, and production `recoverReserved` explicitly skips denied feature-owned reservations before activation. Locked activation rejection remains; arbitrary exceptions are not swallowed. The runtime uses a real RegistrationRecovery bean and actual worker tick, not an empty provider or direct-claim substitute. With interrupted parent, 13 parked reservations and 101 older queued items remain byte-for-byte SQL-row unchanged while unrelated ordinary recovery/qualification and an independent recheck progress. Candidate PUT 503 produces real projection ERROR; existing subsequent tick projects successfully with unchanged admission and provider delta zero. The test waits for its finite async lane using existing `tickAndAwait`, before the next tick.
- F-04 — `savePreparationInputs` and `reserveRecheckItem` enforce `paperAllowed(run, locked=true)` inside their mutation transactions, including the existing-item path. `lockRun` remains usable for denied-parent cancellation and projection. Real REPEATABLE_READ outer transactions establish an allowed entry snapshot; another connection commits cancel/expiry before mutation. Both mutations reject without late input/item/run writes; subsequent existing cancellation still works. All input/reservation × cancel/expiry cases have provider delta zero.
- F-05 — An admitted COMPLETED item with projection PENDING keeps TARGET observing. Runtime blocks the actual candidate PUT, polls the parent while projection is in flight, and proves TARGET is unsealed/RUNNING with admission retained. Releasing a 503 records actual ERROR/admitted-but-sync-failed in the target; existing worker sync retry succeeds with provider delta zero and no qualification replay.
- Reproducible source anchors: ApplyService `apply`/`observe`; ReprocessRepository `claimTarget`/`retry`; ExpertDiscoveryService archived `consumeOutcomeInternal` effect branches; VerificationRepository `claimDue`/`listReserved`/`savePreparationInputs`/`reserveRecheckItem`; RegistrationService `recoverReserved`. Grep used: `applicationError|awaiting =|ENRICHMENT_ENQUEUED|PERSON_ATTEMPTED|REGISTRATION_RAW_MISSING|PAPER_PARENT_PERMISSION_LOST|fun listReserved|fun claimDue|fun recoverReserved|state='PENDING'` over these product paths. Captured output points to ApplyService:57-68,277; ReprocessRepository:164,439-471; ExpertDiscoveryService:2045-2077; VerificationRepository:564,806,975,1267; RegistrationService:421-427.

### Commands and after-final-change outcomes
Commands run from the exact worktree with JDK11:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true -DtrimStackTrace=false -Dstyle.color=never
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke' -Dstyle.color=never
```
- Final fresh four-suite command: exit 0, BUILD SUCCESS, 51 Java tests / 0 failures / 0 errors / 0 skipped, completed 2026-11-02T07:32:17+08:00. Integration 35, Apply 2, Backfill 4, Recheck 10 (actual output `artifact://526:4664-4674`). Maven-bound JS phases also ran; this is not a claim that all Java suites were selected.
- Separate fresh standalone smoke: exit 0, real MySQL 8.0.36 container, completed with `PAPER04_RUNTIME_OK realSQL=true loopbackEsFixture=true originalRetainedPDF=true consumer=true recheck=true worker=true commit=true projection=true legacyCLI=true paidCalls=0` (`artifact://528:2365`). It invokes the old baseline scenarios plus the new explicit-retry, parked tick, locked-preparation and delayed-projection scenarios.
- Both final executions emitted:
```text
PAPER04_EXPLICIT_RETRY caughtFault=RESERVATION closedInputReopened=true beforeRetained=true committedOldReplay=0 rawWrites=1 ordinaryItems=1
PAPER04_EXPLICIT_RETRY caughtFault=RAW closedInputReopened=true beforeRetained=true committedOldReplay=0 rawWrites=1 ordinaryItems=1
PAPER04_PARKED_TICK reservedSkipped=13 parkedDue=101 ordinaryRecoveryAndIndependentRecheckProgress=true projectionRetryProviderDelta=0 parkedWrites=0
PAPER04_LOCKED_PREPARATION mutation=inputs denial=cancel staleEntryAllowed=true lateWrites=0 cancellationStillWorks=true providerDelta=0
PAPER04_LOCKED_PREPARATION mutation=inputs denial=expiry staleEntryAllowed=true lateWrites=0 cancellationStillWorks=true providerDelta=0
PAPER04_LOCKED_PREPARATION mutation=reservation denial=cancel staleEntryAllowed=true lateWrites=0 cancellationStillWorks=true providerDelta=0
PAPER04_LOCKED_PREPARATION mutation=reservation denial=expiry staleEntryAllowed=true lateWrites=0 cancellationStillWorks=true providerDelta=0
PAPER04_DELAYED_PROJECTION pendingTargetUnsealed=true actualErrorObserved=true admittedRetained=true syncRetryProviderDelta=0
```
- Evidence retrieval: grep `PAPER04_EXPLICIT_RETRY|PAPER04_PARKED_TICK|PAPER04_LOCKED_PREPARATION|PAPER04_DELAYED_PROJECTION|Tests run:|BUILD SUCCESS|Exception in thread "discovery-verification-worker"` on final suite output; standalone grep adds `PAPER04_RUNTIME_OK`. Final marker locations: suite 899,1038,1939,2692,2727,2762,2797,4416; standalone 1772,1911,2073,2108,2143,2178,2213,2364-2365. No discovery-verification-worker uncaught exception matched these final outputs.

### Failed executions and corrections retained
Every full-suite attempt below used the exact first command above; none is represented as success.
| Output | Exit / Java results | Observed failure and correction |
| --- | --- | --- |
| `artifact://495` | 1; 51 / 0 failures / 3 errors | Fixture used admitted-only lookup for unadmitted history; expiry cancellation lacked cancellation request; queued seed lacked batch_number. Corrected history lookup, legal cancellation setup and seed column. |
| `artifact://499` | 1; 51 / 0 failures / 4 errors | RAW case exposed implicit target reclaim before explicit retry (fixed product claimTarget). Unrelated RAW was seeded before reservation and became protected (reordered). Rapid overlapping tick exposed the projection trace observation below. |
| `artifact://502` | 1; 51 / 0 failures / 1 error | Parked tick predicate timed out; other new paths completed. |
| `artifact://505` | 1; 51 / 0 failures / 1 error | Explicit ENFORCE setup alone did not resolve parked fixture timeout. |
| `artifact://508` | 1; 51 / 0 failures / 1 error | Explicit ORCID-record identity alone did not resolve parked fixture timeout. |
| `artifact://511` | 1; 1 / 0 failures / 1 error | Targeted diagnostic below exposed actual completed PENDING/FACT_CHANGED with three ORCID attempts, rather than dispatch starvation. |
| `artifact://517` | 1; 51 / 0 failures / 1 error | Using preparedProfile alone was insufficient: externalIds was still a serialized string in the synthetic RAW map. |
| `artifact://520` | 1; 51 / 0 failures / 1 error | RAW externalIds wire representation corrected; actual reason then SOURCE_ID_CONFLICT because arbitrary document key was not the canonical EMAIL key. |
| `artifact://523` | 1; 51 / 0 failures / 1 error | Canonical ExpertIdGenerator key allowed real ERROR/admission and independent work to progress. Remaining assertion incorrectly required the entire fixture's pre-existing trace table count to be zero; corrected to exact parked-parent trace snapshot equality plus empty parked traces/reserved-item zero. |
- Targeted diagnostic command (not the final gate):
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationIntegrationTest#paperBatchParkedTick -DmysqlIt=true -DpaperReprocessIt=true -DtrimStackTrace=false -Dstyle.color=never
```
- Final fixture uses registration.preparedProfile, canonical EMAIL ID, object-valued RAW externalIds, and a real HTTP-converter RAW reread whose fingerprint must equal the reserved fingerprint before tick. Failure assertions were not dropped: parked rows/admission/traces must remain unchanged; unrelated work must actually reach projection ERROR then PROJECTED with retained admission/provider delta zero.

### Remaining concerns, limits and identity
- Observed overlapping projection retry limitation, NOT FIXED: the failed full-suite command recorded at `artifact://499` raised duplicate `discovery_verification_step` step8/attempt2 when a rapid 20ms fixture tick overlapped async projection. CommitService/TraceRepository were not authorized for this repair. Main explicitly required retaining this observation for the fresh verifier, not suppressing the error or expanding product scope. Baseline pump retains direct claim/process semantics; the new production-tick scenario awaits its own finite lane before the next tick. This does not establish general concurrent projection retry safety.
- Real SQL transactions/proxies, admission writes, original consumer/recheck/worker/commit/projection and retained original PDF are exercised. ES is a strict HTTP loopback fixture, NOT a real Elasticsearch engine. ORCID/Apollo are deterministic RestTemplate provider fixtures; no real provider/paid/production calls. Existing eligibility/contact dependencies remain fixture dependencies. Injected PaperCrash/503 logs are intentional; failed commands and teardown effects above are not hidden.
- Original retained 31372 PDF immutable: `shasum -a 256 /Users/lukai/.local/share/paper-reprocess-30711/488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7.bin` exited 0 and returned `488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7`. No input bytes rewritten.
- Worktree helper invoked as required with exact plan/root/branch/gitdir options. It exits 1 at line30 on the already-documented unrelated stale publish registration. Brief:117 fallback used; registrations/helpers/config untouched. Native assertions at staging/commit/final check exact root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`, branch `fast/paper-reprocess-master`, gitdir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`, common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`, registered target, HEAD==branch ref, and Base/approved-plan/before ancestry. Before staging index was empty; staged and committed path sets exactly matched the authorized list above. Commit excludes fast artifacts; controller dirt/history preserved.
- Approved plan file SHA256 remained child `9d2899342bc0d9cbb6336930864a7c23220224f310453e95b339b7cbcc45bb61`, master `a3679e54a439fd8095f0788a7c42359a2cfef9f82eb52dc31d95562da21c55ac`. No plan/ledger/schema/new file/new class/new state/interface/generic bypass/alternate writer/production operation/review-fast-p/later child/push/merge/rewrite/O-01/O-02 edits.

## Epoch 2 — Round 2/3
- Findings: F-01 only, remaining retryable person-registration action (`verify-log.md:169-175`).
- Before: 503e7e51bff189d57bf9da6bad26015e930c7478
- Fix commit: 736535ce9487782e52a73af6cb52517fa799b194
- Authorized files changed:
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`
- Result: FIXED
- Notes: Exact master/child plan identity `5a31faaebf28001fb05d34c6b2aa195cc2a0e01d`, Base `ff7e67506545a31bc38a3d3f674056c522c57917`. Controller explicitly authorized the existing plan-listed RegistrationService direct dependency: its old boolean-only receipt API could not persist actual status/reason/personId/retryability. Optional existing-type argument retains existing callers, writer and permission/lease authority. No new class/state/interface/schema/writer/bypass, plan amendment or adjacent repair. F-02–F-05 remain resolved; O01/O02/O03 remain RECORD_ONLY.

### F-01 bounded correction and actual regression
- Original archived consumer retains the actual `DiscoveryPersonRefreshResult`; FAILED/STALE persist `personRegistration.status/reason/retryable=true` and do not seal `effects.PERSON_ATTEMPTED`. Only completed/non-retryable actions suppress registration. Existing legacy attempted-only receipts with `consumeOutcome.registrationFailed=true` remain retryable; a current structured result takes precedence. Actual success retains its personId in the reason field. Earlier RAW/enrichment/activation/source/before/proposal receipts are preserved.
- The original `registerPerson` action remains the writer entry. No RAW rewriting, generic duplicate adoption, qualification/provider replay or new registration implementation.
- `paperBatchPersonRegistrationRetry` is included in both the integration suite and the separate standalone smoke. FAILED is produced by a real MySQL member-INSERT trigger; STALE is produced by actual HTTP 503 during the real registration service's source reread. The Mockito spy delegates to the real service and records source/doc/result, not fabricated success/failure results.
- Both branches first assert consumer-visible failure and durable retryability with zero fresh person members. After fault removal and explicit existing retry, exactly the same CROSSREF/doc registration action has two calls, and actual SQL person/member IDs match the success receipt. The final parent completes. Byte/value comparisons preserve all verification items/attempts/admissions, enrichment rows, ES fixture documents, original before/input/source/proposal receipts and provider request sequence. Existing verifyEffects also checks original archive/source task preservation and actual counters.

### Commands — fresh after final edits
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DpaperReprocessIt=true -DtrimStackTrace=false -Dstyle.color=never
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest --paper-reprocess-smoke' -Dstyle.color=never
```
- Four required focused suites: exit0 / BUILD SUCCESS, 52 JVM tests / 0 failures / 0 errors / 0 skipped (Integration36, Backfill4, Recheck10, Apply2), finished `2026-11-02T09:29:26+08:00`. Artifact `artifact://554:4904-4914,7664-7667`. Maven-bound Node2018/pass2018/fail0/skipped0 and both bound syntax tasks also completed (`artifact://554:7652-7663`).
- Separate runtime: exit0, elapsed338.36s, disposable MySQL8.0.36 with real SQL/transaction proxies; final timestamped log `2026-11-02 09:35:07.278 +08`, then `PAPER04_RUNTIME_OK realSQL=true loopbackEsFixture=true originalRetainedPDF=true consumer=true recheck=true worker=true commit=true projection=true legacyCLI=true paidCalls=0` (`artifact://556:2604-2605`).
- New branches in suite `artifact://554:2060,2179`; separate runtime `artifact://556:2032,2151` both emit:
```text
PAPER04_PERSON_RETRY fault=FAILED originalActionCalls=2 actualPersonMemberReceipt=true beforeSourceRetained=true rawWrites=1 enrichmentReplay=0 qualificationReplay=0 providerDelta=0 admissionRetained=true
PAPER04_PERSON_RETRY fault=STALE originalActionCalls=2 actualPersonMemberReceipt=true beforeSourceRetained=true rawWrites=1 enrichmentReplay=0 qualificationReplay=0 providerDelta=0 admissionRetained=true
```
- Separate runtime also re-exercises actual legacy Java CLI AUDIT/REUSE/READY/neverStarted (`:472`), reservation/RAW retry (`:1772,1911`) and actual parked production tick (`:2313`). These are not substituted for the new person-failure branches.
- No failed build/test/runtime command occurred in round2. The required identity helper exited1 at entry/staging/commit on the already-documented stale unrelated publish registration; native fallback below used. All prior round1 failed commands and O03 observations above remain intact and were not rerun merely to confirm them.

### Identity, scope and remaining limits
- Native exact identity checks passed at entry/staging/commit and final handoff: root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`, branch `fast/paper-reprocess-master`, Git dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`, common dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`, registered target, HEAD==target branch, Base/approved-plan/Before ancestry. No helper/registration/config repair.
- Product commit subject `fix(fast-p): repair 04 round 2`; staging and committed path sets exactly the three authorized paths above. Controller-owned existing execution/fix/verify/ledger dirt is preserved; no artifacts staged into the product commit. This record remains an uncommitted controller artifact.
- Child SHA256 `9d2899342bc0d9cbb6336930864a7c23220224f310453e95b339b7cbcc45bb61`; master SHA256 `a3679e54a439fd8095f0788a7c42359a2cfef9f82eb52dc31d95562da21c55ac`, unchanged.
- Original retained 31372 PDF hash command `shasum -a 256 /Users/lukai/.local/share/paper-reprocess-30711/488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7.bin` exits0 with `488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7`; fixture also checks immutable bytes each runtime construction. No original input rewritten.
- O01 unchanged: strict loopback HTTP ES is not a live Elasticsearch engine; provider HTTP and email validation are deterministic fixtures, contact/alias dependencies retain existing mocks. No live-provider accuracy, paid calls, deployed/browser/manual acceptance or full Kotlin/WAR gate claimed. O02 generic summary boundary unchanged. O03 historical overlapping 20ms projection-trace duplicate remains a RECORD_ONLY limitation, not repaired/reproduced; no general concurrency-safety claim.
- No product in-scope concern remains known to this bounded fixer. This is FIXED execution evidence, not PASS; fresh child verifier/controller owns the verdict. No review-fast-p, child05, production operation, push/merge/amend/squash/history rewrite.

