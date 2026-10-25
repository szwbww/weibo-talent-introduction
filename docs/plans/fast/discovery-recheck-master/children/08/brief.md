# Child08 execution brief

## Identity / authority
- Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master; branch fast/discovery-recheck-master.
- Exact approved plan docs/plans/2026-10-24/discovery-recheck-08-api.md; identity sha256:47196d02af0f41bd852216230d91ec446020baa6e19b6e5e9a1b0f8c7e1d440a.
- Product base f37ff178554cb19b3264f81ed4deac7a6b43bd8c. Prior07 terminal LIGHT_PASS after one repair; both full ten-child checkpoint modes VALID. Clean governance HEADfca3b4ecb886ebda7b564d9b0a2ef08a60eaf49a.
- Read full exact child/master/evidence. Use execute-p immutable gates. Master commit7bcefb3815f3f3d318c8e619477cab0d67ad6c91, A7 continuing user authorization “后续修改都授权 把所有子计划都跑完”. Necessary in-goal changes/dependencies/list-cap exceptions preauthorized, but controller must first evidence/commit exact amended plan/list and ledger row before extra-file edits. Report concrete dependency to controller for automatic resolution, not another user confirmation. No unrelated behavior/production publishing/paid operation/history rewrite.

## Authorized Files (nine)
- src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckController.kt (new)
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt (new)
- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt
- src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt (new)
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt (new)
- src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt (new)

## Required behavior / all acceptance
- Real /api/discovery/rechecks prepare/start/cancel/sync-retry and read APIs from exact plan. Every endpoint requires existing session authentication, actor only session. Unknown enums/invalid params400, unauth401, missing/cross-run item404, spec/hash/revision/busy/config conflict409 with truthful reason. No body-controlled actor, modes must respect user selection validated server-side.
- Prepare supports IDS(real source lookup <=100 requested IDs, no trust browser snapshots) or ALL_MATCHING(existing ScanService filter+scroll on RAW/discovered). Keep q/issue/decision semantics. ALL_MATCHING no100-person truncation, dedup docIds, freeze batches500, actual1001→3batches1001 even when UI page20. Later insertedRAW never enters old roster. Failed partial freeze => FAILED/notREADY, no partial start.
- Dedicated single-thread prepare executor/task_type DISCOVERY_RECHECK_PREPARE. Real task_execution/run binding and fail reason; prepare taskSUCCESS is not verificationCOMPLETED. task orphan on run creation failure markedFAILED. Ordinary discovery executors/old manual prepare/retry behavior unchanged; do not reuse side-effectful old prepare.
- Default AUDIT+REUSE. Prepare performs zero business admission/three-layerES writes and zero providers, no initializeAdmission/itemactivation/step creation. New item epoch=real run owner id and RESERVED. Existing manual/AUTO approval remains unchanged. Only audit/run/item/task writes for preparation.
- snapshot_json retains existing profile/identityHash/factFingerprint; add reviewInput only for expectedAdmissionRevision(null genuinely absent, not0), beforeDecision, fixed source record/attempt/SHA and baseline cached evidence selection/hash. Fixed canonical rosterHash by ordered docId/identity/fact/revision/evidencehash. Freeze spec/specHash and existing ApolloSettings/rule3 settings. Freeze all inputs successfully before closeInput/READY; do not smuggle control into profile or change ORCID model.
- Idempotent UUID requestKey+same canonical spec returns same run, different spec409. Database-safe short registration using same-connection GET_LOCK("discovery_recheck_prepare",1), exact parameterized JSON_EXTRACT lookup notLIKE; release only aftercommit on sameconnection/finally. Follow existing Apollo transaction-sync pattern, not process-only mutex. No SQL lock during ES scan/provider.
- Start requires rosterHash+revision; verify enabled/config first, invalid =>409 with READY retained. Transaction CAS READY→RUNNING and activate exact frozen items; repeatstart idempotent returnscurrentrun, stalehash/revision409. Reuse original Worker; no second worker or newly looked-up completion-time snapshots.
- PREPARING/READY cancel directly; RUNNING→CANCELLING prevents new external/apply then activeitems settleCANCELLED. Committed admission stays, projection retry allowed separately. Running recovery uses original rosterHash/leases/requestCount; interruptedprepare recognized taskINTERRUPTED/notinputclosed =>INTERRUPTED/notstartable. Completion based on itemterminals and independentprojection, not preparetask status. Cancel/commit real run→item lock race mandatory.
- sync-retry only fixed-run owned admitted item IDs with projectionERROR/PENDING; successfulretry idempotent/no provider/requalification calls. Failed recheck reruns require newprepare/key, not overwrittenoldhistory.
- Reads exact endpoints: GET /runs?page&size&docId?, /latest?docIds (<=100 real IDs), /{id}, /{id}/items?page&size&state, /{id}/items/{itemId}/steps, /{id}/manifest paginated. Page sizes20/50/100. Explicit item ownership404. Read actual before/proposed/current/applied,8steps/blockingStep/allReasons/versions/sourcerefs; counts mutuallyexclusive processed/queued/cancelled/errors; AUDITwouldAdmit vsactualAdmitted and candidatesync separate. GET no task creation/provider/recalculation, no whole personal roster/paper in summary JSON.
- Preserve original manual review buttons/endpoints, protectedmanual/contact/APPLICATION/SBIR state, prior old ordinary null/epoch0/legacy context semantics, archive protection/firstSHA and mail/outreach behavior. No09 UI/10 backfill work here.

