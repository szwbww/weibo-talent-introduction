# Child 02 execution log


## Execution Result: PLAN_CONFLICT

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md`
Plan SHA-256: `1147203f9d2e64cf8760bbf8d24c0702983f5766bb688d3db1ad989be1c15865`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md@1147203f9d2e64cf8760bbf8d24c0702983f5766bb688d3db1ad989be1c15865`
Execution epoch: NEW
Approval basis: explicit user invocation to implement child 02; no amendments approved.
Executor: PaperCaptureImplementer
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`
Target branch: `fast/discovery-paper-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`
Pre-execution code SHA: `74c00bdab46796084c5d4a1a7a3c572b7bee6915`
Pre-execution HEAD: `e0fd89d8c14c659001cf7a4da469e4275ac066d9`
Post-execution code SHA: `74c00bdab46796084c5d4a1a7a3c572b7bee6915` (unchanged)
Evidence HEAD: `e0fd89d8c14c659001cf7a4da469e4275ac066d9` (unchanged; this log is not committed by executor)
Implementation boundary: no product/test edits or implementation commit; stopped at the pre-edit authority gate.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1: enablement fixed for each scope; disabling must not discard the body's already-completed download | CONFLICT | Existing `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` (not authorized for child 02) | Plan line 65 requires scope-start enablement. Service lines 145–146 reread the live setting inside `saveBody` and throw `ARCHIVE_DISABLED` if disabled before saving. |
| T-1 / I-4: immutable first outcome and latest updated on real re-extraction | CONFLICT | Existing `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` (not authorized for child 02) | Plan line 65 requires first/latest; repository lines 65–67 only update `outcome_json` when it is NULL. Subsequent `finishExtraction` calls cannot update latest. |
| T-2 HTTP/worker integration | BLOCKED | Authorized production paths in plan lines 92–97 | Cannot deliver the approved end-to-end behavior until T-1 storage seam has authority and capability. No partial hooks were added. |
| T-3 integration tests / A-1 through A-3 | BLOCKED | Authorized test paths in plan lines 98–99 | No implementation exists for this execution; checks and smoke were not run or claimed. |

### Exact Conflict Evidence

**C-1 — Per-paper enablement cannot be frozen through the fixed seam.**

Governing requirements:
- Child plan `discovery-paper-02-capture.md:65`: “enabled在每篇scope开始时读取，关闭不丢弃该篇已经取得的正文；容量阈值每次写入检查。”
- Child I-4 at lines 31–35 requires preserving original outcomes and independent capture behavior; acceptance line 106 retains first and cancellation results.
- Brief lines 12–14 constrain edits to the eight authorized paths and require the child 01 service as the only archive SQL/filesystem seam.

Existing implementation:
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt:136`: `saveBody(attemptId: Long, bytes: ByteArray, mediaType: String)` has no scope authorization/snapshot argument.
- The same file at lines 145–146 reads `val settings = repository.setting()` and then executes `if (!properties.allowed || !settings.enabled) throw ArchiveException("ARCHIVE_DISABLED", 409)`.
- Thus an enabled scope that begins its request, completes the response, and encounters a concurrent settings disable before `saveBody` cannot save the response through this seam. This conclusion is static code evidence, not a claimed executed concurrency test.
- Treating the exception as ordinary download failure, consuming without saving, waiting for re-enable, temporarily rewriting settings, or issuing direct SQL/file writes would not meet the approved contract.

**C-2 — Re-extraction cannot update latest through `finishExtraction`.**

Governing requirements:
- Child plan `discovery-paper-02-capture.md:65`: outcome schema is `{schemaVersion:1,first:{...},latest:{...}}`; “first 只设一次；latest 随真实抽取更新”.
- Child I-4 at line 32 and acceptance line 106 prohibit overwriting the original first result.
- Child plan line 52 prohibits a second SQL/file writer; authorized paths at lines 92–99 exclude the existing archive service and repository.

Existing implementation:
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt:130–132` delegates `finishExtraction(recordId, outcome)` to the repository.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt:31–34` reuses a record for the same execution/source/paper key.
- Repository lines 65–67 contain:

```kotlin
if (jdbc.update("UPDATE discovery_paper_archive_record SET outcome_json=?,revision=revision+1,updated_at=? WHERE id=? AND outcome_json IS NULL", json, db(now), id) == 0 && record(id) == null) throw ArchiveException("NOT_FOUND", 404)
return record(id)!!
```

