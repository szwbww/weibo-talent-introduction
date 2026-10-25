# Review-Fast-P Ledger — master: docs/plans/2026-10-24/discovery-recheck-master.md

- Status: REPAIR_PLAN_READY
- Review epoch: 1
- Master plan: docs/plans/2026-10-24/discovery-recheck-master.md (commit da8940153f4ad22a5ac3a094f444a7ac4858f695)
- Governing master identity: sha256 5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99; recorded commit da8940153f4ad22a5ac3a094f444a7ac4858f695
- Invoked master identity: sha256 554c841cde657ee6bb8de6df467e77a1032b9a93d54b0adce283220433ea3792
- Master identity state: AMENDMENT_RECORDED
- Governing amendment: A17; I-M3 fixed-input gains / I-M4 child scope / child10 I-1/4; explicit bounded readonly replay factory, ordinary10s unchanged; HUMAN:后续修改都授权 把所有子计划都跑完 (2026-10-25), A7 standing authority
- Amendments: A1–A18, exact Before/After identities, master rules, reasons and approvals in fast-p ledger Amendments; governing diff recorded below
- Fast-p ledger: docs/plans/fast/discovery-recheck-master/ledger.md; sha256 610315d099ce9d10c7d90999436829def499bd4511b2bdc1ccc11d5ee7efc6c3
- Fast-p handoff: docs/plans/fast/discovery-recheck-master/human-review-handoff.md; sha256 7897304ff920ce75ca7b5660caa437e42b2130ae93f7a88a2c792aa9e119aea5
- Master base: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab
- Final code head: 0d60b43e586584319b78c4f3f9ca86b4820dd24f
- Evidence parent before next commit: ee65822ed51caf012e487d82cc04e9d74c0b125a
- Previous evidence commit: N/A
- Branch: fast/discovery-recheck-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Worktree resolution: DISCOVERED_FROM_GIT_WORKTREES
- Discovery evidence: discover_fast_p.py --repo /Users/lukai/IdeaProjects/weibo-talent-introduction --master-plan docs/plans/2026-10-24/discovery-recheck-master.md; exit0 SELECTED, exactly one candidate, child_count10; matching terminal ledger/handoff, identities and ancestry
- Misdirected review evidence: N/A
- Reviewer: /root/aggregate_reviewer (fresh fork_turns=none; created after final code commit; differs from all recorded fast-p writers/verifiers)
- Reviewer attempt: 1
- Machine result: FAIL
- Machine report epoch: machine-verification.md, Epoch 1
- Repair artifact: docs/plans/fix/discovery-recheck-master/repair.md; sha256 1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae; DRAFT_READY
- Repair evidence mode: N/A
- Repair approval source: N/A
- Repair executor: N/A
- Repair code head: N/A
- Manual status: PENDING
- Human sign-off boundary: N/A
- Blocker/next action: Human approval of the exact repair.md via execute-p; authorized product/evidence commits and same-task aggregate re-review using committed repair-execution.md, as specified in the repair handoff

## Preflight — 2026-10-25 Asia/Shanghai

SELECTED identity and lineage validated; ten exact child identities and all forty child artifacts exist. Product/index clean before evidence creation. Base, code, governing identity commit are retained branch ancestors. Code..evidence HEAD changes only fast-p evidence. No prior selected review exists. JDK11 resolved /Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home; Maven /opt/homebrew/bin/mvn, Node /opt/homebrew/bin/node, MySQL /opt/homebrew/opt/mysql@5.7/bin/mysql available. Required reviewer capability available. Mandatory commands: JDK11 mvn test (including bound JS); mvn package -DskipTests. Fresh reviewer must establish required runtime/IT evidence and classify every skip/failure.

## Exact Invoked-to-Governing Master Diff

