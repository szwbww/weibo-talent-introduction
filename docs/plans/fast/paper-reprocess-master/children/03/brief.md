# Child 03 execution brief

- Exact approved plan: docs/plans/2026-11-01/paper-reprocess-03-batch.md
- Plan identity: commit:a6e14b63448fecfa0cd1319074c8d0b5bb2026be
- Master plan: docs/plans/2026-11-01/paper-reprocess-master.md
- Approval: user invocation `/fast-p docs/plans/2026-11-01/paper-reprocess-master.md` on 2026-11-01.
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master
- Target branch: fast/paper-reprocess-master
- Depends on: 02
- Amendments: A3,A4,A5,A6,A7; historical conflicts retained, current epoch4 resuming partial implementation.

Read the complete exact child plan from this worktree. Links written as absolute paths into the original main worktree are planning references; resolve repository-relative sources here, never edit the original worktree.

## Authorized Files
- `src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperReprocessController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt`
- `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt`

No other product/test/plan files may change. An unlisted necessary file or behavior requires PLAN_CONFLICT, not an improvised fix. Original PDFs stay outside Git in a safe local directory. No release, production write, original-file deletion, Apollo requests, push, merge, history rewriting, or modifications of unrelated user changes.

## Required Commands and Runtime Evidence
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest -DmysqlIt=true`.
- Run real MySQL migration/lease/idempotency scenarios and actual API smoke with 121 records; follow test opt-in configuration.

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
Use execute-p against the exact child plan. Commit only authorized implementation files as `feat(fast-p): implement 03`; exclude all fast-p artifacts. Append execution result to `docs/plans/fast/paper-reprocess-master/children/03/execution.md`; never overwrite historical entries. A separate fresh verifier receives only the brief and committed boundary. Do not self-declare PASS.

## Controller Standing Authority and Identity Check
- User authorized continuation and necessary in-objective amendments on 2026-11-01: “继续 后续授权都继续 直到所有子计划都完成且可以运行review-fast-p的阶段 不要执行review-fast-p，我会交给其他agent执行”. Report newly evidenced scope conflicts to controller for a separately committed amendment; do not edit plans yourself.
- Known helper infrastructure defect: `execute-p/scripts/worktree_identity.py:30` resolves unrelated stale publish worktree `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b` and fails. Invoke helper as required, then assert native exact root, branch, gitdir, common-dir, target worktree registration, HEAD and expected commit ancestry before staging/commit and at final handoff. Do not repair/prune unrelated worktree registration or modify helpers/config. Any actual target mismatch remains a stop.
- Expected gitdir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`; common dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Do not run review-fast-p, review-p, verify-p or repair-p; no aggregate final reviewer. Fresh fast-p verifier owns each four-gate verdict; final review is reserved to the user's other agent.

## Completed Prerequisite Boundary
- Child02 terminal code head/Base for this child: `47393695c8157c7033147ae4b041e5c5b3c52aa9`; LIGHT_PASS after two bounded repairs. Evidence commit `03168d167a423fca98c1d593e9b3113e5e2a271e`; full-manifest dirty and clean checkpoints VALID, clean evidence HEAD `0d34800186d6d621f58f0e8312b3a98c8b9783a2`.
- Read sibling02 execution/fix/verify logs for exact new retained-identity interfaces; original beforeSnapshot/roster/history preserved, effective identity two-phase rebase, controlled server-derived admission receipt, STALE_INPUT convergence and transaction-proxied terminal trace established. Do not change that contract.
- Child01 parser code included in Base; PARSER_VERSION=20261101, identity/cache compatibility versions unchanged; frozen-corpus gains are binding opportunities, not admissions. Child00 immutable manifest at `docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json`, original retained sources `/Users/lukai/.local/share/paper-reprocess-30711`.
- Prior parser optional legacy corpus absent and pre-existing numbered References notes are RECORD_ONLY, not new repair tasks. No baseline failure exemption exists for this child's required tests.

## Epoch 2 — Authorized Migration Collision Resolution
- Plan-only commit `6a5f08774ae58b2dac3fb0743efe58837bf56922` replaces occupied V172 with first-free V173. All ten files, schema/behavior/commands/acceptance remain otherwise unchanged.
- Prior epoch1 stopped before product edits. Append epoch2 report; use current exact plan bytes/identity. Standing user authority and A3 bind the change; do not modify existing V172.

## Epoch 3 — Authorized Page Checkpoint Plumbing
- Exact master+child plan-only identity `41ebff5468b8b57a56785bbb8e0bda961bd96815`; A4/A5 authorize thirteen files including three optional parser-chain checkpoint seams. Preserve default callers, parser rules/versions and normal error behavior; narrowly propagate cancellation/budget signals.
- Prior epoch2 stopped before product edits. Its exact grep/signature/catch receipts are append-only execution history. No test or runtime acceptance inherited; implement and verify the complete current plan freshly.
- These three added seams are not permission for parser repairs, cache invalidation or independent unrelated changes. Cover actual page cancellation/budget and default behavior through the existing authorized tests.

## Epoch 4 — Directly Affected Catalog Tests
- Exact master/child plan identity `a6e14b63448fecfa0cd1319074c8d0b5bb2026be`; A6/A7 cap14 authorize directly affected TaskExecutionSummaryExtractorTest. Retire obsolete incidental fixed key/progress lists and count assertions, never re-pin to new copies/count23; preserve meaningful summary/progress behavior coverage.
- Resume existing authorized partial13-file work in this same worktree; do not reset it. Prior epoch3 final focused13test command passed, but no runtime/current acceptance is inherited. Complete newly authorized test changes, run amended three-suite command with real MySQL and actual121-record API runtime freshly.
- Base remains prior terminal code `47393695c8157c7033147ae4b041e5c5b3c52aa9`; there is no epoch3 implementation commit. Preserve all historical execution entries and controller artifact edits.

## Epoch 4 — Resolved Partial Runtime Inputs
- Actual new integration opt-in is `-DpaperReprocessIt=true`; final amended three-suite command MUST include that in addition to `-DmysqlIt=true` to prevent skipped SQL evidence.
- Prior writer identifies opt-in `DiscoveryPaperReprocessRuntimeScenarios` and standalone `Paper03RuntimeSmoke` entries within the authorized test files. Inspect their actual interfaces before invocation; run both freshly, including actual HTTP 121-record/seven-page smoke. Classpath dependency receipt `/tmp/paper03-epoch3-classpath.txt` is old execution context only; resolve freshly as needed.
- Epoch3 current files include canonical JSON immutable-input equality correction, preserving genuine input conflicts, and fixture-only packet/deletion expectation corrections; independent verifier has not accepted any of this yet.