The first write can persist the envelope, but every subsequent write for that record is ignored. Constructing a new `latest` value in the capture cannot persist it, irrespective of the input JSON. Creating a different paper key would violate the required existing `envelopeForPaper` item key. This is static code evidence, not a runtime test claim.

### Smallest Missing Authorization

Approve a bounded plan amendment authorizing changes to:
1. `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` so a scope accepted while enabled can finish saving its response after a settings disable, while each write still enforces current capacity/safety constraints.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` so the sole persistence seam atomically preserves stored first and updates latest for real re-extraction.

The amended contract must explicitly define the service seam and first/latest persistence semantics and reconcile child 01 compatibility. Existing child 02 capture tests can exercise these behaviors, but any required child 01 test edits also need explicit authorization. No amendment or implementation choice was made here. Silently dropping either requirement is not an acceptable alternative.

Controller acknowledgment during this execution explicitly instructed: do not edit excluded child 01 files, work around the seam, or weaken acceptance; finalize this pre-edit PLAN_CONFLICT without a product commit or checks. Controller owns the subsequent PAUSED_FOR_HUMAN state.

### Commands

All shell commands ran with the exact target worktree as `cwd`.

| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md` | PASS; exit 0, run at entry and handoff | Canonical path and SHA-256 unchanged; 13,657 bytes. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master` | PASS; exit 0 | Expected root/branch/git-dir and HEAD reported. |
| `git status --short` | PASS; exit 0 | Entry state: modified controller ledger; untracked `children/02/` evidence directory. Preserved. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-branch fast/discovery-paper-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master` | PASS; exit 0 | Handoff worktree/root/branch/git-dir checks passed; HEAD remains `e0fd89d8c14c659001cf7a4da469e4275ac066d9`. |
| `git diff --name-only 74c00bdab46796084c5d4a1a7a3c572b7bee6915 HEAD` | PASS; exit 0 | Output: child 01 `execution.md`, `fix-log.md`, `verify-log.md`, and master `ledger.md`; no product/test paths. |
| `git diff --name-only` | PASS; exit 0 | Output: `docs/plans/fast/discovery-paper-master/ledger.md` only (pre-existing controller change). |
| `git diff --cached --name-only` | PASS; exit 0 | Empty index. |
| `git merge-base --is-ancestor 74c00bdab46796084c5d4a1a7a3c572b7bee6915 HEAD` | PASS; exit 0 | Child product base is an ancestor of target HEAD. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest -DmigrationIt=true` | NOT RUN | Pre-edit authority conflict; controller explicitly confirmed no checks required. Executed test count: 0; framework skipped count: N/A (no invocation). |
| Separate changed-path smoke/harness | NOT RUN | No changed product path; no scaffolding created. |

### Changed Files

- `docs/plans/fast/discovery-paper-master/children/02/execution.md` — this authorized execution report only.
- Product/test boundary unchanged. No staging, product commit, evidence commit, push, merge, branch change, or history rewrite.
- Controller ledger, brief, other logs, child 01 code, all plans and later children remain untouched by this executor.

### Deviations

- Implementation and required checks did not begin because the authorized storage interface cannot satisfy the exact approved T-1 contract.
- No acceptance reduction or plan amendment was made; no completion or independent verification result is claimed.

### Freshness

- Plan identity rechecked: YES.
- Worktree identity rechecked: YES.
- Reported new commits reachable from target branch: N/A; no new commit. Existing product base ancestry confirmed.
- Required commands run this invocation: NO; pre-edit authority conflict, acknowledged by controller.
- Historical evidence used only as baseline: YES; no historical checks substituted.

### Remaining Blocker

Missing explicit authorization and amended immutable plan bytes for the existing service/repository changes described above.

### Next Action

PLAN_CONFLICT → controller records PAUSED_FOR_HUMAN; obtain a human-approved bounded amendment, then restart `execute-p` with the new exact plan identity. Do not advance to child 03.

## Epoch 2

### Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md`
Plan SHA-256: `53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md@53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4`
Execution epoch: NEW — fresh amended-plan identity; no epoch1 product work reused.
Approval basis: user invocation and brief Epoch 2 resume contract; A1/A2 amendment at `87c5268976807c19c165fe50c91ba3372ed0075d`, including T-1a and ten authorized files.
Executor: PaperCaptureImplementerE2; execute-p implementation/evidence collection, not independent verification.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`
Target branch: `fast/discovery-paper-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`
Pre-execution code SHA: `74c00bdab46796084c5d4a1a7a3c572b7bee6915`
Pre-execution HEAD: `5eea9424b9081be7de5f7277c9a4e7cfd312ec13`
Post-execution code SHA: `030d96b227111587ba591446e9aeb64b1b0e85d7`
Evidence HEAD: `030d96b227111587ba591446e9aeb64b1b0e85d7` — this append-only report is intentionally uncommitted.
Implementation boundary: `74c00bdab46796084c5d4a1a7a3c572b7bee6915..030d96b227111587ba591446e9aeb64b1b0e85d7`; implementation commit subject exactly `feat(fast-p): implement 02`. Product commit contains the ten authorized product/test paths and no evidence.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 / I-1 / I-2 / I-4: full capture scopes, independent legacy samples, original bytes and structured snapshots | IMPLEMENTED | Capture, ExpertDiscoveryService, CaptureTest, FlowIT | Fresh tests exercise six sources, nested/task-isolated scopes, original PDF/XML/HTML/CORE bytes and SHA256, no URL, failure/oversize classification, first/latest parserVersion object, disabled delegation, and captures beyond legacy sample quotas. |
| T-1a: service-issued scope permit and live storage/deployment checks | IMPLEMENTED | ArchiveService, CaptureTest, FlowIT | CaptureTest demonstrates disabling after beginAttempt still saves, disabled new scope makes no record, direct saveBody retains its enabled guard, live low disk/deployment disable blocks issued permits. FlowIT toggles enabled during actual fake HTTP and checks preserved response. |
| T-1a: atomic frozen first and updated latest | IMPLEMENTED | ArchiveRepository, CaptureTest, FlowIT | Repository SELECT FOR UPDATE and transactional merge; repeated capture updates latest, concurrent repository writers preserve baseline and analysis; legacy flat outcome does not acquire a fabricated first. |
| T-2 / I-1 / I-2: download hooks | IMPLEMENTED | PDF, EuropePMC, PMC OA, Capture | Original full response bytes saved synchronously, XML retry gets distinct attempts, multiple failed PDF addresses before success, CORE embedded text has no added HTTP, fallback path captures embedded text then PDF. |
| T-2 / I-3: SYNC worker boundary and complete-page join | IMPLEMENTED | ExpertDiscoveryService, FlowIT | Two workers issue fake HTTP; one injected storage failure while another is delayed; error reaches caller only after delayed worker ends; final progress processedCount=0, no cursor save, no expert writer calls; recovery consumes both papers. |
| T-2 / I-3: QUEUE storage backpressure | IMPLEMENTED | DiscoveryPipelineService, ExpertDiscoveryService, FlowIT | Production processJob and real transactional queue repository/SQL: PENDING, attempts remain 2, nextAttemptAt +60 seconds, no request while low-space gate blocks; restored capacity succeeds; stale lease saveExtraction CAS fails. |
| I-2 / I-4: cache and cancellation | IMPLEMENTED | FlowIT; existing suites unchanged | Cached extraction_json consumes without HTTP or archive writes despite blocked disk; cancellation after response retains readable stored bytes and zero page consumption/no expert writer invocation. |
| T-3 / A-1–A-3 machine fixtures | IMPLEMENTED | CaptureTest, FlowIT | Six source beans plus production capture/expert registration in an isolated Spring context; disposable MySQL, real temporary files, fake loopback HTTP; final guarded run and separate forked smoke below. Human acceptance remains PENDING. |

### Fresh Commands and Results

All repository commands used the exact target worktree as cwd. Commands below are execution self-checks, not an independent PASS.

Required combined command (equivalent to both brief commands, without duplicate Maven-bound Node checks):

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest -DmigrationIt=true
```

