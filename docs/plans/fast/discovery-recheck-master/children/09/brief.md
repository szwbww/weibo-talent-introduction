# Child09 — Immutable implementation brief

## Identity and authority
- Child: 09, epoch1, fix_round0. Depends on08 terminal LIGHT_PASS_WITH_NOTES; full ten-child checkpoint --through-child08 passed dirty and clean before this dispatch.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Master base: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab
- Child product base: 59c34a1b6f04af0669a9276dae7c043bca4164f5
- Pre-execution governance HEAD: e8181bb1491d0ef29729a4190798ad108f803c72
- Exact approved plan: docs/plans/2026-10-24/discovery-recheck-09-frontend.md
- Plan identity: sha256:b92cdfdcbe1ffe5da3e5eda43af3488c18e3c948c777fd196c6f70eeb502b4df
- Master: docs/plans/2026-10-24/discovery-recheck-master.md, latest amendment commit7bcefb3815f3f3d318c8e619477cab0d67ad6c91. Read current plan completely; its S-1 DOM and S-2 verbatim CSS are implementation contract, not illustrative design suggestions. Do not substitute preview data or redesigned classes.
- User continuing authority A7: 后续修改都授权 把所有子计划都跑完. Necessary in-goal scope/direct-regression changes can be authorized by controller after concrete evidence and exact plan-only amendment/ledger registration. You MUST NOT widen scope yourself. Send exact files, evidence, smallest change and requirement to controller before editing unauthorized files. No ordinary expansion needs another human prompt; no unrelated work or deployment authority implied.

## Required workflow and boundaries
Use skill://execute-p for this exact plan. Fresh sole product writer. No original user-worktree edits, other worktrees, push, merge, rebase, squash, amend, reset, history rewrite, production writes/live paid provider calls or later-child implementation. Preserve existing user changes. No full review-p/verify-p/repair-p/fix-v within fast-p loop: independent controller-dispatched light verifier follows. Inspect relevant existing patterns before edits; language-server available -> use it for code intelligence. Skip build/lint/test/formatting mid-flight and run required commands once implementation has settled; reported failures are ground truth, fix only in-plan without weakening assertions. Every required command must run freshly after last relevant edit. Actual browser proof mandatory beyond unit tests. Temporary servers/scripts/screenshots may be outside product tree; remove scaffolds/stop services after evidence, keep screenshot artifacts referenced in execution report, no private roster/credentials in screenshots or committed logs.

## Authorized Files — exactly8
1. src/main/resources/static/discovery-recheck.js (new)
2. src/main/resources/static/app.js
3. src/main/resources/static/index.html
4. src/main/resources/static/styles.css
5. src/test/js/discoveryRecheck.test.js (new)
6. src/test/js/discoveryReview.test.js
7. src/test/js/meetingConfirmationAssets.test.js
8. src/test/js/trustReplyWorkbenchSharedMount.test.js
No backend changes, no additional class/style, no global nav or global p/button/modal changes. If asset-key test dependencies require another test, report before edit. Discover current resource-key references by exact current index key across tests rather than assumptions; quantify with grep receipt. Retire incidental obsolete key/count pinning rather than weaken functional contracts. Exact18 resource target includes new script, all key20261024-discovery-recheck, script beforeapp.

## Explicit requirements/invariants
- Exact S-1 root/three native dialogs after old manual toolbar before existing seven-column table; old manual actions/endpoints and protection checkbox rules remain. New per-row 验证流程 uses existing button secondary small inside current operations cell, no extra column. New nodes/class set subset S-1/S-2+listed old button classes, no inline style. Copy whole S-2 CSS verbatim, all scope .dr-recheck, opaque dialog, body#475569/12px, wide235px step column and narrow2column steps, only tables local horizontal overflow. Preserve earlier01 async counts,02 paper reader and03/04 identity/qualification behavior.
- createDiscoveryRecheck({root,api,getSelectedDocIds,getFilter,onApplied,openPaper}) returns mount/unmount/onRows. app.js host bridges only existing lifecycle/realselecteddocIds/filter/count+list refresh/paper reader, no manualapprove clone.
- All counts/state/source records from actual08 APIs; no preview.js/fake eight people. Defaults AUDIT/REUSE. Prepare frozen roster paginatedmanifest; show realcount/hash/mode/frozenApollo search/detail. READY only enablesstart; any filter/mode/evidence option change invalidates old hash/newprepare. Close/Esc only closes+restoresfocus, not cancel; explicit cancel task calls cancel.
- Memory only; one2s polltimer, pause when documenthidden, resume GET, terminalstop. Abort+epoch protection on close, task/person/context switch, mount/unmount; no stale Promise can update newer DOM, all listeners/timer removed on unmount. No roster/token/qualification localStorage.
- Sixmetrics: fixedtotal,processed,AUDITexpected orENFORCEactualadmitted,pending,error,cancel/protected separately subcounts not summedasonecategory; queued/processing truthful, candidatepending/error separate message. Do not labelproposed ascommitted or syncfailure asadmissionfailure.
- Detail eight serverstate steps/reasons/mainblocker/source+SHA/page/version/time/attempts. Default blocker otherwiselastcompleted. Old history 未记录, no fake success/time. before/proposed/current/applied separate. projection-only retry only eligibleexistingreceipt,no newqualification/providerrequests. History runlist/runitems/docIdhistory sameclear20pagination. latest≤100 docIds inonebatch perpage noN+1. openPaper existingreader withrealvalidatedrefs, missingSHA/page explicitunknown.
- External names/reasons/quotes textContent; validate http(s)/same-originAPI URLs, no providerHTML. Errors code+usableaction, networkfailure neverfake0.

