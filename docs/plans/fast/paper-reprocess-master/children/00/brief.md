# Child 00 execution brief

- Exact approved plan: docs/plans/2026-11-01/paper-reprocess-00-replay.md
- Plan identity: sha256:f9cb78a3c967c67fbbeab0c272a0894cb4928cd6a650e05ae419853dee6a2132
- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Approval: user invocation `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` on 2026-11-01.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Target branch: fast/paper-reprocess-master
- Depends on: none
- Amendments: N/A

Read the complete exact child plan from this worktree. Links written as absolute paths into the original main worktree are planning references; resolve repository-relative sources here, never edit the original worktree.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayTool.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperIdentityReplayToolTest.kt`
- `scripts/discovery_admission_probe/reconcile_paper_identity_replay.py`
- `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`
- `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-results.csv`
- `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-report.md`

No other product/test/plan files may change. An unlisted necessary file or behavior requires PLAN_CONFLICT, not an improvised fix. Original PDFs stay outside Git in a safe local directory. No release, production write, original-file deletion, Apollo requests, push, merge, history rewriting, or modifications of unrelated user changes.

## Required Commands and Runtime Evidence
- python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest
- Run the real offline replay CLI and reconciler on the original-byte manifest; measure via /usr/bin/time. Record exact resolved commands and real source availability, no invented replay.

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
- Prior-child outputs: none

## Baseline
- Original repository revision: b758eb0f1d62f2beda39742085b441fcbbdff5ea.
- Seeded execution start: 3a425461dbe6088e4d4dda1a53141416a392670c; plan/audit/mockup copies only, no product change. All 104 hashes from paper-reprocess-evidence/baseline.json matched this worktree.
- User changes left intact in main; unrelated attachment timeout changes in application.yml are not copied.
- JDK11 11.0.15, Maven 3.9.11, Node v25.7.0 and Docker Engine 29.8.2 available (preflight exit 0).
- Product test baseline: NOT_RUN by orchestration controller. No known baseline test failure asserted.

## Execution and Evidence
Use execute-p against the exact child plan. Commit only authorized implementation files as `feat(fast-p): implement 00`; exclude all fast-p artifacts. Append execution result to `docs/plans/fast/paper-reprocess-master/children/00/execution.md`; never overwrite historical entries. A separate fresh verifier receives only the brief and committed boundary. Do not self-declare PASS.

## Resolved Runtime Inputs — Child 00 Handoff
- Original-byte directory: `/Users/lukai/.local/share/paper-reprocess-30711` (outside Git); manifest: `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`.
- Retained baseline/candidate receipts: `/tmp/paper-reprocess-30711-baseline.json`, `/tmp/paper-reprocess-30711-candidate.json`; runtime inventory evidence: `/tmp/paper-reprocess-30711-runtime.json`.
- Actual replay command shape (use a fresh output path per run): `/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json <fresh-output.json>`.
- Actual reconciliation command shape: `/usr/bin/time -l python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay <fresh-baseline.json> --new-replay <fresh-candidate.json> --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv <fresh-output.csv> --report <fresh-output.md> --runtime-evidence /tmp/paper-reprocess-30711-runtime.json`.
- Receipts/reports reject overwriting. Verifier outputs must stay temporary; never overwrite committed report inputs.
- Baseline command evidence before product changes: runtime versions and validator help passed; no prior focused test result exists. Writer command results are execution evidence, not independent verification.
- Identity-helper infrastructure failure: `worktree_identity.py:30` resolves an unrelated stale registered publish path with strict=True. Exact target identity can be proved with native read-only Git assertions; no stale-path pruning or helper modification.
