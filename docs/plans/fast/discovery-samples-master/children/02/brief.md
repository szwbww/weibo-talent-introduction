# Child 02 — Dependency-bound dispatch brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master
- Branch: fast/discovery-samples-master
- Exact approved plan: docs/plans/2026-10-18/discovery-samples-02-capture.md
- Plan identity: sha256:54e32195d75aa9daa3b7e7b69c23a25245e104cf5c09dac5a66d65054d2e9fec
- Product base: 115049811a715404674f02d03bcd4dcf7c0b723f
- State: PAUSED_FOR_HUMAN; no implementer acquired, no product edits.

## Blocking contract
Master I-5 requires Apollo05 merged and regression-complete before child 02 changes shared services. The external Apollo ledger observed child03 LIGHT_VERIFYING and child05 PENDING. Target worktree does not contain that integration. Explicit fast-p invocation permits local branch/worktree/commits but not merge/rebase. Do not silently implement against pre-Apollo services, import another branch, amend contracts or skip to child03.

## Resume contract
Require completed Apollo05 code/regression evidence and human-authorized integration boundary. Recheck exact approved plan bytes, retained worktree identity, child01 checkpoint and clean product index. Controller must complete the child02 brief with its unchanged authorized eight-file list, exact required sample plus Apollo regression commands, explicit source/worker execution context, original downloader semantics, cache/identity invariants, and reserve→RAW→activate exception/checkpoint invariants before dispatching an isolated execute-p implementer. No child01 replay is authorized or needed.

## Epoch2 — Approved active dispatch contract

The preceding epoch1 block is historical. This section and the exact amended plan govern current execution.
- Approved child plan: docs/plans/2026-10-18/discovery-samples-02-capture.md at commit cd6189360196700cf65c5e21ab3512ddedb98abc.
- Approved master: docs/plans/2026-10-18/discovery-samples-master.md at the same commit. Read entire child/master, contracts C-1..C-6 and code audit again. No unrecorded amendments.
- Product base remains 115049811a715404674f02d03bcd4dcf7c0b723f; pre-execution HEAD additionally contains pause/amendment/evidence commits.
- Use execute-p for this exact amended child. Agent records plan SHA256, worktree identity and epoch NEW for these bytes.

### Frozen prerequisite import authority
Read docs/plans/2026-10-18/discovery-samples-evidence/apollo05-import-manifest.json. Only the seven source product commits listed in sourceProductCommits may be brought in, in that order, using local `git cherry-pick -x <exact SHA>` as a retained-history linear import. Each source commit contains product/tests only. This is expressly authorized by the user's selected local integration option; it is not a merge/rebase.
Before starting, assert correct worktree/branch, clean product index, no cherry-pick already active and expected retained HEAD ancestry. Existing controller-owned fast-p evidence modifications are not yours.
After all imports, before sampling edits, verify each manifest path matches its frozen sourceBlob at upstream 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848; record all original→local SHA mappings and final import SHA in execution.md. No Apollo06/07, source docs/ledger, extra source branch state or manual modifications to imported feature design. A conflict requiring a behavioral decision is PLAN_CONFLICT; do not silently resolve outside original sampling scope.

### Authorized new sampling changes
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSourceTest.kt

### Commands and baseline
Use JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Docker for mandatory isolated MySQL IT uses DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and -Dapi.version=1.44. Do not access production/shared DB or real provider.
After import and before sampling changes, run:
`mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryVerificationIntegrationTest -DskipNodeTests=true -Dapi.version=1.44 test`.
Then perform all new sampling implementation. Skip intermediate builds/lint/tests/formatters except necessary required failing-before behavior checks. At final state freshly run both:
1. `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,PdfEmailExtractorTest,EuropePmcDataSourceTest,OpenAlexDataSourceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test`.
2. The exact Apollo integration regression baseline command above.
Historical source05 baseline includes 53 pre-existing ExpertDiscoveryServiceTest fixture errors (two-argument policy stubs vs three-argument call), optional skips and unrelated Node failures. Record actual command results/counts and classify unchanged baseline; do not relabel exit1 as success or repair unrelated fixtures. New sample behavior must pass.

### Required behavior, evidence and downstream seams
All child I-1..I-4 and master I-1..I-5 unchanged. Explicit positive execution context into actual SYNC worker and QUEUE fresh PAPER/OPENALEX extraction; no global latest task. No scope around consume/RAW/Apollo. Cache hits no sample/download. Preserve identityRuleVersion, HostBusy postponement, business exception and checkpoint propagation. Only observe already complete original PDF/HTML/XML response bytes; no request/stream/error-body rereads or transport/retry/timeout changes. XML successful status/contentType remain null.
Tests must export complete planned capture/cases.json and capture/apollo.json plus actual sample packages for synthetic execution9001/9002, PDF/HTML/XML/403; request/output equality, cache/pause/source separation, and injected sample IO failure combined with Apollo reserve/activate failure. No fabricated result fields. Where no runtime entry is directly launchable, use a disposable driver to exercise actual changed path and report limits, removing scaffolds afterward.
Child03 consumes canonical v1 store packages with stable per-attempt bytes/settings and null-safe status fields; preserve all schema and source outcome semantics.

### Git and output
Commit new sampling changes only in `feat(fast-p): implement 02`; do not include evidence or imported files unchanged by sampling in that commit. Imported upstream commits are separate prerequisites, not fix rounds. Append full execute-p report plus imports/baseline/final commands/smoke evidence to execution.md; never rewrite epoch1. Do not modify ledger, verify-log, fix-log or other plans. Controller owns evidence commits and independent light verifier.
Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, final implementation SHA, command summary and report path. No main edits, push, merge, rebase, amend/reset/squash, deployment, unrelated fixes or later child work.
