# Child 04 execution brief

- Exact approved plan: docs/plans/2026-11-01/paper-reprocess-04-apply.md
- Plan identity: sha256:77f145af6d11dec017b7f52596af0713b3ec40e68ce4370be60f05c68d9e6162
- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Approval: user invocation `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` on 2026-11-01.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Target branch: fast/paper-reprocess-master
- Depends on: 03
- Amendments: N/A

Read the complete exact child plan from this worktree. Links written as absolute paths into the original main worktree are planning references; resolve repository-relative sources here, never edit the original worktree.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessApplyServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperBackfillServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`

No other product/test/plan files may change. An unlisted necessary file or behavior requires PLAN_CONFLICT, not an improvised fix. Original PDFs stay outside Git in a safe local directory. No release, production write, original-file deletion, Apollo requests, push, merge, history rewriting, or modifications of unrelated user changes.

## Required Commands and Runtime Evidence
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessApplyServiceTest,DiscoveryPaperBackfillServiceTest,DiscoveryRecheckServiceTest,DiscoveryVerificationIntegrationTest
- Exercise isolated end-to-end consumer/recheck/commit/projection behavior and legacy CLI compatibility.

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
Use execute-p against the exact child plan. Commit only authorized implementation files as `feat(fast-p): implement 04`; exclude all fast-p artifacts. Append execution result to `docs/plans/fast/paper-reprocess-master/children/04/execution.md`; never overwrite historical entries. A separate fresh verifier receives only the brief and committed boundary. Do not self-declare PASS.

## Completed Prerequisites and Standing Authority
- Child03 terminal code/Base `ff7e67506545a31bc38a3d3f674056c522c57917`; LIGHT_PASS_WITH_NOTES after1repair. Evidence `6b5b919b0e103c6b307c9937b0a2c98e4cb2432e`; full-manifest dirty+clean checkpoint VALID, clean HEAD `9be7ca2c2940abfac761d321f021844c9706e88d`. Read sibling03 execution/fix/verify logs and actual committed interfaces before editing.
- Child03 uses V173; AUDIT-only API persists fixed whole-source PAPER/TARGET evidence, schema1, six steps, exact attempt leases, durable idempotence/lease/cancel/retry, dedicated executor. Only04 enables business writes/ENFORCE; do not duplicate lifecycle authority or relax evidence guards.
- Included child02 code `47393695c8157c7033147ae4b041e5c5b3c52aa9`: original recheck two-phase identity supplement, frozen beforeSnapshot and controlled server-derived admission seam; stale-input outcome+terminal trace atomic under actual Spring transaction proxies. Do not duplicate repair/admission rules or weaken transaction/lease fences.
- O-01 from03 generic task structured summaryRule=null is RECORD_ONLY. Owned detail exposes authoritative paper/target counts; do not expand generic extractor production scope.
- User authorized subsequent necessary in-objective amendments: “继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行”. Report new scope conflicts with exact evidence to controller before unlisted edits; do not edit plans.
- Required integration must actually execute: `-DmysqlIt=true`, plus discovered opt-ins for reprocess fixtures. Cover separate original-source/realSQL runtime after final changes, not tests alone. Real provider/production calls are not authorized; deterministic provider fixtures must exercise existing REFRESH/config consumption without paid calls.
- Known worktree identity helper line30 fails resolving unrelated stale publish path `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b`. Invoke helper as required then use strict native exact root/branch/gitdir/common-dir/registered target/HEAD/ancestry assertions at staging/commit/final checkpoints. Do not prune/change registrations/helpers/config; actual target mismatch stops.
- Expected gitdir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`; common-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Do not run review-fast-p, review-p, verify-p or repair-p; fresh fast-p light verifier owns child verdict and final aggregate review belongs to the user's other agent.
