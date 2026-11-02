# Child 05 execution brief

- Exact approved plan: docs/plans/2026-11-01/paper-reprocess-05-frontend.md
- Plan identity: commit:d678780155fc4fac5ecc4896135806729efaee2f
- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Approval: user invocation `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` on 2026-11-01.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Target branch: fast/paper-reprocess-master
- Depends on: 04
- Amendments: A10,A11; current epoch2, pre-edit epoch1 conflict retained.

Read the complete exact child plan from this worktree. Links written as absolute paths into the original main worktree are planning references; resolve repository-relative sources here, never edit the original worktree.

## Authorized Files
- `src/main/resources/static/discovery-paper-reprocess.js`
- `src/main/resources/static/discovery-papers.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/test/js/discoveryPaperReprocess.test.js`
- `src/test/js/discoveryPapers.test.js`
- `src/main/resources/static/app.js`
- `src/main/resources/static/discovery-recheck.js`
- `src/test/js/discoveryRecheck.test.js`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt`
- `src/test/js/discoveryReview.test.js`
- `src/test/js/mailboxChatBehavior.test.js`
- `src/test/js/mailboxGroupPush.test.js`
- `src/test/js/meetingConfirmationAssets.test.js`
- `src/test/js/trustReplyWorkbenchSharedMount.test.js`

No other product/test/plan files may change. An unlisted necessary file or behavior requires PLAN_CONFLICT, not an improvised fix. Original PDFs stay outside Git in a safe local directory. No release, production write, original-file deletion, Apollo requests, push, merge, history rewriting, or modifications of unrelated user changes.

## Required Commands and Runtime Evidence
- node --test src/test/js/discoveryPaperReprocess.test.js src/test/js/discoveryPapers.test.js src/test/js/discoveryRecheck.test.js
- node --check src/main/resources/static/discovery-paper-reprocess.js
- Exercise the real UI against isolated backend data at 1440px/390px with 100%/125% zoom; capture browser evidence and compare CSS contract exactly.
- Amended final commands also require full `node --test src/test/js/*.test.js` and JDK11 `mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest -DpaperReprocessIt=true -DmysqlIt=true`; SQL must actually run, not skip. No substitute for real browser proof.

Use JDK11 explicitly: ambient Maven defaults to Java25. Skip build/lint/test/formatter runs mid-flight; run the required commands after the final changes as one verification phase. Missing evidence must be reported, never turned into PASS. Use actual runtime smoke in addition to tests.

## Global Invariants

### Invariant I-1: 不猜根因和数量
- Rule: 仅将原文证据+生产重放一致的漏绑作为修复真值；325不是新增/准入人数；未证明的推测不进入需求。
- Applies to: 00/01/06全部报告
- Violation consequence: 承诺无法兑现的修复效果。
- 来源: K-source-recall-needs-original-layout

### Invariant I-2: 来源与历史可追溯
- Rule: 原文SHA、metadata、first.outcome和旧审核轮次不覆写；新解析记录有实现版本、页码和原句；未来任务与本批同一机制。
- Applies to: 所有归档/重处理/重审路径
- Violation consequence: 无法复验，成为一次性脚本。
- 来源: K-extraction-samples-are-not-consumption

### Invariant I-3: 身份事实不等于准入
- Rule: 解析绑定成功不直接晋升；现有邮箱、身份、人物与资格门禁仍执行；论文历史职称不等于当前教授。
- Applies to: 新旧身份补齐/消费/资格
- Violation consequence: 把作者/老师署名误当合格专家。
- 来源: K-paper-facts-need-owner-time-and-readonly-compute

### Invariant I-4: 保护与模式隔离
- Rule: 人工/联系/APPLICATION/冲突保护不绕开；AUDIT不写业务；论文页AUDIT不外呼，专家原AUDIT按原REUSE/REFRESH选项执行；ENFORCE确认后执行；取消不回滚已提交业务。
- Applies to: 所有API、worker、commit
- Violation consequence: 误写或撤销用户处理结果。
- 来源: original

### Invariant I-5: 固定整批与可恢复
- Rule: 任务scope由sourceExecutionId的持久化快照决定，跨全部页；幂等、lease/generation、取消、恢复都由DB控制；关闭页面不取消。
- Applies to: 整批任务与前端
- Violation consequence: 漏处理、重复任务、假运行。
- 来源: K-task-activity-execution-identity

### Invariant I-6: 复用现有写路径
- Rule: 新作者唯一消费入口、已有档案窄CAS修复、准入现有Commit、投影现有project；旧CLI保持只prepare的含义。
- Applies to: 04所有业务写
- Violation consequence: 第二套规则漂移。
- 来源: K-discovery-reentry-keeps-run-accounting

### Invariant I-7: 日志口径明确
- Rule: 论文、attempt、唯一邮箱目标、docId、personId、准入、同步分别计数；六步处理日志包含失败/跳过/未开始，不伪造成功。
- Applies to: 所有API/查询/前端
- Violation consequence: 结果不可解释。
- 来源: original

### Invariant I-8: 不扩Apollo策略
- Rule: 保留当前免费/付费保存配置；一个父任务冻结后各子核验一致；不新增内部500次限制或开启收费详情。
- Applies to: registration/recheck内部派发
- Violation consequence: 配置漂移或费用意外。
- 来源: original

### Invariant I-9: 前端实现受完整契约约束
- Rule: 05中S-1..S-6是全部新增CSS/DOM的唯一权威；已有全局样式不改；每个新状态有文字及可访问操作。
- Applies to: 05全部前端文件
- Violation consequence: agent实现偏离真实页面。
- 来源: K-global-p-is-muted-in-dialogs


## Downstream Interfaces and Gate
- The exact plan defines the contracts; do not broaden them.
- Child 00 must produce its three declared production-replay reports and immutable input manifest for 01. Every available audited combination must have a status; SOURCE_MISSING is not a reproduced category. Child 01 must not invent parser repairs without 00 evidence.
- Child ordering is 00→01→02→03→04→05→06; only the controller advances after independent light verification and artifact checkpoint.
- Prior-child outputs: controller will append exact completed boundary and output pointers before dispatch

## Baseline
- Original repository revision: b758eb0f1d62f2beda39742085b441fcbbdff5ea.
- Seeded execution start: 3a425461dbe6088e4d4dda1a53141416a392670c; plan/audit/mockup copies only, no product change. All 104 hashes from paper-reprocess-evidence/baseline.json matched this worktree.
- User changes left intact in main; unrelated attachment timeout changes in application.yml are not copied.
- JDK11 11.0.15, Maven 3.9.11, Node v25.7.0 and Docker Engine 29.8.2 available (preflight exit 0).
- Product test baseline: NOT_RUN by orchestration controller. No known baseline test failure asserted.

## Execution and Evidence
Use execute-p against the exact child plan. Commit only authorized implementation files as `feat(fast-p): implement 05`; exclude all fast-p artifacts. Append execution result to `docs/plans/fast/paper-reprocess-master/children/05/execution.md`; never overwrite historical entries. A separate fresh verifier receives only the brief and committed boundary. Do not self-declare PASS.

## Completed Prerequisites and Continuation Authority
- Child04 terminal code/Base `736535ce9487782e52a73af6cb52517fa799b194`, LIGHT_PASS_WITH_NOTES after2repairs. Evidence `520eedbf0c20df573365a6962c996d4cb7f296c9`; full-manifest dirty+clean checkpoints VALID; clean metadata HEAD `da02b04ad7a661d83714b1df3c8acfa5cbf5bed2`.
- Read committed real API/domain/controller/service interfaces and sibling03/04 execution/fix/verify logs; schema1 PAPER/TARGET, six trace steps, separate business units, durable child links and frozen Apollo settings already implemented. Render current API, no imaginary DTO or frontend counters. Existing8-step detail remains the sole qualification view.
- O01 isolated ES/provider fixture boundary, O02 generic task summaryRule=null and O03 historical overlapping projection retry are RECORD_ONLY; no frontend authority to repair backend or generic task extractor. Missing necessary backend seam must be evidenced to controller.
- User standing approval: “继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行”; later repeated继续. Necessary objective-preserving amendments go through controller plan-only commit; no writer plan/unlisted edits.
- Frontend cache-key triad styles.css / trust-reply-workbench.js / app.js in index must bump together to equal key. Pre-edit discover all tests using current exact key via specialized grep over tests; report collectively any necessary unlisted contract tests. Retire wording/source/incidental fixed-value assertions rather than re-pin a new copied constant.
- Use actual browser tool and actual isolated backend/API for surface proof; scratch runtime harness allowed outside tracked product scope. Keep service alive for browser scenarios then stop it; disclose external provider/ES fixtures, never pretend static HTML or intercepted data is full backend proof. Backend failure/empty/long-message UI scenarios may use explicit scoped stubs as plan A3 allows, labelled distinctly from real backend cases.
- Known execute-p worktree identity helper fails at line30 on unrelated stale publish registration ending `multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b`. Invoke it as required then assert native exact root/branch/gitdir/common-dir/registration/HEAD/ancestry at stage/commit/handoff. Expected gitdir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`, common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. Do not prune registrations/change helpers; actual mismatch stops.
- Do not run review-fast-p, review-p, verify-p or repair-p; independent fast-p child verifier owns four-gate verdict, aggregate review is reserved to user's other agent.

## Epoch 2 — Authorized Readonly Query/Detail/Test Completion
- Exact current master/child commit `d678780155fc4fac5ecc4896135806729efaee2f`, nineteen files. Original S-1..S-6 CSS/DOM remain byte-for-byte contract, no business writer/qualification/lifecycle change authorized.
- Authority adds existing private8-step openDetail readonly export; server-persisted authoritative category totals/preview missingSourcePapers with explicit unit/overlap/unknown semantics; directly affected tests. Do not fetch all item pages to fabricate global totals or treat NOT_STORED-only archive count as all missing sources.
- Retire incidental fixed resource counts/full-list copies rather than re-pin20; preserve real resource/mount/old-feature behavior. Full exact pre-edit receipts in epoch1 execution log; no prior product/test/runtime acceptance to inherit.