| Command / invocation | Result | Evidence |
|---|---|---|
| execute-p plan_identity.py and worktree_identity.py at entry | PASS, exit 0 | Canonical plan/worktree/branch/git-dir bound; hash above; initial index empty and only controller ledger dirty. |
| Required combined command, initial invocation | FAIL, exit 1; tests not reached | `artifact://83`: FlowIT test compilation required explicit ElasticsearchProperties constructor arguments. Authorized fixture constructor wiring corrected. |
| Required combined command, second invocation | FAIL, exit 1; JVM 341 run, 5 failures, 0 errors, 0 skipped | `artifact://85`: four FlowIT fixture/path failures and one legacy PDF diagnostic-header regression. Details and network-isolation deviation below. Node phase not reached. |
| Required combined command, final invocation after final product/test edits | PASS, exit 0 | `artifact://87`: JVM 341 run, 0 failures, 0 errors, 0 skipped; Maven-bound Node 1807 passed, 0 failed/cancelled/skipped/todo, 334 suites; app.js and task-modal-runtime.js syntax checks passed. Finished 2026-10-23 23:51:09 +08:00. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:java -Dexec.mainClass=com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher -Dexec.classpathScope=test -Dexec.cleanupDaemonThreads=false` | PASS, exit 0, with shutdown warning | `artifact://89`: smoke assertions and BUILD SUCCESS; Maven exec:java classloader shutdown emitted Testcontainers PathUtils NoClassDefFoundError. Switched launcher invocation to a forked JVM below, with no code change. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher'` | PASS, exit 0 | `artifact://91`: distinct guarded smoke process, real MySQL/temp files/fake HTTP; BUILD SUCCESS, no classloader warning. Finished 23:51:59 +08:00. |
| `git diff --check` | PASS, exit 0 | After final implementation edits; `artifact://92`. |
| Plan/worktree identity helpers before staging and commit; explicit-path git add; `git commit -m 'feat(fast-p): implement 02'` | PASS, exit 0 | `artifact://93`: ten files committed; `030d96b227111587ba591446e9aeb64b1b0e85d7`. Evidence and controller ledger excluded. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor HEAD refs/heads/fast/discovery-paper-master` | PASS, exit 0 | New implementation commit is target HEAD and reachable from target branch. |

Final JVM suite counts (all failures/errors/skips zero):

| Suite | Tests |
|---|---:|
| DiscoveryPaperArchiveCaptureTest | 8 |
| DiscoveryPaperArchiveFlowIT | 8 |
| DiscoveryPaperArchiveRepositoryIT | 4 |
| DiscoveryPaperArchiveServiceTest | 15 |
| DiscoveryPaperArchiveControllerTest | 7 |
| DiscoverySampleCaptureTest | 4 |
| DiscoverySampleStoreTest | 7 |
| DiscoveryPipelineServiceTest | 52 |
| ExpertDiscoveryServiceTest | 194 |
| PdfEmailExtractorTest | 42 |
| Total | 341 |

Counts are from the fresh `target/surefire-reports/*.txt` files selected by suite name using `grep` tool pattern `Tests run:`; Node totals are from `artifact://87:7248-7275`, not historical baseline.

Separate forked smoke output:

```text
CAPTURE_SMOKE bytes=1024 sha256=a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02 first=frozen latest=updated savedAttempts=2 sharedFiles=1 blockedHTTP=0 queue=PENDING attempts=2 delay=60s cursor=unchanged
```

The smoke starts a new isolated Docker MySQL database, uses production Spring capture registration and queue SQL, compares downloaded/stored bytes and SHA256, repeats a real capture to observe immutable first and changed latest attempt IDs, and then checks storage-blocked queue backoff with no new HTTP and unchanged stream cursor. Its launcher remains in the authorized FlowIT fixture; no throwaway scripts were left behind.

### Deviations and Corrections — Including Accidental External Requests

1. **The second command was not fully network-isolated.** Its original FlowIT fixture supplied synthetic `paper.downloadUrl` to ARXIV, but production `ArxivDataSource.extractAuthorEmails` ignores that field and constructs `https://arxiv.org/pdf/$arxivId`. The fixture initially passed real BoundedFulltextHttp without a non-loopback guard. The observed log in `artifact://85` reports SYNC ARXIV two papers with `PDF_DOWNLOAD_FAILED=2, HTTP_404=2`, and the cancellation case one paper with `PDF_DOWNLOAD_FAILED=1, HTTP_404=1`; those fake-loopback handlers were not reached. The all-six-source test also reported its attempts-not-all-SAVED assertion failure after the ARXIV case. The hard-coded URI construction plus these observations establishes accidental external synthetic paper requests; the earlier run must not be represented as isolated.
2. Derived synthetic targets from that fixture/code were `https://arxiv.org/pdf/10.fixture/one`, `https://arxiv.org/pdf/10.fixture/two`, and `https://arxiv.org/pdf/10.fixture/ARXIV`. These are code-derived URLs, not packet-captured wire logs; an exact independent outbound request count was not recorded. No real paper IDs, Apollo calls, paid API credentials, production database, production application context, or deployment were used. No outside requests were replayed to confirm the error. Main was notified immediately after diagnosis and instructed that the deviation remain in this report for independent classification.
3. **Final fixture enforcement is fail-closed.** The PDF test transport rewrites only synthetic ARXIV addresses to loopback **before** BoundedFulltextHttp creates its deadline client. It checks the final host equals `127.0.0.1`. The shared RestTemplate also has a fail-closed non-loopback interceptor inherited by bounded clients, covering XML and other fixture HTTP paths. This avoids relying on a request factory that the deadline transport replaces. The final full command and both smoke invocations used this enforcement. No product URL/source-selection behavior was changed.
4. CORE queue payload deliberately omits embedded fullText when a public URL exists in the existing envelope contract. The embedded-text-plus-PDF-fallback test therefore exercises the real SYNC CORE path via its existing CoreSearchPage seam, without changing queue payload/cache protocols.
5. The legacy PDF test exposed an unconditional archive-only raw-status getter evaluation even without a scope. The header observation is now conditional on a non-null archive attempt; the unchanged existing diagnostic-header test passes in the final command.
6. No amendment, scope expansion, acceptance reduction, parser-rule/cache-version change, migration change, child01 replay, later-child inspection, push, merge, or history rewrite occurred. Compiler/test failures were resolved within authorized files. No product/test edits occurred after the final successful combined command.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt` — independent scoped capture, original-byte hooks, blocked exception and first/latest envelope.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` — server-owned scope capability, completion after enabled toggle, live capacity/safety/deployment checks; direct-call guard retained.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` — transactional row lock and first-preserving latest update; legacy baseline semantics retained.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — injected capture, SYNC/QUEUE worker scopes, exception priority, await all started futures.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` — response hooks without replacing legacy sample hooks or disabled-path observations.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt` — raw XML hooks per retry, existing non-scope path preserved.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PmcOaDataSource.kt` — raw XML hooks.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` — only the extraction-call boundary returns storage-blocked jobs via existing CAS with +60 seconds.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt` — scope/permit/storage/snapshot/concurrency regressions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveFlowIT.kt` — source/HTTP/worker/SQL/cursor/cache/cancellation integration and separate smoke.
- This `execution.md` append is evidence only and excluded from the implementation commit. Pre-existing controller ledger modification remains untouched.

### Freshness

- Plan identity rechecked: YES; unchanged SHA-256 above.
- Worktree identity rechecked: YES; expected root/branch/git-dir.
- Reported implementation commit reachable from target branch: YES.
- Required commands run freshly after final product/test edits: YES.
- Historical evidence used only as baseline: YES.
- Implementation index/product working tree clean after commit; only authorized evidence append and pre-existing controller ledger are expected dirty.
- Machine execution evidence only; independent verification and A-1/A-2/A-3 human acceptance remain pending.

### Remaining Blocker

None for implementation handoff. The accidental-request deviation is explicitly retained for the independent verifier/controller to classify; it is not silently waived.

### Next Action

READY_FOR_VERIFICATION → controller runs independent `verify-p`/light verification against the exact amended plan and `030d96b227111587ba591446e9aeb64b1b0e85d7`, reviewing the recorded isolation deviation and fresh guarded command/smoke evidence.

## Epoch 2 — Terminal controller record
- Implementation: 030d96b227111587ba591446e9aeb64b1b0e85d7
- Fix round: 1; fix/code head: c37341aacfc0937e829132ad73d8af7fa24b4b97
- Independent terminal verdict: LIGHT_PASS_WITH_NOTES; PaperCaptureReverifierE2R1, attempt2; Required Action COMPLETE_CHILD.
- F-1 summary envelope consumer resolved. Fresh verifier evidence records342 JVM tests,1807 Node tests and separate guarded real-file/MySQL/HTTP capture smoke, all passing.
- O-1 historical accidental external synthetic ARXIV requests remains RECORD_ONLY, with no exact wire count and no replay; final guarded evidence is separate from that failed run.
- Human acceptance and whole-system verification remain unperformed. Proceed only after controller-owned artifact checkpoint succeeds.
