# Review-Fast-P Ledger — master: docs/plans/2026-10-23/discovery-paper-master.md

- Status: REPAIR_PLAN_READY
- Review epoch: 1
- Master plan: docs/plans/2026-10-23/discovery-paper-master.md (sha256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3)
- Governing master identity: sha256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3; commit 3c5a68ad9917fdee22e0548b5afc57a6df04d580; Git blob matches; commit on retained lineage
- Invoked master identity: sha256 ae1f7d432643853aba3071975167441a2b9607ca04151021617a90f6ae2f215f
- Master identity state: AMENDMENT_RECORDED
- Governing amendment: A16; I-3; I-4; 顺序、隔离与出口; 执行前检查第5项; Synchronize07five→eightfilecount for exactthreecontracttests, no newproductbehavior; HUMAN:批准 继续 — approving preceding07 exactthree-test scopeamendment
- Amendments: A1–A16, exact identities/rules/reasons/human approvals in fast-p ledger Amendments table; governing copy accepted under recorded approvals, invoked copy stale
- Fast-p ledger: docs/plans/fast/discovery-paper-master/ledger.md (sha256 de7e31a72c3f931aacbd8fe285f34c398ab54ec344c308c72031b83ab6c2f87d)
- Fast-p handoff: docs/plans/fast/discovery-paper-master/human-review-handoff.md (sha256 ee4944f00f684918b75d23e7d569212ad93333d02e0f77c1a2e362250e5b7956)
- Master base: c6e9709f349f32c5feb1d067b47c697b0dff05b6
- Final code head: 37da0edbdb9737ed75c27ea8e6e525e35ef78354
- Evidence parent before next commit: ef84aabfc0e509a8178c626f22c430dcdffb78bb
- Previous evidence commit: N/A
- Branch: fast/discovery-paper-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
- Worktree resolution: DISCOVERED_FROM_GIT_WORKTREES
- Discovery evidence: discover_fast_p.py --repo /Users/lukai/IdeaProjects/weibo-talent-introduction --master-plan docs/plans/2026-10-23/discovery-paper-master.md; exit 0; SELECTED exactly one; 8 terminal children; recorded identity commit 3c5a68ad9917fdee22e0548b5afc57a6df04d580; invoked/worktree hashes above
- Misdirected review evidence: N/A
- Reviewer: /root/aggregate_master_reviewer; fresh fork_turns=none, created after final code commit; absent from all fast-p writer/fixer/verifier identities
- Reviewer attempt: 1
- Machine result: FAIL
- Machine report epoch: docs/plans/review/discovery-paper-master/machine-verification.md — Epoch 1; FAIL / INITIAL
- Repair artifact: docs/plans/fix/discovery-paper-master/repair.md (sha256 4c9cc7637968e0691ba61bc362acbd3324c97d1a8d50c0d1ecdb0ec2bd991584); DRAFT_READY; V-1; exactly two Authorized Files
- Repair evidence mode: N/A
- Repair approval source: N/A
- Repair executor: N/A
- Repair code head: N/A
- Manual status: PENDING
- Human sign-off boundary: N/A
- Blocker/next action: Human approve exact repair via $execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md; execute authorized repair/local commits/durable handoff, then resume this aggregate review from the exact retained fast-p handoff when requested

## Preflight
- Registered-worktree discovery SELECTED exactly one matching retained run.
- Full eight-child validate_fast_p.py FINAL: exit 0, VALID, errors=[], A1–A16, evidence head ef84aabfc0e509a8178c626f22c430dcdffb78bb.
- All eight current child plan bytes equal their exact recorded identity commits; every child evidence commit belongs to retained lineage.
- MASTER_BASE_SHA ancestor of final_code_head; final_code_head ancestor of evidence HEAD. Later commits change only fast-p evidence.
- Before review evidence creation: worktree/index clean; no prior review ledger in retained worktree or invoking worktree.
- Baseline environment freshly observed: JDK11 Zulu11.56+19 / 11.0.15; mvn and node installed; Docker 29.8.2 available through approved socket access. No product verification run by controller.
- Verification scope: one aggregate master matrix; complete combined diff; required child command union and final WAR/full JS checks freshly executed by independent reviewer; manual items remain human-only.

