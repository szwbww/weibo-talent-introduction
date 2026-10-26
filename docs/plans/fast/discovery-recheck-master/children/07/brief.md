# Child07 execution brief

## Identity / authority
- Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master; branch fast/discovery-recheck-master.
- Exact plan docs/plans/2026-10-24/discovery-recheck-07-pipeline.md; identity sha256:f07ebf6c63e7e3c1e084a79fcbde47ce51a5ed0f69343b569556562a3d332ad4.
- Product base feb58f65512484c742f7bf5f0edb6ec0e2207df8. Prior06 terminal LIGHT_PASS_WITH_NOTES and both full ten-child checkpoint modes VALID; clean governance HEAD1bdd2d7ab5f034ce529ec9105fae1f350ca2594d.
- Read complete exact child plan, linked master and evidence; execute-p immutable identity/worktree gates required. Master commitbc1cf7fedf59dc3399c2bfbd5be7db0a403b6af7/A7 records user “后续修改都授权 把所有子计划都跑完”. Necessary in-goal amendments are preauthorized but controller must evidence/commit the exact amended plan/list before extra-file edits. Report concrete dependencies as PLAN_CONFLICT to controller for automatic resolution, not repeated user confirmation. No unrelated behavior, production publishing/paid operations or Git history rewrite.

## Authorized Files (ten)
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepository.kt (new)
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepositoryIT.kt (new)

## Mandatory plan / invariants / acceptance
- Existing single Worker.process→Rules→Gate→Commit authority serves ordinary discovery AND recheck. No second qualification engine/workflow; AUDIT only recommendation/trace with zero business admission/three-layer ES writes and unchanged business Gate. ENFORCE uses original prepare/commit/project and full manual/contact/APPLICATION/SBIR/CAS hard blockers; do not remove original protection tests.
- Exactly real steps:1 roster/protection;2 paper source;3 identity;4 academic qualification evidence;5 direct admission;6 Apollo;7 combined result;8 record/sync. Early exits explicitly SKIPPED with reasons. Earliest genuine blocker primary; missing paper with reliable identity is INSUFFICIENT but continue, no identity skips personal external queries/Apollo. Direct sufficient => step6 SKIPPED/DIRECT_SUFFICIENT. Errors retain raw HTTP/code, never pretend ineligible.
- TraceRepository uses existing05 table. Init eight NOT_STARTED idempotently only on activated new items; never invent old completed durations. startStep gets next attempt, finish only RUNNING→terminal, lease/generation guarded; terminal attempts immutable/retry append. Inputs/outputs each<=64KiB with truthful truncated/count and full evidence/attempt references, only short excerpts/refs; no full paper/API keys/HTTP headers. Old no rows returns recorded=false, reads do not create rows.
- Freeze inputHash/snapshot/bodySHA/evidence versions/evaluatedAt. New payload adds ONLY executionContext protocol member with evaluationAt/paperSourceRefs; old missing context retains prior semantics. Every rule3 Worker and Gate evaluation uses same frozen instant; cross-birthday repeatability. REUSE raw ORCID response reprojects facts without re-signing old evidence/new hash; missingraw retains available derived facts explicitly not-reparsed. Identity/fact changes => STALE_INPUT, new round required; do not patch baseline hashes.
- New recheck REUSE reads frozen reviewInput cache/derived evidence/fullsearch, zero HTTP/all providers and zero new HTTP counts; CACHE_REUSED preserves original fetchedAt. No copied old progress/query cursor into new round. REFRESH uses run-frozen Apollo search/detail configuration and existing SQL permits/budget/credits/429/retry cursors; free detail calls0,429 wait not failure. Ordinary in-flight old APOLLO_SEARCH query order/cache compatibility unchanged.
- Ordinary new discoveries share06 paper bridge but academic enrichment remains existing separate task; ordinary path uses existing research data without extra academic external calls. Recheck REFRESH only explicit academic refresh. Reliable identity can proceed without paper.
- Before every external permit and before admission commit recheck run controls/CAS. Cancellation late response may append only one dispatched-request diagnostic, guarded by attempt.itemId/requestKey/ownership and incomplete status; no new permission/lease/item state/admission changes, no fake HTTP status without response (UNKNOWN_AFTER_CANCEL).
- Recheck epoch>0 commit compares frozen reviewInput.expectedAdmissionRevision against current admission revision (null truly absent), not completion-time freshly prepared value. STALE_INPUT/ADMISSION_CHANGED audit only. Only admissible suggestions may write admission; unsuccessful/error suggestions never silently revoke prior approval. Step7 retains before/proposed;step8 independently after/persistedRevision/projection status. Cancel-vs-commit lock order run→item; whichever wins authoritative. Committed admission retained; projection failure separate and retry projection only, providercalls0, step8 retry append.
- No new production start entrypoint or later08/09/10 implementation; preserve all ordinary paths/old payloads, mail/sender behavior, archive first/SHA/protection, independent versions and no backfill.

