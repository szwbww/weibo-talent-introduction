# Child 07 — Execution log

## Epoch 1 — discovery and bounded dependency

- Executor: `Recheck07Implementer`, sole product writer; controller `Main` orchestration only.
- Original invocation identity: `docs/plans/2026-10-24/discovery-recheck-07-pipeline.md@f07ebf6c63e7e3c1e084a79fcbde47ce51a5ed0f69343b569556562a3d332ad4`, original ten authorized paths. Product base `feb58f65512484c742f7bf5f0edb6ec0e2207df8`; initial governance HEAD `1bdd2d7ab5f034ce529ec9105fae1f350ca2594d`.
- The necessary I-5 dependency was not narrowed to ORCID/Apollo: academic `collect` enters the existing OpenAlex batch author/ORCID client, which may subsequently dispatch works and patent HTTP internally. A Worker entry-only ownership check cannot authorize those later dispatches after cancellation.
- No extra-file edits were made before controller release. A7-authorized plans-only amendment `7bcefb3815f3f3d318c8e619477cab0d67ad6c91` and ledger/brief A8/A9 governance HEAD `ef37ddb5e396078257749416f7de35c5cc4b0078` released the four exact existing academic/client/test paths, total fourteen; original scoped drafts were retained, not reset.

### Exact bounded search/reference receipts