## Governing Master Diff (invoked → retained)
```diff
--- /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-10-23/discovery-paper-master.md	2026-10-23 21:44:25
+++ docs/plans/2026-10-23/discovery-paper-master.md	2026-10-24 11:39:29
@@ -28,7 +28,7 @@
 - 来源: K-extraction-samples-are-not-consumption；K-paper-affiliation-is-not-employment；K-admission-event-and-projection-counts。
 
 ### Invariant I-3: 样式与文件边界必须明确
-- Rule: 仅05/07改前端，逐字落实它们的S-n CSS/DOM；不允许执行代理临时补class或inline style。每子计划≤10文件，超出先修订计划；共享文件按01→02→03→04→05→06a→06b→07顺序，不并行覆盖。
+- Rule: 仅05/07改前端产品，逐字落实它们的S-n CSS/DOM；不允许执行代理临时补class或inline style。每子计划≤10文件，超出先修订计划；本次明确例外为06a原5文件加列明的28份JS资源契约同步，共33文件（不授权前端产品/其他行为变更），以及06b原10文件加DiscoveryPipelineService.kt，共11文件（仅新旧队列持久化来源兼容）。例外不泛化授权其他切片。共享文件按01→02→03→04→05→06a→06b→07顺序，不并行覆盖。
 - Applies to: 所有子计划执行和复验。
 - Violation consequence: 漂移成另一套UI、共享文件互相覆盖、复验无法收敛。
 - 来源: K-master-plan-shared-file-sequential-gates；K-frontend-cache-key-triad。
@@ -97,17 +97,17 @@
 | 顺序 | 子计划 | 文件上限 | 可独立验证/部署的结果 |
 |---|---|---:|---|
 | 01 | [存储与管理API](discovery-paper-01-storage.md) | 9 | 独立目录/持久化/鉴权/保护删除/容量API；默认关闭，不接入旧业务 |
-| 02 | [全下载接入](discovery-paper-02-capture.md) | 8 | 六论文来源真实正文留存，容量阻断不丢游标，可通过API使用 |
-| 03 | [版面解析修复](discovery-paper-03-parser.md) | 6 | 两条解析规则独立生效，固定原文回放报告 |
-| 04 | [解析对照与历史事实](discovery-paper-04-analysis.md) | 9 | 只读分析现有正文、分析JSON和查看API |
-| 05 | [论文原文前端](discovery-paper-05-frontend.md) | 6 | 真实页面管理/预览/磁盘/批量取消保护，完整CSS |
-| 06a | [Apollo配置持久化](discovery-paper-06a-apollo-persistence.md) | 5 | 两开关独立保存API，默认/冲突/失败语义 |
-| 06b | [Apollo运行接入](discovery-paper-06b-apollo-runtime.md) | 10 | 新旧run分离、分渠道日志、现有测试适配 |
-| 07 | [Apollo前端配置/日志](discovery-paper-07-apollo-frontend.md) | 5 | 保存状态、收费开关、运行快照与尝试数字，完整CSS |
+| 02 | [全下载接入](discovery-paper-02-capture.md) | 10 | 六论文来源真实正文留存，容量阻断不丢游标，可通过API使用；含已批准的存储接缝修订 |
+| 03 | [版面解析修复](discovery-paper-03-parser.md) | 7 | 两条解析规则独立生效，固定原文回放报告；同步新采集版本回归 |
+| 04 | [解析对照与历史事实](discovery-paper-04-analysis.md) | 10 | 只读分析现有正文、分析JSON和查看API；同步完整基线增益筛选回归 |
+| 05 | [论文原文前端](discovery-paper-05-frontend.md) | 7 | 真实页面管理/预览/磁盘/批量取消保护，完整CSS；同步必跑核验测试资源数 |
+| 06a | [Apollo配置持久化](discovery-paper-06a-apollo-persistence.md) | 33 | 原5文件两开关保存API；额外28测试仅同步资源契约，显式一次性上限例外 |
+| 06b | [Apollo运行接入](discovery-paper-06b-apollo-runtime.md) | 11 | 新旧run分离、分渠道日志、测试适配；新增PipelineService队列版本兼容，明确一次性例外 |
+| 07 | [Apollo前端配置/日志](discovery-paper-07-apollo-frontend.md) | 8 | 保存状态、收费开关、运行快照与尝试数字，完整CSS；同步三份既有直接契约测试 |
 
 每份先实施、机器复验通过，再进入下一份；不把机器复验当成人工验收。任务可以一次连续执行，但不能并行修改共享文件或越过失败关卡。本轮只创建计划，未启动执行。
 
-共享文件顺序：ArchiveService 01→04，Models 01→04，Controller 01→04；Capture 02→03，PdfEmailExtractor 02→04；index/app/styles 05→07。Flyway V159归档、V160 Apollo；执行前再核对最新migration，冲突必须修订计划清单。不得修改已应用迁移。
+共享文件顺序：ArchiveService/ArchiveRepository 01→02→04，Models 01→04，Controller 01→04；Capture 02→03，PdfEmailExtractor 02→04；index/app/styles 05→07。Flyway V159归档、V160 Apollo；执行前再核对最新migration，冲突必须修订计划清单。不得修改已应用迁移。
 
 ### 执行前检查（I-1/I-3）
 
@@ -126,7 +126,7 @@
 
 ## 变更文件清单
 
-本主计划是治理入口，不作为合并后的超大实施清单。具体生产代码权限严格以各子计划≤10文件表为准。本轮治理/子计划产物如下（8个子计划）；代码证据快照及实验算法附件在上文链接，不属于生产实现变更清单：
+本主计划是治理入口，不作为合并后的超大实施清单。具体生产代码权限严格以各子计划文件表为准；除I-3已批准的06a一次性28份测试同步例外和06b单增PipelineService的11文件兼容例外外，各子计划仍≤10文件。本轮治理/子计划产物如下（8个子计划）；代码证据快照及实验算法附件在上文链接，不属于生产实现变更清单：
 
 | 文件 | 用途 |
 |---|---|
@@ -170,3 +170,14 @@
 
 
 计划自审（2026-10-23）：8个子计划的章节顺序、变更清单、相对链接、I/S引用与新增class覆盖已检查；文件数依次9/8/6/9/6/5/10/5；两份CSS括号与DOM class覆盖检查通过。自审发现并修正了“升级缓存兼容常量会拒绝旧队列”“新增script触发16资源断言”“核验构造器/旧迁移测试遗漏”三处范围问题。以上是文档自审，不是产品测试或上线效果证明。
+
+## 修正记录
+
+- 2026-10-23：用户“批准 继续”批准02暂停报告C-1/C-2的最小修订：02授权由8增至10文件，加入ArchiveService/ArchiveRepository，明确已启用scope的保存完成与原子保留first/更新latest。依据I-3与执行前检查第5项，保持每子计划≤10文件且不改变准入或缓存规则；共享文件顺序同步为01→02→04。批准和提交身份记录在 `../fast/discovery-paper-master/ledger.md` 的 Amendments 表；原始自审计数为历史记录。
+- 2026-10-24：用户“批准 继续”批准03暂停报告C-1：03由6增至7文件，增加CaptureTest新采集版本断言同步，保留first冻结与缓存兼容约束。依据I-3及执行前检查第5项；不改变真实样本缺失时不得宣称效果已验证的边界。批准与提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
+- 2026-10-24：用户“批准 继续”批准04暂停报告C-1：04由9增至10文件，增加RepositoryIT不完整基线hasGain筛选预期同步，保留回滚/first/CAS断言。依据I-3及执行前检查第5项；T-3完整基线要求不变，不引入legacy旁路。批准与提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
+- 2026-10-24：用户“批准 继续”批准05暂停报告C-1：05由6增至7文件，加入discoveryVerification.test.js同步资源数量及描述16→17；同key/runtime约束不变。依据I-3及执行前检查第5项；批准与提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
+- 2026-10-24：用户“批准 继续”批准06a暂停报告的28份JS资源契约同步与5＋28＝33文件一次性例外。I-3及变更清单规则同步，其他切片仍≤10文件；只改资源数量/描述和真实有序模块列表，保留所有其他断言，Maven绑定全量Node仍须通过。不授权前端产品改动或后续泛化例外。实际grep失败清单/来源凭据见 `../fast/discovery-paper-master/children/06a/execution.md:139–196`；批准与身份见ledger Amendments。
+- 用户“批准 继续”批准06b暂停C-1：增加DiscoveryPipelineService.kt，明确10→11文件一次性例外；新队列criteriaVersion=2与旧version=1同时可读，新任务null采用保存配置，历史null仍false，既有run保持原快照。依据I-3/I-4及执行前检查第5项；不新增迁移/领域字段/测试文件，不改变准入规则，不豁免其余失败。批准与身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
+- 用户“继续 修复 直到可以人工复验”，后澄清“说错了 是可以执行 review-fast-p”，批准上次暂停中精确列明的七项FlowIT测试契约修订，保持现行生产规则/预算/查询与保守缓存恢复语义，仅既有FlowIT文件及seed输入调整；不删除/跳过测试，不增加文件，不恢复旧生产行为。明确契约在06b修正记录及 `../fast/discovery-paper-master/children/06b/execution.md:451–516`；依据I-3/I-4与执行前检查第5项，原回归全部成功要求不变。完成剩余子计划和证据门后交付READY_FOR_HUMAN_REVIEW，整体review-fast-p另行执行；身份见ledger Amendments。
+- 用户“批准 继续”批准07暂停C-1的明确三测试清单：discoveryContinuousRun.test.js、discoveryScheduleSetting.test.js、discoveryPapers.test.js，原5→8文件，不超I-3上限。同步启动参数省略断言及既有论文CSS标记块边界，保留其余断言及论文CSS字节不变；不扩产品行为、后端或其他文件。依据I-3/I-4及执行前检查第5项；批准及身份见ledger Amendments。
```