## Required commands / runtime proof
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT -DmysqlIt=true
- Tests/build/lint only after implementation complete; final commands after last product/test edit. Real MySQL trace/concurrency IT mandatory, no mocked SQL/skip substitute; boundJS/syntax must run. Read-only regression fixture files in second command are not authorized edits without controller amendment.
- Independent disposable standalone runtime outside JUnit through actual Registration/Worker/Rules/Gate/Commit/Trace and real MySQL with fixture source/provider/ES boundaries. Observe AUDIT zero businesswrites, shared paper pipeline and eight states, fixed-time outcome parity, REUSE HTTP0, free detail0, cancellation-vs-commit/late diagnostic and committed projection-only retry. Do not prefill desired decisions or mock internal changed logic. No production queries/writes/provider costs. Remove temporary scaffold and report exact commands/exits/counts/skips and limits. Tests alone not runtime proof.

## Prior outputs / downstream interfaces
- 06 PaperBodyAnalyzer pure shared compute/parse/assemble existing result schema1/analysis2; AnalysisService lease/cache/explicit commit same API. PaperSourceRef freezes record/attempt/bodySHA/source execution/source/key; bridge.collect frozen VerificationSnapshot/refs/mode/evaluatedAt gives readonly evidence/coverage/diagnostics/analysis/binding refs. Same-SHA invocation dedup. Academic collect returns trusted identity metrics/research/provenance/insufficiency only. See committed06 source/execution for exact signatures, use existing API not another layer. Independent06 fresh30 JVM/1827JS, zero skips, actual SQL/archive/loopback provider runtime passed; no earlier Worker integration claimed.
- 05 V161 run.recheck_json defaultnull/item.review_epoch0 and independent step table. Business reads ordinary all plus committed ENFORCE rechecks only, independent full history; cancelled separate. READY items RESERVED/closed frozenhash/zero steps; transactional READY→RUNNING then activation initializes eight slots idempotently. Fixed F-05-01/02 must stay closed.
- 04 Registration freezes3 for new runs, Worker supports stored0..3/defaultmissing1. Rules evaluatedAt optional; qualificationRULE_VERSION3, layout20261024, identity20260925/extraction20261004 unchanged. No global proof/cache invalidation.
- 08 consumes TraceRepository read DTOs and binds actual start/cancel orchestration; Query/API wiring deferred there. Trace recorded=false for true old history, not fake eight successes. 08 requires RESERVED inactive preparation and step initialization only actual activation.

## Commit / evidence
- Local commit authority feat(fast-p): implement 07, authorized product/test paths only; exclude fast-p logs/plans/ledger. Controller commits evidence separately.
- Append full execute-p result to docs/plans/fast/discovery-recheck-master/children/07/execution.md including exact identities/base/head/commands/side-effect evidence/runtime limits. No full review-p/verify-p/repair-p/fix-v inside loop; controller fresh distinct light verifier afterward.
- No extra-file/plan/ledger/other-worktree edits, unrelated cleanup, later-child review, push/merge/rebase/amend/reset.

## Epoch 2 — A7-authorized necessary dispatch guard amendment

- Exact child/master amended plan identity commit:7bcefb3815f3f3d318c8e619477cab0d67ad6c91, ledger A8/A9. Supersedes prior ten-file scope/old child identity only; all original requirements remain. Product base unchangedfeb58f65512484c742f7bf5f0edb6ec0e2207df8. Original epoch1 scoped drafts preserved; fresh execute-p identity/worktree gates against revised bytes, fix_round0.
- Minimal scope evidence: existing academic collect→OpenAlex author→enrichment→works/patent dispatches each through getJson→policy.reserve→exchange; Worker entry check cannot stop later internal HTTP after cancellation. Guard before permit and after any wait before wire, propagate cancellation ahead of generic catches. Existing ordinary unguarded collection remains legitimate/no behavior change; no new client or engine, no altered paid/free/budget policy.
- Authorized Files now original ten PLUS:
  - src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceService.kt
  - src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt
  - src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceServiceTest.kt
  - src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt
- Additional mandatory final command:
  `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckAcademicEvidenceServiceTest,OpenAlexDataSourceTest -DmysqlIt=true`
- Preserve both original commands and actual runtime proof. Strict local provider transport must prove cancel after author response prevents works/patent dispatch, and guard rechecked after policy wait before wire; preserve ordinary client RequestKind/budget regressions. Tests must observe consumer HTTP behavior, not callback forwarding/mock echoes.
- Append exact scope/search/reference receipts to execution.md, retain epoch1 discovery history and append new immutable epoch2 execution. Product commit remains feat(fast-p): implement 07, only14 approved paths; no evidence/plan/ledger staging.
