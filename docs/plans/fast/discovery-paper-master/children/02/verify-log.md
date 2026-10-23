# Child 02 light verification log


## Epoch 1 — Verification not dispatched
- Implementation stopped at the pre-edit authority gate with PLAN_CONFLICT.
- No verifier verdict or fresh product test result exists for child 02.
- Exact conflict evidence: execution.md C-1/C-2. Human amendment is required before implementation can resume.


## Epoch 2 — Attempt 1 — 2026-10-24T00:02:15+08:00 — PaperCaptureVerifierE2

## Light Verification: LIGHT_FAIL
Child: 02 — docs/plans/2026-10-23/discovery-paper-02-capture.md (amended authority commit 87c5268976807c19c165fe50c91ba3372ed0075d; brief Epoch 2; ledger A1/A2)
Boundary: 74c00bdab46796084c5d4a1a7a3c572b7bee6915..030d96b227111587ba591446e9aeb64b1b0e85d7
Verifier: PaperCaptureVerifierE2 (epoch2 attempt1; independent of PaperCaptureImplementer and PaperCaptureImplementerE2)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary git diff --name-only lists ten product/test paths, all in the amended child change table: Capture, ArchiveService, ArchiveRepository, ExpertDiscoveryService, PdfEmailExtractor, EuropePmcDataSource, PmcOaDataSource, DiscoveryPipelineService, CaptureTest, FlowIT. Interleaved plan/evidence governance paths excluded from product scope as instructed. HEAD is 030d96b227111587ba591446e9aeb64b1b0e85d7 on fast/discovery-paper-master; git diff HEAD -- src pom.xml is empty. git diff 87c5268976807c19c165fe50c91ba3372ed0075d HEAD -- the two named approved plans is empty. No product/test/index/branch/commit edits by this verifier. |
| Plan and invariants | PASS | Capture.kt:37-78,80-125 establishes worker-local scopes, firstFatal rethrow, original-byte synchronous saves, CORE EMBEDDED_TEXT, structured parserVersion, attemptIds and finally restoration. ArchiveService.kt:113-145,178-180 implements server-issued permits, live deployment/capacity/safety checks and retained direct-call enabled guard. ArchiveRepository.kt:66-79 locks the record transactionally, preserves first, updates latest, and leaves analysis untouched. ExpertDiscoveryService.kt:941-945,1606-1632,1682-1712,2121-2150 captures before consumption and joins all started futures before rethrow. Pipeline.kt:1025-1043 handles the new exception before ordinary result dispatch; queue Repository.kt:1227-1250 CAS preserves attempts/cache with +60s. CaptureTest eight cases and FlowIT eight cases freshly pass, covering six sources, independent sample capture, exact bytes/hash, duplicate blobs, retries/fallbacks, disabled scopes and active disable, first/latest concurrency, blocked sync zero-consumption/checkpoint, queue recovery/stale lease, cache and cancellation. |
| Required commands | PASS | Both exact required commands below ran freshly after guard inspection; combined command exit 0 (artifact://105), separate forked smoke exit 0 (artifact://107). Combined JVM: 341 run, 0 failures/errors/skips; Node: 1807 passed, 334 suites, 0 failed/cancelled/skipped/todo; Maven-bound app.js/task-modal-runtime.js syntax checks completed. Counts match epoch2 execution baseline 341 JVM/1807 Node; original storage/sample baseline 37 JVM/1807 Node and original sample-only 11 JVM are historical only. |
| Downstream interfaces | FAIL | F-1: Capture.kt:106-110 emits the new first/latest outcome envelope, but ArchiveService.row():292-295 still reads summary counts from root-level fields. list():307 and detail():311 both call row(). Any real capture with nonempty outcome.emails therefore returns extractionSummary.emails=0. This breaks the existing archive list/detail DTO semantics retained by the brief and the predecessor storage contract (01 T-3 lines 96,107-108). No later-child implementation or plan was reviewed. |

Fresh combined required command (cwd is the named worktree):
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest -DmigrationIt=true`

Fresh suite counts: CaptureTest 8; FlowIT 8; RepositoryIT 4; ArchiveServiceTest 15; ControllerTest 7; SampleCaptureTest 4; SampleStoreTest 7; PipelineServiceTest 52; ExpertDiscoveryServiceTest 194; PdfEmailExtractorTest 42. All failures/errors/skips zero, total 341. Maven BUILD SUCCESS finished 2026-10-24T00:00:58+08:00; Node totals and syntax phases are artifact://105:7260-7275. These are fresh verifier executions, not the retained writer results.

Fresh separate forked smoke command:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher'`

Smoke BUILD SUCCESS finished 2026-10-24T00:01:17+08:00. Disposable MySQL, real temporary files and fake HTTP; observed output: `CAPTURE_SMOKE bytes=1024 sha256=a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02 first=frozen latest=updated savedAttempts=2 sharedFiles=1 blockedHTTP=0 queue=PENDING attempts=2 delay=60s cursor=unchanged` (artifact://107). No deployment or production application context was used. This smoke does not substitute for the unexercised positive-email summary consumer.

### AUTO_FIX
- F-1 (P2) — Preserve extractionSummary when adopting the versioned capture envelope. Governing contract: child02 T-1 frozen original first/outcome with first/latest envelope; brief retains existing storage DTOs; predecessor T-3 list/detail extractionSummary fields. Patch anchor: src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt:106-110. Consuming evidence: src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt:292-295,302-313 reads the old flat root, so a nonempty captured email list becomes zero in both API summaries. Smallest authorized correction: unwrap frozen first.outcome for capture envelopes in the existing service summary reader, retaining flat-outcome handling for existing direct calls; add a positive-email capture/list/detail regression in CaptureTest or FlowIT. No DTO, parser, schema, controller or unauthorized predecessor-test changes are needed. No repair was applied by this verifier.

### RECORD_ONLY
- O-1 — Historical network-isolation deviation remains real and is not waived: execution.md Epoch 2 discloses that an earlier failing writer fixture accidentally used synthetic ARXIV external requests. The retained report distinguishes code-derived URLs (`https://arxiv.org/pdf/10.fixture/one`, `/two`, `/ARXIV`) from packet-captured evidence and explicitly has no exact independent wire count. This verifier did not replay those requests or infer a wire count. Before either fresh command, inspected FlowIT.kt:79-100: synthetic ARXIV URLs are rewritten to loopback before deadline-client construction; target and shared RestTemplate interceptor reject non-127.0.0.1 hosts. RestTemplateConfig.kt:439-461 preserves that interceptor in bounded clients; fake responses contain no redirect target. Fresh commands above use this final guarded fixture. The past deviation cannot be undone by an authorized product repair and remains RECORD_ONLY for controller/human visibility, not a claim that all historical runs were isolated. It does not invalidate the separately fresh guarded evidence or require replaying child01.

### Required Action
- AUTO_FIX


## Epoch 2 — Attempt 2 — 2026-10-24T00:21:39+08:00 — PaperCaptureReverifierE2R1

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 02 — docs/plans/2026-10-23/discovery-paper-02-capture.md (approved authority commit 87c5268976807c19c165fe50c91ba3372ed0075d; brief Epoch 2; ledger A1/A2)
Boundary: 74c00bdab46796084c5d4a1a7a3c572b7bee6915..c37341aacfc0937e829132ad73d8af7fa24b4b97
Verifier: PaperCaptureReverifierE2R1 (epoch2 attempt2; fresh independent re-verifier, distinct from PaperCaptureImplementer, PaperCaptureImplementerE2 and prior verifier PaperCaptureVerifierE2)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff 74c00bdab46796084c5d4a1a7a3c572b7bee6915 c37341aacfc0937e829132ad73d8af7fa24b4b97 --name-only -- src pom.xml` returned exactly the ten amended authorized paths (receipt below). `git show --format=fuller c37341aacfc0937e829132ad73d8af7fa24b4b97 -- src/main src/test` shows repair round1 changes only ArchiveService and already-authorized CaptureTest. HEAD is c37341aacfc0937e829132ad73d8af7fa24b4b97 on fast/discovery-paper-master; code/plan diff against HEAD and cached-name diff were empty. Approved plan diff from 87c5268976807c19c165fe50c91ba3372ed0075d is empty. Interleaved governance/evidence changes are not product scope. |
| Plan and invariants | PASS | Capture.kt:37-78,80-125 supplies worker-local capture, firstFatal rethrow, synchronous original-byte saving, CORE EMBEDDED_TEXT, parserVersion object, attemptIds and finally restoration. ArchiveService.kt:113-145,168-180 retains scope-owned permits, live capacity/deployment/safety checks, and direct-call enabled protection. ArchiveRepository.kt:65-79 transactionally locks the row, preserves frozen first, updates latest and leaves analysis untouched. ExpertDiscoveryService.kt:941-945,1606-1632,1682-1712,2121-2150 and PipelineService.kt:1025-1043 establish extraction-before-consumption, complete future joins and exception handling before ordinary dispatch. QueueRepository.kt:1227-1250 preserves attempts/cache using lease/generation CAS. Fresh CaptureTest and FlowIT cover the required source, byte/hash, retry, disabled, capacity, cache, cancellation and frozen-first/latest cases. |
| Required commands | PASS | Both commands below ran freshly after inspecting final fail-closed loopback routing/guards. Combined command exit 0: 342 JVM tests, zero failures/errors/skips; Maven-bound Node 1807 tests in 334 suites, 1807 passed, zero failed/cancelled/skipped/todo; both syntax phases completed (artifact://130). Separate forked smoke exit 0, BUILD SUCCESS, actual disposable MySQL/real files/fake HTTP (artifact://132). Counts equal repaired execution baseline 342 JVM + 1807 Node; original child01 baseline 37 JVM + 1807 Node is retained as historical comparison only, not replayed or substituted. |
| Downstream interfaces | PASS | F-1 CLOSED by round1 c37341aacfc0937e829132ad73d8af7fa24b4b97: ArchiveService.kt:292-298 unwraps schemaVersion=1 frozen first.outcome, falling back to the original flat root for direct calls. list():304-309 and detail():311-315 both use row(); raw detail outcome remains the intact envelope. CaptureTest.kt:94-113 asserts positive captured email summaries remain frozen after an empty latest extraction and verifies flat emails/resolved/unresolved counts through list and detail. ArchiveModels.kt:34-44 DTOs and ArchiveController.kt:17-21 routes are unchanged in the exact boundary. No later-child implementation or full-system review performed. |

Authorized-scope receipt: the exact `git diff ... --name-only -- src pom.xml` command above returned:
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/PmcOaDataSource.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveFlowIT.kt

Implementation: 030d96b227111587ba591446e9aeb64b1b0e85d7. Repair round1: c37341aacfc0937e829132ad73d8af7fa24b4b97. F-1 from epoch2 attempt1 is resolved, not renumbered or reopened. No new four-gate violation found.

Fresh verification command count: 2 (one combined required-suite invocation and one separate forked smoke); no failed verification invocation in this attempt. All commands used the named retained worktree as cwd.

Combined required command:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest -DmigrationIt=true`

Result: exit 0; BUILD SUCCESS finished 2026-10-24T00:20:18+08:00, artifact://130. Fresh surefire reports: CaptureTest 9; FlowIT 8; RepositoryIT 4; ArchiveServiceTest 15; ControllerTest 7; SampleCaptureTest 4; SampleStoreTest 7; PipelineServiceTest 52; ExpertDiscoveryServiceTest 194; PdfEmailExtractorTest 42; total 342, all failures/errors/skips zero. Evidence obtained using `grep` pattern `Tests run:` over the named suite report files. Node totals and app.js/task-modal-runtime.js syntax phases: artifact://130:7259-7274. Baselines: repaired execution 342 JVM/1807 Node; preceding implementation/attempt1 341 JVM/1807 Node; original child01 37 JVM/1807 Node. The new CaptureTest regression accounts for the one-test increase.

Separate recorded forked smoke command:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test -Dexec.args="-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher"`

Result: exit 0; BUILD SUCCESS finished 2026-10-24T00:20:37+08:00, artifact://132. Observed output: `CAPTURE_SMOKE bytes=1024 sha256=a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02 first=frozen latest=updated savedAttempts=2 sharedFiles=1 blockedHTTP=0 queue=PENDING attempts=2 delay=60s cursor=unchanged`. This was a separately forked JVM using disposable MySQL, actual temporary files, production capture registration and fake HTTP, not a relabelled JUnit result. It does not claim a whole-system or human acceptance verdict.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 — Preserved unchanged in classification: execution.md Epoch 2 discloses historical accidental external synthetic ARXIV requests during an earlier failing writer fixture. The listed targets are code-derived, not packet-captured evidence; no exact independent wire count exists and none is asserted here. Those requests were not replayed. Before fresh execution, inspected FlowIT.kt:79-100: synthetic ARXIV targets are rewritten to 127.0.0.1 before bounded-client construction, both final-target check and shared RestTemplate interceptor reject other hosts; RestTemplateConfig.kt:439-461 copies that interceptor into bounded clients. The inspected fake response fixtures contain no redirect targets. Both fresh commands used this final guarded fixture; the historical deviation remains visible for controller/human review and cannot be undone by an authorized product repair. This note does not invalidate the separately fresh guarded evidence or waive the past deviation.

### Required Action
- COMPLETE_CHILD

### Controller artifact checkpoint correction 1 — 2026-10-24
- The child checkpoint rejected only an annotated repair SHA in fix-log; a canonical bare-SHA binding was appended preserving history.
- PaperCaptureReverifierE2R1's LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD and all recorded command evidence above remain unchanged. No verifier, product check or repair was rerun for this formatting correction.