```diff
diff --git a/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-10-24/discovery-recheck-master.md b/docs/plans/2026-10-24/discovery-recheck-master.md
index d516c2d..cb76990 100644
--- a/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-10-24/discovery-recheck-master.md
+++ b/docs/plans/2026-10-24/discovery-recheck-master.md
@@ -24,7 +24,7 @@
 绑定≠新专家≠资格证据≠可准入≠已准入≠候选同步。每份报告说明固定范围、版本、原文SHA、外部刷新、未完整部分。适用全部子计划；违反会虚报收益。来源：本批原文对照。
 
 ### I-M4：范围受子计划约束
-每子计划≤10文件、≤2独立子系统；每既有共享store最多1新列。共享文件必须串行按前一产物实施，每步独立机器复验。新发现范围或文件超限先修订计划，不能顺手扩大。来源：K-master-plan-shared-file-sequential-gates。
+每子计划默认≤10文件、≤2独立子系统；每既有共享store最多1新列。01经用户于2026-10-25授权，仅为同步修正`discoveryApolloSettings.test.js`的过期全局资源键断言，允许11文件。05经用户于2026-10-25授权，仅为六个直接/共享核验消费者测试同步V161 schema，允许15文件，保留原回归行为。用户随后明确“后续修改都授权 把所有子计划都跑完”：为完整实现既定目标、关键不变量与验收所必需的后续修改（包括直接依赖/回归文件、清单扩展及必要的文件数例外）已获持续授权；控制器须先记录具体证据、逐次单独提交相应计划修订并追加ledger的Amendments，再实施，不再为同类范围变更重复等待授权。此授权不扩大无关业务，不取消独立核验、三轮自动修复上限及不可达前提/安全边界的阻塞处理，也不授权生产发布、付费操作、推送或改写历史。共享文件必须串行按前一产物实施，每步独立机器复验。来源：K-master-plan-shared-file-sequential-gates及本次用户持续授权。
 
 ## 现状审计
 
@@ -49,16 +49,16 @@
 
 | 子计划 | 内容 | 文件数 | 子系统 | 发布门禁 |
 |---|---|---:|---|---|
-| [01](discovery-recheck-01-counts.md) | 一次异步统计、准确错误提示 | 10 | 审核计数+前端 | 可独立发布 |
+| [01](discovery-recheck-01-counts.md) | 一次异步统计、准确错误提示 | 11（授权测试例外） | 审核计数+前端 | 可独立发布 |
 | [02](discovery-recheck-02-archive.md) | 原文格式、姓名显示、DOCTYPE | 10 | 原文分析+前端 | 可独立发布 |
-| [03](discovery-recheck-03-identity-parser.md) | 明确邮箱姓名归属、伪邮箱 | 6 | 来源解析 | 可独立发布；不自动回填 |
+| [03](discovery-recheck-03-identity-parser.md) | 明确邮箱姓名归属、伪邮箱 | 9 | 来源解析 | 可独立发布；不自动回填 |
 | [04](discovery-recheck-04-qualification.md) | 简介事实、绑定姓名、复合职称 | 9 | 原文事实+核验规则 | 可独立发布；规则版本化 |
-| [05](discovery-recheck-05-history.md) | 独立轮次和轨迹schema/读隔离 | 9 | 核验持久化 | 兼容部署，无新启动入口 |
+| [05](discovery-recheck-05-history.md) | 独立轮次和轨迹schema/读隔离 | 15（授权测试例外） | 核验持久化 | 兼容部署，无新启动入口 |
 | [06](discovery-recheck-06-evidence.md) | 共享只读原文/学术采集 | 9 | 原文分析+核验证据 | 兼容部署，无新入口 |
-| [07](discovery-recheck-07-pipeline.md) | 统一Worker与八步 | 10 | 核验执行+审计 | 原发现回归通过；新入口仍未开放 |
+| [07](discovery-recheck-07-pipeline.md) | 统一Worker与八步 | 14（A7授权必需依赖） | 核验执行+审计 | 原发现回归通过；新入口仍未开放 |
 | [08](discovery-recheck-08-api.md) | 冻结/启动/取消/历史API | 9 | 重审协调/API | 与09作为完整用户功能发布单元 |
-| [09](discovery-recheck-09-frontend.md) | 真实审核页、完整CSS | 8 | 审核前端 | 依赖08并联合验收后发布 |
-| [10](discovery-recheck-10-historical-replay.md) | 历史同批验证和补录 | 7 | 原文补录+隔离回放 | 默认只读；生产应用单独按manifest明确执行 |
+| [09](discovery-recheck-09-frontend.md) | 真实审核页、完整CSS、原文桥接及只读统计 | 40（A7授权必需依赖/直接资源回归） | 审核前端+只读统计 | 依赖08并联合验收后发布 |
+| [10](discovery-recheck-10-historical-replay.md) | 历史同批验证和补录、冻结上下文及确定性只读解析 | 9 | 原文补录+隔离回放 | 默认只读；生产应用单独按manifest明确执行 |
 
 ### 八步与不短路的边界
 
@@ -103,7 +103,7 @@
 
 ## 变更文件清单
 
-总计划仅治理，不授权代码改动；每个链接子计划的≤10文件清单是实施唯一边界。总计划制品：本文件、10份子计划、discovery-recheck-evidence/README.md与四份rg回执、一份ES路径回执、一份SHA回执及计划自检结果；知识条目仅记录可复用发现。无需生成验收勾选导出文件。
+总计划仅治理，不授权代码改动；每个链接子计划的文件清单是实施唯一边界（通常≤10，01仅按I-M4授权测试例外为11）。总计划制品：本文件、10份子计划、discovery-recheck-evidence/README.md与四份rg回执、一份ES路径回执、一份SHA回执及计划自检结果；知识条目仅记录可复用发现。无需生成验收勾选导出文件。
 
 ## 验收标准
 
@@ -146,3 +146,15 @@
 - 覆盖：I-M4、所有前端样式合同、Apollo设置保持。
 
 人工验收开始后再导出master-acceptance.md。机器复验通过不等于用户验收完成。
+
+## 修正记录
+
+- 2026-10-25，用户“授权 继续”：批准01增加`src/test/js/discoveryApolloSettings.test.js`并允许11文件，保留独立控制器测试；原断言从旧计划动态读取全局资源键，字面量检索无法发现，导致01必需的资源键升级与全量JS回归冲突。仅修正过期断言，不修改历史计划、不扩大产品行为；授权与提交身份详见`../fast/discovery-recheck-master/ledger.md`的Amendments。
+- 2026-10-25，用户再次“授权 继续”：批准03增加`DiscoveryPaperArchiveCaptureTest.kt`、`PdfEmailExtractorTest.kt`、`ExpertDiscoveryServiceTest.kt`，清单6→9、未突破I-M4上限。只同步已证实的layout版本及明确唯一来源归属正例，不放宽共享/未知/相邻/错误所有者保护，不改变消费或准入行为；授权与提交身份详见同一ledger的Amendments。
+- 2026-10-25，用户“授权 继续”：批准05增加六个已由检索回执证明消费修改后Repository/Gate/Query的旧schema测试：`DiscoveryVerificationReportingIT.kt`、`DiscoveryCountrySupplementFlowIT.kt`、`DiscoveryVerificationFlowIT.kt`、`DiscoveryVerificationIntegrationTest.kt`、`DiscoveryVerificationReplayToolTest.kt`、`DiscoveryVerificationWorkerTest.kt`。清单9→15，仅05获得文件数上限例外；仅同步测试schema至V161，保留原断言及故意测试迁移前后的旧schema阶段，不加生产schema探测兼容分支、不增加生产行为。确切检索请求/输出见`../fast/discovery-recheck-master/children/05/execution.md`的Scope/precondition evidence addendum，授权与提交身份见同一ledger的Amendments。
+- 2026-10-25，用户在05独立核验期间明确“后续修改都授权 把所有子计划都跑完”：批准在原定目标/不变量/验收内继续完成全部子计划，并为必要的后续修改、直接依赖/回归清单扩展及文件数例外提供持续授权。每次仍须以具体证据界定、单独提交计划修订、追加Amendments并保留独立实现/核验和证据门禁；不因同类范围问题再次等待用户确认。未授权无关业务、生产发布、付费操作或Git历史改写；不可达前提与安全冲突仍如实阻塞，不能伪造证据。
+- 2026-10-25，依用户持续授权“后续修改都授权 把所有子计划都跑完”（A7），07增加`DiscoveryRecheckAcademicEvidenceService.kt`、`OpenAlexDataSource.kt`及各自现有测试，清单10→14并记录必要文件数例外。07 I-5要求每次外调前检查取消；现有OpenAlex批次内部会继续发author/works/patent请求，Worker只在collect入口检查不能满足此合同。仅增加该调用链的逐次dispatch取消guard、传播及回归，保留普通补全API/行为、RequestKind/预算与原有免费付费规则；不新增客户端或第二执行引擎。具体调用链与检索回执归档于07 execution日志，提交身份详见ledger的Amendments。
+- 2026-10-25，依用户持续授权“后续修改都授权 把所有子计划都跑完”（A7），09增加`discovery-papers.js`、`discoveryPapers.test.js`、`DiscoveryVerificationRepository.kt`、`DiscoveryVerificationRepositoryIT.kt`，清单8→12并记录必要文件数例外。原文reader仅私有且默认首个可读attempt，宿主无法按冻结record/attempt/SHA复用；08统计仅有聚合queued，缺09已要求的pending/processing和独立protected。仅增加原reader窄公开桥接与只读真实SQL计数及直接回归，不改准入写链、旧人工行为、迁移或全站样式；保留旧API字段，protected为重叠诊断子计数不得加总为互斥类别。具体检索/源码回执归档于09 execution日志，提交身份详见ledger的Amendments。
+- 2026-10-25，依A7持续授权，09再增加子计划逐名列出的28个直接资源回归测试，清单12→40。必需的第18个资源使新鲜全量JS执行`artifact://1241`的28个旧17-count/list断言失败（1840例中28失败）；它们动态取key，旧字面量检索无匹配。仅删除无关套件中过期的全局17数量钉死断言，并为`mailboxChatBehavior.test.js`原脚本列表补入app前的新script；共享key/顺序/各自功能断言保留，指定资源/重审测试仍集中验证完整18资源合同。不改这些功能的产品代码、不以重新钉死旧数量掩盖回归；确切失败与检索回执见09 execution日志，提交身份见ledger的Amendments。
+- 2026-10-25，依A7持续授权，10增加`DiscoveryVerificationRegistrationService.kt`，清单7→8、不突破默认上限。既有reserve按当前clock与新execution/DOI自行locate，无法保留补录manifest固定evaluationAt及唯一record/attempt/SHA；仅向同文件现有Execution传递可空冻结原文上下文，由reserve校验来源后使用，普通默认null仍走旧clock/locator。禁止reserve后重写快照或另建登记链；授权FlowIT覆盖真实consumer→注册固定上下文，原登记/集成测试不改且新鲜执行。检索回执与提交身份见10 execution/ledger。
+- 2026-10-25，依A7持续授权，10增加`discovery/archive/PaperQualificationEvidenceExtractor.kt`，清单8→9。恢复代理实际历史报告4472论文/4487读取尝试/3904 COMPLETE，含生产10秒nanoTime软限的PARTIAL；相同输入重复比较可能因运行耗时改变覆盖，不能用缓存首份报告伪装确定性。仅增加现有生产extractor的显式只读冻结回放factory：固定clock但保留100页/2MiB/100事实工作上限，Tool compare独用，记录预算模式及实现hash/版本。普通@Component、Worker、Backfill及原analyzer仍用System clock/10秒；旧PARTIAL原样历史保留，新模式真实重算且独立版本，不返缓存作二次重算证据。原deadline测试不改并新鲜执行，授权ToolTest覆盖超100页及真实重复解析；身份/资格门槛不变，前后身份见ledger。
```

## Remote Verification Authority — 2026-10-25 Asia/Shanghai

Initial request to SSH root@150.158.92.103 and create a private temporary comparison directory was rejected by automatic approval review because this invocation lacked specific remote-host and sensitive historical-data authority. No remote verification ran under that rejected request.

Human response to the explicit remote verification question: “授权上述远端只读核验及临时证据写入”. This authorizes reading the frozen historical source corpus and roster on root@150.158.92.103, comparing twice with the current matching implementation, creating only private temporary review evidence, and emitting aggregate counts/hashes. It authorizes no business-data application or paid service call. The independent reviewer then obtained approval for that same bounded request and began two real reparses. Final command evidence remains pending until its complete report.

## Epoch 1 Outcome — 2026-10-25T23:08:50.000+08:00

- Independent review-p result: FAIL / INITIAL; V-1 and V-2 are NEW P1, REPAIRABLE.
- V-1: persisted current-round qualification excerpts/source/locator do not reach recheck DTO/UI.
- V-2: default IDS and ALL_MATCHING production browser prepare payloads receive HTTP400 from the real MVC/service contract.
- Machine report retains every mandatory command, all 24 skips, every fast-p RECORD_ONLY re-evaluation, full master matrix, A14 per-file chronology and human sign-off requirement, original and new private-session output incidents, and remote SSH/receipt recovery limits.
- Fresh full JDK11 suite: exit1, 5622 tests / 1 failure / 0 errors / 24 skips; unchanged sample-retention baseline cause proven, never called green.
- Fresh package: exit0; JS1840 pass; selected real MySQL238 tests / 0 failures/errors/skips.
- Fresh historical AUDIT: 897 completed / 897 compatible original caches; 17 new would-admit suggestions plus4 prior positive; actual admission/provider0;15 missing source refs explicit.
- Fresh full-source reports:4472 papers/4487 attempts/3907 complete, identical de0f3414bf791bed320a11bccd46223bdce868024933d94e1014ab33924096a9; remains PARTIAL, complete gains null. Initial SSH255 and second-round missing exit receipt retained; fresh initial output plus durable supplement exit0 establish repeated computation. Remote authorization blocker resolved by direct human approval.
- Repair-p result: DRAFT_READY, single exact five-file repair plan above; not executed. Historical A7 or remote readonly authority does not approve this new repair.
- Manual A-M1–A-M5 and final governing-master/boundary/A14 file sign-off: PENDING.
- Controller completeness/boundary check: retained HEAD/branch unchanged; tracked product/index clean before evidence staging; writes limited to ledger.md, machine-verification.md, and the single repair.md.
- Evidence parent before next commit: ee65822ed51caf012e487d82cc04e9d74c0b125a.
- No human check or sign-off inferred. No product repair, push, merge or deployment performed.