## Reviewer Dispatch
- Attempt 1: SUCCESS; reviewer /root/aggregate_master_reviewer; fork_turns=none; exact aggregate-reviewer input bundle only. Controller remains orchestration-only.

## Epoch 1 Completed Machine Review
- Reviewer: /root/aggregate_master_reviewer, fresh fork_turns=none, created after code commit, independent of every recorded writer/fixer/light verifier.
- Machine result: FAIL; convergence INITIAL; finding V-1 NEW/P1 — scoped blob reuse reserves full bytes before deduplication and unnecessarily blocks zero-growth saves near capacity.
- Reviewer output: complete review-p report persisted verbatim in machine-verification.md, Epoch 1; includes contract matrix, cumulative 78-file scope, each amended added file, A1–A16 authority, fresh commands, RECORD_ONLY mapping, unavailable evidence and repair-p DRAFT_READY.
- Fresh required command union: 27 JVM classes, 567 tests total, 0 failures/errors, 1 private-original skip (566 executed); Maven exit 0. Full Node 1823/1823; focused 65/65 and 118/118; syntax exits 0. Seven comparator controls and six standalone runtime processes passed. Real Chromium actual-assets fixtures exercised required states/lifecycle/viewports. WAR package exit 0, JVM execution explicitly skipped, bound Node/syntax passed.
- Fresh WAR SHA256: fed81740960e47072b2d725af184be956bfa2dd6368ffeddf0ebe444f8c7b117.
- V-1 diagnostic: actual SQL/temp files, existing1024 bytes, usable space min+1023, scoped reuse ARCHIVE_STORAGE_BLOCKED while direct reuse succeeds with archiveBytes1024; process exit0, no physical growth needed.
- Effect evidence: original294/page34 dual-version and original23/person-label effects remain BLOCKED/incomplete under the master's explicit code-completion exception; no real-effect PASS claimed.
- Repair scope: src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt and src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt only. No product repair executed.
- Manual status: PENDING, all26 master/child A-n; no manual-acceptance.md generated because machine did not PASS; no human results/sign-off inferred.
- Controller completeness check: boundary/identity match dispatch; command counts/exits, full matrix, stable V-1, per-file amendment rows, RECORD_ONLY and evidence boundaries present; eligible FAIL/INITIAL with DRAFT_READY; repair bytes match returned hash; resolved two-file one-approval execution handoff and both local commit subjects present.
- Product state before evidence commit: HEAD ef84aabfc0e509a8178c626f22c430dcdffb78bb; no tracked product/test modifications; empty index; only the three authorized review/repair artifacts untracked.
- Evidence commit includes only ledger.md, machine-verification.md and the exact repair.md; no product/test files, baseline plans or fast-p evidence changed.
- Machine report SHA256: 9ba33e0936d2eb753ec3d0cb88d85fe7b0d2508accee7a2727200123145016ab.