## Prior evidence / available runtime context
Read children/08/execution.md and verify-log.md for bounded API/HTTP evidence, not independent proof for09.08 code59c34a1b6f04af0669a9276dae7c043bca4164f5 implements prepare/start/cancel/sync-retry and runs/latest/run/items/steps/manifest with real session actor/taskID/frozenhash/revision. Fresh08 verifier39/56JVM and eachJS1827zero skips; independent forked HTTP414/MySQL runtime frozen1001/500+500+1, AUDITbusiness/provider0, protection/recovery/bothcancelorders/projectionretry. Standalone DiscoveryRecheckFlowIT main is committed inspected backend fixture if useful; forked Java launcher works, Maven exec:java in-process does not. DisposableMySQL Docker available; Testcontainers standalone use -Dapi.version=1.40 if needed. No real ES/liveprovider/deployment proof exists. Existing warnings record-only.

## Required commands and runtime proof
Run in this worktree after final edits:
1. node --check src/main/resources/static/discovery-recheck.js
2. node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js
3. node --test src/test/js/*.test.js
Also check changed app.js syntax, exact whole S-2 CSS equality, S-1/allowedclass subset/no-newstyle and actual real index DOM IDs (DOMstub cannot prove existence). Browser skill docs xd://eval/browser before use. Actual Chromium production index+module+CSS surface1280×720 and640×900 screenshots, inspect no透底/#47556912px/235px or narrow2columns/localtableoverflow. Exercise real host review toolbar→config(defaults/disabled-beforeREADY/fixedroster)→run/history/persondetail/eightsteps; real API integration where accessible with isolated loopback backend/strict fixture, explicitly label fixture versus actualbackend. Check focus/Tab/Esc notcancel, delayedperson1 cannot replaceperson2, close/reopen/context/unmount/timer. Must actually view screenshots, not merely emit imagefiles, then close tabs/servers. Preserve oldmanual/paperreader behavior. No tests-only or static-preview-only proof.

## Deliverables
Commit only authorized product/test changes locally as feat(fast-p): implement 09. Do not commit this brief or other fast-p artifacts with implementation. Append full execute-p result to docs/plans/fast/discovery-recheck-master/children/09/execution.md: exact immutableplan/worktree/base/head, everyfile, requirement evidence, commands/exit/counts, screenshot paths+observed runtime, real vs fixture limits, failures/deviations/cleanup, identity/commitreachability. Reports are separately committed by controller. Do not modify ledger, verify-log orfix-log. Return only READY_FOR_VERIFICATION|BLOCKED|PLAN_CONFLICT, actualcommitSHA, command/runtime summary, reportpath. No overallPASS, humanacceptance, deployment or finalaggregate-review claim. Later10 only after independent09 verification and fullmanifestcheckpoint.

## Epoch2 override — A10/A11 (approved before dependency edits)
- Exact child plan identity now commit:f64b7d3adafdf94743a22b524a4c38745071ebe1; master identity samecommit. Original epoch1 registration/history remains unchanged; use execute-p new immutable epoch2 with fix_round0, same productbase59c34a1b6f04af0669a9276dae7c043bca4164f5. Re-read full amended plan and register its exact new identity before dependency edits or staging. Sole writer remains Recheck09Implementer.
- A7 user standing authority applied to evidenced necessary direct dependencies. Authorized Files now exactly12: original8 plus src/main/resources/static/discovery-papers.js; src/test/js/discoveryPapers.test.js; src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt; src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt. The prior brief's no-backend-edit rule is superseded only for originalRepository read-onlycounts/DTO; no otherbackend/QueryService/schema/write changes. All S-1/S-2/oldmanual/oldreader/queued contracts retained.
- Existingpaperreader narrowopenPaper(ref,trigger) verifiesfixedrecord/attempt/SHA andoptionalrealpage, nosecondreader; preservedefaultreader/disposal/protectionbehavior. Read-onlycounts add pending/processing partition of existingqueued andprotected persisted-reason overlappingdiagnostic; notmutuallyexclusivetotalcategory andnotcomputedfromnewprovider/enrichment. Canonical amendmentrows A10/A11 inledger govern exactscope.
- Additionalfreshcommands afterfinaledits: node --check src/main/resources/static/discovery-papers.js; node --test src/test/js/discoveryPapers.test.js; env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationQueryServiceTest -DmysqlIt=true. Keep originalallcommands/browserproof; realMySQLno skips. Dependencygrep/readreceipts must be accurately appended toexecutionlog. No productcommitbeforecontrollerreleasesrecordedauthority.
