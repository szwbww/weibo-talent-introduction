# Child 02 execution brief

- Exact approved plan: docs/plans/2026-11-01/paper-reprocess-02-identity.md
- Plan identity: commit:9ce385c729c4b3a4c6fd0fff3aa06c126773cec3
- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Approval: user invocation `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` on 2026-11-01.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Target branch: fast/paper-reprocess-master
- Depends on: 01
- Amendments: A1,A2; execution epoch 2 after explicit user approval.

Read the complete exact child plan from this worktree. Links written as absolute paths into the original main worktree are planning references; resolve repository-relative sources here, never edit the original worktree.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt`

No other product/test/plan files may change. An unlisted necessary file or behavior requires PLAN_CONFLICT, not an improvised fix. Original PDFs stay outside Git in a safe local directory. No release, production write, original-file deletion, Apollo requests, push, merge, history rewriting, or modifications of unrelated user changes.

## Required Commands and Runtime Evidence
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationIntegrationTest
- Exercise the original recheck path with isolated original-byte fixture; enable any integration-test opt-in flags found in the existing test.

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
Use execute-p against the exact child plan. Commit only authorized implementation files as `feat(fast-p): implement 02`; exclude all fast-p artifacts. Append execution result to `docs/plans/fast/paper-reprocess-master/children/02/execution.md`; never overwrite historical entries. A separate fresh verifier receives only the brief and committed boundary. Do not self-declare PASS.

## Completed Dependencies 00/01
- Product base for this child: `1979768f08ffe58ca3c919326bb0eebbc833e3a1` (child 01 terminal code head).
- Child 01 evidence commit: `55ee1ffd35d131c8126c38b68d31120e51c6045a`; checkpoint commit: `3065dae4f67eb075ede4395e5586ee0311731b46`.
- Child 01 terminal independent verdict: LIGHT_PASS_WITH_NOTES by PaperParserReverifier01R2 after two bounded repairs. F-01/F-02 resolved; optional older corpus and pre-existing numbered reference heading remain RECORD_ONLY, not repair authority.
- Complete 00..06 manifest checkpoint through 01 passed both dirty-artifact and clean modes, exit 0 / VALID / CHILD_CHECKPOINT.
- Frozen manifest: `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`; SHA-256 `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`; original-byte directory `/Users/lukai/.local/share/paper-reprocess-30711`.
- Prior reports: committed child 00 audit manifest/results/report; child 01 `children/01/verify-log.md` latest attempt 3 for independently observed consumer/version/gain evidence.
- Latest independent parser replay `/tmp/paper-parser01-verifier-e1-a3-replay.json`; complete keyed deltas `/tmp/paper-parser01-verifier-e1-a3-delta.json`; gain source/evidence `/tmp/paper-parser01-verifier-e1-a3-gain-evidence.json`.
- Prior outcome: 49 repaired combinations within frozen reproduced truth, 281 still unresolved, plus one excluded incidental gain. No business/new-expert/admission result is implied. Parser version 20261101; identity version 20260925 and cache extraction version 20261004 remain unchanged.
- Use an isolated source-backed existing-empty-name fixture as required by this child, not live production writes. Preserve original corpus and previous child evidence.
- execute-p worktree helper is known to fail at line 30 strictly resolving an unrelated stale registered publish worktree. Invoke as required and record equivalent exact native read-only root/branch/gitdir/common-dir/registration/HEAD/reachability assertions at all checkpoints. Do not prune worktrees or modify the helper; actual identity mismatch still stops.

## Epoch 2 — Approved Resume
- Exact approved child and master bytes are bound to plan-only commit `9ce385c729c4b3a4c6fd0fff3aa06c126773cec3`; read complete current plan again, not the previous epoch contract.
- User approval: “继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行”, recorded 2026-11-01T23:28:37+08:00. Ledger A1/A2 authorize eleven files and narrow DiscoveryReviewService persisted-admission seam. All prior invariants remain; no generic identity-guard bypass.
- Epoch 1 made no product edits. Implement every requirement, including real controlled old-admission identity change and rejection/rollback cases in the existing authorized tests; do not narrow to experts with no prior admission.
- Actual SQL integration command must include the existing opt-in `-DmysqlIt=true`; ordinary mvn test skips are not proof. Exercise real MySQL CAS/lease and an actual isolated original-recheck runtime path.
- Skip build/lint/test/formatters mid-flight, run after final implementation. Execute-p allows correction of your own uniquely determined in-scope implementation errors; do not misclassify ordinary self-check failures as an independent-verifier repair round.
- New strictly necessary scope conflicts should be evidenced and sent to controller for a separately committed, standing-user-approved amendment; do not alter plans or unrelated files yourself.
- Do not run review-fast-p, review-p, verify-p or repair-p; fresh fast-p verifier owns the light verdict, final aggregate review is deferred.