## Required commands / runtime
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationRepositoryIT,DiscoveryRecheckFlowIT -DmysqlIt=true
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true
- Finalcommands after lastproduct/test edit; no build/lint/test mid-flight. ActualMySQL registration/concurrentprepare/start/cancel/lease/commit/recovery ITs mandatory, not skip/mockedSQL. BoundJS/syntax required. Second-command existing tests read/run only unless authorizedfile or controlleramendment.
- Actual disposable runtime outsideJUnit: serve real new controller/MVC HTTP with isolatedactualMySQL, fixtureES/providers and existingWorker/Gate/Commit/Trace. Exercise unauth401/allnewroutes, sessionactor/defaultAUDITREUSE,1001freeze/start/manifest vs laterRAW, idempotence/conflict, realtaskbinding, readonlybusiness/HTTP0 prepare+AUDIT, cancellation/interruptrecovery/projectiononlyretry and protectedmanual regression. Use actualHTTP/runtime, not onlyMockMvc or prefilleddesiredoutcomes. No production/providercosts; source/runtimefixtures never expose credentials. Remove temporaryscaffold and report exactcommands/exits/counts/skips/boundaries.

## Prior outputs / downstream09 contract
- 07 actual single Registration/Worker/Rules/Gate/Commit/Trace with executionContext.evaluationAt/paperSourceRefs, REUSEcache/originalfetchedAt and perdispatchguards. Legacy missingcontext remainsabsent/live; explicit newordinary/recheck times frozen. Independent37/77/98JVM eachJS1827 and SQL/pipeline/cancelbothorders/project-only runtime passed; no realES/production acceptance.
- TraceRepository provides orderedreadDTOs,recorded=false no write for oldhistory, boundedinput/output/truncation; lease/generation/terminalattemptCAS. QueryService now consumes this readDTO, no independenthistory fabrication.
- 05 READY roster RESERVED/inputclosed/fixedhash/zeroearlysteps; transactional RUNNING activation initializes8slots. BusinessRepository ignores newAUDIT/uncommittedrechecks; separatehistoryall. Preserve corrected F-05-01/02 and F-07-E2-01.
- 06 readonlybridge and academic services supply fixedsource refs/coverage/provenance, noanalysiswrite and REUSEzeroHTTP. Freeze exact existing refs/cachesduringprepare; unavailable evidence explicit, noimplicitnetwork.
- 09 consumes these realserver task/runIDs, prepareREADY→explicitstart hash/revision, fullpagedhistory/details8steps, distinctproposal/current/applied/sync counts. Do not fake frontend acceptance or implement UI now.

## Commit / evidence
- Local implementationcommit authority feat(fast-p): implement 08; onlynineAuthorizedFiles, exclude fast-p logs/plans/ledger. Controller commits evidence separately.
- Append full execute-p result to docs/plans/fast/discovery-recheck-master/children/08/execution.md; exactidentity/base/head/commands/sideeffectproof/limits. Controller freshdistinctlightverifier afterward; no fullreview/verify/repair/fix skill inside loop.
- No extra-file/plan/ledger/other-worktree edits, unrelatedcleanup/later-childimplementation, push/merge/rebase/amend/reset.