Repository root for the following tool calls: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`. Line references below describe the amended source inspected during epoch 2, not claims about old byte offsets.

1. `functions.glob(path="src/test/**/*OpenAlex*Test.kt;src/test/**/*AcademicEvidenceServiceTest.kt")` returned:

   ```text
   src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceServiceTest.kt
   src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt
   src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt
   ```

   The amendment reuses the existing service/client tests above; `OpenAlexRequestPolicyTest.kt` remains outside the edit scope.

2. `functions.grep(pattern="batchEnrichByAuthorIds|batchEnrichByOrcids|academicEvidenceService\\.collect|academic\\.collect|fun beforeRequest|beforeRequest:|beforeDispatch:", path="src")` produced these production-reference excerpts (the same search also returned the existing Mockito stubs and client regression invocations, which were not treated as unused exports):

   ```text
   DiscoveryVerificationWorker.kt:387 academic.collect(listOf(snapshot), academicMode, payload.executionContext!!.evaluationAt,
   DiscoveryRecheckAcademicEvidenceService.kt:73 client.batchEnrichByAuthorIds(authors, RequestKind.HISTORY_ENRICHMENT)
   DiscoveryRecheckAcademicEvidenceService.kt:74 else client.batchEnrichByAuthorIds(authors, RequestKind.HISTORY_ENRICHMENT, beforeDispatch))
   DiscoveryRecheckAcademicEvidenceService.kt:76 client.batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT)
   DiscoveryRecheckAcademicEvidenceService.kt:77 else client.batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT, beforeDispatch))
   ExpertDiscoveryService.kt:3420 openAlex.batchEnrichByAuthorIds(ids, kind)
   ExpertDiscoveryService.kt:3424 openAlex.batchEnrichByOrcids(ids, kind)
   OpenAlexDataSource.kt:533/543/547 batchEnrichByOrcids(one/two/guarded-three arguments)
   OpenAlexDataSource.kt:612/620/624 batchEnrichByAuthorIds(one/two/guarded-three arguments)
   OpenAlexRequestPolicy.kt:686 fun beforeRequest(kind: RequestKind): Permit = reserveInternal(kind, Operation.LIST, null)
   ```

3. `functions.grep(pattern="getJson|beforeDispatch|requestPolicy\\.(reserve|onResponse|onTimeout)|restTemplate.exchange|catch|while|retry|fetchTitles|batchEnrichIdentities", path="src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt:72-104,624-668,687-760,797-825")` returned the narrow dispatch/retry chain:

   ```text
   :75 Every retry goes through this method again, so a retry reserves a second time.
   :77 private fun getJson(..., beforeDispatch: (() -> Unit)? = null)
   :78 beforeDispatch?.invoke()
   :79 val permit = requestPolicy.reserve(kind, operation, url)
   :80 if (permit is Permit.Deferred) throw OpenAlexBudgetDeferredException(permit.reason, permit.retryAt)
   :84 beforeDispatch?.invoke()
   :85 restTemplate.exchange(url, HttpMethod.GET, null, JsonNode::class.java)
   :89-91 catch (e: Exception) { requestPolicy.recordTimeout(permitId); throw e }
   :628 return batchEnrichIdentities(authorIds, url, kind, beforeDispatch)
   :695 getJson(kind, Operation.LIST, url, beforeDispatch)
   :711-712 catch (e: java.util.concurrent.CancellationException) { throw e }
   :733 results[identity] = enrichmentOutcome(node, kind, beforeDispatch)
   :801 fetchTitles(recentWorksUrl(worksUrl), kind, beforeDispatch)
   :806 fetchTitles(patentWorksUrl(worksUrl), kind, beforeDispatch)
   :824 val parsed = parseAuthorEnrichment(node, kind, beforeDispatch)
   ```

   The companion chain search (`fun collect|batchEnrichBy|enrichmentOutcome|parseAuthorEnrichment|fetchTitles|getJson|beforeDispatch|requestPolicy.reserve|restTemplate.exchange|CancellationException`) also returned ORCID batch `:552 getJson(..., beforeDispatch)`, `:568-569` cancellation rethrow, `:589 enrichmentOutcome(..., beforeDispatch)`, and title `:454 getJson(..., beforeDispatch)` / `:466-467` cancellation rethrow. Thus author, works, patent, subsequent identities, and a caller retry cross the same existing permit/wire seam. No new client or global request-policy rewrite was needed.

4. Existing verification callback conventions were checked with `functions.grep(pattern="before|permit|request:|reserve|completed|fun fetch|fun search|fun detail", path="src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClient.kt;src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt")`:

   ```text
   OrcidVerificationEvidenceClient.kt:64-66 read(snapshot, completed, onResponse, reserve)
   :85 val permit = reserve(path) ?: return ...
   :89 transport.request(..., permit, VerificationProvider.ORCID)
   :93 A failed acknowledgement throws ... never issue the next HTTP request.
   :94 onResponse(endpoint, permit, doc, result)
   ApolloPeopleSearchClient.kt:21 One reservation permits one wire request.
   :25-26 RequestPermit.consume requires matching provider and one-shot CAS.
   :72 permit.consume(provider)
   :123 detail(id, permit) / :150 search(query, permit)
   ```

   The academic optional guard preserves those existing ORCID/Apollo contracts and ordinary unguarded enrichment exports, and preserves `RequestKind.HISTORY_ENRICHMENT` and the existing budget/unknown-reservation settlement.

## Epoch 2 — immutable execution binding

- Execution epoch: `NEW`, epoch 2, `fix_round=0` (not a verifier repair round).
- Canonical plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-07-pipeline.md`.
- Plan identity helper receipt: SHA-256 `a1c1182543a6613b1ba206d564cfa76ba6dd20fa756e3caa04b6976ad0f01d4d`, size `13185` bytes. Execution ID is the canonical path above plus `@a1c1182543a6613b1ba206d564cfa76ba6dd20fa756e3caa04b6976ad0f01d4d`.
- Fresh epoch-2 worktree helper receipt (`artifact://1101`): root `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`; branch `fast/discovery-recheck-master`; HEAD `ef37ddb5e396078257749416f7de35c5cc4b0078`; Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`; common Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Approval basis: current child07 invocation plus controller's explicit A7 amendment release against commit `7bcefb3815f3f3d318c8e619477cab0d67ad6c91`. Product base remains `feb58f65512484c742f7bf5f0edb6ec0e2207df8`; no child08 orchestration entry point is implemented.

## Execution Result: READY_FOR_VERIFICATION

This is implementer execution evidence, **not** an independent verification PASS or human acceptance.

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-07-pipeline.md`
- Plan SHA-256: `a1c1182543a6613b1ba206d564cfa76ba6dd20fa756e3caa04b6976ad0f01d4d`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-07-pipeline.md@a1c1182543a6613b1ba206d564cfa76ba6dd20fa756e3caa04b6976ad0f01d4d`
- Execution epoch: `NEW` / epoch 2 / `fix_round=0`
- Approval basis: child07 invocation and explicitly released A7/A8/A9 amendment, identity commit `7bcefb3815f3f3d318c8e619477cab0d67ad6c91`
- Executor: `Recheck07Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`
- Target branch: `fast/discovery-recheck-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master@fast/discovery-recheck-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`
- Pre-execution product code SHA: `feb58f65512484c742f7bf5f0edb6ec0e2207df8`
- Epoch-2 pre-product governance HEAD: `ef37ddb5e396078257749416f7de35c5cc4b0078`
- Post-execution code SHA: `93e3cda5e661a98fe045bbc100ff82c769b1f159`
- Product commit subject: `feat(fast-p): implement 07`
- Evidence HEAD: no executor evidence commit; target HEAD is the product SHA above. This execution log remains deliberately excluded for the controller's separate evidence commit.
- Product boundary: `ef37ddb5e396078257749416f7de35c5cc4b0078..93e3cda5e661a98fe045bbc100ff82c769b1f159`; retained child base is still `feb58f65512484c742f7bf5f0edb6ec0e2207df8`.

### Task Status

| Requirement | Status | Implementation and exercised evidence |
|---|---|---|
| I-1 shared authority / AUDIT separation | IMPLEMENTED | Existing `Worker.process → Rules → policy/Gate → Commit` remains authoritative. Actual SQL runtime covers ordinary Registration, paper-backed DIRECT, REFRESH Apollo and cached full-search REUSE. AUDIT produced no admission/candidate/application business records; ordinary academic collection uses frozen profile data, not a new external enrichment pass. ENFORCE uses existing admission and projection services. Original protection regressions remain unmodified and pass. |
| I-2 genuine eight steps / historical reads | IMPLEMENTED | New trace repository writes existing V161 table. Activated items receive idempotent eight slots; RESERVED/READY and true historical reads do not synthesize steps. Runtime observes real stage results, missing-paper continuation, identity blocker at 3, qualification-conflict blocker at 5, and DIRECT_SUFFICIENT skip at 6. Four real-MySQL trace ITs cover immutable terminal attempts, retry/takeover generations, stale token rejection, competing finishes and bounded/redacted payloads. |
| I-3 frozen facts/time / compatibility | IMPLEMENTED | Payload adds `executionContext`; frozen evaluation time and source refs flow through Worker and Gate. Gate tests cross a birthday with the later live clock while recomputing at the frozen instant, reject changed hashes, and preserve absent-context semantics. REUSE rebuilds available raw ORCID responses without re-signing identity/fact hashes, preserves fetchedAt, and labels missing raw responses `ORCID_NOT_REPARSED`. Runtime checks archive first record and body SHA unchanged and actual Rules/Worker outcome/qualification/route/person parity. |
| I-4 saved settings / zero-HTTP REUSE / old cursors | IMPLEMENTED | New reviewInput carries frozen cache/full search and expected revision, never inherited query progress. Runtime checks direct and full-search REUSE HTTP/request-count zero, old cursor not copied, free Apollo detail HTTP zero, and 429 RETRY_WAIT without transient-failure increment. Existing ordinary/in-flight APOLLO_SEARCH, replay, repository and rule-version compatibility suites pass. |
| I-5 cancellation / CAS / diagnostic / projection | IMPLEMENTED | New review dispatch checks control/lease ownership, including academic author/works/patent dispatch before permit and again after policy wait. Late verification responses complete only their exact dispatched incomplete attempt diagnostic once; cancellation without a response leaves UNKNOWN_AFTER_CANCEL, not invented HTTP. Commit acquires run control under transaction before the original protected lock path, compares frozen nullable expected revision, and preserves pre-existing repository throwing semantics. Actual two-connection races exercise cancellation-first and commit-first; committed admission survives cancellation and projection failure/retry. Projection-only retry appends step 8 and performs no provider calls. |
| Downstream08 reservation boundary | IMPLEMENTED | READY rechecks stay RESERVED with no traces and cannot be claimed. Only RUNNING activation initializes steps. Trace read DTO exposes recorded=false for no-row history. No start API, Query/API integration, UI, or later-child orchestration was added. |

### Final Required Commands

All commands below ran in the exact target worktree **after the final product/test edits**. Each finished with exit `0`, JVM failures/errors/skips `0`, and its Maven-bound Node suite reported `1827` passed, `0` failed/skipped, followed by successful `node-check-app` and `node-check-task-modal-runtime`.

| Exact command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true` | Exit 0; 37 JVM tests | `artifact://1126`: TraceRepositoryIT 4, CommitServiceTest 6, WorkerTest 17, AdmissionGateTest 10. Real MySQL ITs ran, no skip. Maven 5:31; completed 2026-10-25 05:17:45 +08. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT -DmysqlIt=true` | Exit 0; 77 JVM tests | `artifact://1128`: RepositoryIT 24, ReplayToolTest 7, FlowIT 32, IntegrationTest 14. Real MySQL, including upgrade path, no skip. Maven 5:39; completed 05:23:29 +08. Regression files were not edited. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckAcademicEvidenceServiceTest,OpenAlexDataSourceTest -DmysqlIt=true` | Exit 0; 98 JVM tests | `artifact://1134`: AcademicEvidenceServiceTest 8, OpenAlexDataSourceTest 90. Includes actual loopback cancellation and ordinary budget/RequestKind behavior; Maven 2:47; completed 05:26:22 +08. |

