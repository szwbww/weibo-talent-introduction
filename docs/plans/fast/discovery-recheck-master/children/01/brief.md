# Child 01 execution brief

## Identity and authority
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Approved child plan: docs/plans/2026-10-24/discovery-recheck-01-counts.md
- Approved plan identity: sha256:00495a84d294e6a4af1389c2a42421dcafc107de06d3ff42d20dbd1d84979fc3
- Child product base: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab
- Master baseline: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab
- User invocation authorizes this exact child through master order 01→10 and a local implementation commit.
- Read complete exact child plan from target disk. It is authoritative for behavior and file scope.

## Authorized Files


仅下列 10 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewCountsService.kt` | 新增异步汇总与DTO |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsController.kt` | 新增受理/读取 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | scroll错误不吞 |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 新增计数专用executor |
| `src/main/resources/static/app.js` | 单任务计数轮询 |
| `src/main/resources/static/index.html` | 独立status、统一版本键 |
| `src/main/resources/static/styles.css` | S-1完整CSS |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewCountsServiceTest.kt` | 新增服务/失败/并发测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt` | 新增登录/受理/读取测试 |
| `src/test/js/discoveryReview.test.js` | 六路消除、迟到回包、失败不清零 |



## Global invariants (master I-M1 through I-M4)
- No second admission chain; AUDIT has zero admission/ES business writes; ENFORCE uses original CAS chain.
- Recheck epochs/history must not overwrite ordinary items, request counts, or existing admission authority.
- Binding, new expert, qualification evidence, proposed admission, committed admission, and candidate projection are distinct metrics.
- Modify only this child's listed files, at most 10 and 2 subsystems. Shared-file work is serial from this product base. Unexpected scope or behavior decisions require PLAN_CONFLICT; never amend plans yourself.
- Preserve manual/contact/SBIR/APPLICATION protections, SELF/Apollo accepted branches, saved search/detail settings, archive protection/delete/preview, all mail/contact/outreach behavior. This child only fixes counts, not large synchronous classified-list scans.

## Required commands and evidence
- JDK11: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryReviewCountsServiceTest,DiscoveryReviewCountsControllerTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest` (includes Maven-bound JS checks).
- `node --test src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`.
- `node --check src/main/resources/static/app.js`.
- Confirm exact S-1 CSS/DOM and consistent 17 resource keys per plan.
- Exercise actual changed async refresh/read path using a disposable runtime harness or actual local app; observe state transition, real execution id and final counts/failure preserving prior success. Tests alone do not satisfy runtime smoke requirement. Do not change live production data. Remove temporary harness after smoke.
- Run tests/build/lint only after implementation is complete, not mid-flight. Baseline captures pre-existing tests and original six-scan behavior.
- Record exact commands/exit codes/counts/skips, smoke output and any blocked evidence. Never label a skipped test as passing.
- Baseline: frontend three files 43 tests passed, 0 failures/skips. Maven baseline pending at brief creation; controller will append exact outcome before dispatch.
- User-reported 503 is established; do not re-query production merely to reconfirm it.

## Downstream interfaces
- POST `/api/discovery/review/counts/refresh` 202 `{requestKey,executionId?,state}` and GET `/api/discovery/review/counts?requestKey=...` persisted polling, no GET side effects.
- Single global in-flight scan with dedicated single-thread queue0 executor and typed status/result, preserving error categories and old-success timestamp.
- ScanService integrity checks must preserve scanAll consumers used by later ALL_MATCHING and must not globally rewrite listPage.
- Later children continue from these app.js/index.html/styles.css and executor changes; do not implement later child behavior now.

## Output and commit
- Use execute-p identity/worktree gates; no full review/verify-p/fix-v inside fast-p child loop. Independent light verifier is controller's next step.
- Implementation commit: `feat(fast-p): implement 01`, only authorized implementation/test files. No fast-p evidence in that commit.
- Append execution result (full execute-p schema) to `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/children/01/execution.md`; keep all child logs append-only.
- Do not write ledger/verify-log/fix-log, push, merge, rewrite history, or alter another worktree.

## Baseline completion before dispatch
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest`: exit 0; Kotlin 74 tests, 0 failures/errors/skips; Maven-bound JS 1823 passed, 0 failures/skips; BUILD SUCCESS (artifact://759).
- Direct three-file Node baseline: 43 passed, 0 failed/skipped.
