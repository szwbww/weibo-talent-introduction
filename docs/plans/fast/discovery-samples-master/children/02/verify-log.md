# Child 02 Verification Log


## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 02 — docs/plans/2026-10-18/discovery-samples-02-capture.md
Boundary: 115049811a715404674f02d03bcd4dcf7c0b723f..e40863f6d5864ac5c882e7cb83f49e639a84396e
Verifier: SampleCaptureLightVerifierE2
Epoch: Epoch2 Attempt1
Timestamp: 2026-10-19T00:13:04+08:00

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Approved child/master at cd6189360196700cf65c5e21ab3512ddedb98abc match current bytes; approval diff exit 0. Every manifest sourceBlob equals both the frozen upstream blob and the local import-checkpoint blob (42/42). All seven source/local per-commit product diffs are byte-identical and retain cherry-pick provenance. New implementation 3ccdcdddcdc68b2aef8a586582b5baa598c976ab..e40863f6d5864ac5c882e7cb83f49e639a84396e changes exactly the original eight authorized files. Frozen prerequisites are authorized imports, not unauthorized new development. |
| Plan and invariants | PASS | Explicit worker context and extraction-only scope: ExpertDiscoveryService.kt:938-942,1606-1620,1670-1689,2061-2132; queue fresh/cache/pause/CAS dispatch: DiscoveryPipelineService.kt:1017-1102. Original-response hooks: PdfEmailExtractor.kt:227-310 and EuropePmcDataSource.kt:97-129. Capture finally/HostBusy: DiscoverySampleCapture.kt:60-78. reserve→RAW→activate remains outside capture at ExpertDiscoveryService.kt:1825-1850. Direct synthetic artifacts, all nine new tests and disposable actual-path driver satisfy A-1/A-2/A-3 machine evidence below. |
| Required commands | PASS | Both exact final commands freshly executed under JDK11 after bounded inspection. Sample: exit 1, 418 tests / 0 failures / 53 unchanged baseline errors / 1 existing external-API skip; nine new sample tests PASS. Apollo: exit 1, 318 tests / 0 failures / 53 identical baseline errors / 0 skips; isolated MySQL integration 10/10 PASS. This gate passes baseline-relative requirements, not the Maven builds: both builds remain BUILD FAILURE. Receipts artifact://100, artifact://108, artifact://107, artifact://112. |
| Downstream interfaces | PASS | Canonical schemaVersion=1 / EXTRACTION, immutable context, identityExtractionVersion=20261004, per-attempt original bytes/settings and nullable status remain compatible with child03 C-3/C-4/C-5. Read consuming dispatch in DiscoverySampleCapture.kt:32-58,85-108 and DiscoverySampleStore.kt:148-203: PDF_URL/PMC_XML attempts and SAVED/NOT_AVAILABLE/BODY_LIMIT reach canonical writer validation; no new variant is silently dropped. Fresh package-byte checks pass; XML success status/contentType/pdfSettings are null. No queue DTO/schema/cache version changes. |