The named JVM sets total 212 tests. The same 1827-test Node suite ran in each command; these are repeated executions, not 5481 distinct JS cases. No mandatory IT was disabled or skipped.

### Independent Disposable Runtime — not JUnit

Dependency classpath preparation (exit 0):

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/child07-runtime-classpath.txt -DincludeScope=test
```

After all final Maven commands, the separate JDK11 JVM was launched by this actual Python subprocess call, with `cwd` equal to the target worktree:

```python
runtime07_classpath = 'target/test-classes:target/classes:' + pathlib.Path('/tmp/child07-runtime-classpath.txt').read_text().strip()
subprocess.run([
    '/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java',
    '-Dapi.version=1.40', '-cp', runtime07_classpath,
    'com.weibo.talentintroduction.discovery.service.RecheckPipelineRuntimeLauncher'
], cwd='/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master',
   text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
```

Exit `0`, elapsed `40.832s`; complete captured process output: `local://child07-runtime-epoch2.log`. This main invokes the reusable production-service scenario and strict transport scenarios directly; it does not invoke a JUnit method or prefill desired decisions. Final output:

```text
CHILD07_RUNTIME PASS actualRegistrationWorkerRulesGateCommitTrace=true realMySQL=true ordinaryPaper=DIRECT_CLEAR eightSteps=true AUDIT_businessWrites=0 REUSE_HTTP=0 originalFetchedAt=true frozenParity=true freeDetailHTTP=0 lateDiagnostic=once academicCancelWire=1 cancelled429Failures=0 cancelCommitBothOrders=true admittedProjectionRetryProviderHTTP=0 projectionAttempts=3 archiveFirstSHA=unchanged
CHILD07_ACADEMIC cancelDuringWait=false wire=1 cancelledRetryWire=0 ordinaryWire=3 requestKind=HISTORY_ENRICHMENT
CHILD07_ACADEMIC cancelDuringWait=true wire=1 cancelledRetryWire=0 ordinaryWire=3 requestKind=HISTORY_ENRICHMENT
```

Runtime boundaries and observations:

- Disposable MySQL 8.0.36 databases with real migrations through V161, actual transactional Registration/Repository/Trace/Worker/Rules/Gate/Commit/admission logic, plus the retained-source archive fixture with real disk bodies. Step and admission assertions query actual SQL.
- Provider payloads are fixtures served by actual loopback HTTP; original ORCID/Apollo request paths are redirected at the external transport boundary. OpenAlex uses the existing real client and request policy with an isolated funded in-memory external budget ledger. No production endpoint, production DB, account credential, or billable provider call was used.
- ES is the existing deterministic fixture boundary. RAW documents are seeded as pipeline input; candidate projection is attempted through the original writer, deliberately failed once with HTTP 500, then retried. That expected failure is present in logs and is not a hidden test failure. The admission revision remains committed and projection retry makes no new provider calls.
- Ordinary source-backed PAPER facts reach the real Worker. REUSE consumes actual frozen ORCID responses and separately a full cached Apollo search; no client call or copied old query cursor is needed. Missing identity/paper, changed RAW facts, frozen-null admission baseline versus a new revision-0 row, retained approval after an unsuccessful suggestion, 429 wait, and single late diagnostic are asserted.
- Cancellation-first and commit-first are separate JDBC connections contending on the real run lock. Academic cancellation is triggered from the author HTTP handler; works/patent and next-identity dispatch do not follow. A second strict transport run cancels during policy waiting and checks the post-wait guard before wire. The ordinary unguarded path then still dispatches author/works/patent successfully with HISTORY_ENRICHMENT accounting.
- Servers, worker executors, SQL containers and archive fixture files are disposed by their finally/use boundaries; process exit 0 includes those returns. `/tmp/child07-runtime-classpath.txt` was removed successfully. No temporary source scaffold was added; the reusable standalone launcher stays inside the authorized Worker test file.
- This is not a production deployment, real external-provider data validation, full application startup, whole JVM test suite, WAR packaging, UI acceptance, or human A-1..A-4 acceptance. Child08 API/UI/start/cancel orchestration remains reserved. Independent light verification is still required.

### Bring-up Failures and Bounded Corrections

No failures were hidden or addressed by weakening mandatory tests:

- Early primary runs (`artifact://1103`, `artifact://1111`) each exited 1 with 37 JVM tests / one new runtime error / zero skips. Fixture RAW `externalIds` initially used a Kotlin string instead of ES's JSON object, then non-EMAIL document keys conflicted with the explicitly bound ORCID. The runtime fixture was corrected to production shapes; existing SOURCE_ID/fingerprint protections were not relaxed.
- Four focused diagnostic runs used the exact command `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=DiscoveryVerificationWorkerTest#child07*' -DmysqlIt=true -DtrimStackTrace=false`, each exited 1 with the single new runtime scenario error and zero skips (`artifact://1108`, `artifact://1114`, `artifact://1116`, `artifact://1118`). Bring-up also revealed a fixture sending Retry-After on HTTP 200, and a default RestTemplate error handler instead of the existing verification transport factory. Fixtures now use truthful response headers and the production verification factory. The expected projection 500 remains intentionally exercised. A preliminary standalone launch exited 1 on the original fixture assertion and is not counted as the final runtime proof.
- A pre-final primary run succeeded (`artifact://1121`, 37 JVM plus bound 1827 JS), but was superseded by the final commands after the compatibility correction below.
- The first compatibility command (`artifact://1123`) exited 1: 77 JVM tests, one failure, zero errors/skips. `DiscoveryVerificationRepositoryIT:735` requires the original `lockForCommit` to reject a cancelling run. That public behavior was restored; the commit service now checks/holds the run-control lock before entering the original lock method, so normal cancellation returns without an exception/rollback race. Only the already authorized Repository/Commit/CommitTest paths were changed. The regression test was not changed. All required commands were rerun freshly afterward.

### Changed Product Files

The product commit contains exactly the fourteen authorized paths (staged-scope receipt: 14 files, 1212 insertions, 95 deletions); no plan, ledger, execution/fix/verify log, migration, later-child or unrelated source file is included.

- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepository.kt` — new bounded step persistence/read DTO, ownership guards, append-only retry/projection attempts.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — delegate existing step APIs, activated initialization, authoritative cancellation convergence and guarded dispatched-diagnostic completion; preserve original protected commit lock contract.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — real stage traces, frozen context/source evidence, REUSE cache protocol, original REFRESH flow with cancellation and retry diagnostics.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — ordinary frozen paper refs/time, actual-activation traces, AUDIT no admission initialization.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` — frozen expected admission revision, run-lock cancellation authority, original protection/Gate, separate recorded projection retry.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt` — consume the additive context and the same frozen rule-evaluation instant.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceService.kt` — optional dispatch guard threading and truthful failure/wait metadata; live retry clock remains separate from frozen evaluation time.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` — narrow optional per-dispatch guard, post-policy-wait recheck, cancellation propagation while retaining ordinary APIs and RequestKind/budget behavior.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepositoryIT.kt` — new mandatory real-MySQL trace/ownership/CAS/bounds/late-diagnostic cases.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — real end-to-end disposable pipeline and reusable standalone main.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt` — frozen-null revision baseline and cancelled-control regressions.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt` — frozen birthday parity, absent-context compatibility and hash rejection.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceServiceTest.kt` — actual author/mid-chain cancellation, post-wait/retry proof helper and live retry/frozen-time separation.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` — invoke actual post-wait cancellation transport regression; retain existing client regressions.

### Final Identity / Commit / Cleanliness Gates

- `git diff --check` and `git diff --cached --check` both exited 0. Pre-staging index was empty; only explicit authorized product paths were staged.
- `execute-p/scripts/worktree_identity.py` ran with expected root, branch and Git directory immediately before staging and again immediately before commit; identity remained the exact retained worktree.
- Final `plan_identity.py` after commit returned the same canonical path, SHA `a1c1182543a6613b1ba206d564cfa76ba6dd20fa756e3caa04b6976ad0f01d4d` and size 13185. No plan change occurred within epoch 2.
- `git rev-parse HEAD` returned `93e3cda5e661a98fe045bbc100ff82c769b1f159`; subject is exact; `git merge-base --is-ancestor HEAD refs/heads/fast/discovery-recheck-master` exited 0. The final worktree helper reports that same HEAD and expected worktree/branch (`artifact://1142`).
- Product worktree/index are clean after the product commit. The only remaining paths in `git status --short` are the intentionally untracked child07 `execution.md`, `fix-log.md` and `verify-log.md`; the latter two were controller-owned pre-existing evidence files and were not edited by this implementer.
- No full review/verify/repair/fix skill was run, no production/publishing/push/merge/history rewrite occurred, and no unauthorized test weakening or later-child implementation occurred.

### Deviations / Handoff

- Necessary four-file dispatch dependency was explicitly amended and committed by the controller before editing, with a fresh epoch-2 immutable binding. No further scope amendment was needed.
- Current implementation has no known in-scope failing required command. Hand off code SHA `93e3cda5e661a98fe045bbc100ff82c769b1f159` and this report for a distinct verifier's independent light review. Controller owns the separate evidence commit and subsequent child08 orchestration.


## Epoch 2 — Controller terminal closing record
- Implementation93e3cda5e661a98fe045bbc100ff82c769b1f159; separate repair1f37ff178554cb19b3264f81ed4deac7a6b43bd8c; final code headf37ff178554cb19b3264f81ed4deac7a6b43bd8c.
- Fresh distinct Recheck07Reverify1 returned LIGHT_PASS/COMPLETE_CHILD, F-07-E2-01 CLOSED, no open findings or RECORD_ONLY items. Actual Worker SQL retains legacy absent context; controlled real Rules birthday comparison liveNOT_MET/frozenMET and explicit ordinary/recheck frozen inputs retained.
- All three commands freshly37/77/98 JVM, eachJS1827/syntax, zero failures/errors/skips, actual MySQL IT and separate actual pipeline/cancellation/academic guard runtime pass. Repair count1 for epoch2. Full runtime boundaries in verify-log.md retained; no real ES/production/whole-system acceptance claim.