Evidence and authority:
- Read complete Epoch2 brief, amended child/master, C-1 through C-6, code audit and execution.md. Child SHA256 c03657226e2db0d587d171f738cde1a9ff129d190f91833be321863e4075ec31; master b0eb3402f6144f13f4e99de5082ca43eaa27136b2bc96e8f7cab3d676725ed7f. Bound HEAD e40863f6d5864ac5c882e7cb83f49e639a84396e, branch fast/discovery-samples-master, target worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master.
- Scope receipt: `git diff --name-status 3ccdcdddcdc68b2aef8a586582b5baa598c976ab e40863f6d5864ac5c882e7cb83f49e639a84396e` reports only DiscoveryPipelineService.kt, EuropePmcDataSource.kt, ExpertDiscoveryService.kt, PdfEmailExtractor.kt and their corresponding four Test.kt files (artifact:// scope command output). `git diff --name-status -- src pom.xml scripts` and `git diff --cached --name-status` were empty before commands. Product/tests/plans/index/commits/branch were not mutated by this verifier.
- Frozen import receipt: Python iterated every manifest files[] entry, used `git show <sourceCodeHead>:<path>` and `git show 3ccdcdddcdc68b2aef8a586582b5baa598c976ab:<path>`, recomputed Git blob SHA1 and asserted equality with sourceBlob. Output SOURCE_BLOB_EQUALITY 42. It compared exact `git diff <sha>^ <sha> -- src pom.xml scripts` output for each source/local pair: COMMIT_DIFF_EQUALITY 7. `git log --format=fuller d2e1660961d386ce90cde80e867d09f542aa77f2..3ccdcdddcdc68b2aef8a586582b5baa598c976ab` confirms retained source trailers and ordering. These are independent provenance checks, not reliance on executor assertions.

| Frozen source commit | Local import commit |
|---|---|
| 772429192fdab1fd7ac75cb084f08f45aec5c965 | 2c2bc70a597b072bd9b646a9d5ad020355f062c6 |
| 5c05491b963c4d1d9793ebdc1f8469b175f170d8 | 8bc9649c6c2c28df9ea724aa0b8f38624312e8f5 |
| 69a236aa0b15b07755d42c713f24da944a7727e6 | 138d54c3e1741238784d3889e6c95f55353043c1 |
| 98c7b78fde54f69b72f175b94b2ab0eed25d86ab | 25cb626c0c445dd0c2e53b2718d1fe57e7ffa40c |
| a0103c2ea9284c030ad0b0b10c4177474c4f4d47 | c5d3083e75acf11d6ea23977b3721a08c4229345 |
| 083fce2f4b97c88b89a226394ebbf1342001a2d4 | 5cf1b625b13bdb1acb58d2efa91ebd30db136f90 |
| 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 | 3ccdcdddcdc68b2aef8a586582b5baa598c976ab |

Fresh commands (run sequentially in the target worktree):
1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,PdfEmailExtractorTest,EuropePmcDataSourceTest,OpenAlexDataSourceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test` — exit 1, 03:49, finished 2026-10-19T00:06:59+08:00; 418 run, 364 passed, 0 assertion failures, 53 baseline errors, 1 existing optional external API skip. Fresh Surefire testcase error-name set equals /tmp/child02-epoch2-baseline.json exactly (53, no additions/removals); every new sample testcase passed. Receipts artifact://100 and artifact://107.
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryVerificationIntegrationTest -DskipNodeTests=true -Dapi.version=1.44 test` — exit 1, 04:17, finished 2026-10-19T00:11:32+08:00; 318 run, 265 passed, 0 assertion failures, 53 baseline errors, 0 skipped. Exact error-name set again equals the recorded post-import baseline. Fresh isolated mysql:8.0.36 container 4037f26a8d84b86056b41a010b44567bbfdceaa7499252482c0f82a967633bd9, jdbc:mysql://localhost:32896/verification_registration; migrated to V158, DiscoveryVerificationIntegrationTest 10 run / 0 failures / 0 errors / 0 skips. Receipts artifact://108:2213-2224,2448,2512 and artifact://112. No production/shared DB or real provider used.
3. `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp <java.class.path from fresh Surefire XML> -Dsample.acceptanceDir=/tmp/discovery-sample-verifier-smoke-epoch2 <temporary-directory>/Child02VerifierSmoke.java` — exit 0. Disposable source-file driver constructed fresh fixtures and called setUp plus the four actual-path scenarios listed below. Output ACTUAL_PATH_PASS for all four, SMOKE_OK methods=4 retainedPackages=9; driver removed (DISPOSABLE_DRIVER_REMOVED True). No repository scaffold. It exercises real discover/tick/extraction/hooks/store with mocked providers/ES/registration seams, not a launched online application; real isolated MySQL coverage is supplied separately by command 2.
   - ExpertDiscoveryServiceTest.sample sync binds real execution inside reused workers and preserves extraction business results
   - ExpertDiscoveryServiceTest.sample IO failure cannot commit synchronous checkpoint over Apollo reserve or activate failure
   - DiscoveryPipelineServiceTest.sample queue fresh cache pause and failed save keep extraction separate from consumption
   - DiscoveryPipelineServiceTest.sample IO failure leaves Apollo queue reserve and activation failures retryable with cached extraction

Direct artifact inspection:
- Read actual /tmp/discovery-sample-acceptance/capture/cases.json and apollo.json, then actual canonical SYNC XML and QUEUE PDF manifests. Independently checked every package in capture/samples with Python against fixture bytes and SHA256: CANONICAL_PACKAGES 9; execution9001=4, execution9002=4, execution9011=1; BODY_AND_FIXTURE_SHA_EQUAL 7. Both 9001 and 9002 have PDF/HTML/XML SAVED bodies and a 403 metadata-only NOT_AVAILABLE observation; schema/stage, execution-directory identity, sampleId, settings, byte counts and null XML success headers all passed. No fabricated result fields were used.
- cases.json SHA256 08734404d9b1e3c5e6e2ee6f7fb18b13f0427a4474d39a63d246088d62ea0944; apollo.json SHA256 38cf452d7f50f0474da0e6133e7f0ac67aaf072a4e8080cfad27bcf9f3060484.
- SYNC serial and reused-worker concurrency2 business/request comparisons equal=true, additional network requests=0, crossExecutionMixups=0. Fresh QUEUE has four requests/four retained packages. cache-hit newDownloads=0/newSamples=0; pause-after-extraction and save-extraction-lost each retain EXTRACTION with consumed=0. Other-source newSamples=0. HostBusy returns PENDING with attempts=0 and no sample. Identity version stays 20261004. PDF/HTML stream/callback request sequences, retry/callback-exception equivalence, oversized no-half-body, BODY_LIMIT and XML unknown headers are directly asserted and present in cases.json.
- For both SYNC and QUEUE, apollo.json reserve-failure rawWrites=0/activated=0/checkpointCommitted=false; activate-failure rawWrites=1/activated=0/reservedRecoverable=true/checkpointCommitted=false. All four cases diagnosticIoErrors=1, decision=IO_ERROR and business equal=true. Queue jobs remain RETRY_WAIT with attempts=1 and cached extraction asserted by the test. Capture is closed at reserve/activate (explicit outside-scope assertions). No business failure was suppressed or page checkpoint advanced.
- Command receipts and counts are fresh final-state evidence. The earlier post-import 312-test/53-error baseline and executor final counts were used only for comparison, not relabelled as fresh verifier results or rerun solely to reconfirm known errors. Required exact final commands were each run once; no fixes, extra baseline run, build, lint or formatter was performed.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 — Both mandatory Maven commands remain exit 1 / BUILD FAILURE solely because of the same 53 already-observed ExpertDiscoveryServiceTest fixture errors (null automatic-admission argument from historical policy stubs). Fresh error-name equality holds against the recorded post-import baseline; there is no new assertion failure. The sample command also retains the existing optional real Europe PMC API skip. These are explicitly baseline observations outside authorized child02 repair, not green builds, and were not repaired or investigated beyond classification.

### Required Action
- COMPLETE_CHILD
